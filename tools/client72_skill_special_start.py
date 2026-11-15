"""Recover the two admitted special START thresholds from fixed Skill bytes."""

import hashlib
import json
import math
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parent.parent
TASK = 'R3-OLD121-SKILL-SPECIAL-START-HOST-PILOT-20261116'
SPEC = {'path': 'debug/d02-skill-energy-search-20261116/local-analysis/special-start-host-pilot-spec.private.json',
        'bytes': 27354, 'sha256': 'c67b46bc5b31ab3eac256ad24280a3840ac8c0ac2e172b1f7ff7daa92e0b3f4d'}
OUT = ROOT / 'debug/d02-skill-energy-search-20261116/special-start-host-pilot'
STATE = {'stage': 'entry', 'inputReads': [], 'semanticRawReads': [], 'completedKeys': []}


def main():
    spec = json.loads(read_pin('spec', SPEC))
    sources = {name: read_pin(name, pin) for name, pin in spec['inputs'].items()}
    cases = validate_sources(spec, sources)
    raw = read_pin('raw', spec['raw'])
    fragments = []
    for case in cases:
        STATE.update(stage='same-row-hash', currentKey=case['key'])
        source, mask = case['source'], case['maskSpan']
        need(sha(raw[source['begin']:source['end']]) == source['sha256'] and
             sha(raw[mask['begin']:mask['end']]) == mask['sha256'], 'raw source/mask hash differs')
        offset = case['wordOffset']
        STATE['stage'] = 'one-start-word'
        token = raw[offset:offset + 4]
        STATE['semanticRawReads'].append({'key': case['key'], 'begin': offset, 'end': offset + 4,
                                         'rawHex': token.hex(), 'sha256': sha(token), 'bytes': len(token)})
        word = struct.unpack('<I', token)[0]
        bits = (word + 0x70326507) & 0xffffffff
        carry, independent = 0, []
        for byte, constant in zip(token, b'\x07\x65\x32\x70'):
            total = byte + constant + carry
            independent.append(total & 255)
            carry = total >> 8
        need(bits.to_bytes(4, 'little') == bytes(independent), 'bytewise ADD/carry differs')
        value = struct.unpack('<f', bits.to_bytes(4, 'little'))[0]
        need(math.isfinite(value) and value >= 0, 'START threshold is not finite nonnegative Float32')
        need(struct.pack('<f', value) == bits.to_bytes(4, 'little') and
             ((int.from_bytes(struct.pack('<f', value), 'little') - 0x70326507) & 0xffffffff).to_bytes(4, 'little') == token,
             'float bits/inverse word differs')
        fragments.append({'table': 'AvatarSkill', 'key': case['key'], 'source': source,
                          'javaFields': {'id': case['key'], 'specialEnergyMax': value},
                          'logicalIDSource': 'accepted tableKey identity', 'idRawDecoded': False,
                          'fragmentOnly': True, 'fullSkill': False,
                          'startEvidence': {'sourceField': '88602.field440585/+74', 'originalMask': case['rawMaskHex'],
                                            'originalBit30Present': True, 'prefixWidths': case['prefixWidths'],
                                            'wordOffset': offset, 'rawWordHex': token.hex(), 'float32Bits': f'{bits:08x}',
                                            'float32Value': value, 'byteIndexBitsMatch': True,
                                            'byteAddCarryMatch': True, 'inverseWireMatch': True,
                                            'role': 'START requirement; current special energy >= same energySkill resource+74',
                                            'maximumCap': 'not this field; unknown'}})
        STATE['completedKeys'].append(case['key'])
    need(sum(r['bytes'] for r in STATE['semanticRawReads']) == 8, 'semantic raw budget differs')
    STATE['stage'] = 'complete'
    report = {'task': TASK, 'passed': True, 'inputPins': STATE['inputReads'], 'fragments': fragments,
              'candidateCrosscheck': {'source': spec['candidateCrosscheck'],
                                     'matches': {str(f['key']): f['javaFields']['specialEnergyMax'] ==
                                                 spec['candidateCrosscheck'][str(f['key']) + '.specialEnergyCostStart']
                                                 for f in fragments}, 'authority': 'separate comparison only, not forced values'},
              'rawReadLedger': {'physicalDiskBytes': len(raw), 'wholeHashPasses': 1,
                                'sourceSpanHashBytesFromSameBuffer': sum(c['source']['end'] - c['source']['begin'] for c in cases),
                                'knownMaskHashBytesFromSameBuffer': 16, 'newSemanticBytes': 8,
                                'semanticReads': STATE['semanticRawReads']},
              'fragmentOnly': True, 'resourceReady': False, 'fullSkill': False,
              'specialEnergyMinOrMaximumCapProven': False, 'actualAvailabilityProven': False,
              'all121SpecialFieldsComplete': False, 'nativeExecuted': False, 'javaExecuted': False}
    result = encode(report)
    manifest = encode({'task': TASK, 'passed': True, 'resultBytes': len(result), 'resultSHA256': sha(result),
                       'toolSHA256': sha(Path(__file__).read_bytes()), 'specSHA256': SPEC['sha256'],
                       'newSemanticRawBytes': 8, 'resourceReady': False})
    need(len(result) + len(manifest) <= spec['hostExecution']['maxOutputBytes'], 'output budget exceeded')
    write('result.private.json', result)
    write('manifest.private.json', manifest)
    print(json.dumps({'task': TASK, 'passed': True, 'values': {f['key']: f['javaFields']['specialEnergyMax'] for f in fragments},
                      'newSemanticRawBytes': 8, 'resultBytes': len(result), 'resultSHA256': sha(result),
                      'manifestSHA256': sha(manifest), 'combinedOutputBytes': len(result) + len(manifest)}))


