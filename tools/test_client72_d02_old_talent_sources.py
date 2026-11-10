import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import client72_d02_old_talent_sources as subject
from tools.audit_resource72 import git_hash, index_table


class TalentSourcesTest(unittest.TestCase):
    def test_key_validation(self):
        def encode(keys):
            return json.dumps({'currentDepotTalentLookupKeys': keys}).encode()
        self.assertEqual(subject.lookup_keys(encode([0, 2]), 2), [0, 2])
        for keys in ([0], [0, 0], [2, 1], [False, 2], [-1, 2], [0, 0x80000000]):
            with self.subTest(keys=keys), self.assertRaises(ValueError):
                subject.lookup_keys(encode(keys), 2)

    def test_missing_null_empty_and_nonempty(self):
        self.assertEqual(subject.config(None)['state'], 'missing-row')
        self.assertEqual(subject.config({}), {'state': 'null', 'fieldPresent': False, 'value': None})
        self.assertEqual(subject.config({'openConfig': None}), {'state': 'null', 'fieldPresent': True, 'value': None})
        self.assertEqual(subject.config({'openConfig': ''})['state'], 'empty')
        self.assertEqual(subject.config({'openConfig': ' '})['state'], 'nonempty')
        for value in (0, [], True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                subject.config({'openConfig': value})

    def test_duplicate_and_missing_ids_or_json_fields_rejected(self):
        for raw in (b'[{"talentId":1},{"talentId":1}]', b'[{}]',
                    b'[{"talentId":1,"openConfig":"x","openConfig":"y"}]'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                index_table(raw, 'talentId')

    def test_distinct_targets_missing_rows_and_duplicate_sources(self):
        hits = {'A': [{'path': 'one.json'}, {'path': 'two.json'}], 'B': [{'path': 'three.json'}], '': [{'path': 'empty.json'}]}
        rows = subject.compare([1, 2, 3, 4],
                               {1: {'openConfig': 'A'}, 2: {}, 3: {'openConfig': ''}},
                               {1: {'openConfig': 'B'}, 2: {'openConfig': None}, 3: {'openConfig': ''}, 4: {'openConfig': 'C'}}, hits)
        self.assertTrue(rows[0]['javaValueDiffers'])
        self.assertEqual(rows[0]['candidate']['sourceCount'], 2)
        self.assertTrue(rows[0]['candidate']['multipleSources'])
        self.assertEqual(rows[0]['checkout']['binOutputHits'], [{'path': 'three.json'}])
        self.assertFalse(rows[1]['javaValueDiffers'])
        self.assertFalse(rows[2]['javaValueDiffers'])
        self.assertEqual(rows[2]['checkout']['binOutputHits'], [{'path': 'empty.json'}])
        self.assertEqual(rows[3]['candidate']['state'], 'missing-row')
        self.assertEqual(rows[3]['checkout']['sourceCount'], 0)

    def test_scan_nested_and_repeated_key_without_winner(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / 'nested').mkdir()
            a = b'{"key": []}'
            b = b'{"key": [{"$type":"x"}]}'
            (directory / 'a.json').write_bytes(a)
            (directory / 'nested' / 'b.json').write_bytes(b)
            tracked = {'a.json': {'blob': git_hash('blob', a)}, 'missing.json': {'blob': '0' * 40}}
            sources, hits, missing = subject.scan_bin(directory, tracked)
            self.assertEqual([x['path'] for x in hits['key']], ['a.json', 'nested/b.json'])
            self.assertTrue(sources[0]['directOpenConfigDirectory'])
            self.assertFalse(sources[1]['directOpenConfigDirectory'])
            self.assertTrue(sources[0]['matchesGitlinkBytes'])
            self.assertEqual(missing, ['missing.json'])
            (directory / 'duplicate.json').write_text('{"x": [], "x": []}')
            with self.assertRaises(ValueError):
                subject.scan_bin(directory, tracked)

    def test_pin_mismatch_rejected(self):
        with patch.object(subject, 'PINS', {'keys': (subject.PINS['keys'][0], '0' * 64)}):
            with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
                subject.pinned_inputs()

    def test_fixed_inputs_and_independent_cross_check(self):
        data, tracked, proof = subject.pinned_inputs()
        result = subject.make_report(data, tracked, proof)
        candidate = index_table(data['candidate'], 'talentId')
        checkout = index_table(data['checkout'], 'talentId')
        keys = subject.lookup_keys(data['keys'])
        self.assertEqual(len(result['rows']), 726)
        self.assertEqual([r['talentId'] for r in result['rows']], keys)
        for row in result['rows']:
            key = row['talentId']
            for name, table in (('candidate', candidate), ('checkout', checkout)):
                self.assertIn(key, table)
                self.assertEqual(row[name].get('value'), table[key].get('openConfig'))
                for hit in row[name]['binOutputHits']:
                    path = subject.BIN / hit['path']
                    self.assertIn(row[name]['value'], subject.read_json(path.read_bytes()))
        self.assertEqual([r['talentId'] for r in result['rows'] if r['javaValueDiffers']], list(range(621, 627)))
        self.assertTrue(all(s['matchesGitlinkAfterCRLFNormalization'] for s in result['sourceFiles']))
        self.assertEqual(result['trackedJsonMissingFromCheckout'], [])
        self.assertEqual(result, subject.make_report(data, tracked, proof))


if __name__ == '__main__':
    unittest.main()
