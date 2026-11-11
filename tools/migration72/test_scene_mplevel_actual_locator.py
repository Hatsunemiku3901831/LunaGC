import copy
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import scene_mplevel_actual_locator as subject
from tools.client72_resource_blob import parse_header
from tools.client72_resource_locator import reference_hash, reference_pack


class LocatorGateTest(unittest.TestCase):
    def test_pin_size_and_sha(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'source'
            path.write_bytes(b'abc')
            self.assertEqual(subject.check(path, 3, subject.digest(b'abc'))['bytes'], 3)
            for size, sha in [(4, subject.digest(b'abc')), (3, subject.digest(b'bad'))]:
                with self.assertRaisesRegex(ValueError, 'source drift'):
                    subject.check(path, size, sha)

    def test_literal_and_type_suffix_drift(self):
        literal = {'literal': {'cell':'0x145b51ee0','index':69656,'slot':69656,'usageRow':783116,'value':'Data/Common/MetaConfig'}}
        types = [None]*7 + [['MoleMole.Config.ConfigMetaConfig','.ScriptableObject']]
        subject.gate_literal(literal, types)
        for target in ('literal', 'type'):
            a, b = copy.deepcopy(literal), copy.deepcopy(types)
            if target == 'literal': a['literal']['value'] += 'x'
            else: b[7][1] = '.MiHoYoBinData'
            with self.assertRaises(ValueError): subject.gate_literal(a, b)

    def test_u64_reference_and_packing(self):
        self.assertEqual(reference_hash('Data/Common/MetaConfig','.ScriptableObject'), subject.KEY)
        packed = reference_pack(subject.KEY,33095)
        self.assertEqual((packed >> 32, packed & 0xffffffff), (0x642027d5,33095))
        self.assertNotEqual(reference_hash('Data/Common/MetaConfig','.MiHoYoBinData'), subject.KEY)

    def test_exact_match_and_variant_ambiguity(self):
        row = {'index':'31049740','offset':19917,'end':19931,'recordSHA256':'355f82df27c40f1988c634e0e58343fc61a4b6b729dc7c30252c3f17f8b7ecf1','variants':[(0,33095)]}
        subject.gate_match([row])
        for rows in ([],[row,row],[dict(row,variants=[(0,33095),(1,9)])],[dict(row,end=19932)]):
            with self.assertRaisesRegex(ValueError,'index match'): subject.gate_match(rows)

    def test_location_ambiguity_and_extent(self):
        row = (2455671,33095,308700,212203,0,6)
        subject.gate_location([row])
        for rows in ([],[row,row],[(2455671,33095,308701,212203,0,6)],[(2455671,33095,308700,212204,0,6)]):
            with self.assertRaisesRegex(ValueError,'location drift'): subject.gate_location(rows)

    def test_header_extent_codec_and_budget(self):
        info = {'logicalBlobBytes':212203,'fileTailBytes':0,'compressionTag':3,'blockCount':2,'uncompressedBytes':300000}
        subject.gate_header(info)
        for change in ({'logicalBlobBytes':284563},{'fileTailBytes':1},{'compressionTag':9},{'blockCount':33},{'uncompressedBytes':4194305}):
            with self.subTest(change=change), self.assertRaises(ValueError): subject.gate_header(dict(info,**change))

    def test_actual_decoded_header_rejected_by_bounded_parser(self):
        path = subject.OUT/'final1/decoded-header.bin'
        raw = path.read_bytes()
        self.assertEqual(subject.digest(raw),'207d6cdc9f26656a1a9ff05ad4b2087102fd826697045f1f40fffa38d4a5a741')
        with self.assertRaisesRegex(ValueError,'Blob or tail length is outside the actual file'):
            parse_header(raw,212203,117)


if __name__ == '__main__': unittest.main()
