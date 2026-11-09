"""Independent source, ABI and native frontier checks for allocator successor."""
import json
import os
from pathlib import Path
import struct
import unittest
from unittest.mock import patch

from . import client72_avatar_allocator_successor as subject


class PrefixSourceTests(unittest.TestCase):
    def setUp(self):
        self.pdata = struct.pack('<III', 0x5336c0, 0x533740, 0x3c01234)
        self.raw = bytes(range(64))

    def test_exact_prefix_retains_raw_bytes_and_pdata(self):
        result = subject.check_prefix(self.raw, self.pdata)
        self.assertEqual(result['bytesHex'], self.raw.hex())
        self.assertEqual(result['pdataBytesHex'], self.pdata.hex())
        self.assertEqual((result['start'], result['end'], result['functionEnd']),
            ('0x1405336c0', '0x140533700', '0x140533740'))
        self.assertFalse(result['completeFunction'])

    def test_short_function_uses_only_its_true_extent(self):
        pdata = struct.pack('<III', 0x5336c0, 0x5336c9, 0x3c01234)
        result = subject.check_prefix(b'\x41' * 9, pdata)
        self.assertTrue(result['completeFunction'])
        with self.assertRaisesRegex(ValueError, 'extent'):
            subject.check_prefix(b'\x41' * 10, pdata)

    def test_short_or_extra_pdata_is_rejected(self):
        for pdata in (self.pdata[:-1], self.pdata + b'\x00'):
            with self.assertRaisesRegex(ValueError, '12B'):
                subject.check_prefix(self.raw, pdata)

    def test_wrong_entry_or_unwind_is_rejected(self):
        for pdata in (struct.pack('<III', 0x5336c1, 0x533740, 1),
                struct.pack('<III', 0x5336c0, 0x5336c0, 1),
                struct.pack('<III', 0x5336c0, 0x533740, 0)):
            with self.assertRaisesRegex(ValueError, 'extent'):
                subject.check_prefix(self.raw, pdata)

    def test_unapproved_prefix_widths_are_rejected(self):
        for raw in (b'', self.raw[:-1], self.raw + b'\x00'):
            with self.assertRaisesRegex(ValueError, 'extent'):
                subject.check_prefix(raw, self.pdata)


class BoundaryABITests(unittest.TestCase):
    def engine(self, pointer, ret):
        engine = subject.AllocatorNative.__new__(subject.AllocatorNative)
        engine.collection = {'listU32': {'pointer': 0x5000135efc8}}
        engine.failure, engine.entry_state = None, None
        engine.blocked_calls, engine.blocked_edge = {}, None
        engine.lookup_state = None
        engine.stops, engine.path = set(), []
        engine.windows = [(subject.ALLOCATOR, subject.ALLOCATOR + 64, '')]
        engine.reg = lambda name: {'rsp': subject.prior.STACK + 0x10000, 'rcx': pointer,
            'rdx': 0, 'r8': 0, 'rax': 0, 'rsi': pointer}[name]
        engine.boundary_read = lambda address, width, purpose: struct.pack('<Q', ret)
        class CPU:
            stopped = False
            def emu_stop(self):
                self.stopped = True
        return engine, CPU()

    def test_actual_class_and_return_accept_only_the_known_caller(self):
        engine, cpu = self.engine(0x5000135efc8, subject.CALLER_RETURN)
        engine.code(cpu, subject.ALLOCATOR, 5, None)
        self.assertEqual(engine.entry_state['caller'], '0x15183fe84')
        self.assertEqual(engine.entry_state['actualReturn'], '0x15183fe89')
        self.assertEqual(engine.path, [subject.ALLOCATOR])
        self.assertFalse(cpu.stopped)

    def test_low32_collision_and_wrong_return_stop_before_prefix(self):
        for pointer, ret in ((0x6000135efc8, subject.CALLER_RETURN),
                (0x5000135efc8, subject.CALLER_RETURN + 1)):
            engine, cpu = self.engine(pointer, ret)
            engine.code(cpu, subject.ALLOCATOR, 5, None)
            self.assertEqual(engine.failure, 'allocator-caller-ABI-differs')
            self.assertEqual(engine.path, [])
            self.assertTrue(cpu.stopped)

    def test_new_direct_call_stops_before_the_call_or_callee_fetch(self):
        engine, cpu = self.engine(1255, subject.CALLER_RETURN)
        engine.blocked_calls = {0x14053371c: dict(target='0x1405269f0', executed=False)}
        engine.code(cpu, 0x14053371c, 5, None)
        self.assertEqual(engine.path, [])
        self.assertEqual(engine.stop_reason, 'unapproved-direct-callee-before-call')
        self.assertEqual(engine.blocked_edge['registers']['rcx'], '0x4e7')
        self.assertTrue(cpu.stopped)

    def test_lookup_caller_uses_ordinary_definition_and_real_return(self):
        engine, cpu = self.engine(1255, 0x140533721)
        engine.windows.append((0x1405269f0, 0x1405269f5, ''))
        engine.code(cpu, 0x1405269f0, 5, None)
        self.assertEqual(engine.lookup_state['rcx'], '0x4e7')
        self.assertEqual(engine.lookup_state['actualReturn'], '0x140533721')
        self.assertFalse(engine.lookup_state['nativeReturnSupplied'])
        self.assertEqual(engine.path, [0x1405269f0])
        self.assertFalse(cpu.stopped)

    def test_closed_class_pointer_or_wrong_lookup_return_is_rejected(self):
        for pointer, ret in ((0x5000135efc8, 0x140533721), (1255, 0x140533722)):
            engine, cpu = self.engine(pointer, ret)
            engine.code(cpu, 0x1405269f0, 5, None)
            self.assertEqual(engine.failure, 'ordinary-lookup-caller-ABI-differs')
            self.assertEqual(engine.path, [])
            self.assertTrue(cpu.stopped)


