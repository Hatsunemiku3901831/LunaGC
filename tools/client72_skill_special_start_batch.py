"""Merge source-proven START fields, reusing two pilot values and no raw reads."""

import copy
import hashlib
import json
import math
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parent.parent
TASK = 'R3-OLD121-SKILL-SPECIAL-START-HOST-BATCH-20261116'
SPEC = {'path': 'debug/d02-skill-energy-search-20261116/local-analysis/special-start-host-batch-spec.private.json',
        'bytes': 72466, 'sha256': 'c7f530ccee6df14ed0fbb6629b1751bc630fb3f3ec3d5b1b38258b8bf5591a06'}
OUT = ROOT / 'debug/d02-skill-energy-search-20261116/special-start-host-batch'
STATE = {'stage': 'entry', 'inputReads': [], 'completedKeys': [], 'newRawReads': 0}


def main():
    spec = json.loads(read_pin('spec', SPEC))
    sources = {name: json.loads(read_pin(name, pin)) for name, pin in spec['inputPins'].items()}
    cases = validate_sources(spec, sources)
    STATE['stage'] = 'source-field-merge'
    fragments = copy.deepcopy(sources['costBatch']['javaProjectionFragments'])
    for case in cases:
        key = case['key']
        fragment, = [r for r in fragments if r['key'] == key]
        if case['originalBit30Present']:
            pilot, = [r for r in sources['pilotResult']['fragments'] if r['key'] == key]
            evidence = copy.deepcopy(pilot['startEvidence'])
            evidence['reusedPilotResultSHA256'] = spec['inputPins']['pilotResult']['sha256']
            evidence['disposition'] = 'reuse-special-pilot'
            value = pilot['javaFields']['specialEnergyMax']
        else:
            value = 0.0
            evidence = {'sourceField': '88602.field440585/+74', 'originalMask': case['originalMask'],
                        'originalBit30Present': False, 'disposition': case['disposition'],
                        'sourceAbsentRule': spec['sourceRule']['absent'], 'float32Bits': '00000000',
                        'float32Value': 0.0, 'byteIndexBitsMatch': True,
                        'role': 'START requirement; not MAX cap'}
        fragment['javaFields']['specialEnergyMax'] = value
        fragment['startEvidence'] = evidence
        STATE['completedKeys'].append(key)
    STATE['stage'] = 'complete'
    report = {'task': TASK, 'passed': True, 'inputPins': STATE['inputReads'], 'energyKeys': spec['energyKeys'],
              'javaProjectionFragments': fragments,
              'summary': {'skillFragments': 369, 'energyFourKeyFragments': 121, 'otherTwoKeyFragments': 248,
                          'startPresentReused': 2, 'startSourceProvenAbsent': 119,
                          'startValues': {'0.0': 119, '50.0': 1, '100.0': 1},
                          'costPresent': 119, 'costSourceProvenAbsent': 2},
              'rawReadLedger': {'newRawReads': 0, 'newSemanticRawBytes': 0, 'newMaskSemanticBytes': 0,
                                'PEOrMetadataReads': 0, 'existingSourceReads': len(STATE['inputReads']),
                                'unmeasured': 'OS/interpreter/module-loader IO'},
              'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False, 'fullSkill': False,
              'specialEnergyMinOrMaximumCapProven': False, 'actualAvailabilityOrMaxEnergyAdoptionProven': False,
              'nativeExecuted': False, 'javaExecuted': False}
    result = encode(report)
    manifest = encode({'task': TASK, 'passed': True, 'resultBytes': len(result), 'resultSHA256': sha(result),
                       'toolSHA256': sha(Path(__file__).read_bytes()), 'specSHA256': SPEC['sha256'],
                       'newRawReads': 0, 'newSemanticRawBytes': 0, 'resourceReady': False})
    need(len(result) + len(manifest) <= spec['budget']['combinedOutputBytes'], 'output budget exceeded')
    write('result.private.json', result)
    write('manifest.private.json', manifest)
    print(json.dumps({'task': TASK, 'passed': True, 'summary': report['summary'], 'newRawReads': 0,
                      'resultBytes': len(result), 'resultSHA256': sha(result), 'manifestSHA256': sha(manifest),
                      'combinedOutputBytes': len(result) + len(manifest)}))


def validate_sources(spec, sources):
    STATE['stage'] = 'source-contract'
    need(spec['executionTask'] == TASK and len(sources) == 8 and len(spec['cases']) == 121, 'scope differs')
    parser = {line['address']: line for line in sources['parser']['lines']}
    need(all(parser[a['address']] == a for a in spec['sourceRule']['anchors']), 'absent Float path source differs')
    cost = sources['costBatch']
    fragments = cost['javaProjectionFragments']
    need(cost['passed'] and len(fragments) == 369 and len({r['key'] for r in fragments}) == 369,
         'cost batch admission differs')
    energy = sorted({r['javaFields']['energySkill'] for r in sources['depot']['fragments']})
    need(len(energy) == 121 and energy == cost['energyKeys'] == spec['energyKeys'] == [c['key'] for c in spec['cases']],
         'energy/case set differs')
    need(sources['pilotResult']['passed'] and [r['key'] for r in sources['pilotResult']['fragments']] == [11065, 11145],
         'pilot admission differs')
    for case in spec['cases']:
        key = case['key']
        flag, = [r for r in sources['flags']['rows'] if r['skillID'] == key]
        fragment, = [r for r in fragments if r['key'] == key]
        need(flag['source'] == fragment['source'] == case['source'] and flag['rawMaskHex'] == case['originalMask'] and
             flag['maskSpan'] == case['maskSpan'], 'same key/source/mask differs')
        mask = int(flag['rawMaskHex'], 16)
        mask_bytes = bytes.fromhex(flag['maskSpan']['rawHex'])
        bit = mask >> 30 & 1
        need(int.from_bytes(mask_bytes, 'little') == mask and bit == mask_bytes[3] >> 6 & 1 and
             bool(bit) == case['originalBit30Present'], 'independent presence bit differs')
        if bit:
            pilot, = [r for r in sources['pilotResult']['fragments'] if r['key'] == key]
            value = pilot['javaFields']['specialEnergyMax']
            bits = pilot['startEvidence']['float32Bits']
            need(case['disposition'] == 'reuse-special-pilot' and pilot['source'] == fragment['source'] and
                 math.isfinite(value) and value >= 0 and struct.pack('<f', value).hex() == bytes.fromhex(bits)[::-1].hex(),
                 'reused pilot source/value/Float bits differs')
        else:
            need(case['disposition'] == 'source-PXOR-logical-Float32-zero', 'absent disposition differs')
        need(set(fragment['javaFields']) == {'id', 'costElemType', 'costElemVal'} and 'specialEnergyMin' not in fragment['javaFields'],
             'energy input field set differs')
    need([c['key'] for c in spec['cases'] if c['originalBit30Present']] == spec['presentReusedKeys'] == [11065, 11145] and
         [c['key'] for c in spec['cases'] if not c['originalBit30Present']] == spec['sourceProvenAbsentKeys'] and
         len(spec['sourceProvenAbsentKeys']) == 119, 'presence partition differs')
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
