//! Orthographic joining: what `{^ing}` means beyond "no space".
//!
//! Plover's English system carries 38 rewrite rules over the join, plus a word
//! list to choose between candidates. This is the first twelve of them, in
//! Plover's own order, hand written because the rules cannot be compiled by a Rust
//! regex crate at all: rule 8 uses a lookbehind, and neither `regex` nor
//! `regex-lite` has one.
//!
//! Twelve of thirty-eight is a coverage claim, so it is measured rather than
//! asserted: `scripts/check-ortho.py` runs a sample of joins through Plover's own
//! `add_suffix` and reports the agreement rate.
//!
//! Where the rest belong is a design question, not a coding one. Thirty-eight
//! regexes in every host is exactly what the format exists to avoid, and the
//! window makes the alternative available: compile the rules once into an
//! orthography transducer that a host reads like any other artifact. That is
//! `docs/retro.md`'s open item.

/// A vowel, for the rules that test one.
fn vowel(c: char) -> bool {
    matches!(c, 'a' | 'e' | 'i' | 'o' | 'u')
}

fn ends_with_any(word: &str, tails: &[&str]) -> bool {
    tails.iter().any(|t| word.ends_with(t))
}

/// The character before the last one, for a rule that tests the letter under a
/// final `y` or `e`.
fn penult(word: &str) -> Option<char> {
    let mut chars = word.chars().rev();
    chars.next()?;
    chars.next()
}

fn consonant(c: char) -> bool {
    c.is_ascii_alphabetic() && !vowel(c) && c != 'y'
}

fn cut(word: &str, n: usize) -> &str {
    &word[..word.len() - n]
}

/// Join a suffix to a word, or `None` when no rule fires and plain concatenation
/// is right.
///
/// Case is preserved by working on the lowercase pair and re-casing the answer,
/// which is what Plover's `re.IGNORECASE` amounts to.
pub fn join(word: &str, suffix: &str) -> Option<String> {
    let lower = word.to_lowercase();
    let joined = rule(&lower, &suffix.to_lowercase())?;
    Some(match word.chars().next().is_some_and(char::is_uppercase) {
        false => joined,
        true => {
            let mut out = String::new();
            let mut chars = joined.chars();
            if let Some(first) = chars.next() {
                out.extend(first.to_uppercase());
            }
            out.push_str(chars.as_str());
            out
        }
    })
}

