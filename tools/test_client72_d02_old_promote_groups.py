"""Pinned saved-avatar joins, source spans, missing keys and drift checks."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tools import client72_d02_old_promote_groups as subject


class OldPromoteGroupsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.snapshot = json.loads(subject.read_pinned(subject.SNAPSHOT, subject.PINS[subject.SNAPSHOT]))
        cls.report = json.loads(subject.read_pinned(subject.REPORT, subject.PINS[subject.REPORT]))
        cls.blob = subject.read_pinned(subject.frozen.RAW / 'AvatarPromote.cached-m_bytes.bin',
                                       subject.frozen.PINS[subject.frozen.RAW / 'AvatarPromote.cached-m_bytes.bin'])
        cls.saved = subject.saved_avatars(cls.snapshot)
        cls.rows = subject.promote_rows(cls.report, cls.blob)

    def test_saved_121_and_traveler_phase(self):
        self.assertEqual(len(self.saved), 121)
        self.assertEqual(self.saved[10000007], 6)
        self.assertEqual(self.rows[12, 6], subject.ANCHOR)
        a = subject.ANCHOR
        self.assertEqual(hashlib.sha256(self.blob[a['begin']:a['end']]).hexdigest(), a['sha256'])

    def test_all_report_spans_are_independently_contiguous(self):
        spans = sorted(self.rows.values(), key=lambda x: x['begin'])
        self.assertEqual(len(spans), 889)
        self.assertEqual(spans[0]['begin'], 4)
        self.assertEqual(spans[-1]['end'], len(self.blob))
        for a, b in zip(spans, spans[1:]):
            self.assertEqual(a['end'], b['begin'])
        for span in spans:
            self.assertEqual(hashlib.sha256(self.blob[span['begin']:span['end']]).hexdigest(), span['sha256'])

    def test_missing_key_is_explicit(self):
        groups = {1: {'group': 12, 'source': {}, 'trace': {}}}
        output = subject.join({1: 255}, groups, self.rows)
        self.assertFalse(output[0]['promoteKeyFound'])
        self.assertIsNone(output[0]['promoteSource'])
        self.assertEqual(output[0]['savedPhase'], 255)

    def test_unmatched_avatar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'do not match'):
            subject.join({1: 6}, {}, self.rows)

    def test_foreign_duplicate_missing_and_bad_phase_rejected(self):
        for mutation in ('foreign', 'duplicate', 'missing', 'phase'):
            with self.subTest(mutation=mutation):
                snapshot = copy.deepcopy(self.snapshot)
                row = snapshot['avatars'][0]
                if mutation == 'foreign': row['fields']['ownerId'] = -1
                elif mutation == 'duplicate': row['fields']['avatarId'] = snapshot['avatars'][1]['fields']['avatarId']
                elif mutation == 'missing': row['missing'].append('promoteLevel')
                else: row['fields']['promoteLevel'] = 256
                with self.assertRaises(ValueError): subject.saved_avatars(snapshot)

    def test_source_span_or_payload_drift_rejected(self):
        table = next(t for t in self.report['tables'] if t['name'] == 'AvatarPromote')
        drift = copy.deepcopy(self.report)
        next(t for t in drift['tables'] if t['name'] == 'AvatarPromote')['rows'][0]['source']['end'] += 1
        with self.assertRaisesRegex(ValueError, 'source span'): subject.promote_rows(drift, self.blob)
        with self.assertRaisesRegex(ValueError, 'identity'): subject.promote_rows(self.report, self.blob + b'x')
        self.assertEqual(table['count'], 889)

    def test_duplicate_promote_key_rejected(self):
        report = copy.deepcopy(self.report)
        rows = next(t for t in report['tables'] if t['name'] == 'AvatarPromote')['rows']
        rows[1]['group'], rows[1]['phase'] = rows[0]['group'], rows[0]['phase']
        with self.assertRaisesRegex(ValueError, 'duplicate key'): subject.promote_rows(report, self.blob)

    def test_changed_pinned_source_rejected(self):
        with tempfile.TemporaryDirectory(dir=subject.OUT) as tmp:
            p = Path(tmp) / 'input'; p.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'Pinned input changed'):
                subject.read_pinned(p, hashlib.sha256(b'original').hexdigest())

    def test_actual_final_report_independent_join_and_budget(self):
        path = subject.OUT / 'third.json'
        report = json.loads(path.read_bytes())
        cases = report['avatars']
        self.assertEqual({r['avatarId'] for r in cases}, set(self.saved))
        self.assertEqual(len(cases), 121)
        for row in cases:
            self.assertEqual(row['savedPhase'], self.saved[row['avatarId']])
            self.assertEqual(row['promoteSource'], self.rows.get((row['actualGroup'], row['savedPhase'])))
            self.assertEqual(row['promoteKeyFound'], row['promoteSource'] is not None)
            self.assertEqual(row['groupTrace']['stop'], '0x15184ae6f')
            for address in ('0x15184ae4f', '0x15184ae59', '0x15184ae68', '0x15184ae6d'):
                self.assertEqual(row['groupTrace']['executedAddresses'].get(address), 1)
        self.assertEqual(report['missingKeys'], sum(not r['promoteKeyFound'] for r in cases))
        budget = report['budget']
        self.assertEqual(budget['combinedHookVisits'], budget['parserAndSelectedReadoutHookVisits'] + budget['groupSlicesHookVisits'])
        self.assertLessEqual(budget['combinedHookVisits'], budget['limit'])
        self.assertEqual(report['avatarPayload']['rows'], 169)
        self.assertEqual(report['avatarPayload']['cursor'], report['avatarPayload']['bytes'])

    def test_two_fresh_outputs_are_identical_and_bind_final_source(self):
        first = (subject.OUT / 'third.json').read_bytes()
        second = (subject.OUT / 'fourth.json').read_bytes()
        self.assertEqual(first, second)
        self.assertEqual(json.loads(first)['sourceSHA256'], hashlib.sha256(Path(subject.__file__).read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
