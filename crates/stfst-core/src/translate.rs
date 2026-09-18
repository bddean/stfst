//! Plover translation strings to §3 records.
//!
//! Plover mixes text and directives in one band, so text and directives share a
//! namespace and every consumer reimplements an escaping grammar. This is that
//! grammar, written once, on the way in.
//!
//! Anything unrecognised is reported rather than guessed at. The import is only
//! worth trusting if it says what it could not convert.

use crate::record::{Case, Flags, Item, Mods, Record, SpaceOp};

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
    // Each atom, with how it joins and whether it was written as plain text. A
    // record concatenates its items with no gaps, and Plover's atoms are separate
    // actions with a space between them, so the gaps have to be put in here. Doing
    // that needs every atom's join at once, hence the list.
    let mut atoms: Vec<(Vec<Item>, Join, bool)> = Vec::new();
    // Which atom set the pending capital, so a later atom in the same record can
    // spend it.
    let mut case_at: Option<usize> = None;
    for piece in split_pieces(s)? {
        match piece {
            // A text atom that is only whitespace writes nothing. Plover splits an
            // atom into words and a blank one has none, so `{-|} {-|}` writes one
            // capital and not a stray space. Whitespace *inside* an atom survives,
            // and so does a directive whose core is a space: `{^ ^}` still writes one.
            Piece::Text(t) if t.trim().is_empty() => {}
            Piece::Text(t) => atoms.push((vec![Item::Text(t)], Join::default(), true)),
            Piece::Directive(d) => {
                let had_case = flags.case.is_some();
                let (items, join) = directive(&d, &mut flags)?;
                if !had_case && flags.case.is_some() {
                    case_at = Some(atoms.len());
                }
                atoms.push((items, join, false));
            }
        }
    }

    // A pending capital applies to the next *word*, and the next word can be in this
    // same record: `{.}so` is ". So" and not ". so" with the capital left over for
    // whatever comes next. The record language has one case flag for the whole
    // record, so where the word is here the capital is spent here.
    if let Some(at) = case_at {
        if let Some((items, _, _)) = atoms[at + 1..].iter_mut().find(|(i, _, _)| writes_word(i)) {
            if let Some(Item::Text(t)) = items.iter_mut().find(|i| matches!(i, Item::Text(_))) {
                *t = recase(t, flags.case.expect("set by the atom above"));
            }
            // Spent even where there was no letter to spend it on, which is what
            // Plover does: `{!})` leaves the word after it lowercase.
            flags.case = None;
        }
    }

    // Attachment is the one positional property. The record's left edge is the
    // first atom's and its right edge is the last atom's, where "atom" means one
    // that writes a word or says something about joining. A keystroke or a command
    // passes both along instead of deciding either, which is why
    // `{~|()^}{#Left}` still attaches on the right.
    let speaks = |(items, join, _): &&(Vec<Item>, Join, bool)| {
        writes_word(items) || join.left || join.ortho || join.right
    };
    if let Some((_, join, _)) = atoms.iter().find(speaks) {
        flags.attach_l = join.left;
        flags.ortho_l = join.ortho;
    }
    if let Some((_, join, _)) = atoms.iter().rev().find(speaks) {
        flags.attach_r = join.right;
    }

    // A word gap at either end of a text atom is the record's business and not the
    // text's, so Plover's word splitting drops it and the gap logic below puts back
    // the ones that belong between atoms. Whitespace *inside* an atom survives, and
    // so does a directive that spells a space: `{^ ^}` writes one.
    for (items, _, plain) in atoms.iter_mut() {
        if let (true, Some(Item::Text(t))) = (*plain, items.first_mut()) {
            *t = t.trim().to_string();
        }
    }

    // Now the gaps. Two atoms that both write and neither of which attaches have a
    // word boundary between them, and the record has to spell it: `{,}and` is
    // "run, and" and not "run,and".
    let mut body: Vec<Item> = Vec::new();
    let mut gap_owed = false;
    for (items, join, _) in atoms {
        // A gap is a boundary between *words*, so only an atom that writes one has
        // it. A keystroke or a command is not a word, and two in a row are not two
        // words with a space between: `{#Escape}{PLOVER:…}` writes nothing.
        if !writes_word(&items) {
            // Its attachment still speaks for the gap it sits in: `{,}{^}and` closes
            // that one.
            if join.right {
                gap_owed = false;
            }
            body.extend(items);
            continue;
        }
        if gap_owed && !join.left && !join.ortho {
            body.push(Item::Text(" ".to_string()));
        }
        gap_owed = !join.right;
        body.extend(items);
    }

    // One item per atom, deliberately unmerged. Merging them printed the same
    // record and meant a different one: `{^ia}{,}` became the single word "ia," and
    // an orthographic join over that finds no rule, so `run` and `ia` came out
    // "runia" where Plover writes "runnia". The join applies to the first word, so
    // the first word has to be its own item.
    body.retain(|item| !matches!(item, Item::Text(t) if t.is_empty()));
    Ok(Record {
        flags,
        body,
        after: None,
    })
}

