//! Searching the free atoms' chords.

use rayon::prelude::*;
use stfst_core::Stroke;

use crate::design::Search;
use crate::engine::{Problem, Scratch, State};

#[derive(Debug, Clone, Default)]
pub struct Stats {
    pub moves: u64,
    pub accepted: u64,
    /// Units rewritten by the moves scored.
    pub touched: u64,
    /// Candidate chords scored by descent.
    pub scored: u64,
    pub sweeps: u32,
}

fn toggle(c: Stroke, k: usize) -> Stroke {
    if c.contains(k) {
        c.minus(Stroke::EMPTY.with(k))
    } else {
        c.with(k)
    }
}

/// Random single keys within each zone, or `warm` where it gives a chord. A
/// gene with rivals takes the fewest keys no rival has yet.
pub fn start(p: &Problem, warm: &[Option<Stroke>], seed: u64) -> Vec<Stroke> {
    let mut rng = fastrand::Rng::with_seed(seed);
    let mut chords: Vec<Stroke> = warm.iter().map(|c| c.unwrap_or(Stroke::EMPTY)).collect();
    for g in 0..p.genes.len() {
        if !chords[g].is_empty() {
            continue;
        }
        let keys: Vec<usize> = p.zone[g].indices().collect();
        chords[g] = Stroke::EMPTY.with(keys[rng.usize(..keys.len())]);
        if !p.allows(g, chords[g], &chords) {
            let mut all = Vec::new();
            subsets(&keys, p.max_keys as usize, Stroke::EMPTY, &mut all);
            rng.shuffle(&mut all);
            all.sort_by_key(|c| c.len());
            chords[g] = all.into_iter().find(|&c| p.allows(g, c, &chords)).unwrap_or(chords[g]);
        }
    }
    chords
}

/// Simulated annealing over single-key toggles, the handoff's move set. Leaves
/// the state at the best chords seen.
pub fn anneal(st: &mut State, cfg: &Search, seed: u64, stats: &mut Stats) {
    let p = st.p;
    let n = p.genes.len();
    if n == 0 || cfg.moves == 0 {
        return;
    }
    let keys: Vec<Vec<usize>> = p.zone.iter().map(|z| z.indices().collect()).collect();
    let mut rng = fastrand::Rng::with_seed(seed);
    let mut sc = Scratch::default();
    let mut cost = st.cost();
    let mut best = (cost, st.chords.clone());
    let ratio = cfg.t1 / cfg.t0;
    for it in 0..cfg.moves {
        stats.moves += 1;
        let t = cfg.t0 * ratio.powf(it as f64 / cfg.moves as f64);
        let g = if rng.f64() < cfg.target {
            target(st, &mut rng).unwrap_or_else(|| rng.usize(..n))
        } else {
            rng.usize(..n)
        };
        let c = toggle(st.chords[g], keys[g][rng.usize(..keys[g].len())]);
        if !p.allows(g, c, &st.chords) {
            continue;
        }
        let d = st.delta(g, c, &mut sc);
        stats.touched += sc.moved() as u64;
        if d <= 0.0 || rng.f64() < (-d / t).exp() {
            st.apply(g, c, &sc);
            stats.accepted += 1;
            cost += d;
            if cost < best.0 - 1e-12 {
                best = (cost, st.chords.clone());
            }
        }
    }
    *st = State::new(p, best.1);
}

/// A free gene of a word that loses its outline, which its winner lacks.
fn target(st: &State, rng: &mut fastrand::Rng) -> Option<usize> {
    let p = st.p;
    for _ in 0..64 {
        let i = rng.usize(..p.units());
        let w = st.winner(i);
        if w == i {
            continue;
        }
        let own: Vec<usize> = p.genes_of(i).filter(|&g| !p.genes_of(w).any(|h| h == g)).collect();
        if !own.is_empty() {
            return Some(own[rng.usize(..own.len())]);
        }
    }
    None
}

