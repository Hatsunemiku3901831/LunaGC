"""Read the admitted ordinary-energy cost field for fixed Skill10019, host only."""

import hashlib
import json
import math
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parent.parent
SPEC = {'path': 'debug/d02-skill-energy-search-20261116/local-analysis/ordinary-cost-host-pilot-spec.private.json',
        'bytes': 22670, 'sha256': '7a4a793c8b9eba546efbfad3d897be6abb50a1374c9ad8cee1e02c9383bc3bd9'}
TASK = 'R3-OLD121-SKILL-ORDINARY-COST-HOST-PILOT-20261116'
OUT = ROOT / 'debug/d02-skill-energy-search-20261116/ordinary-cost-host-pilot'
STATE = {'stage': 'entry', 'inputReads': [], 'semanticRawReads': []}


def main():
    spec = json.loads(read_pin('spec', SPEC))
    sources = {name: read_pin(name, pin) for name, pin in spec['inputs'].items()}
    selected, bits = validate_sources(spec, sources)
    raw = read_pin('raw', spec['raw'])
    source, mask_span = selected['source'], selected['maskSpan']
    STATE['stage'] = 'raw-source-hash'
    need(sha(raw[source['begin']:source['end']]) == source['sha256'] and
         sha(raw[mask_span['begin']:mask_span['end']]) == mask_span['sha256'], 'raw source/mask hash differs')
    offset = source['end'] - 4 * bits['11'] - 4
    need(offset == selected['costWordOffset'] == 1590 and source['begin'] + 8 <= offset and
         offset + 4 + 4 * bits['11'] == source['end'], 'cost suffix offset/bounds differs')
    STATE['stage'] = 'one-cost-word'
    word_bytes = raw[offset:offset + 4]
    STATE['semanticRawReads'].append({'begin': offset, 'end': offset + 4, 'bytes': len(word_bytes),
                                      'rawHex': word_bytes.hex(), 'sha256': sha(word_bytes)})
    word = struct.unpack('<I', word_bytes)[0]
    independent_word = sum(byte << (index * 8) for index, byte in enumerate(word_bytes))
    float_bits = word ^ 0x8a4ce70f
    per_byte = bytes(byte ^ constant for byte, constant in zip(word_bytes, b'\x0f\xe7\x4c\x8a'))
    need(word == independent_word and float_bits == int.from_bytes(per_byte, 'little'), 'LE/XOR byte arithmetic conflict')
    value = struct.unpack('<f', float_bits.to_bytes(4, 'little'))[0]
    need(math.isfinite(value) and 0 <= value <= 2147483647 and value.is_integer(),
         'decoded float cannot be represented as exact nonnegative Java int')
    integer = int(value)
    need(struct.pack('<f', value) == float_bits.to_bytes(4, 'little') and
         (int.from_bytes(struct.pack('<f', value), 'little') ^ 0x8a4ce70f).to_bytes(4, 'little') == word_bytes,
         'float bit/inverse wire serialization conflict')
    STATE['stage'] = 'complete'
    candidate = spec['numericProofAndIndependentOracle']['fixedAstaCandidate']
    report = {
        'task': TASK, 'passed': True, 'inputPins': STATE['inputReads'],
        'fragment': {'table': 'AvatarSkill', 'key': 10019, 'source': source,
                     'javaFields': {'id': 10019, 'costElemVal': integer},
                     'logicalIDSource': 'accepted tableKey identity', 'idRawDecoded': False,
                     'fragmentOnly': True, 'fullSkill': False},
        'costEvidence': {'sourceField': selected['field'], 'rawMask': selected['rawMaskHex'],
                         'maskBits': bits, 'originalTokenPresent': True, 'ordinarySelectorLogicalValue': 0,
                         'wordOffset': offset, 'rawWordHex': word_bytes.hex(),
                         'float32Bits': f'{float_bits:08x}', 'float32Value': value,
                         'exactJavaInt': integer, 'literalByteAndUInt32Match': True,
                         'floatBitsAndInverseWireMatch': True, 'cursorAtEnd': offset + 4 + 4 * bits['11'],
                         'independentConsumer': 'same energySkill resource+98 decoded, compared with current elemental energy; conditional current>=cost branch'},
        'candidateCrosscheck': {'source': candidate, 'equalsCandidate': integer == candidate['costElemVal'],
                               'authority': 'candidate from pinned source spec only; not a target oracle or forced expectation'},
        'rawReadLedger': {'physicalDiskBytes': len(raw), 'wholeHashPasses': 1,
                          'sourceSpanHashBytesFromSameBuffer': source['end'] - source['begin'],
                          'knownMaskHashBytesFromSameBuffer': 8,
                          'newSemanticBytes': 4, 'semanticReads': STATE['semanticRawReads'],
                          'unmeasured': 'OS/filesystem buffering and interpreter/module-loader IO'},
        'resourceReady': False, 'actualAvailabilityOrMaxEnergyAssignmentProven': False,
        'costOrSpecialFull121Complete': False, 'nativeExecuted': False, 'javaExecuted': False,
    }
    result = encode(report)
    manifest = encode({'task': TASK, 'passed': True, 'resultBytes': len(result), 'resultSHA256': sha(result),
                       'toolSHA256': sha(Path(__file__).read_bytes()), 'specSHA256': SPEC['sha256'],
                       'newSemanticRawBytes': 4, 'resourceReady': False})
    need(len(result) + len(manifest) <= 65536, 'combined output budget exceeded')
    write('result.private.json', result)
    write('manifest.private.json', manifest)
    print(json.dumps({'task': TASK, 'passed': True, 'tableKey': 10019, 'costElemVal': integer,
                      'float32Bits': f'{float_bits:08x}', 'semanticRawBytes': 4,
                      'resultBytes': len(result), 'resultSHA256': sha(result), 'manifestSHA256': sha(manifest),
                      'combinedOutputBytes': len(result) + len(manifest), 'resourceReady': False}))


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
    need((actual['bytes'], actual['sha256']) == (pin['bytes'], pin['sha256']), f'input pin mismatch: {name}')
    return blob


