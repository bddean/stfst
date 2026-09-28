//! Driving the HFST toolchain.
//!
//! HFST is GPLv3 and runs only here, as subprocesses on the build machine. The
//! artifact it produces is read by `stfst-ol`, which links nothing of it.

use std::path::{Path, PathBuf};
use std::process::Command;

use anyhow::{bail, Context, Result};
use stfst_core::context;
use stfst_core::host;
use stfst_core::record::{all_tags, copy_tag, is_optional_tag, is_tag};
use stfst_core::window;
use stfst_core::{Shown, System};

use crate::prelude;
use crate::recipe;

/// What the compiler proved about a dictionary before writing it.
#[derive(Debug)]
pub struct Checks {
    /// Outputs the record language rejects, if any.
    pub bad_records: Vec<String>,
    /// Inputs that are not canonical outlines, if any.
    pub bad_outlines: Vec<String>,
    /// Multichar symbols that are neither record tags nor keys, which means an
    /// internal feature tag survived into the artifact.
    pub leaked_symbols: Vec<String>,
    /// Whether the grammar relates anything at all.
    pub empty: bool,
}

impl Checks {
    pub fn passed(&self) -> bool {
        !self.empty
            && self.bad_records.is_empty()
            && self.bad_outlines.is_empty()
            && self.leaked_symbols.is_empty()
    }

    pub fn report(&self) -> String {
        let mut out = String::new();
        if self.empty {
            out.push_str("  the grammar is empty, so it relates no outline to anything\n");
        }
        for r in &self.bad_records {
            out.push_str(&format!("  output is not a legal record: {r}\n"));
        }
        for o in &self.bad_outlines {
            out.push_str(&format!("  input is not a canonical outline: {o}\n"));
        }
        for s in &self.leaked_symbols {
            out.push_str(&format!("  internal symbol reached the artifact: {s}\n"));
        }
        out
    }
}

/// The first name inside a prelude macro that a grammar redefines, if any.
///
/// A macro body is expanded where it is *called*, and `Fresh` and `Canon` are
/// called after the grammar, so a name they mention is a name the grammar must
/// leave alone. Plain definitions capture their references when the prelude
/// defines them, so shadowing one of those is only confusing, not wrong.
fn shadowed(prelude: &str, bodies: &str) -> Option<String> {
    let mut protected: Vec<&str> = Vec::new();
    for line in prelude.lines() {
        let Some(rest) = line.trim().strip_prefix("define ") else {
            continue;
        };
        if !rest.split_whitespace().next().is_some_and(|n| n.contains('(')) {
            continue;
        }
        let body = rest.split_once(')').map_or(rest, |(_, b)| b);
        protected.extend(
            body.split(|c: char| !c.is_alphanumeric() && c != '_')
                .filter(|w| w.chars().next().is_some_and(char::is_uppercase)),
        );
    }
    bodies
        .lines()
        .filter_map(|line| line.trim().strip_prefix("define "))
        .filter_map(|rest| rest.split_whitespace().next())
        .map(|name| name.split('(').next().unwrap_or(name))
        .find(|name| protected.contains(name))
        .map(str::to_string)
}

pub(crate) fn run(program: &str, args: &[&str]) -> Result<String> {
    let out = Command::new(program)
        .args(args)
        .output()
        .map_err(|e| spawn_error(program, e))?;
    let stdout = String::from_utf8_lossy(&out.stdout).into_owned();
    if !out.status.success() {
        bail!(
            "{program} failed: {}\n{}",
            String::from_utf8_lossy(&out.stderr),
            stdout
        );
    }
    Ok(stdout)
}

/// Why an HFST tool did not start. Not found is the usual case, and its cause is
/// always the same: this is not the dev shell.
pub fn spawn_error(program: &str, e: std::io::Error) -> anyhow::Error {
    match e.kind() {
        std::io::ErrorKind::NotFound => anyhow::anyhow!(
            "{program} is not on PATH, so HFST is missing. It comes from the flake: \
             run this inside the dev shell, as `direnv exec . <command>` or under \
             `nix develop`."
        ),
        _ => anyhow::Error::new(e).context(format!("running {program}")),
    }
}

