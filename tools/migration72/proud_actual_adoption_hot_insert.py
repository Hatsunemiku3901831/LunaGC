"""Bounded offline execution of the frozen Proud Store -> TryInsert hot cases."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'local/migration72/d02-resume-20261111b'
CONTRACT = BASE / 'proud/hot-insert-case-contract.private.json'
OUT = BASE / 'proud-hot-insert'
EMU = ROOT / 'local/migration72/python/unicorn'
STORE, INSERT, SENTINEL = 0x154d40010, 0x1593e7bf0, 0x6d0200100
# Fixed observed hot sequence; stop before any alternative branch.
HOT_PATH = [int(pc, 16) for pc in '''154d40010 154d40012 154d40013 154d40014 154d40015 154d40016 154d4001a 154d4001d 154d40020 154d40024 154d40027 154d4009f 154d400a2 154d400a5 154d400a8 154d400ab 1593e7bf0 1593e7bf2 1593e7bf4 1593e7bf6 1593e7bf8 1593e7bf9 1593e7bfa 1593e7bfb 1593e7bfc 1593e7c03 1593e7c09 1593e7c0c 1593e7c11 1593e7c16 1593e7c19 1593e7c1c 1593e7c1f 1593e7c24 1593e7c26 1593e7c2a 1593e7d1f 1593e7d23 1593e7d26 1593e7d2b 1593e7d30 1593e7d8e 1593e7d93 1593e7d96 1593e7d9a 1593e7da1 1593e7da4 1593e7de4 1593e7de8 1593e7deb 1593e7df1 1593e7df8 1593e7dfb 1593e7dfe 1593e7e00 1593e7e02 1593e7e04 1593e7e0a 1593e7e0c 1593e7e10 1593e7e12 1593e7e15 1593e7e1a 1593e7e1f 1593e7e24 1593e7f1e 1593e7f21 1593e7f27 1593e7f2b 1593e7f2d 1593e8007 1593e800c 1593e8011 1593e8015 1593e8019 1593e801e 1593e8021 1593e8023 1593e8051 1593e8054 1593e8056 1593e80b9 1593e80bc 1593e80bf 1593e80c3 1593e80c6 1593e80cc 1593e80cf 1593e80d5 1593e80d8 1593e80dc 1593e80e0 1593e80e4 1593e80e7 1593e80e9 1593e80eb 1593e80ee 1593e80f3 1593e80f7 1593e80fb 1593e80fe 1593e8100 1593e8102 1593e8104 1593e8109 1593e8110 1593e8111 1593e8112 1593e8113 1593e8114 1593e8116 1593e8118 1593e811a 1593e811c 154d400b0 154d400b1 154d400b5 154d400b6 154d400b7 154d400b8 154d400b9 154d400bb'''.split()]
PINS = {
 'proud/hot-insert-implementation-spec.zh.md': '54f7a936068f97faf33de484b216401c752087d9607a70d2867a50066ce7dcf6',
 'proud/hot-insert-case-contract.private.json': '016a317ab409a0f755d665a8e2b539be5d54fd622185225557858a1755605018',
 'proud/sources1.private.json': '46ef1a0d573e9293f326ab76e9e35305827a4e290ae8376f542f0cb0e9f6438d',
 'proud/sources2.private.json': '46ef1a0d573e9293f326ab76e9e35305827a4e290ae8376f542f0cb0e9f6438d',
 'proud/sources-main-third.private.json': '46ef1a0d573e9293f326ab76e9e35305827a4e290ae8376f542f0cb0e9f6438d',
}
STORE_PINS = {'boundary.private.json':'2ca6496e0138226ce5a797b75489a86578e1aad1061d800c00db67cf86a891b5','code.asm.private.json':'eaa2b2b0848d71148e1690a229029f393e75174651fd7850e04d556713770585','code.bin':'dee346d2bb53b96567d9de97db6ffca4c7cc6354d8f3e3033fd64a4aedfe2389','extraction.private.json':'caf4852a2f6e927770d391d74ea5f7c01800bc1865de865f2e9ae5e20c9e2fa1','pdata.bin':'abd5498b73ce2fd1d93f759efbcd49c6fa57a6f741c6de52ae0154d279b18b82'}
INSERT_PINS = {'boundary.private.json':'1f497608bc26081231c5b64e08a9becacb67690967ff0f1a553f672f552da88a','code.asm.private.json':'759f3fa468151c1fc372c0587c69aa108df395ce713b89d12c99b4abbd3ff680','code.bin':'d7572088110eaf89adc9dd34d52a5db9d20e5f904f394592f09ecfeeb7b1985c','extraction.private.json':'0e47289bc274312fbdb0df85da829565582f75b944e1ad75e6940056c4b26544','pdata.bin':'5bdda9a523f3d9ec60d90f4f1ecfb99d922137a732bcaeb3acdba494a019bd2c','pre-body.private.json':'5b3815bc51a1899f431afeb6b9e32744a4601dcd7b37fe1d2dff99c9734dd878','slot.bin':'23151705077edc725c5ec56bcae682535e91ac9bea637b02ef5d7f84f3fe1831'}
for folder in ('proud/store2','proud/store3'):
 PINS.update({f'{folder}/{k}':v for k,v in STORE_PINS.items()})
for folder in ('proud-insert-window/fresh1','proud-insert-window/fresh2','proud-insert-window/main-third'):
 PINS.update({f'{folder}/{k}':v for k,v in INSERT_PINS.items()})

def sha(path):
 h = hashlib.sha256()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1048576),b''): h.update(chunk)
 return h.hexdigest()

def verify_inputs():
 for name, digest in PINS.items():
  if sha(BASE / name) != digest: raise ValueError('source drift: '+name)
 reference = ROOT/'local/migration72/d02-resume-20261110b/c-proud-single-key-reference/main-third-v3.private.json'
 if sha(reference) != '30b99d804468f9572a54f60b1e3dfc0838d38e55c52e8ae11e285f7fcd1d9a2c': raise ValueError('reference drift')
 h=hashlib.sha256()
 for f in sorted([*EMU.rglob('*.py'),EMU/'lib/unicorn.dll']):
  h.update(f.relative_to(EMU).as_posix().encode()+b'\0'+bytes.fromhex(sha(f)))
 if h.hexdigest() != '397eab2509ff5d750a66671a976c75507eabb813276f2ad3c88bcad8df7eda04': raise ValueError('offline CPU dependency drift')
 game=os.environ.get('GAME_PATH')
 if not game:
  for line in (ROOT/'.env').read_text(encoding='utf-8-sig').splitlines():
   if line.startswith('GAME_PATH='): game=os.path.expandvars(line.partition('=')[2].strip().strip('"\''))
 if not game: raise ValueError('GAME_PATH missing')
 source=json.loads((BASE/'proud/sources1.private.json').read_text())
 for name,digest in source['inputSHA256'].items():
  if sha(Path(game)/name) != digest: raise ValueError('client input drift: '+name)
 contract=json.loads(CONTRACT.read_text())
 code=[]; instructions={}
 for folder,base in (('proud/store2',STORE),('proud-insert-window/fresh1',INSERT)):
  body=(BASE/folder/'code.bin').read_bytes()
  asm=json.loads((BASE/folder/'code.asm.private.json').read_text())
  joined=b''.join(bytes.fromhex(i['hex']) for i in asm)
  if body != joined or any(int(i['address'],16) != base+sum(len(bytes.fromhex(j['hex'])) for j in asm[:n]) for n,i in enumerate(asm)):
   raise ValueError('instruction coverage mismatch: '+folder)
  instructions.update({int(i['address'],16):i for i in asm}); code.append(body)
 if code[0][0x9b:0xa0] != bytes.fromhex('e8407b6a04'): raise ValueError('original call mismatch')
 return contract,code,instructions

def execute(case, code, instructions):
 import unicorn as u
 from unicorn import x86_const as x
 machine=u.Uc(u.UC_ARCH_X86,u.UC_MODE_64)
 for start,size in ((STORE&~0xfff,0x1000),(INSERT&~0xfff,0x2000),(0x6d0000000,0x4000),(0x6d0100000,0x20000),(SENTINEL&~0xfff,0x1000)):
  machine.mem_map(start,size)
 machine.mem_write(STORE,code[0]); machine.mem_write(INSERT,code[1])
 def put(addr,fmt,value): machine.mem_write(addr,struct.pack(fmt,value))
 w,d,b,e=0x6d0000000,0x6d0001000,0x6d0002000,0x6d0003000
 put(w+0x18,'<Q',d); put(d+0x10,'<Q',e); put(d+0x18,'<Q',b); put(d+0x40,'<I',0xffffffff)
 put(b+0x18,'<Q',3); put(e+0x18,'<Q',3)
 initial=case['initialRegisters']; put(int(initial['rsp'],16),'<Q',SENTINEL)
 regs={n:getattr(x,'UC_X86_REG_'+n.upper()) for n in initial if n != 'rflags'}
 regs['rflags']=x.UC_X86_REG_EFLAGS
 for n,v in initial.items(): machine.reg_write(regs[n],int(v,16))
 for i in range(16): machine.reg_write(getattr(x,f'UC_X86_REG_XMM{i}'),0)
 trace=[]; accesses=[]; stop=[]; call=[]; previous=[]
 bucket=b+0x20+case['expected']['bucketIndex']*4
 allowed_reads=[(w,w+48),(d,d+80),(b,b+44),(e,e+92),(0x6d0100000,0x6d0120000)]
 allowed_writes=[(0x6d0100000,0x6d0120000),(d+0x38,d+0x3c),(d+0x44,d+0x48),(bucket,bucket+4),(e+0x20,e+0x24),(e+0x24,e+0x28),(e+0x28,e+0x30),(e+0x30,e+0x34)]
 def inside(addr,size,ranges): return any(lo<=addr and addr+size<=hi for lo,hi in ranges)
 def fail(reason):
  if not stop: stop.append(reason)
  machine.emu_stop()
 def on_code(uc,addr,size,_):
  if addr==SENTINEL:
   if not previous or previous[-1] != 0x154d400bb: fail('sentinel was not reached by original Store RET')
   else: stop.append('original-return'); uc.emu_stop()
   return
  item=instructions.get(addr)
  if item is None or len(bytes.fromhex(item['hex'])) != size: fail(f'undeclared instruction {addr:#x}'); return
  if len(trace)>=1000: fail('instruction budget'); return
  if len(trace)>=len(HOT_PATH) or addr != HOT_PATH[len(trace)]: fail(f'hot path departure {addr:#x}'); return
  if item['mnemonic'].lower().startswith('call'):
   if addr != 0x154d400ab or item['hex'] != 'e8407b6a04' or call: fail(f'undeclared call {addr:#x}'); return
   call.append(hex(addr))
  trace.append(hex(addr)); previous.append(addr)
 def on_mem(uc,access,addr,size,value,_):
  kind='write' if access==u.UC_MEM_WRITE else 'read'
  accesses.append({'pc':hex(uc.reg_read(x.UC_X86_REG_RIP)),'kind':kind,'address':hex(addr),'size':size,'value':hex(value) if kind=='write' else None})
  if not inside(addr,size,allowed_writes if kind=='write' else allowed_reads): fail(f'undeclared {kind} {addr:#x} size {size}')
 def on_invalid(uc,access,addr,size,value,_):
  accesses.append({'pc':hex(uc.reg_read(x.UC_X86_REG_RIP)),'kind':'invalid','access':access,'address':hex(addr),'size':size})
  fail(f'unmapped access {addr:#x} size {size}'); return False
 machine.hook_add(u.UC_HOOK_CODE,on_code)
 machine.hook_add(u.UC_HOOK_MEM_READ|u.UC_HOOK_MEM_WRITE,on_mem)
 machine.hook_add(u.UC_HOOK_MEM_INVALID,on_invalid)
 try: machine.emu_start(STORE,SENTINEL+1,count=1002)
 except u.UcError as ex:
  if not stop: stop.append('Unicorn error: '+str(ex))
 if not stop: stop.append('instruction budget')
 registers={n:hex(machine.reg_read(r)) for n,r in regs.items()}
 xmm={f'xmm{i}':hex(machine.reg_read(getattr(x,f'UC_X86_REG_XMM{i}'))) for i in range(16)}
 data={'entry60BHex':bytes(machine.mem_read(e+0x20,60)).hex(),'buckets12BHex':bytes(machine.mem_read(b+0x20,12)).hex(),
       'dictionaryDWORDs':{f'+{o:x}':int.from_bytes(machine.mem_read(d+o,4),'little') for o in (0x38,0x3c,0x40,0x44)}}
 expected=case['expected']; checks={
  'originalReturn':stop==['original-return'], 'bothBodies':hex(STORE) in trace and hex(INSERT) in trace,
  'singleOriginalCall':call==['0x154d400ab'], 'entry0':data['entry60BHex'][:40]==expected['entry0_20BHex'],
  'remainingEntries':data['entry60BHex'][40:]==expected['entry1and2_40BHex'], 'buckets':data['buckets12BHex']==expected['buckets12BHex'],
  'dictionary':data['dictionaryDWORDs']==expected['dictionaryDWORDs'], 'returnAL':int(registers['rax'],16)&255==expected['returnAL'],
  'nonvolatile':all(registers[n]==initial[n] for n in ('rbx','rbp','rdi','rsi','r12','r13','r14','r15')),
  'stackReturn':int(registers['rsp'],16)==int(initial['rsp'],16)+8,
  'xmmNonvolatile':all(machine.reg_read(getattr(x,f'UC_X86_REG_XMM{i}'))==0 for i in range(6,16)),
  'noModels':True}
 return {'name':case['name'],'stop':stop,'trace':trace,'accesses':accesses,'calls':call,'modelsInvoked':[], 'registers':registers,'xmm':xmm,'data':data,'checks':checks,'passed':all(checks.values())}

def main():
 parser=argparse.ArgumentParser(); parser.add_argument('run',choices=('fresh1','fresh2','fresh3','fresh4','main-third')); args=parser.parse_args()
 OUT.mkdir(parents=True,exist_ok=True); path=OUT/(args.run+'.private.json')
 if path.exists(): raise SystemExit('output already exists: '+str(path))
 report={'task':'E-01d1/R3-PROUD-ACTUAL-ADOPTION/HOT-INSERT','cases':[], 'originalHotPairInsertExecuted':False,'actualProudRowConstructed':False,'actualProudMapProducerExecuted':False,'actualPairLookupHit':False,'naturalInterfaceDispatch':False,'promoteThresholdObserved':False,'javaFormulaProven':False,'resourceReady':False,'completeAvatar':False,'d02Complete':False}
 try:
  contract,code,instructions=verify_inputs()
  report['sourceManifest']={'inputs':PINS,'clientInputs':json.loads((BASE/'proud/sources1.private.json').read_text())['inputSHA256'],'referenceSHA256':'30b99d804468f9572a54f60b1e3dfc0838d38e55c52e8ae11e285f7fcd1d9a2c','offlineCPUCompositeSHA256':'397eab2509ff5d750a66671a976c75507eabb813276f2ad3c88bcad8df7eda04','toolSHA256':sha(Path(__file__))}
  sys.path.insert(0,str(EMU.parent))
  for case in contract['cases']:
   result=execute(case,code,instructions); report['cases'].append(result)
   if not result['passed']: break
  report['originalHotPairInsertExecuted']=len(report['cases'])==3 and all(c['passed'] for c in report['cases'])
 except Exception as ex:
  report['error']=repr(ex)
 path.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
 print(json.dumps({'path':str(path),'sha256':sha(path),'passed':report['originalHotPairInsertExecuted'],'error':report.get('error'),'stops':[c['stop'] for c in report['cases']]}))
 return 0 if report['originalHotPairInsertExecuted'] else 1

if __name__=='__main__': raise SystemExit(main())

