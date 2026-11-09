"""Readout/guard tests for the bounded10339 prefix; no native calls."""
import json
import os
from pathlib import Path
import unittest
from unittest.mock import Mock

from . import client72_string_equals_successor as card


class EqualsPrefixTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = Path(os.environ.get('CLIENT72_STRING_EQUALS_SUCCESSOR_REPORT', card.OUT / 'first.private.json'))
        cls.r = json.loads(cls.path.read_bytes())
        cls.cases = {n['name']: n for n in cls.r['native']}

    def test_source_prefix_keeps_pdata_and_extent_unknown(self):
        r = self.r
        self.assertEqual(r['fixedEXESHA256'], card.prior.EXE_SHA)
        self.assertEqual(r['rawSHA256'], card.prior.RAW_SHA)
        self.assertFalse(r['static']['pdataRecordPresent'])
        self.assertFalse(r['static']['functionExtentKnown'])
        self.assertFalse(r['prefixIsCompleteFunction'])
        self.assertEqual(r['static']['prefixBytes'], 64)
        self.assertEqual(r['static']['reachableBytesUsed'], 48)
        self.assertEqual(r['static']['incompleteTail'], dict(preferred='0x159b6f33e', bytesHex='418d', executed=False))
        self.assertEqual(card.sha(self.path.with_suffix('.source.py').read_bytes()), r['sourceSHA256'])
        for path, digest in r['fixedSourcePins'].items():
            self.assertEqual(card.sha((card.ROOT / path).read_bytes()), digest, path)

    def test_fixed_string_layout_and_target_signature(self):
        static = self.r['static']
        self.assertEqual([(f['name'], f['offset'], f['type']['kind']) for f in static['originalStringFields']],
            [('m_stringLength', 16, 'int32'), ('m_firstChar', 20, 'char')])
        helper = static['targetMethod']
        self.assertEqual((helper['index'], helper['name'], helper['registeredAddress'], helper['flags']),
            (2908, 'EqualsHelper', '0x159aac320', '0x91'))
        self.assertEqual(static['targetSignature']['declaringType'], 293)
        self.assertEqual([p['typeIndex'] for p in static['targetSignature']['parameters']], [33, 33])
        self.assertEqual([p['typeIndex'] for p in static['equalsSignature']['parameters']], [33, 33])
        self.assertEqual(static['parameterType33']['kind'], 'string')
        self.assertEqual(static['returnTypeMetadataStatus'], 'not-recovered')

    def test_null_null_original_returns_low_byte_true(self):
        n = self.cases['null-null']
        self.assertTrue(n['returned'])
        self.assertEqual(n['returnAL'], 1)
        self.assertEqual(n['observations'], 5)
        self.assertEqual(n['executedPreferred'], ['0x159b6f300', '0x159b6f303', '0x159b6f323', '0x159b6f326', '0x159b6f329'])
        self.assertEqual(int(n['registers']['rax'], 16), 0xdeadbeef0000cc01)
        self.assertEqual(n['reads'], [])

    def test_same_owned_pointer_returns_without_any_header_or_field_read(self):
        n = self.cases['same-owned-pointer']
        self.assertTrue(n['returned'])
        self.assertEqual((n['returnAL'], n['observations']), (1, 6))
        self.assertEqual(n['initialRegisters']['rdx'], n['initialRegisters']['r8'])
        self.assertEqual(n['reads'], [])
        self.assertEqual(n['executedPreferred'][-2:], ['0x159b6f32d', '0x159b6f32f'])

    def test_different_owned_lengths_return_false_from_original_branch(self):
        n = self.cases['owned-length-mismatch']
        self.assertTrue(n['returned'])
        self.assertEqual((n['returnAL'], n['observations']), (0, 11))
        self.assertEqual(n['executedPreferred'][-2:], ['0x159b6f32a', '0x159b6f32c'])
        reads = [r for r in self.r['hostBudget']['records'] if r['purpose'] == 'native.owned-field-read'][:2]
        self.assertEqual([int(r['address'], 16) for r in reads], [card.LEFT + 16, card.RIGHT + 16])
        self.assertEqual([int.from_bytes(bytes.fromhex(r['bytesHex']), 'little') for r in reads], [1, 4])

    def test_equal_length_tail_is_stopped_before_original_jmp(self):
        n = self.cases['owned-equal-length-distinct-tail-boundary']
        self.assertFalse(n['returned'])
        self.assertIsNone(n['returnAL'])
        self.assertIsNone(n['failure'])
        self.assertIsNone(n['emulatorError'])
        self.assertEqual((n['observations'], n['completedOriginalInstructions']), (11, 11))
        self.assertEqual(n['preferredStop'], '0x159b6f31e')
        self.assertEqual(n['stopReason'], 'unapproved-tail-transfer-before-jmp')
        self.assertFalse(n['transfer']['executed'])
        self.assertEqual(n['transfer']['preferredTarget'], '0x159aac320')
        self.assertNotIn('0x159b6f31e', n['executedPreferred'])
        self.assertNotIn('0x159aac320', n['executedPreferred'])
        self.assertEqual(n['executedPreferred'][-2:], ['0x159b6f318', '0x159b6f31b'])

    def test_exact_tail_register_abi_and_actual_companion_remain(self):
        n = self.cases['owned-equal-length-distinct-tail-boundary']
        regs = n['transfer']['registers']
        self.assertEqual(regs['rcx'], hex(card.LEFT))
        self.assertEqual(regs['rdx'], hex(card.RIGHT))
        self.assertEqual(regs['r8'], hex(card.RIGHT))
        self.assertEqual(regs['r9'], n['initialRegisters']['r9'])
        self.assertEqual(regs['rsp'], hex(card.prior.prior.STACK + 0x10008))
        self.assertEqual(self.r['static']['tailTransfer']['bytesHex'], 'e9fdcff3ff')

    def test_only_known_owned_length_fields_were_read(self):
        rows = [r for r in self.r['hostBudget']['records'] if r['purpose'] == 'native.owned-field-read']
        self.assertEqual(len(rows), 4)
        self.assertTrue(all(r['width'] == 4 and int(r['address'], 16) in (card.LEFT + 16, card.RIGHT + 16) for r in rows))
        self.assertTrue(all(not r['captureOrdinals'] and r['error'] is None for r in rows))
        self.assertTrue(all(not n['ownedHeaderKnown'] and not n['ownedStringClassProvided'] and n['knownOwnedFieldsOnly'] for n in self.r['native']))

    def test_complete_fresh_input_projection_and_all_final_spans(self):
        self.assertTrue(self.r['inputAudit']['fullTemporalProjectionEqual'])
        self.assertTrue(self.r['rawCapturedReadbackEqual'])
        rows = self.r['hostBudget']['records']
        self.assertEqual(sum(r['purpose'] == 'native.final-readback' for r in rows), 4 * 34)
        self.assertTrue(all(not n['writes'] and n['capturedReadbackEqual'] for n in self.r['native']))
        self.assertEqual(len({n['initialRegisters']['rcx'] for n in self.r['native']}), 1)
        self.assertEqual(len({n['initialRegisters']['r9'] for n in self.r['native']}), 1)

    def test_whole_group_budgets_and_exact_instruction_counts(self):
        self.assertEqual((self.r['hostBudget']['reads'], self.r['hostBudget']['requestedBytes']), (278, 10800))
        self.assertEqual(sum(r['width'] for r in self.r['hostBudget']['records']), 10800)
        self.assertEqual(self.r['callerBudget'], dict(reads=4, requestedBytes=32, maxReads=16, maxBytes=128))
        self.assertEqual(self.r['cpuBudget'], dict(calls=4, observations=33, completedOriginalInstructions=33, perContinuousCall=2000000))
        self.assertTrue(all(0 < r['width'] <= 208 for r in self.r['hostBudget']['records']))

    def test_unknown_owned_class_or_header_read_is_rejected_without_cpu(self):
        engine = card.PrefixNative.__new__(card.PrefixNative)
        engine.owned_known = {card.LEFT + 16: 1}
        engine.failure, engine.missing = None, []
        engine.reg = lambda _: 0x100
        uc = Mock()
        engine.read(uc, 0, card.LEFT, 8, 0, None)
        self.assertEqual(engine.failure, 'unprovided-owned-header-or-field')
        self.assertEqual(engine.missing[0]['address'], hex(card.LEFT))
        uc.emu_stop.assert_called_once()
        uc.mem_read.assert_not_called()

    def test_unapproved_neighbor_and_incomplete_tail_are_rejected_without_cpu(self):
        for preferred in (0x159b6f330, 0x159b6f33e, 0x159b6f340):
            engine = card.PrefixNative.__new__(card.PrefixNative)
            engine.module, engine.allowed, engine.failure = card.prior.PREFERRED, {}, None
            uc = Mock()
            engine.code(uc, preferred, 1, None)
            self.assertEqual(engine.failure, 'unapproved-prefix-instruction')
            uc.emu_stop.assert_called_once()

    def test_no_parent_or_business_promotion_and_no_historical_native_calls(self):
        self.assertEqual(self.r['oldGetterOrAllocatorCalls'], 0)
        for key in ('newPEExtraction', 'naturalBusinessKeys', 'targetHelperExecuted', 'ownedClassOrHeaderInvented',
                'fullStringComparer', 'fullFindEntry', 'naturalCallerObserved', 'configAbilityApproved', 'd02Complete',
                'stockDiskRead', 'clientOrDebuggerStarted'):
            self.assertIs(self.r[key], False, key)
        self.assertEqual(self.r['successModelsSupplied'], [])
        self.assertTrue(all(not n['successModelsSupplied'] for n in self.r['native']))


if __name__ == '__main__':
    unittest.main()
