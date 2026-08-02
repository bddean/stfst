//! The lower tape: an output record.
//!
//! Both tapes are strings, which is the hard constraint of the formalism, so a
//! record is a serialisation and never a nested structure. The body comes first,
//! then the closed tags in canonical order.
//!
//! The tags come last because the keys that decide them come last. A record that
//! led with its flags could not be written until the whole outline had been
//! read, so a forward lookup had to guess the flags and carry every guess: 96
//! live paths after one key, measured in `docs/latency.md`. Nothing else prefers
//! flags first, because this parser takes them in any order anyway.
//!
//! Nothing here parses a flat string as the primary path. A tag and a literal
//! `[` are different *symbols* on the tape, and only the printed form confuses
//! them, so the reader hands over symbols and this module works on those.
//! `Record::parse_display` exists for tests and human input, and is the only
//! place the ambiguity can bite.

use std::fmt;
use std::num::NonZeroU8;

use crate::context::{State, STATE_CLOSE, STATE_OPEN};
use crate::window::{sig_char, Class, DROP};

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

/// Delimiters for the open-ended tags, whose values the host parses. `[STATE]`
/// closes with the same `[END]`, and what it delimits is not part of the record;
/// see `Emission`.
pub const OPEN_TAGS: &[&str] = &["[KEY]", "[CMD]", "[META]", "[ARG]", "[STATE]", "[END]"];

/// Modifier tags, in canonical order, valid only inside a `[KEY]` item.
///
/// A keysym is open-ended and delimits, but the modifiers held with it are five
/// values and closed, so §3's rule enumerates them. Putting them in the item
/// rather than in the keysym text is also what keeps a keysym free to be any
/// character: `ctrl+plus` needs no escape if `+` never delimits anything.
///
/// This order is steno order for the keys that set them in `emily-modifiers`
/// (`-F -P -L -T -D`), so a dictionary emits each one where it reads it.
pub const MOD_TAGS: &[&str] = &["[CTRL]", "[SHIFT]", "[SUPER]", "[ALT]", "[FN]"];

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

/// `[REPLACE1]`: this record supersedes the previous translation unit rather
/// than following it, which is how a retro-modifier stroke rewrites what its
/// base stroke wrote. A multi-stroke entry needs no such tag, because its length
/// already says how much it replaces; a one-stroke fold over a carried state has
/// no length to say it with.
pub fn replace_tag(n: u8) -> String {
    format!("[REPLACE{n}]")
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
    /// `[REPLACE1]`: how many translation units this one supersedes. `None`
    /// appends, which is what every dictionary without a carried state does.
    pub replace: Option<NonZeroU8>,
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
            replace: None,
        }
    }
}

/// The modifiers held with a keystroke. Five orthogonal booleans, so every
/// combination is legal and none is a state to rule out.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct Mods {
    pub ctrl: bool,
    pub shift: bool,
    pub super_: bool,
    pub alt: bool,
    /// Honoured only where the host can synthesise it.
    pub fn_: bool,
}

impl Mods {
    pub fn is_empty(&self) -> bool {
        *self == Mods::default()
    }

    /// The flag a modifier tag sets, for a caller that has the tag and not the
    /// field. `None` when the tag is not a modifier.
    pub fn slot_of(&mut self, tag: &str) -> Option<&mut bool> {
        self.slot(tag)
    }

    fn slot(&mut self, tag: &str) -> Option<&mut bool> {
        match tag {
            "[CTRL]" => Some(&mut self.ctrl),
            "[SHIFT]" => Some(&mut self.shift),
            "[SUPER]" => Some(&mut self.super_),
            "[ALT]" => Some(&mut self.alt),
            "[FN]" => Some(&mut self.fn_),
            _ => None,
        }
    }

