import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools import client72_d02_proud_index_literals as subject

BASE=0x145ac9e20

def encoded(row,index,slot):
    key=(row*0x57c15286+0x26ddebda)&0xffffffff
    source=(5<<29)|index
    first=((source^key^0x2a357d90)-0xabbdcabf)&0xffffffff
    return struct.pack('<II',first,slot^key^0x1855aa65)

class LiteralTests(unittest.TestCase):
    def test_separate_source_and_destination(self):
        rows=encoded(7,18,48935)+encoded(8,19,48936)
        hits=subject.scan(rows,7,BASE,87252)
        self.assertEqual([(x['sourceIndex'],x['destinationSlot']) for x in hits],[(18,48935),(19,48936)])
    def test_duplicate_or_missing_rejected(self):
        rows=encoded(7,18,48935)+encoded(8,19,48935)+encoded(9,20,48936)
        with self.assertRaisesRegex(ValueError,'missing or duplicate'): subject.scan(rows,7,BASE,87252)
        with self.assertRaisesRegex(ValueError,'missing or duplicate'): subject.scan(encoded(7,18,48935),7,BASE,87252)
    def test_registration_budget(self):
        with self.assertRaisesRegex(ValueError,'budget'): subject.scan(bytes(800008),0,BASE,87252)
    def test_destination_out_of_bounds(self):
        with self.assertRaisesRegex(ValueError,'out of bounds'): subject.scan(encoded(7,1,87252),7,BASE,87252)
    def test_group_boundary_input(self):
        with self.assertRaisesRegex(ValueError,'length'): subject.boundaries(b'1234',314557)
    def test_offline_audit_rejects_cipher_drift(self):
        original=subject.OUT/'final1.private.json'
        group=subject.OUT/'final1.private.group.bin'
        with tempfile.TemporaryDirectory(dir=subject.OUT) as d:
            root=Path(d); report=json.loads(original.read_bytes()); report['selected'][0]['cipherHex']='00'+report['selected'][0]['cipherHex'][2:]
            for n in ('one','two'):
                (root/(n+'.json')).write_text(json.dumps(report),encoding='utf8')
                (root/(n+'.group.bin')).write_bytes(group.read_bytes())
            with patch.object(subject,'output_path',side_effect=lambda name,suffix:root/name):
                with self.assertRaisesRegex(ValueError,'literal decode mismatch'): subject.audit('one.json','two.json','out.json')

if __name__=='__main__': unittest.main()
