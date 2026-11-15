"""Project the fixed saved-Depot Skill cohort using admitted ID and element sources."""

import hashlib
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parent.parent
SPEC_PATH = 'debug/d02-skill-energy-search-20261116/element-batch-spec/spec.private.json'
SPEC_SIZE = 305899
SPEC_SHA = '84de972aad8fc82e3008fb57dd4a3d891f505eea49a1da26ff8c27eaf25e3700'
TASK = 'R3-SKILL-ELEMENT-SUFFIX-HOST-BATCH-20261116'
OUTPUT = ROOT / 'debug/d02-skill-energy-search-20261116/element-host-batch'
STATE = {'stage': 'entry', 'fileReads': [], 'semanticRawReads': [], 'fragments': []}


def main():
    spec = json.loads(read_pin('batchSpec', {'path': SPEC_PATH, 'bytes': SPEC_SIZE, 'sha256': SPEC_SHA}))
    blobs = {name: read_pin(name, pin) for name, pin in spec['inputPins'].items()}
    mapping, flags, pilot = validate_sources(spec, blobs)
    raw = read_pin('raw', spec['rawPin'])
    validate_spans(spec, raw)
    enums = {row['targetValue']: row for row in mapping['enumMap']}
    for case in spec['cases']:
        STATE['fragments'].append(project_case(case, pilot, enums, raw))
    for key in spec['cohort']['reusedJavaApprovedKeys']:
        old, = [row for row in mapping['javaProjectionFragments'] if row['table'] == 'AvatarSkill' and row['key'] == key]
        STATE['fragments'].append({
            'table': 'AvatarSkill', 'key': key, 'source': old['source'], 'javaFields': old['javaFields'],
            'fragmentOnly': True, 'idRawDecoded': False, 'logicalIDSource': 'accepted tableKey identity',
            'elementEvidence': {'disposition': 'reused-Java-admitted-control',
                                'originalMask': flags[key]['rawMaskHex'],
                                'elementPresent': bool(int(flags[key]['rawMaskHex'], 16) & (1 << 25))},
        })
    STATE['fragments'].sort(key=lambda row: row['key'])
    need([row['key'] for row in STATE['fragments']] == spec['cohort']['demandKeys'], 'final demand key set differs')
    need(len(STATE['semanticRawReads']) == 119 and semantic_bytes() == 476, 'actual semantic budget/count differs')
    STATE['stage'] = 'complete'
    report = {
        'task': TASK, 'passed': True, 'inputPins': STATE['fileReads'], 'cohort': spec['cohort'],
        'javaProjectionFragments': STATE['fragments'], 'rawReadLedger': {
            'physicalRawDiskBytes': len(raw), 'wholeRawHashPasses': 1,
            'selectedNewSpanHashBytesFromSameBuffer': 43048, 'knownMaskHashBytesFromSameBuffer': 2936,
            'semanticReads': STATE['semanticRawReads'], 'semanticBytes': semantic_bytes(),
            'newMaskSemanticBytes': 0, 'notMeasured': spec['budget']['notMeasured'],
        },
        'summary': {'demand': 369, 'newAdmissionFragments': 367, 'oldJavaReused': 2,
                    'newPresent': 120, 'newAbsentDirectZero': 247, 'newRawWords': 119,
                    'reusedPilot10019': 1, 'energyKeys': 121},
        'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False,
        'fullDeclaredSkillRow': False, 'nativeExecuted': False, 'javaExecuted': False,
        'costAndSpecialFields': 'not projected or admitted; all121 energy rows remain unclosed',
    }
    result = encode(report)
    manifest = encode({
        'task': TASK, 'passed': True, 'specSHA256': SPEC_SHA,
        'toolSHA256': sha(Path(__file__).read_bytes()),
        'resultBytes': len(result), 'resultSHA256': sha(result),
        'outputByteBudget': 1048576, 'inputPins': STATE['fileReads'],
        'semanticRawBytes': semantic_bytes(), 'completedFragments': len(STATE['fragments']),
        'idRawDecoded': False, 'fragmentOnly': True, 'resourceReady': False,
    })
    need(len(result) + len(manifest) <= 1048576, 'combined output exceeds admitted budget')
    write('result.private.json', result)
    write('manifest.private.json', manifest)
    counts = {}
    for fragment in STATE['fragments']:
        symbol = fragment['javaFields']['costElemType']
        counts[symbol] = counts.get(symbol, 0) + 1
    print(json.dumps({'task': TASK, 'passed': True, 'summary': report['summary'],
                      'elementCounts': counts, 'semanticRawBytes': semantic_bytes(),
                      'resultBytes': len(result), 'resultSHA256': sha(result),
                      'manifestSHA256': sha(manifest), 'combinedOutputBytes': len(result) + len(manifest)},
                     ensure_ascii=False))


def need(condition, message):
    if not condition:
        raise ValueError(message)


def sha(blob):
    return hashlib.sha256(blob).hexdigest()


