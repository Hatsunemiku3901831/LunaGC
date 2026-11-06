"""Exercise an owned synthetic Windows target through the existing local debugger.

No game is loaded. Raw paths, processes, registers and logs stay under local/.
AV/C++ exceptions are delivered individually; debugger-owned diagnostic traps
are separately classified and consumed with bare con.
"""
import argparse
import os
import subprocess
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import traceback
MODES = ('baseline', 'pause-con', 'pause-con0', 'hw-con', 'hw-con0')
DEBUGGER_FILES = {'x64dbg.exe': '68d6afc2cc5a5bfd74a1b4066c0651254661355a0e384524cd1a8772adb95092', 'x64dbg.dll': 'dc9d5ff18d6e5fc9d667a2c2bc8fa9cce15fa5e0da9728222259a4525d542c0f', 'plugins/x64dbg-automate.dp64': 'c68f468cdfc3b0beef2ad3f1afdf8465d36cc1274fc874600eb4c5301ac1fcac', 'plugins/libzmq-mt-4_3_5.dll': '008828618c4abb16f63d146370fa9af5150ba024ba9bd7e16b6181d5a14d6303'}

def process_inventory():
    script = '@(Get-CimInstance Win32_Process | Select-Object Name,ProcessId,ParentProcessId,ExecutablePath) | ConvertTo-Json -Compress'
    return json.loads(subprocess.check_output(['powershell.exe', '-NoProfile', '-Command', script], text=True, encoding='utf8'))

def relevant_processes(processes, game_root, target):
    rows = []
    for process in processes:
        raw_path = process['ExecutablePath']
        path = Path(raw_path).resolve() if raw_path else None
        if process['Name'].lower() in ('yuanshen.exe', 'genshinimpact.exe', target.name.lower()) or (path and (path == target or path.is_relative_to(game_root))):
            rows.append(process)
    return rows

def configured_game_root(root):
    value = os.environ.get('GAME_PATH')
    for path in (root / '.env', root / '.env.local'):
        if path.exists():
            for line in path.read_text('utf-8-sig').splitlines():
                match = re.fullmatch('GAME_PATH=(.*)', line.strip())
                if match:
                    value = match[1].strip().strip('"').strip("'")
    if not value:
        raise ValueError('GAME_PATH must be available to audit running game processes')
    return Path(value).resolve()

