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

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, LazyLock, Mutex, OnceLock};
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
            .map(|(name, source, net, _)| {
                scope.spawn(move || once(net, || match force || !used(net) {
                    true => source.compile(net).map_err(|e| e.context(format!("stage {name}"))),
                    false => Ok(()),
                }))
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
            // hfst-xfst takes several times as long for the same composition.
            run("hfst-compose", &["-1", path(&prev), "-2", path(net), "-o", path(&tmp(&out))])?;
            std::fs::rename(tmp(&out), &out)?;
            let mut rows = paths(&run("hfst-fst2txt", &[path(&out)])?)
                .map_err(|e| e.context(format!("{}: {name}", dict.name)))?;
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

/// Each path of an acyclic net, from hfst-fst2txt's listing, as
/// `upper<TAB>lower`. hfst-fst2strings is forty times slower on a pipeline's
/// prefix.
fn paths(txt: &str) -> Result<Vec<String>> {
    #[derive(Default)]
    struct State<'a> {
        arcs: Vec<(usize, &'a str, &'a str)>,
        last: bool,
    }
    let mut states: Vec<State> = Vec::new();
    let symbol = |s| match s {
        "@0@" | "@_EPSILON_SYMBOL_@" => "",
        "@_SPACE_@" => " ",
        "@_TAB_@" => "\t",
        s => s,
    };
    for line in txt.lines() {
        let fields: Vec<&str> = line.split('\t').collect();
        let (state, arc): (usize, _) = match fields[..] {
            [from, to, upper, lower, ..] => (from.parse()?, Some((to.parse()?, symbol(upper), symbol(lower)))),
            [state, ..] => (state.parse()?, None),
            [] => continue,
        };
        let top = arc.map_or(state, |(to, _, _)| state.max(to));
        if states.len() <= top {
            states.resize_with(top + 1, Default::default);
        }
        match arc {
            Some(arc) => states[state].arcs.push(arc),
            None => states[state].last = true,
        }
    }
    let mut rows = Vec::new();
    if states.is_empty() {
        return Ok(rows);
    }
    // Depth-first. A frame is a state, the next arc to take from it, and the
    // lengths of the strings that reach it.
    let (mut upper, mut lower) = (String::new(), String::new());
    let mut stack = vec![(0, 0, 0, 0)];
    while let Some((state, next, u, l)) = stack.last_mut() {
        upper.truncate(*u);
        lower.truncate(*l);
        let State { arcs, last } = &states[*state];
        if *next == 0 && *last {
            rows.push(format!("{upper}\t{lower}"));
        }
        let Some(&(to, a, b)) = arcs.get(*next) else {
            stack.pop();
            continue;
        };
        *next += 1;
        upper.push_str(a);
        lower.push_str(b);
        if stack.len() > states.len() {
            bail!("the net has a cycle");
        }
        stack.push((to, 0, upper.len(), lower.len()));
    }
    Ok(rows)
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

/// Run `build` for `net` once in this process. Pipelines compile at once and
/// share stages, so a second caller waits for the first one's result.
fn once(net: &Path, build: impl FnOnce() -> Result<()>) -> Result<()> {
    type Done = Arc<OnceLock<Result<(), String>>>;
    static NETS: LazyLock<Mutex<HashMap<PathBuf, Done>>> = LazyLock::new(Default::default);
    let done = NETS.lock().unwrap().entry(net.to_path_buf()).or_default().clone();
    done.get_or_init(|| build().map_err(|e| format!("{e:#}")))
        .clone()
        .map_err(anyhow::Error::msg)
}

fn tmp(p: &Path) -> PathBuf {
    let mut name = p.file_name().expect("a cache file").to_os_string();
    static NEXT: AtomicUsize = AtomicUsize::new(0);
    thread_local!(static THREAD: usize = NEXT.fetch_add(1, Ordering::Relaxed));
    name.push(format!(".{}.{}.tmp", std::process::id(), THREAD.with(|t| *t)));
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
