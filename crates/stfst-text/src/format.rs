//! A record applied to the text so far.
//!
//! The record language's own semantics, written once: where the space goes, what
//! a pending capitalisation does, which items are text at all. Everything a host
//! must agree on to be a host, minus the parts only the host can do (press a key,
//! run a command), which come back as events.
//!
//! Not a full Plover formatter. Orthography is absent, and so is anything that
//! needs a dictionary lookup, because a formatter that looked things up would put
//! the two concerns back together.

use stfst_core::record::{Case, Item, Mods, Record, SpaceOp};
use stfst_core::window::{Class, Window};

use crate::{Cell, ModeCase, Tail, Text, Undecided};

/// What only the host can carry out.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Event {
    Key { name: String, mods: Mods },
    Cmd { name: String, arg: String },
}

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum FormatError {
    #[error("no operation named {0}")]
    UnknownMeta(String),
    #[error("[COPY{0}] but the host bound {1} words")]
    NoRegister(u8, usize),
    #[error("the record wants {wanted} characters of window and the host showed {had}")]
    ShortWindow { wanted: usize, had: usize },
    #[error("this record rewrites a window, and none was shown")]
    NoWindow,
    #[error("{0} cannot take the argument {1:?}")]
    BadMeta(String, String),
    #[error("this host has no morphology artifacts, so it cannot inflect")]
    NoMorph,
    #[error("{0:?} is not a pattern: {1}")]
    BadPattern(String, String),
}

impl Text {
    /// Write one record, and say what the host still has to do.
    ///
    /// `registers` are the words `[COPYn]` splices, newest first, which is what
    /// `Text::registers` returns. A host that bound fewer than a record asks for
    /// is a host that read the manifest's window wrong, so that is an error and
    /// not an empty splice.
    pub fn apply(
        &mut self,
        record: &Record,
        registers: &[String],
    ) -> Result<Vec<Event>, FormatError> {
        self.open(record);
        let undecided = self.tail.undecided.clone();
        let before = undecided.as_ref().map(|_| self.to_string());
        let events = self.put(record, registers, None)?;
        if let (Some(undecided), Some(before), true) = (undecided, before, self.tail.wrote) {
            self.decide(undecided, &before, record, registers)?;
        }
        Ok(events)
    }

    /// Write a choice again now that the text after it exists, and what came
    /// after it the same as before.
    ///
    /// The text it reads is what this unit added to the buffer less the gap
    /// before it, so a suffix that joined is read as itself: `{^ing}` is "ing",
    /// as it is in Plover.
    fn decide(
        &mut self,
        undecided: Undecided,
        before: &str,
        record: &Record,
        registers: &[String],
    ) -> Result<(), FormatError> {
        let after = self.to_string();
        let same = before.chars().zip(after.chars()).take_while(|(a, b)| a == b).count();
        let next: String = after.chars().skip(same).collect();
        let next = next.trim_start();
        self.restore(*undecided.from);
        for (n, (unit, registers)) in undecided.units.iter().enumerate() {
            self.put(unit, registers, (n == 0).then_some(next))?;
        }
        self.put(record, registers, None)?;
        Ok(())
    }

