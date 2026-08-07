//! What the keyboard sends, through the runtime it sends it to.
//!
//! The Android side has no stroke type. It has a bit per key in steno order,
//! built by a thumb sliding through a ring, and it reads back a whole buffer.
//! This is that contract over the real dictionary stack: bits in, text out.
//!
//! Skipped when `build/dicts` is absent, because the artifacts are built rather
//! than committed and `cargo test` runs before `stfst compile`.

use std::path::PathBuf;

use stfst_board::Board;
use stfst_core::{Capabilities, Stroke, System};
use stfst_dict::Stack;

const ROOT: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../..");

fn dicts() -> Option<Vec<PathBuf>> {
    let dir = PathBuf::from(ROOT).join("build/dicts");
    let mut found: Vec<PathBuf> = std::fs::read_dir(dir)
        .ok()?
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "stfst"))
        .collect();
    found.sort();
    (!found.is_empty()).then_some(found)
}

fn system() -> System {
    System::load(&PathBuf::from(ROOT).join("systems/stenotype-extended.toml"))
        .expect("the system file is committed")
}

/// The keyboard's stroke: one bit per key index, exactly as `Native.push` reads it.
fn bits(system: &System, keys: &str) -> u64 {
    keys.chars()
        .map(|c| system.index_of_symbol(c).expect("a key of this system"))
        .fold(0u64, |bits, index| bits | (1 << index))
}

/// The other side of that, which is what the JNI layer does with the number.
fn stroke(bits: u64) -> Stroke {
    (0..u64::BITS as usize)
        .filter(|i| bits & (1 << i) != 0)
        .fold(Stroke::EMPTY, |s, i| s.with(i))
}

#[test]
fn a_bitmask_from_two_thumbs_writes_the_word() {
    let Some(dicts) = dicts() else {
        eprintln!("skipped: no build/dicts");
        return;
    };
    let system = system();
    let stack = Stack::open(&dicts, &system, &Capabilities::all()).expect("opening the stack");
    let mut board = Board::new(&system, &stack, None);

    // The left thumb took K A O and the right took E p t, which is `KAOEPT`.
    let left = bits(&system, "KAO");
    let right = bits(&system, "Ept");
    board.push(stroke(left | right)).expect("a stroke");
    assert_eq!("keep the", board.text());

    // The right thumb stayed down, so the next stroke keeps its keys. `-T` alone
    // is "the", and the fold is what makes the pair read as one phrase.
    board.push(stroke(bits(&system, "t"))).expect("a stroke");
    assert_eq!("keep the the", board.text());

    board.undo();
    assert_eq!("keep the", board.text());
    board.undo();
    assert_eq!("", board.text());
}

/// A key order the phone reads out of the system file, so the ring is built from
/// the same string the tapes are.
#[test]
fn the_key_order_is_one_character_per_key() {
    let system = system();
    let order: String = system.keys().iter().map(|k| k.symbol).collect();
    assert_eq!("#@^+STKPWHRAO*EUfrpblgtsdz", order);
    assert_eq!(order.chars().count(), system.key_count());
    // The banks split at the star, and each thumb gets the same count. That is
    // what makes sixteen slots enough for either.
    let split = order.find('*').expect("a star key");
    assert_eq!(13, split);
    assert_eq!(13, order.len() - split);
}
