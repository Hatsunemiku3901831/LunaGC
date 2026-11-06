import unittest
from tools.check_login72_contract import rename_root_fields


class ContractTextRenameTest(unittest.TestCase):
    def test_nested_maps_and_quoted_sentinels_are_not_renamed(self):
        text='RAW: 7 CHILD { RAW: 8 INNER { RAW: 9 } } WORD: "RAW { \\" }" RAW: 10'
        self.assertEqual(rename_root_fields(text,{'RAW':'verified','WORD':'value'}),
            'verified: 7 CHILD { RAW: 8 INNER { RAW: 9 } } value: "RAW { \\" }" verified: 10')


if __name__=='__main__':unittest.main()
