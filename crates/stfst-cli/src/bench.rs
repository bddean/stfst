//! Per-lookup latency, which is the half of M0 that a subprocess cannot answer.

use std::path::Path;
use std::time::Instant;

use anyhow::Result;
use stfst_ol::Automaton;

pub fn run(artifact: &Path, tapes: &[String], reps: usize) -> Result<()> {
    anyhow::ensure!(!tapes.is_empty(), "no probes on stdin");
    let path = crate::lookup::forward(artifact)?;
    let opening = Instant::now();
    let fst = Automaton::open(&path)?;
    let open = opening.elapsed().as_nanos() as f64 / 1e6;

    let mut each = Vec::with_capacity(tapes.len() * reps);
    let mut hits = 0usize;
    for _ in 0..reps {
        for probe in tapes {
            let start = Instant::now();
            let records = fst.lookup(probe);
            each.push(start.elapsed().as_nanos() as u64);
            // Counting the hits is also what keeps the lookup from being
            // optimised away.
            if records.is_some_and(|r| !r.is_empty()) {
                hits += 1;
            }
        }
    }
    each.sort_unstable();
    let total: u64 = each.iter().sum();
    let at = |q: f64| each[((each.len() - 1) as f64 * q) as usize] as f64 / 1000.0;
    println!("open        {open:8.3} ms   mmap, no decode");
    println!("probes      {:8}      {hits} hits", each.len());
    println!("mean        {:8.3} us", total as f64 / each.len() as f64 / 1000.0);
    println!("median      {:8.3} us", at(0.5));
    println!("p95         {:8.3} us", at(0.95));
    println!("p99         {:8.3} us", at(0.99));
    println!("worst       {:8.3} us", at(1.0));
    println!(
        "throughput  {:8.0}      lookups/s, one thread",
        each.len() as f64 / (total as f64 / 1e9)
    );

    let (widest, mean) = frontier(&fst, tapes);
    println!("frontier    {mean:8.1}      paths per step on average, {widest} at the widest");
    Ok(())
}

/// How wide the search gets. Untimed, because it is the explanation rather than
/// the measurement: a lookup costs about what the frontier is wide.
fn frontier(fst: &Automaton, tapes: &[String]) -> (usize, f64) {
    let (mut widest, mut sum, mut steps) = (0usize, 0usize, 0usize);
    for probe in tapes {
        let Some(syms) = fst.alphabet().tokenize(probe) else {
            continue;
        };
        let mut cursor = fst.cursor();
        for s in syms {
            cursor.step(s);
            widest = widest.max(cursor.live());
            sum += cursor.live();
            steps += 1;
        }
    }
    (widest, sum as f64 / steps.max(1) as f64)
}
