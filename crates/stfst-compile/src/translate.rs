//! Plover translation strings to §3 records.
//!
//! Plover mixes text and directives in one band, so text and directives share a
//! namespace and every consumer reimplements an escaping grammar. This is that
//! grammar, written once, on the way in.
//!
//! Anything unrecognised is reported rather than guessed at. The import is only
//! worth trusting if it says what it could not convert.

use stfst_core::record::{Case, Flags, Item, Mods, Record, SpaceOp};

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

/// Plover writes a held key as a wrapper: `Control_L(Left)` is ctrl and Left, and
/// `Alt_L(Tab(Tab))` is alt held over two presses of Tab. A modifier wrapper sets
/// a flag; any other wrapper is a key held over what it wraps, which is that key
/// pressed and then the rest, so it becomes two items.
///
/// Whitespace inside a wrapper is a sequence under the same hold, so
/// `Alt_L(Tab Tab)` means the same as `Alt_L(Tab(Tab))`.
fn keystrokes(token: &str, mods: Mods, out: &mut Vec<Item>) -> Option<()> {
    let held = held_keys(token);
    if held.len() > 1 {
        for word in held {
            keystrokes(word, mods, out)?;
        }
        return Some(());
    }
    let Some((head, rest)) = token.split_once('(') else {
        out.push(Item::Key {
            name: token.to_string(),
            mods,
        });
        return Some(());
    };
    let inner = rest.strip_suffix(')')?;
    let mut mods = mods;
    match modifier(head) {
        Some(tag) => *mods.slot_of(tag)? = true,
        None => out.push(Item::Key {
            name: head.to_string(),
            mods,
        }),
    }
    keystrokes(inner, mods, out)
}

/// Split at whitespace outside any wrapper. `Control_L(a) Control_L(b)` is two
/// keystrokes, and the space in `Alt_L(Tab Tab)` is inside one.
fn held_keys(tokens: &str) -> Vec<&str> {
    let mut out = Vec::new();
    let (mut depth, mut start) = (0usize, None);
    for (i, c) in tokens.char_indices() {
        match c {
            '(' => depth += 1,
            ')' => depth = depth.saturating_sub(1),
            c if c.is_whitespace() && depth == 0 => {
                if let Some(from) = start.take() {
                    out.push(&tokens[from..i]);
                }
                continue;
            }
            _ => {}
        }
        start.get_or_insert(i);
    }
    if let Some(from) = start {
        out.push(&tokens[from..]);
    }
    out
}

/// A Plover modifier name, with the side dropped: it is not a distinction any
/// host here can act on.
fn modifier(name: &str) -> Option<&'static str> {
    let name = name.trim_end_matches("_L").trim_end_matches("_R");
    match name.to_ascii_lowercase().as_str() {
        "control" | "ctrl" => Some("[CTRL]"),
        "shift" => Some("[SHIFT]"),
        "super" | "command" | "cmd" => Some("[SUPER]"),
        "alt" | "option" | "meta" => Some("[ALT]"),
        "fn" => Some("[FN]"),
        _ => None,
    }
}

/// A punctuation mark: joined to the left, and capitalising what follows when it
/// ends a sentence.
fn punctuation(mark: &str, stops: bool, flags: &mut Flags) -> Vec<Item> {
    flags.attach_l = true;
    if stops {
        flags.case = Some(Case::Next);
    }
    vec![Item::Text(mark.to_string())]
}

/// A meta with no argument.
fn meta(name: &str) -> Item {
    Item::Meta {
        name: name.to_string(),
        arg: String::new(),
    }
}

/// A command with no argument.
fn cmd(name: &str) -> Item {
    Item::Cmd {
        name: name.to_string(),
        arg: String::new(),
    }
}

