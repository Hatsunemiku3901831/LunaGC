"""Actual fixed-JAR RegionList bytes into independently bound CN72 native readers.

The global-dispatch callback/class/codec/literal identities are checked first.
Managed storage and bounded wire helpers are explicit offline models; no live
client, full callback, HTTP route or transport trust is exercised.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, PE, read_verified, extract
    from .extract_client72_literals import Literals72
    from .client72_emulator import Offline72, GLOBAL
    from .client72_graph import GraphReader72
except ImportError:
    from extract_client72 import HASHES, PE, read_verified, extract
    from extract_client72_literals import Literals72
    from client72_emulator import Offline72, GLOBAL
    from client72_graph import GraphReader72

JAR_SHA256 = 'dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3'
SEED_SHA256 = '2258b87182df6e8e679c15a86184ddaf8d4b5eda24cd81bf12e85bb8a9e00637'
CASES = ('empty-response', 'one', 'two', 'empty-entry', 'error-retcode', 'raw-text')
ENTRIES = {87445: (0x1458C5708, 0x152FEF410), 13336: (0x1458D5748, 0x14E455B20)}


def field(name, offset, tag, kind, child=None):
    row = dict(clientField=name, objectOffset=offset, tag=tag, type=kind, cardinality='singular')
    if child is not None:
        row.update(nestedTypeIndex=child, cardinality='repeated')
    return row


SCHEMAS = {
    87445: dict(typeIndex=87445, clientType='MADDBHHMFNG', fields=[
        field('MNJHOKMKIIO', 24, 5, 'bytes'), field('PNECJNBHCNG', 32, 2, 'message', 13336),
        field('AMANAEJDNNF', 40, 6, 'bytes'), field('LCCLHKIFBMJ', 48, 7, 'bool'),
        field('LDLIEKOAFBP', 52, 1, 'int32')]),
    13336: dict(typeIndex=13336, clientType='JCOJNGKFIOM', fields=[
        field('NOEEBKJAFAG', 24, 3, 'string'), field('HDLLHEMPPCF', 32, 2, 'string'),
        field('OKDLHHPJOLA', 40, 1, 'string'), field('MJCIFCHMPGH', 48, 4, 'string')])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('client', 'jar', 'fixtures', 'output', 'summary-output'):
        parser.add_argument('--'+flag, type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if not args.output.resolve().is_relative_to(root/'local'):
        parser.error('Raw trace belongs under this checkout local/.')
    if args.summary_output.resolve().is_relative_to(args.client.resolve()):
        parser.error('Summary cannot be written inside the client.')
    if digest(args.jar) != JAR_SHA256:
        parser.error('Use the fixed Java JAR input.')
    binary, metadata, startup = [read_verified(args.client/n, h) for n, h in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    sources = validate_identity(binary, metadata, records)
    fixtures, seed = read_fixtures(root, args.fixtures)
    engine = Offline72(binary, root/'local/migration72/python', records, False)
    graph = RegionListGraph72(engine, root/'protocol/cn-7.2')
    try:
        report = recover(graph, fixtures, seed)
    finally:
        graph.close()
        engine.close()
    report.update(schemaVersion=1, task='D-01b5d', inputSHA256=HASHES, javaJarSHA256=JAR_SHA256,
                  javaSourceSHA256=digest(root/'tools/Client72RegionListFixture.java'),
                  sourceSHA256=digest(Path(__file__)), identityEvidence=sources,
                  sourceInputs={str(p.relative_to(root)).replace('\\', '/'): digest(p) for p in (
                      root/'src/main/proto/QueryRegionListHttpRsp.proto', root/'src/main/proto/RegionSimpleInfo.proto',
                      root/'src/main/java/emu/grasscutter/server/http/dispatch/RegionHandler.java',
                      root/'tools/client72_graph.py', root/'tools/trace_client72_responses.py')},
                  models=['Synthetic managed class/object/string/ByteString/array storage',
                          'Bounded tag/varint/blob helpers and fixed-capacity generic context; native typed readers, list append and nested limits execute',
                          'Java Base64 text independently decoded in the harness; native Base64 and full HTTP callback are not executed'],
                  layers=dict(commandId='HTTP has no game CmdID assigned here',
                              identity='Fixed class/codec and callback with named QueryRegionListHttpRsp error literal',
                              wire='Nine raw fields consumed from actual fixed-JAR Java bytes; no global map promotion'),
                  runtimeStatus='not-tested', liveClientExecuted=False, productionCodeChanged=False,
                  outsideScope=['Full callback including IFix, allocation, server grouping, config consumption and completion Action',
                                'Native Base64 decoding and actual HTTP transport/callback execution',
                                'Endpoint URL selection, TLS/certificate/signature trust, SDK loading conditions and live call/return',
                                'Real GC/CRT, full generic list capacity/Unicode behavior and D-02'])
    for path, value in ((args.output, report), (args.summary_output, summarize(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2)+'\n', 'utf8')
    print(json.dumps(dict(javaCases=len(CASES), nativeCombinations=len(report['results']),
                          rejectedInputs=len(report['rejections']), fields=9)))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_identity(binary, metadata, records):
    pe = PE(binary)
    anchors = {
        0x151225100: '4889d7', 0x15122514E: '48897d40',
        0x151225469: '488b0d98026af4', 0x151225478: 'e8432c22fc',
        0x151225480: '488b4540', 0x151225498: 'e873e69908', 0x1512254A7: 'e894b4c7f6',
        0x151225967: '488b0d3a8b8ef4', 0x152FEEF0C: '488b1df5678df2',
        0x14E455CAC: '488b1d95fa47f7', 0x152FEF106: '488b3d3b668ef2',
        0x152FEF125: 'c7401012000000', 0x152FEF12C: '48897818',
        0x152FEF148: '4889b040900200',
        0x14F8949F8: '488b5318', 0x14F894A07: '488b4320', 0x14F894D3E: 'e89d039901',
        0x14F8951C0: 'e84bb89403', 0x151D8D723: 'e888d95b08', 0x151D8D9CB: '48894718',
        0x1512192B6: 'e815a1ec08', 0x1512193E9: 'e872d5fc08', 0x1512194FA: '48894118',
        0x1512238B2: 'e8b981e3f5', 0x1512238C1: 'e81ad0faf5'}
    for address, encoded in anchors.items():
        expected = bytes.fromhex(encoded)
        if binary[pe.offset(address, len(expected)):][:len(expected)] != expected:
            raise ValueError('Fixed callback/class/codec anchor changed: '+hex(address))
    rows = {r['index']: r for r in records}
    for index, (_, entry) in ENTRIES.items():
        row = rows[index]
        if row['name'] != SCHEMAS[index]['clientType'] or not any(
                m['registeredAddress'] == hex(entry) and m['virtualSlot'] == 19 for m in row['methods']):
            raise ValueError('RegionList type/reader identity changed')
        fields = {f['offset']: f for f in row['fields'] if f['offsetKind'] == 'instance'}
        if len(fields) != len(SCHEMAS[index]['fields']):
            raise ValueError('RegionList instance field set changed')
        for f in SCHEMAS[index]['fields']:
            declaration = fields[f['objectOffset']]
            expected_kind = 'genericinst' if f['type'] == 'message' else 'class' if f['type'] == 'bytes' else f['type']
            if declaration['name'] != f['clientField'] or declaration['type']['kind'] != expected_kind:
                raise ValueError('RegionList field identity changed')
            if f['type'] == 'message' and (declaration['type']['typeDefinition'] != 25060 or
                                          declaration['type']['arguments'][0]['typeDefinition'] != 13336):
                raise ValueError('RegionList repeated child changed')
            if f['type'] == 'bytes' and declaration['type']['typeDefinition'] != 18063:
                raise ValueError('RegionList ByteString declaration changed')
    for owner, name, address in ((23177, 'CLDMBMJBNJF', 0x1512250E0),
                                  (189, 'FromBase64CharPtr', 0x159BC3B10),
                                  (69908, 'MOMMACBEMEK', 0x147EA0940),
                                  (23186, 'MoveNext', 0x14F8949B0), (23178, 'MoveNext', 0x151D8D620),
                                  (23199, 'MoveNext', 0x15122A290),
                                  (8862, '.ctor', 0x15A34B0B0), (1814, 'Create', 0x15A0E33D0),
                                  (1904, 'GetResponse', 0x15A1E6960),
                                  (12832, 'GetGlobalDispatchUrl', 0x1531E0A10),
                                  (9817, 'InitEnv', 0x14705BA70),
                                  (10201, 'CreateHttpReuquest', 0x1471D08E0)):
        if not any(m['name'] == name and m['registeredAddress'] == hex(address) for m in rows[owner]['methods']):
            raise ValueError('Global dispatch source method changed')
    for offset in (24, 32):
        declaration = next(f for f in rows[23181]['fields'] if f['offset'] == offset)
        if declaration['type']['kind'] != 'string':
            raise ValueError('HTTP completion body/error closure changed')
    literal = Literals72(binary, metadata)
    selected = next(r for r in literal.group(literal.global_group) if r['index'] == 35025)
    value = literal.decode(35025)
    if selected['cell'] != '0x145b0e4a8' or value != '[GlobalDispatch] Parse QueryRegionListHttpRsp: ':
        raise ValueError('Independent global-dispatch parse literal changed')
    return dict(callback='0x1512250e0', bodyArgument='rdx stored at frame+0x40',
                base64Call='0x151225498 -> System.Convert.FromBase64CharPtr',
                parseCall='0x1512254a7 -> 0x147ea0940', rootClassCell='0x1458c5708',
                childClassCell='0x1458d5748', codecInitializer='0x152fef100',
                codecWireKey=18, codecGlobalOffset='0x29040', literal=dict(selected, value=value),
                transportSource=dict(scope='static source/data flow; no transport executed',
                    callerCoroutine='23186/0x14f8949b0', directCallbackCall='0x14f894d3e',
                    bodyClosure='23181+0x18', errorClosure='23181+0x20',
                    branches=['0: UnityEngine.WWW coroutine 23178/0x151d8d620',
                              '1: System.Net.HttpWebRequest helper 0x151219230',
                              '2: coroutine 23199/0x15122a290 -> helper 0x151223770 -> HoYoSDKNetworkInterface.InitEnv / HoYoSDKHttpWebRequest.CreateHttpReuquest; worker completion untraced'],
                    bodyStores=['WWW 0x151d8d9cb', 'HttpWebRequest 0x1512194fa'],
                    urlBuilder='MoleMole.Miscs.GetGlobalDispatchUrl 0x1531e0a10',
                    actualRuntimeSelectedBranch='unknown'))


def read_fixtures(root, directory):
    manifest = json.loads((directory/'manifest.json').read_text('utf8'))
    if [r['name'] for r in manifest] != list(CASES):
        raise ValueError('Java fixture set changed')
    seed = (root/'src/main/resources/keys/dispatchSeed.bin').read_bytes()
    if len(seed) != 2076 or hashlib.sha256(seed).hexdigest() != SEED_SHA256:
        raise ValueError('Fixed dispatch seed changed')
    fixtures = {n: (directory/(n+'.bin')).read_bytes() for n in CASES}
    for name, wire in fixtures.items():
        if base64.b64decode((directory/(name+'.base64.txt')).read_text('ascii'), validate=True) != wire:
            raise ValueError('Java Base64 and binary differ')
    child = bytes.fromhex('0a09666978747572655f6112075469746c6520411a0a4445565f5055424c49432230687474703a2f2f3132372e302e302e313a383038382f71756572795f6375725f726567696f6e2f666978747572655f61')
    tail = bytes.fromhex('2a9c10')+seed+bytes.fromhex('320500017f80ff3801')
    if fixtures['empty-response'] != b'' or fixtures['empty-entry'] != b'\x12\0'+tail:
        raise ValueError('Absent list versus explicit empty item changed')
    if fixtures['one'] != b'\x12'+bytes([len(child)])+child+tail:
        raise ValueError('Actual Java list differs from independent literal wire')
    return fixtures, seed


class RegionListGraph72(GraphReader72):
    def __init__(self, engine, directory):
        super().__init__(engine, directory)
        e = engine
        self.schemas.update(SCHEMAS)
        for index, (cell, entry) in ENTRIES.items():
            klass = e.alloc(1024); table = e.alloc(16)
            e.write(cell, klass); e.write(klass+0xC7, 1, 'B')
            e.write(klass+0x80, table); e.write(klass+0xC0, 1, 'H'); e.write(klass+0xBE, 32, 'H')
            e.write(table, self.interface); e.write(table+8, 0, 'I'); e.write(klass+0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry
            e.allow(entry)
            for method in e.records[index]['methods']:
                if method['name'] == '.ctor':
                    e.allow(int(method['registeredAddress'], 16))
        codec = e.alloc(); e.write(codec+0x10, 18, 'I'); e.write(codec+0x18, self.classes[13336])
        e.write(GLOBAL+0x29040, codec)
        self.heap_floor = e.heap_cursor


def expected_result(name, seed):
    if name == 'empty-response':
        return {}
    first = dict(NOEEBKJAFAG='DEV_PUBLIC', HDLLHEMPPCF='Title A',
                 OKDLHHPJOLA='9007199254740993' if name == 'raw-text' else 'fixture_a',
                 MJCIFCHMPGH='http://127.0.0.1:8088/query_cur_region/fixture_a')
    children = [{}] if name == 'empty-entry' else [first]
    if name == 'two':
        children.append(dict(NOEEBKJAFAG='fixture_other', HDLLHEMPPCF='Title B', OKDLHHPJOLA='fixture_b',
                             MJCIFCHMPGH='https://fixture.invalid/query_cur_region/fixture_b'))
    result = dict(MNJHOKMKIIO=dict(kind='bytes', hex=seed.hex()), PNECJNBHCNG=children,
                  AMANAEJDNNF=dict(kind='bytes', hex='00017f80ff'), LCCLHKIFBMJ=True)
    if name == 'error-retcode':
        result['LDLIEKOAFBP'] = -4005
    return result


def recover(graph, fixtures, seed):
    results, traces, rejections = [], [], []
    cases = [(n, fixtures[n], expected_result(n, seed)) for n in CASES]
    merged = expected_result('one', seed); merged['PNECJNBHCNG'] *= 2
    cases.append(('repeated-root-appends', fixtures['one']+fixtures['one'], merged))
    cleared = expected_result('one', seed); del cleared['LCCLHKIFBMJ']
    cases.append(('explicit-zero-clears-retcode-and-bool', fixtures['error-retcode']+b'\x08\0\x38\0', cleared))
    for name, wire, expected in cases:
        trace = graph.run_graph(87445, wire)
        if trace['result'] != expected:
            raise ValueError('Actual Java/native list mismatch: '+name)
        children = expected.get('PNECJNBHCNG', [])
        entries = [r.get('typeIndex') for r in trace['entered']]
        if entries.count(87445) != 1 or entries.count(13336) != len(children):
            raise ValueError('Native list reader coverage changed')
        traces.append(dict(name=name, **trace))
        results.append(dict(name=name, wireLength=len(wire), wireSHA256=hashlib.sha256(wire).hexdigest(),
                            rootEntries=1, childEntries=len(children), children=children,
                            retcode=expected.get('LDLIEKOAFBP', 0), enableLoginPc=expected.get('LCCLHKIFBMJ', False),
                            seedLength=len(seed) if expected else 0,
                            seedSHA256=hashlib.sha256(seed).hexdigest() if expected else None,
                            configHex='00017f80ff' if expected else '', nativeReaderReturned=True))
    for name, wire in (('truncated-child-string', bytes.fromhex('12050a0941')),
                        ('negative-child-length', bytes.fromhex('12ffffffff0f')),
                        ('truncated-seed', bytes.fromhex('2a9c10')+seed[:-1])):
        try:
            graph.run_graph(87445, wire)
        except ValueError as error:
            rejections.append(dict(name=name, wireSHA256=hashlib.sha256(wire).hexdigest(), reason=str(error),
                                   nativeErrorBranch='Native error branch:' in str(error)))
        else:
            raise ValueError('Malformed list unexpectedly returned: '+name)
    return dict(schemas=list(SCHEMAS.values()), javaFixtureCount=len(CASES), nativeCombinationCount=len(cases),
                results=results, rejections=rejections, traces=traces, allDeclaredInstanceFieldsCovered=True,
                fullCallbackExecuted=False)


def summarize(report):
    return {k: v for k, v in report.items() if k != 'traces'}


if __name__ == '__main__':
    main()
