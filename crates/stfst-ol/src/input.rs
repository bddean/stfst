//! Questions about the input language, rather than about one tape.
//!
//! A host that walks a cursor never asks these: it discovers the window of
//! candidates as the strokes arrive, which is why no dictionary here declares a
//! longest key. A host whose dictionary API takes a whole outline has to know
//! the longest before the first stroke, so the compiler answers this once and
//! the manifest carries it.

use std::collections::HashSet;

use crate::{Automaton, Loc, Sym};

/// Layers past this are not an outline anyone strokes, so the answer is reported
/// as unbounded rather than searched for further.
const LAYER_CAP: u32 = 64;

/// One layer: the states reachable without reading the symbol, whether any of
/// them accepts, and where a single reading of it leads.
struct Layer {
    states: Vec<u32>,
    accepts: bool,
    next: Vec<Loc>,
}

impl Automaton {
    /// The greatest number of times `sym` can appear on one accepted input tape.
    /// `None` when there is no greatest.
    ///
    /// With the separator as the symbol, this is the longest outline in strokes,
    /// less one.
    pub fn max_occurrences(&self, sym: Sym) -> Option<u32> {
        let mut seeds = vec![Loc::START];
        let mut history: Vec<Layer> = Vec::new();
        let mut best = None;
        for count in 0..LAYER_CAP {
            let mut layer = self.layer(&seeds, sym);
            if layer.states.is_empty() {
                return best;
            }
            if layer.accepts {
                best = Some(count);
            }
            // A layer that repeats an earlier one repeats it forever, so the
            // accepting layers of that period are the whole answer: one of them
            // and there is no greatest count, none of them and no later layer
            // accepts either.
            if let Some(at) = history.iter().position(|h| h.states == layer.states) {
                return match history[at..].iter().any(|h| h.accepts) {
                    true => None,
                    false => best,
                };
            }
            seeds = std::mem::take(&mut layer.next);
            history.push(layer);
        }
        None
    }

    /// Everything a lookup can reach from `seeds` without reading `sym`.
    ///
    /// The steps are the ones `arcs` takes, over the symbols an input tape can
    /// hold, so this explores exactly what a lookup explores and no more.
    fn layer(&self, seeds: &[Loc], sym: Sym) -> Layer {
        let mut seen: HashSet<u32> = seeds.iter().map(|l| l.0).collect();
        let mut work: Vec<Loc> = seeds.to_vec();
        let mut states: Vec<u32> = seeds.iter().map(|l| l.0).collect();
        let mut accepts = false;
        let mut next = Vec::new();
        let mut past: HashSet<u32> = HashSet::new();
        while let Some(loc) = work.pop() {
            accepts |= self.is_final(loc);
            for want in 0..self.alphabet().input_count() as Sym {
                let crossing = want == sym;
                self.arcs(loc, want, |_, target| {
                    if crossing {
                        if past.insert(target.0) {
                            next.push(target);
                        }
                    } else if seen.insert(target.0) {
                        states.push(target.0);
                        work.push(target);
                    }
                });
            }
        }
        states.sort_unstable();
        Layer {
            states,
            accepts,
            next,
        }
    }
}
