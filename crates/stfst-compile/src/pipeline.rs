//! Driving the HFST toolchain.
//!
//! HFST is GPLv3 and runs only here, as subprocesses on the build machine. The
//! artifact it produces is read by `stfst-ol`, which links nothing of it.

use std::path::{Path, PathBuf};
use std::process::Command;

use anyhow::{bail, Context, Result};
use stfst_core::record::{all_tags, is_tag};
use stfst_core::System;

use crate::prelude;

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
}

impl Checks {
    pub fn passed(&self) -> bool {
        self.bad_records.is_empty()
            && self.bad_outlines.is_empty()
            && self.leaked_symbols.is_empty()
    }

    pub fn report(&self) -> String {
        let mut out = String::new();
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

fn run(program: &str, args: &[&str]) -> Result<String> {
    let out = Command::new(program)
        .args(args)
        .output()
        .with_context(|| format!("running {program}"))?;
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

/// Compile an xfst source that defines `Grammar`, and prove the §3 properties
/// over every reachable path rather than over a sampled test suite.
pub fn compile_xfst(
    system: &System,
    source: &Path,
    work: &Path,
    out_fst: &Path,
) -> Result<Checks> {
    std::fs::create_dir_all(work)?;
    let script = work.join("build.xfst");
    let net = work.join("grammar.hfst");

    let body = std::fs::read_to_string(source)
        .with_context(|| format!("reading {}", source.display()))?;

    let mut s = prelude::generate(system);
    s.push_str(&body);
    s.push_str(&format!(
        "\npush Grammar\nsave stack {}\n",
        net.display()
    ));
    // Bound the counterexamples: the difference can be an infinite language.
    s.push_str("echo STFST-CHECK-RECORD\nregex Grammar.l - Record ;\nprint random-words 3\n");
    s.push_str("echo STFST-CHECK-OUTLINE\nregex Grammar.u - Outline ;\nprint random-words 3\n");
    s.push_str("echo STFST-CHECK-SIGMA\npush Grammar\nprint sigma\n");
    s.push_str("echo STFST-CHECK-END\n");
    std::fs::write(&script, s)?;

    let log = run("hfst-xfst", &["-F", script.to_str().unwrap()])?;
    let checks = parse_checks(&log, system);

    if checks.passed() {
        to_hfstol(&net, out_fst)?;
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
pub fn compile_lexc(source: &Path, work: &Path, out_fst: &Path) -> Result<()> {
    std::fs::create_dir_all(work)?;
    let net = work.join("lexicon.hfst");
    run(
        "hfst-lexc",
        &[source.to_str().unwrap(), "-o", net.to_str().unwrap()],
    )?;
    to_hfstol(&net, out_fst)
}

fn to_hfstol(net: &Path, out: &Path) -> Result<()> {
    // Composition leaves every intermediate symbol in the alphabet, including
    // the feature tags the grammar consumes. Pruning them makes the artifact
    // self describing: its alphabet becomes exactly what it can emit, which is
    // what lets a host check the record language once at load time rather than
    // by walking every transition.
    let pruned = net.with_extension("pruned");
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

fn parse_checks(log: &str, system: &System) -> Checks {
    let mut checks = Checks {
        bad_records: Vec::new(),
        bad_outlines: Vec::new(),
        leaked_symbols: Vec::new(),
    };
    let tags = all_tags();
    let mut section = "";
    for line in log.lines() {
        let line = line.trim();
        if let Some(name) = line.strip_prefix("STFST-CHECK-") {
            section = match name {
                "RECORD" => "record",
                "OUTLINE" => "outline",
                "SIGMA" => "sigma",
                _ => "",
            };
            continue;
        }
        if line.is_empty() || line.starts_with('?') || line.contains("bytes.") {
            continue;
        }
        match section {
            "record" => checks.bad_records.push(line.to_string()),
            "outline" => checks.bad_outlines.push(line.to_string()),
            "sigma" => {
                for sym in line.split_whitespace() {
                    let known = tags.iter().any(|t| t == sym)
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

/// Write the artifact directory: the automaton plus what a host needs to refuse
/// a dictionary it cannot interpret.
pub fn write_artifact(
    dir: &Path,
    name: &str,
    version: &str,
    system: &System,
    priority: i32,
    forward: &Path,
) -> Result<()> {
    std::fs::create_dir_all(dir)?;
    std::fs::copy(forward, dir.join("forward.hfstol"))?;
    std::fs::write(dir.join("tags.txt"), all_tags().join("\n") + "\n")?;
    let keys: Vec<String> = system
        .keys()
        .iter()
        .map(|k| format!("{:?}", k.name))
        .collect();
    std::fs::write(
        dir.join("manifest.toml"),
        format!(
            "[dict]\n\
             name = {name:?}\n\
             version = {version:?}\n\
             system = {:?}\n\
             priority = {priority}\n\
             \n[requires]\n\
             keys = [{}]\n\
             host = 1\n\
             context = []\n\
             state = false\n\
             \n[input]\n\
             alphabet = \"keys\"\n\
             order = {:?}\n\
             separator = {:?}\n",
            system.name,
            keys.join(", "),
            system.order(),
            system.separator.to_string(),
        ),
    )?;
    Ok(())
}

/// Where build products go for a dictionary.
pub fn work_dir(root: &Path, name: &str) -> PathBuf {
    root.join(name)
}
