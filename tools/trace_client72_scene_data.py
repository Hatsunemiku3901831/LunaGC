"""B-02e7 partial native consumption of the reader-created 69612 graph."""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from .client72_session_runtime import fragment
    from .client72_scene_data import SceneDataReader72, SceneDataRuntime72, DATA, FIRST, CACHE, UNKNOWN
    from .client72_scene_nested import CTORS, READERS
    from .trace_client72_scene_nested import ORDER, declarations as nested_declarations, length_field
    from .trace_client72_token import varint
    from . import trace_client72_scene_normal as normal
    from . import trace_client72_scene_query as query
    from . import trace_client72_scene_list as lists
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from client72_session_runtime import fragment
    from client72_scene_data import SceneDataReader72, SceneDataRuntime72, DATA, FIRST, CACHE, UNKNOWN
    from client72_scene_nested import CTORS, READERS
    from trace_client72_scene_nested import ORDER, declarations as nested_declarations, length_field
    from trace_client72_token import varint
    import trace_client72_scene_normal as normal
    import trace_client72_scene_query as query
    import trace_client72_scene_list as lists

RETURN, SCENE_RETURN, NULL, PATCH, PATCH_LIST = END + 0x7000, 0x14CD67473, 0x1404C2BE0, 0x14E2A9FE0, 0x14DE7ACC0
SPECS = (
    ('default', 0, [], None, 0), ('null-message', 17, [1, 0x80000000, 0xFFFFFFFF], 'full', 0),
    ('zero-key-nonempty', 0, [1, 0x80000000, 0xFFFFFFFF], 'full', 1), ('empty-list', 17, [], 'empty', 0),
    ('nonempty-false', 17, [1, 0x80000000, 0xFFFFFFFF], 'full', 0), ('nonempty-true', 17, [1, 0x80000000, 0xFFFFFFFF], 'full', 1),
    ('high-key', 0x80000001, [1, 0x80000000, 0xFFFFFFFF], 'full', 1),
    ('maximum-key-duplicates', 0xFFFFFFFF, [7, 7, 0, 0x80000000, 0xFFFFFFFF], 'full', 0),
    ('missing-cache-dictionary', 17, [1, 0x80000000, 0xFFFFFFFF], 'full', 0),
    ('null-cache-entry', 17, [1, 0x80000000, 0xFFFFFFFF], 'full', 1),
    ('data-ifix', 17, [1, 0x80000000, 0xFFFFFFFF], 'full', 1),
    ('first-ifix', 17, [1, 0x80000000, 0xFFFFFFFF], 'full', 0),
    ('cache-ifix', 17, [7, 7, 0, 0x80000000, 0xFFFFFFFF], 'full', 1),
    ('scene-zero-key', 0, [1, 0x80000000, 0xFFFFFFFF], 'full', 0),
    ('scene-nonempty', 23, [7, 7, 0, 0x80000000, 0xFFFFFFFF], 'full', 0),
    ('scene-high-key', 0x80000001, [1, 0x80000000, 0xFFFFFFFF], 'full', 0),
)
CHILD_LISTS = [[11, 0x80000000, 11], [0, 0xFFFFFFFF], [23, 23, 0x80000001]]
ERROR_SITES = {'missing-cache-dictionary': 0x153A3D65B, 'null-cache-entry': 0x153A3D9B6,
               'data-ifix': 0x153A16B1F, 'first-ifix': 0x153A16D0C, 'cache-ifix': 0x153A3D651}


