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
//! A match begins where a unit the host wrote begins, as Plover's translator
//! combines whole translations: a stroke inside a unit is not a start, or `-G`
//! after `STEUP/HRAEUT` would read `HRAEUT/-G` over a stroke already spent.
//!
//! What the host says before the keys rides in a frame per stroke, for the same
//! reason: a candidate that opened at stroke `p` must read the context and the
//! state as they were at `p`, and replay has to reproduce them. So undo is a pop
//! and redo is a replay, which is what host contract items 5 and 6 ask for.

use stfst_core::{Alternative, Around, Context, Emission, Prefix, Record, Shown, State, Stroke, Window};
use stfst_ol::Cursor;

use crate::{Dict, DictError, Stack};

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
    /// What a fold writes around this match, which is then its base. Host
    /// contract item 8, and `stfst_core::fold`.
    pub fold: Option<Around>,
}

/// A candidate in a dictionary that reads the text after its keys.
///
/// Nothing can be harvested when the keys arrive, so the host keeps this and
/// asks again once a unit has written something. The cursor comes with it rather
/// than the strokes: it has read the keys already, and the frame's own stroke is
/// history the moment the next one lands.
#[derive(Clone)]
pub struct Held<'a> {
    dict: usize,
    strokes: std::ops::Range<usize>,
    words: u8,
    cursor: Cursor<'a>,
}

