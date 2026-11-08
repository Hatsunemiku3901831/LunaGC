"""Bounded offline interface continuation with explicit cross-epoch references."""
import argparse
import copy
import json
from pathlib import Path
import struct

REPO=Path(__file__).resolve().parents[1]
if not __package__:
    import sys
    sys.path.insert(0,str(REPO));__package__='tools'
from . import client72_avatar_fresh_ancestor as ancestor
from . import client72_avatar_handler_closure as frozen

RAW=Path('local/migration72/d02-resume-20261108/r1-fresh-interfaces')
INPUT='local/migration72/d02-resume-20261108/r1-interface-sampling/live-first.private.json'
INPUT_SHA='3c08c1f72e181084bc3d22e7199cf08ca306083322d0638cd6322ef42064d040'
MONITOR='local/migration72/d02-active/plain/run-resume-20261108-3.json'
MONITOR_SHA='8136125522ddc6983a0c22e37bffe5556da58fbb981416493e5b1a6442d7e6fc'
SAMPLER='local/migration72/d02-resume-20261108/r1-interface-sampling/live-first.private.source.py'
SAMPLER_SHA='02ffaeab9c5e2f96bd135f7007b1f964c2863fb16471611c03867b3aefdda251'
ANCESTOR_TOOL='tools/client72_avatar_fresh_ancestor.py'
ANCESTOR_SHA='3715861c8c6570a37d69a5de7967633a563671eba2b185e3e77759d4142c548c'
ANCESTOR_RAW='local/migration72/d02-resume-20261108/r1-fresh-ancestor/first.json'
ANCESTOR_RAW_SHA='dc738c4f3f3d80cdb72bb852d2250de685657fa489818f436ce8e8cc9933256b'
LIMIT=2_000_000
OPAQUE=0x6e0000000
DIRECT_WINDOWS={
    0x14051e2c0:(0x14051e718,'1771e00105e3fcdc2d06a1c942e0002bbb417ce9b3b8444fa8c1ffbaf5c8eeef'),
    0x1404eea50:(0x1404efc2e,'b92006506acefc36c21a50d0ae69e021ee7e249e88ded04f5267d0e18e3c70c2')}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python-libs',type=Path,default=REPO/'local/migration72/python')
    args=parser.parse_args()
    if not args.output.resolve().is_relative_to((REPO/RAW).resolve()):parser.error('Output must stay in r1-fresh-interfaces')
    if args.output.exists() or args.output.with_suffix('.source.py').exists():parser.error('Prior evidence is frozen; choose a new output')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    pins=ancestor.prior.verify_sources()
    for name,sha in ((ANCESTOR_TOOL,ANCESTOR_SHA),(ANCESTOR_RAW,ANCESTOR_RAW_SHA),(INPUT,INPUT_SHA),(MONITOR,MONITOR_SHA),(SAMPLER,SAMPLER_SHA),
                     (ancestor.PRIOR_TOOL,ancestor.PRIOR_SHA),(ancestor.FRESH,ancestor.FRESH_SHA),(ancestor.MONITOR,ancestor.MONITOR_SHA),(ancestor.SAMPLER,ancestor.SAMPLER_SHA)):
        ancestor.prior.read_pinned(name,sha);pins[name]=sha
    b,m,s,*_=[frozen.base.read_verified(args.client/name,sha) for name,sha in frozen.FIVE_INPUTS.items()]
    frozen.base.load_emulator(args.python_libs)
    records,_=frozen.base.extract(b,m,startup=s,structures=True);pe=frozen.base.PE(b)
    old=frozen.audit_snapshot(b,m)
    previous=json.loads((REPO/ANCESTOR_RAW).read_bytes())
    static=ancestor.object_sources(b,s,records,pe,frozen.base)
    old_bridge=ancestor.relocate_ancestors(json.loads((REPO/ancestor.FRESH).read_bytes()),old,b,m,s,pe,frozen.base,static)
    interface_rows=ancestor.provide_interface_rows(old,b,m,s,pe,frozen.base,records)
    parameter=ancestor.prior.generic_parameter(old,b,m,pe,frozen.base)
    incoming=json.loads((REPO/INPUT).read_bytes())
    bridge=bind_interfaces(incoming,old,b,m,pe,frozen.base)
    extra_parameter=parameter362(parameter,b,m,pe,frozen.base)
    result=continue_native(b,m,s,records,old,parameter,extra_parameter,previous,bridge,args.python_libs,args.output)
    projection=None;projected_result=None
    if is_projection_stop(result,old):
        projected=copy.deepcopy(old)
        projection=fixed_method_indices(projected,records)
        projected_result=continue_native(b,m,s,records,projected,parameter,extra_parameter,previous,bridge,args.python_libs,args.output)
    report=dict(task='F-03d3f4d2-fresh-interfaces',sourceSHA256=ancestor.prior.sha256(Path(__file__).read_bytes()),
        sourcePins=pins,inputSHA256=frozen.FIVE_INPUTS,ancestorBridge=old_bridge,
        interfaceBridge=bridge,fixedInterfaceRows=interface_rows,extraGenericParameter=extra_parameter,
        cold=result,fixedMethodProjection=dict(source=projection,cold=projected_result),
        firstMissing=runtime_contract(result,projected_result,projection),
        caseGroups=['unprojected continuous fresh-interface successor','same successor with only immutable method-index projection'],
        budget=dict(caseGroups=2 if projected_result else 1,maxCaseGroups=4,instructionsPerInvocation=LIMIT,newWindowCount=len(DIRECT_WINDOWS),maxNewWindows=12),
        completeColdComparer=False,completeClassInitialization=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False,sameProcessReplay=False,
        scope='Two distinct captured epochs are explicitly rebound into controlled storage. Opaque references have no provided pointee bytes. No natural whole graph, SDK Config timing or live execution is inferred.')
    args.output.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    verify_delivery(report,previous)
    selected=projected_result or result;last=selected['native'][-1]
    print(json.dumps(dict(unprojectedFailure=result['native'][-1]['failure'],failure=last['failure'],error=last['error'],reads=last['uncapturedReads'],
        newWindowRequests=[{k:v for k,v in row.items() if k!='instructions'} for row in selected['extension']['windowRequests']],completeAvatarHandler=False)))


