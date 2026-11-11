"""Recover only approved gadget metadata blocks 1..26 and parse their real prefix."""
import argparse
import hashlib
import json
import os
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-metadata-prefix'
REQUEST='local/migration72/d02-resume-20261111b/scene/gadget-bin-metadata-prefix-request.private.md'
RANGES='local/migration72/d02-resume-20261111b/scene/gadget-bin-metadata-prefix-ranges.private.json'
FIRST='local/migration72/d02-resume-20261111b/scene-gadget-bin-first/main-third-final'
R3='local/migration72/d02-active/resource-payload-astra/payload-final-1.json'
HDR='local/migration72/d02-resume-20261111b/scene-gadget-bin-header/main-third'
PROFILE='protocol/cn-7.2/profile.json'
SNAP='local/migration72/d02-active/plain/initialization-archive-elevated6.json'
ENTRY,END=0x1415aa8c0,0x1415aa911
FORBIDDEN={0x1415a1ed0,0x1415c1ea0,0x1415c1700,0x1415c1810,0x141c802e0,0x141c80580}
UNICORN_PINS={
 'local/migration72/python/unicorn/__init__.py': (305, '0e8ac7115c755994a968fa865bd1b2f2075edea715c17e91d62af2cc52c13392'),
 'local/migration72/python/unicorn/arm64_const.py': (7873, 'ddadd814eccd0a312bb43da7b4f2298690660bd5698c27edc306b1d981cff972'),
 'local/migration72/python/unicorn/arm_const.py': (4284, 'ace0c3e49f61649034a1bec4279576b67e064df146ea8062c67e68dc14e6eb7b'),
 'local/migration72/python/unicorn/m68k_const.py': (1144, 'af95f32f2810fe19888ac723db70c74c7fb1df2bc8ba3578ede0a28b8eca94a7'),
 'local/migration72/python/unicorn/mips_const.py': (5160, '087925a25a128662e095bb861af3c38a2a6c522fc21e222e31b71aeacaaa63d3'),
 'local/migration72/python/unicorn/riscv_const.py': (7270, '86e08d5f6327d154306d5d02899331892de6f44020ff86900a84d2b212205925'),
 'local/migration72/python/unicorn/s390x_const.py': (4101, '4f6194d1697d8684921782a81f494458dc859f8ebf2f78e30f673bd5f7cd590f'),
 'local/migration72/python/unicorn/sparc_const.py': (3381, '9dd58fa5aeab225a0f8186f653f5c8fc5fd161b75ccc6a541e5af79f36af2698'),
 'local/migration72/python/unicorn/tricore_const.py': (3199, '88310f61e12b731b2550031eb6423fbf815e66428b483132d3df47614c9d53ae'),
 'local/migration72/python/unicorn/unicorn.py': (321, '2aed71e6d5bc60807198cdcc3ed43a485a7acae1f41f08d986137afc55df2029'),
 'local/migration72/python/unicorn/unicorn_const.py': (3258, 'c21d7c49f62296591a5f09ed0119ef95083b82f5f3f66b829578784c7b164368'),
 'local/migration72/python/unicorn/unicorn_py3/__init__.py': (24, '2cd4a0d95ef236b5b9e3efb68b20fb6a6d1fb4190db2e9cc0a6447eb2c0aa755'),
 'local/migration72/python/unicorn/unicorn_py3/arch/__init__.py': (0, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'),
 'local/migration72/python/unicorn/unicorn_py3/arch/intel.py': (5453, 'b3b8338a85ac74623e7a67dfb4f44dc9933786a329e64d772987490dbf751637'),
 'local/migration72/python/unicorn/unicorn_py3/arch/types.py': (2774, '525ed179db874a13291f9469bd6243e7293aba17454de9b10a9227106d093787'),
 'local/migration72/python/unicorn/unicorn_py3/unicorn.py': (54301, 'b4de7d560790a587157b17edd71c83b4f2d2bade2447d1a5433c497468ac8aeb'),
 'local/migration72/python/unicorn/x86_const.py': (41605, 'd7663db6425f52838f3e79e45a8fe22b6c02be7edb44367ea2118aec04ee5728'),
 'local/migration72/python/unicorn/lib/unicorn.dll': (9598976, '010d85dce84b8f6ffaec3d5420f1b5f7768f859eb831be574c7da23e656ee8f1'),
}
SUPPORTED={142:'97da5f4688e45a57c8b42d4f42497297',1210:'375926f2689845ddfc700437e60d9774',1208:'0451de58691ec5b6e08bba3d9b5b85fa'}

def require(ok,msg):
 if not ok:raise ValueError(msg)
def sha(b):return hashlib.sha256(b).hexdigest()
def save(path,obj):
 with path.open('xb') as f:f.write((json.dumps(obj,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())
def verify(path,size,digest):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 require(n==size and h.hexdigest()==digest,'pin mismatch: '+str(path.name));return {'bytes':n,'sha256':h.hexdigest()}

def ranges_gate(rows,info):
 require([r['index'] for r in rows]==list(range(1,27)),'indices must be 1..26')
 for row in rows:
  i=row['index'];start=info['compressedEnds'][i-1];end=info['compressedEnds'][i]
  require(row=={'index':i,'compressedBeginRelativeToData':start,'compressedEndRelativeToData':end,
   'rawBegin':info['dataOffset']+start,'rawEndExclusive':info['dataOffset']+end,'rawBytes':end-start,
   'outputBegin':i*131072,'outputEndExclusive':(i+1)*131072,'outputBytes':131072},'range mismatch at '+str(i))
 require(rows[0]['rawBegin']==74727 and rows[-1]['rawEndExclusive']==1539016,'range endpoints mismatch')

def ingress():
 # Two request files and the FIRST pre-artifact are checked before their pins are trusted.
 checked={}
 def pin(path,size,digest):
  if path in checked:
   require(checked[path]=={'bytes':size,'sha256':digest},'conflicting pin '+path);return
  checked[path]=verify(ROOT/path,size,digest)
 pin(REQUEST,6838,'039795d9a039644a0a051207fd57e31f9b89acf74f9eb3dd210248f0cdfb48d8')
 pin(RANGES,12135,'07aa7eff7cbcfad8f7ebdef5228dc637811385d0f097ab30e3ec9cb5ca5c3ea2')
 ranges=json.loads((ROOT/RANGES).read_bytes());pins=ranges['fixedFinalFirstEpochPins']
 pre_path=FIRST+'/pre-execute.private.json';pin(pre_path,pins[pre_path]['bytes'],pins[pre_path]['sha256'])
 pre=json.loads((ROOT/pre_path).read_bytes())
 for group in (pins,pre['pins']):
  for path,v in group.items():pin(path,v['bytes'],v['sha256'])
 pin(R3,196804,'efd152bc8e7337ab5af6aa685a1b16567e63c10a84a31398781d2711521deced')
 for path,(size,digest) in UNICORN_PINS.items():pin(path,size,digest)
 override=os.environ.get('LIBUNICORN_PATH')
 require(not override or Path(override).resolve()==(ROOT/'local/migration72/python/unicorn/lib').resolve(),'unreviewed Unicorn library override')
 require(pre['stage']=='pre-execute' and pre['toolSHA256']==pins['tools/migration72/scene_mplevel_actual_gadget_bin_first.py']['sha256'],'FIRST epoch mismatch')
 old=json.loads((ROOT/FIRST/'result.private.json').read_bytes());old_dir=json.loads((ROOT/FIRST/'directory.private.json').read_bytes())
 require(old['passed'] and old['stage']=='complete' and old_dir['status']=='metadata-prefix-incomplete' and old_dir['metadataEnd']==3523165,'FIRST result mismatch')
 from tools.client72_resource_blob import parse_header
 decoded=(ROOT/HDR/'decoded-header.bin').read_bytes();info=parse_header(decoded,139134667,13709)
 header=json.loads((ROOT/HDR/'header.private.json').read_bytes());require(info==header['header']['parsed'],'header parse mismatch')
 require(info['compressionTag']==3 and info['blockBytes']==131072 and info['blockCount']==3399 and info['uncompressedBytes']==445421180 and info['dataOffset']==13737 and info['fileTailBytes']==0,'header values mismatch')
 ranges_gate(ranges['blocks'],info)
 require(ranges['range']['metadataEnd']==3523165 and ranges['range']['mergedActualPrefixBytes']==3538944 and 26*131072<3523165<=27*131072,'metadata budget mismatch')
 profile_bytes=(ROOT/PROFILE).read_bytes();profile=json.loads(profile_bytes)['files'];stock={v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile.values()}
 require(stock==pre['stock'],'profile/stock mismatch')
 r3=json.loads((ROOT/R3).read_bytes());require(r3['profileSHA256']==sha(profile_bytes),'R3 profile mismatch')
 expected_types=[{'classID':k,'stripped':0,'scriptIndex':-1,'typeHash':v} for k,v in SUPPORTED.items()]
 require(len(r3['completePayloads'])==3 and all((x['serialized']['engine'],x['serialized']['platform'],x['serialized']['typeTree'],x['serialized']['types'])==('2017.4.30f1\n2',-2,False,expected_types) for x in r3['completePayloads']),'R3 grammar mismatch')
 source=ranges['sourcePhysical'];require(source=={'bytes':139134667,'md5':'edc854321f0e9d8c64fd6e80426fe657','path':'YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/24230448.blk','sha256':'cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768'},'physical source mismatch')
 return checked,ranges,profile_bytes,profile,stock,source,decoded,info,pre['allowed']

def physical_prefix(path,source):
 h=hashlib.sha256();m=hashlib.md5();n=0;prefix=b''
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1048576),b''):
   if n<1539016:prefix+=chunk[:1539016-n]
   h.update(chunk);m.update(chunk);n+=len(chunk)
 require(n==source['bytes'] and h.hexdigest()==source['sha256'] and m.hexdigest()==source['md5'],'physical source drift')
 require(len(prefix)==1539016 and prefix[:13737]==(ROOT/HDR/'header-prefix.bin').read_bytes() and prefix[13737:74727]==(ROOT/FIRST/'raw-block0.bin').read_bytes(),'FIRST physical prefix mismatch')
 return prefix

def intersects(address,size,regions):return any(address<end and address+size>start for start,end in regions)
def is_call(raw):
 i=0
 while i<len(raw) and (raw[i] in (0x66,0x67,0xf2,0xf3) or 0x40<=raw[i]<=0x4f):i+=1
 if i<len(raw) and raw[i]==0xe8:return 'direct'
 if i+1<len(raw) and raw[i]==0xff and (raw[i+1]>>3)&7 in (2,3):return 'indirect'
 return None

def make_probe(binary,key):
 from tools.client72_resource_blob import BlobProbe
 from tools.client72_resource_payload import PayloadProbe
 sys.path.insert(0,str(ROOT/'local/migration72/python'))
 from unicorn.unicorn_py3 import unicorn as unicorn_runtime
 require(Path(unicorn_runtime.uclib._name).resolve()==(ROOT/'local/migration72/python/unicorn/lib/unicorn.dll').resolve(),'Unicorn library path mismatch')
 class PrefixOnly(BlobProbe):
  setup=PayloadProbe.setup;transform=PayloadProbe.transform
  def __init__(self):
   super().__init__(binary,ROOT,key);e=self.engine
   require(not e.models and e.unwind_ranges.get(ENTRY)==END,'probe models/pdata mismatch')
   pos=e.pe.offset(ENTRY,END-ENTRY,executable=True);require(sha(binary[pos:pos+81])=='93ee6859b3c44f6dc8a7846ed5b4552f2edc3e99d49450b73e485566931ac18f','prefix code drift')
   e.allowed.append((ENTRY,END));self.owned={};self.scratch=e.heap_cursor;self.last=None
  def call(self,address,registers,stack=(),stops=()):
   require(address==ENTRY and not stack and not stops,'unapproved call')
   e=self.engine;header=registers['rcx'];ptr=registers['rdx'];n=registers['r8'];storage=e.read(header+16);table=e.read(0x145eed350)
   archive_before=bytes(e.uc.mem_read(header-0xa8,0x138));storage_before=bytes(e.uc.mem_read(storage,13725));table_before=bytes(e.uc.mem_read(table,256));before=bytes(e.uc.mem_read(ptr-16,n+32))
   allowed=tuple(e.allowed);targets=[hex(address)];pending=[None];edges=[];writes=[0]
   protected=((ptr-16,ptr),(ptr+128,ptr+n+16),(header-0xa8,header-0xa8+0x138),(storage,storage+13725),(table,table+256))
   def inspect(uc,pc,size,user):
    if pc in FORBIDDEN or not any(a<=pc<z for a,z in allowed):e.failure='unexpected native entry '+hex(pc);uc.emu_stop();return
    if pending[0]:targets.append(hex(pc));edges.append({'site':hex(pending[0][0]),'kind':pending[0][1],'target':hex(pc)})
    kind=is_call(bytes(uc.mem_read(pc,size)));pending[0]=(pc,kind) if kind else None
   def write(uc,access,location,size,value,user):
    if intersects(location,size,protected):e.failure='protected write '+hex(location);uc.emu_stop();return
    if intersects(location,size,((ptr,ptr+128),)):writes[0]+=1
   hook=e.uc.hook_add(e.U.UC_HOOK_CODE,inspect);wh=e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE,write)
   try:value,run,visited=super().call(address,registers,stack,stops)
   finally:e.uc.hook_del(hook);e.uc.hook_del(wh)
   after=bytes(e.uc.mem_read(ptr-16,n+32))
   require(value&255==1 and not run['modeledCalls'] and not run['memoryModels'] and run['failure'] is None and run['error'] is None and run['stop']=='0x620000000','prefix return/model mismatch')
   require(after[:16]==before[:16]==b'\xa5'*16 and after[-16:]==before[-16:]==b'\xa5'*16 and after[144:-16]==before[144:-16],'input guard/tail mismatch')
   require(bytes(e.uc.mem_read(header-0xa8,0x138))==archive_before and bytes(e.uc.mem_read(storage,13725))==storage_before and bytes(e.uc.mem_read(table,256))==table_before,'protected fixture changed')
   require(not any(int(x,16) in FORBIDDEN for x in targets),'forbidden target')
   self.last={'al':value&255,'targets':targets,'callEdges':edges,'prefixWriteEvents':writes[0],'protectedWriteEvents':0,'headerStorageProtectedBytes':13725,'guardsIntact':True,'tailUnchanged':True,'run':run}
   return value,run,visited
 return PrefixOnly()

