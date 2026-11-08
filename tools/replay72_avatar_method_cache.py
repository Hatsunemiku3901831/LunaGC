"""Replay one exact repeated CN72 cache sample; no process or cache writes."""
import argparse
import copy
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0,str(ROOT))
    __package__ = 'tools'
from . import probe72_avatar_method_cache as probe

RAW = ROOT/'local/migration72/d02-resume-20261109/r1-method-cache/replay'
CAPTURE = 'local/migration72/d02-resume-20261109/r1-method-cache/live/cache-live1.private.json'
CAPTURE_SHA = 'ab042d29b67aff6735b498680e74b6152e6c72285cdeb3cc799ceef57a730998'
MONITOR = 'local/migration72/d02-active/plain/run-resume-20261109-cache1.json'
MONITOR_SHA = '860c571ec1277538e269d7fc1663906bfd7b9d2853876cf3e3e3602949d31914'
LIVE_SOURCE_SHA = '402336ad19e97245089ee61e1a6869db908960f1780986ce8ff5dd8c4fa8a73c'
PROBE_SHA = '6053f724185e2b2c7e75503021beb2f5cbde18319c7aac6edb1dfec7ed7d8544'
PINS = {CAPTURE:CAPTURE_SHA,MONITOR:MONITOR_SHA,
    'tools/probe72_avatar_method_cache.py':PROBE_SHA,
    'tools/sample_client72_avatar_method_cache.py':LIVE_SOURCE_SHA,
    'local/migration72/d02-resume-20261109/r1-method-cache/live/cache-live1.private.source.py':LIVE_SOURCE_SHA}
OWNED = 0x33000000000
HIERARCHY = (0x14051f280,0x14051f36f,'2660d47b018f78042ed87c42acc90df893fdb2064c988ad32d39b31dcd98fed3')


class SourceMemory(probe.SparseMemory):
    """Merge only known bytes, retaining holes and all source read ordinals."""
    def __init__(self,records):
        self.origins = records
        values = {}
        for row in records:
            if row['error'] is not None or row['bytesRead'] != row['requested']:
                raise ValueError('Failed/short input span')
            raw = bytes.fromhex(row['bytesHex'])
            if len(raw) != row['requested'] or not 0 < len(raw) <= 56:
                raise ValueError('Input span bytes/width differ')
            for offset,value in enumerate(raw):
                address = row['address']+offset
                if address in values and values[address] != value:
                    raise ValueError('Contradictory overlapping captured bytes')
                values[address] = value
        self.spans = []
        start = previous = None
        data = bytearray()
        for address,value in sorted(values.items()):
            if previous is not None and address != previous+1:
                self.spans.append(('captured',start,bytes(data)))
                data = bytearray()
                start = None
            if start is None:
                start = address
            data.append(value)
            previous = address
        if start is not None:
            self.spans.append(('captured',start,bytes(data)))


