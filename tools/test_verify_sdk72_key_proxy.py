"""Independent PE thunk/data fixtures and strict public-result boundaries."""
import copy
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest

from tools import verify_sdk72_key_proxy as key


NAMES=[f'OwnedExport{index:02}' for index in range(47)]


def fixture_pe(inner=False,inner_name=key.CORE_EXPORT,writable=True,executable=False,base=0x180000000):
    """Non-executable owned PE bytes; exports, code, table and state are separate."""
    data=bytearray(0x3800);data[:2]=b'MZ';struct.pack_into('<I',data,60,0x80)
    data[0x80:0x84]=b'PE\0\0';struct.pack_into('<HH',data,0x84,0x8664,3)
    struct.pack_into('<H',data,0x94,0xf0);optional=0x98
    struct.pack_into('<H',data,optional,0x20b);struct.pack_into('<Q',data,optional+24,base)
    struct.pack_into('<II',data,optional+56,0x7000,0x400)
    struct.pack_into('<II',data,optional+112,0x3000,0x2000)
    headers=optional+0xf0
    for index,values in enumerate([
        (b'.text\0\0\0',0x1000,0x1000,0x1000,0x400,0x60000020),
        (b'.edata\0\0',0x2000,0x3000,0x2000,0x1400,0x40000040),
        (b'.data\0\0\0',0x400,0x6000,0x400,0x3400,0x40000040|(0x80000000 if writable else 0)|(0x20000000 if executable else 0))]):
        name,virtual,start,size,raw,flags=values
        struct.pack_into('<8sIIIIIIHHI',data,headers+index*40,name,virtual,start,size,raw,0,0,0,0,flags)
    names=NAMES+[key.STATE_EXPORT]+([inner_name] if inner else [])
    count=len(names);eat=0x3040;pointers=eat+count*4;ordinals=pointers+count*4;cursor=ordinals+count*2
    for index,name in enumerate(names):
        rva=0x1000+index*24 if index<47 else 0x6000 if index==47 else 0x1800
        struct.pack_into('<I',data,0x1400+eat-0x3000+index*4,rva)
        struct.pack_into('<I',data,0x1400+pointers-0x3000+index*4,cursor)
        struct.pack_into('<H',data,0x1400+ordinals-0x3000+index*2,index)
        encoded=name.encode()+b'\0';data[0x1400+cursor-0x3000:0x1400+cursor-0x3000+len(encoded)]=encoded;cursor+=len(encoded)
        if index<47:
            slot=0x6100+index*8;resolver=0x1900
            code=b'\xff\x25'+struct.pack('<i',slot-(rva+6))+bytes((0x6a,index,0xe8))+struct.pack('<i',resolver-(rva+13))
            code+=b'\x48\x8d\x64\x24\x08\xff\x25'+struct.pack('<i',slot-(rva+24))
            data[0x400+rva-0x1000:0x400+rva-0x1000+24]=code
            struct.pack_into('<Q',data,0x3500+index*8,base+rva+6)
    struct.pack_into('<IIHHIIIIIII',data,0x1400,0,0,0,0,0,1,count,count,eat,pointers,ordinals)
    struct.pack_into('<II',data,0x3400,1,64)
    return bytes(data)


