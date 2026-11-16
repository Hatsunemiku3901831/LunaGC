#!/usr/bin/env python3
"""Prepare an offline, incomplete-until-proven old-snapshot resource bundle."""
import argparse
import hashlib
import json
import os
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REV = '6cfecfb55f889f8e3b7156adb12809608db4f74b'
SNAP = 'local/migration72/d02-resume-20261110b/old-account-snapshot/run-20261110-2/'
PINS = {
 SNAP+'selected-manifest.private.json':'9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b',
 SNAP+'selected-dependencies.private.json':'3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7',
 SNAP+'export/grasscutter/selected-0/account.bson':'19558a3cb414b78d668fcda7f4ed01bc599f729f722ebf6954ea8536e566ee1c',
 SNAP+'export/grasscutter/selected-0/player.bson':'95774a40f97ae3daacbf7a7b8602185889e8a3ca7ac3195fad8d5805f2147a43',
 SNAP+'export/grasscutter/selected-0/avatars-0.bson':'273fa466d17e504d2eef2abb00b175afe3b4aa50f339d622b4614e828c3afc5a',
 SNAP+'export/grasscutter/selected-0/items-0.bson':'563fa1ed07c4fc92722f1b723259ac7a0e79ddc12a718b300e4557e7828712dd',
 'local/migration72/d02-resume-20261116/active-item-saved-refs/result.private.json':'42f16eb1b83016f47a61668402c644dc58f167352d2d85f1b06d08001197e457',
 'local/instances/d02-old-restore/evidence/weapon-saved-affix-refinement.private.json':'a2f9637b36b26ba2f76bb92ef55b2afffbd34883afa076da97b078df1cdc9bed',
 'local/instances/d02-old-restore/evidence/explicit-asset-fetch-receipt.json':'14090f883d8bcb5ad768fca50cf97da78237bc8d217132532cbd319794e90d53',
 'local/instances/d02-old-restore/evidence/explicit-player-config-fetch-receipt.json':'8acea64712c3107fb0ee7e63c7eeca0d963f4a67f93e91abefc94def388aee5e',
 'local/instances/d02-old-restore/evidence/explicit-equip-config-fetch-receipt.json':'0c5fc87c5513a1b94864b7996efce121d975dfa66305070f3a8c947d4ace4280',
 'local/migration72/d02-resume-20261115/old121-avatar-base-growth/projection.private.json':'db4a4e6774de9a18e1ff88a515b905a48735bb5c6b5cec5c0a095766fd743bcc',
 'local/migration72/d02-resume-20261115/old121-avatar-critical/projection.private.json':'961ff8f670175f55d9d775a17e906a9d434029fda88b4902558bc0f7ba8969af',
 'local/migration72/d02-resume-20261116/old121-depot-core-fragment/projection.private.json':'24b008df69e853d1739fe408634c77698340c8b69294f8344718aa82f108d654',
 'debug/d02-skill-energy-search-20261116/special-start-host-batch/result.private.json':'9d4b69d00a438084cd9a10b6b4eacda5618eaeebdab735a3bfe38cd3c4e755cb',
 'local/migration72/d02-resume-20261111-asc/c-old-promote-fields/main-third.private.json':'38c8d66b08ba8743b49a0bafaab88713494a82a3b84a3de94f3da22f84170a54',
 'local/migration72/d02-resume-20261110b/c-old-items/WeaponExcelConfigData.json':'7829d71be03251c5282b27a8d5a670104e59c9ad31d03ee4dcac9b4a8a639e24',
 'local/migration72/d02-resume-20261110b/c-old-items/ReliquaryExcelConfigData.json':'00efb17747cfea347ac7d95bf381c8607c0de7d4887bfd41051841b04c7b5e85',
 'local/migration72/d02-resume-20261110b/c-old-items/MaterialExcelConfigData.json':'c2898b0f2a22870f831544033c92021fa2017475cbaf3bb277278019574a0b0b',
 'local/migration72/d02-resume-20261110b/c-old-items/HomeWorldFurnitureExcelConfigData.json':'afe28eca0e44aa02f137c725421748ae63d5653b3495dd88aee837bd21a6e012',
 'local/migration72/d02-resume-20261110b/c-old-items/tps-candidate/TpsWeaponExcelConfigData.json':'1134be51680369087c229f42347396a696077f4b64740c412b9dab8ab3716daf',
 'local/migration72/d02-resume-20261116/fixed-saved-ref-candidate-blobs/candidate/ReliquaryMainPropExcelConfigData.json':'692c4423e4b716356831d141bc19bd9f8abab06fc6bb18a38e1e2596763e7a2f',
 'local/migration72/d02-resume-20261116/fixed-saved-ref-candidate-blobs/candidate/ReliquaryAffixExcelConfigData.json':'a841807665f4f55ec6a711ac51ece684616d387b9b0e59c4cfdfa190d1d8b64d',
 'local/migration72/d02-resume-20261116/fixed-saved-ref-candidate-blobs/candidate/TpsWeaponAccessoryExcelConfigData.json':'df6840076cd9de5da7f5ef3774fe7404fc272b110f194a6759e358625ccb8cfe',
}
FETCH = {
 'FetterInfo':('db2de9c0ab0c72feab903dcdba0d3b0d5a431f69','4fff8d7cf99b589dde95d72379e4491fbd13dab51e0c927df904f2ce3d875b21'),
 'Fetters':('dac56b51189f95a600862d21295a0d980e976948','bc0d586174e5df8b65087b946eb7e290ded147e720370386ebfc289beac012a9'),
 'FetterStory':('27886e75582c7490582be458be9b7e2fe539c84c','36e450af0f1698d1df8a34fb73a4739beb957ba98db3c6cdf1cecf8b8aa92679'),
 'PhotographExpression':('adedbaa223628865e878e153a60d07239f73a5d0','583d339e0a885949987e716727c31b25c45fa87d7a8c5de0d98d2d548573960b'),
 'PhotographPosename':('6a60ad56806738c663e5726787f3a13d35e4341d','e91949d7ac31a07c686426d2410461bad47e829d6111d62f6062adecc43806f1'),
 'FetterCharacterCard':('f0a912a49f61a670863114d6fe2effd7f3d3968d','bdb305db908af0f5da5268d5dfa774fdfa6949672a218636fe09e7b7982eeeb9'),
 'Reward':('8deed64c67dd17354398020d83a1aac1563dc678','59425e3cea42b0de7b7cf8b406b22d702792a51559143bec191527fe0d017e1f'),
 'AvatarTalent':('86701f32b7d3251602bf20dd20ae9c9e3c3683b2','ea2c4b8899867eefe497eac82553b04f975ddfaf3dcce3620ae5adb731bac3be'),
 'ReliquaryLevel':('f1ba31891e6207fc9c499369e05ecf211f230f0d','a5b7f25f523b557c1f261e7112ec7b85fa17634fd1544b25fdf1969f0da4f8e1'),
 'ReliquarySet':('ce02f01d0c664bd93be15d4be70dcf1c6ad3e72e','9cee1d3a08fed513346157906723070f011c80fe21ed3249140ba3886888926d'),
 'EquipAffix':('355996dedf261ec03586e1639a99bc8295e96d6e','397c2870fa34a8e6173b53d0edb4a947c1285b1eb0e709734089028fefc0828a'),
}
GIT = {
 'Avatar':('149afd9067efe11306b3498d8a9d73332b5c5b64','91e85487325107b9132ccb45a13ee654ea523d5f1ac72ab4ca638873eea8cb70'),
 'AvatarSkillDepot':('d20132d4da823cf0445a616905a6251cdd30ecd1','7e91c34c75387a6926c13710caa1d6829271453224743b2f28cbbc0ebb35acdd'),
 'AvatarSkill':('de622160959a3a7842022a9b002e1a93cb306eb6','5258ed90da6372ea9da572748252775deb4089e908bb0a96c9067eced28eb6ec'),
 'AvatarCurve':('e3586d6389e3db7c0565dde4b31d639abc37e903','774c6abc425a8c78ea98639d7be95dd351cfb951ca1d4202a190d1c4d52aabc5'),
 'AvatarPromote':('b21e5fc5da79a257f4f9b18ae83a56ccc8544c5c','0784244c57e93ed198e8d10cadd5ea419415bb38bca501cb85af76a7bed6d0d5'),
 'WeaponCurve':('fe8785906838834a62cfedb9d9b9cbc6cebf3d93','2c6a7d45303a86898ac22725d4169cd5b3d7b662e60e86da08191b5ca95d737c'),
 'WeaponPromote':('c80e4c077b644bd17c98d48bec975319890f5830','fc74e20edab093301f41c691998c98966509eca52b2bc56df0e99fc6bd72bddd'),
 'ProudSkill':('c9e9bae3672c02521058479c4c562d548b44abce','c0083146091ee6968bcf06c5143196eaec4130b0cf105ec0c49bcdc56ae766bc'),
 'Gadget':('5deea852ac4372ebf1d7b593e07b9813a1b14e7f','af66b09f3fcfb511f9f8a49c0d079bfd5bf538d9a6ba3dce8baeddfcb371e9c5'),
}
IDENTITY_FILES = {
 'ExcelBinOutput/SceneExcelConfigData.json':'a37fce943a2a52025f02dfcbe86dbe25c878daf6d909bf308be6eb8788b5362a',
 'BinOutput/Common/ConfigGlobalCombat.json':'7192c58d9170fa6890760be74372f5fcac55dcf8785b93f5268c034c66b6549e',
 'BinOutput/LevelEntity/ConfigLevelEntity_Level_BigWorld.json':'fc5ac3306bc51ce9687799136694ee49ca56e6c64658f7cf993962726edf43c3',
}
LOCAL_NAMES = {
 'Weapon':'local/migration72/d02-resume-20261110b/c-old-items/WeaponExcelConfigData.json',
 'Reliquary':'local/migration72/d02-resume-20261110b/c-old-items/ReliquaryExcelConfigData.json',
 'Material':'local/migration72/d02-resume-20261110b/c-old-items/MaterialExcelConfigData.json',
 'HomeWorldFurniture':'local/migration72/d02-resume-20261110b/c-old-items/HomeWorldFurnitureExcelConfigData.json',
 'TpsWeapon':'local/migration72/d02-resume-20261110b/c-old-items/tps-candidate/TpsWeaponExcelConfigData.json',
 **{n:'local/migration72/d02-resume-20261116/fixed-saved-ref-candidate-blobs/candidate/'+n+'ExcelConfigData.json' for n in ('ReliquaryMainProp','ReliquaryAffix','TpsWeaponAccessory')},
}

