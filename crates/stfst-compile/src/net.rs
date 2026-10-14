//! A stage's net, compiled on its own from lexc or from xfst that defines
//! `Stage`. What decides a net is here, so build.rs stamps nets with this file
//! and not with how a pipeline composes them.

use std::path::{Path, PathBuf};

use anyhow::Result;
use stfst_core::System;

use crate::pipeline::run;
use crate::prelude;
use crate::recipe::{resolve, Stage};

pub(crate) enum Source {
    Lexc(PathBuf),
    Xfst(String),
}

impl Source {
    pub(crate) fn of(stage: &Stage, base: &Path, system: &System) -> Result<Self> {
        Ok(match &stage.lexc {
            Some(src) => Source::Lexc(resolve(base, src)),
            None => {
                let mut script = prelude::generate_for(system, Some(&[]));
                for src in &stage.xfst {
                    let src = resolve(base, src);
                    script.push_str(
                        &std::fs::read_to_string(&src)
                            .map_err(|e| anyhow::anyhow!("reading {}: {e}", src.display()))?,
                    );
                }
                Source::Xfst(script)
            }
        })
    }

    /// What the net is a function of: the code here, and the source's text.
    pub(crate) fn key(&self) -> Result<u64> {
        let compiler = env!("STFST_NET_HASH").as_bytes();
        Ok(match self {
            Source::Lexc(src) => fnv(fnv(FNV, compiler), &std::fs::read(src)?),
            Source::Xfst(script) => fnv(fnv(fnv(FNV, compiler), b"xfst"), script.as_bytes()),
        })
    }

    /// Write the net to `out`.
    pub(crate) fn compile(&self, out: &Path) -> Result<()> {
        let path = |p: &Path| p.to_str().expect("a path that is UTF-8").to_owned();
        match self {
            Source::Lexc(src) => {
                run("hfst-lexc", &["-q", &path(src), "-o", &path(out)])?;
            }
            Source::Xfst(script) => {
                let file = out.with_extension("xfst");
                std::fs::write(&file, format!("{script}\npush Stage\nsave stack {}\n", out.display()))?;
                run("hfst-xfst", &["-F", &path(&file)])?;
                let _ = std::fs::remove_file(&file);
            }
        }
        Ok(())
    }
}

pub(crate) const FNV: u64 = 0xcbf2_9ce4_8422_2325;

/// FNV-1a, as build.rs hashes the compiler: stable across Rust releases.
pub(crate) fn fnv(mut hash: u64, bytes: &[u8]) -> u64 {
    for &b in bytes {
        hash = (hash ^ b as u64).wrapping_mul(0x0100_0000_01b3);
    }
    hash
}
