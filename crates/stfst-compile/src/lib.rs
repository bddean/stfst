pub mod json;
pub mod lexc;
pub mod pipeline;
pub mod plain;
pub mod prelude;
pub mod recipe;
pub mod translate;

use std::path::Path;

use anyhow::{bail, Result};
use stfst_core::System;

/// Compile every dictionary in a recipe into `out`, one artifact directory each.
pub fn compile_recipe(recipe_path: &Path, out: &Path, work: &Path) -> Result<()> {
    let (recipe, base) = recipe::Recipe::load(recipe_path)?;
    let system = System::load(recipe::resolve(&base, &recipe.system))?;

    for dict in &recipe.dict {
        let work_dir = work.join(&dict.name);
        std::fs::create_dir_all(&work_dir)?;
        let forward = work_dir.join("forward.hfstol");
        let reverse = work_dir.join("reverse.hfstol");

        match dict.source()? {
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

        let artifact = out.join(format!("{}.stfst", dict.name));
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
