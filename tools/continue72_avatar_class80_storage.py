"""Exact32B class+80 storage,12B offset rows and48B selected vtable input."""
import argparse
import json
from pathlib import Path
import struct
import sys

ROOT=Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0,str(ROOT));__package__='tools'
from . import probe72_avatar_hierarchy_storage as hierarchy
from . import probe72_avatar_class80_storage as prepared

RAW=ROOT/'local/migration72/d02-resume-20261109/r1-class80/native'
STATIC_REPORT='local/migration72/d02-resume-20261109/r1-class80/static-first.private.json'
TEMPLATE_CAPTURE=hierarchy.replay.probe.prior.ancestor.FRESH
COLD_REPORT='local/migration72/d02-active/avatar/cold-final-first.json'
PINS={
    'tools/probe72_avatar_class80_storage.py':'be224f8408e7bba17562aead4a2511b6d4f85790014ae16c6d489f2e38e62c65',
    'tools/probe72_avatar_hierarchy_storage.py':'50522dd602d7930735c2d42982ffb06f31f601fc37378f614cc6b85919e33a4e',
    STATIC_REPORT:'8e7decdd285f712f77631e3791a91e92e64d2801b128595023a867bdf246c99d',
    prepared.PARENT:'7026c7f3b287e38c4640ed293a412442b6ce85c932f471bdbdfc2e9ce254d344',
    prepared.CONTRACT:'c231c5b8131e2fa65f3bb07ef81ea537cf408458ef08e118af086956cd73826c'}
PINS[TEMPLATE_CAPTURE]=hierarchy.replay.probe.prior.ancestor.FRESH_SHA
PINS[COLD_REPORT]='e0ffa6518897a27589c5ef1de2831ec0b245fdc7e97e98f9a267e148b098590b'
CANARY=0x9abcdef001234567
MAX_CALLS,MAX_READS,MAX_BYTES=3,64,512
VTABLE_SHA='10b206224a0413e642cffd998b39c2982e4d83f09e886579a2acb345cb9937a0'


def vtable_source(binary,metadata,static,template_header):
    base=prepared.static;pe=base.PE(binary);header=pe.offset(base.HEADER_VA)
    row=int(static['ordinaryMetadata']['fileOffset'],16)
    table=prepared.signed32((base.number(binary,header+0x134)+0xaf707941)&0xffffffff)
    index=prepared.signed32((base.number(metadata,row+0x28)+0x9af313c0)&0xffffffff)
    count=base.number(template_header,0xbe,'H')
    file=base.BODY+table+index*4;base.span(metadata,file,count*4);raw=metadata[file:file+count*4]
    if (index,count,file,prepared.sha(raw))!=(8489,12,0x4b1d3d0,VTABLE_SHA):
        raise ValueError('Exact selected12-slot vtable source differs')
    return dict(address=hex(int(static['modelMetadataBody'],16)+table+index*4),bytes=48,
        fileOffset=hex(file),bytesHex=raw.hex(),sha256=VTABLE_SHA,count=count,firstIndex=index,
        consumer='0x14051cd3a',maxNativeReads=12,
        words=[value for value, in struct.iter_unpack('<I',raw)],
        tableBodyOffset=hex(table),newSuccessModels=0,
        sourceFields=dict(headerFieldVA=hex(base.HEADER_VA+0x134),headerRawHex=binary[header+0x134:header+0x138].hex(),
            metadataIndexFieldFileOffset=hex(row+0x28),metadataIndexRawHex=metadata[row+0x28:row+0x2c].hex(),
            countCapture=TEMPLATE_CAPTURE,countCaptureSHA256=PINS[TEMPLATE_CAPTURE],
            countSpan='ancestor.1234.header+be',countRawHex=template_header[0xbe:0xc0].hex()),
        source='Fixed header+134 and selected metadata+28; actual ordinary template+be count12, not the initially zero owned class field.')


class HookTracker:
    def __init__(self,uc,kind,callback):self.uc=uc;self.code_kind=kind;self.current=callback
    def __getattr__(self,name):return getattr(self.uc,name)
    def hook_add(self,kind,callback,*args,**kwargs):
        if kind==self.code_kind:self.current=callback
        return self.uc.hook_add(kind,callback,*args,**kwargs)


