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
use stfst_dict::{Held, Session, Slot, Stack};
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
        /// What a fold wrote around the base: `and _`, `_ the`.
        fold: Option<String>,
        emissions: Vec<String>,
    },
}

/// A record, or part of one, that belongs to the text after the stroke that
/// wrote it.
#[derive(Clone)]
struct Waiting<'a> {
    /// Where its keys are in the stroke history, so the step it writes can report
    /// under its own outline.
    strokes: std::ops::Range<usize>,
    kind: Kind<'a>,
}

/// The two kinds of held stroke, which differ in what they wait for.
#[derive(Clone)]
enum Kind<'a> {
    /// A frame *reads* the text it waits for, so what waits is the candidate: it
    /// cannot be harvested until there is a window to harvest against. It waits
    /// for a *word*, because words are what it is shown.
    Frame {
        held: Held<'a>,
        /// How many words the text held when the keys were read. What the frame
        /// reads is the words written since, and a unit that wrote none is not
        /// the one it wants: a command writes nothing, and an attached record
        /// joins into the word before it.
        words: usize,
    },
    /// A tail only *follows* that text, so what waits is the record, and the
    /// host's own join does the work when it lands. It waits for text rather than
    /// for a word: `(` joined to the word after it wrote no word of its own, and
    /// the `)` still belongs after that word.
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

/// The folds' suffix keys, and the inflection each one asks for.
const SUFFIXES: &[(char, &str)] = &[('s', "s"), ('d', "ed"), ('g', "ing"), ('z', "z")];

/// How many per-stroke text snapshots [`Board::hints`] can look back through.
/// Far more strokes than any trailing phrase spans.
const SNAPS: usize = 64;

