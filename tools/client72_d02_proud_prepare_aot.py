"""Bounded 5071 tuple AOT pointer identity join; no target code or execution."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'local/migration72/d02-resume-20261110b/c-proud-prepare-aot';sys.path.insert(0,str(ROOT))
ENTRY=0x157ffc650
PINS={
'local/migration72/d02-resume-20261110b/c-proud-lazy-index-contract/next-input-spec.zh.md':'ef4109fa7efc3ffa2a284302ed55d494423f33fa3ed017fcf6c524603ee1f62a',
'local/migration72/d02-resume-20261110b/c-proud-lazy-index-contract/frozen-inputs.private.json':'bc1b0342094786ed7cb79e3f5af71261fb3c248a6f97c41e6f79c2bae3269b1a',
'local/migration72/d02-resume-20261110b/c-proud-lookup-window/final3/code.bin':'479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e',
'local/migration72/d02-resume-20261110b/c-proud-lookup-window/final4/code.bin':'479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e',
'local/migration72/d02-resume-20261110b/c-proud-lookup-window/main-third.private.json':'9e48e1dfb3bc27073e04934572dc21dc57b124ba7d7bed74cbf00701c6376f6c',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/final3.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/final4.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261110b/c-proud-index-aot/main-third.private.json':'ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f',
'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl':'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
'local/migration72/evidence/client72-signatures.json':'28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
'local/migration72/d02-resume-20261110b/c-proud-actual-fields/collect_closed_aot.private.py':'a43824fab5674d69d85a04bdb2a348cb0634ba8f32a78cf49a022f2e916144aa',
'tools/client72_d02_proud_index_aot.py':'43733eda2878637cf5506eb6aa9ce785a00c30fdbc0a28cfc9a30fd94043dc98',
'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
'tools/game_path.ps1':'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
'protocol/cn-7.2/profile.json':'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'}
FIVE={'YuanShen.exe':(450927000,'8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5'),
'YuanShen_Data/Managed/Metadata/global-metadata.dat':(83644188,'45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a'),
'YuanShen_Data/Managed/Metadata/startup-metadata.dat':(4116148,'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105'),
'YuanShen_Data/Plugins/AccountPlatNative.dll':(9360280,'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'),
'YuanShen_Data/Plugins/Astrolabe.dll':(2614168,'87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442')}
HEAD=[('specBase',0x142806bf0,4,'5fb55063'),('specSize',0x142806aa4,4,'c9d15132'),('tableBase',0x142806b6c,4,'2cbf7e3f'),('tableSize',0x142806b90,4,'7946a05c'),('pointerCount',0x1422f9944,4,'7f0d5e61'),('pointerBase',0x1422f9910,8,'b00c474201000000')]
SPEC_SHA='94c576e22b94971933613fef1855ac9223b505e74e2c78974d370e0109df57c8';TABLE_SHA='1af68b22c3f8f66ac55e64440f9a83d0a4aabd112bb587c427fbd0442c707b96'
def sha(b):return hashlib.sha256(b).hexdigest()
def require(c,msg):
 if not c:raise ValueError(msg)
def save(path,obj):
 with path.open('xb') as f:f.write((json.dumps(obj,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf8'))

def sources():
 fixed={p:(ROOT/p).read_bytes() for p in PINS}
 for p,b in fixed.items(): require(sha(b)==PINS[p],'source drift: '+p)
 ca=fixed['local/migration72/d02-resume-20261110b/c-proud-lookup-window/final3/code.bin'];cb=fixed['local/migration72/d02-resume-20261110b/c-proud-lookup-window/final4/code.bin']
 require(ca==cb and len(ca)==236 and ca[0x13:0x18].hex()=='e878010000' and 0x157ffc4d8+int.from_bytes(ca[0x14:0x18],'little',signed=True)==ENTRY,'caller direct E8 differs')
 aots=[json.loads(fixed[p]) for p in PINS if '/c-proud-index-aot/' in p]
 require(len(aots)==3 and aots[0]==aots[1]==aots[2],'old AOT reports differ')
 a=aots[0]; require(a['identityUnique'] and a['partial'] and not a['crossPointerIndexSameCodeComplete'] and len(a['allTupleRows'])==16 and a['signatureCandidates']==[138527],'old AOT scope differs')
 records,_=pickle.loads(fixed['local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl']); sig=json.loads(fixed['local/migration72/evidence/client72-signatures.json'])
 field=next(f for f in records[80063]['fields'] if f['index']==400190); t=field['type']
 require(field==a['field'] and (t['typeDefinition'],t['instanceIndex'],[x.get('typeDefinition',x['kind']) for x in t['arguments']])==(18192,5071,['uint32',75077,47647,25030]),'old field tuple differs')
 methods={m['index']:(r['index'],m) for r in records for m in r['methods']}; signatures={m['index']:m for m in sig['methods']}
 return a,methods,signatures,sig['typeReferences']

def extract_rows(table,specs,methods,selected_indices=None):
 selected=[];shared=[]
 for i in range(len(table)//14):
  ptr,sp=struct.unpack_from('<ii',table,i*14)
  require(0<=ptr<469946 and 0<=sp<len(specs)//12,'generic table reference out of bounds')
  method,cls,inst=struct.unpack_from('<iii',specs,sp*12);owner,decl=methods[method]
  row={'tableIndex':i,'pointerIndex':ptr,'methodSpec':sp,'methodDefinition':method,'method':decl['name'],'classInstance':cls,'methodInstance':inst,'tableRecord14':table[i*14:i*14+14].hex(),'specRecord12':specs[sp*12:sp*12+12].hex()}
  if owner==18192 and cls==5071:selected.append(row)
  if selected_indices is not None and ptr in selected_indices:shared.append(dict(row,owner=owner))
 return selected,shared

def ensure_selection(rows,old):
 require(rows==old,'tuple rows differ from fixed old report')
 ids={r['pointerIndex'] for r in rows}
 require(len(rows)==16 and len(ids)==16 and 84537 in ids,'tuple pointers not unique or >16')
 return ids

def target_matches(rows,pointers):
 return [r for r in rows if pointers[str(r['pointerIndex'])]['address']==hex(ENTRY)]

def attach_rows(rows,pointers,methods,sigs,types):
 result=[]
 for row in rows:
  m=row['methodDefinition']; params=sigs.get(m,{}).get('parameters',[])
  result.append(dict(row,declaration=methods[m][1],signature=sigs.get(m),parameterTypes=[types[str(p['typeIndex'])] for p in params],pointer=pointers[str(row['pointerIndex'])]))
 return result

def collect(dest):
 report={'task':'E-01/R3-PROUD-PREPARE-AOT-D','stage':'source-gate','sourceSHA256':sha(Path(__file__).read_bytes()),'sourcePins':PINS,'inputChecks':{},'pointerQWORDCount':0,'newCodeBytes':0,'pdataBytes':0,'nativeCPU':0,'liveReads':0}
 try:
  old,methods,sigs,types=sources();report['stage']='five-input-gate'
  client=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip());data={}
  for name,(size,expected) in FIVE.items():
   raw=(client/name).read_bytes();report['inputChecks'][name]={'bytes':len(raw),'sha256':sha(raw)}
   require(len(raw)==size and sha(raw)==expected,'five input drift: '+name)
   if name=='YuanShen.exe' or name.endswith('global-metadata.dat'):data[name]=raw
  from tools.extract_client72 import PE
  binary=data['YuanShen.exe'];metadata=data['YuanShen_Data/Managed/Metadata/global-metadata.dat'];pe=PE(binary);values={};header=[]
  for name,va,width,expected in HEAD:
   pos=pe.offset(va,width);raw=binary[pos:pos+width];require(raw.hex()==expected,'header drift: '+name);values[name]=int.from_bytes(raw,'little');header.append({'name':name,'address':hex(va),'hex':raw.hex()})
  sb=0x210+(values['specBase']^0x6347382b);ss=values['specSize']^0x323bae70;tb=0x210+(values['tableBase']^0x3cf0720c);ts=(values['tableSize']+0xa3d9cc8b)&0xffffffff;pc=(values['pointerCount']+0x9ea91e3b)&0xffffffff;pb=values['pointerBase']
  require((sb,ss,tb,ts,pc,pb)==(0x178f84,6979513,0x38ecf30,8000260,469946,0x142470cb0),'decoded header differs')
  specs=metadata[sb:sb+ss];table=metadata[tb:tb+ts];require(sha(specs)==SPEC_SHA and sha(table)==TABLE_SHA,'table drift')
  rows,_=extract_rows(table,specs,methods);ids=ensure_selection(rows,old['allTupleRows']);_,shared=extract_rows(table,specs,methods,ids)
  report.update(stage='pointer-gate',header=header,headerBytes=28,specSHA256=sha(specs),genericTableSHA256=sha(table),selectedRows=rows,sharedRows=shared)
  pointers={}
  for ptr in sorted(ids):
   slot=pb+ptr*8;pos=pe.offset(slot,8);raw=binary[pos:pos+8]
   pointers[str(ptr)]={'slot':hex(slot),'qword':raw.hex(),'address':hex(int.from_bytes(raw,'little'))};report['pointerQWORDCount']=len(pointers)
  require(pointers['84537']==old['pointers']['84537'],'old 84537 pointer drift')
  matched=target_matches(rows,pointers)
  result=attach_rows(rows,pointers,methods,sigs,types)
  report.update(stage='complete',passed=True,selectedWithDeclarations=result,pointers=pointers,targetMatches=matched,
   tupleIdentityUnique=len(matched)==1,globalSameCodeComplete=False,partial=len(matched)!=1,pointerBytesRead=128,newPointerQWORDs=15,
   methodSemanticsProven=False,loaderExecuted=False,resourceReady=False)
  (dest/'spec.bin').write_bytes(specs);(dest/'generic.bin').write_bytes(table);save(dest/'report.private.json',report)
  print(json.dumps({'bytes':(dest/'report.private.json').stat().st_size,'sha256':sha((dest/'report.private.json').read_bytes()),'targetMatches':matched},ensure_ascii=False))
 except Exception as e:
  report.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',report);raise

def audit(a,b,out):
 old,methods,sigs,types=sources();files=('spec.bin','generic.bin','report.private.json');x={n:(a/n).read_bytes() for n in files};y={n:(b/n).read_bytes() for n in files};require(x==y,'fresh data differ')
 report=json.loads(x['report.private.json']);require(sha(x['spec.bin'])==SPEC_SHA and sha(x['generic.bin'])==TABLE_SHA,'table sources differ')
 rows,_=extract_rows(x['generic.bin'],x['spec.bin'],methods);ids=ensure_selection(rows,old['allTupleRows']);_,shared=extract_rows(x['generic.bin'],x['spec.bin'],methods,ids)
 require(rows==report['selectedRows'] and shared==report['sharedRows'],'table join differs')
 pointers=report['pointers'];require(set(map(int,pointers))==ids and len(pointers)==16 and pointers['84537']==old['pointers']['84537'],'pointer set differs')
 for ptr,v in pointers.items(): require(v['slot']==hex(0x142470cb0+int(ptr)*8) and len(bytes.fromhex(v['qword']))==8 and v['address']==hex(int.from_bytes(bytes.fromhex(v['qword']),'little')),'pointer record differs')
 matches=target_matches(rows,pointers)
 require(matches==report['targetMatches'] and report['selectedWithDeclarations']==attach_rows(rows,pointers,methods,sigs,types),'declaration or match differs')
 require(report['header']==[{'name':n,'address':hex(va),'hex':raw} for n,va,width,raw in HEAD] and report['headerBytes']==28 and report['specSHA256']==SPEC_SHA and report['genericTableSHA256']==TABLE_SHA,'header or table report differs')
 require(report['sourcePins']==PINS and report['sourceSHA256']==sha(Path(__file__).read_bytes()) and report['inputChecks']=={n:{'bytes':s,'sha256':h} for n,(s,h) in FIVE.items()},'provenance differs')
 require(report['pointerQWORDCount']==16 and report['pointerBytesRead']==128 and report['newPointerQWORDs']==15 and report['partial']==(len(matches)!=1) and not any(report[k] for k in ('newCodeBytes','pdataBytes','nativeCPU','liveReads','globalSameCodeComplete','methodSemanticsProven','loaderExecuted','resourceReady')),'scope differs')
 result={'task':report['task'],'passed':True,'freshReportsSHA256':sha(x['report.private.json']),'freshTablesEqual':True,'targetMatches':matches,'selectedWithDeclarations':report['selectedWithDeclarations'],'sharedRows':shared,'pointers':pointers,'globalSameCodeComplete':False,'methodSemanticsProven':False,'currentPEReads':0}
 save(out,result);print(json.dumps({'bytes':out.stat().st_size,'sha256':sha(out.read_bytes()),'targetMatches':matches},ensure_ascii=False))

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args();dest=OUT/a.output
 require(dest.name==a.output and not dest.exists(),'new task-local basename required')
 if a.audit:
  require(dest.suffix=='.json' and all((OUT/n).name==n for n in a.audit),'audit requires task-local directories and JSON');audit(*(OUT/n for n in a.audit),dest)
 else:dest.mkdir();collect(dest)
if __name__=='__main__':main()