class Class80Storage:
    def __init__(self,engine,static):
        self.e=engine;self.static=static;self.pointer=self.klass=None;self.zero_count=0
        self.calls=[];self.reads=[];self.read_bytes=0;self.stores=[];self.rejected=[]
        self.publication=None;self.class_results=[];self.inflation_results=[];self.entries=None

    def read(self,address,width,purpose):
        if len(self.reads)>=MAX_READS or self.read_bytes+width>MAX_BYTES:
            raise ValueError('Class80 cumulative host-readout budget')
        self.read_bytes+=width;raw=bytes(self.e.uc.mem_read(address,width))
        self.reads.append(dict(address=hex(address),width=width,purpose=purpose,bytesHex=raw.hex()))
        return raw

    def number(self,address,fmt,purpose):
        return struct.unpack('<'+fmt,self.read(address,struct.calcsize('<'+fmt),purpose))[0]

    def call(self,action,caller):
        if len(self.calls)>=MAX_CALLS:raise ValueError('Class80 cumulative model-call budget')
        self.calls.append(dict(action=action,callerReturn=hex(caller)))

    def tail(self):
        if self.number(self.pointer+32,'Q','owned32 tail check')!=CANARY:raise ValueError('Class80 tail changed')

    def allocate(self,caller):
        e=self.e;parent=e.hierarchy_storage
        if self.pointer is not None or caller!=0x14051cb6d or e.reg('rdx')!=32:
            raise ValueError('Outside single approved class80 allocation')
        target=self.number(e.reg('rbp')+0x18,'Q','current class80 target')
        owner=self.number(0x145bc8ad0,'Q','current registry owner')
        snapshot=e.extra_layer.snapshot
        expected_template=snapshot['reads']['cold.freshAncestor.class1234.header']['address']
        if (parent.published is None or target!=parent.klass or e.reg('rcx')!=owner or e.reg('r12')!=expected_template):
            raise ValueError('Wrong class80 typed class/owner/template')
        old=self.number(target+0x80,'Q','unpublished class80 field')
        count=self.number(target+0xc0,'H','current class80 count')
        source_count=self.number(expected_template+0xc0,'H','template class80 count')
        if (old,count,source_count)!=(0,2,2):raise ValueError('Wrong class80 initial field/count')
        self.call('allocate-dirty32',caller);self.pointer=e.alloc(40);self.klass=target
        e.uc.mem_write(self.pointer,b'\xcc'*32);e.uc.mem_write(self.pointer+32,struct.pack('<Q',CANARY))
        self.calls[-1].update(pointer=hex(self.pointer),width=32,tail=hex(CANARY),
            explicitHostWrites=[dict(width=40,action='existing allocator initialization'),
                dict(width=32,action='dirty owned payload'),dict(width=8,action='nonzero tail')])
        return self.pointer

    def clear(self,caller):
        e=self.e;callers=(0x14051cb7d,0x14051cb8f)
        if (self.pointer is None or self.zero_count>=2 or caller!=callers[self.zero_count] or
                e.reg('rcx')!=self.pointer or e.reg('edx')!=0 or e.reg('r8')!=32):
            raise ValueError('Outside exact two class80 clears')
        self.tail();self.call('clear32',caller);e.uc.mem_write(self.pointer,bytes(32));self.zero_count+=1
        self.calls[-1].update(pointer=hex(self.pointer),width=32)
        return self.pointer

    def native_write(self,uc,address,width,value,writer):
        if self.pointer is None:return
        if address==self.klass+0x80:
            row=dict(address=hex(address),width=width,value=hex(value),writer=hex(writer),kind='field-publication')
            good=(width,value,writer)==(8,self.pointer,0x14051cb93)
        elif address<self.pointer+40 and address+width>self.pointer:
            row=dict(address=hex(address),width=width,value=hex(value),writer=hex(writer),kind='entry-or-tail')
            relative=address-self.pointer;ordinal=relative//16
            if 0<=ordinal<2 and relative%16==8:
                good=(width,writer,value)==(4,0x14051cc3e,self.static['rows'][ordinal]['signedOffset']&0xffffffff)
            elif 0<=ordinal<2 and relative%16==0:
                matches=[x for x in self.class_results if x['ordinal']==ordinal]
                good=width==8 and writer==0x14051cbee and len(matches)==1 and value==int(matches[0]['pointer'],16)
            else:good=False
        else:return
        self.stores.append(row)
        if not good:
            self.rejected.append(row);self.e.failure='Outside original class80 stores or tail boundary';uc.emu_stop()

    def observe(self,address):
        e=self.e
        if address==0x14051cb9a and self.pointer is not None and self.publication is None:
            field=self.number(self.klass+0x80,'Q','original class80 field publication')
            payload=self.read(self.pointer,32,'payload after original publication');self.tail()
            if field!=self.pointer or self.zero_count!=2 or payload!=bytes(32):
                raise ValueError('Original class80 field publication differs')
            self.publication=dict(pointer=hex(self.pointer),klass=hex(self.klass),writer='0x14051cb93',
                readoutAt=hex(address),bytesHex=payload.hex(),tail=hex(CANARY))
        if address==0x14051cbe3 and self.pointer is not None:
            self.class_results.append(dict(ordinal=e.reg('rdi'),byteOffset=e.reg('rsi'),pointer=hex(e.reg('rax')),
                actualReturnSite=hex(address),returnModelSupplied=False))
        if address==0x14051cbd9 and self.pointer is not None:
            self.inflation_results.append(dict(ordinal=e.reg('rdi'),descriptor=hex(e.reg('rax')),
                actualReturnSite=hex(address),returnModelSupplied=False))
        if address==0x14051cc62 and self.pointer is not None and self.entries is None:
            payload=self.read(self.pointer,32,'entries after original row loop');self.tail()
            self.entries=[dict(ordinal=i,classPointer=hex(struct.unpack_from('<Q',payload,i*16)[0]),
                signedOffset=struct.unpack_from('<i',payload,i*16+8)[0],paddingHex=payload[i*16+12:i*16+16].hex()) for i in range(2)]
            if len(self.class_results)!=2 or [x['signedOffset'] for x in self.entries]!=[4,6]:
                raise ValueError('Original class80 row loop did not finish exactly two entries')

    def report(self):
        return dict(calls=self.calls,hostReadout=self.reads,
            budget=dict(modelCalls=len(self.calls),maxModelCalls=MAX_CALLS,readoutCalls=len(self.reads),readoutBytes=self.read_bytes,
                maxReadoutCalls=MAX_READS,maxReadoutBytes=MAX_BYTES,
                scope='All new32B layer classification, audit, publication and final readouts across seven calls; frozen harness readouts retain their original scope.'),originalStores=self.stores,
            publication=self.publication,actualClassReturns=self.class_results,actualInflationReturns=self.inflation_results,
            entries=self.entries,rejected=self.rejected,
            successfulInflationModelsSupplied=0,newNaturalBytesSupplied=0)


