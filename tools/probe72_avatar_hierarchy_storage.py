"""One approved16B hierarchy storage boundary on a frozen native continuation."""
import argparse
import json
from pathlib import Path
import struct
import sys

ROOT=Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0,str(ROOT))
    __package__='tools'
from . import replay72_avatar_method_cache as replay

RAW=ROOT/'local/migration72/d02-resume-20261109/r1-hierarchy'
REPLAY_REPORT='local/migration72/d02-resume-20261109/r1-method-cache/replay/first.json'
PINS={
    'tools/replay72_avatar_method_cache.py':'1ac41682fd129a9951fa1d9560b27b44afe8780193ff609664aeadc3f0fa1945',
    REPLAY_REPORT:'02f1182f1a1d7a042e08bcef400a832d5ead0b946f97527214b6de25eda38bbc',
    'local/migration72/d02-resume-20261109/r1-method-cache/replay/first.next-input.private.json':
        '7aeba6e51bdebd0295c763aab0e98062f28c2acbf68068613539dde10de3a98a'}
CANARY=0xfedcba9876543210
MAX_MODEL_CALLS,MAX_READS,MAX_BYTES=4,64,512


class HierarchyStorage:
    """Validation once per foreign call; fixed typed values thereafter."""
    def __init__(self,engine,canonical):
        self.e=engine
        self.canonical=canonical
        self.pointer=self.klass=None
        self.parent_bytes=None
        self.calls=[]
        self.reads=[]
        self.read_bytes=0
        self.zero_count=0
        self.native_stores=[]
        self.published=None
        self.rejected=[]

    def read(self,address,width,purpose):
        if len(self.reads)>=MAX_READS or self.read_bytes+width>MAX_BYTES:
            raise ValueError('Hierarchy cumulative host-readout budget')
        self.read_bytes+=width
        raw=bytes(self.e.uc.mem_read(address,width))
        self.reads.append(dict(address=hex(address),width=width,purpose=purpose,bytesHex=raw.hex()))
        return raw

    def number(self,address,fmt,purpose):
        return struct.unpack('<'+fmt,self.read(address,struct.calcsize('<'+fmt),purpose))[0]

    def caller(self):
        return self.number(self.e.reg('rsp'),'Q','boundary caller classification')

    def call(self,action,caller):
        if len(self.calls)>=MAX_MODEL_CALLS:
            raise ValueError('Hierarchy cumulative model-call budget')
        self.calls.append(dict(action=action,callerReturn=hex(caller)))

    def tail(self):
        if self.number(self.pointer+16,'Q','owned16 tail check')!=CANARY:
            raise ValueError('Hierarchy tail changed')

    def allocate(self,caller):
        e=self.e
        if self.pointer is not None or caller!=0x14051f2ff or e.reg('rdx')!=16:
            raise ValueError('Outside single approved16B hierarchy allocation')
        owner=self.number(0x145bc8ad0,'Q','current registry owner')
        target=e.reg('rsi')
        classes=e.extra_layer.methods.classes
        if e.reg('rcx')!=owner or classes.get(target)!=400:
            raise ValueError('Wrong hierarchy owner/owned400B class')
        depth=self.number(target+0xc4,'B','source-derived hierarchy depth')
        old=self.number(target+0x48,'Q','unpublished hierarchy field')
        generic=self.number(target+0x68,'Q','owned GenericClass identity')
        definition=self.number(generic,'I','owned definition')
        context=self.number(generic+8,'Q','current captured canonical context')
        method=self.number(generic+16,'Q','null method context')
        published=self.number(generic+24,'Q','GenericClass owned class back-reference')
        if (depth,old,definition,context,method,published)!=(2,0,1234,self.canonical,0,target):
            raise ValueError('Wrong definition/depth/context/initial hierarchy')
        self.call('allocate-dirty16',caller)
        self.pointer=e.alloc(24)
        self.klass=target
        e.uc.mem_write(self.pointer,b'\xcc'*16)
        e.uc.mem_write(self.pointer+16,struct.pack('<Q',CANARY))
        self.calls[-1].update(pointer=hex(self.pointer),width=16,tail=hex(CANARY),
            explicitHostWrites=[dict(width=24,action='existing alloc zero-initialization'),
                                dict(width=16,action='dirty owned payload'),dict(width=8,action='nonzero tail')])
        return self.pointer

    def clear(self,caller):
        e=self.e
        expected=(0x14051f30f,0x14051f321)
        if (self.pointer is None or self.zero_count>=2 or caller!=expected[self.zero_count] or
                e.reg('rcx')!=self.pointer or e.reg('edx')!=0 or e.reg('r8')!=16):
            raise ValueError('Outside exact two hierarchy clears')
        self.tail()
        self.call('clear16',caller)
        e.uc.mem_write(self.pointer,bytes(16))
        self.zero_count+=1
        self.calls[-1].update(pointer=hex(self.pointer),width=16)
        return self.pointer

    def copy(self,caller):
        e=self.e
        snapshot=e.extra_layer.snapshot
        label='cold.class371.hierarchy'
        source=snapshot['reads'][label]['address']
        fixed=snapshot['data'][label]
        if (self.pointer is None or self.zero_count!=2 or self.parent_bytes is not None or
                caller!=0x14051f35c or e.reg('rcx')!=self.pointer or e.reg('rdx')!=source or
                e.reg('r8')!=8 or len(fixed)!=8):
            raise ValueError('Outside exact pinned Object hierarchy8B copy')
        self.tail()
        raw=self.read(source,8,'pinned Object hierarchy source')
        if raw!=fixed:
            raise ValueError('Pinned Object hierarchy source changed')
        self.call('copy-parent8',caller)
        e.uc.mem_write(self.pointer,raw)
        self.parent_bytes=raw
        self.calls[-1].update(pointer=hex(self.pointer),source=hex(source),width=8,sourceSHA256=replay.probe.sha(raw))
        return self.pointer

    def native_write(self,uc,address,width,value,writer):
        if self.pointer is None or address+width<=self.pointer or address>=self.pointer+24:
            return
        row=dict(address=hex(address),width=width,writer=hex(writer),value=hex(value))
        self.native_stores.append(row)
        if (address,width,writer,value)!=(self.pointer+8,8,0x14051f333,self.klass):
            self.rejected.append(row)
            self.e.failure='Outside original hierarchy final-slot store or tail boundary'
            uc.emu_stop()

    def publication(self):
        if self.pointer is None or self.published is not None or self.e.reg('rsi')!=self.klass:
            return
        payload=self.read(self.pointer,16,'readout after original xchg')
        self.tail()
        field=self.number(self.klass+0x48,'Q','original published class field')
        if (self.parent_bytes is None or payload!=self.parent_bytes+struct.pack('<Q',self.klass) or field!=self.pointer):
            raise ValueError('Original hierarchy contents/publication differ')
        self.published=dict(pointer=hex(self.pointer),klass=hex(self.klass),bytesHex=payload.hex(),
            originalStore='0x14051f333',originalPublication='0x14051f361',readoutAt='0x14051f365',tail=hex(CANARY))

    def report(self):
        return dict(calls=self.calls,hostReadout=self.reads,
            budget=dict(modelCalls=len(self.calls),maxModelCalls=MAX_MODEL_CALLS,
                readoutCalls=len(self.reads),readoutBytes=self.read_bytes,maxReadoutCalls=MAX_READS,maxReadoutBytes=MAX_BYTES,
                scope='All new Overlay classification/model validation/publication readouts, cumulative over the complete seven-invocation run. Frozen harness readouts remain its existing declared behavior.'),
            originalNativeStores=self.native_stores,publication=self.published,rejected=self.rejected,
            pointer=hex(self.pointer) if self.pointer else None,newNaturalBytesSupplied=0,
            modelScope='Exactly one dirty owned16B allocation, original two clears, exact existing Object8B copy; native store/xchg are not modeled.')