/// Compile an xfst source that defines `Grammar`, and prove the §3 properties
/// over every reachable path rather than over a sampled test suite.
pub fn compile_xfst(
    system: &System,
    sources: &[PathBuf],
    work: &Path,
    out_fst: &Path,
    out_reverse: &Path,
) -> Result<Checks> {
    std::fs::create_dir_all(work)?;
    let script = work.join("build.xfst");
    let net = work.join("grammar.hfst");
    let fresh = work.join("fresh.hfst");

    let mut bodies = String::new();
    for source in sources {
        let body = std::fs::read_to_string(source)
            .with_context(|| format!("reading {}", source.display()))?;
        bodies.push_str(&body);
    }
    // Which operations this grammar can ask for, read off its own text. A name it
    // never writes is one the acceptor need not know.
    let used: Vec<String> = all_tags()
        .into_iter()
        .filter(|tag| is_optional_tag(tag) && bodies.contains(tag))
        .collect();
    let mut s = prelude::generate_for(system, Some(&used));
    // `define Record` in a grammar cost the whole reverse direction, silently:
    // `Fresh` is expanded below, after the grammar, so it read a name that no
    // longer named a record and composed with nothing.
    if let Some(name) = shadowed(&s, &bodies) {
        bail!(
            "{name} is used inside a prelude macro, which is expanded after this \
             grammar, so redefining it changes what that macro means. Rename it."
        );
    }
    s.push_str(&bodies);
    s.push_str(&format!(
        "\npush Grammar\nsave stack {}\nclear stack\n",
        net.display()
    ));
    // The reverse direction asks what to stroke from where the writer is now, so
    // it reads the relation with no state carried in and none written out.
    s.push_str(&format!(
        "define FreshGrammar Fresh(Grammar) ;\npush FreshGrammar\nsave stack {}\nclear stack\n",
        fresh.display()
    ));
    // Bound the counterexamples: the difference can be an infinite language.
    s.push_str("echo STFST-CHECK-RECORD\nregex Grammar.l - Emission ;\nprint random-words 3\n");
    s.push_str("echo STFST-CHECK-OUTLINE\nregex Grammar.u - Outline ;\nprint random-words 3\n");
    // An undefined identifier is a *symbol* in xfst, not an error, so a typo in a
    // grammar composes with nothing and the result is empty. Nothing else notices:
    // every other check passes trivially over no paths.
    s.push_str("echo STFST-CHECK-EMPTY\npush Grammar\ntest null\nclear stack\n");
    s.push_str("echo STFST-CHECK-SIGMA\npush Grammar\nprint sigma\n");
    s.push_str("echo STFST-CHECK-END\n");
    std::fs::write(&script, s)?;

    let log = run("hfst-xfst", &["-F", script.to_str().unwrap()])?;
    let checks = parse_checks(&log, system);

    if checks.passed() {
        to_hfstol(&net, out_fst)?;
        to_hfstol_inverted(&fresh, out_reverse)?;
    }
    Ok(checks)
}

/// Compile a lexc source, which is how an imported dictionary arrives.
///
/// No acceptor check here, and it is not an omission. The import builds each
/// record through `stfst_core::Record`, which can only serialise canonical
/// order, and each tape through the outline parser, which can only emit steno
/// order. The check exists to catch hand-written rules, and there are none on
/// this path.
pub fn compile_lexc(
    source: &Path,
    work: &Path,
    out_fst: &Path,
    out_reverse: &Path,
) -> Result<()> {
    std::fs::create_dir_all(work)?;
    let net = work.join("lexicon.hfst");
    run(
        "hfst-lexc",
        &[source.to_str().unwrap(), "-o", net.to_str().unwrap()],
    )?;
    to_hfstol(&net, out_fst)?;
    to_hfstol_inverted(&net, out_reverse)
}

