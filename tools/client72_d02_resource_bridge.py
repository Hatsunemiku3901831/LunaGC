"""Bounded, hash-bound resource-to-Java field bridge; never installs resources."""
import argparse
import hashlib
import json
import re
import struct
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
OUTPUT_ROOT = Path('local/migration72/d02-resume-20261108/r3-mapping')
TABLE_REPORT = 'local/migration72/d02-active/avatar/natural-tables/first.json'
TABLE_REPORT_SHA = '1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86'
SOURCE_ROOT = Path('local/migration72/d02-active/resource-locator-astra/locator-final-1')
JAVA = 'src/main/java/emu/grasscutter/'
JAVA_PINS = {
    'data/excels/avatar/AvatarData.java': 'be326428e1c3eee90c1c588c35b8d8133e5e59fa81adaeba4f902b7a4d53a779',
    'data/excels/avatar/AvatarSkillDepotData.java': '2aa9dd97dd2d375d430af7053d3e7adab58e91cf3b997fadf5852d33c24d4f52',
    'data/excels/avatar/AvatarSkillData.java': 'd87cf73c7105dd7854f3fc20ccb009bf0d850b703ba45cba0d572b6a66e1b6bc',
    'data/excels/ItemData.java': 'ec5ec94f2cf7ed4be3cdc7a0249004115ec149a988e19529bd53315016f02c93',
    'game/props/ElementType.java': '7f64fcc73d96a908cf911961557897710224eeb5df190afad8ab9d68824a4d49',
    'data/ResourceType.java': '8a8711f3ad4319b14f4cb4bd9c9dcc6531dad77eae287d8cdf495b118e7d3b6e',
    'data/ResourceLoader.java': 'fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015',
    'game/avatar/AvatarStorage.java': '6d6b07102d5f8b9f95ebbd4e18d1489f9143d502100dcfaadff0235db138e784',
    'game/avatar/Avatar.java': '9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d',
}
NATIVE_ENTRIES = (0x153217960, 0x15345c2d0, 0x15345f3d0, 0x15345a7f0,
                  0x15345e260, 0x14cf26b80)
