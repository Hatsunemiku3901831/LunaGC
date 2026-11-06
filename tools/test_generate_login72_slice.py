import copy
import json
from pathlib import Path
import unittest

from tools.generate_born72_sources import OPCODES, PROTO
from tools.generate_login72_slice import COMMANDS, NAMES, render_sources


class LoginSliceGenerationTest(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[1]
        directory = root / 'protocol/cn-7.2'
        self.inputs = [json.loads((directory / name).read_text('utf8')) for name in (
            'mappings.json', 'profile.json', 'wire-observations.json', 'token-wire-observations.json')]
        self.source = '\n'.join(f'public static final int {n} = -1;' for n in COMMANDS)
        self.source += '\npublic static final int LCNLHAOBBFE = 24604;\n'

    def render(self):
        return render_sources(*self.inputs, self.source)

    def test_exact_wire_slice_and_deterministic_second_generation(self):
        first = self.render()
        self.assertEqual(set(first), {OPCODES, *(PROTO / (n + '.proto') for n in NAMES)})
        self.source = first[OPCODES]
        self.assertEqual(first, self.render())
        self.assertIn('account_type = 15;', first[PROTO / 'GetPlayerTokenReq.proto'])
        self.assertIn('sub_channel_id = 10;', first[PROTO / 'GetPlayerTokenReq.proto'])
        self.assertIn('OILKPHNHCHB = 13;', first[PROTO / 'PingReq.proto'])
        self.assertNotIn('client_time', first[PROTO / 'PingReq.proto'])
        self.assertIn('LCNLHAOBBFE = -124604;', first[OPCODES])

    def test_wire_drift_or_target_hash_must_fail(self):
        for change in ('tag', 'type', 'packed', 'hash'):
            with self.subTest(change=change):
                original = copy.deepcopy(self.inputs)
                if change == 'hash':
                    self.inputs[3]['inputSHA256']['YuanShen.exe'] = '0' * 64
                else:
                    self.inputs[3]['fields'][0][change] = {'tag': 20, 'type': 'bytes', 'packed': True}[change]
                with self.assertRaises(ValueError):
                    self.render()
                self.inputs = original

    def test_candidates_and_new_semantic_claims_cannot_silently_enter(self):
        schema = next(r for r in self.inputs[0]['schemas'] if r['semanticName'] == 'PingReq')
        schema['fields'][0]['status'] = 'verified'
        schema['fields'][0]['ambiguities'] = []
        with self.assertRaisesRegex(ValueError, 'semantic boundary'):
            self.render()
        self.setUp()
        next(r for r in self.inputs[0]['messages'] if r['semanticName'] == 'PingReq')['status'] = 'candidate'
        with self.assertRaisesRegex(ValueError, 'identity must be verified'):
            self.render()

    def test_new_collision_does_not_become_an_alias(self):
        self.source += '\npublic static final int OtherHandler = 26605;\n'
        with self.assertRaisesRegex(ValueError, 'collides'):
            self.render()


if __name__ == '__main__':
    unittest.main()
