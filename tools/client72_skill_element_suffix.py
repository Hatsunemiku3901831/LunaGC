"""Read one admitted Skill suffix field for two controls and one new Depot reference."""

import hashlib
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parent.parent
SPEC = {
    'path': 'debug/d02-skill-energy-search-20261116/local-analysis/element-suffix-source-spec.private.json',
    'bytes': 34086,
    'sha256': '1a4f63d76ab810cbf8cda12954131b7ce471a3491b6c1aec410ae98701636fed',
}
OUTPUT = ROOT / 'debug/d02-skill-energy-search-20261116/element-host-pilot'
TASK = 'R3-SKILL-ELEMENT-SUFFIX-HOST-PILOT-20261116'
STATE = {'stage': 'entry', 'fileReads': [], 'semanticRawReads': [], 'completedCases': []}


def main():
    spec = json.loads(read_pin('spec', SPEC))
    blobs = {name: read_pin(name, pin) for name, pin in spec['inputs'].items()}
    mapping, flags = validate_sources(spec, blobs)
    raw = read_pin('raw', spec['rawInputForNextTask'])
    validate_raw_spans(spec, raw)
    for case in spec['cases']:
        STATE['completedCases'].append(project_element(case, spec, mapping, flags, raw))
    STATE['stage'] = 'complete'
    result = {
        'task': TASK, 'passed': True, 'inputPins': STATE['fileReads'],
        'cases': STATE['completedCases'], 'rawReadLedger': raw_ledger(spec),
        'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False,
        'nativeExecuted': False, 'javaExecuted': False,
        'remainingCompleteNewSkillFragments': 367,
        'remainingPositiveEnergyBindings': 120,
        'remainingEnergyCostSpecialRows': 121,
    }
    result_bytes = write_json('result.private.json', result)
    manifest = {
        'task': TASK, 'passed': True, 'toolSHA256': digest(Path(__file__).read_bytes()),
        'specSHA256': SPEC['sha256'], 'resultBytes': len(result_bytes),
        'resultSHA256': digest(result_bytes), 'outputBudgetBytes': 65536,
        'state': STATE,
    }
    manifest_bytes = write_json('manifest.private.json', manifest)
    need(len(result_bytes) + len(manifest_bytes) <= 65536, 'output budget exceeded')
    print(json.dumps({
        'task': TASK, 'passed': True,
        'values': [{k: row[k] for k in ('tableKey', 'logicalEnumUInt32', 'targetSymbol')}
                   for row in STATE['completedCases']],
        'semanticRawBytes': sum(x['bytes'] for x in STATE['semanticRawReads']),
        'resultSHA256': digest(result_bytes), 'outputBytes': len(result_bytes) + len(manifest_bytes),
        'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False,
    }, ensure_ascii=False))


def need(condition, message):
    if not condition:
        raise ValueError(message)


def digest(blob):
    return hashlib.sha256(blob).hexdigest()


def read_pin(name, pin):
    STATE.update(stage='input-pin', currentInput=name)
    blob = (ROOT / pin['path']).read_bytes()
    actual = {'name': name, 'path': pin['path'], 'bytes': len(blob), 'sha256': digest(blob)}
    STATE['fileReads'].append(actual)
    need((actual['bytes'], actual['sha256']) == (pin['bytes'], pin['sha256']),
         f'input pin mismatch: {name}')
    return blob


