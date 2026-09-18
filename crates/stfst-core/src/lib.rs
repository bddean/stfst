//! Shared vocabulary for the stfst toolchain: what a steno system is, how a
//! stroke sequence becomes an upper tape, and what a lower-tape record is.
//!
//! No I/O policy and no FST toolchain dependency, so a host can link this and
//! the runtime without pulling in the compiler.

pub mod context;
pub mod golden;
pub mod host;
pub mod record;
pub mod system;
pub mod translate;
pub mod window;

pub use context::{Context, Prefix, State};
pub use host::Capabilities;
pub use record::{Case, Emission, Flags, Item, Record, RecordError, SpaceOp};
pub use window::{Class, Glyph, Shown, Window};
pub use system::{Key, Outline, OutlineError, Side, Stroke, System, SystemError};
