"""CN72 Avatar constructor fragments and bounded omitted-input consumers.

Unresolved generic constructor dispatch is stopped, never replaced by success.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path
try:
    from .extract_client72 import HASHES, PE, number, read_verified, extract
    from .trace_client72_avatar_core import core_aot
    from .trace_client72_avatar_outer import avatar_aot
    from .trace_client72_avatar_signed_trees import selected_aot
    from .trace_client72_player_store import type_cells
    from .trace_client72_token import load_emulator
    from .client72_avatar_contract import AvatarContractGraph, avatar_wire, declarations, RETURN
    from .client72_avatar_state import StateRuntime
    from .client72_avatar_skill_depot import EVIDENCE_SHA as DEPOT_INPUTS, candidate_projection
    from .client72_graph_fixtures import scalar, message
    from .client72_emulator import Offline72, GLOBAL
except ImportError:
    from extract_client72 import HASHES, PE, number, read_verified, extract
    from trace_client72_avatar_core import core_aot
    from trace_client72_avatar_outer import avatar_aot
    from trace_client72_avatar_signed_trees import selected_aot
    from trace_client72_player_store import type_cells
    from trace_client72_token import load_emulator
    from client72_avatar_contract import AvatarContractGraph, avatar_wire, declarations, RETURN
    from client72_avatar_state import StateRuntime
    from client72_avatar_skill_depot import EVIDENCE_SHA as DEPOT_INPUTS, candidate_projection
    from client72_graph_fixtures import scalar, message
    from client72_emulator import Offline72, GLOBAL

EVIDENCE_SHA = DEPOT_INPUTS | {'avatar-skill-depot-observations.json': 'a8a7b4ce71869c4af88e3bf8164413ed109c7d6e051478d1e682b19e5d0e1a95'}
CTOR_NATIVE = {0x15183FE60, 0x157D97690, 0x157D7FE90, 0x14FF00C60, 0x15152D870}
NATIVE = CTOR_NATIVE | {0x152F7EC20, 0x15184E020, 0x1517AB3A0, 0x15184D480, 0x1518462C0,
    0x15184FA10, 0x15184F260, 0x15184E030, 0x15184F680, 0x15184F910, 0x151850370, 0x151845140,
    0x15184EC00, 0x15183EAE0, 0x15184E100}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ('client','output','observations'): parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--directory',type=Path,default=Path('protocol/cn-7.2'))
    parser.add_argument('--python-libs',type=Path,default=Path('local/migration72/python'))
    parser.add_argument('--resource-source',type=Path,default=Path('local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'))
    parser.add_argument('--resource-evidence',type=Path,default=Path('local/migration72/evidence'))
    args=parser.parse_args()
    if args.output.resolve()==args.observations.resolve() or any(p.resolve().is_relative_to(args.client.resolve()) for p in (args.output,args.observations)):
        parser.error('Distinct outputs outside client required')
    for name,digest in EVIDENCE_SHA.items():
        if hashlib.sha256((args.directory/name).read_bytes()).hexdigest()!=digest: raise ValueError('Reviewed input changed: '+name)
    b,m,s=[read_verified(args.client/name,digest) for name,digest in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True); declarations(b,m,s,records,args.directory)
    cells=type_cells(b,m,s,records); bindings=core_aot(b,m,s,records); outer_bindings=avatar_aot(b,m,s,records,dict(types=[]))
    outer=json.loads((args.directory/'avatar-outer-observations.json').read_text('utf8'))
    declared=initializer_declarations(b,m,s,records,cells,args.python_libs); candidate,_=candidate_projection(args)
    rows=[run_case(b,records,cells,bindings,outer_bindings,outer,declared,args,case) for case in cases()]
    bad=rejections(b,records,cells,bindings,outer_bindings,outer,declared,args)
    report=dict(schemaVersion=1,task='F-03d3f4d1',inputSHA256=HASHES,inputContracts=EVIDENCE_SHA,
        declarations=declared,candidateSource=candidate,fixtures=rows,rejections=bad,
        completeConstructor=False,completeAvatarHandler=False,businessRequiredForEntry='not-established',runtimeStatus='not-tested',targetProtocolReady=False,
        models=['Typed managed allocation and initialized empty arrays/comparer caches; native constructor stores and covered subconstructors execute',
            'Exact constructor fragments have explicit r14/model live-ins; skipped generic dispatch gaps are not reported executed',
            'Generic MethodInfo/rgctx storage is a controlled fixture; unresolved callee pointers remain zero and stop before call',
            'Array.Clear is an explicit byte-storage model for controlled u32 HashSet slots12/buckets4 and encoded-int pair entries40/buckets4; native code decides ranges and resets headers',
            'Inherited f1 reader/batch/GUID/class fixtures and f4a state/scene dictionary model boundaries remain explicit',
            'Empty reader collections have managed count/index/enumerator adapters; no resource cache omission establishes business necessity',
            'Avatar1001, scene/entity dictionaries and optional unusable entity are synthetic fixtures; no real client ownership/world state is asserted'],
        unresolved=['15183ff11 enum HashSet comparer constructor indirect target',
            '15183ff71 SortedDictionary comparer constructor indirect target',
            '15183ffb4/15183fff4/151840037 custom34054 inherited constructor indirect target',
            '15183f290 equipment refresh tail is reached even for empty equipment; not bypassed',
            '151847f70 skill initialization,15184a5f0 equipment,61980 lifetime,tag29/tag38 required consumers'])
    root=Path(__file__).resolve().parents[1]
    report['sourceSHA256']={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in (
        'tools/client72_avatar_initializer.py','tools/client72_avatar_state.py','tools/client72_avatar_contract.py',
        'tools/client72_emulator.py','tools/client72_session_runtime.py','tools/client72_avatar_skill_depot.py',
        'tools/audit_d02_resources72.py','tools/audit_resource72.py')}
    for path,value in ((args.output,report),(args.observations,compact(report))):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=len(rows),rejections=len(bad),completeConstructor=False,completeAvatarHandler=False,targetProtocolReady=False)))


class InitializerRuntime(StateRuntime):
    def __init__(self,e,g,declared):
        super().__init__(e,g)
        self.classes={};self.allocated_types={};self.constructor_writes=[];self.collect_writes=False;self.model_pointer=None
        self.array_widths={};self.clears=[]
        for a in NATIVE:
            e.allow(a) if a in e.ends else e.allow_unwind(a)
            e.models.pop(a,None)
        e.models.pop(0x15184A5F0,None)
        e.write(self.klass+0x160,0x15184E020)
        self.declared=declared
        for cell,typ in declared['referencedTypes'].items():
            pointer=e.synthetic_type(int(cell,16));e.write(pointer+0xc8,1,'B');self.classes[pointer]=typ
            if typ.get('typeDefinition')==1234:
                cache=e.alloc();e.write(pointer+0x38,cache);e.write(cache,e.alloc())
        e.models[0x1405336C0]=self.allocate_typed
        for cell in declared['emptyArrayCells']:
            e.write(int(cell,16),self.empty_array())
        for cell in declared['listContextCells']:
            context=self.make_context(int(cell,16));e.write(context+0x10,self.empty_array())
        # An initialized EqualityComparer<enum20060> cache, without executing reflection.
        context=self.make_context(int(declared['enumContextCell'],16));method=e.alloc();klass=e.alloc(512);inner=e.alloc();comparer=e.alloc();cache=e.alloc()
        e.write(context+0x10,method);e.write(method,klass);e.write(klass+0xc8,1,'B');e.write(klass+0x30,inner)
        e.write(inner+0x10,comparer);e.write(comparer+0xc8,1,'B');e.write(comparer+0x38,cache);e.write(cache,e.alloc())
        e.write(context+0x18,e.alloc())
        self.enum_comparer_cache=cache
        for cell,offset in ((declared['sortedContextCell'],0x10),(declared['customContextCell'],0x18)):
            context=self.make_context(int(cell,16));e.write(context+offset,e.alloc())
        self.allocation_number=0;self.fail_allocation=None
        e.models[0x159C68C70]=self.clear_array
        self.constructor_hook=e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE,self.on_write)

    def allocate_typed(self):
        e=self.e;klass=e.reg('rcx');typ=self.classes[klass]
        self.allocation_number+=1
        if self.fail_allocation==self.allocation_number:return 0
        pointer=e.alloc(512);e.write(pointer,klass);self.allocated_types[pointer]=typ
        return pointer

    def empty_array(self):
        p=self.e.alloc(32);self.e.write(p+24,0,'I');return p

    def old_array(self,width,count=4):
        e=self.e;p=e.alloc(32+width*count);e.write(p+24,count,'I');e.uc.mem_write(p+32,b'\x7d'*(width*count));self.array_widths[p]=(width,count)
        return p

    def clear_array(self):
        e=self.e;array=e.reg('rcx');index=e.reg('edx');count=e.reg('r8d');width,capacity=self.array_widths[array]
        if index+count>capacity:raise ValueError('Controlled Array.Clear range exceeded')
        e.uc.mem_write(array+32+index*width,bytes(count*width));self.clears.append(dict(width=width,index=index,count=count));return 0

    def raw_lists(self,source):
        e=self.e
        for offset in (0x18,0x20,0x30,0x40,0x60,0x68,0x80):
            listing=e.read(source+offset);klass=e.alloc(1024);e.write(listing,klass)
            e.write(klass+0x1D8,e.model_stub(lambda:e.read(e.reg('rcx')+24,'I')))
            width=8 if offset==0x18 else 4
            e.write(klass+0x250,e.model_stub(lambda w=width:e.read(e.read(e.reg('rcx')+16)+32+e.reg('edx')*w,'Q' if w==8 else 'I')))

    def omission_fragments(self,model,source,old,scalar_value):
        e=self.e;rows=[]
        for name,entry,source_offset,target_offset in (
            ('tag24-u32-set',0x1518462C0,0x40,0xA0),('tag36-u32-set',0x15184D480,0x30,0x58),
            ('tag22-encoded-pairs',0x15184FA10,0x58,0x1E0)):
            pointer=e.read(model+target_offset)
            if old:
                if not pointer:pointer=e.alloc();e.write(model+target_offset,pointer)
                if name=='tag22-encoded-pairs':
                    entries=self.old_array(40);buckets=self.old_array(4);e.write(pointer+0x10,entries);e.write(pointer+0x18,buckets);e.write(pointer+0x38,3,'I')
                else:
                    slots=self.old_array(12);buckets=self.old_array(4);e.write(pointer+0x18,slots);e.write(pointer+0x20,buckets)
                    e.write(pointer+0x2C,3,'I');e.write(pointer+0x30,0xffffffff,'I');e.write(pointer+0x34,2,'I')
            before=len(self.clears);trace=self.invoke(entry,{RETURN,0x1404C2BE0},rcx=model,rdx=e.read(source+source_offset))
            count=e.read(pointer+(0x38 if name=='tag22-encoded-pairs' else 0x34),'I') if pointer else 0
            if trace['stop']!=hex(RETURN) or count!=0:raise ValueError('Omitted collection did not complete clear')
            header={hex(off):e.read(pointer+off,'I') for off in ((0x38,0x3C,0x40,0x44) if name=='tag22-encoded-pairs' else (0x28,0x2C,0x30,0x34))} if pointer else {}
            rows.append(dict(name=name,oldNonempty=old,count=count,header=header,clears=self.clears[before:],trace=trace))
        sentinel=e.alloc();e.write(model+0x1D0,sentinel)
        trace=self.invoke(0x15184F260,rcx=model,rdx=e.read(source+0x48))
        if e.read(model+0x1D0)!=sentinel:raise ValueError('Omitted tag10 unexpectedly replaced existing dictionary')
        rows.append(dict(name='tag10-empty-preserves-existing-dictionary',preserved=True,trace=trace))
        for entry,raw_offset,model_offset in ((0x15184E030,0xD4,0x2E4),(0x15184F680,0xE8,0x21C),(0x15184F910,0xF4,0x23C)):
            prior=999 if old else 0;e.write(model+model_offset,prior,'I');value=e.read(source+raw_offset,'I')
            trace=self.invoke(entry,rcx=model,rdx=value,r8=1)
            if e.read(model+model_offset,'I')!=scalar_value:raise ValueError('Raw scalar setter differs')
            rows.append(dict(name='scalar-'+hex(raw_offset),prior=prior,value=value,stored=e.read(model+model_offset,'I'),trace=trace))
        optional=self.invoke(0x15184E472,{0x15184E4B5},rdi=model,rbx=source)
        rows.append(dict(name='tag26-absent-child',trace=optional))
        for entry,r9 in ((0x151850370,source),(0x151845140,0)):
            trace=self.invoke(entry,rcx=model,rdx=0,r8=1001,r9=r9)
            rows.append(dict(name='optional-tail-'+hex(entry),trace=trace))
        return rows

    def make_context(self,cell):
        e=self.e;info=e.alloc();klass=e.alloc(512);context=e.alloc();e.write(cell,info);e.write(info,klass);e.write(klass+0x30,context)
        return context

    def on_write(self,uc,access,address,size,value,data):
        if self.collect_writes and self.model_pointer<=address<self.model_pointer+0x300:
            self.constructor_writes.append(dict(offset=address-self.model_pointer,width=size,value=value,source=hex(self.e.reg('rip'))))

    def constructor_fragments(self,model):
        self.model_pointer=model;self.collect_writes=True;rows=[]
        for name,entry,stop,registers in (
            ('prefix-to-enum-comparer-ctor',0x15183FE60,0x15183FF11,dict(rcx=model)),
            ('u64-list-to-sorted-comparer-ctor',0x15183FF1A,0x15183FF71,dict(r14=model)),
            ('custom-list-to-inherited-ctor',0x15183FF7A,0x15183FFB4,dict(r14=model)),
            ('container-tail-and-costume-subconstructors',0x15184003D,0x151840331,dict(r14=model))):
            before=len(self.constructor_writes);trace=self.invoke(entry,{stop,0x1404C2BE0},**registers)
            if trace['stop']!=hex(stop):raise ValueError('Unexpected constructor fragment stop: '+name)
            rows.append(dict(name=name,trace=trace,writes=self.constructor_writes[before:]))
        self.collect_writes=False
        return rows

    def snapshot(self,model):
        e=self.e;containers={}
        for offset in (0x68,0x70,0xA0,0x88,0x1F0,0xB8,0x1F8,0x200,0x1D8,0xB0,0x60,0x1E0):
            pointer=e.read(model+offset);typ=self.allocated_types[pointer]
            containers[hex(offset)]=dict(type=typ,nonNull=bool(pointer))
            if typ['typeDefinition']==1255:
                containers[hex(offset)].update(count=e.read(pointer+24,'I'),arrayNonNull=bool(e.read(pointer+16)),arrayLength=e.read(e.read(pointer+16)+24,'I'))
        layer=e.read(e.read(model+0xB0)+0x20);state=e.read(e.read(model+0xB0)+0x18)
        return dict(default284=e.read(model+0x284,'I'),default2b0=e.read(model+0x2B0,'B'),containers=containers,
            costumeLayerType=self.allocated_types[layer]['typeDefinition'],costumeStateType=self.allocated_types[state]['typeDefinition'],
            costumeSets=[dict(type=self.allocated_types[e.read(layer+off)]['typeDefinition'],
                listTypes=[self.allocated_types[e.read(e.read(layer+off)+x)]['instanceIndex'] for x in (0x10,0x18)]) for off in (0x20,0x28)],
            unresolvedUnwrittenOffsets={hex(off):e.read(model+off) for off in (0x1E8,0x1C0,0x50,0x1C8,0x40)})

    def close(self):
        self.e.uc.hook_del(self.constructor_hook);super().close()


def cases():
    return [dict(name=name,guid=guid,old=old,scalar=value,scene=scene,entityId=entity_id,equips=equips) for name,guid,old,value,scene,entity_id,equips in (
        ('fresh-omitted',9007199254740993,False,0,'none',0,[]),
        ('old-values-omitted',9223372036854775809,True,0,'none',0,[]),
        ('nonzero-scalars',9007199254740993,True,42,'none',0,[]),
        ('missing-entity-high-id',9223372036854775809,False,0,'missing',0x80000001,[]),
        ('missing-entity-max-id',9007199254740993,True,0,'missing',0xffffffff,[]),
        ('unusable-existing-entity',9223372036854775809,False,0,'entity',0x100001,[]),
        ('equipment-unknown-boundary',9007199254740993,False,0,'none',0,[0x100000001]))]


def run_case(b,records,cells,bindings,outer_bindings,outer,declared,args,case):
    e=Offline72(b,args.python_libs,records,trace_instructions=False)
    g=AvatarContractGraph(e,args.directory,cells,bindings,outer_bindings,outer);rt=None
    try:
        wire=message(6,avatar_wire(1001,case['guid'],case['equips'],[])+scalar(19,1)+scalar(11,501)+scalar(4,1)
                     +b''.join(scalar(tag,case['scalar']) for tag in (21,25,31)));reader=g.run_graph(30509,wire)
        rt=InitializerRuntime(e,g,declared)
        # Batch delivery remains f1's boundary; no constructor/handler completion is inferred from it.
        e.models[0x15184E100]=rt.deliver;dispatch=rt.root();e.models.pop(0x15184E100,None)
        model,source=rt.delivery[0];e.write(model+0x284,777,'I');e.write(model+0x2B0,0,'B')
        constructor=rt.constructor_fragments(model);snapshot=rt.snapshot(model)
        if snapshot['default284']!=1 or snapshot['default2b0']!=1:raise ValueError('Native constructor defaults differ')
        rt.raw_lists(source);identity=rt.identity(model,source)
        omissions=rt.omission_fragments(model,source,case['old'],case['scalar'])
        entity=rt.setup_scene(case['scene'],entity_id=case['entityId'],usable=False);e.write(model+0x218,case['entityId'],'I')
        before=len(rt.scene_queries);flush=rt.invoke(0x152F7EC20,{RETURN,0x1547052A0,0x1404C2BE0},rcx=model)
        if flush['stop']!=hex(RETURN) or rt.scene_queries[before:]!= ([] if case['scene']=='none' else [case['entityId']]):
            raise ValueError('Scene/entity absent flush did not actually return')
        # One connected real handler prefix. No resource cache is removed to force this path.
        e.write(GLOBAL+0xA3A0,0);whole=rt.invoke(0x15184E100,{0x15183F290,0x15184A5F0,0x1404C2BE0},rcx=model,rdx=source,r8=0)
        expected=0x15184A5F0 if case['equips'] else 0x15183F290
        if whole['stop']!=hex(expected):raise ValueError('Whole handler prefix unknown boundary changed')
        traces=[x['trace'] for x in constructor+omissions]+[flush,whole]
        if any({hex(x) for x in NATIVE}&set(t['models']) for t in traces):raise ValueError('Critical initializer body was modeled')
        if not {'0x15183fe60','0x157d97690','0x157d7fe90'}<=set(constructor[0]['trace']['executedAddresses']):
            raise ValueError('Native ctor prefix/comparer getter absent')
        if not {'0x14ff00c60','0x15152d870'}<=set(constructor[-1]['trace']['executedAddresses']):
            raise ValueError('Native costume child constructors absent')
        return dict(case,wire=wire.hex(),reader=reader,dispatch=dispatch,constructor=constructor,snapshot=snapshot,
            identity=identity,omissions=omissions,flush=dict(trace=flush,sceneQueries=rt.scene_queries[before:]),wholePrefix=whole)
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(case=case,calls=e.calls,registers={n:hex(e.reg(n)) for n in ('rip','rcx','rdx','r8','r14')}),indent=2)+'\n','utf8')
        raise
    finally:
        if rt:rt.close()
        g.close();e.close()


def initializer_declarations(b,m,s,records,cells,libraries):
    load_emulator(libraries)
    from capstone import Cs,CS_ARCH_X86,CS_MODE_64
    pe=PE(b);decoder=Cs(CS_ARCH_X86,CS_MODE_64);decoder.detail=True
    starts=sorted({int(x['registeredAddress'],16) for t in records for x in t['methods'] if x['registeredAddress']!='0x0'})
    optional=number(b,0x3C)+24;rva,size=struct.unpack_from('<II',b,optional+112+3*8)
    ranges={pe.base+start:pe.base+end for start,end,_ in struct.iter_unpack('<III',b[pe.offset(pe.base+rva):pe.offset(pe.base+rva)+size])}
    functions={};references={};targets={}
    for entry in CTOR_NATIVE:
        end=ranges.get(entry) or next(x for x in starts if x>entry)
        instructions=[]
        for i in decoder.disasm(b[pe.offset(entry):pe.offset(entry)+end-entry],entry):
            instructions.append(dict(address=hex(i.address),instruction=i.mnemonic+' '+i.op_str))
            for operand in i.operands:
                if operand.type==3 and operand.mem.base==41:
                    cell=i.address+i.size+operand.mem.disp;targets[i.address]=cell
                    if cell in cells:references[hex(cell)]=cells[cell]['type']
        functions[hex(entry)]=dict(end=hex(end),extent='PE-exception-table' if entry in ranges else 'registered-method-boundary',
            nativeBytesSHA256=hashlib.sha256(b[pe.offset(entry):pe.offset(entry)+end-entry]).hexdigest(),instructions=instructions)
    aot=selected_aot(b,m,s,records,{(2147,11):('.ctor',),(2147,5917):('.ctor',),(2034,12373):('.ctor',),(69137,5278):('.ctor',)})
    expected={(2147,5917):'0x156fbc020',(2034,12373):'0x1570f5f20',(69137,5278):'0x155b229f0'}
    for key,address in expected.items():
        if {r['address'] for r in aot if (r['owner'],r['classInstance'])==key}!={address}:raise ValueError('Constructor concrete AOT changed')
    vslot=next(x for x in records[18163]['methods'] if x['virtualSlot']==18)
    if vslot['registeredAddress']!='0x15184e020':raise ValueError('Avatar entity getter override changed')
    return dict(functions=functions,concreteAOT=aot,referencedTypes=references,entityIdSlot18=vslot,
        emptyArrayCells=sorted({hex(targets[a]) for a in (0x15183FE92,0x15183FF2F,0x151840264,0x1518402AA,
            0x14FF00C9B,0x14FF00CBB,0x14FF00CEE,0x14FF00D0E,0x15152D88E)}),
        listContextCells=sorted({hex(targets[a]) for a in (0x151840055,0x151840093,0x1518400D1,0x1518401FF)}),
        enumContextCell=hex(targets[0x15183FED0]),sortedContextCell=hex(targets[0x15183FF4D]),customContextCell=hex(targets[0x15183FF92]),
        ownerFields={str(i):records[i]['fields'] for i in (18163,87323,77936,48287,2149)},
        owners=[dict(typeIndex=i,name=records[i]['name'],methods=records[i]['methods']) for i in (18163,34054,87323,77936,48287)])


def rejections(b,records,cells,bindings,outer_bindings,outer,declared,args):
    rows=[]
    for name in ('allocation-failure','cold-u32-comparer','missing-enum-ctor-context','null-target-set',
                 'null-wire-list-differs-from-omission','flush-ifix','usable-entity-component-boundary'):
        e=Offline72(b,args.python_libs,records,trace_instructions=False)
        g=AvatarContractGraph(e,args.directory,cells,bindings,outer_bindings,outer);rt=None
        try:
            reader=g.run_graph(30509,message(6,avatar_wire(1001,9007199254740993,[],[])))
            rt=InitializerRuntime(e,g,declared);e.models[0x15184E100]=rt.deliver;rt.root();e.models.pop(0x15184E100,None)
            model,source=rt.delivery[0]
            if name=='allocation-failure':
                rt.fail_allocation=1;trace=rt.invoke(0x15183FE60,{0x1404C2BE0},rcx=model);expected='0x1404c2be0';scope='native-null-branch-after-controlled-allocation-failure'
            elif name=='cold-u32-comparer':
                cell=next(int(cell,16) for cell,typ in declared['referencedTypes'].items() if typ.get('typeDefinition')==1234 and typ['arguments'][0]['kind']=='uint32')
                cache=e.read(e.read(cell)+0x38);e.write(cache,0);e.models.pop(0x140517680,None)
                trace=rt.invoke(0x15183FE60,{0x157D976AE},rcx=model);expected='0x157d976ae';scope='native-cold-comparer-branch-before-unknown-metadata-reflection-state'
            elif name=='missing-enum-ctor-context':
                context=e.read(e.read(e.read(int(declared['enumContextCell'],16)))+0x30);e.write(context+0x18,0);e.models.pop(0x140524010,None)
                trace=rt.invoke(0x15183FE60,{0x140524010},rcx=model);expected='0x140524010';scope='native-missing-generic-context-prefix-before-runtime-resolution'
            else:
                rt.constructor_fragments(model)
                if name=='null-target-set':
                    e.write(model+0xA0,0);trace=rt.invoke(0x1518462C0,{0x1404C2BE0},rcx=model,rdx=e.read(source+0x40));expected='0x1404c2be0';scope='native-null-target-container-error'
                elif name=='null-wire-list-differs-from-omission':
                    pointer=e.read(model+0x1E0);e.write(pointer+0x38,3,'I');trace=rt.invoke(0x15184FA10,rcx=model,rdx=0)
                    if e.read(pointer+0x38,'I')!=3:raise ValueError('Runtime null did not preserve old storage')
                    expected=hex(RETURN);scope='native-runtime-null-preserves-old-count; valid-wire-omission-is-nonnull-empty'
                elif name=='flush-ifix':
                    e.write(0x14586AE59,1,'B');trace=rt.invoke(0x152F7EC20,{0x152F7ECE3},rcx=model);expected='0x152f7ece3';scope='native-ifix-guard-before-patch-state'
                else:
                    rt.setup_scene('entity',entity_id=0x100001,usable=True);e.write(model+0x218,0x100001,'I')
                    trace=rt.invoke(0x152F7EC20,{0x152F7EC86},rcx=model);expected='0x152f7ec86';scope='native-valid-entity-prefix-before-unresolved-component-context'
            if trace['stop']!=expected:raise ValueError('Initializer boundary differs: '+name)
            rows.append(dict(name=name,scope=scope,trace=trace))
        except Exception:
            args.output.with_suffix('.rejections.failure.json').write_text(json.dumps(dict(case=name,calls=e.calls,registers={n:hex(e.reg(n)) for n in ('rip','rcx','rdx','r8')}),indent=2)+'\n','utf8');raise
        finally:
            if rt:rt.close()
            g.close();e.close()
    return rows


def compact(value):
    if isinstance(value,list):return [compact(x) for x in value]
    if isinstance(value,dict):
        if 'executedAddresses' in value:
            return {k:value[k] for k in ('entry','stop','instructions','nativePathSHA256','models')}|dict(nativeEntries=sorted(({hex(x) for x in NATIVE}|{value['entry'],value['stop']})&set(value['executedAddresses'])))
        if 'reader' in value:value=dict(value,readerResult=value['reader']['result'])
        return {k:compact(v) for k,v in value.items() if k!='reader'}
    return value


if __name__=='__main__':main()
