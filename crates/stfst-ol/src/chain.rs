//! Output emitted so far, held as parent-linked chains so that paths which
//! agree on a prefix store it once.

use crate::Sym;

pub(crate) const ROOT: u32 = u32::MAX;

#[derive(Default, Clone)]
pub(crate) struct Chain {
    nodes: Vec<(Sym, u32)>,
}

impl Chain {
    /// Extend `parent` by one symbol, and name the result.
    pub(crate) fn push(&mut self, sym: Sym, parent: u32) -> u32 {
        self.nodes.push((sym, parent));
        (self.nodes.len() - 1) as u32
    }

    pub(crate) fn read(&self, mut id: u32) -> Vec<Sym> {
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
