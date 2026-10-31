//! A design: which chord each atom has, or which keys the search may give it.
//!
//! One TOML file per design. `include` pulls in shared tables (a board's
//! effort weights, a theory's fixed chords); the including file wins. Paths are
//! relative to the file that names them, and `${VAR}` expands from the
//! environment.

use std::path::{Path, PathBuf};

use anyhow::{anyhow, bail, Context, Result};
use indexmap::IndexMap;
use serde::Deserialize;
use stfst_core::{Stroke, System};

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields, default)]
pub struct Objective {
    /// Weight of effort against lost frequency (the handoff's `LAM`).
    pub effort: f64,
    /// Effort per stroke is `ln(1 + weighted keys)` rather than the weighted keys.
    pub log: bool,
    /// Most keys a free atom's chord may hold (the handoff's `MAXB`).
    pub max_keys: u32,
    /// Weight of the frequency of clusters whose chords are out of steno order.
    pub order: f64,
    /// Tags whose free atoms must all have different chords.
    pub distinct: Vec<String>,
}

impl Default for Objective {
    fn default() -> Self {
        Objective { effort: 0.02, log: true, max_keys: 4, order: 0.0, distinct: Vec::new() }
    }
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields, default)]
pub struct Search {
    /// `anneal`, `descent`, or `anneal+descent`.
    pub method: String,
    pub moves: u64,
    pub seeds: u32,
    pub seed: u64,
    pub t0: f64,
    pub t1: f64,
    /// Share of moves that pick an atom of a word that currently loses.
    pub target: f64,
    /// Descent tries every chord of a zone up to this many, else only chords
    /// within two keys of the current one.
    pub max_candidates: usize,
}

