"""Read-only comparison of a pinned old-save projection with pinned decoded keys."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = 'local/migration72/d02-resume-20261110b/old-account-snapshot/run-20261110-2/'
OUTPUT_ROOT = ROOT / 'local/migration72/d02-resume-20261110b/c-old-avatar'
PINS = {
    SNAPSHOT + 'selected-dependencies.private.json': '3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7',
    SNAPSHOT + 'selected-manifest.private.json': '9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b',
    SNAPSHOT + 'acceptance.private.json': '80a39b3943d0e4658cdfae3bc561cafebd0ddcbbc755aeefc7d0df2d4f447667',
    'local/migration72/d02-resume-20261110/c-depot-talents-implementation/first2.private.json': 'ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f',
    'local/migration72/d02-resume-20261108/r3-mapping/main-third.json': 'd990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e',
    'local/migration72/d02-resume-20261108/r3-promote/main-third.json': '546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1',
    'local/migration72/d02-resume-20261108/r3-equipment/main-third.json': '50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc',
    'local/migration72/d02-resume-20261109/r3-item/main-third.json': '9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2',
    'local/migration72/d02-resume-20261109/r3-gadget-bridge/main-third.json': '51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880',
}
MAPPING = 'local/migration72/d02-resume-20261108/r3-mapping/main-third.json'
TABLE_COUNTS = {'Avatar': 169, 'AvatarSkillDepot': 171, 'AvatarSkill': 1176}


def pinned_bytes(path, expected):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f'SHA-256 mismatch: {path.name}')
    return data


def analyze(snapshot, report, expected_counts=TABLE_COUNTS):
    """Compare existing decoded keys; source hashes are recorded, not re-decoded."""
    player = snapshot['player']
    if player['missing'] or player['fields']['teamManager']['missing']:
        raise ValueError('missing player/team fields')
    uid = player['fields']['_id']
    team = player['fields']['teamManager']['currentRegularTeam']
    if team['missing']:
        raise ValueError('missing current team')
    active = team['fields']['avatars']
    if not isinstance(active, list) or any(type(x) is not int or x <= 0 for x in active):
        raise ValueError('invalid active team')
    tables = {}
    for table in report['native']['parsedTables']:
        name = table['name']
        if name not in expected_counts or name in tables:
            raise ValueError('unexpected or duplicate table')
        rows = table['rows']
        if len(rows) != expected_counts[name] or table['count'] != len(rows) or table['cursor'] != table['payloadBytes']:
            raise ValueError('incomplete table')
        index, cursor = {}, 4
        for row in rows:
            key, span = row['key'], row['source']
            if type(key) is not int or key <= 0 or key in index:
                raise ValueError('invalid or duplicate decoded key')
            if span['begin'] != cursor or span['end'] <= cursor or len(span['sha256']) != 64:
                raise ValueError('invalid row span')
            int(span['sha256'], 16)
            index[key] = span
            cursor = span['end']
        if cursor != table['payloadBytes']:
            raise ValueError('row spans do not reach EOF')
        tables[name] = index
    if set(tables) != set(expected_counts):
        raise ValueError('missing table')
    fragment_keys = {name: set() for name in tables}
    for fragment in report['javaProjectionFragments']:
        name, key = fragment['table'], fragment['key']
        if name in tables:
            if not fragment['fragmentOnly'] or tables[name].get(key) != fragment['source']:
                raise ValueError('fragment/source mismatch')
            fragment_keys[name].add(key)
    results, seen = [], set()
    for row in snapshot['avatars']:
        if row['missing']:
            raise ValueError('missing saved avatar fields')
        a = row['fields']
        if a['ownerId'] != uid:
            raise ValueError('avatar UID isolation failure')
        aid, depot = a['avatarId'], a['skillDepotId']
        if type(aid) is not int or aid <= 0 or aid in seen or type(depot) is not int or depot <= 0:
            raise ValueError('invalid or duplicate saved avatar/depot')
        seen.add(aid)
        for key in ('level', 'promoteLevel'):
            if type(a[key]) is not int or a[key] < 0:
                raise ValueError('invalid saved level/phase')
        conditions = {}
        for field in ('talentIdList', 'proudSkillList'):
            values = a[field]
            if not isinstance(values, list) or any(type(x) is not int or x < 0 for x in values):
                raise ValueError('invalid saved conditional list')
            conditions[field] = [x for x in values if x > 0]
        skills = a['skillLevelMap']
        if not isinstance(skills, dict):
            raise ValueError('invalid skill level map')
        skill_refs = []
        for key, level in skills.items():
            if not isinstance(key, str) or not key.isascii() or not key.isdecimal() or str(int(key)) != key or type(level) is not int or level < 0:
                raise ValueError('invalid skill key/level')
            sid = int(key)
            if sid > 0:
                skill_refs.append({'id': sid, 'savedLevel': level, 'source': tables['AvatarSkill'].get(sid),
                                   'bridgeFragmentAvailable': sid in fragment_keys['AvatarSkill']})
        results.append({'avatarId': aid, 'currentDepotId': depot, 'level': a['level'], 'promoteLevel': a['promoteLevel'],
                        'activeRegularTeam': aid in active,
                        'avatarSource': tables['Avatar'].get(aid), 'depotSource': tables['AvatarSkillDepot'].get(depot),
                        'avatarBridgeFragmentAvailable': aid in fragment_keys['Avatar'],
                        'depotBridgeFragmentAvailable': depot in fragment_keys['AvatarSkillDepot'],
                        'conditionalSaved': conditions, 'skillLevelReferences': skill_refs})
    return {'avatars': results, 'activeRegularTeam': active, 'missingActiveAvatarIds': [x for x in active if x not in seen],
            'scope': 'Decoded key and reported source span only; no raw-byte decoding, whole-row semantics or runtime validation.',
            'sourceSpanVerification': 'Structural contiguous ranges and recorded SHA only; payload bytes not rehashed here.',
            'conditionalBoundary': 'Saved talent, proud and skill level references are conditional, not all entry P0.',
            'resourceReady': False, 'd02Complete': False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUTPUT_ROOT.resolve()) or output.suffix != '.json':
        raise ValueError('output must be a JSON file in the private output root')
    blobs = {path: pinned_bytes(ROOT / path, sha) for path, sha in PINS.items()}
    manifest = json.loads(blobs[SNAPSHOT + 'selected-manifest.private.json'])
    snapshot = json.loads(blobs[SNAPSHOT + 'selected-dependencies.private.json'])
    acceptance = json.loads(blobs[SNAPSHOT + 'acceptance.private.json'])
    if (acceptance['selectedManifestSHA256'] != PINS[SNAPSHOT + 'selected-manifest.private.json'] or
            acceptance['selectedDependenciesSHA256'] != PINS[SNAPSHOT + 'selected-dependencies.private.json'] or
            not acceptance['accountOwnershipVerified'] or not acceptance['sourceAndWorkingDatabaseSeparated'] or
            acceptance['wholeAccountOrClientRestored']):
        raise ValueError('snapshot acceptance mismatch')
    for name, entry in manifest.items():
        if entry['sha256'] != snapshot['inputs'][name]['sha256'] or entry['format'] != snapshot['inputs'][name]['format']:
            raise ValueError('manifest/projection mismatch')
        pinned_bytes(Path(entry['path']), entry['sha256'])
    result = analyze(snapshot, json.loads(blobs[MAPPING]))
    if len(result['avatars']) != acceptance['oldAccountActualAvatarCount']:
        raise ValueError('avatar count mismatch')
    result['inputSHA256'] = PINS
    result['snapshotSourceSHA256'] = {name: entry['sha256'] for name, entry in manifest.items()}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write('\n')


if __name__ == '__main__':
    main()
