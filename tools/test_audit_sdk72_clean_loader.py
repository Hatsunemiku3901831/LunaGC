import struct
import tempfile
import unittest

from tools import audit_sdk72_clean_loader as audit


def owned_pe(names=('OwnedAlpha', 'OwnedOmega'), base=1, unnamed=False, forwarder=False, hole=False):
    """Independent non-executable PE fixture with a text section and export directory."""
    data = bytearray(0x1400)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 60, 0x80)
    data[0x80:0x84] = b'PE\0\0'
    struct.pack_into('<HH', data, 0x84, 0x8664, 2)
    struct.pack_into('<H', data, 0x94, 0xf0)
    optional = 0x98
    struct.pack_into('<H', data, optional, 0x20b)
    struct.pack_into('<Q', data, optional + 24, 0x180000000)
    struct.pack_into('<II', data, optional + 56, 0x4000, 0x200)
    struct.pack_into('<II', data, optional + 112, 0x2000, 0x1000)
    sections = optional + 0xf0
    struct.pack_into('<8sIIIIIIHHI', data, sections, b'.text\0\0\0', 0x200, 0x1000, 0x200, 0x200, 0, 0, 0, 0, 0x60000020)
    struct.pack_into('<8sIIIIIIHHI', data, sections + 40, b'.edata\0\0', 0x1000, 0x2000, 0x1000, 0x400, 0, 0, 0, 0, 0x40000040)
    count = len(names)
    named_count = count - int(unnamed)
    eat = 0x2040
    pointers = eat + count * 4
    ordinals = pointers + named_count * 4
    cursor = ordinals + named_count * 2
    for index, name in enumerate(names):
        struct.pack_into('<I', data, 0x400 + eat - 0x2000 + index * 4, 0 if hole and index == 1 else 0x1000 + 8 * index)
        if index < named_count:
            struct.pack_into('<I', data, 0x400 + pointers - 0x2000 + index * 4, cursor)
            struct.pack_into('<H', data, 0x400 + ordinals - 0x2000 + index * 2, index)
            encoded = name.encode('ascii') + b'\0'
            data[0x400 + cursor - 0x2000:0x400 + cursor - 0x2000 + len(encoded)] = encoded
            cursor += len(encoded)
    if forwarder:
        struct.pack_into('<I', data, 0x400 + eat - 0x2000, cursor)
        encoded = b'OwnedDependency.Forwarded\0'
        data[0x400 + cursor - 0x2000:0x400 + cursor - 0x2000 + len(encoded)] = encoded
    struct.pack_into('<IIHHIIIIIII', data, 0x400, 0, 0, 0, 0, 0, base, count, named_count, eat, pointers, ordinals)
    return bytes(data)


class CleanLoaderAuditTest(unittest.TestCase):
    def test_export_names_original_ordinals_and_addresses_are_independent(self):
        rows = audit.PE(owned_pe(base=9)).exports()
        self.assertEqual([(row['name'], row['ordinal'], row['rva']) for row in rows],
                         [('OwnedAlpha', 9, 0x1000), ('OwnedOmega', 10, 0x1008)])

    def test_forwarder_is_retained_and_cannot_pass_native_thunk_contract(self):
        rows = audit.PE(owned_pe(forwarder=True)).exports()
        self.assertEqual(rows[0]['forwarder'], 'OwnedDependency.Forwarded')
        with self.assertRaisesRegex(ValueError, 'coverage differ'):
            audit.require_exports(rows, ['OwnedAlpha', 'OwnedOmega'])

    def test_ordinal_only_export_is_not_misreported_as_absent(self):
        rows = audit.PE(owned_pe(unnamed=True)).exports()
        self.assertEqual(rows[1]['name'], None)
        self.assertEqual(rows[1]['ordinal'], 2)
        with self.assertRaises(ValueError):
            audit.require_exports(rows, ['OwnedAlpha', 'OwnedOmega'])

    def test_zero_eat_hole_is_not_an_export(self):
        rows = audit.PE(owned_pe(hole=True)).exports()
        self.assertEqual(len(rows), 1)
        with self.assertRaises(ValueError):
            audit.require_exports(rows, ['OwnedAlpha', 'OwnedOmega'])

    def test_wrong_ordinals_and_extra_exports_are_rejected(self):
        with self.assertRaises(ValueError):
            audit.require_exports(audit.PE(owned_pe(base=2)).exports(), ['OwnedAlpha', 'OwnedOmega'])
        with self.assertRaises(ValueError):
            audit.require_exports(audit.PE(owned_pe()).exports(), ['OwnedAlpha'])

    def test_bad_headers_truncation_and_unbacked_windows_fail(self):
        for value in (b'', b'MZ', owned_pe()[:160], b'XY' + owned_pe()[2:]):
            with self.subTest(size=len(value)), self.assertRaises(ValueError):
                audit.PE(value)
        with self.assertRaisesRegex(ValueError, 'not file-backed'):
            audit.PE(owned_pe()).bytes_at(0x1800, 16)

    def test_link_inputs_pin_all_ordinals_and_separate_owned_missing_export(self):
        names = audit.source_names()
        self.assertEqual(len(names), 47)
        production = audit.def_text(names, [('Sdk72CleanInspectLoadedSdk', 48)])
        self.assertIn('Astrolabe_AddDetectionThread @1', production)
        self.assertIn('Astrolabe_UploadFileByChunks @47', production)
        self.assertIn('Sdk72CleanInspectLoadedSdk @48', production)
        omitted = audit.def_text(names, [('OwnedMockAttachCount', 48)], omit_last=True)
        self.assertNotIn('Astrolabe_UploadFileByChunks', omitted)
        self.assertIn('OwnedMockAttachCount @48', omitted)

    def test_output_cannot_escape_task_directory_or_overwrite_evidence(self):
        with self.assertRaises(ValueError):
            audit.output_path(audit.ROOT / 'outside-clean-loader.json')
        audit.RAW.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=audit.RAW) as evidence:
            with self.assertRaises(FileExistsError):
                audit.output_path(evidence.name)


if __name__ == '__main__':
    unittest.main()