def validate_sources(spec, blobs):
    STATE['stage'] = 'source-contract'
    need(set(blobs) == {'mapping', 'flags', 'depot', 'parser', 'naturalSource', 'nativeText',
                        'UIntSetter', 'FloatSetter', 'minimum'}, 'expected exactly nine source inputs')
    need([c['key'] for c in spec['cases']] == [10068, 11364, 10019], 'three-key scope differs')
    parser = json.loads(blobs['parser'])
    need(len(parser['lines']) == 1985 and
         parser['lines'][1689:1802] == spec['tail']['instructions'] and
         len(spec['tail']['instructions']) == 113, 'admitted suffix source differs')
    natural = blobs['naturalSource'].decode('utf-8')
    need('self.pending_rows[e.read(e.reg(\'rsp\'))] = (e.reg(\'rcx\'), e.read(self.reader + 0x1c, \'I\'))' in natural and
         "end=e.read(self.reader + 0x1c, 'I')" in natural and
         "spans = {row['pointer']: row for row in self.spans}" in natural,
         'admitted parser-return cursor provenance differs')
    native_text = blobs['nativeText'].decode('utf-8')
    need(all(line in native_text for line in spec['identitySeparateFromElement']['wholeKeyRelationSource'].splitlines()),
         'admitted table-key relation source differs')

    mapping = json.loads(blobs['mapping'])
    tables = [t for t in mapping['native']['parsedTables'] if t['name'] == 'AvatarSkill']
    need(len(tables) == 1, 'Skill table selection is not unique')
    table = tables[0]
    need(table['count'] == len(table['rows']) == 1176 and table['cursor'] == 141702 and
         table['payloadSHA256'] == spec['rawInputForNextTask']['sha256'], 'table identity differs')
    rows = table['rows']
    for case in spec['cases']:
        i = case['mappingRowIndex']
        row = rows[i]
        need(sum(r['key'] == case['key'] for r in rows) == 1 and
             row['key'] == case['key'] and row['source'] == case['source'], 'key/span identity differs')
        need(rows[i - 1]['source']['end'] == case['adjacentEndBefore'] == case['source']['begin'] and
             rows[i + 1]['source']['begin'] == case['adjacentBeginAfter'] == case['source']['end'],
             'admitted neighbor identity differs')

    flag_report = json.loads(blobs['flags'])
    flag_rows = flag_report['rows']
    need(len(flag_rows) == flag_report['selection']['union'] == 371 and
         not any(r['skillID'] == 11364 for r in flag_rows), 'out-of-cohort control relation differs')
    flags = {}
    for case in (spec['cases'][0], spec['cases'][2]):
        matches = [r for r in flag_rows if r['skillID'] == case['key']]
        need(len(matches) == 1, 'admitted flag row selection differs')
        row = matches[0]
        need(row['source'] == case['source'] and row['rawMaskHex'] == case['knownInputOnly']['mask'] and
             row['maskSpan'] == case['knownInputOnly']['maskSpan'], 'admitted mask identity differs')
        flags[case['key']] = row

    depot = json.loads(blobs['depot'])
    depots = [r for r in depot['fragments'] if r['key'] == 201]
    need(len(depots) == 1 and depots[0]['javaFields']['energySkill'] == 10019 and
         depots[0]['source'] == spec['controls']['new10019']['depotSource'], 'current Depot reference differs')
    old = [r for r in mapping['javaProjectionFragments'] if r['table'] == 'AvatarSkill' and r['key'] == 10068]
    need(len(old) == 1 and old[0]['javaFields']['costElemType'] == 'Wind' and
         old[0]['source'] == spec['cases'][0]['source'], 'old Wind control differs')
    named = [r for r in mapping['native']['namedElementCases'] if r['avatarId'] == 10000136]
    need(len(named) == 1 and named[0] == spec['controls']['independent11364']['oracle'],
         'independent named control differs')
    need(named[0]['returned'] == 4 and named[0]['trace']['models'] == [] and
         int(named[0]['calls'][-1]['rdx'], 16) == 11364, 'named Electric control chain differs')
    need(len({e['targetValue'] for e in mapping['enumMap']}) == len(mapping['enumMap']) and
         [e['targetValue'] for e in mapping['enumMap'] if e['accepted']] == list(range(10)),
         'admitted enum domain differs')
    return mapping, flags


def validate_raw_spans(spec, raw):
    STATE['stage'] = 'raw-span-hash'
    for case in spec['cases']:
        source = case['source']
        need(0 <= source['begin'] < source['end'] <= len(raw) and
             digest(raw[source['begin']:source['end']]) == source['sha256'], 'raw source span differs')
        known = case['knownInputOnly']
        if known['mask'] is not None:
            span = known['maskSpan']
            need(digest(raw[span['begin']:span['end']]) == span['sha256'], 'known mask hash differs')


def semantic_read(raw, start, size, label):
    need(0 <= start <= start + size <= len(raw), 'semantic raw read out of bounds')
    need(sum(x['bytes'] for x in STATE['semanticRawReads']) + size <= 20, 'semantic raw budget exceeded')
    blob = raw[start:start + size]
    STATE['semanticRawReads'].append({'label': label, 'begin': start, 'end': start + size,
                                      'bytes': size, 'rawHex': blob.hex(), 'sha256': digest(blob)})
    return blob


