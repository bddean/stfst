//! Reading the automaton the other way round.
//!
//! A forward lookup folds a frontier over a tape, and that works because many
//! paths agree on one record: the frontier converges. The reverse relation does
//! not converge. One word is written by many outlines, so a frontier would hold
//! every distinct outline prefix at once and grow with the length of the word.
//!
//! So a reverse lookup is a cheapest-first search instead. It asks a different
//! question, and the right one: not "every outline" but "the few shortest",
//! which is what a suggestion list shows.

use std::cmp::Ordering;
use std::collections::{BinaryHeap, HashSet};

use crate::chain::{Chain, ROOT};
use crate::{Automaton, Loc, Sym, EPSILON};

/// The cheapest outputs for a whole input, cheapest first.
pub struct Cheapest {
    pub outputs: Vec<Vec<Sym>>,
    /// A bound ended the search, so more outputs may exist. Every output
    /// returned is still no dearer than any output left behind.
    pub truncated: bool,
}

struct Node {
    cost: u64,
    pos: u32,
    loc: Loc,
    out: u32,
}

impl Ord for Node {
    /// `BinaryHeap` pops the greatest, so cheapest must compare greatest. A tie
    /// goes to the node further through the input, and then to the younger
    /// output, which makes a run reproducible.
    fn cmp(&self, other: &Self) -> Ordering {
        other
            .cost
            .cmp(&self.cost)
            .then(self.pos.cmp(&other.pos))
            .then(self.out.cmp(&other.out))
    }
}

impl PartialOrd for Node {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Eq for Node {}

impl PartialEq for Node {
    fn eq(&self, other: &Self) -> bool {
        self.cmp(other) == Ordering::Equal
    }
}

impl Automaton {
    /// The `limit` cheapest outputs for the whole of `input`, cheapest first.
    ///
    /// `weight` is the cost of emitting one output symbol, indexed by symbol; a
    /// symbol past its end costs 1. `budget` bounds the states the search may
    /// expand, because the reverse of a generative dictionary relates one word
    /// to an unbounded set of outlines.
    pub fn cheapest(
        &self,
        input: &[Sym],
        weight: &[u32],
        limit: usize,
        budget: usize,
    ) -> Cheapest {
        let mut chain = Chain::default();
        let mut heap = BinaryHeap::new();
        let mut seen: HashSet<(u32, Loc, u32)> = HashSet::new();
        let mut outputs: Vec<Vec<Sym>> = Vec::new();

        heap.push(Node {
            cost: 0,
            pos: 0,
            loc: Loc::START,
            out: ROOT,
        });

        for _ in 0..budget {
            let Some(node) = heap.pop() else {
                return Cheapest {
                    outputs,
                    truncated: false,
                };
            };
            if node.pos as usize == input.len() && self.is_final(node.loc) {
                let out = chain.read(node.out);
                if !outputs.contains(&out) {
                    outputs.push(out);
                    if outputs.len() >= limit {
                        return Cheapest {
                            outputs,
                            truncated: true,
                        };
                    }
                }
            }

            let mut push = |sym: Sym, loc: Loc, pos: u32| {
                let (out, cost) = match sym {
                    EPSILON => (node.out, node.cost),
                    _ => (
                        chain.push(sym, node.out),
                        node.cost + weight.get(sym as usize).copied().unwrap_or(1) as u64,
                    ),
                };
                if seen.insert((pos, loc, out)) {
                    heap.push(Node {
                        cost,
                        pos,
                        loc,
                        out,
                    });
                }
            };
            // An input epsilon consumes nothing, so it keeps `pos`.
            self.arcs(node.loc, EPSILON, |sym, loc| push(sym, loc, node.pos));
            if let Some(&want) = input.get(node.pos as usize) {
                self.arcs(node.loc, want, |sym, loc| push(sym, loc, node.pos + 1));
            }
        }
        Cheapest {
            outputs,
            truncated: true,
        }
    }
}
