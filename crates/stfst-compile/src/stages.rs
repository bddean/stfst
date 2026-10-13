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
use std::time::{Duration, SystemTime};

use anyhow::{bail, Result};
use stfst_core::System;

use crate::pipeline::run;
use crate::recipe::{resolve, Dict, Recipe};
use crate::{fresh, prelude, STAMP};

/// How long a cached net no compile has used is kept.
const KEEP: Duration = Duration::from_secs(3 * 24 * 3600);

/// Write the pipeline's files, unless they are current. `without` names stages
/// left out, for measuring what one does: the files go to
/// `WORK/NAME/without-STAGE` and not `pipeline`.
///
/// Each stage's net, and each prefix's net and file, is cached under
/// `WORK/cache` by what it was built from, so a change to one stage rebuilds
/// that stage and the prefixes from it on, and dictionaries that share a stage
/// share its net.
#[allow(clippy::too_many_arguments)]
pub fn compile(
    recipe: &Recipe,
    base: &Path,
    system: &System,
    system_path: &Path,
    dict: &Dict,
    work: &Path,
    force: bool,
    without: &[String],
) -> Result<()> {
    let dir = match without {
        [] => work.join(&dict.name).join("pipeline"),
        _ => work.join(&dict.name).join(format!("without-{}", without.join("-"))),
    };
    let stages = dict
        .pipeline
        .iter()
        .filter(|name| !without.contains(name))
        .map(|name| recipe.stage(name))
        .collect::<Result<Vec<_>>>()?;
    if let Some(name) = without.iter().find(|n| !dict.pipeline.contains(n)) {
        bail!("{}'s pipeline has no stage {name}", dict.name);
    }
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
    let cache = work.join("cache");
    std::fs::create_dir_all(&cache)?;
    prune(&cache);

    // What each stage is built from, and its net, compiled where the cache has
    // none. The stages are independent, so they compile at once.
    let mut sources = Vec::new();
    for stage in &stages {
        let source = match &stage.lexc {
            Some(src) => Source::Lexc(resolve(base, src)),
            None => {
                let mut script = prelude::generate_for(system, Some(&[]));
                for src in &stage.xfst {
                    let src = resolve(base, src);
                    script.push_str(
                        &std::fs::read_to_string(&src)
                            .map_err(|e| anyhow::anyhow!("reading {}: {e}", src.display()))?,
                    );
                }
                Source::Xfst(script)
            }
        };
        let key = source.key()?;
        sources.push((stage.name.as_str(), source, cache.join(format!("stage-{key:016x}.hfst")), key));
    }
    std::thread::scope(|scope| {
        let jobs: Vec<_> = sources
            .iter()
            .filter(|(_, _, net, _)| force || !used(net))
            .map(|(name, source, net, _)| {
                scope.spawn(move || source.compile(net).map_err(|e| e.context(format!("stage {name}"))))
            })
            .collect();
        jobs.into_iter().try_for_each(|j| j.join().expect("a stage's compile panicked"))
    })?;

    // Each prefix, composed from the one before it.
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir)?;
    let (mut prev, mut key) = (sources[0].2.clone(), sources[0].3);
    for (name, _, net, stage_key) in &sources[1..] {
        key = fnv(key, &stage_key.to_le_bytes());
        let out = cache.join(format!("prefix-{key:016x}.hfst"));
        let tsv = out.with_extension("tsv");
        if force || !used(&out) || !used(&tsv) {
            let script = format!(
                "load stack {}\ndefine A ;\nload stack {}\ndefine B ;\ndefine P [ A .o. B ] ;\n\
                 push P\nsave stack {}\n",
                prev.display(),
                net.display(),
                tmp(&out).display()
            );
            let file = dir.join(format!("{name}.xfst"));
            std::fs::write(&file, script)?;
            run("hfst-xfst", &["-F", path(&file)])?;
            std::fs::rename(tmp(&out), &out)?;
            let mut rows: Vec<String> = run("hfst-fst2strings", &[path(&out)])?
                .lines()
                .map(|line| match line.split_once(':') {
                    Some((upper, lower)) => format!("{upper}\t{lower}"),
                    None => format!("{line}\t{line}"),
                })
                .collect();
            rows.sort();
            rows.dedup();
            std::fs::write(tmp(&tsv), rows.join("\n") + "\n")?;
            std::fs::rename(tmp(&tsv), &tsv)?;
        }
        let rows = std::fs::read_to_string(&tsv)?;
        let n = rows.lines().count();
        if n == 0 {
            bail!("{}: the pipeline relates nothing after stage {name}", dict.name);
        }
        eprintln!("  {} pipeline: {n} paths after {name}", dict.name);
        std::fs::write(dir.join(format!("{name}.tsv")), rows)?;
        prev = out;
    }
    // Last, and a new file, so the directory's mtime is when this finished.
    std::fs::write(dir.join(STAMP), stamp)?;
    Ok(())
}

enum Source {
    Lexc(PathBuf),
    Xfst(String),
}

impl Source {
    /// What the net is a function of: the compiler, and the source's text.
    fn key(&self) -> Result<u64> {
        let compiler = env!("STFST_COMPILER_HASH").as_bytes();
        Ok(match self {
            Source::Lexc(src) => fnv(fnv(FNV, compiler), &std::fs::read(src)?),
            Source::Xfst(script) => fnv(fnv(fnv(FNV, compiler), b"xfst"), script.as_bytes()),
        })
    }

    fn compile(&self, net: &Path) -> Result<()> {
        let out = tmp(net);
        match self {
            Source::Lexc(src) => {
                run("hfst-lexc", &["-q", path(src), "-o", path(&out)])?;
            }
            Source::Xfst(script) => {
                let file = out.with_extension("xfst");
                std::fs::write(&file, format!("{script}\npush Stage\nsave stack {}\n", out.display()))?;
                run("hfst-xfst", &["-F", path(&file)])?;
                let _ = std::fs::remove_file(&file);
            }
        }
        std::fs::rename(&out, net)?;
        Ok(())
    }
}

const FNV: u64 = 0xcbf2_9ce4_8422_2325;

/// FNV-1a, as build.rs hashes the compiler: stable across Rust releases.
fn fnv(mut hash: u64, bytes: &[u8]) -> u64 {
    for &b in bytes {
        hash = (hash ^ b as u64).wrapping_mul(0x0100_0000_01b3);
    }
    hash
}

fn tmp(p: &Path) -> PathBuf {
    let mut name = p.file_name().expect("a cache file").to_os_string();
    name.push(format!(".{}.tmp", std::process::id()));
    p.with_file_name(name)
}

/// Whether the cache holds `p`, which is then marked used.
fn used(p: &Path) -> bool {
    std::fs::File::options()
        .append(true)
        .open(p)
        .and_then(|f| f.set_modified(SystemTime::now()))
        .is_ok()
}

/// Remove what no compile has used for `KEEP`.
fn prune(cache: &Path) {
    let Ok(entries) = std::fs::read_dir(cache) else { return };
    let now = SystemTime::now();
    for e in entries.flatten() {
        let old = e
            .metadata()
            .and_then(|m| m.modified())
            .is_ok_and(|t| now.duration_since(t).unwrap_or_default() > KEEP);
        if old {
            let _ = std::fs::remove_file(e.path());
        }
    }
}

fn path(p: &Path) -> &str {
    p.to_str().expect("a path that is UTF-8")
}
