//! Shared vocabulary for the stfst toolchain: what a steno system is, how a
//! stroke sequence becomes an upper tape, and what a lower-tape record is.
//!
//! No I/O policy and no FST toolchain dependency, so a host can link this and
//! the runtime without pulling in the compiler.

pub mod record;
pub mod system;

pub use record::{Case, Flags, Item, Record, RecordError, SpaceOp};
pub use system::{Key, Outline, OutlineError, Side, Stroke, System, SystemError};
