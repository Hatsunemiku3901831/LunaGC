"""Pure file/source and exact combined-owned report checks. Never runs native code."""
from __future__ import annotations
import argparse
import ast
import copy
import hashlib
import json
from pathlib import Path
import re
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'local/migration72/d02-resume-20261110b/b-service-trigger-loader'
PRODUCER = 0x197FED3C
SERVICE = 0x3413E0
LITERAL = 0x5AD8638
ORIGINAL = bytes.fromhex('488b15f5982dec498b1e48c744242000000000')
MODES = (0, 1, 3, 11, 0xFFFFFFFF)

def require(value, message='service-trigger contract differs'):
    if not value:
        raise ValueError(message)

def digest(raw):
    return hashlib.sha256(raw).hexdigest()

def fingerprint(path):
    raw = Path(path).read_bytes()
    return dict(bytes=len(raw), sha256=digest(raw))

def verify_sources(get_identity=fingerprint):
    manifest = json.loads((RAW / 'frozen-inputs.private.json').read_bytes())
    for row in manifest['fixedSources'] + manifest['contextSnapshots']:
        require(get_identity(ROOT / row['path']) == {k: row[k] for k in ('bytes', 'sha256')}, row['path'])
    require(fingerprint(RAW / 'binding.private.rs')['sha256'] == manifest['bindingSHA256'])
    return manifest

def bindings():
    text = (RAW / 'binding.private.rs').read_text(encoding='utf-8')
    arrays = {}
    for name in ('PROLOG', 'EPILOG1', 'EPILOG2', 'ORIGINAL_BODY', 'PRODUCER_BYTES',
                 'EXE_BODY_0', 'EXE_BODY_1', 'EXE_BODY_2', 'EXE_BODY_3'):
        match = re.search(r'pub const ' + name + r':\s*&\[u8\]\s*=\s*&(\[[^;]+\]);', text)
        require(match is not None)
        arrays[name] = bytes(ast.literal_eval(match.group(1)))
    return arrays

def prefix(arrays, callback, userdata):
    result = arrays['PROLOG'] + b'\x48\xba' + struct.pack('<Q', userdata)
    result += b'\x48\x89\xe1\x48\x83\xec\x10\x48\xb8' + struct.pack('<Q', callback)
    return result + b'\xff\xd0\x48\x83\xc4\x10' + arrays['EPILOG1'] + arrays['EPILOG2']

def mode_name(mode):
    return str(mode) if mode in (0, 1, 3, 11) else 'other'

def expected_cases():
    return [(p, i, m, f) for p in ('near', 'far') for i in ('mdk', 'accountplat', 'unknown')
            for m in MODES for f in (0x202, 0xAD7)]

def moved_producer(entry, trampoline, placement):
    cell = entry - PRODUCER + LITERAL
    if placement == 'near':
        return b'\x48\x8b\x15' + struct.pack('<i', cell - trampoline - 257) + ORIGINAL[7:]
    return (b'\x48\x89\x5c\x24\xf0\x48\xbb' + struct.pack('<Q', cell) +
            b'\x48\x8b\x13\x48\x8b\x5c\x24\xf0' + ORIGINAL[7:])

def verify_emitter(row, placement, base, producer, arrays=None):
    arrays = arrays or bindings()
    require(set(row) == {'entry', 'trampoline', 'callback', 'userdata', 'placement', 'patchKind',
                        'moverKind', 'sourceCoveredBytes', 'movedEncodingBytes', 'emitterBytes',
                        'resume', 'originalHex', 'patchedHex', 'trampolineHex'})
    for key in ('entry', 'trampoline', 'callback', 'userdata'):
        require(type(row[key]) is int and row[key] > 0xFFFFFFFF)
    require(row['entry'] == base + (PRODUCER if producer else SERVICE))
    require(row['placement'] == row['patchKind'] == row['moverKind'] == placement)
    entry, trampoline = row['entry'], row['trampoline']
    require((abs(entry - trampoline - 250) < 0x7FFFF000) == (placement == 'near'))
    width = 19 if producer else 21
    original = ORIGINAL if producer else arrays['ORIGINAL_BODY'][:21]
    require(row['sourceCoveredBytes'] == width and row['resume'] == entry + width)
    require(row['originalHex'] == original.hex())
    moved = moved_producer(entry, trampoline, placement) if producer else arrays['ORIGINAL_BODY'][1:21]
    expected = prefix(arrays, row['callback'], row['userdata']) + moved
    expected += b'\xff\x25\0\0\0\0' + struct.pack('<Q', entry + width)
    require(row['movedEncodingBytes'] == len(moved))
    expected_length = (283 if placement == 'near' else 299) if producer else 284
    require(row['emitterBytes'] == len(expected) == expected_length)
    require(bytes.fromhex(row['trampolineHex']) == expected)
    if placement == 'near':
        patch = b'\xe9' + struct.pack('<i', trampoline - entry - 5) + original[5:]
    else:
        patch = b'\xff\x25\0\0\0\0' + struct.pack('<Q', trampoline) + original[14:]
    require(row['patchedHex'] == patch.hex())
    return dict(kind='producer' if producer else 'service', placement=placement,
                sourceCoveredBytes=width, movedEncodingBytes=len(moved), emitterBytes=len(expected))

