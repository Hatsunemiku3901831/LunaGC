"""Independent assertions over a freshly generated offline native report.

Generate with client72_avatar_live_contract.py; select another output with
CLIENT72_AVATAR_LIVE_REPORT. No game process, database or Gradle is used.
"""
import json
import os
import unittest
from pathlib import Path


class AvatarLiveContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get('CLIENT72_AVATAR_LIVE_REPORT',
                                  'local/migration72/d02-active/avatar/first.json'))
        cls.report = json.loads(path.read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_fixed_inputs_and_no_readiness_promotion(self):
        self.assertEqual('8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5',
                         self.report['inputSHA256']['YuanShen.exe'])
        self.assertEqual('45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a',
                         self.report['inputSHA256']['YuanShen_Data/Managed/Metadata/global-metadata.dat'])
        self.assertEqual('b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105',
                         self.report['inputSHA256']['YuanShen_Data/Managed/Metadata/startup-metadata.dat'])
        self.assertTrue(self.report['completeWarmManagedConstructor'])
        for name in ('completeConstructor', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[name])
        self.assertEqual('not-tested', self.report['runtimeStatus'])

    def test_leaf_is_not_rejected_for_absent_unwind(self):
        declared = self.report['declarations']
        leaf = declared['bootstrap']
        self.assertFalse(leaf['hasUnwindRecord'])
        self.assertLess(int(leaf['previousUnwindEnd'], 16), 0x1402F8450)
        refs = declared['functions']['0x1402f84a0']['instructions']
        self.assertIn({'address': '0x1402f84a7', 'instruction': 'lea rdx, [rip - 0x5e]',
                       'bytes': '488d15a2ffffff'}, refs)
        stores = declared['functions']['0x1402f8450']['instructions']
        self.assertIn({'address': '0x1402f8481', 'instruction': 'mov qword ptr [rip + 0x58d0a68], rax',
                       'bytes': '488905680a8d05'}, stores)

    def test_actual_bootstrap_overwrites_null_and_high_pointer(self):
        for name in ('bootstrap-fresh', 'bootstrap-old'):
            row = self.rows[name]
            self.assertEqual('0x1422f9988', row['flagPointer'])
            self.assertEqual(1, row['flagByte'])
            self.assertEqual([], row['trace']['models'])
            self.assertEqual('0x620000100', row['trace']['stop'])
            # The inherited observer counts the pre-instruction return stop too.
            self.assertEqual(11, len(set(row['trace']['executedAddresses']) - {'0x620000100'}))

    def test_option_is_file_backed_named_default_has_exact_aot(self):
        declared = self.report['declarations']
        self.assertEqual('0101000007000000', declared['bootstrap']['optionBytes'])
        self.assertFalse(declared['bootstrap']['optionSection']['writable'])
        enum = declared['enum']
        self.assertEqual(['System', 'Int32Enum'], enum['classNameLiterals'])
        self.assertEqual('int32', enum['underlyingField']['type']['kind'])
        aot = enum['canonicalAOT']
        self.assertEqual((15591, 65, 352233, 418926, 13283),
                         tuple(aot[key] for key in ('methodDefinition', 'classInstance', 'methodSpec', 'tableIndex', 'pointerIndex')))
        self.assertEqual('0x156fbc0b0', aot['address'])
        self.assertEqual(351, aot['arguments'][0]['typeDefinition'])

    def test_native_canonicalizer_uses_enum_underlying_type(self):
        row = self.rows['enum20060-canonicalized-by-native']
        self.assertEqual(351, row['returnedType'])
        self.assertTrue(row['returnedCanonicalPointer'])
        self.assertEqual([], row['trace']['models'])
        self.assertTrue({'0x1405269f0', '0x15a4d6b60', '0x14052df40', '0x14052de91'}.issubset(row['trace']['executedAddresses']))
        self.assertTrue(self.rows['ordinary-int32-is-not-enum']['originalPointerRetained'])

    def test_hashset_header_is_written_but_other_payload_is_not_cleaned(self):
        for suffix in ('fresh', 'old'):
            row = self.rows['enum-constructor-' + suffix]
            self.assertEqual([0, 0, 0, 0xffffffff], row['headerWords'])
            self.assertTrue(row['comparerPreserved'])
            self.assertEqual([], row['trace']['models'])
        self.assertEqual('00' * 16, self.rows['enum-constructor-fresh']['untouchedPayload'])
        self.assertEqual('7d' * 16, self.rows['enum-constructor-old']['untouchedPayload'])

    def test_full_warm_constructor_is_one_continuous_path(self):
        required = {'0x15183fe60', '0x15183ff11', '0x15183ff13', '0x15183ff71', '0x15183ffb4',
                    '0x15183fff4', '0x151840037', '0x15184033a', '0x156fbc0b0', '0x159111af0',
                    '0x1570f65b0', '0x158878110', '0x157601a80', '0x156381bf0', '0x157d7fe90'}
        for suffix in ('fresh', 'old'):
            row = self.rows['continuous-avatar-constructor-' + suffix]
            self.assertEqual('0x620000100', row['trace']['stop'])
            self.assertTrue(required.issubset(row['trace']['executedAddresses']))
            self.assertEqual({'0x1405336c0'}, set(row['trace']['models']))
            self.assertEqual(31, len(row['trace']['models']))

    def test_all_middle_fields_store_original_closed_types(self):
        for suffix in ('fresh', 'old'):
            state = self.rows['continuous-avatar-constructor-' + suffix]['state']
            self.assertEqual((1, 1), (state['default284'], state['default2b0']))
            middle = state['middleContainers']
            self.assertEqual(20060, middle['0x1e8']['type']['arguments'][0]['typeDefinition'])
            self.assertEqual([32758, 51041], [arg['typeDefinition'] for arg in middle['0x1c0']['type']['arguments']])
            self.assertEqual([34054] * 3, [middle[key]['type']['typeDefinition'] for key in ('0x50', '0x1c8', '0x40')])
            self.assertTrue(all(row['nonNull'] for row in middle.values()))
            self.assertEqual(12, len(state['containers']))

    def test_cold_and_null_inputs_stop_at_distinct_actual_boundaries(self):
        expected = {'cold-class-cache-before-lock-and-class-build': ('0x15a4d6c03', '0x15a4d6c0f'),
                    'null-enum-object-before-native-error': ('0x156fbc0f2', '0x1404c2be0'),
                    'cold-enum-comparer-before-type-resolution': ('0x157d7feeb', '0x140517680')}
        for name, (stop, forbidden_successor) in expected.items():
            trace = self.rows[name]['trace']
            self.assertEqual(stop, trace['stop'])
            self.assertNotIn(forbidden_successor, trace['executedAddresses'])
            self.assertNotIn('0x159b6e720', trace['models'])


if __name__ == '__main__':
    unittest.main()
