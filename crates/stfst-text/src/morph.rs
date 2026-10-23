//! Joining a suffix to a word, out of compiled artifacts rather than out of code.
//!
//! `{^ing}` is the commonest directive in every real dictionary, and it is not
//! "close the gap": Plover's English system carries 38 rewrite rules over the
//! join plus a 338,882-word list to choose between the candidates they generate.
//! Thirty-eight regexes in every host is what this format exists to avoid, so
//! all three parts are artifacts a host reads:
//!
//!   `ortho.hfstol`  `word ^ suffix` to every candidate, tagged with its rule
//!   `words.hfstol`  a word to how common it is
//!   `infl.hfstol`   `word ^ mode` to an inflected form, for `{:infl:…}`
//!
//! What is left here is the choosing, which is Plover's own algorithm and about
//! fifteen lines: prefer a candidate the word list knows, among those prefer the
//! commonest, and break a tie by which rule made it. None of it is English.
//!
//! A join that makes no word completes to one where a word is near, so `idea` and
//! `cal` is `identical`. That is `join_or_complete`, which the formatter calls;
//! `join` stays Plover's own, so the two can still be held to each other.
//!
//! The two halves stay separate on purpose, and mixing them was the bug this
//! replaced. `{^s}` is orthography, so `child` and `s` make `childs`, exactly as
//! Plover says. `{:infl:ofs}` is inflection, so they make `children`. No rule can
//! know that a plural is irregular, and only the second one asks a lexicon.

use std::fmt;
use std::path::Path;

use stfst_ol::{Automaton, OlError};

/// Plover's `ORTHOGRAPHY_RULES_ALIASES`: two suffixes that are spellings of the
/// same suffix, so a rule written for `ible` answers for `able` too. The one part
/// of the join that is not an artifact, because there is no vocabulary in it.
const ALIASES: &[(&str, &str)] = &[("able", "ible"), ("ability", "ibility")];

/// Plain concatenation's place in the candidate list: after the alias rules and
/// before the direct ones, which is where Plover appends it. Rule numbers stop at
/// 38, so one number orders the whole list.
const SIMPLE: u32 = 50;

/// Plover's rule 38, the doubling rule, and the only one that copies a character
/// out of the word. `match_case` needs to know when it fired.
const DOUBLING: u32 = 38;

/// How many letters a completion may put between a word and its suffix: `ram`
/// and `ication` is `ramification`.
const COMPLETION_GAP: usize = 3;

#[derive(Debug, thiserror::Error)]
pub enum MorphError {
    #[error("reading {0}: {1}")]
    Artifact(String, OlError),
}

pub struct Morph {
    rules: Automaton,
    words: Lexicon,
    infl: Automaton,
}

/// The word list: a word to how common it is.
pub struct Lexicon(Automaton);

impl Lexicon {
    /// How common a word is, lower being commoner, or `None` for a word the list
    /// does not have.
    pub fn freq(&self, word: &str) -> Option<u32> {
        self.0.lookup(word)?.into_iter().next()?.parse().ok()
    }

    /// Every word that is `prefix`, then at most `gap` letters, then `suffix`,
    /// with how common it is.
    pub fn between(&self, prefix: &str, gap: usize, suffix: &str) -> Vec<(String, u32)> {
        let alphabet = self.0.alphabet();
        let (Some(head), Some(tail)) = (alphabet.tokenize(prefix), alphabet.tokenize(suffix))
        else {
            return Vec::new();
        };
        let mut start = self.0.cursor();
        for &sym in &head {
            start.step(sym);
        }
        let mut out = Vec::new();
        for (read, mut cursor) in start.within(gap) {
            let fill = alphabet.render(&read);
            if !fill.chars().all(char::is_alphabetic) {
                continue;
            }
            for &sym in &tail {
                cursor.step(sym);
            }
            let freq = cursor.outputs().iter().find_map(|o| alphabet.render(o).parse().ok());
            if let Some(freq) = freq {
                out.push((format!("{prefix}{fill}{suffix}"), freq));
            }
        }
        out
    }
}

/// A candidate the word list knows, and everything that ranks it.
struct Pick {
    /// How common it is. Lower is commoner, as Plover's list counts.
    freq: u32,
    /// Its place in Plover's candidate list.
    rank: u32,
    /// Which rule made it, or `None` for plain concatenation.
    rule: Option<u32>,
    word: String,
}

impl Pick {
    /// Commonest wins; a tie goes to the earlier place in the list, which is what
    /// Plover's stable sort by frequency amounts to.
    fn beaten_by(&self, other: &Pick) -> bool {
        (other.freq, other.rank) < (self.freq, self.rank)
    }
}

impl fmt::Debug for Morph {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("Morph")
    }
}

