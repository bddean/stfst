//! Steno machines: what their wire formats mean, when a chord ends, and which
//! system keys a chord stands for.
//!
//! No I/O. A host owns the transport (a USB interrupt endpoint, a serial port)
//! and the clock, and a [`Machine`] turns what arrives, and the time passing,
//! into strokes. So the same machine serves a phone and a desktop.
//!
//! A chord is a mask over its protocol's [`Protocol::chart`]: bit i is chart
//! key i. A stroke is a mask over the system's keys in steno order.

pub mod config;
pub mod gemini;
pub mod hid;
pub mod keymap;
pub mod sounds;
pub mod stentura;
pub mod timing;

pub use config::PloverConfig;
pub use keymap::Keymap;
pub use timing::{ChordSend, Cue, HidOptions};

/// A protocol a machine speaks, under the names Plover uses for its keys.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Protocol {
    Hid,
    Gemini,
    Stentura,
}

impl Protocol {
    /// The machine's name in `plover.cfg`: its section, and its keymap's key.
    pub fn title(self) -> &'static str {
        match self {
            Protocol::Hid => "Plover HID",
            Protocol::Gemini => "Gemini PR",
            Protocol::Stentura => "Stentura",
        }
    }

    pub fn titled(title: &str) -> Option<Protocol> {
        [Protocol::Hid, Protocol::Gemini, Protocol::Stentura]
            .into_iter()
            .find(|p| p.title() == title)
    }

    pub fn chart(self) -> &'static [&'static str] {
        match self {
            Protocol::Hid => hid::CHART,
            Protocol::Gemini => gemini::CHART,
            Protocol::Stentura => stentura::CHART,
        }
    }

    /// The mask of the chart keys named, and the names the chart does not have.
    pub fn mask<'a>(self, names: impl IntoIterator<Item = &'a str>) -> (u64, Vec<String>) {
        let chart = self.chart();
        let mut bits = 0;
        let mut unknown = Vec::new();
        for name in names {
            match chart.iter().position(|k| *k == name) {
                Some(i) => bits |= 1 << i,
                None => unknown.push(name.to_string()),
            }
        }
        (bits, unknown)
    }
}

/// What a machine did with what arrived or with the time passing.
#[derive(Debug, Default, PartialEq, Eq)]
pub struct Output {
    /// Strokes, each a mask over the system's keys, and none of them empty.
    pub strokes: Vec<u64>,
    /// The sound the hold calls for.
    pub cue: Option<Cue>,
}

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum Error {
    #[error("the machine broke the protocol: {0}")]
    Protocol(String),
    #[error("the machine stopped answering")]
    Lost,
}

enum Input {
    Hid(timing::Hid),
    Gemini(gemini::Packets),
    Stentura(stentura::Session),
}

/// One machine: a transducer from bytes and time to strokes, and, for a
/// protocol that asks, to bytes to write back.
///
/// The host's loop is the same for every protocol: write [`Machine::outgoing`],
/// wait for bytes until [`Machine::wake`] runs out, then give them to
/// [`Machine::feed`], or tell [`Machine::idle`] that nothing came. For Plover
/// HID one read is one report.
pub struct Machine {
    input: Input,
    keymap: Keymap,
}

impl Machine {
    /// A machine as `plover.cfg` configures it, for a system whose keys, in
    /// steno order, are `names`. The warnings name what the file said that
    /// could not be used.
    pub fn new(protocol: Protocol, cfg: &PloverConfig, names: &[&str]) -> (Machine, Vec<String>) {
        let mut warnings = Vec::new();
        let (input, keymap_titles): (Input, Vec<&str>) = match protocol {
            Protocol::Hid => {
                let options = HidOptions::from_config(cfg);
                warnings.extend(options.unknown().into_iter().map(|k| format!("not a HID key: {k}")));
                let titles = if options.stock {
                    vec![Protocol::Hid.title(), timing::TIMING]
                } else {
                    vec![timing::TIMING, Protocol::Hid.title()]
                };
                (Input::Hid(timing::Hid::new(&options)), titles)
            }
            Protocol::Gemini => (Input::Gemini(gemini::Packets::default()), vec![protocol.title()]),
            Protocol::Stentura => (Input::Stentura(stentura::Session::new()), vec![protocol.title()]),
        };
        let mappings = keymap_titles.iter().find_map(|t| match cfg.keymap(t) {
            Ok(found) => found,
            Err(e) => {
                warnings.push(format!("keymap[{}]: {e}", t.to_lowercase()));
                None
            }
        });
        let mappings = mappings.unwrap_or_else(|| keymap::defaults(protocol));
        let keymap = Keymap::new(protocol.chart(), &mappings, names);
        (Machine { input, keymap }, warnings)
    }

