//! The objective and its incremental evaluation.
//!
//! A word's outline is the union, per stroke, of its atoms' chords. Words with
//! one outline form a group; the group's most frequent word keeps it and the
//! rest are lost. Cost is `(lost frequency + effort weight * effort) / total
//! frequency`, the handoff's objective, plus an order weight times the
//! frequency of clusters whose chords are out of steno order.

use anyhow::{bail, Result};
use indexmap::IndexMap;
use rustc_hash::FxHashMap;
use stfst_core::Stroke;

use crate::design::{Design, Role};
use crate::words::Word;

/// One atom of a cluster: a free gene, or a fixed chord.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Part {
    Gene(u32),
    Fixed(Stroke),
}

/// An onset or coda cluster, over all the words that hold it.
#[derive(Debug, Clone)]
pub struct Cluster {
    pub atoms: Vec<String>,
    pub parts: Vec<Part>,
    pub fused: bool,
    pub f: f64,
}

/// Each chord after the last key of the one before; a chord with no keys
/// writes nothing and is skipped.
pub fn in_order(chords: impl Iterator<Item = Stroke>) -> bool {
    let mut last = None;
    for c in chords.filter(|c| !c.is_empty()) {
        if last.is_some_and(|l| c.indices().next().unwrap() <= l) {
            return false;
        }
        last = c.indices().last();
    }
    true
}

pub const MAX_STROKES: usize = 8;
pub type Chords = [Stroke; MAX_STROKES];

/// The search problem with fixed atoms folded in.
///
/// Words whose fixed chords and free atoms agree always collide, so they are
/// one unit: `f_head` is its best word's frequency and `f_sum` all of them.
pub struct Problem {
    pub genes: Vec<String>,
    pub zone: Vec<Stroke>,
    pub fixed: IndexMap<String, Stroke>,
    pub f_head: Vec<f64>,
    pub f_sum: Vec<f64>,
    base: Vec<Chords>,
    inc_ptr: Vec<u32>,
    inc_gene: Vec<u32>,
    inc_stroke: Vec<u8>,
    g_ptr: Vec<u32>,
    g_unit: Vec<u32>,
    pub word_unit: Vec<u32>,
    pub total_f: f64,
    key_weight: Vec<f64>,
    log_effort: bool,
    pub lambda: f64,
    pub max_keys: u32,
    pub clusters: Vec<Cluster>,
    /// Gene -> the unfused clusters that hold it.
    g_clusters: Vec<Vec<u32>>,
    pub mu: f64,
    /// Gene -> the other genes whose chord it may not share.
    pub rivals: Vec<Vec<u32>>,
}

