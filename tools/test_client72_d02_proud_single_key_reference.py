import json
import math
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools import client72_d02_proud_single_key_reference as m
from tools import client72_d02_proud_base_prefix_reference as b


def word(v):
    return v.to_bytes(4, 'little')


def header(v):
    return (v ^ 0x3f87).to_bytes(2, 'little')


class SingleKeyTests(unittest.TestCase):
    def test_four_presence_combinations_and_setter_order(self):
        for mask, wire, length, expected in [
            (0, header(0), 2, [0,0]),
            (4, header(4)+word(0xffffffff), 6, [0x2061053b,0]),
            (0x80, header(0x80)+word(0xffffffff), 6, [0,0x27b50832]),
            (0x84, header(0x84)+word(0xffffffff)+word(0), 10, [0x2061053b,0x27b50833]),
        ]:
            with self.subTest(mask=mask):
                r=m.Reader(wire,0,len(wire)); m.element(r,3)
                self.assertEqual(r.cursor,length)
                members=r.tokens[1:]
                self.assertEqual([t['fieldIndex'] for t in members],[81538,81537])
                self.assertEqual([int(t['conditionalSetterCopy']['inputBits'],16) for t in members],expected)
                self.assertEqual([t['conditionalSetterCopy']['copyPC'] for t in members],['147e91878','147e918d5'])
                for t in members:
                    self.assertIsNone(t['conditionalSetterCopy']['actualStored'])
                    self.assertFalse(t['conditionalSetterCopy']['nativeExecuted'])
                    if not t['present']:
                        self.assertIsNone(t['rawHex']); self.assertIsNone(t['span'])
                        self.assertEqual(t['width'],0); self.assertEqual(t['cursorBefore'],t['cursorAfter'])

    def test_partial_and_prior_copy(self):
        for data, cursor, count in [(b'\x87',0,1), (header(4)+b'\x01\x02\x03',2,2),
                                     (header(0x84)+word(1)+b'\x01\x02\x03',6,3),
                                     (b'',0,1)]:
            r=m.Reader(data,0,334)
            with self.assertRaises(b.FileStop): m.element(r,0)
            self.assertEqual(r.cursor,cursor); self.assertEqual(len(r.tokens),count)
            self.assertEqual(r.tokens[-1]['cursorBefore'],r.tokens[-1]['cursorAfter'])
        r=m.Reader(header(0x84)+word(1)+b'xyz',0,334)
        with self.assertRaises(b.FileStop): m.element(r,0)
        self.assertEqual(r.tokens[1]['conditionalSetterCopy']['inputBits'],'2061053d')
        self.assertIsNone(r.tokens[1]['conditionalSetterCopy']['actualStored'])

    def test_candidate_crossing_does_not_stop(self):
        r=m.Reader(header(4)+word(7),0,2); m.element(r,0)
        self.assertEqual(r.cursor,6); self.assertTrue(r.tokens[1]['candidateBoundaryCrossed'])

    def test_numeric_start_signed_and_low32(self):
        for cursor, start, signed, next_ in [(0x7fffffff,True,0x7fffffff,0x80000001),
                (0x80000000,True,-0x80000000,0x80000002),(0xffffffff,True,-1,1)]:
            d=m.bounds(cursor,2,0x100000000,334)
            self.assertEqual((d['originalStartCheck'],d['movsxdAddressOffset'],d['cursorAdvanceLow32']),(start,signed,next_))
            if signed < 0: self.assertFalse(d['physicalFullWidthCheck'])
        self.assertFalse(m.bounds(5,2,5,334)['originalStartCheck'])

    def test_base_float_and_le64_precision(self):
        for bits in [0x7fc00001,0x7f800000,0x80000000]:
            self.assertEqual(b.float_input(bits)['floatInputBits'],f'{bits:08x}')
        # Prefix block: 9 bytes, separate zero-padded LE64 groups, no float/list.
        data=(0x6c26).to_bytes(2,'little')+(0x791c).to_bytes(2,'little')+bytes.fromhex('ffffffffffffffff01')
        result=b.parse(data)
        block=next(t for t in result['tokens'] if t['phase']=='base-block')
        self.assertEqual(block['transformedHex'],'1479e39e268e169a16')

    def test_fixed_sample_full_contiguous_spans(self):
        data=m.preflight(); result=m.parse(data)
        self.assertTrue(result['completeConditionalSingleKeyFileSyntax'])
        self.assertEqual(result['stoppedCursor'],334); self.assertEqual(result['consumedBytes'],330)
        spans=[t['span'] for t in result['tokens'] if t['span'] is not None]
        self.assertEqual(len(spans),81)
        self.assertEqual(spans[0][0],4); self.assertEqual(spans[-1][1],334)
        self.assertTrue(all(a[1]==c[0] for a,c in zip(spans,spans[1:])))
        tail=next(t for t in result['tokens'] if t['phase']=='nativeData+42')
        self.assertEqual((tail['rawHex'],tail['wideSum'],tail['resultBits']),('cefc0c44','100000002','00000002'))
        headers = [t for t in result['tokens'] if t['phase'] == 'element-header']
        self.assertEqual([(t['width'], t['transformWidthBits'], t['headerSumU32'], t['resultBits'])
                          for t in headers], [(2, 32, 65536, '00010000')] * 2)
        for i in range(4):
            h=next(t for t in result['tokens'] if t['phase']=='17208-element-header' and t['elementIndex']==i)
            self.assertEqual((h['span'],h['rawHex']),([322+i*2,324+i*2],'873f'))
        self.assertFalse(result['completeProudReference']); self.assertFalse(result['actualCursorObserved'])

    def test_source_drift_and_exclusive_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'source'; source.write_bytes(b'bad')
            with patch.object(m,'PINS',{source:(3,'0'*64)}):
                with self.assertRaisesRegex(ValueError,'source drift'): m.preflight()
            out=Path(tmp)/'owned'; out.write_text('keep')
            with patch.object(m,'DIR',Path(tmp)):
                with self.assertRaises(FileExistsError): m.main(['--output','owned'])
            self.assertEqual(out.read_text(),'keep')

    def test_unapproved_derived_alternative_partial(self):
        data=bytearray(m.preflight()); data[142] ^= 1
        result=m.parse(bytes(data))
        self.assertEqual(result['parseStatus'],'partial')
        self.assertIn('unapproved-derived-alternative',result['stopReason'])
        self.assertFalse(result['completeConditionalSingleKeyFileSyntax'])


if __name__=='__main__': unittest.main()
