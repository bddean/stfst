//! Stentura: the host asks and the machine answers, one packet each way.
//!
//! Strokes are read from the machine's realtime file, `REALTIME.000`: OPEN it,
//! then READC over and over. A read of the realtime file returns whatever was
//! stroked since the last one, and the machine may sit on a read for up to
//! 500 ms while nothing is. Every read before the first empty one is history,
//! and is dropped.
//!
//! A request is a 16-byte header (SOH, sequence number, length, action, five
//! parameters) and a CRC; a response echoes the sequence number and action and
//! carries an error code and two parameters. Either may be followed by a data
//! section and its own CRC. Everything multi-byte is little-endian. A request
//! with no answer within a second is sent again, three times in all.

use crate::Error;

#[rustfmt::skip]
pub const CHART: &[&str] = &[
    "^", "#", "S-", "T-", "K-", "P-",
    "W-", "H-", "R-", "A-", "O-", "*",
    "-E", "-U", "-F", "-R", "-P", "-B",
    "-L", "-G", "-T", "-S", "-D", "-Z",
];

const OPEN: u16 = 0xA;
const READC: u16 = 0xB;
const TIMEOUT_MS: u64 = 1000;
const TRIES: u32 = 3;
const HEADER: usize = 14;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
enum Phase {
    Opening,
    /// Reading the strokes made before we asked, to reach the end of the file.
    CatchingUp,
    Live,
}

pub struct Session {
    phase: Phase,
    seq: u8,
    request: Vec<u8>,
    /// Whether [`Session::request`] is still to be written.
    unsent: bool,
    sent_at: Option<u64>,
    tries: u32,
    received: Vec<u8>,
    /// Where the next read starts in the realtime file.
    block: u16,
    byte: u16,
    /// Stroke bytes short of a whole stroke.
    partial: Vec<u8>,
}

impl Default for Session {
    fn default() -> Self {
        Self::new()
    }
}

impl Session {
    pub fn new() -> Session {
        Session {
            phase: Phase::Opening,
            seq: 0,
            request: request(OPEN, 0, [b'A' as u16, 0, 0, 0, 0], Some(b"REALTIME.000")),
            unsent: true,
            sent_at: None,
            tries: 0,
            received: Vec::new(),
            block: 0,
            byte: 0,
            partial: Vec::new(),
        }
    }

    pub fn outgoing(&mut self, now: u64) -> Option<Vec<u8>> {
        if !self.unsent {
            return None;
        }
        self.unsent = false;
        self.sent_at = Some(now);
        Some(self.request.clone())
    }

    pub fn wake(&self, now: u64) -> Option<u64> {
        self.sent_at.map(|at| (at + TIMEOUT_MS).saturating_sub(now))
    }

    pub fn idle(&mut self, now: u64) -> Result<(), Error> {
        match self.sent_at {
            Some(at) if now >= at + TIMEOUT_MS => self.again(),
            _ => Ok(()),
        }
    }

    /// Bytes from the machine, and the strokes they complete.
    pub fn feed(&mut self, bytes: &[u8]) -> Result<Vec<u64>, Error> {
        self.received.extend_from_slice(bytes);
        let mut strokes = Vec::new();
        while self.received.len() >= 4 {
            let length = u16::from_le_bytes([self.received[2], self.received[3]]) as usize;
            if length < HEADER {
                return Err(Error::Protocol(format!("a packet {length} bytes long")));
            }
            if self.received.len() < length {
                break;
            }
            let packet: Vec<u8> = self.received.drain(..length).collect();
            if !valid(&packet) {
                return Err(Error::Protocol("a packet that fails its CRC".into()));
            }
            if packet[1] != self.seq {
                // An answer to an earlier try.
                self.again()?;
                continue;
            }
            if short(&packet, 4) != short(&self.request, 4) {
                return Err(Error::Protocol("an answer to a different action".into()));
            }
            strokes.extend(self.answer(&packet)?);
        }
        Ok(strokes)
    }

    fn answer(&mut self, packet: &[u8]) -> Result<Vec<u64>, Error> {
        let mut strokes = Vec::new();
        if self.phase == Phase::Opening {
            self.phase = Phase::CatchingUp;
        } else {
            let read = short(packet, 8) as usize;
            if !(read == 0 && packet.len() == HEADER || read + HEADER + 2 == packet.len()) {
                return Err(Error::Protocol(format!("a read of {read} bytes in {}", packet.len())));
            }
            let at = self.byte as usize + read;
            self.block += (at / 512) as u16;
            self.byte = (at % 512) as u16;
            match self.phase {
                Phase::CatchingUp if read == 0 => self.phase = Phase::Live,
                Phase::Live => {
                    self.partial.extend_from_slice(&packet[HEADER..HEADER + read]);
                    let whole = self.partial.len() / 4 * 4;
                    for s in self.partial.drain(..whole).collect::<Vec<_>>().chunks(4) {
                        strokes.push(stroke(s)?);
                    }
                }
                _ => {}
            }
        }
        self.seq = self.seq.wrapping_add(1);
        self.request = request(READC, self.seq, [1, 0, 512, self.block, self.byte], None);
        self.unsent = true;
        self.sent_at = None;
        self.tries = 0;
        Ok(strokes)
    }

