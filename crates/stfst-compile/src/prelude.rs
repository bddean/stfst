//! The steno prelude, generated from a system file.
//!
//! Generating it rather than shipping a fixed file is what makes the key
//! mapping configuration: a Michela board or a hacked keymap changes `order` and
//! `[keys]`, and every acceptor here follows. The record half is generated from
//! the same tag list `stfst_core::record` parses, so the acceptor and the parser
//! cannot drift.

use stfst_core::record::{all_tags, copy_tag, repeat_tag, FLAG_TAGS, MAX_COUNT, MOD_TAGS};
use stfst_core::System;

/// xfst needs `"` around anything it would otherwise read as an operator, and
/// `^ + * -` are all operators.
fn q(symbol: char) -> String {
    format!("\"{symbol}\"")
}

pub fn generate(system: &System) -> String {
    let mut out = String::new();
    out.push_str(&format!(
        "! Generated from system {:?}. Do not edit.\n\n",
        system.name
    ));

    out.push_str("! ---- upper tape: one symbol per key, in steno order\n");
    out.push_str(&format!("define Sep {} ;\n", q(system.separator)));

    let keys: Vec<String> = system.keys().iter().map(|k| q(k.symbol)).collect();
    out.push_str(&format!("define AnyKey [ {} ] ;\n", keys.join(" | ")));

    // Steno order is the concatenation of optional keys, so a stroke is in order
    // by construction and cannot repeat a key.
    let optional: Vec<String> = system
        .keys()
        .iter()
        .map(|k| format!("({})", q(k.symbol)))
        .collect();
    out.push_str(&format!(
        "define Stroke [ [ {} ] - 0 ] ;\n",
        optional.join(" ")
    ));
    out.push_str("define Outline [ Stroke [ Sep Stroke ]* ] ;\n\n");

    out.push_str(
        "! ---- priority union, spelled out\n\
         ! `.P.` complements over the whole alphabet, text included, and nesting five\n\
         ! of those wanted a gigabyte. Every caller prefers on the input side, where\n\
         ! the alphabet is small, so subtract the domains there and compose.\n",
    );
    out.push_str("define PU(A, B) [ A | [ [ B.u - A.u ] .o. B ] ] ;\n\n");

    out.push_str("! ---- lower tape: the record language\n");
    let tags: Vec<String> = all_tags().iter().map(|t| format!("\"{t}\"")).collect();
    out.push_str(&format!("define Tag [ {} ] ;\n", tags.join(" | ")));
    out.push_str("define Text \\Tag ;\n");
    // A keysym is open-ended text; the modifiers held with it are five closed
    // values, so they are tags inside the item and in canonical order.
    let mods: Vec<String> = MOD_TAGS.iter().map(|t| format!("(\"{t}\")")).collect();
    out.push_str(&format!("define Mods [ {} ] ;\n", mods.join(" ")));
    out.push_str("define KeyItem [ \"[KEY]\" Text+ Mods \"[END]\" ] ;\n");
    out.push_str("define CmdItem [ \"[CMD]\" Text+ \"[ARG]\" Text* \"[END]\" ] ;\n");
    out.push_str("define MetaItem [ \"[META]\" Text+ \"[ARG]\" Text* \"[END]\" ] ;\n");
    let copies: Vec<String> = (1..=MAX_COUNT)
        .map(|n| format!("\"{}\"", copy_tag(n)))
        .collect();
    out.push_str(&format!("define CopyItem [ {} ] ;\n", copies.join(" | ")));
    out.push_str("define Item [ Text | KeyItem | CmdItem | MetaItem | CopyItem ] ;\n\n");

    let repeats: Vec<String> = (2..=MAX_COUNT)
        .map(|n| format!("\"{}\"", repeat_tag(n)))
        .collect();
    out.push_str(&format!("define Rep [ {} ] ;\n", repeats.join(" | ")));
    out.push_str(
        "define Record [ Item* (\"[CANCEL_SPACE]\" | \"[RETRO_SPACE]\") (\"[ATTACH_L]\") \
         (\"[ATTACH_R]\") (\"[GLUE]\") (\"[CAP_NEXT]\" | \"[LOWER_NEXT]\") (\"[CARRY_CAP]\") \
         (Rep) ] ;\n\n",
    );

    out.push_str("! ---- canonical order\n");
    out.push_str(
        "! Each hoist moves its flag to the end, and its pass-through is \"any symbol\n\
         ! but this flag\" so a later hoist still finds its own. Flags go last because\n\
         ! the keys that set them are last in steno order, so a dictionary emits them\n\
         ! where it reads them and the lookup never guesses. See docs/latency.md.\n",
    );
    let mut hoists: Vec<String> = Vec::new();
    for tag in FLAG_TAGS {
        let name = format!("Hoist{}", camel(tag));
        out.push_str(&hoist(&name, tag));
        hoists.push(name);
    }
    // Rep is a set of symbols, not one, so each member gets its own hoist.
    for n in 2..=MAX_COUNT {
        let tag = repeat_tag(n);
        let name = format!("HoistRep{n}");
        out.push_str(&hoist(&name, &tag));
        hoists.push(name);
    }
    // Each hoist appends after what the previous one appended, so composing in
    // canonical order leaves the flags in it.
    out.push_str(&format!(
        "define Canonical [ {} ] ;\n",
        hoists.join(" .o. ")
    ));

    out
}

/// `[CANCEL_SPACE]` -> `CancelSpace`. xfst identifiers are alphanumeric, so an
/// underscore in a generated name is a parse error several lines later.
fn camel(tag: &str) -> String {
    let mut out = String::new();
    let mut upper = true;
    for c in tag.chars() {
        match c {
            '[' | ']' => {}
            '_' => upper = true,
            c if upper => {
                out.extend(c.to_uppercase());
                upper = false;
            }
            c => out.extend(c.to_lowercase()),
        }
    }
    out
}

fn hoist(name: &str, tag: &str) -> String {
    format!(
        "define {name} [ [ \\\"{tag}\"* [ \"{tag}\" .x. 0 ] \\\"{tag}\"* [ 0 .x. \"{tag}\" ] ] \
         | \\\"{tag}\"* ] ;\n"
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sys() -> System {
        System::parse(include_str!("../../../systems/stenotype-extended.toml")).unwrap()
    }

    #[test]
    fn operators_are_quoted() {
        let p = generate(&sys());
        assert!(p.contains("define Sep \"/\" ;"));
        // ^ + * would otherwise read as power, Kleene plus and Kleene star.
        assert!(p.contains("\"^\""));
        assert!(p.contains("\"+\""));
        assert!(p.contains("\"*\""));
    }

    #[test]
    fn generated_identifiers_are_alphanumeric() {
        for line in generate(&sys()).lines() {
            if let Some(rest) = line.strip_prefix("define ") {
                // A parameterised define names itself `PU(A, B)`, so the
                // identifier ends at the parameter list.
                let name = rest.split_whitespace().next().unwrap();
                let name = name.split('(').next().unwrap();
                assert!(
                    name.chars().all(|c| c.is_ascii_alphanumeric()),
                    "xfst rejects {name:?} as an identifier"
                );
            }
        }
    }

    #[test]
    fn every_key_appears_once_in_the_stroke_acceptor() {
        let p = generate(&sys());
        let line = p
            .lines()
            .find(|l| l.starts_with("define Stroke"))
            .expect("stroke acceptor");
        for key in sys().keys() {
            let quoted = format!("({})", q(key.symbol));
            assert_eq!(
                line.matches(&quoted).count(),
                1,
                "{} appears wrong number of times",
                key.symbol
            );
        }
    }
}
