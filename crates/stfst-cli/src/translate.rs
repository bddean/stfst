//! Driving the runtime from a stroke stream, for testing only.
//!
//! Strokes in, printed lines out. The host itself is `stfst-board`, which the
//! Android keyboard drives too; what is here is the stream format and the
//! printing, so a line shape stays a testing concern and not a runtime one.

use std::collections::VecDeque;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Context, Result};
use stfst_board::{Board, Outcome};
use stfst_core::{Capabilities, Outline, System};
use stfst_dict::Stack;

/// A line that takes back the last stroke. A host has a key for this; a text
/// stream needs a word, and `*` is a real outline so it cannot be the word.
const UNDO: &str = "!undo";
/// A line that seeds the text, so a case can start from something already
/// written without stroking it.
const BUFFER: &str = "!buffer ";

pub fn run(
    artifacts: &[PathBuf],
    system: &Path,
    strokes: Vec<String>,
    quiet: bool,
    morph: &Path,
) -> Result<()> {
    let system = System::load(system)?;
    // One set of artifacts for every `Text` the board makes, which is what `Arc`
    // is for here: `!buffer` starts a fresh buffer and must not reopen them.
    let morph = Arc::new(stfst_text::morph::Morph::open(morph)?);
    // This harness implements every command and meta in the tables. A host that
    // implemented fewer would be refused the dictionaries that need them, which is
    // the point of declaring.
    let stack = Stack::open(artifacts, &system, &Capabilities::all())?;
    // One macro rather than a branch at every call: the harness prints six
    // different lines and each one is only for a reader.
    macro_rules! say {
        ($($arg:tt)*) => {
            if !quiet {
                println!($($arg)*);
            }
        };
    }
    for (n, dict) in stack.dicts().iter().enumerate() {
        say!("! {n} {} priority {}", dict.name(), dict.priority());
    }

    let mut board = Board::new(&system, &stack, Some(morph));
    let mut queue: VecDeque<String> = strokes.into();
    while let Some(line) = queue.pop_front() {
        let line = line.trim();
        if let Some(seed) = line.strip_prefix(BUFFER) {
            board.seed(seed);
            say!("{line}\t{:?}", board.text());
            continue;
        }
        // `!` marks a comment, as in a golden file, and `#` cannot: it is a key.
        if line.is_empty() || (line.starts_with('!') && line != UNDO) {
            continue;
        }
        if line == UNDO {
            match board.undo() {
                Some(stroke) => say!(
                    "{UNDO}\t{}\t{:?}",
                    system.format_outline(&Outline(vec![stroke])),
                    board.text()
                ),
                None => println!("{UNDO}\t-"),
            }
            continue;
        }
        let outline = system
            .parse_outline(line)
            .with_context(|| format!("reading stroke {line:?}"))?;
        anyhow::ensure!(
            outline.0.len() == 1,
            "{line:?} is {} strokes; feed them one per line",
            outline.0.len()
        );
        for step in board.push(outline.0[0])? {
            let label = step.refired.as_deref().unwrap_or(line);
            match step.outcome {
                Outcome::Unmatched => say!("{label}\t-\t{:?}", step.text),
                Outcome::Stack { name, arg } => say!("{label}\t{name}\t{arg:?}"),
                Outcome::Engine { name } => say!("{label}\t{name}\t{:?}", step.text),
                Outcome::Match {
                    strokes,
                    dict,
                    suffix,
                    emissions,
                } => say!(
                    "{label}\t{strokes}\t{dict}{}\t{}\t{:?}",
                    match &suffix {
                        Some(s) => format!("+{s}"),
                        None => String::new(),
                    },
                    emissions.join("\t"),
                    step.text
                ),
            }
        }
    }
    println!(
        "! {} strokes, {} candidates at the widest",
        board.strokes(),
        board.widest()
    );
    Ok(())
}
