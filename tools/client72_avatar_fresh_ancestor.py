"""Offline fresh-ancestor continuation; no process discovery, launch or reads."""
import argparse
import json
import struct
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
if not __package__:
    import sys
    sys.path.insert(0,str(REPO));__package__='tools'
from . import client72_avatar_parent_continuation as prior

RAW_ROOT = Path('local/migration72/d02-resume-20261108/r1-fresh-ancestor')
FRESH = 'local/migration72/d02-active/main/entry-context-tool/live-resume-20261108-2.json'
FRESH_SHA = '119f8a0b3c3ecc8829eda9bf7422c3646c643afa34ac041ea69b5607a02a7384'
MONITOR = 'local/migration72/d02-active/plain/run-resume-20261108-2.json'
MONITOR_SHA = '5ca5b6e5af9bba74889495426e983d5b8d55048f82091fb10dcd3ed08d732fb2'
SAMPLER = 'tools/sample_client72_entry_context.py'
SAMPLER_SHA = 'ecab38c9e39daf5ea48bb017cc7060c61c8b142c4f3844d37b54c3e7374f9c7c'
PRIOR_TOOL = 'tools/client72_avatar_parent_continuation.py'
PRIOR_SHA = '3ec5a76cf5f6dc46fd29c1a058580f47d74224a525ac12ee0ced51410d2d5cf3'
PRIOR_RAW = 'local/migration72/d02-resume-20261108/r1-parent/first.json'
PRIOR_RAW_SHA = '2a58df6aef88d5a19c88e6806420559c23a053664459f19cc95a5fdad32eca9b'
OBJECT_CELL = 0x145bc95e0
NULLABLE_CELL = 0x145bc96f8
NULLABLE_IDENTITY = 0x6a0000000
CANARY = 0xfedcba9876543210


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python-libs',type=Path,default=REPO/'local/migration72/python')
    args=parser.parse_args()
    if not args.output.resolve().is_relative_to((REPO/RAW_ROOT).resolve()):
        parser.error('Output must stay in the dedicated r1-fresh-ancestor directory')
    if args.output.exists() or args.output.with_suffix('.source.py').exists():
        parser.error('Existing raw/source output is frozen; choose a new output name')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    from . import client72_avatar_handler_closure as frozen
    pins=prior.verify_sources()
    for path,digest in ((PRIOR_TOOL,PRIOR_SHA),(PRIOR_RAW,PRIOR_RAW_SHA),(FRESH,FRESH_SHA),
                        (MONITOR,MONITOR_SHA),(SAMPLER,SAMPLER_SHA)):
        prior.read_pinned(path,digest);pins[path]=digest
    b,m,s,*_= [frozen.base.read_verified(args.client/name,digest) for name,digest in frozen.FIVE_INPUTS.items()]
    frozen.base.load_emulator(args.python_libs)
    records,_=frozen.base.extract(b,m,startup=s,structures=True)
    pe=frozen.base.PE(b)
    static=object_sources(b,s,records,pe,frozen.base)
    snapshot=frozen.audit_snapshot(b,m)
    fresh=json.loads((REPO/FRESH).read_bytes())
    relocation=relocate_ancestors(fresh,snapshot,b,m,s,pe,frozen.base,static)
    interface_rows=provide_interface_rows(snapshot,b,m,s,pe,frozen.base,records)
    parameter=prior.generic_parameter(snapshot,b,m,pe,frozen.base)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    before=continue_cold(frozen,b,m,s,records,snapshot,parameter,args.python_libs,args.output,False)
    cold=continue_cold(frozen,b,m,s,records,snapshot,parameter,args.python_libs,args.output,True)
    controls=object_controls(frozen,b,records,args.python_libs,static)
    boundaries=verify_continuation(before,cold,snapshot)
    contract=next_sampling_contract(b,m,s,pe,frozen.base,records,interface_rows,static)
    report=dict(task='F-03d3f4d2-fresh-ancestor',inputSHA256=frozen.FIVE_INPUTS,sourcePins=pins,
        sourceSHA256=prior.sha256(Path(__file__).read_bytes()),objectSources=static,
        relocation=relocation,genericParameter=parameter,fixedInterfaceRows=interface_rows,
        beforeStaticIdentityBinding=before,cold=cold,objectControls=controls,
        verifiedBoundaries=boundaries,nextSamplingContract=contract,
        completeTwoGenericClassShellsUnderDeclaredModels=True,completeClassInitialization=False,
        sameProcessReplay=False,completeColdComparer=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False,
        scope='Fresh captured ancestor fields are explicitly rebound to the frozen old context. Static canonical identity is a declared model, not a fresh canonical-cell observation. No live process operation is performed.')
    args.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    print(json.dumps(dict(failure=cold['native'][-1]['failure'],error=cold['native'][-1]['error'],
        missing=cold['native'][-1]['uncapturedReads'],completeColdComparer=False)))


def window(b,pe,start,end):
    raw=b[pe.offset(start):pe.offset(start)+end-start]
    return dict(start=hex(start),end=hex(end),width=len(raw),bytesHex=raw.hex(),sha256=prior.sha256(raw))


