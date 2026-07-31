//! Translation: the longest match ending at the current stroke.
//!
//! Plover needs a `LONGEST_KEY` per dictionary, because a hash table cannot say
//! whether a stroke run is the start of anything, so it tries every length. An
//! automaton can say. A cursor that has read strokes `p..=t` is either live,
//! meaning some entry begins that way, or dead, meaning no match starting at `p`
//! can ever exist. So the window of starts worth trying is discovered, it limits
//! itself, and no dictionary declares a longest key.
//!
//! Dead is permanent, which is also what makes undo cheap: a start that died at
//! `t - 1` cannot be live at `t`, so the oldest live start never moves back, and
//! replaying from the one recorded per stroke rebuilds the state exactly.

use stfst_core::{Record, Stroke};
use stfst_ol::Cursor;

use crate::{DictError, Stack};

/// A match ending at the most recent stroke.
#[derive(Debug)]
pub struct Match {
    /// Index into the stack, so a host can name the dictionary that answered.
    pub dict: usize,
    /// How many strokes it consumed, counting back from the current one.
    pub strokes: usize,
    /// Every record the entry holds. More than one means the dictionary is
    /// ambiguous for this outline, which is the host's problem to present.
    pub records: Vec<Record>,
}

/// One live candidate: a dictionary, where it started, and how far it has read.
struct Live<'a> {
    dict: usize,
    start: usize,
    cursor: Cursor<'a>,
}

pub struct Session<'a> {
    stack: &'a Stack,
    history: Vec<Stroke>,
    /// The oldest start still live after each stroke. Enough to rebuild.
    oldest: Vec<usize>,
    live: Vec<Live<'a>>,
}

impl<'a> Session<'a> {
    pub fn new(stack: &'a Stack) -> Self {
        Session {
            stack,
            history: Vec::new(),
            oldest: Vec::new(),
            live: Vec::new(),
        }
    }

    pub fn history(&self) -> &[Stroke] {
        &self.history
    }

    /// How many candidates are open. This is the number that replaces a
    /// declared longest key, and a host can watch it to see the cost.
    pub fn candidates(&self) -> usize {
        self.live.len()
    }

    /// Read one stroke. Every live candidate advances or dies, and a new
    /// candidate opens at this stroke in every dictionary.
    pub fn push(&mut self, stroke: Stroke) {
        for live in &mut self.live {
            self.stack.dict(live.dict).feed(&mut live.cursor, stroke, true);
        }
        self.live.retain(|l| l.cursor.live() > 0);
        self.history.push(stroke);
        self.open_at(self.history.len() - 1);
        let newest = self.history.len() - 1;
        let oldest = self.live.iter().map(|l| l.start).min().unwrap_or(newest);
        self.oldest.push(oldest);
    }

    /// Take back the last stroke.
    pub fn undo(&mut self) -> Option<Stroke> {
        let stroke = self.history.pop()?;
        self.oldest.pop();
        self.live.clear();
        let end = self.history.len();
        if end > 0 {
            for start in self.oldest[end - 1]..end {
                self.open_at(start);
            }
        }
        Some(stroke)
    }

    /// The match a host should use: the longest, and among equals the one from
    /// the highest priority dictionary. The stack is in priority order and
    /// candidates open in stack order, so that is the first of the longest.
    pub fn best(&self) -> Result<Option<Match>, DictError> {
        let mut best: Option<&Live> = None;
        for live in &self.live {
            let longer = best.is_none_or(|b| live.start < b.start);
            if longer && !live.cursor.outputs().is_empty() {
                best = Some(live);
            }
        }
        let Some(live) = best else { return Ok(None) };
        Ok(Some(Match {
            dict: live.dict,
            strokes: self.history.len() - live.start,
            records: self.stack.dict(live.dict).interpret(&live.cursor.outputs())?,
        }))
    }

    /// Open a candidate per dictionary at `start` and read the history from
    /// there. Keeps the ones still live.
    fn open_at(&mut self, start: usize) {
        for (dict, entry) in self.stack.dicts().iter().enumerate() {
            let mut cursor = entry.cursor();
            for (n, &stroke) in self.history[start..].iter().enumerate() {
                entry.feed(&mut cursor, stroke, n > 0);
                if cursor.live() == 0 {
                    break;
                }
            }
            if cursor.live() > 0 {
                self.live.push(Live {
                    dict,
                    start,
                    cursor,
                });
            }
        }
    }
}