class ArtifactTests(unittest.TestCase):
    def test_fixed_data_and_all47_hot_cold_thunks_are_accepted(self):
        result=key.require_artifact(fixture_pe(),NAMES)
        self.assertEqual((result['stateBytes'],result['stateSchema'],result['frozenThunkCount']),(64,1,47))
        self.assertEqual((result['initialTargetTableRva'],result['coldResolverRva']),(0x6100,0x1900))
        self.assertFalse(result['frozenCoreInnerExplicitlyAllowed'])

    def test_core_internal_symbol_requires_explicit_exact_permission(self):
        with self.assertRaises(ValueError):key.require_artifact(fixture_pe(inner=True),NAMES)
        self.assertTrue(key.require_artifact(fixture_pe(inner=True),NAMES,True)['frozenCoreInnerExplicitlyAllowed'])
        with self.assertRaises(ValueError):key.require_artifact(fixture_pe(inner=True,inner_name='UnknownCoreExport'),NAMES,True)
        with self.assertRaises(ValueError):key.require_artifact(fixture_pe(),NAMES,True)

    def test_public_initial_state_rejects_schema_installed_or_high64_binding_data(self):
        for offset,width,value in ((0,4,2),(4,4,65),(20,4,1),(24,8,0x8000000100000001),(60,4,1)):
            raw=bytearray(fixture_pe());raw[0x3400+offset:0x3400+offset+width]=value.to_bytes(width,'little')
            with self.subTest(offset=offset),self.assertRaisesRegex(ValueError,'schema/initial state'):
                key.require_artifact(raw,NAMES)

    def test_state_must_be_writable_data_and_cannot_be_executable(self):
        for writable,executable in ((False,False),(True,True)):
            with self.subTest(writable=writable,executable=executable),self.assertRaisesRegex(ValueError,'non-executable data'):
                key.require_artifact(fixture_pe(writable=writable,executable=executable),NAMES)

    def test_forwarded_or_misnumbered_exports_are_rejected(self):
        raw=bytearray(fixture_pe());struct.pack_into('<I',raw,0x1440,0x3800)
        raw[0x1c00:0x1c00+18]=b'OwnedLibrary.Call\0'
        with self.assertRaises(ValueError):key.require_artifact(raw,NAMES)
        raw=bytearray(fixture_pe());struct.pack_into('<I',raw,0x1400+16,2)
        with self.assertRaises(ValueError):key.require_artifact(raw,NAMES)

    def test_cold_index_slot_and_shared_resolver_drift_are_rejected(self):
        for offset in (0x400+7,0x400+9,0x400+20,0x3500):
            raw=bytearray(fixture_pe());raw[offset]^=1
            with self.subTest(offset=offset),self.assertRaises(ValueError):key.require_artifact(raw,NAMES)

    def test_initial_pointers_are_full64_not_truncated(self):
        raw=fixture_pe(base=0xfedcba9800000000)
        self.assertEqual(key.require_artifact(raw,NAMES)['initialTargetTableRva'],0x6100)
        bad=bytearray(raw);struct.pack_into('<Q',bad,0x3500,0x1006)
        with self.assertRaisesRegex(ValueError,'pointer/slot'):key.require_artifact(bad,NAMES)

    def test_link_inputs_separate_data_and_exact_core_export(self):
        normal=key.def_text(NAMES);explicit=key.def_text(NAMES,True)
        self.assertIn('OwnedExport00 @1',normal);self.assertIn('OwnedExport46 @47',normal)
        self.assertIn('Sdk72KeyProxyState @48 DATA',normal)
        self.assertNotIn('sdk72_key_input_inner',normal)
        self.assertIn('sdk72_key_input_inner @49',explicit)


class PublicResultTests(unittest.TestCase):
    def completed(self,payload=None,code=0,stderr=b''):
        return subprocess.CompletedProcess([],code,json.dumps(key.EXPECTED_OWNED if payload is None else payload).encode(),stderr)

    def test_exact_public_payload_requires_actual_zero_exit_and_empty_stderr(self):
        self.assertTrue(key.checked_owned(self.completed())['passed'])
        for completed in (self.completed(code=1),self.completed(code=0xc0000005),self.completed(stderr=b'unexpected')):
            with self.subTest(code=completed.returncode):self.assertFalse(key.checked_owned(completed)['passed'])

    def test_unrecognized_fields_and_stderr_are_not_copied_or_hashed(self):
        payload=copy.deepcopy(key.EXPECTED_OWNED);payload['body']='private_marker_omit'
        for completed in (self.completed(payload),self.completed(stderr=b'private_marker_omit'),
                          subprocess.CompletedProcess([],1,b'private_marker_omit',b'private_marker_omit')):
            result=key.checked_owned(completed)
            self.assertFalse(result['passed']);self.assertNotIn('publicResult',result)
            self.assertNotIn('private_marker_omit',json.dumps(result));self.assertNotIn('sha256',result)

    def test_boolean_integer_alias_and_missing_guards_are_rejected(self):
        for field,value in (('loaded_binding_positive',1),('production_forwarding_calls',True),
                            ('actual_ilhook_error_no_target_write',False)):
            payload=copy.deepcopy(key.EXPECTED_OWNED);payload[field]=value
            with self.subTest(field=field):self.assertFalse(key.checked_owned(self.completed(payload))['passed'])

    def test_output_confines_to_the_new_task_and_json_bytes_are_lf(self):
        with self.assertRaises(ValueError):key.confined(key.ROOT/'local/migration72/d02-resume-20261108/r1-fresh-ancestor/unused.json')
        key.RAW.mkdir(parents=True,exist_ok=True)
        fd,name=tempfile.mkstemp(dir=key.RAW,suffix='.owned-test.json');os.close(fd)
        path=Path(name)
        try:
            self.assertTrue(path.resolve().is_relative_to(key.RAW.resolve()))
            key.write_json(path,{'schema':1,'values':[1,2]})
            expected=(json.dumps({'schema':1,'values':[1,2]},indent=2)+'\n').encode()
            self.assertEqual(path.read_bytes(),expected);self.assertNotIn(b'\r\n',path.read_bytes())
        finally:path.unlink()


if __name__=='__main__':unittest.main()
