"""Independent input-corruption and bounded descriptor-layout checks."""
import copy
import json
from pathlib import Path
import struct
import tempfile
import unittest

try:
    from .client72_resource_provider import Snapshot, descriptor_slot, digest, fnv64, registered_prefix, BASE
except ImportError:
    from client72_resource_provider import Snapshot, descriptor_slot, digest, fnv64, registered_prefix, BASE


class ProviderContractTests(unittest.TestCase):
    def snapshot(self, mutation=None):
        profile = {'binary': dict(path='client.exe', sha256='fixed', exists=True, size=1)}
        d = dict(status='completed', creation=dict(filetime=123, winerror=None),
                 inputChecks=dict(profileSHA256='profile', files={'binary': dict(path='client.exe', sha256='fixed')}),
                 mainModule=dict(base=BASE), reads=[dict(label='sample', address=4096, length=8,
                                                       bytesHex='0100000000000000', error=None,
                                                       sha256=digest(b'\1'+bytes(7)))])
        if mutation:
            mutation(d)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'snapshot.json'
            path.write_text(json.dumps(d))
            return Snapshot(path, profile, 'profile')

    def test_snapshot_integrity_and_pointer_link(self):
        s = self.snapshot()
        self.assertEqual(s.at('sample', 4096, 8), b'\1'+bytes(7))
        with self.assertRaises(ValueError):
            s.at('sample', 4104, 8)
        with self.assertRaises(ValueError):
            s.at('sample', 4096, 4)

    def test_snapshot_rejects_corruption_and_read_failure(self):
        mutations = [lambda d: d['reads'][0].update(bytesHex='0200000000000000'),
                     lambda d: d['reads'][0].update(length=7),
                     lambda d: d['reads'][0].update(error=299),
                     lambda d: d['reads'].append(copy.deepcopy(d['reads'][0])),
                     lambda d: d['inputChecks'].update(profileSHA256='other'),
                     lambda d: d['inputChecks']['files']['binary'].update(sha256='other'),
                     lambda d: d.update(status='failed')]
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                self.snapshot(mutation)

    def test_snapshot_rejects_unbounded_read(self):
        def change(d):
            data = bytes(257)
            d['reads'][0].update(length=257, bytesHex=data.hex(), sha256=digest(data))
        with self.assertRaises(ValueError):
            self.snapshot(change)

    def test_fnv_published_vectors(self):
        self.assertEqual(fnv64(b''), 0xcbf29ce484222325)
        self.assertEqual(fnv64(b'a'), 0xaf63dc4c8601ec8c)
        self.assertEqual(fnv64(b'foobar'), 0x85944171f73967e8)
        self.assertNotEqual(fnv64(b'ctable.dat'), fnv64(b'CTABLE.DAT'))

    def test_descriptor_ring_and_signed_id(self):
        header = bytearray(48)
        for offset, value in ((16, 0x1000), (24, 8), (32, 15), (40, 20)):
            struct.pack_into('<Q', header, offset, value)
        self.assertEqual(descriptor_slot(header, 0), (0x1038, 8))
        self.assertEqual(descriptor_slot(header, 1), (0x1000, 0))
        self.assertEqual(descriptor_slot(header, 2), (0x1000, 8))
        self.assertEqual(descriptor_slot(header, 0x80000002), (0x1000, 8))
        self.assertEqual(descriptor_slot(header, 19), (0x1008, 0))

    def test_descriptor_bounds_and_invalid_layout(self):
        header = bytearray(48)
        for offset, value in ((16, 0x1000), (24, 8), (40, 20)):
            struct.pack_into('<Q', header, offset, value)
        for file_id in (20, 0x80000014, -1, 0xffffffff, 1 << 32):
            with self.subTest(file_id=file_id), self.assertRaises(ValueError):
                descriptor_slot(header, file_id)
        for map_size in (0, 3):
            struct.pack_into('<Q', header, 24, map_size)
            with self.assertRaises(ValueError):
                descriptor_slot(header, 0)

    def test_prefix_uses_length_and_proven_type(self):
        s = self.snapshot()
        obj = struct.pack('<QQ', 0x14220ba70, 0)
        header = bytearray(40)
        header[8:18] = b'archive:/\0'
        struct.pack_into('<Q', header, 24, 9)
        s.rows = {'fileRouter.registered-0.object': (0x2000, obj),
                  'fileRouter.registered-0.prefixHeader': (0x2030, bytes(header))}
        self.assertEqual(registered_prefix(s), b'archive:/')
        s.rows['fileRouter.registered-0.object'] = (0x2000, struct.pack('<QQ', 0x1421e8a38, 0))
        with self.assertRaises(ValueError):
            registered_prefix(s)

    def test_prefix_does_not_infer_missing_heap_bytes(self):
        s = self.snapshot()
        header = bytearray(40)
        struct.pack_into('<Q', header, 24, 16)
        s.rows = {'fileRouter.registered-0.object': (0x2000, struct.pack('<QQ', 0x14220ba70, 0)),
                  'fileRouter.registered-0.prefixHeader': (0x2030, bytes(header))}
        with self.assertRaises(ValueError):
            registered_prefix(s)


if __name__ == '__main__':
    unittest.main()