class FixedMetadataInputs:
    """Permit only original field reads from the two selected fixed slices."""
    def __init__(self,engine,storage,rows,vtable,state):
        self.e,self.storage,self.rows,self.vtable,self.state=engine,storage,rows,vtable,state
        self.start=int(vtable['address'],16)
        self.pages={int(row['address'],16)&~4095 for row in rows}|{self.start&~4095}

    def reject(self,message):
        self.e.failure=message;self.e.uc.emu_stop()

    def read(self,uc,access,address,width,value,data):
        if address&~4095 not in self.pages:return
        reader=self.e.reg('rip')
        if self.start<=address and address+width<=self.start+48:
            if width!=4 or (address-self.start)%4 or reader!=0x14051cd3a or len(self.state['vtableReads'])>=12:
                self.reject('Unreviewed or overbudget selected vtable row read');return
            ordinal=(address-self.start)//4
            expected=bytes.fromhex(self.vtable['bytesHex'])[ordinal*4:ordinal*4+4]
            try:raw=self.storage.read(address,4,'fixed selected vtable native audit')
            except ValueError as error:self.reject(str(error));return
            if raw!=expected:self.reject('Selected vtable bytes differ');return
            word=int.from_bytes(raw,'little')
            self.state['vtableReads'].append(dict(ordinal=ordinal,address=hex(address),width=4,reader=hex(reader),
                bytesHex=raw.hex(),payload=word&0x1fffffff,kindBits=hex(word&0xe0000000)))
            return
        for ordinal,row in enumerate(self.rows):
            start=int(row['address'],16)
            if (address,width,reader) in ((start,4,0x14051cc35),(start+4,2,0x14051cc39)):
                try:raw=self.storage.read(address,width,'fixed class80 native row audit')
                except ValueError as error:self.reject(str(error));return
                expected=bytes.fromhex(row['bytesHex'])[address-start:address-start+width]
                self.state['metadataReads'].append(dict(ordinal=ordinal,address=hex(address),width=width,
                    reader=hex(reader),bytesHex=raw.hex()))
                if raw!=expected:self.reject('Fixed class80 row bytes differ')
                return
        self.reject('Unprovided class80 metadata-page bytes')

    def write(self,uc,access,address,width,value,data):
        if any(address<page+4096 and address+width>page for page in self.pages):
            self.state['metadataWriteAttempts'].append(dict(address=hex(address),width=width,writer=hex(self.e.reg('rip'))))
            self.reject('Native write to fixed class80 metadata input')


