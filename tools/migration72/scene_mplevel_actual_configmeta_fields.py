"""Decode fixed ConfigMeta fields and bind the selected second UInt64 to its index."""
import argparse
import hashlib
import json
import pickle
import struct
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-fields'
REQ='local/migration72/d02-resume-20261111b/scene/configmeta-fields-bin-header-request.private.md'
ACCEPT='local/migration72/d02-resume-20261111b/scene/configmeta-fields-accepted-directory-pins.private.json'
META='local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SOURCE='local/migration72/d02-resume-20261110b/scene-mplevel-group-source/main-third.private.json'
EXPLORE='local/migration72/d02-resume-20261111b/scene/configmeta-field-walk-exploration.private.json'
INDEX='local/migration72/d02-active/main/payload-index-third.json'
PAYLOAD='local/migration72/d02-active/resource-payload-astra/payload-final-1'
LOCS='local/migration72/d02-active/main/payload-index-third'
PINS={REQ:(8619,'cf6ecf21cfd8f0d7c13204990d5317997260f104fb2a77418538e8b0bffd8bf1'),
 ACCEPT:(1314,'d3f282eb36ae60524342f587b87bcfb54fe7accb54ac4b7ac141d295ff8713e7'),
 META:(117201003,'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f'),
 SOURCE:(51096,'d45198fa2cc6919ecb2c5f4f84c6264e214048d4169abf31c8e7b32ea1bbb3e2'),
 EXPLORE:(181540,'1b79fc06408b48200c62e46edcecd92e3c130020bc7e27d9c3eda3634eeedd88'),
 INDEX:(92384,'e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9'),
 'local/migration72/d02-resume-20261111b/scene-configmeta-ooz/main-third/content.bin':(334996,'5dbd48d5856e05ee877932dc7282a51ce30e03e4208174fd43c327f3a68f5a5e'),
 PAYLOAD+'/31049740.object3.m_bytes.bin':(48614214,'216c791b536ce8e4f1f0128c200a791015768491bf23f2a8a9bc618720b6eb7d'),
 PAYLOAD+'/31049741.object3.m_bytes.bin':(3621204,'782aa1208ab3070a567f8907e7f15b8f3ad046b0d661cdd4774ef9af8f81dffc'),
 LOCS+'/31049740.locations-le-i32.bin':(34612632,'c013043753bdb2679bb9a59d4aab4d6db62d87febd37bb27e69adb00ee8093f2'),
 LOCS+'/31049741.locations-le-i32.bin':(12864,'29b5f4215a2c5caf2d1b57cf967f0d8e1bdb96f00af57d229e63be2a27c4ebb0'),
 'tools/client72_resource_locator.py':(23676,'413d2501323fde768076d59222a39af30ebd4857d89759478d060b6c22834456'),
 'tools/client72_resource_payload_index.py':(19450,'3fd618d4c990f3358eff772b4af30553fb332c2e1ed20423a3f5783e6a59946c')}


def require(ok,msg):
 if not ok:raise ValueError(msg)

def sha(b):return hashlib.sha256(b).hexdigest()

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

