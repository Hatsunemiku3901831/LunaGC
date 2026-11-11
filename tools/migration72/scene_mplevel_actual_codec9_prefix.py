"""Bounded original prefix transform of two fixed compressed ConfigMeta blocks."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-codec9-prefix'
REQUEST='local/migration72/d02-resume-20261111b/scene-header-conflict/codec9-prefix-only-request.private.json'
REQUEST_SHA='42364f5b71128ae356940f6165d5bcb3b77718e1955266dcde3b3ffb9b5c5abf'
ENTRY,END=0x1415aa8c0,0x1415aa911
FORBIDDEN={0x1415a1ed0,0x1415c1ea0,0x1415c1700,0x1415c1810,0x141c802e0,0x141c80580}

def require(ok,message):
    if not ok:raise ValueError(message)

def sha(data):return hashlib.sha256(data).hexdigest()

def check(path,size,expected):
    h=hashlib.sha256();n=0
    with path.open('rb') as f:
        for data in iter(lambda:f.read(1024*1024),b''):h.update(data);n+=len(data)
    require(n==size and h.hexdigest()==expected,'source drift: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}

def save(path,data):
    with path.open('xb') as f:f.write((json.dumps(data,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())

def input_gate(blob,decoded,report,request):
    require(len(blob)==284563 and sha(blob)==request['fixedInputs'][next(x for x in request['fixedInputs'] if x.endswith('/blob.bin'))]['sha256'],'blob mismatch')
    require(len(decoded)==117 and sha(decoded)=='207d6cdc9f26656a1a9ff05ad4b2087102fd826697045f1f40fffa38d4a5a741','header mismatch')
    from tools.client72_resource_blob import parse_header
    info=parse_header(decoded,len(blob),117)
    require(report['passed'] and report['header']['nativeGetterCount']==14 and report['header']['parsed']==info,'header report mismatch')
    require(info['compressionTag']==9 and info['blockCount']==2 and info['compressedEnds']==[212058,284418] and info['dataOffset']==145 and info['uncompressedBytes']==334996 and info['fileTailBytes']==0,'parsed header mismatch')
    for row in request['blocks']:
        raw=blob[row['blobStart']:row['blobEndExclusive']]
        require(len(raw)==row['bytes'] and sha(raw)==row['rawSHA256'] and sha(raw[128:])==row['tailAfter128SHA256'],'raw block mismatch')
    return {'parsed':info,'decodedHeaderHex':decoded.hex()}

def index_gate(index):
    require(index in (0,1),'index outside two fixed blocks')

def is_call(raw):
    i=0
    while i<len(raw) and (raw[i] in (0x66,0x67,0xf2,0xf3) or 0x40<=raw[i]<=0x4f):i+=1
    if i<len(raw) and raw[i]==0xe8:return 'direct'
    if i+1<len(raw) and raw[i]==0xff and (raw[i+1]>>3)&7 in (2,3):return 'indirect'
    return None

def intersects(address,size,regions):
    return any(address<end and address+size>start for start,end in regions)

def make_probe(binary,key):
    from tools.client72_resource_blob import BlobProbe
    from tools.client72_resource_payload import PayloadProbe
    class PrefixOnly(BlobProbe):
        setup=PayloadProbe.setup
        transform=PayloadProbe.transform
        def __init__(self):
            super().__init__(binary,ROOT,key)
            e=self.engine
            require(not e.models,'models not empty')
            require(e.unwind_ranges.get(ENTRY)==END,'prefix pdata extent mismatch')
            pos=e.pe.offset(ENTRY,END-ENTRY,executable=True)
            require(sha(binary[pos:pos+END-ENTRY])=='93ee6859b3c44f6dc8a7846ed5b4552f2edc3e99d49450b73e485566931ac18f','prefix code mismatch')
            e.allowed.append((ENTRY,END));self.owned={};self.scratch=e.heap_cursor;self.last=None
        def call(self,address,registers,stack=(),stops=()):
            require(address==ENTRY and not stack and not stops,'unapproved call')
            e=self.engine;header=registers['rcx'];ptr=registers['rdx'];n=registers['r8'];storage=e.read(header+16);table=e.read(0x145eed350)
            before_archive=bytes(e.uc.mem_read(header-0xa8,0x138));before_storage=bytes(e.uc.mem_read(storage,133));before_table=bytes(e.uc.mem_read(table,256));before_input=bytes(e.uc.mem_read(ptr-16,n+32))
            allowed=tuple(e.allowed);targets=[hex(address)];pending=[None];edges=[];writes=[0]
            protected=((ptr-16,ptr),(ptr+128,ptr+n+16),(header-0xa8,header-0xa8+0x138),(storage,storage+133),(table,table+256))
            def inspect(uc,pc,size,user):
                if pc in FORBIDDEN or not any(a<=pc<z for a,z in allowed):
                    e.failure='unexpected native entry '+hex(pc);uc.emu_stop();return
                if pending[0]:
                    targets.append(hex(pc));edges.append({'site':hex(pending[0][0]),'kind':pending[0][1],'target':hex(pc)})
                kind=is_call(bytes(uc.mem_read(pc,size)));pending[0]=(pc,kind) if kind else None
            def inspect_write(uc,access,location,size,value,user):
                if intersects(location,size,protected):
                    e.failure='write to protected fixture '+hex(location);uc.emu_stop();return
                if intersects(location,size,((ptr,ptr+128),)):writes[0]+=1
            hook=e.uc.hook_add(e.U.UC_HOOK_CODE,inspect)
            write_hook=e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE,inspect_write)
            try: value,run,visited=super().call(address,registers,stack,stops)
            finally:e.uc.hook_del(write_hook);e.uc.hook_del(hook)
            after=bytes(e.uc.mem_read(ptr-16,n+32))
            require(value&255==1 and not run['modeledCalls'] and not run['memoryModels'] and run['failure'] is None and run['error'] is None and run['stop']=='0x620000000','prefix return/model mismatch')
            require(after[:16]==before_input[:16]==b'\xa5'*16 and after[-16:]==before_input[-16:]==b'\xa5'*16 and after[144:-16]==before_input[144:-16],'input guard/tail changed')
            require(bytes(e.uc.mem_read(header-0xa8,0x138))==before_archive and bytes(e.uc.mem_read(storage,133))==before_storage and bytes(e.uc.mem_read(table,256))==before_table,'header/table fixture changed')
            require(not any(int(x,16) in FORBIDDEN for x in targets),'forbidden codec target')
            self.last={'al':value&255,'targets':targets,'callEdges':edges,'prefixWriteEvents':writes[0],'protectedWriteEvents':0,'guardsIntact':True,'tailUnchanged':True,'headerUnchanged':True,'tableUnchanged':True,'run':run}
            return value,run,visited
    return PrefixOnly()

def collect(dest):
    result={'task':'F-03d3s2/CONFIGMETA-CODEC9-PREFIX-ONLY','stage':'source-gate','prefixTransformed':False,'payloadDecoded':False,'codecExecuted':False,'oodleStateExecuted':False,'externalDecoderExecuted':False,'serializedParsed':False,'naturalLoaderReady':False,'sceneReady':False,'D02Ready':False}
    try:
        check(ROOT/REQUEST,10031,REQUEST_SHA);req=json.loads((ROOT/REQUEST).read_bytes())
        result['inputs']={p:check(ROOT/p,x['bytes'],x['sha256']) for p,x in req['fixedInputs'].items()}
        result['tools']={p:check(ROOT/p,x['bytes'],x['sha256']) for p,x in req['sourcePins'].items()}
        check(ROOT/req['profile']['path'],req['profile']['bytes'],req['profile']['sha256'])
        paths=list(req['fixedInputs']);blob=(ROOT/paths[0]).read_bytes();report=json.loads((ROOT/paths[1]).read_bytes());decoded=(ROOT/paths[2]).read_bytes()
        profile_bytes=(ROOT/req['profile']['path']).read_bytes();profile=json.loads(profile_bytes)['files']
        stock={v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile.values()};require(stock==report['stock'],'stock profile mismatch')
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        result['stock']={p:check(game/p,x['bytes'],x['sha256']) for p,x in stock.items()}
        sys.path.insert(0,str(ROOT))
        from tools.client72_resource_provider import Snapshot,u64
        adapter=input_gate(blob,decoded,report,req)
        snapshot=Snapshot(ROOT/paths[4],profile,sha(profile_bytes));pointer=u64(snapshot.at('archiveDecodeTable.cell',snapshot.base+0x5eed350,8));key=snapshot.at('archiveDecodeTable.bytes',pointer,256)
        require(sha(key)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','snapshot table mismatch')
        binary=(game/'YuanShen.exe').read_bytes();p=make_probe(binary,key)
        try:
            result.update(stage='pre-execute',archiveTableSHA256=sha(key),nativePrefix={'entry':hex(ENTRY),'end':hex(END),'bytes':81,'sha256':req['nativePrefix']['codeSHA256']},allowList=[{'begin':hex(a),'end':hex(z)} for a,z in p.engine.allowed],blocks=req['blocks'])
            save(dest/'pre-execute.private.json',result)
            rows=[]
            for item in req['blocks']:
                i=item['index'];index_gate(i);raw=blob[item['blobStart']:item['blobEndExclusive']]
                result['stage']='prefix-'+str(i)
                transformed,run=p.transform(adapter,blob,i)
                require(len(transformed)==len(raw) and transformed[128:]==raw[128:] and sha(transformed[128:])==item['tailAfter128SHA256'],'transformed extent/tail mismatch')
                (dest/('transformed-%d.bin'%i)).write_bytes(transformed)
                rows.append({'index':i,'bytes':len(transformed),'rawSHA256':sha(raw),'transformedSHA256':sha(transformed),'tailAfter128SHA256':sha(transformed[128:]),'changedPrefixBytes':sum(a!=b for a,b in zip(raw[:128],transformed[:128])),'first64Hex':transformed[:64].hex(),**p.last})
            result.update(stage='complete',prefixTransformed=True,outputs=rows)
            save(dest/'result.private.json',result)
            print(json.dumps({'stage':'complete','outputs':[{'index':r['index'],'sha256':r['transformedSHA256']} for r in rows]}))
        finally:p.close()
    except Exception as e:
        result.update(errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True);a=p.parse_args()
    require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'new task-local basename required')
    OUT.mkdir(parents=True,exist_ok=True);d=OUT/a.output;d.mkdir();collect(d)
if __name__=='__main__':main()