def object_sources(b,s,records,pe,base):
    header=pe.offset(base.HEADER_VA)
    resolver=base.TypeResolver(pe,s,records,header)
    address=resolver.references+16
    descriptor=b[pe.offset(address):pe.offset(address)+16]
    if descriptor!=struct.pack('<QII',371,28<<16,0):
        raise ValueError('Fixed Object descriptor representation differs')
    actual=resolver.resolve_index(1)
    if (actual.get('kind'),actual.get('typeDefinition'),records[371]['name'],records[371]['namespace'])!=('object',371,'Object','System'):
        raise ValueError('Exact fixed System.Object identity differs')
    if records[1234]['parent']['typeIndex']!=1 or records[371]['parent'] is not None:
        raise ValueError('Fixed EqualityComparer/Object parent lineage differs')
    inflation_table=window(b,pe,0x1404ede18,0x1404ede48)
    class_table=window(b,pe,0x140517900,0x140517978)
    if 0x1404ede18+struct.unpack_from('<i',bytes.fromhex(inflation_table['bytesHex']),9*4)[0]!=0x1404edd33:
        raise ValueError('kind28 descriptor inflation branch changed')
    if 0x140517900+struct.unpack_from('<i',bytes.fromhex(class_table['bytesHex']),27*4)[0]!=0x140517843:
        raise ValueError('kind28 class branch changed')
    code=window(b,pe,0x140517843,0x14051784f)
    if 0x14051784a+struct.unpack_from('<i',bytes.fromhex(code['bytesHex']),3)[0]!=OBJECT_CELL:
        raise ValueError('Object class canonical reader differs')
    producer=window(b,pe,0x1405427b2,0x1405427d6)
    raw=bytes.fromhex(producer['bytesHex'])
    namespace=0x1405427c0+struct.unpack_from('<i',raw,10)[0]
    name=0x1405427c7+struct.unpack_from('<i',raw,17)[0]
    strings=[b[pe.offset(a):pe.offset(a)+32].split(b'\0',1)[0].decode('ascii') for a in (namespace,name)]
    if strings!=['System','Object'] or 0x1405427d6+struct.unpack_from('<i',raw,32)[0]!=OBJECT_CELL:
        raise ValueError('Named System.Object canonical producer differs')
    nullable=window(b,pe,0x140542fe9,0x14054300a)
    nraw=bytes.fromhex(nullable['bytesHex'])
    nnamespace=0x140542ff7+struct.unpack_from('<i',nraw,10)[0]
    nname=0x140542ffe+struct.unpack_from('<i',nraw,17)[0]
    nstrings=[b[pe.offset(a):pe.offset(a)+32].split(b'\0',1)[0].decode('ascii') for a in (nnamespace,nname)]
    if nstrings!=['System','Nullable`1'] or 0x14054300a+struct.unpack_from('<i',nraw,29)[0]!=NULLABLE_CELL:
        raise ValueError('Named Nullable canonical producer differs')
    nullable_records=[row for row in records if row['namespace']=='System' and row['name']=='Nullable`1']
    if len(nullable_records)!=1 or nullable_records[0]['index'] in (371,1234,1239):
        raise ValueError('Fixed Nullable definition identity is not unique/distinct')
    return dict(typeDefinition=371,kind=28,fixedReferenceIndex=1,fixedDescriptorAddress=hex(address),
        fixedDescriptorHex=descriptor.hex(),resolved=actual,
        parent={'definition':1234,'reference':1,'target':371},canonicalCell=hex(OBJECT_CELL),
        strings=strings,stringAddresses=[hex(a) for a in (namespace,name)],
        nullableIdentity=dict(definition=nullable_records[0]['index'],namespace='System',name='Nullable`1',
            canonicalCell=hex(NULLABLE_CELL),modelIdentity=hex(NULLABLE_IDENTITY),
            observedCanonicalCell=False,headerProvided=False,
            scope='Opaque canonical identity for one original pointer comparison only; fixed named producer and unique metadata definition prove it differs from captured definitions1234/1239. No header, member, method or natural pointer value is invented.'),
        windows=dict(inflationTable=inflation_table,classTable=class_table,canonicalReader=code,
            namedProducer=producer,inflationReturn=window(b,pe,0x1404edd33,0x1404edd54),
            inflationDispatch=window(b,pe,0x1404ed9c3,0x1404ed9eb),
            emptyGenericCounterReader=window(b,pe,0x1404f1289,0x1404f12a7),
            inflatedVectorCleanup=window(b,pe,0x1404edb82,0x1404edbc8),
            nullableNamedProducer=nullable,nullableComparison=window(b,pe,0x140517c1c,0x140517c25),
            interfaceProducer=window(b,pe,0x14051f370,0x14051f554),
            poolAllocator=window(b,pe,0x1405091d0,0x1405092f5),
            definitionCacheReader=window(b,pe,0x15a4d6b60,0x15a4d6c21),
            construction=window(b,pe,0x140517980,0x140517c88)),
        new371MetadataRequiredByTheseBranches=False,
        correction='Previous contract incorrectly generalized ordinary kind18 to exact System.Object371. Old99 already records kind28; this card accepts only the specific fixed descriptor/identity, not arbitrary kind18-or28.')


