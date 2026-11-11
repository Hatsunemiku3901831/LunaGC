import copy
import io
import json
import pickle
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_reader_backend_identifier_window as s

class Gates(unittest.TestCase):
    def test_pin_profile_and_no_globals(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x';p.write_bytes(b'abc');h=s.sha(b'abc')
            s.checked(p,3,h)
            for size,digest in ((4,h),(3,s.sha(b'abd'))):
                with self.assertRaises(ValueError):s.checked(p,size,digest)
        with self.assertRaises(pickle.UnpicklingError):s.base.PlainUnpickler(io.BytesIO(pickle.dumps(Path('x')))).load()
        profile={'files':{'x':{'path':'a','size':3,'sha256':'abc'}}};stock={'a':{'bytes':3,'sha256':'abc'}}
        s.base.verify_profile(profile,stock);profile['files']['x']['size']=4
        with self.assertRaises(ValueError):s.base.verify_profile(profile,stock)

    def test_declaration_and_parameter_gates(self):
        records=[{'index':i,'methods':[]} for i in range(11133)]
        method={'index':78586,'name':'LoadBinDataZipFile','registeredAddress':hex(s.ENTRY),'parameterCount':5,'flags':'0x86','virtualSlot':65535}
        records[11132].update(name='AssetBundleExternalResourceProvider',namespace='MoleMole',methods=[method])
        params=[{'index':i,'name':n,'typeIndex':t} for i,n,t in zip(range(83514,83519),['request','offset','length','compressionType','pageSize'],[27066,70,70,469279,70])]
        sig={'methods':[{'index':78586,'declaringType':11132,'name':'LoadBinDataZipFile','registeredAddress':hex(s.ENTRY),'parameters':params}], 'typeReferences':{'27066':{'kind':'valuetype','byRef':False,'typeDefinition':11130,'name':'ResourceIdentifier'},'70':{'kind':'int32','byRef':False},'469279':{'kind':'valuetype','byRef':False,'typeDefinition':8024,'name':'CompressionType'}}}
        s.verify_metadata(records,sig)
        records[0]['methods'].append(dict(method))
        with self.assertRaises(ValueError):s.verify_metadata(records,sig)
        records[0]['methods'].clear()
        for i in range(5):
            bad=copy.deepcopy(sig);bad['methods'][0]['parameters'][i]['name']='wrong'
            with self.assertRaises(ValueError):s.verify_metadata(records,bad)
        bad=copy.deepcopy(sig);bad['typeReferences']['27066']['typeDefinition']=1
        with self.assertRaises(ValueError):s.verify_metadata(records,bad)
        method['flags']='0x96'
        with self.assertRaises(ValueError):s.verify_metadata(records,sig)

    def test_rel32_and_caller_boundary(self):
        for address,displacement in ((0x180000000,0x123456),(0x180000000,-0x123456)):
            self.assertEqual(s.rel32_target(address,b'\xe9'+struct.pack('<i',displacement)),address+5+displacement)
        with self.assertRaises(ValueError):s.rel32_target(0,b'\xe8'+bytes(4))
        caller=bytes.fromhex('90e96b000000');source={'static':{'functions':[{'entry':hex(s.CALLER),'end':hex(s.CALLER+6),'sha256':s.sha(caller),'instructions':[{'address':hex(s.CALLER),'bytes':'90','mnemonic':'nop','operands':''},{'address':hex(s.CALLER+1),'bytes':'e96b000000','mnemonic':'jmp','operands':'x'}]}]}}
        with self.assertRaises(ValueError):s.verify_caller(caller,source,bytes.fromhex('e96b000000'))

    def test_aot_and_pdata(self):
        cell=bytes.fromhex('d0548a4201000000');slot=bytes.fromhex('a0f4134701000000');s.check_aot(cell,slot)
        for a,b in ((bytes(8),slot),(cell,bytes(8)),(cell,b'')):
            with self.assertRaises(ValueError):s.check_aot(a,b)
        rel=s.ENTRY-s.base.BASE
        def row(begin,end):return struct.pack('<III',begin,end,0x500)
        raw=row(rel,rel+430);self.assertEqual(s.check_row(s.base.pdata_diagnostic(raw,0,s.ENTRY))['size'],430)
        for data in (b'',raw+raw,row(rel-1,rel+430),raw+row(rel-1,rel+1),row(rel,rel),row(rel,rel+431),row(rel,rel+429)):
            with self.assertRaises(ValueError):s.check_row(s.base.pdata_diagnostic(data,0,s.ENTRY))
        for sections,length in (([(100,10,20,0)],1), ([(100,10,20,0x20000000)],11), ([(100,10,20,0x20000000)]*2,1)):
            with self.assertRaises(ValueError):s.base.offset(sections,100,length,True)
        with self.assertRaises(ValueError):s.base.read_at(io.BytesIO(b'a'),0,2)

    def test_decode_limit_and_coverage(self):
        self.assertEqual(len(s.base.decode(b'\x90'*256,0,256)['lines']),256)
        with self.assertRaisesRegex(ValueError,'budget'):s.base.decode(b'\x90'*257,0,256)
        with self.assertRaisesRegex(ValueError,'incomplete'):s.base.decode(b'\x90\x0f',0,256)
        decoded=s.base.decode(bytes.fromhex('c3e900000000'),0,256)
        self.assertEqual(len(decoded['returns']),1);self.assertEqual(len(decoded['externalTails']),1)

    def test_prebody_saved_before_bad_extent_or_section(self):
        for fault in ('missing','extent','section'):
            with self.subTest(fault=fault),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[]
                rel=s.ENTRY-s.base.BASE
                raw=struct.pack('<III',rel,rel+(429 if fault=='extent' else 430),0x500)
                diag=s.base.pdata_diagnostic(b'' if fault=='missing' else raw,0,s.ENTRY)
                def read(f,off,length):
                    reads.append((off,length))
                    if length==5:return bytes.fromhex('e96b000000')
                    if off==0x1422f9950:return bytes.fromhex('d0548a4201000000')
                    if off==0x14293eca0:return bytes.fromhex('a0f4134701000000')
                    if length==14344080:return b'table'
                    raise AssertionError('unexpected body read')
                def offset(sections,address,length,executable=False):
                    if address==s.ENTRY and fault=='section':raise ValueError('non executable/file-backed')
                    return address
                real_sha=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
                with patch.object(s,'source_gate',return_value=({'stock':{}},b'',{})),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s.base,'pe_info',return_value=((0x1a4e6a40,14344080),[],{})),patch.object(s.base,'read_at',side_effect=read),patch.object(s.base,'offset',side_effect=offset),patch.object(s,'verify_caller'),patch.object(s.base,'pdata_diagnostic',return_value=diag),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaises(ValueError):s.collect(dest)
                self.assertTrue((dest/'boundary.private.json').exists());self.assertTrue((dest/'pre-body.private.json').exists());self.assertFalse((dest/'body.bin').exists())
                self.assertFalse(any(length==430 for _,length in reads));self.assertEqual(json.loads((dest/'failure.private.json').read_bytes())['stage'],'pdata-before-body')

    def test_input_failure_stops_before_pe(self):
        for failed_stage in ('source','stock'):
            with self.subTest(failed_stage=failed_stage),tempfile.TemporaryDirectory() as d:
                dest=Path(d)
                source=patch.object(s,'source_gate',side_effect=ValueError('source drift')) if failed_stage=='source' else patch.object(s,'source_gate',return_value=({'stock':{'YuanShen.exe':{'bytes':1,'sha256':'wrong'}}},b'',{}))
                with source,patch.object(s.subprocess,'check_output',return_value=d),patch.object(s,'checked',side_effect=ValueError('stock drift')),patch.object(s.base,'pe_info') as pe:
                    with self.assertRaises(ValueError):s.collect(dest)
                    pe.assert_not_called()
                self.assertFalse((dest/'body.bin').exists())

    def test_real_caller_wrong_boundary_and_tail(self):
        req=json.loads(s.REQUEST.read_bytes());caller=(s.ROOT/req['callerBinding']['acceptedCaller']['rawSource']).read_bytes()
        source=json.loads((s.ROOT/'local/migration72/d02-active/resources/index/native-final-1.json').read_bytes())
        tail=caller[s.TAIL-s.CALLER:s.TAIL-s.CALLER+5];s.verify_caller(caller,source,tail)
        with self.assertRaises(ValueError):s.verify_caller(caller,source,bytes(5))
        bad=copy.deepcopy(source);row=next(x for x in bad['static']['functions'] if x['entry']==hex(s.CALLER))
        line=next(x for x in row['instructions'] if x['address']==hex(s.TAIL));line['address']=hex(s.TAIL+1)
        with self.assertRaises(ValueError):s.verify_caller(caller,bad,tail)

    def test_body_sha_failure_is_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir()
            rel=s.ENTRY-s.base.BASE;diag=s.base.pdata_diagnostic(struct.pack('<III',rel,rel+430,0x500),0,s.ENTRY)
            def read(f,off,length):
                if length==5:return bytes.fromhex('e96b000000')
                if off==0x1422f9950:return bytes.fromhex('d0548a4201000000')
                if off==0x14293eca0:return bytes.fromhex('a0f4134701000000')
                if length==14344080:return b'table'
                if length==430:return bytes(430)
                raise AssertionError('unexpected read')
            real_sha=s.sha
            def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
            with patch.object(s,'source_gate',return_value=({'stock':{}},b'',{})),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s.base,'pe_info',return_value=((0x1a4e6a40,14344080),[],{})),patch.object(s.base,'read_at',side_effect=read),patch.object(s.base,'offset',side_effect=lambda sections,address,length,executable=False:address),patch.object(s,'verify_caller'),patch.object(s.base,'pdata_diagnostic',return_value=diag),patch.object(s,'sha',side_effect=sha):
                with self.assertRaisesRegex(ValueError,'body SHA'):s.collect(dest)
            self.assertTrue((dest/'pre-body.private.json').exists());self.assertEqual((dest/'body.bin').read_bytes(),bytes(430))
            self.assertFalse((dest/'extraction.private.json').exists())

if __name__=='__main__':unittest.main()
