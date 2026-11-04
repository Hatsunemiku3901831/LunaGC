import struct
import unittest
from unittest.mock import patch
from tools import extract_client72_map_aot as aot
from tools.test_client72_structures import FakePE


class MapAotTest(unittest.TestCase):
    def setUp(self):
        self.binary=bytearray(0x1000);self.metadata=bytearray(0x300)
        for off,value in ((0x170,0x6347382B),(0x24,12^0x323BAE70),(0xEC,0x20^0x3CF0720C),
                          (0x110,(16-0xA3D9CC8B)&0xffffffff),(0x364,(1-0x9EA91E3B)&0xffffffff)):
            struct.pack_into('<I',self.binary,off,value)
        struct.pack_into('<Q',self.binary,0x330,0x1800);struct.pack_into('<Q',self.binary,0x800,0x1900)
        struct.pack_into('<iii',self.metadata,0x210,100,314,-1)
        struct.pack_into('<ii',self.metadata,0x230,0,0)
        self.rows=[dict(index=17961,name='Map',methods=[dict(index=100,name='FNNADLCNGFO')])]

    def recover(self):
        with patch.object(aot,'PE',FakePE),patch.object(aot,'HEADER_VA',0x1000),patch.object(aot,'CODE_REGISTRATION',0x1300),patch.object(aot,'TypeResolver') as resolver:
            resolver.return_value.arguments={314:[0,1]}
            resolver.return_value.resolve_index.side_effect=lambda i:dict(kind=['string','int32'][i])
            return aot.recover(self.binary,self.metadata,b'',self.rows)

    def test_aligned_14_byte_rows_and_12_byte_specs(self):
        result=self.recover();self.assertEqual(1,result['tableRows']);self.assertEqual(1,result['methodSpecCapacity'])
        b=result['bindings'][0];self.assertEqual((100,314,'0x1900'),(b['methodDefinition'],b['classInstance'],b['address']))

    def test_bad_function_and_spec_indices_are_rejected(self):
        for pi,si in ((-1,0),(1,0),(0,-1),(0,1)):
            struct.pack_into('<ii',self.metadata,0x230,pi,si)
            with self.subTest(pi=pi,si=si),self.assertRaises(ValueError):self.recover()

    def test_invalid_definition_instance_or_method_args_are_rejected(self):
        for values in ((999,314,-1),(100,-2,-1),(100,314,0)):
            struct.pack_into('<iii',self.metadata,0x210,*values)
            with self.subTest(values=values),self.assertRaises(ValueError):self.recover()

    def test_bad_alignment_and_unbacked_code_are_rejected(self):
        struct.pack_into('<I',self.binary,0x110,(17-0xA3D9CC8B)&0xffffffff)
        with self.assertRaises(ValueError):self.recover()
        struct.pack_into('<I',self.binary,0x110,(16-0xA3D9CC8B)&0xffffffff)
        struct.pack_into('<Q',self.binary,0x800,0x3000)
        with self.assertRaises(ValueError):self.recover()
