import hashlib
import json
import struct
import unittest

from tools import client72_sdk_natural_config_contract as contract

SDK = 0x7ffa00000000
AGGREGATE, CONFIG, URL, CONTROL = 0x12345670000, 0x12345680000, 0x12345690000, 0x123456a0000
HEAD, NODE = 0x23456780000, 0x23456790000
APP_KEY = b'OWNED-PRIVATE-APP-KEY-DO-NOT-REPORT'
KNOWN_URL = next(iter(contract.ALLOWED_ROUTES))


def qword(value):
    return struct.pack('<Q', value)


class Model:
    """Hand-built MSVC layout, independent of collector traversal/formatting."""
    def __init__(self, key=APP_KEY, url=KNOWN_URL, env=0):
        self.segments = {}
        self.heap = 0x34567890000
        self.segments[SDK + 0x7acc80] = qword(AGGREGATE)
        self.segments[SDK + 0x7e79a0] = qword(CONFIG)
        self.segments[AGGREGATE + 0x40] = qword(CONFIG)
        self.segments[CONFIG] = qword(SDK + 0x6c6cd8)
        self.segments[CONFIG + 0x8a0] = struct.pack('<i', env)
        self.segments[CONFIG + 0x218] = self.string(key)
        self.segments[CONFIG + 0x860] = qword(URL) + qword(CONTROL)
        self.set_tree([(b'comboLogin', url)], [(None, None)], 0)

    def string(self, value, force_heap=False):
        header = bytearray(32)
        if len(value) <= 15 and not force_heap:
            header[:len(value)] = value
            capacity = 15
        else:
            address = self.heap
            self.heap += 0x1000
            header[:8] = qword(address)
            self.segments[address] = value or b'\0'
            capacity = max(16, len(value))
        struct.pack_into('<QQ', header, 16, len(value), capacity)
        return bytes(header)

    def set_tree(self, pairs, links, root):
        def pointer(index):
            return HEAD if index is None else NODE + index * 0x100
        self.segments[URL] = qword(HEAD) + qword(len(pairs))
        sentinel = bytearray(32)
        struct.pack_into('<QQQ', sentinel, 0, HEAD, pointer(root), HEAD)
        sentinel[24:26] = b'\x01\x01'
        self.segments[HEAD] = bytes(sentinel)
        for index, ((key, value), (left, right)) in enumerate(zip(pairs, links)):
            entry = bytearray(96)
            struct.pack_into('<QQQ', entry, 0, pointer(left), HEAD, pointer(right))
            entry[24:26] = b'\x01\x00'
            entry[32:64] = self.string(key)
            entry[64:96] = self.string(value)
            self.segments[NODE + index * 0x100] = bytes(entry)

    def memory(self):
        return contract.OwnedMemory(self.segments)

    def collect(self, references=None):
        references = references or (contract.ReferenceRow(0, APP_KEY, KNOWN_URL),
                                     contract.ReferenceRow(4, APP_KEY, KNOWN_URL))
        memory = self.memory()
        return contract.collect_owned_snapshot(memory, SDK, references), memory


