import copy
import datetime as dt
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import audit_d02_quest303_natural_rebind as audit


class Natural303RebindTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = json.loads(audit.FIXTURE.read_text(encoding='utf-8'))

    def test_fixed_jar_sources_classes_inputs_and_owned_fixture(self):
        pins = audit.verify()
        self.assertEqual(pins['baseline']['jar_sha256'], audit.JAR_HASH)
        self.assertEqual(pins['baseline']['source_count'], 31)
        self.assertEqual(pins['baseline']['class_count'], 96)
        self.assertEqual(len(pins['restore_sources']), 46)
        self.assertEqual(len(pins['fixture_child_ids']), 63)
        self.assertEqual(pins['fixture_bytes'], 17211)
        self.assertEqual(len(audit.INPUTS), 3)

    def test_rejects_source_pin_drift(self):
        pins = dict(audit.SOURCE_PINS)
        pins['src/main/java/emu/grasscutter/game/quest/QuestManager.java'] = '0' * 64
        with patch.object(audit, 'SOURCE_PINS', pins):
            with self.assertRaisesRegex(ValueError, 'restore source drift'):
                audit.verify()

    def test_fixed_database_boundary_before_any_prepare(self):
        database = 'lunagc_d02_quest303_natural_' + '1' * 32
        audit.owned_database(audit.URI, database)
        for uri, name in [('mongodb://127.0.0.1:27017', database),
                          ('mongodb://localhost:27027', database),
                          ('mongodb://remote:27027', database),
                          (audit.URI, 'grasscutter'), (audit.URI, database + '_reuse')]:
            with self.subTest(uri=uri, database=name), patch.object(audit, 'verify') as verify:
                with self.assertRaises(ValueError):
                    audit.prepare(uri, name)
                verify.assert_not_called()

    def test_fixture_rejects_wrong_owner_missing_and_inconsistent_children(self):
        self.assertEqual(len(audit.fixture_shape(self.fixture)), 63)
        mutations = [lambda d: d.update(ownerUid=210002),
                     lambda d: d['childQuests'].pop('30302'),
                     lambda d: d['childQuests']['30302'].update(mainQuestId=999),
                     lambda d: d['childQuests']['30302'].update(subQuestId=30303),
                     lambda d: d['childQuests']['30302'].update(finishProgressList=[1]),
                     lambda d: d['childQuests']['30303'].update(state='QUEST_STATE_UNFINISHED'),
                     lambda d: d['childQuests']['30302'].update(failConditionKeys=[])]
        for mutate in mutations:
            document = copy.deepcopy(self.fixture)
            mutate(document)
            with self.assertRaises(ValueError):
                audit.fixture_shape(document)

    def test_database_init_allowed_but_real_service_init_rejected(self):
        path = audit.ROOT / 'local/migration72/d02-r3-quest303-replay-873511c485744e10b8dad1d75e481df8/class-init.log'
        text = path.read_text(encoding='utf-8', errors='replace')
        names = ['com/mongodb/client/MongoClients', 'emu/grasscutter/database/DatabaseManager',
                 'emu/grasscutter/server/game/GameServer']
        lines = [next(line for line in text.splitlines() if "Initializing '" + name + "'" in line)
                 for name in names]
        self.assertEqual(audit.forbidden_lines('\n'.join(lines)), [lines[2]])
        self.assertEqual(audit.forbidden_lines("[class,init] Start class verification for: emu/grasscutter/server/game/GameServer"), [])

    def test_only_exact_independent_run_paths(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            run = base / 'run-20261109-010203-0123abcd'
            run.mkdir()
            with patch.object(audit, 'BASE', base):
                self.assertEqual(audit.resolve_run(run), run.resolve())
                for path in (base, base / 'old-run', base.parent / run.name):
                    with self.assertRaisesRegex(ValueError, 'outside designated'):
                        audit.resolve_run(path)

    def test_fresh_main_preflight_and_accurate_pid_database_rejections(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest = {'run': str(root), 'mongo_uri': audit.URI,
                        'database': 'lunagc_d02_quest303_natural_' + '2' * 32,
                        'mongo_dbpath': str(root / 'mongo-data')}
            proof = dict(manifest, mongo_pid=123, mongo_27027_pid=123,
                         mongo_identity_verified=True, project_game_http_dispatch_plugins_absent=True,
                         http_8088_listeners=[], game_22101_listeners=[],
                         observed_utc=dt.datetime.now(dt.timezone.utc).isoformat())
            path = root / 'main-preflight.private.json'
            path.write_text(json.dumps(proof), encoding='utf-8')
            self.assertEqual(audit.validate_preflight(path, manifest)['proof'], proof)
            for key, value in [('mongo_27027_pid', 999), ('database', 'grasscutter'),
                               ('project_game_http_dispatch_plugins_absent', False),
                               ('http_8088_listeners', [123]), ('observed_utc', '2000-01-01T00:00:00Z')]:
                path.write_text(json.dumps(dict(proof, **{key: value})), encoding='utf-8')
                with self.assertRaises(ValueError):
                    audit.validate_preflight(path, manifest)

    def test_local_pass_requires_actual_restore_and_retains_global_false(self):
        result = {'status': 'EXITED', 'compile_exit': 0, 'exit': 0, 'forbidden_class_init': [],
                  'protected_changed': {}, 'changed_work': {'temp/hsperfdata_User': [None, '<directory>']},
                  'removed_work': []}
        driver = {'outcome': 'PASSED_LOCAL_303_REBIND', 'loadAllCalls': 1, 'initialLoadedAll': False,
                  'loadedAll': True, 'loadAllReturned': True, 'localNecessaryResources': True,
                  'globalResourceReady': False, 'unreadyGatePassed': True, 'restoredChildren': 63,
                  'newObjectBindingsPassed': True, 'packetsAtBoundary': 0,
                  'explicitSaveProjectionPreserved': True, 'managerRestoreProjectionPreserved': True,
                  'databaseRetained': True, 'mongoClientClosed': True}
        self.assertTrue(audit.run_ok(result, driver))
        for key, value in [('outcome', 'RETURNED'), ('loadAllCalls', 2), ('initialLoadedAll', True),
                           ('localNecessaryResources', False), ('globalResourceReady', True),
                           ('restoredChildren', 62), ('newObjectBindingsPassed', False),
                           ('managerRestoreProjectionPreserved', False), ('packetsAtBoundary', 1),
                           ('cleanupFailure', 'timeout')]:
            self.assertFalse(audit.run_ok(result, dict(driver, **{key: value})))
        for key, value in [('exit', 1), ('forbidden_class_init', ['GameServer']),
                           ('protected_changed', {'config': ['a', 'b']}),
                           ('changed_work', {'resources/extra.json': [None, 'hash']})]:
            self.assertFalse(audit.run_ok(dict(result, **{key: value}), driver))

    def test_diagnostic_attribution_keeps_known_rejections_and_omission_scope(self):
        driver = {'questExcelRejected': 17814,
                  'diagnostics': [{'phase': 'LOADALL', 'level': 'ERROR'},
                                  {'phase': 'LOADALL', 'level': 'WARN'},
                                  {'phase': 'UNREADY_MANAGER_GATE', 'level': 'WARN'}]}
        summary = audit.diagnostics_summary(driver)
        self.assertEqual(summary['by_phase']['LOADALL'], {'WARN': 1, 'ERROR': 1})
        self.assertEqual(summary['quest_excel_rejected'], 17814)
        self.assertFalse(summary['global_resource_ready'])

    def test_driver_has_no_manual_ready_or_gameplay_shortcut(self):
        source = audit.DRIVER.read_text(encoding='utf-8')
        self.assertEqual(source.count('ResourceLoader.loadAll();'), 1)
        self.assertNotIn('getDeclaredField("loadedAll")', source)
        for forbidden in ('new GameServer(', 'new World(', '.onLogin(', 'quest.start(', 'child.start(',
                          'ConfigGlobalCombat', 'DatabaseManager.initialize(', '.drop('):
            self.assertNotIn(forbidden, source)
        self.assertIn('source.save();', source)
        self.assertIn('restored.getQuestManager().loadFromDatabase();', source)
        self.assertIn('child.getQuestData() == GameData.getQuestDataMap()', source)


if __name__ == '__main__':
    unittest.main()
