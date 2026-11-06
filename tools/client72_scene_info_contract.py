"""Bounded CN72 SceneInfo identity associations; never starts the client.

Native list iteration, GUID dictionaries, model association stores and a named
Avatar entity getter execute. Scene objects and managed metadata are fixtures.
The secondary model's Weapon name and complete SceneInfo effects remain unknown.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .trace_client72_scene_info_raw import SceneInfoRaw72, declarations as raw_declarations, verify_sources
    from .trace_client72_avatar_middle_trees import inputs, message_list_aot, message, scalar
    from .trace_client72_avatar_outer import avatar_aot
    from .trace_client72_avatar_signed_trees import selected_aot
    from .client72_scene_entry_identity import declarations as identity_declarations
    from .extract_client72 import HASHES, PE, HEADER_VA, BODY, TypeResolver, number
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from .client72_session_runtime import fragment
    from .trace_client72_token import load_emulator
except ImportError:
    from trace_client72_scene_info_raw import SceneInfoRaw72, declarations as raw_declarations, verify_sources
    from trace_client72_avatar_middle_trees import inputs, message_list_aot, message, scalar
    from trace_client72_avatar_outer import avatar_aot
    from trace_client72_avatar_signed_trees import selected_aot
    from client72_scene_entry_identity import declarations as identity_declarations
    from extract_client72 import HASHES, PE, HEADER_VA, BODY, TypeResolver, number
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from client72_session_runtime import fragment
    from trace_client72_token import load_emulator

RETURN = END + 0x100
LIST, AVATAR_SET, SECOND_SET, NAMED = 0x1507E4900, 0x15184AEB0, 0x14EAAF060, 0x14E7712F0
PINS = {'scene-info-raw-observations.json': 'f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75',
    'scene-entry-identity-observations.json': '550fcdd642132598256aca41bc79eec89bdfb7009ad2d22ab7a82409484644d1',
    'avatar-contract-observations.json': 'a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3'}
NATIVE = {LIST, AVATAR_SET, SECOND_SET, NAMED, 0x1507D1620, 0x151846000, 0x15183E790,
    0x14AD08270, 0x1507F02C0, 0x14E9B2C60, 0x14E9B2A80, 0x14E9B1370,
    0x148AA58D0, 0x148AA58E0, 0x15184E020, 0x14EAAEF80, 0x152F80500,
    0x14CD68530, 0x1517B18F0, 0x1517ABD80, 0x153E46950, 0x1517AB3A0}
GENERIC = {0x1547E1DD0, 0x156F6F510, 0x156F6F850, 0x1572292B0}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for key in ('client', 'output', 'observations'): p.add_argument('--' + key, type=Path, required=True)
    p.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    a = p.parse_args(); paths = [a.output.resolve(), a.observations.resolve()]
    if len(set(paths)) != 2 or any(x.is_relative_to(a.client.resolve()) for x in paths): p.error('Distinct outputs outside client required')
    for name, digest in PINS.items():
        if hashlib.sha256((a.directory / name).read_bytes()).hexdigest() != digest: raise ValueError('Reviewed evidence changed: ' + name)
    b, m, s, records, cells, bindings, strings, items = inputs(a)
    inherited = verify_sources(a.directory)
    raw = raw_declarations(b, m, s, records, cells, a.python_libs)
    declared = declarations(b, m, s, records, cells, a.python_libs)
    declared['identity'] = identity_declarations(b, m, s, records)
    outer = avatar_aot(b, m, s, records, dict(types=[]))
    declared['listAot'] = message_list_aot(b, m, s, records, {12523: 18295})
    resolver = TypeResolver(PE(b), s, records, PE(b).offset(HEADER_VA))
    declared['dictionaryAot'] = [row for row in selected_aot(b, m, s, records,
        {(1222, i): ('FindEntry', 'TryInsert') for i in resolver.arguments}) if row['address'] in ('0x156f6f510', '0x156f6f850')]
    if {row['address'] for row in declared['dictionaryAot']} != {'0x156f6f510', '0x156f6f850'}: raise ValueError('U64 dictionary shared AOT changed')
    e = Offline72(b, a.python_libs, records, trace_instructions=False)
    graph = SceneInfoRaw72(e, a.directory, cells, bindings, items, strings, outer, raw)
    e.uc.mem_map(END, 4096); e.uc.mem_write(RETURN, b'\xc3')
    e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE, e._code, begin=END, end=END + 4095))
    try:
        good = [run_case(e, graph, case) for case in cases()]
        validate(good)
        bad = rejections(graph)
    finally:
        graph.close(); e.close()
    report = dict(schemaVersion=1, task='F-03d3s2a', inputSHA256=HASHES, inputContracts=PINS,
        inheritedRawEvidence=inherited, declarations=declared, fixtures=good, rejections=bad,
        semanticClaims=claims(), runtimeStatus='not-tested', fullNetworkHandler=False,
        fullSceneEntityEffects=False, completeSceneInfoRawClosure=False, targetProtocolReady=False,
        secondaryWeaponNameApproved=False, productionRequiredFieldSetApproved=False,
        models=['Controlled preexisting Avatar models, metadata/vtables, managed allocation and exact decoded-list enumerator layout',
            'UInt64 GUID hash/compare/insert and foreach MoveNext execute natively; no GUID dictionary Python lookup',
            'UInt32 model registry insert is captured at the managed dictionary boundary; scene entity lookup storage/index are fixtures',
            'Network feature set membership is an explicit one-key managed HashSet; native predicate and empty-list clear execute',
            'Ability inputs omitted and talent list empty for completed list cases; nonempty ability stops before retain/effect path',
            'No prior scene entity exists during list application; old entity teardown is an explicit stopped boundary',
            'Scene entity fixtures use the observed model entity IDs, not native entity construction',
            'Handler gates start at 1507d1735 and 1507d17d9 with explicit live-ins; earlier Team/ability handlers and later clearing are not executed',
            'Secondary factory allocation supplies controlled initialized objects; native constructors and live managed ABI are not executed'])
    report['sourceSHA256'] = {str(path).replace('\\', '/'): hashlib.sha256(path.read_bytes()).hexdigest() for path in map(Path, (
        'tools/client72_scene_info_contract.py', 'tools/trace_client72_scene_info_raw.py',
        'tools/client72_scene_entry_identity.py', 'tools/client72_emulator.py', 'tools/client72_graph.py', 'tools/client72_session_runtime.py'))}
    for path, value in zip(paths, (report, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8')
    print(json.dumps(dict(fixtures=len(good), rejections=len(bad), semanticClaims=len(report['semanticClaims']), fullNetworkHandler=False)))


def declarations(b, m, s, records, cells, libs):
    load_emulator(libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(b); h = pe.offset(HEADER_VA); resolver = TypeResolver(pe, s, records, h)
    mb = BODY + (number(b, h + 0xB0) ^ 0x51324A1F); pb = BODY + ((number(b, h + 0x174) + 0xAE6BCD68) & 0xffffffff)
    names = {int(method['registeredAddress'], 16): (row, method) for row in records for method in row['methods'] if method['registeredAddress'] != '0x0'}
    optional = number(b, 0x3c) + 24; rv, size = struct.unpack_from('<II', b, optional + 112 + 3 * 8)
    offset = pe.offset(pe.base + rv)
    unwind = {pe.base + start: pe.base + end for start, end, _ in struct.iter_unpack('<III', b[offset:offset + size])}
    ends = sorted(names); md = Cs(CS_ARCH_X86, CS_MODE_64); md.detail = True; methods = []; references = []
    for entry in sorted(NATIVE | GENERIC):
        end = unwind.get(entry, next(x for x in ends if x > entry)); code = b[pe.offset(entry):pe.offset(entry) + end - entry]
        row = dict(entry=hex(entry), extentEnd=hex(end), codeSHA256=hashlib.sha256(code).hexdigest())
        if entry in names:
            owner, method = names[entry]; row.update(ownerType=owner['index'], ownerName=owner['name'], method=method)
            first = parameter_start(method['index'], number(m, mb + method['index'] * 26 + 4))
            row['parameters'] = [resolver.resolve_index(parameter_words(i, number(m, pb + i * 8), number(m, pb + i * 8 + 4))[1]) for i in range(first, first + method['parameterCount'])]
        row['directCalls'] = []
        for ins in md.disasm(code, entry):
            if ins.mnemonic == 'call' and ins.operands[0].type == 2: row['directCalls'].append(dict(site=hex(ins.address), target=hex(ins.operands[0].imm)))
            for op in ins.operands:
                if op.type == 3 and op.mem.base == 41:
                    cell = ins.address + ins.size + op.mem.disp
                    if cell in cells: references.append(dict(entry=hex(entry), site=hex(ins.address), **cells[cell]))
        methods.append(row)
    indexed = {int(x['entry'], 16): x for x in methods}
    if indexed[LIST]['parameters'][0]['arguments'][0].get('typeDefinition') != 18295: raise ValueError('List consumer type changed')
    if indexed[NAMED]['method']['name'] != 'GetAvatarEntityId': raise ValueError('Named Avatar entity consumer changed')
    for entry in (AVATAR_SET, SECOND_SET):
        if indexed[entry]['parameters'][0]['kind'] != 'uint32': raise ValueError('Entity setter parameter changed')
    if cells[0x14589BC70]['type']['typeDefinition'] != 18163: raise ValueError('Avatar exact class changed')
    # Fresh all-group usage decode finds the concrete secondary factory at its actual callsite cell.
    lit = Literals72(b, m); cache = number(b, pe.offset(USAGE_REGISTRATION) + 0x10, 'Q')
    n = ((number(m, lit.groups_base + (lit.global_group + 1) * 4) + 0x8bff3e05) & 0xffffffff) ^ (((lit.global_group + 1) * 0x5843eaa0 - 0x443c8630) & 0xffffffff)
    factory = None; contexts = []
    for i in range(n):
        key = (i * 0x57c15286 + 0x26ddebda) & 0xffffffff
        src = ((number(m, lit.usages_base + i * 8) + 0xabbdcabf) & 0xffffffff) ^ key ^ 0x2a357d90
        slot = number(m, lit.usages_base + i * 8 + 4) ^ key ^ 0x1855aa65
        if src >> 29 != 6 or cache + slot * 8 not in (0x145994388, 0x145A8AC10, 0x145A8ABE8): continue
        spec = src & 0x1fffffff; method, cls, instance = struct.unpack_from('<iii', m, BODY + (number(b, h + 0x170) ^ 0x6347382b) + spec * 12)
        if instance == -1:
            contexts.append(dict(usageRow=i, methodSpec=spec, methodDefinition=method, classInstance=cls,
                classArguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]], cell=hex(cache + slot * 8)))
            continue
        count, pointer = struct.unpack_from('<QQ', b, pe.offset(resolver.instances + instance * 16))
        args = [resolver.resolve_index(resolver.pointer_index(number(b, pe.offset(pointer + j * 8), 'Q'))) for j in range(count)]
        factory = dict(usageRow=i, methodSpec=spec, methodDefinition=method, classInstance=cls, arguments=args, cell=hex(cache + slot * 8))
    if factory is None or factory['methodDefinition'] != 294224 or factory['arguments'][0].get('typeDefinition') != 51522: raise ValueError('Secondary factory target changed')
    if len(contexts) != 2 or {x['methodDefinition'] for x in contexts} != {10183}: raise ValueError('Managed set_Item contexts changed')
    return dict(methods=methods, typeUsages=references, secondaryFactory=factory, managedInsertContexts=contexts,
        hierarchy=[dict(typeIndex=i, parent=records[i]['parent']) for i in (18163, 51522, 25683, 72120)],
        modelFields=[dict(ownerType=i, **f) for i, offsets in ((18163, {0x218}), (89508, {0x28}), (72120, {0x38}), (25683, {0x88})) for f in records[i]['fields'] if f['offset'] in offsets])


class SceneContractRuntime:
    def __init__(self, e, graph, models):
        self.e = e; self.g = graph; self.models = {}; self.allocated = []; self.stores = []; self.lookups = []; self.registrations = []; self.scene_lookups = []
        for entry in NATIVE: e.allow(entry); e.models.pop(entry, None)
        for entry in GENERIC: e.allow_unwind(entry); e.models.pop(entry, None)
        self.avatar_class = self.new_class(); self.secondary_class = self.new_class()
        e.write(0x14589BC70, self.avatar_class)
        for klass, getter in ((self.avatar_class, 0x15184E020), (self.secondary_class, 0x14EAAEF80)):
            for off, addr in ((0x190, 0x148AA58D0), (0x198, 0x148AA58E0), (0x158, 0x152F80500), (0x160, getter)): e.write(klass + off, addr)
        info, context = e.alloc(), e.alloc(); e.write(info + 24, context); e.write(context + 16, self.secondary_class); e.write(0x145994388, info)
        self.manager = e.alloc(); self.network = e.alloc(1024); self.guid_dict = e.alloc()
        e.write(GLOBAL + 0xA650, self.manager); e.write(self.manager + 16, self.guid_dict)
        e.write(self.guid_dict + 16, graph.new_array(4)); e.write(self.guid_dict + 40, graph.new_array(24)); e.write(self.guid_dict + 0x44, 0xffffffff, 'I')
        self.entity_dict = e.alloc(); e.write(self.manager + 32, self.entity_dict)
        for cell in (0x145A8ABE8, 0x145A8AC10):
            info, klass, ctx = e.alloc(), e.alloc(), e.alloc(); e.write(info, klass); e.write(klass + 0x30, ctx); e.write(ctx + 0x88, e.alloc()); e.write(cell, info)
        e.models[0x1578B6A20] = self.register_entity
        e.models[0x1404C2EB0] = self.allocate
        self.feature = e.alloc(); e.write(GLOBAL + 0x219D0, self.feature); e.write(self.network + 0x1E4, 71, 'I')
        e.models[0x157D97D30] = self.contains
        e.synthetic_type(0x1458AE1A0)
        empty_scene = e.alloc(1024); self.old_entity_dictionary = e.alloc()
        e.write(empty_scene + 0x150, self.old_entity_dictionary); e.write(empty_scene + 0xc0, e.alloc())
        e.write(GLOBAL + 0xA3A0, empty_scene)
        self.original_lookup = e.models[0x1578B66F0]; e.models[0x1578B66F0] = self.lookup_old_entity
        e.write(GLOBAL + 0xBE90, e.alloc())
        listing = e.read(OBJECT + 24); klass = e.alloc(1024); e.write(listing, klass); e.write(klass + 0x230, e.model_stub(self.enumerate))
        self.insertions = []
        for guid, initial, kind in models:
            pointer = self.new_model(self.avatar_class if kind == 'avatar' else self.secondary_class)
            e.write(pointer + 0x38, guid); e.write(pointer + (0x218 if kind == 'avatar' else 0x28), initial, 'I'); self.models[guid] = pointer
            self.insertions.append(self.invoke(0x14E9B2A80, rcx=self.manager, rdx=pointer))
        self.hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, self.observe_store)
        self.code_hook = e.uc.hook_add(e.U.UC_HOOK_CODE, self.observe_code)

    def new_class(self):
        e = self.e; klass = e.alloc(1024); hierarchy = e.alloc(); e.write(klass + 0xc7, 1, 'B'); e.write(klass + 0xc8, 1, 'B')
        e.write(klass + 0xc4, 1, 'B'); e.write(klass + 0x48, hierarchy); e.write(hierarchy, klass); return klass

    def new_model(self, klass):
        e = self.e; pointer = e.alloc(1024); e.write(pointer, klass)
        e.write(pointer + 0x80, e.alloc()); e.write(pointer + 0x1d8, e.alloc()); return pointer

    def allocate(self):
        if self.e.reg('rcx') != self.secondary_class: raise ValueError('Unexpected scene model allocation')
        pointer = self.new_model(self.secondary_class); self.allocated.append(pointer); return pointer

    def register_entity(self):
        e = self.e
        if e.reg('rcx') != self.entity_dict: raise ValueError('Unexpected u32 registry')
        self.registrations.append(dict(entityId=e.reg('edx'), guid=e.read(e.reg('r8') + 0x38))); return 1

    def contains(self):
        if self.e.reg('rcx') != self.feature: raise ValueError('Unexpected feature set')
        return int(self.e.reg('edx') == 71)

    def lookup_old_entity(self):
        return 0xffffffff if self.e.reg('rcx') == self.old_entity_dictionary else self.original_lookup()

    def enumerate(self):
        e = self.e; target = e.reg('rcx'); back = e.read(e.reg('rdx') + 16)
        e.write(target, 1, 'B'); e.write(target + 8, e.read(back + 16)); e.write(target + 16, e.read(back + 24, 'I'), 'I'); e.write(target + 20, 0xffffffff, 'I'); return target

    def invoke(self, entry, stops=None, **regs):
        self.e.write(STACK + 0x100008, RETURN)
        try: return fragment(self.e, entry, stops or {RETURN}, dict(rsp=STACK + 0x100008, **regs))
        except self.e.U.UcError as error: raise ValueError(self.e.failure or str(error)) from error

    def observe_store(self, uc, access, address, size, value, data):
        source = self.e.reg('rip')
        if source not in (0x15184B069, 0x1507E4AC4, 0x1507E4ACF, 0x14EAAF086): return
        offset = {0x15184B069: 0x218, 0x1507E4AC4: 0x88, 0x1507E4ACF: 0x38, 0x14EAAF086: 0x28}[source]
        self.stores.append(dict(source=hex(source), size=size, value=value & ((1 << (size * 8)) - 1),
            objectOffset=offset, receiverGuid=self.e.read(address - offset + 0x38)))

    def observe_code(self, uc, address, size, data):
        if address == 0x14E9B2C60: self.lookups.append(self.e.reg('rdx'))
        if address == 0x1517AB3A0: self.scene_lookups.append(self.e.reg('edx'))

    def snapshot(self):
        e = self.e; out = []
        for pointer in list(self.models.values()) + self.allocated:
            avatar = e.read(pointer) == self.avatar_class
            out.append(dict(modelType=18163 if avatar else 51522, guid=e.read(pointer + 0x38),
                entityId=e.read(pointer + (0x218 if avatar else 0x28), 'I'), ownerAvatarGuid=None if avatar else e.read(pointer + 0x88)))
        return out

    def close(self):
        self.e.uc.hook_del(self.hook); self.e.uc.hook_del(self.code_hook)
        self.e.models[0x1578B66F0] = self.original_lookup

    def local_selection(self):
        e = self.e; scene = e.alloc(1024); entries = self.g.new_array(24); dictionary = e.alloc()
        e.write(dictionary + 24, entries); e.write(scene + 0x68, dictionary)
        e.write(GLOBAL + 0xA3A0, scene); e.write(GLOBAL + 0xA400, e.alloc(1024))
        klass = e.synthetic_type(0x145898D98); e.synthetic_type(0x145897948)
        entity_positions = {}; entities = []
        for guid, pointer in self.models.items():
            entity_id = e.read(pointer + 0x218, 'I')
            if e.read(pointer) != self.avatar_class or not entity_id: continue
            position = len(entity_positions); entity_positions[entity_id] = position
            entity = e.alloc(0x600); e.write(entity, klass); config = 10000005 + position * 2
            e.write(entity + 0x3f4, config, 'I'); e.write(entries + 48 + position * 24, entity)
            entities.append(dict(guid=guid, entityId=entity_id, configId=config))
        original = e.models[0x1578B66F0]
        def lookup():
            if e.reg('rcx') != dictionary: return original()
            return entity_positions.get(e.reg('edx'), 0xffffffff)
        e.models[0x1578B66F0] = lookup
        stores = []
        hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, lambda uc, access, address, size, value, data:
            stores.append(dict(source=hex(e.reg('rip')), size=size, value=value)), begin=scene + 0x284, end=scene + 0x287)
        try:
            manager = self.invoke(0x14CD68530, {0x153A20F30, 0x1404C2BE0}, rcx=e.read(GLOBAL + 0xBE90), rdx=OBJECT)
            local = self.invoke(0x153E46950, rcx=0)
            return dict(entities=entities, manager=manager, local=local, stores=stores, storedEntityId=e.read(scene + 0x284, 'I'),
                configId=e.reg('eax'), sceneEntityConstruction='controlled-fixture-not-native', zeroHandlerGuardBypassed=e.read(OBJECT + 48, 'I') == 0)
        finally: e.models[0x1578B66F0] = original; e.uc.hook_del(hook)


def cases():
    a = (9007199254740993, 0x80000001, 0xffffffffffffffff, 0xffffffff)
    b = (0x8000000000000000, 0xffffffff, 9007199254740995, 0x80000001)
    def row(name, entries, current=0x80000001, models=None, **extra):
        wire = scalar(12, current) + scalar(7, 73)
        for guid, entity, secondary, second_entity in entries:
            child = b''.join(scalar(tag, value) for tag, value in ((13, guid), (4, entity), (10, secondary), (7, second_entity)))
            wire += message(2, child)
        return dict(name=name, wire=wire.hex(), entries=entries, current=current,
            models=models if models is not None else [(x[0], 0, 'avatar') for x in entries if x[0]], **extra)
    good = [row('independent-high-pair', [a]), row('two-distinct-records', [a, b], current=b[1]),
        row('two-records-reversed', [b, a], current=a[1]),
        row('swap-two-entity-fields', [(a[0], a[3], a[2], a[1])], current=a[3]),
        row('swap-two-guid-fields-with-distinct-models', [(a[2], a[1], a[0], a[3])], models=[(a[2], 0, 'avatar')]),
        row('same-low32-distinct-full-guids', [(0x100000001, 11, 0x300000001, 33), (0x200000001, 22, 0x400000001, 44)], current=22),
        row('u64-bit63-and-u32-max', [(1 << 63, 0xffffffff, 0xffffffffffffffff, 1)], current=0xffffffff),
        row('zero-entity-ids', [(a[0], 0, a[2], 0)], current=0),
        row('zero-avatar-guid-not-registered', [(0, 11, a[2], 22)], current=11),
        row('missing-avatar-stops-whole-list', [a, b], models=[(b[0], 0, 'avatar')], current=b[1]),
        row('wrong-model-class-stops-whole-list', [a, b], models=[(a[0], 0, 'secondary'), (b[0], 0, 'avatar')], current=b[1]),
        row('empty-list', [], current=0),
        row('duplicate-avatar-last-entity', [a, (a[0], 33, 123, 44)], current=33, models=[(a[0], 0, 'avatar')]),
        row('secondary-existing-model-reused', [a], models=[(a[0], 0, 'avatar'), (a[2], 0, 'secondary')]),
        row('explicit-zero-clears-both-old-ids', [(a[0], 0, a[2], 0)], current=0, models=[(a[0], 91, 'avatar'), (a[2], 92, 'secondary')]),
        row('zero-secondary-guid-remains-unregistered', [(a[0], 11, 0, 22)], current=11)]
    omitted = row('all-four-child-fields-omitted', [], current=0, models=[]); omitted['wire'] += '1200'; good.append(omitted)
    zero = row('duplicate-four-scalars-explicit-zero', [a], current=0)
    zero['wire'] = (scalar(12, 0) + message(2, b''.join(scalar(t, v) + scalar(t, 0) for t, v in ((13, a[0]), (4, a[1]), (10, a[2]), (7, a[3]))))).hex(); good.append(zero)
    wrong = row('wrong-wire-guid-skipped', [], current=1); wrong['wire'] += '120b6a00510100000000000000'; good.append(wrong)
    for name, omitted in [('avatar-id-omitted-clears-old', 4), ('secondary-id-omitted-clears-old', 7),
                          ('avatar-guid-omitted-stops', 13), ('secondary-guid-omitted-zero-model', 10)]:
        sample = row(name, [a], models=[(a[0], 91, 'avatar'), (a[2], 92 if omitted == 7 else 0, 'secondary')])
        child = b''.join(scalar(t, v) for t, v in ((13, a[0]), (4, a[1]), (10, a[2]), (7, a[3])) if t != omitted)
        sample['wire'] = (scalar(12, a[1]) + message(2, child)).hex(); good.append(sample)
    root_omitted = row('local-id-omitted', [a]); root_omitted['wire'] = root_omitted['wire'][12:]; good.append(root_omitted)
    nonempty = row('nonempty-avatar-ability-boundary', [a]); child = b''.join(scalar(t, v) for t, v in ((13, a[0]), (4, a[1]), (10, a[2]), (7, a[3]))) + message(8, b'\x08\x01')
    nonempty['wire'] = (scalar(12, a[1]) + message(2, child)).hex(); nonempty['boundary'] = 0x151846096; good.append(nonempty)
    literal = '6081808080083849122068818080808080801020818080800850ffffffffffffffffff0138ffffffff0f'
    if good[0]['wire'] != literal: raise ValueError('Independent fixed identity literal differs')
    good[0]['wire'] = literal
    return good


def run_case(e, graph, case):
    e.before_instruction = graph.observe_entry
    decoded = graph.run_graph(34208, bytes.fromhex(case['wire']))
    rt = SceneContractRuntime(e, graph, case['models'])
    try:
        stops = {RETURN, 0x1404C2BE0, 0x151846075, 0x151846096}
        trace = rt.invoke(LIST, stops, rcx=rt.network, rdx=e.read(OBJECT + 24))
        snapshot = rt.snapshot(); getters = []; secondary_getters = []
        for pointer in list(rt.models.values()) + rt.allocated:
            if e.read(pointer) != rt.secondary_class: continue
            guid = e.read(pointer + 0x38)
            lookup = rt.invoke(0x14E9B2C60, rcx=rt.manager, rdx=guid); found = e.reg('rax') == pointer
            getter = rt.invoke(0x14EAAEF80, rcx=pointer)
            secondary_getters.append(dict(guid=guid, entityId=e.reg('eax'), trace=getter, registryLookup=lookup, registryFound=found))
        for guid, pointer in rt.models.items():
            if e.read(pointer) != rt.avatar_class: continue
            button = e.alloc(512); e.write(button + 0x178, guid)
            getter = rt.invoke(NAMED, rcx=button)
            if e.reg('eax') != e.read(pointer + 0x218, 'I'): raise ValueError('Named Avatar entity getter differs')
            getters.append(dict(guid=guid, entityId=e.reg('eax'), trace=getter))
        handler = None
        if trace['stop'] == hex(RETURN):
            handler = dict(listGate=rt.invoke(0x1507D1735, {LIST, 0x147161B60, 0x1404C2BE0}, rsi=OBJECT, rdi=rt.network),
                localIdGate=rt.invoke(0x1507D17D9, {0x14CD68530, 0x1507D1918, 0x1404C2BE0}, rsi=OBJECT, rdi=rt.network))
        selection = rt.local_selection() if trace['stop'] == hex(RETURN) else None
        return dict(name=case['name'], wire=case['wire'], inputModels=case['models'], reader=decoded,
            listTrace=trace, insertions=rt.insertions, stores=rt.stores, guidLookups=rt.lookups,
            modelSnapshot=snapshot, getters=getters, secondaryGetters=secondary_getters, entityRegistrations=rt.registrations, localSelection=selection, oldSceneEntityLookups=rt.scene_lookups,
            handlerSuffix=handler, expectedBoundary=hex(case.get('boundary', RETURN)))
    finally: rt.close()


def rejections(graph):
    rejected = []
    for name, wire in [('invalid-root-tag', '00'), ('truncated-child', '12030801'),
                       ('u64-overflow', '120b68ffffffffffffffffff02')]:
        graph.e.before_instruction = graph.observe_entry
        try: graph.run_graph(34208, bytes.fromhex(wire))
        except ValueError as error: rejected.append(dict(name=name, wire=wire, error=str(error),
            scope='native-error-entry' if 'Native error branch:' in str(error) else 'explicit-primitive-input-model'))
        else: raise ValueError('Invalid Scene contract wire accepted')
    return rejected


def validate(rows):
    for row in rows:
        if row['listTrace']['stop'] != row['expectedBoundary']: raise ValueError('Unexpected consumer stop: ' + row['name'])
        traces = [row['listTrace'], *row['insertions'], *[x['trace'] for x in row['getters'] + row['secondaryGetters']]]
        if any({hex(x) for x in NATIVE | GENERIC} & set(t['models']) for t in traces): raise ValueError('Critical business method modeled')
        for read in row['getters'] + row['secondaryGetters']:
            if not any(x['guid'] == read['guid'] and x['entityId'] == read['entityId'] for x in row['modelSnapshot']): raise ValueError('Actual getter differs from model store')
        if row['localSelection']:
            selection = row['localSelection']; expected = row['reader']['result'].get('OLCJCLBMMCF', 0)
            config = next((x['configId'] for x in selection['entities'] if x['entityId'] == expected), 0)
            if (selection['storedEntityId'], selection['configId']) != (expected, config): raise ValueError('Local entity selection differs')
            if selection['manager']['stop'] != '0x153a20f30': raise ValueError('Scene setter did not reach effect boundary')
            if selection['stores'] != [dict(source='0x1517b1904', size=4, value=expected)]: raise ValueError('Local entity store source differs')
    by = {x['name']: x for x in rows}
    a = by['independent-high-pair']
    if [(x['modelType'], x['guid'], x['entityId'], x['ownerAvatarGuid']) for x in a['modelSnapshot']] != [
        (18163, 9007199254740993, 2147483649, None), (51522, 18446744073709551615, 4294967295, 9007199254740993)]: raise ValueError('Independent identity association differs')
    for name in ('zero-avatar-guid-not-registered', 'missing-avatar-stops-whole-list', 'wrong-model-class-stops-whole-list',
                 'all-four-child-fields-omitted', 'duplicate-four-scalars-explicit-zero', 'wrong-wire-guid-skipped', 'avatar-guid-omitted-stops'):
        if by[name]['stores']: raise ValueError('Missing/wrong Avatar unexpectedly applied: ' + name)
    for name in ('two-distinct-records', 'two-records-reversed'):
        actual = {(x['modelType'], x['guid']): (x['entityId'], x['ownerAvatarGuid']) for x in by[name]['modelSnapshot']}
        if actual != {(18163, 9007199254740993): (2147483649, None), (18163, 9223372036854775808): (4294967295, None),
                      (51522, 18446744073709551615): (4294967295, 9007199254740993), (51522, 9007199254740995): (2147483649, 9223372036854775808)}: raise ValueError('List identity cross-wired: ' + name)


def claims():
    return [dict(typeIndex=18295, tag=13, clientField='OKMEHFHNFCG', type='uint64', purpose='Avatar model GUID lookup key',
                 source='1507e49e1 -> 14e9b2c60 -> exact class18163; independent MonoTeamBtn.GetAvatarEntityId'),
        dict(typeIndex=18295, tag=4, clientField='FDDDFIAINCE', type='uint32', purpose='Avatar entity ID',
             source='15184aeb0 store +218; named 14e7712f0 reads +218 by full GUID'),
        dict(typeIndex=18295, tag=10, clientField='NPKKCHABEJH', type='uint64', purpose='Secondary type51522 model GUID; Weapon name unresolved',
             source='concrete generic factory then own +38 and owner Avatar GUID +88 stores'),
        dict(typeIndex=18295, tag=7, clientField='IEDJNHILJBF', type='uint32', purpose='Secondary type51522 entity ID; Weapon name unresolved',
             source='14eaaf060 store +28; inherited entity getter and old-scene entity lookup')]


def compact(value):
    if isinstance(value, list): return [compact(x) for x in value]
    if not isinstance(value, dict): return value
    return {key: compact(item) for key, item in value.items() if key not in ('executedAddresses', 'liveIn', 'nestedCalls')}


if __name__ == '__main__': main()
