import copy
import hashlib
import io
import json
import pickle
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_mplevel_reader_factory_window as s

class Gates(unittest.TestCase):
    def test_source_pin_profile_and_restricted_unpickler(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'p';p.write_bytes(b'abc');h=hashlib.sha256(b'abc').hexdigest()
            self.assertEqual(s.checked(p,3,h)['bytes'],3)
            with self.assertRaises(ValueError):s.checked(p,4,h)
            p.write_bytes(b'abd')
            with self.assertRaises(ValueError):s.checked(p,3,h)
        with self.assertRaises(pickle.UnpicklingError):s.PlainUnpickler(io.BytesIO(pickle.dumps(Path('x')))).load()
        profile={'files':{'x':{'path':'a','size':3,'sha256':'abc'}}};stock={'a':{'bytes':3,'sha256':'abc'}}
        s.verify_profile(profile,stock);profile['files']['x']['size']=4
        with self.assertRaisesRegex(ValueError,'profile stock differs'):s.verify_profile(profile,stock)

    def test_unique_signature_and_three_parameter_types(self):
        records=[{'index':i,'methods':[]} for i in range(60258)]
        method={'index':500134,'name':'GBPNKANHNCH','registeredAddress':hex(s.ENTRY),'parameterCount':3,'flags':'0x96','virtualSlot':65535}
        records[60257].update(name='HALCDKCLBAO',methods=[method])
        sig={'methods':[{'index':500134,'declaringType':60257,'name':'GBPNKANHNCH','registeredAddress':hex(s.ENTRY),'parameters':[{'index':401957,'typeIndex':471},{'index':401958,'typeIndex':445811},{'index':401959,'typeIndex':445811}]}],'typeReferences':{'471':{'kind':'uint64','byRef':False},'445811':{'kind':'int32','byRef':False}}}
        s.verify_metadata(records,sig)
        records[0]['methods'].append(dict(method))
        with self.assertRaisesRegex(ValueError,'not unique'):s.verify_metadata(records,sig)
        records[0]['methods'].clear();sig['typeReferences']['445811']['kind']='uint32'
        with self.assertRaisesRegex(ValueError,'parameter types'):s.verify_metadata(records,sig)

    def test_caller_original_bytes_and_accepted_boundaries(self):
        wanted=bytes.fromhex('4c89f931d24531c0e8f6f5c502');original=bytearray(817);original[s.SETUP-s.CALLER:s.SETUP-s.CALLER+13]=wanted
        lines=['14aa99bfb mov r15, qword ptr [rbx]','14aa99c0d mov rcx, r15','14aa99c10 xor edx, edx','14aa99c12 xor r8d, r8d','14aa99c15 call 0x14d6f9210','14aa99c2e mov rdx, rax']
        chain={'frozenCallerEvidence':[{'entry':hex(s.CALLER),'end':'0x14aa99d81','sha256':'991c1e787c57e6268491098762c259c502ce01923796d47959bfa95ca7091569','asm':lines}]}
        s.verify_caller(wanted,original,chain)
        with self.assertRaisesRegex(ValueError,'caller bytes'):s.verify_caller(bytes(13),original,chain)
        missing=copy.deepcopy(chain);missing['frozenCallerEvidence'][0]['asm'].remove('14aa99c15 call 0x14d6f9210')
        with self.assertRaisesRegex(ValueError,'instruction boundary'):s.verify_caller(wanted,original,missing)
        shifted=bytearray(original);shifted[s.SETUP-s.CALLER:s.SETUP-s.CALLER+13]=bytes(13);shifted[0:13]=wanted
        with self.assertRaisesRegex(ValueError,'caller bytes'):s.verify_caller(wanted,shifted,chain)

    def test_actual_aot(self):
        cell=bytes.fromhex('d0548a4201000000');slot=bytes.fromhex('10926f4d01000000');s.check_aot(cell,slot)
        for a,b in ((bytes(8),slot),(cell,bytes(8)),(cell,b'')):
            with self.assertRaises(ValueError):s.check_aot(a,b)

    def test_pdata_missing_duplicates_overlap_and_executable(self):
        s.check_budget(8192)
        for size in (0,8193):
            with self.assertRaises(ValueError):s.check_budget(size)
        rel=s.ENTRY-s.BASE
        def row(b,e):return struct.pack('<III',b,e,0x500)
        raw=row(rel,rel+64);self.assertEqual(s.choose(s.pdata_diagnostic(raw,0))['size'],64)
        for table in (b'',raw+raw,row(rel-1,rel+64),raw+row(rel-1,rel+2),row(rel,rel)):
            with self.assertRaises(ValueError):s.choose(s.pdata_diagnostic(table,0))
        with self.assertRaises(ValueError):s.pdata_diagnostic(b'bad',0)
        sec=[(100,10,20,0x20000000)];self.assertEqual(s.offset(sec,100,10,True),20)
        for rows,length in ((sec,11),([(100,10,20,0)],1),(sec+sec,1)):
            with self.assertRaises(ValueError):s.offset(rows,100,length,True)
        with self.assertRaises(ValueError):s.read_at(io.BytesIO(b'a'),0,2)

    def test_decode_coverage_limit_returns_external_tails(self):
        self.assertEqual(len(s.decode(b'\x90'*2048,0)['lines']),2048)
        with self.assertRaisesRegex(ValueError,'budget'):s.decode(b'\x90'*2049,0)
        with self.assertRaisesRegex(ValueError,'incomplete'):s.decode(b'\x90\x0f',0)
        decoded=s.decode(bytes.fromhex('c3e900000000'),0)
        self.assertEqual(len(decoded['returns']),1);self.assertEqual(len(decoded['externalTails']),1)
        self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in decoded['lines']),bytes.fromhex('c3e900000000'))

    def test_oversize_or_nonpositive_preserves_prebody_without_body(self):
        for size in (0,8193):
            with self.subTest(size=size),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[]
                row={'recordRVA':'0x1','recordHex':'10926f0d0000000000000000','begin':hex(s.ENTRY),'endExclusive':hex(s.ENTRY+size),'unwindRVA':'0x0','size':size};diag={'exact':[row],'containing':[row]}
                def read(f,off,length):
                    reads.append((off,length))
                    if length==13:return bytes.fromhex('4c89f931d24531c0e8f6f5c502')
                    if off==0x1422f9950:return bytes.fromhex('d0548a4201000000')
                    if off==0x142c76200:return bytes.fromhex('10926f4d01000000')
                    if length==14344080:return b'table'
                    raise AssertionError('unexpected body read')
                real_sha=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
                with patch.object(s,'source_gate',return_value=({'stock':{}},b'',{})),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s,'pe_info',return_value=((0x1a4e6a40,14344080),[],{})),patch.object(s,'read_at',side_effect=read),patch.object(s,'offset',side_effect=lambda sections,address,length,executable=False:address),patch.object(s,'verify_caller'),patch.object(s,'pdata_diagnostic',return_value=diag),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaisesRegex(ValueError,'body budget'):s.collect(dest)
                self.assertTrue((dest/'boundary.private.json').exists());self.assertTrue((dest/'pre-body.private.json').exists());self.assertTrue((dest/'pdata.bin').exists())
                self.assertFalse((dest/'body.bin').exists());self.assertEqual(json.loads((dest/'failure.private.json').read_bytes())['stage'],'pdata-before-body')

    def test_create_new(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'o';s.save(p,b'first')
            with self.assertRaises(FileExistsError):s.save(p,b'second')
            self.assertEqual(p.read_bytes(),b'first')

if __name__=='__main__':unittest.main()
