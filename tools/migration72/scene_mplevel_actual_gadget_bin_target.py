"""Recover only the seven approved blocks containing one bound byte-array object."""
import argparse
import hashlib
import json
import os
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-target'
REQUEST='local/migration72/d02-resume-20261111b/scene/gadget-bin-target-bytes-request.private.json'
PLAN='local/migration72/d02-resume-20261111b/scene/gadget-bin-target-bytes-plan.zh.md'
INDEX='local/migration72/d02-resume-20261111b/scene-gadget-bin-index/main-third'
AUDIT='local/migration72/d02-resume-20261111b/scene-gadget-bin-index/audit-third.private.json'
META='local/migration72/d02-resume-20261111b/scene-gadget-bin-metadata-prefix/main-third'
HDR='local/migration72/d02-resume-20261111b/scene-gadget-bin-header/main-third'
PROFILE='protocol/cn-7.2/profile.json'
SNAP='local/migration72/d02-active/plain/initialization-archive-elevated6.json'

def require(ok,msg):
 if not ok:raise ValueError(msg)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def save(path,obj):
 with path.open('xb') as f:f.write((json.dumps(obj,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())
def verify(path,size,digest):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 require(n==size and h.hexdigest()==digest,'pin mismatch: '+path.name);return {'bytes':n,'sha256':h.hexdigest()}

def ranges_gate(rows,info):
 require([r['index'] for r in rows]==list(range(765,772)),'indices must be 765..771')
 for row in rows:
  i=row['index'];a=info['compressedEnds'][i-1];b=info['compressedEnds'][i]
  require(row=={'index':i,'rawBegin':info['dataOffset']+a,'rawEndExclusive':info['dataOffset']+b,'outputBegin':i*131072,'outputEndExclusive':(i+1)*131072,'availability':'unavailable'},'range mismatch at '+str(i))
 require(rows[0]['rawBegin']==25873585 and rows[-1]['rawEndExclusive']==26119089,'range endpoints mismatch')

def ingress():
 checked={}
 def pin(path,size,digest):
  if path in checked:
   require(checked[path]=={'bytes':size,'sha256':digest},'conflicting pin '+path);return
  checked[path]=verify(ROOT/path,size,digest)
 pin(PLAN,4473,'fc3e77e6f2cbca60a98300b0374dd06bd163fd5754b4ec168aeb1f838aa66335')
 pin(REQUEST,3607,'2cd56e1526dd675e25f4bee8ecd41b805281626790f16eaea0148acdbb3e2884')
 req=json.loads((ROOT/REQUEST).read_bytes());require(req['acceptedIndexThird'],'index third not accepted')
 pre_path=INDEX+'/pre-execute.private.json';p=req['pins'][pre_path];pin(pre_path,p['bytes'],p['sha256']);pre=json.loads((ROOT/pre_path).read_bytes())
 for group in (req['pins'],pre['pins']):
  for path,v in group.items():pin(path,v['bytes'],v['sha256'])
 audit=json.loads((ROOT/AUDIT).read_bytes());require(audit['passed'] and audit['freshFilesEqual'] and audit['currentNativeCalls']==0,'prior index audit mismatch')
 for name in ('index-object.bin','index.private.json'):
  v=audit['artifacts'][name];pin(INDEX+'/'+name,v['bytes'],v['sha256'])
 require(pre['stage']=='pre-execute' and pre['toolSHA256']==req['pins']['tools/migration72/scene_mplevel_actual_gadget_bin_index.py']['sha256'],'prior index epoch mismatch')
 override=os.environ.get('LIBUNICORN_PATH');require(not override or Path(override).resolve()==(ROOT/'local/migration72/python/unicorn/lib').resolve(),'unreviewed Unicorn override')
 profile_bytes=(ROOT/PROFILE).read_bytes();profile=json.loads(profile_bytes)['files'];stock={v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile.values()};require(stock==pre['stock'],'profile/stock mismatch')
 from tools.client72_resource_blob import parse_header
 decoded=(ROOT/HDR/'decoded-header.bin').read_bytes();info=parse_header(decoded,139134667,13709);header=json.loads((ROOT/HDR/'header.private.json').read_bytes())
 require(info==header['header']['parsed'],'header mismatch');ranges_gate(req['blocks'],info)
 d=json.loads((ROOT/META/'directory.private.json').read_bytes())
 from tools.migration72.scene_mplevel_actual_gadget_bin_index import parse_index,match_target,target_plan
 raw_index=(ROOT/INDEX/'index-object.bin').read_bytes();parsed=parse_index(raw_index)
 require(parsed==json.loads((ROOT/INDEX/'index.private.json').read_bytes()),'index report mismatch')
 entry,obj=match_target(parsed,d);target=json.loads((ROOT/INDEX/'target.private.json').read_bytes())
 require(entry==req['entry']==target['entry'] and obj==req['targetObject']==target['object'] and sha(raw_index[entry['start']:entry['endExclusive']])==req['entrySHA256']==target['entrySHA256'],'target association mismatch')
 require(target_plan(obj,info)==req['blocks']==target['targetBlocks'] and not target['byteArray']['headerAvailable'] and not target['byteArray']['objectFullyAvailable'],'target range mismatch')
 source=pre['physical'];require(source=={'bytes':139134667,'md5':'edc854321f0e9d8c64fd6e80426fe657','path':'YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/24230448.blk','sha256':'cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768'},'physical source mismatch')
 return checked,req,profile_bytes,profile,stock,source,decoded,info,pre['allowed']

def physical_prefix(path,source):
 h=hashlib.sha256();m=hashlib.md5();n=0;prefix=bytearray()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1048576),b''):
   if n<26119089:prefix.extend(chunk[:26119089-n])
   h.update(chunk);m.update(chunk);n+=len(chunk)
 require(n==source['bytes'] and h.hexdigest()==source['sha256'] and m.hexdigest()==source['md5'],'physical source drift')
 require(len(prefix)==26119089 and prefix[:13737]==(ROOT/HDR/'header-prefix.bin').read_bytes(),'header prefix mismatch')
 return bytes(prefix)

