"""File-only source preparation and exact owned-report protocol checks; no native launch."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import struct
import unittest

ROOT=Path(__file__).resolve().parents[1]
RAW=ROOT/'local/migration72/d02-resume-20261110b/b-service-entry-owned'
STATIC=ROOT/'local/migration72/d02-resume-20261110b/b-native-account-route/static-main-third'
MANIFEST_SHA='38fc265d34dbcab56e628acc0c246acb5be9c15e76e9845a9d9ad9709e96fc41'
BINDING_SHA='5e0a470490857ff44fd7a1585c6b9f82d4cb5a60553e211df36d5442e25f3619'
ENTRY=0x3413E0
ORIGINAL21=bytes.fromhex('40555356574154415541564157488dac24a8fcffff')
INPUTS=('mdk','accountplat','unknown','null','unreadable')
MODES=('0','1','3','11','other')

def require(value):
    if not value: raise ValueError('service-entry-owned-validation-failed')
def digest(raw): return hashlib.sha256(raw).hexdigest()
def fingerprint(path):
    engine,size=hashlib.sha256(),0
    with Path(path).open('rb') as stream:
        for data in iter(lambda:stream.read(1024*1024),b''):
            engine.update(data);size+=len(data)
    return dict(bytes=size,sha256=engine.hexdigest())
def verify_sources(get_identity=fingerprint):
    try:
        raw=(RAW/'frozen-inputs.private.json').read_bytes()
        require(digest(raw)==MANIFEST_SHA)
        manifest=json.loads(raw)
        require(len(manifest['fixedSources'])==41 and len(manifest['contextSnapshots'])==3)
        rows=manifest['fixedSources']+manifest['contextSnapshots']
        require(len({row['path'] for row in rows})==44)
        require(all(not row['path'].startswith('docs/') for row in rows))
        for row in rows:
            require(get_identity(ROOT/row['path'])=={key:row[key] for key in ('bytes','sha256')})
        require(digest((RAW/'binding.private.rs').read_bytes())==BINDING_SHA)
        return manifest
    except (OSError,ValueError,KeyError,TypeError):
        raise ValueError('service-entry-owned-validation-failed') from None
def bindings():
    text=(RAW/'binding.private.rs').read_text(encoding='utf8')
    arrays={}
    for name in ('ORIGINAL_BODY','ORIGINAL_SLOT','MOVED_CANONICAL','PROLOG','EPILOG1','EPILOG2'):
        match=re.search(r'pub const '+name+r': &\[u8\] = &\[([0-9,\s]*)\];',text)
        require(match is not None)
        arrays[name]=bytes(int(item) for item in match[1].split(',') if item.strip())
    return arrays
def prefix(arrays,callback,userdata):
    raw=arrays['PROLOG']+b'\x48\xba'+struct.pack('<Q',userdata)
    raw+=bytes.fromhex('4889e14883ec1048b8')+struct.pack('<Q',callback)
    raw+=bytes.fromhex('ffd04883c410')+arrays['EPILOG1']+arrays['EPILOG2']
    require(len(raw)==250)
    return raw
def event(input_kind,mode):
    return dict(status='sealed',fact='base-service-entry',
        service=input_kind if input_kind in ('mdk','accountplat') else 'unknown',mode=mode,
        readFailure='read-failed' if input_kind=='unreadable' else 'none')
def expected_cases():
    rows=[]
    for placement in ('near','far'):
        rows += [dict(placement=placement,caseKind='original-sub-resume',inputKind=kind,mode=mode,flagsSeed=flags)
                 for kind in INPUTS for mode in MODES for flags in (0x202,0xAD7)]
        rows += [dict(placement=placement,caseKind='self-owned-continuation-witness',inputKind='mdk',mode='0',flagsSeed=flags)
                 for flags in (0x202,0xAD7)]
    return rows
def verify_emitter(row):
    arrays=bindings()
    require(set(row)=={'placement','patchKind','moverKind','entry','moduleBase','callback','userdata','trampoline',
        'flagsSeed','sourceCoveredBytes','movedEncodingBytes','emitterBytes','resume','originalHex','patchedHex',
        'trampolineHex','caseKind','inputKind','mode','executed','baselineEqual','savedRspEqual',
        'originalReturnAndRet','dropRestored','event'})
    require(all(row[key] is True for key in ('executed','baselineEqual','savedRspEqual','originalReturnAndRet','dropRestored')))
    for key in ('moduleBase','entry','callback','userdata','trampoline','resume'):
        require(type(row[key]) is int and 2**32<=row[key]<2**63)
    base,entry,address=row['moduleBase'],row['entry'],row['trampoline']
    require(entry==base+ENTRY and row['resume']==entry+21 and row['sourceCoveredBytes']==21)
    require(row['inputKind'] in INPUTS and row['mode'] in MODES and row['flagsSeed'] in (0x202,0xAD7))
    require(row['caseKind'] in ('original-sub-resume','self-owned-continuation-witness'))
    original=bytes.fromhex(row['originalHex'])
    require(original==ORIGINAL21)
    distance=address-entry-5
    kind='near' if abs(distance)<=0x7FFFFFFF else 'far'
    mover='near' if abs(entry-(address+250))<0x7FFFF000 else 'far'
    patch=(b'\xe9'+struct.pack('<i',distance)+original[5:] if kind=='near'
           else bytes.fromhex('ff2500000000')+struct.pack('<Q',address)+original[14:])
    moved={20:arrays['MOVED_CANONICAL'],21:original}.get(row['movedEncodingBytes'])
    require(moved is not None)
    expected=prefix(arrays,row['callback'],row['userdata'])+moved
    expected+=bytes.fromhex('ff2500000000')+struct.pack('<Q',entry+21)
    require(bytes.fromhex(row['patchedHex'])==patch and bytes.fromhex(row['trampolineHex'])==expected)
    require(row['emitterBytes']==len(expected)==264+row['movedEncodingBytes'])
    require(row['placement']==row['patchKind']==kind==row['moverKind']==mover)
    require(row['event']==event(row['inputKind'],row['mode']))
    return {key:row[key] for key in ('placement','caseKind','inputKind','mode','flagsSeed')}
def expected_summary():
    return dict(schema=1,scope='owned common service prologue/SUB/callee/normal RET only',
        gpr16=True,xmm16=True,rflags=True,stack128=True,lastError=True,returnHigh64=True,
        savedEntryRsp=True,ownedOriginalReturn=True,ownedCalleeOnce=True,bothActualNearFar=True,
        firstUnknownSealed=True,nullZeroReads=True,modeLow32Only=True,reentrantZeroReads=True,
        actualOtherOsThreadZeroReads=True,failedAndShortReads=True,unknownNoDisclosure=True,
        originalSubResume=True,preSubContinuationWitness=True,witnessNotSdkBytes=True,
        repeat1000ZeroReads=True,actualInstallFailureNoWrite=True,ownedCodeBindingRpm=True,
        parameterCapReads=1,parameterCapBytes=8,bindingReads=3,bindingBytes=1087,
        sourceCoveredBytes=21,movedEncodingCandidates=[20,21],emitterEncodingCandidates=[284,285],
        clientLoaded=False,sdkExecuted=False,exceptionUnwindVerified=False,
        extendedVectorStateVerified=False,naturalTriggerObserved=False,d02Passed=False)
def verify_owned_report(report):
    require({key:value for key,value in report.items() if key!='emitters'}==expected_summary())
    rows=report['emitters'];require(type(rows) is list and len(rows)==104)
    verified=[verify_emitter(row) for row in rows]
    require(verified==expected_cases())
    encodings={kind:sorted({row['movedEncodingBytes'] for row in rows if row['placement']==kind}) for kind in ('near','far')}
    return dict(passed=True,scope='owned-report-structure-and-complete-exact-emitter',
        verifiedCases=verified,actualMovedEncodings=encodings,publicResult=expected_summary())
def artificial_emitter(case=None,moved_bytes=20):
    """Parser fixture only. Never emitted as a native-execution receipt."""
    case=case or expected_cases()[0]
    arrays=bindings();base=0x1000000000;entry=base+ENTRY
    address=entry+0x100000 if case['placement']=='near' else 0x100000000
    callback,userdata=0x2000001000,0x2000002000
    original=ORIGINAL21
    patch=(b'\xe9'+struct.pack('<i',address-entry-5)+original[5:] if case['placement']=='near'
        else bytes.fromhex('ff2500000000')+struct.pack('<Q',address)+original[14:])
    moved=arrays['MOVED_CANONICAL'] if moved_bytes==20 else original
    emitted=prefix(arrays,callback,userdata)+moved+bytes.fromhex('ff2500000000')+struct.pack('<Q',entry+21)
    return dict(case,patchKind=case['placement'],moverKind=case['placement'],entry=entry,moduleBase=base,
        callback=callback,userdata=userdata,trampoline=address,sourceCoveredBytes=21,movedEncodingBytes=moved_bytes,
        emitterBytes=len(emitted),resume=entry+21,originalHex=original.hex(),patchedHex=patch.hex(),
        trampolineHex=emitted.hex(),executed=True,baselineEqual=True,savedRspEqual=True,originalReturnAndRet=True,
        dropRestored=True,event=event(case['inputKind'],case['mode']))

class PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.manifest=verify_sources();cls.arrays=bindings()

    def test_complete_body_slots_extent_and_original_sub_ret(self):
        body=(STATIC/'code.bin').read_bytes()
        self.assertEqual(self.arrays['ORIGINAL_BODY'],body)
        self.assertEqual((len(body),digest(body)),(1071,'eea7fc7301072783039a2dcf7951c2a61e87dd3f935b63701b49fa2e6e32ef1a'))
        instructions=json.loads((STATIC/'code.asm.private.json').read_bytes())
        self.assertEqual(len(instructions),265)
        self.assertEqual(b''.join(bytes.fromhex(row['hex']) for row in instructions),body)
        covered=instructions[:9]
        self.assertEqual(sum(row['size'] for row in covered),21)
        self.assertEqual([row['mnemonic'] for row in covered],['push']*8+['lea'])
        self.assertEqual(body[:21],ORIGINAL21)
        self.assertEqual(body[21:28].hex(),'4881ec58040000')
        self.assertEqual(body[0x41b:].hex(),'4881c458040000415f415e415d415c5f5e5b5dc3')
        self.assertEqual((STATIC/'pdata.bin').read_bytes().hex(),'e01334000f183400000a7300')
        self.assertEqual(self.arrays['ORIGINAL_SLOT'],struct.pack('<Q',0x1803413e0))
        slots=json.loads((STATIC/'service-slots.private.json').read_bytes())
        self.assertEqual([row['vtableRVA'] for row in slots],['0x6c2098','0x6c2ca8'])
        self.assertTrue(all(bytes.fromhex(row['hex'])==self.arrays['ORIGINAL_SLOT'] for row in slots))

    def test_each_actual_fixed_source_and_snapshot_drift_or_absence_refuses(self):
        identities={ROOT/row['path']:{key:row[key] for key in ('bytes','sha256')}
                    for row in self.manifest['fixedSources']+self.manifest['contextSnapshots']}
        self.assertEqual(len(identities),44)
        for target in identities:
            for missing in (False,True):
                def changed(path):
                    if path==target:
                        if missing: raise FileNotFoundError('PRIVATE_SOURCE_SENTINEL')
                        return dict(bytes=0,sha256='0'*64)
                    return identities[path]
                with self.subTest(path=target,missing=missing),self.assertRaisesRegex(ValueError,'^service-entry-owned-validation-failed$'):
                    verify_sources(changed)

    def test_frozen_ilhook_prefix_and_only_two_exact_moved_templates(self):
        name=next(row['path'] for row in self.manifest['fixedSources'] if row['path'].endswith('/ilhook-2.3.0/src/x64.rs'))
        source=(ROOT/name).read_text()
        for function,array in [('write_trampoline_prolog','PROLOG'),('write_trampoline_epilog1','EPILOG1'),
                               ('write_trampoline_epilog2_common','EPILOG2')]:
            text=source.split('fn '+function+'(',1)[1].split('\nfn ',1)[0]
            literal=text.split('buf.write(&[',1)[1].split('])',1)[0]
            self.assertEqual(self.arrays[array],bytes(int(value,16) for value in re.findall(r'0x([0-9a-fA-F]{2})\b',literal)))
        self.assertEqual(self.arrays['MOVED_CANONICAL'],bytes.fromhex('555356574154415541564157488dac24a8fcffff'))
        self.assertEqual(len(prefix(self.arrays,0x2000001000,0x2000002000)),250)
        for kind in ('near','far'):
            for encoding in (20,21):
                case=dict(expected_cases()[0],placement=kind)
                self.assertEqual(verify_emitter(artificial_emitter(case,encoding)),case)

    def test_full104_case_protocol_requires_original_sub_and_separate_witness(self):
        for moved in (20,21):
            report=dict(expected_summary(),emitters=[artificial_emitter(case,moved) for case in expected_cases()])
            checked=verify_owned_report(report)
            self.assertEqual(len(checked['verifiedCases']),104)
            self.assertEqual(sum(row['caseKind']=='self-owned-continuation-witness' for row in checked['verifiedCases']),4)
        for mutate in ('missing','duplicate','client','sdk','natural','unwind','vector'):
            report=dict(expected_summary(),emitters=[artificial_emitter(case) for case in expected_cases()])
            if mutate=='missing': report['emitters'].pop()
            elif mutate=='duplicate': report['emitters'][1]=copy.deepcopy(report['emitters'][0])
            else: report[dict(client='clientLoaded',sdk='sdkExecuted',natural='naturalTriggerObserved',
                             unwind='exceptionUnwindVerified',vector='extendedVectorStateVerified')[mutate]]=True
            with self.subTest(mutate=mutate),self.assertRaises(ValueError):verify_owned_report(report)

    def test_each_exact_emitter_component_and_resume_drift_refuses(self):
        for key in ('originalHex','patchedHex','trampolineHex'):
            row=artificial_emitter();raw=bytearray.fromhex(row[key]);raw[-1]^=1;row[key]=raw.hex()
            with self.subTest(key=key),self.assertRaises(ValueError):verify_emitter(row)
        for key in ('callback','userdata','entry','moduleBase','trampoline','resume','sourceCoveredBytes','movedEncodingBytes','emitterBytes'):
            row=artificial_emitter();row[key]+=8
            with self.subTest(key=key),self.assertRaises(ValueError):verify_emitter(row)
        for unknown in ('raw','pointer','hash','count','caller','returned','authentication'):
            row=artificial_emitter();row['event'][unknown]=123
            with self.subTest(unknown=unknown),self.assertRaises(ValueError):verify_emitter(row)

    def test_mode_and_unknown_public_schema_are_fixed_categories(self):
        for case in expected_cases():
            row=artificial_emitter(case)
            self.assertEqual(set(row['event']),{'status','fact','service','mode','readFailure'})
            if case['inputKind'] not in ('mdk','accountplat'): self.assertEqual(row['event']['service'],'unknown')
            for key,value in [('mode',0xffffffff00000001),('service','MDK'),('readFailure',299)]:
                bad=copy.deepcopy(row);bad['event'][key]=value
                with self.assertRaises(ValueError):verify_emitter(bad)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--verify-sources',action='store_true')
    group.add_argument('--verify-owned',type=Path)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args();manifest=verify_sources()
    if args.verify_sources:
        result=dict(passed=True,scope='file-only-preparation',fixedSources=len(manifest['fixedSources']),
            contextSnapshots=len(manifest['contextSnapshots']),build=False,nativeExecution=False)
    else:
        require(args.verify_owned.resolve().is_relative_to(RAW.resolve()))
        result=verify_owned_report(json.loads(args.verify_owned.read_bytes()))
    encoded=(json.dumps(result,sort_keys=True,indent=2)+'\n').encode()
    if args.output:
        require(args.output.resolve().is_relative_to(RAW.resolve()))
        with args.output.open('xb') as stream:stream.write(encoded)
    print(json.dumps(result,sort_keys=True))

if __name__=='__main__':main()
