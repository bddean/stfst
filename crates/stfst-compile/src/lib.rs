pub mod json;
pub mod lexc;
mod net;
pub mod pipeline;
pub mod plain;
pub mod prelude;
pub mod recipe;
pub mod stages;

/// Plover translations to records. It lives in `stfst-core` because it is the
/// record language's front door and a runtime host needs it too.
pub use stfst_core::translate;

use std::path::{Path, PathBuf};
use std::time::SystemTime;

use anyhow::{bail, Result};
use stfst_core::System;

/// Which dictionaries of a recipe to compile.
#[derive(Default)]
pub struct Which {
    /// The names to compile, or every dictionary in the recipe if empty.
    pub names: Vec<String>,
    /// Compile one whose artifact is newer than its sources anyway.
    pub force: bool,
    /// Write the pipelines and no artifact, which is what a generator reads.
    pub pipelines_only: bool,
    /// Stages left out of each pipeline, which then writes no artifact.
    pub ablate: Vec<String>,
}

/// Compile every dictionary in a recipe into `out`, one artifact directory each.
pub fn compile_recipe(recipe_path: &Path, out: &Path, work: &Path, which: &Which) -> Result<()> {
    let (recipe, base) = recipe::Recipe::load(recipe_path)?;
    let system_path = recipe::resolve(&base, &recipe.system);
    let system = System::load(&system_path)?;
    let unknown: Vec<&str> = which
        .names
        .iter()
        .filter(|n| !recipe.dict.iter().any(|d| d.name == **n))
        .map(String::as_str)
        .collect();
    if !unknown.is_empty() {
        bail!("{} has no dictionary named {}", recipe_path.display(), unknown.join(", "));
    }

    // Each dictionary on its own thread. They share only the stage cache, which
    // compiles a net once however many pipelines want it.
    let dicts: Vec<_> = recipe
        .dict
        .iter()
        .filter(|d| which.names.is_empty() || which.names.contains(&d.name))
        .collect();
    let ctx = (&recipe, base.as_path(), &system, system_path.as_path());
    let failed: Vec<anyhow::Error> = std::thread::scope(|scope| {
        let jobs: Vec<_> = dicts
            .iter()
            .map(|dict| scope.spawn(move || compile_dict(ctx, dict, out, work, which)))
            .collect();
        jobs.into_iter()
            .filter_map(|j| j.join().expect("a dictionary's compile panicked").err())
            .collect()
    });
    match failed.into_iter().reduce(|a, b| anyhow::anyhow!("{a:#}\n{b:#}")) {
        Some(e) => Err(e),
        None => Ok(()),
    }
}

fn compile_dict(
    (recipe, base, system, system_path): (&recipe::Recipe, &Path, &System, &Path),
    dict: &recipe::Dict,
    out: &Path,
    work: &Path,
    which: &Which,
) -> Result<()> {
    if !dict.pipeline.is_empty() {
        stages::compile(recipe, base, system, system_path, dict, work, which.force, &which.ablate)?;
    }
    if which.pipelines_only || !which.ablate.is_empty() {
        return Ok(());
    }
    let source = dict.source()?;
    let artifact = out.join(format!("{}.stfst", dict.name));
    let files: Vec<PathBuf> = source
        .files()
        .iter()
        .map(|s| recipe::resolve(base, s))
        .collect();
    // An editable dictionary's file is read at load, so an edit to it is not a
    // reason to build.
    let mut inputs = if dict.editable { Vec::new() } else { files.clone() };
    inputs.push(system_path.to_path_buf());
    let reserved = recipe.reserved(dict);
    let stamp = stamp(&recipe.system, dict, &reserved)?;
    if !which.force && fresh(&artifact, &stamp, &inputs) {
        eprintln!("  {}: up to date", dict.name);
        return Ok(());
    }
    if dict.editable {
        let file = std::path::absolute(&files[0])?;
        pipeline::write_editable(&artifact, dict, system, &file)?;
        std::fs::write(artifact.join(STAMP), &stamp)?;
        eprintln!("  {} -> {} (reads {})", dict.name, artifact.display(), file.display());
        return Ok(());
    }
    pipeline::build(dict, &source, system, base, &reserved, &work.join(&dict.name), &artifact)?;
    std::fs::write(artifact.join(STAMP), &stamp)?;
    Ok(())
}

