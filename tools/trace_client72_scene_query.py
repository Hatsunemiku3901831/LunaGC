"""B-02e4 native cached query, cold getter boundaries and scene list branches."""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from .client72_scene_reader import SceneReader72
    from .client72_session_runtime import fragment
    from . import trace_client72_scene_normal as normal
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from client72_scene_reader import SceneReader72
    from client72_session_runtime import fragment
    import trace_client72_scene_normal as normal

QUERY, GETTER, LOOKUP, ENUMERATOR = 0x153A18E80, 0x153A357D0, 0x156632D10, 0x1593859B0
NONZERO, ZERO = 0x153A3CB70, 0x15881E210
NULL, BOUNDS, CYCLE = 0x1404C2BE0, 0x1404C2650, 0x159CA53C0
INITIALIZE, DECODE = 0x147BB1B10, 0x14AEF9DB0
QUERY_IFIX, GETTER_IFIX, RETURN, IFIX_TABLE = 0x14D85C250, 0x14E2A5D60, END + 0x3000, 0x613000000
DIRECT = ('absent-buckets', 'absent-key', 'stored-zero', 'stored-nonzero', 'high-key-value', 'maximum-key-value',
          'collision', 'missing-entries', 'out-of-bounds-entry', 'cycle', 'query-ifix', 'getter-ifix',
          'cold-no-source', 'cold-empty-source', 'cold-source-record')
SCENE = ('scene-zero', 'scene-nonzero', 'scene-high-key', 'scene-missing-key')


def declarations(b, m, s, records):
    declared = normal.declarations(b, m, s, records)
    pe = PE(b); h = pe.offset(HEADER_VA); byid = {r['index']: r for r in records}
    resolver = TypeResolver(pe, s, records, h); literal = Literals72(b, m)
    base = number(b, pe.offset(USAGE_REGISTRATION) + 0x48, 'Q'); bindings = []
    for row, cell, source, target, kinds in ((700095, 0x14588CA20, 5203, 1255, ['uint32']),
                                            (702224, 0x1458975F0, 32774, 1222, ['uint32', 'uint32'])):
        key = (row * 0x57C15286 + 0x26DDEBDA) & 0xFFFFFFFF
        value = ((number(m, literal.usages_base + row * 8) + 0xABBDCABF) & 0xFFFFFFFF) ^ key ^ 0x2A357D90
        slot = number(m, literal.usages_base + row * 8 + 4) ^ key ^ 0x1855AA65
        resolved = resolver.resolve_index(value & 0x1FFFFFFF)
        if value >> 29 != 1 or value & 0x1FFFFFFF != source or base + slot * 8 != cell or resolved.get('typeDefinition') != target or [a['kind'] for a in resolved.get('arguments', [])] != kinds:
            raise ValueError('Scene query closed type usage drift')
        bindings.append(dict(usageRow=row, cell=hex(cell), sourceIndex=source, type=resolved))
    cache = next(f for f in byid[61485]['fields'] if f.get('offset') == 0x90)
    source = next(f for f in byid[66294]['fields'] if f['rawOffset'] == '0x4034730')
    if cache['type'].get('typeDefinition') != 1222 or [a['kind'] for a in cache['type']['arguments']] != ['uint32', 'uint32']:
        raise ValueError('Scene query cache declaration drift')
    args = source['type'].get('arguments', [])
    if source['type'].get('typeDefinition') != 1222 or len(args) != 2 or args[0]['kind'] != 'uint32' or args[1].get('typeDefinition') != 70859:
        raise ValueError('Scene query getter source declaration drift')
    source_field = next(f for f in byid[70859]['fields'] if f.get('offset') == 0x80)
    if source_field['type']['kind'] != 'valuetype' or source_field['type'].get('typeDefinition') != 58209:
        raise ValueError('Cold getter raw wrapper declaration drift')
    method_base = BODY + (number(b, h + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(b, h + 0x174) + 0xAE6BCD68) & 0xFFFFFFFF)
    methods = []
    for address, count in ((QUERY, 1), (GETTER, 0), (NONZERO, 2)):
        method = next(x for x in byid[61485]['methods'] if x['registeredAddress'] == hex(address))
        if method['parameterCount'] != count: raise ValueError('Scene query method declaration drift')
        first = parameter_start(method['index'], number(m, method_base + method['index'] * 26 + 4)) if count else 0
        parameters = []
        for index in range(first, first + count):
            _, ref = parameter_words(index, number(m, parameter_base + index * 8), number(m, parameter_base + index * 8 + 4))
            parameters.append(resolver.resolve_index(ref))
        if count and parameters[0]['kind'] != 'uint32': raise ValueError('Query key declaration is not uint32')
        if address == NONZERO and (parameters[1].get('typeDefinition') != 35584 or [a['kind'] for a in parameters[1].get('arguments', [])] != ['uint32']):
            raise ValueError('Nonzero consumer repeated uint32 declaration drift')
        methods.append(dict(entry=hex(address), ownerType=61485, method=method, parameters=parameters, returnTypeStatus='not-recovered'))
    return dict(normal=declared, bindings=bindings, cacheField=cache, sourceGlobalOwner=66294, sourceGlobalField=source,
                sourceRecordField=source_field, methods=methods,
                returnStorage='uint32 value at entries+32+index*16+12; method return declaration is not recovered')


