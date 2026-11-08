"""Offline continuation of the frozen cold factory with a static fresh parent."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
RAW_ROOT = Path('local/migration72/d02-resume-20261108/r1-parent')
FROZEN_TOOL = 'tools/client72_avatar_handler_closure.py'
FROZEN_SHA = '472bb8aa0eb419a8bd45d45bbd41e3c4e881345d40ed779c00c01f753be9cdad'
FRESH = 'local/migration72/d02-active/main/entry-context-tool/live-resume-20261108-1.json'
FRESH_SHA = 'c790c70f1e5882666d556ad4ba54250e8cec3e66c4874667576209b05097d152'
MONITOR = 'local/migration72/d02-active/plain/run-resume-20261108-1.json'
MONITOR_SHA = 'a48b46ab9206ec8809efc67601d406df067634d44a4230c7aad1497a5662daa2'
PROBE = 'local/migration72/d02-resume-20261108/parent-probe3/probe3.private.json'
PROBE_SHA = '4f9c5b836240d141b7df9ec182280bddd5176173585a4364c1b7a69992b8c1d4'
CANARY = 0xfedcba9876543210


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=REPO/'local/migration72/python')
    args = parser.parse_args()
    if not args.output.resolve().is_relative_to((REPO/RAW_ROOT).resolve()):
        parser.error('Output must stay in the dedicated r1-parent directory')
    from . import client72_avatar_handler_closure as frozen
    pins = verify_sources()
    b,m,s,*_ = [frozen.base.read_verified(args.client/name,digest) for name,digest in frozen.FIVE_INPUTS.items()]
    old = frozen.audit_snapshot(b,m)
    fresh = json.loads(read_pinned(FRESH,FRESH_SHA))
    read_pinned(MONITOR,MONITOR_SHA)
    read_pinned(PROBE,PROBE_SHA)
    pe = frozen.base.PE(b)
    relocation = relocate_parent(fresh,old,b,s,pe,frozen.base)
    parameter = generic_parameter(old,b,m,pe,frozen.base)
    frozen.base.load_emulator(args.python_libs)
    records,_ = frozen.base.extract(b,m,startup=s,structures=True)
    cold = continue_cold(frozen,b,m,s,records,old,parameter,args.python_libs,args.output)
    vector = vector_controls(frozen,b,records,args.python_libs)
    contract = sampling_contract(records,b,m,s,pe,frozen.base)
    report = dict(task='F-03d3f4d2-parent-continuation',inputSHA256=frozen.FIVE_INPUTS,
        sourcePins=pins,freshSHA256=FRESH_SHA,monitorSHA256=MONITOR_SHA,priorProbeSHA256=PROBE_SHA,
        relocation=relocation,genericParameter=parameter,cold=cold,vectorControls=vector,
        nextSamplingContract=contract,sourceSHA256=sha256(Path(__file__).read_bytes()),
        evidenceScope='A static kind21 descriptor from a fresh process is rebound into the frozen old 99-span model; this is not a same-process natural replay.',
        completeColdComparer=False,defaultComparerWriteExecuted=False,completeAvatarHandler=False,
        targetProtocolReady=False,d02Complete=False,naturalNativeConfigTypedIdentityVerified=False,
        naturalNativeConfigPredicateAtConsumptionVerified=False,
        osModelScope='Single modeled thread and the two already declared CriticalSections only. New acquisition is restricted to return 14051fba3 while the class lock is held; no contention, scheduling, global lock-order or deadlock proof.',
        osModelSources=['https://learn.microsoft.com/en-us/windows/win32/sync/using-critical-section-objects',
                        'https://learn.microsoft.com/en-us/windows/win32/sync/critical-section-objects'])
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    last=cold['native'][-1]
    print(json.dumps(dict(failure=last['failure'],error=last['error'],reads=last['uncapturedReads'],
                          vectorCases=len(vector),completeColdComparer=False)))


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def read_pinned(name,digest):
    raw=(REPO/name).read_bytes()
    if sha256(raw)!=digest:
        raise ValueError('Pinned source changed: '+str(name))
    return raw


def verify_sources():
    pins={FROZEN_TOOL:FROZEN_SHA}
    previous=json.loads((REPO/'local/migration72/d02-active/avatar/handler-closure/first.json').read_text('utf8'))
    if sha256((REPO/'local/migration72/d02-active/avatar/handler-closure/first.json').read_bytes())!='58c2c4486873fc74d65d825f74aaa715bf56fce8bd77c8e03208500f090d4f67':
        raise ValueError('Frozen parent report changed')
    pins.update({'tools/'+name:digest for name,digest in previous['dependencies'].items()})
    for name,digest in pins.items():read_pinned(name,digest)
    return pins


def relocate_parent(fresh,old,b,s,pe,base):
    if fresh['status']!='completed' or fresh['parentDescriptorStatus']!='read' or fresh['verifiedWindows']!=21 or fresh['sourceObservationSHA256']!=MONITOR_SHA:
        raise ValueError('Fresh sampling contract is incomplete')
    rows={row['name']:row for row in fresh['reads']}
    selected={}
    for name in ('parent.class1239.header','parent.registry.base','parent.descriptor'):
        row=rows[name];raw=bytes.fromhex(row['bytesHex'])
        if row['error'] is not None or len(raw)!=row['requested'] or sha256(raw)!=row['sha256']:
            raise ValueError('Fresh descriptor-chain span changed')
        selected[name]=raw
    header=selected['parent.class1239.header'];previous=old['data']['cold.class1239.header']
    if any(header[off:off+width]!=previous[off:off+width] for off,width in ((0x10,16),(0xb0,4),(0xbe,2))):
        raise ValueError('Fresh and frozen definition identities/sizes differ')
    descriptor=selected['parent.descriptor']
    if len(descriptor)!=16 or descriptor[4:8]!=bytes(4) or descriptor[12:16]!=bytes(4) or (int.from_bytes(descriptor[8:12],'little')>>16)&255!=21:
        raise ValueError('Only the observed static kind21 parent descriptor can be rebound')
    index=int.from_bytes(descriptor[:4],'little');h=pe.offset(base.HEADER_VA)
    start=(base.number(b,h+0xd0)+0xe1492096)&0xffffffff
    size=(base.number(b,h+0x44)+0xe42e197f)&0xffffffff
    if index>=size//8:raise ValueError('Static generic index is outside the fixed registration')
    instance,definition=struct.unpack_from('<II',s,start+index*8)
    if (index,instance,definition)!=(405416,54605,1234):raise ValueError('Static parent registration identity differs')
    jump=0x1404ede18
    if jump+struct.unpack_from('<i',b,pe.offset(jump+8))[0]!=0x1404eda6b:
        raise ValueError('kind21 original jump-table branch changed')
    new_address=int.from_bytes(selected['parent.registry.base'],'little')+struct.unpack_from('<I',header,0xb0)[0]+0x10
    if rows['parent.descriptor']['address']!=new_address:raise ValueError('Fresh descriptor chain address differs')
    old_address=struct.unpack_from('<Q',old['data']['cold.registry.base'])[0]+struct.unpack_from('<I',previous,0xb0)[0]+0x10
    label='cold.freshStaticParentDescriptor'
    old['reads'][label]=dict(address=old_address,length=16,sha256=sha256(descriptor),error=None)
    old['data'][label]=descriptor
    return dict(kind=21,staticGenericIndex=index,instance=instance,definition=definition,
                descriptorHex=descriptor.hex(),freshAddress=hex(new_address),modelAddress=hex(old_address),
                sameProcessReplay=False)


def generic_parameter(old,b,m,pe,base):
    h=pe.offset(base.HEADER_VA)
    actual=old['reads']['cold.class1239.metadata']['address']-1239*70
    encoded=(base.number(b,h+0x64)+0xa7932555)&0xffffffff
    body=actual-encoded
    offset=(base.number(b,h+0x94)+0xd97e8a5c)&0xffffffff
    file=base.BODY+offset+366*14;raw=m[file:file+14]
    if len(raw)!=14 or sha256(raw)!='a51d4766320e925c4dd7ee3105eb77f25a940b851075d483255b89cde829a113':
        raise ValueError('Fixed generic parameter row differs')
    return dict(index=366,fileOffset=hex(file),address=hex(body+offset+366*14),width=14,
                bytesHex=raw.hex(),sha256=sha256(raw),source='Fixed PE header+94 and metadata row; body binding verified by frozen audit_snapshot across 13 class metadata records.')


class VectorStorage:
    """Exact allocator/copy/free boundaries reached by the original vector code."""
    def __init__(self,engine):
        self.e=engine;self.live={};self.events=[];self.allocated=[]

    def allocate(self):
        e=self.e;caller=e.read(e.reg('rsp'));width=e.reg('rcx')
        if caller!=0x1404e561b or width not in (8,16):raise ValueError('Outside bounded vector growth allocation')
        pointer=e.alloc(width+8);e.uc.mem_write(pointer,b'\xcc'*width);e.write(pointer+width,CANARY)
        self.live[pointer]=width;self.allocated.append((pointer,width))
        self.events.append(dict(action='allocate',caller=hex(caller),pointer=hex(pointer),width=width))
        return pointer

    def copy(self):
        e=self.e;caller=e.read(e.reg('rsp'));dst,src,width=(e.reg(n) for n in ('rcx','rdx','r8'))
        if caller!=0x1404e567f or width not in (0,8):raise ValueError('Outside bounded vector copy')
        if not any(p<=dst and dst+width<=p+n for p,n in self.live.items()):raise ValueError('Vector copy destination outside owned bytes')
        if width and not any(p<=src and src+width<=p+n for p,n in self.live.items()):raise ValueError('Vector copy source outside owned bytes')
        if width:e.uc.mem_write(dst,bytes(e.uc.mem_read(src,width)))
        self.events.append(dict(action='copy',caller=hex(caller),destination=hex(dst),source=hex(src),width=width))
        return dst

    def free(self):
        e=self.e;caller=e.read(e.reg('rsp'));pointer=e.reg('rcx');width=e.reg('rdx')
        if caller not in (0x1404e56b4,0x1404e0de7) or self.live.get(pointer)!=width:
            raise ValueError('Outside exact owned vector free')
        if e.read(pointer+width)!=CANARY:raise ValueError('Vector allocation tail changed')
        del self.live[pointer]
        self.events.append(dict(action='free',caller=hex(caller),pointer=hex(pointer),width=width))
        return 0


class ModelLayer:
    def __init__(self,engine,snapshot):
        self.e=engine;self.snapshot=snapshot;self.vector=VectorStorage(engine)
        self.pools=[];self.locks=[];self.callbackFailures=[]

    def wrap(self,key,original):
        if key not in (0x1405091d0,0x141a2e150,0x141fdf310,0x141cbfe48) and getattr(original,'__name__','')!='enter_critical':
            return original
        def supplied():
            e=self.e;caller=e.read(e.reg('rsp'))
            if key==0x1405091d0 and caller==0x1404eda7c:
                if e.reg('rdx')!=16 or e.reg('rcx')!=struct.unpack_from('<Q',self.snapshot['data']['cold.registry.cell'])[0]:
                    raise ValueError('Outside exact parent-descriptor pool16 allocation')
                pointer=e.alloc(16);e.uc.mem_write(pointer,b'\xcc'*16)
                self.pools.append(dict(pointer=hex(pointer),width=16,caller=hex(caller)))
                return pointer
            if key==0x141a2e150 and caller==0x1404e561b:return self.vector.allocate()
            if key==0x141fdf310 and caller==0x1404e567f:return self.vector.copy()
            if key==0x141cbfe48 and e.reg('rcx') in self.vector.live:return self.vector.free()
            if getattr(original,'__name__','')=='enter_critical':
                context={name:cell.cell_contents for name,cell in zip(original.__code__.co_freevars,original.__closure__)}
                held=context['critical_stack'];pointer=e.reg('rcx')
                if caller==0x14051fba3 and pointer==context['critical'] and held and held[-1]==context['class_lock']:
                    if len(held)>=8:raise ValueError('Declared critical depth exceeded')
                    held.append(pointer);context['critical_events'].append(dict(action='enter',pointer=hex(pointer),depth=len(held)))
                    self.locks.append(dict(caller=hex(caller),pointer=hex(pointer),depth=len(held)))
                    return 0
            try:return original()
            except Exception as error:
                self.callbackFailures.append(dict(entry=hex(key),caller=hex(caller),exception=type(error).__name__,message=str(error)))
                raise
        return supplied


class InterceptModels(dict):
    def __init__(self,values,layer):
        super().__init__(values);self.layer=layer

    def __setitem__(self,key,value):
        super().__setitem__(key,self.layer.wrap(key,value))


def continue_cold(frozen,b,m,s,records,snapshot,parameter,libraries,output):
    original=frozen.base.Offline72;layers=[];metadata_reads=[]
    address=int(parameter['address'],16);raw=bytes.fromhex(parameter['bytesHex'])
    class Offline(original):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,**kwargs)
            layer=ModelLayer(self,snapshot);layers.append(layer);self.models=InterceptModels(self.models,layer)
            page=address&~4095
            self.uc.mem_map(page,4096);self.uc.mem_write(address,raw)
            def guard(uc,access,pointer,width,value,data):
                if pointer&~4095==page:
                    if not address<=pointer or pointer+width>address+len(raw):
                        self.failure='Unprovided genericParam metadata bytes';uc.emu_stop()
                    else:metadata_reads.append(dict(address=hex(pointer),width=width,reader=hex(self.reg('rip'))))
            self.hooks.append(self.uc.hook_add(self.U.UC_HOOK_MEM_READ,guard))
    frozen.base.Offline72=Offline
    result={}
    try:
        try:
            frozen.run_cold(b,m,s,records,snapshot,libraries,output.with_suffix('.unexpected-original-stop.json'))
            raise ValueError('Frozen previous missing-descriptor assertion unexpectedly remained valid')
        except AssertionError as error:
            trace=error.__traceback__;frame=None
            while trace:
                if trace.tb_frame.f_code.co_name=='run_cold':frame=trace.tb_frame
                trace=trace.tb_next
            if frame is None:raise
            local=frame.f_locals;e=local['e']
            result=dict(native=local['results'],functions=local['functions'],
                criticalEvents=local['critical_events'],criticalAtStop=[hex(x) for x in local['critical_stack']],
                sharedEvents=local['shared_events'],sharedHeldAtStop=local['shared_depth'] is not None,
                oldExpectedMissingDescriptorAssertionObsolete=True,
                genericRecords=[dict(pointer=hex(p),bytesHex=bytes(e.uc.mem_read(p,32)).hex()) for p in local['generic_allocated']],
                newClasses=[dict(pointer=hex(p),width=n,headerHex=bytes(e.uc.mem_read(p,0xd0)).hex()) for p,n in local['class_allocations'].items()])
            layer=layers[-1]
            result['models']=dict(pool16=[dict(row,bytesHex=bytes(e.uc.mem_read(int(row['pointer'],16),16)).hex()) for row in layer.pools],vectorEvents=layer.vector.events,
                vectorsAtStop={hex(p):n for p,n in layer.vector.live.items()},
                vectorBytes=[dict(pointer=hex(p),width=n,bytesHex=bytes(e.uc.mem_read(p,n)).hex(),tail=hex(e.read(p+n))) for p,n in layer.vector.allocated],
                distinctCriticalAcquisitions=layer.locks,callbackFailures=layer.callbackFailures,
                genericParameterReads=metadata_reads)
            table=struct.unpack_from('<Q',snapshot['data']['cold.classCache.cell'])[0]
            last=result['native'][-1]
            expected=dict(address=hex(table+1234*8),size=8,site='0x15a4d6bf1')
            if last['failure']!='Uncaptured natural-address memory read' or last['error'] is not None or last['uncapturedReads']!=[expected]:
                raise ValueError('New cold continuation did not reach the reviewed class1234 first missing read')
            result['firstMissing']=dict(reader='0x15a4d6bf1',width=8,parameter=1234,
                chain='fresh module B+5bc8f18 -> class cache base -> [base+1234*8]',
                modelAddress=expected['address'],naturalAddressReusable=False,unwindExecuted=False)
    finally:
        frozen.base.Offline72=original
    return result


def vector_controls(frozen,b,records,libraries):
    e=frozen.base.Offline72(b,libraries,records,trace_instructions=True)
    e.prepare();e.uc.mem_map(frozen.RETURN&~4095,4096);e.uc.mem_write(frozen.RETURN,b'\x90')
    e.allow_unwind(0x1404e5560)
    storage=VectorStorage(e)
    e.models.update({0x141a2e150:storage.allocate,0x141fdf310:storage.copy,0x141cbfe48:storage.free})
    vector=e.alloc(24);value=e.alloc(8);results=[]
    try:
        for item in (0xfedcba9876543210,0x8000000100000001):
            e.write(value,item);e.write(frozen.STACK+0x100008,frozen.RETURN)
            for name,n in dict(rsp=frozen.STACK+0x100008,rcx=vector,rdx=e.read(vector+8),r8=value).items():
                e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+name.upper()),n)
            e.failure=None;before=len(storage.events)
            e.before_instruction=lambda a:e.uc.emu_stop() if a==frozen.RETURN else None
            e.allowed.append((frozen.RETURN,frozen.RETURN+1))
            e.uc.emu_start(0x1404e5560,frozen.END,timeout=20_000_000,count=100000)
            if e.failure or e.reg('rip')!=frozen.RETURN:raise ValueError(e.failure or 'Vector control did not return')
            first,last,capacity=(e.read(vector+off) for off in (0,8,16))
            results.append(dict(input=hex(item),values=[hex(e.read(p)) for p in range(first,last,8)],
                bytes=last-first,capacity=capacity-first,tail=hex(e.read(capacity)),events=storage.events[before:]))
        return results
    finally:e.before_instruction=None;e.close()


def sampling_contract(records,b,m,s,pe,base):
    lineage=[];index=1234
    while index is not None:
        record=records[index];lineage.append(dict(index=index,name=record['name'],namespace=record['namespace'],parent=record['parent']))
        index=record['parent']['typeDefinition'] if record['parent'] else None
    if [row['index'] for row in lineage]!=[1234,371]:raise ValueError('Fixed metadata parent chain differs')
    h=pe.offset(base.HEADER_VA);offset=(base.number(b,h+0x64)+0xa7932555)&0xffffffff
    metadata=[]
    for index in (1234,371):
        raw=m[base.BODY+offset+index*70:base.BODY+offset+(index+1)*70]
        metadata.append(dict(index=index,fileOffset=hex(base.BODY+offset+index*70),width=70,bytesHex=raw.hex(),sha256=sha256(raw),interfaceCount=raw[0x43]^0x25))
    pair_start=(base.number(b,h+0xd0)+0xe1492096)&0xffffffff
    pair_bytes=(base.number(b,h+0x44)+0xe42e197f)&0xffffffff
    registrations={definition:[i for i,(instance,owner) in enumerate(struct.iter_unpack('<II',s[pair_start:pair_start+pair_bytes])) if instance==6 and owner==definition] for definition in (2147,1255)}
    fields={row['index']:row for row in records[11325]['fields']}
    config_fields=[fields[index] for index in (53821,53824)]
    for field,definition in zip(config_fields,(2147,1255)):
        typ=field['type']
        if typ['typeDefinition']!=definition or typ['instanceIndex']!=6 or [row['kind'] for row in typ['arguments']]!=['int32']:
            raise ValueError('Exact GlobalVars Int32 configuration field type changed')
    windows=[]
    for name,start,end in (('definitionHeaderAndDeclaringDescriptorReader',0x140517a79,0x140517b26),
                           ('distinctCriticalEntry',0x14051fb96,0x14051fba3)):
        raw=b[pe.offset(start):pe.offset(start)+end-start]
        windows.append(dict(name=name,start=hex(start),end=hex(end),width=len(raw),bytesHex=raw.hex(),sha256=sha256(raw)))
    return dict(scope='Requirements for a future mainline sampler only; this tool never opens a process.',
        parentLineage=lineage,metadata=metadata,additionalReaderWindows=windows,
        reusedReaderWindows=['140517980..1405179f7','15a4d6b60..15a4d6c21','140517aa4..140517acc','1404ed9c3..1404ed9d5'],
        classReads=[dict(index=index,cacheEntryBytes=8,headerBytes=208,headerIdentity=dict(data=index,kind=18),
                         metadataPointerOffset='0x58',metadataBytes=70,
                         compactDescriptorOffsets=['0xb0','0xa8'],descriptorBias='0x10',descriptorBytes=16)
                    for index in (1234,371)],
        conditionalRules=['Read each cache entry from the same fresh B+5bc8f18 root; zero/null/failed roots remain distinct.',
            'Only nonzero class pointers permit a 0xd0 header. Verify index/kind and fixed 70-byte metadata before accepting the class binding.',
            'For each nonzero b0/a8 compact offset, reuse the fresh registry chain B+5bc8ad0 -> slot -> base and read only base+offset+10, width16; zero skips, failures remain unknown.',
            'Do not follow a descriptor to guessed extra types, scan members, or call client code. These reads do not prove cold/default completion.'],
        deferredClassArrays=dict(requiredForCurrent1234ContinuationVerified=False,
            sourceFunction='0x14051b7b0',sourceFunctionSHA256='9e9c1da4044e39f809b8a06a63a1f0eef7a38e36b5da6b9a8dbc287ed1ab263f',
            hierarchyReader=['0x14051c030','0x14051c037','0x14051c047','0x14051c04b'],
            interfacesReader=['0x14051b941','0x14051b945','0x14051b94b','0x14051bb3e','0x14051bb42'],
            interfaceCountDecode='fixed 70-byte metadata row[0x43] XOR 0x25',interfaceElements='Il2CppClass pointers, not 16-byte type descriptors',
            reason='This general assignability reader has not been reached on class1234 in the current continuation. The actual 140517980 constructor does not read these arrays. They are not included in the minimum future read budget.'),
        nativeConfigClassHeads=[dict(owner=11325,field=53821,staticOffset='0x60e8',declaredDefinition=2147,instance=6,argument='System.Int32',objectHeaderBytes=40,classHeaderBytes=208),
                                dict(owner=11325,field=53824,staticOffset='0x6100',declaredDefinition=1255,instance=6,argument='System.Int32',objectHeaderBytes=32,classHeaderBytes=208)],
        nativeConfigSourceFields=config_fields,nativeConfigStaticGenericIndices={str(k):v for k,v in registrations.items()},
        nativeConfigRules=['Reuse the current fresh root and object headers; only nonzero object klass pointers permit a 0xd0 class-header read.',
            'Resolve a static kind21 registration index through the fixed startup pair and exact Int32 argument. A generic definition or opaque dynamic representation alone does not verify Int32 identity.',
            'No set/list member scan, comparer dereference or native call; mode/empty headers do not prove the consumption-time predicate.'],
        budget=dict(classClosureAdditionalReads=10,classClosureAdditionalBytes=636,
                    nativeConfigAdditionalReads=2,nativeConfigAdditionalBytes=416,
                    additionalReadCount=12,additionalBytes=1052,
                    totalWithExisting23ReadContract=35,totalBytesWithExisting1041ByteContract=2093,
                    totalWithMainline30Read1714ByteContract=40,totalBytesWithMainline30Read1714ByteContract=2350,
                    maxSingleReadBytes=208),
        includesPoolContract=False,liveExecution=False)


if __name__=='__main__':
    if not __package__:
        import sys
        sys.path.insert(0,str(REPO));__package__='tools'
    main()
