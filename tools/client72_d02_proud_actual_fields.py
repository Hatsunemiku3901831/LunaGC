"""Audit frozen actual Depot leaves and original row-to-pair map production."""
import argparse
import hashlib
import json
import pickle
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-actual-fields'
ACTUAL = 'local/migration72/d02-resume-20261110b/c-depot-fields/main-third.private.json'
PRIOR = 'local/migration72/d02-resume-20261110/c-depot-proud-successor/main-third.private.json'
PHASE6 = 'local/migration72/d02-resume-20261110b/r3-proud-phase6/main-third.private.json'
METADATA = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
PAYLOAD = 'local/migration72/d02-active/resource-locator-astra/locator-final-1/AvatarSkillDepot.native-m_bytes.bin'
PINS = {
    ACTUAL: '8c06c130d7f2efa6a31a5848eda471810834e019c1a1153158576d2c15f3dfd5',
    PRIOR: 'a0a1f93cb980ddcd39fc46c70c46742f20c8ef154a8690816c2b068c7fdfbc9d',
    PHASE6: '7e6010fa975587364d5a89fe5bc02cf564b5ecb80fea3efda4dc49c6b95f04e4',
    'tools/client72_d02_old_depot_fields.py': 'ee165bd0bc624cb7616772a4dec5f1d8536a07a7104c50bb53b60dfc42cd8d3b',
}
PRIVATE_PINS = {
    'collect_map_source.private.py': '060eab311e211a8a5198dbba458eb625715b4025df2372f8687bfbe7f2f1a683',
    'collect_map_producer.private.py': '74f31156a5018c882418740b594fbd511dc8c74b3426ea378a4ec95b195a55fa',
    'map-source1/extraction.private.json': '5f3029f7a3a97e6a2e3f8717a6a6007880cf0f328b61961f133c5e9e21acb5cb',
    'map-source2/extraction.private.json': '5f3029f7a3a97e6a2e3f8717a6a6007880cf0f328b61961f133c5e9e21acb5cb',
    'map-producer1/extraction.private.json': '48d39aa574496538dd5acafafc4f0a437ab9530b78624519be142ecadee070d2',
    'map-producer2/extraction.private.json': '48d39aa574496538dd5acafafc4f0a437ab9530b78624519be142ecadee070d2',
    'collect_closed_aot.private.py': 'a43824fab5674d69d85a04bdb2a348cb0634ba8f32a78cf49a022f2e916144aa',
    'closed-aot-final1.private.json': '6318807d1e034ecd6bcb4c8df95d07eb53670a1de8df76362b7c6270d926bf9e',
    'closed-aot-final2.private.json': '6318807d1e034ecd6bcb4c8df95d07eb53670a1de8df76362b7c6270d926bf9e',
}


def read_pinned(path, digest):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != digest:
        raise ValueError('Fixed input SHA differs: ' + str(path.relative_to(ROOT)))
    return raw


def actual_leaves(actual, payload):
    """Validate four real queries and preserve typed leaves, including zero."""
    rows = {row['key']: row for row in actual['whole']['rows']}
    samples = actual['samples']
    if (len(rows) != 171 or len(actual['whole']['rows']) != 171 or actual['whole']['cursor'] != 26024
            or len(samples) != 4 or [s['avatarId'] for s in samples] != actual['activeRegularTeamOrder']):
        raise ValueError('Fixed whole or actual sample selection differs')
    selected = []
    for sample in samples:
        source = sample['source']
        row = rows[sample['key']]
        query = sample['query']
        if (source != row['source'] or sample['queryRAX'] != row['pointer']
                or sample['rowPointer'] != row['pointer'] or query['entry'] != '0x14d6d70f0'
                or query['stop'] != '0x620000100'
                or hashlib.sha256(payload[source['begin']:source['end']]).hexdigest() != source['sha256']):
            raise ValueError('Actual query pointer, RETURN or source span differs')
        fields = [f for f in sample['fields'] if f['fieldIndex'] == 105355]
        if len(fields) != 1:
            raise ValueError('Missing or duplicated typed inherent array')
        field = fields[0]
        if (field['owner'] != 22284 or field['offset'] != '0x18'
                or field['type']['elementType'].get('typeDefinition') != 81127
                or not field['inputPresence'].startswith('not observed')):
            raise ValueError('Typed array provenance or presence boundary differs')
        leaves, values = [], []
        for ordinal, pair in enumerate(field['value']):
            if [(f['owner'], f['fieldIndex'], f['offset'], f['type'].get('typeDefinition')) for f in pair] != [
                    (81127, 404853, '0x10', 58209), (81127, 404854, '0x14', 58209)]:
                raise ValueError('Wrong typed leaf or order')
            if any(type(f['value']) is not int or not 0 <= f['value'] <= 0xffffffff for f in pair):
                raise ValueError('Leaf is not a decoded U32')
            leaves.append(dict(ordinal=ordinal, field404853=pair[0]['value'], field404854=pair[1]['value']))
            values.extend(f['value'] for f in pair)
        getters = field['getters']
        if (len(getters) != len(values) or [g['value'] for g in getters] != values
                or any(g['trace']['entry'] != '0x14aef9db0' or g['trace']['stop'] != '0x620000100'
                       or g['trace']['models'] for g in getters)):
            raise ValueError('Original leaf getter values or RETURN differ')
        selected.append(dict(avatarId=sample['avatarId'], depotKey=sample['key'], source=source,
                             orderedLeaves=leaves, inputPresence=field['inputPresence']))
    return selected


