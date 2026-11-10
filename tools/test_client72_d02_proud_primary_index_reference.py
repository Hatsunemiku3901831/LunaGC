import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from tools import client72_d02_proud_primary_index_reference as ref


def enc(n):
    out = bytearray()
    while n >= 128:
        out.append((n & 127) | 128)
        n >>= 7
    out.append(n)
    return bytes(out)


def stream(count, *values):
    return enc(count * 2) + b''.join(enc(v) for v in values)


class PrimitiveTests(unittest.TestCase):
    def test_nine_ten_and_high_bit_combinations(self):
        nine = ref.primitive(b'\x80'*8+b'\x01', 0, 'x')
        self.assertEqual((nine['rawU64'],nine['nextCursor']), (1 << 56, 9))
        for tenth in (0, 1, 2, 127, 128, 129, 254, 255):
            got = ref.primitive(b'\x80'*9+bytes([tenth]), 0, 'x')
            self.assertEqual(got['rawU64'], (tenth & 1) << 63)
            self.assertEqual(got['nextCursor'], 0 if tenth & 128 else 10)
            self.assertEqual(got['originalAL'], 1)
        with self.assertRaises(ref.Boundary):
            ref.primitive(b'\x80'*9, 0, 'x')

    def test_non_shortest_terminates(self):
        read = ref.primitive(b'\x81\x00', 0, 'x')
        self.assertEqual((read['rawU64'], read['nextCursor']), (1, 2))


class HandlerTests(unittest.TestCase):
    def test_empty_and_truncated_headers(self):
        for raw in (b'', b'\x80'):
            r = ref.parse(raw, b'')
            self.assertEqual((r['driverStop'],r['boundary']['stage']), ('input_boundary','header'))
            self.assertIsNone(r['handlerAL'])

    def test_zero_negative_and_trailing(self):
        r = ref.parse(b'\x00\xaa', b'')
        self.assertEqual((r['count'],r['handlerAL'],r['cursor'],r['remainingBytes']), (0,1,1,1))
        r = ref.parse(b'\x01', b'')
        self.assertEqual((r['count'],r['handlerAL'],r['driverStop'],r['records']), (-1,1,'negative_count_unsupported',[]))

    def test_once_initial_and_keys_and_high_bits(self):
        r = ref.parse(stream(3, 0, (1 << 32)+7, 2, 0x80000000, (1 << 32)+3, 0xffffffff, 4), bytes(16))
        self.assertEqual([(x['key'],x['firstU32'],x['secondU32']) for x in r['records']], [(0,7,2),(0x80000000,9,3),(0xffffffff,12,4)])
        self.assertTrue(r['records'][0]['initialRead']['high32Nonzero'])
        self.assertTrue(r['records'][1]['secondRead']['high32Nonzero'])
        self.assertIsNone(r['records'][1]['initialRead'])
        self.assertTrue(ref.lookup(r['records'], 0)['hit'])
        self.assertFalse(ref.lookup(r['records'], 1)['hit'])
        r = ref.parse(stream(1, (1 << 32)+4, 0, 0), b'')
        self.assertEqual(r['records'][0]['key'], 4)
        self.assertTrue(r['records'][0]['keyRead']['high32Nonzero'])
        self.assertEqual(ref.lookup(r['records'],4)['pair'], [0,0])
        self.assertTrue(ref.lookup(r['records'],4)['hit'])

    def test_truncated_item_stages(self):
        for raw, stage in ((stream(1), 'key'),(stream(1,1),'initial'),(stream(1,1,2),'second')):
            r = ref.parse(raw,b'')
            self.assertEqual((r['driverStop'],r['boundary']['stage']),('input_boundary',stage))

    def test_wrap_duplicate_and_ranges(self):
        r=ref.parse(stream(2,1,0xffffffff,2,2,0), bytes(4))
        self.assertEqual([(x['firstU32'],x['secondU32']) for x in r['records']],[(0xffffffff,2),(1,0)])
        self.assertTrue(r['records'][0]['u32Wrap'])
        self.assertIsNone(r['records'][0]['candidateSpan'])
        self.assertEqual(r['records'][1]['candidateSpan'],[1,1])
        self.assertEqual(r['records'][1]['candidateSliceSHA256'],ref.sha(b''))
        r=ref.parse(stream(2,8,0,1,8,1),bytes(2))
        self.assertEqual((r['driverStop'],r['originalInsert'],len(r['records']),r['duplicateRecord']['index']),('duplicate_key','error_helper',1,1))
        self.assertIsNone(r['handlerAL'])

    def test_nonadvancing_is_driver_stop_with_original_al(self):
        for tenth in (128,129,255):
            r=ref.parse(b'\x80'*9+bytes([tenth]), b'')
            self.assertEqual((r['driverStop'],r['cursor'],r['nonadvancing']['read']['originalAL']),('nonadvancing_primitive_budget',0,1))
            self.assertEqual(r['nonadvancing']['read']['rawU64'], (tenth & 1) << 63)

    def test_existing_report_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'existing.json'
            original = b'original report\x00\xff'
            path.write_bytes(original)
            with patch.object(ref, 'make_report', return_value={}), patch('sys.argv', ['reference', 'run', str(path)]):
                with self.assertRaises(FileExistsError):
                    ref.main()
            self.assertEqual(path.read_bytes(), original)

    def test_independent_audit_rejects_record_or_span_tamper(self):
        report=ref.make_report()
        self.assertEqual(ref.audit(report)['recordsAudited'], report['count'])
        changed=copy.deepcopy(report)
        changed['records'][3]['secondRead']['rawHex']='00'
        with self.assertRaises(ValueError): ref.audit(changed)
        changed=copy.deepcopy(report)
        changed['records'][3]['candidateSliceSHA256']='0'*64
        with self.assertRaises(ValueError): ref.audit(changed)


if __name__ == '__main__':
    unittest.main()
