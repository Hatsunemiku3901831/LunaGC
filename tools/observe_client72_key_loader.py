"""Fixed key-loader state/code observer. Offline collector is pure; live CLI is parent-only."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import struct

try:
    from . import observe_client72_clean_loader as clean
    from . import verify_sdk72_key_proxy as key
except ImportError:
    import observe_client72_clean_loader as clean
    import verify_sdk72_key_proxy as key

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'local/migration72/d02-resume-20261108/key-loader/observation'
PRODUCTION = ROOT/'local/migration72/d02-resume-20261108/key-loader/target/release/examples/sdk72_key_proxy.dll'
PRODUCTION_SHA = 'ad18e741052a05850ff7b80b8043af0551981793dc7e3cca89be26e565a2139d'
ILHOOK = 'local/cargo/registry/src/index.crates.io-1949cf8c6b5b557f/ilhook-2.3.0/src/'
ICED = 'local/cargo/registry/src/index.crates.io-1949cf8c6b5b557f/iced-x86-1.21.0/src/'
PINS = {
    'tools/observe_client72_clean_loader.py': '6ece909b020efd46a15124f20198c02b41c43f775637c50203a29ea2fb363dd7',
    'tools/observe_client72_uninstrumented.py': 'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e',
    'tools/audit_sdk72_clean_loader.py': '6ae98c400633d6778a4c6f23313267399bbf72d1672c910c725bcd783fe02dee',
    'tools/verify_sdk72_key_proxy.py': '90135f45eb06df1008eb5a4c7f42facd2355b71dafd08ea107904da4f81b222b',
    'patch/examples/sdk72_key_proxy.rs': 'ce325a8b795739725a52abcd5e68833a93ea7b3ee115c21e932ed86d5fe1119d',
    'patch/examples/sdk72_key_proxy/sdk72_key_proxy_loader.rs': 'c31f9b5f61d68669e54e55f5f8765fb89968417def1ed9679deaee1f1c9c76a4',
    'patch/src/modules/sdk72_key_input.rs': '67af556fea165d1597741cee2bbf2d310542c6c5c9637e44ee8877f4cc885bcd',
    'patch/Cargo.lock': '8482fc5d3e86ac5416038e446431e729a5e55c338d8824407f9aab878c323b2f',
    'tools/client72_key_trampoline_fixture.rs': 'fc9cdaf151876ed2d54bb4cc7cd524e0d8f64d270b0c5b7013bce5db48136cc2',
    ILHOOK+'x64.rs': '1b1350b1fc6b0a2cac0726bd3765c1dbc8bd90450f99439adca02ba208a9e8a7',
    ILHOOK+'x64/move_inst.rs': 'f98e6d9f15faeaab12e00e81c912778b4f8e789f53058b5d3547e3abdcd30720',
    ICED+'encoder.rs': '2cd87e739955e16f66c519eb5c85521b6aa71ea097e6eac6011a4b410c8e4167',
    ICED+'encoder/op_code_handler.rs': '6b0b90336b1b776197d748c5e4c240de476b9d05df145efd48cf89cce861208f',
    ICED+'encoder/encoder_data.rs': '297f0a229c10949ea96d740371bf550e76a0e585d2b0f1749decd717555b13ff',
    ICED+'encoder/ops.rs': '14ace939d094c656cbc49b06c5e718bae0b804802cf74558e9740ffbef6cc0ac',
    'local/migration72/d02-resume-20261108/key-loader/prepare-source1/proof.private.json': 'be24bfada3badef0663310053e5525645c5d7ac8d81ac99c3facc9c993c5fc26',
    'local/migration72/d02-resume-20261108/key-loader/verify-owned-final1/proof.private.json': '9d471c90e74ea4006f94a3ef076c639a77d50a8f28622d4e3c233ba3838fea1b',
}
STATE_FIELDS = ('schema', 'bytes', 'phase', 'failure', 'win32_error', 'installed', 'sdk_base', 'image_size',
                'path_verified', 'disk_verified', 'windows_verified', 'original_bound', 'self_held', 'sdk_held', 'reserved')
CALLBACK_PREFIX = bytes.fromhex('4883ec48f30f7f642420f30f7f6c2430e8')
CALLBACK_SUFFIX = bytes.fromhex('f30f6f642420f30f6f6c24304883c448c3')
SDK_PREFIX = bytes.fromhex('405356574881ec90000000488b059ed13c00')
MAX_READS, MAX_BYTES, MAX_SINGLE = 8, 1600, 512


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def source_arrays(text):
    result = []
    for name in ('write_trampoline_prolog', 'write_trampoline_epilog1', 'write_trampoline_epilog2_common'):
        body = text.split('fn '+name+'(', 1)[1].split('\nfn ', 1)[0]
        raw = re.search(r'buf.write\(&\[(.*?)\]\)', body, re.S)[1]
        result.append(bytes(int(value, 16) for value in re.findall(r'0x([0-9a-fA-F]+)', raw)))
    require(tuple(map(len, result)) == (139, 49, 29), 'Frozen ilhook byte-array layout differs')
    return result


def artifact_contract(proxy, sdk, arrays):
    audit = key.require_artifact(proxy, clean.source_names(), allow_core_inner=True)
    pe, sdk_pe = clean.PE(proxy), clean.PE(sdk)
    exports = {row['name']: row for row in pe.exports()}
    inner, state = exports['sdk72_key_input_inner']['rva'], exports['Sdk72KeyProxyState']['rva']
    candidates = []
    pattern = re.compile(re.escape(CALLBACK_PREFIX)+b'.{4}'+re.escape(CALLBACK_SUFFIX), re.S)
    for section in pe.sections:
        if not section[-1] & 0x20000000:
            continue
        raw = pe.take(section[4], section[3])
        for match in pattern.finditer(raw):
            rva = section[2]+match.start()
            if rva+21+struct.unpack_from('<i', match[0], 17)[0] == inner:
                candidates.append((rva, match[0]))
    require(len(candidates) == 1, 'Unique exact callback/inner rel32 in executable artifact is absent')
    require(sdk_pe.bytes_at(0x3950c0, 18) == SDK_PREFIX, 'Exact SDK moved instructions differ')
    for rva, width, expected in key.WINDOWS:
        require(digest(sdk_pe.bytes_at(rva, width)) == expected, 'Fixed SDK code identity differs')
    prolog, epilog1, epilog2 = arrays
    return dict(stateRva=state, tableRva=audit['initialTargetTableRva'], callbackRva=candidates[0][0],
        callbackCode=candidates[0][1], innerRva=inner, prolog=prolog, epilog1=epilog1, epilog2=epilog2,
        sdkWindows=[(rva, sdk_pe.bytes_at(rva, width)) for rva, width, _ in key.WINDOWS],
        sdkImageSize=sdk_pe.image_size, trampolineMovedOffset=250, originalMovedBytes=18)


def audit_sources(client, artifact=PRODUCTION):
    for path, expected in PINS.items():
        require(digest((ROOT/path).read_bytes()) == expected,
                'Frozen observation source/proof changed: '+path)
    baseline = json.loads((ROOT/'local/migration72/d02-resume-20261108/key-loader/verify-owned-final1/proof.private.json').read_bytes())
    for path, expected in baseline['frozenSourcePins'].items():
        require(digest((ROOT/path).read_bytes()) == expected, 'Frozen clean/key source changed')
    for row in baseline['buildContext'].values():
        require(digest((ROOT/row['path']).read_bytes()) == row['sha256'], 'Frozen key build context changed')
    for name, row in baseline['publicBuildInputs'].items():
        require(digest((key.PUBLIC_ROOT/name).read_bytes()) == row['sha256'], 'Fixed public build input changed')
    raw = artifact.read_bytes()
    require(len(raw) == 578048 and digest(raw) == PRODUCTION_SHA, 'Fixed key DLL artifact changed')
    sdk = (client/'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes()
    require(digest(sdk) == clean.EXPECTED['sdk'], 'Fixed SDK disk identity changed')
    arrays = source_arrays((ROOT/(ILHOOK+'x64.rs')).read_text(encoding='utf-8'))
    return raw, sdk, artifact_contract(raw, sdk, arrays)


def verify_installed(client, artifact=PRODUCTION):
    proxy, sdk, contract = audit_sources(client, artifact)
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    checked = {}
    for name in ('binary', 'metadata', 'startup', 'sdk'):
        row = profile['files'][name]
        actual = digest((client/row['path']).read_bytes())
        require(actual == row['sha256'] == clean.EXPECTED[name], 'Fixed profile/client identity differs: '+name)
        checked[name] = dict(path=row['path'], sha256=actual)
    plugins = client/'YuanShen_Data/Plugins'
    stock = (plugins/'Astrolabe_orig.dll').read_bytes()
    require(digest(stock) == profile['files']['astrolabe']['sha256'] == clean.EXPECTED['astrolabe'], 'Exact original stock alias differs')
    require((plugins/'Astrolabe.dll').read_bytes() == proxy, 'Installed DLL differs from frozen key artifact')
    clean.require_exports(clean.PE(stock).exports(), clean.source_names())
    checked.update(proxy=dict(path='YuanShen_Data/Plugins/Astrolabe.dll', sha256=PRODUCTION_SHA),
                   original=dict(path='YuanShen_Data/Plugins/Astrolabe_orig.dll', sha256=digest(stock)))
    return dict(files=checked, profileSHA256=clean.sha256(ROOT/'protocol/cn-7.2/profile.json'),
        sourcePins=PINS, config={name: clean.file_state(ROOT/name) for name in ('.env', '.env.local', 'config.json')}), proxy, stock, sdk, contract


def decode_state(raw):
    return dict(zip(STATE_FIELDS, struct.unpack('<6IQ8I', raw)))


def state_kind(state, sdk_base, image_size):
    if state['schema'] != 1 or state['bytes'] != 64 or state['reserved'] != 0:
        return 'invalid'
    if state['phase'] == 5:
        expected = dict(phase=5, failure=0, win32_error=0, installed=1, sdk_base=sdk_base, image_size=image_size,
            path_verified=1, disk_verified=1, windows_verified=7, original_bound=1, self_held=1, sdk_held=1)
        return 'installed' if all(state[name] == value for name, value in expected.items()) else 'invalid'
    if state['phase'] == 6 and 101 <= state['failure'] <= 113:
        bound = state['failure'] >= 112
        expected = dict(installed=0, original_bound=1, self_held=int(state['failure'] != 101), sdk_held=0,
            sdk_base=sdk_base if bound else 0, image_size=image_size if bound else 0,
            path_verified=int(bound), disk_verified=int(bound), windows_verified=7 if bound else 0)
        return 'failed' if all(state[name] == value for name, value in expected.items()) else 'invalid'
    return 'nonterminal' if state['phase'] in range(5) else 'invalid'


def patch_target(observed, original, address):
    if observed[0] == 0xe9:
        delta = struct.unpack_from('<i', observed, 1)[0]
        require(abs(delta) <= 0x7fffffff and observed[5:] == original[5:], 'Near patch encoding/tail differs')
        return address+5+delta, 'near'
    require(observed[:6] == b'\xff\x25\0\0\0\0' and observed[14:] == original[14:], 'Far patch encoding/tail differs')
    target = struct.unpack_from('<Q', observed, 6)[0]
    require(abs(target-(address+5)) > 0x7fffffff, 'Far patch violates exact ilhook distance choice')
    return target, 'far'


def trampoline(contract, code_address, sdk_base, callback, userdata):
    prolog = contract['prolog']
    prefix = prolog+b'\x48\xba'+struct.pack('<Q', userdata)+bytes.fromhex('4889e14883ec1048b8')
    prefix += struct.pack('<Q', callback)+bytes.fromhex('ffd04883c410')+contract['epilog1']+contract['epilog2']
    require(len(prefix) == 250, 'Exact JmpBack ABI layout differs')
    entry = sdk_base+0x3950c0
    cookie = sdk_base+0x762270
    moved = bytes.fromhex('5356574881ec90000000')  # Encoder removes ignored REX40 from push rbx.
    if abs(entry-(code_address+250)) < 0x7ffff000:
        moved += b'\x48\x8b\x05'+struct.pack('<i', cookie-(code_address+250+17))
        kind = 'near-rip'
    else:
        # move_inst::relocate_ip_rel_memory_inst selects unused RBX for MOV RAX,[RIP].
        moved += bytes.fromhex('48895c24f048bb')+struct.pack('<Q', cookie)+bytes.fromhex('488b03488b5c24f0')
        kind = 'far-rip-rbx'
    return prefix+moved+b'\xff\x25\0\0\0\0'+struct.pack('<Q', entry+18), kind


def verify_owned_emitter(report, contract):
    require(report['schema'] == 1 and report['scope'] == 'owned generated code only'
            and report['original18Hex'] == SDK_PREFIX.hex(), 'Owned emitter schema/prefix differs')
    require(all(report[name] is False for name in ('sdkLoaded', 'calleeExecuted', 'callbackExecuted', 'cookieDereferenced')),
            'Owned emitter crossed its no-execution boundary')
    require(len(report['movedCases']) == 2, 'Owned emitter case count differs')
    rows = []
    source = 0x1803950c0
    for row, label, delta in zip(report['movedCases'], ('near', 'far'), (0x100000, 0x100000000)):
        require(row['case'] == label and row['source'] == source and row['destination'] == source+delta,
                'Owned original mover addresses/case differ')
        code, kind = trampoline(contract, row['destination']-250, source-0x3950c0, 0x400001000, 1)
        expected = code[250:-14]
        require(bytes.fromhex(row['codeHex']) == expected and row['bytes'] == len(expected),
                'Original iced/ilhook mover differs from observer prediction')
        rows.append(dict(case=label, bytes=len(expected), relocationKind=kind, originalEmitterEqual=True))
    hook = report['ownedHook']
    require(all(type(hook[name]) is int and 0 < hook[name] < (1 << 64) for name in ('entry', 'callback', 'userdata', 'trampoline')),
            'Owned hook address fields differ')
    require(hook['userdata'] == 0x123456789abcdef0 and hook['dropRestored'] is True, 'Owned hook userdata/drop witness differs')
    original = SDK_PREFIX+b'\x90'*235
    require(bytes.fromhex(hook['originalHex']) == original, 'Owned callee is not the exact original prefix/NOP buffer')
    target, encoding = patch_target(bytes.fromhex(hook['patchedHex']), original, hook['entry'])
    require(target == hook['trampoline'] and encoding == hook['patchKind'], 'Actual owned patch target differs')
    expected, relocation = trampoline(contract, target, hook['entry']-0x3950c0, hook['callback'], hook['userdata'])
    require(bytes.fromhex(hook['trampolineHex']) == expected and hook['trampolineBytes'] == len(expected),
            'Actual ilhook full trampoline differs from observer prediction')
    return dict(originalMoverCases=rows, actualOwnedFullTrampolineEqual=True, actualPatchEncoding=encoding,
        actualRelocationKind=relocation, actualTrampolineBytes=len(expected), ownedDropRestored=True,
        sdkLoaded=False, calleeExecuted=False, callbackExecuted=False, cookieDereferenced=False)


def collect(read, modules, contract, original_rvas):
    rows, requested = [], 0
    def get(name, address, width):
        nonlocal requested
        require(len(rows) < MAX_READS and requested+width <= MAX_BYTES and 0 < width <= MAX_SINGLE, 'Key observer read budget exceeded')
        requested += width
        try:
            if not 0 < address < (1 << 64) or address+width > (1 << 64):
                data, error = b'', 'address-outside-uint64'
            else:
                data, error = read(address, width)
        except OSError as failure:
            data, error = b'', dict(winerror=getattr(failure, 'winerror', failure.errno))
        complete = error is None and len(data) == width
        rows.append(dict(name=name, address=address, bytes=width, bytesRead=len(data),
                         error=error if error is not None else None if complete else 'short-read'))
        return data if complete else None
    proxy, sdk, original = (modules[name]['base'] for name in ('Astrolabe.dll', 'AccountPlatNative.dll', 'Astrolabe_orig.dll'))
    state_address = proxy+contract['stateRva']
    first = get('state.before', state_address, 64)
    result = dict(status='partial', reads=rows, naturalCallerObserved=False, targetFunctionsCalled=False,
                  callbackCountRead=False, userdataDereferenced=False, atomicSnapshot=False, d02Passed=False)
    if first is None:
        result['reason'] = 'state-unreadable'
    else:
        state = decode_state(first); kind = state_kind(state, sdk, contract['sdkImageSize'])
        result.update(state=state, stateKind=kind)
        if kind in ('installed', 'failed'):
            table = get('proxy.forward-targets', proxy+contract['tableRva'], 376)
            equal = table is not None and table == struct.pack('<47Q', *(original+rva for rva in original_rvas))
            result['forwardTargetsEqual'] = equal
            windows = []
            observed_entry = None
            for rva, expected in contract['sdkWindows']:
                actual = get('sdk.window.'+hex(rva), sdk+rva, len(expected))
                windows.append(dict(rva=hex(rva), bytes=len(expected), originalEqual=actual == expected))
                if rva == 0x3950c0:
                    observed_entry = actual
            result['sdkWindows'] = windows
            callers_equal = all(row['originalEqual'] for row in windows[1:])
            if kind == 'installed' and equal and callers_equal and observed_entry is not None:
                try:
                    target, encoding = patch_target(observed_entry, contract['sdkWindows'][0][1], sdk+0x3950c0)
                    sample, relocation = trampoline(contract, target, sdk, proxy+contract['callbackRva'], 1)
                    actual = get('ilhook.trampoline', target, len(sample))
                    userdata = struct.unpack_from('<Q', actual, 141)[0] if actual is not None else 0
                    expected, _ = trampoline(contract, target, sdk, proxy+contract['callbackRva'], userdata)
                    trampoline_equal = actual == expected and userdata != 0
                    callback = get('key.callback', proxy+contract['callbackRva'], len(contract['callbackCode']))
                    callback_equal = callback == contract['callbackCode']
                    result.update(patchEncoding=encoding, trampolineBytes=len(sample), relocationKind=relocation,
                        fullTrampolineEqual=trampoline_equal, callbackEqual=callback_equal, userdataNonzero=userdata != 0,
                        returnEntryPlus18Verified=trampoline_equal)
                    if trampoline_equal and callback_equal:
                        result['status'] = 'installed-code-bound'
                except ValueError:
                    result['reason'] = 'patch-or-trampoline-contract-mismatch'
            elif kind == 'failed' and equal and all(row['originalEqual'] for row in windows):
                result['status'] = 'failed-state-original-code-bound'
        else:
            result['reason'] = 'state-not-stable-terminal-kind'
        last = get('state.after', state_address, 64)
        result['stableStateEqual'] = last is not None and last == first
        if not result['stableStateEqual'] or any(row['error'] is not None for row in rows):
            result['status'] = 'partial'
    result['budget'] = dict(reads=len(rows), bytes=requested, maxReads=MAX_READS, maxBytes=MAX_BYTES, maxSingle=MAX_SINGLE)
    return result


def inspect_process(backend, client, evidence, proxy, stock, sdk, contract):
    require(evidence['sourceSHA256'] == clean.sha256(Path(__file__)) and evidence['elapsedSeconds'] >= 90,
            'New observer source/90-second monitor identity differs')
    require(Path(evidence['image']).samefile(client/'YuanShen.exe')
            and Path(evidence['launch']['executable']).samefile(client/'YuanShen.exe'), 'Monitor image file identity differs')
    if not evidence['finalStatus']['alive']:
        return dict(status='partial', reason='target-exited-before-inspection')
    handle = backend.k.OpenProcess(0x101010, False, evidence['pid'])
    if not handle:
        return dict(status='partial', reason='process-unavailable', winerror=C.get_last_error())
    try:
        creation = backend.creation_time(handle)
        require(creation.get('filetime') is not None and creation == evidence['processCreationTime'], 'Created process identity differs')
        require(Path(backend.image(handle)).samefile(client/'YuanShen.exe') and backend.status(handle)['alive'], 'Live image/lifetime differs')
        observed = evidence['samples'][-1]['modules']
        snapshot = backend.snapshot(8|16, evidence['pid'], clean.MODULEENTRY32W, 'Module32FirstW', 'Module32NextW')
        require(observed['error'] is None and snapshot['error'] is None, 'Module enumeration failed')
        modules = {}
        for name, raw in (('Astrolabe.dll', proxy), ('Astrolabe_orig.dll', stock), ('AccountPlatNative.dll', sdk)):
            wanted = client/'YuanShen_Data/Plugins'/name
            current = [row for row in snapshot['items'] if row['name'].casefold() == name.casefold()]
            prior = [row for row in observed['items'] if row['name'].casefold() == name.casefold()]
            require(len(current) == len(prior) == 1, 'Required module is absent or ambiguous')
            row, old = current[0], prior[0]
            require(Path(row['path']).samefile(wanted) and Path(old['path']).samefile(wanted)
                    and row['size'] == old['size'] == clean.PE(raw).image_size and row['base'] == old['base']
                    and clean.sha256(Path(row['path'])) == digest(raw), 'Module file/base/size/disk identity differs')
            modules[name] = row
        backend.k.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        backend.k.ReadProcessMemory.restype = W.BOOL
        def read(address, width):
            buffer, length = C.create_string_buffer(width), C.c_size_t()
            ok = backend.k.ReadProcessMemory(handle, address, buffer, width, C.byref(length))
            return buffer.raw[:length.value], None if ok and length.value == width else dict(winerror=C.get_last_error(), bytesRead=length.value)
        result = collect(read, modules, contract, [row['rva'] for row in clean.PE(stock).exports()])
        result['statusAtEnd'] = backend.status(handle)
        if not result['statusAtEnd']['alive']:
            result['status'] = 'partial'
        result['modules'] = modules
        return result
    finally:
        backend.close(handle)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--owned-emitter-report', type=Path, required=True)
    parser.add_argument('--check-owned-emitter', action='store_true', help='Offline only; no monitor or process API')
    parser.add_argument('--user-ready', action='store_true')
    args = parser.parse_args()
    if not args.check_owned_emitter and not args.user_ready:
        parser.error('A new user-ready response is required for this visible startup')
    output = args.output.resolve()
    if output.exists() or not output.is_relative_to(RAW.resolve()):
        parser.error('Choose a new key-loader/observation output')
    result = dict(scope='Fixed key-loader module monitor/state/code; no target function or authentication',
        sourceSHA256=clean.sha256(Path(__file__)), startUtc=datetime.now(timezone.utc).isoformat(), status='preflight')
    try:
        require(args.owned_emitter_report.resolve().is_relative_to(RAW.resolve()), 'Owned emitter proof must be inside observation raw')
        owned_report = json.loads(args.owned_emitter_report.read_bytes())
        if args.check_owned_emitter:
            _, _, contract = audit_sources(args.client)
            result['ownedEmitter'] = verify_owned_emitter(owned_report, contract)
            result.update(status='completed', scope='Offline owned original-emitter/code comparison only')
            inputs = None
        else:
            inputs, proxy, stock, sdk, contract = verify_installed(args.client)
            result['ownedEmitter'] = verify_owned_emitter(owned_report, contract)
        result['ownedEmitterReportSHA256'] = digest(args.owned_emitter_report.read_bytes())
        if inputs is not None:
            result['inputs'] = inputs
            backend = clean.ModuleBackend()
            clean.observe(backend, args.client, result, 90)
            result['binding'] = inspect_process(backend, args.client, result, proxy, stock, sdk, contract)
            result['status'] = 'completed' if result['binding']['status'] in ('installed-code-bound', 'failed-state-original-code-bound') else 'partial'
    except (OSError, ValueError, KeyError, IndexError) as failure:
        result.update(status='partial', errorType=type(failure).__name__, error=str(failure))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2); stream.write('\n')
    print(json.dumps(dict(status=result['status'], binding=result.get('binding', {}).get('status'), naturalCallerObserved=False, d02Passed=False)))
    if result['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