def validate_sources(spec, sources):
    STATE['stage'] = 'source-contract'
    need(spec['proposedTask'] == TASK and [c['key'] for c in spec['cases']] == [11065, 11145], 'scope differs')
    parser = {line['address']: line for line in json.loads(sources['parser'])['lines']}
    consumer = {line['address']: line for line in json.loads(sources['specialConsumer'])['lines']}
    need(all(parser[a['address']] == a for a in spec['prefixRelation']['anchors']), 'START parser anchor differs')
    need(all(consumer[a['address']] == a for a in spec['responsibility']['anchors']), 'START consumer anchor differs')
    prefix = json.loads(sources['prefixSource'])['evidence']['sourcePrefixRelation']['steps']
    need(prefix[:8] == spec['prefixRelation']['steps'], 'admitted prefix relation differs')
    for step in prefix:
        need(parser[step['gateInstruction']['address']] == step['gateInstruction'] and
             parser[step['cursorAdvanceInstruction']['address']] == step['cursorAdvanceInstruction'], 'prefix anchor differs')
    table, = [t for t in json.loads(sources['mapping'])['native']['parsedTables'] if t['name'] == 'AvatarSkill']
    need(table['payloadSHA256'] == spec['raw']['sha256'], 'same table raw identity differs')
    flags = json.loads(sources['flags'])
    depots = json.loads(sources['depot'])['fragments']
    for case in spec['cases']:
        key, source = case['key'], case['source']
        mapped, = [r for r in table['rows'] if r['key'] == key]
        flag, = [r for r in flags['rows'] if r['skillID'] == key]
        depot, = [r for r in depots if r['key'] == case['actualDepot']['key']]
        need(mapped['source'] == flag['source'] == source and flag['rawMaskHex'] == case['rawMaskHex'] and
             flag['maskSpan'] == case['maskSpan'], 'logical ID/source/mask differs')
        need(depot['source'] == case['actualDepot']['source'] and depot['javaFields']['energySkill'] == key,
             'actual Depot energy reference differs')
        original = int(case['rawMaskHex'], 16)
        effective = original ^ int(flags['rawMaskXorHex'], 16)
        raw_bytes = bytes.fromhex(case['maskSpan']['rawHex'])
        effective_bytes = bytes(a ^ b for a, b in zip(raw_bytes, int(flags['rawMaskXorHex'], 16).to_bytes(8, 'little')))
        need(int.from_bytes(raw_bytes, 'little') == original and f'{effective:016x}' == flag['effectiveMaskHex'], 'mask relation differs')
        widths = []
        for step in prefix:
            mask = original if step['mask'] == 'raw' else effective
            mask_bytes = raw_bytes if step['mask'] == 'raw' else effective_bytes
            bit = step['bit']
            selected = (mask >> bit) & 1
            need(selected == ((mask_bytes[bit // 8] >> (bit % 8)) & 1), 'independent prefix bit differs')
            widths.append(step['whenBitSetConsumeBytes'] * selected)
        expected = [{'step': s['step'], 'bit': s['bit'], 'maskKind': s['mask'], 'bytes': width}
                    for s, width in zip(prefix[:8], widths[:8])]
        offset = source['begin'] + 8 + sum(widths[:8])
        first_string = source['begin'] + 8 + sum(widths)
        need(expected == case['prefixWidths'] and original >> 30 & 1 == raw_bytes[3] >> 6 & 1 == 1 and
             offset == case['wordOffset'] and offset + 4 <= first_string and first_string + 2 <= source['end'],
             'prefix position/presence/before-first-string bounds differs')
    return spec['cases']


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
