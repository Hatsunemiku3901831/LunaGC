"""Independent interface-ingress, owned-storage and native successor assertions."""
import copy
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
from types import SimpleNamespace
import unittest

from tools import client72_avatar_fresh_interfaces as card


class MemoryABI:
    def __init__(self):
        self.uc=self;self.memory={};self.registers=dict(rsp=0x1000,rcx=0,rdx=0,r8=0,r14=0,r15=0,rdi=0)
        self.heap_cursor=0x9000000100000000;self.failure=None;self.steps=0

    def reg(self,name):
        if name=='edx':return self.registers['rdx']&0xffffffff
        return self.registers[name]
    def mem_write(self,pointer,raw):self.memory.update({pointer+i:value for i,value in enumerate(raw)})
    def mem_read(self,pointer,width):return bytes(self.memory[pointer+i] for i in range(width))
    def read(self,pointer,fmt='Q'):return struct.unpack('<'+fmt,self.mem_read(pointer,struct.calcsize('<'+fmt)))[0]
    def write(self,pointer,value,fmt='Q'):self.mem_write(pointer,struct.pack('<'+fmt,value))
    def alloc(self,width):
        pointer=self.heap_cursor;self.heap_cursor+=(width+15)&~15;self.mem_write(pointer,bytes(width));return pointer
    def call(self,caller,**values):self.write(self.registers['rsp'],caller);self.registers.update(values)


class IngressTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.raw=json.loads((card.REPO/card.INPUT).read_bytes())

    def changed_header(self,index,offset,raw):
        incoming=copy.deepcopy(self.raw);row=next(r for r in incoming['records'] if r['name']=='interface.class'+str(index)+'.header')
        value=bytearray.fromhex(row['bytesHex']);value[offset:offset+len(raw)]=raw;row['bytesHex']=value.hex()
        return incoming

    def test_exact_fresh_interface_identity_and_zero_compacts_are_distinct_from_scene_input(self):
        rows,data=card.checked_input(self.raw)
        self.assertEqual((len(rows),sum(map(len,data.values()))),(8,584))
        self.assertEqual(sum(len(v) for k,v in data.items() if k.startswith('interface.')),580)
        for index in (1169,1249):
            h=data['interface.class'+str(index)+'.header']
            self.assertEqual((struct.unpack_from('<Q',h,16)[0],h[26]),(index,18))
            self.assertEqual(struct.unpack_from('<H',h,0xbe)[0],0)
            self.assertEqual(struct.unpack_from('<H',h,0xc2)[0],2)

    def test_wrong_id_kind_and_unprovided_compact_or_generic_context_are_rejected(self):
        for index,offset,value in ((1169,16,struct.pack('<Q',1249)),(1249,26,b'\x1c'),
                                   (1169,0xa8,struct.pack('<I',0x80000001)),(1249,0xb0,struct.pack('<I',1)),
                                   (1249,0x68,struct.pack('<Q',0x8000000100000001))):
            with self.subTest(index=index,offset=offset),self.assertRaises(ValueError):
                card.checked_input(self.changed_header(index,offset,value))

    def test_wrong_cache_metadata_chain_short_span_source_status_and_budget_are_rejected(self):
        variants=[]
        for field,value in (('status','partial'),('sourceSHA256','0'*64),('registryRoot',0x8000000100000001),('registryAttempted',True)):
            item=copy.deepcopy(self.raw);item[field]=value;variants.append(item)
        item=copy.deepcopy(self.raw);item['budget']['requestedBytes']=583;variants.append(item)
        for name,field,value in (('interface.class1169.entry','address',0x8000000100000001),
                                ('interface.class1249.metadata','address',0x8000000100000001),
                                ('interface.class1169.header','bytesRead',207),
                                ('interface.class1169.header','error','owned refusal')):
            item=copy.deepcopy(self.raw);next(r for r in item['records'] if r['name']==name)[field]=value;variants.append(item)
        for ordinal,item in enumerate(variants):
            with self.subTest(ordinal=ordinal),self.assertRaises(ValueError):card.checked_input(item)

    def binding_fixture(self):
        rows,data=card.checked_input(self.raw)
        old={'reads':{'cold.class1239.metadata':{'address':0x8000000100000000+1239*70,'length':70}},
             'data':{'cold.registry.base':struct.pack('<Q',0x50000000000),'cold.classCache.cell':struct.pack('<Q',0x500005467b8)}}
        class Metadata:
            def __getitem__(self,span):
                for index in (1169,1249):
                    if span.start==index*70 and span.stop==(index+1)*70:return data['interface.class'+str(index)+'.metadata']
                raise AssertionError('Unexpected fixed metadata range')
        base=SimpleNamespace(HEADER_VA=0,BODY=0,number=lambda *args:(-0xa7932555)&0xffffffff)
        return old,Metadata(),base

    def test_cross_epoch_binding_preserves_only_observed_identity_and_opaque_equality(self):
        old,metadata,base=self.binding_fixture()
        bridge=card.bind_interfaces(self.raw,old,b'',metadata,SimpleNamespace(offset=lambda address:0),base)
        self.assertFalse(bridge['sameProcessReplay']);self.assertFalse(bridge['freshRegistryObserved'])
        self.assertEqual([c['allocationBytes'] for c in bridge['classes']],[208,208])
        first,second=(bytes.fromhex(c['modelHeaderHex']) for c in bridge['classes'])
        self.assertEqual(struct.unpack_from('<Q',first,0)[0],struct.unpack_from('<Q',second,0)[0])
        self.assertEqual(struct.unpack_from('<Q',first,8)[0],1)
        for c in bridge['classes']:
            self.assertNotEqual(c['freshAddress'],c['modelAddress'])
            for reference in c['opaqueFields']:
                token=int(reference['model'],16)
                self.assertFalse(any(r['address']<=token<r['address']+len(old['data'][name]) for name,r in old['reads'].items() if name in old['data']))

    def test_metadata_bytes_are_checked_against_the_exact_definition_row(self):
        old,metadata,base=self.binding_fixture();bad=copy.deepcopy(self.raw)
        row=next(r for r in bad['records'] if r['name']=='interface.class1169.metadata');value=bytearray.fromhex(row['bytesHex']);value[69]^=1;row['bytesHex']=value.hex()
        with self.assertRaisesRegex(ValueError,'exact fixed row'):
            card.bind_interfaces(bad,old,b'',metadata,SimpleNamespace(offset=lambda address:0),base)


