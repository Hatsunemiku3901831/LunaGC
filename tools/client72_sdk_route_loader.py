"""Fixed CN72 SDK configuration carrier and original route-writer fragments.

All execution is in offline emulators. Configuration and signing inputs remain
inside ignored private artifacts; reports contain fixed routes and booleans.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'local/migration72/python')]
from client72_sdk_transport import Image, digest
from client72_sdk_response_contract import ResponseFixture
from client72_resource_blob import BlobProbe, parse_header
from client72_resource_payload import PayloadProbe, lz4_block
from client72_resource_provider import Snapshot, u64
from extract_client72 import PE, extract, Names, HEADER_VA, BODY, MASK32, MASK64, STRIDE, CODE_REGISTRATION, number
from extract_client72_literals import Literals72, USAGE_REGISTRATION
import capstone
from unicorn import x86_const as ux

RAW = ROOT / 'local/migration72/d02-resume-20261108/r2-route'
INVENTORY = 'local/migration72/d02-resume-20261108/initial-inventory.private.json'
BUNDLE = 'YuanShen_Data/StreamingAssets/MiHoYoSDKRes/PC/mihoyo_sdk_res'
BUNDLE_SHA = '8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda'
ASSET = 'assets/plugins/mihoyosdk/resources/config/server_pc.json'
CONTEXT = 'local/migration72/d02-active/plain/initialization-archive-elevated6.json'
FIXED = {
    INVENTORY: 'f891bafd31c950985ce214cad90e0ba24d2e8733a24dbdca21f9823474c6ff25',
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    CONTEXT: '63025b39285dca6890ae84e5a4cb6f304f44c7ddf46c19911b6a930351429c0d',
    'local/migration72/d02-active/sdk-response-astra/rea-final.json': '3ffeabe2276021f9bd4fcdc77a3172cb3fd52b93cfde706a18d7c401f9e3528a',
    'local/migration72/d02-resume-20261108/r2-route/rea-final.json': 'da35fb0b37ef5b661a3b6c76040b9bb3ba1c4119d949ccda06bd28e14603dc53',
    'tools/client72_sdk_transport.py': '43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237',
    'tools/client72_sdk_login_chain.py': '53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2',
    'tools/client72_sdk_response_contract.py': '792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5',
    'tools/client72_resource_blob.py': 'e0be8ece8071730ea7a310208931e71eb358f07646dcd103dd28661d0cfee798',
    'tools/client72_resource_payload.py': 'b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa',
    'tools/client72_resource_provider.py': '16c5ab0f8a4f2d1a653cb99c8dd972c4b20b742a75e7adbc0f397edb905a7c7f',
    'tools/client72_resource_index_native.py': '3a0fc703c63429d66775a07c1dbfa8c515678461fa00b5f6e050e98cab16a1a0',
    'tools/client72_emulator.py': '8007d2594ef8058a4f2a9f1c24094fb5d2551a202b4e52154876cdfe77a17c3c',
    'tools/trace_client72_token.py': 'f2fbc4728f73447dff4f7320beb3a7a31fb546287272b62ccd88a8e940312fa0',
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/extract_client72_literals.py': '865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed',
    'tools/extract_client72_signatures.py': 'ee92527f3607be774c28be694d6f57e9eed3aa40068b7b584979e27b6470c349',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
    'local/migration72/python/unicorn/lib/unicorn.dll': '010d85dce84b8f6ffaec3d5420f1b5f7768f859eb831be574c7da23e656ee8f1',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/unicorn/unicorn_py3/unicorn.py': 'b4de7d560790a587157b17edd71c83b4f2d2bade2447d1a5433c497468ac8aeb',
    'local/migration72/python/unicorn/x86_const.py': 'd7663db6425f52838f3e79e45a8fe22b6c02be7edb44367ea2118aec04ee5728',
}
EXPECTED_ENVS = [0, 1, 4, 6, 8, 9, 22, 19, 2, 3, 5, 7, 11, 12, 21, 20]
COMBO_HOSTS = [
    'hk4e-sdk.mihoyo.com', 'sandbox-sdk.mihoyo.com', 'preapi-takumi.mihoyo.com',
    'devapi-takumi.mihoyo.com', 'ptsapi-takumi.mihoyo.com', 'api-beta-sdk.mihoyo.com',
    'audit-api.mihoyo.com', 'hotfix-sdk.mihoyo.com', 'hk4e-sdk-os.hoyoverse.com',
    'sandbox-sdk-api.hoyoverse.com', 'hk4e-pre-sdk-os.hoyoverse.com',
    'testing-sg-sdk-api.hoyoverse.com', 'hk4e-beta-sdk-os.hoyoverse.com',
    'testing-sg-pts-api.hoyoverse.com', 'hk4e-sdk-tw.mihoyo.com', 'hotfix-sdk-os.hoyoverse.com',
]


def main():
    args = arguments()
    fixed = verify_inputs(args.client)
    binary = (args.client / 'YuanShen.exe').read_bytes()
    metadata = (args.client / 'YuanShen_Data/Managed/Metadata/global-metadata.dat').read_bytes()
    sdk = Image((args.client / 'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes())
    exe = Image(binary)
    declared = declarations(binary, metadata)
    static = static_proof(exe, sdk)
    artifacts = args.output.with_suffix('')
    artifacts.mkdir(parents=True, exist_ok=False)
    data = (args.client / BUNDLE).read_bytes()
    blob, decoded, runs = decode_bundle(binary, data)
    binding, config_bytes = serialized_config(decoded, blob['parsed']['content'][0]['size'])
    (artifacts / 'mihoyo_sdk_res.serialized.private.bin').write_bytes(decoded)
    (artifacts / 'server_pc.private.json').write_bytes(config_bytes)
    configs = json.loads(config_bytes)
    routes = classify_configs(configs)
    samples = native_fragments(sdk, configs)
    if not all(x['nativeGetterValueEqual'] and x['nativeGetterReturnedPointer64'] and x['appKeyFieldEqual'] for x in samples):
        raise ValueError('Original route/key fragment result differs from actual resource input')
    result = {
        'schemaVersion': 1, 'task': 'R2-ROUTE-SDK-resource-loader',
        'fixedInputs': fixed, 'declarations': declared, 'static': static,
        'bundle': blob, 'payload': runs, 'asset': binding, 'fixedComboRoutes': routes,
        'nativeFragments': samples,
        'signingSource': {'configModelBaseOffset': '0xe8', 'appKeyModelOffset': '0x130',
                          'appKeyConfigOffset': '0x218', 'requestBuilderRva': '0x3be5c0',
                          'cryptoVtableRva': '0x6d1cb8', 'slotByteOffset': '0x58',
                          'signerRva': '0x3958e0', 'appKeyBytesOrHashRecorded': False,
                          'signatureAlgorithmExecuted': False},
        'models': [
            'Archive context is the previously frozen observed 256-byte table. No target process or network exists.',
            'Blob/header/cache storage is explicit; original header checks and all 442 prefix transforms execute.',
            'All blocks use independent bounded LZ4. Only first and last additionally execute the complete native tag3 codec.',
            'Serialized v17 type tree, object table, class142 container and class49 strings are independently parsed.',
            'SDK fixtures stage one actual comboLogin entry and actual app_key in original parsed/cloned CJsonObject.',
            'The two exact writer fragments are separate entries with documented RSP/RBP/R15/R13/RSI values; whole Init is not executed.',
            'Original 13a1b0 aligned allocation wrapper executes. Only underlying storage allocation/free and single-thread mutexes are modeled.',
            'Original outer/nested JSON parsing, key copy, map insert, getter and JSON destruction execute. No parse/lookup/success result is modeled.',
        ],
        'remaining': [
            'Natural selected env, complete managed-to-native Init payload and final request are unobserved.',
            'SDK globals7ad200/240 have no direct producer in the bounded static search; indirect or external writes remain unknown.',
            'Whole 378850 stopped at an uninitialized CRT locale encoded callback in exploratory runs; it is not promoted to a full Init result.',
            'The complete original URL-map writer was not accepted under the two-million SDK instruction cap; the native sample projects only comboLogin.',
            'Combo signing source is bound, but the signature algorithm/verification and any patch input contract are not approved by this card.',
        ],
        'sourceSHA256': digest(Path(__file__).read_bytes()),
        'wholeSdkInitExecuted': False, 'naturalRouteSelected': False,
        'clientExecuted': False, 'productionConfigurationChanged': False, 'd02Passed': False,
    }
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    print(json.dumps({'task': result['task'], 'routes': len(routes), 'nativeFragments': len(samples),
                      'artifactBound': True, 'naturalRouteSelected': False, 'd02Passed': False}))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.client, args.output = args.client.resolve(), args.output.resolve()
    if args.output.exists() or args.output.with_suffix('').exists() or not args.output.is_relative_to(RAW.resolve()):
        parser.error('A new report and artifact directory inside this card ignored raw directory are required')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    return args


def verify_inputs(client):
    for name, expected in FIXED.items():
        if digest((ROOT / name).read_bytes()) != expected:
            raise ValueError('Frozen evidence/source changed: ' + name)
    inventory = json.loads((ROOT / INVENTORY).read_bytes())
    inputs = {}
    for row in inventory['inputs']:
        with (client / row['file']).open('rb') as stream:
            actual = hashlib.file_digest(stream, 'sha256').hexdigest()
        if actual != row['sha256'] or (client / row['file']).stat().st_size != row['size']:
            raise ValueError('Fixed client identity changed: ' + row['file'])
        inputs[row['file']] = actual
    if digest((client / BUNDLE).read_bytes()) != BUNDLE_SHA:
        raise ValueError('Accurate SDK resource bundle changed')
    manifest = (client / (BUNDLE + '.manifest')).read_bytes()
    if digest(manifest) != 'b081285e03c4a94e7955a8a8e0a485fcec935f3beb10ee558f5a626692a4048f':
        raise ValueError('SDK asset manifest changed')
    if b'- Assets/Plugins/MiHoYoSDK/Resources/Config/server_pc.json' not in manifest.splitlines():
        raise ValueError('Exact SDK asset manifest entry absent')
    package = (client / 'pkg_version').read_bytes()
    if digest(package) != '0b8d5de9333f241af06ac3989f8bf3d090f086f71bedcdece844d7cbf194d219':
        raise ValueError('Fixed installation manifest changed')
    rows = [json.loads(x) for x in package.splitlines() if x]
    selected = [x for x in rows if x.get('remoteName') == BUNDLE]
    if len(selected) != 1 or selected[0]['fileSize'] != 24223855 or selected[0]['md5'] != hashlib.md5((client / BUNDLE).read_bytes()).hexdigest():
        raise ValueError('Actual SDK bundle does not match the installation manifest')
    return {'clientFiles': inputs, 'frozenEvidenceAndSources': FIXED,
            'bundleSHA256': BUNDLE_SHA, 'bundleManifestSHA256': digest(manifest),
            'packageManifestSHA256': digest(package), 'packageEntrySizeAndMd5Match': True}


def declarations(binary, metadata):
    pe = PE(binary)
    types, summary = extract(binary, metadata)
    names = Names(metadata, int(summary['stringBase'], 16))
    header = pe.offset(HEADER_VA, BODY)
    table = int(summary['typeOffset'], 16)
    method_base = BODY + (number(binary, header + 0xb0) ^ 0x51324a1f)
    pointers = pe.offset(number(binary, pe.offset(CODE_REGISTRATION, 0x78) + 0x70, 'Q'))
    methods = []
    for index in (70156, 70164, 70395, 71135, 71136):
        key = (((index * 0x1639) ^ 0x5e01c03b) * 0x106b250c17f34ccb + 0x069ffdcbe218faf4) & MASK64
        text = names.decode((number(metadata, method_base + index * 26 + 8) - key + 0xde01c441) & MASK32)
        owners = []
        for owner in (9798, 9857, 9987):
            first = number(metadata, table + owner * STRIDE + 0xc) ^ 0x5abb91bd
            count = (number(metadata, table + owner * STRIDE + 0x3a, 'H') + 0xbf1a) & 0xffff
            if first <= index < first + count:
                owners.append(owner)
        if len(owners) != 1:
            raise ValueError('Selected SDK method owner is ambiguous')
        methods.append({'methodIndex': index, 'name': text, 'ownerType': owners[0], 'ownerName': types[owners[0]]['name'],
                        'registeredVA': hex(number(binary, pointers + index * 8, 'Q'))})
    lit = Literals72(binary, metadata)
    selected = [dict(x, value=lit.decode(x['index'])) for x in lit.group(lit.global_group) if x['index'] in (14110, 14111, 15092)]
    expected = {14110: 'Assets/Plugins/MiHoYoSDK/Resources/', 14111: 'MiHoYoSDKRes/PC/mihoyo_sdk_res', 15092: 'Config/server_pc.json'}
    if {x['index']: x['value'] for x in selected} != expected:
        raise ValueError('Accurate resource literals changed')
    cache = number(binary, pe.offset(USAGE_REGISTRATION) + 0x10, 'Q')
    end = ((number(metadata, lit.groups_base + (lit.global_group + 1) * 4) + 0x8bff3e05) & MASK32) ^ (((lit.global_group + 1) * 0x5843eaa0 - 0x443c8630) & MASK32)
    use = []
    for i in range(end):
        key = (i * 0x57c15286 + 0x26ddebda) & MASK32
        source = ((number(metadata, lit.usages_base + i * 8) + 0xabbdcabf) & MASK32) ^ key ^ 0x2a357d90
        slot = number(metadata, lit.usages_base + i * 8 + 4) ^ key ^ 0x1855aa65
        if source >> 29 == 3 and cache + slot * 8 == 0x14597a3e8:
            use.append({'usageRow': i, 'tag': 3, 'methodDefinition': source & 0x1fffffff, 'cell': '0x14597a3e8'})
    if use != [{'usageRow': 734367, 'tag': 3, 'methodDefinition': 71136, 'cell': '0x14597a3e8'}]:
        raise ValueError('Delegate MethodInfo identity differs')
    layout_lookup = BODY + ((number(binary, header + 0x168) + 0xb01c236d) & MASK32)
    layout_records = BODY + ((number(binary, header + 0x14c) + 0xde642132) & MASK32)
    field_offsets = BODY + ((number(binary, header + 0x154) + 0x936a8a40) & MASK32)
    owner, field = 4013, 18453
    first = number(metadata, table + owner * STRIDE + 8) ^ 0x72239b10
    layout = number(metadata, layout_lookup + owner * 4, 'i')
    field_first = number(metadata, layout_records + layout * 12 + 8)
    offset = number(metadata, field_offsets + (field_first + field - first) * 4)
    field_base = BODY + (number(binary, header + 0x194) ^ 0x536b48b9)
    field_key = (((((field * 0xae93) ^ 0x112d0196) * 0x380b5207 & MASK64) >> 12) * 0x4f038c54 + 0x2c41e5a3) & MASK32
    field_name = names.decode(((number(metadata, field_base + field * 8) ^ 0x2c691d30) - field_key) & MASK32)
    if types[owner]['name'] != 'SDKDelegate' or field_name != 'LoadFile' or offset != 0x04001530:
        raise ValueError('SDKDelegate.LoadFile encoded layout or identity changed')
    return {'methods': methods, 'literals': selected, 'delegateMethodUsage': use,
            'resourceDelegate': {'ownerType': owner, 'owner': types[owner]['name'], 'fieldIndex': field,
                                 'fieldName': field_name, 'metadataEncodedOffset': hex(offset),
                                 'staticOffset': '0x1530', 'storagePointerCell': '0x14586b600',
                                 'offsetAuthority': 'Exact Register store and LoadLocalConfig reader, not masking the metadata marker'}}


def window(pe, start, end):
    raw = pe.data[pe.offset(pe.base + start, end - start):][:end - start]
    return {'startRva': hex(start), 'endRva': hex(end), 'sha256': digest(raw),
            'instructions': [[hex(a - pe.base), m, op] for a, _, m, op in pe.instructions(start, end)]}


def static_proof(exe, sdk):
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    cs.detail, cs.skipdata = True, True
    references = []
    for address, size, raw, flags in sdk.sections:
        if not flags & 0x20000000:
            continue
        for ins in cs.disasm(sdk.data[raw:raw + size], address):
            if not ins.id:
                continue
            for operand in ins.operands:
                if operand.type == capstone.CS_OP_MEM and operand.mem.base == capstone.x86_const.X86_REG_RIP:
                    target = ins.address + ins.size + operand.mem.disp - sdk.base
                    if 0x7ad200 <= target < 0x7ad280:
                        bounds = sdk.enclosing(ins.address - sdk.base)
                        if bounds is None or not any(x[0] == ins.address for x in sdk.instructions(*bounds)):
                            raise ValueError('Resource function reference lacks an independent instruction boundary')
                        references.append({'siteRva': hex(ins.address - sdk.base), 'targetRva': hex(target),
                                           'instruction': ins.mnemonic + ' ' + ins.op_str})
    direct = sorted((x['siteRva'], x['targetRva']) for x in references)
    if direct != sorted([('0x140966', '0x7ad238'), ('0x1409ca', '0x7ad278'),
                         ('0x5d2f64', '0x7ad238'), ('0x5d2f73', '0x7ad200'), ('0x5d2f83', '0x7ad238'),
                         ('0x5d2fa4', '0x7ad278'), ('0x5d2fb3', '0x7ad240'), ('0x5d2fc3', '0x7ad278')]):
        raise ValueError('Fixed SDK resource-global references changed')
    sdk_windows = [(0x9880, 0x98a0), (0x140940, 0x140a5c), (0x373d10, 0x373e60),
                   (0x378dda, 0x378e70), (0x379701, 0x379882), (0x5d2f60, 0x5d2fd3)]
    for entry in (0x364910, 0x3646a0, 0x376ea0, 0x380e60, 0x3be5c0, 0x13a1b0):
        sdk_windows.append(sdk.enclosing(entry))
    exe_windows = [(0x6e369ab, 0x6e369fa), (0x7185c00, 0x7185c16),
                   (0x197761c6, 0x19776225), (0x19776669, 0x1977672e)]
    for entry in (0x6d7f190, 0x6d81ad0, 0x4cbda0, 0x4fd760, 0x7185870, 0x7185b20, 0x199ae770):
        exe_windows.append(exe.enclosing(entry))
    imports = [(d['dll'], f['name']) for d in exe.imports() for f in d['functions'] if f['iatRva'] == 0x3bac2f0]
    if imports != [('KERNEL32.dll', 'GetCurrentDirectoryW')]:
        raise ValueError('InitNativeEnv first-argument API binding differs')
    vt = int.from_bytes(sdk.data[sdk.offset(sdk.base + 0x6d1cb8 + 0x58, 8):][:8], 'little') - sdk.base
    if vt != 0x3958e0:
        raise ValueError('Combo signer slot differs')
    exported = json.loads((RAW / 'rea-final.json').read_bytes())
    ids = [{'id': r['evidence_id'], 'operation': r['operation'], 'parameters': r['parameters'], 'limitations': r['limitations']}
           for r in exported['records'] if r['operation'] == 'xrefs' and r['parameters'].get('address') in
           ('0x1807ad200', '0x1807ad240', '0x1807ad238', '0x1807ad278')]
    return {'sdkGlobalReferences': references, 'directRegistrationFound': False,
            'negativeSearchScope': 'Linear decoding of all fixed SDK executable section bytes, independently checked against pdata boundaries; exact Ghidra xrefs; no proof against arbitrary indirect/external writes.',
            'sdkExports': sdk.exports(), 'sdkWindows': [window(sdk, *x) for x in sdk_windows],
            'exeWindows': [window(exe, *x) for x in exe_windows], 'initCurrentDirectoryImport': imports,
            'reaExactGlobalXrefs': ids, 'exeWholeProgramAnalysis': False}


def decode_bundle(binary, data):
    profile_bytes = (ROOT / 'protocol/cn-7.2/profile.json').read_bytes()
    context = Snapshot(ROOT / CONTEXT, json.loads(profile_bytes)['files'], digest(profile_bytes))
    pointer = u64(context.at('archiveDecodeTable.cell', context.base + 0x5eed350, 8))
    key = context.at('archiveDecodeTable.bytes', pointer, 256)
    if digest(key) != '789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297':
        raise ValueError('Frozen archive context changed')
    p = BlobProbe(binary, ROOT, key)
    e = p.engine
    try:
        n = struct.unpack_from('<I', data, 4)[0]
        if data[:12] != bytes.fromhex('426c62039407000005000000'):
            raise ValueError('Accurate SDK bundle signature differs')
        signature = e.alloc(12)
        e.uc.mem_write(signature, data[:12])
        header, storage = e.alloc(64), e.alloc(n + 48)
        buffer = storage + 16
        e.uc.mem_write(storage, b'\xa5' * (n + 48))
        e.write(header + 16, buffer)
        e.write(header + 40, n + 16)
        output, size = e.alloc(8), e.alloc(8)
        value, check, _ = p.call(0x1415aa740, dict(rcx=signature))
        if value & 255 != 1:
            raise ValueError('Original SDK bundle header check rejected')
        value, prepare, _ = p.call(0x1415aa760, dict(rcx=header, rdx=signature, r8=output, r9=size))
        if value & 255 != 1 or e.read(size) != n + 16 or e.read(output) != buffer:
            raise ValueError('Original SDK bundle header preparation rejected')
        e.uc.mem_write(buffer, data[12:n + 28])
        value, run, _ = p.call(0x1415aa820, dict(rcx=header))
        decoded = bytes(e.uc.mem_read(buffer + 16, n))
        if (value & 255 != 1 or e.uc.mem_read(storage, 16) != b'\xa5' * 16 or
                e.uc.mem_read(buffer + n + 16, 16) != b'\xa5' * 16 or
                bytes(e.uc.mem_read(buffer, 16)) != data[12:28] or decoded[128:] != data[156:n + 28]):
            raise ValueError('Original SDK header transform/IV/tail/canaries differ')
        parsed = parse_header(decoded, len(data), n)
        blob = {'path': BUNDLE, 'inputSHA256': BUNDLE_SHA, 'decodedHeaderHex': decoded.hex(),
                'parsed': parsed, 'headerRuns': [check, prepare, run], 'nativeGetters': p.check_getters(header, parsed)}
    finally:
        p.close()
    if parsed['compressionTag'] != 3 or parsed['blockCount'] != 442 or parsed['uncompressedBytes'] != 57849700 or parsed['fileTailBytes']:
        raise ValueError('Accurate SDK bundle codec/extent changed')
    p = PayloadProbe(binary, ROOT, key)
    whole, rows, samples = bytearray(), [], []
    started = time.monotonic()
    try:
        for index in range(parsed['blockCount']):
            if time.monotonic() - started > 240:
                raise ValueError('Four-minute resource decode budget exhausted')
            transformed, run = p.transform(blob, data, index)
            expected = parsed['lastBlockBytes'] if index + 1 == parsed['blockCount'] else parsed['blockBytes']
            value = lz4_block(transformed, expected)
            whole.extend(value)
            rows.append({'index': index, 'outputBytes': len(value), 'nativePrefixRun': run})
            if index in (0, parsed['blockCount'] - 1):
                native, native_run, order = p.native_block(blob, data, index)
                if native != value:
                    raise ValueError('Native SDK codec and independent decoder differ')
                samples.append({'index': index, 'nativeCodecRun': native_run, 'orderedCalls': order, 'referenceEqual': True})
    finally:
        p.close()
    if len(whole) != parsed['uncompressedBytes']:
        raise ValueError('Complete SDK bundle payload size differs')
    return blob, bytes(whole), {'outputBytes': len(whole), 'blocks': rows, 'nativeSamples': samples,
                                'allBlocksNativePrefixExecuted': True, 'allBlocksNativeCodecExecuted': False,
                                'decodedConfigurationOrPayloadHashRecorded': False}


def read_string(data, cursor, end):
    if cursor + 4 > end:
        raise ValueError('Truncated aligned string length')
    size = struct.unpack_from('<I', data, cursor)[0]
    cursor += 4
    if cursor + size > end:
        raise ValueError('Aligned string exceeds object extent')
    return data[cursor:cursor + size], (cursor + size + 3) & ~3


def serialized_config(data, content_bytes):
    if not 20 <= content_bytes <= len(data):
        raise ValueError('Serialized content extent invalid')
    data = data[:content_bytes]
    meta, size, version, offset = struct.unpack_from('>4I', data)
    if size != len(data) or version != 17 or data[16:20] != bytes(4) or not 20 + meta <= offset <= size:
        raise ValueError('Expected actual v17 serialized header')
    limit = 20 + meta
    end = data.find(b'\0', 20, limit)
    if end < 0:
        raise ValueError('Serialized engine string unterminated')
    cursor = end + 1
    platform, tree, count = struct.unpack_from('<iBI', data, cursor)
    cursor += 9
    if not tree or not 0 < count <= 4096:
        raise ValueError('Expected bounded serialized type trees')
    types = []
    for _ in range(count):
        if cursor + 23 > limit:
            raise ValueError('Serialized type header truncated')
        kind, stripped, script = struct.unpack_from('<iBh', data, cursor)
        cursor += 7 + (16 if kind == 114 else 0) + 16
        nodes, strings = struct.unpack_from('<II', data, cursor)
        cursor += 8
        if not 0 < nodes <= 100000 or cursor + nodes * 24 + strings > limit:
            raise ValueError('Serialized type tree exceeds metadata')
        selected_nodes = [struct.unpack_from('<HBBIIiii', data, cursor + j * 24) for j in range(nodes)] if kind in (49, 142) else []
        cursor += nodes * 24 + strings
        types.append({'classID': kind, 'stripped': stripped, 'scriptIndex': script, 'selectedNodes': selected_nodes})
    objects_count = struct.unpack_from('<I', data, cursor)[0]
    cursor += 4
    if objects_count > 100000:
        raise ValueError('Serialized object budget exceeded')
    objects, selected, container = [], [], []
    for _ in range(objects_count):
        cursor = (cursor + 3) & ~3
        if cursor + 20 > limit:
            raise ValueError('Serialized object table truncated')
        path_id, start, length, type_index = struct.unpack_from('<QIII', data, cursor)
        cursor += 20
        if type_index >= len(types) or offset + start + length > size:
            raise ValueError('Serialized object extent/type invalid')
        objects.append({'pathID': path_id, 'offset': start, 'bytes': length, 'typeIndex': type_index, 'classID': types[type_index]['classID']})
    for obj in objects:
        at, end = offset + obj['offset'], offset + obj['offset'] + obj['bytes']
        if obj['classID'] == 49:
            name, at = read_string(data, at, end)
            if name != b'server_pc':
                continue
            if at + 4 > end:
                raise ValueError('TextAsset content length truncated')
            length = struct.unpack_from('<I', data, at)[0]
            at += 4
            if at + length > end or ((at + length + 3) & ~3) != end:
                raise ValueError('TextAsset content exceeds actual object')
            selected.append(dict(obj, textOffset=at, textBytes=length))
        if obj['classID'] == 142:
            _, at = read_string(data, at, end)
            if at + 4 > end:
                raise ValueError('AssetBundle preload count truncated')
            preload = struct.unpack_from('<I', data, at)[0]
            at += 4 + 12 * preload
            if at + 4 > end:
                raise ValueError('AssetBundle preload vector exceeds object')
            pairs = struct.unpack_from('<I', data, at)[0]
            at += 4
            for _ in range(pairs):
                key, at = read_string(data, at, end)
                if at + 20 > end:
                    raise ValueError('AssetBundle container entry truncated')
                first, amount, file_id, path_id = struct.unpack_from('<iiiQ', data, at)
                at += 20
                if key == ASSET.encode():
                    container.append({'bundlePathID': obj['pathID'], 'key': ASSET, 'preloadIndex': first,
                                      'preloadSize': amount, 'fileID': file_id, 'pathID': path_id})
    if len(selected) != 1 or len(container) != 1 or container[0]['fileID'] != 0 or container[0]['pathID'] != selected[0]['pathID']:
        raise ValueError('Exact AssetBundle container does not uniquely bind the TextAsset')
    item = selected[0]
    nodes = types[item['typeIndex']]['selectedNodes']
    if len(nodes) != 9 or [x[1] for x in nodes] != [0, 1, 2, 3, 3, 1, 2, 3, 3] or nodes[1][4] != 0x800001ab or nodes[5][4] != 0x800001ea:
        raise ValueError('Actual TextAsset name/script type-tree schema changed')
    binding = {'serializedVersion': version, 'platform': platform, 'typeTree': True,
               'typeCount': len(types), 'objectCount': len(objects), 'serializedDataOffset': offset,
               'textAsset': item, 'container': container[0], 'textTypeTreeNodes': nodes,
               'nativeFullSerializedConsumerExecuted': False, 'configurationBytesOrHashRecordedInReport': False}
    return binding, data[item['textOffset']:item['textOffset'] + item['textBytes']]


def classify_configs(configs):
    if not isinstance(configs, list) or [x.get('env') for x in configs] != EXPECTED_ENVS:
        raise ValueError('Actual SDK configuration environment array differs')
    result = []
    for row, host in zip(configs, COMBO_HOSTS):
        value = row['url']['comboLogin']
        parsed = urlsplit(value)
        path = '/hk4e_cn/combo/granter/login/v2/login' if row['env'] in EXPECTED_ENVS[:8] else '/hk4e_global/combo/granter/login/v2/login'
        if parsed.scheme != 'https' or parsed.hostname != host or parsed.path != path or parsed.query or parsed.fragment or parsed.username or parsed.password:
            raise ValueError('Fixed public combo route classification differs')
        if not isinstance(row.get('app_key'), str) or not row['app_key']:
            raise ValueError('Actual app_key input unavailable')
        result.append({'env': row['env'], 'host': host, 'path': path, 'urlMapType': 'object',
                       'urlEntryCount': len(row['url']),
                       'nativeInitUrlFieldType': 'JSON string', 'appKeyPresent': True,
                       'naturalSelectionObserved': False})
    return result


class RouteFragments(ResponseFixture):
    def __init__(self, pe):
        super().__init__(pe)
        self.stop_at = None

    def call(self, rva, *args):
        self.pending, self.native_calls, self.trace = [], [], []
        self.uc.mem_write(self.STACK - 0x10000, bytes(0x11000))
        self.put(self.STACK, self.STOP)
        self.uc.reg_write(ux.UC_X86_REG_RSP, self.STACK)
        for register, value in zip((ux.UC_X86_REG_RCX, ux.UC_X86_REG_RDX, ux.UC_X86_REG_R8, ux.UC_X86_REG_R9), args):
            self.uc.reg_write(register, value)
        for i, value in enumerate(args[4:]):
            self.put(self.STACK + 0x28 + i * 8, value)
        self.uc.emu_start(self.pe.base + rva, self.STOP, count=2000000)
        if self.uc.reg_read(ux.UC_X86_REG_RIP) != self.STOP or self.uc.reg_read(ux.UC_X86_REG_RSP) != self.STACK + 8:
            raise ValueError('Original SDK call did not return within its balanced two-million-instruction bound')
        self.finish_return(self.STOP, self.STACK + 8)
        return self.uc.reg_read(ux.UC_X86_REG_RAX)

    def on_code(self, uc, address, size, user):
        if address - self.pe.base == 0x13a1b0:
            self.trace.append(address - self.pe.base)
            return  # Execute the original aligned allocator; model only its underlying allocation.
        if address - self.pe.base == self.stop_at:
            self.trace.append(address - self.pe.base)
            uc.emu_stop()
            return
        super().on_code(uc, address, size, user)

    def segment(self, start, end, rsp, rbp, config):
        self.trace, self.stop_at = [], end
        for register, value in ((ux.UC_X86_REG_RSP, rsp), (ux.UC_X86_REG_RBP, rbp),
                                (ux.UC_X86_REG_R15, config), (ux.UC_X86_REG_R13, 0),
                                (ux.UC_X86_REG_RSI, 0xffffffffffffffff)):
            self.uc.reg_write(register, value)
        self.uc.emu_start(self.pe.base + start, self.STOP, count=2000000)
        if self.uc.reg_read(ux.UC_X86_REG_RIP) != self.pe.base + end:
            raise ValueError('Original SDK fragment did not reach its exact exit')
        self.stop_at = None
        return len(self.trace)


def native_fragments(pe, configs):
    rows = []
    for code, shape in ((0, 'string'), (1, 'string'), (0, 'object')):
        env = RouteFragments(pe)
        config = env.OBJECT
        obj, source, name, out = (config + n for n in (0x10000, 0x20000, 0x30000, 0x40000))
        env.put(config, pe.base + 0x6c6cd8)
        env.put(config + 0x860, obj)
        sentinel = env.allocate(0x60)
        for offset in (0, 8, 16):
            env.put(sentinel + offset, sentinel)
        env.put(sentinel + 0x18, 0x101, 2)
        env.put(obj, sentinel)
        original = next(x for x in configs if x['env'] == code)
        selected = {'comboLogin': original['url']['comboLogin']}
        packet = {'app_key': original['app_key'], 'url': json.dumps(selected, separators=(',', ':')) if shape == 'string' else selected}
        encoded = json.dumps(packet, separators=(',', ':')).encode()
        text, buffer = config + 0x60000, config + 0x70000
        env.call(0x14d040, source)
        env.put(text + 0x18, 15)
        env.uc.mem_write(buffer, encoded + b'\0')
        env.call(0x138160, text, buffer, len(encoded))
        if not env.call(0x14e110, source, text) & 255:
            raise ValueError('Original SDK JSON setup rejected the exact selected resource fields')
        env.call(0x1380e0, text)
        rsp, rbp = 0x300078000, 0x300078100
        env.call(0x14d2b0, rbp + 0x160, source)
        key_steps = env.segment(0x378dda, 0x378e70, rsp, rbp, config)
        key_equal = env.string(config + 0x218) == original['app_key'].encode()
        url_steps = env.segment(0x379701, 0x379882, rsp, rbp, config)
        entries = {hex(x): env.trace.count(x) for x in (0x14e110, 0x380e60, 0x364910, 0x140940)}
        env.STACK = 0x3000c0008  # Keep the independently staged outer frame intact during native destruction.
        for address in (rbp + 0x220, rbp + 0x160, source):
            env.call(0x14d450, address)
        env.uc.mem_write(name, b'comboLogin\0')
        returned = env.call(0x376ea0, config, out, name, 0)
        expected = original['url']['comboLogin'].encode() if shape == 'string' else b'https://127.0.0.1'
        rows.append({'env': code, 'urlShape': shape, 'urlProjectionKeys': ['comboLogin'],
                     'appKeyFragmentInstructions': key_steps, 'urlFragmentInstructions': url_steps,
                     'getterInstructions': len(env.trace), 'producerEntries': entries, 'mapSize': env.get(obj + 8),
                     'nativeGetterValueEqual': env.string(out) == expected,
                     'nativeGetterReturnedPointer64': returned == out and out > 0xffffffff,
                     'appKeyFieldEqual': key_equal, 'wholeInitReturned': False, 'nativeJsonCleanupReturned': True,
                     'modelCalls': dict(sorted(Counter(x['model'] for x in env.events).items()))})
    return rows


if __name__ == '__main__':
    main()
