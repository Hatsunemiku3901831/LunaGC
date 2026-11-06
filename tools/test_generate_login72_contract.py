import copy
import json
from pathlib import Path
import unittest
from tools.generate_login72_contract import OBSERVATIONS, OPCODES, PROTO, render_sources


class LoginContractGenerationTest(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[1]
        load = lambda n: json.loads((root/'protocol/cn-7.2'/n).read_text('utf8'))
        self.mapping, self.profile = load('mappings.json'), load('profile.json')
        self.reports = [load(n) for n in OBSERVATIONS]
        self.opcodes = (root/OPCODES).read_text('utf8')

    def render(self):
        return render_sources(self.mapping, self.profile, self.reports, self.opcodes)

    def test_full_raw_closure_preserves_correct_md5_names_and_primitive_widths(self):
        first = self.render()
        self.opcodes = first[OPCODES]
        self.assertEqual(first, self.render())
        self.assertEqual(len(first), 5)
        self.assertIn('client_md5 = 437;', first[PROTO/'PlayerLoginRsp.proto'])
        self.assertIn('client_silence_md5 = 1255;', first[PROTO/'PlayerLoginRsp.proto'])
        self.assertIn('sfixed32 PGEMKKCNEMJ = 13;', first[PROTO/'Client72LoginNested.proto'])
        self.assertIn('map<uint64, DKFJJMGPCMD>', first[PROTO/'PlayerLoginRsp.proto'])
        self.assertIn('oneof content', first[PROTO/'Client72LoginNested.proto'])

    def test_identity_and_semantic_promotion_are_not_implicit(self):
        next(r for r in self.mapping['schemas'] if r['semanticName']=='GetPlayerTokenRsp')['fields'][0]['status']='verified'
        with self.assertRaises(ValueError):self.render()

    def test_missing_nested_and_nonempty_graph_evidence_rejected(self):
        self.reports[2]['messages'].pop()
        with self.assertRaises(ValueError):self.render()
        self.setUp();self.reports[3]['contentBindings']=[]
        with self.assertRaises(ValueError):self.render()

    def test_target_shape_hash_and_command_collision_rejected(self):
        for change in ('hash','type','collision'):
            with self.subTest(change=change):
                self.setUp()
                if change=='hash':self.reports[1]['inputSHA256']['YuanShen.exe']='0'*64
                elif change=='type':self.reports[1]['messages'][0]['fields'][0]['type']='uint64'
                else:self.opcodes+='\npublic static final int WrongAlias = 27808;'
                with self.assertRaises(ValueError):self.render()


if __name__=='__main__':unittest.main()
