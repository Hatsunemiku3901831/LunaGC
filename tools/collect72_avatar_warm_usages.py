"""Pure bounded Avatar warm-usage inputs; no target, native or Windows backend."""
import argparse
import datetime
import json
from pathlib import Path
import struct
import sys

ROOT=Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0,str(ROOT));__package__='tools'
from . import collect72_avatar_runtime_inputs as runtime
from . import extract_client72 as static

cache=runtime.cache
RAW=ROOT/'local/migration72/d02-resume-20261109/r1-warm-usages'
INITIALIZER='protocol/cn-7.2/avatar-initializer-observations.json'
CONTEXT='protocol/cn-7.2/avatar-deep-rgctx-observations.json'
PREVIOUS='local/migration72/d02-resume-20261109/r1-runtime-replay/main-third.json'
CALLER='local/migration72/d02-resume-20261109/r1-runtime-replay/warm-caller-source.private.json'
STRING='local/migration72/d02-resume-20261109/r3-string-comparer-input/sampling-contract.private.json'
STRING_PAIRS='local/migration72/d02-resume-20261109/r3-string-comparer-input/owner-pair-contract.private.json'
PINS={INITIALIZER:'1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf',
    CONTEXT:'a8b29f1916799b754669bb1f609fd89f9a8427466217db1460a9503d7df642e0',
    PREVIOUS:'ddd04bb05d43f7104f3561026b749c37e930175f895ca79d667c1772f5574839',
    CALLER:'d1cbd0227980db8c4c275778588ff9dbea3ac953b55b0dda4e0a4b49f917cc8b',
    STRING:'7722ea05b8fabc04527fafec20585b31f6a3d985ab683037c05e90b15e9df20a',
    STRING_PAIRS:'bf03a9d29054fba2f467c2a54220ee966ad964b4a937ba91d2035ab243716291',
    'tools/collect72_avatar_runtime_inputs.py':'2c9ba14c652823a71771a2fee385de44a75161ad9bba947d263b4164aa39fffa',
    'tools/replay72_avatar_runtime_inputs.py':'5d4bd541ca074e081d1f832acd4e22766213588e57b11dcff697f9bcaf599780',
    'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d'}
ROOTS=dict(listU32=0x588ca20,listEmptyItems=0x5b74628,hashSetU32=0x5897cf0,
    u32Comparer=0x594fd78,hashSetEnum=0x58fd7c8,enumCarrier=0x5a30968,stringGetter=0x596c848)
DEFAULT_FAMILY={1235:'GenericEqualityComparer`1',1237:'ObjectEqualityComparer`1',1239:'EnumEqualityComparer`1',
    1240:'SByteEnumEqualityComparer`1',1241:'ShortEnumEqualityComparer`1',1242:'LongEnumEqualityComparer`1'}
STRING_EQUALS={1235:(1235,10296),1237:(1237,10310),1239:(1239,10324),
    1240:(1239,10324),1241:(1239,10324),1242:(1242,10333),1243:(1243,10339)}
MAX_READS,MAX_BYTES,MAX_SINGLE=5276,96608,208
NEW_READS,NEW_BYTES=64,4096
ReadStop=runtime.ReadStop


class Ledger(runtime.Ledger):
    """Keep frozen accounting/overlap/error semantics with this card's budget."""
    def get(self,name,address,width,phase='new'):
        reason=None
        if not 0<width<=MAX_SINGLE or len(self.records)>=MAX_READS or self.requested+width>MAX_BYTES:reason='collector-budget'
        elif phase=='new' and (self.new_reads>=NEW_READS or self.new_bytes+width>NEW_BYTES):reason='new-input-budget'
        if reason:
            self.denied.append(dict(name=name,address=address,width=width,phase=phase,reason=reason));raise ReadStop(reason)
        if phase=='new':self.new_reads+=1;self.new_bytes+=width
        if not 0<address<=runtime.U64 or address+width>1<<64:
            raw,error=b'','address-outside-uint64';meta=dict(beginUTC=self.now(),endUTC=self.now(),backendCalled=False)
        else:raw,error,meta=self.backend_read(address,width)
        row,conflict=self.append(name,phase,address,width,raw,error,meta)
        if row['error'] is not None:raise ReadStop(name+':unreadable')
        if conflict:raise ReadStop(name+':overlap-disagrees')
        return raw


