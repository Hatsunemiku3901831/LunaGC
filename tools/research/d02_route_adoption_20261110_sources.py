"""Bind the one extracted selection body to frozen declarations and three keys."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import pickle
import pickletools
import struct
import sys


ROOT = Path(__file__).resolve().parents[2]
CACHE = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
PINS = {
    CACHE: 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/extract_client72_literals.py': '865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed',
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
}
KEYS = {0x197762A8: 'env', 0x1977632F: 'cp_app_id', 0x197766B3: 'url'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--window', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    save(args.output / 'plan.private.json', {
        'scope': 'Frozen pure-data metadata cache; exactly three compiled key literals from the new body',
        'newCodeWindows': 0, 'keyInstructionRVAs': {hex(k): v for k, v in KEYS.items()},
        'maxCipherBytesEach': 16, 'targetCPU': 0, 'liveReads': 0,
        'stop': 'Any source/body pin or exact key mismatch; no additional literal/callee scan',
    })
    checks = []

    def need(condition, label):
        if not condition:
            raise ValueError(label)
        checks.append(label)

    pins = []
    for relative, expected in PINS.items():
        path = ROOT / relative
        actual = file_sha(path)
        need(actual == expected, 'pin:' + relative)
        pins.append({'path': relative, 'sha256': actual, 'bytes': path.stat().st_size})
    extraction = read_json(args.window / 'extraction.private.json')
    code = (args.window / 'code.bin').read_bytes()
    need(sha(code) == 'b590f54fa3b514a070afe8342d68667618bb794d78ed552fa930a05e903ec01f', 'exact-new-body')
    need(extraction['codeSHA256'] == sha(code) and extraction['codeBytes'] == len(code), 'extraction-body-binding')
    asm = read_json(args.window / 'code.asm.private.json')
    need(b''.join(bytes.fromhex(i['hex']) for i in asm) == code, 'original-instruction-bytes')
    by_rva = {int(i['rva'], 16): i for i in asm}
    cache = (ROOT / CACHE).read_bytes()
    forbidden = {'GLOBAL', 'STACK_GLOBAL', 'REDUCE', 'BUILD', 'OBJ', 'INST', 'NEWOBJ', 'NEWOBJ_EX',
                 'EXT1', 'EXT2', 'EXT4', 'PERSID', 'BINPERSID'}
    need(not ({op.name for op, arg, pos in pickletools.genops(cache)} & forbidden), 'cache-pure-data-opcodes')
    types, cells = pickle.loads(cache)
    need(len(types) == 89748 and len(cells) == 117430, 'cache-exact-shape')
    by_type = {t['index']: t for t in types}
    selected_types = []
    for index in [293, 4132, 4148, 4204, 4316, 4346, 4352, 4408]:
        t = by_type[index]
        selected_types.append(t)
    save(args.output / 'selected-types.private.json', selected_types)
    type_usages = []
    for i in asm:
        raw = bytes.fromhex(i['hex'])
        if i['mnemonic'] != 'mov' or 'qword ptr [rip ' not in i['operands'] or len(raw) != 7:
            continue
        address = int(i['address'], 16) + i['size'] + struct.unpack_from('<i', raw, 3)[0]
        if address in cells:
            type_usages.append({'instructionRVA': i['rva'], 'reference': cells[address]})
    save(args.output / 'type-usages.private.json', type_usages)
    client_profile = read_json(ROOT / 'protocol/cn-7.2/profile.json')
    inputs = {}
    for name in ['binary', 'metadata']:
        expected = client_profile['files'][name]
        path = args.client / expected['path']
        need(path.stat().st_size == expected['size'] and file_sha(path) == expected['sha256'], 'fixed-client:' + name)
        inputs[name] = {'bytes': expected['size'], 'sha256': expected['sha256']}
    binary = (args.client / client_profile['files']['binary']['path']).read_bytes()
    metadata = (args.client / client_profile['files']['metadata']['path']).read_bytes()
    sys.path.insert(0, str(ROOT / 'tools'))
    from extract_client72_literals import Literals72
    literal = Literals72(binary, metadata)
    rows = literal.group(literal.global_group)
    selected_literals = []
    for rva, expected in KEYS.items():
        i = by_rva[rva]
        raw = bytes.fromhex(i['hex'])
        need(len(raw) == 7 and raw[:3] == b'\x48\x8b\x15', 'exact-key-rip-load:' + expected)
        address = int(i['address'], 16) + i['size'] + struct.unpack_from('<i', raw, 3)[0]
        matches = [r for r in rows if int(r['cell'], 16) == address]
        need(len(matches) == 1, 'unique-key-usage:' + expected)
        row = matches[0]
        cipher = literal.payload(row['index'])
        need(0 < len(cipher) <= 16, 'bounded-key-cipher:' + expected)
        need(literal.decode(row['index']) == expected, 'exact-compiled-key:' + expected)
        (args.output / (expected + '.cipher.bin')).write_bytes(cipher)
        selected_literals.append({**row, 'value': expected, 'instructionRVA': hex(rva),
                                  'cipherBytes': len(cipher), 'cipherSHA256': sha(cipher)})
    save(args.output / 'compiled-keys.private.json', selected_literals)
    summary = {'passed': True, 'checks': checks, 'checkCount': len(checks), 'sourcePins': pins,
               'fixedClientInputs': inputs, 'newCodeWindows': 0, 'selectedLiteralCount': len(selected_literals),
               'compiledKeys': selected_literals, 'typeUsageCount': len(type_usages),
               'targetCPU': 0, 'liveReads': 0, 'exceptionUnwindVerified': False,
               'naturalSelectionObserved': False, 'managedRouteAdoptionProven': False, 'd02Passed': False}
    save(args.output / 'audit.private.json', summary)
    print(json.dumps({k: summary[k] for k in ['passed', 'checkCount', 'selectedLiteralCount', 'typeUsageCount']}))


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def file_sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
