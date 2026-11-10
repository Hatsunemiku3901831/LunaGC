import hashlib
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools import client72_d02_proud_primary_index_locator as s
from tools import client72_d02_proud_payload_locator as old
from tools import client72_resource_payload_index as index


class Gates(unittest.TestCase):
    def test_drift(self):
        with tempfile.TemporaryDirectory(dir=s.PRIVATE) as tmp:
            p=Path(tmp)/'source'; p.write_bytes(b'bad')
            with patch.object(s,'ROOT',Path(tmp)):
                with self.assertRaisesRegex(ValueError,'source drift'): s.checked('source',hashlib.sha256(b'good').hexdigest())

    def test_scan_all_variants_and_section_end(self):
        data=struct.pack('<iQB Bi Bi',1,0x1234,2,0,7,1,8)
        section=dict(start=0,end=len(data),count=1,sha256=s.sha(data),bytes=len(data))
        with patch.dict(s.SECTIONS,{'fixture':(0,len(data),1,s.sha(data))}):
            hits=s.scan_assets(data,'fixture',section,0x1234,index.Reader,index.asset_record)
            self.assertEqual(hits[0]['variants'],[(0,7),(1,8)])
            with self.assertRaisesRegex(ValueError,'selector independence'): old.unique_record(hits)
            self.assertEqual(s.scan_assets(data,'fixture',section,0x1235,index.Reader,index.asset_record),[])
        with patch.dict(s.SECTIONS,{'fixture':(0,len(data)-1,1,s.sha(data[:-1]))}):
            bad=dict(start=0,end=len(data)-1,count=1,sha256=s.sha(data[:-1]),bytes=len(data)-1)
            with self.assertRaisesRegex(ValueError,'crosses'): s.scan_assets(data,'fixture',bad,0x1234,index.Reader,index.asset_record)

    def test_record_selector_and_location_ambiguity(self):
        row={'variants':[(0,7)]}
        self.assertEqual(old.unique_record([row]),row)
        for rows in ([],[row,row],[{'variants':[(0,0)]}],[{'variants':[(1,7)]}],[{'variants':[(0,7),(0,8)]}]):
            with self.assertRaises(ValueError): old.unique_record(rows)
        record=struct.pack('<6i',3,7,0,0,0,1)
        self.assertEqual(len(s.locations(record+record,7)),2)
        self.assertEqual(s.locations(record,8),[])
        with self.assertRaisesRegex(ValueError,'extent'): s.locations(b'bad',7)

    def test_entry_bounds_and_identity(self):
        name=b'12345678'; payload=b'abc'
        table=struct.pack('<III',0,1,len(name))+name+struct.pack('<iQQ',0,2,3)
        data=table+struct.pack('<I',3)+payload
        rec=dict(name=name.decode(),fileID=0,pathID=2,fileSize=3)
        a=dict(pathID=1,offset=0,size=len(table),sha256=s.sha(table),assetIndex=dict(records=[rec]))
        b=dict(pathID=2,offset=len(table),size=7,classID=1208,sha256=s.sha(data[len(table):]),lengthPrefixedBytes=dict(bytes=3,sha256=s.sha(payload)))
        parsed=dict(dataOffset=0,objects=[a,b])
        self.assertEqual(old.extract_entry(data,parsed,name.decode())[0],payload)
        rec['fileID']=1
        with self.assertRaisesRegex(ValueError,'unique local'): old.extract_entry(data,parsed,name.decode())
        rec['fileID']=0; rec['fileSize']=100; b['lengthPrefixedBytes']['bytes']=100
        with self.assertRaisesRegex(ValueError,'extent or size'): old.extract_entry(data,parsed,name.decode())

    def test_existing_output_refused(self):
        with tempfile.TemporaryDirectory(dir=s.PRIVATE) as tmp:
            marker=Path(tmp)/'keep'; marker.write_bytes(b'unchanged')
            result=subprocess.run([sys.executable,'-B',str(Path(s.__file__)),'--output',Path(tmp).name],capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0); self.assertEqual(marker.read_bytes(),b'unchanged')


if __name__=='__main__': unittest.main()
