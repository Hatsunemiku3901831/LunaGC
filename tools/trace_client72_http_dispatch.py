"""Fixed Java builder bytes into the CN72 current-region native reader graph.

Only the listed root/RegionInfo fields are covered. HTTP routing, dispatch
cryptography, region-list identity and real SDK execution remain separate.
"""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, PE, read_verified, extract
    from .client72_emulator import Offline72
    from .client72_graph import GraphReader72
    from .trace_client72_dispatch_post import check_declarations
except ImportError:
    from extract_client72 import HASHES, PE, read_verified, extract
    from client72_emulator import Offline72
    from client72_graph import GraphReader72
    from trace_client72_dispatch_post import check_declarations

JAR_SHA256 = 'dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3'
SEED_SHA256 = '2258b87182df6e8e679c15a86184ddaf8d4b5eda24cd81bf12e85bb8a9e00637'
ROOT_TYPE, REGION_TYPE = 30677, 67961
CASES = ('minimal', 'uint32-boundary', 'empty-region', 'missing-region', 'error-retcode', 'raw-text')


def field(name, offset, tag, kind, child=None):
    row = dict(clientField=name, objectOffset=offset, tag=tag, type=kind, cardinality='singular')
    if child is not None:
        row['nestedTypeIndex'] = child
    return row


