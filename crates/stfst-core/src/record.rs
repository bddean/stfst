//! The lower tape: an output record.
//!
//! Both tapes are strings, which is the hard constraint of the formalism, so a
//! record is a serialisation and never a nested structure. Tags come from a
//! closed alphabet in canonical order, then the body.
//!
//! Nothing here parses a flat string as the primary path. A tag and a literal
//! `[` are different *symbols* on the tape, and only the printed form confuses
//! them, so the reader hands over symbols and this module works on those.
//! `Record::parse_display` exists for tests and human input, and is the only
//! place the ambiguity can bite.

use std::fmt;
use std::num::NonZeroU8;

/// Flag tags, in canonical tape order. The prelude generator reads this, so the
/// acceptor it emits and the parser here cannot drift apart.
pub const FLAG_TAGS: &[&str] = &[
    "[CANCEL_SPACE]",
    "[RETRO_SPACE]",
    "[ATTACH_L]",
    "[ATTACH_R]",
    "[GLUE]",
    "[CAP_NEXT]",
    "[LOWER_NEXT]",
    "[CARRY_CAP]",
];

/// Delimiters for the open-ended tags, whose values the host parses.
pub const OPEN_TAGS: &[&str] = &["[KEY]", "[CMD]", "[META]", "[ARG]", "[END]"];

/// Largest repetition and largest bound word index. Both are closed and tiny,
/// so they are enumerated symbols rather than delimited values: that keeps them
/// hoistable to canonical position, which a multi-symbol block is not.
pub const MAX_COUNT: u8 = 9;

pub fn repeat_tag(n: u8) -> String {
    format!("[REP{n}]")
}

pub fn copy_tag(n: u8) -> String {
    format!("[COPY{n}]")
}

