//! The hold's three sounds, synthesised, as `plover-hid-timing` makes them.
//!
//! The arm sound has three parts: ka, a small click; shift; and chunk, a latch.
//! The chunk lands [`LEAD_MS`] into it, so starting it that early puts the chunk
//! on the moment the hold arms. Disarm is a pop upward. Send is a tap, louder
//! than the other two.

use std::f32::consts::PI;

pub const RATE: u32 = 44100;
pub const LEAD_MS: u64 = 60;
const LEAD_S: f32 = LEAD_MS as f32 / 1000.0;

/// Each has its own volume, on top of the whole's.
pub const PARTS: [&str; 5] = ["ka", "shift", "chunk", "disarm", "send"];

/// Mono 16-bit PCM at [`RATE`].
pub struct Sounds {
    pub arm: Vec<i16>,
    pub disarm: Vec<i16>,
    pub send: Vec<i16>,
}

/// Each sound at `volume`, 1 being its designed level, with each part's volume
/// from `parts`, in [`PARTS`] order.
pub fn render(volume: f32, parts: &[f32; PARTS.len()]) -> Sounds {
    let make = |layers: Vec<Layer>, level: f32| {
        let flat = mix(&layers, &[1.0; PARTS.len()]);
        let loudest = loudest(&flat);
        let k = if loudest > 0.0 { volume * level / loudest } else { 0.0 };
        mix(&layers, parts).iter().map(|s| (k * s).clamp(-1.0, 1.0) * 32767.0).map(|s| s as i16).collect()
    };
    Sounds { arm: make(arm(), 0.015), disarm: make(disarm(), 0.015), send: make(send(), 0.05) }
}

/// A part, where it starts in seconds, its gain, and its samples.
struct Layer(usize, f32, f32, Vec<f32>);

fn n(seconds: f32) -> usize {
    (seconds * RATE as f32) as usize
}

fn decay(tau: f32, dur: f32, attack: f32) -> Vec<f32> {
    let a = n(attack).max(1) as f32;
    (0..n(dur)).map(|i| (i as f32 / a).min(1.0) * (-(i as f32) / (tau * RATE as f32)).exp()).collect()
}

/// A sine gliding exponentially from `f0` to `f1` Hz under `env`.
fn sine(f0: f32, f1: f32, env: Vec<f32>) -> Vec<f32> {
    let len = env.len() as f32;
    let mut phase = 0.0f32;
    env.iter()
        .enumerate()
        .map(|(i, e)| {
            phase += 2.0 * PI * f0 * (f1 / f0).powf(i as f32 / len) / RATE as f32;
            e * phase.sin()
        })
        .collect()
}

/// White noise under `env`, one-pole low-passed at `cutoff`, or the rest of it if `high`.
fn noise(env: Vec<f32>, cutoff: f32, seed: u32, high: bool) -> Vec<f32> {
    let mut state = seed.wrapping_mul(0x9E37_79B9) | 1;
    let k = 1.0 - (-2.0 * PI * cutoff / RATE as f32).exp();
    let mut low = 0.0f32;
    env.iter()
        .map(|e| {
            state ^= state << 13;
            state ^= state >> 17;
            state ^= state << 5;
            let x = state as f32 / u32::MAX as f32 * 2.0 - 1.0;
            low += k * (x - low);
            e * if high { x - low } else { low }
        })
        .collect()
}

fn mix(layers: &[Layer], parts: &[f32; PARTS.len()]) -> Vec<f32> {
    let len = layers.iter().map(|l| n(l.1) + l.3.len()).max().unwrap_or(0);
    let mut out = vec![0.0; len];
    for Layer(part, at, gain, samples) in layers {
        let g = gain * parts[*part];
        for (o, s) in out[n(*at)..].iter_mut().zip(samples) {
            *o += g * s;
        }
    }
    out
}

/// The RMS of the loudest 30 ms.
fn loudest(samples: &[f32]) -> f32 {
    let w = n(0.03);
    (0..samples.len().saturating_sub(w).max(1))
        .step_by(w / 4)
        .map(|i| {
            let window = &samples[i..(i + w).min(samples.len())];
            (window.iter().map(|s| s * s).sum::<f32>() / w as f32).sqrt()
        })
        .fold(0.0, f32::max)
}

const KA: usize = 0;
const SHIFT: usize = 1;
const CHUNK: usize = 2;
const DISARM: usize = 3;
const SEND: usize = 4;

fn arm() -> Vec<Layer> {
    let ramp = (0..n(LEAD_S)).map(|i| (i as f32 / n(LEAD_S) as f32).powi(2)).collect();
    vec![
        Layer(KA, 0.0, 0.35, noise(decay(0.002, 0.015, 0.001), 3000.0, 1, true)),
        Layer(KA, 0.0, 0.12, sine(2600.0, 2400.0, decay(0.006, 0.03, 0.001))),
        Layer(SHIFT, 0.0, 0.10, noise(ramp, 1500.0, 2, false)),
        Layer(CHUNK, LEAD_S, 1.0, sine(190.0, 110.0, decay(0.025, 0.12, 0.002))),
        Layer(CHUNK, LEAD_S, 0.6, noise(decay(0.004, 0.03, 0.001), 2500.0, 3, false)),
        Layer(CHUNK, LEAD_S, 0.25, sine(950.0, 900.0, decay(0.012, 0.06, 0.001))),
    ]
}

fn disarm() -> Vec<Layer> {
    vec![
        Layer(DISARM, 0.0, 1.0, sine(350.0, 1200.0, decay(0.014, 0.06, 0.002))),
        Layer(DISARM, 0.0, 0.5, noise(decay(0.01, 0.05, 0.003), 1200.0, 4, false)),
    ]
}

fn send() -> Vec<Layer> {
    vec![
        Layer(SEND, 0.0, 1.0, noise(decay(0.0015, 0.01, 0.0003), 2000.0, 5, true)),
        Layer(SEND, 0.0, 0.35, sine(1900.0, 1800.0, decay(0.008, 0.04, 0.001))),
        Layer(SEND, 0.0, 0.6, sine(260.0, 200.0, decay(0.012, 0.05, 0.001))),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn each_sound_peaks_at_its_level() {
        let s = render(1.0, &[1.0; PARTS.len()]);
        let rms = |pcm: &[i16]| loudest(&pcm.iter().map(|&x| x as f32 / 32767.0).collect::<Vec<_>>());
        assert!((rms(&s.send) - 0.05).abs() < 0.002, "{}", rms(&s.send));
        assert!((rms(&s.arm) - 0.015).abs() < 0.001);
        assert_eq!(s.arm.len(), n(LEAD_S) + n(0.12));
    }

    #[test]
    fn a_silent_part_is_silent() {
        let mut parts = [1.0; PARTS.len()];
        parts[DISARM] = 0.0;
        assert!(render(1.0, &parts).disarm.iter().all(|&x| x == 0));
    }
}
