import unittest
import copy
import json
import math
from pathlib import Path
from tools.client72_session_runtime import permutation_table, SessionRuntime72
from tools.trace_client72_session import SEED_CASES
from tools.validate_protocol72 import validate_session_token_flow


class SessionFixtureTests(unittest.TestCase):
    def test_storage_table_is_bijective_and_inverse(self):
        for multiplier,shift in ((1,0),(197,41),(255,255)):
            table=permutation_table(multiplier,shift)
            self.assertEqual(len(table),4096)
            for row in range(8):
                self.assertEqual(set(table[row*256:(row+1)*256]),set(range(256)))
                for value in range(256):
                    self.assertEqual(table[(row+8)*256+table[row*256+value]],value)

    def test_invalid_storage_table_inputs_rejected(self):
        for multiplier,shift in ((0,0),(2,0),(257,0),(True,0),(1,-1),(1,256),(1,True)):
            with self.assertRaises(ValueError):permutation_table(multiplier,shift)

    def test_array_allocation_rejects_unbounded_or_unknown_shape(self):
        runtime=object.__new__(SessionRuntime72)
        for length,width in ((-1,1),(4097,1),(True,1),(1,4)):
            with self.assertRaises(ValueError):runtime.array(length,width)

    def test_seed_vectors_exercise_byte_order_high_bits_and_extra_bytes(self):
        cases={name:(data,client) for name,data,client in SEED_CASES}
        data,client=cases['high64']
        self.assertEqual(int.from_bytes(data,'big')^client,0xffffffffffffffff)
        self.assertNotEqual(int.from_bytes(data,'little')^client,0xffffffffffffffff)
        self.assertEqual(cases['trailing-byte'][0][:8],cases['mixed'][0])
        self.assertTrue(any(client>>63 for _,client in cases.values()))

    def test_random_source_refuses_values_outside_its_real_domain(self):
        runtime=object.__new__(SessionRuntime72)
        for value in (-0.1,1.0,float('nan'),float('inf'),True,'0.5'):
            with self.assertRaises(ValueError):runtime.generate_client_seed(value)
        self.assertEqual(int(math.nextafter(1.0,0.0)*(1<<64)),0xfffffffffffff800)


class SessionTokenEvidenceTests(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'protocol/cn-7.2'
        self.doc,self.profile,self.flow,self.login,self.response=[json.loads((root/name).read_text('utf8'))
            for name in ('mappings.json','profile.json','session-token-observations.json',
                         'login-wire-observations.json','response-wire-observations.json')]

    def errors(self):return validate_session_token_flow(self.doc,self.profile,self.flow,[self.login,self.response])

    def test_current_token_flow_matches_wire_and_mapping(self):self.assertEqual([],self.errors())

    def test_missing_input_hash_and_substituted_chain_are_rejected(self):
        self.flow['inputSHA256'].pop('YuanShen.exe');self.assertTrue(self.errors())
        self.setUp();self.flow['chain']['sessionOffset']=0x90;self.assertTrue(self.errors())

    def test_token_field_and_wire_cannot_be_renamed_or_promoted_by_substitution(self):
        for key,value in [('tag',2),('semanticField','guessed'),('type','bytes'),('status','candidate')]:
            original=copy.deepcopy(self.flow);self.flow['fieldBindings'][0][key]=value
            self.assertTrue(self.errors());self.flow=original

    def test_missing_duplicate_and_extra_bindings_fail(self):
        original=copy.deepcopy(self.flow)
        self.flow['fieldBindings'].pop();self.assertTrue(self.errors())
        self.flow=copy.deepcopy(original);self.flow['fieldBindings'][1]=self.flow['fieldBindings'][0]
        self.assertTrue(self.errors())
        self.flow=copy.deepcopy(original);self.flow['fieldBindings'].append(copy.deepcopy(self.flow['fieldBindings'][0]))
        self.assertTrue(self.errors())


if __name__=='__main__':unittest.main()
