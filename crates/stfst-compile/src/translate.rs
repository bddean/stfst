//! Plover translation strings to §3 records.
//!
//! Plover mixes text and directives in one band, so text and directives share a
//! namespace and every consumer reimplements an escaping grammar. This is that
//! grammar, written once, on the way in.
//!
//! Anything unrecognised is reported rather than guessed at. The import is only
//! worth trusting if it says what it could not convert.

use stfst_core::record::{Case, Flags, Item, Record, SpaceOp};

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum TranslateError {
    #[error("unbalanced brace")]
    UnbalancedBrace,
    #[error("unsupported directive {{{0}}}")]
    Unsupported(String),
    #[error("{0} appears twice in one translation")]
    Conflict(&'static str),
}

/// Parse a Plover translation into a record.
pub fn translate(s: &str) -> Result<Record, TranslateError> {
    let mut flags = Flags::default();
    let mut body: Vec<Item> = Vec::new();
    let mut text = String::new();

    for piece in split_pieces(s)? {
        match piece {
            Piece::Text(t) => text.push_str(&t),
            Piece::Directive(d) => {
                let items = directive(&d, &mut flags)?;
                if !items.is_empty() && !text.is_empty() {
                    body.push(Item::Text(std::mem::take(&mut text)));
                }
                body.extend(items);
            }
        }
    }
    if !text.is_empty() {
        body.push(Item::Text(text));
    }
    // Merge adjacent text, which `{^}`-only directives leave split.
    let mut merged: Vec<Item> = Vec::new();
    for item in body {
        match (merged.last_mut(), item) {
            (Some(Item::Text(prev)), Item::Text(next)) => prev.push_str(&next),
            (_, item) => merged.push(item),
        }
    }
    Ok(Record {
        flags,
        body: merged,
    })
}

enum Piece {
    Text(String),
    Directive(String),
}

fn split_pieces(s: &str) -> Result<Vec<Piece>, TranslateError> {
    let mut out = Vec::new();
    let mut text = String::new();
    let mut chars = s.chars().peekable();
    while let Some(c) = chars.next() {
        match c {
            '\\' => match chars.next() {
                // Plover only unescapes braces, so a lone backslash is literal.
                Some(n @ ('{' | '}')) => text.push(n),
                Some(n) => {
                    text.push('\\');
                    text.push(n);
                }
                None => text.push('\\'),
            },
            '{' => {
                if !text.is_empty() {
                    out.push(Piece::Text(std::mem::take(&mut text)));
                }
                let mut inner = String::new();
                let mut closed = false;
                while let Some(c) = chars.next() {
                    match c {
                        '\\' => match chars.next() {
                            Some(n @ ('{' | '}')) => inner.push(n),
                            Some(n) => {
                                inner.push('\\');
                                inner.push(n);
                            }
                            None => inner.push('\\'),
                        },
                        '}' => {
                            closed = true;
                            break;
                        }
                        c => inner.push(c),
                    }
                }
                if !closed {
                    return Err(TranslateError::UnbalancedBrace);
                }
                out.push(Piece::Directive(inner));
            }
            '}' => return Err(TranslateError::UnbalancedBrace),
            c => text.push(c),
        }
    }
    if !text.is_empty() {
        out.push(Piece::Text(text));
    }
    Ok(out)
}

fn directive(d: &str, flags: &mut Flags) -> Result<Vec<Item>, TranslateError> {
    // Attachment wraps everything else, so peel it first and recurse on the core.
    let (core, attach_l, attach_r) = peel_attach(d);
    if attach_l {
        flags.attach_l = true;
    }
    if attach_r {
        flags.attach_r = true;
    }

    // `{^}` is attachment and nothing else.
    if core.is_empty() {
        return Ok(Vec::new());
    }

    match core {
        "-|" => return set_case(flags, Case::Next).map(|_| Vec::new()),
        ">" => return set_case(flags, Case::LowerNext).map(|_| Vec::new()),
        "*!" => return set_space(flags, SpaceOp::Cancel).map(|_| Vec::new()),
        "*?" => return set_space(flags, SpaceOp::Retro).map(|_| Vec::new()),
        _ => {}
    }

    if let Some(rest) = core.strip_prefix('&') {
        flags.glue = true;
        return Ok(vec![Item::Text(rest.to_string())]);
    }
    if let Some(rest) = core.strip_prefix("~|") {
        flags.carry_cap = true;
        return if rest.is_empty() {
            Ok(Vec::new())
        } else {
            Ok(vec![Item::Text(rest.to_string())])
        };
    }
    if let Some(rest) = core.strip_prefix('#') {
        return Ok(rest
            .split_whitespace()
            .map(|k| Item::Key(k.to_string()))
            .collect());
    }
    if let Some(rest) = core.strip_prefix("PLOVER:") {
        let (name, arg) = rest.split_once(':').unwrap_or((rest, ""));
        return Ok(vec![Item::Cmd {
            name: format!("plover.{}", name.to_ascii_lowercase()),
            arg: arg.to_string(),
        }]);
    }
    if let Some(rest) = core.strip_prefix(':') {
        let (name, arg) = rest.split_once(':').unwrap_or((rest, ""));
        return Ok(vec![Item::Meta {
            name: name.to_string(),
            arg: arg.to_string(),
        }]);
    }

    // A directive whose body is plain text means "this text, with orthography
    // suppressed". We have no orthography at the call site, so it is just text.
    // An interior caret is left unsupported rather than read as text: it is
    // always a transposed attach marker, and reporting it finds the typo.
    if !core.contains(['{', '}', '^']) {
        return Ok(vec![Item::Text(core.to_string())]);
    }

    Err(TranslateError::Unsupported(d.to_string()))
}

/// Strip the leading and trailing `^` that mark attachment.
fn peel_attach(d: &str) -> (&str, bool, bool) {
    // `{^}` joins across an empty record, so its one caret means both sides.
    if d == "^" {
        return ("", true, true);
    }
    let (d, left) = match d.strip_prefix('^') {
        Some(rest) => (rest, true),
        None => (d, false),
    };
    // A trailing `^` attaches, but `{^^}` is attach-both with empty text and
    // `{^}` already consumed its only caret above.
    let (d, right) = match d.strip_suffix('^') {
        Some(rest) => (rest, true),
        None => (d, false),
    };
    (d, left, right)
}

fn set_case(flags: &mut Flags, want: Case) -> Result<(), TranslateError> {
    match flags.case {
        Some(have) if have != want => Err(TranslateError::Conflict("capitalisation")),
        _ => {
            flags.case = Some(want);
            Ok(())
        }
    }
}

fn set_space(flags: &mut Flags, want: SpaceOp) -> Result<(), TranslateError> {
    match flags.space {
        Some(have) if have != want => Err(TranslateError::Conflict("space operation")),
        _ => {
            flags.space = Some(want);
            Ok(())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn r(s: &str) -> Record {
        translate(s).unwrap_or_else(|e| panic!("{s:?}: {e}"))
    }

    #[test]
    fn plain_text_is_plain_text() {
        assert_eq!(r("distinguish").to_string(), "distinguish");
        assert_eq!(r("world? ").to_string(), "world? ");
    }

    #[test]
    fn the_long_tail_is_text_plus_one_attach_flag() {
        assert_eq!(r("{^ing}").to_string(), "[ATTACH_L]ing");
        assert_eq!(r("{cyto^}").to_string(), "[ATTACH_R]cyto");
        assert_eq!(r("{^s}").to_string(), "[ATTACH_L]s");
        assert_eq!(r("{^a^}").to_string(), "[ATTACH_L][ATTACH_R]a");
    }

    #[test]
    fn bare_attach_carries_no_text() {
        assert_eq!(r("{^}").to_string(), "[ATTACH_L][ATTACH_R]");
    }

    #[test]
    fn case_and_glue() {
        assert_eq!(r("{-|}").to_string(), "[CAP_NEXT]");
        assert_eq!(r("{>}").to_string(), "[LOWER_NEXT]");
        assert_eq!(r("{&1}").to_string(), "[GLUE]1");
        assert_eq!(r("{~|'^}").to_string(), "[ATTACH_R][CARRY_CAP]'");
    }

    #[test]
    fn keystrokes_including_combos() {
        assert_eq!(r("{#Return}").to_string(), "[KEY]Return[END]");
        assert_eq!(
            r("{#Control_L(Left)}{^}").to_string(),
            "[ATTACH_L][ATTACH_R][KEY]Control_L(Left)[END]"
        );
        assert_eq!(
            r("{#Return}{#Return}").to_string(),
            "[KEY]Return[END][KEY]Return[END]"
        );
    }

    #[test]
    fn flags_apply_to_the_record_wherever_they_sit() {
        // Plover writes the cap flag last; a record carries it as a leading tag.
        assert_eq!(r("world? {-|}").to_string(), "[CAP_NEXT]world? ");
        assert_eq!(
            r("{#Return}{#Return}{^}{-|}").to_string(),
            "[ATTACH_L][ATTACH_R][CAP_NEXT][KEY]Return[END][KEY]Return[END]"
        );
    }

    #[test]
    fn escaped_braces_are_text() {
        assert_eq!(r("\\{").to_string(), "{");
        assert_eq!(r("{^ ^}\\{\\}").to_string(), "[ATTACH_L][ATTACH_R] {}");
    }

    #[test]
    fn commands_and_metas() {
        assert_eq!(
            r("{PLOVER:SUSPEND}").to_string(),
            "[CMD]plover.suspend[ARG][END]"
        );
        assert_eq!(
            r("{:retro_case:cap_first_word}").to_string(),
            "[META]retro_case[ARG]cap_first_word[END]"
        );
    }

    #[test]
    fn a_nested_brace_is_reported_not_guessed() {
        assert!(translate("though. {{-|}^}").is_err());
        // An interior caret is a transposed attach marker. typey-type spells
        // the "thousand" suffix `{,^000}` where it means `{^,000}`.
        assert!(matches!(
            translate("{,^000}"),
            Err(TranslateError::Unsupported(_))
        ));
        // A pipe is ordinary text, so this one is supported.
        assert_eq!(r("{^|^}").to_string(), "[ATTACH_L][ATTACH_R]|");
    }

}
