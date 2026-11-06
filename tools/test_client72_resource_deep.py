"""Independent assertions over the fresh native resource research report."""
import json
import hashlib
import unittest
from pathlib import Path
from tools.client72_resource_deep import HASHES, INPUTS


class ResourceDeepTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path('protocol/cn-7.2/resource-deep-observations.json').read_text('utf8'))
        cls.native = cls.report['native']

    def test_depot_omission_overwrites_prior_value(self):
        row = self.native['depotCases'][0]
        self.assertIsNone(row['first'])
        self.assertIsNone(row['second'])
        self.assertEqual(row['priorLogicalValue'], 777)
        self.assertEqual(row['decoded'], [0, 0])
        self.assertEqual(row['bytesConsumed'], 4)
        self.assertEqual(row['trace']['models'], [])

    def test_depot_distinct_fields_and_unsigned_values(self):
        rows = self.native['depotCases']
        self.assertEqual([r['decoded'] for r in rows], [[0, 0], [0, 0], [123, 0], [0, 456], [4294967295, 2147483649]])
        self.assertEqual([r['bytesConsumed'] for r in rows], [4, 12, 8, 8, 12])
        self.assertTrue(all(r['trace']['entry'] == '0x148328b60' and r['trace']['stop'] == '0x620000100' for r in rows))

    def test_nested_condition_type_and_id_are_independent(self):
        rows = self.native['specialCases']
        self.assertEqual(rows[2]['actual'], {'group': 901, 'params': [30302, 2147483649], 'kind': 1})
        self.assertEqual([r['queriedParameter'] for r in rows], [0, 0, 30302, 4294967295, 0])
        self.assertEqual(rows[4]['actual']['kind'], 777)
        self.assertTrue(all(r['lookup']['entry'] == '0x15113cf70' and r['lookup']['stop'] == '0x620000100' for r in rows))

    def test_bgh_nonempty_unicode_and_signed_params(self):
        rows = self.native['conditionCases']
        self.assertEqual(rows[2]['actual'], {'text': '2101001', 'values': [-1, 0, 2147483647], 'kind': 20, 'count': 4294967295})
        self.assertEqual(rows[4]['actual']['text'], '\u4efb\u52a1\0x')
        self.assertEqual(rows[0]['actual']['text'], '')
        self.assertEqual(rows[1]['actual']['text'], '')
        self.assertTrue(all(r['trace']['entry'] == '0x14ef42dd0' and r['trace']['stop'] == '0x620000100' for r in rows))

    def test_actual_consumer_is_bounded_before_split(self):
        row, = self.native['consumerCases']
        self.assertEqual(row['text'], '1,24')
        self.assertEqual(row['separator'], 44)
        self.assertEqual(row['trace']['entry'], '0x149987a80')
        self.assertEqual(row['trace']['stop'], '0x159aacf80')
        self.assertFalse(row['luaNotifyEvaluationExecuted'])
        self.assertFalse(row['gameTimeEvaluationExecuted'])

    def test_rejections_reach_actual_helpers(self):
        rows = self.native['rejectionCases']
        self.assertEqual(len(rows), 5)
        self.assertEqual([r['trace']['stop'] for r in rows], ['0x1404c2650'] * 4 + ['0x1404c2be0'])
        self.assertTrue(all(not r['exceptionHelperExecuted'] for r in rows))

    def test_independent_target_enum_symbols(self):
        enums = {r['typeIndex']: {v['symbol']: v['value'] for v in r['values']} for r in self.report['declarations']['enums']}
        self.assertEqual(enums[85159]['SPECIAL_PROUD_SKILL_OPEN_CONDITION_TYPE_QUEST_FINISH'], 1)
        self.assertEqual(enums[36721]['QUEST_CONTENT_LUA_NOTIFY'], 20)
        self.assertEqual(enums[36721]['QUEST_CONTENT_GAME_TIME_TICK'], 18)
        self.assertEqual(enums[75148]['QUEST_GUIDE_NPC'], 2)
        self.assertEqual(enums[56833]['QUEST_HIDDEN'], 1)

    def test_missing_json_bridge_never_upgrades_gate(self):
        report = self.report
        self.assertEqual(report['candidate']['excelShapes'], {'acceptCondComb': 'dict', 'finishCondComb': 'dict', 'failCondComb': 'str', 'acceptCond': 'absent'})
        self.assertFalse(report['candidate']['JSONToNativeFieldBridgeVerified'])
        self.assertFalse(report['candidate']['automaticAcceptanceRecoverableFromTheseInputs'])
        self.assertEqual(report['candidate']['aliasesApproved'], [])
        self.assertFalse(report['resourceReady'])
        self.assertFalse(report['targetProtocolReady'])
        self.assertEqual(report['runtimeStatus'], 'not-tested')

    def test_report_sources_and_fixed_inputs_are_current(self):
        self.assertEqual(self.report['inputSHA256'], HASHES)
        self.assertEqual(self.report['priorInputs'], INPUTS)
        for path, expected in {**INPUTS, **self.report['sourceSHA256']}.items():
            with self.subTest(path=path):
                self.assertEqual(hashlib.sha256(Path(path).read_bytes()).hexdigest(), expected)

    def test_managed_allocation_sites_preserve_element_width(self):
        rows = self.report['allocationModels']
        self.assertEqual({r['site'] for r in rows}, {'0x15113ccca', '0x14ef42ea3', '0x149987adc'})
        self.assertTrue(all(r['width'] == (2 if r['element'] == 'char' else 4) for r in rows))
        self.assertTrue(all('model' in r['scope'] for r in rows))


if __name__ == '__main__':
    unittest.main()
