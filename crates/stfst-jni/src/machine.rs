//! Steno machines for `NativeMachine`: the Kotlin side owns the USB transport
//! and the clock, and hands every read here.

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jint, jintArray, jlong, jlongArray, jshortArray, jstring};
use jni::JNIEnv;
use stfst_machine::{Cue, Error, Machine, Output, PloverConfig, Protocol};

struct Handle {
    machine: Machine,
    warnings: String,
    error: String,
}

/// # Safety
///
/// `handle` must be one `open` returned and `close` has not been given.
unsafe fn handle(handle: jlong) -> Option<&'static mut Handle> {
    (handle != 0).then(|| &mut *(handle as *mut Handle))
}

fn text(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(Into::into).unwrap_or_default()
}

fn give(env: &mut JNIEnv, text: &str) -> jstring {
    env.new_string(text).map_or(std::ptr::null_mut(), |s| s.into_raw())
}

fn bytes(env: &mut JNIEnv, array: &JByteArray, length: jint) -> Vec<u8> {
    let mut all = env.convert_byte_array(array).unwrap_or_default();
    all.truncate(length.max(0) as usize);
    all
}

/// The cue's ordinal, or -1, then the strokes; or null with the reason in `error`.
fn output(env: &mut JNIEnv, h: &mut Handle, result: Result<Output, Error>) -> jlongArray {
    let out = match result {
        Ok(out) => out,
        Err(e) => {
            h.error = e.to_string();
            return std::ptr::null_mut();
        }
    };
    let cue = match out.cue {
        None => -1,
        Some(Cue::Arm) => 0,
        Some(Cue::Cancel) => 1,
        Some(Cue::Disarm) => 2,
        Some(Cue::Send) => 3,
    };
    let values: Vec<jlong> = std::iter::once(cue).chain(out.strokes.iter().map(|&s| s as jlong)).collect();
    let Ok(array) = env.new_long_array(values.len() as i32) else { return std::ptr::null_mut() };
    if env.set_long_array_region(&array, 0, &values).is_err() {
        return std::ptr::null_mut();
    }
    array.into_raw()
}

/// A machine for `protocol` (0 Plover HID, 1 Gemini PR, 2 Stentura), configured
/// by the text of `plover.cfg`, for a system whose keys are `names`.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_open(
    mut env: JNIEnv,
    _class: JClass,
    protocol: jint,
    cfg: JString,
    names: JString,
) -> jlong {
    let protocol = match protocol {
        0 => Protocol::Hid,
        1 => Protocol::Gemini,
        2 => Protocol::Stentura,
        _ => return 0,
    };
    let cfg = PloverConfig::parse(&text(&mut env, &cfg));
    let names = text(&mut env, &names);
    let names: Vec<&str> = names.split(' ').collect();
    let (machine, warnings) = Machine::new(protocol, &cfg, &names);
    let h = Handle { machine, warnings: warnings.join("\n"), error: String::new() };
    Box::into_raw(Box::new(h)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_close(_env: JNIEnv, _class: JClass, h: jlong) {
    if h != 0 {
        drop(unsafe { Box::from_raw(h as *mut Handle) });
    }
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_feed(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
    now: jlong,
    data: JByteArray,
    length: jint,
) -> jlongArray {
    let Some(h) = (unsafe { handle(h) }) else { return std::ptr::null_mut() };
    let data = bytes(&mut env, &data, length);
    let result = h.machine.feed(now as u64, &data);
    output(&mut env, h, result)
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_idle(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
    now: jlong,
) -> jlongArray {
    let Some(h) = (unsafe { handle(h) }) else { return std::ptr::null_mut() };
    let result = h.machine.idle(now as u64);
    output(&mut env, h, result)
}

/// Milliseconds until `idle` has something to do, or -1 for none.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_wake(
    _env: JNIEnv,
    _class: JClass,
    h: jlong,
    now: jlong,
) -> jlong {
    unsafe { handle(h) }.and_then(|h| h.machine.wake(now as u64)).map_or(-1, |ms| ms as jlong)
}

/// Bytes to write to the machine, or null for none.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_outgoing(
    env: JNIEnv,
    _class: JClass,
    h: jlong,
    now: jlong,
) -> jbyteArray {
    let Some(out) = unsafe { handle(h) }.and_then(|h| h.machine.outgoing(now as u64)) else {
        return std::ptr::null_mut();
    };
    env.byte_array_from_slice(&out).map_or(std::ptr::null_mut(), |a| a.into_raw())
}

/// One of the hold's sounds (0 arm, 1 disarm, 2 send) as PCM, or null if the
/// machine does not hold.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_sound(
    env: JNIEnv,
    _class: JClass,
    h: jlong,
    which: jint,
) -> jshortArray {
    let Some(sounds) = unsafe { handle(h) }.and_then(|h| h.machine.sounds()) else {
        return std::ptr::null_mut();
    };
    let pcm = match which {
        0 => sounds.arm,
        1 => sounds.disarm,
        _ => sounds.send,
    };
    let Ok(array) = env.new_short_array(pcm.len() as i32) else { return std::ptr::null_mut() };
    if env.set_short_array_region(&array, 0, &pcm).is_err() {
        return std::ptr::null_mut();
    }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_warnings(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
) -> jstring {
    let warnings = unsafe { handle(h) }.map(|h| h.warnings.clone()).unwrap_or_default();
    give(&mut env, &warnings)
}

#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_error(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
) -> jstring {
    let error = unsafe { handle(h) }.map(|h| h.error.clone()).unwrap_or_default();
    give(&mut env, &error)
}

/// Whether a HID report descriptor is a Plover HID machine's.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_declares(
    env: JNIEnv,
    _class: JClass,
    descriptor: JByteArray,
) -> jboolean {
    let descriptor = env.convert_byte_array(&descriptor).unwrap_or_default();
    stfst_machine::hid::declares(&descriptor) as jboolean
}

/// The serial machine and its port as `plover.cfg` has them: the protocol (1
/// Gemini PR, 2 Stentura, or `protocol` itself if it is one of those), baud
/// rate, data bits, parity as a character code, and stop bits times two.
#[no_mangle]
pub extern "system" fn Java_cx_yeet_stfst_NativeMachine_serial(
    mut env: JNIEnv,
    _class: JClass,
    cfg: JString,
    protocol: jint,
) -> jintArray {
    let cfg = PloverConfig::parse(&text(&mut env, &cfg));
    let protocol = match protocol {
        1 => Protocol::Gemini,
        2 => Protocol::Stentura,
        _ => cfg.serial_protocol(),
    };
    let s = cfg.serial(protocol);
    let code = if protocol == Protocol::Stentura { 2 } else { 1 };
    let values = [code, s.baudrate as jint, s.bytesize as jint, s.parity as jint, (s.stopbits * 2.0) as jint];
    let Ok(array) = env.new_int_array(values.len() as i32) else { return std::ptr::null_mut() };
    if env.set_int_array_region(&array, 0, &values).is_err() {
        return std::ptr::null_mut();
    }
    array.into_raw()
}
