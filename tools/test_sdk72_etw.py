"""Offline metadata and synthetic evidence guards; does not start an ETW session."""
import hashlib
import json
import os
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT/'local/migration72/d02-active/sdk-etw/Microsoft-Windows-WinHttp-Diagnostics-events.json'
MANIFEST = ROOT/'local/migration72/d02-active/sdk-etw/diagnostics-provider.xml'
LOG = ROOT/'local/migration72/d02-active/sdk-etw-observer/fixture-final2.log'
FIELDS = [('ProcessName','win:UnicodeString'),('ModuleName','win:UnicodeString'),('EventGuid','win:GUID'),
          ('HttpProtVersion','win:UInt32'),('Transport','win:UnicodeString'),('Host','win:AnsiString'),
          ('Path','win:AnsiString'),('HttpMethod','win:AnsiString'),('HttpStatus','win:UInt32'),
          ('FaultReason','win:UnicodeString'),('FaultCode','win:UnicodeString'),('ErrorCode','win:UInt32'),('ParseBuffer','win:UInt64')]

def declared_fields(row):
    if row['Id'] != 0 or row['Version'] not in (0,1): raise ValueError('unexpected event version')
    template = ET.fromstring(row['Template'])
    return [(node.get('name'),node.get('inType')) for node in template if node.tag.endswith('data')]

class SdkEtwTests(unittest.TestCase):
    def test_fixed_real_metadata_exact_versions_and_fields(self):
        self.assertEqual(hashlib.sha256(DATA.read_bytes()).hexdigest(),'deabcfa187e1e5b0640d33c2e538a7542949ebcfaccb1ecf618a83cf3713a4d6')
        self.assertEqual(hashlib.sha256(MANIFEST.read_bytes()).hexdigest(),'eb3acc4085688823bb1f683bcd604cedb3c195f8dfd700915974882c7dd1528c')
        self.assertEqual(ET.parse(MANIFEST).getroot().get('guid'),'64de121b-5f08-5853-ab48-7758f2ea2dd3')
        rows=json.loads(DATA.read_text(encoding='utf-8-sig'))
        self.assertEqual([(r['Id'],r['Version']) for r in rows],[(0,0),(0,1)])
        self.assertEqual(declared_fields(rows[0]),FIELDS)
        self.assertEqual(declared_fields(rows[1]),FIELDS[:2]+[('SessionGuid','win:GUID')]+FIELDS[2:])
        row=dict(rows[0],Version=2)
        with self.assertRaises(ValueError): declared_fields(row)

    def test_synthetic_result_filter_and_no_sentinel(self):
        path = Path(os.environ.get('SDK72_ETW_FIXTURE_LOG', str(LOG)))
        text=path.read_text(encoding='ascii')
        for value in ('start=0','enable_self=0','enable_child=0','enable_id_control=0','self=1 child_filtered=0 child_positive=1 filtered_id=0 id_positive=1 decode_errors=0','child_exit=0','record=host:1 method:1 route:1 status:207','stop=0','query_after_stop=4201'):
            self.assertIn(value,text)
        for forbidden in ('FAKE_SECRET','token=','HEADER_','sdk-static','query_region_list','private-secret'):
            self.assertNotIn(forbidden.lower(),text.lower())

if __name__ == '__main__': unittest.main()
