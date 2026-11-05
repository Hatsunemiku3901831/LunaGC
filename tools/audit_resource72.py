"""Read-only audit of a pinned 7.2 resource candidate, never a deployment gate.

Verifies Git tree/blob objects, compares selected tables by stable IDs and checks
selected avatar references. No files are merged, renamed, copied into resources,
or loaded into the running server. Revision/script/client gaps stay explicit.
"""
import argparse
import configparser
import hashlib
import json
from pathlib import Path
import re
import subprocess

TABLES={
    'AvatarExcelConfigData.json':('id',(10000005,10000007)),
    'AvatarSkillDepotExcelConfigData.json':('id',()),
    'AvatarSkillExcelConfigData.json':('id',()),
    'WeaponExcelConfigData.json':('id',()),
    'PlayerLevelExcelConfigData.json':('level',(1,)),
    'SceneExcelConfigData.json':('id',(3,)),
    'WorldLevelExcelConfigData.json':('level',(1,)),
}


def read_client_evidence(client):
    raw=(client/'config.ini').read_bytes();config=configparser.ConfigParser()
    config.read_string(raw.decode('utf-8-sig'))
    identity={key:config.get('General',key,fallback=None) for key in ('game_version','channel','sub_channel')}
    if identity!=dict(game_version='7.2.0',channel='1',sub_channel='1'):
        raise ValueError('Configured client is not the reviewed CN 7.2 channel')
    revisions={}
    for name in ('res_revision','data_revision','silence_revision'):
        data=(client/'YuanShen_Data/StreamingAssets/AssetBundles'/name).read_bytes()
        revisions[name]=dict(text=data.decode('ascii').strip(),sha256=hashlib.sha256(data).hexdigest())
    return dict(identity=identity,configSHA256=hashlib.sha256(raw).hexdigest()),revisions


def git_hash(kind,data):
    return hashlib.sha1(kind.encode()+b' '+str(len(data)).encode()+b'\0'+data).hexdigest()


def verify_tree(tree):
    if tree.get('truncated') is not False or not isinstance(tree.get('tree'),list):
        raise ValueError('Complete nonrecursive Git tree required')
    entries=tree['tree'];names=set();payload=[]
    for row in sorted(entries,key=lambda r:r['path'].encode()+ (b'/' if r.get('type')=='tree' else b'')):
        path=row.get('path');mode=row.get('mode');sha=row.get('sha')
        if (not isinstance(path,str) or not path or '/' in path or '\\' in path or '\0' in path or
                path in ('.','..') or path in names or mode not in ('040000','100644','100755','120000') or
                row.get('type')!=('tree' if mode=='040000' else 'blob') or
                not isinstance(sha,str) or not re.fullmatch('[0-9a-f]{40}',sha)):
            raise ValueError('Invalid/duplicate Git tree entry')
        names.add(path)
        payload.append(mode.lstrip('0').encode()+b' '+path.encode()+b'\0'+bytes.fromhex(sha))
    if git_hash('tree',b''.join(payload))!=tree.get('sha'):
        raise ValueError('Git tree content hash mismatch')
    return {r['path']:r for r in entries}


