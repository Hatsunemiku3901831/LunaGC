"""Frozen provenance and semantic-boundary regressions for the two Proud windows."""
import copy
import io
import json
import unittest
from unittest.mock import patch

from tools import client72_d02_proud_factory_index_contract as subject


class ContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest, cls.fixed = subject.fixed_sources()
        cls.records, cls.cells = subject.PureData(io.BytesIO(cls.fixed[subject.META])).load()
        cls.signatures = json.loads(cls.fixed[subject.SIG])

    def test_real_contract_preserves_success_and_row_unknowns(self):
        result = subject.audit()
        self.assertEqual(result['indexCctor']['directStoreFields'], [400190, 400189, 400193])
        self.assertEqual(result['indexCctor']['noDirectStoreFields'], [400191, 400192])
        self.assertEqual(result['factory']['ordinaryTail']['requestedType'], 41105)
        for key in ['factoryExecuted', 'indexContentLoadedProven', 'actualRowSpanApproved',
                    'managedRepresentationProvenForActualNewObject', 'parserExecuted', 'resourceReady']:
            self.assertFalse(result[key], key)
        self.assertFalse(result['minimumNextSource']['sourceIndexEqualsDestinationSlotAssumed'])

    def test_source_drift_rejected_before_metadata(self):
        def read(path):
            raw = path.read_bytes()
            return raw + b'x' if path == subject.ROOT / subject.META else raw
        with patch.object(subject.PureData, 'load', side_effect=AssertionError('must not unpickle')):
            with self.assertRaisesRegex(ValueError, 'fixed source drift'):
                subject.fixed_sources(read)

    def test_other_closed_tuple_cannot_replace5071(self):
        cells = copy.deepcopy(self.cells)
        cells[0x1458B5F28]['type']['instanceIndex'] = 50463
        with self.assertRaisesRegex(ValueError, 'closed type/field tuple'):
            subject.typed_sources(self.records, cells, self.signatures)

    def test_wrong_actual_field_store_is_rejected(self):
        lines = json.loads(self.fixed[subject.BASE + 'c-proud-index-ctor-window/ctor1/code.asm.private.json'])
        next(line for line in lines if line['address'] == '0x14d7adbf5')['operands'] = 'qword ptr [rcx + 0x21f60], rax'
        with self.assertRaisesRegex(ValueError, 'cctor original instruction'):
            subject.cctor_contract(lines)

    def test_code_and_aot_drift_are_rejected(self):
        from tools import client72_d02_proud_factory_window as validator
        files = {name: self.fixed[subject.BASE + 'c-proud-factory-window/factory1/' + name] for name in subject.FILES}
        wrong_code = dict(files, **{'code.bin': b'\xc3' + files['code.bin'][1:]})
        with self.assertRaisesRegex(ValueError, 'full original body'):
            validator.validate_window(wrong_code)
        wrong_aot = dict(files, **{'slot.bin': (0x149F0BB41).to_bytes(8, 'little')})
        with self.assertRaisesRegex(ValueError, 'AOT binding'):
            validator.validate_window(wrong_aot)

    def test_tail_jump_cannot_be_substituted_by_zero_ret(self):
        lines = json.loads(self.fixed[subject.BASE + 'c-proud-factory-window/factory1/code.asm.private.json'])
        target = next(line for line in lines if line['address'] == '0x149f0bb66')
        target.update(mnemonic='ret', operands='')
        with self.assertRaisesRegex(ValueError, 'factory original instruction'):
            subject.factory_contract(lines)


if __name__ == '__main__':
    unittest.main()
