"""Reject false closure of exported HW-exit evidence; no live debugger tests."""
from copy import deepcopy
import hashlib
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from tools.audit_client72_hw_exit import (
    DR, EXECUTED_OBSERVER_SHA256, SINGLE_STEP, TARGET_RVA, audit, audit_archive_report, verified_bytes,
)


def wer_event(event_id, fields):
    ns = 'http://schemas.microsoft.com/win/2004/08/events/event'
    root = ET.Element('Event', xmlns=ns)
    system = ET.SubElement(root, 'System')
    ET.SubElement(system, 'EventID').text = str(event_id)
    data = ET.SubElement(root, 'EventData')
    for name, value in fields.items():
        ET.SubElement(data, 'Data', Name=name).text = str(value)
    return {'Id': event_id, 'xml': ET.tostring(root, encoding='unicode')}


def fixture():
    base, pid, tid = 0x140000000, 72, 73
    point = base + TARGET_RVA
    def snapshot(cip, hardware=False):
        regs = dict.fromkeys(DR, 0)
        regs.update(cip=cip, rsp=0x9000, eflags=0x244, rax=0, rcx=0, rdx=0)
        if hardware:
            regs.update(dr0=point, dr7=0x401, dr6=0xFFFF0FF0)
        return dict(registers=regs, registerExpressions=dict(regs), tid=tid, selectedTid=tid, selectedPid=pid)
    def event(kind, time, data=None):
        return dict(event=kind, time=time, data=data)
    system = event('EVENT_SYSTEMBREAKPOINT', 1)
    ep = event('EVENT_BREAKPOINT', 2, dict(type=1, addr=base+0x1000))
    data = dict(ExceptionCode=0xC0000005, ExceptionAddress=base+0x2000, ExceptionFlags=0, dwFirstChance=True)
    av = event('EVENT_EXCEPTION', 3, data)
    stops = [dict(event=system, snapshot=snapshot(base+0x800), action='consume-system-stop'),
             dict(event=ep, snapshot=snapshot(base+0x1000), action='consume-main-entrypoint'),
             dict(event=av, snapshot=snapshot(base+0x2000, True), action='deliver-current-application-first-chance',
                  exceptionExpressions=dict(code=[data['ExceptionCode'], True], addr=[data['ExceptionAddress'], True],
                                            flags=[0, True], firstchance=[1, True]))]
    events = [event('EVENT_CREATE_PROCESS', 0, dict(dwProcessId=pid, debugFileName='client.exe'))]
    commands = []
    for index, stop in enumerate(stops, 1):
        events += [stop['event'], event('EVENT_PAUSE_DEBUG', index+.01), event('EVENT_RESUME_DEBUG', index+.2)]
        commands += [dict(time=index+.1, command='con 1' if index == 3 else 'con'), dict(time=index+.2, command='run')]
    events += [event('EVENT_EXIT_PROCESS', 4, dict(dwExitCode=SINGLE_STEP)), event('EVENT_STOP_DEBUG', 4.1)]
    after = snapshot(base+0x1000)
    after['registers'].update(dr0=point, dr7=1)
    after['registerExpressions'] = dict(after['registers'])
    run = dict(sourceSHA256=EXECUTED_OBSERVER_SHA256, mainObservationMode='global-dispatch-hardware', ready=True,
               stopReason='target-exited', originalClientFilesUnchanged=True, logEvicted=0, pubThreadAlive=True,
               osStatus=dict(alive=False, signaled=True, exitCode=SINGLE_STEP), finalState=dict(debugging=False, running=True),
               logExitCodes=['80000004'], logMessages=[], observations=[], pid=pid, base=base, events=events, stops=stops, commands=commands,
               hardwareInstalledAfterEntry=True, codeChecks=[dict(match=True)], hardwareOperations=[dict(
                   action='install', address=point, codeUnchanged=True, before=deepcopy(stops[1]['snapshot']), after=after,
                   tableAfter=[dict(type=2, addr=point, slot=0, typeEx=2, hwSize=0, enabled=True, active=True,
                                   singleshoot=False, fastResume=False, breakCondition='', commandText='')])])
    error = dict(ProcessId='0x48', AppPath='client.exe', ModulePath='client.exe', AppName='client.exe', ModuleName='client.exe',
                 ExceptionCode='80000004', FaultingOffset='112250e0', IntegratorReportId='report-one')
    report = dict(ReportId='report-one', EventName='APPCRASH', P1='client.exe', P4='client.exe', P7='80000004', P8='112250e0')
    return run, [wer_event(1000, error), wer_event(1001, report)]