    fn tags(&self) -> impl Iterator<Item = &'static str> + '_ {
        MOD_TAGS
            .iter()
            .copied()
            .filter(move |t| match *t {
                "[CTRL]" => self.ctrl,
                "[SHIFT]" => self.shift,
                "[SUPER]" => self.super_,
                "[ALT]" => self.alt,
                _ => self.fn_,
            })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Item {
    Text(String),
    /// `[KEY]Return[SHIFT][END]`: a keystroke, not text.
    Key { name: String, mods: Mods },
    /// `[CMD]wm.focus[ARG]left[END]`: a request the host may decline.
    Cmd { name: String, arg: String },
    /// `[META]retro.caps[ARG]2[END]`: a declared context operation.
    Meta { name: String, arg: String },
    /// `[COPY1]`: splice in the host's nth bound word without inspecting it.
    Copy(u8),
    /// `[DROP]`: the next character of the window, not written.
    Drop,
    /// `[LETTER]`: the next character of the window, in this class. What a rule
    /// writes when it is copying text it was given rather than text of its own,
    /// and where it changes the class, the host recases the character. See
    /// `window`.
    Glyph(Class),
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
    #[error("{0} appears after a flag tag, but the flags come last")]
    BodyAfterFlags(String),
    #[error("unterminated {0}")]
    Unterminated(String),
    #[error("unknown tag {0}")]
    UnknownTag(String),
    #[error("{0} needs [ARG] before [END]")]
    MissingArg(String),
    #[error("{0} counts nothing")]
    ZeroCount(String),
    #[error("unterminated [STATE]")]
    UnterminatedState,
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
        let mut body = Vec::new();
        let i = Self::parse_body(symbols, &mut body)?;
        let flags = Self::parse_flags(&symbols[i..])?;
        Ok(Record { flags, body })
    }

    /// The trailing closed tags.
    fn parse_flags(symbols: &[&str]) -> Result<Flags, RecordError> {
        let mut flags = Flags::default();
        for &s in symbols {
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
                _ if counted_tag(s, "REPLACE").is_some() => {
                    if flags.replace.is_some() {
                        return Err(RecordError::RepeatedTag(s.to_string()));
                    }
                    let n = counted_tag(s, "REPLACE").unwrap();
                    flags.replace = Some(NonZeroU8::new(n).ok_or(RecordError::ZeroCount(s.into()))?);
                }
                _ if counted_tag(s, "REP").is_some() => {
                    if flags.repeat.get() != 1 {
                        return Err(RecordError::RepeatedTag(s.to_string()));
                    }
                    let n = counted_tag(s, "REP").unwrap();
                    flags.repeat =
                        NonZeroU8::new(n).ok_or(RecordError::ZeroCount(s.to_string()))?;
                }
                _ => return Err(RecordError::BodyAfterFlags(s.to_string())),
            }
        }
        Ok(flags)
    }

