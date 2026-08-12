//! A host: strokes in, text out.
//!
//! Everything a consumer of the runtime has to do between a stroke and a
//! document, in one place. It keeps the text, so it can show a dictionary its
//! window, bind its registers, carry its state, and retract the units a longer
//! match supersedes. It also carries out the commands that reach the stroke
//! buffer, because the buffer is here.
//!
//! Two hosts drive it: `stfst translate`, which prints what each stroke did, and
//! the Android keyboard, which puts the text in an editor. Neither owns any of
//! this logic. `Step` is what they both read, and printing is theirs alone.

use std::collections::VecDeque;
use std::sync::Arc;

use stfst_core::{Emission, Outline, Record, Stroke, System};
use stfst_dict::{Session, Stack};
use stfst_text::format::Event;
use stfst_text::morph::Morph;
use stfst_text::Text;

/// Anything that can stop a stroke: no dictionary can hold a record the
/// formatter refuses, but a record can still ask for an operation this text
/// cannot do, and the two errors come from different crates.
#[derive(Debug, thiserror::Error)]
pub enum BoardError {
    #[error(transparent)]
    Dict(#[from] stfst_dict::DictError),
    #[error(transparent)]
    Format(#[from] stfst_text::format::FormatError),
}

/// What one stroke turned out to be.
pub enum Outcome {
    /// No dictionary matched. The text is unchanged.
    Unmatched,
    /// A command the dictionary stack carried out itself.
    Stack { name: String, arg: String },
    /// A command that reached the stroke buffer.
    Engine { name: String },
    Match {
        /// How many strokes the entry consumed, counting back from this one.
        strokes: usize,
        dict: String,
        /// What the key folded off the last stroke meant on its own.
        suffix: Option<String>,
        emissions: Vec<String>,
    },
}

/// One stroke's worth of history, and the text it left behind.
pub struct Step {
    pub outcome: Outcome,
    pub text: String,
    /// Set when the engine fired this stroke rather than the host: `{*+}` and
    /// `{*}` both end by feeding a stroke of their own, and a host that labels
    /// its output needs to name it.
    pub refired: Option<String>,
}

/// A briefer outline for text just written: one entry of a suggestions bar.
pub struct Hint {
    pub text: String,
    pub outline: String,
}

/// How many per-stroke text snapshots [`Board::hints`] can look back through.
/// Far more strokes than any trailing phrase spans.
const SNAPS: usize = 64;

pub struct Board<'a> {
    system: &'a System,
    stack: &'a Stack,
    session: Session<'a>,
    /// Absent where the host has no orthography artifacts. Then `[ORTHO_L]`
    /// degrades to a plain join and an inflection is refused, which is what
    /// `Text` already does about it.
    morph: Option<Arc<Morph>>,
    text: Text,
    /// How many units the text held before each stroke, so a match that spans
    /// several strokes can put back what those strokes wrote.
    marks: Vec<usize>,
    /// The whole text after each recent stroke, newest last, so [`Self::hints`]
    /// can count the strokes a trailing phrase took. An entry goes stale when a
    /// later match supersedes its stroke, and the prefix check there skips it.
    snaps: VecDeque<String>,
    widest: usize,
}

impl<'a> Board<'a> {
    pub fn new(system: &'a System, stack: &'a Stack, morph: Option<Arc<Morph>>) -> Self {
        Board {
            system,
            stack,
            session: stack.session(),
            text: Self::fresh(&morph),
            morph,
            marks: Vec::new(),
            snaps: VecDeque::new(),
            widest: 0,
        }
    }

    pub fn text(&self) -> String {
        self.text.to_string()
    }

    /// How many strokes have been read, and how wide the candidate set ever got.
    pub fn strokes(&self) -> usize {
        self.session.history().len()
    }

    pub fn widest(&self) -> usize {
        self.widest
    }

    /// Start a fresh buffer from text already written, without stroking it.
    pub fn seed(&mut self, written: &str) {
        self.text = Self::fresh(&self.morph);
        self.text.seed(written);
    }

    fn fresh(morph: &Option<Arc<Morph>>) -> Text {
        match morph {
            Some(morph) => Text::with_morph(morph.clone()),
            None => Text::default(),
        }
    }

    /// Take back the last stroke and whatever it wrote.
    pub fn undo(&mut self) -> Option<Stroke> {
        let stroke = self.session.undo()?;
        self.take_text_back();
        Some(stroke)
    }

