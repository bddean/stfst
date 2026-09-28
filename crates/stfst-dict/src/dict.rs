//! One compiled dictionary, and what it promises the host.

use std::num::NonZeroU8;
use std::path::Path;

use serde::Deserialize;
use stfst_core::context;
use stfst_core::fold::{Fold, FOLD};
use stfst_core::record::{all_tags, is_tag};
use stfst_core::{Capabilities, Emission, Prefix, Record, Shown, Stroke, System, Window};
use stfst_ol::{Automaton, Cursor, Sym};

use crate::DictError;

/// The host contract version this crate implements: `FST-DICT-DESIGN.md` §5,
/// items 1 to 6, and a held stroke. Version 1 was items 1 to 4; a dictionary
/// that needs neither a context slot, nor a carried state, nor a bound word
/// still declares 1 and still loads in a host that implements only those.
pub const HOST_VERSION: u32 = 3;

#[derive(Debug, Deserialize)]
pub struct Manifest {
    pub dict: Meta,
    pub requires: Requires,
    pub input: Input,
}

#[derive(Debug, Deserialize)]
pub struct Meta {
    pub name: String,
    pub version: String,
    pub system: String,
    pub priority: i64,
}

#[derive(Debug, Deserialize)]
pub struct Requires {
    pub keys: Vec<String>,
    pub host: u32,
    /// Context slots this dictionary reads, in table order. The host puts them on
    /// the input tape before the keys; a dictionary declaring none sees the tape
    /// it would have seen under host 1.
    pub context: Vec<String>,
    /// Whether the dictionary carries state from one translation unit to the
    /// next, which the host stores and replays without reading.
    pub state: bool,
    /// How many recent words the host must bind, for `[COPYn]` to splice.
    #[serde(default)]
    pub registers: u8,
    /// How many words of already-written text the host must show, after the keys.
    #[serde(default)]
    pub words: u8,
    /// How many words of the next unit's text the host must show instead. The
    /// text is not written when the keys arrive, so the host holds the stroke
    /// until it is.
    #[serde(default)]
    pub next: u8,
    /// Whether some record's tail waits for the unit after it: `[AFTER]`. The
    /// same hold as `next` without the reading, and a host that has one has both.
    #[serde(default)]
    pub after: bool,
    /// The characters this dictionary named, so the host sends them as themselves
    /// and everything else as its class. Read out of the grammar by the compiler,
    /// so a host and a dictionary cannot disagree about it.
    #[serde(default)]
    pub significant: String,
    /// Operations this dictionary can ask a host for. A host that cannot do one
    /// refuses the dictionary at load, which is the point of naming them.
    #[serde(default)]
    pub commands: Vec<String>,
    #[serde(default)]
    pub metas: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct Input {
    pub alphabet: String,
    pub order: String,
    pub separator: char,
    /// The longest outline, in strokes, where a host is told it.
    #[serde(default)]
    pub strokes: Option<u32>,
}

/// What a reverse lookup found: key tapes, shortest first, and whether a bound
/// ended the search. A caller that shows a suggestion list wants to know the
/// difference between "no outline writes this" and "the search gave up".
pub struct Outlines {
    pub tapes: Vec<String>,
    pub truncated: bool,
}

pub struct Dict {
    pub manifest: Manifest,
    fst: Automaton,
    back: Option<Back>,
    encoding: Encoding,
    /// The named characters, as a slice for `Window::encode`.
    significant: Vec<char>,
    shown: Option<Shown>,
}

/// The same relation with the tapes swapped. Optimized lookup only walks the
/// input side, so the other direction is a second automaton and not a second way
/// of reading this one.
///
/// One key weighs one and the separator weighs more than a stroke can hold, so a
/// cheaper tape is one with fewer strokes, and then with fewer keys.
struct Back {
    fst: Automaton,
    weight: Vec<u32>,
}

/// How this dictionary's alphabet numbers the host's keys. Built once at open,
/// so feeding a stroke is an array lookup rather than a string lookup, and a key
/// the dictionary never uses is `None` rather than a failed hash.
struct Encoding {
    keys: Vec<Option<Sym>>,
    separator: Option<Sym>,
}

impl Dict {
    /// Open an artifact directory and check everything that can be checked
    /// before a stroke arrives. §5 asks for that explicitly: an unknown tag is a
    /// load-time error and not a runtime surprise. It is also cheap, because
    /// every tag a dictionary can emit is a symbol in its alphabet.
    pub fn open(dir: &Path, system: &System, host: &Capabilities) -> Result<Dict, DictError> {
        let path = dir.join("manifest.toml");
        let text = std::fs::read_to_string(&path).map_err(|source| DictError::Io {
            path: path.display().to_string(),
            source,
        })?;
        let manifest: Manifest = toml::from_str(&text).map_err(|source| DictError::Manifest {
            path: path.display().to_string(),
            source,
        })?;
        let name = manifest.dict.name.clone();

        if manifest.requires.host > HOST_VERSION {
            return Err(DictError::HostTooOld {
                name,
                wants: manifest.requires.host,
            });
        }
        for slot in &manifest.requires.context {
            if context::slot(slot).is_none() {
                return Err(DictError::UnknownSlot {
                    name: name.clone(),
                    slot: slot.clone(),
                });
            }
        }
        // A meta that does not run leaves the text quietly wrong, so a host that
        // cannot do one refuses the dictionary. A command that does not run is a
        // request declined, which is the ordinary case for `plover.quit` on a
        // phone, so the dictionary still loads.
        for op in &manifest.requires.metas {
            if !host.has_meta(op) {
                return Err(DictError::Unimplemented {
                    name: name.clone(),
                    kind: "meta",
                    op: op.clone(),
                });
            }
        }
        for key in &manifest.requires.keys {
            if !system.keys().iter().any(|k| &k.name == key) {
                return Err(DictError::MissingKey {
                    name,
                    key: key.clone(),
                });
            }
        }

        let fst = Automaton::open(&dir.join("forward.hfstol"))?;
        let alphabet = fst.alphabet();
        let known = all_tags();
        for symbol in alphabet.symbols() {
            // A context symbol and a named character are input side, and the
            // manifest already declares both, so neither is an unknown tag. What
            // this check is for is an output tag a host would not understand.
            if stfst_core::window::sig_char(symbol).is_some()
                || context::all_symbols().iter().any(|c| c == symbol)
            {
                continue;
            }
            if is_tag(symbol) && !known.iter().any(|t| t == symbol) {
                return Err(DictError::UnknownTag {
                    name,
                    tag: symbol.to_string(),
                });
            }
        }
        let encoding = Encoding {
            keys: system
                .keys()
                .iter()
                .map(|k| alphabet.number(&k.symbol.to_string()))
                .collect(),
            separator: alphabet.number(&manifest.input.separator.to_string()),
        };
        let back_path = dir.join("reverse.hfstol");
        let back = match back_path.exists() {
            true => {
                let fst = Automaton::open(&back_path)?;
                let alphabet = fst.alphabet();
                let mut weight = vec![1u32; alphabet.symbols().count()];
                if let Some(sep) = alphabet.number(&manifest.input.separator.to_string()) {
                    weight[sep as usize] = system.key_count() as u32 + 1;
                }
                Some(Back { fst, weight })
            }
            false => None,
        };
        let significant = manifest.requires.significant.chars().collect();
        let shown = match (
            NonZeroU8::new(manifest.requires.words),
            NonZeroU8::new(manifest.requires.next),
        ) {
            (Some(_), Some(_)) => return Err(DictError::TwoWindows { name }),
            (Some(n), None) => Some(Shown::Written(n)),
            (None, Some(n)) => Some(Shown::Next(n)),
            (None, None) => None,
        };
        Ok(Dict {
            manifest,
            fst,
            back,
            encoding,
            significant,
            shown,
        })
    }

