//! The input tape's prefix: what the host tells a dictionary before the keys.
//!
//! A dictionary never reads host state. The host serialises the declared
//! abstraction into a prefix of the input tape, so the relation stays a relation
//! and the automaton stays finite:
//!
//! ```text
//! [CAP=on] "+" "T"   ->   It's
//! ```
//!
//! Two kinds of prefix, one mechanism. A **context slot** is a small abstraction
//! the host computes from its own output, drawn from the closed table below: an
//! unknown slot is a load-time error, exactly as an unknown record tag is. A
//! **state block** is a string the dictionary wrote on its last step and the host
//! handed back, opaque to the host and never inspected by it, which is host
//! contract item 6.
//!
//! The alphabet must be an abstraction and not raw data. `[CAP=on]` is two
//! symbols; the previous word itself would be as many symbols as the world has
//! words, and the automaton would grow with it. Where a dictionary genuinely
//! needs the word, the host binds it as a register and the record splices it back
//! with `[COPY1]`, so the string moves without the transducer reading it.

use std::fmt;

/// A context slot: what the host can say, and the values it can say it with.
pub struct Slot {
    pub name: &'static str,
    pub values: &'static [&'static str],
}

impl Slot {
    pub fn symbol(&self, value: &str) -> String {
        format!("[{}={}]", self.name, value)
    }

    pub fn symbols(&self) -> impl Iterator<Item = String> + '_ {
        self.values.iter().map(|v| self.symbol(v))
    }
}

/// Every context slot a host may be asked for, in tape order.
///
/// Closed, and short on purpose. Each entry is a thing every host has to be able
/// to compute, so adding one is a change to the contract and not to a
/// dictionary.
pub const SLOTS: &[Slot] = &[
    // A capitalisation is pending from the previous unit, so a dictionary that
    // writes its own first letter can write it in the right case.
    Slot {
        name: "CAP",
        values: &["off", "on"],
    },
    // The case of the word the host has bound to `$1`. The word itself never
    // reaches the tape; this is the predicate over it.
    Slot {
        name: "W1",
        values: &["none", "lower", "cap", "upper", "other"],
    },
];

pub fn slot(name: &str) -> Option<&'static Slot> {
    SLOTS.iter().find(|s| s.name == name)
}

/// Every context symbol, which is what an acceptor enumerates and what a
/// load-time check compares an artifact's alphabet against.
pub fn all_symbols() -> Vec<String> {
    SLOTS.iter().flat_map(Slot::symbols).collect()
}

/// The delimiters of a state block. The content is text, so it delimits rather
/// than enumerating: a dictionary's state space is its own business, and a host
/// that had to know the values would be reading the state.
pub const STATE_OPEN: &str = "[STATE]";
pub const STATE_CLOSE: &str = "[END]";

/// A dictionary's state, as the host holds it: a string it stores and replays.
#[derive(Debug, Clone, PartialEq, Eq, Hash, Default)]
pub struct State(pub String);

impl State {
    /// The block as tape symbols. One symbol per character, as everywhere else
    /// on this tape.
    pub fn to_symbols(&self) -> Vec<String> {
        let mut out = vec![STATE_OPEN.to_string()];
        out.extend(self.0.chars().map(|c| c.to_string()));
        out.push(STATE_CLOSE.to_string());
        out
    }
}

impl fmt::Display for State {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{STATE_OPEN}{}{STATE_CLOSE}", self.0)
    }
}

/// The host's answer for every slot, whether or not any dictionary asked.
///
/// One value per `SLOTS` entry rather than a field per slot, so the table stays
/// the only place a slot is defined. A host that has computed nothing yet reads
/// as the first value of each slot, which is why every slot's first value is its
/// "nothing to report" one.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Context {
    values: Vec<&'static str>,
}

impl Default for Context {
    fn default() -> Self {
        Context {
            values: SLOTS.iter().map(|s| s.values[0]).collect(),
        }
    }
}

impl Context {
    /// Report a slot's value. False if the slot or the value is not declared,
    /// which is a host bug rather than a dictionary's.
    pub fn set(&mut self, name: &str, value: &str) -> bool {
        let Some(i) = SLOTS.iter().position(|s| s.name == name) else {
            return false;
        };
        match SLOTS[i].values.iter().find(|v| **v == value) {
            Some(v) => {
                self.values[i] = v;
                true
            }
            None => false,
        }
    }

    /// The prefix for a dictionary that declared these slots. Unknown names are
    /// dropped, because `Dict::open` has already refused a dictionary that
    /// declares one.
    pub fn select(&self, declared: &[String]) -> Vec<(&'static str, &'static str)> {
        SLOTS
            .iter()
            .zip(&self.values)
            .filter(|(s, _)| declared.iter().any(|d| d == s.name))
            .map(|(s, v)| (s.name, *v))
            .collect()
    }
}

/// What the host prefixes onto the input tape: the state it holds for this
/// dictionary, then the context slots the dictionary declared.
///
/// The state comes first because it is the older fact: a host that replays a
/// stroke replays the state it was made in, while the context is recomputed from
/// the output as it stands now.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Prefix {
    pub state: Option<State>,
    /// `(slot name, value)`, in `SLOTS` order.
    pub context: Vec<(&'static str, &'static str)>,
}

impl Prefix {
    pub fn is_empty(&self) -> bool {
        self.state.is_none() && self.context.is_empty()
    }

    pub fn to_symbols(&self) -> Vec<String> {
        let mut out = Vec::new();
        if let Some(state) = &self.state {
            out.extend(state.to_symbols());
        }
        for (name, value) in &self.context {
            out.push(format!("[{name}={value}]"));
        }
        out
    }
}
