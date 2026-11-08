"""Owned byte-memory/backend mocks only; no OpenProcess, launch, DLL load or live RPM."""
import copy
import contextlib
import ctypes as C
import io
import json
from pathlib import Path
import struct
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tools import observe_client72_key_loader as s

PROLOG = bytes.fromhex('549c48f7c408000000742c504883ec10488b44242048890424488b4424184889442408488b4424104889442418c744241001000000eb275050488b44241848890424488b4424084889442418488b4424104889442408c744241000000000535152565755415041514152415341544155415641574883ec400f2904240f294c24100f295424200f295c2430')
EPILOG = bytes.fromhex('0f2804240f284c24100f285424200f285c24304883c440415f415e415d415c415b415a415941585d5f5e5a595b4883c408f7442408010000007410488b44241048894424189d585858eb039d5858')
P, B, O = 0x8000000100000000, 0x8000000200000000, 0x8000000300000000
RVA = [0x1000+index*0x100 for index in range(47)]


def owned_contract():
    original = s.SDK_PREFIX+bytes(range(235))
    callback = bytes.fromhex('4883ec48f30f7f642420f30f7f6c2430e83b800000f30f6f642420f30f6f6c24304883c448c3')
    return dict(stateRva=0x8b000, tableRva=0x8b1e0, callbackRva=0x1000, innerRva=0x9050,
        callbackCode=callback, prolog=PROLOG, epilog1=EPILOG[:49], epilog2=EPILOG[49:],
        sdkWindows=[(0x3950c0, original), (0x34ee6b, b'A'*76), (0x34ef3c, b'B'*76)], sdkImageSize=0x932000)


def state(failure=0, phase=5):
    bound = phase == 5 or failure >= 112
    return struct.pack('<6IQ8I', 1, 64, phase, failure, 0, int(phase == 5), B if bound else 0,
        0x932000 if bound else 0, int(bound), int(bound), 7 if bound else 0,
        1, int(failure != 101), int(phase == 5), 0)


def fixture(far=False):
    contract = owned_contract()
    entry = B+0x3950c0
    target = entry+(0x100000000 if far else 0x100000)
    callback, userdata, cookie = P+0x1000, 0x9000000400000000, B+0x762270
    # Independent explicit ilhook byte vector; does not call production trampoline().
    code = PROLOG+b'\x48\xba'+struct.pack('<Q', userdata)+bytes.fromhex('4889e14883ec1048b8')
    code += struct.pack('<Q', callback)+bytes.fromhex('ffd04883c410')+EPILOG
    if far:
        moved = bytes.fromhex('5356574881ec9000000048895c24f048bb')+struct.pack('<Q', cookie)+bytes.fromhex('488b03488b5c24f0')
        prefix = b'\xff\x25\0\0\0\0'+struct.pack('<Q', target)
    else:
        moved = bytes.fromhex('5356574881ec90000000488b05')+struct.pack('<i', cookie-(target+267))
        prefix = b'\xe9'+struct.pack('<i', target-(entry+5))
    code += moved+b'\xff\x25\0\0\0\0'+struct.pack('<Q', entry+18)
    memory = {P+contract['stateRva']: state(), P+contract['tableRva']: struct.pack('<47Q', *(O+rva for rva in RVA)),
              P+contract['callbackRva']: contract['callbackCode'], target: code}
    memory.update({B+rva: raw for rva, raw in contract['sdkWindows']})
    original = contract['sdkWindows'][0][1]
    memory[entry] = prefix+original[len(prefix):]
    modules = {name: dict(base=base) for name, base in
               (('Astrolabe.dll', P), ('AccountPlatNative.dll', B), ('Astrolabe_orig.dll', O))}
    return contract, memory, modules, target


