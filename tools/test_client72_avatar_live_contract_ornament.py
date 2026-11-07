"""Independent assertions over the exact Ornament parser evidence."""
import json
import os
import unittest
from pathlib import Path


class OrnamentContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path(os.environ.get('CLIENT72_ORNAMENT_REPORT',
            'local/migration72/d02-active/avatar/ornament/first.json')).read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_fixed_candidates_and_exact_native_names(self):
        inputs = self.report['candidateSources']
        self.assertEqual('f1a4885c2fdff06021263e4d6627feca4ff135547820e85a814eecb6495122da', inputs['AvatarOrnamentGroupExcelConfigData.json']['sha256'])
        self.assertEqual('aad42c01df12aaa4956374226e26db11cdbd3d72a5586e4c9bf2e4415ae2287a', inputs['AvatarOrnamentExcelConfigData.json']['sha256'])
        actual = {(row['owner'], row['name']): row['offset'] for row in self.report['declarations']['exactCandidateKeys']}
        self.assertEqual({(61286, 'DLGBPJPJMHP'): '0x1c', (44799, 'PBMHHJCEJPJ'): '0x2c',
                          (44799, 'DENCEMCMLHM'): '0x18', (44799, 'EFFKPNKJHBP'): '0x20'}, actual)

    def test_group_omission_and_explicit_zero_clear_old_values(self):
        for name, length, literal in (('group-omitted', 2, 'bc56'),
                ('group-explicit-zero', 18, 'e9563b23b60567e013d8ab03f9d45f64dacb')):
            row = self.rows[name]
            self.assertEqual([777] * 4, row['oldLogicalValues'])
            self.assertEqual([0, 0, 0], row['decoded'])
            self.assertEqual(0, row['plainField14'])
            self.assertEqual((length, literal), (row['cursor'], row['binaryHex']))
            self.assertEqual('0x620000100', row['trace']['stop'])

    def test_group_nonzero_fields_have_independent_destinations(self):
        row = self.rows['group-distinct-nonzero']
        self.assertEqual([11, 22, 44], row['decoded'])
        self.assertEqual(33, row['plainField14'])
        self.assertEqual('e9563023b6055de013d8cc03f9d48b64dacb', row['binaryHex'])
        row = self.rows['group-high-bits']
        self.assertEqual([0xffffffff, 0x80000001, 0xffffffff], row['decoded'])
        self.assertEqual(0xfffffffe, row['plainField14'])

    def test_group_builder_key_uses_avatar_field_not_group_or_default(self):
        expected = {'group-omitted': 0, 'group-explicit-zero': 0, 'group-distinct-nonzero': 22,
                    'group-high-bits': 0x80000001, 'candidate-exact-DL-only': 0}
        for prefix, key in expected.items():
            row = self.rows[prefix + '-builder-key']
            self.assertEqual(key, row['key'])
            self.assertEqual('0x18', row['encodedSourceOffset'])
            self.assertTrue(row['dictionaryArgumentPreserved'])
            self.assertEqual('0x15437b14c', row['trace']['stop'])
            self.assertEqual([], row['trace']['models'])

    def test_ornament_omission_clears_dirty_scalar_string_and_array(self):
        row = self.rows['ornament-omitted']
        self.assertEqual([777] * 4, row['oldLogicalValues'])
        self.assertEqual(('old', 3), (row['oldText'], row['oldConditionCount']))
        self.assertEqual([0, 0, 0, 0], [row[key] for key in ('id', 'group', 'plainField28', 'logic')])
        self.assertEqual(('', 0, 2, '367c'), (row['text'], row['conditionCount'], row['cursor'], row['binaryHex']))

    def test_ornament_nonzero_high_bits_and_unicode(self):
        for name, values in (('ornament-distinct-nonzero', [42, 73, 99, 2]),
                             ('ornament-high-bits', [0xffffffff, 0x80000001, 0xfffffffe, 0xffffffff])):
            row = self.rows[name]
            self.assertEqual(values, [row[key] for key in ('id', 'group', 'plainField28', 'logic')])
            self.assertEqual('0x620000100', row['trace']['stop'])
        self.assertEqual('风-7.2', self.rows['ornament-short-unicode']['text'])
        self.assertEqual(27, self.rows['ornament-short-unicode']['cursor'])

    def test_exact_candidate_integer_projection_has_literal_bytes(self):
        row = self.rows['candidate-exact-DL-only']
        self.assertEqual([0, 0, 101], row['decoded'])
        self.assertEqual('bd56c464dacb', row['binaryHex'])
        for value, literal in ((101, '267cdf7ec336'), (102, '267ce07ec336')):
            row = self.rows['candidate-exact-PBM-' + str(value)]
            self.assertEqual(value, row['id'])
            self.assertEqual(literal, row['binaryHex'])

    def test_candidate_strings_cover_native_63_and_64_byte_paths(self):
        expected = {101: 'ART/UI/Atlas/TutorialPic/UI_QuestPicture_OrnamentM_Normal_Alpha',
                    102: 'ART/UI/Atlas/TutorialPic/UI_QuestPicture_OrnamentM_Special_Alpha'}
        for value, text in expected.items():
            row = self.rows['candidate-selected-exact-keys-' + str(value)]
            self.assertEqual(text, row['text'])
            self.assertEqual(value, row['id'])
            self.assertEqual(63 if value == 101 else 64, len(text.encode('utf8')))
            self.assertEqual(len(bytes.fromhex(row['binaryHex'])), row['cursor'])
            self.assertEqual('0x620000100', row['trace']['stop'])
            self.assertNotIn('0x1405070c0', row['trace']['models'])
        symbols = {x['symbol']: x['value'] for x in self.report['declarations']['logicEnum'][0]['values']}
        self.assertEqual(1, symbols['LOGIC_AND'])
        self.assertEqual(1, self.rows['candidate-selected-exact-keys-102']['logic'])

    def test_unknown_array_large_scratch_and_errors_do_not_become_success(self):
        expected = {'nested-condition-array-before-parser': '0x14e7e6570',
                    'string-65-bytes-before-scratch-allocator': '0x14a6fe614',
                    'group-required-scalar-truncated': '0x1404c2650', 'group-null-reader': '0x1404c2be0'}
        for name, stop in expected.items():
            self.assertEqual(stop, self.rows[name]['trace']['stop'])
            self.assertNotIn(stop, self.rows[name]['trace']['models'])

    def test_candidate_absence_never_promotes_entry_readiness(self):
        self.assertEqual(23, len(self.rows))
        group = self.report['candidateSources']['AvatarOrnamentGroupExcelConfigData.json']['rows']
        self.assertEqual([10000136], [row['avatarId'] for row in group])
        for key in ('completeOrnamentConditionParser', 'completeCacheBuilder', 'candidateRuntimeRevisionVerified',
                    'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[key])
        self.assertTrue(self.report['completeGroupScalarParser'])
        self.assertEqual('not-tested', self.report['runtimeStatus'])


if __name__ == '__main__':
    unittest.main()
