"""Object[]/String metadata for the post-dispatch diagnostic branch.

The native cast check, thread comparison and no-log-sink path execute. There is
no model replacing the diagnostic method or forcing its return value.
"""
try:
    from .client72_emulator import GLOBAL
except ImportError:
    from client72_emulator import GLOBAL


class DispatchNotice72:
    def __init__(self, runtime):
        self.rt=runtime; self.e=e=runtime.e; self.calls=[]; self.cast_calls=0
        self.old_array=e.models[0x140514EE0]; self.old_string=runtime.string
        self.object_class=e.alloc(512); self.string_class=e.alloc(512)
        for p in (self.object_class,self.string_class): e.write(p+0xCA,0x20,'B')
        e.write(0x145BC95E0,self.object_class)
        cache=e.alloc(); e.write(cache,self.object_class-8); e.write(0x145BC8AD0,cache)
        self.array_type=e.synthetic_type(0x145886110); self.array_class=e.alloc(512)
        e.write(self.array_class+0xAC,8,'I')
        e.models[0x140514EE0]=self.allocate_array
        runtime.string=self.string
        for pointer in runtime.strings: e.write(pointer,self.string_class)
        e.models.pop(0x14051B7B0,None); e.allow_unwind(0x14051B7B0)
        for address in (0x1471623F0,0x147161FA0): e.allow(address)
        e.allow_unwind(0x1412D3860)
        e.allowed.extend(((0x141667EA0,0x141667EA5),(0x140E47320,0x140E47327)))
        # The existing native crypto OS fixture supplies GetCurrentThreadId=1.
        e.write(0x14576FA5C,1,'I')
        if e.read(GLOBAL+0x79E0): raise ValueError('Unexpected initialized log sink')

    def allocate_array(self):
        e=self.e
        if e.reg('rcx')!=self.array_type: return self.old_array()
        count=e.reg('edx')
        if count!=1: raise ValueError('Diagnostic object[] fixture expects one argument')
        p=e.alloc(40); e.write(p,self.array_class); e.write(p+24,count,'I')
        return p

    def string(self,text):
        p=self.old_string(text); self.e.write(p,self.string_class)
        return p

    def observe(self,address):
        e=self.e
        if address==0x14051B7B0:
            if (e.reg('rcx'),e.reg('rdx'))!=(self.object_class,self.string_class): raise ValueError('Diagnostic assignability input changed')
            self.cast_calls+=1
        if address==0x1471623F0:
            args=e.read(e.reg('rsp')+0x30)
            self.calls.append(dict(code=e.reg('r8d'),argument=self.rt.text(e.read(args+32))))
