"""Bounded independent String consumer candidate graph; pure read callback only."""
import json
import struct
from pathlib import Path
from . import collect72_avatar_warm_usages as warm
from . import collect72_avatar_runtime_inputs as runtime

ROOT=Path(__file__).resolve().parents[1]
ReadStop=runtime.ReadStop
STRING_EQUALS=warm.STRING_EQUALS
MAX_READS,MAX_BYTES,MAX_SINGLE=54,2704,208
NEW_READS,NEW_BYTES=27,1352

class Ledger(warm.Ledger):
    def get(self,name,address,width,phase='new'):
        reason=None
        if not 0<width<=MAX_SINGLE or len(self.records)>=MAX_READS or self.requested+width>MAX_BYTES: reason='collector-budget'
        elif phase=='new' and (self.new_reads>=NEW_READS or self.new_bytes+width>NEW_BYTES): reason='new-input-budget'
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

class Graph(warm.UsageGraph):
    def __init__(self,ledger,module):
        super().__init__(ledger,module,{'status':'partial'})

    def string_successor(self, pointer):
        result=dict(status='partial',failure=None,getterStatus='not-collected',identityStatus='not-collected',
            equalsStatus='not-collected',factorySelectionProved=False,comparisonExecuted=False,executableTargetValidated=False)
        try:
            raw=self.ledger.get('string.getterMethodInfo',pointer,56)
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
    if not callable(read) or type(module) is not int or not 0<module<=runtime.U64-0x59ae108-8 or (now is not None and not callable(now)):
        raise ValueError('A read callback, clock and valid module are required')
    clock=now or runtime.utc_now;begin=clock();ledger=Ledger(read,clock);graph=Graph(ledger,module)
    carrier=dict(status='partial',failure=None);successor=None
    try:
        pointer=graph.qword('tryGet.usage',module+0x59ae108);carrier['tryGetMI']=pointer
        if not pointer:raise ReadStop('tryGet:usage-null')
        raw=ledger.get('tryGet.methodInfo',pointer,56);owner=struct.unpack_from('<Q',raw)[0]
        if not owner or struct.unpack_from('<I',raw,24)[0]!=10200:raise ReadStop('tryGet:index-or-owner-mismatch')
        header=ledger.get('dictionary.owner',owner,208);bits=struct.unpack_from('<I',header,0x18)[0]
        if (bits>>16)&255!=21 or bits&0x40000000:raise ReadStop('dictionary:class-kind-mismatch')
        rgctx,generic=struct.unpack_from('<Q',header,0x30)[0],struct.unpack_from('<Q',header,0x68)[0]
        if not rgctx or not generic:raise ReadStop('dictionary:rgctx-or-generic-null')
        raw=ledger.get('dictionary.genericClass',generic,32);definition,inst,method,backref=struct.unpack('<I4xQQQ',raw)
        if definition!=1222 or method or backref!=owner or not inst:raise ReadStop('dictionary:generic-class-mismatch')
        raw=ledger.get('dictionary.instance',inst,16);count,array=struct.unpack('<I4xQ',raw)
        if count!=2 or not array:raise ReadStop('dictionary:instance-mismatch')
        args=struct.unpack('<QQ',ledger.get('dictionary.arguments',array,16))
        if not all(args):raise ReadStop('dictionary:argument-null')
        for label,address,kind in zip(('string','uint64'),args,(14,11)):
            raw=ledger.get('dictionary.'+label,address,16);bits=struct.unpack_from('<I',raw,8)[0]
            if (bits>>16)&255!=kind or bits&0x40000000:raise ReadStop('dictionary:'+label+'-type-mismatch')
        find=graph.qword('dictionary.findEntrySlot',rgctx+0x78);carrier['findEntryMI']=find
        if find:
            raw=ledger.get('dictionary.findEntryMI',find,56)
            if struct.unpack_from('<Q',raw)[0]!=owner or struct.unpack_from('<I',raw,24)[0]!=10194:raise ReadStop('dictionary:find-entry-mismatch')
        getter=graph.qword('dictionary.getterSlot',rgctx+0x20);carrier.update(owner=owner,rgctx=rgctx,getterMI=getter)
        if not find or not getter:
            prefix=ledger.get('dictionary.rgctxPrefix',rgctx,16);prefix_count,definition_ptr=struct.unpack('<I4xQ',prefix)
            carrier['lazyDiagnostic']=dict(count=prefix_count,definitionPointer=definition_ptr)
            if prefix_count!=37 or not definition_ptr:raise ReadStop('dictionary:rgctx-prefix-mismatch')
            words=[int.from_bytes(ledger.get('dictionary.definition'+str(slot),definition_ptr+slot*4,4),'little') for slot in (2,13)]
            carrier['lazyDiagnostic']['words']=words
            if words!=[0x0c08c554,0x0c08c559]:raise ReadStop('dictionary:definition-words-mismatch')
            raise ReadStop('dictionary:lazy-slot-null')
        carrier['status']='collected';successor=graph.string_successor(getter)
    except ReadStop as stop:carrier['failure']=str(stop)
    consistency=ledger.final_repeat()
    ready=carrier['status']=='collected' and successor is not None and successor['status']=='collected' and consistency['status']=='repeated-unchanged'
    return dict(task='E-01/R3-ITEM-ENTRY-F-03d3f4d2-string',status='candidate' if ready else 'partial',
        moduleBase=module,collectionBeginUTC=begin,collectionEndUTC=clock(),carrier=carrier,string=successor,
        records=ledger.records,inputConsistency=consistency,budget=dict(reads=len(ledger.records),requestedBytes=ledger.requested,
        firstPassReads=ledger.new_reads,firstPassBytes=ledger.new_bytes,maxReads=MAX_READS,maxBytes=MAX_BYTES,maxSingleRead=MAX_SINGLE),
        candidateGraphOnly=True,nonAtomic=True,naturalCallerFalse=True,noWrites=True,noRemote=True,noNativeCPU=True,
        completeComparerFalse=True,configAbilityFalse=True,D02False=True)