def type_semantics(raw,target):
    data,bits=struct.unpack_from('<QI',raw);kind=(bits>>16)&255
    expected={'u32':9,'string':14,'enum':17}[target]
    if kind!=expected or bits&0x40000000 or (target=='enum' and data&0xffffffff!=20060):
        raise ReadStop('argument-type-mismatch')
    return dict(kind=kind,byRef=False,dataObserved=hex(data),dataUsedByOriginal=target=='enum',
        semanticName={'u32':'UInt32','string':'String','enum':'enum20060'}[target],
        typeHash=hex(kind*cache.C if target!='enum' else ((17*cache.C)*cache.C+20060)&runtime.U64))


class UsageGraph:
    def __init__(self,ledger,module,prior):
        self.ledger,self.module,self.prior=ledger,module,prior;self.contexts={}
        self.canonical=prior['canonicalContext']['pointer'] if prior['status']=='completed' else None

    def qword(self,label,address):return int.from_bytes(self.ledger.get(label,address,8),'little')

    def root(self,label):
        pointer=self.qword(label+'.usage',self.module+ROOTS[label])
        if not pointer:raise ReadStop(label+':usage-null')
        return pointer

    def context(self,pointer,target,label):
        if not pointer:raise ReadStop(label+':context-null')
        if target=='enum':
            if self.canonical is None or pointer!=self.canonical:raise ReadStop(label+':canonical-context-mismatch')
            return dict(pointer=pointer,kind=17,data=20060,reusedFrozenCanonical=True)
        if pointer==self.canonical:raise ReadStop(label+':primitive-context-is-enum')
        if pointer in self.contexts:
            known=self.contexts[pointer]
            if known['argument']['kind']!={'u32':9,'string':14}[target]:raise ReadStop(label+':cached-argument-kind-mismatch')
            return known
        raw=self.ledger.get(label+'.instance',pointer,16);count,array=struct.unpack('<I4xQ',raw)
        if count!=1:raise ReadStop(label+':argument-count-mismatch')
        if not array:raise ReadStop(label+':argument-array-null')
        descriptor=self.qword(label+'.argument0',array)
        if not descriptor:raise ReadStop(label+':argument-type-null')
        typ=type_semantics(self.ledger.get(label+'.type0',descriptor,16),target)
        result=dict(pointer=pointer,count=1,array=array,descriptor=descriptor,argument=typ,
            argumentInstanceHash=hex((cache.C+int(typ['typeHash'],16))&runtime.U64),internCacheHitObserved=False)
        self.contexts[pointer]=result;return result

    def klass(self,pointer,definition,target,label,expected_context=None):
        header=self.ledger.get(label+'.header',pointer,208);bits=struct.unpack_from('<I',header,0x18)[0]
        if (bits>>16)&255!=21 or bits&0x40000000:raise ReadStop(label+':class-kind-mismatch')
        if not header[0xc8]&1:raise ReadStop(label+':warm-flag-clear')
        generic=struct.unpack_from('<Q',header,0x68)[0]
        if not generic:raise ReadStop(label+':generic-class-null')
        raw=self.ledger.get(label+'.genericClass',generic,32);actual,context,method,backref=struct.unpack('<I4xQQQ',raw)
        if (definition is not None and actual!=definition) or (definition is None and actual not in DEFAULT_FAMILY):
            raise ReadStop(label+':definition-mismatch')
        if method or backref!=pointer:raise ReadStop(label+':method-context-or-backref-mismatch')
        if expected_context is not None and context!=expected_context:raise ReadStop(label+':context-pointer-mismatch')
        args=self.context(context,target,label+'.argument')
        return dict(address=pointer,definition=actual,genericClass=generic,context=context,
            methodInstance=method,backref=backref,arguments=args,headerHex=header.hex(),
            initializedFlagObserved=True,rgctx=struct.unpack_from('<Q',header,0x30)[0],staticArea=struct.unpack_from('<Q',header,0x38)[0],
            observedDefaultName=DEFAULT_FAMILY.get(actual) if definition is None else None)

    def allocation_class(self,label,definition,target):
        result=dict(status='partial',failure=None,kind='class input only; managed allocator readset/execute not closed')
        try:
            pointer=self.root(label);result['pointer']=pointer
            result['class']=self.klass(pointer,definition,target,label);result['status']='collected'
        except ReadStop as stop:result['failure']=str(stop)
        return result

    def empty_items(self):
        result=dict(status='partial',failure=None,arrayIdentityProved=False,arrayBodyRead=False)
        try:result['pointer']=self.root('listEmptyItems');result['status']='collected'
        except ReadStop as stop:result['failure']=str(stop)
        return result

    def u32_default(self):
        result=dict(status='partial',failure=None,identity=dict(status='not-read'),factorySelectionProved=False)
        try:
            pointer=self.root('u32Comparer');owner=self.klass(pointer,1234,'u32','u32Comparer');result['owner']=owner
            if not owner['staticArea']:raise ReadStop('u32Comparer:static-area-null')
            default=self.qword('u32Comparer.defaultCell',owner['staticArea']);result['default']=default
            if not default:raise ReadStop('u32Comparer:default-null')
            result['getterStatus']='collected'
        except ReadStop as stop:result['failure']=str(stop);return result
        try:
            derived=self.qword('u32Comparer.defaultObject',default)
            if not derived:raise ReadStop('u32Comparer:default-class-null')
            identity=self.klass(derived,None,'u32','u32Comparer.defaultClass',owner['context'])
            result['identity']=dict(status='collected',classInfo=identity,
                source='Actual definition checked against fixed parameter0 EqualityComparer inheritance; no enum-derived default assumption.')
            result['status']='collected'
        except ReadStop as stop:result['identity']=dict(status='partial',failure=str(stop));result['failure']=str(stop)
        return result

    def enum_carrier(self,allocation):
        result=dict(status='partial',failure=None)
        try:
            pointer=self.root('enumCarrier');raw=self.ledger.get('enumCarrier.methodInfo',pointer,56)
            owner,index,code=struct.unpack_from('<Q',raw)[0],struct.unpack_from('<I',raw,24)[0],struct.unpack_from('<Q',raw,8)[0]
            result.update(methodInfo=pointer,methodInfoHex=raw.hex(),observedCarrierIndex=index,carrierFunctionPointer=code)
            if not owner:raise ReadStop('enumCarrier:owner-null')
            if allocation['status']=='collected':
                if owner!=allocation['class']['address']:raise ReadStop('enumCarrier:owner-differs-from-allocation-class')
                info=allocation['class'];result['ownerReusedFromAllocation']=True
            else:info=self.klass(owner,2147,'enum','enumCarrier.owner')
            result['owner']=info
            if not info['rgctx']:raise ReadStop('enumCarrier:rgctx-null')
            slots=self.ledger.get('enumCarrier.getterAndCtorSlots',info['rgctx']+0x10,16);getter,ctor=struct.unpack('<QQ',slots)
            result.update(getterMethodInfo=getter,constructorMethodInfo=ctor)
            selected=next((x for x in self.prior['methods'] if x['index']==10288 and x['status']=='hit'),None)
            if selected is None or getter!=selected['pointer']:raise ReadStop('enumCarrier:getter-slot-mismatch')
            if not ctor:raise ReadStop('enumCarrier:constructor-slot-null')
            result['status']='slots-collected'
        except ReadStop as stop:result['failure']=str(stop)
        return result

    def constructor(self,carrier,warm):
        if carrier['status']!='slots-collected':return
        if warm['getter']['status']!='collected':carrier['failure']='enumCarrier:warm-getter-input-incomplete';return
        try:
            raw=self.ledger.get('enumCarrier.constructorMethodInfo',carrier['constructorMethodInfo'],56)
            owner,code=struct.unpack_from('<QQ',raw);index=struct.unpack_from('<I',raw,24)[0]
            carrier['constructor']=dict(bytesHex=raw.hex(),owner=owner,methodDefinition=index,functionPointer=code,
                functionPointerRewritten=False,executableTargetValidated=False)
            if index!=15591:raise ReadStop('enumCarrier:constructor-index-mismatch')
            if owner!=carrier['owner']['address']:raise ReadStop('enumCarrier:constructor-owner-mismatch')
            if not code:raise ReadStop('enumCarrier:constructor-function-null')
            carrier['status']='collected'
        except ReadStop as stop:carrier['status']='partial';carrier['failure']=str(stop)

    def string_class(self,pointer,label,context,*,known=None):
        """The finite String parent templates; never follow a new context."""
        header=known if known is not None else self.ledger.get(label+'.header',pointer,208)
        data,bits=struct.unpack_from('<QI',header,0x10);kind=(bits>>16)&255
        if bits&0x40000000 or kind not in (18,21):raise ReadStop(label+':class-kind-mismatch')
        if not header[0xc8]&1:raise ReadStop(label+':warm-flag-clear')
        generic=struct.unpack_from('<Q',header,0x68)[0]
        if kind==18:
            if data!=1243 or generic:raise ReadStop(label+':ordinary-definition-mismatch')
            actual=1243
        else:
            if not generic:raise ReadStop(label+':generic-class-null')
            raw=self.ledger.get(label+'.genericClass',generic,32);actual,observed,method,backref=struct.unpack('<I4xQQQ',raw)
            if actual not in DEFAULT_FAMILY and actual!=1234:raise ReadStop(label+':definition-mismatch')
            if observed!=context or method or backref!=pointer:raise ReadStop(label+':context-or-backref-mismatch')
        return dict(address=pointer,definition=actual,genericClass=generic,context=context,
            contextSource='actual full pointer' if generic else 'fixed ordinary1243 parent EqualityComparer<String>',
            headerKind=kind,headerHex=header.hex(),initializedFlagObserved=True)

    def optional_string(self):
        result=dict(status='partial',failure=None,getterStatus='not-collected',identityStatus='not-collected',
            equalsStatus='not-collected',factorySelectionProved=False,comparisonExecuted=False,executableTargetValidated=False)
        try:
            pointer=self.root('stringGetter');raw=self.ledger.get('string.getterMethodInfo',pointer,56)
            owner,code=struct.unpack_from('<QQ',raw);index=struct.unpack_from('<I',raw,24)[0]
            result['getterMethodInfo']=dict(pointer=pointer,bytesHex=raw.hex(),owner=owner,functionPointer=code,methodDefinition=index)
            if index!=10288 or not owner:raise ReadStop('string:getter-index-or-owner-mismatch')
            info=self.klass(owner,1234,'string','string.owner');result['owner']=info
            if not info['rgctx'] or not info['staticArea']:raise ReadStop('string:rgctx-or-static-null')
            selected=self.qword('string.comparerClassSlot0',info['rgctx']+0x10);result['classSlot0']=selected
            if selected!=owner:raise ReadStop('string:class-slot-owner-mismatch')
            default=self.qword('string.defaultCell',info['staticArea']);result['default']=default
            if not default:raise ReadStop('string:default-null')
            result['getterStatus']='collected'
            derived=self.qword('string.defaultObject',default)
            if not derived:raise ReadStop('string:default-class-null')
            identity=self.string_class(derived,'string.derived',info['context']);result['identity']=identity
            if identity['definition'] not in STRING_EQUALS:raise ReadStop('string:abstract-or-unknown-derived')
            result['identityStatus']='collected';header=bytes.fromhex(identity['headerHex'])
            count=struct.unpack_from('<H',header,0xbe)[0];result['vtableCount']=count
            if count<9:raise ReadStop('string:equals-slot-outside-vtable')
            function=self.qword('string.equalsFunction',derived+0x110);result['equalsFunction']=function
            if not function:raise ReadStop('string:equals-function-null')
            companion_slot=derived+0x110+count*8;mi=self.qword('string.equalsMethodInfoPointer',companion_slot)
            result.update(equalsMethodInfoSlot=companion_slot,equalsMethodInfo=mi)
            if not mi:raise ReadStop('string:equals-method-info-null')
            raw=self.ledger.get('string.equalsMethodInfo',mi,56);miowner,micode=struct.unpack_from('<QQ',raw)
            method=struct.unpack_from('<I',raw,24)[0]
            result['equals']=dict(bytesHex=raw.hex(),owner=miowner,methodDefinition=method,functionPointer=micode,
                functionEqualsSlot=function==micode,functionPointerRewritten=False)
            if not miowner:raise ReadStop('string:equals-owner-null')
            if miowner==derived:miinfo=identity
            elif miowner==owner:
                miinfo=dict(address=owner,definition=1234,context=info['context'],headerHex=info['headerHex'])
            else:miinfo=self.string_class(miowner,'string.equalsOwner',info['context'])
            result['equals']['ownerClass']=miinfo
            if (miinfo['definition'],method)!=STRING_EQUALS[identity['definition']]:raise ReadStop('string:equals-owner-parent-or-index-mismatch')
            result['equalsStatus']='collected';result['status']='collected'
        except ReadStop as stop:result['failure']=str(stop)
        return result


