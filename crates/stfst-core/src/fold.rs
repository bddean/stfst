//! A fold: a stroke read as another stroke, with something written around it.
//!
//! `KAOEPT` is "keep the" because `-T` comes off and `KAOEP` is "keep", and
//! `SKPAD` is "and add" because `S-K-P-` comes off and `AD` is "add". The base
//! belongs to whichever dictionary writes it, so a fold record names the keys it
//! takes off and puts on rather than the base, and the host looks the base up
//! across the stack. That is also what keeps a fold grammar small: an automaton
//! that copied the stroke into its output would copy it once per alternative.
//!
//! ```text
//! [FOLD] before [STRIP] keys ([ADD] keys) [END] after
//! ```
//!
//! A record is one or more of those, and the host takes the first whose base
//! translates. A host asks only where nothing matched the stroke as written,
//! and tries the longest outline the base can end first, which is Plover's
//! order for its suffix keys.

use std::fmt;

use crate::record::{Record, RecordError};
use crate::system::{Stroke, System};

pub const FOLD: &str = "[FOLD]";
pub const STRIP: &str = "[STRIP]";
pub const ADD: &str = "[ADD]";

/// The tags only a fold record holds.
pub const FOLD_TAGS: &[&str] = &[FOLD, STRIP, ADD];

/// One way to read a stroke.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Alternative {
    pub around: Around,
    pub strip: Stroke,
    pub add: Stroke,
}

