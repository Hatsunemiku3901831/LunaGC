"""Fixed native blob block reads and independent full LZ4 payload recovery.

No process APIs or game-file writes. Only reviewed memory allocation is modeled.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import time

try:
    from .client72_resource_blob import BlobProbe, FILES, parse_header
    from .client72_resource_provider import Snapshot, digest, u64
    from .extract_client72 import read_verified, PE
except ImportError:
    from client72_resource_blob import BlobProbe, FILES, parse_header
    from client72_resource_provider import Snapshot, digest, u64
    from extract_client72 import read_verified, PE

BLOB_REPORT_SHA = 'fe10d49fdfd7fb5df910da6407fd96bfdbfb02f907cced418145c8d4b5a4d739'
BLOB_SOURCE_SHA = 'e0be8ece8071730ea7a310208931e71eb358f07646dcd103dd28661d0cfee798'
NATIVE = (0x1415a1ed0, 0x1415a3e20, 0x1415aa8c0, 0x1415c2710)
SPANS = ((0x140971460, 0x140971561), (0x141c77d70, 0x141c78417),
         (0x1415c24ce, 0x1415c24f1), (0x1415c1e80, 0x1415c1e91))
STATIC = (0x1415c2270, 0x1415c1ea0, 0x1415c1700, 0x1415c1930,
          0x141c81960, 0x141a2e4f0, 0x141a2f1d0, 0x141491de0,
          0x141136af0, 0x1419dfbc0, 0x1419e15a0, 0x1419d91e0)


def main():
    a = arguments()
    root = Path(__file__).resolve().parents[1]
    profile_bytes = (root/'protocol/cn-7.2/profile.json').read_bytes()
    profile = json.loads(profile_bytes)['files']
    binary = None
    for row in profile.values():
        data = read_verified(a.client/row['path'], row['sha256'])
        if row['path'] == 'YuanShen.exe':
            binary = data
    read_verified(root/'tools/client72_resource_blob.py', BLOB_SOURCE_SHA)
    prior = json.loads(read_verified(a.blob_report, BLOB_REPORT_SHA))
    context = Snapshot(a.context, profile, digest(profile_bytes))
    if context.sha256 != prior['contextSnapshotSHA256']:
        raise ValueError('Archive context differs from the frozen blob contract')
    pointer = u64(context.at('archiveDecodeTable.cell', context.base+0x5eed350, 8))
    key = context.at('archiveDecodeTable.bytes', pointer, 256)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    artifacts = a.output.with_suffix('')
    artifacts.mkdir(parents=True, exist_ok=True)
    probe = PayloadProbe(binary, root, key)
    samples, whole, unsupported = [], [], []
    started = time.monotonic()
    try:
        registration = probe.registration
        errors = probe.error_cases()
        for blob in prior['blobs']:
            data = read_verified(a.client/blob['path'], FILES[blob['path']])
            decoded = bytes.fromhex(blob['decodedHeaderHex'])
            info = parse_header(decoded, len(data), blob['parsed']['headerBytes'])
            if info != blob['parsed'] or digest(decoded) != blob['decodedHeaderSHA256']:
                raise ValueError('Frozen decoded-header contract differs')
            if info['compressionTag'] != 3:
                transformed, run = probe.transform(blob, data, 0)
                unsupported.append(dict(path=blob['path'], tag=info['compressionTag'],
                                        firstBlockBytes=len(transformed), transformedSHA256=digest(transformed),
                                        prefixRun=run, payloadDecoded=False,
                                        boundary='Registered tag9 VT14220c570+18=1415c1ea0; Oodle state lifecycle not executed.'))
                continue
            if not samples:
                errors.extend(probe.archive_errors(blob, data))
            chosen = sorted({0, info['blockCount']-1})
            for index in chosen:
                native, run, sequence = probe.native_block(blob, data, index)
                transformed, prefix_run = probe.transform(blob, data, index)
                reference = lz4_block(transformed, len(native))
                if reference != native:
                    raise ValueError('Native/reference real-block outputs differ')
                filename = Path(blob['path']).stem+'-block'+str(index)+'.bin'
                (artifacts/filename).write_bytes(native)
                samples.append(dict(path=blob['path'], blockIndex=index, outputBytes=len(native),
                                    outputSHA256=digest(native), transformedSHA256=digest(transformed),
                                    referenceEqual=True, artifact=filename, orderedCalls=sequence,
                                    nativeRun=run, prefixRun=prefix_run))
            output, blocks = bytearray(), []
            for index in range(info['blockCount']):
                if time.monotonic()-started > 240:
                    raise ValueError('Payload batch exceeded the explicit four-minute budget')
                transformed, run = probe.transform(blob, data, index)
                expected = info['lastBlockBytes'] if index+1 == info['blockCount'] else info['blockBytes']
                piece = lz4_block(transformed, expected)
                output.extend(piece)
                blocks.append(dict(index=index, inputBytes=len(transformed), outputBytes=len(piece),
                                   outputSHA256=digest(piece), transformedSHA256=digest(transformed),
                                   nativePrefixPathSHA256=run['pathSHA256']))
            if len(output) != info['uncompressedBytes']:
                raise ValueError('All-block decoded size differs from the native header')
            filename = Path(blob['path']).stem+'.serialized.bin'
            (artifacts/filename).write_bytes(output)
            parsed = serialized17(bytes(output))
            extracted = []
            by_id = {obj['pathID']:obj for obj in parsed['objects']}
            for obj in parsed['objects']:
                if 'lengthPrefixedBytes' in obj:
                    start = parsed['dataOffset']+obj['offset']+4
                    size = obj['lengthPrefixedBytes']['bytes']
                    name = Path(blob['path']).stem+'.object'+str(obj['pathID'])+'.m_bytes.bin'
                    raw_bytes = output[start:start+size]
                    (artifacts/name).write_bytes(raw_bytes)
                    extracted.append(dict(pathID=obj['pathID'],serializedOffset=start,bytes=size,
                                          sha256=digest(raw_bytes),artifact=name))
                if 'assetIndex' in obj:
                    for record in obj['assetIndex']['records']:
                        target = by_id.get(record['pathID'])
                        if record['fileID'] != 0 or target is None or 'lengthPrefixedBytes' not in target or record['fileSize'] != target['lengthPrefixedBytes']['bytes']:
                            raise ValueError('Actual asset-index target/file size does not match its local byte object')
                        record['targetBytesSHA256'] = target['lengthPrefixedBytes']['sha256']
                        record['fileSizeMatchesTarget'] = True
            whole.append(dict(path=blob['path'], inputSHA256=FILES[blob['path']],
                              bytes=len(output), sha256=digest(output), artifact=filename,
                              everyBlockNativePrefixExecuted=True, everyBlockReferenceDecoded=True,
                              everyBlockNativeCodecExecuted=False, blocks=blocks, serialized=parsed,
                              extractedByteObjects=extracted))
            print(json.dumps(dict(completed=Path(blob['path']).name, blocks=len(blocks), bytes=len(output))), flush=True)
        evidence = probe.code_evidence_payload(binary)
    finally:
        probe.close()
    recovered = [record for payload in whole for obj in payload['serialized']['objects']
                 for record in obj.get('assetIndex',{}).get('records',[])
                 if record['name']=='0000006f' and record.get('fileSizeMatchesTarget')]
    result = dict(schemaVersion=1, task='D02-R3-resource-payload', profileSHA256=digest(profile_bytes),
                  inputSHA256={row['path']: row['sha256'] for row in profile.values()},
                  frozenBlobReportSHA256=BLOB_REPORT_SHA, frozenBlobSourceSHA256=BLOB_SOURCE_SHA,
                  contextSnapshotSHA256=context.sha256, archiveContextSHA256=digest(key),
                  registration=registration, nativeSamples=samples, completePayloads=whole,
                  unresolvedCodecs=unsupported, errors=errors, code=evidence,
                  sourceSHA256={name:digest((root/name).read_bytes()) for name in (
                      'tools/client72_resource_payload.py', 'tools/client72_resource_blob.py',
                      'tools/client72_resource_provider.py', 'tools/client72_resource_index_native.py',
                      'tools/client72_emulator.py', 'tools/trace_client72_token.py')},
                  models=['141a2e4f0 only accepts NULL old pointer, size<=0x40000, alignment1, label0x48, flags0; native downstream NULL branch routes to allocation.',
                          '141a2e5a0 releases only this model allocation with matching label and intact canaries.',
                          'Archive/cached-window/header structures are explicit fixtures built from real file intervals and the frozen decoded header.',
                          'Full payloads use native prefix transform on EVERY block plus independent LZ4 decoding; complete native codec coverage is the five separate samples.'],
                  serialized0000006fRecovered=bool(recovered), serialized0000006fCount=len(recovered),
                  nativeEntry0000006fRead=False,
                  ctableDecoded=False, entry0000006fRead=False, targetProtocolReady=False,
                  resourceReady=False, clientExecuted=False)
    encoded = (json.dumps(result, ensure_ascii=False, indent=2)+'\n').encode()
    a.output.write_bytes(encoded)
    print(json.dumps(dict(sha256=digest(encoded), bytes=len(encoded), nativeSamples=len(samples),
                          completePayloads=len(whole), ctableDecoded=False)))


def arguments():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--blob-report', type=Path, required=True)
    p.add_argument('--context', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    for name in ('client','blob_report','context','output'):
        setattr(a,name,getattr(a,name).resolve())
    if not a.output.is_relative_to(Path(__file__).resolve().parents[1]/'local') or a.output.is_relative_to(a.client):
        p.error('Output must be under checkout local/ and outside the client')
    return a


def lz4_block(data, expected_size):
    """Independent bounded block decoder from the public LZ4 block specification."""
    if not data or not 0 <= expected_size <= 0x40000:
        raise ValueError('Invalid LZ4 input/output bound')
    output, cursor = bytearray(), 0
    while cursor < len(data):
        token = data[cursor]
        cursor += 1
        literal, cursor = extended_length(data, cursor, token >> 4)
        if cursor+literal > len(data) or len(output)+literal > expected_size:
            raise ValueError('Literal exceeds the actual input/output bounds')
        output.extend(data[cursor:cursor+literal])
        cursor += literal
        if cursor == len(data):
            if len(output) != expected_size:
                raise ValueError('LZ4 final literal does not reach expected length')
            return bytes(output)
        if cursor+2 > len(data):
            raise ValueError('Truncated match offset')
        distance = int.from_bytes(data[cursor:cursor+2], 'little')
        cursor += 2
        if not 0 < distance <= len(output):
            raise ValueError('Invalid backward match distance')
        match, cursor = extended_length(data, cursor, token & 15)
        match += 4
        if len(output)+match > expected_size:
            raise ValueError('Match exceeds output capacity')
        for _ in range(match):
            output.append(output[-distance])
    raise ValueError('LZ4 block lacks its terminal literal sequence')


def extended_length(data, cursor, length):
    if length == 15:
        while True:
            if cursor == len(data):
                raise ValueError('Truncated extended length')
            value = data[cursor]
            cursor += 1
            length += value
            if value != 255:
                break
    return length, cursor


def serialized17(data):
    """Version17/no-type-tree metadata only, following fixed native consumer fields."""
    if len(data) < 20:
        raise ValueError('Truncated serialized-file header')
    metadata_size, file_size, version, data_offset = struct.unpack_from('>4I',data)
    if file_size != len(data) or version != 17 or data[16:20] != bytes(4) or not 20+metadata_size <= data_offset <= file_size:
        raise ValueError('Unsupported serialized-file header')
    limit = 20+metadata_size
    cursor = data.find(b'\0',20,limit)
    if cursor < 0:
        raise ValueError('Unterminated serialized engine string')
    engine = data[20:cursor].decode('utf-8')
    cursor += 1
    platform, tree, type_count = struct.unpack_from('<iBI',data,cursor)
    cursor += 9
    if tree or not 0 < type_count <= 4096:
        raise ValueError('This contract covers bounded v17 metadata without a type tree')
    types = []
    for index in range(type_count):
        if cursor+23 > limit:
            raise ValueError('Truncated serialized type')
        kind, stripped, script = struct.unpack_from('<iBh',data,cursor)
        cursor += 7
        if kind in (-1,114):
            raise ValueError('Script-specific type hash is outside these actual samples')
        types.append(dict(classID=kind,stripped=stripped,scriptIndex=script,typeHash=data[cursor:cursor+16].hex()))
        cursor += 16
    count = struct.unpack_from('<I',data,cursor)[0]
    cursor += 4
    if count > 100000:
        raise ValueError('Object table exceeds the bounded contract')
    objects = []
    for index in range(count):
        cursor = (cursor+3)&~3
        if cursor+20 > limit:
            raise ValueError('Truncated serialized object')
        path_id, offset, size, type_index = struct.unpack_from('<QIII',data,cursor)
        cursor += 20
        if type_index >= len(types) or data_offset+offset+size > file_size:
            raise ValueError('Object type/extent exceeds actual data')
        raw = data[data_offset+offset:data_offset+offset+size]
        item = dict(pathID=path_id,offset=offset,size=size,typeIndex=type_index,
                    classID=types[type_index]['classID'],sha256=digest(raw))
        if item['classID'] == 1208 and len(raw)>=4:
            n = struct.unpack_from('<I',raw)[0]
            if n <= len(raw)-4:
                value = raw[4:4+n]
                item['lengthPrefixedBytes'] = dict(bytes=n,sha256=digest(value))
                if value.startswith(b'{'):
                    parsed = json.loads(value)
                    item['json'] = parsed
        if item['classID'] == 1210:
            item['assetIndex'] = asset_index(raw)
        objects.append(item)
    return dict(metadataBytes=metadata_size,fileBytes=file_size,version=version,dataOffset=data_offset,
                engine=engine,platform=platform,typeTree=False,types=types,objects=objects,
                metadataConsumed=cursor-20,metadataTailSHA256=digest(data[cursor:limit]),
                nativeFullConsumerExecuted=False)


def asset_index(raw):
    """Read m_Name plus observed name/m_FileID/m_PathID/m_FileSize schema."""
    if len(raw)<8:
        raise ValueError('Truncated asset-index object')
    name_size=struct.unpack_from('<I',raw)[0]
    if 4+name_size>len(raw):raise ValueError('Object name exceeds asset-index extent')
    object_name=raw[4:4+name_size].decode('utf-8')
    cursor=(4+name_size+3)&~3
    if cursor+4>len(raw):raise ValueError('Missing asset-index count')
    count=struct.unpack_from('<I',raw,cursor)[0];cursor+=4
    rows=[]
    if count>100000:
        raise ValueError('Unsupported bounded asset-index header')
    for index in range(count):
        if cursor+4>len(raw):raise ValueError('Truncated index name length')
        size=struct.unpack_from('<I',raw,cursor)[0];cursor+=4
        if cursor+size>len(raw):raise ValueError('Index name exceeds object')
        name=raw[cursor:cursor+size].decode('utf-8');cursor=(cursor+size+3)&~3
        if cursor+20>len(raw):raise ValueError('Truncated index target/span')
        file_id,path_id,file_size=struct.unpack_from('<iQQ',raw,cursor);cursor+=20
        rows.append(dict(name=name,fileID=file_id,pathID=path_id,fileSize=file_size))
    if cursor!=len(raw):raise ValueError('Asset-index extent is not fully consumed')
    return dict(objectName=object_name,count=count,records=rows,contains0000006f=any(row['name']=='0000006f' for row in rows),
                fieldSchemaSource='1419dfbc0→1419d91e0(m_Name)/1419e15a0(m_FileID4,m_PathID8,m_FileSize8)',
                layoutStatus='independent serialized parse against static native schema; full native consumer not executed')


class PayloadProbe(BlobProbe):
    def __init__(self,binary,root,key):
        super().__init__(binary,root,key)
        e=self.engine
        for address in NATIVE:e.allow_unwind(address)
        e.allowed.extend(SPANS)
        self.owned={}
        e.models={0x141a2e4f0:self.reallocate_null,0x141a2e5a0:self.free_buffer}
        codec=e.alloc(8)
        _,run,_=self.call(0x1415c24ce,dict(rax=codec,rsi=0x14220c5c8),stops=(0x1415c24f0,))
        if e.read(codec)!=0x14220c5c8 or e.read(0x145757478)!=codec or e.read(0x14220c5c8+24)!=0x1415c2710:
            raise ValueError('Native tag3 registration fragment differs')
        codec9=e.alloc(16)
        ret,run9,_=self.call(0x1415c1e80,dict(rcx=codec9,rdx=9))
        if ret!=codec9 or e.read(codec9)!=0x14220c570 or e.read(codec9+8,'I')!=9 or e.read(0x14220c570+24)!=0x1415c1ea0:
            raise ValueError('Native tag9 constructor differs')
        self.registration=dict(tag3=dict(vtable='0x14220c5c8',decode='0x1415c2710',run=run),
                               tag9=dict(vtable='0x14220c570',decode='0x1415c1ea0',run=run9),
                               fullInitializerExecuted=False,liveCodecObjectObserved=False)
        self.scratch=e.heap_cursor

    def reallocate_null(self):
        e=self.engine
        old,size,alignment,label=[e.reg(x) for x in ('rcx','rdx','r8','r9')]
        if old or not 0<size<=0x40000 or alignment!=1 or label!=0x48 or e.read(e.reg('rsp')+40)!=0:
            raise ValueError('Allocation exceeds the reviewed NULL-reallocation contract')
        base=e.alloc(size+32);e.uc.mem_write(base,b'\xa5'*(size+32));self.owned[base+16]=(size,label)
        self.memory_calls.append(dict(action='reallocate-null',size=size,alignment=alignment,label=label))
        return base+16

    def free_buffer(self):
        e=self.engine;pointer,label=e.reg('rcx'),e.reg('rdx')
        size,expected=self.owned.pop(pointer)
        if label!=expected or e.uc.mem_read(pointer-16,16)!=b'\xa5'*16 or e.uc.mem_read(pointer+size,16)!=b'\xa5'*16:
            raise ValueError('Scratch release label/canary differs')
        self.memory_calls.append(dict(action='release',size=size,label=label));return 0

    def setup(self,blob,data,index):
        e=self.engine;info=blob['parsed']
        if self.owned:raise ValueError('Previous native scratch remains owned')
        e.heap_cursor=self.scratch
        start=info['compressedEnds'][index-1] if index else 0
        length=info['compressedEnds'][index]-start
        raw=data[info['dataOffset']+start:info['dataOffset']+start+length]
        archive=e.alloc(0x138);source=e.alloc(length+32);e.uc.mem_write(source,raw)
        e.write(archive+0x38,0xffffffff,'I');e.write(archive+0x58,source);e.write(archive+0x60,source+length)
        e.write(archive+0x78,info['dataOffset']+start);e.write(archive+0x80,info['dataOffset']+start+length)
        e.write(archive+0x98,info['logicalBlobBytes']);e.write(archive+0xa0,info['dataOffset'])
        header=archive+0xa8;storage=e.alloc(info['headerBytes']+16)
        e.uc.mem_write(storage,data[12:28]+bytes.fromhex(blob['decodedHeaderHex']));e.uc.mem_write(header,data[:12])
        for offset,value in ((16,storage),(32,info['headerBytes']+16),(40,info['headerBytes']+16),(48,storage),(56,storage+16)):
            e.write(header+offset,value)
        e.write(archive+0xe8,1,'B');e.write(archive+0xec,info['compressionTag'],'I')
        e.write(archive+0xf0,info['blockBytes'],'I');e.write(archive+0xf4,info['blockCount'],'I');e.write(archive+0xf8,1,'B')
        return archive,header,raw

    def native_block(self,blob,data,index):
        e=self.engine;info=blob['parsed'];archive,_,raw=self.setup(blob,data,index)
        out=e.alloc(info['blockBytes']+32);e.uc.mem_write(out,b'\xa5'*(info['blockBytes']+32))
        # Retain actual ordered direct/indirect call targets, independently of the path hash.
        order=[];old_call=self.call
        from unicorn import UC_HOOK_CODE
        selected=set(NATIVE+(0x1415b6ee0,0x1408bc470,0x141c77d70,0x141a2e4f0,0x141a2e5a0))
        hook=e.uc.hook_add(UC_HOOK_CODE,lambda uc,address,size,user:order.append(hex(address)) if address in selected else None)
        try:value,run,_=old_call(0x1415a1ed0,dict(rcx=archive,rdx=index*info['blockBytes'],r8=info['blockBytes'],r9=out+16))
        finally:e.uc.hook_del(hook)
        size=info['lastBlockBytes'] if index+1==info['blockCount'] else info['blockBytes']
        actual=bytes(e.uc.mem_read(out+16,size))
        if value&255!=1 or self.owned or e.uc.mem_read(out,16)!=b'\xa5'*16 or e.uc.mem_read(out+16+size,info['blockBytes']-size+16)!=b'\xa5'*(info['blockBytes']-size+16):
            raise ValueError('Native block reader failed or exceeded actual output bounds')
        return actual,run,order

    def transform(self,blob,data,index):
        e=self.engine;_,header,raw=self.setup(blob,data,index)
        base=e.alloc(len(raw)+32);e.uc.mem_write(base,b'\xa5'*16+raw+b'\xa5'*16)
        value,run,_=self.call(0x1415aa8c0,dict(rcx=header,rdx=base+16,r8=len(raw)))
        actual=bytes(e.uc.mem_read(base+16,len(raw)))
        if value&255!=1 or run['modeledCalls'] or actual[128:]!=raw[128:] or e.uc.mem_read(base,16)!=b'\xa5'*16 or e.uc.mem_read(base+16+len(raw),16)!=b'\xa5'*16:
            raise ValueError('Native block prefix transform changed its bounded tail/canaries')
        return actual,run

    def error_cases(self):
        e=self.engine;rows=[]
        fixtures=[('literal',b'\x50hello',5,True),('overlap',b'\x16A\x01\x00\x50hello',16,True),
                  ('truncated-literal',b'\x50hell',5,False),('short-output',b'\x50hello',4,False),
                  ('empty-input',b'',5,False),('input-int-limit',b'\x50hello',5,False)]
        for name,raw,capacity,expected in fixtures:
            e.heap_cursor=self.scratch;source=e.alloc(len(raw)+16);e.uc.mem_write(source,raw)
            base=e.alloc(capacity+32);e.uc.mem_write(base,b'\xa5'*(capacity+32));counts=e.alloc(16)
            e.write(counts,0x7fffffff if name=='input-int-limit' else len(raw));e.write(counts+8,capacity)
            value,run,_=self.call(0x1415c2710,dict(rcx=e.read(0x145757478),rdx=source,r8=counts,r9=base+16),stack=(counts+8,))
            if bool(value&255)!=expected or e.uc.mem_read(base,16)!=b'\xa5'*16 or e.uc.mem_read(base+16+capacity,16)!=b'\xa5'*16 or run['modeledCalls']:
                raise ValueError('Native codec boundary result/canary differs')
            if expected and bytes(e.uc.mem_read(base+16,e.read(counts+8)))!=lz4_block(raw,capacity):
                raise ValueError('Native boundary fixture differs from independent decoder')
            rows.append(dict(name=name,returned=bool(value&255),outputCountRaw=e.read(counts+8),canariesIntact=True,run=run))
        return rows

    def archive_errors(self,blob,data):
        e=self.engine;rows=[];block_size=blob['parsed']['blockBytes']
        for name,offset,count,null_buffer,short_window in (
                ('unaligned-offset',1,block_size,False,False),
                ('unaligned-count',0,block_size-1,False,False),
                ('zero-count',0,0,False,False),('null-output',0,block_size,True,False),
                ('actual-block-short-cache',0,block_size,False,True)):
            archive,_,_=self.setup(blob,data,0)
            if short_window:e.write(archive+0x80,e.read(archive+0x80)-1)
            output=e.alloc(block_size);e.uc.mem_write(output,b'\xa5'*block_size)
            value,run,_=self.call(0x1415a1ed0,dict(rcx=archive,rdx=offset,r8=count,r9=0 if null_buffer else output))
            if value&255 or self.owned or e.uc.mem_read(output,block_size)!=b'\xa5'*block_size:
                raise ValueError('Native archive rejection changed output or leaked scratch')
            rows.append(dict(name=name,returned=False,outputUnchanged=True,
                             nativeFileReadAttempted=short_window,run=run))
        return rows

    def code_evidence_payload(self,binary):
        e=self.engine;rows=[]
        for address in sorted(set(NATIVE+STATIC)):
            end=e.unwind_ranges[address];pos=e.pe.offset(address,end-address,executable=True)
            rows.append(dict(start=hex(address),end=hex(end),extent='one .pdata record; may be one function chunk',sha256=digest(binary[pos:pos+end-address])))
        for start,end in SPANS:
            pos=e.pe.offset(start,end-start,executable=True)
            rows.append(dict(start=hex(start),end=hex(end),extent='reviewed contiguous instructions',sha256=digest(binary[pos:pos+end-start])))
        return rows


if __name__=='__main__':main()
