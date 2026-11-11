"""Collect one approved ordinary scalar setter source window, offline."""
import argparse
import hashlib
import io
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111b/proud-gate/scalar-setter-window'
REQUEST='local/migration72/d02-resume-20261111b/proud-gate/row-bridge/native-scalar-setter-window-request.private.json'
REQUEST_SHA='611633a8a950d29bda6954b20a6aec0ffc2aaac027231da3092087439e83bc72'
ENTRY,END,CALL,SETUP,CALLER=0x14aefa090,0x14aefa12d,0x149f0d15d,0x149f0d147,0x149f0c1f0
CELL=0x1422f9950
CALL_BYTES=bytes.fromhex('e82ecffe00')
SETUP_BYTES=bytes.fromhex('baf0b5fdf633541820890fc745c000000000488d4dc0e82ecffe00')
EXTRA={'tools/game_path.ps1':(3506,'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b'),
 'protocol/cn-7.2/profile.json':(1453,'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'),
 'local/migration72/python/capstone/__init__.py':(43853,'94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f'),
 'local/migration72/python/capstone/x86.py':(2816,'08cbb0a02ff152bcf1fbd7c4f8850b3ade38c25b9f0a6a9b900a55ee75e87c67'),
 'local/migration72/python/capstone/x86_const.py':(45756,'83cf4550d9256f5f9b8e218b9b732e46d83ef602b463a01d6eeb778deb28e040'),
 'local/migration72/python/capstone/lib/capstone.dll':(None,'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3')}
FILES=('boundary.private.json','aot-pointer-cell.bin','aot-slot.bin','pdata.bin','caller-site.bin','caller-setup.bin','pre-body.private.json','code.bin','code.asm.private.json','extraction.private.json')


def require(ok,message):
 if not ok:raise ValueError(message)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def check(path,size,digest):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 require((size is None or n==size) and h.hexdigest()==digest,'pin mismatch: '+path.name)
 return {'bytes':n,'sha256':h.hexdigest()}
