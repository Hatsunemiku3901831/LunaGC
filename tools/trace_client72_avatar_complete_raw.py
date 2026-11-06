"""F-03d3e8: dual Object51050 and complete nonempty raw Avatar graph.

Raw closure only; business semantics and live/runtime acceptance stay separate.
"""
import copy
import hashlib
import struct
try:
    from .trace_client72_avatar_multi_lists import (AvatarMultiLists72, message_list_aot, arguments, inputs, write_reports, message, scalar,
        initialize_types, dependency_audit, Offline72, HASHES, json, varint)
    from .trace_client72_avatar_multi_lists import fixtures as multi_fixtures
    from .trace_client72_avatar_middle_trees import fixtures as middle_fixtures
    from .trace_client72_avatar_unions import fixtures as union_fixtures
    from .trace_client72_avatar_primitive_leaves import AvatarPrimitiveLeaves72, leaf_lists_aot, fixtures as primitive_fixtures
    from .trace_client72_avatar_outer import AvatarOuter72, avatar_aot, fixtures as outer_fixtures
    from .trace_client72_avatar_complex_outer import fixtures as complex_fixtures
    from .trace_client72_avatar_items import fixtures as item_fixtures
    from .client72_avatar_contract import CombinedMessages
    from .client72_graph import GraphReader72
    from .client72_emulator import GLOBAL, STREAM
    from .client72_reader import WireInput
except ImportError:
    from trace_client72_avatar_multi_lists import (AvatarMultiLists72, message_list_aot, arguments, inputs, write_reports, message, scalar,
        initialize_types, dependency_audit, Offline72, HASHES, json, varint)
    from trace_client72_avatar_multi_lists import fixtures as multi_fixtures
    from trace_client72_avatar_middle_trees import fixtures as middle_fixtures
    from trace_client72_avatar_unions import fixtures as union_fixtures
    from trace_client72_avatar_primitive_leaves import AvatarPrimitiveLeaves72, leaf_lists_aot, fixtures as primitive_fixtures
    from trace_client72_avatar_outer import AvatarOuter72, avatar_aot, fixtures as outer_fixtures
    from trace_client72_avatar_complex_outer import fixtures as complex_fixtures
    from trace_client72_avatar_items import fixtures as item_fixtures
    from client72_avatar_contract import CombinedMessages
    from client72_graph import GraphReader72
    from client72_emulator import GLOBAL, STREAM
    from client72_reader import WireInput

TYPE = 51050
CHILDREN = {57431, 63939, 87867, 22404, 18849}
OUTER_SHA = '18ffa869ee1ebb7d73e028727aa1b424d0f1ce641530d73c7984696cf38093b8'
SOURCE_HASHES = {
    'outer': OUTER_SHA, 'core': '81a0411e8fe6b7cc3ca606473ae684744a8df73e97009666df1c2775e9bdb3d4',
    'trees': 'e6c348533cc99c7f8af291ef3e019efdbfa21c60f3152a498c8c8b68024f9789',
    'items': 'efe98c6b3d1aeb3a4e063025472a830cc5b8c49b76476f9e8bd1675cd9c4c1ba',
    'complex-outer': 'fa5559ebad448331432dcb9e42701df71bb4531cf9dee56fd64e290aa0129312',
    'unions': 'ba1512e8de4a694813a8f5b57bc121f9a2827e94a746c0434a7459a65ecb485f',
    'primitive-leaves': 'd8369121b9cabe4603c646e2d7acab265737e54256a5d5dd7e6e42be33e8fcaf',
    'signed-trees': '6ec19e0bda44d22b2083784602d05ead27bf5c27d5f5c3bd12e8a70b0b056ee0',
    'middle-trees': '8481fb5e67f760d1464d448846b502a9069b0ff6c7b9e0101df109c80893e193',
    'large-nodes': 'be201804786cd4eb955f7418ce9d22caa851c113f290b618206fca94f21a9bf5',
    'multi-lists': '51d4ef4aa1d4caa495d02b460c1c82c25d1bebea665ad27daf58e41742dc33f6'}


