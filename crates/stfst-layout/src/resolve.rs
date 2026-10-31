//! stfst's own objective: `scripts/gen/resolve.py`'s `resolve`, over words with
//! ordered alternative outlines and the outlines the rest of the stack holds.
//!
//! An outline goes to the word that wants it at the least cost, and among
//! those to the first by rank; a word takes the first outline it can. A held
//! outline is left to its dictionary unless that writes the same word, or the
//! holder is Typey Type and the word is frequent enough to displace it. A word
//! with no outline is lost. Each evaluation resolves every word again, which
//! is cheap enough for descent over the few atoms a theory leaves free.

use anyhow::{bail, Result};
use indexmap::{IndexMap, IndexSet};
use rayon::prelude::*;
use rustc_hash::FxHashMap;
use serde::Serialize;
use stfst_core::Stroke;

use crate::design::{Design, Role};
use crate::engine::{Chords, MAX_STROKES};
use crate::words::Words;

struct Alt {
    level: u32,
    base: Chords,
    inc: Vec<(u32, u8)>,
    displace: bool,
    fallback: Option<u32>,
}

struct Holder {
    word: String,
    /// A word at least this frequent displaces it.
    f: Option<f64>,
}

pub struct Resolve {
    pub genes: Vec<String>,
    pub zone: Vec<Stroke>,
    /// A gene's candidate chords, where the design lists them.
    pub choices: Vec<Option<Vec<Stroke>>>,
    pub max_keys: u32,
    names: Vec<String>,
    fallbacks: Vec<String>,
    f: Vec<f64>,
    alts: Vec<Vec<Alt>>,
    levels: u32,
    held: FxHashMap<Chords, Holder>,
    total_f: f64,
}

#[derive(Debug, Clone, Serialize)]
pub struct Score {
    /// Lost frequency share, in percent.
    pub loss: f64,
    pub lost: usize,
    pub lost_common: usize,
    /// Words whose first outline another dictionary keeps.
    pub left: usize,
    /// Words with no outline at all.
    pub unwritten: usize,
    pub placed: usize,
    /// Of placed words, frequency-weighted.
    pub strokes_per_word: f64,
    /// Sum of placed words' frequencies per million words, as two_syllable.py reports it.
    pub coverage: f64,
    /// Words placed on a fallback outline, by fallback.
    pub fallback: IndexMap<String, Share>,
}

#[derive(Debug, Clone, Default, Serialize)]
pub struct Share {
    pub words: usize,
    /// Of all words' frequency, in percent.
    pub share: f64,
}

pub struct Outcome<'a> {
    pub placed: Vec<Option<Chords>>,
    pub score: Score,
    /// (loser, winner or dictionary, outline)
    pub losers: Vec<(usize, String, Chords)>,
    /// (word, fallback, outline)
    pub fallbacks: Vec<(usize, &'a str, Chords)>,
}

fn chords_of(design: &Design, outline: &str) -> Option<Chords> {
    let o = design.system.parse_outline(outline).ok()?;
    if o.0.len() > MAX_STROKES {
        return None;
    }
    let mut c = [Stroke::EMPTY; MAX_STROKES];
    c[..o.0.len()].copy_from_slice(&o.0);
    Some(c)
}

impl Resolve {
    pub fn new(design: &Design, words: &Words) -> Result<Resolve> {
        let mut genes: IndexMap<String, Stroke> = IndexMap::new();
        let mut fallbacks: IndexSet<String> = IndexSet::new();
        let mut missing = Vec::new();
        let mut alts = Vec::new();
        let mut costs: Vec<f64> = words.words.iter().flat_map(|w| w.alts.iter().map(|a| a.cost)).collect();
        costs.sort_by(f64::total_cmp);
        costs.dedup();
        for w in &words.words {
            let mut mine = Vec::new();
            for a in &w.alts {
                let mut base = [Stroke::EMPTY; MAX_STROKES];
                let mut inc = Vec::new();
                for (s, atoms) in a.strokes.iter().enumerate() {
                    for atom in atoms {
                        match design.role(atom) {
                            Some(Role::Fixed(c)) => base[s] = base[s].union(c),
                            Some(Role::Free(z)) => {
                                let (g, _) = genes.insert_full(atom.clone(), z);
                                inc.push((g as u32, s as u8));
                            }
                            None => missing.push(atom.clone()),
                        }
                    }
                }
                let level = costs.iter().position(|&c| c == a.cost).unwrap() as u32;
                let fallback = a.fallback.as_ref().map(|f| fallbacks.insert_full(f.clone()).0 as u32);
                mine.push(Alt { level, base, inc, displace: a.displace, fallback });
            }
            alts.push(mine);
        }
        if !missing.is_empty() {
            missing.sort();
            missing.dedup();
            bail!("{} atoms have no chord and no zone: {}", missing.len(), missing[..missing.len().min(30)].join(", "));
        }
        let held = words
            .held
            .iter()
            .filter_map(|h| Some((chords_of(design, &h.held)?, Holder { word: h.word.clone(), f: h.f })))
            .collect();
        Ok(Resolve {
            choices: genes.keys().map(|g| design.choices(g).map(<[Stroke]>::to_vec)).collect(),
            genes: genes.keys().cloned().collect(),
            zone: genes.values().copied().collect(),
            fallbacks: fallbacks.into_iter().collect(),
            max_keys: design.objective.max_keys,
            names: words.words.iter().map(|w| w.w.clone()).collect(),
            f: words.words.iter().map(|w| w.f).collect(),
            total_f: words.words.iter().map(|w| w.f).sum(),
            levels: costs.len() as u32,
            alts,
            held,
        })
    }