def checked_input(incoming):
    if incoming['status']!='completed' or incoming['sourceSHA256']!=SAMPLER_SHA or incoming['sourceObservationSHA256']!=MONITOR_SHA:
        raise ValueError('Interface epoch/source/status differs')
    if incoming['registryRoot'] is not None or incoming['registryAttempted'] or incoming['budget']['reads']!=8 or incoming['budget']['requestedBytes']!=584:
        raise ValueError('Exact interface read budget/zero-compact registry condition differs')
    rows={row['name']:row for row in incoming['records']};data={}
    if len(rows)!=8:raise ValueError('Interface records are not unique/exact')
    for name,row in rows.items():
        raw=bytes.fromhex(row['bytesHex'])
        if row['error'] is not None or len(raw)!=row['requested'] or row['bytesRead']!=row['requested']:
            raise ValueError('Failed/short captured interface span')
        data[name]=raw
    cache=int.from_bytes(data['interface.classCache.cell'],'little')
    if rows['interface.classCache.cell']['address']!=incoming['mainModule']['base']+0x5bc8f18 or incoming['cacheRoot']!=cache:
        raise ValueError('Fresh cache root chain differs')
    for index in (1169,1249):
        prefix='interface.class'+str(index);head=data[prefix+'.header'];pointer=int.from_bytes(data[prefix+'.entry'],'little')
        if len(head)!=208 or not pointer or rows[prefix+'.entry']['address']!=cache+index*8 or rows[prefix+'.header']['address']!=pointer:
            raise ValueError('Exact interface cache/header chain differs')
        if struct.unpack_from('<Q',head,0x10)[0]!=index or head[0x1a]!=18:
            raise ValueError('Exact interface id/kind differs')
        if any(struct.unpack_from('<I',head,offset)[0] for offset in (0xa8,0xb0)):
            raise ValueError('Captured zero compact references differ')
        if struct.unpack_from('<Q',head,0x68)[0]!=0:raise ValueError('Ordinary interface definition has an unexpected GenericClass pointer')
        if rows[prefix+'.metadata']['address']!=struct.unpack_from('<Q',head,0x58)[0] or len(data[prefix+'.metadata'])!=70:
            raise ValueError('Interface metadata pointer/width differs')
    return rows,data


