use std::path::PathBuf;
use std::time::Instant;

use anyhow::{bail, Context, Result};
use clap::Parser;
use indexmap::IndexMap;
use stfst_core::Stroke;
use stfst_layout::design::{parse_chord, Design};
use stfst_layout::engine::{Problem, State};
use stfst_layout::resolve::Resolve;
use stfst_layout::{report, search, words};

/// Search a design's free chords and score the result on its words.
#[derive(Parser)]
#[command(name = "stfst-layout")]
struct Cli {
    design: PathBuf,
    /// Words file from `scripts/layout-units.py`; by default the design's
    /// `[words]` and `[encode]`, encoded and cached under build/layout.
    #[arg(long)]
    units: Option<PathBuf>,
    /// Start from the chords of an earlier result (or of any JSON with a
    /// `chords` map from atom to outline).
    #[arg(long)]
    warm: Option<PathBuf>,
    /// Score the start chords without searching.
    #[arg(long)]
    eval: bool,
    /// Score a words file with alternatives by hard collision among its first
    /// outlines, as if nothing else were in the stack.
    #[arg(long)]
    hard: bool,
    #[arg(long)]
    method: Option<String>,
    #[arg(long)]
    moves: Option<u64>,
    #[arg(long)]
    seeds: Option<u32>,
    #[arg(long)]
    seed: Option<u64>,
    #[arg(long)]
    t0: Option<f64>,
    /// Where to write the result; build/layout/DESIGN.json by default.
    #[arg(short, long)]
    out: Option<PathBuf>,
    #[arg(long, default_value_t = 15)]
    worst: usize,
}

fn main() -> Result<()> {
    let cli = Cli::parse();
    let mut design = Design::load(&cli.design)?;
    let s = &mut design.search;
    s.method = cli.method.clone().unwrap_or(s.method.clone());
    s.moves = cli.moves.unwrap_or(s.moves);
    s.seeds = cli.seeds.unwrap_or(s.seeds);
    s.seed = cli.seed.unwrap_or(s.seed);
    s.t0 = cli.t0.unwrap_or(s.t0);
    if !s.method.split('+').all(|m| m == "anneal" || m == "descent") {
        bail!("method {:?}: expected anneal, descent or anneal+descent", s.method);
    }

    let t = Instant::now();
    let words = match &cli.units {
        Some(path) => words::read(path)?,
        None => words::encode(&design)?,
    };
    if !cli.hard && words.words.iter().any(|w| !w.alts.is_empty()) {
        return run_resolve(&cli, &design, &words);
    }
    let p = Problem::new(&design, &words.words)?;
    let fanout: usize = (0..p.genes.len()).map(|g| p.holders(g).len()).sum();
    println!(
        "{}: {} words as {} units, {} free atoms, {} fixed; {:.0} units per free atom ({:.1}s)",
        design.name(),
        words.words.len(),
        p.units(),
        p.genes.len(),
        p.fixed.len(),
        fanout as f64 / p.genes.len().max(1) as f64,
        t.elapsed().as_secs_f64()
    );

    let warm = match &cli.warm {
        Some(path) => warm_chords(&design, &p, path)?,
        None => vec![None; p.genes.len()],
    };
    let t = Instant::now();
    let outcomes = if cli.eval {
        let missing = warm.iter().filter(|c| c.is_none()).count();
        if missing > 0 {
            println!("  {missing} free atoms have no start chord and take a random key");
        }
        let chords = search::start(&p, &warm, design.search.seed);
        let cost = State::new(&p, chords.clone()).cost();
        vec![search::Outcome { seed: design.search.seed, chords, cost, stats: Default::default() }]
    } else {
        search::run(&p, &warm, &design.search)
    };
    let wall = t.elapsed().as_secs_f64();
    let best = &outcomes[0];
    let moves: u64 = outcomes.iter().map(|o| o.stats.moves).sum();
    let scored: u64 = outcomes.iter().map(|o| o.stats.scored).sum();
    let touched: u64 = outcomes.iter().map(|o| o.stats.touched).sum();
    if !cli.eval {
        let costs: Vec<String> = outcomes.iter().map(|o| format!("{}:{:.6}", o.seed, o.cost)).collect();
        println!(
            "  {} in {wall:.1}s: {moves} moves ({:.0}/s, {:.0} us each, {:.0} units rewritten each), {scored} descent candidates; cost by seed {}",
            design.search.method,
            moves as f64 / wall,
            1e6 * wall * rayon::current_num_threads().min(outcomes.len()) as f64 / moves.max(1) as f64,
            touched as f64 / moves.max(1) as f64,
            costs.join(" ")
        );
    }
    let st = State::new(&p, best.chords.clone());
    let rep = report::report(&st, &words.words, cli.worst, parse_chord(&design.system, "*")?);
    println!("{}", rep.summary());

    let chords: IndexMap<&str, String> =
        p.genes.iter().zip(&best.chords).map(|(g, &c)| (g.as_str(), design.format(c))).collect();
    let fixed: IndexMap<&str, String> = p.fixed.iter().map(|(a, &c)| (a.as_str(), design.format(c))).collect();
    let result = serde_json::json!({
        "design": design.path,
        "units": words.path,
        "seed": best.seed,
        "method": if cli.eval { "eval" } else { design.search.method.as_str() },
        "seconds": wall,
        "moves": moves,
        "report": rep,
        "chords": chords,
        "fixed": fixed,
        "meta": words.meta,
    });
    let out =
        cli.out.unwrap_or_else(|| words::workspace_root().join("build/layout").join(format!("{}.json", design.name())));
    if let Some(dir) = out.parent() {
        std::fs::create_dir_all(dir)?;
    }
    std::fs::write(&out, serde_json::to_string_pretty(&result)?)?;
    println!("  wrote {}", out.display());
    Ok(())
}

