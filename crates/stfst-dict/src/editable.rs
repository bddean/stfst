//! A dictionary the host can write to while it is loaded.
//!
//! Every other dictionary in the stack is an automaton built by the compiler, and
//! a host that cannot run the compiler cannot add an entry to one. A keyboard has
//! to be able to: an outline that wrote the wrong thing is defined where it was
//! made, on the phone, and the phone has no HFST.
//!
//! So this one is a map, and its file is a Plover JSON dictionary: the same file
//! `user.json` already is, read here rather than imported by the compiler. What it
//! costs is what a map costs, which is why it is only ever the small one.

use std::collections::BTreeMap;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::RwLock;

use stfst_core::translate::translate;
use stfst_core::{Outline, Record, Stroke, System};

use crate::DictError;

pub struct Editable {
    name: String,
    priority: i64,
    path: PathBuf,
    system: System,
    entries: RwLock<Entries>,
}

/// What the file says, and the same thing keyed the way a stroke arrives.
///
/// The translations are the dictionary; the records and the outlines are read off
/// them, so a save writes back the file that was read and an edit changes one
/// place.
#[derive(Default)]
struct Entries {
    source: BTreeMap<String, String>,
    by_outline: HashMap<Outline, Record>,
    longest: usize,
}

impl Entries {
    fn index(&mut self, system: &System) {
        self.by_outline.clear();
        self.longest = 0;
        for (outline, translation) in &self.source {
            let (Ok(outline), Ok(record)) = (system.parse_outline(outline), translate(translation))
            else {
                // A line this host cannot read is left in the file rather than
                // dropped: another host may know what it means, and a save that
                // silently deleted it would be the worse failure.
                continue;
            };
            self.longest = self.longest.max(outline.0.len());
            self.by_outline.insert(outline, record);
        }
    }
}

/// One line of an editable directory's `editable.toml`.
#[derive(serde::Deserialize)]
struct Listed {
    name: String,
    file: String,
    priority: i64,
}

#[derive(serde::Deserialize)]
struct Listing {
    #[serde(default)]
    dict: Vec<Listed>,
}

/// The editable dictionaries a directory declares, as `Stack::open_with` takes
/// them.
///
/// A JSON dictionary says nothing about where it sits in a stack, and a host that
/// guessed would be the second place that decides priority. So the directory
/// carries a list, written by whatever put the files there, and a directory with
/// no list has no editable dictionaries rather than a default set.
pub fn listed(dir: &Path) -> Result<Vec<(PathBuf, String, i64)>, DictError> {
    let path = dir.join("editable.toml");
    let text = match std::fs::read_to_string(&path) {
        Ok(text) => text,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(source) => {
            return Err(DictError::Io {
                path: path.display().to_string(),
                source,
            })
        }
    };
    let listing: Listing = toml::from_str(&text).map_err(|source| DictError::Manifest {
        path: path.display().to_string(),
        source,
    })?;
    Ok(listing
        .dict
        .into_iter()
        .map(|d| (dir.join(d.file), d.name, d.priority))
        .collect())
}

