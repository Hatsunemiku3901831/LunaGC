import json
from pathlib import Path
import tempfile
import unittest
from tools.audit_resource72 import TABLES, audit, read_client_evidence, git_hash, verify_tree, index_table, compare_rows, references


class ResourceAuditTests(unittest.TestCase):
    def test_git_blob_hash_uses_git_object_header(self):
        self.assertEqual(git_hash('blob',b'test\n'),'9daeafb9864cf43055ae93beb0afd6c7d144bfa4')

    def test_tree_binding_checks_content_and_rejects_truncation(self):
        blob=git_hash('blob',b'test\n');payload=b'100644 test.txt\0'+bytes.fromhex(blob)
        tree=dict(sha=git_hash('tree',payload),truncated=False,tree=[dict(path='test.txt',mode='100644',type='blob',sha=blob)])
        self.assertEqual(verify_tree(tree)['test.txt']['sha'],blob)
        tree['truncated']=True
        with self.assertRaises(ValueError):verify_tree(tree)
        tree['truncated']=False;tree['tree'][0]['path']='other.txt'
        with self.assertRaises(ValueError):verify_tree(tree)

    def test_duplicate_ids_object_keys_and_wrong_shape_are_rejected(self):
        for data in (b'{"id":1}',b'[{"id":1},{"id":1}]',b'[{"id":true}]',b'[{"id":1,"id":2}]',b'[{"value":1}]'):
            with self.assertRaises(ValueError):index_table(data,'id')

    def test_stable_id_comparison_ignores_order_and_retains_missing_vs_null(self):
        a=index_table(b'[{"id":1,"a":null},{"id":2,"x":3}]','id')
        b=index_table(b'[{"id":2,"x":4},{"id":1},{"id":3}]','id')
        result=compare_rows(a,b)
        self.assertEqual(result['addedIds'],[3]);self.assertEqual(result['removedIds'],[])
        self.assertEqual(result['changedFieldsById'],{'1':['a'],'2':['x']})
        self.assertEqual(result['sharedValueChangesById'],{'2':['x']})
        self.assertEqual(result['fieldPresenceChangesById'],{'1':{'added':[],'removed':['a']}})
        self.assertEqual(compare_rows(a,dict(reversed(list(a.items()))))['unchanged'],2)

    def test_positive_references_fail_without_inventing_zero_records(self):
        tables={'AvatarExcelConfigData.json':{1:dict(skillDepotId=2,initialWeapon=3)},
                'AvatarSkillDepotExcelConfigData.json':{2:dict(skills=[4,0],energySkill=5)},
                'WeaponExcelConfigData.json':{3:{}},'AvatarSkillExcelConfigData.json':{4:{}}}
        report=references(tables)
        self.assertEqual(report['checkedPositiveReferences'],4)
        self.assertEqual([m['targetId'] for m in report['missing']],[5])
        tables['AvatarSkillDepotExcelConfigData.json'][2]['skills']='4'
        with self.assertRaises(ValueError):references(tables)

    def test_client_channel_and_revision_input_are_read_from_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);config=root/'config.ini';config.write_text('[General]\ngame_version=7.2.0\nchannel=1\nsub_channel=1\n')
            revision_root=root/'YuanShen_Data/StreamingAssets/AssetBundles';revision_root.mkdir(parents=True)
            for name in ('res_revision','data_revision','silence_revision'):(revision_root/name).write_bytes(b'123\n')
            client,revisions=read_client_evidence(root)
            self.assertEqual(client['identity']['game_version'],'7.2.0')
            self.assertEqual(revisions['data_revision']['text'],'123')
            config.write_text('[General]\ngame_version=7.1.0\nchannel=1\nsub_channel=1\n')
            with self.assertRaises(ValueError):read_client_evidence(root)

    def test_audit_binds_source_blob_and_root_without_promoting_compatibility(self):
        def tree(entries):
            payload=b''.join(r['mode'].lstrip('0').encode()+b' '+r['path'].encode()+b'\0'+bytes.fromhex(r['sha']) for r in sorted(entries,key=lambda r:r['path']))
            return dict(sha=git_hash('tree',payload),truncated=False,tree=entries)
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/'source';baseline=Path(directory)/'baseline';entries=[]
            for root in (source,baseline):(root/'ExcelBinOutput').mkdir(parents=True)
            for name,(key,required) in TABLES.items():
                raw=json.dumps([{key:i} for i in required or (1,)]).encode()
                for root in (source,baseline):(root/'ExcelBinOutput'/name).write_bytes(raw)
                entries.append(dict(path=name,mode='100644',type='blob',sha=git_hash('blob',raw),size=len(raw)))
            excel=tree(entries);root=tree([dict(path='ExcelBinOutput',mode='040000',type='tree',sha=excel['sha'])])
            commit=dict(commit='1'*40,treeSHA=root['sha'],repository='https://github.com/DimbreathBot/AnimeGameData',versionLabel='CNRELWin7.2.0_R1_S2_D3')
            revisions={key:dict(text='1',sha256='synthetic') for key in ('res_revision','data_revision','silence_revision')}
            result=audit(source,baseline,commit,root,excel,revisions)
            self.assertFalse(result['resourceReady']);self.assertEqual(result['references']['missing'],[])
            target=source/'ExcelBinOutput/SceneExcelConfigData.json';original=target.read_bytes();target.write_bytes(original+b' ')
            with self.assertRaises(ValueError):audit(source,baseline,commit,root,excel,revisions)
            target.write_bytes(original);commit['treeSHA']='2'*40
            with self.assertRaises(ValueError):audit(source,baseline,commit,root,excel,revisions)


if __name__=='__main__':unittest.main()
