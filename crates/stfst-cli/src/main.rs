use std::path::PathBuf;

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use stfst_compile::lexc::Align;
use stfst_core::System;

#[derive(Parser)]
#[command(name = "stfst", about = "steno dictionaries as finite-state transducers")]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Import a Plover JSON dictionary to lexc over the key tape.
    Import {
        json: PathBuf,
        #[arg(long)]
        system: PathBuf,
        /// Where to write the lexc; stdout if absent.
        #[arg(short, long)]
        out: Option<PathBuf>,
        /// Write every skipped entry, not just the counts.
        #[arg(long)]
        report: Option<PathBuf>,
        /// Tape alignment. `delayed` shares translations across outlines.
        #[arg(long, default_value = "delayed")]
        align: String,
    },
    /// Show how a system reads an outline, and what tape it becomes.
    Tape {
        #[arg(long)]
        system: PathBuf,
        outlines: Vec<String>,
    },
}

fn main() -> Result<()> {
    match Cli::parse().command {
        Command::Import {
            json,
            system,
            out,
            report,
            align,
        } => {
            let sys = System::load(&system)?;
            let align = match align.as_str() {
                "pairwise" => Align::Pairwise,
                "delayed" => Align::Delayed,
                other => anyhow::bail!("unknown alignment {other:?}"),
            };
            let imported = stfst_compile::json::import_file(&json, &sys, align)
                .with_context(|| format!("importing {}", json.display()))?;
            eprintln!("{}", imported.report.summary());
            if let Some(path) = report {
                let mut lines = String::new();
                for (outline, why) in &imported.report.bad_outlines {
                    lines.push_str(&format!("outline\t{outline}\t{why}\n"));
                }
                for (outline, translation, why) in &imported.report.bad_translations {
                    lines.push_str(&format!("translation\t{outline}\t{translation}\t{why}\n"));
                }
                std::fs::write(path, lines)?;
            }
            match out {
                Some(path) => std::fs::write(path, imported.lexc)?,
                None => print!("{}", imported.lexc),
            }
        }
        Command::Tape { system, outlines } => {
            let sys = System::load(&system)?;
            let outlines: Vec<String> = if outlines.is_empty() {
                std::io::stdin()
                    .lines()
                    .collect::<std::io::Result<Vec<_>>>()?
                    .into_iter()
                    .filter(|l| !l.trim().is_empty())
                    .collect()
            } else {
                outlines
            };
            for outline in outlines {
                match sys.parse_outline(&outline) {
                    Ok(o) => println!(
                        "{outline}\t{}\t{}",
                        sys.to_tape(&o),
                        sys.format_outline(&o)
                    ),
                    Err(e) => println!("{outline}\t!\t{e}"),
                }
            }
        }
    }
    Ok(())
}