/// Apply a case to the first letter of some text, which is what a pending capital
/// does when the word it applies to is in the same record.
fn recase(text: &str, case: Case) -> String {
    let mut out = String::new();
    let mut done = false;
    for c in text.chars() {
        match !done && c.is_alphabetic() {
            true => {
                done = true;
                match case {
                    Case::Next | Case::UpperNext => out.extend(c.to_uppercase()),
                    Case::LowerNext => out.extend(c.to_lowercase()),
                }
            }
            false => out.push(c),
        }
    }
    out
}

/// Whether these items put a word on screen. A keystroke and a command do not, so
/// they take no space beside them and decide no boundary.
fn writes_word(items: &[Item]) -> bool {
    items
        .iter()
        .any(|i| matches!(i, Item::Text(_) | Item::Copy(_) | Item::Glyph(_)))
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
fn punctuation(mark: &str, stops: bool, flags: &mut Flags, join: &mut Join) -> Vec<Item> {
    join.left = true;
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

/// How one directive joins, which is the only thing about it that depends on
/// *where* it sits.
///
/// Plover's attachment is per atom, and a record's is per record, so the two only
/// line up at the edges. The left boundary of a translation is the first atom's
/// and the right boundary is the last atom's; an attach marker between two atoms
/// closes a gap *inside* the translation and says nothing about either end. So
/// `co{^}` is "co" joined to what follows and not to what precedes, which is why
/// `x co{^} y` writes "x coy". Reading every atom's caret as the record's would
/// make it "xcoy".
#[derive(Debug, Default, Clone, Copy)]
struct Join {
    /// `{^…}`: close the gap.
    left: bool,
    /// `{^ing}`: close it *and* apply the orthography rules.
    ortho: bool,
    /// `{…^}`: close the gap on the right.
    right: bool,
}

fn directive(d: &str, flags: &mut Flags) -> Result<(Vec<Item>, Join), TranslateError> {
    // Attachment wraps everything else, so peel it first and read the core alone.
    let (core, attach_l, attach_r) = peel_attach(d);
    // `{^ing}` joins *and* applies orthography; `{^}`, `{^^}` and `{^a^}` only
    // close the gap. So the caret means the stronger thing exactly when it has text
    // and that text is a suffix rather than an infix: nothing is appended to a word
    // when the insertion is inside it.
    let plain = core.is_empty() || attach_r;
    let mut join = Join {
        left: attach_l && plain,
        ortho: attach_l && !plain,
        right: attach_r,
    };
    // With attachment peeled, the core is text. Plover matches its whole-atom metas
    // only on an atom that has no carets, so `{^*^}` is an asterisk and not the star
    // command, and `{^-|^}` is two characters and not a capital. The exception is the
    // carry-capitalisation prefix, which does compose with attachment: it is how
    // `{^~|'^}` writes an apostrophe inside a word without spending the capital.
    //
    // `join` is threaded like `flags`, and for the same reason: `{:attach}` is a core
    // that joins, so the join is an output of reading the core and not only of peeling.
    let items = match (attach_l || attach_r) && !core.is_empty() && !core.starts_with("~|") {
        true => vec![Item::Text(core.to_string())],
        false => core_items(d, core, flags, &mut join)?,
    };
    Ok((items, join))
}

/// What a directive says once its attachment is off.
fn core_items(
    d: &str,
    core: &str,
    flags: &mut Flags,
    join: &mut Join,
) -> Result<Vec<Item>, TranslateError> {
    // `{^}` is attachment and nothing else.
    if core.is_empty() {
        return Ok(Vec::new());
    }

    match core {
        "-|" => {
            set_case(flags, Case::Next);
            return Ok(Vec::new());
        }
        ">" => {
            set_case(flags, Case::LowerNext);
            return Ok(Vec::new());
        }
        "<" => {
            set_case(flags, Case::UpperNext);
            return Ok(Vec::new());
        }
        "*!" => return set_space(flags, SpaceOp::Cancel).map(|_| Vec::new()),
        "*?" => return set_space(flags, SpaceOp::Retro).map(|_| Vec::new()),
        "$" => {
            flags.word_end = true;
            return Ok(Vec::new());
        }
        // The punctuation short forms. Each one is an ordinary record: the mark,
        // joined to the left, and a capital after the ones that end a sentence.
        "," | ":" | ";" => return Ok(punctuation(core, false, flags, join)),
        "." | "?" | "!" => return Ok(punctuation(core, true, flags, join)),
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
    // Case-insensitive because Plover's meta names are, and `commands.json` writes
    // `{MODE:CAPS}`. Missing that read it as the plain-text fallthrough below, so
    // the entry imported and set no mode: the cross-check is what found it.
    if let Some(rest) = strip_prefix_ci(core, "mode:") {
        let (name, arg) = rest.split_once(':').unwrap_or((rest, ""));
        let name = format!("mode.{}", name.to_ascii_lowercase());
        if !crate::host::COMMANDS.contains(&name.as_str()) {
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
    if let Some(rest) = strip_prefix_ci(core, "PLOVER:") {
        let (name, arg) = rest.split_once(':').unwrap_or((rest, ""));
        let name = plugin_command(&name.to_ascii_lowercase());
        if !crate::host::COMMANDS.contains(&name.as_str()) {
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
            return long_form(d, name, arg, flags, join);
        }
    }

    // Anything left is a brace atom naming no operation, and Plover writes nothing
    // at all for one: `{pgh}`, `{--}` and `{...}` are dead entries in typey-type that
    // type nothing there today. Reading the core as text was this module guessing,
    // which is the thing it says it does not do, and reproducing Plover's silence
    // would be the other kind of guess. So it is reported, and `stfst import
    // --report` names all thirty-one.
    Err(TranslateError::Unsupported(d.to_string()))
}

/// A prefix match that ignores case, which is what Plover's meta lookup does.
fn strip_prefix_ci<'a>(s: &'a str, prefix: &str) -> Option<&'a str> {
    s.get(..prefix.len())
        .filter(|head| head.eq_ignore_ascii_case(prefix))
        .map(|_| &s[prefix.len()..])
}

/// `{:name:arg}`, which is Plover's spelled-out form for most of the punctuation
/// above.
fn long_form(
    d: &str,
    name: &str,
    arg: &str,
    flags: &mut Flags,
    join: &mut Join,
) -> Result<Vec<Item>, TranslateError> {
    let unsupported = || TranslateError::Unsupported(d.to_string());
    match (name, arg) {
        ("attach", "") => {
            join.left = true;
            join.right = true;
            Ok(Vec::new())
        }
        ("attach", text) => {
            let (items, inner) = directive(text, flags)?;
            *join = inner;
            Ok(items)
        }
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
        ("key_combo", combo) => {
            let (items, inner) = directive(&format!("#{combo}"), flags)?;
            *join = inner;
            Ok(items)
        }
        ("command", rest) => {
            let (items, inner) = directive(&format!("PLOVER:{rest}"), flags)?;
            *join = inner;
            Ok(items)
        }
        ("mode", rest) => {
            let (items, inner) = directive(&format!("mode:{rest}"), flags)?;
            *join = inner;
            Ok(items)
        }
        ("case", kind) => {
            let case = case_name(kind).ok_or_else(unsupported)?;
            set_case(flags, case);
            Ok(Vec::new())
        }
        ("retro_case", kind) => Ok(vec![meta(match case_name(kind).ok_or_else(unsupported)? {
            Case::Next => "retro.cap",
            Case::UpperNext => "retro.upper",
            Case::LowerNext => "retro.lower",
        })]),
        // `plover-semantic-suffixes`: inflect the previous word. Its clause
        // rewrites, `{:retro:past}` and the rest, are deliberately not here, so an
        // entry that asks for one is reported rather than carried: see
        // docs/parity.md.
        ("infl", mode) => Ok(vec![Item::Meta {
            name: "infl".into(),
            arg: mode.to_string(),
        }]),
        ("retro_currency", format) => Ok(vec![Item::Meta {
            name: "retro.currency".into(),
            arg: format.to_string(),
        }]),
        // Both of these write a fixed punctuation mark, and the difference is only
        // whether a capital follows.
        ("comma", mark) => {
            join.left = true;
            Ok(vec![Item::Text(mark.to_string())])
        }
        ("stop", mark) => {
            join.left = true;
            set_case(flags, Case::Next);
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

/// The last case a translation names is the one Plover spends, so a second one
/// replaces the first rather than conflicting with it.
fn set_case(flags: &mut Flags, want: Case) {
    flags.case = Some(want);
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
        // A word gap at the record's edge belongs to the record, so it is not text:
        // Plover writes "run world? y" and not "run world?  y".
        assert_eq!(r("world? ").to_string(), "world?");
        assert_eq!(r(" gradients").to_string(), "gradients");
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
        // A keystroke is not a word, so it decides neither edge and passes the
        // caret's attachment through to both.
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
        assert_eq!(r("world? {-|}").to_string(), "world?[CAP_NEXT]");
        assert_eq!(
            r("{#Return}{#Return}{^}{-|}").to_string(),
            "[KEY]Return[END][KEY]Return[END][ATTACH_L][ATTACH_R][CAP_NEXT]"
        );
    }

    #[test]
    fn an_attach_marker_between_atoms_is_not_the_record_edge() {
        // Plover's attachment is per atom and a record's is per record, so only the
        // first and last atom reach the edges. Every line here was measured against
        // Plover: "x co{^} y" writes "x coy", so the caret in the middle joins to
        // the right and reading it as the record's would write "xcoy".
        assert_eq!(r("co{^}").to_string(), "co[ATTACH_R]");
        assert_eq!(r("{^}").to_string(), "[ATTACH_L][ATTACH_R]");
        // Punctuation attaches the same way: "x foo{.} y" writes "x foo. Y".
        assert_eq!(r("foo{.}").to_string(), "foo.[CAP_NEXT]");
        assert_eq!(r("{.}").to_string(), ".[ATTACH_L][CAP_NEXT]");
        // A trailing caret still reaches the edge when it is the last thing said.
        assert_eq!(r("{^ing}").to_string(), "ing[ORTHO_L]");
        assert_eq!(r("{^a^}").to_string(), "a[ATTACH_L][ATTACH_R]");
    }

    #[test]
    fn escaped_braces_are_text() {
        assert_eq!(r("\\{").to_string(), "{");
        // The `{^ ^}` is first, so its left caret is the record's; its right caret
        // joins to the text after it, which is inside the record. Plover writes
        // "x {} y".
        assert_eq!(r("{^ ^}\\{\\}").to_string(), " {}[ATTACH_L]");
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
