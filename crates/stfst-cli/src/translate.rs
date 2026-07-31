//! Driving the runtime from a stroke stream, for testing only.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use stfst_core::{Outline, Record, System};
use stfst_dict::Stack;

/// A line that takes back the last stroke. A host has a key for this; a text
/// stream needs a word, and `*` is a real outline so it cannot be the word.
const UNDO: &str = "!undo";

pub fn run(artifacts: &[PathBuf], system: &Path, strokes: Vec<String>) -> Result<()> {
    let system = System::load(system)?;
    let stack = Stack::open(artifacts, &system)?;
    for (n, dict) in stack.dicts().iter().enumerate() {
        println!("! {n} {} priority {}", dict.name(), dict.priority());
    }

    let mut session = stack.session();
    let mut widest = 0;
    for line in strokes {
        let line = line.trim();
        // `!` marks a comment, as in a golden file, and `#` cannot: it is a key.
        if line.is_empty() || (line.starts_with('!') && line != UNDO) {
            continue;
        }
        if line == UNDO {
            match session.undo() {
                Some(stroke) => println!("{UNDO}\t{}", system.format_outline(&Outline(vec![stroke]))),
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
        session.push(outline.0[0]);
        widest = widest.max(session.candidates());

        match session.best()? {
            None => println!("{line}\t-"),
            Some(m) => println!(
                "{line}\t{}\t{}\t{}",
                m.strokes,
                stack.dict(m.dict).name(),
                m.records
                    .iter()
                    .map(Record::to_string)
                    .collect::<Vec<_>>()
                    .join("\t")
            ),
        }
    }
    println!(
        "! {} strokes, {widest} candidates at the widest",
        session.history().len()
    );
    Ok(())
}