class CollectorTests(unittest.TestCase):
    def inspect(self, far=False, mutate=None, errors=None, after=None):
        contract, memory, modules, target = fixture(far)
        if mutate:
            mutate(contract, memory, target)
        calls = []; state_reads = 0
        def read(address, width):
            nonlocal state_reads
            calls.append((address, width))
            if errors and address in errors:
                return errors[address]
            if address == P+contract['stateRva']:
                state_reads += 1
                if state_reads == 2 and after is not None:
                    return after, None
            return memory[address], None
        result = s.collect(read, modules, contract, RVA)
        self.assertLessEqual(len(calls), 8)
        self.assertLessEqual(sum(width for _, width in calls), 1600)
        self.assertTrue(all(width <= 512 for _, width in calls))
        self.assertEqual(len(result['reads']), len(calls))
        return result, calls, target

    def test_near_and_far_full_independent_vectors_high64_exact_budget(self):
        for far, total, width, relocation in ((False, 1228, 281, 'near-rip'), (True, 1244, 297, 'far-rip-rbx')):
            result, calls, target = self.inspect(far)
            self.assertEqual(result['status'], 'installed-code-bound')
            self.assertEqual((len(calls), sum(size for _, size in calls), max(size for _, size in calls)), (8, total, 376))
            self.assertEqual(calls[5], (target, width))
            self.assertEqual(result['relocationKind'], relocation)
            self.assertTrue(result['fullTrampolineEqual'] and result['callbackEqual'] and result['returnEntryPlus18Verified'])
            self.assertTrue(result['stableStateEqual'] and result['userdataNonzero'])
            self.assertTrue(all(address > (1 << 63) for address, _ in calls))
            for name in ('naturalCallerObserved', 'targetFunctionsCalled', 'callbackCountRead', 'userdataDereferenced', 'atomicSnapshot', 'd02Passed'):
                self.assertFalse(result[name])
            self.assertFalse(any('bytesHex' in row or 'sha256' in row for row in result['reads']))

    def test_full_trampoline_every_segment_and_callback_inner_target_are_checked(self):
        # prolog, userdata, callback ABI, epilog1, epilog2, moved instruction, cookie, return edge.
        for offset in (0, 139, 158, 171, 172, 221, 250, 270, 296):
            def mutate(contract, memory, target):
                data = bytearray(memory[target]); data[offset] ^= 1; memory[target] = bytes(data)
            result, _, _ = self.inspect(True, mutate)
            self.assertEqual(result['status'], 'partial')
            self.assertFalse(result['fullTrampolineEqual'])
        def wrong_callback(contract, memory, target):
            data = bytearray(memory[P+0x1000]); data[17] ^= 1; memory[P+0x1000] = bytes(data)
        result, _, _ = self.inspect(mutate=wrong_callback)
        self.assertFalse(result['callbackEqual'])
        self.assertEqual(result['status'], 'partial')

    def test_userdata_must_be_nonzero_without_following_it(self):
        def mutate(contract, memory, target):
            data = bytearray(memory[target]); data[141:149] = bytes(8); memory[target] = bytes(data)
        result, calls, _ = self.inspect(mutate=mutate)
        self.assertFalse(result['userdataNonzero'])
        self.assertEqual(result['status'], 'partial')
        self.assertFalse(any(address == 0x9000000400000000 for address, _ in calls))

    def test_patch_opcode_near_tail_far_tail_and_distance_are_exact(self):
        for far, offset in ((False, 0), (False, 5), (True, 2), (True, 14)):
            def mutate(contract, memory, target):
                data = bytearray(memory[B+0x3950c0]); data[offset] ^= 1; memory[B+0x3950c0] = bytes(data)
            result, calls, _ = self.inspect(far, mutate)
            self.assertEqual(result['status'], 'partial')
            self.assertEqual(len(calls), 6)
        original = owned_contract()['sdkWindows'][0][1]
        patched = b'\xff\x25\0\0\0\0'+struct.pack('<Q', B+0x3950c0+200)+original[14:]
        with self.assertRaisesRegex(ValueError, 'distance choice'):
            s.patch_target(patched, original, B+0x3950c0)
        patched = b'\xe9'+struct.pack('<i', -0x80000000)+original[5:]
        with self.assertRaises(ValueError):
            s.patch_target(patched, original, B+0x3950c0)

    def test_failed_terminals_audit_only_fixed_original_windows(self):
        for failure in (101, 104, 112, 113):
            def mutate(contract, memory, target):
                memory[P+contract['stateRva']] = state(failure, 6)
                memory[B+0x3950c0] = contract['sdkWindows'][0][1]
            result, calls, _ = self.inspect(mutate=mutate)
            self.assertEqual(result['status'], 'failed-state-original-code-bound')
            self.assertEqual((len(calls), sum(width for _, width in calls)), (6, 909))
            self.assertFalse(any(name in result for name in ('patchEncoding', 'fullTrampolineEqual')))

    def test_nonterminal_invalid_state_and_all_installed_flags_refuse_code_follow(self):
        offsets = (0, 4, 12, 16, 20, 24, 32, 36, 40, 44, 48, 52, 56, 60)
        for offset in offsets:
            def mutate(contract, memory, target):
                data = bytearray(memory[P+contract['stateRva']]); data[offset] ^= 1
                memory[P+contract['stateRva']] = bytes(data)
            result, calls, _ = self.inspect(mutate=mutate)
            self.assertEqual(result['status'], 'partial')
            self.assertEqual(len(calls), 2)
        def waiting(contract, memory, target):
            memory[P+contract['stateRva']] = state(0, 2)
        result, calls, _ = self.inspect(mutate=waiting)
        self.assertEqual(result['stateKind'], 'nonterminal')
        self.assertEqual(len(calls), 2)

    def test_state_drift_short_reads_and_win32_failure_preserve_partial(self):
        result, _, _ = self.inspect(after=state(113, 6))
        self.assertFalse(result['stableStateEqual'])
        self.assertEqual(result['status'], 'partial')
        for address, failure in ((P+0x8b000, (bytes(63), None)), (P+0x8b1e0, (b'', {'winerror': 299})),
                (B+0x34ee6b, (bytes(75), None))):
            result, calls, _ = self.inspect(errors={address: failure})
            self.assertEqual(result['status'], 'partial')
            self.assertEqual(sum(1 for value, _ in calls if value == address), 1)
        result, calls, _ = self.inspect(errors={P+0x8b000: (bytes(64), {'winerror': 5})})
        self.assertEqual(len(calls), 1)

    def test_wrong_forward_target_or_caller_stops_before_unknown_trampoline(self):
        for address in (P+0x8b1e0, B+0x34ee6b, B+0x34ef3c):
            def mutate(contract, memory, target):
                raw = bytearray(memory[address]); raw[-1] ^= 1; memory[address] = bytes(raw)
            result, calls, _ = self.inspect(mutate=mutate)
            self.assertEqual(result['status'], 'partial')
            self.assertEqual(len(calls), 6)

    def test_jmp_distance_and_rip_relocation_distance_are_separate_contracts(self):
        contract = owned_contract(); entry = B+0x3950c0
        for delta in (0x7fffef00, 0x7ffff000):
            code, kind = s.trampoline(contract, entry+delta, B, P+0x1000, 1)
            self.assertEqual(kind, 'near-rip' if abs(delta+250) < 0x7ffff000 else 'far-rip-rbx')
            self.assertEqual(len(code), 281 if kind == 'near-rip' else 297)