def audit_capture(capture,monitor):
    if (capture['status'] != 'completed' or capture['failure'] is not None or
            not capture['endIdentityVerified'] or not capture['finalStatus']['alive'] or
            not capture['candidateGraphOnly'] or capture['naturalLookupObserved'] or
            capture['atomicSnapshot'] or capture['naturalCallerObserved'] or capture['naturalWriterExcluded']):
        raise ValueError('Capture completion/identity/claim boundary differs')
    if (capture['sourceSHA256'] != LIVE_SOURCE_SHA or capture['sourceObservationSHA256'] != MONITOR_SHA or
            capture['verifiedSources']['tools/probe72_avatar_method_cache.py'] != PROBE_SHA):
        raise ValueError('Capture source binding differs')
    if (monitor['sourceSHA256'] != 'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e' or
            monitor['status'] != 'completed' or monitor['elapsedSeconds'] != 90 or not monitor['finalStatus']['alive']):
        raise ValueError('Monitor identity/status differs')
    modules = monitor['samples'][-1]['modules']
    items = [x for x in modules['items'] if x['name'].casefold() == 'yuanshen.exe']
    if (modules['error'] is not None or len(items) != 1 or items[0] != capture['mainModule'] or
            capture['pid'] != monitor['pid'] or capture['creation'] != monitor['processCreationTime'] or
            capture['creation']['winerror'] is not None):
        raise ValueError('Same-epoch process/module identity differs')
    if capture['inputChecks'] != monitor['inputs']:
        raise ValueError('Monitor/capture input/config bindings differ')
    files = {v['path']:v['sha256'] for v in capture['inputChecks']['files'].values()}
    if files != probe.prior.frozen.FIVE_INPUTS:
        raise ValueError('Capture five stock identities differ')
    records = capture['records']
    if (len(records),sum(row['requested'] for row in records)) != (141,3048):
        raise ValueError('Exact raw read inventory differs')
    if capture['budget'] != dict(reads=141,requestedBytes=3048,maxReads=3432,maxBytes=58944,
                                maxSingleRead=56,maxProbesPerQuery=32):
        raise ValueError('Exact capture budget differs')
    if capture['inputConsistency'] != dict(status='repeated-unchanged',uniqueSpans=53,firstPassReads=88):
        raise ValueError('Exact capture repeat consistency differs')
    memory = SourceMemory(records)
    generated = probe.collect(memory.read,capture['mainModule']['base'])
    for key,value in generated.items():
        if capture[key] != value:
            raise ValueError('Original collector projection differs: '+key)
    if ([x['index'] for x in capture['methods']] != list(probe.INDICES) or
            any(x['status'] != 'hit' or x['returnedIndex'] != x['index'] for x in capture['methods'])):
        raise ValueError('Selected capture method candidates differ')
    if capture['canonicalContext']['status'] != 'hit':
        raise ValueError('No canonical candidate to replay')
    return memory,dict(rawReadCount=141,rawBytes=3048,firstPassReads=88,uniqueSpans=53,
        collectorProjectionByteEquivalent=True,canonicalCount=1,canonicalType=dict(data=20060,kind=17,byRef=False),
        selectedIndices=list(probe.INDICES),methodCandidates=['hit']*8,
        candidateGraphOnly=True,naturalLookupObserved=False,atomicSnapshot=False,
        processRunningNowClaimed=False)


def add_aliases(memory,capture):
    module = capture['mainModule']['base']
    aliases = []
    for table,rvas in probe.ROOTS.items():
        for field,rva in zip(('mask','blocks','probeLimit'),rvas):
            original,target = module+rva,0x140000000+rva
            raw,error = memory.read(original,8)
            if error:
                raise ValueError('Exact root cell absent')
            if target != original:
                old,error = memory.read(target,8)
                if error:
                    memory.add('root-alias',target,raw)
                elif old != raw:
                    raise ValueError('Conflicting preferred-PE root alias')
            aliases.append(dict(table=table,field=field,rva=hex(rva),capturedAddress=hex(original),
                                nativeCell=hex(target),width=8,bytesHex=raw.hex(),pointerValueUnchanged=True))
    return aliases


def annotate_reads(native,memory,capture,aliases):
    first = capture['records'][:88]
    alias_map = {int(row['nativeCell'],16):int(row['capturedAddress'],16) for row in aliases}
    owned = [(address,address+len(raw)) for label,address,raw in memory.spans if label.startswith('owned')]
    for row in native['externalReads']:
        address,width = int(row['address'],16),row['width']
        original = alias_map.get(address,address)
        if any(a <= address and address+width <= z for a,z in owned):
            row.update(inputKind='owned-fixed-query',captureReadOrdinals=[])
            continue
        matches = [i for i,source in enumerate(first)
            if source['address'] <= original and original+width <= source['address']+source['requested'] and
               bytes.fromhex(source['bytesHex'])[original-source['address']:original-source['address']+width].hex() == row['bytesHex']]
        if not matches or row['error'] is not None:
            raise ValueError('Native read has no exact captured-span source: '+row['reader'])
        row.update(inputKind='captured-root-alias' if original != address else 'captured-span',
                   capturedAddress=hex(original),captureReadOrdinals=matches)


