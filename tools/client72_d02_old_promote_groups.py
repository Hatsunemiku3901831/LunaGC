"""Join fixed saved phases with actual Avatar promote groups and frozen row spans."""
import argparse
import hashlib
import json
from pathlib import Path
import pickle

from tools import client72_d02_promote_resource as frozen
from tools.client72_emulator import GLOBAL

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261111-asc/c-old-promote-groups'
SNAPSHOT = ROOT / 'local/migration72/d02-resume-20261110b/old-account-snapshot/run-20261110-2/selected-dependencies.private.json'
REPORT = frozen.RAW / 'main-third.json'
PINS = {
    SNAPSHOT: '3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7',
    REPORT: '546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1',
    ROOT / 'tools/client72_d02_promote_resource.py': '8d60647478e24defe3b550c74d0f17474517450b29cfb9e8630cc6f82d88cbde',
}
ANCHOR = {'begin': 2670, 'end': 2776, 'sha256': 'd4ac27e008a58c16303a0005dcfe2a8b198a59dc08994861e4dcb3b123847dfb'}
LIMIT = 2_000_000


def read_pinned(path, digest):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != digest:
        raise ValueError(f'Pinned input changed: {path.name}')
    return raw


def saved_avatars(snapshot, expected_count=121):
    player = snapshot['player']
    if player['missing'] or len(snapshot['avatars']) != expected_count:
        raise ValueError('missing player or incorrect saved avatar count')
    owner = player['fields']['_id']
    result = {}
    for row in snapshot['avatars']:
        if row['missing']:
            raise ValueError('missing saved avatar fields')
        fields = row['fields']
        aid, phase = fields['avatarId'], fields['promoteLevel']
        if (fields['ownerId'] != owner or type(aid) is not int or aid <= 0 or aid in result
                or type(phase) is not int or not 0 <= phase <= 255):
            raise ValueError('invalid, duplicate or foreign saved avatar/phase')
        result[aid] = phase
    return result


def promote_rows(report, blob):
    tables = [table for table in report['tables'] if table['name'] == 'AvatarPromote']
    if len(tables) != 1:
        raise ValueError('AvatarPromote table identity changed')
    table = tables[0]
    if (table['count'] != 889 or len(table['rows']) != 889 or table['cursor'] != len(blob)
            or table['payloadBytes'] != len(blob) or table['payloadSHA256'] != frozen.sha(blob)):
        raise ValueError('AvatarPromote report/payload identity changed')
    result, cursor = {}, 4
    for row in table['rows']:
        group, phase, source = row['group'], row['phase'], row['source']
        begin, end = source['begin'], source['end']
        if (type(group) is not int or group < 0 or type(phase) is not int or not 0 <= phase <= 255
                or (group, phase) in result or begin != cursor or not begin < end <= len(blob)
                or frozen.sha(blob[begin:end]) != source['sha256']):
            raise ValueError('duplicate key or invalid Promote source span')
        result[group, phase] = source
        cursor = end
    if cursor != len(blob) or result.get((12, 6)) != ANCHOR:
        raise ValueError('Promote EOF or independent 12/6 anchor changed')
    return result


def join(saved, groups, rows):
    if set(saved) != set(groups):
        raise ValueError('saved avatar and actual group results do not match')
    output = []
    for aid, phase in sorted(saved.items()):
        actual = groups[aid]
        group = actual['group']
        source = rows.get((group, phase))
        output.append({'avatarId': aid, 'savedPhase': phase, 'actualGroup': group,
                       'avatarSource': actual['source'], 'groupTrace': actual['trace'],
                       'promoteSource': source, 'promoteKeyFound': source is not None})
    return output


