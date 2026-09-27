//! Looking up a tape in a compiled artifact, two ways.
//!
//! `stfst-ol` is the reader a host embeds. `hfst-optimized-lookup` is a
//! subprocess and links GPLv3 code, so it is here for one purpose: to hold our
//! reader honest. Any tape the two disagree on is our bug.

use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use anyhow::{bail, Result};
use stfst_ol::Automaton;

pub trait Lookup {
    /// Every record the artifact returns for each tape, in the order given.
    fn lookup_batch(&self, tapes: &[String]) -> Result<Vec<Vec<String>>>;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, clap::ValueEnum)]
pub enum Engine {
    /// Our own reader, mapped in process.
    Stfst,
    /// HFST's, as a subprocess.
    Hfst,
}

pub fn open(artifact: &Path, engine: Engine) -> Result<Box<dyn Lookup>> {
    let forward = forward(artifact)?;
    Ok(match engine {
        Engine::Stfst => Box::new(Automaton::open(&forward)?),
        Engine::Hfst => Box::new(HfstLookup { artifact: forward }),
    })
}

pub fn forward(artifact: &Path) -> Result<PathBuf> {
    let forward = if artifact.is_dir() {
        artifact.join("forward.hfstol")
    } else {
        artifact.to_path_buf()
    };
    if !forward.exists() {
        bail!("no automaton at {}", forward.display());
    }
    Ok(forward)
}

impl Lookup for Automaton {
    fn lookup_batch(&self, tapes: &[String]) -> Result<Vec<Vec<String>>> {
        // A tape using a symbol the automaton does not have matches nothing,
        // which is the same answer as a tape with no path.
        Ok(tapes
            .iter()
            .map(|t| self.lookup(t).unwrap_or_default())
            .collect())
    }
}

struct HfstLookup {
    artifact: PathBuf,
}

impl Lookup for HfstLookup {
    fn lookup_batch(&self, tapes: &[String]) -> Result<Vec<Vec<String>>> {
        let mut child = Command::new("hfst-optimized-lookup")
            .arg("-q")
            .arg(&self.artifact)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .map_err(|e| stfst_compile::pipeline::spawn_error("hfst-optimized-lookup", e))?;
        // Write from a thread. hfst answers as it reads, so filling its stdout
        // pipe while we still hold the pen deadlocks both ends.
        let mut stdin = child.stdin.take().expect("piped");
        let batch = tapes.to_vec();
        let writer = std::thread::spawn(move || {
            for tape in &batch {
                if writeln!(stdin, "{tape}").is_err() {
                    return; // the child died; its stderr is the better report
                }
            }
        });
        let out = child.wait_with_output()?;
        writer.join().map_err(|_| anyhow::anyhow!("writer panicked"))?;
        if !out.status.success() {
            bail!(
                "hfst-optimized-lookup failed: {}",
                String::from_utf8_lossy(&out.stderr)
            );
        }
        Ok(collect(&String::from_utf8_lossy(&out.stdout), tapes))
    }
}

/// hfst prints one line per result, `tape<TAB>record`, and marks a miss with a
/// third field: `tape<TAB>tape<TAB>+?`. An empty second field is not a miss, it
/// is an entry whose record is empty, which `#U` genuinely is.
fn collect(stdout: &str, tapes: &[String]) -> Vec<Vec<String>> {
    let mut by_tape: std::collections::HashMap<&str, Vec<String>> =
        std::collections::HashMap::new();
    for line in stdout.lines() {
        let mut fields = line.split('\t');
        let (Some(tape), Some(value)) = (fields.next(), fields.next()) else {
            continue;
        };
        if fields.next() == Some("+?") {
            by_tape.entry(tape).or_default();
            continue;
        }
        by_tape.entry(tape).or_default().push(value.to_string());
    }
    tapes
        .iter()
        .map(|t| by_tape.get(t.as_str()).cloned().unwrap_or_default())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn no_path_reads_as_no_records() {
        let out = "#HR\t#HR\t+?\n\n#HRU\t!\n";
        let got = collect(out, &["#HR".into(), "#HRU".into()]);
        assert_eq!(got[0], Vec::<String>::new());
        assert_eq!(got[1], vec!["!"]);
    }

    #[test]
    fn an_empty_record_is_not_a_miss() {
        let got = collect("#U\t\n", &["#U".into()]);
        assert_eq!(got[0], vec![""], "#U maps to the empty record");
    }

    #[test]
    fn several_records_for_one_tape_are_kept() {
        let out = "Sr\tone\nSr\ttwo\n";
        assert_eq!(collect(out, &["Sr".into()])[0], vec!["one", "two"]);
    }
}
