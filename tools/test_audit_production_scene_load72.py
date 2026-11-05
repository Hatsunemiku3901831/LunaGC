import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from tools.audit_production_scene_load72 import compile_helper, run_process, verify_inputs


EMPTY = 'monsters={};npcs={};gadgets={};regions={};triggers={};variables={};suites={{gadgets={}}};init_config={suite=1}\n'


class ProductionSceneLoadTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.java = next((cls.repo / 'local/runtime').glob('jdk-17*'))
        cls.jar = cls.repo / 'LunaGC-7.1.0.jar'
        cls.temporary = tempfile.TemporaryDirectory(prefix='scene-production-load-tests-', dir=cls.repo / 'local/migration72/analysis')
        cls.root = Path(cls.temporary.name)
        cls.classes = cls.root / 'classes'
        cls.classes.mkdir()
        compile_helper(cls.repo, cls.java, cls.jar, cls.classes)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def fixture(self, group, common=None, fast=False, guard=False, groups=None):
        with tempfile.TemporaryDirectory(prefix='scene-production-load-fixture-', dir=self.root) as folder:
            root = Path(folder)
            resources = root / 'resources'
            for name in ('Scripts/Scene/3', 'Scripts/Common', 'BinOutput', 'ExcelBinOutput', 'Server', 'ScriptSceneData'):
                (resources / name).mkdir(parents=True)
            (resources / 'Scripts/Scene/3/scene3_group1.lua').write_text(group, 'utf8')
            (resources / 'Scripts/Scene/3/scene3_group2.lua').write_text('error("second group must not execute")', 'utf8')
            if common is not None:
                commons = common if isinstance(common, dict) else {'Shared': common}
                for name, text in commons.items():
                    (resources / 'Scripts/Common' / (name + '.lua')).write_text(text, 'utf8')
            manifest = {}
            for path in (resources / 'Scripts').rglob('*.lua'):
                raw = path.read_bytes()
                manifest[path.relative_to(resources / 'Scripts').as_posix()] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
            return run_process(self.java, self.jar, self.classes, resources, groups or [1], manifest, fast, guard,
                               root / 'work', root / 'evidence/scene-production-load-test')

    def test_actual_fast_require_swallows_local_defs_error_and_preserves_partial_bindings(self):
        group = 'local defs={id=7}\n' + EMPTY + 'require "Shared"\nafter_require=true\nfunction never() error("action must not run") end'
        common = '''common_entered=true
common_value=defs.id
common_completed=true
table.insert(gadgets,{config_id=1,gadget_id=common_value,pos={x=1,y=0,z=1}})
table.insert(suites[1].gadgets,1)'''
        reports = {fast: self.fixture(group, common, fast) for fast in (True, False)}
        fast, inline = (reports[mode]['groups'][0] for mode in (True, False))
        self.assertTrue(fast['isLoadedFlag'])
        self.assertTrue(fast['allCollectionsMaterialized'])
        self.assertEqual(fast['status'], 'partial-production-evaluation')
        self.assertEqual(len(fast['errors']), 1)
        self.assertIn('nil value', fast['errors'][0]['message'])
        self.assertEqual(fast['scalarBindings']['common_entered']['value'], 'true')
        self.assertEqual(fast['scalarBindings']['after_require']['value'], 'true')
        self.assertNotIn('common_completed', fast['bindingTypes'])
        self.assertEqual((fast['collections']['gadgets'], fast['suiteBound']['gadgets']), (0, 0))
        self.assertEqual(inline['status'], 'loaded-within-audit-scope')
        self.assertEqual((inline['collections']['gadgets'], inline['suiteBound']['gadgets']), (1, 1))
        self.assertEqual(inline['scalarBindings']['common_completed']['value'], 'true')
        self.assertEqual(inline['bindingTypes']['never'], 'function')
        self.assertNotEqual(fast['objectsSHA256'], inline['objectsSHA256'])
        for report in reports.values():
            self.assertEqual(report['forbiddenInfrastructureInitialized'], [])
            self.assertEqual(report['newJavaThreadNames'], [])
            self.assertEqual(report['effectiveSourceDifferences'], {})

    def test_guard_records_call_even_when_require_swallows_its_exception_then_stops_next_group(self):
        report = self.fixture(EMPTY + 'require "Shared"\nafter_require=true',
                              'before_call=true\nScriptLib.GetServerTime(ScriptLib)\nafter_call=true',
                              fast=True, guard=True, groups=[1, 2])
        self.assertEqual(report['businessAttempts'], [{'group': 1, 'member': 'GetServerTime'}])
        self.assertEqual((report['processedGroups'], report['requestedGroups']), (1, 2))
        group = report['groups'][0]
        self.assertTrue(group['isLoadedFlag'])
        self.assertTrue(group['allCollectionsMaterialized'])
        self.assertEqual(group['scalarBindings']['after_require']['value'], 'true')
        self.assertNotIn('after_call', group['bindingTypes'])
        self.assertEqual(group['status'], 'outside-scope-business-call-blocked')

    def test_successful_first_require_loses_caller_bindings_for_second_fast_require(self):
        group = EMPTY + 'require "First"\nrequire "Second"\nafter_requires=true'
        commons = {'First': 'first_marker=7',
                   'Second': 'if first_marker ~= 7 then error("second require lost caller bindings") end\nsecond_completed=true'}
        fast = self.fixture(group, commons, fast=True)['groups'][0]
        inline = self.fixture(group, commons, fast=False)['groups'][0]
        self.assertEqual(fast['scalarBindings']['first_marker']['value'], '7')
        self.assertEqual(fast['scalarBindings']['after_requires']['value'], 'true')
        self.assertNotIn('second_completed', fast['bindingTypes'])
        self.assertIn('second require lost caller bindings', fast['errors'][0]['message'])
        self.assertEqual(fast['status'], 'partial-production-evaluation')
        self.assertEqual(inline['scalarBindings']['second_completed']['value'], 'true')
        self.assertEqual(inline['status'], 'loaded-within-audit-scope')

    def test_member_read_and_function_definition_are_not_business_invocations(self):
        report = self.fixture(EMPTY + '''local method=ScriptLib.GetServerTime
read_was_function=type(method)=="function"
function never() return ScriptLib.GetServerTime(ScriptLib) end''', guard=True)
        self.assertEqual(report['businessAttempts'], [])
        self.assertEqual(report['groups'][0]['status'], 'loaded-within-audit-scope')
        self.assertEqual(report['groups'][0]['scalarBindings']['read_was_function']['value'], 'true')

    def test_plain_group_binding_hash_is_identical_in_separate_mode_jvms(self):
        fast = self.fixture(EMPTY, fast=True)['groups'][0]
        inline = self.fixture(EMPTY, fast=False)['groups'][0]
        self.assertEqual(fast['bindingTypes'], inline['bindingTypes'])
        self.assertEqual(fast['objectsSHA256'], inline['objectsSHA256'])
        self.assertEqual(fast['bindingsSHA256'], inline['bindingsSHA256'])

    def test_missing_common_is_visible_despite_loaded_flag(self):
        report = self.fixture(EMPTY + 'require "Missing"', fast=True)
        self.assertEqual(report['missingScripts'], {'Common': 1})
        self.assertTrue(report['groups'][0]['isLoadedFlag'])
        self.assertEqual(report['groups'][0]['status'], 'partial-production-evaluation')
        self.assertEqual(report['groups'][0]['missingScriptsDuringGroup'], {'Common': 1})

    def test_real_top_level_exception_leaves_loaded_flag_and_null_collections(self):
        report = self.fixture('error("real top-level exception")')
        group = report['groups'][0]
        self.assertTrue(group['isLoadedFlag'])
        self.assertFalse(group['allCollectionsMaterialized'])
        self.assertEqual(group['status'], 'incomplete-production-load')
        self.assertEqual(group['collections']['gadgets'], -1)
        self.assertTrue(group['errors'])

    def test_runtime_jar_drift_is_rejected_before_production_initialization(self):
        bad = self.root / 'wrong.jar'
        bad.write_bytes(b'wrong runtime')
        with self.assertRaisesRegex(ValueError, 'changed production loader JAR'):
            verify_inputs(self.repo, self.repo / 'resources', self.repo / 'missing-report.json', bad)


if __name__ == '__main__':
    unittest.main()
