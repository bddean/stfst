pub mod json;
pub mod lexc;
pub mod pipeline;
pub mod plain;
pub mod prelude;
pub mod recipe;
pub mod translate;

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
    // What every artifact is compiled through, so a change to any of them is a
    // change to all of them. The compiler is one of them: a rule it changed is
    // not in any source file.
    let common = [recipe_path.to_path_buf(), system_path, std::env::current_exe()?];

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
        inputs.extend(common.iter().cloned());
        if !which.force && fresh(&artifact, &inputs) {
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

/// Whether this artifact was built after everything that goes into it.
///
/// The directory's mtime is when it was built, because `write_artifact` renames
/// the manifest into place last. An input this cannot read is a reason to
/// compile, so the compiler reports what is wrong with it rather than this.
fn fresh(artifact: &Path, inputs: &[PathBuf]) -> bool {
    let Some(built) = modified(artifact) else {
        return false;
    };
    inputs.iter().all(|p| matches!(modified(p), Some(t) if t <= built))
}

fn modified(path: &Path) -> Option<SystemTime> {
    std::fs::metadata(path).and_then(|m| m.modified()).ok()
}