    /// Read one stroke, with the separator first unless it opens the candidate.
    /// A symbol this dictionary does not have ends the candidate: no entry of
    /// its can contain the stroke.
    pub(crate) fn feed(&self, cursor: &mut Cursor, stroke: Stroke, joined: bool) {
        if joined {
            match self.encoding.separator {
                Some(sep) => cursor.step(sep),
                None => return cursor.kill(),
            }
        }
        for index in stroke.indices() {
            match self.encoding.keys[index] {
                Some(sym) => cursor.step(sym),
                None => return cursor.kill(),
            }
        }
    }

    /// A cursor that has read the host's prefix and is waiting for keys.
    ///
    /// The prefix holds only what this dictionary declared, so a dictionary that
    /// declared nothing starts where it always did, and no lookup pays for a
    /// contract item it does not use. A symbol the automaton does not have kills
    /// the cursor, which is what a host that offers a slot the dictionary never
    /// reads would produce.
    pub(crate) fn cursor(&self, prefix: &Prefix) -> Cursor<'_> {
        let mut cursor = self.fst.cursor();
        if prefix.is_empty() {
            return cursor;
        }
        let alphabet = self.fst.alphabet();
        for symbol in prefix.to_symbols() {
            match alphabet.number(&symbol) {
                Some(sym) => cursor.step(sym),
                None => {
                    cursor.kill();
                    break;
                }
            }
        }
        cursor
    }

    /// How many recent words the host must bind for `[COPYn]` to splice.
    pub fn registers(&self) -> u8 {
        self.manifest.requires.registers
    }

