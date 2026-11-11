import copy
import hashlib
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import proud_gate_comparison_window as s

class Gates(unittest.TestCase):
    def test_hash_and_index_identity(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'p'; p.write_bytes(b'abc')
            h = hashlib.sha256(b'abc').hexdigest()
            self.assertEqual(s.checked(p, h, 3)['bytes'], 3)
            with self.assertRaises(ValueError): s.checked(p, h, 4)
            p.write_bytes(b'abd')
            with self.assertRaises(ValueError): s.checked(p, h, 3)
        # Exercise the actual source-gate byte equality decision without stock access.
        with patch.object(s, 'checked'), patch.object(Path, 'read_bytes', side_effect=[b'{"sources": {}}', b'a', b'b', b'a']):
            with self.assertRaises(ValueError): s.source_gate()

    def test_unique_declaration_and_exact_parameter(self):
        candidate = {'address': hex(s.SITE), 'hex':'488bb9b8120200', 'bytes':7, 'fileOffset':'0xcc52ce3', 'kind':'load-displacement-212b8', 'uniqueContainingRecord':True, 'containingRecords':[{'begin':hex(s.ENTRY),'endExclusive':hex(s.END),'recordHex':'90a44a0d40a84a0d28a04004','recordRVA':'0x1aa0a048','unwindRVA':'0x440a028'}], 'registeredOwners':[{'owner':26342,'ownerName':'FOJEDKOKIMI','method':204056,'methodName':'HFPAJKFPDBI'}]}
        index = {'candidates':[{} for _ in range(73)], 'totalCandidates':73}; index['candidates'][35] = candidate
        method = {'index':204056,'name':'HFPAJKFPDBI','registeredAddress':hex(s.ENTRY),'parameterCount':1,'flags':'0x96','virtualSlot':65535}
        records = [{'index':i,'methods':[]} for i in range(26343)]; records[26342].update(name='FOJEDKOKIMI',methods=[method])
        sig = {'methods':[{'index':204056,'declaringType':26342,'name':'HFPAJKFPDBI','registeredAddress':hex(s.ENTRY),'parameters':[{'index':169868,'name':'NMJLGFIPJII','typeIndex':53502}]}], 'typeReferences':{'53502':{'kind':'class','typeDefinition':18163,'byRef':False}}}
        s.verify_declaration(index, records, sig)
        records[0]['methods'].append(dict(method))
        with self.assertRaisesRegex(ValueError,'not unique'): s.verify_declaration(index,records,sig)
        records[0]['methods'].clear(); sig['typeReferences']['53502']['byRef'] = True
        with self.assertRaisesRegex(ValueError,'parameter type'): s.verify_declaration(index,records,sig)

    def test_aot(self):
        cell=bytes.fromhex('d0548a4201000000'); slot=bytes.fromhex('90a44a4d01000000')
        s.check_aot(cell,slot)
        for a,b in ((b'\0'*8,slot),(cell,b'\0'*8),(cell,b'')):
            with self.assertRaises(ValueError): s.check_aot(a,b)

    def test_pdata_uniqueness_overlap_and_budget(self):
        rel=s.ENTRY-s.BASE
        def row(b,e): return struct.pack('<III', b,e,1)
        raw=row(rel,rel+1024)
        self.assertEqual(s.select_pdata(raw,0)['exact'][0]['size'],1024)
        for table in (b'',raw+raw,row(rel-1,rel+1024),raw+row(rel-1,rel+1),raw+row(rel+1,rel+100),row(rel,rel+1025)):
            with self.assertRaises(ValueError): s.select_pdata(table,0)
        with self.assertRaises(ValueError): s.select_pdata(b'bad',0)

    def test_file_backed_and_executable(self):
        sec=[(100,10,20,0x20000000)]
        self.assertEqual(s.offset(sec,100,10,True),20)
        with self.assertRaises(ValueError): s.offset(sec,100,11,True)
        with self.assertRaises(ValueError): s.offset([(100,10,20,0)],100,1,True)
        with self.assertRaises(ValueError): s.offset(sec+sec,100,1,True)

    def test_decode_boundary_limit_gap_and_site(self):
        self.assertEqual(len(s.decode(b'\x90'*512,0,0,b'\x90')['lines']),512)
        with self.assertRaisesRegex(ValueError,'budget'): s.decode(b'\x90'*513,0,0,b'\x90')
        with self.assertRaisesRegex(ValueError,'whole instruction'): s.decode(bytes.fromhex('b801000000'),0,1,b'\x01')
        with self.assertRaisesRegex(ValueError,'whole instruction'): s.decode(b'\x90',0,0,b'\xcc')
        with self.assertRaisesRegex(ValueError,'incomplete'): s.decode(b'\x90\x0f',0,0,b'\x90')

    def test_failed_candidate_never_reads_body_and_preserves_diagnostics(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); client=root/'client'; client.mkdir(); (client/'YuanShen.exe').write_bytes(b'')
            dest=root/'out'; dest.mkdir()
            row={'recordRVA':'0x1aa0a048','recordHex':'90a44a0d40a84a0d28a04004','begin':hex(s.ENTRY),'endExclusive':hex(s.END),'unwindRVA':'0x440a028','size':944}
            diag={'exact':[row], 'containing':[row], 'siteContaining':[row]}
            reads=[]
            def fake_read(f, off, length):
                reads.append((off,length))
                if length==8 and off==0x1422f9950: return bytes.fromhex('d0548a4201000000')
                if length==8 and off==0x142a33d90: return bytes.fromhex('90a44a4d01000000')
                if length==14344080: return b'table'
                if length==7: return b'wrong!!'
                raise AssertionError('body read before candidate gate')
            def fake_offset(sections,address,length,executable=False):
                return {s.ENTRY:0xcc52c90,s.SITE:0xcc52ce3}.get(address,address)
            real_sha=s.sha
            def fake_sha(data):
                return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
            with patch.object(s,'source_gate',return_value={'fixedClientInputs':{}}), patch.object(s.subprocess,'check_output',return_value=str(client)), patch.object(s,'pe_info',return_value=((0x1a4e6a40,14344080),[])), patch.object(s,'read_at',side_effect=fake_read), patch.object(s,'offset',side_effect=fake_offset), patch.object(s,'select_pdata',return_value=diag), patch.object(s,'sha',side_effect=fake_sha):
                with self.assertRaisesRegex(ValueError,'candidate bytes differ'): s.collect(dest)
            self.assertNotIn((0xcc52c90,944),reads)
            self.assertEqual((dest/'candidate-site.bin').read_bytes(),b'wrong!!')
            self.assertTrue((dest/'boundary.private.json').exists())
            self.assertEqual(__import__('json').loads((dest/'failure.private.json').read_bytes())['stage'],'pdata-before-body')
            self.assertFalse((dest/'code.bin').exists())

    def test_short_read_and_create_new(self):
        import io
        with self.assertRaisesRegex(ValueError,'short read'): s.read_at(io.BytesIO(b'a'),0,2)
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x'; s.save(p,b'first')
            with self.assertRaises(FileExistsError): s.save(p,b'second')
            self.assertEqual(p.read_bytes(),b'first')

if __name__ == '__main__': unittest.main()