class AvatarCompleteRaw72(AvatarMultiLists72):
    def __init__(self, engine, directory, cells, bindings, items, strings, outer_bindings):
        super().__init__(engine, directory, cells, bindings, items, strings); e = self.e
        # Reuse51050 class already captured by34045's native list codec.
        fields = []
        for f in e.records[TYPE]['fields']:
            if f['offsetKind'] != 'instance' or f['offset'] in (40, 56, 92, 100): continue
            typ = f['type']; field = dict(clientField=f['name'], objectOffset=f['offset'], type=typ['kind'], cardinality='singular')
            if typ['kind'] == 'class': field.update(type='message', nestedTypeIndex=typ['typeDefinition'])
            elif typ['kind'] == 'genericinst':
                child = typ['arguments'][0]; field.update(type=child['kind'], cardinality='repeated')
                if child['kind'] == 'class': field.update(type='message', nestedTypeIndex=child['typeDefinition'])
                else: field['packed'] = True
            elif typ['kind'] == 'valuetype': field.update(type='int32', enumTypeIndex=typ['typeDefinition'])
            fields.append(field)
        fields += [dict(clientField='IPECOOBLNDN_' + str(tag), objectOffset=56, type='uint32', cardinality='singular',
            oneof='raw_object_38', discriminatorOffset=100, discriminatorValue=tag) for tag in (1, 2)]
        fields.append(dict(clientField='IDBPHIMMHJP_100', objectOffset=40, type='message', cardinality='singular', nestedTypeIndex=18849,
            oneof='raw_object_28', discriminatorOffset=92, discriminatorValue=100))
        self.schemas[TYPE] = dict(typeIndex=TYPE, clientType=e.records[TYPE]['name'], fields=fields)
        self.complete_initializers = initialize_types(self, {TYPE}); e.allow(0x151509DE0)
        source = (directory / 'avatar-outer-observations.json').read_bytes()
        if hashlib.sha256(source).hexdigest() != OUTER_SHA: raise ValueError('Complete Avatar outer evidence drift')
        for row in json.loads(source)['messages']:
            row = copy.deepcopy(row)
            for f in row['fields']: f.pop('contentStatus', None)
            self.schemas[row['typeIndex']] = row
        for index in (30509, 26081, 14786):
            entry = next(int(m['registeredAddress'], 16) for m in e.records[index]['methods'] if m['name'] == 'MOMMACBEMEK')
            klass = e.alloc(1024); table = e.alloc(16); e.write(klass + 0xC7, 1, 'B'); e.write(klass + 0x80, table)
            e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H'); e.write(table, self.interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
        for cell, row in cells.items():
            if row['type']['kind'] == 'class' and row['type'].get('typeDefinition') in (30509, 26081, 14786): e.write(cell, self.classes[row['type']['typeDefinition']])
        core = self.maps.core; saved = {t: core.message.config[t]['codec'] for t in (3, 10)}
        combined = CombinedMessages(self, directory, bindings + outer_bindings); core.message = combined; core.parts[0] = combined
        core.static_values.update(combined.static_values)
        for tag, codec in saved.items(): combined.config[tag]['codec'] = codec; e.write(GLOBAL + combined.config[tag]['static'], codec)
        e.models[0x1578B66F0] = lambda: self.maps.dictionary_owner().lookup()
        # Keep the existing Item-copy dictionary count wrapper. Its captured
        # router selects the updated core.parts dynamically, including Team.
        e.models[0x1578B6A20] = self.item_copy.insert
        self.codec_classes.add(e.synthetic_type(0x1458C5AA8)); self.complete_initializers += initialize_types(self, {30509, 26081})
        combined.config[5]['codec'] = e.read(GLOBAL + 0x22800)
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index != TYPE: return super().new_message(index, address)
        pointer = GraphReader72.new_message(self, index, address); container = self.e.read(pointer + 80); array = self.new_array(4)
        self.e.write(container, self.float_list_class); self.e.write(container + 16, array)
        self.containers[container].update(width=4, capacity=64, primitiveFloat=True)
        return pointer

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] in (26081, 14786): return AvatarOuter72.snapshot(self, pointer, depth)
        if not pointer or self.objects[pointer] != TYPE: return super().snapshot(pointer, depth)
        result = GraphReader72.snapshot(self, pointer, depth); e = self.e
        discriminator = e.read(pointer + 100, 'I')
        if discriminator:
            if discriminator not in (1, 2): raise ValueError('Complex boxed-u32 discriminator drift')
            boxed = self.values[e.read(pointer + 56)]
            if boxed['kind'] != 'uint32': raise ValueError('Complex Object is not boxed-u32')
            result['IPECOOBLNDN_' + str(discriminator)] = struct.unpack('<I', bytes.fromhex(boxed['hex']))[0]
        container = e.read(pointer + 80); count = e.read(container + 24, 'I'); array = e.read(container + 16)
        if count: result['KGDFJBJMNNJ'] = [e.read(array + 32 + i * 4, 'f') for i in range(count)]
        return result

    def observe_entry(self, address):
        if self.active and address == self.entries[TYPE]: return GraphReader72.observe_entry(self, address)
        # Full graphs also allocate map adapters (e.g.17964), which have no
        # message schema; restrict field attribution to actual message owners.
        if self.active and address in (0x1568B8D60, 0x158DB8E10, 0x1571DDD60):
            for pointer, index in self.objects.items():
                if index not in self.schemas: continue
                for field in self.schemas[index]['fields']:
                    if field['cardinality'] == 'repeated' and field['type'] in ('int32', 'uint32', 'uint64') and self.e.read(pointer + field['objectOffset']) == self.e.reg('rcx'):
                        self.entered.append(dict(kind='native-signed-tree-list-field', ownerType=index, clientField=field['clientField'], objectOffset=field['objectOffset'], key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
            return AvatarPrimitiveLeaves72.observe_entry(self, address)
        if self.active and address == 0x151509DE0: self.entered.append(dict(kind='native-18849-copy', entry=hex(address)))
        if self.active and address == 0x1571DC270:
            for pointer, index in self.objects.items():
                if index == TYPE and self.e.read(pointer + 80) == self.e.reg('rcx'):
                    self.entered.append(dict(kind='native-complex-float-list', ownerType=TYPE, clientField='KGDFJBJMNNJ', objectOffset=80, key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
        return super().observe_entry(address)

    def run_graph(self, index, data, recursion_limit=100):
        row = super().run_graph(index, data, recursion_limit); e = self.e
        row['complexFloatListBits'] = [[hex(e.read(e.read(e.read(pointer + 80) + 16) + 32 + n * 4, 'I')) for n in range(e.read(e.read(pointer + 80) + 24, 'I'))] for pointer, i in self.objects.items() if i == TYPE]
        return row


def scan_raw(graph):
    selected = []; observed = {}
    for tag in range(1, 129):
        for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes.fromhex('0000a03f'))):
            row = graph.run_graph(TYPE, varint(tag << 3 | wire) + payload)
            calls = [x for x in row['entered'] if x.get('ownerType') == TYPE and x.get('kind') in ('native-signed-tree-list-field', 'native-complex-float-list')]
            if row['result'] or row['stores'] or calls:
                names = list(row['result']) or [x['clientField'] for x in calls]
                if len(set(names)) != 1: raise ValueError('Ambiguous complete Object branch: ' + str((tag, wire, names)))
                observed.setdefault(names[0], []).append((tag, wire)); selected.append(dict(typeIndex=TYPE, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
    for field in graph.schemas[TYPE]['fields']:
        bindings = observed.pop(field['clientField'], [])
        if not bindings or len({tag for tag, _ in bindings}) != 1: raise ValueError('Missing complex Object field')
        wires = sorted(w for _, w in bindings); expected = [2] if field['type'] == 'message' else [2, 5] if field['type'] == 'float' else [0, 2] if field['cardinality'] == 'repeated' else [0]
        if wires != expected: raise ValueError('Complex Object metadata/wire mismatch')
        field.update(tag=bindings[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
    if observed or len(graph.schemas[TYPE]['fields']) != 11: raise ValueError('Complex Object field count drift')
    return dict(nativeFixtures=512, scope='type51050 tags1..128/wire0,1,2,5; 12 storage slots, 2 discriminator slots, 3 Object branches, 11 wire fields', fields=selected)


def without_tags(wire, tags):
    source = WireInput(wire); result = bytearray()
    while source.position < len(wire):
        start = source.position; key = source.tag(); source.skip()
        if key >> 3 not in tags: result.extend(wire[start:source.position])
    return bytes(result)


def fixtures():
    known = {x['name']: x for x in middle_fixtures() + multi_fixtures() + primitive_fixtures() + union_fixtures() + complex_fixtures() + item_fixtures() + outer_fixtures()}
    full_wire = scalar(2, 4294967295) + scalar(3, 33) + scalar(10, -1)
    full = {'IPECOOBLNDN_2': 4294967295, 'HLCHKOOIDIF': 33, 'OKDKGHLCKDL': -1}
    for tag, index, name in [(7, 22404, 'GACCOGGKOOC'), (8, 87867, 'KFELKFDIMCO'), (9, 57431, 'BEDHPILKHEH')]:
        case = known['all-fields-' + str(index)]; full_wire += message(tag, bytes.fromhex(case['wire'])); full[name] = case['expected']
    child = known['all-fields-63939']; full_wire += message(6, bytes.fromhex(child['wire'])) + message(6, scalar(1, 9007199254740993))
    full['KCBHOJACCKP'] = [child['expected'], {'ANKEOLKBECN': 9007199254740993}]
    leaf = known['all-fields-18849']; full_wire += message(100, bytes.fromhex(leaf['wire'])); full['IDBPHIMMHJP_100'] = leaf['expected']
    full_wire += scalar(4, 0) + message(4, bytes.fromhex('01ffffffff0f')); full['FEKDEEENNBB'] = [0, 1, 4294967295]
    full_wire += bytes.fromhex('5d0000a0bf5a0c000020400000008000000000'); full['KGDFJBJMNNJ'] = [-1.25, 2.5, -0.0, 0.0]
    upper_case = known['34045-all-six-fields-nonempty-upper-tree']; upper_wire = without_tags(bytes.fromhex(upper_case['wire']), {5}) + message(5, full_wire)
    upper = copy.deepcopy(upper_case['expected']); upper['PPDFGNJDAAE'] = [full]
    complex_case = known['53824-all-12-fields-nonempty-leaves']; complex_wire = without_tags(bytes.fromhex(complex_case['wire']), {7}) + message(7, upper_wire)
    complex_result = copy.deepcopy(complex_case['expected']); complex_result['KDKAEHEEMMJ'] = upper
    avatar_case = known['AvatarInfo-all-37-fields-three-nonempty-child-trees']; avatar_wire = without_tags(bytes.fromhex(avatar_case['wire']), {37}) + message(37, complex_wire)
    avatar = copy.deepcopy(avatar_case['expected']); avatar['AMLLHJJIMNI'] = [complex_result]
    root_case = known['all-allowed-outer-content']; root_wire = without_tags(bytes.fromhex(root_case['wire']), {6}) + message(6, avatar_wire)
    root = copy.deepcopy(root_case['expected']); root['JGECGMPNGBD'] = [avatar]
    cases = [dict(name='51050-all-active-fields-nonempty', typeIndex=TYPE, wire=full_wire.hex(), expected=full),
        dict(name='53824-complete-all-nonempty', typeIndex=53824, wire=complex_wire.hex(), expected=complex_result),
        dict(name='AvatarInfo-complete-all-37-nonempty', typeIndex=48178, wire=avatar_wire.hex(), expected=avatar),
        dict(name='Avatar-complete-all-10-nonempty', typeIndex=30509, wire=root_wire.hex(), expected=root),
        dict(name='boxed-u32-first-upper', typeIndex=TYPE, wire='08ffffffff0f', expected={'IPECOOBLNDN_1': 4294967295}),
        dict(name='boxed-u32-second-nonzero', typeIndex=TYPE, wire='1007', expected={'IPECOOBLNDN_2': 7}),
        dict(name='boxed-u32-first-explicit-zero', typeIndex=TYPE, wire='0800', expected={'IPECOOBLNDN_1': 0}),
        dict(name='boxed-u32-second-explicit-zero', typeIndex=TYPE, wire='1000', expected={'IPECOOBLNDN_2': 0}),
        dict(name='boxed-switch-first-second-first-zero', typeIndex=TYPE, wire='080110020800', expected={'IPECOOBLNDN_1': 0}),
        dict(name='two-objects-switch-independent', typeIndex=TYPE,
            wire=(scalar(1, 11) + message(100, bytes.fromhex(leaf['wire'])) + scalar(2, 22)).hex(), expected={'IPECOOBLNDN_2': 22, 'IDBPHIMMHJP_100': leaf['expected']})]
    # Fixed independently authored u32 lists: [1,128] and [2,255].
    repeated_wire = message(100, bytes.fromhex('0a03018001120302ff01')) + message(100, bytes.fromhex('0a0107120108')) + message(100, b'')
    cases.append(dict(name='tag100-repeated-copy-merge-and-empty-preserves', typeIndex=TYPE, wire=repeated_wire.hex(), expected={'IDBPHIMMHJP_100': {'ABBODGKNDEP': [1, 128, 7], 'KGMKPEBKEFF': [2, 255, 8]}}))
    cases += [dict(name='tag100-explicit-empty-active', typeIndex=TYPE, wire=message(100, b'').hex(), expected={'IDBPHIMMHJP_100': {}}),
        dict(name='complex-float-packed-unpacked-bits', typeIndex=TYPE, wire='5d0000a0bf5a100000204000000080ffff7f7f01000000', expected={'KGDFJBJMNNJ': [-1.25, 2.5, -0.0, 3.4028234663852886e38, 1.401298464324817e-45]}),
        dict(name='complex-float-empty-packed-preserves', typeIndex=TYPE, wire='5d0000a0bf5a00', expected={'KGDFJBJMNNJ': [-1.25]}),
        dict(name='complex-u32-packed-unpacked', typeIndex=TYPE, wire='2000220601ffffffff0f', expected={'FEKDEEENNBB': [0, 1, 4294967295]}),
        dict(name='complex-unknown-wrong-wire-skipped', typeIndex=TYPE, wire=(message(1, b'') + scalar(100, 7) + scalar(128, 9)).hex(), expected={}),
        dict(name='complex-scalar-duplicate-zero', typeIndex=TYPE, wire='1801180050015000', expected={}),
        dict(name='full-root-two-avatar-nonempty-list', typeIndex=30509,
            wire=(message(6, avatar_wire) + message(6, scalar(1, 22) + scalar(2, 18446744073709551615))).hex(), expected={'JGECGMPNGBD': [avatar, {'NLNJDCDLHHE': 22, 'FBLKPHOCBIK': 18446744073709551615}]}),
        dict(name='root-team-map-and-avatar-maps-interleave', typeIndex=30509,
            wire=(bytes.fromhex('2a110807120d12015462088180808080808010') + message(6, bytes.fromhex('1a060807120208013a070807150000a0bf52080807120408031004')) + bytes.fromhex('2a0708071203120142')).hex(),
            expected={'CNCCIILAJNJ': {'7': {'MGIDEJKCAKD': 'B'}}, 'JGECGMPNGBD': [{'IKIFIBAEDGD': {'7': {'NOEEBKJAFAG': 1}}, 'ADJINCFKMML': {'7': -1.25}, 'HLAIJJEMIHE': {'7': {'GMKNIJBNFLK': 3, 'ABAAMEKEIFJ': [4]}}}]})]
    cases.append(known['reused-same-item-six-nested-merge-and-bool-zero'])
    for index in (TYPE, 53824, 48178, 30509): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': complete raw native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    for name, index, wire, scope, limit in [('invalid-tag', TYPE, '00', 'explicit-input-model', 100),
        ('boxed-u32-overflow', TYPE, '08ffffffffffffffffff02', 'explicit-input-model', 100),
        ('complex-float-packed-truncated', TYPE, '5a020000', 'explicit-input-model', 100),
        ('complex-u32-packed-truncated', TYPE, '220180', 'explicit-input-model', 100),
        ('negative-tag100-length', TYPE, 'a206ffffffff0f', 'native-error-entry', 100),
        ('truncated-tag100', TYPE, 'a206030801', 'native-error-entry', 100),
        ('tag100-recursion-limit', TYPE, 'a20600', 'native-error-entry', 0),
        ('full-root-recursion-limit', 30509, '3200', 'native-error-entry', 0),
        ('deep-complex-recursion-limit', 53824, '3a042a02a206', 'native-error-entry', 1)]:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            text = str(error)
            if scope == 'native-error-entry' and 'Native error branch:' not in text: raise
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=text))
        else: raise ValueError('Invalid complete raw input accepted: ' + name)
    return good, rejected


def main():
    a, paths = arguments(__doc__); b, m, s, records, cells, bindings, strings, items = inputs(a)
    sources = {}
    for name, digest in SOURCE_HASHES.items():
        raw = (a.directory / ('avatar-' + name + '-observations.json')).read_bytes()
        if hashlib.sha256(raw).hexdigest() != digest: raise ValueError('Reused raw evidence drift: ' + name)
        sources[name] = json.loads(raw)
    aot = message_list_aot(b, m, s, records, {37610: 63939}); outer = avatar_aot(b, m, s, records, dict(types=[]))
    primitive_aot = leaf_lists_aot(b, m, s, records)
    audit = dependency_audit(b, records, cells, {TYPE}, CHILDREN, 12, a.python_libs)
    e = Offline72(b, a.python_libs, records, trace_instructions=False); graph = AvatarCompleteRaw72(e, a.directory, cells, bindings, items, strings, outer)
    wrapper = next(x for x in primitive_aot if x['address'] == '0x158db87c0'); offset = e.pe.offset(0x158DB87C0)
    if b[offset] != 0xE9 or 0x158DB87C5 + struct.unpack_from('<i', b, offset + 1)[0] != 0x158DB7CD0: raise ValueError('Concrete u32 Add-range wrapper drift')
    copy_method = next(x for x in records[18849]['methods'] if x['registeredAddress'] == '0x151509de0')
    try: scan = scan_raw(graph); good, rejected = verify(graph)
    finally: graph.close(); e.close()
    messages = {row['typeIndex']: copy.deepcopy(row) for source in sources.values() for row in source['messages']}; messages[TYPE] = graph.schemas[TYPE]
    expected = {row['typeIndex'] for row in sources['outer']['dependencyAudit']['types']}
    complex_types = {row['typeIndex'] for name in ('complex-outer', 'unions', 'primitive-leaves', 'signed-trees', 'middle-trees', 'large-nodes', 'multi-lists') for row in sources[name]['messages']} | {TYPE}
    closure = []
    for name, wanted in [('53824-complete-all-nonempty', complex_types), ('AvatarInfo-complete-all-37-nonempty', expected - {30509, 26081, 14786}), ('Avatar-complete-all-10-nonempty', expected)]:
        row = next(x['observation'] for x in good if x['name'] == name)
        entered = {x.get('typeIndex') for x in row['entered']} & expected
        if entered != wanted or 'untracedType' in json.dumps(row['result']): raise ValueError('Incomplete native raw closure: ' + name)
        closure.append(dict(fixture=name, enteredMessageTypeIndices=sorted(entered), messageCount=len(entered)))
    if set(messages) != expected or len(expected) != 65 or len(complex_types) != 34: raise ValueError('Full Avatar schema closure drift')
    for row in messages.values():
        for field in row['fields']:
            if 'contentStatus' in field: field['contentStatus'] = 'nonempty-native-composite-verified'
    context = dict(schemaVersion=1, task='F-03d3e8', inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
        semanticIdentityStatus='unresolved', fullAvatarRawClosure=True, fullComplexRawClosure=True, blockedTypeIndices=[],
        aotBindings=aot, primitiveAotBindings=primitive_aot, outerAotBindings=outer, initializers=graph.complete_initializers, dependencyScope=audit,
        sourceEvidenceSHA256=SOURCE_HASHES, nativeCopyBinding=dict(messageType=18849, method=copy_method, listRangeWrapper=wrapper, nativeListRangeTarget='0x158db7cd0'),
        closureValidation=closure, closureMessages=[messages[i] for i in sorted(messages)],
        closureWireFields=sum(len(x['fields']) for x in messages.values()), complexWireFields=sum(len(messages[i]['fields']) for i in complex_types),
        messages=[graph.schemas[TYPE]], scan=scan, nativeFixtures=len(good), rejections=rejected,
        limitations=['Raw graph only; managed metadata/allocation, primitive input/codecs, boxing and fixed64 arrays are models',
            'No business semantic promotion, handler, live ABI, client acceptance or production integration'])
    write_reports(paths, context, good)
    print(json.dumps(dict(messages=1, storage=12, wireFields=11, scanFixtures=512, nativeFixtures=len(good), rejections=len(rejected))))


if __name__ == '__main__': main()