    /// Write a record's body and leave the tail it leaves, with `next` as the
    /// text its choices read. `None` writes each one undecided.
    fn put(
        &mut self,
        record: &Record,
        registers: &[String],
        next: Option<&str>,
    ) -> Result<Vec<Event>, FormatError> {
        let asks = next.is_none() && record.body.iter().any(|i| matches!(i, Item::IfNext(_)));
        let from = asks.then(|| Box::new(self.snapshot()));
        let flags = &record.flags;
        let attached = self.tail.attach
            || flags.attach_l
            || flags.ortho_l
            || (self.tail.glue && flags.glue);
        // An orthographic join is a join *plus* a rewrite, and it only applies to a
        // word the previous record left unfinished.
        self.ortho = flags.ortho_l && !self.tail.word_end;
        // Only a record that writes text opens a gap. `[CAP_NEXT]` alone must not:
        // it is a note for the next record, and a space here would strand the
        // word it was meant to join.
        let writes = record.body.iter().any(|i| {
            matches!(i, Item::Text(_) | Item::Copy(_) | Item::Glyph(_) | Item::IfNext(_))
        });
        if writes && !attached {
            let space = self.mode.space.clone();
            self.gap(&space);
        }
        // Both space operations edit the gap already written, which is the gap
        // between the last two units. Plover spells them `{*?}` and `{*!}` and
        // resolves both before its formatter sees them, so this is where they
        // land here.
        match flags.space {
            Some(SpaceOp::Retro) => self.retro_gap(),
            Some(SpaceOp::Cancel) => self.retro_join(),
            None => {}
        }

        let mut events = Vec::new();
        let start = self.cells.len();
        // Where a joining write would land, so the first letter this record wrote
        // can be found again afterwards even when it went inside an existing word.
        let cut = self
            .cells
            .last()
            .filter(|c| c.space.is_empty() && !c.word.is_empty())
            .map(|c| c.word.chars().count());
        for _ in 0..flags.repeat.get() {
            for item in &record.body {
                match item {
                    Item::Text(text) => self.write(text),
                    Item::Copy(n) => {
                        let word = registers.get(*n as usize - 1).ok_or(
                            FormatError::NoRegister(*n, registers.len()),
                        )?;
                        self.write(&word.clone());
                    }
                    Item::Key { name, mods } => events.push(Event::Key {
                        name: name.clone(),
                        mods: *mods,
                    }),
                    // A mode is this crate's business; anything else is the
                    // host's, so it leaves as an event.
                    Item::Cmd { name, arg } => match self.mode_command(name, arg) {
                        true => {}
                        false => events.push(Event::Cmd {
                            name: name.clone(),
                            arg: arg.clone(),
                        }),
                    },
                    // A glyph is a window character, and a record that holds one
                    // belongs to `rewrite`, not here.
                    Item::Glyph(_) | Item::Drop => return Err(FormatError::NoWindow),
                    Item::Meta { name, arg } => self.meta(name, arg)?,
                    Item::IfNext(choice) => {
                        let text = choice.text(next).map_err(|e| {
                            FormatError::BadPattern(choice.pattern.clone(), e.to_string())
                        })?;
                        self.write(text);
                    }
                }
            }
        }
        // A case applies to the first letter this record wrote, wherever the
        // record chose to start: attached, that is inside the word already there.
        let wrote = self.first_written(start, cut);
        if let (Some(at), Some(case)) = (wrote, self.tail.case) {
            self.recase(at, case);
        }
        // A mode applies to every word this record wrote, and after the pending
        // case, so `{-|}` inside `{mode:lower}` still loses.
        if let (Some(mode), Some((at, from))) = (self.mode.case, wrote) {
            self.apply_mode(mode, at, from);
        }

        // A record that wrote no word left nothing of its own, so what the unit
        // before it left passes on. That is what `[CARRY_CAP]` says about the
        // case, and Plover says it about all of them: a keystroke between two
        // words does not open the gap the attachment closed.
        let kept = wrote.is_none();
        // A record that wrote nothing is not the text a choice waits for, so it
        // joins the units the choice writes again.
        let undecided = match (from, self.tail.undecided.take()) {
            (Some(from), _) => Some(Undecided {
                from,
                units: vec![(record.clone(), registers.to_vec())],
            }),
            (None, Some(mut waiting)) if kept => {
                waiting.units.push((record.clone(), registers.to_vec()));
                Some(waiting)
            }
            (None, _) => None,
        };
        self.tail = Tail {
            attach: flags.attach_r || (kept && self.tail.attach),
            glue: flags.glue || (kept && self.tail.glue),
            word_end: flags.word_end || (kept && self.tail.word_end),
            wrote: !kept,
            start: match (self.cells.len() > start, kept) {
                (true, _) => Some(self.cells.len() - start),
                (false, true) => self.tail.start,
                (false, false) => None,
            },
            // `[CARRY_CAP]` passes a pending capitalisation on rather than
            // spending it, which is what lets `{~|'}` write an apostrophe inside a
            // word without eating the capital. A record that wrote nothing passes
            // it on too: there was no letter to spend it on.
            case: match (flags.case, flags.carry_cap || wrote.is_none()) {
                (Some(case), _) => Some(case),
                (None, true) => self.tail.case,
                (None, false) => None,
            },
            undecided,
        };
        Ok(events)
    }