    /// Send the request again, if it has tries left.
    fn again(&mut self) -> Result<(), Error> {
        self.tries += 1;
        if self.tries >= TRIES {
            return Err(Error::Lost);
        }
        self.received.clear();
        self.unsent = true;
        self.sent_at = None;
        Ok(())
    }
}

/// Four bytes, each `11` and six keys: `^#STKP WHRAO* EUFRPB LGTSDZ`.
fn stroke(bytes: &[u8]) -> Result<u64, Error> {
    let mut bits = 0;
    for (i, &b) in bytes.iter().enumerate() {
        if b & 0xC0 != 0xC0 {
            return Err(Error::Protocol(format!("0x{b:02X} is not a stroke byte")));
        }
        for j in 0..6 {
            if b & (0x20 >> j) != 0 {
                bits |= 1 << (i * 6 + j);
            }
        }
    }
    Ok(bits)
}

fn short(packet: &[u8], at: usize) -> u16 {
    u16::from_le_bytes([packet[at], packet[at + 1]])
}

fn request(action: u16, seq: u8, params: [u16; 5], data: Option<&[u8]>) -> Vec<u8> {
    let length = 18 + data.map_or(0, |d| d.len() + 2);
    let mut out = vec![1, seq];
    for word in [length as u16, action].into_iter().chain(params) {
        out.extend(word.to_le_bytes());
    }
    out.extend(crc(&out[1..]).to_le_bytes());
    if let Some(data) = data {
        out.extend(data);
        out.extend(crc(data).to_le_bytes());
    }
    out
}

fn valid(packet: &[u8]) -> bool {
    packet.len() >= HEADER
        && short(packet, 2) as usize == packet.len()
        && crc(&packet[1..HEADER]) == 0
        && (packet.len() == HEADER || packet.len() >= HEADER + 3 && crc(&packet[HEADER..]) == 0)
}

/// CRC-16/ARC. Appending it, little-endian, makes the CRC of the whole zero.
fn crc(data: &[u8]) -> u16 {
    let mut crc = 0u16;
    for &b in data {
        crc ^= b as u16;
        for _ in 0..8 {
            crc = if crc & 1 != 0 { crc >> 1 ^ 0xA001 } else { crc >> 1 };
        }
    }
    crc
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::Protocol;

    /// The machine's answer to `request`, with `data` read.
    fn answer(request: &[u8], data: &[u8]) -> Vec<u8> {
        let length = HEADER + if data.is_empty() { 0 } else { data.len() + 2 };
        let mut out = vec![1, request[1]];
        let read = if request[4] == READC as u8 { data.len() as u16 } else { 0 };
        for word in [length as u16, short(request, 4), 0, read, 0] {
            out.extend(word.to_le_bytes());
        }
        out.extend(crc(&out[1..]).to_le_bytes());
        if !data.is_empty() {
            out.extend(data);
            out.extend(crc(data).to_le_bytes());
        }
        out
    }

    #[test]
    fn the_crc_is_arc() {
        assert_eq!(crc(b"123456789"), 0xBB3D);
    }

    #[test]
    fn opens_the_realtime_file_and_skips_its_history() {
        let mut s = Session::new();
        let open = s.outgoing(0).unwrap();
        assert_eq!(&open[18..30], b"REALTIME.000");
        assert!(s.feed(&answer(&open, &[])).unwrap().is_empty());

        // History: a stroke made before we asked.
        let read = s.outgoing(10).unwrap();
        assert_eq!(short(&read, 4), READC);
        assert!(s.feed(&answer(&read, &[0xC0, 0xC0, 0xC0, 0xC1])).unwrap().is_empty());
        let read = s.outgoing(20).unwrap();
        assert_eq!((short(&read, 12), short(&read, 14)), (0, 4), "the read resumes at byte 4");
        assert!(s.feed(&answer(&read, &[])).unwrap().is_empty());

        // S- and -Z, split across two reads.
        let read = s.outgoing(30).unwrap();
        assert!(s.feed(&answer(&read, &[0xC8, 0xC0])).unwrap().is_empty());
        let read = s.outgoing(40).unwrap();
        let strokes = s.feed(&answer(&read, &[0xC0, 0xC1])).unwrap();
        assert_eq!(strokes, vec![Protocol::Stentura.mask(["S-", "-Z"]).0]);
    }

    #[test]
    fn an_unanswered_request_goes_again_then_gives_up() {
        let mut s = Session::new();
        let open = s.outgoing(0).unwrap();
        assert_eq!(s.wake(500), Some(500));
        s.idle(1000).unwrap();
        assert_eq!(s.outgoing(1000).unwrap(), open);
        s.idle(2000).unwrap();
        assert_eq!(s.outgoing(2000).unwrap(), open);
        assert_eq!(s.idle(3000), Err(Error::Lost));
    }

    #[test]
    fn an_answer_can_arrive_in_pieces() {
        let mut s = Session::new();
        let open = s.outgoing(0).unwrap();
        let reply = answer(&open, &[]);
        assert!(s.feed(&reply[..3]).unwrap().is_empty());
        assert!(s.outgoing(1).is_none());
        s.feed(&reply[3..]).unwrap();
        assert!(s.outgoing(2).is_some());
    }
}
