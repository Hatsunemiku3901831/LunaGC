import copy
import json
from pathlib import Path
import unittest
from tools.generate_scene72_sources import render_sources, OPCODES, PROTO


class SceneSourceGenerationTest(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]
        self.inputs=[json.loads((root/'protocol/cn-7.2'/n).read_text('utf8')) for n in (
            'scene-handshake-observations.json','store-weight-contract-observations.json','initialization-wire-observations.json')]
        self.source=(root/OPCODES).read_text('utf8')

    def render(self):return render_sources(*self.inputs,self.source)

    def test_presence_adapter_and_exact_inventory_contract(self):
        outputs=self.render();self.source=outputs[OPCODES]
        self.assertEqual(outputs,self.render());self.assertEqual(len(outputs),11)
        self.assertIn('optional int32 LDLIEKOAFBP = 12;',outputs[PROTO/'EnterSceneReadyRsp.proto'])
        self.assertIn('optional float CJMJBINEBAG = 1;',outputs[PROTO/'Client72SceneHandshakeNested.proto'])
        self.assertIn('weapon_count_limit = 9;',outputs[PROTO/'StoreWeightLimitNotify.proto'])
        self.assertNotIn('weight_limit = ',outputs[PROTO/'StoreWeightLimitNotify.proto'])

    def test_hash_semantic_and_collision_drift_rejected(self):
        for change in ('hash','capacity','presence','collision'):
            with self.subTest(change=change):
                self.setUp()
                if change=='hash':self.inputs[2]['inputSHA256']['YuanShen.exe']='0'*64
                elif change=='capacity':self.inputs[1]['bindings'][0]['tag']=2
                elif change=='presence':self.inputs[0]['responsePresenceContract'][0]['token']['absentRawDefault']=0
                else:self.source+='\npublic static final int UnknownHandler = 9948;'
                with self.assertRaises(ValueError):self.render()


if __name__=='__main__':unittest.main()
