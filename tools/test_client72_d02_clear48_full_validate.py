"""Synthetic-only mutations; these fixtures are not native trace evidence."""
import hashlib
import copy
import json
import os
import struct
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path
from contextlib import nullcontext

from tools import client72_d02_clear48_full_validate as v
from tools.test_client72_d02_trace_validate import fixture as old_synthetic_fixture, add_tail, synthetic_create


class SyntheticValidatorTests(unittest.TestCase):
    def test_failed_non_pi_cleanup_bool_rejected(self):
        records = {'continue': [{'stopEventSeq': 10, 'seq': 11}], 'cleanup': [
            {'api': 'WaitForSingleObject', 'seq': 12, 'handleOrigin': 'CreateProcess.PI.hProcess',
             'result': 0, 'lastErrorIfFailed': None},
            {'api': 'GetExitCodeProcess', 'seq': 13, 'BOOL': True, 'lastErrorIfFalse': None, 'exitCode': 0},
            {'api': 'CloseHandle', 'seq': 14, 'handleOrigin': 'CreateProcess.PI.hThread',
             'BOOL': True, 'lastErrorIfFalse': None},
            {'api': 'CloseHandle', 'seq': 15, 'handleOrigin': 'CreateProcess.PI.hProcess',
             'BOOL': True, 'lastErrorIfFalse': None},
            {'api': 'CloseHandle', 'seq': 1, 'handleOrigin': 'stdout', 'BOOL': True, 'lastErrorIfFalse': None},
        ]}
        exit_event = {'seq': 10, 'exitCode': 0}
        v.check_normal_cleanup(records, exit_event)
        for value, error in ((False, 5), (1, None), (True, 5)):
            broken = copy.deepcopy(records)
            broken['cleanup'][-1].update(BOOL=value, lastErrorIfFalse=error)
            with self.subTest(value=value, error=error), self.assertRaises(ValueError):
                v.check_normal_cleanup(broken, exit_event)

    def test_tf_only_and_reserved_bit_direction(self):
        v.check_tf('00000202', '00000302', '00000300', True)
        for values in [('00000200', '00000300', '00000302', True),
                       ('00000202', '00000300', '00000300', True),
                       ('00000202', '00000302', '00000342', True)]:
            with self.subTest(values=values), self.assertRaises(ValueError):
                v.check_tf(*values)

    def test_actual_flags_select_branch_and_external_target(self):
        row = {'flow': {'kind': 'conditional', 'condition': 'ZF=0',
                        'takenOriginalPC': '0000000141fdf9a0',
                        'fallthroughOriginalPC': '0000000141fdfab2'}}
        base = 0x00007fff00000000
        self.assertEqual(base + 0x1fdf9a0, v.next_pc(row, '00000002', {}, base, 0))
        self.assertEqual(base + 0x1fdfab2, v.next_pc(row, '00000042', {}, base, 0))

    def test_operand_uses_actual_64_bit_registers(self):
        base = 0x00007fff00000000
        row = {'rva': '01fdfb49'}
        self.assertEqual(base + 0x06d190f4, v.operand_address(row, {'R10': f'{base:016x}', 'R11': '0000000000000001'}, base))
        self.assertNotEqual(base + 0x06d190f4, v.operand_address(row, {'R10': f'{base:016x}', 'R11': '0000000000000002'}, base))
        t1 = {'rva':'01fdfa9c','dataOperands':[{'originalTargetRva':'056afde0'}]}
        self.assertEqual(v.operand_address(t1, {}, base), base+0x056afde0)
        other_table = {'rva':'01fdfcfd'}
        self.assertEqual(v.operand_address(other_table, {'R10':f'{base:016x}','R11':'0000000000000002'}, base), base+0x06d19140)

    def test_overlapping_store_uses_current_old_and_preserves_guards(self):
        initial = bytes(range(1, 129))
        final = bytearray(initial)
        final[48:96] = bytes(48)
        case = {'buffer': {'initialHex': initial.hex(), 'expectedFinalHex': final.hex()},
                'stores': [{'ordinal': 97, 'headOffset': 32, 'width': 32, 'expectedActualNewHex': bytes(32).hex()},
                           {'ordinal': 98, 'headOffset': 16, 'width': 32, 'expectedActualNewHex': bytes(32).hex()}]}
        h = 0x00007fff12340020
        records = [{'ordinal': 97, 'EA': f'{h+32:016x}', 'width': 32, 'oldHex': initial[64:96].hex(), 'newHex': bytes(32).hex()},
                   {'ordinal': 98, 'EA': f'{h+16:016x}', 'width': 32, 'oldHex': (initial[48:64] + bytes(16)).hex(), 'newHex': bytes(32).hex()}]
        self.assertEqual(v.check_store_sequence(case, h, records), bytes(final))
        records[1]['oldHex'] = initial[48:80].hex()
        with self.assertRaises(ValueError):
            v.check_store_sequence(case, h, records)
        records[1]['oldHex'] = (initial[48:64] + bytes(16)).hex()
        case['buffer']['expectedFinalHex'] = (bytes([0]) + final[1:]).hex()
        with self.assertRaises(ValueError):
            v.check_store_sequence(case, h, records)

    def test_catalog_coverage_mutation(self):
        sizes = [6] * 24 + [5] * 152
        source = bytes((i % 251 + 1 for i in range(sum(sizes))))
        rows, offset = [], 0
        for i, size in enumerate(sizes):
            rows.append({'ordinal': i, 'offset': offset, 'size': size,
                         'bytesHex': source[offset:offset+size].hex(),
                         'rva': f'{0x1fdf9c0+offset:08x}', 'originalPC': f'{0x141fdf9c0+offset:016x}',
                         'supported': i < 63, 'allowedCaseIds': [257, 258] if i < 63 else []})
            offset += size
        catalog = {'sourceSHA256': hashlib.sha256(source).hexdigest(), 'instructionCount': 176, 'instructions': rows}
        cases = {'cases': [{'id': case, 'instructionOrdinals': list(range(count)),
                            'expectedOriginalPCs': [hex(int(r['originalPC'], 16)) for r in rows[:count]]}
                           for case, count in ((257, 37), (258, 40))]}
        v.check_catalog(catalog, cases, source)
        rows[5]['ordinal'] = 6
        with self.assertRaises(ValueError):
            v.check_catalog(catalog, cases, source)
        rows[5]['ordinal'] = 5
        with self.assertRaises(ValueError):
            v.check_catalog(catalog, cases, source[:-1] + b'\0')

    def test_duplicate_json_key_rejected(self):
        with self.assertRaises(ValueError):
            v.unique([('schema', 1), ('schema', 2)])

    def test_epoch_source_proof_is_top_level_and_exact(self):
        keys = ['k'+str(i) for i in range(18)]
        expected = {k:i for i,k in enumerate(keys)}
        epoch = dict(expected, schema='c48-full-helper-epoch-v1')
        summary = {'sourceProof':dict(expected)}
        v.check_source_proof(epoch,summary,{'sourceProofKeys':keys},expected)
        summary['sourceProof']['extra'] = 1
        with self.assertRaises(ValueError):
            v.check_source_proof(epoch,summary,{'sourceProofKeys':keys},expected)
        with self.assertRaises(ValueError):
            v.check_source_proof(dict(epoch,schema='old'),{'sourceProof':expected},{'sourceProofKeys':keys},expected)

    def test_receipt_before_after_and_actual_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = (Path(directory)/'synthetic.bin').resolve(); path.write_bytes(b'SYNTHETIC')
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            receipt = {'schema':'c48-full-helper-root-run-v1','timedOut':False,'parentCreated':True,'childCreated':None,
                       'parentExit':0,'cleanupConfirmed':True,'error':None,'parentPid':123,
                       'parentCreationFileTime':'0000000000000001','startedUtc':'2026-11-11T00:00:00Z',
                       'endedUtc':'2026-11-11T00:00:01Z','before':{str(path):digest},'after':{str(path):digest}}
            v.check_receipt(receipt,[(path,path.read_bytes())])
            receipt['after'][str(path)]='00'*32
            with self.assertRaises(ValueError): v.check_receipt(receipt,[(path,path.read_bytes())])
            receipt['after'][str(path)]=digest
            with self.assertRaises(ValueError): v.check_receipt(receipt,[(path,b'CHANGED')])
            receipt['childCreated']=True
            with self.assertRaises(ValueError): v.check_receipt(receipt,[(path,path.read_bytes())])

    def test_synthetic_dependency_inventory_every_declared_entry(self):
        with tempfile.TemporaryDirectory() as directory:
            paths=[]
            for i in range(10):
                p=(Path(directory)/f'synthetic-{i}').resolve();p.write_bytes(f'SYNTHETIC-{i}'.encode());paths.append(p)
            def entry(p):
                raw=p.read_bytes();return {'path':str(p),'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
            entries=[entry(p) for p in paths]
            review={'schema':'c48-full-helper-dependency-review-v1','independentlyReviewed':True,'peAndCallerUnwindReviewed':True,
                    'dependencyProofComplete':True,'actualBuildExit':0,'actualCompiledSources':entries[:8],
                    'reviewedCurrentSources':[entries[0]],'source':entries[:9],
                    'buildLogs':[entries[9]],'buildTools':[entries[9]],'compilerDlls':[entries[9]],
                    'includes':[entries[9]],'libraries':[entries[9]],'searchedLibraries':[], 'artifacts':{'synthetic':entries[9]}}
            receipt={'before':{e['path']:e['sha256'] for e in entries}}
            v.check_dependency(review,receipt,[paths[8]])
            paths[9].write_bytes(b'CHANGED')
            with self.assertRaises(ValueError): v.check_dependency(review,receipt,[paths[8]])
            paths[9].write_bytes(b'SYNTHETIC-9')
            del receipt['before'][str(paths[8])]
            with self.assertRaises(ValueError): v.check_dependency(review,receipt,[paths[8]])

    def test_reserved_page_keeps_actual_nonzero_protect(self):
        base = 0x00007fff00000000
        row = {'address': f'{base:016x}', 'allowedRange': {'base': f'{base:016x}', 'bytes': 4096},
               'returnedBytes': 48, 'lastErrorIfZero': None,
               'rawMemoryBasicInformation': {'BaseAddress': f'{base:016x}', 'AllocationBase': f'{base:016x}',
                 'AllocationProtect': 1, 'RegionSize': '0000000000001000', 'State': 0x2000, 'Protect': 0x123, 'Type': 0}}
        v.check_page_query(row, base, 4096, base, 0x2000, None)
        row['rawMemoryBasicInformation']['RegionSize'] = '0000000000000fff'
        with self.assertRaises(ValueError):
            v.check_page_query(row, base, 4096, base, 0x2000, None)

    def test_thirteen_synthetic_page_queries_and_descriptor(self):
        directory = Path(__file__).resolve().parents[1]/'local/migration72/d02-resume-20261110b/a-clear48-full-helper-contract'
        if not directory.is_dir(): self.skipTest('private frozen contract unavailable')
        interface=json.loads((directory/'interface.v1.private.json').read_text())
        cases=json.loads((directory/'cases.v1.private.json').read_text())['cases']
        catalog=json.loads((directory/'catalog.v1.private.json').read_text())
        source=b''.join(bytes.fromhex(x['bytesHex']) for x in catalog['instructions'])
        arena,reserve,image,sp=0x7fff00000000,0x7ffc00000000,0x7ffe00000000,0x7ffb00010000
        known=reserve+4096; head=known+32; h=lambda x:f'{x:016x}'
        epoch={'imageBytes':h(0x5000),'childExeSHA256':'11'*32,'source904SHA256':interface['source904SHA256'],
               'catalogSHA256':interface['catalogSHA256'],'casesSHA256':interface['casesSHA256'],
               'descriptorRva':0x3000,'wrapperBeginRva':0x1000,'armRva':0x1012,'callRva':0x1013,
               'afterCallRva':0x1016,'wrapperEndRva':0x101b}
        events={1:{'rawDebugEventCode':3,'moduleBase':h(image),'moduleImageBytes':h(0x5000),'fileSHA256':'11'*32}}
        def page(purpose,address,length,allocation,state,protect,region=None):
            start,size=region or (address,length)
            return {'purpose':purpose,'stopEventSeq':10,'address':h(address),'allowedRange':{'base':h(address),'bytes':length},
                    'returnedBytes':48,'lastErrorIfZero':None,'rawMemoryBasicInformation':{'BaseAddress':h(start),
                    'AllocationBase':h(allocation),'AllocationProtect':1,'RegionSize':h(size),'State':state,
                    'Protect':protect if protect is not None else 0x123,'Type':0x20000 if state==0x1000 else 0}}
        for case in cases:
            with self.subTest(case=case['id']):
                d=bytearray(384);d[:8]=b'C48FULL1';struct.pack_into('<4I',d,8,1,384,case['id'],1);d[24:40]=bytes.fromhex('ab'*16)
                struct.pack_into('<Q2I',d,40,image,200,201)
                struct.pack_into('<6I',d,56,0x1012,0x1013,0x1016,0x1000,0x101b,904)
                struct.pack_into('<12Q',d,80,arena,0x6d20000,arena+0x1fdf9c0,reserve,65536,known,known,128,head,head+16,sp-0x8000,sp+0x8000)
                struct.pack_into('<8I',d,176,4096,65536,1,2,1,0,0,0);struct.pack_into('<Q',d,208,6)
                struct.pack_into('<4I',d,216,1,0,0,0);struct.pack_into('<4I',d,232,0,0,0x1c000000,0)
                d[248:280]=bytes.fromhex(epoch['source904SHA256']);d[280:312]=bytes.fromhex(epoch['catalogSHA256']);d[312:344]=bytes.fromhex(epoch['casesSHA256'])
                def mem(purpose,address,data,span,spanlen):
                    return {'purpose':purpose,'address':h(address),'width':len(data),'rawHex':data.hex(),'stopEventSeq':10,
                            'allowedRegion':{'base':h(span),'bytes':spanlen,'name':'synthetic'}}
                memories=[mem('descriptor',image+0x3000,d,image,0x5000),mem('wrapper',image+0x1000,bytes.fromhex(interface['wrapper']['bytesHex']),image+0x1000,27),
                          mem('source904',arena+0x1fdf9c0,source,arena+0x1fdf9c0,904),
                          mem('ownedBufferInitial',known,bytes.fromhex(case['buffer']['initialHex']),known,128),
                          mem('ownedBufferFinal',known,bytes.fromhex(case['buffer']['expectedFinalHex']),known,128)]
                pages=[]
                for i,s in enumerate(interface['arena']['queryIntervals']):
                    pages.append(page('arena'+str(i),arena+int(s['rva'],16),s['bytes'],arena,0x1000 if s['state']=='MEM_COMMIT' else 0x2000,s['protect']))
                for i,s in enumerate(interface['buffer']['queryIntervals']):
                    pages.append(page('buffer'+str(i),reserve+s['offset'],s['bytes'],reserve,0x1000 if s['state']=='MEM_COMMIT' else 0x2000,s['protect']))
                pages.append(page('stack',sp-8,40,sp-0x10000,0x1000,4,(sp-0x1000,0x2000)))
                child={'schema':'c48-full-helper-child-result-v1','caseId':case['id'],'runId':'ab'*16,'pid':200,'tid':201,
                       'hostGateValid':True,'cpuidCount':2,'xgetbvCount':1,'xcr0':h(6),'cpuidLeaf0':[1,0,0,0],
                       'cpuidLeaf1':[0,0,0x1c000000,0],'arenaBase':h(arena),'arenaReserved':h(0x6d20000),
                       'bufferReserveBase':h(reserve),'headBase':h(head),'destination':h(head+16),'helperCallCount':1,
                       'continuedToC':True,'returnedRAX':h(head+16),'initial128Hex':case['buffer']['initialHex'],
                       'final128Hex':case['buffer']['expectedFinalHex'],**{k:epoch[k] for k in ('source904SHA256','catalogSHA256','casesSHA256')}}
                records={'memory':memories,'pageQuery':pages,'get':[{'stopEventSeq':10,'control':{'Rip':h(image+0x1013),'Rsp':h(sp),'EFlags':'00000202'},
                         'integer':{'R11':h(arena+0x1fdf9c0),'Rcx':h(head+16),'Rdx':h(0),'R8':h(48)}}]}
                with patch.object(v,'check_child_api'):
                    self.assertEqual(v.check_descriptor_and_pages(case,records,events,epoch,child,'ab'*16,interface),(arena,image,head))
                    pages[1]['rawMemoryBasicInformation']['Protect']=4
                    with self.assertRaises(ValueError): v.check_descriptor_and_pages(case,records,events,epoch,child,'ab'*16,interface)

    def test_short_read_and_span_rejected(self):
        row = {'address': '00007fff00000010', 'width': 4,
               'allowedRegion': {'base': '00007fff00000010', 'bytes': 4, 'name': 'synthetic'},
               'ReadProcessMemoryBOOL': True, 'actualBytesRead': 4, 'rawHex': '01020304'}
        v.check_memory(row)
        row['actualBytesRead'] = 3
        with self.assertRaises(ValueError):
            v.check_memory(row)
        row['actualBytesRead'] = 4
        row['width'] = 5
        with self.assertRaises(ValueError):
            v.check_memory(row)

    def test_signed_r9_ea_wraps_at_64_bits(self):
        registers = {'Rcx': '00007fff00000040', 'R9': 'fffffffffffffff0'}
        self.assertEqual(v.operand_address({'rva': '01fdfd35'}, registers, 0), 0x7fff00000010)

    def test_child_api_sequence_and_result(self):
        arena, buffer = 0x7fff00000000, 0x7ffc00000000
        h = lambda x: f'{x:016x}'
        def alloc(addr, size, typ, protection, result):
            return {'api':'VirtualAlloc','address':h(addr),'size':h(size),'allocationType':typ,
                    'protect':protection,'result':h(result),'lastErrorIfNull':None}
        def protect(addr, value):
            return {'api':'VirtualProtect','address':h(addr),'size':h(4096),'newProtect':value,
                    'BOOL':True,'lastErrorIfFalse':None,'oldProtect':4}
        pages = [arena+x for x in (0x1fdf000,0x56af000,0x5eee000,0x6d19000)]
        setup = [alloc(0,0x6d20000,0x2000,1,arena)]
        setup += [alloc(p,4096,0x1000,4,p) for p in pages]
        setup += [protect(p,n) for p,n in zip(pages,(32,2,2,2))]
        setup += [{'api':'FlushInstructionCache','process':'ffffffffffffffff','address':h(arena+0x1fdf9c0),
                   'size':h(904),'BOOL':True,'lastErrorIfFalse':None},
                  alloc(0,65536,0x2000,1,buffer),alloc(buffer+4096,4096,0x1000,4,buffer+4096)]
        release = [{'api':'VirtualFree','address':h(p),'size':h(0),'freeType':0x8000,'BOOL':True,'lastErrorIfFalse':None}
                   for p in (buffer,arena)]
        child = {'arenaBase':h(arena),'bufferReserveBase':h(buffer),'setupApiRecords':setup,'releaseApiRecords':release}
        v.check_child_api(child)
        setup[5]['oldProtect'] = 2
        with self.assertRaises(ValueError):
            v.check_child_api(child)
        setup[5]['oldProtect'] = 4
        release.reverse()
        with self.assertRaises(ValueError):
            v.check_child_api(child)

    def test_synthetic_call_and_ret_stop_chain(self):
        base, image, head, sp = 0x7fff00000000, 0x7ffe00000000, 0x7ffd00000020, 0x7ffc00001000
        h = lambda x: f'{x:016x}'
        entry, after = base+0x1fdf9c0, image+0x1016
        pre_g = {'R11':h(entry),'Rax':h(head+16)}
        control = lambda rip,rsp: {'Rip':h(rip),'Rsp':h(rsp),'EFlags':'00000202'}
        gets = [{'apiOrdinal':1,'stopEventSeq':1,'seq':2,'integer':pre_g,'control':control(image+0x1013,sp)},
                {'apiOrdinal':2,'stopEventSeq':10,'seq':11,'integer':pre_g,'control':control(entry,sp-8)},
                {'apiOrdinal':3,'stopEventSeq':20,'seq':21,'integer':pre_g,'control':control(after,sp)}]
        span = {'base':h(sp-8),'bytes':8,'name':'synthetic stack slot'}
        retbytes = after.to_bytes(8,'little').hex()
        memory = [{'apiOrdinal':1,'purpose':'CALLslotOld','stopEventSeq':1,'seq':3,'address':h(sp-8),'width':8,'rawHex':'1234567890abcdef','allowedRegion':span},
                  {'apiOrdinal':2,'purpose':'instructionBytes','stopEventSeq':1,'seq':4,'address':h(image+0x1013),'rawHex':'41ffd3','allowedRegion':{'base':h(image+0x1000),'bytes':27}},
                  {'apiOrdinal':3,'purpose':'CALLslotNew','stopEventSeq':10,'seq':12,'address':h(sp-8),'width':8,'rawHex':retbytes,'allowedRegion':span},
                  {'apiOrdinal':4,'purpose':'RETslot','stopEventSeq':10,'seq':14,'address':h(sp-8),'width':8,'rawHex':retbytes,'allowedRegion':span},
                  {'apiOrdinal':5,'purpose':'instructionBytes','stopEventSeq':10,'seq':14,'address':h(entry),'rawHex':'c3','allowedRegion':{'base':h(entry),'bytes':904}}]
        sets = [{'apiOrdinal':1,'stopEventSeq':1,'seq':5,'beforeControl':gets[0]['control'],'submittedControl':{'EFlags':'00000302'}},
                {'apiOrdinal':2,'stopEventSeq':10,'seq':15,'beforeControl':gets[1]['control'],'submittedControl':{'EFlags':'00000302'}}]
        continues = [{'apiOrdinal':1,'stopEventSeq':1,'seq':6,'BOOL':True,'cleanupOnly':False},
                     {'apiOrdinal':2,'stopEventSeq':10,'seq':16,'BOOL':True,'cleanupOnly':False}]
        def tx(i, before, after_get, code, origin, ordinal, op):
            return {'txId':i,'preGetOrdinal':i,'postGetOrdinal':i+1,'preEventSeq':before['stopEventSeq'],
                    'postEventSeq':after_get['stopEventSeq'],'preGPR':before['integer'],'postGPR':after_get['integer'],
                    'preEFlags':before['control']['EFlags'],'postEFlags':after_get['control']['EFlags'],
                    'preRip':before['control']['Rip'],'postRip':after_get['control']['Rip'],'instructionBytes':code,
                    'instructionOrigin':origin,'sourceOrdinal':ordinal,'runtimeRva':None if ordinal is None else '01fdf9c0',
                    'ownedLabel':'C48FullCall' if ordinal is None else None,'ownedRva':'00001013' if ordinal is None else None,
                    'originalPC':None if ordinal is None else '0000000141fdf9c0', 'expectedNextPCs':[after_get['control']['Rip']],
                    'preflightAccepted':True,'retired':True,'status':'retired','tfSetOrdinal':i,'continueOrdinal':i,
                    'ops':[op], 'seq':13 if i == 1 else 22,'eventPID':200,'eventTID':201,'eventType':1}
        callop = {'direction':'write','EA':h(sp-8),'width':8,'allowedSpan':span,'preReadOrdinal':1,'postReadOrdinal':3,
                  'preHex':'1234567890abcdef','postHex':retbytes}
        retop = {'direction':'read','EA':h(sp-8),'width':8,'allowedSpan':span,'preReadOrdinal':4,'postReadOrdinal':None,
                 'preHex':retbytes,'postHex':None}
        txs = [tx(1,gets[0],gets[1],'41ffd3','owned_wrapper_CALL',None,callop),
               tx(2,gets[1],gets[2],'c3','original_helper',0,retop)]
        events = {10:{'rawDebugEventCode':1,'firstChance':True,'exceptionCode':0x80000004,'exceptionAddress':h(entry),'eventPID':200,'eventTID':201,'eventType':1},
                  20:{'rawDebugEventCode':1,'firstChance':True,'exceptionCode':0x80000004,'exceptionAddress':h(after),'eventPID':200,'eventTID':201,'eventType':1}}
        rows = {'transaction':txs,'get':gets,'set':sets,'continue':continues,'memory':memory}
        case = {'id':257,'instructionOrdinals':[0],'stores':[],
                'buffer':{'initialHex':bytes(128).hex(),'expectedFinalHex':bytes(128).hex()},'criticalPostAssertions':{}}
        catalog = {'instructions':[{'supported':True,'allowedCaseIds':[257],'rva':'01fdf9c0',
                                    'originalPC':'0000000141fdf9c0','bytesHex':'c3','flow':{'kind':'ret'},
                                    'dataOperands':[{'direction':'read','width':8}]}]}
        # This tiny synthetic catalog places RET at the entry only for exercising the chain.
        with patch.object(v, 'operand_address', return_value=sp-8):
            v.check_transactions(case,catalog,rows,events,base,image,0x1013,0x1016,head)
            retop['EA'] = h(sp)
            with self.assertRaises(ValueError):
                v.check_transactions(case,catalog,rows,events,base,image,0x1013,0x1016,head)

    def test_lifecycle_real_event_sources_and_borrowed_handles(self):
        data = old_synthetic_fixture(1)
        add_tail(data, [synthetic_create(202)], final_tid=202)
        rows, summary, _, _, _ = data
        records = {}
        for row in rows:
            records.setdefault(row['kind'], []).append(row)
        events = {row['seq']:row for row in records['event']}
        exit_event = next(e for e in events.values() if e['rawDebugEventCode'] == 5)
        releases = v.check_lifecycle(257, records, events, summary, records['transaction'], exit_event)
        self.assertTrue(any(r.get('tid') == 202 and r['basis'] == 'api_contract' for r in releases))
        v.check_normal_cleanup(records,exit_event)
        bad_cleanup = copy.deepcopy(records)
        next(r for r in bad_cleanup['cleanup'] if r['api']=='CloseHandle' and r['handleOrigin']=='CreateProcess.PI.hThread')['BOOL'] = False
        with self.assertRaises(ValueError): v.check_normal_cleanup(bad_cleanup,exit_event)
        bad_cleanup = copy.deepcopy(records)
        next(r for r in bad_cleanup['cleanup'] if r['api']=='GetExitCodeProcess')['exitCode'] = 90
        with self.assertRaises(ValueError): v.check_normal_cleanup(bad_cleanup,exit_event)
        bad = copy.deepcopy(records)
        bad_events = {row['seq']:row for row in bad['event']}
        created = next(e for e in bad_events.values() if e['rawDebugEventCode'] == 2)
        created['eventTID'] = summary['tid']
        with self.assertRaises(ValueError):
            v.check_lifecycle(257,bad,bad_events,summary,bad['transaction'],bad_events[exit_event['seq']])
        bad = copy.deepcopy(records)
        bad['cleanup'].append({'api':'CloseHandle','handleOrigin':'CREATE_THREAD.borrowed','handleRaw':'0000000000000301'})
        bad_events = {row['seq']:row for row in bad['event']}
        with self.assertRaises(ValueError):
            v.check_lifecycle(257,bad,bad_events,summary,bad['transaction'],bad_events[exit_event['seq']])

    def test_record_wait_identity_bool_and_context_mutations(self):
        rows, summary, _, receipt, _ = old_synthetic_fixture(1)
        for row in rows:
            if row['kind'] == 'event' and row['rawDebugEventCode'] == 1:
                row['exceptionCode'] = int(row['exceptionCode'],16)
                row['exceptionFlags'] = int(row['exceptionFlags'],16)
            if row['kind'] == 'continue': row['continueStatus'] = int(row['continueStatus'],16)
            if row['kind'] == 'memory':
                span = row['allowedRegion']
                row['allowedRegion'] = {'base':span['begin'],'bytes':int(span['endExclusive'],16)-int(span['begin'],16),'name':'synthetic'}
                if row['purpose'] == 'sourceCode': row['purpose'] = 'instructionBytes'
        schema = {'common':{'required':['kind','seq','phase','eventPID','eventTID','eventType','monotonicElapsedUs']},
                  'rawRecordKinds':['epoch','ownedChild','wait','event','continue','get','set','calibration','memory','transaction','pageQuery','cleanup'],
                  'memoryPurposeEnum':['descriptor','instructionBytes','CALLslotOld','CALLslotNew','RETslot']}
        v.check_records(rows,summary,receipt,schema)
        bad = copy.deepcopy(rows)
        next(x for x in bad if x['kind']=='wait' and x['BOOL'] is True)['eventTID'] += 1
        with self.assertRaises(ValueError): v.check_records(bad,summary,receipt,schema)
        bad = copy.deepcopy(rows)
        next(x for x in bad if x['kind']=='continue')['BOOL'] = 1
        with self.assertRaises(ValueError): v.check_records(bad,summary,receipt,schema)
        bad = copy.deepcopy(rows)
        first_set = next(x for x in bad if x['kind']=='set')
        readback = next(x for x in bad if x['kind']=='get' and x['apiOrdinal']==first_set['readbackGetOrdinal'])
        readback['integer']['Rbx'] = '0000000000000001'
        with self.assertRaises(ValueError): v.check_records(bad,summary,receipt,schema)

    def test_both_complete_synthetic_transaction_paths(self):
        # Inputs specify the frozen catalog; every context/event below is synthetic, never native evidence.
        directory = Path(__file__).resolve().parents[1]/'local/migration72/d02-resume-20261110b/a-clear48-full-helper-contract'
        if not directory.is_dir(): self.skipTest('private frozen contract unavailable')
        catalog = json.loads((directory/'catalog.v1.private.json').read_text())
        cases = json.loads((directory/'cases.v1.private.json').read_text())['cases']
        base,image,head,sp = 0x7fff00000000,0x7ffe00000000,0x7ffc00001020,0x7ffb00010000
        h = lambda x: f'{x:016x}'
        for case in cases:
            with self.subTest(case=case['id']):
                ords=case['instructionOrdinals']; after=image+0x1016
                def expr(value):
                    if value=='D': return head+16
                    if value=='Bo': return base
                    if value.startswith('Bo+'): return base+int(value[3:],16)
                    if value.startswith('H+'): return head+int(value[2:],16)
                    return int(value,16)
                contexts=[]
                for i in range(len(ords)+2):
                    regs={'Rax':h(head+16),'Rcx':h(head+32),'Rdx':h(16),'R8':h(32),'R9':h(32),
                          'R10':h(base),'R11':h(base+(0x1fdfd35 if case['id']==257 else 0x1fdfb94))}
                    if i==0: regs.update(R11=h(base+0x1fdf9c0),Rcx=h(head+16),Rdx=h(0),R8=h(48))
                    if i==1: regs['R11']=h(base+0x1fdf9c0); regs['Rcx']=h(head+16)
                    if 1<i<=len(ords)+1:
                        for reg,value in case['criticalPostAssertions'].get(str(ords[i-2]),{}).items(): regs[reg]=h(expr(value))
                    if 1<=i<=len(ords):
                        current=catalog['instructions'][ords[i-1]]
                        if int(current['rva'],16) in (0x1fdfb49,0x1fdfcfd): regs['R11']=h(1 if case['id']==258 else 2)
                        rip=base+int(current['rva'],16)
                        next_addr=base+int(catalog['instructions'][ords[i]]['rva'],16) if i<len(ords) else after
                        flags=next((f|0x100 for f in (2,3,0x42,0x43) if v.next_pc(current,f'{f|0x100:08x}',regs,base,after)==next_addr),None)
                        self.assertIsNotNone(flags)
                    else:
                        rip=image+0x1013 if i==0 else after; flags=2 if i==0 else 0x102
                    contexts.append({'Rip':h(rip),'Rsp':h(sp if i in (0,len(ords)+1) else sp-8),'EFlags':f'{flags:08x}','g':regs})
                rows={'transaction':[],'get':[],'set':[],'continue':[],'memory':[]}; events={}; buffer=bytearray.fromhex(case['buffer']['initialHex'])
                def memory(purpose,stop,seq,address,data,span_base,span_bytes):
                    row={'apiOrdinal':len(rows['memory'])+1,'purpose':purpose,'stopEventSeq':stop,'seq':seq,
                         'address':h(address),'width':len(data),'rawHex':data.hex(),
                         'allowedRegion':{'base':h(span_base),'bytes':span_bytes,'name':purpose}}
                    rows['memory'].append(row); return row
                for i,c in enumerate(contexts):
                    stop=10+i*100
                    rows['get'].append({'apiOrdinal':i+1,'stopEventSeq':stop,'seq':stop+1,'integer':c['g'],
                                        'control':{k:c[k] for k in ('Rip','Rsp','EFlags')}})
                    if i: events[stop]={'rawDebugEventCode':1,'firstChance':True,'exceptionCode':0x80000004,
                                        'exceptionAddress':c['Rip'],'eventPID':200,'eventTID':201,'eventType':1}
                for i in range(len(ords)+1):
                    pre,post=contexts[i:i+2]; stop=10+i*100; poststop=stop+100
                    if i==0: code='41ffd3'; row=None; origin='owned_wrapper_CALL'; ordinal=None; pc=image+0x1013
                    else:
                        ordinal=ords[i-1]; row=catalog['instructions'][ordinal]; code=row['bytesHex']; origin='original_helper'; pc=base+int(row['rva'],16)
                    memory('instructionBytes',stop,stop+4,pc,bytes.fromhex(code),image+0x1000 if i==0 else base+0x1fdf9c0,27 if i==0 else 904)
                    ops=[]
                    if i==0: direction,ea,width='write',sp-8,8; old=b'\x91'*8; new=after.to_bytes(8,'little'); pp='CALLslotOld'; np='CALLslotNew'
                    elif row['dataOperands']:
                        spec=row['dataOperands'][0]; direction,width=spec['direction'],spec['width']; ea=v.operand_address(row,dict(pre['g'],Rsp=pre['Rsp']),base)
                        if row['flow']['kind']=='ret': old=after.to_bytes(8,'little'); new=None; pp='RETslot'; np=None
                        elif direction=='read':
                            control=next(x for x in case['controls'] if base+int(x['rva'],16)==ea and x['width']==width)
                            old=bytes.fromhex(control['bytesHex']); new=None; pp='operandRead'; np=None
                        else:
                            start=32+ea-head; old=bytes(buffer[start:start+width]); new=bytes(width); buffer[start:start+width]=new; pp='writeOld'; np='writeNew'
                    else: direction=None
                    if direction:
                        oldrow=memory(pp,stop,stop+5,ea,old,ea,width)
                        newrow=memory(np,poststop,poststop+2,ea,new,ea,width) if new is not None else None
                        ops=[{'direction':direction,'EA':h(ea),'width':width,'allowedSpan':{'base':h(ea),'bytes':width,'name':'synthetic'},
                              'preReadOrdinal':oldrow['apiOrdinal'],'preHex':old.hex(),'postReadOrdinal':newrow['apiOrdinal'] if newrow else None,
                              'postHex':new.hex() if new is not None else None}]
                    control={k:pre[k] for k in ('Rip','Rsp','EFlags')}
                    rows['set'].append({'apiOrdinal':i+1,'stopEventSeq':stop,'seq':stop+6,'beforeControl':control,'submittedControl':{'EFlags':'00000102'}})
                    rows['continue'].append({'apiOrdinal':i+1,'stopEventSeq':stop,'seq':stop+7,'BOOL':True,'cleanupOnly':False})
                    rows['transaction'].append({'txId':i+1,'preGetOrdinal':i+1,'postGetOrdinal':i+2,'preEventSeq':stop,'postEventSeq':poststop,
                       'preGPR':pre['g'],'postGPR':post['g'],'preEFlags':pre['EFlags'],'postEFlags':post['EFlags'],'preRip':pre['Rip'],'postRip':post['Rip'],
                       'instructionBytes':code,'instructionOrigin':origin,'sourceOrdinal':ordinal,'runtimeRva':row['rva'] if row else None,
                       'originalPC':row['originalPC'] if row else None,'ownedLabel':None if row else 'C48FullCall','ownedRva':None if row else '00001013',
                       'expectedNextPCs':[post['Rip']],'preflightAccepted':True,'retired':True,'status':'retired','tfSetOrdinal':i+1,
                       'continueOrdinal':i+1,'ops':ops,'seq':poststop+3,'eventPID':200,'eventTID':201,'eventType':1})
                v.check_transactions(case,catalog,rows,events,base,image,0x1013,0x1016,head)
                bad=copy.deepcopy(rows); bad['transaction'][2]['postRip']='0000000000000000'
                with self.assertRaises(ValueError): v.check_transactions(case,catalog,bad,events,base,image,0x1013,0x1016,head)
                bad=copy.deepcopy(rows); bad['transaction'].pop(3)
                with self.assertRaises(ValueError): v.check_transactions(case,catalog,bad,events,base,image,0x1013,0x1016,head)
                bad=copy.deepcopy(rows); bad['transaction'][2]['runtimeRva']='01fdf9a0'
                with self.assertRaises(ValueError): v.check_transactions(case,catalog,bad,events,base,image,0x1013,0x1016,head)
                branch=next(i+1 for i,o in enumerate(ords) if int(catalog['instructions'][o]['rva'],16) in (0x1fdfaac,0x1fdfc80))
                bad=copy.deepcopy(rows); bad['get'][branch]['control']['EFlags']='00000002'
                bad['transaction'][branch]['preEFlags']='00000002'; bad['transaction'][branch-1]['postEFlags']='00000002'
                bad['set'][branch]['beforeControl']['EFlags']='00000002'
                with self.assertRaises(ValueError): v.check_transactions(case,catalog,bad,events,base,image,0x1013,0x1016,head)
                jump=next(i+1 for i,o in enumerate(ords) if catalog['instructions'][o]['flow']['kind']=='indirect')
                bad=copy.deepcopy(rows); bad['get'][jump]['integer']['R11']=h(base+int(catalog['instructions'][ords[0]]['rva'],16))
                with self.assertRaises(ValueError): v.check_transactions(case,catalog,bad,events,base,image,0x1013,0x1016,head)
                bad=copy.deepcopy(rows); op=next(t['ops'][0] for t in bad['transaction'] if t['instructionOrigin']=='original_helper' and t['ops'])
                op['EA']=h(int(op['EA'],16)+1)
                with self.assertRaises(ValueError): v.check_transactions(case,catalog,bad,events,base,image,0x1013,0x1016,head)
                if case['id']==258:
                    bad=copy.deepcopy(rows); store=next(t for t in bad['transaction'] if t['sourceOrdinal']==98)
                    op=store['ops'][0]; op['preHex']='ff'*op['width']
                    next(m for m in bad['memory'] if m['apiOrdinal']==op['preReadOrdinal'])['rawHex']=op['preHex']
                    with self.assertRaises(ValueError): v.check_transactions(case,catalog,bad,events,base,image,0x1013,0x1016,head)

    def test_shared_sequence_record_and_transaction_integration(self):
        # Captures in-memory synthetic inputs only; no image or instruction is executed.
        captured=[]; original=v.check_transactions
        def capture(*args):
            result=original(*args)
            captured.append(copy.deepcopy(args))
            return result
        with patch.object(v,'check_transactions',side_effect=capture):
            self.test_both_complete_synthetic_transaction_paths()
        self.assertEqual(len(captured),2)
        descriptor_inputs=[]; original_descriptor=v.check_descriptor_and_pages
        def capture_descriptor(*args):
            result=original_descriptor(*args)
            descriptor_inputs.append(copy.deepcopy(args))
            return result
        with patch.object(v,'check_descriptor_and_pages',side_effect=capture_descriptor):
            self.test_thirteen_synthetic_page_queries_and_descriptor()
        self.assertEqual(len(descriptor_inputs),2)
        children=[]; original_child=v.check_child_api
        def capture_child(child):
            result=original_child(child);children.append(copy.deepcopy(child));return result
        with patch.object(v,'check_child_api',side_effect=capture_child):
            self.test_child_api_sequence_and_result()
        self.assertEqual(len(children),1)
        _, _, _, receipt, _ = old_synthetic_fixture(1)
        old_rows, _, _, _, _ = old_synthetic_fixture(1)
        create_template=next(r for r in old_rows if r['kind']=='event' and r['rawDebugEventCode']==3)
        dll_template=next(r for r in old_rows if r['kind']=='event' and r['rawDebugEventCode']==6)
        schema={'common':{'required':['kind','seq','phase','eventPID','eventTID','eventType','monotonicElapsedUs']},
                'rawRecordKinds':['epoch','ownedChild','wait','event','continue','get','set','calibration','memory','transaction','pageQuery','cleanup'],
                'memoryPurposeEnum':['descriptor','wrapper','source904','ownedBufferInitial','instructionBytes','operandRead','writeOld','writeNew','CALLslotOld','CALLslotNew','RETslot','ownedBufferFinal']}
        for descriptor_args,(case,catalog,synthetic,_,base,image,call,after,head) in zip(descriptor_inputs,captured):
            with self.subTest(case=case['id']):
                _, descriptor_records, _, epoch, child, run_id, interface=descriptor_args
                child.update(setupApiRecords=children[0]['setupApiRecords'],releaseApiRecords=children[0]['releaseApiRecords'])
                rows=[]; ords={k:0 for k in ('wait','get','set','continue','memory')}; current=[None,None,None]; stop=None
                def emit(kind,**values):
                    row={'kind':kind,'seq':len(rows)+1,'phase':'SYNTHETIC','eventPID':current[0],'eventTID':current[1],
                         'eventType':current[2],'monotonicElapsedUs':len(rows)*100};row.update(values);rows.append(row);return row
                def event(code,tid=201,**values):
                    nonlocal stop
                    current[:]=[200,tid,code];ords['wait']+=1
                    emit('wait',apiOrdinal=ords['wait'],BOOL=True,lastErrorIfFalse=None,timeoutMs=50,stopEventSeq=len(rows)+2)
                    defaults={'rawDebugEventCode':code,'firstChance':None,'exceptionCode':None,'exceptionAddress':None,
                              'exceptionFlags':None,'exceptionParameters':None,'exceptionParameterCount':None,
                              'continueStatus':None,'ContinueDebugEventBOOL':None,'continueRecordStatus':'separate'}
                    defaults.update(values);e=emit('event',**defaults);stop=e['seq'];return e
                def cont():
                    ords['continue']+=1;return emit('continue',apiOrdinal=ords['continue'],stopEventSeq=stop,
                         continueStatus=0x10002,BOOL=True,lastErrorIfFalse=None,cleanupOnly=False)
                def get(control,integer):
                    ords['get']+=1
                    return emit('get',apiOrdinal=ords['get'],stopEventSeq=stop,requestedGroups=['CONTROL','INTEGER'],BOOL=True,
                                lastErrorIfFalse=None,threadHandleOrigin='CreateProcess.PI.hThread',control=copy.deepcopy(control),integer=copy.deepcopy(integer))
                def set_tf(pre,integer,on):
                    control=copy.deepcopy(pre); control['EFlags']=f'{(int(pre["EFlags"],16)&~0x100)|(0x100 if on else 0):08x}'
                    readback=get(control,integer);ords['set']+=1
                    return emit('set',apiOrdinal=ords['set'],stopEventSeq=stop,requestedGroups=['CONTROL'],beforeControl=copy.deepcopy(pre),
                                submittedControl=control,BOOL=True,lastErrorIfFalse=None,readbackGetOrdinal=readback['apiOrdinal'],
                                readbackControl=control,integerUnchanged=True,eflagsReadbackPolicy='c48-tf-only-reserved-bit1-readback-v2',
                                eflagsReadbackXor='00000000',eflagsReadbackRepresentation='exact')
                def memory(source):
                    ords['memory']+=1
                    return emit('memory',apiOrdinal=ords['memory'],stopEventSeq=stop,purpose=source['purpose'],address=source['address'],
                                width=source['width'],allowedRegion=copy.deepcopy(source['allowedRegion']),ReadProcessMemoryBOOL=True,
                                actualBytesRead=source['width'],rawHex=source['rawHex'])
                epoch_row=emit('epoch',schema='c48-full-helper-epoch-v1')
                emit('ownedChild',pid=200,tid=201,processHandleOrigin='CreateProcess.PI.hProcess',threadHandleOrigin='CreateProcess.PI.hThread',
                     GetProcessTimesBOOL=True,lastErrorIfFalse=None,creationFileTime='0000000000000020',processHandleRaw='0000000000000100',threadHandleRaw='0000000000000101')
                def template(source):
                    return {k:copy.deepcopy(vv) for k,vv in source.items() if k not in ('kind','seq','phase','eventPID','eventTID','eventType','monotonicElapsedUs','rawDebugEventCode')}
                e=template(create_template);e.update(moduleBase=f'{image:016x}',moduleImageBytes='0000000000005000',fileSHA256='11'*32)
                event(3,**e);cont()
                nt=0x100000;e=template(dll_template);e.update(moduleBase=f'{nt:016x}',moduleImageBytes='0000000000010000',
                                                               filePath='synthetic\\ntdll.dll',filePathRequiredLength=19)
                event(6,**e);cont()
                code,tid,worker_fields=synthetic_create(202)
                worker=event(code,tid=tid,**worker_fields);cont()
                event(8,tid=202,debugStringAddress='0000000000123456',debugStringUnicode=0,debugStringLength=16);cont()
                all_gprs=('Rax','Rbx','Rcx','Rdx','Rsi','Rdi','Rbp','R8','R9','R10','R11','R12','R13','R14','R15')
                def context(row):
                    c=dict(row['control'],SegCs=51,SegSs=43);g={k:'0000000000000000' for k in all_gprs};g.update(row['integer']);return c,g
                c0,g0=context(synthetic['get'][0]);c0['Rip']=f'{nt+11:016x}'
                event(1,firstChance=True,exceptionCode=0x80000003,exceptionAddress=f'{nt+10:016x}',exceptionFlags=0,exceptionParameters=[],exceptionParameterCount=0)
                get(c0,g0);emit('calibration',name='systemBreakpoint',stopEventSeq=stop,exceptionAddress=f'{nt+10:016x}',contextRip=c0['Rip'],moduleBase=f'{nt:016x}',exceptionRva='000000000000000a',addressOrigin='systemModule');cont()
                event(1,firstChance=True,exceptionCode=0x80000003,exceptionAddress=f'{image+0x1012:016x}',exceptionFlags=0,exceptionParameters=[],exceptionParameterCount=0)
                pre,gregs=context(synthetic['get'][0]);pre_get=get(pre,gregs)
                emit('calibration',name='ownedInt3',stopEventSeq=stop,exceptionAddress=f'{image+0x1012:016x}',contextRip=pre['Rip'],moduleBase=f'{image:016x}',exceptionRva='0000000000001012',addressOrigin='childImage')
                for m in descriptor_records['memory']:
                    if m['purpose'] != 'ownedBufferFinal': memory(m)
                for index,p in enumerate(descriptor_records['pageQuery']):
                    details={k:copy.deepcopy(value) for k,value in p.items() if k!='stopEventSeq'}
                    emit('pageQuery',apiOrdinal=index+1,stopEventSeq=stop,**details)
                mem_by_id={m['apiOrdinal']:m for m in synthetic['memory']}
                for i,tx0 in enumerate(synthetic['transaction']):
                    tx=copy.deepcopy(tx0);pre_stop=stop
                    fetch=next(m for m in synthetic['memory'] if m['purpose']=='instructionBytes' and m['stopEventSeq']==tx0['preEventSeq'])
                    memory(fetch)
                    if tx['ops']:
                        op=tx['ops'][0];old=memory(mem_by_id[op['preReadOrdinal']]);op['preReadOrdinal']=old['apiOrdinal']
                    setrow=set_tf(pre,gregs,True);controw=cont()
                    post,post_g=context(synthetic['get'][i+1]);postaddr=post['Rip']
                    event(1,firstChance=True,exceptionCode=0x80000004,exceptionAddress=postaddr,exceptionFlags=0,exceptionParameters=[],exceptionParameterCount=0)
                    post_get=get(post,post_g);origin='childImage' if i==len(synthetic['transaction'])-1 else 'sourceArena';module=image if origin=='childImage' else base
                    emit('calibration',name='singleStep',stopEventSeq=stop,exceptionAddress=postaddr,contextRip=postaddr,moduleBase=f'{module:016x}',
                         exceptionRva=f'{int(postaddr,16)-module:0{16 if origin=="childImage" else 8}x}',addressOrigin=origin)
                    if tx['ops'] and tx['ops'][0]['postReadOrdinal'] is not None:
                        m=memory(mem_by_id[tx['ops'][0]['postReadOrdinal']]);tx['ops'][0]['postReadOrdinal']=m['apiOrdinal']
                    tx.update(preGetOrdinal=pre_get['apiOrdinal'],postGetOrdinal=post_get['apiOrdinal'],preEventSeq=pre_stop,postEventSeq=stop,
                              preGPR=gregs,postGPR=post_g,preEFlags=pre['EFlags'],postEFlags=post['EFlags'],preRip=pre['Rip'],postRip=post['Rip'],
                              tfSetOrdinal=setrow['apiOrdinal'],continueOrdinal=controw['apiOrdinal'])
                    for key in ('seq','eventPID','eventTID','eventType'): tx.pop(key,None)
                    emit('transaction',**tx);pre,gregs,pre_get=post,post_g,post_get
                memory(next(m for m in descriptor_records['memory'] if m['purpose']=='ownedBufferFinal'))
                set_tf(pre,gregs,False);cont()
                exit_event=event(5,exitCode=0);cont()
                def cleanup(api,origin,**kw):
                    return emit('cleanup',api=api,handleOrigin=origin,handleRaw='0000000000000100' if origin.endswith('hProcess') else '0000000000000101',**kw)
                cleanup('WaitForSingleObject','CreateProcess.PI.hProcess',result=0,lastErrorIfFailed=None)
                cleanup('GetExitCodeProcess','CreateProcess.PI.hProcess',BOOL=True,lastErrorIfFalse=None,exitCode=0)
                cleanup('CloseHandle','CreateProcess.PI.hThread',BOOL=True,lastErrorIfFalse=None)
                cleanup('CloseHandle','CreateProcess.PI.hProcess',BOOL=True,lastErrorIfFalse=None)
                summary={'pid':200,'tid':201,'exitEventSeq':exit_event['seq'],'exitEventTID':201,'threadLifecycleConsistent':True}
                records,events=v.check_records(rows,summary,receipt,schema)
                self.assertEqual(v.check_descriptor_and_pages(case,records,events,epoch,child,run_id,interface),(base,image,head))
                v.check_transactions(case,catalog,records,events,base,image,call,after,head)
                v.check_breakpoints(records,events,image,base,0x1012,call,len(synthetic['transaction']))
                releases=v.check_lifecycle(case['id'],records,events,summary,records['transaction'],exit_event)
                self.assertTrue(any(x.get('tid')==202 and x['originEventSeq']==worker['seq'] for x in releases))
                # A registered BOOT worker cannot supply a TRACE stop.
                bad_events=copy.deepcopy(events)
                bad_events[records['transaction'][0]['postEventSeq']]['eventTID']=202
                with self.assertRaisesRegex(ValueError,'TRACE identity/event'):
                    v.check_lifecycle(case['id'],records,bad_events,summary,records['transaction'],exit_event)
                # A notification from an unknown TID cannot establish a worker.
                bad_events=copy.deepcopy(events)
                next(e for e in bad_events.values() if e['rawDebugEventCode']==8)['eventTID']=203
                with self.assertRaisesRegex(ValueError,'unknown or exited thread'):
                    v.check_lifecycle(case['id'],records,bad_events,summary,records['transaction'],exit_event)
                v.check_normal_cleanup(records,exit_event)
                self.assertEqual(len(records['memory']),case['successfulInspectorReadCount'])
                self.assertEqual(sum(m['width'] for m in records['memory']),case['successfulInspectorRequestedBytes'])
                # Exercise CLI orchestration with synthetic PE/run files. Only the separately tested
                # external dependency inventory and build snapshot gates are stubbed here.
                from tools.test_client72_d02_clear48_full_artifacts import SyntheticArtifactsTests
                from tools import client72_d02_clear48_full_artifacts as art
                import types
                fake=SyntheticArtifactsTests();fake.setUp()
                try:
                    build=fake.build; root=Path(__file__).resolve().parents[1]; header=root/'tools/client72_d02_clear48_full_helper/shared.h'
                    (build/'shared.h').write_bytes(header.read_bytes())
                    exe=build/'child.exe';pe=bytearray(exe.read_bytes());struct.pack_into('<I',pe,0x98+56,0x5000)
                    struct.pack_into('<I',pe,0x98+0xf0+40+12,0x3000);exe.write_bytes(pe)
                    mapfile=build/'child.map';mapfile.write_text(mapfile.read_text().replace('0000000140002000','0000000140003000'))
                    (build/'parent.exe').write_bytes(b'SYNTHETIC PARENT');(build/'parent.map').write_text('SYNTHETIC MAP')
                    bootstrap=build.parent/'fixture-bootstrap'
                    contract_dir=root/'local/migration72/d02-resume-20261110b/a-clear48-full-helper-contract'
                    full_inventory=os.environ.get('C48_SYNTHETIC_FULL_WITH_FROZEN_SOURCES') == '1'
                    def entry(path):
                        raw=path.read_bytes()
                        return {'path':str(path.resolve()),'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
                    if full_inventory:
                        for name in art.BUILD_SOURCES: (build/name).write_bytes((header.parent/name).read_bytes())
                        seeds=root/'local/migration72/d02-resume-20261110b/a-clear48-full-helper-owned/generated-root-v1'
                        for name in art.GENERATED: (build/name).write_bytes((seeds/name).read_bytes())
                        (build/'build-epoch.h').write_text('#define C48F_SHARED_HEADER_SHA256_HEX "'+art.HEADER+'"\n')
                        source=[dict(name=name,bytes=(build/name).stat().st_size,sha256=hashlib.sha256((build/name).read_bytes()).hexdigest()) for name in sorted(art.BUILD_SOURCES)]
                        (build/'source-manifest.private.json').write_text(json.dumps(source))
                        files=[dict(name=p.name,bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in build.iterdir() if p.is_file()]
                        (build/'build-record.private.json').write_text(json.dumps({'synthetic':True,'schema':'c48-full-helper-build-v1','exit':0,
                            'sharedHeaderSHA256':art.HEADER,'source':source,'files':files,'fixtureExecuted':False,'original904Executed':False}))
                    with (nullcontext() if full_inventory else patch.object(art,'audit_build',return_value=[])):
                        art.prepare(build,header,contract_dir,bootstrap)
                    helper=build.parent/'synthetic-helper.bin';helper.write_bytes(b''.join(bytes.fromhex(x['bytesHex']) for x in catalog['instructions']))
                    review_path=build.parent/'synthetic-review.json'
                    review={'artifacts':{key:{'path':str((build/name).resolve())} for key,name in
                            (('parentExe','parent.exe'),('parentMap','parent.map'),('childExe','child.exe'),('childMap','child.map'))},
                            'actualCompiledSources':[{'path':str((build/name).resolve())} for name in art.BUILD_SOURCES|set(art.GENERATED)|{'build-epoch.h'}],
                            'reviewedCurrentSources':[{'path':str((header.parent/name).resolve())} for name in art.BUILD_SOURCES]}
                    if full_inventory:
                        private=root/'local/migration72/d02-resume-20261110b'; owned=private/'a-clear48-full-helper-owned'
                        compiled=[build/name for name in art.BUILD_SOURCES|set(art.GENERATED)|{'build-epoch.h'}]
                        current=[header.parent/name for name in art.BUILD_SOURCES]
                        supplements=[root/'tools/client72_d02_clear48_full_generate.py',root/'tools/client72_d02_clear48_full_run.py',
                            root/'tools/client72_d02_clear48_full_artifacts.py',root/'tools/client72_d02_clear48_full_validate.py',
                            contract_dir/'frozen-inputs.private.json',bootstrap/'bootstrap.bin',bootstrap/'bootstrap.private.json',helper,
                            private/'a-clear48-backend/input-pins.private.json',private/'a-clear48-trace-exit-lifecycle/exit-lifecycle-supplement.v2.private.json',
                            private/v.BOOT_CONTRACT]
                        supplements += [owned/name for name in ('root-bindings.v1.private.json','child-api-records.v1.private.json',
                            'build-review-schema.v1.private.json','transaction-owned-mapping.v1.private.json','root-receipt-semantics.v1.private.json')]
                        old=json.loads((private/'a-clear48-backend/input-pins.private.json').read_text())
                        sources=compiled+current+supplements+[root/name for name in old]+[contract_dir/name for name in art.contracts(contract_dir)]
                        review.update(schema='c48-full-helper-dependency-review-v1',independentlyReviewed=True,peAndCallerUnwindReviewed=True,
                            dependencyProofComplete=True,actualBuildExit=0,actualCompiledSources=[entry(p) for p in compiled],
                            reviewedCurrentSources=[entry(p) for p in current],source=[entry(p) for p in dict.fromkeys(sources)],
                            buildLogs=[entry(build/'parent.map')],buildTools=[entry(build/'parent.map')],compilerDlls=[entry(build/'parent.map')],
                            includes=[entry(build/'parent.map')],libraries=[entry(build/'parent.map')],searchedLibraries=[])
                        review['artifacts']={key:entry(build/name) for key,name in
                            (('parentExe','parent.exe'),('parentMap','parent.map'),('childExe','child.exe'),('childMap','child.map'))}
                    review_path.write_text(json.dumps(review))
                    run=build.parent/'synthetic-run';native=run/'native';native.mkdir(parents=True)
                    child_hash=hashlib.sha256(exe.read_bytes()).hexdigest();parent_hash=hashlib.sha256((build/'parent.exe').read_bytes()).hexdigest()
                    source_proof=dict(epoch,childExeSHA256=child_hash,parentExeSHA256=parent_hash,
                        bootstrapSHA256=hashlib.sha256((bootstrap/'bootstrap.bin').read_bytes()).hexdigest(),sharedHeaderSHA256=art.HEADER,
                        interfaceContractSHA256=hashlib.sha256((contract_dir/'interface.v1.private.json').read_bytes()).hexdigest(),
                        exitLifecyclePolicy=interface['exitPolicy'],exitLifecycleContractSHA256=interface['exitPolicySHA256'],
                        eflagsReadbackPolicy=interface['eflagsPolicy'])
                    epoch_row.update(source_proof)
                    next(r for r in rows if r['kind']=='event' and r['rawDebugEventCode']==3)['fileSHA256']=child_hash
                    counts={'events':len(records['event']),'waits':len(records['wait']),'gets':len(records['get']),'sets':len(records['set']),
                            'reads':len(records['memory']),'requestedReadBytes':sum(m['width'] for m in records['memory']),
                            'continues':len(records['continue']),'pageQueries':len(records['pageQuery']),
                            'retired':len(records['transaction']),'helperRetired':len(records['transaction'])-1}
                    summary.update(schema='c48-full-helper-parent-result-v1',caseId=case['id'],runId=run_id,sourceProof=source_proof,
                        normalTracePassed=True,firstFailureKind=None,pendingTxId=None,pendingRetired=None,lastCompletedTx=len(records['transaction']),
                        childExitCode=0,helperRetired=len(case['instructionOrdinals']),wrapperRetired=1,helperControlReads=case['helperControlReads'],
                        helperControlReadBytes=case['helperControlReadBytes'],helperStoreEvents=len(case['stores']),helperStoreSumBytes=case['logicalStoreSumBytes'],
                        helperStoreUnionBytes=case['logicalStoreUnionBytes'],helperStoreIntersectionBytes=case['logicalStoreIntersectionBytes'],
                        returnRAX=f'{head+16:016x}',returnRSP=synthetic['get'][0]['control']['Rsp'],budgets=counts)
                    summary.update({k:True for k in ('EXIT_PROCESSobserved','EXIT_PROCESScontinued','ownedProcessSignaled','GetExitCodeProcessBOOL','PIThreadCloseBOOL','PIProcessCloseBOOL','threadLifecycleConsistent','cleanupComplete')})
                    (native/'events.jsonl').write_text(''.join(json.dumps(r)+'\n' for r in rows))
                    (native/'parent-result.json').write_text(json.dumps(summary));(native/'child-result.json').write_text(json.dumps(child))
                    artifacts={'parentExe':str((build/'parent.exe').resolve()),'childExe':str(exe.resolve()),'header':str(header.resolve()),
                               'bootstrap':str((bootstrap/'bootstrap.bin').resolve()),'bootstrapMirror':str((bootstrap/'bootstrap.private.json').resolve()),
                               'helper':str(helper.resolve()),'dependencyReview':str(review_path.resolve()),'contractDirectory':str(contract_dir.resolve())}
                    paths=[Path(p) for k,p in artifacts.items() if k!='contractDirectory']+[contract_dir/n for n in art.contracts(contract_dir)]+[contract_dir/'contract-artifacts.private.json']
                    before={str(p.resolve()):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
                    if full_inventory:
                        for group in ('actualCompiledSources','reviewedCurrentSources','source','buildLogs','buildTools','compilerDlls','includes','libraries','searchedLibraries'):
                            before.update({e['path']:e['sha256'] for e in review[group]})
                        before.update({e['path']:e['sha256'] for e in review['artifacts'].values()})
                    root_receipt={'schema':'c48-full-helper-root-run-v1','caseId':case['id'],'runId':run_id,'artifacts':artifacts,'before':before,'after':dict(before),
                        'parentCreated':True,'childCreated':None,'parentPid':199,'parentCreationFileTime':'0000000000000010','parentExit':0,
                        'timedOut':False,'cleanupConfirmed':True,'startedUtc':'2026-11-11T00:00:00Z','endedUtc':'2026-11-11T00:00:01Z','error':None,
                        'argv':[artifacts['parentExe'],'--child-exe',artifacts['childExe'],'--bootstrap',artifacts['bootstrap'],'--helper',artifacts['helper'],
                                '--case',str(case['id']),'--run-id',run_id,'--output',str(native.resolve())]}
                    (run/'receipt.json').write_text(json.dumps(root_receipt))
                    args=types.SimpleNamespace(run=run,build=build,bootstrap=bootstrap,dependency_review=review_path,contract_dir=contract_dir,output=run/'result.json')
                    cli=['--run',str(run),'--build',str(build),'--bootstrap',str(bootstrap),'--dependency-review',str(review_path),
                         '--contract-dir',str(contract_dir),'--output',str(args.output)]
                    with (nullcontext() if full_inventory else patch.object(v,'check_dependency')), \
                         (nullcontext() if full_inventory else patch.object(art,'audit_build')):
                        self.assertEqual(v.main(cli),0)
                        self.assertTrue(json.loads(args.output.read_text())['accepted'])
                        def reject(name,mutate):
                            bad_rows,bad_summary,bad_child,bad_receipt=copy.deepcopy((rows,summary,child,root_receipt))
                            mutate(bad_rows,bad_summary,bad_child,bad_receipt)
                            (native/'events.jsonl').write_text(''.join(json.dumps(r)+'\n' for r in bad_rows))
                            (native/'parent-result.json').write_text(json.dumps(bad_summary));(native/'child-result.json').write_text(json.dumps(bad_child))
                            (run/'receipt.json').write_text(json.dumps(bad_receipt))
                            output=run/('mutation-'+name+'.json');cli[-1]=str(output)
                            self.assertEqual(v.main(cli),1,name)
                            self.assertFalse(json.loads(output.read_text())['accepted'],name)
                        reject('pending',lambda r,s,c,p:s.update(pendingRetired=False))
                        reject('schema',lambda r,s,c,p:s.update(schema='old'))
                        reject('pc',lambda r,s,c,p:next(x for x in r if x['kind']=='transaction' and x['sourceOrdinal'] is not None).update(preRip='0000000000000000'))
                        reject('padding',lambda r,s,c,p:next(x for x in r if x['kind']=='transaction' and x['sourceOrdinal'] is not None).update(runtimeRva='01fdf9a0'))
                        reject('ea',lambda r,s,c,p:next(x for x in r if x['kind']=='transaction' and x['ops']).get('ops')[0].update(EA='0000000000000000'))
                        reject('width',lambda r,s,c,p:next(x for x in r if x['kind']=='memory' and x['purpose']=='operandRead').update(width=1))
                        reject('duplicate-step',lambda r,s,c,p:r.append(copy.deepcopy(next(x for x in r if x['kind']=='transaction'))))
                        reject('missing-step',lambda r,s,c,p:r.remove(next(x for x in r if x['kind']=='transaction')))
                        reject('identity',lambda r,s,c,p:next(x for x in r if x['kind']=='wait' and x['BOOL'] is True).update(eventTID=202))
                        reject('bool',lambda r,s,c,p:next(x for x in r if x['kind']=='continue').update(BOOL=1))
                        reject('tf-other-bit',lambda r,s,c,p:next(x for x in r if x['kind']=='set')['submittedControl'].update(EFlags='00000342'))
                        reject('gpr',lambda r,s,c,p:next(x for x in r if x['kind']=='get' and x['stopEventSeq']==
                               next(y['stopEventSeq'] for y in r if y['kind']=='calibration' and y['name']=='ownedInt3'))['integer'].update(Rbx='0000000000000001'))
                        reject('reserve-metadata',lambda r,s,c,p:next(x for x in r if x['kind']=='pageQuery' and x['purpose']=='arena1')['rawMemoryBasicInformation'].update(Protect=4))
                        reject('budget',lambda r,s,c,p:s['budgets'].update(reads=s['budgets']['reads']+1))
                        reject('log',lambda r,s,c,p:r.pop())
                        reject('exit',lambda r,s,c,p:next(x for x in r if x['kind']=='cleanup' and x['api']=='GetExitCodeProcess').update(exitCode=90))
                        reject('close',lambda r,s,c,p:next(x for x in r if x['kind']=='cleanup' and x['api']=='CloseHandle').update(BOOL=False))
                        reject('child-release',lambda r,s,c,p:c['releaseApiRecords'][0].update(BOOL=False))
                        reject('receipt',lambda r,s,c,p:p['after'].update({next(iter(p['after'])):'00'*32}))
                        if case['id']==258:
                            reject('overlap-old',lambda r,s,c,p:next(x for x in r if x['kind']=='transaction' and x['sourceOrdinal']==98)['ops'][0].update(preHex='ff'*32))
                finally:
                    fake.tearDown()
if __name__ == '__main__':
    unittest.main()
