//! What to compile.
//!
//! A recipe names each dictionary and where its source is. Nothing about the
//! dictionary set is hardcoded, so a user's own stack is their own recipe file.

use std::num::NonZeroU8;
use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use serde::Deserialize;
use stfst_core::Shown;

#[derive(Debug, Deserialize)]
pub struct Recipe {
    pub system: PathBuf,
    #[serde(default)]
    pub dict: Vec<Dict>,
}

#[derive(Debug, Deserialize)]
pub struct Dict {
    pub name: String,
    /// Higher shadows lower, as `plover.cfg` file order does today.
    pub priority: i32,
    /// Hand-written xfst defining `Grammar`. Gets the §3 acceptor checks.
    /// Several files concatenate in order, which is how two dictionaries share
    /// a table: the shared file comes first and the grammar reads its defines.
    #[serde(default)]
    pub xfst: Vec<PathBuf>,
    /// A Plover JSON dictionary to import.
    #[serde(default)]
    pub json: Option<PathBuf>,
    /// A flat dictionary written in the record language. See `plain`.
    #[serde(default)]
    pub plain: Option<PathBuf>,
    #[serde(default = "default_version")]
    pub version: String,
    /// How many words of already-written text this dictionary is shown, after the
    /// keys. Zero for a dictionary that only reads strokes, which is most of them.
    #[serde(default)]
    pub words: u8,
    /// How many words of the *next* unit's text it is shown instead, which is what
    /// a frame reads: the stroke is held until that text exists. See `Shown`.
    #[serde(default)]
    pub next: u8,
    /// How far a host that needs a longest key should look, for a dictionary
    /// whose outlines have no longest. Only such a dictionary needs it: where the
    /// language has a longest outline, the compiler reads it off the automaton.
    #[serde(default)]
    pub strokes: Option<u32>,
}

fn default_version() -> String {
    "0".into()
}

impl Dict {
    /// The text this dictionary reads, if any. A dictionary reads one span or
    /// none: a rule over the words before the keys and a rule over the words
    /// after them would need two windows on one tape.
    pub fn shown(&self) -> Result<Option<Shown>> {
        match (NonZeroU8::new(self.words), NonZeroU8::new(self.next)) {
            (Some(_), Some(_)) => bail!("{}: has both `words` and `next`", self.name),
            (Some(n), None) => Ok(Some(Shown::Written(n))),
            (None, Some(n)) => Ok(Some(Shown::Next(n))),
            (None, None) => Ok(None),
        }
    }

    pub fn source(&self) -> Result<Source<'_>> {
        let mut found: Vec<Source<'_>> = Vec::new();
        if !self.xfst.is_empty() {
            found.push(Source::Xfst(&self.xfst));
        }
        if let Some(p) = &self.json {
            found.push(Source::Json(p));
        }
        if let Some(p) = &self.plain {
            found.push(Source::Plain(p));
        }
        match found.len() {
            1 => Ok(found.remove(0)),
            0 => bail!("{}: needs one of `xfst`, `json` or `plain`", self.name),
            _ => bail!("{}: has more than one source", self.name),
        }
    }
}

pub enum Source<'a> {
    Xfst(&'a [PathBuf]),
    Json(&'a Path),
    Plain(&'a Path),
}

impl Recipe {
    pub fn load(path: &Path) -> Result<(Recipe, PathBuf)> {
        let text = std::fs::read_to_string(path)
            .with_context(|| format!("reading {}", path.display()))?;
        let recipe: Recipe = toml::from_str(&text)
            .with_context(|| format!("parsing {}", path.display()))?;
        let base = path
            .parent()
            .unwrap_or(Path::new("."))
            .to_path_buf();
        Ok((recipe, base))
    }
}

/// Resolve a recipe-relative path, expanding a leading `~`.
pub fn resolve(base: &Path, path: &Path) -> PathBuf {
    if let Ok(rest) = path.strip_prefix("~") {
        if let Some(home) = std::env::var_os("HOME") {
            return PathBuf::from(home).join(rest);
        }
    }
    if path.is_absolute() {
        path.to_path_buf()
    } else {
        base.join(path)
    }
}
