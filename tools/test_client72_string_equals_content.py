"""Read-only report/source checks and hook negatives; never execute native code."""
import json
from pathlib import Path
import struct
import unittest
from unittest.mock import Mock

from . import client72_string_equals_content as card


class ContentGuards(unittest.TestCase):
    def test_static_source_closure_and_exact_code(self):
        _, windows, rows, pins, static = card.load_sources()
        self.assertEqual([len(w[2]) for w in windows], [48, 177])
        self.assertEqual(len(rows), 19 + 53)
        self.assertEqual(static['helper']['decodedInstructions'], 53)
        self.assertEqual(static['returnTypeMetadataStatus'], 'not-recovered')
        self.assertFalse(static['functionExtentKnown'])
        self.assertFalse(static['pdataRecordPresent'])
        self.assertEqual(len(pins), 54)

    def test_owned_headers_and_unprovided_tail_rejected_before_read(self):
        for address, width in ((card.LEFT, 8), (card.LEFT+8, 8), (card.LEFT+19, 8), (card.LEFT+26, 4)):
            engine = card.ContentNative.__new__(card.ContentNative)
            engine.owned_known = {card.LEFT+16+i: b for i,b in enumerate(card.prefix.owned_fields('A'))}
            engine.failure, engine.missing = None, []
            engine.reg = lambda _: 0x100
            uc = Mock()
            engine.read(uc, 0, address, width, 0, None)
            self.assertEqual(engine.failure, 'unprovided-owned-header-or-field')
            uc.emu_stop.assert_called_once()
            uc.mem_read.assert_not_called()

    def test_neighbors_incomplete_bytes_and_unreviewed_null_calls_rejected(self):
        for address in (0x159b6f330, 0x159b6f33e, 0x159aac3d1, 0x1404c2be0, *card.BLOCKED):
            engine = card.ContentNative.__new__(card.ContentNative)
            engine.module, engine.allowed, engine.failure = card.frozen.PREFERRED, {}, None
            uc = Mock()
            engine.code(uc, address, 1, None)
            self.assertIn(engine.failure, ('unapproved-content-instruction', 'unreviewed-helper-null-callee-or-trap'))
            uc.emu_stop.assert_called_once()

    def test_captured_hole_rejected_without_page_zero_fallback(self):
        engine = card.ContentNative.__new__(card.ContentNative)
        engine.constants, engine.reads, engine.missing = [], [], []
        engine.memory = card.frozen.prior.ExactBytes([])
        engine.failure = None
        engine.reg = lambda _: 0x100
        uc = Mock()
        engine.read(uc, 0, 0x123456780, 8, 0, None)
        self.assertEqual(engine.failure, 'unprovided-captured-byte')
        uc.mem_read.assert_not_called()
        uc.emu_stop.assert_called_once()

    def test_owned_byte_drift_stops_before_native_continue(self):
        engine = card.ContentNative.__new__(card.ContentNative)
        engine.owned_known = {card.LEFT+16+i: b for i,b in enumerate(struct.pack('<i', 1))}
        engine.fixture, engine.owned_reads = [], []
        engine.module = card.frozen.PREFERRED
        engine.memory = card.frozen.prior.ExactBytes([])
        engine.failure = None
        engine.reg = lambda _: 0x100
        uc = Mock()
        uc.mem_read.return_value = bytes(4)
        engine.read(uc, 0, card.LEFT+16, 4, 0, None)
        self.assertEqual(engine.failure, 'owned-field-drift')
        uc.emu_stop.assert_called_once()