    /// Start a unit: remember where to undo to, take back what this record
    /// replaces, and remember where its own writing begins.
    fn open(&mut self, record: &Record) {
        self.mark();
        if let Some(n) = record.flags.replace {
            self.revert(n.get());
        }
        self.base();
        self.trim();
    }

    /// Where the first character this record wrote sits, as a cell and an offset
    /// into it. `None` when it wrote none.
    fn first_written(&self, start: usize, cut: Option<usize>) -> Option<(usize, usize)> {
        if let Some(n) = cut {
            let joined = self.cells[start - 1].word.chars().count() > n;
            if joined {
                return Some((start - 1, n));
            }
        }
        (self.cells.len() > start).then_some((start, 0))
    }

    /// Write a record that rewrites the window it was shown.
    ///
    /// The whole capability a rewriting dictionary needs: the last `words` words
    /// go, and what the record says takes their place. Nothing here knows which
    /// operation ran, and that is the point. A class glyph in the record means
    /// "the next character of the window", so the casing is applied here, with the
    /// host's own Unicode rules, and the transducer only decided where.
    pub fn rewrite(
        &mut self,
        record: &Record,
        window: &Window,
        words: u8,
    ) -> Result<Vec<Event>, FormatError> {
        self.open(record);
        let (start, _) = self.span(words);
        let space = match self.cells.last() {
            Some(last) => last.space.clone(),
            None => String::new(),
        };
        self.cells.truncate(start);

        let mut events = Vec::new();
        let mut text = String::new();
        let mut taken = 0usize;
        for item in &record.body {
            match item {
                Item::Text(t) => text.push_str(t),
                Item::Drop => {
                    if taken >= window.chars.len() {
                        return Err(FormatError::ShortWindow {
                            wanted: taken + 1,
                            had: window.chars.len(),
                        });
                    }
                    taken += 1;
                }
                Item::Glyph(class) => {
                    let c = *window.chars.get(taken).ok_or(FormatError::ShortWindow {
                        wanted: taken + 1,
                        had: window.chars.len(),
                    })?;
                    taken += 1;
                    match class {
                        Class::Upper => text.extend(c.to_uppercase()),
                        Class::Letter => text.extend(c.to_lowercase()),
                        _ => text.push(c),
                    }
                }
                Item::Key { name, mods } => events.push(Event::Key {
                    name: name.clone(),
                    mods: *mods,
                }),
                Item::Cmd { name, arg } => events.push(Event::Cmd {
                    name: name.clone(),
                    arg: arg.clone(),
                }),
                Item::Copy(n) => return Err(FormatError::NoRegister(*n, 0)),
                Item::Meta { name, .. } => {
                    return Err(FormatError::UnknownMeta(name.clone()))
                }
                Item::IfNext(choice) => text.push_str(&choice.otherwise),
            }
        }
        self.write(&text);
        if let Some(last) = self.cells.last_mut() {
            if last.space.is_empty() {
                last.space = space;
            }
        }
        // The rest of the tail is the one the rewritten words left, because a
        // rewrite takes their place rather than following them. A choice among
        // them stays as it was written.
        self.tail.wrote = !text.is_empty();
        self.tail.undecided = None;
        Ok(events)
    }

    /// Put back the text as the unit `n` back found it, so this record replaces
    /// what that unit wrote and nothing older.
    ///
    /// The snapshot this unit already took stays in place, so undoing this record
    /// restores what it replaced: a fold is undoable one step at a time.
    fn revert(&mut self, n: u8) {
        let at = self.bases.len().saturating_sub(n as usize);
        if let Some(snapshot) = self.bases.get(at).cloned() {
            self.restore(snapshot);
        }
    }