def isolated_replay(binary,libraries,capture,memory):
    aliases = add_aliases(memory,capture)
    target,array = OWNED,OWNED+0x100
    memory.add('owned-fixed-enum',target,probe.TARGET_TYPE)
    memory.add('owned-generic-query-array',array,struct.pack('<Q',target))
    machine = probe.Native(binary,libraries,memory)
    frame = machine.STACK+0x11000
    machine.uc.mem_write(frame-0x10,struct.pack('<Q',array))
    generic = machine.run(0x14051fba3,dict(rsi=1,r15=1),{0x14051fd12})
    generic.update(capturedMemoryUsed=True,scope='Exact repeated captured spans plus owned fixed query; source-bound replay, no natural execution timing observed.')
    generic['occupiedMatch'] = bool(machine.reg('r12') & machine.reg('rbx'))
    generic['returnedCanonical'] = hex(machine.reg('rdi'))
    expected = capture['canonicalContext']
    if (generic['failure'] is not None or generic['error'] is not None or generic['stop'] != '0x14051fd12' or
            not generic['occupiedMatch'] or int(generic['returnedCanonical'],16) != expected['pointer']):
        raise ValueError('Native GenericInst result differs from captured candidate')
    generic['probeReadSequence'] = [row['address'] for row in generic['externalReads'] if row['reader'] == '0x14051fc14']
    if generic['probeReadSequence'] != [hex(row['block']+8) for row in expected['path']]:
        raise ValueError('Native GenericInst probe-read sequence differs')
    annotate_reads(generic,memory,capture,aliases)
    methods = []
    for candidate in capture['methods']:
        current = copy.deepcopy(memory)
        query = OWNED+0x1000+candidate['index']*32
        current.add('owned-method-query',query,struct.pack('<I4xQQ',candidate['index'],expected['pointer'],0))
        machine = probe.Native(binary,libraries,current)
        result = machine.run(0x1404eea9c,dict(rsi=query,rbx=1),{0x1404efb1c,0x14052d590})
        result.update(capturedMemoryUsed=True,scope='Exact repeated captured spans plus owned fixed query; source-bound replay, no natural execution timing observed.')
        if (result['failure'] is not None or result['error'] is not None or result['stop'] != '0x1404efb1c' or
                int(result['regs']['rsi'],16) != candidate['pointer']):
            raise ValueError('Native method result differs from captured candidate: '+str(candidate['index']))
        result.update(index=candidate['index'],capturedMethodInfo=hex(candidate['pointer']),
            probeReadSequence=[row['address'] for row in result['externalReads'] if row['reader'] == '0x1404eebbc'],
            entryReadSequence=[row['address'] for row in result['externalReads'] if row['reader'] == '0x1404eec44'],
            keyIndexReadSequence=[row['address'] for row in result['externalReads'] if row['reader'] == '0x1404eec48'],
            valueReadSequence=[row['address'] for row in result['externalReads'] if row['reader'] == '0x1404eedb3'])
        if (result['probeReadSequence'] != [hex(row['block']+8) for row in candidate['path']] or
                result['entryReadSequence'] != [hex(row['entry']) for row in candidate['path']] or
                result['keyIndexReadSequence'] != [hex(row['descriptor']) for row in candidate['path']] or
                result['valueReadSequence'] != [hex(candidate['path'][-1]['entry']+8)]):
            raise ValueError('Native block/entry/key/value read sequence differs')
        annotate_reads(result,current,capture,aliases)
        methods.append(result)
    return dict(genericInst=generic,methods=methods,rootAliases=aliases,
        claimed='Original native lookup of a repeated sampled candidate graph. No observed natural caller/hit or whole constructor.',
        naturalLookupObserved=False,atomicSnapshot=False,externalWritesExecuted=False,
        nativeInvocations=9,instructionsPerInvocation=probe.LIMIT)


