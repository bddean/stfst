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
use std::sync::{Arc, Condvar, LazyLock, Mutex, OnceLock};
use std::time::{Duration, SystemTime};

use anyhow::{bail, Result};
use stfst_core::System;

use crate::pipeline::run;
use crate::recipe::{resolve, Dict, Recipe};
use crate::{fresh, prelude, STAMP};

/// How many shards a pipeline is composed in, at most. A constant, not the
/// machine's cores, so the cache holds across machines.
const SHARDS: u64 = 12;

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

    // Each prefix, composed from the one before it. A prefix is a finite
    // relation, so it is composed in shards, at once, and its files are their
    // union: the first stage split by its start state's arcs.
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir)?;
    let mut key = fnv(sources[0].3, &SHARDS.to_le_bytes());
    let mut prev = shards(&sources[0].2, &cache.join(format!("shards-{key:016x}")), force)?;
    for (name, _, net, stage_key) in &sources[1..] {
        key = fnv(key, &stage_key.to_le_bytes());
        let outs: Vec<PathBuf> =
            (0..prev.len()).map(|i| cache.join(format!("prefix-{key:016x}.{i}.hfst"))).collect();
        let tsv = cache.join(format!("prefix-{key:016x}.tsv"));
        if force || !used(&tsv) || !outs.iter().all(|o| used(o)) {
            let rows = std::thread::scope(|scope| {
                let jobs: Vec<_> = prev
                    .iter()
                    .zip(&outs)
                    .map(|(prev, out)| {
                        scope.spawn(move || -> Result<Vec<String>> {
                            // hfst-xfst takes several times as long for the same composition.
                            limited(|| {
                                run("hfst-compose", &["-1", path(prev), "-2", path(net), "-o", path(&tmp(out))])
                            })?;
                            std::fs::rename(tmp(out), out)?;
                            Txt::parse(&run("hfst-fst2txt", &[path(out)])?)?.paths()
                        })
                    })
                    .collect();
                jobs.into_iter().map(|j| j.join().expect("a shard's compose panicked")).collect::<Result<Vec<_>>>()
            })
            .map_err(|e| e.context(format!("{}: {name}", dict.name)))?;
            let mut rows = rows.concat();
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
        prev = outs;
    }
    // Last, and a new file, so the directory's mtime is when this finished.
    std::fs::write(dir.join(STAMP), stamp)?;
    Ok(())
}

/// A net as hfst-fst2txt lists it. A symbol is as the listing writes it, so a
/// listing of part of the net reads back as that part.
#[derive(Default)]
struct Txt<'a> {
    states: Vec<State<'a>>,
}

#[derive(Default)]
struct State<'a> {
    /// Each arc's target, and the rest of its line: its symbols and weight.
    arcs: Vec<(usize, &'a str)>,
    /// The rest of the line that makes the state final.
    last: Option<&'a str>,
}

impl<'a> Txt<'a> {
    fn parse(txt: &'a str) -> Result<Self> {
        let mut net = Txt::default();
        for line in txt.lines() {
            let mut fields = line.splitn(3, '\t');
            let state: usize = fields.next().unwrap_or_default().parse()?;
            let (arc, last) = match (fields.next(), fields.next()) {
                (Some(to), Some(rest)) => (Some((to.parse()?, rest)), None),
                (weight, _) => (None, Some(weight.unwrap_or(""))),
            };
            let top = arc.map_or(state, |(to, _)| state.max(to));
            if net.states.len() <= top {
                net.states.resize_with(top + 1, Default::default);
            }
            match arc {
                Some(arc) => net.states[state].arcs.push(arc),
                None => net.states[state].last = last,
            }
        }
        Ok(net)
    }

    /// Each path, as `upper<TAB>lower`. hfst-fst2strings is forty times slower
    /// on a pipeline's prefix.
    fn paths(&self) -> Result<Vec<String>> {
        let symbol = |s| match s {
            "@0@" | "@_EPSILON_SYMBOL_@" => "",
            "@_SPACE_@" => " ",
            "@_TAB_@" => "\t",
            s => s,
        };
        let mut rows = Vec::new();
        if self.states.is_empty() {
            return Ok(rows);
        }
        // Depth-first. A frame is a state, the next arc to take from it, and the
        // lengths of the strings that reach it.
        let (mut upper, mut lower) = (String::new(), String::new());
        let mut stack = vec![(0, 0, 0, 0)];
        while let Some((state, next, u, l)) = stack.last_mut() {
            upper.truncate(*u);
            lower.truncate(*l);
            let State { arcs, last } = &self.states[*state];
            if *next == 0 && last.is_some() {
                rows.push(format!("{upper}\t{lower}"));
            }
            let Some(&(to, rest)) = arcs.get(*next) else {
                stack.pop();
                continue;
            };
            *next += 1;
            let mut symbols = rest.split('\t');
            upper.push_str(symbol(symbols.next().unwrap_or_default()));
            lower.push_str(symbol(symbols.next().unwrap_or_default()));
            if stack.len() > self.states.len() {
                bail!("the net has a cycle");
            }
            stack.push((to, 0, upper.len(), lower.len()));
        }
        Ok(rows)
    }