def bind_interfaces(incoming,old,b,m,pe,base):
    rows,data=checked_input(incoming)
    header=pe.offset(base.HEADER_VA);table=(base.number(b,header+0x64)+0xa7932555)&0xffffffff
    meta=old['reads']['cold.class1239.metadata']['address']-1239*70
    registry=int.from_bytes(old['data']['cold.registry.base'],'little');cache=int.from_bytes(old['data']['cold.classCache.cell'],'little')
    pointers={};compacts={};classes=[]
    for ordinal,index in enumerate((1169,1249)):
        prefix='interface.class'+str(index);head=bytearray(data[prefix+'.header']);metadata=data[prefix+'.metadata']
        file=base.BODY+table+index*70
        if metadata!=m[file:file+70]:raise ValueError('Interface metadata does not match its exact fixed row')
        model=registry+0xe0000000+ordinal*4096;changes=[]
        for offset in (0,0x30,0x38,0x40,0x48,0x50,0x60,0x68,0x70,0x78,0x80,0x88,0x90,0x98):
            value=struct.unpack_from('<Q',head,offset)[0]
            if value:
                if value not in pointers:pointers[value]=OPAQUE+len(pointers)*4096
                struct.pack_into('<Q',head,offset,pointers[value]);changes.append(dict(offset=hex(offset),kind='unprovided-absolute',source=hex(value),model=hex(pointers[value])))
        for offset in (0xa4,0xac):
            value=struct.unpack_from('<I',head,offset)[0]
            if value:
                if value not in compacts:compacts[value]=0xdd000000+len(compacts)*4096
                struct.pack_into('<I',head,offset,compacts[value]);changes.append(dict(offset=hex(offset),kind='unprovided-compact',source=hex(value),model=hex(compacts[value])))
        struct.pack_into('<Q',head,0x58,meta+index*70)
        for suffix,address,raw in (('entry',cache+index*8,struct.pack('<Q',model)),('header',model,bytes(head)),('metadata',meta+index*70,metadata)):
            ancestor.add_span(old,'cold.freshInterfaces.class'+str(index)+'.'+suffix,address,raw)
        classes.append(dict(definition=index,freshAddress=hex(rows[prefix+'.header']['address']),modelAddress=hex(model),
            freshHeaderSHA256=ancestor.prior.sha256(data[prefix+'.header']),modelHeaderHex=head.hex(),metadataSHA256=ancestor.prior.sha256(metadata),
            metadataModelAddress=hex(meta+index*70),vtableCount=struct.unpack_from('<H',head,0xbe)[0],
            allocationBytes=((struct.unpack_from('<H',head,0xbe)[0]*16+0xd7)&~15),opaqueFields=changes))
    return dict(inputSHA256=INPUT_SHA,monitorSHA256=MONITOR_SHA,sameProcessReplay=False,freshRegistryObserved=False,
        classes=classes,opaqueAbsolute={hex(value):hex(token) for value,token in pointers.items()},
        opaqueCompact={hex(value):hex(token) for value,token in compacts.items()},
        scope='Only fixed metadata and exact class identity/scalars are bound. Non-null pointees remain unmapped symbolic references; numeric equality with an older address does not bind an object.')


def parameter362(parameter,b,m,pe,base):
    header=pe.offset(base.HEADER_VA);table=(base.number(b,header+0x94)+0xd97e8a5c)&0xffffffff
    file=base.BODY+table+362*14;raw=m[file:file+14]
    if ancestor.prior.sha256(raw)!='6d1df52b90dc9d2aa7c4298132a074cc0c20e357f93a0aa63a6596eb3b938596':raise ValueError('Fixed VAR362 row differs')
    return dict(index=362,width=14,fileOffset=hex(file),address=hex(int(parameter['address'],16)-4*14),
        bytesHex=raw.hex(),sha256=ancestor.prior.sha256(raw),allowedReader='0x1404eda27',allowedOffset=6,allowedWidth=2)


def is_projection_stop(result,snapshot):
    pointer=struct.unpack_from('<Q',snapshot['data']['cold.freshAncestor.class1234.header'],0x98)[0]
    last=result['native'][-1]
    return (last['failure']=='Uncaptured natural-address memory read' and last['error'] is None and
        last['uncapturedReads']==[dict(address=hex(pointer),size=8,site='0x14051e387')])