/// Plover's rules, in Plover's order, so the first match wins as it does there.
fn rule(word: &str, suffix: &str) -> Option<String> {
    let last = word.chars().last()?;
    let first = suffix.chars().next()?;

    // 1. `^(.*[aeiou]c) ^ ly$` -> `\1ally`
    if suffix == "ly" && last == 'c' && penult(word).is_some_and(vowel) {
        return Some(format!("{word}ally"));
    }
    // 2. `^(.+[aeioubmnp])le ^ ly$` -> `\1ly`
    if suffix == "ly" && word.ends_with("le") {
        let before = cut(word, 2).chars().last()?;
        if vowel(before) || matches!(before, 'b' | 'm' | 'n' | 'p') {
            return Some(format!("{}ly", cut(word, 2)));
        }
    }
    // 7. `^(.*(?:s|sh|x|z|zh)) ^ s$` -> `\1es`
    if suffix == "s" && ends_with_any(word, &["s", "sh", "x", "z", "zh"]) {
        return Some(format!("{word}es"));
    }
    // 8. `^(.*(?:oa|ea|i|ee|oo|au|ou|l|n|(?<![gin]a)r|t)ch) ^ s$` -> `\1es`
    //
    // The rule with the lookbehind, which is why these are hand written: a `ch`
    // takes `es` when it is pronounced as one, and an `r` before it only when the
    // `r` does not follow `ga`, `ia` or `na`. `dispatches` and `monarchs`.
    if suffix == "s" && word.ends_with("ch") {
        let stem = cut(word, 2);
        let soft = ends_with_any(stem, &["oa", "ea", "i", "ee", "oo", "au", "ou", "l", "n", "t"]);
        let after_r = stem.ends_with('r')
            && !cut(stem, 1)
                .strip_suffix('a')
                .is_some_and(|s| s.ends_with(['g', 'i', 'n']));
        if soft || after_r {
            return Some(format!("{word}es"));
        }
    }
    // 9. `^(.+[bcdfghjklmnpqrstvwxz])y ^ s$` -> `\1ies`
    if suffix == "s" && last == 'y' && penult(word).is_some_and(consonant) {
        return Some(format!("{}ies", cut(word, 1)));
    }
    // 10. `^(.+)ie ^ ing$` -> `\1ying`
    if suffix == "ing" && word.ends_with("ie") {
        return Some(format!("{}ying", cut(word, 2)));
    }
    // 11. `^(.+[cdfghlmnpr])y ^ ist$` -> `\1ist`
    if suffix == "ist"
        && last == 'y'
        && penult(word).is_some_and(|c| "cdfghlmnpr".contains(c))
    {
        return Some(format!("{}ist", cut(word, 1)));
    }
    // 12. `^(.+[bcdfghjklmnpqrstvwxz])y ^ ([a-hj-xz].*)$` -> `\1i\2`
    if last == 'y' && penult(word).is_some_and(consonant) && first != 'i' && first != 'y' {
        return Some(format!("{}i{suffix}", cut(word, 1)));
    }
    // 13. `^(.+)te ^ en$` -> `\1tten`
    if suffix == "en" && word.ends_with("te") {
        return Some(format!("{}tten", cut(word, 2)));
    }
    // 14. `^(.+[ae]) ^ e(n|ns)$` -> `\1\2`
    if (suffix == "en" || suffix == "ens") && (last == 'a' || last == 'e') {
        return Some(format!("{word}{}", &suffix[1..]));
    }
    // 36. `^(.+e)e ^ (e.+)$` -> `\1\2`
    if word.ends_with("ee") && first == 'e' && suffix.len() > 1 {
        return Some(format!("{}{suffix}", cut(word, 1)));
    }
    // 37. `^(.+[bcdfghjklmnpqrstuvwxz])e ^ ([aeiouy].*)$` -> `\1\2`
    if last == 'e' && (vowel(first) || first == 'y') {
        let before = penult(word)?;
        if before.is_ascii_alphabetic() && !vowel(before) {
            return Some(format!("{}{suffix}", cut(word, 1)));
        }
    }
    // 38. doubling: a single consonant after a single vowel doubles before a
    // vowel-initial suffix, so `run` and `ing` make `running`.
    if vowel(first) || first == 'y' {
        let doubles = "bcdfgklmnprtvz";
        if doubles.contains(last) {
            let before = penult(word)?;
            let mut rest = word.chars().rev().skip(2);
            let two_back = rest.next();
            if vowel(before)
                && two_back.is_some_and(|c| {
                    "bcdfghjklmnprstvwxyz".contains(c) || (c == 'u' && rest.next() == Some('q'))
                })
            {
                return Some(format!("{word}{last}{suffix}"));
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_rules_plover_fires_most() {
        for (word, suffix, want) in [
            ("run", "ing", "running"),
            ("stop", "ed", "stopped"),
            ("make", "ing", "making"),
            ("box", "s", "boxes"),
            ("carry", "s", "carries"),
            ("carry", "ed", "carried"),
            ("die", "ing", "dying"),
            ("free", "er", "freer"),
            ("critic", "ly", "critically"),
            ("simple", "ly", "simply"),
        ] {
            assert_eq!(join(word, suffix).as_deref(), Some(want), "{word} + {suffix}");
        }
    }

    #[test]
    fn no_rule_means_plain_concatenation() {
        // The caller concatenates, so `None` is not a failure.
        assert_eq!(join("cat", "s"), None);
        assert_eq!(join("walk", "ing"), None);
        // A single vowel before two consonants does not double.
        assert_eq!(join("jump", "ing"), None);
        // Case survives the rule.
        assert_eq!(join("Carry", "ed").as_deref(), Some("Carried"));
    }
}
