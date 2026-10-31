//! Chord assignment for steno theories: given words as atoms and a design that
//! fixes some atoms' chords and confines the rest to zones, find the chords
//! that lose the least frequency to collisions at the least effort.

pub mod design;
pub mod engine;
pub mod report;
pub mod resolve;
pub mod search;
pub mod words;
