"""CN72 SDK transport evidence: fixed disk bytes, never a live target."""
from __future__ import annotations

import argparse
import bisect
import hashlib
import json
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'local/migration72/python'))
sys.path.insert(0, str(ROOT / 'tools'))
from extract_client72 import (PE, number, extract, Names, TypeResolver, HEADER_VA,
                             BODY, MASK32, MASK64, CODE_REGISTRATION, STRIDE)
from extract_client72_signatures import parameter_start, parameter_words
import capstone
import unicorn
from unicorn import x86_const as ux

NATURAL_SHA = '2643bfe4006acaf3a7a97c1c0e681d5e71ae8a3c27f048cc31d87dc273adcf9f'
PROFILE_SHA = 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'
REA_SHA = '0c47927148a7cc5d07f2596f0b464e7d04061fad80cfeee612604b6e3acd6c9a'
MODULE_HASHES = {
    'YuanShen.exe': '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5',
    'MHYPBase.dll': '4b8dff048c1db0a359a3717a17be55f02ea9884d6d573c330399fb54f1968f45',
    'AccountPlatNative.dll': 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25',
    'Astrolabe.dll': '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442',
    'HoYoChannel.dll': 'de55c7983f83f737f9570e04cbabc395f7ae0c7c98def76c97135cd187e57cd7',
    'Mmoron.dll': '79715e6b257233d4779d8c7ab9d749377636e973eef350dea4d91099301c26d0',
    'TencentGME.dll': '8d06b104314ec75014aa5728440d7c56a5cdf15d32a5dd9d39b9143d4b7934fb',
    'gmesdk.dll': '6bb1b0b59a0d0aec8b23c5d12356332c25c1d92b4b1fd9a739472045a26e4c00',
    'MTBenchmark_Windows.dll': '27d5910979ade663bed753d0ebb759bf3efea6b7133976ba8092a7d7bf25d8ca',
    'telemetry.dll': 'f7457e733ed047112c1929d6b5c8c7c7f199d19b517ed1aca81602b2a20a78d5',
    'ZFProxyWeb.dll': '0754391949106c03507cbfa09403e0e899112299cb75ff2de71dc378abe4018f',
}

SDK_RANGES = [(0x13cdd0, 0x13ce5a), (0x131ef0, 0x131f22), (0x13ce60, 0x13cebb),
              (0x13cec0, 0x13cf0c), (0x13cf10, 0x13cf4b), (0x13d0c0, 0x13d161),
              (0x41e6e0, 0x41e6e7), (0x41e6f0, 0x41e71e), (0x4284a0, 0x42a0c4),
              (0x42de80, 0x42ded2), (0x4a2e30, 0x4a2eb4), (0x4978c0, 0x4978c5),
              (0x48f4f0, 0x48f4f5), (0x2f0940, 0x2f118e), (0x2f3940, 0x2f40ef),
              (0x2f5ae0, 0x2f60ba), (0x2f3360, 0x2f350b)]


def digest(data):
    return hashlib.sha256(data).hexdigest()