impl Problem {
    pub fn new(design: &Design, words: &[Word]) -> Result<Problem> {
        let mut genes: IndexMap<String, Stroke> = IndexMap::new();
        let mut fixed: IndexMap<String, Stroke> = IndexMap::new();
        let mut missing: IndexMap<String, usize> = IndexMap::new();
        for w in words {
            if w.strokes.len() > MAX_STROKES {
                bail!("{} has {} strokes; at most {MAX_STROKES}", w.w, w.strokes.len());
            }
            for a in w.strokes.iter().flatten() {
                match design.role(a) {
                    Some(Role::Fixed(c)) => {
                        fixed.insert(a.clone(), c);
                    }
                    Some(Role::Free(z)) => {
                        genes.insert(a.clone(), z);
                    }
                    None => *missing.entry(a.clone()).or_default() += 1,
                }
            }
        }
        if !missing.is_empty() {
            let mut m: Vec<_> = missing.into_iter().collect();
            m.sort_by(|a, b| b.1.cmp(&a.1));
            let list: Vec<String> = m.iter().take(30).map(|(a, n)| format!("{a} ({n})")).collect();
            bail!("{} atoms have no chord and no zone: {}", m.len(), list.join(", "));
        }
        genes.sort_keys();

        // unit key: fixed chords and free incidences
        type Key = (Chords, Vec<(u32, u8)>);
        let mut unit_of: FxHashMap<Key, u32> = FxHashMap::default();
        let mut units: Vec<(Key, f64, f64)> = Vec::new();
        let mut word_unit = Vec::with_capacity(words.len());
        for w in words {
            let mut base = [Stroke::EMPTY; MAX_STROKES];
            let mut inc = Vec::new();
            for (s, atoms) in w.strokes.iter().enumerate() {
                for a in atoms {
                    if let Some(&c) = fixed.get(a) {
                        base[s] = base[s].union(c);
                    } else {
                        inc.push((genes.get_index_of(a).unwrap() as u32, s as u8));
                    }
                }
            }
            inc.sort_unstable();
            inc.dedup();
            let key = (base, inc);
            let u = *unit_of.entry(key.clone()).or_insert_with(|| {
                units.push((key, 0.0, 0.0));
                (units.len() - 1) as u32
            });
            let unit = &mut units[u as usize];
            unit.1 = unit.1.max(w.f);
            unit.2 += w.f;
            word_unit.push(u);
        }

        // Most frequent first, so a group's lowest index is its winner.
        let mut order: Vec<u32> = (0..units.len() as u32).collect();
        order.sort_by(|&a, &b| units[b as usize].1.total_cmp(&units[a as usize].1));
        let mut rank = vec![0u32; units.len()];
        for (r, &u) in order.iter().enumerate() {
            rank[u as usize] = r as u32;
        }
        let word_unit = word_unit.into_iter().map(|u| rank[u as usize]).collect();

        let mut clusters: IndexMap<(Vec<String>, bool), f64> = IndexMap::new();
        for w in words {
            for c in &w.clusters {
                *clusters.entry((c.atoms.clone(), c.fused)).or_default() += w.f;
            }
        }
        let mut g_clusters = vec![Vec::new(); genes.len()];
        let clusters = clusters
            .into_iter()
            .enumerate()
            .map(|(k, ((atoms, fused), f))| {
                let parts = atoms
                    .iter()
                    .map(|a| match (genes.get_index_of(a), design.role(a)) {
                        (Some(g), _) => {
                            if !fused {
                                g_clusters[g].push(k as u32);
                            }
                            Ok(Part::Gene(g as u32))
                        }
                        (None, Some(Role::Fixed(c))) => Ok(Part::Fixed(c)),
                        _ => bail!("cluster atom {a} is in no stroke and has no fixed chord"),
                    })
                    .collect::<Result<_>>()?;
                Ok(Cluster { atoms, parts, fused, f })
            })
            .collect::<Result<Vec<_>>>()?;
        for l in &mut g_clusters {
            l.dedup();
        }
        let tag = |a: &str| a.split(':').next().unwrap_or(a).to_string();
        let rivals = (0..genes.len())
            .map(|g| {
                let t = tag(genes.get_index(g).unwrap().0);
                if !design.objective.distinct.contains(&t) {
                    return Vec::new();
                }
                let same = |h: &usize| *h != g && tag(genes.get_index(*h).unwrap().0) == t;
                (0..genes.len()).filter(same).map(|h| h as u32).collect()
            })
            .collect();

        let mut p = Problem {
            zone: genes.values().copied().collect(),
            genes: genes.keys().cloned().collect(),
            fixed: fixed.into_iter().filter(|(a, _)| !a.starts_with('=')).collect(),
            f_head: Vec::new(),
            f_sum: Vec::new(),
            base: Vec::new(),
            inc_ptr: vec![0],
            inc_gene: Vec::new(),
            inc_stroke: Vec::new(),
            g_ptr: Vec::new(),
            g_unit: Vec::new(),
            word_unit,
            total_f: words.iter().map(|w| w.f).sum(),
            key_weight: design.key_weight.clone(),
            log_effort: design.objective.log,
            lambda: design.objective.effort,
            max_keys: design.objective.max_keys,
            clusters,
            g_clusters,
            mu: design.objective.order,
            rivals,
        };
        let mut lists: Vec<Vec<u32>> = vec![Vec::new(); p.genes.len()];
        for (r, &u) in order.iter().enumerate() {
            let ((base, inc), head, sum) = &units[u as usize];
            p.f_head.push(*head);
            p.f_sum.push(*sum);
            p.base.push(*base);
            for &(g, s) in inc {
                p.inc_gene.push(g);
                p.inc_stroke.push(s);
                if lists[g as usize].last() != Some(&(r as u32)) {
                    lists[g as usize].push(r as u32);
                }
            }
            p.inc_ptr.push(p.inc_gene.len() as u32);
        }
        p.g_ptr.push(0);
        for l in lists {
            p.g_unit.extend(l);
            p.g_ptr.push(p.g_unit.len() as u32);
        }
        Ok(p)
    }