    fn outline(alt: &Alt, chords: &[Stroke]) -> Chords {
        let mut o = alt.base;
        for &(g, s) in &alt.inc {
            o[s as usize] = o[s as usize].union(chords[g as usize]);
        }
        o
    }

    fn holds(&self, o: &Chords, w: usize, alt: &Alt) -> bool {
        match self.held.get(o) {
            None => false,
            Some(h) if h.word == self.names[w] => false,
            Some(h) => !(alt.displace && h.f.is_some_and(|f| self.f[w] >= f)),
        }
    }

    /// The words in rank order (the units file's order).
    pub fn run(&self, chords: &[Stroke]) -> Outcome<'_> {
        let n = self.f.len();
        let mut out: FxHashMap<Chords, u32> = FxHashMap::default();
        let mut placed: Vec<Option<Chords>> = vec![None; n];
        let mut by: Vec<Option<u32>> = vec![None; n];
        for level in 0..self.levels {
            for w in 0..n {
                if placed[w].is_some() {
                    continue;
                }
                for alt in self.alts[w].iter().filter(|a| a.level == level) {
                    let o = Self::outline(alt, chords);
                    if !out.contains_key(&o) && !self.holds(&o, w, alt) {
                        out.insert(o, w as u32);
                        placed[w] = Some(o);
                        by[w] = alt.fallback;
                        break;
                    }
                }
            }
        }
        let mut score = Score {
            loss: 0.0,
            lost: 0,
            lost_common: 0,
            left: 0,
            unwritten: 0,
            placed: 0,
            strokes_per_word: 0.0,
            coverage: 0.0,
            fallback: self.fallbacks.iter().map(|f| (f.clone(), Share::default())).collect(),
        };
        let (mut losers, mut fallbacks) = (Vec::new(), Vec::new());
        let (mut lost_f, mut placed_f) = (0.0, 0.0);
        for w in 0..n {
            if let Some(o) = placed[w] {
                score.placed += 1;
                score.coverage += self.f[w] * 1e3;
                placed_f += self.f[w];
                score.strokes_per_word += self.f[w] * o.iter().filter(|s| !s.is_empty()).count() as f64;
                if let Some(k) = by[w] {
                    let s = &mut score.fallback[k as usize];
                    s.words += 1;
                    s.share += 100.0 * self.f[w] / self.total_f;
                    fallbacks.push((w, self.fallbacks[k as usize].as_str(), o));
                }
                continue;
            }
            lost_f += self.f[w];
            let Some(first) = self.alts[w].first() else {
                score.unwritten += 1;
                continue;
            };
            let o = Self::outline(first, chords);
            if let Some(&winner) = out.get(&o) {
                score.lost += 1;
                if self.f[w] > 1e-5 {
                    score.lost_common += 1;
                }
                losers.push((w, self.names[winner as usize].clone(), o));
            } else {
                score.left += 1;
                losers.push((w, format!("({})", self.held.get(&o).map_or("", |h| h.word.as_str())), o));
            }
        }
        score.loss = 100.0 * lost_f / self.total_f;
        score.strokes_per_word /= placed_f.max(f64::MIN_POSITIVE);
        Outcome { placed, score, losers, fallbacks }
    }

    pub fn name(&self, w: usize) -> &str {
        &self.names[w]
    }

    fn cost(&self, chords: &[Stroke]) -> f64 {
        self.run(chords).score.loss
    }

    fn allows(&self, g: usize, c: Stroke) -> bool {
        !c.is_empty() && c.len() <= self.max_keys && self.zone[g].covers(c)
    }

    /// Coordinate descent: each free atom in turn takes its best chord, every
    /// chord of its zone scored in parallel, until a sweep improves nothing.
    /// Returns the chords and the number of evaluations.
    pub fn descent(&self, mut chords: Vec<Stroke>) -> (Vec<Stroke>, u64) {
        let mut evals = 0;
        let mut cost = self.cost(&chords);
        loop {
            let mut improved = false;
            for g in 0..self.genes.len() {
                let mut cands = match &self.choices[g] {
                    Some(c) => c.clone(),
                    None => {
                        let keys: Vec<usize> = self.zone[g].indices().collect();
                        let mut cands = Vec::new();
                        crate::search::subsets(&keys, self.max_keys as usize, Stroke::EMPTY, &mut cands);
                        cands.retain(|&c| self.allows(g, c));
                        cands
                    }
                };
                cands.retain(|&c| c != chords[g]);
                evals += cands.len() as u64;
                let best = cands
                    .par_iter()
                    .map(|&c| {
                        let mut trial = chords.clone();
                        trial[g] = c;
                        (self.cost(&trial), c)
                    })
                    .min_by(|a, b| a.0.total_cmp(&b.0));
                if let Some((c_cost, c)) = best {
                    if c_cost < cost - 1e-12 {
                        cost = c_cost;
                        chords[g] = c;
                        improved = true;
                    }
                }
            }
            if !improved {
                return (chords, evals);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::words::{Alt as WAlt, Held, Word};

    const SYSTEM: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../systems/stenotype-extended.toml");

    fn word(w: &str, f: f64, alts: &[(&str, f64)]) -> Word {
        let alts: Vec<WAlt> = alts
            .iter()
            .map(|&(a, cost)| WAlt {
                cost,
                strokes: vec![a.split(' ').map(String::from).collect()],
                displace: true,
                fallback: None,
            })
            .collect();
        let strokes = alts[0].strokes.clone();
        Word { w: w.into(), f, nsyl: 2, kind: "two-syllable".into(), strokes, alts, clusters: Vec::new() }
    }

    #[test]
    fn rank_then_cost_then_the_stack_decide() {
        let dir = std::env::temp_dir().join(format!("stfst-layout-resolve-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("d.toml");
        std::fs::write(&path, format!("system = {SYSTEM:?}\n[zones]\nX = \"^+\"\n")).unwrap();
        let design = Design::load(&path).unwrap();
        let mut words = Words {
            path: path.clone(),
            meta: serde_json::Value::Null,
            words: vec![
                // ranked first, so it keeps PHART over the cheaper-ranked word
                word("mart", 2e-6, &[("=PHART", 0.0)]),
                word("matter", 2e-4, &[("=PHART", 0.0), ("=PHART X:^", 1.0)]),
                // Typey Type's HAP gives way to a word this frequent
                word("happy", 2e-4, &[("=HAP X:EU", 0.0)]),
                word("lost", 1e-6, &[("=HRO*S", 0.0)]),
            ],
            held: vec![
                Held { held: "^HAP".into(), by: "typey-type".into(), word: "hap".into(), f: Some(1e-5) },
                Held { held: "HRO*S".into(), by: "user".into(), word: "loss".into(), f: None },
            ],
        };
        words.words[1].alts[1].fallback = Some("theory".into());
        let r = Resolve::new(&design, &words).unwrap();
        let x = |s: &str| crate::design::parse_chord(&design.system, s).unwrap();
        let out = r.run(&[x("^"), x("^")]);
        let show = |w: usize| out.placed[w].map(|o| design.format(o[0]));
        assert_eq!(show(0).as_deref(), Some("PHART"));
        assert_eq!(show(1).as_deref(), Some("^PHART"));
        assert_eq!(show(2).as_deref(), Some("^HAP"));
        assert_eq!(show(3), None);
        assert_eq!((out.score.lost, out.score.left, out.score.unwritten), (0, 1, 0));
        assert_eq!(out.score.fallback["theory"].words, 1);
        assert_eq!(out.fallbacks.iter().map(|&(w, f, _)| (w, f)).collect::<Vec<_>>(), [(1, "theory")]);
    }
}