impl Held<'_> {
    /// The most words this candidate can read. A host that has fewer shows
    /// fewer, and then a rule that wanted more does not match.
    pub fn words(&self) -> u8 {
        self.words
    }

    /// Where its keys are in the stroke history. A held stroke is not the stroke
    /// being read, and it can be several strokes back: it waits through every
    /// unit that writes no word.
    pub fn strokes(&self) -> std::ops::Range<usize> {
        self.strokes.clone()
    }
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
    /// Where each unit the host has written begins, oldest first.
    starts: Vec<usize>,
    /// Per stroke, the starts a unit ending there took over, for undo to put
    /// back.
    replaced: Vec<Vec<usize>>,
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
            starts: Vec::new(),
            replaced: Vec::new(),
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

    /// Say that the host wrote the last `strokes` strokes as one unit, so no later
    /// match begins inside it. A stroke the host wrote nothing for is a unit of
    /// its own.
    pub fn wrote(&mut self, strokes: usize) {
        let Some(newest) = self.history.len().checked_sub(1) else {
            return;
        };
        let start = (newest + 1).saturating_sub(strokes);
        let from = self.starts.partition_point(|&s| s < start);
        self.replaced[newest] = self.starts.split_off(from);
        self.starts.push(start);
    }

    /// Whether a match may begin at `start`.
    fn begins_unit(&self, start: usize) -> bool {
        self.starts.binary_search(&start).is_ok()
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
            compiled(self.stack, live.dict).feed(&mut live.cursor, stroke, true);
        }
        self.live.retain(|l| l.cursor.live() > 0);
        self.history.push(stroke);
        self.starts.push(self.history.len() - 1);
        self.replaced.push(Vec::new());
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
        self.starts.pop();
        let mut replaced = self.replaced.pop().unwrap_or_default();
        // The last one taken over is this stroke's own start.
        replaced.pop();
        self.starts.extend(replaced);
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
            if best.as_ref().is_some_and(|(b, _, _)| live.start >= b.start) || !self.begins_unit(live.start) {
                continue;
            }
            // A stroke that turns a dictionary off stops it answering at once,
            // including a run it had already started. That is what `stack.solo` is
            // for: a mode with no interference from a multi-stroke entry below.
            if !self.enabled(now, live.dict) {
                continue;
            }
            let dict = compiled(self.stack, live.dict);
            let (outputs, window) = match dict.shown() {
                None => (dict.outputs(&live.cursor), None),
                // The text a frame reads is not written yet, so nothing can be
                // harvested here. `held` offers the candidate to the host instead.
                Some(shown) if shown.deferred() => continue,
                Some(shown) => {
                    let window = dict.window(&span(shown.words()));
                    (dict.harvest(&live.cursor, &window), Some(window))
                }
            };
            if !outputs.is_empty() {
                best = Some((live, outputs, window));
            }
        }
        let beat = best.as_ref().map(|(live, _, _)| (live.start, live.dict));
        // An editable dictionary is a map, so it has no candidate to advance and
        // no cursor to ask: what it might have matched is however many strokes
        // back its longest entry reaches, tried longest first.
        if let Some((dict, at, record)) = self.editable_match(beat) {
            return Ok(Some(Match {
                dict,
                strokes: self.history.len() - at,
                emissions: vec![Emission { record, state: None }],
                window: None,
                fold: None,
            }));
        }
        let Some((live, outputs, window)) = best else {
            return Ok(None);
        };
        Ok(Some(Match {
            dict: live.dict,
            strokes: self.history.len() - live.start,
            emissions: compiled(self.stack, live.dict).interpret(&outputs)?,
            window,
            fold: None,
        }))
    }

    /// The candidate the host should hold, for a stroke nothing else matched.
    ///
    /// A fallback, as a fold is: a dictionary that reads what follows its keys
    /// answers only where nothing answers already, because whether it answers at
    /// all is unknown until that text exists.
    pub fn held(&self) -> Option<Held<'a>> {
        let now = self.history.len().saturating_sub(1);
        let mut best: Option<(&Live<'a>, Shown)> = None;
        for live in &self.live {
            let Some(shown) = self.stack.dict(live.dict).shown() else {
                continue;
            };
            if !shown.deferred() || !self.enabled(now, live.dict) || !self.begins_unit(live.start) {
                continue;
            }
            if best.is_some_and(|(b, _)| live.start >= b.start) {
                continue;
            }
            best = Some((live, shown));
        }
        let (live, shown) = best?;
        Some(Held {
            dict: live.dict,
            strokes: live.start..self.history.len(),
            words: shown.words(),
            cursor: live.cursor.clone(),
        })
    }

    /// What a held candidate says, now that the text it reads is written.
    ///
    /// `None` where the keys began an entry and finished none, which is the
    /// answer a held stroke cannot give any earlier.
    pub fn resolve(&self, held: &Held<'_>, span: &str) -> Result<Option<Match>, DictError> {
        let dict = compiled(self.stack, held.dict);
        let window = dict.window(span);
        let outputs = dict.harvest(&held.cursor, &window);
        if outputs.is_empty() {
            return Ok(None);
        }
        Ok(Some(Match {
            dict: held.dict,
            strokes: held.strokes.len(),
            emissions: dict.interpret(&outputs)?,
            window: Some(window),
            fold: None,
        }))
    }

    /// The best match a fold finds for the last stroke, with what it writes
    /// around its base.
    ///
    /// A fallback, as it is in Plover: a host asks only where nothing matched the
    /// stroke as written, or `PWABGS` would fold when it has an entry of its own.
    /// A held candidate comes first, because it *is* a match on the stroke as
    /// written and only its answer has to wait.
    ///
    /// Longest first, and among equals the first alternative, which is Plover's
    /// order: `SEFRB/R-GTS` folds `-T` onto reservation before it tries `R-GS`
    /// alone. The base is looked up and not folded again, as Plover does.
    ///
    /// Built here rather than as the strokes arrive. A fold can only matter on a
    /// stroke that matched nothing, which is the rare case, and building it eagerly
    /// cost a cursor per key per candidate on *every* stroke: 63 microseconds a
    /// stroke against 13. The replay is the same one undo uses.
    pub fn folded_with(
        &self,
        span: impl Fn(u8) -> String,
    ) -> Result<Option<Match>, DictError> {
        let Some(newest) = self.history.len().checked_sub(1) else {
            return Ok(None);
        };
        let stroke = self.history[newest];
        let alternatives = self.alternatives(newest, stroke)?;
        if alternatives.is_empty() {
            return Ok(None);
        }
        // A start live before the last stroke, whether or not it survived the
        // stroke as written: `SEFRB/R-GSZ` folds to `SEFRB/R-GS`, and its cursor
        // died on `R-GSZ`.
        let oldest = newest.checked_sub(1).map_or(newest, |p| self.oldest[p]);
        for start in (oldest..=newest).filter(|&s| self.begins_unit(s)) {
            for alt in &alternatives {
                // What goes before the base is a unit of its own, so the base has
                // to begin one.
                if start < newest && !alt.around.before.is_empty() {
                    continue;
                }
                let Some(base) = alt.base(stroke) else {
                    continue;
                };
                if let Some((dict, emissions, window)) = self.replay(start, base, &span)? {
                    return Ok(Some(Match {
                        dict,
                        strokes: self.history.len() - start,
                        emissions,
                        window,
                        fold: Some(alt.around.clone()),
                    }));
                }
            }
        }
        Ok(None)
    }

    /// Every fold alternative the stack holds for one stroke, highest dictionary
    /// first.
    fn alternatives(&self, at: usize, stroke: Stroke) -> Result<Vec<Alternative>, DictError> {
        let mut out = Vec::new();
        for (dict, slot) in self.stack.dicts().iter().enumerate() {
            let Some(entry) = slot.compiled() else {
                continue;
            };
            if !self.enabled(at, dict) || entry.shown().is_some() {
                continue;
            }
            let mut cursor = entry.cursor(&self.prefix(at, dict));
            entry.feed(&mut cursor, stroke, false);
            for fold in entry.folds(&cursor, self.stack.system())? {
                out.extend(fold.0);
            }
        }
        Ok(out)
    }

    /// What the stack writes for the history from `start`, with `base` in place of
    /// the last stroke: the dictionary, its emissions and the window it read.
    #[allow(clippy::type_complexity)]
    fn replay(
        &self,
        start: usize,
        base: Stroke,
        span: &impl Fn(u8) -> String,
    ) -> Result<Option<(usize, Vec<Emission>, Option<Window>)>, DictError> {
        let newest = self.history.len() - 1;
        for (dict, slot) in self.stack.dicts().iter().enumerate() {
            if !self.enabled(newest, dict) {
                continue;
            }
            let entry = match slot {
                crate::Slot::Editable(editable) => {
                    let mut strokes = self.history[start..newest].to_vec();
                    strokes.push(base);
                    if let Some(record) = editable.lookup(&strokes) {
                        return Ok(Some((dict, vec![Emission { record, state: None }], None)));
                    }
                    continue;
                }
                crate::Slot::Compiled(dict) => dict,
            };
            let mut cursor = entry.cursor(&self.prefix(start, dict));
            for (n, &stroke) in self.history[start..newest].iter().enumerate() {
                if cursor.live() == 0 {
                    break;
                }
                entry.feed(&mut cursor, stroke, n > 0);
            }
            if cursor.live() == 0 {
                continue;
            }
            entry.feed(&mut cursor, base, start < newest);
            let (outputs, window) = match entry.shown() {
                None => (entry.outputs(&cursor), None),
                // A fold of a held stroke would be a fallback to a fallback, and
                // the keys a frame reads are its own.
                Some(shown) if shown.deferred() => continue,
                Some(shown) => {
                    let window = entry.window(&span(shown.words()));
                    (entry.harvest(&cursor, &window), Some(window))
                }
            };
            if !outputs.is_empty() {
                return Ok(Some((dict, entry.interpret(&outputs)?, window)));
            }
        }
        Ok(None)
    }

    /// Open a candidate per dictionary at `start` and read the history from
    /// there. Keeps the ones still live.
    fn open_at(&mut self, start: usize) {
        for (dict, slot) in self.stack.dicts().iter().enumerate() {
            let Some(entry) = slot.compiled() else {
                continue;
            };
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

    /// The best an editable dictionary can do, given where the compiled stack
    /// matched. `None` there means nothing matched at all.
    ///
    /// Longest wins, and among equals the higher priority, which is the rule the
    /// cursors already follow; the stack is in priority order, so that is the
    /// first slot that reaches furthest back.
    fn editable_match(&self, beat: Option<(usize, usize)>) -> Option<(usize, usize, Record)> {
        let now = self.history.len();
        let mut best: Option<(usize, usize)> = beat;
        let mut found: Option<(usize, usize, Record)> = None;
        for (dict, slot) in self.stack.dicts().iter().enumerate() {
            let Some(editable) = slot.editable() else {
                continue;
            };
            if !self.enabled(now - 1, dict) {
                continue;
            }
            let most = editable.longest().min(now);
            for length in (1..=most).rev() {
                let start = now - length;
                // Shorter matches from here on, so once one cannot win none can.
                if !wins(start, dict, best) {
                    break;
                }
                if !self.begins_unit(start) {
                    continue;
                }
                if let Some(record) = editable.lookup(&self.history[start..]) {
                    best = Some((start, dict));
                    found = Some((dict, start, record));
                    break;
                }
            }
        }
        found
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

/// Whether a match beats the one already found: longer first, and among equals
/// the dictionary higher in the stack.
fn wins(start: usize, dict: usize, best: Option<(usize, usize)>) -> bool {
    match best {
        None => true,
        Some((at, from)) => start < at || (start == at && dict < from),
    }
}

/// The dictionary behind a live candidate. Candidates open only in compiled
/// dictionaries, so this is the invariant `open_at` keeps rather than a case.
fn compiled(stack: &Stack, at: usize) -> &Dict {
    stack.dict(at).compiled().expect("a candidate in a compiled dictionary")
}
