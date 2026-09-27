//! A fingerprint of everything that decides what the compiler writes, so an
//! artifact is rebuilt when a rule in the compiler changed and not whenever the
//! binary was relinked for some other crate.

use std::path::{Path, PathBuf};

/// Relative to the workspace root. The crates this one's output depends on, and
/// what pins their dependencies and HFST.
const INPUTS: &[&str] = &[
    "crates/stfst-compile/src",
    "crates/stfst-compile/Cargo.toml",
    "crates/stfst-core/src",
    "crates/stfst-core/Cargo.toml",
    "crates/stfst-ol/src",
    "crates/stfst-ol/Cargo.toml",
    "Cargo.lock",
    "flake.lock",
];

fn main() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let mut files = Vec::new();
    for input in INPUTS {
        let path = root.join(input);
        println!("cargo:rerun-if-changed={}", path.display());
        walk(&path, &mut files);
    }
    files.sort();
    // FNV-1a, because it is stable across Rust releases and std's hasher is not.
    let mut hash: u64 = 0xcbf2_9ce4_8422_2325;
    for file in &files {
        let rel = file.strip_prefix(&root).unwrap_or(file);
        let bytes = std::fs::read(file).unwrap_or_default();
        for b in rel.to_string_lossy().bytes().chain([0]).chain(bytes).chain([0]) {
            hash = (hash ^ b as u64).wrapping_mul(0x0100_0000_01b3);
        }
    }
    println!("cargo:rustc-env=STFST_COMPILER_HASH={hash:016x}");
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
