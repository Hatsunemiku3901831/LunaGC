"""Pure host ordered parse and inverse of the fixed actual MPLevel payload."""
import argparse
from collections import Counter
import hashlib
import json
import struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-mplevel-actual-host'
REQUEST='local/migration72/d02-resume-20261111b/scene/mplevel-actual-host-reference-request.private.md'
TARGET='local/migration72/d02-resume-20261111b/scene-gadget-bin-target/main-third'
ORACLE='local/migration72/d02-resume-20261111b/scene/mplevel-actual-exploration.private.json'
PINS={REQUEST:(9585,'a529fee6c08f54f783e4ed83e7fec84f4249024ba95f35b99e3cb7bc6f8f39d4'),
 TARGET+'/payload.bin':(782517,'25bf9c7e24ca9d708b39deafe361c0a339daf9cbb5aadc6b61a2324e57cd891e'),
 TARGET+'/target-object.bin':(782524,'91f24ac25a4a50a5d4dec7152e9c3a86b2290a14100ea102571c413b2f88f9a8'),
 TARGET+'/tail.bin':(3,'709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c'),
 TARGET+'/result.private.json':(495807,'e25e89545fe35fa7e5f9dde4d2412ebc5974e242b317db69c3812a02047f8bce'),
 TARGET+'/pre-execute.private.json':(18513,'c61869821492aeef618ad287144678b4d356ba7ea6b714e94b6ed5bb41c0edd1'),
 'local/migration72/d02-resume-20261111b/scene-gadget-bin-target/audit-third.private.json':(4977,'603c2892ec83bebcc81755f0f06a1ab045965afdb517fc6f191376e8b464f1d4'),
 'local/migration72/d02-resume-20261111-asc/scene-parser/main-third.private.json':(27312,'698cf34f6544786f6343dbdbb63ecd3a354d05e76a2dee97228ce051681d5632'),
 'local/migration72/d02-resume-20261111-asc/scene-mplevel-positive-analysis/main-third.private.json':(74815,'3162a55e75712cc55873cfecec31adfb9fd6640175bba1f52d2fb5f8ed2b5f19'),
 'local/migration72/d02-resume-20261110b/scene-mplevel-group-source/main-third.private.json':(51096,'d45198fa2cc6919ecb2c5f4f84c6264e214048d4169abf31c8e7b32ea1bbb3e2'),
 ORACLE:(5056454,'b09b44d69a10c6745a90684312bfb6dac31b4db1f72f024cc06932789ed144f9'),
 'tools/migration72/scene_mplevel_actual_gadget_bin_target.py':(14661,'ea78690459f8b566b08b1b8fb6cbe3a291fb13e48ff6283c407d01a6ff41b10e')}
KEY_ADD=0x758f43e21a5457c0
CHILD_ADD=0x22c2665a3dc5a88c
EXPECTED_CHILDREN=['MPLevel','Team','Enviro01','Enviro02','Enviro03','Grass01','Water01','BrokenIce01','Tree01','Bush01','Bush02','Bush_NoFire01','Bush_Yellow','Bush_Ashes01','CustomTile','Foxtail_Grass01','Foxtail_Grass02','Windmill','Oil','Storm_Lightning','Storm_Lightning_New']
FLAGS={'nativeReaderExecuted':False,'readerFactoryOffsetOrBackendProven':False,'nativeDictionaryReturned':False,'worldFactoryReturnProven':False,'slot114LifecycleProven':False,'sceneReady':False,'d02Passed':False}


