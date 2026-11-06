"""Mutation checks for the native-backed limited inventory production root."""
import copy
import json
from pathlib import Path
import unittest

from tools.generate_inventory72_sources import render_sources, OPCODES, PROTO

ROOT = Path(__file__).resolve().parents[1]


class InventorySourceTests(unittest.TestCase):
    def setUp(self):
        directory = ROOT / 'protocol/cn-7.2'
        self.inputs = [json.loads((directory / name).read_text('utf8')) for name in (
            'mappings.json', 'profile.json', 'inventory-graph-observations.json',
            'inventory-contract-observations.json', 'equip-contract-observations.json')]
        self.opcodes = (ROOT / OPCODES).read_text('utf8')

    def render(self, values=None, source=None):
        return render_sources(*(self.inputs if values is None else values),
                              self.opcodes if source is None else source)

    def schema(self, values, name):
        return next(r for r in values[0]['schemas'] if r['semanticName'] == name)

    def test_only_root_and_canonical_command_change(self):
        outputs = self.render()
        self.assertEqual({OPCODES, PROTO / 'PlayerStoreNotify.proto'}, set(outputs))
        for path, value in outputs.items(): self.assertEqual((ROOT / path).read_bytes(), value.encode('utf8'))
        self.assertIn('cn72.inventorywire.NDGHFPMJFIM', outputs[PROTO / 'PlayerStoreNotify.proto'])

    def test_unknown_weight_reliquary_and_weapon_fields_cannot_be_named(self):
        for name, tag, semantic in (('PlayerStoreNotify', 8, 'weight_limit'), ('Reliquary', 2, 'exp'),
                                     ('Weapon', 5, 'is_favourite')):
            with self.subTest(name=name):
                values = copy.deepcopy(self.inputs)
                field = next(f for f in self.schema(values, name)['fields'] if f['tag'] == tag)
                field.update(status='verified', semanticField=semantic, ambiguities=[])
                with self.assertRaisesRegex(ValueError, 'raw/semantic boundary'): self.render(values)

    def test_both_equip_branches_and_material_branch_keep_their_native_types(self):
        values = copy.deepcopy(self.inputs)
        field = next(f for f in self.schema(values, 'Equip')['fields'] if f['tag'] == 1)
        field.update(nestedType='Weapon', clientNestedType='FNECPDFLEHN')
        with self.assertRaisesRegex(ValueError, 'raw/semantic boundary'): self.render(values)

    def test_lock_enum_is_separate_from_boolean_wire(self):
        values = copy.deepcopy(self.inputs)
        field = next(f for f in self.schema(values, 'Equip')['fields'] if f['tag'] == 3)
        field['applicationEncoding']['wireTrueToModel'] = 1
        with self.assertRaisesRegex(ValueError, 'raw/semantic boundary'): self.render(values)

    def test_scope_hash_or_native_report_cannot_be_changed_with_mapping(self):
        for table, key, value in ((2, 'targetProtocolReady', True), (3, 'inputSHA256', {}),
                                  (4, 'completeWeaponConstructor', True)):
            with self.subTest(table=table):
                values = copy.deepcopy(self.inputs); values[table][key] = value
                with self.assertRaises(ValueError): self.render(values)

    def test_affix_value_kind_and_oneof_presence_cannot_drift(self):
        values = copy.deepcopy(self.inputs)
        field = next(f for f in self.schema(values, 'Weapon')['fields'] if f['tag'] == 4)
        field['valueType'] = 'int32'
        with self.assertRaisesRegex(ValueError, 'raw/semantic boundary'): self.render(values)

    def test_new_command_collision_and_missing_canonical_declaration_reject(self):
        with self.assertRaisesRegex(ValueError, 'command collision'):
            self.render(source=self.opcodes + '\npublic static final int Unknown = 20892;\n')
        with self.assertRaisesRegex(ValueError, 'one canonical'):
            self.render(source=self.opcodes.replace('public static final int PlayerStoreNotify = 20892;', ''))


if __name__ == '__main__': unittest.main()
