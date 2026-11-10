"""Synthetic PE/map tests only; they do not constitute native evidence."""
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

from tools import client72_d02_clear48_full_artifacts as a


class SyntheticArtifactsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.build = root / 'build'
        self.build.mkdir()
        self.contract = root / 'contract'
        self.contract.mkdir()
        self.header = root / 'shared.h'
        self.header.write_bytes(b'SYNTHETIC HEADER')
        (self.build / 'shared.h').write_bytes(self.header.read_bytes())
        raw = bytearray(0xc00)
        raw[:2] = b'MZ'
        struct.pack_into('<I', raw, 0x3c, 0x80)
        raw[0x80:0x84] = b'PE\0\0'
        struct.pack_into('<HHIIIHH', raw, 0x84, 0x8664, 2, 0, 0, 0, 0xf0, 0)
        struct.pack_into('<H', raw, 0x98, 0x20b)
        struct.pack_into('<Q', raw, 0x98+24, 0x140000000)
        struct.pack_into('<I', raw, 0x98+56, 0x3000)
        sec = 0x98 + 0xf0
        raw[sec:sec+8] = b'.text\0\0\0'
        struct.pack_into('<IIII', raw, sec+8, 0x400, 0x1000, 0x400, 0x400)
        struct.pack_into('<I', raw, sec+36, 0x60000020)
        sec += 40
        raw[sec:sec+8] = b'.data\0\0\0'
        struct.pack_into('<IIII', raw, sec+8, 0x400, 0x2000, 0x400, 0x800)
        struct.pack_into('<I', raw, sec+36, 0xc0000040)
        raw[0x400:0x400+len(a.WRAPPER)] = a.WRAPPER
        raw[0x440:0x440+len(a.XCR)] = a.XCR
        (self.build / 'child.exe').write_bytes(raw)
        addresses = [0x2000, 0x1000, 0x1000, 0x1012, 0x1013, 0x1016, 0x101b, 0x1040, 0x1040, 0x104d]
        (self.build / 'child.map').write_text('\n'.join(' 0001:00000000 %s %016x' % (name, 0x140000000+value)
                                                        for name, value in zip(a.SYMBOLS, addresses)))
        interface = {'source904SHA256': '12'*32, 'catalogSHA256': '34'*32, 'casesSHA256': '56'*32}
        self.fixed = {'interface.v1.private.json': json.dumps(interface).encode()}
        self.header_pin = hashlib.sha256(self.header.read_bytes()).hexdigest()
        self.output = root / 'bootstrap'

    def tearDown(self):
        self.temp.cleanup()

    def prepare(self):
        with patch.object(a, 'HEADER', self.header_pin), patch.object(a, 'contracts', return_value=self.fixed), \
             patch.object(a, 'audit_build', return_value=[]):
            return a.prepare(self.build, self.header, self.contract, self.output)

    def test_bootstrap_layout_and_existing_output(self):
        mirror = self.prepare()
        data = (self.output / 'bootstrap.bin').read_bytes()
        self.assertEqual(len(data), 256)
        self.assertEqual(data[:8], b'C48FBO01')
        self.assertEqual(struct.unpack_from('<I', data, 16)[0], 0x2000)
        self.assertEqual(mirror['bootstrapSHA256'], hashlib.sha256(data).hexdigest())
        with self.assertRaises(ValueError):
            self.prepare()

    def test_wrong_code_and_descriptor_permissions(self):
        path = self.build / 'child.exe'
        raw = bytearray(path.read_bytes())
        raw[0x400] ^= 1
        path.write_bytes(raw)
        with self.assertRaises(ValueError):
            self.prepare()
        raw[0x400] ^= 1
        struct.pack_into('<I', raw, 0x98+0xf0+40+36, 0xe0000040)
        path.write_bytes(raw)
        with self.assertRaises(ValueError):
            self.prepare()

    def test_duplicate_symbol(self):
        path = self.build / 'child.map'
        path.write_text(path.read_text() + '\n 0001:00000000 C48FullArm 0000000140001012')
        with self.assertRaises(ValueError):
            self.prepare()

    def test_build_source_drift_rejected(self):
        source_dir = self.header.parent
        for name in a.BUILD_SOURCES - {'shared.h'}:
            (source_dir/name).write_text('SYNTHETIC '+name)
            (self.build/name).write_bytes((source_dir/name).read_bytes())
        generated = {'catalog.generated.h':b'SYNTHETIC CATALOG', 'cases.generated.h':b'SYNTHETIC CASES'}
        for name, raw in generated.items():
            (self.build/name).write_bytes(raw)
        (self.build/'build-epoch.h').write_text('#define C48F_SHARED_HEADER_SHA256_HEX "'+self.header_pin+'"\n')
        (self.build/'parent.exe').write_bytes(b'SYNTHETIC PARENT')
        (self.build/'parent.map').write_text('SYNTHETIC MAP')
        def entry(path):
            data = path.read_bytes()
            return {'name':path.name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
        sources = [entry(self.build/name) for name in sorted(a.BUILD_SOURCES)]
        (self.build/'source-manifest.private.json').write_text(json.dumps(sources))
        record = {'schema':'c48-full-helper-build-v1','exit':0,'sharedHeaderSHA256':self.header_pin,
                  'fixtureExecuted':False,'original904Executed':False,'source':sources,
                  'files':[entry(p) for p in self.build.iterdir()]}
        (self.build/'build-record.private.json').write_text(json.dumps(record))
        pins = {name:hashlib.sha256(raw).hexdigest() for name,raw in generated.items()}
        with patch.object(a,'HEADER',self.header_pin), patch.object(a,'GENERATED',pins):
            a.audit_build(self.build,source_dir)
            (source_dir/'child.c').write_text('SYNTHETIC CHANGED')
            with self.assertRaises(ValueError):
                a.audit_build(self.build,source_dir)


if __name__ == '__main__':
    unittest.main()
