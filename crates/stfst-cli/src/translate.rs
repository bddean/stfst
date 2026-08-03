//! Driving the runtime from a stroke stream, for testing only.
//!
//! A small host: strokes in, text out. Small enough to read, and complete enough
//! that the parts of the contract only a host can exercise are exercised. It
//! keeps the text, so it can show a dictionary its window, bind its registers,
//! carry its state, and retract the units a longer match supersedes.

use std::collections::VecDeque;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Context, Result};
use stfst_core::{Capabilities, Emission, Outline, System};
use stfst_dict::Stack;
use stfst_text::format::Event;
use stfst_text::Text;

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
    // One set of artifacts for every `Text` this run makes, which is what `Arc` is
    // for here: `!buffer` starts a fresh buffer and must not reopen them.
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

    let mut session = stack.session();
    let mut text = Text::with_morph(morph.clone());
    // How many units the text held before each stroke, so a match that spans
    // several strokes can put back what those strokes wrote.
    let mut marks: Vec<usize> = Vec::new();
    let mut widest = 0;
    // A queue rather than a loop over the input, because a command can put a stroke
    // back: `{*+}` re-fires the last outline and `{*}` re-translates it with the
    // star flipped, so both end by feeding a stroke of their own.
    let mut queue: VecDeque<String> = strokes.into();
    'stroke: while let Some(line) = queue.pop_front() {
        let line = line.trim();
        if let Some(seed) = line.strip_prefix(BUFFER) {
            text = Text::with_morph(morph.clone());
            text.seed(seed);
            say!("{line}\t{:?}", text.to_string());
            continue;
        }
        // `!` marks a comment, as in a golden file, and `#` cannot: it is a key.
        if line.is_empty() || (line.starts_with('!') && line != UNDO) {
            continue;
        }
        if line == UNDO {
            match session.undo() {
                Some(stroke) => {
                    take_text_back(&mut text, &mut marks);
                    say!(
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
            say!("{line}\t-\t{:?}", text.to_string());
            continue;
        };
        // A longer match supersedes what the strokes it covers already wrote.
        let target = marks[marks.len() - m.strokes];
        while text.units() > target {
            text.undo();
        }
        let dict = stack.dict(m.dict);
        let first = m.emissions.first().cloned().unwrap_or_default();
        let events = match &m.window {
            Some(window) => text.rewrite(&first.record, window, dict.words())?,
            None => {
                let registers = text.registers(dict.registers());
                text.apply(&first.record, &registers)?
            }
        };
        // A folded match writes the base, then what the folded key means. The base
        // dictionary never heard of the suffix, and the suffix dictionary never
        // heard of the word.
        if let Some(suffix) = &m.suffix {
            let registers = text.registers(9);
            text.apply(&suffix.record, &registers)?;
        }
        session.accept(m.dict, first.state.clone());
        // The commands the formatter cannot carry out are the ones that reach the
        // stroke buffer, so they land here, where the buffer is. Each one first takes
        // its own stroke back: the command wrote nothing, so leaving it there would
        // cost an extra undo later.
        for event in &events {
            let Event::Cmd { name, arg } = event else {
                continue;
            };
            // A stack command needs no stroke put back, so it runs where it is.
            if session.stack_command(name, arg) {
                say!("{line}\t{name}\t{arg:?}");
                continue 'stroke;
            }
            let engine = matches!(
                name.as_str(),
                "engine.undo" | "engine.repeat_stroke" | "engine.toggle_star"
            );
            if !engine {
                continue;
            }
            take_back(&mut session, &mut text, &mut marks);
            match name.as_str() {
                "engine.undo" => take_back(&mut session, &mut text, &mut marks),
                "engine.repeat_stroke" => {
                    if let Some(&last) = session.history().last() {
                        queue.push_front(system.format_outline(&Outline(vec![last])));
                    }
                }
                "engine.toggle_star" => {
                    if let Some(&last) = session.history().last() {
                        take_back(&mut session, &mut text, &mut marks);
                        let star = system.index_of_symbol('*');
                        queue.push_front(
                            system.format_outline(&Outline(vec![toggled(last, star)])),
                        );
                    }
                }
                _ => {}
            }
            say!("{line}\t{name}\t{:?}", text.to_string());
            continue 'stroke;
        }
        // The context is what the host computes from its own output, so it is
        // recomputed here rather than being remembered.
        let tail = text.tail().clone();
        let context = session.context_mut();
        context.set("CAP", if tail.case.is_some() { "on" } else { "off" });
        context.set("W1", text.last_word_case());

        say!(
            "{line}\t{}\t{}{}\t{}\t{:?}",
            m.strokes,
            dict.name(),
            match &m.suffix {
                Some(s) => format!("+{s}"),
                None => String::new(),
            },
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

/// Take back one stroke and whatever it wrote.
fn take_back(session: &mut stfst_dict::Session, text: &mut Text, marks: &mut Vec<usize>) {
    if session.undo().is_some() {
        take_text_back(text, marks);
    }
}

/// Undo the text back to where the last stroke found it.
///
/// Not one `Text::undo` per stroke: a stroke that matched nothing wrote no unit,
/// and a stroke that superseded a run wrote one where several had been. The mark is
/// what says how many.
fn take_text_back(text: &mut Text, marks: &mut Vec<usize>) {
    let mark = marks.pop().unwrap_or(0);
    while text.units() > mark {
        text.undo();
    }
}

/// The same stroke with the star key flipped, which is what `{*}` re-translates.
fn toggled(stroke: stfst_core::Stroke, star: Option<usize>) -> stfst_core::Stroke {
    let Some(star) = star else { return stroke };
    let mut out = stfst_core::Stroke::EMPTY;
    for i in stroke.indices() {
        if i != star {
            out = out.with(i);
        }
    }
    match stroke.contains(star) {
        true => out,
        false => out.with(star),
    }
}
