"""Single approved element-reader window; static mechanical extraction only."""
import argparse, hashlib, json, pickle, struct, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=ROOT/'local/migration72/d02-resume-20261110b/c-proud-element-reader-window'
BASE='local/migration72/d02-resume-20261110b/'
META='local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG='local/migration72/evidence/client72-signatures.json'
CALLERS=[BASE+'c-proud-native-struct-array-window/fresh1/code.bin',BASE+'c-proud-native-struct-array-window/fresh2/code.bin']
ENTRY=0x14ee1ea00; END=0x14ee1eb1d; CALLER=0x14ee1eb20; CELL=0x1422f9950; METHOD=439383
BODY_SHA="8f64b2b9c0020c75eff11786e33d7e257d9925a08b83b8b1975de0b9524c5247"
FILES=('boundary.private.json','cell.bin','slot.bin','pdata.bin','pre-body.private.json','code.bin','code.asm.private.json','extraction.private.json')
PINS={
 META:'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
 SIG:'28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
 CALLERS[0]:'de050d512bbd5446892a4b68e226f17af504607d6617a18ee2f1675699b89573',
 CALLERS[1]:'de050d512bbd5446892a4b68e226f17af504607d6617a18ee2f1675699b89573',
 BASE+'c-proud-native-struct-array-contract/next-element-reader-source-request.zh.md':'83e28adb3d957f1120570a542ba2d5ad781a1ad801a7999071af7aed953d5218',
 BASE+'c-proud-native-struct-array-contract/main-third.private.json':'ef4347336c719c56b9546edb4ae0e86ae05b4e007e0d1b9f5243b0e1f92ff6b1',
 BASE+'c-proud-native-struct-array-contract/root-next-source-approval.private.json':'188dcae6508f5ccc45ed066398e3cef4ac69d4ecce27ca68f8ce3d4048e92be0',
 'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
 'tools/game_path.ps1':'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
 'protocol/cn-7.2/profile.json':'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
 'local/migration72/python/capstone/__init__.py':'94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
 'local/migration72/python/capstone/lib/capstone.dll':'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3'}