def validate_fresh(fresh):
    if (fresh['status'],fresh['parentDescriptorStatus'],fresh['verifiedWindows'],fresh['sourceSHA256'],
        fresh['sourceObservationSHA256'])!=('partial','read',24,SAMPLER_SHA,MONITOR_SHA):
        raise ValueError('Fresh epoch/source/status contract differs')
    if fresh['ancestorClasses']!=[dict(type=1234,status='read',compactOffsets=dict(parent=16870088,declaring=0)),
                                  dict(type=371,status='class-identity-mismatch')]:
        raise ValueError('Fresh partial reason differs from the exact Object kind mismatch')
    rows={row['name']:row for row in fresh['reads']}
    if len(rows)!=36 or sum(row['requested'] for row in rows.values())!=2232:
        raise ValueError('Fresh read inventory differs')
    data={}
    for name,row in rows.items():
        raw=bytes.fromhex(row['bytesHex'])
        if row['error'] is not None or len(raw)!=row['requested'] or prior.sha256(raw)!=row['sha256']:
            raise ValueError('Fresh captured span changed: '+name)
        data[name]=raw
    for index,prefix in ((1239,'parent.class1239'),(1234,'ancestor.1234'),(371,'ancestor.371')):
        header=data[prefix+'.header']
        if len(header)!=208 or struct.unpack_from('<Q',header,0x10)[0]!=index or header[0x1a]!=(28 if index==371 else 18):
            raise ValueError('Exact fresh class identity/kind differs')
        if struct.unpack_from('<I',header,0xa8)[0]!=0 or (index==371 and struct.unpack_from('<I',header,0xb0)[0]!=0):
            raise ValueError('Observed declaring/root compact offsets differ')
    return rows,data


def add_span(snapshot,label,address,raw):
    for old,row in snapshot['reads'].items():
        begin=max(address,row['address']);end=min(address+len(raw),row['address']+row['length'])
        if begin<end and raw[begin-address:end-address]!=snapshot['data'][old][begin-row['address']:end-row['address']]:
            raise ValueError('Conflicting explicitly provided spans: '+label+' / '+old)
    snapshot['reads'][label]=dict(address=address,length=len(raw),sha256=prior.sha256(raw),error=None)
    snapshot['data'][label]=raw


def relocate_ancestors(fresh,old,b,m,s,pe,base,static):
    rows,data=validate_fresh(fresh)
    q=lambda name,off=0:struct.unpack_from('<Q',data[name],off)[0]
    oq=lambda name,off=0:struct.unpack_from('<Q',old['data'][name],off)[0]
    module=fresh['mainModule']['base']
    if rows['parent.classCache.cell']['address']!=module+0x5bc8f18 or rows['parent.registry.cell']['address']!=module+0x5bc8ad0:
        raise ValueError('Fresh root cells differ')
    if rows['parent.registry.base']['address']!=q('parent.registry.cell'):
        raise ValueError('Fresh registry indirection differs')
    cache=q('parent.classCache.cell');registry=q('parent.registry.base')
    model_cache=oq('cold.classCache.cell');model_registry=oq('cold.registry.base')
    body=old['reads']['cold.class1239.metadata']['address']-1239*70
    h=pe.offset(base.HEADER_VA);table=(base.number(b,h+0x64)+0xa7932555)&0xffffffff
    added=[];projections=[]
    for index,prefix in ((1239,'parent.class1239'),(1234,'ancestor.1234'),(371,'ancestor.371')):
        entry='parent.classCache.entry1239' if index==1239 else prefix+'.cacheEntry'
        header=data[prefix+'.header'];pointer=q(entry)
        if len(header)!=208 or rows[entry]['address']!=cache+index*8 or rows[prefix+'.header']['address']!=pointer:
            raise ValueError('Fresh class cache chain differs')
        projection=bytearray(header);struct.pack_into('<Q',projection,0x58,body+index*70)
        model_pointer=model_registry+(pointer-registry)
        if not 0<=pointer-registry<1<<32:
            raise ValueError('Class pointer does not fit the declared compact registry')
        if index in (1239,371):
            if bytes(projection)!=old['data']['cold.class'+str(index)+'.header'] or model_pointer!=oq('cold.classCache.entry'+str(index)):
                raise ValueError('Existing definition/Object context differs beyond metadata-body rebinding')
        else:
            metadata=data[prefix+'.metadata'];file=base.BODY+table+index*70
            if len(metadata)!=70 or rows[prefix+'.metadata']['address']!=q(prefix+'.header',0x58) or metadata!=m[file:file+70]:
                raise ValueError('Fresh1234 metadata does not equal the exact fixed row')
            for suffix,address,raw in (('entry1234',model_cache+index*8,struct.pack('<Q',model_pointer)),
                                       ('class1234.header',model_pointer,bytes(projection)),
                                       ('class1234.metadata',body+index*70,metadata)):
                label='cold.freshAncestor.'+suffix;add_span(old,label,address,raw);added.append(label)
        projections.append(dict(type=index,freshAddress=hex(pointer),modelAddress=hex(model_pointer),
            headerSHA256=prior.sha256(header),modelHeaderSHA256=prior.sha256(projection),
            metadataPointerFresh=hex(q(prefix+'.header',0x58)),metadataPointerModel=hex(body+index*70),
            rewrittenField='header+0x58 only; other bytes copied opaque, not dereference permission'))
    parent=data['parent.descriptor'];index=struct.unpack_from('<Q',parent)[0]
    start=(base.number(b,h+0xd0)+0xe1492096)&0xffffffff
    pair=struct.unpack_from('<II',s,start+index*8)
    if parent!=struct.pack('<QII',405416,21<<16,0) or pair!=(54605,1234):
        raise ValueError('Exact static parent registration differs')
    for descriptor,header_name,expected in (('parent.descriptor','parent.class1239.header',parent),
                                          ('ancestor.1234.parentDescriptor','ancestor.1234.header',bytes.fromhex(static['fixedDescriptorHex']))):
        compact=struct.unpack_from('<I',data[header_name],0xb0)[0]
        if rows[descriptor]['address']!=registry+compact+0x10 or data[descriptor]!=expected:
            raise ValueError('Exact captured parent descriptor chain differs')
        label='cold.freshAncestor.'+descriptor
        add_span(old,label,model_registry+compact+0x10,data[descriptor]);added.append(label)
    return dict(freshSHA256=FRESH_SHA,oldSamplerSHA256=old['sample']['sourceSHA256'],
        oldSnapshotSHA256='63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0',sameProcessReplay=False,
        registry=dict(freshBase=hex(registry),modelBase=hex(model_registry),rule='registry-relative compact offsets; equality of these arena bases is observed, not a same-process identity claim'),
        cache=dict(freshBase=hex(cache),modelBase=hex(model_cache)),classProjections=projections,
        addedLabels=added,staticParent=dict(kind=21,index=405416,instance=54605,definition=1234),
        objectParent=dict(kind=28,definition=371,descriptorHex=static['fixedDescriptorHex']),
        objectCanonicalBinding=dict(cell=hex(OBJECT_CELL),modelClass=hex(oq('cold.classCache.entry371')),
            observedCanonicalCell=False,source='Original named System/Object producer and kind28 canonical reader; captured classCache371 identity.'),
        copiedRuntimePointers='Opaque header pointers remain supplied bytes only; no pointed-to storage is inferred or filled with page zero.')


