import copy
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_read_return_continuation_region as s

class Gates(unittest.TestCase):
    def test_pins_and_profile(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'x';p.write_bytes(b'abc');h=s.sha(b'abc');s.checked(p,3,h)
            for n,digest in ((4,h),(3,s.sha(b'abd'))):
                with self.assertRaises(ValueError):s.checked(p,n,digest)
        profile={'files':{'x':{'path':'a','size':3,'sha256':'x'}}};stock={'a':{'bytes':3,'sha256':'x'}}
        s.base.verify_profile(profile,stock);profile['files']['x']['size']=4
        with self.assertRaises(ValueError):s.base.verify_profile(profile,stock)

    def test_predecessor_and_context(self):
        req=json.loads(s.REQUEST.read_bytes());item=req['predecessorBinding']['acceptedRegion'];body=(s.ROOT/item['rawSource']).read_bytes();lines=json.loads((s.ROOT/item['asmSource']).read_bytes());report=json.loads(((s.ROOT/item['rawSource']).parent/'extraction.private.json').read_bytes());context=body[s.CONTEXT-s.OLD_MID:]
        s.verify_predecessor(body,lines,report,context)
        with self.assertRaises(ValueError):s.verify_predecessor(body,lines,report,bytes(11))
        bad=bytearray(body);bad[0]^=1
        with self.assertRaises(ValueError):s.verify_predecessor(bytes(bad),lines,report,context)
        bad=copy.deepcopy(lines);bad[-1]['address']=hex(s.PC)
        with self.assertRaises(ValueError):s.verify_predecessor(body,bad,report,context)

    def test_containing_exact_and_overlap_budget_section(self):
        rel=s.PC-s.base.BASE
        def row(a,b):return struct.pack('<III',a,b,0x500)
        def decision(data,sections=None):return s.diagnose(s.base.pdata_diagnostic(data,0,s.PC),sections if sections is not None else [(s.PC-100,10000,50,0x20000000)])
        d=decision(row(rel-10,rel+30));self.assertEqual(d['matchLists']['exact'],[]);self.assertEqual(d['selected']['sourcePCOffset'],10);self.assertEqual(d['overlap']['bytes'],10);self.assertEqual(d['distinctNew']['bytes'],30);self.assertFalse(d['eligible'])
        d=decision(row(s.OLD_BEGIN-s.base.BASE+20,rel+30));self.assertFalse(d['eligible']);self.assertEqual([x['bytes'] for x in d['overlap']['intersections']],[184,415]);self.assertEqual(d['overlap']['bytes'],599);self.assertEqual(d['distinctNew']['bytes'],30)
        d=decision(row(rel,rel+32));self.assertTrue(d['eligible']);self.assertEqual(d['overlap']['bytes'],0);self.assertEqual(d['distinctNew']['bytes'],32)
        for data in (b'',row(rel,rel+1)*2,row(rel-1,rel+1)+row(rel,rel+1),row(rel,rel)):
            self.assertFalse(decision(data)['eligible'])
        d=decision(row(rel,rel+8193));self.assertFalse(d['eligible']);self.assertFalse(d['budget']['positiveAndWithinBudget'])
        self.assertFalse(decision(row(rel,rel+32),[(s.PC,32,50,0)])['eligible'])
        self.assertFalse(decision(row(rel,rel+32),[(s.PC,31,50,0x20000000)])['eligible'])
        self.assertFalse(decision(row(rel-1,rel+32)+row(rel,rel),[(s.PC-1,33,50,0x20000000)])['eligible'])
        with self.assertRaises(ValueError):s.base.read_at(io.BytesIO(b'a'),0,2)

    def test_decode_pc_external_and_fallthrough(self):
        d=s.decode_region(bytes.fromhex('740290'),s.PC);self.assertEqual(d['externalBranches'][0]['target'],hex(s.PC+4));self.assertEqual(d['regionEndFallthrough']['target'],hex(s.PC+3))
        d=s.decode_region(bytes.fromhex('c3'),s.PC);self.assertFalse(d['regionEndFallthrough']['possible'])
        with self.assertRaisesRegex(ValueError,'boundary'):s.decode_region(bytes.fromhex('6690'),s.PC-1)
        with self.assertRaises(ValueError):s.decode_region(b'\x90\x0f',s.PC)
        with self.assertRaises(ValueError):s.decode_region(b'\x90'*2049,s.PC)

    def test_source_stock_failure_before_pe(self):
        for stage in ('source','stock'):
            with self.subTest(stage=stage),tempfile.TemporaryDirectory() as d:
                gate=patch.object(s,'source_gate',side_effect=ValueError('source drift')) if stage=='source' else patch.object(s,'source_gate',return_value=({'stock':{'YuanShen.exe':{'bytes':1,'sha256':'x'}}},b'',[],{}))
                with gate,patch.object(s.subprocess,'check_output',return_value=d),patch.object(s,'checked',side_effect=ValueError('stock drift')),patch.object(s.base,'pe_info') as pe:
                    with self.assertRaises(ValueError):s.collect(Path(d))
                    pe.assert_not_called()
                self.assertFalse((Path(d)/'region.bin').exists())

    def test_overlap_and_missing_preserve_prebody_zero_region(self):
        for kind in ('overlap','missing','oversize','section'):
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[];rel=s.PC-s.base.BASE
                row=struct.pack('<III',rel-1 if kind=='overlap' else rel,rel+(8193 if kind=='oversize' else 32),0x500)
                diag=s.base.pdata_diagnostic(b'' if kind=='missing' else row,0,s.PC)
                sections=[(s.PC-1,9000,50,0 if kind=='section' else 0x20000000)]
                def read(f,off,n):
                    reads.append(n)
                    if n==11:return bytes(11)
                    if n==14344080:return b'table'
                    raise AssertionError('unexpected region read')
                original_sha=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else original_sha(data)
                original_offset=s.base.offset
                def offset(sec,address,n,executable=False):
                    if address==s.CONTEXT or n==14344080:return address
                    return original_offset(sec,address,n,executable)
                with patch.object(s,'source_gate',return_value=({'stock':{}},b'',[],{})),patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s.base,'pe_info',return_value=((0x1a4e6a40,14344080),sections,{})),patch.object(s.base,'read_at',side_effect=read),patch.object(s.base,'offset',side_effect=offset),patch.object(s,'verify_predecessor'),patch.object(s.base,'pdata_diagnostic',return_value=diag),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaises(ValueError):s.collect(dest)
                self.assertTrue((dest/'pre-body.private.json').exists());self.assertFalse((dest/'region.bin').exists());self.assertNotIn(32,reads)
                failure=json.loads((dest/'failure.private.json').read_bytes());self.assertEqual(failure['regionBytesRead'],0);self.assertEqual(failure['stage'],'pdata-before-region')
                if kind=='overlap':
                    decision=failure['exceptionDirectory']['decision'];self.assertEqual(decision['matchLists']['exact'],[]);self.assertEqual(decision['overlap']['bytes'],1);self.assertTrue((dest/'pdata.bin').exists())

    def test_pure_audit_and_tampering(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);a=root/'a';b=root/'b';a.mkdir();b.mkdir();body=bytes.fromhex('90c3');decoded=s.decode_region(body,s.PC);rel=s.PC-s.base.BASE
            diag=s.base.pdata_diagnostic(struct.pack('<III',rel,rel+2,0x500),0,s.PC);decision=s.diagnose(diag,[(s.PC,2,50,0x20000000)]);directory={'rva':'0x1a4e6a40','bytes':14344080,'sha256':'fixture','decision':decision};boundary={'exceptionDirectory':directory}
            report={'exceptionDirectory':directory,'inputs':{},'passed':True,'newRegions':1,'regionBytesRead':2,'begin':hex(s.PC),'endExclusive':hex(s.PC+2),'regionSHA256':s.sha(body),'instructionCount':2,'toolSHA256':s.sha(Path(s.__file__).read_bytes()),'logicalReadComplete':False,'aotApplicable':False,'aotBytes':0,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0,**{k:decoded[k] for k in ('references','returns','externalTails','externalBranches','externalConditionalBranches','externalUnconditionalBranches','selectedTargetCoverage','regionEndFallthrough','sourcePCInstruction','capstoneVersion')}}
            data={'boundary.private.json':boundary,'pre-body.private.json':boundary,'extraction.private.json':report,'region.asm.private.json':decoded['lines'],'predecessor-context.bin':bytes(11),'pdata.bin':bytes.fromhex(decision['selected']['recordHex']),'region.bin':body}
            for dest in (a,b):
                for name,value in data.items():s.save(dest/name,value)
            req={'stock':{},'pdata':{'sha256':'fixture','bytes':14344080}}
            with patch.object(s,'source_gate',return_value=(req,b'',[],{})),patch.object(s,'verify_predecessor'),patch.object(s.subprocess,'check_output',side_effect=AssertionError('no GAME_PATH')),patch.object(s.base,'pe_info',side_effect=AssertionError('no PE')):
                s.audit(a,b,root/'audit.json');(b/'region.bin').write_bytes(b'xx')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad1.json')
                (a/'region.bin').write_bytes(b'xx')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad2.json')

if __name__=='__main__':unittest.main()
