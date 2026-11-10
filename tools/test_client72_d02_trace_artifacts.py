"""Offline contract tests for trace bootstrap; all images are synthetic data."""
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

from tools import client72_d02_trace_artifacts as artifact

PRIVATE_TEST = Path(__file__).resolve().parents[1] / 'local/migration72/d02-resume-20261110b/a-clear48-trace-artifacts-tests'
BASE = 0x180000000
IMAGE_SIZE = 0x5000
WRAPPER = bytes.fromhex('4883ec284989cb4889d14c89c24d89c8cc41ffd34883c428c3')
LEAVES = {
    'C48ReturnLeaf': bytes.fromhex('48b8efcdab89674523f1c3'),
    'C48YmmLeaf': bytes.fromhex('c5fe6f01c5fe7f02c4e37d18c001c4c17e7f00c5f877c3'),
    'C48FaultLeaf': bytes.fromhex('0f0bc3'),
    'C48XcrEntry': bytes.fromhex('31c90f01d048c1e2204809d0c3'),
}
LABELS = {
    'g_c48Descriptor': 0x3100, 'C48Wrapper': 0x1010,
    'C48WrapperBegin': 0x1010, 'C48Arm': 0x1020, 'C48Call': 0x1021,
    'C48AfterCall': 0x1024, 'C48WrapperEnd': 0x1029,
    'C48ReturnLeaf': 0x1050, 'C48ReturnLeafEnd': 0x105b,
    'C48YmmLeaf': 0x1070, 'C48YmmLeafEnd': 0x1087,
    'C48FaultLeaf': 0x10a0, 'C48FaultLeafEnd': 0x10a3,
    'C48ReadXcr0': 0x10c0, 'C48XcrEntry': 0x10c0, 'C48XcrEnd': 0x10cd,
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


class TraceArtifactTests(unittest.TestCase):
    def setUp(self):
        PRIVATE_TEST.mkdir(parents=True, exist_ok=True)
        self.tmp = tempfile.TemporaryDirectory(dir=PRIVATE_TEST)
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.private = self.root / 'private'
        self.contract = self.private / 'contract'
        self.contract.mkdir(parents=True)
        self.build = self.private / 'a-clear48-trace-witness' / 'build'
        self.build.mkdir(parents=True)
        self.output = self.build.parent / 'out'
        self.source = self.root / 'tools/client72_d02_native_trace'
        self.source.mkdir(parents=True)
        header = b'synthetic header epoch\n'
        self.header_hash = digest(header)
        (self.source / 'shared.h').write_bytes(header)
        (self.build / 'shared.h').write_bytes(header)
        source_records = [{'name': 'shared.h', 'sha256': digest(header), 'bytes': len(header)}]
        for name in ('child.c', 'witness.asm', 'parent.c', 'build.ps1'):
            source = ('synthetic source snapshot ' + name + '\n').encode()
            (self.source / name).write_bytes(source)
            (self.build / name).write_bytes(source)
            source_records.append({'name': name, 'sha256': digest(source), 'bytes': len(source)})
        self.record = {'exit': 0, 'sharedHeaderSHA256': self.header_hash,
                       'fixtureExecuted': False, 'source': source_records}
        self.write_record()
        cases = {'sharedWrapper': {'hex': WRAPPER.hex()}, 'cases': [
            {'leafHex': LEAVES[name].hex()} for name in ('C48ReturnLeaf', 'C48YmmLeaf', 'C48FaultLeaf')]}
        documents = {'interface.v1.private.json': {}, 'witness-cases.private.json': cases,
                     'event-schema.v1.private.json': {}, 'frozen-inputs.private.json': {}}
        pins = {}
        for name, doc in documents.items():
            raw = json.dumps(doc).encode()
            (self.contract / name).write_bytes(raw)
            pins[name] = digest(raw)
        self.context = patch.multiple(artifact, ROOT=self.root, PRIVATE=self.private, CONTRACT=self.contract,
                                      HEADER=self.header_hash, CONTRACT_PINS=pins)
        self.context.start()
        self.addCleanup(self.context.stop)
        self.labels = LABELS.copy()
        self.write_map()
        self.write_pe()

    def write_record(self):
        (self.build / 'build-record.private.json').write_text(json.dumps(self.record), encoding='utf-8')

    def write_map(self):
        text = ''.join(f' 0001:00000000 {name} {BASE + rva:016x}\n' for name, rva in self.labels.items())
        (self.build / 'child.map').write_text(text, encoding='utf-8')

    def write_pe(self, text_flags=0x60000020, data_flags=0xc0000040, data_size=0x400, image_size=IMAGE_SIZE):
        raw = bytearray(0xc00)
        raw[:2] = b'MZ'
        struct.pack_into('<I', raw, 0x3c, 0x80)
        raw[0x80:0x84] = b'PE\0\0'
        struct.pack_into('<HHIIIHH', raw, 0x84, 0x8664, 2, 0, 0, 0, 0xf0, 0)
        struct.pack_into('<H', raw, 0x98, 0x20b)
        struct.pack_into('<Q', raw, 0x98 + 24, BASE)
        struct.pack_into('<I', raw, 0x98 + 56, image_size)
        for p, name, size, va, rawptr, flags in (
            (0x188, b'.text', 0x400, 0x1000, 0x400, text_flags),
            (0x1b0, b'.data', data_size, 0x3000, 0x800, data_flags)):
            raw[p:p + 8] = name.ljust(8, b'\0')
            struct.pack_into('<IIII', raw, p + 8, size, va, 0x400, rawptr)
            struct.pack_into('<I', raw, p + 36, flags)
        for rva, code in [(0x1010, WRAPPER)] + [(LABELS[n], b) for n, b in LEAVES.items()]:
            offset = 0x400 + rva - 0x1000
            raw[offset:offset + len(code)] = code
        (self.build / 'child.exe').write_bytes(raw)

    def reject(self, message):
        with self.assertRaisesRegex(ValueError, message):
            artifact.prepare(self.build, self.output)
        self.assertFalse((self.output / 'bootstrap.bin').exists())
        self.assertFalse((self.output / 'bootstrap.private.json').exists())

    def test_bootstrap_layout_and_identity(self):
        artifact.prepare(self.build, self.output)
        data = (self.output / 'bootstrap.bin').read_bytes()
        exe = (self.build / 'child.exe').read_bytes()
        self.assertEqual(len(data), 128)
        values = struct.unpack('<8sIIIIQ32s32s8I', data)
        self.assertEqual(values[:6], (b'C48BOOT1', 1, 128, 0x3100, 7, IMAGE_SIZE))
        self.assertNotEqual(IMAGE_SIZE, len(exe))
        self.assertEqual(values[6:8], (hashlib.sha256(exe).digest(), bytes.fromhex(self.header_hash)))
        self.assertEqual(values[8:], (0x1020, 0x1021, 0x1024, 0x1029, 0x1050, 0x1070, 0x10a0, 0x1010))
        summary = json.loads((self.output / 'bootstrap.private.json').read_text(encoding='utf-8'))
        self.assertEqual(summary['labelsRva'], LABELS)
        self.assertEqual((summary['imageBytes'], summary['childFileBytes']), (IMAGE_SIZE, len(exe)))
        self.assertEqual(summary['bootstrapSHA256'], digest(data))
        self.assertFalse(summary['executed'])
        self.assertEqual(summary['nativeCPU'], 0)
        self.assertTrue(summary['dependencyReviewRequired'])
        self.assertTrue(summary['callerUnwindReviewRequired'])

    def test_missing_and_duplicate_map_symbols(self):
        for duplicate in (False, True):
            with self.subTest(duplicate=duplicate):
                self.write_map()
                mapfile = self.build / 'child.map'
                lines = mapfile.read_text().splitlines(keepends=True)
                mapfile.write_text(''.join(lines + [lines[1]] if duplicate else lines[1:]), encoding='utf-8')
                self.reject('duplicate symbol' if duplicate else 'missing map symbol')
                self.output.rmdir()

    def test_wrong_leaf_bytes_and_label_relationships(self):
        for kind in ('bytes', 'extent', 'wrapper', 'alias'):
            with self.subTest(kind=kind):
                self.labels = LABELS.copy()
                self.write_pe()
                if kind == 'bytes':
                    exe = self.build / 'child.exe'
                    raw = bytearray(exe.read_bytes())
                    raw[0x450] ^= 1
                    exe.write_bytes(raw)
                elif kind == 'extent':
                    self.labels['C48ReturnLeafEnd'] += 1
                elif kind == 'wrapper':
                    self.labels['C48Call'] += 1
                else:
                    self.labels['C48ReadXcr0'] += 1
                self.write_map()
                self.reject({'bytes': 'leaf bytes or extent', 'extent': 'leaf bytes or extent',
                             'wrapper': 'wrapper label drift', 'alias': 'XCR alias'}[kind])
                self.output.rmdir()

    def test_section_permissions_and_descriptor_extent(self):
        for kwargs, error in [({'text_flags': 0xe0000020}, 'section permissions'),
                              ({'data_flags': 0x40000040}, 'descriptor not writable non-code'),
                              ({'data_flags': 0xe0000040}, 'descriptor not writable non-code'),
                              ({'data_size': 0x1ff}, 'descriptor not writable non-code'),
                              ({'image_size': 0x31ff}, 'descriptor outside image')]:
            with self.subTest(kwargs=kwargs):
                self.write_pe(**kwargs)
                self.reject(error)
                self.output.rmdir()

    def test_contract_header_build_and_source_epoch_drift(self):
        for kind in ('contract', 'header', 'snapshot', 'current', 'exit', 'executed', 'record_header'):
            with self.subTest(kind=kind):
                contract = self.contract / 'interface.v1.private.json'
                before = contract.read_bytes()
                build_header = self.build / 'shared.h'
                old_header = build_header.read_bytes()
                build_source = self.build / 'child.c'
                old_source = build_source.read_bytes()
                current = self.source / 'child.c'
                old_current = current.read_bytes()
                old_record = self.record.copy()
                if kind == 'contract': contract.write_bytes(before + b' ')
                elif kind == 'header': build_header.write_bytes(old_header + b' ')
                elif kind == 'snapshot': build_source.write_bytes(old_source + b' ')
                elif kind == 'current': current.write_bytes(old_current + b' ')
                elif kind == 'exit': self.record['exit'] = 1
                elif kind == 'executed': self.record['fixtureExecuted'] = True
                else: self.record['sharedHeaderSHA256'] = '0' * 64
                self.write_record()
                self.reject({'contract': 'contract drift', 'header': 'header epoch drift',
                             'snapshot': 'built source snapshot drift', 'current': 'current source drift',
                             'exit': 'build not accepted', 'executed': 'build not accepted',
                             'record_header': 'build not accepted'}[kind])
                contract.write_bytes(before)
                build_header.write_bytes(old_header)
                build_source.write_bytes(old_source)
                current.write_bytes(old_current)
                self.record = old_record
                self.write_record()
                self.output.rmdir()

    def test_missing_source_manifest_is_rejected(self):
        # A successful build record must identify the source epoch, not merely
        # validate whichever records happen to be present.
        self.record['source'] = []
        self.write_record()
        with self.assertRaises(ValueError):
            artifact.prepare(self.build, self.output)
        self.assertFalse((self.output / 'bootstrap.bin').exists())
    def test_incomplete_or_duplicate_source_manifest_is_rejected(self):
        original = self.record['source']
        for records in (original[:-1], original + [original[-1]]):
            with self.subTest(count=len(records)):
                self.output = self.build.parent / ('out-' + str(len(records)))
                self.record['source'] = records
                self.write_record()
                with self.assertRaises(ValueError):
                    artifact.prepare(self.build, self.output)
                self.assertFalse((self.output / 'bootstrap.bin').exists())

    def test_output_conflict_and_non_owned_paths(self):
        self.output.mkdir()
        marker = self.output / 'existing'
        marker.write_bytes(b'preserve')
        self.reject('build missing or output exists')
        self.assertEqual(marker.read_bytes(), b'preserve')
        outside = self.root / 'outside'
        with self.assertRaisesRegex(ValueError, 'non-owned artifact path'):
            artifact.prepare(self.build, outside)
        self.assertFalse(outside.exists())
        with self.assertRaisesRegex(ValueError, 'non-owned artifact path'):
            artifact.prepare(self.root, self.build.parent / 'other')
        self.assertFalse((self.build.parent / 'other').exists())


if __name__ == '__main__':
    unittest.main()
