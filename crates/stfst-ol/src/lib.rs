//! Reader for HFST's `hfstol` runtime format.
//!
//! The file is three flat tables: a symbol table, an index table and a
//! transition table. Nothing is decoded on open, so a mapped artifact costs no
//! resident memory beyond the pages a lookup touches. HFST is GPLv3 and this
//! crate does not link it; the layout is read off HFST's own `transducer.h`,
//! and `scripts/crosscheck-ol.sh` holds the reading to `hfst-optimized-lookup`.

mod alphabet;
mod chain;
mod cursor;
mod input;
mod search;

pub use alphabet::Alphabet;
pub use cursor::Cursor;
use cursor::Start;
pub use search::Cheapest;

use std::ops::Deref;
use std::path::Path;
use std::sync::OnceLock;

pub type Sym = u16;

pub(crate) const EPSILON: Sym = 0;
const NO_SYMBOL: Sym = Sym::MAX;
const TRANSITION_TABLE_START: u32 = 1 << 31;
const INDEX_ENTRY: usize = 6;
const TRANSITION_ENTRY: usize = 8;

#[derive(Debug, thiserror::Error)]
pub enum OlError {
    #[error("{0}")]
    Io(#[from] std::io::Error),
    #[error("not an hfstol automaton: {0}")]
    Malformed(&'static str),
    #[error("hfstol automaton uses a feature this reader does not have: {0}")]
    Unsupported(&'static str),
}

/// A position in the automaton. Below `TRANSITION_TABLE_START` it is an index
/// table slot, above it a transition table slot; HFST packs both in one number
/// so a target needs no tag.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub(crate) struct Loc(u32);

impl Loc {
    pub(crate) const START: Loc = Loc(0);
}

enum Site {
    Index(u32),
    Trans(u32),
}

enum Data {
    Mapped(memmap2::Mmap),
    Owned(Vec<u8>),
}

impl Deref for Data {
    type Target = [u8];
    fn deref(&self) -> &[u8] {
        match self {
            Data::Mapped(m) => m,
            Data::Owned(v) => v,
        }
    }
}

pub struct Automaton {
    data: Data,
    alphabet: Alphabet,
    index_at: usize,
    index_len: u32,
    trans_at: usize,
    trans_len: u32,
    /// The frontier before any symbol. Every cursor starts here, and for an
    /// automaton that guesses early the closure is most of what one costs.
    start: OnceLock<Start>,
}

impl Automaton {
    /// # Safety
    ///
    /// Mapping is safe as long as nothing rewrites the file while it is open.
    /// The compiler writes an artifact once and never in place, so this holds
    /// for a build directory; a host serving mutable files should copy first.
    pub fn open(path: &Path) -> Result<Self, OlError> {
        let file = std::fs::File::open(path)?;
        let map = unsafe { memmap2::Mmap::map(&file)? };
        Self::from_data(Data::Mapped(map))
    }

    pub fn from_bytes(bytes: Vec<u8>) -> Result<Self, OlError> {
        Self::from_data(Data::Owned(bytes))
    }

    fn from_data(data: Data) -> Result<Self, OlError> {
        let mut p = 0;
        if data.starts_with(b"HFST") {
            // "HFST" NUL, then a length, then that many bytes of properties,
            // then one more NUL which the length does not count.
            let n = u16_at(&data, 5)? as usize;
            p = 7 + n;
            if *data.get(p).ok_or(OlError::Malformed("truncated header"))? != 0 {
                return Err(OlError::Malformed("unterminated property block"));
            }
            p += 1;
        }
        let input_count = u16_at(&data, p)? as usize;
        let symbol_count = u16_at(&data, p + 2)? as usize;
        let index_len = u32_at(&data, p + 4)?;
        let trans_len = u32_at(&data, p + 8)?;
        // Two counts we do not need, then nine flags stored a word each.
        let flags_at = p + 20;
        if u32_at(&data, flags_at)? != 0 {
            return Err(OlError::Unsupported("weights"));
        }
        // An epsilon cycle that emits output has no finite set of outputs, so
        // the closure in `Cursor` would not terminate. HFST records whether one
        // is there, which is cheaper than finding out during a lookup.
        if u32_at(&data, flags_at + 7 * 4)? != 0 {
            return Err(OlError::Unsupported("input epsilon cycles"));
        }
        p = flags_at + 9 * 4;

        if input_count > symbol_count {
            return Err(OlError::Malformed("more input symbols than symbols"));
        }
        let mut symbols = Vec::with_capacity(symbol_count);
        for _ in 0..symbol_count {
            let end = data
                .get(p..)
                .ok_or(OlError::Malformed("truncated symbol table"))?
                .iter()
                .position(|&b| b == 0)
                .ok_or(OlError::Malformed("truncated symbol table"))?;
            symbols.push(
                std::str::from_utf8(&data[p..p + end])
                    .map_err(|_| OlError::Malformed("symbol is not utf-8"))?
                    .to_string(),
            );
            p += end + 1;
        }

        let index_at = p;
        let trans_at = index_at + index_len as usize * INDEX_ENTRY;
        let end = trans_at + trans_len as usize * TRANSITION_ENTRY;
        // The tables are written in native byte order, so this is also the
        // check that catches an artifact built on a big-endian host.
        if end != data.len() {
            return Err(OlError::Malformed("table sizes do not match file size"));
        }
        Ok(Automaton {
            alphabet: Alphabet::new(symbols, input_count)?,
            data,
            index_at,
            index_len,
            trans_at,
            trans_len,
            start: OnceLock::new(),
        })
    }

    pub fn alphabet(&self) -> &Alphabet {
        &self.alphabet
    }

    pub fn cursor(&self) -> Cursor<'_> {
        Cursor::start(self, self.start.get_or_init(|| Cursor::closed(self)))
    }

    /// Every record the automaton returns for a tape. `None` means the tape
    /// used a symbol the automaton does not have, which no entry could match.
    pub fn lookup(&self, tape: &str) -> Option<Vec<String>> {
        let syms = self.alphabet.tokenize(tape)?;
        Some(
            self.lookup_symbols(&syms)
                .iter()
                .map(|r| self.alphabet.render(r))
                .collect(),
        )
    }

    pub fn lookup_symbols(&self, input: &[Sym]) -> Vec<Vec<Sym>> {
        let mut cursor = self.cursor();
        for &s in input {
            cursor.step(s);
        }
        cursor.outputs()
    }

    fn site(&self, loc: Loc) -> Site {
        if loc.0 < TRANSITION_TABLE_START {
            Site::Index(loc.0)
        } else {
            Site::Trans(loc.0 - TRANSITION_TABLE_START)
        }
    }

    /// A state's own slot says whether it accepts: input `NO_SYMBOL` with
    /// target 1.
    pub(crate) fn is_final(&self, loc: Loc) -> bool {
        match self.site(loc) {
            Site::Index(p) => {
                p < self.index_len && self.index_input(p) == NO_SYMBOL && self.index_target(p) == 1
            }
            Site::Trans(q) => {
                q < self.trans_len && self.trans_input(q) == NO_SYMBOL && self.trans_target(q) == 1
            }
        }
    }

    /// Every transition out of `loc` reading `want`. Epsilon is not a special
    /// case: `want = EPSILON` finds the epsilon transitions by the same rule,
    /// because HFST reserves symbol 0 the slot it would occupy anyway.
    pub(crate) fn arcs(&self, loc: Loc, want: Sym, mut f: impl FnMut(Sym, Loc)) {
        let group = match self.site(loc) {
            // An index state keeps its accepting slot at `p` and the entry for
            // symbol `s` at `p + 1 + s`, which is what makes lookup a jump
            // rather than a scan.
            Site::Index(p) => {
                let slot = p as u64 + 1 + want as u64;
                if slot >= self.index_len as u64 {
                    return;
                }
                let slot = slot as u32;
                if self.index_input(slot) != want {
                    return;
                }
                match self.index_target(slot).checked_sub(TRANSITION_TABLE_START) {
                    Some(g) => g,
                    None => return,
                }
            }
            // A transition state has one accepting slot too, at `q`, and its
            // transitions follow it. An index target points straight at a
            // symbol's group; a transition target points at a state.
            Site::Trans(q) => q + 1,
        };
        let mut j = group;
        while j < self.trans_len && self.trans_input(j) == want {
            f(self.trans_output(j), Loc(self.trans_target(j)));
            j += 1;
        }
    }

    /// Every transition out of `loc`, as input, output and target. `arcs` asks
    /// what one symbol does, which is what a lookup asks; a walk of the language
    /// asks what the state does.
    pub(crate) fn out_arcs(&self, loc: Loc, mut f: impl FnMut(Sym, Sym, Loc)) {
        match self.site(loc) {
            Site::Index(_) => {
                for want in 0..self.alphabet.input_count() as Sym {
                    self.arcs(loc, want, |out, target| f(want, out, target));
                }
            }
            Site::Trans(q) => {
                let mut j = q + 1;
                while j < self.trans_len && self.trans_input(j) != NO_SYMBOL {
                    f(self.trans_input(j), self.trans_output(j), Loc(self.trans_target(j)));
                    j += 1;
                }
            }
        }
    }

    fn index_input(&self, i: u32) -> Sym {
        read_u16(&self.data, self.index_at + i as usize * INDEX_ENTRY)
    }

    fn index_target(&self, i: u32) -> u32 {
        read_u32(&self.data, self.index_at + i as usize * INDEX_ENTRY + 2)
    }

    fn trans_input(&self, i: u32) -> Sym {
        read_u16(&self.data, self.trans_at + i as usize * TRANSITION_ENTRY)
    }

    fn trans_output(&self, i: u32) -> Sym {
        read_u16(&self.data, self.trans_at + i as usize * TRANSITION_ENTRY + 2)
    }

    fn trans_target(&self, i: u32) -> u32 {
        read_u32(&self.data, self.trans_at + i as usize * TRANSITION_ENTRY + 4)
    }
}

fn read_u16(data: &[u8], at: usize) -> Sym {
    Sym::from_le_bytes([data[at], data[at + 1]])
}

fn read_u32(data: &[u8], at: usize) -> u32 {
    u32::from_le_bytes([data[at], data[at + 1], data[at + 2], data[at + 3]])
}

fn u16_at(data: &[u8], at: usize) -> Result<Sym, OlError> {
    if at + 2 > data.len() {
        return Err(OlError::Malformed("truncated header"));
    }
    Ok(read_u16(data, at))
}

fn u32_at(data: &[u8], at: usize) -> Result<u32, OlError> {
    if at + 4 > data.len() {
        return Err(OlError::Malformed("truncated header"));
    }
    Ok(read_u32(data, at))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_truncated_file_is_an_error() {
        assert!(matches!(
            Automaton::from_bytes(b"HFST\0\x02\0".to_vec()),
            Err(OlError::Malformed(_))
        ));
    }

    #[test]
    fn a_short_file_is_an_error() {
        assert!(matches!(
            Automaton::from_bytes(Vec::new()),
            Err(OlError::Malformed(_))
        ));
    }
}
