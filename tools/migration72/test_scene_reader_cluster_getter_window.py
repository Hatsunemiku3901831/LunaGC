import copy
import struct
from pathlib import Path
import unittest
from unittest.mock import patch
from tools.migration72 import scene_reader_cluster_getter_window as w


class Gates(unittest.TestCase):
    def test_caller_and_aot(self):
        source={'reusedCompleteProducerRaw':[{'entry':hex(w.CALLER),'instructions':[{'address':hex(w.CALLER),'bytes':('00'*353)}]}]}
        with self.assertRaises(ValueError): w.verify_caller(source,[bytes.fromhex('e81b53f8ff'),bytes.fromhex('e8bf52f8ff')])
        w.check_aot(bytes.fromhex('d0548a4201000000'),bytes.fromhex('302ce54601000000'))
        for cell,slot in [('00'*8,'302ce54601000000'),('d0548a4201000000','00'*8)]:
            with self.assertRaises(ValueError): w.check_aot(bytes.fromhex(cell),bytes.fromhex(slot))
        for pc,raw in zip(w.SITES,('e81b53f8ff','e8bf52f8ff')):
            self.assertEqual(pc+5+struct.unpack('<i',bytes.fromhex(raw)[1:])[0],w.ENTRY)

    def test_pdata_gates(self):
        row={'begin':hex(w.ENTRY),'endExclusive':hex(w.ENTRY+1),'size':1,'recordHex':'00'*12}
        diag={'exact':[row],'containing':[row]};self.assertEqual(w.check_row(diag),row)
        for d in ({'exact':[],'containing':[]},{'exact':[row,row],'containing':[row,row]},{'exact':[row],'containing':[]}):
            with self.assertRaises(ValueError):w.check_row(d)
        for size in (0,-1,1025):
            bad=copy.deepcopy(row);bad['size']=size
            with self.assertRaises(ValueError):w.check_row({'exact':[bad],'containing':[bad]})
        with self.assertRaises(ValueError):w.base.offset([(w.ENTRY,1,0,0)],w.ENTRY,1,True)
        with self.assertRaises(ValueError):w.base.offset([(w.ENTRY,1,0,0x20000000)],w.ENTRY,2,True)

    def test_decode_full_and_strict_engine_budget(self):
        w.decode(b'\x90',w.ENTRY)
        import capstone
        original=capstone.Cs.disasm
        calls=[]; yielded=[]
        def tracked(engine, code, address, count=0):
            calls.append(count)
            for ins in original(engine,code,address,count):
                yielded.append(ins.address)
                yield ins
        with patch.object(capstone.Cs,'disasm',tracked):
            self.assertEqual(len(w.decode(b'\x90'*256,w.ENTRY)['lines']),256)
            self.assertEqual(calls,[256]);self.assertEqual(len(yielded),256)
            calls.clear();yielded.clear()
            with self.assertRaisesRegex(ValueError,'instruction budget reached with undecoded bytes'):
                w.decode(b'\x90'*257,w.ENTRY)
            self.assertEqual(calls,[256]);self.assertEqual(len(yielded),256)

    def test_short_body_and_incomplete_decode(self):
        import io,tempfile
        with tempfile.TemporaryDirectory() as tmp:
            report={}
            with self.assertRaisesRegex(ValueError,'short body read'):
                w.read_body(io.BytesIO(b'\x90'),0,2,Path(tmp),report)
            self.assertEqual((Path(tmp)/'body.bin').read_bytes(),b'\x90')
            self.assertEqual((report['newBodies'],report['bodyBytesRead']),(1,1))
        self.assertEqual(len(w.decode(b'\x90\xc3',w.ENTRY)['lines']),2)
        with self.assertRaisesRegex(ValueError,'incomplete decode'):w.decode(b'\x0f',w.ENTRY)

    def test_pin_failure_stops_before_body(self):
        import json, tempfile
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(w, 'source_gate', side_effect=ValueError('pin mismatch')):
                with self.assertRaisesRegex(ValueError, 'pin mismatch'): w.collect(Path(tmp))
            report=json.loads((Path(tmp)/'failure.private.json').read_bytes())
            self.assertEqual((report['stage'],report['newBodies'],report['bodyBytesRead']),('source-gate',0,0))
            self.assertFalse((Path(tmp)/'body.bin').exists())

    def test_metadata_ordinary_and_signatures_are_distinct(self):
        method={'index':77588,'name':'get_instance','nameIndex':'0xc0565af','registeredAddress':hex(w.ENTRY),'parameterCount':0,'flags':'0x896','virtualSlot':65535}
        records=[{'index':i,'methods':[]} for i in range(11010)]
        records[11009].update(namespace='MoleMole',name='ObjectPoolNonGenericCluster',methods=[method])
        self.assertEqual(w.verify_metadata(records,{'methods':[]})['addressAliases'],[(11009,77588)])
        with self.assertRaises(ValueError):w.verify_metadata(records,{'methods':[method]})
        bad=copy.deepcopy(records);bad[11009]['methods'][0]['flags']='0x0'
        with self.assertRaises(ValueError):w.verify_metadata(bad,{'methods':[]})

    def test_plain_unpickler_forbids_global(self):
        import io,pickle
        with self.assertRaises(pickle.UnpicklingError):w.base.PlainUnpickler(io.BytesIO(b'cos\nsystem\n.')).load()

if __name__=='__main__':unittest.main()
