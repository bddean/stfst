//! A steno system: which keys exist, what order they are in, and which tape
//! symbol each one writes.
//!
//! The upper tape is one symbol per key, in steno order, with strokes joined by
//! a separator. Nothing here assumes the stock layout: a Michela board or a
//! hand-hacked keymap ships its own file and the whole toolchain follows.

use std::collections::BTreeMap;
use std::path::Path;

use indexmap::IndexMap;
use serde::Deserialize;

#[derive(Debug, thiserror::Error)]
pub enum SystemError {
    #[error("reading {path}: {source}")]
    Io { path: String, source: std::io::Error },
    #[error("parsing {path}: {source}")]
    Toml { path: String, source: toml::de::Error },
    #[error("`order` repeats the symbol {0:?}")]
    DuplicateSymbol(char),
    #[error("`order` has symbol {0:?} with no entry in [keys]")]
    UnnamedSymbol(char),
    #[error("[keys] names symbol {0:?}, which is absent from `order`")]
    UnorderedSymbol(char),
    #[error("[keys] maps both {0:?} and {1:?} to the key {2:?}")]
    DuplicateKey(char, char, String),
    #[error("`separator` {0:?} is also a key symbol")]
    SeparatorIsSymbol(char),
    #[error("[numbers] maps digit {0:?} to {1:?}, which is not a key symbol")]
    UnknownNumberSymbol(char, char),
    #[error("[implicit_hyphen] lists {0:?}, which is not a key symbol")]
    UnknownImplicitHyphen(char),
}

#[derive(Debug, Deserialize)]
struct SystemFile {
    name: String,
    order: String,
    separator: char,
    keys: IndexMap<char, String>,
    #[serde(default)]
    numbers: BTreeMap<char, char>,
    #[serde(default)]
    implicit_hyphen: String,
    #[serde(default)]
    suffix_keys: String,
}

/// One key of a system, in steno order.
#[derive(Debug, Clone)]
pub struct Key {
    /// Tape symbol. Single character so that xfst `{TPl}` needs no sugar.
    pub symbol: char,
    /// The host's name for this key, e.g. `S-` or `-S`.
    pub name: String,
    /// `name` with hyphens stripped, which is how outlines spell it. A name that
    /// does not strip to one character falls back to `symbol`.
    pub letter: char,
    pub side: Side,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Side {
    Left,
    Center,
    Right,
}

#[derive(Debug, Clone)]
pub struct System {
    pub name: String,
    pub separator: char,
    keys: Vec<Key>,
    by_symbol: BTreeMap<char, usize>,
    /// digit -> index into `keys`
    numbers: BTreeMap<char, usize>,
    implicit_hyphen: Vec<usize>,
    /// Keys that can be *folded* onto a stroke: the engine may strip one, look the
    /// rest up across the whole stack, and append what the bare key means on its
    /// own. Plover's `SUFFIX_KEYS`.
    suffix_keys: Vec<usize>,
    /// First index on the right bank; a written `-` skips to here.
    first_right: usize,
}

impl System {
    pub fn load(path: impl AsRef<Path>) -> Result<Self, SystemError> {
        let path = path.as_ref();
        let text = std::fs::read_to_string(path).map_err(|source| SystemError::Io {
            path: path.display().to_string(),
            source,
        })?;
        Self::parse(&text).map_err(|e| match e {
            SystemError::Toml { source, .. } => SystemError::Toml {
                path: path.display().to_string(),
                source,
            },
            other => other,
        })
    }