def save(path,obj):
 with path.open('xb') as f:f.write((json.dumps(obj,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())

class PureData(pickle.Unpickler):
 def find_class(self,module,name):raise ValueError('pickle globals forbidden')

def check_call(raw):
 require(raw==CALL_BYTES and CALL+5+struct.unpack('<i',raw[1:])[0]==ENTRY,'caller E8 mismatch')

def source_gate():
 check(ROOT/REQUEST,19810,REQUEST_SHA);req=json.loads((ROOT/REQUEST).read_bytes())
 sources={v['path']:(v['bytes'],v['sha256']) for v in req['sources'].values()};sources.update(EXTRA)
 pins={p:check(ROOT/p,*v) for p,v in sources.items()}
 profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())['files']
 require({v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile.values()}==req['fixedClientInputs'],'profile stock mismatch')
 old=(ROOT/req['sources']['parserRaw']['path']).read_bytes()
 require(old[SETUP-CALLER:SETUP-CALLER+27]==SETUP_BYTES and old[CALL-CALLER:CALL-CALLER+5]==CALL_BYTES,'frozen caller bytes mismatch');check_call(CALL_BYTES)
 asm=json.loads((ROOT/req['sources']['parserAsm']['path']).read_bytes());selected=[x for x in asm if SETUP<=int(x['address'],16)<CALL+5]
 cursor=SETUP
 for row in selected:
  b=bytes.fromhex(row['hex']);require(int(row['address'],16)==cursor and old[cursor-CALLER:cursor-CALLER+len(b)]==b,'saved caller boundary mismatch');cursor+=len(b)
 require(cursor==CALL+5 and selected[-1]['address']==hex(CALL) and selected[-1]['mnemonic']=='call' and selected[-1]['operands']==hex(ENTRY),'saved direct call mismatch')
 with (ROOT/req['sources']['metadata']['path']).open('rb') as f:records,_=PureData(f).load()
 signatures=json.loads((ROOT/req['sources']['signatures']['path']).read_bytes())
 binding(records,signatures,req['target'])
 mapping=json.loads((ROOT/req['sources']['mapping']['path']).read_bytes())
 require(mapping['inputSHA256']==req['fixedClientInputsInheritedFromVerifiedProducer']=={p:v['sha256'] for p,v in req['fixedClientInputs'].items()},'inherited stock mismatch')
 require(req['target']['entry']==hex(ENTRY) and req['target']['expectedBytes']==157 and req['target']['expectedEndExclusiveFromVerifiedManifest']==hex(END) and req['budget']['maxCodeBytes']==256 and req['budget']['maxDecodedInstructions']==128,'target budget mismatch')
 return req,pins

def binding(records,signatures,target):
 matches=[(owner['index'],m) for owner in records for m in owner['methods'] if m['index']==483726]
 require(len(matches)==1 and matches[0][0]==58209 and matches[0][1]==target['declaration'],'metadata method binding mismatch')
 methods=[m for m in signatures['methods'] if m['index']==483726]
 require(len(methods)==1 and methods[0]==target['signature'] and [signatures['typeReferences'][str(p['typeIndex'])] for p in methods[0]['parameters']]==target['resolvedParameters'],'signature binding mismatch')


class FilePE:
 def __init__(self,f):
  self.f=f;h=self.read(0,4096);nt=struct.unpack_from('<I',h,0x3c)[0]
  require(h[:2]==b'MZ' and h[nt:nt+4]==b'PE\0\0' and struct.unpack_from('<H',h,nt+4)[0]==0x8664 and struct.unpack_from('<H',h,nt+24)[0]==0x20b,'PE mismatch')
  self.base=struct.unpack_from('<Q',h,nt+48)[0];require(self.base==0x140000000,'image base mismatch')
  count=struct.unpack_from('<H',h,nt+6)[0];opt=struct.unpack_from('<H',h,nt+20)[0];table=self.read(nt+24+opt,count*40);self.sections=[]
  for i in range(count):
   off=i*40;rva,size,raw=struct.unpack_from('<III',table,off+12);flags=struct.unpack_from('<I',table,off+36)[0]
   self.sections.append({'begin':self.base+rva,'end':self.base+rva+size,'raw':raw,'flags':flags})
  self.pdata_rva,self.pdata_size=struct.unpack_from('<II',h,nt+24+0x70+3*8)
 def read(self,offset,size):
  self.f.seek(offset);raw=self.f.read(size);require(len(raw)==size,'short file read');return raw
 def section(self,va,size,executable=False):
  items=[x for x in self.sections if x['begin']<=va and va+size<=x['end'] and (not executable or x['flags']&0x20000000)]
  require(len(items)==1,'section missing or ambiguous');return items[0]
 def at(self,va,size,executable=False):
  sec=self.section(va,size,executable);return self.read(sec['raw']+va-sec['begin'],size)

def extent(table,base,rva):
 require(len(table)%12==0,'unaligned pdata');exact=[];containing=[]
 for index,(begin,end,unwind) in enumerate(struct.iter_unpack('<III',table)):
  if begin==ENTRY-base or begin<=ENTRY-base<end:
   row={'begin':hex(base+begin),'endExclusive':hex(base+end),'size':end-begin,'unwindRVA':hex(unwind),'recordRVA':hex(rva+index*12),'recordHex':table[index*12:index*12+12].hex()}
   if begin==ENTRY-base:exact.append(row)
   if begin<=ENTRY-base<end:containing.append(row)
 return {'exact':exact,'containing':containing}

def choose(diag):
 require(len(diag['exact'])==1 and diag['exact']==diag['containing'],'pdata missing or ambiguous')
 row=diag['exact'][0];require(row['begin']==hex(ENTRY) and row['endExclusive']==hex(END) and row['size']==157 and row['size']<=256,'body extent mismatch')
 return row

def decode(code):
 sys.path.insert(0,str(ROOT/'local/migration72/python'))
 import capstone
 from capstone.x86_const import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
 require(Path(capstone._cs._name).resolve()==(ROOT/'local/migration72/python/capstone/lib/capstone.dll').resolve(),'Capstone library mismatch')
 cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64);cs.detail=True;rows=[];cursor=ENTRY
 for ins in cs.disasm(code,ENTRY):
  raw=bytes(ins.bytes);require(ins.address==cursor and raw==code[cursor-ENTRY:cursor-ENTRY+len(raw)],'decode gap/overlap')
  call=ins.group(capstone.CS_GRP_CALL);branch=ins.group(capstone.CS_GRP_JUMP)
  direct=hex(ins.operands[0].imm) if (call or branch) and ins.operands and ins.operands[0].type==X86_OP_IMM else None
  rip=[hex(ins.address+ins.size+op.mem.disp) for op in ins.operands if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP]
  rows.append({'address':hex(ins.address),'size':ins.size,'hex':raw.hex(),'mnemonic':ins.mnemonic,'operands':ins.op_str,'call':call,'branch':branch,'directTarget':direct,'ripTargets':rip})
  cursor+=ins.size;require(len(rows)<=128,'instruction budget exceeded')
 require(cursor==ENTRY+len(code),'incomplete decode');return rows