def source_gate():
 checked={p:check(ROOT/p,*v) for p,v in PINS.items()}
 accepted=json.loads((ROOT/ACCEPT).read_bytes())
 require(accepted['acceptedDirectory']=='CONFIGMETA-SERIALIZED-IDENTITY' and accepted['requestSHA256']==PINS[REQ][1],'accepted directory binding mismatch')
 for p,v in accepted['pins'].items():checked[p]=check(ROOT/p,v['bytes'],v['sha256'])
 report=json.loads((ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-serialized/main-third-final/identity.private.json').read_bytes())
 require(report['prefix']['cursor']==44 and report['prefix']['absoluteCursor']==4428 and report['class114ObjectOrdinal']==2 and report['customSchemaParsed']==False,'accepted identity mismatch')
 return checked

def declarations(records, source):
 cls, pair, base = records[77121], records[77122], records[78929]
 require(cls['parent']['typeDefinition']==78929 and base['fieldCount']==0 and base['fields']==[], 'parent field mismatch')
 fields=cls['fields'];require(cls['fieldCount']==len(fields)==182 and cls['fieldStart']==386045 and [f['index'] for f in fields]==list(range(386045,386227)), 'field order mismatch')
 for f in fields:
  t=f['type'];require(t['kind']=='szarray' and t['attributes']=='0x6' and t['elementType']['kind']=='valuetype' and t['elementType']['typeDefinition']==77122,'field type mismatch')
 require(pair['fieldCount']==2 and [(f['index'],f['name'],f['type']['kind'],f['type']['attributes']) for f in pair['fields']]==[(386227,'origin','uint64','0x6'),(386228,'bin','uint64','0x6')], 'pair declaration mismatch')
 selected=source['selectedDeclarations']['gadgetPathHashArray']
 require(selected['index']==386052 and fields[7]==selected and source['selectedDeclarations']['pathHash']['fields']==pair['fields'], 'consumer field binding mismatch')
 return fields

def walk(obj,fields):
 cursor=44;rows=[]
 for ordinal,f in enumerate(fields):
  start=cursor;require(cursor+4<=len(obj),'truncated count');count=struct.unpack_from('<i',obj,cursor)[0];cursor+=4
  require(count>=0 and count<=(len(obj)-cursor)//16,'negative or truncated array')
  pairs=[[hex(a),hex(b)] for a,b in struct.iter_unpack('<QQ',obj[cursor:cursor+16*count])]
  cursor+=16*count
  rows.append({'ordinal':ordinal,'field':f['index'],'name':f['name'],'count':count,'start':start,'end':cursor,'sha256':sha(obj[start:cursor]),'pairs':pairs})
 require(cursor==len(obj),'array data not exact EOF')
 return rows

def target_gate(rows):
 require(len(rows)==182 and sum(r['count'] for r in rows)==20615 and sum(r['count']==0 for r in rows)==2,'actual count mismatch')
 x=rows[7]
 require(x['field']==386052 and x['start']==72248 and x['end']==72268 and x['count']==1 and x['sha256']=='8ce2e7973c43e9dd4e2b787abf76a680844ac1a04eb6cb5417638a9537ae3a4d' and x['pairs']==[['0x644cf04bac35cb7f','0xce23884493554189']],'selected array mismatch')
 return int(x['pairs'][0][1],16)

def choose(matches,locs,key):
 from tools.client72_resource_locator import reference_pack
 require(len(matches)==1,'index match not unique')
 row=matches[0]
 require(row['index']=='31049741' and row['offset']==206928 and row['end']==206942 and row['recordSHA256']=='551d1828e00afdc6d998b0aa48349bc9c378e3257deaff7c3df18292648bb109' and row['variants']==[(0,1)],'index record or variant mismatch')
 packed=reference_pack(key,1);require(packed==4941386953816801281 and packed>>32==0x44935541 and packed&0xffffffff==1,'packed identifier mismatch')
 hits=[(name,i,list(v)) for name,rows in locs.items() for i,v in enumerate(rows) if v[1]==1]
 same=[(name,i,list(v)) for name,rows in locs.items() for i,v in enumerate(rows) if v[0]==24230448]
 require(hits==same==[('31049741',0,[24230448,1,0,0,0,7])],'location ambiguous')
 return {'key':key,'keyHex':f'{key:016x}','indexRecord':row,'packed':packed,'bundleID':1,'assetEntryHex':'44935541','location':hits[0]}

def collect(dest):
 result={'task':'F-03d3s2/CONFIGMETA-FIELDS-BIN','stage':'sources','payloadDecoded':False,'reader42005Executed':False,'MPLevelRecovered':False,'naturalLoaderReady':False,'SceneReady':False,'D02Ready':False,'toolSHA256':sha(Path(__file__).read_bytes())}
 try:
  result['pins']=source_gate();result['stage']='pre-fields';save(dest/'pre-fields.private.json',result)
  with (ROOT/META).open('rb') as f:records,_=PureData(f).load()
  source=json.loads((ROOT/SOURCE).read_bytes());fields=declarations(records,source);del records
  obj=(ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-serialized/main-third-final/object-2.bin').read_bytes()
  rows=walk(obj,fields);key=target_gate(rows)
  explore=json.loads((ROOT/EXPLORE).read_bytes())['fieldWalk']
  require(all((a['field'],a['count'],a['start'],a['end'],a['sha256'])==(b['field'],b['count'],b['serializedObjectStart'],b['serializedObjectEnd'],b['payloadSHA256']) and ('firstPairs' not in b or a['pairs'][:3]==b['firstPairs']) for a,b in zip(rows,explore)) and len(explore)==len(rows),'exploration cross-check mismatch')
  save(dest/'field-walk.private.json',rows);result.update(stage='pre-index',fieldCount=len(rows),pairCount=sum(x['count'] for x in rows),keyHex=f'{key:016x}')
  save(dest/'pre-index.private.json',result)
  from tools.client72_resource_locator import find_matches
  prior=json.loads((ROOT/INDEX).read_bytes());matches=find_matches(ROOT/PAYLOAD,prior,[{'key':key}])[key]
  locs={name:list(struct.iter_unpack('<6i',(ROOT/LOCS/(name+'.locations-le-i32.bin')).read_bytes())) for name in ('31049740','31049741')}
  binding=choose(matches,locs,key)
  result.update(stage='complete',passed=True,binding=binding,sourceChain=source['sourceChain'],customSchemaParsed=True,actualReaderReturnTypeKnown=False)
  save(dest/'result.private.json',result)
  print(json.dumps({'fields':len(rows),'pairs':20615,'key':f'{key:016x}','entry':'44935541'}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def audit(first,second,output):
 names=('pre-fields.private.json','field-walk.private.json','pre-index.private.json','result.private.json')
 copies=[{n:(OUT/d/n).read_bytes() for n in names} for d in (first,second)]
 require(copies[0]==copies[1],'fresh artifacts differ')
 data=copies[0];checks=source_gate();pre=json.loads(data['pre-fields.private.json'])
 require(pre['pins']==checks and pre['toolSHA256']==sha(Path(__file__).read_bytes()),'pin proof mismatch')
 with (ROOT/META).open('rb') as f:records,_=PureData(f).load()
 source=json.loads((ROOT/SOURCE).read_bytes());fields=declarations(records,source);del records
 obj=(ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-serialized/main-third-final/object-2.bin').read_bytes()
 rows=walk(obj,fields);key=target_gate(rows)
 require(rows==json.loads(data['field-walk.private.json']),'field walk mismatch')
 from tools.client72_resource_locator import find_matches
 prior=json.loads((ROOT/INDEX).read_bytes());matches=find_matches(ROOT/PAYLOAD,prior,[{'key':key}])[key]
 locs={name:list(struct.iter_unpack('<6i',(ROOT/LOCS/(name+'.locations-le-i32.bin')).read_bytes())) for name in ('31049740','31049741')}
 binding=choose(matches,locs,key);result=json.loads(data['result.private.json'])
 require(json.loads(json.dumps(binding))==result['binding'] and result['passed'] and result['stage']=='complete','binding differs')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'nativeCalls':0,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in data.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required')
 OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
