"""Tamper checks over the real native/protoc evidence and fixed wire vectors."""
import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_scene_nested import validate_summary, length_field


class SceneNestedEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads((Path(__file__).resolve().parents[1] / 'protocol/cn-7.2/scene-nested-observations.json').read_text('utf8'))

    def reject(self, change):
        document = copy.deepcopy(self.evidence)
        change(document)
        with self.assertRaises(ValueError): validate_summary(document)

    def test_complete_recorded_evidence(self):
        validate_summary(self.evidence)

    def test_fixed_length_wire_vectors_are_not_native_writer_roundtrips(self):
        self.assertEqual(length_field(395, bytes.fromhex('1801')).hex(), 'da18021801')
        self.assertEqual(length_field(1357, b'').hex(), 'ea5400')

    def test_changed_inputs_and_unverified_semantics_rejected(self):
        self.reject(lambda d: d['inputSHA256'].__setitem__('YuanShen.exe', '0' * 64))
        self.reject(lambda d: d.__setitem__('businessSemantics', 'verified'))

    def test_missing_protoc_and_independent_boundaries_rejected(self):
        self.reject(lambda d: d.__setitem__('independentProtocFixtures', 0))
        self.reject(lambda d: d['observations'].pop())

    def test_raw_field_tag_or_status_cannot_be_promoted(self):
        self.reject(lambda d: d['messages'][0]['fields'][0].__setitem__('tag', 1))
        self.reject(lambda d: d['messages'][0]['fields'][0].__setitem__('semanticStatus', 'verified'))

    def test_modeled_reader_or_omitted_constructor_not_native_proof(self):
        self.reject(lambda d: d['observations'][0]['models'].extend(d['observations'][0]['executedReaders']))
        self.reject(lambda d: d['observations'][0]['executedConstructors'].clear())

    def test_inner_and_outer_uint64_must_survive(self):
        def truncate(document, name, offset):
            row = next(r for r in document['observations'] if r['name'] == name)
            row['storedObject']['fields'][offset] &= 0xFFFFFFFF
            row['expected']['fields'][offset] &= 0xFFFFFFFF
        self.reject(lambda d: truncate(d, '36137-maximum', '0x20'))
        self.reject(lambda d: truncate(d, 'scene-all-nonempty', '0x68'))

    def test_native_errors_model_errors_and_accepted_limit_stay_distinct(self):
        self.reject(lambda d: d['rejections'][1].__setitem__('origin', 'native-error-branch'))
        self.reject(lambda d: next(r for r in d['observations'] if r['name'] == 'INTMAX-empty-child-fixture-accepted').__setitem__('boundaryScope', None))
        self.reject(lambda d: d.__setitem__('outsideScope', []))


if __name__ == '__main__': unittest.main()
