//! Emitting lexc.
//!
//! The one hazard is escaping. `0` on either tape means epsilon to lexc, so an
//! unescaped digit in output text silently becomes nothing.

use stfst_core::record::all_tags;

/// `%` before every non-alphanumeric ASCII character. lexc's metacharacter set
/// is larger than its documentation admits, and `%X` is literal for any X, so
/// escaping the whole class is both safe and shorter than tracking the set.
/// Non-ASCII needs no escape and must not get one.
pub fn escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        // `0` is lexc's epsilon, so an unescaped one silently vanishes.
        if c == '0' || (c.is_ascii() && !c.is_ascii_alphanumeric()) {
            out.push('%');
        }
        out.push(c);
    }
    out
}

/// Escape a record's symbol sequence, leaving declared multichar tags alone
/// since lexc reads those as single symbols.
pub fn escape_symbols(symbols: &[String]) -> String {
    let tags = all_tags();
    let mut out = String::new();
    for s in symbols {
        if tags.contains(s) {
            out.push_str(s);
        } else {
            out.push_str(&escape(s));
        }
    }
    out
}

pub fn header() -> String {
    format!("Multichar_Symbols {}\n\n", all_tags().join(" "))
}

/// How the two tapes line up, which decides what the automaton can share.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Align {
    /// `upper:lower`, lexc's default. Two symbols ride one arc, so an entry is
    /// dense, but the pair sequence is unique to the entry and nothing is shared
    /// across entries.
    Pairwise,
    /// Consume the whole outline, then emit the translation. Costs an arc per
    /// symbol, and in exchange every outline reaching the same translation
    /// converges on one copy of it.
    Delayed,
}

pub fn lexicon(
    entries: impl IntoIterator<Item = (String, String)>,
    align: Align,
) -> String {
    match align {
        Align::Pairwise => {
            let mut out = header();
            out.push_str("LEXICON Root\n");
            for (upper, lower) in entries {
                let lower = if lower.is_empty() { "0".into() } else { lower };
                out.push_str(&format!("{upper}:{lower} # ;\n"));
            }
            out
        }
        Align::Delayed => {
            let mut root = String::from("LEXICON Root\n");
            let mut tails = String::new();
            let mut ids: indexmap::IndexMap<String, usize> = indexmap::IndexMap::new();
            for (upper, lower) in entries {
                let next = ids.len();
                let id = *ids.entry(lower).or_insert(next);
                root.push_str(&format!("{upper}:0 T{id} ;\n"));
            }
            for (lower, id) in &ids {
                let lower = if lower.is_empty() { "0" } else { lower };
                tails.push_str(&format!("LEXICON T{id}\n0:{lower} # ;\n"));
            }
            let mut out = header();
            out.push_str(&root);
            out.push('\n');
            out.push_str(&tails);
            out
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn epsilon_hazard_is_escaped() {
        assert_eq!(escape("100"), "1%0%0");
        assert_eq!(escape("a:b"), "a%:b");
        assert_eq!(escape("#"), "%#");
        assert_eq!(escape("\""), "%\"");
    }

    #[test]
    fn non_ascii_passes_through() {
        assert_eq!(escape("€¬↦"), "€¬↦");
    }

    #[test]
    fn declared_tags_pass_through_unescaped() {
        let syms = vec!["[ATTACH_L]".to_string(), "0".to_string()];
        assert_eq!(escape_symbols(&syms), "[ATTACH_L]%0");
    }
}
