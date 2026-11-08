"""Pure bounded CN72 runtime-input collection; owned CLI only, no live adapter."""
import argparse
import datetime
import json
from pathlib import Path
import struct
import sys

ROOT=Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0,str(ROOT));__package__='tools'
from . import probe72_avatar_method_cache as cache

RAW=ROOT/'local/migration72/d02-resume-20261109/r1-runtime-inputs'
THIRD='local/migration72/d02-resume-20261109/r1-class80/native/main-third.json'
CONTRACT='local/migration72/d02-resume-20261109/r1-class80/native/main-third.next-input.private.json'
PINS={
    'tools/probe72_avatar_method_cache.py':'6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544',
    'tools/continue72_avatar_class80_storage.py':'9c19d79f6606b17bfde2d00762d1e8ded232e0bdf82b4047abddb9877b1a6c65',
    THIRD:'75e3bc05bb9a8044318c3fce5d30fc2d9f7a51138ede8af23a7e953e8a361283',
    CONTRACT:'6c5be2eb618e9e16e457ef741dab7c2626147d1dcca821e5e99c010f4b8d54e5'}
ORDINARY_RVA=0x5bc8f20
INDICES=(3870,3872,3873,3876)
MAX_READS,MAX_BYTES,MAX_SINGLE=5188,89960,208
NEW_READS,NEW_BYTES=20,772
U64=(1<<64)-1


class ReadStop(Exception):pass


def utc_now():return datetime.datetime.now(datetime.timezone.utc).isoformat()


