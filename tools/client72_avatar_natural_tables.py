"""Actual hash-bound MiHoYoBinData table parsers; offline research only."""
import argparse
import hashlib
import json
import struct
from collections import Counter
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .client72_avatar_cold_interfaces import FIVE_INPUTS
    from .client72_resource_deep import ResourceRuntime, RETURN, ENCODE, DECODE
    from .client72_session_runtime import permutation_table
    from .client72_emulator import GLOBAL, STACK, END
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_scene_deep import method_usages
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_signatures import annotate_generic_positions, bind_class_parameters
except ImportError:
    import client72_avatar_deep as base
    from client72_avatar_cold_interfaces import FIVE_INPUTS
    from client72_resource_deep import ResourceRuntime, RETURN, ENCODE, DECODE
    from client72_session_runtime import permutation_table
    from client72_emulator import GLOBAL, STACK, END
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_scene_deep import method_usages
    from extract_client72_enums import recover as recover_enums
    from extract_client72_signatures import annotate_generic_positions, bind_class_parameters

TABLES = {
    'Avatar': (0x14f33b1b0, 58272, 0x30870, 169, '466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9'),
    'AvatarSkillDepot': (0x14d6d54a0, 22284, 0x212b8, 171, 'fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b'),
    'AvatarSkill': (0x14eb02da0, 88602, 0x16c70, 1176, 'a5d2fdc6bbc32ea408c51e90c04777b771e9ab4fec4c35fc74186b6dc3c236fe'),
    'Weapon': (0x154177330, 87097, 0x34900, 288, 'cca19efbe032e5416d78c28fcb69c2ea2d9707187197aa4ed90d869c3a1ff918'),
    'AvatarOrnamentGroup': (0x15437a970, 61286, 0x13b70, 1, 'bd3bd58de65c59b1f4bb9f87d1535725cf22e621f402965cc42fbb765b454f00'),
    'AvatarOrnament': (0x147366b80, 44799, 0x1cd98, 2, '291895b576850960aa89965ae1ee7e4644f2c0fc31c01973cc6141e139201400'),
}
SELECTED = {'Avatar': (10000005, 10000007, 10000136), 'AvatarSkillDepot': (501, 701, 502, 504, 704),
            'AvatarSkill': (100540, 100550, 11101, 10004, 10035, 10101, 10001, 10102, 5005010, 5007010, 10006, 10067, 10068), 'Weapon': (11101,),
            'AvatarOrnamentGroup': (1,), 'AvatarOrnament': (101, 102)}
QUERIES = {'Avatar': 0x14f3382e0, 'AvatarSkillDepot': 0x14d6d70f0, 'AvatarSkill': 0x14eb02d30,
           'Weapon': 0x154177600, 'AvatarOrnamentGroup': 0x15437acb0, 'AvatarOrnament': 0x1473673b0}