    pub fn parse(text: &str) -> Result<Self, SystemError> {
        let file: SystemFile = toml::from_str(text).map_err(|source| SystemError::Toml {
            path: "<memory>".into(),
            source,
        })?;

        let mut keys: Vec<Key> = Vec::new();
        let mut by_symbol: BTreeMap<char, usize> = BTreeMap::new();
        let mut by_name: BTreeMap<String, usize> = BTreeMap::new();

        for symbol in file.order.chars() {
            if by_symbol.contains_key(&symbol) {
                return Err(SystemError::DuplicateSymbol(symbol));
            }
            let name = file
                .keys
                .get(&symbol)
                .ok_or(SystemError::UnnamedSymbol(symbol))?
                .clone();

            let letter = key_letter(&name, symbol);
            let side = match (name.starts_with('-'), name.ends_with('-')) {
                (true, _) => Side::Right,
                (_, true) => Side::Left,
                _ => Side::Center,
            };

            if let Some(&prior) = by_name.get(&name) {
                let prior: usize = prior;
                return Err(SystemError::DuplicateKey(
                    keys[prior].symbol,
                    symbol,
                    name,
                ));
            }
            by_name.insert(name.clone(), keys.len());
            by_symbol.insert(symbol, keys.len());
            keys.push(Key {
                symbol,
                name,
                letter,
                side,
            });
        }

        for symbol in file.keys.keys() {
            if !by_symbol.contains_key(symbol) {
                return Err(SystemError::UnorderedSymbol(*symbol));
            }
        }
        if by_symbol.contains_key(&file.separator) {
            return Err(SystemError::SeparatorIsSymbol(file.separator));
        }

        let mut numbers = BTreeMap::new();
        for (digit, symbol) in &file.numbers {
            let idx = *by_symbol
                .get(symbol)
                .ok_or(SystemError::UnknownNumberSymbol(*digit, *symbol))?;
            numbers.insert(*digit, idx);
        }

        let mut suffix_keys = Vec::new();
        for symbol in file.suffix_keys.chars() {
            let idx = *by_symbol
                .get(&symbol)
                .ok_or(SystemError::UnknownImplicitHyphen(symbol))?;
            suffix_keys.push(idx);
        }

        let mut implicit_hyphen = Vec::new();
        for symbol in file.implicit_hyphen.chars() {
            let idx = *by_symbol
                .get(&symbol)
                .ok_or(SystemError::UnknownImplicitHyphen(symbol))?;
            implicit_hyphen.push(idx);
        }

        let first_right = keys
            .iter()
            .position(|k| k.side == Side::Right)
            .unwrap_or(keys.len());

        Ok(System {
            name: file.name,
            separator: file.separator,
            keys,
            by_symbol,
            numbers,
            implicit_hyphen,
            suffix_keys,
            first_right,
        })
    }

    pub fn keys(&self) -> &[Key] {
        &self.keys
    }

    /// The keys a stroke may be folded on, newest declaration order.
    pub fn suffix_keys(&self) -> &[usize] {
        &self.suffix_keys
    }

    pub fn key_count(&self) -> usize {
        self.keys.len()
    }

    pub fn index_of_symbol(&self, symbol: char) -> Option<usize> {
        self.by_symbol.get(&symbol).copied()
    }

    /// The steno order as a symbol string, which is how the prelude spells it.
    pub fn order(&self) -> String {
        self.keys.iter().map(|k| k.symbol).collect()
    }
}

fn key_letter(name: &str, symbol: char) -> char {
    let stripped = name.trim_matches('-');
    let mut chars = stripped.chars();
    match (chars.next(), chars.next()) {
        (Some(c), None) => c,
        _ => symbol,
    }
}

/// A single stroke: a subset of the system's keys.
///
/// A bitmask over steno-order positions, so a stroke cannot hold a key twice
/// and cannot be out of order. Both invariants are otherwise easy to violate
/// while building a tape.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Default)]
pub struct Stroke(u64);

impl Stroke {
    pub const EMPTY: Stroke = Stroke(0);

    pub fn is_empty(self) -> bool {
        self.0 == 0
    }

    pub fn contains(self, index: usize) -> bool {
        index < 64 && self.0 & (1 << index) != 0
    }

    pub fn with(self, index: usize) -> Stroke {
        debug_assert!(index < 64, "systems are limited to 64 keys");
        Stroke(self.0 | (1 << index))
    }

    /// The same stroke without one key, for a caller folding a suffix off it.
    pub fn without(self, index: usize) -> Stroke {
        Stroke(self.0 & !(1 << index))
    }

    pub fn indices(self) -> impl Iterator<Item = usize> {
        (0..64).filter(move |i| self.0 & (1 << i) != 0)
    }

    pub fn len(self) -> u32 {
        self.0.count_ones()
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Default)]
pub struct Outline(pub Vec<Stroke>);

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum OutlineError {
    #[error("{outline:?}: {letter:?} is not a key of this system, or is out of steno order")]
    UnknownOrOutOfOrder { outline: String, letter: char },
    #[error("{outline:?}: empty stroke")]
    EmptyStroke { outline: String },
    #[error("{outline:?}: digit {digit:?} has no number-bar key")]
    UnmappedDigit { outline: String, digit: char },
}

impl System {
    /// Parse an outline as written in a Plover dictionary, e.g. `TP-L` or
    /// `STKPWAOURB/-G` or `2-BG`.
    ///
    /// Left to right over steno order, taking the earliest key that can still
    /// match. This is what makes `TR` mean `T- R-` while `T-R` means `T- -R`.
    pub fn parse_outline(&self, outline: &str) -> Result<Outline, OutlineError> {
        let mut strokes = Vec::new();
        for chunk in outline.split(self.separator) {
            strokes.push(self.parse_stroke(chunk, outline)?);
        }
        Ok(Outline(strokes))
    }

