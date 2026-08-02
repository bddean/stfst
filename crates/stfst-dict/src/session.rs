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
//!
//! What the host says before the keys rides in a frame per stroke, for the same
//! reason: a candidate that opened at stroke `p` must read the context and the
//! state as they were at `p`, and replay has to reproduce them. So undo is a pop
//! and redo is a replay, which is what host contract items 5 and 6 ask for.

use stfst_core::{Context, Emission, Prefix, State, Stroke, Window};
use stfst_ol::Cursor;

use crate::{DictError, Stack};

/// A match ending at the most recent stroke.
#[derive(Debug)]
pub struct Match {
    /// Index into the stack, so a host can name the dictionary that answered.
    pub dict: usize,
    /// How many strokes it consumed, counting back from the current one.
    pub strokes: usize,
    /// Every tape the entry holds. More than one means the dictionary is
    /// ambiguous for this outline, which is the host's problem to present.
    pub emissions: Vec<Emission>,
    /// The window this match was read against, when the dictionary asked for one.
    /// The host needs it to substitute the characters back.
    pub window: Option<Window>,
    /// What a folded key means on its own, to be written after this match.
    ///
    /// Set when the match was found by stripping a suffix key from the last stroke:
    /// `PWABGS` is `PWABG` plus whatever `-S` means, and the base dictionary knows
    /// nothing about the suffix. Host contract item 8.
    pub suffix: Option<Emission>,
}

/// One live candidate: a dictionary, where it started, and how far it has read.
struct Live<'a> {
    dict: usize,
    start: usize,
    cursor: Cursor<'a>,
}

/// What the host says before the keys, as of one point in the stroke history:
/// the context it computes from its own output, and the state each dictionary
/// carries.
#[derive(Clone, Default)]
struct Frame {
    context: Context,
    /// Per dictionary, in stack order. `None` is a dictionary at its start
    /// state, which is the absent prefix and not an empty one.
    state: Vec<Option<State>>,
    /// Which dictionaries are answering. A stroke can turn one off, so this is
    /// per frame like everything else here, and undo puts it back: Plover's
    /// dictionary commands have no undo at all.
    enabled: Vec<bool>,
    /// What `stack.solo` saved, for `stack.end_solo` to restore.
    solo: Option<Vec<bool>>,
}

pub struct Session<'a> {
    stack: &'a Stack,
    history: Vec<Stroke>,
    /// The oldest start still live after each stroke. Enough to rebuild.
    oldest: Vec<usize>,
    live: Vec<Live<'a>>,
    /// `frames[i]` is what a candidate opening at stroke `i` reads. The last one
    /// is current, so there is always one more frame than stroke.
    frames: Vec<Frame>,
}