def collect(read,module,*,now=None):
    if not callable(read) or type(module) is not int or not 0<module<=runtime.U64-0x5bc8f20-8:
        raise ValueError('A read callback and valid same-epoch module are required')
    clock=now or runtime.utc_now;begin=clock();ledger=Ledger(read,clock)
    prior=cache.collect(ledger.cache_read,module);ledger.adopt_cache(prior)
    graph=UsageGraph(ledger,module,prior)
    list_class=graph.allocation_class('listU32',1255,'u32');items=graph.empty_items()
    u32_class=graph.allocation_class('hashSetU32',2147,'u32');u32_default=graph.u32_default()
    enum_class=graph.allocation_class('hashSetEnum',2147,'enum');carrier=graph.enum_carrier(enum_class)
    warm=runtime.warm_inputs(ledger,prior);graph.constructor(carrier,warm)
    main_end=len(ledger.records);main_denied=len(ledger.denied);string=graph.optional_string()
    consistency=ledger.final_repeat()
    main_repeats=[r for r in consistency['finalRepeats'] if r['firstOrdinal']<=main_end]
    consistent=not ledger.conflicts and not main_denied and all(r['error'] is None for r in ledger.records[:main_end]) and all(
        r['status']=='unchanged' for r in main_repeats)
    string_ready=consistency['status']=='repeated-unchanged' and string['status']=='collected'
    ready=dict(listClass=consistent and list_class['status']=='collected',listEmptyItemsSlot=consistent and items['status']=='collected',
        hashSetU32Class=consistent and u32_class['status']=='collected',u32Default=consistent and u32_default['status']=='collected',
        hashSetEnumClass=consistent and enum_class['status']=='collected',enumCaller=consistent and carrier['status']=='collected',
        enumWarmGetter=consistent and warm['getter']['status']=='collected',enumWarmIdentity=consistent and warm['identity']['status']=='collected')
    return dict(task='R1-AVATAR-WARM-USAGES',moduleBase=module,collectionBeginUTC=begin,collectionEndUTC=clock(),
        status='completed' if all(ready.values()) else 'partial',cacheCollector=prior,listU32=list_class,listEmptyItems=items,
        hashSetU32=u32_class,u32Default=u32_default,hashSetEnum=enum_class,enumCaller=carrier,enumWarm=warm,
        candidateInputs=ready,optionalString=string,optionalStringCandidate=string_ready,records=ledger.records,inputConsistency=consistency,
        mainInputConsistency=dict(status='repeated-unchanged' if consistent else 'failed',firstPassEndOrdinal=main_end,
            finalSpans=len(main_repeats),scope='All main/cache successful spans repeated after main and optional String acquisition; any overlap conflict rejects both.'),
        budget=dict(reads=len(ledger.records),requestedBytes=ledger.requested,maxReads=MAX_READS,maxBytes=MAX_BYTES,maxSingleRead=MAX_SINGLE,
            newFirstPassReads=ledger.new_reads,newFirstPassBytes=ledger.new_bytes,maxNewFirstPassReads=NEW_READS,maxNewFirstPassBytes=NEW_BYTES,
            graphUpperReads=59,graphUpperBytes=3216,mainGraphUpperReads=42,mainGraphUpperBytes=2296,
            optionalStringUpperReads=17,optionalStringUpperBytes=920),
        candidateGraphOnly=True,atomicSnapshot=False,naturalLookupObserved=False,naturalCallerObserved=False,naturalWriterExcluded=False,
        externalWritesExecuted=False,remoteCallExecuted=False,nativeCpuExecuted=False,initializedOrLoadedAllAssumed=False,
        prototypePointersModified=False,ordinaryFourMethodsRequested=False,fullAvatarAllocationVerified=False,
        completeAvatarConstructor=False,completeAvatarHandler=False,g2Verified=False,d02Complete=False)


