"""Source-bound capture projection, exact native paths and negative cases."""
import copy
import json
import os
from pathlib import Path
import struct
import unittest

from tools import replay72_avatar_method_cache as card


class CaptureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.capture=json.loads((card.ROOT/card.CAPTURE).read_bytes())
        cls.monitor=json.loads((card.ROOT/card.MONITOR).read_bytes())

    def test_exact_capture_source_and_monitor_hashes(self):
        for path,expected in card.PINS.items():
            self.assertEqual(card.probe.sha((card.ROOT/path).read_bytes()),expected,path)
        memory,audit=card.audit_capture(self.capture,self.monitor)
        self.assertEqual(audit['rawReadCount'],141)
        self.assertEqual(audit['rawBytes'],3048)
        self.assertEqual(audit['canonicalType'],dict(data=20060,kind=17,byRef=False))
        self.assertEqual(self.capture['canonicalContext']['pointer']-self.capture['mainModule']['base'],
                         int(self.capture['staticContextSource']['registeredInstanceRVA'],16))
        self.assertTrue(audit['collectorProjectionByteEquivalent'])
        self.assertGreater(len(memory.spans),1)

    def test_source_identity_completion_and_claim_drift_rejected(self):
        changes=(('sourceSHA256','0'*64),('sourceObservationSHA256','0'*64),('pid',0),
                 ('endIdentityVerified',False),('naturalLookupObserved',True),('atomicSnapshot',True),
                 ('status','partial'))
        for key,value in changes:
            with self.subTest(key=key):
                capture=copy.deepcopy(self.capture)
                capture[key]=value
                with self.assertRaises(ValueError):card.audit_capture(capture,self.monitor)

    def test_creation_module_and_monitor_input_drift_rejected(self):
        for kind in ('creation','module','files','elapsed'):
            capture,monitor=copy.deepcopy(self.capture),copy.deepcopy(self.monitor)
            if kind=='creation':capture['creation']['filetime']+=1
            elif kind=='module':capture['mainModule']['base']+=4096
            elif kind=='files':capture['inputChecks']['files']['binary']['sha256']='0'*64
            else:monitor['elapsedSeconds']=89
            with self.subTest(kind=kind),self.assertRaises(ValueError):card.audit_capture(capture,monitor)

    def test_read_budget_and_repeat_inventory_drift_rejected(self):
        for kind in ('count','budget','repeat'):
            capture=copy.deepcopy(self.capture)
            if kind=='count':capture['records'].pop()
            elif kind=='budget':capture['budget']['requestedBytes']+=1
            else:capture['inputConsistency']['uniqueSpans']-=1
            with self.subTest(kind=kind),self.assertRaises(ValueError):card.audit_capture(capture,self.monitor)

    def test_wrong_candidate_pointer_or_probe_sequence_rejected(self):
        for kind in ('pointer','path','canonical'):
            capture=copy.deepcopy(self.capture)
            if kind=='pointer':capture['methods'][0]['pointer']+=8
            elif kind=='path':capture['methods'][0]['path'][0]['slot']+=1
            else:capture['canonicalContext']['pointer'] &= 0xffffffff
            with self.subTest(kind=kind),self.assertRaises(ValueError):card.audit_capture(capture,self.monitor)

    def test_failed_short_or_contradictory_overlapping_span_rejected(self):
        for kind in ('failed','short','conflict'):
            records=copy.deepcopy(self.capture['records'])
            if kind=='failed':records[0]['error']='owned failure'
            elif kind=='short':records[0]['bytesRead']=7
            else:
                row=records[88]
                raw=bytearray.fromhex(row['bytesHex'])
                raw[0]^=1
                row['bytesHex']=raw.hex()
            with self.subTest(kind=kind),self.assertRaises(ValueError):card.SourceMemory(records)

    def test_source_memory_keeps_holes_and_partial_page_unknown(self):
        records=[dict(address=0x100008000,requested=4,bytesRead=4,bytesHex='01000000',error=None),
                 dict(address=0x100008008,requested=4,bytesRead=4,bytesHex='02000000',error=None)]
        memory=card.SourceMemory(records)
        self.assertEqual(memory.read(0x100008000,4),(b'\x01\0\0\0',None))
        self.assertEqual(memory.read(0x100008004,4),(b'','unprovided-owned-span'))
        self.assertEqual(memory.read(0x100008000,12),(b'','unprovided-owned-span'))
        self.assertEqual(len(memory.spans),2)

    def test_only_six_root_cell_storage_addresses_aliased_pointer_values_preserved(self):
        memory=card.SourceMemory(self.capture['records'])
        aliases=card.add_aliases(memory,self.capture)
        self.assertEqual(len(aliases),6)
        module=self.capture['mainModule']['base']
        for row in aliases:
            rva=int(row['rva'],16)
            self.assertEqual(int(row['capturedAddress'],16),module+rva)
            self.assertEqual(int(row['nativeCell'],16),0x140000000+rva)
            self.assertEqual(memory.read(module+rva,8),memory.read(0x140000000+rva,8))
            self.assertTrue(row['pointerValueUnchanged'])
        pointer=self.capture['canonicalContext']['pointer']
        self.assertEqual(memory.read(pointer,4),(struct.pack('<I',1),None))
        self.assertNotEqual(pointer,pointer&0xffffffff)

    def test_conflicting_root_alias_rejected(self):
        memory=card.SourceMemory(self.capture['records'])
        memory.add('owned-bad-alias',0x140000000+card.probe.ROOTS['method'][0],bytes(8))
        with self.assertRaisesRegex(ValueError,'Conflicting'):card.add_aliases(memory,self.capture)

    def test_no_bytes_beyond_last_methodinfo56_supplied(self):
        memory=card.SourceMemory(self.capture['records'])
        last=self.capture['methods'][-1]
        raw,error=memory.read(last['pointer'],56)
        self.assertIsNone(error)
        self.assertEqual(raw.hex(),last['methodInfoHex'])
        self.assertEqual(memory.read(last['pointer']+56,1),(b'','unprovided-owned-span'))


class ReplayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=os.environ.get('CLIENT72_AVATAR_METHOD_CACHE_REPLAY_REPORT',str(card.RAW/'first.json'))
        cls.report=json.loads(Path(path).read_bytes())
        cls.capture=json.loads((card.ROOT/card.CAPTURE).read_bytes())

    def test_current_source_and_old_frozen_sources_match(self):
        self.assertEqual(self.report['sourceSHA256'],card.probe.sha(Path(card.__file__).read_bytes()))
        self.assertEqual(self.report['captureSHA256'],card.CAPTURE_SHA)
        for path,expected in self.report['sourcePins'].items():
            self.assertEqual(card.probe.sha((card.ROOT/path).read_bytes()),expected,path)

    def test_original_genericinst_canonical_and_probe_read_sequence(self):
        replay=self.report['isolated']['genericInst']
        self.assertEqual(replay['entry'],'0x14051fba3')
        self.assertEqual(replay['stop'],'0x14051fd12')
        self.assertTrue(replay['occupiedMatch'])
        self.assertEqual(replay['returnedCanonical'],hex(self.capture['canonicalContext']['pointer']))
        self.assertEqual(replay['probeReadSequence'],[hex(x['block']+8) for x in self.capture['canonicalContext']['path']])

    def test_eight_original_methods_exact_key_value_and_probe_sequence(self):
        rows=self.report['isolated']['methods']
        self.assertEqual([x['index'] for x in rows],list(range(10287,10295)))
        for replay,capture in zip(rows,self.capture['methods']):
            self.assertEqual(replay['stop'],'0x1404efb1c')
            self.assertEqual(replay['regs']['rsi'],hex(capture['pointer']))
            self.assertEqual(replay['probeReadSequence'],[hex(x['block']+8) for x in capture['path']])
            self.assertEqual(replay['entryReadSequence'],[hex(x['entry']) for x in capture['path']])
            self.assertEqual(replay['keyIndexReadSequence'],[hex(x['descriptor']) for x in capture['path']])
            self.assertEqual(replay['valueReadSequence'],[hex(capture['path'][-1]['entry']+8)])
        self.assertEqual([len(x['probeReadSequence']) for x in rows],[2,2,1,1,1,2,2,2])

    def test_every_native_captured_read_has_exact_raw_first_pass_byte_source(self):
        replay=self.report['isolated']
        rows=[replay['genericInst'],*replay['methods']]
        raw=self.capture['records'][:88]
        for native in rows:
            for read in native['externalReads']:
                if read['inputKind']=='owned-fixed-query':
                    self.assertFalse(read['captureReadOrdinals'])
                    continue
                address=int(read['capturedAddress'],16)
                self.assertTrue(read['captureReadOrdinals'])
                for ordinal in read['captureReadOrdinals']:
                    source=raw[ordinal]
                    self.assertLessEqual(source['address'],address)
                    self.assertLessEqual(address+read['width'],source['address']+source['requested'])
                    data=bytes.fromhex(source['bytesHex'])
                    offset=address-source['address']
                    self.assertEqual(data[offset:offset+read['width']].hex(),read['bytesHex'])

    def test_frozen_six_prefix_paths_and_registers_are_unchanged(self):
        reference=json.loads((card.ROOT/card.probe.PRIOR_REPORT).read_bytes())['fixedMethodProjection']['cold']
        continuous=self.report['continuous']
        self.assertTrue(continuous['frozenSixPrefixUnchanged'])
        self.assertEqual(len(continuous['native']),7)
        for new,old in zip(continuous['native'][:6],reference['native'][:6]):
            for key in ('name','failure','error','regs'):self.assertEqual(new[key],old[key])
            self.assertEqual(new['trace']['pathSHA256'],old['trace']['pathSHA256'])

    def test_continuous_original_factory_canonical_and_eight_writebacks(self):
        continuous=self.report['continuous']
        state=continuous['captureExtension']
        self.assertEqual((state['factoryEntries'],state['injected']),(2,True))
        self.assertEqual(state['canonicalReturn'],[hex(self.capture['canonicalContext']['pointer'])])
        self.assertEqual(state['methodReturns'],[dict(index=x['index'],pointer=hex(x['pointer'])) for x in self.capture['methods']])
        self.assertEqual([x['classInstance'] for x in state['methodQueries']],
                         [hex(self.capture['canonicalContext']['pointer'])]*8)
        arrays=continuous['extension']['methodPointerStorage']['allocations']
        self.assertEqual(len(arrays),1)
        self.assertEqual(arrays[0]['bytesHex'],b''.join(struct.pack('<Q',x['pointer']) for x in self.capture['methods']).hex())
        self.assertEqual(arrays[0]['tail'],'0xfedcba9876543210')
        self.assertFalse(state['capturedWriteAttempts'])
        self.assertFalse(state['newUnknownReads'])
        self.assertFalse(continuous['models']['callbackFailures'])

    def test_exact_hierarchy_storage_gap_before_allocator_model(self):
        continuous=self.report['continuous']
        native=continuous['native'][-1]
        stop=continuous['captureExtension']['allocationStop']
        self.assertEqual((native['failure'],native['error']),('Unprovided hierarchy owned storage allocation',None))
        self.assertEqual((native['regs']['rip'],native['regs']['rdx']),('0x1405091d0','0x10'))
        self.assertEqual((stop['callerReturn'],stop['width'],stop['depth']),('0x14051f2ff',16,2))
        self.assertFalse(stop['newStorageSupplied'])
        self.assertEqual(stop['ownedClass'],continuous['newClasses'][1]['pointer'])
        self.assertEqual(continuous['hierarchyWindow']['sha256'],card.HIERARCHY[2])
        self.assertFalse(continuous['extension']['windowRequests'])

    def test_native_negative_read_holes_high_pointer_and_forbidden_write(self):
        rows={x['name']:x for x in self.report['negativeControls']}
        self.assertEqual(len(rows),4)
        self.assertEqual(rows['missing-key-byte']['regs']['rip'],'0x1404eec48')
        self.assertEqual(rows['unprovided-record-tail']['regs']['rip'],'0x1404f0760')
        self.assertEqual(rows['truncated-canonical']['regs']['rip'],'0x1404eebbc')
        for name in ('missing-key-byte','unprovided-record-tail','truncated-canonical'):
            self.assertEqual(rows[name]['failure'],'unprovided-owned-span')
            self.assertIsNone(rows[name]['stop'])
        write=rows['forbidden-input-write']
        self.assertEqual(write['failure'],'unexpected-external-write')
        self.assertEqual(write['externalWrites'],[dict(address=hex(self.capture['methods'][0]['pointer']),width=8,writer='0x140527095')])

    def test_instruction_caps_no_natural_runtime_claim(self):
        report=self.report
        rows=[report['isolated']['genericInst'],*report['isolated']['methods'],*report['negativeControls']]
        for row in rows:
            self.assertEqual(row['instructionLimit'],2000000)
            self.assertLess(row['steps'],2000000)
        for row in report['continuous']['extension']['invocations']:
            self.assertEqual(row['enforcedLimit'],2000000)
            self.assertLess(row['steps'],2000000)
        for key in ('completeColdComparer','completeClassInitialization','defaultComparerWriteExecuted',
                    'completeAvatarHandler','targetProtocolReady','d02Complete'):
            self.assertFalse(report[key])
        self.assertFalse(report['isolated']['naturalLookupObserved'])
        self.assertFalse(report['isolated']['atomicSnapshot'])
        self.assertFalse(report['isolated']['externalWritesExecuted'])

    def test_minimum_next_storage_contract_has_exact_callers_and_no_live_gap(self):
        contract=self.report['nextInputContract']
        self.assertEqual((contract['minimumInput']['bytes'],contract['minimumInput']['hierarchyDepth']),(16,2))
        self.assertEqual([x['callerReturn'] for x in contract['exactBoundaries']],
                         ['0x14051f2ff','0x14051f30f','0x14051f321','0x14051f35c'])
        self.assertEqual([x['width'] for x in contract['exactBoundaries']],[16,16,16,8])
        self.assertEqual(contract['newNaturalBytesRequiredForThisGap'],0)
        self.assertFalse(contract['newLiveSamplingRequiredForThisGap'])
        self.assertFalse(contract['capturedContext']['methodInfoOwnerPointeesProvided'])


if __name__=='__main__':unittest.main()
