//! One compiled dictionary, and what it promises the host.

use std::path::Path;

use serde::Deserialize;
use stfst_core::record::{all_tags, is_tag};
use stfst_core::{Record, Stroke, System};
use stfst_ol::{Automaton, Cursor, Sym};

use crate::DictError;

/// The host contract version this crate implements: items 1 to 4 of
/// `FST-DICT-DESIGN.md` §5. Context tapes and translation-unit state are 5 and
/// 6, so a dictionary that declares them is refused rather than mistranslated.
pub const HOST_VERSION: u32 = 1;

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
    pub context: Vec<String>,
    pub state: bool,
}

#[derive(Debug, Deserialize)]
pub struct Input {
    pub alphabet: String,
    pub order: String,
    pub separator: char,
}

pub struct Dict {
    pub manifest: Manifest,
    fst: Automaton,
    encoding: Encoding,
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
    pub fn open(dir: &Path, system: &System) -> Result<Dict, DictError> {
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
        if !manifest.requires.context.is_empty() || manifest.requires.state {
            return Err(DictError::Unavailable { name });
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
        Ok(Dict {
            manifest,
            fst,
            encoding,
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

    pub(crate) fn cursor(&self) -> Cursor<'_> {
        self.fst.cursor()
    }

    pub fn name(&self) -> &str {
        &self.manifest.dict.name
    }

    pub fn priority(&self) -> i64 {
        self.manifest.dict.priority
    }

    pub(crate) fn interpret(&self, outputs: &[Vec<Sym>]) -> Result<Vec<Record>, DictError> {
        let alphabet = self.fst.alphabet();
        let mut records = Vec::with_capacity(outputs.len());
        for symbols in outputs {
            let text: Vec<&str> = symbols.iter().map(|&s| alphabet.symbol(s)).collect();
            records.push(Record::parse_symbols(&text).map_err(|source| DictError::Record {
                name: self.name().to_string(),
                source,
            })?);
        }
        Ok(records)
    }
}
