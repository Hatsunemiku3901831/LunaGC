"""Source-bound CN72 native service-entry extraction; no target execution."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b/b-native-account-route'
HELPER = 'tools/research/d02_route_adoption_20261110_extract.py'
ENTRY = 0x3413E0
MAX_CODE = 8192
SERVICE_TABLES = {'mdk': 0x6C2098, 'accountplat': 0x6C2CA8}
PINS = {
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    HELPER: 'bee3d8ca64bee11022ffdb583bf21922d92d859c63aedbcfe783a4b9c198f081',
    'tools/game_path.ps1': '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
    'tools/client72_sdk_transport.py': '43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237',
    'tools/client72_sdk_login_chain.py': '53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2',
    'tools/client72_sdk_crypto_contract.py': '9b8c8f10f88260bbd3dfc2c62b76632c3fad9a0e0d9d7de2fb74e04f22b66395',
    'tools/client72_sdk_response_contract.py': '792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5',
    'local/migration72/d02-active/sdk-transport-astra/final-first.json': '3650c789f07778ee54a705d517e4ba88914231b0e426bfc53d9202af8edba9fb',
    'local/migration72/d02-active/sdk-login-chain-astra/final-v2-first.json': '3d864c07b944ed63af4f69e07f5b4e9abafb8f2e30de47b716bd0f06f6c7fd96',
    'local/migration72/d02-active/sdk-login-chain-astra/rea-final.json': '94ff59a7900378914d1ee387616bf81412bc0be306090f6c9db563b5e0e091a6',
    'local/migration72/d02-active/sdk-crypto-contract-astra/final-first.json': 'c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313',
    'local/migration72/d02-active/sdk-crypto-contract-astra/rea-final.json': '3c14ee95de5234b04ceb559b13e724ef78819cad1042fde6f08d19673cbcf91c',
    'local/migration72/d02-active/sdk-response-astra/final-first.json': '77ffd6e3c378000456608197cd8ca197a6183c9b4378198ae969cf48f0442992',
}


def main():
    args = arguments()
    args.output.mkdir(parents=True, exist_ok=False)
    save(args.output / 'plan.private.json', {
        'task': 'D02-R2/NATIVE-ACCOUNT-LOCAL-ROUTE',
        'entryRVA': hex(ENTRY), 'maxCodeBytes': MAX_CODE,
        'newCodeWindows': 1, 'xdataBytes': 0, 'neighborCallees': 0,
        'targetCPU': 0, 'liveReads': 0,
        'necessity': 'Classify both inherited service identities and mode before ticket/autologin.',
        'stop': 'Source drift, unequal service slots, nonunique extent, cap, incomplete decode.',
    })
    sources = verify_pins()
    spec = importlib.util.spec_from_file_location('native_account_route_pe', ROOT / HELPER)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    profile = load('protocol/cn-7.2/profile.json')
    inputs = helper.verify_client(args.client, profile['files'])
    transport = load('local/migration72/d02-active/sdk-transport-astra/final-first.json')
    modules = []
    for row in transport['modules']:
        actual = file_sha(args.client / row['path'])
        require(actual == row['sha256'], 'natural-module-disk-identity-differs: ' + row['path'])
        modules.append({'path': row['path'], 'sha256': actual})
    sdk = (args.client / 'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes()
    pe = helper.PE(sdk)
    login = load('local/migration72/d02-active/sdk-login-chain-astra/final-v2-first.json')
    service_slots = []
    for label, table in SERVICE_TABLES.items():
        rtti_name = 'MDKLoginService' if label == 'mdk' else 'AccountPlatLoginService'
        require(any(r['vtableRva'] == table and r['subobjectOffset'] == 0
                    for r in login['staticRTTI'][rtti_name]), 'service-rtti-differs')
        raw = pe.read_rva(table + 8, 8)
        require(struct.unpack('<Q', raw)[0] == pe.base + ENTRY, 'service-entry-is-not-shared')
        service_slots.append({'service': label, 'vtableRVA': hex(table), 'slotByteOffset': 8,
                              'targetRVA': hex(ENTRY), 'hex': raw.hex(), 'sha256': sha(raw)})
    save(args.output / 'service-slots.private.json', service_slots)
    rea = load('local/migration72/d02-active/sdk-login-chain-astra/rea-final.json')
    records = [r for r in rea['records'] if r.get('operation') == 'procedure_pseudo_code'
               and (r.get('parameters') or {}).get('procedure') == hex(pe.base + ENTRY)]
    require(len(records) == 1, 'existing-service-pseudocode-not-unique')
    record = records[0]
    require(record['subject']['digest']['sha256'] == sha(sdk), 'rea-target-identity-differs')
    directory_rva, directory_bytes = pe.exception_directory()
    table = pe.read_rva(directory_rva, directory_bytes)
    diagnostic = extent_diagnostic(table, ENTRY)
    save(args.output / 'boundary-diagnostic.private.json', diagnostic)
    offset, begin, end, unwind = select_extent(diagnostic)
    code = pe.read_rva(begin, end - begin, executable=True)
    pdata = table[offset:offset + 12]
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    decoded = list(decoder.disasm(code, pe.base + begin))
    require(b''.join(bytes(i.bytes) for i in decoded) == code, 'service-body-decode-incomplete')
    assembly = [{'rva': hex(i.address - pe.base), 'size': i.size,
                 'hex': bytes(i.bytes).hex(), 'mnemonic': i.mnemonic, 'operands': i.op_str}
                for i in decoded]
    (args.output / 'code.bin').write_bytes(code)
    (args.output / 'pdata.bin').write_bytes(pdata)
    save(args.output / 'code.asm.private.json', assembly)
    prior = verify_prior_windows(pe, transport, login)
    crypto = load('local/migration72/d02-active/sdk-crypto-contract-astra/final-first.json')
    routes = crypto['nativeRoutes']['tables']
    require([r['tableCode'] for r in routes] == list(range(6)), 'route-table-codes-differ')
    require(all(r['path'] == '/account/ma-cn-passport/app/loginByPassword' for r in routes),
            'route-table-path-differs')
    result = {
        'task': 'D02-R2/NATIVE-ACCOUNT-LOCAL-ROUTE', 'passed': True,
        'sourceSHA256': file_sha(Path(__file__)), 'nativeSources': sources,
        'profileInputs': inputs, 'naturalElevenModuleDiskInputs': modules,
        'entryRVA': hex(begin), 'endRVAExclusive': hex(end),
        'codeBytes': len(code), 'codeSHA256': sha(code), 'instructions': len(decoded),
        'pdataRecordRVA': hex(directory_rva + offset), 'pdataHex': pdata.hex(),
        'pdataSHA256': sha(pdata), 'unwindRVA': hex(unwind),
        'serviceSlots': service_slots, 'existingREAEvidence': record['evidence_id'],
        'entryCoverage': minimum_coverage(assembly, 14), 'priorWindows': prior,
        'accountPlatCandidateRoutes': routes,
        'approvedNaturalRoute': 'unknown', 'naturalServiceMode': 'unknown',
        'newCodeWindows': 1, 'targetedXdataBytes': 0, 'neighborCalleeBytes': 0,
        'targetCPU': 0, 'liveReads': 0, 'hookImplemented': False,
        'serviceSelectionObserved': False, 'authenticationObserved': False, 'd02Passed': False,
    }
    save(args.output / 'static-audit.private.json', result)
    print(json.dumps({k: result[k] for k in ('passed', 'entryRVA', 'endRVAExclusive',
                                            'codeBytes', 'instructions', 'codeSHA256')}))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(args.output.resolve().is_relative_to(PRIVATE.resolve()), 'output-outside-task-private-root')
    require(not args.output.exists(), 'output-already-exists')
    return args


def verify_prior_windows(pe, transport, login):
    sdk = next(r for r in transport['modules'] if r['path'].endswith('/AccountPlatNative.dll'))
    selected = [(sdk['sdkCode']['0x41e6f0'], 0x41E6F0, 0x41E71E),
                (sdk['sdkCode']['0x2f0940'], 0x2F0940, 0x2F118E),
                (login['codeWindows']['0x3433e0'], 0x3433E0, 0x343460),
                (login['codeWindows']['0x34eca0'], 0x34ECA0, 0x34F500)]
    result = []
    for old, begin, end in selected:
        raw = pe.read_rva(begin, end - begin, executable=True)
        require(sha(raw) == old['sha256'], 'prior-window-sha-differs')
        result.append({'startRVA': hex(begin), 'endRVAExclusive': hex(end),
                       'bytes': len(raw), 'sha256': sha(raw)})
    return result


def extent_diagnostic(table, entry):
    require(len(table) % 12 == 0, 'exception-directory-misaligned')
    exact, containing = [], []
    for offset in range(0, len(table), 12):
        begin, end, unwind = struct.unpack_from('<III', table, offset)
        row = [offset, begin, end, unwind]
        if begin == entry:
            exact.append(row)
        if begin <= entry < end:
            containing.append(row)
    return {'entryRVA': hex(entry), 'exact': exact, 'containing': containing}


def select_extent(diagnostic):
    exact, containing = diagnostic['exact'], diagnostic['containing']
    require(len(exact) == 1 and exact == containing, 'exception-extent-not-unique')
    row = exact[0]
    require(0 < row[2] - row[1] <= MAX_CODE, 'body-exceeds-approved-cap')
    return row


def minimum_coverage(assembly, width):
    used, selected = 0, []
    for instruction in assembly:
        used += instruction['size']
        selected.append(instruction)
        if used >= width:
            return {'requiredBytes': width, 'coveredBytes': used, 'instructions': selected,
                    'resumeRVA': hex(int(assembly[0]['rva'], 16) + used)}
    raise ValueError('body-too-short-for-coverage')


def verify_pins():
    result = []
    for relative, expected in PINS.items():
        path = ROOT / relative
        require(file_sha(path) == expected, 'frozen-source-differs: ' + relative)
        result.append({'path': relative, 'bytes': path.stat().st_size, 'sha256': expected})
    return result


def file_sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load(relative):
    return json.loads((ROOT / relative).read_text(encoding='utf-8-sig'))


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


if __name__ == '__main__':
    raise SystemExit(main())