impl Morph {
    /// Open the three artifacts in a directory, as `stfst morph` writes them.
    pub fn open(dir: &Path) -> Result<Morph, MorphError> {
        let load = |name: &str| {
            Automaton::open(&dir.join(name)).map_err(|e| MorphError::Artifact(name.to_string(), e))
        };
        Ok(Morph {
            rules: load("ortho.hfstol")?,
            words: Lexicon(load("words.hfstol")?),
            infl: load("infl.hfstol")?,
        })
    }

    /// Join a suffix to a word: Plover's `add_suffix`.
    ///
    /// Total, because a join always produces something. Where no rule fires and
    /// nothing is in the word list, that something is concatenation.
    pub fn join(&self, word: &str, suffix: &str) -> String {
        let lower = word.to_lowercase();
        let (rule, joined) = self.join_lower(&lower, &suffix.to_lowercase());
        match rule {
            // No rule fired, so the answer is the two strings as they were written.
            // Taking them back verbatim is what keeps `{^-Z}` from becoming `-z`:
            // the rules run over a lowercase pair and only a rule's own output is
            // the rules' to spell.
            None => format!("{word}{suffix}"),
            Some(rule) => match_case(&joined, word, rule == DOUBLING),
        }
    }

    /// A join that lands on a word where the rules alone do not: `idea` and `cal`
    /// is `identical` where `join` says `ideacal`.
    ///
    /// The word less a final vowel, then a few letters, then the suffix, and the
    /// commonest such word wins. Only where no candidate of the join is a word, so
    /// it never overrides `join`, which stays Plover's own.
    pub fn join_or_complete(&self, word: &str, suffix: &str) -> String {
        let lower = word.to_lowercase();
        let suffix_lower = suffix.to_lowercase();
        let (_, joined) = self.join_lower(&lower, &suffix_lower);
        let letters = suffix_lower.chars().count();
        // A one-letter suffix ends too many words: `child` and `s` would be
        // `childes`.
        if letters < 2 || self.words.freq(&joined).is_some() {
            return self.join(word, suffix);
        }
        let gap = letters.min(COMPLETION_GAP);
        let found = self.complete(&lower, &suffix_lower, gap).into_iter().min_by(|a, b| {
            (a.1, a.0.len(), &a.0).cmp(&(b.1, b.0.len(), &b.0))
        });
        match found {
            Some((completed, _)) => match_case(&completed, word, false),
            None => self.join(word, suffix),
        }
    }

    /// The words that complete a lowercase join, with how common each is.
    fn complete(&self, word: &str, suffix: &str, gap: usize) -> Vec<(String, u32)> {
        let stem = word.strip_suffix(['a', 'e', 'i', 'o', 'u', 'y']).unwrap_or(word);
        if stem.chars().count() < 2 {
            return Vec::new();
        }
        self.words.between(stem, gap, suffix)
    }

    /// `{:infl:mode}`: the inflected form, or `None` for a mode that is not one.
    ///
    /// The lexicon first, then the rules. So `run` and `ed` are `ran` because the
    /// lexicon says so, and `walk` and `ed` are `walked` because the rules do. The
    /// artifact holds only the pairs where the two disagree, which is why the
    /// fallback is not a shortcut: it is where most words are answered.
    pub fn inflect(&self, word: &str, mode: &str) -> Option<String> {
        let mode = match mode {
            "ed" => "ed",
            // The past participle, which is a mode of its own: "she had run" and
            // "she ran" are two forms, and only whatever asked knows which is
            // meant. A rule cannot make it, so off the lexicon it falls back to
            // the same `ed` join.
            "en" => "en",
            "ing" => "ing",
            "ofs" | "s" => "s",
            "ofz" | "z" => "z",
            _ => return None,
        };
        let lower = word.to_lowercase();
        if let Some(form) = self.first(&self.infl, &format!("{lower}^{mode}")) {
            // A lexicon form is not a rule's output, so nothing in it was doubled.
            return Some(match_case(&form, word, false));
        }
        Some(match mode {
            "en" => self.join(word, "ed"),
            // A possessive is a rule and not vocabulary: an apostrophe after a
            // final `s`, and `'s` otherwise. The words that break it are thirteen
            // pronouns, and those are in the lexicon with the rest.
            "z" => match lower.ends_with('s') {
                true => format!("{word}'"),
                false => format!("{word}'s"),
            },
            _ => self.join(word, mode),
        })
    }

