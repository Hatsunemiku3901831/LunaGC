import json
import struct
import unittest

from . import client72_sdk_scan_limit_followup as follow


class ScanLimitFollowupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files, cls.pins = follow.frozen_inputs()
        cls.fields, cls.static = follow.metadata_and_windows(cls.files)
        cls.rows, cls.assets = follow.source_assets(cls.files)
        cls.cases = {row['case']: row['observation'] for row in follow.owned_cases()}

    def test_exact_epoch_and_natural_unit_limit(self):
        result = follow.audit_epoch(self.files)
        self.assertTrue(result['liveSourceAcceptedArtifactAndFixtureJoined'])
        self.assertNotIn('patch/Cargo.toml', self.pins)
        self.assertEqual(self.pins[follow.MANIFEST_SNAPSHOT], '3ea4ff0853353f3070b818935c20e6e56b21219f64bea983e4aec8906ed8dd65')
        self.assertEqual((result['naturalEvent'], result['frozenPins'], result['newSources']), (11, 55, 5))
        for field in ('utf8LimitEventObserved', 'classificationSucceeded', 'naturalPayloadLengthKnown', 'missingNulProved'):
            self.assertFalse(result[field])

    def test_changed_raw_terminal_cannot_be_joined(self):
        files = dict(self.files)
        path = follow.SPLIT + 'live3.private.json.reads.private.json'
        ledger = json.loads(files[path])
        changed = bytearray.fromhex(ledger['reads'][-1]['bytesHex'])
        struct.pack_into('<I', changed, 56, 12)
        ledger['reads'][-1]['bytesHex'] = changed.hex()
        files[path] = json.dumps(ledger).encode()
        with self.assertRaisesRegex(ValueError, 'Frozen event11 state differs'):
            follow.audit_epoch(files)

    def test_two_whole_new_functions_and_string_representation(self):
        self.assertEqual([r['bytes'] for r in self.static['newCompleteWindows']], [326, 322])
        self.assertEqual(len(self.fields), 695)
        self.assertEqual((self.static['sourceStringLengthOffset'], self.static['sourceCharsOffset']), (16, 20))
        self.assertTrue(self.static['allocationReservesExtraUnit'])
        self.assertFalse(self.static['allocatorZeroOrNulWriteProved'])
        self.assertFalse(self.static['serializer140d35430FieldCoverageProved'])

    def test_same_basename_does_not_select_region_as_language(self):
        prefix = 'assets/plugins/mihoyosdk/resources/'
        language = self.assets[prefix + 'language/zh-cn.json']
        region = self.assets[prefix + 'region/zh-cn.json']
        self.assertNotEqual(set(language), set(region))
        self.assertGreater(sum(k in language for k in self.fields), sum(k in region for k in self.fields))

    def test_source_projection_language_changes_boundary_for_all_cn_candidates(self):
        results = follow.source_projection_cases(self.rows, self.assets, self.fields)
        self.assertEqual(len(results), 24)
        for row in results:
            with self.subTest(env=row['cnEnv'], kind=row['inputKind']):
                if row['inputKind'] == 'without-language':
                    self.assertFalse(row['unitBudgetExhausted'])
                    self.assertIsNone(row['ownedPrefixObservation'])
                else:
                    self.assertTrue(row['unitBudgetExhausted'])
                    observation = row['ownedPrefixObservation']
                    self.assertEqual(observation['event'], 13)
                    self.assertTrue(observation['appKeyMatches'])
                    self.assertTrue(observation['comboUrlMatches'])
                    self.assertFalse(observation['completePayloadValidated'])

    def test_only_complete_required_fields_before_open_language_qualify(self):
        self.assertEqual(self.cases['language-after-required-fields']['event'], 13)
        for label in ('language-before-url', 'cut-inside-url', 'fake-keys-in-other-string',
                      'duplicate-required-key', 'wrong-env', 'wrong-url-shape',
                      'closed-language-then-other-large-field'):
            with self.subTest(label=label):
                self.assertEqual(self.cases[label], dict(event=11, prefixFieldsObserved=False))

    def test_reference_mismatch_is_observed_as_false(self):
        key, url = self.cases['key-mismatch-stays-false'], self.cases['url-mismatch-stays-false']
        self.assertEqual((key['event'], url['event']), (13, 13))
        self.assertFalse(key['appKeyMatches'])
        self.assertTrue(key['comboUrlMatches'])
        self.assertTrue(url['appKeyMatches'])
        self.assertFalse(url['comboUrlMatches'])

    def test_malformed_prefix_does_not_become_a_fact(self):
        for label in ('invalid-escape-in-language', 'invalid-utf16-in-required-prefix'):
            self.assertEqual(self.cases[label]['event'], 11)
        base = dict(env=0, app_key=follow.KEY, url=follow.compact(dict(comboLogin=follow.URL)))
        for text in (follow.compact(dict(base, language='a' * follow.MAX_UNITS)).replace('"env":0,', '"env":0 ', 1),
                     follow.compact(dict(base, language='a' * follow.MAX_UNITS)).replace('"env":0', '"env":true', 1)):
            self.assertEqual(follow.observe_owned_prefix(text.encode('utf-16-le')[:follow.MAX_UNITS * 2])['event'], 11)

    def test_partial_unicode_escape_can_remain_open_without_repair(self):
        for tail in ('"abc', '"abc\\', '"abc\\u1', '"abc\\u12ab'):
            self.assertTrue(follow._open_string_tail(tail))
        for tail in ('abc', '"closed"', '"bad\\q', '"bad\n'):
            self.assertFalse(follow._open_string_tail(tail))

    def test_unknown_suffix_and_nul_are_never_validated(self):
        known = self.cases['language-after-required-fields']
        unknown = self.cases['unobserved-suffix-is-not-validated']
        self.assertEqual(known, unknown)
        for row in (known, unknown):
            self.assertFalse(row['completePayloadValidated'])
            self.assertFalse(row['payloadTerminatorObserved'])
            self.assertFalse(row['originalMarshalObserved'])
            self.assertFalse(row['naturalInitOrRoutePassed'])

    def test_consumed_prefix_retains_utf8_limit(self):
        text = follow.compact(dict(gamebiz='\u0800' * 23000, env=0, app_key=follow.KEY,
                                   url=follow.compact(dict(comboLogin=follow.URL)), language='a' * follow.MAX_UNITS))
        raw = text.encode('utf-16-le')[:follow.MAX_UNITS * 2]
        self.assertEqual(follow.observe_owned_prefix(raw)['event'], 11)

    def test_report_has_no_owned_or_source_content(self):
        report = follow.run_audit()
        encoded = json.dumps(report)
        for sentinel in (follow.KEY, follow.URL, 'INVALID_UNOBSERVED_SUFFIX'):
            self.assertNotIn(sentinel, encoded)
        for field in ('naturalScanLimitCauseProved', 'naturalPrefixObserved', 'naturalPayloadRead',
                      'naturalLengthHashOrBodyRecorded', 'newNativeExecution', 'originalMarshalExecuted',
                      'naturalInitEnvOrRoutePassed', 'd02Passed'):
            self.assertFalse(report[field])


if __name__ == '__main__':
    unittest.main()
