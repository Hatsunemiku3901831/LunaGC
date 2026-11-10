"""Extract only saved phase Promote fields from the fixed original whole parser."""
import argparse
import json
from pathlib import Path
import pickle
import re
import struct

from tools import client72_d02_promote_resource as frozen
from tools import client72_d02_old_promote_groups as groups
from tools.client72_emulator import GLOBAL
from tools.client72_resource_deep import RETURN

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261111-asc/c-old-promote-fields'
GROUP_REPORT = groups.OUT / 'third.json'
PINS = {GROUP_REPORT: 'e500009492028183179eed579b7e5f85f6883fe191d5efee546eb914bf584e4c',
        groups.REPORT: groups.PINS[groups.REPORT],
        ROOT / 'tools/client72_d02_promote_resource.py': groups.PINS[ROOT / 'tools/client72_d02_promote_resource.py']}
LIMIT = 2_000_000


def targets(report, rows):
    avatars = report['avatars']
    if len(avatars) != 121 or report['missingKeys'] != 0:
        raise ValueError('Expected 121 saved avatars and no missing Promote key')
    result = {}
    ids = set()
    for row in avatars:
        aid, key = row['avatarId'], (row['actualGroup'], row['savedPhase'])
        source = row['promoteSource']
        if aid in ids or key in result or not row['promoteKeyFound'] or rows.get(key) != source:
            raise ValueError('Duplicate target or source span mismatch')
        ids.add(aid)
        result[key] = {'avatarId': aid, 'source': source}
    if result.get((12, 6)) != {'avatarId': 10000007, 'source': groups.ANCHOR}:
        raise ValueError('Saved traveler anchor changed')
    return result


def project(row, enums, java_fight):
    fields = {x['offset']: x['value'] for x in row['fields']}
    if set(fields) != {'0x10', '0x18', '0x2c', '0x30'}:
        raise ValueError('Missing or unexpected selected field')
    group, phase = fields['0x30'], fields['0x2c']
    if group != row['group'] or phase != row['phase'] or type(phase) is not int or not 0 <= phase <= 255:
        raise ValueError('Actual composite key mismatch')
    group = frozen.equip.java_id(group)
    key = frozen.equip.java_id((group << 8) + phase)
    if type(fields['0x10']) is not list or type(fields['0x18']) is not list:
        raise ValueError('Selected arrays must be nonnull')
    props, costs = [], []
    for prop in fields['0x10']:
        values = {x['offset']: x['value'] for x in prop}
        if set(values) != {'0x0', '0x4'}:
            raise ValueError('Missing property field')
        value = values['0x4']
        bits = struct.unpack('<I', struct.pack('<f', value['float32']))[0]
        if bits != int(value['bits'], 16) or bits & 0x7f800000 == 0x7f800000:
            raise ValueError('Invalid float32 bits')
        props.append({'propType': frozen.equip.exact_enum(values['0x0']['value'], 32419, enums, java_fight),
                      'value': value['float32'], 'bits': value['bits']})
    for item in fields['0x18']:
        values = {x['offset']: x['value'] for x in item}
        if set(values) != {'0x0', '0x4'}:
            raise ValueError('Missing material field')
        item_id, count = values['0x0'], values['0x4']
        if any(type(x) is not int or not 0 <= x <= 0x7fffffff for x in (item_id, count)):
            raise ValueError('Material pair cannot be represented by Java int')
        costs.append({'id': item_id, 'count': count})
    return {'key': key, 'javaFields': {'avatarPromoteId': group, 'promoteLevel': phase,
            'addProps': [{'propType': p['propType'], 'value': p['value']} for p in props], 'costItems': costs},
            'addPropBits': [p['bits'] for p in props], 'materialReferencesVerified': False,
            'serializedFieldPresence': 'unknown', 'omittedNotAssertedAsDefaults':
            ['scoinCost', 'unlockMaxLevel', 'requiredPlayerLevel']}


