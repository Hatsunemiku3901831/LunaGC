"""Negative gates for the bounded original windows and selected static slot."""
import copy
import json
import unittest

from tools import client72_d02_proud_mode_factory as subject


class OriginalModeFactoryGates(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.window_files={n:(subject.OUT/'mode-a1'/n).read_bytes() for n in subject.FILES}
        cls.slot_files={n:(subject.OUT/'slot9-1'/n).read_bytes() for n in subject.SLOTFILES}

    def validate_window(self,files):
        return subject.window(files,20540,158254,0x14f587b40,0x14f587bd6,37)

    def test_both_complete_windows_and_one_definition(self):
        for stem,owner,method,entry,end,count,_,_ in subject.WINDOWS:
            files={n:(subject.OUT/(stem+'1')/n).read_bytes() for n in subject.FILES}
            report,_=subject.window(files,owner,method,entry,end,count)
            self.assertTrue(report['passed'])
        proof=subject.slot_definition(self.slot_files)
        self.assertTrue(proof['staticSlotDefinitionBound'])
        self.assertFalse(proof['naturalVtableObserved'])

    def test_wrong_aot_slot_rejected(self):
        files=dict(self.window_files)
        files['slot.bin']=(0x14f587b41).to_bytes(8,'little')
        with self.assertRaisesRegex(ValueError,'AOT'):
            self.validate_window(files)

    def test_second_containing_pdata_rejected(self):
        files=dict(self.window_files)
        report=json.loads(files['extraction.private.json'])
        report['exceptionDirectory']['diagnostic']['containing'].append(copy.deepcopy(report['exceptionDirectory']['diagnostic']['exact'][0]))
        files['extraction.private.json']=json.dumps(report).encode()
        with self.assertRaisesRegex(ValueError,'pdata'):
            self.validate_window(files)

    def test_decode_order_and_original_bytes_required(self):
        files=dict(self.window_files)
        lines=json.loads(files['code.asm.private.json'])
        lines[1],lines[2]=lines[2],lines[1]
        files['code.asm.private.json']=json.dumps(lines).encode()
        with self.assertRaisesRegex(ValueError,'decode'):
            self.validate_window(files)

    def test_methodspec_word_cannot_replace_method_definition(self):
        files=dict(self.slot_files)
        files['slot9-definition.bin']=(0xc008134c).to_bytes(4,'little')
        with self.assertRaisesRegex(ValueError,'word'):
            subject.slot_definition(files)

    def test_slot9_requires_source_count_above9(self):
        files=dict(self.slot_files)
        raw=bytearray(files['type63831.bin']);raw[0x44]=9^0xf4
        files['type63831.bin']=bytes(raw)
        with self.assertRaisesRegex(ValueError,'count'):
            subject.slot_definition(files)


if __name__=='__main__':unittest.main()
