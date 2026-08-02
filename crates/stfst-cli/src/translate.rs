//! Driving the runtime from a stroke stream, for testing only.
//!
//! A small host: strokes in, text out. Small enough to read, and complete enough
//! that the parts of the contract only a host can exercise are exercised. It
//! keeps the text, so it can show a dictionary its window, bind its registers,
//! carry its state, and retract the units a longer match supersedes.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use stfst_core::{Emission, Outline, System};
use stfst_dict::Stack;
use stfst_text::Text;

/// A line that takes back the last stroke. A host has a key for this; a text
/// stream needs a word, and `*` is a real outline so it cannot be the word.
const UNDO: &str = "!undo";
/// A line that seeds the text, so a case can start from something already
/// written without stroking it.
const BUFFER: &str = "!buffer ";

pub fn run(artifacts: &[PathBuf], system: &Path, strokes: Vec<String>) -> Result<()> {
    let system = System::load(system)?;
    let stack = Stack::open(artifacts, &system)?;
    for (n, dict) in stack.dicts().iter().enumerate() {
        println!("! {n} {} priority {}", dict.name(), dict.priority());
    }

    let mut session = stack.session();
    let mut text = Text::default();
    // How many units the text held before each stroke, so a match that spans
    // several strokes can put back what those strokes wrote.
    let mut marks: Vec<usize> = Vec::new();
    let mut widest = 0;
    for line in strokes {
        let line = line.trim();
        if let Some(seed) = line.strip_prefix(BUFFER) {
            text = Text::default();
            text.seed(seed);
            println!("{line}\t{:?}", text.to_string());
            continue;
        }
        // `!` marks a comment, as in a golden file, and `#` cannot: it is a key.
        if line.is_empty() || (line.starts_with('!') && line != UNDO) {
            continue;
        }
        if line == UNDO {
            match session.undo() {
                Some(stroke) => {
                    marks.pop();
                    text.undo();
                    println!(
                        "{UNDO}\t{}\t{:?}",
                        system.format_outline(&Outline(vec![stroke])),
                        text.to_string()
                    );
                }
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
        marks.push(text.units());
        session.push(outline.0[0]);
        widest = widest.max(session.candidates());

        let found = session.best_with(|words| text.span(words).1)?;
        let Some(m) = found else {
            println!("{line}\t-\t{:?}", text.to_string());
            continue;
        };
        // A longer match supersedes what the strokes it covers already wrote.
        let target = marks[marks.len() - m.strokes];
        while text.units() > target {
            text.undo();
        }
        let dict = stack.dict(m.dict);
        let first = m.emissions.first().cloned().unwrap_or_default();
        match &m.window {
            Some(window) => text.rewrite(&first.record, window, dict.words())?,
            None => {
                let registers = text.registers(dict.registers());
                text.apply(&first.record, &registers)?
            }
        };
        session.accept(m.dict, first.state.clone());
        // The context is what the host computes from its own output, so it is
        // recomputed here rather than being remembered.
        let tail = text.tail().clone();
        let context = session.context_mut();
        context.set("CAP", if tail.case.is_some() { "on" } else { "off" });
        context.set("W1", text.last_word_case());

        println!(
            "{line}\t{}\t{}\t{}\t{:?}",
            m.strokes,
            dict.name(),
            m.emissions
                .iter()
                .map(Emission::to_string)
                .collect::<Vec<_>>()
                .join("\t"),
            text.to_string()
        );
    }
    println!(
        "! {} strokes, {widest} candidates at the widest",
        session.history().len()
    );
    Ok(())
}
