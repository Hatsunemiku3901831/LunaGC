import unittest
import ctypes as C
from ctypes import wintypes as W
import os
from unittest.mock import Mock, patch
from pathlib import Path
from tempfile import TemporaryDirectory
import json

from tools.migration72 import proud_actual_adoption_hot_equality_sampler as sampler


class HotEqualityReaderTests(unittest.TestCase):
    def fixture(self, n=3):
        base, q, s, o, k = 0x180000000, 0x900000000100, 0x900000000200, 0x900000000300, 0x900000000400
        f, m = base + 0x1234, 0xf123456789abcdef
        addresses = [base+0x594e6f0, q+0x38, s, o, k+0xbe, k+0x110, k+0x110+n*8]
        values = [q, s, o, k, n, f, m if n else f]
        widths = [8, 8, 8, 8, 2, 8, 8]
        memory = {a: v.to_bytes(w, 'little') for a, v, w in zip(addresses, values, widths)}
        calls = []
        def read(address, width):
            calls.append((address, width))
            return memory[address], None
        return base, addresses, memory, calls, read

    def test_seven_reads_and_full_width_values(self):
        base, addresses, _, calls, read = self.fixture()
        result = sampler.collect(read, base, 0x6000000)
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(result['values']['M'], 0xf123456789abcdef)
        self.assertEqual([a for a, _ in calls], addresses)
        self.assertEqual((result['reads'], result['requestedBytes']), (7, 50))
        self.assertEqual(result['functionLocation'], 'fixed-exe')

    def test_each_null_stops_without_downstream_reads(self):
        for i in (0, 1, 2, 3, 5, 6):
            base, addresses, memory, calls, read = self.fixture()
            memory[addresses[i]] = bytes(8)
            result = sampler.collect(read, base, 0x6000000)
            self.assertEqual(result['status'], 'partial')
            self.assertEqual(len(calls), i+1)
            self.assertEqual(result['records'][-1]['bytesHex'], '00'*8)

    def test_each_failed_or_short_read_stops_and_keeps_bytes(self):
        for i in range(7):
            for mode in ('short', 'error'):
                base, addresses, memory, calls, read = self.fixture()
                def failing(address, width):
                    if address == addresses[i]:
                        calls.append((address, width))
                        return b'\xa5', None if mode == 'short' else {'winerror': 299}
                    return read(address, width)
                result = sampler.collect(failing, base, 0x6000000)
                self.assertEqual(result['status'], 'partial')
                self.assertEqual(len(calls), i+1)
                self.assertEqual(result['records'][-1]['bytesHex'], 'a5')
                self.assertIsNotNone(result['records'][-1]['error'])

    def test_zero_and_max_u16_count(self):
        for n in (0, 65535):
            base, addresses, _, calls, read = self.fixture(n)
            result = sampler.collect(read, base, 0x6000000)
            self.assertEqual(result['values']['N'], n)
            self.assertEqual(calls[-1][0], addresses[-1])
            self.assertEqual(result['reads'], 7)

    def test_function_outside_fixed_image_remains_unknown(self):
        base, addresses, memory, _, read = self.fixture()
        memory[addresses[5]] = (0xf23456789abcdef0).to_bytes(8, 'little')
        result = sampler.collect(read, base, 0x6000000)
        self.assertEqual(result['functionLocation'], 'unknown')
        self.assertEqual(result['values']['F'], 0xf23456789abcdef0)

    def test_overflow_address_records_failure_without_callback(self):
        base, addresses, memory, calls, read = self.fixture()
        memory[addresses[0]] = (2**64-0x10).to_bytes(8, 'little')
        result = sampler.collect(read, base, 0x6000000)
        self.assertEqual(len(calls), 1)
        self.assertEqual(result['records'][-1]['error'], 'address-outside-uint64')

    def test_epoch_mismatch_stops_before_read(self):
        with TemporaryDirectory() as directory:
            game = Path(directory)
            target = game/'YuanShen.exe'
            target.write_bytes(b'fixture')
            module = dict(name='YuanShen.exe', path=str(target), base=0x180000000, size=0x6000000)
            inputs = dict(files=dict(binary=dict(path='YuanShen.exe')))
            source = dict(task='R3-PROUD-HOT-EQUALITY-IDENTITY', status='completed', finalStatus=dict(alive=True),
                          sourceSHA256=sampler.sha256(Path(sampler.__file__)), image=str(target), inputChecks=inputs,
                          mainModule=module, pid=42, creation=dict(filetime=123, winerror=None))
            observation = game/'observation.json'
            observation.write_text(json.dumps(source), encoding='utf-8')
            for changed in ('creation', 'module', 'inputs'):
                backend = Mock()
                backend.k.OpenProcess.return_value = 1
                backend.creation_time.return_value = dict(filetime=124 if changed == 'creation' else 123, winerror=None)
                backend.image.return_value = str(target)
                backend.status.return_value = dict(alive=True)
                live = dict(module, base=module['base']+1 if changed == 'module' else module['base'])
                backend.snapshot.return_value = dict(error=None, items=[live])
                actual_inputs = dict(files=dict(binary=dict(path='YuanShen.exe')), changed=True) if changed == 'inputs' else inputs
                with patch.object(sampler.subprocess, 'check_output', return_value=str(game)), \
                     patch.object(sampler, 'verified_inputs', return_value=actual_inputs), \
                     patch.object(sampler, 'verify_sources', return_value=dict(imageSize=module['size'])), \
                     patch.object(sampler, 'WindowsBackend', return_value=backend):
                    with self.assertRaises(ValueError):
                        sampler.execute(observation, {})
                backend.k.ReadProcessMemory.assert_not_called()

    def test_identity_uses_only_query_handle_and_no_read(self):
        with TemporaryDirectory() as directory:
            game = Path(directory)
            target = game/'YuanShen.exe'
            target.write_bytes(b'fixture')
            module = dict(name='YuanShen.exe', path=str(target), base=0x180000000, size=0x6000000)
            inputs = dict(files=dict(binary=dict(path='YuanShen.exe')))
            backend = Mock()
            backend.k.OpenProcess.return_value = 1
            backend.creation_time.return_value = dict(filetime=123, winerror=None)
            backend.image.return_value = str(target)
            backend.status.return_value = dict(alive=True)
            backend.snapshot.return_value = dict(error=None, items=[module])
            result = {}
            with patch.object(sampler.subprocess, 'check_output', return_value=str(game)), \
                 patch.object(sampler, 'verified_inputs', return_value=inputs), \
                 patch.object(sampler, 'verify_sources', return_value=dict(imageSize=module['size'])), \
                 patch.object(sampler, 'WindowsBackend', return_value=backend):
                sampler.identify(42, result)
            backend.k.OpenProcess.assert_called_once_with(0x101000, False, 42)
            backend.k.ReadProcessMemory.assert_not_called()
            self.assertEqual(result['status'], 'completed')
            self.assertEqual(result['mainModule'], module)

    @unittest.skipUnless(os.name == 'nt' and os.environ.get('PROUD_SAMPLER_SELF_TEST') == '1',
                         'explicit Windows self-process test only')
    def test_win32_self_process_read(self):
        kernel = C.WinDLL('kernel32', use_last_error=True)
        kernel.GetCurrentProcessId.restype = W.DWORD
        kernel.OpenProcess.argtypes = [W.DWORD, W.BOOL, W.DWORD]
        kernel.OpenProcess.restype = W.HANDLE
        kernel.ReadProcessMemory.argtypes = [W.HANDLE, C.c_void_p, C.c_void_p, C.c_size_t, C.POINTER(C.c_size_t)]
        kernel.ReadProcessMemory.restype = W.BOOL
        kernel.CloseHandle.argtypes = [W.HANDLE]
        kernel.CloseHandle.restype = W.BOOL
        cell, q, storage, obj, klass = (C.create_string_buffer(size) for size in (8, 0x40, 8, 8, 0x120))
        def put(buffer, offset, value, width):
            C.memmove(C.addressof(buffer)+offset, value.to_bytes(width, 'little'), width)
        put(cell, 0, C.addressof(q), 8)
        put(q, 0x38, C.addressof(storage), 8)
        put(storage, 0, C.addressof(obj), 8)
        put(obj, 0, C.addressof(klass), 8)
        put(klass, 0xbe, 1, 2)
        put(klass, 0x110, 0x123456789abcdef0, 8)
        put(klass, 0x118, 0xfedcba9876543210, 8)
        handle = kernel.OpenProcess(0x101010, False, kernel.GetCurrentProcessId())
        if not handle:
            raise C.WinError(C.get_last_error())
        try:
            def read(address, width):
                buf, count = C.create_string_buffer(width), C.c_size_t()
                ok = kernel.ReadProcessMemory(handle, address, buf, width, C.byref(count))
                return buf.raw[:count.value], None if ok else {'winerror': C.get_last_error()}
            result = sampler.collect(read, C.addressof(cell)-0x594e6f0, 0)
            self.assertEqual(result['status'], 'completed')
            self.assertEqual((result['reads'], result['requestedBytes']), (7, 50))
            self.assertEqual(result['values']['F'], 0x123456789abcdef0)
            self.assertEqual(result['values']['M'], 0xfedcba9876543210)
        finally:
            kernel.CloseHandle(handle)


if __name__ == '__main__':
    unittest.main()