REVISION_FILES = {
    'res_revision': '860f5ac3d85195eea5edd9631977a581215c2c53025787fa72232c6e84cd725e',
    'data_revision': '860f5ac3d85195eea5edd9631977a581215c2c53025787fa72232c6e84cd725e',
    'silence_revision': '69a17593e968eccd631ee38f9b0b7ecdf3a5750072cc355ccfa2df55281ab378',
}
SOURCE_COMMIT = 'local/migration72/evidence/resource-source-commit.json'
SOURCE_COMMIT_SHA = '97adcf1557633513b82d395c005863760f527fd6850c6895733b93f0cdecf694'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=REPO / 'local/migration72/python')
    args = parser.parse_args()
    if not args.output.resolve().is_relative_to((REPO / OUTPUT_ROOT).resolve()):
        parser.error('Output must stay in the dedicated r3-mapping directory')
    reviewed, java_sources, pins = verify_inputs()
    revisions = read_revisions(args.client)
    native = run_native(args.client, args.python_libs, reviewed, args.output.with_suffix('.failure.json'))
    enum_rows = next(row for row in reviewed['tables'][0]['enumSymbols'] if row['typeIndex'] == 74813)
    enum_map = align_elements(enum_rows['values'], java_sources['game/props/ElementType.java'])
    projections = project_fields(native['parsedTables'], enum_map)
    report = dict(task='D02-R3/E-01-resource-bridge', inputSHA256=native['inputSHA256'],
        sourcePins=pins, native=native, enumMap=enum_map, javaProjectionFragments=projections,
        scope='Reviewed field fragments only; not complete Java resource rows, loadAll, Avatar handler, current saved depot, or resource readiness.',
        closedFields=['Avatar.id', 'Avatar.skillDepotId', 'AvatarSkillDepot.id',
                      'AvatarSkillDepot.skills', 'AvatarSkillDepot.energySkill',
                      'AvatarSkill.id', 'AvatarSkill.costElemType'],
        weaponScope='Only the previously proven Weapon row/map identity is reused. No new Weapon field or natural NativeConfig permission.',
        loadingOrder=['Ability embryos/config/player ability group inputs', 'AvatarSkill HIGHEST',
                      'AvatarSkillDepot HIGH', 'Curve/Item/Promote NORMAL (same-priority ordering unspecified)',
                      'Avatar LOW', 'Inventory.loadFromDatabase', 'AvatarStorage.postLoad/recalcStats',
                      'completeLoadingVitals/restoreLoadingVitals(true)'],
        revisions=revisions,
        models=['NaturalRuntime typed classes/RGCTX, managed allocation, UTF8 conversion, collection capacities and scalar-storage codec are the frozen explicit models.',
                'Skill component and existing skill objects are typed storage fixtures, with IDs from actual parsed rows. No full skill factory/component constructor or lifecycle executes.',
                'The depot initialization slice starts at 15345c306 after earlier reset calls, and stops at 15345c4fd before other special fields; no success replacement of those calls.',
                'Role selection uses original native ValueCollection enumeration/FindEntry and array store. Preconstructed ValueCollection, empty override dictionary and six-slot typed array are explicit fixtures.',
                'Component b3=false is an explicit fixture suppressing event dispatch after selection, not an observed lifecycle value or permission to omit those events.',
                'MonoMPTeamBtn.UpdateEnergySkillAvailable is a static named caller anchor; only its callee prefix is executed to the selected pointer, before availability predicates.'],
        unknowns=['Weapon natural NativeConfig mode/3521/6506 and native backend remain unresolved.',
                  'Avatar initialWeapon/base properties/curve/promote and other depot/skill English aliases need their own consumer bridges.',
                  'Same-revision curves/promote/config/ability inputs, complete Java rows, loader outcomes, positive manager rebind and actual selected revision remain unresolved.',
                  'Target element values 10..18 have no equal named Java ElementType; Java Default255 has no target symbol. Neither side may be silently mapped to None.'],
        nativeCurrentConstructorExecuted=False, javaResourcesLoaded=False, resourceReady=False,
        completeAvatarHandler=False, targetProtocolReady=False, d02Complete=False,
        sourceSHA256=sha256(Path(__file__).read_bytes()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', 'utf8')
    print(json.dumps(dict(roles=[(row['depotId'], row['roles']) for row in native['roleCases']],
                          closedFields=report['closedFields'], resourceReady=False)))


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_pinned(path, digest):
    raw = path.read_bytes()
    if sha256(raw) != digest:
        raise ValueError('Pinned input changed: ' + str(path.relative_to(REPO)))
    return raw


def verify_inputs():
    reviewed = json.loads(read_pinned(REPO / TABLE_REPORT, TABLE_REPORT_SHA))
    pins = {TABLE_REPORT: TABLE_REPORT_SHA,
            'tools/client72_avatar_natural_tables.py': reviewed['sourceSHA256']}
    pins.update({'tools/' + name: digest for name, digest in reviewed['dependencySHA256'].items()})
    for name, digest in pins.items():
        read_pinned(REPO / name, digest)
    sources = {}
    for name, digest in JAVA_PINS.items():
        sources[name] = read_pinned(REPO / (JAVA + name), digest).decode('utf8')
        pins[JAVA + name] = digest
    return reviewed, sources, pins


def read_revisions(client):
    bundled = {}
    for name, digest in REVISION_FILES.items():
        relative = 'YuanShen_Data/StreamingAssets/AssetBundles/' + name
        raw = (client / relative).read_bytes()
        if sha256(raw) != digest:
            raise ValueError('Pinned installed revision file changed: ' + relative)
        bundled[name] = dict(file=relative, text=raw.decode('ascii'), bytes=len(raw), sha256=digest)
    candidate = json.loads(read_pinned(REPO / SOURCE_COMMIT, SOURCE_COMMIT_SHA))
    return dict(payloadSource='Fixed installed-client locator and exact m_bytes hashes',
                bundledRevisionFiles=bundled, candidateCommitEvidenceSHA256=SOURCE_COMMIT_SHA,
                candidateSource=candidate, activeSelectedRevision=None, activeRevisionVerified=False,
                scope='Current installed StreamingAssets file bytes and fixed candidate commit label; neither establishes the active hot-update revision or alignment of their different values.')


def align_elements(target_values, java_source):
    java_values = {name: int(value) for name, value in re.findall(r'^    (\w+)\(\s*(\d+)\s*,', java_source, re.M)}
    result = []
    for row in target_values:
        name, value = row['symbol'], row['value']
        result.append(dict(targetSymbol=name, targetValue=value, targetFieldIndex=row['fieldIndex'],
                           javaSymbol=name if java_values.get(name) == value else None,
                           javaValue=java_values.get(name),
                           accepted=java_values.get(name) == value))
    return result


def java_id(value, *, allow_zero=False):
    if type(value) is not int or not (0 if allow_zero else 1) <= value <= 0x7fffffff:
        raise ValueError('Target uint32 ID cannot be represented by this Java int resource key')
    return value


def java_element(value, enum_map):
    row = next((row for row in enum_map if row['targetValue'] == value and row['accepted']), None)
    if row is None:
        raise ValueError('No exact named Java ElementType for target value ' + str(value))
    return row['javaSymbol']


def project_fields(tables, enum_map):
    projected = []
    keys = {table['name']: {row['key'] for row in table['rows']} for table in tables}
    for table in tables:
        for row in table['selected']:
            if not row['found']:
                continue
            fields = {field['offset']: field['value'] for field in row['fields']}
            name = table['name']
            if name == 'Avatar':
                output = dict(id=java_id(row['key']), skillDepotId=java_id(fields['0x168']))
                if output['skillDepotId'] not in keys['AvatarSkillDepot']:
                    raise ValueError('Actual default depot reference is absent')
            elif name == 'AvatarSkillDepot':
                output = dict(id=java_id(row['key']), skills=[java_id(value, allow_zero=True) for value in fields['0x30']],
                              energySkill=java_id(fields['0x6c'], allow_zero=True))
                if any(value not in keys['AvatarSkill'] for value in output['skills'] + [output['energySkill']] if value):
                    raise ValueError('Positive actual skill reference is absent')
            else:
                output = dict(id=java_id(row['key']), costElemType=java_element(fields['0x54']['value'], enum_map))
            projected.append(dict(table=name, key=row['key'], source=row['source'], javaFields=output,
                                  fragmentOnly=True))
    return projected


def install_table(rt, name):
    """Reuse the frozen whole loader with that loader's exact generic context."""
    from . import client72_avatar_natural_tables as card
    e = rt.e
    rt.name, rt.capacity = name, card.TABLES[name][3]
    rt.reader, rt.spans, rt.pending_rows = 0, [], {}
    rt.row_entry = next(int(row['registeredAddress'], 16) for row in rt.records[card.TABLES[name][1]]['methods'] if row['name'] == 'CNLIBMNOALB')
    rt.bind_function(card.TABLES[name][0])
    refs = {int(row['target'], 16) for row in rt.functions[hex(card.TABLES[name][0])]['references'] if int(row['target'], 16) not in rt.cells}
    uses = card.method_usages(rt.binary, rt.metadata, rt.startup, rt.records, refs)
    rt.loader_usages = uses
    klass = next(ptr for ptr, typ in rt.type_records.items() if typ.get('typeDefinition') == 1222 and typ['arguments'][1].get('typeDefinition') == card.TABLES[name][1])
    rt.dictionary_class = klass
    context = e.alloc(256)
    e.write(klass + 0x30, context)
    constructor = e.alloc(64)
    e.write(constructor, klass)
    e.write(constructor + 8, e.model_stub(rt.dictionary_constructor))
    e.write(context + 0x10, constructor)
    insert = e.alloc(64)
    e.write(insert, klass)
    e.write(insert + 8, 0x1578b6a20)
    e.write(context + 0x88, insert)
    for usage in uses:
        if usage['ownerType'] != 1222 or usage['classArguments'][1].get('typeDefinition') != card.TABLES[name][1]:
            raise ValueError('Unexpected whole-loader generic context')
        info = e.alloc(64)
        e.write(info, klass)
        e.write(int(usage['cell'], 16), info)
        rt.method_cells[int(usage['cell'], 16)] = usage


def run_native(client, libraries, reviewed, failure_path):
    from . import client72_avatar_natural_tables as card
    from .client72_avatar_cold_interfaces import FIVE_INPUTS
    b, m, s, *_ = [card.base.read_verified(client / name, digest) for name, digest in FIVE_INPUTS.items()]
    blobs = {name: card.base.read_verified(REPO / SOURCE_ROOT / (name + '.native-m_bytes.bin'), spec[4]) for name, spec in card.TABLES.items()}
    card.base.read_verified((REPO / SOURCE_ROOT).with_suffix('.json'), card.LOCATOR_SHA)
    records, _ = card.base.extract(b, m, startup=s, structures=True)
    cells = card.base.type_cells(b, m, s, records)
    codec = card.natural_codec(REPO / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = card.NaturalRuntime(b, m, s, records, cells, libraries, 'Avatar', codec=codec)
    tables = []
    try:
        for name in ('Avatar', 'AvatarSkillDepot', 'AvatarSkill'):
            if name != 'Avatar':
                install_table(rt, name)
            rt.e.allowed = list(dict.fromkeys(rt.e.allowed))
            result = rt.parse_table(blobs[name])
            tables.append({key: result[key] for key in ('name', 'payloadSHA256', 'payloadBytes', 'cursor', 'count', 'rows', 'selected', 'trace')})
        by_name = {table['name']: table for table in tables}
        named_cases = []
        for avatar_id in (10000005, 10000007, 10000136, 0xffffffff):
            before = len(rt.call_order)
            trace = rt.call(0x153217960, rcx=avatar_id)
            named_cases.append(dict(avatarId=avatar_id, returned=rt.e.reg('eax'), trace=trace,
                                    calls=rt.call_order[before:]))
        role_cases = [run_role_case(rt, by_name, depot_id) for depot_id in (501, 701, 704)]
        missing_skill = run_missing_skill_case(rt, by_name, int(role_cases[-1]['componentPointer'],16))
        static = static_consumers(rt)
        return dict(inputSHA256=FIVE_INPUTS, payloadSHA256={name: sha256(blob) for name, blob in blobs.items()},
                    parsedTables=tables, namedElementCases=named_cases, roleCases=role_cases,
                    missingEnergySkill=missing_skill,
                    staticConsumers=static, componentMetadata=records[53763], skillObjectMetadata=records[53767],
                    nativeSources=rt.functions, callOrder=rt.call_order,
                    scalarCodec=codec['proof'], allocationModels=rt.allocations,
                    fullThreeTables=True, completeSkillFactory=False)
    except Exception as error:
        failure_path.parent.mkdir(parents=True, exist_ok=True)
        failure_path.write_text(json.dumps(dict(error=str(error), nativeFailure=rt.e.failure,
            completedTables=[dict(name=table['name'], cursor=table['cursor'], count=table['count']) for table in tables],
            registers={name:hex(rt.e.reg(name)) for name in ('rip','rcx','rdx','r8','r9','rsi','r14','rax','rsp')},
            callOrder=rt.call_order, nativeSources=rt.functions), indent=2)+'\n', 'utf8')
        raise
    finally:
        rt.e.close()


def run_role_case(rt, tables, depot_id):
    from . import client72_avatar_natural_tables as card
    e = rt.e
    depot = next(row for row in tables['AvatarSkillDepot']['rows'] if row['key'] == depot_id)
    selected = next(row for row in tables['AvatarSkillDepot']['selected'] if row['key'] == depot_id)
    fields = {row['offset']: row['value'] for row in selected['fields']}
    component_fields = {row['offset']: row for row in rt.records[53763]['fields'] if row['offsetKind'] == 'instance'}
    component_type = dict(kind='class', typeDefinition=53763, name=rt.records[53763]['name'], namespace='')
    object_type = component_fields[0x210]['type']['arguments'][1]
    component = e.alloc(int(rt.records[53763]['fieldLayout']['rawSizeAndFlags'][0], 16))
    e.write(component, rt.bind_type(component_type))
    e.write(component + 0x290, int(depot['pointer'], 16))
    dictionary = e.alloc(0x48)
    e.write(dictionary, rt.bind_type(component_fields[0x210]['type']))
    e.write(dictionary + 0x18, rt.array(16, 24))
    e.write(dictionary + 0x20, rt.array(16, 4))
    e.write(dictionary + 0x3c, 0xffffffff, 'I')
    e.write(component + 0x210, dictionary)
    objects, inserts = {}, []
    for key in sorted({value for value in fields['0x30'] + [fields['0x6c']] if value}):
        actual = next(row for row in tables['AvatarSkill']['rows'] if row['key'] == key)
        if rt.decode(int(actual['pointer'], 16) + 0x68) != key:
            raise ValueError('Actual skill row/key differs')
        pointer = e.alloc(int(rt.records[53767]['fieldLayout']['rawSizeAndFlags'][0], 16))
        e.write(pointer, rt.bind_type(object_type))
        e.write(pointer + 0x50, key, 'I')
        e.write(pointer + 0x54, 0xffffffff, 'I')
        objects[key] = pointer
        inserts.append(rt.call(0x1578b6a20, rcx=dictionary, rdx=key, r8=pointer, r9=2))
    rt.bind_function(0x15345c2d0)
    trace = rt.call(0x15345c306, stops=(0x15345c4fd,), rsi=component, r14=int(depot['pointer'], 16))
    roles = {str(key): e.read(pointer + 0x54, 'i') for key, pointer in objects.items()}
    # Explicit already-constructed ValueCollection. Its native enumerator still runs.
    view = e.alloc(24)
    definition = rt.generic_definition(1222, 11)
    header = e.pe.offset(card.base.HEADER_VA)
    generic_base = card.base.BODY + ((card.base.number(rt.binary, header + 0x94) + 0xd97e8a5c) & 0xffffffff)
    value_type = card.bind_class_parameters(card.annotate_generic_positions(definition['type'], rt.metadata, generic_base),
                                             component_fields[0x210]['type']['arguments'])
    e.write(view, rt.bind_type(value_type))
    e.write(view + 0x10, dictionary)
    e.write(dictionary + 0x10, view)
    overrides = e.alloc(0x60)
    e.write(overrides, rt.bind_type(component_fields[0x288]['type']))
    e.write(component + 0x288, overrides)
    array_type = component_fields[0x240]['type']
    array = rt.array(6, 8)
    e.write(array, rt.bind_type(array_type))
    e.write(component + 0x240, array)
    selection = rt.call(0x15345a7f0, rcx=component, rdx=4, r8=0)
    selected_pointer = e.read(array + 0x40)
    selected_id = e.read(selected_pointer + 0x50, 'I') if selected_pointer else None
    ui = rt.call(0x15345e260, stops=(0x15345e2aa,), rcx=component)
    return dict(depotId=depot_id, componentPointer=hex(component), source=depot['source'], scalarPresence=selected['scalarPresence'],
                regularSkills=fields['0x30'], field6c=fields['0x6c'], roles=roles,
                roleAssignment=trace, nativeDictionaryInserts=inserts,
                selection=selection, energySlot4Id=selected_id, energySlot4Pointer=hex(selected_pointer),
                uiConsumerPrefix=ui, uiSelectedPointer=hex(e.reg('rdi')) if ui['stop'] == hex(0x15345e2aa) else None,
                fullAvailabilityExecuted=False, completeComponentConstructor=False)


def run_missing_skill_case(rt, tables, component):
    from .client72_emulator import GLOBAL
    e = rt.e
    depot = next(row for row in tables['AvatarSkillDepot']['rows'] if row['key'] == 704)
    actual_cache = e.read(GLOBAL + 0x16c70)
    empty_cache = e.alloc(0x48)
    e.write(empty_cache, e.read(actual_cache))
    try:
        e.write(GLOBAL + 0x16c70, empty_cache)
        trace = rt.call(0x15345c42d, stops=(0x15345c4fd,), rsi=component, r14=int(depot['pointer'],16))
    finally:
        e.write(GLOBAL + 0x16c70, actual_cache)
    return dict(depotId=704, source=depot['source'], field6c=10068,
                controlledDifference='The actual Skill cache is replaced only for this call by an empty typed Dictionary storage; original cache restored afterwards.',
                originalCacheRestored=e.read(GLOBAL + 0x16c70)==actual_cache,
                trace=trace, scope='Stops at native error helper entry; no exception construction or full handler acceptance claim.')


def static_consumers(rt):
    methods = {int(method['registeredAddress'], 16):dict(owner=owner['index'], ownerName=owner['name'], method=method)
               for owner in rt.records for method in owner['methods'] if int(method['registeredAddress'], 16) in NATIVE_ENTRIES}
    result = []
    for address in NATIVE_ENTRIES:
        rt.bind_function(address)
        end = int(rt.functions[hex(address)]['end'], 16)
        raw = rt.binary[rt.e.pe.offset(address):rt.e.pe.offset(address)+end-address]
        result.append(dict(entry=hex(address), end=hex(end), bytesSHA256=sha256(raw), metadata=methods[address],
                           instructions=[dict(address=hex(ins.address), instruction=ins.mnemonic+' '+ins.op_str)
                                         for ins in rt.decoder.disasm(raw, address)]))
    return result


if __name__ == '__main__':
    if not __package__:
        import sys
        sys.path.insert(0, str(REPO))
        __package__ = 'tools'
    main()