INITIALIZATION_SHA = 'c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d'
LOCATOR_SHA = 'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--table', choices=TABLES, action='append')
    p.add_argument('--source', type=Path, default=Path('local/migration72/d02-active/resource-locator-astra/locator-final-1'))
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    a = p.parse_args()
    if a.output.resolve().is_relative_to(a.client.resolve()) or a.output.resolve().is_relative_to(a.source.resolve()):
        p.error('Output must be outside immutable inputs')
    blobs = {name: base.read_verified(a.source / (name + '.native-m_bytes.bin'), TABLES[name][4]) for name in (a.table or TABLES)}
    locator = base.read_verified(a.source.with_suffix('.json'), LOCATOR_SHA)
    codec = natural_codec(Path('local/migration72/d02-active/plain/initialization-resources-elevated5.json'))
    verified = [base.read_verified(a.client / n, h) for n, h in FIVE_INPUTS.items()]
    b, m, s = verified[:3]
    records, _ = base.extract(b, m, startup=s, structures=True)
    cells = base.type_cells(b, m, s, records)
    results = []
    a.output.parent.mkdir(parents=True, exist_ok=True)
    for name, blob in blobs.items():
        rt = NaturalRuntime(b, m, s, records, cells, a.python_libs, name, codec=codec)
        try:
            results.append(rt.parse_table(blob))
        except Exception:
            e = rt.e
            a.output.with_suffix('.failure.json').write_text(json.dumps(dict(table=name, completed=results,
                failure=e.failure, registers={n: hex(e.reg(n)) for n in ('rip', 'rcx', 'rdx', 'r8', 'rax', 'rsp')},
                cursor=e.read(rt.reader + 0x1c, 'I') if rt.reader else None,
                calls=rt.call_order, models=e.calls, allocations=rt.allocations, metadataGroups=rt.metadata_groups,
                nativeSources=rt.functions, classes=rt.type_records), indent=2) + '\n', 'utf8')
            raise
        finally:
            rt.e.close()
    a.output.with_suffix('.tables.json').write_text(json.dumps(results, indent=2, ensure_ascii=False) + '\n', 'utf8')
    controls = native_controls(b, m, s, records, cells, a.python_libs, codec) if not a.table else []
    report = dict(task='D02-R1-natural-tables', inputSHA256=FIVE_INPUTS, tables=results, controls=controls,
                  crossReferences=cross_references(results),
                  locatorReportSHA256=hashlib.sha256(locator).hexdigest(), scalarStorageCodec=codec['proof'],
                  completeAvatarHandler=False, completeColdComparer=False, targetProtocolReady=False,
                  activeRevisionVerified=False, javaResourcesLoaded=False, targetToJavaMappingApproved=False,
                  models=['Initialized typed managed class/vtable/hierarchy, MethodInfo/RGCTX and exact collection storage are explicit fixtures, not natural cold initialization.',
                          'Object/array allocation, UTF8 conversion after native byte transforms, memcpy/memset and exact class-identity assignability are bounded models.',
                          'Whole loops, row parsers, scalar encode/decode, UInt32 Dictionary insert/query, Group builder/enumerator and List growth execute original fixed native bytes.',
                          'Weapon uses explicit empty configuration HashSet and absent secondary configuration List; normal managed backing constructor path, not natural NativeConfig state.',
                          'Weapon TLS is explicit index0/4096B slab storage; native scratch allocate/release execute. Unobserved cache slots are never called natural input.'],
                  unresolved=['Resource payload identity is proven by locator, but actual selected live resource revision is not.',
                              'No complete Avatar constructor/handler, equipment lifecycle, generic cold comparer/default writeback or g2 permission follows from these resource contracts.',
                              'Scalar/list aliases beyond explicit consumers or original named enum constants remain pending semantic review; no missing protagonist Ornament row authorizes tag38 omission.',
                              'AvatarCurve/WeaponCurve/Promote and ConfigAvatar/PlayerAbilityGroup inputs and Java SavedVitals execution remain separate requirements.'],
                  dependencySHA256={name: hashlib.sha256(Path('tools', name).read_bytes()).hexdigest() for name in (
                      'client72_resource_deep.py', 'client72_avatar_deep.py', 'client72_emulator.py', 'client72_session_runtime.py',
                      'extract_client72.py', 'extract_client72_enums.py', 'extract_client72_signatures.py', 'extract_client72_literals.py',
                      'client72_scene_deep.py', 'client72_avatar_live_contract_ornament.py', 'client72_avatar_cold_interfaces.py')},
                  sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
    a.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', 'utf8')
    print(json.dumps(dict(tables=[r['name'] for r in results], counts=[r['count'] for r in results])))


def natural_codec(path):
    raw = base.read_verified(path, INITIALIZATION_SHA)
    report = json.loads(raw)
    rows = {row['label']: row for row in report['reads']}
    labels = ('resourceDecode.permutation', 'resourceDecode.length', 'resourceDecode.seed',
              *(f'resourceDecode.quarter{i}' for i in range(4)))
    selected = {name: rows[name] for name in labels}
    data = {}
    for name, row in selected.items():
        value = bytes.fromhex(row['bytesHex'])
        if row['error'] is not None or len(value) != row['length'] or hashlib.sha256(value).hexdigest() != row['sha256']:
            raise ValueError('Natural scalar-storage span failed integrity check')
        data[name] = value
    pointer = int.from_bytes(data['resourceDecode.permutation'], 'little')
    if int.from_bytes(data['resourceDecode.length'], 'little') != 2048 or rows['resourceDecode.length']['address'] != pointer + 0x18:
        raise ValueError('Natural permutation array header chain differs')
    inverse = b''.join(data[f'resourceDecode.quarter{i}'] for i in range(4))
    forward = bytearray(1024)
    for quarter in range(4):
        part = inverse[quarter * 256:(quarter + 1) * 256]
        if len(part) != 256 or len(set(part)) != 256 or rows[f'resourceDecode.quarter{quarter}']['address'] != pointer + 0x420 + quarter * 256:
            raise ValueError('Captured inverse quarter is not the exact array permutation span')
        for index, value in enumerate(part):
            forward[quarter * 256 + value] = index
    table = bytes(forward) + inverse
    return dict(table=table, seed=int.from_bytes(data['resourceDecode.seed'], 'little'), proof=dict(
        rawSHA256=INITIALIZATION_SHA, sourceSHA256=report['sourceSHA256'], inverseSHA256=hashlib.sha256(inverse).hexdigest(),
        forwardReconstructedSHA256=hashlib.sha256(forward).hexdigest(), tableSHA256=hashlib.sha256(table).hexdigest(),
        seedHex=data['resourceDecode.seed'].hex(),
        scope='Captured inverse quarters and seed; forward quarters reconstructed as exact bijective inverses, not claimed live reads; native scalar storage only, not a file cipher',
        spans=[dict(label=name, length=selected[name]['length'], sha256=selected[name]['sha256']) for name in labels]))


def cross_references(tables):
    by_name = {table['name']: table for table in tables}
    if not all(name in by_name for name in ('Avatar', 'AvatarSkillDepot', 'AvatarSkill', 'Weapon')):
        return []
    keys = {name: {row['key'] for row in table['rows']} for name, table in by_name.items()}
    result = []
    for avatar in by_name['Avatar']['selected'][:2]:
        fields = {field['offset']: field['value'] for field in avatar['fields']}
        depot_id, weapon_id = fields['0x168'], fields['0x154']
        depot = next(row for row in by_name['AvatarSkillDepot']['selected'] if row['key'] == depot_id)
        depot_fields = {field['offset']: field['value'] for field in depot['fields']}
        lists = {offset: depot_fields[offset] for offset in ('0x30', '0x38')}
        positive = sorted({value for values in lists.values() for value in values if value > 0})
        missing = [value for value in positive if value not in keys['AvatarSkill']]
        if depot_id not in keys['AvatarSkillDepot'] or weapon_id not in keys['Weapon'] or missing:
            raise ValueError('Actual protagonist resource reference is absent')
        result.append(dict(avatarId=avatar['key'], avatarSource=avatar['source'], defaultDepot=depot_id,
            nativeDefaultDepotConsumer='Miscs.GetAvatarElementType 0x153217960, reviewed separately',
            weaponReferenceField154=weapon_id, depotSource=depot['source'], listsByOffset=lists,
            positiveSkillReferences=positive, missingSkillReferences=missing,
            field68=depot_fields['0x68'], field6c=depot_fields['0x6c'], scalarPresence=depot['scalarPresence'],
            scope='Join of independently native-parsed rows and native per-ID query results; not a complete Avatar handler'))
    return result


class NaturalRuntime(ResourceRuntime):
    def __init__(self, binary, metadata, startup, records, cells, libraries, name, codec=None, capacity=None):
        base.load_emulator(libraries)
        from capstone import Cs, CS_ARCH_X86, CS_MODE_64
        self.binary, self.metadata, self.startup, self.records, self.cells, self.name = binary, metadata, startup, records, cells, name
        self.capacity = TABLES[name][3] if capacity is None else capacity
        self.e = e = base.Offline72(binary, libraries, records, trace_instructions=True)
        self.decoder = Cs(CS_ARCH_X86, CS_MODE_64)
        self.decoder.detail = True
        e.uc.mem_map(RETURN & ~4095, 4096)
        e.uc.mem_write(RETURN, b'\x90')
        e.prepare()
        e.allow_unwind(ENCODE)
        self.extents = e.unwind_ranges
        self.functions, self.classes, self.type_records, self.method_cells = {}, {}, {}, {}
        self.objects, self.arrays, self.strings = {}, {}, {}
        self.allocations, self.string_conversions, self.metadata_groups, self.call_order = [], [], [], []
        self.reader, self.spans, self.pending_rows = 0, [], {}
        self.scratch_requests, self.slab = [], 0
        self.row_entry = next(int(x['registeredAddress'], 16) for x in records[TABLES[name][1]]['methods'] if x['name'] == 'CNLIBMNOALB')
        self.literals = Literals72(binary, metadata)
        self.reference_base = base.number(binary, e.pe.offset(0x1428a53c8), 'Q')
        self.all_methods = {method['index']: (owner, method) for owner in records for method in owner['methods']}
        enum_rows = recover_enums(binary, metadata, records, indices=(32419, 54063, 34819, 28885, 74813,
            73925, 85180, 42794, 37328, 64315, 19957, 18743, 27148))['enums']
        self.enum_symbols = {row['typeIndex']: row for row in enum_rows}
        self.models = {0x1405336c0: self.allocate_object, 0x140514ee0: self.allocate_array,
                       0x14051b7b0: self.assignable, 0x141fdf9c0: self.memset, 0x141fdf310: self.memcpy,
                       0x14f0de820: self.convert_string, 0x14052d0f0: self.initialize_metadata}
        e.models.update(self.models)
        table = self.array(2048, 1)
        raw = permutation_table(13, 29)
        e.uc.mem_write(table + 32, codec['table'] if codec else raw[:1024] + raw[2048:3072])
        e.write(GLOBAL + 0xe970, table)
        e.write(0x145872d30, codec['seed'] if codec else 0x12345678, 'I')
        self.registry_base = e.alloc(16)
        registry = e.alloc(8)
        e.write(registry, self.registry_base)
        e.write(0x145bc8ad0, registry)
        empty = self.array(0, 8)
        for cell in (0x145b744f8, 0x145b74530, 0x145b74618, 0x145b749b0, 0x145b749b8, 0x145b74d50):
            e.write(cell, empty)
        e.write(0x145ac9e50, self.string(''))
        self.bind_function(TABLES[name][0])
        non_type_cells = {int(row['target'], 16) for row in self.functions[hex(TABLES[name][0])]['references'] if int(row['target'], 16) not in cells}
        uses = method_usages(binary, metadata, startup, records, non_type_cells)
        self.loader_usages = uses
        dictionary = next(ptr for ptr, typ in self.type_records.items() if typ.get('typeDefinition') == 1222)
        self.dictionary_class = dictionary
        context = e.alloc(256)
        e.write(dictionary + 0x30, context)
        constructor = e.alloc(64)
        e.write(constructor, dictionary)
        e.write(constructor + 8, e.model_stub(self.dictionary_constructor))
        e.write(context + 0x10, constructor)
        insert = e.alloc(64)
        e.write(insert, dictionary)
        e.write(insert + 8, 0x1578b6a20)
        e.write(context + 0x88, insert)
        for usage in uses:
            if usage['ownerType'] != 1222 or usage['classArguments'][1].get('typeDefinition') != TABLES[name][1]:
                raise ValueError('Unexpected whole loader generic context')
            method = e.alloc(64)
            e.write(method, dictionary)
            e.write(int(usage['cell'], 16), method)
            self.method_cells[int(usage['cell'], 16)] = usage
        if name == 'Weapon':
            # Explicit empty configuration-set fixture. The native predicate and
            # managed backing-object constructor remain executed, not stubbed.
            configuration = e.alloc(0x48)
            e.write(configuration, self.bind_type(records[61440]['fields'][1]['type']))
            e.write(GLOBAL + 0x60e8, configuration)
            gsbase = 0x630000000
            e.uc.mem_map(gsbase, 4096)
            e.uc.reg_write(e.R.UC_X86_REG_GS_BASE, gsbase)
            tlsarray, tlsblock = e.alloc(256), e.alloc(8192)
            e.write(gsbase + 0x58, tlsarray)
            e.write(tlsarray, tlsblock)
            e.write(0x145eef080, 0, 'I')
            self.slab, buffer = e.alloc(32), e.alloc(4096)
            e.write(tlsblock + 0x1998, 4096)
            e.write(tlsblock + 0x19a0, self.slab)
            e.write(tlsblock + 0x19a8, self.slab)
            e.write(self.slab, buffer)
            e.write(self.slab + 16, 4096)

    def bind_function(self, address):
        if address in self.e.models or hex(address) in self.functions:
            return
        end = self.extents.get(address) or self.e.ends.get(address)
        if end is None or end - address > 0x20000:
            return
        e = self.e
        code = self.binary[e.pe.offset(address):e.pe.offset(address) + end - address]
        references = []
        for ins in self.decoder.disasm(code, address):
            for operand in ins.operands:
                if operand.type == 3 and ins.reg_name(operand.mem.base) == 'rip':
                    cell = ins.address + ins.size + operand.mem.disp
                    references.append(dict(site=hex(ins.address), target=hex(cell), instruction=ins.mnemonic + ' ' + ins.op_str))
                    if cell in self.cells:
                        e.write(cell, self.bind_type(self.cells[cell]['type'], self.cells[cell]['sourceIndex']))
        self.functions[hex(address)] = dict(end=hex(end), sha256=hashlib.sha256(code).hexdigest(), references=references)
        e.allowed.append((address, end))

    def bind_type(self, typ, source_index=None):
        def semantic(t):
            return {k: [semantic(x) for x in v] if isinstance(v, list) else semantic(v) if isinstance(v, dict) else v
                    for k, v in t.items() if k not in ('attributes',)}
        key = json.dumps(semantic(typ), sort_keys=True)
        if key in self.classes:
            return self.classes[key]
        e = self.e
        klass = e.alloc(2048)
        self.classes[key] = klass
        self.type_records[klass] = typ
        e.write(klass + 0xc7, 1, 'B')
        e.write(klass + 0xc8, 1, 'B')
        if source_index is not None:
            address = self.reference_base + source_index * 16
            e.uc.mem_write(klass + 0x10, self.binary[e.pe.offset(address):e.pe.offset(address) + 16])
        if typ['kind'] == 'szarray':
            element = self.bind_type(typ['elementType'])
            e.write(klass + 0xac, element - self.registry_base, 'I')
        elif 'typeDefinition' in typ:
            index = typ['typeDefinition']
            owners = []
            while index is not None:
                owners.append(self.records[index])
                parent = self.records[index]['parent']
                index = parent['typeDefinition'] if parent else None
            for owner in reversed(owners):
                for method in owner['methods']:
                    if method['virtualSlot'] != 65535 and method['registeredAddress'] != '0x0':
                        offset = 0xd0 + method['virtualSlot'] * 8
                        if offset >= 2048:
                            raise ValueError('Class vtable exceeds declared storage')
                        e.write(klass + offset, int(method['registeredAddress'], 16))
            hierarchy = e.alloc(len(owners) * 8)
            for position, owner in enumerate(reversed(owners)):
                parent_class = klass if owner['index'] == typ['typeDefinition'] else self.bind_type(
                    next(record['parent'] for record in owners if record['parent'] and record['parent']['typeDefinition'] == owner['index']))
                e.write(hierarchy + position * 8, parent_class)
            e.write(klass + 0x48, hierarchy)
            e.write(klass + 0xc4, len(owners), 'B')
        return klass

    def array(self, count, width):
        pointer = super().array(count, width)
        self.arrays[pointer] = dict(count=count, width=width, type=None)
        return pointer

    def allocate_object(self):
        e = self.e
        typ = self.type_records[e.reg('rcx')]
        index = typ['typeDefinition']
        size = {1222: 0x48, 1255: 0x20}.get(index, int(self.records[index]['fieldLayout']['rawSizeAndFlags'][0], 16))
        pointer = e.alloc(size)
        e.write(pointer, e.reg('rcx'))
        self.objects[pointer] = index
        self.allocations.append(dict(operation='object', type=index, size=size, pointer=hex(pointer), caller=hex(e.read(e.reg('rsp')))))
        return pointer

    def element_width(self, typ):
        if typ['kind'] in ('class', 'string', 'object', 'szarray', 'genericinst'):
            return 8
        widths = {'bool': 1, 'char': 2, 'int32': 4, 'uint32': 4, 'single': 4, 'float': 4, 'int64': 8, 'uint64': 8, 'double': 8}
        if typ['kind'] in widths:
            return widths[typ['kind']]
        if typ['kind'] == 'valuetype':
            row = self.records[typ['typeDefinition']]
            if row['parent'] and row['parent']['typeDefinition'] == 204:
                return 4
            size = int(row['fieldLayout']['rawSizeAndFlags'][0], 16) - 16
            if size in (1, 2, 4, 8, 12, 16):
                return size
        raise ValueError('Undeclared array element width: ' + str(typ))

    def allocate_array(self):
        e = self.e
        typ = self.type_records[e.reg('rcx')]
        if typ['kind'] != 'szarray' or e.reg('rdx') > 4096:
            raise ValueError('Outside declared resource array input')
        width = self.element_width(typ['elementType'])
        pointer = self.array(e.reg('rdx'), width)
        e.write(pointer, e.reg('rcx'))
        self.arrays[pointer]['type'] = typ
        self.allocations.append(dict(operation='array', element=typ['elementType'], count=e.reg('rdx'), width=width, caller=hex(e.read(e.reg('rsp')))))
        return pointer

    def assignable(self):
        e = self.e
        if e.reg('rcx') not in self.type_records or e.reg('rdx') not in self.type_records:
            raise ValueError('Unbound resource array type comparison')
        return int(e.reg('rcx') == e.reg('rdx'))

    def dictionary_constructor(self):
        e = self.e
        count, obj = e.reg('edx'), e.reg('rcx')
        if e.read(obj) != self.dictionary_class or count != self.capacity or e.reg('r8') != 0:
            raise ValueError(f'Unexpected Dictionary capacity constructor input: requested={count}, declared={self.capacity}')
        e.write(obj + 0x18, self.array(max(1, count), 24))
        e.write(obj + 0x20, self.array(max(1, count), 4))
        e.write(obj + 0x3c, 0xffffffff, 'I')
        return 0

    def initialize_metadata(self):
        e = self.e
        group = e.reg('ecx')
        lit = self.literals
        def first(i):
            return ((base.number(self.metadata, lit.groups_base + i * 4) + 0x8bff3e05) & 0xffffffff) ^ ((i * 0x5843eaa0 - 0x443c8630) & 0xffffffff)
        rows = []
        registration = e.pe.offset(USAGE_REGISTRATION)
        for i in range(first(group), first(group + 1)):
            key = (i * 0x57c15286 + 0x26ddebda) & 0xffffffff
            source = ((base.number(self.metadata, lit.usages_base + i * 8) + 0xabbdcabf) & 0xffffffff) ^ key ^ 0x2a357d90
            slot = base.number(self.metadata, lit.usages_base + i * 8 + 4) ^ key ^ 0x1855aa65
            kind = source >> 29
            if kind == 1:
                cell = base.number(self.binary, registration + 0x48, 'Q') + slot * 8
                usage = self.cells[cell]
                e.write(cell, self.bind_type(usage['type'], usage['sourceIndex']))
            elif kind == 6:
                cell = base.number(self.binary, registration + 0x10, 'Q') + slot * 8
                usages = method_usages(self.binary, self.metadata, self.startup, self.records, {cell})
                usage = usages[0]
                if cell not in self.method_cells:
                    info, owner = e.alloc(64), e.alloc(512)
                    e.write(info, owner)
                    e.write(owner + 0x10, usage['ownerType'])
                    e.write(cell, info)
                    self.method_cells[cell] = usage
            else:
                raise ValueError('Unmodeled metadata usage kind ' + str(kind))
            rows.append(usage)
        self.metadata_groups.append(dict(group=group, usages=rows))
        return 0

    def call(self, entry, stops=(), **registers):
        e = self.e
        e.calls, e.failure, e.steps = [], None, 0
        e.write(STACK + 0x100008, RETURN)
        registers = dict(rsp=STACK + 0x100008, **registers)
        for key, value in registers.items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + key.upper()), value)
        counts, digest, reached = Counter(), hashlib.sha256(), []
        stop_set = {RETURN, 0x1404c2be0, 0x1404c2650, 0x1404c2c70, 0x1404c2770, 0x1404bbd90, *stops}
        for stop in stop_set:
            e.allowed.append((stop, stop + 1))
        def observe(address):
            if address == 0x1405070c0:
                self.scratch_requests.append(dict(count=e.reg('rdx'), usedBefore=e.read(self.slab + 8),
                                                  caller=hex(e.read(e.reg('rsp')))))
            if address == self.row_entry and self.reader and e.reg('rdx') == self.reader:
                self.pending_rows[e.read(e.reg('rsp'))] = (e.reg('rcx'), e.read(self.reader + 0x1c, 'I'))
            if address in self.pending_rows:
                pointer, begin = self.pending_rows.pop(address)
                self.spans.append(dict(pointer=hex(pointer), begin=begin, end=e.read(self.reader + 0x1c, 'I')))
            if address in self.extents or address in e.ends:
                self.bind_function(address)
                self.call_order.append(dict(entry=hex(address), rcx=hex(e.reg('rcx')), rdx=hex(e.reg('rdx'))))
            counts[address] += 1
            digest.update(struct.pack('<Q', address))
            if address in stop_set:
                reached.append(address)
                e.uc.emu_stop()
        e.before_instruction = observe
        try:
            e.uc.emu_start(entry, END, timeout=60_000_000, count=40_000_000)
        finally:
            e.before_instruction = None
        if e.failure or len(reached) != 1:
            raise ValueError(e.failure or 'Native resource parser did not reach declared stop')
        return dict(entry=hex(entry), stop=hex(reached[0]), instructions=e.steps, nativePathSHA256=digest.hexdigest(),
                    executedAddresses={hex(k): v for k, v in sorted(counts.items())}, models=[r['address'] for r in e.calls])

    def parse_table(self, blob):
        e = self.e
        self.reader = e.alloc(64)
        array = self.array(len(blob), 1)
        e.uc.mem_write(array + 0x20, blob)
        e.write(self.reader + 0x10, array)
        trace = self.call(TABLES[self.name][0], rcx=self.reader)
        if trace['stop'] != hex(RETURN):
            raise ValueError('Actual table reached native error helper')
        dictionary = e.read(GLOBAL + TABLES[self.name][2])
        entries = e.read(dictionary + 0x18)
        count = e.read(dictionary + 0x38, 'I')
        rows = []
        for i in range(count):
            key = e.read(entries + 0x28 + i * 24, 'I')
            pointer = e.read(entries + 0x30 + i * 24)
            rows.append(dict(key=key, pointer=hex(pointer), bytes=bytes(e.uc.mem_read(pointer, int(self.records[TABLES[self.name][1]]['fieldLayout']['rawSizeAndFlags'][0], 16))).hex()))
        if e.read(self.reader + 0x1c, 'I') != len(blob) or count != self.capacity:
            raise ValueError('Actual whole table length/count mismatch')
        spans = {row['pointer']: row for row in self.spans}
        for row in rows:
            span = spans[row['pointer']]
            row['source'] = dict(begin=span['begin'], end=span['end'], sha256=hashlib.sha256(blob[span['begin']:span['end']]).hexdigest())
        selected = []
        for key in SELECTED.get(self.name, ()):
            query = self.call(QUERIES[self.name], rcx=key)
            pointer = e.reg('rax')
            matching = next((row for row in rows if row['key'] == key), None)
            if query['stop'] != hex(RETURN) or pointer != (int(matching['pointer'], 16) if matching else 0):
                raise ValueError('Selected native resource query differs from inserted row')
            selected.append(dict(key=key, found=bool(pointer), source=matching['source'] if matching else None,
                                 fields=self.snapshot_object(pointer, TABLES[self.name][1], 0) if pointer else None, query=query))
            if self.name == 'AvatarSkillDepot' and matching:
                mask = (struct.unpack_from('<I', blob, matching['source']['begin'])[0] + 0x51fee905) & 0xffffffff
                selected[-1]['scalarPresence'] = dict(maskHex=f'{mask:08x}', field68=bool(mask & 0x40), field6c=bool(mask & 0x10),
                    source='native row parser header mask; field68 bit40 / field6c bit10')
        derived = self.group_by_avatar(dictionary) if self.name == 'AvatarOrnamentGroup' else None
        return dict(name=self.name, payloadSHA256=hashlib.sha256(blob).hexdigest(), payloadBytes=len(blob),
                    cursor=e.read(self.reader + 0x1c, 'I'), count=count, rows=rows, selected=selected,
                    derivedByAvatar=derived,
                    trace=trace, nativeSources=self.functions, allocationModels=self.allocations,
                    metadataGroups=self.metadata_groups, loaderMethodUsages=self.loader_usages,
                    scratchRequests=self.scratch_requests, scratchUsedAtEnd=e.read(self.slab + 8) if self.slab else None,
                    typedClasses=[dict(pointer=hex(k), type=v) for k, v in self.type_records.items()],
                    enumSymbols=list(self.enum_symbols.values()),
                    strings={hex(k): v for k, v in self.strings.items()}, callOrder=self.call_order)

    def group_by_avatar(self, primary):
        e = self.e
        self.bind_function(0x15437af90)
        table_type = self.records[28604]['fields'][1]['type']
        table_class = self.bind_type(table_type)
        list_type = table_type['arguments'][1]
        list_class = self.bind_type(list_type)
        list_context = e.alloc(512)
        e.write(list_class + 0x30, list_context)
        array_type = dict(kind='szarray', elementType=list_type['arguments'][0], rank=1)
        e.write(list_context + 0x10, self.array(0, 8))
        e.write(list_context + 0x18, self.bind_type(array_type))
        context_proof = []
        for slot, expected, address in ((7, 10441, 0x1591151b0), (17, 10379, 0x159111f60), (26, 10406, 0)):
            proof = self.generic_definition(1255, slot)
            if proof['kind'] != 3 or proof['methodDefinition'] != expected:
                raise ValueError('List context definition differs')
            info = e.alloc(64)
            e.write(info, list_class)
            e.write(info + 8, address)
            e.write(list_context + 0x10 + slot * 8, info)
            context_proof.append(proof)
        table_context = e.alloc(512)
        e.write(table_class + 0x30, table_context)
        insert = e.alloc(64)
        e.write(insert, table_class)
        e.write(insert + 8, 0x1578b6a20)
        e.write(table_context + 0x88, insert)
        context_proof.append(self.generic_definition(1222, 15))
        usages = method_usages(self.binary, self.metadata, self.startup, self.records,
                              {0x1459b4d98, 0x1459b4da8, 0x1459b4db0, 0x1459b4db8})
        for usage in usages:
            info = e.alloc(64)
            owner = list_class if usage['ownerType'] == 1255 else table_class if usage['classInstance'] == 23903 else self.dictionary_class
            e.write(info, owner)
            e.write(int(usage['cell'], 16), info)
            self.method_cells[int(usage['cell'], 16)] = usage
        derived = e.alloc(0x48)
        e.write(derived, table_class)
        e.write(derived + 0x18, self.array(1, 24))
        e.write(derived + 0x20, self.array(1, 4))
        e.write(derived + 0x3c, 0xffffffff, 'I')
        e.write(GLOBAL + 0x13b78, derived)
        # Existing typed Dictionary.ValueCollection storage; enumeration, lookup,
        # List allocation/growth and dictionary insertion still execute natively.
        value_definition = self.generic_definition(1222, 11)
        value_type = value_definition['type']
        h = e.pe.offset(base.HEADER_VA)
        generic_base = base.BODY + ((base.number(self.binary, h + 0x94) + 0xd97e8a5c) & 0xffffffff)
        value_type = bind_class_parameters(annotate_generic_positions(value_type, self.metadata, generic_base),
                                          self.type_records[self.dictionary_class]['arguments'])
        view = e.alloc(24)
        e.write(view, self.bind_type(value_type))
        e.write(view + 0x10, primary)
        e.write(primary + 0x10, view)
        context_proof.append(value_definition)
        queries = []
        for avatar in (10000005, 10000007, 10000136):
            trace = self.call(0x15437ada0, rcx=avatar)
            pointer = e.reg('rax')
            if trace['stop'] != hex(RETURN):
                raise ValueError('Group by-avatar native query failed')
            items = []
            if pointer:
                array = e.read(pointer + 0x10)
                items = [hex(e.read(array + 0x20 + i * 8)) for i in range(e.read(pointer + 0x18, 'I'))]
            queries.append(dict(avatarId=avatar, listPointer=hex(pointer), groupPointers=items, trace=trace))
        return dict(dictionaryCount=e.read(derived + 0x38, 'I'), queries=queries, methodUsages=usages,
                    genericDefinitions=context_proof,
                    fixture='Empty derived Dictionary plus already constructed typed primary ValueCollection; no loader/cold runtime initialization claim')

    def generic_definition(self, owner, slot):
        e, b, m = self.e, self.binary, self.metadata
        h = e.pe.offset(base.HEADER_VA)
        start = base.BODY + ((base.number(b, h + 0x50) + 0xe113ba5c) & 0xffffffff)
        size = (base.number(b, h + 0x104) + 0x938d8470) & 0xffffffff
        packed = dict(struct.iter_unpack('<II', m[start:start + size]))[owner]
        if slot >= packed >> 24:
            raise ValueError('Generic context slot exceeds exact definition')
        definitions = base.BODY + (base.number(b, h + 0x9c) ^ 0x4b5cdca3)
        word = base.number(m, definitions + ((packed & 0xffffff) + slot) * 4)
        kind, index = (word >> 26) & 31, word & 0x3ffffff
        proof = dict(owner=owner, slot=slot, word=hex(word), kind=kind, index=index)
        if kind == 3:
            specs = base.BODY + (base.number(b, h + 0x170) ^ 0x6347382b)
            method, cls, inst = struct.unpack_from('<iii', m, specs + index * 12)
            proof.update(methodDefinition=method, classInstance=cls, methodInstance=inst,
                         method=self.all_methods[method][1]['name'])
        elif kind == 2:
            proof['type'] = base.TypeResolver(e.pe, self.startup, self.records, h).resolve_index(index)
        return proof

    def snapshot_object(self, pointer, index, depth):
        if depth > 5:
            return dict(unresolved='nested depth beyond selected projection', type=index)
        owners = []
        while index is not None and index != 371:
            owners.append(self.records[index])
            parent = self.records[index]['parent']
            index = parent['typeDefinition'] if parent else None
        return [dict(owner=owner['index'], fieldIndex=field['index'], field=field['name'], offset=hex(field['offset']),
                     type=field['type'], value=self.snapshot_value(pointer + field['offset'], field['type'], depth + 1))
                for owner in reversed(owners) for field in owner['fields'] if field['offsetKind'] == 'instance']

    def snapshot_value(self, pointer, typ, depth):
        e = self.e
        kind = typ['kind']
        if kind == 'valuetype':
            index = typ['typeDefinition']
            if index == 58209:
                return self.decode(pointer)
            if index == 44333:
                trace = self.call(0x14ef4b180, rcx=pointer)
                if trace['stop'] != hex(RETURN):
                    raise ValueError('Native 44333 wrapper getter failed')
                bits = e.reg('eax')
                return dict(decoded32=bits, bits=f'{bits:08x}', getter='0x14ef4b180', signedInterpretation='not-inferred')
            if index == 54724:
                trace = self.call(0x14f905140, rcx=pointer)
                if trace['stop'] != hex(RETURN):
                    raise ValueError('Native float wrapper getter failed')
                bits = e.reg('xmm0') & 0xffffffff
                return dict(float32=struct.unpack('<f', struct.pack('<I', bits))[0], bits=f'{bits:08x}', getter='0x14f905140')
            row = self.records[index]
            if row['parent'] and row['parent']['typeDefinition'] == 204:
                value = e.read(pointer, 'i')
                symbols = [x['symbol'] for x in self.enum_symbols.get(index, {}).get('values', []) if x['value'] == value]
                return dict(enumType=index, value=value, bits=f"{e.read(pointer, 'I'):08x}", symbols=symbols)
            if index == 81112:
                return [dict(field=x['name'], offset=hex(x['offset'] - 16),
                    value=self.snapshot_value(pointer + x['offset'] - 16, x['type'], depth + 1))
                    for x in row['fields'] if x['offsetKind'] == 'instance']
            return dict(type=index, rawHex=bytes(e.uc.mem_read(pointer, self.element_width(typ))).hex(), semantic='unresolved-value-type')
        formats = {'uint32': 'I', 'int32': 'i', 'bool': 'B', 'single': 'f', 'float': 'f', 'double': 'd'}
        if kind in formats:
            value = e.read(pointer, formats[kind])
            return bool(value) if kind == 'bool' else value
        if kind in ('uint64', 'int64'):
            return dict(decimal=str(e.read(pointer, 'Q' if kind == 'uint64' else 'q')), bits=f"{e.read(pointer):016x}")
        target = e.read(pointer)
        if not target:
            return dict(pointerNull=True, meaning='storage-null; omission/default semantics require field-flag evidence')
        if kind == 'string':
            if target not in self.strings:
                raise ValueError('Unrecorded string conversion source')
            return self.strings[target]
        if kind == 'szarray':
            count = e.read(target + 0x18, 'I')
            if count > 4096:
                raise ValueError('Selected array exceeds bounded projection')
            element = typ['elementType']
            width = self.element_width(element)
            return [self.snapshot_value(target + 0x20 + i * width, element, depth + 1) for i in range(count)]
        if kind == 'class' and target in self.objects:
            return self.snapshot_object(target, self.objects[target], depth)
        return dict(pointer=hex(target), type=typ, semantic='unresolved-native-or-generic-container')