FIVE={
'YuanShen.exe':(450927000,'8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'),
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
 require(len(fixed[BASE+'c-proud-native-struct-array-contract/next-element-reader-source-request.zh.md'])==6324 and len(fixed[BASE+'c-proud-native-struct-array-contract/main-third.private.json'])==207092 and len(fixed[BASE+'c-proud-native-struct-array-contract/root-next-source-approval.private.json'])==543,'upstream size drift')
 caller=fixed[CALLERS[0]]
 require(len(caller)==329 and caller==fixed[CALLERS[1]],'complete caller differs')
 setup={0x14ee1eb33:'4889cf',0x14ee1ebd4:'4c8d742420',0x14ee1ebe0:'48c744242000000000',0x14ee1ebe9:'4c89f1',0x14ee1ebec:'4889fa',0x14ee1ebef:'e80cfeffff',0x14ee1ebfc:'4c8b442420'}
 for pc,raw in setup.items():
  actual=caller[pc-CALLER:pc-CALLER+len(bytes.fromhex(raw))]
  require(actual.hex()==raw,'caller setup/E8 differs')
  if pc==0x14ee1ebef: require(pc+5+int.from_bytes(actual[1:],'little',signed=True)==ENTRY,'E8 target differs')
 records,_=pickle.loads(fixed[META]); signatures=json.loads(fixed[SIG])
 hits=[(r['index'],m) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
 require(len(hits)==1 and hits[0][0]==52802 and records[52802]['name']=='FLJHJHJJDME','unique owner differs')
 method=hits[0][1]
 require((method['index'],method['name'],method['flags'],method['virtualSlot'],method['parameterCount'])==(METHOD,'CNLIBMNOALB','0x83',65535,1),'method declaration differs')
 sigs=[m for m in signatures['methods'] if m['index']==METHOD]
 require(len(sigs)==1 and sigs[0]['declaringType']==52802 and sigs[0]['registeredAddress']==hex(ENTRY) and sigs[0]['name']==method['name'],'signature differs')
 sig=sigs[0]; refs=[signatures['typeReferences'][str(p['typeIndex'])] for p in sig['parameters']]
 require([(p['index'],p['typeIndex']) for p in sig['parameters']]==[(362908,86325)],'parameter indices differ')
 require(refs[0]['kind']=='class' and not refs[0]['byRef'] and refs[0]['typeDefinition']==42005 and refs[0]['name']=='OFMKHIMCGKE','parameter type differs')
 return {'method':method,'signature':sig,'parameterTypes':refs,'returnType':'unknown','callerSHA256':digest(caller),'setupAndE8':{hex(k):v for k,v in setup.items()}}

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
def bound(row):
 require(row['begin']==hex(ENTRY) and row['endExclusive']==hex(END) and row['size']==285,'body boundary mismatch')
def bind_body(code):
 require(len(code)==285 and digest(code)==BODY_SHA,'body identity mismatch')
def bind_slot(slot):
 require(len(slot)==8 and int.from_bytes(slot,'little')==ENTRY,'AOT pointer mismatch')

def decode(code):
 sys.path.insert(0,str(ROOT/'local/migration72/python'))
 import capstone
 from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
 cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True
 lines=[]; calls=[]; branches=[]; rip=[]; memory=[]; implicit_stack=[]
 for i in cs.disasm(code,ENTRY,count=128):
  line={'address':hex(i.address),'hex':bytes(i.bytes).hex(),'mnemonic':i.mnemonic,'operands':i.op_str}; lines.append(line)
  if i.mnemonic in ('push','pop','call','ret'): implicit_stack.append(line)
  if i.group(capstone.CS_GRP_JUMP): branches.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  if i.group(capstone.CS_GRP_CALL): calls.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  for n,o in enumerate(i.operands):
   if o.type==X86_OP_MEM:
    memory.append(dict(line,operandIndex=n,access=o.access))
    if o.mem.base==X86_REG_RIP: rip.append(dict(line,operandIndex=n,target=hex(i.address+i.size+o.mem.disp)))
 require(0<len(lines)<=128,'instruction limit exceeds 128')
 cursor=ENTRY
 for line in lines:
  b=bytes.fromhex(line['hex']); require(int(line['address'],16)==cursor and code[cursor-ENTRY:cursor-ENTRY+len(b)]==b,'decode gap or bytes differ'); cursor+=len(b)
 require(cursor==ENTRY+len(code),'decode incomplete or exceeds 128')
 return {'lines':lines,'calls':calls,'branches':branches,'ripReferences':rip,'memoryOperands':memory,'implicitStackInstructions':implicit_stack,'capstoneVersion':capstone.__version__}

def collect(dest):
 report={'task':'E-01/R3-PROUD-ELEMENT-READER-WINDOW','stage':'source-gate','sourceSHA256':digest(Path(__file__).read_bytes()),'sourcePins':PINS,'inputChecks':{},'nativeCPU':0,'liveReads':0,'xdataBytesRead':0,'neighborCalleeBytesRead':0,'newCodeWindows':0,'network':0,'targetProcessOperations':0,'builds':0,'database':0,'git':0}
 try:
  report['identity']=source_gate(); report['stage']='five-input-gate'
  client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip()); binary=None
  for name,(size,expected) in FIVE.items():
   raw=(client/name).read_bytes(); actual=digest(raw); report['inputChecks'][name]={'bytes':len(raw),'sha256':actual}
   require(len(raw)==size and actual==expected,'five-input drift: '+name)
   if name=='YuanShen.exe': binary=raw
  sys.path.insert(0,str(ROOT))
  from tools.extract_client72 import PE,number
  pe=PE(binary)
  def read(va,n,executable=False):
   off=pe.offset(va,n,executable=executable); return binary[off:off+n]
  report['stage']='caller-and-aot'
  caller=read(CALLER,329,True)
  require(caller==(ROOT/CALLERS[0]).read_bytes(),'current complete caller differs')
  cell=read(CELL,8); pointer=int.from_bytes(cell,'little'); slot_address=pointer+METHOD*8
  (dest/'cell.bin').write_bytes(cell)
  slot=read(slot_address,8); (dest/'slot.bin').write_bytes(slot); bind_slot(slot)
  report['aot']={'cell':hex(CELL),'cellHex':cell.hex(),'table':hex(pointer),'method':METHOD,'slot':hex(slot_address),'slotHex':slot.hex()}
  report['stage']='pdata-before-body'; nt=number(binary,0x3c); rva,size=struct.unpack_from('<II',binary,nt+24+0x70+3*8)
  require((rva,size)==(0x1a4e6a40,14344080),'exception directory drift')
  table=read(pe.base+rva,size); table_hash=digest(table)
  require(table_hash=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory SHA drift')
  diagnostic=extent(table,ENTRY,pe.base,rva)
  report['exceptionDirectory']={'rva':hex(rva),'bytes':size,'sha256':table_hash,'recordsScanned':len(table)//12,'diagnostic':diagnostic}
  save(dest/'boundary.private.json',report); row=choose(diagnostic); pdata=table[row['offset']:row['offset']+12]; (dest/'pdata.bin').write_bytes(pdata)
  report.update(pdataHex=pdata.hex(),pdataSHA256=digest(pdata),entry=hex(ENTRY),endExclusive=row['endExclusive'],codeBytes=row['size']); save(dest/'pre-body.private.json',report)
  bound(row); code=read(ENTRY,row['size'],True); report['newCodeWindows']=1; (dest/'code.bin').write_bytes(code); bind_body(code); decoded=decode(code); save(dest/'code.asm.private.json',decoded['lines'])
  report.update(stage='complete',passed=True,newCodeWindows=1,codeSHA256=digest(code),assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()),instructions=len(decoded['lines']),calls=decoded['calls'],branches=decoded['branches'],ripReferences=decoded['ripReferences'],memoryOperands=decoded['memoryOperands'],implicitStackInstructions=decoded['implicitStackInstructions'],capstoneVersion=decoded['capstoneVersion'],methodSemanticsProven=False,parserExecuted=False,resourceReady=False)
  save(dest/'extraction.private.json',report)
  print(json.dumps({'codeBytes':len(code),'sha256':digest(code),'instructions':len(decoded['lines']),'calls':len(decoded['calls']),'branches':len(decoded['branches']),'rip':len(decoded['ripReferences']),'memory':len(decoded['memoryOperands'])}))
 except Exception as e:
  report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise

def audit(first,second,out):
 identity=source_gate(); copies=[{n:(d/n).read_bytes() for n in FILES} for d in (first,second)]; require(copies[0]==copies[1],'eight originals differ')
 files=copies[0]; report=json.loads(files['extraction.private.json']); pre=json.loads(files['pre-body.private.json']); boundary=json.loads(files['boundary.private.json'])
 row=choose(report['exceptionDirectory']['diagnostic']); bound(row)
 require(boundary['stage']=='pdata-before-body' and pre['stage']=='pdata-before-body' and boundary['exceptionDirectory']==pre['exceptionDirectory']==report['exceptionDirectory'] and pre['aot']==boundary['aot']==report['aot'] and pre['identity']==boundary['identity']==report['identity'],'pre-body provenance differs')
 directory=report['exceptionDirectory']; require((directory['rva'],directory['bytes'],directory['recordsScanned'],directory['sha256'])==('0x1a4e6a40',14344080,1195340,'5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d'),'directory provenance differs')
 begin,end,_=struct.unpack('<III',files['pdata.bin']); require(begin+0x140000000==ENTRY and end-begin==row['size'] and hex(end+0x140000000)==row['endExclusive'],'pdata bytes differ')
 cell=files['cell.bin']; slot=files['slot.bin']; bind_slot(slot); aot=report['aot']; require(len(cell)==8 and aot=={'cell':hex(CELL),'cellHex':cell.hex(),'table':hex(int.from_bytes(cell,'little')),'method':METHOD,'slot':hex(int.from_bytes(cell,'little')+METHOD*8),'slotHex':slot.hex()},'AOT provenance differs')
 code=files['code.bin']; bind_body(code); require(len(code)==row['size'] and digest(code)==report['codeSHA256'] and report['entry']==hex(ENTRY) and report['endExclusive']==row['endExclusive'] and report['pdataHex']==files['pdata.bin'].hex() and report['pdataSHA256']==digest(files['pdata.bin']) and report['assemblySHA256']==digest(files['code.asm.private.json']),'code or recorded provenance differs')
 dec=decode(code); require(dec['lines']==json.loads(files['code.asm.private.json']) and len(dec['lines'])==report['instructions'] and all(dec[k]==report[k] for k in ('calls','branches','ripReferences','memoryOperands','implicitStackInstructions','capstoneVersion')),'decoded evidence differs')
 require(report['identity']==identity and report['sourcePins']==PINS and report['sourceSHA256']==digest(Path(__file__).read_bytes()) and report['inputChecks']=={n:{'bytes':s,'sha256':h} for n,(s,h) in FIVE.items()},'source or input drift')
 require(report['passed'] and report['stage']=='complete' and report['newCodeWindows']==1 and not any(report[k] for k in ('nativeCPU','liveReads','xdataBytesRead','neighborCalleeBytesRead','network','targetProcessOperations','builds','database','git','methodSemanticsProven','parserExecuted','resourceReady')),'scope differs')
 result={'task':report['task'],'passed':True,'twoFreshEightFilesEqual':True,'identity':identity,'aot':aot,'exceptionDirectory':directory,'entry':hex(ENTRY),'endExclusive':row['endExclusive'],'codeBytes':len(code),'instructions':len(dec['lines']),'artifacts':{n:{'bytes':len(b),'sha256':digest(b)} for n,b in files.items()},'calls':dec['calls'],'branches':dec['branches'],'ripReferences':dec['ripReferences'],'memoryOperands':dec['memoryOperands'],'implicitStackInstructions':dec['implicitStackInstructions'],'currentPEReads':0,'nativeCPU':0,'liveReads':0,'methodSemanticsProven':False,'resourceReady':False}
 save(out,result); print(json.dumps({'auditBytes':out.stat().st_size,'sha256':digest(out.read_bytes())}))

def main():
 p=argparse.ArgumentParser(description=__doc__); p.add_argument('--output',required=True); p.add_argument('--audit',nargs=2); a=p.parse_args(); dest=PRIVATE/a.output
 require(dest.name==a.output and not dest.exists(),'new task-local basename required')
 if a.audit:
  require(dest.suffix=='.json' and all((PRIVATE/n).name==n for n in a.audit),'audit needs task-local directories and JSON')
  audit(*(PRIVATE/n for n in a.audit),dest)
 else: dest.mkdir(); collect(dest)
if __name__=='__main__': main()
