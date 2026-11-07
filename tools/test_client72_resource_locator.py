import hashlib
import struct
import unittest

try:
    from .client72_resource_locator import reference_hash, reference_pack, serialized_objects
except ImportError:
    from client72_resource_locator import reference_hash, reference_pack, serialized_objects


def serialized_fixture(value,path_id=1<<40):
    raw=struct.pack('<I',len(value))+value
    metadata=b'v17\0'+struct.pack('<iBI',-2,0,1)
    metadata+=struct.pack('<iBh',1208,0,-1)+bytes(16)+struct.pack('<I',1)
    metadata+=bytes((-(20+len(metadata)))%4)
    metadata+=struct.pack('<QIII',path_id,0,len(raw),0)
    header=struct.pack('>4I',len(metadata),128+len(raw),17,128)+bytes(4)
    return header+metadata+bytes(128-len(header)-len(metadata))+raw


class LocatorTests(unittest.TestCase):
    def test_fixed_actual_avatar_key(self):
        self.assertEqual(reference_hash('Data/_ExcelBinOutput/AvatarExcelConfigData','.MiHoYoBinData'),
                         0x35f3539930d6e87b)

    def test_hash_width_padding_transition(self):
        self.assertEqual(reference_hash('A'*256,''),int.from_bytes(hashlib.md5(b'A'*256).digest()[:8],'little'))
        self.assertEqual(reference_hash('A'*257,''),int.from_bytes(hashlib.md5(b'A'*257+bytes(255)).digest()[:8],'little'))
        self.assertNotEqual(reference_hash('a',''),reference_hash('A',''))

    def test_utf16_low_byte_not_utf8(self):
        self.assertEqual(reference_hash('\u0100',''),reference_hash('\0',''))
        self.assertEqual(reference_hash(None,'.MiHoYoBinData'),0)
        self.assertEqual(reference_hash('','.MiHoYoBinData'),0)

    def test_real_identifier_and_high_bits(self):
        self.assertEqual(reference_pack(0x35f3539930d6e87b,178),0x9930d6e8000000b2)
        self.assertEqual(reference_pack(0xffffffffffffffff,-1),0xffffffff00000001)
        self.assertEqual(reference_pack(0x100000001,0x40000001),0x0100000000000001)
        self.assertEqual(reference_pack(1,0x20000000),0x20000000)

    def test_binary_brace_prefix_is_not_json(self):
        raw=b'{\x00\xf5\xffnot-json'
        parsed=serialized_objects(serialized_fixture(raw))
        obj=parsed['objects'][0]
        self.assertEqual(obj['pathID'],1<<40)
        self.assertEqual(obj['lengthPrefixedBytes'],dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()))
        self.assertNotIn('json',obj)

    def test_byte_array_length_exceeds_actual_object(self):
        raw=bytearray(serialized_fixture(b'abc'))
        struct.pack_into('<I',raw,128,4)
        with self.assertRaisesRegex(ValueError,'Byte-array extent'):
            serialized_objects(raw)

    def test_invalid_header_and_truncated_metadata(self):
        raw=serialized_fixture(b'abc')
        for n in (0,19,20,60,len(raw)-1):
            with self.subTest(size=n),self.assertRaises(ValueError):serialized_objects(raw[:n])
        wrong=bytearray(raw);struct.pack_into('>I',wrong,0,2)
        with self.assertRaises(ValueError):serialized_objects(wrong)

    def test_unreviewed_class_and_object_extent_rejected(self):
        raw=bytearray(serialized_fixture(b'abc'))
        struct.pack_into('<i',raw,33,114)
        with self.assertRaisesRegex(ValueError,'class schema'):serialized_objects(raw)
        raw=bytearray(serialized_fixture(b'abc'))
        struct.pack_into('<I',raw,72,0x100000)
        with self.assertRaisesRegex(ValueError,'identity/extent'):serialized_objects(raw)


if __name__=='__main__':unittest.main()