class Ledger:
    """Record requests, including frozen-cache local rejects and all final reads."""
    def __init__(self,read,now):
        self.backend,self.now=read,now
        self.records=[];self.requested=0;self.new_reads=0;self.new_bytes=0
        self.cache_calls=[];self.spans={};self.observed={};self.conflicts=[];self.denied=[]

    def backend_read(self,address,width,*,frozen_error=False):
        begin=self.now();exception=None
        try:raw,error=self.backend(address,width)
        except OSError as failure:
            winerror=getattr(failure,'winerror',None)
            code=winerror if winerror is not None else failure.errno
            raw,error=b'',{'winerror':getattr(failure,'winerror',failure.errno) if frozen_error else code}
            exception=dict(type='OSError',winerror=winerror,errno=failure.errno,normalizedError=code,
                frozenLegacyErrorPreserved=frozen_error)
        if not isinstance(raw,(bytes,bytearray)):
            raise TypeError('read backend must return (bytes,error)')
        raw=bytes(raw);end=self.now()
        meta=dict(beginUTC=begin,endUTC=end,backendCalled=True)
        if exception is not None:meta['backendException']=exception
        return raw,error,meta

    def cache_read(self,address,width):
        raw,error,meta=self.backend_read(address,width,frozen_error=True)
        self.cache_calls.append((address,width,raw,error,meta));return raw,error

    def append(self,name,phase,address,width,raw,error,meta):
        complete=error is None and len(raw)==width
        error=error if error is not None else (None if complete else 'partial-read')
        self.requested+=width
        row=dict(ordinal=len(self.records)+1,name=name,phase=phase,address=address,requested=width,
            bytesRead=len(raw),bytesHex=raw.hex(),error=error,**meta)
        self.records.append(row)
        conflict=False
        if complete:
            self.spans.setdefault((address,width),(raw,row['ordinal']))
            for offset,value in enumerate(raw):
                previous=self.observed.get(address+offset)
                if previous is not None and previous[0]!=value:
                    if not conflict:
                        self.conflicts.append(dict(address=address+offset,firstOrdinal=previous[1],
                            currentOrdinal=row['ordinal'],firstByte=previous[0],currentByte=value))
                    conflict=True
                else:self.observed.setdefault(address+offset,(value,row['ordinal']))
        return row,conflict

    def adopt_cache(self,result):
        cursor=0
        for original in result['records']:
            address,width=original['address'],original['requested']
            if 0<address<=U64 and address+width<=1<<64:
                actual_address,actual_width,raw,error,meta=self.cache_calls[cursor];cursor+=1
                normalized=error if error is not None else (None if len(raw)==width else 'partial-read')
                if (actual_address,actual_width,raw[:width].hex(),len(raw),normalized)!=(
                        address,width,original['bytesHex'],original['bytesRead'],original['error']):
                    raise ValueError('Frozen collector/backend request sequence differs')
            else:
                raw,error=b'',original['error'];meta=dict(beginUTC=None,endUTC=None,backendCalled=False)
            row,_=self.append(original['name'],'cache',address,width,raw,error,meta)
            row['cacheOrdinal']=len(self.records)
        if cursor!=len(self.cache_calls) or len(self.records)!=result['budget']['reads'] or self.requested!=result['budget']['requestedBytes']:
            raise ValueError('Frozen collector accounting differs')
        if len(self.spans)>cache.FIRST_PASS_READS or sum(n for _,n in self.spans)>cache.FIRST_PASS_BYTES:
            raise ValueError('Frozen unique-span budget differs')

    def get(self,name,address,width,phase='new'):
        reason=None
        if not 0<width<=MAX_SINGLE or len(self.records)>=MAX_READS or self.requested+width>MAX_BYTES:
            reason='collector-budget'
        elif phase=='new' and (self.new_reads>=NEW_READS or self.new_bytes+width>NEW_BYTES):
            reason='new-input-budget'
        if reason:
            self.denied.append(dict(name=name,address=address,width=width,phase=phase,reason=reason));raise ReadStop(reason)
        if phase=='new':self.new_reads+=1;self.new_bytes+=width
        if not 0<address<=U64 or address+width>1<<64:
            raw,error=b'','address-outside-uint64';meta=dict(beginUTC=self.now(),endUTC=self.now(),backendCalled=False)
        else:raw,error,meta=self.backend_read(address,width)
        row,conflict=self.append(name,phase,address,width,raw,error,meta)
        if row['error'] is not None:raise ReadStop(name+':unreadable')
        if conflict:raise ReadStop(name+':overlap-disagrees')
        return raw

    def final_repeat(self):
        spans=list(self.spans.items());results=[]
        for (address,width),(before,ordinal) in spans:
            first=len(self.records)
            try:
                after=self.get('final.'+str(len(results)),address,width,'final')
                failure=None if after==before else 'span-changed'
            except ReadStop as stop:failure=str(stop)
            results.append(dict(address=address,width=width,firstOrdinal=ordinal,
                repeatOrdinal=self.records[-1]['ordinal'] if len(self.records)>first else None,
                status='unchanged' if failure is None else 'failed',failure=failure))
        okay=not self.conflicts and not self.denied and all(row['error'] is None for row in self.records) and all(
            row['status']=='unchanged' for row in results)
        return dict(status='repeated-unchanged' if okay else 'failed',
            scope='A new full exact-span pass after all extension reads, including every successful frozen-cache span.',
            uniqueSpans=len(spans),uniqueBytes=sum(width for _,width in self.spans),
            finalRepeats=results,overlapConflicts=self.conflicts,deniedRequests=self.denied,
            atomicSnapshot=False,naturalWriterExcluded=False)


