//! Walking the automaton one input symbol at a time.
//!
//! A steno host does not have the whole outline when it starts looking: it has
//! one stroke, then another. So the traversal is a frontier of live paths that
//! a symbol advances, and a plain lookup is that frontier folded over a tape.
//! There is no separate whole-string search.
//!
//! The frontier can be wide, so this is a search and not a walk. An automaton
//! that emits output the input has not yet decided has to guess, and every guess
//! is a live path until a symbol kills it. An imported dictionary runs at 34
//! paths a step for that reason, measured in `docs/latency.md`.

use std::collections::HashSet;
use std::hash::{BuildHasherDefault, Hasher};

use crate::chain::{Chain, ROOT};
use crate::{Automaton, Loc, Sym, EPSILON};

/// One live path: where it is, and the output it has emitted so far.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
struct Path {
    loc: Loc,
    out: u32,
}

/// A closed start frontier, held by the automaton and cloned into each cursor.
pub struct Start {
    frontier: Vec<Path>,
    out: Chain,
}

#[derive(Clone)]
pub struct Cursor<'a> {
    fst: &'a Automaton,
    out: Chain,
    frontier: Vec<Path>,
    /// The paths this step has already seen. Held across steps, so a step
    /// allocates nothing once the buffers have grown.
    seen: HashSet<Path, BuildHasherDefault<FastHash>>,
    taken: Vec<(u32, Sym, Loc)>,
}

impl<'a> Cursor<'a> {
    /// The frontier before any symbol, closed. `Automaton` reads this once.
    pub(crate) fn closed(fst: &'a Automaton) -> Start {
        let start = Path {
            loc: Loc::START,
            out: ROOT,
        };
        let mut cursor = Cursor {
            fst,
            out: Chain::default(),
            frontier: vec![start],
            seen: HashSet::default(),
            taken: Vec::new(),
        };
        cursor.seen.insert(start);
        cursor.close();
        Start {
            frontier: cursor.frontier,
            out: cursor.out,
        }
    }

    pub(crate) fn start(fst: &'a Automaton, start: &Start) -> Self {
        // `seen` is not carried: a step clears it before it reads it, so the
        // closure's entries are dead the moment this returns.
        Cursor {
            fst,
            out: start.out.clone(),
            frontier: start.frontier.clone(),
            seen: HashSet::default(),
            taken: Vec::new(),
        }
    }

    /// Read one input symbol.
    pub fn step(&mut self, sym: Sym) {
        self.collect(0..self.frontier.len(), sym);
        self.frontier.clear();
        self.seen.clear();
        self.extend();
        self.close();
    }

    /// How many paths are live. Zero means no stroke can extend what has been
    /// read into an entry, and a lookup costs roughly this.
    pub fn live(&self) -> usize {
        self.frontier.len()
    }

    /// End every path. For a caller that knows the next symbol is not in this
    /// automaton's alphabet, which no step could represent.
    pub fn kill(&mut self) {
        self.frontier.clear();
    }

    /// The input symbols some live path can read next, in symbol order.
    pub fn next_inputs(&self) -> Vec<Sym> {
        let mut out = Vec::new();
        for path in &self.frontier {
            self.fst.out_arcs(path.loc, |sym, _, _| {
                if sym != EPSILON {
                    out.push(sym);
                }
            });
        }
        out.sort_unstable();
        out.dedup();
        out
    }

    /// Whether what has been read so far is an entry, which is `outputs` without
    /// reading any of them.
    pub fn accepts(&self) -> bool {
        self.frontier.iter().any(|p| self.fst.is_final(p.loc))
    }

    /// Every record reachable by what has been read so far.
    pub fn outputs(&self) -> Vec<Vec<Sym>> {
        let mut records: Vec<Vec<Sym>> = Vec::new();
        for path in &self.frontier {
            if !self.fst.is_final(path.loc) {
                continue;
            }
            let record = self.out.read(path.out);
            if !records.contains(&record) {
                records.push(record);
            }
        }
        records
    }

    /// Follow every epsilon transition, so the frontier only ever holds states
    /// that a real symbol can leave. The frontier doubles as the worklist: it
    /// grows, nothing leaves it, and `i` walks it.
    fn close(&mut self) {
        let mut i = 0;
        while i < self.frontier.len() {
            self.collect(i..i + 1, EPSILON);
            i += 1;
            self.extend();
        }
    }

    /// Gather every `want` arc out of these frontier paths.
    fn collect(&mut self, paths: std::ops::Range<usize>, want: Sym) {
        let fst = self.fst;
        let taken = &mut self.taken;
        taken.clear();
        for path in &self.frontier[paths] {
            let from = path.out;
            fst.arcs(path.loc, want, |out, loc| taken.push((from, out, loc)));
        }
    }

    /// Turn collected arcs into frontier paths, dropping any path already seen.
    /// Dropping is also what ends the closure: a repeated path has nothing new
    /// below it.
    fn extend(&mut self) {
        for i in 0..self.taken.len() {
            let (parent, sym, loc) = self.taken[i];
            let out = if sym == EPSILON {
                parent
            } else {
                self.out.push(sym, parent)
            };
            let path = Path { loc, out };
            if self.seen.insert(path) {
                self.frontier.push(path);
            }
        }
    }
}

/// Multiply-rotate over a path's eight bytes. The default hasher is SipHash,
/// which costs a quarter of a lookup at this frontier width and buys nothing
/// here: the keys are our own table offsets, not anything a caller chooses.
#[derive(Default, Clone)]
struct FastHash(u64);

impl FastHash {
    fn mix(&mut self, n: u64) {
        self.0 = (self.0.rotate_left(5) ^ n).wrapping_mul(0x517c_c1b7_2722_0a95);
    }
}

impl Hasher for FastHash {
    fn write(&mut self, bytes: &[u8]) {
        for &b in bytes {
            self.mix(b as u64);
        }
    }

    fn write_u32(&mut self, n: u32) {
        self.mix(n as u64);
    }

    fn finish(&self) -> u64 {
        self.0
    }
}
