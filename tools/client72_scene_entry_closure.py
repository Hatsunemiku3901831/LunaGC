"""Bounded CN72 SceneInfo continuation. Offline CPU only; no production writes."""
import argparse
import hashlib
import importlib.util
import json
import struct
import sys
from pathlib import Path

try:
    from .client72_scene_deep import (inputs, raw_declarations, avatar_aot, Offline72,
        SceneInfoRaw72, SceneContractRuntime, PE, scalar, message, OBJECT, GLOBAL,
        END, RETURN, clear_model, selected_aot, TypeResolver, HEADER_VA, number)
    from .extract_client72 import CODE_REGISTRATION
    from .trace_client72_token import load_emulator
    from .extract_client72_literals import Literals72
    from .extract_client72_enums import recover as recover_enums
except ImportError:
    from client72_scene_deep import (inputs, raw_declarations, avatar_aot, Offline72,
        SceneInfoRaw72, SceneContractRuntime, PE, scalar, message, OBJECT, GLOBAL,
        END, RETURN, clear_model, selected_aot, TypeResolver, HEADER_VA, number)
    from extract_client72 import CODE_REGISTRATION
    from trace_client72_token import load_emulator
    from extract_client72_literals import Literals72
    from extract_client72_enums import recover as recover_enums

CONSUMER, INSERT = 0x1507E44F0, 0x1553C8AF0
TABLE_RUNTIME_SHA = 'a1955fcf4b47dd70cf0dc51495b30307beb8d54a7b5b960dca97503ac1efe48f'
SCENE_SHA = '2964028cba6cd66b4c04e86472411d10756643e230c1b6b5c34206ab7a0e0ce0'
LOCATOR_SHA = 'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda'
PROFILE_SHA = 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'
PINS = {
    'scene-info-raw-observations.json': 'f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75',
    'scene-info-contract-observations.json': 'a0c88290e5d4bbd31b2ea0e83986ffc9e51235462b727429e441cba8cd48a2ad',
    'scene-deep-observations.json': 'bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5',
    'scene-deep-followup-observations.json': '755ff3ad04ec69684b608de36667619746ec7421b463a5728b32f292b613e974',
    'scene-handshake-observations.json': 'b2ff1ac423fc3f1685715a261452359265a7dd0bfdb2821b6af3c55866ad8847',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--static-only', action='store_true')
    parser.add_argument('--scene-runtime', type=Path, default=Path('local/migration72/d02-active/scene-entry-astra/natural-runtime-snapshot.py'))
    parser.add_argument('--scene-table', type=Path, default=Path('local/migration72/d02-active/resource-locator-astra/locator-final-1/Scene.native-m_bytes.bin'))
    parser.add_argument('--locator-report', type=Path, default=Path('local/migration72/d02-active/resource-locator-astra/locator-final-1.json'))
    args = parser.parse_args()
    proof = verify_inputs(args)
    binary, metadata, startup, records, cells, bindings, strings, items = inputs(args)
    audit = declarations(binary, metadata, startup, records, cells, args.python_libs)
    report = dict(schemaVersion=1, task='D02-R4-scene-entry-closure', inputs=proof,
        declarations=audit, runtimeStatus='not-tested', targetProtocolReady=False,
        fullNetworkHandler=False, fullSceneEntityEffects=False,
        productionRequiredFieldSetApproved=False, naturalAbilityHashRegistryLoaded=False)
    if not args.static_only:
        raw = raw_declarations(binary, metadata, startup, records, cells, args.python_libs)
        outer = avatar_aot(binary, metadata, startup, records, dict(types=[]))
        engine = Offline72(binary, args.python_libs, records, trace_instructions=False)
        graph = SceneInfoRaw72(engine, args.directory, cells, bindings, items, strings, outer, raw)
        engine.uc.mem_map(END, 4096)
        engine.uc.mem_write(RETURN, b'\xc3')
        engine.hooks.append(engine.uc.hook_add(engine.U.UC_HOOK_CODE, engine._code, begin=END, end=END+4095))
        try:
            report['floatFixtures'] = []
            for case in float_cases():
                print('native float fixture: '+case['name'], flush=True)
                report['floatFixtures'].append(float_case(engine, graph, audit, case))
            report['tokenFixtures'] = token_cases(engine, graph)
        finally:
            graph.close()
            engine.close()
        report['sceneTable'] = scene_table(args,binary,metadata,startup,records,cells)
    report['sourceSHA256'] = {name: sha(Path(name).read_bytes()) for name in (
        'tools/client72_scene_entry_closure.py', 'tools/client72_scene_info_contract.py',
        'tools/client72_scene_deep.py', 'tools/trace_client72_scene_info_raw.py',
        'tools/client72_emulator.py', 'tools/client72_graph.py', 'tools/client72_session_runtime.py',
        'tools/client72_resource_deep.py', 'tools/client72_avatar_deep.py', 'tools/client72_avatar_cold_interfaces.py')}
    report['javaSourceSHA256'] = {name:sha(Path(name).read_bytes()) for name in (
        'src/main/java/emu/grasscutter/server/packet/send/PacketPlayerEnterSceneInfoNotify.java',
        'src/main/java/emu/grasscutter/server/packet/send/PacketSceneTeamUpdateNotify.java',
        'src/main/java/emu/grasscutter/server/packet/send/PacketSceneEntityAppearNotify.java',
        'src/main/java/emu/grasscutter/server/packet/recv/HandlerSceneInitFinishReq.java',
        'src/main/java/emu/grasscutter/server/packet/recv/HandlerEnterSceneDoneReq.java',
        'src/main/java/emu/grasscutter/game/world/Scene.java',
        'src/main/java/emu/grasscutter/game/avatar/AvatarStorage.java',
        'src/main/java/emu/grasscutter/game/inventory/GameItem.java')}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False)+'\n', 'utf8')
    print(json.dumps(dict(floatFixtures=len(report.get('floatFixtures', [])), fullNetworkHandler=False)))


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def verify_inputs(args):
    if args.output.resolve().is_relative_to(args.client.resolve()):
        raise ValueError('Output must be outside the fixed client')
    profile_bytes=(args.directory/'profile.json').read_bytes()
    if sha(profile_bytes)!=PROFILE_SHA:
        raise ValueError('Fixed five-input profile changed')
    profile = json.loads(profile_bytes)
    if set(profile['files']) != {'binary', 'metadata', 'startup', 'sdk', 'astrolabe'}:
        raise ValueError('Five profile inputs required')
    checks = {}
    for key, item in profile['files'].items():
        raw = (args.client/item['path']).read_bytes()
        if len(raw) != item['size'] or sha(raw) != item['sha256']:
            raise ValueError('Fixed client input changed: '+key)
        checks[key] = dict(size=len(raw), sha256=sha(raw))
    for name, digest in PINS.items():
        raw = (args.directory/name).read_bytes()
        if sha(raw) != digest:
            raise ValueError('Reviewed Scene evidence changed: '+name)
        source = json.loads(raw).get('sourceSHA256', {})
        for path, expected in source.items():
            if sha(Path(path).read_bytes()) != expected:
                raise ValueError('Reviewed source changed: '+path)
    return dict(profileSHA256=sha((args.directory/'profile.json').read_bytes()),
        files=checks, reviewedEvidenceSHA256=PINS)