def run(binary,metadata,startup,libraries,capture,static,vtable,output):
    frozen=hierarchy.replay.probe.prior.frozen;ancestor=hierarchy.replay.probe.prior.ancestor
    original=frozen.base.Offline72;base_code=original._code;engines=[]
    source_parent=json.loads((ROOT/prepared.PARENT).read_bytes())
    previous_boundaries=source_parent['continuous']['hierarchyExtension']['boundaryProbes']
    known={(int(x['entry'],16),int(x['callerReturn'],16)) for x in previous_boundaries}
    state=dict(factoryEntries=0,metadataBindings=[],metadataReads=[],metadataWriteAttempts=[],vtableBinding=vtable,
        vtableReads=[],ordinaryGetterEntries=[],inputBindingDuringPrepare=False,nextUnprovidedRequest=None,invalidReads=[])
    candidates=static['controlledInputCandidates'];vtable_start=int(vtable['address'],16)
    class Overlay(original):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,**kwargs)
            self.class80_storage=Class80Storage(self,static);engines.append(self)
            self.class80_tracker=HookTracker(self.uc,self.U.UC_HOOK_CODE,self._code);self.uc=self.class80_tracker

        def _invalid(self,uc,access,address,width,value,data):
            state['invalidReads'].append(dict(address=hex(address),width=width,reader=hex(self.reg('rip')),access=access))
            return super()._invalid(uc,access,address,width,value,data)

        def prepare(self):
            super().prepare();storage=self.class80_storage;snapshot=self.extra_layer.snapshot
            for ordinal,row in enumerate(candidates):
                ancestor.add_span(snapshot,'cold.class80.fixedRow'+str(ordinal),int(row['address'],16),bytes.fromhex(row['bytesHex']))
            ancestor.add_span(snapshot,'cold.class80.fixedVtableSlice',vtable_start,bytes.fromhex(vtable['bytesHex']))
            state['metadataBindings']=candidates;state['inputBindingDuringPrepare']=True
            inherited=self.class80_tracker.current
            def model(uc,address,size,data,callback):
                previous=self.models[address];dict.__setitem__(self.models,address,callback)
                try:base_code(self,uc,address,size,data)
                finally:dict.__setitem__(self.models,address,previous)
            def code(uc,address,size,data):
                if address==0x159b6eb50:state['factoryEntries']+=1
                try:
                    if state['factoryEntries']==2:
                        if address==0x140526fb0:
                            caller=storage.number(self.reg('rsp'),'Q','original vtable MethodInfo getter caller')
                            index=self.reg('ecx')
                            if (caller==0x14051cdc7 and state['vtableReads'] and
                                    index==state['vtableReads'][-1]['payload']):
                                if address in self.models:raise ValueError('Unapproved ordinary getter success model')
                                state['ordinaryGetterEntries'].append(dict(entry=hex(address),callerReturn=hex(caller),
                                    methodIndex=index,modelSupplied=False))
                                return base_code(self,uc,address,size,data)
                        parent=getattr(self,'hierarchy_storage',None)
                        if parent is not None and parent.published is not None and address in (0x1405091d0,0x141fdf9c0):
                            caller=storage.number(self.reg('rsp'),'Q','class80 boundary caller classification')
                            if address==0x1405091d0 and caller==0x14051cb6d and storage.pointer is None:
                                return model(uc,address,size,data,lambda:storage.allocate(caller))
                            if address==0x141fdf9c0 and caller in (0x14051cb7d,0x14051cb8f):
                                return model(uc,address,size,data,lambda:storage.clear(caller))
                            if (address,caller) not in known:
                                state['nextUnprovidedRequest']=dict(entry=hex(address),callerReturn=hex(caller),
                                    rcx=hex(self.reg('rcx')),rdx=hex(self.reg('rdx')),r8=hex(self.reg('r8')),storageSupplied=False)
                                self.failure='Unprovided successor storage/model boundary';uc.emu_stop();return
                        storage.observe(address)
                except ValueError as error:
                    self.failure=str(error);uc.emu_stop();return
                return inherited(uc,address,size,data)
            fixed=FixedMetadataInputs(self,storage,candidates,vtable,state)
            self.uc.hook_del(self.hooks[0]);self.hooks[0]=self.uc.hook_add(self.U.UC_HOOK_CODE,code)
            self.hooks.extend((self.uc.hook_add(self.U.UC_HOOK_MEM_READ,fixed.read),self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE,fixed.write),
                self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE,lambda uc,a,p,n,v,d:storage.native_write(uc,p,n,v,self.reg('rip')))))
    frozen.base.Offline72=Overlay
    try:result=hierarchy.run_continuous(binary,metadata,startup,libraries,capture,output)
    finally:frozen.base.Offline72=original
    if len(engines)!=1:raise ValueError('Class80 must use one continuous engine')
    storage=engines[0].class80_storage.report();old=result['hierarchyStorage']['budget'];new=storage['budget']
    cumulative=dict(modelCalls=old['modelCalls']+new['modelCalls'],maxModelCalls=7,
        readoutCalls=old['readoutCalls']+new['readoutCalls'],maxReadoutCalls=128,
        readoutBytes=old['readoutBytes']+new['readoutBytes'],maxReadoutBytes=1024)
    if cumulative['modelCalls']>7 or cumulative['readoutCalls']>128 or cumulative['readoutBytes']>1024:
        raise ValueError('Cumulative16B+32B budget exceeded')
    result['class80Storage']=storage;result['class80Extension']=state;result['cumulativeStorageBudget']=cumulative
    expected_interfaces=bytes.fromhex(result['interfaceStorage']['allocations'][0]['bytesHex'])
    if storage['entries'] is not None:
        if [int(x['classPointer'],16) for x in storage['entries']]!=list(struct.unpack('<QQ',expected_interfaces)):
            raise ValueError('Original class80 returns differ from the already produced interface classes')
        result['class80InterfaceIdentityVerified']=True
    output.with_suffix('.class80-observed.private.json').write_bytes((json.dumps(result,indent=2)+'\n').encode())
    return result


