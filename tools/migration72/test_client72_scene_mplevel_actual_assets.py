"""Independent synthetic v17 catalogs; no captured client binary fixtures."""
import struct
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import client72_scene_mplevel_actual_assets as subject


def aligned_string(value):
    raw = value.encode()
    result = struct.pack('<i', len(raw)) + raw
    return result + bytes((-len(result)) & 3)


def script_object(class_name='ConfigMetaConfig', namespace='MoleMole.Config'):
    return (aligned_string('ConfigMetaConfig') + struct.pack('<i', 7) + b'F' * 16
            + aligned_string(class_name) + aligned_string(namespace)
            + aligned_string('Assembly-CSharp.dll') + b'\0')


def catalog_file(types, objects, scripts=(), externals=()):
    """Build a tiny standard catalog with deliberate synthetic object bytes."""
    metadata = bytearray(b'2017.4.30f1\0' + struct.pack('<iBi', 19, 0, len(types)))
    for kind, script in types:
        metadata += struct.pack('<iBh', kind, 0, script)
        if kind == 114:
            metadata += b'S' * 16
        metadata += b'T' * 16
    metadata += struct.pack('<i', len(objects))
    payload = bytearray()
    for path_id, type_id, raw in objects:
        metadata += bytes((-(len(metadata) + 20)) & 3)
        metadata += struct.pack('<qIIi', path_id, len(payload), len(raw), type_id)
        payload += raw
    metadata += struct.pack('<i', len(scripts))
    for file_id, path_id in scripts:
        metadata += struct.pack('<i', file_id)
        metadata += bytes((-(len(metadata) + 20)) & 3)
        metadata += struct.pack('<q', path_id)
    metadata += struct.pack('<i', len(externals))
    for path in externals:
        metadata += b'\0' + bytes(16) + struct.pack('<i', 0) + path.encode() + b'\0'
    metadata += b'\0'
    data_offset = (20 + len(metadata) + 15) & ~15
    size = data_offset + len(payload)
    return (struct.pack('>4I', len(metadata), size, 17, data_offset) + bytes(4)
            + metadata + bytes(data_offset - 20 - len(metadata)) + payload)


class ActualAssetsTests(unittest.TestCase):
    def test_external_script_reference_preserves_all_64_bits(self):
        target_id = (1 << 40) + 26977
        script = catalog_file([(115, -1)], [(target_id, 0, script_object())])
        bodies = [bytes(16) + struct.pack('<iq', 1, value) for value in (target_id, 26977)]
        data = catalog_file([(114, 0), (114, 1)], [(10, 0, bodies[0]), (11, 1, bodies[1])],
                            [(1, target_id), (1, 26977)], ['globalgamemanagers.assets'])
        directory = subject.parse_directory(data)
        self.assertEqual('53' * 16, directory['types'][0]['scriptHash'])
        result = subject.audit_catalogs({
            'globalgamemanagers.assets': (script, subject.parse_directory(script)),
            'resources.assets': (data, directory)})
        self.assertEqual(target_id, result['targetScripts'][0]['pathID'])
        self.assertEqual([10], [row['pathID'] for row in result['targetReferences']])
        self.assertEqual(2, result['catalogs'][1]['monoBehaviourPPtrsChecked'])

    def test_plaintext_name_is_not_a_script_type_binding(self):
        raw = catalog_file([(115, -1)], [(1, 0, script_object('OtherClass'))])
        result = subject.audit_catalogs({'globalgamemanagers.assets': (raw, subject.parse_directory(raw))})
        self.assertEqual([], result['targetScripts'])

    def test_object_pptr_must_equal_the_metadata_script_reference(self):
        body = bytes(16) + struct.pack('<iq', 1, 88)
        raw = catalog_file([(114, 0)], [(7, 0, body)], [(1, 99)], ['globalgamemanagers.assets'])
        with self.assertRaisesRegex(ValueError, 'PPtr differs'):
            subject.audit_catalogs({'resources.assets': (raw, subject.parse_directory(raw))})

    def test_truncated_unsupported_and_unconsumed_metadata_are_rejected(self):
        raw = catalog_file([(115, -1)], [(1, 0, script_object())])
        endian = bytearray(raw)
        endian[16] = 1
        extra_metadata = bytearray(raw)
        struct.pack_into('>I', extra_metadata, 0, struct.unpack_from('>I', raw)[0] + 1)
        for name, value in [('short', raw[:-1]), ('endian', endian), ('metadata-tail', extra_metadata)]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                subject.parse_directory(bytes(value))

    def test_duplicate_object_identity_is_rejected(self):
        raw = catalog_file([(115, -1)], [(5, 0, script_object()), (5, 0, script_object())])
        with self.assertRaisesRegex(ValueError, 'identity'):
            subject.parse_directory(raw)

    def test_source_drift_is_rejected_at_the_entry_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'catalog.bin'
            path.write_bytes(b'synthetic input')
            with self.assertRaisesRegex(ValueError, 'Frozen source changed'):
                subject.verified(path, '0' * 64)


if __name__ == '__main__':
    unittest.main()
