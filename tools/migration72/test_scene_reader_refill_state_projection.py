import unittest
from tools.migration72 import scene_reader_refill_state_projection as s

class LiteralOracle(unittest.TestCase):
    def test_all_eleven_literal_expectations(self):
        fixtures=s.fixtures();self.assertEqual(len(fixtures),11)
        out={x['name']:s.project(x['input']) for x in fixtures}
        for result in out.values():
            self.assertFalse(result['naturalReady']);self.assertFalse(result['naturalReachabilityProven'])
            self.assertEqual(result['before']['field28Bits'],result['after']['field28Bits'])
            self.assertEqual(result['before']['tokenU64'],result['after']['tokenU64'])
        r=out['token_zero'];self.assertEqual(r['before'],r['after']);self.assertEqual(r['before'],{'array':'unread/unknown','baseBits':'unread/unknown','cursorBits':'unread/unknown','tokenU64':0,'field28Bits':'unread/unknown'});self.assertEqual(r['stop'],{'kind':'local-return','pc':'0x14f61493a'});self.assertIsNone(r['readIntent'])
        r=out['nonzero64_cache_less_than_capacity'];self.assertEqual(r['before']['field28Bits'],0x89abcdef);self.assertEqual(r['before']['tokenU64'],0xfedcba9876543210);self.assertEqual(r['comparison']['sumSigned'],7);self.assertEqual(r['before'],r['after']);self.assertEqual(r['stop']['kind'],'local-return');self.assertIsNone(r['readIntent'])
        r=out['exact_fit_refills'];self.assertEqual(r['comparison']['sumSigned'],8);self.assertFalse(r['comparison']['strictLess']);self.assertEqual((r['after']['baseBits'],r['after']['cursorBits']),(103,0));self.assertEqual(r['readIntent'],{'tokenU64':0xfedcba9876543210,'arrayId':'A','arg3':0,'capacityBits':8,'offsetU64':103});self.assertEqual(r['stop']['pc'],'0x14f614929')
        r=out['count_exceeds_capacity'];self.assertEqual(r['before'],r['after']);self.assertEqual(r['stop'],{'kind':'pending-allocation','pc':'0x14f6147f1','requestedCountBits':9});self.assertIsNone(r['readIntent'])
        r=out['adoption_preserves_initial_state'];self.assertIsNone(r['before']['array']);self.assertEqual(r['after']['array'],{'id':'A','capacity':8});self.assertEqual((r['after']['baseBits'],r['after']['cursorBits']),(17,3));self.assertEqual(r['readIntent']['offsetU64'],20);self.assertEqual(r['readIntent']['capacityBits'],8)
        r=out['signed_offset'];self.assertEqual(r['readIntent']['offsetU64'],0xffffffffffffffff);self.assertEqual((r['after']['baseBits'],r['after']['cursorBits']),(0xfffffffe,1))
        r=out['adoption_null'];self.assertIsNone(r['after']['array']);self.assertEqual(r['stop'],{'kind':'pending-error-call','pc':'0x14f61493b'});self.assertIsNone(r['readIntent'])
        r=out['zero_count_at_end'];self.assertEqual((r['after']['baseBits'],r['after']['cursorBits']),(18,0));self.assertEqual(r['readIntent']['offsetU64'],18);self.assertEqual(r['readIntent']['capacityBits'],8)
        r=out['negative_count_bit_semantics'];self.assertEqual(r['comparison']['sumSigned'],2);self.assertEqual(r['stop']['kind'],'local-return');self.assertIsNone(r['readIntent'])
        r=out['i32_wrap'];self.assertEqual(r['comparison']['sumBits'],0x80000002);self.assertEqual(r['comparison']['sumSigned'],-2147483646);self.assertEqual(r['stop']['kind'],'local-return')
        r=out['base_wrap_sign_extension'];self.assertEqual((r['after']['baseBits'],r['after']['cursorBits']),(0x80000000,0));self.assertEqual(r['readIntent']['offsetU64'],0xffffffff80000000)

    def test_token_only_has_no_defaults_or_external_intent(self):
        r=s.project({'start':'entry','token':0})
        self.assertEqual(r['before'],{'array':'unread/unknown','baseBits':'unread/unknown','cursorBits':'unread/unknown','tokenU64':0,'field28Bits':'unread/unknown'})
        self.assertEqual(r['before'],r['after']);self.assertEqual(r['unread'],['array','base','cursor','count','slot','field28'])
        self.assertIsNone(r['readIntent']);self.assertEqual(r['stop']['kind'],'local-return');self.assertFalse(r['naturalReady'])

    def test_token_zero_supplied_values_remain_conditional_and_unread(self):
        value={'id':'given','capacity':7}
        r=s.project({'start':'entry','token':0,'array':value,'base':-12,'cursor':31,'field28':0x12345678})
        self.assertEqual(r['before'],r['after']);self.assertEqual(r['before']['array'],value)
        self.assertEqual((r['before']['baseBits'],r['before']['cursorBits'],r['before']['field28Bits']),(-12,31,0x12345678))
        self.assertIn('field28',r['unread']);self.assertFalse(r['naturalReachabilityProven']);self.assertIsNone(r['readIntent'])

    def test_inputs_validated_at_entry(self):
        datum=s.fixtures()[1]['input']
        for key,value in (('token',-1),('token',1<<64),('count',1<<32),('base',-2147483649),('start','other'),('array',None)):
            bad={**datum,key:value}
            with self.assertRaises(ValueError):s.project(bad)
        with self.assertRaises(ValueError):s.project({**datum,'extra':1})

    def test_fixed_spec_and_source(self):
        spec=s.pin_sources();self.assertEqual(len(spec['cases']),11);self.assertEqual(spec['sourceSHA256'],'dcf901e0952f9fa49adcf090c5b97510953019358c32f64bdf298840f8441999')

if __name__=='__main__':unittest.main()
