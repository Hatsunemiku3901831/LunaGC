"""1017 domain separation without inventing a gameplay name or S2C direction."""
import copy
import json
from pathlib import Path
import unittest
from tools.client72_cmd1017_domain import validate_fixtures, GAME_SHA256
from tools.extract_client72 import HASHES

ROOT = Path(__file__).resolve().parents[1]


class Command1017DomainTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((ROOT/'protocol/cn-7.2/cmd1017-domain-observations.json').read_text('utf8'))

    def test_raw_command_duplicate_is_preserved_and_domains_are_separate(self):
        report = self.report
        self.assertEqual(report['inputSHA256'], HASHES)
        self.assertEqual([(r['typeIndex'], r['cmdId']) for r in report['declarations']['staticCommands']], [(22065, 1017), (63999, 1017)])
        self.assertTrue(report['globalStaticDuplicateRetained'])
        self.assertEqual({r['typeIndex']: r['transportDomain'] for r in report['domains']},
                         {63999: 'game-session', 22065: 'beyond-wpf-stream'})
        external = next(r for r in report['domains'] if r['typeIndex'] == 22065)
        self.assertIsNone(external['semanticName'])
        self.assertEqual(external['direction'], 'process-outbound-to-external-stream')
        self.assertEqual(report['runtimeStatus'], 'not-tested'); self.assertFalse(report['targetProtocolReady'])

    def test_external_native_path_keeps_actual_message_command_and_writer(self):
        validate_fixtures(copy.deepcopy(self.report['fixtures']))
        for fixture in self.report['fixtures']:
            self.assertEqual(fixture['completion'], 'external-stream-queue-entry-only')
            self.assertEqual(fixture['trace']['stop'], '0x14bbe8100')
            self.assertEqual(fixture['envelopeTypeIndex'], 18771)
            self.assertEqual(fixture['envelopeCommand'], 1017)

    def test_type_usage_and_independent_stream_literals_are_bound(self):
        d = self.report['declarations']
        self.assertEqual({r['typeIndex']: r['cell'] for r in d['typeUsages']},
                         {22065: '0x1458d86f0', 34182: '0x1458ccf88', 18771: '0x1458d7fd0'})
        self.assertEqual({r['value'] for r in d['transportLiterals']}, {'beyond socket error ', 'wpf send thread error '})
        self.assertEqual(d['externalSenderParameters'][0]['kind'], 'uint16')
        self.assertEqual(d['externalSenderParameters'][1]['typeDefinition'], 67861)
        self.assertTrue(any(r['field']['type']['name'] == 'NetworkStream' for r in d['transportFields']))

    def test_existing_game_domain_request_has_independent_named_sender_proof(self):
        game = self.report['declarations']['gameEvidence']
        self.assertEqual(game['sha256'], GAME_SHA256)
        self.assertEqual(game['role']['literal']['value'], 'Genshin Loading Log: NetworkManager RequestEnterSceneDone')
        self.assertEqual(len(game['requestObservations']), 4)
        for row in game['requestObservations']:
            self.assertEqual((row['typeIndex'], row['entry'], row['stop']), (63999, '0x15121e380', '0x15120d730'))
            self.assertTrue(row['protocMatch']); self.assertTrue(row['requestPointerPreserved'])

    def test_changed_command_or_game_sender_cannot_pass_as_external_evidence(self):
        for key, value in [('envelopeCommand', 1018), ('messageTypeIndex', 63999), ('messagePointerPreserved', False)]:
            rows = copy.deepcopy(self.report['fixtures']); rows[0][key] = value
            with self.assertRaises(ValueError): validate_fixtures(rows)
        rows = copy.deepcopy(self.report['fixtures'])
        rows[0]['trace']['executedAddresses']['0x15120d730'] = 1
        with self.assertRaises(ValueError): validate_fixtures(rows)


if __name__ == '__main__': unittest.main()
