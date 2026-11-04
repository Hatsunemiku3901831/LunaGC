"""Synthetic validator fixtures; no fixture is asserted to be a real 7.2 message."""
import copy
import unittest

from tools.validate_protocol72 import validate


class Mapping72Test(unittest.TestCase):
    def setUp(self):
        self.profile = {'target': {'version': '7.2.0', 'channel': 1, 'subChannel': 1},
                        'files': {'binary': {'sha256': 'a' * 64}}}
        self.doc = {'schemaVersion': 1, 'target': self.profile['target'], 'requiredMessages': ['ExampleReq'],
                    'closure': {'status': 'verified', 'evidence': ['closure']},
                    'evidence': [dict(id=k, layer=k, inputFile='binary', sha256='a'*64, location='synthetic-test')
                                 for k in ('opcode', 'message', 'field', 'closure')],
                    'opcodes': [dict(semanticName='ExampleReq', clientType='ABC', cmdId=65535, status='verified', evidence=['opcode'])],
                    'messages': [dict(semanticName='ExampleReq', clientType='ABC', direction='C2S', aliases=['AliasReq'], status='verified', evidence=['message'])],
                    'schemas': [dict(semanticName='ExampleReq', status='verified', evidence=['field'], fields=[
                        dict(semanticField='guid', tag=9, type='uint64', cardinality='singular', presence='implicit',
                             packed=False, status='verified', evidence=['field'])])]}

    def rejected(self, fragment):
        self.assertTrue(any(fragment in e for e in validate(self.doc, self.profile, ready=True)))

    def test_synthetic_complete_structure_passes_without_claiming_runtime(self):
        self.assertEqual([], validate(self.doc, self.profile, ready=True))

    def test_unknown_seed_can_be_saved_but_never_published(self):
        self.doc['closure']['status'] = 'unresolved'
        for table in ('opcodes', 'messages', 'schemas'):
            self.doc[table][0].update(status='unresolved', evidence=[])
        self.doc['opcodes'][0]['cmdId'] = None
        self.doc['messages'][0]['clientType'] = None
        self.doc['schemas'][0]['fields'] = []
        self.assertEqual([], validate(self.doc, self.profile))
        self.rejected('not verified')

    def test_old_or_wrong_file_evidence_fails(self):
        self.doc['evidence'][0]['sha256'] = 'b'*64
        self.rejected('hash mismatch')

    def test_opcode_evidence_cannot_prove_a_wire_field(self):
        self.doc['schemas'][0]['fields'][0]['evidence'] = ['opcode']
        self.rejected('wrong or missing field')

    def test_aliases_cannot_be_two_canonical_packets(self):
        for table in ('opcodes', 'messages', 'schemas'):
            row = copy.deepcopy(self.doc[table][0]); row['semanticName'] = 'SecondReq'
            self.doc[table].append(row)
        self.rejected('multiple canonical')

    def test_opcode_zero_negative_overflow_and_bool_rejected(self):
        for value in (0, -1, 65536, True):
            self.doc['opcodes'][0]['cmdId'] = value
            self.rejected('uint16')

    def test_duplicate_reserved_and_unknown_nested_fields_fail(self):
        field = self.doc['schemas'][0]['fields'][0]
        field.update(tag=19000, type='message', nestedType='Missing')
        self.rejected('reserved tag')
        self.rejected('missing nested schema')
        second = copy.deepcopy(field); second['semanticField'] = 'other'
        self.doc['schemas'][0]['fields'].append(second)
        self.rejected('duplicate tag')

    def test_mismatched_client_type_and_verified_ambiguity_fail(self):
        self.doc['opcodes'][0]['clientType'] = 'XYZ'
        self.rejected('disagree')
        self.doc['messages'][0]['ambiguities'] = ['two possible callsites']
        self.rejected('unresolved ambiguity')

    def test_map_requires_real_value_type_and_explicit_presence(self):
        field = self.doc['schemas'][0]['fields'][0]
        field.update(type='map', cardinality='map', keyType='string', valueType='imaginary')
        self.rejected('map key/value')
        field.update(type='uint64', cardinality='singular'); field.pop('presence')
        self.rejected('presence')

    def test_packed_string_or_repeated_oneof_fail(self):
        self.doc['schemas'][0]['fields'][0].update(type='string', cardinality='repeated', packed=True, oneof='choice')
        self.rejected('packed')
        self.rejected('oneof')

    def test_unknown_format_old_target_and_invalid_enum_are_rejected(self):
        self.doc['schemaVersion'] = 999
        self.rejected('schema version')
        self.profile['target']['version'] = '7.1.0'
        self.rejected('CN 7.2')
        self.doc['schemas'][0]['fields'][0].update(type='enum', enumValues={'VALUE': '1'})
        self.rejected('enum values')


if __name__ == '__main__':
    unittest.main()
