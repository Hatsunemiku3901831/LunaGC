"""Independent wire, native application and evidence-boundary regressions."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
from tools.client72_store_weight_contract import store_limits_wire, validate_fixtures, RAW_SHA256
from tools.client72_reader import WireInput
from tools.extract_client72 import HASHES

ROOT = Path(__file__).resolve().parents[1]


class StoreWeightContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((ROOT/'protocol/cn-7.2/store-weight-contract-observations.json').read_text('utf8'))

    def test_five_bindings_are_distinct_from_three_unresolved_raw_fields(self):
        report = self.report
        self.assertEqual(report['inputSHA256'], HASHES)
        self.assertEqual((report['typeIndex'], report['clientType'], report['cmdId']), (28836, 'KNCCLAJAEEC', 22609))
        self.assertEqual(report['semanticIdentityStatus'], 'verified-offline-purpose-alias')
        self.assertEqual({b['tag']: b['semanticField'] for b in report['bindings']},
                         {6: 'store_type', 1: 'reliquary_count_limit', 9: 'weapon_count_limit',
                          14: 'material_count_limit', 5: 'furniture_count_limit'})
        self.assertEqual({f['tag'] for f in report['unresolvedFields']}, {2, 4, 7})
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for f in report['unresolvedFields']))
        self.assertEqual(report['runtimeStatus'], 'not-tested'); self.assertFalse(report['targetProtocolReady'])
        raw = ROOT/'protocol/cn-7.2/initialization-wire-observations.json'
        self.assertEqual(hashlib.sha256(raw.read_bytes()).hexdigest(), RAW_SHA256)

    def test_independent_builder_slice_wire_and_uint32_inputs(self):
        self.assertEqual(store_limits_wire(1, 11, 55, 99, 141).hex(), '080b283730014863708d01')
        self.assertEqual(store_limits_wire(1, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF).hex(),
                         '08ffffffff0f28ffffffff0f300148ffffffff0f70ffffffff0f')
        inp = WireInput(bytes.fromhex('080b283730014863708d01')); values = {}
        while key := inp.tag(): values[key >> 3] = inp.varint()
        self.assertEqual(values, {1: 11, 5: 55, 6: 1, 9: 99, 14: 141})
        for value in (-1, 1 << 32, True):
            with self.assertRaises(ValueError): store_limits_wire(1, value, 2)

    def test_native_store_selection_and_all_four_category_counts(self):
        validate_fixtures(copy.deepcopy(self.report['fixtures']))
        self.assertEqual(self.report['dispatcherExecutions'], 12)
        rows = {r['name']: r for r in self.report['fixtures']}
        self.assertEqual(rows['depot']['results'][0]['packLimits'], {})
        self.assertEqual(rows['depot']['results'][0]['depotLimits'], {'3': 11, '4': 99, '2': 141, '6': 55})
        symbols = {v['symbol']: v['value'] for row in self.report['declarations']['enums'] for v in row['values']}
        self.assertEqual({k: symbols[k] for k in ['StorePack', 'StoreDepot', 'ITEM_RELIQUARY', 'ITEM_WEAPON', 'ITEM_MATERIAL', 'ITEM_FURNITURE']},
                         dict(StorePack=1, StoreDepot=2, ITEM_RELIQUARY=3, ITEM_WEAPON=4, ITEM_MATERIAL=2, ITEM_FURNITURE=6))

    def test_native_constructor_and_omitted_fields_do_not_invent_defaults(self):
        constructor = self.report['constructor']
        self.assertEqual(constructor['entry'], '0x14fd44980')
        for fixture in constructor['fixtures']:
            self.assertEqual(set(fixture['actualFields'].values()), {fixture['initialRawValue']})
            self.assertEqual(fixture['completion'], 'constructor-returned')
        row = next(r for r in self.report['fixtures'] if r['name'] == 'omitted-fields-managed-zero')['results'][0]
        self.assertEqual(row['wire'], '')
        self.assertEqual(row['packLimits'], {'3': 0, '4': 0, '2': 0, '6': 0})
        self.assertEqual(row['readerEvents'], [])

    def test_unused_raw_fields_and_last_value_are_observed_independently(self):
        rows = {r['name']: r for r in self.report['fixtures']}
        independent = rows['independent-pack']['results'][0]
        perturbed = rows['unconsumed-fields-perturbed']['results'][0]
        self.assertEqual(independent['packLimits'], perturbed['packLimits'])
        self.assertFalse({24, 36, 44} & {r['objectOffset'] for r in independent['packetFieldReads']})
        duplicate = rows['duplicate-wire-last-wins']['results'][0]
        self.assertEqual(duplicate['packLimits']['3'], 7)
        update = rows['existing-dictionary-overwrite']['results']
        self.assertEqual(len(update[1]['packLimits']), 4)
        self.assertEqual(update[1]['packLimits'], {'3': 7, '4': 9, '2': 10, '6': 8})

    def test_registration_and_native_null_boundaries_are_bounded(self):
        self.assertEqual(self.report['registration']['commands'], [20892, 22609])
        self.assertEqual(self.report['registration']['trace']['stop'], '0x153cbff90')
        boundaries = self.report['nativeNullBoundaries']
        self.assertEqual({r['name'] for r in boundaries}, {'null-message', 'missing-pack-instance'})
        for row in boundaries:
            self.assertEqual(row['trace']['stop'], '0x1404c2be0')
            self.assertEqual(row['trace']['completion'], 'bounded-fragment')

    def test_drift_cannot_become_success_by_changing_a_store_value_or_omitting_application(self):
        for kind in ('value', 'path', 'reads', 'event'):
            rows = copy.deepcopy(self.report['fixtures'])
            result = next(r for r in rows if r['name'] == 'independent-pack')['results'][0]
            if kind == 'value': result['packLimits']['3'] = 123
            elif kind == 'path': result['executedNativeEntries'].remove('0x157d1a480')
            elif kind == 'reads': result['packetFieldReads'].append(dict(objectOffset=44))
            else: result['events'] = []
            with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_real_applied_limit_is_the_downstream_capacity_threshold(self):
        literal = self.report['declarations']['capacityBoundaryLiteral']
        self.assertEqual((literal['index'], literal['cell'], literal['value']),
                         (25222, '0x145afb250', 'SHOP_ITEM_LIMITED'))
        rows = self.report['downstreamCapacityBoundaries']['fixtures']
        self.assertEqual([r['currentCountSource'] for r in rows], [0, 140, 141, 142])
        for row in rows:
            self.assertEqual(row['limitReadback'], 141)
            self.assertEqual(row['currentCountReadback'], row['currentCountSource'])
            self.assertEqual(row['belowLimit'], row['currentCountSource'] < 141)
            self.assertEqual(row['trace']['stop'], '0x14e3e3385' if row['belowLimit'] else '0x14e3e330d')


if __name__ == '__main__': unittest.main()
