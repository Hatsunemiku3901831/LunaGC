import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from tools.audit_scene_scripts72 import evaluate, summarize, ENTITY_TABLES


class SceneScriptAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.java = next((cls.repo / 'local/runtime').glob('jdk-17*'))
        cls.jar = cls.repo / 'LunaGC-7.1.0.jar'
        cls.build = tempfile.TemporaryDirectory(dir=cls.repo / 'local/migration72/analysis')
        cls.classes = Path(cls.build.name)
        subprocess.run([str(cls.java / 'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(cls.jar),
                        '-d', str(cls.classes), str(cls.repo / 'tools/Client72SceneScriptAudit.java')],
                       check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.build.cleanup()

    def run_fixture(self, directory, group, common='shared = 7\n', all_static=False, other_block=False, fast_require=False):
        root = Path(directory)
        scripts, source = root / 'Scripts', root / 'Source'
        (scripts / 'Scene/3').mkdir(parents=True)
        (scripts / 'Common').mkdir()
        (source / 'ExcelBinOutput').mkdir(parents=True)
        for name in ENTITY_TABLES:
            (source / 'ExcelBinOutput' / name).write_text('[{"id":7}]', 'utf8')
        (scripts / 'Scene/3/scene3.lua').write_text('''scene_config={born_pos={x=1,y=0,z=1}}
blocks={10};block_rects={{min={x=0,z=0},max={x=2,z=2}}}''', 'utf8')
        (scripts / 'Scene/3/scene3_block10.lua').write_text('groups={{id=100}}', 'utf8')
        (scripts / 'Scene/3/scene3_group100.lua').write_text(group, 'utf8')
        if other_block:
            (scripts / 'Scene/3/scene3.lua').write_text('''scene_config={born_pos={x=1,y=0,z=1}}
blocks={10,11};block_rects={{min={x=0,z=0},max={x=2,z=2}},{min={x=10,z=10},max={x=20,z=20}}}''', 'utf8')
            (scripts / 'Scene/3/scene3_block11.lua').write_text('groups={{id=200,dynamic_load=true}}', 'utf8')
            (scripts / 'Scene/3/scene3_group200.lua').write_text('error("not a static grid input")', 'utf8')
        if common is not None:
            (scripts / 'Common/Shared.lua').write_text(common, 'utf8')
        output = root / 'report.json'
        subprocess.run([str(self.java / 'bin/java.exe'), '-cp', str(self.classes) + ';' + str(self.jar),
                        'Client72SceneScriptAudit', str(scripts), str(source), str(output),
                        *(['all-static'] if all_static else []), *(['fast-require'] if fast_require else [])],
                       check=True, capture_output=True)
        return json.loads(output.read_text('utf8'))

    def test_real_lua_requires_sparse_collections_and_no_trigger_execution(self):
        with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as directory:
            report = self.run_fixture(directory, '''require "Shared"
gadgets={[9000]={config_id=9000,gadget_id=shared,vision_level=VisionLevelType.VISION_LEVEL_NORMAL}}
triggers={{name="TR",condition="",action="never_execute"}}
function never_execute() error("trigger must not run in this audit") end
suites={{gadgets={9000},triggers={"TR"}}}''')
        self.assertEqual(report['evaluatedGroups'], 1)
        self.assertEqual(report['checkedPositiveEntityIds'], {'gadgets': 1})
        self.assertEqual(report['issues'], [])
        self.assertIn('Common/Shared.lua', report['files'])
        self.assertFalse(report['legacySourceIsNative72'])
        self.assertFalse(report['resourceReady'])
        self.assertEqual(report['runtimeStatus'], 'not-tested')
        self.assertEqual(summarize(report)['evaluatedScriptManifest']['commonFiles'], 1)

    def test_missing_target_id_suite_member_and_trigger_function_remain_visible(self):
        with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as directory:
            report = self.run_fixture(directory, '''gadgets={{config_id=1,gadget_id=8}}
garbages={gadgets={{config_id=3,gadget_id=9}}}
triggers={{name="TR",action="not_defined"}}
suites={{gadgets={2},triggers={"TR"}}}''')
        self.assertEqual({row['kind'] for row in report['issues']},
                         {'missing-target-entity', 'missing-suite-member', 'missing-trigger-function'})
        self.assertEqual(next(row['targetId'] for row in report['issues'] if 'targetId' in row), 8)
        missing = {row['targetId']: row['sourceSection'] for row in report['issues'] if 'targetId' in row}
        self.assertEqual(missing, {8: 'gadgets', 9: 'garbages.gadgets'})

    def test_compiled_api_references_ignore_comments_and_strings(self):
        with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as directory:
            report = self.run_fixture(directory, '''-- ScriptLib.CommentOnly()
local text="ScriptLib.StringOnly()"
function unused() ScriptLib.Cn72AuditUnknownMember(1) end''')
        self.assertEqual(report['directScriptLibMemberReferences'], ['Cn72AuditUnknownMember'])
        self.assertEqual(report['missingDirectScriptLibMembers'], ['Cn72AuditUnknownMember'])

    def test_lua_failure_or_missing_common_is_not_replaced_with_success(self):
        for group, common in [('require "Shared"', None), ('error("real top-level failure")', '')]:
            with self.subTest(group=group), tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as directory:
                report = self.run_fixture(directory, group, common)
            self.assertEqual(report['evaluatedGroups'], 0)
            self.assertEqual(report['issues'][0]['kind'], 'lua-evaluation')

    def test_grid_scope_inventories_other_blocks_but_only_evaluates_static_inputs(self):
        with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as directory:
            report = self.run_fixture(directory, 'gadgets={{config_id=1,gadget_id=7}}',
                                      all_static=True, other_block=True)
        self.assertEqual(report['selectedBlocks'], [10, 11])
        self.assertEqual((report['declaredGroups'], report['evaluatedGroups']), (2, 1))
        dynamic = next(row for row in report['groupDeclarations'] if row['id'] == 200)
        self.assertTrue(dynamic['dynamicLoad'])
        self.assertFalse(dynamic['evaluated'])
        self.assertEqual(report['issues'], [])
        self.assertEqual(summarize(report)['groupDeclarationCounts']['dynamicEvaluated'], 0)

    def test_inline_mode_preserves_group_locals_while_fast_require_cannot_see_them(self):
        group = 'local defs={id=7}\nrequire "Shared"\ngadgets={{config_id=1,gadget_id=from_local}}'
        common = 'from_local=defs.id\n'
        for fast in (False, True):
            with self.subTest(fast=fast), tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as directory:
                report = self.run_fixture(directory, group, common, fast_require=fast)
            if fast:
                self.assertEqual(report['evaluatedGroups'], 0)
                self.assertEqual(report['issues'][0]['kind'], 'lua-evaluation')
            else:
                self.assertEqual(report['evaluatedGroups'], 1)
                self.assertEqual(report['issues'], [])
                self.assertEqual(report['checkedPositiveEntityIds'], {'gadgets': 1})


if __name__ == '__main__':
    unittest.main()