pub fn is_tag(symbol: &str) -> bool {
    symbol.len() > 1 && symbol.starts_with('[') && symbol.ends_with(']')
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Case {
    /// `{-|}`
    Next,
    /// `{>}`
    LowerNext,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SpaceOp {
    Cancel,
    Retro,
}

/// The closed part of the tag table. `case` and `space` are `Option` rather
/// than pairs of booleans because `[CAP_NEXT][LOWER_NEXT]` is not a state a
/// record can be in.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Flags {
    pub attach_l: bool,
    pub attach_r: bool,
    pub glue: bool,
    pub case: Option<Case>,
    /// `{~|}`: pass the pending capitalisation through to the next word.
    pub carry_cap: bool,
    pub space: Option<SpaceOp>,
    /// `[REP3]`. Repetition is a host operation: an FST cannot copy an
    /// unbounded string, and unrolling it per glyph is the enumeration the
    /// format exists to avoid.
    pub repeat: NonZeroU8,
}

impl Default for Flags {
    fn default() -> Self {
        Flags {
            attach_l: false,
            attach_r: false,
            glue: false,
            case: None,
            carry_cap: false,
            space: None,
            repeat: NonZeroU8::new(1).unwrap(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Item {
    Text(String),
    /// `[KEY]Return[END]`: a keystroke, not text.
    Key(String),
    /// `[CMD]wm.focus[ARG]left[END]`: a request the host may decline.
    Cmd { name: String, arg: String },
    /// `[META]retro.caps[ARG]2[END]`: a declared context operation.
    Meta { name: String, arg: String },
    /// `[COPY1]`: splice in the host's nth bound word without inspecting it.
    Copy(u8),
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Record {
    pub flags: Flags,
    pub body: Vec<Item>,
}

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum RecordError {
    #[error("{0} appears twice")]
    RepeatedTag(String),
    #[error("[CAP_NEXT] and [LOWER_NEXT] cannot both apply")]
    ConflictingCase,
    #[error("[CANCEL_SPACE] and [RETRO_SPACE] cannot both apply")]
    ConflictingSpace,
    #[error("{0} appears after the body, but tags are canonically leading")]
    TagAfterBody(String),
    #[error("unterminated {0}")]
    Unterminated(String),
    #[error("unknown tag {0}")]
    UnknownTag(String),
    #[error("{0} needs [ARG] before [END]")]
    MissingArg(String),
    #[error("{tag} value {value:?} is not a number")]
    NotANumber { tag: String, value: String },
    #[error("[REP]0[END] is not a repetition")]
    ZeroRepeat,
}

impl Record {
    pub fn text(s: impl Into<String>) -> Record {
        Record {
            flags: Flags::default(),
            body: vec![Item::Text(s.into())],
        }
    }

    /// Parse a tape symbol sequence. Each element is either a tag (`[KEY]`) or
    /// a single character of text.
    pub fn parse_symbols(symbols: &[&str]) -> Result<Record, RecordError> {
        let mut flags = Flags::default();
        let mut body = Vec::new();
        let mut i = 0;

        // Leading closed tags.
        while i < symbols.len() {
            let s = symbols[i];
            let set = |slot: &mut bool| -> Result<(), RecordError> {
                if *slot {
                    return Err(RecordError::RepeatedTag(s.to_string()));
                }
                *slot = true;
                Ok(())
            };
            match s {
                "[ATTACH_L]" => set(&mut flags.attach_l)?,
                "[ATTACH_R]" => set(&mut flags.attach_r)?,
                "[GLUE]" => set(&mut flags.glue)?,
                "[CARRY_CAP]" => set(&mut flags.carry_cap)?,
                "[CAP_NEXT]" | "[LOWER_NEXT]" => {
                    let want = if s == "[CAP_NEXT]" {
                        Case::Next
                    } else {
                        Case::LowerNext
                    };
                    match flags.case {
                        None => flags.case = Some(want),
                        Some(have) if have == want => {
                            return Err(RecordError::RepeatedTag(s.to_string()))
                        }
                        Some(_) => return Err(RecordError::ConflictingCase),
                    }
                }
                "[CANCEL_SPACE]" | "[RETRO_SPACE]" => {
                    let want = if s == "[CANCEL_SPACE]" {
                        SpaceOp::Cancel
                    } else {
                        SpaceOp::Retro
                    };
                    match flags.space {
                        None => flags.space = Some(want),
                        Some(have) if have == want => {
                            return Err(RecordError::RepeatedTag(s.to_string()))
                        }
                        Some(_) => return Err(RecordError::ConflictingSpace),
                    }
                }
                _ if counted_tag(s, "REP").is_some() => {
                    if flags.repeat.get() != 1 {
                        return Err(RecordError::RepeatedTag(s.to_string()));
                    }
                    let n = counted_tag(s, "REP").unwrap();
                    flags.repeat = NonZeroU8::new(n).ok_or(RecordError::ZeroRepeat)?;
                }
                _ => break,
            }
            i += 1;
        }

        // Body.
        while i < symbols.len() {
            let s = symbols[i];
            match s {
                "[KEY]" => {
                    let (value, next) = take_until(symbols, i + 1, "[END]", "[KEY]")?;
                    body.push(Item::Key(value));
                    i = next;
                }
                _ if counted_tag(s, "COPY").is_some() => {
                    body.push(Item::Copy(counted_tag(s, "COPY").unwrap()));
                    i += 1;
                }
                "[CMD]" | "[META]" => {
                    let (name, after_name) = take_until(symbols, i + 1, "[ARG]", s)?;
                    let (arg, next) = take_until(symbols, after_name, "[END]", s)?;
                    body.push(if s == "[CMD]" {
                        Item::Cmd { name, arg }
                    } else {
                        Item::Meta { name, arg }
                    });
                    i = next;
                }
                _ if is_tag(s) => {
                    if FLAG_TAGS.contains(&s) || counted_tag(s, "REP").is_some() {
                        return Err(RecordError::TagAfterBody(s.to_string()));
                    }
                    return Err(RecordError::UnknownTag(s.to_string()));
                }
                _ => {
                    // Merge a run of characters into one text item.
                    let mut text = String::new();
                    while i < symbols.len() && !is_tag(symbols[i]) {
                        text.push_str(symbols[i]);
                        i += 1;
                    }
                    body.push(Item::Text(text));
                }
            }
        }

        Ok(Record { flags, body })
    }

    /// The canonical tape form, as symbols.
    pub fn to_symbols(&self) -> Vec<String> {
        let mut out: Vec<String> = Vec::new();
        let f = &self.flags;
        match f.space {
            Some(SpaceOp::Cancel) => out.push("[CANCEL_SPACE]".into()),
            Some(SpaceOp::Retro) => out.push("[RETRO_SPACE]".into()),
            None => {}
        }
        if f.attach_l {
            out.push("[ATTACH_L]".into());
        }
        if f.attach_r {
            out.push("[ATTACH_R]".into());
        }
        if f.glue {
            out.push("[GLUE]".into());
        }
        match f.case {
            Some(Case::Next) => out.push("[CAP_NEXT]".into()),
            Some(Case::LowerNext) => out.push("[LOWER_NEXT]".into()),
            None => {}
        }
        if f.carry_cap {
            out.push("[CARRY_CAP]".into());
        }
        if f.repeat.get() != 1 {
            out.push(repeat_tag(f.repeat.get()));
        }
        for item in &self.body {
            match item {
                Item::Text(t) => out.extend(t.chars().map(|c| c.to_string())),
                Item::Key(k) => {
                    out.push("[KEY]".into());
                    out.extend(k.chars().map(|c| c.to_string()));
                    out.push("[END]".into());
                }
                Item::Copy(n) => out.push(copy_tag(*n)),
                Item::Cmd { name, arg } | Item::Meta { name, arg } => {
                    out.push(
                        if matches!(item, Item::Cmd { .. }) {
                            "[CMD]"
                        } else {
                            "[META]"
                        }
                        .into(),
                    );
                    out.extend(name.chars().map(|c| c.to_string()));
                    out.push("[ARG]".into());
                    out.extend(arg.chars().map(|c| c.to_string()));
                    out.push("[END]".into());
                }
            }
        }
        out
    }

    /// Parse the printed form. Only for tests and human input: a literal `[` in
    /// text is indistinguishable from the start of a tag here, which is exactly
    /// the ambiguity `parse_symbols` does not have.
    pub fn parse_display(s: &str) -> Result<Record, RecordError> {
        let symbols = tokenise_display(s);
        let refs: Vec<&str> = symbols.iter().map(|s| s.as_str()).collect();
        Record::parse_symbols(&refs)
    }
}

/// `[REP3]` -> `Some(3)` when `prefix` is `"REP"`.
fn counted_tag(symbol: &str, prefix: &str) -> Option<u8> {
    symbol
        .strip_prefix('[')?
        .strip_suffix(']')?
        .strip_prefix(prefix)?
        .parse()
        .ok()
}

/// Every tag of the closed alphabet, which is also what the prelude's acceptor
/// enumerates.
pub fn all_tags() -> Vec<String> {
    let mut out: Vec<String> = FLAG_TAGS.iter().map(|s| s.to_string()).collect();
    out.extend(OPEN_TAGS.iter().map(|s| s.to_string()));
    for n in 2..=MAX_COUNT {
        out.push(repeat_tag(n));
    }
    for n in 1..=MAX_COUNT {
        out.push(copy_tag(n));
    }
    out
}

fn tokenise_display(s: &str) -> Vec<String> {
    let owned = all_tags();
    let known: Vec<&str> = owned.iter().map(|s| s.as_str()).collect();
    let mut out = Vec::new();
    let mut rest = s;
    'outer: while !rest.is_empty() {
        if rest.starts_with('[') {
            for tag in &known {
                if let Some(tail) = rest.strip_prefix(*tag) {
                    out.push((*tag).to_string());
                    rest = tail;
                    continue 'outer;
                }
            }
        }
        let c = rest.chars().next().unwrap();
        out.push(c.to_string());
        rest = &rest[c.len_utf8()..];
    }
    out
}

fn take_until(
    symbols: &[&str],
    mut i: usize,
    terminator: &str,
    opener: &str,
) -> Result<(String, usize), RecordError> {
    let mut value = String::new();
    while i < symbols.len() {
        if symbols[i] == terminator {
            return Ok((value, i + 1));
        }
        if is_tag(symbols[i]) {
            break;
        }
        value.push_str(symbols[i]);
        i += 1;
    }
    if terminator == "[ARG]" {
        Err(RecordError::MissingArg(opener.to_string()))
    } else {
        Err(RecordError::Unterminated(opener.to_string()))
    }
}

impl fmt::Display for Record {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        for s in self.to_symbols() {
            f.write_str(&s)?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn syms(v: &[&str]) -> Vec<&'static str> {
        // helper so tests read as tape symbol lists
        v.iter().map(|s| Box::leak(s.to_string().into_boxed_str()) as &'static str).collect()
    }

    #[test]
    fn the_doc_example_round_trips() {
        let r = Record::parse_symbols(&syms(&["[ATTACH_L]", "[CAP_NEXT]", "i", "n", "g"])).unwrap();
        assert!(r.flags.attach_l);
        assert_eq!(r.flags.case, Some(Case::Next));
        assert_eq!(r.body, vec![Item::Text("ing".into())]);
        assert_eq!(r.to_string(), "[ATTACH_L][CAP_NEXT]ing");
    }

    #[test]
    fn a_literal_bracket_in_text_is_just_text() {
        // emily-symbols emits "[" as a glyph, so this must not read as a tag
        let r = Record::parse_symbols(&syms(&["["])).unwrap();
        assert_eq!(r.body, vec![Item::Text("[".into())]);
    }

    #[test]
    fn keystrokes_and_commands_parse() {
        let r = Record::parse_symbols(&syms(&["[KEY]", "T", "a", "b", "[END]"])).unwrap();
        assert_eq!(r.body, vec![Item::Key("Tab".into())]);

        let r = Record::parse_symbols(&syms(&[
            "[CMD]", "w", "m", ".", "f", "[ARG]", "l", "e", "f", "t", "[END]",
        ]))
        .unwrap();
        assert_eq!(
            r.body,
            vec![Item::Cmd {
                name: "wm.f".into(),
                arg: "left".into()
            }]
        );
    }

    #[test]
    fn repeat_is_a_flag_on_the_record() {
        let r = Record::parse_symbols(&syms(&["[REP3]", "!"])).unwrap();
        assert_eq!(r.flags.repeat.get(), 3);
        assert_eq!(r.body, vec![Item::Text("!".into())]);
        assert_eq!(r.to_string(), "[REP3]!");
    }

    #[test]
    fn conflicting_case_is_rejected() {
        assert_eq!(
            Record::parse_symbols(&syms(&["[CAP_NEXT]", "[LOWER_NEXT]", "a"])),
            Err(RecordError::ConflictingCase)
        );
    }

    #[test]
    fn a_trailing_flag_is_rejected_because_order_is_canonical() {
        assert_eq!(
            Record::parse_symbols(&syms(&["a", "[CAP_NEXT]"])),
            Err(RecordError::TagAfterBody("[CAP_NEXT]".into()))
        );
    }

    #[test]
    fn unterminated_open_tags_are_rejected() {
        assert_eq!(
            Record::parse_symbols(&syms(&["[KEY]", "T", "a", "b"])),
            Err(RecordError::Unterminated("[KEY]".into()))
        );
        assert_eq!(
            Record::parse_symbols(&syms(&["[CMD]", "x", "[END]"])),
            Err(RecordError::MissingArg("[CMD]".into()))
        );
    }

    #[test]
    fn display_form_round_trips() {
        for s in [
            "[ATTACH_L][CAP_NEXT]ing",
            "[KEY]Return[END]",
            "[CMD]wm.focus[ARG]left[END]",
            "[REP3]!",
            "[COPY1]",
            "[GLUE]a",
            "plain text",
        ] {
            let r = Record::parse_display(s).unwrap();
            assert_eq!(r.to_string(), s, "{s} did not round-trip");
        }
    }
}
