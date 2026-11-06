"""Guards for the finite SceneInfo raw evidence; no production readiness claim."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.extract_client72 import HASHES


class SceneInfoRawTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        path = Path(os.environ.get('SCENE_INFO_RAW_OBSERVATIONS', cls.root / 'protocol/cn-7.2/scene-info-raw-observations.json'))
        cls.report = json.loads(path.read_text('utf8'))
        cls.messages = {r['typeIndex']: r for r in cls.report['messages']}
        cls.cases = {r['name']: r for r in cls.report['fixtures']}

    def test_fixed_inputs_and_independent_source_evidence(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        for name, digest in self.report['sourceEvidenceSHA256'].items():
            raw = (self.root / 'protocol/cn-7.2' / ('avatar-' + name + '-observations.json')).read_bytes()
            self.assertEqual(digest, hashlib.sha256(raw).hexdigest())
        for name, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / name).read_bytes()).hexdigest())

    def test_nine_typed_readers_and_finite_metadata_scope(self):
        self.assertEqual({18295, 34208, 44512, 49723, 73976, 80154, 83320, 87468, 89710}, set(self.messages))
        self.assertEqual(36, sum(len(r['fields']) for r in self.messages.values()))
        self.assertEqual((1152, 40, 21), (self.report['scan']['nativeFixtures'], self.report['nativeFixtures'], len(self.report['rejections'])))
        self.assertEqual(36, self.report['declarations']['metadataStorageFields'])
        declarations = {r['typeIndex']: r for r in self.report['declarations']['types']}
        for index in self.messages:
            reader = next(r for r in declarations[index]['methods'] if r['name'] == 'MOMMACBEMEK')
            self.assertEqual(52310, reader['parameterType']['typeDefinition'])
            self.assertFalse(reader['parameterType']['byRef'])
            self.assertEqual(64, len(reader['codeSHA256']))
        self.assertEqual('0x15a4e691c', next(r for r in declarations[34208]['methods'] if r['name'] == 'MOMMACBEMEK')['protectedJump'])

    def test_cctor_concrete_aot_and_codec_class_sources(self):
        aot = [r for r in self.report['aotBindings'] if r['method'] == 'FNNADLCNGFO']
        self.assertEqual({12523: 18295, 10766: 73976, 6486: 53824, 8979: 80154, 12390: 83320},
            {r['classInstance']: r['arguments'][0]['typeDefinition'] for r in aot})
        self.assertTrue(all(r['address'] == '0x158dc41f0' for r in aot))
        self.assertEqual([18295, 34208, 49723, 89710], [r['typeIndex'] for r in self.report['initializers']])
        self.assertEqual(6, len(self.report['nativeCodecBindings']))
        self.assertEqual([(18295, 11, 73976), (18295, 12, 53824)],
            [(r['ownerType'], r['tag'], r['nestedTypeIndex']) for r in self.report['nativeCodecBindings']
                if r['ownerType'] == 18295 and 'nestedTypeIndex' in r])
        adds = [r for r in self.report['primitiveAotBindings'] if r['method'] == 'Add']
        self.assertEqual({'0x158db87c0', '0x158db8bc0'}, {r['address'] for r in adds})

    def test_fixed32_is_independently_distinguished_from_uint32(self):
        fields = {r['tag']: r for r in self.messages[83320]['fields']}
        self.assertEqual({1: ('uint32', 28, [0]), 2: ('fixed32', 32, [5]), 3: ('fixed32', 24, [5])},
            {tag: (r['type'], r['objectOffset'], r['acceptedWireTypes']) for tag, r in fields.items()})
        self.assertEqual(['0x1524650c1'], fields[2]['nativeSources'])
        self.assertEqual(['0x1524650e1'], fields[3]['nativeSources'])
        declared = next(r for r in self.report['declarations']['types'] if r['typeIndex'] == 83320)
        self.assertIn('0x147521000', next(r for r in declared['methods'] if r['name'] == 'MOMMACBEMEK')['nativeDirectCalls'])
        literal = self.cases['83320-independent-literal-two-fixed32']
        self.assertEqual('08818080800815efcdab891dffffffff', literal['wire'])
        self.assertEqual(dict(DOBGMPBDLKF=2147483649, EMBPDEHFOJK=2309737967, OPKFBNFODJM=4294967295), literal['result'])

    def test_nonzero_uint64_and_full_complex_child_readers(self):
        avatar = self.cases['18295-all-nine-nonempty-complex-graph']['result']
        self.assertEqual((18446744073709551615, 9007199254740993), (avatar['NPKKCHABEJH'], avatar['OKMEHFHNFCG']))
        swapped = self.cases['18295-u64-independent-swapped-high-bits']
        self.assertEqual('50818080808080801068ffffffffffffffffff01', swapped['wire'])
        self.assertEqual((9007199254740993, 18446744073709551615), (swapped['result']['NPKKCHABEJH'], swapped['result']['OKMEHFHNFCG']))
        coverage = self.report['nativeCoverage']['nonemptyCompositeCoverage']
        self.assertEqual([(36, 34), (42, 34)], [(r['messageCount'], r['reusedCompleteComplexMessageCount']) for r in coverage])
        self.assertTrue(all('untracedType' not in json.dumps(self.cases[r['fixture']]['result']) for r in coverage))

    def test_same_shape_children_merge_and_lists_append_independently(self):
        self.assertEqual(dict(CCODNLIKNGM={}, LBJMODOPOCP=dict(FAGKAADHKDI=True)),
            self.cases['18295-same-shape-singular-children-merge-independently']['result'])
        self.assertEqual([dict(JPCOIFLAAED=11), dict(CIIBGHCBCGK=22), {}],
            self.cases['18295-list-items-do-not-merge-and-empty-appends']['result']['CDOAFDGJGDF'])
        merged = self.cases['49723-singular-container-merges-and-list-appends']['result']
        self.assertEqual(2, len(merged['EOFFDMLNFIM']['GFDEDOJEICE']))
        self.assertEqual({}, merged['KNFDPJCMNOE'][1])
        root = self.cases['34208-duplicate-nested-merge-preserves-list-independence']['result']
        self.assertEqual([dict(FDDDFIAINCE=3), dict(FDDDFIAINCE=4)], root['DIGAMAKGCPA'])

    def test_native_message_array_store_and_primitive_add_are_executed(self):
        coverage = self.report['nativeCoverage']
        self.assertTrue(coverage['noCriticalFunctionModeled'])
        self.assertEqual(set(self.messages), set(coverage['executedNewMessageTypeIndices']))
        for row in self.cases.values(): self.assertFalse(set(row['modeledAddresses']) & set(coverage['criticalNativeAddresses']))
        appends = [r for r in self.cases['89710-two-distinct-nonempty-items']['entered'] if r.get('kind') == 'native-scene-message-list-store']
        self.assertEqual([(0, 83320), (1, 83320)], [(r['arrayIndex'], r['childTypeIndex']) for r in appends])
        self.assertEqual({'0x158dc4236'}, {r['entry'] for r in appends})
        adds = [r for r in self.cases['18295-u32-mixed-packed-unpacked-and-empty']['entered'] if r.get('kind') == 'native-scene-u32-add']
        self.assertEqual([0, 1, 4294967295, 128], [r['value'] for r in adds])
        self.assertEqual({'0x158db8bc0'}, {r['entry'] for r in adds})

    def test_zero_wrong_wire_signed_enum_and_float_bits(self):
        for name, row in self.cases.items():
            if name.startswith(('empty-', 'duplicate-zero-')): self.assertEqual({}, row['result'])
        self.assertEqual({}, self.cases['83320-duplicate-fixed32-u32-explicit-zero']['result'])
        self.assertEqual({}, self.cases['83320-wrong-varints-and-wrong-fixed32-skipped']['result'])
        self.assertEqual(-1, self.cases['87468-independent-negative-enum-and-u32-max']['result']['EMNNKLLNMBG'])
        negative = self.cases['80154-negative-zero-subnormal-and-finite-max']['floatScalarBits']
        self.assertEqual(['0x80000000'], [r['valueBits'] for r in negative])
        self.assertEqual(['0x1'], [r['valueBits'] for r in self.cases['80154-minimum-positive-subnormal']['floatScalarBits']])
        self.assertEqual(['0x7f7fffff'], [r['valueBits'] for r in self.cases['80154-maximum-finite-float']['floatScalarBits']])

    def test_native_errors_are_separate_from_input_and_scope_rejections(self):
        rejected = self.report['rejections']
        self.assertEqual(12, sum(r['scope'] == 'explicit-input-model' for r in rejected))
        native = [r for r in rejected if r['scope'] == 'native-error-entry']
        self.assertEqual(5, len(native))
        self.assertTrue(all('Native error branch:' in r['error'] for r in native))
        self.assertEqual(2, sum(r['scope'] == 'explicit-scope-refusal' for r in rejected))
        self.assertEqual(2, sum(r['scope'] == 'fixture-model-boundary' for r in rejected))

    def test_capacity64_success_and_growth65_stays_a_fixture_limit(self):
        self.assertEqual(64, len(self.cases['89710-fixed-capacity64-items']['result']['GFDEDOJEICE']))
        self.assertEqual(list(range(64)), self.cases['18295-fixed-capacity64-u32']['result']['CEKDDNOPKMK'])
        bounds = {r['name']: r for r in self.report['rejections'] if r['scope'] == 'fixture-model-boundary'}
        self.assertIn('Unmapped access 0x30 at 0x158dc42ca', bounds['message-list-growth65-outside-fixture']['error'])
        self.assertIn('Unmodeled native call 0x140514ee0', bounds['u32-list-growth65-outside-fixture']['error'])

    def test_unknown_object_owners_have_no_approved_schema(self):
        self.assertEqual([72829, 54267], self.report['unresolvedObjectOwnerTypeIndices'])
        self.assertFalse({72829, 54267} & set(self.messages))
        field = next(r for r in self.messages[49723]['fields'] if r['tag'] == 12)
        self.assertEqual('empty-envelope-only; nonempty-refused', field['contentStatus'])
        envelope = self.cases['49723-six-outer-fields-with-unresolved-empty-envelope']['result']['PKJBKFHECBE']
        self.assertEqual(dict(untracedType=72829, content='empty-envelope-only'), envelope)
        for row in self.report['rejections']:
            if row['scope'] == 'explicit-scope-refusal': self.assertIn('Nonempty unresolved Object owner refused: 72829', row['error'])

    def test_client_and_full_scene_acceptance_are_not_claimed(self):
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        for flag in ('targetProtocolReady', 'completeSceneInfoRawClosure', 'fullNetworkHandler', 'fullSceneEntityEffects'):
            self.assertFalse(self.report[flag])
        self.assertFalse(self.report['nativeCoverage']['completeSceneInfoRawClosure'])


if __name__ == '__main__': unittest.main()
