//! Walking the automaton one input symbol at a time.
//!
//! A steno host does not have the whole outline when it starts looking: it has
//! one stroke, then another. So the traversal is a frontier of live paths that
//! a symbol advances, and a plain lookup is that frontier folded over a tape.
//! There is no separate whole-string search.
//!
//! The frontier is wide. A record puts its flags first and the keys that decide
//! them last, so the automaton emits a flag prefix on a guess and carries every
//! guess forward. That is tens of live paths per step and hundreds at the
//! widest, most of which die. `docs/latency.md` measures it.

use std::collections::HashSet;
use std::hash::{BuildHasherDefault, Hasher};

use crate::{Automaton, Loc, Sym, EPSILON};

const ROOT: u32 = u32::MAX;

/// One live path: where it is, and the output it has emitted so far.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
struct Path {
    loc: Loc,
    out: u32,
}

#[derive(Clone)]
pub struct Cursor<'a> {
    fst: &'a Automaton,
    /// Emitted output, as parent-linked chains so paths share their prefixes.
    nodes: Vec<(Sym, u32)>,
    frontier: Vec<Path>,
    /// The paths this step has already seen. Held across steps, so a step
    /// allocates nothing once the buffers have grown.
    seen: HashSet<Path, BuildHasherDefault<FastHash>>,
    taken: Vec<(u32, Sym, Loc)>,
}

impl<'a> Cursor<'a> {
    pub(crate) fn new(fst: &'a Automaton) -> Self {
        let start = Path {
            loc: Loc::START,
            out: ROOT,
        };
        let mut cursor = Cursor {
            fst,
            nodes: Vec::new(),
            frontier: vec![start],
            seen: HashSet::default(),
            taken: Vec::new(),
        };
        cursor.seen.insert(start);
        cursor.close();
        cursor
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

    /// Every record reachable by what has been read so far.
    pub fn outputs(&self) -> Vec<Vec<Sym>> {
        let mut records: Vec<Vec<Sym>> = Vec::new();
        for path in &self.frontier {
            if !self.fst.is_final(path.loc) {
                continue;
            }
            let record = self.read(path.out);
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
                self.nodes.push((sym, parent));
                (self.nodes.len() - 1) as u32
            };
            let path = Path { loc, out };
            if self.seen.insert(path) {
                self.frontier.push(path);
            }
        }
    }

    fn read(&self, mut id: u32) -> Vec<Sym> {
        let mut out = Vec::new();
        while id != ROOT {
            let (sym, parent) = self.nodes[id as usize];
            out.push(sym);
            id = parent;
        }
        out.reverse();
        out
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