def sha(data): return hashlib.sha256(data).hexdigest()
def blob(data): return hashlib.sha1(f'blob {len(data)}\0'.encode()+data).hexdigest()
def excel(name): return f'ExcelBinOutput/{name}ExcelConfigData.json'
def encoded(obj): return (json.dumps(obj,ensure_ascii=False,indent=2)+'\n').encode()

def load_inputs(repo, evidence):
    fixed = {}
    for path, expected in PINS.items():
        data=(ROOT/path).read_bytes()
        if sha(data)!=expected: raise ValueError(f'fixed input SHA mismatch: {path}')
        if path.endswith('.json'): fixed[path]=json.loads(data)
    env=dict(os.environ,GIT_NO_LAZY_FETCH='1',GIT_TERMINAL_PROMPT='0')
    sources={}; metadata={}
    for name,(expected_blob,expected_sha) in {**GIT,**FETCH}.items():
        path=excel(name)
        actual=subprocess.check_output(['git','-C',str(repo),'rev-parse',f'{REV}:{path}'],env=env).decode().strip()
        if actual!=expected_blob: raise ValueError(f'tree blob mismatch: {path}')
        data=(evidence/path).read_bytes() if name in FETCH else subprocess.check_output(['git','-C',str(repo),'show',f'{REV}:{path}'],env=env)
        if sha(data)!=expected_sha or blob(data)!=expected_blob: raise ValueError(f'candidate mismatch: {path}')
        sources[name]=json.loads(data);metadata[path]={'blob':actual,'sha256':sha(data),'classification':'upstream-candidate','source':'explicit fixed-commit raw fetch' if name in FETCH else 'fixed commit git show'}
    for name,path in LOCAL_NAMES.items():
        data=(ROOT/path).read_bytes(); remote=excel(name)
        actual=subprocess.check_output(['git','-C',str(repo),'rev-parse',f'{REV}:{remote}'],env=env).decode().strip()
        if blob(data)!=actual: raise ValueError(f'local candidate does not match tree: {path}')
        sources[name]=json.loads(data);metadata[remote]={'blob':actual,'sha256':sha(data),'classification':'upstream-candidate','source':'fixed local candidate'}
    assets={}
    for receipt in ('explicit-asset-fetch-receipt.json','explicit-player-config-fetch-receipt.json','explicit-equip-config-fetch-receipt.json'):
        for entry in fixed['local/instances/d02-old-restore/evidence/'+receipt]:
            path=entry['path']; data=(evidence/path).read_bytes()
            actual=subprocess.check_output(['git','-C',str(repo),'rev-parse',f'{REV}:{path}'],env=env).decode().strip()
            if entry.get('error') or actual!=entry['blob'] or blob(data)!=actual or sha(data)!=entry['sha256']: raise ValueError(f'asset candidate mismatch: {path}')
            assets[path]=data;metadata[path]={'blob':actual,'sha256':sha(data),'classification':'upstream-candidate','source':'explicit fixed-commit raw fetch'}
    for path,expected_sha in IDENTITY_FILES.items():
        data=(ROOT/'local/instances/d02-m0/resources'/path).read_bytes()
        if sha(data)!=expected_sha:raise ValueError(f'fixed scene identity input changed: {path}')
        assets[path]=data
        metadata[path]={'sha256':expected_sha,'classification':'server-provisional' if 'GlobalCombat' in path else 'fixed-M0-identity-input','source':'audited M0 input copied without modification; optional globals not appended'}
    license_data=subprocess.check_output(['git','-C',str(repo),'show',f'{REV}:LICENSE'],env=env)
    if sha(license_data)!='5653a2bde6fd785bc82cd78fb96dd28c8ebd335e968e5b1a00584c5e0713dda2': raise ValueError('license mismatch')
    return fixed,sources,metadata,license_data,assets