    pub fn units(&self) -> usize {
        self.f_head.len()
    }

    /// Units that hold this gene, most frequent first.
    pub fn holders(&self, g: usize) -> &[u32] {
        &self.g_unit[self.g_ptr[g] as usize..self.g_ptr[g + 1] as usize]
    }

    fn incs(&self, i: usize) -> impl Iterator<Item = (usize, usize)> + '_ {
        let r = self.inc_ptr[i] as usize..self.inc_ptr[i + 1] as usize;
        self.inc_gene[r.clone()].iter().zip(&self.inc_stroke[r]).map(|(&g, &s)| (g as usize, s as usize))
    }

    pub fn genes_of(&self, i: usize) -> impl Iterator<Item = usize> + '_ {
        self.incs(i).map(|(g, _)| g)
    }

    pub fn outline(&self, i: usize, chords: &[Stroke]) -> Chords {
        let mut o = self.base[i];
        for (g, s) in self.incs(i) {
            o[s] = o[s].union(chords[g]);
        }
        o
    }

    fn outline_with(&self, i: usize, chords: &[Stroke], gene: usize, chord: Stroke) -> Chords {
        let mut o = self.base[i];
        for (g, s) in self.incs(i) {
            o[s] = o[s].union(if g == gene { chord } else { chords[g] });
        }
        o
    }

    pub fn keys_weight(&self, s: Stroke) -> f64 {
        s.indices().map(|k| self.key_weight[k]).sum()
    }

    pub fn effort(&self, o: &Chords) -> f64 {
        o.iter()
            .filter(|s| !s.is_empty())
            .map(|&s| {
                let w = self.keys_weight(s);
                if self.log_effort {
                    w.ln_1p()
                } else {
                    w
                }
            })
            .sum()
    }

    pub fn ordered(&self, k: usize, chords: &[Stroke], gene: usize, chord: Stroke) -> bool {
        in_order(self.clusters[k].parts.iter().map(|&p| match p {
            Part::Gene(g) if g as usize == gene => chord,
            Part::Gene(g) => chords[g as usize],
            Part::Fixed(c) => c,
        }))
    }

    /// A chord this gene may take, given the others' chords.
    pub fn allows(&self, g: usize, c: Stroke, chords: &[Stroke]) -> bool {
        !c.is_empty()
            && c.len() <= self.max_keys
            && self.zone[g].covers(c)
            && self.rivals[g].iter().all(|&h| chords[h as usize] != c)
    }
}

/// Reused buffers for one evaluation.
#[derive(Default)]
pub struct Scratch {
    arrive: FxHashMap<Chords, u32>,
    moved: Vec<(u32, Chords, f64)>,
    dkept: f64,
    deffort: f64,
    flipped: Vec<u32>,
    ddisorder: f64,
}

impl Scratch {
    /// Units the last scored move would rewrite.
    pub fn moved(&self) -> usize {
        self.moved.len()
    }
}

const NONE: u32 = u32::MAX;

