//! Owned encoder proof only. Never loads SDK or executes the owned callee/callback.
use iced_x86::{Decoder, DecoderOptions, Instruction};
use ilhook::x64::{CallbackOption, HookFlags, HookType, Hooker, Registers};
pub use ilhook::HookError;
#[path = "../local/cargo/registry/src/index.crates.io-1949cf8c6b5b557f/ilhook-2.3.0/src/x64/move_inst.rs"]
mod move_inst;
use std::{ffi::c_void, slice};

const ORIGINAL: [u8; 18] = [
    0x40, 0x53, 0x56, 0x57, 0x48, 0x81, 0xec, 0x90, 0, 0, 0,
    0x48, 0x8b, 0x05, 0x9e, 0xd1, 0x3c, 0,
];
const USERDATA: usize = 0x1234_5678_9abc_def0;

#[link(name = "kernel32")]
extern "system" {
    fn VirtualAlloc(address: *mut c_void, bytes: usize, kind: u32, protection: u32) -> *mut c_void;
    fn VirtualFree(address: *mut c_void, bytes: usize, kind: u32) -> i32;
}

struct OwnedPage(*mut c_void);
impl Drop for OwnedPage {
    fn drop(&mut self) { unsafe { VirtualFree(self.0, 0, 0x8000); } }
}

// A code address for metadata/ABI encoding only. This function is never invoked.
unsafe extern "win64" fn dummy_callback(_: *mut Registers, _: usize) {}

fn hex(raw: &[u8]) -> String {
    let mut out = String::with_capacity(raw.len() * 2);
    for byte in raw { use std::fmt::Write; write!(&mut out, "{byte:02x}").unwrap(); }
    out
}

fn instructions(address: u64) -> Result<Vec<Instruction>, &'static str> {
    let mut decoder = Decoder::with_ip(64, &ORIGINAL, address, DecoderOptions::NONE);
    let mut result = Vec::new();
    let mut bytes = 0;
    while bytes < 14 {
        let inst = decoder.decode();
        if inst.is_invalid() { return Err("OWNED_DECODE_FAILED"); }
        bytes += inst.len();
        result.push(inst);
    }
    if bytes != 18 { return Err("OWNED_DECODE_LENGTH_CHANGED"); }
    Ok(result)
}

fn moved_case(source: u64, destination: u64, label: &str) -> Result<String, &'static str> {
    let instructions = instructions(source)?;
    let moved = move_inst::move_code_to_addr(&instructions, destination)
        .map_err(|_| "ORIGINAL_MOVE_CODE_FAILED")?;
    Ok(format!("{{\"case\":\"{label}\",\"source\":{source},\"destination\":{destination},\"bytes\":{},\"codeHex\":\"{}\"}}",
        moved.len(), hex(&moved)))
}

unsafe fn owned_hook() -> Result<String, &'static str> {
    let page = OwnedPage(VirtualAlloc(std::ptr::null_mut(), 4096, 0x3000, 0x40));
    if page.0.is_null() { return Err("OWNED_ALLOCATION_FAILED"); }
    let memory = slice::from_raw_parts_mut(page.0.cast::<u8>(), 4096);
    memory.fill(0x90);
    memory[..18].copy_from_slice(&ORIGINAL);
    let original = memory[..253].to_vec();
    let entry = page.0 as usize;
    let callback = dummy_callback as *const () as usize;
    let hook = Hooker::new(entry, HookType::JmpBack(dummy_callback), CallbackOption::None,
        USERDATA, HookFlags::empty()).hook().map_err(|_| "OWNED_HOOK_INSTALL_FAILED")?;
    let patched = slice::from_raw_parts(page.0.cast::<u8>(), 253).to_vec();
    let (target, kind) = if patched[0] == 0xe9 {
        let delta = i32::from_le_bytes(patched[1..5].try_into().unwrap());
        ((entry as i64 + 5 + i64::from(delta)) as usize, "near")
    } else if patched[..6] == [0xff, 0x25, 0, 0, 0, 0] {
        (u64::from_le_bytes(patched[6..14].try_into().unwrap()) as usize, "far")
    } else {
        return Err("OWNED_PATCH_ENCODING_CHANGED");
    };
    // The destination was just written by our own HookPoint. Read only its known
    // generated code length; never call it, dereference cookie or execute page.
    let moved = move_inst::move_code_to_addr(&instructions(entry as u64)?, target as u64 + 250)
        .map_err(|_| "OWNED_MOVE_CODE_FAILED")?;
    let width = 250 + moved.len() + 14;
    if width > 512 { return Err("OWNED_GENERATED_CODE_BUDGET"); }
    let trampoline = slice::from_raw_parts(target as *const u8, width).to_vec();
    drop(hook);
    let restored = slice::from_raw_parts(page.0.cast::<u8>(), 253) == original;
    if !restored { return Err("OWNED_HOOK_DROP_DID_NOT_RESTORE"); }
    Ok(format!(concat!("{{\"entry\":{},\"callback\":{},\"userdata\":{},\"trampoline\":{},",
        "\"patchKind\":\"{}\",\"originalHex\":\"{}\",\"patchedHex\":\"{}\",",
        "\"trampolineBytes\":{},\"trampolineHex\":\"{}\",\"dropRestored\":true}}"),
        entry, callback, USERDATA, target, kind, hex(&original), hex(&patched), width, hex(&trampoline)))
}

fn run() -> Result<String, &'static str> {
    let source = 0x18039_50c0;
    let near = moved_case(source, source + 0x100000, "near")?;
    let far = moved_case(source, source + 0x1_0000_0000, "far")?;
    let hook = unsafe { owned_hook()? };
    Ok(format!(concat!("{{\"schema\":1,\"scope\":\"owned generated code only\",",
        "\"original18Hex\":\"{}\",\"movedCases\":[{},{}],\"ownedHook\":{},",
        "\"sdkLoaded\":false,\"calleeExecuted\":false,\"callbackExecuted\":false,\"cookieDereferenced\":false}}"),
        hex(&ORIGINAL), near, far, hook))
}

fn main() {
    match run() {
        Ok(report) => println!("{report}"),
        Err(stage) => { eprintln!("KEY_TRAMPOLINE_FIXTURE_FAILED:{stage}"); std::process::exit(2); }
    }
}