/// The host's own state as one stroke found it: how many units the text held, so
/// a match spanning several strokes can put back what they wrote, and the frame
/// that was waiting for a word. A stack of these makes an undo a pop.
struct Mark<'a> {
    units: usize,
    held: Option<Waiting<'a>>,
    /// What [`Board::unit`] answered before this stroke, so taking the stroke
    /// back takes the answer back with it.
    unit: usize,
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
    /// A command the dictionary asked the *host* for, waiting to be taken.
    /// Everything this board can do itself it has already done; what is left is
    /// a request to the keyboard around it, like opening a dialog.
    pending: Option<(String, String)>,
    /// How many strokes the last match consumed, which is what "the last thing I
    /// wrote" means to anything reaching back over it.
    unit: usize,
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
            pending: None,
            unit: 0,
        }
    }

    /// The text as it shows, which is not only what the strokes have written: a
    /// tail that is still waiting shows its close, so a frame is closed around
    /// the word before the word is there. See `Text::preview`.
    pub fn text(&self) -> String {
        match &self.held {
            Some(Waiting {
                kind: Kind::After { record, .. },
                ..
            }) => self.text.preview(record),
            // A frame reads the text it waits for, so its answer is not known
            // yet and there is nothing honest to show.
            _ => self.text.to_string(),
        }
    }

    /// How many strokes have been read, and how wide the candidate set ever got.
    pub fn strokes(&self) -> usize {
        self.session.history().len()
    }

    /// How many strokes the last match consumed, capped by what there is.
    pub fn unit(&self) -> usize {
        self.unit.clamp(1, self.strokes().max(1)).min(self.strokes())
    }

    /// The last `strokes` strokes, as an outline.
    pub fn tail(&self, strokes: usize) -> Outline {
        let history = self.session.history();
        let from = history.len().saturating_sub(strokes);
        Outline(history[from..].to_vec())
    }

    /// A command this board did not carry out, for the host to answer. Taken
    /// once: it is an event, not a state.
    pub fn take_command(&mut self) -> Option<(String, String)> {
        self.pending.take()
    }

    /// Take the last `strokes` back and make them again.
    ///
    /// What a definition made after the fact needs: the dictionaries have changed
    /// under the history, so the strokes are read a second time and write what
    /// they now mean, where they wrote what they used to.
    pub fn restroke(&mut self, strokes: usize) -> Result<Vec<Step>, BoardError> {
        let again: Vec<Stroke> = self.tail(strokes).0;
        for _ in 0..again.len() {
            self.undo();
        }
        let mut steps = Vec::new();
        for stroke in again {
            steps.extend(self.push(stroke)?);
        }
        Ok(steps)
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
            // What the strokes wrote, without a waiting tail's preview: a snap is
            // compared against the text as a prefix.
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
                    text: self.text(),
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
            unit: self.unit,
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
                    kind: Kind::Frame {
                        words: self.text.words(),
                        held,
                    },
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
        // A fold writes around its base, and the base never heard of it.
        let around = m.fold.clone().unwrap_or_default();
        self.write_plain(&around.before)?;
        let events = match (&m.window, dict.shown()) {
            (Some(window), Some(shown)) => {
                self.text.rewrite(&first.record, window, shown.words())?
            }
            _ => {
                let registers = self.text.registers(dict.registers());
                self.text.apply(&first.record, &registers)?
            }
        };
        self.write_plain(&around.after)?;
        self.session.accept(m.dict, first.state.clone());
        self.session.wrote(m.strokes);

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
                // Not one this board can carry out, so it waits for the host. The
                // stroke stays where it is: what a host does about its own command
                // stroke is the host's business, and taking one back here moved a
                // held frame that had nothing to do with it.
                self.pending = Some((name.clone(), arg.clone()));
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
                text: self.text(),
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

        self.unit = m.strokes;
        let outcome = Outcome::Match {
            strokes: m.strokes,
            dict: dict.name().to_string(),
            fold: m.fold.as_ref().map(ToString::to_string),
            emissions: m.emissions.iter().map(Emission::to_string).collect(),
        };
        // What was already waiting lands first: it belongs to the text this unit
        // wrote. Then a tail of this record's own starts waiting for the next.
        let framed = self.resolve()?;
        if let Some(tail) = &first.record.after {
            let end = self.session.history().len();
            self.held = Some(Waiting {
                strokes: end - m.strokes..end,
                kind: Kind::After {
                    record: (**tail).clone(),
                    dict: m.dict,
                },
            });
        }
        Ok(Read {
            outcome,
            text: self.text(),
            framed,
            fire: None,
        })
    }

    /// Write a record that reads no window and carries no state, if it writes
    /// anything.
    fn write_plain(&mut self, record: &Record) -> Result<(), BoardError> {
        if !record.is_empty() {
            let registers = self.text.registers(9);
            self.text.apply(record, &registers)?;
        }
        Ok(())
    }

    /// A stroke that framed nothing: it wrote no text of its own, so no frame can
    /// have been waiting for it.
    fn unframed(&self, outcome: Outcome) -> Read {
        Read {
            outcome,
            text: self.text(),
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
        self.unit = mark.unit;
        self.snaps.pop_back();
        while self.text.units() > mark.units {
            self.text.undo();
        }
    }

    /// Write what was waiting for this unit, if this unit is the one it wanted.
    ///
    /// A frame is shown the unit's own words and nothing older, so it cannot
    /// reach past the text it was stroked for. A unit that wrote more words than
    /// the frame declared is text the frame cannot be shown, so it does not fire:
    /// showing the last few would frame the tail of a phrase.
    fn resolve(&mut self) -> Result<Option<(Outcome, String)>, BoardError> {
        let Some(held) = self.held.take() else {
            return Ok(None);
        };
        let landed = match held.kind {
            Kind::Frame { words, .. } => self.text.words() > words,
            Kind::After { .. } => self.text.tail().wrote,
        };
        if !landed {
            // This unit is not the one it waits for, so it waits on.
            self.held = Some(held);
            return Ok(None);
        }
        let label = self.outline(held.strokes.clone());
        let outcome = match held.kind {
            Kind::Frame { ref held, words } => self.frame(held, self.text.words() - words)?,
            Kind::After { ref record, dict } => {
                self.write_plain(record)?;
                Some(Outcome::Match {
                    strokes: held.strokes.len(),
                    dict: self.stack.dict(dict).name().to_string(),
                    fold: None,
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
            fold: None,
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
        let mut texts = vec![phrase.to_string()];
        let lower = phrase.to_lowercase();
        if lower != phrase {
            texts.push(lower);
        }
        // Shortest first, then the exact text, then an outline a dictionary holds
        // over one a fold makes, then by `suggest` and the stack's order.
        let mut best: Option<((usize, usize, bool, i64, usize), Outline)> = None;
        let mut offer = |cost: (usize, usize, bool, i64, usize), outline: Outline| {
            if best.as_ref().is_none_or(|(b, _)| *b > cost) {
                best = Some((cost, outline));
            }
        };
        for (prio, text) in texts.iter().enumerate() {
            for (rank, dict) in self.stack.dicts().iter().enumerate() {
                for outline in self.outlines(dict, text) {
                    offer((outline.0.len(), prio, false, -dict.suggest(), rank), outline);
                }
            }
            if words == 1 {
                for (outline, suggest, rank) in self.folded(text) {
                    offer((outline.0.len(), prio, true, -suggest, rank), outline);
                }
            }
        }
        let ((short, ..), outline) = best?;
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

    /// The cheapest outline `dict` writes `text` with, if it has one.
    fn outlines(&self, dict: &Slot, text: &str) -> Vec<Outline> {
        let Ok(found) = dict.outlines(&Record::text(text), 1) else {
            return Vec::new();
        };
        found.tapes.iter().filter_map(|t| self.system.from_tape(t).ok()).collect()
    }

    /// The outlines a fold writes `word` with: a base's outline and a suffix key,
    /// where the base inflects to the word and a fresh board reads the outline
    /// back as it. Each with its base's dictionary's `suggest` and place.
    fn folded(&self, word: &str) -> Vec<(Outline, i64, usize)> {
        let Some(morph) = &self.morph else {
            return Vec::new();
        };
        let chars: Vec<char> = word.chars().collect();
        let mut out = Vec::new();
        for (key, mode) in SUFFIXES {
            let Some(key) = self.system.index_of_symbol(*key) else {
                continue;
            };
            let bases: Vec<String> = (1..=4.min(chars.len().saturating_sub(1)))
                .map(|n| chars[..chars.len() - n].iter().collect::<String>())
                .flat_map(|stem| ["", "e", "y"].map(|end| format!("{stem}{end}")))
                .filter(|base| morph.inflect(base, mode).as_deref() == Some(word))
                .collect();
            for base in bases {
                for (rank, dict) in self.stack.dicts().iter().enumerate() {
                    for Outline(mut strokes) in self.outlines(dict, &base) {
                        let Some(last) = strokes.last_mut() else { continue };
                        if last.contains(key) {
                            continue;
                        }
                        *last = last.with(key);
                        let outline = Outline(strokes);
                        if self.reads(&outline) == word {
                            out.push((outline, dict.suggest(), rank));
                        }
                    }
                }
            }
        }
        out
    }

    /// What `outline` writes on a board of its own.
    fn reads(&self, outline: &Outline) -> String {
        let mut board = Board::new(self.system, self.stack, self.morph.clone());
        for stroke in &outline.0 {
            if board.push(*stroke).is_err() {
                return String::new();
            }
        }
        board.text().trim().to_string()
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