class HardwareExitAuditTests(unittest.TestCase):
    def test_exit_and_same_fault_rva_still_leave_cause_and_entry_unknown(self):
        run, wer = fixture()
        result = audit(run, wer)
        self.assertEqual(result['rootCause'], 'unknown')
        self.assertFalse(result['rawKernelEventReceiptProven'])
        self.assertFalse(result['naturalEntryOrReturnObserved'])
        self.assertFalse(result['clientAcceptancePassed'])
        self.assertEqual(result['secondsLastRunToPubExit'], .8)

    def test_pid_report_id_fault_offset_and_code_are_all_required(self):
        for old, new in [('0x48', '0x49'), ('report-one', 'report-two'), ('112250e0', '112250e1'), ('80000004', '80000003')]:
            with self.subTest(field=old):
                run, wer = fixture()
                wer[0]['xml'] = wer[0]['xml'].replace(old, new)
                with self.assertRaises(ValueError):
                    audit(run, wer)

    def test_av_continue_and_unclassified_pause_cannot_be_hidden(self):
        run, wer = fixture()
        run['commands'][-2]['command'] = 'con'
        with self.assertRaisesRegex(ValueError, 'Continue'):
            audit(run, wer)
        run, wer = fixture()
        run['events'].insert(-2, dict(event='EVENT_PAUSE_DEBUG', time=3.9, data=None))
        with self.assertRaisesRegex(ValueError, 'Unclassified'):
            audit(run, wer)

    def test_late_register_or_command_state_breaks_the_contract(self):
        for mutation in ('slot', 'command', 'cpu'):
            with self.subTest(mutation=mutation):
                run, wer = fixture()
                if mutation == 'slot':
                    run['stops'][-1]['snapshot']['registers']['dr1'] = 1
                elif mutation == 'command':
                    run['hardwareOperations'][0]['tableAfter'][0]['commandText'] = 'run'
                else:
                    run['hardwareOperations'][0]['after']['registers']['rax'] = 1
                with self.assertRaises(ValueError):
                    audit(run, wer)

    def test_supplied_digest_is_enforced_before_json_parsing(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'input.json'
            path.write_bytes(b'{}')
            digest = hashlib.sha256(b'{}').hexdigest()
            self.assertEqual(verified_bytes(path, digest), b'{}')
            path.write_bytes(b'{"changed":true}')
            with self.assertRaisesRegex(ValueError, 'SHA-256'):
                verified_bytes(path, digest)

    def test_wer_module_presence_is_not_a_pub_load_or_sdk_success(self):
        run, _ = fixture()
        report = '\n'.join(['IntegratorReportIdentifier=report-one', 'AppPath=client.exe', 'EventType=APPCRASH',
                            'IsFatal=1', 'Sig[6].Value=80000004', 'Sig[7].Value=112250e0',
                            'LoadedModule[0]=client.exe', 'LoadedModule[1]=Astrolabe.dll'])
        result = audit_archive_report(report, run, 'report-one')
        self.assertTrue(result['astrolabeInWer'])
        self.assertFalse(result['astrolabeInPub'])
        self.assertEqual(result['werOnlyDllNamesAbsentFromLogs'], ['astrolabe.dll'])
        self.assertFalse(result['sdkAuthenticationVerified'])
        self.assertFalse(result['moduleListIsEventHistory'])
        self.assertEqual(result['startupUiObservation'], 'not-observed')
        with self.assertRaisesRegex(ValueError, 'ID differs'):
            audit_archive_report(report, run, 'other-report')


if __name__ == '__main__':
    unittest.main()
