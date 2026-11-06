import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.extract_client72 import HASHES


class AvatarControlConsumerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_CONTROL_OBSERVATIONS', cls.root / 'protocol/cn-7.2/avatar-control-observations.json'))
        cls.report = json.loads(path.read_text('utf8'))
        cls.rows = {r['name']: r for r in cls.report['fixtures']}

    def test_fixed_source_and_no_current_control_promotion(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('F-03d3f2a', self.report['task'])
        self.assertFalse(self.report['currentControlGuidBindingApproved'])
        self.assertFalse(self.report['completeSceneHandler'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        self.assertEqual(7, len(self.rows))
        for path, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / path).read_bytes()).hexdigest())

    def test_native_hero_and_local_consumers_can_select_distinct_entities(self):
        a = self.rows['hero-a-local-b']
        b = self.rows['hero-b-local-a']
        self.assertEqual((0, 1, 1002), (a['heroOrdinal'], a['activeOrdinal'], a['localConfig']))
        self.assertEqual((1, 0, 1001), (b['heroOrdinal'], b['activeOrdinal'], b['localConfig']))
        self.assertTrue(a['heroEffectDelivered'])
        self.assertTrue(b['heroEffectDelivered'])
        self.assertEqual('0x153e6d660', a['heroEffect']['stop'])

    def test_high_guid_bits_survive_reader_dispatch_and_native_lookup(self):
        for name, value in [('hero-a-local-b', 9007199254740993),
                            ('hero-b-local-a', 9223372036854775809),
                            ('max-u64-hero', 18446744073709551615)]:
            row = self.rows[name]
            self.assertEqual(value, row['storedGuid'])
            self.assertEqual(value, row['readerResult']['LFMIIOCDKNK'])
            self.assertIn('0x156f6f510', row['hero']['nativeEntries'])
        row = self.rows['hero-and-local-match']
        self.assertEqual((0, 0, 1001), (row['heroOrdinal'], row['activeOrdinal'], row['localConfig']))

    def test_absent_or_disabled_hero_does_not_replace_local_avatar(self):
        for name in ('missing-hero-keeps-local', 'zero-hero-keeps-local', 'disabled-hero-keeps-local'):
            row = self.rows[name]
            self.assertIsNone(row['heroOrdinal'])
            self.assertFalse(row['heroEffectDelivered'])
            self.assertEqual(1002, row['localConfig'])
            self.assertEqual('0x620000100', row['heroEffect']['stop'])

    def test_critical_native_lookup_and_named_consumers_are_not_modeled(self):
        for row in self.rows.values():
            for key, required in [('dispatch', {'0x1507cc240', '0x1507cee50'}),
                                  ('hero', {'0x1517b8730', '0x14e9b2c60', '0x156f6f510'}),
                                  ('local', {'0x153e46950', '0x1517abd80'}),
                                  ('heroEffect', {'0x153e4d900', '0x1517b8730'})]:
                self.assertTrue(required <= set(row[key]['nativeEntries']))
                self.assertFalse(required & set(row[key]['models']))
        named = {m['name'] for m in self.report['declarations']['methods']}
        self.assertTrue({'GetLocalAvatarConfigId', 'SetHeroMoveRatio'} <= named)


if __name__ == '__main__':
    unittest.main()
