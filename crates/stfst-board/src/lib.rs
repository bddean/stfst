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
use stfst_dict::{Held, Session, Stack};
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
    /// Nothing matched, and a frame is waiting to read the text after its keys,
    /// so the stroke wrote nothing and its answer is not known yet. A record with
    /// only a tail is not this: its body writes at once and matched.
    Held,
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

/// A stroke that wrote nothing yet, because its record belongs to the text after
/// it.
#[derive(Clone)]
struct Waiting<'a> {
    /// Where its keys are in the stroke history, so the step it writes can report
    /// under its own outline.
    strokes: std::ops::Range<usize>,
    /// How many words the text held when the keys were read. What it waits for is
    /// the words written since, and a unit that writes none is not the one it
    /// wants: a command writes nothing, and an attached record joins into the word
    /// before it.
    words: usize,
    kind: Kind<'a>,
}

/// The two kinds of held stroke, which the host holds the same way.
#[derive(Clone)]
enum Kind<'a> {
    /// A frame *reads* the text it waits for, so what waits is the candidate: it
    /// cannot be harvested until there is a window to harvest against.
    Frame(Held<'a>),
    /// A suffix only *follows* that text, so what waits is the record, and the
    /// host's own join does the work when it lands.
    After {
        record: Record,
        /// The dictionary it came from, for the step it writes to name.
        dict: usize,
    },
}

/// What one stroke did, before the host turns it into steps.
struct Read {
    outcome: Outcome,
    /// The text after this stroke's own writing, which is not the text after the
    /// frame it completed.
    text: String,
    /// The frame this stroke gave a word to, and the outline that stroked it.
    framed: Option<(Outcome, String)>,
    /// A stroke the engine wants fired next.
    fire: Option<Stroke>,
}

/// One stroke's worth of history, and the text it left behind.
pub struct Step {
    pub outcome: Outcome,
    pub text: String,
    /// The outline this step belongs to, where it is not the stroke just read:
    /// `{*+}` and `{*}` both end by feeding a stroke of their own, and a held
    /// frame writes when a later stroke gives it a word. A host that labels its
    /// output needs to name which stroke it is reporting.
    pub label: Option<String>,
}

/// A briefer outline for text just written: one entry of a suggestions bar.
pub struct Hint {
    pub text: String,
    pub outline: String,
}

/// How many per-stroke text snapshots [`Board::hints`] can look back through.
/// Far more strokes than any trailing phrase spans.
const SNAPS: usize = 64;

/// The host's own state as one stroke found it: how many units the text held, so
/// a match spanning several strokes can put back what they wrote, and the frame
/// that was waiting for a word. A stack of these makes an undo a pop.
struct Mark<'a> {
    units: usize,
    held: Option<Waiting<'a>>,
}

