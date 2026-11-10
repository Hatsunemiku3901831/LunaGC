"""Locate one literal-bound payload from frozen static artifacts, without native execution."""
import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'local/migration72'
PRIVATE = BASE / 'd02-resume-20261110b/c-proud-payload-locator'
LITERAL = 'd02-resume-20261110b/c-proud-row-source/reader-literal{}.private.json'
LITERAL_SHA = '9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9'
PATH_SHA = 'dbcea51e0cf6e8d6809b225fe28a34b77c69838ea8d182d7fd77465a5e684fea'
LOCATOR_REPORT = 'd02-active/resource-locator-astra/locator-final-1.json'
INDEX_REPORT = 'd02-active/resource-payload-index-astra/index-final-2.json'
SERIALIZED = 'd02-active/resource-locator-astra/locator-final-1/25539185.serialized.bin'
PINS = {
    'tools/client72_resource_locator.py': '413d2501323fde768076d59222a39af30ebd4857d89759478d060b6c22834456',
    'tools/client72_resource_payload_index.py': '3fd618d4c990f3358eff772b4af30553fb332c2e1ed20423a3f5783e6a59946c',
    'tools/client72_resource_payload.py': 'b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa',
    'tools/client72_resource_blob.py': 'e0be8ece8071730ea7a310208931e71eb358f07646dcd103dd28661d0cfee798',
    'tools/client72_resource_index_native.py': '3a0fc703c63429d66775a07c1dbfa8c515678461fa00b5f6e050e98cab16a1a0',
    'tools/client72_emulator.py': '8007d2594ef8058a4f2a9f1c24094fb5d2551a202b4e52154876cdfe77a17c3c',
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/extract_client72_literals.py': '865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed',
    'tools/client72_resource_provider.py': '16c5ab0f8a4f2d1a653cb99c8dd972c4b20b742a75e7adbc0f397edb905a7c7f',
    'tools/trace_client72_token.py': 'f2fbc4728f73447dff4f7320beb3a7a31fb546287272b62ccd88a8e940312fa0',
    'tools/client72_resource_index.py': 'b7e1c58ccc6de5102b8e458c967a223bc2d070260b0b91e9830bf24e68e39273',
    'local/migration72/'+LITERAL.format(1): LITERAL_SHA,
    'local/migration72/'+LITERAL.format(2): LITERAL_SHA,
    'local/migration72/'+LOCATOR_REPORT: 'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda',
    'local/migration72/'+INDEX_REPORT: 'e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9',
    'local/migration72/d02-active/resource-payload-astra/payload-final-2/31049740.object3.m_bytes.bin': '216c791b536ce8e4f1f0128c200a791015768491bf23f2a8a9bc618720b6eb7d',
    'local/migration72/d02-active/resource-payload-astra/payload-final-2/31049741.object3.m_bytes.bin': '782aa1208ab3070a567f8907e7f15b8f3ad046b0d661cdd4774ef9af8f81dffc',
    'local/migration72/d02-active/resource-payload-index-astra/index-final-2/31049740.locations-le-i32.bin': 'c013043753bdb2679bb9a59d4aab4d6db62d87febd37bb27e69adb00ee8093f2',
    'local/migration72/d02-active/resource-payload-index-astra/index-final-2/31049741.locations-le-i32.bin': '29b5f4215a2c5caf2d1b57cf967f0d8e1bdb96f00af57d229e63be2a27c4ebb0',
}
SERIALIZED_SHA = '0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def verify(data, expected, label):
    if sha(data) != expected:
        raise ValueError('source drift: '+label)
    return data


def unique_record(rows):
    if len(rows) != 1:
        raise ValueError('asset record count is %d, expected one' % len(rows))
    row = rows[0]
    if len(row['variants']) != 1 or row['variants'][0][0] != 0 or row['variants'][0][1] == 0:
        raise ValueError('selector independence not established for all variants')
    return row


def one_location(data, bundle):
    if len(data) % 24:
        raise ValueError('location sidecar extent is not a multiple of 24')
    hits = [(offset, struct.unpack_from('<6i', data, offset)) for offset in range(0, len(data), 24)
            if struct.unpack_from('<i', data, offset+4)[0] == bundle]
    if len(hits) != 1:
        raise ValueError('location record count is %d, expected one' % len(hits))
    offset, values = hits[0]
    raw = data[offset:offset+24]
    return dict(offset=offset, end=offset+24, hex=raw.hex(), sha256=sha(raw),
                values=dict(zip(('blockID','bundleID','valueA','valueB','groupID','categoryBits'), values)))


