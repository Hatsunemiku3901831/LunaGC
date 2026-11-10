"""Fixed-source saved Promote selection, projections and rejection checks."""
import copy
import hashlib
import json
import struct
import unittest

from tools import client72_d02_old_promote_fields as subject


class OldPromoteFieldsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.first = (subject.OUT / 'first.json').read_bytes()
        cls.second = (subject.OUT / 'second.json').read_bytes()
        cls.report = json.loads(cls.first)
        cls.group_report = json.loads(subject.groups.read_pinned(subject.GROUP_REPORT, subject.PINS[subject.GROUP_REPORT]))
        cls.original = json.loads(subject.groups.read_pinned(subject.groups.REPORT, subject.PINS[subject.groups.REPORT]))
        cls.blob = subject.groups.read_pinned(subject.frozen.RAW / 'AvatarPromote.cached-m_bytes.bin',
                    subject.frozen.PINS[subject.frozen.RAW / 'AvatarPromote.cached-m_bytes.bin'])
        cls.rows = subject.groups.promote_rows(cls.original, cls.blob)
        cls.targets = subject.targets(cls.group_report, cls.rows)
        old = json.loads(subject.frozen.card.base.read_verified(subject.frozen.equip.OLD_REPORT,
                         subject.frozen.equip.PINS[subject.frozen.equip.OLD_REPORT]))
        cls.enums = {x['typeIndex']: x for x in old['tables'][0]['enumSymbols']}
        cls.java = {x['symbol']: x['value'] for x in cls.enums[32419]['values']}
        cls.anchor = next(r for r in cls.report['rows'] if (r['group'], r['phase']) == (12, 6))

    def test_fresh_reports_are_byte_identical_and_bind_source(self):
        self.assertEqual(self.first, self.second)
        self.assertEqual(self.report['sourceSHA256'], hashlib.sha256(subject.Path(subject.__file__).read_bytes()).hexdigest())
        self.assertTrue(self.report['fiveInputsRecheckedAfterNative'])

    def test_each_target_span_and_composite_key(self):
        actual = self.report['rows']
        self.assertEqual(len(actual), 121)
        self.assertEqual({(r['group'], r['phase']) for r in actual}, set(self.targets))
        for row in actual:
            target = self.targets[row['group'], row['phase']]
            self.assertEqual((row['avatarId'], row['source']), (target['avatarId'], target['source']))
            span = row['source']
            self.assertEqual(hashlib.sha256(self.blob[span['begin']:span['end']]).hexdigest(), span['sha256'])
            self.assertEqual(row['projection']['key'], (row['group'] << 8) + row['phase'])
        self.assertEqual(self.report['payload']['rows'], 889)
        self.assertEqual(self.report['payload']['cursor'], 87706)
        budget = self.report['budget']
        self.assertEqual(budget['combinedHookVisits'], budget['wholeHookVisits'] + budget['selectedReadoutHookVisits'])
        self.assertLessEqual(budget['combinedHookVisits'], 2_000_000)

    def test_anchor_java_contract_and_actual_zero_id_nonzero_count(self):
        p = self.anchor['projection']
        self.assertTrue(self.report['anchor12_6MatchesOriginalFields'])
        self.assertEqual(p['addPropBits'], ['454be5f3', '434cc000', '427edf70', '3e75c28f'])
        self.assertEqual([x['propType'] for x in p['javaFields']['addProps']],
                         ['FIGHT_PROP_BASE_HP', 'FIGHT_PROP_BASE_DEFENSE', 'FIGHT_PROP_BASE_ATTACK', 'FIGHT_PROP_ATTACK_PERCENT'])
        self.assertEqual(p['javaFields']['costItems'], [{'id': 104104, 'count': 6}, {'id': 0, 'count': 20},
                         {'id': 100024, 'count': 60}, {'id': 112007, 'count': 24}])
        self.assertFalse(p['materialReferencesVerified'])
        self.assertEqual(p['serializedFieldPresence'], 'unknown')
        self.assertEqual(p['omittedNotAssertedAsDefaults'], ['scoinCost', 'unlockMaxLevel', 'requiredPlayerLevel'])

    def test_all_projected_values_and_order_follow_native_fields(self):
        self.assertTrue(any(any(v != '00000000' for v in r['projection']['addPropBits']) for r in self.report['rows']))
        for row in self.report['rows']:
            self.assertEqual(subject.project(row, self.enums, self.java), row['projection'])
            values = [struct.pack('<f', p['value']).hex() for p in row['projection']['javaFields']['addProps']]
            self.assertEqual(values, [struct.pack('<I', int(b, 16)).hex() for b in row['projection']['addPropBits']])

    def test_unknown_enum_and_missing_fields_rejected(self):
        row = copy.deepcopy(self.anchor)
        props = next(f['value'] for f in row['fields'] if f['offset'] == '0x10')
        props[0][0]['value']['value'] = 0x7fffffff
        with self.assertRaisesRegex(ValueError, 'Unknown or nonmatching'):
            subject.project(row, self.enums, self.java)
        row = copy.deepcopy(self.anchor)
        row['fields'].pop()
        with self.assertRaisesRegex(ValueError, 'Missing or unexpected'):
            subject.project(row, self.enums, self.java)
        row = copy.deepcopy(self.anchor)
        next(f['value'] for f in row['fields'] if f['offset'] == '0x18')[0].pop()
        with self.assertRaisesRegex(ValueError, 'Missing material'):
            subject.project(row, self.enums, self.java)

    def test_float_bits_and_java_enum_mismatch_rejected(self):
        row = copy.deepcopy(self.anchor)
        next(f['value'] for f in row['fields'] if f['offset'] == '0x10')[0][1]['value']['bits'] = '00000000'
        with self.assertRaisesRegex(ValueError, 'float32 bits'):
            subject.project(row, self.enums, self.java)
        with self.assertRaisesRegex(ValueError, 'Unknown or nonmatching'):
            subject.project(self.anchor, self.enums, self.java | {'FIGHT_PROP_BASE_HP': 999})

    def test_target_drift_and_duplicate_rejected(self):
        report = copy.deepcopy(self.group_report)
        report['avatars'][0]['promoteSource']['end'] += 1
        with self.assertRaisesRegex(ValueError, 'span mismatch'):
            subject.targets(report, self.rows)
        report = copy.deepcopy(self.group_report)
        report['avatars'][0] = copy.deepcopy(report['avatars'][1])
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            subject.targets(report, self.rows)


if __name__ == '__main__':
    unittest.main()
