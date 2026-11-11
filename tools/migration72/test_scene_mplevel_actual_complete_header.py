import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path

from tools.client72_resource_blob import parse_header
from tools.migration72 import scene_mplevel_actual_complete_header as subject


class CompleteHeaderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.req = json.loads((subject.ROOT/subject.REQUEST).read_bytes())
        cls.header = (subject.ROOT/cls.req['sources'][8]['path']).read_bytes()
        cls.prefix = (subject.ROOT/cls.req['sources'][7]['path']).read_bytes()

    def test_original_and_one_byte_short_still_rejected(self):
        for n in (212203,284562):
            with self.subTest(n=n), self.assertRaisesRegex(ValueError,'Blob or tail length is outside the actual file'):
                parse_header(self.header,n,117)
        parsed=parse_header(self.header,284563,117)
        subject.parsed_gate(parsed,self.req['headerExpected'])

    def test_prefix_mismatch_rejected(self):
        blob=self.prefix+bytes(72360)
        subject.prefix_gate(blob,self.prefix)
        for changed in (b'X'+blob[1:],blob[:-1]):
            with self.assertRaisesRegex(ValueError,'blob length or old prefix mismatch'):
                subject.prefix_gate(changed,self.prefix)

    def test_location_witness_drift_rejected(self):
        expected=self.req['exactNewInput']['sourceBasis']
        rows=[tuple(x) for x in expected['allSameBlockRows']]
        subject.location_gate(rows,expected)
        changed=copy.deepcopy(rows);changed[3]=(2455671,77054,593264,11782,0,6)
        with self.assertRaisesRegex(ValueError,'location witness'):
            subject.location_gate(changed,expected)

    def test_source_sha_or_size_drift(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'source';p.write_bytes(b'abc')
            subject.check(p,3,subject.digest(b'abc'))
            for n,sha in ((4,subject.digest(b'abc')),(3,subject.digest(b'bad'))):
                with self.assertRaisesRegex(ValueError,'source drift'):subject.check(p,n,sha)

    def test_table_ends_relative_pointer_and_content_bounds(self):
        for offset,fmt,value in ((60,'<I',284417),(32,'<q',10000),(68,'<I',334997)):
            changed=bytearray(self.header);struct.pack_into(fmt,changed,offset,value)
            with self.subTest(offset=offset), self.assertRaises(ValueError):parse_header(changed,284563,117)

    def test_tag9_observed_and_altered_tag_rejected(self):
        parsed=parse_header(self.header,284563,117)
        self.assertEqual(parsed['compressionTag'],9)
        with self.assertRaisesRegex(ValueError,'compressionTag'):
            subject.parsed_gate(dict(parsed,compressionTag=3),self.req['headerExpected'])


if __name__=='__main__':unittest.main()
