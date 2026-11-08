"""Fixed-source promote/curve and birth-entry resource contracts; offline only."""
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
from tools import client72_d02_equipment_resource as equip
from tools import client72_avatar_natural_tables as card
from tools.client72_emulator import GLOBAL, STACK, HEAP, END
from tools.client72_resource_deep import RETURN
from tools.client72_scene_deep import method_usages

RAW = ROOT / 'local/migration72/d02-resume-20261108/r3-promote'
EQUIPMENT_REPORT = equip.RAW / 'main-third.json'
PINS = {
    ROOT / 'tools/client72_d02_equipment_resource.py': '0fe44e0c7cf684ed508dd2105695f1aeefe477632158bc8e1b98c9504cef4af9',
    EQUIPMENT_REPORT: '50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc',
    RAW / 'WeaponPromote.cached-m_bytes.bin': '24b6bb8e1a0b519a3c80913804aabe2ab488b507d4fd12e2cc8a175e0c7a2b9b',
    RAW / 'AvatarPromote.cached-m_bytes.bin': '4fadbdbb37babef2ffd8051d78b4cf0fa9476efc6dae8d6ff4d2178e8d832449',
    RAW / 'AvatarCurve.cached-m_bytes.bin': '4b7eb712589496626b69bae4fe9a55c592f18c894877c698a183158bdf487aa3',
    RAW / 'cached-source-bindings.private.json': '1a8b26aaca9d25251ee4993ede9719541e777c7cc468c4af1a176bef15e9f636',
    RAW / 'java-manifest.private.json': '9e62c6731c29573a1e7fb4a6b0a603aaef4a2de40d2c7bf15f27e959cb912f64',
    RAW / 'weapon-promote-entry.private.json': 'a618ebe30431f07a6166de5fdc6efcda229c131accdb4d500971ec4d68763212',
    RAW / 'avatar-entry-windows.private.json': '646be611ba04f06a8ce92244058775b7b6f87daa2fda580213e35405002f5b76',
    RAW / 'avatar-stat-consumer.private.json': 'a9e69e426b5241f347a44bcf2a4a23af29dd723d07456ab74a12cbfe9c1d9f7b',
    RAW / 'avatar-base-consumer.private.json': 'b38f34cc7ecfcf7f5a960e27c15f51407c840c0d68e5c1274516f8feba6b0263',
    RAW / 'avatar-promote-critical-consumers.private.json': '0c2e81b338fb2352faef061dc56469420d7a2bbdb1b7f458afa3a869de198eca',
}
SPECS = {
    'WeaponPromote': dict(owner=22605, row=20394, whole=0x147bf6630, parser=0x150ab84d0,
        listOffset=0x34e20, cacheOffset=0x34e28, groupOffset=0x24, phaseOffset=0x2c,
        builder=0x147bf5b70, getter=0x147bf60c0, group=11101),
    'AvatarPromote': dict(owner=22917, row=17694, whole=0x14af8f430, parser=0x147e0a510,
        listOffset=0x15c88, cacheOffset=0x15c80, groupOffset=0x30, phaseOffset=0x2c,
        builder=0x14af8f760, getter=0x14af8f5c0, group=12),
    'AvatarCurve': dict(owner=39516, row=68066, whole=0x147bd6dd0, parser=0x14fd05cc0,
        cacheOffset=0x34a80, keyOffset=0x18),
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


class PromoteRuntime(equip.EquipmentRuntime):
    def __init__(self, *args, **kwargs):
        self.dictionary_capacity = 0
        self.storage_events = []
        self.call_totals = {}
        self.call_instruction_total = 0
        self.allowed_range_compaction = dict(passes=0,duplicateTuplesRemoved=0,addressCoverageChanged=False)
        super().__init__(*args, **kwargs)
        self.e.models[0x159111b30] = self.list_storage

    def call(self, entry, stops=(), **registers):
        self.compact_allowed_ranges()
        trace = super().call(entry,stops=stops,**registers)
        total = self.call_totals.setdefault(hex(entry),dict(calls=0,instructionHookVisits=0))
        total['calls'] += 1
        total['instructionHookVisits'] += trace['instructions']
        self.call_instruction_total += trace['instructions']
        return trace

    def compact_allowed_ranges(self):
        # The inherited per-call stop ranges accumulate duplicates. Preserve the
        # exact set of tuples, rather than scanning thousands of identical ones.
        ranges = sorted(set(self.e.allowed))
        self.allowed_range_compaction['passes'] += 1
        self.allowed_range_compaction['duplicateTuplesRemoved'] += len(self.e.allowed)-len(ranges)
        self.e.allowed[:] = ranges

    def dictionary_constructor(self):
        e = self.e
        pointer, requested = e.reg('rcx'), e.reg('edx')
        typ = self.type_records[e.read(pointer)]
        if typ.get('typeDefinition') != 1222 or typ['arguments'][0]['kind'] != 'uint32':
            raise ValueError('Only exact typed UInt32 Dictionary storage is declared')
        capacity = requested if requested else self.dictionary_capacity
        if not 1 <= capacity <= 4096:
            raise ValueError('Dictionary capacity outside the source-bound storage fixture')
        e.write(pointer + 0x18, self.array(capacity, 24))
        e.write(pointer + 0x20, self.array(capacity, 4))
        e.write(pointer + 0x3c, 0xffffffff, 'I')
        self.storage_events.append(dict(kind='dictionary-storage', requested=requested, allocated=capacity, type=typ))
        return 0

    def list_storage(self):
        e = self.e
        pointer, count = e.reg('rcx'), e.reg('edx')
        typ = self.type_records[e.read(pointer)]
        if typ.get('typeDefinition') != 1255 or not 1 <= count <= 4096:
            raise ValueError('Only source-bound typed List storage is declared')
        array = self.array(count, 8)
        e.write(array, self.bind_type(dict(kind='szarray', elementType=typ['arguments'][0], rank=1)))
        e.write(pointer + 0x10, array)
        self.storage_events.append(dict(kind='list-storage', count=count, type=typ))
        return 0

    def snapshot_value(self, pointer, typ, depth):
        if typ.get('typeDefinition') in (52802, 17208):
            return [dict(field=f['name'], fieldIndex=f['index'], offset=hex(f['offset'] - 16),
                value=self.snapshot_value(pointer + f['offset'] - 16, f['type'], depth + 1))
                for f in self.records[typ['typeDefinition']]['fields'] if f['offsetKind'] == 'instance']
        return super().snapshot_value(pointer, typ, depth)

    def call_whole(self, entry, row_count, *, total_limit):
        self.compact_allowed_ranges()
        e = self.e
        e.write(STACK + 0x100008, RETURN)
        e.uc.reg_write(e.R.UC_X86_REG_RSP, STACK + 0x100008)
        e.uc.reg_write(e.R.UC_X86_REG_RCX, self.reader)
        stops = {RETURN, 0x1404c2be0, 0x1404c2650, 0x1404c2c70, 0x1404c2770, 0x1404bbd90}
        e.allowed.extend((x, x + 1) for x in stops)
        segments, total, next_row = [], 0, 128
        segment_entry = entry
        while True:
            e.calls, e.failure, e.steps = [], None, 0
            counts, reached = Counter(), []
            before = self.execution_state()
            if segments and before != segments[-1]['after']:
                raise ValueError('Registers or modeled memory changed between whole segments')
            def observe(address):
                if address == self.row_entry and self.reader and e.reg('rdx') == self.reader:
                    if len(self.spans) == next_row:
                        reached.append(address)
                        e.uc.emu_stop()
                        return
                    self.pending_rows[e.read(e.reg('rsp'))] = (e.reg('rcx'), e.read(self.reader + 0x1c, 'I'))
                if address in self.pending_rows:
                    pointer, begin = self.pending_rows.pop(address)
                    self.spans.append(dict(pointer=hex(pointer), begin=begin, end=e.read(self.reader + 0x1c, 'I')))
                if address in self.extents or address in e.ends:
                    self.bind_function(address)
                    self.call_order.append(dict(entry=hex(address), rcx=hex(e.reg('rcx')), rdx=hex(e.reg('rdx'))))
                counts[address] += 1
                if address in stops:
                    reached.append(address)
                    e.uc.emu_stop()
            limit = min(2_000_000, total_limit - total)
            if limit <= 0:
                raise ValueError('Source-bound whole cumulative instruction budget exhausted')
            e.before_instruction = observe
            try:
                e.uc.emu_start(segment_entry, END, count=limit)
            finally:
                e.before_instruction = None
            total += e.steps
            segment = dict(entry=hex(segment_entry), instructions=e.steps, totalInstructions=total,
                before=before, after=self.execution_state(), completedRows=len(self.spans),
                stop=hex(reached[0]) if len(reached) == 1 else None,
                executedAddresses={hex(k):v for k,v in sorted(counts.items())},
                models=[r['address'] for r in e.calls],instructionCountsAreHookVisits=True,
                unexecutedStopHookIncluded=len(reached)==1)
            segments.append(segment)
            self.last_whole_segments = segments
            if e.failure or len(reached) != 1:
                raise ValueError(e.failure or 'Whole segment did not reach its exact source-bound stop')
            if reached[0] in stops:
                return dict(entry=hex(entry), stop=hex(reached[0]), instructions=total,
                    segments=segments, rowCount=row_count, inputResetBetweenSegments=False,
                    registersStackAndHeapPreserved=True,instructionCountsAreHookVisits=True)
            if len(self.spans) != next_row or next_row >= row_count:
                raise ValueError('Unexpected whole continuation row boundary')
            next_row += 128
            segment_entry = e.reg('rip')

    def execution_state(self):
        registers = ('rip','rsp','rbp','rcx','rdx','r8','r9','rax','rbx','rsi','rdi','r10','r11','r12','r13','r14','r15',
            'eflags','mxcsr',*(f'xmm{i}' for i in range(16)))
        return dict(registers={k:hex(self.e.reg(k)) for k in registers},
            memorySHA256={label:sha(bytes(self.e.uc.mem_read(address,size))) for label,address,size in
                [('stack',STACK,0x200000),('heap',HEAP,self.e.heap_cursor-HEAP),('globals',GLOBAL,0x600000)]},
            heapCursor=hex(self.e.heap_cursor), cursor=self.e.read(self.reader + 0x1c, 'I'), completedRows=len(self.spans))


def type_signature(typ):
    return (typ['kind'], typ.get('typeDefinition'), tuple(type_signature(x) for x in typ.get('arguments', [])))


def bind_generics(rt, entry):
    e = rt.e
    rt.bind_function(entry)
    refs = {int(x['target'], 16) for x in rt.functions[hex(entry)]['references'] if int(x['target'], 16) not in rt.cells}
    uses = method_usages(rt.binary, rt.metadata, rt.startup, rt.records, refs)
    for use in uses:
        classes = [p for p, typ in rt.type_records.items() if typ.get('typeDefinition') == use['ownerType']
                   and tuple(type_signature(x) for x in typ.get('arguments', [])) == tuple(type_signature(x) for x in use['classArguments'])]
        if len(classes) != 1:
            raise ValueError('Exact generic context not unique')
        klass = classes[0]
        if not e.read(klass + 0x30):
            context = e.alloc(256)
            e.write(klass + 0x30, context)
            constructor, insert = e.alloc(64), e.alloc(64)
            e.write(constructor, klass)
            e.write(constructor + 8, e.model_stub(rt.dictionary_constructor))
            e.write(context + 0x10, constructor)
            e.write(insert, klass)
            e.write(insert + 8, 0x1578b6a20)
            e.write(context + 0x88, insert)
        method = e.alloc(64)
        e.write(method, klass)
        e.write(int(use['cell'], 16), method)
        rt.method_cells[int(use['cell'], 16)] = use
    return uses


def parse_source(rt, name):
    spec = SPECS[name]
    blob = (RAW / (name + '.cached-m_bytes.bin')).read_bytes()
    word = struct.unpack_from('<I', blob)[0]
    count = ((word + 0x3151fd) if name == 'WeaponPromote' else
             (word ^ 0xebf536d6) if name == 'AvatarPromote' else (word + 0xd98492ef)) & 0xffffffff
    rt.reader, rt.spans, rt.pending_rows, rt.row_entry = 0, [], {}, spec['parser']
    uses = bind_generics(rt, spec['whole'])
    rt.reader = rt.e.alloc(64)
    data = rt.array(len(blob), 1)
    rt.e.uc.mem_write(data + 0x20, blob)
    rt.e.write(rt.reader + 0x10, data)
    readout_begin = rt.call_instruction_total
    limit = 6_000_000 if name == 'WeaponPromote' else 2_000_000
    trace = rt.call_whole(spec['whole'], count, total_limit=limit)
    if trace['stop'] != hex(RETURN) or rt.e.read(rt.reader + 0x1c, 'I') != len(blob):
        raise ValueError('Original whole parser did not reach exact EOF')
    spans = {x['pointer']: x for x in rt.spans}
    if len(rt.spans)!=count or rt.spans[0]['begin']!=4 or rt.spans[-1]['end']!=len(blob) or any(
        a['end']!=b['begin'] for a,b in zip(rt.spans,rt.spans[1:])):
        raise ValueError('Original whole row spans do not cover the exact payload after its header')
    if 'listOffset' in spec:
        collection = rt.e.read(GLOBAL + spec['listOffset'])
        array = rt.e.read(collection + 0x10)
        actual = rt.e.read(collection + 0x18, 'I')
        pointers = [rt.e.read(array + 0x20 + i * 8) for i in range(actual)]
    else:
        collection = rt.e.read(GLOBAL + spec['cacheOffset'])
        entries = rt.e.read(collection + 0x18)
        actual = rt.e.read(collection + 0x38, 'I')
        pointers = [rt.e.read(entries + 0x30 + i * 24) for i in range(actual)]
        cache_keys = {pointer:rt.e.read(entries + 0x28 + i * 24,'I') for i,pointer in enumerate(pointers)}
    if actual != count:
        raise ValueError('Whole row count differs from source framing')
    rows = []
    for pointer in pointers:
        span = spans[hex(pointer)]
        record = dict(pointer=hex(pointer), source=dict(begin=span['begin'], end=span['end'],
            sha256=sha(blob[span['begin']:span['end']])))
        if 'groupOffset' in spec:
            record.update(group=rt.decode(pointer + spec['groupOffset']), phase=rt.decode(pointer + spec['phaseOffset']))
            if record['group'] == spec['group']:
                record['fields'] = rt.snapshot_object(pointer, spec['row'], 0)
        else:
            key = rt.decode(pointer + spec['keyOffset'])
            if key!=cache_keys[pointer]:
                raise ValueError('Native row level differs from its inserted Dictionary key')
            record.update(key=key,nativeCacheKey=cache_keys[pointer],fields=rt.snapshot_object(pointer, spec['row'], 0))
        rows.append(record)
    readout = rt.call_instruction_total - readout_begin
    if trace['instructions'] + readout > limit:
        raise ValueError('Whole parser plus actual native row readout exceeds the table budget')
    return dict(name=name,count=count,payloadBytes=len(blob),payloadSHA256=sha(blob),cursor=len(blob),
        rows=rows,trace=trace,wholeMethodUsages=uses,rowReadoutInstructionHookVisits=readout,
        wholePlusReadoutInstructionHookVisits=trace['instructions']+readout,cumulativeLimit=limit)


def selected_index(rt, name, table):
    spec, e = SPECS[name], rt.e
    selected = [x for x in table['rows'] if x['group'] == spec['group']]
    original = e.read(GLOBAL + spec['listOffset'])
    projection = e.alloc(0x20)
    e.write(projection, e.read(original))
    array = rt.array(len(selected), 8)
    e.write(array, e.read(e.read(original + 0x10)))
    for i, row in enumerate(selected):
        e.write(array + 0x20 + i * 8, int(row['pointer'], 16))
    e.write(projection + 0x10, array)
    e.write(projection + 0x18, len(selected), 'I')
    e.write(GLOBAL + spec['listOffset'], projection)
    e.write(GLOBAL + spec['cacheOffset'], 0)
    rt.dictionary_capacity = len(selected)
    uses = bind_generics(rt, spec['builder'])
    trace = rt.call(spec['builder'])
    e.write(GLOBAL + spec['listOffset'], original)
    queries = []
    rt.bind_function(spec['getter'])
    for row in selected:
        args = dict(rcx=row['group'], rdx=row['phase']) if name == 'WeaponPromote' else dict(rcx=row['phase'], rdx=row['group'])
        query = rt.call(spec['getter'], **args)
        if e.reg('rax') != int(row['pointer'], 16) or query['stop'] != hex(RETURN):
            raise ValueError('Native grouped query differs from actual selected row')
        queries.append(dict(group=row['group'], phase=row['phase'], samePointer=True, trace=query))
    return dict(group=spec['group'], selectedRows=len(selected), originalListRestored=True,
                builder=trace, queries=queries, methodUsages=uses, projectedIndexOnly=True)


def verify_inputs(client):
    pins, sources = equip.verify_inputs(client)
    verified = {path:card.base.read_verified(path,digest) for path,digest in PINS.items()}
    pins.update({str(path.relative_to(ROOT)):digest for path,digest in PINS.items()})
    manifest = json.loads(verified[RAW / 'java-manifest.private.json'])
    for name, identity in manifest.items():
        for path in (ROOT / 'src/main/java/emu/grasscutter' / name, RAW / 'java-epoch0' / name):
            data = card.base.read_verified(path,identity['sha256'])
            pins[str(path.relative_to(ROOT))] = identity['sha256']
        sources[name] = data.decode('utf8')
    bindings = json.loads(verified[RAW / 'cached-source-bindings.private.json'])
    locator = json.loads(equip.LOCATOR.read_bytes())
    carrier = card.base.read_verified(equip.SOURCE / '25539185.serialized.bin',locator['payload']['outputSHA256'])
    for row in bindings:
        start, length = row['serializedOffset'], row['bytes']
        blob = verified[RAW / (row['name'] + '.cached-m_bytes.bin')]
        if carrier[start:start+length] != blob or row['sha256'] != sha(blob):
            raise ValueError('Cached resource differs from its fixed carrier bytes')
    return pins, sources, bindings


def project_promote_row(row, name, enums, java_fight, item_keys):
    fields = {f['offset']:f['value'] for f in row['fields']}
    spec = SPECS[name]
    group, phase = fields[hex(spec['groupOffset'])], fields[hex(spec['phaseOffset'])]
    if group != row['group'] or phase != row['phase'] or type(phase) is not int or not 0 <= phase <= 255:
        raise ValueError('Actual promote composite key is inconsistent or unrepresentable')
    group = equip.java_id(group)
    key = equip.java_id((group << 8) + phase)
    if type(fields['0x10']) is not list or type(fields['0x18']) is not list:
        raise ValueError('Java promote onLoad requires nonnull actual arrays')
    props = []
    for prop in fields['0x10']:
        values = {f['offset']:f['value'] for f in prop}
        value = values['0x4']
        bits = struct.unpack('<I',struct.pack('<f',value['float32']))[0]
        if bits != int(value['bits'],16) or bits & 0x7f800000 == 0x7f800000:
            raise ValueError('Promote float32 is nonfinite or differs from its recorded bits')
        props.append(dict(propType=equip.exact_enum(values['0x0']['value'],32419,enums,java_fight),value=value['float32']))
    costs = []
    for item in fields['0x18']:
        values = {f['offset']:f['value'] for f in item}
        item_id, count = values['0x0'], values['0x4']
        if type(item_id) is not int or type(count) is not int or not 0 <= item_id <= 0x7fffffff or not 0 <= count <= 0x7fffffff:
            raise ValueError('Actual material pair cannot be represented by Java int')
        if item_id and item_id not in item_keys:
            raise ValueError('Missing positive material Item resource reference')
        costs.append(dict(id=item_id,count=count))
    group_field = 'weaponPromoteId' if name == 'WeaponPromote' else 'avatarPromoteId'
    omitted = ['coinCost' if name == 'WeaponPromote' else 'scoinCost','unlockMaxLevel','requiredPlayerLevel']
    return dict(table=name,key=key,source=row['source'],
        javaFields={group_field:group,'promoteLevel':phase,'costItems':costs,'addProps':props},
        completeBirthInputRow=phase == 0, allDeclaredGameFieldsMapped=False,
        omittedNotAssertedAsSourceDefaults=omitted,
        onLoadSourceModel=dict(costItems=[x for x in costs if x['id']],
            addProps=[x for x in props if x['value'] != 0.0],actualJavaExecuted=False))


def project_curves(table, enums, required):
    if [r['key'] for r in table['rows']] != list(range(1,101)):
        raise ValueError('AvatarCurve levels must be contiguous actual levels 1..100')
    rows = []
    for row in table['rows']:
        fields = {f['offset']:f['value'] for f in row['fields']}
        if fields['0x18'] != row['key']:
            raise ValueError('AvatarCurve row key differs from its native cache key')
        infos, names = [], set()
        for info in fields['0x10']:
            values = {f['offset']:f['value'] for f in info}
            name = equip.exact_enum(values['0x0']['value'],54063,enums)
            arith = equip.exact_enum(values['0x4']['value'],58570,enums)
            if name in names:
                raise ValueError('Duplicate curve: native first match differs from Java last write')
            names.add(name)
            if values['0x4']['value'] != 2:
                raise ValueError('Java AvatarData multiplication requires actual MULTI arithmetic')
            bits = struct.unpack('<I',struct.pack('<f',values['0x8']['float32']))[0]
            if bits != int(values['0x8']['bits'],16) or bits & 0x7f800000 == 0x7f800000:
                raise ValueError('Curve float32 is nonfinite or differs from its recorded bits')
            infos.append(dict(type=name,arith=arith,value=values['0x8']['float32']))
        if not required <= names:
            raise ValueError('Missing positive Avatar growth curve reference')
        rows.append(dict(table='AvatarCurve',key=row['key'],source=row['source'],
            javaFields=dict(level=row['key'],curveInfos=infos),completeDeclaredJavaInputRow=True))
    return rows


def native_birth_consumers(rt, tables):
    print(json.dumps(dict(stage='reused-native-parser-start',table='Weapon')),flush=True)
    equip.install_table(rt,'Weapon')
    weapon = rt.parse_table((equip.SOURCE / 'Weapon.native-m_bytes.bin').read_bytes())
    print(json.dumps(dict(stage='reused-native-parser-eof',table='Weapon',rows=weapon['count'])),flush=True)
    print(json.dumps(dict(stage='reused-native-parser-start',table='Avatar')),flush=True)
    equip.install_table(rt,'Avatar')
    avatar = rt.parse_table((equip.SOURCE / 'Avatar.native-m_bytes.bin').read_bytes())
    print(json.dumps(dict(stage='reused-native-parser-eof',table='Avatar',rows=avatar['count'])),flush=True)
    print(json.dumps(dict(stage='reused-native-parser-start',table='WeaponCurve')),flush=True)
    weapon_curves = equip.install_curve(rt)
    print(json.dumps(dict(stage='reused-native-parser-eof',table='WeaponCurve',rows=weapon_curves['count'])),flush=True)
    for entry in (0x150758430,0x1507587b0,0x15184adf0,0x1507f2850):
        rt.bind_function(entry)
    e = rt.e
    item = next(x for x in weapon['rows'] if x['key'] == 11101)
    actual_weapon = int(item['pointer'],16)
    model, proto = equip.object_storage(rt,29353),equip.object_storage(rt,64947)
    e.write(model + 0x30,actual_weapon)
    e.write(model + 0x40,proto)
    fields = {x['offset']:x['value'] for x in next(x for x in weapon['selected'] if x['key'] == 11101)['fields'] if x['offset']=='0x20'}
    backing = {x['offset']:x['value'] for x in fields['0x20']}
    stats = []
    for index,level,phase in ((0,1,0),(1,1,0),(0,20,0),(0,20,1),(0,40,2),(0,70,4)):
        e.write(proto + 0x30,level,'I')
        e.write(proto + 0x2c,phase,'I')
        trace = rt.call(0x150758430,rcx=model,rdx=index)
        if trace['stop'] != hex(RETURN):
            raise ValueError('Complete native weapon stat function did not return')
        prop = {x['offset']:x['value'] for x in backing['0x68'][index]}
        fight, curve = prop['0x18']['value'],prop['0x10']['value']
        curve_row = next(x for x in weapon_curves['rows'] if x['key'] == level)
        curve_infos = next(x['value'] for x in curve_row['fields'] if x['offset']=='0x10')
        factor = next({x['offset']:x['value'] for x in v}['0x8']['float32'] for v in curve_infos
            if {x['offset']:x['value'] for x in v}['0x0']['value']==curve)
        promote = next(x for x in tables['WeaponPromote']['rows'] if x.get('group')==11101 and x['phase']==phase)
        promote_props = next(x['value'] for x in promote['fields'] if x['offset']=='0x10')
        matching = [v for v in promote_props if {x['offset']:x['value'] for x in v}['0x0']['value']==fight]
        add = {x['offset']:x['value'] for x in matching[0]}['0x4']['float32'] if matching else 0.0
        multiplied = struct.unpack('<f',struct.pack('<f',prop['0x14']['float32']*factor))[0]
        expected = struct.unpack('<I',struct.pack('<f',multiplied+add))[0]
        actual = e.reg('xmm0') & 0xffffffff
        if actual != expected:
            raise ValueError('Complete native weapon stat differs from independent float32 oracle')
        stats.append(dict(itemId=11101,index=index,level=level,phase=phase,fightProperty=fight,
            baseBits=prop['0x14']['bits'],factor=factor,promoteValue=add,
            resultBits=f'{actual:08x}',independentOracleBits=f'{expected:08x}',trace=trace,
            actualPromoteRowSource=promote['source'],completeNativeFunction=True))
    print(json.dumps(dict(stage='complete-native-weapon-stats',cases=len(stats))),flush=True)
    groups, base_fragments, grow, raw_floats = [], [], [], []
    curve_rows = {x['key']:x for x in tables['AvatarCurve']['rows']}
    for avatar_id in (10000005,10000007):
        row = next(x for x in avatar['rows'] if x['key']==avatar_id)
        pointer = int(row['pointer'],16)
        trace = rt.call(0x15184ae18,stops=(0x15184ae6f,),rax=avatar_id)
        if trace['stop']!='0x15184ae6f' or e.reg('esi')!=12:
            raise ValueError('Original Avatar promote producer did not select actual group12')
        groups.append(dict(avatarId=avatar_id,avatarPromoteId=12,source=row['source'],trace=trace,
            exactField='58272+178',fullProducerExecuted=False))
        for start,stop,offset,prop_id in ((0x1507f34e0,0x1507f34fc,0x64,2000),
            (0x1507f39ba,0x1507f39d9,0x9c,4),(0x1507f3b5a,0x1507f3b76,0x5c,7)):
            slot = e.alloc(64)
            trace = rt.call(start,stops=(stop,),rdi=pointer,rbx=slot,rbp=STACK+0xff000)
            bits = e.reg('xmm0') & 0xffffffff
            if trace['stop']!=hex(stop) or e.read(slot+0x24,'I')!=prop_id:
                raise ValueError('Avatar property fragment did not preserve exact field/enum consumer')
            base_fragments.append(dict(avatarId=avatar_id,offset=hex(offset),targetProperty=prop_id,
                targetSymbol=equip.exact_enum(prop_id,32419,rt.enum_symbols),bits=f'{bits:08x}',trace=trace,
                completeNativeFunction=False,source=row['source']))
        selected = next(x for x in avatar['selected'] if x['key']==avatar_id)
        fields = {x['offset']:x['value'] for x in selected['fields']}
        for offset in ('0x64','0x9c','0x5c','0x98','0xa4'):
            raw_floats.append(dict(avatarId=avatar_id,offset=offset,value=fields[offset],
                hpBaseCriticalCriticalHurtAliasesApproved=False))
        for binding in fields['0x48']:
            values = {x['offset']:x['value'] for x in binding}
            prop,curve = values['0x0']['value'],values['0x4']['value']
            source_offset = {1:'0x64',4:'0x9c',7:'0x5c'}[prop]
            base_bits = int(fields[source_offset]['bits'],16)
            base = fields[source_offset]['float32']
            for level in (1,20,100):
                actual_curve = curve_rows[level]
                infos = next(x['value'] for x in actual_curve['fields'] if x['offset']=='0x10')
                factor = next({x['offset']:x['value'] for x in info}['0x8']['float32'] for info in infos
                    if {x['offset']:x['value'] for x in info}['0x0']['value']==curve)
                e.uc.reg_write(e.R.UC_X86_REG_XMM0,base_bits)
                trace = rt.call(0x150757a00,rdx=curve,r8=int(actual_curve['pointer'],16))
                expected = struct.unpack('<I',struct.pack('<f',base*factor))[0]
                if trace['stop']!=hex(RETURN) or e.reg('xmm0') & 0xffffffff != expected:
                    raise ValueError('Original shared curve arithmetic differs from Avatar row/float32 inputs')
                grow.append(dict(avatarId=avatar_id,property=prop,curve=curve,level=level,sourceOffset=source_offset,
                    resultBits=f'{expected:08x}',trace=trace,curveSource=actual_curve['source'],
                    originalSharedHelper=True,completeAvatarConsumer=False,
                    baseOffsetSelectedByCallerFixture=True,hpAliasApproved=False))
    missing = []
    for name in ('WeaponPromote','AvatarPromote'):
        spec = SPECS[name]
        for group,phase in ((spec['group'],255),(0xffffffff,0)):
            args = dict(rcx=group,rdx=phase) if name=='WeaponPromote' else dict(rcx=phase,rdx=group)
            trace = rt.call(spec['getter'],**args)
            if trace['stop']!=hex(RETURN) or e.reg('rax')!=0:
                raise ValueError('Absent promote query did not return actual null')
            missing.append(dict(table=name,group=group,phase=phase,trace=trace,returnedNull=True))
    old_guid = json.loads(EQUIPMENT_REPORT.read_bytes())['consumers']['initialWeapon']
    print(json.dumps(dict(stage='bounded-avatar-and-absence-consumers',avatars=len(groups),
        sharedCurveCases=len(grow),missingQueries=len(missing))),flush=True)
    return dict(weaponStats=stats,avatarPromoteGroups=groups,avatarBaseFragments=base_fragments,
        avatarRawFloats=raw_floats,avatarCurveArithmetic=grow,missingQueries=missing,
        inheritedNativeUint64=[{k:x[k] for k in ('avatarId','syntheticGuidDecimal','syntheticGuidBits','guidPreserved')} for x in old_guid],
        reusedTableParsers=[{k:t[k] for k in ('name','count','cursor','payloadSHA256','trace')} for t in (weapon,avatar,weapon_curves)],
        completeAvatarConsumer=False,javaOnLoadExecuted=False)


def project(tables, enums, sources, consumers):
    java_fight = {name:int(value) for name,value in re.findall(r'^    (FIGHT_PROP_\w+)\((\d+)\)',sources['game/props/FightProperty.java'],re.M)}
    required = {equip.exact_enum(x,54063,enums) for x in (22,32)}
    curves = project_curves(tables['AvatarCurve'],enums,required)
    promotes = []
    for name in ('WeaponPromote','AvatarPromote'):
        row = next(x for x in tables[name]['rows'] if x.get('group')==SPECS[name]['group'] and x['phase']==0)
        promotes.append(project_promote_row(row,name,enums,java_fight,set()))
    avatar_fragments = []
    for group in consumers['avatarPromoteGroups']:
        props = {x['targetProperty']:x for x in consumers['avatarBaseFragments'] if x['avatarId']==group['avatarId']}
        base = {name:struct.unpack('<f',struct.pack('<I',int(props[prop]['bits'],16)))[0]
            for name,prop in (('attackBase',4),('defenseBase',7))}
        avatar_fragments.append(dict(table='Avatar',key=group['avatarId'],source=group['source'],
            javaFields=dict(id=group['avatarId'],avatarPromoteId=group['avatarPromoteId'],**base),
            fragmentOnly=True,floatBits={name:props[prop]['bits'] for name,prop in (('attackBase',4),('defenseBase',7))}))
    return dict(completeBirthPromoteInputRows=promotes,completeAvatarCurveInputRows=curves,avatarFieldFragments=avatar_fragments,
        optionalUpgradePromoteRowsExported=False,
        birthScopeOnly=True,allDeclaredGameFieldsMapped=False,actualJavaLoaded=False,
        unapprovedAvatarFields=['hpBase','critical','criticalHurt','propGrowCurves'],
        requiredAdditionalReferences=['same-source complete Avatar/Item/ability/gadget rows and Java loader outcomes'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('Fresh report in the card private raw directory required')
    pins, sources, bindings = verify_inputs(args.client)
    binary, metadata, startup = [(args.client / name).read_bytes() for name in list(card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((equip.RAW / 'metadata.private.pkl').read_bytes())
    codec = card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = PromoteRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    result = dict(task='E-01-PROMOTE-ENTRY', sourceSHA256=sha(Path(__file__).read_bytes()), tables=[], indexes=[])
    try:
        for name in SPECS:
            table = parse_source(rt, name)
            result['tables'].append(table)
            print(json.dumps(dict(stage='original-whole-eof',table=name,rows=table['count'],
                instructionHookVisits=table['trace']['instructions'])),flush=True)
            if 'groupOffset' in SPECS[name]:
                begin = rt.call_instruction_total
                result['indexes'].append(selected_index(rt, name, table))
                table['selectedIndexInstructionHookVisits'] = rt.call_instruction_total - begin
                if table['wholePlusReadoutInstructionHookVisits'] + table['selectedIndexInstructionHookVisits'] > table['cumulativeLimit']:
                    raise ValueError('Whole/readout/selected native index exceeds the table cumulative budget')
        by_name = {x['name']:x for x in result['tables']}
        result['consumers'] = native_birth_consumers(rt,by_name)
        result['javaProjection'] = project(by_name,rt.enum_symbols,sources,result['consumers'])
        result.update(sourcePins=pins,resourceBindings=bindings,nativeSources=rt.functions,
            storageModels=rt.storage_events,enumSymbols=list(rt.enum_symbols.values()),
            nativeCallTotals=rt.call_totals,nativeCallInstructionHookVisits=rt.call_instruction_total,
            allowedRangeCompaction=rt.allowed_range_compaction,
            nativeConfigTypedFixture=rt.config_proof,completeNativeConfigInitialization=False,
            javaResourcesLoaded=False,resourceReady=False,activeSelectedRevision=None,
            activeRevisionVerified=False,completeAvatarHandler=False,d02Complete=False,
            testSourceSHA256=sha((ROOT/'tools/test_client72_d02_promote_resource.py').read_bytes()),
            modelScope=[
                'Only exact typed managed storage/metadata/UTF8/memcpy and fixed scalar-codec initialization are modeled. Parsers, Dictionary insertion/query, primitive decoding and property arithmetic execute original code.',
                'The inherited host allow-list is deduplicated as sorted(set(existing tuples)); its exact address coverage, models, registers, native memory and budgets are unchanged.',
                'Promote full source lists are parsed. Their selected group pointers are projected into a typed List for the original grouped index builder; source Lists are restored. Complete whole-group index construction is not claimed.',
                'Dictionary requested capacity zero is preallocated to the source-bounded selected group count; allocation/growth timing differs from real CLR constructor. No resource values or lookups are supplied by a model.',
                'Whole parser segments continue the same Unicorn call and preserve registers/stack/heap/input. Reported instruction counts include the hook on each unexecuted stop instruction and modeled library entries.',
                'Original whole weapon stat function now reaches its actual return with real group11101 Promote rows. Typed weapon model/level proto are caller fixtures; this is not the entire equipment or Avatar lifecycle.',
                'Avatar promote source and three field consumers execute exact slices with actual Avatar cache/rows. Shared curve helper receives base-offset choices selected by this caller fixture, including unapproved HP association. This proves helper arithmetic on real rows, not a complete Avatar growth caller or recalculation.',
                'Logical parser zero values are observed. Individual serialized optional-presence bits are not mapped and explicit zero tokens are not asserted.',
                'Uint64 preservation is inherited from the fixed equipment third report and explicitly labeled, not counted as a new native whole-producer execution.',
            ],
            scope='Complete same-source birth Promote input rows and AvatarCurve inputs; other Avatar/Item fields remain incomplete. No Java loadAll/manager/client acceptance.')
    except Exception as error:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(error=str(error), partial=result,
            nativeFailure=rt.e.failure, registers={k:hex(rt.e.reg(k)) for k in ['rip','rcx','rdx','r8','r9','rax']},
            wholeSegments=getattr(rt,'last_whole_segments',[]), sources=rt.functions, calls=rt.call_order[-80:]),indent=2)+'\n')
        raise
    finally:
        rt.e.close()
    equip.verify_client(args.client)
    result['fiveInputsRecheckedAfterNative'] = True
    args.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
    print(json.dumps({'tables':[(t['name'],t['count']) for t in result['tables']], 'resourceReady':False}))


if __name__ == '__main__':
    main()
