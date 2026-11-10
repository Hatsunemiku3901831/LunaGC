"""Extract three conditional, source-bound Invoke windows without target execution."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b/b-service-trigger'
HELPER = 'tools/research/d02_route_adoption_20261110_extract.py'
SIGNATURES = 'local/migration72/evidence/client72-signatures.json'
REGISTRATION = 0x1422F98E0
TABLE = 0x1428A54D0
WINDOWS = [
    (70135, 9797, 'Invoke', 0x146E36310, 2048, ['funcName', 'args', 'callback']),
    (70155, 9798, 'Invoke', 0x146D81680, 8192, ['funcName', 'args', 'callback']),
    (70166, 9798, 'HoYoSDK_Invoke', 0x146D81A00, 4096,
     ['strFuncName', 'strArgs', 'callback', 'nCallbackIndex']),
]
PINS = {
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    HELPER: 'bee3d8ca64bee11022ffdb583bf21922d92d859c63aedbcfe783a4b9c198f081',
    SIGNATURES: '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'tools/game_path.ps1': '9d89da73dbb8479507eaf00fbe12f523b3472ca05e4aaa20602e2eb196cefa3b',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
}


def main():
    args = arguments()
    args.output.mkdir(parents=True, exist_ok=False)
    plan = {'task': 'D02-R2/NATURAL-SERVICE-TRIGGER', 'maxWindows': 3,
            'maxTotalCodeBytes': 14336, 'targetCPU': 0, 'liveReads': 0,
            'newREA': 0, 'xdataBytes': 0, 'neighborCallees': 0,
            'conditions': [{'method': m, 'owner': o, 'name': n, 'entryVA': hex(a),
                            'maxCodeBytes': c, 'parameters': p}
                           for m, o, n, a, c, p in WINDOWS]}
    save(args.output / 'plan.private.json', plan)
    frozen = verify_pins()
    spec = importlib.util.spec_from_file_location('service_trigger_pe', ROOT / HELPER)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    profile = json.loads((ROOT / 'protocol/cn-7.2/profile.json').read_bytes())
    inputs = helper.verify_client(args.client, profile['files'])
    signatures = json.loads((ROOT / SIGNATURES).read_bytes())
    pe = helper.PE((args.client / profile['files']['binary']['path']).read_bytes())
    pointer = pe.read_va(REGISTRATION + 0x70, 8)
    require(struct.unpack('<Q', pointer)[0] == TABLE, 'aot-table-pointer-differs')
    exception_rva, exception_bytes = pe.exception_directory()
    pdata_table = pe.read_rva(exception_rva, exception_bytes)
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    previous = None
    windows = []
    for method, owner, name, address, cap, parameters in WINDOWS:
        directory = args.output / str(method)
        directory.mkdir()
        declaration = [m for m in signatures['methods'] if m['index'] == method]
        require(len(declaration) == 1, 'method-declaration-not-unique')
        declaration = declaration[0]
        require((declaration['declaringType'], declaration['name'],
                 int(declaration['registeredAddress'], 16)) == (owner, name, address),
                'method-declaration-differs')
        require([p['name'] for p in declaration['parameters']] == parameters,
                'method-parameters-differ')
        slot = pe.read_va(TABLE + method * 8, 8)
        require(struct.unpack('<Q', slot)[0] == address, 'aot-slot-differs')
        if previous is not None:
            edge = direct_edges(previous, address)
            save(directory / 'predecessor-diagnostic.private.json', edge)
            require(len(edge) >= 1, 'predecessor-has-no-direct-original-edge')
        else:
            edge = []
        diagnostic = extent_diagnostic(pdata_table, address - pe.base)
        save(directory / 'boundary-diagnostic.private.json', diagnostic)
        offset, begin, end, unwind = select_extent(diagnostic, cap)
        raw = pe.read_rva(begin, end - begin, executable=True)
        decoded = list(decoder.disasm(raw, address))
        require(b''.join(bytes(i.bytes) for i in decoded) == raw, 'body-decode-incomplete')
        cursor = address
        for instruction in decoded:
            require(instruction.address == cursor, 'body-decode-not-contiguous')
            cursor += instruction.size
        require(cursor == pe.base + end, 'body-decode-end-differs')
        assembly = [{'rva': hex(i.address - pe.base), 'size': i.size,
                     'hex': bytes(i.bytes).hex(), 'mnemonic': i.mnemonic, 'operands': i.op_str}
                    for i in decoded]
        pdata = pdata_table[offset:offset + 12]
        (directory / 'code.bin').write_bytes(raw)
        (directory / 'pdata.bin').write_bytes(pdata)
        (directory / 'aot-slot.bin').write_bytes(slot)
        save(directory / 'code.asm.private.json', assembly)
        window = {'method': method, 'owner': owner, 'name': name, 'declaration': declaration,
                  'entryRVA': hex(begin), 'endRVAExclusive': hex(end), 'bytes': len(raw),
                  'maxCodeBytes': cap, 'codeSHA256': sha(raw), 'instructions': len(decoded),
                  'pdataRecordRVA': hex(exception_rva + offset), 'pdataHex': pdata.hex(),
                  'pdataSHA256': sha(pdata), 'unwindRVA': hex(unwind),
                  'aotSlotVA': hex(TABLE + method * 8), 'aotSlotHex': slot.hex(),
                  'aotSlotSHA256': sha(slot), 'originalPredecessorEdges': edge}
        save(directory / 'extraction.private.json', window)
        windows.append(window)
        previous = assembly
    require(sum(w['bytes'] for w in windows) <= 14336, 'total-code-budget-exceeded')
    result = {**plan, 'passed': True, 'sourceSHA256': sha(Path(__file__).read_bytes()),
              'frozenInputs': frozen, 'profileInputs': inputs,
              'aotPointerHex': pointer.hex(), 'aotPointerSHA256': sha(pointer),
              'windows': windows, 'newCodeWindows': len(windows),
              'naturalActionObserved': False, 'observerInstalled': False,
              'naturalServiceMode': 'unknown', 'naturalLoginUrl': 'unknown', 'd02Passed': False}
    save(args.output / 'extraction.private.json', result)
    print(json.dumps({'passed': True, 'windows': len(windows),
                      'codeBytes': sum(w['bytes'] for w in windows)}))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(args.output.resolve().is_relative_to(PRIVATE.resolve()), 'output-outside-task-root')
    require(not args.output.exists(), 'output-already-exists')
    return args


def direct_edges(assembly, target):
    edges = []
    for row in assembly:
        raw = bytes.fromhex(row['hex'])
        if len(raw) == 5 and raw[0] in (0xE8, 0xE9):
            location = int(row['rva'], 16)
            decoded = 0x140000000 + location + 5 + struct.unpack('<i', raw[1:])[0]
            if decoded == target:
                edges.append({'rva': row['rva'], 'hex': row['hex'],
                              'kind': 'call' if raw[0] == 0xE8 else 'tail-jump',
                              'targetVA': hex(target), 'normalReturnRVA':
                              hex(location + 5) if raw[0] == 0xE8 else None})
    return edges


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


def select_extent(diagnostic, cap):
    exact, containing = diagnostic['exact'], diagnostic['containing']
    require(len(exact) == 1 and exact == containing, 'exception-extent-not-unique')
    row = exact[0]
    require(0 < row[2] - row[1] <= cap, 'body-exceeds-approved-cap')
    return row


def verify_pins():
    result = []
    for relative, expected in PINS.items():
        raw = (ROOT / relative).read_bytes()
        require(sha(raw) == expected, 'frozen-source-differs: ' + relative)
        result.append({'path': relative, 'bytes': len(raw), 'sha256': expected})
    return result


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n',
                    encoding='utf-8')


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


if __name__ == '__main__':
    raise SystemExit(main())