def continuous_replay(binary,metadata,startup,libraries,capture,output):
    """Keep six frozen cases, then bind this sample before the seventh call."""
    prior,ancestor,frozen = probe.prior,probe.prior.ancestor,probe.prior.frozen
    records,_ = frozen.base.extract(binary,metadata,startup=startup,structures=True)
    pe = frozen.base.PE(binary)
    start,end,expected=HIERARCHY
    if probe.sha(binary[pe.offset(start,end-start,executable=True):pe.offset(start)+end-start]) != expected:
        raise ValueError('Exact hierarchy producer window differs')
    snapshot = frozen.audit_snapshot(binary,metadata)
    static = ancestor.object_sources(binary,startup,records,pe,frozen.base)
    ancestor.relocate_ancestors(json.loads((ROOT/ancestor.FRESH).read_bytes()),snapshot,
        binary,metadata,startup,pe,frozen.base,static)
    ancestor.provide_interface_rows(snapshot,binary,metadata,startup,pe,frozen.base,records)
    parameter = ancestor.prior.generic_parameter(snapshot,binary,metadata,pe,frozen.base)
    bridge = prior.bind_interfaces(json.loads((ROOT/prior.INPUT).read_bytes()),snapshot,
        binary,metadata,pe,frozen.base)
    extra = prior.parameter362(parameter,binary,metadata,pe,frozen.base)
    projection = prior.fixed_method_indices(snapshot,records)
    captured = SourceMemory(capture['records'])
    for ordinal,(_,address,raw) in enumerate(captured.spans):
        ancestor.add_span(snapshot,'cold.methodCacheCapture.'+str(ordinal),address,raw)
    aliases = add_aliases(copy.deepcopy(captured),capture)
    previous = json.loads((ROOT/prior.ANCESTOR_RAW).read_bytes())
    frozen_prefix = json.loads((ROOT/probe.PRIOR_REPORT).read_bytes())['fixedMethodProjection']['cold']
    original_engine,original_windows = frozen.base.Offline72,prior.DIRECT_WINDOWS
    state = dict(factoryEntries=0,injected=False,inputBinding=[],capturedReads=[],capturedWriteAttempts=[],
                 methodQueries=[],methodReturns=[],canonicalReturn=[],newUnknownReads=[],allocationStop=None)
    alias_map={int(row['nativeCell'],16):int(row['capturedAddress'],16) for row in aliases}
    pages = {page for _,a,raw in captured.spans for page in range(a&~4095,(a+len(raw)+4095)&~4095,4096)}
    class Overlay(original_engine):
        def __init__(self,*args,**kwargs):
            super().__init__(*args,**kwargs)
            def read_guard(uc,access,address,width,value,data):
                if state['injected'] and (address&~4095 in pages or address in alias_map):
                    raw,error = captured.read(alias_map.get(address,address),width)
                    row = dict(address=hex(address),width=width,reader=hex(self.reg('rip')),bytesHex=raw.hex(),error=error)
                    state['capturedReads'].append(row)
                    if error:
                        state['newUnknownReads'].append(row)
                        self.failure='Unprovided fresh cache captured-page bytes'
                        uc.emu_stop()
            def write_guard(uc,access,address,width,value,data):
                if state['injected'] and (address&~4095 in pages or
                        any(int(row['nativeCell'],16) <= address and address+width <= int(row['nativeCell'],16)+8 for row in aliases)):
                    state['capturedWriteAttempts'].append(dict(address=hex(address),width=width,writer=hex(self.reg('rip'))))
                    self.failure='Native write to frozen fresh cache input'
                    uc.emu_stop()
            self.hooks.extend((self.uc.hook_add(self.U.UC_HOOK_MEM_READ,read_guard),
                               self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE,write_guard)))

        def _code(self,uc,address,size,data):
            if address == 0x159b6eb50:
                state['factoryEntries'] += 1
                if state['factoryEntries'] == 2:
                    for row in aliases:
                        self.write(int(row['nativeCell'],16),int.from_bytes(bytes.fromhex(row['bytesHex']),'little'))
                    state['inputBinding']=aliases
                    state['injected']=True
            if state['injected'] and address == 0x1404e0d18:
                state['canonicalReturn'].append(hex(self.reg('rax')))
            if state['injected'] and address == 0x1404eea50:
                pointer=self.reg('rcx')
                raw=bytes(self.uc.mem_read(pointer,24))
                state['methodQueries'].append(dict(index=struct.unpack_from('<I',raw)[0],
                    classInstance=hex(struct.unpack_from('<Q',raw,8)[0]),methodInstance=hex(struct.unpack_from('<Q',raw,16)[0])))
            if state['injected'] and address == 0x14051e3b2:
                state['methodReturns'].append(dict(index=state['methodQueries'][-1]['index'],pointer=hex(self.reg('rax'))))
            if state['injected'] and address in (0x14052d590,0x140526fb0):
                self.failure='Fresh selected lookup reached unprovided miss writer'
                uc.emu_stop()
                return
            if state['injected'] and address == 0x1405091d0 and self.read(self.reg('rsp')) == 0x14051f2ff:
                state['allocationStop']=dict(entry=hex(address),callerReturn='0x14051f2ff',
                    owner=hex(self.reg('rcx')),width=self.reg('rdx'),ownedClass=hex(self.reg('rsi')),
                    depth=self.read(self.reg('rsi')+0xc4,'B'),newStorageSupplied=False)
                self.failure='Unprovided hierarchy owned storage allocation'
                uc.emu_stop()
                return
            return super()._code(uc,address,size,data)
    frozen.base.Offline72=Overlay
    prior.DIRECT_WINDOWS={**original_windows,0x1404f0640:probe.WINDOWS[0x1404f0640],
                          0x14052d590:probe.WINDOWS[0x14052d590],0x140526fb0:probe.WINDOWS[0x140526fb0],
                          HIERARCHY[0]:(HIERARCHY[1],HIERARCHY[2])}
    try:
        result=prior.continue_native(binary,metadata,startup,records,snapshot,parameter,extra,previous,bridge,libraries,output)
    finally:
        frozen.base.Offline72=original_engine
        prior.DIRECT_WINDOWS=original_windows
    if not state['injected'] or len(result['native']) != 7:
        raise ValueError('Fresh input binding did not occur at the exact seventh call')
    for current,old in zip(result['native'][:6],frozen_prefix['native'][:6]):
        if any(current[k] != old[k] for k in ('name','failure','error','regs')) or current['trace']['pathSHA256'] != old['trace']['pathSHA256']:
            raise ValueError('Frozen six-prefix case changed')
    if state['canonicalReturn'] != [hex(capture['canonicalContext']['pointer'])]:
        raise ValueError('Original factory did not resolve the fresh canonical by content')
    if state['methodReturns'] != [dict(index=x['index'],pointer=hex(x['pointer'])) for x in capture['methods']]:
        raise ValueError('Original method loop did not return all eight sampled values')
    if (result['native'][-1]['failure'] != 'Unprovided hierarchy owned storage allocation' or
            result['native'][-1]['error'] is not None or state['capturedWriteAttempts'] or state['newUnknownReads'] or
            result['extension']['windowRequests'] or result['models']['callbackFailures']):
        raise ValueError('Continuous case did not reach the exact bounded storage gap')
    stop=state['allocationStop']
    if (stop['width'],stop['depth'],stop['ownedClass']) != (16,2,result['newClasses'][1]['pointer']):
        raise ValueError('Exact hierarchy allocation width/class differs')
    arrays=result['extension']['methodPointerStorage']['allocations']
    if len(arrays) != 1 or arrays[0]['bytesHex'] != b''.join(struct.pack('<Q',x['pointer']) for x in capture['methods']).hex():
        raise ValueError('Original method-pointer-array writeback differs')
    annotate_reads(dict(externalReads=state['capturedReads']),captured,capture,aliases)
    result['captureExtension']=state
    result['fixedMethodProjectionSource']=projection
    result['hierarchyWindow']=dict(start=hex(HIERARCHY[0]),end=hex(HIERARCHY[1]),width=HIERARCHY[1]-HIERARCHY[0],sha256=HIERARCHY[2])
    result['frozenSixPrefixUnchanged']=True
    result['completeColdComparer']=False
    result['defaultComparerWriteExecuted']=False
    result['completeAvatarHandler']=False
    return result