def build(fixed, source, assets):
    dep=fixed[SNAP+'selected-dependencies.private.json']; active_refs=fixed['local/migration72/d02-resume-20261116/active-item-saved-refs/result.private.json']
    avatars=[x['fields'] for x in dep['avatars']];items=[x['fields'] for x in dep['items']];active=[x for x in items if x['deleted'] is not True and x['count']>0]
    if (len(avatars),len(items),len(active),sum(bool(x['equipCharacter']) for x in active))!=(121,12254,10727,471): raise ValueError('snapshot cohort counts changed')
    gaps=[]; selected={}; summaries={}
    def select(name,key,ids):
        idx=defaultdict(list)
        for row in source[name]: idx[row.get(key,0)].append(row)
        result=[]
        for value in sorted(ids):
            rows=idx.get(value,[])
            if len(rows)!=1: gaps.append({'kind':'missing-or-duplicate-row','table':name,'key':value,'matches':len(rows)})
            else: result.append(rows[0].copy())
        selected[name]=result
        summaries[name]={'requested':sorted(ids),'found':len(result)}
        return result
    avatar_ids={x['avatarId'] for x in avatars}; depot_ids={x['skillDepotId'] for x in avatars}
    for name in ('FetterInfo','Fetters','FetterStory','PhotographExpression','PhotographPosename','FetterCharacterCard'):
        selected[name]=[row.copy() for row in source[name] if row.get('avatarId') in avatar_ids]
        summaries[name]={'avatarIds':sorted(avatar_ids),'found':len(selected[name])}
    select('Reward','rewardId',{row['rewardId'] for row in selected['FetterCharacterCard'] if row.get('rewardId')})
    av=select('Avatar','id',avatar_ids); depot=select('AvatarSkillDepot','id',depot_ids)
    for table,path,member in [('Avatar','local/migration72/d02-resume-20261115/old121-avatar-base-growth/projection.private.json','fragments'),('Avatar','local/migration72/d02-resume-20261115/old121-avatar-critical/projection.private.json','fragments'),('AvatarSkillDepot','local/migration72/d02-resume-20261116/old121-depot-core-fragment/projection.private.json','fragments')]:
        overrides={x['key']:x['javaFields'] for x in fixed[path][member]}
        for row in selected[table]:
            if row['id'] not in overrides: gaps.append({'kind':'missing-target-overlay','table':table,'key':row['id']})
            else: row.update(overrides[row['id']])
    saved=fixed['local/migration72/d02-resume-20261116/old121-depot-core-fragment/projection.private.json']['savedCurrentDepotPairs']
    if {(x['avatarId'],x['skillDepotId']) for x in avatars}!={(x['avatarId'],x['savedCurrentDepotId']) for x in saved}: raise ValueError('saved depot pairs differ')
    skills={int(k) for a in avatars for k in a['skillLevelMap']}
    for row in depot: skills.update(x for x in row.get('skills',[])+row.get('subSkills',[])+[row.get('energySkill',0)] if x)
    skillrows=select('AvatarSkill','id',skills)
    over={x['key']:x['javaFields'] for x in fixed['debug/d02-skill-energy-search-20261116/special-start-host-batch/result.private.json']['javaProjectionFragments']}
    for row in skillrows:
        if row['id'] in over: row.update(over[row['id']])
    skillmap={x['id']:x for x in skillrows}
    for skill_id,cap in ((11065,200),(11145,100)):
        if skill_id not in skillmap or skillmap[skill_id].get('specialEnergyMin') is not None:raise ValueError('special cap candidate changed')
        skillmap[skill_id]['specialEnergyMin']=cap
    select('AvatarTalent','talentId',{x for a in avatars for x in a['talentIdList'] if x})
    skill_by_id={x['id']:x for x in skillrows}
    proud_ids={x for a in avatars for x in a['proudSkillList'] if x}
    proud_ids.update(skill_by_id[int(k)].get('proudSkillGroupId',0)*100+v for a in avatars for k,v in a['skillLevelMap'].items() if int(k) in skill_by_id and skill_by_id[int(k)].get('proudSkillGroupId',0)>0)
    for row in depot:
        proud_ids.update(x['proudSkillGroupId']*100+1 for x in row.get('inherentProudSkillOpens',[]) if x.get('proudSkillGroupId'))
        proud_ids.update(x*100+1 for x in row.get('questProudSkillGroupIds',[]) if x)
    # Group 7639's fixed candidate uses 7633xx IDs despite declaring group 7639.
    # Saved skill 10765 level 10 can use that same group's existing level-10 effect.
    matches=[x for x in source['ProudSkill'] if x.get('proudSkillGroupId')==7639 and x.get('level')==10]
    if len(matches)!=1 or matches[0]['proudSkillId']!=763310:raise ValueError('7639 provisional source differs')
    source['ProudSkill']=source['ProudSkill']+[dict(matches[0],proudSkillId=763910)]
    select('ProudSkill','proudSkillId',proud_ids)
    select('AvatarCurve','level',set(range(1,101)))
    for row in selected['Avatar']:
        for c in row.get('propGrowCurves',[]):
            for curve in selected['AvatarCurve']:
                if not any(x.get('type')==c.get('growCurve') for x in curve['curveInfos']): gaps.append({'kind':'missing-avatar-curve','avatarId':row['id'],'curve':c['growCurve'],'level':curve['level']})
    groups={x.get('avatarPromoteId') for x in av}; phases={(x,phase) for x in groups for phase in (0,6)}
    idx=defaultdict(list)
    for r in source['AvatarPromote']:idx[(r.get('avatarPromoteId'),r.get('promoteLevel',0))].append(r)
    selected['AvatarPromote']=[]
    for key in sorted(phases):
        if len(idx[key])!=1:gaps.append({'kind':'missing-or-duplicate-promote','table':'AvatarPromote','key':key,'matches':len(idx[key])})
        else:selected['AvatarPromote'].append(idx[key][0].copy())
    promotefrag=fixed['local/migration72/d02-resume-20261111-asc/c-old-promote-fields/main-third.private.json']['rows']
    pro={(r['group'],r['phase']):r['projection']['javaFields'] for r in promotefrag}
    for row in selected['AvatarPromote']:
        key=(row['avatarPromoteId'],row.get('promoteLevel',0))
        if key[1]==6:
            if key not in pro:gaps.append({'kind':'missing-target-promote','key':key})
            else:row.update(pro[key])
    indexes={}
    for family in ('Weapon','Reliquary','Material','HomeWorldFurniture','TpsWeapon'):
        idx=defaultdict(list)
        for row in source[family]:idx[row.get('id')].append(row)
        indexes[family]=idx
    classes=defaultdict(set);dist=Counter(); selected_equipped=Counter()
    for item in active:
        matches=[(family, row) for family,idx in indexes.items() for row in idx.get(item['itemId'],[])]
        if len(matches)!=1:gaps.append({'kind':'item-family-missing-or-ambiguous','id':item['itemId'],'matches':[m[0] for m in matches]})
        else:
            family,_=matches[0];classes[family].add(item['itemId']);dist[family]+=1
            if item['equipCharacter']:selected_equipped[family]+=1
    for family in indexes:select(family,'id',classes[family])
    equipped=defaultdict(list)
    for item in active:
        if item['equipCharacter']:
            if item['equipCharacter'] not in avatar_ids:gaps.append({'kind':'unknown-equipped-avatar','itemId':item['itemId'],'avatarId':item['equipCharacter']})
            matches=[row for idx in indexes.values() for row in idx.get(item['itemId'],[])]
            if len(matches)==1:equipped[item['equipCharacter']].append((item['itemId'],'EQUIP_WEAPON' if matches[0].get('itemType')=='ITEM_WEAPON' else matches[0].get('equipType')))
    for avatar_id in sorted(avatar_ids):
        slots=[slot for _,slot in equipped[avatar_id]]
        if slots.count('EQUIP_WEAPON')!=1 or len(slots)!=len(set(slots)):gaps.append({'kind':'equipped-slot-conflict','avatarId':avatar_id,'slots':slots})
    selected_ids={x['id'] for x in selected['Weapon']}
    for row in av:
        if row.get('initialWeapon') not in selected_ids:gaps.append({'kind':'missing-initial-weapon','avatarId':row['id'],'weaponId':row.get('initialWeapon')})
    gadget_ids={row['gadgetId'] for family in ('Weapon','TpsWeapon') for row in selected[family] if row.get('gadgetId')}
    select('Gadget','id',gadget_ids)
    equip_levels={x['level'] for x in active if x['itemId'] in classes['Weapon']}
    select('WeaponCurve','level',equip_levels|{1})
    for row in selected['Weapon']:
        for prop in row.get('weaponProp',[]):
            for curve in selected['WeaponCurve']:
                if prop.get('type') and not any(x.get('type')==prop['type'] for x in curve['curveInfos']): gaps.append({'kind':'missing-weapon-curve','weaponId':row['id'],'curve':prop['type'],'level':curve['level']})
    weapon_by_id={x['id']:x for x in selected['Weapon']}; wkeys={(weapon_by_id[x['itemId']]['weaponPromoteId'],x['promoteLevel']) for x in active if x['itemId'] in weapon_by_id}|{(x['weaponPromoteId'],0) for x in selected['Weapon']}
    idx=defaultdict(list)
    for r in source['WeaponPromote']:idx[(r.get('weaponPromoteId'),r.get('promoteLevel',0))].append(r)
    selected['WeaponPromote']=[]
    for key in sorted(wkeys):
        if len(idx[key])!=1:gaps.append({'kind':'missing-or-duplicate-promote','table':'WeaponPromote','key':key,'matches':len(idx[key])})
        else:selected['WeaponPromote'].append(idx[key][0])
    relic_by_id={x['id']:x for x in selected['Reliquary']}
    rkeys={(relic_by_id[x['itemId']]['rankLevel'],x['level']) for x in active if x['itemId'] in relic_by_id}
    idx=defaultdict(list)
    for r in source['ReliquaryLevel']:idx[(r.get('rank',0),r['level'])].append(r)
    selected['ReliquaryLevel']=[]
    for key in sorted(rkeys):
        if len(idx[key])!=1:gaps.append({'kind':'missing-or-duplicate-relic-level','key':key,'matches':len(idx[key])})
        else:selected['ReliquaryLevel'].append(idx[key][0])
    sets={x.get('setId',0) for x in selected['Reliquary']}-{0}; setrows=select('ReliquarySet','setId',sets)
    affixes={r.get('EquipAffixId',0)*10+i for r in setrows for i in range(len(r.get('setNeedNum',[])))}-{0}
    saved_weapons=fixed['local/instances/d02-old-restore/evidence/weapon-saved-affix-refinement.private.json']['items']
    if len(saved_weapons)!=dist['Weapon']:gaps.append({'kind':'saved-weapon-cohort-mismatch','expected':len(saved_weapons),'classified':dist['Weapon']})
    affixes.update(af*10+x['fields']['refinement']['value'] for x in saved_weapons for af in x['fields']['affixes']['value'] if af)
    idx=defaultdict(list)
    for row in source['EquipAffix']: idx[row['affixId']].append(row)
    selected['EquipAffix']=[]; provisional=[]
    allowed={1114124,1114164,1114194,1114204,1114214,1114294,1154154}
    for key in sorted(affixes):
        rows=idx.get(key,[])
        if len(rows)==1:selected['EquipAffix'].append(rows[0])
        elif not rows and key in allowed and len(idx.get((key//10)*10,[]))==1:
            base=idx[(key//10)*10][0]
            if base['id']!=key//10 or base.get('level',0)!=0:raise ValueError('provisional source group mismatch')
            row=base.copy();row['affixId']=key;row['level']=key%10;selected['EquipAffix'].append(row)
            provisional.append({'key':key,'sourceKey':base['affixId'],'policy':'saved refinement uses same group level-0 effect; server-defined'})
        else:gaps.append({'kind':'missing-or-duplicate-row','table':'EquipAffix','key':key,'matches':len(rows)})
    summaries['EquipAffix']={'requested':sorted(affixes),'found':len(selected['EquipAffix']),'serverProvisional':provisional}
    positive=active_refs['positiveReferences']
    for name,key in [('ReliquaryMainProp','ReliquaryCandidateItems.mainPropId'),('ReliquaryAffix','ReliquaryCandidateItems.appendPropIdList'),('TpsWeaponAccessory','TPSCandidateItems.tpsAccessoryIds')]:select(name,'id',set(positive[key]['uniquePositiveIds']))
    if positive['TPSCandidateItems.tpsAccessoryIds']['uniquePositiveIds']!=positive['TPSCandidateItems.tpsMaterialAccessoryIds']['uniquePositiveIds']:gaps.append({'kind':'tps-material-accessory-references-differ'})
    asset_json={path:json.loads(data) for path,data in assets.items()}
    group=asset_json['BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json']
    for row in depot:
        name=row.get('skillDepotAbilityGroup')
        if name and name not in group:gaps.append({'kind':'missing-ability-group','depotId':row['id'],'name':name})
    for row in av:
        name='AetherShadow' if row['id']==10000134 else 'LumineShadow' if row['id']==10000135 else row['iconName'].split('_')[-1]
        path=f'BinOutput/Avatar/ConfigAvatar_{name}.json'
        if path not in assets:gaps.append({'kind':'missing-avatar-config','avatarId':row['id'],'path':path})
    openconfigs=defaultdict(list)
    for path,content in asset_json.items():
        if path.startswith('BinOutput/Talent/AvatarTalents/'):
            for key in content:openconfigs[key].append(path)
    for row in selected['ProudSkill']+selected['AvatarTalent']:
        name=row.get('openConfig')
        if name and len(openconfigs[name])!=1:gaps.append({'kind':'missing-or-ambiguous-avatar-openconfig','name':name,'matches':openconfigs[name]})
    equipconfigs=defaultdict(list)
    for path,content in asset_json.items():
        if path.startswith(('BinOutput/Talent/EquipTalents/','BinOutput/Talent/RelicTalents/')):
            for key in content:equipconfigs[key].append(path)
    for row in selected['EquipAffix']:
        name=row.get('openConfig')
        if name and len(equipconfigs[name])!=1:gaps.append({'kind':'missing-or-ambiguous-equip-openconfig','key':row['affixId'],'name':name,'matches':equipconfigs[name]})
    provisional.extend([{'table':'AvatarSkill','key':11065,'field':'specialEnergyMin','value':200,'policy':'Mavuika server cap'}, {'table':'AvatarSkill','key':11145,'field':'specialEnergyMin','value':100,'policy':'Skirk server cap'}])
    provisional.append({'table':'ProudSkill','key':763910,'sourceKey':763310,'group':7639,'sourceLevel':10,'avatarId':10000076,'skillId':10765,'policy':'saved level 10 uses existing same-group candidate effect; server-defined'})
    gaps.append({'kind':'not-runtime-validated','detail':'real ResourceLoader and Player/AvatarStorage/Inventory cold save/load have not run; candidate client asset identities are not target-verified'})
    item_ledger=[{'documentIndex':i,'id':x['_id'],'ownerId':x['ownerId'],'itemId':x['itemId'],'active':x['deleted'] is not True and x['count']>0,'equipCharacter':x['equipCharacter'],'families':[family for family,idx in indexes.items() if x['itemId'] in idx]} for i,x in enumerate(items)]
    avatar_ledger=[{'documentIndex':i,'id':x['_id'],'ownerId':x['ownerId'],'avatarId':x['avatarId'],'skillDepotId':x['skillDepotId'],'level':x['level'],'promoteLevel':x['promoteLevel']} for i,x in enumerate(avatars)]
    if len({x['_id']['$oid'] for x in items})!=len(items) or len({x['_id']['$oid'] for x in avatars})!=len(avatars):raise ValueError('duplicate snapshot identity')
    report={'avatarLedger':avatar_ledger,'itemLedger':item_ledger,'counts':{'avatars':len(avatars),'itemsTotal':len(items),'activeItems':len(active),'inactiveItems':len(items)-len(active),'equipped':sum(selected_equipped.values()),'activeFamilyCounts':dict(dist),'equippedFamilyCounts':dict(selected_equipped)},'avatarLevelPhase':dict(Counter(f"{x['level']}/{x['promoteLevel']}" for x in avatars)),'activeItemLevelPhase':dict(Counter(f"{x['level']}/{x['promoteLevel']}" for x in active)),'weaponLevelPhase':sorted({(x['level'],x['promoteLevel']) for x in active if x['itemId'] in classes['Weapon']}),'relicRankLevel':sorted(rkeys),'selected':summaries,'serverProvisional':provisional,'gaps':gaps,'sourceProjectionOrderPreserved':True,'ready':False}
    return selected,report

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source-repo',type=Path,required=True);parser.add_argument('--instance-root',type=Path,required=True);parser.add_argument('--bundle-name',required=True);args=parser.parse_args()
    expected=(ROOT/'local/instances/d02-old-restore').resolve()
    if args.instance_root.resolve()!=expected:raise ValueError('instance root must be local/instances/d02-old-restore')
    if not args.bundle_name or not all(c.isalnum() or c in '-_' for c in args.bundle_name):raise ValueError('invalid bundle name')
    output=expected/'resources'/args.bundle_name
    if output.exists():raise FileExistsError(f'refusing to overwrite {output}')
    fixed,source,metadata,license_data,assets=load_inputs(args.source_repo,expected/'evidence')
    selected,report=build(fixed,source,assets)
    output.mkdir(parents=True)
    hashes={}
    for name,rows in selected.items():
        path=excel(name);data=encoded(rows);dest=output/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);hashes[path]=sha(data)
    for path,data in assets.items():
        dest=output/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);hashes[path]=sha(data)
    (output/'D02-OLD-AstaPS-Resource-LICENSE.txt').write_bytes(license_data)
    hashes['D02-OLD-AstaPS-Resource-LICENSE.txt']=sha(license_data)
    manifest={'schema':'d02-old-snapshot-data-v1','ready':False,'repository':'https://github.com/MeChen618/AstaPS-Resource','commit':REV,'tree':'3cf83488a94b47a018934e5f91c1bf7e93357aae','license':'MIT; game data and third-party rights separately assessed','inputs':PINS,'candidateSources':metadata,'targetOverlay':'Avatar base/growth and critical; current Depot skills/energy; Skill available id/element/cost/START; AvatarPromote phase6 typed javaFields only','serverProvisional':report['serverProvisional'],'outputs':hashes,'report':report,'limits':['offline partial bundle; not a loader or client acceptance','all active rows represented in report; original documents are not modified','no ready file; do not hydrate until all gaps and actual loader gate are addressed']}
    (output/'D02-OLD-RESOURCE-MANIFEST.json').write_bytes(encoded(manifest))
    print(json.dumps({'ready':False,'counts':report['counts'],'gapCount':len(report['gaps']),'output':str(output)},ensure_ascii=False))
if __name__=='__main__':main()
