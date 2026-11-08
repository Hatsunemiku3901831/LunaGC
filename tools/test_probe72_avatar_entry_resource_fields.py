"""Independent actual-row, native-path and strict birth-input checks."""
import copy
import hashlib
import json
import os
from pathlib import Path
import struct
import unittest

from tools import probe72_avatar_entry_resource_fields as subject


class AvatarEntryResourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        old = json.loads(subject.prior.card.base.read_verified(subject.prior.EQUIPMENT_REPORT,
            subject.prior.PINS[subject.prior.EQUIPMENT_REPORT]))
        promote = json.loads(subject.prior.card.base.read_verified(subject.PROMOTE_REPORT,
            subject.PINS[subject.PROMOTE_REPORT.relative_to(subject.ROOT).as_posix()]))
        cls.avatars = next(t for t in old['tables'] if t['name'] == 'Avatar')
        cls.curves = next(t for t in promote['tables'] if t['name'] == 'AvatarCurve')
        cls.enums = {t['typeIndex']: t for t in promote['enumSymbols']}
        cls.java = {v['symbol']: v['value'] for v in cls.enums[32419]['values']}
        report = Path(os.environ.get('CLIENT72_AVATAR_ENTRY_REPORT', str(subject.RAW / 'first.json')))
        cls.report = json.loads(report.read_bytes())

    def avatar(self, key=10000005):
        row = copy.deepcopy(next(x for x in self.avatars['selected'] if x['key'] == key))
        return row

    def fields(self, row):
        return {f['offset']: f['value'] for f in row['fields']}

    def project(self, row, curves=None, enums=None, java=None):
        return subject.project_avatar(row, enums or self.enums, java or self.java, curves or self.curves)

    def test_birth_fields_preserve_float_bits_and_binding_order(self):
        for key in (10000005, 10000007):
            result = self.project(self.avatar(key))
            self.assertEqual(result['floatBits'], dict(hpBase='4463f2a0', attackBase='418e76c9', defenseBase='4264e666'))
            self.assertEqual(result['javaFields']['propGrowCurves'], [
                dict(type='FIGHT_PROP_BASE_HP', growCurve='GROW_CURVE_HP_S4'),
                dict(type='FIGHT_PROP_BASE_ATTACK', growCurve='GROW_CURVE_ATTACK_S4'),
                dict(type='FIGHT_PROP_BASE_DEFENSE', growCurve='GROW_CURVE_HP_S4')])
            self.assertEqual(struct.pack('<f', result['javaFields']['hpBase']).hex(), 'a0f26344')
            self.assertTrue(result['fragmentOnly'])

    def test_independent_avatar136_uses_different_base_and_s5(self):
        result = self.project(self.avatar(10000136))
        self.assertEqual(result['floatBits'], dict(hpBase='446269db', attackBase='41a3db23', defenseBase='42767fe6'))
        self.assertEqual([b['growCurve'] for b in result['javaFields']['propGrowCurves']],
            ['GROW_CURVE_HP_S5', 'GROW_CURVE_ATTACK_S5', 'GROW_CURVE_HP_S5'])

    def test_critical_values_are_retained_raw_but_not_aliased(self):
        row = self.avatar()
        self.assertEqual(self.fields(row)['0x98']['bits'], '3d4ccccd')
        self.assertEqual(self.fields(row)['0xa4']['bits'], '3f000000')
        result = self.project(row)
        self.assertFalse(result['criticalAliasesApproved'])
        self.assertNotIn('critical', result['javaFields'])
        self.assertNotIn('criticalHurt', result['javaFields'])
        self.assertEqual(result['omittedUnknownFields'], ['critical', 'criticalHurt'])

    def test_missing_base_value_is_not_defaulted(self):
        row = self.avatar()
        row['fields'] = [f for f in row['fields'] if f['offset'] != '0x64']
        with self.assertRaises(KeyError):
            self.project(row)

    def test_float_recorded_bits_mismatch_is_rejected(self):
        row = self.avatar()
        self.fields(row)['0x64']['bits'] = '4463f2a1'
        with self.assertRaisesRegex(ValueError, 'original bits'):
            self.project(row)

    def test_nonfinite_base_value_is_rejected(self):
        row = self.avatar()
        self.fields(row)['0x64'].update(bits='7f800000', float32=float('inf'))
        with self.assertRaisesRegex(ValueError, 'finite'):
            self.project(row)

    def test_null_empty_and_missing_growth_binding_are_rejected(self):
        for value in (None, []):
            row = self.avatar()
            next(f for f in row['fields'] if f['offset'] == '0x48')['value'] = value
            with self.assertRaises(ValueError):
                self.project(row)
        row = self.avatar()
        self.fields(row)['0x48'].pop()
        with self.assertRaisesRegex(ValueError, 'Missing actual base'):
            self.project(row)

    def test_duplicate_and_unapproved_property_bindings_are_rejected(self):
        row = self.avatar()
        bindings = self.fields(row)['0x48']
        bindings.append(copy.deepcopy(bindings[0]))
        with self.assertRaisesRegex(ValueError, 'one actual binding'):
            self.project(row)
        row = self.avatar()
        self.fields(row)['0x48'][0][0]['value']['value'] = 20
        with self.assertRaisesRegex(ValueError, 'approved base'):
            self.project(row)

    def test_unknown_curve_name_and_java_property_value_mismatch_are_rejected(self):
        row = self.avatar()
        self.fields(row)['0x48'][0][1]['value']['value'] = 0xfffffffe
        with self.assertRaisesRegex(ValueError, 'Unknown or nonmatching'):
            self.project(row)
        with self.assertRaisesRegex(ValueError, 'Unknown or nonmatching'):
            self.project(self.avatar(), java=self.java | {'FIGHT_PROP_BASE_HP': 999})

    def test_missing_curve_nonmulti_and_duplicate_curve_are_rejected(self):
        for edit in ('missing', 'nonmulti', 'duplicate'):
            table = copy.deepcopy(self.curves)
            infos = next(f['value'] for f in table['rows'][19]['fields'] if f['offset'] == '0x10')
            if edit == 'missing':
                infos.pop(0)
            elif edit == 'nonmulti':
                infos[0][1]['value']['value'] = 1
            else:
                infos.append(copy.deepcopy(infos[0]))
            with self.assertRaises(ValueError):
                self.project(self.avatar(), curves=table)

    def test_high_resource_id_is_rejected_and_real_uint64_hash_is_preserved(self):
        for key in (0x80000000, 0x8000000100000001):
            row = self.avatar()
            row['key'] = key
            with self.assertRaisesRegex(ValueError, 'Java int'):
                self.project(row)
        source = self.fields(self.avatar())['0x28']
        self.assertEqual(source['bits'], 'adfb90ae5bdec0ac')
        self.assertEqual(int(source['decimal']), 0xadfb90ae5bdec0ac)
        self.assertGreater(int(source['decimal']), 1 << 63)

    def test_formal_report_source_identity_and_false_completion_states(self):
        report = self.report
        self.assertEqual(report['sourceSHA256'], hashlib.sha256(Path(subject.__file__).read_bytes()).hexdigest())
        self.assertEqual(report['testSourceSHA256'], hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        for flag in ('completeAvatarHandler', 'completeActorIdCodec', 'completeCaller', 'fullTableParsingNewlyExecuted',
                'completeAvatarInputRow', 'completeItemInputRow', 'javaResourcesLoaded', 'resourceReady', 'activeRevisionVerified', 'd02Complete'):
            self.assertIs(report[flag], False, flag)
        self.assertIsNone(report['activeSelectedRevision'])
        self.assertTrue(report['fiveInputsRecheckedAfterNative'])

    def test_original_rows_are_exact_frozen_fields_and_spans(self):
        rows = self.report['selectedRows']
        self.assertEqual([(r['table'], r['key']) for r in rows],
            [('Avatar', k) for k in (10000005, 10000007, 10000136)] + [('AvatarCurve', k) for k in (1, 20, 100)])
        for row in rows:
            old = next(x for x in (self.avatars['selected'] if row['table'] == 'Avatar' else self.curves['rows']) if x['key'] == row['key'])
            self.assertEqual(row['fields'], old['fields'])
            self.assertEqual(row['source'], old['source'])
            self.assertEqual(row['parser']['stop'], hex(subject.RETURN))
            self.assertFalse(row['wholeTableNewlyExecuted'])

    def test_all_27_complete_helpers_use_raw_base_and_independent_float32(self):
        cases = self.report['consumers']['completeHelper']
        self.assertEqual(len(cases), 27)
        offsets = {1: '0x64', 4: '0x9c', 7: '0x5c'}
        for case in cases:
            fields = self.fields(self.avatar(case['avatarId']))
            base = fields[offsets[case['property']]]['float32']
            curve = next(x for x in self.curves['rows'] if x['key'] == case['level'])
            entries = next(f['value'] for f in curve['fields'] if f['offset'] == '0x10')
            factor = next(i[2]['value']['float32'] for i in entries if i[0]['value']['value'] == case['growthType'])
            expected = struct.pack('<f', base * factor).hex()
            self.assertEqual(struct.pack('<I', int(case['resultBits'], 16)).hex(), expected)
            self.assertEqual(case['trace']['stop'], hex(subject.RETURN))
            self.assertIn('0x153a7f940', case['trace']['executedAddresses'])
            self.assertIn('0x14f905140', case['trace']['executedAddresses'])
            self.assertTrue(case['completeNativeFunction'])

    def test_27_real_caller_slices_execute_native_lookup_and_complete_helper(self):
        cases = self.report['consumers']['callerSlices']
        self.assertEqual(len(cases), 27)
        for case in cases:
            self.assertEqual(case['trace']['entry'], '0x153a7f621')
            self.assertEqual(case['trace']['stop'], '0x153a7f6f6')
            self.assertTrue(case['selectedAvatarSamePointer'])
            self.assertIn('0x1578b66f0', case['trace']['executedAddresses'])
            self.assertIn('0x153a7f940', case['trace']['executedAddresses'])
            self.assertFalse(case['completeCaller'])
            self.assertFalse(case['liveIn']['actorIdCodecExecuted'])

    def test_native_negatives_keep_missing_and_error_boundaries(self):
        cases = {c['name']: c for c in self.report['negativeConsumers']}
        self.assertEqual(len(cases), 12)
        self.assertEqual(cases['null-growth-array']['trace']['stop'], '0x1404c2be0')
        self.assertIsNone(cases['null-growth-array']['returnedBits'])
        for name, case in cases.items():
            if name != 'null-growth-array':
                self.assertEqual(case['returnedBits'], '00000000')
        self.assertFalse(cases['caller-missing-level']['convertedToJavaDefault'])
        self.assertFalse(cases['caller-missing-avatar']['convertedToJavaDefault'])

    def test_native_budget_includes_readout_indexes_and_negative_calls(self):
        totals = self.report['nativeCallTotals']
        self.assertEqual(sum(x['instructionHookVisits'] for x in totals.values()), self.report['nativeCallInstructionHookVisits'])
        self.assertGreater(self.report['nativeCallInstructionHookVisits'], 37960)
        self.assertLess(self.report['nativeCallInstructionHookVisits'], 2_000_000)
        self.assertEqual(totals['0x153a7f940']['calls'], 37)
        self.assertEqual(totals['0x153a7f621']['calls'], 29)
        for index in self.report['nativeIndexes']:
            self.assertTrue(index['selectedStorageFixture'])
            self.assertEqual(index['declaredCapacity'], 3)
            self.assertTrue(all(q['samePointer'] for q in index['queries']))

    def test_static_typed_contract_and_critical_unknown_are_not_overstated(self):
        helper, caller = self.report['staticSources']
        self.assertEqual([p.get('typeDefinition') for p in helper['parameters']], [58272, 68066, 32419])
        self.assertEqual(helper['start'], '0x153a7f940')
        self.assertEqual(caller['start'], '0x153a7f560')
        critical = self.report['criticalInvestigation']
        self.assertFalse(critical['aliasApproved'])
        self.assertFalse(critical['nativeCriticalConsumerExecuted'])
        self.assertTrue(critical['candidateFilterIsNotExhaustiveProof'])
        self.assertEqual(critical['directFieldA4ThenFloatTailCandidates'], 0)


if __name__ == '__main__':
    unittest.main()