    /// Set the gap after the last word, if it has none.
    fn gap(&mut self, space: &str) {
        if let Some(last) = self.cells.last_mut() {
            if last.space.is_empty() {
                last.space = space.to_string();
            }
        }
    }

    /// `{*?}`: put a space back between the last two words.
    /// The word whose trailing gap separates the last two units, which is what a
    /// retrospective space operation edits.
    fn unit_gap(&self) -> Option<usize> {
        self.cells.len().checked_sub(self.tail.start? + 1)
    }

    /// Put the gap between the last two units back: `{*?}`.
    fn retro_gap(&mut self) {
        let space = self.mode.space.clone();
        if let Some(cell) = self.unit_gap().and_then(|at| self.cells.get_mut(at)) {
            if cell.space.is_empty() {
                cell.space = space;
            }
        }
        self.tail.start = None;
    }

    /// Take the gap between the last two units away, which makes them one word:
    /// `{*!}`.
    fn retro_join(&mut self) {
        if let Some(at) = self.unit_gap() {
            if at + 1 < self.cells.len() && !self.cells[at].space.is_empty() {
                let next = self.cells.remove(at + 1);
                self.cells[at].word.push_str(&next.word);
                self.cells[at].space = next.space;
            }
        }
        // The boundary is spent either way. A second operation on it has nothing
        // to edit, which is Plover's answer too.
        self.tail.start = None;
    }

    /// Append text, splitting it into words. It joins the word before it when
    /// there is no gap, which covers both an attaching record and one item
    /// following another inside one record.
    fn write(&mut self, text: &str) {
        // A space inside a translation is a space between words, so a mode that
        // changed what goes between words changed this one too: under
        // `{mode:snake}`, "a b" is "a_b". Per character, so two spaces stay two, and
        // only a space, because a tab is not a word gap. The words stay separate
        // cells either way, which is what lets camel case the second one.
        let space_char = self.mode.space.clone();
        let respace = |gap: String| match space_char == " " {
            true => gap,
            false => gap.replace(' ', &space_char),
        };
        for (n, (word, space)) in split_words(text)
            .into_iter()
            .map(|(w, s)| (w, respace(s)))
            .enumerate()
        {
            // An orthographic join happens at one boundary, so it is spent on the
            // first word and every word after it is a plain append. That holds across
            // items too, which is what `{^ia}{,}` needs: the rule joins "ia" and the
            // comma that follows is only a comma.
            let joining = std::mem::take(&mut self.ortho) && n == 0;
            let join = n == 0
                && self
                    .cells
                    .last()
                    .is_some_and(|c| c.space.is_empty() && !c.word.is_empty());
            match join {
                true => {
                    let rewritten = match (joining, &self.morph) {
                        (true, Some(morph)) => {
                            Some(morph.join(&self.cells.last().expect("a cell").word, &word))
                        }
                        _ => None,
                    };
                    let last = self.cells.last_mut().expect("a cell to join");
                    match rewritten {
                        Some(whole) => last.word = whole,
                        None => last.word.push_str(&word),
                    }
                    last.space = space;
                }
                false => self.cells.push(Cell { word, space }),
            }
        }
    }

    /// Apply a pending case to the first letter at or after this position.
    ///
    /// `{<}` is the odd one: it cases the whole word rather than its first letter,
    /// so it does not stop at the first letter it changes.
    fn recase(&mut self, (at, from): (usize, usize), case: Case) {
        let Some(cell) = self.cells.get_mut(at) else {
            return;
        };
        let mut out = String::new();
        let mut done = false;
        for (i, c) in cell.word.chars().enumerate() {
            if done || i < from || !c.is_alphabetic() {
                out.push(c);
                continue;
            }
            match case {
                Case::Next => out.extend(c.to_uppercase()),
                Case::LowerNext => out.extend(c.to_lowercase()),
                Case::UpperNext => {
                    out.extend(c.to_uppercase());
                    continue;
                }
            }
            done = true;
        }
        cell.word = out;
    }

