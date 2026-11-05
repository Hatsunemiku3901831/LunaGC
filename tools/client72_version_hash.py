"""Offline version-source/storage fixtures; native ASCII/SHA1/Base64 execute."""
try:
    from .client72_emulator import GLOBAL
except ImportError:
    from client72_emulator import GLOBAL

TYPE_BINDINGS = ((698841,0x145886C28,441),(699157,0x145887D90,430),
                 (699518,0x1458894B0,420),(699775,0x14588AA48,1056),(699905,0x14588B398,1055))
HASH_ENTRY, HASH_RETURN = 0x15321A4D0, 0x15A4E0DC7


class VersionHash72:
    def __init__(self, runtime, version_parts):
        self.rt=runtime; self.e=e=runtime.e; self.version_parts=version_parts
        self.version_calls=0; self.formats=[]; self.hash_inputs=[]; self.hash_outputs=[]
        self.old_allocate=e.models[0x1405336C0]
        self.classes={e.synthetic_type(cell):index for _,cell,index in TYPE_BINDINGS}
        for cls,index in self.classes.items():
            for method in e.records[index]['methods']:
                entry=int(method['registeredAddress'],16)
                if entry: e.allow(entry)
                if method['virtualSlot']!=65535: e.write(cls+0xd0+method['virtualSlot']*8,entry)
        for entry in (HASH_ENTRY,0x1531E9EB0,0x147CF2160,0x1531D02B0,0x159A8D050,0x159A8E9D0,
                      0x159BEDDE0,0x159BEDAF0,0x159B3DC50,0x159B05490): e.allow(entry)
        e.models.update({0x1405336C0:self.allocate,0x1531D03D0:self.version_source,0x159AB12F0:self.format_version})
        # Initialized standard '?' fallbacks: run their real constructors, then
        # place the results in the caches read by the native fallback getters.
        replacement=self.string('?')
        for cell,cache,constructor in ((0x145886C28,0x3c90,0x159CA1500),(0x145887D90,0x3c58,0x159C40EE0)):
            p=e.alloc(128);e.write(p,e.read(cell));runtime.session.call(constructor,p,replacement);e.write(GLOBAL+cache,p)

    def allocate(self):
        e=self.e;cls=e.reg('rcx')
        if cls not in self.classes:return self.old_allocate()
        p=e.alloc(128);e.write(p,cls);return p

    def string(self,text):
        raw=text.encode('utf-16-le');p=self.e.alloc(22+len(raw));self.e.write(p+16,len(raw)//2,'I')
        if raw:self.e.uc.mem_write(p+20,raw)
        return p

    def version_source(self):
        e=self.e;self.version_calls+=1
        outputs=[e.reg(r) for r in ('rcx','rdx','r8','r9')]+[e.read(e.reg('rsp')+o) for o in (0x28,0x30)]
        for output,text in zip(outputs,self.version_parts):e.write(output,0 if text is None else self.string(text))
        return 0

    def format_version(self):
        e=self.e;fmt=self.rt.text(e.reg('rdx'));args=e.reg('r8')
        if e.reg('rcx')!=0 or fmt!='{0}{1}mhy2020' or e.read(args+16)!=0 or e.read(args+24)!=e.read(GLOBAL+0x7e0):
            raise ValueError('Version hash format arguments outside reviewed two-string shape')
        inputs=[self.rt.text(e.read(args+o)) if e.read(args+o) else '' for o in (0,8)]
        text=inputs[0]+inputs[1]+'mhy2020';self.formats.append(dict(format=fmt,inputs=inputs,result=text))
        return self.string(text)

    def observe(self,address):
        e=self.e
        if address==0x15321A61E:
            p=e.reg('rdx');self.hash_inputs.append(bytes(e.uc.mem_read(p+32,e.read(p+24))))
        elif address==0x15321A623:
            p=e.reg('rax');self.hash_outputs.append(bytes(e.uc.mem_read(p+32,e.read(p+24))))
