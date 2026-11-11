"""Recover only fixed gadget block zero and inspect the actual available prefix."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from tools.migration72 import scene_mplevel_actual_configmeta_fields_header as prior
from tools.migration72.client72_scene_mplevel_actual_assets import Reader
from tools.client72_resource_payload import PayloadProbe,lz4_block,asset_index
from tools.client72_resource_blob import BlobProbe,parse_header
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-first'
REQUEST='local/migration72/d02-resume-20261111b/scene/gadget-bin-first-block-directory-request.private.md'
HEADER='local/migration72/d02-resume-20261111b/scene-gadget-bin-header/main-third'
PINS={REQUEST:(8317,'bb82bd0e5708810d5eab82bf427067c6419db445d303da2dc069f7cfb122d12e'),
 HEADER+'/pre-header.private.json':(4214,'bd5abcdfa73c63a35a6e3f595dea4e2723da8d63e2232bc5ef90d14c556419d1'),
 HEADER+'/header.private.json':(73561,'015a47a31732eeb4447c879637a14d33dc8b0fe3988bcc9efa814c61c5845b02'),
 HEADER+'/header-prefix.bin':(13737,'c74880c474ed41ae719f438ea2c805bf9a4e8a443faa8c0ebf623a132176bca0'),
 HEADER+'/decoded-header.bin':(13709,'5f430aa919b19dac878a66f8da5b36b5b7fadf5bb97ff5256b250367654e1b21'),
 'tools/client72_resource_payload.py':(25622,'b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa'),
 'tools/migration72/client72_scene_mplevel_actual_assets.py':(None,'b7855047e003c49d5eeaa1bda932e076b462ba973506432e698677c21f6a75d3'),
 'tools/migration72/scene_mplevel_actual_configmeta_fields_header.py':(None,'be4773a47deff4b5ef4e3eac85b060b9654b75e0e2d43d25bac97e32dcd67b88')}
ENTRY,END=0x1415aa8c0,0x1415aa911
FORBIDDEN={0x1415a1ed0,0x1415c1ea0,0x1415c1700,0x1415c1810,0x141c802e0,0x141c80580}
require=prior.require;sha=prior.sha;check=prior.check;save=prior.save


def gate():
 pins={p:check(ROOT/p,*v) for p,v in PINS.items()}
 checks,pb,profile,stock,source=prior.inputs();pins.update(checks)
 old=json.loads((ROOT/HEADER/'header.private.json').read_bytes());pre=json.loads((ROOT/HEADER/'pre-header.private.json').read_bytes())
 require(old['passed'] and old['stage']=='complete' and old['physical']==pre['physical'] and old['toolSHA256']==PINS['tools/migration72/scene_mplevel_actual_configmeta_fields_header.py'][1],'prior header mismatch')
 decoded=(ROOT/HEADER/'decoded-header.bin').read_bytes();parsed=parse_header(decoded,source['bytes'],13709)
 require(parsed==old['header']['parsed'] and (parsed['compressionTag'],parsed['blockBytes'],parsed['blockCount'],parsed['lastBlockBytes'],parsed['dataOffset'],parsed['logicalBlobBytes'],parsed['fileTailBytes'])==(3,131072,3399,38524,13737,139134667,0),'header facts mismatch')
 require(parsed['compressedEnds'][:3]==[60990,121813,184932] and parsed['compressedEnds'][-1]==139120930,'compressed bounds mismatch')
 require(len(parsed['content'])==1 and {k:parsed['content'][0][k] for k in ('name','offset','size','flag')}=={'name':'CAB-53126bc4d34ebc277960cf0c743cffbc','offset':0,'size':445421180,'flag':True},'content mismatch')
 return pins,pb,profile,stock,source,decoded,parsed


def source_prefix(path,source):
 h=hashlib.sha256();m=hashlib.md5();n=0;prefix=b''
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1048576),b''):
   if n<74727:prefix+=chunk[:74727-n]
   h.update(chunk);m.update(chunk);n+=len(chunk)
 require(n==source['bytes'] and h.hexdigest()==source['sha256'] and m.hexdigest()==source['md5'],'physical source drift')
 require(len(prefix)==74727 and prefix[:13737]==(ROOT/HEADER/'header-prefix.bin').read_bytes(),'header prefix drift')
 return prefix


def index_gate(index):require(index==0,'only block zero permitted')

def is_call(raw):
 i=0
 while i<len(raw) and (raw[i] in (0x66,0x67,0xf2,0xf3) or 0x40<=raw[i]<=0x4f):i+=1
 if i<len(raw) and raw[i]==0xe8:return 'direct'
 if i+1<len(raw) and raw[i]==0xff and (raw[i+1]>>3)&7 in (2,3):return 'indirect'
 return None

def intersects(address,size,regions):return any(address<end and address+size>start for start,end in regions)

def make_probe(binary,key):
 class PrefixOnly(BlobProbe):
  setup=PayloadProbe.setup
  def __init__(self):
   super().__init__(binary,ROOT,key);e=self.engine
   require(not e.models,'models not empty');require(e.unwind_ranges.get(ENTRY)==END,'prefix pdata mismatch')
   pos=e.pe.offset(ENTRY,END-ENTRY,executable=True)
   require(sha(binary[pos:pos+END-ENTRY])=='93ee6859b3c44f6dc8a7846ed5b4552f2edc3e99d49450b73e485566931ac18f','prefix code mismatch')
   e.allowed.append((ENTRY,END));self.owned={};self.scratch=e.heap_cursor;self.last=None
  def transform(self,blob,data,index):
   index_gate(index);require(len(data)==74727 and len(data[13737:])==60990,'actual prefix incomplete')
   return PayloadProbe.transform(self,blob,data,index)
  def call(self,address,registers,stack=(),stops=()):
   require(address==ENTRY and not stack and not stops,'unapproved call')
   e=self.engine;header=registers['rcx'];ptr=registers['rdx'];n=registers['r8'];storage=e.read(header+16);table=e.read(0x145eed350)
   require(n==60990,'block length mismatch')
   archive_before=bytes(e.uc.mem_read(header-0xa8,0x138));storage_before=bytes(e.uc.mem_read(storage,13725));table_before=bytes(e.uc.mem_read(table,256));input_before=bytes(e.uc.mem_read(ptr-16,n+32))
   allowed=tuple(e.allowed);targets=[hex(address)];pending=[None];edges=[];writes=[0]
   protected=((ptr-16,ptr),(ptr+128,ptr+n+16),(header-0xa8,header-0xa8+0x138),(storage,storage+13725),(table,table+256))
   def inspect(uc,pc,size,user):
    if pc in FORBIDDEN or not any(a<=pc<z for a,z in allowed):e.failure='unexpected native entry '+hex(pc);uc.emu_stop();return
    if pending[0]:targets.append(hex(pc));edges.append({'site':hex(pending[0][0]),'kind':pending[0][1],'target':hex(pc)})
    kind=is_call(bytes(uc.mem_read(pc,size)));pending[0]=(pc,kind) if kind else None
   def inspect_write(uc,access,location,size,value,user):
    if intersects(location,size,protected):e.failure='write to protected fixture '+hex(location);uc.emu_stop();return
    if intersects(location,size,((ptr,ptr+128),)):writes[0]+=1
   hook=e.uc.hook_add(e.U.UC_HOOK_CODE,inspect);write_hook=e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE,inspect_write)
   try:value,run,visited=super().call(address,registers,stack,stops)
   finally:e.uc.hook_del(hook);e.uc.hook_del(write_hook)
   after=bytes(e.uc.mem_read(ptr-16,n+32))
   require(value&255==1 and not run['modeledCalls'] and not run['memoryModels'] and run['failure'] is None and run['error'] is None and run['stop']=='0x620000000','prefix return/model mismatch')
   require(after[:16]==input_before[:16]==b'\xa5'*16 and after[-16:]==input_before[-16:]==b'\xa5'*16 and after[144:-16]==input_before[144:-16],'guard/tail changed')
   require(bytes(e.uc.mem_read(header-0xa8,0x138))==archive_before and bytes(e.uc.mem_read(storage,13725))==storage_before and bytes(e.uc.mem_read(table,256))==table_before,'protected fixture changed')
   require(not any(int(x,16) in FORBIDDEN for x in targets),'forbidden target')
   self.last={'al':value&255,'targets':targets,'callEdges':edges,'prefixWriteEvents':writes[0],'protectedWriteEvents':0,'headerStorageProtectedBytes':13725,'guardsIntact':True,'tailUnchanged':True,'run':run}
   return value,run,visited
 return PrefixOnly()


def block_plan(begin,end,info):
 require(0<=begin<end<=info['uncompressedBytes'],'invalid output extent')
 size=info['blockBytes'];result=[]
 for i in range(begin//size,(end-1)//size+1):
  start=info['compressedEnds'][i-1] if i else 0;finish=info['compressedEnds'][i]
  result.append({'index':i,'outputBegin':i*size,'outputEndExclusive':min((i+1)*size,info['uncompressedBytes']),
                 'rawBegin':info['dataOffset']+start,'rawEndExclusive':info['dataOffset']+finish,'alreadyAvailable':i==0})
 return result


def prefix_directory(raw,info):
 require(len(raw)>=20,'serialized header truncated')
 meta,size,version,data=struct.unpack_from('>4I',raw)
 require(size==info['content'][0]['size'] and version==17 and raw[16:20]==bytes(4) and 20+meta<=data<=size,'serialized header mismatch')
 result={'header':{'metadataBytes':meta,'fileBytes':size,'version':version,'dataOffset':data,'endianReservedHex':raw[16:20].hex()},'availablePrefixBytes':len(raw),'expectedFullContentBytes':size,'metadataEnd':20+meta}
 if 20+meta>len(raw):
  result.update(status='metadata-prefix-incomplete',requiredPrefixBlocks=(20+meta+info['blockBytes']-1)//info['blockBytes'],nextBlocks=[x for x in block_plan(0,20+meta,info) if x['index']!=0]);return result
 r=Reader(raw,20,20+meta);engine=r.cstring();platform,tree=r.read('iB')
 result.update(engine=engine,platform=platform,typeTree=tree)
 if engine not in ('2017.4.30f1','2017.4.30f1\n2') or platform!=19 or tree!=0:
  result.update(status='unsupported-engine-platform-tree',firstUnknownOffset=20);return result
 types=[]
 for index in range(r.count(23)):
  off=r.pos;kind,stripped,script=r.read('iBh');sh=r.take(16).hex() if kind==114 else None;th=r.take(16).hex()
  types.append({'index':index,'offset':off,'classID':kind,'stripped':stripped,'scriptIndex':script,'scriptHash':sh,'typeHash':th})
 objects=[];seen=set()
 for ordinal in range(r.count(20)):
  r.align();off=r.pos;pid,start,length,typ=r.read('qIIi')
  require(pid not in seen and 0<=typ<len(types) and data+start+length<=size,'invalid object identity or extent');seen.add(pid)
  objects.append({'ordinal':ordinal,'recordOffset':off,'pathID':pid,'relativeStart':start,'start':data+start,'endExclusive':data+start+length,'bytes':length,'typeIndex':typ,'classID':types[typ]['classID'],'typeHash':types[typ]['typeHash'],'fullyAvailable':data+start+length<=len(raw)})
 ordered=sorted(objects,key=lambda x:x['start']);require(all(a['endExclusive']<=b['start'] for a,b in zip(ordered,ordered[1:])),'overlapping objects')
 scripts=[]
 for index in range(r.count(12)):
  f=r.read('i');r.align();scripts.append({'index':index,'fileID':f,'pathID':r.read('q')})
 externals=[]
 for index in range(r.count(22)):
  empty=r.cstring();guid=r.take(16).hex();typ=r.read('i');path=r.cstring();externals.append({'index':index,'empty':empty,'guid':guid,'type':typ,'path':path})
 user=r.cstring();require(r.pos==r.end,'unconsumed metadata')
 result.update(status='directory-complete',types=types,objects=objects,scripts=scripts,externals=externals,userInformation=user,metadataConsumed=r.pos)
 indexes=[o for o in objects if o['classID']==1210]
 result['indexPlans']=[{'object':o,'blocks':block_plan(o['start'],o['endExclusive'],info)} for o in indexes]
 if any(t['classID']==114 for t in types):result.update(status='script-schema-unreviewed');return result
 if not indexes:result.update(status='index-absent');return result
 if any(o['typeHash']!='375926f2689845ddfc700437e60d9774' for o in indexes):result.update(status='index-typehash-unreviewed');return result
 if any(not o['fullyAvailable'] for o in indexes):result.update(status='index-object-incomplete');return result
 rows=[]
 for o in indexes:
  parsed=asset_index(raw[o['start']:o['endExclusive']]);rows.append({'objectOrdinal':o['ordinal'],'parsed':parsed})
 result['indexes']=rows
 matches=[entry for x in rows for entry in x['parsed']['records'] if entry['name']=='44935541']
 if len(matches)!=1:result.update(status='entry-missing-or-ambiguous',entryMatchCount=len(matches));return result
 entry=matches[0];pid=entry['pathID'] if entry['pathID']<1<<63 else entry['pathID']-(1<<64)
 target=[o for o in objects if o['pathID']==pid and o['classID']==1208 and o['typeHash']=='0451de58691ec5b6e08bba3d9b5b85fa']
 if entry['fileID']!=0 or len(target)!=1:result.update(status='target-binding-unresolved',entry=entry);return result
 result.update(status='target-planned',entry=entry,target=target[0],targetBlocks=block_plan(target[0]['start'],target[0]['endExclusive'],info))
 return result


def collect(dest):
 result={'task':'F-03d3s2/GADGET-BIN-FIRST-BLOCK','stage':'sources','nativePrefixExecuted':False,'independentLZ4Decoded':False,'targetNativeCodecExecuted':False,'fullPayloadDecoded':False,'reader42005Executed':False,'MPLevelRecovered':False,'naturalLoaderReady':False,'SceneReady':False,'D02Ready':False,'toolSHA256':sha(Path(__file__).read_bytes())}
 try:
  pins,pb,profile,stock,source,decoded,parsed=gate();result['pins']=pins
  game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
  result['stock']={p:check(game/p,v['bytes'],v['sha256']) for p,v in stock.items()}
  result['stage']='physical';prefix=source_prefix(game/prior.BLOCK,source);block=prefix[13737:]
  require(len(block)==60990,'block zero short');(dest/'raw-block0.bin').write_bytes(block)
  from tools.client72_resource_provider import Snapshot,u64
  snapshot=Snapshot(ROOT/prior.SNAP,profile,sha(pb));ptr=u64(snapshot.at('archiveDecodeTable.cell',snapshot.base+0x5eed350,8));key=snapshot.at('archiveDecodeTable.bytes',ptr,256)
  require(sha(key)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','archive table mismatch')
  binary=(game/'YuanShen.exe').read_bytes();probe=make_probe(binary,key)
  try:
   result.update(stage='pre-execute',physical={'bytes':source['bytes'],'sha256':source['sha256'],'md5':source['md5']},inputPrefix={'bytes':len(prefix),'sha256':sha(prefix)},rawBlock={'begin':13737,'endExclusive':74727,'bytes':len(block),'sha256':sha(block)},archiveTableSHA256=sha(key),allowed=[{'begin':hex(a),'endExclusive':hex(z)} for a,z in probe.engine.allowed])
   save(dest/'pre-execute.private.json',result)
   result['stage']='prefix';blob={'parsed':parsed,'decodedHeaderHex':decoded.hex()};transformed,_=probe.transform(blob,prefix,0)
   result['nativePrefixExecuted']=True;(dest/'transformed-block0.bin').write_bytes(transformed)
   result['transform']={'sha256':sha(transformed),'changedPrefixBytes':sum(a!=b for a,b in zip(block[:128],transformed[:128])),'unchangedTailSHA256':sha(transformed[128:]),**probe.last}
   require(transformed[128:]==block[128:],'transformed tail mismatch')
   result['stage']='independent-lz4';output=lz4_block(transformed,131072);result['independentLZ4Decoded']=True;(dest/'output-prefix.bin').write_bytes(output)
   result['output']={'bytes':len(output),'sha256':sha(output)}
  finally:probe.close()
  result['stage']='directory';directory=prefix_directory(output,parsed);save(dest/'directory.private.json',directory)
  if directory.get('metadataEnd',len(output)+1)<=len(output):(dest/'metadata.bin').write_bytes(output[20:directory['metadataEnd']])
  result.update(stage='complete',passed=True,directoryStatus=directory['status']);save(dest/'result.private.json',result)
  print(json.dumps({'rawSHA256':sha(block),'outputSHA256':sha(output),'directoryStatus':directory['status'],'metadataEnd':directory['metadataEnd']}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise


def audit(first,second,output):
 names=['pre-execute.private.json','raw-block0.bin','transformed-block0.bin','output-prefix.bin','directory.private.json','result.private.json']
 for d in (first,second):
  if (OUT/d/'metadata.bin').exists() and 'metadata.bin' not in names:names.append('metadata.bin')
 copies=[{n:(OUT/d/n).read_bytes() for n in names} for d in (first,second)]
 require(copies[0]==copies[1],'fresh artifacts differ');raw=copies[0]
 pins,pb,profile,stock,source,decoded,parsed=gate()
 pre=json.loads(raw['pre-execute.private.json']);result=json.loads(raw['result.private.json'])
 require(pre['pins']==result['pins']==pins and pre['stock']==result['stock']==stock and pre['toolSHA256']==result['toolSHA256']==sha(Path(__file__).read_bytes()),'source proof mismatch')
 require(pre['stage']=='pre-execute' and result['stage']=='complete' and result['passed'],'stage mismatch')
 game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
 physical=source_prefix(game/prior.BLOCK,source)
 require(raw['raw-block0.bin']==physical[13737:74727] and pre['inputPrefix']=={'bytes':len(physical),'sha256':sha(physical)} and pre['rawBlock']=={'begin':13737,'endExclusive':74727,'bytes':60990,'sha256':sha(raw['raw-block0.bin'])},'raw block mismatch')
 transformed=raw['transformed-block0.bin'];block=raw['raw-block0.bin'];output_raw=raw['output-prefix.bin']
 require(len(transformed)==len(block)==60990 and transformed[128:]==block[128:] and result['transform']['sha256']==sha(transformed) and result['transform']['changedPrefixBytes']==sum(a!=b for a,b in zip(block[:128],transformed[:128])),'transform mismatch')
 require(lz4_block(transformed,131072)==output_raw and result['output']=={'bytes':len(output_raw),'sha256':sha(output_raw)},'LZ4 output mismatch')
 directory=prefix_directory(output_raw,parsed);require(directory==json.loads(raw['directory.private.json']) and result['directoryStatus']==directory['status'],'directory/plan mismatch')
 if 'metadata.bin' in raw:require(raw['metadata.bin']==output_raw[20:directory['metadataEnd']],'metadata bytes mismatch')
 require(result['nativePrefixExecuted'] and result['independentLZ4Decoded'] and not any(result[k] for k in ('targetNativeCodecExecuted','fullPayloadDecoded','reader42005Executed','MPLevelRecovered','naturalLoaderReady','SceneReady','D02Ready')),'scope mismatch')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'currentNativeCalls':0,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in raw.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required');OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