def natural_owner_contract(binary):
    function=json.loads((ROOT/COLD_REPORT).read_bytes())['declarations']['functions']['0x157d7fe90']
    start,end=0x157d7fe90,int(function['end'],16);pe=prepared.static.PE(binary)
    actual=prepared.sha(binary[pe.offset(start,end-start,executable=True):pe.offset(start)+end-start])
    if actual!=function['nativeBytesSHA256']:raise ValueError('Original Default getter source window differs')
    return dict(task='R1-NATURAL-OWNER-DEFAULT-contract-only',samplingStarted=False,samplingAuthorizedByThisCard=False,
        input='Reacquire canonical and the10288 cache hit in one fresh user-ready epoch; old owners are provenance pins only.',
        originalConsumer=dict(start=hex(start),end=hex(end),sha256=actual,argument='RCX=the complete sampled10288 MethodInfo'),
        phaseA=dict(newReads=[dict(source='MethodInfo10288+0',width=208,meaning='natural owner class header'),
            dict(source='owner+68',width=32,meaning='GenericClass definition1234, current canonical classInst, null methodInst and owner backref'),
            dict(source='[owner+30]+10',width=8,meaning='actual RGCTX comparer-class slot; require it equals this owner for this minimal contract'),
            dict(source='[owner+38]+0',width=8,meaning='actual Default pointer, after owner+c8 bit0 is observed set')],
            firstPassReads=4,firstPassBytes=256,withExactSpanRepeatReads=8,withExactSpanRepeatBytes=512,
            fastPath='Original157d7fe90 reachesfed5 nonnull branch,ff4b return; no class initializer, lazy RGCTX, helper or default-write callback.',
            stop='Any null/short/changed span, wrong definition/context/backref, clear c8 bit0, comparer slot different from owner, or null Default stops; do not set any flag or field.',
            proves='Source-bound original warm getter returns the sampled pointer without dereferencing Default; not observed natural caller execution.'),
        optionalObjectIdentity=dict(additionalFirstPassReads=3,additionalFirstPassBytes=248,
            reads=['Default+0/8 -> object class','that class/208','that class+68 -> GenericClass/32'],
            condition='Check actual definition1239 and canonical context; otherwise record actual identity and stop without substituting a class.',
            phaseAPlusIdentityRepeatedReads=14,phaseAPlusIdentityRepeatedBytes=1008),
        lifecycle=dict(addressing='Fresh B+RVA for roots; every descendant comes from successful reads in this epoch. Preserve full64-bit pointers. No old PID/base/owner reuse.',
            timing='Record collection and per-read begin/end UTC, raw ordinal, error and identity before/after. RPM only; no writer/lock guarantee.',
            consistency='All selected roots, descriptors and new spans must receive one final exact-span repeat after acquisition. Same bytes prove repeat consistency only; atomicSnapshot=false and naturalLookupObserved=false.',
            integratedMaximum='If a new staged collector defers the existing cache final repeat until after phaseA, total maximum3440 reads59456B; phaseA+identity3446 reads59952B. The frozen collector cannot simply append reads and claim its earlier repeat occurred afterwards.',
            stock='Fresh user-ready, profile/config/process/module identity and all five stock hashes required; this card performs no new capture.'),
        furtherConsumers='The warm getter requires no interface/vtable span. Original Avatar/HashSet dispatch may need the actual Default object class+80/count+c0 and selected vtable pairs/MethodInfos; derive precise slots from its real caller before approving another bounded extension. Owner header alone does not cover those consumers.',
        completeAvatarInputContract=False,initializedOrLoadedAllAssumed=False,ownerRewritten=False,
        atomicSnapshot=False,naturalLookupObserved=False,completeClassInitialization=False,completeAvatarHandler=False)


