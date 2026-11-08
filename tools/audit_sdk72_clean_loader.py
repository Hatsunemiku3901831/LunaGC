"""Offline PE/source/build-input audit for the clean proxy. Never loads a DLL."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'local/migration72/d02-resume-20261108/clean-loader'
STOCK_SHA = '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442'
SDK_SHA = 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'
ENTRY_RVA, ENTRY_LENGTH = 0x3950c0, 253
ENTRY_SHA = '634a8f823f6f84675977f867ec3e114c6eed100f68be81bb49e2add2e0820b8c'


def digest(data):
    return hashlib.sha256(data).hexdigest()


class PE:
    """PE32+ section/export reader. Bad or unbacked external input is rejected here."""
    def __init__(self, data):
        self.data = data
        if len(data) < 64 or data[:2] != b'MZ':
            raise ValueError('Missing DOS header')
        pe = self.unpack('<I', 60)[0]
        if self.take(pe, 4) != b'PE\0\0':
            raise ValueError('Missing PE header')
        machine, count = self.unpack('<HH', pe + 4)
        optional_size = self.unpack('<H', pe + 20)[0]
        optional = pe + 24
        if machine != 0x8664 or self.unpack('<H', optional)[0] != 0x20b or optional_size < 120:
            raise ValueError('Expected AMD64 PE32+')
        self.base = self.unpack('<Q', optional + 24)[0]
        self.image_size = self.unpack('<I', optional + 56)[0]
        self.header_size = self.unpack('<I', optional + 60)[0]
        self.export_rva, self.export_size = self.unpack('<II', optional + 112)
        self.sections = []
        for index in range(count):
            fields = self.unpack('<8sIIIIIIHHI', optional + optional_size + 40 * index)
            self.sections.append(fields)

    def take(self, offset, size):
        if offset < 0 or size < 0 or offset + size > len(self.data):
            raise ValueError('PE file range is not backed by bytes')
        return self.data[offset:offset + size]

    def unpack(self, fmt, offset):
        return struct.unpack(fmt, self.take(offset, struct.calcsize(fmt)))

    def offset(self, rva, size=1):
        if rva < self.header_size and rva + size <= self.header_size:
            self.take(rva, size)
            return rva
        for section in self.sections:
            _, _, start, raw_size, raw_offset, *_ = section
            if start <= rva and rva + size <= start + raw_size:
                offset = raw_offset + rva - start
                self.take(offset, size)
                return offset
        raise ValueError('PE RVA is not file-backed')

    def bytes_at(self, rva, size):
        return self.take(self.offset(rva, size), size)

    def string(self, rva):
        result = bytearray()
        for index in range(4096):
            byte = self.bytes_at(rva + index, 1)[0]
            if not byte:
                return result.decode('ascii')
            result.append(byte)
        raise ValueError('PE export string exceeds bound')

    def exports(self):
        if not self.export_rva:
            return []
        fields = self.unpack('<IIHHIIIIIII', self.offset(self.export_rva, 40))
        _, _, _, _, _, base, count, named_count, functions, names, ordinals = fields
        by_index = {}
        for index in range(named_count):
            name_rva = self.unpack('<I', self.offset(names + index * 4, 4))[0]
            function_index = self.unpack('<H', self.offset(ordinals + index * 2, 2))[0]
            if function_index >= count:
                raise ValueError('Export ordinal index exceeds address table')
            by_index.setdefault(function_index, []).append(self.string(name_rva))
        rows = []
        for index in range(count):
            rva = self.unpack('<I', self.offset(functions + index * 4, 4))[0]
            if not rva:
                continue
            forwarder = self.string(rva) if self.export_rva <= rva < self.export_rva + self.export_size else None
            for name in by_index.get(index, [None]):
                rows.append({'name': name, 'ordinal': base + index, 'rva': rva, 'forwarder': forwarder})
        return sorted(rows, key=lambda row: (row['ordinal'], row['name'] or ''))


def source_names():
    text = (ROOT / 'patch/examples/sdk72_clean_proxy/sdk72_clean_proxy_core.rs').read_text('utf-8')
    return re.findall(r'b"(Astrolabe_[A-Za-z0-9_]+)\\0"', text)


def require_exports(rows, names, extra=()):
    wanted = [(name, index + 1) for index, name in enumerate(names)] + list(extra)
    actual = [(row['name'], row['ordinal']) for row in rows]
    key = lambda item: (item[1], item[0] or '')
    if sorted(actual, key=key) != sorted(wanted, key=key) or any(row['forwarder'] for row in rows):
        raise ValueError('Exact export names/ordinals/native-target coverage differ')


def def_text(names, extra=(), omit_last=False):
    lines = ['LIBRARY sdk72_clean_proxy', 'EXPORTS']
    lines += [f'  {name} @{index + 1}' for index, name in enumerate(names) if not (omit_last and index == 46)]
    lines += [f'  {name} @{ordinal}' for name, ordinal in extra]
    return '\n'.join(lines) + '\n'


def output_path(path):
    path = Path(path).resolve()
    if not path.is_relative_to(RAW.resolve()):
        raise ValueError('Output must remain in this task clean-loader directory')
    if path.exists():
        raise FileExistsError('Preserve previous output; choose a new path')
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def audit(client, production=None, owned=None, emit=None):
    inventory = json.loads((ROOT / 'local/migration72/d02-resume-20261108/initial-inventory.private.json').read_bytes())
    inputs = []
    for item in inventory['inputs']:
        data = (client / item['file']).read_bytes()
        if digest(data) != item['sha256'] or len(data) != item['size']:
            raise ValueError('Fixed five-input identity changed: ' + item['file'])
        inputs.append({'file': item['file'], 'sha256': item['sha256'], 'bytes': len(data)})
    stock = (client / 'YuanShen_Data/Plugins/Astrolabe.dll').read_bytes()
    sdk = (client / 'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes()
    if digest(stock) != STOCK_SHA or digest(sdk) != SDK_SHA:
        raise ValueError('Production fixed stock/SDK gate differs')
    stock_pe, sdk_pe = PE(stock), PE(sdk)
    names = source_names()
    if len(names) != 47:
        raise ValueError('Source export count differs')
    require_exports(stock_pe.exports(), names)
    if sdk_pe.image_size != 0x932000 or digest(sdk_pe.bytes_at(ENTRY_RVA, ENTRY_LENGTH)) != ENTRY_SHA:
        raise ValueError('Reviewed full SDK encrypt entry window differs')
    thunks = (ROOT / 'patch/examples/sdk72_clean_proxy/sdk72_clean_proxy.S').read_text('utf-8')
    declared = [(name, int(index)) for name, index in re.findall(r'^clean_export (Astrolabe_\w+), (\d+)$', thunks, re.M)]
    if declared != list(zip(names, range(47))):
        raise ValueError('Assembly thunk table differs from fixed export inventory')
    result = {'scope': 'offline files and source declarations; no DLL loading',
              'fixed_inputs': inputs, 'stock_exports': stock_pe.exports(),
              'sdk_image_size': sdk_pe.image_size,
              'sdk_entry_window': {'rva': ENTRY_RVA, 'bytes': ENTRY_LENGTH, 'sha256': ENTRY_SHA},
              'source_hashes': {}, 'artifacts': {}, 'build_inputs': {}}
    for path in sorted((ROOT / 'patch/examples').rglob('sdk72_clean_proxy*')):
        if path.is_file():
            result['source_hashes'][path.relative_to(ROOT).as_posix()] = digest(path.read_bytes())
    for kind, path, extra in [('production', production, [('Sdk72CleanInspectLoadedSdk', 48)]), ('owned', owned, [])]:
        if path:
            data = Path(path).read_bytes()
            pe = PE(data)
            require_exports(pe.exports(), names, extra)
            result['artifacts'][kind] = {'sha256': digest(data), 'bytes': len(data), 'exports': pe.exports()}
    if emit:
        for filename, extra, omit in [('production.def', [('Sdk72CleanInspectLoadedSdk', 48)], False),
                                      ('owned.def', [], False), ('mock.def', [('OwnedMockAttachCount', 48)], False),
                                      ('mock-omit.def', [('OwnedMockAttachCount', 48)], True)]:
            path = output_path(Path(emit) / filename)
            content = def_text(names, extra, omit)
            path.write_text(content, encoding='ascii', newline='\n')
            result['build_inputs'][path.relative_to(ROOT).as_posix()] = digest(content.encode('ascii'))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--production', type=Path)
    parser.add_argument('--owned', type=Path)
    parser.add_argument('--emit-build-inputs', type=Path)
    args = parser.parse_args()
    path = output_path(args.output)
    result = audit(args.client, args.production, args.owned, args.emit_build_inputs)
    path.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'exports': 47, 'fixed_inputs': 5, 'artifacts': list(result['artifacts']), 'dlls_loaded': 0}))


if __name__ == '__main__':
    main()