    /// A mode over every word from this position on, which is what a mode does
    /// that a pending case does not: it applies to all of them and it persists.
    fn apply_mode(&mut self, mode: ModeCase, at: usize, from: usize) {
        // Spent once. Camel's lowercase word is the first since the mode was set,
        // so it belongs to the mode and not to the buffer: "run" then
        // `{mode:camel}` then "one two" is "runoneTwo".
        let first_word = std::mem::take(&mut self.mode.camel_first);
        for (n, cell) in self.cells[at..].iter_mut().enumerate() {
            let skip = if n == 0 { from } else { 0 };
            let head: String = cell.word.chars().take(skip).collect();
            let rest: String = cell.word.chars().skip(skip).collect();
            let cased = match mode {
                ModeCase::Caps => rest.to_uppercase(),
                ModeCase::Lower => rest.to_lowercase(),
                ModeCase::Title => title(&rest),
                // Camel is title case with its first word left alone, which is what
                // makes the run read as one identifier.
                ModeCase::Camel if first_word && n == 0 => rest,
                ModeCase::Camel => title(&rest),
            };
            cell.word = head + &cased;
        }
    }

    /// `{mode:…}`. True when this crate handled it.
    fn mode_command(&mut self, name: &str, arg: &str) -> bool {
        self.mode.case = match name {
            "mode.caps" => Some(ModeCase::Caps),
            "mode.title" => Some(ModeCase::Title),
            "mode.lower" => Some(ModeCase::Lower),
            "mode.camel" => Some(ModeCase::Camel),
            "mode.snake" => {
                self.mode.space = "_".to_string();
                None
            }
            "mode.reset_case" => None,
            "mode.set_space" => {
                self.mode.space = arg.to_string();
                return true;
            }
            "mode.reset_space" => {
                self.mode.space = " ".to_string();
                return true;
            }
            "mode.reset" => {
                self.mode = crate::Mode::default();
                return true;
            }
            _ => return false,
        };
        // Camel runs words together, and snake puts an underscore between them, so
        // both are a case and a space at once.
        if name == "mode.camel" {
            self.mode.space = String::new();
            self.mode.camel_first = true;
        }
        true
    }

    /// The declared metas: edits to text already written. Everything here is a
    /// Plover built-in, because a dictionary-specific edit belongs in the
    /// dictionary, over a window.
    fn meta(&mut self, name: &str, arg: &str) -> Result<(), FormatError> {
        let Some(cell) = self.cells.last_mut() else {
            return Ok(());
        };
        match name {
            "retro.cap" => cell.word = title(&cell.word),
            "retro.upper" => cell.word = cell.word.to_uppercase(),
            "retro.lower" => cell.word = cell.word.to_lowercase(),
            // `{*($c)}`: the argument is the format, with `c` where the number goes.
            "retro.currency" => {
                let digits: String = cell.word.chars().filter(|c| c.is_ascii_digit()).collect();
                if digits.is_empty() {
                    return Ok(());
                }
                let (before, after) = arg.split_once('c').unwrap_or((arg, ""));
                cell.word = format!("{before}{}{after}", grouped(&digits));
            }
            // `{:infl:…}`: the lexicon, and the rules only where it agrees with
            // them. Without a lexicon there is nothing honest to write, so this
            // fails rather than appending `ed` to `run`.
            "infl" => {
                let word = cell.word.clone();
                let morph = self.morph.as_ref().ok_or(FormatError::NoMorph)?;
                cell.word = morph
                    .inflect(&word, arg)
                    .ok_or_else(|| FormatError::BadMeta(name.into(), arg.into()))?;
            }
            _ => return Err(FormatError::UnknownMeta(name.to_string())),
        }
        Ok(())
    }
}