class StaticArtifactTests(unittest.TestCase):
    def test_actual_frozen_production_callback_state_table_and_sources_without_loading(self):
        inventory = json.loads((s.ROOT/'local/migration72/d02-resume-20261108/initial-inventory.private.json').read_bytes())
        _, _, contract = s.audit_sources(Path(inventory['gamePath']))
        self.assertEqual((contract['stateRva'], contract['tableRva'], contract['callbackRva'], contract['innerRva']),
                         (0x8b000, 0x8b1e0, 0x1000, 0x9050))
        self.assertEqual(contract['prolog'], PROLOG)
        self.assertEqual(contract['epilog1']+contract['epilog2'], EPILOG)
        self.assertEqual(contract['callbackCode'], owned_contract()['callbackCode'])

    def test_callback_code_or_export_target_drift_does_not_find_valid_candidate(self):
        raw = s.PRODUCTION.read_bytes(); pe = s.clean.PE(raw)
        changed = bytearray(raw); changed[pe.offset(0x1000+17)] ^= 1
        with patch.object(s.key, 'require_artifact', return_value=dict(initialTargetTableRva=0x8b1e0)):
            with self.assertRaisesRegex(ValueError, 'callback'):
                s.artifact_contract(bytes(changed), raw, (PROLOG, EPILOG[:49], EPILOG[49:]))

    def test_source_pin_drift_rejected_before_any_artifact_or_process(self):
        with patch.object(s, 'digest', return_value='wrong'), self.assertRaisesRegex(ValueError, 'source/proof'):
            s.audit_sources(Path('.'))