def validate_sources(spec, sources):
    STATE['stage'] = 'source-contract'
    need(spec['proposedTask'] == TASK and len(sources) == 13, 'pilot task/source set differs')
    selected = spec['selected']
    need(selected['key'] == 10019 and selected['semanticRawBytes'] == 4, 'one-field scope differs')
    parser = json.loads(sources['parser'])
    lines = {line['address']: line for line in parser['lines']}
    need(all(lines[anchor['address']] == anchor for anchor in spec['parserContract']['anchors']), 'parser anchor conflict')
    need(lines['0x149b57100']['hex'] == '31f6' and lines['0x149b5711a']['hex'] == '0f85c6010000',
         'ordinary selector zero/branch source differs')
    consumer = {line['address']: line for line in json.loads(sources['ordinaryConsumer'])['lines']}
    need(all(consumer[anchor['address']] == anchor for anchor in spec['independentConsumerResponsibility']['anchors']),
         'independent configured-cost consumer source differs')
    mapping = json.loads(sources['mapping'])
    table, = [table for table in mapping['native']['parsedTables'] if table['name'] == 'AvatarSkill']
    matches = [row for row in table['rows'] if row['key'] == 10019]
    need(len(matches) == 1 and matches[0]['source'] == selected['source'] and
         table['payloadSHA256'] == spec['raw']['sha256'], 'same logical ID/source identity differs')
    flags = json.loads(sources['flags'])
    row, = [row for row in flags['rows'] if row['skillID'] == 10019]
    need(row['source'] == selected['source'] and row['rawMaskHex'] == selected['rawMaskHex'] and
         row['maskSpan'] == selected['maskSpan'], 'admitted original mask differs')
    raw_mask = int(selected['rawMaskHex'], 16)
    mask_bytes = bytes.fromhex(selected['maskSpan']['rawHex'])
    effective = raw_mask ^ int(flags['rawMaskXorHex'], 16)
    need(f'{effective:016x}' == row['effectiveMaskHex'], 'raw/effective mask relation differs')
    bits = {str(bit): (raw_mask >> bit) & 1 for bit in (53, 11, 48)}
    independent_bits = {str(bit): (mask_bytes[bit // 8] >> (bit % 8)) & 1 for bit in (53, 11, 48)}
    need(int.from_bytes(mask_bytes, 'little') == raw_mask and bits == independent_bits and
         bits['53'] == 1 and bits['11'] == 0 and bits['48'] == 0 and not effective & (1 << 4),
         'selected ordinary/present/tail bit contract differs')
    bits['effective4'] = 0
    return selected, bits


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
