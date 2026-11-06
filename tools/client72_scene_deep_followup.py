"""Bounded SceneInfo CopyFrom and nonzero ability-hash registry continuation."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
try:
    from .client72_scene_deep import (inputs,raw_declarations,avatar_aot,Offline72,SceneInfoRaw72,
        DeepRuntime,SceneContractRuntime,declarations,selected_aot,PE,HASHES,TypeResolver,HEADER_VA,
        scalar,message,OBJECT,GLOBAL,END,RETURN,LIST,GUID,DISPOSE,compact,clear_model)
    from .extract_client72 import BODY,number
    from .extract_client72_signatures import annotate_generic_positions,bind_class_parameters
except ImportError:
    from client72_scene_deep import (inputs,raw_declarations,avatar_aot,Offline72,SceneInfoRaw72,
        DeepRuntime,SceneContractRuntime,declarations,selected_aot,PE,HASHES,TypeResolver,HEADER_VA,
        scalar,message,OBJECT,GLOBAL,END,RETURN,LIST,GUID,DISPOSE,compact,clear_model)
    from extract_client72 import BODY,number
    from extract_client72_signatures import annotate_generic_positions,bind_class_parameters

PIN='bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5'
COPY_NATIVE={0x147CCFC70,0x147CCF650,0x14A42BD90,0x154C79510,0x154C79640,0x158DC4600,0x158DC2140}


def copy_class_context(binary,metadata,resolver,records):
    pe=PE(binary);h=pe.offset(HEADER_VA)
    base=BODY+((number(binary,h+0x50)+0xe113ba5c)&0xffffffff)
    size=(number(binary,h+0x104)+0x938d8470)&0xffffffff
    definitions=BODY+(number(binary,h+0x9c)^0x4b5cdca3)
    generic=BODY+((number(binary,h+0x94)+0xd97e8a5c)&0xffffffff)
    ranges=dict(struct.iter_unpack('<II',metadata[base:base+size]));packed=ranges[25060];index=packed&0xffffff
    arguments=[resolver.resolve_index(i) for i in resolver.arguments[5883]];rows=[]
    for slot,expected in ((3,1256),(9,89084)):
        word=number(metadata,definitions+(index+slot)*4);kind=(word>>26)&31;ref=word&0x3ffffff
        if kind!=2:raise ValueError('Copy context is not a type')
        original=annotate_generic_positions(resolver.resolve_index(ref),metadata,generic)
        inflated=bind_class_parameters(original,arguments)
        if inflated['typeDefinition']!=expected or inflated['arguments'][0]['typeDefinition']!=77771:raise ValueError('Concrete copy type differs')
        rows.append(dict(owner=25060,definitionIndex=index,slot=slot,contextOffset=16+slot*8,word=hex(word),typeRef=ref,openType=original,inflatedType=inflated))
    methods=records[89084]['methods']
    if len(methods)!=1 or methods[0]['name']!='GFJDJODGIDB' or methods[0]['parameterCount']!=0:raise ValueError('Exact clone interface contract changed')
    concrete=next(x for x in records[77771]['methods'] if x['name']=='GFJDJODGIDB')
    return dict(classRangeSHA256=hashlib.sha256(metadata[base:base+size]).hexdigest(),rows=rows,interfaceMethods=methods,concreteClone=concrete)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ('client','output','observations'):p.add_argument('--'+key,type=Path,required=True)
    p.add_argument('--directory',type=Path,default=Path('protocol/cn-7.2'))
    p.add_argument('--python-libs',type=Path,default=Path('local/migration72/python'))
    a=p.parse_args();paths=[a.output.resolve(),a.observations.resolve()]
    if len(set(paths))!=2 or any(x.is_relative_to(a.client.resolve()) for x in paths):p.error('Distinct outputs outside client required')
    first=(a.directory/'scene-deep-observations.json').read_bytes()
    if hashlib.sha256(first).hexdigest()!=PIN:raise ValueError('Reviewed Scene deep evidence drift')
    for name,digest in json.loads(first)['sourceSHA256'].items():
        if hashlib.sha256(Path(name).read_bytes()).hexdigest()!=digest:raise ValueError('First Scene source changed: '+name)
    b,m,s,records,cells,bindings,strings,items=inputs(a)
    raw=raw_declarations(b,m,s,records,cells,a.python_libs);outer=avatar_aot(b,m,s,records,dict(types=[]))
    e=Offline72(b,a.python_libs,records,trace_instructions=False);g=SceneInfoRaw72(e,a.directory,cells,bindings,items,strings,outer,raw)
    e.uc.mem_map(END,4096);e.uc.mem_write(RETURN,b'\xc3');e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE,e._code,begin=END,end=END+4095))
    audit=declarations(b,m,s,records,g,a.python_libs)
    pe=PE(b);resolver=TypeResolver(pe,s,records,pe.offset(HEADER_VA))
    contexts=[dict(x,methodArguments=[resolver.resolve_index(i) for i in resolver.arguments[x['methodInstance']]])
        for x in audit['methodUsages'] if x['ownerType']==61203]
    audit['copyClassContext']=copy_class_context(b,m,resolver,records)
    COPY_NATIVE.update(int(x['registeredAddress'],16) for x in records[77771]['methods'] if x['name'] in ('GFJDJODGIDB','CopyFrom','DGOODBNLMIN'))
    aot=selected_aot(b,m,s,records,{(1222,4742):('ContainsKey','get_Item'),(1222,1541):('FindEntry',)})
    addresses={x['method']:int(x['address'],16) for x in aot}
    if set(addresses)!= {'FindEntry','ContainsKey','get_Item'} or addresses['FindEntry']!=0x1578B66F0:raise ValueError('Exact uint32/string dictionary AOT drift: '+str(aot))
    try:
        copies=[copy_case(e,g,audit,c) for c in copy_cases()]
        registry=[registry_case(e,g,addresses,c) for c in registry_cases()]
        native=sorted(COPY_NATIVE|set(addresses.values())|{0x1500D85A0,0x1507E41C0})
        identities=[]
        for entry in native:
            if entry in e.ends: end=e.ends[entry]
            else: e.allow_unwind(entry);end=e.unwind_ranges[entry]
            if e.unwind_ranges and entry in e.unwind_ranges:end=e.unwind_ranges[entry]
            code=b[pe.offset(entry):pe.offset(entry)+end-entry]
            identities.append(dict(entry=hex(entry),extentEnd=hex(end),codeSHA256=hashlib.sha256(code).hexdigest()))
    finally:g.close();e.close()
    report=dict(schemaVersion=1,task='F-03d3s2-deep-followup',inputSHA256=HASHES,firstSceneEvidenceSHA256=PIN,
        methodContexts=contexts,copyClassContext=audit['copyClassContext'],dictionaryAot=aot,nativeCode=identities,copyFixtures=copies,registryFixtures=registry,
        runtimeStatus='not-tested',targetProtocolReady=False,fullNetworkHandler=False,fullSceneEntityEffects=False,
        completeNonemptyListCopy=False,coveredNonemptyListTypes=[77771],naturalAbilityHashRegistryLoaded=False,
        models=['Inherited managed allocation/classes and literal/header models remain as the first Scene deep card',
            'Concrete generic clear/copy MethodInfo identities derive fixed usage; required RGCTX presence/storage is a controlled model',
            'Clone interface/context+58 and Enumerator/context+28 are independently recovered from class RGCTX table and inflated with type77771',
            'Hash registry buckets/entries and managed UTF-16 strings are controlled fixtures; actual uint32 hash/compare/collision walk executes',
            'Native ContainsKey/get_Item and ability lookup return original pointers; no Python dictionary lookup callback',
            'Prefix old tuple Array.Clear byte operation is modeled; selection/count/version/tuple writes are native'],
        sourceSHA256={x:hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in ('tools/client72_scene_deep_followup.py','tools/client72_scene_deep.py','tools/client72_scene_info_contract.py')})
    for path,value in zip(paths,(report,compact(report))):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(copyFixtures=len(copies),registryFixtures=len(registry))))


class CopyRuntime(DeepRuntime):
    def __init__(self,e,g,audit):
        super().__init__(e,g,audit,[(GUID,0,'avatar')])
        self.copy_clears=[];self.prior_clear=e.models.get(0x159C68C70);e.models[0x159C68C70]=clear_model(e,g,self.copy_clears)
        for addr in (0x154C79640,0x158DC4600,0x158DC2140):e.allow_unwind(addr);e.models.pop(addr,None)
        e.write(0x14589DB00,self.secondary_class)
        dispose=e.alloc(1024);table=e.alloc();e.write(dispose+0xc8,1,'B');e.write(dispose+0xc0,1,'H');e.write(dispose+0x80,table)
        e.write(table,e.read(0x1458861B0));e.write(table+8,5,'I');e.write(dispose+0xf8,DISPOSE)
        clone_interface=e.alloc(256);e.write(clone_interface+0xc8,1,'B')
        clone_row=next(x for x in e.records[77771]['methods'] if x['name']=='GFJDJODGIDB')
        clone_entry=int(clone_row['registeredAddress'],16);message_class=g.classes[77771]
        interface_table=e.alloc(32);e.write(interface_table,g.interface);e.write(interface_table+16,clone_interface);e.write(interface_table+24,clone_row['virtualSlot'],'I')
        e.write(message_class+0x80,interface_table);e.write(message_class+0xc0,2,'H');e.write(message_class+0xd0+clone_row['virtualSlot']*8,clone_entry)
        for row in e.records[77771]['methods']:
            if row['name'] in ('GFJDJODGIDB','CopyFrom','DGOODBNLMIN'):e.allow(int(row['registeredAddress'],16));e.models.pop(int(row['registeredAddress'],16),None)
        for row in audit['methodUsages']:
            if row['ownerType']!=61203:continue
            clear,cl,cc=e.alloc(),e.alloc(),e.alloc();e.write(clear,cl);e.write(cl+0x30,cc)
            for off in (0x10,0x18,0x20):e.write(cc+off,e.alloc())
            e.write(cc+0x28,dispose)
            copy,cl,cc=e.alloc(),e.alloc(),e.alloc();e.write(copy,cl);e.write(cl+0x30,cc);e.write(cc+0xc0,clear)
            e.write(cc+0x18,e.alloc());e.write(cc+0x20,e.alloc());e.write(cc+0x28,dispose)
            if row['methodInstance']==5883:
                e.write(cc+0x58,clone_interface)
                add,addcl,addctx=e.alloc(),e.alloc(),e.alloc();e.write(add,addcl);e.write(addcl+0x30,addctx);e.write(addctx+0x38,e.alloc());e.write(cc+0xc8,add)
            info,ctx=e.alloc(),e.alloc();e.write(info+0x18,ctx);e.write(ctx+0x18,clear);e.write(ctx+0x30,copy);e.write(int(row['cell'],16),info)

    def close(self):
        if self.prior_clear is None:self.e.models.pop(0x159C68C70,None)
        else:self.e.models[0x159C68C70]=self.prior_clear
        super().close()


def copy_cases():
    return [dict(name='nonempty-bool-copy',wire='0801'),dict(name='explicit-zero-copy',wire='0800'),
        dict(name='duplicate-bool-last-zero',wire='08010800'),dict(name='wrong-wire-bool-skipped',wire='0a00'),
        dict(name='two-nonempty-lists-cloned',wire='080112051d0000a03f32051d000020c0',nonempty=True),
        dict(name='reused-copy-clears-old-bool-and-lists',wire='080112051d0000a03f32051d000020c0',nonempty=True,clearPrior=True),
        dict(name='omitted-secondary-child',wire=None)]


def copy_case(e,g,audit,case):
    child=scalar(13,GUID)+scalar(4,0x80000001)+scalar(10,0xffffffffffffffff)+scalar(7,0xffffffff)+message(8,b'\x08\x00' if case.get('clearPrior') else b'\x08\x01')
    if case['wire'] is not None:child+=message(5,bytes.fromhex(case['wire']))
    wire=scalar(12,0x80000001)+message(2,child);e.before_instruction=g.observe_entry;reader=g.run_graph(34208,wire)
    rt=CopyRuntime(e,g,audit)
    try:
        list_pointer=e.read(OBJECT+24);back=e.read(list_pointer+16);source=e.read(e.read(back+16)+32);ability=e.read(source+0x20)
        trace=rt.invoke(LIST,{RETURN,0x140524010,0x1404C2BE0},rcx=rt.network,rdx=list_pointer)
        secondary=rt.allocated[0];result=e.read(secondary+0x90);expected=hex(RETURN)
        if trace['stop']!=expected:raise ValueError('Secondary copy boundary differs: '+case['name'])
        copied=g.snapshot(result) if result else None
        source_result=g.snapshot(ability) if ability else None
        if copied!=source_result:raise ValueError('Actual copy result differs')
        if result and result==ability:raise ValueError('Secondary copied original pointer instead of allocating')
        child_copies=[]
        if case.get('nonempty'):
            for off in (0x20,0x18):
                from_list=e.read(ability+off);to_list=e.read(result+off)
                from_child=e.read(e.read(e.read(from_list+16)+16)+32);to_child=e.read(e.read(e.read(to_list+16)+16)+32)
                if from_child==to_child or g.snapshot(from_child)!=g.snapshot(to_child):raise ValueError('Nested child was not independently cloned')
                child_copies.append(dict(offset=off,sourcePointer=hex(from_child),copyPointer=hex(to_child),value=g.snapshot(to_child)))
        clearing=None
        if case.get('clearPrior'):
            second=rt.invoke(0x147CCFC70,{RETURN},rcx=result,rdx=e.read(source+0x38))
            after=g.snapshot(result)
            if after!={}:raise ValueError('Native CopyFrom did not clear previous values')
            clearing=dict(trace=second,after=after,clearCalls=rt.copy_clears)
        critical=sorted(set(trace['models'])&{hex(x) for x in COPY_NATIVE})
        if critical:raise ValueError('Critical copy method modeled')
        return dict(name=case['name'],wire=wire.hex(),reader=reader,trace=trace,sourcePointer=hex(ability),copyPointer=hex(result),copyResult=copied,
            sourceAfter=source_result,modelSnapshot=rt.snapshot(),criticalModeledAddresses=critical,childCopies=child_copies,clearing=clearing,
            returnAddressAtStop=hex(e.read(e.reg('rsp'))) if case.get('boundary') else None,
            nativeEntries=sorted(set(trace['executedAddresses'])&{hex(x) for x in COPY_NATIVE}))
    finally:rt.close()


def registry_cases():
    return [dict(name='two-distinct-nonzero-names',pairs=[(0x89abcdef,0x12345678,77)],table=[(0x89abcdef,'Ability_A'),(0x12345678,'Modifier_B')]),
        dict(name='same-hash-mask-distinct-high-bit',pairs=[(1,0x80000001,0xffffffff)],table=[(1,'low'),(0x80000001,'high')]),
        dict(name='list-order-and-repeat',pairs=[(1,0x80000001,12),(0x80000001,1,13),(1,1,14)],table=[(1,'一'),(0x80000001,'二')]),
        dict(name='zero-uses-controlled-default',pairs=[(0,1,7)],table=[(1,'registered')]),
        dict(name='missing-key-without-default-error-branch',pairs=[(2,1,7)],table=[(1,'present')],missing=True,defaultNull=True),
        dict(name='missing-key-uses-existing-controlled-default',pairs=[(2,1,7)],table=[(1,'present')]),
        dict(name='registered-empty-name-error-branch',pairs=[(2,1,7)],table=[(2,''),(1,'present')],missing=True),
        dict(name='same-bucket-collision-chain',pairs=[(1,8,21)],table=[(1,'first'),(8,'second')])]


def registry_case(e,g,addresses,case):
    listing=b''.join(message(1,scalar(1,value)+bytes.fromhex('15')+struct.pack('<I',a)+bytes.fromhex('1d')+struct.pack('<I',b)) for a,b,value in case['pairs'])
    wire=scalar(12,91)+message(2,scalar(13,GUID))+message(15,message(1,listing))
    e.before_instruction=g.observe_entry;reader=g.run_graph(34208,wire);rt=SceneContractRuntime(e,g,[(GUID,0,'avatar')])
    ability=e.read(OBJECT+0x20);nested=e.read(e.read(ability+0x20)+0x18);klass=e.alloc(1024);e.write(nested,klass);e.write(klass+0x230,e.model_stub(rt.enumerate))
    target=e.alloc();output=g.new_array(24);e.write(target+16,output);e.write(target+24,1,'I');e.write(rt.network+0x108,target)
    clears=[];saved={addr:e.models.get(addr) for addr in (*addresses.values(),0x159C68C70)}
    e.models[0x159C68C70]=clear_model(e,g,clears)
    for addr in addresses.values():e.allow_unwind(addr);e.models.pop(addr,None)
    for addr in (0x1500D85A0,0x1507E41C0,0x1517ACC90):e.allow(addr);e.models.pop(addr,None)
    interface=e.synthetic_type(0x1458DC1A0);cl=e.alloc(1024);iface=e.alloc();e.write(cl+0xc0,1,'H');e.write(cl+0xbe,32,'H');e.write(cl+0x80,iface);e.write(iface,interface)
    e.write(cl+0xd0,addresses['get_Item']);e.write(cl+0xf0,addresses['ContainsKey'])
    for off in (0xd0,0xf0):e.write(cl+256+off,e.alloc())
    dictionary=e.alloc();entries=g.new_array(24,16);buckets=g.new_array(4,7);e.write(dictionary,cl);e.write(dictionary+0x18,entries);e.write(dictionary+0x20,buckets)
    strings={};table=[]
    for i,(key,text) in enumerate([(0,'controlled-default'),*case['table']]):
        pointer=e.alloc(24+len(text.encode('utf-16-le')));raw=text.encode('utf-16-le');e.write(pointer+16,len(raw)//2,'I');e.uc.mem_write(pointer+20,raw);strings[pointer]=text
        if key==0:e.write(GLOBAL+0xc698,0 if case.get('defaultNull') else pointer);continue
        position=i-1;hashed=key&0x7fffffff;bucket=hashed%7;previous=e.read(buckets+32+bucket*4,'I')
        row=entries+32+position*24;e.write(row,hashed,'I');e.write(row+4,(previous-1)&0xffffffff,'I');e.write(row+8,key,'I');e.write(row+16,pointer)
        e.write(buckets+32+bucket*4,position+1,'I');table.append(dict(key=key,text=text,pointer=hex(pointer),bucket=bucket))
    e.write(GLOBAL+0xc6d0,dictionary)
    try:
        trace=rt.invoke(0x1507D1620,{0x1507E44F0,0x1500D872C,0x1404C2BE0},rcx=rt.network,rdx=OBJECT)
        expected='0x1500d872c' if case.get('missing') else '0x1507e44f0'
        if trace['stop']!=expected:
            failure=dict(case=case,trace=trace,registers={r:hex(e.reg(r)) for r in ('rcx','rdx','r8','r9')},dictionary=hex(dictionary),buckets=bytes(e.uc.mem_read(buckets+32,28)).hex(),entries=bytes(e.uc.mem_read(entries+32,48)).hex())
            Path('local/migration72/astra-scene/registry-boundary-failure.json').write_text(json.dumps(failure,indent=2),'utf8')
            raise ValueError('Nonzero registry continuation differs: '+case['name']+' '+trace['stop'])
        count=e.read(target+24,'I');tuples=[]
        for i in range(count):
            p,q,n=[e.read(output+32+i*24+j*8) for j in range(3)]
            tuples.append(dict(first=strings[p],second=strings[q],value=n,firstPointer=hex(p),secondPointer=hex(q)))
        expected_values={0:'controlled-default',**dict(case['table'])}
        if not case.get('missing') and [(x['first'],x['second'],x['value']) for x in tuples]!=[(expected_values.get(b,'controlled-default'),expected_values.get(a,'controlled-default'),n) for a,b,n in case['pairs']]:raise ValueError('Nonzero tuple source order differs')
        if set(trace['models'])&{hex(x) for x in addresses.values()}:raise ValueError('Hash dictionary lookup modeled')
        return dict(name=case['name'],wire=wire.hex(),reader=reader,controlledTable=table,trace=trace,tuples=tuples,clearCalls=clears,
            nativeDictionaryEntries=sorted(set(trace['executedAddresses'])&{hex(x) for x in addresses.values()}))
    finally:
        for addr,model in saved.items():
            if model is None:e.models.pop(addr,None)
            else:e.models[addr]=model
        rt.close()


if __name__=='__main__':main()
