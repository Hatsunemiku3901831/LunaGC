"""Independent constructor-definition, canonicalization and native-store checks."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_deep_rgctx import INPUTS
from tools.extract_client72 import HASHES


class AvatarGenericContextTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        cls.report = json.loads(Path(os.environ.get('CLIENT72_AVATAR_RGCTX_OBSERVATIONS',
            cls.root / 'protocol/cn-7.2/avatar-deep-rgctx-observations.json')).read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['native']}

    def test_fixed_inputs_sources_and_partial_claim(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(INPUTS, self.report['inputContracts'])
        self.assertEqual(6, len(self.rows))
        for name, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / name).read_bytes()).hexdigest())
        for flag in ('completeConstructor', 'completeAvatarHandler', 'targetProtocolReady'):
            self.assertFalse(self.report[flag])
        self.assertEqual('not-tested', self.report['runtimeStatus'])

    def test_exact_class_range_definition_words(self):
        self.assertEqual(('0x106fda4', 8928, 1116, '0x40925ec'), tuple(self.report['tables'][key]
            for key in ('classRangeBase', 'classRangeBytes', 'classRangeCount', 'definitionBase')))
        expected = [(2147, 1, '0xc08c700', 575232, 15591, 2147),
                    (2034, 0, '0xc08c5a3', 574883, 14780, 2034),
                    (69137, 1, '0xc08dc1a', 580634, 10374, 1255)]
        self.assertEqual(expected, [tuple(row[key] for key in ('owner', 'slot', 'word', 'methodSpec', 'methodDefinition', 'declaringOwner'))
                                   for row in self.report['selected']])
        self.assertEqual([3, 3, 3], [row['kind'] for row in self.report['selected']])

    def test_open_parameters_are_inflated_to_actual_target_arguments(self):
        self.assertEqual([[20060], [32758, 51041], [63831]],
            [[argument['typeDefinition'] for argument in row['inflatedArguments']] for row in self.report['selected']])
        self.assertEqual([[0], [0, 1], [0]],
            [[argument['genericParameterPosition'] for argument in row['openArguments']] for row in self.report['selected']])

    def test_reference_canonicalizer_runs_without_enum_flag_model(self):
        for source in (63831, 51041):
            row = self.rows['reference-canonicalization-' + str(source)]
            self.assertTrue(row['returnedObjectTypePointer'])
            self.assertEqual(28, row['resultKind'])
            self.assertEqual('0x14052dd70', row['trace']['entry'])
            self.assertEqual('0x620000100', row['trace']['stop'])
            self.assertEqual([], row['trace']['models'])
        aot = next(row for row in self.report['canonicalAOT'] if row['methodDefinition'] == 10374)
        self.assertEqual(('0x159111af0', 0, 'object'), (aot['address'], aot['classInstance'], aot['arguments'][0]['kind']))

    def test_three_inherited_lists_have_actual_native_constructor_stores(self):
        for name in ('fresh', 'old'):
            row = self.rows['three-inherited-list-constructors-' + name]
            self.assertEqual(['0x50', '0x1c8', '0x40'], [container['offset'] for container in row['containers']])
            for container in row['containers']:
                self.assertEqual(34054, container['type'])
                self.assertTrue(container['backingArrayIsInitializedEmpty'])
                self.assertEqual((0, 0), (container['count'], container['arrayLength']))
            self.assertEqual('0x15184003d', row['trace']['stop'])
            self.assertIn('0x159111af0', row['trace']['nativeEntries'])
            self.assertEqual(['0x1405336c0'] * 3, row['trace']['models'])

    def test_list_constructor_and_missing_context_stop(self):
        row = self.rows['list-constructor-overwrites-old-backing-array']
        self.assertTrue(row['preservedPointer'])
        self.assertEqual('0x620000100', row['trace']['stop'])
        self.assertEqual([], row['trace']['models'])
        failure = self.rows['missing-list-array-context-before-resolver']['trace']
        self.assertEqual('0x159111b1e', failure['stop'])
        self.assertEqual([], failure['models'])
        self.assertNotIn('0x140524010', failure['nativeEntries'])

    def test_enum_option_remains_unknown_despite_bounded_search(self):
        enum = self.report['enumSharing']
        self.assertEqual('0x145bc8ef0', enum['flagPointerCell'])
        self.assertIsNone(enum['observedRuntimeValue'])
        self.assertFalse(enum['pointerCellFileBacked'])
        self.assertEqual([], enum['directMovLeaCandidates'])
        self.assertEqual([('0x1402f8481', False)], [(row['address'], row['isInstructionBoundary']) for row in enum['rawByteCandidateChecks']])
        self.assertIn('not proof', enum['searchScope'])
        self.assertIn('no alias', enum['unapprovedExampleType']['relation'])


if __name__ == '__main__':
    unittest.main()
