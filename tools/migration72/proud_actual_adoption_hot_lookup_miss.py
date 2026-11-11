"""Execute only the frozen original Proud hot-lookup miss sequence offline."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT/'local/migration72/d02-resume-20261111b'
CONTRACT = BASE/'proud/hot-lookup-miss-case-contract.private.json'
OUT = BASE/'proud-hot-lookup-miss'
CONTRACT_SHA = '4231abaa5331649247f78145d34f8a98ffb12ea2a96cbacbecc251fd2a383aeb'
INSERT_TOOL_SHA = 'db3494dc185ea0b8b65aa1538f2459993f2b8eb6a8d7d4f25a86b0b2b1bed567'
SENTINEL = 0x6d0200100
WRAPPER, DICT, BUCKETS, ENTRIES, OUTPUT = 0x6d0000000,0x6d0001000,0x6d0002000,0x6d0003000,0x6d0004000
STACK, STACK_SIZE = 0x6d0100000,0x20000


def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1048576),b''): h.update(chunk)
 return h.hexdigest()


def inputs():
 if CONTRACT.stat().st_size != 36416 or sha(CONTRACT)!=CONTRACT_SHA: raise ValueError('contract drift')
 c=json.loads(CONTRACT.read_text(encoding='utf-8'))
 for name,pin in c['sources'].items():
  p=ROOT/name
  if p.stat().st_size!=pin['bytes'] or sha(p)!=pin['sha256']: raise ValueError('source drift: '+name)
 previous=ROOT/'tools/migration72/proud_actual_adoption_hot_insert.py'
 if sha(previous)!=INSERT_TOOL_SHA: raise ValueError('accepted insert tool drift')
 sys.path.insert(0,str(previous.parent))
 import proud_actual_adoption_hot_insert as old
 old.verify_inputs()  # Includes client hashes and the pinned offline CPU dependency.
 if c['fixedClientInputs']!=json.loads((BASE/'proud/sources-main-third.private.json').read_text())['inputSHA256']: raise ValueError('client input manifest mismatch')
 sys.path.insert(0,str(old.EMU.parent))
 instructions={}; bodies=[]
 for folder,address in (('proud/store2',0x154d40010),('proud-insert-window/fresh1',0x1593e7bf0),('proud/lookup1',0x154d400d0),('proud/prepare1',0x154d40260),('proud/index2',0x1593e7870)):
  d=BASE/folder; body=(d/'code.bin').read_bytes(); asm=json.loads((d/'code.asm.private.json').read_text())
  pos=address
  for item in asm:
   part=bytes.fromhex(item['hex'])
   if int(item['address'],16)!=pos or body[pos-address:pos-address+len(part)]!=part: raise ValueError('original instruction coverage drift: '+folder)
   instructions[pos]=item; pos+=len(part)
  if pos!=address+len(body): raise ValueError('original body length drift: '+folder)
  bodies.append((address,body))
 for item in c['allowedCalls']:
  pc=int(item['pc'],16); instruction=instructions[pc]
  if instruction['hex']!=item['hex'] or pc+5+int.from_bytes(bytes.fromhex(item['hex'])[1:],'little',signed=True)!=int(item['target'],16): raise ValueError('call mismatch')
 return c,instructions,bodies


def execute(contract,instructions,bodies):
 import unicorn as u
 from unicorn import x86_const as x
 m=u.Uc(u.UC_ARCH_X86,u.UC_MODE_64)
 for addr,size in ((0x154d40000,0x1000),(0x1593e7000,0x2000),(WRAPPER,0x5000),(STACK,STACK_SIZE),(SENTINEL&~0xfff,0x1000)): m.mem_map(addr,size)
 for addr,body in bodies: m.mem_write(addr,body)
 def put(addr,fmt,value): m.mem_write(addr,struct.pack(fmt,value))
 put(WRAPPER+0x18,'<Q',DICT); put(DICT+0x10,'<Q',ENTRIES); put(DICT+0x18,'<Q',BUCKETS); put(DICT+0x40,'<I',0xffffffff)
 put(BUCKETS+0x18,'<Q',3); put(ENTRIES+0x18,'<Q',3)
 reads=[(WRAPPER,WRAPPER+48),(DICT,DICT+80),(BUCKETS,BUCKETS+44),(ENTRIES,ENTRIES+92),(OUTPUT,OUTPUT+12),(STACK,STACK+STACK_SIZE)]
 store_writes=[(DICT+0x38,4),(DICT+0x44,4),(BUCKETS+0x24,4),(ENTRIES+0x20,4),(ENTRIES+0x24,4),(ENTRIES+0x28,8),(ENTRIES+0x30,4)]
 calls={int(a['pc'],16):a['hex'] for a in contract['allowedCalls']}
 trace=[]; accesses=[]; stops=[]; seen_calls=[]; helper_eax=[]; index_pairs=[]; total=0; results=[]
 registers={n:getattr(x,'UC_X86_REG_'+n.upper()) for n in ('rax','rbx','rcx','rdx','rsi','rdi','rbp','rsp','r8','r9','r10','r11','r12','r13','r14','r15','rip')}
 registers['rflags']=x.UC_X86_REG_EFLAGS
 allowed=[]; expected_pcs=[]
 def fail(reason):
  if not stops: stops.append(reason)
  m.emu_stop()
 def on_code(uc,pc,size,_):
  nonlocal total
  if pc==SENTINEL:
   if len(trace)!=len(expected_pcs) or not trace or trace[-1]!=expected_pcs[-1] or instructions[trace[-1]]['mnemonic']!='ret': fail('sentinel not reached through original RET')
   else: stops.append('original-return'); uc.emu_stop()
   return
  if len(trace)>=1000 or total>=4000: fail('instruction budget'); return
  item=instructions.get(pc)
  if item is None or len(bytes.fromhex(item['hex']))!=size: fail(f'undeclared instruction {pc:#x}'); return
  if len(trace)>=len(expected_pcs) or pc!=expected_pcs[len(trace)]: fail(f'PC departure {pc:#x}'); return
  if item['mnemonic'].lower().startswith('call'):
   if pc not in calls or item['hex']!=calls[pc]: fail(f'undeclared call {pc:#x}'); return
   seen_calls.append(hex(pc))
  if pc==0x1593e7870: index_pairs.append(hex(uc.reg_read(x.UC_X86_REG_RDX)))
  if pc==0x154d40174: helper_eax.append(hex(uc.reg_read(x.UC_X86_REG_EAX)))
  trace.append(pc); total+=1
 def on_mem(uc,access,addr,size,value,_):
  kind='write' if access==u.UC_MEM_WRITE else 'read'; pc=uc.reg_read(x.UC_X86_REG_RIP)
  accesses.append({'pc':hex(pc),'kind':kind,'address':hex(addr),'size':size,'value':hex(value) if kind=='write' else None})
  if kind=='read':
   if not any(lo<=addr and addr+size<=hi for lo,hi in reads): fail(f'undeclared read {addr:#x} size {size}')
  elif not (STACK<=addr and addr+size<=STACK+STACK_SIZE) and (addr,size) not in allowed:
   fail(f'undeclared write {addr:#x} size {size}')
 def on_invalid(uc,access,addr,size,value,_):
  accesses.append({'pc':hex(uc.reg_read(x.UC_X86_REG_RIP)),'kind':'invalid','access':access,'address':hex(addr),'size':size})
  fail(f'unmapped access {addr:#x} size {size}'); return False
 m.hook_add(u.UC_HOOK_CODE,on_code); m.hook_add(u.UC_HOOK_MEM_READ|u.UC_HOOK_MEM_WRITE,on_mem); m.hook_add(u.UC_HOOK_MEM_INVALID,on_invalid)
 def snapshot():
  return {name:bytes(m.mem_read(address,size)).hex() for name,address,size in (('wrapper',WRAPPER,48),('dictionary',DICT,80),('buckets',BUCKETS,44),('entries',ENTRIES,92),('out',OUTPUT,12))}
 def invocation(name,initial,pcs,data_writes):
  nonlocal trace,accesses,stops,seen_calls,helper_eax,index_pairs,allowed,expected_pcs
  trace=[]; accesses=[]; stops=[]; seen_calls=[]; helper_eax=[]; index_pairs=[]; allowed=data_writes; expected_pcs=[int(p,16) for p in pcs]
  m.mem_write(STACK,bytes(STACK_SIZE)); put(int(initial['rsp'],16),'<Q',SENTINEL)
  for reg,value in initial.items(): m.reg_write(registers[reg],int(value,16))
  for i in range(16): m.reg_write(getattr(x,f'UC_X86_REG_XMM{i}'),0)
  before=snapshot()
  try: m.emu_start(int(initial['rip'],16),SENTINEL+1,count=1002)
  except u.UcError as ex:
   if not stops: stops.append('Unicorn error: '+repr(ex))
  if not stops: stops.append('instruction budget')
  after=snapshot(); regs={n:hex(m.reg_read(r)) for n,r in registers.items()}; xmms={f'xmm{i}':hex(m.reg_read(getattr(x,f'UC_X86_REG_XMM{i}'))) for i in range(16)}
  r={'name':name,'before':before,'after':after,'trace':[hex(v) for v in trace],'calls':seen_calls,'accesses':accesses,'stops':stops,'indexHelperEAX':helper_eax,'indexEntryPairs':index_pairs,'registers':regs,'xmm':xmms,'modelsInvoked':[]}
  results.append(r); return r
 initial=contract['initialStore']; store_case=initial['case']
 store=invocation('original-store',store_case['initialRegisters'],initial['expectedPCs'],store_writes)
 expected=store_case['expected']; ebytes=bytes.fromhex(store['after']['entries']); bbytes=bytes.fromhex(store['after']['buckets']); dbytes=bytes.fromhex(store['after']['dictionary'])
 store['checks']={'returned':store['stops']==['original-return'],'calls':store['calls']==initial['calls'],'entry':ebytes[0x20:].hex()==expected['entry0_20BHex']+expected['entry1and2_40BHex'],'buckets':bbytes[0x20:].hex()==expected['buckets12BHex'],'dictionary':all(int.from_bytes(dbytes[int(k[1:],16):int(k[1:],16)+4],'little')==v for k,v in expected['dictionaryDWORDs'].items()),'wrapperFlag0':bytes.fromhex(store['after']['wrapper'])[0x28]==0,'returnAL':int(store['registers']['rax'],16)&255==expected['returnAL'],'XMMnonvolatile':all(int(store['xmm'][f'xmm{i}'],16)==0 for i in range(6,16)),'GPandStack':all(store['registers'][n]==store_case['initialRegisters'][n] for n in ('rbx','rbp','rdi','rsi','r12','r13','r14','r15')) and int(store['registers']['rsp'],16)==int(store_case['initialRegisters']['rsp'],16)+8}
 if not all(store['checks'].values()): return results
 original={k:store['after'][k] for k in ('dictionary','buckets','entries')}
 for case in contract['lookupSequence']:
  # Only this out buffer and the private invocation stack/registers are reset.
  m.mem_write(OUTPUT,bytes.fromhex(case['before']['out12BHex']))
  if m.mem_read(WRAPPER+0x28,1)[0]!=case['before']['wrapperByte28']:
   results.append({'name':case['name'],'stops':['wrapper flag before invocation disagrees with contract'],'before':snapshot()}); break
  r=invocation(case['name'],case['initialRegisters'],case['expected']['originalPCs'],[(int(w['address'],16),w['bytes']) for w in case['allowedDataWrites']])
  exp=case['expected']; data_writes=[(int(a['address'],16),a['size'],int(a['value'],16)&((1<<(a['size']*8))-1)) for a in r['accesses'] if a['kind']=='write' and int(a['address'],16)<STACK]
  expected_writes=[(int(a['address'],16),a['bytes'],a['value']) for a in case['allowedDataWrites']]
  r['checks']={'returned':r['stops']==['original-return'],'calls':r['calls']==exp['calls'],'indexEAX':r['indexHelperEAX']==[exp['originalIndexHelperEAX']], 'AL':int(r['registers']['rax'],16)&255==exp['AL'],'EAX':int(r['registers']['rax'],16)&0xffffffff==int(exp['EAXLow32ForTheseValidPaths'],16),'queryPair':r['indexEntryPairs']==[case['queryPairU64']], 'beforeOut':r['before']['out']==case['before']['out12BHex'],'out':r['after']['out']==exp['out12BHex'],'flag':bytes.fromhex(r['after']['wrapper'])[0x28]==exp['wrapperByte28'], 'dataWrites':sorted(data_writes)==sorted(expected_writes),'mapPreserved':all(r['after'][k]==original[k] for k in original),'wrapperPreservedExceptFlag':r['after']['wrapper'][:80]==store['after']['wrapper'][:80] and r['after']['wrapper'][82:]==store['after']['wrapper'][82:],'GPandStack':all(r['registers'][n]==case['initialRegisters'][n] for n in ('rbx','rbp','rdi','rsi','r12','r13','r14','r15')) and int(r['registers']['rsp'],16)==int(case['initialRegisters']['rsp'],16)+8,'XMMnonvolatile':all(int(r['xmm'][f'xmm{i}'],16)==0 for i in range(6,16)), 'count':len(r['trace'])==exp['instructionCount']}
  if not all(r['checks'].values()): break
 return results


def main():
 parser=argparse.ArgumentParser(); parser.add_argument('run',choices=('fresh1','fresh2','fresh3','fresh4','fresh5','fresh6','main-third')); args=parser.parse_args()
 OUT.mkdir(parents=True,exist_ok=True); path=OUT/(args.run+'.private.json')
 if path.exists(): raise SystemExit('output already exists: '+str(path))
 report={'task':'R3-PROUD-HOT-LOOKUP-MISS','contractSHA256':CONTRACT_SHA,'toolSHA256':sha(Path(__file__)),'results':[],'originalConditionalHotLookupMissExecuted':False,'actualPairMapHit':False,'actualProudMapProducerExecuted':False,'actualProudRowConstructed':False,'naturalInterfaceDispatch':False,'promoteThresholdObserved':False,'resourceReady':False,'completeAvatar':False,'d02Complete':False}
 try:
  contract,instructions,bodies=inputs(); report['sourceCount']=len(contract['sources']); report['results']=execute(contract,instructions,bodies)
  report['originalConditionalHotLookupMissExecuted']=len(report['results'])==4 and all(all(r.get('checks',{}).values()) and r.get('checks') for r in report['results'])
 except Exception as ex: report['error']=repr(ex)
 path.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(json.dumps({'sha256':sha(path),'passed':report['originalConditionalHotLookupMissExecuted'],'error':report.get('error'),'stops':[r['stops'] for r in report['results']]}))
 return 0 if report['originalConditionalHotLookupMissExecuted'] else 1

if __name__=='__main__': raise SystemExit(main())
