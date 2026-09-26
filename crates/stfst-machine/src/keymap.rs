//! Which system key each machine key stands for.

use crate::Protocol;

/// Plover's form: each system key, or `no-op`, with the machine keys bound to it.
pub type Mappings = Vec<(String, Vec<String>)>;

/// A machine chord to a stroke. A machine key the keymap leaves out, or binds
/// to `no-op` or to a key the system lacks, adds nothing.
#[derive(Clone, Debug)]
pub struct Keymap {
    /// Per chart key, its stroke bit, or zero.
    bits: Vec<u64>,
}

impl Keymap {
    /// `names` is the system's keys in steno order.
    pub fn new(chart: &[&str], mappings: &Mappings, names: &[&str]) -> Keymap {
        let mut bits = vec![0; chart.len()];
        for (action, keys) in mappings {
            let bit = names.iter().position(|n| n == action).map_or(0, |i| 1 << i);
            for key in keys {
                if let Some(i) = chart.iter().position(|k| k == key) {
                    bits[i] = bit;
                }
            }
        }
        Keymap { bits }
    }

    pub fn stroke(&self, chord: u64) -> u64 {
        self.bits
            .iter()
            .enumerate()
            .filter(|(i, _)| chord >> i & 1 == 1)
            .fold(0, |out, (_, bit)| out | bit)
    }
}

/// English Stenotype's keymap for `protocol`, which is Plover's when
/// `plover.cfg` has none.
pub fn defaults(protocol: Protocol) -> Mappings {
    let plain = [
        "T-", "K-", "P-", "W-", "H-", "R-", "A-", "O-", "-E", "-U", "-F", "-R", "-P", "-B", "-L",
        "-G", "-T", "-S", "-D", "-Z",
    ];
    let own = |k: &str| (k.to_string(), vec![k.to_string()]);
    let many = |k: &str, keys: &[&str]| (k.to_string(), keys.iter().map(|s| s.to_string()).collect());
    let mut out: Mappings = Vec::new();
    match protocol {
        Protocol::Stentura => {
            out.extend(["#", "S-", "*"].map(own));
            out.extend(plain.map(own));
            out.push(many("no-op", &["^"]));
        }
        Protocol::Hid | Protocol::Gemini => {
            out.push(many(
                "#",
                &["#1", "#2", "#3", "#4", "#5", "#6", "#7", "#8", "#9", "#A", "#B", "#C"],
            ));
            out.push(many("S-", &["S1-", "S2-"]));
            out.push(many("*", &["*1", "*2", "*3", "*4"]));
            out.extend(plain.map(own));
            let spare: Vec<String> = if protocol == Protocol::Hid {
                (1..=26).map(|i| format!("X{i}")).collect()
            } else {
                ["Fn", "pwr", "res1", "res2"].map(String::from).to_vec()
            };
            out.push(("no-op".to_string(), spare));
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const NAMES: &[&str] = &[
        "🦐-", "#", "@-", "^-", "+-", "S-", "T-", "K-", "P-", "W-", "H-", "R-", "A-", "O-", "*",
        "-E", "-U", "-F", "-R", "-P", "-B", "-L", "-G", "-T", "-S", "-D", "-Z",
    ];

    fn bit(name: &str) -> u64 {
        1 << NAMES.iter().position(|n| *n == name).unwrap()
    }

    #[test]
    fn a_default_keymap_folds_the_split_keys() {
        let map = Keymap::new(Protocol::Gemini.chart(), &defaults(Protocol::Gemini), NAMES);
        let (chord, _) = Protocol::Gemini.mask(["S1-", "S2-", "*3", "-Z", "Fn"]);
        assert_eq!(map.stroke(chord), bit("S-") | bit("*") | bit("-Z"));
    }

    #[test]
    fn a_key_the_system_lacks_adds_nothing() {
        let mappings = vec![("Q-".to_string(), vec!["T-".to_string()])];
        let map = Keymap::new(Protocol::Hid.chart(), &mappings, NAMES);
        assert_eq!(map.stroke(Protocol::Hid.mask(["T-"]).0), 0);
    }
}
