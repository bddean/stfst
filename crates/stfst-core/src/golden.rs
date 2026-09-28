//! The golden test format: strokes in, output record out.
//!
//! Host independent on purpose. Every host that claims to read these artifacts
//! should pass the same files, so the format has no room for a host's opinion:
//! a tape, a tab, and the expected records.

use crate::fold::{Fold, FOLD};
use crate::record::Record;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Case {
    pub tape: String,
    /// Every record the dictionary should return. Empty means "no entry".
    pub expect: Vec<String>,
}

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum GoldenError {
    #[error("line {line}: expected `tape<TAB>record`, got {text:?}")]
    Malformed { line: usize, text: String },
    #[error("line {line}: {tape:?} appears twice")]
    Duplicate { line: usize, tape: String },
}

pub fn parse(text: &str) -> Result<Vec<Case>, GoldenError> {
    let mut cases: Vec<Case> = Vec::new();
    for (n, raw) in text.lines().enumerate() {
        let line = n + 1;
        let raw = raw.trim_end_matches(['\r']);
        // `!` and not `#`, because `#` is the number-bar key and a tape may
        // start with it.
        if raw.trim().is_empty() || raw.starts_with('!') {
            continue;
        }
        // No tab at all means "no entry". A tab followed by nothing means one
        // record which is empty, which is a different claim: emily-symbols' bare
        // identifier `#U` really does map to the empty record.
        let (tape, expect) = match raw.split_once('\t') {
            None => (raw, Vec::new()),
            Some((tape, rest)) => (tape, rest.split('\t').map(str::to_string).collect()),
        };
        if tape.trim().is_empty() {
            return Err(GoldenError::Malformed {
                line,
                text: raw.to_string(),
            });
        }
        if cases.iter().any(|c| c.tape == tape) {
            return Err(GoldenError::Duplicate {
                line,
                tape: tape.to_string(),
            });
        }
        cases.push(Case {
            tape: tape.to_string(),
            expect,
        });
    }
    Ok(cases)
}

pub fn render(cases: &[Case]) -> String {
    let mut out = String::new();
    for case in cases {
        out.push_str(&case.tape);
        for r in &case.expect {
            out.push('\t');
            out.push_str(r);
        }
        out.push('\n');
    }
    out
}

/// One case's verdict. `expect` and `got` are compared as sets, because a
/// dictionary returns a set and the order of it is the host's business.
#[derive(Debug)]
pub struct Outcome {
    pub tape: String,
    pub expect: Vec<String>,
    pub got: Vec<String>,
}

impl Outcome {
    pub fn passed(&self) -> bool {
        let mut a = self.expect.clone();
        let mut b = self.got.clone();
        a.sort();
        b.sort();
        a == b
    }
}

/// Reject an expectation that is not a legal record, so a typo in a golden file
/// cannot quietly become the standard a host is measured against.
pub fn check_expectations(cases: &[Case]) -> Vec<(String, String, String)> {
    let mut bad = Vec::new();
    for case in cases {
        for r in &case.expect {
            let checked = match r.starts_with(FOLD) {
                true => Fold::check_display(r).map_err(|e| e.to_string()),
                false => Record::parse_display(r).map(|_| ()).map_err(|e| e.to_string()),
            };
            if let Err(e) = checked {
                bad.push((case.tape.clone(), r.clone(), e));
            }
        }
    }
    bad
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn comments_and_blanks_are_skipped() {
        let cases = parse("! a comment\n\n#HRU\t!\n").unwrap();
        assert_eq!(cases.len(), 1);
        assert_eq!(cases[0].tape, "#HRU");
        assert_eq!(cases[0].expect, vec!["!"]);
    }

    #[test]
    fn no_entry_and_an_empty_record_are_different_claims() {
        assert_eq!(parse("#HR\n").unwrap()[0].expect, Vec::<String>::new());
        assert_eq!(parse("#U\t\n").unwrap()[0].expect, vec![""]);
    }

    #[test]
    fn several_records_are_a_set() {
        let cases = parse("Sr\tone\ttwo\n").unwrap();
        assert_eq!(cases[0].expect, vec!["one", "two"]);
        let outcome = Outcome {
            tape: "Sr".into(),
            expect: vec!["one".into(), "two".into()],
            got: vec!["two".into(), "one".into()],
        };
        assert!(outcome.passed(), "order must not matter");
    }

    #[test]
    fn a_duplicate_tape_is_rejected() {
        assert!(matches!(
            parse("Sr\ta\nSr\tb\n"),
            Err(GoldenError::Duplicate { .. })
        ));
    }

    #[test]
    fn an_illegal_expectation_is_caught() {
        let cases = parse("Sr\t[CAP_NEXT]ing\n").unwrap();
        assert_eq!(check_expectations(&cases).len(), 1);
    }

    #[test]
    fn round_trips() {
        let text = "#HRU\t!\n#HR\n#U\t\n";
        assert_eq!(render(&parse(text).unwrap()), text);
    }
}
