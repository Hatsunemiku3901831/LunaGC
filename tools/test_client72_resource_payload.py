"""Independent block-format, serialized-boundary and 64-bit schema regressions."""
import struct
import unittest

try:
    from .client72_resource_payload import lz4_block, asset_index, serialized17
except ImportError:
    from client72_resource_payload import lz4_block, asset_index, serialized17


class PayloadTests(unittest.TestCase):
    def test_literal_and_overlapping_match(self):
        self.assertEqual(lz4_block(b'\x50hello',5),b'hello')
        self.assertEqual(lz4_block(b'\x16A\x01\x00\x50hello',16),b'A'*11+b'hello')
        self.assertEqual(lz4_block(b'\0',0),b'')

    def test_extended_literals_and_match(self):
        raw=bytes(range(100))*3
        self.assertEqual(lz4_block(b'\xf0\xff\x1e'+raw,300),raw)
        self.assertEqual(lz4_block(b'\x1fA\x01\x00\xff\x1a\x50hello',306),b'A'*301+b'hello')

    def test_truncation_offset_and_output_errors(self):
        cases=[(b'',0),(b'\xf0',20),(b'\xf0\xff',270),(b'\x50hell',5),
               (b'\x10A\x01',5),(b'\x10A\x00\x00\x50hello',10),
               (b'\x10A\x02\x00\x50hello',10),(b'\x50hello',4),
               (b'\x50hello',6),(b'\x1fA\x01\x00',20)]
        for raw,size in cases:
            with self.subTest(raw=raw),self.assertRaises(ValueError):lz4_block(raw,size)

    def test_asset_index_sizes_are_u64_not_length_offset_pair(self):
        name=b'0000006f'
        raw=struct.pack('<III',0,1,len(name))+name+struct.pack('<iQQ',0,1<<40,(1<<33)+123)
        item=asset_index(raw)['records'][0]
        self.assertEqual(item['pathID'],1<<40)
        self.assertEqual(item['fileSize'],(1<<33)+123)
        self.assertNotIn('byteOffset',item)
        self.assertTrue(asset_index(raw)['contains0000006f'])

    def test_asset_index_named_object_and_empty_list(self):
        raw=struct.pack('<I',3)+b'abc\0'+struct.pack('<I',0)
        self.assertEqual(asset_index(raw)['objectName'],'abc')
        self.assertEqual(asset_index(raw)['records'],[])

    def test_asset_index_truncation_and_trailing_bytes(self):
        raw=struct.pack('<III',0,1,8)+b'0000006f'+struct.pack('<iQQ',0,3,48)
        for wrong in (raw[:-1],raw+b'\0',raw[:10],struct.pack('<II',0,100001)):
            with self.subTest(size=len(wrong)),self.assertRaises(ValueError):asset_index(wrong)

    def test_serialized_header_rejects_wrong_size_version_endian(self):
        for values,flag in (((40,80,16,64),0),((40,81,17,64),0),((40,80,17,64),1)):
            data=struct.pack('>4I',*values)+bytes((flag,0,0,0))+bytes(60)
            with self.assertRaises(ValueError):serialized17(data)


if __name__=='__main__':unittest.main()
