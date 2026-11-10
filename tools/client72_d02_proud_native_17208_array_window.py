"""Single approved native-struct-array window; static mechanical extraction only."""
import argparse, hashlib, json, pickle, struct, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=ROOT/'local/migration72/d02-resume-20261110b/c-proud-native-17208-array-window'
BASE='local/migration72/d02-resume-20261110b/'
META='local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG='local/migration72/evidence/client72-signatures.json'
CALLERS=[BASE+'c-proud-parser-contract/parser1/code.bin',BASE+'c-proud-parser-contract/parser2/code.bin']
ENTRY=0x147e91d10; CALLER=0x149f0c1f0; CELL=0x1422f9950; METHOD=130225
FILES=('boundary.private.json','cell.bin','slot.bin','pdata.bin','pre-body.private.json','code.bin','code.asm.private.json','extraction.private.json')
PINS={
 META:'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
 SIG:'28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
 CALLERS[0]:'949868da171fd252ad9f8486d1a20bf5c211dbdc71065106db7775b6b18e682b',
 CALLERS[1]:'949868da171fd252ad9f8486d1a20bf5c211dbdc71065106db7775b6b18e682b',
 BASE+'c-proud-derived-file-contract/next-native-17208-array-source-request.zh.md':'3c61c935475bd1979685b51dce5d2689795c4e0ce9b2bbc4881984a7458ac327',
 BASE+'c-proud-derived-file-contract/main-third.private.json':'4849718ddb2679ad26d821d2c74b867968e31ed11b5593d4abeb903f03eb355f',
 BASE+'c-proud-derived-file-contract/fresh1.private.json':'4849718ddb2679ad26d821d2c74b867968e31ed11b5593d4abeb903f03eb355f',
 BASE+'c-proud-derived-file-contract/fresh2.private.json':'4849718ddb2679ad26d821d2c74b867968e31ed11b5593d4abeb903f03eb355f',
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
 request=BASE+'c-proud-derived-file-contract/next-native-17208-array-source-request.zh.md'
 third=BASE+'c-proud-derived-file-contract/main-third.private.json'
 require(len(fixed[request])==5887 and len(fixed[third])==576784 and fixed[third]==fixed[BASE+'c-proud-derived-file-contract/fresh1.private.json']==fixed[BASE+'c-proud-derived-file-contract/fresh2.private.json'],'upstream size or copies drift')
 approval=json.loads((ROOT/BASE/'c-proud-derived-file-contract/root-next-source-approval.private.json').read_bytes())
 require(approval=={'approvedSingleEntry':'147e91d10','codeMax':8192,'exceptionDirectoryActualReadLedgerRequired':True,'instructionMax':2048,'method':130225,'newAotQwords':2,'rootAcceptedRequestSHA256':PINS[request],'rootThirdBytes':576784,'rootThirdSHA256':PINS[third],'runtimeApproved':False,'task':'E-01/R3-PROUD-NATIVE-17208-ARRAY-WINDOW','twoFreshAndRootThirdRequired':True,'xdataAndNeighborBodies':0},'approval differs')
 caller=fixed[CALLERS[0]]
 require(len(caller)==7674 and caller==fixed[CALLERS[1]],'complete caller differs')
 setup={0x149f0d8d5:'488b4d48',0x149f0d8dd:'884241',0x149f0d8e0:'f7453400008000',0x149f0d8e7:'7521',0x149f0d90a:'498d5550',0x149f0d90e:'4531c0',0x149f0d911:'e8fa43f8fd'}
 for pc,raw in setup.items():
  actual=caller[pc-CALLER:pc-CALLER+len(bytes.fromhex(raw))]
  require(actual.hex()==raw,'caller setup/E8 differs')
  if pc==0x149f0d8e7: require(pc+2+int.from_bytes(actual[1:],'little',signed=True)==0x149f0d90a and (0x1b8afee2 & 0x800000)!=0,'rawH branch differs')
  if pc==0x149f0d911: require(pc+5+int.from_bytes(actual[1:],'little',signed=True)==ENTRY,'E8 target differs')
 require(caller[0x149f0c341-CALLER:0x149f0c341-CALLER+4].hex()=='48897d48','reader save differs')
 records,_=pickle.loads(fixed[META]); signatures=json.loads(fixed[SIG])
 hits=[(r['index'],m) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
 require(len(hits)==1 and hits[0][0]==17208 and records[17208]['name']=='BPDMGCDGJBK','unique owner differs')
 method=hits[0][1]
 require((method['index'],method['name'],method['flags'],method['virtualSlot'],method['parameterCount'])==(METHOD,'CNLIBMNOALB','0x93',65535,3),'method declaration differs')
 sigs=[m for m in signatures['methods'] if m['index']==METHOD]
 require(len(sigs)==1 and sigs[0]['declaringType']==17208 and sigs[0]['registeredAddress']==hex(ENTRY) and sigs[0]['name']==method['name'],'signature differs')
 sig=sigs[0]; refs=[signatures['typeReferences'][str(p['typeIndex'])] for p in sig['parameters']]
 require([(p['index'],p['typeIndex']) for p in sig['parameters']]==[(121524,86325),(121525,498052),(121526,480006)],'parameter indices differ')
 require(refs[0]['kind']=='class' and refs[0]['typeDefinition']==42005 and refs[1]['kind']=='genericinst' and refs[1]['byRef'] and refs[1]['typeDefinition']==11506 and refs[1]['genericIndex']==86675 and refs[1]['instanceIndex']==5954 and refs[1]['arguments'][0]['kind']=='valuetype' and refs[1]['arguments'][0]['typeDefinition']==17208 and refs[2]['kind']=='class' and refs[2]['typeDefinition']==11476 and refs[2]['name']=='INativeAllocator','parameter types differ')
 fields=[f for f in records[63831]['fields'] if f['index']==316251]
 require(len(fields)==1 and fields[0]['offset']==0x50 and fields[0]['name']=='HEENJHOPKDO' and fields[0]['type']['genericIndex']==86675 and fields[0]['type']['instanceIndex']==5954 and fields[0]['type']['arguments'][0]['typeDefinition']==17208,'caller output field differs')
 return {'method':method,'signature':sig,'parameterTypes':refs,'callerField':fields[0],'returnType':'unknown','callerSHA256':digest(caller),'rawH':hex(0x1b8afee2),'setupAndE8':{hex(k):v for k,v in setup.items()}}

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
def bound(size): require(0<size<=8192,'extent exceeds 8192B bound')
def bind_slot(slot):
 require(len(slot)==8 and int.from_bytes(slot,'little')==ENTRY,'AOT pointer mismatch')

def decode(code):
 sys.path.insert(0,str(ROOT/'local/migration72/python'))
 import capstone
 from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
 cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True
 lines=[]; calls=[]; branches=[]; rip=[]; memory=[]; implicit_stack=[]
 for i in cs.disasm(code,ENTRY,count=2048):
  line={'address':hex(i.address),'hex':bytes(i.bytes).hex(),'mnemonic':i.mnemonic,'operands':i.op_str}; lines.append(line)
  if i.mnemonic in ('push','pop','call','ret'): implicit_stack.append(line)
  if i.group(capstone.CS_GRP_JUMP): branches.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  if i.group(capstone.CS_GRP_CALL): calls.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  for n,o in enumerate(i.operands):
   if o.type==X86_OP_MEM:
    memory.append(dict(line,operandIndex=n,access=o.access))
    if o.mem.base==X86_REG_RIP: rip.append(dict(line,operandIndex=n,target=hex(i.address+i.size+o.mem.disp)))
 require(0<len(lines)<=2048,'instruction limit exceeds 2048')
 cursor=ENTRY
 for line in lines:
  b=bytes.fromhex(line['hex']); require(int(line['address'],16)==cursor and code[cursor-ENTRY:cursor-ENTRY+len(b)]==b,'decode gap or bytes differ'); cursor+=len(b)
 require(cursor==ENTRY+len(code),'decode incomplete or exceeds 2048')
 return {'lines':lines,'calls':calls,'branches':branches,'ripReferences':rip,'memoryOperands':memory,'implicitStackInstructions':implicit_stack,'capstoneVersion':capstone.__version__}

def collect(dest):
 report={'task':'E-01/R3-PROUD-NATIVE-17208-ARRAY-WINDOW','stage':'source-gate','sourceSHA256':digest(Path(__file__).read_bytes()),'sourcePins':PINS,'inputChecks':{},'nativeCPU':0,'liveReads':0,'xdataBytesRead':0,'neighborCalleeBytesRead':0,'newCodeWindows':0,'network':0,'targetProcessOperations':0,'builds':0,'database':0,'git':0}
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
  caller=read(CALLER,7674,True)
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
  report['exceptionDirectory']={'rva':hex(rva),'bytes':size,'sha256':table_hash,'recordsScanned':len(table)//12,'readLedger':{'va':hex(pe.base+rva),'fileOffset':pe.offset(pe.base+rva,size),'requestedBytes':size,'actualBytes':len(table),'sha256':table_hash},'diagnostic':diagnostic}
  save(dest/'boundary.private.json',report); row=choose(diagnostic); pdata=table[row['offset']:row['offset']+12]; (dest/'pdata.bin').write_bytes(pdata)
  report.update(pdataHex=pdata.hex(),pdataSHA256=digest(pdata),entry=hex(ENTRY),endExclusive=row['endExclusive'],codeBytes=row['size']); save(dest/'pre-body.private.json',report)
  bound(row['size']); code=read(ENTRY,row['size'],True); (dest/'code.bin').write_bytes(code); decoded=decode(code); save(dest/'code.asm.private.json',decoded['lines'])
  report.update(stage='complete',passed=True,newCodeWindows=1,codeSHA256=digest(code),assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()),instructions=len(decoded['lines']),calls=decoded['calls'],branches=decoded['branches'],ripReferences=decoded['ripReferences'],memoryOperands=decoded['memoryOperands'],implicitStackInstructions=decoded['implicitStackInstructions'],capstoneVersion=decoded['capstoneVersion'],methodSemanticsProven=False,parserExecuted=False,resourceReady=False)
  save(dest/'extraction.private.json',report)
  print(json.dumps({'codeBytes':len(code),'sha256':digest(code),'instructions':len(decoded['lines']),'calls':len(decoded['calls']),'branches':len(decoded['branches']),'rip':len(decoded['ripReferences']),'memory':len(decoded['memoryOperands'])}))
 except Exception as e:
  report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise

def audit(first,second,out):
 identity=source_gate(); copies=[{n:(d/n).read_bytes() for n in FILES} for d in (first,second)]; require(copies[0]==copies[1],'eight originals differ')
 files=copies[0]; report=json.loads(files['extraction.private.json']); pre=json.loads(files['pre-body.private.json']); boundary=json.loads(files['boundary.private.json'])
 row=choose(report['exceptionDirectory']['diagnostic']); bound(row['size'])
 require(boundary['stage']=='pdata-before-body' and pre['stage']=='pdata-before-body' and boundary['exceptionDirectory']==pre['exceptionDirectory']==report['exceptionDirectory'] and pre['aot']==boundary['aot']==report['aot'] and pre['identity']==boundary['identity']==report['identity'],'pre-body provenance differs')
 directory=report['exceptionDirectory']; require((directory['rva'],directory['bytes'],directory['recordsScanned'],directory['sha256'])==('0x1a4e6a40',14344080,1195340,'5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d'),'directory provenance differs')
 require(directory['readLedger']=={'va':hex(0x140000000+0x1a4e6a40),'fileOffset':directory['readLedger']['fileOffset'],'requestedBytes':14344080,'actualBytes':14344080,'sha256':directory['sha256']},'actual read ledger differs')
 begin,end,_=struct.unpack('<III',files['pdata.bin']); require(begin+0x140000000==ENTRY and end-begin==row['size'] and hex(end+0x140000000)==row['endExclusive'],'pdata bytes differ')
 cell=files['cell.bin']; slot=files['slot.bin']; bind_slot(slot); aot=report['aot']; require(len(cell)==8 and aot=={'cell':hex(CELL),'cellHex':cell.hex(),'table':hex(int.from_bytes(cell,'little')),'method':METHOD,'slot':hex(int.from_bytes(cell,'little')+METHOD*8),'slotHex':slot.hex()},'AOT provenance differs')
 code=files['code.bin']; require(len(code)==row['size'] and digest(code)==report['codeSHA256'] and report['entry']==hex(ENTRY) and report['endExclusive']==row['endExclusive'] and report['pdataHex']==files['pdata.bin'].hex() and report['pdataSHA256']==digest(files['pdata.bin']) and report['assemblySHA256']==digest(files['code.asm.private.json']),'code or recorded provenance differs')
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