def metadata_families(binary,metadata,startup):
    """Resolve only finite fixed parent rows and their parameter0 references."""
    pe=static.PE(binary);header=pe.offset(static.HEADER_VA)
    table=static.BODY+((static.number(binary,header+0x64)+0xa7932555)&0xffffffff)
    size=static.number(binary,header+0xbc)^0x1e6fc854
    names=static.Names(metadata,static.BODY+(static.number(binary,header+0x70)^0x18202ccc))
    registration=pe.offset(static.METADATA_REGISTRATION,0x90)
    references=static.number(binary,registration,'Q');instances=static.number(binary,registration+0x70,'Q')
    reference_count=(static.number(binary,registration+0x8c)+0xce67355c)&0xffffffff
    generic=(static.number(binary,header+0xd0)+0xe1492096)&0xffffffff
    generic_bytes=(static.number(binary,header+0x44)+0xe42e197f)&0xffffffff
    parameter=(static.number(binary,header+0x94)+0xd97e8a5c)&0xffffffff
    definitions={};descriptors={}
    def definition(index):
        if index in definitions:return definitions[index]
        if not 0<=index<size//70:raise ValueError('Selected fixed definition outside table')
        file=table+index*70;static.span(metadata,file,70);raw=metadata[file:file+70]
        result=dict(index=index,name=names.decode((static.number(raw,0x18)+0xa285a06d)&0xffffffff),
            namespace=names.decode(static.number(raw,0x14)^0x149e96f1),fileOffset=hex(file),bytesHex=raw.hex(),sha256=cache.sha(raw))
        definitions[index]=result;return result
    def descriptor(pointer,depth=0):
        if pointer in descriptors:return descriptors[pointer]
        if depth>3 or pointer<references or (pointer-references)%16 or (pointer-references)//16>=reference_count:
            raise ValueError('Selected fixed parent reference outside bounded source')
        file=pe.offset(pointer,16);raw=binary[file:file+16];data,bits=struct.unpack_from('<QI',raw);kind=(bits>>16)&255
        result=dict(rva=hex(pointer-pe.base),bytesHex=raw.hex(),sha256=cache.sha(raw),kind=kind,data=data,byRef=bool(bits&0x40000000))
        descriptors[pointer]=result
        if kind==21:
            if data*8+8>generic_bytes:raise ValueError('Selected startup pair outside table')
            pair=generic+data*8;instance,owner=struct.unpack_from('<II',startup,pair)
            raw_inst=binary[pe.offset(instances+instance*16,16):pe.offset(instances+instance*16)+16]
            count,array=struct.unpack('<QQ',raw_inst)
            if count!=1:raise ValueError('Selected parent is not single-argument')
            result.update(definition=definition(owner),startupPairFile=hex(pair),startupPairHex=startup[pair:pair+8].hex(),
                instance=instance,instanceRVA=hex(instances+instance*16-pe.base),instanceHex=raw_inst.hex(),
                argument=descriptor(static.number(binary,pe.offset(array,8),'Q'),depth+1))
        elif kind in (19,30):
            file=static.BODY+parameter+data*14;static.span(metadata,file,14);param=metadata[file:file+14]
            seed=((data*0x42bb72a)&0xffffffff)>>12;encoded=static.number(param,6,'H')
            result.update(parameterFile=hex(file),parameterHex=param.hex(),parameterSHA256=cache.sha(param),
                position=(((seed*0xffffe680+0x5c80)&0xffffffff)^((encoded+0xffffbe10)&0xffffffff))&0xffff)
        return result
    paths={}
    for index,name in DEFAULT_FAMILY.items():
        if definition(index)['name']!=name:raise ValueError('Fixed Default family name differs')
        path=[];current=index
        while current!=1234:
            if current in path or len(path)>=3:raise ValueError('Selected inheritance chain differs')
            path.append(current);raw=bytes.fromhex(definition(current)['bytesHex'])
            reference=(static.number(raw,0)+0xa0a35767)&0xffffffff
            parent=descriptor(references+reference*16);argument=parent.get('argument',{})
            if parent['kind']!=21 or argument.get('kind')!=19 or argument.get('position')!=0 or parent['byRef']:
                raise ValueError('Default family is not the fixed parameter0 parent chain')
            current=parent['definition']['index']
        paths[str(index)]=path+[1234]
    for index,name in ((1234,'EqualityComparer`1'),(1255,'List`1'),(2147,'HashSet`1')):
        if definition(index)['name']!=name:raise ValueError('Fixed warm usage definition name differs')
    ordinary=definition(1243)
    if ordinary['name']!='InternalStringComparer':raise ValueError('Fixed ordinary String comparer differs')
    reference=(static.number(bytes.fromhex(ordinary['bytesHex']),0)+0xa0a35767)&0xffffffff
    parent=descriptor(references+reference*16)
    if parent['kind']!=21 or parent['definition']['index']!=1234 or parent['argument']['kind']!=14 or parent['argument']['byRef']:
        raise ValueError('Fixed ordinary String parent differs')
    method_base=static.BODY+(static.number(binary,header+0xb0)^0x51324a1f);methods={}
    for owner,index in ((1234,10289),*sorted(set(STRING_EQUALS.values()))):
        file=method_base+index*26;static.span(metadata,file,26);raw=metadata[file:file+26]
        key=(((index*0x1639)^0x5e01c03b)*0x106b250c17f34ccb+0x069ffdcbe218faf4)&runtime.U64
        name=names.decode((static.number(raw,8)-key+0xde01c441)&0xffffffff)
        flags,slot=static.method_flags_slot(index,static.number(raw,16,'H'),static.number(raw,18,'H'))
        ownerraw=bytes.fromhex(definition(owner)['bytesHex']);first=static.number(ownerraw,12)^0x5abb91bd
        count=(static.number(ownerraw,0x3a,'H')+0xbf1a)&0xffff
        if not first<=index<first+count or name!='Equals' or slot!=8 or flags!=(0x5c6 if owner==1234 else 0xc6):
            raise ValueError('Fixed Equals owner/method/flags/slot differs')
        methods[str(owner)]=dict(index=index,flags=hex(flags),name=name,slot=slot,fileOffset=hex(file),bytesHex=raw.hex(),sha256=cache.sha(raw))
    return dict(definitions={str(k):v for k,v in definitions.items()},parentDescriptors=list(descriptors.values()),
        parameter0Paths=paths,ordinaryStringParent=parent,equalsMethods=methods,wholeGenericGraphScanned=False,runtimeParentPointeesRead=False)


