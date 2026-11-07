"""Bounded Avatar closure research: captured cold state and actual Int32 NativeConfig."""
import argparse
import hashlib
import json
import struct
from collections import Counter
from pathlib import Path

try:
    from .client72_avatar_cold_contract import base, RETURN, ColdRuntime
    from .client72_avatar_cold_interfaces import FIVE_INPUTS
    from .client72_emulator import STACK, END, GLOBAL
    from .client72_seed_crypto import import_slots
    from .extract_client72 import METADATA_REGISTRATION
    from . import client72_avatar_natural_tables as card
except ImportError:
    from client72_avatar_cold_contract import base, RETURN, ColdRuntime
    from client72_avatar_cold_interfaces import FIVE_INPUTS
    from client72_emulator import STACK, END, GLOBAL
    from client72_seed_crypto import import_slots
    from extract_client72 import METADATA_REGISTRATION
    import client72_avatar_natural_tables as card

RAW_SHA = '63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0'
SAMPLER_SHA = '2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae'
OBSERVATION_SHA = '303db4bc6daa85d3fda6d73f52cbe339021ba84cfaa45152bb4bf92a1746c58a'
TABLE_TOOL_SHA = '920395dfd4817523951d2c766167ca481d8f6b2b64288980fee2cade2ae9ecad'
SAMPLING_CONTRACT_SHA = '6503b65872f093d503c2db8668607542c0a5d3a336002e829e5aa82b924734a4'
RAW_DIRECTORY = Path('local/migration72/d02-active/avatar/handler-closure')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if not args.output.resolve().is_relative_to(RAW_DIRECTORY.resolve()):
        parser.error('Output must stay in this contract ignored directory')
    b, m, s, *_ = [base.read_verified(args.client / name, digest) for name, digest in FIVE_INPUTS.items()]
    base.read_verified(Path(card.__file__), TABLE_TOOL_SHA)
    snapshot = audit_snapshot(b, m)
    base.load_emulator(args.python_libs)
    records, _ = base.extract(b, m, startup=s, structures=True)
    cells = base.type_cells(b, m, s, records)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    sampling = json.loads(base.read_verified(RAW_DIRECTORY / 'native-config-sampling-contract.json', SAMPLING_CONTRACT_SHA))
    pe = base.PE(b)
    for window in sampling['codeWindows'].values():
        start = int(window['start'], 16)
        raw = b[pe.offset(start):pe.offset(start) + window['bytes']]
        if hashlib.sha256(raw).hexdigest() != window['sha256']:
            raise ValueError('Sampling source window changed')
    actual_fields = {row['index']: row for row in records[11325]['fields']}
    if any(actual_fields[row['index']] != row for row in sampling['sourceMetadata']['fields']):
        raise ValueError('NativeConfig source field metadata changed')
    cold = run_cold(b, m, s, records, snapshot, args.python_libs, args.output.with_suffix('.cold.json'))
    config = run_config(b, m, s, records, cells, args.python_libs, args.output.with_suffix('.config.json'))
    consumers = run_consumers(b, m, s, records, cells, args.python_libs, args.output.with_suffix('.consumers.json'))
    report = dict(task='D02-R1-handler-closure-bounded', inputSHA256=FIVE_INPUTS,
        sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        dependencies={name: hashlib.sha256(Path('tools', name).read_bytes()).hexdigest() for name in (
            'client72_avatar_natural_tables.py', 'client72_avatar_cold_contract.py', 'client72_avatar_cold_interfaces.py',
            'client72_emulator.py', 'client72_session_runtime.py', 'client72_seed_crypto.py', 'extract_client72.py')},
        cold=cold, nativeConfig=config, resourceConsumers=consumers,
        snapshot=dict(rawSHA256=RAW_SHA, samplerSHA256=SAMPLER_SHA, observationSHA256=OBSERVATION_SHA,
                      readsVerified=99, ordinaryMetadataRecordsVerified=13, atomicSnapshot=False),
        samplingContractSHA256=SAMPLING_CONTRACT_SHA,
        sourceWindows=sampling['codeWindows'], completeColdComparer=False, defaultComparerWriteExecuted=False,
        completeAvatarHandler=False, targetProtocolReady=False, naturalNativeConfigModeObserved=False,
        firstColdBlocker=dict(reader='0x1404ed9c3', field='uncaptured parent RuntimeType descriptor +8, width4',
            chain='classCache[1239] -> header+b0 compact offset -> registry base -> parent class+10 descriptor',
            minimumFutureReadBytes=16, oldPointerMustNotBeReused=True,
            reason='Captured class1239 names a parent pointer, but its descriptor bytes were never read; mapped-page zero must not become input.'),
        scope='Current offline experiments only. Whole handler/default write, natural mode and production use remain unproved. Further input collection is deferred under the user pause instruction.')
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(coldCases=len(cold['native']), configCases=len(config['cases']), consumers=len(consumers['consumers']), completeAvatarHandler=False)))


