"""Recover the admitted 121 energy-Skill cost fields without native execution."""

import hashlib
import json
import math
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parent.parent
TASK = 'R3-OLD121-SKILL-ORDINARY-COST-HOST-BATCH-20261116'
SPEC = {'path': 'debug/d02-skill-energy-search-20261116/local-analysis/ordinary-cost-host-batch-spec.private.json',
        'bytes': 93527, 'sha256': 'de081c668c7e9517c53bbe09347d3a5494a4ee9b0efdd78a71526759154be632'}
OUT = ROOT / 'debug/d02-skill-energy-search-20261116/ordinary-cost-host-batch'
STATE = {'stage': 'entry', 'inputReads': [], 'semanticRawReads': [], 'completedKeys': []}


def main():
    spec = json.loads(read_pin('spec', SPEC))
    sources = {name: read_pin(name, pin) for name, pin in spec['inputPins'].items()}
    elements, pilot = validate_sources(spec, sources)
    raw = read_pin('raw', spec['rawPin'])
    cost_fields = {}
    for case in spec['cases']:
        STATE.update(stage='same-row-hash', currentKey=case['key'])
        key, source, mask = case['key'], case['source'], case['maskSpan']
        need(sha(raw[source['begin']:source['end']]) == source['sha256'] and
             sha(raw[mask['begin']:mask['end']]) == mask['sha256'], 'same-row raw/mask hash differs')
        evidence = {'sourceField': '88602.field440599/+98', 'originalMask': case['rawMaskHex'],
                    'originalBit53Present': case['costPresent'], 'lastBit11Present': case['lastTokenPresent'],
                    'sourceOrdinarySelectorZero': case['sourceOrdinarySelectorZero'],
                    'disposition': case['disposition'], 'byteIndexBitsMatch': True}
        if case['disposition'] == 'reuse-10019-pilot':
            value = pilot['costEvidence']['float32Value']
            bits = int(pilot['costEvidence']['float32Bits'], 16)
            integer = pilot['fragment']['javaFields']['costElemVal']
            evidence.update(reusedResultSHA256=spec['inputPins']['pilotResult']['sha256'],
                            wordOffset=case['wordOffset'], inverseWireMatch=True)
        elif case['costPresent']:
            STATE['stage'] = 'one-cost-word'
            offset = case['wordOffset']
            token = raw[offset:offset + 4]
            STATE['semanticRawReads'].append({'key': key, 'begin': offset, 'end': offset + 4,
                                             'bytes': len(token), 'rawHex': token.hex(), 'sha256': sha(token)})
            word = struct.unpack('<I', token)[0]
            bits = word ^ 0x8a4ce70f
            independent = bytes(byte ^ constant for byte, constant in zip(token, b'\x0f\xe7\x4c\x8a'))
            need(sum(byte << (index * 8) for index, byte in enumerate(token)) == word and
                 bits.to_bytes(4, 'little') == independent, 'LE/byte XOR differs')
            value = struct.unpack('<f', bits.to_bytes(4, 'little'))[0]
            need(math.isfinite(value) and 0 <= value <= 2147483647 and value.is_integer(),
                 'cost is not exact nonnegative Java int')
            integer = int(value)
            need(struct.pack('<f', value) == bits.to_bytes(4, 'little') and
                 (int.from_bytes(struct.pack('<f', value), 'little') ^ 0x8a4ce70f).to_bytes(4, 'little') == token,
                 'float/inverse wire differs')
            evidence.update(wordOffset=offset, rawWordHex=token.hex(), byteXorMatch=True, inverseWireMatch=True)
        else:
            value, bits, integer = 0.0, 0, 0
            evidence['sourceAbsentRule'] = 'original PXOR XMM1 supplies logical Float32 +0 to same setter; not JSON/default fallback'
        evidence.update(float32Bits=f'{bits:08x}', float32Value=value, exactJavaInt=integer,
                        cursorAtEnd=source['end'], configuredThresholdRole=True)
        cost_fields[key] = {'value': integer, 'evidence': evidence}
        STATE['completedKeys'].append(key)
    need(len(STATE['semanticRawReads']) == 118 and sum(r['bytes'] for r in STATE['semanticRawReads']) == 472,
         'new word budget differs')
    fragments = []
    for original in elements['javaProjectionFragments']:
        key = original['key']
        fragment = {'table': 'AvatarSkill', 'key': key, 'source': original['source'],
                    'javaFields': dict(original['javaFields']), 'idRawDecoded': False, 'fragmentOnly': True}
        if key in cost_fields:
            fragment['javaFields']['costElemVal'] = cost_fields[key]['value']
            fragment['costEvidence'] = cost_fields[key]['evidence']
        fragments.append(fragment)
    STATE['stage'] = 'complete'
    report = {'task': TASK, 'passed': True, 'inputPins': STATE['inputReads'], 'energyKeys': spec['energyKeys'],
              'javaProjectionFragments': fragments, 'counts': spec['counts'],
              'summary': {'skillFragments': 369, 'energyThreeKeyFragments': 121, 'otherTwoKeyFragments': 248,
                          'costHistogram': {str(value): sum(c['value'] == value for c in cost_fields.values())
                                            for value in sorted({c['value'] for c in cost_fields.values()})}},
              'rawReadLedger': {'physicalDiskBytes': len(raw), 'wholeHashPasses': 1,
                                'sourceSpanHashBytesFromSameBuffer': sum(c['source']['end'] - c['source']['begin'] for c in spec['cases']),
                                'knownMaskHashBytesFromSameBuffer': 968, 'newMaskSemanticBytes': 0,
                                'newSemanticBytes': 472, 'semanticReads': STATE['semanticRawReads']},
              'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False, 'fullSkill': False,
              'specialFull121Complete': False, 'actualAvailabilityOrMaxEnergyAssignmentProven': False,
              'nativeExecuted': False, 'javaExecuted': False}
    result = encode(report)
    manifest = encode({'task': TASK, 'passed': True, 'resultBytes': len(result), 'resultSHA256': sha(result),
                       'toolSHA256': sha(Path(__file__).read_bytes()), 'specSHA256': SPEC['sha256'],
                       'newSemanticRawBytes': 472, 'newMaskSemanticBytes': 0, 'resourceReady': False})
    need(len(result) + len(manifest) <= spec['budget']['combinedOutputBytes'], 'combined output budget exceeded')
    write('result.private.json', result)
    write('manifest.private.json', manifest)
    print(json.dumps({'task': TASK, 'passed': True, 'summary': report['summary'], 'newSemanticRawBytes': 472,
                      'resultBytes': len(result), 'resultSHA256': sha(result), 'manifestSHA256': sha(manifest),
                      'combinedOutputBytes': len(result) + len(manifest)}))


