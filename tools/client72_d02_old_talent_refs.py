"""Join fixed old-save and decoded Depot reports; do not decode target rows."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261111-asc/c-talent-refs'
INPUTS = {
    'dependencies': ('local/migration72/d02-resume-20261110b/old-account-snapshot/run-20261110-2/selected-dependencies.private.json', '3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7'),
    'depotReport': ('local/migration72/d02-resume-20261110b/c-all-depot-refs/main-third.private.json', 'b6c806dfd5634f20501a9e57a70d88bf4d41b63b47740cf68dff817b7d79527a'),
    'talentEvidence': ('docs/CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md', '3e5263046dfd970ec9af3bc4866964beda98de74cd1fde6cd4dd824b9784220d'),
    'avatarJava': ('src/main/java/emu/grasscutter/game/avatar/Avatar.java', '9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d'),
}


def read_inputs():
    result = {}
    for name, (path, digest) in INPUTS.items():
        raw = (ROOT / path).read_bytes()
        if hashlib.sha256(raw).hexdigest() != digest:
            raise ValueError(f'{name} SHA-256 mismatch')
        if name in ('dependencies', 'depotReport'):
            result[name] = json.loads(raw)
    return result


def id_list(values):
    if not isinstance(values, list) or any(type(v) is not int or not 0 <= v <= 0x7fffffff for v in values):
        raise ValueError('invalid talent list')
    return values


def project(snapshot, report, expected_count=121):
    player = snapshot['player']
    team_manager = player['fields']['teamManager']
    team = team_manager['currentRegularTeam']
    if player['missing'] or team_manager['missing'] or team['missing']:
        raise ValueError('missing player or current team')
    owner = player['fields']['_id']
    active = team['fields']['avatars']
    if (not isinstance(active, list) or any(type(x) is not int or x <= 0 for x in active)
            or len(set(active)) != len(active)):
        raise ValueError('invalid current team')
    samples = report['samples']
    avatars = snapshot['avatars']
    if len(avatars) != expected_count or len(samples) != expected_count:
        raise ValueError('missing or extra avatar/Depot row')
    depot_by_avatar, depot_ids = {}, set()
    for sample in samples:
        aid, depot = sample['avatarId'], sample['key']
        if (type(aid) is not int or aid <= 0 or aid in depot_by_avatar or
                type(depot) is not int or depot <= 0 or depot in depot_ids):
            raise ValueError('invalid or duplicate report avatar/Depot')
        depot_by_avatar[aid] = sample
        depot_ids.add(depot)
    results, seen = [], set()
    for row in avatars:
        if row['missing']:
            raise ValueError('missing saved avatar fields')
        fields = row['fields']
        aid, depot = fields['avatarId'], fields['skillDepotId']
        if (fields['ownerId'] != owner or type(aid) is not int or aid <= 0 or aid in seen or
                type(depot) is not int or depot <= 0):
            raise ValueError('invalid, duplicate, or foreign saved avatar')
        seen.add(aid)
        sample = depot_by_avatar.get(aid)
        if sample is None or sample['key'] != depot:
            raise ValueError('saved avatar/current Depot does not match report')
        selected = [f for f in sample['fields'] if f['fieldIndex'] == 105357]
        key_fields = [f for f in sample['fields'] if f['fieldIndex'] == 105363]
        if len(selected) != 1 or len(key_fields) != 1 or key_fields[0]['value'] != depot:
            raise ValueError('missing, duplicate, or mismatched Depot field')
        field = selected[0]
        typ = field['type']
        if (field['owner'] != 22284 or field['offset'] != '0x28' or typ['kind'] != 'szarray'
                or typ['rank'] != 1 or typ['elementType']['kind'] != 'valuetype'
                or typ['elementType']['typeDefinition'] != 58209):
            raise ValueError('Talent typed identity differs')
        source = sample['source']
        if (type(source['begin']) is not int or type(source['end']) is not int
                or not 0 <= source['begin'] < source['end'] or len(source['sha256']) != 64
                or any(c not in '0123456789abcdef' for c in source['sha256'])):
            raise ValueError('invalid Depot source span')
        depot_values = id_list(field['value'])
        saved_values = id_list(fields['talentIdList'])
        consumed = set(depot_values) & set(saved_values)
        results.append({'avatarId': aid, 'currentDepotId': depot, 'activeRegularTeam': aid in active,
                        'depotSource': source, 'depotTalentValues': depot_values,
                        'savedTalentValues': saved_values, 'currentDepotUnlocked': sorted(consumed),
                        'savedOutsideCurrentDepot': sorted(set(saved_values) - set(depot_values))})
    if seen != set(depot_by_avatar) or not set(active) <= seen:
        raise ValueError('report or active team contains unmatched avatar')
    results.sort(key=lambda r: r['avatarId'])
    return {'task': 'E-01/E-01c/E-01d1 R3 old Talent references',
            'inputs': {name: {'path': path, 'sha256': digest} for name, (path, digest) in INPUTS.items()},
            'avatars': results,
            'currentDepotTalentLookupKeys': sorted({v for r in results for v in r['currentDepotUnlocked']}),
            'activeRegularTeamTalentLookupKeys': sorted({v for r in results if r['activeRegularTeam'] for v in r['currentDepotUnlocked']}),
            'savedOutsideCurrentDepotKeys': sorted({v for r in results for v in r['savedOutsideCurrentDepot']}),
            'boundary': 'Java current Depot and saved unlock set intersection, including zero if present in both. Talent row and OpenConfig lookup/value/revision require separate verification; saved outside-current references remain conditional. Input presence of typed field is unknown.',
            'completeAvatar': False, 'resourceReady': False, 'd02Complete': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='new JSON basename in private task directory')
    args = parser.parse_args()
    output = OUT / args.output
    if output.parent.resolve() != OUT.resolve() or output.suffix != '.json' or output.exists():
        parser.error('output must be a new JSON basename in the task directory')
    inputs = read_inputs()
    result = project(inputs['dependencies'], inputs['depotReport'])
    raw = (json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf-8')
    OUT.mkdir(parents=True, exist_ok=True)
    with output.open('xb') as stream:
        stream.write(raw)
    print(f'bytes={len(raw)} sha256={hashlib.sha256(raw).hexdigest()}')


if __name__ == '__main__':
    main()
