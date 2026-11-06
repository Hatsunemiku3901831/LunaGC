import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from tools import audit_astaps72 as audit


def indexed(source):
    messages, enums = audit.parse_proto(source, 'synthetic.proto')
    audit.resolve_shapes(messages, enums)
    return messages, enums


def mapping(fields, cmd=41):
    return dict(schemas=[dict(semanticName='Example', status='candidate', fields=fields)],
                opcodes=[dict(semanticName='Example', clientType='RAWEXAMPLE', cmdId=cmd)])


def target(name='raw_name', tag=1, typ='uint32', cardinality='singular', packed=False, status='unresolved', semantic='unknown_field_1'):
    return dict(clientField=name, tag=tag, type=typ, cardinality=cardinality,
                packed=packed, status=status, semanticField=semantic)


class AstaPSAuditTests(unittest.TestCase):
    def test_nested_maps_oneof_enums_and_explicit_packing_are_retained(self):
        messages, enums = indexed('''syntax = "proto3";
message Example {
  message Child {
    uint64 value = 1;
  }
  enum Mode {
    NONE = 0;
    ERROR = -1;
  }
  map<uint64, Child> entries = 5;
  repeated sint64 signed_values = 6 [packed=false];
  oneof content {
    Child child = 7;
    Mode mode = 8;
  }
}
''')
        fields = messages['Example']['fields']
        self.assertEqual(('map', 'uint64', 'message', 'Example.Child'),
                         tuple(fields[0][k] for k in ('type', 'mapKeyType', 'mapValueType', 'mapValueTypeName')))
        self.assertFalse(fields[1]['packed'])
        self.assertEqual('content', fields[2]['oneof'])
        self.assertEqual('enum', fields[3]['type'])
        self.assertEqual(-1, enums['Example.Mode']['ERROR'])
        self.assertEqual('uint64', messages['Example.Child']['fields'][0]['type'])

    def test_unindexed_unresolved_or_duplicate_source_is_rejected(self):
        for source in ('message X { uint32 a = 1; }', 'message X {\n  Missing a = 1;\n}',
                       'message X {\n uint32 a = 1;\n uint64 b = 1;\n}'):
            with self.subTest(source=source), self.assertRaises(ValueError):
                indexed(source)

    def test_zero_negative_and_old_opcode_comments_never_supply_target_id(self):
        source = '''// public static final int Fake = 999;
public static final int Example = 0; // 7.2 unknown (7.1: 41)
public static final int Negative = -1001; // no old ID
'''
        opcodes = audit.parse_opcodes(source)
        self.assertNotIn('Fake', opcodes)
        self.assertEqual(dict(value=0, line=2, legacy71=41), opcodes['Example'])
        self.assertEqual(-1001, opcodes['Negative']['value'])
        result = audit.compare_targets(mapping([]), {}, opcodes)[0]
        self.assertFalse(result['opcodeMatches'])
        opcodes['Example']['value'] = -1001
        self.assertFalse(audit.compare_targets(mapping([]), {}, opcodes)[0]['opcodeMatches'])

    def test_parked_or_old_field_is_missing_target_tag_without_name_fallback(self):
        messages, _ = indexed('''message Example {
 uint32 account_type = 20004;
 uint32 other = 7;
}
''')
        data = mapping([target(tag=15, status='verified', semantic='account_type'), target(tag=8)])
        result = audit.compare_targets(data, messages, {})[0]
        self.assertEqual(2, result['counts']['missingTargetTags'])
        self.assertTrue(all(f['astaAtSameTag'] is None for f in result['fields']))

    def test_shape_detects_type_cardinality_packed_and_map_key_conflicts(self):
        messages, _ = indexed('''message Example {
 int32 a = 1;
 repeated uint32 b = 2;
 repeated uint32 c = 3 [packed = false];
 map<uint32, int32> d = 4;
}
''')
        fields = [target(typ='uint32'), target(tag=2), target(tag=3, cardinality='repeated', packed=True),
                  dict(target(tag=4, typ='map', cardinality='map'), keyType='uint64', valueType='int32')]
        result = audit.compare_targets(mapping(fields), messages, {})[0]
        self.assertEqual(4, result['counts']['outerShapeConflict'])

    def test_verified_semantic_swap_is_conflict_even_when_wire_shape_agrees(self):
        messages, _ = indexed('''message Example {
 string client_silence_md5 = 437;
 string client_md5 = 1255;
}
''')
        fields = [target('RAWMD5', 437, 'string', status='verified', semantic='client_md5'),
                  target('RAWSILENCE', 1255, 'string', status='verified', semantic='client_silence_md5')]
        result = audit.compare_targets(mapping(fields), messages, {})[0]
        self.assertEqual(2, result['counts']['matchingOuterShape'])
        self.assertEqual(2, result['counts']['verifiedSemanticConflict'])

    def test_named_target_message_map_compares_outer_kind_without_claiming_nested_equivalence(self):
        messages, _ = indexed('''message Example {
 map<uint32, SourceChild> values = 6;
}
message SourceChild {
 uint64 value = 1;
}
''')
        field = dict(target(tag=6, typ='map', cardinality='map'), keyType='uint32', valueType='TargetChild')
        data = mapping([field])
        data['schemas'].append(dict(semanticName='TargetChild', status='unresolved', fields=[]))
        result = audit.compare_targets(data, messages, {})[0]
        self.assertTrue(result['fields'][0]['sameOuterShape'])
        self.assertEqual('SourceChild', result['fields'][0]['astaAtSameTag']['mapValueTypeName'])
        field['valueType'] = 'uint64'
        self.assertFalse(audit.compare_targets(data, messages, {})[0]['fields'][0]['sameOuterShape'])

    def test_candidate_name_is_not_promoted_and_mapping_is_not_mutated(self):
        messages, _ = indexed('message Example {\n uint32 old_business_name = 1;\n}')
        data = mapping([target('TARGETRAW', status='candidate', semantic='guessed_business_name')])
        before = copy.deepcopy(data)
        result = audit.compare_targets(data, messages, {})[0]
        self.assertEqual(before, data)
        self.assertEqual('TARGETRAW', result['fields'][0]['proposedFieldName'])
        self.assertFalse(result['fields'][0]['semanticConflict'])

    def test_reference_pin_rejects_wrong_commit_tree_and_dirty_state(self):
        def responses(values):
            return [subprocess.CompletedProcess([], 0, x.encode(), b'') for x in values]
        for values in (['other', audit.TREE, ''], [audit.COMMIT, 'other', ''],
                       [audit.COMMIT, audit.TREE, ' M src/main/proto/Example.proto']):
            with patch.object(audit.subprocess, 'run', side_effect=responses(values)), self.assertRaises(ValueError):
                audit.verify_reference(Path('synthetic'))
        with patch.object(audit.subprocess, 'run', side_effect=responses([audit.COMMIT, audit.TREE, ''])):
            self.assertTrue(audit.verify_reference(Path('synthetic'))['clean'])

    def test_mapping_and_tree_drift_are_rejected_before_protoc(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); mapping_path = root / 'mapping.json'
            mapping_path.write_text('{}', encoding='utf8')
            with patch.object(audit, 'verify_reference', return_value={}), self.assertRaisesRegex(ValueError, 'mapping source changed'):
                audit.audit(root, Path('unused-protoc'), mapping_path)
            with patch.object(audit, 'verify_reference', return_value={}), patch.object(audit, 'MAPPING_SHA256', audit.text_hash(mapping_path)), self.assertRaisesRegex(ValueError, 'Proto source tree changed'):
                audit.audit(root, Path('unused-protoc'), mapping_path)

    def test_parked_wire_proof_requires_exact_nonzero_independent_bytes(self):
        expected = bytes.fromhex('90e20903b0e20901b8e20901')
        responses = [subprocess.CompletedProcess([], 0, expected, b''),
                     subprocess.CompletedProcess([], 0, b'reg_platform: 3\nis_proficient_player: true\naccount_type: 1\n', b'')]
        with patch.object(audit.subprocess, 'run', side_effect=responses):
            result = audit.parked_wire_check(Path('synthetic'), Path('protoc'))
        self.assertEqual(expected.hex(), result['actualWireHex'])
        self.assertTrue(result['independentReadAssert'])
        with patch.object(audit.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, b'', b'')), self.assertRaises(ValueError):
            audit.parked_wire_check(Path('synthetic'), Path('protoc'))
        responses = [subprocess.CompletedProcess([], 0, expected, b''), subprocess.CompletedProcess([], 0, b'account_type: 0', b'')]
        with patch.object(audit.subprocess, 'run', side_effect=responses), self.assertRaisesRegex(ValueError, 'read mismatch'):
            audit.parked_wire_check(Path('synthetic'), Path('protoc'))

    def test_fixed_summary_records_limits_and_independent_real_integration(self):
        summary = json.loads((audit.ROOT / 'protocol/cn-7.2/astaps-audit-observations.json').read_text('utf8'))
        self.assertEqual(audit.HASHES, summary['inputSHA256'])
        self.assertEqual(audit.COMMIT, summary['reference']['commit'])
        self.assertEqual(audit.PROTO_DIGEST, summary['protoSourceTree']['sha256'])
        self.assertEqual(audit.MAPPING_SHA256, summary['targetMapping']['sha256'])
        self.assertEqual((248, 156, 92, 5), tuple(summary['counts'][k] for k in
            ('targetFields', 'matchingTargetOuterShape', 'missingTargetTags', 'verifiedSemanticConflicts')))
        self.assertEqual((967, 567, 12), tuple(summary['counts'][k] for k in ('parkedFields', 'zeroOpcodes', 'negativeOpcodes')))
        self.assertTrue(summary['compilation']['success'])
        self.assertTrue(summary['parkedWireCheck']['independentWireAssert'])
        self.assertFalse(summary['targetProtocolReady'])
        self.assertEqual('not-tested', summary['runtimeStatus'])
        self.assertNotIn('sourceManifest', summary)
        self.assertEqual('GPL-3.0', summary['license']['project'])
        self.assertEqual('3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986', summary['keySourceSHA256']['LICENSE'])


if __name__ == '__main__':
    unittest.main()
