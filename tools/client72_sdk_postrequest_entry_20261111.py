"""Extract only the source-bound CN72 PostRequest body; never run the target."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parents[1]
OWNER, METHOD = 4362, 33107
ENTRY = 0x15992C890
CALLER_RVA = 0x19801312
REGISTRATION = 0x1422F98E0
MAX_CODE = 8192
HELPER = 'tools/research/d02_route_adoption_20261110_extract.py'
SIGNATURES = 'local/migration72/evidence/client72-signatures.json'
OLD_CODE = 'local/migration72/d02-resume-20261110/b-combo-request-window/code.bin'
PINS = {
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    HELPER: 'bee3d8ca64bee11022ffdb583bf21922d92d859c63aedbcfe783a4b9c198f081',
    SIGNATURES: '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    OLD_CODE: '2fdf917e9fa54a1bf1db172fa32fec4c8c212a404ee57d79e5d8023884bc05ac',
    'tools/game_path.ps1': '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
}


def main():
    args = arguments()
    args.output.mkdir(parents=True, exist_ok=False)
    frozen = verify_pins()
    spec = importlib.util.spec_from_file_location('postrequest_pe_helper', ROOT / HELPER)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    profile = helper.load_json(ROOT / 'protocol/cn-7.2/profile.json')
    inputs = helper.verify_client(args.client, profile['files'])
    signatures = helper.load_json(ROOT / SIGNATURES)
    matches = [m for m in signatures['methods'] if m['index'] == METHOD]
    require(len(matches) == 1, 'method-not-unique')
    method = matches[0]
    require((method['declaringType'], method['name'], int(method['registeredAddress'], 16)) ==
            (OWNER, 'PostRequest', ENTRY), 'method-identity-differs')
    require([p['name'] for p in method['parameters']] ==
            ['requestUrl', 'bodyString', 'callback', 'extHeaders', 'timeoutSecond', 'retryTime'],
            'method-signature-differs')
    binary = (args.client / profile['files']['binary']['path']).read_bytes()
    pe = helper.PE(binary)
    pointer = pe.read_va(REGISTRATION + 0x70, 8)
    table = struct.unpack('<Q', pointer)[0]
    require(table == 0x1428A54D0, 'aot-table-differs')
    slot_va = table + METHOD * 8
    slot = pe.read_va(slot_va, 8)
    require(struct.unpack('<Q', slot)[0] == ENTRY, 'aot-slot-differs')
    caller = pe.read_rva(CALLER_RVA, 5, executable=True)
    require(caller == (ROOT / OLD_CODE).read_bytes()[CALLER_RVA - 0x19801220:CALLER_RVA - 0x19801220 + 5],
            'original-caller-bytes-differ')
    require(caller[:1] == b'\xe8' and
            pe.base + CALLER_RVA + 5 + struct.unpack_from('<i', caller, 1)[0] == ENTRY,
            'original-caller-target-differs')
    exception_rva, exception_bytes = pe.exception_directory()
    records = pe.read_rva(exception_rva, exception_bytes)
    entry_rva = ENTRY - pe.base
    # Boundary and cap checks complete before the only new executable-body read.
    diagnostic = extent_diagnostic(records, entry_rva)
    helper.save(args.output / 'boundary-diagnostic.private.json', {
        **diagnostic, 'directoryRVA': hex(exception_rva), 'directoryBytes': exception_bytes,
        'bodyReadBeforeBoundary': False, 'targetedXdataBytes': 0,
    })
    offset, begin, end, unwind = select_extent(diagnostic)
    code = pe.read_rva(begin, end - begin, executable=True)
    pdata = records[offset:offset + 12]
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    instructions = list(decoder.disasm(code, ENTRY))
    require(b''.join(bytes(i.bytes) for i in instructions) == code, 'incomplete-decode')
    assembly = [{'va': hex(i.address), 'rva': hex(i.address - pe.base), 'size': i.size,
                 'hex': bytes(i.bytes).hex(), 'mnemonic': i.mnemonic, 'operands': i.op_str}
                for i in instructions]
    blobs = {'code.bin': code, 'pdata.bin': pdata, 'aot-pointer-cell.bin': pointer,
             'aot-slot.bin': slot, 'caller-19801312.bin': caller}
    for name, raw in blobs.items():
        (args.output / name).write_bytes(raw)
    helper.save(args.output / 'code.asm.private.json', assembly)
    (args.output / 'code.asm.private.txt').write_text('\n'.join(
        f"{i.address:016X}  {bytes(i.bytes).hex():<30} {i.mnemonic} {i.op_str}"
        for i in instructions) + '\n', encoding='utf-8')
    summary = {
        'task': 'R2-ROUTE-CONFIG-SELECT/POSTREQUEST-ENTRY', 'passed': True,
        'scope': 'One exact PostRequest body; 12B pdata; AOT pointer and slot; original E8 caller',
        'owner': OWNER, 'method': METHOD, 'methodDeclaration': method,
        'inputClient': str(args.client.resolve()), 'inputs': inputs, 'frozenInputs': frozen,
        'imageBase': hex(pe.base), 'entryVA': hex(ENTRY), 'entryRVA': hex(begin),
        'endRVAExclusive': hex(end), 'codeBytes': len(code), 'maxCodeBytes': MAX_CODE,
        'instructions': len(instructions), 'pdataRecordRVA': hex(exception_rva + offset),
        'unwindRVA': hex(unwind), 'aotTableVA': hex(table), 'aotSlotVA': hex(slot_va),
        'originalCallerRVA': hex(CALLER_RVA), 'originalReturnRVA': hex(CALLER_RVA + 5),
        'originalBlobs': {name: {'bytes': len(raw), 'sha256': sha(raw),
                                 **({'hex': raw.hex()} if name != 'code.bin' else {})}
                          for name, raw in blobs.items()},
        'entryMinimumCoverage': {str(width): minimum_coverage(assembly, width) for width in (5, 14)},
        'newCodeWindows': 1, 'targetedXdataBytes': 0, 'neighborCalleeBytes': 0,
        'targetCPU': 0, 'liveReads': 0, 'hookImplemented': False,
        'exceptionUnwindVerified': False, 'naturalEntryObserved': False,
        'networkSendObserved': False, 'authenticationObserved': False, 'd02Passed': False,
    }
    helper.save(args.output / 'extraction.private.json', summary)
    print(json.dumps({k: summary[k] for k in ('passed', 'entryRVA', 'endRVAExclusive',
                                            'codeBytes', 'instructions', 'pdataRecordRVA', 'unwindRVA')}))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True,
                        help='Use this checkout\'s freshly resolved GAME_PATH')
    parser.add_argument('--output', type=Path, required=True)
    return parser.parse_args()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def verify_pins():
    result = []
    for name, expected in PINS.items():
        raw = (ROOT / name).read_bytes()
        require(sha(raw) == expected, 'frozen-source-differs: ' + name)
        result.append({'path': name, 'bytes': len(raw), 'sha256': expected})
    return result


def extent_diagnostic(records, entry_rva):
    require(len(records) % 12 == 0, 'exception-directory-misaligned')
    exact, containing = [], []
    for offset in range(0, len(records), 12):
        begin, end, unwind = struct.unpack_from('<III', records, offset)
        row = (offset, begin, end, unwind)
        if begin == entry_rva:
            exact.append(row)
        if begin <= entry_rva < end:
            containing.append(row)
    return {'entryRVA': hex(entry_rva), 'exact': exact, 'containing': containing}


def select_extent(diagnostic):
    exact, containing = diagnostic['exact'], diagnostic['containing']
    require(len(exact) == 1 and exact == containing, 'exception-extent-not-unique')
    row = exact[0]
    require(0 < row[2] - row[1] <= MAX_CODE, 'body-exceeds-approved-cap')
    return row


def minimum_coverage(assembly, width):
    used, selected = 0, []
    for instruction in assembly:
        selected.append(instruction)
        used += instruction['size']
        if used >= width:
            return {'requiredBytes': width, 'coveredBytes': used, 'instructions': selected,
                    'resumeRVA': hex(int(assembly[0]['rva'], 16) + used)}
    raise ValueError('body-too-short-for-entry-coverage')


if __name__ == '__main__':
    raise SystemExit(main())
