"""Bounded offline locator for the exact ConfigMeta resource segment."""
import argparse
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-locator'
SPEC = 'local/migration72/d02-resume-20261111b/scene/configmeta-locator-spec.zh.md'
PINS = {
 SPEC: (10929,'908a7c9ab8de67853b95c1b35fb37cdb859212b37eb63213ccbd508ef1811881'),
 'protocol/cn-7.2/profile.json': (1453,'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'),
 'local/migration72/d02-resume-20261111b/scene/configmeta-m177845-literal.private.json': (2438,'b8f3d7b1a6053c541033a79116fe1f4f63785cb00613d667b4a42fa868854b1e'),
 'local/migration72/d02-resume-20261111b/scene-holder-window/fresh1/body.bin': (141,'f62abe553b1ce6225660e55d5b8eb40ad239f5b36c94f225ebfc1468abbee14f'),
 'local/migration72/d02-resume-20261111b/scene-holder-window/fresh1/body.asm.private.json': (5649,'0865d5a8f142c92527439953bc24588bcbd46f5b65d121d27c43407498deed35'),
 'local/migration72/d02-resume-20261111b/scene-holder-window/fresh1/extraction.private.json': (5101,'49fa229f5130f5a0c8b6a5ca541b891b782eb20e29d4acd25862da9aaa5fab65'),
 'local/migration72/d02-active/main/payload-index-third.json': (92384,'e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9'),
 'local/migration72/d02-active/main/locator-third.json': (1452522,'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda'),
 'local/migration72/d02-active/resource-payload-astra/payload-final-1.json': (196804,'efd152bc8e7337ab5af6aa685a1b16567e63c10a84a31398781d2711521deced'),
 'local/migration72/d02-active/resource-blob-astra/blob-final-1.json': (66137,'fe10d49fdfd7fb5df910da6407fd96bfdbfb02f907cced418145c8d4b5a4d739'),
 'local/migration72/d02-active/plain/initialization-archive-elevated6.json': (3242,'63025b39285dca6890ae84e5a4cb6f304f44c7ddf46c19911b6a930351429c0d'),
 'local/migration72/evidence/manifest-full.json': (2705780,'cd572bacd2c9dbbb8372d22cfe0fae675c5c40844ca78bc71f17f000d253af00'),
 'local/migration72/d02-resume-20261111b/scene/assets-fresh1.private.json': (20492,'3b3bc0c7cb26678cb672606832c229571000279add93ad6703351495bc63239d'),
 'local/migration72/d02-resume-20261111b/scene/configmeta-locator-index-candidate.private.json': (1132,'e452ac47b77adee12eb9df610fdade1fa86db0773244086d7666be5381cea04c'),
 'local/migration72/d02-resume-20261111b/scene/configmeta-exact-block-source.private.json': (1256,'1a66e9796fd90ea0e34bc77c7aa71cb9484d2e96bf205711675fd0548dafcfce'),
 'local/migration72/d02-active/resource-payload-astra/payload-final-1/31049740.object3.m_bytes.bin': (48614214,'216c791b536ce8e4f1f0128c200a791015768491bf23f2a8a9bc618720b6eb7d'),
 'local/migration72/d02-active/resource-payload-astra/payload-final-1/31049741.object3.m_bytes.bin': (3621204,'782aa1208ab3070a567f8907e7f15b8f3ad046b0d661cdd4774ef9af8f81dffc'),
 'local/migration72/d02-active/main/payload-index-third/31049740.locations-le-i32.bin': (34612632,'c013043753bdb2679bb9a59d4aab4d6db62d87febd37bb27e69adb00ee8093f2'),
 'local/migration72/d02-active/main/payload-index-third/31049741.locations-le-i32.bin': (12864,'29b5f4215a2c5caf2d1b57cf967f0d8e1bdb96f00af57d229e63be2a27c4ebb0'),
}
TOOLS = {
 'tools/client72_resource_locator.py':'413d2501323fde768076d59222a39af30ebd4857d89759478d060b6c22834456',
 'tools/client72_resource_payload_index.py':'3fd618d4c990f3358eff772b4af30553fb332c2e1ed20423a3f5783e6a59946c',
 'tools/client72_resource_blob.py':'e0be8ece8071730ea7a310208931e71eb358f07646dcd103dd28661d0cfee798',
 'tools/client72_resource_payload.py':'b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa',
 'tools/client72_resource_provider.py':'16c5ab0f8a4f2d1a653cb99c8dd972c4b20b742a75e7adbc0f397edb905a7c7f',
 'tools/client72_resource_index_native.py':'3a0fc703c63429d66775a07c1dbfa8c515678461fa00b5f6e050e98cab16a1a0',
 'tools/client72_emulator.py':'8007d2594ef8058a4f2a9f1c24094fb5d2551a202b4e52154876cdfe77a17c3c',
 'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
 'tools/extract_client72_literals.py':'865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed'}
BLOCK='YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/02455671.blk'
KEY=0x69d7a7642027d5f7

def require(condition, reason):
    if not condition: raise ValueError(reason)

def digest(data): return hashlib.sha256(data).hexdigest()

