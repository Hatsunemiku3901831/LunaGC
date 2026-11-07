"""Fixed-file SceneInfo capability registration and context provenance, offline only."""
import argparse
import hashlib
import json
import struct
import importlib.util
from pathlib import Path
try:
    from .client72_scene_entry_closure import inputs, verify_inputs, sha, selected_aot
    from .client72_scene_deep import method_usages, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_literals import Literals72
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, GLOBAL, STACK, END
    from .client72_session_runtime import fragment
    from .trace_client72_token import load_emulator
except ImportError:
    from client72_scene_entry_closure import inputs, verify_inputs, sha, selected_aot
    from client72_scene_deep import method_usages, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_literals import Literals72
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, GLOBAL, STACK, END
    from client72_session_runtime import fragment
    from trace_client72_token import load_emulator

PREVIOUS_SOURCE='5d255f494bf5755b204697789c3a8ef1ae4db88ea3031d772d0a7cf987f7bfb3'
RETURN=END+0x100
ADD,LOOKUP=0x1500D5480,0x1500D85A0
ENTRIES=(ADD,LOOKUP,0x1500D6270,0x1500D7090,0x1548F4930,0x1548F4E00,0x1412D3860,0x140546AD0,
    0x1504F4BB0,0x1500D5380,0x1500D9980,0x14D2CA110,0x15154BF20,0x152E10FC0,0x152E110C0,
    0x152E108B0,0x152E10C00,0x152E106C0,0x152E11810,
    0x157FFC650,0x157FFC780,0x152C255D0,0x1553C0930,0x1553C0640,
    0x14E62B290,0x14AA97070,0x14AA99DC0,0x14AA97730,0x14AA9C630,0x14AA9AF10,
    0x146ECD7B0,0x146ECD800,0x146ECD850)
RAW_ROOT=Path('local/migration72/d02-active/scene-runtime-context-astra')
SNAPSHOT=Path('local/migration72/d02-active/scene-entry-astra/natural-runtime-snapshot.py')
SNAPSHOT_SHA='a1955fcf4b47dd70cf0dc51495b30307beb8d54a7b5b960dca97503ac1efe48f'
GADGET_SHA='6355185856a095aea5d523a8465029b2c40c61d5de055e53fc0412a6472dcf2c'
PRIMARY_SHA='1e95ae771d6597160b2438d5a1ead40f04f58a84a26be466c0226fab6923138f'
JAVA_INPUTS={
    'src/main/java/emu/grasscutter/utils/Utils.java':'5e1f7afd65b57b209d116dab05eb19a5ec37e1537435ad20659412d7ca047664',
    'src/main/java/emu/grasscutter/game/player/TeamManager.java':'e22bf409e5d9f1d9182f0b4094ed72e02e40751347a152e825c6c3e4f9434dfb',
    'src/main/java/emu/grasscutter/server/packet/send/PacketPlayerEnterSceneInfoNotify.java':'07763b4d1e515e1083681209acc11280c78abdfa6190669d7c3ca0b6da1d9201'}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path)
    p.add_argument('--directory',type=Path,default=Path('protocol/cn-7.2'))
    p.add_argument('--python-libs',type=Path,default=Path('local/migration72/python'))
    a=p.parse_args()
    if sha(Path('tools/client72_scene_entry_closure.py').read_bytes())!=PREVIOUS_SOURCE:
        raise ValueError('Frozen preceding Scene card changed')
    proof=verify_inputs(a)
    for path,digest in JAVA_INPUTS.items():
        if sha(Path(path).read_bytes())!=digest:raise ValueError('Fixed Java source changed: '+path)
    b,m,s,records,cells,bindings,strings,items=inputs(a)
    audit=declarations(b,m,s,records,cells,a.python_libs)
    engine=Offline72(b,a.python_libs,records,trace_instructions=False)
    engine.uc.mem_map(END,4096);engine.uc.mem_write(RETURN,b'\xc3')
    engine.hooks.append(engine.uc.hook_add(engine.U.UC_HOOK_CODE,engine._code,begin=END,end=END+4095))
    try:
        native=run_native(engine,audit)
    finally:
        engine.close()
    resource_proof,blobs=locate_resources(b)
    primary=parse_primary_native(b,a.python_libs,blobs['GadgetPrimaryIndex'])
    primary_boundary=parse_primary_native(b,a.python_libs,blobs['GadgetPrimaryIndex'],truncate_at=3)
    gadget=parse_gadget_rows(b,m,s,records,cells,a.python_libs,blobs['Gadget'],primary)
    report=dict(task='D02-R4-scene-runtime-context',inputs=proof,declarations=audit,native=native,
        resources=resource_proof,primaryIndex=primary,primaryBoundary=primary_boundary,gadgetRows=gadget,
        observations=observation_scope(),javaSourceSHA256=JAVA_INPUTS,
        sourceSHA256={x:sha(Path(x).read_bytes()) for x in ('tools/client72_scene_runtime_context.py',
            'tools/client72_scene_entry_closure.py','tools/client72_emulator.py','tools/client72_session_runtime.py',
            'tools/client72_resource_locator.py','tools/client72_resource_deep.py',str(SNAPSHOT))},
        completeSceneInfoHandler=False,naturalAbilityRegistryObserved=False,targetProtocolReady=False,
        runtimeStatus='not-tested')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n','utf8')
    print(json.dumps(dict(hashCases=len(native['hashCases']),registrations=len(native.get('registrations',[])),
        primaryCount=primary['count'],gadgetRows=len(gadget))))