def audit_snapshot(b, m):
    path=Path('local/migration72/d02-active/main/cold-arrays-tool/live-elevated1.json');raw=path.read_bytes();assert hashlib.sha256(raw).hexdigest()=='63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0';sample=json.loads(raw);reads={r['label']:r for r in sample['reads']};assert len(reads)==99
    source=Path('local/migration72/d02-active/main/cold-arrays-tool/sampler-planned-v6.py');assert hashlib.sha256(source.read_bytes()).hexdigest()==sample['sourceSHA256']=='2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae'
    data={}
    for label,row in reads.items():
     value=bytes.fromhex(row['bytesHex']);assert row['error'] is None and len(value)==row['length']<=256 and hashlib.sha256(value).hexdigest()==row['sha256'];data[label]=value
    def q(label,off=0):return struct.unpack_from('<Q',data[label],off)[0]
    def u(label,off=0):return struct.unpack_from('<I',data[label],off)[0]
    module=sample['mainModule']['base'];cache=q('cold.classCache.cell');classes={};states=[]
    for idx in (117,204,241,243,248,284,314,371,405,513,531,1239,20060):
     prefix='cold.class'+str(idx);pointer=q('cold.classCache.entry'+str(idx));classes[pointer]=idx
     assert reads['cold.classCache.entry'+str(idx)]['address']==cache+idx*8 and reads[prefix+'.header']['address']==pointer and q(prefix+'.header',0x10)==idx
     assert reads[prefix+'.metadata']['address']==q(prefix+'.header',0x58) and len(data[prefix+'.metadata'])==70
     assert reads[prefix+'.hierarchy']['address']==q(prefix+'.header',0x48) and len(data[prefix+'.hierarchy'])==data[prefix+'.header'][0xc4]*8
     count=data[prefix+'.metadata'][0x43]^0x25
     if count:assert reads[prefix+'.interfaces']['address']==q(prefix+'.header',0x88) and len(data[prefix+'.interfaces'])==count*8
     states.append(dict(type=idx,kind=data[prefix+'.header'][0x1a],c7=data[prefix+'.header'][0xc7],c8=data[prefix+'.header'][0xc8],c9=data[prefix+'.header'][0xc9],ca=data[prefix+'.header'][0xca],genericInstPointerNull=q(prefix+'.header',0x68)==0,interfaceCount=count,depth=data[prefix+'.header'][0xc4]))
    for state in states:
     label='cold.class'+str(state['type'])+'.hierarchy';state['hierarchy']=[classes.get(x,{'unreadClassPointer':hex(x)}) for x, in struct.iter_unpack('<Q',data[label])]
    assert q('cold.type314Default.cell')==q('cold.classCache.entry314') and q('cold.array117Default.cell')==q('cold.classCache.entry117')
    assert q('cold.runtime284.slot21')-module==0x19b87c70 and q('cold.runtime284.slot82')-module==0x19b6ec60
    assert reads['cold.runtime284.slot21']['address']==q('cold.classCache.entry284')+0x178
    assert reads['cold.runtime284.slot82']['address']==q('cold.classCache.entry284')+0x360
    descriptor=data['cold.type314Default.header'][0x10:0x20];bits=int.from_bytes(descriptor[8:12],'little');h=(((bits>>16)&255)*0x1cfaa2db+((bits>>30)&1))*0x1cfaa2db+int.from_bytes(descriptor[:8],'little');h&=0xffffffffffffffff
    mask=q('cold.typeArrayCache.mask');bucket=h&mask;occupied=q('cold.typeArrayCache.firstBlock',8);bit=1<<(bucket&63);rank=(occupied&(bit-1)).bit_count()
    assert occupied&bit and reads['cold.typeArrayCache.firstBlock']['address']==q('cold.typeArrayCache.blocks')+(bucket>>6)*32
    assert reads['cold.typeArrayCache.firstEntry']['address']==q('cold.typeArrayCache.firstBlock')+rank*16
    assert q('cold.typeArrayCache.firstEntry')==q('cold.type314Default.cell') and q('cold.typeArrayCache.firstEntry',8)==q('cold.typeArrayUsage.cell')
    assert reads['cold.typeArrayCache.firstKey.header']['address']==q('cold.typeArrayCache.firstEntry') and data['cold.typeArrayCache.firstKey.header']==data['cold.type314Default.header']
    assert reads['cold.typeArrayCache.firstValue.header']['address']==q('cold.typeArrayUsage.cell') and data['cold.typeArrayCache.firstValue.header']==data['cold.typeArrayUsage.header']
    assert data['cold.typeArrayUsage.header'][0x1a]==29 and q('cold.typeArrayUsage.header',0x10)==q('cold.type314Default.cell')+0x10
    pe=base.PE(b);header=pe.offset(base.HEADER_VA);table=base.BODY+((base.number(b,header+0x64)+0xa7932555)&0xffffffff)
    for state in states:
     assert data['cold.class'+str(state['type'])+'.metadata']==m[table+state['type']*70:table+(state['type']+1)*70]
    base.read_verified(Path('local/migration72/d02-active/plain/run-elevated3.json'),OBSERVATION_SHA)
    assert sample['sourceObservationSHA256']==OBSERVATION_SHA
    assert len({reads['cold.class'+str(row['type'])+'.metadata']['address']-row['type']*70 for row in states})==1
    return dict(sample=sample,reads=reads,data=data,states=states)