def declarations(b, m, s, records):
    nested = nested_declarations(b, m, s, records); prior = lists.declarations(b, m, s, records)
    pe = PE(b); h = pe.offset(HEADER_VA); owner = next(r for r in records if r['index'] == 61485)
    resolver = TypeResolver(pe, s, records, h)
    method_base = BODY + (number(b, h + 0xB0) ^ 0x51324A1F)
    params = BODY + ((number(b, h + 0x174) + 0xAE6BCD68) & 0xFFFFFFFF)
    methods = []
    for address, kinds in ((DATA, ['class', 'bool']), (FIRST, ['uint32', 'genericinst', 'bool']), (CACHE, ['uint32', 'genericinst', 'bool'])):
        method = next(x for x in owner['methods'] if x['registeredAddress'] == hex(address))
        if method['parameterCount'] != len(kinds): raise ValueError('Data consumer parameter count drift')
        first = parameter_start(method['index'], number(m, method_base + method['index'] * 26 + 4)); values = []
        for i in range(first, first + len(kinds)):
            _, ref = parameter_words(i, number(m, params + i * 8), number(m, params + i * 8 + 4))
            values.append(resolver.resolve_index(ref))
        if [v['kind'] for v in values] != kinds: raise ValueError('Data consumer parameter kind drift')
        if address == DATA and values[0].get('typeDefinition') != 69612: raise ValueError('Data consumer message type drift')
        if address != DATA and (values[1].get('typeDefinition') != 35584 or [a['kind'] for a in values[1]['arguments']] != ['uint32']):
            raise ValueError('Data consumer repeated uint32 source drift')
        methods.append(dict(entry=hex(address), ownerType=61485, method=method, parameters=values))
    field = next(f for f in owner['fields'] if f.get('offset') == 0x1F8)
    args = field['type'].get('arguments', [])
    if field['type'].get('typeDefinition') != 1222 or len(args) != 2 or args[0]['kind'] != 'uint32' or args[1].get('typeDefinition') != 1255:
        raise ValueError('Data destination cache declaration drift')
    at = pe.offset(0x14CD67467, 12, executable=True); code = b[at:at + 12]
    if code[:7] != bytes.fromhex('488b56484531c0') or code[7] != 0xE8 or 0x14CD67473 + int.from_bytes(code[8:], 'little', signed=True) != DATA:
        raise ValueError('Normal scene original +48/false call source drift')
    return dict(nested=nested, listPrefix=prior, methods=methods, cacheField=field,
                caller=dict(entry='0x14cd67467', bytes=code.hex(), sourceOffset='0x48', boolValue=0, messageType=69612))


def packed(tag, values):
    return length_field(tag, b''.join(varint(v) for v in values))


def wire_for(spec):
    name, key, items, child, flag = spec
    if name == 'default': return b''
    # This is a hand-written input, independent of the native writer.
    wire = (varint(13 * 8) + varint(items[0]) + packed(13, items[1:-1]) + varint(13 * 8) + varint(items[-1])
            if name in ('maximum-key-duplicates', 'cache-ifix', 'scene-nonempty') else packed(13, items))
    wire += varint(11 * 8) + varint(key)
    if child is not None:
        body = b'' if child == 'empty' else b''.join(packed(t, v) for t, v in zip((1, 7, 6), CHILD_LISTS))
        wire += length_field(1, body)
    return wire


def expected_graph(spec):
    _, key, items, child, _ = spec
    nested = None if child is None else dict(typeIndex=25866, fields=dict(zip(('0x18', '0x20', '0x28'), CHILD_LISTS if child == 'full' else [[], [], []])))
    return dict(typeIndex=69612, fields={'0x18': list(items), '0x20': nested, '0x28': key})


def execute(reader, spec, message, owner, registers, entry, stops):
    e = reader.e; name, key, items, child, flag = spec
    runtime = SceneDataRuntime72(reader, owner, message, key, flag, name, query.IFIX_TABLE)
    previous = e.before_instruction
    try:
        trace = fragment(e, entry, stops, registers)
        return dict(name=name, suppliedBool=flag, expected=expected_graph(spec), trace=trace, stop=trace['stop'], **runtime.result(trace['stop']))
    finally:
        e.before_instruction = previous
        runtime.close()


def fixture(reader, spec):
    e = reader.e; name, key, items, child, flag = spec
    e.before_instruction = reader.observe_native
    read = reader.run_message(69612, wire_for(spec))
    state = query.setup(e, reader, 'absent-key', key)
    e.write(STACK + 0x100008, RETURN)
    result = execute(reader, spec, OBJECT, state['owner'],
                     dict(rsp=STACK + 0x100008, rcx=state['owner'], rdx=0 if name == 'null-message' else OBJECT, r8=flag),
                     DATA, {RETURN, UNKNOWN, NULL, PATCH, PATCH_LIST})
    result['reader'] = read
    return result


def scene_fixture(reader, spec):
    e = reader.e; captured = {}; original_fragment, original_encode = normal.fragment, normal.encode_fixture
    def encode(values): return original_encode(values) + length_field(1723, wire_for(spec))
    def extend(engine, entry, stops, registers):
        if entry != normal.ENTRY or lists.NONZERO_RETURN not in stops: raise ValueError('Scene list prefix boundary drift')
        owner, message = e.read(GLOBAL + 0xA400), e.read(OBJECT + 0x48)
        # The outer query cache really contains key17; data uses distinct key23/high or early zero.
        e.write(e.read(owner + 0x90) + 0x44, 1, 'I')
        captured.update(execute(reader, spec, message, owner, registers, entry, {SCENE_RETURN, UNKNOWN, NULL, PATCH, PATCH_LIST}))
        captured['originalMessage48Pointer'] = hex(message)
        return captured['trace']
    normal.fragment, normal.encode_fixture = extend, encode
    try:
        e.before_instruction = reader.observe_native
        prefix = lists.scene_fixture(reader, 'scene-nz-new')
        captured['reader'] = prefix['reader']; captured['prefix'] = {k: v for k, v in prefix.items() if k not in ('reader', 'trace')}
        return captured
    finally:
        normal.fragment, normal.encode_fixture = original_fragment, original_encode