    /// The text this dictionary is shown, and how much of it.
    pub fn shown(&self) -> Option<Shown> {
        self.shown
    }

    /// Encode a span the way this dictionary expects to read it.
    pub fn window(&self, span: &str) -> Window {
        Window::encode(span, &self.significant)
    }

    /// What this candidate says, having also read the window.
    ///
    /// The live cursor holds keys only, so it is cloned to ask: a candidate that
    /// is never harvested costs nothing, and one that is costs a step per
    /// character of window. Nothing about the live frontier changes, which is why
    /// a window is affordable at all.
    pub(crate) fn harvest(&self, cursor: &Cursor<'_>, window: &Window) -> Vec<Vec<Sym>> {
        let alphabet = self.fst.alphabet();
        let mut cursor = cursor.clone();
        for symbol in window.to_symbols() {
            match alphabet.number(&symbol) {
                Some(sym) => cursor.step(sym),
                // A class this dictionary's rules never mention. The stroke does
                // not translate, which is the visible answer rather than a rule
                // matching half a word.
                None => return Vec::new(),
            }
        }
        cursor.outputs()
    }

    /// The context slots this dictionary reads.
    pub fn context(&self) -> &[String] {
        &self.manifest.requires.context
    }

    pub fn stateful(&self) -> bool {
        self.manifest.requires.state
    }

    pub fn name(&self) -> &str {
        &self.manifest.dict.name
    }

    pub fn priority(&self) -> i64 {
        self.manifest.dict.priority
    }

    /// The shortest outlines that write this record, as key tapes, shortest
    /// first. Item 4 of the host contract, and what a suggestions window asks
    /// for.
    ///
    /// Shortest, not every: a generative dictionary writes one word with
    /// unboundedly many outlines, so "every outline" is not a finite answer.
    /// Fewer strokes wins, and a tie goes to fewer keys.
    ///
    /// A record is the whole query, flags included, so `ing` and `ing[ATTACH_L]`
    /// are different questions. A symbol this dictionary does not have means no
    /// entry of its can be this record, so the answer is empty rather than an
    /// error.
    pub fn outlines(&self, record: &Record, limit: usize) -> Result<Outlines, DictError> {
        let back = self.back.as_ref().ok_or_else(|| DictError::NoReverse {
            name: self.name().to_string(),
        })?;
        let alphabet = back.fst.alphabet();
        let mut input = Vec::new();
        for symbol in record.to_symbols() {
            match alphabet.number(&symbol) {
                Some(sym) => input.push(sym),
                None => {
                    return Ok(Outlines {
                        tapes: Vec::new(),
                        truncated: false,
                    })
                }
            }
        }
        let found = back
            .fst
            .cheapest(&input, &back.weight, limit, limit * 4096);
        Ok(Outlines {
            tapes: found
                .outputs
                .iter()
                .map(|tape| alphabet.render(tape))
                .collect(),
            truncated: found.truncated,
        })
    }

    /// What a cursor says as the stroke was written: every record but a fold,
    /// which answers only where nothing did. See `folds`.
    pub(crate) fn outputs(&self, cursor: &Cursor<'_>) -> Vec<Vec<Sym>> {
        let mut outputs = cursor.outputs();
        outputs.retain(|o| !self.is_fold(o));
        outputs
    }

    /// The fold records a cursor holds, in the automaton's order.
    pub(crate) fn folds(&self, cursor: &Cursor<'_>, system: &System) -> Result<Vec<Fold>, DictError> {
        let alphabet = self.fst.alphabet();
        cursor
            .outputs()
            .iter()
            .filter(|o| self.is_fold(o))
            .map(|symbols| {
                let text: Vec<&str> = symbols.iter().map(|&s| alphabet.symbol(s)).collect();
                Fold::parse_symbols(&text, system).map_err(|source| DictError::Fold {
                    name: self.name().to_string(),
                    source,
                })
            })
            .collect()
    }

    fn is_fold(&self, output: &[Sym]) -> bool {
        let alphabet = self.fst.alphabet();
        output.first().is_some_and(|&s| alphabet.symbol(s) == FOLD)
    }

    pub(crate) fn interpret(&self, outputs: &[Vec<Sym>]) -> Result<Vec<Emission>, DictError> {
        let alphabet = self.fst.alphabet();
        let mut out = Vec::with_capacity(outputs.len());
        for symbols in outputs {
            let text: Vec<&str> = symbols.iter().map(|&s| alphabet.symbol(s)).collect();
            out.push(
                Emission::parse_symbols(&text).map_err(|source| DictError::Record {
                    name: self.name().to_string(),
                    source,
                })?,
            );
        }
        Ok(out)
    }
}
