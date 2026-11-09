import unittest
import tempfile
from pathlib import Path
from tools import audit_d02_natural_load_first as audit
class NaturalLoadAuditTests(unittest.TestCase):
    def test_real_jdk_class_initialization_logs(self):
        sources=[audit.ROOT/'local/migration72/d02-r3-quest303-replay-873511c485744e10b8dad1d75e481df8/class-init.log',audit.ROOT/'local/migration72/d02-http-route-main-ff5c99f982d04df6a01df914672941b7/class-init.log',audit.ROOT/'local/migration72/d02-r3-grid-replay/suite/class-init.log']
        text='\n'.join(p.read_text(encoding='utf-8',errors='replace') for p in sources)
        names=['com/mongodb/client/MongoClients','emu/grasscutter/database/DatabaseManager','emu/grasscutter/server/game/GameServer','emu/grasscutter/server/http/HttpServer','emu/grasscutter/scripts/ScriptLoader']
        lines=[next(line for line in text.splitlines() if "Initializing '"+name+"'" in line) for name in names]
        # No actual DispatchServer initialization was found; preserve the JDK format as a synthetic contrast.
        lines.append("[1.000s][info][class,init] 1 Initializing 'emu/grasscutter/server/dispatch/DispatchServer' (0x0000)")
        hits=audit.forbidden_lines('\n'.join(lines))
        self.assertEqual(len(hits),5)
        self.assertNotIn(lines[4],hits)
    def test_only_initialization_not_verification(self):
        text="[class,init] Start class verification for: emu/grasscutter/database/DatabaseManager\n[class,init] 1 Initializing 'emu/grasscutter/database/DatabaseManager'(no method) (0x123)"
        self.assertEqual(len(audit.forbidden_lines(text)),1)
    def test_fixed_inputs_distinct_and_exact(self):
        self.assertEqual(len(audit.INPUTS),3)
        self.assertEqual(len({x[1] for x in audit.INPUTS}),3)
        self.assertTrue(all(len(x[2])==64 for x in audit.INPUTS))
    def test_inventory_sees_extra_script_and_empty_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); (root/'Scene').mkdir(); before=audit.inventory(root)
            (root/'Scene'/'groups_replacement.lua').write_text('unexpected')
            (root/'extra').mkdir(); after=audit.inventory(root)
            self.assertIn('Scene'+__import__('os').sep+'groups_replacement.lua',after)
            self.assertIn('extra',after); self.assertNotEqual(before,after)
    def test_exit_status_requires_complete_return_and_unchanged_inputs(self):
        result={'status':'EXITED','compile_exit':0,'exit':0,'forbidden_class_init':[], 'protected_changed':{}, 'changed_work':{}, 'removed_work':[]}
        self.assertTrue(audit.run_ok(result,{'outcome':'RETURNED'}))
        for outcome in ['THREW',None]: self.assertFalse(audit.run_ok(result,{'outcome':outcome}))
        self.assertFalse(audit.run_ok(result,None))
        self.assertFalse(audit.run_ok(result,{'outcome':'RETURNED','observationFailure':'stack'}))
        for key,value in [('status','TIMEOUT'),('status','COMPILE_FAILED'),('status','FORBIDDEN_CLASS_INIT'),('exit',1),('changed_work',{'x':[None,'hash']}),('protected_changed',{'x':['a','b']})]:
            modified=dict(result); modified[key]=value; self.assertFalse(audit.run_ok(modified,{'outcome':'RETURNED'}))
    def test_first_run_temp_fixture_and_input_changes(self):
        # Minimal redacted shape from the frozen first-run result; no local path is retained.
        result={'status':'EXITED','compile_exit':0,'exit':0,'forbidden_class_init':[],
                'protected_changed':{},'changed_work':{'temp\\hsperfdata_User':[None,'<directory>']},'removed_work':[]}
        driver={'outcome':'RETURNED'}
        self.assertTrue(audit.run_ok(result,driver))
        for path,change in [('resources\\ExcelBinOutput\\QuestExcelConfigData.json',['old','new']),
                            ('config.json',['old','new']),('extra',[None,'<directory>'])]:
            modified=dict(result); modified['changed_work']=dict(result['changed_work'],**{path:change})
            self.assertFalse(audit.run_ok(modified,driver))
        modified=dict(result); modified['protected_changed']={'protected':['old','new']}
        self.assertFalse(audit.run_ok(modified,driver))
        modified=dict(result); modified['removed_work']=['resources']
        self.assertFalse(audit.run_ok(modified,driver))
    def test_compile_log_marks_run_used(self):
        with tempfile.TemporaryDirectory() as folder:
            run=Path(folder); (run/'classes').mkdir(); (run/'compile.stderr.log').write_text('failed')
            with self.assertRaisesRegex(ValueError,'run already used'): audit.execute(run)
            self.assertFalse((run/'result.json').exists())
    def test_refuses_pin_mismatch(self):
        with self.assertRaisesRegex(ValueError,'drift'): audit.check(False,'source drift')
if __name__=='__main__': unittest.main()