def setup(e, reader, name, key, owner=None):
    owner = owner or e.alloc(1024)
    dictionary, buckets, entries = e.alloc(80), e.alloc(32 + 17 * 4), e.alloc(32 + 4 * 16)
    e.write(owner + 0x90, dictionary); e.write(dictionary + 0x10, buckets); e.write(dictionary + 0x18, entries)
    e.write(buckets + 24, 17, 'I'); e.write(entries + 24, 4, 'I')
    value = {'stored-zero': 0, 'scene-zero': 0, 'high-key-value': 0x80000001,
             'maximum-key-value': 0xFFFFFFFF, 'scene-high-key': 0xFFFFFFFF}.get(name, 7)
    pairs = [] if name in ('absent-buckets', 'absent-key', 'scene-missing-key') or name.startswith('cold-') else [(key, value)]
    if name == 'collision': pairs = [(key, value), (key + 17, 99)]
    for i, (item_key, item_value) in enumerate(pairs):
        hashed = item_key & 0x7FFFFFFF; bucket = buckets + 32 + hashed % 17 * 4
        at = entries + 32 + i * 16
        e.write(at, hashed, 'I'); e.write(at + 4, e.read(bucket, 'I') - 1, 'i')
        e.write(at + 8, item_key, 'I'); e.write(at + 12, item_value, 'I'); e.write(bucket, i + 1, 'I')
    e.write(dictionary + 0x38, len(pairs), 'I')
    if name == 'absent-buckets': e.write(dictionary + 0x10, 0)
    if name == 'missing-entries': e.write(dictionary + 0x18, 0)
    if name == 'out-of-bounds-entry':
        e.write(entries + 24, 1, 'I'); e.write(buckets + 32 + (key & 0x7FFFFFFF) % 17 * 4, 2, 'I')
    if name == 'cycle':
        e.write(entries + 24, 1, 'I'); e.write(entries + 32, (key + 17) & 0x7FFFFFFF, 'I'); e.write(entries + 36, 0, 'i')
    e.write(0x1458330DD, int(name == 'query-ifix'), 'B'); e.write(0x145833093, int(name == 'getter-ifix'), 'B')
    e.write(GLOBAL + 0x232A0, IFIX_TABLE)
    for offset in (0x3E4C88, 0x3E4A38): e.write(IFIX_TABLE + offset, e.alloc())
    e.write(GLOBAL + 0x34730, 0)
    cache_class = e.synthetic_type(0x1458975F0); list_class = e.synthetic_type(0x14588CA20)
    static_class = e.synthetic_type(0x14594FD78); static = e.alloc(); e.write(static_class + 0x38, static); e.write(static, 1)
    original_allocator = reader.allocate; allocations = []
    def allocate():
        klass = e.reg('rcx')
        if klass not in (cache_class, list_class): return original_allocator()
        pointer = e.alloc(80); e.write(pointer, klass); allocations.append(dict(pointer=hex(pointer), kind='cache' if klass == cache_class else 'list'))
        return pointer
    e.models[0x1405336C0] = allocate
    source_record = None
    if name.startswith('cold-'):
        e.write(owner + 0x90, 0)
        if name != 'cold-no-source':
            source = e.alloc(80); e.write(GLOBAL + 0x34730, source)
            if name == 'cold-source-record':
                source_entries = e.alloc(56); source_record = e.alloc(160)
                e.write(source + 0x38, 1, 'I'); e.write(source + 0x18, source_entries); e.write(source_entries + 24, 1, 'I')
                e.write(source_entries + 32, key & 0x7FFFFFFF, 'I'); e.write(source_entries + 40, key, 'I')
                e.write(source_entries + 48, source_record); e.write(source_record + 0x80, 0x12345678, 'I')
    for address in (QUERY, GETTER): e.allow(address)
    for address in (LOOKUP, ENUMERATOR): e.allow_unwind(address)
    return dict(owner=owner, dictionary=dictionary, buckets=buckets, entries=entries, key=key, value=value,
                allocations=allocations, sourceRecord=source_record,
                entriesBefore=bytes(e.uc.mem_read(entries, 96)).hex())


