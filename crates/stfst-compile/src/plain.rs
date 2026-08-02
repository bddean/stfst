//! A flat dictionary written in the record language.
//!
//! §4's friendlier surface than `lexc`: one entry per line, an outline as a steno
//! writer spells it, a tab, and a record. Comments and blank lines are allowed,
//! and a duplicate outline is an error rather than the silent last-wins a JSON
//! object gives.
//!
//! ```text
//! ! repeat-word: a connective, then the word again
//! ^SKP    and [COPY1]
//! ```
//!
//! This is where a hand-written flat dictionary goes when it has nothing
//! generative to say. Anything with rules in it is an xfst grammar instead, and
//! anything that came from Plover is an import.
//!
//! `!` opens a comment and not `#`, because `#` is a key: `#+` is an outline in
//! this system.

use std::collections::BTreeMap;

use stfst_core::{Record, System};

use crate::lexc::{self, Align};

#[derive(Debug)]
pub struct Plain {
    pub lexc: String,
    /// `(tape, record)` for every entry, in file order.
    pub golden: Vec<(String, String)>,
    pub entries: usize,
}

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum PlainError {
    #[error("line {line}: expected `outline<TAB>record`")]
    NoTab { line: usize },
    #[error("line {line}: {outline:?} is not an outline: {why}")]
    BadOutline {
        line: usize,
        outline: String,
        why: String,
    },
    #[error("line {line}: {record:?} is not a record: {why}")]
    BadRecord {
        line: usize,
        record: String,
        why: String,
    },
    #[error("line {line}: {outline:?} is already defined on line {first}")]
    Duplicate {
        line: usize,
        outline: String,
        first: usize,
    },
}

pub fn compile(text: &str, system: &System, align: Align) -> Result<Plain, Vec<PlainError>> {
    let mut errors = Vec::new();
    let mut seen: BTreeMap<String, usize> = BTreeMap::new();
    let mut entries: Vec<(String, String)> = Vec::new();
    let mut golden: Vec<(String, String)> = Vec::new();
    let mut used: Vec<String> = Vec::new();

    for (n, raw) in text.lines().enumerate() {
        let line = n + 1;
        let source = raw.trim_end();
        if source.trim().is_empty() || source.trim_start().starts_with('!') {
            continue;
        }
        let Some((outline, record)) = source.split_once('\t') else {
            errors.push(PlainError::NoTab { line });
            continue;
        };
        let outline = outline.trim();
        // Only the outline is trimmed. A record can begin or end with a space,
        // and `and [COPY1]` is the reason: the space is the word boundary.
        let parsed = match system.parse_outline(outline) {
            Ok(o) => o,
            Err(why) => {
                errors.push(PlainError::BadOutline {
                    line,
                    outline: outline.to_string(),
                    why: why.to_string(),
                });
                continue;
            }
        };
        let record = match symbols(record).and_then(|s| {
            let refs: Vec<&str> = s.iter().map(String::as_str).collect();
            Record::parse_symbols(&refs).map_err(|e| e.to_string())
        }) {
            Ok(r) => r,
            Err(why) => {
                errors.push(PlainError::BadRecord {
                    line,
                    record: record.to_string(),
                    why,
                });
                continue;
            }
        };
        if let Some(first) = seen.insert(outline.to_string(), line) {
            errors.push(PlainError::Duplicate {
                line,
                outline: outline.to_string(),
                first,
            });
            continue;
        }
        let tape = system.to_tape(&parsed);
        golden.push((tape.clone(), record.to_string()));
        let symbols = record.to_symbols();
        for symbol in &symbols {
            if symbol.chars().count() > 1 && !used.contains(symbol) {
                used.push(symbol.clone());
            }
        }
        entries.push((lexc::escape(&tape), lexc::escape_symbols(&symbols)));
    }

    if !errors.is_empty() {
        return Err(errors);
    }
    Ok(Plain {
        entries: entries.len(),
        lexc: lexc::lexicon(entries, align, &used),
        golden,
    })
}

/// A written record as tape symbols.
///
/// Not `Record::parse_display`, which reads an unknown `[TYPO]` as four
/// characters of text. That is the right reading for something a machine printed
/// and the wrong one for something a person typed, so here a bracket run must be
/// a tag and a literal bracket is written `\[`.
fn symbols(field: &str) -> Result<Vec<String>, String> {
    let known = stfst_core::record::all_tags();
    let mut out = Vec::new();
    let mut chars = field.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '\\' => match chars.next() {
                Some(next @ ('[' | ']' | '\\')) => out.push(next.to_string()),
                Some(next) => return Err(format!("\\{next} is not an escape here")),
                None => return Err("a trailing backslash".into()),
            },
            '[' => {
                let mut tag = String::from("[");
                for c in chars.by_ref() {
                    tag.push(c);
                    if c == ']' {
                        break;
                    }
                }
                if !known.iter().any(|t| *t == tag) {
                    return Err(format!("{tag} is not a tag; write a literal one as \\[…"));
                }
                out.push(tag);
            }
            c => out.push(c.to_string()),
        }
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sys() -> System {
        System::parse(include_str!("../../../systems/stenotype-extended.toml")).unwrap()
    }

    #[test]
    fn an_entry_is_an_outline_a_tab_and_a_record() {
        let plain = compile(
            "! a comment\n\n^SKP\tand [COPY1]\n^-T\tthe [COPY1]\n",
            &sys(),
            Align::Pairwise,
        )
        .unwrap();
        assert_eq!(plain.entries, 2);
        assert_eq!(
            plain.golden,
            vec![
                ("^SKP".to_string(), "and [COPY1]".to_string()),
                ("^t".to_string(), "the [COPY1]".to_string()),
            ]
        );
    }

    #[test]
    fn what_json_lets_pass_is_an_error_here() {
        // A duplicate outline is last-wins and silent in a JSON object.
        let twice = compile("TEFT\ta\nTEFT\tb\n", &sys(), Align::Pairwise).unwrap_err();
        assert!(matches!(twice[0], PlainError::Duplicate { line: 2, .. }));
        // An outline out of steno order, which no machine can send.
        let backwards = compile("TS-K\ta\n", &sys(), Align::Pairwise).unwrap_err();
        assert!(matches!(backwards[0], PlainError::BadOutline { .. }));
        // A tag that is not in the record language. `parse_display` would read
        // this as text, which is what a hand-written source must not do.
        let bad = compile("TEFT\t[NOPE]\n", &sys(), Align::Pairwise).unwrap_err();
        assert!(matches!(bad[0], PlainError::BadRecord { .. }));
    }

    #[test]
    fn a_literal_bracket_is_escaped() {
        let plain = compile("TEFT\t\\[a\\]\n", &sys(), Align::Pairwise).unwrap();
        assert_eq!(plain.golden[0].1, "[a]");
    }
}