SCHEMAS = {
    ROOT_TYPE: dict(typeIndex=ROOT_TYPE, clientType='IBOFGKFAEPI', fields=[
        field('LDLIEKOAFBP', 80, 1, 'int32'), field('EHCHDIJEJEJ', 48, 2, 'string'),
        field('NBBEAACDEFM', 72, 3, 'message', REGION_TYPE), field('MNJHOKMKIIO', 64, 11, 'bytes'),
        field('DGHGFAELIDO', 32, 14, 'string')]),
    REGION_TYPE: dict(typeIndex=REGION_TYPE, clientType='IENBACJFBGI', fields=[
        field('GOFEDFBICAJ', 208, 1, 'string'), field('KJGNCFLEJLK', 316, 2, 'uint32'),
        field('IJNBKLIHNJP', 216, 36, 'string')])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--fixtures', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if not args.output.resolve().is_relative_to(root/'local'):
        parser.error('Raw output belongs under this checkout local/.')
    if args.summary_output.resolve().is_relative_to(args.client.resolve()):
        parser.error('Summary cannot be written to the client.')
    if digest(args.jar) != JAR_SHA256:
        parser.error('Use the fixed JAR, not a concurrently rebuilt artifact.')
    binary, metadata, startup = [read_verified(args.client/name, sha) for name, sha in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    validate_declarations(binary, records)
    fixtures, seed = read_fixtures(root, args.fixtures)
    engine = Offline72(binary, root/'local/migration72/python', records, False)
    runtime = HttpDispatchGraph72(engine, root/'protocol/cn-7.2')
    try:
        report = recover(runtime, fixtures, seed)
    finally:
        runtime.close()
        engine.close()
    report.update(schemaVersion=1, task='D02-R2 HTTP current-region builder wire', inputSHA256=HASHES,
                  javaJarSHA256=JAR_SHA256, dispatchSeedSHA256=SEED_SHA256,
                  sourceSHA256=digest(Path(__file__)), javaFixtureSourceSHA256=digest(root/'tools/Client72HttpDispatchFixture.java'),
                  sourceInputs={str(path.relative_to(root)).replace('\\','/'): digest(path) for path in (
                      root/'src/main/proto/QueryCurrRegionHttpRsp.proto', root/'src/main/proto/RegionInfo.proto',
                      root/'src/main/java/emu/grasscutter/server/http/dispatch/RegionHandler.java',
                      root/'tools/client72_graph.py', root/'tools/trace_client72_responses.py',
                      root/'tools/trace_client72_dispatch_post.py')},
                  models=['Synthetic managed class/object allocation and empty default string/ByteString storage',
                          'Bounded input tag/varint/blob helper models; typed readers and native ReadMessage/limits execute',
                          'Existing graph metadata/interface dispatch; no actual GC, HTTP or SDK'],
                  layers=dict(commandId='not-applicable-to-HTTP; no game CmdID assigned',
                              semanticIdentity='Existing dispatch input type and Java producer names are associated; no live route or complete business semantics asserted',
                              wire='Eight listed raw fields consumed from actual fixed-JAR Java bytes'),
                  outsideScope=['Full RegionHandler request/event/cryptographic producer execution',
                                'QueryRegionList native identity/reader and nested list',
                                'Remaining current-region oneof/config and RegionInfo/ResVersion fields',
                                'Full dispatch handler, SDK initialization, HTTP transport, live call/return and D-02'],
                  productionCodeChanged=False, clientExecuted=False, runtimeStatus='not-tested')
    summary = summarize(report)
    for path, value in ((args.output, report), (args.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(javaCases=6, nativeCombinations=len(report['results']),
                          rejectedInputs=len(report['rejections']), fields=8)))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_declarations(binary, records):
    check_declarations(binary, records)
    by_index = {row['index']: row for row in records}
    for index, entry in ((ROOT_TYPE, 0x153A598A0), (REGION_TYPE, 0x149BB4EB0)):
        row = by_index[index]
        if row['name'] != SCHEMAS[index]['clientType']:
            raise ValueError('Target type identity changed')
        if not any(method['registeredAddress'] == hex(entry) and method['virtualSlot'] == 19 for method in row['methods']):
            raise ValueError('Target typed reader changed')
        fields = {item['offset']: item for item in row['fields'] if item['offsetKind'] == 'instance'}
        for item in SCHEMAS[index]['fields']:
            declaration = fields[item['objectOffset']]
            expected_kind = 'class' if item['type'] in ('message', 'bytes') else item['type']
            if declaration['name'] != item['clientField'] or declaration['type']['kind'] != expected_kind:
                raise ValueError('Target field identity/layout changed')
    pe = PE(binary); address = 0x153A597FC; raw = binary[pe.offset(address, 7):][:7]
    if raw[:3] != bytes.fromhex('488b1d') or address + 7 + int.from_bytes(raw[3:], 'little', signed=True) != 0x1458C3CC0:
        raise ValueError('Current-region clone class identity changed')


def read_fixtures(root, directory):
    manifest = json.loads((directory/'manifest.json').read_text('utf8'))
    if [row['name'] for row in manifest] != list(CASES):
        raise ValueError('Java fixture set changed')
    seed = (root/'src/main/resources/keys/dispatchSeed.bin').read_bytes()
    if hashlib.sha256(seed).hexdigest() != SEED_SHA256 or len(seed) != 2076:
        raise ValueError('Dispatch seed changed')
    fixtures = {name: (directory/(name+'.response.bin')).read_bytes() for name in CASES}
    # Literal wire keys/bytes, independently specified from the native store table.
    minimal_region = bytes.fromhex('0a093132372e302e302e3110d5ac01a20207686b34655f636e')
    maximum_region = bytes.fromhex('0a0f666978747572652e696e76616c696410ffffffff0fa20207686b34655f636e')
    if (directory/'minimal.region.bin').read_bytes() != minimal_region or (directory/'uint32-boundary.region.bin').read_bytes() != maximum_region:
        raise ValueError('Actual Java leaf bytes differ from independent literal wire')
    seed_field = bytes.fromhex('5a9c10') + seed
    if fixtures['minimal'] != b'\x1a' + bytes([len(minimal_region)]) + minimal_region + seed_field:
        raise ValueError('Actual Java response differs from independent nested wire')
    if fixtures['empty-region'] != b'\x1a\0' + seed_field or fixtures['missing-region'] != seed_field:
        raise ValueError('Java absent versus empty nested encoding changed')
    return fixtures, seed


class HttpDispatchGraph72(GraphReader72):
    def __init__(self, engine, directory):
        super().__init__(engine, directory)
        self.schemas.update(SCHEMAS)
        for index, cell, entry in ((ROOT_TYPE, 0x1458C3CC0, 0x153A598A0),
                                    (REGION_TYPE, 0x1458DEE48, 0x149BB4EB0)):
            klass = engine.alloc(1024); table = engine.alloc(16)
            engine.write(cell, klass); engine.write(klass+0xC7, 1, 'B')
            engine.write(klass+0x80, table); engine.write(klass+0xC0, 1, 'H'); engine.write(klass+0xBE, 32, 'H')
            engine.write(table, self.interface); engine.write(table+8, 0, 'I'); engine.write(klass+0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry
            engine.allow(entry)
            for method in engine.records[index]['methods']:
                if method['name'] == '.ctor':
                    engine.allow(int(method['registeredAddress'], 16))
        self.heap_floor = engine.heap_cursor


def expected_result(name, seed):
    root = dict(MNJHOKMKIIO=dict(kind='bytes', hex=seed.hex()))
    if name != 'missing-region':
        root['NBBEAACDEFM'] = ({} if name == 'empty-region' else dict(
            GOFEDFBICAJ='fixture.invalid' if name == 'uint32-boundary' else '127.0.0.1',
            KJGNCFLEJLK=0xffffffff if name == 'uint32-boundary' else 22101, IJNBKLIHNJP='hk4e_cn'))
    if name == 'error-retcode':
        root.update(LDLIEKOAFBP=-4005, EHCHDIJEJEJ='fixture-denied')
    if name == 'raw-text':
        root['DGHGFAELIDO'] = '9007199254740993'
    return root


def recover(runtime, fixtures, seed):
    traces, results, rejections = [], [], []
    cases = [(name, fixtures[name], expected_result(name, seed)) for name in CASES]
    cases.append(('merged-region-overwrite', fixtures['minimal']+fixtures['uint32-boundary'], expected_result('uint32-boundary', seed)))
    explicit_zero = expected_result('error-retcode', seed); del explicit_zero['LDLIEKOAFBP']
    cases.append(('negative-retcode-cleared-by-explicit-zero', fixtures['error-retcode']+b'\x08\0', explicit_zero))
    for name, wire, expected in cases:
        result = runtime.run_graph(ROOT_TYPE, wire)
        if result['result'] != expected:
            raise ValueError('Actual Java/native object mismatch: '+name)
        entered = [row.get('typeIndex') for row in result['entered']]
        expected_regions = 0 if name == 'missing-region' else 2 if name == 'merged-region-overwrite' else 1
        if entered.count(ROOT_TYPE) != 1 or entered.count(REGION_TYPE) != expected_regions:
            raise ValueError('Native typed reader coverage changed')
        traces.append(dict(name=name, **result))
        results.append(dict(name=name, wireLength=len(wire), wireSHA256=hashlib.sha256(wire).hexdigest(),
                            rootReaderEntries=1, regionReaderEntries=expected_regions,
                            regionPresent='NBBEAACDEFM' in expected,
                            nested=expected.get('NBBEAACDEFM'), retcode=expected.get('LDLIEKOAFBP', 0),
                            message=expected.get('EHCHDIJEJEJ', ''), rawTag14Text=expected.get('DGHGFAELIDO', ''),
                            seedLength=len(seed), seedSHA256=hashlib.sha256(seed).hexdigest(), nativeReaderReturned=True))
    for name, wire in (('truncated-java-seed', fixtures['minimal'][:-1]),
                        ('nested-length-exceeds-input', bytes.fromhex('1a050a0941')),
                        ('negative-nested-length', bytes.fromhex('1affffffff0f'))):
        try:
            runtime.run_graph(ROOT_TYPE, wire)
        except ValueError as error:
            rejections.append(dict(name=name, wireSHA256=hashlib.sha256(wire).hexdigest(), reason=str(error),
                                   nativeErrorBranch='Native error branch:' in str(error)))
        else:
            raise ValueError('Malformed fixture unexpectedly returned: '+name)
    return dict(schemas=list(SCHEMAS.values()), results=results, rejections=rejections, traces=traces,
                javaFixtureCount=6, nativeCombinationCount=8, rejectedFixtureCount=3,
                fullCurrentRegionSchema=False, fullRegionInfoSchema=False, regionListConsumed=False)


def summarize(report):
    return {key: value for key, value in report.items() if key != 'traces'}


if __name__ == '__main__':
    main()