def observe(e, state, trace, name):
    stop = int(trace['stop'], 16)
    original = e.read(OBJECT + 0x20)
    return dict(name=name, key=state['key'], suppliedValue=state['value'], stop=trace['stop'], trace=trace,
                queryReturn=e.reg('eax') if stop == RETURN else None,
                inputEntriesHex=state['entriesBefore'],
                cachedDictionaryPreserved=e.read(state['owner'] + 0x90) == state['dictionary'],
                entriesUnchanged=bytes(e.uc.mem_read(state['entries'], 96)).hex() == state['entriesBefore'],
                allocations=state['allocations'], cachePointer=hex(e.read(state['owner'] + 0x90)),
                decodeRaw=e.read(e.reg('rcx'), 'I') if stop == DECODE else None,
                decodeEnumeratedKey=e.reg('ebx') if stop == DECODE else None,
                branchArguments=(dict(receiverMatches=e.reg('rcx') == state['owner'], key=e.reg('edx'), originalRepeatedMatches=e.reg('r8') == original)
                                 if stop == NONZERO else dict(newListMatches=any(a == dict(pointer=hex(e.reg('rcx')), kind='list') for a in state['allocations']),
                                                            originalRepeatedMatches=e.reg('rdx') == original, third=e.reg('r8'),
                                                            newListArrayIsNull=e.read(e.reg('rcx') + 0x10) == 0) if stop == ZERO else None))


def direct_fixture(e, reader, name):
    e.prepare(); e.heap_cursor = reader.heap_floor
    key = {'high-key-value': 0x80000011, 'maximum-key-value': 0xFFFFFFFF}.get(name, 17)
    state = setup(e, reader, name, key); e.write(STACK + 0x100008, RETURN)
    trace = fragment(e, QUERY, {RETURN, NULL, BOUNDS, CYCLE, QUERY_IFIX, GETTER_IFIX, INITIALIZE, DECODE},
                     dict(rsp=STACK + 0x100008, rcx=state['owner'], rdx=key))
    return observe(e, state, trace, name)


