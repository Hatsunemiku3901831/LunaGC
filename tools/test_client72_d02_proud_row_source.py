"""Regression checks for the real static window and selected literal boundary."""
import copy
import json
import unittest

from tools import client72_d02_proud_row_source as audit


class ProudRowSourceEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.literal = json.loads(audit.pinned(audit.OUT/'reader-literal1.private.json',
            audit.PRIVATE_PINS['reader-literal1.private.json']))
        cls.files = {p:audit.pinned(audit.OUT/'typed-producer1'/p,digest)
                     for p,digest in audit.PRODUCER_PINS.items()}

    def test_real_path_and_source_bytes_reproduce(self):
        self.assertEqual(audit.reader_literal(self.literal),'Data/_ExcelBinOutput/ProudSkillExcelConfigData')
        report,decoded = audit.window(self.files,0x14d7ad8e0,0x14d7adb3e,145)
        self.assertEqual((report['codeBytes'],len(decoded)),(606,145))

    def test_source_index_is_checked_independently(self):
        literal = copy.deepcopy(self.literal)
        literal['registration']['index'] -= 1
        with self.assertRaisesRegex(ValueError,'Source index or destination slot'):
            audit.reader_literal(literal)

    def test_other_destination_slot_cannot_supply_the_path(self):
        literal = copy.deepcopy(self.literal)
        literal['registration']['slot'] += 1
        with self.assertRaisesRegex(ValueError,'Source index or destination slot'):
            audit.reader_literal(literal)

    def test_encrypted_byte_drift_is_rejected(self):
        literal = copy.deepcopy(self.literal)
        raw = bytearray.fromhex(literal['source']['encryptedHex'])
        raw[0] ^= 1
        literal['source']['encryptedHex'] = raw.hex()
        with self.assertRaisesRegex(ValueError,'cipher or bound'):
            audit.reader_literal(literal)

    def test_neighbor_method_cannot_supply_an_extent(self):
        with self.assertRaisesRegex(ValueError,'Static window extent'):
            audit.window(self.files,0x14d7ad8e0,0x14d7adb40,145)

    def test_wrong_original_aot_slot_is_rejected(self):
        files = dict(self.files)
        files['slot.bin'] = (0x14d7adf70).to_bytes(8,'little')
        with self.assertRaisesRegex(ValueError,'Original AOT QWORD'):
            audit.window(files,0x14d7ad8e0,0x14d7adb3e,145)


if __name__=='__main__':
    unittest.main()