def execute(args):
    import ctypes as C
    import struct, sys, time, uuid
    from importlib.metadata import version
    from x64dbg_automate import X64DbgClient
    from x64dbg_automate.events import EventType
    from x64dbg_automate.models import BreakpointType, HardwareBreakpointType
    root = Path(__file__).resolve().parents[1]
    sys.path.insert(0, str(root / 'tools'))
    from extract_client72 import PE, number
    target = args.target.resolve()
    manifest = json.loads(args.manifest.read_text('utf-8-sig'))
    assert target.is_relative_to((root / 'local').resolve())
    assert target == Path(manifest['target']).resolve()
    assert hashlib.sha256(target.read_bytes()).hexdigest() == manifest['targetSHA256']
    assert hashlib.sha256((root / manifest['source']).read_bytes()).hexdigest() == manifest['sourceSHA256']
    binary = target.read_bytes()
    pe = PE(binary)
    exports = {}
    optional = number(binary, 60) + 24
    directory = pe.offset(pe.base + number(binary, optional + 112))
    count = number(binary, directory + 24)
    functions = pe.offset(pe.base + number(binary, directory + 28))
    names = pe.offset(pe.base + number(binary, directory + 32))
    ordinals = pe.offset(pe.base + number(binary, directory + 36))
    for i in range(count):
        off = pe.offset(pe.base + number(binary, names + i * 4))
        name = binary[off:off + 256].split(b'\x00')[0].decode('ascii')
        ordinal = number(binary, ordinals + i * 2, 'H')
        exports[name] = number(binary, functions + ordinal * 4)
    assert all((name in exports for name in ('channel_marker', 'g_fixture_state', 'fixture_av', 'fixture_cpp')))
    debugger = args.debugger.resolve()
    sessions = [s for s in X64DbgClient.list_sessions() if s.host in ('localhost', '127.0.0.1')]
    assert len(sessions) == 1 and Path(sessions[0].cmdline[0]).resolve() == debugger.resolve()
    provenance = {name: hashlib.sha256((debugger.parent / name).read_bytes()).hexdigest() for name in DEBUGGER_FILES}
    assert provenance == DEBUGGER_FILES
    assert version('x64dbg_automate') == '0.9.2'
    game_root = configured_game_root(root)
    initial_processes = process_inventory()
    assert not relevant_processes(initial_processes, game_root, target)
    k32 = C.WinDLL('kernel32', use_last_error=True)
    k32.CreateEventW.argtypes = [C.c_void_p, C.c_int, C.c_int, C.c_wchar_p]
    k32.CreateEventW.restype = C.c_void_p
    k32.SetEvent.argtypes = [C.c_void_p]
    k32.CloseHandle.argtypes = [C.c_void_p]
    k32.OpenProcess.argtypes = [C.c_uint32, C.c_int, C.c_uint32]
    k32.OpenProcess.restype = C.c_void_p
    k32.GetExitCodeProcess.argtypes = [C.c_void_p, C.POINTER(C.c_uint32)]
    k32.WaitForSingleObject.argtypes = [C.c_void_p, C.c_uint32]
    c = X64DbgClient(str(debugger))
    c.attach_session(sessions[0].pid)
    assert c.get_debugger_pid() == sessions[0].pid and c.debugger_is_elevated() and (not c.is_debugging())
    all_events = []

    def capture(event):
        all_events.append(dict(time=time.monotonic(), event=str(event.event_type), data=event.event_data.model_dump(mode='json') if event.event_data else None))
    for kind in (EventType.EVENT_SYSTEMBREAKPOINT, EventType.EVENT_BREAKPOINT, EventType.EVENT_EXCEPTION, EventType.EVENT_PAUSE_DEBUG, EventType.EVENT_RESUME_DEBUG, EventType.EVENT_CREATE_PROCESS, EventType.EVENT_EXIT_PROCESS, EventType.EVENT_STOP_DEBUG):
        c.watch_debug_event(kind, capture)
    time.sleep(0.6)
    results = []
    report = dict(schemaVersion=1, task='D-01b4', fixtureSHA256=manifest['targetSHA256'], fixtureSourceSHA256=manifest['sourceSHA256'], debuggerFiles=provenance, pythonClientVersion='0.9.2', gameLoaded=False, initialProcesses=initial_processes, results=results)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    def regs():
        return {name: c.get_reg(name) for name in ('cip', 'rsp', 'rax', 'rcx', 'rdx', 'dr0', 'dr1', 'dr2', 'dr3', 'dr6', 'dr7')}

    def state_at(base):
        values = struct.unpack('<6I', c.read_memory(base + exports['g_fixture_state'], 24))
        assert values[0] == 0x72324442
        return dict(zip(('magic', 'phase', 'avCaught', 'cppCaught', 'markerCalls', 'ticks'), values))

    def wait_stop(floor, timeout=3):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if not c.is_debugging():
                return ('exited', None)
            if not c.is_running():
                matches = [e for e in all_events[floor:] if e['event'] in ('EVENT_BREAKPOINT', 'EVENT_EXCEPTION', 'EVENT_SYSTEMBREAKPOINT', 'EVENT_PAUSE_DEBUG')]
                specific = [e for e in matches if e['event'] != 'EVENT_PAUSE_DEBUG']
                if specific:
                    return ('paused', specific[-1])
                if matches:
                    time.sleep(0.1)
                    newer = [e for e in all_events[floor:] if e['event'] in ('EVENT_BREAKPOINT', 'EVENT_EXCEPTION', 'EVENT_SYSTEMBREAKPOINT')]
                    return ('paused', newer[-1] if newer else matches[-1])
            time.sleep(0.02)
        return ('running', None) if c.is_debugging() and c.is_running() else ('paused-missing-event', None)

    def run_checked(pid, commands):
        assert c.is_debugging() and (not c.is_running()) and (c.eval_sync('$pid') == (pid, True))
        floor = len(all_events)
        assert c.cmd_sync('run')
        commands.append('run')
        return wait_stop(floor, 0.3)

    def clear_hw(address):
        rows = c.get_breakpoints(BreakpointType.BpHardware)
        assert len(rows) == 1 and rows[0].addr == address and rows[0].name.startswith('cn72_channel_')
        before = regs()
        assert before['dr0'] == address and (not any((before[n] for n in ('dr1', 'dr2', 'dr3'))))
        assert c.clear_hardware_breakpoint(address)
        after_api = regs()
        # Only the just-owned slot is cleared; before/after CPU state is retained.
        for name in ('dr0', 'dr6', 'dr7'):
            assert c.set_reg(name, 0)
        assert not c.get_breakpoints(BreakpointType.BpHardware)
        return dict(before=before, afterAPI=after_api, after=regs())
    try:
        for ordinal, mode in enumerate(MODES):
            assert not c.is_debugging(), 'Do not replace another target'
            assert not relevant_processes(process_inventory(), game_root, target)
            case_dir = root / 'local/migration72/debug-channel/runs' / args.output.stem / f'{ordinal:02d}-{mode}'
            case_dir.mkdir(parents=True, exist_ok=True)
            report_path = case_dir / 'progress.jsonl'
            event_name = 'Local\\LunaGC72Channel_' + uuid.uuid4().hex
            gate = k32.CreateEventW(None, True, False, event_name)
            assert gate
            process = None
            floor = len(all_events)
            case_event_floor = floor
            # Automate clears its log buffer at every INITDEBUG.
            log_floor = 0
            commands = []
            row = dict(name=mode, events=[], stops=[], commands=commands, targetSHA256=hashlib.sha256(binary).hexdigest(), reportPath=str(report_path), eventName=event_name)
            try:
                row['loadHelperResult'] = c.load_executable(str(target), cmdline=f'"{event_name}" "{report_path}" "exceptions"', current_dir=str(target.parent))
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    fresh = all_events[case_event_floor:]
                    creates = [e for e in fresh if e['event'] == 'EVENT_CREATE_PROCESS']
                    systems = [e for e in fresh if e['event'] == 'EVENT_SYSTEMBREAKPOINT']
                    if creates and systems and c.is_debugging() and (not c.is_running()):
                        assert c.eval_sync('$pid') == (creates[-1]['data']['dwProcessId'], True)
                        assert Path(creates[-1]['data']['debugFileName']).resolve() == target
                        break
                    time.sleep(0.05)
                else:
                    raise ValueError('No matching created process/system-stop/state after load')
                row['matchingCreatedSystemStop'] = True
                pid, ok = c.eval_sync('$pid')
                assert ok
                base, ok = c.eval_sync('mod.main()')
                assert ok
                process = k32.OpenProcess(1052672, False, pid)
                row.update(pid=pid, base=base, processHandleAvailable=bool(process), processOpenError=0 if process else C.get_last_error())
                row['initialRegisters'] = regs()
                row['priorHardware'] = [bp.model_dump(mode='json') for bp in c.get_breakpoints(BreakpointType.BpHardware)]
                assert not row['priorHardware'] and (not any((row['initialRegisters'][n] for n in ('dr0', 'dr1', 'dr2', 'dr3', 'dr6', 'dr7'))))
                for bp in c.get_breakpoints(BreakpointType.BpNormal):
                    if bp.name.startswith('cn72_channel_'):
                        assert c.clear_breakpoint(bp.addr)
                assert c.cmd_sync('con')
                commands.append('con')
                status, event = run_checked(pid, commands)
                exceptions = 0
                hardware_set = False
                action_done = False
                gate20 = False
                gate30 = False
                started = time.monotonic()
                while time.monotonic() - started < 15:
                    if not c.is_debugging():
                        break
                    if not c.is_running():
                        if not event:
                            status, event = wait_stop(floor, 1)
                        stop = dict(event=event, registers=regs(), fixture=state_at(base))
                        row['stops'].append(stop)
                        if event and event['event'] == 'EVENT_EXCEPTION':
                            ex = event['data']
                            values = {key: c.eval_sync('ex.' + key + '()') for key in ('code', 'addr', 'firstchance')}
                            stop['expressions'] = values
                            assert values['code'] == (ex['ExceptionCode'], True) and values['addr'] == (ex['ExceptionAddress'], True) and (values['firstchance'] == (int(ex['dwFirstChance']), True))
                            if not ex['dwFirstChance']:
                                row['secondChance'] = True
                                break
                            if ex['ExceptionCode'] in (0x80000003, 0x80000004) and mode.endswith('con0') and action_done:
                                expected = 0x80000003 if mode == 'pause-con0' else 0x80000004
                                assert ex['ExceptionCode'] == expected and ex['ExceptionAddress'] == row['ownedStopAddress']
                                row.setdefault('forwardedOwnedTrap', []).append(dict(code=ex['ExceptionCode'], address=ex['ExceptionAddress'], sameAsOwnedStop=ex['ExceptionAddress'] == row['ownedStopAddress']))
                                assert c.cmd_sync('con')
                                commands.append('con')
                                floor = len(all_events)
                                status, event = run_checked(pid, commands)
                                continue
                            if ex['ExceptionCode'] not in (0xC0000005, 0xE06D7363):
                                row['unexpectedException'] = ex
                                break
                            exceptions += 1
                            if ex['ExceptionCode'] == 0xE06D7363 and mode.startswith('hw'):
                                assert c.is_debugging() and (not c.is_running()) and (c.eval_sync('$pid') == (pid, True))
                                address = base + exports['channel_marker']
                                assert c.set_hardware_breakpoint(address, HardwareBreakpointType.x, 1)
                                assert c.cmd_sync(f'bphwname 0x{address:x}, "cn72_channel_marker"')
                                hardware_set = True
                                row['hardwareSetRegisters'] = regs()
                            assert c.cmd_sync('con 1')
                            commands.append('con 1')
                        elif event and event['event'] == 'EVENT_BREAKPOINT' and (event['data']['name'] == 'cn72_channel_marker'):
                            row['hardwareHit'] = True
                            row['ownedStopAddress'] = stop['registers']['cip']
                            row['hardwareCleanup'] = clear_hw(base + exports['channel_marker'])
                            command = 'con 0' if mode == 'hw-con0' else 'con'
                            assert c.cmd_sync(command)
                            commands.append(command)
                            action_done = True
                        elif event and event['event'] == 'EVENT_PAUSE_DEBUG' and mode.startswith('pause') and row.get('pauseRequested'):
                            row['pauseObserved'] = True
                            row['ownedStopAddress'] = stop['registers']['cip']
                            command = 'con 0' if mode == 'pause-con0' else 'con'
                            assert c.cmd_sync(command)
                            commands.append(command)
                            action_done = True
                        elif event and event['event'] in ('EVENT_SYSTEMBREAKPOINT', 'EVENT_BREAKPOINT'):
                            ep = base + number(binary, number(binary, 60) + 24 + 16)
                            assert event['event'] == 'EVENT_SYSTEMBREAKPOINT' or event['data']['addr'] == ep
                            assert c.cmd_sync('con')
                            commands.append('con')
                        else:
                            row['unclassifiedStop'] = True
                            break
                        floor = len(all_events)
                        status, event = run_checked(pid, commands)
                        continue
                    fixture = state_at(base)
                    row['lastRunningFixture'] = fixture
                    if fixture['phase'] == 20 and (not gate20):
                        assert fixture['avCaught'] == 1 and fixture['cppCaught'] == 1
                        if mode.startswith('pause') and (not row.get('pauseRequested')):
                            assert c.is_debugging() and c.is_running() and (c.eval_sync('$pid') == (pid, True))
                            row['pauseRequested'] = True
                            floor = len(all_events)
                            row['pauseHelperResult'] = c.pause()
                            commands.append('pause')
                            status, event = wait_stop(floor, 3)
                            if status == 'paused':
                                continue
                            row['pauseWaitStatus'] = status
                            if status == 'exited':
                                break
                        if not mode.startswith('pause') or action_done:
                            assert k32.SetEvent(gate)
                            gate20 = True
                    elif fixture['phase'] == 30 and (not gate30):
                        assert k32.SetEvent(gate)
                        gate30 = True
                    status, event = wait_stop(len(all_events), 0.05)
                row.update(nativeExceptionStops=exceptions, hardwareSet=hardware_set, actionDone=action_done, elapsedSeconds=time.monotonic() - started)
                deadline = time.monotonic() + 2
                while time.monotonic() < deadline:
                    kinds = {event['event'] for event in all_events[case_event_floor:]}
                    log = c.get_log(since_index=log_floor)
                    if {'EVENT_EXIT_PROCESS', 'EVENT_STOP_DEBUG'} <= kinds and any((re.search('0x0 \\(0\\)', line) for line in log[1])):
                        break
                    time.sleep(0.02)
                if process:
                    code = C.c_uint32()
                    assert k32.GetExitCodeProcess(process, C.byref(code))
                    row['osExitCode'] = code.value
                    row['osSignaled'] = k32.WaitForSingleObject(process, 0) == 0
                row['debugging'] = c.is_debugging()
                row['running'] = c.is_running() if row['debugging'] else False
                row['events'] = all_events[case_event_floor:]
                row['progress'] = [json.loads(line) for line in report_path.read_text('utf8').splitlines()] if report_path.exists() else []
                row['log'] = c.get_log(since_index=log_floor)
                if row['debugging']:
                    assert c.eval_sync('$pid') == (pid, True)
                    row['leftOwnedTargetForReview'] = True
                results.append(row)
                args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
                print(json.dumps({k: row.get(k) for k in ('name', 'nativeExceptionStops', 'pauseRequested', 'pauseObserved', 'hardwareHit', 'osExitCode', 'osSignaled', 'debugging', 'running', 'unclassifiedStop')}), flush=True)
                if row['debugging']:
                    break
            finally:
                if process:
                    k32.CloseHandle(process)
                k32.CloseHandle(gate)
    except BaseException:
        report['observerError'] = traceback.format_exc()
        report['pendingCase'] = row if 'row' in locals() else None
        report['allEvents'] = all_events
        args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
        raise
    finally:
        report['finalDebugging'] = c.is_debugging()
        report['finalProcesses'] = relevant_processes(process_inventory(), game_root, target)
        args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
        c.detach_session()
    return report

