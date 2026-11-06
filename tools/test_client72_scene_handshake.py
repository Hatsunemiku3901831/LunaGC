"""Independent vectors and fail-closed checks for the scene handshake evidence."""
import copy
import hashlib
import json
from pathlib import Path
import unittest

from tools import client72_scene_handshake as subject

ROOT = Path(__file__).resolve().parents[1]


class SceneHandshakeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.summary = json.loads((ROOT / 'protocol/cn-7.2/scene-handshake-observations.json').read_text('utf8'))

    def test_complete_evidence_scope(self):
        subject.validate_summary(self.summary)
        self.assertEqual(len(self.summary['semanticBindings']), 13)
        self.assertFalse(self.summary['targetProtocolReady'])
        self.assertEqual(self.summary['postDispatchWrapper']['forwardsAt'], '0x1507d1da2')
        self.assertIsNotNone(next(r for r in self.summary['roles'] if r['cmdId'] == 1017)['commandDomainAmbiguity'])

    def test_independent_request_wire_vectors(self):
        samples = {r['name']: r for r in self.summary['requestObservations']}
        # Manually specified target writer outputs, including logical zero's
        # nonzero encoded storage; these are not re-encoded by the schema under test.
        self.assertEqual(samples['EnterSceneReadyReq-0']['wire'], '18c4e904')
        self.assertEqual(samples['EnterSceneReadyReq-1']['wire'], '18c3e904')
        self.assertEqual(samples['SceneInitFinishReq-0']['wire'], '58c09e05')
        self.assertEqual(samples['EnterSceneDoneReq-0']['wire'], '60abb4feff0f')
        self.assertEqual(samples['PostEnterSceneReq-0']['wire'], '30ccaafeff0f')
        self.assertEqual(samples['PostEnterSceneReq-1']['doubleBits'], 0xC0C81CA000000000)
        self.assertEqual(samples['PostEnterSceneReq-2147483648']['doubleBits'], 0x4340000000000001)

    def test_response_encoded_defaults_are_not_explicit_zero(self):
        samples = {r['name']: r for r in self.summary['readerObservations']}
        defaults = {
            17278: {'0x18': 0xC18A, '0x1c': 0x9435},
            36349: {'0x18': 0xFFFF7F50, '0x1c': 0xFFFF2E48},
            74698: {'0x18': None, '0x20': 0xFFFFAE74, '0x24': 0x3966},
            51326: {'0x18': 0xFFFF8333, '0x1c': 0x12F00},
        }
        for index, expected in defaults.items():
            self.assertEqual(samples[f'{index}-empty']['storedObject']['fields'], expected)
            explicit = samples[f'{index}-duplicate-explicit-zero']['storedObject']['fields']
            self.assertEqual(explicit, {k: None if v is None else 0 for k, v in expected.items()})
            self.assertNotEqual(explicit, expected)

    def test_independent_response_arithmetic_vectors(self):
        # Target constructor constants independently establish logical zero.
        for index, token, code in ((17278, 0xC18A, 0x9435), (36349, 0xFFFF7F50, 0xFFFF2E48),
                                   (74698, 0x3966, 0xFFFFAE74), (51326, 0x12F00, 0xFFFF8333)):
            self.assertEqual(subject.raw_response_token(index, 0), token)
            self.assertEqual(subject.raw_retcode(index, 0), code)
        self.assertEqual([subject.logical_response_token(i, 0) for i in (17278, 36349, 74698, 51326)],
                         [0xFFFF418A, 0x10F30, 0x397A, 0xFFFF5100])
        self.assertEqual([subject.logical_retcode(i, 0) for i in (17278, 36349, 74698, 51326)],
                         [-37833, 72120, 54156, 33587])

    def test_nonempty_nested_and_explicit_zero_merge(self):
        samples = {r['name']: r for r in self.summary['readerObservations']}
        nested = samples['done-nonempty-nested']['storedObject']['fields']['0x18']['fields']
        self.assertEqual((nested['0x20'], nested['0x24']), (0x80000001, 0xFFFFFFFF))
        self.assertEqual(nested['0x18']['fields'], {'0x18': 0x3FA00000, '0x1c': 0x47800000, '0x20': 0xC0200000})
        merged = samples['done-duplicate-nested-explicit-zero']['storedObject']['fields']['0x18']['fields']
        self.assertEqual((merged['0x20'], merged['0x24']), (0, 0))
        self.assertEqual(merged['0x18']['fields'], {'0x18': 0, '0x1c': 0x47800000, '0x20': 0xC0200000})

    def test_tokens_are_named_through_native_missing_key_path(self):
        for row in self.summary['responseTokenObservations']:
            self.assertEqual(row['namedLiteralIndex'], 52998)
            self.assertEqual(row['lookupResult'], 'missing-key-native')
            self.assertEqual(row['logicalToken'], row['observedNamedToken'])
        raw_zero = [r for r in self.summary['responseTokenObservations'] if r['name'].endswith('raw-zero')]
        self.assertEqual(len(raw_zero), 4)
        self.assertTrue(all(r['rawToken'] == 0 and r['logicalToken'] != 0 for r in raw_zero))
        post = [r for r in self.summary['requestTokenLinkObservations'] if r['typeIndex'] == 74076]
        self.assertTrue(all(not r['schedulerExecuted'] and r['producerPathSHA256'] for r in post))

    def test_raw_proto_and_existing_uint64_list_sources_are_pinned(self):
        path = ROOT / 'protocol/cn-7.2/scene-handshake-research.proto'
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), self.summary['protoSHA256'])
        self.assertEqual(path.read_text('utf8'), subject.proto_text(self.summary['messages']))
        for name, digest in subject.REUSED_EVIDENCE.items():
            self.assertEqual(hashlib.sha256((path.parent / name).read_bytes()).hexdigest(), digest)
        old = json.loads((path.parent / 'scene-nested-observations.json').read_text('utf8'))
        observations = {r['name']: r for r in old['observations']}
        self.assertEqual(observations['scene-all-nonempty']['storedObject']['fields']['0x68'], 0xFEDCBA9876543210)
        self.assertEqual(observations['36137-maximum']['storedObject']['fields']['0x20'], 0xFFFFFFFFFFFFFFFF)

    def test_changed_input_or_tag_is_rejected(self):
        changed = copy.deepcopy(self.summary); changed['inputSHA256']['YuanShen.exe'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'fixed inputs'): subject.validate_summary(changed)
        changed = copy.deepcopy(self.summary); changed['messages'][0]['fields'][0]['tag'] += 1
        with self.assertRaisesRegex(ValueError, 'wire schema'): subject.validate_summary(changed)

    def test_missing_or_promoted_evidence_is_rejected(self):
        changed = copy.deepcopy(self.summary); changed['responseTokenObservations'].pop()
        with self.assertRaisesRegex(ValueError, 'coverage incomplete'): subject.validate_summary(changed)
        changed = copy.deepcopy(self.summary); changed['messages'][0]['fields'][0]['semanticStatus'] = 'verified'
        with self.assertRaisesRegex(ValueError, 'silently promoted'): subject.validate_summary(changed)
        changed = copy.deepcopy(self.summary); changed['semanticBindings'].append({'typeIndex': 74076, 'tag': 1, 'name': 'client_time'})
        with self.assertRaisesRegex(ValueError, 'semantic boundary'): subject.validate_summary(changed)

    def test_error_origin_and_actual_consumption_are_enforced(self):
        changed = copy.deepcopy(self.summary); changed['rejections'][0]['origin'] = 'native-error-branch'
        with self.assertRaisesRegex(ValueError, 'rejection scope'): subject.validate_summary(changed)
        changed = copy.deepcopy(self.summary); changed['semanticObservations'][1]['observedRetcodeBits'] = 0
        with self.assertRaisesRegex(ValueError, 'application/wire arithmetic'): subject.validate_summary(changed)
        changed = copy.deepcopy(self.summary); changed['responseTokenObservations'][0]['lookupResult'] = 'modeled-success'
        with self.assertRaisesRegex(ValueError, 'lookup provenance'): subject.validate_summary(changed)


if __name__ == '__main__': unittest.main()