def ordinary_inputs(ledger,module):
    result=dict(status='partial',root=None,methods=[],failure=None)
    try:
        root=int.from_bytes(ledger.get('ordinary.root',module+ORDINARY_RVA,8),'little');result['root']=root
        if not root:raise ReadStop('ordinary:root-null')
    except ReadStop as stop:result['failure']=str(stop);return result
    for index in INDICES:
        row=dict(index=index,status='partial',failure=None)
        try:
            slot=root+index*8;row['slot']=slot
            pointer=int.from_bytes(ledger.get('ordinary.'+str(index)+'.slot',slot,8),'little');row['pointer']=pointer
            if not pointer:raise ReadStop('ordinary:slot-null')
            raw=ledger.get('ordinary.'+str(index)+'.methodInfo',pointer,56);row['methodInfoHex']=raw.hex()
            returned=struct.unpack_from('<I',raw,24)[0];row['returnedIndex']=returned
            if returned!=index:raise ReadStop('ordinary:returned-index-mismatch')
            owner=struct.unpack_from('<Q',raw)[0];row['owner']=owner
            if not owner:raise ReadStop('ordinary:owner-null')
            flag=ledger.get('ordinary.'+str(index)+'.ownerFlag',owner+0xc9,1)[0];row['ownerFlag']=flag
            if flag&0x20:raise ReadStop('ordinary:unprovided-owner-inflation')
            row['status']='collected'
        except ReadStop as stop:row['failure']=str(stop)
        result['methods'].append(row)
    if all(row['status']=='collected' for row in result['methods']):result['status']='collected'
    return result


def typed_class(ledger,address,definition,canonical,label):
    header=ledger.get(label+'.header',address,208)
    bits=struct.unpack_from('<I',header,0x18)[0]
    if (bits>>16)&255!=21 or bits&0x40000000:raise ReadStop(label+':type-mismatch')
    if not header[0xc8]&1:raise ReadStop(label+':initialization-flag-clear')
    generic=struct.unpack_from('<Q',header,0x68)[0]
    if not generic:raise ReadStop(label+':generic-class-null')
    raw=ledger.get(label+'.genericClass',generic,32)
    actual,context,method,backref=struct.unpack('<I4xQQQ',raw)
    if actual!=definition:raise ReadStop(label+':definition-mismatch')
    if context!=canonical or method:raise ReadStop(label+':context-mismatch')
    if backref!=address:raise ReadStop(label+':backref-mismatch')
    return header,dict(address=address,definition=actual,genericClass=generic,canonical=context,backref=backref,
        observedInitializedFlag=True,headerKind=21)


def warm_inputs(ledger,prior):
    result=dict(dependency=None,getter=dict(status='partial',failure=None),identity=dict(status='not-read',failure=None))
    getter=result['getter']
    try:
        if prior['status']!='completed' or prior['inputConsistency']['status']!='repeated-unchanged':
            raise ReadStop('warm:cache-dependency-incomplete')
        canonical=prior['canonicalContext']['pointer']
        selected=next((row for row in prior['methods'] if row['index']==10288 and row['status']=='hit'),None)
        if selected is None:raise ReadStop('warm:10288-not-hit')
        descriptor=selected['path'][-1]['descriptor']
        key=next(row for row in reversed(prior['records']) if row['name'].startswith('method10288.') and
            row['name'].endswith('.key') and row['address']==descriptor and row['error'] is None)
        index,context,method=struct.unpack('<I4xQQ',bytes.fromhex(key['bytesHex']))
        if (index,context,method)!=(10288,canonical,0):raise ReadStop('warm:selected-key-context-mismatch')
        result['dependency']=dict(index=10288,methodInfo=selected['pointer'],canonical=canonical,keyDescriptor=descriptor,
            keyBytesHex=key['bytesHex'],cacheInternalConsistency=prior['inputConsistency']['status'])
        owner=struct.unpack_from('<Q',bytes.fromhex(selected['methodInfoHex']))[0];getter['owner']=owner
        if not owner:raise ReadStop('warm:owner-null')
        header,owner_info=typed_class(ledger,owner,1234,canonical,'warm.owner');getter['class']=owner_info
        slots,static_area=struct.unpack_from('<QQ',header,0x30)
        if not slots:raise ReadStop('warm:rgctx-null')
        slot=int.from_bytes(ledger.get('warm.rgctx.classSlot',slots+0x10,8),'little');getter['rgctxSlot']=slot
        if slot!=owner:raise ReadStop('warm:rgctx-slot-mismatch')
        if not static_area:raise ReadStop('warm:static-area-null')
        default=int.from_bytes(ledger.get('warm.defaultCell',static_area,8),'little');getter['default']=default
        if not default:raise ReadStop('warm:default-null')
        getter['status']='collected'
    except ReadStop as stop:getter['failure']=str(stop);return result
    identity=result['identity'];identity['status']='partial'
    try:
        klass=int.from_bytes(ledger.get('warm.default.objectHeader',default,8),'little');identity['classAddress']=klass
        if not klass:raise ReadStop('warm.default:class-null')
        _,info=typed_class(ledger,klass,1239,canonical,'warm.defaultClass')
        identity.update(status='collected',classInfo=info)
    except ReadStop as stop:identity['failure']=str(stop)
    return result