def negative_controls(binary,libraries,capture):
    cases=[]
    canonical=capture['canonicalContext']['pointer']
    for name in ('missing-key-byte','truncated-canonical','unprovided-record-tail','forbidden-input-write'):
        memory=SourceMemory(capture['records'])
        add_aliases(memory,capture)
        query=OWNED+0x200000
        memory.add('owned-negative-query',query,struct.pack('<I4xQQ',10287,
            canonical&0xffffffff if name == 'truncated-canonical' else canonical,0))
        if name == 'missing-key-byte':
            hole=capture['methods'][0]['path'][0]['descriptor']+3
            split=[]
            for label,address,raw in memory.spans:
                if address <= hole < address+len(raw):
                    if hole>address:split.append((label,address,raw[:hole-address]))
                    if hole+1<address+len(raw):split.append((label,hole+1,raw[hole-address+1:]))
                else:split.append((label,address,raw))
            memory.spans=split
        machine=probe.Native(binary,libraries,memory)
        if name == 'unprovided-record-tail':
            result=machine.run(0x1404f0740,dict(rcx=capture['methods'][-1]['pointer']+48),set())
        elif name == 'forbidden-input-write':
            result=machine.run(0x140527095,dict(rcx=capture['methods'][0]['pointer'],rbx=0,rax=0x8000000100000001),set())
        else:
            result=machine.run(0x1404eea9c,dict(rsi=query,rbx=1),{0x1404efb1c,0x14052d590})
        result.update(name=name,scope='Rejected independent control; not an accepted capture projection or natural case.')
        cases.append(result)
    return cases


