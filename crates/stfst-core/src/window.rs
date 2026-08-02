//! The window: text the host has already written, on the input tape.
//!
//! A dictionary that has to *rewrite* what is on screen needs to see it, and a
//! record tag cannot say "the last three words". So the host puts them on the
//! tape, after the keys, and the record that comes back replaces exactly those
//! words.
//!
//! After the keys, not before, and that is forced. A transducer reads one tape
//! left to right, so a window before the keys would have to be held while the
//! keys were read and then written out again, and reproducing a string you have
//! already read is the copy relation, which is not regular. Keys first, and the
//! words stream through the rule the keys selected.
//!
//! ## Characters the transducer does not need to know
//!
//! The tape does not carry the characters. It carries, per character, either the
//! character itself when the grammar names it, or the *class* it belongs to. So a
//! rule that recases a word says "this letter is uppercase now" and never learns
//! which letter it was, and the host applies its own casing when it substitutes
//! the answer back. Full Unicode casing stays in the host, where it is one call,
//! and no character class is declared anywhere: the compiler reads the significant
//! set out of the finished grammar and writes it into the manifest.
//!
//! ```text
//! "^" "A" "p" [W] [LETTER][LETTER][LETTER] [SP] [UPPER][LETTER] [/W]
//!                 fox                           Ox
//! ```
//!
//! Only class glyphs are substituted, and they are substituted in order, so a
//! rule accounts for every one of them: it keeps it, or it says `[DROP]`. A
//! significant character never takes a turn, because it is literal on both tapes,
//! so a rule may insert and delete those freely. What a rule must not do is
//! reorder what it keeps, and that is the whole correspondence.

/// The delimiters. The window opens once, after the keys, and runs to the end of
/// the tape.
pub const WINDOW_OPEN: &str = "[W]";
pub const WINDOW_CLOSE: &str = "[/W]";

/// The next window character, deleted.
///
/// Every character the host showed has to be accounted for, in order, or the one
/// after it is written in its place: substitution has no other way to line the
/// two tapes up. So a rule that drops a character says so, and "delete" and
/// "keep" are the same shape of statement. `retro.join` closing a gap is the case
/// that found this out.
pub const DROP: &str = "[DROP]";

/// What a character is, when the grammar has not named it.
///
/// Each one is used. `Letter` and `Upper` are what a recase moves between,
/// `Caseless` is a letter a recase must skip, `Digit` and `Other` are what a
/// split on word shape divides at, and `Space` is the word boundary that also
/// remembers the exact whitespace.
///
/// Case is a class of its own rather than a mark after the letter. A mark would
/// make "a lowercase letter" mean "a letter with no mark after it", so every rule
/// would carry a lookahead, and the two camelCase rules could not be written at
/// all without one.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Class {
    Letter,
    Upper,
    Caseless,
    Digit,
    Other,
    Space,
}

impl Class {
    pub const ALL: &'static [Class] = &[
        Class::Letter,
        Class::Upper,
        Class::Caseless,
        Class::Digit,
        Class::Other,
        Class::Space,
    ];

    pub fn tag(&self) -> &'static str {
        match self {
            Class::Letter => "[LETTER]",
            Class::Upper => "[UPPER]",
            Class::Caseless => "[CASELESS]",
            Class::Digit => "[DIGIT]",
            Class::Other => "[OTHER]",
            Class::Space => "[SP]",
        }
    }

    pub fn of(c: char) -> Class {
        if c.is_whitespace() {
            return Class::Space;
        }
        if c.is_numeric() {
            return Class::Digit;
        }
        if !c.is_alphabetic() {
            return Class::Other;
        }
        // A letter with no case at all: every script that does not have one, and
        // the reason a recase can skip a word rather than mangle it.
        if c.to_lowercase().eq(c.to_uppercase()) {
            return Class::Caseless;
        }
        match c.to_lowercase().next() == Some(c) {
            true => Class::Letter,
            false => Class::Upper,
        }
    }

    pub fn from_tag(tag: &str) -> Option<Class> {
        Class::ALL.iter().copied().find(|c| c.tag() == tag)
    }
}

/// One character on the tape: its class and case, or the character itself where
/// the grammar names it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Glyph {
    Class(Class),
    /// A character the grammar named, so it reaches the tape as itself.
    Sig(char),
}

/// A significant character as a tape symbol. `[=-]` and not `-`, because `-` is
/// how a key spells itself and the two must not collide: the compiler has to be
/// able to read the significant set straight out of the alphabet.
pub fn sig_symbol(c: char) -> String {
    format!("[={c}]")
}

/// The character a significant symbol stands for.
pub fn sig_char(symbol: &str) -> Option<char> {
    let inner = symbol.strip_prefix("[=")?.strip_suffix(']')?;
    let mut chars = inner.chars();
    let c = chars.next()?;
    chars.next().is_none().then_some(c)
}

impl Glyph {
    pub fn symbol(&self) -> String {
        match self {
            Glyph::Class(class) => class.tag().to_string(),
            Glyph::Sig(c) => sig_symbol(*c),
        }
    }
}

/// A span of already-written text, as the tape carries it.
///
/// `chars` is kept beside the glyphs because substitution needs it: a class glyph
/// coming back means "the next character of this span", and the host has to have
/// the character to write it.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Window {
    pub glyphs: Vec<Glyph>,
    /// The characters the class glyphs stand for, in order. A significant
    /// character is not here: it is literal on both tapes, so it never takes a
    /// turn.
    pub chars: Vec<char>,
}

impl Window {
    /// Encode a span, given the characters this dictionary named.
    pub fn encode(span: &str, significant: &[char]) -> Window {
        let mut window = Window::default();
        for c in span.chars() {
            if significant.contains(&c) {
                window.glyphs.push(Glyph::Sig(c));
                continue;
            }
            window.glyphs.push(Glyph::Class(Class::of(c)));
            window.chars.push(c);
        }
        window
    }

    /// The whole window as tape symbols, delimiters included.
    pub fn to_symbols(&self) -> Vec<String> {
        let mut out = vec![WINDOW_OPEN.to_string()];
        out.extend(self.glyphs.iter().map(Glyph::symbol));
        out.push(WINDOW_CLOSE.to_string());
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_character_is_its_class_unless_the_grammar_named_it() {
        let w = Window::encode("a-B", &['-']);
        assert_eq!(
            w.glyphs,
            vec![
                Glyph::Class(Class::Letter),
                Glyph::Sig('-'),
                Glyph::Class(Class::Upper),
            ]
        );
        // The significant character is not a substitution slot.
        assert_eq!(w.chars, vec!['a', 'B']);
        assert_eq!(w.to_symbols().concat(), "[W][LETTER][=-][UPPER][/W]");
    }

    #[test]
    fn a_letter_with_no_case_is_not_a_letter_to_recase() {
        let w = Window::encode("東 é", &[]);
        assert_eq!(
            w.glyphs,
            vec![
                Glyph::Class(Class::Caseless),
                Glyph::Class(Class::Space),
                Glyph::Class(Class::Letter),
            ]
        );
    }
}