/// Compile the morphology artifacts: the rules from xfst, the two lexicons from
/// lexc.
///
/// None of the dictionary checks apply and none of them is missing. A dictionary
/// relates a key tape to a record, so §3 has properties to check; these relate
/// text to text, so it has nothing to say about them. Nor is there a reverse
/// direction: a host asks what a join produces and never what produced one.
///
/// The empty check stays, because the trap it catches is xfst's and not a
/// dictionary's: an undefined identifier is a *symbol* there, so a typo in a rule
/// composes with nothing and leaves an artifact that answers every question with
/// silence.
pub fn compile_morph(rules: &Path, lexicons: &Path, work: &Path, out: &Path) -> Result<Vec<String>> {
    std::fs::create_dir_all(work)?;
    std::fs::create_dir_all(out)?;
    let mut report = Vec::new();

    let net = work.join("ortho.hfst");
    let script = work.join("ortho.xfst");
    std::fs::write(
        &script,
        format!(
            "source {}\npush Ortho\nsave stack {}\nclear stack\n\
             echo STFST-CHECK-EMPTY\npush Ortho\ntest null\nclear stack\n",
            rules.display(),
            net.display()
        ),
    )?;
    let log = run("hfst-xfst", &["-F", script.to_str().unwrap()])?;
    if empty(&log) {
        bail!(
            "{} defines an empty Ortho, so it relates no join to anything",
            rules.display()
        );
    }
    to_hfstol(&net, &out.join("ortho.hfstol"))?;
    report.push(format!("ortho.hfstol from {}", rules.display()));

    for name in ["words", "infl"] {
        let source = lexicons.join(format!("{name}.lexc"));
        if !source.exists() {
            bail!(
                "{} is missing; run scripts/gen-morph.py under Plover's python",
                source.display()
            );
        }
        let net = work.join(format!("{name}.hfst"));
        run(
            "hfst-lexc",
            &[source.to_str().unwrap(), "-o", net.to_str().unwrap()],
        )?;
        to_hfstol(&net, &out.join(format!("{name}.hfstol")))?;
        report.push(format!("{name}.hfstol from {}", source.display()));
    }
    Ok(report)
}

/// Whether an xfst log's `test null` said the net was empty.
fn empty(log: &str) -> bool {
    let mut after = false;
    for line in log.lines() {
        let line = line.trim();
        if line == "STFST-CHECK-EMPTY" {
            after = true;
            continue;
        }
        if after && !line.is_empty() {
            return line.starts_with('1');
        }
    }
    false
}

fn to_hfstol(net: &Path, out: &Path) -> Result<()> {
    // Composition leaves every intermediate symbol in the alphabet, including
    // the feature tags the grammar consumes. Pruning them makes the artifact
    // self describing: its alphabet becomes exactly what it can emit, which is
    // what lets a host check the record language once at load time rather than
    // by walking every transition.
    let pruned = out.with_extension("pruned.hfst");
    run(
        "hfst-prune-alphabet",
        &["-i", net.to_str().unwrap(), "-o", pruned.to_str().unwrap()],
    )?;
    run(
        "hfst-fst2fst",
        &[
            "-O",
            "-i",
            pruned.to_str().unwrap(),
            "-o",
            out.to_str().unwrap(),
        ],
    )?;
    Ok(())
}

/// The same relation read the other way, records to outlines. Optimized lookup
/// only ever walks the input side, so a reverse lookup needs its own automaton
/// and cannot share the forward one.
fn to_hfstol_inverted(net: &Path, out: &Path) -> Result<()> {
    let inverted = out.with_extension("inverted.hfst");
    run(
        "hfst-invert",
        &["-i", net.to_str().unwrap(), "-o", inverted.to_str().unwrap()],
    )?;
    to_hfstol(&inverted, out)
}

fn parse_checks(log: &str, system: &System) -> Checks {
    let mut checks = Checks {
        bad_records: Vec::new(),
        bad_outlines: Vec::new(),
        leaked_symbols: Vec::new(),
        empty: true,
    };
    let tags = all_tags();
    let mut section = "";
    for line in log.lines() {
        let line = line.trim();
        if let Some(name) = line.strip_prefix("STFST-CHECK-") {
            section = match name {
                "RECORD" => "record",
                "OUTLINE" => "outline",
                "EMPTY" => "empty",
                "SIGMA" => "sigma",
                _ => "",
            };
            continue;
        }
        if line.is_empty() || line.starts_with('?') || line.contains("bytes.") {
            continue;
        }
        match section {
            // `test null` answers "1, (1 = TRUE, 0 = FALSE)" for the empty language.
            "empty" => checks.empty = line.starts_with('1'),
            "record" => checks.bad_records.push(line.to_string()),
            "outline" => checks.bad_outlines.push(line.to_string()),
            "sigma" => {
                for sym in line.split_whitespace() {
                    let known = tags.iter().any(|t| t == sym)
                        || context::all_symbols().iter().any(|c| c == sym)
                        || window::sig_char(sym).is_some()
                        || host::is_command(sym)
                        || host::is_meta(sym)
                        || sym.chars().count() == 1
                        || sym.starts_with("@_")
                        || sym == format!("{}", system.separator);
                    if !known && is_tag(sym) {
                        checks.leaked_symbols.push(sym.to_string());
                    }
                }
            }
            _ => {}
        }
    }
    checks
}