def fixed_method_indices(snapshot,records):
    """A field projection justified by the exact ordinary MethodInfo producer."""
    label='cold.freshAncestor.class1234.header';head=bytearray(snapshot['data'][label])
    metadata=snapshot['data']['cold.freshAncestor.class1234.metadata']
    start=struct.unpack_from('<i',metadata,12)[0]^0x5abb91bd
    count=(struct.unpack_from('<H',metadata,0x3a)[0]+0xbf1a)&0xffff
    record=next(row for row in records if row['index']==1234)
    if struct.unpack_from('<Q',head,0x68)[0] or struct.unpack_from('<H',head,0xc2)[0]!=8 or count!=8 or start<0:
        raise ValueError('1234 ordinary fixed method-index source differs')
    if record['methodStart']!=start or record['methodCount']!=8 or [m['index'] for m in record['methods']]!=list(range(start,start+8)):
        raise ValueError('Independent fixed metadata method span differs')
    original=struct.unpack_from('<Q',head,0x98)[0]
    if not original:raise ValueError('Captured1234 method-array non-null branch differs')
    array=0x6f0000000;body=0x6f0001000
    struct.pack_into('<Q',head,0x98,array)
    snapshot['data'][label]=bytes(head);snapshot['reads'][label]['sha256']=ancestor.prior.sha256(head)
    ancestor.add_span(snapshot,'cold.fixedMethodProjection.array1234',array,b''.join(struct.pack('<Q',body+i*56) for i in range(8)))
    for i in range(8):ancestor.add_span(snapshot,'cold.fixedMethodProjection.index'+str(i),body+i*56+24,struct.pack('<I',start+i))
    return dict(definition=1234,methodStart=start,count=count,methodNames=[m['name'] for m in record['methods']],
        metadataSHA256=ancestor.prior.sha256(metadata),source='Fixed70B row and independent extract_client72.recover_members; original14051e469/46d/5d6/4c6/4ca/4ce.',
        sourceWindow=dict(entry='0x14051e2c0',end='0x14051e718',sha256=DIRECT_WINDOWS[0x14051e2c0][1]),
        originalArrayOpaque=hex(original),modelArray=hex(array),modelRecords=hex(body),arrayWidth=64,providedRecordFieldOffset=24,providedRecordFieldWidth=4,
        suppliedBytes=96,naturalMethodInfoObserved=False,wholeMethodInfoSupplied=False,
        scope='A separately reported controlled projection of immutable method-definition indices. Only array64 and eight4B fields exist; no other MethodInfo field or natural pointee identity is supplied.')


class ExtensionLayer(ancestor.FreshLayer):
    def __init__(self,engine,snapshot):
        super().__init__(engine,snapshot);self.class_arena=None;self.classes=None;self.newAllocations=[]
        self.methods=MethodPointerStorage(engine,snapshot)

    def wrap(self,key,original):
        inherited=super().wrap(key,original)
        if key==0x1405091d0 and getattr(original,'__name__','')=='class_or_generic_allocate':
            context={name:cell.cell_contents for name,cell in zip(original.__code__.co_freevars,original.__closure__)}
            self.class_arena=context['class_arena'];self.classes=context['class_allocations']
            self.methods.classes=self.classes
        if key not in (0x1405091d0,0x141fdf9c0):return inherited
        def supplied():
            e=self.e;caller=e.read(e.reg('rsp'))
            if key==0x1405091d0 and caller==0x14051e35b:return self.methods.allocate()
            if key==0x141fdf9c0 and caller==0x14051e36d:return self.methods.clear()
            if key==0x1405091d0 and caller==0x140517a1d and e.reg('rdx')==208:
                expected=self.snapshot['reads']['cold.freshInterfaces.class1249.header']['address']
                owner=int.from_bytes(self.snapshot['data']['cold.registry.cell'],'little')
                if e.reg('rcx')!=owner or e.reg('r15')!=expected:raise ValueError('Outside exact1249/208B native constructor allocation')
                pointer=self.class_arena+len(self.classes)*4096;e.uc.mem_write(pointer,b'\xcc'*208);e.write(pointer+208,ancestor.CANARY)
                self.classes[pointer]=208;self.newAllocations.append(dict(pointer=hex(pointer),width=208,caller=hex(caller),definition=1249))
                return pointer
            return inherited()
        return supplied