def shared_iterator_aot(binary, metadata, startup, records):
    """Retain zero native entries instead of confusing them with callable bodies."""
    pe = PE(binary)
    h, cr = pe.offset(HEADER_VA), pe.offset(CODE_REGISTRATION)
    specs = 0x210 + (number(binary, h+0x170) ^ 0x6347382B)
    count = (number(binary, cr+0x64) + 0x9EA91E3B) & 0xffffffff
    pointers = pe.offset(number(binary, cr+0x30, 'Q'), count*8)
    table = 0x210 + (number(binary, h+0xEC) ^ 0x3CF0720C)
    size = (number(binary, h+0x110) + 0xA3D9CC8B) & 0xffffffff
    owners = {method['index']: (record['index'], method) for record in records for method in record['methods']}
    resolver = TypeResolver(pe, startup, records, h)
    rows = []
    for index in range(size//14):
        pointer, spec = struct.unpack_from('<ii', metadata, table+index*14)
        method, instance, method_instance = struct.unpack_from('<iii', metadata, specs+spec*12)
        owner, row = owners[method]
        if owner not in (33677, 33678) or instance != 0 or row['name'] not in (
            'MoveNext', 'get_Current', 'Dispose', 'System.Collections.Generic.IEnumerable<T>.GetEnumerator'):
            continue
        if method_instance != -1:
            raise ValueError('Iterator unexpectedly has method arguments')
        address = number(binary, pointers+pointer*8, 'Q')
        rows.append(dict(owner=owner, method=row['name'], virtualSlot=row['virtualSlot'],
            methodDefinition=method, methodSpec=spec, tableIndex=index, pointerIndex=pointer,
            classInstance=instance, address=hex(address),
            arguments=[resolver.resolve_index(x) for x in resolver.arguments[instance]]))
    return rows


def scene_table(args,binary,metadata,startup,records,cells):
    source=args.scene_runtime.read_bytes()
    blob=args.scene_table.read_bytes()
    locator=args.locator_report.read_bytes()
    if sha(source)!=TABLE_RUNTIME_SHA or sha(blob)!=SCENE_SHA or sha(locator)!=LOCATOR_SHA:
        raise ValueError('Frozen Scene input/runtime/locator changed')
    targets=[x for x in json.loads(locator)['targets'] if x['name']=='Scene']
    if len(targets)!=1 or targets[0]['entry']['sha256']!=SCENE_SHA or targets[0]['literal']['cell']!='0x145b65db0':
        raise ValueError('Scene locator identity differs')
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
    spec=importlib.util.spec_from_file_location('tools._client72_scene_frozen_runtime',args.scene_runtime)
    runtime=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runtime)
    runtime.TABLES['Scene']=(0x147BB17E0,70859,0x34730,2431,SCENE_SHA)
    rt=runtime.NaturalRuntime(binary,metadata,startup,records,cells,args.python_libs,'Scene')
    e=rt.e
    gsbase=0x630000000
    e.uc.mem_map(gsbase,4096);e.uc.reg_write(e.R.UC_X86_REG_GS_BASE,gsbase)
    tlsarray,tlsblock=e.alloc(256),e.alloc(8192)
    e.write(gsbase+0x58,tlsarray);e.write(tlsarray,tlsblock);e.write(0x145EEF080,0,'I')
    slab,buffer=e.alloc(32),e.alloc(4096)
    e.write(tlsblock+0x1998,4096);e.write(tlsblock+0x19a0,slab);e.write(tlsblock+0x19a8,slab)
    e.write(slab,buffer);e.write(slab+16,4096)
    rt.bind_function(0x1405070C0)
    scratch=[]
    def observe_scratch(uc,address,size,data):
        scratch.append(dict(caller=hex(e.read(e.reg('rsp'))),count=e.reg('rdx'),usedBefore=e.read(slab+8)))
    e.uc.hook_add(e.U.UC_HOOK_CODE,observe_scratch,begin=0x1405070C0,end=0x1405070C0)
    try:
        result=rt.parse_table(blob)
        queries=[]
        dictionary=e.read(GLOBAL+0x34730)
        for key in (3,4,0xffffffff):
            trace=rt.call(0x1578B66F0,rcx=dictionary,rdx=key)
            position=e.reg('eax')
            actual_key=None if position==0xffffffff else e.read(e.read(dictionary+0x18)+0x28+position*24,'I')
            queries.append(dict(key=key,position=position,storedKey=actual_key,trace=trace))
        selected=[]
        enum_indices={f['type']['typeDefinition'] for f in records[70859]['fields'] if f['type']['kind']=='valuetype'
            and records[f['type']['typeDefinition']]['parent']['typeDefinition']==204}
        enums=recover_enums(binary,metadata,records,indices=tuple(sorted(enum_indices)))['enums']
        for row in result['rows']:
            if row['key'] not in (1,3,4):continue
            pointer=int(row['pointer'],16)
            fields=[]
            for field in records[70859]['fields']:
                offset=field['offset'];typ=field['type'];item=dict(name=field['name'],offset=offset,type=typ)
                if typ['kind']=='string':item['value']=rt.strings.get(e.read(pointer+offset))
                elif typ['kind']=='bool':item['value']=bool(e.read(pointer+offset,'B'))
                elif typ.get('typeDefinition')==58209:item['value']=rt.decode(pointer+offset)
                elif typ.get('typeDefinition') in enum_indices:item['value']=e.read(pointer+offset,'I')
                else:continue
                fields.append(item)
            selected.append(dict(key=row['key'],fields=fields))
        result.update(runtimeSourceSHA256=TABLE_RUNTIME_SHA,locatorSHA256=LOCATOR_SHA,
            loader=records[66294],rowType=records[70859],rowEnums=enums,selectedRows=selected,
            tlsScratchRequests=scratch,tlsSlabUsedAtEnd=e.read(slab+8),
            nativeKeyQueries=queries,
            tlsModel='Explicit OS TLS index/storage and preallocated 4096B slab; native 1405070C0 allocation and inlined release execute',
            actualRevisionSelected=False,naturalSceneScriptStageObserved=False,
            scalarStorageModel='Reviewed paired permutation/seed storage fixture; not the file cipher or naturally captured encode table')
        if e.read(slab+8)!=0 or '0x1405070c0' in result['trace']['models']:
            raise ValueError('Native scratch lifetime differs')
        boundary=runtime.NaturalRuntime(binary,metadata,startup,records,cells,args.python_libs,'Scene')
        try:
            ee=boundary.e
            boundary.reader=ee.alloc(64)
            short=boundary.array(4,1)
            ee.uc.mem_write(short+32,blob[:4]);ee.write(boundary.reader+0x10,short)
            short_trace=boundary.call(0x147BB17E0,rcx=boundary.reader)
            if short_trace['stop']!='0x1404c2650':
                raise ValueError('Truncated Scene row reached unexpected boundary')
            result['truncatedFirstRow']=dict(inputSHA256=sha(blob[:4]),inputBytes=4,
                cursor=ee.read(boundary.reader+0x1c,'I'),trace=short_trace,
                scope='Native bounds helper on actual four-byte header with missing first row; not all malformed-table cases')
        finally:
            boundary.e.close()
        return result
    except Exception:
        args.output.with_suffix('.scene-failure.json').write_text(json.dumps(dict(
            failure=e.failure,cursor=e.read(rt.reader+0x1c,'I') if rt.reader else None,
            registers={name:hex(e.reg(name)) for name in ('rip','rcx','rdx','rax','rsp')},
            nativeSources=rt.functions,callOrder=rt.call_order,tlsScratchRequests=scratch),indent=2)+'\n','utf8')
        raise
    finally:
        e.close()


