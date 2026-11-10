"""One approved static parser window with fresh input and extent gates."""
import argparse
import hashlib
import io
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b/scene-mplevel-parser-window'
UP = 'local/migration72/d02-resume-20261110b/scene-mplevel-group-source/'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG = 'local/migration72/evidence/client72-signatures.json'
ENTRY, CALLER, CELL, METHOD = 0x14b6accb0, 0x14aa99a50, 0x1422f9950, 587454
PINS = {
 META: (117201003, 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f'),
 SIG: (78649549, '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe'),
 UP+'one-window-request.private.json': (None, 'f0cc4c1e5736069c689511c447243548ec226be0abc24a3a1f1dc56f360e3d48'),
 UP+'root-next-source-approval.private.json': (None, '6b913db18c86500c67a6fedb3829abdf179b7f59fc94cd498878a30a4ae21947'),
 UP+'main-third.private.json': (51096, 'd45198fa2cc6919ecb2c5f4f84c6264e214048d4169abf31c8e7b32ea1bbb3e2'),
 'tools/extract_client72.py': (None, '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d'),
 'tools/game_path.ps1': (None, '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b'),
 'local/migration72/python/capstone/__init__.py': (None, '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f'),
 'local/migration72/python/capstone/lib/capstone.dll': (None, '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3')}
FIVE = {
 'YuanShen.exe': (450927000,'8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'),
 'YuanShen_Data/Managed/Metadata/global-metadata.dat': (83644188,'45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a'),
 'YuanShen_Data/Managed/Metadata/startup-metadata.dat': (4116148,'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105'),
 'YuanShen_Data/Plugins/AccountPlatNative.dll': (9360280,'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'),
 'YuanShen_Data/Plugins/Astrolabe.dll': (2614168,'87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442')}
CALLER_SHA = '991c1e787c57e6268491098762c259c502ce01923796d47959bfa95ca7091569'

class PureData(pickle.Unpickler):
 def find_class(self, module, name): raise ValueError('metadata contains global class')
def digest(data): return hashlib.sha256(data).hexdigest()
def require(value, message):
 if not value: raise ValueError(message)
def save(path, obj):
 with path.open('xb') as stream: stream.write((json.dumps(obj,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode())
def source_gate():
 raw = {}
 for path,(size,sha) in PINS.items():
  data=(ROOT/path).read_bytes(); require((size is None or len(data)==size) and digest(data)==sha,'source drift: '+path); raw[path]=data
 # Both complete inputs have passed SHA gates before either is parsed.
 records,_=PureData(io.BytesIO(raw[META])).load(); signatures=json.loads(raw[SIG])
 approval=json.loads(raw[UP+'root-next-source-approval.private.json']); request=json.loads(raw[UP+'one-window-request.private.json'])
 require(approval['approvedSingleEntry']=='14b6accb0' and approval['owner']==70954 and approval['method']==METHOD and approval['codeMax']==2048 and approval['instructionMax']==512 and approval['twoFreshAndRootThirdRequired'] and approval['exceptionDirectoryActualReadLedgerRequired'] and not approval['runtimeApproved'] and approval['xdataAndNeighborBodies']==0,'approval mismatch')
 require(request['request']['callerRecordedSHA256']==CALLER_SHA and request['boundaries']['oldCallerHasAsmAndRecordedCodeHashOnly'] and request['request']['expectedCallHexCalculatedNotOriginal']=='e87a30c100','upstream caller provenance mismatch')
 matches=[(r['index'],m) for r in records for m in r['methods'] if m['registeredAddress']==hex(ENTRY)]
 require(len(matches)==1 and matches[0][0]==70954,'method owner/address not unique')
 method=matches[0][1]; require((method['index'],method['name'],method['flags'],method['virtualSlot'],method['parameterCount'])==(METHOD,'CNLIBMNOALB','0x83',65535,1),'method declaration mismatch')
 sigs=[m for m in signatures['methods'] if m['index']==METHOD]; require(len(sigs)==1,'signature not unique'); sig=sigs[0]
 require(sig['declaringType']==70954 and sig['registeredAddress']==hex(ENTRY) and len(sig['parameters'])==1 and sig['parameters'][0]['typeIndex']==86325,'signature mismatch')
 ref=signatures['typeReferences']['86325']; require(ref['typeDefinition']==42005 and ref['kind']=='class' and not ref['byRef'],'reader mismatch')
 return {'method':method,'signature':sig,'readerType':ref,'returnType':'unknown','receiverRegister':'RCX','readerRegister':'RDX','oldCallerOriginalBytesAvailable':False}

def check_caller(data):
 require(len(data)==817 and digest(data)==CALLER_SHA,'complete caller SHA/size mismatch')
 for pc,expected in ((0x14aa99c2b,'4889f1'),(0x14aa99c2e,'4889c2'),(0x14aa99c31,'e87a30c100')):
  actual=data[pc-CALLER:pc-CALLER+len(bytes.fromhex(expected))]; require(actual.hex()==expected,'fresh caller setup/E8 mismatch')
  if pc==0x14aa99c31: require(pc+5+int.from_bytes(actual[1:],'little',signed=True)==ENTRY,'fresh E8 target mismatch')
 return {'size':len(data),'sha256':digest(data),'setup':{'0x14aa99c2b':'4889f1','0x14aa99c2e':'4889c2','0x14aa99c31':'e87a30c100'},'e8Evidence':'fresh caller bytes'}
def extent(table,entry,base,rva):
 require(len(table)%12==0,'unaligned pdata'); exact=[]; containing=[]
 for off in range(0,len(table),12):
  begin,end,unwind=struct.unpack_from('<III',table,off)
  row={'begin':hex(base+begin),'endExclusive':hex(base+end),'unwindRVA':hex(unwind),'recordRVA':hex(rva+off),'offset':off,'size':end-begin}
  if begin==entry-base: exact.append(row)
  if begin<=entry-base<end: containing.append(row)
 return {'exact':exact,'containing':containing}
def choose(diag):
 require(len(diag['exact'])==1 and diag['exact']==diag['containing'],'unique exact/containing pdata required'); return diag['exact'][0]
def bound(size): require(0<size<=2048,'extent exceeds 2048B bound')
def bind_slot(slot): require(len(slot)==8 and int.from_bytes(slot,'little')==ENTRY,'AOT pointer mismatch')
def decode(code):
 sys.path.insert(0,str(ROOT/'local/migration72/python'))
 import capstone
 from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
 cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64); cs.detail=True
 lines=[]; calls=[]; branches=[]; rip=[]; memory=[]; stack=[]
 for i in cs.disasm(code,ENTRY,count=512):
  line={'address':hex(i.address),'hex':bytes(i.bytes).hex(),'mnemonic':i.mnemonic,'operands':i.op_str}; lines.append(line)
  if i.mnemonic in ('push','pop','call','ret','leave','enter'): stack.append(line)
  if i.group(capstone.CS_GRP_JUMP): branches.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  if i.group(capstone.CS_GRP_CALL): calls.append(dict(line,target=hex(i.operands[0].imm) if i.operands and i.operands[0].type==X86_OP_IMM else None))
  for n,o in enumerate(i.operands):
   if o.type==X86_OP_MEM:
    memory.append(dict(line,operandIndex=n,access=o.access))
    if o.mem.base==X86_REG_RIP: rip.append(dict(line,operandIndex=n,target=hex(i.address+i.size+o.mem.disp)))
 require(0<len(lines)<=512,'instruction limit exceeds 512'); cursor=ENTRY
 for line in lines:
  b=bytes.fromhex(line['hex']); require(int(line['address'],16)==cursor and code[cursor-ENTRY:cursor-ENTRY+len(b)]==b,'decode gap or bytes differ'); cursor+=len(b)
 require(cursor==ENTRY+len(code),'decode incomplete or exceeds 512')
 return {'lines':lines,'calls':calls,'branches':branches,'ripReferences':rip,'memoryOperands':memory,'implicitStackInstructions':stack,'capstoneVersion':capstone.__version__}

def collect(dest):
 report={'task':'F-03d3s2/SCENE-MPLEVEL-PARSER-WINDOW','stage':'source-gate','sourceSHA256':digest(Path(__file__).read_bytes()),'sourcePins':PINS,'inputChecks':{},'nativeCPU':0,'liveReads':0,'xdataBytesRead':0,'neighborCalleeBytesRead':0,'newCodeWindows':0,'network':0,'targetProcessOperations':0,'builds':0,'database':0,'git':0}
 try:
  report['identity']=source_gate(); report['stage']='five-input-gate'
  client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip()); binary=None
  for name,(size,expected) in FIVE.items():
   raw=(client/name).read_bytes(); actual=digest(raw); report['inputChecks'][name]={'bytes':len(raw),'sha256':actual}
   require(len(raw)==size and actual==expected,'five-input drift: '+name)
   if name=='YuanShen.exe': binary=raw
  sys.path.insert(0,str(ROOT)); from tools.extract_client72 import PE,number
  pe=PE(binary); require(pe.base==0x140000000,'PE image base mismatch')
  def read(va,n,executable=False):
   off=pe.offset(va,n,executable=executable); return binary[off:off+n]
  report['stage']='caller-and-aot'; caller=read(CALLER,817,True); (dest/'caller.bin').write_bytes(caller); report['caller']=check_caller(caller)
  cell=read(CELL,8); (dest/'cell.bin').write_bytes(cell); pointer=int.from_bytes(cell,'little'); slot_address=pointer+METHOD*8
  slot=read(slot_address,8); (dest/'slot.bin').write_bytes(slot); bind_slot(slot)
  report['aot']={'cell':hex(CELL),'cellHex':cell.hex(),'table':hex(pointer),'method':METHOD,'slot':hex(slot_address),'slotHex':slot.hex()}
  report['stage']='pdata-before-body'; nt=number(binary,0x3c); rva,size=struct.unpack_from('<II',binary,nt+24+0x70+3*8)
  require((rva,size)==(0x1a4e6a40,14344080),'exception directory drift')
  table=read(pe.base+rva,size); table_hash=digest(table); require(table_hash=='5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d','exception directory SHA drift')
  diagnostic=extent(table,ENTRY,pe.base,rva)
  report['exceptionDirectory']={'rva':hex(rva),'bytes':size,'sha256':table_hash,'recordsScanned':len(table)//12,'readLedger':{'va':hex(pe.base+rva),'fileOffset':pe.offset(pe.base+rva,size),'requestedBytes':size,'actualBytes':len(table),'sha256':table_hash},'diagnostic':diagnostic}
  save(dest/'boundary.private.json',report); row=choose(diagnostic); pdata=table[row['offset']:row['offset']+12]; (dest/'pdata.bin').write_bytes(pdata)
  report.update(pdataHex=pdata.hex(),pdataSHA256=digest(pdata),entry=hex(ENTRY),endExclusive=row['endExclusive'],codeBytes=row['size']); save(dest/'pre-body.private.json',report)
  bound(row['size']); code=read(ENTRY,row['size'],True); (dest/'code.bin').write_bytes(code); decoded=decode(code); save(dest/'code.asm.private.json',decoded['lines'])
  report.update(stage='complete',passed=True,newCodeWindows=1,codeSHA256=digest(code),assemblySHA256=digest((dest/'code.asm.private.json').read_bytes()),instructions=len(decoded['lines']),calls=decoded['calls'],branches=decoded['branches'],ripReferences=decoded['ripReferences'],memoryOperands=decoded['memoryOperands'],implicitStackInstructions=decoded['implicitStackInstructions'],capstoneVersion=decoded['capstoneVersion'],methodSemanticsProven=False,parserExecuted=False,resourceReady=False)
  save(dest/'extraction.private.json',report); print(json.dumps({'codeBytes':len(code),'sha256':digest(code),'instructions':len(decoded['lines']),'calls':len(decoded['calls'])}))
 except Exception as e:
  report.update(passed=False,errorType=type(e).__name__,error=str(e)); save(dest/'failure.private.json',report); raise

def main():
 parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output',required=True); args=parser.parse_args(); dest=PRIVATE/args.output
 require(dest.name==args.output and not dest.exists(),'fresh task-local basename required'); dest.mkdir(); collect(dest)
if __name__=='__main__': main()