ROW_BOOLS = ('gpr16', 'xmm16Low128', 'rflags', 'rbpEqualsSPlus128', 'stack128', 'lastError',
             'returnHigh64', 'originalVirtualCallOnce', 'originalReturn', 'actualInstallerBeforeAction',
             'successThenFailureHeld', 'ownedNormalReturnRestored')

def expected_summary():
    return dict(schema=1, scope='real combined owned normal-flow only', producerFirstErrorSealed=True,
                producerFailureShortEachRead=True, producerActualOtherThreadZeroRead=True,
                serviceUnknownReentrantOtherThreadSealed=True, bindingEveryFailureShortPrefix=True,
                allSevenCodeSlotDriftsRejected=True, actualLoadedModuleFailureAndHold=True,
                actualBothIlhookFailuresNoWrite=True, clientLoaded=False, sdkExecuted=False,
                naturalTriggerObserved=False, exceptionUnwindVerified=False, extendedVectorVerified=False, d02Passed=False)

def verify_owned_report(report):
    require({k:v for k,v in report.items() if k != 'cases'} == expected_summary())
    rows = report['cases']
    require(type(rows) is list and len(rows) == 60)
    arrays = bindings()
    verified = []
    for row, case in zip(rows, expected_cases()):
        placement, input_kind, mode, flags = case
        require(set(row) == {'placement', 'inputKind', 'mode', 'flagsSeed', 'exeBase', 'sdkBase',
            'literalCell', 'literalValue', 'producer', 'service', 'serviceEvent', 'producerReads',
            'producerBytes', 'serviceReads', 'serviceBytes', 'bindingReads', 'bindingBytes', 'midRspMod16', *ROW_BOOLS})
        require((row['placement'], row['inputKind'], row['mode'], row['flagsSeed']) == case)
        for key in ROW_BOOLS:
            require(row[key] is True)
        require(type(row['exeBase']) is int and row['exeBase'] > 0xFFFFFFFF)
        require(type(row['sdkBase']) is int and row['sdkBase'] > 0xFFFFFFFF and row['sdkBase'] != row['exeBase'])
        require(row['literalCell'] == row['exeBase'] + LITERAL and row['literalValue'] == 0x8877665544332211)
        require((row['producerReads'], row['producerBytes'], row['serviceReads'], row['serviceBytes'],
                 row['bindingReads'], row['bindingBytes'], row['midRspMod16']) == (2,16,1,8,14,5832,0))
        require(row['serviceEvent'] == dict(status='sealed', fact='base-service-entry', service=input_kind,
                                           mode=mode_name(mode), readFailure='none'))
        producer = verify_emitter(row['producer'], placement, row['exeBase'], True, arrays)
        service = verify_emitter(row['service'], placement, row['sdkBase'], False, arrays)
        verified.append(dict(placement=placement, inputKind=input_kind, mode=mode_name(mode), flagsSeed=flags,
                             producer=producer, service=service))
    return dict(passed=True, scope='combined-owned-exact-emitter-and-report', cases=60,
                emitters=120, verifiedCases=verified, clientLoaded=False, sdkExecuted=False, d02Passed=False)

