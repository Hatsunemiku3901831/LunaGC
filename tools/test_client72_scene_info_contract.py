import hashlib
import json
from pathlib import Path
import unittest

from tools.extract_client72 import HASHES


class SceneInfoContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        cls.report = json.loads((cls.root / 'protocol/cn-7.2/scene-info-contract-observations.json').read_text('utf8'))
        cls.rows = {row['name']: row for row in cls.report['fixtures']}

    def test_fixed_inputs_and_reviewed_prerequisites(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        for name, digest in self.report['inputContracts'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / 'protocol/cn-7.2' / name).read_bytes()).hexdigest())
        for name, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / name).read_bytes()).hexdigest())

    def test_independent_semantic_and_concrete_target_sources(self):
        d = self.report['declarations']; methods = {row['entry']: row for row in d['methods']}
        self.assertEqual((34208, 21761), (d['identity']['typeIndex'], d['identity']['cmdId']))
        self.assertEqual(18295, methods['0x1507e4900']['parameters'][0]['arguments'][0]['typeDefinition'])
        self.assertEqual('GetAvatarEntityId', methods['0x14e7712f0']['method']['name'])
        self.assertEqual(51522, d['secondaryFactory']['arguments'][0]['typeDefinition'])
        self.assertEqual((738059, 23017, 294224, '0x145994388'), tuple(d['secondaryFactory'][k] for k in ('usageRow', 'methodSpec', 'methodDefinition', 'cell')))
        self.assertEqual({5845, 19807}, {x['classInstance'] for x in d['managedInsertContexts']})
        self.assertEqual({'0x156f6f510', '0x156f6f850'}, {x['address'] for x in d['dictionaryAot']})

    def test_independent_literal_and_real_store_widths(self):
        row = self.rows['independent-high-pair']
        self.assertEqual('6081808080083849122068818080808080801020818080800850ffffffffffffffffff0138ffffffff0f', row['wire'])
        self.assertEqual([(18163, 9007199254740993, 2147483649, None),
                          (51522, 18446744073709551615, 4294967295, 9007199254740993)],
                         [(x['modelType'], x['guid'], x['entityId'], x['ownerAvatarGuid']) for x in row['modelSnapshot']])
        self.assertEqual([('0x15184b069', 4, 2147483649), ('0x1507e4ac4', 8, 9007199254740993),
                          ('0x1507e4acf', 8, 18446744073709551615), ('0x14eaaf086', 4, 4294967295)],
                         [(x['source'], x['size'], x['value']) for x in row['stores']])
        self.assertEqual(2147483649, row['getters'][0]['entityId'])

    def test_record_permutation_and_equal_low32_do_not_cross_bind(self):
        projection = lambda row: {(x['modelType'], x['guid']): (x['entityId'], x['ownerAvatarGuid']) for x in row['modelSnapshot']}
        self.assertEqual(projection(self.rows['two-distinct-records']), projection(self.rows['two-records-reversed']))
        self.assertEqual({(18163, 4294967297): (11, None), (18163, 8589934593): (22, None),
                          (51522, 12884901889): (33, 4294967297), (51522, 17179869185): (44, 8589934593)},
                         projection(self.rows['same-low32-distinct-full-guids']))
        swap = self.rows['swap-two-entity-fields']['modelSnapshot']
        self.assertEqual([4294967295, 2147483649], [x['entityId'] for x in swap])
        swap_guid = self.rows['swap-two-guid-fields-with-distinct-models']['modelSnapshot']
        self.assertEqual([18446744073709551615, 9007199254740993], [x['guid'] for x in swap_guid])

    def test_missing_or_wrong_avatar_aborts_remaining_records(self):
        for name in ('missing-avatar-stops-whole-list', 'wrong-model-class-stops-whole-list'):
            row = self.rows[name]
            self.assertEqual([], row['stores'])
            self.assertEqual('0x620000100', row['listTrace']['stop'])
            self.assertEqual([0], [x['entityId'] for x in row['modelSnapshot'] if x['modelType'] == 18163])
        for name in ('zero-avatar-guid-not-registered', 'all-four-child-fields-omitted',
                     'duplicate-four-scalars-explicit-zero', 'wrong-wire-guid-skipped', 'avatar-guid-omitted-stops'):
            self.assertEqual([], self.rows[name]['stores'])

    def test_zero_and_omission_really_clear_existing_entity_ids(self):
        row = self.rows['explicit-zero-clears-both-old-ids']
        self.assertEqual([0, 0], [x['entityId'] for x in row['modelSnapshot']])
        self.assertEqual([91, 92], row['oldSceneEntityLookups'])
        self.assertEqual([('0x15184b069', 0), ('0x14eaaf086', 0)],
                         [(x['source'], x['value']) for x in row['stores'] if x['size'] == 4])
        self.assertEqual(0, self.rows['avatar-id-omitted-clears-old']['getters'][0]['entityId'])
        self.assertEqual(0, self.rows['secondary-id-omitted-clears-old']['secondaryGetters'][0]['entityId'])

    def test_duplicate_avatar_and_secondary_registry_behavior(self):
        row = self.rows['duplicate-avatar-last-entity']
        self.assertEqual(33, row['getters'][0]['entityId'])
        self.assertEqual([2147483649, 33], [x['value'] for x in row['stores'] if x['source'] == '0x15184b069'])
        existing = self.rows['secondary-existing-model-reused']
        self.assertEqual(2, len(existing['modelSnapshot']))
        self.assertTrue(existing['secondaryGetters'][0]['registryFound'])
        zero = self.rows['zero-secondary-guid-remains-unregistered']['secondaryGetters'][0]
        self.assertEqual((0, 22, False), (zero['guid'], zero['entityId'], zero['registryFound']))

    def test_handler_gates_and_local_selection_have_separate_boundaries(self):
        high = self.rows['independent-high-pair']
        self.assertEqual('0x1507e4900', high['handlerSuffix']['listGate']['stop'])
        self.assertEqual('0x14cd68530', high['handlerSuffix']['localIdGate']['stop'])
        self.assertEqual('0x147161b60', self.rows['empty-list']['handlerSuffix']['listGate']['stop'])
        for name in ('zero-entity-ids', 'local-id-omitted'):
            self.assertEqual('0x1507d1918', self.rows[name]['handlerSuffix']['localIdGate']['stop'])
            self.assertTrue(self.rows[name]['localSelection']['zeroHandlerGuardBypassed'])
        selection = high['localSelection']
        self.assertEqual((2147483649, 10000005), (selection['storedEntityId'], selection['configId']))
        self.assertEqual('0x153a20f30', selection['manager']['stop'])
        self.assertEqual('controlled-fixture-not-native', selection['sceneEntityConstruction'])

    def test_nonempty_ability_and_wire_errors_do_not_become_success(self):
        row = self.rows['nonempty-avatar-ability-boundary']
        self.assertEqual('0x151846096', row['listTrace']['stop'])
        self.assertIsNone(row['handlerSuffix']); self.assertIsNone(row['localSelection'])
        self.assertEqual([18163], [x['modelType'] for x in row['modelSnapshot']])
        self.assertEqual(3, len(self.report['rejections']))
        self.assertIn('Native error branch:', self.report['rejections'][1]['error'])

    def test_models_and_unproven_names_remain_explicit(self):
        self.assertEqual('not-tested', self.report['runtimeStatus'])
        for key in ('fullNetworkHandler', 'fullSceneEntityEffects', 'completeSceneInfoRawClosure',
                    'targetProtocolReady', 'secondaryWeaponNameApproved', 'productionRequiredFieldSetApproved'):
            self.assertFalse(self.report[key])
        critical = {'0x1507e4900', '0x15184aeb0', '0x14eaaf060', '0x14e7712f0',
                    '0x14e9b2c60', '0x1547e1dd0', '0x156f6f510', '0x156f6f850', '0x1572292b0'}
        for row in self.report['fixtures']:
            self.assertFalse(critical.intersection(row['listTrace']['models']))
            self.assertFalse({'0x1541b67d0', '0x148b78700'}.intersection(row['reader']['modeledAddresses']))
        self.assertEqual({4, 7, 10, 13}, {x['tag'] for x in self.report['semanticClaims']})


if __name__ == '__main__': unittest.main()
