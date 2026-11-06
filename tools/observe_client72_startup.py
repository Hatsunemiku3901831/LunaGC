"""One bounded, original CN72 startup observation through an existing debugger.

Raw process details, strings, registers and logs are restricted to local/.
No client call is synthesized, and no check or return value is changed.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import traceback

from check_debug_channel72 import DEBUGGER_FILES, process_inventory, relevant_processes
from extract_client72 import HASHES, PE, number, read_verified

ENTRIES = {'marshal': 0x159C21C90, 'factory': 0x14052FBF0,
           'http': 0x15A3464C0, 'browser': 0x15A366300, 'sdk_rsa': 0x15997C290}
GLOBAL_DISPATCH_ENTRY = 0x1512250E0
GLOBAL_DISPATCH_RETURN = 0x14F894D43
TERMINATORS = ('ntdll.NtTerminateProcess', 'ntdll.RtlExitUserProcess',
               'kernelbase.TerminateProcess')
DR_NAMES = ('dr0', 'dr1', 'dr2', 'dr3', 'dr6', 'dr7')
STOP_EVENTS = ('EVENT_SYSTEMBREAKPOINT', 'EVENT_BREAKPOINT', 'EVENT_EXCEPTION',
               'EVENT_PAUSE_DEBUG')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=120, choices=range(90, 151))
    parser.add_argument('--summary-output', type=Path)
    parser.add_argument('--main-observations', choices=('software', 'none', 'global-dispatch'), default='software',
                        help='none runs zero main points; global-dispatch observes only the verified HTTP body consumer.')
    parser.add_argument('--resume-from', type=Path,
                        help='Resume only this observer\'s first system-stop hardware rejection; no new load.')
    parser.add_argument('--continue-thread-name-from', type=Path,
                        help='Continue the same pending, verified Windows thread-name exception; no new load.')
    parser.add_argument('--prior-observation', type=Path,
                        help='Local evidence proving ownership of restored R2 software points.')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    args.output = args.output.resolve()
    if not args.output.is_relative_to(root / 'local') or args.output.exists():
        parser.error('Choose a new raw output file within this checkout local/.')
    if args.resume_from:
        args.resume_from = args.resume_from.resolve()
        if not args.resume_from.is_relative_to(root / 'local'):
            parser.error('Resume evidence must be in local/.')
    if args.continue_thread_name_from:
        args.continue_thread_name_from = args.continue_thread_name_from.resolve()
        if args.resume_from or not args.continue_thread_name_from.is_relative_to(root / 'local'):
            parser.error('Choose one local continuation source.')
    if args.prior_observation:
        args.prior_observation = args.prior_observation.resolve()
        if not args.prior_observation.is_relative_to(root / 'local'):
            parser.error('Prior observation must be in local/.')
    report = execute(root, args)
    if args.summary_output:
        args.summary_output.write_text(json.dumps(summarize(report), indent=2) + '\n', 'utf8')
    print(json.dumps(summarize(report)), flush=True)


def stop_action(event, expressions, cip, owned, entrypoint, tls_addresses, thread_name_validated=False):
    """Classify current evidence; stale ex.* never classifies a breakpoint."""
    if event is None:
        return 'stop-missing-event'
    kind, data = event['event'], event['data']
    if kind == 'EVENT_EXCEPTION':
        expected = {'code': data['ExceptionCode'], 'addr': data['ExceptionAddress'],
                    'firstchance': int(data['dwFirstChance'])}
        if any(expressions.get(k) != [v, True] for k, v in expected.items()):
            return 'stop-exception-mismatch'
        if not data['dwFirstChance']:
            return 'stop-second-chance'
        if data['ExceptionCode'] in (0xC0000005, 0xE06D7363):
            return 'deliver-current-application-first-chance'
        if data['ExceptionCode'] == 0x406D1388 and thread_name_validated:
            return 'deliver-current-application-first-chance'
        return 'stop-unknown-exception'
    if kind == 'EVENT_SYSTEMBREAKPOINT':
        return 'consume-system-stop'
    if kind == 'EVENT_BREAKPOINT':
        if data['addr'] != cip:
            return 'stop-breakpoint-address-mismatch'
        if owned.get(cip) == data['name']:
            return 'observe-owned-point'
        if data['singleshoot'] and cip == entrypoint:
            return 'consume-main-entrypoint'
        if data['singleshoot'] and cip in tls_addresses:
            return 'consume-verified-tls-callback'
        return 'stop-unowned-breakpoint'
    return 'stop-unrequested-pause'


def matching_returns(pending, snapshot):
    regs = snapshot['registers']
    return [item for item in pending if item['address'] == regs['cip']
            and item['expectedRsp'] == regs['rsp'] and item['tid'] == snapshot['tid']]


def main_entries(mode):
    return {'software': ENTRIES, 'none': {},
            'global-dispatch': {'global_dispatch': GLOBAL_DISPATCH_ENTRY}}[mode]


def return_observation(origin, snapshot, managed_string):
    item = dict(kind=origin['kind'], returnMatched=True, origin=origin, **snapshot)
    if origin['kind'] not in ('browser', 'global_dispatch'):
        item.update(managed_string(snapshot['registers']['rax']))
    else:
        item['returnValueSemantics'] = 'void; no result object asserted'
    if origin.get('inputHex') is not None and item.get('resultUtf16Hex') is not None:
        try:
            expected = bytes.fromhex(origin['inputHex']).decode('utf8').encode('utf-16-le')
            item['conversionMatches'] = expected.hex() == item['resultUtf16Hex']
        except UnicodeError:
            item['conversionMatches'] = 'input-not-valid-utf8'
    return item


def register_action(snapshot):
    registers = snapshot['registers']
    if any(registers[key] != value for key, value in snapshot['registerExpressions'].items()):
        return 'stop-register-api-mismatch'
    if any(registers[key] for key in ('dr0', 'dr1', 'dr2', 'dr3')) or registers['dr7'] & ~0x400:
        return 'stop-hardware-state-changed'
    if registers['dr6'] not in (0, 0xffff0ff0):
        return 'stop-hardware-status-changed'
    return None


def thread_name_payload(event_data):
    """Microsoft's x64 pack(8) THREADNAME_INFO occupies three ULONG_PTR values."""
    values = event_data['ExceptionInformation']
    return (event_data['ExceptionCode'] == 0x406D1388 and event_data['dwFirstChance']
            and event_data['NumberParameters'] == 3 and len(values) == 3
            and values[0] == 0x1000 and values[1] != 0 and values[2] >> 32 == 0)