    /// Plover's choice among the candidates, over a lowercase pair, and the rule
    /// that made it.
    fn join_lower(&self, word: &str, suffix: &str) -> (Option<u32>, String) {
        let simple = format!("{word}{suffix}");
        let mut best: Option<Pick> = None;
        let mut consider = |rank: u32, rule: Option<u32>, word: String| {
            if let Some(freq) = self.words.freq(&word) {
                let pick = Pick { freq, rank, rule, word };
                if best.as_ref().is_none_or(|b| b.beaten_by(&pick)) {
                    best = Some(pick);
                }
            }
        };
        // An alias candidate counts only if the word list knows it, which is why
        // Plover puts it first: it is a guess that has to earn its place.
        for (from, to) in ALIASES {
            if suffix == *from {
                for (rule, candidate) in self.candidates(word, to) {
                    consider(rule, Some(rule), candidate);
                }
            }
        }
        consider(SIMPLE, None, simple.clone());
        let direct = self.candidates(word, suffix);
        for (rule, candidate) in &direct {
            consider(SIMPLE + rule, Some(*rule), candidate.clone());
        }
        if let Some(pick) = best {
            return (pick.rule, pick.word);
        }
        // Nothing the word list knows, so the earliest rule wins and its answer
        // stands whether it is a word or not.
        match direct.iter().min_by_key(|(rule, _)| *rule) {
            Some((rule, candidate)) => (Some(*rule), candidate.clone()),
            None => (None, simple),
        }
    }

    /// Every candidate the rules make, as `(rule number, word)`.
    fn candidates(&self, word: &str, suffix: &str) -> Vec<(u32, String)> {
        let Some(outputs) = self.rules.lookup(&format!("{word}^{suffix}")) else {
            return Vec::new();
        };
        outputs.iter().filter_map(|o| tagged(o)).collect()
    }

    fn first(&self, fst: &Automaton, tape: &str) -> Option<String> {
        fst.lookup(tape)?.into_iter().next()
    }
}

/// Split `[R=38]running` into the rule number and the candidate.
fn tagged(output: &str) -> Option<(u32, String)> {
    let (tag, rest) = output.strip_prefix("[R=")?.split_once(']')?;
    Some((tag.parse().ok()?, rest.to_string()))
}

/// Give an answer the case of the word it came from.
///
/// The rules run over a lowercase pair, and Plover's own run under `IGNORECASE`
/// and put the word back through a backreference, so the word keeps every capital
/// it had and only what the rule inserted is lowercase. Copying the original
/// character wherever the answer still agrees with it says the same thing without
/// knowing which rule fired: `AIDS` and `s` is `AIDSes`, and `Carry` and `ed` is
/// `Carried` because the `y` is where the two stop agreeing.
///
/// `doubled` is the exception, and there is exactly one. Rule 38 writes a
/// character the word wrote twice, so the copy inherits the original's case:
/// `GOP` and `ed` is `GOPPed`. Guessing that from the strings cannot work, since
/// `CIA` and `able` has the same shape and means nothing of the kind, so the
/// caller says which rule answered instead.
fn match_case(joined: &str, word: &str, doubled: bool) -> String {
    let mut out: Vec<char> = Vec::new();
    let mut original = word.chars().peekable();
    for c in joined.chars() {
        let same = |o: &char| o.to_lowercase().eq(c.to_lowercase());
        if original.peek().is_some_and(same) {
            out.push(original.next().expect("peeked"));
            continue;
        }
        match out.last() {
            Some(&last) if doubled && same(&last) => out.push(last),
            _ => {
                // Past the first disagreement the answer is the rule's text rather
                // than the word's, so there is nothing left to copy from.
                out.push(c);
                original = "".chars().peekable();
            }
        }
    }
    out.into_iter().collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The artifacts are built rather than committed, so a test without them skips.
    fn morph() -> Option<Morph> {
        let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../build/morph");
        let morph = Morph::open(&dir).ok();
        if morph.is_none() {
            eprintln!("skipped: no build/morph");
        }
        morph
    }

    #[test]
    fn a_join_that_makes_no_word_completes_to_one() {
        let Some(m) = morph() else { return };
        for (word, suffix, want) in [
            ("nun", "ry", "nunnery"),
            ("ram", "ication", "ramification"),
            ("idea", "cal", "identical"),
            ("reserve", "tion", "reservation"),
            ("compete", "tion", "competition"),
            ("Reserve", "tion", "Reservation"),
        ] {
            assert_ne!(m.join(word, suffix), want, "{word} + {suffix} was already a word");
            assert_eq!(m.join_or_complete(word, suffix), want, "{word} + {suffix}");
        }
    }

    #[test]
    fn a_join_that_makes_a_word_or_nothing_near_one_stands() {
        let Some(m) = morph() else { return };
        for (word, suffix) in [("run", "ing"), ("child", "s"), ("run", "tion")] {
            assert_eq!(m.join_or_complete(word, suffix), m.join(word, suffix), "{word} + {suffix}");
        }
    }
}