class StorageTests(unittest.TestCase):
    def fixture(self):
        e=MemoryABI();owner=0x8123456789abcdef;source=0x8000000100003000;target=0x8000000100004000;generic=0x8000000100005000
        snapshot={'reads':{'cold.freshAncestor.class1234.header':{'address':source}},'data':{'cold.registry.cell':struct.pack('<Q',owner)}}
        e.write(target+0x68,generic);e.write(generic,1234,'I');e.write(source+0xc2,8,'H')
        model=card.MethodPointerStorage(e,snapshot);model.classes[target]=400
        e.call(0x14051e35b,rcx=owner,rdx=64,r15=64,rdi=source,r14=target)
        return e,model,owner,source,target

    def test_method_pointer_array_has_exact64_owned_bytes_and_nonzero_64bit_tail(self):
        e,model,*_=self.fixture();pointer=model.allocate()
        self.assertGreater(pointer,1<<63);self.assertEqual(e.mem_read(pointer,64),b'\xcc'*64)
        self.assertEqual(e.read(pointer+64),0xfedcba9876543210)
        e.call(0x14051e36d,rcx=pointer,rdx=0,r8=64);self.assertEqual(model.clear(),pointer)
        self.assertEqual(e.mem_read(pointer,64),bytes(64));self.assertEqual(e.read(pointer+64),0xfedcba9876543210)

    def test_method_array_rejects_wrong_return_owner_width_source_target_definition_and_count(self):
        for changes in ({'caller':0x14051e35a},{'rcx':1},{'rdx':56},{'r15':65},{'rdi':1},{'r14':1}):
            e,model,*_=self.fixture();values=dict(changes);caller=values.pop('caller',0x14051e35b);e.call(caller,**values)
            with self.subTest(changes=changes),self.assertRaises(ValueError):model.allocate()
            self.assertEqual(model.events,[])
        for offset,fmt,value in ((0xc2,'H',9),(0xc2,'H',0)):
            e,model,_,source,_=self.fixture();e.write(source+offset,value,fmt)
            with self.assertRaises(ValueError):model.allocate()
        e,model,_,_,target=self.fixture();e.write(e.read(target+0x68),1249,'I')
        with self.assertRaises(ValueError):model.allocate()

    def test_clear_rejects_changed_tail_and_never_writes_adjacent_bytes(self):
        e,model,*_=self.fixture();pointer=model.allocate();e.write(pointer-8,0x8000000100000001)
        for caller,pointer_delta,width,value in ((0x14051e36c,0,64,0),(0x14051e36d,8,64,0),(0x14051e36d,0,56,0),(0x14051e36d,0,64,1)):
            e.call(caller,rcx=pointer+pointer_delta,rdx=value,r8=width)
            with self.subTest(caller=caller,delta=pointer_delta,width=width,value=value),self.assertRaises(ValueError):model.clear()
        e.write(pointer+64,card.ancestor.CANARY^1);e.call(0x14051e36d,rcx=pointer,rdx=0,r8=64)
        with self.assertRaisesRegex(ValueError,'tail changed'):model.clear()
        self.assertEqual(e.mem_read(pointer,64),b'\xcc'*64);self.assertEqual(e.read(pointer-8),0x8000000100000001)

    def test_only_exact1249_template_constructs208_bytes_and_preserves_tail(self):
        e=MemoryABI();owner=0x8000000100000001;template=0x8000000100002000
        layer=card.ExtensionLayer(e,{'reads':{'cold.freshInterfaces.class1249.header':{'address':template}},'data':{'cold.registry.cell':struct.pack('<Q',owner)}})
        layer.class_arena=0x9000000200000000;layer.classes={}
        def reject():raise ValueError('Inherited allocator refused unrelated request')
        callback=layer.wrap(0x1405091d0,reject)
        for values in ({'rcx':owner^1,'r15':template},{'rcx':owner,'r15':template^1}):
            e.call(0x140517a1d,rdx=208,**values)
            with self.assertRaises(ValueError):callback()
        e.call(0x140517a1d,rcx=owner,rdx=208,r15=template);pointer=callback()
        self.assertEqual(layer.classes,{pointer:208});self.assertEqual(e.mem_read(pointer,208),b'\xcc'*208)
        self.assertEqual(e.read(pointer+208),0xfedcba9876543210)
        e.call(0x140517a1c,rcx=owner,rdx=208,r15=template)
        with self.assertRaisesRegex(ValueError,'Inherited'):callback()