def declarations(binary, metadata, startup, records, cells, libraries):
    load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    decoder.detail = True
    pe = PE(binary)
    optional = number(binary, 0x3c)+24
    rva, length = struct.unpack_from('<II', binary, optional+112+24)
    offset = pe.offset(pe.base+rva)
    extents = {pe.base+a: pe.base+b for a,b,_ in struct.iter_unpack('<III', binary[offset:offset+length])}
    iterator = shared_iterator_aot(binary, metadata, startup, records)
    dictionary = selected_aot(binary, metadata, startup, records,
        {(1222,5971): ('TryInsert','FindEntry','Clear','set_Item','get_Item')})
    if next(x['address'] for x in dictionary if x['method']=='TryInsert') != hex(INSERT):
        raise ValueError('Concrete uint32/float dictionary differs')
    entries = {CONSUMER, INSERT, 0x1507D1620, 0x1517ACC90, 0x14828FEA0, 0x14828BA00,
        0x14CD68530, 0x148290060, 0x14828F870, 0x15154BF20, 0x14CD5F7B0}
    entries.update(int(x['address'],16) for x in iterator if int(x['address'],16))
    rows = []
    for entry in sorted(entries):
        end = extents.get(entry)
        if end is None and binary[pe.offset(entry)] == 0xc3:
            end = entry+1
        if end is None:
            raise ValueError('Missing bounded native extent: '+hex(entry))
        code = binary[pe.offset(entry):pe.offset(entry)+end-entry]
        instructions, usages, calls = [], [], []
        for ins in decoder.disasm(code,entry):
            instructions.append(dict(address=hex(ins.address), bytes=ins.bytes.hex(), mnemonic=ins.mnemonic, operands=ins.op_str))
            if ins.mnemonic=='call' and ins.operands[0].type==2:
                calls.append(dict(site=hex(ins.address), target=hex(ins.operands[0].imm)))
            for operand in ins.operands:
                if operand.type==3 and operand.mem.base==41:
                    cell=ins.address+ins.size+operand.mem.disp
                    if cell in cells:
                        usages.append(dict(site=hex(ins.address), **cells[cell]))
        rows.append(dict(entry=hex(entry), moduleRVA=hex(entry-pe.base), extentEnd=hex(end),
            codeSHA256=sha(code), instructions=instructions, typeUsages=usages, directCalls=calls))
    field=next(f for f in records[56238]['fields'] if f['offset']==0x110)
    if field['type']['instanceIndex'] != 5971:
        raise ValueError('Network state dictionary layout differs')
    adjustors=[]
    pattern=b'\x48\x83\xc1\x10\xe9'
    for va, size, raw, _ in pe.sections:
        data=binary[raw:raw+size]
        offset=data.find(pattern)
        while offset >= 0:
            target=va+offset+9+struct.unpack_from('<i', data, offset+5)[0]
            if target in {int(x['address'],16) for x in iterator if x['owner']==33678}:
                thunk=data[offset:offset+9]
                decoded=list(decoder.disasm(thunk,va+offset))
                if len(decoded)!=2 or decoded[0].op_str!='rcx, 0x10' or decoded[1].mnemonic!='jmp':
                    raise ValueError('Iterator unbox thunk decode differs')
                adjustors.append(dict(entry=hex(va+offset),target=hex(target),bytes=thunk.hex(),codeSHA256=sha(thunk)))
            offset=data.find(pattern,offset+1)
    token_literal=Literals72(binary,metadata).selected((52998,))['literals']
    if len(token_literal)!=1 or token_literal[0]['value']!='[Loading] LoadingTask GetLoadingTask is Null token:' or token_literal[0]['cell']!='0x145b31650':
        raise ValueError('Independent loading token literal differs')
    return dict(native=rows, tokenDiagnosticLiteral=token_literal, floatDictionaryField=field, floatDictionaryAot=dictionary, iteratorAdjustors=adjustors,
        sharedIteratorAot=iterator, listType=next(f for f in records[49723]['fields'] if f['offset']==0x18),
        floatMessageFields=[f for f in records[80154]['fields'] if f['offsetKind']=='instance'])


