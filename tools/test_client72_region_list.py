"""Independent fixture/source boundaries; never starts a client or server."""
import base64
import json
from pathlib import Path
import tempfile
import unittest

from tools.trace_client72_region_list import CASES, read_fixtures, summarize


class RegionListFixtureTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parents[1]
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.seed = (self.root/'src/main/resources/keys/dispatchSeed.bin').read_bytes()
        child = (b'\x0a\x09fixture_a\x12\x07Title A\x1a\x0aDEV_PUBLIC\x22\x30'
                 b'http://127.0.0.1:8088/query_cur_region/fixture_a')
        tail = b'\x2a\x9c\x10'+self.seed+b'\x32\x05\x00\x01\x7f\x80\xff\x38\x01'
        self.samples = {'empty-response': b'', 'empty-entry': b'\x12\0'+tail,
                        'one': b'\x12'+bytes([len(child)])+child+tail}
        (self.directory/'manifest.json').write_text(json.dumps([dict(name=n) for n in CASES]))
        for name in CASES:
            self.write_pair(name, self.samples.get(name, b''))

    def write_pair(self, name, wire):
        (self.directory/(name+'.bin')).write_bytes(wire)
        (self.directory/(name+'.base64.txt')).write_text(base64.b64encode(wire).decode('ascii'))

    def test_literal_list_seed_and_empty_item(self):
        fixtures, seed = read_fixtures(self.root, self.directory)
        self.assertEqual(seed, self.seed)
        self.assertNotEqual(fixtures['empty-entry'], fixtures['empty-response'])

    def test_relabelled_list_key_is_rejected(self):
        changed = bytearray(self.samples['one']); changed[0] = 0x1a
        self.write_pair('one', changed)
        with self.assertRaisesRegex(ValueError, 'independent literal wire'):
            read_fixtures(self.root, self.directory)

    def test_empty_item_cannot_be_omitted(self):
        self.write_pair('empty-entry', self.samples['empty-entry'][2:])
        with self.assertRaisesRegex(ValueError, 'Absent list'):
            read_fixtures(self.root, self.directory)

    def test_base64_body_must_match_java_binary(self):
        (self.directory/'one.base64.txt').write_text('AA==')
        with self.assertRaisesRegex(ValueError, 'Base64 and binary differ'):
            read_fixtures(self.root, self.directory)

    def test_fixture_identity_is_required(self):
        (self.directory/'manifest.json').write_text('[{"name":"shape-candidate"}]')
        with self.assertRaisesRegex(ValueError, 'fixture set'):
            read_fixtures(self.root, self.directory)

    def test_summary_keeps_runtime_and_callback_boundaries(self):
        summary = summarize(dict(traces=[dict(wire='raw')], fullCallbackExecuted=False,
                                 runtimeStatus='not-tested', liveClientExecuted=False))
        self.assertNotIn('traces', summary)
        self.assertFalse(summary['fullCallbackExecuted'])
        self.assertFalse(summary['liveClientExecuted'])
        self.assertEqual(summary['runtimeStatus'], 'not-tested')


if __name__ == '__main__':
    unittest.main()