class MethodPointerStorage:
    """Only the observed 1234 method-pointer-array allocation and clearing."""
    def __init__(self,engine,snapshot):
        self.e=engine;self.snapshot=snapshot;self.classes={};self.allocations={};self.events=[]

    def allocate(self):
        e=self.e;caller=e.read(e.reg('rsp'));target=e.reg('r14');source=e.reg('rdi')
        template=self.snapshot['reads']['cold.freshAncestor.class1234.header']['address']
        owner=int.from_bytes(self.snapshot['data']['cold.registry.cell'],'little')
        if caller!=0x14051e35b or e.reg('rcx')!=owner or e.reg('rdx')!=64 or e.reg('r15')!=64 or source!=template:
            raise ValueError('Outside exact1234 eight-method pointer allocation')
        if self.classes.get(target)!=400 or e.read(e.read(target+0x68),'I')!=1234 or e.read(source+0xc2,'H')!=8:
            raise ValueError('Method array does not belong to the owned1234 class')
        pointer=e.alloc(72);e.uc.mem_write(pointer,b'\xcc'*64);e.write(pointer+64,ancestor.CANARY)
        self.allocations[pointer]=64;self.events.append(dict(action='allocate',caller=hex(caller),pointer=hex(pointer),width=64,definition=1234))
        return pointer

    def clear(self):
        e=self.e;pointer=e.reg('rcx');width=e.reg('r8')
        if e.read(e.reg('rsp'))!=0x14051e36d or e.reg('edx')!=0 or self.allocations.get(pointer)!=width or width!=64:
            raise ValueError('Outside exact1234 method-pointer zeroing')
        if e.read(pointer+64)!=ancestor.CANARY:raise ValueError('Method-pointer allocation tail changed')
        e.uc.mem_write(pointer,bytes(64));self.events.append(dict(action='clear',caller='0x14051e36d',pointer=hex(pointer),width=64))
        return pointer


class CpuAdapter:
    def __init__(self,uc,engine,state,extra):self.uc=uc;self.e=engine;self.state=state;self.extra=extra
    def __getattr__(self,name):return getattr(self.uc,name)
    def emu_start(self,*args,**kwargs):
        requested=kwargs.get('count',0);kwargs['count']=min(requested,LIMIT) if requested else LIMIT
        before=self.e.steps
        try:return self.uc.emu_start(*args,**kwargs)
        finally:
            steps=self.e.steps-before
            self.state['invocations'].append(dict(entry=hex(args[0]),requestedLimit=requested,enforcedLimit=kwargs['count'],steps=steps))
            if steps>=LIMIT and not self.e.failure:self.e.failure='Instruction budget exhausted (2000000)'
    def hook_add(self,kind,callback,*args,**kwargs):
        if kind==self.e.U.UC_HOOK_MEM_READ and getattr(callback,'__name__','')=='read_guard':
            old_callback=callback;e=self.e;extra=self.extra;state=self.state
            def extended(uc,access,pointer,width,value,data):
                if 0x6f0000000<=pointer<0x6f0002000:
                    state['methodProjectionReads'].append(dict(address=hex(pointer),width=width,reader=hex(e.reg('rip'))))
                if int(extra['address'],16)<=pointer and pointer+width<=int(extra['address'],16)+14:
                    if pointer!=int(extra['address'],16)+6 or width!=2 or e.reg('rip')!=0x1404eda27:
                        e.failure='Unreviewed VAR362 field reader';uc.emu_stop();return
                    state['parameter362Reads'].append(dict(address=hex(pointer),width=width,reader=hex(e.reg('rip'))));return
                return old_callback(uc,access,pointer,width,value,data)
            callback=extended
        return self.uc.hook_add(kind,callback,*args,**kwargs)


