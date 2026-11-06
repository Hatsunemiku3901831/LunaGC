"""Pinned candidate checks for the controlled D-02 starting avatars.

This is an offline, partial reference audit, not a deployment or client gate.
"""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from .audit_resource72 import git_hash, index_table, verify_tree
except ImportError:
    from audit_resource72 import git_hash, index_table, verify_tree

COMMIT = 'd54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'
ROOT = '5b334e711319726c504da95dbfe6ebb26baaa7d3'
FILES = ('AvatarExcelConfigData.json', 'AvatarSkillDepotExcelConfigData.json',
         'AvatarSkillExcelConfigData.json', 'WeaponExcelConfigData.json')
DEPOTS = {10000005: (501, 504), 10000007: (701, 704)}
EXTRA_FILES = ('AvatarCurveExcelConfigData.json', 'AvatarPromoteExcelConfigData.json',
               'AvatarTalentExcelConfigData.json', 'ProudSkillExcelConfigData.json',
               'WeaponCurveExcelConfigData.json', 'WeaponPromoteExcelConfigData.json')


def load_tables(source, root_tree, excel_tree, commit):
    if commit.get('commit') != COMMIT or commit.get('treeSHA') != ROOT:
        raise ValueError('Unexpected candidate commit/tree')
    root = verify_tree(root_tree)
    entries = verify_tree(excel_tree)
    if root_tree['sha'] != ROOT or root['ExcelBinOutput']['sha'] != excel_tree['sha']:
        raise ValueError('Candidate tree chain mismatch')
    tables, hashes = {}, {}
    for name in FILES:
        raw = (source / 'ExcelBinOutput' / name).read_bytes()
        entry = entries[name]
        if len(raw) != entry['size'] or git_hash('blob', raw) != entry['sha']:
            raise ValueError('Candidate blob mismatch: ' + name)
        tables[name] = index_table(raw, 'id')
        hashes[name] = hashlib.sha256(raw).hexdigest()
    return tables, hashes


def audit(tables):
    avatars = tables[FILES[0]]
    depots = tables[FILES[1]]
    skills = tables[FILES[2]]
    weapons = tables[FILES[3]]
    result = []
    for avatar_id, depot_ids in DEPOTS.items():
        avatar = avatars[avatar_id]
        weapon_id = avatar['initialWeapon']
        if type(weapon_id) is not int or weapon_id <= 0 or weapon_id not in weapons:
            raise ValueError(f'Missing starting weapon for {avatar_id}')
        if avatar['skillDepotId'] != depot_ids[0]:
            raise ValueError(f'Unexpected starting depot for {avatar_id}')
        branches = []
        for depot_id in depot_ids:
            depot = depots[depot_id]
            refs = {}
            for field in ('skills', 'subSkills', 'energySkill', 'attackModeSkill'):
                value = depot.get(field, [] if field in ('skills', 'subSkills') else 0)
                values = value if isinstance(value, list) else [value]
                if any(type(v) is not int or v < 0 for v in values):
                    raise ValueError(f'Invalid skill reference {depot_id}:{field}')
                refs[field] = [v for v in values if v > 0]
                missing = [v for v in refs[field] if v not in skills]
                if missing:
                    raise ValueError(f'Missing skills {depot_id}:{field}:{missing}')
            branches.append({'id': depot_id, 'references': refs,
                             'talents': depot.get('talents', []),
                             'inherentProudSkillOpens': depot.get('inherentProudSkillOpens', []),
                             'skillDepotAbilityGroup': depot.get('skillDepotAbilityGroup')})
        result.append({'id': avatar_id, 'startingWeapon': weapon_id,
                       'avatarPromoteId': avatar.get('avatarPromoteId'),
                       'growthCurves': avatar.get('propGrowCurves'), 'depots': branches})
    return result


def load_extra(source, excel_tree):
    entries = verify_tree(excel_tree)
    rows, hashes = {}, {}
    for name in EXTRA_FILES:
        raw = (source / 'ExcelBinOutput' / name).read_bytes()
        entry = entries[name]
        if len(raw) != entry['size'] or git_hash('blob', raw) != entry['sha']:
            raise ValueError('Candidate blob mismatch: ' + name)
        rows[name] = json.loads(raw, object_pairs_hook=lambda pairs: dict(pairs))
        if not isinstance(rows[name], list):
            raise ValueError('Expected array: ' + name)
        hashes[name] = hashlib.sha256(raw).hexdigest()
    return rows, hashes