def next_contract(continuous,natural):
    last=continuous['native'][-1]
    return dict(task='R1-CLASS80-STORAGE-next',currentStop=dict(failure=last['failure'],error=last['error'],
        registers=continuous['stopRegisters'],lastRuntimeRead=continuous['runtimeGlobalReads'][-1]),
        producer=continuous['functions']['0x14051c9d0'],ordinaryGetter=continuous['functions']['0x140526fb0'],
        currentVtable=dict(count=12,providedBytes=48,consumed=continuous['class80Extension']['vtableReads'],
            selectedObjectMethodIndices=[3870,3872,3873,3876],remainingVtableComplete=False),
        minimumNextInput=dict(moduleRVA='0x5bc8f20',width=8,reader='0x140526fc5',
            meaning='Actual ordinary MethodInfo pointer-table root. It has no supplied value; no empty-root or return model is authorized.'),
        conditionalReadOnlySuffix=dict(sequence=['B+5bc8f20/8','root+3870*8/8','nonzero MethodInfo/56','nonzero MethodInfo.owner+c9/1'],
            oneQueryFirstPassReads=4,oneQueryFirstPassBytes=73,
            fourSelectedObjectQueriesMaxReads=13,fourSelectedObjectQueriesMaxBytes=268,
            repeat='Repeat each unique successful exact span at end; upper bound26 reads536B for all four selected Object indices. Not a complete class or runtime contract.',
            null='Null root/slot/owner or unreadable byte stops. A null slot enters the original writer; never supply a zero slot to avoid that writer.',
            ownerFlag='If actual owner+c9 bit0x20 is set, stop before the new inflation branch; its inputs are not supplied here.',
            replay='Preserve sampled addresses/values and bind only root-cell storage B+5bc8f20 to preferredPE+5bc8f20. Reject writes, holes and contradictory overlap; stop before any unprovided root, branch or target code.',
            lifetime='Future same-epoch sampling must reacquire dependent cache/owner spans; a new process root cannot turn the existing mixed controlled graph into one natural graph.'),
        alternativeNaturalOwner=natural,continuedStorageSupplied=False,newSuccessModelsApproved=0,newNativeWindowsApproved=0,
        completeColdComparer=False,completeClassInitialization=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python-libs',type=Path,default=ROOT/'local/migration72/python');args=parser.parse_args()
    if not args.output.resolve().is_relative_to(RAW.resolve()):parser.error('Output must stay in class80/native')
    if any(x.exists() for x in (args.output,args.output.with_suffix('.source.py'),args.output.with_suffix('.observed.private.json'),
            args.output.with_suffix('.class80-observed.private.json'),args.output.with_suffix('.next-input.private.json'))):parser.error('Prior evidence/source is frozen')
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    binary,metadata,startup,*_=[hierarchy.replay.probe.prior.frozen.base.read_verified(args.client/path,expected)
        for path,expected in hierarchy.replay.probe.prior.frozen.FIVE_INPUTS.items()]
    hierarchy.replay.probe.prior.frozen.base.load_emulator(args.python_libs)
    pins,_,_=hierarchy.replay.probe.verify_sources(binary,metadata)
    for path,expected in {**hierarchy.replay.PINS,**hierarchy.PINS,**prepared.PINS,**PINS}.items():
        if path in pins:
            if pins[path]!=expected:raise ValueError('Contradictory source pin: '+path)
        else:
            hierarchy.replay.probe.prior.ancestor.prior.read_pinned(path,expected);pins[path]=expected
    parent=json.loads((ROOT/prepared.PARENT).read_bytes());contract=json.loads((ROOT/prepared.CONTRACT).read_bytes())
    static=prepared.prepare(binary,metadata,startup,parent,contract)
    _,captured=hierarchy.replay.probe.prior.ancestor.validate_fresh(json.loads((ROOT/TEMPLATE_CAPTURE).read_bytes()))
    vtable=vtable_source(binary,metadata,static,captured['ancestor.1234.header'])
    natural=natural_owner_contract(binary)
    frozen_static=json.loads((ROOT/STATIC_REPORT).read_bytes())
    if any(frozen_static[key]!=value for key,value in static.items()):raise ValueError('Fixed static preparation projection differs')
    capture=json.loads((ROOT/hierarchy.replay.CAPTURE).read_bytes())
    hierarchy.replay.audit_capture(capture,json.loads((ROOT/hierarchy.replay.MONITOR).read_bytes()))
    result=run(binary,metadata,startup,args.python_libs,capture,static,vtable,args.output)
    following=next_contract(result,natural)
    report=dict(task='R1-CLASS80-STORAGE-native',sourceSHA256=prepared.sha(Path(__file__).read_bytes()),
        sourcePins={**pins,**hierarchy.replay.PINS,**hierarchy.PINS,**PINS},
        inputSHA256=hierarchy.replay.probe.prior.frozen.FIVE_INPUTS,staticPreparationSHA256=PINS[STATIC_REPORT],
        nativePhaseAuthorizedByTask=True,continuous=result,nextInputContract=following,successfulInflationModelsSupplied=0,
        sourceBoundControlledGraph=True,atomicSnapshot=False,naturalLookupObserved=False,
        newInitializedOrLoadedAllFlagsSupplied=False,newNaturalBytesSupplied=0,
        completeClass80Rows=result['class80Storage']['entries'] is not None,completeClassInitialization=False,
        defaultComparerWriteExecuted=False,completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)
    args.output.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    args.output.with_suffix('.next-input.private.json').write_bytes((json.dumps(following,indent=2)+'\n').encode())
    last=result['native'][-1]
    print(json.dumps(dict(task=report['task'],failure=last['failure'],error=last['error'],stop=last['regs']['rip'],
        publication=result['class80Storage']['publication'] is not None,entries=report['completeClass80Rows'],
        budget=result['cumulativeStorageBudget'],newWindows=len(result['extension']['windowRequests']),completeAvatarHandler=False)))


if __name__=='__main__':main()
