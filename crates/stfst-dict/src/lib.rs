//! The runtime side of the host contract: a stack of compiled dictionaries, and
//! translation over a stroke history.
//!
//! Items 1 to 4 of `FST-DICT-DESIGN.md` §5. Nothing here links HFST, and nothing
//! decodes an artifact on open, so this is what an embedded host imports.

mod dict;
mod session;

pub use dict::{Dict, Input, Manifest, Meta, Outlines, Requires, HOST_VERSION};
pub use session::{Held, Match, Session};

use std::path::PathBuf;

use stfst_core::{Capabilities, RecordError, System};

#[derive(Debug, thiserror::Error)]
pub enum DictError {
    #[error("reading {path}: {source}")]
    Io {
        path: String,
        source: std::io::Error,
    },
    #[error("{path}: {source}")]
    Manifest {
        path: String,
        source: toml::de::Error,
    },
    #[error("{0}")]
    Automaton(#[from] stfst_ol::OlError),
    #[error("{name} wants host {wants}, and this host is {HOST_VERSION}")]
    HostTooOld { name: String, wants: u32 },
    #[error("{name} declares both `words` and `next`, and a tape has one window")]
    TwoWindows { name: String },
    #[error("{name} reads context slot {slot}, which is not in the contract's table")]
    UnknownSlot { name: String, slot: String },
    #[error("{name} asks for the {kind} {op}, which this host does not implement")]
    Unimplemented {
        name: String,
        kind: &'static str,
        op: String,
    },
    #[error("{name} needs key {key}, which the system does not have")]
    MissingKey { name: String, key: String },
    #[error("{name} can emit {tag}, which is not a tag in the record language")]
    UnknownTag { name: String, tag: String },
    #[error("{name} is for system {wants}, and this host loaded {have}")]
    WrongSystem {
        name: String,
        wants: String,
        have: String,
    },
    #[error("{name} was compiled without reverse.hfstol, so it cannot be read backwards")]
    NoReverse { name: String },
    #[error("{name} produced a record that does not parse: {source}")]
    Record { name: String, source: RecordError },
}

/// The dictionaries a host has loaded, in priority order.
pub struct Stack {
    dicts: Vec<Dict>,
    /// Kept because folding needs it: which keys may be stripped, and what a bare
    /// key means, are both stack-wide questions.
    system: System,
}

impl Stack {
    /// Higher priority shadows lower, which is what `plover.cfg` file order
    /// means today. Equal priorities keep the order given.
    pub fn open(
        dirs: &[PathBuf],
        system: &System,
        host: &Capabilities,
    ) -> Result<Stack, DictError> {
        let mut dicts = Vec::with_capacity(dirs.len());
        for dir in dirs {
            let dict = Dict::open(dir, system, host)?;
            if dict.manifest.dict.system != system.name {
                return Err(DictError::WrongSystem {
                    name: dict.name().to_string(),
                    wants: dict.manifest.dict.system.clone(),
                    have: system.name.clone(),
                });
            }
            dicts.push(dict);
        }
        dicts.sort_by_key(|d| -d.priority());
        Ok(Stack {
            dicts,
            system: system.clone(),
        })
    }

    pub fn dicts(&self) -> &[Dict] {
        &self.dicts
    }

    pub fn dict(&self, index: usize) -> &Dict {
        &self.dicts[index]
    }

    pub fn session(&self) -> Session<'_> {
        Session::new(self)
    }

    pub(crate) fn system_suffix_keys(&self) -> &[usize] {
        self.system.suffix_keys()
    }

    /// What one key means on its own, looked up across the whole stack.
    ///
    /// This is what makes folding cross-stack: `-S` is defined in whichever
    /// dictionary defines it, and the base word is in whichever dictionary has the
    /// base word, and neither knows about the other.
    pub(crate) fn bare(
        &self,
        key: usize,
        context: &stfst_core::Context,
        enabled: &[bool],
    ) -> Result<Option<stfst_core::Emission>, DictError> {
        let stroke = stfst_core::Stroke::EMPTY.with(key);
        for (at, dict) in self.dicts.iter().enumerate() {
            if !enabled[at] {
                continue;
            }
            let prefix = stfst_core::Prefix {
                state: None,
                context: context.select(dict.context()),
            };
            let mut cursor = dict.cursor(&prefix);
            dict.feed(&mut cursor, stroke, false);
            let outputs = cursor.outputs();
            if !outputs.is_empty() {
                return Ok(dict.interpret(&outputs)?.into_iter().next());
            }
        }
        Ok(None)
    }
}