class Image(PE):
    def __init__(self, data):
        super().__init__(data)
        optional = number(data, 0x3c) + 24
        self.directories = [struct.unpack_from('<II', data, optional + 112 + i * 8)
                            for i in range(min(number(data, optional + 108), 16))]
        self.runtime = []
        rva, size = self.directories[3]
        if rva:
            off = self.offset(self.base + rva, size)
            self.runtime = sorted(struct.unpack_from('<III', data, off + i)
                                  for i in range(0, size - 11, 12))
        self.starts = [r[0] for r in self.runtime]
        self.cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)

    def cstring(self, rva):
        off = self.offset(self.base + rva)
        end = self.data.find(b'\0', off, off + 4096)
        if end < 0:
            raise ValueError('Unterminated PE name')
        self.offset(self.base + rva, end - off + 1)
        return self.data[off:end].decode('ascii')

    def imports(self):
        result = []
        for kind, index, width in [('normal', 1, 20), ('delay', 13, 32)]:
            rva, size = self.directories[index]
            if not rva:
                continue
            off = self.offset(self.base + rva, size)
            terminated = False
            for pos in range(off, off + size - width + 1, width):
                entry = struct.unpack_from('<' + 'I' * (width // 4), self.data, pos)
                if not any(entry):
                    terminated = True
                    break
                if kind == 'normal':
                    ilt, _, _, name_rva, iat = entry
                else:
                    attrs, name_rva, _, iat, ilt, _, _, _ = entry
                    if attrs != 1:
                        raise ValueError('Delay descriptor is not RVA-based')
                dll = self.cstring(name_rva)
                functions = []
                for slot in range(8192):
                    thunk = number(self.data, self.offset(self.base + (ilt or iat) + slot * 8, 8), 'Q')
                    if not thunk:
                        break
                    ordinal = thunk & 0xffff if thunk >> 63 else None
                    name = None if ordinal is not None else self.cstring(thunk + 2)
                    functions.append({'name': name, 'ordinal': ordinal, 'iatRva': iat + slot * 8,
                                      'iltRva': (ilt or iat) + slot * 8})
                else:
                    raise ValueError('Import thunk budget exceeded')
                result.append({'kind': kind, 'dll': dll, 'functions': functions})
            if not terminated:
                raise ValueError('Import descriptors lack terminator within declared directory')
        return result

    def exports(self):
        rva, size = self.directories[0]
        if not rva:
            return []
        off = self.offset(self.base + rva, 40)
        ordinal_base, count, named, funcs, names, ordinals = struct.unpack_from('<IIIIII', self.data, off + 16)
        labels = {}
        for i in range(named):
            idx = number(self.data, self.offset(self.base + ordinals + i * 2, 2), 'H')
            name = self.cstring(number(self.data, self.offset(self.base + names + i * 4, 4)))
            labels.setdefault(idx, []).append(name)
        result = []
        for i in range(count):
            target = number(self.data, self.offset(self.base + funcs + i * 4, 4))
            if target:
                result.append({'ordinal': ordinal_base + i, 'names': labels.get(i, []), 'rva': target,
                               'forwarder': self.cstring(target) if rva <= target < rva + size else None})
        return result


    def instructions(self, start, end):
        off = self.offset(self.base + start, end - start, executable=True)
        return list(self.cs.disasm_lite(self.data[off:off + end - start], self.base + start))

    def enclosing(self, rva):
        i = bisect.bisect_right(self.starts, rva) - 1
        if i >= 0 and self.runtime[i][0] <= rva < self.runtime[i][1]:
            return self.runtime[i][:2]
        return None

    def direct_callers(self, rva):
        result = []
        for start, size, raw, flags in self.sections:
            if not flags & 0x20000000:
                continue
            code = self.data[raw:raw + size]
            for match in re.finditer(b'\xe8', code):
                pos = match.start()
                if pos + 5 > len(code):
                    continue
                site = start - self.base + pos
                if site + 5 + struct.unpack_from('<i', code, pos + 1)[0] != rva:
                    continue
                bounds = self.enclosing(site)
                if bounds is None:
                    result.append({'siteRva': site, 'function': None, 'status': 'unwind-boundary-unavailable'})
                    continue
                instructions = self.instructions(*bounds)
                hit = next((j for j, i in enumerate(instructions) if i[0] == self.base + site and i[2] == 'call'), None)
                if hit is not None:
                    result.append({'siteRva': site, 'function': list(bounds), 'status': 'instruction-boundary-verified',
                                   'before': [[hex(a-self.base), m, op] for a, _, m, op in instructions[max(0, hit-8):hit+2]]})
        return result

    def rip_references(self, targets):
        """Bounded RIP-relative MOV/LEA/CALL/JMP candidates, verified from .pdata."""
        result = []
        pattern = re.compile(rb'(?:[\x40-\x4f])?[\x8b\x8d\xff][\x05\x0d\x15\x1d\x25\x2d\x35\x3d].{4}', re.DOTALL)
        for start, size, raw, flags in self.sections:
            if not flags & 0x20000000:
                continue
            code = self.data[raw:raw + size]
            for match in pattern.finditer(code):
                site = start - self.base + match.start()
                target = site + len(match.group()) + struct.unpack_from('<i', match.group(), len(match.group()) - 4)[0]
                if target not in targets:
                    continue
                bounds = self.enclosing(site)
                if bounds is None:
                    result.append({'siteRva': site, 'targetRva': target, 'label': targets[target],
                                   'status': 'unwind-boundary-unavailable'})
                    continue
                instructions = self.instructions(*bounds)
                hit = next((j for j, i in enumerate(instructions) if i[0] == self.base + site), None)
                if hit is not None:
                    row = {'siteRva': site, 'targetRva': target, 'label': targets[target],
                                   'function': list(bounds), 'status': 'instruction-boundary-verified',
                                   'window': [[hex(a-self.base), m, op] for a, _, m, op in instructions[max(0, hit-7):hit+4]]}
                    if targets[target].endswith('!GetProcAddress'):
                        row['immediateRdxLiteral'] = None
                        for address, length, mnemonic, operands in reversed(instructions[max(0, hit-7):hit]):
                            match = re.fullmatch(r'rdx, \[rip ([+-]) (0x[0-9a-f]+)\]', operands)
                            if mnemonic == 'lea' and match:
                                pointer = address-self.base+length+int(match[2],16)*(1 if match[1]=='+' else -1)
                                try:
                                    row['immediateRdxLiteral'] = self.cstring(pointer)
                                except (ValueError, UnicodeDecodeError):
                                    pass  # Literal decode unknown, not a resolved dynamic name.
                                break
                            if mnemonic.startswith('j') or mnemonic=='call' or operands.startswith(('rdx,','edx,')):
                                break
                    result.append(row)
        return result


class NativeSetopt:
    """Real target option dispatch/string copy; only CRT storage is modeled."""
    HANDLE = 0x400000000
    STRING = 0x400004000
    STOP = 0x500000000
    STACK = 0x300008008
    HEAP = 0x600000000

    def __init__(self, pe):
        self.pe = pe
        self.uc = unicorn.Uc(unicorn.UC_ARCH_X86, unicorn.UC_MODE_64)
        size = number(pe.data, number(pe.data, 0x3c) + 24 + 56)
        self.uc.mem_map(pe.base, (size + 0xfff) & ~0xfff)
        for address, count, offset, _ in pe.sections:
            self.uc.mem_write(address, pe.data[offset:offset + count])
        for address in [0x300000000, self.HANDLE, self.STOP, self.HEAP]:
            self.uc.mem_map(address, 0x10000)
        self.allocations = {}
        self.heap_next = self.HEAP
        self.fail_allocation = False
        self.events = []
        self.trace = []
        self.uc.hook_add(unicorn.UC_HOOK_CODE, self.on_code)

    def call(self, option, value, *, null_handle=False):
        self.trace = []
        self.uc.mem_write(self.STACK, struct.pack('<Q', self.STOP))
        self.uc.reg_write(ux.UC_X86_REG_RSP, self.STACK)
        self.uc.reg_write(ux.UC_X86_REG_RCX, 0 if null_handle else self.HANDLE)
        self.uc.reg_write(ux.UC_X86_REG_RDX, option & 0xffffffffffffffff)
        self.uc.reg_write(ux.UC_X86_REG_R8, value & 0xffffffffffffffff)
        self.uc.reg_write(ux.UC_X86_REG_R9, 0xfedcba9876543210)
        self.uc.emu_start(self.pe.base + 0x41e6f0, self.STOP, count=100000)
        if self.uc.reg_read(ux.UC_X86_REG_RIP) != self.STOP:
            raise ValueError('Native instruction budget exhausted')
        if self.uc.reg_read(ux.UC_X86_REG_RSP) != self.STACK + 8:
            raise ValueError('Native stack imbalance')
        return self.uc.reg_read(ux.UC_X86_REG_EAX)

    def on_code(self, uc, address, size, _):
        self.trace.append(address - self.pe.base)
        if address == self.pe.base + 0x4978c0:
            count = uc.reg_read(ux.UC_X86_REG_RCX)
            if not 1 <= count <= 2048:
                raise ValueError('Fixture CRT allocation outside declared samples')
            result = 0 if self.fail_allocation else self.heap_next
            if result:
                self.allocations[result] = count
                self.heap_next += (count + 15) & ~15
            self.events.append({'model': 'CRT-storage-allocate', 'bytes': count, 'success': bool(result)})
        elif address == self.pe.base + 0x48f4f0:
            pointer = uc.reg_read(ux.UC_X86_REG_RCX)
            if pointer:
                del self.allocations[pointer]
            self.events.append({'model': 'CRT-storage-free', 'null': pointer == 0})
            result = 0
        else:
            return
        # Return only from the two declared storage boundaries. The setopt
        # validator, jump tables, strdup length/copy, ownership and return run.
        stack = uc.reg_read(ux.UC_X86_REG_RSP)
        uc.reg_write(ux.UC_X86_REG_RAX, result)
        uc.reg_write(ux.UC_X86_REG_RIP, int.from_bytes(uc.mem_read(stack, 8), 'little'))
        uc.reg_write(ux.UC_X86_REG_RSP, stack + 8)

    def word(self, offset, width=8):
        return int.from_bytes(self.uc.mem_read(self.HANDLE + offset, width), 'little')


def native_samples(pe):
    result = []
    # Expected constants/bytes are independent inputs, not an encoder round trip.
    for name, option, value, expected, offset, stored, width in [
        ('unknown-option', 999999, 0x20000000000001, 48, None, None, 8),
        ('timeout-nonzero', 13, 37, 0, 0x2c8, 37000, 4),
        ('int64-above-2pow53', 30120, 0x20000000000001, 0, 0x210, 0x20000000000001, 8),
        ('int64-minus-one', 30120, -1, 0, 0x210, 0xffffffffffffffff, 8),
        ('userdata-pointer64', 10001, 0x123456789abcdef0, 0, 0x1c0, 0x123456789abcdef0, 8),
    ]:
        env = NativeSetopt(pe)
        status = env.call(option, value)
        if status != expected or (offset is not None and env.word(offset, width) != stored):
            raise ValueError('Native scalar sample mismatch: ' + name)
        if 0x4284a0 not in env.trace:
            raise ValueError('Native dispatcher was bypassed')
        result.append({'name': name, 'option': option, 'return': status, 'offset': offset,
                       'stored': stored, 'steps': len(env.trace), 'modelEvents': env.events})
    env = NativeSetopt(pe)
    status = env.call(10002, 0xfedcba9876543210, null_handle=True)
    if status != 43 or 0x4284a0 in env.trace or env.events:
        raise ValueError('Native null-handle guard mismatch')
    result.append({'name': 'null-handle', 'return': status, 'steps': len(env.trace), 'modelEvents': []})
    for name, data in [('url-ascii', b'https://fixture.invalid/a'), ('url-empty', b''),
                       ('url-syntax-not-validated-here', b'not a URL'),
                       ('url-utf8', 'https://fixture.invalid/测试'.encode()),
                       ('url-nul', b'https://fixture.invalid/a\0ignored'),
                       ('url-long', b'https://fixture.invalid/' + b'a' * 1024)]:
        env = NativeSetopt(pe)
        env.uc.mem_write(env.STRING, data + b'\0')
        status = env.call(10002, env.STRING)
        expected = data.split(b'\0')[0] + b'\0'
        pointer = env.word(0x628)
        if (status != 0 or pointer == env.STRING or env.word(0x7c0) != pointer
                or bytes(env.uc.mem_read(pointer, len(expected))) != expected
                or env.allocations != {pointer: len(expected)} or 0x42933b not in env.trace
                or 0x4a2e50 not in env.trace or 0x4a5710 not in env.trace):
            raise ValueError('Native URL copy mismatch: ' + name)
        trace_sha = digest(json.dumps(env.trace).encode())
        env.uc.mem_write(env.STRING, b'X')
        if bytes(env.uc.mem_read(pointer, len(expected))) != expected:
            raise ValueError('URL aliases caller buffer')
        clear = env.call(10002, 0)
        if clear != 0 or env.word(0x628) or env.word(0x7c0) or env.allocations:
            raise ValueError('Native URL ownership clear mismatch')
        result.append({'name': name, 'return': status, 'inputBytes': len(data),
                       'copiedBytesWithNul': len(expected), 'clearReturn': clear,
                       'copyTraceSHA256': trace_sha, 'modelEvents': env.events})
    env = NativeSetopt(pe)
    env.uc.mem_write(env.STRING, b'https://fixture.invalid/error\0')
    env.fail_allocation = True
    status = env.call(10002, env.STRING)
    if status != 27 or env.word(0x628) or env.word(0x7c0) or env.allocations or 0x42deb3 not in env.trace:
        raise ValueError('Native URL allocation failure mismatch')
    result.append({'name': 'url-allocation-failure', 'return': status, 'steps': len(env.trace),
                   'modelEvents': env.events})
    env = NativeSetopt(pe)
    env.uc.mem_write(env.STRING, b'first\0')
    if env.call(10002, env.STRING) != 0:
        raise ValueError('Initial URL failed')
    old = env.word(0x628)
    env.uc.mem_write(env.STRING, b'second\0')
    if env.call(10002, env.STRING) != 0 or old in env.allocations:
        raise ValueError('Native URL replacement did not free previous storage')
    if bytes(env.uc.mem_read(env.word(0x628), 7)) != b'second\0':
        raise ValueError('Native URL replacement mismatch')
    env.fail_allocation = True
    status = env.call(10002, env.STRING)
    if status != 27 or env.allocations or env.word(0x628) or env.word(0x7c0):
        raise ValueError('Native replacement allocation failure ownership mismatch')
    result.append({'name': 'url-replace-then-allocation-failure', 'return': status,
                   'modelEvents': env.events})
    return result


def managed_sdk(pe, metadata, startup):
    """Decode selected declarations again from fixed inputs, without cached JSON."""
    types, summary = extract(pe.data, metadata)
    names = Names(metadata, int(summary['stringBase'], 16))
    header = pe.offset(HEADER_VA, BODY)
    method_base = BODY + (number(pe.data, header + 0xb0) ^ 0x51324a1f)
    parameter_base = BODY + ((number(pe.data, header + 0x174) + 0xae6bcd68) & MASK32)
    pointers = pe.offset(number(pe.data, pe.offset(CODE_REGISTRATION, 0x78) + 0x70, 'Q'))
    resolver = TypeResolver(pe, startup, types, header)
    result = []
    for type_index in [9798, 4362]:
        owner = types[type_index]
        entry = int(summary['typeOffset'], 16) + type_index * STRIDE
        first = number(metadata, entry + 0xc) ^ 0x5abb91bd
        count = (number(metadata, entry + 0x3a, 'H') + 0xbf1a) & 0xffff
        methods = []
        for i in range(first, first + count):
            key = (((i * 0x1639) ^ 0x5e01c03b) * 0x106b250c17f34ccb + 0x069ffdcbe218faf4) & MASK64
            record = method_base + i * 26
            method_name = names.decode((number(metadata, record + 8) - key + 0xde01c441) & MASK32)
            argc = ((metadata[record + 0x18] ^ 0xf9) - key) & 0xff
            params = []
            if argc:
                start = parameter_start(i, number(metadata, record + 4))
                for pi in range(start, start + argc):
                    ni, ti = parameter_words(pi, number(metadata, parameter_base + pi * 8),
                                             number(metadata, parameter_base + pi * 8 + 4))
                    params.append({'name': names.decode(ni), 'type': None if ti == MASK32 else resolver.resolve_index(ti)})
            address = number(pe.data, pointers + i * 8, 'Q')
            methods.append({'index': i, 'name': method_name, 'registeredVA': hex(address), 'parameters': params})
        result.append({'typeIndex': type_index, 'namespace': owner['namespace'], 'name': owner['name'],
                       'methods': methods, 'returnTypes': 'not-recovered'})
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--natural', required=True, type=Path)
    parser.add_argument('--rea-bundle', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to((ROOT / 'local').resolve()):
        raise ValueError('Output must be new and inside checkout local/')
    raw = args.natural.read_bytes()
    if digest(raw) != NATURAL_SHA:
        raise ValueError('Natural snapshot identity mismatch')
    natural = json.loads(raw)
    profile_path = ROOT / 'protocol/cn-7.2/profile.json'
    if digest(profile_path.read_bytes()) != PROFILE_SHA or digest(args.rea_bundle.read_bytes()) != REA_SHA:
        raise ValueError('Fixed profile or independent REA evidence identity mismatch')
    profile = json.loads(profile_path.read_bytes())
    verified = {}
    for key, entry in profile['files'].items():
        actual = digest((args.client / entry['path']).read_bytes())
        if actual != entry['sha256']:
            raise ValueError('Profile input identity mismatch: ' + key)
        verified[key] = actual
    paths = sorted({Path(m['path']) for s in natural['samples'] for m in s['modules']['items']
                    if Path(m['path']).is_relative_to(args.client)})
    if {p.name for p in paths} != set(MODULE_HASHES):
        raise ValueError('Natural module set changed')
    records = json.loads(args.rea_bundle.read_bytes())['records']
    references = {r['normalized_result']['artifact']['sha256']: r for r in records
                  if r.get('operation') == 'inspect_pe_imports'}
    modules = []
    for path in paths:
        data = path.read_bytes()
        sha = digest(data)
        if sha != MODULE_HASHES[path.name]:
            raise ValueError('Natural module disk identity mismatch: ' + path.name)
        pe = Image(data)
        imports = pe.imports()
        reference = references[sha]
        reference_rows = []
        for kind, key in [('normal', 'imports'), ('delay', 'delay_imports')]:
            for dll in reference['normalized_result'][key]:
                for f in dll['functions']:
                    reference_rows.append((kind, dll['name'], f['name'], f['ordinal'], f['iat_rva'], f['ilt_rva']))
        actual_rows = [(d['kind'], d['dll'], f['name'], f['ordinal'], f['iatRva'], f['iltRva'])
                       for d in imports for f in d['functions']]
        if actual_rows != reference_rows:
            raise ValueError('Independent import parser disagreement: ' + path.name)
        module = {'path': path.relative_to(args.client).as_posix(), 'sha256': sha, 'bytes': len(data),
                  'imageBase': hex(pe.base), 'imports': imports, 'exports': pe.exports(),
                  'reaEvidence': reference['evidence_id'], 'independentImportRows': len(actual_rows)}
        resolver_slots = {f['iatRva']: d['dll'] + '!' + f['name'] for d in imports for f in d['functions']
                          if f['name'] in ['GetProcAddress', 'LoadLibraryA', 'LoadLibraryW', 'LoadLibraryExW']}
        module['resolverReferences'] = pe.rip_references(resolver_slots)
        module['networkNameLiteralCounts'] = {name: {'asciiNul': data.count(name.encode()+b'\0'),
            'utf16Nul': data.count(name.encode('utf-16le')+b'\0\0')} for name in
            ['WinHttpConnect','WinHttpOpenRequest','WinHttpSendRequest','HttpSendRequestW',
             'curl_easy_setopt','curl_easy_perform']}
        if path.name == 'YuanShen.exe':
            module['managedSDK'] = managed_sdk(pe,
                (args.client / profile['files']['metadata']['path']).read_bytes(),
                (args.client / profile['files']['startup']['path']).read_bytes())
            module['pinvokeCode'] = {hex(start): [[hex(a-pe.base), m, op] for a, _, m, op in pe.instructions(start, end)]
                for start, end in [(0x6d81ad0,0x6d81bd6), (0x6d81a00,0x6d81ad0), (0x6d81540,0x6d81600)]}
        if path.name == 'AccountPlatNative.dll':
            module['selectedCallers'] = {hex(rva): pe.direct_callers(rva) for rva in [0x41e6f0, 0x4284a0]}
            module['sdkCode'] = {hex(start): {'range': [start, end],
                'sha256': digest(pe.data[pe.offset(pe.base+start):pe.offset(pe.base+end-1)+1]),
                'instructions': [[hex(a-pe.base), m, op] for a, _, m, op in pe.instructions(start, end)]}
                for start, end in SDK_RANGES}
            module['setoptEntryBytes'] = pe.data[pe.offset(pe.base+0x41e6f0):pe.offset(pe.base+0x41e71d)+1].hex()
            module['setoptPdata'] = pe.enclosing(0x41e6f0)
            module['curlVersionLiteral'] = {'rva': 0x611540, 'text': pe.cstring(0x611540)}
            module['nativeSamples'] = native_samples(pe)
            module['staticRTTI'] = []
            for table, col in [(0x694980, 0x6dd018), (0x6bf300, 0x6ec748)]:
                values = struct.unpack_from('<6I', data, pe.offset(pe.base+col, 24))
                module['staticRTTI'].append({'vtableRva': table, 'colRva': col, 'colWords': values,
                    'typeName': pe.cstring(values[3]+16), 'slots': [hex(number(data, pe.offset(pe.base+table+i*8,8),'Q')-pe.base) for i in range(16)]})
            module['loginStringReferences'] = pe.rip_references({0x6c5550:'AccountPlatAccountLoginJobber',
                0x6c4488:'MDKAccountLoginJobber',0x6d3fb8:'ComboLogin',0x6940e0:'login_login',
                0x6d3d18:'login_account_plat',0x6c1200:'login_by_account'})
        modules.append(module)
    result = {'scope': 'Static disk metadata and bounded code; not natural invocation',
              'naturalSHA256': digest(raw), 'profileSHA256': digest(profile_path.read_bytes()),
              'profileInputs': verified, 'sourceSHA256': digest(Path(__file__).read_bytes()),
              'dependencySHA256': {name: digest((ROOT/'tools'/name).read_bytes()) for name in
                                   ['extract_client72.py','extract_client72_signatures.py']},
              'engineVersions': {'capstone': capstone.__version__, 'unicorn': unicorn.__version__},
              'reaBundleSHA256': digest(args.rea_bundle.read_bytes()), 'modules': modules}
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps({'modules': len(modules), 'rows': sum(m['independentImportRows'] for m in modules),
                      'outputSHA256': digest(args.output.read_bytes())}))


if __name__ == '__main__':
    main()