def collect(dest):
 result={'task':'E-01d1/R3-PROUD-ROW-BRIDGE/NATIVE-SCALAR-SETTER-SOURCE','stage':'source-gate','toolSHA256':sha(Path(__file__).read_bytes()),'requestSHA256':REQUEST_SHA,'nativeCPU':0,'live':0,'calleeBodies':0,'xdataBytes':0,'globalContentBytes':0,'newBodies':0,'actualEncodedValueObserved':False,'actualProudRowConstructed':False,'resourceReady':False,'d02Complete':False}
 try:
  req,result['pins']=source_gate()
  result.update({name:False for name in req['remainFalse']})
  game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
  result['stage']='five-stock';result['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in req['fixedClientInputs'].items()}
  with (game/'YuanShen.exe').open('rb') as f:
   pe=FilePE(f);result['stage']='caller'
   site=pe.at(CALL,5,True);setup=pe.at(SETUP,27,True);check_call(site);require(setup==SETUP_BYTES and setup[-5:]==site,'fresh caller setup mismatch')
   (dest/'caller-site.bin').write_bytes(site);(dest/'caller-setup.bin').write_bytes(setup)
   result['caller']={'site':hex(CALL),'siteHex':site.hex(),'setupBegin':hex(SETUP),'setupBytes':len(setup),'setupSHA256':sha(setup),'target':hex(ENTRY)}
   result['stage']='ordinary-aot';cell=pe.at(CELL,8);pointer=int.from_bytes(cell,'little');slot_va=pointer+483726*8;slot=pe.at(slot_va,8)
   require(slot.hex()==req['aot']['expectedSlotRaw'] and int.from_bytes(slot,'little')==ENTRY,'ordinary AOT slot mismatch')
   (dest/'aot-pointer-cell.bin').write_bytes(cell);(dest/'aot-slot.bin').write_bytes(slot)
   result['aot']={'pointerCell':hex(CELL),'pointerCellHex':cell.hex(),'pointer':hex(pointer),'methodIndex':483726,'slot':hex(slot_va),'slotHex':slot.hex(),'target':hex(ENTRY)}
   result['stage']='pdata-before-body';table=pe.at(pe.base+pe.pdata_rva,pe.pdata_size)
   result['exceptionDirectory']={'rva':hex(pe.pdata_rva),'bytesScanned':pe.pdata_size,'diagnostic':extent(table,pe.base,pe.pdata_rva)}
   save(dest/'boundary.private.json',result);row=choose(result['exceptionDirectory']['diagnostic']);sec=pe.section(ENTRY,row['size'],True)
   pdata=bytes.fromhex(row['recordHex']);(dest/'pdata.bin').write_bytes(pdata)
   result.update(selected=row,executableSection={'begin':hex(sec['begin']),'endExclusive':hex(sec['end']),'flags':hex(sec['flags'])},budget={'maxBytes':256,'expectedBytes':157,'maxInstructions':128,'xdataBytes':0})
   save(dest/'pre-body.private.json',result)
   result['stage']='body';code=pe.at(ENTRY,row['size'],True);(dest/'code.bin').write_bytes(code);result['newBodies']=1
   require(sha(code)==req['target']['expectedCodeSHA256FromVerifiedManifest'],'body SHA mismatch')
   rows=decode(code);save(dest/'code.asm.private.json',rows)
   result.update(stage='complete',passed=True,body={'begin':hex(ENTRY),'endExclusive':hex(END),'bytes':len(code),'sha256':sha(code),'instructions':len(rows)},assemblySHA256=sha((dest/'code.asm.private.json').read_bytes()),calls=[x for x in rows if x['call']],branches=[x for x in rows if x['branch']],ripReferences=[x for x in rows if x['ripTargets']],semantics='source only; root review pending')
   save(dest/'extraction.private.json',result)
   print(json.dumps({'bytes':len(code),'sha256':sha(code),'instructions':len(rows),'calls':len(result['calls']),'branches':len(result['branches'])}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def audit(first,second,output):
 copies=[{n:(OUT/d/n).read_bytes() for n in FILES} for d in (first,second)];require(copies[0]==copies[1],'fresh artifacts differ');raw=copies[0]
 req,pins=source_gate();boundary,pre,result=(json.loads(raw[n]) for n in ('boundary.private.json','pre-body.private.json','extraction.private.json'))
 require(boundary['pins']==pre['pins']==result['pins']==pins and boundary['stock']==pre['stock']==result['stock']==req['fixedClientInputs'],'source proof mismatch')
 require(boundary['stage']==pre['stage']=='pdata-before-body' and result['stage']=='complete' and result['passed'] and result['toolSHA256']==sha(Path(__file__).read_bytes()),'stage/tool mismatch')
 require(boundary['exceptionDirectory']==pre['exceptionDirectory']==result['exceptionDirectory'],'pdata proof mismatch');row=choose(result['exceptionDirectory']['diagnostic'])
 begin,end,unwind=struct.unpack('<III',raw['pdata.bin']);require(begin+0x140000000==ENTRY and end+0x140000000==END and hex(unwind)==row['unwindRVA'] and raw['pdata.bin'].hex()==row['recordHex'],'pdata raw mismatch')
 check_call(raw['caller-site.bin']);require(raw['caller-setup.bin']==SETUP_BYTES and raw['caller-setup.bin'][-5:]==raw['caller-site.bin'],'caller mismatch')
 pointer=int.from_bytes(raw['aot-pointer-cell.bin'],'little');slot=pointer+483726*8
 require(result['aot']['pointer']==hex(pointer) and result['aot']['slot']==hex(slot) and int.from_bytes(raw['aot-slot.bin'],'little')==ENTRY and raw['aot-slot.bin'].hex()==req['aot']['expectedSlotRaw'],'aot mismatch')
 code=raw['code.bin'];rows=decode(code)
 require(len(code)==157 and sha(code)==req['target']['expectedCodeSHA256FromVerifiedManifest']==result['body']['sha256'] and rows==json.loads(raw['code.asm.private.json']) and sha(raw['code.asm.private.json'])==result['assemblySHA256'],'body/assembly mismatch')
 require(result['calls']==[x for x in rows if x['call']] and result['branches']==[x for x in rows if x['branch']] and result['ripReferences']==[x for x in rows if x['ripTargets']],'edge mismatch')
 require(result['newBodies']==1 and not any(result[k] for k in ('nativeCPU','live','calleeBodies','xdataBytes','globalContentBytes',*req['remainFalse'])),'scope mismatch')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'currentPEReads':0,'currentNativeCalls':0,'sameDecoderUsed':True,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in raw.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required');OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
