//! Every outline an artifact holds, walked out of its automaton.

use std::io::Write;
use std::path::Path;

use anyhow::{bail, Context, Result};
use stfst_core::window::WINDOW_OPEN;
use stfst_core::{Capabilities, System};
use stfst_dict::Dict;
use stfst_ol::{Automaton, Cursor, Sym};

/// Print each outline in steno order, the shorter first where one extends
/// another. With `records`, what it writes beside it; an outline that reads the
/// text after its keys writes `[W]`, since what it writes depends on that text.
pub fn run(artifact: &Path, system: &System, strokes: Option<u32>, records: bool) -> Result<()> {
    let dict = Dict::open(artifact, system, &Capabilities::all())?;
    if !dict.context().is_empty() || dict.stateful() {
        bail!("{} reads a context or a state before its keys, which dump does not walk", dict.name());
    }
    // An artifact has a reverse automaton exactly when its outlines have a
    // longest, so without one the walk would not end.
    let strokes = match (strokes, artifact.join("reverse.hfstol").exists()) {
        (Some(n), _) => n,
        (None, true) => dict.manifest.input.strokes.unwrap_or(1),
        (None, false) => bail!("{} has no longest outline: pass --strokes", dict.name()),
    };
    let fst = Automaton::open(&crate::lookup::forward(artifact)?)?;
    let alphabet = fst.alphabet();
    // The walk reads keys and the separator only, in steno order with the
    // separator first, so an outline comes before every outline it begins.
    let separator = alphabet.number(&system.separator.to_string());
    let mut order = vec![separator];
    order.extend(system.keys().iter().map(|k| alphabet.number(&k.symbol.to_string())));
    let walk = Walk {
        system,
        fst: &fst,
        order,
        separator,
        window: dict.shown().and_then(|_| alphabet.number(WINDOW_OPEN)),
        strokes,
        records,
    };
    let mut out = std::io::BufWriter::new(std::io::stdout().lock());
    walk.visit(fst.cursor(), &mut Vec::new(), 1, &mut out)?;
    out.flush()?;
    Ok(())
}

struct Walk<'a> {
    system: &'a System,
    fst: &'a Automaton,
    /// The symbols the walk reads, in the order it reads them.
    order: Vec<Option<Sym>>,
    separator: Option<Sym>,
    window: Option<Sym>,
    strokes: u32,
    records: bool,
}

impl Walk<'_> {
    fn visit(&self, cursor: Cursor<'_>, tape: &mut Vec<Sym>, stroke: u32, out: &mut impl Write) -> Result<()> {
        let ends_a_stroke = !tape.is_empty() && tape.last().copied() != self.separator;
        if ends_a_stroke {
            let reads_text = self.window.is_some_and(|w| {
                let mut c = cursor.clone();
                c.step(w);
                c.live() > 0
            });
            if cursor.accepts() || reads_text {
                let written = if self.records { cursor.outputs() } else { Vec::new() };
                self.print(tape, &written, reads_text, out)?;
            }
        }
        let mut next: Vec<(usize, Sym)> = cursor
            .next_inputs()
            .into_iter()
            .filter_map(|s| self.order.iter().position(|&o| o == Some(s)).map(|r| (r, s)))
            .collect();
        next.sort_unstable();
        for (_, sym) in next {
            let at_separator = Some(sym) == self.separator;
            if at_separator && (!ends_a_stroke || stroke >= self.strokes) {
                continue;
            }
            let mut c = cursor.clone();
            c.step(sym);
            if c.live() == 0 {
                continue;
            }
            tape.push(sym);
            self.visit(c, tape, stroke + at_separator as u32, out)?;
            tape.pop();
        }
        Ok(())
    }

    fn print(&self, tape: &[Sym], written: &[Vec<Sym>], reads_text: bool, out: &mut impl Write) -> Result<()> {
        let alphabet = self.fst.alphabet();
        let rendered = alphabet.render(tape);
        let outline = self
            .system
            .from_tape(&rendered)
            .with_context(|| format!("reading back tape {rendered}"))?;
        write!(out, "{}", self.system.format_outline(&outline))?;
        if self.records {
            for record in written {
                write!(out, "\t{}", alphabet.render(record))?;
            }
            if reads_text {
                write!(out, "\t{WINDOW_OPEN}")?;
            }
        }
        writeln!(out)?;
        Ok(())
    }
}
