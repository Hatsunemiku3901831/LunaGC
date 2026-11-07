"""Independent assertions over actual native table runs; no client process use."""
import hashlib
import json
import os
import struct
import unittest
from pathlib import Path

from tools.client72_avatar_natural_tables import TABLES, FIVE_INPUTS, INITIALIZATION_SHA, LOCATOR_SHA, natural_codec


class NaturalTableContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = Path(os.environ.get('CLIENT72_NATURAL_TABLES_REPORT',
            'local/migration72/d02-active/avatar/natural-tables/first.json'))
        cls.report = json.loads(cls.path.read_text('utf8'))
        cls.tables = {row['name']: row for row in cls.report['tables']}
        cls.controls = {row['name']: row for row in cls.report['controls']}

    def row(self, table, key):
        return next(row for row in self.tables[table]['selected'] if row['key'] == key)

    def fields(self, table, key):
        return {field['offset']: field['value'] for field in self.row(table, key)['fields']}

    def test_fixed_inputs_and_source(self):
        self.assertEqual(self.report['inputSHA256'], FIVE_INPUTS)
        self.assertEqual(self.report['locatorReportSHA256'], LOCATOR_SHA)
        self.assertEqual(self.report['sourceSHA256'], hashlib.sha256(Path('tools/client72_avatar_natural_tables.py').read_bytes()).hexdigest())
        for name, digest in self.report['dependencySHA256'].items():
            self.assertEqual(hashlib.sha256(Path('tools', name).read_bytes()).hexdigest(), digest, name)

    def test_six_complete_native_whole_tables(self):
        expected = {'Avatar': (169, 64384), 'AvatarSkillDepot': (171, 26024), 'AvatarSkill': (1176, 141702),
                    'Weapon': (288, 128936), 'AvatarOrnamentGroup': (1, 22), 'AvatarOrnament': (2, 346)}
        self.assertEqual(set(self.tables), set(expected))
        for name, (count, size) in expected.items():
            table = self.tables[name]
            self.assertEqual((table['count'], table['payloadBytes'], table['cursor']), (count, size, size))
            self.assertEqual(table['payloadSHA256'], TABLES[name][4])
            self.assertEqual(len({row['key'] for row in table['rows']}), count)
            self.assertEqual(table['trace']['stop'], '0x620000100')
            self.assertEqual(table['trace']['executedAddresses'][hex(TABLES[name][0])], 1)
            self.assertEqual(table['trace']['executedAddresses']['0x1578b6a20'], count)
            self.assertNotIn(hex(TABLES[name][0]), table['trace']['models'])

    def test_actual_row_spans_cover_payload_exactly(self):
        source = Path('local/migration72/d02-active/resource-locator-astra/locator-final-1')
        for name, table in self.tables.items():
            blob = (source / (name + '.native-m_bytes.bin')).read_bytes()
            cursor = 4
            for row in table['rows']:
                span = row['source']
                self.assertEqual(span['begin'], cursor)
                self.assertGreater(span['end'], cursor)
                self.assertEqual(hashlib.sha256(blob[cursor:span['end']]).hexdigest(), span['sha256'])
                cursor = span['end']
            self.assertEqual(cursor, len(blob))

    def test_protagonists_native_values_float_bits_and_unsigned64(self):
        for avatar, depot, text_hash in ((10000005, 501, 1533656306), (10000007, 701, 3816664530)):
            fields = self.fields('Avatar', avatar)
            self.assertEqual((fields['0x18'], fields['0x168'], fields['0x154'], fields['0x1c']), (avatar, depot, 11101, text_hash))
            self.assertEqual([fields[key]['bits'] for key in ('0x64', '0x9c', '0x5c', '0x98', '0xa4')],
                             ['4463f2a0', '418e76c9', '4264e666', '3d4ccccd', '3f000000'])
            self.assertEqual(fields['0xd8'], list(range(depot, depot + 8)))
            self.assertGreater(int(fields['0x28']['decimal']), 1 << 63)
            self.assertEqual(int(fields['0x28']['decimal']), int(fields['0x28']['bits'], 16))
            curves = fields['0x48']
            self.assertEqual([[field['value']['value'] for field in curve] for curve in curves], [[1, 22], [4, 32], [7, 22]])
            self.assertEqual(curves[0][1]['value']['symbols'], ['GROW_CURVE_HP_S4'])

    def test_real_depot_absent_flags_and_nonzero_control(self):
        for depot, skill in ((501, 100540), (701, 100550)):
            fields = self.fields('AvatarSkillDepot', depot)
            self.assertEqual(fields['0x30'], [skill, 0, 0, 0])
            self.assertEqual((fields['0x68'], fields['0x6c']), (0, 0))
            self.assertFalse(self.row('AvatarSkillDepot', depot)['scalarPresence']['field68'])
            self.assertFalse(self.row('AvatarSkillDepot', depot)['scalarPresence']['field6c'])
            self.assertEqual(fields['0x20'], ['', '', ''])
            self.assertEqual(fields['0x28'], [0] * 6)
        self.assertTrue(self.row('AvatarSkillDepot', 704)['scalarPresence']['field6c'])
        control = self.fields('AvatarSkillDepot', 704)
        self.assertEqual(control['0x6c'], 10068)
        nested = {field['offset']: field['value'] for field in control['0x10'][0]}
        self.assertEqual((nested['0x1c'], nested['0x10'], nested['0x18']['value']), (751, [603201], 1))

    def test_skills_and_weapon_same_numeric_id_are_distinct(self):
        for skill, ability, group in ((100540, 'Avatar_PlayerBoy_ExtraAttack_Common', 730),
                                      (100550, 'Avatar_PlayerGirl_ExtraAttack_Common', 731),
                                      (11101, 'Avatar_Iansan_ExtraAttack', 11031)):
            fields = self.fields('AvatarSkill', skill)
            self.assertEqual((fields['0x68'], fields['0x10'], fields['0x60']), (skill, ability, group))
            self.assertEqual(fields['0x54']['symbols'], ['None'])
        self.assertEqual(self.fields('AvatarSkill', 10068)['0x54']['symbols'], ['Wind'])
        self.assertNotEqual(self.row('Weapon', 11101)['source']['sha256'], self.row('AvatarSkill', 11101)['source']['sha256'])

    def test_weapon_managed_backing_and_properties(self):
        fields = self.fields('Weapon', 11101)
        backing = {field['offset']: field['value'] for field in fields['0x20']}
        self.assertEqual(fields['0x10'], fields['0x20'])
        self.assertEqual((backing['0x28'], backing['0x2c'], backing['0x84']), (11101, 50011101, 11101))
        self.assertEqual(backing['0x38']['symbols'], ['ITEM_WEAPON'])
        self.assertEqual(backing['0x98']['symbols'], ['WEAPON_SWORD_ONE_HAND'])
        self.assertEqual(backing['0x40'], [0, 0])
        props = [{field['offset']: field['value'] for field in row} for row in backing['0x68']]
        self.assertEqual((props[0]['0x10']['symbols'], props[0]['0x14']['bits'], props[0]['0x18']['symbols']),
                         (['GROW_CURVE_ATTACK_101'], '41b9f5c3', ['FIGHT_PROP_BASE_ATTACK']))
        self.assertEqual((props[1]['0x10']['symbols'], props[1]['0x14']['bits'], props[1]['0x18']['symbols']),
                         (['GROW_CURVE_CRITICAL_101'], '00000000', ['FIGHT_PROP_NONE']))
        table = self.tables['Weapon']
        self.assertEqual((len(table['scratchRequests']), table['scratchUsedAtEnd']), (576, 0))
        self.assertEqual(max(row['count'] for row in table['scratchRequests']), 160)
        for entry in ('0x15449a920', '0x1484451b0', '0x15043f0f0', '0x1405070c0'):
            self.assertIn(entry, table['trace']['executedAddresses'])
            self.assertNotIn(entry, table['trace']['models'])

    def test_actual_ornament_builder_and_missing_protagonists(self):
        derived = self.tables['AvatarOrnamentGroup']['derivedByAvatar']
        self.assertEqual(derived['dictionaryCount'], 1)
        first, second, present = derived['queries']
        self.assertEqual((first['listPointer'], second['listPointer']), ('0x0', '0x0'))
        self.assertEqual(len(present['groupPointers']), 1)
        self.assertEqual(present['groupPointers'][0], self.tables['AvatarOrnamentGroup']['rows'][0]['pointer'])
        for entry in ('0x15437af90', '0x1593af830', '0x1591151b0', '0x159111f60', '0x1578b6a20'):
            self.assertIn(entry, first['trace']['executedAddresses'])
            self.assertNotIn(entry, first['trace']['models'])
        self.assertNotIn('0x15437af90', second['trace']['executedAddresses'])
        self.assertEqual([row['key'] for row in self.tables['AvatarOrnament']['rows']], [101, 102])

    def test_collision_high_key_and_list_append(self):
        case = self.controls['two-rows-same-avatar-high-u32']
        self.assertEqual(case['trace']['stop'], '0x620000100')
        fields = {field['offset']: field['value'] for field in case['highKeyFields']}
        self.assertEqual((fields['0x10'], fields['0x14'], fields['0x1c']), (0x80000001, 0x80000001, 0xfffffffe))
        self.assertEqual(len(case['derived']['queries'][2]['groupPointers']), 2)
        self.assertEqual(self.controls['duplicate-key-rejected']['trace']['stop'], '0x1404bbd90')

    def test_truncation_distinguishes_native_and_model_rejection(self):
        tail = self.controls['last-scalar-truncated']
        self.assertEqual((tail['trace']['stop'], tail['cursor'], len(bytes.fromhex(tail['inputHex']))), ('0x620000100', 22, 21))
        self.assertFalse(tail['exactExtentAccepted'])
        header = self.controls['header-truncated']
        self.assertIsNone(header['trace']['stop'])
        self.assertIn('requested=1577058304, declared=0', header['trace']['failure'])
        self.assertEqual(header['cursor'], 4)
        self.assertFalse(header['exactExtentAccepted'])
        zero = self.controls['zero-row-whole']
        self.assertEqual((zero['trace']['stop'], zero['cursor']), ('0x620000100', 4))

    def test_wrong_class_is_native_rejection(self):
        case = self.controls['actual-ornament-wrong-condition-array-class']
        self.assertEqual(case['trace']['stop'], '0x1404c2c70')
        self.assertEqual(len(case['changed']), 1)
        self.assertEqual((case['changed'][0]['declared'], case['changed'][0]['injected']), (58937, 61286))

    def test_natural_inverse_and_reconstructed_forward(self):
        codec = natural_codec(Path('local/migration72/d02-active/plain/initialization-resources-elevated5.json'))
        self.assertEqual(self.report['scalarStorageCodec'], codec['proof'])
        self.assertEqual(codec['proof']['rawSHA256'], INITIALIZATION_SHA)
        self.assertEqual(codec['seed'], 0x3b0a002e)
        for quarter in range(4):
            forward = codec['table'][quarter * 256:(quarter + 1) * 256]
            inverse = codec['table'][1024 + quarter * 256:1024 + (quarter + 1) * 256]
            self.assertEqual(bytes(inverse[value] for value in forward), bytes(range(256)))

    def test_actual_cross_references_preserve_zeros(self):
        self.assertEqual(len(self.report['crossReferences']), 2)
        for row in self.report['crossReferences']:
            self.assertEqual(row['missingSkillReferences'], [])
            self.assertEqual(row['weaponReferenceField154'], 11101)
            self.assertEqual(row['listsByOffset']['0x30'][1:], [0, 0, 0])
            for skill in row['positiveSkillReferences']:
                self.assertTrue(self.row('AvatarSkill', skill)['found'])

    def test_no_unearned_production_or_natural_completion(self):
        for key in ('completeAvatarHandler', 'completeColdComparer', 'targetProtocolReady', 'activeRevisionVerified',
                    'javaResourcesLoaded', 'targetToJavaMappingApproved'):
            self.assertIs(self.report[key], False)


if __name__ == '__main__':
    unittest.main()