def run_continuous(binary,metadata,startup,libraries,capture,output):
    frozen=replay.probe.prior.frozen
    original_engine=frozen.base.Offline72
    base_code=original_engine._code
    stores=[]
    state=dict(factoryEntries=0,active=False,boundaryProbes=[],nextUnprovidedRequest=None,
               frozenPostconditionObsolete=None)
    class Overlay(original_engine):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,**kwargs)
            storage=HierarchyStorage(self,capture['canonicalContext']['pointer'])
            self.hierarchy_storage=storage
            stores.append(storage)
            old_callback=self._code
            def model(uc,address,size,data,callback):
                old=self.models[address]
                dict.__setitem__(self.models,address,callback)
                try:base_code(self,uc,address,size,data)
                finally:dict.__setitem__(self.models,address,old)
            def code(uc,address,size,data):
                if address==0x159b6eb50:
                    state['factoryEntries']+=1
                    state['active']=state['factoryEntries']==2
                if state['active'] and address in (0x1405091d0,0x141fdf9c0,0x141fdf310):
                    try:caller=storage.caller()
                    except ValueError as error:
                        self.failure=str(error);uc.emu_stop();return
                    state['boundaryProbes'].append(dict(entry=hex(address),callerReturn=hex(caller)))
                    if address==0x1405091d0 and caller==0x14051cb6d:
                        target=storage.number(self.reg('rbp')+0x18,'Q','next class+80 target')
                        state['nextUnprovidedRequest']=dict(entry=hex(address),callerReturn=hex(caller),
                            width=self.reg('rdx'),owner=hex(self.reg('rcx')),ownedClass=hex(target),
                            field='0x80',fieldValueBefore=hex(storage.number(target+0x80,'Q','next unpublished+80')),
                            count=storage.number(target+0xc0,'H','original target count'),
                            template=hex(self.reg('r12')),
                            templateCount=storage.number(self.reg('r12')+0xc0,'H','original template count'),
                            newStorageSupplied=False)
                        self.failure='Unprovided class+80 storage allocation'
                        uc.emu_stop();return
                    if address==0x1405091d0 and caller==0x14051f2ff:
                        if storage.pointer is not None:
                            state['nextUnprovidedRequest']=dict(entry=hex(address),callerReturn=hex(caller),
                                width=self.reg('rdx'),owner=hex(self.reg('rcx')),ownedClass=hex(self.reg('rsi')))
                            self.failure='Unprovided subsequent hierarchy storage allocation'
                            uc.emu_stop();return
                        return model(uc,address,size,data,lambda:storage.allocate(caller))
                    if address==0x141fdf9c0 and caller in (0x14051f30f,0x14051f321):
                        return model(uc,address,size,data,lambda:storage.clear(caller))
                    if address==0x141fdf310 and caller==0x14051f35c:
                        return model(uc,address,size,data,lambda:storage.copy(caller))
                if state['active'] and address==0x14051f365:
                    try:storage.publication()
                    except ValueError as error:
                        self.failure=str(error);uc.emu_stop();return
                return old_callback(uc,address,size,data)
            self.uc.hook_del(self.hooks[0])
            self.hooks[0]=self.uc.hook_add(self.U.UC_HOOK_CODE,code)
            self.hooks.append(self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE,
                lambda uc,access,address,width,value,data:storage.native_write(uc,address,width,value,self.reg('rip'))))
    frozen.base.Offline72=Overlay
    result=old_state=None
    try:
        try:
            replay.continuous_replay(binary,metadata,startup,libraries,capture,output)
            raise ValueError('Frozen old stop unexpectedly remained valid')
        except ValueError as error:
            trace=error.__traceback__
            frame=None
            while trace:
                if trace.tb_frame.f_code is replay.continuous_replay.__code__:
                    frame=trace.tb_frame
                trace=trace.tb_next
            allowed=('Continuous case did not reach the exact bounded storage gap',
                     'Original method loop did not return all eight sampled values')
            if frame is None or str(error) not in allowed:
                raise
            result=frame.f_locals['result']
            old_state=frame.f_locals['state']
            state['frozenPostconditionObsolete']=str(error)
    finally:frozen.base.Offline72=original_engine
    if len(stores)!=1 or result is None or len(result['native'])!=7:
        raise ValueError('One continuous engine/seven invocation scope differs')
    storage=stores[0].report()
    output.with_suffix('.observed.private.json').write_bytes((json.dumps(dict(
        continuous=result,captureExtension=old_state,hierarchyStorage=storage,hierarchyExtension=state),indent=2)+'\n').encode())
    if storage['publication'] is None or len(storage['calls'])!=4 or storage['rejected']:
        raise ValueError('Exact approved storage/publication did not complete')
    reference=json.loads((ROOT/REPLAY_REPORT).read_bytes())['continuous']
    for current,old in zip(result['native'][:6],reference['native'][:6]):
        if any(current[key]!=old[key] for key in ('name','failure','error','regs')) or current['trace']['pathSHA256']!=old['trace']['pathSHA256']:
            raise ValueError('Frozen six independent prefix cases changed')
    if old_state['capturedWriteAttempts'] or result['models']['callbackFailures']:
        raise ValueError('Unexpected captured write/model failure')
    result['captureExtension']=old_state
    result['hierarchyStorage']=storage
    result['hierarchyExtension']=state
    result['frozenSixPrefixUnchanged']=True
    return result


