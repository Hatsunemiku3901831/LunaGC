"""Bounded native Avatar slot lookup and Weapon GUID fallback, no live client."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
except ImportError:
    import client72_avatar_deep as base

INPUTS = {'avatar-deep-observations.json':'fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7',
          'avatar-deep-suffix-observations.json':'5e03ba3486470055450cc4233f8cf3a500680569d874e5e8b1e1efad5b9e58c6'}
ENTRIES = (0x151845920, 0x15183E450)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('client','output','observations'):parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--python-libs',type=Path,default=Path('local/migration72/python'))
    args=parser.parse_args()
    if args.output.resolve()==args.observations.resolve() or any(path.resolve().is_relative_to(args.client.resolve()) for path in (args.output,args.observations)):
        parser.error('Distinct outputs outside client required')
    for name,digest in INPUTS.items():
        if hashlib.sha256((Path('protocol/cn-7.2')/name).read_bytes()).hexdigest()!=digest:raise ValueError('Reviewed Avatar source changed')
    b,m,s=[base.read_verified(args.client/name,digest) for name,digest in base.HASHES.items()]
    records,_=base.extract(b,m,startup=s,structures=True);cells=base.type_cells(b,m,s,records)
    declared=declarations(b,records,args.python_libs)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    rows=probe(b,m,s,records,cells,args)
    root=Path(__file__).resolve().parents[1]
    report=dict(task='F-03d3f4d2-equipment-lookup',inputSHA256=base.HASHES,inputContracts=INPUTS,declarations=declared,native=rows,
        completeSlotLookup=True,completeEquipmentAssignment=False,completeAvatarHandler=False,completeConstructor=False,
        runtimeStatus='not-tested',targetProtocolReady=False,
        sourceSHA256={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in (
            'tools/client72_avatar_deep_equipment.py','tools/client72_avatar_deep.py','tools/client72_avatar_initializer.py',
            'tools/client72_avatar_contract.py','tools/client72_emulator.py','tools/client72_session_runtime.py')},
        models=['Managed model classes and initialized state are explicit fixtures based on metadata inheritance; no equipment model ctor or resource parser is asserted.',
            'GUID setter, manager registration/query and UInt64 dictionary bodies are inherited native execution; +68 list storage is an explicit prepared managed list.',
            'Slot lookup and +248 GUID setter/fallback execute fully; equipment factory/assignment/list mutation/refresh remain separate.'])
    args.output.write_text(json.dumps(report,indent=2)+'\n','utf8')
    args.observations.parent.mkdir(parents=True,exist_ok=True)
    args.observations.write_text(json.dumps(compact(report),indent=2)+'\n','utf8')
    print(json.dumps(dict(nativeCases=len(rows['fixtures']),registrations=len(rows['registrations']),completeSlotLookup=True,completeEquipmentAssignment=False)))


def declarations(binary,records,libraries):
    base.load_emulator(libraries)
    from capstone import Cs,CS_ARCH_X86,CS_MODE_64
    pe=base.PE(binary);decoder=Cs(CS_ARCH_X86,CS_MODE_64)
    optional=base.number(binary,0x3c)+24;rva,size=struct.unpack_from('<II',binary,optional+112+3*8)
    ranges={pe.base+a:pe.base+z for a,z,_ in struct.iter_unpack('<III',binary[pe.offset(pe.base+rva):pe.offset(pe.base+rva)+size])}
    functions={}
    for entry in ENTRIES:
        code=binary[pe.offset(entry):pe.offset(entry)+ranges[entry]-entry]
        functions[hex(entry)]=dict(end=hex(ranges[entry]),nativeBytesSHA256=hashlib.sha256(code).hexdigest(),
            instructions=[dict(address=hex(ins.address),instruction=ins.mnemonic+' '+ins.op_str) for ins in decoder.disasm(code,entry)])
    hierarchy=[dict(typeIndex=index,parent=records[index]['parent']) for index in (51522,87122,25683)]
    if any(row['parent'].get('typeDefinition')!=25683 for row in hierarchy[:2]):raise ValueError('Equipment inheritance changed')
    return dict(functions=functions,hierarchy=hierarchy,
                guidGetter=next(method for method in records[72120]['methods'] if method['virtualSlot']==25))


def probe(binary,metadata,startup,records,cells,args):
    directory=Path('protocol/cn-7.2');declared=base.initializer_declarations(binary,metadata,startup,records,cells,args.python_libs)
    e=base.Offline72(binary,args.python_libs,records,trace_instructions=False)
    g=base.AvatarContractGraph(e,directory,cells,base.core_aot(binary,metadata,startup,records),
        base.avatar_aot(binary,metadata,startup,records,dict(types=[])),json.loads((directory/'avatar-outer-observations.json').read_text('utf8')))
    rt=None;rows=[]
    try:
        avatar_guid=0x8000000000000001
        g.run_graph(30509,base.message(6,base.avatar_wire(1001,avatar_guid,[],[])))
        rt=base.DeepRuntime(e,g,declared,json.loads((directory/'avatar-deep-observations.json').read_text('utf8')))
        e.models[0x15184E100]=rt.deliver;rt.root();e.models.pop(0x15184E100,None)
        model,_=rt.delivery[0];rt.constructor_fragments(model)
        for entry in ENTRIES:e.allow(entry);e.models.pop(entry,None)
        base_class=e.synthetic_type(0x14589B530);e.write(base_class+0xc4,1,'B')
        models={};registrations=[]
        for typ,guid,slot in ((51522,0xffffffffffffffff,6),(51522,0x200000001,6),(87122,0x100000001,1)):
            klass,hierarchy=e.alloc(1024),e.alloc(32)
            e.write(klass+0xc4,2,'B');e.write(klass+0x48,hierarchy);e.write(hierarchy,base_class);e.write(hierarchy+8,klass)
            e.write(klass+0x198,0x148AA58E0)
            pointer=e.alloc(256);e.write(pointer,klass);e.write(pointer+0x48,slot,'I')
            setter=rt.invoke(0x148AA58D0,rcx=pointer,rdx=guid)
            registration=rt.invoke(0x14E9B2A80,rcx=rt.manager,rdx=pointer)
            registrations.append(dict(type=typ,guid=guid,slot=slot,setter=setter,registration=registration));models[guid]=pointer
        listing=e.read(model+0x68);array=rt.array(8,8);e.write(listing+0x10,array)
        for name,guids,slot in (
            ('missing-and-wrong-class-before-weapon',[999,avatar_guid,0xffffffffffffffff,0x100000001],6),
            ('reliquary-slot',[0xffffffffffffffff,0x100000001],1),
            ('first-matching-weapon',[0x200000001,0xffffffffffffffff],6),
            ('missing-slot',[0x200000001,0x100000001],7),('empty',[],6)):
            e.write(listing+0x18,len(guids),'I')
            for index,guid in enumerate(guids):e.write(array+0x20+index*8,guid)
            trace=rt.invoke(0x151845920,rcx=model,rdx=slot);result=e.reg('rax')
            rows.append(dict(name='lookup-'+name,guids=guids,slot=slot,
                foundGuid=e.read(result+0x38) if result else None,trace=trace))
        for name,value,guids in (('explicit-high',0x8000000000000099,[]),
                                ('zero-finds-first',0,[0x200000001,0xffffffffffffffff]),('zero-no-weapon',0,[0x100000001])):
            e.write(listing+0x18,len(guids),'I')
            for index,guid in enumerate(guids):e.write(array+0x20+index*8,guid)
            e.write(model+0x248,777)
            trace=rt.invoke(0x15183E450,rcx=model,rdx=value)
            rows.append(dict(name='guid-fallback-'+name,inputGuid=value,guids=guids,stored=e.read(model+0x248),trace=trace))
        e.write(model+0x68,0)
        trace=rt.invoke(0x151845920,{0x1518459DA},rcx=model,rdx=6)
        rows.append(dict(name='null-list-before-native-error',trace=trace))
        return dict(registrations=registrations,fixtures=rows)
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=rows,calls=e.calls,
            registers={name:hex(e.reg(name)) for name in ('rip','rcx','rdx','r8','rsp')}),indent=2)+'\n','utf8');raise
    finally:
        if rt:rt.close()
        g.close();e.close()


def compact(value):
    if isinstance(value,list):return [compact(item) for item in value]
    if isinstance(value,dict):
        if 'executedAddresses' in value:
            return {key:value[key] for key in ('entry','stop','completion','liveIn','instructions','nativePathSHA256','models')} | {
                'nativeEntries':sorted(({hex(x) for x in ENTRIES}|{'0x14e9b2c60','0x14e9b2a80','0x156f6f510','0x156f6f850',value['entry']})&set(value['executedAddresses']))}
        return {key:compact(item) for key,item in value.items() if key!='instructions' or not isinstance(item,list)}
    return value


if __name__=='__main__':main()
