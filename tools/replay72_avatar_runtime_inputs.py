"""Source-bound CN72 warm/native replay of one exact runtime-input collection."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT=Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0,str(ROOT));__package__='tools'
from . import collect72_avatar_runtime_inputs as collector
from . import continue72_avatar_class80_storage as class80

cache=collector.cache
RAW=ROOT/'local/migration72/d02-resume-20261109/r1-runtime-replay'
CAPTURE='local/migration72/d02-resume-20261109/r1-runtime-inputs/live/runtime-live1.private.json'
MONITOR='local/migration72/d02-active/plain/run-resume-20261109-runtime1.json'
REVIEW='local/migration72/d02-resume-20261109/r1-runtime-inputs/live/main-review.private.json'
EXIT='local/migration72/d02-resume-20261109/r1-runtime-inputs/live/post-exit.private.json'
PINS={CAPTURE:'04cbe1ffd685244d4599a384186297fa4fdf19e50c2d7c6864f6b9cb4a3b48ac',
    MONITOR:'46f83efb57862c635f96473d50a3e07a613e415b0401b36d51df1f47f81ee140',
    REVIEW:'2f20e7768d56744207f6c3776b02f66f0f16194404aa32f90bdb05986ddb0c5c',
    EXIT:'5f63126c1e9e03e04f928260770d1c80b1240e311a7dbd51a9a14c7315d74998',
    'tools/collect72_avatar_runtime_inputs.py':'2c9ba14c652823a71771a2fee385de44a75161ad9bba947d263b4164aa39fffa',
    'tools/continue72_avatar_class80_storage.py':'9c19d79f6606b17bfde2d00762d1e8ded232e0bdf82b4047abddb9877b1a6c65',
    'tools/sample_client72_avatar_runtime_inputs.py':'c388f4d555ce019b143d125a9effda5c32da8cab642d10019a7a983af5dc54ad',
    'local/migration72/d02-resume-20261109/r1-runtime-inputs/live/runtime-live1.private.source.py':
        'c388f4d555ce019b143d125a9effda5c32da8cab642d10019a7a983af5dc54ad'}
MAX_HOST_READS,MAX_HOST_BYTES=4096,65536
LIMIT=2_000_000
GETTER=(0x157d7fe90,0x157d8000f,'3fba97a567bac84fac55704f814b1145cedc8621b483232c0330df805469faa3')


class HostBudget:
    def __init__(self):self.records=[];self.requested=0;self.denied=[]
    def record(self,purpose,address,width,raw,error=None,**extra):
        if not 0<width<=208 or len(self.records)>=MAX_HOST_READS or self.requested+width>MAX_HOST_BYTES:
            self.denied.append(dict(purpose=purpose,address=hex(address),width=width));raise ValueError('Captured overlay host budget')
        self.requested+=width
        row=dict(ordinal=len(self.records)+1,purpose=purpose,address=hex(address),width=width,
            bytesHex=raw.hex(),bytesRead=len(raw),error=error,**extra)
        self.records.append(row);return row
    def report(self):
        return dict(reads=len(self.records),requestedBytes=self.requested,maxReads=MAX_HOST_READS,maxBytes=MAX_HOST_BYTES,
            records=self.records,deniedRequests=self.denied,
            scope='All new captured overlay raw/projection/alias/native audits and readouts across warm and seven cold calls; fixed PE/file gate and frozen harness readouts retain their separate scope.')


class ExactMemory:
    """Only actual successful source bytes, without a mapped-page fallback."""
    def __init__(self,records,budget):
        self.budget=budget;self.values={};self.origins={};self.aliases=[];self.sourceExact={}
        for row in records:
            address,width=row['address'],row['requested'];raw=bytes.fromhex(row['bytesHex'])
            budget.record('audit.raw',address,width,raw,row['error'],captureOrdinals=[row['ordinal']])
            if row['error'] is not None or len(raw)!=width or row['bytesRead']!=width or not 0<width<=208:
                raise ValueError('Failed/short/oversize runtime source span')
            self.add(address,raw,{row['ordinal']})
            self.sourceExact.setdefault((address,width),raw)

    def add(self,address,raw,origins):
        if type(address) is not int or not 0<address<=collector.U64 or address+len(raw)>1<<64:
            raise ValueError('Source span outside uint64')
        for offset,value in enumerate(raw):
            cell=address+offset
            if cell in self.values and self.values[cell]!=value:raise ValueError('Contradictory captured overlap')
            self.values[cell]=value;self.origins.setdefault(cell,set()).update(origins)

    @property
    def spans(self):
        result=[];start=previous=None;raw=bytearray()
        for address,value in sorted(self.values.items()):
            if previous is not None and address!=previous+1:
                result.append(('runtime-captured',start,bytes(raw)));raw=bytearray();start=None
            if start is None:start=address
            raw.append(value);previous=address
        if start is not None:result.append(('runtime-captured',start,bytes(raw)))
        return result

    def lookup(self,address,width):
        cells=range(address,address+width)
        if not all(cell in self.values for cell in cells):return b'','unprovided-captured-byte',[]
        origins=set().union(*(self.origins[cell] for cell in cells))
        return bytes(self.values[cell] for cell in cells),None,sorted(origins)

    def read(self,address,width,purpose='audit.projection'):
        raw,error,origins=self.lookup(address,width)
        self.budget.record(purpose,address,width,raw,error,captureOrdinals=origins)
        return raw,error

    def audit_uc(self,uc,address,width,purpose,reader):
        expected,error,origins=self.lookup(address,width)
        raw=bytes(uc.mem_read(address,width)) if error is None else b''
        if error is None and raw!=expected:error='captured-emulator-bytes-differ'
        row=self.budget.record(purpose,address,width,raw,error,reader=hex(reader),captureOrdinals=origins)
        return row

    def add_root_aliases(self,module):
        groups=[(table,field,rva) for table,rvas in cache.ROOTS.items() for field,rva in zip(('mask','blocks','probeLimit'),rvas)]
        groups.append(('ordinary','root',collector.ORDINARY_RVA))
        for table,field,rva in groups:
            original,native=module+rva,0x140000000+rva
            raw,error=self.read(original,8,'audit.alias')
            if error:raise ValueError('Root alias has no source')
            _,_,origins=self.lookup(original,8);self.add(native,raw,set(origins))
            self.aliases.append(dict(table=table,field=field,rva=hex(rva),capturedAddress=hex(original),nativeCell=hex(native),
                width=8,bytesHex=raw.hex(),pointerValueUnchanged=True))
        return self.aliases


def audit_capture(capture,monitor,budget):
    if not all(capture[key] for key in ('ordinaryInputComplete','warmGetterInputComplete','warmObjectIdentityComplete','endIdentityVerified')):
        raise ValueError('Incomplete runtime capture')
    if (capture['status'],capture['budget']['reads'],capture['budget']['requestedBytes'],
            capture['inputConsistency']['status'],capture['inputConsistency']['uniqueSpans'])!=('completed',231,5869,'repeated-unchanged',70):
        raise ValueError('Exact runtime capture inventory differs')
    if any(capture[key] for key in ('atomicSnapshot','naturalLookupObserved','naturalCallerObserved','naturalWriterExcluded','externalWritesExecuted','nativeCpuExecuted')):
        raise ValueError('Runtime capture claim boundary differs')
    if (monitor['status'],monitor['elapsedSeconds'],monitor['finalStatus']['alive'])!=('completed',90,True):
        raise ValueError('Fresh90sec monitor differs')
    modules=monitor['samples'][-1]['modules']
    selected=[row for row in modules['items'] if row['name'].casefold()=='yuanshen.exe']
    if modules['error'] is not None or selected!=[capture['mainModule']] or capture['moduleBase']!=capture['mainModule']['base']:
        raise ValueError('Fresh module identity differs')
    if capture['pid']!=monitor['pid'] or capture['creation']!=monitor['processCreationTime'] or capture['inputChecks']!=monitor['inputs']:
        raise ValueError('Same-epoch identity/input binding differs')
    if capture['sourceObservationSHA256']!=PINS[MONITOR] or capture['sourceSHA256']!=PINS['tools/sample_client72_avatar_runtime_inputs.py']:
        raise ValueError('Runtime sampler/monitor source differs')
    if {row['path']:row['sha256'] for row in capture['inputChecks']['files'].values()}!=cache.prior.frozen.FIVE_INPUTS:
        raise ValueError('Captured five stock files differ')
    if (len(capture['records']),sum(row['requested'] for row in capture['records']))!=(231,5869):
        raise ValueError('Raw request accounting differs')
    if [row['ordinal'] for row in capture['records']]!=list(range(1,232)):
        raise ValueError('Raw request sequence differs')
    memory=ExactMemory(capture['records'],budget)
    ticks=[capture['collectionBeginUTC']]
    for row in capture['records']:ticks.extend((row['beginUTC'],row['endUTC']))
    ticks.append(capture['collectionEndUTC']);clock=iter(ticks)
    projection=collector.collect(memory.read,capture['moduleBase'],now=lambda:next(clock))
    if any(capture[key]!=value for key,value in projection.items()):raise ValueError('Exact original runtime collector projection differs')
    if next(clock,None) is not None:raise ValueError('Recorded collector clock sequence differs')
    return memory,dict(rawRequests=231,rawBytes=5869,uniqueSpans=70,collectorProjectionEquivalent=True,
        sameEpochCanonical=capture['cacheCollector']['canonicalContext']['pointer'],definition1234And1239Verified=True,
        atomicSnapshot=False,naturalLookupObserved=False,processRunningNowClaimed=False)


class WarmNative:
    STACK,RETURN=0x33000000000,0x33000030000
    def __init__(self,binary,libraries,memory):
        self.U,self.R=cache.prior.frozen.base.load_emulator(libraries);self.uc=self.U.Uc(self.U.UC_ARCH_X86,self.U.UC_MODE_64)
        self.memory=memory;self.steps=0;self.failure=None;self.reads=[];self.writes=[];self.missing=[];self.addresses=[]
        pe=cache.prior.frozen.base.PE(binary);start,end,_=GETTER;pages=set()
        for address,width in [(start,end-start),(self.STACK,0x20000),(self.RETURN,1)]+[(a,len(raw)) for _,a,raw in memory.spans]:
            for page in range(address&~4095,(address+width+4095)&~4095,4096):
                if page not in pages:self.uc.mem_map(page,4096);pages.add(page)
        self.uc.mem_write(start,binary[pe.offset(start):pe.offset(start)+end-start])
        for _,address,raw in memory.spans:self.uc.mem_write(address,raw)
        self.uc.hook_add(self.U.UC_HOOK_CODE,self.code)
        self.uc.hook_add(self.U.UC_HOOK_MEM_READ,self.read)
        self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE,self.write)
        self.uc.hook_add(self.U.UC_HOOK_MEM_INVALID,self.invalid)
    def reg(self,name):return self.uc.reg_read(getattr(self.R,'UC_X86_REG_'+name.upper()))
    def code(self,uc,address,width,data):
        self.steps+=1;self.addresses.append(address)
        if not GETTER[0]<=address<GETTER[1]:self.failure='unreviewed-warm-helper-edge';uc.emu_stop()
    def read(self,uc,access,address,width,value,data):
        if self.STACK<=address and address+width<=self.STACK+0x20000:return
        row=self.memory.audit_uc(uc,address,width,'warm.native',self.reg('rip'));self.reads.append(row['ordinal'])
        if row['error']:self.failure=row['error'];uc.emu_stop()
    def write(self,uc,access,address,width,value,data):
        if self.STACK<=address and address+width<=self.STACK+0x20000:return
        self.writes.append(dict(address=hex(address),width=width,writer=hex(self.reg('rip')),value=hex(value)))
        self.failure='unexpected-warm-external-write';uc.emu_stop()
    def invalid(self,uc,access,address,width,value,data):
        self.memory.budget.record('warm.invalid',address,width,b'','unprovided-captured-byte',reader=hex(self.reg('rip')))
        self.missing.append(dict(address=hex(address),width=width,site=hex(self.reg('rip')),access=access))
        self.failure='unprovided-warm-memory-or-code';return False
    def run(self,method,default):
        sp=self.STACK+0x10008;self.uc.mem_write(sp,struct.pack('<Q',self.RETURN))
        for name,value in dict(rsp=sp,rcx=method).items():self.uc.reg_write(getattr(self.R,'UC_X86_REG_'+name.upper()),value)
        error=None
        try:self.uc.emu_start(GETTER[0],self.RETURN,count=LIMIT)
        except self.U.UcError as failure:error=str(failure)
        if self.steps>=LIMIT and self.failure is None:self.failure='instruction-budget'
        returned=self.reg('rip')==self.RETURN and self.failure is None and error is None
        return dict(entry=hex(GETTER[0]),argumentMethodInfo=hex(method),expectedDefault=hex(default),
            returnedDefault=hex(self.reg('rax')),actualRetObserved=returned and 0x157d7ff55 in self.addresses,
            returnedDefaultMatches=returned and self.reg('rax')==default,
            failure=self.failure,error=error,stop=hex(self.reg('rip')),steps=self.steps,instructionLimit=LIMIT,
            pathSHA256=cache.sha(b''.join(struct.pack('<Q',a) for a in self.addresses)),
            executedAddresses=[hex(a) for a in self.addresses],hostReadOrdinals=self.reads,externalWrites=self.writes,missing=self.missing,
            modelsSupplied=[],sourceBoundCandidateGraph=True,naturalCallerObserved=False,defaultWriteExecuted=False)


class CaptureAdapter:
    """Install after frozen prepare hooks; keep all original callbacks and caps."""
    def __init__(self,uc,engine,memory,spans,state):
        self.uc,self.e,self.memory,self.spans,self.state=uc,engine,memory,spans,state
        self.callback=None;self.installed=False
        self.pages={page for _,address,raw in spans for page in range(address&~4095,(address+len(raw)+4095)&~4095,4096)}
    def __getattr__(self,name):return getattr(self.uc,name)
    def hook_add(self,kind,callback,*args,**kwargs):
        if kind==self.e.U.UC_HOOK_CODE:self.callback=callback
        return self.uc.hook_add(kind,callback,*args,**kwargs)
    def install(self):
        inherited=self.callback;e=self.e;state=self.state;memory=self.memory
        snapshot=e.extra_layer.snapshot
        known=[(row['address'],row['address']+row['length'],label) for label,row in snapshot['reads'].items()
            if not label.startswith('cold.runtimeReplay.') and not label.startswith('cold.methodCacheCapture.')]
        ordinary=next(row for row in memory.aliases if row['table']=='ordinary')
        def code(uc,address,size,data):
            if address==0x159b6eb50:
                state['factoryEntries']+=1
                if state['factoryEntries']==2:
                    state['active']=True
                    raw=bytes.fromhex(ordinary['bytesHex'])
                    e.write(int(ordinary['nativeCell'],16),int.from_bytes(raw,'little'))
                    state['ordinaryRootPublishedAtSeventh']=dict(ordinary,entry=hex(address))
            if state['active'] and address==0x14051cd3a:state['vtableStarted']=True
            return inherited(uc,address,size,data)
        def read(uc,access,address,width,value,data):
            if not state['active']:return
            raw,error,_=memory.lookup(address,width)
            captured_page=any(address<page+4096 and address+width>page for page in self.pages)
            alias=any(int(row['nativeCell'],16)<=address and address+width<=int(row['nativeCell'],16)+8 for row in memory.aliases)
            if not captured_page and not alias:return
            if error is not None and not state['vtableStarted']:
                sources=[label for a,z,label in known if a<=address and address+width<=z]
                if sources:
                    state['priorControlledOverlapReads'].append(dict(address=hex(address),width=width,reader=hex(e.reg('rip')),sources=sources))
                    return
            row=memory.audit_uc(uc,address,width,'cold.native',e.reg('rip'));state['capturedReadOrdinals'].append(row['ordinal'])
            if row['error']:e.failure='Unprovided current-epoch captured input byte';uc.emu_stop()
        def write(uc,access,address,width,value,data):
            if not cache.prior.frozen.STACK<=address<cache.prior.frozen.STACK+0x200000:
                state['nativeWrites'].append(dict(callOrdinal=len(state['invocations'])+1,active=state['active'],
                    vtableStarted=state['vtableStarted'],address=hex(address),width=width,writer=hex(e.reg('rip')),value=hex(value)))
            if not state['active']:return
            captured_page=any(address<page+4096 and address+width>page for page in self.pages)
            alias=any(address<int(row['nativeCell'],16)+8 and address+width>int(row['nativeCell'],16) for row in memory.aliases)
            if captured_page or alias:
                state['capturedWriteAttempts'].append(dict(address=hex(address),width=width,writer=hex(e.reg('rip')),value=hex(value)))
                e.failure='Native write to current runtime source input';uc.emu_stop()
        def invalid(uc,access,address,width,value,data):
            if state['active']:
                memory.budget.record('cold.invalid',address,width,b'','unprovided-memory-or-code',reader=hex(e.reg('rip')))
                row=dict(address=hex(address),width=width,site=hex(e.reg('rip')),access=access,rsp=hex(e.reg('rsp')))
                try:
                    stack=bytes(uc.mem_read(e.reg('rsp'),8));memory.budget.record('cold.invalid.caller',e.reg('rsp'),8,stack)
                    row['stackTop']=hex(int.from_bytes(stack,'little'))
                except e.U.UcError:row['stackTop']=None
                state['invalid'].append(row)
            return False
        e.uc.hook_del(e.hooks[0]);e.hooks[0]=self.uc.hook_add(e.U.UC_HOOK_CODE,code)
        e.hooks.extend((self.uc.hook_add(e.U.UC_HOOK_MEM_READ,read),self.uc.hook_add(e.U.UC_HOOK_MEM_WRITE,write),
            self.uc.hook_add(e.U.UC_HOOK_MEM_INVALID,invalid)))
        self.installed=True
    def emu_start(self,*args,**kwargs):
        if not self.installed:self.install()
        before=self.e.steps
        try:return self.uc.emu_start(*args,**kwargs)
        finally:
            self.state['invocations'].append(dict(entry=hex(args[0]),enforcedLimit=kwargs.get('count'),
                steps=self.e.steps-before,stop=hex(self.e.reg('rip')),failure=self.e.failure))
            self.state['lastStorageReports']=dict(hierarchy=self.e.hierarchy_storage.report(),class80=self.e.class80_storage.report())
            if len(self.state['invocations'])==7:
                for address,width in list(self.memory.sourceExact)+[(int(row['nativeCell'],16),8) for row in self.memory.aliases]:
                    row=self.memory.audit_uc(self.uc,address,width,'cold.final-input-audit',self.e.reg('rip'))
                    self.state['inputReadbackOrdinals'].append(row['ordinal'])


def continuous_replay(binary,metadata,startup,libraries,capture,memory,output):
    frozen=cache.prior.frozen;ancestor=cache.prior.ancestor
    original=frozen.base.Offline72;spans=memory.spans
    replay=class80.hierarchy.replay;old_memory=replay.SourceMemory
    memory.add_root_aliases(capture['moduleBase'])
    view=copy.deepcopy(capture['cacheCollector']);view['mainModule']=copy.deepcopy(capture['mainModule'])
    if any(row['requested']>56 for row in view['records']):raise ValueError('Cache-only projection exceeds old56B limit')
    if view['inputConsistency']!={'status':'repeated-unchanged','uniqueSpans':53,'firstPassReads':88}:
        raise ValueError('Old first88 source annotation is not applicable to this exact collection')
    state=dict(active=False,factoryEntries=0,vtableStarted=False,ordinaryRootPublishedAtSeventh=None,
        capturedReadOrdinals=[],capturedWriteAttempts=[],priorControlledOverlapReads=[],invalid=[],invocations=[],
        nativeWrites=[],
        inputReadbackOrdinals=[],
        snapshotBindings=[],initializationFailure=None,cacheOnlyProjection=dict(records=len(view['records']),maxSingleRead=56,
            canonical=view['canonicalContext']['pointer'],sourceSHA256=PINS[CAPTURE]))
    class Overlay(original):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,**kwargs)
            self.runtime_input_adapter=CaptureAdapter(self.uc,self,memory,spans,state);self.uc=self.runtime_input_adapter
        def prepare(self):
            super().prepare();snapshot=self.extra_layer.snapshot
            for ordinal,(_,address,raw) in enumerate(spans):
                ancestor.add_span(snapshot,'cold.runtimeReplay.'+str(ordinal),address,raw)
                state['snapshotBindings'].append(dict(address=hex(address),width=len(raw),sha256=cache.sha(raw)))
    class CountedCacheMemory(old_memory):
        def read(self,address,width):
            raw,error=super().read(address,width)
            _,_,origins=memory.lookup(address,width)
            memory.budget.record('cold.frozen-cache-reader',address,width,raw,error,captureOrdinals=origins)
            return raw,error
    frozen.base.Offline72=Overlay;replay.SourceMemory=CountedCacheMemory;result=None;error=None
    try:
        source_parent=json.loads((ROOT/class80.prepared.PARENT).read_bytes());contract=json.loads((ROOT/class80.prepared.CONTRACT).read_bytes())
        static=class80.prepared.prepare(binary,metadata,startup,source_parent,contract)
        _,template=ancestor.validate_fresh(json.loads((ROOT/class80.TEMPLATE_CAPTURE).read_bytes()))
        vtable=class80.vtable_source(binary,metadata,static,template['ancestor.1234.header'])
        result=class80.run(binary,metadata,startup,libraries,view,static,vtable,output)
    except (ValueError,AssertionError) as failure:
        error=dict(type=type(failure).__name__,message=str(failure))
        trace=failure.__traceback__
        while trace:
            local=trace.tb_frame.f_locals
            if trace.tb_frame.f_code in (class80.run.__code__,class80.hierarchy.run_continuous.__code__,class80.hierarchy.replay.continuous_replay.__code__):
                if isinstance(local.get('result'),dict):result=local['result']
            trace=trace.tb_next
        state['initializationFailure']=error
    finally:frozen.base.Offline72=original;replay.SourceMemory=old_memory
    return dict(result=result,state=state,error=error,
        sourceScope='Current cache/ordinary/warm capture plus frozen explicitly controlled templates and storage; not one natural runtime graph.',
        rootAliases=memory.aliases,capturedOwnerPointersRewritten=False,capturedFunctionPointersRewritten=False,
        priorControlledFunctionRebasesRetained=['RuntimeType slot21','RuntimeType slot82'],oldSourceMemoryLimitChanged=False,
        frozenCacheReaderAccounted=True)


def next_contract(capture,continuous):
    if continuous is None or continuous['result'] is None:
        return dict(currentGap=None if continuous is None else continuous['error'],newInputApproved=False)
    result=continuous['result'];last=result['native'][-1];extension=result['class80Extension']
    index=int(result['stopRegisters']['rcx'],16);root=capture['ordinaryMethods']['root']
    return dict(task='R1-RUNTIME-INPUT-REPLAY-next',currentGap=dict(reader=last['regs']['rip'],
        failure=last['failure'],error=last['error'],methodIndex=index,tableRoot=hex(root),address=hex(root+index*8),width=8,
        sourceFormula='Actual same-epoch ordinary root + original ECX methodIndex*8',
        actualEntry=extension['ordinaryGetterEntries'][-1]),
        minimumNextBytes=8,nextBytesPresentInCapture=False,inflatedMethodInfoIsNotOrdinarySlot=True,
        fixedRemainingSlots=dict(producer='14051c9d0 already reviewed;14051cd3a fixed48B slice',
            payloads=[10294,10293,10289,10290,0,0,10291,10292],uniqueOrdinaryIndices=[10294,10293,10289,10290,0,10291,10292],
            zeroMeaning='Both0 words are fixed source values. Originalmask branch routes them to ordinary getter index0; no null/miss semantics are assumed.',
            conditionalSameEpochBudget=dict(slotReads=7,slotBytes=56,recordReads=7,recordBytes=392,ownerFlagReads=7,ownerFlagBytes=7,
                firstPassReads=21,firstPassBytes=455,withExactRepeatReads=42,withExactRepeatBytes=910),
            condition='Only nonnull slots permit56B MethodInfo+correct index and nonnull owner+c9/1. Null/error/writer/inflation successor remains a real frontier. Root and dependent graph must be freshly reacquired for any future epoch.',
            notApproved=True),
        warmAlternative='Original warm getter is already replayed with current actual owner/rgctx/static field. It does not require cold class creation. Full Avatar caller still needs its own source-bound MethodInfo/usage/rgctx/class-cache graph; do not refill owned cold classes with these natural headers.',
        newSamplingApproved=False,newStorageApproved=False,newSuccessModelApproved=False,
        completeColdClassInitialization=False,defaultOriginalWriteExecuted=False,completeAvatarHandler=False,g2Verified=False,d02Complete=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--warm-only',action='store_true')
    parser.add_argument('--python-libs',type=Path,default=ROOT/'local/migration72/python');args=parser.parse_args()
    if not args.output.resolve().is_relative_to(RAW.resolve()):parser.error('Output must stay in this replay directory')
    if any(p.exists() for p in (args.output,args.output.with_suffix('.source.py'),args.output.with_suffix('.next-input.private.json'))):parser.error('Prior replay evidence is immutable')
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    binary,metadata,startup,*_=[cache.prior.frozen.base.read_verified(args.client/name,expected) for name,expected in cache.prior.frozen.FIVE_INPUTS.items()]
    cache.prior.frozen.base.load_emulator(args.python_libs);pins,_,_=cache.verify_sources(binary,metadata)
    fixed=collector.verify_sources(binary,metadata,verified_sources=pins);pins.update(fixed['sourcePins'])
    for path,expected in {**class80.hierarchy.PINS,**class80.PINS,**PINS}.items():
        if path in pins:
            if pins[path]!=expected:raise ValueError('Source pin conflict: '+path)
        else:
            if cache.sha((ROOT/path).read_bytes())!=expected:raise ValueError('Fixed replay source differs: '+path)
            pins[path]=expected
    capture=json.loads((ROOT/CAPTURE).read_bytes());monitor=json.loads((ROOT/MONITOR).read_bytes())
    review=json.loads((ROOT/REVIEW).read_bytes());exited=json.loads((ROOT/EXIT).read_bytes())
    if (review['reportSHA256']!=PINS[CAPTURE] or review['checkCount']!=14 or not all(review['checks'].values()) or
            not exited['normalExitConfirmed'] or exited['loginPageExplicitlyConfirmed'] or
            len(exited['clientInputs'])!=5 or not all(row['same'] for row in exited['clientInputs']) or
            len(exited['protectedFiles'])!=13 or not all(row['same'] for row in exited['protectedFiles']) or
            exited['processes'] or exited['tcp'] or exited['udp'] or exited['aliasExists']):
        raise ValueError('Frozen normal-exit/review proof differs')
    budget=HostBudget();memory,audit=audit_capture(capture,monitor,budget)
    warm=WarmNative(binary,args.python_libs,memory).run(capture['warmDefault']['dependency']['methodInfo'],capture['warmDefault']['getter']['default'])
    continuous=None if args.warm_only else continuous_replay(binary,metadata,startup,args.python_libs,capture,memory,args.output)
    invocations=[] if continuous is None else continuous['state']['invocations']
    if len(invocations)>7 or any(row['steps']>LIMIT for row in invocations):raise ValueError('Original seven-call CPU budget exceeded')
    total_steps=warm['steps']+sum(row['steps'] for row in invocations)
    inherited=None if continuous is None else continuous['state'].get('lastStorageReports')
    if inherited:
        budgets=[row['budget'] for row in inherited.values()]
        inherited['cumulativeBudget']=dict(modelCalls=sum(row['modelCalls'] for row in budgets),maxModelCalls=7,
            readoutCalls=sum(row['readoutCalls'] for row in budgets),maxReadoutCalls=128,
            readoutBytes=sum(row['readoutBytes'] for row in budgets),maxReadoutBytes=1024)
        total=inherited['cumulativeBudget']
        if total['modelCalls']>7 or total['readoutCalls']>128 or total['readoutBytes']>1024:raise ValueError('Inherited storage budget exceeded')
    following=next_contract(capture,continuous)
    readbacks=[] if continuous is None else continuous['state']['inputReadbackOrdinals']
    integrity=(len(readbacks)==len(memory.sourceExact)+len(memory.aliases) and bool(readbacks) and
        all(budget.records[ordinal-1]['error'] is None for ordinal in readbacks))
    report=dict(task='R1-RUNTIME-INPUT-REPLAY',sourceSHA256=cache.sha(Path(__file__).read_bytes()),sourcePins=pins,
        inputSHA256=cache.prior.frozen.FIVE_INPUTS,captureSHA256=PINS[CAPTURE],inputAudit=audit,warm=warm,
        continuous=continuous,continuousNotRun='Explicit development warm-only phase' if args.warm_only else None,
        inheritedStorage=inherited,hostBudget=budget.report(),inputReadbackVerified=integrity,nextInputContract=following,
        cpuBudget=dict(calls=1+len(invocations),maxCalls=8,instructions=total_steps,maxInstructions=16000000,perCall=LIMIT),
        sourceBoundCandidateGraph=True,atomicSnapshot=False,naturalLookupObserved=False,naturalCallerObserved=False,
        warmGetterReturnVerified=warm['returnedDefaultMatches'],defaultOriginalWriteExecuted=False,
        completeColdClassInitialization=False,completeAvatarHandler=False,g2Verified=False,d02Complete=False,
        capturedInputReadOnlyVerified=integrity and not warm['externalWrites'] and (continuous is None or not continuous['state']['capturedWriteAttempts']),
        normalExitConfirmed=True,loginPageExplicitlyConfirmed=False,
        userFeedback='Normal exit only; login page was not explicitly confirmed.')
    args.output.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    args.output.with_suffix('.next-input.private.json').write_bytes((json.dumps(following,indent=2)+'\n').encode())
    last=None if continuous is None or continuous['result'] is None else continuous['result']['native'][-1]
    print(json.dumps(dict(warmReturn=report['warmGetterReturnVerified'],warmFailure=warm['failure'],cpu=report['cpuBudget'],
        coldIntegrationError=None if continuous is None else continuous['error'],coldStop=None if last is None else last['regs']['rip'],
        coldFailure=None if last is None else last['failure'],coldError=None if last is None else last['error'],
        hostReads=len(budget.records),hostBytes=budget.requested)))


if __name__=='__main__':main()
