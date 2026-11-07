"""Independent static examples and frozen-source rejection, without Java execution."""
import json
from pathlib import Path
import tempfile
import unittest

from tools import audit_d02_java_resource_contract as audit


class JavaResourceContractTest(unittest.TestCase):
    def test_declared_aliases_and_wide_java_type_are_read_not_invented(self):
        source = '''class Fixture {
            @SerializedName(value = "_key", alternate = {"key", "RAWKEY"})
            private long identifier;
            private List<Integer> references;
            @Getter private int count = 9;
        }'''
        wide = audit.inspect_field(source, 'identifier', 'long')
        self.assertEqual(wide['acceptedJavaKeys'], ['_key', 'key', 'RAWKEY'])
        self.assertEqual(wide['javaType'], 'long')
        self.assertEqual(wide['javaOmittedDefault'], '0')
        self.assertEqual(wide['declarationLine'], 3)
        self.assertFalse(wide['targetMappingApproved'])
        reference = audit.inspect_field(source, 'references', 'List<Integer>')
        self.assertEqual(reference['acceptedJavaKeys'], ['references'])
        self.assertEqual(reference['javaOmittedDefault'], 'null')
        self.assertEqual(audit.inspect_field(source, 'count', 'int')['javaOmittedDefault'], '9')

    def test_resource_serialized_name_replaces_java_field_name(self):
        source = '@SerializedName("type")\nprivate SceneType sceneType;'
        field = audit.inspect_field(source, 'sceneType', 'SceneType')
        self.assertEqual(field['acceptedJavaKeys'], ['type'])
        self.assertEqual(field['javaOmittedDefault'], 'null')

    def test_annotation_with_braced_alternates_and_public_float(self):
        source = '''class Fixture {
            @SerializedName(
                value = "specialEnergyMin",
                alternate = {"LNEGGCDIDCP", "specialEnergyCostMax"})
            public float minimum;
        }'''
        field = audit.inspect_field(source, 'minimum', 'float')
        self.assertEqual(field['acceptedJavaKeys'],
                         ['specialEnergyMin', 'LNEGGCDIDCP', 'specialEnergyCostMax'])
        self.assertEqual(field['javaOmittedDefault'], '0')

    def test_wrong_or_ambiguous_field_type_cannot_supply_a_contract(self):
        for source in ('private String value;', 'private int value;\nprivate int value;'):
            with self.subTest(source=source), self.assertRaisesRegex(ValueError, 'one typed'):
                audit.inspect_field(source, 'value', 'int')

    def test_absent_zero_null_empty_and_unsigned64_remain_distinct(self):
        source = json.loads('{"zero":0,"null":null,"empty":[],"wide":18446744073709551615}')
        result = audit.project_row(source, ['absent', 'zero', 'null', 'empty', 'wide'])
        self.assertEqual(result['absent'], {'present': False})
        self.assertEqual(result['zero'], {'present': True, 'value': 0})
        self.assertEqual(result['null'], {'present': True, 'value': None})
        self.assertEqual(result['empty'], {'present': True, 'value': []})
        self.assertEqual(result['wide']['value'], (1 << 64) - 1)
        self.assertEqual(source, {'zero': 0, 'null': None, 'empty': [], 'wide': (1 << 64) - 1})

    def test_candidate_uses_only_declared_java_keys_and_keeps_nested_owner(self):
        source = '@SerializedName("type")\nprivate SceneType sceneType;'
        field = audit.inspect_field(source, 'sceneType', 'SceneType')
        row = {'type': 'SCENE_WORLD', 'sceneType': 'unapproved same-name guess'}
        result = audit.candidate_projection(row, [field,
            dict(field='param', acceptedJavaKeys=['param'], nestedOwner='QuestCondition')])
        self.assertEqual(result, {'sceneType': {'acceptedJavaKeys': ['type'],
                                              'rawMatches': {'type': 'SCENE_WORLD'}}})
        self.assertEqual(audit.candidate_projection({}, [field])['sceneType']['rawMatches'], {})

    def test_order_guard_rejects_onload_after_install(self):
        anchors = ['resource.onLoad();', 'map.put(id, resource);']
        self.assertEqual([x['line'] for x in audit.ordered_anchors('\n'.join(anchors), anchors)], [1, 2])
        with self.assertRaisesRegex(ValueError, 'out-of-order'):
            audit.ordered_anchors('\n'.join(reversed(anchors)), anchors)

    def test_same_length_source_change_is_rejected_before_interpretation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'Input.java'
            path.write_bytes(b'private int id;')
            expected = audit.sha256(path.read_bytes())
            self.assertEqual(audit.read_pinned(path, expected), b'private int id;')
            path.write_bytes(b'private int xx;')
            with self.assertRaisesRegex(ValueError, 'Frozen input changed'):
                audit.read_pinned(path, expected)

    def test_missing_or_duplicate_candidate_id_is_not_last_write_success(self):
        for rows in ([], [{'id': 5}, {'id': 5}]):
            with self.subTest(rows=rows), self.assertRaisesRegex(ValueError, 'one candidate'):
                audit.select_row(rows, 'id', 5)

    def test_duplicate_source_json_key_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate JSON key'):
            json.loads('{"id":5,"id":7}', object_pairs_hook=audit.unique_object)


if __name__ == '__main__':
    unittest.main()
