"""Check nested reader evidence against independent values and fixed inputs."""
import hashlib
import json
import unittest
from pathlib import Path
from tools.client72_resource_deep_nested import HASHES, PRIOR


class ResourceDeepNestedTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path('protocol/cn-7.2/resource-deep-nested-observations.json').read_text('utf8'))

    def test_type_cells_and_virtual_slot(self):
        cells = self.report['allocationTypeCells']
        self.assertEqual(cells[0]['type']['elementType']['typeDefinition'], 40302)
        self.assertEqual(cells[1]['type']['typeDefinition'], 40302)
        self.assertEqual(cells[2]['type']['elementType']['typeDefinition'], 58209)
        self.assertEqual(self.report['elementParser']['virtualSlot'], 4)

    def test_missing_empty_and_two_defaults_differ(self):
        rows = self.report['cases'][:3]
        self.assertEqual([r['bytesConsumed'] for r in rows], [4, 8, 12])
        self.assertEqual([len(r['actual']) for r in rows], [0, 0, 2])
        self.assertEqual(rows[0]['trace']['nativeEntryVisits'], {'0x148328b60': 1, '0x15113ce30': 0, '0x15113cbb0': 0})
        self.assertEqual(rows[1]['trace']['nativeEntryVisits'], {'0x148328b60': 1, '0x15113ce30': 1, '0x15113cbb0': 0})
        self.assertEqual(rows[2]['trace']['nativeEntryVisits'], {'0x148328b60': 1, '0x15113ce30': 1, '0x15113cbb0': 2})
        for row in rows[2]['actual']:
            self.assertEqual({k: v for k, v in row.items() if k != 'query'}, {'group': 0, 'params': [], 'kind': 0, 'queriedParameter': 0})

    def test_full_nested_nonzero_highbit_and_unknown_enum(self):
        rows = self.report['cases']
        self.assertEqual(rows[3]['actual'][0]['params'], [30302, 2147483649])
        self.assertEqual(rows[3]['actual'][1]['queriedParameter'], 4294967295)
        self.assertEqual(rows[3]['actual'][1]['group'], 4294967295)
        self.assertEqual([r['queriedParameter'] for r in rows[4]['actual']], [0, 0])
        self.assertEqual(rows[4]['actual'][0]['kind'], 777)
        self.assertTrue(all(r['trace']['entry'] == '0x148328b60' and r['trace']['stop'] == '0x620000100' for r in rows))

    def test_native_error_boundaries_and_conditional_type_model(self):
        rows = self.report['rejections']
        self.assertEqual([r['trace']['stop'] for r in rows], ['0x1404c2c70', '0x1404c2be0', '0x1404c2be0', '0x1404c2650'])
        checks = self.report['typeCheckModels']
        self.assertTrue(any(not r['fixtureClassMatches'] for r in checks))
        self.assertTrue(any(r['fixtureClassMatches'] for r in checks))
        self.assertTrue(all(r['expectedType'] == 40302 for r in checks))

    def test_source_hashes_and_no_gate_upgrade(self):
        report = self.report
        self.assertEqual(report['inputSHA256'], HASHES)
        self.assertEqual(report['priorInputs'], PRIOR)
        for path, expected in {**PRIOR, **report['sourceSHA256']}.items():
            with self.subTest(path=path):
                self.assertEqual(hashlib.sha256(Path(path).read_bytes()).hexdigest(), expected)
        self.assertFalse(report['resourceReady'])
        self.assertFalse(report['targetProtocolReady'])
        self.assertFalse(report['JSONToNativeFieldBridgeVerified'])
        self.assertEqual(report['aliasesApproved'], [])


if __name__ == '__main__':
    unittest.main()
