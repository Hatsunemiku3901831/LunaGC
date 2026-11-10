"""Evidence regressions for the real parser/constructor windows."""
import json
import struct
import unittest

from tools import client72_d02_proud_parser_contract as audit


class ProudParserContractEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files={name:(audit.OUT/'parser1'/name).read_bytes() for name in audit.FILES}

    def test_all_real_complete_windows_reproduce(self):
        for stem,entry,end,count,report_sha,code_sha,_,_ in audit.WINDOWS:
            files={name:(audit.OUT/(stem+'1')/name).read_bytes() for name in audit.FILES}
            audit.pinned(audit.OUT/(stem+'1')/'extraction.private.json',report_sha)
            audit.pinned(audit.OUT/(stem+'1')/'code.bin',code_sha)
            report,lines=audit.window(files,entry,end,count)
            self.assertEqual((report['codeBytes'],len(lines)),(end-entry,count))

    def test_neighbor_extent_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'extent or instruction count'):
            audit.window(self.files,0x149f0c1f0,0x149f0e000,1778)

    def test_ambiguous_containing_pdata_is_rejected(self):
        files=dict(self.files)
        report=json.loads(files['extraction.private.json'])
        report['exceptionDirectory']['diagnostic']['containing']*=2
        files['extraction.private.json']=json.dumps(report).encode('utf8')
        with self.assertRaisesRegex(ValueError,'unique exact pdata'):
            audit.window(files,0x149f0c1f0,0x149f0dfea,1778)

    def test_raw_child_tail_byte_drift_is_rejected(self):
        files=dict(self.files)
        code=bytearray(files['code.bin'])
        code[0x149f0c238-0x149f0c1f0]^=1
        files['code.bin']=bytes(code)
        with self.assertRaisesRegex(ValueError,'decode bytes or order'):
            audit.window(files,0x149f0c1f0,0x149f0dfea,1778)

    def test_wrong_original_aot_slot_is_rejected(self):
        files=dict(self.files)
        files['slot.bin']=struct.pack('<Q',0x14f6f13c0)
        with self.assertRaisesRegex(ValueError,'AOT QWORD'):
            audit.window(files,0x149f0c1f0,0x149f0dfea,1778)

    def test_reordered_original_instructions_are_rejected(self):
        files=dict(self.files)
        lines=json.loads(files['code.asm.private.json'])
        lines[0],lines[1]=lines[1],lines[0]
        files['code.asm.private.json']=json.dumps(lines).encode('utf8')
        with self.assertRaisesRegex(ValueError,'decode bytes or order'):
            audit.window(files,0x149f0c1f0,0x149f0dfea,1778)


if __name__=='__main__':
    unittest.main()
