//! When a Plover HID chord ends, and what a long hold adds to it: the rules of
//! `plover-hid-timing`.
//!
//! A chord ends when a group of the keys it occupies has been fully released,
//! so the send rules are partitions of the keyboard: one group of every key,
//! one group per key, or the named groups and the keys they leave over.
//!
//! `hold_ms` after the last press, a pending chord is held, and when it sends
//! it carries `hold_key` too. The send is still the release, so an ordinary
//! stroke waits on nothing.

use crate::{hid, sounds, PloverConfig, Protocol};

/// The section of the machine these rules come from.
pub const TIMING: &str = "Plover HID Timing";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ChordSend {
    AllUp,
    FirstUp,
    GroupUp,
}

impl ChordSend {
    pub fn parse(value: &str) -> Option<ChordSend> {
        match value {
            "all_up" => Some(ChordSend::AllUp),
            "first_up" => Some(ChordSend::FirstUp),
            "group_up" => Some(ChordSend::GroupUp),
            _ => None,
        }
    }
}

/// The hold's sounds. `Cancel` stops the arm sound before its latch.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Cue {
    Arm,
    Cancel,
    Disarm,
    Send,
}

#[derive(Clone, Debug, PartialEq)]
pub struct HidOptions {
    /// Stock Plover HID, which has no groups and no hold.
    pub stock: bool,
    pub chord_send: ChordSend,
    /// HID key names, `|` between groups.
    pub group_keys: String,
    pub hold_ms: u64,
    /// The HID key a held chord gains; blank is no hold.
    pub hold_key: String,
    pub hold_volume: f32,
    /// Each part's volume on top of `hold_volume`, in [`sounds::PARTS`] order.
    pub parts: [f32; sounds::PARTS.len()],
    pub double_tap_repeat: bool,
    pub repeat_delay_ms: u64,
    pub repeat_interval_ms: u64,
}

impl Default for HidOptions {
    fn default() -> Self {
        HidOptions {
            stock: false,
            chord_send: ChordSend::FirstUp,
            group_keys: "S1- S2- T- K- P- W- H- R- A- O-".into(),
            hold_ms: 250,
            hold_key: String::new(),
            hold_volume: 1.0,
            parts: [1.0; sounds::PARTS.len()],
            double_tap_repeat: false,
            repeat_delay_ms: 200,
            repeat_interval_ms: 30,
        }
    }
}

impl HidOptions {
    /// Stock Plover HID's options if `machine_type` names it, and the timing
    /// machine's otherwise, since that one has the more to say.
    pub fn from_config(cfg: &PloverConfig) -> HidOptions {
        let d = HidOptions::default();
        let stock = cfg.machine_type() == Some(Protocol::Hid.title());
        let s = if stock { Protocol::Hid.title() } else { TIMING };
        let repeats = HidOptions {
            double_tap_repeat: cfg.flag(s, "double_tap_repeat").unwrap_or(d.double_tap_repeat),
            repeat_delay_ms: cfg.parsed(s, "repeat_delay_ms").unwrap_or(d.repeat_delay_ms),
            repeat_interval_ms: cfg.parsed(s, "repeat_interval_ms").unwrap_or(d.repeat_interval_ms),
            ..d
        };
        if stock {
            let first_up = cfg.flag(s, "first_up_chord_send").unwrap_or(false);
            return HidOptions {
                stock,
                chord_send: if first_up { ChordSend::FirstUp } else { ChordSend::AllUp },
                ..repeats
            };
        }
        HidOptions {
            chord_send: cfg.get(s, "chord_send").and_then(ChordSend::parse).unwrap_or(repeats.chord_send),
            group_keys: cfg.get(s, "group_keys").map_or(repeats.group_keys.clone(), String::from),
            hold_ms: cfg.parsed(s, "hold_ms").unwrap_or(repeats.hold_ms),
            hold_key: cfg.get(s, "hold_key").map_or(repeats.hold_key.clone(), String::from),
            hold_volume: cfg.parsed(s, "hold_volume").unwrap_or(repeats.hold_volume),
            parts: sounds::PARTS.map(|p| cfg.parsed(s, &format!("hold_volume_{p}")).unwrap_or(1.0)),
            ..repeats
        }
    }

    /// The partition [`HidOptions::chord_send`] asks for.
    pub fn groups(&self) -> Vec<u64> {
        match self.chord_send {
            ChordSend::AllUp => vec![u64::MAX],
            ChordSend::FirstUp => (0..64).map(|i| 1 << i).collect(),
            ChordSend::GroupUp => {
                let named: Vec<u64> = self
                    .group_keys
                    .split('|')
                    .map(|g| Protocol::Hid.mask(g.split_whitespace()).0)
                    .filter(|&g| g != 0)
                    .collect();
                let rest = named.iter().fold(u64::MAX, |r, g| r & !g);
                let mut parts = named;
                if rest != 0 {
                    parts.push(rest);
                }
                if parts.len() > 1 { parts } else { vec![u64::MAX] }
            }
        }
    }

