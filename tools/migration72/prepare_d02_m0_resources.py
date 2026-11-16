#!/usr/bin/env python3
"""Prepare the fixed D02-M0 birth resource subset; no runtime state is touched."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

REV = '6cfecfb55f889f8e3b7156adb12809608db4f74b'
PINS = {
 'BinOutput/Common/ConfigGlobalCombat.json': ('13bea26209dc91cd0d0cd2064c6ccb587a3c261b','aa24f33e1a1b39ba6d88dcf94e3300f0ea4cdb2f2d6f6266e8f0137541383cb0'),
 'ExcelBinOutput/AvatarExcelConfigData.json': ('149afd9067efe11306b3498d8a9d73332b5c5b64','91e85487325107b9132ccb45a13ee654ea523d5f1ac72ab4ca638873eea8cb70'),
 'ExcelBinOutput/AvatarSkillDepotExcelConfigData.json': ('d20132d4da823cf0445a616905a6251cdd30ecd1','7e91c34c75387a6926c13710caa1d6829271453224743b2f28cbbc0ebb35acdd'),
 'ExcelBinOutput/AvatarSkillExcelConfigData.json': ('de622160959a3a7842022a9b002e1a93cb306eb6','5258ed90da6372ea9da572748252775deb4089e908bb0a96c9067eced28eb6ec'),
 'ExcelBinOutput/AvatarCurveExcelConfigData.json': ('e3586d6389e3db7c0565dde4b31d639abc37e903','774c6abc425a8c78ea98639d7be95dd351cfb951ca1d4202a190d1c4d52aabc5'),
 'ExcelBinOutput/AvatarPromoteExcelConfigData.json': ('b21e5fc5da79a257f4f9b18ae83a56ccc8544c5c','0784244c57e93ed198e8d10cadd5ea419415bb38bca501cb85af76a7bed6d0d5'),
 'ExcelBinOutput/WeaponExcelConfigData.json': ('0686f5f0682565bf99b3dd66cc09a532260eb152','7829d71be03251c5282b27a8d5a670104e59c9ad31d03ee4dcac9b4a8a639e24'),
 'ExcelBinOutput/WeaponCurveExcelConfigData.json': ('fe8785906838834a62cfedb9d9b9cbc6cebf3d93','2c6a7d45303a86898ac22725d4169cd5b3d7b662e60e86da08191b5ca95d737c'),
 'ExcelBinOutput/WeaponPromoteExcelConfigData.json': ('c80e4c077b644bd17c98d48bec975319890f5830','fc74e20edab093301f41c691998c98966509eca52b2bc56df0e99fc6bd72bddd'),
 'ExcelBinOutput/GadgetExcelConfigData.json': ('5deea852ac4372ebf1d7b593e07b9813a1b14e7f','af66b09f3fcfb511f9f8a49c0d079bfd5bf538d9a6ba3dce8baeddfcb371e9c5'),
 'ExcelBinOutput/SceneExcelConfigData.json': ('41d80bd0169191d1ee9a46f90b861c48ad8371a6','0428ec983cede075bee48de7d46dd398d64b1448f391ca93fc8be11010ca21f5'),
 'ExcelBinOutput/ProudSkillExcelConfigData.json': ('c9e9bae3672c02521058479c4c562d548b44abce','c0083146091ee6968bcf06c5143196eaec4130b0cf105ec0c49bcdc56ae766bc'),
 'BinOutput/Avatar/ConfigAvatar_PlayerGirl.json': ('4ce222f50693318241c28ccc5b158771ab3ab63c','2e17e9f13595cc229deeb7a449387abea9042092fd305a0ed08d528b514db655'),
 'BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json': ('f7f75e1362619a0d33b6069ddcea6a06e4f8c1d2','1d139e020a421bee191a35063c8e5364d760e42d3e6fc1763e31da087eb6c74b'),
 'BinOutput/LevelEntity/ConfigLevelEntity_All.json': ('b1ea5fc4fbf200ca8d80dc1629dc612c9a37a9fb','896e9947105cd07432cc1e8f5c3592c1a2a32654b63a5b65a84f6ebd949e6e18'),
 'BinOutput/Talent/AvatarTalents/ConfigTalent_Player_Wind.json': ('9f72a8effc4a5f790ed7e784be6705b63ce2c767','32ce31162ede2f1d6ede639a5fc6e2ffdee73d9a14bebff88cb93abcd95d1faf'),
 'Scripts/Scene/3/scene3.lua': ('7c62466cc187024496c90e3991ccf9d8979fb71f','ecc2504788e0a5246fdbda505888a56a53367a403676e230231f4b1771d60e6d'),
 'LICENSE': ('337856c369eb467b1bbf5810ca61cbb8975c6c1f','5653a2bde6fd785bc82cd78fb96dd28c8ebd335e968e5b1a00584c5e0713dda2'),
}
LOCAL = {
 'skill_bridge': ('local/migration72/d02-resume-20261108/r3-mapping/main-third.json','d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e'),
 'gadget': ('local/migration72/d02-resume-20261109/r3-java-gadget-key/approved-lookup-key.private.json','f5601d314c51a6417aeb899d26c80cfe2ecdf490c5ff869de2b11ab09fca7398'),
 'avatar': ('local/migration72/d02-resume-20261110/c-birth-avatar-join/main-third.json','2ba724156e1716b81a129b81eb3a6333a0be069e8df28dba20257a7cce9cb3a6'),
 'depot': ('local/migration72/d02-resume-20261110/c-depot-talents-implementation/first2.private.json','ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f'),
 'equipment': ('local/migration72/d02-resume-20261108/r3-equipment/main-third.json','50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc'),
 'promote': ('local/migration72/d02-resume-20261108/r3-promote/main-third.json','546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1'),
}
ROOT = Path(__file__).resolve().parents[2]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def one(rows, predicate, label):
    found = [row for row in rows if predicate(row)]
    if len(found) != 1:
        raise ValueError(f'{label}: expected one row, got {len(found)}')
    return found[0]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_inputs(repo):
    sources = {}
    for path, (blob, sha) in PINS.items():
        obj = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', f'{REV}:{path}']).decode().strip()
        data = subprocess.check_output(['git', '-C', str(repo), 'show', f'{REV}:{path}'])
        if obj != blob or digest(data) != sha:
            raise ValueError(f'fixed source mismatch: {path}')
        sources[path] = data
    local = {}
    for name, (path, sha) in LOCAL.items():
        data = (ROOT / path).read_bytes()
        if digest(data) != sha:
            raise ValueError(f'fixed target fragment mismatch: {path}')
        local[name] = json.loads(data)
    return sources, local


def build(sources, local):
    parsed = {p: json.loads(b) for p, b in sources.items() if p.endswith('.json')}
    excel = lambda name: parsed['ExcelBinOutput/' + name + 'ExcelConfigData.json']
    row = lambda name, key, value: one(excel(name), lambda x: x.get(key) == value, f'{name}:{value}')
    avatar = row('Avatar', 'id', 10000007).copy()
    avatar.update(one(local['avatar'], lambda x: x['id'] == 10000007, 'target avatar'))
    depot = row('AvatarSkillDepot', 'id', 701).copy()
    depot.update(one(local['depot']['mergedDepots'], lambda x: x['id'] == 701, 'target depot'))
    skills = [100550, 10035, 10101, 10001, 10102, 5007010]
    skill_rows = [row('AvatarSkill', 'id', i).copy() for i in skills]
    skill_rows[0].update(one(local['skill_bridge']['javaProjectionFragments'], lambda x: x['table'] == 'AvatarSkill' and x['key'] == 100550, 'target birth skill')['javaFields'])
    equipment = local['equipment']['javaProjectionFragments']
    weapon = row('Weapon', 'id', 11101).copy()
    weapon.update(one(equipment, lambda x: x['table'] == 'Weapon' and x['key'] == 11101, 'target weapon')['javaFields'])
    projection = local['promote']['javaProjection']
    avatar_curve = one(projection['completeAvatarCurveInputRows'], lambda x: x['key'] == 1, 'target avatar curve')['javaFields']
    weapon_curve = one(equipment, lambda x: x['table'] == 'WeaponCurve' and x['key'] == 1, 'target weapon curve')['javaFields']
    promote_rows = projection['completeBirthPromoteInputRows']
    avatar_promote = one(excel('AvatarPromote'), lambda x: x.get('avatarPromoteId') == 12 and x.get('promoteLevel', 0) == 0, 'avatar promote').copy()
    avatar_promote.update(one(promote_rows, lambda x: x['table'] == 'AvatarPromote' and x['key'] == 3072, 'target avatar promote')['javaFields'])
    weapon_promote = one(excel('WeaponPromote'), lambda x: x.get('weaponPromoteId') == 11101 and x.get('promoteLevel', 0) == 0, 'weapon promote').copy()
    weapon_promote.update(one(promote_rows, lambda x: x['table'] == 'WeaponPromote' and x['key'] == 2841856, 'target weapon promote')['javaFields'])
    group = 'Avatar_Player_AbilityGroup_Girl_Common'
    group_data = parsed['BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json'][group]
    config = parsed['BinOutput/Avatar/ConfigAvatar_PlayerGirl.json']
    proud = row('ProudSkill', 'proudSkillId', 73101)
    talent = parsed['BinOutput/Talent/AvatarTalents/ConfigTalent_Player_Wind.json']
    open_config = proud['openConfig']
    require(depot['skillDepotAbilityGroup'] == group and depot['skills'] == [100550, 0, 0, 0], 'depot/group mismatch')
    require(sorted(depot['subSkills']) == sorted(skills[1:]) and len(config['abilities']) == 5 and all(x.get('abilityName') for x in config['abilities']) and len(group_data['targetAbilities']) == 1 and group_data['targetAbilities'][0]['abilityName'] == 'Avatar_PlayerGirl_ExtraAttack_Common', 'skill/base/group abilities mismatch')
    require(skill_rows[0]['proudSkillGroupId'] == 731 and len(talent[open_config]) == 25, 'candidate proud reference mismatch')
    require(avatar['skillDepotId'] == 701 and avatar['initialWeapon'] == 11101, 'avatar references mismatch')
    require(weapon['gadgetId'] == 50011101 and weapon['weaponPromoteId'] == 11101, 'weapon references mismatch')
    require(all(any(c['type'] == t for c in curves['curveInfos']) for curves, names in [(avatar_curve, ['GROW_CURVE_HP_S4','GROW_CURVE_ATTACK_S4']), (weapon_curve, ['GROW_CURVE_ATTACK_101','GROW_CURVE_CRITICAL_101'])] for t in names), 'required level 1 curve missing')
    gadget = row('Gadget', 'id', 50011101).copy()
    gadget.update(one(local['gadget']['javaProjectionFragments'], lambda x: x['id'] == 50011101, 'target gadget')['fields'])
    scene = row('Scene', 'id', 3)
    level = parsed['BinOutput/LevelEntity/ConfigLevelEntity_All.json']
    require(scene['levelEntityConfig'] == 'Level_BigWorld' and 'Level_BigWorld' in level, 'scene level entity mismatch')
    rows = {'Avatar': [avatar], 'AvatarSkillDepot': [depot], 'AvatarSkill': skill_rows,
        'AvatarCurve': [avatar_curve], 'AvatarPromote': [avatar_promote], 'Weapon': [weapon],
        'WeaponCurve': [weapon_curve], 'WeaponPromote': [weapon_promote],
        'Gadget': [gadget], 'Scene': [scene], 'ProudSkill': [proud]}
    output = {'ExcelBinOutput/' + k + 'ExcelConfigData.json': v for k, v in rows.items()}
    output.update({'BinOutput/Avatar/ConfigAvatar_PlayerGirl.json': config,
        'BinOutput/Common/ConfigGlobalCombat.json': {'defaultAbilities': {
            name: [] for name in ['nonHumanoidMoveAbilities', 'levelDefaultAbilities',
                'levelElementAbilities', 'levelItemAbilities', 'levelSBuffAbilities',
                'defaultMPLevelAbilities', 'defaultAvatarAbilities', 'defaultTeamAbilities']}},
        'BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json': {group: group_data},
        'BinOutput/LevelEntity/ConfigLevelEntity_Level_BigWorld.json': {'Level_BigWorld': level['Level_BigWorld']},
        'BinOutput/Talent/AvatarTalents/ConfigTalent_Player_Wind.json': {open_config: talent[open_config]}})
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance-root', required=True, type=Path)
    parser.add_argument('--source-repo', required=True, type=Path, help='local AstaPS-Resource git repository')
    args = parser.parse_args()
    sources, local = read_inputs(args.source_repo)
    output = build(sources, local)
    resource_root = args.instance_root / 'resources'
    encoded = {path: (json.dumps(obj, ensure_ascii=False, indent=2) + '\n').encode('utf-8') for path, obj in output.items()}
    encoded['Scripts/Scene/3/scene3.lua'] = sources['Scripts/Scene/3/scene3.lua']
    encoded['D02-M0-AstaPS-Resource-LICENSE.txt'] = sources['LICENSE']
    manifest = {'schema': 'd02-m0-resources-v1', 'source': {'repository': 'https://github.com/MeChen618/AstaPS-Resource', 'commit': REV, 'files': {p: {'blob': b, 'sha256': s, 'classification': 'upstream-candidate'} for p, (b, s) in PINS.items()}},
      'target_fragments': {name: {'path': path, 'sha256': sha, 'classification': 'target-verified specified fields only'} for name, (path, sha) in LOCAL.items()},
      'merge': {'Avatar': 'target avatar fields override candidate row', 'AvatarSkillDepot': 'target skills/energy/talents override candidate row', 'AvatarSkill': 'target 100550 id/costElemType override candidate row; five subskills remain candidate', 'Weapon': 'target 11101 weapon fields and ordered properties override candidate row', 'Gadget': 'target ID and jsonName override candidate row', 'AvatarCurve/WeaponCurve': 'target level 1 projection', 'AvatarPromote/WeaponPromote': 'target phase 0 fields override candidate row', 'other': 'upstream-candidate, including Scene, group, skills and configs'},
      'server_provisional': [{'path': 'BinOutput/Common/ConfigGlobalCombat.json', 'policy': 'M0 adds no optional global MP/team/level abilities; five base avatar abilities remain. Only server object identity validation; client required ability wire is unresolved.', 'candidate_fields': 'fixed candidate is obfuscated; no defaultAbilities mapping is claimed'}], 'outputs': {path: digest(data) for path, data in sorted(encoded.items())},
      'limits': ['questing=true, depot 701 only', 'Scene3 Lua alone is not a complete scene script set', 'not proof of target revision, full loader, persistence or client entry']}
    encoded['D02-M0-MANIFEST.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    for path, data in encoded.items():
        dest = resource_root / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    print(f'Prepared {len(encoded)} files in {resource_root}; see D02-M0-MANIFEST.json')


if __name__ == '__main__':
    main()