def run(client):
    raw = {path: read_pinned(path, digest) for path, digest in PINS.items()}
    snapshot, report = (json.loads(raw[p]) for p in (SNAPSHOT, REPORT))
    saved = saved_avatars(snapshot)
    pins, _, bindings = frozen.verify_inputs(client)
    blob = (frozen.RAW / 'AvatarPromote.cached-m_bytes.bin').read_bytes()
    rows = promote_rows(report, blob)
    avatar_blob = (frozen.equip.SOURCE / 'Avatar.native-m_bytes.bin').read_bytes()
    locator = json.loads(frozen.equip.LOCATOR.read_bytes())
    target = next(t for t in locator['targets'] if t['name'] == 'Avatar')
    entry = target['entry']
    carrier = frozen.card.base.read_verified(frozen.equip.SOURCE / '25539185.serialized.bin', locator['payload']['outputSHA256'])
    begin, size = entry['serializedOffset'], entry['bytes']
    if (target['location']['blockID'] != 25539185 or len(avatar_blob) != size
            or entry['sha256'] != frozen.sha(avatar_blob) or carrier[begin:begin + size] != avatar_blob):
        raise ValueError('Avatar payload differs from frozen same-source carrier')
    binary, metadata, startup = [(client / name).read_bytes() for name in list(frozen.card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((frozen.equip.RAW / 'metadata.private.pkl').read_bytes())
    codec = frozen.card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = frozen.PromoteRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    try:
        frozen.equip.install_table(rt, 'Avatar')
        start = rt.call_instruction_total
        avatar = rt.parse_table(avatar_blob)
        parse_visits = rt.call_instruction_total - start
        if avatar['count'] != 169 or avatar['cursor'] != len(avatar_blob):
            raise ValueError('Avatar whole parse count or EOF changed')
        by_id = {r['key']: r for r in avatar['rows']}
        if len(by_id) != 169 or not set(saved) <= set(by_id):
            raise ValueError('saved avatar missing from actual Avatar cache')
        spans = sorted((r['source'] for r in avatar['rows']), key=lambda s: s['begin'])
        if (spans[0]['begin'] != 4 or spans[-1]['end'] != len(avatar_blob)
                or any(a['end'] != b['begin'] for a, b in zip(spans, spans[1:]))
                or any(frozen.sha(avatar_blob[s['begin']:s['end']]) != s['sha256'] for s in spans)):
            raise ValueError('Avatar source spans do not cover the fixed payload')
        rt.bind_function(0x15184adf0)
        if not rt.e.read(GLOBAL + frozen.card.TABLES['Avatar'][2]):
            raise ValueError('actual Avatar cache absent')
        groups = {}
        start = rt.call_instruction_total
        for aid in sorted(saved):
            trace = rt.call(0x15184ae18, stops=(0x15184ae6f,), rax=aid)
            if (trace['stop'] != '0x15184ae6f'
                    or any(trace['executedAddresses'].get(address) != 1
                           for address in ('0x15184ae4f', '0x15184ae59', '0x15184ae68', '0x15184ae6d'))):
                raise ValueError('actual group producer did not read the cache row and +178')
            groups[aid] = {'group': rt.e.reg('esi'), 'source': by_id[aid]['source'], 'trace': trace}
            if parse_visits + rt.call_instruction_total - start > LIMIT:
                raise ValueError('Avatar parser and group slice cumulative budget exhausted')
        group_visits = rt.call_instruction_total - start
        output = join(saved, groups, rows)
        traveler = next(r for r in output if r['avatarId'] == 10000007)
        if traveler['actualGroup'] != 12 or traveler['savedPhase'] != 6 or traveler['promoteSource'] != ANCHOR:
            raise ValueError('saved traveler 12/6 anchor changed')
        result = {'task': 'E-01d1/R3 saved Avatar promote group and phase keys',
                  'sourceSHA256': frozen.sha(Path(__file__).read_bytes()),
                  'inputs': {str(p.relative_to(ROOT)): digest for p, digest in PINS.items()},
                  'sourcePins': pins, 'avatarPayload': {'sha256': frozen.sha(avatar_blob), 'bytes': len(avatar_blob),
                      'carrierOffset': begin, 'rows': avatar['count'], 'cursor': avatar['cursor'],
                      'parseTrace': avatar['trace']},
                  'promotePayload': {'sha256': frozen.sha(blob), 'bytes': len(blob), 'rows': len(rows)},
                  'budget': {'limit': LIMIT, 'parserAndSelectedReadoutHookVisits': parse_visits,
                             'groupSlicesHookVisits': group_visits, 'combinedHookVisits': parse_visits + group_visits,
                             'individualCallLimit': LIMIT},
                  'avatars': output, 'missingKeys': sum(not r['promoteKeyFound'] for r in output),
                  'actualAvatarCache': True, 'fullProducerExecuted': False, 'completeFields': False,
                  'javaLoaded': False, 'completeRecovery': False, 'resourceReady': False, 'd02Complete': False,
                  'scope': 'Actual saved phase and original Avatar group slice joined to frozen Promote row spans; field semantics, Java load and recovery require separate acceptance.'}
    finally:
        rt.e.close()
    frozen.equip.verify_client(client)
    result['fiveInputsRecheckedAfterNative'] = True
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', required=True, help='new report basename in private directory')
    args = parser.parse_args()
    output = OUT / args.output
    if output.parent.resolve() != OUT.resolve() or output.suffix != '.json' or output.exists():
        parser.error('output must be a new JSON basename in the private task directory')
    result = run(args.client)
    OUT.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, indent=2, sort_keys=True)
        stream.write('\n')
    print(json.dumps({'avatars': len(result['avatars']), 'missingKeys': result['missingKeys'], 'budget': result['budget']}))


if __name__ == '__main__':
    main()