def collect(read,module,*,now=None):
    """Use only read(address,width)->(bytes,error); do not open or call a process."""
    if not callable(read) or type(module) is not int or not 0<module<=U64-ORDINARY_RVA-8:
        raise ValueError('A read callback and valid current module base are required')
    clock=now or utc_now;begin=clock();ledger=Ledger(read,clock)
    prior=cache.collect(ledger.cache_read,module);ledger.adopt_cache(prior)
    ordinary=ordinary_inputs(ledger,module);warm=warm_inputs(ledger,prior)
    consistency=ledger.final_repeat();consistent=consistency['status']=='repeated-unchanged'
    ordinary_ready=consistent and ordinary['status']=='collected'
    getter_ready=consistent and warm['getter']['status']=='collected'
    identity_ready=getter_ready and warm['identity']['status']=='collected'
    return dict(task='R1-RUNTIME-INPUT-COLLECTOR',moduleBase=module,collectionBeginUTC=begin,collectionEndUTC=clock(),
        status='completed' if ordinary_ready and identity_ready else 'partial',
        cacheCollector=prior,ordinaryMethods=ordinary,warmDefault=warm,records=ledger.records,inputConsistency=consistency,
        budget=dict(reads=len(ledger.records),requestedBytes=ledger.requested,maxReads=MAX_READS,maxBytes=MAX_BYTES,maxSingleRead=MAX_SINGLE,
            newFirstPassReads=ledger.new_reads,newFirstPassBytes=ledger.new_bytes,maxNewFirstPassReads=NEW_READS,maxNewFirstPassBytes=NEW_BYTES),
        ordinaryInputComplete=ordinary_ready,warmGetterInputComplete=getter_ready,warmObjectIdentityComplete=identity_ready,
        candidateGraphOnly=True,atomicSnapshot=False,naturalLookupObserved=False,naturalCallerObserved=False,naturalWriterExcluded=False,
        externalWritesExecuted=False,remoteCallExecuted=False,nativeCpuExecuted=False,newInitializedOrLoadedAllFlagsSupplied=False,
        completeColdComparer=False,completeClassInitialization=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)