def provide_interface_rows(snapshot,b,m,s,pe,base,records):
    h=pe.offset(base.HEADER_VA)
    table=(base.number(b,h+0x64)+0xa7932555)&0xffffffff
    interfaces=base.number(b,h+0xc8)^0x1d590619
    body=snapshot['reads']['cold.class1239.metadata']['address']-1239*70-table
    resolver=base.TypeResolver(pe,s,records,h);result=[]
    for index in (1239,1234):
        row=m[base.BODY+table+index*70:base.BODY+table+(index+1)*70]
        count=row[0x43]^0x25;first=(base.number(row,0x36,'H')+0x6f6c)&0xffff
        file=base.BODY+interfaces+first*4;raw=m[file:file+count*4]
        if count:
            add_span(snapshot,'cold.freshAncestor.fixedInterfaceRefs'+str(index),body+interfaces+first*4,raw)
        refs=[value for value, in struct.iter_unpack('<I',raw)]
        result.append(dict(definition=index,count=count,first=first,width=len(raw),fileOffset=hex(file),
            modelAddress=hex(body+interfaces+first*4),bytesHex=raw.hex(),sha256=prior.sha256(raw),
            referenceIndices=refs,interfaces=[resolver.resolve_index(value) for value in refs],
            source='Fixed PE header+c8 XOR 1d590619; fixed class metadata+36 add6f6c u16 and +43 XOR25. Native reader14051f462 reads exact4-byte rows.'))
    if [row['count'] for row in result]!=[0,2]:raise ValueError('Exact comparer interface counts differ')
    return result


