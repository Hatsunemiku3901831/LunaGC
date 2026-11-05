import struct
import unittest
from tools.extract_client72_literals import prefix, decrypt, Literals72
from tools.extract_client72_enums import defaults, symbols


class MetadataValueTests(unittest.TestCase):
    def test_independent_target_cipher_vectors(self):
        vectors=[(46063,0x0de343e7,0x14483b,'bf53d08676d5c456ef32df7798fca89a10f6383cbc02722b22f6',b'[Login] OnPlayerLoginRsp: '),
                 (46129,0xd9ed05c7,0x144f53,'8b22e79d2b70cb30c3c51f5168b9a5e3318d5a3a9ad8989a5656899dee',b'[Login] OnGetPlayerTokenRsp: '),
                 (46140,0x3a9b961b,0x14504e,'ed9b13c8ecd9680c275757b31e3f5db1481c85',b'OnGetPlayerTokenRsp')]
        for index,encoded,offset,cipher,plain in vectors:
            self.assertEqual(prefix(encoded,index),offset)
            self.assertEqual(decrypt(bytes.fromhex(cipher),index),plain)

    def test_index_and_prefix_bounds(self):
        for index in (-1,True,1<<29):
            with self.assertRaises(ValueError):decrypt(b'',index)
        table=object.__new__(Literals72);table.metadata=b'\0'*8;table.prefix_base=0;table.data_base=8
        with self.assertRaises(ValueError):table.payload(0)
        with self.assertRaises(ValueError):table.payload(1)

    def test_usage_source_and_destination_are_separate(self):
        table=object.__new__(Literals72);table.global_group=1
        table.group=lambda _: [dict(index=7,slot=3,cell='0x118')]
        table.decode=lambda _: 'marker'
        table.slot_count=9
        self.assertEqual(table.selected((7,))['literals'][0]['slot'],3)
        with self.assertRaises(ValueError):table.selected((8,))
        table.group=lambda _: [dict(index=7,slot=3),dict(index=8,slot=3)]
        with self.assertRaises(ValueError):table.selected((7,8))

    def test_default_table_rejects_duplicates_and_bad_refs(self):
        valid=struct.pack('<iii',70,0,2)
        self.assertEqual(defaults(valid,0,12,3)[2]['typeIndex'],70)
        for raw,size in [(valid,11),(valid,24),(valid*2,24),(struct.pack('<iii',70,-2,2),12),(struct.pack('<iii',70,0,3),12)]:
            with self.assertRaises(ValueError):defaults(raw,0,size,3)

    def test_enum_signed_values_aliases_and_missing_default(self):
        row=dict(index=1,name='E',namespace='',fields=[dict(name='value__',type={'kind':'int32'}),
            *[dict(name=name,index=i,offsetKind='literal',type={'typeDefinition':1}) for i,name in enumerate(('A','B','C'))]])
        raw=struct.pack('<iii',-1,7,7);table={i:dict(dataIndex=i*4,typeIndex=70,defaultRow=i) for i in range(3)}
        values=symbols(row,table,raw,0)['values']
        self.assertEqual([v['value'] for v in values],[-1,7,7])
        table.pop(2)
        with self.assertRaises(ValueError):symbols(row,table,raw,0)
        row['fields'][0]['type']['kind']='float'
        with self.assertRaises(ValueError):symbols(row,table,raw,0)


if __name__=='__main__':unittest.main()