def continue_native(b,m,s,records,snapshot,parameter,extra,previous,bridge,libraries,output):
    original_engine=frozen.base.Offline72;original_layer=ancestor.FreshLayer
    known={int(address,16):row['sha256'] for address,row in previous['cold']['functions'].items()}
    for address,row in previous['cold']['functions'].items():
        start=int(address,16);end=int(row['end'],16);pe=frozen.base.PE(b)
        if ancestor.prior.sha256(b[pe.offset(start):pe.offset(start)+end-start])!=row['sha256']:raise ValueError('Frozen precise prefix window differs')
    if len(DIRECT_WINDOWS)>12:raise ValueError('Direct successor window budget exceeded')
    state=dict(invocations=[],windowRequests=[],parameter362Reads=[],methodProjectionReads=[],opaqueStops=[],methodInflationCalls=[]);engines=[]
    from capstone import Cs,CS_ARCH_X86,CS_MODE_64,CS_OP_REG,CS_OP_MEM,CS_OP_IMM
    md=Cs(CS_ARCH_X86,CS_MODE_64);md.detail=True
    class Offline(original_engine):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,**kwargs);self.uc=CpuAdapter(self.uc,self,state,extra);engines.append(self)
        def prepare(self):
            super().prepare();self.uc.mem_write(int(extra['address'],16),bytes.fromhex(extra['bytesHex']))
        def _code(self,uc,address,size,data):
            if self.unwind_ranges and address in self.unwind_ranges and address not in known:
                end=self.unwind_ranges[address];raw=b[self.pe.offset(address):self.pe.offset(address)+end-address]
                actual=ancestor.prior.sha256(raw)
                if DIRECT_WINDOWS.get(address)!=(end,actual):
                    state['windowRequests'].append(dict(entry=hex(address),end=hex(end),width=len(raw),sha256=actual,
                        instructions=[dict(address=hex(i.address),instruction=i.mnemonic+' '+i.op_str) for i in md.disasm(raw,address)]))
                    self.failure='Unreviewed direct successor window';uc.emu_stop();return
            if address==0x1404eea50:
                pointer=self.reg('rcx');raw=bytes(self.uc.mem_read(pointer,24))
                state['methodInflationCalls'].append(dict(caller=hex(self.read(self.reg('rsp'))),descriptor=hex(pointer),bytesHex=raw.hex(),
                    methodIndex=struct.unpack_from('<I',raw)[0],classInstance=hex(struct.unpack_from('<Q',raw,8)[0]),
                    methodInstance=hex(struct.unpack_from('<Q',raw,16)[0]),copyAndInternByte=self.reg('edx')&255,
                    copyAndInternRequested=bool(self.reg('edx')&255)))
            if opaque_use(self,address,size,md,state,CS_OP_REG,CS_OP_MEM,CS_OP_IMM):uc.emu_stop();return
            return super()._code(uc,address,size,data)
    frozen.base.Offline72=Offline;ancestor.FreshLayer=ExtensionLayer
    try:
        result=ancestor.continue_cold(frozen,b,m,s,records,snapshot,parameter,libraries,output,True)
        e=engines[-1];layer=e.extra_layer
        # Captured metadata and storage reports are retained by the frozen wrapper.
        result['extension']=state
        result['extension']['reviewedWindows']=[dict(entry=hex(a),end=hex(z),sha256=d) for a,(z,d) in DIRECT_WINDOWS.items()]
        result['extension']['class208Allocations']=[dict(row,tail=hex(e.read(int(row['pointer'],16)+row['width']))) for row in layer.newAllocations]
        result['extension']['methodPointerStorage']=dict(events=layer.methods.events,allocations=[dict(pointer=hex(p),width=n,bytesHex=bytes(e.uc.mem_read(p,n)).hex(),tail=hex(e.read(p+n))) for p,n in layer.methods.allocations.items()])
        result['extension']['stopRegisters']={n:hex(e.reg(n)) for n in ('rip','rsp','rax','rbx','rcx','rdx','rsi','rdi','r8','r9','r10','r11','r12','r13','r14','r15')}
        result['extension']['modelBoundaries']='Original400B constructors unchanged. Exact template1249 produces208B. The observed1234 eight-method pointer array has64 owned bytes. Both boundaries carry independently nonzero tails. No new OS model.'
        return result
    finally:
        frozen.base.Offline72=original_engine;ancestor.FreshLayer=original_layer
        for engine in engines:engine.close()