def declarations(b,m,s,records,cells,libraries):
    load_emulator(libraries)
    from capstone import Cs,CS_ARCH_X86,CS_MODE_64
    decoder=Cs(CS_ARCH_X86,CS_MODE_64);decoder.detail=True
    pe=PE(b);h=pe.offset(HEADER_VA)
    rv,size=struct.unpack_from('<II',b,number(b,0x3c)+24+112+24)
    off=pe.offset(pe.base+rv)
    extents={pe.base+x:pe.base+y for x,y,_ in struct.iter_unpack('<III',b[off:off+size])}
    literals=Literals72(b,m)
    selected=literals.selected((6,10015,64772))['literals']
    resolver=TypeResolver(pe,s,records,h)
    mb=BODY+(number(b,h+0xb0)^0x51324a1f)
    pb=BODY+((number(b,h+0x174)+0xae6bcd68)&0xffffffff)
    owners={int(method['registeredAddress'],16):(row,method) for row in records for method in row['methods'] if method['registeredAddress']!='0x0'}
    aot=selected_aot(b,m,s,records,{(1222,4742):('ContainsKey','get_Item','Add','set_Item'),(1222,1541):('FindEntry','TryInsert')})
    rows=[];wanted=set()
    for entry in sorted(set(ENTRIES)|{int(x['address'],16) for x in aot}):
        end=extents[entry];raw=b[pe.offset(entry):pe.offset(entry)+end-entry]
        instructions=[];references=[]
        for ins in decoder.disasm(raw,entry):
            instructions.append(dict(address=hex(ins.address),bytes=ins.bytes.hex(),text=ins.mnemonic+' '+ins.op_str))
            for op in ins.operands:
                if op.type==3 and ins.reg_name(op.mem.base)=='rip':
                    cell=ins.address+ins.size+op.mem.disp;wanted.add(cell)
                    references.append(dict(site=hex(ins.address),cell=hex(cell),type=cells.get(cell)))
        row=dict(entry=hex(entry),end=hex(end),codeSHA256=sha(raw),instructions=instructions,references=references)
        if entry in owners:
            owner,method=owners[entry]
            start=parameter_start(method['index'],number(m,mb+method['index']*26+4))
            row.update(owner=owner['index'],method=method,parameters=[resolver.resolve_index(parameter_words(i,
                number(m,pb+i*8),number(m,pb+i*8+4))[1]) for i in range(start,start+method['parameterCount'])])
        rows.append(row)
    referenced_literals=[]
    for literal in literals.group(literals.global_group):
        if int(literal['cell'],16) in wanted:
            referenced_literals.append(dict(literal,value=literals.decode(literal['index'])))
    uses=method_usages(b,m,s,records,wanted)
    for row in uses:
        instance=row['methodInstance']
        if instance!=-1:
            entry=pe.offset(resolver.instances+instance*16)
            count,pointer=struct.unpack_from('<QQ',b,entry)
            if not 0<count<=255:raise ValueError('Invalid registered method argument count')
            indices=[resolver.pointer_index(number(b,pe.offset(pointer)+i*8,'Q')) for i in range(count)]
            row['methodArguments']=[resolver.resolve_index(x) for x in indices]
            row['argumentRecord']=dict(address=hex(resolver.instances+instance*16),count=count,pointer=hex(pointer),typeIndices=indices)
        else:row['methodArguments']=[]
    fields=[f for f in records[41992]['fields'] if f['rawOffset'] in ('0x400c608','0x400c628','0x400c698','0x400c6d0')]
    context_fields=[dict(owner=r['index'],ownerName=r['name'],field=f) for r in records for f in r['fields']
        if f['rawOffset'] in ('0x4005d60','0x40213e8','0x40213f0','0x40237d8','0x40237d0','0x40237e0','0x2011551')]
    primitive_base=number(b,pe.offset(0x1428A53C8)+0x30,'Q')
    if primitive_base!=0x14586B610:raise ValueError('Primitive static root differs')
    primitive_fields=[dict(owner=r['index'],ownerName=r['name'],field=f,
        address=hex(primitive_base+(int(f['rawOffset'],16)&0xffffff))) for r in records for f in r['fields']
        if f['rawOffset'] in ('0x2019f80','0x2003eb0')]
    return dict(methods=rows,literals=selected,referencedLiterals=referenced_literals,staticFields=fields,
        genericUsages=uses,dictionaryAot=aot,primitiveBase=hex(primitive_base),primitiveFields=primitive_fields,
        contextFields=context_fields,contextTypes=[records[x] for x in (11054,11061,27264,40472,17586,17591)])


