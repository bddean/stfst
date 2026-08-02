//! The runtime side of the host contract: a stack of compiled dictionaries, and
//! translation over a stroke history.
//!
//! Items 1 to 4 of `FST-DICT-DESIGN.md` §5. Nothing here links HFST, and nothing
//! decodes an artifact on open, so this is what an embedded host imports.

mod dict;
mod session;

pub use dict::{Dict, Input, Manifest, Meta, Outlines, Requires, HOST_VERSION};
pub use session::{Match, Session};

use std::path::PathBuf;

use stfst_core::{RecordError, System};

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
    #[error("{name} reads context slot {slot}, which is not in the contract's table")]
    UnknownSlot { name: String, slot: String },
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
}

impl Stack {
    /// Higher priority shadows lower, which is what `plover.cfg` file order
    /// means today. Equal priorities keep the order given.
    pub fn open(dirs: &[PathBuf], system: &System) -> Result<Stack, DictError> {
        let mut dicts = Vec::with_capacity(dirs.len());
        for dir in dirs {
            let dict = Dict::open(dir, system)?;
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
        Ok(Stack { dicts })
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
}