def runtime_contract(unprojected,projected,projection):
    return dict(unprojected=dict(failure=unprojected['native'][-1]['failure'],missing=unprojected['native'][-1]['uncapturedReads'],
            meaning='Observed1234 header+98 pointee is not captured. Its immutable method indices can be projected from the fixed ordinary producer, in a separate controlled case.'),
        selected='controlled immutable-method-index projection',failure=projected['native'][-1]['failure'] if projected else None,
        firstRead=dict(moduleRVA='0x5bc7290',width=8,reader='0x1404eeb80',field='method-cache mask',observed=False),
        minimumUnblockingRoots=[dict(moduleRVA=hex(address-0x140000000),width=8,reader=hex(reader),field=field)
            for address,reader,field in ((0x145bc7290,0x1404eeb80,'mask'),(0x145bc72b0,0x1404eeb87,'blocks'),(0x145bc72b8,0x1404eeb8e,'probeLimit'))],
        minimumRootReadCount=3,minimumRootBytes=24,
        consumerWindow=dict(entry='0x1404eea50',end='0x1404efc2e',width=4574,sha256=DIRECT_WINDOWS[0x1404eea50][1]),
        currentArguments=projected['extension']['methodInflationCalls'] if projected else [],
        fixedMethodIndices=list(range(projection['methodStart'],projection['methodStart']+8)) if projection else [],
        conditionalLookup=dict(source='1404eeba9..1404eec95 in the same pinned consumer window',
            slot='(current64BitHash & mask)',block='blocks + ((slot >> 6) << 5)',bit='slot & 63',
            blockFields=[dict(offset=0,width=8,meaning='packed entries pointer'),dict(offset=8,width=8,meaning='occupied bits'),dict(offset=16,width=8,meaning='tombstone bits')],
            blockMinimumBytes=24,occupiedEntry='packed entries + popcount(occupiedBits & ((1<<bit)-1))*16',
            entryFields=[dict(offset=0,width=8,meaning='method descriptor pointer'),dict(offset=8,width=8,meaning='returned MethodInfo pointer')],
            descriptorMinimumBytes=24,descriptorFields='methodIndex4B at+0; class/method instance pointers8B at+8/+16',
            nextSlot='(previousSlot + previousProbeCount + 1) & mask',
            zeroBranch='A clear occupied bit consults the tombstone bit and native probeLimit; a miss executes new materialization/interning, never a successful empty-method stub.',
            unknown='Context equality callee1404f0640 and miss-side producers14052d590/140526fb0 are direct future branches, not reviewed/executed in this two-window card. Returned MethodInfo bytes and later vtable/default consumers remain unavailable.'),
        samplingReady=False,samplingStarted=False,fullLookupClosureOrBudgetClaimed=False,
        releaseCondition='A separately approved bounded sampler must bind a single fresh epoch/module and these roots, then capture only the selected-key lookup spans proven by the original consumer. Current modeled class-instance identity/hash is not a live pointer or universal bucket. A full hit/miss closure and its total budget must be reviewed before any new sampling.',
        noDefaultCacheState='No natural mask/blocks/probeLimit, empty-cache assumption, occupied entry or MethodInfo object has been supplied.')


