import copy
import io
import json
import pickle
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_reader_loaded_bundle_window as s

class Gates(unittest.TestCase):
    def test_pins_profile_and_plain_pickle(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x';p.write_bytes(b'abc');h=s.sha(b'abc');s.checked(p,3,h)
            for size,digest in ((4,h),(3,s.sha(b'abd'))):
                with self.assertRaises(ValueError):s.checked(p,size,digest)
        with self.assertRaises(pickle.UnpicklingError):s.base.PlainUnpickler(io.BytesIO(pickle.dumps(Path('x')))).load()
        profile={'files':{'x':{'path':'a','size':3,'sha256':'abc'}}};stock={'a':{'bytes':3,'sha256':'abc'}}
        s.base.verify_profile(profile,stock);profile['files']['x']['size']=4
        with self.assertRaises(ValueError):s.base.verify_profile(profile,stock)

    def test_declaration_and_five_parameters(self):
        records=[{'index':i,'methods':[]} for i in range(11121)]
        method={'index':78349,'name':'LoadBinDataZipFile','registeredAddress':hex(s.ENTRY),'parameterCount':5,'flags':'0x86','virtualSlot':65535}
        records[11120].update(name='LoadedBundle',namespace='MoleMole',methods=[method])
        params=[{'index':i,'name':n,'typeIndex':t} for i,n,t in zip(range(83275,83280),['hash','offset','length','compressionType','pageSize'],[70,70,70,469279,70])]
        sig={'methods':[{'index':78349,'declaringType':11120,'name':'LoadBinDataZipFile','registeredAddress':hex(s.ENTRY),'parameters':params}],'typeReferences':{'70':{'kind':'int32','byRef':False},'469279':{'kind':'valuetype','byRef':False,'typeDefinition':8024,'name':'CompressionType'}}}
        s.verify_metadata(records,sig);records[0]['methods'].append(dict(method))
        with self.assertRaises(ValueError):s.verify_metadata(records,sig)
        records[0]['methods'].clear()
        for i in range(5):
            bad=copy.deepcopy(sig);bad['methods'][0]['parameters'][i]['name']='wrong'
            with self.assertRaises(ValueError):s.verify_metadata(records,bad)
        bad=copy.deepcopy(sig);bad['typeReferences']['469279']['typeDefinition']=1
        with self.assertRaises(ValueError):s.verify_metadata(records,bad)
        method['flags']='0x96'
        with self.assertRaises(ValueError):s.verify_metadata(records,sig)

    def test_signed_rel32_and_caller_boundary(self):
        for displacement in (0x123456,-0x123456):
            address=0x180000000;raw=b'\xe9'+struct.pack('<i',displacement)
            self.assertEqual(s.previous.rel32_target(address,raw),address+5+displacement)
        with self.assertRaises(ValueError):s.previous.rel32_target(0,b'\xe8'+bytes(4))
        prefix=bytes(222);tail=bytes.fromhex('e91d47c0ff');suffix=bytes(203);caller=prefix+tail+suffix
        lengths=[2]*107+[8,5,203];lines=[];pos=0
        for n in lengths:
            lines.append({'address':hex(s.CALLER+pos),'hex':caller[pos:pos+n].hex(),'mnemonic':'fixture','operands':''});pos+=n
        decoded={'lines':lines,'references':[],'returns':[],'externalTails':[],'capstoneVersion':'fixture'}
        digest='a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1'
        report={'entry':hex(s.CALLER),'endExclusive':'0x14713f64e','bodySHA256':digest,'passed':True,'instructionCount':110,**{k:decoded[k] for k in ('references','returns','externalTails','capstoneVersion')}}
        audit={'passed':True,'twoFreshNineFilesByteIdentical':True}
        with patch.object(s,'sha',return_value=digest),patch.object(s.base,'decode',return_value=decoded):
            s.verify_caller(caller,lines,report,audit,tail)
            with self.assertRaises(ValueError):s.verify_caller(caller,lines,report,audit,bytes(5))
            shifted=copy.deepcopy(lines);shifted[108]['address']=hex(s.TAIL+1)
            with self.assertRaises(ValueError):s.verify_caller(caller,shifted,report,audit,tail)

    def test_aot_pdata_and_mapping(self):
        cell=bytes.fromhex('d0548a4201000000');slot=bytes.fromhex('a03cd44601000000');s.check_aot(cell,slot)
        for a,b in ((bytes(8),slot),(cell,bytes(8)),(cell,b'')):
            with self.assertRaises(ValueError):s.check_aot(a,b)
        rel=s.ENTRY-s.base.BASE
        def row(b,e):return struct.pack('<III',b,e,0x500)
        for size in (1,8192):self.assertEqual(s.check_row(s.base.pdata_diagnostic(row(rel,rel+size),0,s.ENTRY))['size'],size)
        raw=row(rel,rel+32)
        for data in (b'',raw+raw,row(rel-1,rel+32),raw+row(rel-1,rel+1),row(rel,rel),row(rel,rel+8193)):
            with self.assertRaises(ValueError):s.check_row(s.base.pdata_diagnostic(data,0,s.ENTRY))
        for sections,length in (([(100,10,20,0)],1), ([(100,10,20,0x20000000)],11), ([(100,10,20,0x20000000)]*2,1)):
            with self.assertRaises(ValueError):s.base.offset(sections,100,length,True)
        with self.assertRaises(ValueError):s.base.read_at(io.BytesIO(b'a'),0,2)

    def test_decode_coverage_and_budget(self):
        self.assertEqual(len(s.base.decode(b'\x90'*2048,0,2048)['lines']),2048)
        with self.assertRaisesRegex(ValueError,'budget'):s.base.decode(b'\x90'*2049,0,2048)
        with self.assertRaisesRegex(ValueError,'incomplete'):s.base.decode(b'\x90\x0f',0,2048)
        decoded=s.base.decode(bytes.fromhex('c3e900000000'),0,2048)
        self.assertEqual(len(decoded['returns']),1);self.assertEqual(len(decoded['externalTails']),1)

    def test_source_stock_failure_before_pe(self):
        for stage in ('source','stock'):
            with self.subTest(stage=stage),tempfile.TemporaryDirectory() as d:
                dest=Path(d)
                gate=patch.object(s,'source_gate',side_effect=ValueError('source drift')) if stage=='source' else patch.object(s,'source_gate',return_value=({'stock':{'YuanShen.exe':{'bytes':1,'sha256':'wrong'}}},b'',[],{},{}))
                with gate,patch.object(s.subprocess,'check_output',return_value=d),patch.object(s,'checked',side_effect=ValueError('stock drift')),patch.object(s.base,'pe_info') as pe:
                    with self.assertRaises(ValueError):s.collect(dest)
                    pe.assert_not_called()
                self.assertFalse((dest/'body.bin').exists())

    def test_bad_prebody_retained_without_body_read(self):
        for fault in ('missing','oversize','section'):
            with self.subTest(fault=fault),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[]
                rel=s.ENTRY-s.base.BASE;raw=struct.pack('<III',rel,rel+(8193 if fault=='oversize' else 32),0x500)
                diag=s.base.pdata_diagnostic(b'' if fault=='missing' else raw,0,s.ENTRY)
                def read(f,off,length):
                    reads.append((off,length))
                    if length==5:return bytes.fromhex('e91d47c0ff')
                    if off==0x1422f9950:return bytes.fromhex('d0548a4201000000')
                    if off==0x14293e538:return bytes.fromhex('a03cd44601000000')
                    if length==14344080:return b'table'
                    raise AssertionError('unexpected body read')
                def offset(sections,address,length,executable=False):
                    if address==s.ENTRY and fault=='section':raise ValueError('non executable/file-backed')
                    return address
                real_sha=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
                with patch.object(s,'source_gate',return_value=({'stock':{}},b'',[],{},{})),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s.base,'pe_info',return_value=((0x1a4e6a40,14344080),[],{})),patch.object(s.base,'read_at',side_effect=read),patch.object(s.base,'offset',side_effect=offset),patch.object(s,'verify_caller'),patch.object(s.base,'pdata_diagnostic',return_value=diag),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaises(ValueError):s.collect(dest)
                self.assertTrue((dest/'boundary.private.json').exists());self.assertTrue((dest/'pre-body.private.json').exists());self.assertFalse((dest/'body.bin').exists())
                self.assertFalse(any(off==s.ENTRY for off,_ in reads));self.assertEqual(json.loads((dest/'failure.private.json').read_bytes())['stage'],'pdata-before-body')

    def test_pure_audit_saved_hex_and_tamper(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);first=root/'one';second=root/'two';first.mkdir();second.mkdir();out=root/'audit.json'
            body=bytes.fromhex('90c3');decoded=s.base.decode(body,s.ENTRY,2048);rel=s.ENTRY-s.base.BASE
            row=s.check_row(s.base.pdata_diagnostic(struct.pack('<III',rel,rel+len(body),0x500),0,s.ENTRY))
            directory={'rva':'0x1a4e6a40','bytes':14344080,'sha256':'fixture','diagnostic':{'exact':[row],'containing':[row]}}
            boundary={'exceptionDirectory':directory};report={'exceptionDirectory':directory,'inputs':{},'passed':True,'newBodies':1,'entry':hex(s.ENTRY),'endExclusive':row['endExclusive'],'bodyBytes':len(body),'bodySHA256':s.sha(body),'instructionCount':len(decoded['lines']),'toolSHA256':s.sha(Path(s.__file__).read_bytes()),'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0,**{k:decoded[k] for k in ('references','returns','externalTails','capstoneVersion')}}
            data={'boundary.private.json':boundary,'pre-body.private.json':boundary,'extraction.private.json':report,'body.asm.private.json':decoded['lines'],'caller-tail.bin':bytes.fromhex('e91d47c0ff'),'cell.bin':bytes.fromhex('d0548a4201000000'),'slot.bin':bytes.fromhex('a03cd44601000000'),'pdata.bin':bytes.fromhex(row['recordHex']),'body.bin':body}
            for dest in (first,second):
                for name,value in data.items():s.save(dest/name,value)
            req={'stock':{},'pdata':{'sha256':'fixture','bytes':14344080}}
            with patch.object(s,'source_gate',return_value=(req,b'',[],{},{})),patch.object(s,'verify_caller'),patch.object(s.subprocess,'check_output',side_effect=AssertionError('no GAME_PATH')),patch.object(s.base,'pe_info',side_effect=AssertionError('no PE')):
                s.audit(first,second,out);self.assertTrue(json.loads(out.read_bytes())['passed'])
                (second/'body.bin').write_bytes(b'xx')
                with self.assertRaisesRegex(ValueError,'fresh artifacts differ'):s.audit(first,second,root/'tamper.json')
                (first/'body.bin').write_bytes(b'xx')
                with self.assertRaisesRegex(ValueError,'decode audit differs'):s.audit(first,second,root/'same-tamper.json')

if __name__=='__main__':unittest.main()