def check(path, size, sha):
    h=hashlib.sha256(); n=0
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b); n+=len(b)
    require((size is None or n==size) and h.hexdigest()==sha,'source drift: '+path.name)
    return {'bytes':n,'sha256':h.hexdigest()}

def save(path, value):
    with path.open('xb') as f: f.write((json.dumps(value,sort_keys=True,ensure_ascii=False,indent=2)+'\n').encode())

def gate_literal(literal, types):
    require(literal['literal']=={'cell':'0x145b51ee0','index':69656,'slot':69656,'usageRow':783116,'value':'Data/Common/MetaConfig'},'literal drift')
    require(types[7]==['MoleMole.Config.ConfigMetaConfig','.ScriptableObject'],'type suffix drift')

def gate_match(rows):
    require(len(rows)==1 and rows[0]['index']=='31049740' and rows[0]['offset']==19917 and rows[0]['end']==19931 and rows[0]['recordSHA256']=='355f82df27c40f1988c634e0e58343fc61a4b6b729dc7c30252c3f17f8b7ecf1' and rows[0]['variants']==[(0,33095)],'index match drift or ambiguous')

def gate_location(rows):
    require(rows==[(2455671,33095,308700,212203,0,6)],'location drift or ambiguous')

def gate_header(info):
    require(info['logicalBlobBytes']==212203 and info['fileTailBytes']==0,'logical blob extent mismatch')
    require(info['compressionTag']==3,'unknown codec: '+str(info['compressionTag']))
    require(info['blockCount']<=32 and info['uncompressedBytes']<=4*1024*1024,'payload budget exceeded')

def header_decode(binary,key,data,dest):
    from tools.client72_resource_blob import BlobProbe, parse_header
    p=BlobProbe(binary,ROOT,key); e=p.engine
    try:
        n=struct.unpack_from('<I',data,4)[0]
        require(data[:12]==bytes.fromhex('426c62037500000005000000') and n==117 and n+28<=len(data),'signature or header extent drift')
        sig=e.alloc(12); e.uc.mem_write(sig,data[:12]); h=e.alloc(64); storage=e.alloc(n+48); buf=storage+16
        e.uc.mem_write(storage,b'\xa5'*(n+48)); e.write(h+16,buf); e.write(h+40,n+16); out=e.alloc(8); size=e.alloc(8)
        v,check,_=p.call(0x1415aa740,dict(rcx=sig)); require(v&255==1,'signature rejected')
        v,prepare,_=p.call(0x1415aa760,dict(rcx=h,rdx=sig,r8=out,r9=size)); require(v&255==1 and e.read(size)==n+16 and e.read(out)==buf,'header preparation failed')
        e.uc.mem_write(buf,data[12:n+28]); v,run,visited=p.call(0x1415aa820,dict(rcx=h))
        decoded=bytes(e.uc.mem_read(buf+16,n))
        require(v&255==1 and bytes(e.uc.mem_read(storage,16))==b'\xa5'*16 and bytes(e.uc.mem_read(buf+n+16,16))==b'\xa5'*16 and bytes(e.uc.mem_read(buf,16))==data[12:28] and 0x141cc06c0 in visited,'header transform bounds/IV failed')
        require(not any(x['modeledCalls'] or x['memoryModels'] for x in (check,prepare,run)),'unexpected header model')
        scalar=[]
        for address, name in ((0x1415aa890,'logicalBlobBytes'),(0x1415aa8a0,'dataOffset'),(0x1415aa920,'compressionTag'),(0x1415aa930,'blockBytes'),(0x1415aa940,'blockCount'),(0x1415aa9f0,'contentCount')):
            value, getter, _=p.call(address,dict(rcx=h))
            require(not getter['modeledCalls'] and not getter['memoryModels'],'unexpected scalar getter model')
            scalar.append({'name':name,'value':value&0xffffffff,'run':getter})
        (dest/'decoded-header.bin').write_bytes(decoded)
        save(dest/'header-raw.private.json',{'decodedHeaderHex':decoded.hex(),'decodedHeaderSHA256':digest(decoded),'totalRaw':struct.unpack_from('<I',decoded)[0],'tailRaw':struct.unpack_from('<I',decoded,4)[0],'compressionRaw':decoded[0x14],'exponentRaw':decoded[0x15],'scalars':scalar,'runs':[check,prepare,run]})
        parsed=parse_header(decoded,len(data),n); getters=p.check_getters(h,parsed)
        require(not any(x['modeledCalls'] or x['memoryModels'] for x in [x['run'] for x in getters]),'unexpected header model')
        return {'decodedHeaderHex':decoded.hex(),'decodedHeaderSHA256':digest(decoded),'parsed':parsed,'runs':[check,prepare,run],'nativeGetters':getters}
    finally:p.close()