def summarize(report):
    rows = []
    for row in report['results']:
        event_counts = Counter(event['event'] for event in row['events'])
        exceptions = [stop for stop in row['stops']
                      if stop.get('event') and stop['event']['event'] == 'EVENT_EXCEPTION']
        codes = [f"{stop['event']['data']['ExceptionCode']:08x}" for stop in exceptions]
        matched = all(stop['expressions'] == {
            'code': [stop['event']['data']['ExceptionCode'], True],
            'addr': [stop['event']['data']['ExceptionAddress'], True],
            'firstchance': [1, True],
        } and stop['event']['data']['dwFirstChance'] for stop in exceptions)
        exit_codes = [event['data']['dwExitCode'] for event in row['events']
                      if event['event'] == 'EVENT_EXIT_PROCESS']
        hardware = next((stop for stop in row['stops'] if stop.get('event')
                         and stop['event']['event'] == 'EVENT_BREAKPOINT'
                         and stop['event']['data']['name'] == 'cn72_channel_marker'), None)
        last = row['progress'][-1] if row['progress'] else {}
        dr = row['initialRegisters']
        cleanup = row.get('hardwareCleanup')
        debug_regs = ('dr0', 'dr1', 'dr2', 'dr3', 'dr6', 'dr7')
        rows.append(dict(
            name=row['name'],
            freshCreateSystemStop=row.get('matchingCreatedSystemStop', False),
            initialHardwareClean=not row['priorHardware'] and not any(dr[name] for name in debug_regs),
            nativeExceptionStops=row['nativeExceptionStops'],
            exceptionSequence=codes,
            allExceptionsMatchedAndFirstChance=matched,
            ownedTrapReclassified=[dict(code=f"{item['code']:08x}", sameAddress=item['sameAsOwnedStop'])
                                   for item in row.get('forwardedOwnedTrap', [])],
            pauseObserved=row.get('pauseObserved', False),
            pauseHelperResult=row.get('pauseHelperResult'),
            hardwareHit=row.get('hardwareHit', False),
            hardwareSlot=hardware['event']['data']['slot'] if hardware else None,
            hardwareHitAtOwnedAddress=hardware is None or (
                hardware['event']['data']['addr'] == hardware['registers']['cip']
                == cleanup['before']['dr0']),
            hardwareCleared=cleanup is None or not any(cleanup['after'][name] for name in debug_regs),
            hardwareAPICleared=cleanup is None or not any(cleanup['afterAPI'][name] for name in debug_regs),
            hardwareCleanupPreservedCPU=cleanup is None or all(
                cleanup['before'][name] == cleanup['after'][name]
                for name in ('cip', 'rsp', 'rax', 'rcx', 'rdx')),
            finalPhase=last.get('phase'), avCaught=last.get('avCaught'),
            cppCaught=last.get('cppCaught'), markerCalls=last.get('markerCalls'),
            osHandleAvailable=row['processHandleAvailable'], osExitCode=row.get('osExitCode'),
            osSignaled=row.get('osSignaled'), debuggerStopped=not row['debugging'],
            pubExitCodes=exit_codes, pubEventCounts=dict(sorted(event_counts.items())),
            exitZeroLogged=any(re.search(r'0x0 \(0\)', line) for line in row['log'][1]),
            logEvicted=row['log'][3], completed=not row.get('leftOwnedTargetForReview', False),
        ))
    return dict(
        schemaVersion=1, task='D-01b4', fixtureSHA256=report['fixtureSHA256'],
        fixtureSourceSHA256=report['fixtureSourceSHA256'], debuggerFiles=report['debuggerFiles'],
        pythonClientVersion=report['pythonClientVersion'], gameLoaded=False,
        realClientExitCause='unknown', finalDebugging=report['finalDebugging'],
        finalRelevantProcesses=len(report['finalProcesses']), caseCount=len(rows), results=rows,
        controlContract=dict(
            applicationFirstChance='matched EVENT_EXCEPTION and ex.code/addr/firstchance, then con 1 and run',
            ownedDebuggerStop='bare con and run; con 0 is a deliberate negative test',
            readyGate='fresh CREATE_PROCESS and SYSTEMBREAKPOINT plus matching PID/debugging/paused',
            completionGate='owned OS process handle plus native progress, EXIT/STOP PUB and debugger log',
            logCursor='reset to zero on each fresh debug initialization; never reuse the previous target cursor',
        ),
        limitations=[
            'Synthetic target only; no explanation of the game exit is inferred',
            'Negative cases recover the known owned trap with bare con and exit zero; they do not reproduce fatal game exits',
            'Raw timing, PID, addresses, gate names and progress ticks are intentionally omitted',
            'PUB completeness here does not establish that every real-client session is complete',
        ],
    )