def float_cases():
    return [dict(name='two-nonzero-values', pairs=[(1,0x3fa00000),(8,0xc0200000)]),
        dict(name='same-masked-hash-high-bit', pairs=[(1,0x3f800000),(0x80000001,0x40000000)]),
        dict(name='duplicate-last-value-wins', pairs=[(7,0x3f800000),(7,0xc0200000),(0xffffffff,0x7f7fffff)]),
        dict(name='zero-subnormal-negative-zero', pairs=[(0,0),(1,1),(0xffffffff,0x80000000)]),
        dict(name='empty-clears-old-state', pairs=[]),
        dict(name='protected-entity-keeps-old-state', pairs=[(9,0x3fa00000)], entityFlag=1),
        dict(name='unprotected-entity-replaces-old-state', pairs=[(9,0x3fa00000)], entityFlag=0),
        dict(name='null-list-clears-before-error', pairs=[], nullList=True),
        dict(name='null-dictionary-native-error', pairs=[(1,0x3f800000)], nullDictionary=True),
        dict(name='growth-stops-at-real-resize', pairs=[(x,0x3f800000+x) for x in range(9)], capacity=8)]


def float_case(e, g, audit, case):
    nested=b''.join(message(3,scalar(1,key)+b'\x15'+struct.pack('<I',bits)) for key,bits in case['pairs'])
    wire=scalar(12,0x8000005b)+message(2,scalar(13,9007199254740993))+message(15,nested)
    e.before_instruction=g.observe_entry
    reader=g.run_graph(34208,wire)
    rt=SceneContractRuntime(e,g,[])
    listing=e.read(e.read(OBJECT+0x20)+0x18)
    interface_types={int(x['cell'],16):x for row in audit['native'] if row['entry']==hex(CONSUMER) for x in row['typeUsages']}
    interfaces={cell:e.synthetic_type(cell) for cell in interface_types}
    enumerable=next(interfaces[c] for c,r in interface_types.items() if r['type'].get('typeDefinition')==1247)
    enumerator_interface=next(interfaces[c] for c,r in interface_types.items() if r['type'].get('typeDefinition')==1248)
    move_interface=next(interfaces[c] for c,r in interface_types.items() if r['type'].get('name')=='IEnumerator')
    disposable=next(interfaces[c] for c,r in interface_types.items() if r['type'].get('name')=='IDisposable')
    methods={x['method']:int(x['address'],16) for x in audit['sharedIteratorAot']}
    adjustors={int(x['target'],16):int(x['entry'],16) for x in audit['iteratorAdjustors']}
    gateways=[]
    for name in ('MoveNext','get_Current'):
        target=methods[name]
        gateway=e.model_stub(lambda: 0)
        e.models.pop(gateway)
        # Explicit managed unbox model, not client bytes or a successful business call.
        code=b'\x48\x83\xc1\x10\x48\xb8'+struct.pack('<Q',target)+b'\xff\xe0'
        e.uc.mem_write(gateway,code)
        e.allowed.append((gateway,gateway+len(code)))
        adjustors[target]=gateway
        gateways.append(dict(address=hex(gateway),target=hex(target),bytes=code.hex(),
            scope='synthetic managed unbox gateway; receiver+16 only, result computed by native body'))
    listclass=e.alloc(1024)
    table=e.alloc(16)
    e.write(listing,listclass)
    e.write(listclass+0xc0,1,'H');e.write(listclass+0xbe,64,'H');e.write(listclass+0x80,table)
    e.write(table,enumerable);e.write(table+8,16,'I')
    e.write(listclass+0xd0+16*8,methods['System.Collections.Generic.IEnumerable<T>.GetEnumerator'])
    e.write(listclass+0x230,e.model_stub(rt.enumerate))
    iteratorclass=e.alloc(1024)
    itable=e.alloc(48)
    e.write(iteratorclass+0xc8,1,'B');e.write(iteratorclass+0xc0,3,'H');e.write(iteratorclass+0xbe,64,'H');e.write(iteratorclass+0x80,itable)
    for pos,(interface,slot) in enumerate(((enumerator_interface,4),(move_interface,6),(disposable,5))):
        e.write(itable+pos*16,interface);e.write(itable+pos*16+8,slot,'I')
    for name,slot in (('MoveNext',6),('get_Current',4),('Dispose',5)):
        entry=methods[name]
        e.write(iteratorclass+0xd0+slot*8,adjustors.get(entry,entry))
    info,klass,ctx=e.alloc(),e.alloc(),e.alloc()
    e.write(info,klass);e.write(klass+0x30,ctx);e.write(ctx+0x18,e.alloc());e.write(ctx+0x20,iteratorclass)
    e.write(listclass+0xd0+(16+64)*8,info)
    boxes=[]
    def box():
        if e.reg('rcx') != iteratorclass:
            raise ValueError('Unexpected boxed type')
        pointer=e.alloc(64)
        e.write(pointer,iteratorclass)
        e.uc.mem_write(pointer+16,bytes(e.uc.mem_read(e.reg('rdx'),48)))
        boxes.append(pointer)
        return pointer
    saved={a:e.models.get(a) for a in (0x140525C40,0x159C68C70)}
    clears=[]
    e.models[0x140525C40]=box
    e.models[0x159C68C70]=clear_model(e,g,clears)
    for address in (CONSUMER,INSERT,0x1517ACC90,*methods.values()):
        if address==methods['Dispose']:
            e.allowed.append((address,address+1))
        else:
            e.allow_unwind(address)
        e.models.pop(address,None)
    for item in audit['iteratorAdjustors']:
        address=int(item['entry'],16)
        e.allowed.append((address,address+len(bytes.fromhex(item['bytes']))))
    dictionary=e.alloc(80)
    capacity=case.get('capacity',16)
    entries=g.new_array(16,capacity)
    buckets=g.new_array(4,7)
    e.write(dictionary+0x10,entries);e.write(dictionary+0x18,buckets);e.write(dictionary+0x40,0xffffffff,'I')
    e.write(dictionary+0x38,11,'I');e.write(dictionary+0x44,1,'I')
    e.write(entries+32,99,'I');e.write(entries+36,0xffffffff,'I');e.write(entries+40,99,'I');e.write(entries+44,0x3f000000,'I')
    e.write(buckets+32+(99%7)*4,1,'I')
    e.write(rt.network+0x110,0 if case.get('nullDictionary') else dictionary)
    entity=None
    if 'entityFlag' in case:
        scene=e.read(GLOBAL+0xa3a0);e.write(scene+0x230,0x80000077,'I');e.write(scene+0x284,0x8000005b,'I')
        old_entries=g.new_array(24)
        e.write(rt.old_entity_dictionary+24,old_entries)
        entity=e.alloc(0x500);e.write(entity+0x80,e.alloc());e.write(entity+0x426,case['entityFlag'],'B')
        e.write(old_entries+48,entity)
        previous=e.models[0x1578B66F0]
        e.models[0x1578B66F0]=lambda: (0 if e.reg('edx')==0x80000077 else 0xffffffff) if e.reg('rcx')==rt.old_entity_dictionary else previous()
    observed=[]
    def capture(uc,address,size,data):
        if address==INSERT:
            observed.append(dict(key=e.reg('edx'),floatBits=e.reg('xmm2')&0xffffffff,behavior=e.reg('r9b')))
    hook=e.uc.hook_add(e.U.UC_HOOK_CODE,capture,begin=INSERT,end=INSERT)
    try:
        trace=rt.invoke(CONSUMER,{RETURN,0x1404C2BE0,0x159C329A0,0x1553C9040},rcx=rt.network,rdx=0 if case.get('nullList') else listing)
        count=e.read(dictionary+0x44,'I')
        result=[dict(key=e.read(entries+32+i*16+8,'I'),floatBits=e.read(entries+32+i*16+12,'I')) for i in range(count)]
        return dict(name=case['name'],wire=wire.hex(),wireSHA256=sha(wire),reader=reader,
            entityFlag=case.get('entityFlag'),entityLookupIds=rt.scene_lookups,trace=trace,
            nativeInsertArguments=observed,result=result,version=e.read(dictionary+0x38,'I'),clearCalls=clears,
            boxedEnumerators=len(boxes),modeledUnboxGateways=gateways,
            criticalModeledAddresses=sorted(set(trace['models'])&{hex(CONSUMER),hex(INSERT),*map(hex,methods.values())}),
            models=['Managed typed interface tables/context and box allocation/copy; synthetic unbox gateways are explicit, native MoveNext/current and consumer execute',
                'Existing reader backing-array/count descriptor copied to enumerator by reviewed storage model',
                'Array.Clear byte operation and scene FindEntry fixture only; dictionary hash/collision/store native',
                'Preallocated dictionary capacity is fixture; actual resize entry is a stop, not a client capacity limit'])
    finally:
        e.uc.hook_del(hook)
        for address,model in saved.items():
            if model is None:e.models.pop(address,None)
            else:e.models[address]=model
        rt.close()