    /// Read one stroke. More than one `Step` comes back when a command re-fires
    /// a stroke, and the extra ones are labelled with the outline that fired.
    pub fn push(&mut self, stroke: Stroke) -> Result<Vec<Step>, BoardError> {
        let mut steps = Vec::new();
        let mut queue: VecDeque<(Stroke, Option<String>)> = VecDeque::new();
        queue.push_back((stroke, None));
        while let Some((stroke, refired)) = queue.pop_front() {
            let (outcome, fire) = self.read(stroke)?;
            // The newest snap may be an engine command's survivor rather than
            // this stroke's placeholder; either way it now reads this text.
            if let Some(last) = self.snaps.back_mut() {
                *last = self.text.to_string();
            }
            steps.push(Step {
                outcome,
                text: self.text.to_string(),
                refired,
            });
            if let Some(next) = fire {
                let label = self.system.format_outline(&Outline(vec![next]));
                queue.push_back((next, Some(label)));
            }
        }
        Ok(steps)
    }

    /// One stroke through the runtime. The second half of the answer is a stroke
    /// the engine wants fired next.
    fn read(&mut self, stroke: Stroke) -> Result<(Outcome, Option<Stroke>), BoardError> {
        self.marks.push(self.text.units());
        self.snaps.push_back(String::new());
        if self.snaps.len() > SNAPS {
            self.snaps.pop_front();
        }
        self.session.push(stroke);
        self.widest = self.widest.max(self.session.candidates());

        let text = &self.text;
        let found = self.session.best_with(|words| text.span(words).1)?;
        let Some(m) = found else {
            return Ok((Outcome::Unmatched, None));
        };
        // A longer match supersedes what the strokes it covers already wrote.
        let target = self.marks[self.marks.len() - m.strokes];
        while self.text.units() > target {
            self.text.undo();
        }
        let dict = self.stack.dict(m.dict);
        let first = m.emissions.first().cloned().unwrap_or_default();
        let events = match &m.window {
            Some(window) => self.text.rewrite(&first.record, window, dict.words())?,
            None => {
                let registers = self.text.registers(dict.registers());
                self.text.apply(&first.record, &registers)?
            }
        };
        // A folded match writes the base, then what the folded key means. The base
        // dictionary never heard of the suffix, and the suffix dictionary never
        // heard of the word.
        if let Some(suffix) = &m.suffix {
            let registers = self.text.registers(9);
            self.text.apply(&suffix.record, &registers)?;
        }
        self.session.accept(m.dict, first.state.clone());

        for event in &events {
            let Event::Cmd { name, arg } = event else {
                continue;
            };
            // A stack command needs no stroke put back, so it runs where it is.
            if self.session.stack_command(name, arg) {
                return Ok((
                    Outcome::Stack {
                        name: name.clone(),
                        arg: arg.clone(),
                    },
                    None,
                ));
            }
            let engine = matches!(
                name.as_str(),
                "engine.undo" | "engine.repeat_stroke" | "engine.toggle_star"
            );
            if !engine {
                continue;
            }
            // Each one first takes its own stroke back: the command wrote nothing,
            // so leaving it there would cost an extra undo later.
            self.take_back();
            let fire = match name.as_str() {
                "engine.undo" => {
                    self.take_back();
                    None
                }
                "engine.repeat_stroke" => self.session.history().last().copied(),
                "engine.toggle_star" => match self.session.history().last().copied() {
                    Some(last) => {
                        self.take_back();
                        Some(self.toggled(last))
                    }
                    None => None,
                },
                _ => None,
            };
            return Ok((Outcome::Engine { name: name.clone() }, fire));
        }
        // The context is what the host computes from its own output, so it is
        // recomputed here rather than being remembered.
        let tail = self.text.tail().clone();
        let context = self.session.context_mut();
        context.set("CAP", if tail.case.is_some() { "on" } else { "off" });
        let last_word = self.text.last_word_case();
        self.session.context_mut().set("W1", last_word);

        Ok((
            Outcome::Match {
                strokes: m.strokes,
                dict: dict.name().to_string(),
                suffix: m.suffix.as_ref().map(ToString::to_string),
                emissions: m.emissions.iter().map(Emission::to_string).collect(),
            },
            None,
        ))
    }

    /// Take back one stroke and whatever it wrote.
    fn take_back(&mut self) {
        if self.session.undo().is_some() {
            self.take_text_back();
        }
    }

