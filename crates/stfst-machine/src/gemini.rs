//! Gemini PR: six bytes per chord, the first with its top bit set and the rest
//! clear, seven keys in each.

#[rustfmt::skip]
pub const CHART: &[&str] = &[
    "Fn", "#1", "#2", "#3", "#4", "#5", "#6",
    "S1-", "S2-", "T-", "K-", "P-", "W-", "H-",
    "R-", "A-", "O-", "*1", "*2", "res1", "res2",
    "pwr", "*3", "*4", "-E", "-U", "-F", "-R",
    "-P", "-B", "-L", "-G", "-T", "-S", "-D",
    "#7", "#8", "#9", "#A", "#B", "#C", "-Z",
];

/// The packets in a byte stream, however the transport cuts it.
#[derive(Default)]
pub struct Packets {
    packet: [u8; 6],
    have: usize,
}

impl Packets {
    /// The chords these bytes complete.
    pub fn feed(&mut self, bytes: &[u8]) -> Vec<u64> {
        let mut out = Vec::new();
        for &b in bytes {
            // A byte with the top bit starts a packet, and anything before it
            // was a packet cut short.
            if b & 0x80 != 0 {
                self.have = 0;
            } else if self.have == 0 {
                continue;
            }
            self.packet[self.have] = b;
            self.have += 1;
            if self.have == 6 {
                out.push(chord(&self.packet));
                self.have = 0;
            }
        }
        out
    }
}

fn chord(packet: &[u8; 6]) -> u64 {
    let mut bits = 0;
    for (i, b) in packet.iter().enumerate() {
        for j in 1..8 {
            if b & (0x80 >> j) != 0 {
                bits |= 1 << (i * 7 + j - 1);
            }
        }
    }
    bits
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::Protocol;

    #[test]
    fn a_packet_split_across_reads_is_one_chord() {
        let mut p = Packets::default();
        // S1- is byte 1's first key; -Z is byte 5's last.
        assert!(p.feed(&[0x80, 0x40, 0x00]).is_empty());
        assert_eq!(p.feed(&[0x00, 0x00, 0x01]), vec![Protocol::Gemini.mask(["S1-", "-Z"]).0]);
    }

    #[test]
    fn a_packet_cut_short_is_dropped() {
        let mut p = Packets::default();
        let chords = p.feed(&[0x00, 0x80, 0x01, 0x80, 0, 0, 0, 0, 0x01]);
        assert_eq!(chords, vec![Protocol::Gemini.mask(["-Z"]).0]);
    }
}
