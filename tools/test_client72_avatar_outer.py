import json
import os
from pathlib import Path
from types import SimpleNamespace
import unittest
from tools.extract_client72 import HASHES
from tools.trace_client72_avatar_outer import ROOT, TEAM, RENAME, AVATAR, verify


class AvatarOuterRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('CLIENT72_AVATAR_OUTER_OBSERVATIONS', root / 'protocol/cn-7.2/avatar-outer-observations.json'))
        cls.report = json.loads(path.read_text('utf8')); cls.cases = {x['name']: x for x in cls.report['fixtures']}

    def test_fixed_cmd_type_and_partial_scope(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual([(20143, ROOT, 'ILEPMBEJKIA')], [(x['cmdId'], x['typeIndex'], x['clientType']) for x in self.report['commands']])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertFalse(self.report['fullAvatarRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual({ROOT, TEAM, RENAME}, {m['typeIndex'] for m in self.report['messages']})

    def test_static_dependency_audit_does_not_claim_wire_recovery(self):
        audit = self.report['dependencyAudit']
        self.assertEqual((65, 368), (audit['typeCount'], audit['instanceStorageFields']))
        self.assertEqual([28471, 44169, 51050, 65333, 78350, 85344], audit['objectOwners'])
        self.assertEqual('static-candidates-not-wire-discriminated', audit['objectEdgesStatus'])
        self.assertEqual([{'typeIndex': ROOT, 'entry': '0x152de03c0', 'jump': '0x15a4e4753'}], audit['protectedReaders'])
        self.assertEqual(37, len(audit['containerAOTRequirements']))
        self.assertTrue(all(x['status'] == 'static-binding-found-not-all-executed' for x in audit['containerAOTRequirements']))

    def test_four_same_shape_lists_use_observed_offsets(self):
        root = next(m for m in self.report['messages'] if m['typeIndex'] == ROOT)
        raw = {f['tag']: (f['clientField'], f['objectOffset']) for f in root['fields'] if f['cardinality'] == 'repeated' and f['type'] == 'uint32'}
        self.assertEqual({4: ('BJKPIFCAKKA', 64), 13: ('GDFOCENMMIA', 56), 14: ('LCDMGOFLDKF', 24), 15: ('HENJGDHPLIN', 32)}, raw)
        self.assertEqual({'BJKPIFCAKKA': [1, 128], 'GDFOCENMMIA': [2, 128], 'LCDMGOFLDKF': [3, 128], 'HENJGDHPLIN': [4, 128]}, self.cases['outer-u32-lists']['result'])
        self.assertEqual(384, self.report['scan']['nativeFixtures'])
        packed = next(x for x in self.report['scan']['fields'] if (x['typeIndex'], x['tag'], x['wireType']) == (ROOT, 4, 2))
        self.assertTrue(any(x.get('kind') == 'native-list-field' and x['objectOffset'] == 64 and x['key'] == 34 for x in packed['entered']))

    def test_native_u32_and_u64_collection_bodies_are_distinct(self):
        rows = self.report['aotBindings']
        for instance, address in [(11, '0x158db8e10'), (58, '0x1571ddd60')]:
            row = next(x for x in rows if (x['owner'], x['classInstance'], x['method']) == (35584, instance, 'FNNADLCNGFO'))
            self.assertEqual(address, row['address'])
        initializers = {x['typeIndex']: x for x in self.report['initializers']}
        self.assertEqual([{'width': 64, 'tag': 98}], initializers[TEAM]['factoryArguments'])
        self.assertEqual({'IAACAIILIFD': [0, 9007199254740993, 18446744073709551615]}, self.cases['outer-u64-list-packed-unpacked-upper-zero']['result'])
        self.assertEqual({'PIDCNPCLBCE': [0, 18446744073709551615]}, self.cases['team-u64-packed-unpacked-upper-zero']['result'])

    def test_nonempty_team_map_and_rename_message_list(self):
        self.assertEqual({'CNCCIILAJNJ': {'7': {'MGIDEJKCAKD': 'T', 'PIDCNPCLBCE': [9007199254740993]}}}, self.cases['root-nonempty-team-map']['result'])
        row = self.cases['root-team-map-duplicate-value-replaces']
        self.assertEqual({'CNCCIILAJNJ': {'7': {'MGIDEJKCAKD': 'B'}}}, row['result'])
        self.assertTrue(row['mapStores'])
        self.assertTrue(any(x.get('typeIndex') == TEAM for x in row['entered']))
        self.assertEqual({'AGCIPPKKCAC': [{'NLNJDCDLHHE': 11, 'PBDIJJHDMOF': 'N'}]}, self.cases['root-nonempty-rename-list']['result'])

    def test_unicode_nul_zero_and_uint64_boundary(self):
        self.assertEqual({'MGIDEJKCAKD': '中🌙\0'}, self.cases['team-unicode-nul']['result'])
        self.assertEqual({'PBDIJJHDMOF': '中🌙\0'}, self.cases['rename-unicode-nul']['result'])
        self.assertEqual({}, self.cases['rename-explicit-zero-empty']['result'])
        self.assertEqual({}, self.cases['outer-duplicate-zero-wrong-wire']['result'])
        self.assertEqual(18446744073709551615, self.cases['outer-upper-scalars']['result']['LFMIIOCDKNK'])
        self.assertEqual(4294967295, self.cases['outer-upper-scalars']['result']['OCIKDBJPOAJ'])

    def test_combined_outer_content_keeps_avatar_info_explicitly_untraced(self):
        row = self.cases['all-allowed-outer-content']['result']
        self.assertEqual(10, len(row))
        self.assertEqual([{'untracedType': AVATAR, 'content': 'empty-envelope-only'}], row['JGECGMPNGBD'])
        rejected = {x['name']: x for x in self.report['rejections']}
        self.assertEqual('explicit-scope-refusal', rejected['nonempty-avatar-info']['scope'])
        self.assertIn('Nonempty AvatarInfo outside F-03d3a', rejected['nonempty-avatar-info']['error'])
        self.assertEqual(4, sum(x['scope'] == 'explicit-input-model' for x in rejected.values()))

    def test_result_drift_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Avatar native result differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'invented': True}}))


if __name__ == '__main__': unittest.main()
