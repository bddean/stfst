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
    /// The board the keyboard was writing with, while it writes somewhere else.
    /// Steno is how a translation is typed, so defining one needs a second buffer
    /// and not a second stack.
    saved: Option<Board<'static>>,
    order: String,
}

impl Host {
    /// A fresh buffer and a fresh stroke history, for a keyboard that has just
    /// been given a different text field to write into.
    fn reset(&mut self) {
        self.board = Board::new(self.system, self.stack, self.morph.clone());
    }

    fn scratch(&mut self, on: bool) {
        match (on, self.saved.take()) {
            (true, None) => {
                let fresh = Board::new(self.system, self.stack, self.morph.clone());
                self.saved = Some(std::mem::replace(&mut self.board, fresh));
            }
            (true, Some(saved)) => self.saved = Some(saved),
            (false, Some(saved)) => self.board = saved,
            (false, None) => {}
        }
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

fn open_host(dicts: &str, editable: &str, system: &str, morph: &str) -> Result<Host, String> {
    let system = System::load(Path::new(system)).map_err(|e| format!("system: {e}"))?;
    let system: &'static System = Box::leak(Box::new(system));
    let found = artifacts(Path::new(dicts));
    if found.is_empty() {
        return Err(format!("no .stfst directories under {dicts}"));
    }
    let editable = stfst_dict::listed(Path::new(editable)).map_err(|e| format!("{e}"))?;
    let stack = Stack::open_with(&found, &editable, system, &Capabilities::all())
        .map_err(|e| format!("{e}"))?;
    let stack: &'static Stack = Box::leak(Box::new(stack));
    // Absent artifacts are not an error: without them a suffix join is a plain
    // join, which is the honest answer rather than a refusal to start.
    let morph = Morph::open(Path::new(morph)).ok().map(Arc::new);
    let order = system.keys().iter().map(|k| k.symbol).collect();
    Ok(Host {
        system,
        stack,
        board: Board::new(system, stack, morph.clone()),
        saved: None,
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
    editable: JString,
    system: JString,
    morph: JString,
) -> jlong {
    let (Some(dicts), Some(editable), Some(system), Some(morph)) = (
        read(&mut env, &dicts),
        read(&mut env, &editable),
        read(&mut env, &system),
        read(&mut env, &morph),
    ) else {
        fail("a path was not readable as a string".into());
        return 0;
    };
    match open_host(&dicts, &editable, &system, &morph) {
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

/// Briefer outlines for what was just written, one `text=OUTLINE` entry per
/// line, trailing single words and phrases both. Empty when nothing is briefer.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_hints(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(host) = (unsafe { host(handle) }) else {
        return give(&mut env, "");
    };
    let lines: Vec<String> = host
        .board
        .hints(4)
        .iter()
        .map(|h| format!("{}={}", h.text, h.outline))
        .collect();
    give(&mut env, &lines.join("\n"))
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

/// The outline of the last `strokes` strokes, as the system spells it.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_tail(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    strokes: jlong,
) -> jstring {
    let Some(host) = (unsafe { host(handle) }) else {
        return give(&mut env, "");
    };
    let outline = host.board.tail(strokes.max(0) as usize);
    let text = host.system.format_outline(&outline);
    give(&mut env, &text)
}

/// How many strokes the last match consumed: what "the thing I just wrote" is,
/// before anyone asks for more or less of it.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_unit(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    match unsafe { host(handle) } {
        Some(host) => host.board.unit() as jlong,
        None => 0,
    }
}

/// A command the dictionary asked the keyboard for, as `name\targument`, taken
/// once. Empty when there is none.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_command(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(host) = (unsafe { host(handle) }) else {
        return give(&mut env, "");
    };
    match host.board.take_command() {
        Some((name, arg)) => give(&mut env, &format!("{name}\t{arg}")),
        None => give(&mut env, ""),
    }
}

/// Write into a second buffer, and back again.
///
/// A translation is typed in steno like anything else, and the board is the only
/// thing that knows what a stroke writes. So defining one swaps the buffer rather
/// than reading strokes some other way, and what the keyboard had written is
/// waiting when it swaps back.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_scratch(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    on: jlong,
) -> jstring {
    let Some(host) = (unsafe { host(handle) }) else {
        return give(&mut env, "");
    };
    host.scratch(on != 0);
    give(&mut env, &host.board.text())
}

/// What an editable dictionary says the outline writes, or empty.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_defined(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    dict: JString,
    outline: JString,
) -> jstring {
    let (Some(host), Some(dict), Some(outline)) = (
        unsafe { host(handle) },
        read(&mut env, &dict),
        read(&mut env, &outline),
    ) else {
        return give(&mut env, "");
    };
    let found = host
        .stack
        .editable(&dict)
        .and_then(|e| e.translation(&outline))
        .unwrap_or_default();
    give(&mut env, &found)
}

/// Define an outline, and make the last `back` strokes again so the text they
/// already wrote is the text they now mean. An empty translation deletes the
/// entry, and `back` of zero defines one without touching what is written.
///
/// Returns the reason it did not happen, or empty.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_define(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    dict: JString,
    outline: JString,
    translation: JString,
    back: jlong,
) -> jstring {
    let (Some(host), Some(dict), Some(outline), Some(translation)) = (
        unsafe { host(handle) },
        read(&mut env, &dict),
        read(&mut env, &outline),
        read(&mut env, &translation),
    ) else {
        return give(&mut env, "a string was not readable");
    };
    let Some(editable) = host.stack.editable(&dict) else {
        return give(&mut env, &format!("no editable dictionary called {dict}"));
    };
    if let Err(e) = editable.set(&outline, &translation) {
        return give(&mut env, &format!("{e}"));
    }
    if let Err(e) = host.board.restroke(back.max(0) as usize) {
        return give(&mut env, &format!("{e}"));
    }
    give(&mut env, "")
}

/// Start the buffer again from text already written, without stroking it. What
/// fills a field with an entry's current translation, so it can be edited.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_Native_seed(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    text: JString,
) -> jstring {
    let (Some(host), Some(text)) = (unsafe { host(handle) }, read(&mut env, &text)) else {
        return give(&mut env, "");
    };
    host.board.seed(&text);
    give(&mut env, &host.board.text())
}