def audit_extended(tables, extra, gadgets):
    avatar_curve, avatar_promote, talent, proud, weapon_curve, weapon_promote = (
        extra[name] for name in EXTRA_FILES)
    checks = []

    def require(label, matches):
        if len(matches) != 1:
            raise ValueError(f'{label}: expected one row, found {len(matches)}')
        checks.append(label)
        return matches[0]

    avatar_level = require('avatar curve level 1', [r for r in avatar_curve if r.get('level') == 1])
    weapon_level = require('weapon curve level 1', [r for r in weapon_curve if r.get('level') == 1])
    for name in ('GROW_CURVE_HP_S4', 'GROW_CURVE_ATTACK_S4'):
        require('avatar level 1:' + name, [r for r in avatar_level['curveInfos'] if r.get('type') == name])
    for name in ('GROW_CURVE_ATTACK_101', 'GROW_CURVE_CRITICAL_101'):
        require('weapon level 1:' + name, [r for r in weapon_level['curveInfos'] if r.get('type') == name])
    require('avatar promote 12 level 0', [r for r in avatar_promote
            if r.get('avatarPromoteId') == 12 and r.get('promoteLevel', 0) == 0])
    require('weapon promote 11101 level 0', [r for r in weapon_promote
            if r.get('weaponPromoteId') == 11101 and r.get('promoteLevel', 0) == 0])
    gadget_id = tables[FILES[3]][11101].get('gadgetId')
    if type(gadget_id) is not int or gadget_id <= 0 or gadget_id not in gadgets:
        raise ValueError('Missing starting weapon gadget')
    checks.append('weapon gadget ' + str(gadget_id))
    depots = [tables[FILES[1]][i] for ids in DEPOTS.values() for i in ids]
    talents = sorted({value for depot in depots for value in depot.get('talents', []) if value > 0})
    for value in talents:
        require('talent ' + str(value), [r for r in talent if r.get('talentId') == value])
    skill_ids = {value for depot in depots for field in ('skills', 'subSkills')
                 for value in depot.get(field, []) if value > 0}
    skill_ids.update(depot.get(field, 0) for depot in depots
                     for field in ('energySkill', 'attackModeSkill') if depot.get(field, 0) > 0)
    groups = {tables[FILES[2]][skill].get('proudSkillGroupId', 0) for skill in skill_ids}
    groups.update(opening.get('proudSkillGroupId', 0) for depot in depots
                  for opening in depot.get('inherentProudSkillOpens', []))
    for group in sorted(g for g in groups if g > 0):
        require('proud group level 1 ' + str(group), [r for r in proud
                if r.get('proudSkillId') == group * 100 + 1 and r.get('proudSkillGroupId') == group])
    return checks


def main():
    parser = argparse.ArgumentParser()
    for option in ('source', 'commit', 'root-tree', 'excel-tree', 'output'):
        parser.add_argument('--' + option, type=Path, required=True)
    parser.add_argument('--extended-source', type=Path)
    args = parser.parse_args()
    read = lambda p: json.loads(p.read_text(encoding='utf-8-sig'))
    tables, hashes = load_tables(args.source, read(args.root_tree), read(args.excel_tree), read(args.commit))
    report = {'commit': COMMIT, 'rootTree': ROOT, 'sourceSHA256': hashes,
              'avatars': audit(tables), 'resourceReady': False,
              'scope': 'Selected starting avatar, weapon, depot and skill references only',
              'open': ['runtime revision', 'promotion/talent/curve/ability and weapon dependencies',
                       'scene resources and trigger behavior', 'production loading and client entry']}
    if args.extended_source:
        extra, extra_hashes = load_extra(args.extended_source, read(args.excel_tree))
        gadget_path = args.source / 'ExcelBinOutput' / 'GadgetExcelConfigData.json'
        raw = gadget_path.read_bytes()
        entry = verify_tree(read(args.excel_tree))[gadget_path.name]
        if len(raw) != entry['size'] or git_hash('blob', raw) != entry['sha']:
            raise ValueError('Candidate blob mismatch: ' + gadget_path.name)
        report['extendedSourceSHA256'] = extra_hashes
        report['extendedSourceSHA256'][gadget_path.name] = hashlib.sha256(raw).hexdigest()
        report['extendedChecks'] = audit_extended(tables, extra, index_table(raw, 'id'))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
