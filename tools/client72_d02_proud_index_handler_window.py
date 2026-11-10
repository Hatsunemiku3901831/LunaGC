"""Single approved 157ffc780 window, static mechanical extraction only."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; PRIVATE=ROOT/'local/migration72/d02-resume-20261110b/c-proud-index-handler-window'
sys.path.insert(0,str(ROOT))
ENTRY=0x157ffc780; SLOT=0x142515ed0
FILES=('boundary.private.json','caller.bin','code.asm.private.json','code.bin','extraction.private.json','pdata.bin','pre-body.private.json','slot.bin')
PINS={
'local/migration72/d02-resume-20261110b/c-proud-lazy-index-contract/next-input-spec.zh.md':'ef4109fa7efc3ffa2a284302ed55d494423f33fa3ed017fcf6c524603ee1f62a',
'local/migration72/d02-resume-20261110b/c-proud-lookup-window/final3/code.bin':'479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e',
'local/migration72/d02-resume-20261110b/c-proud-lookup-window/final4/code.bin':'479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e',
'local/migration72/d02-resume-20261110b/c-proud-lookup-window/main-third.private.json':'9e48e1dfb3bc27073e04934572dc21dc57b124ba7d7bed74cbf00701c6376f6c',
'local/migration72/d02-resume-20261110b/c-proud-prepare-aot/final3/report.private.json':'69ab19ff424019a5d38d02a42ae6573a4731788519b136a38a74378cd19f02b6',
'local/migration72/d02-resume-20261110b/c-proud-prepare-aot/final4/report.private.json':'69ab19ff424019a5d38d02a42ae6573a4731788519b136a38a74378cd19f02b6',
'local/migration72/d02-resume-20261110b/c-proud-prepare-aot/main-third.private.json':'0bff021d1171cde4c1248370da22718887765e472c788ecfba619aa41e5ff5e3',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/final3.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/final4.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/main-third.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl':'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
'local/migration72/evidence/client72-signatures.json':'28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
'local/migration72/d02-resume-20261110b/c-proud-lazy-index-contract/frozen-inputs.private.json':'bc1b0342094786ed7cb79e3f5af71261fb3c248a6f97c41e6f79c2bae3269b1a',
'local/migration72/d02-resume-20261110b/c-proud-actual-fields/collect_closed_aot.private.py':'a43824fab5674d69d85a04bdb2a348cb0634ba8f32a78cf49a022f2e916144aa',
'local/migration72/d02-resume-20261110b/c-proud-row-source/typed-producer1/code.bin':'c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d',
'local/migration72/d02-resume-20261110b/c-proud-factory-index-contract/next-lookup-window-request.zh.md':'cccd0d64ebf7b8cfc748193bff61237d223ae0f1eb27a4356a4e1c122d0d02b4',
'tools/client72_d02_proud_prepare_aot.py':'4b5049aef4f869c9c843249a3bf168e5f2c9ae4ff104004b55d96ecb3cf14984',
'tools/client72_d02_proud_index_aot.py':'43733eda2878637cf5506eb6aa9ce785a00c30fdbc0a28cfc9a30fd94043dc98',
'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
'tools/game_path.ps1':'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
'protocol/cn-7.2/profile.json':'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
'local/migration72/python/capstone/__init__.py':'94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
'local/migration72/python/capstone/lib/capstone.dll':'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'}
PINS['docs/CLIENT_72_D02_PROUD_PREPARE_CONTRACT_20261111.md']='764cb3a9aab1457ab774bb71e80c0e6c44ad872b1baa58cc2700aa10c4f6d579'
PINS['tools/client72_d02_proud_prepare_window.py']='ea83d1195754ea2dc785252f55945b318f416735c84fa985f44f0412ed60e243'
PINS['tools/test_client72_d02_proud_prepare_window.py']='f2dc48166820ce00f47a7b1de562e7a4844866a92064312fb56a9f8932521018'
PINS['local/migration72/d02-resume-20261110b/c-proud-prepare-window/final1/code.bin']='0c1d792245409c0df4df69beed8bd9b0a520f8a4d56d14e2fa1dd43ed2b4db7b'
PINS['local/migration72/d02-resume-20261110b/c-proud-prepare-window/final2/code.bin']='0c1d792245409c0df4df69beed8bd9b0a520f8a4d56d14e2fa1dd43ed2b4db7b'
PINS['local/migration72/d02-resume-20261110b/c-proud-prepare-window/main-third.private.json']='c322aa7f0d5d397c21e35edf86422cab2794a401d0784fd68045e6e7884d81cf'
PINS['local/migration72/d02-resume-20261110b/c-proud-primary-index-locator/main-third/payload.bin']='f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05'
FIVE={'YuanShen.exe':(450927000,'8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'),
'YuanShen_Data/Managed/Metadata/global-metadata.dat':(83644188,'45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a'),
'YuanShen_Data/Managed/Metadata/startup-metadata.dat':(4116148,'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105'),
'YuanShen_Data/Plugins/AccountPlatNative.dll':(9360280,'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'),
'YuanShen_Data/Plugins/Astrolabe.dll':(2614168,'87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442')}

def digest(b): return hashlib.sha256(b).hexdigest()
def require(v,msg):
 if not v: raise ValueError(msg)
def save(p,obj):
 with p.open('xb') as f: f.write((json.dumps(obj,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf8'))

def source_gate():
 fixed={p:(ROOT/p).read_bytes() for p in PINS}
 for p,b in fixed.items(): require(digest(b)==PINS[p],'source drift: '+p)
 prefix='local/migration72/d02-resume-20261110b/'
 caller=fixed[prefix+'c-proud-prepare-window/final1/code.bin']
 require(len(caller)==289 and caller==fixed[prefix+'c-proud-prepare-window/final2/code.bin'] and digest(caller)=='0c1d792245409c0df4df69beed8bd9b0a520f8a4d56d14e2fa1dd43ed2b4db7b','caller drift')
 for pc,raw in ((0x157ffc6ab,'e8d0000000'),(0x157ffc710,'e86b000000')):
  b=caller[pc-0x157ffc650:pc-0x157ffc650+5]
  require(b.hex()==raw and pc+5+int.from_bytes(b[1:],'little',signed=True)==ENTRY,'caller E8 differs')
 third=json.loads(fixed[prefix+'c-proud-prepare-window/main-third.private.json'])
 require(third['passed'] and third['entry']=='0x157ffc650' and third['codeBytes']==289 and third['artifacts']['code.bin']['sha256']==digest(caller),'prepare third differs')
 d=json.loads(fixed[prefix+'c-proud-prepare-aot/main-third.private.json'])
 expected={'classInstance':5071,'method':'CDMPEFBPCLP','methodDefinition':138530,'methodInstance':-1,'methodSpec':175105,'owner':18192,'pointerIndex':84548,'specRecord12':'221d0200cf130000ffffffff','tableIndex':13205,'tableRecord14':'444a010001ac0200ffff83650000'}
 rows=[r for r in d['sharedRows'] if r['pointerIndex']==84548]
 declarations=[r for r in d['selectedWithDeclarations'] if r['pointerIndex']==84548]
 require(d['passed'] and rows==[expected] and len(declarations)==1 and all(declarations[0][k]==v for k,v in expected.items() if k!='owner') and declarations[0]['signature']['declaringType']==18192 and declarations[0]['parameterTypes'][0]['typeDefinition']==42005 and declarations[0]['declaration']['parameterCount']==1 and d['pointers']['84548']=={'address':hex(ENTRY),'qword':'80c7ff5701000000','slot':hex(SLOT)},'D identity differs')
 payload=fixed[prefix+'c-proud-primary-index-locator/main-third/payload.bin']
 require(len(payload)==31858 and digest(payload)=='f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05','payload identity differs')
 return {'aotRow':rows[0],'declaration':declarations[0],'callerSHA256':digest(caller),'callerE8':['e8d0000000','e86b000000'],'payloadBytes':len(payload),'payloadSHA256':digest(payload),'crossPointerIndexSameCodeComplete':False}


def extent(table,entry,base,rva):
 require(len(table)%12==0,'unaligned pdata'); exact=[]; containing=[]
 for off in range(0,len(table),12):
  begin,end,unwind=struct.unpack_from('<III',table,off)
  row={'begin':hex(base+begin),'endExclusive':hex(base+end),'unwindRVA':hex(unwind),'recordRVA':hex(rva+off),'offset':off,'size':end-begin}
  if begin==entry-base: exact.append(row)
  if begin<=entry-base<end: containing.append(row)
 return {'exact':exact,'containing':containing}
def choose(diag):
 require(len(diag['exact'])==1 and diag['exact']==diag['containing'],'unique exact/containing pdata required')
 row=diag['exact'][0]; require(row['begin']==hex(ENTRY),'wrong entry'); return row
def bound(size): require(0<size<=4096,'extent exceeds 4096B bound')
def bind_slot(slot):
 require(slot.hex()=='80c7ff5701000000' and int.from_bytes(slot,'little')==ENTRY,'AOT pointer mismatch')

def decode(code):
 sys.path.insert(0,str(ROOT/'local/migration72/python'))
 import capstone
 from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
 cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True
 lines=[]; calls=[]; branches=[]; rip=[]; memory=[]; implicit_stack=[]
 for i in cs.disasm(code,ENTRY):
  line={'address':hex(i.address),'hex':bytes(i.bytes).hex(),'mnemonic':i.mnemonic,'operands':i.op_str}; lines.append(line)
  if i.mnemonic in ('push','pop','call','ret'): implicit_stack.append(line)
  if i.group(capstone.CS_GRP_JUMP): branches.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  if i.group(capstone.CS_GRP_CALL): calls.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  for n,o in enumerate(i.operands):
   if o.type==X86_OP_MEM:
    memory.append(dict(line,operandIndex=n,access=o.access))
    if o.mem.base==X86_REG_RIP: rip.append(dict(line,operandIndex=n,target=hex(i.address+i.size+o.mem.disp)))
 require(0<len(lines)<=1024,'instruction limit exceeds 1024')
 cursor=ENTRY
 for line in lines:
  b=bytes.fromhex(line['hex']); require(int(line['address'],16)==cursor and code[cursor-ENTRY:cursor-ENTRY+len(b)]==b,'decode gap or bytes differ'); cursor+=len(b)
 require(cursor==ENTRY+len(code),'decode incomplete')
 return {'lines':lines,'calls':calls,'branches':branches,'ripReferences':rip,'memoryOperands':memory,'implicitStackInstructions':implicit_stack,'capstoneVersion':capstone.__version__}

def collect(dest):
 report={'task':'E-01/R3-PROUD-INDEX-HANDLER-WINDOW','stage':'source-gate','sourceSHA256':digest(Path(__file__).read_bytes()),'sourcePins':PINS,'inputChecks':{},'nativeCPU':0,'liveReads':0,'xdataBytesRead':0,'neighborCalleeBytesRead':0,'newCodeWindows':0}
 try:
  report['identity']=source_gate(); report['stage']='five-input-gate'
  client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip()); binary=None
  for name,(size,expected) in FIVE.items():
   raw=(client/name).read_bytes(); actual=digest(raw); report['inputChecks'][name]={'bytes':len(raw),'sha256':actual}
   require(len(raw)==size and actual==expected,'five-input drift: '+name)
   if name=='YuanShen.exe': binary=raw
  from tools.extract_client72 import PE,number
  pe=PE(binary)
  def read(va,n,executable=False):
   off=pe.offset(va,n,executable=executable); return binary[off:off+n]
  report['stage']='closed-aot-slot'; slot=read(SLOT,8); bind_slot(slot)
  (dest/'slot.bin').write_bytes(slot); (dest/'caller.bin').write_bytes((ROOT/'local/migration72/d02-resume-20261110b/c-proud-prepare-window/final1/code.bin').read_bytes())
  report['aot']={'slot':hex(SLOT),'slotHex':slot.hex(),'pointerIndex':84548}
  report['stage']='pdata-before-body'; nt=number(binary,0x3c); rva,size=struct.unpack_from('<II',binary,nt+24+0x70+3*8)
  require((rva,size)==(0x1a4e6a40,14344080),'exception directory drift'); table=read(pe.base+rva,size); diagnostic=extent(table,ENTRY,pe.base,rva); report['exceptionDirectory']={'rva':hex(rva),'bytes':size,'sha256':digest(table),'recordsScanned':len(table)//12,'diagnostic':diagnostic}
  save(dest/'boundary.private.json',report); row=choose(diagnostic); pdata=table[row['offset']:row['offset']+12]; (dest/'pdata.bin').write_bytes(pdata)
  report.update(pdataHex=pdata.hex(),pdataSHA256=digest(pdata),entry=hex(ENTRY),endExclusive=row['endExclusive'],codeBytes=row['size']); save(dest/'pre-body.private.json',report)
  bound(row['size']); code=read(ENTRY,row['size'],True); decoded=decode(code); (dest/'code.bin').write_bytes(code); save(dest/'code.asm.private.json',decoded['lines'])
  report.update(stage='complete',passed=True,newCodeWindows=1,codeSHA256=digest(code),assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()),instructions=len(decoded['lines']),
   calls=decoded['calls'],branches=decoded['branches'],ripReferences=decoded['ripReferences'],memoryOperands=decoded['memoryOperands'],implicitStackInstructions=decoded['implicitStackInstructions'],capstoneVersion=decoded['capstoneVersion'],
   methodSemanticsProven=False,loaderExecuted=False,actualIndexFilled=False,actualRowSpanApproved=False,parserExecuted=False,resourceReady=False)
  save(dest/'extraction.private.json',report)
  print(json.dumps({'codeBytes':len(code),'sha256':digest(code),'instructions':len(decoded['lines']),'calls':len(decoded['calls']),'branches':len(decoded['branches']),'rip':len(decoded['ripReferences']),'memory':len(decoded['memoryOperands'])}))
 except Exception as e:
  report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise

def audit(first,second,out):
 identity=source_gate(); copies=[{n:(d/n).read_bytes() for n in FILES} for d in (first,second)]; require(copies[0]==copies[1],'eight originals differ')
 files=copies[0]; report=json.loads(files['extraction.private.json']); pre=json.loads(files['pre-body.private.json']); boundary=json.loads(files['boundary.private.json'])
 row=choose(report['exceptionDirectory']['diagnostic']); bound(row['size'])
 require(boundary['stage']=='pdata-before-body' and pre['stage']=='pdata-before-body' and boundary['exceptionDirectory']==pre['exceptionDirectory']==report['exceptionDirectory'] and pre['aot']==boundary['aot']==report['aot'] and pre['identity']==boundary['identity']==report['identity'],'pre-body provenance differs')
 begin,end,_=struct.unpack('<III',files['pdata.bin']); require(begin+0x140000000==ENTRY and end-begin==row['size'] and hex(end+0x140000000)==row['endExclusive'],'pdata bytes differ')
 bind_slot(files['slot.bin']); require(digest(files['caller.bin'])==identity['callerSHA256'],'caller artifact drift'); code=files['code.bin']; require(len(code)==row['size'] and digest(code)==report['codeSHA256'] and report['entry']==hex(ENTRY) and report['endExclusive']==row['endExclusive'] and report['pdataHex']==files['pdata.bin'].hex() and report['pdataSHA256']==digest(files['pdata.bin']) and report['assemblySHA256']==digest(files['code.asm.private.json']),'code or recorded provenance differs')
 dec=decode(code); require(dec['lines']==json.loads(files['code.asm.private.json']) and all(dec[k]==report[k] for k in ('calls','branches','ripReferences','memoryOperands','implicitStackInstructions','capstoneVersion')),'decoded evidence differs')
 require(report['identity']==identity and report['sourcePins']==PINS and report['sourceSHA256']==digest(Path(__file__).read_bytes()) and report['inputChecks']=={n:{'bytes':s,'sha256':h} for n,(s,h) in FIVE.items()},'source or input drift')
 require(report['passed'] and report['stage']=='complete' and report['newCodeWindows']==1 and not any(report[k] for k in ('nativeCPU','liveReads','xdataBytesRead','neighborCalleeBytesRead','methodSemanticsProven','loaderExecuted','actualIndexFilled','actualRowSpanApproved','parserExecuted','resourceReady')),'scope differs')
 result={'task':report['task'],'passed':True,'twoFreshEightFilesEqual':True,'identity':identity,'entry':hex(ENTRY),'endExclusive':row['endExclusive'],'codeBytes':len(code),'instructions':len(dec['lines']),
  'artifacts':{n:{'bytes':len(b),'sha256':digest(b)} for n,b in files.items()},'calls':dec['calls'],'branches':dec['branches'],'ripReferences':dec['ripReferences'],'memoryOperands':dec['memoryOperands'],'implicitStackInstructions':dec['implicitStackInstructions'],
  'currentPEReads':0,'nativeCPU':0,'liveReads':0,'methodSemanticsProven':False,'resourceReady':False}
 save(out,result); print(json.dumps({'auditBytes':out.stat().st_size,'sha256':digest(out.read_bytes())}))

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args(); dest=PRIVATE/a.output
 require(dest.name==a.output and not dest.exists(),'new task-local basename required')
 if a.audit:
  require(dest.suffix=='.json' and all((PRIVATE/n).name==n for n in a.audit),'audit needs task-local directories and JSON')
  audit(*(PRIVATE/n for n in a.audit),dest)
 else: dest.mkdir();collect(dest)
if __name__=='__main__': main()
