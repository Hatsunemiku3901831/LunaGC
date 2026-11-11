"""Verify only the approved complete ConfigMeta blob header; no payload decode."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
import time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-complete-header'
REQUEST='local/migration72/d02-resume-20261111b/scene-header-conflict/complete-blob-source-request.private.json'
REQUEST_SHA='7d7d37c45796ada9770f0a5e1a6ed19f4a3bb6ab727861ccd7818b09e903b82f'

def require(ok,message):
    if not ok: raise ValueError(message)

def digest(raw): return hashlib.sha256(raw).hexdigest()

def check(path,size,sha):
    h=hashlib.sha256(); count=0
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b); count+=len(b)
    require(count==size and h.hexdigest()==sha,'source drift: '+path.name)
    return {'bytes':count,'sha256':h.hexdigest()}

def save(path,obj):
    with path.open('xb') as f:f.write((json.dumps(obj,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())

def location_gate(rows,expected):
    require(rows==[tuple(x) for x in expected['allSameBlockRows']], 'same-block location witness drift')
    target=tuple(expected['targetLocation']); following=tuple(expected['followingLocation'])
    require(target in rows and following in rows and rows.index(following)==rows.index(target)+1 and following[2]==target[2]+284563,'next-location witness mismatch')

def prefix_gate(blob,prefix):
    require(len(blob)==284563 and len(prefix)==212203 and blob[:212203]==prefix and digest(prefix)=='2033c3659731308aa208d4e3ac9ee52e0b9df1396a5d0be32a3b5f5971859bb0','blob length or old prefix mismatch')

def parsed_gate(parsed,expected):
    for k in ('logicalBlobBytes','dataOffset','headerBytes','compressionTag','blockBytes','blockCount','lastBlockBytes','uncompressedBytes'):
        require(parsed[k]==expected[k], 'header mismatch: '+k)
    require(parsed['fileTailBytes']==0 and parsed['compressedEnds']==[212058,284418], 'header tail/ends mismatch')
    require(len(parsed['content'])==1 and {k:parsed['content'][0][k] for k in ('name','size','offset','flag')}=={'name':expected['content']['name'],'size':expected['content']['bytes'],'offset':0,'flag':True},'content mismatch')

def decode_header(binary,key,blob,old_header,expected):
    from tools.client72_resource_blob import BlobProbe,parse_header
    p=BlobProbe(binary,ROOT,key);e=p.engine
    try:
        n=struct.unpack_from('<I',blob,4)[0]
        require(blob[:12]==bytes.fromhex('426c62037500000005000000') and n==117,'signature mismatch')
        sig=e.alloc(12);e.uc.mem_write(sig,blob[:12]);h=e.alloc(64);storage=e.alloc(n+48);buf=storage+16
        e.uc.mem_write(storage,b'\xa5'*(n+48));e.write(h+16,buf);e.write(h+40,n+16);out=e.alloc(8);size=e.alloc(8)
        value,check_run,_=p.call(0x1415aa740,dict(rcx=sig));require(value&255==1,'signature rejected')
        value,prepare,_=p.call(0x1415aa760,dict(rcx=h,rdx=sig,r8=out,r9=size));require(value&255==1 and e.read(out)==buf and e.read(size)==n+16,'prepare failed')
        e.uc.mem_write(buf,blob[12:n+28]);value,transform,visited=p.call(0x1415aa820,dict(rcx=h))
        decoded=bytes(e.uc.mem_read(buf+16,n))
        require(value&255==1 and decoded==old_header and digest(decoded)==expected['decodedSHA256'] and bytes(e.uc.mem_read(buf,16))==blob[12:28] and bytes(e.uc.mem_read(storage,16))==b'\xa5'*16 and bytes(e.uc.mem_read(buf+n+16,16))==b'\xa5'*16 and e.read(h+56)==buf+16 and e.read(h+48)==buf and 0x141cc06c0 in visited,'header transform identity/guard/cookie mismatch')
        parsed=parse_header(decoded,len(blob),n);parsed_gate(parsed,expected)
        getters=p.check_getters(h,parsed)
        require(len(getters)==14 and not any(r['modeledCalls'] or r['memoryModels'] for r in [check_run,prepare,transform]+[x['run'] for x in getters]),'getter/model mismatch')
        return decoded,{'decodedSHA256':digest(decoded),'parsed':parsed,'runs':[check_run,prepare,transform],'nativeGetters':getters,'nativeGetterCount':len(getters),'canaries':True,'ivPreserved':True,'cookieCheck':True}
    finally:p.close()

def collect(dest):
    started=time.monotonic()
    result={'task':'F-03d3s2/CONFIGMETA-COMPLETE-HEADER','stage':'sources','payloadTransform':False,'codecExecution':False,'clientExecuted':False}
    try:
        check(ROOT/REQUEST,9260,REQUEST_SHA); req=json.loads((ROOT/REQUEST).read_bytes())
        result['sources']={x['path']:check(ROOT/x['path'],x['bytes'],x['sha256']) for x in req['sources']}
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        result['stock']={p:check(game/p,x['bytes'],x['sha256']) for p,x in req['stock'].items()}
        profile_bytes=(ROOT/'protocol/cn-7.2/profile.json').read_bytes();profile=json.loads(profile_bytes)['files']
        require({x['path']:{'bytes':x['size'],'sha256':x['sha256']} for x in profile.values()}==req['stock'],'profile mismatch')
        rowraw=(ROOT/req['sources'][0]['path']).read_bytes()
        rows=[r for r in struct.iter_unpack('<6i',rowraw) if r[0]==2455671]
        location_gate(rows,req['exactNewInput']['sourceBasis'])
        source=req['sourceFile'];path=game/source['path'];check(path,source['bytes'],source['sha256'])
        md5=hashlib.md5()
        with path.open('rb') as f:
            for b in iter(lambda:f.read(1024*1024),b''):md5.update(b)
        require(md5.hexdigest()==source['md5'],'source MD5 mismatch')
        with path.open('rb') as f:
            f.seek(308700);blob=f.read(284563)
        prefix=(ROOT/req['sources'][7]['path']).read_bytes();prefix_gate(blob,prefix)
        old_header=(ROOT/req['sources'][8]['path']).read_bytes()
        sys.path.insert(0,str(ROOT))
        from tools.client72_resource_provider import Snapshot,u64
        from tools.client72_resource_blob import parse_header
        context=Snapshot(ROOT/req['sources'][3]['path'],profile,digest(profile_bytes))
        pointer=u64(context.at('archiveDecodeTable.cell',context.base+0x5eed350,8));key=context.at('archiveDecodeTable.bytes',pointer,256)
        require(digest(key)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','context table mismatch')
        (dest/'blob.bin').write_bytes(blob);(dest/'new-tail.bin').write_bytes(blob[212203:])
        result.update(stage='pre-decode',locationRows=[list(x) for x in rows],blob={'bytes':len(blob),'sha256':digest(blob)},newTail={'bytes':len(blob)-len(prefix),'sha256':digest(blob[len(prefix):])},prefixEqual=True,sourceSHA256=source['sha256'],contextTableSHA256=digest(key))
        save(dest/'pre-decode.private.json',result)
        require(time.monotonic()-started<60,'time budget expired before header')
        binary=(game/'YuanShen.exe').read_bytes();result['stage']='header'
        decoded,report=decode_header(binary,key,blob,old_header,req['headerExpected'])
        require(time.monotonic()-started<60,'time budget exceeded')
        # Preserve unchanged parser rejections on both original and one-byte-short inputs.
        for length in (212203,284562):
            try: parse_header(decoded,length,117)
            except ValueError as e: require(str(e)=='Blob or tail length is outside the actual file','unexpected short-input failure')
            else: raise ValueError('short input unexpectedly accepted')
        (dest/'decoded-header.bin').write_bytes(decoded)
        result.update(stage='complete-header',passed=True,header=report,shortInputsRejected=[212203,284562],codecStopped=9)
        save(dest/'header.private.json',result)
        print(json.dumps({'stage':result['stage'],'blobSHA256':digest(blob),'tailSHA256':digest(blob[212203:]),'getters':14}))
    except Exception as e:
        result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);a=p.parse_args()
    require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'new task-local basename required')
    OUT.mkdir(parents=True,exist_ok=True);d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
