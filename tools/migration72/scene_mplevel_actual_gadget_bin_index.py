"""Recover only the fixed gadget asset-index blocks and bind one exact entry."""
import argparse
import hashlib
import json
import os
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-index'
REQUEST='local/migration72/d02-resume-20261111b/scene/gadget-bin-index-request.private.json'
PLAN='local/migration72/d02-resume-20261111b/scene/gadget-bin-index-plan.zh.md'
META='local/migration72/d02-resume-20261111b/scene-gadget-bin-metadata-prefix/main-third'
HDR='local/migration72/d02-resume-20261111b/scene-gadget-bin-header/main-third'
PROFILE='protocol/cn-7.2/profile.json'
SNAP='local/migration72/d02-active/plain/initialization-archive-elevated6.json'
EXPECTED_TYPE='0451de58691ec5b6e08bba3d9b5b85fa'

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
 require([r['index'] for r in rows]==list(range(43,87)),'indices must be 43..86')
 for row in rows:
  i=row['index'];a=info['compressedEnds'][i-1];b=info['compressedEnds'][i]
  require(row=={'index':i,'rawBegin':info['dataOffset']+a,'rawEndExclusive':info['dataOffset']+b,
   'outputBegin':i*131072,'outputEndExclusive':(i+1)*131072,'alreadyAvailable':False},'range mismatch at '+str(i))
 require(rows[0]['rawBegin']==2238253 and rows[-1]['rawEndExclusive']==4560114,'range endpoints mismatch')

def ingress():
 checked={}
 def pin(path,size,digest):
  if path in checked:
   require(checked[path]=={'bytes':size,'sha256':digest},'conflicting pin '+path);return
  checked[path]=verify(ROOT/path,size,digest)
 pin(PLAN,5543,'0cafc7c29e336b2711e5b832d07592da75d38aee9507cd785ad85e7f558e1cdc')
 pin(REQUEST,10916,'f6cc436b4a96b0429c25a446d4ddb6640444a354f4e8975ff8c95bfef2d2f843')
 req=json.loads((ROOT/REQUEST).read_bytes());require(req['acceptedMetadataThird'] and req['targetEntryName']=='44935541','request binding mismatch')
 prep=META+'/pre-execute.private.json';pv=req['pins'][prep];pin(prep,pv['bytes'],pv['sha256'])
 pre=json.loads((ROOT/prep).read_bytes())
 for group in (req['pins'],pre['pins']):
  for path,v in group.items():pin(path,v['bytes'],v['sha256'])
 require(pre['stage']=='pre-execute' and pre['toolSHA256']==req['pins']['tools/migration72/scene_mplevel_actual_gadget_bin_metadata.py']['sha256'],'metadata epoch mismatch')
 override=os.environ.get('LIBUNICORN_PATH');require(not override or Path(override).resolve()==(ROOT/'local/migration72/python/unicorn/lib').resolve(),'unreviewed Unicorn library override')
 profile_bytes=(ROOT/PROFILE).read_bytes();profile=json.loads(profile_bytes)['files'];stock={v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile.values()}
 require(stock==pre['stock'],'stock profile mismatch')
 from tools.client72_resource_blob import parse_header
 decoded=(ROOT/HDR/'decoded-header.bin').read_bytes();info=parse_header(decoded,139134667,13709)
 header=json.loads((ROOT/HDR/'header.private.json').read_bytes());require(info==header['header']['parsed'],'header mismatch')
 ranges_gate(req['blocks'],info)
 d=json.loads((ROOT/META/'directory.private.json').read_bytes());plans=json.loads((ROOT/META/'index-plan.private.json').read_bytes())
 require(len(plans)==1 and plans[0]['object']==req['indexObject'] and plans[0]['blocks']==req['blocks'] and d['indexPlans']==plans,'accepted index plan mismatch')
 require((d['objectCount'],d['metadataEnd'],d['declaredFullFileBytes'])==(176152,3523165,445421180),'metadata facts mismatch')
 source=pre['physical'];require(source=={'bytes':139134667,'md5':'edc854321f0e9d8c64fd6e80426fe657','path':'YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/24230448.blk','sha256':'cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768'},'physical source mismatch')
 return checked,req,profile_bytes,profile,stock,source,decoded,info,d,pre['allowed']

