import json
from pathlib import Path
import unittest
from types import SimpleNamespace
from tools.extract_client72 import HASHES
from tools.trace_client72_player_store import ROOT, ITEM, EXPECTED_CLOSURE, BRANCHES, verify


class PlayerStoreOuterRawTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((Path(__file__).resolve().parents[1] /
            'protocol/cn-7.2/player-store-outer-observations.json').read_text('utf8'))
        cls.cases = {x['name']: x for x in cls.report['fixtures']}

    def test_fixed_inputs_and_scope_do_not_claim_full_initialization(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual('F-03d2a1', self.report['task'])
        self.assertEqual('unresolved', self.report['semanticIdentityStatus'])
        self.assertFalse(self.report['fullStoreRawClosure'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual([(20892, ROOT)], [(c['cmdId'], c['typeIndex']) for c in self.report['commands']])

    def test_dependency_audit_reaches_nested_oneof_classes(self):
        audit = self.report['dependencyAudit']
        self.assertEqual(EXPECTED_CLOSURE, {x['typeIndex'] for x in audit['types']})
        self.assertEqual((15, 42), (audit['typeCount'], audit['instanceStorageFields']))
        bindings = {x['type']['typeDefinition'] for x in audit['typeCells']}
        self.assertTrue({28473, 28474, 28475}.issubset(bindings))
        edges = {(x['source'], x['target']) for x in audit['edges']}
        self.assertTrue({(ROOT, ITEM), (63442, 28471), (28471, 28473), (28471, 28474), (28471, 28475),
                         (44169, 64947), (44169, 74063), (63899, 61085)}.issubset(edges))

    def test_bounded_outer_scan_recovers_thirteen_fields(self):
        scan = self.report['scan']
        self.assertEqual(512, scan['nativeFixtures'])
        self.assertEqual(13, len(scan['fields']))
        self.assertEqual(set(BRANCHES), {x['tag'] for x in scan['fields'] if x['typeIndex'] == ITEM and x['wireType'] == 2})
        self.assertTrue(all(x['result'] or x['stores'] for x in scan['fields']))

    def test_independent_wire_preserves_high64_and_upper_limits(self):
        row = self.cases['item-high64']
        self.assertEqual('082a108180808080808010', row['wire'])
        self.assertEqual({'PJAGIOHBKLD': 42, 'FBLKPHOCBIK': 9007199254740993}, row['result'])
        self.assertEqual({'PJAGIOHBKLD': 4294967295, 'FBLKPHOCBIK': 18446744073709551615}, self.cases['item-max']['result'])
        self.assertEqual({'PAKLDABINMP': 4294967295, 'CLFJNKKFONI': 4294967295}, self.cases['root-max']['result'])

    def test_scalar_oneof_merge_zero_switch_and_return(self):
        self.assertEqual({'FLPGMLOFAKP_7': {'GNHFNECPAHD': 42}}, self.cases['same-scalar-leaf-omission-retains']['result'])
        self.assertEqual({'FLPGMLOFAKP_7': {}}, self.cases['same-scalar-leaf-explicit-zero']['result'])
        self.assertEqual({'FLPGMLOFAKP_7': {}}, self.cases['switch-and-return-clears-old-value']['result'])
        self.assertEqual({}, self.cases['item-explicit-zero']['result'])
        self.assertEqual({'PJAGIOHBKLD': 2, 'FBLKPHOCBIK': 3}, self.cases['item-duplicate-wrong-wire']['result'])

    def test_message_list_really_reads_two_elements(self):
        row = self.cases['root-two-item-list']
        self.assertEqual(2, len(row['result']['MBEEKCPJBEL']))
        self.assertEqual(2, sum(x.get('typeIndex') == ITEM for x in row['entered']))
        self.assertTrue(any(x.get('entry') == '0x158dc41f0' for x in row['entered']))
        self.assertEqual({'MBEEKCPJBEL': [{}]}, self.cases['root-empty-item-list']['result'])

    def test_untraced_children_stay_marked_and_nonempty_is_refused(self):
        for tag, child in BRANCHES.items():
            result = self.cases['item-branch-envelope-' + str(tag)]['result']['FLPGMLOFAKP_' + str(tag)]
            if tag in (7, 9): self.assertEqual({}, result)
            else: self.assertEqual({'untracedType': child, 'content': 'empty-envelope-only'}, result)
        rejected = {x['name']: x for x in self.report['rejections']}
        self.assertIn('Nonempty child outside F-03d2a1', rejected['untraced-nonempty-nested']['error'])
        self.assertEqual('explicit-scope-refusal', rejected['untraced-nonempty-nested']['scope'])
        self.assertEqual(5, len(rejected))

    def test_result_drift_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'native graph differs'):
            verify(SimpleNamespace(run_graph=lambda *args: {'result': {'unexpected': 1}}))


if __name__ == '__main__': unittest.main()