class ContentReports(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.paths = [card.OUT/name for name in ('first.private.json', 'repeat.private.json')]
        cls.reports = [json.loads(p.read_bytes()) for p in cls.paths]

    def test_two_formal_groups_byte_identical_source_copies_and_closure(self):
        self.assertEqual(self.paths[0].read_bytes(), self.paths[1].read_bytes())
        for path, r in zip(self.paths, self.reports):
            self.assertEqual(card.sha(path.with_suffix('.source.py').read_bytes()), r['sourceSHA256'])
            self.assertEqual(r['sourceSHA256'], card.sha(Path(card.__file__).read_bytes()))
            for name, digest in r['fixedSourcePins'].items():
                self.assertEqual(card.sha((card.ROOT/name).read_bytes()), digest, name)

    def test_case_ids_distinct_equal_length_fixtures_and_expected_AL(self):
        for r in self.reports:
            self.assertEqual([n['name'] for n in r['native']], [c[0] for c in card.CASES])
            self.assertIsNone(r['firstFailure'])
            for n, (name, left, right, expected) in zip(r['native'], card.CASES):
                self.assertTrue(n['returned'], name)
                self.assertEqual(n['returnAL'], expected, name)
                self.assertIsNone(n['failure'])
                self.assertIsNone(n['emulatorError'])
                self.assertEqual(n['fixture'], card.fixtures(left, right))
                self.assertEqual(n['fixture'][0]['lengthUnits'], n['fixture'][1]['lengthUnits'])
                self.assertNotEqual(n['initialRegisters']['rdx'], n['initialRegisters']['r8'])

    def test_original_entry_tail_helper_caller_and_exact_abi(self):
        for r in self.reports:
            for n in r['native']:
                self.assertEqual(n['executedPreferred'][0], hex(card.prefix.ENTRY))
                self.assertIn(hex(card.prefix.TAIL), n['executedPreferred'])
                self.assertIn(hex(card.prefix.TARGET), n['executedPreferred'])
                self.assertEqual([t['preferred'] for t in n['transitions']], [hex(card.prefix.TAIL), hex(card.prefix.TARGET)])
                tail, helper = [t['registers'] for t in n['transitions']]
                self.assertEqual(tail, helper)
                self.assertEqual(helper['rcx'], hex(card.LEFT))
                self.assertEqual(helper['rdx'], hex(card.RIGHT))
                self.assertEqual(helper['r8'], hex(card.RIGHT))
                self.assertEqual(helper['r9'], n['initialRegisters']['r9'])
                sp = card.frozen.prior.STACK+0x10008
                self.assertEqual(helper['rsp'], hex(sp))
                self.assertEqual(n['registers']['rsp'], hex(sp+8))
                self.assertEqual(n['stop'], hex(card.frozen.prior.RETURN))
                self.assertEqual(n['boundaries'][0]['returnAddress'], n['stop'])
                self.assertEqual(len(n['boundaries']), 1)

    def test_complete_original_instruction_paths_and_no_unreviewed_code(self):
        _, _, rows, _, _ = card.load_sources()
        allowed = {row['va']: row for row in rows}
        for r in self.reports:
            for n in r['native']:
                path = n['originalInstructionPath']
                self.assertEqual([row['va'] for row in path], n['executedPreferred'])
                self.assertEqual(len(path), n['completedOriginalInstructions'])
                self.assertEqual(n['observations'], n['completedOriginalInstructions'])
                self.assertEqual(path[-1]['mnemonic'], 'ret')
                for row in path:
                    self.assertNotIn(int(row['va'],16), card.BLOCKED)
                    self.assertEqual({k: row[k] for k in allowed[row['va']]}, allowed[row['va']])
                self.assertTrue(any(row['rspBefore'] == hex(card.frozen.prior.STACK+0xffe0) for row in path))

    def test_every_owned_read_bytes_field_coverage_and_explicit_terminator(self):
        for r in self.reports:
            count, requested, terminator_cases = 0, 0, []
            for n in r['native']:
                known = {int(s['address'],16)+i: b for s in n['controlledOwnedStorage']
                    for i,b in enumerate(bytes.fromhex(s['bytesHex']))}
                for read in n['ownedReads']:
                    count += 1
                    requested += read['width']
                    address, width = int(read['address'],16), read['width']
                    self.assertEqual(read['bytesHex'], bytes(known[address+i] for i in range(width)).hex())
                    self.assertIsNone(read['error'])
                    self.assertEqual(read['captureOrdinals'], [])
                    for overlap in read['terminatorOverlap']:
                        self.assertEqual(overlap['bytesHex'], '00'*len(overlap['addresses']))
                        self.assertEqual(overlap['source'], 'explicit-owned-terminator')
                terminator_reads = [x for x in n['ownedReads'] if x['terminatorOverlap']]
                if terminator_reads:
                    terminator_cases.append(n['name'])
                    self.assertEqual(len(terminator_reads), 2)
                    self.assertEqual([x['preferredReader'] for x in terminator_reads], ['0x159aac390', '0x159aac392'])
                    self.assertTrue(all(x['width'] == 4 for x in terminator_reads))
                self.assertFalse(n['naturalAllocatorTerminatorVerified'])
                self.assertFalse(n['ownedHeaderKnown'])
                self.assertFalse(n['ownedStringClassProvided'])
            self.assertEqual(count, sum(x['purpose']=='native.owned-field-read' for x in r['hostBudget']['records']))
            self.assertEqual((count, requested), (130, 752))
            self.assertEqual(terminator_cases, ['one-equal', 'one-diff', 'embeddedNul-diff', '13unit-equal', '25unit-diff'])

    def test_all_captured_final_spans_and_same_actual_Default_MI(self):
        for r in self.reports:
            self.assertTrue(r['inputAudit']['fullTemporalProjectionEqual'])
            self.assertTrue(r['rawCapturedReadbackEqual'])
            self.assertEqual(sum(x['purpose']=='native.final-readback' for x in r['hostBudget']['records']), 14*34)
            self.assertEqual(len({n['initialRegisters']['rcx'] for n in r['native']}), 1)
            self.assertEqual(len({n['initialRegisters']['r9'] for n in r['native']}), 1)
            self.assertTrue(all(n['capturedReadbackEqual'] and not n['writes'] for n in r['native']))

    def test_hard_group_and_cumulative_budgets(self):
        self.assertEqual(sum(r['cpuBudget']['calls'] for r in self.reports), 28)
        for r in self.reports:
            cpu, host = r['cpuBudget'], r['hostBudget']
            self.assertEqual(cpu['calls'], 14)
            self.assertEqual(cpu['perContinuousCall'], 2000000)
            self.assertEqual(cpu['developmentNativeCalls'], 0)
            self.assertEqual(cpu['observations'], 651)
            self.assertEqual(cpu['completedOriginalInstructions'], 651)
            self.assertEqual(cpu['observations'], sum(n['observations'] for n in r['native']))
            self.assertEqual(host['reads'], len(host['records']))
            self.assertEqual(host['requestedBytes'], sum(x['width'] for x in host['records']))
            self.assertEqual((host['reads'], host['requestedBytes']), (744, 24976))
            self.assertLessEqual(host['reads'], 4096)
            self.assertLessEqual(host['requestedBytes'], 65536)
            self.assertTrue(all(0 < x['width'] <= 208 for x in host['records']))
            self.assertEqual(r['callerBudget'], dict(reads=14, requestedBytes=112, maxReads=16, maxBytes=128))

    def test_no_full_comparer_business_or_parent_promotion(self):
        for r in self.reports:
            self.assertTrue(r['targetHelperExecuted'])
            self.assertEqual(r['oldGetterOrAllocatorCalls'], 0)
            self.assertEqual(r['successModelsSupplied'], [])
            for key in ('prefixIsCompleteFunction', 'helperMethodInfoInvented', 'inputR8ChangedByHarness',
                'ownedClassOrHeaderInvented', 'naturalAllocatorTerminatorVerified', 'newPEExtraction',
                'fullStringComparer', 'fullFindEntry', 'naturalBusinessKeys', 'naturalCallerObserved',
                'defaultCreationVerified', 'configAbilityApproved', 'd02Complete', 'stockDiskRead',
                'clientOrDebuggerStarted'):
                self.assertIs(r[key], False, key)


if __name__ == '__main__':
    unittest.main()