def run_config(b, m, s, r, cells, libraries, out):
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    p=base.PE(b);md=Cs(CS_ARCH_X86,CS_MODE_64);md.detail=True
    rt=card.NaturalRuntime(b,m,s,r,cells,libraries,'AvatarOrnamentGroup');e=rt.e
    global_fields={f['name']:f for f in r[11325]['fields']};st=global_fields['_serializedCodeSwitches']['type'];lt=global_fields['codeSwitches']['type']
    assert r[11325]['name']=='GlobalVars' and st['typeDefinition']==2147 and lt['typeDefinition']==1255 and st['instanceIndex']==lt['instanceIndex']==6 and st['arguments'][0]['kind']==lt['arguments'][0]['kind']=='int32'
    setclass=rt.bind_type(st);listclass=rt.bind_type(lt);intarray=rt.bind_type(dict(kind='szarray',elementType=lt['arguments'][0],rank=1))
    aot=base.selected_aot(b,m,s,r,{(1235,6):('GetHashCode','Equals','.ctor')});eq=next(x for x in aot if x['methodDefinition']==10296);hs=next(x for x in aot if x['methodDefinition']==10297)
    assert eq['address']=='0x157d873d0' and hs['address']=='0x157d873e0'
    cmpt=next(x['type'] for x in cells.values() if x['type'].get('typeDefinition')==1235 and x['type'].get('instanceIndex')==6);cmpclass=rt.bind_type(cmpt);comparer=e.alloc(16);e.write(comparer,cmpclass)
    rt.bind_function(0x157485660)
    ifcell=next(int(x['target'],16) for x in rt.functions['0x157485660']['references'] if int(x['target'],16) in cells and cells[int(x['target'],16)]['type'].get('name')=='IEqualityComparer`1')
    assert cells[ifcell]['type']['arguments'][0]['kind']=='int32'
    interfaces=e.alloc(16);e.write(interfaces,e.read(ifcell));e.write(interfaces+8,8,'I');e.write(cmpclass+0x80,interfaces);e.write(cmpclass+0xc0,1,'H');e.write(cmpclass+0xbe,12,'H')
    leafs={}
    for slot,row in ((8,eq),(9,hs)):
     a=int(row['address'],16);ins=[]
     for x in md.disasm(b[p.offset(a):p.offset(a)+32],a):
      ins.append(x)
      if x.mnemonic=='ret':break
     assert ins[-1].mnemonic=='ret' and len(ins)<=5 and all(x.mnemonic not in ('call','jmp') for x in ins)
     end=ins[-1].address+ins[-1].size;e.allowed.append((a,end));leafs[hex(a)]=dict(sha256=hashlib.sha256(b[p.offset(a):p.offset(a)+end-a]).hexdigest(),instructions=[hex(x.address)+' '+x.mnemonic+' '+x.op_str for x in ins])
     info=e.alloc(64);e.write(info,cmpclass);e.write(info+8,a);e.write(cmpclass+0xd0+slot*8,a);e.write(cmpclass+0xd0+(12+slot)*8,info)
    rt.bind_function(0x157484f80)
    for ptr,typ in rt.type_records.items():
     if typ.get('typeDefinition')==1234 and typ.get('instanceIndex')==6:
      statics=e.alloc(8);e.write(statics,comparer);e.write(ptr+0x38,statics)
    oldallocate=rt.allocate_object
    def allocate_config():
     typ=rt.type_records[e.reg('rcx')]
     if typ.get('typeDefinition')==2147:
      if typ.get('instanceIndex')!=6 or typ['arguments'][0]['kind']!='int32':raise ValueError('GlobalVars requires HashSet<Int32>')
      ptr=e.alloc(0x48);e.write(ptr,e.reg('rcx'));rt.objects[ptr]=2147;return ptr
     return oldallocate()
    e.models[0x1405336c0]=allocate_config
    e.allowed.append((0x146d41fcc,0x146d42021))
    results=[]
    try:
     for name,serialized,codes,want in (
      ('empty-code-config-native',[],[],True),('serialized3521-managed',[3521],[],False),('list3521-managed',[],[3521],False),
      ('list6506-managed',[],[6506],False),('list6104-clears-mode',[],[6104],False),('serialized6506-is-not-list6506',[6506],[],True),
      ('signed-hash-collision-not-3521',[0x80000dc1],[],True),('signed-list-value-not-6104',[],[0x800017d8],True)):
      rt.bind_function(0x146d41bb0)
      ctor=rt.call(0x146d41fcc,stops=(0x146d42021,));assert ctor['stop']=='0x146d42021'
      setobj=e.read(GLOBAL+0x60e8);listobj=e.read(GLOBAL+0x6100);assert e.read(setobj)==setclass and e.read(listobj)==listclass and e.read(setobj+0x18)==comparer
      slots=rt.array(7,12);buckets=rt.array(7,4);e.write(setobj+0x10,slots);e.write(setobj+0x20,buckets)
      adds=[]
      for value in serialized:
       trace=rt.call(0x1574872b0,rcx=setobj,edx=value);assert trace['stop']==hex(card.RETURN) and e.reg('rax')&255;adds.append(trace)
      items=rt.array(len(codes),4);e.write(items,intarray)
      for pos,value in enumerate(codes):e.write(items+0x20+pos*4,value,'I')
      e.write(listobj+0x10,items);e.write(listobj+0x18,len(codes),'I')
      writer=rt.call(0x14f585f00,stops=(0x14f585f7d,0x14f585f9c));mode=e.read(0x14586ceb2,'B');assert bool(mode)==(6104 not in codes)
      queries=[]
      for key in (3521,6506):
       t=rt.call(0x14d5b9fa0,ecx=key);queries.append(dict(kind='serialized',key=key,value=bool(e.reg('rax')&255),trace=t))
       t=rt.call(0x14d581600,ecx=key);queries.append(dict(kind='list',key=key,value=bool(e.reg('rax')&255),trace=t))
      model=e.alloc(0x48);decision=rt.call(0x1484451b0,stops=(0x148445202,0x14844526a),rcx=model)
      native=decision['stop']=='0x14844526a';assert native==want
      results.append(dict(name=name,serializedInt32Bits=[hex(x) for x in serialized],codesInt32Bits=[hex(x) for x in codes],nativeSelected=native,mode=mode,configCtor=ctor,adds=adds,writer=writer,queries=queries,constructorDecision=decision))
     bad=rt.bind_type(r[61440]['fields'][1]['type']);e.uc.reg_write(e.R.UC_X86_REG_RCX,bad)
     try:allocate_config();raise AssertionError('UInt32 config class accepted')
     except ValueError as err:wrong=str(err)
     report=dict(sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),owner=11325,fields=[global_fields[n] for n in ('_serializedCodeSwitches','codeSwitches','enableExcelNativeConfigCodeSwitch')],aot=aot,comparerLeafs=leafs,cases=results,wrongUInt32Rejected=wrong,nativeSources=rt.functions,models='Typed managed allocation and prepared original Int32 comparer/RGCTX; actual GlobalVars constructor fragment, native HashSet Add/Contains, actual primitive Int32 comparer and normal-path code-switch writer/constructor decision. No natural mode or full constructor claimed.',naturalModeObserved=False,completeAvatarHandler=False,targetProtocolReady=False)
     out.write_text(json.dumps(report,indent=2)+'\n','utf8');return report
    except Exception:
     out.with_suffix('.failure.json').write_text(json.dumps(dict(completed=results,failure=e.failure,regs={k:hex(e.reg(k)) for k in ('rip','rcx','rdx','r8','rax')},calls=rt.call_order[-25:],models=e.calls,nativeSources=rt.functions),indent=2)+'\n','utf8');raise
    finally:e.close()