def read_pin(name, pin):
    STATE.update(stage='input-pin', currentInput=name)
    blob = (ROOT / pin['path']).read_bytes()
    actual = {'name': name, 'path': pin['path'], 'bytes': len(blob), 'sha256': sha(blob)}
    STATE['fileReads'].append(actual)
    need((actual['bytes'], actual['sha256']) == (pin['bytes'], pin['sha256']), f'pin mismatch: {name}')
    return blob


def validate_sources(spec, blobs):
    STATE['stage'] = 'source-contract'
    need(spec['executionTask'] == TASK and spec['budget']['newRawSemanticBytes'] == 476, 'batch contract differs')
    original = json.loads(blobs['pilotSpec'])
    parser = json.loads(blobs['parser'])
    need(parser['lines'][1689:1802] == original['tail']['instructions'] and
         len(original['tail']['instructions']) == 113, 'admitted tail source differs')
    anchor = {row['address']: row for row in parser['lines'][1689:1802]}
    need([anchor[address]['hex'] for address in ('0x149b58ac2', '0x149b58ac8', '0x149b58ace', '0x149b58b17')] ==
         ['31c9', 'f7c600000002', '7447', '41894d54'], 'absent direct-zero branch differs')
    mapping = json.loads(blobs['mapping'])
    table, = [table for table in mapping['native']['parsedTables'] if table['name'] == 'AvatarSkill']
    need(table['count'] == len(table['rows']) == 1176 and table['cursor'] == 141702 and
         table['payloadSHA256'] == spec['rawPin']['sha256'], 'same-row identity table differs')
    flag_rows = json.loads(blobs['flags'])['rows']
    flags = {row['skillID']: row for row in flag_rows}
    need(len(flags) == len(flag_rows) == 371, 'admitted flag cohort differs')
    depot = json.loads(blobs['depot'])
    demand = {key for row in depot['fragments'] for key in row['javaFields']['skills'] + [row['javaFields']['energySkill']] if key > 0}
    energy = {row['javaFields']['energySkill'] for row in depot['fragments']}
    old = set(depot['skillReferences']['approvedIntersection'])
    cohort = spec['cohort']
    need(len(depot['fragments']) == len(energy) == 121 and old == {10067, 10068} and
         sorted(demand) == cohort['demandKeys'] and len(demand) == 369 and
         sorted(demand - old) == cohort['newKeys'] and len(cohort['newKeys']) == 367 and
         sorted(energy) == cohort['energyKeys'] and cohort['reusedJavaApprovedKeys'] == sorted(old),
         'exact demand/new/old/energy partition differs')
    need([case['tableKey'] for case in spec['cases']] == cohort['newKeys'], 'case set differs')
    for case in spec['cases']:
        key = case['tableKey']
        row = flags[key]
        original_row = table['rows'][case['mappingRowIndex']]
        need(original_row['key'] == key and original_row['source'] == row['source'] == case['source'] and
             row['rawMaskHex'] == case['rawMask'] and row['maskSpan'] == case['knownMaskSpan'],
             f'key/span/mask identity differs: {key}')
        need(case['energyReference'] == (key in energy), 'energy reference identity differs')
    need(sum(case['elementPresent'] for case in spec['cases']) == 120 and
         sum(not case['elementPresent'] for case in spec['cases']) == 247 and
         all(int(flags[key]['rawMaskHex'], 16) & (1 << 25) for key in energy), 'present/absent/energy partition differs')
    need(11101 in cohort['newKeys'] and not flags[11101]['gates']['elementTokenPresent'] and
         11364 not in demand, '11101/control classification differs')
    for key, expected in ((10067, 'None'), (10068, 'Wind')):
        matches = [row for row in mapping['javaProjectionFragments'] if row['table'] == 'AvatarSkill' and row['key'] == key]
        need(len(matches) == 1 and matches[0]['javaFields'] == {'id': key, 'costElemType': expected} and
             matches[0]['source'] == flags[key]['source'], 'old Java-admitted control differs')
    need(not int(flags[10067]['rawMaskHex'], 16) & (1 << 25), 'old None control does not use absent branch')
    pilot = json.loads(blobs['pilotResult'])
    need(pilot['passed'] and pilot['idRawDecoded'] is False, 'accepted pilot flags differ')
    pilot_rows = {row['tableKey']: row for row in pilot['cases']}
    need({key: (row['logicalEnumUInt32'], row['targetSymbol']) for key, row in pilot_rows.items()} ==
         {10068: (7, 'Wind'), 11364: (4, 'Electric'), 10019: (5, 'Ice')} and
         pilot_rows[10019]['source'] == flags[10019]['source'], 'reused pilot controls differ')
    return mapping, flags, pilot_rows


def validate_spans(spec, raw):
    STATE['stage'] = 'raw-span-hash'
    span_bytes = mask_bytes = 0
    for case in spec['cases']:
        source, mask = case['source'], case['knownMaskSpan']
        need(0 <= source['begin'] < source['end'] <= len(raw) and
             sha(raw[source['begin']:source['end']]) == source['sha256'], 'raw source span differs')
        need(mask['begin'] == source['begin'] and mask['end'] == source['begin'] + 8 and
             sha(raw[mask['begin']:mask['end']]) == mask['sha256'], 'known mask hash differs')
        span_bytes += source['end'] - source['begin']
        mask_bytes += 8
    need((span_bytes, mask_bytes) == (43048, 2936), 'source hash byte ledger differs')


