"""D02 bounded SceneInfo retain/state investigation; offline target instructions only.

Does not modify the common runtime, production schemas, client, or resources.
Managed metadata is controlled; native retain/release and stage gates execute.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .client72_scene_info_contract import SceneContractRuntime, PINS, LIST, RETURN, compact, rejections
    from .trace_client72_scene_info_raw import SceneInfoRaw72, declarations as raw_declarations, verify_sources
    from .trace_client72_avatar_middle_trees import inputs, scalar, message
    from .trace_client72_avatar_outer import avatar_aot
    from .trace_client72_avatar_signed_trees import selected_aot
    from .extract_client72 import PE, HASHES, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .extract_client72_enums import recover as recover_enums
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from .trace_client72_token import load_emulator
    from .client72_session_runtime import fragment
except ImportError:
    from client72_scene_info_contract import SceneContractRuntime, PINS, LIST, RETURN, compact, rejections
    from trace_client72_scene_info_raw import SceneInfoRaw72, declarations as raw_declarations, verify_sources
    from trace_client72_avatar_middle_trees import inputs, scalar, message
    from trace_client72_avatar_outer import avatar_aot
    from trace_client72_avatar_signed_trees import selected_aot
    from extract_client72 import PE, HASHES, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from extract_client72_enums import recover as recover_enums
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from trace_client72_token import load_emulator
    from client72_session_runtime import fragment

EVIDENCE = dict(PINS, **{'scene-info-contract-observations.json': 'a0c88290e5d4bbd31b2ea0e83986ffc9e51235462b727429e441cba8cd48a2ad',
    'avatar-deep-suffix-observations.json':'5e03ba3486470055450cc4233f8cf3a500680569d874e5e8b1e1efad5b9e58c6'})
SETTER, STAGE, DISPOSE = 0x151846000, 0x1482879C0, 0x154FB0700
RETAIN_LIST, RELEASE_LIST = 0x158DC1870, 0x158DC2860
POOL = 0x14D447B80
POOL_FLAG = 0x14586D70A
GUID = 9007199254740993


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--directory', type=Path, default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    paths = [args.output.resolve(), args.observations.resolve()]
    if len(set(paths)) != 2 or any(p.is_relative_to(args.client.resolve()) for p in paths): parser.error('Distinct outputs outside client required')
    for name, digest in EVIDENCE.items():
        if hashlib.sha256((args.directory / name).read_bytes()).hexdigest() != digest: raise ValueError('Reviewed evidence drift: ' + name)
    factory_roles=json.loads((args.directory/'avatar-deep-suffix-observations.json').read_text('utf8'))['factoryCallsites']
    binary, metadata, startup, records, cells, bindings, strings, items = inputs(args)
    inherited = verify_sources(args.directory)
    raw = raw_declarations(binary, metadata, startup, records, cells, args.python_libs)
    outer = avatar_aot(binary, metadata, startup, records, dict(types=[]))
    e = Offline72(binary, args.python_libs, records, trace_instructions=False)
    g = SceneInfoRaw72(e, args.directory, cells, bindings, items, strings, outer, raw)
    e.uc.mem_map(END, 4096); e.uc.mem_write(RETURN, b'\xc3')
    e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE, e._code, begin=END, end=END + 4095))
    audit = declarations(binary, metadata, startup, records, g, args.python_libs)
    try:
        retain=[]
        for c in retention_cases():
            print('native fixture: '+c['name'],flush=True)
            retain.append(retention_case(e,g,audit,c))
        gates = stage_cases(e, g)
        handler = handler_cases(e,g)
        prefix = handler_prefix_cases(e,g)
        suffix = handler_suffix_case(e,g,audit)
        lifecycle = lifecycle_cases(e,g)
        errors = rejections(g)
        validate(retain, gates)
    finally: g.close(); e.close()
    report = dict(schemaVersion=1, task='F-03d3s2-deep', inputSHA256=HASHES,
        inputContracts=EVIDENCE, inheritedRawEvidence=inherited, declarations=audit,
        retentionFixtures=retain, stageFixtures=gates, handlerFixtures=handler, handlerPrefixFixtures=prefix,
        handlerSuffixFixture=suffix,lifecycleFixtures=lifecycle,wireRejections=errors,runtimeStatus='not-tested',
        fullNetworkHandler=False, fullSceneEntityEffects=False, targetProtocolReady=False,
        completeSceneInfoRawClosure=False, productionRequiredFieldSetApproved=False,
        secondaryWholeClassWeaponNameApproved=False,
        secondaryWeaponFactoryRoleApproved=True,weaponRoleCrossEvidence=factory_roles,
        models=['Existing s1 reader and s2a Avatar/GUID/scene fixtures unchanged',
            'Message vtables derive actual inherited metadata slots10/11; class/MethodInfo/RGCTX are controlled managed layouts',
            'Generic retain/release context presence entries are opaque tokens; IDisposable binds the target shared List<Object>.Enumerator.Dispose AOT',
            'Pooling flag is explicitly disabled for completed last-reference replacement; pool-enabled reclaim is an actual separate stopped boundary',
            'World stage fixtures do not claim naturally loaded SceneScriptData; zero hash fallback is controlled null, never an approved production name',
            'Managed Array.Clear byte effect for prefix/suffix is explicitly modeled; native count/version/tuple stores execute',
            'SceneScriptData and managers are controlled state fixtures; native stage predicate runs; no naturally loaded scene is claimed'],
        sourceSHA256={p: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in (
            'tools/client72_scene_deep.py','tools/client72_scene_info_contract.py',
            'tools/trace_client72_scene_info_raw.py','tools/client72_emulator.py','tools/client72_graph.py','tools/client72_session_runtime.py')})
    for path, value in zip(paths, (report, compact(report))):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(retentionFixtures=len(retain),stageFixtures=len(gates),handlerFixtures=len(handler),lifecycleFixtures=len(lifecycle),fullNetworkHandler=False)))


def virtual(records, index, slot):
    while True:
        hit = [m for m in records[index]['methods'] if m['virtualSlot'] == slot]
        if hit: return dict(owner=index, **hit[0])
        index = records[index]['parent']['typeDefinition']


def declarations(b, m, s, records, graph, libs):
    load_emulator(libs)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = PE(b); h = pe.offset(HEADER_VA); md = Cs(CS_ARCH_X86,CS_MODE_64); md.detail=True
    optional=number(b,0x3c)+24; rv,size=struct.unpack_from('<II',b,optional+112+3*8)
    off=pe.offset(pe.base+rv); unwind={pe.base+x:pe.base+y for x,y,_ in struct.iter_unpack('<III',b[off:off+size])}
    slots = {i: {slot: virtual(records,i,slot) for slot in (10,11)} for i in (34045,77771,85344)}
    entries={int(row['registeredAddress'],16) for value in slots.values() for row in value.values()}
    entries.update({SETTER,STAGE,DISPOSE,RETAIN_LIST,RELEASE_LIST,POOL,0x147CCFC70,0x147CCF650,0x14A42BD90,0x154C79510,0x154C79640,0x1507D1620,0x1507E41C0,0x1500D85A0,0x1496A7750,0x15184AEB0,0x14EAAF060,0x14CD68530,0x14828FEA0,0x14828BA00,0x15154BF20})
    methods=[]; wanted=set()
    for entry in sorted(entries):
        end=unwind.get(entry)
        if entry==DISPOSE:
            if b[pe.offset(entry)]!=0xc3: raise ValueError('Shared Dispose is no longer a native ret leaf')
            end=entry+1
        elif end is None:
            # Tiny leaf methods may not require unwind data; extent still comes from the current registration table.
            end=graph.e.ends[entry]
        code=b[pe.offset(entry):pe.offset(entry)+end-entry]
        calls=[]; references=[]
        for ins in md.disasm(code,entry):
            if ins.mnemonic=='call' and ins.operands[0].type==2: calls.append(dict(site=hex(ins.address),target=hex(ins.operands[0].imm)))
            for op in ins.operands:
                if op.type==3 and op.mem.base==41:
                    cell=ins.address+ins.size+op.mem.disp; references.append(hex(cell)); wanted.add(cell)
        methods.append(dict(entry=hex(entry),extentEnd=hex(end),codeSHA256=hashlib.sha256(code).hexdigest(),directCalls=calls,ripReferences=references))
    contexts=method_usages(b,m,s,records,wanted)
    selections={(25060,row['classInstance']):('KFBBFDGJNBI','ELADGJLADDA') for row in contexts if row['ownerType']==25060}
    selections[(1256,0)]=('Dispose',)
    aot=selected_aot(b,m,s,records,selections)
    if not any(x['owner']==1256 and x['address']==hex(DISPOSE) and x['method']=='Dispose' for x in aot): raise ValueError('Dispose shared target drift')
    expected={11:'0x147ccf320',10:'0x147ccf3b0'}
    if {slot:slots[34045][slot]['registeredAddress'] for slot in expected} != expected: raise ValueError('Ability retain/release slot drift')
    stage_field=next(f for f in records[45183]['fields'] if f['offset']==0x70)
    if stage_field['name']!='stageType' or stage_field['type']['typeDefinition']!=66233: raise ValueError('Stage field identity drift')
    literal=Literals72(b,m).selected((64772,))['literals']
    if len(literal)!=1 or literal[0]['cell']!='0x145b48640' or literal[0]['value']!="[ABILITY HASH] can't find str from hash = ": raise ValueError('Ability hash registry literal drift')
    if hex(POOL_FLAG) not in next(x['ripReferences'] for x in methods if x['entry']==hex(POOL)): raise ValueError('Pool flag RIP source drift')
    return dict(virtualSlots=slots,methods=methods,methodUsages=contexts,aot=aot,abilityHashLiteral=literal,poolEnabledFlag=hex(POOL_FLAG),
        stageField=dict(ownerType=45183,ownerName=records[45183]['name'],**stage_field),
        stageEnum=recover_enums(b,m,records,indices=(66233,))['enums'][0])


def method_usages(b,m,s,records,wanted):
    pe=PE(b); h=pe.offset(HEADER_VA); resolver=TypeResolver(pe,s,records,h); lit=Literals72(b,m)
    cache=number(b,pe.offset(USAGE_REGISTRATION)+0x10,'Q')
    n=((number(m,lit.groups_base+(lit.global_group+1)*4)+0x8bff3e05)&0xffffffff)^(((lit.global_group+1)*0x5843eaa0-0x443c8630)&0xffffffff)
    owners={z['index']:(r,z) for r in records for z in r['methods']}; rows=[]
    for i in range(n):
        key=(i*0x57c15286+0x26ddebda)&0xffffffff
        src=((number(m,lit.usages_base+i*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
        slot=number(m,lit.usages_base+i*8+4)^key^0x1855aa65
        if src>>29!=6 or cache+slot*8 not in wanted: continue
        spec=src&0x1fffffff; method,cls,args=struct.unpack_from('<iii',m,BODY+(number(b,h+0x170)^0x6347382b)+spec*12)
        owner,row=owners[method]
        rows.append(dict(cell=hex(cache+slot*8),usageRow=i,methodSpec=spec,methodDefinition=method,
            ownerType=owner['index'],method=row['name'],classInstance=cls,methodInstance=args,
            classArguments=[resolver.resolve_index(x) for x in resolver.arguments[cls]] if cls!=-1 else []))
    return rows


class DeepRuntime(SceneContractRuntime):
    def __init__(self,e,g,audit,models):
        super().__init__(e,g,models)
        self.deep_entries=set()
        for index,value in audit['virtualSlots'].items():
            for slot,row in value.items():
                entry=int(row['registeredAddress'],16); e.write(g.classes[index]+0xd0+slot*8,entry)
                e.allow(entry); e.models.pop(entry,None); self.deep_entries.add(entry)
        e.allowed.append((DISPOSE,DISPOSE+1)); e.models.pop(DISPOSE,None); self.deep_entries.add(DISPOSE)
        for entry in (RETAIN_LIST,RELEASE_LIST): e.allow_unwind(entry); e.models.pop(entry,None); self.deep_entries.add(entry)
        for entry in (STAGE,POOL,0x147CCFC70,0x147CCF650,0x14A42BD90): e.allow(entry); e.models.pop(entry,None); self.deep_entries.add(entry)
        e.allow_unwind(0x154C79510); e.models.pop(0x154C79510,None); self.deep_entries.add(0x154C79510)
        dispose_class=e.alloc(1024); table=e.alloc(); interface=e.read(0x1458861B0)
        e.write(dispose_class+0xc8,1,'B'); e.write(dispose_class+0xc0,1,'H'); e.write(dispose_class+0x80,table)
        e.write(table,interface); e.write(table+8,5,'I'); e.write(dispose_class+0xf8,DISPOSE)
        for row in audit['methodUsages']:
            if row['ownerType']!=25060 or row['method'] not in ('KFBBFDGJNBI','ELADGJLADDA'): continue
            info,klass,ctx=e.alloc(),e.alloc(),e.alloc()
            e.write(info,klass); e.write(klass+0x30,ctx)
            e.write(ctx+0x18,e.alloc()); e.write(ctx+0x20,e.alloc()); e.write(ctx+0x28,dispose_class)
            e.write(int(row['cell'],16),info)
        # Explicit fixture: collection pool cleanup is disabled, so native release returns before allocator/pool effects.
        e.write(POOL_FLAG,0,'B')

    def counters(self):
        e=self.e; objects=[]; lists=[]
        for pointer,index in self.g.objects.items():
            objects.append(dict(typeIndex=index,pointer=hex(pointer),count=e.read(pointer+0x14,'i'),flags=e.read(pointer+0x11,'B')))
            for f in self.g.schemas.get(index,{}).get('fields',[]):
                if f['cardinality']=='repeated' and f['type']=='message':
                    target=e.read(pointer+f['objectOffset'])
                    lists.append(dict(ownerType=index,field=f['clientField'],pointer=hex(target),count=e.read(target+0x1c,'i') if target else None))
        return dict(objects=objects,lists=lists)


def retention_cases():
    # Independent protobuf literals: tag1 bool, tag2/6 repeated77771, float tag3=1.25/-2.5.
    scalar_only='0801'; children='080112051d0000a03f32051d000020c0'
    return [dict(name='nonempty-bool',ability=scalar_only),dict(name='two-nonempty-child-lists',ability=children),
        dict(name='same-pointer-repeat',ability=children,mode='same'),
        dict(name='replace-existing-reference',ability=children,mode='replace'),
        dict(name='replace-last-reference-pool-disabled',ability=children,mode='replace',count=0),
        dict(name='replace-last-reference-pool-enabled-boundary',ability=children,mode='pool',count=0),
        dict(name='null-does-not-release-old-reference',ability=children,mode='null'),
        dict(name='counter-int32-max-wrap',ability=scalar_only,count=0x7fffffff),
        dict(name='uninitialized-child-list-null-error',ability=scalar_only,mode='missing-list'),
        dict(name='lazy-input-flag-stops-before-materialize',ability=scalar_only,mode='lazy'),
        dict(name='full-list-avatar-retained-secondary-copy-boundary',ability=children,mode='full-list'),
        dict(name='duplicate-wire-avatar-ability-merge',ability=children,mode='duplicate-wire')]


def retention_case(e,g,audit,case):
    child=scalar(13,GUID)+scalar(4,0x80000001)+scalar(10,0xffffffffffffffff)+scalar(7,0xffffffff)
    child+=message(8,bytes.fromhex(case['ability']))
    if case.get('mode')=='duplicate-wire': child+=message(8,bytes.fromhex('12051d00006040'))
    if case.get('mode') in ('full-list','replace','pool'): child+=message(5,bytes.fromhex('0801' if case['mode']=='full-list' else case['ability']))
    wire=scalar(12,0x80000001)+message(2,child)
    e.before_instruction=g.observe_entry; decoded=g.run_graph(34208,wire)
    rt=DeepRuntime(e,g,audit,[(GUID,0,'avatar')])
    try:
        wrapper=e.read(OBJECT+0x18); backing=e.read(wrapper+0x10); array=e.read(backing+0x10)
        row=e.read(array+0x20); ability=e.read(row+0x38); avatar=rt.models[GUID]
        if 'count' in case: e.write(ability+0x14,case['count'],'I')
        mode=case.get('mode'); stops={RETURN,0x1404C2BE0,0x14A440C40,0x140514EE0,0x154C795C4}
        if mode=='missing-list': e.write(ability+0x20,0)
        if mode=='lazy': e.write(ability+0x11,1,'B')
        before=rt.counters()
        trace=rt.invoke(LIST if mode=='full-list' else SETTER,stops,
            rcx=rt.network if mode=='full-list' else avatar,rdx=wrapper if mode=='full-list' else ability)
        first=rt.counters(); second=None; replacement=None
        if mode in ('same','replace','pool','null'):
            target=ability
            if mode in ('replace','pool'):
                # An independent same-shape child from tag5 is a distinct actual reader result.
                target=e.read(row+0x20); replacement=hex(target)
            if mode=='null': target=0
            if mode=='pool': e.write(POOL_FLAG,1,'B')
            second=rt.invoke(SETTER,stops,rcx=avatar,rdx=target)
        return dict(name=case['name'],wire=wire.hex(),reader=decoded,before=before,first=first,after=rt.counters(),
            trace=trace,secondTrace=second,abilityPointer=hex(ability),replacementPointer=replacement,
            storedPointer=hex(e.read(avatar+0x90)),modelSnapshot=rt.snapshot(),stores=rt.stores,
            criticalModeledAddresses=sorted(set(trace['models']) & {hex(x) for x in rt.deep_entries|{SETTER,LIST}}),
            criticalNativeEntries=sorted(set(trace['executedAddresses']) & {hex(x) for x in rt.deep_entries|{SETTER,LIST}}))
    finally: rt.close()


def stage_cases(e,g):
    rows=[]
    for name,value in [('World',0),('Dungeon',1),('Room',2),('HomeworldExternal',3),('HomeworldInternal',4),
        ('BeyondGameplay',5),('BeyondGameplayPrepare',6),('BeyondSocialHall',7),('BeyondGameplaySettle',8),('unknown-max',0xffffffff),('null-scene-data',None)]:
        e.prepare(); owner=e.alloc(128); data=e.alloc(128)
        e.write(owner+0x28,0 if value is None else data)
        if value is not None: e.write(data+0x70,value,'I')
        e.allow(STAGE); e.models.pop(STAGE,None)
        # Dungeon path requires these managers; it still stops at the real current-dungeon query.
        e.write(GLOBAL+0x9e80,e.alloc()); e.write(GLOBAL+0xbe90,e.alloc()); e.write(STACK+0x100008,RETURN)
        trace=fragment(e,STAGE,{RETURN,0x14CD5EDC0,0x1404C2BE0},dict(rcx=owner,rsp=STACK+0x100008))
        rows.append(dict(name=name,stageType=value,trace=trace,result=bool(e.reg('al')) if trace['stop']==hex(RETURN) else None))
    return rows


def handler_cases(e,g):
    rows=[]
    for name,stage,manager in [('world-skips-complex-object',0,True),('beyond-enters-complex-consumer',5,True),
        ('beyond-without-manager-skips',5,False),('dungeon-needs-resource-query',1,True),('room-skips-complex-object',2,True)]:
        wire=scalar(12,91)+message(2,scalar(13,GUID)+scalar(4,91))+message(15,scalar(7,73))
        e.before_instruction=g.observe_entry; reader=g.run_graph(34208,wire); rt=SceneContractRuntime(e,g,[(GUID,0,'avatar')])
        game,stage_owner,data=e.alloc(),e.alloc(128),e.alloc(128)
        e.write(GLOBAL+0xb330,game); e.write(game+0x60,stage_owner); e.write(stage_owner+0x28,data); e.write(data+0x70,stage,'I')
        e.write(GLOBAL+0xabe0,e.alloc() if manager else 0); e.write(GLOBAL+0x9e80,e.alloc()); e.allow(STAGE); e.models.pop(STAGE,None)
        try:
            trace=rt.invoke(0x1507D16DC,{LIST,0x1496A7750,0x14CD5EDC0,0x1404C2BE0},rsi=OBJECT,rdi=rt.network)
            expected=0x14CD5EDC0 if stage==1 else 0x1496A7750 if stage==5 and manager else LIST
            if trace['stop']!=hex(expected): raise ValueError('Handler gate differs')
            rows.append(dict(name=name,stageType=stage,complexManagerPresent=manager,reader=reader,trace=trace,
                objectPointerAtConsumer=hex(e.reg('r8')) if expected==0x1496A7750 else None,scope='explicit-live-in-handler-middle; earlier prefix not executed'))
        finally: rt.close()
    return rows


def lifecycle_cases(e,g):
    rows=[]
    for kind in ('avatar','secondary'):
        for usable in (False,True):
            e.before_instruction=g.observe_entry; g.run_graph(34208,message(2,scalar(13,GUID)))
            rt=SceneContractRuntime(e,g,[(GUID,91,kind)])
            dictionary=rt.old_entity_dictionary; entries=g.new_array(24); entity=e.alloc(0x600)
            e.write(dictionary+24,entries); e.write(entries+48,entity); e.write(entity,e.alloc(1024)); e.write(entity+0x80,e.alloc() if usable else 0)
            original=e.models[0x1578B66F0]
            e.models[0x1578B66F0]=lambda: (0 if e.reg('edx')==91 else 0xffffffff) if e.reg('rcx')==dictionary else original()
            entry,offset,boundary=(0x15184AEB0,0x218,0x15184AF37) if kind=='avatar' else (0x14EAAF060,0x28,0x14EAAF0E3)
            try:
                trace=rt.invoke(entry,{RETURN,boundary},rcx=rt.models[GUID],rdx=92)
                stored=e.read(rt.models[GUID]+offset,'I'); expected=91 if kind=='avatar' and usable else 92
                if stored!=expected or trace['stop']!=hex(boundary if usable else RETURN): raise ValueError('Entity replacement boundary differs')
                rows.append(dict(modelType=18163 if kind=='avatar' else 51522,priorEntityId=91,newEntityId=92,usableOldEntity=usable,
                    storedEntityIdAtStop=stored,sceneQueries=rt.scene_lookups,trace=trace,scope='real lookup and ordering before unresolved component teardown'))
            finally: rt.close()
    return rows


def clear_model(e,g,calls):
    def clear():
        array,start,count=e.reg('rcx'),e.reg('edx'),e.reg('r8d')
        width,capacity=g.arrays[array]
        if start+count>capacity: raise ValueError('Declared Array.Clear fixture out of range')
        calls.append(dict(array=hex(array),start=start,count=count,elementWidth=width))
        e.uc.mem_write(array+32+start*width,bytes(count*width)); return 0
    return clear


def handler_prefix_cases(e,g):
    rows=[]
    for name,a,b in [('nonzero-first-hash-needs-string-table',0x89abcdef,0x12345678),
        ('zero-first-nonzero-second-needs-string-table',0,0x80000001),('zero-hashes-controlled-fallback',0,0)]:
        embryo=bytes.fromhex('08ffffffff0f15')+struct.pack('<I',a)+bytes.fromhex('1d')+struct.pack('<I',b)
        wire=scalar(12,91)+message(2,scalar(13,GUID)+scalar(4,91))+message(15,message(1,message(1,embryo)))
        e.before_instruction=g.observe_entry; reader=g.run_graph(34208,wire); rt=SceneContractRuntime(e,g,[(GUID,0,'avatar')])
        outer=e.read(OBJECT+0x20); listing=e.read(e.read(outer+0x20)+0x18)
        klass=e.alloc(1024); e.write(listing,klass); e.write(klass+0x230,e.model_stub(rt.enumerate))
        target=e.alloc(); array=g.new_array(24); e.write(target+16,array); e.write(target+24,1,'I'); e.write(rt.network+0x108,target)
        calls=[]; previous=e.models.get(0x159C68C70); e.models[0x159C68C70]=clear_model(e,g,calls)
        for entry in (0x1507E41C0,0x1500D85A0,0x1517ACC90): e.allow(entry); e.models.pop(entry,None)
        e.write(GLOBAL+0xc6d0,0); e.write(GLOBAL+0xc698,0)
        try:
            trace=rt.invoke(0x1507D1620,{0x1404C2BE0,0x1507E44F0},rcx=rt.network,rdx=OBJECT)
            expected=0x1404C2BE0 if a or b else 0x1507E44F0
            if trace['stop']!=hex(expected): raise ValueError('Full typed prefix boundary differs')
            rows.append(dict(name=name,wire=wire.hex(),reader=reader,hashFields=dict(tag2=a,tag3=b),
                trace=trace,clearCalls=calls,outputCount=e.read(target+24,'I'),
                tupleValues=[e.read(array+32+i*8) for i in range(3)],
                scope='continuous typed handler entry to genuine missing hash-string table or next consumer; fallback is controlled null'))
        finally:
            rt.close()
            if previous is None: e.models.pop(0x159C68C70,None)
            else: e.models[0x159C68C70]=previous
    return rows


def handler_suffix_case(e,g,audit):
    wire=message(2,scalar(13,GUID))+message(2,scalar(13,0xffffffffffffffff))
    e.before_instruction=g.observe_entry; reader=g.run_graph(34208,wire); rt=SceneContractRuntime(e,g,[])
    context=next(x for x in audit['methodUsages'] if x['cell']=='0x145a10990')
    if (context['ownerType'],context['method'],context['classArguments'][0]['typeDefinition'])!=(25060,'Clear',18295): raise ValueError('Handler clear context changed')
    info,klass,ctx=e.alloc(),e.alloc(),e.alloc(); e.write(info,klass); e.write(klass+0x30,ctx); e.write(ctx+16,e.alloc()); e.write(0x145a10990,info)
    wrapper=e.read(OBJECT+24); backing=e.read(wrapper+16); array=e.read(backing+16)
    prior=dict(count=e.read(backing+24,'I'),version=e.read(backing+28,'I'),pointers=[hex(e.read(array+32+i*8)) for i in range(2)])
    calls=[]; previous=e.models.get(0x159C68C70); e.models[0x159C68C70]=clear_model(e,g,calls)
    rsp=STACK+0x100008; e.write(rsp+0x68,0); e.write(rsp+0x70,0); e.write(rsp+0x78,RETURN)
    try:
        trace=fragment(e,0x1507D1802,{RETURN},dict(rsp=rsp,rsi=OBJECT,rdi=rt.network))
        after=dict(count=e.read(backing+24,'I'),version=e.read(backing+28,'I'),pointers=[hex(e.read(array+32+i*8)) for i in range(2)])
        if after['count']!=0 or after['version']!=prior['version']+1 or after['pointers']!=['0x0','0x0']: raise ValueError('Handler cleanup differs')
        return dict(reader=reader,prior=prior,after=after,trace=trace,clearCalls=calls,
            scope='explicit-live-in suffix after unexecuted scene-manager return; managed Array.Clear byte effect modeled')
    finally:
        rt.close()
        if previous is None: e.models.pop(0x159C68C70,None)
        else: e.models[0x159C68C70]=previous


def validate(rows,gates):
    by={r['name']:r for r in rows}
    for r in rows:
        if r['criticalModeledAddresses']: raise ValueError('Critical retain path was modeled')
        expected='0x1404c2be0' if r['name']=='uninitialized-child-list-null-error' else '0x14a440c40' if r['name']=='lazy-input-flag-stops-before-materialize' else '0x154c795c4' if r['name']=='full-list-avatar-retained-secondary-copy-boundary' else hex(RETURN)
        if r['trace']['stop']!=expected: raise ValueError('Unexpected retention stop '+r['name']+': '+r['trace']['stop'])
    for name in ('nonempty-bool','two-nonempty-child-lists','same-pointer-repeat','duplicate-wire-avatar-ability-merge','full-list-avatar-retained-secondary-copy-boundary'):
        if by[name]['storedPointer']!=by[name]['abilityPointer']: raise ValueError('Native pointer store differs: '+name)
    if by['null-does-not-release-old-reference']['storedPointer']!='0x0': raise ValueError('Null branch failed')
    if by['replace-existing-reference']['storedPointer']!=by['replace-existing-reference']['replacementPointer']: raise ValueError('Replacement store failed')
    if by['replace-last-reference-pool-enabled-boundary']['secondTrace']['stop']!='0x140514ee0': raise ValueError('Pool-enabled last release did not reach actual allocator')
    for row in gates:
        if row['stageType'] in (1,None):
            expected='0x14cd5edc0' if row['stageType']==1 else '0x1404c2be0'
            if row['trace']['stop']!=expected: raise ValueError('Gate boundary differs')
        elif row['result']!=(row['stageType']==5): raise ValueError('Native stage result differs')


if __name__=='__main__': main()
