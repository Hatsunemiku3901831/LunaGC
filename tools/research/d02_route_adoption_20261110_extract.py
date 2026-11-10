"""Extract one fixed CN72 LoadLocalConfig exception body; never execute it."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parents[2]
METHOD = 33350
OWNER = 4408
ENTRY = 0x159776120
REGISTRATION = 0x1422F98E0
MAX_CODE = 16384
PINS = {
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    'local/migration72/evidence/client72-signatures.json': '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'local/migration72/d02-resume-20261108/r2-route/main-third.json': 'bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471',
    'local/migration72/d02-resume-20261109/r2-config-select/explore-windows.private.json': '8871dd865a9be44f06a2950a8bce9e23ba150d60b9900a731599ace7a280a247',
    'local/migration72/d02-resume-20261109/r2-config-select/sync-windows.private.json': '4722fea419f51cb985648ccf0ff5f318c3a32b489c49c25ad8d07c2b25c1a9d1',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
}
CALLERS = (0x197770DF, 0x199AE7A8, 0x1980775C)


def main():
    args = arguments()
    args.output.mkdir(parents=True, exist_ok=False)
    plan = {
        'task': 'R2-ROUTE-CONFIG-SELECT/B-REQUEST-ADOPTION',
        'owner': OWNER, 'method': METHOD, 'entryVA': hex(ENTRY),
        'necessity': 'Existing frozen reports contain only two partial LoadLocalConfig spans; '
                     'env/cp_app_id/readOnly and return selection cannot be proved from them.',
        'scope': 'One exact exception body, its 12B pdata, AOT pointer+slot, three original E8 callers',
        'maxCodeBytes': MAX_CODE, 'newCodeWindows': 1, 'xdataBytes': 0,
        'targetCPU': 0, 'liveReads': 0, 'otherCallees': 0,
        'stop': 'Any fixed-input/AOT/caller mismatch; nonunique exact pdata entry; cap; incomplete decode',
    }
    save(args.output / 'plan.private.json', plan)
    pins = verify_pins()
    profile = load_json(ROOT / 'protocol/cn-7.2/profile.json')
    inputs = verify_client(args.client, profile['files'])
    signatures = load_json(ROOT / 'local/migration72/evidence/client72-signatures.json')
    matches = [m for m in signatures['methods'] if m['index'] == METHOD]
    require(len(matches) == 1, 'Method index must be unique')
    method = matches[0]
    require((method['declaringType'], method['name'], int(method['registeredAddress'], 16)) ==
            (OWNER, 'LoadLocalConfig', ENTRY), 'Method identity differs')
    require([p['name'] for p in method['parameters']] == ['env', 'readOnly'], 'Parameter declaration differs')
    binary = (args.client / profile['files']['binary']['path']).read_bytes()
    pe = PE(binary)
    pointer_cell = pe.read_va(REGISTRATION + 0x70, 8)
    table = struct.unpack('<Q', pointer_cell)[0]
    require(table == 0x1428A54D0, 'AOT table differs')
    slot_va = table + METHOD * 8
    slot = pe.read_va(slot_va, 8)
    require(struct.unpack('<Q', slot)[0] == ENTRY, 'AOT slot differs')
    callers = []
    for rva in CALLERS:
        raw = pe.read_rva(rva, 5, executable=True)
        require(raw[0] == 0xE8, 'Caller is not direct E8')
        target = pe.base + rva + 5 + struct.unpack_from('<i', raw, 1)[0]
        require(target == ENTRY, 'Caller target differs')
        callers.append({'rva': hex(rva), 'hex': raw.hex(), 'targetVA': hex(target)})
    exception_rva, exception_size = pe.exception_directory()
    require(exception_size % 12 == 0, 'Exception directory is not record aligned')
    table_bytes = pe.read_rva(exception_rva, exception_size)
    entry_rva = ENTRY - pe.base
    exact = []
    containing = []
    for offset in range(0, len(table_bytes), 12):
        begin, end, unwind = struct.unpack_from('<III', table_bytes, offset)
        if begin <= entry_rva < end:
            containing.append((offset, begin, end, unwind))
        if begin == entry_rva:
            exact.append((offset, begin, end, unwind))
    diagnostic = {'entryRVA': hex(entry_rva), 'exact': exact, 'containing': containing,
                  'directoryRVA': hex(exception_rva), 'directoryBytes': exception_size,
                  'xdataBytesRead': 0}
    save(args.output / 'boundary-diagnostic.private.json', diagnostic)
    require(len(exact) == 1 and exact == containing, 'Expected one exact, unique containing exception entry')
    offset, begin, end, unwind = exact[0]
    require(0 < end - begin <= MAX_CODE, 'Body exceeds approved cap')
    code = pe.read_rva(begin, end - begin, executable=True)
    old = load_json(ROOT / 'local/migration72/d02-resume-20261108/r2-route/main-third.json')
    prior_spans = []
    for w in old['static']['exeWindows'][2:4]:
        start, stop = int(w['startRva'], 16), int(w['endRva'], 16)
        require(begin <= start < stop <= end, 'Old partial is outside exact body')
        require(sha(code[start - begin:stop - begin]) == w['sha256'], 'Old partial SHA differs')
        prior_spans.append({k: w[k] for k in ['startRva', 'endRva', 'sha256']})
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    decoder = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    instructions = list(decoder.disasm(code, ENTRY))
    require(sum(i.size for i in instructions) == len(code), 'Decode is incomplete')
    require(b''.join(bytes(i.bytes) for i in instructions) == code, 'Decode bytes differ')
    pdata = table_bytes[offset:offset + 12]
    for name, raw in [('code.bin', code), ('pdata.bin', pdata),
                      ('aot-pointer-cell.bin', pointer_cell), ('aot-slot.bin', slot)]:
        (args.output / name).write_bytes(raw)
    for c in callers:
        (args.output / ('caller-' + c['rva'][2:] + '.bin')).write_bytes(bytes.fromhex(c['hex']))
    assembly = [{'address': hex(i.address), 'rva': hex(i.address - pe.base),
                 'size': i.size, 'hex': bytes(i.bytes).hex(),
                 'mnemonic': i.mnemonic, 'operands': i.op_str} for i in instructions]
    save(args.output / 'code.asm.private.json', assembly)
    (args.output / 'code.asm.private.txt').write_text('\n'.join(
        f"{i.address:016X}  {bytes(i.bytes).hex():<30} {i.mnemonic} {i.op_str}"
        for i in instructions) + '\n', encoding='utf-8')
    direct_targets = {int(i.op_str, 16) for i in instructions
                      if i.mnemonic in ('call', 'jmp') and i.op_str.startswith('0x')}
    selected = [m for m in signatures['methods'] if int(m.get('registeredAddress') or '0', 16) in direct_targets
                or m['index'] in (METHOD, 33354, 32799, 32801, 40108, 33107)]
    save(args.output / 'selected-signatures.private.json', selected)
    summary = {
        **plan, 'passed': True, 'inputs': inputs, 'frozenInputs': pins,
        'methodDeclaration': method, 'imageBase': hex(pe.base),
        'pointerTableVA': hex(table), 'pointerSlotVA': hex(slot_va),
        'originalPointerCellHex': pointer_cell.hex(), 'originalSlotHex': slot.hex(),
        'codeBytes': len(code), 'codeSHA256': sha(code), 'instructions': len(instructions),
        'endVAExclusive': hex(pe.base + end), 'pdataRecordRVA': hex(exception_rva + offset),
        'pdataHex': pdata.hex(), 'pdataSHA256': sha(pdata), 'unwindRVA': hex(unwind),
        'callers': callers, 'priorPartialSpans': prior_spans,
        'directTargetsVA': [hex(t) for t in sorted(direct_targets)],
        'naturalSelectionObserved': False, 'managedRouteAdoptionProven': False,
        'exceptionUnwindVerified': False, 'd02Passed': False,
    }
    save(args.output / 'extraction.private.json', summary)
    print(json.dumps({k: summary[k] for k in ['passed', 'codeBytes', 'codeSHA256', 'instructions',
                                             'endVAExclusive', 'pdataRecordRVA', 'unwindRVA']}))


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    return parser.parse_args()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def file_sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def load_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def verify_pins():
    pins = []
    for relative, expected in PINS.items():
        path = ROOT / relative
        actual = file_sha(path)
        require(actual == expected, 'Frozen input differs: ' + relative)
        pins.append({'path': relative, 'bytes': path.stat().st_size, 'sha256': actual})
    return pins


def verify_client(client, files):
    result = {}
    for name, expected in files.items():
        path = client / expected['path']
        size, digest = path.stat().st_size, file_sha(path)
        require(size == expected['size'] and digest == expected['sha256'], 'Client input differs: ' + name)
        result[name] = {'bytes': size, 'sha256': digest}
    return result


class PE:
    def __init__(self, data):
        self.data = data
        header = struct.unpack_from('<I', data, 0x3C)[0]
        require(data[:2] == b'MZ' and data[header:header + 4] == b'PE\0\0', 'Expected PE')
        require(struct.unpack_from('<H', data, header + 4)[0] == 0x8664, 'Expected AMD64')
        self.optional = header + 24
        require(struct.unpack_from('<H', data, self.optional)[0] == 0x20B, 'Expected PE32+')
        self.base = struct.unpack_from('<Q', data, self.optional + 24)[0]
        optional_size = struct.unpack_from('<H', data, header + 20)[0]
        count = struct.unpack_from('<H', data, header + 6)[0]
        self.sections = []
        for i in range(count):
            entry = self.optional + optional_size + i * 40
            rva, size, raw = struct.unpack_from('<III', data, entry + 12)
            flags = struct.unpack_from('<I', data, entry + 36)[0]
            self.sections.append((rva, size, raw, flags))

    def read_rva(self, address, size, executable=False):
        for rva, raw_size, raw, flags in self.sections:
            if rva <= address and address + size <= rva + raw_size:
                require(not executable or flags & 0x20000000, 'Code span is not executable')
                result = self.data[raw + address - rva:raw + address - rva + size]
                require(len(result) == size, 'Short file span')
                return result
        raise ValueError('Span is not file-backed')

    def read_va(self, address, size):
        return self.read_rva(address - self.base, size)

    def exception_directory(self):
        return struct.unpack_from('<II', self.data, self.optional + 112 + 3 * 8)


if __name__ == '__main__':
    main()
