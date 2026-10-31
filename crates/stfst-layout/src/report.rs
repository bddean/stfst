//! Scoring a finished layout word by word, as the handoff's `fv2.py` reports it.

use indexmap::IndexMap;
use rustc_hash::{FxHashMap, FxHashSet};
use serde::Serialize;
use stfst_core::Stroke;

use crate::engine::{Chords, State};
use crate::words::Word;

#[derive(Debug, Serialize)]
pub struct Report {
    /// Lost frequency share, in percent, by `all`, `N syl` and word kind.
    pub loss: IndexMap<String, f64>,
    /// Lost share, in percent, once each loser in turn takes its outline with
    /// `*` added to one stroke, last stroke first, where no word holds it.
    pub loss_star: f64,
    pub keys_per_stroke: f64,
    /// fv2.py's figure, which counts a key held in both strokes once.
    pub keys_per_stroke_fv2: f64,
    pub strokes_per_word: f64,
    pub strokes_per_long_word: f64,
    pub cost: f64,
    /// Collisions by lost frequency: the words, most frequent first.
    pub worst: Vec<Vec<String>>,
    /// `onset` and `coda`: share of cluster frequency whose consonants' chords
    /// are in steno order, that a fused chord writes instead, and neither, in
    /// percent.
    pub clusters: IndexMap<&'static str, [f64; 3]>,
    /// The commonest clusters out of order, with their share of their side.
    pub disordered: Vec<(String, f64)>,
}

pub fn report(st: &State, words: &[Word], worst: usize, star: Stroke) -> Report {
    let p = st.p;
    let outline = |k: usize| -> &Chords { st.outline_of(p.word_unit[k] as usize) };
    let mut groups: FxHashMap<&Chords, Vec<usize>> = FxHashMap::default();
    for k in 0..words.len() {
        groups.entry(outline(k)).or_default().push(k);
    }
    let mut tot: IndexMap<String, f64> = IndexMap::new();
    let mut lost: IndexMap<String, f64> = IndexMap::new();
    for key in ["all", "1 syl", "2 syl", "3 syl", "4 syl", "root", "inflected", "derived"] {
        tot.insert(key.into(), 0.0);
        lost.insert(key.into(), 0.0);
    }
    let mut collisions: Vec<(f64, Vec<String>)> = Vec::new();
    for g in groups.values_mut() {
        g.sort_by(|&a, &b| words[b].f.total_cmp(&words[a].f));
        for (n, &k) in g.iter().enumerate() {
            let w = &words[k];
            for cat in ["all".to_string(), format!("{} syl", w.nsyl), w.kind.clone()] {
                *tot.entry(cat.clone()).or_default() += w.f;
                if n > 0 {
                    *lost.entry(cat).or_default() += w.f;
                }
            }
        }
        if g.len() > 1 {
            let l: f64 = g[1..].iter().map(|&k| words[k].f).sum();
            collisions.push((l, g.iter().take(3).map(|&k| words[k].w.clone()).collect()));
        }
    }
    collisions.sort_by(|a, b| b.0.total_cmp(&a.0));

    let mut losers: Vec<usize> = groups.values().flat_map(|g| g[1..].iter().copied()).collect();
    losers.sort_by(|&a, &b| words[b].f.total_cmp(&words[a].f));
    let mut taken: FxHashSet<Chords> = groups.keys().map(|&&o| o).collect();
    let mut rescued = 0.0;
    for k in losers {
        let o = *outline(k);
        let starred = (0..words[k].strokes.len()).rev().filter(|&s| !o[s].covers(star)).map(|s| {
            let mut t = o;
            t[s] = t[s].union(star);
            t
        });
        if let Some(t) = starred.into_iter().find(|t| !taken.contains(t)) {
            taken.insert(t);
            rescued += words[k].f;
        }
    }

    let side = |c: &crate::engine::Cluster| if c.atoms[0].starts_with("L:") { "onset" } else { "coda" };
    let mut clusters: IndexMap<&'static str, [f64; 3]> = IndexMap::new();
    let mut disordered: Vec<(String, f64)> = Vec::new();
    for (c, &ok) in p.clusters.iter().zip(st.ordered()) {
        let kind = if ok { 0 } else if c.fused { 1 } else { 2 };
        clusters.entry(side(c)).or_default()[kind] += c.f;
        if kind == 2 {
            let name = c.atoms.iter().map(|a| a.split_once(':').map_or(a.as_str(), |x| x.1)).collect::<Vec<_>>();
            disordered.push((format!("{} {}", side(c), name.join(" ")), c.f));
        }
    }
    for (name, f) in &mut disordered {
        *f *= 100.0 / clusters[name.split(' ').next().unwrap()].iter().sum::<f64>();
    }
    disordered.sort_by(|a, b| b.1.total_cmp(&a.1));
    disordered.truncate(worst);
    for shares in clusters.values_mut() {
        let t: f64 = shares.iter().sum();
        shares.iter_mut().for_each(|x| *x *= 100.0 / t);
    }
    let loss = tot
        .iter()
        .map(|(k, &t)| (k.clone(), if t > 0.0 { 100.0 * lost.get(k).copied().unwrap_or(0.0) / t } else { 0.0 }))
        .collect();

    let (mut f, mut strokes, mut keys, mut keys_fv2, mut long_f, mut long_strokes) = (0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    for (k, w) in words.iter().enumerate() {
        let o = outline(k);
        let n = w.strokes.len() as f64;
        f += w.f;
        strokes += w.f * n;
        keys += w.f * o.iter().map(|&s| p.keys_weight(s)).sum::<f64>();
        let union = o.iter().fold(stfst_core::Stroke::EMPTY, |a, &s| a.union(s));
        keys_fv2 += w.f * p.keys_weight(union);
        if w.nsyl >= 3 {
            long_f += w.f;
            long_strokes += w.f * n;
        }
    }
    Report {
        loss_star: (100.0 * (lost["all"] - rescued) / tot["all"]).max(0.0),
        loss,
        keys_per_stroke: keys / strokes,
        keys_per_stroke_fv2: keys_fv2 / strokes,
        strokes_per_word: strokes / f,
        strokes_per_long_word: if long_f > 0.0 { long_strokes / long_f } else { 0.0 },
        cost: st.cost(),
        worst: collisions.into_iter().take(worst).map(|c| c.1).collect(),
        clusters,
        disordered,
    }
}

impl Report {
    pub fn summary(&self) -> String {
        let loss: Vec<String> = self.loss.iter().map(|(k, v)| format!("{k} {v:.2}%")).collect();
        let worst: Vec<String> = self.worst.iter().map(|w| w.join("/")).collect();
        let mut out = format!(
            "  {}  with *: {:.2}%\n  keys/stroke {:.2} (fv2 count {:.2}), strokes/word {:.3}, 3+ syl {:.3}, \
             cost {:.6}\n  worst: {}",
            loss.join("  "),
            self.loss_star,
            self.keys_per_stroke,
            self.keys_per_stroke_fv2,
            self.strokes_per_word,
            self.strokes_per_long_word,
            self.cost,
            worst.join("; ")
        );
        for (side, [ok, fused, bad]) in &self.clusters {
            out += &format!("\n  {side} clusters: {ok:.1}% in order, {fused:.1}% by a fused chord, {bad:.1}% neither");
        }
        if !self.disordered.is_empty() {
            let d: Vec<String> = self.disordered.iter().map(|(c, f)| format!("{c} {f:.1}%")).collect();
            out += &format!("\n  out of order: {}", d.join(", "));
        }
        out
    }
}
