"""Natural main-EXE software entry/return after optional AV/C++ in an owned fixture.

Uses the same event classifier, DR guard, return association, naked con/con1
policy and three exit diagnostics as the original-client observer. Only the
preexisting, hash-bound synthetic executable is loaded. No game is started.
"""
import argparse
import ctypes as C
from importlib.metadata import version
import hashlib
import json
from pathlib import Path
import subprocess
import time
import traceback
import uuid

from check_debug_channel72 import DEBUGGER_FILES, process_inventory, relevant_processes
from extract_client72 import PE, number
from observe_client72_startup import DR_NAMES, STOP_EVENTS, TERMINATORS, exit_log_codes, matching_returns, register_action, stop_action


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mode', choices=('no-exceptions', 'exceptions'), required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--prior', type=Path, help='Exact previous owned synthetic report proving persisted exit-point ownership.')
    args = p.parse_args()
    root = Path(__file__).resolve().parents[1]
    args.output = args.output.resolve()
    if not args.output.is_relative_to(root/'local') or args.output.exists():
        p.error('Choose a fresh output under local/.')
    if args.prior and not args.prior.resolve().is_relative_to(root/'local'):
        p.error('Prior evidence belongs under local/.')
    execute(root, args)


def execute(root, args):
    from x64dbg_automate import X64DbgClient
    from x64dbg_automate.events import EventType
    from x64dbg_automate.models import BreakpointType
    manifest = json.loads((root/'local/migration72/debug-channel/build.json').read_text('utf-8-sig'))
    target = Path(manifest['target']).resolve()
    assert target == (root/'local/migration72/debug-channel/channel_fixture.exe').resolve()
    binary = target.read_bytes()
    assert hashlib.sha256(binary).hexdigest() == manifest['targetSHA256'] == '54b68323ec091af156788d08cb36d412f3d812cd6b596cc9c21978766f5196b7'
    assert hashlib.sha256((root/manifest['source']).read_bytes()).hexdigest() == manifest['sourceSHA256']
    prior = json.loads(args.prior.read_text('utf8')) if args.prior else None
    if prior:
        assert prior['fixtureSHA256'] == manifest['targetSHA256'] and prior['osStatus']['exitCode'] == 0 and not prior['osStatus']['alive']
    pe = PE(binary)
    optional = number(binary, 0x3c)+24
    directory = pe.offset(pe.base+number(binary, optional+112))
    functions = pe.offset(pe.base+number(binary, directory+28))
    names = pe.offset(pe.base+number(binary, directory+32))
    ordinals = pe.offset(pe.base+number(binary, directory+36))
    exports = {}
    for i in range(number(binary, directory+24)):
        offset = pe.offset(pe.base+number(binary, names+i*4))
        name = binary[offset:offset+256].split(b'\0')[0].decode('ascii')
        exports[name] = number(binary, functions+number(binary, ordinals+i*2, 'H')*4)
    assert {'channel_marker', 'g_fixture_state', 'fixture_av', 'fixture_cpp'} <= exports.keys()
    game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(root/'tools/game_path.ps1')], text=True).strip()).resolve()
    assert not relevant_processes(process_inventory(), game, target)
    debugger = (root/'local/runtime/x64dbg-2026.05.27/release/x64/x64dbg.exe').resolve()
    assert {n: hashlib.sha256((debugger.parent/n).read_bytes()).hexdigest() for n in DEBUGGER_FILES} == DEBUGGER_FILES
    assert version('x64dbg_automate') == '0.9.2'
    sessions = [s for s in X64DbgClient.list_sessions() if s.host in ('localhost', '127.0.0.1')]
    assert len(sessions) == 1 and Path(sessions[0].cmdline[0]).resolve() == debugger
    k = C.WinDLL('kernel32', use_last_error=True)
    k.CreateEventW.argtypes = [C.c_void_p, C.c_int, C.c_int, C.c_wchar_p]; k.CreateEventW.restype = C.c_void_p
    k.SetEvent.argtypes = [C.c_void_p]; k.CloseHandle.argtypes = [C.c_void_p]
    k.OpenProcess.argtypes = [C.c_uint32, C.c_int, C.c_uint32]; k.OpenProcess.restype = C.c_void_p
    k.WaitForSingleObject.argtypes = [C.c_void_p, C.c_uint32]; k.WaitForSingleObject.restype = C.c_uint32
    k.GetExitCodeProcess.argtypes = [C.c_void_p, C.POINTER(C.c_uint32)]
    k.QueryFullProcessImageNameW.argtypes = [C.c_void_p, C.c_uint32, C.c_wchar_p, C.POINTER(C.c_uint32)]
    gate_name = 'Local\\CN72SoftwareFixture_'+uuid.uuid4().hex
    gate = k.CreateEventW(None, True, False, gate_name); assert gate
    progress = args.output.with_suffix('.progress.jsonl')
    report = dict(task='D-01b5f synthetic software breakpoint', mode=args.mode, gameLoaded=False,
                  fixtureSHA256=manifest['targetSHA256'], fixtureSourceSHA256=manifest['sourceSHA256'],
                  sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  sharedObserverSHA256=hashlib.sha256((root/'tools/observe_client72_startup.py').read_bytes()).hexdigest(),
                  events=[], stops=[], observations=[], commands=[], logMessages=[], logEvicted=0,
                  gateSignals=[], processReport=str(progress), observationBudgetSeconds=30)
    c = X64DbgClient(str(debugger)); c.attach_session(sessions[0].pid)
    process, pid, base, cursor = None, None, None, 0
    points, pending, expected_main, started = {}, [], {}, time.monotonic()

    def capture(event):
        report['events'].append(dict(time=time.monotonic(), event=str(event.event_type), data=event.event_data.model_dump(mode='json') if event.event_data else None))
    for kind in (EventType.EVENT_CREATE_PROCESS, EventType.EVENT_SYSTEMBREAKPOINT, EventType.EVENT_BREAKPOINT,
                 EventType.EVENT_EXCEPTION, EventType.EVENT_PAUSE_DEBUG, EventType.EVENT_RESUME_DEBUG,
                 EventType.EVENT_LOAD_DLL, EventType.EVENT_EXIT_PROCESS, EventType.EVENT_STOP_DEBUG):
        c.watch_debug_event(kind, capture)

    def os_status():
        if not process:
            return None
        code = C.c_uint32(); assert k.GetExitCodeProcess(process, C.byref(code))
        wait = k.WaitForSingleObject(process, 0); assert wait in (0, 258)
        return dict(alive=wait == 258, exitCode=code.value, signaled=wait == 0)

    def pull_log():
        nonlocal cursor
        cursor, lines, remaining, evicted = c.get_log(cursor); assert remaining == 0
        report['logMessages'].extend(lines); report['logEvicted'] += evicted

    def save():
        report.update(elapsedSeconds=round(time.monotonic()-started, 3), osStatus=os_status(),
                      finalState=dict(debugging=c.is_debugging(), running=c.is_running()),
                      pubThreadAlive=c.sub_thread.is_alive(), ownedPointsRemaining={str(a):n for a,n in points.items()})
        args.output.write_text(json.dumps(report, indent=2)+'\n', 'utf8')

    def command(value):
        report['commands'].append(dict(time=time.monotonic(), command=value)); assert c.cmd_sync(value)

    def snapshot():
        ctx = c.get_regs().context
        regs = {n:getattr(ctx,n) for n in ('rax','rcx','rdx','r8','r9','rsp','eflags')+DR_NAMES}
        regs['cip'] = ctx.rip
        tid, ok = c.eval_sync('tid()'); assert ok
        return dict(registers=regs, tid=tid, registerExpressions={n:c.get_reg(n) for n in ('cip','rsp','eflags')+DR_NAMES})

    def fixture_state():
        data = c.read_memory(base+exports['g_fixture_state'], 24)
        values = [int.from_bytes(data[i:i+4], 'little') for i in range(0, 24, 4)]
        assert values[0] == 0x72324442
        return dict(zip(('magic','phase','avCaught','cppCaught','markerCalls','ticks'), values))

    def point(address, name):
        assert not any(p.addr == address for p in c.get_breakpoints(BreakpointType.BpNormal))
        assert c.set_breakpoint(address, name); points[address] = name

    def remove(address):
        found = [p for p in c.get_breakpoints(BreakpointType.BpNormal) if p.addr == address]
        assert len(found) == 1 and found[0].name == points[address]
        assert c.clear_breakpoint(address); del points[address]
        if address in expected_main:
            assert c.read_memory(address,16) == expected_main[address]
            report.setdefault('restoredMainCodeWindows', []).append(dict(address=address, sha256=hashlib.sha256(expected_main[address]).hexdigest()))

    def wait_event(floor, deadline):
        stopped_since = None
        while time.monotonic() < deadline:
            if not c.is_debugging() or not os_status()['alive']:
                return None
            if not c.is_running():
                found = [e for e in report['events'][floor:] if e['event'] in STOP_EVENTS]
                specific = [e for e in found if e['event'] != 'EVENT_PAUSE_DEBUG']
                if specific:
                    return specific[-1]
                stopped_since = stopped_since or time.monotonic()
                if time.monotonic()-stopped_since > 1:
                    return found[-1] if found else None
            else:
                stopped_since = None
                if len(report['gateSignals']) < 2:
                    state = fixture_state(); report['latestFixtureState'] = state
                    phase = state['phase']
                    if phase in (20,30) and phase not in report['gateSignals']:
                        assert k.SetEvent(gate); report['gateSignals'].append(phase)
            time.sleep(0.02)
        return None

    try:
        assert c.get_debugger_pid() == sessions[0].pid and c.debugger_is_elevated() and not c.is_debugging()
        time.sleep(0.6)
        report['loadReturned'] = c.load_executable(str(target), cmdline=f'"{gate_name}" "{progress}" "{args.mode}"', current_dir=str(target.parent))
        ready = time.monotonic()+10
        while time.monotonic() < ready:
            creates = [e for e in report['events'] if e['event'] == 'EVENT_CREATE_PROCESS']
            systems = [e for e in report['events'] if e['event'] == 'EVENT_SYSTEMBREAKPOINT']
            if creates and systems and c.is_debugging() and not c.is_running():
                creation = creates[-1]['data']; pid = creation['dwProcessId']
                assert Path(creation['debugFileName']).resolve() == target and c.eval_sync('$pid') == (pid,True)
                process = k.OpenProcess(0x101000, False, pid); assert process
                path = C.create_unicode_buffer(32768); size = C.c_uint32(32768)
                assert k.QueryFullProcessImageNameW(process, 0, path, C.byref(size)) and Path(path.value).resolve() == target
                assert os_status()['alive']; report['ready'] = True; report['pid'] = pid
                break
            time.sleep(0.04)
        else:
            raise ValueError('Missing new synthetic ready events/OS identity')
        base, ok = c.eval_sync('mod.main()'); assert ok; report['base'] = base
        ep = base+number(binary, optional+16); marker = base+exports['channel_marker']
        expected_main[marker] = binary[pe.offset(pe.base+exports['channel_marker'], 16):][:16]
        report['initialSnapshot'] = snapshot(); assert register_action(report['initialSnapshot']) is None
        for kind in (BreakpointType.BpHardware, BreakpointType.BpMemory, BreakpointType.BpException):
            assert not c.get_breakpoints(kind), 'Non-software inherited point needs independent ownership review'
        initial = c.get_breakpoints(BreakpointType.BpNormal)
        known = {}
        if prior:
            report['priorEvidenceSHA256'] = hashlib.sha256(args.prior.read_bytes()).hexdigest()
            for symbol in TERMINATORS:
                name = 'cn72_sw_fixture_exit_'+symbol.replace('.', '_')
                assert name in prior['ownedPointsRemaining'].values()
                address, ok = c.eval_sync(symbol); assert ok; known[name] = address
        for bp in initial:
            if bp.singleshoot and bp.addr == ep:
                continue
            assert known.get(bp.name) == bp.addr and not bp.breakCondition and not bp.commandText and not bp.fastResume, 'Inherited software point needs ownership review'
            report.setdefault('removedPriorOwnedPoints', []).append(bp.model_dump(mode='json'))
            assert c.clear_breakpoint(bp.addr)
        assert c.read_memory(marker, 16) == expected_main[marker]
        point(marker, 'cn72_sw_fixture_marker')
        for symbol in TERMINATORS:
            address, ok = c.eval_sync(symbol); assert ok
            point(address, 'cn72_sw_fixture_exit_'+symbol.replace('.', '_'))
        report['initialOwnedPoints'] = {str(a):n for a,n in points.items()}
        deadline = time.monotonic()+30; event = systems[-1]; report['stopReason'] = 'budget'
        while time.monotonic() < deadline and c.is_debugging() and os_status()['alive']:
            assert not c.is_running() and c.eval_sync('$pid') == (pid,True)
            row = dict(event=event, snapshot=snapshot(), fixture=fixture_state()); report['stops'].append(row)
            report['latestFixtureState'] = row['fixture']
            problem = register_action(row['snapshot'])
            if problem:
                report['stopReason'] = problem; break
            expressions = {}
            if event and event['event'] == 'EVENT_EXCEPTION':
                expressions = {n:list(c.eval_sync('ex.'+n+'()')) for n in ('code','addr','firstchance')}
                row['exceptionExpressions'] = expressions
            action = stop_action(event, expressions, row['snapshot']['registers']['cip'], points, ep, set())
            row['action'] = action
            if action.startswith('stop-'):
                report['stopReason'] = action; break
            if action == 'observe-owned-point':
                snap = row['snapshot']; regs = snap['registers']; address = regs['cip']; name = points[address]
                if name == 'cn72_sw_fixture_marker':
                    assert regs['rcx'] == 1 and row['fixture']['markerCalls'] == 0
                    ret = c.read_qword(regs['rsp']); original = ret-base+pe.base
                    expected_main[ret] = binary[pe.offset(original,16,executable=True):][:16]
                    assert c.read_memory(ret,16) == expected_main[ret]
                    pending.append(dict(address=ret, expectedRsp=regs['rsp']+8, tid=snap['tid']))
                    report['observations'].append(dict(kind='marker-entry', **snap, fixture=row['fixture']))
                    remove(marker); point(ret, 'cn72_sw_fixture_return')
                elif name == 'cn72_sw_fixture_return':
                    assert matching_returns(pending,snap) == pending and len(pending) == 1
                    assert row['fixture']['markerCalls'] == 1
                    report['observations'].append(dict(kind='marker-return', **snap, fixture=row['fixture']))
                    pending.clear(); remove(address)
                else:
                    assert name.startswith('cn72_sw_fixture_exit_')
                    exit_code = regs['rcx'] if name.endswith('RtlExitUserProcess') else regs['rdx']
                    assert exit_code & 0xffffffff == 0 and row['fixture']['phase'] == 40
                    report['observations'].append(dict(kind='expected-normal-exit-api', name=name, **snap))
            pull_log(); save()
            command('con 1' if action == 'deliver-current-application-first-chance' else 'con')
            floor = len(report['events']); command('run'); event = wait_event(floor, deadline)
            if not c.is_debugging() or not os_status()['alive']:
                report['stopReason'] = 'fixture-exited'; break
            if c.is_running():
                break
        if c.is_debugging() and c.is_running() and os_status()['alive']:
            floor = len(report['events']); report['pauseReturned'] = c.pause()
            report['budgetPauseEvent'] = wait_event(floor, time.monotonic()+3)
            assert report['budgetPauseEvent'] and report['budgetPauseEvent']['event'] == 'EVENT_PAUSE_DEBUG'
        if c.is_debugging() and not c.is_running() and os_status()['alive']:
            report['finalSnapshot'] = snapshot()
            for address in list(points):
                remove(address)
        drain = time.monotonic()+3
        while time.monotonic() < drain and (c.is_debugging() or not any(e['event']=='EVENT_EXIT_PROCESS' for e in report['events']) or not any(e['event']=='EVENT_STOP_DEBUG' for e in report['events'])):
            time.sleep(0.04)
        time.sleep(0.2); pull_log(); save()
        report['exitEvents'] = [e['data']['dwExitCode'] for e in report['events'] if e['event'] == 'EVENT_EXIT_PROCESS']
        report['logExitCodes'] = exit_log_codes(report['logMessages'])
        report['progressLines'] = [json.loads(s) for s in progress.read_text().splitlines()]
        expected_caught = 0 if args.mode == 'no-exceptions' else 1
        state = report['progressLines'][-1]
        assert state['phase'] == 40 and state['markerCalls'] == 3 and state['avCaught'] == state['cppCaught'] == expected_caught
        assert report['osStatus']['exitCode'] == 0 and report['exitEvents'] == [0] and report['logExitCodes'][-1] == '0'
        assert [r['kind'] for r in report['observations'][:2]] == ['marker-entry','marker-return']
        assert not c.is_debugging() and report['logEvicted'] == 0 and report['pubThreadAlive']
        report['passed'] = True; save()
    except BaseException:
        report['error'] = traceback.format_exc()
        if c.is_debugging() and c.is_running() and process and os_status()['alive']:
            report['errorPauseReturned'] = c.pause()
        pull_log(); save(); raise
    finally:
        if process:
            k.CloseHandle(process)
        k.CloseHandle(gate); c.detach_session()
    print(json.dumps(dict(mode=args.mode,passed=report['passed'],stops=len(report['stops']),
                          observations=[r['kind'] for r in report['observations']],finalFixture=state,
                          osExitCode=report['osStatus']['exitCode'],pubExitCodes=report['exitEvents'],logExitCodes=report['logExitCodes'])))


if __name__ == '__main__':
    main()