impl Alternative {
    /// The stroke to look up in place of `stroke`, if this alternative reads it.
    pub fn base(&self, stroke: Stroke) -> Option<Stroke> {
        if !stroke.covers(self.strip) {
            return None;
        }
        let base = stroke.minus(self.strip).union(self.add);
        (!base.is_empty()).then_some(base)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Fold(pub Vec<Alternative>);

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum FoldError {
    #[error("a fold record starts with {FOLD}")]
    NoFold,
    #[error("a fold alternative has no {STRIP}")]
    NoStrip,
    #[error("unterminated {STRIP}")]
    Unterminated,
    #[error("{0} is not a key of this system")]
    NotAKey(String),
    #[error(transparent)]
    Record(#[from] RecordError),
}

impl Fold {
    /// Whether a tape is a fold record rather than an ordinary one.
    pub fn is_fold(symbols: &[&str]) -> bool {
        symbols.first() == Some(&FOLD)
    }

    pub fn parse_symbols(symbols: &[&str], system: &System) -> Result<Fold, FoldError> {
        Fold::parse_with(symbols, &|c| system.index_of_symbol(c))
    }

    /// Parse the printed form with no system, so every character counts as a key.
    /// Only for checking a golden file's shape: its keys are checked against the
    /// system when an artifact holding them is read.
    pub fn check_display(s: &str) -> Result<(), FoldError> {
        let symbols = crate::record::tokenise_display(s);
        let refs: Vec<&str> = symbols.iter().map(String::as_str).collect();
        Fold::parse_with(&refs, &|_| Some(0)).map(|_| ())
    }

    fn parse_with(symbols: &[&str], key: &dyn Fn(char) -> Option<usize>) -> Result<Fold, FoldError> {
        if !Fold::is_fold(symbols) {
            return Err(FoldError::NoFold);
        }
        symbols[1..]
            .split(|s| *s == FOLD)
            .map(|alt| Alternative::parse(alt, key))
            .collect::<Result<_, _>>()
            .map(Fold)
    }
}

impl Alternative {
    fn parse(symbols: &[&str], key: &dyn Fn(char) -> Option<usize>) -> Result<Alternative, FoldError> {
        let at = symbols.iter().position(|s| *s == STRIP).ok_or(FoldError::NoStrip)?;
        let end = at + symbols[at..].iter().position(|s| *s == "[END]").ok_or(FoldError::Unterminated)?;
        let keys = &symbols[at + 1..end];
        let (strip, add) = match keys.iter().position(|s| *s == ADD) {
            Some(i) => (&keys[..i], &keys[i + 1..]),
            None => (keys, &[][..]),
        };
        Ok(Alternative {
            around: Around {
                before: Record::parse_symbols(&symbols[..at])?,
                after: Record::parse_symbols(&symbols[end + 1..])?,
            },
            strip: stroke(strip, key)?,
            add: stroke(add, key)?,
        })
    }
}

fn stroke(symbols: &[&str], key: &dyn Fn(char) -> Option<usize>) -> Result<Stroke, FoldError> {
    symbols.iter().try_fold(Stroke::EMPTY, |stroke, s| {
        let mut chars = s.chars();
        match (chars.next().and_then(key), chars.next()) {
            (Some(index), None) => Ok(stroke.with(index)),
            _ => Err(FoldError::NotAKey(s.to_string())),
        }
    })
}

/// What a fold writes around its base: `and _`, `_ the`.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Around {
    /// Written before the base, as a unit of its own. Only where the base begins
    /// an outline: a unit already on screen cannot have something put before it.
    pub before: Record,
    /// Written after the unit the base completes.
    pub after: Record,
}

impl fmt::Display for Around {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let side = |r: &Record| match r.is_empty() {
            true => String::new(),
            false => r.to_string(),
        };
        let shown = format!("{} _ {}", side(&self.before), side(&self.after));
        f.write_str(shown.trim())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sys() -> System {
        System::parse(include_str!("../../../systems/stenotype-extended.toml")).unwrap()
    }

    fn parse(tape: &[&str]) -> Fold {
        Fold::parse_symbols(tape, &sys()).unwrap()
    }

    fn stroke(system: &System, keys: &str) -> Stroke {
        keys.chars()
            .fold(Stroke::EMPTY, |s, c| s.with(system.index_of_symbol(c).unwrap()))
    }

    #[test]
    fn a_suffix_strips_its_key_and_writes_after() {
        let system = sys();
        let fold = parse(&["[FOLD]", "[STRIP]", "t", "[END]", "t", "h", "e"]);
        let alt = &fold.0[0];
        assert!(alt.around.before.is_empty());
        assert_eq!(Record::text("the"), alt.around.after);
        assert_eq!(Some(stroke(&system, "KAOEp")), alt.base(stroke(&system, "KAOEpt")));
        // The base has to be a stroke too.
        assert_eq!(None, alt.base(stroke(&system, "t")));
        assert_eq!(None, alt.base(stroke(&system, "KAOEp")));
    }

    #[test]
    fn alternatives_keep_their_order_and_can_put_a_key_back() {
        let system = sys();
        let tape = [
            "[FOLD]", "a", "n", "d", "[STRIP]", "S", "K", "P", "[END]",
            "[FOLD]", "a", "n", "d", "[STRIP]", "S", "K", "P", "[ADD]", "T", "[END]",
        ];
        let fold = parse(&tape);
        let skpha = stroke(&system, "SKPHA");
        let bases: Vec<_> = fold.0.iter().map(|a| a.base(skpha)).collect();
        assert_eq!(vec![Some(stroke(&system, "HA")), Some(stroke(&system, "THA"))], bases);
    }

    #[test]
    fn an_ordinary_record_is_not_a_fold() {
        assert!(!Fold::is_fold(&["t", "h", "e"]));
        assert_eq!(Err(FoldError::NoFold), Fold::parse_symbols(&["t"], &sys()));
        assert_eq!(
            Err(FoldError::NotAKey("[KEY]".into())),
            Fold::parse_symbols(&["[FOLD]", "[STRIP]", "[KEY]", "[END]"], &sys())
        );
    }

    #[test]
    fn around_shows_where_the_base_goes() {
        let around = Around { before: Record::text("and"), after: Record::default() };
        assert_eq!("and _", around.to_string());
        let around = Around { before: Record::default(), after: Record::text("the") };
        assert_eq!("_ the", around.to_string());
    }
}