/// A words file with alternatives is scored as stfst's `resolve` scores it.
fn run_resolve(cli: &Cli, design: &Design, words: &words::Words) -> Result<()> {
    let r = Resolve::new(design, words)?;
    println!(
        "{}: {} words, {} held outlines, {} free atoms (stfst resolve)",
        design.name(),
        words.words.len(),
        words.held.len(),
        r.genes.len()
    );
    let warm = match &cli.warm {
        Some(path) => warm_map(design, path)?,
        None => IndexMap::new(),
    };
    let mut rng = fastrand::Rng::with_seed(design.search.seed);
    let start: Vec<Stroke> = (0..r.genes.len())
        .map(|g| {
            let keys: Vec<usize> = r.zone[g].indices().collect();
            let pick = || match &r.choices[g] {
                Some(c) => c[0],
                None => Stroke::EMPTY.with(keys[rng.usize(..keys.len())]),
            };
            warm.get(&r.genes[g]).copied().unwrap_or_else(pick)
        })
        .collect();
    let t = Instant::now();
    r.run(&start);
    let per_eval = t.elapsed().as_secs_f64();
    let t = Instant::now();
    let (chords, evals) = if cli.eval || r.genes.is_empty() { (start, 0) } else { r.descent(start) };
    let wall = t.elapsed().as_secs_f64();
    let out = r.run(&chords);
    let s = &out.score;
    println!("  one evaluation {:.1} ms; descent {evals} evaluations in {wall:.1}s", 1e3 * per_eval);
    println!(
        "  lost {:.3}% of frequency: {} words lose to another word ({} of zipf > 4), {} leave theirs to another dictionary, {} have no outline; {} placed, {:.3} strokes each, coverage {:.1}",
        s.loss, s.lost, s.lost_common, s.left, s.unwritten, s.placed, s.strokes_per_word, s.coverage
    );
    let worst: Vec<String> = out
        .losers
        .iter()
        .take(cli.worst)
        .map(|(w, win, o)| format!("{}~{}={}", r.name(*w), win, design.format_outline(o)))
        .collect();
    println!("  first losers: {}", worst.join(" "));
    for (f, sh) in &s.fallback {
        println!("  on the {f} fallback: {} words, {:.3}% of frequency", sh.words, sh.share);
    }
    let chords_out: IndexMap<&str, String> =
        r.genes.iter().zip(&chords).map(|(g, &c)| (g.as_str(), design.format(c))).collect();
    if !chords_out.is_empty() {
        println!("  chords: {}", chords_out.iter().map(|(a, c)| format!("{a}={c}")).collect::<Vec<_>>().join(" "));
    }
    let losers: Vec<_> =
        out.losers.iter().map(|(w, win, o)| serde_json::json!([r.name(*w), win, design.format_outline(o)])).collect();
    let fallbacks: Vec<_> =
        out.fallbacks.iter().map(|(w, f, o)| serde_json::json!([r.name(*w), f, design.format_outline(o)])).collect();
    let result = serde_json::json!({
        "design": design.path, "units": words.path, "seconds": wall, "evaluations": evals,
        "score": s, "chords": chords_out, "losers": losers, "fallbacks": fallbacks, "meta": words.meta,
    });
    let path = cli
        .out
        .clone()
        .unwrap_or_else(|| words::workspace_root().join("build/layout").join(format!("{}.json", design.name())));
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)?;
    }
    std::fs::write(&path, serde_json::to_string_pretty(&result)?)?;
    println!("  wrote {}", path.display());
    Ok(())
}

fn warm_map(design: &Design, path: &PathBuf) -> Result<IndexMap<String, Stroke>> {
    let v: serde_json::Value =
        serde_json::from_str(&std::fs::read_to_string(path).with_context(|| format!("reading {}", path.display()))?)?;
    let mut map = IndexMap::new();
    for key in ["fixed", "chords"] {
        if let Some(m) = v.get(key).and_then(|m| m.as_object()) {
            for (atom, c) in m {
                let c = c.as_str().with_context(|| format!("{atom} is not an outline"))?;
                map.insert(atom.clone(), parse_chord(&design.system, c)?);
            }
        }
    }
    Ok(map)
}

/// Each free atom's chord in the file's `chords` or `fixed` map, within its zone.
fn warm_chords(design: &Design, p: &Problem, path: &PathBuf) -> Result<Vec<Option<Stroke>>> {
    let map = warm_map(design, path)?;
    let out: Vec<Option<Stroke>> =
        p.genes.iter().enumerate().map(|(g, a)| map.get(a).map(|&c| c.minus(c.minus(p.zone[g])))).collect();
    let outside =
        p.genes.iter().enumerate().filter(|&(g, a)| map.get(a).is_some_and(|&c| !p.zone[g].covers(c))).count();
    if outside > 0 {
        println!("  {outside} warm chords reach outside their zone and were cut to it");
    }
    Ok(out)
}