    /// The body, up to the first flag tag. Returns where the flags start.
    fn parse_body(symbols: &[&str], body: &mut Vec<Item>) -> Result<usize, RecordError> {
        let mut i = 0;
        while i < symbols.len() && !is_flag(symbols[i]) {
            let s = symbols[i];
            match s {
                "[KEY]" => {
                    let (name, mods, next) = take_key(symbols, i + 1)?;
                    body.push(Item::Key { name, mods });
                    i = next;
                }
                DROP => {
                    body.push(Item::Drop);
                    i += 1;
                }
                _ if Class::from_tag(s).is_some() => {
                    body.push(Item::Glyph(Class::from_tag(s).unwrap()));
                    i += 1;
                }
                _ if sig_char(s).is_some() => {
                    // A character the grammar named reaches the output as itself,
                    // so it is text and takes no substitution slot.
                    body.push(Item::Text(sig_char(s).unwrap().to_string()));
                    i += 1;
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
                _ if is_tag(s) => return Err(RecordError::UnknownTag(s.to_string())),
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
        Ok(i)
    }

    /// The canonical tape form, as symbols.
    pub fn to_symbols(&self) -> Vec<String> {
        let mut out: Vec<String> = Vec::new();
        for item in &self.body {
            match item {
                Item::Text(t) => out.extend(t.chars().map(|c| c.to_string())),
                Item::Key { name, mods } => {
                    out.push("[KEY]".into());
                    out.extend(name.chars().map(|c| c.to_string()));
                    out.extend(mods.tags().map(str::to_string));
                    out.push("[END]".into());
                }
                Item::Copy(n) => out.push(copy_tag(*n)),
                Item::Glyph(class) => out.push(class.tag().to_string()),
                Item::Drop => out.push(DROP.to_string()),
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
        if let Some(n) = f.replace {
            out.push(replace_tag(n.get()));
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

/// One whole output tape: the record, and the state a Mealy dictionary carries
/// forward to its next step.
///
/// The state is not output, so it is not part of the record: nothing that
/// formats text ever sees it, and the host stores it without reading it. It sits
/// last on the tape for the same reason the flags do. The whole stroke decides
/// it, so a tape that led with it could not be written until the stroke had been
/// read, and every path would carry a guess.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Emission {
    pub record: Record,
    pub state: Option<State>,
}

impl Emission {
    pub fn parse_symbols(symbols: &[&str]) -> Result<Emission, RecordError> {
        let (head, state) = split_state(symbols)?;
        Ok(Emission {
            record: Record::parse_symbols(head)?,
            state,
        })
    }

    pub fn parse_display(s: &str) -> Result<Emission, RecordError> {
        let symbols = tokenise_display(s);
        let refs: Vec<&str> = symbols.iter().map(|s| s.as_str()).collect();
        Emission::parse_symbols(&refs)
    }

    pub fn to_symbols(&self) -> Vec<String> {
        let mut out = self.record.to_symbols();
        if let Some(state) = &self.state {
            out.extend(state.to_symbols());
        }
        out
    }
}

impl fmt::Display for Emission {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        for s in self.to_symbols() {
            f.write_str(&s)?;
        }
        Ok(())
    }
}

/// Split a trailing state block off a tape. `[STATE]` cannot appear inside an
/// item, so the last one opens the block and the tape has to end with its
/// `[END]`.
fn split_state<'a>(symbols: &'a [&'a str]) -> Result<(&'a [&'a str], Option<State>), RecordError> {
    let Some(at) = symbols.iter().rposition(|s| *s == STATE_OPEN) else {
        return Ok((symbols, None));
    };
    if symbols.last() != Some(&STATE_CLOSE) {
        return Err(RecordError::UnterminatedState);
    }
    let content: String = symbols[at + 1..symbols.len() - 1].concat();
    Ok((&symbols[..at], Some(State(content))))
}

/// A closed tag, which is to say one that belongs after the body.
fn is_flag(symbol: &str) -> bool {
    FLAG_TAGS.contains(&symbol)
        || counted_tag(symbol, "REP").is_some()
        || counted_tag(symbol, "REPLACE").is_some()
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
    out.extend(MOD_TAGS.iter().map(|s| s.to_string()));
    for n in 2..=MAX_COUNT {
        out.push(repeat_tag(n));
    }
    for n in 1..=MAX_COUNT {
        out.push(copy_tag(n));
    }
    for n in 1..=MAX_COUNT {
        out.push(replace_tag(n));
    }
    out.extend(Class::ALL.iter().map(|c| c.tag().to_string()));
    out.push(DROP.to_string());
    out.push(crate::window::WINDOW_OPEN.to_string());
    out.push(crate::window::WINDOW_CLOSE.to_string());
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

/// A keystroke item: the keysym, then its modifiers, then `[END]`. The keysym is
/// text and the modifiers are tags, so the two need no delimiter between them.
fn take_key(symbols: &[&str], mut i: usize) -> Result<(String, Mods, usize), RecordError> {
    let mut name = String::new();
    while i < symbols.len() && !is_tag(symbols[i]) {
        name.push_str(symbols[i]);
        i += 1;
    }
    let mut mods = Mods::default();
    while i < symbols.len() {
        if symbols[i] == "[END]" {
            return Ok((name, mods, i + 1));
        }
        match mods.slot(symbols[i]) {
            Some(slot) if !*slot => *slot = true,
            Some(_) => return Err(RecordError::RepeatedTag(symbols[i].to_string())),
            None => break,
        }
        i += 1;
    }
    Err(RecordError::Unterminated("[KEY]".into()))
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
        let r = Record::parse_symbols(&syms(&["i", "n", "g", "[ATTACH_L]", "[CAP_NEXT]"])).unwrap();
        assert!(r.flags.attach_l);
        assert_eq!(r.flags.case, Some(Case::Next));
        assert_eq!(r.body, vec![Item::Text("ing".into())]);
        assert_eq!(r.to_string(), "ing[ATTACH_L][CAP_NEXT]");
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
        assert_eq!(
            r.body,
            vec![Item::Key {
                name: "Tab".into(),
                mods: Mods::default()
            }]
        );

        // Modifiers are tags inside the item, so a keysym never needs escaping.
        let r = Record::parse_symbols(&syms(&["[KEY]", "+", "[CTRL]", "[ALT]", "[END]"]))
            .unwrap();
        assert_eq!(
            r.body,
            vec![Item::Key {
                name: "+".into(),
                mods: Mods {
                    ctrl: true,
                    alt: true,
                    ..Mods::default()
                }
            }]
        );
        assert_eq!(r.to_string(), "[KEY]+[CTRL][ALT][END]");

        // Canonical order is fixed, so the parser takes them in any order and the
        // serialiser puts them back.
        let r = Record::parse_display("[KEY]c[ALT][CTRL][END]").unwrap();
        assert_eq!(r.to_string(), "[KEY]c[CTRL][ALT][END]");
        assert_eq!(
            Record::parse_display("[KEY]c[CTRL][CTRL][END]"),
            Err(RecordError::RepeatedTag("[CTRL]".into()))
        );

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
        let r = Record::parse_symbols(&syms(&["!", "[REP3]"])).unwrap();
        assert_eq!(r.flags.repeat.get(), 3);
        assert_eq!(r.body, vec![Item::Text("!".into())]);
        assert_eq!(r.to_string(), "![REP3]");
    }

    #[test]
    fn conflicting_case_is_rejected() {
        assert_eq!(
            Record::parse_symbols(&syms(&["a", "[CAP_NEXT]", "[LOWER_NEXT]"])),
            Err(RecordError::ConflictingCase)
        );
    }

    #[test]
    fn a_leading_flag_is_rejected_because_order_is_canonical() {
        assert_eq!(
            Record::parse_symbols(&syms(&["[CAP_NEXT]", "a"])),
            Err(RecordError::BodyAfterFlags("a".into()))
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
    fn a_state_block_is_not_part_of_the_record() {
        let e = Emission::parse_display("it isn't[REPLACE1][STATE]it be neg[END]").unwrap();
        assert_eq!(e.record.body, vec![Item::Text("it isn't".into())]);
        assert_eq!(e.record.flags.replace.unwrap().get(), 1);
        assert_eq!(e.state, Some(State("it be neg".into())));
        assert_eq!(e.to_string(), "it isn't[REPLACE1][STATE]it be neg[END]");

        // No block is the ordinary case, and it is not the empty block.
        let e = Emission::parse_display("ing[ATTACH_L]").unwrap();
        assert_eq!(e.state, None);
        // A record parser on its own has no meaning for the block.
        assert_eq!(
            Record::parse_display("a[STATE]x[END]"),
            Err(RecordError::UnknownTag("[STATE]".into()))
        );
    }

    #[test]
    fn display_form_round_trips() {
        for s in [
            "ing[ATTACH_L][CAP_NEXT]",
            "[KEY]Return[END]",
            "[CMD]wm.focus[ARG]left[END]",
            "![REP3]",
            "[COPY1]",
            "a[GLUE]",
            "plain text",
        ] {
            let r = Record::parse_display(s).unwrap();
            assert_eq!(r.to_string(), s, "{s} did not round-trip");
        }
    }
}
