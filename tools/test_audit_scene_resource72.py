import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.audit_resource72 import TABLES, git_hash
from tools import audit_scene_resource72 as scene


def tree(entries):
    ordered = sorted(entries, key=lambda r: r['path'].encode() + (b'/' if r['type'] == 'tree' else b''))
    payload = b''.join(r['mode'].lstrip('0').encode() + b' ' + r['path'].encode()
                       + b'\0' + bytes.fromhex(r['sha']) for r in ordered)
    return dict(sha=git_hash('tree', payload), truncated=False, tree=ordered)


def child(name, data):
    return dict(path=name, mode='040000', type='tree', sha=data['sha'])


class SceneResourceTests(unittest.TestCase):
    def test_points_keep_unknown_fields_and_accept_partial_vectors(self):
        raw = b'{"points":{"0":{"pos":{"y":2},"NEW_UNKNOWN":17},"2147483647":{"unlocked":false}}}'
        document, points, projected = scene.index_points(raw)
        self.assertEqual(points[0]['NEW_UNKNOWN'], 17)
        self.assertEqual(projected[0], {'pos': {'y': 2}})
        self.assertIn(2147483647, points)
        self.assertEqual(document['points']['0']['pos'], {'y': 2})

    def test_point_map_rejects_duplicate_keys_ids_and_invalid_shapes(self):
        for raw in (b'[]', b'{"points":[]}', b'{"points":{"1":{},"1":{}}}',
                    b'{"points":{"01":{}}}', b'{"points":{"-1":{}}}',
                    b'{"points":{"2147483648":{}}}', b'{"points":{"1":null}}',
                    b'{"points":{"1":{"gadgetId":true}}}',
                    b'{"points":{"1":{"dungeonIds":[1,"2"]}}}',
                    b'{"points":{"1":{"pos":{"x":NaN}}}}',
                    b'{"points":{"1":{"tranSceneId":null}}}'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                scene.index_points(raw)

    def test_reviewed_aliases_work_but_collisions_are_not_silently_resolved(self):
        raw = b'{"points":{"1":{"JHHFPGJNMIN":[9],"maxSpringVolume":5,"PHBHHNDGFCF":7}}}'
        with self.assertRaisesRegex(ValueError, 'Ambiguous'):
            scene.index_points(raw)
        # Baseline-only raw comparison does not claim to validate old aliases.
        self.assertEqual(scene.index_points(raw, review_fields=False)[1][1]['PHBHHNDGFCF'], 7)
        _, _, projected = scene.index_points(b'{"points":{"1":{"JHHFPGJNMIN":[9]}}}')
        self.assertEqual(projected, {1: {'dungeonIds': [9]}})

    def test_positive_references_follow_all_referenced_weekdays_and_preserve_missing(self):
        points = {1: dict(gadgetId=7, tranSceneId=3, dungeonIds=[9, 0], dungeonRandomList=[4]),
                  2: dict(gadgetId=8, dungeonRandomList=[0])}
        tables = {'GadgetExcelConfigData.json': {7: {}}, 'SceneExcelConfigData.json': {3: {}},
                  'DungeonExcelConfigData.json': {9: {}},
                  'DailyDungeonConfigData.json': {4: dict(monday=[9], sunday=[10])}}
        result = scene.point_references(points, tables)
        self.assertEqual(result['checkedPositiveReferences'], 7)
        self.assertEqual(result['referencedDailySchedules'], [4])
        self.assertEqual([r['targetId'] for r in result['missing']], [8, 10])
        tables['DailyDungeonConfigData.json'][4]['monday'] = [True]
        with self.assertRaises(ValueError):
            scene.point_references(points, tables)

    def fixture(self, directory):
        root = Path(directory)
        source, baseline, repository = (root / name for name in ('source', 'baseline', 'repository'))
        entries = []
        data = {name: [{key: i} for i in required or (1,)] for name, (key, required) in TABLES.items()}
        data.update({'GadgetExcelConfigData.json': [{'id': 7}], 'DungeonExcelConfigData.json': [{'id': 9}],
                     'DailyDungeonConfigData.json': [{'id': 4, 'monday': [9]}],
                     'SceneExcelConfigData.json': [{'id': 3, 'scriptData': 'uninterpreted'}]})
        for name, rows in data.items():
            raw = json.dumps(rows).encode()
            for target in (source, baseline):
                p = target / 'ExcelBinOutput' / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(raw)
            entries.append(dict(path=name, mode='100644', type='blob', size=len(raw), sha=git_hash('blob', raw)))
        raw = b'{"points":{"1":{"gadgetId":7,"tranSceneId":3,"dungeonIds":[9],"dungeonRandomList":[4],"UNKNOWN":2}}}'
        for target in (source, baseline):
            p = target / scene.POINT_FILE
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(raw)
        point_tree = tree([dict(path='scene3_point.json', mode='100644', type='blob', size=len(raw), sha=git_hash('blob', raw))])
        scene_tree = tree([child('Point', point_tree)])
        bin_tree = tree([child('Scene', scene_tree)])
        excel_tree = tree(entries)
        root_tree = tree([child('BinOutput', bin_tree), child('ExcelBinOutput', excel_tree)])
        trees = dict(root=root_tree, bin=bin_tree, scene=scene_tree, point=point_tree, excel=excel_tree)
        commit = dict(commit=scene.COMMIT, treeSHA=root_tree['sha'], repository='https://github.com/DimbreathBot/AnimeGameData',
                      versionLabel='CNRELWin7.2.0_R1_S2_D3')
        revisions = {k: dict(text='1', sha256='synthetic') for k in ('res_revision', 'data_revision', 'silence_revision')}
        repository.mkdir()
        (repository / 'reader.java').write_bytes(b'reviewed\n')
        loaders = {'reader.java': hashlib.sha256(b'reviewed\n').hexdigest()}
        args = (source, baseline, commit, trees, revisions, repository)
        return args, loaders

    def test_end_to_end_tree_chain_and_existing_lua_do_not_promote_readiness(self):
        with tempfile.TemporaryDirectory() as directory:
            args, loaders = self.fixture(directory)
            lua = args[1] / 'Scripts/Scene/3/scene3.lua'
            lua.parent.mkdir(parents=True)
            lua.write_bytes(b'scene_config = {}\n')
            with patch.object(scene, 'ROOT_TREE', args[3]['root']['sha']), patch.object(scene, 'LOADERS', loaders):
                result = scene.audit_scene(*args)
                self.assertEqual(result, scene.audit_scene(*args))
            self.assertEqual(result['references']['checkedPositiveReferences'], 5)
            self.assertEqual(result['references']['missing'], [])
            self.assertEqual(result['fieldsNotConsumedByPointData'], {'UNKNOWN': 1})
            self.assertTrue(result['scripts']['baselineRootPresent'])
            self.assertFalse(result['scripts']['sourceContainsScriptsTree'])
            self.assertFalse(result['resourceReady'])
            self.assertEqual(result['runtimeStatus'], 'not-tested')
            self.assertEqual(scene.summary(result)['pointDifferences']['unchanged'], 1)

    def test_content_chain_loader_and_commit_drift_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            args, loaders = self.fixture(directory)
            with patch.object(scene, 'ROOT_TREE', args[3]['root']['sha']), patch.object(scene, 'LOADERS', loaders):
                path = args[0] / scene.POINT_FILE
                original = path.read_bytes()
                path.write_bytes(original + b' ')
                with self.assertRaisesRegex(ValueError, 'blob mismatch'):
                    scene.audit_scene(*args)
                path.write_bytes(original)
                wrong = copy.deepcopy(args[3])
                wrong['point'] = tree([])
                with self.assertRaisesRegex(ValueError, 'tree chain'):
                    scene.audit_scene(*args[:3], wrong, *args[4:])
                (args[5] / 'reader.java').write_bytes(b'changed\n')
                with self.assertRaisesRegex(ValueError, 'Review changed'):
                    scene.audit_scene(*args)
                (args[5] / 'reader.java').write_bytes(b'reviewed\n')
                args[2]['commit'] = '1' * 40
                with self.assertRaisesRegex(ValueError, 'fixed CN 7.2 commit'):
                    scene.audit_scene(*args)

    def test_fixed_root_cannot_be_replaced_with_a_self_consistent_unrelated_tree(self):
        with tempfile.TemporaryDirectory() as directory:
            args, _ = self.fixture(directory)
            with self.assertRaisesRegex(ValueError, 'fixed CN 7.2 commit'):
                scene.audit_scene(*args)


if __name__ == '__main__':
    unittest.main()
