"""Execute only the fixed new physical blob's approved native header transform."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from tools.migration72 import scene_mplevel_actual_configmeta_fields as fields
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-header'
FREPORT='local/migration72/d02-resume-20261111b/scene-configmeta-fields/main-third/result.private.json'
FAUDIT='local/migration72/d02-resume-20261111b/scene-configmeta-fields/audit-third.private.json'
BLOCKSRC='local/migration72/d02-resume-20261111b/scene/gadget-bin-exact-block-source.private.json'
PRIOR='local/migration72/d02-active/resource-blob-astra/blob-final-1.json'
SNAP='local/migration72/d02-active/plain/initialization-archive-elevated6.json'
PROFILE='protocol/cn-7.2/profile.json'
BLOCK='YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/24230448.blk'
PINS={FREPORT:(None,'f1caaed3f51b4dfbe7eb766ae70968c8731b5253a70ab954033a2a5c3fd053f4'),
 FAUDIT:(None,'8ade21b82a641b02ca4cfc0067ce4781f057b53e4b2692d7dbab4acd153347c5'),
 BLOCKSRC:(633,'2cef68a6adfbbb9f62432fa3e9f285ae2a4f760ba8a55abbef1d0e0856da1ff9'),
 PRIOR:(66137,'fe10d49fdfd7fb5df910da6407fd96bfdbfb02f907cced418145c8d4b5a4d739'),
 SNAP:(3242,'63025b39285dca6890ae84e5a4cb6f304f44c7ddf46c19911b6a930351429c0d'),
 PROFILE:(1453,'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'),
 'tools/migration72/scene_mplevel_actual_configmeta_fields.py':(None,'f829624874d4f35da45da7104563547ee726aa7eae77c514d16ccec04759e78e'),
 'tools/game_path.ps1':(None,'9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b'),
 'tools/extract_client72.py':(21826,'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d')}
FILES=('pre-header.private.json','header-prefix.bin','decoded-header.bin','header.private.json')
require=fields.require
sha=fields.sha
check=fields.check
save=fields.save

def inputs():
 checks={p:check(ROOT/p,*x) for p,x in PINS.items()}
 prior=json.loads((ROOT/PRIOR).read_bytes())
 for p,h in prior['sourceSHA256'].items():checks[p]=check(ROOT/p,None,h)
 profile_bytes=(ROOT/PROFILE).read_bytes();profile=json.loads(profile_bytes)['files']
 stock={x['path']:{'bytes':x['size'],'sha256':x['sha256']} for x in profile.values()}
 require({p:v['sha256'] for p,v in stock.items()}==prior['inputSHA256'],'stock profile mismatch')
 result=json.loads((ROOT/FREPORT).read_bytes());audit=json.loads((ROOT/FAUDIT).read_bytes())
 require(result['passed'] and result['binding']['keyHex']=='ce23884493554189' and result['binding']['assetEntryHex']=='44935541' and result['binding']['location']==['31049741',0,[24230448,1,0,0,0,7]] and audit['passed'] and audit['freshFilesEqual'], 'field/index prerequisite mismatch')
 source=json.loads((ROOT/BLOCKSRC).read_bytes())
 require(source['path']==BLOCK and source['bytes']==139134667 and source['sha256']=='cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768' and source['md5']=='edc854321f0e9d8c64fd6e80426fe657','source binding mismatch')
 return checks,profile_bytes,profile,stock,source

def stream_source(path,source):
 h=hashlib.sha256();m=hashlib.md5();n=0;prefix=b''
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1048576),b''):
   if n<13737:prefix+=chunk[:13737-n]
   h.update(chunk);m.update(chunk);n+=len(chunk)
 require(n==source['bytes'] and h.hexdigest()==source['sha256'] and m.hexdigest()==source['md5'],'physical source drift')
 require(len(prefix)==13737 and prefix[:12].hex()=='426c62038d35000005000000' and struct.unpack_from('<I',prefix,4)[0]==13709,'header signature mismatch')
 return prefix,{'bytes':n,'sha256':h.hexdigest(),'md5':m.hexdigest()}

def decode_header(binary,key,prefix,file_size):
 from tools.client72_resource_blob import BlobProbe,parse_header
 p=BlobProbe(binary,ROOT,key);e=p.engine;n=13709
 try:
  sig=e.alloc(12);e.uc.mem_write(sig,prefix[:12]);h=e.alloc(64);storage=e.alloc(n+48);buf=storage+16
  e.uc.mem_write(storage,b'\xa5'*(n+48));e.write(h+16,buf);e.write(h+40,n+16);out=e.alloc(8);size=e.alloc(8)
  value,check_run,_=p.call(0x1415aa740,dict(rcx=sig));require(value&255==1,'signature rejected')
  value,prepare,_=p.call(0x1415aa760,dict(rcx=h,rdx=sig,r8=out,r9=size));require(value&255==1 and e.read(out)==buf and e.read(size)==n+16,'prepare failed')
  e.uc.mem_write(buf,prefix[12:]);value,transform,visited=p.call(0x1415aa820,dict(rcx=h))
  decoded=bytes(e.uc.mem_read(buf+16,n))
  require(value&255==1 and bytes(e.uc.mem_read(buf,16))==prefix[12:28] and bytes(e.uc.mem_read(storage,16))==b'\xa5'*16 and bytes(e.uc.mem_read(buf+n+16,16))==b'\xa5'*16 and decoded[128:]==prefix[156:] and e.read(h+56)==buf+16 and e.read(h+48)==buf and 0x141cc06c0 in visited,'header transform/guard/cookie mismatch')
  parsed=parse_header(decoded,file_size,n);getters=p.check_getters(h,parsed)
  runs=[check_run,prepare,transform]+[x['run'] for x in getters]
  require(not any(r['modeledCalls'] or r['memoryModels'] for r in runs),'unapproved model')
  return decoded,{'decodedSHA256':sha(decoded),'parsed':parsed,'runs':[check_run,prepare,transform],'nativeGetters':getters,'nativeGetterCount':len(getters),'canaries':True,'ivPreserved':True,'untouchedTail':True,'cookieCheck':True}
 finally:p.close()

def collect(dest):
 result={'task':'F-03d3s2/GADGET-BIN-HEADER-ONLY','stage':'sources','payloadPrefixExtracted':False,'fullSourceStreamHashed':True,'payloadDecoded':False,'codecExecution':False,'clientExecuted':False,'reader42005Executed':False,'naturalLoaderReady':False,'SceneReady':False,'D02Ready':False,'toolSHA256':sha(Path(__file__).read_bytes())}
 try:
  checks,pb,profile,stock,source=inputs();result['pins']=checks
  game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
  result['stage']='stock';result['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in stock.items()}
  pkg=game/'pkg_version';check(pkg,None,'0b8d5de9333f241af06ac3989f8bf3d090f086f71bedcdece844d7cbf194d219')
  lines=pkg.read_text(encoding='utf-8-sig').splitlines();record=json.loads(lines[1599])
  require(record['remoteName']==BLOCK and record['fileSize']==source['bytes'] and record['md5']==source['md5'],'pkg line binding mismatch')
  result['stage']='physical-source';prefix,physical=stream_source(game/BLOCK,source)
  from tools.client72_resource_provider import Snapshot,u64
  context=Snapshot(ROOT/SNAP,profile,sha(pb));pointer=u64(context.at('archiveDecodeTable.cell',context.base+0x5eed350,8));key=context.at('archiveDecodeTable.bytes',pointer,256)
  require(sha(key)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','archive table mismatch')
  (dest/'header-prefix.bin').write_bytes(prefix)
  result.update(stage='pre-header',physical=physical,prefix={'bytes':len(prefix),'sha256':sha(prefix)},archiveTableSHA256=sha(key),pkgLine=1600)
  save(dest/'pre-header.private.json',result)
  result['stage']='header';binary=(game/'YuanShen.exe').read_bytes()
  decoded,header=decode_header(binary,key,prefix,source['bytes']);(dest/'decoded-header.bin').write_bytes(decoded)
  result.update(stage='complete',passed=True,header=header)
  save(dest/'header.private.json',result)
  parsed=header['parsed'];print(json.dumps({'headerSHA256':sha(decoded),'getters':header['nativeGetterCount'],'compressionTag':parsed['compressionTag'],'blockCount':parsed['blockCount'],'contentCount':len(parsed['content']),'logicalBytes':parsed['logicalBlobBytes'],'uncompressedBytes':parsed['uncompressedBytes'],'fileTailBytes':parsed['fileTailBytes']}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def audit(first,second,output):
 from tools.client72_resource_blob import parse_header
 copies=[{n:(OUT/d/n).read_bytes() for n in FILES} for d in (first,second)]
 require(copies[0]==copies[1],'fresh outputs differ');raw=copies[0];pre=json.loads(raw['pre-header.private.json']);res=json.loads(raw['header.private.json'])
 checks,_,_,stock,source=inputs()
 require(pre['pins']==res['pins']==checks and pre['stock']==res['stock']==stock and pre['physical']==res['physical']=={'bytes':source['bytes'],'sha256':source['sha256'],'md5':source['md5']},'source proof mismatch')
 require(pre['toolSHA256']==res['toolSHA256']==sha(Path(__file__).read_bytes()) and pre['stage']=='pre-header' and res['stage']=='complete' and res['passed'],'stage/tool mismatch')
 prefix=raw['header-prefix.bin'];decoded=raw['decoded-header.bin'];require(len(prefix)==13737 and prefix[:12].hex()=='426c62038d35000005000000' and pre['prefix']=={'bytes':len(prefix),'sha256':sha(prefix)},'prefix mismatch')
 header=res['header'];parsed=parse_header(decoded,source['bytes'],13709)
 require(header['parsed']==parsed and header['decodedSHA256']==sha(decoded) and len(decoded)==13709 and decoded[128:]==prefix[156:],'decoded header mismatch')
 require(header['nativeGetterCount']==len(header['nativeGetters']) and all(header[k] for k in ('canaries','ivPreserved','untouchedTail','cookieCheck')),'header evidence mismatch')
 require(not any(res[k] for k in ('payloadPrefixExtracted','payloadDecoded','codecExecution','clientExecuted','reader42005Executed','naturalLoaderReady','SceneReady','D02Ready')),'scope mismatch')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'currentNativeCalls':0,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in raw.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required');OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
