import hashlib
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from tools import client72_d02_proud_payload_locator as s


class Gates(unittest.TestCase):
    def test_source_drift(self):
        with self.assertRaisesRegex(ValueError, 'source drift'):
            s.verify(b'wrong', hashlib.sha256(b'right').hexdigest(), 'fixture')

    def test_unique_and_multiple_records_and_selector(self):
        row = {'variants': [(0,7)]}
        self.assertIs(s.unique_record([row]), row)
        for rows in ([], [row,row]):
            with self.assertRaisesRegex(ValueError, 'record count'):
                s.unique_record(rows)
        with self.assertRaisesRegex(ValueError, 'selector independence'):
            s.unique_record([{'variants': [(0,7),(1,8)]}])

    def test_locations_and_bounds(self):
        row = struct.pack('<6i', 3,7,0,0,0,1)
        self.assertEqual(s.one_location(row,7)['offset'],0)
        for bad in (row+row, b'\0'):
            with self.assertRaises(ValueError):
                s.one_location(bad,7)
        with self.assertRaisesRegex(ValueError, 'record count'):
            s.one_location(row,8)

    def test_extract_and_bad_extent(self):
        name=b'12345678'; payload=b'abc'
        index=struct.pack('<I',0)+struct.pack('<I',1)+struct.pack('<I',len(name))+name+struct.pack('<iQQ',0,2,len(payload))
        data=index+struct.pack('<I',len(payload))+payload
        record=dict(name=name.decode(),fileID=0,pathID=2,fileSize=3)
        obj1=dict(pathID=1,offset=0,size=len(index),sha256=s.sha(index),assetIndex=dict(records=[record]))
        obj2=dict(pathID=2,offset=len(index),size=7,classID=1208,sha256=s.sha(data[len(index):]),lengthPrefixedBytes=dict(bytes=3,sha256=s.sha(payload)))
        parsed=dict(dataOffset=0,objects=[obj1,obj2])
        raw, info=s.extract_entry(data,parsed,name.decode())
        self.assertEqual(raw,payload)
        self.assertEqual(info['indexRecordSpan']['span'],[8,len(index)])
        obj2['lengthPrefixedBytes']['bytes']=100
        record['fileSize']=100
        with self.assertRaisesRegex(ValueError,'extent or size'):
            s.extract_entry(data,parsed,name.decode())

    def test_existing_output_refused(self):
        s.PRIVATE.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=s.PRIVATE) as tmp:
            marker=Path(tmp)/'keep';marker.write_bytes(b'unchanged')
            result=subprocess.run([sys.executable,str(s.ROOT/'tools/client72_d02_proud_payload_locator.py'),
                                   '--output',Path(tmp).name],capture_output=True,text=True)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('new task-private basename',result.stderr)
            self.assertEqual(marker.read_bytes(),b'unchanged')


if __name__ == '__main__':
    unittest.main()
