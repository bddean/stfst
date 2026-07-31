mod oracle;

use std::path::PathBuf;

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use stfst_compile::lexc::Align;
use oracle::{HfstLookup, Lookup};
use stfst_core::{golden, System};

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
        /// Also write a golden test file.
        #[arg(long)]
        golden: Option<PathBuf>,
    },
    /// Compile every dictionary in a recipe.
    Compile {
        recipe: PathBuf,
        #[arg(short, long, default_value = "build/dicts")]
        out: PathBuf,
        #[arg(long, default_value = "build/work")]
        work: PathBuf,
    },
    /// Look up outlines in a compiled artifact.
    Lookup {
        artifact: PathBuf,
        #[arg(long)]
        system: PathBuf,
        /// Outlines as a Plover dictionary spells them; stdin if absent.
        outlines: Vec<String>,
    },
    /// Run a golden test file against a compiled artifact.
    Test {
        artifact: PathBuf,
        golden: PathBuf,
    },
    /// Emit the generated xfst prelude for a system.
    Prelude {
        #[arg(long)]
        system: PathBuf,
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
            golden: golden_out,
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
            if let Some(path) = golden_out {
                let cases: Vec<golden::Case> = imported
                    .golden
                    .iter()
                    .map(|(tape, record)| golden::Case {
                        tape: tape.clone(),
                        expect: vec![record.clone()],
                    })
                    .collect();
                let header = format!(
                    "! {} cases from {}. A regression file for the import pipeline,\n\
                     ! not an oracle: the expectations come from our own translation\n\
                     ! grammar, which is unit tested separately.\n",
                    cases.len(),
                    json.display()
                );
                std::fs::write(path, header + &golden::render(&cases))?;
            }
            match out {
                Some(path) => std::fs::write(path, imported.lexc)?,
                None => print!("{}", imported.lexc),
            }
        }
        Command::Compile { recipe, out, work } => {
            stfst_compile::compile_recipe(&recipe, &out, &work)?;
        }
        Command::Lookup {
            artifact,
            system,
            outlines,
        } => {
            let sys = System::load(&system)?;
            let outlines = or_stdin(outlines)?;
            let mut tapes = Vec::new();
            for outline in &outlines {
                tapes.push(sys.to_tape(&sys.parse_outline(outline)?));
            }
            let results = HfstLookup::open(&artifact)?.lookup_batch(&tapes)?;
            for ((outline, tape), records) in outlines.iter().zip(&tapes).zip(&results) {
                if records.is_empty() {
                    println!("{outline}\t{tape}\t-");
                } else {
                    println!("{outline}\t{tape}\t{}", records.join("\t"));
                }
            }
        }
        Command::Test { artifact, golden: path } => {
            let text = std::fs::read_to_string(&path)
                .with_context(|| format!("reading {}", path.display()))?;
            let cases = golden::parse(&text)?;
            let bad = golden::check_expectations(&cases);
            for (tape, record, why) in &bad {
                eprintln!("{}: expectation {record:?} is not a legal record: {why}", tape);
            }
            if !bad.is_empty() {
                anyhow::bail!("{} illegal expectations in {}", bad.len(), path.display());
            }

            let tapes: Vec<String> = cases.iter().map(|c| c.tape.clone()).collect();
            let results = HfstLookup::open(&artifact)?.lookup_batch(&tapes)?;
            let mut failed = 0;
            for (case, got) in cases.iter().zip(results) {
                let outcome = golden::Outcome {
                    tape: case.tape.clone(),
                    expect: case.expect.clone(),
                    got,
                };
                if !outcome.passed() {
                    failed += 1;
                    println!(
                        "FAIL {}\n  expected {:?}\n  got      {:?}",
                        outcome.tape, outcome.expect, outcome.got
                    );
                }
            }
            println!("{} cases, {failed} failed", cases.len());
            if failed > 0 {
                anyhow::bail!("{failed} golden cases failed");
            }
        }
        Command::Prelude { system } => {
            print!("{}", stfst_compile::prelude::generate(&System::load(&system)?));
        }
        Command::Tape { system, outlines } => {
            let sys = System::load(&system)?;
            for outline in or_stdin(outlines)? {
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

/// Read outlines from stdin when none are given as arguments, so a caller can
/// pipe 20,000 of them without hitting the argv limit.
fn or_stdin(given: Vec<String>) -> Result<Vec<String>> {
    if !given.is_empty() {
        return Ok(given);
    }
    Ok(std::io::stdin()
        .lines()
        .collect::<std::io::Result<Vec<_>>>()?
        .into_iter()
        .filter(|l| !l.trim().is_empty())
        .collect())
}
