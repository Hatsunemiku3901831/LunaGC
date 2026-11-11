"""Recover one source-bound World parent byte array from one approved block."""
import argparse
import hashlib
import json
import os
import struct
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
REQUEST=ROOT/'local/migration72/d02-resume-20261111c/world-required-config-type-suffix/one-parent-block-payload-request.private.json'
REQUEST_SHA='1064ea668af5daba75758e91fa5ca0e07d66994c20a62f0d949f725c73fd9a28'
PLAN=REQUEST.parent
OUT=ROOT/'local/migration72/d02-resume-20261111c/world-required-parent-payload'
INDEX=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-index/main-third'
HDR=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-header/main-third'
DIRECTORY=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-metadata-prefix/main-third/directory.private.json'
OLDPRE=ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-target/main-third/pre-execute.private.json'
SNAP=ROOT/'local/migration72/d02-active/plain/initialization-archive-elevated6.json'
PREFIX_END=25777971
FILES=('pre-execute.private.json','physical-prefix.bin','block-761.raw.bin','block-761.transformed.bin','block-761.output.bin','block-761.proof.private.json','parent-object.bin','parent-payload.bin','parent-tail.bin','result.private.json')


def require(ok,msg):
    if not ok:raise ValueError(msg)


def sha(data):return hashlib.sha256(data).hexdigest()


