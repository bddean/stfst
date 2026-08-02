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

use crate::ortho;
use crate::{Cell, ModeCase, Tail, Text};

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

        let flags = &record.flags;
        let attached = self.tail.attach
            || flags.attach_l
            || flags.ortho_l
            || flags.space == Some(SpaceOp::Cancel)
            || (self.tail.glue && flags.glue);
        // An orthographic join is a join *plus* a rewrite, and it only applies to a
        // word the previous record left unfinished.
        self.ortho = flags.ortho_l && !self.tail.word_end;
        // Only a record that writes text opens a gap. `[CAP_NEXT]` alone must not:
        // it is a note for the next record, and a space here would strand the
        // word it was meant to join.
        let writes = record
            .body
            .iter()
            .any(|i| matches!(i, Item::Text(_) | Item::Copy(_) | Item::Glyph(_)));
        if writes && !attached {
            let space = self.mode.space.clone();
            self.gap(&space);
        }
        if flags.space == Some(SpaceOp::Retro) {
            self.retro_gap();
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

        self.tail = Tail {
            attach: flags.attach_r,
            glue: flags.glue,
            word_end: flags.word_end,
            // `[CARRY_CAP]` passes a pending capitalisation on rather than
            // spending it, which is what lets `{~|'}` write an apostrophe inside a
            // word without eating the capital. A record that wrote nothing passes
            // it on too: there was no letter to spend it on.
            case: match (flags.case, flags.carry_cap || wrote.is_none()) {
                (Some(case), _) => Some(case),
                (None, true) => self.tail.case,
                (None, false) => None,
            },
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
            }
        }
        self.write(&text);
        if let Some(last) = self.cells.last_mut() {
            if last.space.is_empty() {
                last.space = space;
            }
        }
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
    fn retro_gap(&mut self) {
        let at = self.cells.len().saturating_sub(2);
        if let Some(cell) = self.cells.get_mut(at) {
            if cell.space.is_empty() {
                cell.space = " ".to_string();
            }
        }
    }

    /// Append text, splitting it into words. It joins the word before it when
    /// there is no gap, which covers both an attaching record and one item
    /// following another inside one record.
    fn write(&mut self, text: &str) {
        for (n, (word, space)) in split_words(text).into_iter().enumerate() {
            if n > 0 {
                self.ortho = false;
            }
            let join = n == 0
                && self
                    .cells
                    .last()
                    .is_some_and(|c| c.space.is_empty() && !c.word.is_empty());
            match join {
                true => {
                    let rewritten = self
                        .ortho
                        .then(|| ortho::join(&self.cells.last().expect("a cell").word, &word))
                        .flatten();
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
        let first_word = at == 0 && from == 0;
        for (n, cell) in self.cells[at..].iter_mut().enumerate() {
            let skip = if n == 0 { from } else { 0 };
            let head: String = cell.word.chars().take(skip).collect();
            let rest: String = cell.word.chars().skip(skip).collect();
            let cased = match mode {
                ModeCase::Caps => rest.to_uppercase(),
                ModeCase::Lower => rest.to_lowercase(),
                ModeCase::Title => title(&rest),
                // Camel is title case with the very first word left alone, which is
                // what makes the run read as one identifier.
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
            // `{:infl:…}`: the regular cases, which are the orthography rules. An
            // irregular form is a lexicon, and this host has none, so `run` and
            // `ed` come back as `runed` rather than as nothing: the entry is
            // reported by the checker rather than failing silently.
            "infl" => {
                let suffix = match arg {
                    "ed" => "ed",
                    "ing" => "ing",
                    "ofs" | "s" => "s",
                    "ofz" | "z" => "'s",
                    _ => return Err(FormatError::BadMeta(name.into(), arg.into())),
                };
                let word = cell.word.clone();
                cell.word = ortho::join(&word, suffix).unwrap_or(word + suffix);
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
        // `{:infl:…}`: the regular cases, which are the orthography rules.
        assert_eq!(text(&["run", "[META=infl]ing[END]"]), "running");
        assert_eq!(text(&["carry", "[META=infl]ofs[END]"]), "carries");
        assert_eq!(text(&["dog", "[META=infl]ofz[END]"]), "dog's");
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
    fn an_orthographic_join_is_not_a_plain_join() {
        // `{^ing}` after `run`, against `{^^}ing` which only closes the gap.
        assert_eq!(text(&["run", "ing[ORTHO_L]"]), "running");
        assert_eq!(text(&["run", "ing[ATTACH_L]"]), "runing");
        // `{$}` says the word is finished, so nothing folds into it.
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