impl<'a> Session<'a> {
    pub fn new(stack: &'a Stack) -> Self {
        Session {
            stack,
            history: Vec::new(),
            oldest: Vec::new(),
            live: Vec::new(),
            frames: vec![Frame {
                context: Context::default(),
                state: vec![None; stack.dicts().len()],
                enabled: vec![true; stack.dicts().len()],
                solo: None,
            }],
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

    /// The context the next stroke will be read under. A host updates this as
    /// its output changes; only the slots a dictionary declared reach its tape.
    pub fn context_mut(&mut self) -> &mut Context {
        &mut self.frames.last_mut().expect("a current frame").context
    }

    /// Store the state a dictionary emitted, so its next stroke reads it. The
    /// host calls this for the emission it accepted, which is why the runtime
    /// does not: a dictionary can offer several, and choosing is the host's.
    pub fn accept(&mut self, dict: usize, state: Option<State>) {
        self.frames.last_mut().expect("a current frame").state[dict] = state;
    }

    /// Turn dictionaries on and off, which is what `plover-dict-commands` does.
    ///
    /// The argument is a comma-separated list of names, each with `+` to enable,
    /// `-` to disable or `!` to toggle. A name this stack does not have is ignored,
    /// because a user's dictionary set is not a promise about anyone else's.
    ///
    /// Returns false when the command is not one of these, so a host can pass
    /// everything through and let this decide.
    pub fn stack_command(&mut self, name: &str, arg: &str) -> bool {
        let count = self.stack.dicts().len();
        let names: Vec<String> = self.stack.dicts().iter().map(|d| d.name().to_string()).collect();
        let frame = self.frames.last_mut().expect("a current frame");
        match name {
            "stack.toggle" => {}
            "stack.solo" => {
                // Solo remembers what it interrupted, and starts from nothing on:
                // the point of it is a mode with no interference from below.
                frame.solo = Some(frame.enabled.clone());
                frame.enabled = vec![false; count];
            }
            "stack.end_solo" => {
                if let Some(saved) = frame.solo.take() {
                    frame.enabled = saved;
                }
                return true;
            }
            _ => return false,
        }
        for item in arg.split(',').map(str::trim).filter(|s| !s.is_empty()) {
            let (op, wanted) = item.split_at(1);
            let Some(at) = names.iter().position(|n| n == wanted) else {
                continue;
            };
            frame.enabled[at] = match op {
                "+" => true,
                "-" => false,
                "!" => !frame.enabled[at],
                _ => continue,
            };
        }
        true
    }

    /// Whether a dictionary is answering, as of the frame a candidate reads.
    fn enabled(&self, at: usize, dict: usize) -> bool {
        self.frames[at].enabled[dict]
    }

    /// The state a dictionary carries right now, for a host that shows it.
    pub fn state(&self, dict: usize) -> Option<&State> {
        self.frames.last()?.state[dict].as_ref()
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
        // The stroke has been read under the frame in force; what the host does
        // about it belongs to the next one.
        let next = self.frames.last().expect("a current frame").clone();
        self.frames.push(next);
    }

    /// Take back the last stroke, and with it whatever the host recorded after
    /// it: the frame stack makes that a pop.
    pub fn undo(&mut self) -> Option<Stroke> {
        let stroke = self.history.pop()?;
        self.oldest.pop();
        self.frames.pop();
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
        self.best_with(|_| String::new())
    }

    /// The same, for a host that can show a dictionary the text it has written.
    ///
    /// `span` is asked for the last `n` words, and only for a dictionary that
    /// declared a window. A host with no text to show returns an empty span, and
    /// then a rewriting dictionary simply matches nothing.
    pub fn best_with(
        &self,
        span: impl Fn(u8) -> String,
    ) -> Result<Option<Match>, DictError> {
        let span = &span;
        let mut best: Option<(&Live, Vec<Vec<stfst_ol::Sym>>, Option<Window>)> = None;
        let now = self.history.len().saturating_sub(1);
        for live in &self.live {
            if best.as_ref().is_some_and(|(b, _, _)| live.start >= b.start) {
                continue;
            }
            // A stroke that turns a dictionary off stops it answering at once,
            // including a run it had already started. That is what `stack.solo` is
            // for: a mode with no interference from a multi-stroke entry below.
            if !self.enabled(now, live.dict) {
                continue;
            }
            let dict = self.stack.dict(live.dict);
            let (outputs, window) = match dict.words() {
                0 => (live.cursor.outputs(), None),
                n => {
                    let window = dict.window(&span(n));
                    (dict.harvest(&live.cursor, &window), Some(window))
                }
            };
            if !outputs.is_empty() {
                best = Some((live, outputs, window));
            }
        }
        // A fold is a fallback, as it is in Plover: only when nothing matches the
        // stroke as it was written. Otherwise `PWABGS` would fold when it has an
        // entry of its own.
        let Some((live, outputs, window)) = best else {
            return self.folded_best(&span);
        };
        Ok(Some(Match {
            dict: live.dict,
            strokes: self.history.len() - live.start,
            emissions: self.stack.dict(live.dict).interpret(&outputs)?,
            window,
            suffix: None,
        }))
    }

    /// The best match that needed a key folded off the last stroke, with what that
    /// key means on its own.
    ///
    /// Host contract item 8, and the answer to where a suffix rule lives when the
    /// base word is in a dictionary that has never heard of the suffix: not in a
    /// dictionary at all.
    ///
    /// Built here rather than as the strokes arrive. A fold can only matter on a
    /// stroke that matched nothing, which is the rare case, and building it eagerly
    /// cost a cursor per key per candidate on *every* stroke: 63 microseconds a
    /// stroke against 13. The replay is the same one undo uses.
    fn folded_best(
        &self,
        span: &impl Fn(u8) -> String,
    ) -> Result<Option<Match>, DictError> {
        let Some(newest) = self.history.len().checked_sub(1) else {
            return Ok(None);
        };
        let mut best: Option<(usize, usize, Vec<Vec<stfst_ol::Sym>>, Option<Window>)> = None;
        for &key in self.stack.system_suffix_keys() {
            if !self.history[newest].contains(key) {
                continue;
            }
            let bare = self.history[newest].without(key);
            if bare.is_empty() {
                continue;
            }
            let oldest = self.oldest.get(newest).copied().unwrap_or(newest);
            for start in oldest..=newest {
                for (dict, entry) in self.stack.dicts().iter().enumerate() {
                    if !self.enabled(newest, dict) || best.as_ref().is_some_and(|b| start >= b.1) {
                        continue;
                    }
                    let mut cursor = entry.cursor(&self.prefix(start, dict));
                    // The history up to the last stroke as written, then the last
                    // stroke with the key taken off it.
                    for (n, &stroke) in self.history[start..newest].iter().enumerate() {
                        if cursor.live() == 0 {
                            break;
                        }
                        entry.feed(&mut cursor, stroke, n > 0);
                    }
                    if cursor.live() == 0 {
                        continue;
                    }
                    entry.feed(&mut cursor, bare, start < newest);
                    let (outputs, window) = match entry.words() {
                        0 => (cursor.outputs(), None),
                        n => {
                            let window = entry.window(&span(n));
                            (entry.harvest(&cursor, &window), Some(window))
                        }
                    };
                    if !outputs.is_empty() {
                        best = Some((dict, start, outputs, window));
                    }
                }
            }
            if let Some((dict, start, outputs, window)) = best {
                // What the bare key means is a lookup like any other, in the whole
                // stack, so the suffix and the base need not be in the same
                // dictionary or know about each other.
                let frame = &self.frames[newest];
                let Some(suffix) = self.stack.bare(key, &frame.context, &frame.enabled)? else {
                    return Ok(None);
                };
                return Ok(Some(Match {
                    dict,
                    strokes: self.history.len() - start,
                    emissions: self.stack.dict(dict).interpret(&outputs)?,
                    window,
                    suffix: Some(suffix),
                }));
            }
        }
        Ok(None)
    }

    /// Open a candidate per dictionary at `start` and read the history from
    /// there. Keeps the ones still live.
    fn open_at(&mut self, start: usize) {
        for (dict, entry) in self.stack.dicts().iter().enumerate() {
            if !self.enabled(start, dict) {
                continue;
            }
            let mut cursor = entry.cursor(&self.prefix(start, dict));
            for (n, &stroke) in self.history[start..].iter().enumerate() {
                if cursor.live() == 0 {
                    break;
                }
                entry.feed(&mut cursor, stroke, n > 0);
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

    /// What dictionary `dict` reads before the keys of a candidate starting at
    /// `start`.
    fn prefix(&self, start: usize, dict: usize) -> Prefix {
        let entry = self.stack.dict(dict);
        if entry.context().is_empty() && !entry.stateful() {
            return Prefix::default();
        }
        let frame = &self.frames[start];
        Prefix {
            state: match entry.stateful() {
                true => frame.state[dict].clone(),
                false => None,
            },
            context: frame.context.select(entry.context()),
        }
    }
}