class OwnedEmitterCheckerTests(unittest.TestCase):
    def report(self):
        contract, memory, _, target = fixture(True)
        source = 0x1803950c0
        rows = []
        for label, delta in (('near', 0x100000), ('far', 0x100000000)):
            destination, cookie = source+delta, source+18+0x3cd19e
            moved = bytes.fromhex('5356574881ec90000000')
            if label == 'near':
                moved += b'\x48\x8b\x05'+struct.pack('<i', cookie-(destination+17))
            else:
                moved += bytes.fromhex('48895c24f048bb')+struct.pack('<Q', cookie)+bytes.fromhex('488b03488b5c24f0')
            rows.append(dict(case=label, source=source, destination=destination, bytes=len(moved), codeHex=moved.hex()))
        original = s.SDK_PREFIX+b'\x90'*235
        patched = memory[B+0x3950c0][:14]+original[14:]
        code = bytearray(memory[target]); code[141:149] = struct.pack('<Q', 0x123456789abcdef0)
        return dict(schema=1, scope='owned generated code only', original18Hex=s.SDK_PREFIX.hex(), movedCases=rows,
            ownedHook=dict(entry=B+0x3950c0, callback=P+0x1000, userdata=0x123456789abcdef0, trampoline=target,
                patchKind='far', originalHex=original.hex(), patchedHex=patched.hex(), trampolineBytes=len(code),
                trampolineHex=code.hex(), dropRestored=True),
            sdkLoaded=False, calleeExecuted=False, callbackExecuted=False, cookieDereferenced=False)

    def test_original_emitter_mover_and_actual_hook_full_code_comparison(self):
        result = s.verify_owned_emitter(self.report(), owned_contract())
        self.assertTrue(result['actualOwnedFullTrampolineEqual'])
        self.assertEqual([row['bytes'] for row in result['originalMoverCases']], [17, 33])
        self.assertEqual(result['actualTrampolineBytes'], 297)

    def test_owned_emitter_wrong_encoding_or_execution_claim_is_rejected(self):
        for mutation in ('mover', 'hook', 'original', 'drop', 'boundary', 'address'):
            report = self.report()
            if mutation == 'mover': report['movedCases'][0]['codeHex'] = '00'*17
            elif mutation == 'hook': report['ownedHook']['trampolineHex'] = '00'*297
            elif mutation == 'original': report['original18Hex'] = '00'*18
            elif mutation == 'drop': report['ownedHook']['dropRestored'] = False
            elif mutation == 'boundary': report['calleeExecuted'] = True
            else: report['movedCases'][1]['destination'] += 1
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                s.verify_owned_emitter(report, owned_contract())

    def test_offline_cli_never_creates_backend_or_requires_ready(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); report = root/'owned.json'; output = root/'checked.json'
            report.write_text(json.dumps(self.report()), encoding='utf-8')
            argv = ['observer', '--client', str(root), '--output', str(output), '--owned-emitter-report', str(report), '--check-owned-emitter']
            with patch.object(s, 'RAW', root), patch.object(s, 'audit_sources', return_value=(b'', b'', owned_contract())), \
                    patch.object(s.clean, 'ModuleBackend') as backend, patch('sys.argv', argv), contextlib.redirect_stdout(io.StringIO()):
                s.main()
            backend.assert_not_called()
            self.assertEqual(json.loads(output.read_bytes())['status'], 'completed')


class BackendTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.game = Path(self.temp.name); (self.game/'YuanShen.exe').write_bytes(b'owned image')
        plugins = self.game/'YuanShen_Data/Plugins'; plugins.mkdir(parents=True)
        self.modules = []
        for name, base, raw in (('Astrolabe.dll', P, b'proxy'), ('Astrolabe_orig.dll', O, b'stock'), ('AccountPlatNative.dll', B, b'sdk')):
            path = plugins/name; path.write_bytes(raw)
            self.modules.append(dict(name=name, base=base, size=0x1000, path=str(path)))
        self.evidence = dict(sourceSHA256=s.clean.sha256(Path(s.__file__)), elapsedSeconds=90.1, pid=6173,
            image=str(self.game/'YuanShen.exe'), launch=dict(executable=str(self.game/'YuanShen.exe')),
            finalStatus=dict(alive=True), processCreationTime=dict(filetime=0x1234567890ab, winerror=None),
            samples=[dict(modules=dict(error=None, items=copy.deepcopy(self.modules)))])
        self.backend = Mock(); self.backend.k.OpenProcess.return_value = 79
        self.backend.creation_time.return_value = self.evidence['processCreationTime']
        self.backend.image.return_value = str(self.game/'YuanShen.exe')
        self.backend.status.return_value = dict(alive=True)
        self.backend.snapshot.return_value = dict(error=None, items=copy.deepcopy(self.modules))
        self.contract, self.memory, _, _ = fixture()
        def rpm(handle, address, buffer, width, count):
            raw = self.memory[address]; C.memmove(buffer, raw, width)
            C.cast(count, C.POINTER(C.c_size_t))[0] = len(raw)
            return True
        self.backend.k.ReadProcessMemory.side_effect = rpm
        self.pe = patch.object(s.clean, 'PE', return_value=SimpleNamespace(image_size=0x1000, exports=lambda: [dict(rva=rva) for rva in RVA]))
        self.pe.start(); self.addCleanup(self.pe.stop)

    def run_inspect(self):
        return s.inspect_process(self.backend, self.game, self.evidence, b'proxy', b'stock', b'sdk', self.contract)

    def test_rights_samefile_extended_prefix_and_handle_close(self):
        self.backend.snapshot.return_value['items'][0]['path'] = '\\\\?\\'+self.modules[0]['path']
        result = self.run_inspect()
        self.assertEqual(result['status'], 'installed-code-bound')
        self.backend.k.OpenProcess.assert_called_once_with(0x101010, False, 6173)
        self.backend.close.assert_called_once_with(79)
        self.assertEqual(self.backend.k.ReadProcessMemory.call_count, 8)

    def test_source_short_monitor_and_pid_creation_drift_before_first_rpm(self):
        for mutation in ('source', 'duration', 'creation'):
            evidence = copy.deepcopy(self.evidence)
            if mutation == 'source': self.evidence['sourceSHA256'] = 'wrong'
            elif mutation == 'duration': self.evidence['elapsedSeconds'] = 89.9
            else: self.backend.creation_time.return_value = dict(filetime=1)
            with self.assertRaises(ValueError): self.run_inspect()
            self.backend.k.ReadProcessMemory.assert_not_called()
            self.evidence = evidence

    def test_each_module_base_size_path_and_enumeration_drift_before_rpm(self):
        for mutation in ('base', 'size', 'path', 'missing', 'enumeration'):
            snapshot = dict(error=None, items=copy.deepcopy(self.modules))
            if mutation in ('base', 'size'): snapshot['items'][1][mutation] += 1
            elif mutation == 'path': snapshot['items'][1]['path'] = self.modules[0]['path']
            elif mutation == 'missing': snapshot['items'].pop()
            else: snapshot['error'] = 5
            self.backend.snapshot.return_value = snapshot
            with self.assertRaises(ValueError): self.run_inspect()
            self.backend.k.ReadProcessMemory.assert_not_called()
        self.assertEqual(self.backend.close.call_count, 5)

    def test_exit_after_collect_is_partial_and_handle_closed(self):
        self.backend.status.side_effect = [dict(alive=True), dict(alive=False)]
        result = self.run_inspect()
        self.assertEqual(result['status'], 'partial')
        self.backend.close.assert_called_once()


if __name__ == '__main__':
    unittest.main()
