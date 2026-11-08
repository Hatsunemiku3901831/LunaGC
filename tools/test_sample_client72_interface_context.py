"""Owned sparse-memory and PE fixtures only; no process attach or client launch."""
import copy
import ctypes as C
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools import sample_client72_interface_context as s

B, CACHE, REG_SLOT, REGISTRY = 0x18000000000, 0x22000000000, 0x33000000000, 0x44000000000
HEADERS = (0x55000000000, 0x66000000000)
META = {1169: bytes(range(70)), 1249: bytes(range(80, 150))}


def q(value):
    return struct.pack('<Q', value)


def fixture(compacts=False):
    memory = {B+0x5bc8f18: q(CACHE), B+0x5bc8ad0: q(REG_SLOT), REG_SLOT: q(REGISTRY),
              B+0x5eef080: struct.pack('<I', 0xf1234567)}
    for n, ((index, slot, _, _), address) in enumerate(zip(s.CLASSES, HEADERS)):
        header = bytearray([0xd7]*208)
        struct.pack_into('<Q', header, 0x10, index)
        struct.pack_into('<I', header, 0x18, (18 << 16) | 0x34)
        struct.pack_into('<Q', header, 0x58, address+0x1000)
        for offset, compact in ((0xb0, 0xabc100+0x100*n), (0xa8, 0xdef200+0x100*n)):
            struct.pack_into('<I', header, offset, compact if compacts else 0)
            memory[REGISTRY+compact+0x10] = bytes(range(31+n, 47+n))
        memory.update({CACHE+slot: q(address), address: bytes(header), address+0x1000: META[index]})
    return memory