def verify_delivery(report,previous):
    """A report is accepted only at the two exact independently retained stops."""
    cases=(report['cold'],report['fixedMethodProjection']['cold'])
    if cases[1] is None:raise ValueError('Controlled immutable-index case did not execute')
    expected=dict(address='0x500010ea5c0',size=8,site='0x14051e387')
    first=cases[0]['native'][-1]
    if first['failure']!='Uncaptured natural-address memory read' or first['error'] is not None or first['uncapturedReads']!=[expected]:raise ValueError('Unprojected method-array first gap differs')
    last=cases[1]['native'][-1]
    missing=[row for row in cases[1]['runtimeGlobalReads'] if not row['supplied']]
    if last['failure']!='Unbound runtime global cell read' or last['error'] is not None or last['uncapturedReads'] or missing!=[dict(address='0x145bc7290',width=8,reader='0x1404eeb80',supplied=False)]:
        raise ValueError('Projected real method-cache first gap differs')
    for cold in cases:
        if len(cold['native'])!=7 or len(cold['extension']['invocations'])!=7:raise ValueError('Frozen producer prefix was not completed')
        for current,old in zip(cold['native'][:6],previous['cold']['native'][:6]):
            if any(current[key]!=old[key] for key in ('name','failure','error','regs')) or current['trace']['pathSHA256']!=old['trace']['pathSHA256']:
                raise ValueError('Frozen independent producer assertion changed')
        extension=cold['extension']
        if extension['windowRequests'] or extension['opaqueStops'] or cold['models']['callbackFailures']:raise ValueError('Unexpected new window, opaque use or model failure')
        if any(row['enforcedLimit']!=LIMIT or row['steps']>=LIMIT for row in extension['invocations']):raise ValueError('Native instruction budget differs')
        if [row['width'] for row in cold['newClasses']]!=[400,400,208] or len(cold['genericRecords'])!=3:raise ValueError('Nested native class construction differs')
        for record,klass in zip(cold['genericRecords'],cold['newClasses']):
            if struct.unpack_from('<Q',bytes.fromhex(record['bytesHex']),24)[0]!=int(klass['pointer'],16) or struct.unpack_from('<Q',bytes.fromhex(klass['headerHex']),0x68)[0]!=int(record['pointer'],16):
                raise ValueError('Original two-way GenericClass publication differs')
        if len(extension['class208Allocations'])!=1 or extension['class208Allocations'][0]['tail']!='0xfedcba9876543210':raise ValueError('208B native constructor tail differs')
        arrays=extension['methodPointerStorage']['allocations']
        if len(arrays)!=1 or arrays[0]['width']!=64 or arrays[0]['bytesHex']!='00'*64 or arrays[0]['tail']!='0xfedcba9876543210':raise ValueError('Owned64B method-pointer storage differs')
        interfaces=cold['interfaceStorage']['allocations']
        expected_interfaces=struct.pack('<QQ',int(report['interfaceBridge']['classes'][0]['modelAddress'],16),int(cold['newClasses'][2]['pointer'],16)).hex()
        if len(interfaces)!=1 or interfaces[0]['width']!=16 or interfaces[0]['bytesHex']!=expected_interfaces or interfaces[0]['tail']!='0xfedcba9876543210':raise ValueError('Original interface-array writeback differs')
    calls=cases[1]['extension']['methodInflationCalls']
    if len(calls)!=1 or calls[0]['methodIndex']!=10287 or calls[0]['classInstance']!='0x142377940' or calls[0]['methodInstance']!='0x0' or calls[0]['caller']!='0x14051e3b2' or not calls[0]['copyAndInternRequested']:
        raise ValueError('Original native method-inflation arguments differ')


def opaque_use(e,address,size,md,state,reg_kind,mem_kind,imm_kind):
    # Opaque reference bytes may be copied, but their missing identities/pointees
    # cannot silently become old-epoch objects or drive an identity branch.
    instruction=next(md.disasm(bytes(e.uc.mem_read(address,size)),address),None)
    if instruction is None:return False
    if instruction.mnemonic.startswith('nop'):return False
    tokens=[];other=[];memory_operand=False
    for operand in instruction.operands:
        if operand.type==mem_kind:
            memory_operand=True
            values=[]
            for register in (operand.mem.base,operand.mem.index):
                if register:
                    try:values.append(e.reg(instruction.reg_name(register)))
                    except AttributeError:pass
            if any(OPAQUE<=value<OPAQUE+0x100000 for value in values):
                state['opaqueStops'].append(dict(reader=hex(address),instruction=instruction.mnemonic+' '+instruction.op_str,width=operand.size))
                e.failure='Unprovided opaque interface pointer dereference';return True
        elif operand.type==reg_kind and operand.access&1:
            name=instruction.reg_name(operand.reg)
            try:value=e.reg(name)
            except AttributeError:continue
            if OPAQUE<=value<OPAQUE+0x100000:tokens.append(value)
            else:other.append(value)
        elif operand.type==imm_kind:other.append(operand.imm)
    if tokens and instruction.mnemonic not in ('mov','movabs','push','pop'):
        if instruction.mnemonic in ('cmp','test') and not memory_operand and not other:return False
        if instruction.mnemonic=='cmp' and not memory_operand and all(value==0 for value in other):return False
        state['opaqueStops'].append(dict(reader=hex(address),instruction=instruction.mnemonic+' '+instruction.op_str))
        e.failure='Unbound cross-epoch opaque pointer operation';return True
    return False


if __name__=='__main__':main()
