mod bench;
mod lookup;
mod translate;

use std::path::PathBuf;

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use lookup::Engine;
use stfst_compile::lexc::Align;
use stfst_core::{golden, Capabilities, System};

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
        /// Also write every entry as `tape<TAB>symbols`, symbols separated by
        /// US (0x1f). What an encoding experiment reads.
        #[arg(long)]
        pairs: Option<PathBuf>,
    },
    /// Read a flat dictionary written in the record language, to lexc.
    Plain {
        source: PathBuf,
        #[arg(long)]
        system: PathBuf,
        /// Where to write the lexc; stdout if absent.
        #[arg(short, long)]
        out: Option<PathBuf>,
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
        #[arg(long, value_enum, default_value_t = Engine::Stfst)]
        engine: Engine,
        /// Read the arguments as key tapes, not as Plover outlines.
        #[arg(long)]
        tape: bool,
    },
    /// Look up the outlines that write a record, which is the direction a
    /// suggestions window asks in.
    Reverse {
        artifact: PathBuf,
        #[arg(long)]
        system: PathBuf,
        /// Records in printed form; stdin if absent.
        records: Vec<String>,
        /// How many outlines to show per record, shortest first.
        #[arg(long, default_value_t = 5)]
        limit: usize,
    },
    /// Run a golden test file against a compiled artifact.
    Test {
        artifact: PathBuf,
        golden: PathBuf,
        #[arg(long, value_enum, default_value_t = Engine::Stfst)]
        engine: Engine,
    },
    /// Measure lookup latency in process, which is the number M0 asks for.
    Bench {
        artifact: PathBuf,
        #[arg(long)]
        system: PathBuf,
        /// Read stdin as key tapes, not as Plover outlines.
        #[arg(long)]
        tape: bool,
        /// Passes over the whole probe list.
        #[arg(long, default_value_t = 1)]
        reps: usize,
    },
    /// Translate a stroke stream through a dictionary stack.
    Translate {
        /// Artifact directories. Priority comes from each manifest.
        artifacts: Vec<PathBuf>,
        #[arg(long)]
        system: PathBuf,
        /// One stroke per line on stdin; `!undo` takes one back.
        #[arg(long)]
        strokes: Option<PathBuf>,
        /// Print only the summary. What a per-stroke measurement needs, because
        /// printing the whole buffer each stroke costs more than translating it.
        #[arg(long)]
        quiet: bool,
        /// Where `stfst morph` wrote the artifacts. Without them a host cannot
        /// inflect, so a dictionary that asks to is refused.
        #[arg(long, default_value = "build/morph")]
        morph: PathBuf,
    },
    /// Join word and suffix pairs, for measuring the orthography rules against
    /// Plover's own. One `word<TAB>suffix` per line.
    Ortho {
        #[arg(long)]
        pairs: PathBuf,
        /// Where `stfst morph` wrote the artifacts.
        #[arg(long, default_value = "build/morph")]
        morph: PathBuf,
    },
    /// Inflect words, for measuring `{:infl:…}` against the lexicon it came from.
    /// One `word<TAB>mode` per line.
    Infl {
        #[arg(long)]
        pairs: PathBuf,
        #[arg(long, default_value = "build/morph")]
        morph: PathBuf,
    },
    /// Compile the morphology artifacts: the orthography rules and the two
    /// lexicons. Not a dictionary, so not part of `compile`: nothing here is over
    /// the key tape. `scripts/gen-morph.py` writes the lexc these read.
    Morph {
        #[arg(long, default_value = "morphology/ortho.xfst")]
        rules: PathBuf,
        /// Directory holding `words.lexc` and `infl.lexc`.
        #[arg(long, default_value = "build/morph")]
        lexicons: PathBuf,
        #[arg(long, default_value = "build/morph")]
        out: PathBuf,
        #[arg(long, default_value = "build/morph-work")]
        work: PathBuf,
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
            pairs,
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
            if let Some(path) = pairs {
                let mut lines = String::new();
                for (tape, symbols) in &imported.pairs {
                    lines.push_str(tape);
                    lines.push('\t');
                    lines.push_str(&symbols.join("\u{1f}"));
                    lines.push('\n');
                }
                std::fs::write(path, lines)?;
            }
            match out {
                Some(path) => std::fs::write(path, imported.lexc)?,
                None => print!("{}", imported.lexc),
            }
        }
        Command::Plain {
            source,
            system,
            out,
            golden: golden_out,
        } => {
            let sys = System::load(&system)?;
            let text = std::fs::read_to_string(&source)
                .with_context(|| format!("reading {}", source.display()))?;
            let compiled = stfst_compile::plain::compile(&text, &sys, Align::Pairwise)
                .map_err(|errors| {
                    anyhow::anyhow!(
                        "{}:\n{}",
                        source.display(),
                        errors
                            .iter()
                            .map(|e| format!("  {e}"))
                            .collect::<Vec<_>>()
                            .join("\n")
                    )
                })?;
            eprintln!("{} entries", compiled.entries);
            if let Some(path) = golden_out {
                let cases: Vec<golden::Case> = compiled
                    .golden
                    .iter()
                    .map(|(tape, record)| golden::Case {
                        tape: tape.clone(),
                        expect: vec![record.clone()],
                    })
                    .collect();
                let header = format!(
                    "! {} cases from {}. The source is already outline to record, so\n\
                     ! this file checks the outline parse and the compile, not the\n\
                     ! meaning of an entry.\n",
                    cases.len(),
                    source.display()
                );
                std::fs::write(path, header + &golden::render(&cases))?;
            }
            match out {
                Some(path) => std::fs::write(path, compiled.lexc)?,
                None => print!("{}", compiled.lexc),
            }
        }
        Command::Compile { recipe, out, work } => {
            stfst_compile::compile_recipe(&recipe, &out, &work)?;
        }
        Command::Lookup {
            artifact,
            system,
            outlines,
            engine,
            tape,
        } => {
            let sys = System::load(&system)?;
            let outlines = or_stdin(outlines)?;
            let tapes = to_tapes(&sys, &outlines, tape)?;
            let results = lookup::open(&artifact, engine)?.lookup_batch(&tapes)?;
            for ((outline, tape), records) in outlines.iter().zip(&tapes).zip(&results) {
                if records.is_empty() {
                    println!("{outline}\t{tape}\t-");
                } else {
                    println!("{outline}\t{tape}\t{}", records.join("\t"));
                }
            }
        }
        Command::Reverse {
            artifact,
            system,
            records,
            limit,
        } => {
            let sys = System::load(&system)?;
            let dict = stfst_dict::Dict::open(&artifact, &sys, &Capabilities::all())?;
            for text in or_stdin(records)? {
                let record = stfst_core::Record::parse_display(&text)?;
                let found = dict.outlines(&record, limit)?;
                let outlines: Vec<String> = found
                    .tapes
                    .iter()
                    .map(|tape| Ok(sys.format_outline(&sys.from_tape(tape)?)))
                    .collect::<Result<_>>()?;
                let more = if found.truncated { "\t..." } else { "" };
                match outlines.is_empty() {
                    true => println!("{text}\t-{more}"),
                    false => println!("{text}\t{}{more}", outlines.join("\t")),
                }
            }
        }
        Command::Test {
            artifact,
            golden: path,
            engine,
        } => {
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
            let results = lookup::open(&artifact, engine)?.lookup_batch(&tapes)?;
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
        Command::Bench {
            artifact,
            system,
            tape,
            reps,
        } => {
            let sys = System::load(&system)?;
            let tapes = to_tapes(&sys, &or_stdin(Vec::new())?, tape)?;
            bench::run(&artifact, &tapes, reps)?;
        }
        Command::Translate {
            artifacts,
            system,
            strokes,
            quiet,
            morph,
        } => {
            let lines = match strokes {
                Some(path) => std::fs::read_to_string(&path)
                    .with_context(|| format!("reading {}", path.display()))?
                    .lines()
                    .map(str::to_string)
                    .collect(),
                None => or_stdin(Vec::new())?,
            };
            translate::run(&artifacts, &system, lines, quiet, &morph)?;
        }
        Command::Ortho { pairs, morph } => {
            let text = std::fs::read_to_string(&pairs)
                .with_context(|| format!("reading {}", pairs.display()))?;
            let morph = stfst_text::morph::Morph::open(&morph)?;
            for line in text.lines() {
                let Some((word, suffix)) = line.split_once('\t') else {
                    continue;
                };
                println!("{}", morph.join(word, suffix));
            }
        }
        Command::Infl { pairs, morph } => {
            let text = std::fs::read_to_string(&pairs)
                .with_context(|| format!("reading {}", pairs.display()))?;
            let morph = stfst_text::morph::Morph::open(&morph)?;
            for line in text.lines() {
                let Some((word, mode)) = line.split_once('\t') else {
                    continue;
                };
                match morph.inflect(word, mode) {
                    Some(form) => println!("{form}"),
                    None => println!("!{mode}"),
                }
            }
        }
        Command::Morph {
            rules,
            lexicons,
            out,
            work,
        } => {
            for line in stfst_compile::pipeline::compile_morph(&rules, &lexicons, &work, &out)? {
                println!("{line}");
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

/// Probes as key tapes, whether they arrived as tapes or as Plover outlines.
fn to_tapes(sys: &System, given: &[String], already_tapes: bool) -> Result<Vec<String>> {
    given
        .iter()
        .map(|s| {
            if already_tapes {
                Ok(s.clone())
            } else {
                Ok(sys.to_tape(&sys.parse_outline(s)?))
            }
        })
        .collect()
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