def plan(begin,end,info,available):
 require(0<=begin<end<=info['uncompressedBytes'],'invalid output extent')
 size=info['blockBytes'];rows=[]
 for i in range(begin//size,(end-1)//size+1):
  a=info['compressedEnds'][i-1] if i else 0;b=info['compressedEnds'][i]
  rows.append({'index':i,'rawBegin':info['dataOffset']+a,'rawEndExclusive':info['dataOffset']+b,'outputBegin':i*size,
               'outputEndExclusive':min((i+1)*size,info['uncompressedBytes']),'alreadyAvailable':min((i+1)*size,info['uncompressedBytes'])<=available})
 return rows

def directory(raw,full,info):
 from tools.migration72.client72_scene_mplevel_actual_assets import Reader
 require(len(raw)>=20,'serialized header truncated')
 meta,size,version,data=struct.unpack_from('>4I',raw)
 require(size==full and version==17 and raw[16:20]==bytes(4) and 20+meta<=data<=size,'header mismatch')
 require(20+meta<=len(raw),'metadata crosses available prefix')
 r=Reader(raw,20,20+meta);engine=r.cstring();platform,tree=r.read('iB')
 require(engine=='2017.4.30f1\n2' and platform==-2 and tree==0,'engine/platform/tree mismatch at '+str(r.pos))
 types=[]
 for index in range(r.count(23)):
  offset=r.pos;kind,stripped,script=r.read('iBh');script_hash=r.take(16).hex() if kind==114 else None;typehash=r.take(16).hex()
  require(kind in SUPPORTED and typehash==SUPPORTED[kind] and stripped==0 and script==-1,'unsupported type at '+str(offset))
  types.append({'index':index,'offset':offset,'classID':kind,'stripped':stripped,'scriptIndex':script,'scriptHash':script_hash,'typeHash':typehash})
 object_count_offset=r.pos;count=r.count(20);objects=[];seen=set();object_table_start=None
 for ordinal in range(count):
  r.align();offset=r.pos
  if object_table_start is None:object_table_start=offset
  pid,start,length,typ=r.read('qIIi')
  require(pid not in seen and 0<=typ<len(types) and data+start+length<=size,'object identity/extent at '+str(offset));seen.add(pid)
  objects.append({'ordinal':ordinal,'recordOffset':offset,'pathID':pid,'relativeStart':start,'start':data+start,'endExclusive':data+start+length,'bytes':length,'typeIndex':typ,'classID':types[typ]['classID'],'typeHash':types[typ]['typeHash'],'fullyAvailable':data+start+length<=len(raw)})
 ordered=sorted(objects,key=lambda x:x['start']);require(all(a['endExclusive']<=b['start'] for a,b in zip(ordered,ordered[1:])),'overlapping objects')
 object_table_end=r.pos;scripts=[]
 for i in range(r.count(12)):
  offset=r.pos;fid=r.read('i');r.align();scripts.append({'index':i,'offset':offset,'fileID':fid,'pathID':r.read('q')})
 externals=[]
 for i in range(r.count(22)):
  offset=r.pos;empty=r.cstring();guid=r.take(16).hex();typ=r.read('i');path=r.cstring();externals.append({'index':i,'offset':offset,'empty':empty,'guid':guid,'type':typ,'path':path})
 user_offset=r.pos;user=r.cstring();require(r.pos==r.end,'unconsumed metadata at '+str(r.pos))
 index_plans=[{'object':o,'blocks':plan(o['start'],o['endExclusive'],info,len(raw))} for o in objects if o['classID']==1210]
 return {'metadataBytes':meta,'metadataEnd':r.pos,'availablePrefixBytes':len(raw),'declaredFullFileBytes':size,'dataOffset':data,'engine':engine,'platform':platform,'typeTree':tree,'types':types,
         'objectCountOffset':object_count_offset,'objectCount':count,'objectTableStart':object_table_start,'objectTableEnd':object_table_end,'objects':objects,
         'scripts':scripts,'externals':externals,'userInformationOffset':user_offset,'userInformation':user,'indexPlans':index_plans}

def actual_directory_gate(d):
 require((d['metadataBytes'],d['metadataEnd'],d['declaredFullFileBytes'],d['dataOffset'],d['objectCount'],d['objectTableStart'],d['objectTableEnd'])==(3523145,3523165,445421180,3523168,176152,116,3523156),'actual metadata shape mismatch')
 require([(t['classID'],t['typeHash']) for t in d['types']]==list(SUPPORTED.items()),'actual types mismatch')

def collect(dest):
 result={'task':'F-03d3s2/GADGET-BIN-METADATA-PREFIX','stage':'sources','block0Reexecuted':False,'nativePrefixCount':0,'independentLZ4Count':0,'targetNativeCodecExecuted':False,'fullPayloadDecoded':False,'reader42005Executed':False,'MPLevelRecovered':False,'naturalLoaderReady':False,'SceneReady':False,'D02Ready':False,'toolSHA256':sha(Path(__file__).read_bytes())}
 try:
  pins,ranges,profile_bytes,profile,stock,source,decoded,info,first_allowed=ingress();result['pins']=pins
  game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
  result['stage']='stock';result['stock']={p:verify(game/p,v['bytes'],v['sha256']) for p,v in stock.items()}
  result['stage']='physical';prefix=physical_prefix(game/source['path'],source)
  from tools.client72_resource_provider import Snapshot,u64
  snapshot=Snapshot(ROOT/SNAP,profile,sha(profile_bytes));pointer=u64(snapshot.at('archiveDecodeTable.cell',snapshot.base+0x5eed350,8));key=snapshot.at('archiveDecodeTable.bytes',pointer,256)
  require(sha(key)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','archive table mismatch')
  binary=(game/'YuanShen.exe').read_bytes();probe=make_probe(binary,key)
  try:
   require([{'begin':hex(a),'endExclusive':hex(b)} for a,b in probe.engine.allowed]==first_allowed,'FIRST allowlist drift')
   result.update(stage='pre-execute',physical=source,inputPrefix={'bytes':len(prefix),'sha256':sha(prefix)},blocks=ranges['blocks'],archiveTableSHA256=sha(key),allowed=[{'begin':hex(a),'endExclusive':hex(b)} for a,b in probe.engine.allowed])
   save(dest/'pre-execute.private.json',result)
   from tools.client72_resource_payload import lz4_block
   blob={'parsed':info,'decodedHeaderHex':decoded.hex()};outputs=[(ROOT/FIRST/'output-prefix.bin').read_bytes()];reports=[]
   for row in ranges['blocks']:
    i=row['index'];result['stage']='block-'+str(i);raw=prefix[row['rawBegin']:row['rawEndExclusive']];(dest/('block-%02d.raw.bin'%i)).write_bytes(raw)
    transformed,_=probe.transform(blob,prefix,i);result['nativePrefixCount']+=1;(dest/('block-%02d.transformed.bin'%i)).write_bytes(transformed)
    require(transformed[128:]==raw[128:],'tail changed');out=lz4_block(transformed,131072);result['independentLZ4Count']+=1;(dest/('block-%02d.output.bin'%i)).write_bytes(out);outputs.append(out)
    reports.append({'index':i,'rawSHA256':sha(raw),'transformedSHA256':sha(transformed),'outputSHA256':sha(out),'changedPrefixBytes':sum(a!=b for a,b in zip(raw[:128],transformed[:128])),'tailSHA256':sha(raw[128:]),**probe.last})
    save(dest/('block-%02d.proof.private.json'%i),reports[-1])
  finally:probe.close()
  merged=b''.join(outputs);require(len(merged)==3538944 and merged[:131072]==outputs[0] and result['nativePrefixCount']==result['independentLZ4Count']==26,'merged output mismatch')
  (dest/'actual-prefix.bin').write_bytes(merged);result['stage']='directory';d=directory(merged,445421180,info);actual_directory_gate(d)
  (dest/'metadata.bin').write_bytes(merged[20:d['metadataEnd']]);save(dest/'directory.private.json',d)
  save(dest/'index-plan.private.json',d['indexPlans'])
  result.update(stage='complete',passed=True,blockReports=reports,actualPrefix={'bytes':len(merged),'sha256':sha(merged)},metadata={'bytes':d['metadataBytes'],'sha256':sha(merged[20:d['metadataEnd']])},objectCount=d['objectCount'],indexObjectCount=len(d['indexPlans']))
  save(dest/'result.private.json',result)
  print(json.dumps({'prefixSHA256':sha(merged),'metadataSHA256':result['metadata']['sha256'],'objects':d['objectCount'],'indexObjects':len(d['indexPlans'])}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def audit(first,second,output):
 names=['pre-execute.private.json','actual-prefix.bin','metadata.bin','directory.private.json','index-plan.private.json','result.private.json']
 for i in range(1,27):names.extend('block-%02d.%s'% (i,n) for n in ('raw.bin','transformed.bin','output.bin','proof.private.json'))
 copies=[{n:(OUT/d/n).read_bytes() for n in names} for d in (first,second)]
 require(copies[0]==copies[1],'fresh artifacts differ');raw=copies[0];del copies
 pins,ranges,profile_bytes,profile,stock,source,decoded,info,first_allowed=ingress()
 pre=json.loads(raw['pre-execute.private.json']);res=json.loads(raw['result.private.json'])
 require(pre['pins']==res['pins']==pins and pre['stock']==res['stock']==stock and pre['toolSHA256']==res['toolSHA256']==sha(Path(__file__).read_bytes()),'source proof mismatch')
 require(pre['stage']=='pre-execute' and pre['blocks']==ranges['blocks'] and pre['allowed']==first_allowed and res['stage']=='complete' and res['passed'],'stage/range mismatch')
 game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
 physical=physical_prefix(game/source['path'],source)
 require(pre['inputPrefix']=={'bytes':len(physical),'sha256':sha(physical)},'physical prefix mismatch')
 from tools.client72_resource_payload import lz4_block
 merged=[(ROOT/FIRST/'output-prefix.bin').read_bytes()];reports=[]
 for row in ranges['blocks']:
  i=row['index'];block=raw['block-%02d.raw.bin'%i];transformed=raw['block-%02d.transformed.bin'%i];out=raw['block-%02d.output.bin'%i];proof=json.loads(raw['block-%02d.proof.private.json'%i])
  require(block==physical[row['rawBegin']:row['rawEndExclusive']] and len(block)==row['rawBytes'] and transformed[128:]==block[128:],'raw block or tail mismatch')
  require(lz4_block(transformed,131072)==out and len(out)==row['outputBytes'],'block decode mismatch')
  require(proof['index']==i and proof['rawSHA256']==sha(block) and proof['transformedSHA256']==sha(transformed) and proof['outputSHA256']==sha(out) and proof['tailSHA256']==sha(block[128:]) and proof['changedPrefixBytes']==sum(a!=b for a,b in zip(block[:128],transformed[:128])),'block proof mismatch')
  require(proof['al']==1 and proof['guardsIntact'] and proof['tailUnchanged'] and proof['protectedWriteEvents']==0 and proof['headerStorageProtectedBytes']==13725 and not proof['run']['modeledCalls'] and not proof['run']['memoryModels'] and proof['run']['error'] is None and proof['run']['failure'] is None,'prefix protection evidence mismatch')
  reports.append(proof);merged.append(out)
 merged=b''.join(merged);require(merged==raw['actual-prefix.bin'] and len(merged)==3538944 and res['actualPrefix']=={'bytes':len(merged),'sha256':sha(merged)},'concat mismatch')
 d=directory(merged,445421180,info);actual_directory_gate(d)
 require(d==json.loads(raw['directory.private.json']) and d['indexPlans']==json.loads(raw['index-plan.private.json']) and raw['metadata.bin']==merged[20:d['metadataEnd']],'directory/plan mismatch')
 require(res['blockReports']==reports and res['nativePrefixCount']==res['independentLZ4Count']==26 and not any(res[k] for k in ('block0Reexecuted','targetNativeCodecExecuted','fullPayloadDecoded','reader42005Executed','MPLevelRecovered','naturalLoaderReady','SceneReady','D02Ready')),'scope mismatch')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'currentNativeCalls':0,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in raw.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required');OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