/// What a dictionary turned out to need from its host, read back from the
/// automaton it compiled to.
///
/// Nothing declares any of this in the source. The alphabet already says it: a
/// grammar that reads `[CAP=off]` has the symbol, one that carries state has
/// `[STATE]`, and one that splices the host's second bound word has `[COPY2]`. A
/// declaration would be a second copy of a fact the artifact determines, and the
/// two would eventually disagree.
#[derive(Debug, Default)]
pub struct Declared {
    /// Context slot names, in table order.
    pub context: Vec<String>,
    pub state: bool,
    /// How many recent words the host must bind, from the highest `[COPYn]`.
    pub registers: u8,
    /// Characters this dictionary named, so the host sends them as themselves
    /// rather than as their class.
    pub significant: Vec<char>,
    /// Operations this dictionary can ask for. Both are symbols, so both come
    /// straight out of the alphabet, which is why they are symbols.
    pub commands: Vec<String>,
    pub metas: Vec<String>,
    /// The text the host must show, if any. Declared in the recipe rather than
    /// read back: it is a property of the language and not of the alphabet, and
    /// its word count is the dial that sets what a lookup costs.
    pub shown: Option<Shown>,
    /// The longest outline, in strokes. `None` when the language has no longest,
    /// and then no host that needs one can load this dictionary.
    pub strokes: Option<u32>,
    /// Whether the language itself has a longest outline, before the recipe
    /// fills one in.
    pub bounded: bool,
    /// Whether some record waits for the unit after it: `[AFTERn]`.
    pub after: bool,
}

impl Declared {
    /// The oldest host contract that can run this dictionary. Items 5 and 6 are
    /// host version 2, so a dictionary that uses neither stays loadable by a host
    /// that implements neither.
    pub fn host(&self) -> u32 {
        // A held stroke is host 3, whether the record reads the text it waits for
        // or only follows it. A host that shows written text has no reason to hold
        // anything, so it would run either against the wrong words.
        if self.after || self.shown.is_some_and(Shown::deferred) {
            return 3;
        }
        let plain = self.context.is_empty()
            && !self.state
            && self.registers == 0
            && self.shown.is_none();
        // Commands and metas do not raise the version: host 1 had them, and the
        // only change is that the manifest now says which ones.
        match plain {
            true => 1,
            false => 2,
        }
    }
}

pub fn declare(forward: &Path, system: &System, dict: &recipe::Dict) -> Result<Declared> {
    let fst = stfst_ol::Automaton::open(forward)
        .with_context(|| format!("reading back {}", forward.display()))?;
    // The longest outline. A cursor discovers it as the strokes arrive, so no host
    // here asks; Plover's dictionary API takes a whole outline, so it has to know
    // before the first stroke. Where the language has no longest, the recipe says
    // how far to look and an artifact with neither is one Plover cannot load.
    let longest = match fst.alphabet().number(&system.separator.to_string()) {
        Some(sep) => fst.max_occurrences(sep).map(|n| n + 1),
        // No separator in the alphabet at all: every entry is one stroke.
        None => Some(1),
    };
    let mut declared = Declared {
        shown: dict.shown()?,
        bounded: longest.is_some(),
        strokes: longest.or(dict.strokes),
        ..Declared::default()
    };
    let symbols: Vec<&str> = fst.alphabet().symbols().collect();
    for slot in context::SLOTS {
        if slot.symbols().any(|s| symbols.contains(&s.as_str())) {
            declared.context.push(slot.name.to_string());
        }
    }
    declared.state = symbols.contains(&context::STATE_OPEN);
    // A record that waits for the unit after it needs a host that holds a stroke,
    // and the alphabet is what says so.
    declared.after = symbols.contains(&stfst_core::record::AFTER);
    for n in 1..=stfst_core::record::MAX_COUNT {
        if symbols.contains(&copy_tag(n).as_str()) {
            declared.registers = declared.registers.max(n);
        }
    }
    // Which characters the grammar named, read straight out of the alphabet. The
    // host has to agree with this exactly, so nothing declares it twice.
    declared.significant = symbols.iter().filter_map(|s| window::sig_char(s)).collect();
    declared.significant.sort_unstable();
    for symbol in &symbols {
        if host::is_command(symbol) {
            declared.commands.push(host::tag_name(symbol, "CMD").unwrap().to_string());
        }
        if host::is_meta(symbol) {
            declared.metas.push(host::tag_name(symbol, "META").unwrap().to_string());
        }
    }
    Ok(declared)
}

