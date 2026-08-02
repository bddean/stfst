//! The other side of the record language: text a host has written, and the
//! operations a record can ask for over it.
//!
//! Nothing here looks anything up. A dictionary answers "what does this outline
//! mean"; this answers "what does that mean for what is on screen", and the two
//! are orthogonal enough that a host could replace either one.
//!
//! The buffer is a list of words, each with the whitespace that follows it,
//! oldest first. Words rather than characters because that is the unit a
//! dictionary asks for a window of, and each word carrying its own whitespace is
//! what lets a rewrite preserve exact spacing.
//!
//! There is no operation table here. A dictionary that rewrites text puts the
//! rule in the transducer and reads the window off the tape, so this side has one
//! general capability: show the last N words, and replace exactly those N words
//! with what came back. Recasing, joining, splitting and wrapping are not named
//! anywhere in this crate.

pub mod format;
pub mod ortho;

use std::collections::VecDeque;

/// How much text a host keeps, and how far back it can undo.
///
/// Both are policy rather than format, and both have to be bounded, because a
/// snapshot per unit over an unbounded buffer is quadratic: 9,349 strokes cost 320
/// microseconds each before this, almost all of it copying words nothing could
/// reach. The numbers are more than any window or undo run needs, and the document
/// a writer is filling lives in their editor, not here.
const CELLS: usize = 512;
const UNITS: usize = 64;

use stfst_core::record::Case;

/// One word and the whitespace after it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Cell {
    pub word: String,
    pub space: String,
}

impl Cell {
    pub fn new(word: &str, space: &str) -> Cell {
        Cell {
            word: word.to_string(),
            space: space.to_string(),
        }
    }
}

/// What the last unit left for the next one: the state a formatter carries.
///
/// Plover calls these the "pending" flags. They are here and not in the buffer
/// because they are not text: undoing a unit has to restore them, and appending
/// one has to consume them.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Tail {
    /// The previous record attached on its right, so no space follows it.
    pub attach: bool,
    /// The previous record was glue, so another glue record joins it.
    pub glue: bool,
    /// A case to apply to the next word.
    pub case: Option<Case>,
    /// The previous record said its word was finished, so a suffix must not fold
    /// into it: `{$}`.
    pub word_end: bool,
}

/// A persistent output mode, which a `[CMD=mode.*]` sets and only another command
/// clears. Not part of the tail: a mode outlives the unit that set it.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModeCase {
    Caps,
    Title,
    Lower,
    /// Title case with no space, and a lowercase first word.
    Camel,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Mode {
    pub case: Option<ModeCase>,
    /// What goes between words. `{mode:snake}` makes it an underscore.
    pub space: String,
}

impl Default for Mode {
    fn default() -> Self {
        Mode {
            case: None,
            space: " ".to_string(),
        }
    }
}

/// The text a host has written, and enough history to take a unit back.
#[derive(Debug, Default)]
pub struct Text {
    cells: Vec<Cell>,
    tail: Tail,
    mode: Mode,
    /// The buffer and tail before each unit, newest last. What makes undo a pop.
    ///
    /// A snapshot per unit rather than an inverse edit per unit: an edit is not
    /// always invertible (a retro operation can delete a distinction), and a
    /// steno host writes a few words a second, so the copy is not the cost.
    history: VecDeque<Snapshot>,
    /// The buffer as each unit found it *after* whatever that unit replaced, so
    /// before it wrote anything of its own.
    ///
    /// Not the same stack as `history`, and the difference is the whole meaning of
    /// `[REPLACE1]`. Undo of a fold has to restore what the fold replaced, so it
    /// wants the state before the unit. Another fold on top has to replace only
    /// what the previous fold *wrote*, so it wants the state that fold started
    /// from. Reverting to `history` instead brings the base phrase back and writes
    /// the new one after it.
    bases: VecDeque<Snapshot>,
    /// Units written, ever. A count rather than the length of `history`, which is
    /// capped: a caller comparing marks needs a number that only goes up.
    units: usize,
    /// Set while a record with `[ORTHO_L]` is writing its first text, so the join
    /// applies once and to the right join. Transient, and never observed from
    /// outside a call.
    pub(crate) ortho: bool,
}

