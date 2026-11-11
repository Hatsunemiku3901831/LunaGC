import copy
import io
import json
import pickle
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_read_core_window as s

class Gates(unittest.TestCase):
    def test_pins_profile_no_globals_and_zero_metadata(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'p';p.write_bytes(b'abc');h=s.sha(b'abc');s.checked(p,3,h)
            for n,digest in ((4,h),(3,s.sha(b'abd'))):
                with self.assertRaises(ValueError):s.checked(p,n,digest)
        with self.assertRaises(pickle.UnpicklingError):s.base.PlainUnpickler(io.BytesIO(pickle.dumps(Path('x')))).load()
        profile={'files':{'x':{'path':'a','size':3,'sha256':'abc'}}};stock={'a':{'bytes':3,'sha256':'abc'}}
        s.base.verify_profile(profile,stock);profile['files']['x']['size']=4
        with self.assertRaises(ValueError):s.base.verify_profile(profile,stock)
        records=[{'index':1,'methods':[]}];s.check_no_managed_target(records)
        records[0]['methods'].append({'index':2,'registeredAddress':hex(s.ENTRY)})
        with self.assertRaisesRegex(ValueError,'metadata hits'):s.check_no_managed_target(records)

    def test_rel32_high64_and_caller_identity(self):
        for delta in (0x123456,-0x123456):
            addr=0x180000000;self.assertEqual(s.rel32_target(addr,b'\xe8'+struct.pack('<i',delta)),addr+5+delta)
        with self.assertRaises(ValueError):s.rel32_target(0,b'\xe9'+bytes(4))
        req=json.loads(s.REQUEST.read_bytes());v=req['callerBinding']['acceptedCaller'];caller=(s.ROOT/v['rawSource']).read_bytes();lines=json.loads((s.ROOT/v['asmSource']).read_bytes());report=json.loads(((s.ROOT/v['rawSource']).parent/'extraction.private.json').read_bytes());setup=caller[s.SETUP-s.CALLER:s.SETUP-s.CALLER+24]
        s.verify_caller(caller,lines,report,setup)
        with self.assertRaises(ValueError):s.verify_caller(caller,lines,report,bytes(24))
        altered=bytearray(caller);altered[0]^=1
        with self.assertRaises(ValueError):s.verify_caller(bytes(altered),lines,report,setup)
        bad=copy.deepcopy(lines);line=next(x for x in bad if x['address']==hex(s.SETUP+19));line['address']=hex(s.SETUP+20)
        with self.assertRaises(ValueError):s.verify_caller(caller,bad,report,setup)

    def test_pdata_mapping_and_decode(self):
        rel=s.ENTRY-s.base.BASE
        def row(a,b):return struct.pack('<III',a,b,0x500)
        for size in (1,8192):self.assertEqual(s.check_row(s.base.pdata_diagnostic(row(rel,rel+size),0,s.ENTRY))['size'],size)
        raw=row(rel,rel+32)
        for data in (b'',raw+raw,row(rel-1,rel+32),raw+row(rel-1,rel+1),row(rel,rel),row(rel,rel+8193)):
            with self.assertRaises(ValueError):s.check_row(s.base.pdata_diagnostic(data,0,s.ENTRY))
        for sec,n in (([(100,10,20,0)],1), ([(100,10,20,0x20000000)],11), ([(100,10,20,0x20000000)]*2,1)):
            with self.assertRaises(ValueError):s.base.offset(sec,100,n,True)
        with self.assertRaises(ValueError):s.base.read_at(io.BytesIO(b'a'),0,2)
        self.assertEqual(len(s.base.decode(b'\x90'*2048,0,2048)['lines']),2048)
        with self.assertRaises(ValueError):s.base.decode(b'\x90'*2049,0,2048)
        with self.assertRaises(ValueError):s.base.decode(b'\x90\x0f',0,2048)

    def test_source_stock_fail_before_pe(self):
        for stage in ('source','stock'):
            with self.subTest(stage=stage),tempfile.TemporaryDirectory() as d:
                dest=Path(d);gate=patch.object(s,'source_gate',side_effect=ValueError('source drift')) if stage=='source' else patch.object(s,'source_gate',return_value=({'stock':{'YuanShen.exe':{'bytes':1,'sha256':'x'}}},b'',[],{}))
                with gate,patch.object(s.subprocess,'check_output',return_value=d),patch.object(s,'checked',side_effect=ValueError('stock drift')),patch.object(s.base,'pe_info') as pe:
                    with self.assertRaises(ValueError):s.collect(dest)
                    pe.assert_not_called()
                self.assertFalse((dest/'body.bin').exists())

    def test_failure_prebody_without_body(self):
        for fault in ('missing','oversize','section'):
            with self.subTest(fault=fault),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[]
                rel=s.ENTRY-s.base.BASE;raw=struct.pack('<III',rel,rel+(8193 if fault=='oversize' else 32),0x500);diag=s.base.pdata_diagnostic(b'' if fault=='missing' else raw,0,s.ENTRY)
                def read(f,off,length):
                    reads.append((off,length))
                    if length==24:return bytes.fromhex('4c8b442460488d5720488b4b084803d64c63c8e8495a0100')
                    if length==14344080:return b'table'
                    raise AssertionError('unexpected body read')
                def offset(sections,address,length,executable=False):
                    if address==s.ENTRY and fault=='section':raise ValueError('section not executable/file-backed')
                    return address
                real=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real(data)
                with patch.object(s,'source_gate',return_value=({'stock':{}},b'',[],{})),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s.base,'pe_info',return_value=((0x1a4e6a40,14344080),[],{})),patch.object(s.base,'read_at',side_effect=read),patch.object(s.base,'offset',side_effect=offset),patch.object(s,'verify_caller'),patch.object(s.base,'pdata_diagnostic',return_value=diag),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaises(ValueError):s.collect(dest)
                self.assertTrue((dest/'boundary.private.json').exists());self.assertTrue((dest/'pre-body.private.json').exists());self.assertFalse((dest/'body.bin').exists());self.assertFalse(any(off==s.ENTRY for off,_ in reads))
                result=json.loads((dest/'failure.private.json').read_bytes());self.assertEqual(result['stage'],'pdata-before-body');self.assertEqual(result['bodyBytesRead'],0)

    def test_pure_audit_saved_hex_and_tampering(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);a=root/'a';b=root/'b';a.mkdir();b.mkdir();body=bytes.fromhex('90c3');decoded=s.base.decode(body,s.ENTRY,2048);rel=s.ENTRY-s.base.BASE
            row=s.check_row(s.base.pdata_diagnostic(struct.pack('<III',rel,rel+2,0x500),0,s.ENTRY));directory={'rva':'0x1a4e6a40','bytes':14344080,'sha256':'fixture','diagnostic':{'exact':[row],'containing':[row]}}
            boundary={'exceptionDirectory':directory};report={'exceptionDirectory':directory,'inputs':{},'passed':True,'newBodies':1,'bodyBytesRead':2,'entry':hex(s.ENTRY),'endExclusive':row['endExclusive'],'bodyBytes':2,'bodySHA256':s.sha(body),'instructionCount':2,'toolSHA256':s.sha(Path(s.__file__).read_bytes()),'ordinaryMethodHits':[],'aotApplicable':False,'aotBytes':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0,**{k:decoded[k] for k in ('references','returns','externalTails','capstoneVersion')}}
            data={'boundary.private.json':boundary,'pre-body.private.json':boundary,'extraction.private.json':report,'body.asm.private.json':decoded['lines'],'caller-setup.bin':bytes.fromhex('4c8b442460488d5720488b4b084803d64c63c8e8495a0100'),'pdata.bin':bytes.fromhex(row['recordHex']),'body.bin':body}
            for dest in (a,b):
                for name,value in data.items():s.save(dest/name,value)
            req={'stock':{},'pdata':{'sha256':'fixture','bytes':14344080}}
            with patch.object(s,'source_gate',return_value=(req,b'',[],{})),patch.object(s,'verify_caller'),patch.object(s.subprocess,'check_output',side_effect=AssertionError('no GAME_PATH')),patch.object(s.base,'pe_info',side_effect=AssertionError('no PE')):
                s.audit(a,b,root/'audit.json');(b/'body.bin').write_bytes(b'xx')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad1.json')
                (a/'body.bin').write_bytes(b'xx')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad2.json')

if __name__=='__main__':unittest.main()
