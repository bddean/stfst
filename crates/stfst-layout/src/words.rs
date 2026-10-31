//! The words a design is scored on, as `scripts/layout-units.py` writes them.

use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::io::{BufRead, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use anyhow::{bail, Context, Result};
use serde::Deserialize;

use crate::design::Design;

#[derive(Debug, Clone, Deserialize)]
pub struct Word {
    pub w: String,
    pub f: f64,
    pub nsyl: u32,
    pub kind: String,
    /// Each stroke's atoms.
    pub strokes: Vec<Vec<String>>,
    /// Outlines in the order `resolve` tries them; empty for one outline.
    #[serde(default)]
    pub alts: Vec<Alt>,
    /// Its onset and coda clusters, in spoken order.
    #[serde(default)]
    pub clusters: Vec<Cluster>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Cluster {
    pub atoms: Vec<String>,
    /// The theory writes it with a chord of its own (Ireland's -mp, -st).
    #[serde(default)]
    pub fused: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Alt {
    pub cost: f64,
    pub strokes: Vec<Vec<String>>,
    /// May take an outline Typey Type holds.
    pub displace: bool,
    /// Which fallback the encoder added it as, if any.
    #[serde(default)]
    pub fallback: Option<String>,
}

/// An outline the rest of the stack writes.
#[derive(Debug, Clone, Deserialize)]
pub struct Held {
    pub held: String,
    pub by: String,
    pub word: String,
    /// A word at least this frequent takes it; none ever does if absent.
    pub f: Option<f64>,
}

#[derive(Debug)]
pub struct Words {
    pub path: PathBuf,
    pub meta: serde_json::Value,
    pub words: Vec<Word>,
    pub held: Vec<Held>,
}

pub fn read(path: &Path) -> Result<Words> {
    let file = std::fs::File::open(path).with_context(|| format!("reading {}", path.display()))?;
    let mut lines = std::io::BufReader::new(file).lines();
    let head: serde_json::Value = serde_json::from_str(&lines.next().context("empty units file")??)?;
    let meta = head.get("meta").cloned().context("units file has no meta line")?;
    let (mut words, mut held) = (Vec::new(), Vec::new());
    for line in lines {
        let line = line?;
        let ctx = || format!("in {}", path.display());
        if line.starts_with("{\"held\"") {
            held.push(serde_json::from_str(&line).with_context(ctx)?);
        } else {
            words.push(serde_json::from_str(&line).with_context(ctx)?);
        }
    }
    Ok(Words { path: path.to_path_buf(), meta, words, held })
}

pub fn workspace_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
}

/// The design's words, encoded by `scripts/layout-units.py` unless a run with the
/// same `[words]`, `[encode]` and script is cached under `build/layout`.
pub fn encode(design: &Design) -> Result<Words> {
    let root = workspace_root();
    let script = root.join("scripts/layout-units.py");
    let mut words = design.words.clone();
    if !words.contains_key("path") {
        if words.get("source").and_then(|s| s.as_str()) == Some("handoff") {
            bail!("{}: [words] needs a path", design.path.display());
        }
        let lexc = root.join("dictionaries/lexicon.lexc");
        words.insert("path".into(), lexc.to_string_lossy().into_owned().into());
    }
    let config = serde_json::json!({ "words": words, "encode": design.encode }).to_string();
    let mut h = DefaultHasher::new();
    config.hash(&mut h);
    std::fs::read(&script).with_context(|| format!("reading {}", script.display()))?.hash(&mut h);
    let dir = root.join("build/layout");
    let out = dir.join(format!("units-{:016x}.jsonl", h.finish()));
    if !out.exists() {
        std::fs::create_dir_all(&dir)?;
        // The stfst-two-syllable source imports the generators, which need the phrasing venv.
        let venv = std::env::var("HOME").unwrap_or_default() + "/.config/plover/phrasing/.venv/bin/python";
        let python =
            std::env::var("PYTHON").unwrap_or_else(|_| if Path::new(&venv).exists() { venv } else { "python3".into() });
        let tmp = out.with_extension("tmp");
        let mut child = Command::new(&python)
            .arg("-I")
            .arg(&script)
            .stdin(Stdio::piped())
            .stdout(std::fs::File::create(&tmp)?)
            .spawn()
            .with_context(|| format!("running {python} {}", script.display()))?;
        child.stdin.take().unwrap().write_all(config.as_bytes())?;
        if !child.wait()?.success() {
            bail!("{} failed", script.display());
        }
        std::fs::rename(&tmp, &out)?;
    }
    read(&out)
}