/// Every chord a gene may take, if there are few enough; else those within two
/// keys of its current chord.
fn candidates(p: &Problem, g: usize, chords: &[Stroke], limit: usize) -> Vec<Stroke> {
    let current = chords[g];
    let keys: Vec<usize> = p.zone[g].indices().collect();
    let max = p.max_keys as usize;
    let count: usize = (1..=max.min(keys.len())).map(|k| choose(keys.len(), k)).sum();
    let mut out = Vec::new();
    if count <= limit {
        subsets(&keys, max, Stroke::EMPTY, &mut out);
    } else {
        for (a, &ka) in keys.iter().enumerate() {
            let c = toggle(current, ka);
            out.push(c);
            for &kb in &keys[a + 1..] {
                out.push(toggle(c, kb));
            }
        }
    }
    out.retain(|&c| c != current && p.allows(g, c, chords));
    out
}

fn choose(n: usize, k: usize) -> usize {
    (0..k).fold(1, |acc, i| acc * (n - i) / (i + 1))
}

pub fn subsets(keys: &[usize], room: usize, acc: Stroke, out: &mut Vec<Stroke>) {
    for (a, &k) in keys.iter().enumerate() {
        let c = acc.with(k);
        out.push(c);
        if room > 1 {
            subsets(&keys[a + 1..], room - 1, c, out);
        }
    }
}

/// Coordinate descent: give each gene in turn its best chord, scoring the
/// candidates in parallel, until a sweep improves nothing.
pub fn descent(st: &mut State, cfg: &Search, stats: &mut Stats) {
    let p = st.p;
    let mut order: Vec<usize> = (0..p.genes.len()).collect();
    order.sort_by_key(|&g| std::cmp::Reverse(p.holders(g).len()));
    let mut sc = Scratch::default();
    loop {
        stats.sweeps += 1;
        let mut improved = false;
        for &g in &order {
            let cands = candidates(p, g, &st.chords, cfg.max_candidates);
            stats.scored += cands.len() as u64;
            let shared = &*st;
            let best = cands
                .par_iter()
                .map_init(Scratch::default, |sc, &c| (shared.delta(g, c, sc), c))
                .min_by(|a, b| a.0.total_cmp(&b.0));
            if let Some((d, c)) = best {
                if d < -1e-12 {
                    st.set(g, c, &mut sc);
                    improved = true;
                }
            }
        }
        if !improved {
            break;
        }
    }
}

pub struct Outcome {
    pub seed: u64,
    pub chords: Vec<Stroke>,
    pub cost: f64,
    pub stats: Stats,
}

/// One run per seed, in parallel; the best first.
pub fn run(p: &Problem, warm: &[Option<Stroke>], cfg: &Search) -> Vec<Outcome> {
    let anneal_on = cfg.method.split('+').any(|m| m == "anneal");
    let descent_on = cfg.method.split('+').any(|m| m == "descent");
    let mut out: Vec<Outcome> = (0..cfg.seeds as u64)
        .into_par_iter()
        .map(|k| {
            let seed = cfg.seed + k;
            let mut stats = Stats::default();
            let mut st = State::new(p, start(p, warm, seed));
            if anneal_on {
                anneal(&mut st, cfg, seed, &mut stats);
            }
            if descent_on {
                descent(&mut st, cfg, &mut stats);
            }
            Outcome { seed, cost: st.cost(), chords: st.chords, stats }
        })
        .collect();
    out.sort_by(|a, b| a.cost.total_cmp(&b.cost));
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn subsets_count_matches_binomials() {
        let keys = [0, 1, 2, 3, 4, 5];
        let mut out = Vec::new();
        subsets(&keys, 4, Stroke::EMPTY, &mut out);
        assert_eq!(out.len(), 6 + 15 + 20 + 15);
        assert!(out.iter().all(|c| c.len() <= 4));
    }
}
