//! The desktop's `plover.cfg`, for what it says about machines.

use std::collections::HashMap;

use crate::keymap::Mappings;
use crate::Protocol;

/// An INI file as Python's configparser reads Plover's: option names lowercased.
#[derive(Clone, Debug, Default)]
pub struct PloverConfig {
    sections: HashMap<String, HashMap<String, String>>,
}

/// Plover's default system, which is what an absent `[System] name` means.
pub const DEFAULT_SYSTEM: &str = "English Stenotype";

impl PloverConfig {
    pub fn parse(text: &str) -> PloverConfig {
        let mut sections: HashMap<String, HashMap<String, String>> = HashMap::new();
        let mut current: Option<String> = None;
        for line in text.lines().map(str::trim) {
            if line.is_empty() || line.starts_with('#') || line.starts_with(';') {
                continue;
            }
            if let Some(name) = line.strip_prefix('[').and_then(|l| l.strip_suffix(']')) {
                sections.entry(name.to_string()).or_default();
                current = Some(name.to_string());
                continue;
            }
            let (Some(section), Some((key, value))) = (&current, line.split_once('=')) else {
                continue;
            };
            sections
                .get_mut(section)
                .expect("a section is made when its header is read")
                .insert(key.trim().to_lowercase(), value.trim().to_string());
        }
        PloverConfig { sections }
    }

    pub fn get(&self, section: &str, key: &str) -> Option<&str> {
        self.sections.get(section)?.get(key).map(String::as_str)
    }

    pub fn machine_type(&self) -> Option<&str> {
        self.get("Machine Configuration", "machine_type")
    }

    /// The keymap the current system has for `machine`, or None when it has none.
    pub fn keymap(&self, machine: &str) -> Result<Option<Mappings>, serde_json::Error> {
        let system = self.get("System", "name").unwrap_or(DEFAULT_SYSTEM);
        let key = format!("keymap[{}]", machine.to_lowercase());
        let Some(text) = self.get(&format!("System: {system}"), &key) else {
            return Ok(None);
        };
        let pairs: Vec<(String, OneOrMany)> = serde_json::from_str(text)?;
        Ok(Some(pairs.into_iter().map(|(action, keys)| (action, keys.into_vec())).collect()))
    }

    /// The serial protocol `machine_type` names, and Gemini PR when it names
    /// another kind of machine.
    pub fn serial_protocol(&self) -> Protocol {
        match self.machine_type().and_then(Protocol::titled) {
            Some(p @ Protocol::Stentura) => p,
            _ => Protocol::Gemini,
        }
    }

    /// How a serial machine's port is set up.
    pub fn serial(&self, protocol: Protocol) -> Serial {
        let s = protocol.title();
        let d = Serial::default();
        Serial {
            baudrate: self.parsed(s, "baudrate").unwrap_or(d.baudrate),
            bytesize: self.parsed(s, "bytesize").unwrap_or(d.bytesize),
            parity: self.parsed(s, "parity").unwrap_or(d.parity),
            stopbits: self.parsed(s, "stopbits").unwrap_or(d.stopbits),
        }
    }

    pub fn parsed<T: std::str::FromStr>(&self, section: &str, key: &str) -> Option<T> {
        self.get(section, key)?.parse().ok()
    }

    /// A boolean as configparser reads one.
    pub fn flag(&self, section: &str, key: &str) -> Option<bool> {
        match self.get(section, key)?.to_lowercase().as_str() {
            "true" | "1" | "yes" | "on" => Some(true),
            "false" | "0" | "no" | "off" => Some(false),
            _ => None,
        }
    }
}

/// A serial port's settings, as pyserial names them.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Serial {
    pub baudrate: u32,
    pub bytesize: u8,
    /// `N`, `E`, `O`, `M` or `S`.
    pub parity: char,
    /// 1, 1.5 or 2.
    pub stopbits: f32,
}

impl Default for Serial {
    fn default() -> Self {
        Serial { baudrate: 9600, bytesize: 8, parity: 'N', stopbits: 1.0 }
    }
}

#[derive(serde::Deserialize)]
#[serde(untagged)]
enum OneOrMany {
    One(String),
    Many(Vec<String>),
}

impl OneOrMany {
    fn into_vec(self) -> Vec<String> {
        match self {
            OneOrMany::One(k) => vec![k],
            OneOrMany::Many(ks) => ks,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const CFG: &str = r#"
[Machine Configuration]
machine_type = Plover HID Timing

[System: Stenotype Extended]
dictionaries = [{"enabled": true, "path": "user.json"}]
keymap[plover hid timing] = [["🦐-", ["X1"]], ["S-", ["S2-"]], ["no-op", "X2"]]

[System]
name = Stenotype Extended

[Plover HID Timing]
Hold_MS = 150
double_tap_repeat = False
"#;

    #[test]
    fn reads_options_by_lowercased_name() {
        let cfg = PloverConfig::parse(CFG);
        assert_eq!(cfg.machine_type(), Some("Plover HID Timing"));
        assert_eq!(cfg.parsed::<u64>("Plover HID Timing", "hold_ms"), Some(150));
        assert_eq!(cfg.flag("Plover HID Timing", "double_tap_repeat"), Some(false));
    }

    #[test]
    fn reads_the_current_systems_keymap() {
        let cfg = PloverConfig::parse(CFG);
        let map = cfg.keymap("Plover HID Timing").unwrap().unwrap();
        assert_eq!(map[0], ("🦐-".to_string(), vec!["X1".to_string()]));
        assert_eq!(map[2], ("no-op".to_string(), vec!["X2".to_string()]));
        assert_eq!(cfg.keymap("Gemini PR").unwrap(), None);
    }

    #[test]
    fn a_serial_machine_is_gemini_unless_the_file_says_stentura() {
        assert_eq!(PloverConfig::parse(CFG).serial_protocol(), Protocol::Gemini);
        let cfg = PloverConfig::parse("[Machine Configuration]\nmachine_type = Stentura\n[Stentura]\nbaudrate = 19200\nstopbits = 1.5\n");
        assert_eq!(cfg.serial_protocol(), Protocol::Stentura);
        let serial = cfg.serial(Protocol::Stentura);
        assert_eq!((serial.baudrate, serial.parity, serial.stopbits), (19200, 'N', 1.5));
    }
}