def checked(path,size,digest):
    h=hashlib.sha256();n=0
    with path.open('rb') as f:
        while chunk:=f.read(1048576):h.update(chunk);n+=len(chunk)
    require(n==size and h.hexdigest()==digest,'pin mismatch: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}


def save(path,data):
    if not isinstance(data,bytes):data=(json.dumps(data,sort_keys=True,indent=2,ensure_ascii=False)+'\n').encode('utf8')
    with path.open('xb') as f:f.write(data)


def selected_entry(raw,entry):
    part=raw[entry['start']:entry['endExclusive']]
    require(len(part)==32,'index entry extent differs')
    size,name,file_id,path_id,file_size=struct.unpack('<I8siQQ',part)
    require((size,name,file_id,path_id,file_size)==(8,b'54c32b51',0,14619,3266),'index entry bytes differ')
    return part


def ingress():
    checked(REQUEST,14181,REQUEST_SHA);req=json.loads(REQUEST.read_bytes());pins={}
    def pin(path,p):
        record={'bytes':p['bytes'],'sha256':p['sha256']}
        if path in pins:require(pins[path]==record,'conflicting pin '+path)
        else:pins[path]=checked(ROOT/path,record['bytes'],record['sha256'])
    for name,p in req['fixedPlanningArtifacts'].items():pin(str((PLAN/name).relative_to(ROOT)).replace('\\','/'),p)
    binding=json.loads((PLAN/'parent-body-binding.private.json').read_bytes())
    for group in (req['ingress']['frozenDependencyPins'],binding['pins']):
        for path,p in group.items():pin(path,p)
    old=json.loads(OLDPRE.read_bytes())
    for path,p in old['pins'].items():pin(path,p)
    require(old['stage']=='pre-execute' and old['nativePrefixCount']==0 and len(old['allowed'])==29 and old['allowed']==binding['existingPrefixAllowlist'],'old probe provenance differs')
    require(not any(int(x['begin'],16)<=0x1405336c0<int(x['endExclusive'],16) for x in old['allowed']),'refused allocator is in allowlist')
    override=os.environ.get('LIBUNICORN_PATH');require(not override or Path(override).resolve()==(ROOT/'local/migration72/python/unicorn/lib').resolve(),'unreviewed Unicorn override')
    profile_bytes=(ROOT/'protocol/cn-7.2/profile.json').read_bytes();profile=json.loads(profile_bytes)['files']
    stock={v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile.values()}
    require(stock==req['ingress']['stock']==old['stock']==binding['stock'],'profile stock differs')
    source=req['ingress']['physical'];require(source==old['physical']==binding['physical'],'physical identity differs')
    query=json.loads((PLAN/'parent-resource-query.private.json').read_bytes());target=req['target']
    text=target['parentPath']+target['suffix'];chars=text.encode('utf-16le')[::2];require(len(chars)==61 and len(chars)<=256,'hash input differs')
    digest=hashlib.md5(chars+bytes(256-len(chars))).digest()
    require(query['path']==target['parentPath'] and query['suffix']==target['suffix'] and query['keyHex']==target['keyHex'] and int.from_bytes(digest[:8],'little')==int(target['keyHex'],16) and digest.hex()==target['md5Hex']==query['md5Hex'],'parent full-key identity differs')
    hits=query['hits'];require(len(hits)==1 and hits[0]['index']==target['index'] and hits[0]['key']==int(target['keyHex'],16) and hits[0]['variants']==[[0,1]] and hits[0]['recordSHA256']==target['assetRecordSHA256'] and hits[0]['locationBindings'][0]['matches']==[target['location']],'asset record association differs')
    raw_index=(INDEX/'index-object.bin').read_bytes();index=json.loads((INDEX/'index.private.json').read_bytes());entry=target['entry'];part=selected_entry(raw_index,entry)
    matches=[r for r in index['records'] if r['name']==entry['name']]
    require(matches==[entry] and entry==binding['entry'] and part.hex()==binding['entryRecordHex'] and sha(part)==binding['entryRecordSHA256'] and target['indexClassID']==binding['indexClassID']==1210,'index association differs')
    directory=json.loads(DIRECTORY.read_bytes());obj=target['targetObject'];matches=[o for o in directory['objects'] if o['pathID']==entry['pathID']]
    require(matches==[obj] and obj==binding['targetObject'] and obj['classID']==1208 and obj['typeIndex']==2 and obj['bytes']==3272 and obj['pathID']==14619 and directory['engine']==binding['metadataEngine'],'directory association differs')
    plans=directory['indexPlans'];require(len(plans)==1 and plans[0]['object']['classID']==1210 and plans[0]['object']['typeIndex']==1 and plans[0]['object']['bytes']==len(raw_index),'index class/source differs')
    require(directory['types'][1]['classID']==1210 and directory['types'][2]['classID']==1208 and directory['types'][2]['typeHash']==obj['typeHash'],'metadata class/type differs')
    meta_audit=json.loads((ROOT/'local/migration72/d02-resume-20261111b/scene-gadget-bin-metadata-prefix/audit-third.private.json').read_bytes())
    require(meta_audit['passed'] and meta_audit['freshFilesEqual'] and meta_audit['currentNativeCalls']==0,'metadata audit differs')
    prefix_path='local/migration72/d02-resume-20261111b/scene-gadget-bin-metadata-prefix/main-third/actual-prefix.bin';pin(prefix_path,meta_audit['artifacts']['actual-prefix.bin'])
    metadata_prefix=(ROOT/prefix_path).read_bytes();pid,relative,size,type_index=struct.unpack_from('<qIIi',metadata_prefix,obj['recordOffset'])
    require((pid,relative,size,type_index)==(obj['pathID'],obj['relativeStart'],obj['bytes'],obj['typeIndex']) and directory['dataOffset']+relative==obj['start'],'original metadata object row differs')
    from tools.client72_resource_blob import parse_header
    decoded=(HDR/'decoded-header.bin').read_bytes();info=parse_header(decoded,source['bytes'],len(decoded));header=json.loads((HDR/'header.private.json').read_bytes())
    require(info==header['header']['parsed'] and all(info[k]==v for k,v in binding['header'].items()),'header differs')
    block=req['bounds']['newBlocks'][0];i=block['index'];require(len(req['bounds']['newBlocks'])==1 and i==761 and block==binding['block'],'block identity differs')
    require((info['dataOffset']+info['compressedEnds'][i-1],info['dataOffset']+info['compressedEnds'][i],i*info['blockBytes'],(i+1)*info['blockBytes'])==(block['rawBegin'],block['rawEndExclusive'],block['outputBegin'],block['outputEndExclusive']),'block range differs')
    require((block['rawBegin'],block['rawEndExclusive'],block['rawBytes'],block['outputBytes'])==(25741681,25777971,36290,131072) and 0<=obj['start']-block['outputBegin']<obj['endExclusive']-block['outputBegin']<=block['outputBytes'] and (obj['start']-block['outputBegin'],obj['endExclusive']-block['outputBegin'])==(34592,37864),'object range differs')
    return req,pins,profile_bytes,profile,stock,source,decoded,info,old['allowed'],entry,obj


def physical_prefix(path,source):
    h=hashlib.sha256();m=hashlib.md5();n=0;prefix=bytearray()
    with path.open('rb') as f:
        while chunk:=f.read(1048576):
            if n<PREFIX_END:prefix.extend(chunk[:PREFIX_END-n])
            h.update(chunk);m.update(chunk);n+=len(chunk)
    require(n==source['bytes'] and h.hexdigest()==source['sha256'] and m.hexdigest()==source['md5'],'physical source drift')
    require(len(prefix)==PREFIX_END and prefix[:13737]==(HDR/'header-prefix.bin').read_bytes(),'physical prefix/header differs')
    return bytes(prefix)


def extract(output,entry,obj,block):
    require(len(output)==131072,'block output extent differs')
    begin=obj['start']-block['outputBegin'];end=obj['endExclusive']-block['outputBegin']
    require((begin,end)==(34592,37864),'object boundary differs')
    raw=output[begin:end];require(len(raw)==3272,'short object')
    count=struct.unpack_from('<I',raw)[0];require(count==entry['fileSize']==3266 and count+4+2==len(raw),'byte array count differs')
    payload=raw[4:4+count];tail=raw[4+count:]
    return raw,payload,tail,{'count':count,'countRawHex':raw[:4].hex(),'objectBytes':len(raw),'objectSHA256':sha(raw),'payloadBytes':len(payload),'payloadSHA256':sha(payload),'tailBytes':len(tail),'tailSHA256':sha(tail),'tailHex':tail.hex()}



def verify_prefix_proof(proof):
    run=proof['run'];observed=proof['observedExecution']
    require(proof['al']==1 and proof['guardsIntact'] and proof['tailUnchanged'] and proof['protectedWriteEvents']==0 and proof['headerStorageProtectedBytes']==13725 and not run['modeledCalls'] and not run['memoryModels'] and run['failure'] is None and run['error'] is None and run['stop']=='0x620000000' and observed['steps']==run['steps'] and observed['steps']<=100000 and observed['callEdges']==proof['callEdges'],'prefix guard/stop/budget differs')


def collect(dest):
    result={'task':'F-03d3s2/WORLD-REQUIRED-PARENT-ONE-BLOCK-PAYLOAD','stage':'sources','toolSHA256':sha(Path(__file__).read_bytes()),'nativePrefixCount':0,'independentLZ4Count':0,'targetNativeCodecExecuted':False,'fullArchiveDecoded':False,'nativeReaderOrFactoryCalls':0,'WorldSlotCalls':0,'liveReads':0,'MPLevelRecovered':False,'naturalReaderReady':False,'SceneReady':False,'D02Ready':False}
    probe=None;events={'steps':0,'callEdges':[],'writes':[],'pending':None}
    try:
        req,pins,pb,profile,stock,source,decoded,info,allowed,entry,obj=ingress();result['pins']=pins;result['stage']='stock'
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        result['stock']={p:checked(game/p,v['bytes'],v['sha256']) for p,v in stock.items()};result['stage']='physical'
        prefix=physical_prefix(game/source['path'],source);block=req['bounds']['newBlocks'][0];raw=prefix[block['rawBegin']:block['rawEndExclusive']]
        require(len(raw)==36290,'short selected raw block');save(dest/'physical-prefix.bin',prefix);save(dest/'block-761.raw.bin',raw)
        from tools.client72_resource_provider import Snapshot,u64
        context=Snapshot(ROOT/SNAP,profile,sha(pb));pointer=u64(context.at('archiveDecodeTable.cell',context.base+0x5eed350,8));key=context.at('archiveDecodeTable.bytes',pointer,256)
        require(sha(key)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','archive table differs')
        from tools.migration72.scene_mplevel_actual_gadget_bin_metadata import make_probe,is_call
        from tools.client72_resource_payload import lz4_block
        binary=(game/'YuanShen.exe').read_bytes();require(len(binary)==stock['YuanShen.exe']['bytes'] and sha(binary)==stock['YuanShen.exe']['sha256'],'PE source drift')
        result['currentPEReadLedger']={'stockHashBytes':stock['YuanShen.exe']['bytes'],'probeBinaryBytes':len(binary),'probeBinarySHA256':sha(binary),'newCalleeWindows':0}
        probe=make_probe(binary,key);engine=probe.engine
        require(not engine.models and [{'begin':hex(a),'endExclusive':hex(b)} for a,b in engine.allowed]==allowed,'probe allowlist/model differs')
        result.update(stage='pre-execute',physical=source,physicalPrefix={'bytes':len(prefix),'sha256':sha(prefix)},selectedBlock=block,entry=entry,object=obj,archiveTableSHA256=sha(key),allowed=allowed)
        save(dest/'pre-execute.private.json',result)
        def observe(uc,pc,size,user):
            events['steps']+=1
            if events['steps']>100000:
                engine.failure='instruction budget exceeded 100000';uc.emu_stop();return
            if events['pending'] is not None:events['callEdges'].append({'site':hex(events['pending'][0]),'kind':events['pending'][1],'target':hex(pc)})
            kind=is_call(bytes(uc.mem_read(pc,size)));events['pending']=(pc,kind) if kind else None
        def written(uc,access,address,size,value,user):events['writes'].append({'address':hex(address),'size':size})
        h1=engine.uc.hook_add(engine.U.UC_HOOK_CODE,observe);h2=engine.uc.hook_add(engine.U.UC_HOOK_MEM_WRITE,written)
        result['stage']='block-761-prefix';result['nativePrefixCount']=1
        try:transformed,_=probe.transform({'parsed':info,'decodedHeaderHex':decoded.hex()},prefix,761)
        finally:
            engine.uc.hook_del(h1);engine.uc.hook_del(h2)
            result['observedExecution']={'steps':events['steps'],'callEdges':events['callEdges'],'writes':events['writes'],'lastEngineRun':probe.runs[-1] if probe.runs else None}
        require(events['steps']<=100000 and probe.last is not None,'instruction budget or prefix result differs')
        save(dest/'block-761.transformed.bin',transformed);require(len(transformed)==len(raw) and transformed[128:]==raw[128:],'transformed tail differs')
        proof={'index':761,'rawSHA256':sha(raw),'transformedSHA256':sha(transformed),'tailSHA256':sha(raw[128:]),'changedPrefixBytes':sum(a!=b for a,b in zip(raw[:128],transformed[:128])),'observedExecution':result['observedExecution'],**probe.last}
        verify_prefix_proof(proof)
        result['prefixProof']=proof
        result['stage']='single-lz4';result['independentLZ4Count']=1;out=lz4_block(transformed,131072);require(len(out)==131072,'decoded length differs');save(dest/'block-761.output.bin',out)
        proof['outputSHA256']=sha(out);save(dest/'block-761.proof.private.json',proof)
        result['stage']='object-and-count';objraw,payload,tail,details=extract(out,entry,obj,block)
        save(dest/'parent-object.bin',objraw);save(dest/'parent-payload.bin',payload);save(dest/'parent-tail.bin',tail)
        result.update(stage='complete',passed=True,blockProof=proof,byteArray=details)
        save(dest/'result.private.json',result);print(json.dumps({'payloadBytes':len(payload),'payloadSHA256':sha(payload),'tailHex':tail.hex(),'steps':events['steps']}))
    except Exception as e:
        result.update(passed=False,errorType=type(e).__name__,error=str(e));save(dest/'failure.private.json',result);raise
    finally:
        if probe is not None:probe.close()


def audit(first,second,output):
    copies=[{name:(p/name).read_bytes() for name in FILES} for p in (first,second)];require(copies[0]==copies[1],'fresh artifacts differ');f=copies[0]
    req,pins,pb,profile,stock,source,decoded,info,allowed,entry,obj=ingress()
    pre=json.loads(f['pre-execute.private.json']);result=json.loads(f['result.private.json']);proof=json.loads(f['block-761.proof.private.json']);block=req['bounds']['newBlocks'][0]
    require(pre['stage']=='pre-execute' and pre['nativePrefixCount']==pre['independentLZ4Count']==0 and pre['pins']==result['pins']==pins and pre['stock']==result['stock']==stock and pre['allowed']==allowed and pre['physical']==source and pre['selectedBlock']==block and pre['entry']==entry and pre['object']==obj,'pre-execute/source differs')
    prefix=f['physical-prefix.bin'];require(len(prefix)==PREFIX_END and sha(prefix)==pre['physicalPrefix']['sha256'] and prefix[:13737]==(HDR/'header-prefix.bin').read_bytes(),'saved source prefix differs')
    raw=f['block-761.raw.bin'];transformed=f['block-761.transformed.bin'];out=f['block-761.output.bin']
    require(raw==prefix[block['rawBegin']:block['rawEndExclusive']] and len(raw)==36290 and len(transformed)==len(raw) and transformed[128:]==raw[128:],'block source/tail differs')
    from tools.client72_resource_payload import lz4_block
    require(lz4_block(transformed,131072)==out and len(out)==131072,'independent LZ4 differs')
    objraw,payload,tail,details=extract(out,entry,obj,block)
    require(objraw==f['parent-object.bin'] and payload==f['parent-payload.bin'] and tail==f['parent-tail.bin'] and details==result['byteArray'],'object/count/tail differs')
    require(proof==result['blockProof'] and proof['rawSHA256']==sha(raw) and proof['transformedSHA256']==sha(transformed) and proof['outputSHA256']==sha(out) and proof['tailSHA256']==sha(raw[128:]) and proof['changedPrefixBytes']==sum(a!=b for a,b in zip(raw[:128],transformed[:128])),'prefix proof differs')
    verify_prefix_proof(proof)
    require(result['stage']=='complete' and result['passed'] and result['nativePrefixCount']==result['independentLZ4Count']==1 and result['toolSHA256']==pre['toolSHA256']==sha(Path(__file__).read_bytes()) and not any(result[k] for k in ('targetNativeCodecExecuted','fullArchiveDecoded','nativeReaderOrFactoryCalls','WorldSlotCalls','liveReads','MPLevelRecovered','naturalReaderReady','SceneReady','D02Ready')),'scope differs')
    report={'passed':True,'twoFreshAllFilesByteIdentical':True,'currentPEReads':0,'currentNativeCalls':0,'savedPhysicalPrefixOnly':True,'fullPhysicalIdentityRehashedInAudit':False,'artifacts':{name:{'bytes':len(data),'sha256':sha(data)} for name,data in f.items()},'byteArray':details}
    save(output,report);print(json.dumps({'passed':True,'payloadBytes':len(payload),'payloadSHA256':sha(payload),'tailHex':tail.hex()}))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True);parser.add_argument('--audit',nargs=2);args=parser.parse_args()
    require(Path(args.output).name==args.output and not (OUT/args.output).exists(),'new task-local basename required')
    OUT.mkdir(parents=True,exist_ok=True)
    if args.audit:
        require(args.output.endswith('.json') and all(Path(x).name==x for x in args.audit),'audit inputs must be basenames')
        audit(*(OUT/x for x in args.audit),OUT/args.output)
    else:
        dest=OUT/args.output;dest.mkdir();collect(dest)

if __name__=='__main__':main()
