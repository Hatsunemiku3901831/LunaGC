import copy
import json
import struct
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import scene_mplevel_actual_configmeta_serialized as subject


def aligned_string(value):
    raw=value.encode()
    out=struct.pack('<i',len(raw))+raw
    return out+bytes((-len(out))&3)


def fixture(engine=subject.ENGINE):
    negative, high = -1, 0x1ffffffff
    prop = bytes.fromhex('00112233445566778899aabbccddeeff')
    script = aligned_string('ConfigMetaConfig')+struct.pack('<i',0)+prop+aligned_string('ConfigMetaConfig')+aligned_string('MoleMole.Config')+aligned_string('Assembly-CSharp.dll')+b'\0'
    instance = struct.pack('<iqB',0,0,1)+bytes(3)+struct.pack('<iq',0,negative)+aligned_string('MetaConfig')+b'fixture'
    r=bytearray(b'\0'*20)
    r+=engine.encode()+b'\0'+struct.pack('<iB',19,0)+struct.pack('<i',2)
    r+=struct.pack('<iBh',115,0,-1)+bytes(16)
    r+=struct.pack('<iBh',114,0,0)+bytes.fromhex('11223344556677889900aabbccddeeff')+prop
    r+=struct.pack('<i',2)
    for pid,start,size,typ in [(negative,0,len(script),0),(high,128,len(instance),1)]:
        r+=bytes((-len(r))&3)+struct.pack('<qIIi',pid,start,size,typ)
    r+=struct.pack('<ii',1,0)+bytes((-len(r)-8)&3)+struct.pack('<q',negative)
    r+=struct.pack('<ii',0,0)[:4]+b'\0'
    meta=len(r)-20; data=512
    r+=bytes(data-len(r))+script+bytes(128-len(script))+instance
    struct.pack_into('>4I',r,0,meta,len(r),17,data)
    target=[{'objectHex':script.hex(),'objectSHA256':subject.sha(script),'script':subject.mono_script(script)}]
    return bytes(r),target


class DirectoryTests(unittest.TestCase):
    def setUp(self):
        self.raw,self.target=fixture()
        self.directory=subject.parse_directory(self.raw)

    def test_engine_exact_and_full_64_bit(self):
        self.assertEqual(self.directory['engine'],subject.ENGINE)
        self.assertEqual([x['pathID'] for x in self.directory['objects']],[-1,0x1ffffffff])
        self.assertEqual((-1)&0xffffffff,0x1ffffffff&0xffffffff)
        without,_=fixture('2017.4.30f1')
        from tools.migration72.client72_scene_mplevel_actual_assets import parse_directory as old_parse
        self.assertEqual(old_parse(without)['engine'], '2017.4.30f1')
        with self.assertRaisesRegex(ValueError,'engine/platform/tree'):subject.parse_directory(without)
        with self.assertRaisesRegex(ValueError,'Unreviewed Unity'):old_parse(self.raw)

    def test_class114_hash_cursor_and_identity(self):
        typ=self.directory['types'][1]
        self.assertEqual(typ['typeHashOffset']-typ['scriptHashOffset'],16)
        self.assertEqual(typ['end']-typ['offset'],39)
        result=subject.identity(self.raw,self.directory,self.target)
        self.assertEqual(result['prefix']['cursor'],44)
        self.assertEqual(result['unparsedScriptBytes'],7)
        self.assertEqual(result['scriptPointer']['pathID'],-1)

    def test_bad_script_index_reference_and_pptr(self):
        typ=self.directory['types'][1]
        for offset,fmt,value in [(typ['offset']+5,'<h',5),(self.directory['scripts'][0]['pathIDOffset'],'<q',0x1ffffffff)]:
            raw=bytearray(self.raw);struct.pack_into(fmt,raw,offset,value)
            with self.assertRaises(ValueError):subject.parse_directory(raw)
        wrong=bytearray(self.raw);struct.pack_into('<q',wrong,self.directory['objects'][1]['start']+20,2)
        with self.assertRaisesRegex(ValueError,'prefix mismatch'):subject.identity(wrong,self.directory,self.target)
        wrong=copy.deepcopy(self.target);wrong[0]['script']['className']='Other'
        with self.assertRaisesRegex(ValueError,'tuple mismatch'):subject.identity(self.raw,self.directory,wrong)

    def test_type_properties_conflict(self):
        wrong=copy.deepcopy(self.directory);wrong['types'][1]['typeHash']='00'*16
        with self.assertRaisesRegex(ValueError,'type/properties'):subject.identity(self.raw,wrong,self.target)

    def test_metadata_truncation_and_remaining_bytes(self):
        raw=bytearray(self.raw);struct.pack_into('>I',raw,0,self.directory['metadataBytes']+1)
        with self.assertRaisesRegex(ValueError,'unconsumed metadata'):subject.parse_directory(raw)
        raw=bytearray(self.raw);struct.pack_into('>I',raw,0,self.directory['metadataBytes']-1)
        with self.assertRaises(ValueError):subject.parse_directory(raw)
        with self.assertRaises(ValueError):subject.parse_directory(self.raw[:-1])

    def test_duplicate_overlap_outside(self):
        first,second=self.directory['objects']
        for offset,fmt,value in [(second['recordOffset'],'<q',first['pathID']),
                                 (second['recordOffset']+8,'<I',0),
                                 (second['recordOffset']+8,'<I',99999)]:
            raw=bytearray(self.raw);struct.pack_into(fmt,raw,offset,value)
            with self.assertRaises(ValueError):subject.parse_directory(raw)

    def test_sha_drift(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'sample';p.write_bytes(b'abc')
            self.assertEqual(subject.checked(p,3,subject.sha(b'abc')),b'abc')
            with self.assertRaisesRegex(ValueError,'pin mismatch'):subject.checked(p,3,subject.sha(b'abd'))


if __name__ == '__main__':unittest.main()
