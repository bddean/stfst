//! Pipelines: a dictionary's stages composed in order.
//!
//! A stage is a relation, compiled on its own from lexc or from xfst that
//! defines `Stage`. A dictionary's `pipeline` composes them, and what each
//! prefix of the composition relates is written out, one `upper<TAB>lower` line
//! per path, sorted: `WORK/NAME/pipeline/STAGE.tsv`. The first stage is the
//! pipeline's source and gets no file of its own.
//!
//! What a pipeline relates is not an artifact. It is what a generator resolves
//! into one, and the files are what `scripts/check.sh` holds to
//! `tests/golden/NAME/`, so a change to one stage shows as a diff at that stage.

use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use stfst_core::System;

use crate::pipeline::run;
use crate::recipe::{resolve, Dict, Recipe};
use crate::{fresh, prelude, STAMP};

/// Write the pipeline's files, unless they are current.
pub fn compile(
    recipe: &Recipe,
    base: &Path,
    system: &System,
    system_path: &Path,
    dict: &Dict,
    work: &Path,
    force: bool,
) -> Result<()> {
    let dir = work.join(&dict.name).join("pipeline");
    let stages = dict
        .pipeline
        .iter()
        .map(|name| recipe.stage(name))
        .collect::<Result<Vec<_>>>()?;
    let mut inputs = vec![system_path.to_path_buf()];
    let mut described = Vec::new();
    for stage in &stages {
        let files: Vec<PathBuf> = stage
            .files()?
            .into_iter()
            .map(|f| resolve(base, f))
            .collect();
        described.push(format!("{} = {:?}", stage.name, stage.files()?));
        inputs.extend(files);
    }
    let stamp = format!(
        "compiler = {:?}\nsystem = {:?}\n{}\n",
        env!("STFST_COMPILER_HASH"),
        system_path.display().to_string(),
        described.join("\n")
    );
    if !force && fresh(&dir, &stamp, &inputs) {
        eprintln!("  {} pipeline: up to date", dict.name);
        return Ok(());
    }
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir)?;

    let mut nets = Vec::new();
    for stage in &stages {
        let net = dir.join(format!("{}.hfst", stage.name));
        match &stage.lexc {
            Some(src) => {
                let src = resolve(base, src);
                run("hfst-lexc", &["-q", path(&src), "-o", path(&net)])?;
            }
            None => {
                let mut script = prelude::generate_for(system, Some(&[]));
                for src in &stage.xfst {
                    let src = resolve(base, src);
                    script.push_str(
                        &std::fs::read_to_string(&src)
                            .map_err(|e| anyhow::anyhow!("reading {}: {e}", src.display()))?,
                    );
                }
                script.push_str(&format!("\npush Stage\nsave stack {}\n", net.display()));
                let file = dir.join(format!("{}.xfst", stage.name));
                std::fs::write(&file, script)?;
                run("hfst-xfst", &["-F", path(&file)])?;
            }
        }
        nets.push(net);
    }

    // Each prefix, composed from the one before it.
    let mut script = String::new();
    for (k, net) in nets.iter().enumerate() {
        script.push_str(&format!(
            "load stack {}\ndefine Stage{k} ;\n",
            net.display()
        ));
    }
    let mut prefixes = Vec::new();
    for (k, stage) in stages.iter().enumerate().skip(1) {
        let prev = if k == 1 {
            "Stage0".to_string()
        } else {
            format!("Prefix{}", k - 1)
        };
        let out = dir.join(format!("{}.prefix.hfst", stage.name));
        script.push_str(&format!(
            "define Prefix{k} [ {prev} .o. Stage{k} ] ;\npush Prefix{k}\nsave stack {}\nclear stack\n",
            out.display()
        ));
        prefixes.push((stage.name.as_str(), out));
    }
    let file = dir.join("compose.xfst");
    std::fs::write(&file, script)?;
    run("hfst-xfst", &["-F", path(&file)])?;

    for (name, net) in prefixes {
        let mut rows: Vec<String> = run("hfst-fst2strings", &[path(&net)])?
            .lines()
            .map(|line| match line.split_once(':') {
                Some((upper, lower)) => format!("{upper}\t{lower}"),
                None => format!("{line}\t{line}"),
            })
            .collect();
        if rows.is_empty() {
            bail!(
                "{}: the pipeline relates nothing after stage {name}",
                dict.name
            );
        }
        rows.sort();
        rows.dedup();
        eprintln!(
            "  {} pipeline: {} paths after {name}",
            dict.name,
            rows.len()
        );
        std::fs::write(dir.join(format!("{name}.tsv")), rows.join("\n") + "\n")?;
    }
    // Last, and a new file, so the directory's mtime is when this finished.
    std::fs::write(dir.join(STAMP), stamp)?;
    Ok(())
}

fn path(p: &Path) -> &str {
    p.to_str().expect("a path that is UTF-8")
}