def validate_fixtures(rows, defaults):
    if len(rows) != len(SPECS) or {r['name'] for r in rows} != {s[0] for s in SPECS}: raise ValueError('Incomplete data consumer cases')
    if [r['typeIndex'] for r in defaults] != list(ORDER) or any(hex(CTORS[r['typeIndex']]) not in r['executedAddresses'] for r in defaults):
        raise ValueError('Missing separately executed constructor defaults in ORDER')
    cases = {r['name']: r for r in rows}
    for spec in SPECS:
        name, key, items, child, flag = spec; row = cases[name]
        early = name in ('default', 'null-message', 'zero-key-nonempty', 'scene-zero-key')
        stop = NULL if name in ('missing-cache-dictionary', 'null-cache-entry') else PATCH if name == 'data-ifix' else PATCH_LIST if name in ('first-ifix','cache-ifix') else SCENE_RETURN if name == 'scene-zero-key' else RETURN if early else UNKNOWN
        if row['stop'] != hex(stop) or row['trace']['stop'] != hex(stop): raise ValueError('Data consumer boundary mismatch')
        if row['graphBefore'] != expected_graph(spec) or row['graphAfter'] != row['graphBefore']: raise ValueError('Independent nested input/source graph mismatch')
        read = row['reader']; read_graph = read['storedObject']['fields']['0x48'] if name.startswith('scene-') else read['storedObject']
        required_readers = (READERS[69612],) + ((READERS[25866], CTORS[25866]) if child is not None else ())
        if read_graph != expected_graph(spec) or any(hex(a) not in read['executedAddresses'] or hex(a) in read['models'] for a in required_readers):
            raise ValueError('Actual nested native reader/child constructor evidence missing')
        if (row['child25866Pointer'] == '0x0') != (child is None): raise ValueError('Original child pointer lost')
        addresses = row['trace']['executedAddresses']
        if hex(DATA) not in addresses or {hex(a) for a in (DATA,FIRST,CACHE)} & set(row['trace']['models']): raise ValueError('Data native body omitted or modeled')
        if any(UNKNOWN < int(a,16) < UNKNOWN + 1600 or int(a,16) == 0x14FCDCAB0 for a in addresses):
            raise ValueError('Unverified helper/25866 downstream body crossed')
        if name in ERROR_SITES and hex(ERROR_SITES[name]) not in addresses: raise ValueError('Original error/IFix callsite missing')
        if name.startswith('scene-') and (row['messagePointer'] != row['originalMessage48Pointer'] or not row['prefix']['normalPrefix']['messageContentUnchanged']):
            raise ValueError('Same-CPU original scene +48 pointer/content changed')
        ingress = row['ingress']; first = ingress[0]
        if first != dict(entry=hex(DATA), ownerMatches=True, messageMatches=name != 'null-message', flag=flag): raise ValueError('Original data pointer/bool changed')
        if early:
            if len(ingress) != 1 or row['cacheAfter'] != row['cacheBefore']: raise ValueError('Early data branch mutated cache')
            continue
        if stop != UNKNOWN and row['cacheBefore'] != row['cacheAfter']: raise ValueError('Stopped data branch changed original cache')
        if name == 'data-ifix': continue
        if ingress[1] != dict(entry=hex(FIRST),ownerMatches=True,rawKey=key,sourceListMatches=True,flag=flag): raise ValueError('First helper raw inputs changed')
        if name == 'first-ifix': continue
        if ingress[2] != dict(entry=hex(CACHE),ownerMatches=True,rawKey=key,sourceListMatches=True,flag=flag): raise ValueError('Cache helper raw inputs changed')
        if stop != UNKNOWN: continue
        after = row['cacheAfter']
        if after['items'] != list(items) or after['rawArray'][:len(items)] != list(items) or after['size'] != len(items) or after['version'] != 11:
            raise ValueError('Actual cache clear/InsertRange contents or version mismatch')
        if after['pointer'] != row['cacheBefore']['pointer'] or after['arrayPointer'] != row['cacheBefore']['arrayPointer']:
            raise ValueError('Data existing cache identity/backing array changed')
        expected_raw = row['cacheBefore']['rawArray'][:]; expected_raw[:len(items)] = items
        if after['rawArray'] != expected_raw: raise ValueError('Data cache old backing values changed beyond actual copy')
        if row['unknownArguments'] != dict(cacheListMatches=True,listPointer=after['pointer'],rawKey=key,items=list(items)):
            raise ValueError('Unknown boundary arguments changed')
        if row['dictionary'] != [dict(hash=key & 0x7FFFFFFF,next=-1,key=key,value=after['pointer'])]: raise ValueError('Actual cache dictionary identity/value changed')
        calls = [dict(kind='Count',dataList=True,count=len(items))]
        if items: calls.append(dict(kind='CopyTo',dataList=True,count=len(items),index=0))
        if [c for c in row['interfaceCalls'] if c['dataList']] != calls: raise ValueError('Actual data ICollection Count/CopyTo missing')
        if not any(w == dict(offset='0x18',size=4,value=0,source='0x153a3d86e') for w in row['nativeListWrites']): raise ValueError('Original data clear store missing')
        if name.startswith('scene-') and not all(hex(a) in addresses for a in (normal.ENTRY, query.QUERY, lists.NONZERO, lists.INSERT_RANGE)):
            raise ValueError('Same-CPU original normal/query/list prefix missing')