pub struct State<'p> {
    pub p: &'p Problem,
    pub chords: Vec<Stroke>,
    outline: Vec<Chords>,
    eff: Vec<f64>,
    gid: Vec<u32>,
    groups: Vec<Vec<u32>>,
    spare: Vec<u32>,
    index: FxHashMap<Chords, u32>,
    kept: f64,
    effort: f64,
    ordered: Vec<bool>,
    disorder: f64,
}

impl<'p> State<'p> {
    pub fn new(p: &'p Problem, chords: Vec<Stroke>) -> State<'p> {
        let n = p.units();
        let mut st = State {
            p,
            outline: Vec::with_capacity(n),
            eff: Vec::with_capacity(n),
            gid: vec![NONE; n],
            groups: Vec::new(),
            spare: Vec::new(),
            index: FxHashMap::default(),
            kept: 0.0,
            effort: 0.0,
            ordered: Vec::with_capacity(p.clusters.len()),
            disorder: 0.0,
            chords,
        };
        for (k, c) in p.clusters.iter().enumerate() {
            let ok = p.ordered(k, &st.chords, usize::MAX, Stroke::EMPTY);
            if !ok && !c.fused {
                st.disorder += c.f;
            }
            st.ordered.push(ok);
        }
        for i in 0..n {
            let o = p.outline(i, &st.chords);
            let e = p.effort(&o);
            st.effort += p.f_sum[i] * e;
            st.outline.push(o);
            st.eff.push(e);
        }
        for i in 0..n {
            st.insert(i as u32);
        }
        st
    }

    pub fn cost(&self) -> f64 {
        (self.p.total_f - self.kept + self.p.lambda * self.effort + self.p.mu * self.disorder) / self.p.total_f
    }

    /// Whether each cluster of `Problem::clusters` is written in steno order.
    pub fn ordered(&self) -> &[bool] {
        &self.ordered
    }

    pub fn loss(&self) -> f64 {
        (self.p.total_f - self.kept) / self.p.total_f
    }

    pub fn outline_of(&self, i: usize) -> &Chords {
        &self.outline[i]
    }

    /// The unit that keeps this unit's outline.
    pub fn winner(&self, i: usize) -> usize {
        self.groups[self.gid[i] as usize][0] as usize
    }

    fn insert(&mut self, i: u32) {
        let o = self.outline[i as usize];
        let f = &self.p.f_head;
        let g = match self.index.get(&o) {
            Some(&g) => g,
            None => {
                let g = self.spare.pop().unwrap_or_else(|| {
                    self.groups.push(Vec::new());
                    (self.groups.len() - 1) as u32
                });
                self.index.insert(o, g);
                g
            }
        };
        let m = &mut self.groups[g as usize];
        match m.first() {
            None => self.kept += f[i as usize],
            Some(&h) if i < h => self.kept += f[i as usize] - f[h as usize],
            _ => {}
        }
        let pos = m.binary_search(&i).unwrap_err();
        m.insert(pos, i);
        self.gid[i as usize] = g;
    }

    fn remove(&mut self, i: u32) {
        let g = self.gid[i as usize];
        let f = &self.p.f_head;
        let m = &mut self.groups[g as usize];
        let pos = m.binary_search(&i).unwrap();
        m.remove(pos);
        if pos == 0 {
            self.kept -= f[i as usize];
            if let Some(&h) = m.first() {
                self.kept += f[h as usize];
            }
        }
        if m.is_empty() {
            self.index.remove(&self.outline[i as usize]);
            self.spare.push(g);
        }
        self.gid[i as usize] = NONE;
    }

    /// The change in cost if `gene` took `chord`. Reads the state only, so
    /// candidates can be scored in parallel.
    pub fn delta(&self, gene: usize, chord: Stroke, sc: &mut Scratch) -> f64 {
        let p = self.p;
        sc.arrive.clear();
        sc.moved.clear();
        sc.deffort = 0.0;
        for &i in p.holders(gene) {
            let o = p.outline_with(i as usize, &self.chords, gene, chord);
            if o == self.outline[i as usize] {
                continue;
            }
            let e = p.effort(&o);
            sc.deffort += p.f_sum[i as usize] * (e - self.eff[i as usize]);
            sc.moved.push((i, o, e));
            sc.arrive.entry(o).or_insert(i);
        }
        for &(i, _, _) in &sc.moved {
            sc.arrive.entry(self.outline[i as usize]).or_insert(NONE);
        }
        let f = |i: u32| if i == NONE { 0.0 } else { p.f_head[i as usize] };
        let mut dkept = 0.0;
        let sc = &mut *sc;
        for (o, &arrive) in &sc.arrive {
            let (before, stays) = match self.index.get(o) {
                None => (NONE, NONE),
                Some(&g) => {
                    let m = &self.groups[g as usize];
                    // moved is in unit order, as holders are
                    let moving = |j: &u32| sc.moved.binary_search_by_key(j, |&(i, _, _)| i).is_ok();
                    let stays = m.iter().copied().find(|j| !moving(j)).unwrap_or(NONE);
                    (m[0], stays)
                }
            };
            dkept += f(arrive.min(stays)) - f(before);
        }
        sc.dkept = dkept;
        sc.flipped.clear();
        sc.ddisorder = 0.0;
        for &k in &p.g_clusters[gene] {
            if p.ordered(k as usize, &self.chords, gene, chord) != self.ordered[k as usize] {
                sc.flipped.push(k);
                let f = p.clusters[k as usize].f;
                sc.ddisorder += if self.ordered[k as usize] { f } else { -f };
            }
        }
        (p.lambda * sc.deffort + p.mu * sc.ddisorder - dkept) / p.total_f
    }

    /// Make the move that `delta` last scored into `sc`.
    pub fn apply(&mut self, gene: usize, chord: Stroke, sc: &Scratch) {
        for &(i, _, _) in &sc.moved {
            self.remove(i);
        }
        for &(i, o, e) in &sc.moved {
            self.outline[i as usize] = o;
            self.eff[i as usize] = e;
            self.insert(i);
        }
        self.effort += sc.deffort;
        for &k in &sc.flipped {
            self.ordered[k as usize] ^= true;
        }
        self.disorder += sc.ddisorder;
        self.chords[gene] = chord;
    }

    pub fn set(&mut self, gene: usize, chord: Stroke, sc: &mut Scratch) -> f64 {
        let d = self.delta(gene, chord, sc);
        self.apply(gene, chord, sc);
        d
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::design::Design;
    use crate::words::Cluster;

    const SYSTEM: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../systems/stenotype-extended.toml");

    fn design(body: &str) -> Design {
        static N: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);
        let dir = std::env::temp_dir().join(format!("stfst-layout-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join(format!("{}.toml", N.fetch_add(1, std::sync::atomic::Ordering::Relaxed)));
        std::fs::write(&path, format!("system = {SYSTEM:?}\n{body}")).unwrap();
        Design::load(&path).unwrap()
    }

    fn word(w: &str, f: f64, strokes: &[&[&str]]) -> Word {
        Word {
            w: w.into(),
            f,
            nsyl: strokes.len() as u32,
            kind: "root".into(),
            strokes: strokes.iter().map(|s| s.iter().map(|a| a.to_string()).collect()).collect(),
            alts: Vec::new(),
            clusters: Vec::new(),
        }
    }

    fn chord(d: &Design, s: &str) -> Stroke {
        crate::design::parse_chord(&d.system, s).unwrap()
    }

    #[test]
    fn identical_words_merge_and_lose_their_minor_share() {
        let d = design("[objective]\neffort = 0\n[fixed]\n\"L:T\" = \"T\"\n\"V:A\" = \"A\"\n[zones]\nW = \"-TS\"\n");
        let words = [
            word("tat", 3.0, &[&["L:T", "V:A"]]),
            word("tatt", 1.0, &[&["L:T", "V:A"]]),
            word("tata", 2.0, &[&["L:T", "V:A", "W:A"]]),
        ];
        let p = Problem::new(&d, &words).unwrap();
        assert_eq!(p.units(), 2);
        assert_eq!(p.genes, vec!["W:A".to_string()]);
        let st = State::new(&p, vec![chord(&d, "-T")]);
        assert!((st.loss() - 1.0 / 6.0).abs() < 1e-12);
    }

    #[test]
    fn delta_matches_a_rebuild_after_every_move() {
        let d = design(
            "[objective]\neffort = 0.05\nmax_keys = 2\norder = 0.3\n\
             [zones]\nL = \"STKPWHR\"\nV = \"AOEU\"\nE = \"-TSDZ\"\n",
        );
        let mut rng = fastrand::Rng::with_seed(7);
        let atoms = |t: &str, n: usize| -> Vec<String> { (0..n).map(|k| format!("{t}:{k}")).collect() };
        let (l, v, e) = (atoms("L", 6), atoms("V", 4), atoms("E", 3));
        let words: Vec<Word> = (0..300)
            .map(|k| {
                let mut s1 = vec![l[rng.usize(..6)].clone(), v[rng.usize(..4)].clone()];
                if rng.bool() {
                    s1.push(e[rng.usize(..3)].clone());
                }
                let mut strokes = vec![s1];
                if k % 3 == 0 {
                    strokes.push(vec![v[rng.usize(..4)].clone(), l[rng.usize(..6)].clone()]);
                }
                let (a, b) = (rng.usize(..6), rng.usize(..6));
                let onset = Cluster { atoms: vec![l[a].clone(), l[b].clone()], fused: false };
                strokes[0].push(l[b].clone());
                Word {
                    w: format!("w{k}"),
                    f: rng.f64() + 0.01,
                    nsyl: 1,
                    kind: "root".into(),
                    strokes,
                    alts: Vec::new(),
                    clusters: vec![onset],
                }
            })
            .collect();
        let p = Problem::new(&d, &words).unwrap();
        let start: Vec<Stroke> =
            (0..p.genes.len()).map(|g| Stroke::EMPTY.with(p.zone[g].indices().next().unwrap())).collect();
        let mut st = State::new(&p, start);
        let mut sc = Scratch::default();
        for _ in 0..400 {
            let g = rng.usize(..p.genes.len());
            let keys: Vec<usize> = p.zone[g].indices().collect();
            let mut c = Stroke::EMPTY;
            for _ in 0..rng.usize(1..=2) {
                c = c.with(keys[rng.usize(..keys.len())]);
            }
            let before = st.cost();
            let d = st.set(g, c, &mut sc);
            let fresh = State::new(&p, st.chords.clone());
            assert!((fresh.cost() - before - d).abs() < 1e-9, "delta {d} vs rebuild {}", fresh.cost() - before);
            assert!((fresh.cost() - st.cost()).abs() < 1e-9);
        }
    }

    #[test]
    fn order_is_by_key_and_skips_empty_chords() {
        let d = design("");
        let c = |s: &str| chord(&d, s);
        assert!(in_order([c("S"), c("TK"), c("R")].into_iter()));
        assert!(in_order([c("-PB"), Stroke::EMPTY, c("-D")].into_iter()));
        assert!(!in_order([c("-PL"), c("-P")].into_iter()));
        assert!(!in_order([c("-S"), c("-T")].into_iter()));
    }

    #[test]
    fn fixed_atoms_take_their_chord_and_unknown_atoms_are_reported() {
        let d = design("[fixed]\n\"L:B\" = \"PW\"\n\"R:N\" = \"-PB\"\n[zones]\nV = \"AOEU\"\n");
        let p = Problem::new(&d, &[word("ban", 1.0, &[&["L:B", "V:AE", "R:N"]])]).unwrap();
        let st = State::new(&p, vec![chord(&d, "A")]);
        assert_eq!(d.format(st.outline_of(0)[0]), "PWAPB");
        let err = Problem::new(&d, &[word("x", 1.0, &[&["Q:X"]])]).err().unwrap();
        assert!(err.to_string().contains("Q:X"));
    }
}