def require(ok,msg):
 if not ok:raise ValueError(msg)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def save(path,obj):
 with path.open('xb') as f:f.write((json.dumps(obj,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode())

def ingress():
 raw={};checks={}
 for path,(size,digest) in PINS.items():
  data=(ROOT/path).read_bytes();require(len(data)==size and sha(data)==digest,'pin mismatch: '+path);raw[path]=data;checks[path]={'bytes':len(data),'sha256':digest}
 data,obj,tail=raw[TARGET+'/payload.bin'],raw[TARGET+'/target-object.bin'],raw[TARGET+'/tail.bin']
 require(struct.unpack_from('<I',obj)[0]==len(data) and obj[4:4+len(data)]==data and obj[4+len(data):]==tail,'target byte-array relation mismatch')
 report=json.loads(raw[TARGET+'/result.private.json']);audit=json.loads(raw['local/migration72/d02-resume-20261111b/scene-gadget-bin-target/audit-third.private.json'])
 require(report['passed'] and report['stage']=='complete' and report['byteArray']['payloadSHA256']==sha(data) and report['byteArray']['count']==len(data) and audit['passed'] and audit['freshFilesEqual'],'accepted target mismatch')
 return raw,checks

class ReadError(ValueError):
 def __init__(self,cursor,needed,remaining):
  self.cursor=cursor;self.needed=needed;self.remaining=remaining
  super().__init__(f'payload EOF at cursor {cursor}: need {needed}, remaining {remaining}')

class Reader:
 def __init__(self,raw):self.raw=raw;self.pos=0
 def take(self,n):
  if n<0 or self.pos+n>len(self.raw):raise ReadError(self.pos,n,len(self.raw)-self.pos)
  start=self.pos;self.pos+=n;return self.raw[start:self.pos]
 def number(self,fmt):return struct.unpack('<'+fmt,self.take(struct.calcsize('<'+fmt)))[0]

def words(raw,constant,inverse=False):
 out=bytearray()
 for i in range(0,len(raw),8):
  chunk=raw[i:i+8];width=len(chunk);mask=(1<<(8*width))-1;value=int.from_bytes(chunk,'little')
  value=(value-constant if inverse else value+constant)&mask
  out.extend(value.to_bytes(width,'little'))
 return bytes(out)

def string_field(reader,ordinal,child=False):
 offset=reader.pos;length_raw=reader.number('H')
 length=((length_raw^0xa88c)+0x665a)&0xffff if child else length_raw^0x57c0
 begin=reader.pos;encoded=reader.take(length);decoded=words(encoded,CHILD_ADD if child else KEY_ADD)
 return {'ordinal':ordinal,'lengthOffset':offset,'lengthRaw':length_raw,'bytesOffset':begin,'byteLength':length,'end':reader.pos,
         'decodedHex':decoded.hex(),'bytesSHA256':sha(decoded),'ascii':decoded.decode('ascii') if all(x<128 for x in decoded) else None}

def parse(raw):
 r=Reader(raw);prefix_raw=r.number('B');prefix=((prefix_raw^0x08)+0x3e)&0xff
 base={'startOffset':0,'prefixOffset':0,'prefixRaw':prefix_raw,'prefixDecoded':prefix}
 if not prefix&0x20:return dict(base,status='unresolved-shared-empty-branch',cursor=r.pos,reason='prefix-clear')
 outer_offset=r.pos;outer_raw=r.number('I');count=(outer_raw+0xf8f43bce)&0xffffffff
 base.update(outerCountOffset=outer_offset,outerRaw=outer_raw,outerCount=count)
 if not count:return dict(base,status='unresolved-shared-empty-branch',cursor=r.pos,reason='outer-zero')
 groups=[]
 for ordinal in range(count):
  start=r.pos;key=string_field(r,0);m_offset=r.pos;m_raw=r.number('I');m=m_raw^0x9cb545fb
  children=[string_field(r,j,True) for j in range(m)]
  groups.append({'ordinal':ordinal,'start':start,'key':key,'childCountOffset':m_offset,'childCountRaw':m_raw,'childCount':m,'children':children,'end':r.pos,'recordSHA256':sha(raw[start:r.pos])})
 return dict(base,status='host-ordered-groups-recovered',groups=groups,endOffset=r.pos,trailingBytes=len(raw)-r.pos,exactEOF=r.pos==len(raw))

def summarize(parsed):
 groups=parsed['groups'];keys=[g['key']['decodedHex'] for g in groups];duplicates=Counter(keys)
 children=[c for g in groups for c in g['children']]
 matches=[g for g in groups if any(c['decodedHex']==b'MPLevel'.hex() for c in g['children'])]
 return {'groupCount':len(groups),'childCount':len(children),'emptyKeys':sum(not g['key']['byteLength'] for g in groups),
         'emptyChildren':sum(not c['byteLength'] for c in children),'zeroChildOrdinals':[g['ordinal'] for g in groups if not g['childCount']],
         'duplicateKeys':[{'decodedHex':k,'count':v} for k,v in duplicates.items() if v>1],
         'allDecodedBytesASCII':all(g['key']['ascii'] is not None for g in groups) and all(c['ascii'] is not None for c in children),
         'mplevelOrdinals':[g['ordinal'] for g in matches]}

def inverse(parsed):
 require(parsed['status']=='host-ordered-groups-recovered','cannot encode unresolved branch')
 out=bytearray([((parsed['prefixDecoded']-0x3e)&0xff)^0x08]);out.extend(struct.pack('<I',(parsed['outerCount']-0xf8f43bce)&0xffffffff))
 for g in parsed['groups']:
  k=bytes.fromhex(g['key']['decodedHex']);out.extend(struct.pack('<H',len(k)^0x57c0));out.extend(words(k,KEY_ADD,True))
  out.extend(struct.pack('<I',g['childCount']^0x9cb545fb))
  for c in g['children']:
   v=bytes.fromhex(c['decodedHex']);out.extend(struct.pack('<H',((len(v)-0x665a)&0xffff)^0xa88c));out.extend(words(v,CHILD_ADD,True))
 return bytes(out)

def actual_gate(raw,parsed,summary,oracle):
 require(parsed['status']=='host-ordered-groups-recovered' and parsed['prefixRaw']==0x6a and parsed['prefixDecoded']==0xa0 and parsed['outerCount']==707 and parsed['exactEOF'] and parsed['endOffset']==len(raw)==782517,'actual framing mismatch')
 require(summary=={'groupCount':707,'childCount':15835,'emptyKeys':0,'emptyChildren':0,'zeroChildOrdinals':[450,461,502,511,575],'duplicateKeys':[],'allDecodedBytesASCII':True,'mplevelOrdinals':[544]},'actual summary mismatch')
 group=parsed['groups'][544];key=group['key'];children=group['children']
 require((group['start'],group['end'],group['recordSHA256'],key['byteLength'],key['lengthOffset'],key['bytesOffset'],key['ascii'],key['bytesSHA256'],group['childCountOffset'],group['childCount'])==(731264,731558,'08d0ca8d1eb5afaf7d998dfb64a2ab3cfbd6d380f6e6c60345eaa53c76d937a1',47,731264,731266,'Data/_BinOutput/Gadget/ConfigGadget_Scene_World','8ac986bbf8379bc4f8c85eb0060e3705e9e1e3293df8c404617067235d777cf2',731313,21),'MPLevel group mismatch')
 require([c['ascii'] for c in children]==EXPECTED_CHILDREN and (children[0]['lengthOffset'],children[0]['bytesOffset'],children[0]['byteLength'],children[0]['bytesSHA256'],children[-1]['bytesOffset'],children[-1]['byteLength'],children[-1]['end'])==(731317,731319,7,'bd9476ee50d2eb3cf9ee3a076761a0c2c66e8b284cf278aae28c9f01c8ceaef4',731539,19,731558),'MPLevel children mismatch')
 old=oracle['orderedEntries'];require(len(old)==len(parsed['groups']),'oracle group count mismatch')
 for g,o in zip(parsed['groups'],old):
  k=g['key'];require((g['ordinal'],g['start'],g['end'],k['ascii'],k['byteLength'],k['bytesOffset'],k['bytesSHA256'],g['childCount'])==(o['ordinal'],o['start'],o['end'],o['key'],o['keyByteLength'],o['keyBytesOffset'],o['keyBytesSHA256'],o['childCount']),'oracle group mismatch')
  require(len(g['children'])==len(o['children']),'oracle child count mismatch')
  for c,p in zip(g['children'],o['children']):require((c['ordinal'],c['lengthOffset'],c['bytesOffset'],c['byteLength'],c['ascii'],c['bytesSHA256'])==(p['ordinal'],p['lengthOffset'],p['bytesOffset'],p['byteLength'],p['value'],p['bytesSHA256']),'oracle child mismatch')
 return group

def collect(dest):
 result={'task':'F-03d3s2/MPLEVEL-ACTUAL-HOST-REFERENCE','stage':'sources','toolSHA256':sha(Path(__file__).read_bytes()),**FLAGS}
 try:
  inputs,result['pins']=ingress();result['stage']='pre-execute';save(dest/'pre-execute.private.json',result)
  payload=inputs[TARGET+'/payload.bin'];result['stage']='parse';parsed=parse(payload)
  if parsed['status']!='host-ordered-groups-recovered':
   result.update(status=parsed['status'],cursor=parsed['cursor'],reason=parsed['reason'])
   raise ValueError('unresolved shared empty branch')
  summary=summarize(parsed);group=actual_gate(payload,parsed,summary,json.loads(inputs[ORACLE]))
  result['stage']='inverse';encoded=inverse(parsed);require(encoded==payload,'inverse not byte-identical')
  (dest/'inverse-reencoded.bin').write_bytes(encoded)
  save(dest/'mplevel.private.json',{'status':'actual-MPLevel-group-recovered','group':group,**FLAGS})
  result.update(stage='complete',passed=True,status='host-ordered-groups-recovered',framing={k:v for k,v in parsed.items() if k!='groups'},summary=summary,orderedEntries=parsed['groups'],inverseSHA256=sha(encoded),nativeConsumerEOFCheckProven=False)
  save(dest/'result.private.json',result)
  print(json.dumps({'groups':summary['groupCount'],'children':summary['childCount'],'ordinal':group['ordinal'],'inverseSHA256':sha(encoded)}))
 except Exception as e:
  result.update(passed=False,errorType=type(e).__name__,error=str(e))
  if isinstance(e,ReadError):result.update(cursor=e.cursor,needed=e.needed,remaining=e.remaining)
  save(dest/'failure.private.json',result);raise

def audit(first,second,output):
 names=('pre-execute.private.json','inverse-reencoded.bin','mplevel.private.json','result.private.json')
 copies=[{n:(OUT/d/n).read_bytes() for n in names} for d in (first,second)]
 require(copies[0]==copies[1],'fresh artifacts differ');files=copies[0]
 sources,pins=ingress();payload=sources[TARGET+'/payload.bin'];pre=json.loads(files['pre-execute.private.json']);result=json.loads(files['result.private.json']);world=json.loads(files['mplevel.private.json'])
 require(pre['pins']==result['pins']==pins and pre['toolSHA256']==result['toolSHA256']==sha(Path(__file__).read_bytes()) and pre['stage']=='pre-execute' and result['stage']=='complete' and result['passed'],'source or stage mismatch')
 for k,v in FLAGS.items():require(pre[k]==result[k]==world[k]==v,'scope mismatch')
 framing=result['framing'];groups=result['orderedEntries'];require(framing['status']=='host-ordered-groups-recovered' and framing['prefixOffset']==0 and framing['outerCountOffset']==1,'framing state mismatch')
 require(len(payload)>=5 and payload[0]==framing['prefixRaw'] and ((payload[0]^8)+0x3e)&0xff==framing['prefixDecoded'],'prefix mismatch')
 original_outer=int.from_bytes(payload[1:5],'little');require(original_outer==framing['outerRaw'] and (original_outer+0xf8f43bce)&0xffffffff==framing['outerCount']==len(groups),'outer count mismatch')
 rebuilt=bytearray([((framing['prefixDecoded']-0x3e)&0xff)^8]);rebuilt.extend(((len(groups)-0xf8f43bce)&0xffffffff).to_bytes(4,'little'))
 cursor=5;child_total=empty_keys=empty_children=0;zeros=[];ascii_ok=True;keys=[];matches=[]
 def inspect_field(field,position,ordinal,child):
  nonlocal ascii_ok
  require(field['ordinal']==ordinal and field['lengthOffset']==position and position+2<=len(payload),'length offset mismatch')
  encoded_length=int.from_bytes(payload[position:position+2],'little');width=((encoded_length^0xa88c)+0x665a)&0xffff if child else encoded_length^0x57c0
  begin=position+2;end=begin+width;require(end<=len(payload) and (field['lengthRaw'],field['bytesOffset'],field['byteLength'],field['end'])==(encoded_length,begin,width,end),'string bounds mismatch')
  decoded=bytes.fromhex(field['decodedHex']);require(len(decoded)==width and sha(decoded)==field['bytesSHA256'],'decoded bytes/hash mismatch')
  view=decoded.decode('ascii') if all(x<128 for x in decoded) else None;require(view==field['ascii'],'ASCII view mismatch');ascii_ok &= view is not None
  constant=CHILD_ADD if child else KEY_ADD;inverse_bytes=bytearray()
  for offset in range(0,width,8):
   chunk=decoded[offset:offset+8];mask=(1<<(8*len(chunk)))-1;inverse_bytes.extend(((int.from_bytes(chunk,'little')-constant)&mask).to_bytes(len(chunk),'little'))
  require(bytes(inverse_bytes)==payload[begin:end],'encoded word mismatch')
  raw_len=(((width-0x665a)&0xffff)^0xa88c) if child else width^0x57c0
  rebuilt.extend(raw_len.to_bytes(2,'little'));rebuilt.extend(inverse_bytes)
  return end,decoded
 for ordinal,g in enumerate(groups):
  require(g['ordinal']==ordinal and g['start']==cursor,'group start mismatch')
  cursor,key=inspect_field(g['key'],cursor,0,False);keys.append(key);empty_keys+=not len(key)
  require(g['childCountOffset']==cursor and cursor+4<=len(payload),'child count offset mismatch')
  mraw=int.from_bytes(payload[cursor:cursor+4],'little');m=mraw^0x9cb545fb
  require((g['childCountRaw'],g['childCount'],len(g['children']))==(mraw,m,m),'child count mismatch');rebuilt.extend((m^0x9cb545fb).to_bytes(4,'little'));cursor+=4
  if not m:zeros.append(ordinal)
  found=False
  for j,c in enumerate(g['children']):
   cursor,value=inspect_field(c,cursor,j,True);empty_children+=not len(value);child_total+=1;found|=value==b'MPLevel'
  if found:matches.append(ordinal)
  require(g['end']==cursor and g['recordSHA256']==sha(payload[g['start']:cursor]),'group extent/hash mismatch')
 duplicates=Counter(keys)
 summary={'groupCount':len(groups),'childCount':child_total,'emptyKeys':empty_keys,'emptyChildren':empty_children,'zeroChildOrdinals':zeros,
          'duplicateKeys':[{'decodedHex':k.hex(),'count':v} for k,v in duplicates.items() if v>1], 'allDecodedBytesASCII':ascii_ok,'mplevelOrdinals':matches}
 require(summary==result['summary'] and summary=={'groupCount':707,'childCount':15835,'emptyKeys':0,'emptyChildren':0,'zeroChildOrdinals':[450,461,502,511,575],'duplicateKeys':[],'allDecodedBytesASCII':True,'mplevelOrdinals':[544]},'summary mismatch')
 require(cursor==len(payload)==framing['endOffset'] and framing['trailingBytes']==0 and framing['exactEOF'] and bytes(rebuilt)==files['inverse-reencoded.bin']==payload and result['inverseSHA256']==sha(payload),'inverse/EOF mismatch')
 selected=groups[544];require(world['group']==selected and world['status']=='actual-MPLevel-group-recovered' and selected['key']['ascii']=='Data/_BinOutput/Gadget/ConfigGadget_Scene_World' and [x['ascii'] for x in selected['children']]==EXPECTED_CHILDREN and selected['recordSHA256']=='08d0ca8d1eb5afaf7d998dfb64a2ab3cfbd6d380f6e6c60345eaa53c76d937a1','MPLevel mismatch')
 require(not result['nativeConsumerEOFCheckProven'],'native EOF claim mismatch')
 save(OUT/output,{'passed':True,'freshFilesEqual':True,'currentNativeCalls':0,'parserMainCalled':False,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in files.items()}})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);p.add_argument('--audit',nargs=2);a=p.parse_args()
 require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'fresh basename required');OUT.mkdir(parents=True,exist_ok=True)
 if a.audit:
  require(a.output.endswith('.json') and all(Path(n).name==n for n in a.audit),'invalid audit basename');audit(*a.audit,a.output)
 else:
  d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
