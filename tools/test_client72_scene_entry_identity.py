import json
from pathlib import Path
import unittest

from tools.extract_client72 import HASHES


class SceneEntryIdentityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        cls.report = json.loads((root / 'protocol/cn-7.2/scene-entry-identity-observations.json').read_text('utf8'))
        cls.cases = {row['name']: row for row in cls.report['fixtures']}

    def test_independent_typed_identity_registration_and_named_consumer(self):
        declared = self.report['declarations']
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual((34208, 21761, 'PlayerEnterSceneInfoNotify'),
                         (declared['typeIndex'], declared['cmdId'], declared['semanticName']))
        self.assertEqual('0x1507c2a12', declared['registration'])
        methods = {row['registeredAddress']: row for row in declared['methods']}
        for address in ('0x1507d1620', '0x14cd68530'):
            self.assertEqual(34208, methods[address]['parameterType']['typeDefinition'])
        self.assertEqual('uint32', methods['0x1517b18f0']['parameterType']['kind'])
        self.assertEqual('GetLocalAvatarConfigId', methods['0x153e46950']['name'])
        self.assertEqual(4, len(declared['literals']))
        self.assertEqual(54061, declared['entityClassUsage']['type']['typeDefinition'])

    def test_all_five_outer_fields_and_empty_only_nested_scope(self):
        fields = self.report['messages'][0]['fields']
        self.assertEqual(128, self.report['scanFixtures'])
        self.assertEqual({2, 7, 12, 14, 15}, {row['tag'] for row in fields})
        nested = {row['tag']: row for row in fields if row['type'] == 'message'}
        self.assertEqual({2: 18295, 14: 44512, 15: 49723},
                         {tag: row['nestedTypeIndex'] for tag, row in nested.items()})
        self.assertTrue(all(row['contentStatus'] == 'not-traced' for row in nested.values()))
        self.assertEqual(5, len(self.report['declarations']['envelopeClassUsages']))

    def test_nonzero_unsigned_ids_reach_real_store_and_local_avatar(self):
        for name, entity in [('low-id', 0x100001), ('high-id', 0x80000001), ('max-id', 0xFFFFFFFF), ('two-fields-swapped', 7)]:
            row = self.cases[name]
            self.assertEqual(entity, row['storedEntityId'])
            self.assertEqual(10000005, row['localAvatarConfigId'])
            self.assertEqual([dict(source='0x1517b1904', size=4, value=entity)], row['stores'])
            self.assertEqual('0x153a20f30', row['manager']['stop'])
            self.assertEqual('0x620000100', row['local']['stop'])
            self.assertNotIn('0x1517b18f0', row['manager']['models'])
            self.assertNotIn('0x1517abd80', row['local']['models'])
            self.assertNotEqual(row['otherField'], entity)

    def test_zero_presence_wrong_wire_and_other_field_do_not_select_entity(self):
        for name in ('duplicate-zero', 'other-scalar-only', 'wrong-wire-id'):
            row = self.cases[name]
            self.assertEqual((0, 0), (row['storedEntityId'], row['localAvatarConfigId']))
            self.assertTrue(row['networkZeroGuardNotExecuted'])
        writes = self.cases['duplicate-zero']['reader']['events']
        self.assertEqual([0x80000001, 0], [event['value'] for event in writes if event['objectOffset'] == 48])
        binding = self.report['verifiedBinding']
        self.assertEqual((12, 48, 644, 'uint32'),
                         (binding['tag'], binding['objectOffset'], binding['managerFieldOffset'], binding['type']))

    def test_existing_entity_null_and_ifix_stop_before_new_store(self):
        for name, stop, stored in [('existing-entity-cleanup-boundary', '0x14adf3180', 0x100001),
                                   ('null-typed-input', '0x1404c2be0', 0),
                                   ('ifix-manager-boundary', '0x14cd6872e', 0)]:
            row = self.cases[name]
            self.assertEqual((stop, stored, []), (row['manager']['stop'], row['storedEntityId'], row['stores']))
            self.assertIsNone(row['local'])

    def test_models_and_unfinished_boundaries_remain_explicit(self):
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        for key in ('targetProtocolReady', 'completeSceneInfoRawClosure', 'completeNetworkHandler',
                    'completeSetterEffects', 'currentControlGuidBindingApproved'):
            self.assertFalse(self.report[key])
        self.assertEqual(3, len(self.report['rejections']))
        self.assertIn('Nonempty nested/container payload not modeled', self.report['rejections'][-1]['error'])


if __name__ == '__main__': unittest.main()