def next_sampling_contract(b,m,s,pe,base,records,interfaces,static):
    h=pe.offset(base.HEADER_VA);table=(base.number(b,h+0x64)+0xa7932555)&0xffffffff
    resolver=base.TypeResolver(pe,s,records,h);classes=[]
    refs=bytes(b[resolver.reference_base:resolver.reference_base+resolver.reference_count*16])
    for index in (1169,1249):
        evidence=[(ordinal,bits) for ordinal,(data,bits) in enumerate(struct.iter_unpack('<QI4x',refs))
                  if data==index and bits==18<<16]
        if not evidence:raise ValueError('Next exact ordinary definition lacks a fixed kind18 descriptor')
        ordinal=evidence[0][0];address=resolver.references+ordinal*16
        raw=m[base.BODY+table+index*70:base.BODY+table+(index+1)*70]
        classes.append(dict(definition=index,name=records[index]['name'],namespace=records[index]['namespace'],
            parent=records[index]['parent'],cacheOffset=hex(index*8),cacheEntryBytes=8,headerBytes=208,
            headerIdentity=dict(data=index,kind=18),fixedReferenceIndex=ordinal,
            fixedDescriptor=window(b,pe,address,address+16),
            metadata=dict(fileOffset=hex(base.BODY+table+index*70),width=70,bytesHex=raw.hex(),sha256=prior.sha256(raw),
                interfaceCount=raw[0x43]^0x25),
            declaringDescriptorRule='Only if captured header+a8 compact offset is nonzero; record zero as skipped. No guessed extra class follows it.',
            parentDescriptorRule='Only if captured header+b0 is nonzero; fixed metadata parent is recorded for comparison, not a guessed runtime pointer.'))
    if any(row['parent'] is not None or row['metadata']['interfaceCount']!=0 for row in classes):
        raise ValueError('Next interface definition parent/interface closure differs')
    parameter=(base.number(b,h+0x94)+0xd97e8a5c)&0xffffffff
    raw=m[base.BODY+parameter+362*14:base.BODY+parameter+363*14]
    return dict(liveExecution=False,includesPoolOrNativeConfig=False,
        firstMissing=dict(reader='0x15a4d6bf1',definition=1169,width=8,
            chain='fresh module B+5bc8f18 -> same fresh cache base -> [base+1169*8]',
            oldNaturalAddressReusable=False,errorIsNotSuccess=True),
        exactSuccessors=interfaces[1]['interfaces'],
        sourceChain=['14051f462 loads ref444353 -> 1404ed990 leaves kind18 descriptor unchanged -> 140517680 kind18 -> 1405269f0 -> 15a4d6bf1 cache1169.',
            'If the first lookup completes and the original loop reaches the next fixed4-byte row, ref448407 is static generic405409 / instance54600 / definition1249 / VAR362. Its original inflation substitutes the current known single argument; 140517980 then needs the ordinary definition1249 cache/header.',
            'The 1169 cache result is unknown, including whether it is null. No interface array value, additional type, runtime member or natural canonical pointer is inferred.'],
        classes=classes,staticOnlyNextParameter=dict(index=362,width=14,fileOffset=hex(base.BODY+parameter+362*14),
            bytesHex=raw.hex(),sha256=prior.sha256(raw),liveReadRequired=False),
        mandatoryRuntimeBudget=dict(additionalCacheAndHeaderReads=4,additionalCacheAndHeaderBytes=432,
            assumesReuseOfFreshCacheRoot=True,metadataValidationAdditionalReads=2,metadataValidationAdditionalBytes=140,
            conditionalDescriptorMaxAdditionalReads=4,conditionalDescriptorMaxAdditionalBytes=64,
            conservativeAdditionalMaxReads=10,conservativeAdditionalMaxBytes=636,maxSingleReadBytes=208),
        rules=['A new sampling epoch must obtain its own module/cache/registry roots; this card never requests or performs live reading.',
            'Read only exact cache slots1169 and1249; null entries remain an observed cold-cache branch, not a class header address.',
            'For each nonzero entry read208 bytes, require its exact index and fixed descriptor kind18. Do not generalize Object371 kind28 to these unrelated definitions.',
            'Optional strict metadata validation reads exactly70 bytes through header+58 and compares the fixed row. This is distinguished from the432-byte minimum of the currently proved class lookup/head consumers.',
            'Reuse the same fresh registry chain only for nonzero b0/a8 and read16-byte descriptors at base+compact+10. Preserve unknown tag/identity/null cases; do not recursively guess a new type.',
            'Do not collect hierarchy/interface/vtable/method arrays now: no exact unread runtime array span has yet been reached. New runtime method/member consumers remain future frontier work.'],
        reusedWindows=['15a4d6b60..15a4d6c21','140517980..140517c88','14051f370..14051f554'],
        nullableCanonicalCell=dict(address='B+5bc96f8',width=8,mandatoryForCurrentDeclaredModel=False,
            scope=static['nullableIdentity']['scope'],headerOrMembersRequested=False))


def verify_continuation(before,cold,snapshot):
    first=before['native'][-1]
    unbound=[row for row in before['runtimeGlobalReads'] if not row['supplied']]
    if first['failure']!='Unbound runtime global cell read' or first['error'] is not None or unbound!=[
        dict(address=hex(NULLABLE_CELL),width=8,reader='0x140517c1c',supplied=False)]:
        raise ValueError('The independent pre-binding native run did not preserve the exact Nullable runtime-cell first gap')
    table=struct.unpack_from('<Q',snapshot['data']['cold.classCache.cell'])[0];last=cold['native'][-1]
    expected=dict(address=hex(table+1169*8),size=8,site='0x15a4d6bf1')
    if last['failure']!='Uncaptured natural-address memory read' or last['error'] is not None or last['uncapturedReads']!=[expected] or last['regs']['rcx']!='0x491':
        raise ValueError('The fresh continuation did not reach the exact reviewed IEqualityComparer1169 cache first gap')
    if len(cold['genericRecords'])!=2 or [row['width'] for row in cold['newClasses']]!=[400,400]:
        raise ValueError('Native generic-class shell count/size differs')
    for row,klass,index in zip(cold['genericRecords'],cold['newClasses'],(1239,1234)):
        raw=bytes.fromhex(row['bytesHex'])
        if struct.unpack_from('<I',raw)[0]!=index or struct.unpack_from('<Q',raw,8)[0]!=0x142377940 or struct.unpack_from('<Q',raw,24)[0]!=int(klass['pointer'],16):
            raise ValueError('Original cached-class publication did not finish for its exact definition/argument')
    if cold['models']['callbackFailures'] or any(not row['supplied'] for row in cold['runtimeGlobalReads']):
        raise ValueError('A model callback or unbound global failure was mistaken for the runtime cache frontier')
    if any(row['tailCanary'] not in (None,hex(CANARY)) for row in cold['native']):
        raise ValueError('Original RuntimeType array tail changed')
    if cold['interfaceStorage']['allocations']!=[dict(pointer='0x612000680',width=16,bytesHex='00'*16,tail=hex(CANARY))]:
        raise ValueError('Native zeroed interface storage or tail differs before the first unprovided element')
    if len(cold['criticalAtStop'])!=1 or cold['sharedHeldAtStop']:
        raise ValueError('Unexpected lock ownership at the genuine cache frontier')
    return dict(preBindingFirstGap=dict(reader='0x140517c1c',width=8,address=hex(NULLABLE_CELL)),
        firstRuntimeClassGap=dict(reader='0x15a4d6bf1',definition=1169,width=8,modelAddress=expected['address']),
        nativeCachedClassPublications=2,classInitializationCompleted=False,
        preservedOriginalProducerIndices=[0,2072,405417,411432],allArrayTailsPreserved=True,
        unprovidedNaturalBytesConsumed=False,unboundNewRuntimeGlobalBytesConsumed=False,
        runtimeGlobalGuardRange=dict(start='0x145bc7000',end='0x145bca000',
            armedAt='The previous cache1234 frontier,15a4d6bf1',scope='These selected runtime-global cells only; no claim of a universal runtime-global inventory.'),
        sourceModelInitializers=['Object371 canonical identity by exact named producer.',
            'One opaque Nullable definition identity for pointer comparison, no header storage.',
            'Controlled empty GenericClass cache tombstone counter+40 explicitly0; native reader1404f129d and 16-byte update1404f16f6.',
            'Pool/vector/interface storage only at recorded exact callers/widths; their initialization is explicit allocator/memset modeling.'])