def validate_sources(spec, sources):
    STATE['stage'] = 'source-contract'
    need(spec['executionTask'] == TASK and len(spec['cases']) == 121, 'scope differs')
    parser = {line['address']: line for line in json.loads(sources['parser'])['lines']}
    need(all(parser[a['address']] == a for a in spec['fieldContract']['parserAnchors']), 'cost parser anchor differs')
    parent = json.loads(sources['pilotSpec'])
    consumer = {line['address']: line for line in json.loads(sources['ordinaryConsumer'])['lines']}
    need(all(consumer[a['address']] == a for a in parent['independentConsumerResponsibility']['anchors']),
         'independent consumer anchor differs')
    need(parser['0x149b57100']['hex'] == '31f6' and parser['0x149b5711a']['hex'] == '0f85c6010000' and
         parser['0x149b58b83']['hex'] == '660fefc9', 'selector zero/absent Float source differs')
    elements = json.loads(sources['skillElementBatch'])
    need(elements['passed'] and len(elements['javaProjectionFragments']) == 369, 'element admission differs')
    element_keys = [f['key'] for f in elements['javaProjectionFragments']]
    depot = json.loads(sources['depot'])['fragments']
    energy = sorted({f['javaFields']['energySkill'] for f in depot})
    need(len(depot) == len(energy) == 121 and energy == spec['energyKeys'] and
         set(energy) <= set(element_keys) and len(set(element_keys)) == 369, '121 Depot/369 Skill set differs')
    flags = json.loads(sources['flags'])
    table, = [t for t in json.loads(sources['mapping'])['native']['parsedTables'] if t['name'] == 'AvatarSkill']
    need(table['payloadSHA256'] == spec['rawPin']['sha256'], 'same table raw identity differs')
    need([c['key'] for c in spec['cases']] == energy, 'case keys differ')
    for case in spec['cases']:
        key, source = case['key'], case['source']
        flag, = [r for r in flags['rows'] if r['skillID'] == key]
        mapped, = [r for r in table['rows'] if r['key'] == key]
        element, = [r for r in elements['javaProjectionFragments'] if r['key'] == key]
        need(flag['source'] == mapped['source'] == element['source'] == source and
             case['maskSpan'] == flag['maskSpan'] and case['rawMaskHex'] == flag['rawMaskHex'] and
             case['effectiveMaskHex'] == flag['effectiveMaskHex'], 'logical ID/source/mask differs')
        original = int(case['rawMaskHex'], 16)
        effective = original ^ int(flags['rawMaskXorHex'], 16)
        mask = bytes.fromhex(case['maskSpan']['rawHex'])
        effective_bytes = bytes(a ^ b for a, b in zip(mask, int(flags['rawMaskXorHex'], 16).to_bytes(8, 'little')))
        need(int.from_bytes(mask, 'little') == original and f'{effective:016x}' == case['effectiveMaskHex'], 'mask relation differs')
        bits = {bit: (original >> bit) & 1 for bit in (53, 11)}
        need(all(bits[bit] == ((mask[bit // 8] >> (bit % 8)) & 1) for bit in bits) and
             ((effective >> 4) & 1) == ((effective_bytes[0] >> 4) & 1), 'independent byte bits differ')
        need(bool(bits[53]) == case['costPresent'] and bool(bits[11]) == case['lastTokenPresent'] and
             (not bool(effective & 16)) == case['sourceOrdinarySelectorZero'], 'presence/selector differs')
        if bits[53]:
            offset = source['end'] - 4 * bits[11] - 4
            need(offset == case['wordOffset'] and source['begin'] + 8 <= offset and
                 offset + 4 + 4 * bits[11] == source['end'], 'suffix position/bounds differs')
        else:
            need(case['wordOffset'] is None and source['begin'] + 8 <= source['end'] - 4 * bits[11],
                 'absent branch/cursor differs')
    need([c['key'] for c in spec['cases'] if c['costPresent']] == spec['partitions']['presentKeys'] and
         [c['key'] for c in spec['cases'] if not c['costPresent']] == spec['partitions']['absentKeys'] == [11065, 11145],
         'present/absent partition differs')
    need([c['key'] for c in spec['cases'] if c['disposition'] == 'read4'] == spec['partitions']['newRawWordKeys'] and
         [c['key'] for c in spec['cases'] if c['disposition'] == 'reuse-10019-pilot'] == [10019], 'read/reuse partition differs')
    pilot = json.loads(sources['pilotResult'])
    need(pilot['passed'] and pilot['fragment']['key'] == 10019 and
         pilot['fragment']['javaFields']['costElemVal'] == 80 and pilot['costEvidence']['float32Bits'] == '42a00000',
         'pilot control differs')
    return elements, pilot


def need(condition, message):
    if not condition:
        raise ValueError(message)


def sha(blob):
    return hashlib.sha256(blob).hexdigest()


def read_pin(name, pin):
    STATE.update(stage='input-pin', currentInput=name)
    blob = (ROOT / pin['path']).read_bytes()
    actual = {'name': name, 'path': pin['path'], 'bytes': len(blob), 'sha256': sha(blob)}
    STATE['inputReads'].append(actual)
    need((actual['bytes'], actual['sha256']) == (pin['bytes'], pin['sha256']), 'input pin differs: ' + name)
    return blob


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def write(name, blob):
    with (OUT / name).open('xb') as stream:
        stream.write(blob)


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=False)
    try:
        main()
    except Exception as error:
        write('failure.private.json', encode({'task': TASK, 'passed': False, 'errorType': type(error).__name__,
                                             'error': str(error), 'state': STATE, 'resourceReady': False}))
        print(f'{TASK}: stopped at {STATE["stage"]}: {error}', file=sys.stderr)
        sys.exit(1)