/// The first letter capitalised, the rest left alone.
fn title(word: &str) -> String {
    let mut out = String::new();
    let mut done = false;
    for c in word.chars() {
        match !done && c.is_alphabetic() {
            true => {
                out.extend(c.to_uppercase());
                done = true;
            }
            false => out.push(c),
        }
    }
    out
}

/// Digits with a comma every three, counted from the right.
fn grouped(digits: &str) -> String {
    let mut out = String::new();
    for (n, c) in digits.chars().enumerate() {
        if n > 0 && (digits.len() - n) % 3 == 0 {
            out.push(',');
        }
        out.push(c);
    }
    out
}

/// Text as `(word, trailing whitespace)` pairs.
pub(crate) fn split_words(text: &str) -> Vec<(String, String)> {
    let mut out: Vec<(String, String)> = Vec::new();
    let mut word = String::new();
    let mut space = String::new();
    for c in text.chars() {
        match (c.is_whitespace(), space.is_empty()) {
            (true, _) => space.push(c),
            (false, false) => {
                out.push((std::mem::take(&mut word), std::mem::take(&mut space)));
                word.push(c);
            }
            (false, true) => word.push(c),
        }
    }
    if !word.is_empty() || !space.is_empty() {
        out.push((word, space));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn text(records: &[&str]) -> String {
        let mut out = Text::default();
        for r in records {
            let record = Record::parse_display(r).unwrap();
            let registers = out.registers(9);
            out.apply(&record, &registers).unwrap();
        }
        out.to_string()
    }

    /// The record's body written, then what its tail shows while it waits.
    fn preview(record: &str) -> String {
        let mut out = Text::default();
        let record = Record::parse_display(record).unwrap();
        let registers = out.registers(9);
        out.apply(&record, &registers).unwrap();
        out.preview(record.after.as_ref().unwrap())
    }

    #[test]
    fn a_waiting_tail_shows_its_close_around_a_word_that_is_not_there() {
        assert_eq!(preview("from[AFTER]to[END]"), "from to");
        assert_eq!(preview("\"[ATTACH_R][CARRY_CAP][AFTER]\"[ATTACH_L][END]"), "\"\"");
        // A join wants a word nobody has written, so the hyphen stands for it
        // rather than the tail joining the body: "ands" would be a lie.
        assert_eq!(preview("and[AFTER]s[ORTHO_L][END]"), "and -s");
        assert_eq!(preview("by[AFTER][META=infl]ing[END][END]"), "by -ing");
        // A choice reads the word that lands, not the tail waiting for it.
        assert_eq!(
            preview("[IF_NEXT](?i)[aeiou][THEN]an[ELSE]a[END][AFTER]is adding[END]"),
            "a is adding"
        );
    }

    #[test]
    fn a_choice_is_written_again_once_the_next_text_exists() {
        const AN: &str = "[IF_NEXT](?i)[aeiou][THEN]an[ELSE]a[END]";
        assert_eq!(text(&[AN]), "a");
        assert_eq!(text(&[AN, "egg"]), "an egg");
        assert_eq!(text(&[AN, "dog"]), "a dog");
        assert_eq!(text(&["such", AN, "Owl"]), "such an Owl");
        // The text a choice reads is what the next unit added, joined or not.
        assert_eq!(text(&["run", AN, "ing[ORTHO_L]"]), "run aning");
        // Each reads the one after it, so the first reads the second's guess.
        assert_eq!(text(&[AN, AN, "egg"]), "an an egg");
        // A unit that writes nothing is not the one it waits for, and what it
        // set still applies once the choice is written again.
        assert_eq!(text(&[AN, "[CAP_NEXT]", "egg"]), "an Egg");
        assert_eq!(text(&[AN, "[CMD=mode.caps][END]", "egg"]), "an EGG");
        assert_eq!(text(&["[CMD=mode.caps][END]", AN, "egg"]), "AN EGG");
        assert_eq!(text(&[".[CAP_NEXT]", AN, "egg"]), ". An egg");
        assert_eq!(text(&[AN, ".[ATTACH_L]", "egg"]), "a. egg");

        // Undoing the word that decided it brings the guess back, still waiting.
        let mut out = Text::default();
        for r in [AN, "egg"] {
            out.apply(&Record::parse_display(r).unwrap(), &[]).unwrap();
        }
        out.undo();
        assert_eq!(out.to_string(), "a");
        out.apply(&Record::parse_display("owl").unwrap(), &[]).unwrap();
        assert_eq!(out.to_string(), "an owl");
    }

    #[test]
    fn a_space_comes_between_words_and_not_before_the_first() {
        assert_eq!(text(&["hello", "world"]), "hello world");
        assert_eq!(text(&["hello", "ing[ATTACH_L]"]), "helloing");
        assert_eq!(text(&["cyto[ATTACH_R]", "plasm"]), "cytoplasm");
        assert_eq!(text(&["a", "[ATTACH_L][ATTACH_R]", "b"]), "ab");
    }

    #[test]
    fn a_case_applies_to_the_next_record_wherever_it_starts() {
        assert_eq!(text(&["[CAP_NEXT]", "hello"]), "Hello");
        assert_eq!(text(&["hello", "[CAP_NEXT]", "world"]), "hello World");
        // Attached, the letter to case is inside the word already written.
        assert_eq!(text(&["hello", "[CAP_NEXT]", "there[ATTACH_L]"]), "helloThere");
        // Carry-cap spends nothing, so the capital lands on the word after.
        assert_eq!(
            text(&["[CAP_NEXT]", "'[ATTACH_L][ATTACH_R][CARRY_CAP]", "tis"]),
            "'Tis"
        );
    }

    #[test]
    fn a_record_that_writes_no_word_leaves_the_tail_where_it_found_it() {
        // A keystroke between two words does not open the gap the attachment
        // closed, which is Plover's answer and the one the importer already gave
        // inside a record.
        assert_eq!(text(&["a[ATTACH_R]", "[KEY]c[CTRL][END]", "b"]), "ab");
        assert_eq!(text(&["a[GLUE]", "[KEY]c[END]", "b[GLUE]"]), "ab");
        // A flag the record sets is still its own.
        assert_eq!(text(&["a[ATTACH_R]", "[CMD=mode.reset][END]", "b"]), "ab");
    }

    #[test]
    fn a_space_operation_edits_the_gap_already_written() {
        assert_eq!(text(&["a", "b", "[CANCEL_SPACE]"]), "ab");
        assert_eq!(text(&["a", "b", "[CANCEL_SPACE]", "c"]), "ab c");
        // The boundary is spent, so a second operation finds nothing.
        assert_eq!(text(&["a", "b", "[CANCEL_SPACE]", "[RETRO_SPACE]"]), "ab");
        // The gap the last unit opened, not the last word gap: `in the` is one
        // unit and the join goes in front of it.
        assert_eq!(text(&["a", "in the", "[CANCEL_SPACE]"]), "ain the");
        // A unit that joined has no gap there, and neither has the first unit.
        assert_eq!(text(&["a", "b[ATTACH_L]", "[CANCEL_SPACE]"]), "ab");
        assert_eq!(text(&["a", "[CANCEL_SPACE]"]), "a");
        assert_eq!(text(&["[CANCEL_SPACE]"]), "");
        assert_eq!(text(&["a", "b", "[RETRO_SPACE]"]), "a b");
    }

    #[test]
    fn glue_joins_glue_and_nothing_else() {
        assert_eq!(text(&["a[GLUE]", "b[GLUE]"]), "ab");
        assert_eq!(text(&["a[GLUE]", "b"]), "a b");
        assert_eq!(text(&["a", "b[GLUE]"]), "a b");
    }

    #[test]
    fn a_register_splices_the_last_word() {
        // repeat-word: `and [COPY1]` after "more" is "more and more".
        assert_eq!(text(&["more", "and [COPY1]"]), "more and more");
        let mut out = Text::default();
        let record = Record::parse_display("and [COPY1]").unwrap();
        assert_eq!(
            out.apply(&record, &[]),
            Err(FormatError::NoRegister(1, 0))
        );
    }

    #[test]
    fn replace_puts_the_previous_unit_back_first() {
        // What a one-stroke retro fold does: the second record supersedes the
        // first, and undoing it restores the first.
        let mut out = Text::default();
        for r in ["it's", "it isn't[REPLACE1]"] {
            let record = Record::parse_display(r).unwrap();
            out.apply(&record, &[]).unwrap();
        }
        assert_eq!(out.to_string(), "it isn't");
        out.undo();
        assert_eq!(out.to_string(), "it's");
    }

    #[test]
    fn repetition_repeats_the_body() {
        assert_eq!(text(&["![REP3]"]), "!!!");
    }

    #[test]
    fn the_declared_metas_edit_what_is_written() {
        assert_eq!(text(&["hello", "[META=retro.cap][END]"]), "Hello");
        assert_eq!(text(&["hello", "[META=retro.upper][END]"]), "HELLO");
        assert_eq!(text(&["HELLO", "[META=retro.lower][END]"]), "hello");
        // `{*($c)}`: the argument is the format and `c` is where the number goes.
        assert_eq!(text(&["1234", "[META=retro.currency]$c[END]"]), "$1,234");
    }

    #[test]
    fn inflection_needs_a_lexicon_and_says_so() {
        // `run` and `ed` is `ran`, and no rule can know that, so a host with no
        // lexicon refuses instead of answering with the rules. Answering was the bug.
        let mut out = Text::default();
        let record = Record::parse_display("[META=infl]ed[END]").unwrap();
        out.apply(&Record::parse_display("run").unwrap(), &[]).unwrap();
        assert_eq!(out.apply(&record, &[]), Err(FormatError::NoMorph));
    }

    #[test]
    fn a_mode_persists_and_undoes() {
        assert_eq!(text(&["[CMD=mode.caps][END]", "one", "two"]), "ONE TWO");
        assert_eq!(text(&["[CMD=mode.snake][END]", "one", "two"]), "one_two");
        assert_eq!(text(&["[CMD=mode.camel][END]", "one", "two"]), "oneTwo");
        assert_eq!(
            text(&["[CMD=mode.caps][END]", "one", "[CMD=mode.reset][END]", "two"]),
            "ONE two"
        );
        assert_eq!(text(&["[CMD=mode.set_space]-[END]", "one", "two"]), "one-two");

        // A mode is host state, so taking back the stroke that set it turns it off.
        let mut out = Text::default();
        for r in ["[CMD=mode.caps][END]", "one"] {
            let record = Record::parse_display(r).unwrap();
            out.apply(&record, &[]).unwrap();
        }
        out.undo();
        out.undo();
        assert_eq!(out.mode().case, None);
    }

    #[test]
    fn an_orthographic_join_is_a_plain_join_with_no_rules_to_apply() {
        // What this crate decides on its own is *where* the join happens. What the
        // join spells is `morph`'s, and with no artifacts there is nothing to
        // spell: `running` is measured in `scripts/check-ortho.py`, not here.
        // `{^ing}` and `{^^}ing` close the same gap, and only the rules tell them
        // apart. `{$}` does not reopen it: it stops the rewrite, not the join.
        for record in ["ing[ORTHO_L]", "ing[ATTACH_L]"] {
            assert_eq!(text(&["run", record]), "runing");
        }
        assert_eq!(text(&["run[WORD_END]", "ing[ORTHO_L]"]), "runing");
    }

    #[test]
    fn undo_takes_back_one_unit() {
        let mut out = Text::default();
        for r in ["hello", "[CAP_NEXT]", "world"] {
            let record = Record::parse_display(r).unwrap();
            out.apply(&record, &[]).unwrap();
        }
        assert_eq!(out.to_string(), "hello World");
        out.undo();
        // The pending capital comes back with the text, or the redo differs.
        assert_eq!(out.to_string(), "hello");
        assert_eq!(out.tail().case, Some(Case::Next));
    }
}