/// Write the artifact directory: the automaton plus what a host needs to refuse
/// a dictionary it cannot interpret.
pub fn write_artifact(
    dir: &Path,
    dict: &recipe::Dict,
    system: &System,
    forward: &Path,
    reverse: &Path,
) -> Result<()> {
    std::fs::create_dir_all(dir)?;
    std::fs::copy(forward, dir.join("forward.hfstol"))?;
    let declared = declare(forward, system, dict)?;
    // A word an unbounded dictionary writes has unboundedly many outlines, so
    // "what writes this" has no finite answer and the cheapest few are a search
    // the host would pay for on every stroke.
    if declared.bounded && reverse.exists() {
        std::fs::copy(reverse, dir.join("reverse.hfstol"))?;
    } else {
        let _ = std::fs::remove_file(dir.join("reverse.hfstol"));
    }
    let (name, version, priority) = (&dict.name, &dict.version, dict.priority);
    std::fs::write(dir.join("tags.txt"), all_tags().join("\n") + "\n")?;
    let keys: Vec<String> = system
        .keys()
        .iter()
        .map(|k| format!("{:?}", k.name))
        .collect();
    let manifest = format!(
        "[dict]\n\
             name = {name:?}\n\
             version = {version:?}\n\
             system = {:?}\n\
             priority = {priority}\n\
             \n[requires]\n\
             keys = [{}]\n\
             host = {}\n\
             context = [{}]\n\
             state = {}\n\
             registers = {}\n\
             after = {}\n\
             {}\
             significant = {:?}\n\
             commands = [{}]\n\
             metas = [{}]\n\
             \n[input]\n\
             alphabet = \"keys\"\n\
             order = {:?}\n\
             separator = {:?}\n\
             {}",
        system.name,
        keys.join(", "),
        declared.host(),
        declared
            .context
            .iter()
            .map(|c| format!("{c:?}"))
            .collect::<Vec<_>>()
            .join(", "),
        declared.state,
        declared.registers,
        declared.after,
        match declared.shown {
            Some(Shown::Written(n)) => format!("words = {n}\n"),
            Some(Shown::Next(n)) => format!("next = {n}\n"),
            None => String::new(),
        },
        declared.significant.iter().collect::<String>(),
        quoted(&declared.commands),
        quoted(&declared.metas),
        system.order(),
        system.separator.to_string(),
        match declared.strokes {
            Some(n) => format!("strokes = {n}\n"),
            None => String::new(),
        },
    );
    // The manifest goes in through a rename, which is what moves the directory's
    // mtime: overwriting a file in place does not. So the artifact's mtime is
    // when it was built, which is what a host reloads by and what `fresh` reads.
    let staged = dir.join("manifest.toml.new");
    std::fs::write(&staged, manifest)?;
    std::fs::rename(&staged, dir.join("manifest.toml"))?;
    Ok(())
}

fn quoted(names: &[String]) -> String {
    names
        .iter()
        .map(|n| format!("{n:?}"))
        .collect::<Vec<_>>()
        .join(", ")
}

/// Where build products go for a dictionary.
pub fn work_dir(root: &Path, name: &str) -> PathBuf {
    root.join(name)
}

#[cfg(test)]
mod tests {
    use super::shadowed;

    #[test]
    fn a_name_inside_a_prelude_macro_is_protected() {
        let prelude = "define Record [ Item* ] ;\ndefine Fresh(G) [ G .o. Record ] ;\n";
        assert_eq!(shadowed(prelude, "define Record [ A ] ;").as_deref(), Some("Record"));
        assert_eq!(shadowed(prelude, "define Bundle [ A ] ;"), None);
        // A plain definition captures its references where the prelude writes
        // them, so shadowing `Item` after the fact changes nothing.
        assert_eq!(shadowed(prelude, "define Item [ A ] ;"), None);
    }
}
