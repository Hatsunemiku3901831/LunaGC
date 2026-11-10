"""Read pinned offline Morphia documents; outputs belong in an ignored directory."""
import argparse
import hashlib
import json
import struct
from pathlib import Path


def bson_document(data, start=0):
    """Decode BSON document values without interpreting unknown Java fields."""
    size = struct.unpack_from('<i', data, start)[0]
    end = start + size
    if size < 5 or end > len(data) or data[end - 1] != 0:
        raise ValueError('invalid BSON document length')
    pos = start + 4
    result = {}

    def cstring(offset):
        stop = data.index(0, offset, end)
        return data[offset:stop].decode('utf-8'), stop + 1

    while pos < end - 1:
        kind = data[pos]
        key, pos = cstring(pos + 1)
        if key in result:
            raise ValueError('duplicate BSON key')
        if kind in (3, 4):
            value, pos = bson_document(data, pos)
            if kind == 4:
                if list(value) != [str(i) for i in range(len(value))]:
                    raise ValueError('invalid BSON array keys')
                value = list(value.values())
        elif kind in (2, 13, 14):
            length = struct.unpack_from('<i', data, pos)[0]
            pos += 4
            if length < 1 or pos + length > end or data[pos + length - 1] != 0:
                raise ValueError('invalid BSON string')
            value = data[pos:pos + length - 1].decode('utf-8')
            pos += length
        elif kind == 1:
            value = struct.unpack_from('<d', data, pos)[0]; pos += 8
        elif kind == 16:
            value = struct.unpack_from('<i', data, pos)[0]; pos += 4
        elif kind in (9, 17, 18):
            value = struct.unpack_from('<q', data, pos)[0]; pos += 8
        elif kind == 8:
            if data[pos] not in (0, 1): raise ValueError('invalid BSON boolean')
            value = bool(data[pos]); pos += 1
        elif kind == 10:
            value = None
        elif kind == 7:
            value = {'$oid': data[pos:pos + 12].hex()}; pos += 12
        elif kind == 5:
            length = struct.unpack_from('<i', data, pos)[0]
            if length < 0: raise ValueError('invalid BSON binary')
            value = {'$binaryOpaque': True}; pos += 5 + length
        elif kind == 11:
            _, pos = cstring(pos); _, pos = cstring(pos); value = {'$regexOpaque': True}
        elif kind == 19:
            value = {'$decimal128Opaque': True}; pos += 16
        elif kind in (6, 127, 255):
            value = {'$bsonType': kind}
        else:
            raise ValueError(f'unsupported BSON type {kind}')
        if pos > end - 1: raise ValueError('BSON value exceeds document')
        result[key] = value
    if pos != end - 1: raise ValueError('invalid BSON terminator')
    return result, end


def normalize(value):
    if isinstance(value, list): return [normalize(v) for v in value]
    if isinstance(value, dict):
        if len(value) == 1 and next(iter(value)) in ('$numberInt', '$numberLong'):
            return int(next(iter(value.values())))
        if len(value) == 1 and '$numberDouble' in value:
            return float(value['$numberDouble'])
        return {k: normalize(v) for k, v in value.items()}
    return value


def read_input(row):
    raw = Path(row['path']).read_bytes()
    if hashlib.sha256(raw).hexdigest() != row['sha256'].lower():
        raise ValueError('input SHA-256 mismatch')
    if row['format'] == 'bson':
        docs, pos = [], 0
        while pos < len(raw):
            doc, pos = bson_document(raw, pos); docs.append(doc)
    elif row['format'] in ('json', 'jsonl'):
        if row['format'] == 'jsonl': docs = [json.loads(line) for line in raw.splitlines() if line.strip()]
        else:
            docs = json.loads(raw)
            if isinstance(docs, dict): docs = [docs]
        docs = normalize(docs)
    else: raise ValueError('unsupported input format')
    if not isinstance(docs, list) or any(not isinstance(doc, dict) for doc in docs):
        raise ValueError('expected document or array of documents')
    return docs


def project(doc, keys):
    return {'fields': {k: doc[k] for k in keys if k in doc},
            'missing': [k for k in keys if k not in doc]}


