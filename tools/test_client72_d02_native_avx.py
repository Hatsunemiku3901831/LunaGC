"""Pure declaration, artifact and synthetic-report tests; never launches a fixture."""
import copy
import hashlib
from pathlib import Path
import tempfile
import unittest
from tools import client72_d02_native_avx as m

def report():
    initial = m.block_initial()
    after = bytearray(initial)
    after[128:160] = bytes(range(1, 33))
    after[224:256] = bytes(range(1, 17))*2
    return {'mode':'capability','exit':0,'imageBase':'10000000','probeRva':4096,'xcrRva':8192,'witnessRva':12288,
            'cpuidCount':2,'cpuid0':[1,2,3,4],'cpuid1':[0,0,0x1c000000,0], 'xgetbvCount':1,'xcr0':'0000000000000007',
            'probeCount':1,'witnessCount':0,'normalReturnObserved':True,'postSnapshotReached':True,'fault':{'seen':False},
            'blocksBefore':initial.hex(),'blocksAfter':bytes(after).hex(),
            'probeBytesBefore':m.PROBE.hex(),'probeBytesAfter':m.PROBE.hex(),'xcrBytesBefore':m.XCR.hex(),'xcrBytesAfter':m.XCR.hex(),
            'witnessBytesBefore':m.UD2.hex(),'witnessBytesAfter':m.UD2.hex()}

RVAS = {'probe':4096,'xcr':8192,'witness':12288}

class PureTests(unittest.TestCase):
    def test_predicate(self):
        self.assertEqual(m.host_plan(0, 0x1c000000, 7), (False, False))
        for bit in (26,27,28):
            self.assertEqual(m.host_plan(1, 0x1c000000 & ~(1 << bit), 7), (False, False))
        for xcr in (0, 2, 4):
            self.assertEqual(m.host_plan(1, 0x1c000000, xcr), (True, False))
        self.assertEqual(m.host_plan(1, 0x1c000000, 6), (True, True))

    def test_synthetic_report_mutations(self):
        original = report()
        m.validate_report(original, 0, RVAS, 'capability')
        for offset in (0,32,64,96,128,160,192,224,256):
            changed = copy.deepcopy(original)
            data = bytearray.fromhex(changed['blocksAfter']); data[offset] ^= 1; changed['blocksAfter'] = data.hex()
            with self.subTest(offset=offset), self.assertRaises(ValueError): m.validate_report(changed, 0, RVAS, 'capability')
        for field, bad in [('normalReturnObserved',False),('postSnapshotReached',False),('probeCount',0),('probeRva',4097),('exit',31),('probeBytesAfter','00'*23)]:
            changed = copy.deepcopy(original); changed[field] = bad
            with self.subTest(field=field), self.assertRaises(ValueError): m.validate_report(changed, 0, RVAS, 'capability')

    def test_synthetic_ud2(self):
        r = report(); r.update(mode='ud2',exit=30,cpuidCount=0,xgetbvCount=0,probeCount=0,witnessCount=1,normalReturnObserved=False,postSnapshotReached=False,blocksAfter=m.block_initial().hex())
        r['fault'] = {'seen':True,'phase':3,'code':0xc000001d,'address':hex(0x10003000),'rip':hex(0x10003000),'rva':0x3000}
        m.validate_report(r,30,RVAS,'ud2')
        for key, value in [('code',0),('rip','10003001'),('phase',2),('rva',0x3001)]:
            changed = copy.deepcopy(r); changed['fault'][key] = value
            with self.assertRaises(ValueError): m.validate_report(changed,30,RVAS,'ud2')

    def test_sources_and_output_conflict(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); p = root/'fixture.bin'; p.write_bytes(m.PROBE)
            pin = hashlib.sha256(m.PROBE).hexdigest(); m.verify_pin(p,pin)
            p.write_bytes(m.PROBE[:8]+b'\x00'+m.PROBE[9:])
            with self.assertRaises(ValueError): m.verify_pin(p,pin)
            output = root/'run'; m.reserve_output(output)
            with self.assertRaises(FileExistsError): m.reserve_output(output)

    def test_pe_mapping_rejects_bad_offset_and_truncation(self):
        sections = [('.text',4096,23,16,23,0x60000020)]
        b = bytes(16)+m.PROBE
        self.assertEqual(m.mapped_bytes(b, sections,4096,23)[0],m.PROBE)
        with self.assertRaises(ValueError): m.mapped_bytes(b,sections,4097,23)
        with self.assertRaises(ValueError): m.mapped_bytes(b[:-1],sections,4096,23)
        with self.assertRaises(ValueError): m.mapped_bytes(b,[('.text',4096,23,16,23,0xe0000020)],4096,23)

if __name__ == '__main__': unittest.main()