def utf16_hash(text):
    raw=text.encode('utf-16-le','surrogatepass');value=0
    for (unit,) in struct.iter_unpack('<H',raw):
        value=(131*value+unit)&0xffffffff
    return value


class Runtime:
    def __init__(self,e,audit):
        self.e=e;self.audit=audit;self.strings={}
        for row in audit['methods']:
            e.allow_unwind(int(row['entry'],16));e.models.pop(int(row['entry'],16),None)

    def string(self,text):
        raw=text.encode('utf-16-le','surrogatepass');pointer=self.e.alloc(24+len(raw))
        self.e.write(pointer+16,len(raw)//2,'I')
        if raw:self.e.uc.mem_write(pointer+20,raw)
        self.strings[pointer]=text
        return pointer

    def run(self,entry,stops=(),**registers):
        self.e.write(STACK+0x100008,RETURN)
        return fragment(self.e,entry,{RETURN,0x1404C2BE0,*stops},dict(rsp=STACK+0x100008,**registers))


class RegistryRuntime(Runtime):
    """Forward string-key cache is explicit storage; reverse dictionary is native."""
    def __init__(self,e,audit):
        super().__init__(e,audit)
        self.forward={};self.forward_calls=[]
        refs={int(ref['cell'],16):ref['type']['type'] for row in audit['methods'] for ref in row['references'] if ref['type']}
        interfaces={}
        for instance in (3472,4742):
            cell=next(c for c,t in refs.items() if t.get('instanceIndex')==instance and t.get('name')=='IDictionary`2')
            interfaces[instance]=e.synthetic_type(cell)
        functions={row['method']:int(row['address'],16) for row in audit['dictionaryAot']}
        self.reverse_class=e.alloc(1024)
        self.forward_class=e.alloc(1024)
        self.reverse=e.alloc(80);self.forward_object=e.alloc(80)
        e.write(self.reverse,self.reverse_class);e.write(self.forward_object,self.forward_class)
        self.entries=self.array(64,24);self.buckets=self.array(67,4)
        e.write(self.reverse+0x18,self.entries);e.write(self.reverse+0x20,self.buckets);e.write(self.reverse+0x3c,0xffffffff,'I')
        for klass,interface in ((self.reverse_class,interfaces[4742]),(self.forward_class,interfaces[3472])):
            table=e.alloc(16);e.write(klass+0x80,table);e.write(klass+0xc0,1,'H');e.write(klass+0xbe,64,'H');e.write(table,interface)
        ctx=e.alloc(256);info=e.alloc(64);e.write(info,self.reverse_class);e.write(self.reverse_class+0x30,ctx);e.write(ctx+0x88,e.alloc(64))
        for slot,name in ((0,'get_Item'),(4,'ContainsKey'),(5,'Add')):
            e.write(self.reverse_class+0xd0+slot*8,functions[name]);e.write(self.reverse_class+0xd0+(64+slot)*8,info)
        e.write(self.forward_class+0xd0+4*8,e.model_stub(self.contains))
        e.write(self.forward_class+0xd0+5*8,e.model_stub(self.add))
        e.write(GLOBAL+0xc608,self.forward_object);e.write(GLOBAL+0xc6d0,self.reverse)
        e.write(GLOBAL+0xc698,self.string(''));e.write(GLOBAL+0xc628,self.string('Default'))
        e.write(0x14576FA5C,23,'I')
        e.models[0x141667EA0]=lambda:23

    def array(self,count,width):
        pointer=self.e.alloc(32+count*width);self.e.write(pointer+24,count,'I');return pointer

    def contains(self):
        e=self.e
        if e.reg('rcx')!=self.forward_object or e.reg('rdx') not in self.strings:
            raise ValueError('Forward string-key cache input outside declared storage')
        name=self.strings[e.reg('rdx')]
        self.forward_calls.append(dict(operation='ContainsKey',name=name))
        return int(name in self.forward)

    def add(self):
        e=self.e
        if e.reg('rcx')!=self.forward_object or e.reg('rdx') not in self.strings:
            raise ValueError('Forward string-key cache input outside declared storage')
        name=self.strings[e.reg('rdx')];value=e.reg('r8d')
        if name in self.forward:raise ValueError('Duplicate forward Add outside native contains guard')
        self.forward[name]=value;self.forward_calls.append(dict(operation='Add',name=name,value=value));return 0

    def snapshot(self):
        e=self.e;count=e.read(self.reverse+0x38,'I')
        return [dict(key=e.read(self.entries+0x28+i*24,'I'),text=self.strings[e.read(self.entries+0x30+i*24)]) for i in range(count)]


def run_native(e,audit):
    rt=Runtime(e,audit)
    cases=[]
    for text in ('','Default','A','AB','ABC','ABCD','ABCDE','Ability_Avatar_Dive_Team','TeamAbility_MoonPhase','能力_😀','\ud800','A'*257):
        pointer=rt.string(text)
        trace=rt.run(0x1500D55A8,(0x1500D5664,0x1500D55D0),r14=pointer)
        value=e.reg('edi')
        if value!=utf16_hash(text):raise ValueError('Native UTF16 ability hash differs')
        cases.append(dict(text=text.encode('unicode_escape').decode('ascii'),utf16Hex=text.encode('utf-16-le','surrogatepass').hex(),value=value,
            trace=trace,scope='Actual hash block after forward-cache miss, before dictionary writes'))
    literals={r['index']:r for r in audit['literals']}
    empty=rt.string(literals[6]['value']);default=rt.string(literals[10015]['value'])
    e.write(int(literals[6]['cell'],16),empty);e.write(int(literals[10015]['cell'],16),default)
    intern_calls=[]
    def intern():
        pointer=e.reg('rcx');intern_calls.append(dict(pointer=hex(pointer),text=rt.strings[pointer]));return pointer
    e.models[0x140546AD0]=intern
    init=rt.run(0x1500D6270,(0x1500D62BC,))
    initialization=dict(trace=init,internCalls=intern_calls,lookupDefault=rt.strings[e.read(GLOBAL+0xc698)],
        overrideDefault=rt.strings[e.read(GLOBAL+0xc628)],
        scope='Actual cctor prefix stores; string interning is explicit identity storage model; remainder not executed')
    e.models.pop(0x140546AD0)
    registrations=[]
    for name,texts,queries in (
        ('default-and-high-hash',['Default','Ability_Avatar_Dive_Team'],['Default','Ability_Avatar_Dive_Team','missing']),
        ('duplicate-keeps-original-entry',['TeamAbility_MoonPhase','TeamAbility_MoonPhase'],['TeamAbility_MoonPhase']),
        ('unicode-surrogate-units',['能力_😀'],['能力_😀']),
        ('genuine-hash-collision',['A\x83','B\x00'],['A\x83']),
        ('empty-registration-and-zero-query',[''],[''])):
        registry=RegistryRuntime(e,audit);steps=[]
        for text in texts:
            trace=registry.run(ADD,(0x1500D57AA,),rcx=registry.string(text))
            steps.append(dict(text=text,trace=trace,reverse=registry.snapshot()))
        results=[]
        for text in queries:
            key=utf16_hash(text)
            trace=registry.run(LOOKUP,(0x1500D872C,),rcx=key)
            pointer=e.reg('rax') if trace['stop']==hex(RETURN) else None
            results.append(dict(input=text,key=key,trace=trace,result=registry.strings.get(pointer),resultPointer=hex(pointer) if pointer is not None else None))
        registrations.append(dict(name=name,registrations=steps,queries=results,forwardCache=registry.forward_calls,
            reverse=registry.snapshot(),models=['Forward IDictionary<string,uint32> is an explicit typed membership/Add storage model',
                'Reverse ContainsKey/Add/get_Item/FindEntry/TryInsert execute native; no Python reverse-map lookup or writes',
                'OS thread-id source is modeled as23 and native main-thread comparison executes; no observed natural thread is claimed',
                'Concrete Dictionary<uint32,string> controlled implementation is not claimed as naturally selected DenseDictionary factory output']))
    e.synthetic_type(0x14589B538)
    constant=rt.run(0x14D2CA40D,(0x14D2CA417,))
    template=e.read(0x14586F4C0,'I')
    if template!=90000004:raise ValueError('World entity template initializer differs')
    factory=rt.run(0x15154BF20,(0x152E110C0,),rcx=0x80000091,rdx=0x80000077)
    if factory['stop']!='0x152e110c0' or e.reg('ecx')!=template:
        raise ValueError('Real world entity factory did not route exact initialized template')
    factory_result=dict(constantTrace=constant,templateId=template,entityId=0x80000091,
        secondU32Argument=0x80000077,factoryTrace=factory,lookupTemplateId=e.reg('ecx'),
        source='Actual target static field initialization instruction and complete factory prefix to template getter',
        scope='Explicit class-initialized metadata; actual template cache/load and entity creation not completed')
    pool=e.alloc(80)
    replaced=rt.run(0x146ECD7B0,rcx=pool)
    initialized=rt.run(0x146ECD800)
    if e.read(GLOBAL+0x5d60)!=pool:raise ValueError('Native existing pool identity not retained')
    e.write(GLOBAL+0x5d60,0)
    absent=rt.run(0x146ECD800,(0x146ECD850,))
    if absent['stop']!='0x146ecd850':raise ValueError('Native absent pool did not dispatch Recreate')
    return dict(hashCases=cases,initialization=initialization,registrations=registrations,worldTemplate=factory_result,
        poolWriter=dict(replace=replaced,initializeExisting=initialized,initializeAbsent=absent,
            scope='Native pointer replacement and existing/non-existing Init branches only; opaque storage pointer is not a valid entity allocator or natural pool'))


def locate_resources(binary):
    """Use the reviewed real index and serialized bundle, not name guessing."""
    try:
        from .client72_resource_locator import HashProbe,find_matches,INDEX_REPORT,read_verified
    except ImportError:
        from client72_resource_locator import HashProbe,find_matches,INDEX_REPORT,read_verified
    root=Path.cwd()
    sources={
        'local/migration72/d02-active/resource-payload-index-astra/index-final-1.json':INDEX_REPORT,
        'local/migration72/d02-active/resource-locator-astra/locator-final-1.json':'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda',
        'local/migration72/d02-active/resource-locator-astra/locator-final-1/25539185.serialized.bin':'0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7'}
    data=[read_verified(root/path,digest) for path,digest in sources.items()]
    index,locator=json.loads(data[0]),json.loads(data[1]);serialized=data[2]
    probe=HashProbe(binary,root);targets=[];blobs={}
    try:
        for name,path,digest,producer in (
            ('Gadget','Data/_ExcelBinOutput/GadgetExcelConfigData',GADGET_SHA,'152E108B0'),
            ('GadgetPrimaryIndex','Data/_ExcelBinOutput/GadgetExcelConfigDataPrimaryIndex',PRIMARY_SHA,'152E11810→157FFC650')):
            key,trace=probe.hash(path,'.MiHoYoBinData')
            targets.append(dict(name=name,path=path,key=key,hashRun=trace,sha256=digest,producer=producer,
                carrier='Both exact native loader paths call 14D6E1570 / MiHoYoBinData'))
        matches=find_matches(root/'local/migration72/d02-active/resource-payload-astra/payload-final-1',index,targets)
        objects=locator['payload']['serialized']['objects'];asset_indexes=[x for x in objects if 'assetIndex' in x]
        by_id={x['pathID']:x for x in objects}
        for target in targets:
            found=matches[target['key']]
            if len(found)!=1 or found[0]['variants']!=[(0,178)]:
                raise ValueError('Actual resource index match differs')
            packed,trace=probe.pack(target['key'],178);name,name_trace=probe.entry_name(packed>>32)
            entries=[r for obj in asset_indexes for r in obj['assetIndex']['records'] if r['name']==name]
            if len(entries)!=1:raise ValueError('Serialized native entry not unique')
            entry=entries[0];obj=by_id[entry['pathID']]
            offset=locator['payload']['serialized']['dataOffset']+obj['offset']+4
            length=obj['lengthPrefixedBytes']['bytes']
            blob=serialized[offset:offset+length]
            if obj['classID']!=1208 or entry['fileID']!=0 or entry['fileSize']!=length or sha(blob)!=target['sha256']:
                raise ValueError('Actual Gadget serialized payload differs')
            target.update(indexMatches=found,packed=packed,packRun=trace,entryName=name,nameRun=name_trace,
                entry=entry,offset=offset,bytes=length)
            blobs[target['name']]=blob
    finally:probe.close()
    return dict(sources=sources,targets=targets),blobs


def decode_primary_reference(raw):
    """Independent uvarint/zigzag index decoder with an explicit input boundary."""
    pos=0
    def varint():
        nonlocal pos
        value=0
        for shift in range(0,35,7):
            if pos>=len(raw):raise ValueError('Truncated primary index varint')
            byte=raw[pos];pos+=1
            if shift==28 and byte>15:raise ValueError('Primary index u32 overflow')
            value|=(byte&127)<<shift
            if byte<128:return value
        raise ValueError('Primary index unterminated varint')
    count=varint();count=(count>>1)^-(count&1)
    if count<0 or count>len(raw)//2:raise ValueError('Invalid primary index count')
    rows=[];offset=None
    for _ in range(count):
        key=varint()
        if offset is None:offset=varint()
        length=varint();rows.append((key,offset,length));offset+=length
        if offset>0xffffffff:raise ValueError('Primary index offset overflow')
    if pos!=len(raw):raise ValueError('Trailing primary index bytes')
    return rows


def parse_primary_native(binary,libraries,raw,truncate_at=None):
    expected=decode_primary_reference(raw)
    source_digest=sha(raw)
    if truncate_at is not None:raw=raw[:truncate_at]
    e=Offline72(binary,libraries,[],trace_instructions=False)
    e.uc.mem_map(END,4096);e.uc.mem_write(RETURN,b'\xc3')
    e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE,e._code,begin=END,end=END+4095))
    for entry in (0x157FFC780,0x152C255D0,0x14F614760,0x1553C0930,0x1553C0640):e.allow_unwind(entry)
    rt=Runtime.__new__(Runtime);rt.e=e
    def array(count,width):
        pointer=e.alloc(32+count*width);e.write(pointer+24,count,'I');return pointer
    owner=e.alloc(64);dictionary=e.alloc(80);count=len(expected)
    entries=array(count,20);buckets=array(count,4);reader=e.alloc(64);data=array(len(raw),1)
    e.uc.mem_write(data+32,raw);e.write(reader+16,data);e.write(owner+24,dictionary)
    e.write(dictionary+16,entries);e.write(dictionary+0x30,buckets);e.write(dictionary+0x40,0xffffffff,'I')
    try:
        trace=rt.run(0x157FFC780,(0x1404C2650,),rcx=owner,rdx=reader)
        got=[tuple(e.read(entries+32+i*20+off,'I') for off in (8,12,16)) for i in range(e.read(dictionary+0x3c,'I'))]
        if truncate_at is not None:
            if trace['stop']!='0x1404c2650' or got:raise ValueError('Truncated primary did not stop before inserting first row')
            return dict(sourceSHA256=source_digest,payloadSHA256=sha(raw),bytes=len(raw),cursor=e.read(reader+28,'I'),
                rows=got,trace=trace,scope='Actual count prefix then absent first key; native reader array boundary, not a successful empty index')
        if got!=expected or e.read(reader+28,'I')!=len(raw):raise ValueError('Native primary table differs from independent reference')
        queries=[]
        for key in (90000004,90000003,50011101,0xffffffff):
            query=rt.run(0x1553C0640,rcx=dictionary,rdx=key);position=e.reg('eax')
            queries.append(dict(key=key,position=position,range=None if position==0xffffffff else got[position],trace=query))
        return dict(payloadSHA256=sha(raw),count=count,cursor=e.read(reader+28,'I'),rows=got,
            end=got[-1][1]+got[-1][2],queries=queries,trace=trace,
            model='Explicit reader, owner, preallocated Dictionary<uint32,offset+length> backing storage; all parsing/inserts/queries native, no method models')
    finally:e.close()