impl Editable {
    /// Read a Plover JSON dictionary, or start an empty one where the file is not
    /// there yet: a dictionary nobody has written to is not an error.
    pub fn open(
        path: &Path,
        name: &str,
        priority: i64,
        system: &System,
    ) -> Result<Editable, DictError> {
        let source: BTreeMap<String, String> = match std::fs::read_to_string(path) {
            Ok(text) => serde_json::from_str(&text).map_err(|source| DictError::Json {
                path: path.display().to_string(),
                source,
            })?,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => BTreeMap::new(),
            Err(source) => {
                return Err(DictError::Io {
                    path: path.display().to_string(),
                    source,
                })
            }
        };
        let mut entries = Entries {
            source,
            ..Entries::default()
        };
        entries.index(system);
        Ok(Editable {
            name: name.to_string(),
            priority,
            path: path.to_path_buf(),
            system: system.clone(),
            entries: RwLock::new(entries),
        })
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn priority(&self) -> i64 {
        self.priority
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn len(&self) -> usize {
        self.read().source.len()
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// The most strokes an entry has, which is how far back a lookup has to try.
    pub fn longest(&self) -> usize {
        self.read().longest
    }

    pub fn lookup(&self, strokes: &[Stroke]) -> Option<Record> {
        let outline = Outline(strokes.to_vec());
        self.read().by_outline.get(&outline).cloned()
    }

    /// What this dictionary says the outline writes, as it is written in the file.
    pub fn translation(&self, outline: &str) -> Option<String> {
        self.read().source.get(outline).cloned()
    }

    /// Outlines that write this record, shortest first. A map has no cheapest
    /// path to walk, so this is the scan it has to be; the dictionary is small.
    pub fn outlines(&self, record: &Record, limit: usize) -> Vec<String> {
        let entries = self.read();
        let mut found: Vec<&Outline> = entries
            .by_outline
            .iter()
            .filter(|(_, r)| *r == record)
            .map(|(o, _)| o)
            .collect();
        found.sort_by_key(|o| (o.0.len(), o.0.clone()));
        found
            .into_iter()
            .take(limit)
            .map(|o| self.system.to_tape(o))
            .collect()
    }

    /// Define an outline, or redefine it. An empty translation deletes it, which
    /// is what an empty field means everywhere this is reached from.
    pub fn set(&self, outline: &str, translation: &str) -> Result<(), DictError> {
        let outline = self.normalize(outline)?;
        if translation.is_empty() {
            return self.remove(&outline);
        }
        translate(translation).map_err(|source| DictError::Translation {
            translation: translation.to_string(),
            source,
        })?;
        {
            let mut entries = self.write();
            entries.source.insert(outline, translation.to_string());
            entries.index(&self.system);
        }
        self.save()
    }

    pub fn remove(&self, outline: &str) -> Result<(), DictError> {
        let outline = self.normalize(outline)?;
        {
            let mut entries = self.write();
            if entries.source.remove(&outline).is_none() {
                return Ok(());
            }
            entries.index(&self.system);
        }
        self.save()
    }

    /// The outline as the file spells it, so `tkupt` and `TKUPT` are one entry.
    fn normalize(&self, outline: &str) -> Result<String, DictError> {
        let parsed =
            self.system
                .parse_outline(outline)
                .map_err(|source| DictError::Outline {
                    outline: outline.to_string(),
                    source,
                })?;
        Ok(self.system.format_outline(&parsed))
    }

    pub fn save(&self) -> Result<(), DictError> {
        let text = {
            let entries = self.read();
            serde_json::to_string_pretty(&entries.source).map_err(|source| DictError::Json {
                path: self.path.display().to_string(),
                source,
            })?
        };
        if let Some(dir) = self.path.parent() {
            std::fs::create_dir_all(dir).map_err(|source| DictError::Io {
                path: dir.display().to_string(),
                source,
            })?;
        }
        // Written beside the file and renamed over it: a keyboard is killed
        // whenever the phone feels like it, and a half-written dictionary is one
        // that does not load.
        let temp = self.path.with_extension("json.new");
        std::fs::write(&temp, text).map_err(|source| DictError::Io {
            path: temp.display().to_string(),
            source,
        })?;
        std::fs::rename(&temp, &self.path).map_err(|source| DictError::Io {
            path: self.path.display().to_string(),
            source,
        })
    }

    fn read(&self) -> std::sync::RwLockReadGuard<'_, Entries> {
        self.entries.read().unwrap_or_else(|e| e.into_inner())
    }

    fn write(&self) -> std::sync::RwLockWriteGuard<'_, Entries> {
        self.entries.write().unwrap_or_else(|e| e.into_inner())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const EXT: &str = include_str!("../../../systems/stenotype-extended.toml");

    fn sys() -> System {
        System::parse(EXT).expect("stock system file must be valid")
    }

    fn open(dir: &Path) -> Editable {
        Editable::open(&dir.join("user.json"), "user", 80, &sys()).unwrap()
    }

    fn temp(test: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("stfst-editable-{}-{test}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn a_missing_file_is_an_empty_dictionary() {
        let dir = temp("missing");
        let dict = open(&dir);
        assert!(dict.is_empty());
        assert_eq!(dict.longest(), 0);
    }

    #[test]
    fn an_entry_survives_being_written_and_read_back() {
        let dir = temp("round-trip");
        let system = sys();
        let dict = open(&dir);
        dict.set("TKUPT", "dupt").unwrap();
        dict.set("TAEUP/TAEUP", "{^}tape").unwrap();
        assert_eq!(dict.longest(), 2);

        let again = open(&dir);
        assert_eq!(again.translation("TKUPT").as_deref(), Some("dupt"));
        let outline = system.parse_outline("TKUPT").unwrap();
        assert_eq!(
            again.lookup(&outline.0),
            Some(stfst_core::translate::translate("dupt").unwrap())
        );
        assert_eq!(again.outlines(&translate("dupt").unwrap(), 4), vec!["TKUpt"]);
    }

    #[test]
    fn the_outline_is_stored_the_way_the_system_spells_it() {
        let dir = temp("spelling");
        let dict = open(&dir);
        dict.set("TKUP-T", "dupt").unwrap();
        assert_eq!(dict.translation("TKUPT").as_deref(), Some("dupt"));
    }

    #[test]
    fn an_empty_translation_deletes() {
        let dir = temp("delete");
        let dict = open(&dir);
        dict.set("TKUPT", "dupt").unwrap();
        dict.set("TKUPT", "").unwrap();
        assert!(dict.is_empty());
        assert!(open(&dir).is_empty());
    }

    #[test]
    fn a_translation_no_record_can_hold_is_refused_before_it_is_saved() {
        let dir = temp("refused");
        let dict = open(&dir);
        assert!(dict.set("TKUPT", "{oops").is_err());
        assert!(dict.is_empty());
    }
}