def next_contract(continuous,capture):
    stop=continuous['captureExtension']['allocationStop']
    return dict(task='R1-HIERARCHY-STORAGE',source='Exact original hierarchy producer and bounded native request',
        currentStop=stop,producer=continuous['hierarchyWindow'],
        minimumInput=dict(kind='explicit owned typed storage, not a new natural pointer sample',bytes=16,
            tailCanaryBytes=8,definition=1234,hierarchyDepth=2,
            ownerRule='Current snapshot cold.registry.cell only; do not use the captured fresh method-cache owner or a historical pool address.',
            classRule='The second owned400B class, GenericClass definition1234 and current fresh canonical context; +48 remains null before this exact producer.'),
        exactBoundaries=[dict(entry='0x1405091d0',callerReturn='0x14051f2ff',width=16,action='allocate dirty owned16B only'),
            dict(entry='0x141fdf9c0',callerReturn='0x14051f30f',width=16,action='zero exact owned range, preserve tail'),
            dict(entry='0x141fdf9c0',callerReturn='0x14051f321',width=16,action='second original clear, preserve tail'),
            dict(entry='0x141fdf310',callerReturn='0x14051f35c',width=8,
                 action='copy only the existing pinned System.Object hierarchy8B into owned16B; do not grant arbitrary copy')],
        originalStores=['14051f333 writes this owned class to the final slot',
            '14051f361 xchg publishes the owned array to class+48; keep the original instruction'],
        existingSource='Original99-span System.Object header/hierarchy and current owned class fields; no unprovided parent/type/method byte is inferred.',
        newNaturalBytesRequiredForThisGap=0,newLiveSamplingRequiredForThisGap=False,
        executionCondition='Future bounded implementation must separately approve these exact typed storage boundaries; current card does not implement any of them.',
        nextStop='Any other owner/caller/size, tail change, unprovided read, new native window or captured input write stops; do not preapprove a later24B array.',
        capturedContext=dict(captureSHA256=CAPTURE_SHA,canonicalValuePreserved=True,
            methodInfoOwnerPointeesProvided=False,
            noOwnerRewrite='MethodInfo+0 owner pointers remain captured opaque values; never rewrite them to the owned classes.',
            graph='Owned GenericClass/class models and old captured ancestors plus fresh cache spans are an explicit controlled combination, not one natural runtime graph.'),
        completeColdComparer=False,completeClassInitialization=False,defaultComparerWriteExecuted=False,
        completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--python-libs',type=Path,default=ROOT/'local/migration72/python')
    args = parser.parse_args()
    if not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('Output must stay in the dedicated replay directory')
    if any(path.exists() for path in (args.output,args.output.with_suffix('.source.py'),args.output.with_suffix('.next-input.private.json'))):
        parser.error('Prior raw/source is frozen; choose a new output')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.with_suffix('.source.py').write_bytes(Path(__file__).read_bytes())
    for path,expected in PINS.items():
        probe.prior.ancestor.prior.read_pinned(path,expected)
    binary,metadata,startup,*_ = [probe.prior.frozen.base.read_verified(args.client/name,digest)
        for name,digest in probe.prior.frozen.FIVE_INPUTS.items()]
    probe.prior.frozen.base.load_emulator(args.python_libs)
    pins,windows,context_source = probe.verify_sources(binary,metadata)
    capture = json.loads((ROOT/CAPTURE).read_bytes())
    monitor = json.loads((ROOT/MONITOR).read_bytes())
    memory,audit = audit_capture(capture,monitor)
    replay = isolated_replay(binary,args.python_libs,capture,memory)
    continuous=continuous_replay(binary,metadata,startup,args.python_libs,capture,args.output)
    negatives=negative_controls(binary,args.python_libs,capture)
    following=next_contract(continuous,capture)
    report = dict(task='R1-METHOD-CACHE-REPLAY',sourceSHA256=probe.sha(Path(__file__).read_bytes()),
        sourcePins={**pins,**PINS},inputSHA256=probe.prior.frozen.FIVE_INPUTS,
        captureSHA256=CAPTURE_SHA,monitorSHA256=MONITOR_SHA,inputAudit=audit,
        codeWindows=[{k:v for k,v in row.items() if k != 'instructions'} for row in windows],
        contextSource=context_source,isolated=replay,continuous=continuous,negativeControls=negatives,nextInputContract=following,
        continuousPrefixIntegrated=True,completeColdComparer=False,completeClassInitialization=False,
        defaultComparerWriteExecuted=False,completeAvatarHandler=False,targetProtocolReady=False,d02Complete=False)
    args.output.write_bytes((json.dumps(report,indent=2)+'\n').encode())
    args.output.with_suffix('.next-input.private.json').write_bytes((json.dumps(following,indent=2)+'\n').encode())
    print(json.dumps(dict(task=report['task'],nativeInvocations=20,methods=8,
        continuousFailure=continuous['native'][-1]['failure'],continuousError=continuous['native'][-1]['error'],
        sourceBoundGraphReplay=True,naturalLookupObserved=False,completeAvatarHandler=False)))


if __name__ == '__main__':
    main()