impl Default for Search {
    fn default() -> Self {
        Search {
            method: "anneal+descent".into(),
            moves: 300_000,
            seeds: 1,
            seed: 1,
            t0: 0.01,
            t1: 1e-6,
            target: 0.5,
            max_candidates: 4096,
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct File {
    system: PathBuf,
    #[serde(default)]
    effort: IndexMap<String, f64>,
    #[serde(default)]
    objective: Objective,
    #[serde(default)]
    zones: IndexMap<String, String>,
    #[serde(default)]
    atoms: IndexMap<String, String>,
    #[serde(default)]
    fixed: IndexMap<String, String>,
    #[serde(default)]
    choices: IndexMap<String, Vec<String>>,
    #[serde(default)]
    search: Search,
    #[serde(default)]
    words: toml::Table,
    #[serde(default)]
    encode: toml::Table,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Role {
    Fixed(Stroke),
    /// Searched, within these keys.
    Free(Stroke),
}

#[derive(Debug)]
pub struct Design {
    pub path: PathBuf,
    pub system: System,
    /// Per key index.
    pub key_weight: Vec<f64>,
    pub objective: Objective,
    pub search: Search,
    zones: IndexMap<String, Stroke>,
    atoms: IndexMap<String, Stroke>,
    fixed: IndexMap<String, Stroke>,
    /// Atom or tag -> the chords descent may give it, in place of its zone's.
    choices: IndexMap<String, Vec<Stroke>>,
    /// What `scripts/layout-units.py` reads.
    pub words: toml::Table,
    pub encode: toml::Table,
}

impl Design {
    pub fn load(path: impl AsRef<Path>) -> Result<Design> {
        let path = path.as_ref();
        let table = load_merged(path, &mut Vec::new())?;
        let file: File =
            toml::Value::Table(table).try_into().with_context(|| format!("reading design {}", path.display()))?;
        let system = System::load(&file.system)?;

        let chord = |what: &str, s: &str| -> Result<Stroke> {
            parse_chord(&system, s).with_context(|| format!("{what} = {s:?} in {}", path.display()))
        };
        let mut key_weight = vec![1.0; system.key_count()];
        for (keys, w) in &file.effort {
            for i in chord(&format!("effort {keys:?}"), keys)?.indices() {
                key_weight[i] = *w;
            }
        }
        let table = |t: &IndexMap<String, String>, what: &str| -> Result<IndexMap<String, Stroke>> {
            t.iter().map(|(k, v)| Ok((k.clone(), chord(&format!("{what}.{k:?}"), v)?))).collect()
        };
        Ok(Design {
            path: path.to_path_buf(),
            key_weight,
            objective: file.objective,
            search: file.search,
            zones: table(&file.zones, "zones")?,
            atoms: table(&file.atoms, "atoms")?,
            fixed: table(&file.fixed, "fixed")?,
            choices: file
                .choices
                .iter()
                .map(|(k, cs)| {
                    Ok((k.clone(), cs.iter().map(|c| chord(&format!("choices.{k:?}"), c)).collect::<Result<_>>()?))
                })
                .collect::<Result<_>>()?,
            words: file.words,
            encode: file.encode,
            system,
        })
    }

    /// A literal `=OUTLINE` atom is that chord; else `[fixed]`, then the atom's
    /// own zone in `[atoms]`, then its tag's in `[zones]`, then the keys of its
    /// `[choices]`.
    pub fn role(&self, atom: &str) -> Option<Role> {
        if let Some(lit) = atom.strip_prefix('=') {
            return parse_chord(&self.system, lit).ok().map(Role::Fixed);
        }
        if let Some(&c) = self.fixed.get(atom) {
            return Some(Role::Fixed(c));
        }
        let tag = atom.split(':').next().unwrap_or(atom);
        let zone = self.atoms.get(atom).or_else(|| self.zones.get(tag)).copied();
        zone.or_else(|| Some(self.choices(atom)?.iter().fold(Stroke::EMPTY, |z, &c| z.union(c)))).map(Role::Free)
    }

    /// The chords `[choices]` lists for an atom, or else for its tag.
    pub fn choices(&self, atom: &str) -> Option<&[Stroke]> {
        let tag = atom.split(':').next().unwrap_or(atom);
        self.choices.get(atom).or_else(|| self.choices.get(tag)).map(Vec::as_slice)
    }

    pub fn format(&self, chord: Stroke) -> String {
        if chord.is_empty() {
            String::new()
        } else {
            self.system.format_outline(&stfst_core::Outline(vec![chord]))
        }
    }

    /// An outline of up to `MAX_STROKES` strokes, its empty ones skipped.
    pub fn format_outline(&self, strokes: &[Stroke]) -> String {
        self.system.format_outline(&stfst_core::Outline(strokes.iter().copied().filter(|s| !s.is_empty()).collect()))
    }

    pub fn name(&self) -> String {
        self.path.file_stem().map(|s| s.to_string_lossy().into_owned()).unwrap_or_default()
    }
}

/// One stroke in outline notation; `""` is no keys.
pub fn parse_chord(system: &System, s: &str) -> Result<Stroke> {
    if s.is_empty() {
        return Ok(Stroke::EMPTY);
    }
    let outline = system.parse_outline(s)?;
    match outline.0.as_slice() {
        [one] => Ok(*one),
        _ => bail!("{s:?} is more than one stroke"),
    }
}

/// The file with its includes merged under it and its paths made absolute.
fn load_merged(path: &Path, stack: &mut Vec<PathBuf>) -> Result<toml::Table> {
    let canon = path.canonicalize().with_context(|| format!("reading {}", path.display()))?;
    if stack.contains(&canon) {
        bail!("{} includes itself", path.display());
    }
    let text = std::fs::read_to_string(path).with_context(|| format!("reading {}", path.display()))?;
    let mut table: toml::Table = toml::from_str(&text).with_context(|| format!("parsing {}", path.display()))?;
    let dir = canon.parent().unwrap_or(Path::new(".")).to_path_buf();
    resolve_paths(&mut table, &dir)?;

    let includes = match table.remove("include") {
        None => Vec::new(),
        Some(toml::Value::Array(a)) => a,
        Some(_) => bail!("{}: include must be a list of paths", path.display()),
    };
    stack.push(canon);
    let mut merged = toml::Table::new();
    for inc in includes {
        let inc = inc.as_str().ok_or_else(|| anyhow!("{}: include lists a non-string", path.display()))?;
        let inc_path = dir.join(expand(inc)?);
        merge(&mut merged, load_merged(&inc_path, stack)?);
    }
    stack.pop();
    merge(&mut merged, table);
    Ok(merged)
}

fn merge(into: &mut toml::Table, from: toml::Table) {
    for (k, v) in from {
        match (into.get_mut(&k), v) {
            (Some(toml::Value::Table(a)), toml::Value::Table(b)) => merge(a, b),
            (_, v) => {
                into.insert(k, v);
            }
        }
    }
}

fn resolve_paths(table: &mut toml::Table, dir: &Path) -> Result<()> {
    let fix = |v: Option<&mut toml::Value>| -> Result<()> {
        if let Some(toml::Value::String(s)) = v {
            *s = dir.join(expand(s)?).to_string_lossy().into_owned();
        }
        Ok(())
    };
    fix(table.get_mut("system"))?;
    for (section, key) in [("words", "path"), ("encode", "lists")] {
        if let Some(toml::Value::Table(t)) = table.get_mut(section) {
            fix(t.get_mut(key))?;
        }
    }
    Ok(())
}

fn expand(s: &str) -> Result<String> {
    let mut out = String::new();
    let mut rest = s;
    while let Some(start) = rest.find("${") {
        out.push_str(&rest[..start]);
        let end = rest[start..].find('}').ok_or_else(|| anyhow!("unclosed ${{ in {s:?}"))? + start;
        let var = &rest[start + 2..end];
        out.push_str(&std::env::var(var).with_context(|| format!("{s:?} needs ${var} set"))?);
        rest = &rest[end + 1..];
    }
    out.push_str(rest);
    Ok(out)
}
