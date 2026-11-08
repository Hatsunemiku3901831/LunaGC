"""Fixed-CN72 Weapon backing field -> segmented native named Gadget query."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import pickle
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import probe72_item_entry_resource_fields as item
from tools.client72_emulator import GLOBAL, STACK, END
from tools.client72_resource_deep import RETURN
from tools.extract_client72_literals import Literals72

prior = item.prior
RAW = ROOT / 'local/migration72/d02-resume-20261109/r3-gadget-bridge'
SCENE = ROOT / 'local/migration72/d02-active/main/scene-runtime-context-third.json'
FIELD_START, FIELD_STOP = 0x14c424b6f, 0x14c424bb1
CALL_START, ARG_STOP = 0x14c424d7c, 0x15153dde5
QUERY_START, QUERY_STOP = 0x15153dfd0, 0x15153e008
FACTORY, NAMED, GETTER = 0x15153dd80, 0x152e10fc0, 0x152e110c0
HOOK_LIMIT = 500_000
PINS = {
    'tools/probe72_item_entry_resource_fields.py': '6581362650dbbbc3ddb85b059ad22b1e659c3b372add636bab7a72d0788d016d',
    'tools/client72_scene_runtime_context.py': 'b5af21df00710e711b551f88507445e987eae998ba12e049e67a54204e19802e',
    'local/migration72/d02-resume-20261109/r3-item/main-third.json': '9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2',
    'local/migration72/evidence/client72-signatures.json': '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'local/migration72/d02-resume-20261109/r3-gadget-bridge/codex-windows.private.json': 'bdff2b0fa682033d359767fd2c47a4f614ef1e0bdc9ab1e081a247317a65ec58',
    'local/migration72/d02-resume-20261109/r3-gadget-bridge/factory-wrapper.private.json': 'a09a247236f57f0d5d1f8b79ae47df7fea52fbeac5ff13117b34ee83860667c5',
    'local/migration72/d02-resume-20261109/r3-gadget-bridge/typed-candidates.private.json': 'efff4d2dfcdaf1fd409bb42b3ade8c65dddf798fce4e0787a0a7f2c175c47b43',
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


class GadgetBridgeRuntime(prior.PromoteRuntime):
    def __init__(self, *args, **kwargs):
        self.native_calls = []
        super().__init__(*args, **kwargs)

    def call(self, address, stops=(), *, stack_return=True, **registers):
        e = self.e
        remaining = HOOK_LIMIT - self.call_instruction_total
        if remaining <= 0:
            raise ValueError('Weapon Gadget cumulative native budget exhausted')
        self.compact_allowed_ranges()
        e.calls, e.failure, e.steps = [], None, 0
        live = dict(rsp=STACK + 0x100008)
        live.update(registers)
        if stack_return:
            e.write(live['rsp'], RETURN)
        for name, value in live.items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + name.upper()), value)
        stop_set = {RETURN, 0x1404c2be0, 0x1404c2650, 0x1404c2c70, *stops}
        e.allowed.extend((x, x + 1) for x in stop_set)
        counts, reached, observed = Counter(), [], []

        def observe(ip):
            if ip == 0x148ba9780:
                raise ValueError('Unreviewed NativeClass Item backing branch reached')
            if ip in self.extents or ip in e.ends:
                self.bind_function(ip)
                observed.append(dict(entry=hex(ip), rcx=hex(e.reg('rcx')), rdx=hex(e.reg('rdx'))))
            counts[ip] += 1
            if ip in stop_set:
                reached.append(ip)
                e.uc.emu_stop()

        e.before_instruction = observe
        limit = min(2_000_000, remaining)
        try:
            e.uc.emu_start(address, END, count=limit)
        finally:
            e.before_instruction = None
            total = self.call_totals.setdefault(hex(address), dict(calls=0, instructionHookVisits=0))
            total['calls'] += 1
            total['instructionHookVisits'] += e.steps
            self.call_instruction_total += e.steps
            trace = dict(entry=hex(address), stop=hex(reached[0]) if len(reached) == 1 else None,
                instructions=e.steps, countLimit=limit, liveIn={k: hex(v) for k, v in live.items()},
                executedAddresses={hex(k): v for k, v in sorted(counts.items())}, observedEntries=observed,
                models=[row['address'] for row in e.calls], failure=e.failure, stackReturnWritten=stack_return)
            self.native_calls.append(trace)
        if e.failure or len(reached) != 1:
            raise ValueError(e.failure or 'Exact Weapon Gadget stop not reached within native budget')
        return trace


def static_contract(rt, scene):
    import capstone
    codex = json.loads((RAW / 'codex-windows.private.json').read_bytes())['windows']
    factory = json.loads((RAW / 'factory-wrapper.private.json').read_bytes())
    signature = json.loads((ROOT / 'local/migration72/evidence/client72-signatures.json').read_bytes())
    refs = signature['typeReferences']
    windows = []
    for win in [*codex, factory]:
        start, end = int(win['start'], 16), int(win['end'], 16)
        raw = rt.binary[rt.e.pe.offset(start):rt.e.pe.offset(start) + end - start]
        if rt.extents[start] != end or sha(raw) != win['sha256']:
            raise ValueError('Frozen codex/factory extent or bytes differ')
        decoded = [dict(address=hex(i.address), size=i.size, bytes=i.bytes.hex(), instruction=i.mnemonic + ' ' + i.op_str)
                   for i in rt.decoder.disasm(raw, start)]
        if decoded != win['instructions']:
            raise ValueError('Frozen complete codex/factory instruction stream differs')
        declared = [dict(m, parameters=[dict(p, type=refs[str(p['typeIndex'])]) for p in m['parameters']])
                    for m in signature['methods'] if int(m['registeredAddress'], 16) == start]
        if declared != win['typedMethods']:
            raise ValueError('Actual cached parameter references differ from frozen typed methods')
        rt.bind_function(start)
        windows.append({k: win[k] for k in ('start', 'end', 'bytes', 'sha256', 'typedMethods')})
    code = rt.binary[rt.e.pe.offset(FACTORY):rt.e.pe.offset(FACTORY) + factory['bytes']]
    writes = []
    for ins in rt.decoder.disasm(code, FACTORY):
        for operand in ins.operands:
            if operand.type != capstone.x86.X86_OP_MEM or not operand.access & capstone.CS_AC_WRITE:
                continue
            base = ins.reg_name(operand.mem.base)
            relative = operand.mem.disp if base == 'rbp' else operand.mem.disp - 0x80 if base == 'rsp' else None
            if relative is not None and relative < 0x29c and relative + operand.size > 0x298:
                writes.append(dict(address=hex(ins.address), relativeToRbp=relative,
                                   bytes=operand.size, instruction=ins.mnemonic + ' ' + ins.op_str))
    early = [w for w in writes if int(w['address'], 16) < 0x15153dfe8]
    if [w['address'] for w in early] != ['0x15153ddd9']:
        raise ValueError('Factory second argument slot has another direct pre-query write')
    if not any(w['address'] == '0x15153f785' for w in writes):
        raise ValueError('Known later factory slot reuse disappeared')
    fields = dict(backing=next(f for f in rt.records[34113]['fields'] if f['offset'] == 0x10),
                  field2c=next(f for f in rt.records[88968]['fields'] if f['index'] == 442107),
                  gadgetCache=next(f for f in rt.records[54132]['fields'] if f['name'] == 'FNLANEFCGPE'))
    if fields['backing']['type'].get('typeDefinition') != 88968 or fields['field2c']['type'].get('typeDefinition') != 58209:
        raise ValueError('Typed Item backing and encoded field declaration differ')
    literal = Literals72(rt.binary, rt.metadata).selected((48333,))['literals'][0]
    if literal['value'] != 'Data/_ExcelBinOutput/GadgetExcelConfigData':
        raise ValueError('Original named Gadget resource literal changed')
    named = [m for m in scene['declarations']['methods'] if m['entry'] in (hex(NAMED), hex(GETTER))]
    for method in named:
        rt.bind_function(int(method['entry'], 16))
        if rt.functions[method['entry']]['sha256'] != method['codeSHA256']:
            raise ValueError('Frozen named Gadget query bytes differ')
    return dict(newWindows=windows, newWindowCount=3, newWindowBytes=sum(w['bytes'] for w in windows),
                factorySlotWrites=writes, onlyDirectWriteBeforeFirstQuery='0x15153ddd9', fields=fields,
                namedGadgetLiteral=literal, reusedNamedQueryMethods=named,
                naturalFullFactoryControlFlowVerified=False, laterSlotReuseExplicit=True)


def parse_gadget_and_cache(rt, scene):
    e = rt.e
    original = next(r for r in scene['gadgetRows'] if r['key'] == 50011101)
    payload = (ROOT / 'local/migration72/d02-active/scene-runtime-context-astra/Gadget.native-m_bytes.bin').read_bytes()
    data = payload[original['offset']:original['offset'] + original['length']]
    if sha(data) != original['sliceSHA256'] or data != (ROOT / 'local/migration72/d02-active/scene-runtime-context-astra/Gadget50011101.native-row.bin').read_bytes():
        raise ValueError('Actual Gadget positive source span differs')
    prior.bind_generics(rt, 0x152e109f0)
    rt.dictionary_class = next(k for k, typ in rt.type_records.items() if typ.get('typeDefinition') == 1222
        and [a.get('typeDefinition') for a in typ['arguments']] == [None, 40472])
    rt.reader, rt.row_entry = e.alloc(64), 0x14e62b290
    array = rt.array(len(data), 1)
    e.uc.mem_write(array + 0x20, data)
    e.write(rt.reader + 0x10, array)
    obj = prior.equip.object_storage(rt, 40472)
    trace = rt.call(0x14e62b290, rcx=obj, rdx=rt.reader)
    if trace['stop'] != hex(RETURN) or e.read(rt.reader + 0x1c, 'I') != len(data):
        raise ValueError('Original Gadget parser did not consume its exact real row')
    values = []
    for owner in (27264, 40472):
        for field in rt.records[owner]['fields']:
            offset, typ = field['offset'], field['type']
            if offset is None:
                continue
            kind = typ['kind']
            if kind == 'string':
                value = rt.strings.get(e.read(obj + offset))
            elif typ.get('typeDefinition') == 58209:
                value = rt.decode(obj + offset)
            elif kind in ('bool', 'uint32', 'int32', 'uint64'):
                value = e.read(obj + offset, {'bool': 'B', 'uint32': 'I', 'int32': 'i', 'uint64': 'Q'}[kind])
                if kind == 'bool':
                    value = bool(value)
            elif kind == 'valuetype' and rt.records[typ['typeDefinition']]['parent'].get('typeDefinition') == 204:
                value = e.read(obj + offset, 'I')
            else:
                continue
            values.append(dict(owner=owner, field=field, value=value))
    if values != original['values']:
        raise ValueError('Independent real Gadget row logical replay differs from Scene proof')
    dictionary = new_cache(rt)
    context = e.read(rt.dictionary_class + 0x30)
    e.write(STACK + 0x100030, e.read(context + 0x88))
    inserted = rt.call(0x1578b6a20, rcx=dictionary, rdx=50011101, r8=obj, r9=2)
    if inserted['stop'] != hex(RETURN) or e.read(dictionary + 0x38, 'I') != 1:
        raise ValueError('Original insertion did not install the actual parsed Gadget row')
    e.write(GLOBAL + 0x237d8, dictionary)
    e.write(0x14587cb61, 0, 'B')
    return dict(pointer=hex(obj), key=50011101, source=dict(begin=original['offset'], end=original['offset'] + len(data), sha256=sha(data)),
        parser=trace, values=values, constructorExecuted=False, originalSceneLogicalValuesEqual=True,
        dictionary=hex(dictionary), cacheType=rt.type_records[rt.dictionary_class], insertion=inserted,
        controlledNonStreaming=True, naturalCachePublication=False)


def new_cache(rt):
    e = rt.e
    pointer = e.alloc(80)
    e.write(pointer, rt.dictionary_class)
    e.write(pointer + 0x18, rt.array(4, 24))
    e.write(pointer + 0x20, rt.array(7, 4))
    e.write(pointer + 0x3c, 0xffffffff, 'I')
    return pointer


def bridge_case(rt, row, name, expected_id, gadget, encoded=None, high_id=False, empty_cache=False):
    e = rt.e
    weapon = int(row['pointer'], 16)
    backing = e.read(weapon + 0x10)
    before = bytes(e.uc.mem_read(backing + 0x2c, 4))
    cache = e.read(GLOBAL + 0x237d8)
    encoder = None
    if encoded is not None:
        e.uc.mem_write(backing + 0x2c, encoded)
    if high_id:
        encoder = rt.call(0x14aefa090, rcx=backing + 0x2c, rdx=0xffffffff)
    if empty_cache:
        e.write(GLOBAL + 0x237d8, new_cache(rt))
    try:
        field = rt.call(FIELD_START, stops=(FIELD_STOP,), stack_return=False,
                        rsp=STACK + 0x100000, r14=weapon)
        decoded = e.reg('ebx')
        if field['stop'] != hex(FIELD_STOP) or decoded != expected_id:
            raise ValueError('Original Weapon field fragment did not produce the expected u32 in EBX')
        # EBX is preserved in the same CPU; original instructions perform EDX=EBX.
        call = rt.call(CALL_START, stops=(ARG_STOP,), stack_return=False,
                       rsp=STACK + 0x120000, rdi=0xa0100042)
        rbp, rsp = e.reg('rbp'), e.reg('rsp')
        slot = bytes(e.uc.mem_read(rbp + 0x298, 4))
        argument = int.from_bytes(slot, 'little')
        observed = next(x for x in call['observedEntries'] if x['entry'] == hex(FACTORY))
        if argument != decoded or int(observed['rdx'], 16) != decoded or call['stop'] != hex(ARG_STOP):
            raise ValueError('Exact call edge and original prologue lost the decoded second argument')
        # Separate declared invocation. The intervening factory instructions do not execute.
        query = rt.call(QUERY_START, stops=(QUERY_STOP,), stack_return=False, rsp=rsp, rbp=rbp)
        returned = e.reg('rcx')
        expected_pointer = int(gadget['pointer'], 16) if expected_id == 50011101 and not empty_cache else 0
        if query['stop'] != hex(QUERY_STOP) or returned != expected_pointer:
            raise ValueError('Original named Gadget query did not return the actual positive/missing row')
        if bytes(e.uc.mem_read(rbp + 0x298, 4)) != slot:
            raise ValueError('Actual saved argument slot was overwritten before independent query')
        if not {hex(NAMED), hex(GETTER), '0x1578b66f0'} <= set(query['executedAddresses']):
            raise ValueError('Named Gadget wrapper/cache query was not original native execution')
        return dict(name=name, weaponId=row['key'], source=row['source'], originalEncodedWord=before.hex(),
            suppliedEncodedWord=bytes(e.uc.mem_read(backing + 0x2c, 4)).hex(), decodedId=decoded,
            encoder=encoder, field=field, callEdgeAndPrologue=call,
            originalFactoryFrame=dict(rbp=hex(rbp), rsp=hex(rsp), slot=hex(rbp + 0x298), slotBytes=slot.hex(),
                originalSecondU32=argument, preservedAcrossDeclaredQuery=True),
            query=query, returnedGadgetPointer=hex(returned), actualParsedGadgetSamePointer=returned == int(gadget['pointer'], 16),
            expectedMissing=expected_pointer == 0, selectedCacheOnly=True, emptyCache=empty_cache,
            independentSlices=True, continuousNaturalFactory=False, entityLifecycleExecuted=False)
    finally:
        e.uc.mem_write(backing + 0x2c, before)
        e.write(GLOBAL + 0x237d8, cache)


def null_weapon_case(rt):
    trace = rt.call(FIELD_START, stack_return=False, rsp=STACK + 0x100000, r14=0)
    if trace['stop'] != '0x1404c2be0' or hex(FACTORY) in trace['executedAddresses']:
        raise ValueError('Original null Weapon field boundary differs')
    return dict(name='null-weapon', trace=trace, exceptionHelperExecuted=False, resourceDefaultApproved=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('A fresh report in this card private directory is required')
    pins, _ = prior.equip.verify_inputs(args.client)
    for name, digest in (item.PINS | PINS).items():
        prior.card.base.read_verified(ROOT / name, digest)
        pins[name] = digest
    binary, metadata, startup = [(args.client / name).read_bytes() for name in list(prior.card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((prior.equip.RAW / 'metadata.private.pkl').read_bytes())
    codec = prior.card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = GadgetBridgeRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    result = dict(task='E-01/R3-WEAPON-GADGET-BRIDGE', schemaVersion=1, sourcePins=pins,
        sourceSHA256=sha(Path(__file__).read_bytes()),
        testSourceSHA256=sha((ROOT / 'tools/test_probe72_weapon_gadget_bridge.py').read_bytes()), cases=[])
    try:
        scene = json.loads(SCENE.read_bytes())
        result['staticContract'] = static_contract(rt, scene)
        table = next(t for t in json.loads(item.EQUIPMENT_REPORT.read_bytes())['tables'] if t['name'] == 'Weapon')
        result['selectedWeaponRows'] = rows = item.parse_selected(rt, table)
        old = json.loads((item.RAW / 'main-third.json').read_bytes())
        for row in rows:
            frozen = next(x for x in old['selectedRows'] if x['key'] == row['key'])
            if row['fields'] != frozen['fields'] or row['source'] != frozen['source']:
                raise ValueError('Real Weapon logical fields differ from the fixed Item third proof')
        result['gadget'] = gadget = parse_gadget_and_cache(rt, scene)
        encoded = [bytes(rt.e.uc.mem_read(rt.e.read(int(row['pointer'], 16) + 0x10) + 0x2c, 4)) for row in rows]
        result['cases'].append(bridge_case(rt, rows[0], 'original-11101', 50011101, gadget))
        result['cases'].append(bridge_case(rt, rows[1], 'original-11501-selected-cache-miss', 50011501, gadget))
        result['cases'].append(bridge_case(rt, rows[0], '11101-encoded-from-11501', 50011501, gadget, encoded=encoded[1]))
        result['cases'].append(bridge_case(rt, rows[1], '11501-encoded-from-11101', 50011101, gadget, encoded=encoded[0]))
        result['cases'].append(bridge_case(rt, rows[0], 'uint32-max-missing', 0xffffffff, gadget, high_id=True))
        result['cases'].append(bridge_case(rt, rows[0], 'original-11101-empty-cache', 50011101, gadget, empty_cache=True))
        result['cases'].append(null_weapon_case(rt))
        # Only this new successful bridge report advances the source-field role;
        # the frozen Item parser/report remains unchanged.
        for row in rows:
            row['field2c']['gadgetIdAliasApproved'] = True
            row['field2c']['semanticScope'] = 'Weapon managed field; complete row/configuration is not approved'
        result['javaProjectionFragments'] = [dict(table='Weapon', key=11101, javaFields=dict(id=11101, gadgetId=50011101),
            fieldSource=dict(owner=88968, fieldIndex=442107, offset='0x2c', type=58209,
                             source=rows[0]['source'], encodedBits=rows[0]['field2c']['encodedBits']),
            gadgetSource=gadget['source'], fragmentOnly=True, completeDeclaredJavaInputRow=False,
            actualJavaLoaded=False, gadgetConfigurationApproved=False, abilityInputsApproved=False)]
        result.update(nativeCalls=rt.native_calls, nativeCallTotals=rt.call_totals,
            nativeInstructionHookVisits=rt.call_instruction_total, nativeSources=rt.functions,
            gadgetIdAliasApproved=True, fullFactoryExecuted=False, naturalCodexFlowExecuted=False,
            entityLifecycleExecuted=False, fullTableParsingNewlyExecuted=False, completeItemInputRow=False,
            javaResourcesLoaded=False, activeRevisionVerified=False, resourceReady=False, d02Complete=False,
            hookLimit=HOOK_LIMIT, eachCallLimit=2_000_000, scalarStorageCodec=codec['proof'],
            nativeConfigTypedFixture=rt.config_proof,
            modelScope=['Existing typed managed storage, metadata/RGCTX, memory libraries/UTF8 and paired resource codec are reused; no new consumer-success model or Random fixture is added.',
                'Only two real Weapon spans and one real Gadget span are parsed. No whole table, primary index, archive extraction or resource search runs.',
                'Gadget cache holds only the actual50011101 parser object, inserted/read by original UInt32 Dictionary methods. 50011501 miss describes this selected cache only.',
                'The non-streaming flag is explicitly false as in the frozen Scene contract; natural runtime mode/publication is not verified.',
                'Field slice, direct-call/prologue slice and query slice are separate declared invocations. The actual prologue saves the decoded argument/frame; no PC jump is disguised as continuous factory execution.',
                'The named24-byte handle carries the actual Gadget pointer at+10, read by original factory instructions intoRCX. Query success is not inferred from ID equality.',
                'No factory middle branch, entity allocator, pool, lifecycle, Gadget jsonName/type/config/ability or complete Java resource row is approved.'])
    except Exception as error:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(error=str(error), partial=result,
            nativeFailure=rt.e.failure, nativeCalls=rt.native_calls, nativeCallTotals=rt.call_totals,
            nativeInstructionHookVisits=rt.call_instruction_total, nativeSources=rt.functions,
            registers={k: hex(rt.e.reg(k)) for k in ('rip', 'rsp', 'rbp', 'rcx', 'rdx', 'rbx', 'r14')}), indent=2, sort_keys=True) + '\n', 'utf8')
        raise
    finally:
        rt.e.close()
    prior.equip.verify_client(args.client)
    result['fiveInputsRecheckedAfterNative'] = True
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(result['cases']), hooks=result['nativeInstructionHookVisits'],
                         gadgetIdAliasApproved=True, resourceReady=False)))


if __name__ == '__main__':
    main()