def extract_entry(contents, parsed, name):
    indexed = [(obj, rec) for obj in parsed['objects'] if 'assetIndex' in obj
               for rec in obj['assetIndex']['records'] if rec['name'] == name]
    if len(indexed) != 1:
        raise ValueError('serialized entry count is %d, expected one' % len(indexed))
    index_obj, record = indexed[0]
    objects = [obj for obj in parsed['objects'] if obj['pathID'] == record['pathID']]
    if record['fileID'] != 0 or len(objects) != 1 or objects[0]['classID'] != 1208:
        raise ValueError('entry target is not unique local class 1208')
    obj = objects[0]
    size = obj['lengthPrefixedBytes']['bytes']
    start = parsed['dataOffset'] + obj['offset'] + 4
    if size != record['fileSize'] or start < 0 or start+size > len(contents):
        raise ValueError('serialized payload extent or size differs')
    raw = contents[start:start+size]
    if sha(raw) != obj['lengthPrefixedBytes']['sha256']:
        raise ValueError('serialized payload SHA differs')
    index_start = parsed['dataOffset'] + index_obj['offset']
    raw_index = contents[index_start:index_start+index_obj['size']]
    cursor = (4 + struct.unpack_from('<I', raw_index)[0] + 3) & ~3
    count = struct.unpack_from('<I', raw_index, cursor)[0]
    cursor += 4
    record_spans = []
    for _ in range(count):
        start_record = cursor
        n = struct.unpack_from('<I', raw_index, cursor)[0]
        cursor += 4
        record_name = raw_index[cursor:cursor+n].decode('utf8')
        cursor = (cursor+n+3) & ~3
        file_id, path_id, file_size = struct.unpack_from('<iQQ', raw_index, cursor)
        cursor += 20
        if record_name == name:
            if (file_id, path_id, file_size) != (record['fileID'], record['pathID'], record['fileSize']):
                raise ValueError('serialized index record differs')
            raw_record = raw_index[start_record:cursor]
            record_spans.append(dict(span=[index_start+start_record, index_start+cursor],
                                     bytes=len(raw_record), sha256=sha(raw_record), hex=raw_record.hex()))
    if len(record_spans) != 1 or cursor != len(raw_index):
        raise ValueError('serialized record span is not unique and complete')
    entry = dict(indexPathID=index_obj['pathID'], indexRecordSpan=record_spans[0], indexObjectSpan=[index_start, index_start+index_obj['size']],
                 indexObjectSHA256=index_obj['sha256'], record=record, objectPathID=obj['pathID'],
                 objectSpan=[parsed['dataOffset']+obj['offset'], parsed['dataOffset']+obj['offset']+obj['size']],
                 objectSHA256=obj['sha256'], payloadSpan=[start,start+size], bytes=size, sha256=sha(raw))
    return raw, entry


