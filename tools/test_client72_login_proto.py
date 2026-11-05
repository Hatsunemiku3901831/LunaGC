import copy
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from tools.review_client72_login_proto import recover, gap_matrix, read_descriptors, packet_candidates


class LoginProtoTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.java = next((cls.repo / 'local/runtime').glob('jdk-17*'))
        cache = cls.repo / 'local/gradle/caches/modules-2/files-2.1/com.google.protobuf'
        cls.protoc = next((cache / 'protoc/3.18.1').rglob('*.exe'))
        cls.jar = next((cache / 'protobuf-java/3.19.6').rglob('*.jar'))
        cls.review = cls.repo / 'protocol/cn-7.2/login-proto-review.json'
        cls.report = json.loads((cls.repo / 'protocol/cn-7.2/login-proto-gap-matrix.json').read_text('utf8'))

    def test_real_matrix_reproduces_and_keeps_all_layers_unchanged(self):
        actual = recover(self.repo, self.java, self.protoc, self.jar, self.review)
        self.assertEqual(actual, self.report)
        mapping = json.loads((self.repo / 'protocol/cn-7.2/mappings.json').read_text('utf8'))
        matrix = {row['legacyName']: row for row in actual['messageMatrix']}
        for kind in ('opcodes', 'messages', 'schemas'):
            for original in mapping[kind]:
                self.assertEqual(matrix[original['semanticName']]['layers'][kind], original)
        self.assertFalse(actual['closureComplete'])
        self.assertFalse(actual['targetProtocolReady'])
        self.assertEqual(actual['missingProtoRoots'], [])
        self.assertIn('DoSetPlayerBornDataNotify.proto', actual['protoDependencies']['files'])
        self.assertTrue(matrix['DoSetPlayerBornDataNotify']['layers']['schemas']['emptySchemaEvidence'])
        self.assertNotIn('schemas: no field evidence; this does not prove an empty target message',
                         matrix['DoSetPlayerBornDataNotify']['gaps'])
        self.assertEqual(actual['commandTableScope']['transportDomainsStatus'], 'unresolved')
        self.assertEqual(len(actual['commandTableScope']['duplicateValues']), 5)
        dynamic = next(v for v in actual['packetCandidates'] if v['dynamicOpcode'])
        self.assertEqual({tuple(v['constructorArguments']) for v in dynamic['sourceSites']}, {
            ('PacketOpcodes.ItemAddHintNotify',), ('packet.getOpcode()', 'packet.shouldBuildHeader()'), ('opcode',)})
        self.assertIn('ItemAddHintNotify', matrix)

    def test_actual_nonzero_legacy_tags_64bit_lists_and_map_are_only_dependencies(self):
        graph = self.report['protoDependencies']
        fields = {row['name']: row for row in graph['fields'] if row['owner'] == '.AvatarDataNotify'}
        self.assertEqual(fields['choose_avatar_guid']['type'], 'TYPE_UINT64')
        self.assertEqual(fields['temp_avatar_guid_list']['label'], 'LABEL_REPEATED')
        self.assertEqual(fields['avatar_list']['typeName'], '.AvatarInfo')
        self.assertTrue(graph['types'][fields['avatar_team_map']['typeName']]['mapEntry'])
        row = next(v for v in self.report['messageMatrix'] if v['legacyName'] == 'AvatarDataNotify')
        self.assertEqual(row['layers']['schemas']['fields'], [])
        self.assertIsNone(row['layers']['opcodes'].get('cmdId'))
        self.assertFalse(row['targetProtocolReady'])

    def test_descriptor_scopes_oneof_map_enum_signed_boundary_and_missing_import(self):
        with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as folder:
            repo = Path(folder)
            proto = repo / 'src/main/proto'
            proto.mkdir(parents=True)
            (repo / 'tools').mkdir()
            (repo / 'tools/Client72ProtoDependencies.java').write_bytes(
                (self.repo / 'tools/Client72ProtoDependencies.java').read_bytes())
            (proto / 'Leaf.proto').write_text('syntax="proto3"; package fixture; message Leaf { sfixed32 error=1; }', 'utf8')
            (proto / 'Root.proto').write_text('''syntax="proto3"; package fixture; import "Leaf.proto";
message Root {
 message Inner { uint64 guid=7; }
 enum Status { ZERO=0; NEGATIVE=-7; }
 repeated Inner items=9;
 map<uint32, Leaf> values=11;
 oneof choice { Inner nested=13; uint64 boundary=17; }
 Status status=19;
}''', 'utf8')
            graph = read_descriptors(repo, {'Root.proto'}, self.protoc, self.java, self.jar, repo)
            self.assertEqual(set(graph['files']), {'Root.proto', 'Leaf.proto'})
            fields = {(row['owner'], row['name']): row for row in graph['fields']}
            self.assertEqual(fields['.fixture.Root.Inner', 'guid']['legacyTag'], 7)
            self.assertEqual(fields['.fixture.Root', 'items']['typeName'], '.fixture.Root.Inner')
            self.assertEqual(fields['.fixture.Root', 'nested']['oneofIndex'], 0)
            self.assertEqual(fields['.fixture.Root', 'boundary']['type'], 'TYPE_UINT64')
            self.assertTrue(graph['types']['.fixture.Root.ValuesEntry']['mapEntry'])
            self.assertEqual(fields['.fixture.Leaf', 'error']['type'], 'TYPE_SFIXED32')
            self.assertIn(dict(file='Root.proto', owner='.fixture.Root.Status', name='NEGATIVE', legacyValue=-7), graph['enumSymbols'])
            (proto / 'Leaf.proto').unlink()
            with self.assertRaises(subprocess.CalledProcessError):
                read_descriptors(repo, {'Root.proto'}, self.protoc, self.java, self.jar, repo)

    def test_legacy_name_or_partial_target_evidence_cannot_fill_other_layers(self):
        mapping = dict(opcodes=[dict(semanticName='Known', status='verified', cmdId=123,
                       clientType='TARGET', evidence=['opcode-only'])], messages=[], schemas=[])
        rows = gap_matrix({'Known', 'Legacy'}, mapping, {'Known': {'PacketKnown'}})
        known = next(v for v in rows if v['legacyName'] == 'Known')
        self.assertEqual(known['layers']['messages']['status'], 'unresolved')
        self.assertEqual(known['layers']['schemas']['status'], 'unresolved')
        legacy = next(v for v in rows if v['legacyName'] == 'Legacy')
        self.assertNotIn('cmdId', legacy['layers']['opcodes'])
        self.assertTrue(all(v['targetProtocolReady'] is False for v in rows))

    def test_packet_inheritance_includes_alias_and_non_packet_prefixed_subclass(self):
        with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as folder:
            repo = Path(folder)
            send = repo / 'src/main/java/emu/grasscutter/server/packet/send'
            send.mkdir(parents=True)
            base = repo / 'src/main/java/emu/grasscutter/net/packet/BasePacket.java'
            base.parent.mkdir(parents=True)
            base.write_text('class BasePacket {}', 'utf8')
            (send / 'PacketParent.java').write_text('class PacketParent extends BasePacket { RealOuterClass.Real r; /* FakeOuterClass */ }', 'utf8')
            (send / 'PackageReply.java').write_text('class PackageReply extends PacketParent {}', 'utf8')
            method = dict(kind='method', owner='Test', name='entry', parameters=[], file='Test.java', calls=[
                dict(kind='construct', selector='PackageReply', line=2, lambdaDepth=1)])
            rows = packet_candidates(repo, dict(methods=[dict(id='Test#entry()', directPacketArguments=[])]), [method])
            self.assertEqual(rows[0]['directProtoReferences'], ['Real.proto'])
            self.assertTrue(rows[0]['basePacketInheritanceObserved'])
            self.assertEqual(rows[0]['sourceSites'][0]['lambdaDepth'], 1)

    def test_input_drift_or_readiness_promotion_is_rejected(self):
        original = json.loads(self.review.read_text('utf8'))
        for group in ('sourceSHA256', 'evidenceSHA256', 'targetProtocolReady'):
            review = copy.deepcopy(original)
            if group == 'targetProtocolReady':
                review[group] = True
            else:
                review[group][next(iter(review[group]))] = '0' * 64
            with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as folder:
                path = Path(folder) / 'review.json'
                path.write_text(json.dumps(review), 'utf8')
                with self.assertRaises(ValueError):
                    recover(self.repo, self.java, self.protoc, self.jar, path)


if __name__ == '__main__':
    unittest.main()