    pub fn hold_mask(&self) -> u64 {
        if self.hold_ms == 0 || self.hold_key.trim().is_empty() {
            return 0;
        }
        Protocol::Hid.mask([self.hold_key.trim()]).0
    }

    /// The names in `group_keys` and `hold_key` that are not HID keys.
    pub fn unknown(&self) -> Vec<String> {
        let names = self.group_keys.split(['|', ' ']).chain([self.hold_key.as_str()]);
        Protocol::Hid.mask(names.map(str::trim).filter(|n| !n.is_empty())).1
    }
}

/// Key-state reports in, chords out. Times are milliseconds on one clock.
///
/// A pending chord is held once `hold_ms` has passed since its last press. The
/// arm sound starts `lead_ms` before that, so its latch lands on the moment.
pub struct Chorder {
    groups: Vec<u64>,
    hold_ms: u64,
    hold_mask: u64,
    lead_ms: u64,
    pressed: u64,
    chord: u64,
    sent: bool,
    cued: bool,
    last_press: u64,
}

impl Chorder {
    pub fn new(groups: Vec<u64>, hold_ms: u64, hold_mask: u64, lead_ms: u64) -> Chorder {
        Chorder {
            groups,
            hold_ms,
            hold_mask: if hold_ms > 0 { hold_mask } else { 0 },
            lead_ms,
            pressed: 0,
            chord: 0,
            sent: false,
            cued: false,
            last_press: 0,
        }
    }

    /// The chord this report completes, if any, and the sound it calls for.
    pub fn report(&mut self, now: u64, keys: u64) -> (Option<u64>, Option<Cue>) {
        let held = self.pending() && self.holding(now);
        let cued = self.cued;
        // A report that adds a key is a press, and so is one that repeats the
        // state; only a report that just drops keys leaves the hold clock alone.
        let press = keys & !self.pressed != 0 || keys == self.pressed;
        if press {
            self.last_press = now;
            self.cued = false;
            if self.sent {
                self.chord = 0;
                self.sent = false;
            }
            self.chord |= keys;
        }
        let mut chord = None;
        if self.chord != 0 && !self.sent && self.complete(keys) {
            chord = Some(self.chord | if self.holding(now) { self.hold_mask } else { 0 });
            self.sent = true;
        }
        self.pressed = keys;
        let cue = match chord {
            Some(c) if c & self.hold_mask != 0 => Some(Cue::Send),
            _ if held && press => Some(Cue::Disarm),
            _ if cued && (press || chord.is_some()) => Some(Cue::Cancel),
            _ => None,
        };
        (chord, cue)
    }

    /// True once per hold: when the arm sound should start.
    pub fn tick(&mut self, now: u64) -> bool {
        if self.cued || self.wait(now) != Some(0) {
            return false;
        }
        self.cued = true;
        true
    }

    /// Milliseconds until [`Chorder::tick`] turns true, or None if it won't.
    pub fn wake(&self, now: u64) -> Option<u64> {
        if self.cued { None } else { self.wait(now) }
    }

    fn wait(&self, now: u64) -> Option<u64> {
        if self.hold_mask == 0 || !self.pending() {
            return None;
        }
        Some((self.last_press + self.hold_ms).saturating_sub(self.lead_ms).saturating_sub(now))
    }

    fn pending(&self) -> bool {
        self.chord != 0 && !self.sent
    }

    fn complete(&self, keys: u64) -> bool {
        self.groups.iter().any(|g| g & self.chord != 0 && g & keys == 0)
    }

    fn holding(&self, now: u64) -> bool {
        self.hold_mask != 0 && now.saturating_sub(self.last_press) >= self.hold_ms
    }
}

/// The machine: reports through the chorder, and a chord held down after a
/// double tap repeating, as stock Plover HID does.
pub struct Hid {
    chorder: Chorder,
    options: HidOptions,
    hold_mask: u64,
    current: u64,
    last_sent: u64,
    press_started: u64,
}

impl Hid {
    pub fn new(options: &HidOptions) -> Hid {
        let hold_mask = options.hold_mask();
        Hid {
            chorder: Chorder::new(options.groups(), options.hold_ms, hold_mask, sounds::LEAD_MS),
            options: options.clone(),
            hold_mask,
            current: 0,
            last_sent: 0,
            press_started: 0,
        }
    }

    pub fn report(&mut self, now: u64, report: &[u8]) -> (Vec<u64>, Option<Cue>) {
        let Some(keys) = hid::parse(report) else { return (Vec::new(), None) };
        self.current = keys;
        self.press_started = now;
        let (chord, cue) = self.chorder.report(now, keys);
        if let Some(c) = chord {
            // Without the hold bit, so a held chord still repeats.
            self.last_sent = c & !self.hold_mask;
        }
        (chord.into_iter().collect(), cue)
    }

    pub fn idle(&mut self, now: u64) -> (Vec<u64>, Option<Cue>) {
        let cue = self.chorder.tick(now).then_some(Cue::Arm);
        let repeat = self.repeating()
            && now.saturating_sub(self.press_started) > self.options.repeat_delay_ms;
        (if repeat { vec![self.current] } else { Vec::new() }, cue)
    }

