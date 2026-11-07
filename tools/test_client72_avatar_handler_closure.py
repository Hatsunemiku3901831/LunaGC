"""Independent evidence checks for the bounded Avatar closure; no live process use."""
import hashlib
import json
import os
import struct
import unittest
from pathlib import Path

from tools.client72_avatar_cold_interfaces import FIVE_INPUTS


ROOT = Path('local/migration72/d02-active/avatar/handler-closure')


class HandlerClosureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(Path(os.environ.get(
            'CLIENT72_AVATAR_HANDLER_CLOSURE_REPORT', ROOT / 'first.json')).read_text('utf8'))
        cls.cold = cls.report['cold']
        cls.config = cls.report['nativeConfig']
        cls.resources = cls.report['resourceConsumers']

    def test_fixed_inputs_and_sources(self):
        self.assertEqual(self.report['inputSHA256'], FIVE_INPUTS)
        digest = hashlib.sha256(Path('tools/client72_avatar_handler_closure.py').read_bytes()).hexdigest()
        for report in (self.report, self.cold, self.config, self.resources):
            self.assertEqual(report['sourceSHA256'], digest)
        for name, expected in self.report['dependencies'].items():
            self.assertEqual(hashlib.sha256(Path('tools', name).read_bytes()).hexdigest(), expected)
        contract = (ROOT / 'native-config-sampling-contract.json').read_bytes()
        self.assertEqual(hashlib.sha256(contract).hexdigest(),
                         '6503b65872f093d503c2db8668607542c0a5d3a336002e829e5aa82b924734a4')

    def test_natural_capture_bytes_and_exact_typearray_candidate(self):
        raw = Path('local/migration72/d02-active/main/cold-arrays-tool/live-elevated1.json').read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(),
                         '63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0')
        sample = json.loads(raw)
        self.assertEqual(len(sample['reads']), 99)
        data = {}
        for row in sample['reads']:
            value = bytes.fromhex(row['bytesHex'])
            self.assertEqual((len(value), hashlib.sha256(value).hexdigest()),
                             (row['length'], row['sha256']))
            self.assertIsNone(row['error'])
            self.assertLessEqual(len(value), 256)
            data[row['label']] = value
        key, value = struct.unpack('<QQ', data['cold.typeArrayCache.firstEntry'])
        self.assertEqual(key, int.from_bytes(data['cold.type314Default.cell'], 'little'))
        self.assertEqual(value, int.from_bytes(data['cold.typeArrayUsage.cell'], 'little'))
        self.assertEqual(data['cold.typeArrayCache.firstKey.header'], data['cold.type314Default.header'])
        self.assertEqual(data['cold.typeArrayCache.firstValue.header'], data['cold.typeArrayUsage.header'])
        self.assertFalse(self.report['snapshot']['atomicSnapshot'])

    def test_natural_class_hierarchy_is_not_a_ready_comparer(self):
        classes = {row['type']: row for row in self.cold['classes']}
        self.assertEqual(len(classes), 13)
        self.assertEqual((classes[117]['interfaceCount'], classes[117]['depth']), (6, 2))
        self.assertEqual((classes[314]['interfaceCount'], classes[314]['depth']), (0, 3))
        self.assertEqual(classes[284]['hierarchy'], [371, 513, 314, 531, 284])
        self.assertEqual(classes[20060]['hierarchy'], [371, 405, 204, 20060])
        self.assertEqual(classes[1239]['hierarchy'][1],
                         {'unreadClassPointer': hex(int(self.cold['expectedUncapturedParentDescriptor'], 16) - 0x10)})
        self.assertFalse(self.report['defaultComparerWriteExecuted'])

    def test_four_original_genericclass_producers(self):
        cases = self.cold['native'][:4]
        self.assertEqual([case['producer']['index'] for case in cases], [0, 2072, 405417, 411432])
        for case in cases:
            proof = case['producer']
            instance, definition = struct.unpack('<II', bytes.fromhex(proof['inputPair']))
            actual = bytes.fromhex(proof['outputBytes'])
            self.assertEqual(len(actual), 32)
            self.assertEqual(struct.unpack_from('<I', actual)[0], definition)
            self.assertEqual(struct.unpack_from('<Q', actual, 8)[0],
                             int(proof['registrationInstanceBase'], 16) + instance * 16)
            self.assertEqual(actual[4:8] + actual[16:], bytes(20))
            self.assertEqual(case['stop'], '0x14053f47a')
            self.assertIsNone(case['failure'])
            self.assertEqual(case['trace']['models'], [])

    def test_registered_intern_native_insert_then_exact_hit(self):
        inserted, hit = self.cold['native'][4:6]
        self.assertEqual(inserted['stop'], '0x14053f800')
        self.assertEqual(hit['stop'], '0x1404e0d18')
        self.assertEqual(hit['regs']['rax'], '0x142377940')
        for address in ('0x1404f0740', '0x1404f22e0'):
            self.assertIn(address, inserted['trace']['executedAddresses'])
        for address in ('0x159b6eb50', '0x14051b7b0', '0x1404e0c70',
                        '0x14051fb70', '0x1404f0740', '0x1404f0820'):
            self.assertIn(address, hit['trace']['executedAddresses'])
            self.assertNotIn(address, {row['address'] for row in hit['trace']['models']})
        self.assertEqual(hit['arrayElement'], self.cold['argument'])
        self.assertEqual(hit['tailCanary'], '0xfedcba9876543210')

    def test_dynamic_genericclass_creation_has_unwritten_cachedclass(self):
        model = self.cold['genericClassModel']
        self.assertEqual(model['registeredRows'], 411433)
        self.assertEqual(model['sourceBytesSHA256'],
                         'a6df7e71ad5ebab4188c102137c7146a2d2012fbb6a83b716f98704bb484ae1c')
        self.assertEqual(len(model['dynamicRecords']), 1)
        raw = bytes.fromhex(model['dynamicRecords'][0]['bytes'])
        self.assertEqual(struct.unpack('<I4xQQQ', raw), (1239, 0x142377940, 0, 0))
        current = self.cold['native'][-1]
        ordered = [row['entry'] for row in current['trace']['callOrder']]
        sequence = ['0x14051fb70', '0x1404edf20', '0x1404f1270', '0x1404f22e0',
                    '0x140517980', '0x1404ed990']
        self.assertEqual([ordered.index(address) for address in sequence],
                         sorted(ordered.index(address) for address in sequence))
        self.assertEqual(len(self.cold['newTypeClasses']), 1)
        self.assertEqual(self.cold['newTypeClasses'][0]['size'], 400)

    def test_uncaptured_parent_read_is_the_exact_failure(self):
        current = self.cold['native'][-1]
        parent = int(self.cold['expectedUncapturedParentDescriptor'], 16)
        self.assertIsNone(current['stop'])
        self.assertIsNone(current['error'])
        self.assertEqual(current['failure'], 'Uncaptured natural-address memory read')
        self.assertEqual(current['uncapturedReads'],
                         [{'address': hex(parent + 8), 'size': 4, 'site': '0x1404ed9c3'}])
        self.assertEqual(current['regs']['rip'], '0x1404ed9c3')
        self.assertEqual(current['tailCanary'], '0xfedcba9876543210')
        self.assertEqual(self.report['firstColdBlocker']['minimumFutureReadBytes'], 16)
        self.assertTrue(self.report['firstColdBlocker']['oldPointerMustNotBeReused'])

    def test_nested_lock_state_is_not_claimed_balanced(self):
        events = self.cold['criticalEvents']
        self.assertEqual([row['depth'] for row in events], [1, 0, 1, 0, 1, 2])
        self.assertEqual(events[-1]['pointer'], events[-2]['pointer'])
        self.assertEqual(self.cold['locksAtStop'],
                         {'criticalDepth': 2, 'sharedHeld': False, 'unwindExecuted': False})
        self.assertEqual([row[0] for row in self.cold['sharedEvents']],
                         ['shared-enter', 'shared-leave', 'exclusive-enter', 'exclusive-leave'])

    def test_native_source_windows_pinned(self):
        expected = {
            'registeredClassProducer': 'e0e692f0b166776907d6e24401afdf2f56d42cf50c783833eecf05444aa5130f',
            'registeredInstanceLoop': 'd3b5a4ffa5ab9af9de179d0a0bd1c1f3504d979e03aad8cb91f62bcd39e502ae',
            'dynamicClassProducer': 'da9466a6ee08e1cb7dda3715e6de4e8c4645934d9fc2079c9d445a0723c6edbc'}
        self.assertEqual({key: row['sha256'] for key, row in self.cold['sourceWindows'].items()}, expected)
        for row in self.cold['sourceWindows'].values():
            self.assertEqual(int(row['end'], 16) - int(row['start'], 16), row['bytes'])
            self.assertTrue(row['instructions'])

    def test_correct_globalvars_signed_collection_identity(self):
        fields = {row['name']: row for row in self.config['fields']}
        self.assertEqual(self.config['owner'], 11325)
        for name, index, offset, definition in (
                ('_serializedCodeSwitches', 53821, '0x40060e8', 2147),
                ('codeSwitches', 53824, '0x4006100', 1255)):
            row = fields[name]
            self.assertEqual((row['index'], row['rawOffset']), (index, offset))
            self.assertEqual((row['type']['typeDefinition'], row['type']['instanceIndex']), (definition, 6))
            self.assertEqual(row['type']['arguments'][0]['kind'], 'int32')
        self.assertEqual(fields['enableExcelNativeConfigCodeSwitch']['rawOffset'], '0x20018a2')
        self.assertEqual(self.config['wrongUInt32Rejected'], 'GlobalVars requires HashSet<Int32>')

    def test_eight_native_config_branches_against_independent_boolean_formula(self):
        self.assertEqual(len(self.config['cases']), 8)
        for case in self.config['cases']:
            serialized = {int(value, 16) for value in case['serializedInt32Bits']}
            codes = {int(value, 16) for value in case['codesInt32Bits']}
            mode = 6104 not in codes
            selected = mode and 3521 not in serialized and not ({3521, 6506} & codes)
            self.assertEqual(case['mode'], int(mode), case['name'])
            self.assertEqual(case['nativeSelected'], bool(selected), case['name'])
            self.assertEqual(case['constructorDecision']['stop'],
                             '0x14844526a' if selected else '0x148445202')
            for query in case['queries']:
                expected = query['key'] in (serialized if query['kind'] == 'serialized' else codes)
                self.assertEqual(query['value'], expected, case['name'])

    def test_signed_high_bit_collisions_keep_full_int32_equality(self):
        cases = {row['name']: row for row in self.config['cases']}
        collision = cases['signed-hash-collision-not-3521']
        self.assertEqual(0x80000dc1 & 0x7fffffff, 3521)
        self.assertEqual(collision['serializedInt32Bits'], ['0x80000dc1'])
        self.assertFalse(collision['queries'][0]['value'])
        self.assertTrue(collision['nativeSelected'])
        high_list = cases['signed-list-value-not-6104']
        self.assertEqual(high_list['codesInt32Bits'], ['0x800017d8'])
        self.assertEqual(high_list['mode'], 1)
        self.assertTrue(cases['serialized6506-is-not-list6506']['nativeSelected'])
        self.assertFalse(cases['list6506-managed']['nativeSelected'])

    def test_actual_config_producers_and_comparer_functions_execute(self):
        self.assertEqual(self.config['comparerLeafs']['0x157d873d0']['sha256'],
                         '9c84a16302f7e3fc6650d12aeb6cefe2a89b80acd960a0710faeafbc202cccdc')
        self.assertEqual(self.config['comparerLeafs']['0x157d873e0']['sha256'],
                         '9dbdd75a2520874388427ea903f2a414435e178f0eb872d390d8b5369b840f91')
        for case in self.config['cases']:
            self.assertIn('0x157484f80', case['configCtor']['executedAddresses'])
            self.assertIn('0x14f585f00', case['writer']['executedAddresses'])
            for trace in case['adds']:
                for address in ('0x1574872b0', '0x157d873e0'):
                    self.assertIn(address, trace['executedAddresses'])
                    self.assertNotIn(address, trace['models'])
        collision = next(row for row in self.config['cases'] if row['name'].startswith('signed-hash'))
        self.assertIn('0x157d873d0', collision['queries'][0]['trace']['executedAddresses'])

    def test_same_cache_three_complete_actual_tables(self):
        expected = [('Avatar', 169, 64384), ('AvatarSkillDepot', 171, 26024), ('AvatarSkill', 1176, 141702)]
        self.assertEqual([(row['name'], row['count'], row['cursor']) for row in self.resources['tables']], expected)
        source = Path('local/migration72/d02-active/resource-locator-astra/locator-final-1')
        for table in self.resources['tables']:
            self.assertEqual(hashlib.sha256((source / (table['name'] + '.native-m_bytes.bin')).read_bytes()).hexdigest(), table['sha256'])
            self.assertEqual(table['trace']['stop'], '0x620000100')
            self.assertEqual(table['trace']['executedAddresses']['0x1578b6a20'], table['count'])
        self.assertEqual(self.resources['heapUsed'], 1091408)

    def test_element_consumer_follows_depot_field_and_missing_skill(self):
        self.assertEqual(len(self.resources['consumers']), 3)
        for case, avatar, depot in zip(self.resources['consumers'], (10000005, 10000007), (501, 701)):
            self.assertEqual((case['avatarId'], case['returned']), (avatar, 0))
            self.assertEqual(case['reads'], [
                {'table': 'Avatar', 'key': avatar, 'offset': '0x168', 'width': 4, 'site': '0x1532179c8'},
                {'table': 'AvatarSkillDepot', 'key': depot, 'offset': '0x6c', 'width': 4, 'site': '0x153217a27'}])
            for address in ('0x153217960', '0x1578b66f0', '0x14aef9db0'):
                self.assertIn(address, case['trace']['executedAddresses'])
                self.assertNotIn(address, case['trace']['models'])
            self.assertEqual(case['trace']['executedAddresses']['0x1578b66f0'], 3)
            self.assertEqual(case['trace']['executedAddresses']['0x14aef9db0'], 2)
        missing = self.resources['consumers'][2]
        self.assertEqual((missing['avatarId'], missing['returned'], missing['reads']), (0xffffffff, 0, []))
        self.assertEqual(missing['trace']['executedAddresses']['0x1578b66f0'], 1)
        order = self.resources['callOrder']
        begin = next(index for index, row in enumerate(order) if row['entry'] == '0x153217960')
        lookups = [row for row in order[begin:] if row['entry'] == '0x1578b66f0']
        self.assertEqual([int(row['rdx'], 16) for row in lookups],
                         [10000005, 501, 0, 10000007, 701, 0, 0xffffffff])
        self.assertEqual(lookups[0]['rcx'], lookups[3]['rcx'])
        self.assertEqual(lookups[1]['rcx'], lookups[4]['rcx'])
        self.assertEqual(lookups[2]['rcx'], lookups[5]['rcx'])
        self.assertEqual(self.resources['nativeSources']['0x153217960']['sha256'],
                         '202de0d982b8af2533dea6834cbe6238ef1fdea60d7ce9ec604149f8615f983a')

    def test_minimal_sample_spec_and_approval_boundaries(self):
        contract = json.loads((ROOT / 'native-config-sampling-contract.json').read_text('utf8'))
        self.assertEqual([row['length'] for row in contract['orderedReads']], [1, 8, 8, 8, 40, 32])
        self.assertEqual(contract['bounds']['maxTotalBytes'], 97)
        self.assertEqual(contract['orderedReads'][0]['address'], 'B+0x586ceb2')
        self.assertEqual(contract['orderedReads'][1]['address'], 'B+0x586b600')
        for field in ('completeColdComparer', 'defaultComparerWriteExecuted', 'completeAvatarHandler',
                      'targetProtocolReady', 'naturalNativeConfigModeObserved'):
            self.assertFalse(self.report[field])
        self.assertFalse(self.config['naturalModeObserved'])
        self.assertIn('pause instruction', self.report['scope'])


if __name__ == '__main__':
    unittest.main()