def exit_log_codes(messages):
    # This fixed debugger emits a localized stop line followed by the .dd64 save path.
    return re.findall(r'\b0x([0-9A-Fa-f]{1,8}) \((?:STATUS_[A-Z0-9_]+|[0-9]+)\)[^\n]*\.dd64',
                      '\n'.join(messages))


def summarize(report):
    events = Counter(row['event'] for row in report['events'])
    exceptions = Counter(f"{row['data']['ExceptionCode']:08X}" for row in report['events']
                         if row['event'] == 'EVENT_EXCEPTION')
    returns = [row for row in report['observations'] if row.get('returnMatched')]
    return dict(schemaVersion=1, task='D02-R2 / D-01b5', inputSHA256=report['inputSHA256'],
                debuggerFiles=report['debuggerFiles'], pythonClientVersion='0.9.2',
                observationBudgetSeconds=report['observationBudgetSeconds'],
                mainObservationMode=report.get('mainObservationMode', 'software'),
                naturalEntryObservationEnabled=report.get('mainObservationMode', 'software') != 'none',
                ready=report.get('ready', False), stopReason=report.get('stopReason'),
                elapsedSeconds=report.get('elapsedSeconds'), events=dict(events),
                exceptions=dict(exceptions), pubThreadAlive=report.get('pubThreadAlive'),
                osStatus=report.get('osStatus'), finalState=report.get('finalState'),
                logExitCodes=report.get('logExitCodes', []),
                logEvicted=report.get('logEvicted', 0),
                codeChecks=report.get('codeChecks', []),
                naturalEntries=[row['kind'] for row in report['observations']
                                if row.get('entryReached')],
                naturalReturns=[dict(kind=row['kind'], resultLength=row.get('resultLength'),
                                     conversionMatches=row.get('conversionMatches')) for row in returns],
                originalClientFilesUnchanged=report.get('originalClientFilesUnchanged'),
                softwarePointsRemaining=(len(report.get('ownedPointsRemaining', {}))
                                         if report.get('finalState', {}).get('debugging') else 0),
                newHardwareInstalled=False,
                inheritedHardwareRemoved=('inheritedHardwareCleanup' in report or
                                           report.get('inheritedHardwareAlreadyCleared', False) or
                                           bool(report.get('factoryHardwareCleanup'))),
                clientCallsSynthesized=False, checksChanged=False,
                patchInstalled=False, serviceStarted=False, sdkAuthenticationVerified=False,
                loginOrWorldVerified=False, oldExitCause='unknown')


