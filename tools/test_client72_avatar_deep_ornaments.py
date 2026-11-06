"""Independent literal/cache/loader source checks for tag38's resources."""
import hashlib
import json
import os
from pathlib import Path
import unittest

from tools.client72_avatar_deep_ornaments import INPUTS
from tools.extract_client72 import HASHES


class AvatarOrnamentSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        cls.report = json.loads(Path(os.environ.get('CLIENT72_AVATAR_ORNAMENT_OBSERVATIONS',
            cls.root / 'protocol/cn-7.2/avatar-deep-ornaments-observations.json')).read_text('utf8'))

    def test_fixed_inputs_and_sources(self):
        self.assertEqual(HASHES, self.report['inputSHA256'])
        self.assertEqual(INPUTS, self.report['inputContracts'])
        for name, digest in self.report['sourceSHA256'].items():
            self.assertEqual(digest, hashlib.sha256((self.root / name).read_bytes()).hexdigest())

    def test_independent_named_resource_literals(self):
        expected = [(28604, 29427, '0x145b035b8', 'AvatarOrnamentGroupExcelConfigData'),
                    (39027, 40297, '0x145b18968', 'AvatarOrnamentExcelConfigData')]
        for row, (owner, index, cell, name) in zip(self.report['declarations'], expected):
            self.assertEqual(owner, row['owner'])
            self.assertEqual(index, row['literal']['index'])
            self.assertEqual(cell, row['literal']['cell'])
            self.assertEqual('Data/_ExcelBinOutput/' + name, row['literal']['value'])

    def test_cache_types_match_the_existing_tag38_consumers(self):
        for row, offset, child in zip(self.report['declarations'], ('0x13b70', '0x1cd98'), (61286, 44799)):
            self.assertEqual(offset, row['cacheOffset'])
            arguments = row['cacheField']['type']['arguments']
            self.assertEqual('uint32', arguments[0]['kind'])
            self.assertEqual(child, arguments[1]['typeDefinition'])
            self.assertTrue(row['initializedOwnerCells'])

    def test_exact_getter_loader_references_and_native_io_boundary(self):
        expected = [('0x15437ad90', '0x15437ab80', '0x15437aba7'),
                    ('0x147366b70', '0x147366a40', '0x147366a67')]
        for decl, native, (getter, loader, literal_site) in zip(self.report['declarations'], self.report['native'], expected):
            self.assertEqual([getter, loader], [row['entry'] for row in decl['functions']])
            self.assertIn(literal_site, decl['functions'][1]['literalReferenceSites'])
            self.assertEqual([getter, loader], [row['entry'] for row in native['traces']])
            self.assertEqual(['0x620000100', '0x14d6e1570'], [row['stop'] for row in native['traces']])
            self.assertEqual([[], []], [row['models'] for row in native['traces']])
            self.assertTrue(native['exactLiteralPointerPreserved'])

    def test_no_parser_or_live_acceptance_claim(self):
        for flag in ('resourceFileIOExecuted', 'completeAvatarHandler', 'completeConstructor', 'targetProtocolReady'):
            self.assertFalse(self.report[flag])
        self.assertEqual('not-tested', self.report['runtimeStatus'])


if __name__ == '__main__':
    unittest.main()
