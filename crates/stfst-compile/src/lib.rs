pub mod json;
pub mod lexc;
pub mod pipeline;
pub mod plain;
pub mod prelude;
pub mod recipe;

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

    for dict in &recipe.dict {
        if !which.names.is_empty() && !which.names.contains(&dict.name) {
            continue;
        }
        let source = dict.source()?;
        let artifact = out.join(format!("{}.stfst", dict.name));
        let mut inputs: Vec<PathBuf> = source
            .files()
            .iter()
            .map(|s| recipe::resolve(&base, s))
            .collect();
        inputs.push(system_path.clone());
        let stamp = stamp(&recipe.system, dict);
        if !which.force && fresh(&artifact, &stamp, &inputs) {
            eprintln!("  {}: up to date", dict.name);
            continue;
        }
        let work_dir = work.join(&dict.name);
        std::fs::create_dir_all(&work_dir)?;
        let forward = work_dir.join("forward.hfstol");
        let reverse = work_dir.join("reverse.hfstol");

        match source {
            recipe::Source::Xfst(srcs) => {
                let srcs: Vec<_> = srcs.iter().map(|s| recipe::resolve(&base, s)).collect();
                let checks = pipeline::compile_xfst(&system, &srcs, &work_dir, &forward, &reverse)?;
                if !checks.passed() {
                    bail!("{} failed its checks:\n{}", dict.name, checks.report());
                }
            }
            recipe::Source::Plain(src) => {
                let src = recipe::resolve(&base, src);
                let text = std::fs::read_to_string(&src)?;
                let compiled =
                    plain::compile(&text, &system, lexc::Align::Pairwise).map_err(|errors| {
                        let lines: Vec<String> = errors.iter().map(|e| format!("  {e}")).collect();
                        anyhow::anyhow!("{}:\n{}", src.display(), lines.join("\n"))
                    })?;
                eprintln!("  {}: {} entries", dict.name, compiled.entries);
                let lexc_path = work_dir.join("plain.lexc");
                std::fs::write(&lexc_path, &compiled.lexc)?;
                pipeline::compile_lexc(&lexc_path, &work_dir, &forward, &reverse)?;
            }
            recipe::Source::Json(src) => {
                let src = recipe::resolve(&base, src);
                let imported = json::import_file(&src, &system, lexc::Align::Pairwise)?;
                eprintln!("  {}: {}", dict.name, imported.report.summary());
                let lexc_path = work_dir.join("import.lexc");
                std::fs::write(&lexc_path, &imported.lexc)?;
                pipeline::compile_lexc(&lexc_path, &work_dir, &forward, &reverse)?;
            }
        }

        pipeline::write_artifact(&artifact, dict, &system, &forward, &reverse)?;
        std::fs::write(artifact.join(STAMP), &stamp)?;
        let fwd = std::fs::metadata(artifact.join("forward.hfstol"))?.len();
        let rev = std::fs::metadata(artifact.join("reverse.hfstol"))
            .map(|m| m.len())
            .unwrap_or(0);
        eprintln!(
            "  {} -> {} ({fwd} bytes forward, {rev} reverse)",
            dict.name,
            artifact.display()
        );
    }
    Ok(())
}

/// The file in an artifact directory that says what it was compiled from, other
/// than the source files themselves.
const STAMP: &str = "stamp";

/// What an artifact is a function of, besides its files' contents: its own entry
/// in the recipe, and the compiler's source. Not the rest of the recipe, so
/// adding a dictionary rebuilds that dictionary and nothing else.
fn stamp(system: &Path, dict: &recipe::Dict) -> String {
    format!(
        "compiler = {:?}\nsystem = {:?}\n{dict:#?}\n",
        env!("STFST_COMPILER_HASH"),
        system.display().to_string(),
    )
}

/// Whether this artifact was built from `stamp`, after every one of `inputs`
/// last changed.
///
/// The directory's mtime is when it was built, because `write_artifact` renames
/// the manifest into place last. An input this cannot read is a reason to
/// compile, so the compiler reports what is wrong with it rather than this.
fn fresh(artifact: &Path, stamp: &str, inputs: &[PathBuf]) -> bool {
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
        assert!(!fresh(&artifact, &stamp(system, &a), &[source.clone()]));

        std::fs::write(artifact.join(STAMP), stamp(system, &a)).unwrap();
        assert!(fresh(&artifact, &stamp(system, &a), &[source.clone()]));
        // Its own entry changed.
        let moved = dict("name = \"a\"\npriority = 2\njson = \"a.json\"\n");
        assert!(!fresh(&artifact, &stamp(system, &moved), &[source.clone()]));
        // A source newer than the artifact.
        std::thread::sleep(std::time::Duration::from_millis(10));
        std::fs::write(&source, "{ }").unwrap();
        assert!(!fresh(&artifact, &stamp(system, &a), &[source]));
        std::fs::remove_dir_all(&dir).unwrap();
    }
}
