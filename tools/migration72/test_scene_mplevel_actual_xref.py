import io
import pickle
import struct
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import scene_mplevel_actual_xref as subject


class SceneXrefGatesTest(unittest.TestCase):
    def test_pure_data_unpickler_rejects_classes(self):
        self.assertEqual(subject.PureData(io.BytesIO(pickle.dumps({'x': [1]}))).load(), {'x': [1]})
        with self.assertRaisesRegex(ValueError, 'global class'):
            subject.PureData(io.BytesIO(pickle.dumps(Path('x')))).load()

    def test_source_hash_or_size_drift_stops(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'pin'
            path.write_bytes(b'fixed')
            sha = subject.digest(b'fixed')
            self.assertEqual(subject.checked(path, 5, sha), b'fixed')
            with self.assertRaises(ValueError): subject.checked(path, 6, sha)
            path.write_bytes(b'other')
            with self.assertRaises(ValueError): subject.checked(path, 5, sha)

    def test_methodspec_argument_and_usage_gates(self):
        valid = (0xc0016e00, 2, subject.CELL - 16, (80455, -1, 66311), 1, 18, False, 77121)
        subject.check_usage(*valid)
        for i, changed in [(0, 93696), (1, 3), (3, (80455, -1, 66312)), (4, 2), (5, 17), (6, True), (7, 77122)]:
            args = list(valid); args[i] = changed
            with self.subTest(i=i), self.assertRaises(ValueError): subject.check_usage(*args)

    def test_exact_six_shapes_and_false_target(self):
        start = subject.CELL - 0x2000
        for prefix in (0x48, 0x4c):
            for opcode in (0x8b, 0x8d, 0x89):
                code = bytes((prefix, opcode, 0x05)) + struct.pack('<i', subject.CELL-start-7)
                found = subject.scan(code, start)
                self.assertEqual(len(found), 1)
                self.assertEqual(found[0]['site'], hex(start))
                self.assertEqual(subject.scan(code[:-1], start), [])
                self.assertEqual(subject.scan(code, start+1), [])
        code = b'\x48\x8b\x04' + b'\x00'*4
        self.assertEqual(subject.scan(code, start), [])

    def test_raw_match_without_instruction_boundary_is_not_valid(self):
        candidate = {'site':'0x101', 'bytes':'488b0500000000', 'kind':'load'}
        decoded = {'0x100':[{'address':'0x100','bytes':'90488b0500000000','ripTargets':[hex(subject.CELL)]}]}
        self.assertEqual(subject.valid_refs([candidate], decoded), [])
        decoded = {'0x100':[{'address':'0x101','bytes':candidate['bytes'],'ripTargets':[hex(subject.CELL)]}]}
        self.assertEqual(subject.valid_refs([candidate], decoded), [candidate])
        decoded['0x100'][0]['ripTargets'] = ['0x1']
        self.assertEqual(subject.valid_refs([candidate], decoded), [])

    def test_pdata_unique_overlap_and_bounds(self):
        base = 0x100000; site = base+0x105
        candidate = {'site':hex(site),'bytes':'','kind':'load'}
        raw = struct.pack('<III', 0x100, 0x110, 3)
        located = subject.locate_pdata(raw, 0x1000, base, [candidate])
        self.assertEqual(subject.budget([candidate],located)[0], None)
        duplicate = subject.locate_pdata(raw+raw, 0x1000, base, [candidate])
        self.assertEqual(subject.budget([candidate],duplicate)[0], 'pdata-ambiguous-or-missing')
        missing = subject.locate_pdata(raw, 0x1000, base, [{'site':hex(base+1)}])
        self.assertEqual(subject.budget([candidate],missing)[0], 'pdata-ambiguous-or-missing')
        huge = subject.locate_pdata(struct.pack('<III',0x100,0x100+8193,0),0x1000,base,[candidate])
        self.assertEqual(subject.budget([candidate],huge)[0], 'body-budget-exceeded')
        self.assertEqual(subject.budget([candidate]*5,[])[0], 'raw-candidate-limit')
        candidates=[{'site':hex(base+i*9000+1)} for i in range(3)]
        table=b''.join(struct.pack('<III',i*9000,i*9000+6000,0) for i in range(3))
        self.assertEqual(subject.budget(candidates,subject.locate_pdata(table,0,base,candidates))[0], 'body-budget-exceeded')

    def test_multiple_valid_caller_stops(self):
        first={'site':'0x100','bytes':'00'}; second={'site':'0x200','bytes':'01'}
        located=[{'candidate':first,'containing':[{'begin':'0x100'}]}, {'candidate':second,'containing':[{'begin':'0x200'}]}]
        self.assertEqual(subject.outcome([],located),('zero-valid',None))
        self.assertEqual(subject.outcome([first],located),('unique-caller','0x100'))
        self.assertEqual(subject.outcome([first,second],located),('multiple-valid-callers',None))

    def test_aot_pointer_drift(self):
        subject.check_aot((0x1234).to_bytes(8, 'little'), '0x1234')
        for raw in (b'', (0x1235).to_bytes(8, 'little')):
            with self.assertRaisesRegex(ValueError, 'AOT pointer mismatch'):
                subject.check_aot(raw, '0x1234')

    def test_contiguous_and_instruction_budget(self):
        self.assertEqual(len(subject.decode(b'\x90\xc3',0x1000)),2)
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            subject.decode(b'\x90\x0f',0x1000)
        with self.assertRaisesRegex(ValueError, 'instruction budget'):
            subject.decode(b'\x90'*2049,0x1000)


if __name__ == '__main__': unittest.main()