class NaturalConfigContractTest(unittest.TestCase):
    def test_high64_roots_heap_key_and_known_route_preserve_only_equal_env_sets(self):
        result, memory = Model().collect()
        self.assertEqual(result['snapshotStatus'], 'collected-owned-only')
        self.assertEqual(result['appKeyEqualsResourceEnvs'], [0, 4])
        self.assertEqual(result['comboEqualsResourceEnvs'], [0, 4])
        self.assertEqual(result['storedComboRouteClass'], 'cn-env-0')
        self.assertTrue(result['configRootAgreement'])
        self.assertTrue(result['configVtableMatches'])
        self.assertLessEqual(memory.reads, 76)
        self.assertLessEqual(memory.bytes_read, 4644)

    def test_sso_heap_and_empty_secret_report_no_secret_length_or_derived_identity(self):
        for key, heap in [(b'owned-sso-key', False), (APP_KEY, False), (b'', False), (b'owned-sso-key', True)]:
            with self.subTest(heap=heap, empty=not key):
                model = Model(key=key)
                model.segments[CONFIG + 0x218] = model.string(key, force_heap=heap)
                result, _ = model.collect((contract.ReferenceRow(6, key, KNOWN_URL),))
                self.assertEqual(result['appKeyEqualsResourceEnvs'], [6])
                serialized = json.dumps(contract.public_formatter(result))
                if key:
                    self.assertFalse(key.decode() in serialized, 'Private key entered the public report')
                self.assertFalse(hashlib.sha256(key).hexdigest() in serialized, 'Private key digest entered the public report')
                self.assertFalse('length' in serialized.lower(), 'Sensitive length metadata entered the report')
                self.assertFalse('bytes_read' in serialized, 'Dynamic read totals entered the report')

    def test_zero_env_is_stored_default_not_proof_of_init_or_natural_selection(self):
        result, _ = Model(env=0).collect()
        self.assertEqual(result['storedEnv'], 0)
        self.assertFalse(result['nativeInitCompletionObserved'])
        self.assertFalse(result['naturalDeepReadApproved'])
        self.assertFalse(result['liveBackendImplemented'])

    def test_unsigned_byte_order_and_fixed_key_lookup_do_not_dump_non_target_values(self):
        model = Model()
        model.set_tree([(b'A' * 16, b'NON_TARGET_PRIVATE'), (b'comboLogin', KNOWN_URL),
                        (b'\xffroute', b'OTHER_NON_TARGET_PRIVATE')], [(None, 1), (None, 2), (None, None)], 0)
        class Spy(contract.OwnedMemory):
            def __init__(self, segments):
                super().__init__(segments)
                self.calls = []
            def read(self, address, size):
                self.calls.append((address, size))
                return super().read(address, size)
        memory = Spy(model.segments)
        result = contract.collect_owned_snapshot(memory, SDK, (contract.ReferenceRow(0, APP_KEY, KNOWN_URL),))
        self.assertEqual(result['storedComboRouteClass'], 'cn-env-0')
        self.assertNotIn((NODE + 64, 32), memory.calls)
        self.assertNotIn((NODE + 2 * 0x100 + 64, 32), memory.calls)
        self.assertFalse('NON_TARGET_PRIVATE' in json.dumps(result), 'Non-target value entered the report')

    def test_missing_key_and_empty_tree_classify_static_fallback_without_calling_getter(self):
        for pairs, links, root in [([], [], None), ([(b'other', b'unread-non-target')], [(None, None)], 0)]:
            with self.subTest(empty=not pairs):
                model = Model()
                model.set_tree(pairs, links, root)
                result, _ = model.collect()
                self.assertEqual(result['comboLookupStatus'], 'absent')
                self.assertTrue(result['getterFallbackExpectedFromStaticContract'])
                self.assertFalse(result['naturalDeepReadApproved'])

    def test_empty_target_url_and_unknown_url_do_not_publish_raw_url_length_or_hash(self):
        unknown = b'https://unknown.invalid/OWNED-PRIVATE-URL?secret=DO-NOT-REPORT'
        for value in (b'', unknown):
            with self.subTest(empty=not value):
                result, _ = Model(url=value).collect()
                self.assertEqual(result['storedComboRouteClass'], 'unknown')
                self.assertEqual(result['getterFallbackExpectedFromStaticContract'], not value)
                serialized = json.dumps(result)
                self.assertFalse('unknown.invalid' in serialized, 'Unknown URL entered the report')
                self.assertFalse('DO-NOT-REPORT' in serialized, 'Private URL marker entered the report')
                self.assertFalse(hashlib.sha256(value).hexdigest() in serialized, 'Private URL digest entered the public report')

    def test_root_null_mismatch_and_vtable_failure_stop_before_deep_read(self):
        for address, value, status in [
                (SDK + 0x7acc80, 0, 'config-root-not-initialized'),
                (AGGREGATE + 0x40, CONFIG + 2**32, 'config-root-disagreement'),
                (CONFIG, SDK + 0x6c6ce0, 'config-vtable-mismatch')]:
            with self.subTest(status=status):
                model = Model()
                model.segments[address] = qword(value)
                result, memory = model.collect()
                self.assertEqual(result['snapshotStatus'], status)
                self.assertFalse(result['appKeyComparisonCompleted'])
                self.assertLessEqual(memory.reads, 4)

    def test_64_bit_string_length_and_bad_capacity_do_not_truncate_or_read_payload(self):
        for length, capacity, pointer in [(2**32 + 1, 2**32 + 1, 0x456789a0000),
                                          (4, 3, 0), (16, 15, 0), (16, 16, 0)]:
            with self.subTest(capacity=capacity):
                model = Model()
                header = bytearray(32)
                struct.pack_into('<Q', header, 0, pointer)
                struct.pack_into('<QQ', header, 16, length, capacity)
                model.segments[CONFIG + 0x218] = bytes(header)
                result, memory = model.collect()
                self.assertEqual(result['snapshotStatus'], 'app-key-unavailable')
                self.assertEqual(memory.reads, 6)

    def test_fixed_bounds_have_nonzero_positive_and_exact_rejections(self):
        for size, expected in [(128, 'collected-owned-only'), (129, 'app-key-unavailable')]:
            with self.subTest(bound='key', size=size):
                result, _ = Model(key=b'Q' * size).collect()
                self.assertEqual(result['snapshotStatus'], expected)
        for size, expected in [(256, 'collected-owned-only'), (257, 'combo-url-invalid')]:
            with self.subTest(bound='url', size=size):
                result, _ = Model(url=b'R' * size).collect()
                self.assertEqual(result['snapshotStatus'], expected)
        for size, expected in [(64, 'collected-owned-only'), (65, 'url-map-key-invalid')]:
            with self.subTest(bound='map-key', size=size):
                model = Model()
                model.set_tree([(b'A' * size, b'unread'), (b'comboLogin', KNOWN_URL)],
                               [(None, 1), (None, None)], 0)
                result, _ = model.collect()
                self.assertEqual(result['snapshotStatus'], expected)

    def test_high64_map_count_is_rejected_before_nodes_and_cycle_hits_budget(self):
        model = Model()
        model.segments[URL] = qword(HEAD) + qword(2**32 + 1)
        result, _ = model.collect()
        self.assertEqual(result['snapshotStatus'], 'url-map-descriptor-outside-contract')
        model = Model()
        model.set_tree([(b'AAAA', b'unread')], [(None, 0)], 0)
        result, memory = model.collect()
        self.assertEqual(result['snapshotStatus'], 'url-map-node-budget-exhausted')
        self.assertLessEqual(memory.reads, contract.MAX_READS)
        self.assertLessEqual(memory.bytes_read, contract.MAX_BYTES)

    def test_inaccessible_heap_and_wrong_sentinel_stay_unknown_not_key_absent(self):
        model = Model()
        header = bytearray(model.segments[CONFIG + 0x218])
        header[:8] = qword(0x5555666677778888)
        model.segments[CONFIG + 0x218] = bytes(header)
        result, _ = model.collect()
        self.assertEqual(result['snapshotStatus'], 'owned-range-unavailable')
        model = Model()
        head = bytearray(model.segments[HEAD])
        head[25] = 0
        model.segments[HEAD] = bytes(head)
        result, _ = model.collect()
        self.assertEqual(result['snapshotStatus'], 'url-map-sentinel-mismatch')
        self.assertEqual(result['comboLookupStatus'], 'not-read')

    def test_segments_are_immutable_copies_and_bad_overlaps_are_rejected_at_entrance(self):
        buffer = bytearray(b'owned')
        memory = contract.OwnedMemory({0x12345678900: buffer})
        buffer[:] = b'other'
        self.assertEqual(memory.read(0x12345678900, 5), b'owned')
        with self.assertRaises(ValueError):
            contract.OwnedMemory({0x1000: b'1234', 0x1002: b'12'})

    def test_private_resource_input_is_only_converted_in_memory_and_repr_is_safe(self):
        data = json.dumps([{'env': 0, 'app_key': APP_KEY.decode(), 'url': {'comboLogin': KNOWN_URL.decode()}}]).encode()
        rows = contract.references_from_server_pc_bytes(data)
        result, _ = Model().collect(rows)
        self.assertEqual(result['appKeyEqualsResourceEnvs'], [0])
        self.assertFalse(APP_KEY.decode() in repr(rows), 'Private resource key entered repr')
        with self.assertRaisesRegex(ValueError, 'shape'):
            contract.references_from_server_pc_bytes(b'[{"env":true,"app_key":"synthetic","url":{}}]')

    def test_formatter_drops_extra_material_and_budget_is_constant(self):
        result, _ = Model().collect()
        result['privateMaterial'] = APP_KEY.decode()
        public = contract.public_formatter(result)
        self.assertFalse('privateMaterial' in public, 'Private field entered the public formatter')
        self.assertEqual(public['budget'], {'maximumReads': 76, 'maximumBytes': 4644, 'maximumNodes': 32})


if __name__ == '__main__':
    unittest.main()
