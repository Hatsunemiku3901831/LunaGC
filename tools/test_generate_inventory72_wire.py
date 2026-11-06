"""Generation drift cannot remove native oneof/zero/list/map wire evidence."""
import copy
import json
from pathlib import Path
import unittest

from tools.generate_inventory72_wire import render_sources, OUTPUT

ROOT = Path(__file__).resolve().parents[1]


class InventoryWireGeneratorTests(unittest.TestCase):
    def setUp(self):
        self.raw = json.loads((ROOT / 'protocol/cn-7.2/inventory-graph-observations.json').read_text('utf8'))

    def test_only_separate_raw_namespace_is_written(self):
        outputs = render_sources(self.raw)
        self.assertEqual({OUTPUT}, set(outputs))
        self.assertEqual((ROOT / OUTPUT).read_bytes(), outputs[OUTPUT].encode('utf8'))
        self.assertIn('package cn72.inventorywire;', outputs[OUTPUT])

    def test_modified_field_or_missing_branch_rejects(self):
        for change in ('tag', 'branch', 'oneof', 'packing'):
            with self.subTest(change=change):
                raw = copy.deepcopy(self.raw)
                item = next(row for row in raw['messages'] if row['typeIndex'] == 65333)
                if change == 'tag': item['fields'][0]['tag'] = 12
                elif change == 'branch': item['fields'].pop()
                elif change == 'oneof': item['fields'][2].pop('oneof')
                else: raw['messages'][0]['fields'][1]['packed'] = False
                with self.assertRaisesRegex(ValueError, 'evidence changed'): render_sources(raw)

    def test_input_live_claim_and_unknown_semantics_cannot_be_promoted(self):
        for key, value in (('inputSHA256', {}), ('runtimeStatus', 'passed'), ('semanticIdentityStatus', 'verified')):
            with self.subTest(key=key):
                raw = copy.deepcopy(self.raw); raw[key] = value
                with self.assertRaisesRegex(ValueError, 'evidence changed'): render_sources(raw)

    def test_golden_wire_or_result_cannot_change_with_the_schema(self):
        for key in ('wire', 'result'):
            with self.subTest(key=key):
                raw = copy.deepcopy(self.raw); raw['fixtures'][0][key] = '' if key == 'wire' else {}
                with self.assertRaisesRegex(ValueError, 'evidence changed'): render_sources(raw)


if __name__ == '__main__': unittest.main()