def artificial_emitter(base, producer, placement, arrays=None):
    arrays = arrays or bindings()
    entry = base + (PRODUCER if producer else SERVICE)
    trampoline = entry + 0x20000000 if placement == 'near' else entry + 0x200000000
    callback, userdata = 0x7FF812345670, 0x1AB12345670
    original = ORIGINAL if producer else arrays['ORIGINAL_BODY'][:21]
    moved = moved_producer(entry, trampoline, placement) if producer else arrays['ORIGINAL_BODY'][1:21]
    emitted = prefix(arrays, callback, userdata) + moved + b'\xff\x25\0\0\0\0' + struct.pack('<Q', entry + len(original))
    patch = ((b'\xe9' + struct.pack('<i', trampoline-entry-5) + original[5:]) if placement == 'near' else
             b'\xff\x25\0\0\0\0' + struct.pack('<Q', trampoline) + original[14:])
    return dict(entry=entry, trampoline=trampoline, callback=callback, userdata=userdata, placement=placement,
                patchKind=placement, moverKind=placement, sourceCoveredBytes=len(original), movedEncodingBytes=len(moved),
                emitterBytes=len(emitted), resume=entry+len(original), originalHex=original.hex(),
                patchedHex=patch.hex(), trampolineHex=emitted.hex())

def artificial_report():
    # Exercises the verifier only. No such report is a CPU execution witness.
    arrays = bindings()
    rows = []
    for placement, input_kind, mode, flags in expected_cases():
        eb, sb = 0x2000000000, 0x2100000000
        rows.append(dict(placement=placement, inputKind=input_kind, mode=mode, flagsSeed=flags,
            exeBase=eb, sdkBase=sb, literalCell=eb+LITERAL, literalValue=0x8877665544332211,
            producer=artificial_emitter(eb,True,placement,arrays), service=artificial_emitter(sb,False,placement,arrays),
            serviceEvent=dict(status='sealed',fact='base-service-entry',service=input_kind,mode=mode_name(mode),readFailure='none'),
            producerReads=2,producerBytes=16,serviceReads=1,serviceBytes=8,bindingReads=14,bindingBytes=5832,
            midRspMod16=0,**dict.fromkeys(ROW_BOOLS,True)))
    return dict(expected_summary(),cases=rows)

class ServiceTriggerLoaderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = verify_sources()
        cls.arrays = bindings()

    def test_complete_frozen_code_and_original_call_not_covered(self):
        a = self.arrays
        expected = [(3564,'2f1d9497c12f74668ec02b0af4e202df4acbcf17990e8acf15e43f415f42e8d1'),
            (155,'0f7aa71b63ff733cd37d1711fa502d77baee5cd5c976569cb003dcb40660347e'),
            (823,'44dacd31e7c7c89506e1e21031390070162896c1f3489ef4011ac67e04721008'),
            (203,'76cd5ef82796d0f2a2b4f25b9dd396df1237f5f6fab4b4e983bab46eb9ff3c99')]
        for index, pair in enumerate(expected):
            self.assertEqual((len(a['EXE_BODY_'+str(index)]),digest(a['EXE_BODY_'+str(index)])),pair)
        self.assertEqual(sum(n for n,_ in expected),4745)
        self.assertEqual(sum((n+511)//512 for n,_ in expected),11)
        self.assertEqual(a['PRODUCER_BYTES'],ORIGINAL)
        self.assertEqual(PRODUCER+7+struct.unpack('<i',ORIGINAL[3:7])[0],LITERAL)
        self.assertEqual(a['EXE_BODY_0'][0xA2C:0xA3F],ORIGINAL)
        self.assertEqual(a['EXE_BODY_0'][0xA48:0xA4E],bytes.fromhex('ff93f0000000'))
        self.assertEqual(digest(a['ORIGINAL_BODY']),'eea7fc7301072783039a2dcf7951c2a61e87dd3f935b63701b49fa2e6e32ef1a')

    def test_each_fixed_source_and_snapshot_drift_or_missing_refuses(self):
        identities = {ROOT/r['path']:{k:r[k] for k in ('bytes','sha256')}
                      for r in self.manifest['fixedSources']+self.manifest['contextSnapshots']}
        for target in identities:
            for absent in (False,True):
                def changed(path):
                    if path == target:
                        if absent:
                            raise FileNotFoundError(path)
                        return dict(bytes=identities[path]['bytes'],sha256='0'*64)
                    return identities[path]
                with self.subTest(path=target,absent=absent),self.assertRaises((ValueError,FileNotFoundError)):
                    verify_sources(changed)

    def test_both_complete_emitters_and_all_60_combined_cases(self):
        report=artificial_report()
        verified=verify_owned_report(report)
        self.assertEqual(verified['emitters'],120)
        for mutation in ('missing','duplicate','flag','fake-install','wrong-ledger','unknown-disclosure'):
            bad=copy.deepcopy(report)
            if mutation=='missing':bad['cases'].pop()
            elif mutation=='duplicate':bad['cases'][1]=bad['cases'][0]
            elif mutation=='flag':bad['cases'][0]['midRspMod16']=8
            elif mutation=='fake-install':bad['cases'][0]['actualInstallerBeforeAction']=False
            elif mutation=='wrong-ledger':bad['cases'][0]['bindingReads']=16
            else:bad['cases'][0]['serviceEvent']['raw']=123
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):verify_owned_report(bad)

    def test_each_byte_and_pointer_of_both_emitters_refuses_drift(self):
        for producer in (False,True):
            for placement in ('near','far'):
                row=artificial_emitter(0x2000000000,producer,placement,self.arrays)
                for field in ('originalHex','patchedHex','trampolineHex'):
                    raw=bytes.fromhex(row[field])
                    for index in range(len(raw)):
                        bad=copy.deepcopy(row);mutated=bytearray(raw);mutated[index]^=1;bad[field]=mutated.hex()
                        with self.subTest(producer=producer,placement=placement,field=field,index=index),self.assertRaises(ValueError):
                            verify_emitter(bad,placement,0x2000000000,producer,self.arrays)
                for key in ('entry','trampoline','callback','userdata','resume','movedEncodingBytes','sourceCoveredBytes','emitterBytes'):
                    bad=copy.deepcopy(row);bad[key]+=8
                    with self.subTest(producer=producer,placement=placement,key=key),self.assertRaises(ValueError):
                        verify_emitter(bad,placement,0x2000000000,producer,self.arrays)

    def test_transparent_runtime_source_and_lifetime_order(self):
        root=(ROOT/'patch/examples/sdk72_service_trigger_proxy.rs').read_text()
        observed=(ROOT/'patch/examples/sdk72_service_trigger_proxy/observer.rs').read_text()
        loader=(ROOT/'patch/examples/sdk72_service_trigger_proxy/loader.rs').read_text()
        fixture=(ROOT/'patch/examples/sdk72_service_trigger_proxy/fixture.rs').read_text()
        self.assertIn('INITIALIZE.call_once',root)
        for forbidden in ('std::thread','LoadLibrary','wait_loaded','route','key_input'):
            self.assertNotIn(forbidden,root)
        self.assertLess(root.index('std::mem::forget(hook)',root.index('unsafe fn install_sdk')),root.index('state.installed.store(1'))
        self.assertLess(observed.index('compare_exchange(0,1'),observed.index('self.word(r.r14'))
        self.assertIn('target!=(self.exe_base+IMPLEMENTATION) as u64',observed)
        self.assertNotIn('registers.rdx',observed)
        self.assertIn('bytes.chunks_mut(512)',loader)
        self.assertIn('[(service_observer::ENTRY,1071)',loader)
        self.assertIn('service_observer::install_owned(c.sdk+service_observer::ENTRY',fixture)
        self.assertNotIn('installed_mask.store(255',fixture)

    def test_frozen_mover_arrays_and_mid_function_shape(self):
        source=(ROOT/'local/cargo/registry/src/index.crates.io-1949cf8c6b5b557f/ilhook-2.3.0/src/x64.rs').read_text()
        for fn,name in [('write_trampoline_prolog','PROLOG'),('write_trampoline_epilog1','EPILOG1'),('write_trampoline_epilog2_common','EPILOG2')]:
            body=source.split('fn '+fn+'(',1)[1].split('\nfn ',1)[0]
            literal=body.split('buf.write(&[',1)[1].split('])',1)[0]
            actual=bytes(int(value,16) for value in re.findall(r'0x([0-9a-fA-F]{2})\b',literal))
            self.assertEqual(actual,self.arrays[name])
        self.assertEqual(self.arrays['EXE_BODY_0'][:27].hex(),'5541574156415541545657534881ec98000000488dac2480000000')
        asm=(ROOT/'patch/examples/sdk72_service_trigger_proxy/fixture.S').read_text()
        self.assertIn('capture {before}, 0x8e8, 8, 0',asm)
        self.assertIn('capture {virtual_snapshot}, 0x8f0, 16, 0',asm)
        self.assertIn('call qword ptr [rax + {service_target}]',asm)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-sources',action='store_true')
    parser.add_argument('--owned-report',type=Path)
    args=parser.parse_args()
    if args.verify_sources:
        result=verify_sources();print(json.dumps(dict(passed=True,fixedSources=len(result['fixedSources']),contextSnapshots=len(result['contextSnapshots']))))
    elif args.owned_report:
        print(json.dumps(verify_owned_report(json.loads(args.owned_report.read_bytes())),sort_keys=True))
    else:
        unittest.main(argv=[__file__])

if __name__=='__main__':
    main()