    fn parse_stroke(&self, chunk: &str, whole: &str) -> Result<Stroke, OutlineError> {
        // The number bar substitutes digits for letter keys, so undo that first
        // and parse one language rather than two.
        let text: String = if chunk.chars().any(|c| c.is_ascii_digit()) {
            let mut out = String::from("#");
            for c in chunk.chars() {
                if c.is_ascii_digit() {
                    let idx = self.numbers.get(&c).ok_or(OutlineError::UnmappedDigit {
                        outline: whole.to_string(),
                        digit: c,
                    })?;
                    out.push(self.keys[*idx].letter);
                } else if c != '#' {
                    out.push(c);
                }
            }
            out
        } else {
            chunk.to_string()
        };

        let mut stroke = Stroke::EMPTY;
        let mut pos = 0usize;
        for c in text.chars() {
            if c == '-' {
                pos = pos.max(self.first_right);
                continue;
            }
            let found = (pos..self.keys.len()).find(|&i| self.keys[i].letter == c);
            match found {
                Some(i) => {
                    stroke = stroke.with(i);
                    pos = i + 1;
                }
                None => {
                    return Err(OutlineError::UnknownOrOutOfOrder {
                        outline: whole.to_string(),
                        letter: c,
                    })
                }
            }
        }
        if stroke.is_empty() {
            return Err(OutlineError::EmptyStroke {
                outline: whole.to_string(),
            });
        }
        Ok(stroke)
    }

    /// Render an outline the way a Plover dictionary spells it, inserting the
    /// disambiguating hyphen only where no implicit-hyphen key already splits
    /// the banks.
    pub fn format_outline(&self, outline: &Outline) -> String {
        let parts: Vec<String> = outline.0.iter().map(|s| self.format_stroke(*s)).collect();
        parts.join(&self.separator.to_string())
    }

    fn format_stroke(&self, stroke: Stroke) -> String {
        let has_implicit = self.implicit_hyphen.iter().any(|&i| stroke.contains(i));
        let has_right = stroke
            .indices()
            .any(|i| self.keys[i].side == Side::Right);
        let mut out = String::new();
        let mut hyphenated = has_implicit || !has_right;
        for i in stroke.indices() {
            if !hyphenated && self.keys[i].side == Side::Right {
                out.push('-');
                hyphenated = true;
            }
            out.push(self.keys[i].letter);
        }
        out
    }

    /// One stroke as tape symbols, in steno order.
    pub fn stroke_symbols(&self, stroke: Stroke) -> impl Iterator<Item = char> + '_ {
        stroke.indices().map(|i| self.keys[i].symbol)
    }

    /// The upper tape: one symbol per key in steno order, strokes separated.
    pub fn to_tape(&self, outline: &Outline) -> String {
        let mut out = String::new();
        for (n, stroke) in outline.0.iter().enumerate() {
            if n > 0 {
                out.push(self.separator);
            }
            out.extend(self.stroke_symbols(*stroke));
        }
        out
    }

