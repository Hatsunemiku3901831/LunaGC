"""Static-only source/row decoding checks; no native emulator imported or run."""
import json
import os
from pathlib import Path
import struct
import unittest

from tools import probe72_avatar_class80_storage as card


class RowTests(unittest.TestCase):
    def test_two_exact_records_and_signed_offsets(self):
        table=16
        raw=bytearray(card.static.BODY+table+12)
        struct.pack_into('<ihih',raw,card.static.BODY+table,444353,-32768,684513,32767)
        rows=card.offsets_rows(raw,table,(-0xe42a)&0xffff,2)
        self.assertEqual([x['index'] for x in rows],[0,1])
        self.assertEqual([x['typeRef'] for x in rows],[444353,684513])
        self.assertEqual([x['signedOffset'] for x in rows],[-32768,32767])
        self.assertEqual([x['storedOffsetDword'] for x in rows],['0xffff8000','0x7fff'])
        self.assertTrue(all(x['bytes']==6 for x in rows))

    def test_row_index_wrap_is16bit_and_does_not_assume12_contiguous_bytes(self):
        table=0
        raw=bytearray(card.static.BODY+65536*6)
        struct.pack_into('<ih',raw,card.static.BODY+65535*6,1,-1)
        struct.pack_into('<ih',raw,card.static.BODY,2,6)
        rows=card.offsets_rows(raw,table,(65535-0xe42a)&0xffff,2)
        self.assertEqual([x['index'] for x in rows],[65535,0])
        self.assertEqual([x['typeRef'] for x in rows],[1,2])
        self.assertEqual(rows[0]['storedOffsetDword'],'0xffffffff')

    def test_null_reference_is_explicit_source_branch_and_invalid_negatives_rejected(self):
        raw=bytearray(card.static.BODY+6)
        struct.pack_into('<ih',raw,card.static.BODY,-1,0)
        row=card.offsets_rows(raw,0,(-0xe42a)&0xffff,1)[0]
        self.assertEqual(row['typeRef'],-1)
        resolver=object.__new__(card.FocusedTypes)
        self.assertEqual(resolver.resolve(-1)['status'],'source-null-branch')
        self.assertFalse(resolver.resolve(-1)['inflationOrClassReturnProved'])
        struct.pack_into('<i',raw,card.static.BODY,-2)
        with self.assertRaisesRegex(ValueError,'negative'):card.offsets_rows(raw,0,(-0xe42a)&0xffff,1)

    def test_range_count_and_short_record_are_rejected(self):
        for count in (-1,3,True):
            with self.subTest(count=count),self.assertRaises(ValueError):
                card.offsets_rows(bytes(1024),0,(-0xe42a)&0xffff,count)
        with self.assertRaises(ValueError):card.offsets_rows(bytes(card.static.BODY+5),0,(-0xe42a)&0xffff,1)
        with self.assertRaises(ValueError):card.offsets_rows(bytes(1024),-1024,(-0xe42a)&0xffff,1)

    def test_signed_table_decode_preserves_native_sign_extension(self):
        self.assertEqual(card.signed32(0x7fffffff),2147483647)
        self.assertEqual(card.signed32(0x80000000),-2147483648)
        self.assertEqual(card.signed32(0xffffffff),-1)


class StaticReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=os.environ.get('CLIENT72_AVATAR_CLASS80_STATIC_REPORT',str(card.RAW/'static-first.private.json'))
        cls.report=json.loads(Path(path).read_bytes())

    def test_static_source_pins_parent_and_contract_match(self):
        self.assertEqual(self.report['sourceSHA256'],card.sha(Path(card.__file__).read_bytes()))
        for path,expected in self.report['sourcePins'].items():
            self.assertEqual(card.sha((card.ROOT/path).read_bytes()),expected,path)
        parent=json.loads((card.ROOT/card.PARENT).read_bytes())
        self.assertEqual(json.loads((card.ROOT/card.CONTRACT).read_bytes()),parent['nextInputContract'])

    def test_exact_two_source_rows_have_expected_indices_refs_and_offsets(self):
        rows=self.report['rows']
        self.assertEqual([x['index'] for x in rows],[445,446])
        self.assertEqual([x['fileOffset'] for x in rows],['0x8d08e6','0x8d08ec'])
        self.assertEqual([x['bytesHex'] for x in rows],['c1c706000400','e1710a000600'])
        self.assertEqual([x['typeRef'] for x in rows],[444353,684513])
        self.assertEqual([x['signedOffset'] for x in rows],[4,6])
        self.assertEqual(self.report['rowTableBodyOffset'],'0x8cfc68')
        self.assertEqual(self.report['encodedStart'],7571)

    def test_first_row_ordinary1169_and_second_generic1249_var362(self):
        first,second=[x['type'] for x in self.report['rows']]
        self.assertEqual((first['kind'],first['definition']['index']),(18,1169))
        self.assertEqual(first['definition']['name'],'IEqualityComparer')
        self.assertEqual(first['definition']['namespace'],'System.Collections')
        self.assertEqual((second['kind'],second['genericIndex'],second['instanceIndex']),(21,405409,54600))
        self.assertEqual(second['definition']['index'],1249)
        self.assertEqual(second['definition']['name'],'IEqualityComparer`1')
        parameter=second['arguments'][0]
        self.assertEqual((parameter['kind'],parameter['parameterIndex'],parameter['argumentPosition']),(19,362,0))
        self.assertEqual(parameter['encodedArgumentPosition'],34416)
        self.assertEqual(parameter['parameterSHA256'],'6d1df52b90dc9d2aa7c4298132a074cc0c20e357f93a0aa63a6596eb3b938596')
        self.assertFalse(first['inflationOrClassReturnProved'])
        self.assertFalse(second['inflationOrClassReturnProved'])

    def test_only_three_selected_descriptors_and12_source_bytes(self):
        self.assertEqual(self.report['selectedDescriptorCount'],3)
        candidates=self.report['controlledInputCandidates']
        self.assertEqual(sum(x['bytes'] for x in candidates),12)
        for candidate,row in zip(candidates,self.report['rows']):
            self.assertEqual(candidate['bytesHex'],row['bytesHex'])
            self.assertEqual(candidate['sourceFileOffset'],row['fileOffset'])
            self.assertEqual(candidate['sha256'],row['sha256'])
            self.assertEqual(int(candidate['address'],16),int(self.report['modelMetadataBody'],16)+int(row['metadataBodyOffset'],16))

    def test_no_native_cpu_or_five_stock_gate_claim(self):
        report=self.report
        self.assertEqual(report['staticInputSHA256'],card.static.HASHES)
        self.assertEqual(len(report['staticInputSHA256']),3)
        self.assertEqual(report['newModelCalls'],0)
        for key in ('fullFiveStockGateRun','nativeCpuRun','hierarchyThirdAccepted','modelAddressIsNaturalEpochClaimed',
                    'completeClass80Rows','completeClassInitialization','defaultComparerWriteExecuted',
                    'completeAvatarHandler','targetProtocolReady','d02Complete'):
            self.assertFalse(report[key],key)
        self.assertFalse(report['producer']['newNativeWindow'])
        self.assertFalse(report['parameterConsumer']['newNativeWindow'])


if __name__=='__main__':unittest.main()