pub struct Board<'a> {
    system: &'a System,
    stack: &'a Stack,
    session: Session<'a>,
    /// Absent where the host has no orthography artifacts. Then `[ORTHO_L]`
    /// degrades to a plain join and an inflection is refused, which is what
    /// `Text` already does about it.
    morph: Option<Arc<Morph>>,
    text: Text,
    /// What one stroke has to put back, per stroke read.
    marks: Vec<Mark<'a>>,
    /// A stroke that read its keys and is waiting for the text after them.
    held: Option<Waiting<'a>>,
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
            held: None,
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
        self.held = None;
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
            let read = self.read(stroke)?;
            // The newest snap may be an engine command's survivor rather than
            // this stroke's placeholder; either way it now reads this text.
            if let Some(last) = self.snaps.back_mut() {
                *last = self.text.to_string();
            }
            steps.push(Step {
                outcome: read.outcome,
                text: read.text,
                label: refired,
            });
            // A frame writes after the unit that gave it a word, and reports
            // under its own outline.
            if let Some((outcome, label)) = read.framed {
                steps.push(Step {
                    outcome,
                    text: self.text.to_string(),
                    label: Some(label),
                });
            }
            if let Some(next) = read.fire {
                let label = self.system.format_outline(&Outline(vec![next]));
                queue.push_back((next, Some(label)));
            }
        }
        Ok(steps)
    }

    /// One stroke through the runtime.
    fn read(&mut self, stroke: Stroke) -> Result<Read, BoardError> {
        self.marks.push(Mark {
            units: self.text.units(),
            held: self.held.clone(),
        });
        self.snaps.push_back(String::new());
        if self.snaps.len() > SNAPS {
            self.snaps.pop_front();
        }
        self.session.push(stroke);
        self.widest = self.widest.max(self.session.candidates());

        let text = &self.text;
        // Three questions, in this order: what matches the stroke as written,
        // what is waiting to read the text after it, and only then what a folded
        // key would mean. A held candidate matched the keys as written, so it
        // comes before the fold; all that waits is its answer.
        let mut found = self.session.best_with(|words| text.span(words).1)?;
        if found.is_none() {
            if let Some(held) = self.session.held() {
                self.held = Some(Waiting {
                    strokes: held.strokes(),
                    words: self.text.words(),
                    kind: Kind::Frame(held),
                });
                return Ok(self.unframed(Outcome::Held));
            }
            let text = &self.text;
            found = self.session.folded_with(|words| text.span(words).1)?;
        }
        let Some(m) = found else {
            return Ok(self.unframed(Outcome::Unmatched));
        };
        // A longer match supersedes what the strokes it covers already wrote, and
        // with them whatever the host was holding when they began.
        let mark = &self.marks[self.marks.len() - m.strokes];
        let target = mark.units;
        self.held = mark.held.clone();
        while self.text.units() > target {
            self.text.undo();
        }
        let dict = self.stack.dict(m.dict);
        let first = m.emissions.first().cloned().unwrap_or_default();
        let events = match (&m.window, dict.shown()) {
            (Some(window), Some(shown)) => {
                self.text.rewrite(&first.record, window, shown.words())?
            }
            _ => {
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
                return Ok(self.unframed(Outcome::Stack {
                    name: name.clone(),
                    arg: arg.clone(),
                }));
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
            return Ok(Read {
                outcome: Outcome::Engine { name: name.clone() },
                text: self.text.to_string(),
                framed: None,
                fire,
            });
        }
        // The context is what the host computes from its own output, so it is
        // recomputed here rather than being remembered.
        let tail = self.text.tail().clone();
        let context = self.session.context_mut();
        context.set("CAP", if tail.case.is_some() { "on" } else { "off" });
        let last_word = self.text.last_word_case();
        self.session.context_mut().set("W1", last_word);

        let outcome = Outcome::Match {
            strokes: m.strokes,
            dict: dict.name().to_string(),
            suffix: m.suffix.as_ref().map(ToString::to_string),
            emissions: m.emissions.iter().map(Emission::to_string).collect(),
        };
        // What was already waiting lands first: it belongs to the text this unit
        // wrote. Then a tail of this record's own starts waiting for the next.
        let framed = self.resolve()?;
        if let Some(tail) = &first.record.after {
            let end = self.session.history().len();
            self.held = Some(Waiting {
                strokes: end - m.strokes..end,
                words: self.text.words(),
                kind: Kind::After {
                    record: (**tail).clone(),
                    dict: m.dict,
                },
            });
        }
        Ok(Read {
            outcome,
            text: self.text.to_string(),
            framed,
            fire: None,
        })
    }

    /// A stroke that framed nothing: it wrote no text of its own, so no frame can
    /// have been waiting for it.
    fn unframed(&self, outcome: Outcome) -> Read {
        Read {
            outcome,
            text: self.text.to_string(),
            framed: None,
            fire: None,
        }
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
        let Some(mark) = self.marks.pop() else {
            return;
        };
        self.held = mark.held;
        self.snaps.pop_back();
        while self.text.units() > mark.units {
            self.text.undo();
        }
    }

    /// Write the frame that was waiting for a word, if this unit wrote one.
    ///
    /// The window is the unit's own words and nothing older, so a frame cannot
    /// reach past the text it was stroked for. A unit that wrote more words than
    /// the frame declared is text the frame cannot be shown, so it does not fire:
    /// showing the last few would frame the tail of a phrase.
    fn resolve(&mut self) -> Result<Option<(Outcome, String)>, BoardError> {
        let Some(held) = self.held.take() else {
            return Ok(None);
        };
        let wrote = self.text.words().saturating_sub(held.words);
        if wrote == 0 {
            // Nothing was written since the keys were read, so what waits waits on.
            self.held = Some(held);
            return Ok(None);
        }
        let label = self.outline(held.strokes.clone());
        let outcome = match held.kind {
            Kind::Frame(ref frame) => self.frame(frame, wrote)?,
            Kind::After { ref record, dict } => {
                let registers = self.text.registers(9);
                self.text.apply(record, &registers)?;
                Some(Outcome::Match {
                    strokes: held.strokes.len(),
                    dict: self.stack.dict(dict).name().to_string(),
                    suffix: None,
                    emissions: vec![record.to_string()],
                })
            }
        };
        Ok(outcome.map(|outcome| (outcome, label)))
    }

    /// Harvest a frame against the words this unit wrote, and write what comes
    /// back over them.
    fn frame(&mut self, held: &Held<'a>, wrote: usize) -> Result<Option<Outcome>, BoardError> {
        let words = match u8::try_from(wrote) {
            Ok(words) if words <= held.words() => words,
            _ => return Ok(None),
        };
        let span = self.text.span(words).1;
        let Some(m) = self.session.resolve(held, &span)? else {
            return Ok(None);
        };
        let window = m.window.as_ref().expect("a held match reads a window");
        let first = m.emissions.first().cloned().unwrap_or_default();
        self.text.rewrite(&first.record, window, words)?;
        self.session.accept(m.dict, first.state.clone());
        Ok(Some(Outcome::Match {
            strokes: m.strokes,
            dict: self.stack.dict(m.dict).name().to_string(),
            suffix: None,
            emissions: m.emissions.iter().map(Emission::to_string).collect(),
        }))
    }

    /// How an outline reads, for a step that reports a stroke the host has
    /// already read.
    fn outline(&self, strokes: std::ops::Range<usize>) -> String {
        let strokes = &self.session.history()[strokes];
        self.system.format_outline(&Outline(strokes.to_vec()))
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
