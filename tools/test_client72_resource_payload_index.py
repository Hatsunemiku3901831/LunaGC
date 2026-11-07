import struct
import unittest

try:
    from .client72_resource_payload_index import Reader, asset_record, choose_variant, parse_index
except ImportError:
    from client72_resource_payload_index import Reader, asset_record, choose_variant, parse_index


def fixture(sort=True):
    def ints(*values):
        return struct.pack('<'+'i'*len(values), *values)
    # Explicit source layout, including a high u64 and nested variant/dependency lists.
    raw = ints(1,3)+b'Foo'+ints(2)+b'.X'
    raw += ints(2)+struct.pack('<QB',0xfedcba9876543210,2)
    raw += struct.pack('<BiBi',0,7,1,9)+struct.pack('<QB',0x100000001,0)
    raw += ints(1,2,12,7,2,8,9)  # one bundle with two dependencies, capacity12
    raw += ints(2,101,102)+ints(1,103)
    raw += ints(1,3,2)+struct.pack('<iBBiBB',101,5,1,102,3,0)
    raw += ints(2,101,1,7,11,22,102,1,9,0,0)
    return raw+(ints(2,102,101) if sort else b'')


class IndexContractTest(unittest.TestCase):
    def test_full_nested_and_u64(self):
        raw = fixture()
        result, locations = parse_index(raw)
        self.assertEqual(result['finalPosition'],len(raw))
        self.assertEqual(result['remainderBytes'],0)
        self.assertEqual(result['types'],[('Foo','.X')])
        self.assertEqual(result['assetSamples'][0]['key'],0xfedcba9876543210)
        self.assertEqual(result['assetSamples'][1]['key'],0x100000001)
        self.assertEqual(result['sections']['bundles']['dependencyCount'],2)
        self.assertEqual(result['sections']['locations']['zeroValuePairs'],1)
        self.assertEqual(list(struct.iter_unpack('<6i',locations)),[(101,7,11,22,3,11),(102,9,0,0,3,6)])

    def test_selection_is_first_nonzero_and_matching_tag(self):
        variants = [(2,0),(7,123),(1,456)]
        self.assertEqual(choose_variant(variants,0),123)
        self.assertEqual(choose_variant(variants,1),456)
        self.assertEqual(choose_variant(variants,7),123)
        self.assertEqual(choose_variant([],1),0)

    def test_optional_sort_requires_exact_eof(self):
        result,_ = parse_index(fixture(False))
        self.assertFalse(result['sections']['sort']['present'])
        for suffix in (b'\x00',b'junk',b'\x00'*5):
            with self.subTest(suffix=suffix),self.assertRaises(ValueError):
                parse_index(fixture()+suffix)

    def test_every_partial_byte_truncation_stops(self):
        raw = fixture(False)
        # An absent final sort list is valid; no other section may be partial.
        for n in range(len(raw)):
            with self.subTest(length=n),self.assertRaises(ValueError):
                parse_index(raw[:n])

    def test_negative_and_impossible_counts(self):
        for count in (-1,0x7fffffff):
            with self.subTest(count=count),self.assertRaises(ValueError):
                parse_index(struct.pack('<i',count))

    def test_actual_wire_value_signed_and_key_unsigned(self):
        r = Reader(struct.pack('<QB',0xffffffffffffffff,1)+struct.pack('<Bi',255,-123))
        row = asset_record(r)
        self.assertEqual(row['key'],0xffffffffffffffff)
        self.assertEqual(row['variants'],[(255,-123)])
        self.assertEqual(row['end'],14)

    def test_non_ascii_is_explicitly_unimplemented(self):
        with self.assertRaisesRegex(ValueError,'Non-ASCII'):
            Reader(struct.pack('<i',1)+b'\xff').ascii_string()

    def test_dependency_total_and_location_capacity(self):
        raw = fixture()
        result,_ = parse_index(raw)
        bad = bytearray(raw)
        struct.pack_into('<i',bad,result['sections']['bundles']['start']+4,3)
        with self.assertRaisesRegex(ValueError,'Dependency total'):
            parse_index(bad)
        bad = bytearray(raw)
        struct.pack_into('<i',bad,result['sections']['locations']['start']+12,12)
        with self.assertRaisesRegex(ValueError,'location bundle'):
            parse_index(bad)


if __name__ == '__main__':
    unittest.main()
