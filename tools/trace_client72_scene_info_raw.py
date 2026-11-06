"""F-03d3s1: bounded nonempty SceneInfo raw contracts, not a full closure.

Nine outer/simple message contracts reuse the independently verified Avatar raw
graph. Object owners 72829/54267 remain unresolved; their nonempty input refuses.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import struct

try:
    from .trace_client72_avatar_complete_raw import AvatarCompleteRaw72, SOURCE_HASHES, fixtures as complete_fixtures
    from .trace_client72_avatar_middle_trees import inputs, message_list_aot, message, scalar
    from .trace_client72_avatar_signed_trees import install_types, initialize_types, selected_aot
    from .trace_client72_avatar_outer import avatar_aot
    from .extract_client72 import HASHES, PE, HEADER_VA, BODY, TypeResolver, number
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, STREAM, GLOBAL
    from .client72_graph import GraphReader72
    from .trace_client72_token import varint, load_emulator
except ImportError:
    from trace_client72_avatar_complete_raw import AvatarCompleteRaw72, SOURCE_HASHES, fixtures as complete_fixtures
    from trace_client72_avatar_middle_trees import inputs, message_list_aot, message, scalar
    from trace_client72_avatar_signed_trees import install_types, initialize_types, selected_aot
    from trace_client72_avatar_outer import avatar_aot
    from extract_client72 import HASHES, PE, HEADER_VA, BODY, TypeResolver, number
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, STREAM, GLOBAL
    from client72_graph import GraphReader72
    from trace_client72_token import varint, load_emulator

TYPES = {34208, 18295, 44512, 49723, 73976, 80154, 89710, 87468, 83320}
SIMPLE = TYPES - {49723}
BLOCKED = 72829
REUSED_SHA = '37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'): parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args(); paths = [args.output.resolve(), args.observations.resolve()]
    if len(set(paths)) != 2 or any(p.is_relative_to(args.client.resolve()) for p in paths):
        parser.error('Distinct outputs outside client required')
    binary, metadata, startup, records, cells, bindings, strings, items = inputs(args)
    reused = verify_sources(args.directory)
    audit = declarations(binary, metadata, startup, records, cells, args.python_libs)
    concrete = {f['type']['instanceIndex']: f['type']['arguments'][0]['typeDefinition']
        for i in TYPES for f in records[i]['fields'] if f['offsetKind'] == 'instance'
        and f['type']['kind'] == 'genericinst' and f['type']['typeDefinition'] == 25060}
    aot = message_list_aot(binary, metadata, startup, records, concrete)
    primitive = selected_aot(binary, metadata, startup, records, {(35584, 11): ('.ctor', 'FNNADLCNGFO', 'Add')})
    if not any(x['method'] == 'FNNADLCNGFO' and x['address'] == '0x158db8e10' for x in primitive):
        raise ValueError('Scene uint32 list concrete AOT drift')
    outer = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    engine = Offline72(binary, args.python_libs, records, trace_instructions=False)
    graph = SceneInfoRaw72(engine, args.directory, cells, bindings, items, strings, outer, audit)
    try:
        scan = scan_raw(graph)
        good, rejected = verify(graph)
        coverage = validate_coverage(graph, good, args.directory)
    finally:
        graph.close(); engine.close()
    context = dict(schemaVersion=1, task='F-03d3s1', inputSHA256=HASHES,
        sourceEvidenceSHA256=reused, runtimeStatus='not-tested', targetProtocolReady=False,
        completeSceneInfoRawClosure=False, semanticIdentityStatus='unresolved-except-previous-f2b-tag12',
        fullNetworkHandler=False, fullSceneEntityEffects=False,
        boundedRawTypeIndices=sorted(TYPES), prioritizedTypeIndices=sorted(SIMPLE),
        unresolvedObjectOwnerTypeIndices=[72829, 54267], aotBindings=aot, primitiveAotBindings=primitive,
        declarations=audit, initializers=graph.scene_initializers,
        nativeCodecBindings=graph.scene_codec_bindings,
        messages=[graph.schemas[i] for i in sorted(TYPES)], scan=scan,
        nativeFixtures=len(good), rejections=rejected, nativeCoverage=coverage,
        sourceSHA256={p: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in (
            'tools/trace_client72_scene_info_raw.py', 'tools/trace_client72_avatar_complete_raw.py',
            'tools/client72_graph.py', 'tools/client72_emulator.py')},
        limitations=['Finite outer/simple raw slice only; no complete SceneInfo closure or business promotion',
            '72829 empty envelope executes its reader but has no schema; all nonempty input refuses before that reader body',
            '54267 and Object-erased descendants are unresolved, not represented as successful empty models',
            'Managed allocation/class/interface metadata, primitive inputs/codecs/float and fixed-capacity arrays are explicit models',
            '64 elements execute; growth65 reaches unmodeled message RGCTX or primitive allocator, not a client size rejection',
            'Actual message readers, nested limit logic, native stores and concrete list read/Add execute in the target image',
            'No network handler, entity construction, live ABI, deployment or client acceptance'])
    summary = dict(context, fixtures=[dict(name=r['name'], typeIndex=r['typeIndex'], wire=r['wire'], **{
        k: v for k, v in r['observation'].items() if k not in ('typeIndex', 'wire', 'nestedCalls')}) for r in good])
    for path, value in zip(paths, (dict(context, fixtures=good), summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8')
    print(json.dumps(dict(messages=len(TYPES), wireFields=sum(len(graph.schemas[i]['fields']) for i in TYPES),
        scanFixtures=scan['nativeFixtures'], nativeFixtures=len(good), rejections=len(rejected))))


def verify_sources(directory):
    sources = dict(SOURCE_HASHES)
    # The complete e8 graph is fixed independently of this new recovery.
    sources['complete-raw'] = REUSED_SHA
    for name, expected in sources.items():
        raw = (directory / ('avatar-' + name + '-observations.json')).read_bytes()
        if hashlib.sha256(raw).hexdigest() != expected: raise ValueError('Reused Avatar raw evidence drift: ' + name)
    return sources


def declarations(binary, metadata, startup, records, cells, python_libs):
    load_emulator(python_libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(binary); md = Cs(CS_ARCH_X86, CS_MODE_64); md.detail = True
    ends = sorted({int(m['registeredAddress'], 16) for row in records for m in row['methods'] if m['registeredAddress'] != '0x0'})
    header = pe.offset(HEADER_VA); resolver = TypeResolver(pe, startup, records, header)
    method_base = BODY + (number(binary, header + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, header + 0x174) + 0xAE6BCD68) & 0xffffffff)
    rows, usages, edges = [], [], []
    for index in sorted(TYPES | {BLOCKED, 54267}):
        row = records[index]; fields = [f for f in row['fields'] if f['offsetKind'] == 'instance']; methods = []
        for field in fields:
            for typ in field['type'].get('arguments', [field['type']]):
                if typ['kind'] == 'class': edges.append(dict(owner=index, field=field['name'], targetType=typ['typeDefinition']))
        for source in row['methods']:
            if source['name'] not in ('MOMMACBEMEK', '.cctor'): continue
            method = copy.deepcopy(source); entry = int(method['registeredAddress'], 16); end = next(a for a in ends if a > entry)
            raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
            method.update(codeSHA256=hashlib.sha256(raw).hexdigest(), extentEnd=hex(end))
            if source['name'] == 'MOMMACBEMEK':
                if source['parameterCount'] != 1: raise ValueError('Scene raw typed reader signature changed')
                first = parameter_start(source['index'], number(metadata, method_base + source['index'] * 26 + 4))
                _, ref = parameter_words(first, number(metadata, parameter_base + first * 8), number(metadata, parameter_base + first * 8 + 4))
                typ = resolver.resolve_index(ref)
                if typ.get('typeDefinition') != 52310 or typ.get('byRef'): raise ValueError('Scene raw reader input type changed: ' + str(typ))
                method.update(parameterIndex=first, parameterTypeIndex=ref, parameterType=typ)
            if raw[:1] == b'\xe9':
                method['protectedJump'] = hex(entry + 5 + struct.unpack_from('<i', raw, 1)[0])
            direct_calls = set()
            for ins in md.disasm(raw, entry):
                if ins.mnemonic == 'call' and ins.operands[0].type == 2: direct_calls.add(hex(ins.operands[0].imm))
                for op in ins.operands:
                    if op.type == 3 and op.mem.base == 41:
                        cell = ins.address + ins.size + op.mem.disp
                        if cell in cells: usages.append(dict(owner=index, method=source['name'], site=hex(ins.address), **cells[cell]))
            method['nativeDirectCalls'] = sorted(direct_calls)
            methods.append(method)
        rows.append(dict(typeIndex=index, clientType=row['name'], fields=fields, methods=methods,
            contractStatus='outer-and-simple-native' if index in TYPES else 'unresolved-object-owner'))
    return dict(types=rows, nativeUsageReferences=usages, metadataEdges=edges,
        metadataStorageFields=sum(len(r['fields']) for r in rows if r['typeIndex'] in TYPES),
        scope='Fresh metadata and bounded registered method extents; protected root jump is recorded, not a static dependency closure')


class SceneInfoRaw72(AvatarCompleteRaw72):
    def __init__(self, engine, directory, cells, bindings, items, strings, outer, audit):
        super().__init__(engine, directory, cells, bindings, items, strings, outer); e = self.e
        install_types(self, cells, TYPES)
        # 72829 has no schema. Only its truly empty reader path is executable.
        index = BLOCKED; entry = next(int(m['registeredAddress'], 16) for m in e.records[index]['methods'] if m['name'] == 'MOMMACBEMEK')
        klass, table = e.alloc(1024), e.alloc(16); e.write(klass + 0xC7, 1, 'B')
        e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
        e.write(table, self.interface); e.write(klass + 0xE8, entry)
        self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry; e.allow(entry)
        for cell, row in cells.items():
            if row['type'].get('typeDefinition') == index and row['type']['kind'] == 'class': e.write(cell, klass)
        self.scene_type_usages = []
        for row in audit['nativeUsageReferences']:
            if row['owner'] not in TYPES: continue
            typ = row['type']; cell = int(row['cell'], 16)
            if typ['kind'] == 'genericinst' and typ.get('typeDefinition') in (25060, 45077):
                modeled = e.synthetic_type(cell); self.scene_type_usages.append(row)
                if typ['typeDefinition'] == 45077: self.codec_classes.add(modeled)
        self.scene_initializers = initialize_types(self, TYPES)
        self.scene_codec_bindings = []
        for owner, static, tag, child in [(34208, 0x35000, 2, 18295), (18295, 0x17248, 11, 73976),
            (18295, 0x17240, 12, 53824), (49723, 0x11A20, 3, 80154), (89710, 0x27040, 1, 83320)]:
            codec = e.read(GLOBAL + static)
            if e.read(codec + 0x10, 'I') != tag << 3 | 2 or e.read(codec + 0x18) != self.classes[child]:
                raise ValueError('Scene native message codec binding drift')
            self.scene_codec_bindings.append(dict(ownerType=owner, staticOffset=static, tag=tag,
                nestedTypeIndex=child, codecTagOffset=16, codecClassOffset=24, initializer='native-cctor-store'))
        codec = e.read(GLOBAL + 0x17250)
        if e.read(codec + 0x34, 'I') != 50: raise ValueError('Scene native primitive codec binding drift')
        self.scene_codec_bindings.append(dict(ownerType=18295, staticOffset=0x17250, tag=6,
            valueType='uint32', codecTagOffset=52, initializer='native-cctor-to-explicit-primitive-codec-factory'))
        self.heap_floor = e.heap_cursor

    def new_message(self, index, address=None):
        if index == BLOCKED:
            p = self.e.alloc(512) if address is None else address
            self.e.write(p, self.classes[index]); self.e.write(p + 0x14, 1, 'I'); self.objects[p] = index; return p
        if index in TYPES: return GraphReader72.new_message(self, index, address)
        return super().new_message(index, address)

    def snapshot(self, pointer, depth=0):
        if pointer and self.objects[pointer] == BLOCKED: return dict(untracedType=BLOCKED, content='empty-envelope-only')
        if not pointer or self.objects[pointer] not in TYPES: return super().snapshot(pointer, depth)
        result = GraphReader72.snapshot(self, pointer, depth)
        for field in self.schemas[self.objects[pointer]]['fields']:
            if field['type'] == 'float':
                if self.e.read(pointer + field['objectOffset'], 'I'): result[field['clientField']] = self.e.read(pointer + field['objectOffset'], 'f')
                else: result.pop(field['clientField'], None)
        return result

    def observe_entry(self, address):
        if self.active:
            if address == self.entries.get(BLOCKED):
                stream = self.e.reg('rdx')
                if self.e.read(stream + 0x44, 'I') != self.e.read(stream + 0x38, 'I'):
                    raise ValueError('Nonempty unresolved Object owner refused: 72829; 54267 descendants not recovered')
            if address in (0x158DC41F0, 0x158DB8E10):
                for pointer, index in self.objects.items():
                    if index not in TYPES: continue
                    for field in self.schemas[index]['fields']:
                        if field['cardinality'] == 'repeated' and self.e.read(pointer + field['objectOffset']) == self.e.reg('rcx'):
                            self.entered.append(dict(kind='native-scene-list-field', ownerType=index,
                                clientField=field['clientField'], objectOffset=field['objectOffset'],
                                key=self.e.read(STREAM + 0x20, 'I'), entry=hex(address)))
            if address in (0x158DC4236, 0x158DB8BC0):
                for pointer, index in self.objects.items():
                    if index not in TYPES: continue
                    for field in self.schemas[index]['fields']:
                        if field['cardinality'] != 'repeated': continue
                        container = self.e.read(pointer + field['objectOffset'])
                        if address == 0x158DB8BC0 and field['type'] == 'uint32' and container == self.e.reg('rcx'):
                            self.entered.append(dict(kind='native-scene-u32-add', ownerType=index,
                                clientField=field['clientField'], entry=hex(address), value=self.e.reg('edx')))
                        if address == 0x158DC4236 and field['type'] == 'message':
                            backing = self.e.read(container + 16)
                            if self.e.read(backing + 16) == self.e.reg('rcx'):
                                self.entered.append(dict(kind='native-scene-message-list-store', ownerType=index,
                                    clientField=field['clientField'], entry=hex(address), arrayIndex=self.e.reg('edx'),
                                    childTypeIndex=self.objects[self.e.reg('rbx')]))
        return super().observe_entry(address)

    def run_graph(self, index, data, recursion_limit=100):
        try: row = super().run_graph(index, data, recursion_limit)
        except self.e.U.UcError as error:
            raise ValueError('Offline native failure: ' + str(self.e.failure or error)) from error
        row['modeledAddresses'] = sorted({c['address'] for c in self.e.calls})
        row['floatScalarBits'] += [dict(typeIndex=i, clientField=f['clientField'], valueBits=hex(self.e.read(pointer + f['objectOffset'], 'I')))
            for pointer, i in self.objects.items() if i in TYPES for f in self.schemas[i]['fields'] if f['type'] == 'float']
        return row


def scan_raw(graph):
    selected, count = [], 0
    for index in sorted(TYPES):
        observed = {}
        for tag in range(1, 33):
            for wire, payload in ((0, varint(0x12345678)), (1, bytes(8)), (2, b'\0'), (5, bytes.fromhex('0000a03f'))):
                try: row = graph.run_graph(index, varint(tag << 3 | wire) + payload)
                except ValueError as error: raise ValueError(f'Scene scan {index}/{tag}/{wire}: {error}') from error
                count += 1
                calls = [r for r in row['entered'] if r.get('kind') == 'native-scene-list-field' and r['ownerType'] == index]
                if row['result'] or row['stores'] or calls:
                    names = list(row['result']) or [r['clientField'] for r in calls]
                    if not names: names = [f['clientField'] for f in graph.schemas[index]['fields'] if any(s['typeIndex'] == index and s['objectOffset'] == f['objectOffset'] for s in row['stores'])]
                    if len(set(names)) != 1: raise ValueError('Ambiguous Scene field scan: ' + str((index, tag, wire, names)))
                    observed.setdefault(names[0], []).append((tag, wire))
                    selected.append(dict(typeIndex=index, tag=tag, wireType=wire, result=row['result'], entered=row['entered'], stores=row['stores']))
        for field in graph.schemas[index]['fields']:
            hits = observed.pop(field['clientField'], [])
            if not hits or len({t for t, _ in hits}) != 1: raise ValueError('Missing Scene field: ' + str((index, field)))
            wires = sorted(w for _, w in hits)
            if wires == [5] and field['type'] == 'uint32': field.update(type='fixed32', metadataKind='uint32')
            expected = [2] if field['type'] == 'message' else [0, 2] if field['cardinality'] == 'repeated' else [5] if field['type'] in ('float', 'fixed32') else [0]
            if wires != expected: raise ValueError('Scene metadata/wire mismatch: ' + str((index, field, wires)))
            field.update(tag=hits[0][0], acceptedWireTypes=wires, semanticStatus='unresolved')
            field['nativeSources'] = sorted({store['source'] for row in selected if row['typeIndex'] == index and row['tag'] == hits[0][0]
                for store in row['stores'] if store['typeIndex'] == index and store['objectOffset'] == field['objectOffset']} |
                {call['entry'] for row in selected if row['typeIndex'] == index and row['tag'] == hits[0][0]
                    for call in row['entered'] if call.get('kind') == 'native-scene-list-field' and call['clientField'] == field['clientField']})
            if field.get('nestedTypeIndex') == BLOCKED: field['contentStatus'] = 'empty-envelope-only; nonempty-refused'
        if observed: raise ValueError('Unexpected Scene wire field')
    if sum(len(graph.schemas[i]['fields']) for i in TYPES) != 36: raise ValueError('Scene limited field count drift')
    return dict(nativeFixtures=count, scope='9 types tags1..32/wire0,1,2,5; 36 fields; not Object-erased dependency closure', fields=selected)


def fixtures():
    # The keys and scalar/fixed32 bytes below are independent literals. Existing
    # e8 fixtures supply only their already verified child content.
    known = {r['name']: r for r in complete_fixtures()}
    complex_case = known['53824-complete-all-nonempty']
    complex_wire = bytes.fromhex(complex_case['wire'])
    leaf_wire = bytes.fromhex('080b10161821202c2801')
    leaf = dict(JPCOIFLAAED=11, CIIBGHCBCGK=22, FOKGPCODGMF=33, IJKEMAINHOD=44, GAPJBCJONCA=True)
    embryo_wire = bytes.fromhex('08818080800815efcdab891dffffffff')
    embryo = dict(DOBGMPBDLKF=2147483649, EMBPDEHFOJK=2309737967, OPKFBNFODJM=4294967295)
    pair_wire = bytes.fromhex('0807150000a0bf'); pair = dict(ANKEOLKBECN=7, GOKKMOKPCEE=-1.25)
    list_wire = message(1, embryo_wire) + message(1, bytes.fromhex('080115000000801d78563412'))
    listing = dict(GFDEDOJEICE=[embryo, dict(DOBGMPBDLKF=1, EMBPDEHFOJK=2147483648, OPKFBNFODJM=305419896)])
    avatar_wire = bytes.fromhex('20ffffffff0f38818080800850ffffffffffffffffff016881808080808080103000320601ffffffff0f')
    avatar_wire += message(5, b'\x08\x01') + message(8, b'\x08\0')
    avatar_wire += message(11, leaf_wire) + message(11, bytes.fromhex('18ffffffff0f'))
    avatar_wire += message(12, complex_wire) + message(12, b'\x08\x4d')
    avatar = dict(FDDDFIAINCE=4294967295, IEDJNHILJBF=2147483649, NPKKCHABEJH=18446744073709551615,
        OKMEHFHNFCG=9007199254740993, CEKDDNOPKMK=[0, 1, 4294967295], CCODNLIKNGM=dict(FAGKAADHKDI=True), LBJMODOPOCP={},
        CDOAFDGJGDF=[leaf, dict(FOKGPCODGMF=4294967295)], JOBBNEKLCOF=[complex_case['expected'], dict(LLHEBIHGDAE=77)])
    team_wire = bytes.fromhex('08ffffffff0f28818080800812020801')
    team = dict(HKLILOKPKBJ=4294967295, LLHEBIHGDAE=2147483649, KDKAEHEEMMJ=dict(FAGKAADHKDI=True))
    known_ability_wire = bytes.fromhex('38ffffffff0f4d0000a0bf') + message(3, pair_wire) + message(3, bytes.fromhex('08081500002040'))
    known_ability_wire += message(1, list_wire) + message(4, b'\x08\x01')
    ability = dict(JNBCPNHJCNI=4294967295, AGJIEAHJPJC=-1.25,
        KNFDPJCMNOE=[pair, dict(ANKEOLKBECN=8, GOKKMOKPCEE=2.5)], EOFFDMLNFIM=listing, GLHJPPFOGMB=dict(FAGKAADHKDI=True))
    envelope = dict(ability, PKJBKFHECBE=dict(untracedType=72829, content='empty-envelope-only'))
    scene_wire = bytes.fromhex('38818080800860ffffffff0f') + message(2, avatar_wire) + message(2, bytes.fromhex('6880808080808080808001'))
    scene_wire += message(14, team_wire) + message(15, known_ability_wire)
    scene = dict(MCCMGLDOECH=2147483649, OLCJCLBMMCF=4294967295,
        DIGAMAKGCPA=[avatar, dict(OKMEHFHNFCG=9223372036854775808)], LFLOLJADPBI=team, AEKMONHBHAA=ability)
    cases = [
        dict(name='73976-independent-literal-all-fields', typeIndex=73976, wire=leaf_wire.hex(), expected=leaf),
        dict(name='80154-independent-literal-nonzero-float', typeIndex=80154, wire=pair_wire.hex(), expected=pair),
        dict(name='83320-independent-literal-two-fixed32', typeIndex=83320, wire=embryo_wire.hex(), expected=embryo),
        dict(name='87468-independent-negative-enum-and-u32-max', typeIndex=87468,
            wire='08ffffffffffffffffff0110ffffffff0f', expected=dict(EMNNKLLNMBG=-1, JFBIDBBFBPG=4294967295)),
        dict(name='89710-two-distinct-nonempty-items', typeIndex=89710, wire=list_wire.hex(), expected=listing),
        dict(name='18295-all-nine-nonempty-complex-graph', typeIndex=18295, wire=avatar_wire.hex(), expected=avatar),
        dict(name='44512-all-three-nonempty-child', typeIndex=44512, wire=team_wire.hex(), expected=team),
        dict(name='49723-five-fields-nonempty-known-children', typeIndex=49723, wire=known_ability_wire.hex(), expected=ability),
        dict(name='49723-six-outer-fields-with-unresolved-empty-envelope', typeIndex=49723,
            wire=(known_ability_wire + bytes.fromhex('6200')).hex(), expected=envelope),
        dict(name='34208-all-five-nonempty-bounded-graph', typeIndex=34208, wire=scene_wire.hex(), expected=scene),
        dict(name='18295-u64-independent-swapped-high-bits', typeIndex=18295,
            wire='50818080808080801068ffffffffffffffffff01', expected=dict(NPKKCHABEJH=9007199254740993, OKMEHFHNFCG=18446744073709551615)),
        dict(name='18295-same-shape-singular-children-merge-independently', typeIndex=18295,
            wire='2a020801420208012a020800', expected=dict(CCODNLIKNGM={}, LBJMODOPOCP=dict(FAGKAADHKDI=True))),
        dict(name='18295-list-items-do-not-merge-and-empty-appends', typeIndex=18295,
            wire='5a02080b5a0210165a00', expected=dict(CDOAFDGJGDF=[dict(JPCOIFLAAED=11), dict(CIIBGHCBCGK=22), {}])),
        dict(name='49723-singular-container-merges-and-list-appends', typeIndex=49723,
            wire=(message(1, message(1, embryo_wire)) + message(1, message(1, b'\x08\x07')) + message(1, b'') + message(3, pair_wire) + message(3, b'')).hex(),
            expected=dict(EOFFDMLNFIM=dict(GFDEDOJEICE=[embryo, dict(DOBGMPBDLKF=7)]), KNFDPJCMNOE=[pair, {}])),
        dict(name='34208-duplicate-nested-merge-preserves-list-independence', typeIndex=34208,
            wire='72020801720228027a0208017a020a001202200312022004',
            expected=dict(LFLOLJADPBI=dict(HKLILOKPKBJ=1, LLHEBIHGDAE=2), AEKMONHBHAA=dict(EOFFDMLNFIM={}),
                DIGAMAKGCPA=[dict(FDDDFIAINCE=3), dict(FDDDFIAINCE=4)])),
        dict(name='83320-wrong-varints-and-wrong-fixed32-skipped', typeIndex=83320,
            wire='180710070d01000000f80109', expected={}),
        dict(name='83320-duplicate-fixed32-u32-explicit-zero', typeIndex=83320,
            wire='1dffffffff1d0000000015efcdab89150000000008ffffffff0f0800', expected={}),
        dict(name='80154-negative-zero-subnormal-and-finite-max', typeIndex=80154,
            wire='150100000015ffff7f7f1500000080', expected=dict(GOKKMOKPCEE=-0.0)),
        dict(name='80154-minimum-positive-subnormal', typeIndex=80154,
            wire='1501000000', expected=dict(GOKKMOKPCEE=1.401298464324817e-45)),
        dict(name='80154-maximum-finite-float', typeIndex=80154,
            wire='15ffff7f7f', expected=dict(GOKKMOKPCEE=3.4028234663852886e38)),
        dict(name='49723-float-duplicate-positive-zero', typeIndex=49723,
            wire='4d0000a0bf4d00000000', expected={}),
        dict(name='18295-u32-mixed-packed-unpacked-and-empty', typeIndex=18295,
            wire='3000320601ffffffff0f3200308001', expected=dict(CEKDDNOPKMK=[0, 1, 4294967295, 128])),
        dict(name='89710-fixed-capacity64-items', typeIndex=89710,
            wire=b''.join(message(1, scalar(1, n)) for n in range(64)).hex(), expected=dict(GFDEDOJEICE=[dict(DOBGMPBDLKF=n) if n else {} for n in range(64)])),
        dict(name='18295-fixed-capacity64-u32', typeIndex=18295,
            wire=message(6, bytes(range(64))).hex(), expected=dict(CEKDDNOPKMK=list(range(64))))]
    for index, tags in [(34208, (7, 12)), (18295, (4, 7, 10, 13)), (44512, (1, 5)),
        (73976, (1, 2, 3, 4, 5)), (87468, (1, 2)), (80154, (1,)), (49723, (7,))]:
        cases.append(dict(name='duplicate-zero-' + str(index), typeIndex=index,
            wire=b''.join(scalar(tag, 1) + scalar(tag, 0) for tag in tags).hex(), expected={}))
    for index in sorted(TYPES): cases.append(dict(name='empty-' + str(index), typeIndex=index, wire='', expected={}))
    return cases


def verify(graph):
    good = []
    for case in fixtures():
        row = graph.run_graph(case['typeIndex'], bytes.fromhex(case['wire']))
        if row['result'] != case['expected']: raise ValueError(case['name'] + ': Scene raw native result differs ' + str(row['result']))
        good.append(dict(case, observation=row))
    rejected = []
    errors = [('invalid-tag-' + str(i), i, '00', 'explicit-input-model', 100) for i in sorted(TYPES)]
    errors += [('u64-overflow', 18295, '68ffffffffffffffffff02', 'explicit-input-model', 100),
        ('u32-packed-truncated', 18295, '320180', 'explicit-input-model', 100),
        ('fixed32-truncated', 83320, '1d010203', 'explicit-input-model', 100),
        ('negative-scene-list-length', 34208, '12ffffffff0f', 'native-error-entry', 100),
        ('truncated-scene-child', 34208, '12030801', 'native-error-entry', 100),
        ('scene-list-recursion-limit', 34208, '1200', 'native-error-entry', 0),
        ('known-singular-recursion-limit', 49723, '0a00', 'native-error-entry', 0),
        ('deep-scene-recursion-limit', 34208, '12022a00', 'native-error-entry', 1),
        ('nonempty-object-owner-direct-parent', 49723, '62020801', 'explicit-scope-refusal', 100),
        ('nonempty-object-owner-scene-root', 34208, '7a0462020801', 'explicit-scope-refusal', 100),
        ('message-list-growth65-outside-fixture', 89710, (b'\x0a\0' * 65).hex(), 'fixture-model-boundary', 100),
        ('u32-list-growth65-outside-fixture', 18295, message(6, bytes(range(65))).hex(), 'fixture-model-boundary', 100)]
    for name, index, wire, scope, limit in errors:
        try: graph.run_graph(index, bytes.fromhex(wire), limit)
        except ValueError as error:
            reason = str(error)
            if scope == 'native-error-entry' and 'Native error branch:' not in reason: raise ValueError(name + ': ' + reason) from error
            if scope == 'explicit-scope-refusal' and 'Nonempty unresolved Object owner refused:' not in reason: raise ValueError(name + ': ' + reason) from error
            if scope == 'fixture-model-boundary':
                expected = 'Unmapped access 0x30 at 0x158dc42ca' if name.startswith('message-list') else 'Unmodeled native call 0x140514ee0'
                if expected not in reason: raise ValueError(name + ': ' + reason) from error
            rejected.append(dict(name=name, typeIndex=index, wire=wire, scope=scope, error=reason))
        else: raise ValueError('Invalid or out-of-scope Scene input accepted: ' + name)
    return good, rejected


def validate_coverage(graph, good, directory):
    source = json.loads((directory / 'avatar-complete-raw-observations.json').read_text('utf8'))
    complex_types = set(next(row['enteredMessageTypeIndices'] for row in source['closureValidation']
        if row['fixture'] == '53824-complete-all-nonempty'))
    cases = {r['name']: r['observation'] for r in good}
    entered_types = {r['typeIndex'] for row in cases.values() for r in row['entered'] if 'typeIndex' in r}
    if not TYPES <= entered_types: raise ValueError('Some new typed readers were not executed')
    critical = {hex(graph.entries[i]) for i in TYPES} | {'0x1474d7e60', '0x147521000', '0x158dc41f0', '0x158db8e10', '0x158db8bc0'}
    for name, row in cases.items():
        if critical & set(row['modeledAddresses']): raise ValueError('Critical Scene reader or Add was modeled: ' + name)
        if not any(r.get('entry') == hex(graph.entries[row['typeIndex']]) for r in row['entered']):
            raise ValueError('Root typed reader not observed: ' + name)
        if 'untracedType' in json.dumps(row['result']) and name != '49723-six-outer-fields-with-unresolved-empty-envelope':
            raise ValueError('Unresolved child leaked into a bounded nonempty case')
    coverage = []
    for name, wanted in [('18295-all-nine-nonempty-complex-graph', complex_types | {18295, 73976}),
        ('34208-all-five-nonempty-bounded-graph', complex_types | (TYPES - {87468}))]:
        entered = {r['typeIndex'] for r in cases[name]['entered'] if 'typeIndex' in r}
        if entered != wanted: raise ValueError('Bounded Scene native reader set differs: ' + name + str(sorted(entered ^ wanted)))
        coverage.append(dict(fixture=name, enteredMessageTypeIndices=sorted(entered), messageCount=len(entered),
            reusedCompleteComplexMessageCount=len(complex_types)))
    appends = [r for r in cases['89710-two-distinct-nonempty-items']['entered'] if r.get('kind') == 'native-scene-message-list-store']
    if [(r['arrayIndex'], r['childTypeIndex']) for r in appends] != [(0, 83320), (1, 83320)]:
        raise ValueError('Scene concrete message array append not observed')
    adds = [r['value'] for r in cases['18295-u32-mixed-packed-unpacked-and-empty']['entered']
        if r.get('kind') == 'native-scene-u32-add']
    if adds != [0, 1, 4294967295, 128]: raise ValueError('Scene uint32 Add values differ: ' + str(adds))
    return dict(executedNewMessageTypeIndices=sorted(TYPES), criticalNativeAddresses=sorted(critical),
        noCriticalFunctionModeled=True, nonemptyCompositeCoverage=coverage,
        nativeMessageArrayStore='0x158dc4236', nativeUint32Add='0x158db8bc0',
        objectOwnerNonemptyStatus='explicitly-refused', completeSceneInfoRawClosure=False)


if __name__ == '__main__': main()