def recover(b,m,s,records,libraries):
    declared = declarations(b,m,s,records); e = Offline72(b,libraries,records,True); reader = SceneDataReader72(e)
    e.uc.mem_map(RETURN,4096);e.uc.mem_write(RETURN,b'\xc3');e.uc.mem_map(query.IFIX_TABLE,0x400000)
    try:
        defaults = [reader.run_message(index,b'') for index in ORDER]
        rows = [scene_fixture(reader,spec) if spec[0].startswith('scene-') else fixture(reader,spec) for spec in SPECS]
        code = {}
        for address in (DATA,FIRST,CACHE):
            end=e.ends[address];raw=b[e.pe.offset(address,end-address,executable=True):][:end-address]
            code[hex(address)]=dict(size=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    finally: reader.close();e.close()
    validate_fixtures(rows,defaults)
    outcomes = dict(nextHelperEntry=sum(r['stop']==hex(UNKNOWN) for r in rows),
                    earlyReturn=sum(r['stop'] in (hex(RETURN),hex(SCENE_RETURN)) for r in rows),
                    nativeNullEntry=sum(r['stop']==hex(NULL) for r in rows),
                    ifixEntry=sum(r['stop'] in (hex(PATCH),hex(PATCH_LIST)) for r in rows))
    return dict(schemaVersion=1,inputSHA256=HASHES,declarations=declared,nativeCode=code,constructorDefaults=defaults,fixtures=rows,fixtureCount=len(rows),
                outcomeCounts=outcomes,businessSemantics='unresolved',stopsObservedBeforeInstruction=True,
                runtimeStatus='not-tested',targetProtocolReady=False,unknownBodyExecuted=False,child25866ConsumerExecuted=False,
                models=['constructor defaults individually observed in ORDER before parent allocation; parent managed construction dispatch/physical EOF remain unverified',
                        'Graph view adapter delegates same CPU/object to native run_message(SCENE)',
                        'explicit absent-key query storage and independent owner+1F8 existing List<uint32> cache',
                        'initialized metadata; known managed ICollection Count/CopyTo/array allocation storage from prior list fixture'],
                outsideScope=['14AF3D130 body/return and later cache/system behavior','25866 downstream14FCDCAB0 consumption',
                              'bool-dependent later network/system branch','real parent constructor dispatch/physical buffer refill/EOF','IFix implementations',
                              'unknown field business names/complete scene entry/SDK/network ordering/D-02'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('client','output','summary-output'):p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    if a.output.resolve()==a.summary_output.resolve() or any(v.resolve().is_relative_to(a.client.resolve()) for v in (a.output,a.summary_output)):
        p.error('Distinct reports outside client required')
    b,m,s=[read_verified(a.client/n,h) for n,h in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries)
    report['toolSourceSHA256']={n:hashlib.sha256((Path(__file__).resolve().parents[1]/n).read_bytes().replace(b'\r\n',b'\n')).hexdigest() for n in (
        'tools/client72_scene_data.py','tools/trace_client72_scene_data.py','tools/client72_scene_nested.py','tools/trace_client72_scene_nested.py',
        'tools/client72_scene_list.py','tools/trace_client72_scene_list.py','tools/trace_client72_scene_query.py','tools/trace_client72_scene_normal.py')}
    summary={k:v for k,v in report.items() if k not in ('fixtures','constructorDefaults')}
    summary['constructorDefaults']=[dict(typeIndex=r['typeIndex'],storedObject=r['storedObject'],constructor=hex(CTORS[r['typeIndex']])) for r in report['constructorDefaults']]
    summary['observations']=[{k:v for k,v in r.items() if k not in ('trace','reader','prefix')} for r in report['fixtures']]
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=len(report['fixtures']),unknownBodyExecuted=False,child25866ConsumerExecuted=False)))


if __name__=='__main__':main()