def execute(root, args):
    import ctypes as C
    from importlib.metadata import version
    from x64dbg_automate import X64DbgClient
    from x64dbg_automate.events import EventType
    from x64dbg_automate.models import BreakpointType

    game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                     str(root / 'tools/game_path.ps1')], text=True).strip()).resolve()
    target = game / 'YuanShen.exe'
    binary = read_verified(target, HASHES['YuanShen.exe'])
    for name, digest in HASHES.items():
        if name != 'YuanShen.exe':
            read_verified(game / name, digest)
    pe = PE(binary)
    profile = json.loads((root / 'protocol/cn-7.2/profile.json').read_text('utf8'))
    hashes = dict(HASHES)
    for key in ('sdk', 'astrolabe'):
        row = profile['files'][key]
        read_verified(game / row['path'], row['sha256'])
        hashes[row['path']] = row['sha256']
    debugger = (root / 'local/runtime/x64dbg-2026.05.27/release/x64/x64dbg.exe').resolve()
    provenance = {name: hashlib.sha256((debugger.parent / name).read_bytes()).hexdigest()
                  for name in DEBUGGER_FILES}
    assert provenance == DEBUGGER_FILES and version('x64dbg_automate') == '0.9.2'
    sessions = [s for s in X64DbgClient.list_sessions() if s.host in ('localhost', '127.0.0.1')]
    assert len(sessions) == 1 and Path(sessions[0].cmdline[0]).resolve() == debugger
    continuation = args.continue_thread_name_from
    resume_path = continuation or args.resume_from
    resumed = json.loads(resume_path.read_text('utf8')) if resume_path else None
    prior_observation = json.loads(args.prior_observation.read_text('utf8')) if args.prior_observation else None
    if prior_observation:
        assert prior_observation['inputSHA256'] == hashes
        assert not prior_observation['finalState']['debugging']
    related = relevant_processes(process_inventory(), game, target)
    if resumed:
        assert resumed['ready'] and not resumed['ownedPointsRemaining']
        if continuation:
            assert resumed['mainObservationMode'] == args.main_observations == 'none'
            assert resumed['stopReason'] == 'stop-unknown-exception'
            assert thread_name_payload(resumed['stops'][-1]['event']['data'])
        else:
            assert not resumed['commands']
        assert resumed['finalState'] == dict(debugging=True, running=False)
        assert resumed['osStatus']['alive'] and resumed['inputSHA256'] == hashes
        assert resumed['session']['pid'] == sessions[0].pid
        assert len(related) == 1 and related[0]['ProcessId'] == resumed['pid']
    else:
        assert not related, 'Existing game must not be replaced'
    report = dict(task='D02-R2 / D-01b5', inputSHA256=hashes, debuggerFiles=provenance,
                  sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  mainObservationMode=args.main_observations,
                  observationBudgetSeconds=args.seconds, events=[], stops=[], observations=[], commands=[],
                  logMessages=[], logEvicted=0, session=sessions[0].model_dump(mode='json'),
                  configSHA256={name: hashlib.sha256((root / name).read_bytes()).hexdigest()
                                for name in ('.env', '.env.local', 'config.json') if (root / name).exists()})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    k32 = C.WinDLL('kernel32', use_last_error=True)
    k32.OpenProcess.argtypes = [C.c_uint32, C.c_int, C.c_uint32]
    k32.OpenProcess.restype = C.c_void_p
    k32.GetExitCodeProcess.argtypes = [C.c_void_p, C.POINTER(C.c_uint32)]
    k32.WaitForSingleObject.argtypes = [C.c_void_p, C.c_uint32]
    k32.WaitForSingleObject.restype = C.c_uint32
    k32.QueryFullProcessImageNameW.argtypes = [C.c_void_p, C.c_uint32, C.c_wchar_p, C.POINTER(C.c_uint32)]
    k32.CloseHandle.argtypes = [C.c_void_p]
    class ThreadEntry(C.Structure):
        _fields_ = [('size', C.c_uint32), ('usage', C.c_uint32), ('tid', C.c_uint32),
                    ('pid', C.c_uint32), ('priority', C.c_long), ('delta', C.c_long), ('flags', C.c_uint32)]
    k32.CreateToolhelp32Snapshot.argtypes = [C.c_uint32, C.c_uint32]
    k32.CreateToolhelp32Snapshot.restype = C.c_void_p
    k32.Thread32First.argtypes = [C.c_void_p, C.POINTER(ThreadEntry)]
    k32.Thread32Next.argtypes = [C.c_void_p, C.POINTER(ThreadEntry)]
    c = X64DbgClient(str(debugger))
    c.attach_session(sessions[0].pid)
    points, pending, process, pid, base = {}, [], None, None, None
    log_index, started, entrypoint = 0, time.monotonic(), None

    def capture(event):
        report['events'].append(dict(time=time.monotonic(), event=str(event.event_type),
                                    data=event.event_data.model_dump(mode='json') if event.event_data else None))
    for kind in (EventType.EVENT_SYSTEMBREAKPOINT, EventType.EVENT_BREAKPOINT,
                 EventType.EVENT_EXCEPTION, EventType.EVENT_PAUSE_DEBUG, EventType.EVENT_RESUME_DEBUG,
                 EventType.EVENT_CREATE_PROCESS, EventType.EVENT_EXIT_PROCESS,
                 EventType.EVENT_LOAD_DLL, EventType.EVENT_STOP_DEBUG):
        c.watch_debug_event(kind, capture)

    def command(value):
        report['commands'].append(dict(time=time.monotonic(), command=value))
        assert c.cmd_sync(value), value

    def os_status():
        if not process:
            return None
        code = C.c_uint32()
        assert k32.GetExitCodeProcess(process, C.byref(code))
        wait = k32.WaitForSingleObject(process, 0)
        assert wait in (0, 258)
        return dict(alive=wait == 258, signaled=wait == 0, exitCode=code.value)

    def pull_log():
        nonlocal log_index
        log_index, lines, remaining, evicted = c.get_log(log_index)
        assert remaining == 0
        report['logMessages'].extend(lines)
        report['logEvicted'] += evicted

    def save():
        report['elapsedSeconds'] = round(time.monotonic() - started, 3)
        report['pubThreadAlive'] = c.sub_thread.is_alive()
        report['osStatus'] = os_status()
        report['finalState'] = dict(debugging=c.is_debugging(), running=c.is_running())
        report['ownedPointsRemaining'] = {str(k): v for k, v in points.items()}
        args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')

    def read(pointer, length):
        if not pointer or not 0 < length <= 8192:
            return None
        if not c.check_valid_read_ptr(pointer) or not c.check_valid_read_ptr(pointer + length - 1):
            return None
        return c.read_memory(pointer, length)

    def validate_thread_name(data):
        if not thread_name_payload(data):
            return None
        values = data['ExceptionInformation']
        name = read(values[1], 256)
        if name is None or b'\0' not in name:
            return None
        target_tid = values[2] & 0xffffffff
        if target_tid == 0xffffffff:
            target_tid, ok = c.eval_sync('tid()'); assert ok
        handle = k32.CreateToolhelp32Snapshot(4, 0)
        assert handle not in (None, 0, C.c_void_p(-1).value)
        entry = ThreadEntry(); entry.size = C.sizeof(entry)
        found = False
        try:
            ok = k32.Thread32First(handle, C.byref(entry))
            while ok:
                if entry.pid == pid and entry.tid == target_tid:
                    found = True
                    break
                ok = k32.Thread32Next(handle, C.byref(entry))
        finally:
            k32.CloseHandle(handle)
        if not found:
            return None
        return dict(payloadMatches=True, targetThreadExists=True, targetTid=target_tid,
                    nameBytesHex=name.split(b'\0')[0].hex())

    def snapshot():
        context = c.get_regs().context
        registers = {name: getattr(context, name) for name in
                     ('rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'rbp', 'rsp',
                      'r8', 'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15', 'eflags') + DR_NAMES}
        registers['cip'] = context.rip
        stack, code = read(registers['rsp'], 256), read(registers['cip'], 64)
        tid, ok = c.eval_sync('tid()'); assert ok
        return dict(registers=registers, tid=tid, stackHex=stack.hex() if stack else None,
                    codeHex=code.hex() if code else None,
                    registerExpressions={key: c.get_reg(key) for key in ('cip', 'rsp', 'eflags') + DR_NAMES})

    def clear_inherited_factory(label):
        hardware = c.get_breakpoints(BreakpointType.BpHardware)
        before = snapshot()
        if not hardware and register_action(before) is None:
            return
        old_path = root / 'local/migration72/evidence/d01b3-hardware.json'
        old = json.loads(old_path.read_text('utf8'))
        old_point = old['ownedHardware']
        address = base + ENTRIES['factory'] - pe.base
        assert old_point['name'] == 'cn72_d01b3_factory_hw' and old_point['slot'] == 0
        assert old_point['address'] - old['base'] == ENTRIES['factory'] - pe.base
        assert all(before['registers'][key] == value for key, value in before['registerExpressions'].items())
        dr = before['registers']
        assert dr['dr0'] in (0, address) and not any(dr[key] for key in ('dr1', 'dr2', 'dr3'))
        assert dr['dr7'] & ~0x401 == 0 and dr['dr6'] & 0xE00F == 0
        if hardware:
            assert len(hardware) == 1 and hardware[0].name == old_point['name']
            assert hardware[0].addr == address and hardware[0].slot == 0 and hardware[0].mod == 'yuanshen.exe'
            assert not hardware[0].breakCondition and not hardware[0].commandText
            assert c.clear_hardware_breakpoint(address)
        else:
            assert dr['dr0'] == address
        # At EP the existing software observer masks this byte in debugger reads.
        actual = c.read_memory(address, 32)
        expected = binary[pe.offset(ENTRIES['factory'], 32, executable=True):][:32]
        assert actual == expected and hashlib.sha256(actual).hexdigest() == old['codeSHA256']
        after_api = snapshot()
        for key in ('dr0', 'dr6', 'dr7'):
            assert c.set_reg(key, 0)
        after = snapshot()
        report.setdefault('factoryHardwareCleanup', []).append(dict(
            stage=label, sourceSHA256=hashlib.sha256(old_path.read_bytes()).hexdigest(),
            before=before, afterAPI=after_api, after=after))
        assert all(before['registers'][key] == after['registers'][key]
                   for key in before['registers'] if key not in DR_NAMES + ('eflags',))
        assert before['registers']['eflags'] ^ after['registers']['eflags'] in (0, 2)
        assert not c.get_breakpoints(BreakpointType.BpHardware) and register_action(after) is None

    def remove(address):
        found = [bp for bp in c.get_breakpoints(BreakpointType.BpNormal) if bp.addr == address]
        assert len(found) == 1 and found[0].name == points[address]
        assert c.clear_breakpoint(address)
        del points[address]

    def point(address, name):
        assert not any(bp.addr == address for bp in c.get_breakpoints(BreakpointType.BpNormal))
        assert c.set_breakpoint(address, name)
        points[address] = name

    def managed_string(pointer):
        raw = read(pointer, 24)
        if raw is None:
            return dict(resultPointer=pointer, resultReadable=False)
        length = int.from_bytes(raw[16:20], 'little')
        value = read(pointer + 20, length * 2) if length else b''
        return dict(resultPointer=pointer, resultLength=length,
                    resultUtf16Hex=value.hex() if value is not None else None)

    def observe(row):
        snap = row['snapshot']
        regs = snap['registers']
        name = points[regs['cip']]
        if name.startswith('cn72_r2_exit_'):
            report['observations'].append(dict(kind=name, terminationApiReached=True, **snap))
            return 'termination-api-pending'
        if name.startswith('cn72_r2_return'):
            matches = matching_returns(pending, snap)
            if not matches:
                return 'return-unrelated-thread-or-stack'
            for origin in matches:
                item = return_observation(origin, snap, managed_string)
                report['observations'].append(item)
                pending.remove(origin)
            remove(regs['cip'])
            if any(item['kind'] in ('http', 'browser', 'sdk_rsa', 'global_dispatch') or
                   (item['kind'] == 'marshal' and item.get('inputHex')) for item in matches):
                return 'natural-call-return-observed'
            return None
        kind = name.removeprefix('cn72_r2_')
        item = dict(kind=kind, entryReached=True, **snap)
        raw = None
        if kind == 'marshal':
            prefix = read(regs['rcx'], 1024)
            item['inputPrefixHex'] = prefix.hex() if prefix is not None else None
            raw = prefix.split(b'\0')[0] if prefix is not None and b'\0' in prefix else None
        elif kind == 'factory':
            length = regs['rdx'] & 0xffffffff
            raw = read(regs['rcx'], length) if length else b''
            item['inputByteLength'] = length
        elif kind == 'global_dispatch':
            item['receiver'] = regs['rcx']
            item['completionAction'] = regs['r8']
            item['bodyString'] = managed_string(regs['rdx'])
            body_length = item['bodyString'].get('resultLength')
            if body_length and body_length > 4096:
                prefix = read(regs['rdx'] + 20, 8192)
                item['bodyString']['prefixUtf16Hex'] = prefix.hex() if prefix else None
                item['bodyString']['prefixCharLimit'] = 4096
        else:
            # Browser receiver is RCX; all other named parameters are strings.
            for reg in (('rdx',) if kind == 'browser' else ('rcx', 'rdx')):
                item[reg + 'String'] = managed_string(regs[reg])
        report['observations'].append(item)
        address = c.read_qword(regs['rsp'])
        assert c.check_valid_read_ptr(address)
        if kind == 'global_dispatch':
            if (not item['bodyString'].get('resultLength') or
                    item['bodyString'].get('resultUtf16Hex') is None and
                    item['bodyString'].get('prefixUtf16Hex') is None):
                return 'global-dispatch-body-unreadable-or-empty'
            if address != base + GLOBAL_DISPATCH_RETURN - pe.base:
                return 'global-dispatch-unreviewed-return-site'
            expected = binary[pe.offset(GLOBAL_DISPATCH_RETURN, 32, executable=True):][:32]
            assert c.read_memory(address, 32) == expected
            # Keep exactly one main-module point: replace entry by this return site.
            remove(regs['cip'])
        if address not in points:
            point(address, 'cn72_r2_return')
        else:
            assert points[address] == 'cn72_r2_return'
        pending.append(dict(kind=kind, address=address, expectedRsp=regs['rsp'] + 8,
                            tid=snap['tid'], inputHex=raw.hex() if raw is not None else None))
        if kind != 'global_dispatch':
            remove(regs['cip'])
        return None

    def verified_tls_addresses(event):
        if event['event'] != 'EVENT_BREAKPOINT' or not event['data']['singleshoot']:
            return set()
        address = event['data']['addr']
        module, ok = c.eval_sync(f'mod.base(0x{address:x})')
        if not ok or not module:
            return set()
        header = read(module, 4096)
        if header is None or header[:2] != b'MZ':
            return set()
        optional = number(header, 0x3c) + 24
        if optional + 192 > len(header) or number(header, optional, 'H') != 0x20b:
            return set()
        tls_rva = number(header, optional + 112 + 9 * 8)
        tls = read(module + tls_rva, 40) if tls_rva else None
        if tls is None:
            return set()
        callbacks = int.from_bytes(tls[24:32], 'little')
        result = set()
        for index in range(16):
            raw = read(callbacks + index * 8, 8)
            if raw is None or int.from_bytes(raw, 'little') == 0:
                break
            result.add(int.from_bytes(raw, 'little'))
        return result

    def wait_event(floor, deadline):
        stopped_since = None
        while time.monotonic() < deadline:
            if not c.is_debugging() or not os_status()['alive']:
                return None
            if not c.is_running():
                found = [row for row in report['events'][floor:] if row['event'] in STOP_EVENTS]
                specific = [row for row in found if row['event'] != 'EVENT_PAUSE_DEBUG']
                if specific:
                    return specific[-1]
                stopped_since = stopped_since or time.monotonic()
                if time.monotonic() - stopped_since > 1:
                    return found[-1] if found else None
            else:
                stopped_since = None
            if time.monotonic() - report.get('lastProgress', started) >= 10:
                report['lastProgress'] = time.monotonic()
                pull_log(); save()
                print(json.dumps(dict(progressSeconds=report['elapsedSeconds'],
                                      stops=len(report['stops']), entries=len(report['observations']),
                                      state=report['finalState'], osAlive=report['osStatus']['alive'],
                                      pubThreadAlive=report['pubThreadAlive'])), flush=True)
            time.sleep(0.04)
        return None

    try:
        assert c.get_debugger_pid() == sessions[0].pid and c.debugger_is_elevated()
        time.sleep(0.6)
        if resumed:
            assert c.is_debugging() and not c.is_running()
            assert c.eval_sync('$pid') == (resumed['pid'], True)
            assert c.eval_sync('mod.main()') == (resumed['base'], True)
            current = snapshot()
            previous_snapshot = resumed['finalSnapshot'] if continuation else resumed['initialSnapshot']
            for key in ('cip', 'rsp'):
                assert current['registers'][key] == previous_snapshot['registers'][key]
            assert current['tid'] == previous_snapshot['tid']
            report['resumeFrom'] = str(resume_path)
            report['resumeEvidenceSHA256'] = hashlib.sha256(resume_path.read_bytes()).hexdigest()
            report['osHandleGapBeforeContinuation'] = True
            report['continuationKind'] = 'verified-thread-name-stop' if continuation else 'rejected-system-preflight'
            report['events'] = list(resumed['events'])
        else:
            assert not c.is_debugging()
            assert not relevant_processes(process_inventory(), game, target)
            report['loadHelperResult'] = c.load_executable(str(target), current_dir=str(game))
        ready_deadline = time.monotonic() + 10
        while time.monotonic() < ready_deadline:
            creates = [row for row in report['events'] if row['event'] == 'EVENT_CREATE_PROCESS']
            systems = [row for row in report['events'] if row['event'] == 'EVENT_SYSTEMBREAKPOINT']
            if creates and systems and c.is_debugging() and not c.is_running():
                create = creates[-1]['data']
                pid = create['dwProcessId']
                assert c.eval_sync('$pid') == (pid, True)
                assert Path(create['debugFileName']).resolve() == target
                process = k32.OpenProcess(0x101000, False, pid)
                assert process, f'OpenProcess failed: {C.get_last_error()}'
                path, length = C.create_unicode_buffer(32768), C.c_uint32(32768)
                assert k32.QueryFullProcessImageNameW(process, 0, path, C.byref(length))
                assert Path(path.value).resolve() == target and os_status()['alive']
                report.update(ready=True, pid=pid, osPathMatches=True, processHandleAvailable=True)
                break
            time.sleep(0.05)
        else:
            raise ValueError('Missing fresh matching CREATE_PROCESS/SYSTEMBREAKPOINT/OS readiness')
        base, ok = c.eval_sync('mod.main()'); assert ok
        report['base'] = base
        entrypoint = base + number(binary, number(binary, 0x3c) + 24 + 16)
        report['initialSnapshot'] = snapshot()
        prior = {str(kind): [bp.model_dump(mode='json') for bp in c.get_breakpoints(kind)]
                 for kind in (BreakpointType.BpNormal, BreakpointType.BpHardware,
                              BreakpointType.BpMemory, BreakpointType.BpException)}
        report['priorBreakpoints'] = prior
        if not resumed:
            clear_inherited_factory('system-breakpoint')
            report['initialSnapshot'] = snapshot()
        elif continuation:
            assert not c.get_breakpoints(BreakpointType.BpHardware)
            pending_exception = resumed['stops'][-1]['event']['data']
            for key, value in (('code', pending_exception['ExceptionCode']),
                               ('addr', pending_exception['ExceptionAddress']), ('firstchance', 1)):
                assert c.eval_sync('ex.' + key + '()') == (value, True)
            report['pendingThreadNameValidation'] = validate_thread_name(pending_exception)
            assert report['pendingThreadNameValidation']
        elif c.get_breakpoints(BreakpointType.BpHardware):
            # The first load exposed this exact old owned point from the debugger database.
            old_path = root / 'local/migration72/evidence/d01b3-hardware.json'
            old = json.loads(old_path.read_text('utf8'))
            old_point = old['ownedHardware']
            hardware = c.get_breakpoints(BreakpointType.BpHardware)
            address = base + ENTRIES['factory'] - pe.base
            assert old_point['name'] == 'cn72_d01b3_factory_hw' and old_point['slot'] == 0
            assert old_point['address'] - old['base'] == ENTRIES['factory'] - pe.base
            assert len(hardware) == 1 and hardware[0].name == old_point['name']
            assert hardware[0].addr == address and hardware[0].slot == 0 and hardware[0].mod == 'yuanshen.exe'
            assert not hardware[0].breakCondition and not hardware[0].commandText
            before = snapshot()
            dr = before['registers']
            assert dr['dr0'] == address and not any(dr[name] for name in ('dr1', 'dr2', 'dr3'))
            assert dr['dr7'] & ~0x401 == 0 and dr['dr6'] & 0xE00F == 0
            actual = c.read_memory(address, 32)
            expected = binary[pe.offset(ENTRIES['factory'], 32, executable=True):][:32]
            assert actual == expected and hashlib.sha256(actual).hexdigest() == old['codeSHA256']
            assert c.clear_hardware_breakpoint(address)
            after_api = snapshot()
            for name in ('dr0', 'dr6', 'dr7'):
                assert c.set_reg(name, 0)
            after = snapshot()
            report['inheritedHardwareCleanup'] = dict(sourceSHA256=hashlib.sha256(old_path.read_bytes()).hexdigest(),
                                                       before=before, afterAPI=after_api, after=after)
            assert all(before['registers'][key] == after['registers'][key]
                       for key in before['registers'] if key not in DR_NAMES + ('eflags',))
            assert before['registers']['eflags'] ^ after['registers']['eflags'] in (0, 2)
            report['initialSnapshot'] = after
        elif resumed:
            current = report['initialSnapshot']['registers']
            original = resumed['initialSnapshot']['registers']
            assert all(current[key] == value for key, value in original.items()
                       if key not in DR_NAMES + ('eflags',))
            assert current['eflags'] ^ original['eflags'] in (0, 2)
            report['inheritedHardwareAlreadyCleared'] = True
            report['flagsReadback'] = dict(original=original['eflags'], current=current['eflags'],
                                           differingBits=current['eflags'] ^ original['eflags'])
        assert not c.get_breakpoints(BreakpointType.BpHardware)
        assert register_action(report['initialSnapshot']) is None
        assert not prior[str(BreakpointType.BpMemory)] and not prior[str(BreakpointType.BpException)]
        # A prior point is adopted only by its exact known name AND live symbol address.
        known_old = {}
        symbols = {}
        for symbol in TERMINATORS:
            address, ok = c.eval_sync(symbol); assert ok
            symbols[symbol] = address
            for prefix in ('cn72_observe_', 'cn72_d01b3_exit_'):
                known_old[prefix + symbol.replace('.', '_')] = address
            if prior_observation:
                name = 'cn72_r2_exit_' + symbol.replace('.', '_')
                assert name in prior_observation['ownedPointsRemaining'].values()
                known_old[name] = address
        if prior_observation:
            for kind, original in main_entries(prior_observation.get('mainObservationMode', 'software')).items():
                name = 'cn72_r2_' + kind
                assert name in prior_observation['ownedPointsRemaining'].values()
                known_old[name] = base + original - pe.base
            report['priorObservationSHA256'] = hashlib.sha256(args.prior_observation.read_bytes()).hexdigest()
        for bp in c.get_breakpoints(BreakpointType.BpNormal):
            if bp.singleshoot and bp.addr == entrypoint:
                continue
            assert known_old.get(bp.name) == bp.addr, 'Pre-existing breakpoint needs ownership review'
            assert not bp.breakCondition and not bp.commandText and not bp.fastResume
            report.setdefault('removedVerifiedPriorPoints', []).append(bp.model_dump(mode='json'))
            assert c.clear_breakpoint(bp.addr)
        for kind, original in ENTRIES.items():
            address = base + original - pe.base
            expected = binary[pe.offset(original, 32, executable=True):][:32]
            actual = c.read_memory(address, 32)
            assert actual == expected
            report.setdefault('codeChecks', []).append(dict(kind=kind, originalVA=f'{original:X}',
                                                            sha256=hashlib.sha256(actual).hexdigest(), match=True))
        if args.main_observations == 'global-dispatch':
            address = base + GLOBAL_DISPATCH_ENTRY - pe.base
            expected = binary[pe.offset(GLOBAL_DISPATCH_ENTRY, 32, executable=True):][:32]
            actual = c.read_memory(address, 32)
            assert actual == expected
            report['codeChecks'].append(dict(kind='global_dispatch', originalVA=f'{GLOBAL_DISPATCH_ENTRY:X}',
                                             sha256=hashlib.sha256(actual).hexdigest(), match=True))
        for kind, original in main_entries(args.main_observations).items():
            point(base + original - pe.base, 'cn72_r2_' + kind)
        for symbol, address in symbols.items():
            point(address, 'cn72_r2_exit_' + symbol.replace('.', '_'))
        report['stopReason'] = 'observation-budget'
        deadline = time.monotonic() + args.seconds
        event = resumed['stops'][-1]['event'] if continuation else systems[-1]
        while time.monotonic() < deadline and c.is_debugging() and os_status()['alive']:
            assert not c.is_running() and c.eval_sync('$pid') == (pid, True)
            row = dict(event=event, snapshot=snapshot())
            report['stops'].append(row)
            if event and event['event'] == 'EVENT_BREAKPOINT' and event['data']['singleshoot'] and event['data']['addr'] == entrypoint:
                clear_inherited_factory('main-entrypoint')
                row['snapshotAfterEntryHardwareReview'] = snapshot()
            register_problem = register_action(row.get('snapshotAfterEntryHardwareReview', row['snapshot']))
            if register_problem:
                row['action'] = report['stopReason'] = register_problem
                break
            expressions = {}
            if event and event['event'] == 'EVENT_EXCEPTION':
                expressions = {name: list(c.eval_sync('ex.' + name + '()'))
                               for name in ('code', 'addr', 'firstchance', 'flags')}
                row['exceptionExpressions'] = expressions
                if event['data']['ExceptionCode'] == 0x406D1388:
                    row['threadNameValidation'] = validate_thread_name(event['data'])
            tls = verified_tls_addresses(event) if event else set()
            row['verifiedTlsAddresses'] = sorted(tls)
            action = stop_action(event, expressions, row['snapshot']['registers']['cip'],
                                 points, entrypoint, tls, bool(row.get('threadNameValidation')))
            row['action'] = action
            if action.startswith('stop-'):
                report['stopReason'] = action
                break
            if action == 'observe-owned-point':
                reason = observe(row)
                if reason:
                    report['stopReason'] = reason
                    break
            pull_log(); save()
            assert c.is_debugging() and not c.is_running() and os_status()['alive']
            command('con 1' if action == 'deliver-current-application-first-chance' else 'con')
            floor = len(report['events'])
            command('run')
            event = wait_event(floor, deadline)
            if not c.is_debugging() or not os_status()['alive']:
                report['stopReason'] = 'target-exited'
                break
            if c.is_running():
                break
        if c.is_debugging() and c.is_running() and os_status()['alive']:
            floor = len(report['events'])
            report['observerPauseRequested'] = True
            report['pauseHelperResult'] = c.pause()
            pause_event = wait_event(floor, time.monotonic() + 3)
            report['pauseEvent'] = pause_event
            assert c.is_debugging() and not c.is_running() and os_status()['alive']
            assert c.eval_sync('$pid') == (pid, True)
            assert pause_event and pause_event['event'] == 'EVENT_PAUSE_DEBUG'
            report['budgetPauseVerified'] = True
        if c.is_debugging() and not c.is_running():
            report['finalSnapshot'] = snapshot()
            # Restore only points created in this invocation; leave the target paused.
            for address in list(points):
                remove(address)
            report['finalBreakpoints'] = [bp.model_dump(mode='json')
                                          for bp in c.get_breakpoints(BreakpointType.BpNormal)]
        time.sleep(0.8)
        pull_log()
        report['logExitCodes'] = exit_log_codes(report['logMessages'])
        report['finalRelatedProcesses'] = relevant_processes(process_inventory(), game, target)
        report['originalClientFilesUnchanged'] = all(hashlib.sha256((game / name).read_bytes()).hexdigest()
                                                      == digest for name, digest in hashes.items())
        save()
    except BaseException:
        report['observerError'] = traceback.format_exc()
        report['stopReason'] = 'observer-error'
        # A fault does not authorize a blind resume or a second load.
        if c.is_debugging() and c.is_running() and process and os_status()['alive']:
            report['errorPauseRequested'] = True
            report['errorPauseHelperResult'] = c.pause()
        pull_log(); save()
        raise
    finally:
        if process:
            k32.CloseHandle(process)
        c.detach_session()
    return report


if __name__ == '__main__':
    main()