class FrozenFrontierTests(unittest.TestCase):
    def test_predecessor_acceptance_and_all_inherited_pins(self):
        accepted, loaded = subject.load_frozen()
        self.assertEqual(accepted['sourceSHA256'],
            '7981c1e22609d4ac6c5927354e508cfc02b5aa242e33e0c213ea16883e8993ef')
        self.assertEqual(accepted['native'][0]['executedAddresses'][-1], '0x15183fe84')
        self.assertEqual(loaded[subject.REVIEW]['checkCount'], 17)
        self.assertTrue(accepted['selectedStaticSources']['ordinary10294NotSubstituted'])

    def test_frozen_source_pin_mismatch_is_rejected_without_native(self):
        with patch.dict(subject.PINS, {subject.PREDECESSOR: '0' * 64}):
            with self.assertRaisesRegex(ValueError, 'predecessor source'):
                subject.load_frozen()


class ActualPrefixTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = Path(os.environ.get('CLIENT72_AVATAR_ALLOCATOR_PREFIX_REPORT',
            subject.OUT / 'first.private.json'))
        cls.report = json.loads(cls.path.read_bytes())
        cls.raw = json.loads((subject.ROOT / subject.prior.CAPTURE).read_bytes())['collection']

    def test_report_is_bound_to_its_actual_executed_source(self):
        self.assertEqual(subject.sha(self.path.with_suffix('.source.py').read_bytes()), self.report['sourceSHA256'])
        self.assertEqual(self.report['predecessorReportSHA256'], subject.PINS[subject.PREDECESSOR])
        self.assertTrue(self.report['fullFiveStockGateRun'])
        self.assertEqual(self.report['approvedPrefix']['sourceSHA256'],
            '0da01c5edebbdb477ef39d9166985cc7c10b2bc48dcdcbd94058d7eae66f2fcb')

    def test_original_avatar_caller_and_stack_return_are_real(self):
        report = self.report
        original = json.loads((subject.ROOT / subject.PREDECESSOR).read_bytes())['native'][0]
        self.assertEqual(report['native']['executedAddresses'][:10], original['executedAddresses'])
        self.assertEqual(int(report['allocatorEntry']['rcx'], 16), self.raw['listU32']['pointer'])
        self.assertEqual(report['allocatorEntry']['actualReturn'], '0x15183fe89')
        self.assertEqual(report['boundaryHostBudget']['records'][0]['bytesHex'], '89fe835101000000')

    def test_actual_nonzero_flag_and_original_branch_reach_exact_code_boundary(self):
        report, native = self.report, self.report['native']
        expected = ['0x1405336c0', '0x1405336c1', '0x1405336c2', '0x1405336c3',
            '0x1405336c7', '0x1405336cc', '0x1405336d4', '0x1405336d7', '0x1405336de']
        self.assertEqual(report['allocatorPrefixObservedAddresses'], expected)
        self.assertEqual(native['stop'], '0x140533711')
        self.assertEqual(native['failure'], 'unreviewed-code-edge')
        self.assertIsNone(native['emulatorError'])
        self.assertNotIn(native['stop'], native['executedAddresses'])
        self.assertEqual(report['actualFlag']['valueHex'], '62')
        self.assertEqual(report['actualFlag']['captureOrdinals'], [143, 227])
        for ordinal in report['actualFlag']['captureOrdinals']:
            row = self.raw['records'][ordinal - 1]
            offset = self.raw['listU32']['pointer'] + 0xca - row['address']
            self.assertEqual(bytes.fromhex(row['bytesHex'])[offset], 0x62)
        raw = bytes.fromhex(report['approvedPrefix']['bytesHex'])
        self.assertEqual(raw[0x1e:0x20], b'\x75\x31')
        self.assertEqual(subject.ALLOCATOR + 0x20 + struct.unpack('b', raw[0x1f:0x20])[0], 0x140533711)

    def test_incomplete_tail_and_slow_lock_prefix_were_not_executed(self):
        prefix, native = self.report['approvedPrefix'], self.report['native']
        self.assertEqual(prefix['completeInstructionEnd'], '0x1405336ff')
        self.assertEqual(prefix['partialTrailingBytes']['bytesHex'], 'e8')
        self.assertFalse(prefix['partialTrailingBytes']['decoded'])
        self.assertFalse(prefix['partialTrailingBytes']['targetKnown'])
        self.assertEqual(prefix['functionEnd'], '0x140533864')
        self.assertFalse(prefix['fullExceptionExtentApproval'])
        for address in ('0x1405336e0', '0x1405336eb', '0x1405336f2', '0x1405336ff', '0x140533711'):
            self.assertNotIn(address, native['executedAddresses'])
        readers = [row for row in self.report['hostBudget']['records'] if row['purpose'] == 'native.read']
        self.assertEqual([(row['width'], row['reader']) for row in readers], [(8, '0x15183fe7d'), (1, '0x1405336d7')])
        self.assertNotIn('0x145bc95a8', [row['address'] for row in readers])

    def test_all_original_inputs_read_back_and_cumulative_budgets_hold(self):
        report = self.report
        self.assertTrue(report['rawCapturedSourceUnchanged'])
        self.assertTrue(report['native']['capturedReadbackEqual'])
        self.assertTrue(report['inputAudit']['fullTemporalProjectionEqual'])
        ledger = report['hostBudget']
        self.assertEqual((ledger['reads'], ledger['requestedBytes']), (612, 19497))
        self.assertEqual(sum(row['purpose'] == 'native.final-readback' for row in ledger['records']), 91)
        self.assertEqual((report['boundaryHostBudget']['reads'], report['boundaryHostBudget']['requestedBytes']), (1, 8))
        self.assertEqual((report['cpuBudget']['calls'], report['cpuBudget']['instructions']), (1, 19))
        self.assertLessEqual(ledger['reads'], ledger['maxReads'])
        self.assertLessEqual(ledger['requestedBytes'], ledger['maxBytes'])

    def test_no_fake_object_return_or_parent_promotion(self):
        report = self.report
        self.assertTrue(report['managedAllocatorPrefixExecuted'])
        for key in ('managedAllocationReturned', 'defaultOriginalWriteExecuted', 'completeAvatarConstructor',
                'completeColdClassInitialization', 'completeAvatarHandler', 'g2Verified', 'd02Complete',
                'atomicSnapshot', 'naturalCallerObserved', 'naturalLookupObserved', 'clientOrDebuggerStarted',
                'remoteCallsExecuted', 'priorReportsChanged'):
            self.assertFalse(report[key], key)
        self.assertEqual(report['successModelsSupplied'], [])
        self.assertEqual(report['modelBoundaries']['gcHeapThreadOSModelsAdded'], [])
        self.assertEqual(report['newNativeWindows'], 1)
        self.assertEqual(report['newNativeBytes'], 64)
        self.assertFalse(report['nextInput']['newNaturalInputNeedEstablished'])
        self.assertFalse(report['nextInput']['autoContinuation'])
        self.assertEqual([(row['writer'], row['width'], row['value']) for row in report['native']['writes']],
            [('0x15183fe6c', 4, '0x1'), ('0x15183fe76', 1, '0x1')])


class ActualFullFunctionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = Path(os.environ.get('CLIENT72_AVATAR_ALLOCATOR_FULL_REPORT',
            subject.OUT / 'full-first.private.json'))
        cls.report = json.loads(cls.path.read_bytes())
        cls.capture = json.loads((subject.ROOT / subject.prior.CAPTURE).read_bytes())['collection']

    def test_complete_same_function_source_is_exact_and_old_prefix_is_unchanged(self):
        report = self.report
        self.assertEqual(subject.sha(self.path.with_suffix('.source.py').read_bytes()), report['sourceSHA256'])
        self.assertEqual(report['approvedAllocator']['sha256'],
            'c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2')
        self.assertEqual((report['newNativeBytes'], report['incrementalNativeBytes']), (420, 356))
        self.assertTrue(report['approvedAllocator']['fullExceptionExtentApproval'])
        self.assertFalse(report['approvedAllocator']['calleeFetchAllowed'])
        self.assertEqual(report['previousPrefixReports'][subject.PREFIX_FIRST],
            'da93c41ebd8f074045b002a7b4df17d490ae127e136c82d82ee129ab2c694d2e')
        raw = bytes.fromhex(report['approvedAllocator']['bytesHex'])
        self.assertEqual(raw[:64], bytes.fromhex(report['approvedPrefix']['bytesHex']))
        self.assertEqual(sum(row['width'] for row in report['allocatorInstructions']), 420)

    def test_actual_generic_definition_read_and_first_external_call_boundary(self):
        report, native = self.report, self.report['native']
        self.assertEqual(native['executedAddresses'][-4:],
            ['0x140533711', '0x140533715', '0x140533718', '0x14053371a'])
        self.assertEqual(native['stop'], '0x14053371c')
        self.assertEqual(native['stopReason'], 'unapproved-direct-callee-before-call')
        self.assertIsNone(native['failure'])
        self.assertIsNone(native['emulatorError'])
        self.assertEqual(report['blockedEdge']['target'], '0x1405269f0')
        self.assertEqual(report['blockedEdge']['callerReturn'], '0x140533721')
        self.assertFalse(report['blockedEdge']['executed'])
        self.assertEqual(report['blockedEdge']['registers']['rcx'], '0x4e7')
        reads = [row for row in report['hostBudget']['records'] if row['purpose'] == 'native.read']
        self.assertEqual([(row['reader'], row['width']) for row in reads[-2:]],
            [('0x140533711', 8), ('0x14053371a', 4)])
        self.assertEqual(int.from_bytes(bytes.fromhex(reads[-1]['bytesHex']), 'little'), 1255)
        self.assertEqual(int(reads[-1]['address'], 16), self.capture['listU32']['class']['genericClass'])
        self.assertNotIn('0x1405269f0', native['executedAddresses'])
        self.assertNotIn('0x15a4d6b60', native['executedAddresses'])

    def test_full_phase_keeps_all_budgets_inputs_and_no_success_models(self):
        report = self.report
        self.assertEqual((report['hostBudget']['reads'], report['hostBudget']['requestedBytes']), (614, 19509))
        self.assertEqual((report['boundaryHostBudget']['reads'], report['boundaryHostBudget']['requestedBytes']), (1, 8))
        self.assertEqual((report['cpuBudget']['calls'], report['cpuBudget']['instructions']), (1, 23))
        self.assertTrue(report['rawCapturedSourceUnchanged'])
        self.assertEqual(sum(row['purpose'] == 'native.final-readback' for row in report['hostBudget']['records']), 91)
        self.assertEqual(report['successModelsSupplied'], [])
        self.assertEqual(report['modelBoundaries']['gcHeapThreadOSModelsAdded'], [])
        for key in ('managedAllocationReturned', 'completeAvatarConstructor', 'completeAvatarHandler',
                'completeColdClassInitialization', 'defaultOriginalWriteExecuted', 'g2Verified', 'd02Complete'):
            self.assertFalse(report[key], key)


class ActualLookupSuccessorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = Path(os.environ.get('CLIENT72_AVATAR_ALLOCATOR_SUCCESSOR_REPORT',
            subject.OUT / 'final-first.private.json'))
        cls.report = json.loads(cls.path.read_bytes())
        cls.capture = json.loads((subject.ROOT / subject.prior.CAPTURE).read_bytes())['collection']

    def test_real_continuous_call_trampoline_and_vm_execute_before_missing_read(self):
        report, native = self.report, self.report['native']
        prior_full = json.loads((subject.ROOT / subject.FULL_FIRST).read_bytes())['native']
        self.assertEqual(native['executedAddresses'][:23], prior_full['executedAddresses'])
        self.assertEqual(native['executedAddresses'][23:26],
            ['0x14053371c', '0x1405269f0', '0x15a4d6b60'])
        self.assertEqual(native['executedAddresses'][-1], '0x15a4d6bd4')
        self.assertEqual(report['ordinaryLookupEntry']['rcx'], '0x4e7')
        self.assertEqual(report['ordinaryLookupEntry']['actualReturn'], '0x140533721')
        self.assertEqual(native['stop'], '0x15a4d6bd4')
        self.assertEqual(native['failure'], 'unprovided-captured-byte')
        self.assertEqual(native['emulatorError'], 'Invalid memory read (UC_ERR_READ_UNMAPPED)')
        self.assertEqual(report['cpuBudget']['instructions'], 62)
        self.assertEqual(report['cpuBudget']['completedOriginalInstructions'], 61)
        self.assertFalse(report['cpuBudget']['lastObservedInstructionCompleted'])

    def test_exact_ordinary_class_cache_root_is_missing_and_closed_cached_class_exists(self):
        report, native = self.report, self.report['native']
        self.assertEqual(native['missing'], [dict(address='0x145bc8f18', width=8,
            reader='0x15a4d6bd4', fetch=False)])
        memory = subject.prior.ExactBytes(self.capture['records'])
        self.assertEqual(memory.lookup(self.capture['moduleBase'] + 0x5bc8f18, 8)[:2],
            (b'', 'unprovided-captured-byte'))
        gc = self.capture['listU32']['class']['genericClass']
        raw, error, origins = memory.lookup(gc + 0x18, 8)
        self.assertIsNone(error)
        self.assertEqual(int.from_bytes(raw, 'little'), self.capture['listU32']['pointer'])
        self.assertEqual(origins, [144, 228])
        self.assertIn('Ordinary class-definition cache root', report['nextInput']['exactMissingObject'])

    def test_reused_original_code_only_has_pinned_approval_and_no_old_context(self):
        report = self.report
        self.assertEqual(subject.sha(self.path.with_suffix('.source.py').read_bytes()), report['sourceSHA256'])
        self.assertEqual([(row['start'], row['end']) for row in report['reusedNativeWindows']],
            [('0x1405269f0', '0x1405269f5'), ('0x15a4d6b60', '0x15a4d7671')])
        self.assertEqual(report['reusedNativeSource']['approvalSHA256'],
            '03f2942396db30ec17e776288c8f3c2ed29d3c0e32c7387d84c74f24aeb19080')
        self.assertFalse(report['reusedNativeSource']['oldModelsImported'])
        self.assertFalse(report['reusedNativeSource']['newNaturalInputs'])
        self.assertEqual(report['newNativeWindows'], 1)
        self.assertEqual(report['newNativeBytes'], 420)
        self.assertIsNone(report['blockedEdge'])

    def test_failed_native_read_is_counted_and_all_inputs_finally_read_back(self):
        report = self.report
        ledger = report['hostBudget']
        failures = [row for row in ledger['records'] if row['purpose'] == 'native.invalid-read']
        self.assertEqual(len(failures), 1)
        self.assertEqual((failures[0]['address'], failures[0]['width'], failures[0]['bytesHex']),
            ('0x145bc8f18', 8, ''))
        self.assertEqual((ledger['reads'], ledger['requestedBytes']), (615, 19517))
        self.assertEqual((report['boundaryHostBudget']['reads'], report['boundaryHostBudget']['requestedBytes']), (2, 16))
        self.assertEqual(sum(row['purpose'] == 'native.final-readback' for row in ledger['records']), 91)
        self.assertTrue(report['rawCapturedSourceUnchanged'])
        self.assertTrue(report['native']['capturedReadbackEqual'])

    def test_only_root_minimal_proposal_and_no_gc_os_or_complete_avatar_claim(self):
        report = self.report
        contract = report['nextInput']['minimalProposedInput']
        self.assertEqual((contract['moduleRVA'], contract['width']), ('0x5bc8f18', 8))
        self.assertEqual(contract['withExactFinalRepeat'], dict(reads=2, bytes=16))
        self.assertFalse(contract['producer']['exactTargetFunctionKnown'])
        self.assertTrue(contract['conditional1255SlotNotAuthorized'])
        self.assertFalse(contract['liveExecuted'])
        self.assertFalse(report['nextInput']['liveSamplingAuthorized'])
        self.assertTrue(report['nextInput']['newNaturalInputNeedEstablished'])
        self.assertEqual(report['successModelsSupplied'], [])
        self.assertEqual(report['modelBoundaries']['gcHeapThreadOSModelsAdded'], [])
        for key in ('managedAllocationReturned', 'completeAvatarConstructor', 'completeAvatarHandler',
                'completeColdClassInitialization', 'defaultOriginalWriteExecuted', 'g2Verified', 'd02Complete',
                'naturalCallerObserved', 'naturalLookupObserved', 'atomicSnapshot'):
            self.assertFalse(report[key], key)
        self.assertNotIn('0x140533721', report['native']['executedAddresses'])
        self.assertNotIn('0x140563cd0', report['native']['executedAddresses'])


if __name__ == '__main__':
    unittest.main()
