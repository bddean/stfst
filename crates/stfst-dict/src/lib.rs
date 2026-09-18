//! The runtime side of the host contract: a stack of compiled dictionaries, and
//! translation over a stroke history.
//!
//! Items 1 to 4 of `FST-DICT-DESIGN.md` §5. Nothing here links HFST, and nothing
//! decodes an artifact on open, so this is what an embedded host imports.

mod dict;
mod editable;
mod session;

pub use dict::{Dict, Input, Manifest, Meta, Outlines, Requires, HOST_VERSION};
pub use editable::{listed, Editable};
pub use session::{Held, Match, Session};

use std::path::PathBuf;

use stfst_core::{Capabilities, OutlineError, RecordError, Shown, System};
use stfst_core::translate::TranslateError;

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
    #[error("{path}: {source}")]
    Json {
        path: String,
        source: serde_json::Error,
    },
    #[error("{outline}: {source}")]
    Outline {
        outline: String,
        source: OutlineError,
    },
    #[error("{translation}: {source}")]
    Translation {
        translation: String,
        source: TranslateError,
    },
    #[error("{0} is not a dictionary this host can write to")]
    NotEditable(String),
}

/// One dictionary of a stack. Compiled is the ordinary case: a relation over
/// tapes, built once and read. An editable one is the exception a keyboard needs,
/// and it has none of what makes a compiled dictionary powerful: no window, no
/// carried state, no context slot.
pub enum Slot {
    Compiled(Dict),
    Editable(Editable),
}

impl Slot {
    pub fn name(&self) -> &str {
        match self {
            Slot::Compiled(d) => d.name(),
            Slot::Editable(e) => e.name(),
        }
    }

    pub fn priority(&self) -> i64 {
        match self {
            Slot::Compiled(d) => d.priority(),
            Slot::Editable(e) => e.priority(),
        }
    }

    pub fn compiled(&self) -> Option<&Dict> {
        match self {
            Slot::Compiled(d) => Some(d),
            Slot::Editable(_) => None,
        }
    }

    pub fn editable(&self) -> Option<&Editable> {
        match self {
            Slot::Compiled(_) => None,
            Slot::Editable(e) => Some(e),
        }
    }

    pub fn shown(&self) -> Option<Shown> {
        self.compiled().and_then(Dict::shown)
    }

    pub fn registers(&self) -> u8 {
        self.compiled().map_or(0, Dict::registers)
    }

    pub fn context(&self) -> &[String] {
        self.compiled().map_or(&[], Dict::context)
    }

    pub fn stateful(&self) -> bool {
        self.compiled().is_some_and(Dict::stateful)
    }

    /// Outlines that write this record, cheapest first.
    pub fn outlines(&self, record: &stfst_core::Record, limit: usize) -> Result<Outlines, DictError> {
        match self {
            Slot::Compiled(d) => d.outlines(record, limit),
            Slot::Editable(e) => Ok(Outlines {
                tapes: e.outlines(record, limit),
                truncated: false,
            }),
        }
    }
}

/// The dictionaries a host has loaded, in priority order.
pub struct Stack {
    dicts: Vec<Slot>,
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
        Stack::open_with(dirs, &[], system, host)
    }

    /// The same, with editable dictionaries mixed in by priority: `(path, name,
    /// priority)` each, read as Plover JSON and written back there.
    pub fn open_with(
        dirs: &[PathBuf],
        editable: &[(PathBuf, String, i64)],
        system: &System,
        host: &Capabilities,
    ) -> Result<Stack, DictError> {
        let mut dicts = Vec::with_capacity(dirs.len() + editable.len());
        for dir in dirs {
            let dict = Dict::open(dir, system, host)?;
            if dict.manifest.dict.system != system.name {
                return Err(DictError::WrongSystem {
                    name: dict.name().to_string(),
                    wants: dict.manifest.dict.system.clone(),
                    have: system.name.clone(),
                });
            }
            dicts.push(Slot::Compiled(dict));
        }
        for (path, name, priority) in editable {
            dicts.push(Slot::Editable(Editable::open(path, name, *priority, system)?));
        }
        dicts.sort_by_key(|d| -d.priority());
        Ok(Stack {
            dicts,
            system: system.clone(),
        })
    }

    pub fn dicts(&self) -> &[Slot] {
        &self.dicts
    }

    pub fn dict(&self, index: usize) -> &Slot {
        &self.dicts[index]
    }

    /// The editable dictionary called `name`, for a host that has an entry to add.
    pub fn editable(&self, name: &str) -> Option<&Editable> {
        self.dicts
            .iter()
            .filter_map(Slot::editable)
            .find(|e| e.name() == name)
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
        for (at, slot) in self.dicts.iter().enumerate() {
            if !enabled[at] {
                continue;
            }
            let dict = match slot {
                Slot::Editable(editable) => {
                    if let Some(record) = editable.lookup(&[stroke]) {
                        return Ok(Some(stfst_core::Emission { record, state: None }));
                    }
                    continue;
                }
                Slot::Compiled(dict) => dict,
            };
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
