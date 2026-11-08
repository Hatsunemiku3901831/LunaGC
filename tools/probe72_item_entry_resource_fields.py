"""Fixed-source birth Weapon affix and unresolved Gadget bridge; offline only."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import pickle
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import client72_d02_promote_resource as prior
from tools.client72_emulator import GLOBAL, STACK
from tools.client72_resource_deep import RETURN
from tools.extract_client72 import HEADER_VA, CODE_REGISTRATION, TypeResolver, number

RAW = ROOT / 'local/migration72/d02-resume-20261109/r3-item'
EQUIPMENT_REPORT = prior.equip.RAW / 'main-third.json'
AFFIX_CONSUMER, GROUP_GETTER = 0x150758e60, 0x152b8ef00
COUNT, ITEM, ARRAY_GET = 0x159c6ca60, 0x15480d060, 0x1404c2610
FIELD2C_GETTER = 0x148446d40
CONTROL_ITEM = 11501
PINS = {
    'tools/client72_d02_equipment_resource.py': '0fe44e0c7cf684ed508dd2105695f1aeefe477632158bc8e1b98c9504cef4af9',
    'tools/client72_d02_promote_resource.py': '8d60647478e24defe3b550c74d0f17474517450b29cfb9e8630cc6f82d88cbde',
    'tools/trace_client72_avatar_signed_trees.py': '511e0d8ef2baa9bb12f0d9f6150b24f78e3f1815d4e7b112d4158ba52056e240',
    'local/migration72/d02-resume-20261108/r3-equipment/main-third.json': '50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc',
    'local/migration72/d02-active/main/scene-runtime-context-third.json': 'bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4',
    'local/migration72/d02-active/scene-runtime-context-astra/Gadget.native-m_bytes.bin': '6355185856a095aea5d523a8465029b2c40c61d5de055e53fc0412a6472dcf2c',
    'local/migration72/d02-active/scene-runtime-context-astra/GadgetPrimaryIndex.native-m_bytes.bin': '1e95ae771d6597160b2438d5a1ead40f04f58a84a26be466c0226fab6923138f',
    'local/migration72/d02-active/scene-runtime-context-astra/Gadget50011101.native-row.bin': 'eaf903171c14cfd27367b0bc7012ef7036671d4b7bb0d6c3723d237e6a17b9ad',
    'local/migration72/d02-resume-20261109/r3-item/array-aot.private.json': 'f37757a3a77529837a3f7530bd152044dc351fc3a476f78c89b8c3237230c9f3',
    'local/migration72/d02-resume-20261109/r3-item/array-interface-types.private.json': 'af18269db7fe7434b1f81c78667fd2d99a9ca4e720738f9bcc8e0fccbcdf098f',
    'local/migration72/d02-resume-20261109/r3-item/array-runtime-windows.private.json': '19140624f7ff35761fad947ef2e3e461e8efc03bd05b0463673f040906653045',
    'local/migration72/d02-resume-20261109/r3-item/field2c-direct-callers.private.json': 'a5338d955b09046ec0b16f3a9625b7955c763aae07dc722e474e6078745f932f',
    'local/migration72/d02-resume-20261109/r3-item/gadget-cache-and-field2c-sites.private.json': '980ea28bf05bcfab5d17337c1efca7dbbec302760286800befeb319815af4e33',
    'local/migration72/d02-resume-20261109/r3-item/gadget-getter-direct-callers.private.json': '4ed725128724bde7d723e10773f682e9aefac12b679287bc2144dbe967fff791',
    'local/migration72/d02-resume-20261109/r3-item/gadget-getter-selected-callers.private.json': '20b4b1b613e8ed3d3bab8ab399e1ee5307ea2a304c72920ead9e7248cabefea3',
    'local/migration72/d02-resume-20261109/r3-item/known-affix-consumer.private.json': '296e5be05377a7203a9f1bbd914d6b21ebacf8b6c2ac58ddcd638076470d25ea',
    'local/migration72/d02-resume-20261109/r3-item/typed-item-field2c-accessors.private.json': '62fa55c2a751b24da75140b1d64fcd38d95f2fcf643f0e1860317445cc2c0638',
    'local/migration72/d02-resume-20261109/r3-item/weapon-model-field2c-consumers.private.json': '5ce8ae3eae013571b41bfe4a252b5cfddaf38ccef916cd799b986593a16879b6',
    'local/migration72/d02-resume-20261109/r3-item/weapon-model-field2c-sites.private.json': '01cb211bacade0caa938c6aeca18952e4e2cf4adefcea32f9e4a340ac5e3bbf4',
    'local/migration72/d02-resume-20261109/r3-item/probe01.failure.json': '1d1490a39f7c3907e6e0c1edaa5c86da7fec2e33e5a2e433956f3a86b97ea2d5',
    'local/migration72/d02-resume-20261109/r3-item/probe02.failure.json': 'dc8cae86337dddccf2f2619639eb249c8da5b737edbbfc0668ed4ce95bc115d2',
    'local/migration72/d02-resume-20261109/r3-item/probe03.json': '24e72acd915753c12894d6a4c36dca8d87a9b68609e66ebe01017ea0a5f66f01',
    'local/migration72/d02-resume-20261109/r3-item/probe01-source.private.py': '8323c664ebda2d8e1ef99162929ffda4380894c5e02e879f52fb6f6424b4a92f',
    'local/migration72/d02-resume-20261109/r3-item/probe02-source.private.py': '9abedebb5366a9f9d3484cf7ae42f974eb95ae85c72b7bf5901482da6dc86e1e',
    'local/migration72/d02-resume-20261109/r3-item/probe03-source.private.py': '4888b2e4239884591787de85a4d7692be0a39349372150779d202616b1f473c2',
    'src/main/java/emu/grasscutter/game/inventory/GameItem.java': 'e9de522816d9400c1cd3a3ba3ae9ca9ff6691365ceee1ecb8d534c4a03cef132',
    'src/main/java/emu/grasscutter/game/avatar/AvatarStorage.java': '6d6b07102d5f8b9f95ebbd4e18d1489f9143d502100dcfaadff0235db138e784',
    'src/main/java/emu/grasscutter/game/avatar/Avatar.java': '9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d',
    'src/main/java/emu/grasscutter/game/entity/EntityWeapon.java': '67e74b4ef6bc5e0e8f2ef91effa69065a07d6692f55bf6185d49341c400a2d7e',
    'src/main/java/emu/grasscutter/server/packet/send/PacketSceneTeamUpdateNotify.java': '9d90c0b79a6d46cfee1c8f124ac11265d0dead187179db7d889ed2c86c29975d',
    'src/main/java/emu/grasscutter/data/excels/ItemData.java': 'ec5ec94f2cf7ed4be3cdc7a0249004115ec149a988e19529bd53315016f02c93',
    'src/main/java/emu/grasscutter/data/excels/GadgetData.java': 'f09705e376ebcacbb431891cdb368ffb51136911ffae2efa3ec9755c005fa81b',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('Require a fresh report within this card private directory')
    pins, _ = prior.equip.verify_inputs(args.client)
    for name, digest in PINS.items():
        prior.card.base.read_verified(ROOT / name, digest)
    pins.update(PINS)
    binary, metadata, startup = [(args.client / name).read_bytes() for name in list(prior.card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((prior.equip.RAW / 'metadata.private.pkl').read_bytes())
    table = next(t for t in json.loads(EQUIPMENT_REPORT.read_bytes())['tables'] if t['name'] == 'Weapon')
    codec = prior.card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = ItemRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    result = dict(schemaVersion=1, task='E-01/R3-ITEM-ENTRY', sourcePins=pins,
        sourceSHA256=sha(Path(__file__).read_bytes()),
        testSourceSHA256=sha((ROOT / 'tools/test_probe72_item_entry_resource_fields.py').read_bytes()))
    try:
        result['staticEvidence'] = static_evidence(rt)
        result['arrayMethodAOT'] = array_aot(rt)
        result['preliminaryExecutions'] = preliminary_executions()
        result['selectedRows'] = rows = parse_selected(rt, table)
        group_one=rt.call_instruction_total
        result['arrayInterfaceFixture'] = install_array_interface(rt, rows)
        result['affixConsumers'] = affix_consumers(rt, rows)
        group_two=rt.call_instruction_total
        result['controlledNegatives'] = negatives(rt, rows)
        result['javaProjectionFragments'] = [project_item(row) for row in rows if row['key'] == 11101]
        result.update(nativeSources=rt.functions, nativeCallTotals=rt.call_totals,
            nativeCallInstructionHookVisits=rt.call_instruction_total,
            nativeCalls=rt.item_calls,
            experimentGroupInstructionHookVisits=dict(selectedRowsAndReadout=group_one,
                actualArrayConsumerSlices=group_two-group_one,
                controlledNegatives=rt.call_instruction_total-group_two),
            nativeConfigTypedFixture=rt.config_proof, scalarStorageCodec=codec['proof'],
            allowedRangeCompaction=rt.allowed_range_compaction,
            gadgetIdAliasApproved=False, skillAffixAliasApproved=True,
            newDirectFunctionWindows=8, nativeExperimentGroups=3, eachCallLimit=2_000_000,
            fullTableParsingNewlyExecuted=False, fullAffixConsumerExecuted=False,
            actualPositiveAffixReferenceLoaded=False, completeItemInputRow=False,
            javaResourcesLoaded=False, resourceReady=False, activeSelectedRevision=None,
            activeRevisionVerified=False, d02Complete=False,
            unresolved=[
                'Item base88968 field442107/+2c getter148446d40 is decoded but lacks a typed Item-to-Gadget semantic consumer; gadgetId alias is refused.',
                'Gadget50011101 actual92-byte row and primary/full payload are already available from frozen Scene evidence. Its existence/value equality is not proof that Item+2c selects it.',
                'Gadget jsonName/type-to-ConfigEntityGadget, ability and controller inputs are not approved by this card. Complete Item/entity/entry permission is withheld.',
                'Independent nonzero Weapon11501 reaches the exact named EquipAffix group getter; actual positive group/level rows and Java refinement-ID alignment are not supplied or exported.',
            ],
            modelScope=[
                'Only two actual Weapon rows are re-parsed at prior whole-parser spans; no unpacking, new archive index or whole-table parsing occurs. Old equipment/Curve/Promote and Java results are reused, not re-counted.',
                'Original constructors, row parsers, uint/float codecs, System.Array Count/get_Item and known affix consumer slices execute. Typed storage, memory libraries, allocation, metadata/RGCTX and reviewed scalar initialization remain explicit inherited fixtures.',
                'The exact encoded58209[] class receives a declared rank1/stride4 and ICollection/IList interface table pointing to exact registered Array AOT methods. This is a storage/dispatch fixture, not natural CLR class initialization. No Python count/index/result callback is installed.',
                'Consumer slices15075934f..152b8ef00 start with explicit actual Weapon pointer, exact metadata-cell87097 class, typed29353 model, slot index and original R14=RSP+20 stack scratch. Each stops before the named EquipAffix getter; no row/lookup/level result is fabricated. The default-affix branch is selected explicitly, not naturally entered from the full Weapon model method.',
                'Negative empty EquipAffix cache is exact declared Dictionary storage with no entries; its native missing-group result is a controlled Java/target behavior probe, never permission to omit positive references. Array bounds/null/count exits are stopped before exception/later code.',
                'All successful calls and selected native readout costs are counted; hooks include unexecuted exact stop visits. First failures retain current-call hook count separately. Full source raw64-bit/enum/float32/list/null data are retained; token-level omission for every scalar is not separately recovered.',
            ])
    except Exception as error:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(error=str(error), partial=result,
            completedSelectedRows=getattr(rt,'item_rows',[]),
            nativeFailure=rt.e.failure, nativeCallTotals=rt.call_totals,
            completedCallInstructionHookVisits=rt.call_instruction_total,
            currentCallInstructionHookVisits=rt.e.steps,
            currentCallAlreadyIncludedInCompletedTotal=rt.last_item_call_completed,
            nativeCalls=rt.item_calls,
            registers={k:hex(rt.e.reg(k)) for k in ('rip','rsp','rcx','rdx','r8','r9','rax')},
            nativeSources=rt.functions), indent=2, sort_keys=True)+'\n', 'utf8')
        raise
    finally:
        rt.e.close()
    prior.equip.verify_client(args.client)
    result['fiveInputsRecheckedAfterNative'] = True
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True)+'\n', 'utf8')
    print(json.dumps(dict(task=result['task'], rows=len(rows), affixSlots=len(result['affixConsumers']),
        instructionHookVisits=result['nativeCallInstructionHookVisits'], gadgetIdApproved=False, resourceReady=False)))


class ItemRuntime(prior.PromoteRuntime):
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        self.item_calls=[]
        self.last_item_call_completed=True

    def call(self,entry,stops=(),**registers):
        self.last_item_call_completed=False
        trace=super().call(entry,stops=stops,**registers)
        self.last_item_call_completed=True
        self.item_calls.append({key:trace[key] for key in ('entry','stop','instructions')})
        return trace


def preliminary_executions():
    result=[]
    for name in ('probe01.failure.json','probe02.failure.json','probe03.json'):
        value=json.loads((RAW / name).read_bytes())
        failed='partial' in value
        total=value['completedCallInstructionHookVisits' if failed else 'nativeCallInstructionHookVisits']
        calls=sum(x['calls'] for x in value['nativeCallTotals'].values())
        result.append(dict(report=name,reportSHA256=sha((RAW / name).read_bytes()),
            sourceSHA256=(value['partial'] if failed else value)['sourceSHA256'],
            rejected=failed,error=value.get('error'),completedNativeCalls=calls,
            completedInstructionHookVisits=total,
            additionalUncountedFailedNativeInstructions=0,
            failureScope='Host assertion after completed native call; last current-call hooks are already in completed total.' if failed else None))
    if sum(x['completedNativeCalls'] for x in result)!=291 or sum(x['completedInstructionHookVisits'] for x in result)!=28555:
        raise ValueError('Preliminary native accounting changed')
    return result


def static_evidence(rt):
    windows = []
    windows += json.loads((RAW / 'typed-item-field2c-accessors.private.json').read_bytes())
    windows += json.loads((RAW / 'weapon-model-field2c-consumers.private.json').read_bytes())
    windows += [x['window'] for x in json.loads((RAW / 'array-aot.private.json').read_bytes())['matches']]
    windows += json.loads((RAW / 'array-runtime-windows.private.json').read_bytes())
    if len({x['start'] for x in windows}) != 8:
        raise ValueError('Direct static-window budget changed')
    known = json.loads((RAW / 'known-affix-consumer.private.json').read_bytes())
    for window in [*windows, known]:
        start, end = int(window['start'],16), int(window['end'],16)
        raw = rt.binary[rt.e.pe.offset(start):rt.e.pe.offset(start)+end-start]
        if sha(raw) != window['sha256'] or rt.extents[start] != end:
            raise ValueError('Exact reviewed native window changed')
        actual = [dict(address=hex(i.address),size=i.size,instruction=i.mnemonic+' '+i.op_str) for i in rt.decoder.disasm(raw,start)]
        if actual != window['instructions']:
            raise ValueError('Exact reviewed native instructions changed')
    return dict(newWindows=windows, reusedAffixConsumer=known,
        field2cDirectCallers=json.loads((RAW / 'field2c-direct-callers.private.json').read_bytes()),
        gadgetDirectCallSites=json.loads((RAW / 'gadget-getter-direct-callers.private.json').read_bytes()),
        gadgetInlineCacheFilter=json.loads((RAW / 'gadget-cache-and-field2c-sites.private.json').read_bytes()),
        ruledOutModelProtoConsumers=['0x150758750','0x1507581a0'],
        filterIsNotExhaustiveProof=True)


def array_aot(rt):
    b,m,s = rt.binary,rt.metadata,rt.startup
    pe=rt.e.pe;h=pe.offset(HEADER_VA);cr=pe.offset(CODE_REGISTRATION)
    resolver=TypeResolver(pe,s,rt.records,h)
    specs=0x210+(number(b,h+0x170)^0x6347382B)
    spec_count=(number(b,h+0x24)^0x323BAE70)//12
    table=0x210+(number(b,h+0xEC)^0x3CF0720C)
    size=(number(b,h+0x110)+0xA3D9CC8B)&0xffffffff
    count=(number(b,cr+0x64)+0x9EA91E3B)&0xffffffff
    pointers=pe.offset(number(b,cr+0x30,'Q'),count*8)
    rows=[]
    for i in range(size//14):
        pointer,spec=struct.unpack_from('<ii',m,table+i*14)
        if not 0<=pointer<count or not 0<=spec<spec_count:
            raise ValueError('AOT bounds')
        method,cls,args=struct.unpack_from('<iii',m,specs+spec*12)
        if method not in (999,1004):
            continue
        n,p=struct.unpack_from('<QQ',b,pe.offset(resolver.instances+args*16,16))
        if not 0<n<=255:
            raise ValueError('Array generic arguments')
        decoded=[resolver.resolve_index(resolver.pointer_index(number(b,pe.offset(p+j*8,8),'Q'))) for j in range(n)]
        if not any(x.get('typeDefinition')==58209 for x in decoded):
            continue
        rows.append(dict(methodDefinition=method,classInstance=cls,methodArgumentInstance=args,
            tableIndex=i,methodSpec=spec,pointerIndex=pointer,
            address=hex(number(b,pointers+pointer*8,'Q')),arguments=decoded))
    expected=[{k:v for k,v in x.items() if k!='window'} for x in json.loads((RAW / 'array-aot.private.json').read_bytes())['matches']]
    if rows != expected or [x['address'] for x in rows] != ['0x1547ecd00',hex(ITEM)]:
        raise ValueError('Exact encoded58209 Array specialization changed')
    return rows


def parse_selected(rt, table):
    e=rt.e;blob=(prior.equip.SOURCE / 'Weapon.native-m_bytes.bin').read_bytes()
    if sha(blob)!=table['payloadSHA256']:
        raise ValueError('Actual Weapon source changed')
    parser=0x154498500
    rt.reader,rt.row_entry=e.alloc(64),parser
    data=rt.array(len(blob),1);e.uc.mem_write(data+0x20,blob);e.write(rt.reader+0x10,data)
    result=rt.item_rows=[]
    for key in (11101,CONTROL_ITEM):
        original=next(x for x in table['rows'] if x['key']==key);span=original['source']
        if not 4<=span['begin']<span['end']<=len(blob) or sha(blob[span['begin']:span['end']])!=span['sha256']:
            raise ValueError('Selected Weapon source span changed')
        p=prior.equip.object_storage(rt,87097)
        declared=rt.cells[0x1458aa7e8]
        e.write(p,rt.bind_type(declared['type'],declared['sourceIndex']))
        ctor=rt.call(0x15449a920,rcx=p)
        e.write(rt.reader+0x1c,span['begin'],'I');parsed=rt.call(parser,rcx=p,rdx=rt.reader)
        if ctor['stop']!=hex(RETURN) or parsed['stop']!=hex(RETURN) or e.read(rt.reader+0x1c,'I')!=span['end']:
            raise ValueError('Selected original Weapon parser did not consume exact row')
        begin=rt.call_instruction_total;fields=rt.snapshot_object(p,87097,0)
        actual=next((x for x in table['selected'] if x['key']==key),None)
        if actual is not None and fields!=actual['fields']:
            raise ValueError('Selected Weapon logical field replay changed')
        base=e.read(p+0x10);back=e.read(p+0x20);array=e.read(back+0x40)
        if base!=back or rt.decode(back+0x28)!=key or not array:
            raise ValueError('Actual Weapon backing/ID/affix array differs')
        field=rt.call(FIELD2C_GETTER,rcx=p);field2c=e.reg('eax')
        count=e.read(array+0x18,'I');raw=[e.read(array+0x20+i*4,'I') for i in range(count)]
        logical=[rt.decode(array+0x20+i*4) for i in range(count)]
        result.append(dict(key=key,pointer=hex(p),source=span,constructor=ctor,parser=parsed,
            fields=fields,readoutInstructionHookVisits=rt.call_instruction_total-begin,
            field2c=dict(owner=88968,fieldIndex=442107,offset='0x2c',encodedBits=f'{e.read(back+0x2c,"I"):08x}',
                logicalValue=field2c,getter=field,gadgetIdAliasApproved=False),
            affixArray=dict(owner=20600,fieldIndex=97497,offset='0x40',pointer=hex(array),
                count=count,elementType=58209,encodedBits=[f'{x:08x}' for x in raw],logicalValues=logical,
                present=True,logicalEmpty=count==0,individualTokenPresenceRecovered=False),
            fullSourceTokenPresenceRecovered=False))
    if not any(value>0 for value in result[1]['affixArray']['logicalValues']):
        raise ValueError('Independent actual Weapon11501 is not a nonzero affix control')
    return result


def install_array_interface(rt, rows):
    e=rt.e;rt.bind_function(AFFIX_CONSUMER)
    interfaces=[]
    for cell,definition in ((0x1458978f0,1244),(0x1458a2e78,1250)):
        typ=rt.cells[cell]['type']
        if typ.get('typeDefinition')!=definition or [x.get('typeDefinition') for x in typ['arguments']]!=[58209]:
            raise ValueError('Affix exact ICollection/IList type changed')
        interfaces.append(e.read(cell))
    classes={e.read(int(row['affixArray']['pointer'],16)) for row in rows}
    if len(classes)!=1 or rt.type_records[next(iter(classes))]['kind']!='szarray':
        raise ValueError('Actual affix arrays lack one typed encoded58209[] class')
    klass=next(iter(classes));table=e.alloc(32)
    e.write(klass+0x80,table);e.write(klass+0xc0,2,'H');e.write(klass+0xbe,2,'H')
    e.write(klass+0xc8,2,'I');e.write(klass+0xb8,4,'H')
    for slot,(interface,native) in enumerate(zip(interfaces,(COUNT,ITEM))):
        info=e.alloc(64);e.write(info,klass);e.write(info+8,native)
        e.write(table+slot*16,interface);e.write(table+slot*16+8,slot,'I')
        e.write(klass+0xd0+slot*8,native);e.write(klass+0xd0+(slot+2)*8,info)
    return dict(type=rt.type_records[klass],rank=1,stride=4,interfaceFixture=True,
        interfaces=[dict(cell=hex(cell),type=rt.cells[cell]['type'],native=hex(native))
                    for cell,native in ((0x1458978f0,COUNT),(0x1458a2e78,ITEM))],
        countMethodDefinition=991,itemMethodDefinition=1004,itemMethodArgumentInstance=3826,
        naturalCLRInitializationExecuted=False,pythonCountOrIndexCallback=False)


def affix_consumers(rt, rows):
    e=rt.e;result=[]
    for row in rows:
        p=int(row['pointer'],16);model=prior.equip.object_storage(rt,29353);e.write(model+0x30,p)
        for index,expected in enumerate(row['affixArray']['logicalValues']):
            trace=rt.call(0x15075934f,stops=(GROUP_GETTER,0x15075964d,0x15075965f),
                rax=p,rsi=model,rbp=index,r14=STACK+0x100028)
            if trace['stop']!=hex(GROUP_GETTER) or e.reg('ecx')!=expected:
                raise ValueError('Actual array affix group/order differs at named getter entry')
            executed=trace['executedAddresses']
            if not all(hex(address) in executed for address in (COUNT,ITEM,ARRAY_GET,0x14aef9db0)):
                raise ValueError('Affix consumer bypassed original Count/get_Item/uint decoder')
            result.append(dict(itemId=row['key'],index=index,group=expected,source=row['source'],
                trace=trace,actualArray=True,stoppedBeforeGroupLookup=True,
                groupGetter=hex(GROUP_GETTER),actualPositiveAffixRowSupplied=False))
    return result


def negatives(rt, rows):
    e=rt.e;row=rows[0];p=int(row['pointer'],16);array=int(row['affixArray']['pointer'],16)
    result=[];model=prior.equip.object_storage(rt,29353);e.write(model+0x30,p)
    trace=rt.call(0x15075934f,stops=(GROUP_GETTER,0x15075964d),
        rax=p,rsi=model,rbp=row['affixArray']['count'],r14=STACK+0x100028)
    if trace['stop']!='0x15075964d' or hex(ITEM) in trace['executedAddresses']:
        raise ValueError('Affix count boundary did not avoid native index access')
    result.append(dict(name='consumer-at-count',trace=trace,lookupAttempted=False))
    trace=rt.call(ITEM,stops=(0x1404c2650,),rcx=array,rdx=row['affixArray']['count'])
    if trace['stop']!='0x1404c2650':
        raise ValueError('Original Array boundary did not reach exact error entry')
    result.append(dict(name='native-array-out-of-range',trace=trace,exceptionEntryOnly=True))
    back=e.read(p+0x20);e.write(back+0x40,0)
    try:
        trace=rt.call(0x15075934f,stops=(GROUP_GETTER,0x15075965f),rax=p,rsi=model,rbp=0,r14=STACK+0x100028)
        if trace['stop']!='0x15075965f':
            raise ValueError('Null affix array did not reach its actual caller branch')
        result.append(dict(name='controlled-null-affix-array',trace=trace,targetOmissionPermission=False))
    finally:
        e.write(back+0x40,array)
    typ=next(f['type'] for f in rt.records[45983]['fields'] if f['rawOffset']=='0x400dfd8')
    cache=e.alloc(0x48);e.write(cache,rt.bind_type(typ));e.write(cache+0x3c,0xffffffff,'I')
    e.write(GLOBAL+0xdfd8,cache)
    for key in (0,next(v for v in rows[1]['affixArray']['logicalValues'] if v>0),0xffffffff):
        trace=rt.call(GROUP_GETTER,rcx=key)
        if trace['stop']!=hex(RETURN) or e.reg('rax')!=0:
            raise ValueError('Controlled empty real typed EquipAffix query did not return null')
        result.append(dict(name='controlled-missing-affix-group',key=key,trace=trace,returnedNull=True,
            cacheFixture=True,actualPositiveReferenceClosed=False,omissionPermission=False))
    return result


def project_item(row):
    if type(row['key']) is not int or row['key']!=11101:
        raise ValueError('Only source-approved birth Item11101 is exported by this card')
    values=row['affixArray']['logicalValues']
    if not row['affixArray']['present'] or type(values) is not list or not values:
        raise ValueError('Do not collapse missing/null/empty affix storage into observed zero slots')
    if any(type(x) is not int or not 0<=x<=0x7fffffff for x in values):
        raise ValueError('Affix uint32 is not an exact Java int')
    if any(values):
        raise ValueError('Positive affix reference requires actual rows and Java group/refinement contract')
    return dict(table='Weapon',key=row['key'],source=row['source'],
        javaFields=dict(id=row['key'],skillAffix=list(values)),fragmentOnly=True,
        sourceListOrderAndZeroSlotsPreserved=True,gameItemPositiveAffixes=[],
        gadgetIdAliasApproved=False,completeItemInputRow=False,
        omittedUnknownFields=['gadgetId','Gadget/config/ability necessities'],
        omissionsAreNotTargetDefaults=True)


if __name__=='__main__':
    main()
