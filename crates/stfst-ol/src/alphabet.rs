//! The symbol table, and turning a tape into symbol numbers.

use std::collections::HashMap;

use crate::{OlError, Sym};

pub struct Alphabet {
    symbols: Vec<String>,
    by_name: HashMap<String, Sym>,
    input_count: usize,
    longest_input: usize,
}

impl Alphabet {
    /// Symbols `0..input_count` are the ones an input tape may use, and are the
    /// only ones `by_name` holds. HFST orders the table so they come first,
    /// which is why a symbol at or above that count can never be a transition
    /// input, and why the unknown and identity symbols below are worth
    /// refusing only there.
    pub(crate) fn new(symbols: Vec<String>, input_count: usize) -> Result<Self, OlError> {
        let mut by_name = HashMap::with_capacity(input_count);
        let mut longest_input = 0;
        for (i, s) in symbols.iter().enumerate().take(input_count) {
            if is_flag_diacritic(s) {
                return Err(OlError::Unsupported("flag diacritics"));
            }
            if matches!(
                s.as_str(),
                "@_UNKNOWN_SYMBOL_@" | "@_IDENTITY_SYMBOL_@" | "@_DEFAULT_SYMBOL_@"
            ) {
                return Err(OlError::Unsupported("unknown or identity input symbols"));
            }
            by_name.insert(s.clone(), i as Sym);
            longest_input = longest_input.max(s.len());
        }
        Ok(Alphabet {
            symbols,
            by_name,
            input_count,
            longest_input,
        })
    }

    /// Epsilon renders as nothing, so a record is the concatenation of the
    /// output symbols along its path.
    pub fn symbol(&self, s: Sym) -> &str {
        match s {
            crate::EPSILON => "",
            _ => &self.symbols[s as usize],
        }
    }

    /// Every symbol the automaton can read or write. A host checks these once at
    /// load time, so an artifact that could emit something it does not
    /// understand is refused before a stroke arrives.
    pub fn symbols(&self) -> impl Iterator<Item = &str> {
        self.symbols.iter().map(String::as_str)
    }

    /// How many symbols an input tape may use. A symbol at or above this can
    /// never be a transition input, so a traversal need not try it.
    pub fn input_count(&self) -> usize {
        self.input_count
    }

    pub fn number(&self, name: &str) -> Option<Sym> {
        self.by_name.get(name).copied()
    }

    /// Longest match over the input symbols. `None` if some part of the tape is
    /// not a symbol this automaton knows, which is a different answer from "no
    /// entry".
    pub fn tokenize(&self, tape: &str) -> Option<Vec<Sym>> {
        let mut out = Vec::new();
        let mut i = 0;
        while i < tape.len() {
            let limit = (i + self.longest_input).min(tape.len());
            let found = (i + 1..=limit)
                .rev()
                .filter(|&j| tape.is_char_boundary(j))
                .find_map(|j| self.number(&tape[i..j]).map(|s| (s, j)))?;
            out.push(found.0);
            i = found.1;
        }
        Some(out)
    }

    pub fn render(&self, syms: &[Sym]) -> String {
        syms.iter().map(|&s| self.symbol(s)).collect()
    }
}

/// `@` operation `.` feature (`.` value) `@`, e.g. `@U.NEED.YES@`.
fn is_flag_diacritic(s: &str) -> bool {
    let mut c = s.chars();
    s.len() > 4
        && c.next() == Some('@')
        && matches!(c.next(), Some('P' | 'N' | 'R' | 'D' | 'C' | 'U'))
        && c.next() == Some('.')
        && s.ends_with('@')
}

#[cfg(test)]
mod tests {
    use super::*;

    fn alphabet() -> Alphabet {
        let syms = ["@_EPSILON_SYMBOL_@", "S", "T", "[KEY]", "x"];
        Alphabet::new(syms.iter().map(|s| s.to_string()).collect(), 4).unwrap()
    }

    #[test]
    fn longest_match_wins() {
        let a = alphabet();
        assert_eq!(a.tokenize("ST[KEY]"), Some(vec![1, 2, 3]));
    }

    #[test]
    fn a_symbol_past_the_input_count_is_not_tokenizable() {
        assert_eq!(alphabet().tokenize("x"), None, "x is output only");
    }

    #[test]
    fn epsilon_renders_as_nothing() {
        assert_eq!(alphabet().render(&[1, 0, 2]), "ST");
    }

    #[test]
    fn flag_diacritics_are_refused() {
        let syms = ["@_EPSILON_SYMBOL_@", "@U.CASE.UP@"];
        let e = Alphabet::new(syms.iter().map(|s| s.to_string()).collect(), 2);
        assert!(matches!(e, Err(OlError::Unsupported(_))));
    }
}