def scene_fixture(reader, name):
    e = reader.e; captured = {}; original_fragment = normal.fragment
    # Reuse the original reader/task setup in this process and extend only its declared stop.
    def extend(engine, entry, stops, registers):
        if entry != normal.ENTRY or stops != {normal.NEXT, normal.NULL, normal.RECYCLE}: raise ValueError('Normal fixture boundary drift')
        key = 0x80000001 if name == 'scene-high-key' else 17
        state = setup(e, reader, name, key, e.read(GLOBAL + 0xA400))
        if name == 'scene-high-key':
            # The native reader produced this value in the uint32-high-bit fixture below.
            if e.read(OBJECT + 0x7C, 'I') != (((key - 0xC0E3) & 0xFFFFFFFF) ^ 0x2D03): raise ValueError('Scene high key wire mismatch')
        trace = original_fragment(engine, entry, {NONZERO, ZERO, NULL, BOUNDS, CYCLE}, registers)
        captured.update(observe(e, state, trace, name)); return trace
    normal.fragment = extend
    try:
        prior = normal.fixture(reader, 'uint32-high-bit' if name == 'scene-high-key' else 'nonzero')
    finally:
        normal.fragment = original_fragment
    captured['normalPrefix'] = {k: v for k, v in prior.items() if k not in ('trace', 'stage')}
    return captured


def validate_fixtures(rows):
    if len(rows) != len(DIRECT + SCENE) or {r['name'] for r in rows} != set(DIRECT + SCENE): raise ValueError('Incomplete scene query coverage')
    boundaries = {'missing-entries': NULL, 'out-of-bounds-entry': BOUNDS, 'cycle': CYCLE, 'query-ifix': QUERY_IFIX,
                  'getter-ifix': GETTER_IFIX, 'cold-no-source': INITIALIZE, 'cold-source-record': DECODE}
    sites = {'missing-entries': 0x156632F9C, 'out-of-bounds-entry': 0x153A18F2E, 'cycle': 0x156632FA1,
             'query-ifix': 0x153A18F1A, 'getter-ifix': 0x153A35A34, 'cold-no-source': 0x153A35846, 'cold-source-record': 0x153A3594F}
    for r in rows:
        name = r['name']; expected = boundaries.get(name, NONZERO if name in ('scene-nonzero', 'scene-high-key') else ZERO if name.startswith('scene-') else RETURN)
        if r['stop'] != hex(expected) or r['trace']['stop'] != hex(expected): raise ValueError('Query native stop mismatch')
        addresses = r['trace']['executedAddresses']
        if hex(QUERY) not in addresses: raise ValueError('Query body omitted')
        if name in sites and hex(sites[name]) not in addresses: raise ValueError('Wrong native error/IFix/source boundary branch')
        if {hex(a) for a in (QUERY, GETTER, LOOKUP, ENUMERATOR)} & set(r['trace']['models']): raise ValueError('Native query/getter replaced with model')
        if not r['entriesUnchanged']: raise ValueError('Query changed supplied entries')
        if not name.startswith('cold-') and not r['cachedDictionaryPreserved']: raise ValueError('Cached dictionary pointer changed')
        if expected == RETURN:
            value = {'high-key-value': 0x80000001, 'maximum-key-value': 0xFFFFFFFF}.get(name, 0 if name in ('absent-buckets', 'absent-key', 'stored-zero', 'cold-empty-source') else 7)
            if r['queryReturn'] != value or hex(GETTER) not in addresses or hex(LOOKUP) not in addresses: raise ValueError('Native query value mismatch')
        if name == 'high-key-value' and (r['key'], r['queryReturn']) != (0x80000011, 0x80000001): raise ValueError('High-bit query coverage missing')
        if name == 'maximum-key-value' and (r['key'], r['queryReturn']) != (0xFFFFFFFF, 0xFFFFFFFF): raise ValueError('Maximum uint32 query coverage missing')
        if name == 'cold-empty-source' and (r['cachedDictionaryPreserved'] or not any(a['kind'] == 'cache' for a in r['allocations']) or hex(ENUMERATOR) not in addresses):
            raise ValueError('Cold getter allocation/enumeration omitted')
        if name == 'cold-source-record' and (r['decodeRaw'] != 0x12345678 or r['decodeEnumeratedKey'] != r['key'] or hex(ENUMERATOR) not in addresses):
            raise ValueError('Cold getter source record mismatch')
        if name.startswith('scene-'):
            prefix = r['normalPrefix']; source = prefix['reader']['storedObject']
            if prefix['stop'] != r['stop'] or not prefix['messageContentUnchanged'] or hex(normal.ENTRY) not in addresses or hex(normal.INSERT) not in addresses:
                raise ValueError('Scene reader/native task prefix lost')
            if source['repeatedUint32'] != [1, 0x80000000, 0xFFFFFFFF] or source['rawStoredScalars']['0x68'] != 0xFEDCBA9876543210:
                raise ValueError('Scene independent wire list/uint64 mismatch')
            if ((source['rawStoredScalars']['0x7c'] ^ 0x2D03) + 0xC0E3) & 0xFFFFFFFF != r['key']:
                raise ValueError('Scene native decoded key/wire mismatch')
            if expected == NONZERO:
                if r['branchArguments'] != dict(receiverMatches=True, key=r['key'], originalRepeatedMatches=True): raise ValueError('Nonzero original argument mismatch')
            elif r['branchArguments'] != dict(newListMatches=True, originalRepeatedMatches=True, third=0, newListArrayIsNull=True):
                raise ValueError(f'Zero branch list/source argument mismatch: {name}: {r["branchArguments"]}')