def parse_gadget_rows(binary,metadata,startup,records,cells,libraries,raw,primary):
    if sha(SNAPSHOT.read_bytes())!=SNAPSHOT_SHA:raise ValueError('Frozen NaturalRuntime snapshot differs')
    spec=importlib.util.spec_from_file_location('tools._scene_gadget_runtime',SNAPSHOT)
    card=importlib.util.module_from_spec(spec);spec.loader.exec_module(card)
    card.TABLES['Gadget']=(0x152E109F0,40472,0x237d8,31682,GADGET_SHA)
    count=(struct.unpack_from('<I',raw)[0]+0x5c0a5c94)&0xffffffff
    if count!=primary['count'] or primary['end']!=len(raw):raise ValueError('Gadget header or primary extent differs')
    results=[]
    for item in primary['queries']:
        if item['range'] is None:continue
        key,offset,length=item['range'];data=raw[offset:offset+length]
        rt=card.NaturalRuntime(binary,metadata,startup,records,cells,libraries,'Gadget');e=rt.e
        try:
            klass=next(k for k,v in rt.type_records.items() if v.get('typeDefinition')==40472)
            size=int(records[40472]['fieldLayout']['rawSizeAndFlags'][0],16)
            obj=e.alloc(size);e.write(obj,klass);rt.objects[obj]=40472
            obj,cursor,trace=rt.parse(0x14E62B290,data,obj=obj)
            if cursor!=length:raise ValueError('Gadget row did not consume exact primary slice')
            values=[]
            for owner in (27264,40472):
                for field in records[owner]['fields']:
                    off=field['offset'];typ=field['type'];kind=typ['kind']
                    if off is None:continue
                    if kind=='string':value=rt.strings.get(e.read(obj+off))
                    elif typ.get('typeDefinition')==58209:value=rt.decode(obj+off)
                    elif kind=='bool':value=bool(e.read(obj+off,'B'))
                    elif kind in ('uint32','int32','uint64'):value=e.read(obj+off,{'uint32':'I','int32':'i','uint64':'Q'}[kind])
                    elif kind=='valuetype' and records[typ['typeDefinition']]['parent'].get('typeDefinition')==204:value=e.read(obj+off,'I')
                    else:continue
                    values.append(dict(owner=owner,field=field,value=value))
            row=dict(key=key,offset=offset,length=length,sliceSHA256=sha(data),cursor=cursor,values=values,
                objectBytes=bytes(e.uc.mem_read(obj,size)).hex(),trace=trace)
            if key==90000004:row['factory']=world_factory_from_row(rt,obj,key)
            if key==90000004:
                bad_obj=e.alloc(size);e.write(bad_obj,klass);rt.objects[bad_obj]=40472
                _,bad_cursor,bad_trace=rt.parse(0x14E62B290,data[:-1],obj=bad_obj)
                if bad_trace['stop']!='0x1404c2650':raise ValueError('Truncated actual Gadget row missed native boundary')
                row['truncated']=dict(bytes=len(data)-1,cursor=bad_cursor,payloadSHA256=sha(data[:-1]),trace=bad_trace)
            row.update(nativeSources=rt.functions,typedClasses=rt.type_records)
            results.append(row)
        finally:e.close()
    return results