/// The text and the state that goes with it, as one unit found it.
#[derive(Debug, Clone, Default)]
pub(crate) struct Snapshot {
    pub cells: Vec<Cell>,
    pub tail: Tail,
    pub mode: Mode,
}

impl Text {
    pub fn tail(&self) -> &Tail {
        &self.tail
    }

    pub fn mode(&self) -> &Mode {
        &self.mode
    }

    pub fn units(&self) -> usize {
        self.units
    }

    /// Everything written, as one string.
    pub fn to_string(&self) -> String {
        let mut out = String::new();
        for cell in &self.cells {
            out.push_str(&cell.word);
            out.push_str(&cell.space);
        }
        out
    }

    /// The last `n` words, newest first, which is the order the registers are
    /// numbered in: `[COPY1]` is the most recent word.
    pub fn registers(&self, n: u8) -> Vec<String> {
        self.cells
            .iter()
            .rev()
            .take(n as usize)
            .map(|c| c.word.clone())
            .collect()
    }

    /// Take back the last unit.
    pub fn undo(&mut self) -> bool {
        self.bases.pop_back();
        match self.history.pop_back() {
            Some(snapshot) => {
                self.units -= 1;
                self.restore(snapshot);
                true
            }
            None => false,
        }
    }

    pub(crate) fn snapshot(&self) -> Snapshot {
        Snapshot {
            cells: self.cells.clone(),
            tail: self.tail.clone(),
            mode: self.mode.clone(),
        }
    }

    pub(crate) fn restore(&mut self, snapshot: Snapshot) {
        self.cells = snapshot.cells;
        self.tail = snapshot.tail;
        self.mode = snapshot.mode;
    }

    /// Remember where a unit starts, so it can be taken back. The mode goes in
    /// too: undoing the stroke that turned capitals on has to turn them off.
    fn mark(&mut self) {
        let snapshot = self.snapshot();
        self.history.push_back(snapshot);
        self.units += 1;
        if self.history.len() > UNITS {
            self.history.pop_front();
        }
    }

    /// Remember where this unit's own writing starts, once it has replaced
    /// whatever it replaces.
    fn base(&mut self) {
        let snapshot = self.snapshot();
        self.bases.push_back(snapshot);
        if self.bases.len() > UNITS {
            self.bases.pop_front();
        }
    }

    /// Drop text nothing can reach any more.
    pub(crate) fn trim(&mut self) {
        if self.cells.len() > CELLS {
            self.cells.drain(..self.cells.len() - CELLS);
        }
    }

    /// Start from text a host has already written, for a caller that is testing
    /// what a rewrite does rather than how the text got there.
    pub fn seed(&mut self, written: &str) {
        for (word, space) in crate::format::split_words(written) {
            self.cells.push(Cell { word, space });
        }
    }

    /// The case of the newest word, as the `W1` context slot spells it.
    pub fn last_word_case(&self) -> &'static str {
        let Some(cell) = self.cells.last() else {
            return "none";
        };
        let mut letters = cell.word.chars().filter(|c| c.is_alphabetic()).peekable();
        let Some(first) = letters.next() else {
            return "none";
        };
        let rest_upper = letters.clone().all(|c| c.is_uppercase());
        let any_rest = letters.peek().is_some();
        match (first.is_uppercase(), rest_upper, any_rest) {
            (true, true, true) => "upper",
            (true, _, false) => "upper",
            (true, false, _) => "cap",
            (false, _, _) => "lower",
        }
    }

    /// The last `words` words, as one span of text, and where it starts.
    ///
    /// The trailing whitespace of the newest word is not in the span. It sits
    /// after everything a rewrite could touch, so leaving it out means a rewrite
    /// cannot lose it.
    pub fn span(&self, words: u8) -> (usize, String) {
        let start = self.cells.len().saturating_sub(words as usize);
        let mut out = String::new();
        for (n, cell) in self.cells[start..].iter().enumerate() {
            out.push_str(&cell.word);
            if start + n + 1 < self.cells.len() {
                out.push_str(&cell.space);
            }
        }
        (start, out)
    }
}
