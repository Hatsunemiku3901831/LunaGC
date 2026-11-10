"""Exact sample acceptance and instruction-before-execution rejection checks."""
import unittest

from tools import client72_d02_first_pop_copy as target


class FirstPopCopyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.frozen = target.load_frozen()
        lookup, _ = target.extract_approved_lookup()
        cls.windows = target.code_windows(cls.frozen, lookup)

    def engine(self, **kwargs):
        return target.FirstPopCopy(self.frozen, self.windows, **kwargs)

    def test_actual_sample_full_entry_and_nine_writes(self):
        result = self.engine().run()
        checks = target.verify_execution(result, self.frozen['collection'])
        self.assertTrue(checks['actualDirtySuffixCleared'])
        self.assertEqual(result['path'][0], '0x1405336c0')
        self.assertEqual(len(result['calls']), 4)
        self.assertEqual(len(result['returns']), 5)
        self.assertEqual(len(result['capturedWrites']), 9)
        self.assertEqual(result['uniquePermissionBytes'], 44)

    def test_no_permission_stops_before_first_write(self):
        result = self.engine(write_enabled=False).run()
        self.assertEqual(result['rejected']['pc'], '0x140563d2d')
        self.assertEqual(result['failure'], 'unapproved-write-before-instruction')
        self.assertNotIn('0x140563d2d', result['path'])
        self.assertEqual(result['capturedWrites'], [])
        self.assertEqual(result['changedByteAddresses'], [])

    def test_missing_lookup_cell_stops_before_original_read(self):
        engine = self.engine()
        root = engine.collection['records'][10]['address']
        del engine.source[root]
        result = engine.run()
        self.assertEqual(result['failure'], 'unprovided-read-before-instruction')
        self.assertEqual(result['rejected']['address'], hex(root))
        self.assertEqual(result['capturedWrites'], [])
        self.assertNotIn(result['rejected']['pc'], result['path'])

    def test_missing_tls_byte_is_not_filled_by_mapped_page(self):
        engine = self.engine()
        address = engine.collection['records'][0]['address']
        del engine.source[address]
        result = engine.run()
        self.assertEqual(result['rejected']['pc'], '0x1405337b9')
        self.assertEqual(result['failure'], 'unprovided-read-before-instruction')
        self.assertEqual(result['changedByteAddresses'], [])

    def test_removed_helper_callee_stops_before_call(self):
        engine = self.engine()
        engine.permitted_entries.remove(target.HELPER + engine.delta)
        result = engine.run()
        self.assertEqual(result['failure'], 'unapproved-callee')
        self.assertEqual(result['rejected']['pc'], '0x140563d85')
        self.assertNotIn('0x140563d85', result['path'])
        self.assertEqual(len(result['capturedWrites']), 6)
        state = next(r for r in result['originalAndFinalSpans'] if r['name'] == 'allocation.state19cc')
        self.assertEqual(state['finalBytesHex'], '05')

    def test_removed_sse_write_stops_before_dirty_clear(self):
        engine = self.engine()
        del engine.write_specs[0x141fdfa7a]
        result = engine.run()
        self.assertEqual(result['failure'], 'unapproved-write-before-instruction')
        self.assertEqual(result['rejected']['pc'], '0x141fdfa7a')
        self.assertNotIn('0x141fdfa7a', result['path'])
        head = next(r for r in result['originalAndFinalSpans'] if r['name'] == 'allocation.completeHead')
        self.assertEqual(head['finalBytesHex'][32:], '01000000000000000000000000000000')

    def test_wider_permission_does_not_authorize_exact_store(self):
        engine = self.engine()
        address, _ = engine.write_specs[0x141fdfa7a]
        engine.write_specs[0x141fdfa7a] = (address, 32)
        result = engine.run()
        self.assertEqual(result['rejected']['pc'], '0x141fdfa7a')
        self.assertEqual(result['failure'], 'unapproved-write-before-instruction')
        self.assertEqual(len(result['capturedWrites']), 6)

    def test_removed_second_sse_event_is_not_collapsed(self):
        engine = self.engine()
        del engine.write_specs[0x141fdfa7e]
        result = engine.run()
        self.assertEqual(result['rejected']['pc'], '0x141fdfa7e')
        self.assertEqual(len(result['capturedWrites']), 7)
        self.assertEqual(result['capturedWrites'][-1]['oldBytesHex'], '01000000000000000000000000000000')
        self.assertEqual(result['capturedWrites'][-1]['newBytesHex'], '00' * 16)


if __name__ == '__main__':
    unittest.main()
