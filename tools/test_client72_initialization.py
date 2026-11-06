"""Independent raw wire and trace guards; no Asta semantic promotion."""
import copy
import json
from pathlib import Path
import unittest
from tools.trace_client72_initialization import validate_fixtures, INDEPENDENT_VALUES, INDEPENDENT_WIRE
from tools.client72_reader import WireInput
from tools.extract_client72 import HASHES

ROOT = Path(__file__).resolve().parents[1]


class InitializationWireTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.observation = json.loads((ROOT/'protocol/cn-7.2/initialization-wire-observations.json').read_text('utf8'))
        cls.trace = dict(fixtures=cls.observation['fixtureEvidence'], modelRejections=cls.observation['modelRejections'])

    def test_native_complete_layout_and_explicit_scope(self):
        o = self.observation
        self.assertEqual(o['inputSHA256'], HASHES)
        self.assertEqual((o['typeIndex'], o['clientType'], o['cmdId']), (28836, 'KNCCLAJAEEC', 22609))
        self.assertEqual({f['objectOffset'] for f in o['fields']}, set(INDEPENDENT_VALUES))
        self.assertEqual({f['tag'] for f in o['fields']}, {1, 2, 4, 5, 6, 7, 9, 14})
        self.assertTrue(all(f['semanticStatus'] == 'unresolved' for f in o['fields']))
        self.assertEqual(o['semanticIdentityStatus'], 'candidate')
        self.assertFalse(o['targetProtocolReady']); self.assertEqual(o['runtimeStatus'], 'not-tested')
        validate_fixtures(copy.deepcopy(self.trace['fixtures']))

    def test_independent_keys_and_nonzero_values(self):
        inp = WireInput(INDEPENDENT_WIRE); values = {}
        while key := inp.tag():
            self.assertEqual(key & 7, 0); values[key >> 3] = inp.varint()
        self.assertEqual(values, {1: 11, 2: 22, 4: 44, 5: 55, 6: 1, 7: 77, 9: 99, 14: 141})

    def test_wrong_wire_duplicate_and_uint32_boundary_are_native_observations(self):
        rows = {r['name']: r for r in self.trace['fixtures']}
        self.assertEqual([e['value'] for e in rows['duplicate-last-wins']['events']], [1, 2])
        self.assertFalse(rows['wrong-wire-and-unknown']['events'])
        self.assertEqual({e['value'] for e in rows['uint32-upper-bound']['events']}, {0xFFFFFFFF})
        self.assertEqual({e['value'] for e in rows['explicit-zero']['events']}, {0})

    def test_changed_field_binding_or_value_is_rejected(self):
        for name, key, value in [('scan-varint-2', 'objectOffset', 48), ('independent-nonzero', 'value', 999)]:
            rows = copy.deepcopy(self.trace['fixtures'])
            next(r for r in rows if r['name'] == name)['events'][0][key] = value
            with self.assertRaises(ValueError): validate_fixtures(rows)

    def test_malformed_inputs_are_labeled_model_rejections(self):
        self.assertEqual(len(self.trace['modelRejections']), 5)
        for row in self.trace['modelRejections']:
            self.assertEqual(row['errorScope'], 'explicit-wire-input-model')
            inp = WireInput(bytes.fromhex(row['wire']))
            with self.assertRaises(ValueError):
                while inp.tag(): inp.skip()

    def test_legacy_selection_coordinates_do_not_identify_cn72_type(self):
        audit = self.observation['legacySelectionAudit']
        self.assertEqual(audit['targetCommandCount'], 4012)
        self.assertEqual(audit['targetCmd21136Matches'], [])
        self.assertEqual([(r['clientType'], r['cmdId'], r['getter']) for r in audit['targetSameIndexCommandBindings']],
                         [('DDFOJHINPLA', 29156, '0x149813ec0')])
        self.assertEqual(audit['targetTypeAt46070']['name'], 'DDFOJHINPLA')
        self.assertNotEqual(audit['targetTypeAt46070']['name'], audit['historicalClaim']['clientType'])


if __name__ == '__main__': unittest.main()