    /// Undo the text back to where the last stroke found it.
    ///
    /// Not one `Text::undo` per stroke: a stroke that matched nothing wrote no
    /// unit, and a stroke that superseded a run wrote one where several had been.
    /// The mark is what says how many.
    fn take_text_back(&mut self) {
        let mark = self.marks.pop().unwrap_or(0);
        self.snaps.pop_back();
        while self.text.units() > mark {
            self.text.undo();
        }
    }

    /// Briefer outlines for the trailing 1..=`max_words` words of the text.
    ///
    /// For each trailing span, the strokes that wrote it are counted back
    /// through the per-stroke snapshots, and every dictionary in the stack is
    /// asked to read the span backwards. The cheapest outline that saves
    /// strokes is kept: strictly fewer for a phrase, no more (and different)
    /// for a single word. A span the buffer cannot isolate (a word written
    /// mid-stroke, or text older than the snapshots) yields nothing.
    pub fn hints(&self, max_words: u8) -> Vec<Hint> {
        let current = self.text.to_string();
        let mut out = Vec::new();
        let mut seen_start = usize::MAX;
        for words in 1..=max_words {
            let (start, phrase) = self.text.span(words);
            // The span saturates at the head of the buffer; asking wider
            // repeats the same phrase.
            if start == seen_start {
                break;
            }
            seen_start = start;
            let phrase = phrase.trim().to_string();
            if !phrase.chars().any(char::is_alphanumeric) {
                continue;
            }
            let Some(strokes) = self.window(&phrase, &current) else {
                continue;
            };
            if let Some(hint) = self.hint_for(&phrase, words as usize, strokes) {
                out.push(hint);
            }
        }
        out
    }

    /// How many trailing strokes wrote exactly `phrase`, or None when no
    /// stroke boundary isolates it.
    fn window(&self, phrase: &str, current: &str) -> Option<usize> {
        let total = self.snaps.len();
        let history = self.session.history().len();
        for n in 1..=total {
            let boundary = if n < total {
                self.snaps[total - 1 - n].as_str()
            } else if total == history {
                ""
            } else {
                // Strokes older than the snapshots: the span cannot be counted.
                return None;
            };
            // A stale snapshot (its stroke was superseded) is not a prefix of
            // the text any more, so it cannot be the edge of the window.
            let Some(wrote) = current.strip_prefix(boundary) else {
                continue;
            };
            let wrote = wrote.trim();
            if wrote == phrase {
                return Some(n);
            }
            if wrote.len() > phrase.len() {
                return None;
            }
        }
        None
    }

    /// The best briefer outline for `phrase`, written with `strokes` strokes.
    fn hint_for(&self, phrase: &str, words: usize, strokes: usize) -> Option<Hint> {
        let history = self.session.history();
        if strokes > history.len() {
            return None;
        }
        let typed = self
            .system
            .format_outline(&Outline(history[history.len() - strokes..].to_vec()));
        // Exact text first; the lowercase variant after it, so a brief still
        // surfaces for the word that opened a sentence.
        let mut records = vec![Record::text(phrase)];
        let lower = phrase.to_lowercase();
        if lower != phrase {
            records.push(Record::text(lower.as_str()));
        }
        let mut best: Option<(usize, usize, Outline)> = None;
        for (prio, record) in records.iter().enumerate() {
            for (rank, dict) in self.stack.dicts().iter().enumerate() {
                let Ok(found) = dict.outlines(record, 1) else {
                    continue;
                };
                for tape in &found.tapes {
                    let Ok(outline) = self.system.from_tape(tape) else {
                        continue;
                    };
                    let cost = (outline.0.len(), prio * self.stack.dicts().len() + rank);
                    if best.as_ref().map_or(true, |(s, r, _)| (*s, *r) > cost) {
                        best = Some((cost.0, cost.1, outline));
                    }
                }
            }
        }
        let (short, _, outline) = best?;
        let brief = self.system.format_outline(&outline);
        let worth = if words > 1 {
            short < strokes
        } else {
            short <= strokes && brief != typed
        };
        worth.then(|| Hint {
            text: phrase.to_string(),
            outline: brief,
        })
    }

    /// The same stroke with the star key flipped, which is what `{*}`
    /// re-translates.
    fn toggled(&self, stroke: Stroke) -> Stroke {
        let Some(star) = self.system.index_of_symbol('*') else {
            return stroke;
        };
        let mut out = Stroke::EMPTY;
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
}