    /// Bytes that arrived at `now`, in milliseconds on the host's clock.
    pub fn feed(&mut self, now: u64, bytes: &[u8]) -> Result<Output, Error> {
        let (chords, cue) = match &mut self.input {
            Input::Hid(m) => m.report(now, bytes),
            Input::Gemini(p) => (p.feed(bytes), None),
            Input::Stentura(s) => (s.feed(bytes)?, None),
        };
        Ok(self.output(chords, cue))
    }

    /// Nothing arrived before the time [`Machine::wake`] gave.
    pub fn idle(&mut self, now: u64) -> Result<Output, Error> {
        let (chords, cue) = match &mut self.input {
            Input::Hid(m) => m.idle(now),
            Input::Gemini(_) => (Vec::new(), None),
            Input::Stentura(s) => {
                s.idle(now)?;
                (Vec::new(), None)
            }
        };
        Ok(self.output(chords, cue))
    }

    /// Milliseconds from `now` until [`Machine::idle`] has something to do,
    /// or None to wait for bytes however long they take.
    pub fn wake(&self, now: u64) -> Option<u64> {
        match &self.input {
            Input::Hid(m) => m.wake(now),
            Input::Gemini(_) => None,
            Input::Stentura(s) => s.wake(now),
        }
    }

    /// Bytes to write to the machine now, taken once.
    pub fn outgoing(&mut self, now: u64) -> Option<Vec<u8>> {
        match &mut self.input {
            Input::Stentura(s) => s.outgoing(now),
            _ => None,
        }
    }

    /// The hold's sounds at the configured volume, if this machine holds.
    pub fn sounds(&self) -> Option<sounds::Sounds> {
        match &self.input {
            Input::Hid(m) => m.sounds(),
            _ => None,
        }
    }

    fn output(&self, chords: Vec<u64>, cue: Option<Cue>) -> Output {
        let strokes = chords.into_iter().map(|c| self.keymap.stroke(c)).filter(|&s| s != 0).collect();
        Output { strokes, cue }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const NAMES: &[&str] = &[
        "🦐-", "#", "@-", "^-", "+-", "S-", "T-", "K-", "P-", "W-", "H-", "R-", "A-", "O-", "*",
        "-E", "-U", "-F", "-R", "-P", "-B", "-L", "-G", "-T", "-S", "-D", "-Z",
    ];

    const CFG: &str = r#"
[Machine Configuration]
machine_type = Plover HID Timing

[System: Stenotype Extended]
keymap[plover hid timing] = [["\ud83e\udd90-", ["X1"]], ["^-", ["S1-"]], ["S-", ["S2-"]], ["T-", ["T-"]], ["no-op", ["X2"]]]

[System]
name = Stenotype Extended

[Plover HID Timing]
chord_send = all_up
hold_key = X1
hold_ms = 150
"#;

    fn report(keys: &[&str]) -> Vec<u8> {
        let mut r = vec![0x50];
        r.extend(Protocol::Hid.mask(keys.iter().copied()).0.reverse_bits().to_be_bytes());
        r
    }

    fn bits(keys: &[&str]) -> u64 {
        keys.iter().map(|k| 1 << NAMES.iter().position(|n| n == k).unwrap()).sum()
    }

    #[test]
    fn a_held_chord_reaches_the_key_the_keymap_gives_the_hold() {
        let (mut m, warnings) = Machine::new(Protocol::Hid, &PloverConfig::parse(CFG), NAMES);
        assert!(warnings.is_empty(), "{warnings:?}");
        assert!(m.sounds().is_some());

        m.feed(0, &report(&["S1-", "T-"])).unwrap();
        assert_eq!(m.feed(50, &report(&[])).unwrap().strokes, [bits(&["^-", "T-"])]);

        m.feed(100, &report(&["S2-"])).unwrap();
        assert_eq!(m.wake(100), Some(150 - sounds::LEAD_MS));
        assert_eq!(m.idle(190).unwrap().cue, Some(Cue::Arm));
        let out = m.feed(300, &report(&[])).unwrap();
        assert_eq!(out, Output { strokes: vec![bits(&["🦐-", "S-"])], cue: Some(Cue::Send) });
    }

    #[test]
    fn a_chord_of_unmapped_keys_writes_nothing() {
        let (mut m, _) = Machine::new(Protocol::Hid, &PloverConfig::parse(CFG), NAMES);
        m.feed(0, &report(&["X2"])).unwrap();
        assert!(m.feed(10, &report(&[])).unwrap().strokes.is_empty());
    }

    #[test]
    fn a_machine_without_a_keymap_takes_plovers_default() {
        let (mut m, _) = Machine::new(Protocol::Gemini, &PloverConfig::parse(CFG), NAMES);
        let out = m.feed(0, &[0x80, 0x60, 0, 0, 0, 0]).unwrap();
        assert_eq!(out.strokes, [bits(&["S-"])]);
    }
}
