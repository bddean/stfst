//! Importing a Plover JSON dictionary.
//!
//! This is an import, not a code generator: the JSON is the data, there is no
//! theory in it to preserve. Generative dictionaries are hand-written source
//! instead.

use std::collections::BTreeMap;
use std::path::Path;

use stfst_core::System;

use crate::lexc::{self, Align};
use crate::translate::{translate, TranslateError};

#[derive(Debug, Default)]
pub struct ImportReport {
    pub entries: usize,
    pub imported: usize,
    /// Outlines the system could not parse, with the reason.
    pub bad_outlines: Vec<(String, String)>,
    /// Translations with no record equivalent, with the reason.
    pub bad_translations: Vec<(String, String, String)>,
}

impl ImportReport {
    pub fn summary(&self) -> String {
        format!(
            "{} of {} entries imported; {} outlines unparseable, {} translations unsupported",
            self.imported,
            self.entries,
            self.bad_outlines.len(),
            self.bad_translations.len()
        )
    }
}

pub struct Import {
    pub lexc: String,
    pub report: ImportReport,
    /// Golden cases: `(tape, record)`. Every entry whose record carries a tag,
    /// since that is where the translation grammar can be wrong, plus a fixed
    /// stride through the rest. Deterministic, so the file does not churn.
    pub golden: Vec<(String, String)>,
}

pub fn import_file(
    path: impl AsRef<Path>,
    system: &System,
    align: Align,
) -> anyhow::Result<Import> {
    let text = std::fs::read_to_string(path.as_ref())?;
    let raw: BTreeMap<String, String> = serde_json::from_str(&text)?;
    Ok(import(&raw, system, align))
}

pub fn import(raw: &BTreeMap<String, String>, system: &System, align: Align) -> Import {
    let mut report = ImportReport {
        entries: raw.len(),
        ..Default::default()
    };
    let mut entries: Vec<(String, String)> = Vec::with_capacity(raw.len());
    let mut golden: Vec<(String, String)> = Vec::new();
    const STRIDE: usize = 500;

    for (outline, translation) in raw {
        let parsed = match system.parse_outline(outline) {
            Ok(o) => o,
            Err(e) => {
                report.bad_outlines.push((outline.clone(), e.to_string()));
                continue;
            }
        };
        let record = match translate(translation) {
            Ok(r) => r,
            Err(e) => {
                report.bad_translations.push((
                    outline.clone(),
                    translation.clone(),
                    match e {
                        TranslateError::Unsupported(d) => format!("unsupported {{{d}}}"),
                        other => other.to_string(),
                    },
                ));
                continue;
            }
        };
        let tape = system.to_tape(&parsed);
        let plain = record.to_string();
        if plain.contains('[') || report.imported % STRIDE == 0 {
            golden.push((tape.clone(), plain));
        }
        entries.push((
            lexc::escape(&tape),
            lexc::escape_symbols(&record.to_symbols()),
        ));
        report.imported += 1;
    }

    Import {
        lexc: lexc::lexicon(entries, align),
        report,
        golden,
    }
}