class ProjectionTests(unittest.TestCase):
    def fixture(self):
        incoming=json.loads((card.REPO/card.ancestor.FRESH).read_bytes())
        rows={row['name']:bytes.fromhex(row['bytesHex']) for row in incoming['reads']}
        snapshot={'reads':{},'data':{}}
        for suffix,source,address in (('header','ancestor.1234.header',0x8000000100000000),('metadata','ancestor.1234.metadata',0x8000000200000000)):
            card.ancestor.add_span(snapshot,'cold.freshAncestor.class1234.'+suffix,address,rows[source])
        record=dict(index=1234,methodStart=10287,methodCount=8,methods=[dict(index=index,name='independent-method-'+str(index)) for index in range(10287,10295)])
        return snapshot,[record]

    def test_only_the_eight_fixed_indices_are_projected_and_every_other_record_byte_is_absent(self):
        snapshot,records=self.fixture();before=snapshot['data']['cold.freshAncestor.class1234.header']
        projection=card.fixed_method_indices(snapshot,records)
        self.assertEqual((projection['methodStart'],projection['count'],projection['suppliedBytes']),(10287,8,96))
        self.assertFalse(projection['naturalMethodInfoObserved']);self.assertFalse(projection['wholeMethodInfoSupplied'])
        model=snapshot['data']['cold.freshAncestor.class1234.header'];self.assertEqual(model[:0x98],before[:0x98]);self.assertEqual(model[0xa0:],before[0xa0:])
        array=snapshot['data']['cold.fixedMethodProjection.array1234'];pointers=[p[0] for p in struct.iter_unpack('<Q',array)]
        self.assertEqual(pointers,[0x6f0001000+i*56 for i in range(8)])
        for ordinal,index in enumerate(range(10287,10295)):
            label='cold.fixedMethodProjection.index'+str(ordinal);row=snapshot['reads'][label]
            self.assertEqual((row['address'],row['length']),(pointers[ordinal]+24,4))
            self.assertEqual(snapshot['data'][label],struct.pack('<I',index))
            self.assertFalse(any(r['address']<=pointers[ordinal]+23<r['address']+r['length'] for r in snapshot['reads'].values()))
            self.assertFalse(any(r['address']<=pointers[ordinal]+28<r['address']+r['length'] for r in snapshot['reads'].values()))

    def test_projection_rejects_different_count_generic_source_span_and_zero_array(self):
        for change in ('count','generic','span','zero-array','metadata-start'):
            snapshot,records=self.fixture();label='cold.freshAncestor.class1234.header';head=bytearray(snapshot['data'][label])
            if change=='count':struct.pack_into('<H',head,0xc2,9)
            if change=='generic':struct.pack_into('<Q',head,0x68,0x8000000100000001)
            if change=='span':records[0]['methods'][7]['index']=10295
            if change=='zero-array':struct.pack_into('<Q',head,0x98,0)
            if change=='metadata-start':
                meta=bytearray(snapshot['data']['cold.freshAncestor.class1234.metadata']);meta[12]^=1;snapshot['data']['cold.freshAncestor.class1234.metadata']=bytes(meta)
            snapshot['data'][label]=bytes(head)
            with self.subTest(change=change),self.assertRaises(ValueError):card.fixed_method_indices(snapshot,records)
            self.assertNotIn('cold.fixedMethodProjection.array1234',snapshot['data'])

    def test_a_different_first_failure_never_enables_the_projection_case(self):
        snapshot,_=self.fixture();pointer=struct.unpack_from('<Q',snapshot['data']['cold.freshAncestor.class1234.header'],0x98)[0]
        last=dict(failure='Uncaptured natural-address memory read',error=None,uncapturedReads=[dict(address=hex(pointer),size=8,site='0x14051e387')])
        self.assertTrue(card.is_projection_stop({'native':[last]},snapshot))
        for key,value in (('failure','Other failure'),('error',''),('uncapturedReads',[dict(address=hex(pointer),size=4,site='0x14051e387')])):
            changed=dict(last);changed[key]=value;self.assertFalse(card.is_projection_stop({'native':[changed]},snapshot))


class GuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        card.frozen.base.load_emulator(card.REPO/'local/migration72/python')
        import capstone
        cls.cs=capstone;cls.md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64);cls.md.detail=True

    def opaque(self,code,**registers):
        e=MemoryABI();e.mem_write(0x2000,bytes.fromhex(code));e.registers.update(registers);state={'opaqueStops':[]}
        stopped=card.opaque_use(e,0x2000,len(bytes.fromhex(code)),self.md,state,self.cs.CS_OP_REG,self.cs.CS_OP_MEM,self.cs.CS_OP_IMM)
        return stopped,e,state

    def test_opaque_references_can_copy_and_check_null_but_cannot_read_or_join_old_identity(self):
        for code in ('4889ca','4885c9','4883f900'):
            with self.subTest(code=code):self.assertFalse(self.opaque(code,rcx=card.OPAQUE)[0])
        for code,registers in (('488b01',{'rcx':card.OPAQUE}),('4839c1',{'rcx':card.OPAQUE,'rax':0x500010ea220}),
                               ('4883c101',{'rcx':card.OPAQUE}),('483b08',{'rcx':card.OPAQUE,'rax':0x3000})):
            stopped,e,state=self.opaque(code,**registers)
            with self.subTest(code=code):self.assertTrue(stopped);self.assertTrue(e.failure);self.assertEqual(len(state['opaqueStops']),1)

    def test_instruction_limit_is_hard_for_unbounded_and_frozen20m_invocations(self):
        e=MemoryABI();state={'invocations':[]}
        class OwnedCpu:
            def emu_start(self,entry,end,count):self.last=count;e.steps+=count
        cpu=OwnedCpu();wrapped=card.CpuAdapter(cpu,e,state,{})
        for count,expected in ((0,2_000_000),(20_000_000,2_000_000),(7,7)):
            e.failure=None;wrapped.emu_start(0x1000,0x2000,count=count)
            self.assertEqual(cpu.last,expected);self.assertEqual(state['invocations'][-1]['steps'],expected)
            self.assertEqual(e.failure is not None,expected==2_000_000)

    def test_var362_extension_only_accepts_the_exact_two_byte_reader_and_delegates_other_spans(self):
        e=MemoryABI();e.U=SimpleNamespace(UC_HOOK_MEM_READ=7);state={'parameter362Reads':[]};delegated=[];stops=[]
        class OwnedCpu:
            def hook_add(self,kind,callback):self.callback=callback;return 41
            def emu_stop(self):stops.append(True)
        cpu=OwnedCpu();wrapped=card.CpuAdapter(cpu,e,state,{'address':'0x3000'})
        def read_guard(*args):delegated.append(args[2:4])
        self.assertEqual(wrapped.hook_add(7,read_guard),41)
        e.registers['rip']=0x1404eda27;cpu.callback(cpu,0,0x3006,2,0,None)
        self.assertEqual(state['parameter362Reads'],[dict(address='0x3006',width=2,reader='0x1404eda27')]);self.assertEqual(delegated,[])
        for pointer,width,reader in ((0x3005,2,0x1404eda27),(0x3006,4,0x1404eda27),(0x3006,2,0x1404eda28)):
            e.failure=None;e.registers['rip']=reader;cpu.callback(cpu,0,pointer,width,0,None)
            with self.subTest(pointer=pointer,width=width,reader=reader):self.assertEqual(e.failure,'Unreviewed VAR362 field reader')
        cpu.callback(cpu,0,0x300e,1,0,None);self.assertEqual(delegated,[(0x300e,1)]);self.assertEqual(len(stops),3)


class OutputGateTests(unittest.TestCase):
    def test_existing_output_and_outside_directory_are_rejected_before_fixed_client_access(self):
        existing=card.REPO/card.RAW/'probe1.json';before=hashlib.sha256(existing.read_bytes()).hexdigest()
        outside=card.REPO/'local/migration72/d02-resume-20261108/fresh-interfaces-rejected.json'
        for path in (existing,outside):
            run=subprocess.run([sys.executable,'-m','tools.client72_avatar_fresh_interfaces','--client','must-not-read-client','--output',str(path)],cwd=card.REPO,capture_output=True,text=True)
            self.assertEqual(run.returncode,2);self.assertNotIn('Traceback',run.stderr)
        self.assertEqual(hashlib.sha256(existing.read_bytes()).hexdigest(),before);self.assertFalse(outside.exists())


class NativeReportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=Path(os.environ.get('CLIENT72_AVATAR_FRESH_INTERFACES_REPORT',card.REPO/card.RAW/'first.json'))
        cls.report=json.loads(path.read_bytes());cls.cases=(cls.report['cold'],cls.report['fixedMethodProjection']['cold'])
        cls.previous=json.loads((card.REPO/card.ANCESTOR_RAW).read_bytes())

    def test_unprojected_missing_array_and_projected_missing_mask_are_both_retained(self):
        raw,projected=self.cases
        self.assertEqual(raw['native'][-1]['uncapturedReads'],[dict(address='0x500010ea5c0',size=8,site='0x14051e387')])
        self.assertEqual(raw['native'][-1]['failure'],'Uncaptured natural-address memory read')
        self.assertIsNone(raw['native'][-1]['error']);self.assertIsNone(projected['native'][-1]['error'])
        self.assertEqual(projected['native'][-1]['failure'],'Unbound runtime global cell read')
        self.assertEqual([r for r in projected['runtimeGlobalReads'] if not r['supplied']],[dict(address='0x145bc7290',width=8,reader='0x1404eeb80',supplied=False)])
        self.assertEqual(projected['extension']['stopRegisters']['r10'],'0x282f')
        contract=self.report['firstMissing']
        self.assertEqual([(r['moduleRVA'],r['width']) for r in contract['minimumUnblockingRoots']],[('0x5bc7290',8),('0x5bc72b0',8),('0x5bc72b8',8)])
        self.assertFalse(contract['samplingReady']);self.assertFalse(contract['samplingStarted']);self.assertFalse(contract['fullLookupClosureOrBudgetClaimed'])

    def test_frozen_six_producer_paths_and_all_seven_instruction_caps_remain_exact(self):
        for cold in self.cases:
            self.assertEqual(len(cold['native']),7)
            for current,previous in zip(cold['native'][:6],self.previous['cold']['native'][:6]):
                self.assertEqual(current['name'],previous['name']);self.assertEqual(current['trace']['pathSHA256'],previous['trace']['pathSHA256'])
                self.assertEqual(current['regs'],previous['regs']);self.assertEqual(current['failure'],previous['failure'])
            self.assertEqual(len(cold['extension']['invocations']),7)
            for row in cold['extension']['invocations']:
                self.assertEqual(row['enforcedLimit'],2_000_000);self.assertLess(row['steps'],2_000_000)
            self.assertEqual(cold['models']['callbackFailures'],[]);self.assertEqual(cold['extension']['opaqueStops'],[])

    def test_original_nested_1249_publication_and_interface_array_use_distinct_nonzero_64bit_classes(self):
        for cold in self.cases:
            records=cold['genericRecords'];classes=cold['newClasses']
            self.assertEqual([struct.unpack_from('<I',bytes.fromhex(r['bytesHex']))[0] for r in records],[1239,1234,1249])
            self.assertEqual([k['width'] for k in classes],[400,400,208])
            for r,k in zip(records,classes):
                self.assertEqual(struct.unpack_from('<Q',bytes.fromhex(r['bytesHex']),24)[0],int(k['pointer'],16))
                self.assertEqual(struct.unpack_from('<Q',bytes.fromhex(k['headerHex']),0x68)[0],int(r['pointer'],16))
            interface=cold['interfaceStorage']['allocations'][0]
            self.assertEqual(struct.unpack('<QQ',bytes.fromhex(interface['bytesHex'])),(0x500e0000000,0x500f0002000))
            self.assertEqual(interface['tail'],'0xfedcba9876543210')
            template=bytes.fromhex(self.report['interfaceBridge']['classes'][1]['modelHeaderHex']);shell=bytes.fromhex(classes[2]['headerHex'])
            for offset in (0,0x58,0x70):self.assertEqual(shell[offset:offset+8],template[offset:offset+8])
            self.assertEqual(shell[0x98:0xa0],bytes(8))
            self.assertEqual(cold['extension']['class208Allocations'][0]['tail'],'0xfedcba9876543210')
            storage=cold['extension']['methodPointerStorage']['allocations'][0]
            self.assertEqual((storage['width'],storage['bytesHex'],storage['tail']),(64,'00'*64,'0xfedcba9876543210'))

    def test_the_first_native_method_uses_only_projected_array8_and_index4_then_real_context(self):
        self.assertEqual(self.cases[0]['extension']['methodProjectionReads'],[])
        extra=self.cases[1]['extension']
        self.assertEqual(extra['methodProjectionReads'],[dict(address='0x6f0000000',width=8,reader='0x14051e387'),dict(address='0x6f0001018',width=4,reader='0x14051e38f')])
        self.assertEqual(len(extra['methodInflationCalls']),1);call=extra['methodInflationCalls'][0]
        self.assertEqual(bytes.fromhex(call['bytesHex']),struct.pack('<I4xQQ',10287,0x142377940,0))
        self.assertEqual((call['caller'],call['copyAndInternByte']),('0x14051e3b2',1))
        self.assertEqual(self.report['fixedMethodProjection']['source']['methodStart'],10287)
        self.assertEqual(self.report['fixedMethodProjection']['source']['metadataSHA256'],'00b9a09dbe29589ee7a40b147d587b1cdbbd1f4c1e8eee101a9d886a500837f7')

    def test_only_two_direct_windows_are_admitted_and_readers_match_original_instructions(self):
        expected={'0x14051e2c0':('0x14051e718','1771e00105e3fcdc2d06a1c942e0002bbb417ce9b3b8444fa8c1ffbaf5c8eeef'),
                  '0x1404eea50':('0x1404efc2e','b92006506acefc36c21a50d0ae69e021ee7e249e88ded04f5267d0e18e3c70c2')}
        for cold in self.cases:
            self.assertEqual(cold['extension']['windowRequests'],[])
            self.assertEqual({r['entry']:(r['end'],r['sha256']) for r in cold['extension']['reviewedWindows']},expected)
        methods=self.cases[1]['functions']['0x14051e2c0'];inflate=self.cases[1]['functions']['0x1404eea50']
        self.assertEqual((methods['end'],methods['sha256']),expected['0x14051e2c0']);self.assertEqual((inflate['end'],inflate['sha256']),expected['0x1404eea50'])
        instructions={r['address']:r['instruction'] for r in methods['instructions']+inflate['instructions']}
        self.assertEqual(instructions['0x14051e5d6'],'mov dword ptr [rbx + 0x18], r8d')
        self.assertEqual(instructions['0x1404eeb80'],'mov r12, qword ptr [rip + 0x56d8709]')
        self.assertEqual(instructions['0x1404eeb87'],'mov rsi, qword ptr [rip + 0x56d8722]')
        self.assertEqual(instructions['0x1404eeb8e'],'mov rax, qword ptr [rip + 0x56d8723]')

    def test_epoch_and_completion_boundaries_stay_false_and_current_sources_are_bound(self):
        for flag in ('completeColdComparer','completeClassInitialization','defaultComparerWriteExecuted','completeAvatarHandler','targetProtocolReady','d02Complete','sameProcessReplay'):
            self.assertIs(self.report[flag],False)
        self.assertEqual(self.report['budget']['caseGroups'],2);self.assertEqual(self.report['budget']['newWindowCount'],2)
        self.assertEqual(self.report['inputSHA256'],card.frozen.FIVE_INPUTS)
        self.assertEqual(self.report['interfaceBridge']['inputSHA256'],card.INPUT_SHA);self.assertFalse(self.report['interfaceBridge']['freshRegistryObserved'])
        self.assertEqual(self.report['sourceSHA256'],hashlib.sha256(Path(card.__file__).read_bytes()).hexdigest())
        self.assertEqual(hashlib.sha256((card.REPO/card.INPUT).read_bytes()).hexdigest(),card.INPUT_SHA)


if __name__=='__main__':unittest.main()
