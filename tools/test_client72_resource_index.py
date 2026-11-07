import struct
import unittest

from tools.client72_resource_index import recover_declarations


class NamesFixture:
    def decode(self, index):
        return {11: 'FixtureOwner', 12: 'Fixture.Namespace', 13: 'ReadFixture'}[index]


class ResourceIndexDeclarationTest(unittest.TestCase):
    def setUp(self):
        # Independent fixture uses the established 70-byte type row, not a guessed 0x40 row.
        self.binary = bytearray(64)
        self.metadata = bytearray(512)
        self.owner, self.method, self.method_base = 3, 2, 300
        type_offset = self.owner * 70
        struct.pack_into('<I', self.metadata, type_offset + 0xc, self.method ^ 0x5abb91bd)
        struct.pack_into('<H', self.metadata, type_offset + 0x3a, (1 - 0xbf1a) & 0xffff)
        struct.pack_into('<I', self.metadata, type_offset + 0x18, (11 - 0xa285a06d) & 0xffffffff)
        struct.pack_into('<I', self.metadata, type_offset + 0x14, 12 ^ 0x149e96f1)
        key = (((self.method * 0x1639) ^ 0x5e01c03b) * 0x106b250c17f34ccb + 0x069ffdcbe218faf4) & 0xffffffffffffffff
        entry = self.method_base + self.method * 26
        struct.pack_into('<I', self.metadata, entry + 8, (13 + key - 0xde01c441) & 0xffffffff)
        self.metadata[entry + 0x18] = ((255 + key) & 0xff) ^ 0xf9
        self.address = 0xfedcba9876543210
        struct.pack_into('<Q', self.binary, self.method * 8, self.address)
        self.specs = [(self.owner, self.method, 'ReadFixture', self.address)]

    def recover(self, specs=None):
        return recover_declarations(self.binary, self.metadata, NamesFixture(), 0,
                                    self.method_base, 0, self.specs if specs is None else specs)

    def test_recovers_actual_row_stride_nonzero_count_and_all_pointer_bits(self):
        self.assertEqual(self.recover(), [dict(owner=3, ownerName='FixtureOwner', namespace='Fixture.Namespace',
            index=2, name='ReadFixture', address='0xfedcba9876543210', parameterCount=255)])

    def test_wrong_owner_cannot_borrow_same_method_address(self):
        with self.assertRaisesRegex(ValueError, 'outside declaring type'):
            self.recover([(2, self.method, 'ReadFixture', self.address)])

    def test_changed_method_address_rejected(self):
        struct.pack_into('<Q', self.binary, self.method * 8, self.address - 1)
        with self.assertRaisesRegex(ValueError, 'declaration changed'):
            self.recover()

    def test_changed_expected_name_rejected(self):
        with self.assertRaisesRegex(ValueError, 'declaration changed'):
            self.recover([(self.owner, self.method, 'OtherFixture', self.address)])

    def test_truncated_member_record_rejected(self):
        self.metadata = self.metadata[:self.method_base + self.method * 26 + 8]
        with self.assertRaises(ValueError):
            self.recover()


if __name__ == '__main__':
    unittest.main()
