//! What to compile.
//!
//! A recipe names each dictionary and where its source is. Nothing about the
//! dictionary set is hardcoded, so a user's own stack is their own recipe file.

use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use serde::Deserialize;

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
    #[serde(default)]
    pub xfst: Option<PathBuf>,
    /// A Plover JSON dictionary to import.
    #[serde(default)]
    pub json: Option<PathBuf>,
    #[serde(default = "default_version")]
    pub version: String,
}

fn default_version() -> String {
    "0".into()
}

impl Dict {
    pub fn source(&self) -> Result<Source<'_>> {
        match (&self.xfst, &self.json) {
            (Some(p), None) => Ok(Source::Xfst(p)),
            (None, Some(p)) => Ok(Source::Json(p)),
            (None, None) => bail!("{}: needs one of `xfst` or `json`", self.name),
            (Some(_), Some(_)) => bail!("{}: has both `xfst` and `json`", self.name),
        }
    }
}

pub enum Source<'a> {
    Xfst(&'a Path),
    Json(&'a Path),
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