def world_factory_from_row(rt,obj,key):
    """Actual cache insert/getter/factory until the unsupplied object-pool root."""
    e=rt.e
    dictionary=e.alloc(80);e.write(dictionary,rt.dictionary_class)
    e.write(dictionary+0x18,rt.array(4,24));e.write(dictionary+0x20,rt.array(7,4));e.write(dictionary+0x3c,0xffffffff,'I')
    e.write(GLOBAL+0x237d8,dictionary)
    context=e.read(rt.dictionary_class+0x30);method=e.read(context+0x88)
    e.write(STACK+0x100030,method)
    inserted=rt.call(0x1578B6A20,rcx=dictionary,rdx=key,r8=obj,r9=2)
    if e.read(dictionary+0x38,'I')!=1:raise ValueError('Real Gadget cache insertion failed')
    # This is a declared controlled non-streaming branch, not a natural flag capture.
    e.write(0x14587cb61,0,'B')
    rt.bind_function(0x152E110C0)
    query=rt.call(0x152E110C0,rcx=key,rdx=1)
    if e.reg('rax')!=obj:raise ValueError('Native template getter differs from actual selected row')
    missing=rt.call(0x152E110C0,rcx=0xffffffff,rdx=1)
    if e.reg('rax')!=0:raise ValueError('Native missing template getter did not return null')
    rt.bind_function(0x15154BF20)
    # Actual constant writer; pool is deliberately unavailable, and execution stops before its call.
    rt.bind_function(0x14D2CA110)
    constant=rt.call(0x14D2CA40D,(0x14D2CA417,))
    factory=rt.call(0x15154BF20,(0x1404C2BE0,),rcx=0x80000091,rdx=0x80000077)
    if e.reg('rbx')!=obj:raise ValueError('Factory template handle lost real row')
    if factory['stop']!='0x1404c2be0':raise ValueError('Unavailable pool did not reach actual null boundary')
    return dict(insert=inserted,query=query,missingQuery=missing,constant=constant,factory=factory,
        row=hex(obj),templateId=key,poolRoot=hex(GLOBAL+0x5d60),poolProvided=False,
        scope='Actual selected Gadget row and native Dictionary insert→getter→world-factory; non-streaming branch is explicit; unavailable pool stops at native null helper before allocation')


def observation_scope():
    """Classify fixed completed captures without using their historical process identity."""
    sources={
        'local/migration72/d02-active/plain/run-elevated3.json':'303db4bc6daa85d3fda6d73f52cbe339021ba84cfaa45152bb4bf92a1746c58a',
        'local/migration72/d02-active/main/cold-arrays-tool/live-elevated1.json':'63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0'}
    data=[]
    for path,digest in sources.items():
        raw=Path(path).read_bytes()
        if sha(raw)!=digest:raise ValueError('Fixed natural observation changed')
        data.append(json.loads(raw))
    reads=data[1]['reads']
    if len(reads)!=99 or any(r.get('error') for r in reads):raise ValueError('Expected fixed 99 successful read spans')
    return dict(sources=sources,readCount=len(reads),readLabels=[r['label'] for r in reads],
        abilityRegistry='not-sampled',objectPool='not-sampled',gadgetConfigRegistry='not-sampled',
        scope='Historical cold-class/SDK observations only; no absent/null claim for unrequested scene roots; no process is opened by this tool')


if __name__=='__main__':main()
