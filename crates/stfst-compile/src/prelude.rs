//! The steno prelude, generated from a system file.
//!
//! Generating it rather than shipping a fixed file is what makes the key
//! mapping configuration: a Michela board or a hacked keymap changes `order` and
//! `[keys]`, and every acceptor here follows. The record half is generated from
//! the same tag list `stfst_core::record` parses, so the acceptor and the parser
//! cannot drift.

use stfst_core::context::{self, Slot};
use stfst_core::fold::{ADD, FOLD, STRIP};
use stfst_core::host;
use stfst_core::window::{self, Class};
use stfst_core::record::{
    all_tags, copy_tag, is_optional_tag, repeat_tag, replace_tag, AFTER, FLAG_TAGS, MAX_COUNT,
    MOD_TAGS,
};
use stfst_core::System;

/// xfst needs `"` around anything it would otherwise read as an operator, and
/// `^ + * -` are all operators.
fn q(symbol: char) -> String {
    format!("\"{symbol}\"")
}

/// The whole prelude, with every operation the tables name.
pub fn generate(system: &System) -> String {
    generate_for(system, None)
}

/// The prelude for one grammar, naming only the operations that grammar mentions.
///
/// Every symbol an acceptor names is a symbol the record check has to complement
/// over, and the check is the expensive step: naming all thirty-one commands and
/// metas took `emily-symbols` from 29 seconds to 4 minutes and 25. So the acceptor
/// gets the vocabulary the grammar can actually use. Nothing else changes, because
/// the manifest is derived from the finished artifact and not from this.
pub fn generate_for(system: &System, used: Option<&[String]>) -> String {
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
    out.push_str("define Keys [ Stroke [ Sep Stroke ]* ] ;\n\n");

    out.push_str(
        "! ---- priority union, spelled out\n\
         ! `.P.` complements over the whole alphabet, text included, and nesting five\n\
         ! of those wanted a gigabyte. Every caller prefers on the input side, where\n\
         ! the alphabet is small, so subtract the domains there and compose.\n",
    );
    out.push_str("define PU(A, B) [ A | [ [ B.u - A.u ] .o. B ] ] ;\n\n");

    out.push_str(
        "! ---- context: what the host says before the keys\n\
         ! One value per slot at most, in table order, so a host cannot spell the\n\
         ! same context two ways. A dictionary reads the slots it wants and the\n\
         ! compiler writes what it read into the manifest.\n",
    );
    for slot in context::SLOTS {
        let values: Vec<String> = slot.symbols().map(|s| format!("\"{s}\"")).collect();
        out.push_str(&format!(
            "define {} [ {} ] ;\n",
            ctx_name(slot),
            values.join(" | ")
        ));
    }
    let optional_slots: Vec<String> = context::SLOTS
        .iter()
        .map(|s| format!("({})", ctx_name(s)))
        .collect();
    out.push_str(&format!(
        "define Ctx [ {} ] ;\n\n",
        optional_slots.join(" ")
    ));

    out.push_str(
        "! ---- the window: text the host has already written\n\
         ! One symbol per character, after the keys. A character is its class unless\n\
         ! the grammar names it, in which case the host sends `[=x]` and the rule can\n\
         ! read it. `Glyph` takes that named set as a parameter, because only the\n\
         ! dictionary knows what it named.\n",
    );
    for class in Class::ALL {
        out.push_str(&format!(
            "define {} \"{}\" ;\n",
            class_name(*class),
            class.tag()
        ));
    }
    let classes: Vec<&str> = Class::ALL.iter().map(|c| class_name(*c)).collect();
    out.push_str(&format!("define AnyClass [ {} ] ;\n", classes.join(" | ")));
    out.push_str("define Glyph(Sig) [ AnyClass | Sig ] ;\n");
    out.push_str(&format!("define Drop \"{}\" ;\n", window::DROP));
    out.push_str(&format!(
        "define WinOpen \"{}\" ;\ndefine WinEnd \"{}\" ;\n",
        window::WINDOW_OPEN,
        window::WINDOW_CLOSE
    ));
    // Permissive on purpose. What the window holds is the dictionary's business,
    // and this only has to say where it starts and stops for the outline check.
    out.push_str("define Win [ WinOpen [ \\WinEnd ]* WinEnd ] ;\n\n");

    out.push_str("! ---- lower tape: the record language\n");
    let tags: Vec<String> = all_tags()
        .iter()
        .filter(|t| {
            // The same reasoning as `named`: a tag no grammar here can emit does not
            // need to be in the alphabet the check complements over.
            !is_optional_tag(t) || used.is_none_or(|u| u.iter().any(|s| s == *t))
        })
        .map(|t| format!("\"{t}\""))
        .collect();
    out.push_str(&format!("define Tag [ {} ] ;\n", tags.join(" | ")));
    // Text is what neither tape reads as structure. Context symbols count, or a
    // record body could hold one and a state block could swallow the prefix.
    let ctx_any: Vec<String> = context::SLOTS.iter().map(|s| ctx_name(s)).collect();
    out.push_str(&format!(
        "define Special [ Tag | {} ] ;\n",
        ctx_any.join(" | ")
    ));
    out.push_str("define Text \\Special ;\n");
    // A keysym is open-ended text; the modifiers held with it are five closed
    // values, so they are tags inside the item and in canonical order.
    let mods: Vec<String> = MOD_TAGS.iter().map(|t| format!("(\"{t}\")")).collect();
    out.push_str(&format!("define Mods [ {} ] ;\n", mods.join(" ")));
    out.push_str("define KeyItem [ \"[KEY]\" Text+ Mods \"[END]\" ] ;\n");
    // A command and a meta name one operation from a closed table, so each name is
    // a symbol and the artifact's alphabet declares what a host must implement.
    let named = |tags: Vec<String>| -> String {
        let kept: Vec<String> = tags
            .into_iter()
            .filter(|tag| used.is_none_or(|u| u.iter().any(|s| s == tag)))
            .map(|tag| format!("\"{tag}\""))
            .collect();
        // An empty set still has to be a language, and `[ 0 - 0 ]` is the empty one.
        match kept.is_empty() {
            true => "[ 0 - 0 ]".to_string(),
            false => kept.join(" | "),
        }
    };
    out.push_str(&format!(
        "define CmdName [ {} ] ;\ndefine MetaName [ {} ] ;\n",
        named(host::COMMANDS.iter().map(|c| host::command_tag(c)).collect()),
        named(host::METAS.iter().map(|m| host::meta_tag(m)).collect())
    ));
    out.push_str("define CmdItem [ CmdName Text* \"[END]\" ] ;\n");
    out.push_str("define MetaItem [ MetaName Text* \"[END]\" ] ;\n");
    let copies: Vec<String> = (1..=MAX_COUNT)
        .map(|n| format!("\"{}\"", copy_tag(n)))
        .collect();
    out.push_str(&format!("define CopyItem [ {} ] ;\n", copies.join(" | ")));
    // A control character cannot ride a tape, so it names its code point instead.
    // Two hex digits exactly, which is what makes the spelling unique: the
    // characters that need escaping stop at U+009F.
    out.push_str("define Hex [ \"0\"|1|2|3|4|5|6|7|8|9|A|B|C|D|E|F ] ;\n");
    out.push_str(&format!(
        "define EscItem [ \"{}\" Hex Hex \"[END]\" ] ;\n",
        stfst_core::record::ESCAPE
    ));
    out.push_str(
        "define Item [ Text | AnyClass | Drop | KeyItem | CmdItem | MetaItem | CopyItem\n\
         \x20            | EscItem ] ;\n\n",
    );

    let repeats: Vec<String> = (2..=MAX_COUNT)
        .map(|n| format!("\"{}\"", repeat_tag(n)))
        .collect();
    out.push_str(&format!("define Rep [ {} ] ;\n", repeats.join(" | ")));
    let replaces: Vec<String> = (1..=MAX_COUNT)
        .map(|n| format!("\"{}\"", replace_tag(n)))
        .collect();
    out.push_str(&format!("define Replace [ {} ] ;\n", replaces.join(" | ")));

    // Built from the same groups the parser reads, so a new flag cannot be legal
    // for one and illegal for the other.
    let slots: Vec<String> = stfst_core::record::FLAG_GROUPS
        .iter()
        .map(|group| {
            let values: Vec<String> = group.iter().map(|t| format!("\"{t}\"")).collect();
            format!("({})", values.join(" | "))
        })
        .collect();
    out.push_str(&format!(
        "define Plain [ Item* {} (Rep) (Replace) ] ;\n",
        slots.join(" ")
    ));
    out.push_str(&format!(
        "! A record's deferred tail waits for the unit after this one, and carries\n\
         ! its own flags: the join is the point. `Plain` is a record without one,\n\
         ! which is also what a tail is, so nothing here recurses. A hoist moves a\n\
         ! flag to the end of the record, so a grammar that writes a tail places\n\
         ! its own flags by hand and the acceptor check proves it did.\n\
         define Record [ Plain ( \"{AFTER}\" Plain \"[END]\" ) ] ;\n\n"
    ));

    out.push_str(
        "! ---- state: a string the dictionary wrote and the host handed back\n\
         ! Opaque to the host, so it delimits rather than enumerating, and it sits\n\
         ! last on each tape: the whole stroke decides it. `Fresh` is the same\n\
         ! relation with no state carried in and none written out, which is the\n\
         ! question a reverse lookup asks.\n",
    );
    out.push_str(&format!(
        "define StateBlock [ \"{}\" Text* \"{}\" ] ;\n",
        context::STATE_OPEN, context::STATE_CLOSE
    ));
    out.push_str("define Prefix [ (StateBlock) Ctx ] ;\n");
    out.push_str("define Outline [ Prefix Keys (Win) ] ;\n");
    // A fold names keys, so they are the stroke acceptor's symbols on this tape.
    // See `stfst_core::fold`.
    let fold = match used.is_none_or(|u| u.iter().any(|s| s == FOLD)) {
        true => format!(
            "define FoldAlt [ \"{FOLD}\" Plain \"{STRIP}\" Stroke (\"{ADD}\" Stroke) \"[END]\" Plain ] ;\n\
             define Fold [ FoldAlt+ ] ;\n"
        ),
        false => "define Fold [ 0 - 0 ] ;\n".to_string(),
    };
    out.push_str(&fold);
    out.push_str("define Emission [ Record (StateBlock) | Fold ] ;\n");
    out.push_str(
        "define Fresh(G) [ [ [ 0 .x. Ctx ] Keys ] .o. G .o. [ Record ([ StateBlock .x. 0 ]) ] ] ;\n\n",
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
    // No hoist for Replace. Every hoist is a composition every grammar pays for,
    // and a dictionary that carries state emits it after the whole record
    // already; the acceptor check is what proves that, so a hoist would only
    // hide a misplacement.
    // Applied to the grammar one at a time, and never composed with each other.
    // Each hoist is three states with an arc per symbol, so composing nineteen of
    // them together is a product: it came to 786,432 states and 86 seconds, and it
    // doubled every time the flag table grew. Against the grammar, left to right,
    // each step stays the size of the grammar.
    //
    // Each hoist appends after what the previous one appended, so applying them in
    // canonical order leaves the flags in it.
    out.push_str(&format!(
        "define Canon(G) [ G .o. {} ] ;\n",
        hoists.join(" .o. ")
    ));

    out
}

/// The acceptor for one character class. `Lo` rather than `Letter`, so a rule
/// reads at a glance and the identifiers stay short where they are used most.
fn class_name(class: Class) -> &'static str {
    match class {
        Class::Letter => "Lo",
        Class::Upper => "Up",
        Class::Caseless => "Cs",
        Class::Digit => "Dg",
        Class::Other => "Ot",
        Class::Space => "Sp",
    }
}

/// The acceptor for one context slot's values.
fn ctx_name(slot: &Slot) -> String {
    format!("Ctx{}", slot.name)
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