def token_cases(e,g):
    cases=[('zero',scalar(7,0),0),('one',scalar(7,1),1),('high-bit',scalar(7,0x80000001),0x80000001),
        ('uint32-max',scalar(7,0xffffffff),0xffffffff),('omitted',b'',0),
        ('duplicate-last-wins',scalar(7,5)+scalar(7,0x80000001),0x80000001),
        ('wrong-wire-skipped',b'\x3d\x01\x00\x00\x00',0),('token-mismatch',scalar(7,91),92)]
    rows=[]
    for name,token_wire,active in cases:
        wire=token_wire+scalar(12,0x8000005b)+message(15,scalar(7,0x80000077))
        e.before_instruction=g.observe_entry
        reader=g.run_graph(34208,wire)
        rt=SceneContractRuntime(e,g,[])
        game,level,transition,loader=e.alloc(),e.alloc(1024),e.alloc(1024),e.alloc(1024)
        e.write(GLOBAL+0xb330,game);e.write(game+0x60,level)
        e.write(GLOBAL+0xa0d0,transition);e.write(transition+0xd0,active,'I');e.write(transition+0x120,1,'B')
        e.write(GLOBAL+0xbe90,loader);e.write(GLOBAL+0xc1b0,e.alloc())
        dictionary,entries,buckets=e.alloc(),g.new_array(24,16),g.new_array(4,17)
        e.write(loader+0x38,dictionary);e.write(dictionary+0x18,entries);e.write(dictionary+0x20,buckets);e.write(dictionary+0x3c,0xffffffff,'I')
        for address in (0x14828FEA0,0x148290060,0x14CD5F7B0,0x1578B66F0):
            e.allow_unwind(address);e.models.pop(address,None)
        formatted=[]
        saved={a:e.models.get(a) for a in (0x159C1ABD0,0x147161B60,0x159AD6130)}
        def token_format():
            formatted.append(e.reg('edx'))
            return 0
        e.models[0x159C1ABD0]=token_format
        e.models[0x147161B60]=lambda:0
        e.models[0x159AD6130]=lambda:0
        try:
            trace=rt.invoke(0x14CD685D2,{0x14CD5FA0F,0x14FED6AA0,0x1404C2BE0},rcx=GLOBAL,rsi=OBJECT)
            rows.append(dict(name=name,wire=wire.hex(),wireSHA256=sha(wire),reader=reader,
                rawToken=e.read(OBJECT+0x34,'I'),activeToken=active,namedDiagnosticTokens=formatted,
                trace=trace,diagnosticLiteralCell='0x145b31650' if formatted else None,
                diagnosticPointerMatchesCell=e.reg('rcx')==e.read(0x145B31650) if formatted else None,
                scope='Explicit manager continuation live-in after earlier local-entity setter; no full handler claim',
                models=['Game/Level/transition contexts and empty loading-task dictionary are explicit storage fixtures',
                    'Formatting provider, token format and earlier diagnostic sink only; token routing and dictionary miss are native']))
        finally:
            for address,model in saved.items():
                if model is None:e.models.pop(address,None)
                else:e.models[address]=model
            rt.close()
    return rows


if __name__ == '__main__':
    main()
