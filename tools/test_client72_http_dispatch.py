"""Independent fixture-input guards; live client/SDK is never started."""
import json
from pathlib import Path
import tempfile
import unittest

from tools.trace_client72_http_dispatch import CASES, read_fixtures, summarize


class HttpDispatchFixtureTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parents[1]
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.seed = (self.root/'src/main/resources/keys/dispatchSeed.bin').read_bytes()
        self.minimal = bytes.fromhex('0a093132372e302e302e3110d5ac01a20207686b34655f636e')
        maximum = bytes.fromhex('0a0f666978747572652e696e76616c696410ffffffff0fa20207686b34655f636e')
        (self.directory/'manifest.json').write_text(json.dumps([dict(name=name) for name in CASES]))
        (self.directory/'minimal.region.bin').write_bytes(self.minimal)
        (self.directory/'uint32-boundary.region.bin').write_bytes(maximum)
        seed_field = bytes.fromhex('5a9c10') + self.seed
        self.encoded = {
            'minimal': b'\x1a'+bytes([len(self.minimal)])+self.minimal+seed_field,
            'empty-region': b'\x1a\0'+seed_field,
            'missing-region': seed_field}
        for name in CASES:
            (self.directory/(name+'.response.bin')).write_bytes(self.encoded.get(name, b''))

    def test_fixed_literal_wire_and_absent_empty_pair(self):
        fixtures, seed = read_fixtures(self.root, self.directory)
        self.assertEqual(seed, self.seed)
        self.assertNotEqual(fixtures['empty-region'], fixtures['missing-region'])

    def test_mutated_generated_leaf_is_rejected(self):
        changed = bytearray(self.minimal); changed[0] = 0x12
        (self.directory/'minimal.region.bin').write_bytes(changed)
        with self.assertRaisesRegex(ValueError, 'independent literal wire'):
            read_fixtures(self.root, self.directory)

    def test_root_cannot_relabel_the_nested_field(self):
        changed = bytearray(self.encoded['minimal']); changed[0] = 0x22
        (self.directory/'minimal.response.bin').write_bytes(changed)
        with self.assertRaisesRegex(ValueError, 'independent nested wire'):
            read_fixtures(self.root, self.directory)

    def test_empty_region_cannot_be_silently_omitted(self):
        (self.directory/'empty-region.response.bin').write_bytes(self.encoded['missing-region'])
        with self.assertRaisesRegex(ValueError, 'absent versus empty'):
            read_fixtures(self.root, self.directory)

    def test_fixture_set_drift_is_rejected_before_native_execution(self):
        (self.directory/'manifest.json').write_text(json.dumps([dict(name='candidate')]))
        with self.assertRaisesRegex(ValueError, 'fixture set'):
            read_fixtures(self.root, self.directory)

    def test_summary_keeps_partial_scope_without_raw_graph(self):
        raw = dict(traces=[dict(wire='private-raw-buffer')], fullCurrentRegionSchema=False,
                   fullRegionInfoSchema=False, regionListConsumed=False, runtimeStatus='not-tested')
        summary = summarize(raw)
        self.assertNotIn('traces', summary)
        self.assertFalse(summary['fullCurrentRegionSchema'])
        self.assertFalse(summary['regionListConsumed'])
        self.assertEqual(summary['runtimeStatus'], 'not-tested')


if __name__ == '__main__':
    unittest.main()
