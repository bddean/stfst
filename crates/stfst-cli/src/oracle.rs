//! Looking up a tape in a compiled artifact.
//!
//! `hfst-optimized-lookup` is the lookup here, which is fine for a testing CLI
//! and wrong for anything embedded: it is a subprocess, and HFST is GPLv3. The
//! seam exists so `stfst-ol` replaces it without the callers noticing.

use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use anyhow::{bail, Context, Result};

pub trait Lookup {
    /// Every record the artifact returns for each tape, in the same order as
    /// the tapes given.
    fn lookup_batch(&self, tapes: &[String]) -> Result<Vec<Vec<String>>>;
}

pub struct HfstLookup {
    artifact: PathBuf,
}

impl HfstLookup {
    pub fn open(artifact: &Path) -> Result<Self> {
        let forward = if artifact.is_dir() {
            artifact.join("forward.hfstol")
        } else {
            artifact.to_path_buf()
        };
        if !forward.exists() {
            bail!("no automaton at {}", forward.display());
        }
        Ok(HfstLookup { artifact: forward })
    }
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
            .context("spawning hfst-optimized-lookup")?;
        {
            let stdin = child.stdin.as_mut().expect("piped");
            for tape in tapes {
                writeln!(stdin, "{tape}")?;
            }
        }
        let out = child.wait_with_output()?;
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
