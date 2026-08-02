//! What a host can be asked to do: the command and meta tables.
//!
//! §3 put commands and metas in the "open-ended, so delimit" column, and that was
//! wrong for the reason the checking argument gives. A host implements a fixed set
//! of operations, so the set is closed and small, so it enumerates: one symbol per
//! operation, `[CMD=mode.caps]`. The pay-off is that the load-time check is the
//! same one-pass alphabet check as every other tag, and a dictionary that asks for
//! something a host cannot do is refused before a stroke arrives instead of
//! silently writing nothing.
//!
//! The argument still delimits, because an argument is text.
//!
//! The two tables are not the same kind of thing. A **command** is a request that
//! changes the host or the engine and produces no text. A **meta** edits text the
//! host has already written. A dictionary that wants an edit nobody has named
//! should read the window instead and write the edit itself; these are here
//! because they are Plover built-ins, so every host has to have them anyway.

/// Commands, in tape order. Each one is a thing a host either does or declines.
pub const COMMANDS: &[&str] = &[
    // Output modes. Plover's `{mode:…}`: persistent host state, and the reason
    // this table exists at all, because no record tag can say "from now on".
    "mode.caps",
    "mode.title",
    "mode.lower",
    "mode.camel",
    "mode.snake",
    "mode.reset_case",
    "mode.set_space",
    "mode.reset_space",
    "mode.reset",
    // Operations on the stroke buffer rather than on text. Plover spells these as
    // macros, `=undo` and `{*+}` and `{*}`, and they are the one class that reaches
    // back into the input side.
    "engine.undo",
    "engine.repeat_stroke",
    "engine.toggle_star",
    // Engine control, which a host may not have a way to honour at all.
    "plover.suspend",
    "plover.resume",
    "plover.toggle",
    "plover.add_translation",
    "plover.lookup",
    "plover.suggestions",
    "plover.configure",
    "plover.focus",
    "plover.quit",
    "plover.set_config",
    // Plugin commands the config in `~/.config/plover` actually uses. A command
    // is a *request*, so a host that cannot honour one declines it and the
    // dictionary still loads; what the table decides is whether the name means
    // anything at all.
    "plover.steno_chords",
    "stack.toggle",
    "stack.solo",
    "stack.end_solo",
    "stack.priority",
];

/// Metas: edits to text already written.
///
/// Short on purpose, and it should stay short. Everything here is a Plover
/// built-in that a dictionary cannot express any other way. A dictionary-specific
/// edit belongs in the dictionary, over a window.
pub const METAS: &[&str] = &[
    // `{*-|}`, `{*<}`, `{*>}`: recase the previous word.
    "retro.cap",
    "retro.upper",
    "retro.lower",
    // `{*($c)}`: the previous number as currency, with the argument as the format.
    "retro.currency",
    // `{:infl:ed}`, from `plover-semantic-suffixes`: inflect the previous word,
    // where the argument is the mode. The regular cases are the orthography rules;
    // `run` to `ran` is a lexicon, and the right home for that is a morphology
    // transducer composed onto the window, which is what HFST is for.
    "infl",
    // `{:retro:past}`, from the same plugin: rewrite the clause. Declared and not
    // implemented here, because it needs the same lexicon plus a parse.
    "clause",
];

pub fn command_tag(name: &str) -> String {
    format!("[CMD={name}]")
}

pub fn meta_tag(name: &str) -> String {
    format!("[META={name}]")
}

/// The name inside a `[CMD=…]` or `[META=…]` symbol.
pub fn tag_name<'a>(symbol: &'a str, kind: &str) -> Option<&'a str> {
    symbol
        .strip_prefix('[')?
        .strip_suffix(']')?
        .strip_prefix(kind)?
        .strip_prefix('=')
}

pub fn is_command(symbol: &str) -> bool {
    tag_name(symbol, "CMD").is_some_and(|n| COMMANDS.contains(&n))
}

pub fn is_meta(symbol: &str) -> bool {
    tag_name(symbol, "META").is_some_and(|n| METAS.contains(&n))
}

/// What one host can do. A dictionary declares what it needs, and `Stack::open`
/// compares: the same shape as declaring the keys a system must have.
///
/// Empty by default, so a host gets nothing for free and a library user has to say
/// what they implemented.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Capabilities {
    pub commands: Vec<String>,
    pub metas: Vec<String>,
}

/// Metas the reference host does not implement. Naming them here rather than
/// leaving them out of the table is the difference between "no host can do this"
/// and "this host cannot".
const UNIMPLEMENTED: &[&str] = &["clause"];

impl Capabilities {
    /// Every command and meta in the tables. For a test, or a host that claims
    /// everything.
    pub fn all() -> Capabilities {
        Capabilities {
            commands: COMMANDS.iter().map(|s| s.to_string()).collect(),
            metas: METAS.iter().map(|s| s.to_string()).collect(),
        }
    }

    /// What the reference formatter and CLI between them actually do.
    pub fn reference() -> Capabilities {
        Capabilities {
            metas: METAS
                .iter()
                .filter(|m| !UNIMPLEMENTED.contains(m))
                .map(|s| s.to_string())
                .collect(),
            ..Capabilities::all()
        }
    }

    pub fn has_command(&self, name: &str) -> bool {
        self.commands.iter().any(|c| c == name)
    }

    pub fn has_meta(&self, name: &str) -> bool {
        self.metas.iter().any(|m| m == name)
    }
}
