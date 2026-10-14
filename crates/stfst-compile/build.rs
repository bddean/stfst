//! Fingerprints of what decides what the compiler writes, so an artifact is
//! rebuilt when a rule in the compiler changed and not whenever the binary was
//! relinked for some other crate.
//!
//! One fingerprint for each kind of output, of only the files that decide it. A
//! file in none of them, such as lib.rs, only schedules the work: a change
//! there that changes an output needs `--force`.

use std::path::{Path, PathBuf};

/// Relative to the workspace root. What every output depends on: the crates it
/// reads systems and records with, and what pins their dependencies and HFST.
const COMMON: &[&str] = &[
    "crates/stfst-compile/Cargo.toml",
    "crates/stfst-compile/src/prelude.rs",
    "crates/stfst-core/src",
    "crates/stfst-core/Cargo.toml",
    "Cargo.lock",
    "flake.lock",
];

/// A stage's net.
const NET: &[&str] = &["crates/stfst-compile/src/net.rs"];

/// What a pipeline relates, from its stages' nets.
const PIPELINE: &[&str] = &["crates/stfst-compile/src/net.rs", "crates/stfst-compile/src/stages.rs"];

/// A dictionary's artifact.
const ARTIFACT: &[&str] = &[
    "crates/stfst-compile/src/pipeline.rs",
    "crates/stfst-compile/src/lexc.rs",
    "crates/stfst-compile/src/json.rs",
    "crates/stfst-compile/src/plain.rs",
    "crates/stfst-compile/src/recipe.rs",
    "crates/stfst-ol/src",
    "crates/stfst-ol/Cargo.toml",
];

fn main() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    for (name, inputs) in [("NET", NET), ("PIPELINE", PIPELINE), ("ARTIFACT", ARTIFACT)] {
        let hash = fingerprint(&root, COMMON.iter().chain(inputs));
        println!("cargo:rustc-env=STFST_{name}_HASH={hash:016x}");
    }
}

fn fingerprint<'a>(root: &Path, inputs: impl Iterator<Item = &'a &'a str>) -> u64 {
    let mut files = Vec::new();
    for input in inputs {
        let path = root.join(input);
        println!("cargo:rerun-if-changed={}", path.display());
        walk(&path, &mut files);
    }
    files.sort();
    // FNV-1a, because it is stable across Rust releases and std's hasher is not.
    let mut hash: u64 = 0xcbf2_9ce4_8422_2325;
    for file in &files {
        let rel = file.strip_prefix(root).unwrap_or(file);
        let bytes = std::fs::read(file).unwrap_or_default();
        for b in rel.to_string_lossy().bytes().chain([0]).chain(bytes).chain([0]) {
            hash = (hash ^ b as u64).wrapping_mul(0x0100_0000_01b3);
        }
    }
    hash
}

fn walk(path: &Path, out: &mut Vec<PathBuf>) {
    match std::fs::read_dir(path) {
        Ok(entries) => {
            for entry in entries.flatten() {
                walk(&entry.path(), out);
            }
        }
        Err(_) if path.is_file() => out.push(path.to_path_buf()),
        Err(_) => {}
    }
}
