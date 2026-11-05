import json
from pathlib import Path
import tempfile
import unittest

from tools.audit_scene_serialization72 import evaluate, summarize, verify_sources, REVIEWED_SOURCES
from tools.audit_scene_scripts72 import ENTITY_TABLES


class SceneSerializationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.java = next((cls.repo / 'local/runtime').glob('jdk-17*'))
        cls.jar = cls.repo / 'LunaGC-7.1.0.jar'

    def fixture(self, group, gadget=None, dynamic=False):
        with tempfile.TemporaryDirectory(prefix='scene-serialization-test-', dir=self.repo / 'local/migration72/analysis') as folder:
            root = Path(folder)
            scripts, source, work = root / 'Scripts', root / 'Source', root / 'classes'
            (scripts / 'Scene/3').mkdir(parents=True)
            (scripts / 'Common').mkdir()
            (source / 'ExcelBinOutput').mkdir(parents=True)
            work.mkdir()
            for name in ENTITY_TABLES:
                (source / 'ExcelBinOutput' / name).write_text(json.dumps([gadget or {'id': 7}]), 'utf8')
            (scripts / 'Scene/3/scene3.lua').write_text('''scene_config={born_pos={x=1,y=0,z=1}}
blocks={10};block_rects={{min={x=-10,z=-10},max={x=10,z=10}}}''', 'utf8')
            (scripts / 'Scene/3/scene3_block10.lua').write_text('groups={{id=100,pos={x=-19,y=0,z=19}}' +
                (',{id=200,pos={x=0,y=0,z=0},dynamic_load=true}' if dynamic else '') + '}', 'utf8')
            (scripts / 'Scene/3/scene3_group100.lua').write_text(group, 'utf8')
            (scripts / 'Scene/3/scene3_group200.lua').write_text('error("dynamic must not run")', 'utf8')
            (scripts / 'Common/Shared.lua').write_text('from_local=defs.id', 'utf8')
            config = root / 'config.json'
            config.write_text(json.dumps({'server': {'game': {'visionOptions': [
                {'name': f'LEVEL{i}', 'visionRange': 80 + i * 10, 'gridWidth': 20} for i in range(6)]}}}), 'utf8')
            output = root / 'report.json'
            report = evaluate(self.repo, scripts, source, self.java, self.jar, config, output, work)
            self.assertFalse(report['productionInfrastructureInitialized'])
            return report

    def test_real_serializer_sparse_nested_suite_inline_and_dynamic_filter(self):
        report = self.fixture('''local defs={id=7}
require "Shared"
gadgets={[9000]={config_id=9000,gadget_id=from_local,pos={x=-19,y=2,z=19},vision_level=2,
boss_chest={monster_config_id=8}}}
monsters={{config_id=8,monster_id=7,pos={x=1,y=2,z=3},affix={1,3}}}
regions={{config_id=9,shape=RegionShape.CUBIC,pos={x=1,y=2,z=3},size={x=3,y=4,z=5}}}
triggers={{name="never",action="never"}}
function never() error("action must not execute") end
suites={{monsters={8},gadgets={9000},regions={9},triggers={"never"}}}''', dynamic=True)
        self.assertEqual(report['issues'], [])
        self.assertEqual(report['counts']['serializedGroups'], 1)
        self.assertEqual(report['counts']['initializedSuites'], 1)
        self.assertEqual(report['counts']['suiteBound.gadgets'], 1)
        self.assertEqual(report['counts']['dynamicGroupsSkipped'], 1)
        self.assertEqual(report['counts']['gridInputGroupsCompleted'], 1)
        self.assertEqual(report['gridLayers'][2]['cells'], 1)  # Java truncation of -19/20 is zero.
        self.assertEqual(report['gridLayers'][2]['groupMemberships'], 1)
        self.assertFalse(report['resourceReady'])
        self.assertFalse(report['legacySourceIsNative72'])
        self.assertEqual(summarize(report)['scriptManifest']['commonFiles'], 1)

    def test_fractional_radius_preserved_and_other_coercion_or_unknowns_stay_visible(self):
        report = self.fixture('''gadgets={{config_id=1,gadget_id=7,pos={x=1,y=0,z=1},persistent=0,new_field=9}}
regions={{config_id=2,shape=RegionShape.SPHERE,pos={x=1,y=0,z=1},radius=2.75}}
suites={{gadgets={99}}}''')
        kinds = {row['kind'] for row in report['issues']}
        self.assertEqual(kinds, {'boolean-coercion', 'suite-reference-dropped'})
        self.assertEqual(report['ignoredSourceFields']['SceneGadget.new_field'], 1)
        self.assertFalse(any(row['kind'] == 'numeric-value-changed' for row in report['issues']))

    def test_invalid_grid_layer_missing_position_and_unknown_region_enum_are_not_success(self):
        report = self.fixture('''gadgets={{config_id=1,gadget_id=7,vision_level=9,pos={x=1,y=0,z=1}}}
regions={{config_id=2,shape=99}}''')
        self.assertEqual({row['kind'] for row in report['issues']},
                         {'invalid-vision-layer', 'invalid-grid-position', 'unknown-region-shape'})
        self.assertNotIn('gridInputGroupsCompleted', report['counts'])
        self.assertEqual(report['counts']['gridInputGroupsPartial'], 1)

    def test_target_vision_name_and_garbage_missing_reference_follow_distinct_paths(self):
        report = self.fixture('''gadgets={{config_id=1,gadget_id=7,pos={x=1,y=0,z=1}}}
garbages={gadgets={{config_id=2,gadget_id=8,pos={x=41,y=0,z=1},vision_level=1}}}''',
                              {'id': 7, 'visionLevel': 'LEVEL3'})
        self.assertEqual(report['counts']['targetGadgetRowsWithVisionLevel'], 1)
        self.assertEqual(report['gridLayers'][3]['groupMemberships'], 1)
        self.assertEqual(report['gridLayers'][1]['groupMemberships'], 1)
        self.assertEqual(report['issues'], [{'group': 100, 'kind': 'missing-target-gadget', 'path': 'garbages.gadgets', 'value': '8'}])

    def test_real_top_level_failure_retained_without_serialized_success(self):
        report = self.fixture('error("real failure")')
        self.assertNotIn('serializedGroups', report['counts'])
        self.assertEqual(report['issues'][0]['kind'], 'group-failure')
        self.assertIn('real failure', report['issues'][0]['value'])

    def test_numeric_trigger_strings_and_boolean_fields_bind_after_production_fix(self):
        report = self.fixture('''monsters={{config_id=1,monster_id=7,pos={x=1,y=0,z=1},isElite=true}}
gadgets={{config_id=2,gadget_id=7,pos={x=1,y=0,z=1},is_use_point_array=true}}
triggers={{name=tostring(52001)}}
suites={{triggers={tostring(52001)}}}''')
        self.assertEqual(report['unboundDeclaredSourceFields'], {})
        self.assertEqual(report['unboundDeclaredFieldExamples'], {})
        self.assertEqual(report['issues'], [])
        self.assertEqual(report['counts']['serializedGroups'], 1)
        self.assertEqual(report['counts']['initializedSuites'], 1)
        self.assertEqual(report['counts']['suiteBound.triggers'], 1)

    def test_reviewed_source_drift_rejected(self):
        with tempfile.TemporaryDirectory(prefix='scene-serialization-guard-', dir=self.repo / 'local/migration72/analysis') as folder:
            root = Path(folder)
            first = next(iter(REVIEWED_SOURCES))
            (root / first).parent.mkdir(parents=True)
            (root / first).write_text('changed input', 'utf8')
            with self.assertRaisesRegex(ValueError, 'changed serialization dependency'):
                verify_sources(root)


if __name__ == '__main__':
    unittest.main()