def verify_sources(binary,metadata=None,*,verified_sources=None):
    """Verify each fixed dependency once and only two already-reviewed PE windows."""
    loaded={}
    for path in (THIRD,CONTRACT):
        raw=(ROOT/path).read_bytes()
        if cache.sha(raw)!=PINS[path]:raise ValueError('Fixed source changed: '+path)
        loaded[path]=raw
    parent=json.loads(loaded[THIRD]);contract=json.loads(loaded[CONTRACT])
    if parent['nextInputContract']!=contract or not parent['completeClass80Rows']:
        raise ValueError('Fixed third/next projection differs')
    pins={**parent['sourcePins'],**PINS};reused=[];verified_sources=verified_sources or {}
    for path,expected in pins.items():
        if path in loaded:continue
        if path in verified_sources:
            if verified_sources[path]!=expected:raise ValueError('Earlier source pin differs: '+path)
            reused.append(path);continue
        raw=(ROOT/path).read_bytes()
        if cache.sha(raw)!=expected:raise ValueError('Fixed source changed: '+path)
    executable=next(iter(parent['inputSHA256']))
    binary_sha=cache.sha(binary)
    if binary_sha!=parent['inputSHA256'][executable]:raise ValueError('Fixed executable differs')
    pe=cache.prior.frozen.base.PE(binary)
    windows=[]
    for start,info in ((0x140526fb0,contract['ordinaryGetter']),
            (0x157d7fe90,contract['alternativeNaturalOwner']['originalConsumer'])):
        end=int(info['end'],16);raw=binary[pe.offset(start,end-start,executable=True):pe.offset(start)+end-start]
        if cache.sha(raw)!=info['sha256']:raise ValueError('Original consumer window differs')
        windows.append(dict(start=hex(start),end=hex(end),sha256=info['sha256']))
    if (cache.MAX_READS,cache.MAX_BYTES,cache.FIRST_PASS_READS,cache.FIRST_PASS_BYTES)!=(3432,58944,1716,29472):
        raise ValueError('Frozen collector budget differs')
    return dict(sourcePins=pins,reusedSourcePins=reused,executable=executable,executableSHA256=binary_sha,windows=windows,
        metadataArgumentUsed=False,newNativeWindows=0,nativeCpuExecuted=False,fullFiveStockGateRun=False)


class OwnedMemory:
    def __init__(self,spans):
        self.values={}
        for row in spans:
            address=row['address'];raw=bytes.fromhex(row['bytesHex'])
            if type(address) is not int or not 0<address<=U64 or address+len(raw)>1<<64:
                raise ValueError('Owned source address outside uint64')
            for offset,value in enumerate(raw):
                if address+offset in self.values and self.values[address+offset]!=value:
                    raise ValueError('Contradictory owned spans')
                self.values[address+offset]=value
    def read(self,address,width):
        try:return bytes(self.values[address+i] for i in range(width)),None
        except KeyError:return b'','owned-unprovided-byte'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--owned-input',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if not all(path.resolve().is_relative_to(RAW.resolve()) for path in (args.owned_input,args.output)):
        parser.error('Owned input/output must stay in this card ignored directory')
    if any(path.exists() for path in (args.output,args.output.with_suffix('.source.py'))):parser.error('Prior owned evidence is immutable')
    raw=args.owned_input.read_bytes();fixture=json.loads(raw)
    if fixture['scope']!='owned-artificial-memory':parser.error('CLI accepts declared owned proof only')
    memory=OwnedMemory(fixture['spans'])
    binary=(args.client/next(iter(cache.prior.frozen.FIVE_INPUTS))).read_bytes();sources=verify_sources(binary)
    tick=0
    def owned_clock():
        nonlocal tick
        tick+=1
        return (datetime.datetime(2000,1,1,tzinfo=datetime.timezone.utc)+datetime.timedelta(microseconds=tick)).isoformat()
    result=collect(memory.read,fixture['module'],now=owned_clock)
    report=dict(task='R1-RUNTIME-INPUT-COLLECTOR-owned-proof',sourceSHA256=cache.sha(Path(__file__).read_bytes()),
        ownedFixtureSHA256=cache.sha(raw),fixedSources=sources,result=result,
        clock='Explicit synthetic monotonic owned clock; execution proof records actual wall clock separately.',
        liveSamplingStarted=False,naturalInputSupplied=False,windowsAdapterImplemented=False)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    args.output.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(json.dumps(dict(status=result['status'],budget=result['budget'],uniqueSpans=result['inputConsistency']['uniqueSpans'],
        ordinary=result['ordinaryInputComplete'],getter=result['warmGetterInputComplete'],identity=result['warmObjectIdentityComplete'],
        ownedOnly=True,naturalLookupObserved=False)))


if __name__=='__main__':main()
