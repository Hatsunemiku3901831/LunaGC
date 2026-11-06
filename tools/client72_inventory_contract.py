"""Native CN72 store reset and bounded Material/Item consumption evidence.

The notification and each item's application are separate declared fragments.
List delivery is a boundary; category/fanout/UI tails do not execute. No Asta
schema, real resource dataset, client process or live runtime is an input.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, CODE_REGISTRATION, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from .trace_client72_inventory_graph import InventoryGraph72
    from .client72_session_runtime import fragment
    from .client72_graph_fixtures import scalar, message
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, CODE_REGISTRATION, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from trace_client72_inventory_graph import InventoryGraph72
    from client72_session_runtime import fragment
    from client72_graph_fixtures import scalar, message

RETURN = END + 0x100
ROOT, ITEM, MATERIAL, CMD = 68537, 65333, 63442, 20892
DISPATCH, WRAPPER, PACK, BASE, RESET = 0x153CBB460, 0x153CAF190, 0x150809180, 0x14E43BE70, 0x14E3DC3D0
APPLY, FACTORY, CONVERT = 0x14E43A630, 0x150ED35E0, 0x148FED420
RESOURCE, GUID_INSERT, GUID_FIND = 0x14A1D8440, 0x14E43AF70, 0x156F6F510
CATEGORY = 0x14E43B0E0
RAW_SHA = {
    'player-store-outer-observations.json': '2c14b416e5e18d868bde7d645939f6ea9c2a936761967e555e92538c2d11e20f',
    'inventory-container-observations.json': '352f094fec870074abb70207af317870525b025c9b68805500c6410347feb72d',
    'inventory-graph-observations.json': 'ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18'}
BINDINGS = [(ROOT, 2, 'item_list'), (ROOT, 14, 'store_type'), (ITEM, 1, 'item_id'),
            (ITEM, 2, 'guid'), (ITEM, 5, 'material'), (MATERIAL, 1, 'count')]
# warehouse offset -> bucket offset, entry offset, count offset, version offset
LAYOUTS = {0x68: (0x10, 0x28, 0x40, 0x38), 0x38: (0x20, 0x18, 0x38, 0x40),
    **{off: (0x18, 0x28, 0x38, 0x40) for off in (0x48, 0x70, 0x20, 0x18, 0x60)},
    0x28: (0x20, 0x10, 0x44, 0x3C), 0x50: (0x20, 0x28, 0x38, 0x40),
    **{off: (0x20, 0x28, 0x38, 0x40) for off in (0xC8, 0x88, 0xB8)},
    **{off: (0x20, 0x18, 0x38, 0x40) for off in (0x90, 0xC0)}}


def material_item(item_id, guid, count):
    if any(type(v) is not int or not 0 <= v < (1 << bits) for v, bits in ((item_id, 32), (guid, 64), (count, 32))):
        raise ValueError('Item ID/count are uint32 and GUID is uint64')
    return scalar(1, item_id) + scalar(2, guid) + message(5, scalar(1, count) if count else b'')


def store_wire(items, store_type):
    if type(store_type) is not int or not 0 <= store_type < (1 << 32): raise ValueError('Store type requires uint32')
    return b''.join(message(2, item) for item in items) + scalar(14, store_type)


def validate_result(expected, actual):
    if any(actual.get(key) != value for key, value in expected.items()):
        raise ValueError('Native item result differs: ' + str(actual) + ' expected ' + str(expected))


def fixture_cases():
    specs = [('material-nonzero', [(1001, 9007199254740993, 42)], 1),
        ('empty-pack', [], 1), ('empty-depot', [], 2),
        ('two-materials', [(1001, 71, 42), (1002, 72, 43)], 1),
        ('high-guid-collision', [(1001, 0x100000001, 42), (1001, 0x200000002, 43)], 1),
        ('same-guid-overwrite', [(1001, 71, 42), (1002, 71, 43)], 1),
        ('zero-count', [(1001, 71, 0)], 1),
        ('upper-integers', [(0xFFFFFFFF, 0xFFFFFFFFFFFFFFFF, 0xFFFFFFFF)], 2),
        ('signed-delta-boundary', [(1001, 71, 0x80000000)], 1),
        ('same-guid-negative-delta', [(1001, 71, 42), (1001, 71, 2)], 1),
        ('same-guid-zero-removes', [(1001, 71, 42), (1001, 71, 0)], 1),
        ('zero-guid', [(1001, 0, 1)], 1),
        ('store-none-fallback', [(1001, 71, 42)], 0),
        ('store-unknown-fallback', [(1001, 71, 42)], 0xFFFFFFFF)]
    result = [dict(name=name, items=items, storeType=store, wire=store_wire([material_item(*v) for v in items], store))
              for name, items, store in specs]
    item = scalar(1, 1001) + scalar(2, 71)
    result.extend([
        dict(name='same-material-explicit-zero', items=[(1001, 71, 0)], storeType=1,
             wire=store_wire([item + message(5, scalar(1, 42)) + message(5, scalar(1, 0))], 1)),
        dict(name='oneof-seven-then-material', items=[(1001, 71, 42)], storeType=1,
             wire=store_wire([item + message(7, scalar(1, 99)) + message(5, scalar(1, 42))], 1)),
        dict(name='nested-detail-retained-unknown', items=[(1001, 71, 42)], storeType=1,
             wire=store_wire([item + message(5, scalar(1, 42) + message(2, message(3, scalar(1, 17))))], 1)),
        dict(name='resource-category-is-not-wire-tag', items=[(1001, 71, 42)], storeType=1,
             category=7, wire=store_wire([material_item(1001, 71, 42)], 1)),
        dict(name='missing-resource-rejected-by-application', items=[(1001, 71, 42)], storeType=1,
             missing=True, wire=store_wire([material_item(1001, 71, 42)], 1))])
    return result


def declarations(binary, metadata, startup, records, directory):
    for name, expected in RAW_SHA.items():
        if hashlib.sha256((directory / name).read_bytes()).hexdigest() != expected:
            raise ValueError('Reviewed inventory evidence changed: ' + name)
    pe = PE(binary); h = pe.offset(HEADER_VA); by = {r['index']: r for r in records}
    resolver = TypeResolver(pe, startup, records, h)
    mb = BODY + (number(binary, h+0xB0) ^ 0x51324A1F)
    pb = BODY + ((number(binary, h+0x174) + 0xAE6BCD68) & 0xFFFFFFFF)
    typed = []
    for owner, address, argument in ((54747, WRAPPER, ROOT), (59658, BASE, ROOT), (43222, PACK, ROOT),
            (59658, APPLY, ITEM), (51337, FACTORY, ITEM), (68895, CONVERT, ITEM), (59658, GUID_INSERT, 24040)):
        method = next(m for m in by[owner]['methods'] if m['registeredAddress'] == hex(address))
        start = parameter_start(method['index'], number(metadata, mb+method['index']*26+4))
        params = [resolver.resolve_index(parameter_words(i, number(metadata, pb+i*8), number(metadata, pb+i*8+4))[1])
                  for i in range(start, start+method['parameterCount'])]
        if not params or params[0].get('typeDefinition') != argument: raise ValueError('Typed inventory consumer changed')
        typed.append(dict(ownerType=owner, entry=hex(address), method=method['name'], parameters=params))
    for owner, slot, entry in ((59658, 25, BASE), (43222, 25, PACK), (51337, 9, 0x150ED3210), (51337, 11, 0x150ED3510)):
        if not any(m['virtualSlot'] == slot and m['registeredAddress'] == hex(entry) for m in by[owner]['methods']):
            raise ValueError('Inventory virtual slot changed')
    if by[75768]['parent']['typeDefinition'] != 51337 or by[63074]['parent']['typeDefinition'] != 34113:
        raise ValueError('Material model/resource parent changed')
    enums = recover_enums(binary, metadata, records, indices=(55345, 34819))['enums']
    wanted = {'StoreNone': 0, 'StorePack': 1, 'StoreDepot': 2, 'ITEM_MATERIAL': 2}
    symbols = {v['symbol']: v['value'] for enum in enums for v in enum['values']}
    if any(symbols.get(k) != v for k, v in wanted.items()): raise ValueError('Store/material enum changed')
    enums = [dict(enum, values=[v for v in enum['values'] if v['symbol'] in wanted]) for enum in enums]
    lr = Literals72(binary, metadata)
    literal = next(dict(row, value=lr.decode(row['index'])) for row in lr.group(lr.global_group) if row['index'] == 70971)
    if literal['cell'] != '0x145b547f8' or literal['value'] != 'Data/_ExcelBinOutput/MaterialExcelConfigData':
        raise ValueError('Material resource literal changed')
    # Same typed resource owner supplies path, loading and lookup. The native
    # loader prefix is separately executed up to its file-I/O boundary.
    for address in (0x14A1D7890, 0x14A1D79C0, RESOURCE):
        if not any(m['registeredAddress'] == hex(address) for m in by[17822]['methods']):
            raise ValueError('Material path/load/query owner changed')
    fields = [f for f in by[17822]['fields'] if f['rawOffset'] == '0x4032480']
    if len(fields) != 1 or fields[0]['type']['arguments'][1].get('typeDefinition') != 63074:
        raise ValueError('Material resource dictionary type changed')
    # The actual oneof factory reads a MethodSpec destination, not a guessed class.
    usage = 760457; key = (usage*0x57C15286+0x26DDEBDA) & 0xFFFFFFFF
    source = ((number(metadata, lr.usages_base+usage*8)+0xABBDCABF)&0xFFFFFFFF)^key^0x2A357D90
    slot = number(metadata, lr.usages_base+usage*8+4)^key^0x1855AA65
    cache = number(binary, pe.offset(USAGE_REGISTRATION)+0x10, 'Q')
    specs = BODY + (number(binary, h+0x170)^0x6347382B)
    method, cls, inst = struct.unpack_from('<iii', metadata, specs+99291*12)
    count, pointer = struct.unpack_from('<QQ', binary, pe.offset(resolver.instances+inst*16))
    args = [resolver.resolve_index(resolver.pointer_index(number(binary, pe.offset(pointer+j*8), 'Q'))) for j in range(count)]
    if source != (6 << 29 | 99291) or cache+slot*8 != 0x145A721F8 or cls != -1 or args[0].get('typeDefinition') != 75768:
        raise ValueError('Material factory metadata binding changed')
    factory_method = next(m for m in by[51337]['methods'] if m['index'] == method)
    eq_method, eq_cls, eq_args = struct.unpack_from('<iii', metadata, specs+360608*12)
    if (eq_method, eq_cls, eq_args) != (10296, 58, -1) or resolver.resolve_index(resolver.arguments[58][0])['kind'] != 'uint64':
        raise ValueError('Native UInt64 equality declaration changed')
    if not any(m['index'] == eq_method and m['name'] == 'Equals' and m['virtualSlot'] == 8 for m in by[1235]['methods']):
        raise ValueError('Native equality comparer slot changed')
    table = BODY+(number(binary, h+0xEC)^0x3CF0720C)
    size = (number(binary, h+0x110)+0xA3D9CC8B)&0xFFFFFFFF
    pointers = number(binary, pe.offset(CODE_REGISTRATION)+0x30, 'Q')
    equality_bindings = []
    for i in range(size//14):
        pointer_index, spec_index = struct.unpack_from('<ii', metadata, table+i*14)
        if spec_index == 360608:
            address = number(binary, pe.offset(pointers+pointer_index*8), 'Q')
            equality_bindings.append(dict(tableIndex=i, pointerIndex=pointer_index, address=hex(address)))
    if len(equality_bindings) != 1 or equality_bindings[0]['address'] != '0x158bb0140':
        raise ValueError('Concrete native UInt64 equality registration changed')
    getter = next(m for m in by[ROOT]['methods'] if m['virtualSlot'] == 22)
    if getter.get('literalU16Candidate') != CMD: raise ValueError('Inventory command getter changed')
    dictionary = next(f for f in by[59658]['fields'] if f['offset'] == 0x68)
    if [a.get('typeDefinition', a['kind']) for a in dictionary['type']['arguments']] != ['uint64', 24040]:
        raise ValueError('GUID dictionary declaration changed')
    return dict(rawEvidenceSHA256=RAW_SHA, typedConsumers=typed, enums=enums, resourceLiteral=literal,
        resourceDictionary=fields[0], guidDictionary=dictionary,
        materialFactory=dict(usageRow=usage, methodSpec=99291, cell='0x145a721f8', methodDefinition=method,
                             method=factory_method['name'], instance=inst, arguments=args),
        commandGetter=getter,
        uint64Equality=dict(methodSpec=360608, methodDefinition=10296, ownerType=1235, classInstance=58, bindings=equality_bindings,
                            entry='0x158bb0140', scope='Concrete UInt64 equality, managed singleton supplied'),
        nativeEntry32SHA256={hex(a): hashlib.sha256(binary[pe.offset(a, 32):][:32]).hexdigest()
            for a in (DISPATCH, WRAPPER, PACK, BASE, RESET, APPLY, FACTORY, CONVERT, RESOURCE, GUID_INSERT, GUID_FIND)})


class InventoryContract72:
    def __init__(self, e, graph, case):
        self.e, self.g, self.case = e, graph, case
        self.clear_calls, self.delivered, self.resource_lookups = [], [], []
        self.widths, self.dictionary_specs, self.enums = {}, {}, {}
        self.allocate_original = e.models[0x1405336C0]
        e.models[0x1405336C0] = self.allocate
        self.clear_original = e.models[0x159C68C70]
        e.models[0x159C68C70] = self.array_clear
        self.resource_lookup_original = e.models[0x1578B66F0]
        e.models[0x1578B66F0] = self.resource_lookup
        for a in (DISPATCH, WRAPPER, PACK, BASE, RESET, APPLY, FACTORY, CONVERT, RESOURCE, GUID_INSERT,
                  0x1497FD010, 0x150ED3010, 0x150ED3210, 0x150ED3510, 0x152EE2F00, 0x14E3D73E0, 0x148446B80, 0x14E3DD4F0,
                  0x14A1D7890, 0x14A1D79C0): e.allow(a)
        for a in (0x15459E4B0, GUID_FIND, 0x156F6F850, 0x156F70100): e.allow_unwind(a)
        e.allowed.append((0x158BB0140, 0x158BB0147))
        # The protobuf-map harness also uses this AOT address as a storage model.
        # Inventory evidence requires the concrete UInt64 dictionary code itself.
        for a in (GUID_FIND, 0x156F6F850, 0x156F70100): e.models.pop(a, None)
        e.uc.mem_map(END, 4096); e.uc.mem_write(RETURN, b'\xc3')
        e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE, e._code, begin=END, end=END+4095))
        self.data_class = e.alloc(1024); e.write(self.data_class+0xC8, 1, 'B')
        e.write(self.data_class+0xF8, 0x150ED3010)
        e.write(self.data_class+0x118, 0x150ED3210); e.write(self.data_class+0x128, 0x150ED3510)
        self.info(0x145A721F8, self.data_class, method=True)
        self.wrapper_class = e.synthetic_type(0x1458B5F70)
        pool, klass = e.alloc(), e.alloc(); e.write(pool, klass); e.write(GLOBAL+0x5D60, pool)
        e.write(klass+0xF0, e.model_stub(self.rent))
        for cell in (0x145899300, 0x14589E458, 0x145897D88):
            klass = e.synthetic_type(cell); e.write(klass+0xC4, 1, 'B')
        self.stack_trace_class = e.synthetic_type(0x1458887E8)
        e.write(0x143BAC478, e.model_stub(self.filetime))
        e.models[0x159C5A080] = lambda: 0
        e.models[0x159B55280] = lambda: 0
        stack_text = e.alloc(); e.models[0x159B55F60] = lambda: stack_text
        cache, config = e.alloc(), e.alloc(); e.write(GLOBAL+0xA070, cache)
        e.models[0x14A9CE8E0] = lambda: config
        # Actual collection arrays are produced by the native graph reader.
        listing = e.read(OBJECT+24); lc = e.alloc(1024); e.write(listing, lc)
        e.write(lc+0x230, e.model_stub(self.enumerator))
        e.models[0x1572292B0] = self.move_next
        self.pack = self.warehouse(True); self.depot = self.warehouse(False)
        e.write(GLOBAL+0xB4D0, self.pack); e.write(GLOBAL+0x286A0, self.depot)
        self.module, self.packet = e.alloc(), e.alloc()
        e.write(self.packet+0x10, OBJECT); e.write(self.packet+0x22, CMD, 'H')
        self.resource_dict, self.resource_array = e.alloc(), self.array(24)
        e.write(self.resource_dict+0x18, self.resource_array); e.write(GLOBAL+0x32480, self.resource_dict)
        self.resources = {}
        for item_id, _, _ in case['items']:
            if item_id in self.resources or case.get('missing'): continue
            resource, base, resource_class = e.alloc(), e.alloc(), e.alloc()
            e.write(resource, resource_class); e.write(resource+0x10, base)
            e.write(base+0x38, case.get('category', 2), 'I')
            position = len(self.resources); self.resources[item_id] = (position, resource)
            e.write(self.resource_array+0x30+position*24, resource)
        for cell in (0x145A6B490, 0x145A6B498): self.info(cell, e.alloc(), context_offset=0x88)
        equality_type = e.synthetic_type(0x145951DF0)
        static, equality, equality_class = e.alloc(), e.alloc(), e.alloc(512)
        e.write(equality_type+0x38, static); e.write(static, equality); e.write(equality, equality_class)
        e.write(equality_class+0x110, 0x158BB0140)
        # All fallback resource dictionaries absent; native query will return null
        # at the explicit non-Material lookup boundary for a missing resource.
        e.models[0x147C38EC0] = lambda: 0
        e.models[0x1535F65E0] = lambda: 0
        e.models[0x1538B3FD0] = lambda: 0
        e.models[0x14A07BFB0] = lambda: 0
        e.models[0x14EBD90E0] = lambda: 0
        for cell in (0x1458B0890, 0x1458A3720, 0x1458CB2D8): e.synthetic_type(cell)

    def info(self, cell, value, method=False, context_offset=0x88):
        e = self.e; info, klass, ctx = e.alloc(), e.alloc(), e.alloc()
        e.write(cell, info)
        if method: e.write(info+0x18, ctx); e.write(ctx+0x10, value)
        else: e.write(info, klass); e.write(klass+0x30, ctx); e.write(ctx+context_offset, value)

    def array(self, width, capacity=17):
        p = self.e.alloc(32+capacity*width); self.e.write(p+24, capacity, 'I'); self.widths[p] = width; return p

    def warehouse(self, pack):
        e = self.e; obj, klass = e.alloc(), e.alloc(1024); e.write(obj, klass)
        e.write(klass+0x198, PACK if pack else BASE); e.write(klass+0x190, CATEGORY)
        e.write(klass+0x180, 0x1508054B0 if pack else 0x14E3DCF30)
        e.write(klass+0x188, 0x150809970 if pack else 0x14E3DD5E0)
        for off, (bo, eo, co, vo) in LAYOUTS.items():
            if not pack and off >= 0x88: continue
            d, buckets, entries = e.alloc(), self.array(4), self.array(24)
            e.write(obj+off, d); e.write(d+bo, buckets); e.write(d+eo, entries)
            e.write(d+co, 1, 'I'); e.write(d+vo, 9, 'I')
            e.write(buckets+32, 1, 'I'); e.write(entries+32, 0xAABBCCDD)
            self.dictionary_specs[d] = dict(owner=obj, offset=off, count=co, version=vo, arrays=[buckets, entries])
        e.write(obj+0x78, 0x1234)  # Capacity pointer must survive the reset.
        return obj

    def allocate(self):
        if self.e.reg('rcx') == self.stack_trace_class: return self.e.alloc()
        return self.allocate_original()

    def rent(self):
        p = self.e.alloc(512); self.e.write(p, self.e.reg('rdx')); return p

    def filetime(self): self.e.write(self.e.reg('rcx'), 123456789); return 0

    def array_clear(self):
        e = self.e; p, start, count = e.reg('rcx'), e.reg('edx'), e.reg('r8d')
        if p not in self.widths: return self.clear_original()
        width = self.widths[p]
        if start+count > e.read(p+24, 'I'): raise ValueError('Inventory clear exceeds fixture storage')
        if count: e.uc.mem_write(p+32+start*width, bytes(count*width))
        self.clear_calls.append(dict(array=p, start=start, count=count, width=width)); return 0

    def enumerator(self):
        e = self.e; out, listing = e.reg('rcx'), e.reg('rdx')
        backing = e.read(listing+0x10); array = e.read(backing+0x10); count = e.read(backing+0x18, 'I')
        self.enums[out] = dict(array=array, count=count, position=-1)
        e.write(out, 1, 'B'); e.write(out+8, array); return out

    def move_next(self):
        p = self.e.reg('rcx'); row = self.enums[p]; row['position'] += 1
        self.e.write(p+20, row['position'], 'I'); return int(row['position'] < row['count'])

    def resource_lookup(self):
        e = self.e
        if e.reg('rcx') != self.resource_dict: return self.resource_lookup_original()
        key = e.reg('edx'); self.resource_lookups.append(key)
        return self.resources[key][0] if key in self.resources else 0xFFFFFFFF

    def invoke(self, entry, stops=None, **registers):
        e = self.e; e.write(STACK+0x100008, RETURN)
        return fragment(e, entry, stops or {RETURN}, dict(rsp=STACK+0x100008, **registers))

    def delivery(self):
        e = self.e; self.delivered.append((e.reg('rcx'), e.reg('rdx'), e.reg('r8') & 255)); return 0

    def reset_and_deliver(self):
        e = self.e; e.models[APPLY] = self.delivery
        trace = self.invoke(DISPATCH, rcx=self.module, rdx=self.packet)
        del e.models[APPLY]
        selected = self.depot if self.case['storeType'] == 2 else self.pack
        checks = []
        for d, spec in self.dictionary_specs.items():
            wanted = spec['owner'] == selected
            state = dict(warehouse='pack' if spec['owner'] == self.pack else 'depot', offset=hex(spec['offset']),
                count=e.read(d+spec['count'], 'I'), version=e.read(d+spec['version'], 'I'),
                arraysZero=all(not any(bytes(e.uc.mem_read(a+32, self.widths[a]))) for a in spec['arrays']))
            if state['count'] != (0 if wanted else 1) or state['version'] != (10 if wanted else 9) or state['arraysZero'] != wanted:
                raise ValueError('Native inventory reset differs: ' + str(state))
            checks.append(state)
        if e.read(selected+0x78) != 0x1234: raise ValueError('Reset unexpectedly changed capacity')
        if len(self.delivered) != len(self.case['items']) or any(owner != selected or flag != 1 for owner, _, flag in self.delivered):
            raise ValueError('Actual list delivery differs')
        return trace, checks, selected

    def resource_path(self):
        literal = self.e.alloc(); self.e.write(0x145B547F8, literal)
        getter = self.invoke(0x14A1D79C0)
        if self.e.reg('rax') != literal: raise ValueError('Resource path getter differs')
        loader = self.invoke(0x14A1D7890, {0x14D6E1570})
        if self.e.reg('rcx') != literal: raise ValueError('Resource loader path argument differs')
        return dict(getter=getter, loader=loader, fileIOExecuted=False,
                    path='Data/_ExcelBinOutput/MaterialExcelConfigData')

    def application(self, warehouse, pointer):
        e = self.e
        # A preallocated category list supplies the native wrapper's ordinal.
        # The native category-list query itself executes; no item fields are supplied.
        d = e.read(warehouse+0x70); entries = e.read(d+0x28); listing = e.alloc()
        e.write(entries+0x30, listing); e.models[0x1565EE8D0] = lambda: 0
        trace = self.invoke(APPLY, {CATEGORY, RETURN}, rcx=warehouse, rdx=pointer, r8=1)
        if trace['stop'] == hex(RETURN): return trace, None, None
        wrapper = e.reg('rdi'); data = e.read(wrapper+0x10)
        result = dict(id=e.read(data+0x10, 'I'), guid=e.read(data+0x18), count=e.read(data+0x20, 'I'),
                      category=e.read(data+0x28, 'I'), delta=struct.unpack('<i', struct.pack('<I', e.reg('r8d')))[0],
                      modelClass=e.read(data) == self.data_class,
                      resource=e.read(data+0x30) == self.resources[e.read(data+0x10, 'I')][1],
                      clonedMaterial=self.g.snapshot(e.read(data+0x50)))
        # Separate suffix starts after the category callback. Its effects are
        # deliberately absent; this proves only the zero/nonzero slot selection.
        live = {name: e.reg(name) for name in ('rsp', 'rdi', 'r12', 'r13', 'r14', 'rbx')}
        selection = fragment(e, 0x14E43A94B, {0x1508054B0, 0x14E3DCF30, 0x150809970, 0x14E3DD5E0}, live)
        result['selectedOperation'] = 'remove' if selection['stop'] in ('0x150809970', '0x14e3dd5e0') else 'insert'
        if result['selectedOperation'] != ('insert' if result['count'] else 'remove'):
            raise ValueError('Native zero-count selection changed')
        return dict(prefix=trace, selection=selection), wrapper, result

    def index(self, warehouse, wrapper, guid, count):
        insert = self.invoke(GUID_INSERT if count else 0x14E3DD4F0, rcx=warehouse, rdx=wrapper)
        d = self.e.read(warehouse+0x68)
        lookup = self.invoke(GUID_FIND, rcx=d, rdx=guid)
        slot = self.e.reg('eax')
        if count:
            if slot == 0xFFFFFFFF: raise ValueError('Native GUID lookup lost inserted item')
            entries = self.e.read(d+0x28); stored = self.e.read(entries+0x30+slot*24)
            if stored != wrapper: raise ValueError('Native GUID index returned another wrapper')
        elif slot != 0xFFFFFFFF: raise ValueError('Native zero-count removal retained GUID')
        return dict(mutation=insert, lookup=lookup, slot=slot,
                    entries=self.e.read(d+0x40, 'I')-self.e.read(d+0x3C, 'I'))


def verify(binary, records, directory, libraries):
    audit = json.loads((directory/'player-store-outer-observations.json').read_text('utf8'))['dependencyAudit']
    all_cases = []
    for case in fixture_cases():
        e = Offline72(binary, libraries, records, trace_instructions=False); graph = InventoryGraph72(e, directory, audit)
        try:
            read = graph.run_graph(ROOT, case['wire']); runtime = InventoryContract72(e, graph, case)
            resource_path = runtime.resource_path()
            reset, dictionaries, warehouse = runtime.reset_and_deliver()
            applied = []; previous = {}
            for (_, pointer, _), (item_id, guid, count) in zip(runtime.delivered, case['items']):
                trace, wrapper, value = runtime.application(warehouse, pointer)
                if case.get('missing'):
                    if wrapper is not None: raise ValueError('Missing resource produced an inventory item')
                    applied.append(dict(native=trace, result=None)); continue
                category = case.get('category', 2)
                validate_result(dict(id=item_id, guid=guid, count=count, category=category, modelClass=True, resource=True), value)
                delta = (count-previous.get(guid, 0)) & 0xFFFFFFFF
                delta = delta if delta < 0x80000000 else delta-(1 << 32)
                if value['delta'] != delta: raise ValueError('Native quantity difference changed')
                indexed = runtime.index(warehouse, wrapper, guid, count)
                if count: previous[guid] = count
                else: previous.pop(guid, None)
                if indexed['entries'] != len(previous): raise ValueError('Native duplicate GUID semantics changed')
                applied.append(dict(native=trace, result=value, guidIndex=indexed))
            all_cases.append(dict(name=case['name'], storeType=case['storeType'], wire=case['wire'].hex(),
                reader=read, notification=reset, dictionaries=dictionaries, clearCalls=len(runtime.clear_calls),
                resourcePath=resource_path, materialResourceQueries=runtime.resource_lookups, applications=applied))
        finally: graph.close(); e.close()
    return all_cases


def reject_cases(binary, records, directory, libraries):
    audit = json.loads((directory/'player-store-outer-observations.json').read_text('utf8'))['dependencyAudit']
    e = Offline72(binary, libraries, records, trace_instructions=False); graph = InventoryGraph72(e, directory, audit)
    result = []
    try:
        for name, wire, limit in [('recursion-limit', '1200', 0), ('negative-nested-length', '12ffffffff0f', 100),
                ('truncated-material', '12052a050801', 100), ('invalid-root-tag', '00', 100),
                ('guid-overflow', '120b10ffffffffffffffffff02', 100)]:
            try: graph.run_graph(ROOT, bytes.fromhex(wire), limit)
            except ValueError as error:
                detail = str(error)
                result.append(dict(name=name, wire=wire, recursionLimit=limit, error=detail,
                    scope='native-error-branch' if 'Native error branch:' in detail else 'explicit-input-model'))
            else: raise ValueError('Invalid inventory fixture accepted: ' + name)
    finally: graph.close(); e.close()
    return result


def native_summary(trace):
    relevant = {trace['entry'], trace['stop']} | {hex(a) for a in (DISPATCH, WRAPPER, PACK, BASE, RESET,
        APPLY, FACTORY, CONVERT, RESOURCE, GUID_INSERT, GUID_FIND, 0x150ED3010, 0x150ED3210, 0x150ED3510,
        0x1497FD010, 0x15459E4B0, 0x152EE2F00, 0x152EE2B10, 0x14E3D73E0, 0x148446B80,
        0x14E3DD4F0, 0x156F6F850, 0x156F70100, 0x158BB0140)}
    return {k: trace[k] for k in ('entry', 'stop', 'instructions', 'nativePathSHA256', 'models')} | {
        'nativeEntries': sorted(set(trace['executedAddresses']) & relevant)}


def validate_report(report):
    errors = []
    scope = dict(inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
                 semanticName='PlayerStoreNotify', semanticIdentityStatus='verified',
                 identityBasis='inferred-native-store-replacement-purpose', originalClientMessageNameRecovered=False,
                 completeHandlerExecuted=False)
    if any(report.get(k) != v for k, v in scope.items()) or not report.get('models'):
        errors.append('Inventory input/runtime scope changed')
    if [(x.get('typeIndex'), x.get('tag'), x.get('semanticField')) for x in report.get('fieldBindings', [])] != BINDINGS:
        errors.append('Bounded inventory field scope changed')
    if report.get('unresolvedTags') != {'68537': [8], '65333': [6, 7, 8, 9, 10], '63442': [2]}:
        errors.append('Unknown inventory fields promoted')
    if len(report.get('rejections', [])) != 5: errors.append('Inventory error cases missing')
    if [x['name'] for x in report.get('fixtures', [])] != [x['name'] for x in fixture_cases()]:
        errors.append('Inventory fixtures changed')
    if report.get('declarations', {}).get('rawEvidenceSHA256') != RAW_SHA:
        errors.append('Reviewed raw input hashes changed')
    for row, case in zip(report.get('fixtures', []), fixture_cases()):
        if row['wire'] != case['wire'].hex() or row['storeType'] != case['storeType']:
            errors.append('Independent wire input changed')
        if row['clearCalls'] != (18 if case['storeType'] == 2 else 28): errors.append('Native clear coverage changed')
        if len(row['applications']) != len(case['items']): errors.append('Native repeated-item count changed')
        n = row['notification']
        required = {hex(a) for a in (DISPATCH, WRAPPER, BASE, RESET)}
        if row['storeType'] != 2: required.add(hex(PACK))
        if not required <= set(n['nativeEntries']) or row['applications'] and hex(APPLY) not in n['models']:
            errors.append('Notification reset/list boundary evidence missing')
        previous = {}
        for app, (item_id, guid, count) in zip(row['applications'], case['items']):
            if case.get('missing'):
                if app['result'] is not None: errors.append('Missing resource was accepted')
                continue
            if app['result'] is None:
                errors.append('Valid material application missing'); continue
            delta = (count-previous.get(guid, 0)) & 0xFFFFFFFF
            expected = dict(id=item_id, guid=guid, count=count, category=case.get('category', 2), modelClass=True,
                resource=True, delta=delta if delta < 0x80000000 else delta-(1 << 32),
                selectedOperation='insert' if count else 'remove')
            if any(app['result'].get(k) != v for k,v in expected.items()): errors.append('Native Material values changed')
            if count: previous[guid] = count
            else: previous.pop(guid, None)
            if app['guidIndex']['entries'] != len(previous): errors.append('GUID collision/overwrite/remove behavior changed')
            native = app['native']['prefix']
            needed = {hex(a) for a in (APPLY, FACTORY, CONVERT, RESOURCE, 0x150ED3210, 0x150ED3510)}
            if not needed <= set(native['nativeEntries']) or needed & set(native['models']):
                errors.append('Material application replaced by fixture')
            for stage in ('mutation', 'lookup'):
                trace = app['guidIndex'][stage]
                if {hex(GUID_FIND), hex(0x156F6F850), hex(0x156F70100)} & set(trace['models']):
                    errors.append('Concrete GUID dictionary replaced by fixture')
    return errors


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--client', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path); p.add_argument('--observations', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(a.client/name, digest) for name, digest in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    declared = declarations(b, m, s, records, a.directory)
    cases = verify(b, records, a.directory, a.python_libs)
    rejections = reject_cases(b, records, a.directory, a.python_libs)
    context = dict(schemaVersion=1, task='F-03d2b', inputSHA256=HASHES, declarations=declared,
        runtimeStatus='not-tested', targetProtocolReady=False, nativeFixtures=len(cases),
        semanticName='PlayerStoreNotify', semanticIdentityStatus='verified',
        identityBasis='inferred-native-store-replacement-purpose', originalClientMessageNameRecovered=False,
        completeHandlerExecuted=False, rejections=rejections,
        fieldBindings=[dict(typeIndex=t, tag=tag, semanticField=name, status='verified') for t, tag, name in BINDINGS],
        unresolvedTags={'68537': [8], '65333': [6, 7, 8, 9, 10], '63442': [2]},
        models=['Managed protobuf primitive input/default containers/clone allocation',
            'Explicit declared warehouse vtables; live singleton construction not executed',
            'Array.Clear storage and repeated-list enumerator over actual reader output',
            'Notification APPLY delivery sink; each actual item is later passed to a separate native APPLY prefix',
            'OS FILETIME/UTC offset and diagnostic StackTrace construction/string',
            'Resource dictionary storage/lookup, controlled rows/category and absent fallback tables',
            'Pool/metadata generic contexts and existing category-list lookup storage',
            'Category callback and index fanout omitted; suffix and native GUID primitives run separately'],
        scope='Separated native notification reset/list delivery, Material application prefix, count selection and GUID-index fragments')
    repo = Path(__file__).resolve().parents[1]
    context['sourceSHA256'] = {name: hashlib.sha256((repo/name).read_bytes()).hexdigest() for name in (
        'tools/client72_inventory_contract.py', 'tools/trace_client72_inventory_graph.py',
        'tools/trace_client72_inventory_containers.py', 'tools/trace_client72_player_store.py',
        'tools/client72_graph.py', 'tools/client72_map_runtime.py', 'tools/client72_session_runtime.py')}
    summary = dict(context, fixtures=[dict(name=c['name'], storeType=c['storeType'], wire=c['wire'],
        clearCalls=c['clearCalls'], dictionaries=c['dictionaries'], materialResourceQueries=c['materialResourceQueries'],
        notification=native_summary(c['notification']), resourcePath={k:native_summary(v) if isinstance(v, dict) else v for k,v in c['resourcePath'].items()},
        applications=[dict(result=x['result'], native={k:native_summary(v) for k,v in x['native'].items()} if x['result'] is not None else native_summary(x['native']),
            guidIndex={k:native_summary(v) if isinstance(v, dict) else v for k,v in x.get('guidIndex', {}).items()}) for x in c['applications']]) for c in cases])
    errors = validate_report(summary)
    if errors: raise ValueError(errors)
    for path, data in zip(paths, [dict(context, fixtures=cases), summary]):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(data, indent=2)+'\n', 'utf8')
    print(json.dumps(dict(nativeFixtures=len(cases), runtimeStatus='not-tested')))


if __name__ == '__main__': main()