def run(client):
    raw = {p: groups.read_pinned(p, digest) for p, digest in PINS.items()}
    group_report, report = (json.loads(raw[p]) for p in (GROUP_REPORT, groups.REPORT))
    pins, sources, bindings = frozen.verify_inputs(client)
    blob = groups.read_pinned(frozen.RAW / 'AvatarPromote.cached-m_bytes.bin',
                              frozen.PINS[frozen.RAW / 'AvatarPromote.cached-m_bytes.bin'])
    rows = groups.promote_rows(report, blob)
    required = targets(group_report, rows)
    binary, metadata, startup = [(client / name).read_bytes() for name in list(frozen.card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((frozen.equip.RAW / 'metadata.private.pkl').read_bytes())
    codec = frozen.card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = frozen.PromoteRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    try:
        spec = frozen.SPECS['AvatarPromote']
        count = (struct.unpack_from('<I', blob)[0] ^ 0xebf536d6) & 0xffffffff
        if count != 889 or len(blob) != 87706:
            raise ValueError('Source framing changed')
        rt.reader, rt.spans, rt.pending_rows, rt.row_entry = 0, [], {}, spec['parser']
        uses = frozen.bind_generics(rt, spec['whole'])
        rt.reader = rt.e.alloc(64)
        data = rt.array(len(blob), 1)
        rt.e.uc.mem_write(data + 0x20, blob)
        rt.e.write(rt.reader + 0x10, data)
        trace = rt.call_whole(spec['whole'], count, total_limit=LIMIT)
        if trace['stop'] != hex(RETURN) or rt.e.read(rt.reader + 0x1c, 'I') != len(blob):
            raise ValueError('Original whole parser did not reach exact EOF')
        spans = rt.spans
        if (len(spans) != count or spans[0]['begin'] != 4 or spans[-1]['end'] != len(blob)
                or any(a['end'] != b['begin'] for a, b in zip(spans, spans[1:]))):
            raise ValueError('Original whole spans do not cover payload')
        collection = rt.e.read(GLOBAL + spec['listOffset'])
        array = rt.e.read(collection + 0x10)
        if rt.e.read(collection + 0x18, 'I') != count:
            raise ValueError('Original list count changed')
        pointers = [rt.e.read(array + 0x20 + i * 8) for i in range(count)]
        span_by_ptr = {int(s['pointer'], 16): s for s in spans}
        if len(span_by_ptr) != count or set(pointers) != set(span_by_ptr):
            raise ValueError('Original list pointers and parsed spans differ')
        by_span = {(s['begin'], s['end']): p for p, s in span_by_ptr.items()}
        field_specs = [f for f in rt.records[spec['row']]['fields']
                       if f['offsetKind'] == 'instance' and f['offset'] in (0x10, 0x18, 0x2c, 0x30)]
        if {f['offset'] for f in field_specs} != {0x10, 0x18, 0x2c, 0x30} or len(field_specs) != 4:
            raise ValueError('Selected typed row fields changed')
        java_fight = {n: int(v) for n, v in re.findall(r'^    (FIGHT_PROP_\w+)\((\d+)\)',
                      sources['game/props/FightProperty.java'], re.M)}
        output = []
        start = rt.call_instruction_total
        for (group, phase), target in sorted(required.items()):
            source = target['source']
            pointer = by_span.get((source['begin'], source['end']))
            if pointer is None or frozen.sha(blob[source['begin']:source['end']]) != source['sha256']:
                raise ValueError('Target source span mismatch')
            fields = [{'offset': hex(f['offset']), 'value': rt.snapshot_value(pointer + f['offset'], f['type'], 1)}
                      for f in field_specs]
            row = {'avatarId': target['avatarId'], 'group': group, 'phase': phase, 'source': source, 'fields': fields}
            row['projection'] = project(row, rt.enum_symbols, java_fight)
            output.append(row)
            if trace['instructions'] + rt.call_instruction_total - start > LIMIT:
                raise ValueError('Whole parser plus selected readout exceeds two million budget')
        visits = rt.call_instruction_total - start
        anchor = next(r for r in output if (r['group'], r['phase']) == (12, 6))
        baseline = next(r for r in next(t for t in report['tables'] if t['name'] == 'AvatarPromote')['rows']
                        if (r['group'], r['phase']) == (12, 6))
        baseline_fields = {f['offset']: f['value'] for f in baseline['fields'] if f['offset'] in ('0x10', '0x18', '0x2c', '0x30')}
        if {f['offset']: f['value'] for f in anchor['fields']} != baseline_fields:
            raise ValueError('12/6 actual field anchor differs from existing original report')
        result = {'task': 'E-01d1/R3 saved Promote fields', 'sourceSHA256': frozen.sha(Path(__file__).read_bytes()),
                  'inputs': {str(p.relative_to(ROOT)): h for p, h in PINS.items()}, 'sourcePins': pins,
                  'payload': {'sha256': frozen.sha(blob), 'bytes': len(blob), 'rows': count, 'cursor': len(blob)},
                  'budget': {'limit': LIMIT, 'wholeHookVisits': trace['instructions'], 'selectedReadoutHookVisits': visits,
                             'combinedHookVisits': trace['instructions'] + visits},
                  'trace': trace, 'wholeMethodUsages': uses, 'resourceBindings': bindings, 'rows': output,
                  'anchor12_6MatchesOriginalFields': True, 'materialReferencesVerified': False,
                  'serializedFieldPresence': 'unknown', 'actualJavaLoaded': False, 'completeRecovery': False,
                  'resourceReady': False, 'd02Complete': False,
                  'scope': 'Original parser and selected typed fields only; no upgrade cost semantics, material consumption, Java execution or client acceptance.'}
    finally:
        rt.e.close()
    frozen.equip.verify_client(client)
    result['fiveInputsRecheckedAfterNative'] = True
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    output = OUT / args.output
    if output.parent.resolve() != OUT.resolve() or output.suffix != '.json' or output.exists():
        parser.error('output must be a new JSON basename in the private task directory')
    result = run(args.client)
    OUT.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, indent=2, sort_keys=True, allow_nan=False)
        stream.write('\n')
    print(json.dumps({'rows': len(result['rows']), 'budget': result['budget']}))


if __name__ == '__main__':
    main()