def frozen_window(folder, report, entry, end, count):
    """Bind decoded instructions to original body and its exact pdata record."""
    code = read_pinned(folder / 'code.bin', report['codeSHA256'])
    asm = read_pinned(folder / 'code.asm', report['codeAsmSHA256'])
    pdata = read_pinned(folder / 'pdata.bin', report['pdataSHA256'])
    if (report['entry'] != hex(entry) or report['endExclusive'] != hex(end)
            or report['codeBytes'] != len(code) or len(code) != end-entry
            or report['instructions'] != count or report['nativeCPU'] or report['liveReads'] or report['xdataBytesRead']):
        raise ValueError('Window identity, extent or static boundary differs')
    begin_rva, end_rva, _ = struct.unpack('<III', pdata)
    if (begin_rva+0x140000000, end_rva+0x140000000) != (entry, end):
        raise ValueError('Original pdata does not bind exact complete body')
    cursor, decoded = entry, {}
    for line in asm.decode('utf8').splitlines():
        m = re.fullmatch(r'([0-9a-f]{16}) ([0-9a-f]+)\s+(.+?)\s*', line)
        if not m:
            raise ValueError('Malformed frozen instruction')
        address, raw, text = int(m[1], 16), bytes.fromhex(m[2]), m[3]
        if address != cursor or code[address-entry:address-entry+len(raw)] != raw:
            raise ValueError('Frozen decode no longer matches contiguous original bytes')
        decoded[address] = dict(hex=raw.hex(), instruction=text)
        cursor += len(raw)
    if cursor != end or len(decoded) != count:
        raise ValueError('Frozen decode incomplete')
    return decoded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='new JSON basename in the task directory')
    args = parser.parse_args()
    output = OUT / args.output
    if output.parent.resolve() != OUT.resolve() or output.suffix != '.json' or output.exists():
        parser.error('output must be a new task-local JSON basename')
    fixed = {p: read_pinned(ROOT / p, digest) for p, digest in PINS.items()}
    private = {p: read_pinned(OUT / p, digest) for p, digest in PRIVATE_PINS.items()}
    prior, phase6, actual = [json.loads(fixed[p]) for p in (PRIOR, PHASE6, ACTUAL)]
    aot = json.loads(private['closed-aot-final1.private.json'])
    if private['closed-aot-final1.private.json'] != private['closed-aot-final2.private.json']:
        raise ValueError('Fresh closed-AOT bytes differ')
    for p, digest in aot['pins'].items():
        read_pinned(ROOT / p, digest)
    if (aot['pointerQWORDCount'] != 56 or aot['pointerBytesRead'] != 448
            or aot['headerBytesRead'] != 28 or aot['nativeCPU'] or aot['newCodeWindows']):
        raise ValueError('Closed-AOT evidence budget or static boundary differs')
    for address, owner, method, instance in [('0x154d40010', 18192, 138526, 50463),
                                               ('0x1586e2450', 2147, 15620, 50464)]:
        matches = aot['targets'][address]
        if len(matches) != 1:
            raise ValueError('Closed-AOT target has no unique selected tuple')
        row = matches[0]
        if ((row['owner'], row['methodDefinition'], row['classInstance']) != (owner, method, instance)
                or struct.unpack('<iii', bytes.fromhex(row['specRecord12'])) != (method, instance, -1)
                or int.from_bytes(bytes.fromhex(row['pointerQWORD']), 'little') != int(address, 16)
                or struct.unpack('<ii', bytes.fromhex(row['tableRecord14'])[:8]) != (row['pointerIndex'], row['methodSpec'])):
            raise ValueError('Closed-AOT original record identity differs')
    inherited = {p: item['sha256'] for p, item in prior['inputs'].items()}
    for p, digest in inherited.items():
        read_pinned(ROOT / p, digest)
    for p, item in phase6['newPins'].items():
        read_pinned(ROOT / p, item['sha256'])
    records, cells = pickle.loads(read_pinned(ROOT / METADATA, inherited[METADATA]))
    payload = read_pinned(ROOT / PAYLOAD, inherited[PAYLOAD])
    selected = actual_leaves(actual, payload)
    windows = []
    for prefix, entry, end, count in [('map-source', 0x14d7ae340, 0x14d7ae3ee, 37),
                                      ('map-producer', 0x14d7acac0, 0x14d7ad72b, 742)]:
        first = json.loads(private[prefix+'1/extraction.private.json'])
        second = json.loads(private[prefix+'2/extraction.private.json'])
        for name in ('code.bin', 'code.asm', 'pdata.bin', 'pre-body.private.json', 'extraction.private.json'):
            if (OUT / (prefix+'1') / name).read_bytes() != (OUT / (prefix+'2') / name).read_bytes():
                raise ValueError('Fresh static extraction bytes differ')
        decoded = frozen_window(OUT / (prefix+'1'), first, entry, end, count)
        frozen_window(OUT / (prefix+'2'), second, entry, end, count)
        windows.append(dict(prefix=prefix, extraction=first, decoded=decoded))
    getter, producer = [w['decoded'] for w in windows]
    getter_anchors = {
        0x14d7ae354: 'mov rax, qword ptr [rip - 0x7f42d5b]',
        0x14d7ae35b: 'mov rax, qword ptr [rax + 0x21f70]',
        0x14d7ae367: 'cmp byte ptr [rax + 0x28], 0',
        0x14d7ae396: 'call 0x14d7acac0',
        0x14d7ae3a2: 'mov rax, qword ptr [rax + 0x21f70]',
    }
    producer_anchors = {
        0x14d7acb3d: 'mov rcx, qword ptr [rax + 0x21f70]',
        0x14d7acb4f: 'call 0x154d3f9f0',
        0x14d7ad378: 'call rbx',
        0x14d7ad37a: 'mov ebx, eax',
        0x14d7ad390: 'mov ecx, ebx',
        0x14d7ad394: 'call 0x14d7adf70',
        0x14d7ad399: 'mov rsi, rax',
        0x14d7ad3b2: 'mov rax, qword ptr [rsi + 0x40]',
        0x14d7ad3bb: 'mov eax, dword ptr [rax + 0x90]',
        0x14d7ad3eb: 'call 0x14aef9db0',
        0x14d7ad3f0: 'mov edi, eax',
        0x14d7ad3f2: 'mov rax, qword ptr [rsi + 0x40]',
        0x14d7ad3fb: 'mov eax, dword ptr [rax + 0x84]',
        0x14d7ad42b: 'call 0x14aef9db0',
        0x14d7ad430: 'shl rax, 0x20',
        0x14d7ad434: 'mov esi, edi',
        0x14d7ad436: 'or rsi, rax',
        0x14d7ad448: 'call 0x1586e2450',
        0x14d7ad44f: 'je 0x14d7ad4de',
        0x14d7ad46c: 'mov rcx, qword ptr [rax + 0x21f70]',
        0x14d7ad47c: 'mov rdx, rsi',
        0x14d7ad47f: 'mov r8d, ebx',
        0x14d7ad485: 'call 0x154d40010',
        0x14d7ad5d3: 'mov byte ptr [rax + 0x28], 1',
    }
    for decoded, anchors in ((getter, getter_anchors), (producer, producer_anchors)):
        if any(decoded[a]['instruction'] != text for a, text in anchors.items()):
            raise ValueError('Original producer opcode anchor differs')
    bindings = [(80063, 663543, '0x14d7ae340'), (80063, 663554, '0x14d7acac0')]
    for owner, index, address in bindings:
        unique = [(r['index'], m['index']) for r in records for m in r['methods'] if m['registeredAddress'] == address]
        if unique != [(owner, index)]:
            raise ValueError('Unique entry provenance differs')
    if (cells[0x14589a0a0]['type']['typeDefinition'] != 80063
            or cells[0x1458e4618]['type']['typeDefinition'] != 80066
            or cells[0x1458ea828]['type']['typeDefinition'] != 2147
            or cells[0x1458ea828]['type']['arguments'][0]['typeDefinition'] != 80067
            or cells[0x1458972e0]['type']['typeDefinition'] != 1248
            or cells[0x1458972e0]['type']['arguments'][0]['kind'] != 'uint32'):
        raise ValueError('Original usage type provenance differs')
    config_fields = [f for f in records[41105]['fields'] if f['index'] in (203373, 203376)]
    if [(f['index'], f['offset'], f['type'].get('typeDefinition')) for f in config_fields] != [
            (203373, 0x84, 58209), (203376, 0x90, 58209)]:
        raise ValueError('NativeConfig typed key leaf declarations differ')
    flat = [leaf for sample in selected for leaf in sample['orderedLeaves']]
    positive = [leaf['field404854'] for leaf in flat if leaf['field404854'] > 0]
    result = dict(task='E-01/R3-PROUD-ACTUAL-FIELDS', sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        fixedPins=PINS, privatePins=PRIVATE_PINS, inherited20Pins=inherited,
        phase6Pins={p: v['sha256'] for p, v in phase6['newPins'].items()},
        actualSamples=selected, actualReferenceCounts=dict(slots=len(flat), positive=len(positive), zero=len(flat)-len(positive),
            uniquePositive=len(set(positive)), observedPlus10Values=sorted({leaf['field404853'] for leaf in flat})),
        originalWindows=[dict(prefix=w['prefix'], extraction=w['extraction']) for w in windows],
        getterOpcodes={hex(a): getter[a] for a in getter_anchors}, producerOpcodes={hex(a): producer[a] for a in producer_anchors},
        typedUsageCells={hex(a): cells[a] for a in (0x14589a0a0, 0x1458e4618, 0x1458ea828, 0x1458972e0)},
        nativeConfigKeyFields=config_fields,
        provedStaticRelation=dict(lowU32='decoded +90 under the caller-constrained NativeConfig41105 shape (field203376)',
            highU32='decoded +84 under the same caller-constrained shape (field203373)',
            mapObject='same global object from RIP14586b600, offset21f70',
            value='same enumerated U32 passed to14d7adf70 and retained inEBX',
            packing='(uint64(decoded+84)<<32) | decoded+90',
            scope='normal non-hotfix NativeConfig-present branch; static argument production, not an executed map write'),
        rowToPairProducerIdentified=True, mapStoreCallSemanticsApproved=False,
        closedAOTTargets=aot['targets'], closedAOTTables=aot['tables'], allSharedPointerRows=aot['allSharedPointerRows'],
        hashSetCalleeIdentityApproved=True, hashSetCallSemanticsApproved=False,
        actualMapExecuted=False, actualProudTableIdentityApproved=False,
        uninspectedGenericCalls=dict(map='154d40010 receives same map object, pair key and original enumerated U32; body not audited; closed AOT owner18192/method138526/classInstance50463 uniquely bound',
            hashSet='1586e2450 is closed HashSet80067.method15620 AddIfNotPresent; false branches to early failure; helper body and duplicate behavior not executed'),
        wrapper63831Status='caller-consumption shape constraint; declared return type of14d7adf70 remains unrecovered',
        nextInput=dict(entry='0x14d7adf70', owner=80063, method=663540,
            directSource='original14d7ad394 E8d70b0000; same enumerated U32 inECX, EDXfalse',
            required='Bind this queried row key and wrapper/native row to original resource table path, parser and actual payload; then sample actual rows for the15 positive Depot references without Java arithmetic.'),
        thresholdMissing='typed81127.field404853/+10 original decoded comparison against sourced Avatar PROP_BREAK_LEVEL1002; this producer does not read Depot81127',
        savedProudValuesUsedAsAliasProof=False, zeroUsedAsOmissionProof=False,
        inherentProjectionApproved=False, promoteThresholdApproved=False, gTimes100PlusLevelProven=False,
        proudSkillGroupIdAliasApproved=False, secondU32LevelIdentityApproved=False,
        specialQuestProjectionApproved=False, resourceReady=False, completeAvatar=False, d02Complete=False,
        auditCurrentPEReads=0, auditNativeCPU=0, auditLiveReads=0, jvm=0, database=0, network=0)
    raw = (json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2)+'\n').encode('utf8')
    with output.open('xb') as f:
        f.write(raw)
    print(json.dumps(dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), counts=result['actualReferenceCounts'],
                          staticWindows=2, staticBytes=174+3179, rowToPairProducerIdentified=True)))


if __name__ == '__main__':
    main()