def recover(b, m, s, records, libraries):
    declared = declarations(b, m, s, records)
    e = Offline72(b, libraries, records, True); reader = SceneReader72(e)
    e.uc.mem_map(RETURN, 4096); e.uc.mem_write(RETURN, b'\xc3'); e.uc.mem_map(IFIX_TABLE, 0x400000)
    try:
        rows = [direct_fixture(e, reader, name) for name in DIRECT] + [scene_fixture(reader, name) for name in SCENE]
        code = {}
        for address in (QUERY, GETTER, LOOKUP, ENUMERATOR):
            end = e.ends[address] if address in (QUERY, GETTER) else e.unwind_ranges[address]
            raw = b[e.pe.offset(address, end - address, executable=True):][:end - address]
            code[hex(address)] = dict(size=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    finally:
        reader.close(); e.close()
    validate_fixtures(rows)
    return dict(schemaVersion=1, inputSHA256=HASHES, declarations=declared, nativeCode=code, fixtures=rows, fixtureCount=len(rows),
                runtimeStatus='not-tested', targetProtocolReady=False, consumerBodiesExecuted=False,
                models=['normal reader/task explicit storage from B-02e3', 'cached Dictionary<uint32,uint32> fixed entries/buckets with null comparer',
                        'managed cache/list allocation and initialized generic static storage; list constructor body is stopped',
                        'cold source empty dictionary or one explicit raw source record; decoder/initialization bodies stopped'],
                outsideScope=['query key/value business semantics', 'real GLOBAL+34730 initialization/data population and source record decoder',
                              'custom comparer and dictionary mutation/growth', 'IFix bodies',
                              '153A3CB70 and 15881E210 bodies/later scene data consumers', 'SDK/media/network order/live scene entry/D-02'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'summary-output'): p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--libraries', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    if a.output.resolve() == a.summary_output.resolve() or any(x.resolve().is_relative_to(a.client.resolve()) for x in (a.output, a.summary_output)):
        p.error('Distinct report paths outside client required')
    b, m, s = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True); report = recover(b, m, s, records, a.libraries)
    report['toolSourceSHA256'] = {n: hashlib.sha256((Path(__file__).resolve().parents[1] / n).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                                 for n in ('tools/trace_client72_scene_query.py', 'tools/trace_client72_scene_normal.py', 'tools/client72_scene_reader.py', 'tools/client72_stage_runtime.py')}
    summary = {k: v for k, v in report.items() if k != 'fixtures'}
    summary['observations'] = [{k: v for k, v in row.items() if k not in ('trace', 'normalPrefix')} for row in report['fixtures']]
    for path, value in ((a.output, report), (a.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=len(report['fixtures']), runtimeStatus='not-tested', consumerBodiesExecuted=False)))


if __name__ == '__main__': main()