class FreshVectorStorage(prior.VectorStorage):
    def free(self):
        e=self.e;caller=e.read(e.reg('rsp'));pointer=e.reg('rcx');width=e.reg('rdx')
        if caller!=0x1404edbbf:return super().free()
        if width!=8 or self.live.get(pointer)!=8:
            raise ValueError('Outside exact inflated one-argument vector cleanup')
        if e.read(pointer+width)!=CANARY:raise ValueError('Inflated vector allocation tail changed')
        del self.live[pointer]
        self.events.append(dict(action='free',caller=hex(caller),pointer=hex(pointer),width=width))
        return 0


class InterfaceStorage:
    def __init__(self,engine,snapshot):
        self.e=engine;self.snapshot=snapshot;self.allocations={};self.events=[];self.classes={}

    def allocate(self):
        e=self.e;caller=e.read(e.reg('rsp'));width=e.reg('rdx');klass=e.reg('r14')
        if caller!=0x14051f3ce or width not in (0,16) or e.reg('rcx')!=struct.unpack_from('<Q',self.snapshot['data']['cold.registry.cell'])[0]:
            raise ValueError('Outside exact generic-comparer interface storage')
        if self.classes.get(klass)!=400:raise ValueError('Interface storage caller class is not an owned400-byte shell')
        generic=e.read(klass+0x68);definition=e.read(generic,'I')
        if (definition,width) not in ((1239,0),(1234,16)):
            raise ValueError('Interface storage does not match its fixed comparer declaration')
        pointer=e.alloc(width+8 if width else 0)
        if width:e.uc.mem_write(pointer,b'\xcc'*width);e.write(pointer+width,CANARY)
        self.allocations[pointer]=width
        self.events.append(dict(action='allocate',caller=hex(caller),pointer=hex(pointer),width=width,definition=definition))
        return pointer

    def clear(self):
        e=self.e;caller=e.read(e.reg('rsp'));pointer=e.reg('rcx');width=e.reg('r8')
        if caller not in (0x14051f3de,0x14051f3f8) or e.reg('edx')!=0 or self.allocations.get(pointer)!=width:
            raise ValueError('Outside exact generic-interface zeroing')
        if width:e.uc.mem_write(pointer,bytes(width))
        if width and e.read(pointer+width)!=CANARY:raise ValueError('Interface allocation tail changed')
        self.events.append(dict(action='clear',caller=hex(caller),pointer=hex(pointer),width=width))
        return pointer


class FreshLayer(prior.ModelLayer):
    def __init__(self,engine,snapshot):
        super().__init__(engine,snapshot)
        self.vector=FreshVectorStorage(engine)
        self.interfaces=InterfaceStorage(engine,snapshot)
        self.runtimeGlobals=[];self.globalKnown=set();self.armed=False

    def wrap(self,key,original):
        inherited=super().wrap(key,original)
        if key==0x1405091d0 and getattr(original,'__name__','')=='class_or_generic_allocate':
            context={name:cell.cell_contents for name,cell in zip(original.__code__.co_freevars,original.__closure__)}
            self.interfaces.classes=context['class_allocations']
        if key not in (0x1405091d0,0x141fdf9c0):return inherited
        def supplied():
            e=self.e;caller=e.read(e.reg('rsp'))
            if key==0x1405091d0 and caller==0x14051f3ce:return self.interfaces.allocate()
            if key==0x141fdf9c0 and caller in (0x14051f3de,0x14051f3f8):return self.interfaces.clear()
            return inherited()
        return supplied

    def written(self,address,width):
        if address<0x145bca000 and address+width>0x145bc7000:
            self.globalKnown.update(range(max(address,0x145bc7000),min(address+width,0x145bca000)))