def save(path, report):
    path.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2)+'\n', encoding='utf8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    dest = PRIVATE / args.output
    if not args.output or dest.name != args.output or dest.exists():
        parser.error('output must be a new task-private basename')
    dest.mkdir(parents=True)
    report = dict(task='E-01/R3-PROUD-PAYLOAD-LOCATOR', stage='source-gate',
                  sourceSHA256=sha(Path(__file__).read_bytes()), sources={}, targetHashNativeExecuted=False,
                  parserExecuted=False, revisionProven=False, resourceReady=False, targetCPU=0, liveReads=0)
    try:
        for relative, expected in PINS.items():
            raw = (ROOT/relative).read_bytes()
            verify(raw, expected, relative)
            report['sources'][relative] = dict(bytes=len(raw), sha256=sha(raw))
        literal_raw = [(BASE/LITERAL.format(i)).read_bytes() for i in (1,2)]
        if literal_raw[0] != literal_raw[1]:
            raise ValueError('literal fresh reports differ')
        literal = json.loads(literal_raw[0])
        decoded = bytes.fromhex(literal['decoded']['hex'])
        if len(decoded) != 46 or sha(decoded) != PATH_SHA or decoded.decode('utf8') != literal['decoded']['value']:
            raise ValueError('literal decoded bytes differ')
        path = decoded.decode('utf8')
        report['literal'] = dict(decoded=literal['decoded'], originalLoad=literal['originalLoad'],
                                 registration=literal['registration'], source=literal['source'], producerPins=literal['pins'],
                                 producerInputChecks=literal['inputChecks'])
        prior = json.loads((BASE/LOCATOR_REPORT).read_bytes())
        index = json.loads((BASE/INDEX_REPORT).read_bytes())
        sys.path.insert(0,str(ROOT))
        from tools import client72_resource_locator as locator
        if prior['genericType']['type']['name'] != 'MiHoYoBinData' or prior['genericType']['type']['namespace'] != 'UnityEngine':
            raise ValueError('old actual generic type differs')
        types = index['indexes'][0]['types']
        suffixes = [x[1] for x in types if x[0] == 'UnityEngine.MiHoYoBinData']
        if suffixes != ['.MiHoYoBinData']:
            raise ValueError('old actual type suffix differs')
        suffix = suffixes[0]
        old = []
        if len(prior['targets']) != 9:
            raise ValueError('old reference target count differs')
        for item in prior['targets']:
            row = unique_record([item['indexRecord']])
            key = locator.reference_hash(item['path'], suffix)
            packed = locator.reference_pack(key, row['variants'][0][1])
            if (key != item['key'] or packed != item['packedIdentifier'] or
                packed & 0xffffffff != item['bundleID'] or packed >> 32 != item['assetPathHash'] or
                f'{packed >> 32:08x}' != item['nativeEntryName'] or not item['selectorIndependent']):
                raise ValueError('old reference hash/pack/name differs')
            old.append(dict(name=item['name'], key=key, packed=packed, entryName=item['nativeEntryName']))
        report['oldNineReferenceChecks'] = old
        payload_dir = BASE/'d02-active/resource-payload-astra/payload-final-2'
        section_checks = []
        for row in index['indexes']:
            data = (payload_dir/(row['name']+'.object3.m_bytes.bin')).read_bytes()
            section = row['sections']['assets']
            start,end = section['start'], section['end']
            if not 0 <= start <= end <= len(data) or end-start != section['bytes'] or sha(data[start:end]) != section['sha256']:
                raise ValueError('asset section size or SHA differs')
            if struct.unpack_from('<i',data,start)[0] != section['count']:
                raise ValueError('asset section count differs')
            section_checks.append(dict(index=row['name'], start=start, end=end, bytes=end-start,
                                       sha256=section['sha256'], count=section['count']))
        if len(section_checks) != 2:
            raise ValueError('expected exactly two sections')
        key = locator.reference_hash(path,suffix)
        report.update(stage='index-scan', suffix=suffix, sections=section_checks,
                      hostKey=key, hostKeyHex=f'{key:016x}')
        matches = locator.find_matches(payload_dir,index,[dict(key=key)])
        rows = matches[key]
        for row in rows:
            raw = (payload_dir/(row['index']+'.object3.m_bytes.bin')).read_bytes()[row['offset']:row['end']]
            if sha(raw) != row['recordSHA256']:
                raise ValueError('record SHA differs')
            row['recordHex'] = raw.hex()
        report['matches'] = rows
        save(dest/'index-diagnostic.private.json',report)
        row = unique_record(rows)
        packed = locator.reference_pack(key,row['variants'][0][1])
        bundle, path_hash = packed & 0xffffffff, packed >> 32
        name = f'{path_hash:08x}'
        sidecar = BASE/'d02-active/resource-payload-index-astra/index-final-2'/(row['index']+'.locations-le-i32.bin')
        location = one_location(sidecar.read_bytes(),bundle)
        report.update(stage='location', selectorIndependent=True, packedIdentifier=packed, bundleID=bundle,
                      assetPathHash=path_hash, entryName=name, location=location)
        save(dest/'location-diagnostic.private.json',report)
        old_location = prior['targets'][0]['location']
        if location['values'] != old_location or any(t['location'] != old_location for t in prior['targets']):
            raise ValueError('different location: frozen serialized bundle material is not established')
        if prior['payload']['artifact'] != Path(SERIALIZED).name or prior['payload']['outputSHA256'] != SERIALIZED_SHA:
            raise ValueError('frozen serialized provenance differs')
        contents = verify((BASE/SERIALIZED).read_bytes(), SERIALIZED_SHA, SERIALIZED)
        if len(contents) != 71555980 or len(contents) != prior['payload']['outputBytes']:
            raise ValueError('serialized size differs')
        report['serializedSource'] = dict(path='local/migration72/'+SERIALIZED, bytes=len(contents), sha256=sha(contents))
        parsed = locator.serialized_objects(contents)
        raw, entry = extract_entry(contents,parsed,name)
        (dest/'payload.bin').write_bytes(raw)
        report.update(stage='complete', passed=True, serializedHeader={k:parsed[k] for k in ('version','dataOffset','metadataBytes','types')},
                      entry=entry, actualPayloadExtracted=True, nativeSerializedLookupExecuted=False)
        save(dest/'result.private.json',report)
        print(json.dumps(dict(key=f'{key:016x}',bundleID=bundle,entryName=name,payloadBytes=len(raw),payloadSHA256=sha(raw))))
    except Exception as error:
        report.update(passed=False,errorType=type(error).__name__,error=str(error))
        save(dest/'failure.private.json',report)
        raise


if __name__ == '__main__':
    main()
