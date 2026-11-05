"""Managed-library/environment fixtures for the native SDK update method.

Business methods and native export wrappers execute. Export table and preference
backend are explicitly uninitialized; this is not an actual SDK initialization.
"""
import json
try:
    from .client72_emulator import GLOBAL
except ImportError:
    from client72_emulator import GLOBAL

SDK_ENTRY = 0x15275F080
CELLS = ((0x1458875C8,4354),(0x14588F3E8,4356),(0x14588F408,4358),(0x145886158,452))


class SDKUpdate72:
    def __init__(self, runtime, app, version, channel, language, languages):
        self.rt=runtime; self.e=e=runtime.e; self.languages=languages
        self.objects={}; self.builders={}; self.maps={}; self.arrays={}; self.boxes={}
        self.outputs=[]; self.releases=[]; self.entries=0
        self.old_alloc=e.models.get(0x1405336C0); self.old_box=e.models.get(0x140525C40)
        self.old_clear=e.models.get(0x159C68C70)
        self.classes={e.synthetic_type(cell):index for cell,index in CELLS}
        for cell in (0x1458875D0,0x1458ADCE0,0x1458AE590,0x145898EB0,0x145898EB8): e.synthetic_type(cell)
        e.write(0x145B815AB,1,'B'); e.write(0x145B9783C,1,'B')
        e.write(runtime.sdk+0x68,runtime.string(app))
        config=e.alloc(); e.write(config+0x18,runtime.string(channel)); e.write(GLOBAL+0x11AB0,config)
        e.write(GLOBAL+0x29F30,e.alloc()); e.write(GLOBAL+0x141B8,e.alloc())
        self.language_class=e.synthetic_type(0x1458AEE08); e.write(0x145870BC0,language,'I')
        e.write(self.language_class+0xE8,e.model_stub(self.enum_text))
        self.pool=e.alloc(); poolclass=e.alloc(1024); e.write(self.pool,poolclass); e.write(GLOBAL+0x5D60,self.pool)
        e.write(poolclass+0xF0,e.model_stub(self.rent)); e.write(poolclass+0xF8,e.model_stub(self.release))
        # Exact caller-used RGCTX slot for Dictionary<string,string>.TryInsert.
        meta=e.alloc(); cls=e.alloc(); ctx=e.alloc(256)
        e.write(0x14596D550,meta); e.write(meta,cls); e.write(cls+0x30,ctx)
        self.insert_meta=e.alloc(); e.write(ctx+0x88,self.insert_meta)
        context=e.alloc(1024); e.write(0x1455AFED0,context)
        gpu=e.alloc(64); e.uc.mem_write(gpu,b'gpu-fixture\0'); e.write(context,gpu)
        e.write(0x14572BC18,0); e.write(0x1457A98B0,0)
        for cls,index in self.classes.items():
            if index==4354: e.write(cls+0x178,e.model_stub(self.write_json))
        e.models.update({0x1405336C0:self.allocate,0x1597AE4E0:self.json_ctor,0x1597C35D0:self.json_add,
                         0x159BABF40:self.builder_init,0x159B94F30:self.builder_string,
                         0x1414D30B0:self.native_string,0x159AACA10:self.string_equals,
                         0x1531DCB70:lambda:runtime.string(version),0x140AFCC40:self.cpu_source,
                         0x1404BBFC0:self.string_from_length,0x1404BBEC0:self.string_from_zero,
                         0x140525C40:self.box,0x15787D2A0:self.insert,0x159C68C70:self.clear_array})
        for address in (SDK_ENTRY,0x14947D950,0x153D34030,0x1527571F0,0x15275F990): e.allow(address)
        for address in (0x141447620,0x141447670,0x141454390,0x140AD0130,0x140AD1060,
                        0x141465DA0,0x141465E10,0x1585B97A0,0x159382140): e.allow_unwind(address)
        # Reviewed leaf thunks and direct branch continuation not given separate
        # PE unwind records. The last byte included is the actual native ret.
        e.allowed.extend(((0x140AD124F,0x140AD1357),(0x1419D9F00,0x1419D9F0F),
                          (0x1416BCDE0,0x1416BCDE7),(0x140AC4160,0x140AC417E),
                          (0x140AC4180,0x140AC419E),(0x14165BA40,0x14165BA48)))

    def allocate(self):
        e=self.e; cls=e.reg('rcx')
        if cls not in self.classes:
            if self.old_alloc is None: raise ValueError('Unknown SDK allocation')
            return self.old_alloc()
        p=e.alloc(512); e.write(p,cls); self.objects[p]={}
        return p

    def json_ctor(self):
        if self.classes[self.e.read(self.e.reg('rcx'))]!=4354: raise ValueError('SDK JSONObject constructor receiver')
        return 0

    def json_add(self):
        e=self.e; p=e.reg('r8'); index=self.classes[e.read(p)]
        if index==4356: value=self.rt.text(e.read(p+16))
        elif index==4358: value=bool(e.read(p+16,'B'))
        else: raise ValueError('SDK JSON value outside declared fixtures')
        self.objects[e.reg('rcx')][self.rt.text(e.reg('rdx'))]=value
        return 0

    def write_json(self):
        e=self.e
        self.builders[e.reg('rdx')]=json.dumps(self.objects[e.reg('rcx')],separators=(',',':'))
        return 0

    def builder_init(self): self.builders[self.e.reg('rcx')]=''; return 0
    def builder_string(self): return self.rt.string(self.builders[self.e.reg('rcx')])

    def native_buffer(self, dst, raw):
        e=self.e; p=e.alloc(len(raw)+1); e.uc.mem_write(p,raw+b'\0')
        e.uc.mem_write(dst,bytes(40)); e.write(dst,p); e.write(dst+24,len(raw),'I')
        return dst

    def native_string(self):
        return self.native_buffer(self.e.reg('rdx'),self.rt.text(self.e.read(self.e.reg('rcx'))).encode('ascii'))

    def cpu_source(self): return self.native_buffer(self.e.reg('rcx'),b'cpu-fixture')

    def zero_string(self, pointer):
        raw=bytearray()
        for i in range(2048):
            value=self.e.read(pointer+i,'B')
            if not value: return raw.decode('ascii')
            raw.append(value)
        raise ValueError('Native string outside fixture bound')

    def string_from_length(self):
        return self.rt.string(bytes(self.e.uc.mem_read(self.e.reg('rcx'),self.e.reg('edx'))).decode('ascii'))

    def string_from_zero(self): return self.rt.string(self.zero_string(self.e.reg('rcx')))

    def string_equals(self):
        if self.e.reg('r8d')!=4: raise ValueError('Unexpected StringComparison fixture')
        return int(self.rt.text(self.e.reg('rcx')).lower()==self.rt.text(self.e.reg('rdx')).lower())

    def rent(self):
        e=self.e
        if e.reg('rcx')!=self.pool: raise ValueError('SDK dictionary pool receiver')
        p=e.alloc(); entries=e.alloc(32+24*16); buckets=e.alloc(32+4*16)
        for a,width in ((entries,24),(buckets,4)):
            e.write(a+24,16,'I'); self.arrays[a]=(width,16)
        e.write(p+0x10,buckets); e.write(p+0x30,entries); self.maps[p]={}
        return p

    def release(self):
        e=self.e; p=e.reg('rdx')
        if e.reg('rcx')!=self.pool or p not in self.maps or e.read(p+0x44,'I')!=0: raise ValueError('Dictionary release/clear mismatch')
        entries=e.read(p+0x30)
        if any(e.uc.mem_read(entries+32,24*len(self.maps[p]))): raise ValueError('Native dictionary entries not cleared')
        self.releases.append(dict(entriesBeforeClear=len(self.maps[p]),countAfterClear=0))
        return 0

    def enum_text(self): return self.rt.string(self.languages[self.e.read(self.e.reg('rcx')+16,'I')])

    def box(self):
        e=self.e
        if e.reg('rcx')!=self.language_class:
            if self.old_box is None: raise ValueError('Unknown SDK boxing type')
            return self.old_box()
        p=e.alloc(); e.write(p,self.language_class); e.write(p+16,e.read(e.reg('rdx'),'I'),'I')
        self.boxes[p]=77368
        return p

    def insert(self):
        e=self.e; p=e.reg('rcx'); k=e.reg('rdx'); v=e.reg('r8'); name=self.rt.text(k)
        if p not in self.maps or e.reg('r9d')!=2 or e.read(e.reg('rsp')+0x28)!=self.insert_meta: raise ValueError('Dictionary helper binding changed')
        if name in self.maps: raise ValueError('Duplicate dictionary key outside fixture')
        i=e.read(p+0x44,'I')
        if i>=16: raise ValueError('Dictionary fixture capacity')
        self.maps[p][name]=self.rt.text(v); entries=e.read(p+0x30)
        e.write(entries+32+i*24,0,'i'); e.write(entries+40+i*24,k); e.write(entries+48+i*24,v)
        e.write(p+0x44,i+1,'I'); e.write(p+0x40,e.read(p+0x40,'I')+1,'I')
        return 1

    def clear_array(self):
        e=self.e; p=e.reg('rcx')
        if p not in self.arrays:
            if self.old_clear is None: raise ValueError('Unknown clear array')
            return self.old_clear()
        width,capacity=self.arrays[p]; start,count=e.reg('edx'),e.reg('r8d')
        if start+count>capacity: raise ValueError('SDK Array.Clear bounds')
        e.uc.mem_write(p+32+start*width,bytes(count*width))
        return 0

    def observe(self,address):
        if address==SDK_ENTRY: self.entries+=1
        if address in (0x140AC4160,0x140AC4180):
            if self.e.read(0x14572BC18)!=0: raise ValueError('Fixture unexpectedly initialized native SDK exports')
            self.outputs.append(dict(entry=hex(address),value=json.loads(self.zero_string(self.e.reg('rcx')))))