def native_controls(binary, metadata, startup, records, cells, libraries, codec):
    # Independent whole-table inputs, not locator products or candidate JSON.
    # Reuse the reviewed row encoder; new count framing and whole-table behavior
    # are checked independently of the actual locator products.
    try:
        from .client72_avatar_live_contract_ornament import group_bytes
    except ImportError:
        from client72_avatar_live_contract_ornament import group_bytes
    first = group_bytes(dict(group=1, avatar=10000136, plain=0xffffffff, default=101))
    second = group_bytes(dict(group=0x80000001, avatar=10000136, plain=0x80000001, default=0xfffffffe))
    cases = [('two-rows-same-avatar-high-u32', 2, struct.pack('<I', 2 ^ 0x5eb64fe9) + first + second, hex(RETURN)),
             ('duplicate-key-rejected', 2, struct.pack('<I', 2 ^ 0x5eb64fe9) + first + first, '0x1404bbd90'),
             ('last-scalar-truncated', 1, struct.pack('<I', 1 ^ 0x5eb64fe9) + first[:-1], hex(RETURN)),
             ('header-truncated', 0, b'\xe9\x4f\xb6', None),
             ('zero-row-whole', 0, struct.pack('<I', 0x5eb64fe9), hex(RETURN))]
    results = []
    for name, count, blob, expected in cases:
        rt = NaturalRuntime(binary, metadata, startup, records, cells, libraries, 'AvatarOrnamentGroup', codec=codec, capacity=count)
        e = rt.e
        try:
            rt.reader = e.alloc(64)
            array = rt.array(len(blob), 1)
            e.uc.mem_write(array + 32, blob)
            e.write(rt.reader + 0x10, array)
            try:
                trace = rt.call(TABLES['AvatarOrnamentGroup'][0], rcx=rt.reader)
            except ValueError as error:
                trace = dict(stop=None, failure=str(error), registers={key: hex(e.reg(key)) for key in ('rip', 'rcx', 'rdx', 'rax')}, models=e.calls)
            result = dict(name=name, inputHex=blob.hex(), inputSHA256=hashlib.sha256(blob).hexdigest(),
                          cursor=e.read(rt.reader + 0x1c, 'I'), trace=trace, expectedStop=expected,
                          expectationMatched=trace['stop'] == expected, callOrder=list(rt.call_order))
            result['exactExtentAccepted'] = result['cursor'] == len(blob)
            if name == 'two-rows-same-avatar-high-u32':
                primary = e.read(GLOBAL + 0x13b70)
                result['derived'] = rt.group_by_avatar(primary)
                query = rt.call(QUERIES['AvatarOrnamentGroup'], rcx=0x80000001)
                pointer = e.reg('rax')
                result['highKeyQuery'] = query
                result['highKeyFields'] = rt.snapshot_object(pointer, 61286, 0)
                if rt.decode(pointer + 0x10) != 0x80000001 or rt.decode(pointer + 0x1c) != 0xfffffffe:
                    raise ValueError('High uint32 key/default was truncated')
            results.append(result)
        finally:
            e.close()
    rt = NaturalRuntime(binary, metadata, startup, records, cells, libraries, 'AvatarOrnament', codec=codec)
    e = rt.e
    try:
        actual = base.read_verified(Path('local/migration72/d02-active/resource-locator-astra/locator-final-1/AvatarOrnament.native-m_bytes.bin'), TABLES['AvatarOrnament'][4])
        changed = []
        def wrong_array_class():
            typ = rt.type_records[e.reg('rcx')]
            pointer = rt.allocate_array()
            if typ['elementType'].get('typeDefinition') == 58937:
                wrong = dict(typ, elementType=dict(kind='class', typeDefinition=61286, name=records[61286]['name'], namespace=''))
                e.write(pointer, rt.bind_type(wrong))
                changed.append(dict(pointer=hex(pointer), declared=58937, injected=61286))
            return pointer
        e.models[0x140514ee0] = wrong_array_class
        rt.reader = e.alloc(64)
        array = rt.array(len(actual), 1)
        e.uc.mem_write(array + 32, actual)
        e.write(rt.reader + 0x10, array)
        trace = rt.call(TABLES['AvatarOrnament'][0], rcx=rt.reader)
        results.append(dict(name='actual-ornament-wrong-condition-array-class', payloadSHA256=TABLES['AvatarOrnament'][4],
                            changed=changed, trace=trace, callOrder=list(rt.call_order)))
    finally:
        e.close()
    return results


if __name__ == '__main__':
    main()