    pub fn from_tape(&self, tape: &str) -> Result<Outline, OutlineError> {
        let mut strokes = Vec::new();
        for chunk in tape.split(self.separator) {
            let mut stroke = Stroke::EMPTY;
            for c in chunk.chars() {
                let i = self
                    .index_of_symbol(c)
                    .ok_or(OutlineError::UnknownOrOutOfOrder {
                        outline: tape.to_string(),
                        letter: c,
                    })?;
                stroke = stroke.with(i);
            }
            if stroke.is_empty() {
                return Err(OutlineError::EmptyStroke {
                    outline: tape.to_string(),
                });
            }
            strokes.push(stroke);
        }
        Ok(Outline(strokes))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const EXT: &str = include_str!("../../../systems/stenotype-extended.toml");

    fn sys() -> System {
        System::parse(EXT).expect("stock system file must be valid")
    }

    #[test]
    fn order_round_trips() {
        let s = sys();
        assert_eq!(s.order(), "🦐#@^+STKPWHRAO*EUfrpblgtsdz");
        assert_eq!(s.key_count(), 27);
    }

    #[test]
    fn greedy_match_prefers_the_left_bank() {
        let s = sys();
        assert_eq!(s.to_tape(&s.parse_outline("TR").unwrap()), "TR");
        assert_eq!(s.to_tape(&s.parse_outline("T-R").unwrap()), "Tr");
        assert_eq!(s.to_tape(&s.parse_outline("TPL").unwrap()), "TPl");
        assert_eq!(s.to_tape(&s.parse_outline("TP-L").unwrap()), "TPl");
    }

    #[test]
    fn lowercase_right_bank_disambiguates_what_text_cannot() {
        let s = sys();
        assert_eq!(s.to_tape(&s.parse_outline("S").unwrap()), "S");
        assert_eq!(s.to_tape(&s.parse_outline("-S").unwrap()), "s");
        // The R follows the vowels, and left R- precedes A- in steno order, so
        // this R is the right-bank one.
        assert_eq!(s.to_tape(&s.parse_outline("STKPWAOURB").unwrap()), "STKPWAOUrb");
    }

    #[test]
    fn multi_stroke_outlines_keep_the_separator() {
        let s = sys();
        assert_eq!(s.to_tape(&s.parse_outline("TKEU/-S").unwrap()), "TKEU/s");
    }

    #[test]
    fn number_bar_normalises_to_letters() {
        let s = sys();
        // 2-BG is #, T-, -B, -G
        assert_eq!(s.to_tape(&s.parse_outline("2-BG").unwrap()), "#Tbg");
        assert_eq!(s.to_tape(&s.parse_outline("#T-BG").unwrap()), "#Tbg");
    }

    #[test]
    fn formatting_reinserts_the_hyphen_only_when_needed() {
        let s = sys();
        for outline in ["TR", "T-R", "TPL", "TKEU/-S", "STKPWAOURB", "-S", "AEU"] {
            let parsed = s.parse_outline(outline).unwrap();
            let printed = s.format_outline(&parsed);
            assert_eq!(
                s.parse_outline(&printed).unwrap(),
                parsed,
                "{outline} printed as {printed} and did not re-parse"
            );
        }
        assert_eq!(s.format_outline(&s.parse_outline("TP-L").unwrap()), "TP-L");
        assert_eq!(s.format_outline(&s.parse_outline("TPAL").unwrap()), "TPAL");
    }

    #[test]
    fn tape_round_trips() {
        let s = sys();
        for outline in ["TR", "T-R", "TKEU/-S", "2-BG", "STKPWAOURB"] {
            let parsed = s.parse_outline(outline).unwrap();
            let tape = s.to_tape(&parsed);
            assert_eq!(s.from_tape(&tape).unwrap(), parsed);
        }
    }

    #[test]
    fn word_named_keys_spell_outlines_with_their_symbols() {
        let words = r#"
name = "words"
order = "PRA"
separator = "/"
[keys]
P = "Pee"
R = "circle-r"
A = "Ay"
"#;
        let s = System::parse(words).expect("word-named keys must load");
        assert_eq!(s.keys().iter().map(|k| k.letter).collect::<String>(), "PRA");
        let parsed = s.parse_outline("PRA").unwrap();
        assert_eq!(s.to_tape(&parsed), "PRA");
        assert_eq!(s.format_outline(&parsed), "PRA");
        assert_eq!(s.from_tape("PRA").unwrap(), parsed);
    }

    #[test]
    fn a_duplicate_symbol_is_rejected() {
        let bad = r#"
name = "bad"
order = "STS"
separator = "/"
[keys]
S = "S-"
T = "T-"
"#;
        assert!(matches!(
            System::parse(bad),
            Err(SystemError::DuplicateSymbol('S'))
        ));
    }

    #[test]
    fn two_symbols_for_one_key_is_rejected() {
        let bad = r#"
name = "bad"
order = "Ss"
separator = "/"
[keys]
S = "S-"
s = "S-"
"#;
        assert!(matches!(
            System::parse(bad),
            Err(SystemError::DuplicateKey(..))
        ));
    }

    #[test]
    fn a_symbol_missing_from_order_is_rejected() {
        let bad = r#"
name = "bad"
order = "S"
separator = "/"
[keys]
S = "S-"
t = "-T"
"#;
        assert!(matches!(
            System::parse(bad),
            Err(SystemError::UnorderedSymbol('t'))
        ));
    }
}