def unique_object(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate JSON object key')
        result[key]=value
    return result


def index_table(data,key):
    rows=json.loads(data,object_pairs_hook=unique_object)
    if not isinstance(rows,list):raise ValueError('Selected resource table must be an array')
    result={}
    for row in rows:
        if not isinstance(row,dict) or type(row.get(key)) is not int or row[key]<0 or row[key] in result:
            raise ValueError('Missing/non-integer/duplicate stable ID')
        result[row[key]]=row
    return result


def compare_rows(before,after):
    common=set(before)&set(after)
    changed={str(key):sorted(k for k in set(before[key])|set(after[key]) if before[key].get(k)!=after[key].get(k)
                                or (k in before[key])!=(k in after[key]))
             for key in sorted(common) if before[key]!=after[key]}
    shared={str(key):sorted(k for k in set(before[key])&set(after[key]) if before[key][k]!=after[key][k])
            for key in sorted(common) if any(before[key][k]!=after[key][k] for k in set(before[key])&set(after[key]))}
    presence={str(key):dict(added=sorted(set(after[key])-set(before[key])),removed=sorted(set(before[key])-set(after[key])))
              for key in sorted(common) if set(before[key])!=set(after[key])}
    return dict(addedIds=sorted(set(after)-set(before)),removedIds=sorted(set(before)-set(after)),
                changedFieldsById=changed,sharedValueChangesById=shared,fieldPresenceChangesById=presence,
                unchanged=len(common)-len(changed))


def references(tables):
    missing=[];checked=0
    for source,target,field,is_list in (
        ('AvatarExcelConfigData.json','AvatarSkillDepotExcelConfigData.json','skillDepotId',False),
        ('AvatarExcelConfigData.json','WeaponExcelConfigData.json','initialWeapon',False),
        ('AvatarSkillDepotExcelConfigData.json','AvatarSkillExcelConfigData.json','skills',True),
        ('AvatarSkillDepotExcelConfigData.json','AvatarSkillExcelConfigData.json','energySkill',False),
        ('AvatarSkillDepotExcelConfigData.json','AvatarSkillExcelConfigData.json','attackModeSkill',False)):
        for identity,row in tables[source].items():
            value=row.get(field,[] if is_list else 0)
            values=value if is_list else [value]
            if not isinstance(values,list) or any(type(v) is not int or v<0 for v in values):
                raise ValueError('Reference field does not match declared scalar/list schema')
            for value in values:
                if value==0:continue  # Explicit absence sentinel, not a missing positive ID.
                checked+=1
                if value not in tables[target]:missing.append(dict(source=source,id=identity,field=field,target=target,targetId=value))
    return dict(checkedPositiveReferences=checked,missing=missing,
                scope='avatar skillDepotId/initialWeapon and depot skills/energySkill/attackModeSkill only')


def audit(source,baseline,commit,root_tree,excel_tree,revisions):
    if not re.fullmatch('[0-9a-f]{40}',commit.get('commit','')):
        raise ValueError('Pinned upstream commit SHA required')
    match=re.fullmatch(r'CNRELWin7\.2\.0_R(\d+)_S(\d+)_D(\d+)',commit.get('versionLabel',''))
    if not match or commit.get('repository')!='https://github.com/DimbreathBot/AnimeGameData':
        raise ValueError('Reviewed CN 7.2 source/version declaration required')
    root=verify_tree(root_tree);entries=verify_tree(excel_tree)
    if root_tree['sha']!=commit.get('treeSHA') or root.get('ExcelBinOutput',{}).get('sha')!=excel_tree['sha']:
        raise ValueError('Commit/root/Excel tree chain mismatch')
    source_tables={};comparisons=[]
    for name,(key,required) in TABLES.items():
        entry=entries.get(name,{})
        if entry.get('type')!='blob':raise ValueError('Required audited source table absent from pinned tree')
        raw=(source/'ExcelBinOutput'/name).read_bytes();old=(baseline/'ExcelBinOutput'/name).read_bytes()
        if len(raw)!=entry.get('size') or git_hash('blob',raw)!=entry['sha']:
            raise ValueError('Source resource size/Git blob hash mismatch: '+name)
        after=index_table(raw,key);before=index_table(old,key);source_tables[name]=after
        comparisons.append(dict(file=name,stableKey=key,sourceRows=len(after),baselineRows=len(before),
                                sourceSHA256=hashlib.sha256(raw).hexdigest(),baselineSHA256=hashlib.sha256(old).hexdigest(),
                                sourceGitBlob=entry['sha'],missingRequiredIds=sorted(set(required)-set(after)),
                                **compare_rows(before,after)))
    bundled={name:entry['text'] for name,entry in revisions.items()}
    if set(bundled)!={'res_revision','data_revision','silence_revision'} or any(not re.fullmatch(r'\d+',v) for v in bundled.values()):
        raise ValueError('Bundled revision evidence missing or invalid')
    labels=dict(zip(('R','S','D'),match.groups()))
    return dict(schemaVersion=1,upstream=commit,rootTreeSHA=root_tree['sha'],excelTreeSHA=excel_tree['sha'],
                tables=comparisons,references=references(source_tables),
                sourceRevisionLabels=labels,bundledRevisionFiles=revisions,
                revisionValueSetsEqual=set(labels.values())==set(bundled.values()),
                revisionScope='bundled StreamingAssets files only; label-to-runtime hotfix alignment unverified',
                resourceReady=False,scope='pinned candidate/input and selected stable-ID audit; no compatibility or deployment claim',
                gaps=['installed runtime hotfix/revision alignment','other mandatory tables and ID references',
                      'Lua scripts and scene resources','server schema/loader compatibility','real client entry acceptance'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('source','baseline','commit-report','root-tree','excel-tree','client','output','summary-output'):
        p.add_argument('--'+name,required=True,type=Path)
    a=p.parse_args()
    for output in (a.output,a.summary_output):
        if any(output.resolve().is_relative_to(root.resolve()) for root in (a.source,a.baseline,a.client)):
            p.error('Reports must be outside resource/client input trees')
    read=lambda path:json.loads(path.read_text('utf-8-sig'))
    client,revisions=read_client_evidence(a.client)
    report=audit(a.source,a.baseline,*map(read,(a.commit_report,a.root_tree,a.excel_tree)),revisions)
    report['client']=client
    report['baselineGitCommit']=subprocess.run(['git','-C',str(a.baseline),'rev-parse','HEAD'],check=True,capture_output=True,text=True).stdout.strip()
    summary={k:v for k,v in report.items() if k not in ('tables','references')}
    summary['tables']=[{k:v for k,v in table.items() if k not in ('addedIds','removedIds','changedFieldsById','sharedValueChangesById','fieldPresenceChangesById')}
                      |dict(added=len(table['addedIds']),removed=len(table['removedIds']),changed=len(table['changedFieldsById']),
                            sharedValueChanged=len(table['sharedValueChangesById']),fieldPresenceChanged=len(table['fieldPresenceChangesById']))
                      for table in report['tables']]
    summary['references']={k:v for k,v in report['references'].items() if k!='missing'}
    summary['references']['missingCount']=len(report['references']['missing'])
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(tables=len(report['tables']),positiveReferences=report['references']['checkedPositiveReferences'],
                         missingReferences=len(report['references']['missing']),resourceReady=False)))


if __name__=='__main__':main()