    pub fn wake(&self, now: u64) -> Option<u64> {
        let repeat = self.repeating().then_some(self.options.repeat_interval_ms);
        match (self.chorder.wake(now), repeat) {
            (Some(a), Some(b)) => Some(a.min(b)),
            (a, b) => a.or(b),
        }
    }

    pub fn sounds(&self) -> Option<sounds::Sounds> {
        (self.hold_mask != 0 && self.options.hold_volume > 0.0)
            .then(|| sounds::render(self.options.hold_volume, &self.options.parts))
    }

    fn repeating(&self) -> bool {
        self.options.double_tap_repeat && self.current != 0 && self.current == self.last_sent
    }
}

#[cfg(test)]
mod tests {
    //! `test_chording.py`, on the same fake clock.
    use super::*;

    const S: u64 = 1;
    const T: u64 = 2;
    const K: u64 = 4;
    const HOLD: u64 = 1 << 63;
    const LEFT: u64 = S | T | K;

    fn chorder(send: ChordSend, hold_ms: u64, lead_ms: u64) -> Chorder {
        let options = HidOptions {
            chord_send: send,
            group_keys: "S1- T- K-".into(),
            ..HidOptions::default()
        };
        Chorder::new(options.groups(), hold_ms, if hold_ms > 0 { HOLD } else { 0 }, lead_ms)
    }

    fn play(c: &mut Chorder, script: &[(u64, u64)]) -> Vec<Option<u64>> {
        script.iter().map(|&(t, k)| c.report(t, k).0).collect()
    }

    #[test]
    fn all_up_sends_when_the_last_key_is_up() {
        let mut c = chorder(ChordSend::AllUp, 0, 0);
        assert_eq!(
            play(&mut c, &[(0, S), (100, S | T), (200, T), (300, 0)]),
            [None, None, None, Some(S | T)]
        );
    }

    #[test]
    fn first_up_sends_on_the_first_release_and_held_keys_join_the_next() {
        let mut c = chorder(ChordSend::FirstUp, 0, 0);
        assert_eq!(
            play(&mut c, &[(0, S | T), (100, T), (200, T | K), (300, K)]),
            [None, Some(S | T), None, Some(T | K)]
        );
    }

    #[test]
    fn group_up_waits_for_the_whole_group() {
        let e = Protocol::Hid.mask(["-E"]).0;
        let mut c = chorder(ChordSend::GroupUp, 0, 0);
        assert_eq!(play(&mut c, &[(0, S | T), (100, T), (200, 0)]), [None, None, Some(S | T)]);
        assert_eq!(play(&mut c, &[(300, S | e), (400, S)]), [None, Some(S | e)]);
        assert_eq!(LEFT & e, 0);
    }

    #[test]
    fn a_held_chord_gains_the_hold_key_and_sounds() {
        let mut c = chorder(ChordSend::AllUp, 250, 60);
        assert_eq!(c.report(0, S), (None, None));
        assert_eq!(c.wake(0), Some(190));
        assert!(!c.tick(189));
        assert!(c.tick(190));
        assert!(!c.tick(191), "once per hold");
        assert_eq!(c.report(300, 0), (Some(S | HOLD), Some(Cue::Send)));
    }

    #[test]
    fn a_press_after_the_hold_disarms_it_and_a_quick_send_cancels_the_arm() {
        let mut c = chorder(ChordSend::AllUp, 250, 60);
        c.report(0, S);
        assert!(c.tick(190));
        assert_eq!(c.report(260, S | T), (None, Some(Cue::Disarm)));
        assert!(c.tick(450));
        assert_eq!(c.report(460, 0), (Some(S | T), Some(Cue::Cancel)));
    }

    #[test]
    fn reads_the_timing_machine_from_plover_cfg() {
        let cfg = PloverConfig::parse(
            "[Plover HID Timing]\nchord_send = group_up\nhold_key = X1\nhold_ms = 150\n\
             group_keys = S1- T- | Q-\nhold_volume_ka = 2.0\n",
        );
        let o = HidOptions::from_config(&cfg);
        assert_eq!((o.chord_send, o.hold_ms), (ChordSend::GroupUp, 150));
        assert_eq!(o.hold_mask(), Protocol::Hid.mask(["X1"]).0);
        assert_eq!(o.unknown(), ["Q-"]);
        assert_eq!(o.parts[0], 2.0);
    }

    #[test]
    fn a_double_tap_held_repeats() {
        let options = HidOptions { double_tap_repeat: true, chord_send: ChordSend::AllUp, ..Default::default() };
        let mut m = Hid::new(&options);
        let report = |keys: u64| {
            let mut r = vec![0x50];
            r.extend(keys.reverse_bits().to_be_bytes());
            r
        };
        assert_eq!(m.report(0, &report(S)).0, Vec::<u64>::new());
        assert_eq!(m.report(50, &report(0)).0, [S]);
        m.report(100, &report(S));
        assert_eq!(m.wake(100), Some(30));
        assert!(m.idle(250).0.is_empty(), "before the delay");
        assert_eq!(m.idle(301).0, [S]);
    }
}
