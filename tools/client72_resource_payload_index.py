"""Audit two fixed ResourceIndex byte arrays against their native consumers.

No process access. Full parsing is independent Python; bounded native consumer
execution uses explicit primitive-reader and void output-recorder models.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct

try:
    from .client72_resource_index_native import NativeIndexProbe
    from .client72_emulator import END
    from .extract_client72 import read_verified
except ImportError:
    from client72_resource_index_native import NativeIndexProbe
    from client72_emulator import END
    from extract_client72 import read_verified

PAYLOAD_SOURCE = 'b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa'
PAYLOAD_REPORT = 'efd152bc8e7337ab5af6aa685a1b16567e63c10a84a31398781d2711521deced'
INPUTS = {
    '31049740': (48614214, '216c791b536ce8e4f1f0128c200a791015768491bf23f2a8a9bc618720b6eb7d'),
    '31049741': (3621204, '782aa1208ab3070a567f8907e7f15b8f3ad046b0d661cdd4774ef9af8f81dffc'),
}
CONSUMERS = (0x146f00000, 0x146f00480, 0x146f00580, 0x146f00880,
             0x146f01f20, 0x146f020d0, 0x146f02280, 0x146f02a60,
             0x146d8c8f0, 0x146d8c950, 0x146d8c9b0, 0x146d8cad0,
             0x147234d00, 0x147234e10, 0x147236520,
             0x14713f3c0, 0x14713f4a0, 0x147145190, 0x147148040)


def main():
    args = arguments()
    root = Path(__file__).resolve().parents[1]
    profile_bytes = (root/'protocol/cn-7.2/profile.json').read_bytes()
    profile = json.loads(profile_bytes)['files']
    binary = None
    for row in profile.values():
        value = read_verified(args.client/row['path'], row['sha256'])
        if row['path'] == 'YuanShen.exe':
            binary = value
    read_verified(root/'tools/client72_resource_payload.py', PAYLOAD_SOURCE)
    prior = json.loads(read_verified(args.payload_report, PAYLOAD_REPORT))
    if prior['profileSHA256'] != digest(profile_bytes):
        raise ValueError('Frozen payload profile differs')
    artifacts = args.output.with_suffix('')
    artifacts.mkdir(parents=True, exist_ok=True)
    probe = AssetConsumerProbe(binary, root)
    results, traces = [], []
    try:
        for name, (size, sha) in INPUTS.items():
            filename = name+'.object3.m_bytes.bin'
            source = read_verified(args.payload_report.with_suffix('')/filename, sha)
            if len(source) != size:
                raise ValueError('Fixed byte-array size differs')
            parent = next(x for x in prior['completePayloads'] if Path(x['path']).stem == name)
            obj = next(x for x in parent['extractedByteObjects'] if x['artifact'] == filename)
            if obj['sha256'] != sha or obj['serializedOffset'] != 4252:
                raise ValueError('Extracted object provenance differs')
            result, locations = parse_index(source)
            artifact = name+'.locations-le-i32.bin'
            (artifacts/artifact).write_bytes(locations)
            result.update(name=name, inputBytes=size, inputSHA256=sha, sourceArtifact=filename,
                          serializedOffset=obj['serializedOffset'],
                          locationArtifact=dict(name=artifact, bytes=len(locations), sha256=digest(locations),
                              rowBytes=24, columns=['blockID','bundleID','valueA','valueB','groupID','categoryBits']))
            section = result['sections']['assets']
            trace = probe.run(source[section['start']:section['end']], selector=0, prefix=True)
            expected = result['assetSamples'][0]
            if trace['outputs'] != [dict(key=expected['key'], value=choose_variant(expected['variants'], 0), reserved=0)]:
                raise ValueError('Actual native first record differs from independent parsing')
            trace.update(name=name, sourceOffset=section['start'], sourceSHA256=sha,
                         originalDeclaredCount=section['count'], inputCountUnchanged=True)
            traces.append(trace)
            for sample in result['multiVariantSamples'][:1]:
                raw = source[sample['offset']:sample['end']]
                for selector in (0, 1):
                    run = probe.run(struct.pack('<i', 1)+raw, selector=selector)
                    if run['outputs'][0]['value'] != choose_variant(sample['variants'], selector):
                        raise ValueError('Actual multi-variant record differs from native selection')
                    run.update(name=name, actualRecordOffset=sample['offset'], actualRecordSHA256=digest(raw),
                               fixture='One unchanged actual record with an explicit synthetic count=1 header')
                    traces.append(run)
            results.append(result)
        errors = probe.boundaries()
        code = probe.code_evidence_index(binary)
    finally:
        probe.close()
    bridge = fixed_blob_bridge(results, artifacts, args.client, root)
    result = dict(schemaVersion=1, task='D02-R3-resource-payload-index', profileSHA256=digest(profile_bytes),
        inputSHA256={r['path']:r['sha256'] for r in profile.values()},
        frozenPayloadReportSHA256=PAYLOAD_REPORT, frozenPayloadSourceSHA256=PAYLOAD_SOURCE,
        indexes=results, nativeConsumerSamples=traces, nativeBoundaries=errors, code=code,
        actualBlobBridge=bridge,
        sourceSHA256={p:digest((root/p).read_bytes()) for p in (
            'tools/client72_resource_payload_index.py', 'tools/client72_resource_index_native.py',
            'tools/client72_emulator.py', 'tools/trace_client72_token.py')},
        models=[
            'Synthetic managed reader/vtable routes VT+120/+148/+160 to immutable byte-array reads of u8/i32/u64, respectively; EOF stops, with no successful value invented.',
            'SetAssetCount147234d00 and AddAsset147234e10 are void recorders of their actual ABI inputs; dictionary allocation/insertion is not executed.',
            'Native ReadAssetInfoMap_B146f00580 is original PE code. Actual-input runs preserve the entire original section and original count and stop after its first emitted record.',
            'One unchanged actual two-variant record is also run with an explicit count=1 fixture and selectors0/1; selector14586d100 is an offline input, not observed runtime state.',
            'Full index parsing is independently implemented from the static consumer order. All actual type strings are ASCII; non-ASCII ReadChars decoding is deliberately outside this contract.'
        ], completeIndependentIndexParse=True, completeNativeIndexParse=False,
        loadBinDataZipFileExecuted=False, resourceIdentifierResolved=False,
        ctableDecoded=False, revisionProven=False, exporterReady=False, clientExecuted=False)
    encoded = (json.dumps(result, ensure_ascii=False, indent=2)+'\n').encode()
    args.output.write_bytes(encoded)
    print(json.dumps(dict(indexes=len(results), nativeSamples=len(traces), boundaries=len(errors),
                          sha256=digest(encoded), bytes=len(encoded))))


def arguments():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path)
    p.add_argument('--payload-report', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    a = p.parse_args()
    a.client, a.payload_report, a.output = a.client.resolve(), a.payload_report.resolve(), a.output.resolve()
    if not a.output.is_relative_to(Path(__file__).resolve().parents[1]/'local') or a.output.is_relative_to(a.client):
        p.error('Output must be in checkout local/ and outside the client')
    return a


def digest(value):
    return hashlib.sha256(value).hexdigest()


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def read(self, fmt):
        size = struct.calcsize('<'+fmt)
        if self.pos+size > len(self.data):
            raise ValueError('Input EOF at '+str(self.pos)+' for '+fmt)
        values = struct.unpack_from('<'+fmt, self.data, self.pos)
        self.pos += size
        return values[0] if len(values) == 1 else values

    def count(self, minimum):
        value = self.read('i')
        if value < 0 or value > (len(self.data)-self.pos)//minimum:
            raise ValueError('Negative or impossible count at '+str(self.pos-4))
        return value

    def ascii_string(self):
        size = self.count(1)
        value = self.data[self.pos:self.pos+size]
        if any(c >= 128 for c in value):
            raise ValueError('Non-ASCII ReadChars requires an additional encoding contract')
        self.pos += size
        return value.decode('ascii')


def asset_record(reader):
    start = reader.pos
    key = reader.read('Q')
    count = reader.read('B')
    variants = [reader.read('Bi') for _ in range(count)]
    return dict(offset=start, end=reader.pos, key=key, variants=variants)


def choose_variant(variants, selector):
    value = 0
    for tag, candidate in variants:
        if value == 0 or tag == selector:
            value = candidate
    return value


def parse_index(data):
    """Parse the fixed ASCII profile, preserving unresolved integers and flags."""
    r = Reader(data)
    sections, category, locations = {}, {}, bytearray()
    def finish(name, start, **details):
        sections[name] = dict(start=start, end=r.pos, bytes=r.pos-start,
                              sha256=digest(data[start:r.pos]), **details)
    count = r.count(8)
    types = [(r.ascii_string(), r.ascii_string()) for _ in range(count)]
    finish('types', 0, count=count)
    start = r.pos
    count = r.count(9)
    variant_counts, tags, keys = Counter(), Counter(), set()
    samples, multi, max_value, duplicates = [], [], 0, 0
    for _ in range(count):
        row = asset_record(r)
        key, variants = row['key'], row['variants']
        duplicates += key in keys
        keys.add(key)
        variant_counts[len(variants)] += 1
        tags.update(tag for tag, _ in variants)
        for _, value in variants:
            max_value = max(max_value, value)
        if len(samples) < 3:
            samples.append(row)
        if len(variants) > 1 and len(multi) < 3:
            multi.append(row)
    finish('assets', start, count=count, duplicateKeys=duplicates,
           variantCounts=dict(sorted(variant_counts.items())), tags=dict(sorted(tags.items())),
           maximumRawVariantValue=max_value)
    del keys
    start = r.pos
    count, declared, capacity = r.read('iii')
    if min(count, declared, capacity) < 0 or count*8+declared*4 > len(data)-r.pos:
        raise ValueError('Invalid dependency header')
    dependency_ids, dependency_total, dependency_samples = set(), 0, []
    for _ in range(count):
        key = r.read('i')
        n = r.count(4)
        values = [r.read('i') for _ in range(n)]
        if key < 0 or key >= capacity or key in dependency_ids:
            raise ValueError('Duplicate or out-of-capacity dependency key')
        dependency_ids.add(key)
        dependency_total += n
        if len(dependency_samples) < 3:
            dependency_samples.append(dict(bundleID=key, dependencies=values))
    if dependency_total != declared:
        raise ValueError('Dependency total differs from declared total')
    finish('bundles', start, count=count, dependencyCount=declared, capacity=capacity, samples=dependency_samples)
    del dependency_ids
    for name in ('preload', 'shaderPreload'):
        start = r.pos
        count = r.count(4)
        values = [r.read('i') for _ in range(count)]
        finish(name, start, count=count, first=values[:8])
    start = r.pos
    count = r.count(8)
    groups = []
    for _ in range(count):
        group = r.read('i')
        size = r.count(6)
        for _ in range(size):
            block, a, b = r.read('iBB')
            if block in category:
                raise ValueError('Duplicate block category')
            category[block] = (group, a*2 | b)
        groups.append(dict(group=group, count=size))
    finish('categories', start, count=count, blockCount=len(category), groups=groups)
    start = r.pos
    count = r.count(8)
    seen_blocks, seen_bundles, entry_count, zero_pairs, location_samples = set(), set(), 0, 0, []
    for _ in range(count):
        block = r.read('i')
        size = r.count(12)
        if block in seen_blocks:
            raise ValueError('Duplicate location block')
        seen_blocks.add(block)
        group, bits = category.get(block, (0, 0))
        first = []
        for _ in range(size):
            bid, a, b = r.read('iii')
            if not 0 <= bid < capacity or min(a, b) < 0 or bid in seen_bundles:
                raise ValueError('Invalid or duplicate location bundle')
            seen_bundles.add(bid)
            locations.extend(struct.pack('<6i', block, bid, a, b, group, bits))
            entry_count += 1
            zero_pairs += a == b == 0
            if len(first) < 4:
                first.append([bid, a, b])
        if len(location_samples) < 4:
            location_samples.append(dict(blockID=block, count=size, entries=first))
    finish('locations', start, count=count, entries=entry_count, zeroValuePairs=zero_pairs,
           samples=location_samples, allBlocksHaveCategory=seen_blocks <= category.keys())
    start = r.pos
    present = r.pos != len(data)
    count = r.count(4) if present else 0
    values = [r.read('i') for _ in range(count)]
    if r.pos != len(data):
        raise ValueError('Trailing bytes after optional block sort list')
    finish('sort', start, present=present, count=count, first=values[:8],
           duplicates=len(values)-len(set(values)), valuesBelongToLocationBlocks=set(values) <= seen_blocks)
    return dict(sections=sections, types=types, assetSamples=samples, multiVariantSamples=multi,
                finalPosition=r.pos, remainderBytes=len(data)-r.pos), bytes(locations)


class AssetConsumerProbe(NativeIndexProbe):
    def __init__(self, binary, root):
        super().__init__(binary, root)
        e = self.engine
        e.models.clear()
        e.allowed.clear()
        e.allow_unwind(0x146f00580)
        self.reader_class = e.alloc(0x200)
        self.reader = e.alloc(0x20)
        self.deserializer = e.alloc(0x28)
        self.index = e.alloc(0x80)
        self.binary_reader = e.alloc(0x20)
        e.write(self.binary_reader, self.reader_class)
        e.write(self.reader+0x18, self.binary_reader)
        e.write(self.deserializer+0x18, self.index)
        for offset, fmt in ((0x120, 'B'), (0x148, 'i'), (0x160, 'Q')):
            e.write(self.reader_class+offset, e.model_stub(lambda f=fmt:self.read_primitive(f)))
        e.models.update({0x147234d00:self.record_count, 0x147234e10:self.record_asset})

    def read_primitive(self, fmt):
        if self.engine.reg('rcx') != self.binary_reader:
            raise ValueError('Unexpected binary reader')
        start = self.input.pos
        value = self.input.read(fmt)
        self.reads.append(dict(offset=start, type=fmt, value=value))
        return value

    def record_count(self):
        self.counts.append(self.engine.reg('rdx') & 0xffffffff)
        return 0  # void: RAX is ignored at 146f00660.

    def record_asset(self):
        e = self.engine
        if e.reg('rcx') != self.index:
            raise ValueError('Unexpected output index')
        p = e.reg('rdx')
        self.outputs.append(dict(value=e.read(p,'i'), reserved=e.read(p+4,'I'), key=e.read(p+8)))
        return 0  # void: RAX is ignored at 146f00816.

    def run(self, data, selector, prefix=False, expect_error=False):
        self.input = Reader(data)
        self.reads, self.outputs, self.counts = [], [], []
        self.engine.write(0x14586d100, selector, 'I')
        failure = None
        try:
            _, run, _ = self.call(0x146f00580, dict(rcx=self.deserializer, rdx=self.reader),
                                  stops=(0x146f00816,) if prefix else ())
        except ValueError:
            if not expect_error:
                raise
            run = self.runs[-1]
            failure = run['failure']
        if expect_error and (not failure or 'Input EOF' not in failure):
            raise ValueError('Truncated primitive did not stop with truthful EOF')
        return dict(inputBytes=len(data), inputSHA256=digest(data), selectorFixture=selector,
                    nativeRun=run, reads=self.reads, outputs=self.outputs, declaredCounts=self.counts,
                    finalReadPosition=self.input.pos, boundaryStopped=prefix, modeledEOF=failure)

    def boundaries(self):
        values = [(0xffffffffffffffff, []), (0x100000001, [(0,0),(7,123),(1,456)]),
                  (0x8000000000000001, [(1,-1)])]
        source = struct.pack('<i',len(values))+b''.join(
            struct.pack('<QB',k,len(v))+b''.join(struct.pack('<Bi',*x) for x in v) for k,v in values)
        cases = []
        for selector in (0,1,7):
            run = self.run(source, selector)
            expected = [dict(key=k,value=choose_variant(v,selector),reserved=0) for k,v in values]
            if run['outputs'] != expected or run['finalReadPosition'] != len(source):
                raise ValueError('Native u64/zero/multiple/signed fixture differs')
            cases.append(dict(name='u64-zero-multiple-signed-selector'+str(selector), **run))
        for length in (1,4,11,13,len(source)-1):
            cases.append(dict(name='truncated-'+str(length), **self.run(source[:length],0,expect_error=True)))
        return cases

    def code_evidence_index(self, binary):
        e = self.engine
        result = []
        for address in CONSUMERS:
            e.allow_unwind(address)
            end = e.unwind_ranges[address]
            result.append(dict(entry=hex(address), end=hex(end), exactPdata=True,
                               sha256=digest(binary[e.pe.offset(address):e.pe.offset(address)+end-address]),
                               nativeExecuted=address == 0x146f00580))
        return result


def fixed_blob_bridge(results, artifacts, client, root):
    prior = json.loads(read_verified(root/'local/migration72/d02-active/resource-blob-astra/blob-final-1.json',
        'fe10d49fdfd7fb5df910da6407fd96bfdbfb02f907cced418145c8d4b5a4d739'))
    blob = next(x for x in prior['blobs'] if Path(x['path']).stem == '09302948')
    source = read_verified(client/blob['path'], 'f307fff7f1b3bb3005ae9b15ca7c869726c2c1f75ffd73ddbff9bf4780f8dd6f')
    rows = (artifacts/results[0]['locationArtifact']['name']).read_bytes()
    selected = [row for row in struct.iter_unpack('<6i',rows) if row[0] == 9302948]
    first = selected[0]
    if first[:4] != (9302948,29475,0,blob['parsed']['logicalBlobBytes']):
        raise ValueError('First actual block interval differs from native blob contract')
    if any(a < 0 or n <= 0 or a+n > len(source) for _,_,a,n,_,_ in selected):
        raise ValueError('An actual 09302948 interval lies outside its verified file')
    return dict(blockID=9302948,path=blob['path'],inputSHA256=digest(source),
                indexRecords=len(selected), allIntervalsWithinActualFile=True,
                firstBundleID=first[1], firstOffset=first[2], firstBytes=first[3],
                firstStoredBlobSHA256=digest(source[:first[3]]), nativeHeaderLengthEqual=True,
                remainingEntriesNativeHeadersExecuted=False, payloadDecoded=False,
                compressionTag=blob['parsed']['compressionTag'])


if __name__ == '__main__':
    main()