CONTRACT_DIR=ROOT/'local/migration72/d02-parallel-20261109/a-avatar-string'
CONTRACT_PINS={
    'approved-string-consumer-contract.private.json':'f7fe7bc791a929f1ad504213d30e3e59008671f9f251ad5d53fd0c051651b7e9',
    'string-consumer-contract.private.json':'9e84014cc0a1258090c69e67c3f57074a1ead249e431882c55a500f57e367867',
    'next-input-contract.private.json':'7730167bb88a485124149efe3f6129fdfdd2f984a924bb46f6c354d2633bca07',
    'main-review.private.json':'6903e4b6eccdddba1f5ad188bdf901a643a30e1dfa9a1c168575e16925581ce7',
    'main-third.private.json':'5ca9ac157ede168f299bceb65841e24a9accf89d8ef3bdb08c249a2afabdad1c',
    'final-first.private.json':'5ca9ac157ede168f299bceb65841e24a9accf89d8ef3bdb08c249a2afabdad1c'}

def verify_sources(binary,metadata,startup):
    loaded={}
    for name,expected in CONTRACT_PINS.items():
        raw=(CONTRACT_DIR/name).read_bytes()
        if warm.cache.sha(raw)!=expected:raise ValueError('Frozen String contract source differs: '+name)
        loaded[name]=json.loads(raw)
    approved=loaded['approved-string-consumer-contract.private.json']
    contract=loaded['string-consumer-contract.private.json']
    if approved['contractSHA256']!=CONTRACT_PINS['string-consumer-contract.private.json'] or approved['nextInputContractSHA256']!=CONTRACT_PINS['next-input-contract.private.json'] or approved['mainReviewSHA256']!=CONTRACT_PINS['main-review.private.json'] or approved['sourceReportSHA256']!=CONTRACT_PINS['main-third.private.json']:
        raise ValueError('Approval source links differ')
    for path,expected in contract['sourcePins'].items():
        if path.startswith('GAME_PATH/'):continue
        if warm.cache.sha((ROOT/path).read_bytes())!=expected:raise ValueError('Frozen input source differs: '+path)
    if warm.cache.sha(Path(warm.__file__).read_bytes())!='667fa5948f1d645feb923f5b166efa7bb1667cb12ad6b8135d2911bffd4e26b7':raise ValueError('Frozen warm helper differs')
    cache_pins,_,_=warm.cache.verify_sources(binary,metadata)
    old=warm.verify_sources(binary,metadata,startup,verified_sources=cache_pins)
    return dict(approvedContractSHA256=CONTRACT_PINS['approved-string-consumer-contract.private.json'],
        contractSHA256=approved['contractSHA256'],sourcePinsChecked=len(contract['sourcePins']),oldSourceVerification=old,
        newNativeWindows=0,nativeCpuExecuted=False)


def main(argv=None):
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--owned-input',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(argv);owned_root=ROOT/'local/migration72/d02-parallel-20261109/r3-string-consumer'
    if not args.owned_input.resolve().is_relative_to(owned_root.resolve()) or not args.output.resolve().is_relative_to(owned_root.resolve()) or args.output.exists():parser.error('Fresh owned input/output within card directory required')
    raw=args.owned_input.read_bytes();fixture=json.loads(raw)
    if fixture['scope']!='owned-artificial-memory':parser.error('Owned artificial fixture required')
    memory=runtime.OwnedMemory(fixture['spans']);tick=0
    def clock():
        nonlocal tick
        tick+=1
        return 'owned-clock-'+str(tick)
    result=collect(memory.read,fixture['module'],now=clock)
    report=dict(ownedArtificialInput=True,liveSampling=False,nativeCpuExecuted=False,fixtureSHA256=warm.cache.sha(raw),result=result)
    with args.output.open('x',encoding='utf8') as stream:json.dump(report,stream,indent=2);stream.write('\n')
    print(json.dumps(dict(status=result['status'],budget=result['budget'],ownedOnly=True)))

if __name__=='__main__':main()