def semantic_bytes():
    return sum(row['bytes'] for row in STATE['semanticRawReads'])


def project_case(case, pilot, enums, raw):
    key, source = case['tableKey'], case['source']
    STATE.update(stage='element-projection', currentKey=key)
    mask = int(case['rawMask'], 16)
    mask_bytes = bytes.fromhex(case['knownMaskSpan']['rawHex'])
    bits = {str(bit): (mask >> bit) & 1 for bit in (25, 54, 53, 11)}
    independent_bits = {str(bit): (mask_bytes[bit // 8] >> (bit % 8)) & 1 for bit in (25, 54, 53, 11)}
    need(int.from_bytes(mask_bytes, 'little') == mask and bits == independent_bits == case['maskBits'] and
         bool(bits['25']) == case['elementPresent'], 'independent original-mask bit conflict')
    tail = bits['54'] + 4 * bits['53'] + 4 * bits['11']
    evidence = {'originalMask': case['rawMask'], 'elementPresent': case['elementPresent'],
                'disposition': case['elementDisposition'], 'maskBits': bits,
                'byteIndexBitsMatch': True, 'tailBytes': tail}
    if case['elementDisposition'] == 'source-absent-direct-zero':
        need(bits['25'] == 0 and case['elementOffset'] is None, 'absent branch contract differs')
        value, cursor = 0, source['end'] - tail
        evidence['sourceBranch'] = 'ECX=0; original-bit25 JE; direct DWORD[row+54]=ECX'
    elif case['elementDisposition'] == 'reuse-pilot-10019':
        need(key == 10019 and bits['25'] == 1, 'pilot reuse identity differs')
        value, cursor = pilot[key]['logicalEnumUInt32'], pilot[key]['elementOffset'] + 4
        need(case['elementOffset'] == pilot[key]['elementOffset'], 'pilot reuse offset differs')
        evidence['reusedPilotResultSHA256'] = '168d9f9cefaea799455bb1c503d7c177348e04e82695ab3b48a42d304cb886f4'
    else:
        need(case['elementDisposition'] == 'read-four-bytes' and bits['25'] == 1, 'present branch contract differs')
        offset = source['end'] - tail - 4
        need(offset == case['elementOffset'] and source['begin'] + 8 <= offset and offset + 4 <= source['end'],
             'element offset/bounds conflict')
        need(semantic_bytes() + 4 <= 476, 'semantic byte budget exceeded')
        word_bytes = raw[offset:offset + 4]
        STATE['semanticRawReads'].append({'tableKey': key, 'begin': offset, 'end': offset + 4,
                                          'bytes': 4, 'rawHex': word_bytes.hex(), 'sha256': sha(word_bytes)})
        value = ((int.from_bytes(word_bytes, 'little') + 0x5a02b85a) & 0xffffffff) ^ 0xdd69ea57
        carry, digits = 0, []
        for digit, constant in zip(word_bytes, b'\x5a\xb8\x02\x5a'):
            total = digit + constant + carry
            digits.append(total & 255)
            carry = total >> 8
        literal = bytes(digit ^ constant for digit, constant in zip(digits, b'\x57\xea\x69\xdd'))
        need(value == int.from_bytes(literal, 'little') and
             (((value ^ 0xdd69ea57) - 0x5a02b85a) & 0xffffffff).to_bytes(4, 'little') == word_bytes,
             'literal arithmetic/inverse conflict')
        cursor = offset + 4
        evidence.update(elementOffset=offset, literalArithmeticAndInverseMatch=True)
    need(source['begin'] + 8 <= cursor and cursor + tail == source['end'], 'same-row exact suffix end conflict')
    need(value in enums and enums[value]['accepted'] and enums[value]['javaValue'] == value and
         enums[value]['targetSymbol'] == enums[value]['javaSymbol'] and 0 <= value <= 9,
         'target/Java enum mismatch or inadmissible target-only/Default value')
    evidence.update(logicalEnumUInt32=value, cursorAfterElement=cursor, cursorAtEnd=cursor + tail)
    return {'table': 'AvatarSkill', 'key': key, 'source': source,
            'javaFields': {'id': key, 'costElemType': enums[value]['javaSymbol']},
            'fragmentOnly': True, 'idRawDecoded': False, 'logicalIDSource': 'accepted tableKey identity',
            'elementEvidence': evidence}


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def write(name, blob):
    with (OUTPUT / name).open('xb') as stream:
        stream.write(blob)


if __name__ == '__main__':
    OUTPUT.mkdir(parents=True, exist_ok=False)
    try:
        main()
    except Exception as error:
        write('failure.private.json', encode({'task': TASK, 'passed': False, 'errorType': type(error).__name__,
                                              'error': str(error), 'state': STATE,
                                              'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False}))
        print(f'{TASK}: stopped at {STATE["stage"]}: {error}', file=sys.stderr)
        sys.exit(1)