class CollectorTests(unittest.TestCase):
    def run_fixture(self, memory, failures=None):
        calls, failures = [], failures or {}
        def read(address, width):
            calls.append((address, width))
            if address in failures:
                item = failures[address]
                if isinstance(item, OSError):
                    raise item
                return item
            return memory[address], None
        result = s.collect(read, B, META)
        self.assertEqual(len(calls), len(result['records']))
        self.assertLessEqual(len(calls), 14)
        self.assertLessEqual(sum(width for _, width in calls), 664)
        self.assertTrue(all(width <= 208 for _, width in calls))
        return result, calls

    def test_high64_zero_parent_and_exact_minimum(self):
        result, calls = self.run_fixture(fixture())
        self.assertEqual((len(calls), sum(w for _, w in calls), max(w for _, w in calls)), (8, 584, 208))
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(result['cacheRoot'], CACHE)
        self.assertFalse(result['registryAttempted'])
        self.assertEqual(result['tlsIndex']['value'], 0xf1234567)
        self.assertEqual(calls[1], (CACHE+0x2488, 8))
        self.assertEqual(calls[4], (CACHE+0x2708, 8))
        for row in result['classes']:
            self.assertIsNone(row['staticParent'])
            self.assertEqual(row['staticInterfaceCount'], 0)
            self.assertTrue(row['metadataMatchesFixed'])
            self.assertEqual(row['compactOffsets'], dict(parent=0, declaring=0))
            self.assertEqual({x['status'] for x in row['descriptors'].values()}, {'zero-skipped'})
        for name in ('atomicSnapshot', 'completeColdComparer', 'completeClassInitialization',
                     'completeAvatarHandler', 'targetProtocolReady', 'd02Complete'):
            self.assertFalse(result[name])
        self.assertFalse(result['tlsIndex']['allocationThreadProved'])
        self.assertFalse(result['tlsIndex']['threadOrTlsArrayRead'])

    def test_nonzero_compacts_full_exact_budget_shared_registry(self):
        result, calls = self.run_fixture(fixture(True))
        self.assertEqual((len(calls), sum(w for _, w in calls)), (14, 664))
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(result['registryRoot'], REGISTRY)
        self.assertEqual(calls.count((B+0x5bc8ad0, 8)), 1)
        self.assertEqual(calls.count((REG_SLOT, 8)), 1)
        for row in result['classes']:
            for descriptor in row['descriptors'].values():
                self.assertEqual(descriptor['address'], REGISTRY+descriptor['compact']+0x10)
                self.assertEqual(descriptor['status'], 'read-uninterpreted')
                self.assertEqual(descriptor['semanticIdentity'], 'unknown')

    def test_cache_zero_vs_failed_preserved_tls_independent(self):
        for value, failures, expected, root in (
                (q(0), {}, 'cache-null', 0),
                (q(CACHE), {B+0x5bc8f18: (b'', {'winerror': 5})}, 'cache-unreadable', None)):
            with self.subTest(expected=expected):
                memory = fixture(); memory[B+0x5bc8f18] = value
                result, calls = self.run_fixture(memory, failures)
                self.assertEqual([x['status'] for x in result['classes']], [expected]*2)
                self.assertEqual(result['cacheRoot'], root)
                self.assertEqual(calls, [(B+0x5bc8f18, 8), (B+0x5eef080, 4)])
                self.assertEqual(result['status'], 'partial')
                self.assertEqual(result['tlsIndex']['status'], 'read')

    def test_nullable_entry_does_not_read_null_header(self):
        memory = fixture(); memory[CACHE+0x2488] = q(0)
        result, calls = self.run_fixture(memory)
        self.assertEqual(result['classes'][0]['status'], 'entry-null')
        self.assertEqual(result['classes'][0]['headerAddress'], 0)
        self.assertEqual(result['classes'][1]['status'], 'read')
        self.assertEqual((len(calls), sum(w for _, w in calls)), (6, 306))
        self.assertNotIn((0, 208), calls)

    def test_short_and_failed_entry_are_unknown(self):
        for failure in ((q(HEADERS[0])[:7], None), (q(HEADERS[0]), {'winerror': 299})):
            result, calls = self.run_fixture(fixture(), {CACHE+0x2488: failure})
            self.assertEqual(result['classes'][0]['status'], 'entry-unreadable')
            self.assertIsNone(result['classes'][0]['headerAddress'])
            self.assertNotIn((HEADERS[0], 208), calls)

    def test_header_short_oserror_and_identity_stop_before_metadata(self):
        for failure in ((bytes(207), None), OSError(5, 'owned denied')):
            result, calls = self.run_fixture(fixture(), {HEADERS[0]: failure})
            self.assertEqual(result['classes'][0]['status'], 'header-unreadable')
            self.assertNotIn((HEADERS[0]+0x1000, 70), calls)
        for offset, value in ((0x10, q((1 << 40)+1169)), (0x18, struct.pack('<I', 28 << 16))):
            memory = fixture(); header = bytearray(memory[HEADERS[0]])
            header[offset:offset+len(value)] = value; memory[HEADERS[0]] = bytes(header)
            result, calls = self.run_fixture(memory)
            self.assertEqual(result['classes'][0]['status'], 'identity-mismatch')
            self.assertNotIn((HEADERS[0]+0x1000, 70), calls)

    def test_metadata_null_short_error_and_mismatch_stop_descriptors(self):
        for mode in ('null', 'short', 'error', 'mismatch'):
            memory, failures = fixture(True), {}
            if mode == 'null':
                header = bytearray(memory[HEADERS[0]]); struct.pack_into('<Q', header, 0x58, 0)
                memory[HEADERS[0]] = bytes(header)
            elif mode == 'short':
                failures[HEADERS[0]+0x1000] = (META[1169][:-1], None)
            elif mode == 'error':
                failures[HEADERS[0]+0x1000] = (META[1169], {'winerror': 5})
            else:
                memory[HEADERS[0]+0x1000] = bytes(70)
            result, _ = self.run_fixture(memory, failures)
            expected = {'null': 'metadata-null', 'short': 'metadata-unreadable',
                        'error': 'metadata-unreadable', 'mismatch': 'metadata-mismatch'}[mode]
            self.assertEqual(result['classes'][0]['status'], expected)
            self.assertEqual(result['classes'][1]['status'], 'read')
            self.assertNotIn('interface.class1169.parentDescriptor', [r['name'] for r in result['records']])

    def test_registry_null_and_errors_once_without_descriptors(self):
        for root, failures, expected in ((0, {}, 'registry-null'),
                (REG_SLOT, {B+0x5bc8ad0: (b'', {'winerror': 5})}, 'registry-unreadable'),
                (REG_SLOT, {REG_SLOT: (b'\x12', None)}, 'registry-unreadable')):
            memory = fixture(True); memory[B+0x5bc8ad0] = q(root)
            result, calls = self.run_fixture(memory, failures)
            self.assertEqual(calls.count((B+0x5bc8ad0, 8)), 1)
            self.assertEqual(result['status'], 'partial')
            self.assertFalse(any(width == 16 for _, width in calls))
            for row in result['classes']:
                self.assertEqual({x['status'] for x in row['descriptors'].values()}, {expected})
        memory = fixture(True); memory[REG_SLOT] = q(0)
        result, _ = self.run_fixture(memory)
        self.assertEqual(result['registryRoot'], 0)

    def test_descriptor_failure_not_inferred_or_retried(self):
        address = REGISTRY+0xabc100+0x10
        result, calls = self.run_fixture(fixture(True), {address: (bytes(15), None)})
        self.assertEqual(result['classes'][0]['descriptors']['parent']['status'], 'descriptor-unreadable')
        self.assertEqual(result['classes'][0]['status'], 'descriptor-unavailable')
        self.assertEqual(calls.count((address, 16)), 1)
        self.assertEqual(result['classes'][1]['status'], 'read')

    def test_tls_zero_is_observed_but_failed_not_zero(self):
        memory = fixture(); memory[B+0x5eef080] = bytes(4)
        result, _ = self.run_fixture(memory)
        self.assertEqual(result['tlsIndex']['value'], 0)
        self.assertEqual(result['status'], 'completed')
        result, _ = self.run_fixture(memory, {B+0x5eef080: (bytes(3), None)})
        self.assertIsNone(result['tlsIndex']['value'])
        self.assertEqual(result['status'], 'partial')

    def test_budget_rejects_before_read_and_pointer_overflow_records_failure(self):
        read = Mock(return_value=(b'x'*208, None)); budget = s.ReadBudget(read)
        with self.assertRaisesRegex(ValueError, 'budget'):
            budget.get('oversize', B, 209)
        self.assertEqual(read.call_count, 0)
        budget.get('overflow', (1 << 64)-4, 8)
        self.assertEqual(read.call_count, 0)
        self.assertEqual(budget.records[0]['error'], 'address-outside-uint64')
        budget = s.ReadBudget(Mock(return_value=(bytes(4), None)))
        for _ in range(14):
            budget.get('four', B, 4)
        with self.assertRaisesRegex(ValueError, 'budget'):
            budget.get('fifteenth', B, 4)
        budget = s.ReadBudget(read)
        for _ in range(3):
            budget.get('full', B, 208)
        with self.assertRaisesRegex(ValueError, 'budget'):
            budget.get('total-overflow', B, 41)


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.game = self.root/'game'; self.game.mkdir()
        self.target = self.game/'YuanShen.exe'; self.target.write_bytes(b'owned exe')
        (self.game/'metadata.dat').write_bytes(b'owned metadata')
        profile = self.root/'protocol/cn-7.2/profile.json'; profile.parent.mkdir(parents=True)
        profile.write_text(json.dumps({'files': {'metadata': {'path': 'metadata.dat'}}}))
        self.module = dict(name='YuanShen.exe', path=str(self.target), base=B, size=0x20000000)
        self.modules = dict(error=None, items=[self.module])
        self.inputs = dict(profileSHA256='owned', files={'binary': 'owned'},
                           config={name: dict(exists=False) for name in ('.env', '.env.local', 'config.json')})
        self.source = dict(status='completed', finalStatus=dict(alive=True), elapsedSeconds=90.1,
            sourceSHA256=s.MONITOR_SHA, pid=9123, processCreationTime=dict(filetime=0x123456789abc, winerror=None),
            launch=dict(cwd=str(self.game), executable=str(self.target)), image=str(self.target), inputs=self.inputs,
            samples=[dict(modules=self.modules)])
        self.observation = self.root/'owned-observation.json'
        backend = self.backend = Mock()
        backend.k.OpenProcess.return_value = 77
        backend.creation_time.return_value = self.source['processCreationTime']
        backend.image.return_value = str(self.target)
        backend.status.return_value = dict(alive=True)
        backend.snapshot.return_value = copy.deepcopy(self.modules)
        memory = fixture()
        def rpm(handle, address, buffer, width, count):
            raw = memory[address]; C.memmove(buffer, raw, width)
            C.cast(count, C.POINTER(C.c_size_t))[0] = len(raw)
            return True
        backend.k.ReadProcessMemory.side_effect = rpm
        for obj in (patch.object(s, 'ROOT', self.root),
                    patch.object(s, 'verified_inputs', return_value=copy.deepcopy(self.inputs)),
                    patch.object(s, 'verify_contracts', return_value=(META, dict(imageSize=self.module['size']))),
                    patch.object(s, 'WindowsBackend', return_value=backend)):
            obj.start(); self.addCleanup(obj.stop)

    def run_execute(self):
        self.observation.write_text(json.dumps(self.source))
        return s.execute(self.observation)

    def reject(self, expected):
        with self.assertRaisesRegex(ValueError, expected):
            self.run_execute()
        self.backend.k.ReadProcessMemory.assert_not_called()

    def test_win32_contract_rights_high_base_and_handle_lifecycle(self):
        result = self.run_execute()
        self.backend.k.OpenProcess.assert_called_once_with(0x101010, False, 9123)
        self.backend.close.assert_called_once_with(77)
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(result['mainModule']['base'], B)
        self.assertEqual(self.backend.k.ReadProcessMemory.call_count, 8)

    def test_extended_windows_path_is_same_file_identity(self):
        extended = '\\\\?\\'+str(self.target.resolve())
        self.assertTrue(s.same_file(extended, self.target))
        self.source['image'] = extended
        self.source['samples'][0]['modules']['items'][0]['path'] = extended
        self.backend.image.return_value = extended
        self.assertEqual(self.run_execute()['status'], 'completed')

    def test_monitor_short_run_or_wrong_source_rejected_before_open(self):
        self.source['elapsedSeconds'] = 89.99
        self.reject('90-second'); self.backend.k.OpenProcess.assert_not_called()
        self.source['elapsedSeconds'] = 90
        self.source['sourceSHA256'] = 'wrong'
        self.reject('source'); self.backend.k.OpenProcess.assert_not_called()

    def test_profile_config_or_pe_drift_rejected_before_open(self):
        self.source['inputs'] = copy.deepcopy(self.inputs)
        self.source['inputs']['profileSHA256'] = 'wrong'
        self.reject('profile'); self.backend.k.OpenProcess.assert_not_called()
        self.source['inputs'] = copy.deepcopy(self.inputs)
        self.source['inputs']['config']['.env'] = dict(exists=True, sha256='wrong')
        self.reject('configuration'); self.backend.k.OpenProcess.assert_not_called()
        self.source['inputs'] = self.inputs
        with patch.object(s, 'verify_contracts', side_effect=ValueError('Fixed PE code window mismatch')):
            self.reject('PE code window'); self.backend.k.OpenProcess.assert_not_called()

    def test_pid_reuse_image_or_exit_refuses_all_rpm_closes_handle(self):
        for field in ('creation', 'image', 'exit'):
            with self.subTest(field=field):
                self.backend.creation_time.return_value = self.source['processCreationTime']
                self.backend.image.return_value = str(self.target)
                self.backend.status.return_value = dict(alive=True)
                if field == 'creation':
                    self.backend.creation_time.return_value = dict(filetime=1, winerror=None)
                elif field == 'image':
                    other = self.game/'other.exe'; other.write_bytes(b'owned other')
                    self.backend.image.return_value = str(other)
                else:
                    self.backend.status.return_value = dict(alive=False)
                self.reject('identity|image or lifetime')
        self.assertEqual(self.backend.close.call_count, 3)

    def test_module_base_size_file_and_enumeration_drift_refuse_rpm(self):
        for mutation in ('base', 'size', 'path', 'error', 'duplicate'):
            with self.subTest(mutation=mutation):
                modules = copy.deepcopy(self.modules)
                if mutation in ('base', 'size'):
                    modules['items'][0][mutation] += 1
                elif mutation == 'path':
                    other = self.game/'other.exe'; other.write_bytes(b'owned other')
                    modules['items'][0]['path'] = str(other)
                elif mutation == 'error':
                    modules['error'] = 5
                else:
                    modules['items'].append(copy.deepcopy(self.module))
                self.backend.snapshot.return_value = modules
                self.reject('module')
        self.assertEqual(self.backend.close.call_count, 5)

    def test_exit_after_collect_retains_partial_and_closes(self):
        self.backend.status.side_effect = [dict(alive=True), dict(alive=False)]
        result = self.run_execute()
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(len(result['records']), 8)
        self.backend.close.assert_called_once()

    def test_private_output_new_only_and_game_excluded(self):
        raw = self.root/'raw'; raw.mkdir()
        with patch.object(s, 'OUTPUT_ROOT', raw):
            self.assertEqual(s.output_path(raw/'new.json', self.game), raw/'new.json')
            existing = raw/'existing.json'; existing.write_bytes(b'owned')
            for path in (existing, self.root/'elsewhere.json', raw):
                with self.assertRaises(ValueError):
                    s.output_path(path, self.game)
        with patch.object(s, 'OUTPUT_ROOT', self.game):
            with self.assertRaises(ValueError):
                s.output_path(self.game/'raw.json', self.game)


class FixedSourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        pins = {'owned-reader.py': s.digest(b'owned reader')}
        (self.root/'owned-reader.py').write_bytes(b'owned reader')
        binary = bytearray(0x14000)
        binary[:2] = b'MZ'; struct.pack_into('<I', binary, 0x3c, 0x80)
        binary[0x80:0x84] = b'PE\0\0'; struct.pack_into('<HH', binary, 0x84, 0x8664, 3)
        struct.pack_into('<H', binary, 0x94, 0xf0); struct.pack_into('<H', binary, 0x98, 0x20b)
        struct.pack_into('<Q', binary, 0xb0, 0x140000000)
        struct.pack_into('<I', binary, 0x98+56, 0x20000000)
        struct.pack_into('<II', binary, 0x98+112+9*8, 0x3a8dab0, 40)
        for i, (rva, raw, width) in enumerate(((0x1000, 0x400, 0x100),
                (0x3529000, 0x1000, 0x11000), (0x3a8d000, 0x12000, 0x1000))):
            offset = 0x188+i*40
            struct.pack_into('<III', binary, offset+12, rva, width, raw)
            struct.pack_into('<I', binary, offset+36, 0x60000020 if i == 0 else 0x40000040)
        binary[0x400:0x410] = bytes(range(16))
        struct.pack_into('<Q', binary, 0x12000+0xab0+16, 0x145eef080)
        self.tls_index_file = 0x12000+0xab0+16
        contract = dict(classes=[])
        self.metadata = bytearray(256); classes = []
        for n, (index, slot, _, _) in enumerate(s.CLASSES):
            data = META[index]; file = 16+n*80; self.metadata[file:file+70] = data
            address = (0x143529ee0, 0x143539fa0)[n]
            descriptor = q(index)+struct.pack('<II', 18 << 16, 0)
            offset = 0x1000+address-0x143529000
            binary[offset:offset+16] = descriptor
            classes.append((index, slot, file, s.digest(data)))
            contract['classes'].append(dict(definition=index, cacheOffset=hex(slot), headerIdentity=dict(data=index, kind=18),
                parent=None, metadata=dict(interfaceCount=0, fileOffset=hex(file), bytesHex=data.hex()),
                fixedDescriptor=dict(start=hex(address), bytesHex=descriptor.hex(), sha256=s.digest(descriptor))))
        self.binary, self.contract = binary, contract
        report = json.dumps(dict(nextSamplingContract=contract)).encode()
        raw = json.dumps(contract).encode()
        for path, data in ((s.CONTRACT, raw), (s.ANCESTOR_REPORT, report)):
            target = self.root/path; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data)
        for obj in (patch.object(s, 'ROOT', self.root), patch.object(s, 'SOURCE_PINS', pins),
                    patch.object(s, 'CONTRACT_SHA', s.digest(raw)), patch.object(s, 'ANCESTOR_SHA', s.digest(report)),
                    patch.object(s, 'WINDOWS', ((0x1000, 16, s.digest(bytes(range(16)))),)),
                    patch.object(s, 'CLASSES', tuple(classes))):
            obj.start(); self.addCleanup(obj.stop)

    def test_fixed_pe_metadata_descriptors_and_tls_directory(self):
        metadata, checks = s.verify_contracts(self.binary, self.metadata)
        self.assertEqual(metadata, META)
        self.assertEqual(checks['tlsDirectory']['addressOfIndexRVA'], '0x5eef080')
        self.assertEqual(checks['imageSize'], 0x20000000)

    def test_source_contract_and_origin_report_hash_failures(self):
        for path in ('owned-reader.py', s.CONTRACT, s.ANCESTOR_REPORT):
            target = self.root/path; original = target.read_bytes(); target.write_bytes(original+b' ')
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, 'hash mismatch'):
                s.verify_contracts(self.binary, self.metadata)
            target.write_bytes(original)

    def test_code_metadata_descriptor_tls_directory_and_index_drift(self):
        for offset in (0x400, 0x1000+0xee0, self.tls_index_file, 0x98+112+9*8):
            binary = bytearray(self.binary); binary[offset] ^= 1
            with self.subTest(offset=offset), self.assertRaises(ValueError):
                s.verify_contracts(binary, self.metadata)
        metadata = bytearray(self.metadata); metadata[16] ^= 1
        with self.assertRaisesRegex(ValueError, 'metadata mismatch'):
            s.verify_contracts(self.binary, metadata)

    def test_origin_projection_mismatch_even_if_new_file_hash_matches(self):
        path = self.root/s.CONTRACT; contract = copy.deepcopy(self.contract)
        contract['classes'][0]['parent'] = 123
        raw = json.dumps(contract).encode(); path.write_bytes(raw)
        with patch.object(s, 'CONTRACT_SHA', s.digest(raw)), self.assertRaisesRegex(ValueError, 'projection'):
            s.verify_contracts(self.binary, self.metadata)


if __name__ == '__main__':
    unittest.main()
