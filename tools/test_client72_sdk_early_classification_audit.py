import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

from . import client72_sdk_early_classification_audit as audit


class EarlyClassificationAuditTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = audit.frozen_inputs()
        cls.cases = {row['case']: row for row in audit.owned_cases()}

    def test_live_accepted_sources_artifacts_and_actual_state_join(self):
        result = audit.audit_epoch(self.files)
        self.assertTrue(result['liveAcceptedSourceAndBuildEpochEqual'])
        self.assertTrue(result['actualArtifactAndFixtureHashesEqual'])
        self.assertEqual(result['stableHistoricalEvent'], 5)
        self.assertTrue(result['naturalNameLimitExcluded'])
        self.assertFalse(result['naturalOriginDisambiguated'])

    def test_source_identity_failure_stops_before_analysis(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'source.rs').write_bytes(b'changed source')
            with patch.dict(audit.SOURCE_PINS, {'source.rs': audit.digest(b'frozen source')}, clear=True):
                with self.assertRaisesRegex(ValueError, 'Frozen source/evidence identity differs'):
                    audit.frozen_inputs(root)

    def test_mixed_epoch_is_rejected(self):
        for field in ('artifactAuditSHA256', 'productionSHA256', 'fixtureSHA256', 'manifestSHA256'):
            files = dict(self.files)
            live = json.loads(files[audit.EARLY + 'live2.private.json'])
            live['inputs'][field] = 'different-epoch'
            files[audit.EARLY + 'live2.private.json'] = json.dumps(live).encode()
            with self.subTest(field=field), self.assertRaises(ValueError):
                audit.audit_epoch(files)

    def test_natural_state_must_match_both_reads(self):
        files = dict(self.files)
        key = audit.EARLY + 'live2.private.json.reads.private.json'
        ledger = json.loads(files[key])
        changed = bytearray.fromhex(ledger['reads'][-1]['bytesHex'])
        struct.pack_into('<I', changed, 56, 2)
        ledger['reads'][-1]['bytesHex'] = changed.hex()
        files[key] = json.dumps(ledger).encode()
        with self.assertRaisesRegex(ValueError, 'Frozen natural state differs'):
            audit.audit_epoch(files)

    def test_actual_bridge_caller_and_helper_representation(self):
        result = audit.audit_static_paths(self.files)
        self.assertEqual(result['registers'], dict(rcx='this', rdx='managed name', r8='managed payload'))
        self.assertEqual(result['charsOffset'], 20)
        self.assertTrue(result['exactBridgeAndImplementationInstructionsEqual'])
        self.assertTrue(result['exactMarshalHelperInstructionsEqual'])
        self.assertEqual(result['nameFailureEvents'], [3, 9])
        self.assertEqual(result['payloadLimitExitCount'], 2)
        self.assertFalse(result['classifierCanEmitLimit'])

    def test_unknown_and_nonmatching_name_do_not_read_payload(self):
        unknown = self.cases['unknown-caller']
        self.assertEqual((unknown['nameReads'], unknown['payloadReads']), (0, 0))
        for label in ('long-name', 'case-name', 'swapped-parameters', 'unreadable-name'):
            with self.subTest(label=label):
                self.assertEqual(self.cases[label]['payloadReads'], 0)
                self.assertNotEqual(self.cases[label]['event'], 5)
        self.assertEqual(self.cases['long-name']['nameReads'], 13)

    def test_last_unit_includes_nul_and_next_slot_nul_is_unseen(self):
        accepted = self.cases['unit-last-slot-nul']
        rejected = self.cases['unit-next-slot-real-nul']
        self.assertEqual((accepted['event'], accepted['payloadReads']), (2, audit.MAX_UNITS))
        self.assertEqual((rejected['event'], rejected['payloadReads']), (5, audit.MAX_UNITS))
        self.assertEqual(rejected['origin'], 'payload-unit-scan-limit')

    def test_terminated_and_unterminated_same_prefix_are_indistinguishable(self):
        name, body = 0x8000000200000000, 0x8000000300000000
        prefix = audit.packet(ignored='a' * (audit.MAX_UNITS - len(audit.packet(ignored=''))))
        traces = []
        for terminated in (True, False):
            memory = audit.OwnedMemory([(name, audit.managed('local_config')),
                                        (body, audit.managed(prefix, terminated=terminated))])
            outcome = audit.inspect_owned(memory, name, body)
            self.assertEqual((outcome.event, outcome.origin), (5, 'payload-unit-scan-limit'))
            traces.append(memory.reads)
        self.assertEqual(traces[0], traces[1])

    def test_utf8_limit_is_independent_of_unit_limit(self):
        accepted = self.cases['utf8-last-allowed-byte']
        rejected = self.cases['utf8-first-rejected-byte']
        self.assertEqual(accepted['event'], 2)
        self.assertEqual((rejected['event'], rejected['origin']), (5, 'payload-utf8-limit'))
        self.assertLess(rejected['payloadReads'], audit.MAX_UNITS)
        for target in (audit.MAX_UTF8_BYTES, audit.MAX_UTF8_BYTES + 1):
            text = audit.utf8_boundary_packet(target)
            self.assertEqual(len(text.encode()), target)
            self.assertIsInstance(json.loads(text), dict)

    def test_utf16_decode_precedence_requires_observed_nul(self):
        name, body = 0x8000000200000000, 0x8000000300000000
        for count, terminated, expected in ((1, True, 10), (audit.MAX_UNITS, False, 5)):
            memory = audit.OwnedMemory([(name, audit.managed('local_config')),
                                        (body, audit.managed(units=[0xD800] * count, terminated=terminated))])
            self.assertEqual(audit.inspect_owned(memory, name, body).event, expected)

    def test_high64_uses_chars_only_and_stops_at_first_nul(self):
        name, body = 0x8000000200000000, 0x8000000300000000
        name_bytes, body_bytes = audit.managed('local_config'), audit.managed(audit.packet())
        memory = audit.OwnedMemory([(name, name_bytes), (body, body_bytes + b'OWNED_UNREAD_TAIL')])
        self.assertEqual(audit.inspect_owned(memory, name, body).event, 2)
        self.assertEqual(memory.reads[0], name + 20)
        self.assertEqual(memory.reads[13], body + 20)
        self.assertEqual(memory.reads[-1] + 2, body + len(body_bytes))
        before = list(memory.reads)
        audit.inspect_owned(memory, name, body, terminal=True)
        self.assertEqual(memory.reads, before)

    def test_shape_and_reference_failures_do_not_become_limit(self):
        self.assertEqual(self.cases['url-object']['event'], 8)
        mismatch = self.cases['reference-mismatch']
        self.assertEqual(mismatch['event'], 2)
        self.assertFalse(mismatch['keyMatches'])
        self.assertTrue(mismatch['urlMatches'])
        for value in (True, -1, 1.25, 4294967296):
            self.assertEqual(audit.classify_owned(audit.packet(env=value)).event, 7)
        self.assertEqual(audit.classify_owned(audit.packet(env=1.0)).event, 2)

    def test_output_contains_only_fixed_diagnosis_and_owned_counts(self):
        report = audit.run_audit()
        encoded = json.dumps(report)
        for sentinel in (audit.KEY, audit.URL, 'OWNED_BODY_SENTINEL', 'OWNED_TAIL_SENTINEL'):
            self.assertNotIn(sentinel, encoded)
        for flag in ('naturalPayloadRead', 'naturalPayloadHashOrLengthRecorded', 'nativeMarshalExecuted',
                     'nativeExportObserved', 'writerReturnObserved', 'naturalInitEnvOrRoutePassed', 'd02Passed'):
            self.assertFalse(report[flag])


if __name__ == '__main__':
    unittest.main()
