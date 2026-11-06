"""CN72 bounded Equip, Weapon and Reliquary native-consumer contract.

Typed factories and independent named resource/actor-slot consumers execute.
Managed storage, resource rows, primitive-codec initialization and the explicitly
separated role-node delivery boundary are fixtures, not live initialization.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from .client72_session_runtime import SessionRuntime72, fragment, permutation_table
    from .trace_client72_inventory_graph import InventoryGraph72
    from .client72_graph_fixtures import scalar, message
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from client72_session_runtime import SessionRuntime72, fragment, permutation_table
    from trace_client72_inventory_graph import InventoryGraph72
    from client72_graph_fixtures import scalar, message

RETURN = END + 0x100
RAW_SHA = 'ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18'
BASE_SHA = '130f6387005ad3b5cf0e2dc2e7c73b930e698fa07f03a7fe8b48fefe87dc16db'
FIELDS = [(65333, 6, 'equip'), (44169, 1, 'reliquary'), (44169, 2, 'weapon'), (44169, 3, 'is_locked'),
          (64947, 1, 'level'), (64947, 2, 'exp'), (64947, 3, 'promote_level'), (64947, 4, 'affix_map'),
          (74063, 1, 'level'), (74063, 6, 'is_star')]
UNKNOWN = {'64947': [5, 6], '74063': [2, 3, 4, 5, 7, 8]}
# path, metadata owner, static dictionary offset, getter, loader, literal index/cell
RESOURCES = [
    ('Weapon', 71245, 0x34900, 0x154177230, 0x154177240, 80414, 0x145B66F10),
    ('Reliquary', 60446, 0x35470, 0x147C38570, 0x147C38D90, 82242, 0x145B6A830),
    ('WeaponCurve', 67884, 0x123A0, 0x1502C1CE0, 0x1502C1BF0, 27754, 0x145B00170),
    ('WeaponLevel', 12244, 0x281D0, 0x1490466D0, 0x149046840, 53603, 0x145B32938),
    ('WeaponPromote', 22605, 0x34E28, 0x147BF6620, 0x147BF6530, 81103, 0x145B68498),
    ('EquipAffix', 45983, 0xDFD8, 0x152B8ED00, 0x152B8EFD0, 21444, 0x145AF3C40)]


def equip_item(item_id, guid, branch, payload, locked):
    if (type(item_id) is not int or not 0 <= item_id < 1 << 32 or
        type(guid) is not int or not 0 <= guid < 1 << 64 or
        type(branch) is not int or branch not in (0, 1, 2) or type(payload) is not bytes or
        type(locked) is not bool or not branch and payload):
        raise ValueError('Equip requires uint32 ID, uint64 GUID, branch 0/1/2 and bytes/bool')
    equip = (message(branch, payload) if branch else b'') + (scalar(3, 1) if locked else b'')
    return scalar(1, item_id) + scalar(2, guid) + message(6, equip)


def weapon_payload(level=3, exp=250, promote=2, affixes=((77, 4),), extra=b''):
    return scalar(1, level) + scalar(2, exp) + scalar(3, promote) + b''.join(
        message(4, scalar(1, k) + scalar(2, v)) for k, v in affixes) + extra


def fixture_cases():
    weapon = dict(branch=2, id=1001, guid=9007199254740993, level=3, exp=250, promote=2, affixes=[(77, 4)], locked=False, star=False)
    relic = dict(branch=1, id=2001, guid=9007199254740995, level=3, exp=0, promote=0, affixes=[], locked=False, star=False)
    cases = []
    specs = [
        ('weapon-nonzero', [weapon]),
        ('reliquary-lock-star', [relic | dict(locked=True, star=True)]),
        ('two-slots-distinct-guids', [weapon, relic]),
        ('weapon-level-changed', [weapon | dict(level=4)]),
        ('weapon-promote-changed', [weapon | dict(promote=3)]),
        ('weapon-exp-over-threshold', [weapon | dict(exp=1500)]),
        ('weapon-empty-affix-default', [weapon | dict(affixes=[])]),
        ('weapon-unrelated-affix', [weapon | dict(affixes=[(999, 4)])]),
        ('weapon-two-affixes', [weapon | dict(affixes=[(77, 4), (88, 1), (999, 0xFFFFFFFF)])]),
        ('weapon-missing-affix-level', [weapon | dict(affixes=[(77, 3)])]),
        ('weapon-affix-explicit-zero', [weapon | dict(affixes=[(77, 0)])]),
        ('weapon-upper-integers', [weapon | dict(id=0xFFFFFFFF, guid=0xFFFFFFFFFFFFFFFF, level=0xFFFFFFFF,
                                               exp=0xFFFFFFFF, promote=0xFFFFFFFF, affixes=[(0xFFFFFFFF, 0xFFFFFFFF)])]),
        ('weapon-zero-values', [weapon | dict(guid=0, level=0, exp=0, promote=0, affixes=[])]),
        ('reliquary-other-slot', [relic | dict(id=2002, guid=1 << 63, level=4)]),
        ('high-guid-collision', [weapon | dict(guid=0x100000001), relic | dict(guid=0x200000002)]),
        ('same-guid-overwrite', [weapon | dict(guid=71), weapon | dict(id=1002, guid=71, level=4, exp=500)]),
        ('role-order-and-missing-guid', [weapon, relic]),
        ('unknown-weapon-fields-retained', [weapon | dict(extra=scalar(5, 1)+message(6, bytes.fromhex('018001')))]),
        ('unknown-reliquary-fields-retained', [relic | dict(extra=scalar(2, 275)+scalar(3, 7)+scalar(4, 10001)+
            message(5, bytes.fromhex('1121'))+message(7, bytes.fromhex('0708'))+message(8, bytes.fromhex('6300')))]),
    ]
    for name, items in specs:
        wires = []
        for item in items:
            body = weapon_payload(item['level'], item['exp'], item['promote'], item['affixes'], item.get('extra', b'')) if item['branch'] == 2 else (
                scalar(1, item['level']) + scalar(6, int(item['star'])) + item.get('extra', b''))
            wires.append(equip_item(item['id'], item['guid'], item['branch'], body, item['locked']))
        cases.append(dict(name=name, items=items, wire=b''.join(message(2, w) for w in wires)))
    cases[-3]['roleGuids'] = [relic['guid'], 999, weapon['guid']]
    # These vectors independently exercise protobuf same-/different-oneof merge.
    prefix = scalar(1, 1001)+scalar(2, weapon['guid'])
    cases.append(dict(name='reliquary-then-weapon', items=[weapon], wire=message(2,
        prefix+message(6, message(1, scalar(1, 99))+message(2, weapon_payload())))))
    final = weapon | dict(exp=0, affixes=[(77, 0)])
    cases.append(dict(name='same-weapon-explicit-zero', items=[final], wire=message(2,
        prefix+message(6, message(2, weapon_payload())+message(2, scalar(2, 0)+message(4, scalar(1, 77)+scalar(2, 0)))))))
    cases.append(dict(name='weapon-then-reliquary', items=[relic], wire=message(2,
        scalar(1, relic['id'])+scalar(2, relic['guid'])+message(6, message(2, weapon_payload())+message(1, scalar(1, 3))))))
    return cases


def declarations(binary, metadata, startup, records, directory):
    for name, digest in [('inventory-graph-observations.json', RAW_SHA), ('inventory-contract-observations.json', BASE_SHA)]:
        if hashlib.sha256((directory/name).read_bytes()).hexdigest() != digest: raise ValueError('Reviewed inventory input changed')
    by = {t['index']: t for t in records}; pe = PE(binary); h = pe.offset(HEADER_VA)
    resolver = TypeResolver(pe, startup, records, h)
    mb = BODY+(number(binary, h+0xB0)^0x51324A1F)
    pb = BODY+((number(binary, h+0x174)+0xAE6BCD68)&0xFFFFFFFF)
    typed = []
    for owner, entry in [(51337, 0x150ED35E0), (68895, 0x148FED420), (29353, 0x150757D20),
            (18163, 0x15184E100), (18163, 0x15184EC00), (18163, 0x15184A5F0),
            (46352, 0x14A2C38C0), (51522, 0x14AD07E80)]:
        m = next(m for m in by[owner]['methods'] if m['registeredAddress'] == hex(entry))
        start = parameter_start(m['index'], number(metadata, mb+m['index']*26+4))
        args = [resolver.resolve_index(parameter_words(i, number(metadata, pb+i*8), number(metadata, pb+i*8+4))[1])
                for i in range(start, start+m['parameterCount'])]
        typed.append(dict(ownerType=owner, entry=hex(entry), method=m['name'], parameters=args))
    if any(typed[i]['parameters'][0].get('typeDefinition') != 65333 for i in range(3)):
        raise ValueError('Equip factory input declaration changed')
    if (typed[3]['parameters'][0].get('typeDefinition') != 48178 or
        typed[4]['parameters'][0]['arguments'][0]['kind'] != 'uint64' or
        [x.get('typeDefinition') for x in typed[5]['parameters'][:3]] != [49540, 29353, 52700] or
        typed[6]['parameters'][0].get('typeDefinition') != 29353 or
        [x['kind'] for x in typed[7]['parameters'][0]['arguments']] != ['uint32', 'uint32']):
        raise ValueError('Independent actor-slot/experience/map consumer changed')
    literals = Literals72(binary, metadata); all_literals = {x['index']: x for x in literals.group(literals.global_group)}
    resources = []
    for name, owner, offset, getter, loader, source, cell in RESOURCES:
        row = all_literals[source]; text = literals.decode(source)
        if row['cell'] != hex(cell) or text != 'Data/_ExcelBinOutput/'+name+'ExcelConfigData':
            raise ValueError('Named resource identity changed')
        for entry in (getter, loader):
            if not any(m['registeredAddress'] == hex(entry) for m in by[owner]['methods']): raise ValueError('Resource owner changed')
        field = next(f for f in by[owner]['fields'] if f['rawOffset'] == hex(0x4000000+offset))
        resources.append(dict(name=name, ownerType=owner, dictionaryField=field, getter=hex(getter), loader=hex(loader),
                              literal=dict(row, value=text)))
    enums = recover_enums(binary, metadata, records, indices=(34819, 49540, 73539, 85101))['enums']
    wanted = {'ITEM_RELIQUARY': 3, 'ITEM_WEAPON': 4, 'EQUIP_BRACER': 1, 'EQUIP_DRESS': 5, 'EQUIP_WEAPON': 6,
              'UnLock': 1, 'Lock': 2, 'UnStar': 1, 'Star': 2}
    symbols = {v['symbol']: v['value'] for x in enums for v in x['values']}
    if any(symbols.get(k) != v for k,v in wanted.items()): raise ValueError('Independent equipment/lock/star enums changed')
    enums = [dict(x, values=[v for v in x['values'] if v['symbol'] in wanted]) for x in enums]
    storage = []
    for offset, target in [(0x28, 34819), (0x38, 85101), (0x3C, 73539), (0x40, 64947), (0x48, 74063)]:
        field = next(f for f in by[68895]['fields'] if f['offset'] == offset)
        if field['type'].get('typeDefinition') != target:
            raise ValueError('Typed equipment model storage changed')
        storage.append(field)
    specs = BODY+(number(binary, h+0x170)^0x6347382B); cache = number(binary, pe.offset(USAGE_REGISTRATION)+0x10, 'Q')
    factory = []
    for usage, spec, cell, target in [(760451, 99283, 0x145A721C0, 29353), (760452, 99285, 0x145A721C8, 52700)]:
        key = (usage*0x57C15286+0x26DDEBDA)&0xFFFFFFFF
        source = ((number(metadata, literals.usages_base+usage*8)+0xABBDCABF)&0xFFFFFFFF)^key^0x2A357D90
        slot = number(metadata, literals.usages_base+usage*8+4)^key^0x1855AA65
        method, cls, inst = struct.unpack_from('<iii', metadata, specs+spec*12)
        n, pointer = struct.unpack_from('<QQ', binary, pe.offset(resolver.instances+inst*16))
        args = [resolver.resolve_index(resolver.pointer_index(number(binary, pe.offset(pointer+i*8), 'Q'))) for i in range(n)]
        if source != (6 << 29 | spec) or cache+slot*8 != cell or cls != -1 or len(args) != 1 or args[0].get('typeDefinition') != target:
            raise ValueError('Equip generic factory binding changed')
        factory.append(dict(usageRow=usage, methodSpec=spec, cell=hex(cell), methodDefinition=method, modelType=target))
    return dict(rawEvidenceSHA256=RAW_SHA, baseContractSHA256=BASE_SHA, typedConsumers=typed,
                resources=resources, enums=enums, modelStorage=dict(typeIndex=68895, fields=storage), factories=factory,
                nativeEntry32SHA256={hex(a): hashlib.sha256(binary[pe.offset(a, 32):][:32]).hexdigest() for a in (
                    0x150ED35E0, 0x148FED420, 0x150757D20, 0x15184EC00, 0x15184A5F0,
                    0x150758430, 0x14A2C38C0, 0x150758E60, 0x14AD07E80)})


class EquipRuntime72:
    def __init__(self,e,g):
        self.e=e;self.g=g;self.db={};self.lookup_log=[]
        alloc=e.models[0x1405336c0];arralloc=e.models.get(0x140514ee0)
        self.session=SessionRuntime72(e,5,7);e.models[0x1405336c0]=alloc
        if arralloc:e.models[0x140514ee0]=arralloc
        for off in [0xbed0,0x31e60]:e.write(GLOBAL+off,e.read(GLOBAL+0x204d0))
        p=e.alloc(32+2048);e.write(p+24,2048,'I');table=permutation_table(13,29);e.uc.mem_write(p+32,table[:1024]+table[2048:3072]);e.write(GLOBAL+0xe970,p);e.write(0x145872d30,0x12345678,'I')
        e.uc.mem_map(END,4096);e.uc.mem_write(END+0x100,b'\xc3');e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE,e._code,begin=END,end=END+4095))
        for a in [0x150ed35e0,0x150ed3010,0x150ed3210,0x150ed3510,0x150757d20,0x148fed420,0x1504d7880,0x147745020,0x147c38ec0,0x14aefa090,0x14aef9db0,0x14f905140,0x150758430,0x150757a00,0x1507587b0,0x147bf60c0,0x14a2c38c0,0x14ad63660,0x14b1046f0,0x14b1049f0]:e.allow(a)
        e.allow_unwind(0x15459e4b0)
        self.classes={}
        for idx,cell,info,entry in [(29353,0x145899fb8,0x145a721c0,0x150757d20),(52700,0x14589df00,0x145a721c8,0x150ed3210)]:
            c=e.alloc(1024);e.write(c+0xc8,1,'B');e.write(c+0xc7,1,'B');e.write(cell,c);e.write(c+0xf8,0x150ed3010);e.write(c+0x118,entry);e.write(c+0x128,0x150ed3510);self.classes[idx]=c
            mi=e.alloc();ctx=e.alloc();e.write(mi+0x18,ctx);e.write(ctx+0x10,c);e.write(info,mi)
        pool=e.alloc();pc=e.alloc();e.write(pool,pc);e.write(GLOBAL+0x5d60,pool);e.write(pc+0xf0,e.model_stub(self.rent))
        e.synthetic_type(0x1458b0890)
        self.resclasses={}
        for idx,cell in [(87097,0x1458aa7e8),(74316,0x1458aa7e0)]:
            c=e.synthetic_type(cell);hier=e.alloc();e.write(c+0xc4,1,'B');e.write(c+0x48,hier);e.write(hier,c);self.resclasses[idx]=c
        self.origlookup=e.models[0x1578b66f0];e.models[0x1578b66f0]=self.lookup
        self.resources={}
        for id,idx,cat,slot in [(1001,87097,4,6),(1002,87097,4,6),(0xFFFFFFFF,87097,4,6),
                                (2001,74316,3,1),(2002,74316,3,5)]:
            res=e.alloc(256);base=e.alloc(256);cfg=e.alloc(256);e.write(res,self.resclasses[idx]);e.write(res+0x10,base);e.write(res+0x20,cfg);e.write(base+0x38,cat,'I');self.code(base+0x2c,4)
            self.code(cfg+0x90,2);self.code(cfg+0x84,9001);e.write(cfg+0x7c,slot,'I');self.resources[id]=res
        e.write(GLOBAL+0x34900,self.dictionary('weapon',{i:self.resources[i] for i in (1001,1002,0xFFFFFFFF)}))
        e.write(GLOBAL+0x35470,self.dictionary('reliquary',{i:self.resources[i] for i in (2001,2002)}))
        # Controlled property fixture, actual level/phase lookup and scalar transforms.
        prop=e.alloc();e.write(prop+0x10,77,'I');self.code(prop+0x14,struct.unpack('<I',struct.pack('<f',2.5))[0]);e.write(prop+0x18,2001,'I')
        pa=self.array(8,1);e.write(pa+32,prop)
        for i in (1001,1002,0xFFFFFFFF):e.write(e.read(self.resources[i]+0x20)+0x68,pa)
        curves={}
        for lv,f in [(0,1.0),(1,2.0),(3,3.0),(4,4.0),(0xFFFFFFFF,5.0)]:
            row=e.alloc();a=self.array(12,1);e.write(row+0x10,a);e.write(a+32,77,'I');e.write(a+36,2,'I');self.code(a+40,struct.unpack('<I',struct.pack('<f',f))[0]);curves[lv]=row
        e.write(GLOBAL+0x123a0,self.dictionary('curve',curves))
        phases={}
        for ph,f in [(0,0.0),(2,1.25),(3,2.25)]:
            row=e.alloc();a=self.array(8,1);e.write(row+0x10,a);e.write(a+32,2001,'I');self.code(a+36,struct.unpack('<I',struct.pack('<f',f))[0]);phases[ph]=row
        e.write(GLOBAL+0x34e28,self.dictionary('promote-group',{9001:self.dictionary('promote-phase',phases)}))
        levels={}
        for lv in [0,1,3,4,0xFFFFFFFF]:
            row=e.alloc();a=self.array(4,2);e.write(row+0x10,a);self.code(a+32,100);self.code(a+36,1000);levels[lv]=row
        e.write(GLOBAL+0x281d0,self.dictionary('level',levels))
    def array(self,w,n=17):
        p=self.e.alloc(32+w*n);self.e.write(p+24,n,'I');return p
    def dictionary(self,name,items):
        e=self.e;p=e.alloc();a=self.array(24);e.write(p+0x18,a);e.write(p+0x38,len(items),'I');self.db[p]=(name,list(items))
        for i,(key,value)in enumerate(items.items()):e.write(a+0x30+i*24,value)
        return p
    def lookup(self):
        e=self.e;p=e.reg('rcx');k=e.reg('edx')
        if p not in self.db:return self.origlookup()
        name,keys=self.db[p];self.lookup_log.append((name,k));return keys.index(k)if k in keys else 0xffffffff
    def rent(self):
        p=self.e.alloc(512);self.e.write(p,self.e.reg('rdx'));return p
    def invoke(self,entry,stops=None,**regs):
        self.e.write(STACK+0x100008,END+0x100);return fragment(self.e,entry,stops or{END+0x100},{'rsp':STACK+0x100008,**regs})
    def code(self,p,value):return self.invoke(0x14aefa090,rcx=p,rdx=value)
    def factory(self,p):
        tr=self.invoke(0x150ed35e0,{END+0x100,0x150757dfb,0x1404c2be0},rcx=p,rdx=0)
        model=self.e.reg('rdi') if tr['stop']=='0x150757dfb' else self.e.reg('rax')
        return tr,model
    def auxiliary(self):
        e=self.e;self.affix_rows={};self.encoded_maps=[];self.delivered=[];self.role_nodes=[]
        self.orig_allocate=e.models[0x1405336c0]
        self.affix_list_class=e.synthetic_type(0x1458e1048);e.models[0x1405336c0]=self.allocate
        for a in [0x150758e60,0x152b8ef00,0x14e3da130,0x14e43af70,0x15184ec00,0x15184a5f0,0x14ad07e80]:e.allow(a)
        for a in [0x15893df20,0x1576cdee0,0x156f6f510,0x156f6f850]:e.allow_unwind(a)
        for a in [0x156f6f510,0x156f6f850]:e.models.pop(a,None)
        e1=e.synthetic_type(0x1458978f0);e2=e.synthetic_type(0x1458a2e78)
        klass=self.g.copy_runtime.interface_class([(e1,lambda:2),(e2,self.affix_id)])
        l=e.alloc();e.write(l,klass)
        for i in (1001,1002,0xFFFFFFFF):e.write(e.read(self.resources[i]+0x20)+0x40,l)
        self.affix_ids=e.alloc(8);self.code(self.affix_ids,77);self.code(self.affix_ids+4,88)
        groups={}
        for aid in [77,88]:
            levels={}
            for lv in [0,1,4]:
                p=e.alloc();levels[lv]=p;self.affix_rows[p]=(aid,lv)
            groups[aid]=self.dictionary('affix-level-'+str(aid),levels)
        e.write(GLOBAL+0xdfd8,self.dictionary('affix-group',groups))
        self.info(0x1459fc200,self.array(8,17),0x10)
        self.inventory=e.alloc();d=e.alloc();e.write(self.inventory+0x68,d);e.write(d+0x10,self.array(4));e.write(d+0x28,self.array(24));e.write(d+0x44,0xffffffff,'I')
        for c in [0x145a6b490,0x145a6b498]:self.info(c,e.alloc(),0x88)
        e.write(GLOBAL+0xb4d0,self.inventory)
        self.actor=e.alloc(1024);l=e.alloc();e.write(l+0x10,self.array(8));e.write(self.actor+0x68,l)
        self.guids=[];self.input_list=e.alloc();c=e.alloc(1024);e.write(self.input_list,c)
        e.write(c+0x1d8,e.model_stub(lambda:len(self.guids)));e.write(c+0x250,e.model_stub(lambda:self.guids[e.reg('edx')]))
        self.entity_manager=e.alloc();e.write(GLOBAL+0xa650,self.entity_manager);e.models[0x1547e1dd0]=self.entity
        e.models[0x1593cff80]=self.store_encoded
    def info(self,cell,value,off):
        e=self.e;info=e.alloc();c=e.alloc();ctx=e.alloc();e.write(cell,info);e.write(info,c);e.write(c+0x30,ctx);e.write(ctx+off,value)
    def affix_id(self):return self.e.read(self.affix_ids+self.e.reg('edx')*4,'I')
    def allocate(self):
        if self.e.reg('rcx')==self.affix_list_class:return self.e.alloc()
        return self.orig_allocate()
    def link(self,p):
        e=self.e;d=e.read(p+16);linked=e.read(p+24);nodes=self.g.maps.dictionaries[d]['nodes'];head=e.alloc(80);e.write(linked+16,head);e.write(linked+24,len(nodes),'I');e.write(linked+28,1,'I')
        chain=[head]+nodes
        for i,node in enumerate(chain):e.write(node+16,head);e.write(node+24,chain[(i+1)%len(chain)]);e.write(node+48,1,'B')
    def index(self,p):
        wrapper=self.e.alloc();self.e.write(wrapper+16,p)
        trace=self.invoke(0x14e43af70,rcx=self.inventory,rdx=wrapper)
        self.guids.append(self.e.read(p+24))
        return trace
    def role_sink(self):
        e=self.e;self.delivered.append((e.reg('edx'),e.reg('r8'),e.reg('r9')));return 0
    def route(self):
        e=self.e;e.models[0x15184a5f0]=self.role_sink;tr=self.invoke(0x15184ec00,{0x15183eae0,0x1404c2be0},rcx=self.actor,rdx=self.input_list,r8=0);del e.models[0x15184a5f0];return tr
    def entity(self):
        e=self.e;p=e.alloc(512);d=e.alloc();e.write(p+0x98,d);self.role_nodes.append(p);return p
    def store_encoded(self):
        e=self.e;p=e.alloc(32);e.uc.mem_write(p,bytes(e.uc.mem_read(e.reg('rdx'),16))+bytes(e.uc.mem_read(e.reg('r8'),16)));self.encoded_maps.append(p);return 1
    def assign(self,slot,weapon,relic):
        e=self.e;self.encoded_maps=[];old=e.alloc();new=e.alloc();e.write(STACK+0x100008+0x28,old);e.write(STACK+0x100008+0x30,new)
        tr=self.invoke(0x15184a5f0,{0x15184a8f4,0x15184a84a,0x1404c2be0},rcx=self.actor,rdx=slot,r8=weapon,r9=relic)
        if tr['stop']=='0x1404c2be0':return tr,None
        p=self.role_nodes[-1];dec=self.invoke(0x14b1049f0,rcx=p+0x50);level=e.reg('eax');pairs=[];pair_decodes=[]
        for q in self.encoded_maps:
            key_trace=self.invoke(0x14b1049f0,rcx=q);k=e.reg('eax')
            value_trace=self.invoke(0x14b1049f0,rcx=q+16);pairs.append((k,e.reg('eax')))
            pair_decodes.append(dict(key=key_trace,value=value_trace))
        return dict(assignment=tr,levelDecode=dec,pairDecodes=pair_decodes),dict(id=e.read(p+0x70,'I'),slot=e.read(p+0x48,'I'),level=level,pairs=pairs)

    def resource_paths(self):
        results=[];e=self.e
        for name,owner,offset,getter,loader,index,cell in RESOURCES:
            literal=e.alloc();e.write(cell,literal);e.allow(getter);e.allow(loader)
            a=self.invoke(getter)
            if e.reg('rax')!=literal:raise ValueError('Named resource getter differs')
            b=self.invoke(loader,{0x14D6E1570})
            if e.reg('rcx')!=literal:raise ValueError('Named resource loader argument differs')
            results.append(dict(name=name,getter=a,loader=b,fileIOExecuted=False))
        return results


def float32(value):
    return struct.unpack('<f',struct.pack('<f',value))[0]


def expected_affixes(item):
    values=dict(item['affixes'])
    return [(key,values[key]) for key in (77,88) if key in values and values[key] in (0,1,4)] if values else [(77,0),(88,0)]


def verify(binary, records, directory, libraries):
    audit=json.loads((directory/'player-store-outer-observations.json').read_text('utf8'))['dependencyAudit']
    results=[]
    for case in fixture_cases():
        e=Offline72(binary,libraries,records,trace_instructions=False);g=InventoryGraph72(e,directory,audit)
        try:
            read=g.run_graph(68537,case['wire']);back=e.read(e.read(OBJECT+24)+16);array=e.read(back+16)
            pointers=[e.read(array+32+i*8) for i in range(e.read(back+24,'I'))]
            rt=EquipRuntime72(e,g);paths=rt.resource_paths();models=[];latest={}
            for pointer,item in zip(pointers,case['items']):
                trace,p=rt.factory(pointer)
                if trace['stop'] not in (hex(RETURN),'0x150757dfb'):raise ValueError('Valid equip rejected during construction')
                kind=29353 if item['branch']==2 else 52700
                body=e.read(p+(0x40 if item['branch']==2 else 0x48))
                actual=dict(id=e.read(p+16,'I'),guid=e.read(p+24),count=e.read(p+32,'I'),category=e.read(p+40,'I'),
                    lockedState=e.read(p+56,'I'),starState=e.read(p+60,'I'),modelType=kind,correctClass=e.read(p)==rt.classes[kind],
                    rawBody=g.snapshot(body))
                expected=dict(id=item['id'],guid=item['guid'],count=1,category=4 if item['branch']==2 else 3,
                    lockedState=2 if item['locked'] else 1,starState=(2 if item['star'] else 1) if item['branch']==1 else 0,
                    correctClass=True)
                if any(actual[k]!=v for k,v in expected.items()):raise ValueError('Native Equip model differs: '+str(actual))
                models.append(dict(pointer=p,item=item,construction=trace,result=actual));latest[item['guid']]=item
            rt.auxiliary();consumers=[]
            for model in models:
                p,item=model['pointer'],model['item'];consumer={}
                if item['branch']==2:
                    rt.link(e.read(e.read(p+0x40)+24))
                    start=len(rt.lookup_log);stat=rt.invoke(0x150758430,rcx=p,rdx=0)
                    value=struct.unpack('<f',(e.reg('xmm0')&0xFFFFFFFF).to_bytes(4,'little'))[0]
                    factor={0:1.0,1:2.0,3:3.0,4:4.0,0xFFFFFFFF:5.0}[item['level']]
                    expected=2.5*factor+{0:0.0,2:1.25,3:2.25}.get(item['promote'],0.0)
                    if value!=expected:raise ValueError('Native curve/promote arithmetic differs')
                    ratio=rt.invoke(0x14A2C38C0,rdx=p)
                    progress=struct.unpack('<f',(e.reg('xmm0')&0xFFFFFFFF).to_bytes(4,'little'))[0]
                    if progress!=float32(float32(item['exp'])/1000.0):raise ValueError('Native experience ratio differs')
                    affix=rt.invoke(0x150758E60,rcx=p);listing=e.reg('rax')
                    selected=[rt.affix_rows[e.read(e.read(listing+16)+32+i*8)] for i in range(e.read(listing+24,'I'))] if listing else []
                    if selected!=expected_affixes(item):raise ValueError('Native affix group/level selection differs')
                    consumer=dict(stat=stat,experience=ratio,affix=affix,statValue=value,experienceRatio=progress,
                                  selectedAffixes=selected,resourceQueries=rt.lookup_log[start:])
                consumer['guidIndex']=rt.index(p)
                consumers.append(consumer)
            rt.guids=case.get('roleGuids',[x['guid'] for x in case['items']])
            route=rt.route()
            if route['stop']!='0x15183eae0':raise ValueError('Valid actor inventory lookup failed')
            expected_items=[latest[x] for x in rt.guids if x in latest]
            if len(rt.delivered)!=len(expected_items):raise ValueError('Actor GUID list delivery changed')
            assigned=[]
            for args,item in zip(rt.delivered,expected_items):
                slot,weapon,relic=args
                if slot!=(6 if item['branch']==2 else (1 if item['id']==2001 else 5)) or bool(weapon)!=(item['branch']==2):
                    raise ValueError('Named actor equip slot/class changed')
                trace,value=rt.assign(*args)
                expected=dict(id=item['id'],slot=slot,level=item['level'],pairs=list(dict(item['affixes']).items()) if weapon else [])
                if value!=expected:raise ValueError('Actor native equipment/affix storage differs: '+str(value))
                assigned.append(dict(native=trace,result=value,guid=item['guid']))
            results.append(dict(name=case['name'],wire=case['wire'].hex(),reader=read,paths=paths,
                constructions=[{k:v for k,v in x.items() if k not in ('pointer','item')} for x in models],
                consumers=consumers,roleRoute=route,roleGuids=rt.guids,roleAssignments=assigned))
        finally:g.close();e.close()
    return results


def rejections(binary,records,directory,libraries):
    audit=json.loads((directory/'player-store-outer-observations.json').read_text('utf8'))['dependencyAudit'];result=[]
    for name,branch,item_id in [('empty-equip-oneof',0,1001),('weapon-with-reliquary-id',2,2001),('reliquary-with-weapon-id',1,1001)]:
        e=Offline72(binary,libraries,records,trace_instructions=False);g=InventoryGraph72(e,directory,audit)
        wire=equip_item(item_id,9007199254740993,branch,scalar(1,3) if branch else b'',False)
        try:
            g.run_graph(65333,wire);rt=EquipRuntime72(e,g);trace,p=rt.factory(OBJECT)
            stage='factory'
            if trace['stop']!='0x1404c2be0':
                rt.auxiliary();rt.index(p);trace=rt.route();stage='actor-slot-routing'
                if trace['stop']!='0x1404c2be0':
                    trace,value=rt.assign(*rt.delivered[0]);stage='actor-slot-assignment'
            if trace['stop']!='0x1404c2be0':raise ValueError('Wrong Equip/ID business fixture did not reach native error')
            result.append(dict(name=name,wire=wire.hex(),scope='native-null-error-helper-boundary',stage=stage,native=trace))
        finally:g.close();e.close()
    e=Offline72(binary,libraries,records,trace_instructions=False);g=InventoryGraph72(e,directory,audit)
    try:
        for name,wire,limit in [('recursion-limit','3200',0),('negative-equip-length','32ffffffff0f',100),
                ('truncated-equip','320512050801',100),('invalid-tag','00',100),('guid-overflow','10ffffffffffffffffff02',100)]:
            try:g.run_graph(65333,bytes.fromhex(wire),limit)
            except ValueError as error:
                detail=str(error);result.append(dict(name=name,wire=wire,recursionLimit=limit,error=detail,
                    scope='native-reader-error-helper' if 'Native error branch:' in detail else 'explicit-input-model'))
            else:raise ValueError('Bad equip wire accepted')
    finally:g.close();e.close()
    return result


def native_summary(trace):
    relevant={hex(x) for x in (0x150ED35E0,0x148FED420,0x150ED3210,0x150ED3510,0x150757D20,
        0x147745020,0x1504D7880,0x147C38EC0,0x150758430,0x150757A00,0x1507587B0,0x147BF60C0,
        0x14A2C38C0,0x14AD63660,0x150758E60,0x152B8EF00,0x15893DF20,0x15184EC00,0x14E3DA130,
        0x14E43AF70,0x156F6F510,0x156F6F850,0x15184A5F0,0x14AD07E80,0x1576CDEE0,0x14B1046F0,0x14B1049F0,
        0x14AEFA090,0x14AEF9DB0,0x14F905140)}|{trace['entry'],trace['stop']}
    return {k:trace[k] for k in ('entry','stop','instructions','nativePathSHA256','models')}|{
        'nativeEntries':sorted(relevant&set(trace['executedAddresses']))}


def compact(value):
    if isinstance(value,list):return [compact(x) for x in value]
    if isinstance(value,dict):
        if 'executedAddresses' in value:return native_summary(value)
        return {k:compact(v) for k,v in value.items() if k!='reader'}
    return value


def native_contract(trace, entries, stop):
    """Require actual execution of the claimed core, preserving declared fixture calls."""
    expected = {hex(x) for x in entries}
    return (trace['stop'] == hex(stop) and expected <= set(trace['nativeEntries']) and
            not expected.intersection(trace['models']))


def validate_report(report):
    errors=[]
    if (report.get('inputSHA256')!=HASHES or report.get('runtimeStatus')!='not-tested' or
        report.get('targetProtocolReady') is not False or report.get('completeWeaponConstructor') is not False or
        report.get('completeAvatarHandler') is not False or not report.get('models')):errors.append('Equip evidence scope changed')
    if [(x.get('typeIndex'),x.get('tag'),x.get('semanticField')) for x in report.get('fieldBindings',[])]!=FIELDS:
        errors.append('Limited Equip field bindings changed')
    if report.get('unresolvedTags')!=UNKNOWN:errors.append('Unknown Equip field promoted')
    if [x['name'] for x in report.get('fixtures',[])]!=[x['name'] for x in fixture_cases()]:errors.append('Equip fixtures changed')
    if report.get('nativeFixtures')!=len(fixture_cases()):errors.append('Equip fixture count changed')
    expected_errors=[('empty-equip-oneof','native-null-error-helper-boundary'),
        ('weapon-with-reliquary-id','native-null-error-helper-boundary'),
        ('reliquary-with-weapon-id','native-null-error-helper-boundary'),
        ('recursion-limit','native-reader-error-helper'),('negative-equip-length','native-reader-error-helper'),
        ('truncated-equip','native-reader-error-helper'),('invalid-tag','explicit-input-model'),('guid-overflow','explicit-input-model')]
    if [(x['name'],x['scope']) for x in report.get('rejections',[])]!=expected_errors:errors.append('Equip error scope changed')
    for row,case in zip(report.get('fixtures',[]),fixture_cases()):
        if row['wire']!=case['wire'].hex():errors.append('Independent Equip wire changed')
        if len(row['constructions'])!=len(case['items']) or len(row['consumers'])!=len(case['items']):
            errors.append('Equip native construction count changed')
        for construction,item,consumer in zip(row['constructions'],case['items'],row['consumers']):
            value=construction['result'];weapon=item['branch']==2
            expected=dict(id=item['id'],guid=item['guid'],count=1,category=4 if weapon else 3,correctClass=True,
                modelType=29353 if weapon else 52700,lockedState=2 if item['locked'] else 1,
                starState=0 if weapon else (2 if item['star'] else 1))
            if any(value.get(k)!=v for k,v in expected.items()):errors.append('Native model/lock/star changed')
            raw=dict(NJAGFJINHMA=item['level'])
            if weapon:raw.update(NILMBAGDEAL=item['exp'],KFDCBKBPPGL=item['promote'],
                JMGKMMFNABP={str(k):v for k,v in item['affixes']})
            else:raw['CGKIGPFDFOF']=item['star']
            if row['name']=='unknown-weapon-fields-retained':raw.update(MNCFEDPDEAG=True,FLGGIMBHFOO=[1,128])
            if row['name']=='unknown-reliquary-fields-retained':raw.update(NILMBAGDEAL=275,KFDCBKBPPGL=7,
                OJDKGGHBKKK=10001,FOFCJMPCEBB=[17,33],IMEGOOADEBF=[7,8],IHKPPDPBEML=[99,0])
            if value['rawBody']!={k:v for k,v in raw.items() if v}:errors.append('Native Equip clone values changed')
            constructor=[0x150ED35E0,0x148FED420,0x150ED3510,0x1504D7880 if weapon else 0x147745020]
            if not native_contract(construction['construction'],constructor,0x150757DFB if weapon else RETURN):
                errors.append('Equip constructor native boundary changed')
            if not native_contract(consumer['guidIndex'],[0x14E43AF70,0x156F6F850],RETURN):
                errors.append('Native GUID insertion changed')
            if weapon:
                expected=2.5*{0:1.,1:2.,3:3.,4:4.,0xFFFFFFFF:5.}[item['level']]+{0:0.,2:1.25,3:2.25}.get(item['promote'],0.)
                if consumer['statValue']!=expected or consumer['experienceRatio']!=float32(float32(item['exp'])/1000.):
                    errors.append('Native level/experience/promote values changed')
                if consumer['selectedAffixes']!=[list(x) for x in expected_affixes(item)]:errors.append('Native affix selection changed')
                for key,entries in [('stat',[0x150758430,0x150757A00,0x1507587B0,0x147BF60C0,0x14AEF9DB0,0x14F905140]),
                    ('experience',[0x14A2C38C0,0x14AD63660,0x14AEF9DB0]),('affix',[0x150758E60,0x14AEF9DB0])]:
                    if not native_contract(consumer[key],entries,RETURN):errors.append('Equip consumer replaced with fixture')
        if not native_contract(row['roleRoute'],[0x15184EC00,0x14E3DA130,0x156F6F510],0x15183EAE0):
            errors.append('Native actor GUID routing changed')
        latest={x['guid']:x for x in case['items']};expected=[latest[g] for g in row['roleGuids'] if g in latest]
        if len(expected)!=len(row['roleAssignments']):errors.append('Actor item routing changed')
        for actual,item in zip(row['roleAssignments'],expected):
            expect=dict(id=item['id'],slot=6 if item['branch']==2 else (1 if item['id']==2001 else 5),
                        level=item['level'],pairs=[list(x) for x in dict(item['affixes']).items()] if item['branch']==2 else [])
            if actual['result']!=expect or actual['guid']!=item['guid']:errors.append('Actor slot/level/affix transfer changed')
            assign=actual['native'];entries=[0x15184A5F0,0x14B1046F0]
            if item['branch']==2:entries += [0x14AD07E80,0x1576CDEE0]
            if (not native_contract(assign['assignment'],entries,0x15184A8F4 if item['branch']==2 else 0x15184A84A) or
                not native_contract(assign['levelDecode'],[0x14B1049F0],RETURN) or
                len(assign['pairDecodes'])!=len(expect['pairs']) or
                any(not native_contract(t,[0x14B1049F0],RETURN) for p in assign['pairDecodes'] for t in p.values())):
                errors.append('Actor native integer/affix codec changed')
    return errors


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('client','output','observations'):p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--directory',type=Path,default=Path('protocol/cn-7.2'))
    p.add_argument('--python-libs',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    paths=[a.output.resolve(),a.observations.resolve()]
    if len(set(paths))!=2 or any(x.is_relative_to(a.client.resolve()) for x in paths):p.error('Distinct outputs outside client required')
    b,m,s=[read_verified(a.client/name,digest) for name,digest in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);declared=declarations(b,m,s,records,a.directory)
    good=verify(b,records,a.directory,a.python_libs);bad=rejections(b,records,a.directory,a.python_libs)
    context=dict(schemaVersion=1,task='F-03d2b2',inputSHA256=HASHES,declarations=declared,
        runtimeStatus='not-tested',targetProtocolReady=False,completeWeaponConstructor=False,completeAvatarHandler=False,
        identityBasis='inferred-named-resources-and-native-actor-equip-slots',originalClientNamesRecovered=False,
        semanticTypes=[dict(typeIndex=t,semanticName=n,status='verified') for t,n in [(44169,'Equip'),(64947,'Weapon'),(74063,'Reliquary')]],
        fieldBindings=[dict(typeIndex=t,tag=tag,semanticField=name,status='verified') for t,tag,name in FIELDS],
        unresolvedTags=UNKNOWN,nativeFixtures=len(good),rejections=bad,
        models=['Native reader primitives/default containers and managed clone allocation',
                'Declared metadata/pool/class storage; Weapon constructor stops after base value copy before derived callbacks',
                'Controlled named resource dictionaries, resource rows, native-array interfaces and generic List storage',
                'Reversible uint32/float/integer codec tables and Random fixture; target encode/decode/arithmetic execute',
                'Linked storage constructed over actual reader/clone map nodes; native enumerator and pair conversions execute',
                'Actor input GUID list storage; slot assignment is a delivery sink, then separately executed to declared stop',
                'Actor equipment node allocation and encoded dictionary storage; no whole actor initialization or UI'],
        fixtures=good)
    repo=Path(__file__).resolve().parents[1]
    context['sourceSHA256']={name:hashlib.sha256((repo/name).read_bytes()).hexdigest() for name in (
        'tools/client72_equip_contract.py','tools/trace_client72_inventory_graph.py','tools/trace_client72_inventory_containers.py',
        'tools/client72_graph.py','tools/client72_map_runtime.py','tools/client72_session_runtime.py')}
    summary=json.loads(json.dumps(compact(context)))
    errors=validate_report(summary)
    if errors:raise ValueError(errors)
    for path,data in zip(paths,[context,summary]):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n','utf8')
    print(json.dumps(dict(nativeFixtures=len(good),rejections=len(bad),verifiedFields=len(FIELDS))))


if __name__=='__main__':main()