def project_element(case, spec, mapping, flags, raw):
    key, source = case['key'], case['source']
    STATE.update(stage='element-field', currentKey=key)
    if key in flags:
        mask_bytes = bytes.fromhex(flags[key]['maskSpan']['rawHex'])
        mask = int(flags[key]['rawMaskHex'], 16)
        need(int.from_bytes(mask_bytes, 'little') == mask, 'existing mask byte identity differs')
    else:
        mask_bytes = semantic_read(raw, source['begin'], 8, f'{key}:new-mask')
        mask = int.from_bytes(mask_bytes, 'little')
    bits = {b: (mask >> b) & 1 for b in (25, 54, 53, 11)}
    byte_bits = {b: (mask_bytes[b // 8] >> (b % 8)) & 1 for b in (25, 54, 53, 11)}
    need(bits == byte_bits, 'integer/byte-index mask conflict')
    need(bits[25] == 1, 'selected row lacks element token')
    widths = [bits[54], 4 * bits[53], 4 * bits[11]]
    offset = source['end'] - sum(widths) - 4
    byte_offset = source['end'] - byte_bits[54] - 4 * byte_bits[53] - 4 * byte_bits[11] - 4
    need(offset == byte_offset and source['begin'] + 8 <= offset and offset + 4 <= source['end'],
         'element suffix offset/bounds conflict')
    known_offset = case['knownInputOnly']['elementOffset']
    need(known_offset is None or offset == known_offset, 'known source element offset conflict')
    word_bytes = semantic_read(raw, offset, 4, f'{key}:element-word')
    word = int.from_bytes(word_bytes, 'little')
    value = ((word + 0x5a02b85a) & 0xffffffff) ^ 0xdd69ea57
    carry, added = 0, []
    for digit, constant in zip(word_bytes, b'\x5a\xb8\x02\x5a'):
        total = digit + constant + carry
        added.append(total & 255)
        carry = total >> 8
    literal = bytes(digit ^ constant for digit, constant in zip(added, b'\x57\xea\x69\xdd'))
    need(value == int.from_bytes(literal, 'little'), 'literal byte arithmetic conflict')
    need((((value ^ 0xdd69ea57) - 0x5a02b85a) & 0xffffffff).to_bytes(4, 'little') == word_bytes,
         'inverse serialization conflict')
    enums = [e for e in mapping['enumMap'] if e['targetValue'] == value]
    need(len(enums) == 1 and enums[0]['accepted'] and enums[0]['javaValue'] == value and
         enums[0]['targetSymbol'] == enums[0]['javaSymbol'] and 0 <= value <= 9,
         'element enum is not admitted; target-only values and Java Default255 are rejected')
    enum = enums[0]
    expected = {10068: (7, 'Wind'), 11364: (4, 'Electric')}.get(key)
    need(expected is None or (value, enum['targetSymbol']) == expected, 'admitted nonzero control conflict')
    cursor = offset + 4
    cursor_steps = []
    for bit, width in zip((54, 53, 11), widths):
        cursor_steps.append({'bit': bit, 'begin': cursor, 'end': cursor + width, 'width': width})
        cursor += width
    need(cursor == source['end'], 'suffix cursor differs from admitted row end')
    return {
        'tableKey': key, 'role': case['role'], 'source': source, 'rawMask': f'{mask:016x}',
        'maskBits': bits, 'independentByteIndexBits': byte_bits,
        'tailWidthsWithSourceAnchors': {
            'bit54': {'width': widths[0], 'anchor': '0x149b58b1b'},
            'bit53': {'width': widths[1], 'anchor': '0x149b58b2f'},
            'bit11': {'width': widths[2], 'anchor': '0x149b58bef'},
        },
        'elementOffset': offset, 'elementRawWordHex': word_bytes.hex(),
        'logicalEnumUInt32': value, 'targetSymbol': enum['targetSymbol'], 'javaSymbol': enum['javaSymbol'],
        'controlResult': 'matched-admitted-nonzero-control' if expected else 'new-field-no-candidate-oracle',
        'inverseSerializationMatches': True, 'literalByteArithmeticMatches': True,
        'suffixCursorSteps': cursor_steps, 'cursorAtEnd': cursor,
        'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False,
    }


def raw_ledger(spec):
    return {
        'physicalRawDiskBytes': spec['rawInputForNextTask']['bytes'], 'wholeFileHashPasses': 1,
        'selectedSpanHashBytesFromSameBuffer': sum(c['source']['end'] - c['source']['begin'] for c in spec['cases']),
        'knownMaskHashBytesFromSameBuffer': 16,
        'semanticReads': STATE['semanticRawReads'],
        'semanticBytes': sum(x['bytes'] for x in STATE['semanticRawReads']),
        'semanticByteBudget': 20,
        'notMeasured': 'OS/filesystem buffering and interpreter/module-loader IO',
    }


def write_json(name, value):
    blob = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    need(len(blob) <= 65536, 'single output exceeds budget')
    with (OUTPUT / name).open('xb') as stream:
        stream.write(blob)
    return blob


if __name__ == '__main__':
    OUTPUT.mkdir(parents=True, exist_ok=False)
    try:
        main()
    except Exception as error:
        write_json('failure.private.json', {
            'task': TASK, 'passed': False, 'errorType': type(error).__name__, 'error': str(error),
            'state': STATE, 'fragmentOnly': True, 'idRawDecoded': False, 'resourceReady': False,
        })
        print(f'{TASK}: stopped at {STATE["stage"]}: {error}', file=sys.stderr)
        sys.exit(1)
