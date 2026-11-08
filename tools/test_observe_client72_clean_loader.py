"""Read-only module-window scope and compiled transparent thunk checks."""
import struct
import unittest
from unittest.mock import patch
from pathlib import Path
import tempfile
from types import SimpleNamespace

from tools import observe_client72_clean_loader as card


class CleanModuleObservationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.proxy = (card.RAW/'target/release/examples/sdk72_clean_proxy.dll').read_bytes()
        cls.table = card.pointer_table(cls.proxy)

    def test_compiled_exports_point_to_one_contiguous_table(self):
        pe = card.PE(self.proxy)
        rows = pe.exports()
        self.assertEqual(len(rows), 48)
        self.assertEqual((rows[-1]['ordinal'], rows[-1]['name']), (48, 'Sdk72CleanInspectLoadedSdk'))
        self.assertEqual([x['ordinal'] for x in rows[:47]], list(range(1, 48)))
        for index, row in enumerate(rows[:47]):
            raw = pe.bytes_at(row['rva'], 6)
            self.assertEqual(raw[:2], b'\xff\x25')
            self.assertEqual(row['rva']+6+struct.unpack_from('<i', raw, 2)[0], self.table+8*index)

    def test_changed_thunk_is_rejected_before_process_read(self):
        pe = card.PE(self.proxy)
        changed = bytearray(self.proxy)
        changed[pe.offset(pe.exports()[19]['rva'], 1)] = 0x90
        with self.assertRaisesRegex(ValueError, 'reviewed RIP-relative'):
            card.pointer_table(bytes(changed))

    def fixtures(self):
        modules = {'Astrolabe.dll': {'base': 0x8000000100000000},
                   'Astrolabe_orig.dll': {'base': 0x9000000200000000},
                   'AccountPlatNative.dll': {'base': 0xa000000300000000}}
        original_rvas = [0x41000+0x101*i for i in range(47)]
        windows = {(rva, width): bytes((rva+i*17)%251 for i in range(width))
                   for rva, width in ((card.ENTRY_RVA, card.ENTRY_LENGTH), *card.CALLER_WINDOWS)}
        spans = {modules['Astrolabe.dll']['base']+self.table:
                 struct.pack('<47Q', *(modules['Astrolabe_orig.dll']['base']+x for x in original_rvas))}
        spans.update({modules['AccountPlatNative.dll']['base']+rva: data for (rva, width), data in windows.items()})
        class FixedPE:
            def exports(self):
                return [{'rva': rva} for rva in original_rvas]
            def bytes_at(self, rva, width):
                return windows[rva, width]
        real_pe = card.PE
        def image(data):
            return real_pe(data) if data == self.proxy else FixedPE()
        return modules, spans, image

    def inspect(self, spans, modules, image):
        calls = []
        def read(address, width):
            calls.append((address, width))
            return spans[address]
        with patch.object(card, 'PE', side_effect=image):
            result = card.inspect_windows(read, modules, self.proxy, b'stock', b'sdk')
        return result, calls

    def test_nonzero_high64_targets_and_exact_four_read_budget(self):
        modules, spans, image = self.fixtures()
        result, calls = self.inspect(spans, modules, image)
        self.assertEqual(result['status'], 'bound')
        self.assertTrue(all(x['equal'] for x in result['windows']))
        self.assertEqual([width for address, width in calls], [376, 253, 76, 76])
        self.assertEqual(sum(width for address, width in calls), 781)
        self.assertTrue(all(address > 1<<63 for address, width in calls))
        self.assertFalse(result['targetFunctionsCalled'])
        self.assertFalse(result['hookInstalled'])
        self.assertFalse(result['naturalCallerObserved'])
        self.assertFalse(result['d02Passed'])

    def test_wrong_single_forward_target_does_not_appear_bound(self):
        modules, spans, image = self.fixtures()
        address = modules['Astrolabe.dll']['base']+self.table
        wrong = bytearray(spans[address]); wrong[-1] ^= 1; spans[address] = bytes(wrong)
        result, calls = self.inspect(spans, modules, image)
        self.assertEqual(result['status'], 'window-or-target-mismatch')
        self.assertFalse(result['windows'][0]['equal'])
        self.assertTrue(all(x['equal'] for x in result['windows'][1:]))

    def test_each_exact_sdk_code_window_detects_independent_drift(self):
        for index, (rva, width) in enumerate(((card.ENTRY_RVA, card.ENTRY_LENGTH), *card.CALLER_WINDOWS), 1):
            with self.subTest(rva=rva):
                modules, spans, image = self.fixtures()
                address = modules['AccountPlatNative.dll']['base']+rva
                wrong = bytearray(spans[address]); wrong[width-1] ^= 1; spans[address] = bytes(wrong)
                result, calls = self.inspect(spans, modules, image)
                self.assertEqual([x['equal'] for x in result['windows']], [i != index for i in range(4)])
                self.assertEqual(result['status'], 'window-or-target-mismatch')

    def test_short_pointer_read_is_not_padded_with_zero(self):
        modules, spans, image = self.fixtures()
        address = modules['Astrolabe.dll']['base']+self.table
        spans[address] = spans[address][:-1]
        with self.assertRaises(struct.error):
            self.inspect(spans, modules, image)

    def test_failed_process_read_is_retained_without_other_reads(self):
        modules, spans, image = self.fixtures()
        calls = []
        def read(address, width):
            calls.append((address, width))
            raise OSError(299, 'Bounded module window read failed')
        with patch.object(card, 'PE', side_effect=image), self.assertRaises(OSError):
            card.inspect_windows(read, modules, self.proxy, b'stock', b'sdk')
        self.assertEqual(len(calls), 1)

    def test_inspection_handle_includes_wait_right_used_by_lifetime_check(self):
        test = self
        game = card.RAW/'owned-path-only'
        class Backend:
            def __init__(self):
                self.k = self; self.closed = []; self.rights = 0
            def OpenProcess(self, rights, inherit, pid):
                self.rights = rights
                test.assertEqual((inherit, pid), (False, 12345))
                return 0x1234567887654321
            def creation_time(self, handle):
                return {'filetime': 123456789, 'winerror': None}
            def image(self, handle):
                return str(game/'YuanShen.exe')
            def status(self, handle):
                if not self.rights & 0x100000:
                    raise PermissionError(5, 'WaitForSingleObject requires SYNCHRONIZE')
                return {'alive': False}
            def close(self, handle):
                self.closed.append(handle)
        backend = Backend()
        result = card.inspect_process(backend, game, {'pid': 12345,
            'processCreationTime': {'filetime': 123456789, 'winerror': None}}, b'', b'', b'')
        self.assertEqual(result, {'status': 'target-exited'})
        self.assertEqual(backend.rights, 0x101010)
        self.assertEqual(backend.closed, [0x1234567887654321])

    def test_actual_extended_windows_path_is_the_same_loaded_original_file(self):
        with tempfile.TemporaryDirectory(dir=card.RAW) as directory:
            game = Path(directory); plugins = game/'YuanShen_Data/Plugins'; plugins.mkdir(parents=True)
            bodies = {'Astrolabe.dll': b'owned proxy', 'Astrolabe_orig.dll': b'owned stock', 'AccountPlatNative.dll': b'owned sdk'}
            rows = []
            for index, (name, body) in enumerate(bodies.items()):
                path = plugins/name; path.write_bytes(body)
                loaded = '\\\\?\\'+str(path.resolve()) if name == 'Astrolabe_orig.dll' else str(path)
                rows.append({'name': name, 'path': loaded, 'base': 0x100000000*(index+1), 'size': 0x5000})
            original = rows[1]
            self.assertNotEqual(Path(original['path']).resolve(), (plugins/original['name']).resolve())
            self.assertTrue(Path(original['path']).samefile(plugins/original['name']))
            class Backend:
                def __init__(self):
                    self.k = SimpleNamespace(OpenProcess=lambda *args: 123, ReadProcessMemory=lambda *args: None)
                def creation_time(self, handle): return {'filetime': 123456789}
                def image(self, handle): return str(game/'YuanShen.exe')
                def status(self, handle): return {'alive': True}
                def snapshot(self, *args): return {'error': None, 'items': rows}
                def close(self, handle): pass
            with patch.object(card, 'PE', return_value=SimpleNamespace(image_size=0x5000)), patch.object(card, 'inspect_windows', return_value={'status': 'bound'}) as windows:
                result = card.inspect_process(Backend(), game, {'pid': 12345, 'processCreationTime': {'filetime': 123456789}}, *bodies.values())
            self.assertEqual(result['status'], 'bound')
            windows.assert_called_once()


if __name__ == '__main__':
    unittest.main()