fn directive(d: &str, flags: &mut Flags) -> Result<Vec<Item>, TranslateError> {
    // Attachment wraps everything else, so peel it first and recurse on the core.
    let (core, attach_l, attach_r) = peel_attach(d);
    if attach_l {
        // `{^ing}` joins *and* applies orthography; `{^}`, `{^^}` and `{^a^}` only
        // close the gap. So the caret means the stronger thing exactly when it has
        // text and that text is a suffix rather than an infix: nothing is appended
        // to a word when the insertion is inside it.
        match core.is_empty() || attach_r {
            true => flags.attach_l = true,
            false => flags.ortho_l = true,
        }
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
        "<" => return set_case(flags, Case::UpperNext).map(|_| Vec::new()),
        "*!" => return set_space(flags, SpaceOp::Cancel).map(|_| Vec::new()),
        "*?" => return set_space(flags, SpaceOp::Retro).map(|_| Vec::new()),
        "$" => {
            flags.word_end = true;
            return Ok(Vec::new());
        }
        // The punctuation short forms. Each one is an ordinary record: the mark,
        // joined to the left, and a capital after the ones that end a sentence.
        "," | ":" | ";" => return Ok(punctuation(core, false, flags)),
        "." | "?" | "!" => return Ok(punctuation(core, true, flags)),
        // The retro operations Plover spells with a leading star. These are the
        // three the record language cannot say any other way, because they edit a
        // word that is already written.
        "*-|" => return Ok(vec![meta("retro.cap")]),
        "*<" => return Ok(vec![meta("retro.upper")]),
        "*>" => return Ok(vec![meta("retro.lower")]),
        // Operations on the stroke buffer, which Plover spells as macros.
        "*+" => return Ok(vec![cmd("engine.repeat_stroke")]),
        "*" => return Ok(vec![cmd("engine.toggle_star")]),
        _ => {}
    }

    // `{mode:…}` is persistent host state, so it is a command and not a flag.
    if let Some(rest) = core.strip_prefix("mode:") {
        let (name, arg) = rest.split_once(':').unwrap_or((rest, ""));
        let name = format!("mode.{}", name.to_ascii_lowercase());
        if !stfst_core::host::COMMANDS.contains(&name.as_str()) {
            return Err(TranslateError::Unsupported(d.to_string()));
        }
        return Ok(vec![Item::Cmd {
            name,
            arg: arg.to_string(),
        }]);
    }
    // `{*($c)}`: the previous number as currency.
    if let Some(rest) = core.strip_prefix("*(").and_then(|r| r.strip_suffix(')')) {
        return Ok(vec![Item::Meta {
            name: "retro.currency".into(),
            arg: rest.to_string(),
        }]);
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
        let mut out = Vec::new();
        for token in held_keys(rest) {
            keystrokes(token, Mods::default(), &mut out)
                .ok_or_else(|| TranslateError::Unsupported(d.to_string()))?;
        }
        return Ok(out);
    }
    if let Some(rest) = core.strip_prefix("PLOVER:") {
        let (name, arg) = rest.split_once(':').unwrap_or((rest, ""));
        let name = plugin_command(&name.to_ascii_lowercase());
        if !stfst_core::host::COMMANDS.contains(&name.as_str()) {
            return Err(TranslateError::Unsupported(d.to_string()));
        }
        return Ok(vec![Item::Cmd {
            name,
            arg: arg.to_string(),
        }]);
    }
    // The long forms of what the punctuation above says: `{:case:cap_first_word}`
    // for `{-|}`, and so on. A name outside the tables is reported, because a
    // record that asks for an operation no host implements is worse than a record
    // that fails to import.
    // `{:name:arg}`, where a name starts with a letter. Without that guard,
    // typey-type's `{^:55}` reads as a meta called 55 rather than as text.
    if let Some(rest) = core.strip_prefix(':') {
        if rest.starts_with(|c: char| c.is_ascii_alphabetic() || c == '_') {
            let (name, arg) = rest.split_once(':').unwrap_or((rest, ""));
            return long_form(d, name, arg, flags);
        }
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

/// `{:name:arg}`, which is Plover's spelled-out form for most of the punctuation
/// above.
fn long_form(
    d: &str,
    name: &str,
    arg: &str,
    flags: &mut Flags,
) -> Result<Vec<Item>, TranslateError> {
    let unsupported = || TranslateError::Unsupported(d.to_string());
    match (name, arg) {
        ("attach", "") => {
            flags.attach_l = true;
            flags.attach_r = true;
            Ok(Vec::new())
        }
        ("attach", text) => directive(text, flags),
        ("glue", text) => {
            flags.glue = true;
            Ok(vec![Item::Text(text.to_string())])
        }
        ("carry_capitalize", text) => {
            flags.carry_cap = true;
            Ok(match text.is_empty() {
                true => Vec::new(),
                false => vec![Item::Text(text.to_string())],
            })
        }
        ("word_end", "") => {
            flags.word_end = true;
            Ok(Vec::new())
        }
        ("key_combo", combo) => directive(&format!("#{combo}"), flags),
        ("command", rest) => directive(&format!("PLOVER:{rest}"), flags),
        ("mode", rest) => directive(&format!("mode:{rest}"), flags),
        ("case", kind) => {
            let case = case_name(kind).ok_or_else(unsupported)?;
            set_case(flags, case).map(|_| Vec::new())
        }
        ("retro_case", kind) => Ok(vec![meta(match case_name(kind).ok_or_else(unsupported)? {
            Case::Next => "retro.cap",
            Case::UpperNext => "retro.upper",
            Case::LowerNext => "retro.lower",
        })]),
        // `plover-semantic-suffixes`: inflect the previous word, or rewrite the
        // clause. `retro` is renamed to `clause` here, because this record language
        // already has `retro.cap` and the two are not the same kind of operation.
        ("infl", mode) => Ok(vec![Item::Meta {
            name: "infl".into(),
            arg: mode.to_string(),
        }]),
        ("retro", op) => Ok(vec![Item::Meta {
            name: "clause".into(),
            arg: op.to_string(),
        }]),
        ("retro_currency", format) => Ok(vec![Item::Meta {
            name: "retro.currency".into(),
            arg: format.to_string(),
        }]),
        // Both of these write a fixed punctuation mark, and the difference is only
        // whether a capital follows.
        ("comma", mark) => {
            flags.attach_l = true;
            Ok(vec![Item::Text(mark.to_string())])
        }
        ("stop", mark) => {
            flags.attach_l = true;
            set_case(flags, Case::Next)?;
            Ok(vec![Item::Text(mark.to_string())])
        }
        _ => Err(unsupported()),
    }
}

/// A `{PLOVER:…}` name in this record language.
///
/// The dictionary-stack commands come from `plover-dict-commands` rather than from
/// Plover, and they are about the stack rather than about Plover, so they are named
/// for what they do.
fn plugin_command(name: &str) -> String {
    match name {
        "toggle_dict" => "stack.toggle".into(),
        "solo_dict" => "stack.solo".into(),
        "end_solo_dict" => "stack.end_solo".into(),
        "priority_dict" => "stack.priority".into(),
        other => format!("plover.{other}"),
    }
}

fn case_name(kind: &str) -> Option<Case> {
    match kind {
        "cap_first_word" => Some(Case::Next),
        "upper_first_word" => Some(Case::UpperNext),
        "lower_first_char" => Some(Case::LowerNext),
        _ => None,
    }
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
        assert_eq!(r("{^ing}").to_string(), "ing[ORTHO_L]");
        assert_eq!(r("{cyto^}").to_string(), "cyto[ATTACH_R]");
        assert_eq!(r("{^s}").to_string(), "s[ORTHO_L]");
        // An infix is inserted inside a word, not appended to one, so no rule fires.
        assert_eq!(r("{^a^}").to_string(), "a[ATTACH_L][ATTACH_R]");
    }

    #[test]
    fn bare_attach_carries_no_text() {
        assert_eq!(r("{^}").to_string(), "[ATTACH_L][ATTACH_R]");
    }

    #[test]
    fn the_punctuation_metas_are_ordinary_records() {
        assert_eq!(r("{,}").to_string(), ",[ATTACH_L]");
        assert_eq!(r("{:comma:,}").to_string(), ",[ATTACH_L]");
        assert_eq!(r("{:stop:.}").to_string(), ".[ATTACH_L][CAP_NEXT]");
    }

    #[test]
    fn case_and_glue() {
        assert_eq!(r("{-|}").to_string(), "[CAP_NEXT]");
        assert_eq!(r("{>}").to_string(), "[LOWER_NEXT]");
        assert_eq!(r("{&1}").to_string(), "1[GLUE]");
        assert_eq!(r("{~|'^}").to_string(), "'[ATTACH_R][CARRY_CAP]");
        assert_eq!(r("{:carry_capitalize:'}").to_string(), "'[CARRY_CAP]");
    }

    #[test]
    fn keystrokes_including_combos() {
        assert_eq!(r("{#Return}").to_string(), "[KEY]Return[END]");
        // A modifier wrapper is a flag on the item, not part of the keysym.
        assert_eq!(
            r("{#Control_L(Left)}{^}").to_string(),
            "[KEY]Left[CTRL][END][ATTACH_L][ATTACH_R]"
        );
        assert_eq!(r("{:key_combo:Return}").to_string(), "[KEY]Return[END]");
        // Anything else a wrapper holds is that key pressed, then the rest, and a
        // space inside the wrapper is the same sequence under the same hold.
        assert_eq!(
            r("{#Alt_L(Tab(Tab))}").to_string(),
            "[KEY]Tab[ALT][END][KEY]Tab[ALT][END]"
        );
        assert_eq!(
            r("{#Alt_L(Tab Tab)}").to_string(),
            "[KEY]Tab[ALT][END][KEY]Tab[ALT][END]"
        );
        assert_eq!(
            r("{#Control_L(a) Escape}").to_string(),
            "[KEY]a[CTRL][END][KEY]Escape[END]"
        );
        assert_eq!(
            r("{#Return}{#Return}").to_string(),
            "[KEY]Return[END][KEY]Return[END]"
        );
    }

    #[test]
    fn flags_apply_to_the_record_wherever_they_sit() {
        // Plover writes the cap flag last; a record carries it as a leading tag.
        assert_eq!(r("world? {-|}").to_string(), "world? [CAP_NEXT]");
        assert_eq!(
            r("{#Return}{#Return}{^}{-|}").to_string(),
            "[KEY]Return[END][KEY]Return[END][ATTACH_L][ATTACH_R][CAP_NEXT]"
        );
    }

    #[test]
    fn escaped_braces_are_text() {
        assert_eq!(r("\\{").to_string(), "{");
        assert_eq!(r("{^ ^}\\{\\}").to_string(), " {}[ATTACH_L][ATTACH_R]");
    }

    #[test]
    fn commands_and_metas() {
        assert_eq!(r("{PLOVER:SUSPEND}").to_string(), "[CMD=plover.suspend][END]");
        // The short form and the long form are the same record.
        assert_eq!(r("{*-|}").to_string(), "[META=retro.cap][END]");
        assert_eq!(
            r("{:retro_case:cap_first_word}").to_string(),
            "[META=retro.cap][END]"
        );
        assert_eq!(r("{mode:caps}").to_string(), "[CMD=mode.caps][END]");
        assert_eq!(r("{mode:set_space:_}").to_string(), "[CMD=mode.set_space]_[END]");
        // Operations on the stroke buffer, not on text.
        assert_eq!(r("{*+}").to_string(), "[CMD=engine.repeat_stroke][END]");
        assert_eq!(r("{*}").to_string(), "[CMD=engine.toggle_star][END]");
        // A name in neither table is reported rather than passed on, because a host
        // could only decline it at runtime.
        assert!(translate("{:no_such_meta:x}").is_err());
        assert!(translate("{PLOVER:NO_SUCH_COMMAND}").is_err());
    }

    #[test]
    fn a_caret_with_text_is_an_orthographic_join() {
        // Plover's most-used directive means "join and apply the rules", and
        // `{^}` on its own means only "close the gap".
        assert_eq!(r("{^ing}").to_string(), "ing[ORTHO_L]");
        assert_eq!(r("{^}").to_string(), "[ATTACH_L][ATTACH_R]");
        assert_eq!(r("{^^}").to_string(), "[ATTACH_L][ATTACH_R]");
        assert_eq!(r("{<}").to_string(), "[UPPER_NEXT]");
        assert_eq!(r("{$}").to_string(), "[WORD_END]");
        assert_eq!(r("{.}").to_string(), ".[ATTACH_L][CAP_NEXT]");
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
        assert_eq!(r("{^|^}").to_string(), "|[ATTACH_L][ATTACH_R]");
    }

}
