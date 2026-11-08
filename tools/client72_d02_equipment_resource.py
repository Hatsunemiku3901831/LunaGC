"""Hash-bound initial-weapon and weapon-property bridge; offline native only."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import pickle
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import client72_avatar_natural_tables as card
from tools.client72_d02_resource_bridge import install_table
from tools.client72_emulator import GLOBAL, STACK, END
from tools.client72_resource_deep import RETURN
from tools.client72_scene_deep import method_usages

RAW = ROOT / 'local/migration72/d02-resume-20261108/r3-equipment'
INVENTORY = ROOT / 'local/migration72/d02-resume-20261108/initial-inventory.private.json'
OLD_REPORT = ROOT / 'local/migration72/d02-active/avatar/natural-tables/first.json'
R3_REPORT = ROOT / 'local/migration72/d02-resume-20261108/r3-mapping/main-third.json'
LOCATOR = ROOT / 'local/migration72/d02-active/resource-locator-astra/locator-final-1.json'
SOURCE = LOCATOR.with_suffix('')
CURVE = RAW / 'WeaponCurve.cached-m_bytes.bin'
CURVE_WHOLE, CURVE_ROW = 0x1502c1960, 0x14a3a9750
CURVE_COUNT = 100
WINDOWS = (0x151843490, 0x15184a5f0, 0x14ad07e80, 0x14ad08160, 0x14e9b15a0,
           0x150758430, 0x150757a00, 0x1507587b0, 0x1518545b0, CURVE_WHOLE)
PINS = {
    INVENTORY: 'f891bafd31c950985ce214cad90e0ba24d2e8733a24dbdca21f9823474c6ff25',
    OLD_REPORT: '1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86',
    R3_REPORT: 'd990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e',
    LOCATOR: 'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda',
    ROOT / 'tools/client72_d02_resource_bridge.py': 'a61531b49894a69a864c0467d683bb8f749dfcfe57051eda83b17a1cebdbbc11',
    ROOT / 'protocol/cn-7.2/equip-contract-observations.json': '77e1df9d53d93380362f30ec0ad604b447a1123f4840963fa9f91a1da41b5011',
    ROOT / 'tools/client72_equip_contract.py': 'dd3b0033846f62c36a527b2244d6d91737e50e825954eab167b952f60ed0a764',
    RAW / 'metadata.private.pkl': 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    RAW / 'java-manifest.private.json': 'b254fd665d9786f6d30ec3a6198604ff81e23872d260f1700a7593ef12a6f91b',
    CURVE: '14c5a563a352e01947cdea3b067a0cbfd0c25acbe9d58b6b21207f6f1a462f9f',
    CURVE.with_suffix('.source.private.json'): '81ff072affaa33837380f0a4d401339bc7d410e4418364ed24fcbaf8c18de38e',
    ROOT / 'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
    ROOT / 'local/migration72/python/unicorn/lib/unicorn.dll': '010d85dce84b8f6ffaec3d5420f1b5f7768f859eb831be574c7da23e656ee8f1',
}


def main():
    args = arguments()
    pins, sources = verify_inputs(args.client)
    binary, metadata, startup = [(args.client / name).read_bytes() for name in list(card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((RAW / 'metadata.private.pkl').read_bytes())
    codec = card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = EquipmentRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    try:
        tables, consumers = run_native(rt)
        fragments, alignment = project(tables, rt.enum_symbols, sources)
        result = dict(schemaVersion=1, task='E-01-bridge-equipment', sourcePins=pins,
            tables=tables, consumers=consumers, javaProjectionFragments=fragments,
            enumAlignment=alignment, staticConsumers=static_consumers(rt),
            codeSources=rt.functions, scalarStorageCodec=codec['proof'],
            inheritedItemTypeConsumer=dict(report='protocol/cn-7.2/equip-contract-observations.json',
                factory='0x150ed35e0', originalResourceEnumType=34819,
                actualNewRowField='Weapon base88968+38', scope='Named enum/factory contract reused from prior controlled-resource fixtures; not counted as new actual-row factory execution.'),
            sourcePresence=dict(initialWeapon='Read directly from actual parsed Avatar+154; no Java default supplied.',
                weaponProperties='Actual parsed array has two entries in source order; all three logical fields are retained including NONE/zero.',
                individualPropertyBinaryOptionalFlags='Not separately mapped; logical zero is an observed original-parser result, not a claim that an explicit zero token was present.',
                curveInfos='Original ordered inline array; each level key, all enum values and float bits retained.'),
            unresolvedPositiveReferences=[dict(resource='WeaponPromote', id=11101, promoteLevel=0,
                exactGetter='0x147bf60c0', requiredInput='same-source WeaponPromote row for group11101/phase0; no substitute empty addProps'),
                dict(resource='complete Avatar/Item rows', requiredInput='other mandatory AvatarCurve/AvatarPromote/ability/gadget fields and actual Java loader outcomes remain outside this fragment card')],
            sourceSHA256=sha(Path(__file__).read_bytes()),
            testSourceSHA256=sha((ROOT / 'tools/test_client72_d02_equipment_resource.py').read_bytes()),
            activeSelectedRevision=None, activeRevisionVerified=False,
            completeAvatarHandler=False, wholeEquipmentAssignment=False,
            javaResourcesLoaded=False, resourceReady=False, d02Complete=False,
            modelScope=[
                'Frozen typed NaturalRuntime storage, metadata/RGCTX, allocation, UTF8 and memcpy/memset models; original parsers, codecs and UInt32 Dictionary execute.',
                'GlobalVars HashSet<Int32> uses its exact metadata type and explicitly null buckets; secondary List is absent. This fixes the old type label, not natural NativeConfig mode/3521/6506.',
                'Avatar row lookup is native against the newly parsed actual cache. Initial weapon producer runs three exact slices with explicit mutable typed proto objects, actor and synthetic uint64 GUID.',
                'Weapon model and level proto are typed caller storage; stats execute from the real entry until the exact promote call, before an unprovided Promote table.',
                'Curve is selected from the already frozen serialized object/index cache; no archive decoding or global index reconstruction is repeated.',
                'These are field/row fragments, not installed Item/Avatar resources, Java loading, natural selected revision or a client session.',
            ])
    except Exception as error:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(error=str(error),
            nativeFailure=rt.e.failure, registers={k: hex(rt.e.reg(k)) for k in ('rip', 'rsp', 'rcx', 'rdx', 'r8', 'r9', 'rax')},
            calls=rt.call_order[-100:], codeSources=rt.functions), indent=2) + '\n', 'utf8')
        raise
    finally:
        rt.e.close()
    verify_client(args.client)
    result['fiveInputsRecheckedAfterNative'] = True
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False, sort_keys=True) + '\n', 'utf8')
    print(json.dumps(dict(task=result['task'], tableRows=[(x['name'], x['count']) for x in tables],
                          projectedFragments=len(fragments), resourceReady=False)))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.client, args.output = args.client.resolve(), args.output.resolve()
    if args.output.exists() or not args.output.is_relative_to(RAW.resolve()):
        parser.error('Require a fresh report within this card private raw directory')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    return args


def sha(data):
    return hashlib.sha256(data).hexdigest()


def verify_client(client):
    for name, digest in card.FIVE_INPUTS.items():
        with (client / name).open('rb') as stream:
            actual = hashlib.file_digest(stream, 'sha256').hexdigest()
        if actual != digest:
            raise ValueError('Fixed original input changed; no proxy/hash fallback: ' + name)


def verify_inputs(client):
    verified = {path: card.base.read_verified(path, PINS[path]) for path in
                (OLD_REPORT, R3_REPORT, RAW / 'java-manifest.private.json')}
    old, r3 = (json.loads(verified[path]) for path in (OLD_REPORT, R3_REPORT))
    pins = {str(k.relative_to(ROOT)): v for k, v in PINS.items()}
    pins.update(r3['sourcePins'])
    pins['tools/client72_avatar_natural_tables.py'] = old['sourceSHA256']
    pins.update({'tools/' + name: digest for name, digest in old['dependencySHA256'].items()})
    source_names = {}
    for name, identity in json.loads(verified[RAW / 'java-manifest.private.json']).items():
        file = ROOT / 'src/main/java/emu/grasscutter' / name
        pins[str(file.relative_to(ROOT))] = identity['sha256']
        pins[str((RAW / 'java-epoch0' / name).relative_to(ROOT))] = identity['sha256']
        source_names[file] = name
    sources = {}
    for name, digest in pins.items():
        path = ROOT / name
        data = verified[path] if path in verified else card.base.read_verified(path, digest)
        if path in source_names:
            sources[source_names[path]] = data.decode('utf8')
    for name, spec in card.TABLES.items():
        card.base.read_verified(SOURCE / (name + '.native-m_bytes.bin'), spec[4])
    locator = json.loads(LOCATOR.read_bytes())
    private = json.loads(CURVE.with_suffix('.source.private.json').read_bytes())
    serialized = card.base.read_verified(SOURCE / '25539185.serialized.bin', locator['payload']['outputSHA256'])
    if serialized[private['serializedOffset']:private['serializedOffset'] + private['bytes']] != CURVE.read_bytes():
        raise ValueError('New curve projection differs from exact frozen carrier range')
    verify_client(client)
    pins.update({'GAME_PATH/' + name: digest for name, digest in card.FIVE_INPUTS.items()})
    return pins, sources


class EquipmentRuntime(card.NaturalRuntime):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        # Exact source is GlobalVars11325, not the former unrelated UInt32 set.
        field = next(f for f in self.records[11325]['fields'] if f['name'] == '_serializedCodeSwitches')
        if field['type']['arguments'][0]['kind'] != 'int32':
            raise ValueError('NativeConfig exact HashSet<Int32> source changed')
        configuration = self.e.alloc(0x48)
        self.e.write(configuration, self.bind_type(field['type']))
        self.e.write(GLOBAL + 0x60e8, configuration)
        self.config_proof = dict(sourceOwner=11325, field=field, bucketsExplicitlyNull=True,
                                 actualNativeInitializationObserved=False)
        enums = card.recover_enums(self.binary, self.metadata, self.records, indices=(58570,))['enums']
        self.enum_symbols.update({x['typeIndex']: x for x in enums})

    def call(self, entry, stops=(), **registers):
        e = self.e
        e.calls, e.failure, e.steps = [], None, 0
        e.write(STACK + 0x100008, RETURN)
        for key, value in dict(rsp=STACK + 0x100008, **registers).items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + key.upper()), value)
        counts, reached = Counter(), []
        stop_set = {RETURN, 0x1404c2be0, 0x1404c2650, 0x1404c2c70, 0x1404c2770, 0x1404bbd90, *stops}
        e.allowed.extend((x, x + 1) for x in stop_set)
        def observe(address):
            if address == self.row_entry and self.reader and e.reg('rdx') == self.reader:
                self.pending_rows[e.read(e.reg('rsp'))] = (e.reg('rcx'), e.read(self.reader + 0x1c, 'I'))
            if address in self.pending_rows:
                pointer, begin = self.pending_rows.pop(address)
                self.spans.append(dict(pointer=hex(pointer), begin=begin, end=e.read(self.reader + 0x1c, 'I')))
            if address in self.extents or address in e.ends:
                self.bind_function(address)
                self.call_order.append(dict(entry=hex(address), rcx=hex(e.reg('rcx')), rdx=hex(e.reg('rdx'))))
            counts[address] += 1
            if address in stop_set:
                reached.append(address)
                e.uc.emu_stop()
        e.before_instruction = observe
        try:
            e.uc.emu_start(entry, END, count=2_000_000)
        finally:
            e.before_instruction = None
        if e.failure or len(reached) != 1:
            raise ValueError(e.failure or 'Exact native stop not reached within two million instructions')
        return dict(entry=hex(entry), stop=hex(reached[0]), instructions=e.steps,
                    executedAddresses={hex(k): v for k, v in sorted(counts.items())},
                    models=[r['address'] for r in e.calls])

    def snapshot_value(self, pointer, typ, depth):
        if typ.get('typeDefinition') == 66952:
            return [dict(field=f['name'], fieldIndex=f['index'], offset=hex(f['offset'] - 16),
                         value=self.snapshot_value(pointer + f['offset'] - 16, f['type'], depth + 1))
                    for f in self.records[66952]['fields'] if f['offsetKind'] == 'instance']
        return super().snapshot_value(pointer, typ, depth)


def object_storage(rt, index):
    record = rt.records[index]
    pointer = rt.e.alloc(int(record['fieldLayout']['rawSizeAndFlags'][0], 16))
    rt.e.write(pointer, rt.bind_type(dict(kind='class', typeDefinition=index, name=record['name'], namespace=record['namespace'])))
    rt.objects[pointer] = index
    return pointer


def install_curve(rt):
    e = rt.e
    rt.capacity, rt.reader, rt.spans, rt.pending_rows, rt.row_entry = CURVE_COUNT, 0, [], {}, CURVE_ROW
    rt.bind_function(CURVE_WHOLE)
    refs = {int(x['target'], 16) for x in rt.functions[hex(CURVE_WHOLE)]['references'] if int(x['target'], 16) not in rt.cells}
    uses = method_usages(rt.binary, rt.metadata, rt.startup, rt.records, refs)
    typ = next(f['type'] for f in rt.records[67884]['fields'] if f['rawOffset'] == '0x40123a0')
    rt.dictionary_class = klass = rt.bind_type(typ)
    context, constructor, insert = e.alloc(256), e.alloc(64), e.alloc(64)
    e.write(klass + 0x30, context)
    e.write(constructor, klass)
    e.write(constructor + 8, e.model_stub(rt.dictionary_constructor))
    e.write(context + 0x10, constructor)
    e.write(insert, klass)
    e.write(insert + 8, 0x1578b6a20)
    e.write(context + 0x88, insert)
    for use in uses:
        if use['ownerType'] != 1222 or use['classArguments'][1].get('typeDefinition') != 12924:
            raise ValueError('Unexpected curve whole-loader context')
        method = e.alloc(64)
        e.write(method, klass)
        e.write(int(use['cell'], 16), method)
        rt.method_cells[int(use['cell'], 16)] = use
    blob = CURVE.read_bytes()
    count = ((struct.unpack_from('<I', blob)[0] ^ 0x956381b7) + 0xf4c7de0b) & 0xffffffff
    if count != CURVE_COUNT:
        raise ValueError('Curve count source changed')
    rt.reader = e.alloc(64)
    array = rt.array(len(blob), 1)
    e.uc.mem_write(array + 0x20, blob)
    e.write(rt.reader + 0x10, array)
    trace = rt.call(CURVE_WHOLE, rcx=rt.reader)
    dictionary = e.read(GLOBAL + 0x123a0)
    entries = e.read(dictionary + 0x18)
    if trace['stop'] != hex(RETURN) or e.read(rt.reader + 0x1c, 'I') != len(blob) or e.read(dictionary + 0x38, 'I') != count:
        raise ValueError('Original curve parser did not cover the exact table')
    spans = {x['pointer']: x for x in rt.spans}
    rows = []
    for i in range(count):
        key, pointer = e.read(entries + 0x28 + i * 24, 'I'), e.read(entries + 0x30 + i * 24)
        span = spans[hex(pointer)]
        if rt.decode(pointer + 0x18) != key:
            raise ValueError('Actual curve row ID and dictionary key differ')
        rows.append(dict(key=key, pointer=hex(pointer), source=dict(begin=span['begin'], end=span['end'],
            sha256=sha(blob[span['begin']:span['end']])), fields=rt.snapshot_object(pointer, 12924, 0)))
    return dict(name='WeaponCurve', count=count, payloadBytes=len(blob), payloadSHA256=sha(blob),
                cursor=len(blob), rows=rows, trace=trace, sourceBinding=json.loads(CURVE.with_suffix('.source.private.json').read_bytes()))


def run_native(rt):
    e = rt.e
    weapon = rt.parse_table((SOURCE / 'Weapon.native-m_bytes.bin').read_bytes())
    install_table(rt, 'Avatar')
    avatar = rt.parse_table((SOURCE / 'Avatar.native-m_bytes.bin').read_bytes())
    curves = install_curve(rt)
    rt.bind_function(0x14e9b15a0)
    rt.bind_function(0x150758430)
    rt.bind_function(0x1507587b0)
    initial = [initial_weapon(rt, avatar, weapon, value) for value in (10000005, 10000007, 10000136)]
    properties = [weapon_property(rt, weapon, curves, key, index, level)
                  for key, index, level in [(11101, 0, 1), (11101, 1, 1), (11101, 0, 20), (11101, 1, 20)]]
    query = rt.call(card.QUERIES['Weapon'], rcx=0xffffffff)
    if e.reg('rax') != 0 or query['stop'] != hex(RETURN):
        raise ValueError('Missing Weapon query did not return null')
    compact = []
    for table in (avatar, weapon):
        keys = ('name', 'payloadSHA256', 'payloadBytes', 'count', 'cursor', 'rows', 'selected', 'trace', 'scratchUsedAtEnd')
        compact.append({k: table[k] for k in keys})
    compact.append(curves)
    return compact, dict(initialWeapon=initial, weaponProperties=properties,
        missingWeapon=dict(key=0xffffffff, returnedNull=True, trace=query),
        correctNativeConfigType='GlobalVars._serializedCodeSwitches:HashSet<Int32>',
        nativeConfigTypedFixture=rt.config_proof,
        completeNativeConfigInitialization=False)


def initial_weapon(rt, avatar, weapon, avatar_id):
    e = rt.e
    lookup = rt.call(0x14e9b1631, stops=(0x14e9b1684, 0x14e9b1842), rdi=avatar_id)
    selected = next(x for x in avatar['rows'] if x['key'] == avatar_id)
    pointer = e.reg('rbx')
    if lookup['stop'] != '0x14e9b1684' or pointer != int(selected['pointer'], 16):
        raise ValueError('Original initial-weapon caller selected the wrong actual Avatar row')
    proto, weapon_proto, equip, actor = (object_storage(rt, index) for index in (65333, 64947, 44169, 18163))
    guid = 0x8000000100000001
    e.write(proto + 0x20, guid)
    first = rt.call(0x14e9b1776, stops=(0x14e9b17a5,), rbx=pointer, r14=proto)
    item_id = e.read(proto + 0x28, 'I')
    level = rt.call(0x14e9b17b4, stops=(0x14e9b17d0,), rax=weapon_proto, rdi=weapon_proto)
    wrap = rt.call(0x14e9b17df, stops=(0x14e9b183b,), rax=equip, rbx=equip,
                   rdi=weapon_proto, r14=proto, rsi=actor)
    query = rt.call(card.QUERIES['Weapon'], rcx=item_id)
    referenced = next((x for x in weapon['rows'] if x['key'] == item_id), None)
    if referenced is None or e.reg('rax') != int(referenced['pointer'], 16):
        raise ValueError('Positive initialWeapon reference is absent from actual Weapon namespace')
    if e.read(weapon_proto + 0x30, 'I') != 1 or e.read(proto + 0x2c, 'I') != 6 or e.read(proto + 0x20) != guid:
        raise ValueError('Original initial-weapon producer level/case/GUID differs')
    return dict(avatarId=avatar_id, source=selected['source'], initialWeapon=item_id,
                sourceField=dict(owner=58272, index=289481, name='ECGFIKIODEP', offset='0x154'),
                lookup=lookup, idProducer=first, defaultLevelProducer=level, equipCaseProducer=wrap,
                weaponQuery=query, referencedWeaponSource=referenced['source'],
                initialLevel=1, equipCase=6, syntheticGuidDecimal=str(guid), syntheticGuidBits=f'{guid:016x}',
                guidPreserved=True, fullProducerExecuted=False, wholeAssignmentExecuted=False)


def weapon_property(rt, table, curves, item_id, index, level):
    e = rt.e
    actual = next(x for x in table['rows'] if x['key'] == item_id)
    pointer = int(actual['pointer'], 16)
    model, proto = object_storage(rt, 29353), object_storage(rt, 64947)
    e.write(model + 0x30, pointer)
    e.write(model + 0x40, proto)
    e.write(proto + 0x30, level, 'I')
    trace = rt.call(0x150758430, stops=(0x1507585f0,), rcx=model, rdx=index)
    result_bits = e.reg('xmm6') & 0xffffffff
    backing = e.read(pointer + 0x20)
    array = e.read(backing + 0x68)
    prop = e.read(array + 0x20 + index * 8)
    curve_type, fight_prop = e.read(prop + 0x10, 'I'), e.read(prop + 0x18, 'I')
    curve_row = next(x for x in curves['rows'] if x['key'] == level)
    info = e.read(int(curve_row['pointer'], 16) + 0x10)
    values = []
    for i in range(e.read(info + 0x18, 'I')):
        if e.read(info + 0x20 + i * 12, 'I') == curve_type:
            values.append(info + 0x20 + i * 12)
    if len(values) != 1 or e.reg('edx') != fight_prop or e.reg('r8d') != 0:
        raise ValueError('Actual property/curve/promote caller values differ')
    rt.call(0x14f905140, rcx=prop + 0x14)
    base_bits = e.reg('xmm0') & 0xffffffff
    rt.call(0x14f905140, rcx=values[0] + 8)
    factor_bits = e.reg('xmm0') & 0xffffffff
    arith = e.read(values[0] + 4, 'I')
    base, factor = (struct.unpack('<f', struct.pack('<I', x))[0] for x in (base_bits, factor_bits))
    if arith != 2 or result_bits != struct.unpack('<I', struct.pack('<f', base * factor))[0]:
        raise ValueError('Requested Java multiplier semantics do not match actual native curve')
    promote_trace = rt.call(0x1507587b0, stops=(0x147bf60c0,), rcx=model, rdx=fight_prop, r8=0)
    promote_id, promote_level = e.reg('ecx'), e.reg('edx')
    if promote_id != rt.decode(backing + 0x84) or promote_level != 0:
        raise ValueError('Actual WeaponPromote source/caller differs')
    return dict(itemId=item_id, source=actual['source'], index=index, level=level,
                curveType=curve_type, fightProperty=fight_prop, initValueBits=f'{base_bits:08x}',
                curveValueBits=f'{factor_bits:08x}', arith=arith, nativeValueBeforePromoteBits=f'{result_bits:08x}',
                independentFloat32MultiplyEqual=True, trace=trace, stoppedBeforePromote=True,
                weaponPromoteId=promote_id, promoteLevel=promote_level, promoteIdConsumer=promote_trace,
                actualPromoteRowSupplied=False)


def java_id(value):
    if type(value) is not int or not 1 <= value <= 0x7fffffff:
        raise ValueError('Positive uint32 resource ID cannot be represented by Java int')
    return value


def exact_enum(value, index, enums, java=None):
    matches = [x['symbol'] for x in enums[index]['values'] if x['value'] == value]
    if len(matches) != 1 or java is not None and java.get(matches[0]) != value:
        raise ValueError('Unknown or nonmatching named enum, no default fallback')
    return matches[0]


def project(tables, enums, sources):
    java_item = {n: int(v) for n, v in re.findall(r'^    (ITEM_\w+)\((\d+)\)', sources['game/inventory/ItemType.java'], re.M)}
    java_fight = {n: int(v) for n, v in re.findall(r'^    (FIGHT_PROP_\w+)\((\d+)\)', sources['game/props/FightProperty.java'], re.M)}
    by_name = {x['name']: x for x in tables}
    weapon_keys = {x['key'] for x in by_name['Weapon']['rows']}
    fragments = []
    for row in by_name['Avatar']['selected']:
        fields = {x['offset']: x['value'] for x in row['fields']}
        initial = java_id(fields['0x154'])
        if initial not in weapon_keys:
            raise ValueError('Missing positive initialWeapon reference')
        fragments.append(dict(table='Avatar', key=java_id(row['key']), source=row['source'],
                              javaFields=dict(id=row['key'], initialWeapon=initial), fragmentOnly=True))
    item = by_name['Weapon']['selected'][0]
    backing = next(x for x in item['fields'] if x['offset'] == '0x20' and x['type'].get('typeDefinition') == 20600)
    fields = {x['offset']: x['value'] for x in backing['value']}
    properties = []
    for prop in fields['0x68']:
        original = {x['offset']: x['value'] for x in prop}
        bits = int(original['0x14']['bits'], 16)
        value = struct.unpack('<f', struct.pack('<I', bits))[0]
        properties.append(dict(propType=exact_enum(original['0x18']['value'], 32419, enums, java_fight),
                               initValue=value, type=exact_enum(original['0x10']['value'], 54063, enums)))
    required = {x['type'] for x in properties}
    curve_rows = []
    for row in by_name['WeaponCurve']['rows']:
        fields_curve = {x['offset']: x['value'] for x in row['fields']}
        infos, names = [], set()
        for info in fields_curve['0x10']:
            values = {x['offset']: x['value'] for x in info}
            name = exact_enum(values['0x0']['value'], 54063, enums)
            arith = exact_enum(values['0x4']['value'], 58570, enums)
            if name in names:
                raise ValueError('Duplicate curve type differs between native first-match and Java last-write')
            names.add(name)
            infos.append(dict(type=name, arith=arith, value=values['0x8']['float32']))
            if values['0x4']['value'] != 2:
                raise ValueError('Curve arithmetic is not Java multiplier semantics')
        if not required.issubset(names):
            raise ValueError('Required positive Weapon curve reference is missing')
        curve_rows.append(dict(table='WeaponCurve', key=java_id(row['key']), source=row['source'],
            javaFields=dict(level=java_id(fields_curve['0x18']), curveInfos=infos), fragmentOnly=True))
    fragments.append(dict(table='Weapon', key=java_id(item['key']), source=item['source'],
        javaFields=dict(id=item['key'], itemType=exact_enum(fields['0x38']['value'], 34819, enums, java_item),
                        weaponProp=properties, weaponPromoteId=java_id(fields['0x84'])), fragmentOnly=True))
    fragments.extend(curve_rows)
    alignment = dict(itemType=[dict(targetSymbol=x['symbol'], targetValue=x['value'], accepted=java_item.get(x['symbol']) == x['value'])
                              for x in enums[34819]['values']],
                     fightProperty=[dict(targetSymbol=x['symbol'], targetValue=x['value'], accepted=java_fight.get(x['symbol']) == x['value'])
                                    for x in enums[32419]['values']],
                     curveTypes=enums[54063], curveArithmetic=enums[58570])
    return fragments, alignment


def static_consumers(rt):
    result = []
    methods = {int(m['registeredAddress'], 16): dict(owner=o['index'], ownerName=o['name'], method=m)
               for o in rt.records for m in o['methods'] if m['registeredAddress'] != '0x0'}
    for entry in WINDOWS:
        rt.bind_function(entry)
        end = int(rt.functions[hex(entry)]['end'], 16)
        code = rt.binary[rt.e.pe.offset(entry):rt.e.pe.offset(entry) + end - entry]
        result.append(dict(entry=hex(entry), end=hex(end), sha256=sha(code), metadata=methods[entry],
            instructions=[dict(address=hex(i.address), instruction=i.mnemonic + ' ' + i.op_str) for i in rt.decoder.disasm(code, entry)]))
    return result


if __name__ == '__main__':
    main()