/// The file in an artifact directory that says what it was compiled from, other
/// than the source files themselves.
pub(crate) const STAMP: &str = "stamp";

/// What an artifact is a function of, besides its files' contents: the fields of
/// its own recipe entry that reach the artifact, and the compiler's source. Not
/// the rest of the recipe, so adding a dictionary rebuilds that dictionary and
/// nothing else.
fn stamp(system: &Path, dict: &recipe::Dict, reserved: &str) -> Result<String> {
    let files: Vec<String> = dict.source()?.files().iter().map(|f| f.display().to_string()).collect();
    Ok(format!(
        "compiler = {:?}\nsystem = {:?}\nname = {:?}\npriority = {}\nversion = {:?}\n\
         sources = {files:?}\neditable = {}\nshown = {:?}\nstrokes = {:?}\nreserved = {reserved:?}\n",
        env!("STFST_ARTIFACT_HASH"),
        system.display().to_string(),
        dict.name,
        dict.priority,
        dict.version,
        dict.editable,
        dict.shown()?,
        dict.strokes,
    ))
}

/// Whether this artifact was built from `stamp`, after every one of `inputs`
/// last changed.
///
/// The directory's mtime is when it was built, because `write_artifact` renames
/// the manifest into place last. An input this cannot read is a reason to
/// compile, so the compiler reports what is wrong with it rather than this.
pub(crate) fn fresh(artifact: &Path, stamp: &str, inputs: &[PathBuf]) -> bool {
    let Some(built) = modified(artifact) else {
        return false;
    };
    std::fs::read_to_string(artifact.join(STAMP)).is_ok_and(|s| s == stamp)
        && inputs.iter().all(|p| matches!(modified(p), Some(t) if t <= built))
}

fn modified(path: &Path) -> Option<SystemTime> {
    std::fs::metadata(path).and_then(|m| m.modified()).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn dict(toml: &str) -> recipe::Dict {
        toml::from_str(toml).unwrap()
    }

    #[test]
    fn an_artifact_is_fresh_for_its_own_entry_only() {
        let dir = std::env::temp_dir().join(format!("stfst-fresh-{}", std::process::id()));
        let artifact = dir.join("a.stfst");
        std::fs::create_dir_all(&artifact).unwrap();
        let source = dir.join("a.json");
        std::fs::write(&source, "{}").unwrap();
        let a = dict("name = \"a\"\npriority = 1\njson = \"a.json\"\n");
        let system = Path::new("sys.toml");
        // Built after its source, but by no recorded entry: stale.
        std::thread::sleep(std::time::Duration::from_millis(10));
        std::fs::write(artifact.join("forward.hfstol"), "").unwrap();
        assert!(!fresh(&artifact, &stamp(system, &a, "").unwrap(), std::slice::from_ref(&source)));

        std::fs::write(artifact.join(STAMP), stamp(system, &a, "").unwrap()).unwrap();
        assert!(fresh(&artifact, &stamp(system, &a, "").unwrap(), std::slice::from_ref(&source)));
        // Its own entry changed.
        let moved = dict("name = \"a\"\npriority = 2\njson = \"a.json\"\n");
        assert!(!fresh(&artifact, &stamp(system, &moved, "").unwrap(), std::slice::from_ref(&source)));
        // A source newer than the artifact.
        std::thread::sleep(std::time::Duration::from_millis(10));
        std::fs::write(&source, "{ }").unwrap();
        assert!(!fresh(&artifact, &stamp(system, &a, "").unwrap(), &[source]));
        std::fs::remove_dir_all(&dir).unwrap();
    }
}
