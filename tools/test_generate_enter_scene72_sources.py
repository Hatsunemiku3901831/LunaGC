import json
from pathlib import Path
import unittest
from tools.generate_enter_scene72_sources import render_sources,OPCODES,PROTO


class EntryGenerationTest(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]
        self.contract,self.handshake=[json.loads((root/'protocol/cn-7.2'/name).read_text('utf8')) for name in (
            'enter-scene-contract-observations.json','scene-handshake-observations.json')]
        self.opcodes=(root/OPCODES).read_text('utf8')

    def test_complete_raw_family_four_names_and_shared_vector(self):
        out=render_sources(self.contract,self.handshake,self.opcodes)
        self.assertEqual(out,render_sources(self.contract,self.handshake,out[OPCODES]))
        source=out[PROTO/'PlayerEnterSceneNotify.proto']
        self.assertIn('optional uint32 scene_id = 7;',source)
        self.assertIn('BGDCNKAKGHI pos = 14;',source)
        self.assertIn('optional uint64 CNABNLMHMII = 6;',source)
        self.assertIn('repeated uint32 EMLMBEIBJHJ = 13 [packed = true];',source)
        self.assertNotIn('scene_begin_time',source)
        self.assertNotIn('message BGDCNKAKGHI',out[PROTO/'Client72EnterSceneNested.proto'])

    def test_unknown_semantics_shared_shape_and_command_drift_rejected(self):
        for change in ('meaning','vector','collision'):
            with self.subTest(change=change):
                self.setUp()
                if change=='meaning':self.contract['semanticBindings'][0]['name']='world_level'
                elif change=='vector':next(r for r in self.handshake['messages'] if r['typeIndex']==84343)['fields'][0]['tag']=99
                else:self.opcodes+='\npublic static final int UnexpectedTarget = 27748;'
                with self.assertRaises(ValueError):render_sources(self.contract,self.handshake,self.opcodes)


if __name__=='__main__':unittest.main()