    /// The net as at most `n` nets whose union it is, each a listing, with
    /// about as many paths each: the start state's arcs, dealt out.
    fn shards(&self, n: usize) -> Result<Vec<String>> {
        if self.states.is_empty() {
            return Ok(vec![String::new()]);
        }
        let counts = self.counts()?;
        let mut arcs: Vec<_> = self.states[0].arcs.iter().collect();
        arcs.sort_by_key(|(to, _)| std::cmp::Reverse(counts[*to]));
        let mut bins: Vec<(u64, Vec<&(usize, &str)>)> = vec![(0, Vec::new()); n.min(arcs.len()).max(1)];
        for arc in arcs {
            let bin = bins.iter_mut().min_by_key(|(paths, _)| *paths).expect("a bin");
            bin.0 += counts[arc.0];
            bin.1.push(arc);
        }
        Ok(bins.into_iter().enumerate().map(|(i, (_, arcs))| self.listing(&arcs, i == 0)).collect())
    }

    /// How many paths leave each state.
    fn counts(&self) -> Result<Vec<u64>> {
        let mut counts = vec![None; self.states.len()];
        let mut open = vec![false; self.states.len()];
        // A state is counted after every state it reaches.
        let mut stack = vec![(0, false)];
        while let Some((state, reached)) = stack.pop() {
            if reached {
                open[state] = false;
                let s = &self.states[state];
                counts[state] = Some(s.arcs.iter().fold(s.last.is_some() as u64, |n, (to, _)| {
                    n.saturating_add(counts[*to].expect("counted"))
                }));
            } else if open[state] {
                bail!("the net has a cycle");
            } else if counts[state].is_none() {
                open[state] = true;
                stack.push((state, true));
                stack.extend(self.states[state].arcs.iter().map(|(to, _)| (*to, false)));
            }
        }
        Ok(counts.into_iter().map(Option::unwrap_or_default).collect())
    }

    /// The listing of the part of the net that starts with `arcs`, renumbered
    /// so it holds only the states they reach.
    fn listing(&self, arcs: &[&(usize, &str)], last: bool) -> String {
        let mut number = HashMap::from([(0, 0)]);
        let mut queue = Vec::new();
        let mut visit = |to: usize, queue: &mut Vec<usize>| {
            let next = number.len();
            *number.entry(to).or_insert_with(|| {
                queue.push(to);
                next
            })
        };
        let mut out = String::new();
        for (to, rest) in arcs {
            out.push_str(&format!("0\t{}\t{rest}\n", visit(*to, &mut queue)));
        }
        if let (true, Some(weight)) = (last, self.states[0].last) {
            out.push_str(&final_line(0, weight));
        }
        let mut i = 0;
        while let Some(&state) = queue.get(i) {
            i += 1;
            let from = visit(state, &mut queue);
            for (to, rest) in &self.states[state].arcs {
                out.push_str(&format!("{from}\t{}\t{rest}\n", visit(*to, &mut queue)));
            }
            if let Some(weight) = self.states[state].last {
                out.push_str(&final_line(from, weight));
            }
        }
        out
    }
}

fn final_line(state: usize, weight: &str) -> String {
    match weight {
        "" => format!("{state}\n"),
        _ => format!("{state}\t{weight}\n"),
    }
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

/// The shards of `net`, cached as `PREFIX.I.hfst` beside `PREFIX.count`.
fn shards(net: &Path, prefix: &Path, force: bool) -> Result<Vec<PathBuf>> {
    let count = prefix.with_extension("count");
    let at = |i: usize| prefix.with_extension(format!("{i}.hfst"));
    if !force && used(&count) {
        let n: usize = std::fs::read_to_string(&count)?.trim().parse()?;
        if (0..n).all(|i| used(&at(i))) {
            return Ok((0..n).map(at).collect());
        }
    }
    let listings = Txt::parse(&run("hfst-fst2txt", &[path(net)])?)?.shards(SHARDS as usize)?;
    for (i, listing) in listings.iter().enumerate() {
        let file = tmp(&at(i)).with_extension("txt");
        std::fs::write(&file, listing)?;
        run("hfst-txt2fst", &["-i", path(&file), "-o", path(&tmp(&at(i)))])?;
        let _ = std::fs::remove_file(&file);
        std::fs::rename(tmp(&at(i)), at(i))?;
    }
    std::fs::write(&count, listings.len().to_string())?;
    Ok((0..listings.len()).map(at).collect())
}

/// Run `f` when fewer HFST processes than cores are running.
fn limited<T>(f: impl FnOnce() -> T) -> T {
    static RUNNING: Mutex<usize> = Mutex::new(0);
    static FREED: Condvar = Condvar::new();
    let cores = std::thread::available_parallelism().map_or(4, |n| n.get());
    *FREED.wait_while(RUNNING.lock().unwrap(), |n| *n >= cores).unwrap() += 1;
    let out = f();
    *RUNNING.lock().unwrap() -= 1;
    FREED.notify_one();
    out
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