def continue_cold(frozen,b,m,s,records,snapshot,parameter,libraries,output,nullable_identity):
    original=frozen.base.Offline72;layers=[];parameter_reads=[]
    address=int(parameter['address'],16);raw=bytes.fromhex(parameter['bytesHex'])
    class Offline(original):
        def __init__(self,*args,**kwargs):
            self.extra_layer=None
            super().__init__(*args,**kwargs)
            layer=FreshLayer(self,snapshot);self.extra_layer=layer;layers.append(layer)
            self.models=prior.InterceptModels(self.models,layer)
            self.write(OBJECT_CELL,struct.unpack_from('<Q',snapshot['data']['cold.classCache.entry371'])[0])
            if nullable_identity:self.write(NULLABLE_CELL,NULLABLE_IDENTITY)
            # Explicit empty-cache tombstone counter, not a natural page-zero input.
            # Native insert adds +38 occupancy and +40 tombstones before +50 capacity.
            self.write(0x145bc7260,0)
            page=address&~4095;self.uc.mem_map(page,4096);self.uc.mem_write(address,raw)
            def read_guard(uc,access,pointer,width,value,data):
                reader=self.reg('rip')
                if reader==0x15a4d6bf1 and self.reg('rcx')==1234:layer.armed=True
                if pointer&~4095==page:
                    if not address<=pointer or pointer+width>address+len(raw):
                        self.failure='Unprovided genericParam metadata bytes';uc.emu_stop()
                    else:parameter_reads.append(dict(address=hex(pointer),width=width,reader=hex(reader)))
                if layer.armed and pointer<0x145bca000 and pointer+width>0x145bc7000:
                    supplied=all(a in layer.globalKnown for a in range(pointer,pointer+width))
                    row=dict(address=hex(pointer),width=width,reader=hex(reader),supplied=supplied)
                    layer.runtimeGlobals.append(row)
                    if not supplied:self.failure='Unbound runtime global cell read';uc.emu_stop()
            def write_guard(uc,access,pointer,width,value,data):layer.written(pointer,width)
            self.hooks.extend((self.uc.hook_add(self.U.UC_HOOK_MEM_READ,read_guard),
                               self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE,write_guard)))

        def write(self,address,value,fmt='Q'):
            super().write(address,value,fmt)
            if self.extra_layer:self.extra_layer.written(address,struct.calcsize('<'+fmt))
    frozen.base.Offline72=Offline
    try:
        try:
            frozen.run_cold(b,m,s,records,snapshot,libraries,output.with_suffix('.unexpected-original-stop.json'))
            raise ValueError('Frozen original stop unexpectedly remained valid')
        except AssertionError as error:
            trace=error.__traceback__;frame=None
            while trace:
                if trace.tb_frame.f_code.co_name=='run_cold':frame=trace.tb_frame
                trace=trace.tb_next
            if frame is None:raise
            local=frame.f_locals;e=local['e'];layer=layers[-1]
            return dict(native=local['results'],functions=local['functions'],
                criticalEvents=local['critical_events'],criticalAtStop=[hex(x) for x in local['critical_stack']],
                sharedEvents=local['shared_events'],sharedHeldAtStop=local['shared_depth'] is not None,
                oldExpectedMissingDescriptorAssertionObsolete=True,
                genericRecords=[dict(pointer=hex(p),bytesHex=bytes(e.uc.mem_read(p,32)).hex()) for p in local['generic_allocated']],
                newClasses=[dict(pointer=hex(p),width=n,headerHex=bytes(e.uc.mem_read(p,0xd0)).hex()) for p,n in local['class_allocations'].items()],
                models=dict(pool16=[dict(row,bytesHex=bytes(e.uc.mem_read(int(row['pointer'],16),16)).hex()) for row in layer.pools],
                    vectorEvents=layer.vector.events,vectorsAtStop={hex(p):n for p,n in layer.vector.live.items()},
                    vectorBytes=[dict(pointer=hex(p),width=n,bytesHex=bytes(e.uc.mem_read(p,n)).hex(),tail=hex(e.read(p+n))) for p,n in layer.vector.allocated],
                    distinctCriticalAcquisitions=layer.locks,callbackFailures=layer.callbackFailures,genericParameterReads=parameter_reads),
                interfaceStorage=dict(events=layer.interfaces.events,
                    allocations=[dict(pointer=hex(p),width=n,bytesHex=bytes(e.uc.mem_read(p,n)).hex(),tail=hex(e.read(p+n)) if n else None)
                        for p,n in layer.interfaces.allocations.items()]),
                runtimeGlobalReads=layer.runtimeGlobals,stopRegisters={n:hex(e.reg(n)) for n in ('rip','rsp','rcx','rdx','r8','rax','rdi','rsi','r15')},
                returnAddress=hex(e.read(e.reg('rsp'))),opaqueNullableIdentitySupplied=nullable_identity)
    finally:frozen.base.Offline72=original


