import copy
import io
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools.migration72 import scene_read_control_tail_regions as s

class Gates(unittest.TestCase):
    def make(self,ranges):
        table=b''.join(struct.pack('<III',a-s.base.BASE,b-s.base.BASE,0x500+i) for i,(a,b) in enumerate(ranges))
        return [s.base.pdata_diagnostic(table,0,t) for t in s.TARGETS]
    def sections(self):return [(s.OLD[0][0],20000,100,0x20000000)]

    def test_dedup_one_two_three_and_exact_optional(self):
        lo=min(s.TARGETS);hi=max(s.TARGETS)
        for ranges,count in (([(lo,hi+1)],1), ([(lo,s.TARGETS[0]+1),(hi,hi+1)],2), ([(t,t+1) for t in s.TARGETS],3)):
            d=s.diagnose(self.make(ranges),self.sections());self.assertTrue(d['eligible'],d['issues']);self.assertEqual(len(d['regions']),count)
        d=s.diagnose(self.make([(lo,hi+1)]),self.sections());self.assertEqual(d['targets'][0]['matchLists']['exact'],[])
        self.assertEqual(d['budget']['regionBytes'],hi-lo+1);self.assertEqual(sum(len(r['targets']) for r in d['regions']),3)

    def test_overlap_each_old_new_pairs_budget_section(self):
        first,second,third=s.OLD;lo=min(s.TARGETS);hi=max(s.TARGETS)
        for old in (first,second,third):
            d=s.diagnose(self.make([(old[1]-1,hi+1)]),self.sections());self.assertFalse(d['eligible']);self.assertGreater(d['oldOverlapBytes'],0)
        ranges=[(lo,lo+7),(lo+5,s.TARGETS[0]+1),(hi,hi+1)]
        d=s.diagnose(self.make(ranges),self.sections());self.assertFalse(d['eligible']);self.assertTrue(any(x['bytes']>0 for x in d['pairwiseNewIntersections']))
        d=s.diagnose(self.make([(lo,lo+6),(lo+6,hi-5),(hi,hi+8100)]),self.sections());self.assertIn('aggregate region budget',d['issues']);self.assertTrue(all(r['size']<=8192 for r in d['regions']))
        ranges=[(t,t+1) for t in s.TARGETS];d=s.diagnose(self.make(ranges),[(lo,200,0,0)]);self.assertFalse(d['eligible'])
        d=s.diagnose(self.make([(lo,lo)]),self.sections());self.assertFalse(d['eligible'])
        d=s.diagnose(self.make([(lo,hi+1),(lo,hi+1)]),self.sections());self.assertFalse(d['eligible'])
        with self.assertRaises(ValueError):s.base.read_at(io.BytesIO(b'a'),0,2)

    def test_old_sources_and_jcc_boundaries(self):
        req=json.loads(s.REQUEST.read_bytes());root=s.ROOT/'local/migration72/d02-resume-20261111c'
        bodies=[(root/'scene-read-core-window/fresh1/body.bin').read_bytes(),(root/'scene-read-core-continuation-region/fresh1/region.bin').read_bytes(),(root/'scene-read-return-continuation-region/fresh1/region.bin').read_bytes()]
        raws=[bytes.fromhex(x['hex']) for x in req['sourceBinding']['branchSources']];s.verify_sources(req,bodies,raws)
        bad=list(raws);bad[0]=bytes(6)
        with self.assertRaises(ValueError):s.verify_sources(req,bodies,bad)
        broken=copy.deepcopy(req);broken['sourceBinding']['branchSources'][0]['site']=hex(int(broken['sourceBinding']['branchSources'][0]['site'],16)+1)
        with self.assertRaises(ValueError):s.verify_sources(broken,bodies,raws)
        for delta in (0x123456,-0x123456):
            site=0x180000000;self.assertEqual(s.jcc_target(site,b'\x0f\x84'+struct.pack('<i',delta)),site+6+delta)
        with self.assertRaises(ValueError):s.jcc_target(0,b'\xe8'+bytes(5))

    def test_decode_targets_external_and_aggregate_budget(self):
        pc=s.TARGETS[0];row={'begin':hex(pc),'targets':[hex(pc)]}
        d=s.decode_region(bytes.fromhex('740290'),row,2048);self.assertEqual(d['externalConditionalBranches'][0]['target'],hex(pc+4));self.assertEqual(d['regionEndFallthrough']['target'],hex(pc+3))
        d=s.decode_region(bytes.fromhex('c3'),row,2048);self.assertFalse(d['regionEndFallthrough']['possible'])
        with self.assertRaises(ValueError):s.decode_region(bytes.fromhex('6690'),{'begin':hex(pc-1),'targets':[hex(pc)]},2048)
        with self.assertRaises(ValueError):s.decode_region(b'\x90\x0f',row,2048)
        with self.assertRaises(ValueError):s.decode_region(b'\x90'*2,row,1)

    def test_source_stock_or_batch_failure_prevents_all_regions(self):
        for kind in ('source','stock','missing','overlap','overbudget','section'):
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as d:
                root=Path(d);client=root/'client';client.mkdir();(client/'YuanShen.exe').write_bytes(b'');dest=root/'out';dest.mkdir();reads=[]
                rows=[{'site':hex(0x150000000+i*6),'hex':'0f8400000000','target':hex(0x150000006+i*6),'sourceKind':'core','condition':'fixture'} for i in range(8)]
                stock={'YuanShen.exe':{'bytes':1,'sha256':'bad'}} if kind=='stock' else {}
                req={'stock':stock,'sourceBinding':{'branchSources':rows}}
                gate=patch.object(s,'source_gate',side_effect=ValueError('source drift')) if kind=='source' else patch.object(s,'source_gate',return_value=(req,[]))
                ranges=[(t,t+1) for t in s.TARGETS]
                if kind=='missing':ranges=ranges[:-1]
                if kind=='overlap':ranges=[(s.OLD[2][1]-1,max(s.TARGETS)+1)]
                if kind=='overbudget':ranges=[(min(s.TARGETS),min(s.TARGETS)+6),(min(s.TARGETS)+6,max(s.TARGETS)-5),(max(s.TARGETS),max(s.TARGETS)+8100)]
                diags=self.make(ranges);sections=[(s.OLD[0][0],20000,100,0 if kind=='section' else 0x20000000)]
                def read(f,off,n):
                    reads.append(n)
                    if n==6:return bytes.fromhex('0f8400000000')
                    if n==14344080:return b'table'
                    raise AssertionError('new region read')
                original_sha=s.sha
                def sha(data):return '5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d' if data==b'table' else original_sha(data)
                original_offset=s.base.offset
                def offset(sec,address,n,executable=False):
                    if n==6 or n==14344080:return address
                    return original_offset(sec,address,n,executable)
                checker=patch.object(s,'checked',side_effect=ValueError('stock drift')) if kind=='stock' else patch.object(s,'checked')
                with gate,checker,patch.object(s.subprocess,'check_output',return_value=str(client)),patch.object(s.base,'pe_info',return_value=((0x1a4e6a40,14344080),sections,{})) as pe,patch.object(s.base,'read_at',side_effect=read),patch.object(s.base,'offset',side_effect=offset),patch.object(s,'verify_sources'),patch.object(s.base,'pdata_diagnostic',side_effect=diags),patch.object(s,'sha',side_effect=sha):
                    with self.assertRaises(ValueError):s.collect(dest)
                    if kind in ('source','stock'):pe.assert_not_called()
                self.assertFalse(any(dest.glob('region-*.bin')))
                if kind not in ('source','stock'):
                    self.assertTrue((dest/'pre-body.private.json').exists());self.assertEqual(json.loads((dest/'failure.private.json').read_bytes())['regionBytesRead'],0)
                    self.assertEqual(len(reads),9)

    def test_pure_audit_reconstruction_and_tampering(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);a=root/'a';b=root/'b';a.mkdir();b.mkdir();diags=self.make([(t,t+1) for t in s.TARGETS]);decision=s.diagnose(diags,self.sections())
            directory={'rva':'0x1a4e6a40','bytes':14344080,'sha256':'fixture','decision':decision};boundary={'exceptionDirectory':directory};bodies={};results=[]
            for row in decision['regions']:
                name=row['begin'];body=b'\xc3';decoded=s.decode_region(body,row,2048);bodies['pdata-'+name+'.bin']=bytes.fromhex(row['recordHex']);bodies['region-'+name+'.bin']=body;bodies['region-'+name+'.asm.private.json']=decoded['lines']
                results.append({'begin':name,'endExclusive':row['endExclusive'],'bytes':1,'sha256':s.sha(body),'instructionCount':1,**{k:decoded[k] for k in ('references','returns','externalTails','externalConditionalBranches','externalUnconditionalBranches','regionEndFallthrough','targetInstructions','capstoneVersion')}})
            branches=[];req={'stock':{},'pdata':{'sha256':'fixture','bytes':14344080},'sourceBinding':{'incomingFrameByTarget':{}}}
            report={'exceptionDirectory':directory,'branchSources':branches,'incomingFrameByTarget':{},'regions':results,'instructionCount':3,'regionBytesRead':3,'newRegions':3,'inputs':{},'passed':True,'toolSHA256':s.sha(Path(s.__file__).read_bytes()),'logicalReadComplete':False,'aotApplicable':False,'aotBytes':0,'freshBranchBytes':48,'freshBranchReadCount':8,'targetCPU':0,'nativeExecution':0,'liveReads':0,'xdataBytes':0,'calleeBodies':0,'constructorBodies':0,'globalContents':0}
            data={'boundary.private.json':boundary,'pre-body.private.json':boundary,'branch-sources.private.json':branches,'extraction.private.json':report,**bodies}
            for dest in (a,b):
                for name,value in data.items():s.save(dest/name,value)
            with patch.object(s,'source_gate',return_value=(req,[])),patch.object(s,'verify_sources'),patch.object(s.subprocess,'check_output',side_effect=AssertionError('no GAME_PATH')),patch.object(s.base,'pe_info',side_effect=AssertionError('no PE')):
                s.audit(a,b,root/'audit.json');name='region-'+decision['regions'][0]['begin']+'.bin';(b/name).write_bytes(b'\x90')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad1.json')
                (a/name).write_bytes(b'\x90')
                with self.assertRaises(ValueError):s.audit(a,b,root/'bad2.json')

if __name__=='__main__':unittest.main()
