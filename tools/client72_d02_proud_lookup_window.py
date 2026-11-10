"""Single approved shared 157ffc4c0 window, static mechanical extraction only."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; PRIVATE=ROOT/'local/migration72/d02-resume-20261110b/c-proud-lookup-window'
sys.path.insert(0,str(ROOT))
ENTRY=0x157ffc4c0; BASE_CELL=0x1422f9910; SLOT=0x142515e78
FILES=('boundary.private.json','cell.bin','code.asm.private.json','code.bin','extraction.private.json','pdata.bin','pre-body.private.json','slot.bin')
PINS={
'local/migration72/d02-resume-20261110b/c-proud-factory-index-contract/next-lookup-window-request.zh.md':'cccd0d64ebf7b8cfc748193bff61237d223ae0f1eb27a4356a4e1c122d0d02b4',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/final3.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/final4.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/main-third.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261110b/c-proud-row-source/typed-producer1/code.bin':'c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d',
'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl':'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
'local/migration72/evidence/client72-signatures.json':'28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
'tools/client72_d02_proud_index_aot.py':'43733eda2878637cf5506eb6aa9ce785a00c30fdbc0a28cfc9a30fd94043dc98',
'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
'tools/game_path.ps1':'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
'protocol/cn-7.2/profile.json':'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
'local/migration72/python/capstone/__init__.py':'94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
'local/migration72/python/capstone/lib/capstone.dll':'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'}
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
 reports=[json.loads(fixed[p]) for p in PINS if '/c-proud-index-aot/' in p]
 require(len(reports)==3 and reports[0]==reports[1]==reports[2],'AOT reports differ')
 a=reports[0]; require(a['identityUnique'] and a['partial'] and not a['crossPointerIndexSameCodeComplete'], 'AOT scope differs')
 require(a['signatureCandidates']==[138527] and a['targetPointerIndices']==[84537] and len(a['samePointerIndexRows'])==1,'AOT binding ambiguous')
 row=a['samePointerIndexRows'][0]
 require((row['owner'],row['methodDefinition'],row['classInstance'],row['methodInstance'],row['tableIndex'],row['methodSpec'],row['pointerIndex'])==(18192,138527,5071,-1,13207,175093,84537),'AOT record differs')
 require(row['tableRecord14']=='394a0100f5ab0200ffff89660000' and row['specRecord12']=='1f1d0200cf130000ffffffff' and row['pointer']=={'slot':'0x142515e78','qword':'c0c4ff5701000000','address':'0x157ffc4c0'},'AOT original records differ')
 records,_=pickle.loads(fixed['local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl']); sig=json.loads(fixed['local/migration72/evidence/client72-signatures.json'])
 field=next(f for f in records[80063]['fields'] if f['index']==400190); t=field['type']
 require(field==a['field'] and (field['rawOffset'],t['typeDefinition'],t['instanceIndex'],[x.get('typeDefinition',x['kind']) for x in t['arguments']])==('0x4021f58',18192,5071,['uint32',75077,47647,25030]),'field tuple differs')
 method=next(m for m in records[18192]['methods'] if m['index']==138527); s=next(m for m in sig['methods'] if m['index']==138527)
 types=[sig['typeReferences'][str(p['typeIndex'])] for p in s['parameters']]
 require(method['name']=='NGNLGNHAKKO' and [(x['kind'],x.get('genericParameterPosition'),x['byRef']) for x in types]==[('var',0,False),('var',1,True)],'method signature differs')
 caller=fixed['local/migration72/d02-resume-20261110b/c-proud-row-source/typed-producer1/code.bin']
 for site,raw in [(0x14d7ad91f,'488b05dadc0bf8'),(0x14d7ad926,'488b88581f0200'),(0x14d7ad938,'4c8d45d089fa4531c9e87aeb840a'),(0x14d7ad946,'84c00f8449010000')]:
  require(caller[site-0x14d7ad8e0:site-0x14d7ad8e0+len(bytes.fromhex(raw))].hex()==raw,'caller bytes differ')
 require(len(caller)==606 and 0x14d7ad946+int.from_bytes(caller[0x62:0x66],'little',signed=True)==ENTRY,'caller E8 target differs')
 return {'field':field,'method':method,'signature':s,'parameterTypes':types,'aotRow':row,'callerSHA256':digest(caller),'callerSetupAndE8':'4c8d45d089fa4531c9e87aeb840a','crossPointerIndexSameCodeComplete':False}

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
def bind_slot(cell,slot):
 require(cell.hex()=='b00c474201000000' and int.from_bytes(cell,'little')+84537*8==SLOT and slot.hex()=='c0c4ff5701000000' and int.from_bytes(slot,'little')==ENTRY,'AOT pointer mismatch')
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
  if i.group(capstone.CS_GRP_CALL) and i.operands and i.operands[0].type==X86_OP_IMM: calls.append(dict(line,target=hex(i.operands[0].imm)))
  for n,o in enumerate(i.operands):
   if o.type==X86_OP_MEM:
    memory.append(dict(line,operandIndex=n,access=o.access))
    if o.mem.base==X86_REG_RIP: rip.append(dict(line,operandIndex=n,target=hex(i.address+i.size+o.mem.disp)))
 require(0<len(lines)<=1024,'instruction limit exceeds 1024')
 cursor=ENTRY
 for line in lines:
  b=bytes.fromhex(line['hex']); require(int(line['address'],16)==cursor and code[cursor-ENTRY:cursor-ENTRY+len(b)]==b,'decode gap or bytes differ'); cursor+=len(b)
 require(cursor==ENTRY+len(code),'decode incomplete')
 return {'lines':lines,'directCalls':calls,'branches':branches,'ripReferences':rip,'memoryOperands':memory,'implicitStackInstructions':implicit_stack,'capstoneVersion':capstone.__version__}

def collect(dest):
 report={'task':'E-01/R3-PROUD-LOOKUP-WINDOW','stage':'source-gate','sourceSHA256':digest(Path(__file__).read_bytes()),'sourcePins':PINS,'inputChecks':{},'nativeCPU':0,'liveReads':0,'xdataBytesRead':0,'neighborCalleeBytesRead':0,'newCodeWindows':0}
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
  report['stage']='closed-aot-slot'; cell=read(BASE_CELL,8); slot=read(SLOT,8); bind_slot(cell,slot)
  (dest/'cell.bin').write_bytes(cell); (dest/'slot.bin').write_bytes(slot)
  report['aot']={'cell':hex(BASE_CELL),'cellHex':cell.hex(),'slot':hex(SLOT),'slotHex':slot.hex(),'pointerIndex':84537}
  report['stage']='pdata-before-body'; nt=number(binary,0x3c); rva,size=struct.unpack_from('<II',binary,nt+24+0x70+3*8)
  table=read(pe.base+rva,size); diagnostic=extent(table,ENTRY,pe.base,rva); report['exceptionDirectory']={'rva':hex(rva),'bytes':size,'diagnostic':diagnostic}
  save(dest/'boundary.private.json',report); row=choose(diagnostic); pdata=table[row['offset']:row['offset']+12]; (dest/'pdata.bin').write_bytes(pdata)
  report.update(pdataHex=pdata.hex(),pdataSHA256=digest(pdata),entry=hex(ENTRY),endExclusive=row['endExclusive'],codeBytes=row['size']); save(dest/'pre-body.private.json',report)
  bound(row['size']); code=read(ENTRY,row['size'],True); decoded=decode(code); (dest/'code.bin').write_bytes(code); save(dest/'code.asm.private.json',decoded['lines'])
  report.update(stage='complete',passed=True,newCodeWindows=1,codeSHA256=digest(code),assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()),instructions=len(decoded['lines']),
   directCalls=decoded['directCalls'],branches=decoded['branches'],ripReferences=decoded['ripReferences'],memoryOperands=decoded['memoryOperands'],implicitStackInstructions=decoded['implicitStackInstructions'],capstoneVersion=decoded['capstoneVersion'],
   methodSemanticsProven=False,loaderExecuted=False,actualIndexFilled=False,actualRowSpanApproved=False,parserExecuted=False,resourceReady=False)
  save(dest/'extraction.private.json',report)
  print(json.dumps({'codeBytes':len(code),'sha256':digest(code),'instructions':len(decoded['lines']),'calls':len(decoded['directCalls']),'branches':len(decoded['branches']),'rip':len(decoded['ripReferences']),'memory':len(decoded['memoryOperands'])}))
 except Exception as e:
  report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise

def audit(first,second,out):
 identity=source_gate(); copies=[{n:(d/n).read_bytes() for n in FILES} for d in (first,second)]; require(copies[0]==copies[1],'eight originals differ')
 files=copies[0]; report=json.loads(files['extraction.private.json']); pre=json.loads(files['pre-body.private.json']); boundary=json.loads(files['boundary.private.json'])
 row=choose(report['exceptionDirectory']['diagnostic']); bound(row['size'])
 require(boundary['stage']=='pdata-before-body' and pre['stage']=='pdata-before-body' and boundary['exceptionDirectory']==pre['exceptionDirectory']==report['exceptionDirectory'] and pre['aot']==boundary['aot']==report['aot'] and pre['identity']==boundary['identity']==report['identity'],'pre-body provenance differs')
 begin,end,_=struct.unpack('<III',files['pdata.bin']); require(begin+0x140000000==ENTRY and end-begin==row['size'] and hex(end+0x140000000)==row['endExclusive'],'pdata bytes differ')
 bind_slot(files['cell.bin'],files['slot.bin']); code=files['code.bin']; require(len(code)==row['size'] and digest(code)==report['codeSHA256'] and report['entry']==hex(ENTRY) and report['endExclusive']==row['endExclusive'] and report['pdataHex']==files['pdata.bin'].hex() and report['pdataSHA256']==digest(files['pdata.bin']) and report['assemblySHA256']==digest(files['code.asm.private.json']),'code or recorded provenance differs')
 dec=decode(code); require(dec['lines']==json.loads(files['code.asm.private.json']) and all(dec[k]==report[k] for k in ('directCalls','branches','ripReferences','memoryOperands','implicitStackInstructions','capstoneVersion')),'decoded evidence differs')
 require(report['identity']==identity and report['sourcePins']==PINS and report['sourceSHA256']==digest(Path(__file__).read_bytes()) and report['inputChecks']=={n:{'bytes':s,'sha256':h} for n,(s,h) in FIVE.items()},'source or input drift')
 require(report['passed'] and report['stage']=='complete' and report['newCodeWindows']==1 and not any(report[k] for k in ('nativeCPU','liveReads','xdataBytesRead','neighborCalleeBytesRead','methodSemanticsProven','loaderExecuted','actualIndexFilled','actualRowSpanApproved','parserExecuted','resourceReady')),'scope differs')
 result={'task':report['task'],'passed':True,'twoFreshEightFilesEqual':True,'identity':identity,'entry':hex(ENTRY),'endExclusive':row['endExclusive'],'codeBytes':len(code),'instructions':len(dec['lines']),
  'artifacts':{n:{'bytes':len(b),'sha256':digest(b)} for n,b in files.items()},'directCalls':dec['directCalls'],'branches':dec['branches'],'ripReferences':dec['ripReferences'],'memoryOperands':dec['memoryOperands'],'implicitStackInstructions':dec['implicitStackInstructions'],
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