def run_cold(b, m, s, records, snapshot, libraries, out):
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    sample,reads,data,states=(snapshot[key] for key in ('sample','reads','data','states'))
    module=sample['mainModule']['base'];pe=base.PE(b)
    def q(label,off=0):return struct.unpack_from('<Q',data[label],off)[0]
    e=base.Offline72(b,libraries,records,trace_instructions=True);e.uc.mem_map(RETURN&~4095,4096);e.uc.mem_write(RETURN,b'\x90');e.prepare();e.allow_unwind(0x159b6eb50)
    rt=ColdRuntime(e);pages=set();known=[];violations=[];functions={};order=[];allocations=[];md=Cs(CS_ARCH_X86,CS_MODE_64)
    for label,row in reads.items():
     if not label.startswith('cold.'):continue
     a=row['address'];z=a+row['length'];page=a&~4095
     while page<z:
      if page not in pages:e.uc.mem_map(page,4096);pages.add(page)
      page+=4096
     e.uc.mem_write(a,data[label]);known.append((a,z,label))
    for address,label in ((0x145bc8f18,'cold.classCache.cell'),(0x145bc8ad0,'cold.registry.cell'),(0x145bc97a8,'cold.type314Default.cell'),(0x145bc9868,'cold.array117Default.cell'),(0x145886658,'cold.typeArrayUsage.cell'),(0x1458869b8,'cold.classCache.entry284'),(0x145bc8ef0,'cold.options.cell'),(0x145bc9638,'cold.canonical.cell'),(0x145bc7160,'cold.typeArrayCache.mask'),(0x145bc7180,'cold.typeArrayCache.blocks'),(0x145bc7188,'cold.typeArrayCache.probeLimit')):e.write(address,q(label))
    e.write(0x145bc8ee0,0x1428a53c8);e.write(0x145bc7218,rt.lock)
    for label in ('cold.runtime284.slot21','cold.runtime284.slot82'):
     observed=q(label);assert observed>module;e.write(reads[label]['address'],0x140000000+observed-module)
    c284=q('cold.classCache.entry284');argument=e.alloc(32);definition=e.alloc(32)
    e.write(argument,c284);e.write(argument+0x10,q('cold.classCache.entry20060')+0x10)
    e.write(definition,c284);e.write(definition+0x10,q('cold.classCache.entry1239')+0x10)
    def allocate_type_array():
     assert e.reg('rcx')==q('cold.typeArrayUsage.cell') and e.reg('rdx')==1 and e.read(e.reg('rsp'))==0x159b6eb6e
     pointer=e.alloc(48);e.write(pointer,e.reg('rcx'));e.write(pointer+0x18,1);e.write(pointer+0x28,0xfedcba9876543210);allocations.append(pointer);return pointer
    e.models[0x140514ee0]=allocate_type_array
    vector_allocations={}
    def allocate_vector():
     assert e.reg('rcx')==8 and e.read(e.reg('rsp'))==0x1404e4da8
     pointer=e.alloc(8);e.uc.mem_write(pointer,b'\xcc'*8);vector_allocations[pointer]=8;return pointer
    def copy_vector():
     assert e.read(e.reg('rsp'))==0x1404e4dc1 and e.reg('r8')==0
     return e.reg('rcx')
    def free_vector():
     assert e.read(e.reg('rsp'))==0x1404e0de7 and vector_allocations.pop(e.reg('rcx'))==e.reg('rdx')==8
     return 0
    e.models[0x141a2e150]=allocate_vector;e.models[0x141fdf310]=copy_vector;e.models[0x141cbfe48]=free_vector

    critical=e.alloc(40);critical_stack=[];critical_events=[]
    def enter_critical():
     pointer=e.reg('rcx')
     if pointer not in (critical,class_lock) or (critical_stack and critical_stack[-1]!=pointer) or len(critical_stack)>=8:raise ValueError('Undeclared critical-section ownership')
     critical_stack.append(pointer);critical_events.append(dict(action='enter',pointer=hex(pointer),depth=len(critical_stack)));return 0
    def leave_critical():
     pointer=e.reg('rcx')
     if not critical_stack or critical_stack[-1]!=pointer:raise ValueError('Unbalanced critical section')
     critical_stack.pop();critical_events.append(dict(action='leave',pointer=hex(pointer),depth=len(critical_stack)));return 0
    im=import_slots(e.pe);assert im['EnterCriticalSection']==0x143bac1f0 and im['LeaveCriticalSection']==0x143bac5f8
    e.write(0x143bac1f0,e.model_stub(enter_critical));e.write(0x143bac5f8,e.model_stub(leave_critical));e.write(0x145bc8ba8,critical)
    block=e.alloc(32);frame=e.alloc(1024)
    for address,value in ((0x145bc8c18,63),(0x145bc8c38,block),(0x145bc8c40,64),(0x145bc8c58,64),(0x145bc8c60,64)):e.write(address,value)
    def allocate_packed():
     if e.reg('rcx')!=32 or e.read(e.reg('rsp'))!=0x1404f239a:raise ValueError('Unexpected packed-entry allocation')
     pointer=e.alloc(32);e.uc.mem_write(pointer,b'\xcc'*32);return pointer
    def free_empty_packed():
     if e.reg('rcx')!=0 or e.read(e.reg('rsp'))!=0x1404f2645:raise ValueError('Only initial null packed storage can be freed')
     return 0
    e.models[0x141c41728]=allocate_packed;e.models[0x141c3ee1c]=free_empty_packed
    e.allowed.append((0x14053f737,0x14053fb4d))


    # Explicit empty generic-class cache and source-derived registered class records.
    generic_lock=e.alloc(8);shared_depth=None;shared_events=[]
    def shared_enter():
     nonlocal shared_depth
     pointer=e.reg('rcx')
     if pointer not in (rt.lock,generic_lock) or shared_depth is not None:raise ValueError('Undeclared shared lock')
     shared_depth=pointer;shared_events.append(['shared-enter',hex(pointer)]);return 0
    def shared_leave():
     nonlocal shared_depth
     pointer=e.reg('rcx')
     if pointer!=shared_depth:raise ValueError('Unbalanced shared lock')
     shared_depth=None;shared_events.append(['shared-leave',hex(pointer)]);return 0
    def exclusive_enter():
     nonlocal shared_depth
     if e.reg('rcx')!=generic_lock or shared_depth is not None:raise ValueError('Undeclared exclusive lock')
     shared_depth=generic_lock;shared_events.append(['exclusive-enter',hex(generic_lock)]);return 0
    def exclusive_leave():
     nonlocal shared_depth
     if e.reg('rcx')!=generic_lock or shared_depth!=generic_lock:raise ValueError('Unbalanced exclusive lock')
     shared_depth=None;shared_events.append(['exclusive-leave',hex(generic_lock)]);return 0
    assert im['AcquireSRWLockExclusive']==0x143bac0f0 and im['ReleaseSRWLockExclusive']==0x143bac700
    for address,fn in ((0x143bac0f8,shared_enter),(0x143bac708,shared_leave),(0x143bac0f0,exclusive_enter),(0x143bac700,exclusive_leave)):e.write(address,e.model_stub(fn))
    e.write(0x145bc7490,generic_lock)
    generic_block=e.alloc(32)
    for offset,value in ((8,63),(0x10,generic_block),(0x18,generic_block+32),(0x28,generic_block),(0x30,64),(0x48,64),(0x50,64)):e.write(0x145bc7220+offset,value)
    h=pe.offset(base.HEADER_VA);generic_start=(base.number(b,h+0xd0)+0xe1492096)&0xffffffff;generic_bytes=(base.number(b,h+0x44)+0xe42e197f)&0xffffffff;generic_count=generic_bytes//8
    instance_base=base.number(b,pe.offset(METADATA_REGISTRATION)+0x70,'Q');generic_arena=0x680000000;arena_bytes=(generic_count+16)*32;e.uc.mem_map(generic_arena,(arena_bytes+4095)&~4095)
    registered=bytearray(generic_count*32)
    for index,(inst,definition_index) in enumerate(struct.iter_unpack('<II',s[generic_start:generic_start+generic_bytes])):struct.pack_into('<I4xQQQ',registered,index*32,definition_index,instance_base+inst*16,0,0)
    e.uc.mem_write(generic_arena,bytes(registered));generic_pool=e.alloc(32);e.write(generic_pool+8,generic_arena);e.write(0x145bc8ad8,generic_pool);generic_allocated=[]
    def generic_class_allocate():
     if e.reg('rcx')!=generic_pool or e.reg('rdx')!=32 or e.read(e.reg('rsp'))!=0x1404ee1a4:raise ValueError('Outside declared GenericClass32 allocation')
     pointer=generic_arena+(generic_count+len(generic_allocated))*32;e.uc.mem_write(pointer,b'\xcc'*32);generic_allocated.append(pointer);return pointer
    e.models[0x1405091d0]=generic_class_allocate


    class_lock=e.alloc(40);e.write(0x145bc95a8,class_lock)
    class_arena=q('cold.registry.base')+0xf0000000;e.uc.mem_map(class_arena,0x10000);class_allocations={}
    class_bytes=((struct.unpack_from('<H',data['cold.class1239.header'],0xbe)[0]*16+0xd7)&~15)
    old_pool_allocate=e.models[0x1405091d0]
    def class_or_generic_allocate():
     if e.reg('rcx')==q('cold.registry.cell'):
      if e.read(e.reg('rsp'))!=0x140517a1d or e.reg('rdx')!=class_bytes:raise ValueError('Outside natural-definition-sized class allocation')
      pointer=class_arena+len(class_allocations)*0x1000;e.uc.mem_write(pointer,b'\xcc'*class_bytes);class_allocations[pointer]=class_bytes;return pointer
     return old_pool_allocate()
    def class_zero():
     pointer=e.reg('rcx')
     if pointer not in class_allocations or e.reg('edx')!=0 or e.reg('r8')!=class_allocations[pointer] or e.read(e.reg('rsp'))!=0x140517a2d:raise ValueError('Outside exact class zeroing source')
     e.uc.mem_write(pointer,bytes(class_allocations[pointer]));return pointer
    e.models[0x1405091d0]=class_or_generic_allocate;e.models[0x141fdf9c0]=class_zero
    source_table=0x690000000;e.uc.mem_map(source_table,(generic_bytes+4095)&~4095);e.uc.mem_write(source_table,s[generic_start:generic_start+generic_bytes]);e.write(frame+0x2b8,source_table);e.allowed.append((0x14053f44a,0x14053f47a))

    def guard(uc,access,address,size,value,userdata):
     if (address&~4095) in pages and not any(a<=address and address+size<=z for a,z,label in known):
      violations.append(dict(address=hex(address),size=size,site=hex(e.reg('rip'))));e.failure='Uncaptured natural-address memory read';e.uc.emu_stop()
    hook=e.uc.hook_add(e.U.UC_HOOK_MEM_READ,guard)
    results=[]
    def invoke(name,stops,entry=0x159b6eb50,regs=None):
     e.calls=[];e.failure=None;e.steps=0;e.uc.context_restore(e.context);e.write(STACK+0x100008,RETURN)
     for reg,v in dict(dict(rsp=STACK+0x100008,rcx=definition,rdx=argument),**(regs or {})).items():e.uc.reg_write(getattr(e.R,'UC_X86_REG_'+reg.upper()),v)
     counts=Counter();path=hashlib.sha256();hit=[];start=len(order);vstart=len(violations)
     stops={RETURN,0x1404c2be0,0x1404c2650,0x1404c2c70,*stops}
     e.allowed.extend((a,a+1) for a in stops)
     def observe(a):
      if a in e.unwind_ranges and hex(a) not in functions:
       z=e.unwind_ranges[a];raw=b[e.pe.offset(a):e.pe.offset(a)+z-a];functions[hex(a)]=dict(end=hex(z),sha256=hashlib.sha256(raw).hexdigest(),instructions=[dict(address=hex(x.address),instruction=x.mnemonic+' '+x.op_str) for x in md.disasm(raw,a)]);e.allowed.append((a,z))
      if a in e.unwind_ranges or a in e.models:order.append(dict(entry=hex(a),rcx=hex(e.reg('rcx')),rdx=hex(e.reg('rdx')),r8=hex(e.reg('r8'))))
      counts[a]+=1;path.update(struct.pack('<Q',a))
      if a in stops:hit.append(a);e.uc.emu_stop()
     e.before_instruction=observe
     error=None
     try:e.uc.emu_start(entry,END,timeout=20000000,count=20000000)
     except Exception as exc:error=str(exc)
     finally:e.before_instruction=None
     results.append(dict(name=name,stop=hex(hit[0]) if hit else None,failure=e.failure,error=error,regs={n:hex(e.reg(n)) for n in ('rip','rcx','rdx','r8','rax','rdi','rsi')},trace=dict(executedAddresses={hex(k):v for k,v in sorted(counts.items())},pathSHA256=path.hexdigest(),models=e.calls,callOrder=order[start:]),uncapturedReads=violations[vstart:],arrayElement=hex(e.read(allocations[-1]+0x20)) if allocations else None,tailCanary=hex(e.read(allocations[-1]+0x28)) if allocations else None))
    try:
     for selected_index in (0,2072,405417,generic_count-1):
      check=e.alloc(32);e.uc.mem_write(check,b'\xcc'*32)
      invoke('registered-GenericClass-producer-'+str(selected_index),{0x14053f47a},entry=0x14053f44a,regs=dict(rax=check,rsi=selected_index,rbp=frame,xmm6=0))
      assert results[-1]['stop']=='0x14053f47a' and bytes(e.uc.mem_read(check,32))==bytes(registered[selected_index*32:(selected_index+1)*32])
      results[-1]['producer'] = dict(index=selected_index, inputPair=s[generic_start+selected_index*8:generic_start+(selected_index+1)*8].hex(), outputBytes=bytes(e.uc.mem_read(check,32)).hex(), registrationInstanceBase=hex(instance_base))
     invoke('registered-generic-instance5917-native-insert',{0x14053f800},entry=0x14053f827,regs=dict(rax=0x1428a53c8,rbx=5917,rbp=frame))
     assert results[-1]['stop']=='0x14053f800' and e.read(e.read(block))==0x142377940
     invoke('actual-ready-factory-through-registered-intern',{0x1404e0d18})
     assert results[-1]['stop']=='0x1404e0d18' and e.reg('rax')==0x142377940
     actual_table=reads['cold.class1239.metadata']['address']-1239*70
     encoded_table=(base.number(b,pe.offset(base.HEADER_VA)+0x64)+0xa7932555)&0xffffffff
     e.write(0x145bc8ef8,base.HEADER_VA);e.write(0x145bc8f00,actual_table-encoded_table)
     invoke('actual-ready-classes-generic-factory-continuation',set())
     parent_descriptor=q('cold.registry.base')+struct.unpack_from('<I',data['cold.class1239.header'],0xb0)[0]+0x10
     assert results[-1]['failure']=='Uncaptured natural-address memory read' and results[-1]['error'] is None
     assert results[-1]['uncapturedReads']==[dict(address=hex(parent_descriptor+8),size=4,site='0x1404ed9c3')]
     assert results[-1]['regs']['rip']=='0x1404ed9c3'
     assert len(generic_allocated)==len(class_allocations)==1 and class_bytes==400
     assert e.read(generic_allocated[0]+0x18)==0 and len(critical_stack)==2
     assert all(row['tailCanary'] in (None,'0xfedcba9876543210') for row in results)
     report=dict(sampleSHA256=RAW_SHA,sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),samplerSHA256=sample['sourceSHA256'],inputSHA256=FIVE_INPUTS,classes=states,native=results,functions=functions,argument=hex(argument),definition=hex(definition),models=['Captured spans mapped at observed addresses; exact slot21/82 code pointers rebased to the fixed PE. Uncaptured reads in mapped natural pages are rejected.', 'New typed RuntimeType wrapper/Type[] storage, exact 8B C++ vector and32B packed storage allocation/free, source-sized400B class allocation and memset are explicit models.', 'One original startup iteration inserts fixed registered argument instance5917 into a controlled64-bucket cache. GenericClass cache starts empty, and original native creation/insertion execute.', '411433 registered GenericClass records follow fixed startup data and original producer stores, independently checked by four native producer fragments; these are not natural cache observations.', 'Fixed metadata header pointer and metadata body base inferred consistently from all13 actual metadata addresses are declared bindings.', 'Single-thread SRW lock ownership and reentrant CriticalSection behavior are explicit imported-OS models. At the missing-read stop, unwinding/release is not claimed.'],registeredInternProducer=dict(index=5917,pointer='0x142377940',source='fixed registration+70 with one exact original startup loop iteration; controlled empty 64-bucket cache, not claimed natural cache'),criticalEvents=critical_events,sharedEvents=shared_events,genericClassModel=dict(registeredRows=generic_count,sourceBytesSHA256=hashlib.sha256(registered).hexdigest(),sourceProducer="14053f44a..14053f47a; modeled full registered records, four independent original native producer comparisons passed",dynamicRecords=[dict(pointer=hex(x),bytes=bytes(e.uc.mem_read(x,32)).hex()) for x in generic_allocated]),newTypeClasses=[dict(pointer=hex(x),size=n,header=bytes(e.uc.mem_read(x,0xd0)).hex()) for x,n in class_allocations.items()],expectedUncapturedParentDescriptor=hex(q('cold.registry.base')+struct.unpack_from('<I',data['cold.class1239.header'],0xb0)[0]+0x10),completeColdComparer=False,completeAvatarHandler=False,targetProtocolReady=False)
     report['sourceWindows']={}
     for name,start,end in (('registeredClassProducer',0x14053f44a,0x14053f47a),('registeredInstanceLoop',0x14053f737,0x14053fb4d),('dynamicClassProducer',0x1404ee193,0x1404ee1bf)):
      raw=b[pe.offset(start):pe.offset(start)+end-start]
      report['sourceWindows'][name]=dict(start=hex(start),end=hex(end),bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),instructions=[dict(address=hex(x.address),instruction=x.mnemonic+' '+x.op_str) for x in md.disasm(raw,start)])
     report['locksAtStop']=dict(criticalDepth=len(critical_stack),sharedHeld=shared_depth is not None,unwindExecuted=False)
     out.write_text(json.dumps(report,indent=2)+'\n','utf8');return report
    finally:e.close()



