"""Fixed literal/type/hash/index/block/serialized-entry evidence; offline only."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import time

try:
    from .client72_resource_payload_index import Reader, asset_record, INPUTS, PAYLOAD_REPORT
    from .client72_resource_index_native import NativeIndexProbe
    from .client72_resource_payload import PayloadProbe, lz4_block, asset_index
    from .client72_resource_blob import BlobProbe, parse_header, MANIFESTS
    from .client72_resource_provider import Snapshot, u64, digest
    from .extract_client72 import read_verified, PE, number, HEADER_VA, BODY, extract, TypeResolver
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
except ImportError:
    from client72_resource_payload_index import Reader, asset_record, INPUTS, PAYLOAD_REPORT
    from client72_resource_index_native import NativeIndexProbe
    from client72_resource_payload import PayloadProbe, lz4_block, asset_index
    from client72_resource_blob import BlobProbe, parse_header, MANIFESTS
    from client72_resource_provider import Snapshot, u64, digest
    from extract_client72 import read_verified, PE, number, HEADER_VA, BODY, extract, TypeResolver
    from extract_client72_literals import Literals72, USAGE_REGISTRATION

INDEX_SOURCE = '3fd618d4c990f3358eff772b4af30553fb332c2e1ed20423a3f5783e6a59946c'
INDEX_REPORT = 'e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9'
BLOCK_PATH = 'YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/25539185.blk'
BLOCK_SHA = '33d71a39ea639d4f9f9a30d7432b11602e52047e5f43b09ba2e3eaa20990266a'
NAMES = ('Avatar','AvatarSkillDepot','AvatarSkill','Weapon','AvatarOrnamentGroup','AvatarOrnament','Quest','Chapter','Scene')
PREFIX, SUFFIX = 'Data/_ExcelBinOutput/', '.MiHoYoBinData'
NATIVE_HASH = (0x146ebb3e0,0x14146ac90,0x14146acc0,0x1415ca8a0,0x1415caa50,
               0x1415cd5a0,0x140a51ef0,0x141fdf9c0,0x141a12b90)
HASH_SPANS = ((0x1415cd4c0,0x1415cd59a),(0x140a51c00,0x140a51c22),
              (0x1414d3090,0x1414d309d),(0x1414d30a0,0x1414d30a8),
              (0x140003750,0x140003754),(0x1404bbeb0,0x1404bbeb5),
              (0x141cc01a0,0x141cc01ee),(0x147237cf9,0x147237d19),
              (0x140970e90,0x140970e91))
STATIC = (0x14d6e1570,0x154c35bb0,0x154c35d60,0x154c35b10,0x1471361f0,
          0x147237b80,0x147234540,0x14713f4a0,0x141a12b90)


def main():
    a = arguments()
    root = Path(__file__).resolve().parents[1]
    pb = (root/'protocol/cn-7.2/profile.json').read_bytes()
    profile = json.loads(pb)['files']
    verified = {x['path']:read_verified(a.client/x['path'],x['sha256']) for x in profile.values()}
    binary = verified['YuanShen.exe']
    metadata = verified['YuanShen_Data/Managed/Metadata/global-metadata.dat']
    startup = verified['YuanShen_Data/Managed/Metadata/startup-metadata.dat']
    read_verified(root/'tools/client72_resource_payload_index.py',INDEX_SOURCE)
    prior = json.loads(read_verified(a.index_report,INDEX_REPORT))
    payload_input = json.loads(read_verified(a.payload_directory.with_suffix('.json'),PAYLOAD_REPORT))
    if prior['profileSHA256'] != digest(pb):
        raise ValueError('Index profile differs')
    generic, literals = declarations(binary,metadata,startup)
    type_pairs = prior['indexes'][0]['types']
    if [x for x in type_pairs if x[0] == 'UnityEngine.MiHoYoBinData'] != [['UnityEngine.MiHoYoBinData',SUFFIX]]:
        raise ValueError('Actual serialized index type suffix differs')
    probe = HashProbe(binary,root)
    targets, tests = [], []
    try:
        for name in NAMES:
            path = PREFIX+name+'ExcelConfigData'
            key, run = probe.hash(path,SUFFIX)
            targets.append(dict(name=name,path=path,literal=literals.get(path),
                pathSource='fixed global literal' if path in literals else 'candidate exact name; absent in fixed global literal group',
                suffixSource='actual generic type plus actual 31049740 type map',key=key,keyHex=f'{key:016x}',hashRun=run))
        matches = find_matches(a.payload_directory,prior,targets)
        locations = {x['name']:read_verified(a.index_report.with_suffix('')/x['locationArtifact']['name'],
                                           x['locationArtifact']['sha256']) for x in prior['indexes']}
        for target in targets:
            rows = matches[target['key']]
            if len(rows) != 1 or rows[0]['variants'] != [(0,178)]:
                raise ValueError('Actual target index binding differs')
            row = rows[0]
            packed, run = probe.pack(target['key'],row['variants'][0][1])
            entry_name, name_run = probe.entry_name(packed>>32)
            target.update(indexRecord=row,packedIdentifier=packed,packRun=run,
                bundleID=packed & 0xffffffff,assetPathHash=packed>>32,
                nativeEntryName=entry_name,nativeEntryNameRun=name_run,selectorIndependent=True)
            hits = [x for x in struct.iter_unpack('<6i',locations[row['index']]) if x[1] == target['bundleID']]
            if hits != [(25539185,178,0,0,0,7)]:
                raise ValueError('Actual target block location differs')
            target['location'] = dict(zip(('blockID','bundleID','valueA','valueB','groupID','categoryBits'),hits[0]))
        tests = probe.boundaries()
        code = probe.code_evidence_locator(binary)
    finally:
        probe.close()
    data = read_verified(a.client/BLOCK_PATH,BLOCK_SHA)
    manifest = manifest_binding(a.client,data)
    context = Snapshot(a.context,profile,digest(pb))
    if context.sha256 != payload_input['contextSnapshotSHA256']:
        raise ValueError('Fixed archive snapshot differs from frozen payload provenance')
    pointer = u64(context.at('archiveDecodeTable.cell',context.base+0x5eed350,8))
    key = context.at('archiveDecodeTable.bytes',pointer,256)
    if digest(key) != '789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297':
        raise ValueError('Fixed archive context differs')
    blob = decode_header(binary,root,key,data)
    artifacts = a.output.with_suffix('')
    artifacts.mkdir(parents=True,exist_ok=True)
    payload = decode_payload(binary,root,key,data,blob,artifacts)
    contents = payload.pop('_bytes')
    objects = payload['serialized']['objects']
    indexes = [x for x in objects if 'assetIndex' in x]
    by_id = {x['pathID']:x for x in objects}
    for target in targets:
        records = [(obj,record) for obj in indexes for record in obj['assetIndex']['records'] if record['name'] == target['nativeEntryName']]
        if len(records) != 1:
            raise ValueError('Exact native entry name absent or ambiguous in actual serialized index')
        index_obj, record = records[0]
        obj = by_id.get(record['pathID'])
        if record['fileID'] != 0 or obj is None or obj['classID'] != 1208:
            raise ValueError('Actual entry target is not a local byte object')
        start = payload['serialized']['dataOffset']+obj['offset']+4
        n = obj['lengthPrefixedBytes']['bytes']
        if record['fileSize'] != n:
            raise ValueError('Actual index u64 file size differs from byte-array length')
        raw = contents[start:start+n]
        filename = target['name']+'.native-m_bytes.bin'
        (artifacts/filename).write_bytes(raw)
        target['entry'] = dict(indexPathID=index_obj['pathID'],record=record,objectPathID=obj['pathID'],
            serializedOffset=start,bytes=n,sha256=digest(raw),artifact=filename,head16=raw[:16].hex(),
            nativeSerializedLookupExecuted=False,tableParserExecuted=False)
    result = dict(schemaVersion=1,task='D02-R3-resource-locator',profileSHA256=digest(pb),
        inputSHA256={r['path']:r['sha256'] for r in profile.values()},
        frozenIndexSourceSHA256=INDEX_SOURCE,frozenIndexReportSHA256=INDEX_REPORT,
        genericType=generic,targets=targets,nativeBoundaries=tests,code=code,
        manifest=manifest,contextSnapshotSHA256=context.sha256,archiveContextSHA256=digest(key),
        blob=blob,payload=payload,
        sourceSHA256={n:digest((root/n).read_bytes()) for n in (
            'tools/client72_resource_locator.py','tools/client72_resource_payload_index.py',
            'tools/client72_resource_payload.py','tools/client72_resource_blob.py',
            'tools/client72_resource_index_native.py','tools/client72_emulator.py',
            'tools/extract_client72.py','tools/extract_client72_literals.py')},
        models=['Path strings are explicit UTF16 fixtures. Native hash/digest instructions execute; only free(NULL,label6) is modeled on the reviewed stack path.',
                'GetResourceIdentifier147237cf9..d18 is the exact pure packing continuation with real index value and key staged; full dictionary lookup is not executed.',
                'Index lookup uses only actual decoded byte arrays and all original variants; nine targets have a unique nonzero tag0 value so selector does not affect them.',
                'New blob header storage is preallocated and filled with exact actual file bytes; native header checks/transform/getters execute without I/O or codec success models.',
                'Every one of 546 blocks executes native prefix transform and independent LZ4 decode; first/last additionally execute full native archive/tag3 codec with the frozen NULL allocation model.',
                'Serialized object/index lookup is an independent bounded parse of static native schema. No byte-prefix JSON or table-format inference is used.'],
        actualTableByteArraysRecovered=True,completeNativeResourceReader=False,
        ctableDecoded=False,tableParserExecuted=False,revisionProven=False,exporterReady=False,clientExecuted=False)
    encoded = (json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode()
    a.output.write_bytes(encoded)
    print(json.dumps(dict(targets=len(targets),literalBacked=len(literals),blocks=len(payload['blocks']),
                          bytes=len(encoded),sha256=digest(encoded))))


def arguments():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('client','index-report','payload-directory','context','output'):
        p.add_argument('--'+name,required=True,type=Path)
    a=p.parse_args()
    for name,value in vars(a).items():
        setattr(a,name,value.resolve())
    if not a.output.is_relative_to(Path(__file__).resolve().parents[1]/'local') or a.output.is_relative_to(a.client):
        p.error('Output must be in checkout local/ and outside client')
    return a


def declarations(binary,metadata,startup):
    pe=PE(binary);h=pe.offset(HEADER_VA);lit=Literals72(binary,metadata)
    i=735837;key=(i*0x57c15286+0x26ddebda)&0xffffffff
    src=((number(metadata,lit.usages_base+i*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
    slot=number(metadata,lit.usages_base+i*8+4)^key^0x1855aa65
    cache=number(binary,pe.offset(USAGE_REGISTRATION)+0x10,'Q')
    if src != (6<<29|16871) or cache+slot*8 != 0x145983438:
        raise ValueError('Actual loader generic usage differs')
    method,cls,inst=struct.unpack_from('<iii',metadata,BODY+(number(binary,h+0x170)^0x6347382b)+16871*12)
    if (method,cls,inst)!=(80127,-1,58257):
        raise ValueError('Actual loader generic method instance differs')
    records,_=extract(binary,metadata)
    resolver=TypeResolver(pe,startup,records,h)
    n,ptr=struct.unpack_from('<QQ',binary,pe.offset(resolver.instances+inst*16))
    if n!=1:
        raise ValueError('Actual loader generic arity differs')
    arg=resolver.resolve_index(resolver.pointer_index(number(binary,pe.offset(ptr),'Q')))
    if arg.get('typeDefinition')!=8026 or arg.get('name')!='MiHoYoBinData' or arg.get('namespace')!='UnityEngine':
        raise ValueError('Actual loader generic type differs')
    wanted={PREFIX+x+'ExcelConfigData' for x in NAMES}
    found={}
    for row in lit.group(lit.global_group):
        text=lit.decode(row['index'])
        if text in wanted:
            if text in found:raise ValueError('Duplicate selected global literal')
            found[text]=dict(row,value=text)
    if wanted-found.keys()!={PREFIX+'QuestExcelConfigData'}:
        raise ValueError('Exact selected literal scope differs')
    return dict(instruction='14d6e158d: mov rdx,[rip-0x7d5e15c]',cell='0x145983438',
        usageRow=i,methodSpec=16871,methodDefinition=method,methodInstantiation=inst,type=arg),found


def reference_hash(path,suffix):
    if not path:
        return 0
    raw=(path+(suffix or '')).encode('utf-16le')[::2]
    size=256
    while size<len(raw):size*=2
    return int.from_bytes(hashlib.md5(raw+bytes(size-len(raw))).digest()[:8],'little')


def reference_pack(key,value):
    signed=(value & 0x3fffffff)
    if signed & 0x20000000:signed-=0x40000000
    return ((key>>8)&0xffffffff)<<32 | abs(signed)


class HashProbe(NativeIndexProbe):
    def __init__(self,binary,root):
        super().__init__(binary,root)
        e=self.engine;e.allowed.clear();e.models.clear()
        for address in NATIVE_HASH:e.allow_unwind(address)
        e.allowed.extend(HASH_SPANS)
        e.models[0x141a2e5a0]=self.null_release

    def null_release(self):
        if self.engine.reg('rcx')!=0 or self.engine.reg('rdx')!=6:
            raise ValueError('Only reviewed stack-path free(NULL,label6) is modeled')
        return 0

    def managed_string(self,text):
        if text is None:return 0
        raw=text.encode('utf-16le')
        if len(raw)>2048:raise ValueError('This hash model only covers the native stack allocation path')
        e=self.engine;p=e.alloc(len(raw)+22);e.write(p+16,len(raw)//2,'I')
        if raw:e.uc.mem_write(p+20,raw)
        return p

    def hash(self,path,suffix):
        if len((path or '').encode('utf-16le'))+len((suffix or '').encode('utf-16le'))>2048:
            raise ValueError('Combined string exceeds reviewed stack allocation path')
        value,run,_=self.call(0x146ebb3e0,dict(rcx=self.managed_string(path),rdx=self.managed_string(suffix)))
        if value!=reference_hash(path,suffix):raise ValueError('Actual native hash differs from independent padded-MD5 reference')
        return value,run

    def pack(self,key,value):
        result,run,_=self.call(0x147237cf9,dict(rsi=key),stack=(0,0,0,0,0,value & 0xffffffff),stops=(0x147237d18,))
        if result!=reference_pack(key,value):raise ValueError('Actual native identifier packing differs')
        return result,run

    def entry_name(self,value):
        e=self.engine;destination=e.alloc(40);e.write(destination+0x20,0x46,'I')
        _,run,_=self.call(0x141a12b90,dict(rcx=value,rdx=destination),stops=(0x140970e90,))
        actual=bytes(e.uc.mem_read(e.reg('rdx'),e.reg('r8')))
        if actual!=f'{value:08x}'.encode('ascii'):raise ValueError('Native entry name differs')
        return actual.decode('ascii'),run

    def boundaries(self):
        rows=[]
        for path,suffix in ((None,None),('',SUFFIX),('a',''),('资源/🙂','尾'),('A'*256,''),('B'*257,'')):
            value,run=self.hash(path,suffix)
            rows.append(dict(kind='hash',path=path,suffix=suffix,value=value,run=run))
        for key,value in ((0xffffffffffffffff,-1),(0x100000001,0x40000001),(0x8000000000000100,-2147483647),(1,0x20000000)):
            result,run=self.pack(key,value)
            rows.append(dict(kind='pack',key=key,value=value,result=result,run=run))
        result,run,_=self.call(0x14146ac90,dict(rcx=self.managed_string('nonempty'),rdx=0xffffffff))
        if result!=0:raise ValueError('Native negative-width rejection differs')
        rows.append(dict(kind='negative-width',result=result,run=run))
        return rows

    def code_evidence_locator(self,binary):
        e=self.engine;ranges=set(e.allowed)
        for address in STATIC:e.allow_unwind(address);ranges.add((address,e.unwind_ranges[address]))
        return [dict(start=hex(a),end=hex(z),exactPdata=e.unwind_ranges.get(a)==z,
                     sha256=digest(binary[e.pe.offset(a):e.pe.offset(a)+z-a])) for a,z in sorted(ranges)]


def find_matches(directory,prior,targets):
    result={x['key']:[] for x in targets}
    for name,(_,sha) in INPUTS.items():
        data=read_verified(directory/(name+'.object3.m_bytes.bin'),sha)
        section=next(x for x in prior['indexes'] if x['name']==name)['sections']['assets']
        r=Reader(data);r.pos=section['start'];count=r.read('i')
        for _ in range(count):
            row=asset_record(r)
            if row['key'] in result:
                row.update(index=name,recordSHA256=digest(data[row['offset']:row['end']]))
                result[row['key']].append(row)
        if r.pos!=section['end']:raise ValueError('Frozen asset section end differs')
    return result


def manifest_binding(client,data):
    out=[]
    for name in ('pkg_version','YuanShen_Data/StreamingAssets/data_versions_streaming'):
        raw=read_verified(client/name,MANIFESTS[name])
        rows=[json.loads(x) for x in raw.decode('utf8').splitlines() if x.strip()]
        wanted=BLOCK_PATH if name=='pkg_version' else 'blocks/00/25539185.blk'
        found=[x for x in rows if x['remoteName']==wanted]
        if len(found)!=1 or found[0]['md5']!=hashlib.md5(data).hexdigest() or found[0]['fileSize']!=len(data):
            raise ValueError('Actual block manifest/hash/size differs')
        out.append(dict(manifest=name,sha256=digest(raw),record=found[0]))
    return dict(path=BLOCK_PATH,bytes=len(data),sha256=digest(data),records=out,
                basenameMatchesIndexBlockID=25539185,nativePathOpenerExecuted=False)


def decode_header(binary,root,key,data):
    p=BlobProbe(binary,root,key);e=p.engine
    try:
        n=struct.unpack_from('<I',data,4)[0]
        if data[:12]!=bytes.fromhex('426c6203f508000005000000') or n+28>len(data):
            raise ValueError('Fixed real table block signature differs')
        sig=e.alloc(12);e.uc.mem_write(sig,data[:12]);h=e.alloc(64);storage=e.alloc(n+48);buf=storage+16
        e.uc.mem_write(storage,b'\xa5'*(n+48));e.write(h+16,buf);e.write(h+40,n+16);out=e.alloc(8);size=e.alloc(8)
        value,check,_=p.call(0x1415aa740,dict(rcx=sig))
        if value&255!=1:raise ValueError('Native actual signature rejected')
        value,prepare,_=p.call(0x1415aa760,dict(rcx=h,rdx=sig,r8=out,r9=size))
        if value&255!=1 or e.read(size)!=n+16 or e.read(out)!=buf:raise ValueError('Native header preparation differs')
        e.uc.mem_write(buf,data[12:n+28]);value,run,_=p.call(0x1415aa820,dict(rcx=h))
        if value&255!=1:raise ValueError('Native table header transform failed')
        decoded=bytes(e.uc.mem_read(buf+16,n))
        if (bytes(e.uc.mem_read(storage,16))!=b'\xa5'*16 or bytes(e.uc.mem_read(buf+n+16,16))!=b'\xa5'*16
            or bytes(e.uc.mem_read(buf,16))!=data[12:28] or decoded[128:]!=data[156:n+28]):
            raise ValueError('Native header changed bounds/IV/untouched tail')
        parsed=parse_header(decoded,len(data),n)
        getters=p.check_getters(h,parsed)
        return dict(path=BLOCK_PATH,inputSHA256=BLOCK_SHA,fileBytes=len(data),decodedHeaderHex=decoded.hex(),
                    decodedHeaderSHA256=digest(decoded),parsed=parsed,runs=[check,prepare,run],nativeGetters=getters)
    finally:p.close()


def decode_payload(binary,root,key,data,blob,artifacts):
    p=PayloadProbe(binary,root,key);info=blob['parsed'];out=bytearray();rows=[];samples=[];started=time.monotonic()
    try:
        if info['compressionTag']!=3 or info['blockCount']!=546 or info['fileTailBytes']:
            raise ValueError('Actual table block codec/extent differs')
        for i in (0,info['blockCount']-1):
            raw,run,seq=p.native_block(blob,data,i);tr,pr=p.transform(blob,data,i)
            if lz4_block(tr,len(raw))!=raw:raise ValueError('Native/reference table block differs')
            samples.append(dict(index=i,sha256=digest(raw),run=run,sequence=seq,prefixRun=pr))
        for i in range(info['blockCount']):
            if time.monotonic()-started>240:raise ValueError('Four minute payload boundary')
            tr,run=p.transform(blob,data,i)
            size=info['lastBlockBytes'] if i+1==info['blockCount'] else info['blockBytes']
            raw=lz4_block(tr,size);out.extend(raw)
            rows.append(dict(index=i,inputSHA256=digest(tr),outputSHA256=digest(raw),pathSHA256=run['pathSHA256']))
        if len(out)!=info['uncompressedBytes']:raise ValueError('Full decoded extent differs')
        data=bytes(out);filename='25539185.serialized.bin';(artifacts/filename).write_bytes(data)
        return dict(outputBytes=len(data),outputSHA256=digest(data),artifact=filename,nativeSamples=samples,blocks=rows,
                    allBlocksNativePrefixExecuted=True,allBlocksNativeCodecExecuted=False,
                    serialized=serialized_objects(data),_bytes=data)
    finally:p.close()


def serialized_objects(data):
    """v17 metadata and byte objects only; no guessed JSON interpretation."""
    if len(data)<20:raise ValueError('Truncated serialized header')
    metadata_size,file_size,version,data_offset=struct.unpack_from('>4I',data)
    if file_size!=len(data) or version!=17 or data[16:20]!=bytes(4) or not 20+metadata_size<=data_offset<=file_size:
        raise ValueError('Unsupported serialized header')
    limit=20+metadata_size;cursor=data.find(b'\0',20,limit)
    if cursor<0:raise ValueError('Unterminated engine string')
    engine=data[20:cursor].decode('utf8');cursor+=1
    if cursor+9>limit:raise ValueError('Truncated platform/type header')
    platform,tree,type_count=struct.unpack_from('<iBI',data,cursor);cursor+=9
    if tree or not 0<type_count<=4096:raise ValueError('Unsupported type tree/count')
    types=[]
    for _ in range(type_count):
        if cursor+23>limit:raise ValueError('Truncated serialized type')
        kind,stripped,script=struct.unpack_from('<iBh',data,cursor);cursor+=7
        if kind not in (142,1208,1210):raise ValueError('Unreviewed serialized class schema')
        types.append(dict(classID=kind,stripped=stripped,scriptIndex=script,typeHash=data[cursor:cursor+16].hex()));cursor+=16
    if cursor+4>limit:raise ValueError('Missing object count')
    count=struct.unpack_from('<I',data,cursor)[0];cursor+=4
    if count>100000:raise ValueError('Unbounded serialized objects')
    objects=[];ids=set()
    for _ in range(count):
        cursor=(cursor+3)&~3
        if cursor+20>limit:raise ValueError('Truncated serialized object')
        path_id,offset,size,type_index=struct.unpack_from('<QIII',data,cursor);cursor+=20
        if type_index>=len(types) or data_offset+offset+size>file_size or path_id in ids:
            raise ValueError('Invalid serialized object identity/extent')
        ids.add(path_id);raw=data[data_offset+offset:data_offset+offset+size]
        item=dict(pathID=path_id,offset=offset,size=size,typeIndex=type_index,classID=types[type_index]['classID'],sha256=digest(raw))
        if item['classID']==1208:
            if len(raw)<4:raise ValueError('Missing byte-array length')
            n=struct.unpack_from('<I',raw)[0]
            if n>len(raw)-4 or len(raw)-4-n>3:raise ValueError('Byte-array extent/padding differs')
            item['lengthPrefixedBytes']=dict(bytes=n,sha256=digest(raw[4:4+n]))
        if item['classID']==1210:item['assetIndex']=asset_index(raw)
        objects.append(item)
    return dict(metadataBytes=metadata_size,fileBytes=file_size,version=version,dataOffset=data_offset,
        engine=engine,platform=platform,typeTree=False,types=types,objects=objects,
        metadataConsumed=cursor-20,metadataTailSHA256=digest(data[cursor:limit]),nativeFullConsumerExecuted=False)


if __name__=='__main__':main()
