"""Fixed-source birth Avatar base/growth consumers; offline, selected rows only."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import pickle
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import client72_d02_promote_resource as prior
from tools.client72_emulator import GLOBAL
from tools.client72_resource_deep import RETURN
from tools.extract_client72 import HEADER_VA, BODY, TypeResolver, number
from tools.extract_client72_signatures import parameter_start, parameter_words

RAW = ROOT / 'local/migration72/d02-resume-20261109/r3-avatar'
PROMOTE_REPORT = prior.RAW / 'main-third.json'
PINS = {
    'tools/client72_d02_promote_resource.py': '8d60647478e24defe3b550c74d0f17474517450b29cfb9e8630cc6f82d88cbde',
    'tools/extract_client72_signatures.py': 'ee92527f3607be774c28be694d6f57e9eed3aa40068b7b584979e27b6470c349',
    'local/migration72/d02-resume-20261108/r3-promote/main-third.json': '546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1',
    'local/migration72/d02-resume-20261109/r3-avatar/avatar-stat-helper.private.json': 'b930bab1409672aee3202f2b6ba2df54515fcee33ae636a2cd17f493f299a1c6',
    'local/migration72/d02-resume-20261109/r3-avatar/avatar-curve-consumers.private.json': '766ee27b60b7576971968685d586268b94c02aadc2717039b2773d82faa07fb6',
    'local/migration72/d02-resume-20261109/r3-avatar/exact-curve-helper-callers.private.json': 'abd0e48ad39f0f40e9de8d60cb68f994811aad54511fad11bcf527152e3d28ee',
    'local/migration72/d02-resume-20261109/r3-avatar/critical-avatar-consumer.private.json': 'e6acd3fe80d70db6bd68c846ef8d5ef13bbed6e1c69baf3af1c3ab4d35fa1d93',
    'local/migration72/d02-resume-20261109/r3-avatar/critical-exact-load-sites.private.json': '0e13aedff0a79eed6b5a7d122ab7d87b63f7698b2060513498d3a8ec81e4d120',
    'local/migration72/d02-resume-20261109/r3-avatar/critical-exact-tail-sites.private.json': 'a5338d955b09046ec0b16f3a9625b7955c763aae07dc722e474e6078745f932f',
}
AVATARS = (10000005, 10000007, 10000136)
LEVELS = (1, 20, 100)
PROPERTIES = {1: ('hpBase', '0x64'), 4: ('attackBase', '0x9c'), 7: ('defenseBase', '0x5c')}
HELPER, CALLER, CALLER_START, CALLER_STOP = 0x153a7f940, 0x153a7f560, 0x153a7f621, 0x153a7f6f6


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('A fresh report in this card private directory is required')
    pins, sources, _ = prior.verify_inputs(args.client)
    for name, digest in PINS.items():
        prior.card.base.read_verified(ROOT / name, digest)
    pins.update(PINS)
    binary, metadata, startup = [(args.client / name).read_bytes() for name in list(prior.card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((prior.equip.RAW / 'metadata.private.pkl').read_bytes())
    old = json.loads(prior.EQUIPMENT_REPORT.read_bytes())
    promote = json.loads(PROMOTE_REPORT.read_bytes())
    avatar_table = next(t for t in old['tables'] if t['name'] == 'Avatar')
    curve_table = next(t for t in promote['tables'] if t['name'] == 'AvatarCurve')
    codec = prior.card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = prior.PromoteRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    result = dict(task='E-01/R3-AVATAR-ENTRY', schemaVersion=1,
        sourceSHA256=sha(Path(__file__).read_bytes()), sourcePins=pins,
        testSourceSHA256=sha((ROOT / 'tools/test_probe72_avatar_entry_resource_fields.py').read_bytes()),
        selectedRows=[], nativeIndexes=[])
    try:
        result['staticSources'] = static_sources(rt)
        prior.equip.install_table(rt, 'Avatar')
        avatars = parse_selected(rt, avatar_table, AVATARS, 'Avatar', 58272, 0x14f9935d0,
            (prior.equip.SOURCE / 'Avatar.native-m_bytes.bin').read_bytes())
        result['selectedRows'].extend(avatars)
        result['nativeIndexes'].append(selected_index(rt, avatars, 58272, 0x30870))
        prior.bind_generics(rt, prior.SPECS['AvatarCurve']['whole'])
        curves = parse_selected(rt, curve_table, LEVELS, 'AvatarCurve', 68066, 0x14fd05cc0,
            (prior.RAW / 'AvatarCurve.cached-m_bytes.bin').read_bytes())
        result['selectedRows'].extend(curves)
        result['nativeIndexes'].append(selected_index(rt, curves, 68066, 0x34a80))
        result['consumers'] = execute_consumers(rt, avatars, curves)
        result['negativeConsumers'] = execute_negatives(rt, avatars, curves)
        java_fight = {name: int(value) for name, value in re.findall(r'^    (FIGHT_PROP_\w+)\((\d+)\)', sources['game/props/FightProperty.java'], re.M)}
        result['javaProjectionFragments'] = [project_avatar(row, rt.enum_symbols, java_fight, curve_table) for row in avatars if row['key'] in AVATARS[:2]]
        result.update(enumSymbols=list(rt.enum_symbols.values()), nativeSources=rt.functions,
            nativeCallTotals=rt.call_totals, nativeCallInstructionHookVisits=rt.call_instruction_total,
            allowedRangeCompaction=rt.allowed_range_compaction, nativeConfigTypedFixture=rt.config_proof,
            scalarStorageCodec=codec['proof'], storageModels=rt.storage_events,
            completeAvatarHandler=False, completeActorIdCodec=False, completeCaller=False,
            fullTableParsingNewlyExecuted=False, completeAvatarInputRow=False, completeItemInputRow=False,
            javaResourcesLoaded=False, resourceReady=False, activeSelectedRevision=None,
            activeRevisionVerified=False, d02Complete=False,
            missingJavaInputFields=['Avatar.critical', 'Avatar.criticalHurt',
                'same-source Avatar icon/ability references and complete Depot/Skill necessities',
                'Item gadgetId/skillAffix and their necessary positive references'],
            criticalInvestigation=dict(aliasApproved=False,
                directFieldA4ThenFloatCallCandidates=len(json.loads((RAW / 'critical-exact-load-sites.private.json').read_bytes())),
                directFieldA4ThenFloatTailCandidates=len(json.loads((RAW / 'critical-exact-tail-sites.private.json').read_bytes())),
                candidateFilterIsNotExhaustiveProof=True,
                rejectedCacheCandidate='148bde5a0 contains Avatar cache usage but no confirmed Avatar+a4 or +98 consumer',
                nativeCriticalConsumerExecuted=False),
            modelScope=[
                'All prior fixed inputs and stock files are guarded. Only six actual rows are re-parsed at already reviewed payload spans; whole-table parsing, resource unpacking/indexing and old bridge/weapon/Promote consumers are not repeated.',
                'Original row constructors/parsers, scalar getters, UInt32 Dictionary TryInsert/FindEntry and full helper153a7f940 execute. Only managed typed storage, metadata/RGCTX, memory libraries, UTF8 conversion and the reviewed scalar-codec initialization are modeled.',
                'Two selected-row Dictionary stores are explicit fixtures with exact declared key/value classes and capacity3; insertion/query remain native. They do not prove whole cache publication, cold initialization or natural runtime mode.',
                'Caller slice153a7f621..153a7f6f6 starts after actor-ID decoding: RAX is the explicit Avatar ID, EBP is requested level, ESI is FightProperty, and RBX is typed18163 storage. It selects actual same-pointer Avatar/Curve rows and calls the full helper. The preceding actor-ID codec and later comparison/UI/lifecycle are not executed.',
                'Native counts include all selected constructors/parsers, original field readout, index insertion/query, helper, caller and negative calls. Counts are instruction-hook visits, including the hook at each unexecuted exact stop and modeled library entries; storage writes are separately declared fixtures.',
                'Resources use the reviewed actual inverse-permutation snapshot/seed; the forward permutation is its declared mathematical inverse, not a new live observation. NativeConfig exact HashSet<Int32> null-buckets remains a fixture.',
                'Controlled negatives mutate only offline typed storage and restore it. Null/error helper arrival is not execution of the full exception or live-client behavior. Missing actual bindings/curves are not converted into Java zero/default rows.',
                'Logical zero/float bits/list order come from original parsers. Optional source token presence for every field is not separately recovered. HP and growth are approved fragments; critical/criticalHurt and complete Java load/manager/client acceptance remain unknown.',
            ])
    except Exception as error:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(error=str(error), partial=result,
            nativeFailure=rt.e.failure, nativeCallTotals=rt.call_totals,
            nativeCallInstructionHookVisits=rt.call_instruction_total,
            registers={k: hex(rt.e.reg(k)) for k in ('rip', 'rcx', 'rdx', 'r8', 'r9', 'rax')},
            nativeSources=rt.functions), indent=2, sort_keys=True) + '\n', 'utf8')
        raise
    finally:
        rt.e.close()
    prior.equip.verify_client(args.client)
    result['fiveInputsRecheckedAfterNative'] = True
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', 'utf8')
    print(json.dumps(dict(selectedRows=len(result['selectedRows']), helperCases=len(result['consumers']['completeHelper']),
        callerCases=len(result['consumers']['callerSlices']), negativeCases=len(result['negativeConsumers']),
        instructionHookVisits=result['nativeCallInstructionHookVisits'], resourceReady=False)))


def static_sources(rt):
    helper = json.loads((RAW / 'avatar-stat-helper.private.json').read_bytes())
    caller = next(x for x in json.loads((RAW / 'avatar-curve-consumers.private.json').read_bytes()) if int(x['start'], 16) == CALLER)
    result = []
    h = rt.e.pe.offset(HEADER_VA)
    resolver = TypeResolver(rt.e.pe, rt.startup, rt.records, h)
    mb = BODY + (number(rt.binary, h + 0xb0) ^ 0x51324a1f)
    pb = BODY + ((number(rt.binary, h + 0x174) + 0xae6bcd68) & 0xffffffff)
    for window in (helper, caller):
        start, end = int(window['start'], 16), int(window['end'], 16)
        rt.bind_function(start)
        raw = rt.binary[rt.e.pe.offset(start):rt.e.pe.offset(start) + end - start]
        if rt.functions[hex(start)]['end'] != hex(end) or sha(raw) != window['sha256']:
            raise ValueError('Selected consumer PE window differs from frozen evidence')
        actual = [dict(address=hex(i.address), instruction=i.mnemonic + ' ' + i.op_str, size=i.size) for i in rt.decoder.disasm(raw, start)]
        if actual != window['instructions']:
            raise ValueError('Selected consumer instruction stream differs from the fixed window')
        method = next(q for q in rt.records[66972]['methods'] if int(q['registeredAddress'], 16) == start)
        first = parameter_start(method['index'], number(rt.metadata, mb + method['index'] * 26 + 4))
        parameters = [resolver.resolve_index(parameter_words(i, number(rt.metadata, pb + i * 8), number(rt.metadata, pb + i * 8 + 4))[1])
            for i in range(first, first + method['parameterCount'])]
        result.append(dict(window, parameters=parameters))
    if [x.get('typeDefinition') for x in result[0]['parameters']] != [58272, 68066, 32419]:
        raise ValueError('Actual Avatar/Curve/FightProperty helper declaration changed')
    return result


def parse_selected(rt, table, keys, name, typ, parser, blob):
    e = rt.e
    if sha(blob) != table['payloadSHA256']:
        raise ValueError('Selected-row source differs from the reviewed whole table')
    rt.reader, rt.row_entry = e.alloc(64), parser
    data = rt.array(len(blob), 1)
    e.uc.mem_write(data + 0x20, blob)
    e.write(rt.reader + 0x10, data)
    ctor = next(int(q['registeredAddress'], 16) for q in rt.records[typ]['methods'] if q['name'] == '.ctor')
    actual = []
    for key in keys:
        source = next(q for q in table['rows'] if q['key'] == key)
        span = source['source']
        if not 4 <= span['begin'] < span['end'] <= len(blob) or sha(blob[span['begin']:span['end']]) != span['sha256']:
            raise ValueError('Selected row source span/hash mismatch')
        pointer = prior.equip.object_storage(rt, typ)
        constructor = rt.call(ctor, rcx=pointer)
        e.write(rt.reader + 0x1c, span['begin'], 'I')
        trace = rt.call(parser, rcx=pointer, rdx=rt.reader)
        if constructor['stop'] != hex(RETURN) or trace['stop'] != hex(RETURN) or e.read(rt.reader + 0x1c, 'I') != span['end']:
            raise ValueError('Original selected-row parser did not consume its exact reviewed span')
        readout_begin = rt.call_instruction_total
        fields = rt.snapshot_object(pointer, typ, 0)
        old = next(q for q in table['selected' if name == 'Avatar' else 'rows'] if q['key'] == key)
        if fields != old['fields'] or span != old['source']:
            raise ValueError('Independent selected-row reparse differs from the reviewed original logical fields')
        actual.append(dict(table=name, key=key, pointer=hex(pointer), fields=fields, source=span,
            payloadSHA256=sha(blob), constructor=constructor, parser=trace,
            nativeReadoutInstructionHookVisits=rt.call_instruction_total - readout_begin,
            originalWholeEvidenceReused=True, wholeTableNewlyExecuted=False))
    print(json.dumps(dict(stage='selected-original-rows', table=name, keys=list(keys))), flush=True)
    return actual


def selected_index(rt, rows, typ, cache_offset):
    e = rt.e
    klass = next(p for p, t in rt.type_records.items() if t.get('typeDefinition') == 1222 and t['arguments'][1].get('typeDefinition') == typ)
    pointer = e.alloc(0x48)
    e.write(pointer, klass)
    e.write(pointer + 0x18, rt.array(len(rows), 24))
    e.write(pointer + 0x20, rt.array(len(rows), 4))
    e.write(pointer + 0x3c, 0xffffffff, 'I')
    inserts, queries = [], []
    for row in rows:
        trace = rt.call(0x1578b6a20, rcx=pointer, rdx=row['key'], r8=int(row['pointer'], 16), r9=2)
        if trace['stop'] != hex(RETURN):
            raise ValueError('Original selected-row Dictionary insertion did not return')
        inserts.append(dict(key=row['key'], trace=trace))
    e.write(GLOBAL + cache_offset, pointer)
    for row in rows:
        trace = rt.call(0x1578b66f0, rcx=pointer, rdx=row['key'])
        position = e.reg('eax')
        array = e.read(pointer + 0x18)
        if trace['stop'] != hex(RETURN) or position >= len(rows) or e.read(array + 0x30 + position * 24) != int(row['pointer'], 16):
            raise ValueError('Original selected-row Dictionary query lost the parsed row pointer')
        queries.append(dict(key=row['key'], samePointer=True, trace=trace))
    return dict(valueType=typ, cacheOffset=hex(cache_offset), pointer=hex(pointer), selectedStorageFixture=True,
        declaredCapacity=len(rows), type=rt.type_records[klass], inserts=inserts, queries=queries)


def execute_consumers(rt, avatars, curves):
    e = rt.e
    complete, slices = [], []
    for avatar in avatars:
        for curve in curves:
            for prop in PROPERTIES:
                expected, base, binding, factor = oracle(avatar, curve, prop)
                trace = rt.call(HELPER, rcx=int(avatar['pointer'], 16), rdx=int(curve['pointer'], 16), r8=prop)
                bits = e.reg('xmm0') & 0xffffffff
                if trace['stop'] != hex(RETURN) or bits != expected:
                    raise ValueError('Complete original Avatar stat helper differs from source float32 oracle')
                complete.append(dict(avatarId=avatar['key'], level=curve['key'], property=prop, source=avatar['source'],
                    curveSource=curve['source'], baseBits=base['bits'], growthType=binding,
                    factorBits=factor['bits'], resultBits=f'{bits:08x}', independentOracleBits=f'{expected:08x}',
                    completeNativeFunction=True, trace=trace))
        model = prior.equip.object_storage(rt, 18163)
        for curve in curves:
            for prop in PROPERTIES:
                trace = rt.call(CALLER_START, stops=(CALLER_STOP,), rax=avatar['key'], rbx=model, rbp=curve['key'], rsi=prop)
                expected, _, _, _ = oracle(avatar, curve, prop)
                if trace['stop'] != hex(CALLER_STOP) or e.reg('xmm0') & 0xffffffff != expected or e.reg('r15') != int(avatar['pointer'], 16):
                    raise ValueError('Actual Avatar cache/Curve cache caller slice differs from its selected source rows')
                if hex(HELPER) not in trace['executedAddresses'] or hex(0x1578b66f0) not in trace['executedAddresses']:
                    raise ValueError('Caller slice replaced native resource lookup/helper execution')
                slices.append(dict(avatarId=avatar['key'], level=curve['key'], property=prop,
                    selectedAvatarSamePointer=True, resultBits=f'{expected:08x}',
                    liveIn=dict(raxAvatarId=avatar['key'], ebpLevel=curve['key'], esiFightProperty=prop,
                        rbxTypedActorFixture=hex(model), actorIdCodecExecuted=False),
                    completeCaller=False, trace=trace))
    return dict(completeHelper=complete, callerSlices=slices)


def execute_negatives(rt, avatars, curves):
    e = rt.e
    avatar, curve = avatars[0], curves[1]
    ap, cp = int(avatar['pointer'], 16), int(curve['pointer'], 16)
    cases = []
    for name, aid, cid, prop in [('null-avatar', 0, cp, 1), ('null-curve', ap, 0, 1),
            ('none-property', ap, cp, 0), ('critical-is-not-base-grow-property', ap, cp, 20),
            ('unknown-property', ap, cp, 0xffffffff)]:
        trace = rt.call(HELPER, rcx=aid, rdx=cid, r8=prop)
        if trace['stop'] != hex(RETURN) or e.reg('xmm0') & 0xffffffff:
            raise ValueError('Original missing/unsupported helper input did not return its observed zero')
        cases.append(dict(name=name, returnedBits='00000000', trace=trace, callerInputFixture=True))
    grow, infos = e.read(ap + 0x48), e.read(cp + 0x10)
    mutations = [('null-growth-array', ap + 0x48, 'Q', 0, '0x1404c2be0'),
        ('empty-growth-array', grow + 0x18, 'I', 0, hex(RETURN)),
        ('missing-hp-growth-binding', grow + 0x20, 'I', 0xfffffffe, hex(RETURN)),
        ('missing-hp-curve-type', infos + 0x20, 'I', 0xfffffffe, hex(RETURN)),
        ('unknown-hp-curve-arithmetic', infos + 0x24, 'I', 0x7fffffff, hex(RETURN))]
    for name, address, fmt, value, stop in mutations:
        old = e.read(address, fmt)
        e.write(address, value, fmt)
        try:
            trace = rt.call(HELPER, rcx=ap, rdx=cp, r8=1)
            bits = e.reg('xmm0') & 0xffffffff
            if trace['stop'] != stop or stop == hex(RETURN) and bits:
                raise ValueError('Original controlled missing binding/curve did not reach its exact expected boundary')
            cases.append(dict(name=name, trace=trace, expectedStop=stop,
                returnedBits=f'{bits:08x}' if stop == hex(RETURN) else None,
                mutation=dict(address=hex(address), format=fmt, original=hex(old), controlled=hex(value)),
                originalActualRowRestored=True, fixtureMutationOnly=True, fullExceptionExecuted=False))
        finally:
            e.write(address, old, fmt)
    # Actual cache misses pass null to the original helper. They do not create
    # source/default rows; production conversion rejects missing references.
    model = prior.equip.object_storage(rt, 18163)
    for name, key, level in [('caller-missing-level', avatar['key'], 0xffffffff),
            ('caller-missing-avatar', 0xffffffff, curve['key'])]:
        trace = rt.call(CALLER_START, stops=(CALLER_STOP,), rax=key, rbx=model, rbp=level, rsi=1)
        if trace['stop'] != hex(CALLER_STOP) or e.reg('xmm0') & 0xffffffff:
            raise ValueError('Caller cache-miss boundary differs from the actual null-input helper path')
        cases.append(dict(name=name, trace=trace, returnedBits='00000000', convertedToJavaDefault=False))
    return cases


def oracle(avatar, curve, prop):
    fields = {f['offset']: f['value'] for f in avatar['fields']}
    base = fields[PROPERTIES[prop][1]]
    binding = next({f['offset']: f['value'] for f in item}['0x4']['value'] for item in fields['0x48']
        if {f['offset']: f['value'] for f in item}['0x0']['value'] == prop)
    infos = next(f['value'] for f in curve['fields'] if f['offset'] == '0x10')
    values = next({f['offset']: f['value'] for f in item} for item in infos
        if {f['offset']: f['value'] for f in item}['0x0']['value'] == binding)
    if values['0x4']['value'] != 2:
        raise ValueError('Birth Avatar growth contract requires the actual MULTI arithmetic')
    factor = values['0x8']
    bits = struct.unpack('<I', struct.pack('<f', base['float32'] * factor['float32']))[0]
    return bits, base, binding, factor


def checked_float(value):
    bits = int(value['bits'], 16)
    if type(value['float32']) not in (float, int) or struct.unpack('<I', struct.pack('<f', value['float32']))[0] != bits or bits & 0x7f800000 == 0x7f800000:
        raise ValueError('Resource float32 must be finite and preserve the original bits')
    return value['float32']


def project_avatar(row, enums, java_fight, curve_table):
    key = prior.equip.java_id(row['key'])
    fields = {f['offset']: f['value'] for f in row['fields']}
    if type(fields['0x48']) is not list:
        raise ValueError('Java Avatar onLoad needs the actual nonnull growth bindings')
    values = {name: checked_float(fields[offset]) for name, offset in PROPERTIES.values()}
    bindings, used = [], set()
    for item in fields['0x48']:
        pair = {f['offset']: f['value'] for f in item}
        prop, grow = pair['0x0']['value'], pair['0x4']['value']
        if prop not in PROPERTIES or prop in used:
            raise ValueError('Birth growth input requires one actual binding for each approved base property')
        used.add(prop)
        name = prior.equip.exact_enum(prop, 32419, enums, java_fight)
        curve = prior.equip.exact_enum(grow, 54063, enums)
        bindings.append(dict(type=name, growCurve=curve))
    if used != set(PROPERTIES):
        raise ValueError('Missing actual base property growth binding')
    # Reuses the reviewed complete 100-level table conversion gate. No parser or
    # CPU consumer is invoked here and no absent/default curve is accepted.
    prior.project_curves(curve_table, enums, {b['growCurve'] for b in bindings})
    return dict(table='Avatar', key=key, source=row['source'],
        javaFields=dict(id=key, **values, propGrowCurves=bindings),
        floatBits={name: fields[offset]['bits'] for name, offset in PROPERTIES.values()},
        fragmentOnly=True, actualJavaLoaded=False, criticalAliasesApproved=False,
        omittedUnknownFields=['critical', 'criticalHurt'], fullDeclaredJavaInputRow=False)


if __name__ == '__main__':
    main()
