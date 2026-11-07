"""Offline correlation of a single-HW startup exit, WER and fixed source files.

This reads exported files only. It cannot connect to a debugger, continue a
target, or turn a WER fault address into an observed function call.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PureWindowsPath
import re
import xml.etree.ElementTree as ET


TARGET_RVA = 0x112250E0
SINGLE_STEP = 0x80000004
EXECUTED_OBSERVER_SHA256 = 'd7ca80bbd896628e6e852fef58bc585eb2a1a2e137dabee06e51563f01681d8b'
DR = ('dr0', 'dr1', 'dr2', 'dr3', 'dr6', 'dr7')
SOURCE_HASHES = {
    'TitanEngine.Debugger.DebugLoop.cpp': '7259dbe598ee47c427acbc1febd6ece02a17058104fdeb69d7526a2a820bdc4e',
    'debugger.cpp': '7197d4430edcafad1c55b7d4decbcd637654c2a5d6f19de4b155e54259fa67a1',
    'plugin.cpp': 'af35ff088ad62469bee53190bda371c95aac0a8e1ece4e03773cd48ea77afbc0',
    'xauto_server.h': '54de849e8731012160b7ba0d8745eaf903d0fd7d44fbeeecf70c8939c3b7a72f',
}


def main():
    args = arguments()
    run = json.loads(verified_bytes(args.run, args.run_sha256).decode('utf-8-sig'))
    wer = json.loads(verified_bytes(args.wer, args.wer_sha256).decode('utf-8-sig'))
    sources = {name: hashlib.sha256(verified_bytes(args.source_dir / name, digest)).hexdigest()
               for name, digest in SOURCE_HASHES.items()}
    result = audit(run, wer)
    result['inputSHA256'] = {'run': args.run_sha256, 'wer': args.wer_sha256}
    if args.archive_report:
        report = verified_bytes(args.archive_report, args.archive_report_sha256).decode('utf-16')
        result['archiveReport'] = audit_archive_report(report, run, result['wer']['integratorReportId'])
        result['inputSHA256']['archiveReport'] = args.archive_report_sha256
    result['sourceSHA256'] = sources
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'auditComplete': True, 'rootCause': result['rootCause'],
                      'stops': result['stopCount'], 'exitCode': result['exitCode']}))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--run-sha256', required=True)
    parser.add_argument('--wer', type=Path, required=True)
    parser.add_argument('--wer-sha256', required=True)
    parser.add_argument('--source-dir', type=Path, required=True)
    parser.add_argument('--archive-report', type=Path)
    parser.add_argument('--archive-report-sha256')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if bool(args.archive_report) != bool(args.archive_report_sha256):
        parser.error('Archive report and its SHA-256 must be supplied together.')
    local = Path(__file__).resolve().parents[1] / 'local'
    if not args.output.resolve().is_relative_to(local):
        parser.error('Output must remain under the checkout local directory.')
    return args


def verified_bytes(path, expected):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f'Input SHA-256 mismatch: {path.name}')
    return data


def require(condition, message):
    if not condition:
        raise ValueError(message)


def event_data(row):
    root = ET.fromstring(row['xml'])
    ns = {'e': 'http://schemas.microsoft.com/win/2004/08/events/event'}
    require(int(root.find('e:System/e:EventID', ns).text) == row['Id'], 'WER event ID mismatch')
    return {item.attrib['Name']: item.text or '' for item in root.findall('e:EventData/e:Data', ns)}


def correlate_wer(rows, pid, process_path):
    errors = [event_data(row) for row in rows if row['Id'] == 1000]
    matches = [row for row in errors if int(row['ProcessId'], 0) == pid
               and row['AppPath'].casefold() == process_path.casefold()]
    require(len(matches) == 1, 'Need exactly one WER1000 for this PID and path')
    error = matches[0]
    require(error['AppPath'].casefold() == error['ModulePath'].casefold(), 'WER fault module differs')
    require(int(error['ExceptionCode'], 16) == SINGLE_STEP, 'WER exception differs from exit')
    require(int(error['FaultingOffset'], 16) == TARGET_RVA, 'WER fault RVA differs from the owned HW point')
    reports = [event_data(row) for row in rows if row['Id'] == 1001]
    reports = [row for row in reports if row['ReportId'] == error['IntegratorReportId']]
    require(len(reports) == 1, 'Need exactly one correlated WER1001 report')
    report = reports[0]
    require(report['EventName'] == 'APPCRASH', 'WER1001 is not APPCRASH')
    require(report['P1'].casefold() == error['AppName'].casefold()
            and report['P4'].casefold() == error['ModuleName'].casefold(), 'WER report names differ')
    require(int(report['P7'], 16) == SINGLE_STEP and int(report['P8'], 16) == TARGET_RVA,
            'WER report code or offset differs')
    return {'pidAndPathMatch': True, 'reportIdMatch': True, 'integratorReportId': error['IntegratorReportId'],
            'faultModuleIsProcess': True, 'code': '80000004', 'faultRva': '112250E0'}


def audit_archive_report(text, run, report_id):
    entries = [line.split('=', 1) for line in text.splitlines() if '=' in line]
    fields = dict(entries)
    require(len(fields) == len(entries), 'Duplicate archive report keys')
    require(fields['IntegratorReportIdentifier'] == report_id, 'Archive report ID differs')
    process_path = next(row['data']['debugFileName'] for row in run['events'] if row['event'] == 'EVENT_CREATE_PROCESS')
    require(fields['AppPath'].casefold() == process_path.casefold(), 'Archive application path differs')
    require(fields['EventType'] == 'APPCRASH' and fields['IsFatal'] == '1', 'Archive is not the fatal APPCRASH')
    require(int(fields['Sig[6].Value'], 16) == SINGLE_STEP
            and int(fields['Sig[7].Value'], 16) == TARGET_RVA, 'Archive exception signature differs')
    indexed_modules = [(int(match[1]), value) for key, value in fields.items()
                       if (match := re.fullmatch(r'LoadedModule\[(\d+)\]', key))]
    require(sorted(index for index, _ in indexed_modules) == list(range(len(indexed_modules))),
            'Archive module index gap')
    names = {PureWindowsPath(value).name.casefold() for _, value in indexed_modules}
    observed = {row['data']['modname'].casefold() for row in run['events'] if row['event'] == 'EVENT_LOAD_DLL'}
    missing_dlls = sorted(name for name in names - observed if name.endswith('.dll'))
    logs = ''.join(run['logMessages']).casefold()
    return {
        'sameReportAndPath': True, 'moduleRecordCount': len(indexed_modules), 'uniqueModuleCount': len(names),
        'pubUniqueDllCount': len(observed), 'moduleNameIntersectionCount': len(names & observed),
        'werOnlyModuleNames': sorted(names - observed), 'pubOnlyModuleNames': sorted(observed - names),
        'werOnlyDllCount': len(missing_dlls),
        'werOnlyDllNamesAbsentFromLogs': [name for name in missing_dlls if name not in logs],
        'astrolabeInWer': 'astrolabe.dll' in names, 'astrolabeInPub': 'astrolabe.dll' in observed,
        'accountPlatNativeInWer': 'accountplatnative.dll' in names,
        'faultThreadOrContextFields': [key for key in fields if any(word in key.casefold() for word in ('thread', 'context', 'dump'))],
        'moduleListIsEventHistory': False, 'sdkAuthenticationVerified': False,
        'startupUiObservation': 'not-observed',
    }


def audit(run, wer):
    require(run['sourceSHA256'] == EXECUTED_OBSERVER_SHA256, 'Observer source version differs')
    require(run['mainObservationMode'] == 'global-dispatch-hardware', 'Wrong observation mode')
    require(run['ready'] and run['stopReason'] == 'target-exited', 'Not a completed target-exit observation')
    require('observerError' not in run and not run.get('observerPauseRequested'), 'Observer error or budget pause')
    require(run['originalClientFilesUnchanged'] and run['logEvicted'] == 0 and run['pubThreadAlive'],
            'File or collection invariant failed')
    require(run['osStatus'] == {'alive': False, 'signaled': True, 'exitCode': SINGLE_STEP}, 'OS exit differs')
    require(run['finalState']['debugging'] is False, 'Debugger still owns a target')
    require(run['logExitCodes'] == ['80000004'], 'Exit log differs')
    require(not run['observations'], 'This audit concerns an exit without natural entry/return evidence')
    events, stops = run['events'], run['stops']
    counts = Counter(row['event'] for row in events)
    creates = [row for row in events if row['event'] == 'EVENT_CREATE_PROCESS']
    exits = [row for row in events if row['event'] == 'EVENT_EXIT_PROCESS']
    require(len(creates) == len(exits) == counts['EVENT_STOP_DEBUG'] == 1, 'Create/exit/stop closure missing')
    require(creates[0]['data']['dwProcessId'] == run['pid'], 'CREATE PID differs')
    require(exits[0]['data']['dwExitCode'] == SINGLE_STEP, 'PUB exit differs')
    require(counts['EVENT_PAUSE_DEBUG'] == counts['EVENT_RESUME_DEBUG'] == len(stops), 'Unclassified pause/resume exists')
    stop_events = [row for row in events if row['event'] in
                   ('EVENT_SYSTEMBREAKPOINT', 'EVENT_BREAKPOINT', 'EVENT_EXCEPTION')]
    require(stop_events == [row['event'] for row in stops], 'Stop ledger does not cover the published stops in order')
    require(len(run['commands']) == 2 * len(stops), 'Unexpected command count')
    point = run['base'] + TARGET_RVA
    operations = run['hardwareOperations']
    require(len(operations) == 1 and operations[0]['action'] == 'install', 'HW install/clear history differs')
    op = operations[0]
    require(op['address'] == point and op['codeUnchanged'], 'HW target or original bytes differ')
    before, after = op['before']['registers'], op['after']['registers']
    require(all(before[k] == after[k] for k in before if k not in DR + ('eflags',))
            and before['eflags'] ^ after['eflags'] in (0, 2), 'HW installation changed CPU execution state')
    require(not any(before[k] for k in DR), 'HW installation did not start clean')
    require(after['dr0'] == point and after['dr7'] in (1, 0x401)
            and not any(after[k] for k in ('dr1', 'dr2', 'dr3', 'dr6')), 'HW installation CPU result differs')
    table = op['tableAfter']
    require(len(table) == 1, 'Not exactly one installed HW point')
    bp = table[0]
    require((bp['type'], bp['addr'], bp['slot'], bp['typeEx'], bp['hwSize']) == (2, point, 0, 2, 0)
            and bp['enabled'] and bp['active'] and not bp['singleshoot'] and not bp['fastResume']
            and not bp['breakCondition'] and not bp['commandText'], 'HW breakpoint contract differs')
    entry_seen = False
    for index, stop in enumerate(stops):
        snap, event, action = stop['snapshot'], stop['event'], stop['action']
        regs = snap['registers']
        require(all(regs[k] == v for k, v in snap['registerExpressions'].items()), 'Register APIs disagree')
        require(snap['selectedPid'] == run['pid'] and snap['selectedTid'] == snap['tid'], 'Selected thread identity differs')
        require(not any(regs[k] for k in ('dr1', 'dr2', 'dr3')), 'Another HW slot is present')
        require(regs['dr0'] == (point if entry_seen else 0)
                and regs['dr7'] in ((1, 0x401) if entry_seen else (0,))
                and regs['dr6'] in ((0, 0xFFFF0FF0) if entry_seen else (0,)), 'Observed DR contract differs')
        if action == 'consume-main-entrypoint':
            require(not entry_seen and snap == op['before'] and event['event'] == 'EVENT_BREAKPOINT'
                    and event['data']['type'] == 1, 'HW install was not at the first EP')
            entry_seen = True
        elif action == 'deliver-current-application-first-chance':
            data = event['data']
            require(event['event'] == 'EVENT_EXCEPTION' and data['ExceptionCode'] == 0xC0000005
                    and data['dwFirstChance'], 'Unverified application exception')
            require(stop['exceptionExpressions'] == {
                'code': [data['ExceptionCode'], True], 'addr': [data['ExceptionAddress'], True],
                'firstchance': [1, True], 'flags': [data['ExceptionFlags'], True]}, 'Exception API identity differs')
        elif action == 'consume-verified-tls-callback':
            require(event['event'] == 'EVENT_BREAKPOINT' and event['data']['type'] == 1 and event['data']['singleshoot']
                    and event['data']['addr'] in stop['verifiedTlsAddresses'], 'TLS source missing')
        else:
            require(action == 'consume-system-stop' and event['event'] == 'EVENT_SYSTEMBREAKPOINT', 'Unknown stop action')
        commands = run['commands'][2 * index:2 * index + 2]
        expected = 'con 1' if action == 'deliver-current-application-first-chance' else 'con'
        require([row['command'] for row in commands] == [expected, 'run'], 'Continue does not match the stop')
        require(event['time'] <= commands[0]['time'] <= commands[1]['time'] <= exits[0]['time'], 'Command/event chronology differs')
    require(entry_seen and run['hardwareInstalledAfterEntry'], 'No EP installation evidence')
    require(all(row['match'] for row in run['codeChecks']), 'Code window mismatch')
    correlated = correlate_wer(wer, run['pid'], creates[0]['data']['debugFileName'])
    return {
        'schemaVersion': 1, 'task': 'D02-R2-HW-EXIT', 'auditComplete': True,
        'stopCount': len(stops), 'stopActions': dict(Counter(row['action'] for row in stops)),
        'eventCounts': dict(counts), 'observedThreadCount': len({row['snapshot']['tid'] for row in stops}),
        'exitCode': '80000004', 'wer': correlated,
        'secondsLastRunToPubExit': round(exits[0]['time'] - run['commands'][-1]['time'], 3),
        'hardwareInstallCount': 1, 'hardwareHighLevelHits': 0, 'publishedFinalExceptions': 0,
        'unclassifiedPublishedPauses': 0, 'naturalEntryOrReturnObserved': False,
        'rawKernelEventReceiptProven': False, 'faultThreadContextAvailable': False,
        'threadCreationSubscriptionPresent': False,
        'rootCause': 'unknown', 'clientAcceptancePassed': False,
    }


if __name__ == '__main__':
    main()