def verify_sources(binary,metadata,startup,*,verified_sources=None):
    loaded={};pins={}
    for path in (PREVIOUS,INITIALIZER,CONTEXT,CALLER,STRING,STRING_PAIRS):
        raw=(ROOT/path).read_bytes()
        if cache.sha(raw)!=PINS[path]:raise ValueError('Fixed warm usage source differs: '+path)
        loaded[path]=raw;pins[path]=PINS[path]
    previous=json.loads(loaded[PREVIOUS]);initial=json.loads(loaded[INITIALIZER])['declarations'];context=json.loads(loaded[CONTEXT])
    string=json.loads(loaded[STRING]);pairs=json.loads(loaded[STRING_PAIRS])
    if int(string['primaryRoot']['moduleRVA'],16)!=ROOTS['stringGetter'] or pairs['sourceContractSHA256']!=PINS[STRING]:
        raise ValueError('Fixed String usage/contract differs')
    if {int(k):(v[0]['ownerDefinition'],v[0]['methodDefinition']) for k,v in pairs['allowedCompanionOwnersByActualDerived'].items()}!=STRING_EQUALS:
        raise ValueError('Fixed String owner/companion table differs')
    find_path=pairs['sourceFindEntry']['path'];loaded[find_path]=(ROOT/find_path).read_bytes()
    if cache.sha(loaded[find_path])!=pairs['sourceFindEntry']['sha256']:raise ValueError('Fixed FindEntry source differs')
    for group in (previous['sourcePins'],{p:h for p,h in string['sourcePins'].items() if not p.startswith('GAME_PATH/')},
            {find_path:pairs['sourceFindEntry']['sha256']},PINS):
        for path,expected in group.items():
            if path in pins and pins[path]!=expected:raise ValueError('Conflicting fixed source pins: '+path)
            pins[path]=expected
    reused=[];verified_sources=verified_sources or {}
    for path,expected in pins.items():
        if path in loaded:continue
        if path in verified_sources:
            if verified_sources[path]!=expected:raise ValueError('Earlier source pin differs: '+path)
            reused.append(path);continue
        if cache.sha((ROOT/path).read_bytes())!=expected:raise ValueError('Fixed warm usage dependency differs: '+path)
    for (name,expected),raw in zip(static.HASHES.items(),(binary,metadata,startup)):
        if cache.sha(raw)!=expected:raise ValueError('Fixed warm usage input differs: '+name)
    for label,definition in (('listU32',1255),('hashSetU32',2147),('u32Comparer',1234),('hashSetEnum',2147)):
        typ=initial['referencedTypes'][hex(0x140000000+ROOTS[label])]
        expected_kind='valuetype' if label=='hashSetEnum' else 'uint32'
        if typ['typeDefinition']!=definition or len(typ['arguments'])!=1 or typ['arguments'][0]['kind']!=expected_kind:
            raise ValueError('Fixed usage type differs: '+label)
        if typ['arguments'][0]['byRef'] or (label=='hashSetEnum' and typ['arguments'][0]['typeDefinition']!=20060):
            raise ValueError('Fixed usage argument differs: '+label)
    if int(initial['enumContextCell'],16)!=0x140000000+ROOTS['enumCarrier'] or hex(0x140000000+ROOTS['listEmptyItems']) not in initial['emptyArrayCells']:
        raise ValueError('Original fixed caller usage differs')
    selected=[r for r in context['selected'] if r['owner']==2147 and r['slot']==1]
    if len(selected)!=1 or selected[0]['methodDefinition']!=15591 or selected[0]['actualInstance']!=5917:
        raise ValueError('Original constructor RGCTX word differs')
    pe=static.PE(binary);windows=[]
    specs=[(int(k,16),int(v['end'],16),v['nativeBytesSHA256']) for k,v in initial['functions'].items()
        if k in ('0x15183fe60','0x157d97690','0x157d7fe90')]
    specs.extend((entry,*cache.WINDOWS[entry]) for entry in (0x1404f0740,0x1404f0820))
    specs.extend((int(w['start'],16),int(w['end'],16),w['sha256']) for w in string['newWindows'])
    helper=string['reusedColdHelper'];specs.append((int(helper['start'],16),int(helper['end'],16),helper['sha256']))
    find=json.loads(loaded[find_path])
    specs.extend((int(w['start'],16),int(w['end'],16),w['sha256']) for w in find['windows'])
    for start,end,expected in specs:
        file=pe.offset(start,end-start,executable=True)
        if cache.sha(binary[file:file+end-start])!=expected:raise ValueError('Original known consumer window differs')
        windows.append(dict(start=hex(start),end=hex(end),sha256=expected))
    families=metadata_families(binary,metadata,startup)
    for derived,template in string['derivedDefinitionTemplates'].items():
        if int(derived)==1234:continue
        chain=[row['definition'] for row in template['parentChain']]+[1234]
        if int(derived)!=1243 and chain!=families['parameter0Paths'][derived]:raise ValueError('String finite parent template differs')
    return dict(sourcePins=pins,reusedSourcePins=reused,inputSHA256=static.HASHES,knownWindows=windows,
        defaultFamilies=families,stringCompanionPairs=STRING_EQUALS,stringSourceContractSHA256=PINS[STRING],
        inheritedFiveInputPins={p:h for p,h in string['sourcePins'].items() if p.startswith('GAME_PATH/')},
        constructorDefinition=15591,newNativeWindows=0,reusedR3StringWindow=True,
        nativeCpuExecuted=False,fullFiveStockGateRun=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--owned-input',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    if not all(p.resolve().is_relative_to(RAW.resolve()) for p in (args.output,args.owned_input)):
        parser.error('Owned input/output must stay in this card ignored directory')
    if any(p.exists() for p in (args.output,args.output.with_suffix('.source.py'))):parser.error('Prior owned evidence is immutable')
    fixture_raw=args.owned_input.read_bytes();fixture=json.loads(fixture_raw)
    if fixture['scope']!='owned-artificial-memory':parser.error('CLI accepts explicitly owned proof only')
    inputs=[(args.client/name).read_bytes() for name in static.HASHES];sources=verify_sources(*inputs)
    memory=runtime.OwnedMemory(fixture['spans']);tick=0
    def owned_clock():
        nonlocal tick
        tick+=1
        return (datetime.datetime(2000,1,1,tzinfo=datetime.timezone.utc)+datetime.timedelta(microseconds=tick)).isoformat()
    result=collect(memory.read,fixture['module'],now=owned_clock)
    report=dict(task='R1-AVATAR-WARM-USAGES-owned-proof',sourceSHA256=cache.sha(Path(__file__).read_bytes()),
        fixtureSHA256=cache.sha(fixture_raw),fixedSources=sources,result=result,ownedArtificialInput=True,
        clock='Synthetic monotonic owned clock; real execution UTC in separate proof',liveSamplingStarted=False,
        windowsAdapterImplemented=False,nativeCpuExecuted=False)
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    args.output.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    print(json.dumps(dict(status=result['status'],budget=result['budget'],inputs=result['candidateInputs'],ownedOnly=True)))


if __name__=='__main__':main()
