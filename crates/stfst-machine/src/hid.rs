//! Plover HID: the whole key state on every change, as one report.

pub const USAGE_PAGE: u32 = 0xFF50;
pub const USAGE: u32 = 0x4C56;

/// Every compliant device uses this report id, `P`.
const REPORT_ID: u8 = 0x50;

#[rustfmt::skip]
pub const CHART: &[&str] = &[
    "S1-", "T-", "K-", "P-", "W-", "H-", "R-", "A-",
    "O-", "*1", "-E", "-U", "-F", "-R", "-P", "-B",
    "-L", "-G", "-T", "-S", "-D", "-Z", "#1", "S2-",
    "*2", "*3", "*4", "#2", "#3", "#4", "#5", "#6",
    "#7", "#8", "#9", "#A", "#B", "#C", "X1", "X2",
    "X3", "X4", "X5", "X6", "X7", "X8", "X9", "X10",
    "X11", "X12", "X13", "X14", "X15", "X16", "X17", "X18",
    "X19", "X20", "X21", "X22", "X23", "X24", "X25", "X26",
];

/// The keys a report holds, or None if it is not a key report. The wire puts
/// chart key 0 in the top bit.
pub fn parse(report: &[u8]) -> Option<u64> {
    match report {
        [REPORT_ID, state @ ..] if state.len() >= 8 => {
            Some(u64::from_be_bytes(state[..8].try_into().unwrap()).reverse_bits())
        }
        _ => None,
    }
}

/// Whether a HID report descriptor declares the Plover HID usage, which is how
/// a host tells the machine's interface from the rest of a composite device.
pub fn declares(descriptor: &[u8]) -> bool {
    let mut page = 0;
    let mut at = 0;
    while at < descriptor.len() {
        let prefix = descriptor[at];
        if prefix == 0xFE {
            // A long item: its data size is in the next byte.
            let Some(&size) = descriptor.get(at + 1) else { return false };
            at += 3 + size as usize;
            continue;
        }
        let size = [0, 1, 2, 4][(prefix & 3) as usize];
        let Some(bytes) = descriptor.get(at + 1..at + 1 + size) else { return false };
        let data = bytes.iter().rev().fold(0u32, |d, &b| d << 8 | b as u32);
        match prefix & 0xFC {
            0x04 => page = data,
            0x08 => {
                // A four-byte usage carries its own page in the top half.
                let usage_page = if size == 4 { data >> 16 } else { page };
                if usage_page == USAGE_PAGE && data & 0xFFFF == USAGE {
                    return true;
                }
            }
            _ => {}
        }
        at += 1 + size;
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_first_chart_key_is_the_top_bit() {
        let report = [0x50, 0x80, 0, 0, 0, 0, 0, 0, 0x01];
        assert_eq!(parse(&report), Some(1 | 1 << 63));
        assert_eq!(parse(&[0x01, 0, 0, 0, 0, 0, 0, 0, 0]), None);
        assert_eq!(parse(&[0x50, 0, 0]), None);
    }

    #[test]
    fn finds_the_usage_in_a_descriptor() {
        // Usage Page (0xFF50), Usage (0x4C56), Collection, ..., End Collection.
        let plover = [0x06, 0x50, 0xFF, 0x0A, 0x56, 0x4C, 0xA1, 0x01, 0x85, 0x50, 0xC0];
        assert!(declares(&plover));
        // A boot keyboard: Generic Desktop, Keyboard.
        let keyboard = [0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0xC0];
        assert!(!declares(&keyboard));
        assert!(!declares(&plover[..4]));
    }
}