def extract(window,window_start,obj,entry):
 relative=obj['start']-window_start;end=obj['endExclusive']-window_start
 require(0<=relative<=end<=len(window) and end-relative==obj['bytes'],'object not fully available in actual window')
 raw=window[relative:end];require(len(raw)>=4,'array count truncated')
 count=struct.unpack_from('<I',raw)[0];require(count<=len(raw)-4 and count==entry['fileSize'],'array count/fileSize mismatch')
 payload=raw[4:4+count];tail=raw[4+count:]
 return raw,payload,tail,{'count':count,'countRawHex':raw[:4].hex(),'objectBytes':len(raw),'objectSHA256':sha(raw),
                         'payloadBytes':len(payload),'payloadSHA256':sha(payload),'tailBytes':len(tail),'tailSHA256':sha(tail),'tailHex':tail.hex(),'payloadFirst64Hex':payload[:64].hex()}

def collect(dest):
 result={'task':'F-03d3s2/GADGET-BIN-TARGET-BYTES','stage':'sources','nativePrefixExecuted':False,'independentLZ4Decoded':False,'nativePrefixCount':0,'independentLZ4Count':0,'targetNativeCodecExecuted':False,'fullPayloadDecoded':False,'reader42005Executed':False,'reader42005StartKnown':False,'MPLevelRecovered':False,'naturalLoaderReady':False,'SceneReady':False,'D02Ready':False,'toolSHA256':sha(Path(__file__).read_bytes())}
 try:
  pins,req,pb,profile,stock,source,decoded,info,allowed=ingress();result['pins']=pins
  game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
  result['stage']='stock';result['stock']={p:verify(game/p,v['bytes'],v['sha256']) for p,v in stock.items()}
  result['stage']='physical';prefix=physical_prefix(game/source['path'],source)
  from tools.client72_resource_provider import Snapshot,u64
  context=Snapshot(ROOT/SNAP,profile,sha(pb));pointer=u64(context.at('archiveDecodeTable.cell',context.base+0x5eed350,8));key=context.at('archiveDecodeTable.bytes',pointer,256)
  require(sha(key)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','archive table mismatch')
  from tools.migration72.scene_mplevel_actual_gadget_bin_metadata import make_probe
  from tools.client72_resource_payload import lz4_block
  binary=(game/'YuanShen.exe').read_bytes();probe=make_probe(binary,key)
  try:
   require([{'begin':hex(a),'endExclusive':hex(b)} for a,b in probe.engine.allowed]==allowed,'allowlist drift')
   result.update(stage='pre-execute',physical=source,sourcePrefix={'bytes':len(prefix),'sha256':sha(prefix)},blocks=req['blocks'],archiveTableSHA256=sha(key),allowed=allowed)
   save(dest/'pre-execute.private.json',result)
   blob={'parsed':info,'decodedHeaderHex':decoded.hex()};outputs=[];reports=[]
   for row in req['blocks']:
    i=row['index'];result['stage']='block-'+str(i);raw=prefix[row['rawBegin']:row['rawEndExclusive']];(dest/('block-%d.raw.bin'%i)).write_bytes(raw)
    transformed,_=probe.transform(blob,prefix,i);result['nativePrefixCount']+=1;result['nativePrefixExecuted']=True;(dest/('block-%d.transformed.bin'%i)).write_bytes(transformed)
    require(transformed[128:]==raw[128:],'tail changed');out=lz4_block(transformed,131072);result['independentLZ4Count']+=1;result['independentLZ4Decoded']=True;(dest/('block-%d.output.bin'%i)).write_bytes(out);outputs.append(out)
    reports.append({'index':i,'rawSHA256':sha(raw),'transformedSHA256':sha(transformed),'outputSHA256':sha(out),'changedPrefixBytes':sum(a!=b for a,b in zip(raw[:128],transformed[:128])),'tailSHA256':sha(raw[128:]),**probe.last})
    save(dest/('block-%d.proof.private.json'%i),reports[-1])
  finally:probe.close()
  window=b''.join(outputs);start=req['windowBegin'];require(len(window)==917504 and start+len(window)==req['windowEndExclusive'] and result['nativePrefixCount']==result['independentLZ4Count']==7,'window mismatch')
  (dest/'target-window.bin').write_bytes(window);result['stage']='object-and-count';raw,payload,tail,details=extract(window,start,req['targetObject'],req['entry'])
  (dest/'target-object.bin').write_bytes(raw);(dest/'payload.bin').write_bytes(payload);(dest/'tail.bin').write_bytes(tail)
  result.update(stage='complete',passed=True,blockReports=reports,window={'begin':start,'endExclusive':start+len(window),'bytes':len(window),'sha256':sha(window)},entry=req['entry'],entrySHA256=req['entrySHA256'],object=req['targetObject'],byteArray=details)
  save(dest/'result.private.json',result)
  print(json.dumps({'objectBytes':len(raw),'payloadBytes':len(payload),'payloadSHA256':sha(payload),'tailBytes':len(tail),'tailHex':tail.hex()}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def audit(first,second,output):
 names=['pre-execute.private.json','target-window.bin','target-object.bin','payload.bin','tail.bin','result.private.json']
 for i in range(765,772):names.extend('block-%d.%s'%(i,n) for n in ('raw.bin','transformed.bin','output.bin','proof.private.json'))
 copies=[{n:(OUT/dname/n).read_bytes() for n in names} for dname in (first,second)]
 require(copies[0]==copies[1],'fresh artifacts differ');raw=copies[0];del copies
 pins,req,pb,profile,stock,source,decoded,info,allowed=ingress();pre=json.loads(raw['pre-execute.private.json']);res=json.loads(raw['result.private.json'])
 require(pre['pins']==res['pins']==pins and pre['stock']==res['stock']==stock and pre['toolSHA256']==res['toolSHA256']==sha(Path(__file__).read_bytes()),'source proof mismatch')
 require(pre['stage']=='pre-execute' and pre['blocks']==req['blocks'] and pre['allowed']==allowed and res['stage']=='complete' and res['passed'],'stage/range mismatch')
 game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
 physical=physical_prefix(game/source['path'],source);require(pre['sourcePrefix']=={'bytes':len(physical),'sha256':sha(physical)},'physical prefix mismatch')
 from tools.client72_resource_payload import lz4_block
 outputs=[];reports=[]
 for row in req['blocks']:
  i=row['index'];block=raw['block-%d.raw.bin'%i];transformed=raw['block-%d.transformed.bin'%i];out=raw['block-%d.output.bin'%i];proof=json.loads(raw['block-%d.proof.private.json'%i])
  require(block==physical[row['rawBegin']:row['rawEndExclusive']] and transformed[128:]==block[128:],'raw block or tail mismatch')
  require(lz4_block(transformed,131072)==out and len(out)==131072,'block decode mismatch')
  require(proof['index']==i and proof['rawSHA256']==sha(block) and proof['transformedSHA256']==sha(transformed) and proof['outputSHA256']==sha(out) and proof['tailSHA256']==sha(block[128:]) and proof['changedPrefixBytes']==sum(a!=b for a,b in zip(block[:128],transformed[:128])),'block proof mismatch')
  require(proof['al']==1 and proof['guardsIntact'] and proof['tailUnchanged'] and proof['protectedWriteEvents']==0 and proof['headerStorageProtectedBytes']==13725 and not proof['run']['modeledCalls'] and not proof['run']['memoryModels'] and proof['run']['error'] is None and proof['run']['failure'] is None and proof['run']['stop']=='0x620000000','prefix protection evidence mismatch')
  outputs.append(out);reports.append(proof)
 window=b''.join(outputs);start=req['windowBegin'];require(window==raw['target-window.bin'] and len(window)==917504 and res['window']=={'begin':start,'endExclusive':start+len(window),'bytes':len(window),'sha256':sha(window)},'window mismatch')
 obj,payload,tail,details=extract(window,start,req['targetObject'],req['entry'])
 require(obj==raw['target-object.bin'] and payload==raw['payload.bin'] and tail==raw['tail.bin'] and details==res['byteArray'] and res['object']==req['targetObject'] and res['entry']==req['entry'] and res['entrySHA256']==req['entrySHA256'],'target bytes mismatch')
 require(res['blockReports']==reports and res['nativePrefixExecuted'] and res['independentLZ4Decoded'] and res['nativePrefixCount']==res['independentLZ4Count']==7 and not any(res[k] for k in ('targetNativeCodecExecuted','fullPayloadDecoded','reader42005Executed','reader42005StartKnown','MPLevelRecovered','naturalLoaderReady','SceneReady','D02Ready')),'scope mismatch')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'currentNativeCalls':0,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in raw.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required');OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
