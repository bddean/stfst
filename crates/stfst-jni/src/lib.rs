//! The Android keyboard's view of the runtime.
//!
//! Nothing here decides anything. It opens a dictionary stack, hands strokes to
//! `stfst-board`, and returns the text the board wrote, so the Kotlin side owns
//! the gestures and this owns none of them.
//!
//! Text comes back whole rather than as an edit. The board already knows how to
//! retract what a longer match supersedes, and a whole buffer is one string; an
//! edit protocol across the boundary would be a second implementation of
//! something `Text` does correctly.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use jni::objects::{JClass, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;
use stfst_board::Board;
use stfst_core::{Capabilities, Stroke, System};
use stfst_dict::Stack;
use stfst_text::morph::Morph;

/// The stack and the system outlive every stroke and are never freed, so the
/// board can borrow them for `'static`. A keyboard opens one set for the life of
/// the process; freeing them would mean a self-referential handle for no gain.
struct Host {
    system: &'static System,
    stack: &'static Stack,
    morph: Option<Arc<Morph>>,
    board: Board<'static>,
    order: String,
}

impl Host {
    /// A fresh buffer and a fresh stroke history, for a keyboard that has just
    /// been given a different text field to write into.
    fn reset(&mut self) {
        self.board = Board::new(self.system, self.stack, self.morph.clone());
    }
}

/// The last failure, so `open` can return zero and still say why.
static LAST_ERROR: std::sync::Mutex<String> = std::sync::Mutex::new(String::new());

fn fail(message: String) {
    if let Ok(mut slot) = LAST_ERROR.lock() {
        *slot = message;
    }
}

fn read(env: &mut JNIEnv, s: &JString) -> Option<String> {
    env.get_string(s).ok().map(Into::into)
}

fn give(env: &mut JNIEnv, text: &str) -> jstring {
    match env.new_string(text) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Every `*.stfst` directory under `dir`, in name order.
///
/// Priority comes from each manifest, not from this order, so a keyboard needs no
/// recipe file to load a stack the compiler wrote.
fn artifacts(dir: &Path) -> Vec<PathBuf> {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return Vec::new();
    };
    let mut found: Vec<PathBuf> = entries
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "stfst"))
        .collect();
    found.sort();
    found
}

fn open_host(dicts: &str, system: &str, morph: &str) -> Result<Host, String> {
    let system = System::load(Path::new(system)).map_err(|e| format!("system: {e}"))?;
    let system: &'static System = Box::leak(Box::new(system));
    let found = artifacts(Path::new(dicts));
    if found.is_empty() {
        return Err(format!("no .stfst directories under {dicts}"));
    }
    let stack = Stack::open(&found, system, &Capabilities::all()).map_err(|e| format!("{e}"))?;
    let stack: &'static Stack = Box::leak(Box::new(stack));
    // Absent artifacts are not an error: without them a suffix join is a plain
    // join, which is the honest answer rather than a refusal to start.
    let morph = Morph::open(Path::new(morph)).ok().map(Arc::new);
    let order = system.keys().iter().map(|k| k.symbol).collect();
    Ok(Host {
        system,
        stack,
        board: Board::new(system, stack, morph.clone()),
        morph,
        order,
    })
}

/// # Safety
///
/// `handle` must be one `open` returned and `close` has not been given.
unsafe fn host(handle: jlong) -> Option<&'static mut Host> {
    (handle != 0).then(|| &mut *(handle as *mut Host))
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_open(
    mut env: JNIEnv,
    _class: JClass,
    dicts: JString,
    system: JString,
    morph: JString,
) -> jlong {
    let (Some(dicts), Some(system), Some(morph)) = (
        read(&mut env, &dicts),
        read(&mut env, &system),
        read(&mut env, &morph),
    ) else {
        fail("a path was not readable as a string".into());
        return 0;
    };
    match open_host(&dicts, &system, &morph) {
        Ok(host) => Box::into_raw(Box::new(host)) as jlong,
        Err(message) => {
            fail(message);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_lastError(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let message = LAST_ERROR.lock().map(|s| s.clone()).unwrap_or_default();
    give(&mut env, &message)
}

/// Read one stroke, given as a bit per key in system order, and return the whole
/// text after it.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_push(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    keys: jlong,
) -> jstring {
    let Some(host) = (unsafe { host(handle) }) else {
        return give(&mut env, "");
    };
    let mut stroke = Stroke::EMPTY;
    let bits = keys as u64;
    for index in 0..u64::BITS as usize {
        if bits & (1 << index) != 0 {
            stroke = stroke.with(index);
        }
    }
    if stroke.is_empty() {
        return give(&mut env, &host.board.text());
    }
    match host.board.push(stroke) {
        Ok(_) => give(&mut env, &host.board.text()),
        Err(e) => {
            fail(format!("{e}"));
            give(&mut env, &host.board.text())
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_undo(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(host) = (unsafe { host(handle) }) else {
        return give(&mut env, "");
    };
    host.board.undo();
    give(&mut env, &host.board.text())
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_text(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    match unsafe { host(handle) } {
        Some(host) => give(&mut env, &host.board.text()),
        None => give(&mut env, ""),
    }
}

/// The key symbols in steno order, one character each. The ring is built from
/// this, so the layout follows the system file rather than a second copy of it.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_order(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    match unsafe { host(handle) } {
        Some(host) => give(&mut env, &host.order),
        None => give(&mut env, ""),
    }
}

/// Forget every stroke and start the text again.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_reset(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(host) = unsafe { host(handle) } {
        host.reset();
    }
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_close(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Host) });
    }
}