def physical_prefix(path,source):
 h=hashlib.sha256();m=hashlib.md5();n=0;prefix=b''
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1048576),b''):
   if n<4560114:prefix+=chunk[:4560114-n]
   h.update(chunk);m.update(chunk);n+=len(chunk)
 require(n==source['bytes'] and h.hexdigest()==source['sha256'] and m.hexdigest()==source['md5'],'physical source drift')
 require(len(prefix)==4560114 and prefix[:13737]==(ROOT/HDR/'header-prefix.bin').read_bytes(),'header prefix mismatch')
 return prefix

def parse_index(raw):
 from tools.migration72.client72_scene_mplevel_actual_assets import Reader
 r=Reader(raw)
 def string():
  n=r.read('I');value=r.take(n).decode('utf-8');r.align();return value
 object_name=string();count_offset=r.pos;count=r.read('I')
 require(count<=(r.end-r.pos)//24,'index count exceeds object at '+str(count_offset))
 records=[]
 for ordinal in range(count):
  start=r.pos;name=string();file_id,path_id,file_size=r.read('iQQ')
  records.append({'ordinal':ordinal,'start':start,'endExclusive':r.pos,'name':name,'fileID':file_id,'pathID':path_id,'fileSize':file_size})
 require(r.pos==r.end,'index trailing bytes at '+str(r.pos))
 return {'objectName':object_name,'countOffset':count_offset,'count':count,'records':records,'end':r.pos}

def match_target(index,directory):
 matches=[r for r in index['records'] if r['name']=='44935541']
 require(len(matches)==1,'target entry missing or ambiguous')
 entry=matches[0];require(entry['fileID']==0,'target fileID not local')
 objs=[o for o in directory['objects'] if o['pathID']&0xffffffffffffffff==entry['pathID']]
 require(len(objs)==1,'target pathID missing or ambiguous')
 obj=objs[0];require(obj['classID']==1208 and obj['typeHash']==EXPECTED_TYPE,'target class/type hash mismatch')
 return entry,obj

def target_plan(obj,info):
 rows=[];size=info['blockBytes']
 require(0<=obj['start']<obj['endExclusive']<=info['uncompressedBytes'],'target extent invalid')
 for i in range(obj['start']//size,(obj['endExclusive']-1)//size+1):
  a=info['compressedEnds'][i-1] if i else 0;b=info['compressedEnds'][i]
  source='metadata-prefix' if 0<=i<=26 else 'index-window' if 43<=i<=86 else 'unavailable'
  rows.append({'index':i,'rawBegin':info['dataOffset']+a,'rawEndExclusive':info['dataOffset']+b,'outputBegin':i*size,
               'outputEndExclusive':min((i+1)*size,info['uncompressedBytes']),'availability':source})
 return rows

def target_bytes(entry,obj,window,window_start):
 start,end=obj['start'],obj['endExclusive'];limit=window_start+len(window)
 result={'objectFullyAvailable':window_start<=start and end<=limit,'headerAvailable':window_start<=start and start+4<=limit}
 if result['headerAvailable']:
  relative=start-window_start;count=struct.unpack_from('<I',window,relative)[0]
  require(count<=obj['bytes']-4 and count==entry['fileSize'],'byte array count/fileSize mismatch')
  result.update(arrayBytes=count,headerHex=window[relative:relative+4].hex())
  if result['objectFullyAvailable']:
   raw=window[relative:relative+obj['bytes']];payload=raw[4:4+count]
   result.update(objectSHA256=sha(raw),payloadSHA256=sha(payload),trailingBytes=len(raw)-4-count)
   return result,raw,payload
 return result,None,None

def collect(dest):
 result={'task':'F-03d3s2/GADGET-BIN-ACTUAL-INDEX','stage':'sources','nativePrefixCount':0,'independentLZ4Count':0,'targetNativeCodecExecuted':False,'fullPayloadDecoded':False,'reader42005Executed':False,'MPLevelRecovered':False,'naturalLoaderReady':False,'SceneReady':False,'D02Ready':False,'toolSHA256':sha(Path(__file__).read_bytes())}
 try:
  pins,req,pb,profile,stock,source,decoded,info,d,allowed=ingress();result['pins']=pins
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
    i=row['index'];result['stage']='block-'+str(i);raw=prefix[row['rawBegin']:row['rawEndExclusive']];(dest/('block-%02d.raw.bin'%i)).write_bytes(raw)
    transformed,_=probe.transform(blob,prefix,i);result['nativePrefixCount']+=1;(dest/('block-%02d.transformed.bin'%i)).write_bytes(transformed)
    require(transformed[128:]==raw[128:],'tail changed');out=lz4_block(transformed,131072);result['independentLZ4Count']+=1;(dest/('block-%02d.output.bin'%i)).write_bytes(out);outputs.append(out)
    reports.append({'index':i,'rawSHA256':sha(raw),'transformedSHA256':sha(transformed),'outputSHA256':sha(out),'changedPrefixBytes':sum(a!=b for a,b in zip(raw[:128],transformed[:128])),'tailSHA256':sha(raw[128:]),**probe.last})
    save(dest/('block-%02d.proof.private.json'%i),reports[-1])
  finally:probe.close()
  window=b''.join(outputs);start=req['blocks'][0]['outputBegin'];require(len(window)==5767168 and result['nativePrefixCount']==result['independentLZ4Count']==44,'window mismatch')
  (dest/'index-window.bin').write_bytes(window);obj=req['indexObject'];raw=window[obj['start']-start:obj['endExclusive']-start]
  require(len(raw)==obj['bytes'],'index object not fully available');(dest/'index-object.bin').write_bytes(raw)
  result['stage']='index';index=parse_index(raw);save(dest/'index.private.json',index)
  result['stage']='target-binding';entry,target=match_target(index,d)
  entry_raw=raw[entry['start']:entry['endExclusive']];planned=target_plan(target,info);availability,target_raw,payload=target_bytes(entry,target,window,start)
  association={'entry':entry,'entrySHA256':sha(entry_raw),'object':target,'targetBlocks':planned,'byteArray':availability}
  if target_raw is not None:
   (dest/'target-object.bin').write_bytes(target_raw);(dest/'target-payload.bin').write_bytes(payload)
  elif availability['headerAvailable']:(dest/'target-header.bin').write_bytes(bytes.fromhex(availability['headerHex']))
  save(dest/'target.private.json',association)
  result.update(stage='complete',passed=True,blockReports=reports,window={'begin':start,'endExclusive':start+len(window),'bytes':len(window),'sha256':sha(window)},
   indexObject={'bytes':len(raw),'sha256':sha(raw),'recordCount':index['count']},target=association)
  save(dest/'result.private.json',result)
  print(json.dumps({'indexBytes':len(raw),'indexSHA256':sha(raw),'records':index['count'],'target':target,'targetAvailability':availability,'targetBlocks':len(planned)}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def audit(first,second,output):
 names=['pre-execute.private.json','index-window.bin','index-object.bin','index.private.json','target.private.json','result.private.json']
 for i in range(43,87):names.extend('block-%02d.%s'%(i,n) for n in ('raw.bin','transformed.bin','output.bin','proof.private.json'))
 for dname in (first,second):
  for name in ('target-object.bin','target-payload.bin','target-header.bin'):
   if (OUT/dname/name).exists() and name not in names:names.append(name)
 copies=[{n:(OUT/dname/n).read_bytes() for n in names} for dname in (first,second)]
 require(copies[0]==copies[1],'fresh artifacts differ');raw=copies[0];del copies
 pins,req,pb,profile,stock,source,decoded,info,d,allowed=ingress();pre=json.loads(raw['pre-execute.private.json']);res=json.loads(raw['result.private.json'])
 require(pre['pins']==res['pins']==pins and pre['stock']==res['stock']==stock and pre['toolSHA256']==res['toolSHA256']==sha(Path(__file__).read_bytes()),'source proof mismatch')
 require(pre['stage']=='pre-execute' and pre['blocks']==req['blocks'] and pre['allowed']==allowed and res['stage']=='complete' and res['passed'],'stage/range mismatch')
 game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
 physical=physical_prefix(game/source['path'],source);require(pre['sourcePrefix']=={'bytes':len(physical),'sha256':sha(physical)},'physical prefix mismatch')
 from tools.client72_resource_payload import lz4_block
 outputs=[];reports=[]
 for row in req['blocks']:
  i=row['index'];block=raw['block-%02d.raw.bin'%i];transformed=raw['block-%02d.transformed.bin'%i];out=raw['block-%02d.output.bin'%i];proof=json.loads(raw['block-%02d.proof.private.json'%i])
  require(block==physical[row['rawBegin']:row['rawEndExclusive']] and transformed[128:]==block[128:],'raw block or tail mismatch')
  require(lz4_block(transformed,131072)==out and len(out)==131072,'block decode mismatch')
  require(proof['index']==i and proof['rawSHA256']==sha(block) and proof['transformedSHA256']==sha(transformed) and proof['outputSHA256']==sha(out) and proof['tailSHA256']==sha(block[128:]) and proof['changedPrefixBytes']==sum(a!=b for a,b in zip(block[:128],transformed[:128])),'block proof mismatch')
  require(proof['al']==1 and proof['guardsIntact'] and proof['tailUnchanged'] and proof['protectedWriteEvents']==0 and proof['headerStorageProtectedBytes']==13725 and not proof['run']['modeledCalls'] and not proof['run']['memoryModels'] and proof['run']['error'] is None and proof['run']['failure'] is None and proof['run']['stop']=='0x620000000','prefix protection evidence mismatch')
  outputs.append(out);reports.append(proof)
 window=b''.join(outputs);start=req['blocks'][0]['outputBegin'];obj=req['indexObject']
 require(window==raw['index-window.bin'] and res['window']=={'begin':start,'endExclusive':start+len(window),'bytes':len(window),'sha256':sha(window)},'window mismatch')
 source_index=window[obj['start']-start:obj['endExclusive']-start];require(source_index==raw['index-object.bin'],'index object mismatch')
 index=parse_index(source_index);require(index==json.loads(raw['index.private.json']),'index parse mismatch')
 entry,target=match_target(index,d);planned=target_plan(target,info);availability,target_raw,payload=target_bytes(entry,target,window,start)
 association={'entry':entry,'entrySHA256':sha(source_index[entry['start']:entry['endExclusive']]),'object':target,'targetBlocks':planned,'byteArray':availability}
 require(association==json.loads(raw['target.private.json'])==res['target'] and res['indexObject']=={'bytes':len(source_index),'sha256':sha(source_index),'recordCount':index['count']},'target association mismatch')
 if target_raw is not None:require(raw['target-object.bin']==target_raw and raw['target-payload.bin']==payload,'target payload mismatch')
 elif availability['headerAvailable']:require(raw['target-header.bin']==bytes.fromhex(availability['headerHex']),'target header mismatch')
 require(res['blockReports']==reports and res['nativePrefixCount']==res['independentLZ4Count']==44 and not any(res[k] for k in ('targetNativeCodecExecuted','fullPayloadDecoded','reader42005Executed','MPLevelRecovered','naturalLoaderReady','SceneReady','D02Ready')),'scope mismatch')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'currentNativeCalls':0,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in raw.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required');OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
