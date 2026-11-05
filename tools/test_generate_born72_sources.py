"""Generation must retain evidence gates and reject partial or ambiguous input."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from tools.generate_born72_sources import NAMES, OPCODES, render_sources, write_sources

ROOT = Path(__file__).resolve().parents[1]


class BornGenerationTest(unittest.TestCase):
    def setUp(self):
        directory = ROOT / 'protocol/cn-7.2'
        self.inputs = [json.loads((directory / name).read_text('utf8')) for name in
                       ('mappings.json', 'profile.json', 'commands.json', 'born-observations.json')]
        self.source = '\n'.join(f'public static final int {name} = -1;' for name in NAMES) + '\n'

    def render(self):
        return render_sources(*self.inputs, self.source)

    def test_determinism_and_exact_scope(self):
        first = self.render()
        self.assertEqual(first, self.render())
        self.assertEqual(len(first), 4)
        self.assertIn('DoSetPlayerBornDataNotify = 5008;', first[OPCODES])
        self.assertIn('SetPlayerBornDataReq = 8541;', first[OPCODES])
        self.assertIn('SetPlayerBornDataRsp = 414;', first[OPCODES])
        self.assertIn('uint32 avatar_id = 11;', first[Path('src/main/proto/SetPlayerBornDataReq.proto')])
        self.assertIn('int32 retcode = 15;', first[Path('src/main/proto/SetPlayerBornDataRsp.proto')])

    def test_candidates_cannot_generate(self):
        for layer in ('opcodes', 'messages', 'schemas'):
            with self.subTest(layer=layer):
                original = copy.deepcopy(self.inputs[0])
                next(r for r in self.inputs[0][layer] if r['semanticName'] == NAMES[1])['status'] = 'candidate'
                with self.assertRaises(ValueError): self.render()
                self.inputs[0] = original

    def test_input_hash_and_field_drift_rejected(self):
        for key in ('hash', 'tag', 'type', 'presence', 'unknown'):
            with self.subTest(key=key):
                self.setUp()
                fields = next(r for r in self.inputs[0]['schemas'] if r['semanticName'] == NAMES[1])['fields']
                if key == 'hash': self.inputs[3]['inputSHA256']['YuanShen.exe'] = '0' * 64
                elif key == 'unknown': fields[0]['status'] = 'candidate'
                else: fields[0][key] = dict(tag=2, type='uint64', presence='explicit')[key]
                with self.assertRaises(ValueError): self.render()

    def test_collision_alias_and_static_binding_rejected(self):
        self.source += 'public static final int LegacyUnverified = 5008;\n'
        with self.assertRaisesRegex(ValueError, 'not an approved alias'): self.render()
        self.setUp()
        next(r for r in self.inputs[0]['messages'] if r['semanticName'] == NAMES[0])['aliases'] = ['Legacy']
        with self.assertRaisesRegex(ValueError, 'no alias'): self.render()
        self.setUp()
        next(r for r in self.inputs[0]['opcodes'] if r['semanticName'] == NAMES[1])['cmdId'] = 26105
        with self.assertRaises(ValueError): self.render()

    def test_check_detects_drift_without_writing(self):
        outputs = self.render()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for path in outputs: (root / path).parent.mkdir(parents=True, exist_ok=True)
            self.assertEqual(len(write_sources(root, outputs, check=True)), 4)
            write_sources(root, outputs)
            self.assertEqual(write_sources(root, outputs, check=True), [])
            (root / OPCODES).write_text('drift', encoding='utf8')
            self.assertEqual(write_sources(root, outputs, check=True), [str(OPCODES).replace('\\', '/')])
            self.assertEqual((root / OPCODES).read_text(), 'drift')

    def test_empty_schema_requires_native_evidence(self):
        next(r for r in self.inputs[0]['schemas'] if r['semanticName'] == NAMES[0]).pop('emptySchemaEvidence')
        with self.assertRaisesRegex(ValueError, 'empty-schema'): self.render()


if __name__ == '__main__': unittest.main()