def extract(player, avatars, items):
    uid = player['_id']
    if type(uid) is not int or uid <= 0: raise ValueError('player _id must be positive integer')
    own_avatars = [a for a in avatars if a.get('ownerId') == uid]
    own_items = [i for i in items if i.get('ownerId') == uid]
    loaded_items = [i for i in own_items if i.get('deleted') is not True
                    and type(i.get('count')) is int and i['count'] > 0]
    avatar_keys = ('_id', 'ownerId', 'avatarId', 'skillDepotId', 'level', 'promoteLevel',
                   'trialAvatarId', 'grantReason', 'fromParentQuestId', 'talentIdList', 'proudSkillList', 'skillLevelMap')
    item_keys = ('_id', 'ownerId', 'itemId', 'count', 'deleted', 'level', 'promoteLevel', 'equipCharacter')
    p = project(player, ('_id', 'sceneId', 'position', 'teamManager'))
    team = player.get('teamManager')
    if isinstance(team, dict):
        p['fields']['teamManager'] = project(team, ('currentTeamIndex', 'currentCharacterIndex'))
        teams, index = team.get('teams'), team.get('currentTeamIndex')
        current = teams.get(str(index)) if isinstance(teams, dict) and type(index) is int else None
        p['fields']['teamManager']['currentRegularTeam'] = project(current, ('avatars',)) if isinstance(current, dict) else None
    if isinstance(player.get('position'), dict): p['fields']['position'] = project(player['position'], ('x', 'y', 'z'))
    references = []
    saved_conditional = []
    for a in own_avatars:
        for field, kind, approved in (('avatarId', 'Avatar', {10000005, 10000007}), ('skillDepotId', 'Depot', {501, 701})):
            value = a.get(field)
            if type(value) is int and value > 0:
                references.append({'type': kind, 'id': value, 'scope': 'birth-id-only' if value in approved else 'additional', 'semanticStatus': 'not-verified-by-this-driver'})
        for field in ('talentIdList', 'proudSkillList'):
            values = a.get(field)
            if isinstance(values, list):
                saved_conditional.extend({'field': field, 'id': value} for value in values if type(value) is int and value > 0)
        levels = a.get('skillLevelMap')
        if isinstance(levels, dict):
            saved_conditional.extend({'field': 'skillLevelMap', 'id': int(key), 'level': value}
                                     for key, value in levels.items() if isinstance(key, str) and key.isdecimal() and int(key) > 0)
    for i in loaded_items:
        value = i.get('itemId')
        if type(value) is int and value > 0:
            references.append({'type': 'Item', 'id': value, 'scope': 'birth-id-only' if value == 11101 else 'additional', 'semanticStatus': 'not-verified-by-this-driver'})
    if type(player.get('sceneId')) is int and player['sceneId'] > 0:
        references.append({'type': 'Scene', 'id': player['sceneId'], 'scope': 'separate-scene-review', 'semanticStatus': 'not-verified-by-this-driver'})
    known = {a.get('avatarId') for a in own_avatars if type(a.get('avatarId')) is int}
    problems = []
    for item in loaded_items:
        if type(item.get('equipCharacter')) is int and item['equipCharacter'] > 0 and item['equipCharacter'] not in known:
            problems.append({'kind': 'equip-character-not-in-owner-avatars', 'itemId': item.get('itemId'), 'equipCharacter': item['equipCharacter']})
    current = p['fields'].get('teamManager', {}).get('currentRegularTeam') if isinstance(team, dict) else None
    for avatar_id in (current or {}).get('fields', {}).get('avatars', []):
        if avatar_id not in known: problems.append({'kind': 'team-avatar-not-in-owner-avatars', 'avatarId': avatar_id})
    return {'player': p, 'avatars': [project(a, avatar_keys) for a in own_avatars],
            'items': [project(i, item_keys) for i in own_items], 'references': references,
            'savedConditionalReferences': saved_conditional, 'unresolved': problems,
            'excludedOtherOwner': {'avatars': len(avatars) - len(own_avatars), 'items': len(items) - len(own_items)},
            'limitations': ['resource semantics and item type/equip slot are not checked', 'missing weapon fallback requires AvatarData.initialWeapon',
                            'trial and team repair paths require separate review', 'saved team is not necessarily runtime team',
                            'saved conditional references are not evidence of active use or target semantics',
                            'promote, curves, skills, talents and ability references require resource joins']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True, help='private JSON: player, avatars, items input rows')
    parser.add_argument('--manifest-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True, help='new private file in ignored directory')
    args = parser.parse_args()
    raw = args.manifest.read_bytes()
    if hashlib.sha256(raw).hexdigest() != args.manifest_sha256.lower(): raise ValueError('manifest SHA-256 mismatch')
    manifest = json.loads(raw)
    docs = {name: read_input(manifest[name]) for name in ('player', 'avatars', 'items')}
    if len(docs['player']) != 1: raise ValueError('player input must contain exactly one selected player')
    result = extract(docs['player'][0], docs['avatars'], docs['items'])
    result['inputs'] = {name: {'sha256': manifest[name]['sha256'], 'format': manifest[name]['format']} for name in docs}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8', newline='\n') as out:
        json.dump(result, out, ensure_ascii=False, indent=2, allow_nan=False); out.write('\n')


if __name__ == '__main__': main()
