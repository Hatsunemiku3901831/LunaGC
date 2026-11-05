from pathlib import Path
import tempfile
import unittest

from tools.audit_scene_script_api72 import (java_surface, literal_string, read_inputs, reachable_files,
                                           registered_surface, scan_lua, tokens, verify_sources, REVIEWED_SOURCES,
                                           USERDATA, WRAPPER_GROUP)


class SceneScriptApiTests(unittest.TestCase):
    def test_comments_quoted_and_long_strings_cannot_create_api_sites(self):
        source = '''-- ScriptLib.Comment()
--[==[ ScriptLib.LongComment() ]==]
local a="ScriptLib.String() \\""
local b='ScriptLib.Single()'
local c=[=[ScriptLib.LongString()]=]
ScriptLib.Real(context)
'''
        # An escaped quote remains inside the quoted token; it does not expose a fake call.
        source = source.replace('local a="ScriptLib.String() \\""', 'local a="ScriptLib.String() \\" escaped"')
        result = scan_lua(source)
        self.assertEqual([row['member'] for row in result['sites']], ['Real'])
        self.assertEqual(result['sites'][0]['line'], 6)
        self.assertEqual(result['sites'][0]['source'], 'ScriptLib.Real(context)')

    def test_long_bracket_equals_depth_and_line_column_offsets(self):
        source = '--[=[ ignored ]] ScriptLib.Bad() ]=]\r\n  ScriptLib . Good {x=1}\n'
        site = scan_lua(source)['sites'][0]
        self.assertEqual((site['member'], site['line'], site['column'], site['use']), ('Good', 2, 3, 'call-syntax'))
        self.assertEqual(source[site['start']:site['end']], 'ScriptLib . Good')

    def test_call_syntax_member_values_declarations_colon_and_constant_brackets(self):
        result = scan_lua('''local f=ScriptLib.Stored
ScriptLib.Call(context)
ScriptLib:Colon(context)
ScriptLib["Bracket"] "argument"
function ScriptLib.Declared() end
ScriptLib[ [=[LongName]=] ]({})''')
        self.assertEqual([(row['member'], row['use']) for row in result['sites']],
                         [('Stored', 'member-value'), ('Call', 'call-syntax'), ('Colon', 'call-syntax'),
                          ('Bracket', 'call-syntax'), ('Declared', 'member-declaration'), ('LongName', 'call-syntax')])
        self.assertEqual(result['sites'][2]['accessSyntax'], 'colon')

    def test_dynamic_keys_aliases_shadows_and_escaped_keys_remain_open(self):
        result = scan_lua('''local lib=ScriptLib
lib.Hidden(context)
ScriptLib[name](context)
ScriptLib["Esc\\097ped"](context)
local ScriptLib={}
ScriptLib.Shadowed(context)''')
        self.assertEqual([row['member'] for row in result['sites']], ['Shadowed'])
        self.assertEqual([row['kind'] for row in result['boundaries']],
                         ['ScriptLib-value-or-shadow-boundary', 'dynamic-member', 'dynamic-member', 'ScriptLib-value-or-shadow-boundary'])
        # This tool intentionally does not promote the shadowed lexical site to a resolved global.
        self.assertEqual(result['sites'][0]['use'], 'call-syntax')

    def test_static_require_graph_is_separate_from_dynamic_require(self):
        scans = {'Scene/3/scene3_group1.lua': scan_lua('require "A"\nrequire(name)'),
                 'Common/A.lua': scan_lua('require([=[B]=])\nScriptLib.Shared()'),
                 'Common/B.lua': scan_lua('require "A"\nrequire "Missing"')}
        reachable, unresolved = reachable_files('Scene/3/scene3_group1.lua', scans)
        self.assertEqual(reachable, set(scans))
        self.assertEqual(unresolved, [{'file': 'Common/B.lua', 'line': 2, 'target': 'Common/Missing.lua'}])
        self.assertEqual(scans['Scene/3/scene3_group1.lua']['boundaries'][0]['kind'], 'dynamic-require')

    def test_unterminated_literals_rejected_and_long_strings_keep_literal_content(self):
        for source in ('--[=[ unfinished', 'local a="unfinished', 'local a=[==[unfinished'):
            with self.subTest(source=source), self.assertRaisesRegex(ValueError, 'Unterminated'):
                tokens(source)
        self.assertEqual(literal_string(tokens('[=[\nValue]=]')[0]), 'Value')
        self.assertIsNone(literal_string(tokens('"escaped\\n"')[0]))

    def test_jdk_parse_reads_public_methods_without_loading_static_initializers(self):
        repo = Path(__file__).resolve().parents[1]
        java = next((repo / 'local/runtime').glob('jdk-17*'))
        with tempfile.TemporaryDirectory(prefix='scene-script-api-test-', dir=repo / 'local/migration72/analysis') as folder:
            root = Path(folder)
            source = root / 'ApiFixture.java'
            source.write_text('''class ApiFixture {
static { if (true) throw new IllegalStateException("must never initialize"); }
// public int CommentOnly() { return 0; }
private String hidden() { return "public int StringOnly() {}"; }
public int Actual(int value) { return helper(value); }
private int helper(int value) { return value; }
}''', 'utf8')
            classes = root / 'classes'
            classes.mkdir()
            rows = java_surface(repo, java, classes, [source.relative_to(repo).as_posix()])
        self.assertEqual([row['name'] for row in rows if row['public']], ['Actual'])
        actual = next(row for row in rows if row['name'] == 'Actual')
        self.assertEqual(actual['calls'], ['helper'])
        self.assertEqual(actual['line'], 5)
        self.assertEqual(actual['signature'], 'public int Actual(int value)')

    def test_source_and_parent_report_drift_fail_before_audit(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            name = next(iter(REVIEWED_SOURCES))
            (root / name).parent.mkdir(parents=True)
            (root / name).write_text('changed', 'utf8')
            with self.assertRaisesRegex(ValueError, 'changed API dependency'):
                verify_sources(root)
            report, summary = root / 'report.json', root / 'summary.json'
            report.write_text('{}', 'utf8')
            summary.write_text('{}', 'utf8')
            with self.assertRaisesRegex(ValueError, 'report/summary changed'):
                read_inputs(root, root, report, summary)

    def test_reviewed_registration_keys_exclude_other_method_string_literals(self):
        def method(file, name, source):
            return dict(file=file, owner='Fixture', name=name, line=1, signature=name,
                        methodSHA256='test', logsUnimplemented=False, source=source)
        methods = [method(USERDATA, 'get', '''timerFunctions.get(key); tideFunctions.get(key);
groupFunctions.get(key); sceneBattleFunctions.get(key); switch(key) {case "Direct": return direct;}''')]
        for file in (WRAPPER_GROUP, 'src/main/java/emu/grasscutter/scripts/GroupTimerLua.java'):
            methods.append(method(file, 'functions', 'return Map.of("Registered", function(true));'))
        for file in ('src/main/java/emu/grasscutter/scripts/MonsterTideLua.java',
                     'src/main/java/emu/grasscutter/scripts/ScenePlayBattleLua.java'):
            methods.append(method(file, 'functions', 'for(var n:new String[]{"ArrayRegistered"}) { if(n.startsWith("Get")) log("FakeKey"); }'))
        self.assertEqual(set(registered_surface(methods)), {'Direct', 'Registered', 'ArrayRegistered'})


if __name__ == '__main__':
    unittest.main()
