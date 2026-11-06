"""Bounded CN72 Avatar batch application, identity and normal-property contract.

Actual dispatcher, typed consumer fragments, GUID dictionary, resource lookup,
normal-property codecs and equipment routing execute; managed allocation,
collection storage and declared delivery boundaries remain explicit fixtures.
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
    from .extract_client72_commands import classify_commands
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from .client72_session_runtime import SessionRuntime72, fragment, permutation_table
    from .client72_graph_fixtures import scalar, message
    from .trace_client72_avatar_core import AvatarCore72, CoreMessageMaps72, core_aot
    from .trace_client72_avatar_outer import TeamMap72, AvatarOuter72, avatar_aot
    from .trace_client72_player_store import type_cells
    from .client72_player_data_contract import PlayerContract72
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from extract_client72_commands import classify_commands
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from client72_session_runtime import SessionRuntime72, fragment, permutation_table
    from client72_graph_fixtures import scalar, message
    from trace_client72_avatar_core import AvatarCore72, CoreMessageMaps72, core_aot
    from trace_client72_avatar_outer import TeamMap72, AvatarOuter72, avatar_aot
    from trace_client72_player_store import type_cells
    from client72_player_data_contract import PlayerContract72

RETURN=END+0x100
ROOT,AVATAR=30509,48178
FIELDS=[(ROOT,6,'avatar_list'),(AVATAR,1,'avatar_id'),(AVATAR,2,'guid'),
        (AVATAR,5,'equip_guid_list'),(AVATAR,3,'prop_map')]
RAW_HASHES={
    'avatar-outer-observations.json':'18ffa869ee1ebb7d73e028727aa1b424d0f1ce641530d73c7984696cf38093b8',
    'avatar-core-observations.json':'81a0411e8fe6b7cc3ca606473ae684744a8df73e97009666df1c2775e9bdb3d4',
    'player-data-contract-observations.json':'e239ee03cc645d61dfa87ad87db827eb268273f049c496f3730a213153c29689',
    'equip-contract-observations.json':'77e1df9d53d93380362f30ec0ad604b447a1123f4840963fa9f91a1da41b5011'}
PROPERTY_IDS={1001:'PROP_EXP',1002:'PROP_BREAK_LEVEL',4001:'PROP_LEVEL'}
UNKNOWN={str(ROOT):[3,4,5,9,11,12,13,14,15],str(AVATAR):[4,6,7]+list(range(9,39))}


def avatar_wire(avatar_id,guid,equip_guids,properties):
    if (type(avatar_id) is not int or not 0<=avatar_id<1<<32 or type(guid) is not int or not 0<=guid<1<<64 or
        any(type(x) is not int or not 0<=x<1<<64 for x in equip_guids)):
        raise ValueError('Avatar ID is uint32; instance and equipment GUIDs are uint64')
    wire=scalar(1,avatar_id)+scalar(2,guid)+b''.join(scalar(5,g) for g in equip_guids)
    for key,prop,branch,value,independent in properties:
        if not (type(key) is int and 0<=key<1<<32 and type(prop) is int and 0<=prop<1<<32 and branch in (0,2,3)):
            raise ValueError('Property keys are uint32; supported raw union branches are absent/int64/float')
        if branch==2 and (type(value) is not int or not -(1<<63)<=value<1<<63):raise ValueError('Property value is int64')
        child=scalar(1,prop)
        if branch==2:child+=scalar(2,value)
        elif branch==3:child+=b'\x1d'+struct.pack('<f',value)
        if independent:child+=scalar(4,independent)
        wire+=message(3,scalar(1,key)+message(2,child))
    return wire


def fixture_cases():
    avatar=dict(id=1001,guid=9007199254740993,equips=[0x100000001,0x200000002],
                props=[(7,4001,2,42,0),(8,1001,2,275,0),(9,1002,2,3,0)])
    specs=[('avatar-nonzero',[avatar]),('empty-list',[]),
        ('two-avatar-distinct-identities',[avatar,avatar|dict(id=1002,guid=9007199254740995)]),
        ('same-id-distinct-guid',[avatar,avatar|dict(guid=1<<63,equips=[0x200000002])]),
        ('same-guid-reuses-model',[avatar,avatar|dict(id=1002,props=[(5,4001,2,7,0)])]),
        ('colliding-high-guids',[avatar|dict(guid=0x100000001),avatar|dict(id=1002,guid=0x200000002)]),
        ('zero-identity-values',[avatar|dict(id=0,guid=0,equips=[],props=[])]),
        ('upper-identity-values',[avatar|dict(id=0xffffffff,guid=0xffffffffffffffff)]),
        ('missing-avatar-resource',[avatar|dict(id=99999)]),
        ('level-above-double-exact-range',[avatar|dict(props=[(7,4001,2,9007199254740993,0)])]),
        ('int64-min-max',[avatar|dict(props=[(7,1001,2,-(1<<63),0),(8,1002,2,(1<<63)-1,0)])]),
        ('negative-and-zero-properties',[avatar|dict(props=[(7,4001,2,-1,0),(8,1001,2,0,0),(9,1002,2,0,0)])]),
        ('float-union-is-not-int64',[avatar|dict(props=[(7,4001,3,2.5,0)])]),
        ('independent-tag4-only',[avatar|dict(props=[(7,4001,0,0,987)])]),
        ('int64-union-beats-tag4',[avatar|dict(props=[(7,4001,2,43,987)])]),
        ('duplicate-outer-key-replaces',[avatar|dict(props=[(7,4001,2,42,0),(7,1001,2,99,0)])]),
        ('two-keys-same-level-property',[avatar|dict(props=[(7,4001,2,42,0),(8,4001,2,99,0)])]),
        ('two-keys-same-promote-property',[avatar|dict(props=[(7,1002,2,2,0),(8,1002,2,3,0)])]),
        ('equipment-order-missing-and-duplicate',[avatar|dict(equips=[0x200000002,999,0x100000001,0x200000002])]),
        ('equipment-upper-and-zero-missing',[avatar|dict(equips=[0,0xffffffffffffffff])])]
    cases=[dict(name=name,avatars=items,wire=b''.join(message(6,avatar_wire(x['id'],x['guid'],x['equips'],x['props'])) for x in items)) for name,items in specs]
    packed=avatar|dict(equips=[0,0x100000001,0x200000002])
    wire=avatar_wire(packed['id'],packed['guid'],[],packed['props'])+scalar(5,0)+message(5,bytes.fromhex('81808080108280808020'))
    cases.append(dict(name='packed-unpacked-equipment',avatars=[packed],wire=message(6,wire)))
    explicit=avatar|dict(id=0,guid=0,equips=[],props=[])
    cases.append(dict(name='explicit-zero-overwrite',avatars=[explicit],wire=message(6,scalar(1,1001)+scalar(2,71)+scalar(1,0)+scalar(2,0))))
    return cases


def declarations(binary,metadata,startup,records,directory):
    inputs={}
    for name,digest in RAW_HASHES.items():
        raw=(directory/name).read_bytes()
        if hashlib.sha256(raw).hexdigest()!=digest:raise ValueError('Reviewed Avatar input changed: '+name)
        inputs[name]=json.loads(raw)
    commands=[x for x in classify_commands(records)['commands'] if x['cmdId']==20143]
    if len(commands)!=1 or commands[0]['typeIndex']!=ROOT:raise ValueError('Avatar command binding changed')
    pe=PE(binary);h=pe.offset(HEADER_VA);by={x['index']:x for x in records};resolver=TypeResolver(pe,startup,records,h)
    mb=BODY+(number(binary,h+0xb0)^0x51324a1f);pb=BODY+((number(binary,h+0x174)+0xae6bcd68)&0xffffffff)
    typed=[]
    for owner,entry in [(56238,0x1507cee50),(18163,0x15184e100),(18163,0x15184ec00),(89508,0x152f7f670)]:
        row=next(m for m in by[owner]['methods'] if m['registeredAddress']==hex(entry))
        first=parameter_start(row['index'],number(metadata,mb+row['index']*26+4))
        args=[resolver.resolve_index(parameter_words(i,number(metadata,pb+i*8),number(metadata,pb+i*8+4))[1]) for i in range(first,first+row['parameterCount'])]
        typed.append(dict(ownerType=owner,entry=hex(entry),method=row['name'],parameters=args))
    if (typed[0]['parameters'][0].get('typeDefinition')!=ROOT or typed[1]['parameters'][0].get('typeDefinition')!=AVATAR or
        typed[2]['parameters'][0]['arguments'][0]['kind']!='uint64' or typed[3]['parameters'][0]['arguments'][1].get('typeDefinition')!=78350):
        raise ValueError('Typed Avatar consumers changed')
    virtuals=[]
    for owner,slot,entry in [(72120,24,0x148aa58d0),(72120,25,0x148aa58e0),(89508,17,0x152f80500),
            (89508,7,0x152f7ed50),(89508,14,0x152f80100),(18163,20,0x1518407c0)]:
        if not any(m['virtualSlot']==slot and m['registeredAddress']==hex(entry) for m in by[owner]['methods']):raise ValueError('Avatar vtable declaration changed')
        virtuals.append(dict(ownerType=owner,slot=slot,entry=hex(entry)))
    if [by[i]['parent']['typeDefinition'] for i in [18163,72120]]!=[72120,89508]:raise ValueError('Avatar model inheritance changed')
    enums=recover_enums(binary,metadata,records,indices=(62096,75464))['enums'];wanted=set(PROPERTY_IDS.values())|{'AvatarLevelChangedNoDependent','AvatarBreakLevelChanged'}
    enums=[dict(x,values=[v for v in x['values'] if v['symbol'] in wanted]) for x in enums]
    symbols={v['symbol']:v['value'] for x in enums for v in x['values']}
    if symbols!={**{v:k for k,v in PROPERTY_IDS.items()},'AvatarLevelChangedNoDependent':367,'AvatarBreakLevelChanged':368}:
        raise ValueError('Minimal Avatar property/event symbols changed')
    literal=Literals72(binary,metadata);row=next(x for x in literal.group(literal.global_group) if x['index']==66330)
    text=literal.decode(66330)
    if row['cell']!='0x145b4b6f0' or text!='Data/_ExcelBinOutput/AvatarExcelConfigData':raise ValueError('Named Avatar resource changed')
    field=next(f for f in by[65789]['fields'] if f['rawOffset']=='0x4030870')
    if [x.get('kind') for x in field['type']['arguments']]!=['uint32','class'] or field['type']['arguments'][1].get('typeDefinition')!=58272:raise ValueError('Avatar resource dictionary changed')
    for a in [0x14f33b360,0x14f33b0c0,0x14f3382e0]:
        if not any(m['registeredAddress']==hex(a) for m in by[65789]['methods']):raise ValueError('Avatar resource owner changed')
    usage=737549;spec=21679;key=(usage*0x57c15286+0x26ddebda)&0xffffffff
    source=((number(metadata,literal.usages_base+usage*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
    slot=number(metadata,literal.usages_base+usage*8+4)^key^0x1855aa65;cache=number(binary,pe.offset(USAGE_REGISTRATION)+0x10,'Q')
    method,cls,inst=struct.unpack_from('<iii',metadata,BODY+(number(binary,h+0x170)^0x6347382b)+spec*12)
    n,ptr=struct.unpack_from('<QQ',binary,pe.offset(resolver.instances+inst*16))
    args=[resolver.resolve_index(resolver.pointer_index(number(binary,pe.offset(ptr+i*8),'Q'))) for i in range(n)]
    if source!=(6<<29|spec) or cache+slot*8!=0x145990bc8 or method!=294224 or cls!=-1 or len(args)!=1 or args[0].get('typeDefinition')!=18163:
        raise ValueError('Actual generic Avatar factory binding changed')
    return dict(inputContracts=RAW_HASHES,commands=commands,typedConsumers=typed,virtualDispatch=virtuals,enums=enums,
        resource=dict(ownerType=65789,dictionary=field,literal=dict(row,value=text),getter='0x14f33b360',loader='0x14f33b0c0',query='0x14f3382e0'),
        factory=dict(usageRow=usage,methodSpec=spec,methodDefinition=method,modelType=18163,cell='0x145990bc8'))


class CombinedMessages(CoreMessageMaps72):
    CONFIG=CoreMessageMaps72.CONFIG|TeamMap72.CONFIG

class AvatarContractGraph(AvatarCore72):
    def __init__(self,e,directory,cells,bindings,outer_bindings,outer):
        super().__init__(e,directory,cells,bindings)
        self.schemas.update({x['typeIndex']:x for x in outer['messages']})
        for idx in [30509,26081,14786]:
            entry=next(int(m['registeredAddress'],16) for m in e.records[idx]['methods'] if m['name']=='MOMMACBEMEK')
            klass=e.alloc(1024);table=e.alloc(16);e.write(klass+0xc7,1,'B');e.write(klass+0x80,table);e.write(klass+0xc0,1,'H');e.write(klass+0xbe,32,'H');e.write(table,self.interface);e.write(klass+0xe8,entry)
            self.classes[idx]=klass;self.class_indices[klass]=idx;self.entries[idx]=entry;e.allow(entry)
        for cell,row in cells.items():
            idx=row['type'].get('typeDefinition')
            if row['type']['kind']=='class' and idx in [30509,26081,14786]:e.write(cell,self.classes[idx])
        saved={t:self.maps.message.config[t]['codec'] for t in [3,10]}
        combined=CombinedMessages(self,directory,bindings+outer_bindings)
        self.maps.message=combined;self.maps.parts[0]=combined;self.maps.static_values=combined.static_values
        for t,c in saved.items():combined.config[t]['codec']=c;e.write(GLOBAL+combined.config[t]['static'],c)
        e.models[0x1578b66f0]=lambda:self.maps.dictionary_owner().lookup()
        e.models[0x1578b6a20]=lambda:self.maps.dictionary_owner().insert()
        self.codec_classes.add(e.synthetic_type(0x1458c5aa8))
        for entry in [0x152de07f0,0x1502ce560]:e.allow(entry);e.prepare();e.run(entry)
        combined.config[5]['codec']=e.read(GLOBAL+0x22800)
        self.heap_floor=e.heap_cursor
    def snapshot(self,p,depth=0):
        if p and self.objects[p] in (26081,14786):return AvatarOuter72.snapshot(self,p,depth)
        return super().snapshot(p,depth)

class AvatarRuntime:
    def __init__(self,e,g):
        self.e=e;self.g=g;self.delivery=[];self.allocated=[];self.lookup_log=[]
        original=e.models[0x1405336c0];self.session=SessionRuntime72(e,5,7);e.models[0x1405336c0]=original
        for off in [0xbed0,0x31e60]:e.write(GLOBAL+off,e.read(GLOBAL+0x204d0))
        p=e.alloc(2080);e.write(p+24,2048,'I');t=permutation_table(13,29);e.uc.mem_write(p+32,t[:1024]+t[2048:3072]);e.write(GLOBAL+0xe970,p);e.write(0x145872d30,0x12345678,'I')
        e.uc.mem_map(END,4096);e.uc.mem_write(RETURN,b'\xc3');e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE,e._code,begin=END,end=END+4095))
        self.klass=e.alloc(1024);e.write(self.klass+0xc8,1,'B');e.write(self.klass+0xc7,1,'B');hier=e.alloc();e.write(self.klass+0xc4,1,'B');e.write(self.klass+0x48,hier);e.write(hier,self.klass)
        for off,addr in [(0x190,0x148aa58d0),(0x198,0x148aa58e0),(0x158,0x152f80500),
                (0x108,0x152f7ed50),(0x140,0x152f80100),(0x170,0x1518407c0)]:e.write(self.klass+off,addr);e.allow(addr)
        info=e.alloc();ctx=e.alloc();e.write(info+0x18,ctx);e.write(ctx+0x10,self.klass);e.write(0x145990bc8,info)
        self.manager=e.alloc();self.model=e.alloc(512);self.network=e.alloc(512);e.write(self.network+0x180,self.model)
        e.write(GLOBAL+0xa650,self.manager);e.write(self.manager+0x10,self.guid_dict());e.write(self.model+0x168,12345,'I')
        for off in [0x38,0xb8,0xa0]:p=e.alloc();e.write(p+0x10,self.array(4));e.write(self.model+off,p)
        info=e.alloc();c=e.alloc();ctx=e.alloc();e.write(info,c);e.write(c+0x30,ctx);e.write(ctx+0x88,e.alloc());e.write(0x145a8ac10,info)
        e.models[0x1404c2eb0]=self.allocate
        e.models[0x15881f800]=lambda:0
        e.models[0x15184e100]=self.deliver
        e.models[0x151a9c360]=lambda:0
        e.write(GLOBAL+0xa420,e.alloc())
        listing=e.read(OBJECT+0x48);klass=e.alloc(1024);e.write(listing,klass);e.write(klass+0x230,e.model_stub(self.enumerate))
        for a in [0x1507cc240,0x1507cee50,0x14e9b2a80,0x14e9b2c60,0x15184e100,0x14b1046f0,0x14b1049f0,0x15184adf0,0x14aefa090,0x14aef9db0]:e.allow(a)
        for a in [0x1547e1dd0,0x156f6f510,0x156f6f850,0x1572292b0]:e.allow_unwind(a);e.models.pop(a,None)
        self.resource_dict=e.alloc();a=self.array(24);e.write(self.resource_dict+24,a);e.write(GLOBAL+0x30870,self.resource_dict)
        self.resource_rows={}
        for i,aid in enumerate([1001,1002,0xffffffff]):
            row=e.alloc(512);self.resource_rows[aid]=i;e.write(a+48+i*24,row)
            self.invoke(0x14aefa090,rcx=row+0x178,rdx=aid^0x1234)
        self.original_lookup=e.models[0x1578b66f0];e.models[0x1578b66f0]=self.resource_lookup
        self.dictionaries={};self.properties=[];self.events=[]
        self.pool=e.alloc();pc=e.alloc();e.write(self.pool,pc);e.write(pc+0xf0,e.model_stub(self.rent));e.write(pc+0xf8,e.model_stub(lambda:0));e.write(GLOBAL+0x5d60,self.pool)
        e.models[0x156ed8b50]=self.lookup;e.models[0x156ed8ea0]=self.insert
        e.models[0x156d36600]=self.contains;e.models[0x156d38ec0]=self.get;e.models[0x156d36540]=self.enumerate_props
        self.enumerator=e.alloc();c=e.alloc(1024);table=e.alloc(48);e.write(self.enumerator,c);e.write(c+0x80,table);e.write(c+0xc0,3,'H')
        for i,(cell,fn) in enumerate([(0x1458861a8,self.move_next),(0x1458a71f0,self.current),(0x1458861b0,lambda:0)]):
            interface=e.synthetic_type(cell);e.write(table+i*16,interface);e.write(table+i*16+8,i,'I');e.write(c+0xd0+i*8,e.model_stub(fn))
        e.write(GLOBAL+0x9e50,e.alloc());e.write(GLOBAL+0xa3a0,0)
        e.models[0x1532d6720]=self.event;e.models[0x140525c40]=self.box
        e.synthetic_type(0x1458baa98)
        for a in [0x152f7f670,0x152f805b0,0x14a1b7ec0,0x14a1b8bf0]:e.allow(a)
        self.observer=e.uc.hook_add(e.U.UC_HOOK_CODE,self.observe)
        self.inventory=e.alloc();e.write(self.inventory+0x68,self.guid_dict());e.write(GLOBAL+0xb4d0,self.inventory)
        info=e.alloc();c=e.alloc();ctx=e.alloc();e.write(info,c);e.write(c+0x30,ctx);e.write(ctx+0x88,e.alloc());e.write(0x145a6b490,info);e.write(0x145a6b498,info)
        self.equip_rows=[];self.equip_delivered=[];self.equip_index=[]
        relic_class=e.synthetic_type(0x1458aa7e0);h=e.alloc();e.write(relic_class+0xc4,1,'B');e.write(relic_class+0x48,h);e.write(h,relic_class)
        for idx,guid,aid,slot,cell in [(29353,0x100000001,10001,6,0x145899fb8),(52700,0x200000002,20001,1,0x14589df00)]:
            c=e.synthetic_type(cell);p=e.alloc();e.write(p,c);e.write(p+16,aid,'I');e.write(p+24,guid)
            if slot!=6:
                res=e.alloc();cfg=e.alloc(256);e.write(res,relic_class);e.write(res+32,cfg);e.write(cfg+0x7c,slot,'I');e.write(p+48,res)
            wrapper=e.alloc();e.write(wrapper+16,p)
            e.allow(0x14e43af70);self.equip_index.append(self.invoke(0x14e43af70,rcx=self.inventory,rdx=wrapper))
            self.equip_rows.append(dict(typeIndex=idx,guid=guid,id=aid,slot=slot))
        for a in [0x15184ec00,0x14e3da130]:e.allow(a)
        e.models[0x15184a5f0]=self.equip_sink
    def enumerate(self):
        e=self.e;p=e.reg('rcx');back=e.read(e.reg('rdx')+16)
        e.write(p,1,'B');e.write(p+8,e.read(back+16));e.write(p+16,e.read(back+24,'I'),'I');e.write(p+20,0xffffffff,'I');return p
    def array(self,w,n=32):
        p=self.e.alloc(32+w*n);self.e.write(p+24,n,'I');return p
    def guid_dict(self):
        e=self.e;p=e.alloc();e.write(p+0x10,self.array(4));e.write(p+0x28,self.array(24));e.write(p+0x44,0xffffffff,'I');return p
    def allocate(self):
        p=self.e.alloc(1024);self.e.write(p,self.klass);self.allocated.append(p);return p
    def deliver(self):
        self.delivery.append((self.e.reg('rcx'),self.e.reg('rdx')));return 0
    def invoke(self,entry,stops=None,**regs):
        self.e.write(STACK+0x100008,RETURN)
        return fragment(self.e,entry,stops or {RETURN},{'rsp':STACK+0x100008,**regs})
    def root(self):
        e=self.e;p=e.alloc();e.write(p+0x10,OBJECT);e.write(p+0x22,20143,'H')
        return self.invoke(0x1507cc240,{0x1507cf07a,0x1404c2be0},rcx=self.network,rdx=p)
    def identity(self,model,wire):
        e=self.e;e.models.pop(0x15184e100,None)
        tr=self.invoke(0x15184e100,{0x15184e1e4},rcx=model,rdx=wire,r8=0)
        dec=self.invoke(0x14b1049f0,rcx=model+0x250)
        aid=e.reg('eax');resource=self.invoke(0x15184adf0,rcx=model)
        return dict(trace=tr,id=aid,guid=e.read(model+0x38),decode=dec,resource=resource,resourceValue=e.reg('eax'))
    def resource_lookup(self):
        if self.e.reg('rcx')!=self.resource_dict:return self.original_lookup()
        key=self.e.reg('edx');self.lookup_log.append(key);return self.resource_rows.get(key,0xffffffff)
    rent=PlayerContract72.rent
    lookup=PlayerContract72.lookup
    insert=PlayerContract72.insert
    contains=PlayerContract72.contains
    get=PlayerContract72.get
    enumerate_props=PlayerContract72.enumerate
    move_next=PlayerContract72.move_next
    current=PlayerContract72.current
    def nodes(self,pointer):
        info=self.g.maps.message.dictionaries[self.e.read(pointer+16)]
        return [(self.e.read(n+40,'I'),self.e.read(n+48)) for n in info['nodes']]
    def box(self):
        e=self.e;p=e.alloc();e.write(p,e.reg('rcx'));e.uc.mem_write(p+16,bytes(e.uc.mem_read(e.reg('rdx'),16)));return p
    def event(self):
        e=self.e;p=e.reg('rdx');obj=e.read(p+8)
        self.events.append(dict(id=e.read(p,'I'),guid=e.read(obj+16),newValue=e.read(obj+24,'f'),oldValue=e.read(obj+28,'f')));return 0
    def observe(self,uc,a,size,data):
        e=self.e
        if a==0x152f7ed50:self.properties.append(dict(type=e.reg('edx'),raw=struct.unpack('<q',e.reg('r8').to_bytes(8,'little'))[0]))
        if a==0x152f7f07c:self.properties[-1]['stored']=struct.unpack('<d',(e.reg('xmm0')&((1<<64)-1)).to_bytes(8,'little'))[0]
    def prop(self,model,wire):
        e=self.e;p=e.read(wire+0x70);e.write(e.read(p+24)+24,len(self.nodes(p)),'I')
        start,end=len(self.properties),len(self.events)
        tr=self.invoke(0x15184e3ad,{0x15184e3bf},rdi=model,rbx=wire)
        values={};reads=[]
        for key in [4001,1001,1002]:
            reads.append(self.invoke(0x152f805b0,rcx=model,rdx=key));values[key]=struct.unpack('<d',(e.reg('xmm0')&((1<<64)-1)).to_bytes(8,'little'))[0]
        return dict(trace=tr,reads=reads,values=values,properties=self.properties[start:],events=self.events[end:])
    def equip_sink(self):
        e=self.e;p=e.reg('r8') or e.reg('r9')
        self.equip_delivered.append(dict(slot=e.reg('edx'),guid=e.read(p+24),id=e.read(p+16,'I')));return 0
    def equipment(self,model,wire):
        e=self.e;l=e.read(wire+24);c=e.alloc(1024);e.write(l,c)
        e.write(c+0x1d8,e.model_stub(lambda:e.read(e.reg('rcx')+24,'I')))
        e.write(c+0x250,e.model_stub(lambda:e.read(e.read(e.reg('rcx')+16)+32+e.reg('edx')*8)))
        listing=e.alloc();e.write(listing+16,self.array(8));e.write(model+0x68,listing)
        start=len(self.equip_delivered)
        tr=self.invoke(0x15184e2d6,{0x15183eae0,0x1404c2be0},rdi=model,rbx=wire)
        return dict(trace=tr,delivered=self.equip_delivered[start:])

    def resource_paths(self):
        e=self.e;p=e.alloc();e.write(0x145b4b6f0,p)
        for a in [0x14f33b360,0x14f33b0c0]:e.allow(a)
        getter=self.invoke(0x14f33b360)
        if e.reg('rax')!=p:raise ValueError('Avatar path getter changed')
        loader=self.invoke(0x14f33b0c0,{0x14d6e1570})
        if e.reg('rcx')!=p:raise ValueError('Avatar resource I/O argument changed')
        return dict(getter=getter,loader=loader,fileIOExecuted=False)


def expected_properties(item):
    # Native protobuf map overwrites the value at an existing outer key; the
    # separate property consumer chooses the child property ID, not that key.
    entries={p[0]:p for p in item['props']};values={1001:0.,1002:0.,4001:0.};seen=set();history=[];events=[]
    for key,prop,branch,value,independent in entries.values():
        raw=value if branch==2 else 0;converted=float(raw)
        history.append(dict(type=prop,raw=raw,stored=converted))
        if prop in seen and prop in (1002,4001) and values[prop]!=converted:
            f=lambda x:struct.unpack('<f',struct.pack('<f',x))[0]
            events.append(dict(id=367 if prop==4001 else 368,guid=item['guid'],oldValue=f(values[prop]),newValue=f(converted)))
        values[prop]=converted;seen.add(prop)
    return dict(values=values,properties=history,events=events)


def verify(binary,records,directory,libraries,cells,bindings,outer_bindings,outer):
    good=[]
    for case in fixture_cases():
        e=Offline72(binary,libraries,records,trace_instructions=False);g=AvatarContractGraph(e,directory,cells,bindings,outer_bindings,outer)
        try:
            reader=g.run_graph(ROOT,case['wire']);runtime=AvatarRuntime(e,g);paths=runtime.resource_paths()
            trace=runtime.root()
            if trace['stop']!='0x1507cf07a' or len(runtime.delivery)!=len(case['avatars']):raise ValueError('Actual Avatar list dispatch changed')
            rows=[];model_ordinals={};latest={}
            for (model,wire),item in zip(runtime.delivery,case['avatars']):
                ordinal=model_ordinals.setdefault(model,len(model_ordinals))
                identity=runtime.identity(model,wire)
                expected_resource=item['id']^0x1234 if item['id'] in (1001,1002,0xffffffff) else 0
                if identity['id']!=item['id'] or identity['guid']!=item['guid'] or identity['resourceValue']!=expected_resource:
                    raise ValueError('Avatar identity/resource consumer differs')
                props=runtime.prop(model,wire)
                if {k:props[k] for k in ('values','properties','events')}!=expected_properties(item):raise ValueError(case['name']+': Avatar native property values/events differ')
                equips=runtime.equipment(model,wire)
                expected=[dict(slot=x['slot'],guid=x['guid'],id=x['id']) for guid in item['equips'] for x in runtime.equip_rows if x['guid']==guid]
                if equips['delivered']!=expected or equips['trace']['stop']!='0x15183eae0':raise ValueError('Avatar equipment GUID/slot routing differs')
                rows.append(dict(modelOrdinal=ordinal,identity=identity,properties=props,equipment=equips))
                if item['guid']:latest[item['guid']]=(model,item['id'])
            lookups=[]
            for guid,(model,aid) in latest.items():
                lookup=runtime.invoke(0x14e9b2c60,rcx=runtime.manager,rdx=guid)
                if e.reg('rax')!=model:raise ValueError('Avatar GUID model lookup changed')
                decode=runtime.invoke(0x14b1049f0,rcx=model+0x250)
                if e.reg('eax')!=aid:raise ValueError('Last Avatar identity for a GUID changed')
                lookups.append(dict(guid=guid,id=aid,lookup=lookup,decode=decode))
            good.append(dict(name=case['name'],wire=case['wire'].hex(),reader=reader,dispatch=trace,
                resourcePaths=paths,allocatedModels=len(runtime.allocated),models=rows,indexReadback=lookups,
                equipmentFixture=runtime.equip_rows,equipmentIndex=runtime.equip_index,
                unknownRootTag3StoredRaw=e.read(runtime.model+0x128)))
        finally:g.close();e.close()
    return good


def rejections(binary,records,directory,libraries,cells,bindings,outer_bindings,outer):
    e=Offline72(binary,libraries,records,trace_instructions=False);g=AvatarContractGraph(e,directory,cells,bindings,outer_bindings,outer);bad=[]
    try:
        for name,index,wire,limit in [('recursion-limit',ROOT,'3200',0),('negative-avatar-length',ROOT,'32ffffffff0f',100),
                ('truncated-avatar',ROOT,'32030801',100),('invalid-tag',ROOT,'00',100),('guid-overflow',AVATAR,'10ffffffffffffffffff02',100),
                ('complex-avatar-outside-scope',AVATAR,'aa02020801',100)]:
            try:g.run_graph(index,bytes.fromhex(wire),limit)
            except ValueError as error:
                detail=str(error);scope='native-reader-error-helper' if 'Native error branch:' in detail else 'explicit-scope-refusal' if 'Nonempty child outside' in detail else 'explicit-input-model'
                bad.append(dict(name=name,wire=wire,typeIndex=index,recursionLimit=limit,error=detail,scope=scope))
            else:raise ValueError('Bad/out-of-scope Avatar input accepted')
        g.run_graph(ROOT,b'');rt=AvatarRuntime(e,g)
        trace=rt.invoke(0x1507cee50,{0x1404c2be0},rcx=rt.network,rdx=0)
        bad.append(dict(name='null-typed-input',scope='native-null-error-helper-boundary',native=trace))
    finally:g.close();e.close()
    return bad


def compact(value):
    relevant={hex(x) for x in [0x1507cc240,0x1507cee50,0x1547e1dd0,0x14e9b2a80,0x14e9b2c60,0x148aa58d0,0x148aa58e0,
        0x152f80500,0x156f6f510,0x156f6f850,0x1572292b0,0x15184e100,0x15184adf0,0x14b1046f0,0x14b1049f0,0x14aef9db0,
        0x152f7f670,0x152f7ed50,0x152f80100,0x1518407c0,0x152f805b0,0x14a1b7ec0,0x14a1b8bf0,0x15184ec00,0x14e3da130,
        0x14e43af70,0x15184a5f0]}
    if isinstance(value,list):return [compact(x) for x in value]
    if isinstance(value,dict):
        if 'executedAddresses' in value:
            return {k:value[k] for k in ('entry','stop','instructions','nativePathSHA256','models')}|dict(
                nativeEntries=sorted((relevant|{value['entry'],value['stop']})&set(value['executedAddresses'])))
        return {k:compact(v) for k,v in value.items() if k!='reader'}
    return value


def validate_report(report):
    errors=[]
    if (report.get('inputSHA256')!=HASHES or report.get('runtimeStatus')!='not-tested' or report.get('targetProtocolReady') is not False or
        report.get('completeAvatarHandler') is not False or report.get('fullAvatarRawClosure') is not False):errors.append('Avatar contract scope changed')
    if [(x.get('typeIndex'),x.get('tag'),x.get('semanticField')) for x in report.get('fieldBindings',[])]!=FIELDS:errors.append('Limited Avatar bindings changed')
    if report.get('unresolvedTags')!=UNKNOWN:errors.append('Unknown Avatar fields promoted')
    if report.get('propertyIds')!={str(k):v for k,v in PROPERTY_IDS.items()}:errors.append('Unapproved Avatar property enum added')
    if [x['name'] for x in report.get('fixtures',[])]!=[x['name'] for x in fixture_cases()]:errors.append('Avatar fixture coverage changed')
    if [(x['name'],x['scope']) for x in report.get('rejections',[])]!=[
        ('recursion-limit','native-reader-error-helper'),('negative-avatar-length','native-reader-error-helper'),
        ('truncated-avatar','native-reader-error-helper'),('invalid-tag','explicit-input-model'),('guid-overflow','explicit-input-model'),
        ('complex-avatar-outside-scope','explicit-scope-refusal'),('null-typed-input','native-null-error-helper-boundary')]:errors.append('Avatar rejection scope changed')
    for row,case in zip(report.get('fixtures',[]),fixture_cases()):
        if row['wire']!=case['wire'].hex() or len(row['models'])!=len(case['avatars']):errors.append('Independent Avatar wire/model count changed')
        if row['dispatch']['stop']!='0x1507cf07a':errors.append('Avatar list boundary changed')
        required=[0x1507cc240,0x1507cee50,0x1572292b0]
        if case['avatars']:required += [0x1547e1dd0,0x14e9b2c60,0x148aa58d0,0x14e9b2a80]
        if any(x['guid'] for x in case['avatars']):required += [0x156f6f850]
        if not native_contract(row['dispatch'],required,0x1507cf07a):errors.append('Native Avatar dispatch/factory replaced with fixture')
        expected_count=len({x['guid'] for x in case['avatars'] if x['guid']})+sum(x['guid']==0 for x in case['avatars'])
        if row['allocatedModels']!=expected_count:errors.append('Avatar GUID reuse changed')
        for model,item in zip(row['models'],case['avatars']):
            ident=model['identity'];expected_resource=item['id']^0x1234 if item['id'] in (1001,1002,0xffffffff) else 0
            if (ident['id'],ident['guid'],ident['resourceValue'])!=(item['id'],item['guid'],expected_resource):errors.append('Avatar native identity changed')
            expected=json.loads(json.dumps(expected_properties(item)))
            if {k:model['properties'][k] for k in expected}!=expected:errors.append('Avatar property ID/value/encoding changed')
            equipment=[dict(slot=x['slot'],guid=x['guid'],id=x['id']) for guid in item['equips'] for x in row['equipmentFixture'] if x['guid']==guid]
            if model['equipment']['delivered']!=equipment:errors.append('Avatar equipment GUID source changed')
            if (not native_contract(ident['trace'],[0x15184e100,0x14b1046f0],0x15184e1e4) or
                not native_contract(ident['decode'],[0x14b1049f0],RETURN) or
                not native_contract(ident['resource'],[0x15184adf0,0x14b1049f0],RETURN)):
                errors.append('Native Avatar ID/resource codec replaced with fixture')
            required=[0x152f7f670,0x152f80100]
            if item['props']:required += [0x152f7ed50,0x1518407c0,0x14b1046f0,0x14a1b7ec0,0x14a1b8bf0]
            if not native_contract(model['properties']['trace'],required,0x15184e3bf):errors.append('Native Avatar property codec/callback replaced with fixture')
            required=[0x15184ec00]
            if item['equips']:required += [0x14e3da130,0x156f6f510]
            if not native_contract(model['equipment']['trace'],required,0x15183eae0):errors.append('Native Avatar equipment lookup replaced with fixture')
        for lookup in row['indexReadback']:
            if not native_contract(lookup['lookup'],[0x14e9b2c60,0x156f6f510],RETURN):errors.append('Native Avatar GUID lookup changed')
    return errors


def native_contract(trace,entries,stop):
    expected={hex(x) for x in entries}
    return trace['stop']==hex(stop) and expected<=set(trace['nativeEntries']) and not expected.intersection(trace['models'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('client','output','observations'):p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--directory',type=Path,default=Path('protocol/cn-7.2'))
    p.add_argument('--python-libs',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    outputs=[a.output.resolve(),a.observations.resolve()]
    if len(set(outputs))!=2 or any(x.is_relative_to(a.client.resolve()) for x in outputs):p.error('Distinct outputs outside client required')
    b,m,s=[read_verified(a.client/name,digest) for name,digest in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);declared=declarations(b,m,s,records,a.directory)
    outer=json.loads((a.directory/'avatar-outer-observations.json').read_text('utf8'))
    cells=type_cells(b,m,s,records);bindings=core_aot(b,m,s,records);outer_bindings=avatar_aot(b,m,s,records,dict(types=[]))
    good=verify(b,records,a.directory,a.python_libs,cells,bindings,outer_bindings,outer)
    bad=rejections(b,records,a.directory,a.python_libs,cells,bindings,outer_bindings,outer)
    context=dict(schemaVersion=1,task='F-03d3f1',inputSHA256=HASHES,declarations=declared,
        runtimeStatus='not-tested',targetProtocolReady=False,completeAvatarHandler=False,fullAvatarRawClosure=False,
        originalClientNamesRecovered=False,identityBasis='inferred-native-avatar-resource-model-property-events-and-equipment-slots',
        semanticTypes=[dict(typeIndex=t,semanticName=n,status='verified') for t,n in [(ROOT,'AvatarDataNotify'),(AVATAR,'AvatarInfo')]],
        fieldBindings=[dict(typeIndex=t,tag=tag,semanticField=name,status='verified') for t,tag,name in FIELDS],propertyIds=PROPERTY_IDS,
        unresolvedTags=UNKNOWN,
        fixtures=good,nativeFixtures=len(good),rejections=bad,
        models=['Typed class/vtable/constructor storage; actual generic factory and GUID setter/getter/index execute',
            'Primitive input and reader default containers; native root/AvatarInfo/PropValue reader and collection helpers execute',
            'List enumerator initialization from actual native reader backing array; MoveNext and GUID factory loop execute',
            'Initial unrelated root list storage and team-registration boundary; actual Avatar application delivered to sink then split',
            'Named Avatar resource rows/dictionary storage and reversible integer/double codec initialization/Random fixture',
            'Normal-property enumerator over actual map nodes; encoded dictionary and pool storage; real setter/callback/readback',
            'Weapon/Reliquary models are declared fixture inputs covered by fixed Equip contract, not newly constructed here',
            'Actual equipment GUID list storage; native inventory lookup/slot routing; slot assignment remains delivery boundary'])
    root=Path(__file__).resolve().parents[1]
    context['sourceSHA256']={n:hashlib.sha256((root/n).read_bytes()).hexdigest() for n in [
        'tools/client72_avatar_contract.py','tools/trace_client72_avatar_core.py','tools/trace_client72_avatar_outer.py',
        'tools/client72_player_data_contract.py','tools/client72_graph.py','tools/client72_map_runtime.py','tools/client72_session_runtime.py']}
    summary=json.loads(json.dumps(compact(context)));errors=validate_report(summary)
    if errors:raise ValueError(errors)
    for path,data in zip(outputs,[context,summary]):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n','utf8')
    print(json.dumps(dict(nativeFixtures=len(good),rejections=len(bad),verifiedFields=len(FIELDS))))


if __name__=='__main__':main()