def validate_summary(report):
    errors = []
    if report.get('gameLoaded') is not False or report.get('realClientExitCause') != 'unknown' or report.get('caseCount') != 5:
        errors.append('Synthetic debugger scope mismatch')
    if report.get('finalDebugging') is not False or report.get('finalRelevantProcesses') != 0:
        errors.append('Final target state was not empty')
    if report.get('debuggerFiles') != DEBUGGER_FILES or report.get('pythonClientVersion') != '0.9.2':
        errors.append('Debugger provenance mismatch')
    rows = report.get('results', [])
    if [row.get('name') for row in rows] != list(MODES):
        errors.append('Synthetic debugger cases mismatch')
    for row in rows:
        name = row.get('name', '')
        expected_codes = ['c0000005', 'e06d7363']
        extra = '80000003' if name == 'pause-con0' else '80000004' if name == 'hw-con0' else None
        if extra:
            expected_codes.append(extra)
        expected_traps = [dict(code=extra, sameAddress=True)] if extra else []
        if row.get('exceptionSequence') != expected_codes or row.get('ownedTrapReclassified') != expected_traps:
            errors.append('Owned trap/application exception distinction mismatch')
        if row.get('pauseObserved') != name.startswith('pause') or row.get('hardwareHit') != name.startswith('hw'):
            errors.append('Requested debugger action was not actually observed')
        for key in ('freshCreateSystemStop', 'initialHardwareClean', 'allExceptionsMatchedAndFirstChance',
                    'hardwareCleared', 'hardwareAPICleared', 'hardwareHitAtOwnedAddress',
                    'hardwareCleanupPreservedCPU', 'osHandleAvailable', 'osSignaled', 'debuggerStopped', 'exitZeroLogged', 'completed'):
            if row.get(key) is not True:
                errors.append('Debugger evidence missing: ' + key)
        if (row.get('nativeExceptionStops'), row.get('finalPhase'), row.get('avCaught'), row.get('cppCaught'), row.get('markerCalls'), row.get('osExitCode'), row.get('pubExitCodes'), row.get('logEvicted')) != (2, 40, 1, 1, 3, 0, [0], 0):
            errors.append('Native target/OS/PUB/log outcome mismatch')
        if row.get('pauseHelperResult') != (True if name.startswith('pause') else None):
            errors.append('Pause helper result missing')
        if row.get('hardwareSlot') != (0 if name.startswith('hw') else None):
            errors.append('Owned hardware slot mismatch')
        counts = row.get('pubEventCounts', {})
        if any((counts.get(name) != 1 for name in ('EVENT_CREATE_PROCESS', 'EVENT_SYSTEMBREAKPOINT', 'EVENT_EXIT_PROCESS', 'EVENT_STOP_DEBUG'))):
            errors.append('Lifecycle PUB event missing or duplicated')
        if counts.get('EVENT_EXCEPTION') != len(expected_codes):
            errors.append('Exception PUB count mismatch')
    return errors

def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', type=Path, default=root / 'local/migration72/debug-channel/channel_fixture.exe')
    parser.add_argument('--manifest', type=Path, default=root / 'local/migration72/debug-channel/build.json')
    parser.add_argument('--debugger', type=Path, default=root / 'local/runtime/x64dbg-2026.05.27/release/x64/x64dbg.exe')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    args = parser.parse_args()
    if not args.output.resolve().is_relative_to((root / 'local').resolve()):
        parser.error('Raw process, memory and log evidence must stay under ignored local/')
    report = execute(args)
    summary = summarize(json.loads(json.dumps(report)))
    errors = validate_summary(summary)
    if errors:
        raise ValueError(errors)
    args.summary_output.parent.mkdir(parents=True, exist_ok=True)
    args.summary_output.write_text(json.dumps(summary, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(cases=summary['caseCount'], gameLoaded=False, errors=0)))
if __name__ == '__main__':
    main()