def collect(dest):
    result={'task':'F-03d3s2/CONFIGMETA-ACTUAL-LOCATOR','stage':'sources','clientExecuted':False,'naturalLoadObserved':False}
    try:
        result['sources']={p:check(ROOT/p,n,h) for p,(n,h) in PINS.items()}
        result['tools']={p:check(ROOT/p,None,h) for p,h in TOOLS.items()}
        prior_locator=json.loads((ROOT/'local/migration72/d02-active/main/locator-third.json').read_bytes())
        require(prior_locator['sourceSHA256']=={p:h for p,h in TOOLS.items() if p!='tools/client72_resource_provider.py'},'prior tool provenance mismatch')
        sys.path.insert(0,str(ROOT))
        from tools.client72_resource_locator import reference_hash, reference_pack, find_matches
        from tools.client72_resource_provider import Snapshot, u64
        request=json.loads((ROOT/'local/migration72/d02-resume-20261111b/scene/configmeta-m177845-one-window-request.private.json').read_bytes())
        require(digest((ROOT/'local/migration72/d02-resume-20261111b/scene/configmeta-m177845-one-window-request.private.json').read_bytes())=='913283b2c80f7e9cc9bbbce79aafb467c949320a57f61d0ac4aa69c002755e1a','stock request drift')
        profile_bytes=(ROOT/'protocol/cn-7.2/profile.json').read_bytes(); profile=json.loads(profile_bytes)['files']
        require({v['path']:{'bytes':v['size'],'sha256':v['sha256']} for v in profile.values()}==request['stock'],'stock profile drift')
        game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True).strip())
        result['stock']={p:check(game/p,x['bytes'],x['sha256']) for p,x in request['stock'].items()}
        index=json.loads((ROOT/'local/migration72/d02-active/main/payload-index-third.json').read_bytes())
        literal=json.loads((ROOT/'local/migration72/d02-resume-20261111b/scene/configmeta-m177845-literal.private.json').read_bytes())
        gate_literal(literal,index['indexes'][0]['types'])
        require(reference_hash('Data/Common/MetaConfig','.ScriptableObject')==KEY,'reference hash mismatch')
        rows=find_matches(ROOT/'local/migration72/d02-active/resource-payload-astra/payload-final-1',index,[{'key':KEY}])[KEY]
        gate_match(rows); packed=reference_pack(KEY,33095)
        require(packed&0xffffffff==33095 and packed>>32==0x642027d5,'reference packing mismatch')
        locations=[]
        for x in index['indexes']:
            raw=(ROOT/'local/migration72/d02-active/main/payload-index-third'/x['locationArtifact']['name']).read_bytes()
            locations.extend(r for r in struct.iter_unpack('<6i',raw) if r[1]==33095)
        gate_location(locations)
        manifest=(game/'pkg_version').read_bytes(); require(digest(manifest)=='0b8d5de9333f241af06ac3989f8bf3d090f086f71bedcdece844d7cbf194d219','manifest drift')
        lines=manifest.decode('utf8').splitlines(); matches=[(i+1,json.loads(s)) for i,s in enumerate(lines) if s.strip() and json.loads(s).get('remoteName')==BLOCK]
        require(len(matches)==1 and matches[0][0]==341,'manifest entry mismatch')
        block=(game/BLOCK).read_bytes(); require(len(block)==1751851 and digest(block)=='f0191829b0d173e2da353c06c81b119c0ceac7c4875f43da077d4e78c4f46a76' and hashlib.md5(block).hexdigest()=='77782549a3acee9bf1d14d0b3d87ccb2','block mismatch')
        require(matches[0][1]['fileSize']==len(block) and matches[0][1]['md5']==hashlib.md5(block).hexdigest(),'manifest block mismatch')
        segment=block[308700:520903]; require(len(segment)==212203 and digest(segment)=='2033c3659731308aa208d4e3ac9ee52e0b9df1396a5d0be32a3b5f5971859bb0','segment drift')
        (dest/'segment.bin').write_bytes(segment)
        context=Snapshot(ROOT/'local/migration72/d02-active/plain/initialization-archive-elevated6.json',profile,digest(profile_bytes))
        ptr=u64(context.at('archiveDecodeTable.cell',context.base+0x5eed350,8)); table=context.at('archiveDecodeTable.bytes',ptr,256)
        require(digest(table)=='789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297','archive table drift')
        result.update(stage='pre-decode',path='Data/Common/MetaConfig',suffix='.ScriptableObject',keyHex=f'{KEY:016x}',entry='642027d5',record=rows[0],location=list(locations[0]),manifestLine=341,segment={'bytes':len(segment),'sha256':digest(segment)},archiveTableSHA256=digest(table))
        save(dest/'pre-decode.private.json',result)
        binary=(game/'YuanShen.exe').read_bytes()
        result['stage']='header-parse'
        blob=header_decode(binary,table,segment,dest); result['stage']='header'; result['header']=blob
        save(dest/'header.private.json',result)
        gate_header(blob['parsed'])
        raise ValueError('payload continuation is outside the observed header stop')
    except Exception as e:
        result.update(passed=False,errorType=type(e).__name__,error=str(e))
        save(dest/'failure.private.json',result)
        raise

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--output',required=True); a=p.parse_args()
    require(Path(a.output).name==a.output and not (OUT/a.output).exists(),'new task-local basename required')
    OUT.mkdir(parents=True,exist_ok=True); d=OUT/a.output; d.mkdir(); collect(d)
if __name__=='__main__':main()
