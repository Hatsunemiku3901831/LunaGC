import hashlib
import json
import unittest
from pathlib import Path
from tools.client72_resource_deep_quest import HASHES, PRIOR


class ResourceQuestConsumerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path('protocol/cn-7.2/resource-deep-quest-observations.json').read_text('utf8'))

    def test_game_time_selected_lua_rejected(self):
        rows = self.report['cases']
        self.assertEqual([r['selectedIndex'] for r in rows[:4]], [None, 0, 1, 0])
        self.assertEqual(rows[0]['enumValues'], [20])
        self.assertEqual(rows[1]['enumValues'], [18])
        self.assertTrue(all(r['trace']['stop'] == '0x620000100' for r in rows[:6]))

    def test_native_null_boundaries_and_output_clear(self):
        rows = self.report['cases']
        self.assertEqual([r['trace']['stop'] for r in rows[-2:]], ['0x1404c2be0', '0x1404c2be0'])
        self.assertTrue(all(r['outputClearedBeforeSearch'] for r in rows))

    def test_original_parameter_types_and_caller_edges(self):
        d = self.report['declarations']
        self.assertEqual(d['caller'], '0x1499882f0')
        self.assertEqual([r['target'] for r in d['directCalls']], ['0x1487cbb80', '0x149987a80'])
        self.assertTrue(all(any(p['type'].get('typeDefinition') == 69472 for p in r['parameters']) for r in d['typedMethods']))

    def test_hashes_and_lua_boundary_stay_explicit(self):
        r = self.report
        self.assertEqual(r['inputSHA256'], HASHES)
        self.assertEqual(r['priorInputs'], PRIOR)
        for path, sha in {**PRIOR, **r['sourceSHA256']}.items():
            self.assertEqual(hashlib.sha256(Path(path).read_bytes()).hexdigest(), sha, path)
        self.assertFalse(r['luaNotifyEvaluationExecuted'])
        self.assertEqual(r['aliasesApproved'], [])
        self.assertFalse(r['resourceReady'])
        self.assertFalse(r['targetProtocolReady'])


if __name__ == '__main__':
    unittest.main()
