import copy
import io
import json
import pickle
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_world_mplevel_type_factory_window as s

class Gates(unittest.TestCase):
    def test_pins_profile_no_globals(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'p';p.write_bytes(b'abc');h=s.sha(b'abc');s.checked(p,3,h)
            for size,digest in ((4,h),(3,s.sha(b'abd'))):
                with self.assertRaises(ValueError):s.checked(p,size,digest)
        with self.assertRaises(pickle.UnpicklingError):s.base.PlainUnpickler(io.BytesIO(pickle.dumps(Path('x')))).load()
        profile={'files':{'x':{'path':'a','size':3,'sha256':'abc'}}};stock={'a':{'bytes':3,'sha256':'abc'}};s.base.verify_profile(profile,stock)
        profile['files']['x']['size']=4
        with self.assertRaises(ValueError):s.base.verify_profile(profile,stock)

    def test_declaration_and_uint32_parameter(self):
        records=[{'index':i,'methods':[]} for i in range(62018)]
        m={'index':514570,'name':'PCKPBPDHLNB','registeredAddress':hex(s.ENTRY),'parameterCount':1,'flags':'0x93','virtualSlot':65535};records[62017].update(name='NPJMDHONOMP',methods=[m])
        sig={'methods':[{'index':514570,'declaringType':62017,'name':'PCKPBPDHLNB','registeredAddress':hex(s.ENTRY),'parameters':[{'index':410581,'typeIndex':124}]}],'typeReferences':{'124':{'kind':'uint32','byRef':False}}}
        s.verify_metadata(records,sig);records[0]['methods'].append(dict(m))
        with self.assertRaises(ValueError):s.verify_metadata(records,sig)
        records[0]['methods'].clear()
        for field,value in (('index',1),('typeIndex',70)):
            bad=copy.deepcopy(sig);bad['methods'][0]['parameters'][0][field]=value
            with self.assertRaises(ValueError):s.verify_metadata(records,bad)
        bad=copy.deepcopy(sig);bad['typeReferences']['124']['kind']='int32'
        with self.assertRaises(ValueError):s.verify_metadata(records,bad)
        m['flags']='0x83'
        with self.assertRaises(ValueError):s.verify_metadata(records,sig)

    def test_caller_context_and_signed_rel32(self):
        req=json.loads(s.REQUEST.read_bytes());raw=(s.ROOT/req['fixedArtifacts']['oldCallerBody']['file']).read_bytes();manifest=json.loads((s.ROOT/req['fixedArtifacts']['oldCallerManifest']['file']).read_bytes());context=raw[s.CONTEXT-s.CALLER:s.CONTEXT-s.CALLER+20]
        s.verify_caller(raw,manifest,context)
        with self.assertRaises(ValueError):s.verify_caller(raw,manifest,bytes(20))
        bad=bytearray(raw);bad[0]^=1
        with self.assertRaises(ValueError):s.verify_caller(bytes(bad),manifest,context)
        wrong=copy.deepcopy(manifest);line=next(x for x in wrong['instructions'] if x['va']=='0x14b135d07');line['va']='0x14b135d08'
        with self.assertRaises(ValueError):s.verify_caller(raw,wrong,context)
        for offset in (0x123456,-0x123456):
            addr=0x180000000;self.assertEqual(s.rel32_target(addr,b'\xe8'+struct.pack('<i',offset)),addr+5+offset)
        with self.assertRaises(ValueError):s.rel32_target(0,b'\xe9'+bytes(4))

    def test_aot_pdata_and_decode(self):
        cell=bytes.fromhex('d0548a4201000000');slot=bytes.fromhex('a0848a4e01000000');s.check_aot(cell,slot)
        for a,b in ((bytes(8),slot),(cell,bytes(8)),(cell,b'')):
            with self.assertRaises(ValueError):s.check_aot(a,b)
        rel=s.ENTRY-s.base.BASE
        def row(a,b):return struct.pack('<III',a,b,0x500)
        sec=[(s.ENTRY-1,9000,50,0x20000000)]
        self.assertTrue(s.decide(s.base.pdata_diagnostic(row(rel,rel+392),0,s.ENTRY),sec)['eligible'])
        raw=row(rel,rel+392)
        for data in (b'',raw+raw,row(rel-1,rel+32),raw+row(rel-1,rel+1),row(rel,rel),row(rel,rel+8193),row(rel,rel+1),row(rel,rel+8192)):
            self.assertFalse(s.decide(s.base.pdata_diagnostic(data,0,s.ENTRY),sec)['eligible'])
        self.assertFalse(s.decide(s.base.pdata_diagnostic(raw,0,s.ENTRY),[(s.ENTRY,392,50,0)])['eligible'])
        self.assertFalse(s.decide(s.base.pdata_diagnostic(raw,0,s.ENTRY),[(s.ENTRY,391,50,0x20000000)])['eligible'])
        with self.assertRaises(ValueError):s.base.read_at(io.BytesIO(b'a'),0,2)
        self.assertEqual(len(s.base.decode(b'\x90'*2048,s.ENTRY,2048)['lines']),2048)
        with self.assertRaises(ValueError):s.base.decode(b'\x90'*2049,s.ENTRY,2048)
        with self.assertRaises(ValueError):s.base.decode(b'\x90\x0f',s.ENTRY,2048)
        decoded=s.base.decode(bytes.fromhex('ffe0'),s.ENTRY,2048);self.assertIsNone(decoded['references'][0]['target'])

    def test_failure_before_or_after_body_records_actual_bytes(self):
        for fault in ('source','stock','missing','oversize','section','decode'):
            with self.subTest(fault=fault),tempfile.TemporaryDirectory() as d:
                root=Path(d);game=root/'game';game.mkdir();(game/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[]
                rel=s.ENTRY-s.base.BASE;raw=struct.pack('<III',rel,rel+(8193 if fault=='oversize' else 392),0x500);diag=s.base.pdata_diagnostic(b'' if fault=='missing' else raw,0,s.ENTRY)
                sections=[(s.ENTRY,9000,50,0 if fault=='section' else 0x20000000)]
                stock={'YuanShen.exe':{'bytes':1,'sha256':'wrong'}} if fault=='stock' else {};req={'stock':stock}
                gate=patch.object(s,'source_gate',side_effect=ValueError('source drift')) if fault=='source' else patch.object(s,'source_gate',return_value=(req,{},b'',{}))
                def read(f,off,n):
                    reads.append((off,n))
                    if n==20:return bytes.fromhex('0fb6440820ffc1894e1c04350fb6c8e894277703')
                    if off==0x1422f9950:return bytes.fromhex('d0548a4201000000')
                    if off==0x142c92520:return bytes.fromhex('a0848a4e01000000')
                    if n==14344080:return b'table'
                    if n==392:return b'\x90'*391+b'\x0f'
                    raise AssertionError('unexpected read')
                original=s.base.offset
                def offset(sec,address,n,executable=False):
                    if address in (s.CONTEXT,0x1422f9950,0x142c92520) or n==14344080:return address
                    return original(sec,address,n,executable)
                real_sha=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else real_sha(data)
                checker=patch.object(s,'checked',side_effect=ValueError('stock drift')) if fault=='stock' else patch.object(s,'checked')
                with gate,checker,patch.object(s.subprocess,'check_output',return_value=str(game)),patch.object(s.base,'pe_info',return_value=((0x1a4e6a40,14344080),sections,{'headerBytes':100,'sectionTableBytes':100})) as pe,patch.object(s.base,'read_at',side_effect=read),patch.object(s.base,'offset',side_effect=offset),patch.object(s,'verify_caller'),patch.object(s.base,'pdata_diagnostic',return_value=diag),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaises(ValueError):s.collect(dest)
                    if fault in ('source','stock'):pe.assert_not_called()
                report=json.loads((dest/'failure.private.json').read_bytes())
                if fault in ('missing','oversize','section'):
                    self.assertTrue((dest/'pre-body.private.json').exists());self.assertFalse((dest/'body.bin').exists());self.assertEqual(report['bodyBytesRead'],0)
                if fault=='decode':
                    self.assertTrue((dest/'pre-body.private.json').exists());self.assertEqual((dest/'body.bin').read_bytes(),b'\x90'*391+b'\x0f');self.assertEqual(report['bodyBytesRead'],392);self.assertFalse((dest/'extraction.private.json').exists())

    def test_layout_instruction_proof_and_coverage(self):
        body=(s.OUT/'layout-v1-freeze/fresh1/body.bin').read_bytes();d=s.decode_body(body);layout=d['layout']
        self.assertEqual((len(body),len(d['lines']),layout['code']['bytes'],layout['nop']['hex'],layout['table']['bytes']),(392,81,358,'6690',32))
        self.assertEqual(layout['code']['sha256'],'ff72581b0136babd3694d4d7eef8ec3d0b87a9cda89b258c3e4d7897356819e2')
        self.assertEqual(layout['table']['sha256'],'12b5a0194a84cfdd30184fa6784922a6b385f5e7af26e02842b2f57932595143')
        spec=json.loads((s.ROOT/'local/migration72/d02-resume-20261111c/world-type-factory-raw-review/collector-layout-fix-spec.private.json').read_bytes())['expectedLayout']
        self.assertEqual(layout['table']['rows'],spec['data']['signedRows']);self.assertEqual([x['signed32'] for x in layout['table']['rows']],[-332,-286,-274,-262,-122,-97,-70,-45])
        self.assertEqual(b''.join(bytes.fromhex(x['hex']) for x in d['lines'])+bytes.fromhex(layout['nop']['hex'])+bytes.fromhex(layout['table']['rawHex']),body)
        self.assertEqual((layout['coverageBytes'],layout['overlapBytes'],layout['uncoveredBytes']),(392,0,0))
        starts=set(layout['code']['boundaries']);self.assertTrue(all(x['target'] in starts for x in layout['cfgEdges']))
        self.assertEqual(layout['case1']['target'],'0x14e8a84ea');self.assertEqual(layout['case1']['allocationReturnAndDependencies'],'frozen')
        self.assertFalse(layout['logicalFunctionComplete']);self.assertFalse(layout['normalEndFallthroughIntoTable'])

    def test_layout_rejects_modified_dispatch_nop_table_and_source(self):
        body=(s.OUT/'layout-v1-freeze/fresh1/body.bin').read_bytes()
        for offset in (7,8,10,14,20,23,26,27,357,358,359):
            bad=bytearray(body);bad[offset]^=1
            with self.subTest(offset=offset),self.assertRaises(ValueError):s.decode_body(bytes(bad),_structural_test_only=True)
        bad=bytearray(body);bad[100]^=1
        with self.assertRaises(ValueError):s.decode_body(bytes(bad))
        for target in (s.ENTRY+1,0x14e8a8606,0x14e8a8608,0x14e8a8628):
            bad=bytearray(body);bad[360:364]=struct.pack('<i',target-0x14e8a8608)
            with self.subTest(target=hex(target)),self.assertRaises(ValueError):s.decode_body(bytes(bad),_structural_test_only=True)
        with self.assertRaises(ValueError):s.decode_body(body[:-1],_structural_test_only=True)

    def test_pure_audit_tamper_no_pe(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);a=root/'a';b=root/'b';a.mkdir();b.mkdir();body=(s.OUT/'layout-v1-freeze/fresh1/body.bin').read_bytes();decoded=s.decode_body(body);rel=s.ENTRY-s.base.BASE
            diag=s.base.pdata_diagnostic(struct.pack('<III',rel,rel+392,0x500),0,s.ENTRY);decision=s.decide(diag,[(s.ENTRY,392,50,0x20000000)]);directory={'rva':'0x1a4e6a40','bytes':14344080,'sha256':'fixture','decision':decision};boundary={'exceptionDirectory':directory}
            report={'exceptionDirectory':directory,'pins':{},'stock':{},'passed':True,'conditionalSelector':1,'newBodies':1,'bodyBytesRead':392,'entry':hex(s.ENTRY),'endExclusive':hex(s.ENTRY+392),'bodySHA256':s.sha(body),'instructionCount':81,'layoutComplete':True,'toolSHA256':s.sha(Path(s.__file__).read_bytes()),'actualReaderFrame':False,'naturalMapProduced':False,'MPLevelObjectProduced':False,'targetCPU':0,'nativeExecution':0,'models':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'globalContents':0,**{k:decoded[k] for k in ('references','returns','externalTails','computedControlEdges','externalBranches','regionEndFallthrough','linearDecodeLimit','logicalFunctionComplete','capstoneVersion')}}
            data={'boundary.private.json':boundary,'pre-body.private.json':boundary,'caller-context.bin':bytes(20),'aot-cell.bin':bytes.fromhex('d0548a4201000000'),'aot-slot.bin':bytes.fromhex('a0848a4e01000000'),'pdata.bin':bytes.fromhex(decision['selected']['recordHex']),'body.bin':body,'body.asm.private.json':decoded['lines'],'body.layout.private.json':decoded['layout'],'extraction.private.json':report}
            for dest in (a,b):
                for name,value in data.items():s.save(dest/name,value)
            req={'stock':{},'pdata':{'exceptionDirectorySHA256':'fixture'}}
            with patch.object(s,'source_gate',return_value=(req,{},b'',{})),patch.object(s,'verify_caller'),patch.object(s.subprocess,'check_output',side_effect=AssertionError('no GAME_PATH')),patch.object(s.base,'pe_info',side_effect=AssertionError('no PE')):
                s.audit(a,b,root/'audit.json')
                for flag in ('actualReaderFrame','naturalMapProduced','MPLevelObjectProduced','logicalFunctionComplete'):
                    altered=copy.deepcopy(report);altered[flag]=True
                    for dest in (a,b):(dest/'extraction.private.json').write_text(json.dumps(altered))
                    with self.subTest(flag=flag),self.assertRaises(ValueError):s.audit(a,b,root/'badflag.json')
                for dest in (a,b):(dest/'extraction.private.json').write_text(json.dumps(report))
                for kind in ('count','target','coverage','nop','table'):
                    altered=copy.deepcopy(decoded['layout'])
                    if kind=='count':altered['table']['indexRange']=[0,6]
                    elif kind=='target':altered['table']['rows'][0]['target']='0x14e8a8606'
                    elif kind=='coverage':altered['uncoveredBytes']=1
                    elif kind=='nop':altered['nop']['hex']='9090'
                    else:altered['table']['rawHex']='00'+altered['table']['rawHex'][2:]
                    for dest in (a,b):(dest/'body.layout.private.json').write_text(json.dumps(altered))
                    with self.subTest(kind=kind),self.assertRaises(ValueError):s.audit(a,b,root/'badlayout.json')
                for dest in (a,b):(dest/'body.layout.private.json').write_text(json.dumps(decoded['layout']))
                (b/'body.bin').write_bytes(b'xx')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad1.json')
                (a/'body.bin').write_bytes(b'xx')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad2.json')

if __name__=='__main__':unittest.main()