def run_consumers(b, m, s, r, cells, libraries, out):
    codec=card.natural_codec(Path('local/migration72/d02-active/plain/initialization-resources-elevated5.json'))
    rt=card.NaturalRuntime(b,m,s,r,cells,libraries,'Avatar',codec=codec);e=rt.e
    tables=[];targets={}
    def install(name):
     rt.name=name;rt.capacity=card.TABLES[name][3];rt.reader=0;rt.spans=[];rt.pending_rows={}
     rt.row_entry=next(int(x['registeredAddress'],16) for x in r[card.TABLES[name][1]]['methods'] if x['name']=='CNLIBMNOALB')
     rt.bind_function(card.TABLES[name][0]);refs={int(x['target'],16) for x in rt.functions[hex(card.TABLES[name][0])]['references'] if int(x['target'],16) not in cells}
     uses=card.method_usages(b,m,s,r,refs);rt.loader_usages=uses
     klass=next(ptr for ptr,typ in rt.type_records.items() if typ.get('typeDefinition')==1222 and typ['arguments'][1].get('typeDefinition')==card.TABLES[name][1]);rt.dictionary_class=klass
     ctx=e.alloc(256);e.write(klass+0x30,ctx);ctor=e.alloc(64);e.write(ctor,klass);e.write(ctor+8,e.model_stub(rt.dictionary_constructor));e.write(ctx+0x10,ctor)
     insert=e.alloc(64);e.write(insert,klass);e.write(insert+8,0x1578b6a20);e.write(ctx+0x88,insert)
     for use in uses:
      assert use['ownerType']==1222 and use['classArguments'][1].get('typeDefinition')==card.TABLES[name][1]
      mi=e.alloc(64);e.write(mi,klass);e.write(int(use['cell'],16),mi);rt.method_cells[int(use['cell'],16)]=use
    try:
     for name in ('Avatar','AvatarSkillDepot','AvatarSkill'):
      if name!='Avatar':install(name)
      e.allowed=list(dict.fromkeys(e.allowed))
      blob=card.base.read_verified(Path('local/migration72/d02-active/resource-locator-astra/locator-final-1')/(name+'.native-m_bytes.bin'),card.TABLES[name][4])
      result=rt.parse_table(blob);tables.append(dict(name=name,sha256=result['payloadSHA256'],cursor=result['cursor'],count=result['count'],trace=result['trace']))
      for row in result['rows']:
       if row['key'] in {'Avatar':(10000005,10000007),'AvatarSkillDepot':(501,701),'AvatarSkill':(100540,100550)}[name]:targets[int(row['pointer'],16)]=(name,row['key'],int(r[card.TABLES[name][1]]['fieldLayout']['rawSizeAndFlags'][0],16))
     reads=[]
     def observed(uc,access,address,size,value,data):
      for pointer,(name,key,width) in targets.items():
       if pointer<=address< pointer+width:reads.append(dict(table=name,key=key,offset=hex(address-pointer),width=size,site=hex(e.reg('rip'))))
     hook=e.uc.hook_add(e.U.UC_HOOK_MEM_READ,observed)
     consumers=[]
     for key in (10000005,10000007,0xffffffff):
      start=len(reads);trace=rt.call(0x153217960,rcx=key);consumers.append(dict(avatarId=key,returned=e.reg('eax'),trace=trace,reads=reads[start:]));assert trace['stop']==hex(card.RETURN) and e.reg('eax')==0
     e.uc.hook_del(hook)
     report=dict(sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),runtimeSHA256=hashlib.sha256(Path(card.__file__).read_bytes()).hexdigest(),tables=tables,consumers=consumers,heapUsed=e.heap_cursor-0x612000000,nativeSources=rt.functions,callOrder=rt.call_order,models='same frozen typed class/storage/allocator contexts, complete three actual whole loaders in one emulator; no resource findentry model',completeAvatarHandler=False)
     out.write_text(json.dumps(report,indent=2)+'\n','utf8');return report
    except Exception:
     out.with_suffix('.failure.json').write_text(json.dumps(dict(tables=tables,failure=e.failure,regs={n:hex(e.reg(n)) for n in ('rip','rcx','rdx','rax')},heapUsed=e.heap_cursor-0x612000000,calls=rt.call_order[-30:],nativeSources=rt.functions),indent=2)+'\n','utf8');raise
    finally:e.close()



if __name__ == '__main__':
    main()