def object_controls(frozen,b,records,libraries,static):
    e=frozen.base.Offline72(b,libraries,records,trace_instructions=True)
    e.prepare();e.uc.mem_map(frozen.RETURN&~4095,4096);e.uc.mem_write(frozen.RETURN,b'\x90')
    e.allow_unwind(0x1404ed990);e.allow_unwind(0x140517680)
    lock=e.alloc(40);stack=[];events=[]
    def enter():
        if e.reg('rcx')!=lock or e.read(e.reg('rsp'))!=0x1404ed9c3:raise ValueError('Outside exact descriptor lock enter')
        stack.append(lock);events.append('enter');return 0
    def leave():
        if e.reg('rcx')!=lock or stack!=[lock] or e.read(e.reg('rsp'))!=0x1404edd40:raise ValueError('Outside exact descriptor lock leave')
        stack.pop();events.append('leave');return 0
    e.write(0x145bc95a8,lock);e.write(0x143bac1f0,e.model_stub(enter));e.write(0x143bac5f8,e.model_stub(leave))
    descriptor=e.alloc(24);e.write(descriptor+16,CANARY);e.write(OBJECT_CELL,0x8000000100000001)
    cases=[]
    def invoke(entry,stops=(),**registers):
        e.failure=None;e.calls=[];e.write(frozen.STACK+0x100008,frozen.RETURN)
        for name,value in dict(dict(rsp=frozen.STACK+0x100008,rcx=descriptor,rdx=0xfedcba9876543210,r8=0),**registers).items():
            e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+name.upper()),value)
        hit=[];executed=[];reads=[]
        def observe(a):
            executed.append(a)
            if a in {frozen.RETURN,*stops}:hit.append(a);e.uc.emu_stop()
        e.allowed.extend((a,a+1) for a in (frozen.RETURN,*stops));e.before_instruction=observe
        def capture(uc,access,address,width,value,data):
            if not frozen.STACK<=address<frozen.STACK+0x200000:
                reads.append(dict(address=hex(address),width=width,reader=hex(e.reg('rip'))))
        hook=e.uc.hook_add(e.U.UC_HOOK_MEM_READ,capture)
        try:e.uc.emu_start(entry,frozen.END,timeout=20_000_000,count=100000)
        finally:e.uc.hook_del(hook)
        if e.failure or not hit:raise ValueError(e.failure or 'Object control budget exhausted')
        return dict(entry=hex(entry),stop=hex(hit[0]),rax=hex(e.reg('rax')),rcx=hex(e.reg('rcx')),
            executed=[hex(a) for a in executed],tail=hex(e.read(descriptor+16)),reads=reads)
    try:
        for value in (371,0xfedcba9876543210):
            e.uc.mem_write(descriptor,struct.pack('<QII',value,28<<16,0))
            inflated=invoke(0x1404ed990);klass=invoke(0x140517680)
            if inflated['rax']!=hex(descriptor) or klass['rax']!='0x8000000100000001' or stack or e.read(descriptor+16)!=CANARY:
                raise ValueError('Original kind28 branch did not preserve the exact identity/64-bit/tail contract')
            cases.append(dict(data=hex(value),kind=28,inflation=inflated,classConversion=klass))
        e.uc.mem_write(descriptor,struct.pack('<QII',371,18<<16,0))
        ordinary=invoke(0x140517680,(0x1405269f0,))
        if ordinary['stop']!='0x1405269f0' or ordinary['rcx']!='0x173':raise ValueError('Ordinary class kind18 did not use its separate definition lookup')
        e.allowed.append((0x140517c1c,0x140517c25));e.write(NULLABLE_CELL,0x8000000100000001)
        comparisons=[]
        for value,stop in ((0x8000000200000001,0x140517c50),(0x8000000100000001,0x140517c25)):
            result=invoke(0x140517c1c,(0x140517c25,0x140517c50),rax=value)
            if result['stop']!=hex(stop):raise ValueError('Original Nullable identity comparison lost high64 identity')
            comparisons.append(dict(input=hex(value),canonical='0x8000000100000001',trace=result))
        e.allow_unwind(0x1405091d0);pool=e.alloc(48);cursor=0x6b0000000
        e.uc.mem_map(cursor,4096);e.write(cursor,CANARY);e.write(cursor+8,0x8000000100000001)
        e.write(pool+0x10,cursor+4096);e.write(pool+0x18,cursor);e.write(pool+0x20,16)
        pool_cases=[]
        for width in (0,8,16):
            before=e.read(pool+0x18);result=invoke(0x1405091d0,rcx=pool,rdx=width)
            after=e.read(pool+0x18)
            if result['rax']!=hex(before) or after!=before+width or e.read(cursor)!=CANARY or e.read(cursor+8)!=0x8000000100000001:
                raise ValueError('Original pool fast-path did not preserve zero/nonzero cursor and payload')
            pool_cases.append(dict(width=width,before=hex(before),after=hex(after),trace=result,
                originalPayloadPreserved=True))
        return dict(kind28=cases,kind18DistinctBranch=ordinary,lockEvents=events,
            nullableIdentityComparisons=comparisons,poolFastPath=pool_cases,
            inputScope='The sentinel descriptor tests the original kind28 consumer; it is not an accepted snapshot identity or extra runtime type.',
            metadata371Read=False,wholeConstructor=False)
    finally:e.before_instruction=None;e.close()


if __name__=='__main__':
    main()
