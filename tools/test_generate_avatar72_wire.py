import copy
import json
from pathlib import Path
import unittest
from types import SimpleNamespace
from tools.generate_avatar72_wire import OUTPUT, render_sources, load_evidence, native_cases
from tools.trace_client72_avatar_wire_adapter import verify_java, float_bits

ROOT = Path(__file__).resolve().parents[1]


class AvatarWireGeneratorTests(unittest.TestCase):
    def setUp(self): self.raw, self.sources = load_evidence(ROOT / 'protocol/cn-7.2')

    def test_only_separate_raw_namespace_and_deterministic_source(self):
        outputs = render_sources(self.raw, self.sources); self.assertEqual({OUTPUT}, set(outputs))
        self.assertEqual((ROOT / OUTPUT).read_bytes(), outputs[OUTPUT].encode('utf8')); self.assertIn('package cn72.avatarwire;', outputs[OUTPUT])
        self.assertEqual(65, outputs[OUTPUT].count('\nmessage ')); self.assertEqual(7, outputs[OUTPUT].count('  oneof ')); self.assertEqual(42, outputs[OUTPUT].count('[packed = true]'))

    def test_typed_message_maps_double_objects_and_optional_scalars(self):
        source = render_sources(self.raw, self.sources)[OUTPUT]
        for text in ('map<uint32, CFCDAEBPLOG> CNCCIILAJNJ = 5;', 'map<uint32, CODAEKFNEEE> IKIFIBAEDGD = 3;',
            'map<uint32, float> ADJINCFKMML = 7;', 'map<string, uint32> JMPEPGDDKPD = 1;',
            'uint32 IPECOOBLNDN_1 = 1;', 'uint32 IPECOOBLNDN_2 = 2;', 'IDNNFOFBALH IDBPHIMMHJP_100 = 100;',
            'optional int64 ANKEOLKBECN = 1;', 'repeated int32 MLKKEOCOAKN = 18 [packed = true];'):
            self.assertIn(text, source)
        self.assertNotIn('enum ', source); self.assertNotIn('PNOEONGHLPL =', source); self.assertNotIn('BFCOKBPMILJ =', source)

    def test_schema_wire_oneof_fixture_and_runtime_drift_rejected(self):
        for change in ('schema', 'wire', 'oneof', 'runtime', 'claim'):
            raw = copy.deepcopy(self.raw)
            if change == 'schema': raw['closureMessages'][0]['fields'][0]['tag'] = 99
            elif change == 'wire': raw['fixtures'][0]['wire'] = ''
            elif change == 'oneof': raw['messages'][0]['fields'][-1].pop('oneof')
            elif change == 'runtime': raw['runtimeStatus'] = 'passed'
            else: raw['targetProtocolReady'] = True
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, 'raw evidence changed'): render_sources(raw, self.sources)

    def test_all_11_dependencies_are_fixed_not_just_complete_report(self):
        for name in self.sources:
            sources = copy.deepcopy(self.sources); sources[name]['fixtures'][0]['result'] = {'invented': 1}
            with self.subTest(source=name), self.assertRaisesRegex(ValueError, 'dependency evidence changed'): render_sources(self.raw, sources)
        with self.assertRaisesRegex(ValueError, 'dependency evidence changed'): render_sources(self.raw, {})

    def test_line_ending_representation_does_not_change_evidence(self):
        raw = json.loads(json.dumps(self.raw, indent=2).replace('\n', '\r\n')); sources = {n: json.loads(json.dumps(r, indent=2).replace('\n', '\r\n')) for n, r in self.sources.items()}
        self.assertEqual(render_sources(self.raw, self.sources), render_sources(raw, sources))

    def test_oracle_cases_are_native_complete_and_keep_merge_boundaries(self):
        rows = native_cases(self.raw, self.sources); self.assertEqual(len(rows), len({(x['source'], x['name']) for x in rows}))
        self.assertTrue(all('untracedType' not in json.dumps(x['expected']) for x in rows))
        self.assertFalse(any(x['name'] == 'signed-negative-packed-length-no-append' for x in rows))
        self.assertTrue(any(x['name'] == 'Avatar-complete-all-10-nonempty' for x in rows)); self.assertTrue(any(x['name'] == 'tag100-repeated-copy-merge-and-empty-preserves' for x in rows))

    def test_actual_native_result_drift_cannot_use_java_as_its_own_oracle(self):
        cases = {('fixed', 'independent'): dict(wire='1007', expected={'IPECOOBLNDN_2': 7})}
        rows = [dict(source='fixed', name='independent', typeIndex=51050, bytes=bytes.fromhex('1007'), wire='1007', generatedClass='test')]
        with self.assertRaisesRegex(ValueError, 'Java wire/native object mismatch'):
            verify_java(SimpleNamespace(run_graph=lambda *args: dict(result={'IPECOOBLNDN_2': 8})), rows, cases)

    def test_negative_zero_loss_is_rejected_even_when_python_numbers_compare_equal(self):
        self.assertNotEqual(float_bits({'list': [-0.0]}), float_bits({'list': [0.0]}))
        cases = {('fixed', 'negative-zero'): dict(wire='5d00000080', expected={'KGDFJBJMNNJ': [-0.0]})}
        rows = [dict(source='fixed', name='negative-zero', typeIndex=51050, bytes=bytes.fromhex('5d00000080'), wire='5d00000080', generatedClass='test')]
        with self.assertRaisesRegex(ValueError, 'Java wire/native object mismatch'):
            verify_java(SimpleNamespace(run_graph=lambda *args: dict(result={'KGDFJBJMNNJ': [0.0]})), rows, cases)


if __name__ == '__main__': unittest.main()