def next_contract(continuous):
    request=continuous['hierarchyExtension']['nextUnprovidedRequest']
    function=continuous['functions']['0x14051c9d0']
    return dict(task='R1-CLASS80-STORAGE',currentStop=request,
        producer=dict(start='0x14051c9d0',end=function['end'],sha256=function['sha256']),
        nativeSizeSource='14051cb4e reads template+c0 U16=2;14051cb61 shifts count by4;14051cb65 passes32.',
        currentDestination='Same owned definition1234/depth2 class, field+80 remains null; hierarchy+48 now published by original instructions.',
        minimumStorageBytes=32,storageSupplied=False,
        possibleFutureBoundaries=[dict(entry='0x1405091d0',callerReturn='0x14051cb6d',width=32),
            dict(entry='0x141fdf9c0',callerReturn='0x14051cb7d',width=32),
            dict(entry='0x141fdf9c0',callerReturn='0x14051cb8f',width=32)],
        originalFieldStore='14051cb93 writes the array to owned class+80; not executed yet.',
        laterSource='14051cc07..cc3e uses fixed70B metadata+30, row ordinal=(U16+0xe42a+entryIndex)&0xffff; table comes from fixed header+54 plus0x8054c847; each row6B holds type-ref S32 and offset S16. Actual selected rows/consumer inputs are not supplied or approved here.',
        originalEntryStores=['14051cc3e writes a sign-extended row offset asDWORD at entry+8',
            '14051cbee writes the original inflated class pointer at entry+0; stride16'],
        newNaturalBytesForAllocationOnly=0,followingRowAndConsumerClosureVerified=False,
        instruction='Plan and verify the exact32B storage and immutable row inputs in a new task; do not enable it from this contract or preapprove other calls.',
        completeColdComparer=False,completeClassInitialization=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python-libs',type=Path,default=ROOT/'local/migration72/python')
    args=parser.parse_args()
    if not args.output.resolve().is_relative_to(RAW.resolve()):parser.error('Output must stay in r1-hierarchy')
    if any(path.exists() for path in (args.output,args.output.with_suffix('.source.py'),
            args.output.with_suffix('.observed.private.json'),args.output.with_suffix('.next-input.private.json'))):
        parser.error('Prior evidence is frozen; choose a new output')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    for path,expected in {**replay.PINS,**PINS}.items():replay.probe.prior.ancestor.prior.read_pinned(path,expected)
    binary,metadata,startup,*_=[replay.probe.prior.frozen.base.read_verified(args.client/name,expected)
        for name,expected in replay.probe.prior.frozen.FIVE_INPUTS.items()]
    replay.probe.prior.frozen.base.load_emulator(args.python_libs)
    pins,_,_=replay.probe.verify_sources(binary,metadata)
    capture=json.loads((ROOT/replay.CAPTURE).read_bytes())
    replay.audit_capture(capture,json.loads((ROOT/replay.MONITOR).read_bytes()))
    continuous=run_continuous(binary,metadata,startup,args.python_libs,capture,args.output)
    following=next_contract(continuous)
    report=dict(task='R1-HIERARCHY-STORAGE',sourceSHA256=replay.probe.sha(Path(__file__).read_bytes()),
        sourcePins={**pins,**replay.PINS,**PINS},inputSHA256=replay.probe.prior.frozen.FIVE_INPUTS,
        continuous=continuous,nextInputContract=following,newNaturalBytesSupplied=0,completeColdComparer=False,completeClassInitialization=False,
        defaultComparerWriteExecuted=False,completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)
    args.output.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    args.output.with_suffix('.next-input.private.json').write_bytes((json.dumps(following,indent=2)+'\n').encode())
    last=continuous['native'][-1]
    print(json.dumps(dict(task=report['task'],failure=last['failure'],error=last['error'],
        stop=last['regs']['rip'],publication=continuous['hierarchyStorage']['publication'] is not None,
        hostBudget=continuous['hierarchyStorage']['budget'],completeAvatarHandler=False)))


if __name__=='__main__':main()
