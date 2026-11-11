import copy
import hashlib
import struct
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import proud_actual_adoption_window as subject


class TryInsertWindowGateTest(unittest.TestCase):
    def test_exact_e8_rejects_change_and_wrong_target(self):
        subject.check_call(bytes.fromhex('e8407b6a04'))
        for value in (bytes.fromhex('e8407b6a05'), bytes.fromhex('e9407b6a04'), b''):
            with self.subTest(value=value), self.assertRaises(ValueError):
                subject.check_call(value)

    def test_unique_exact_containing_and_bounds(self):
        base, entry = 0x140000000, subject.ENTRY
        relative = entry - base
        raw = struct.pack('<III', relative, relative + 64, 123)
        diagnostic = subject.extent(raw, entry, base, 0x3000)
        self.assertEqual(subject.choose(diagnostic)['size'], 64)
        for test in (raw + raw, struct.pack('<III', relative - 2, relative + 64, 0),
                     struct.pack('<III', relative, relative + 4097, 0),
                     struct.pack('<III', relative, relative, 0)):
            with self.subTest(test=test.hex()), self.assertRaises(ValueError):
                subject.choose(subject.extent(test, entry, base, 0x3000))
        with self.assertRaises(ValueError):
            subject.extent(b'bad', entry, base, 0)
        overlap = raw + struct.pack('<III', relative - 5, relative + 10, 0)
        with self.assertRaisesRegex(ValueError, 'ambiguous pdata'):
            subject.choose(subject.extent(overlap, entry, base, 0))

    def test_hash_and_size_pin_reject_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input'
            path.write_bytes(b'fixed')
            digest = hashlib.sha256(b'fixed').hexdigest()
            self.assertEqual(b'fixed', subject.checked(path, digest, 5))
            with self.assertRaisesRegex(ValueError, 'pin mismatch'):
                subject.checked(path, digest, 4)
            path.write_bytes(b'other')
            with self.assertRaisesRegex(ValueError, 'pin mismatch'):
                subject.checked(path, digest)

    def test_decoding_requires_contiguous_complete_instruction_stream(self):
        result = subject.decode(bytes.fromhex('b801000000c3'))
        self.assertEqual([x['mnemonic'] for x in result['lines']], ['mov', 'ret'])
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            subject.decode(bytes.fromhex('b8010000000f'))

    def test_decode_instruction_limit(self):
        with self.assertRaisesRegex(ValueError, 'instruction bound'):
            subject.decode(b'\x90' * 1025)

    def test_metadata_tables_use_absolute_offsets_and_unique_method_tuple(self):
        metadata = bytearray(256)
        table_base, spec_base = 29, 101
        table = struct.pack('<ii', 3, 1) + b'\x00' * 6
        spec = struct.pack('<iii', 10196, 50461, -1)
        metadata[table_base:table_base + 14] = table
        metadata[spec_base + 12:spec_base + 24] = spec
        aot = {'tables': {
            'genericMethods': {'offset': hex(table_base), 'bytes': 14, 'count': 1, 'sha256': hashlib.sha256(table).hexdigest()},
            'methodSpecs': {'offset': hex(spec_base), 'bytes': 24, 'sha256': hashlib.sha256(metadata[spec_base:spec_base + 24]).hexdigest()},
            'pointerBase': '0x1000'}}
        target = {'owner': 1222, 'method': 10196, 'name': 'TryInsert', 'classInstance': 50461,
                  'methodInstance': -1, 'tableIndex': 0, 'tableRecord14': table.hex(), 'methodSpec': 1,
                  'specRecord12': spec.hex(), 'pointerIndex': 3, 'pointerSlot': '0x1018',
                  'address': '0x1593e7bf0', 'pointerQWORD': 'ignored'}
        self.assertEqual(subject.verify_aot(bytes(metadata), aot, target)['tableIndex'], 0)
        wrong = copy.deepcopy(aot)
        wrong['tables']['genericMethods']['offset'] = hex(table_base + 1)
        with self.assertRaisesRegex(ValueError, 'metadata table differs'):
            subject.verify_aot(bytes(metadata), wrong, target)
        wrong = copy.deepcopy(target)
        wrong['classInstance'] += 1
        with self.assertRaisesRegex(ValueError, 'selected row differs'):
            subject.verify_aot(bytes(metadata), aot, wrong)
        duplicate = table + table
        metadata[table_base:table_base + 28] = duplicate
        aot['tables']['genericMethods'].update(bytes=28, count=2, sha256=hashlib.sha256(duplicate).hexdigest())
        with self.assertRaisesRegex(ValueError, 'not unique'):
            subject.verify_aot(bytes(metadata), aot, target)

if __name__ == '__main__':
    unittest.main()
