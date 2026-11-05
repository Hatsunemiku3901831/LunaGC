"""Bounded native reader/clone storage for the CN72 type 46311 scene candidate.

Only type 84343 nested content is executed here. Other message content remains
unknown. Managed allocation, arrays, strings and codec delegates are fixtures;
the target constructor, ReadMessage/limits, reader and clone bodies execute.
"""
import struct
try:
    from .client72_emulator import OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY, HEAP
    from .client72_reader import WireInput
    from .trace_client72_responses import Reader72
except ImportError:
    from client72_emulator import OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY, HEAP
    from client72_reader import WireInput
    from trace_client72_responses import Reader72

SCENE, POSITION = 46311, 84343
READER, CONSTRUCTOR, CLONE, COPY = 0x1523856B0, 0x152387890, 0x152386C00, 0x152386CB0
POSITION_READER = 0x154048D40


class SceneReader72(Reader72):
    def __init__(self, engine):
        super().__init__(engine)
        e = self.e
        self.classes, self.class_indices = {}, {}
        interface = e.alloc(); e.write(0x1458AAD80, interface)
        for index, cell, entry in ((SCENE, 0x1458A8C10, READER), (POSITION, 0x145898730, POSITION_READER)):
            klass = e.synthetic_type(cell); table = e.alloc(16)
            e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(table, interface); e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index
            e.allow(entry)
        self.list_class = e.synthetic_type(0x1458974B0)
        self.list_static = e.alloc(); e.write(self.list_class + 0x38, self.list_static)
        self.array_class = e.synthetic_type(0x1458869F0)
        for cell, off in ((0x14592A0F0, 0), (0x14592A1A0, 8)):
            klass = e.synthetic_type(cell); data = e.alloc(); e.write(klass + 0x38, data); e.write(data + off, 1, 'B')
        self.codec = e.alloc(); delegate = e.alloc()
        e.write(self.codec + 0x18, delegate); e.write(self.codec + 0x34, 106, 'I')
        e.write(delegate + 0x18, 0x147520110); e.write(delegate + 0x40, STREAM)
        # Execute the actual .cctor and observe its factory argument and static store.
        self.factory_tags = []
        e.models[0x14E5A5E30] = self.codec_factory
        e.prepare(); e.run(0x1523876A0)
        if self.factory_tags != [106] or e.read(GLOBAL + 0xFC90) != self.codec:
            raise ValueError('Scene uint32 codec initializer drift')
        for entry in (0x1474D7E60, 0x14751FFB0, 0x1475210F0, 0x147521760, 0x147521000,
                      0x147520110, 0x1475211E0, 0x1475202D0):
            e.models.pop(entry, None); e.allow(entry)
        e.models.pop(0x158DB8E10, None)
        for entry in (0x158DB8E10, 0x158DB8BC0, 0x158DB7CD0, 0x14051B7B0): e.allow_unwind(entry)
        for entry in (CONSTRUCTOR, CLONE, COPY, 0x1523876D0, 0x154048960): e.allow(entry)
        e.models.update({0x1405336C0: self.allocate, 0x14D4480C0: self.allocate,
                         0x14051A870: self.constructor_info, 0x140514EE0: self.allocate_array,
                         0x159C69AF0: self.array_copy, 0x1474E97E0: self.float32,
                         0x147500E70: lambda: self.blob('string'), 0x14751FD30: self.raw_byte})
        self.ctor_infos = {}
        for index, constructor in ((SCENE, CONSTRUCTOR), (POSITION, 0x154048960)):
            info = e.alloc(64); e.write(info + 8, constructor); e.write(info + 0x2F, 2, 'B')
            self.ctor_infos[self.classes[index]] = info
        for address, name in ((0x1509CAFA0, 'negative-length'), (0x1509CB360, 'truncated-message'),
                              (0x1509CB410, 'recursion-limit'), (0x1509CB200, 'invalid-end-tag'),
                              (0x1509CB2B0, 'malformed-varint')):
            e.models[address] = lambda name=name: self.reject_native(name)
        self.heap_floor = e.heap_cursor
        self.entered = []
        self.graph_hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, self.graph_write, begin=HEAP, end=HEAP + 0x1FFFFF)

    def codec_factory(self):
        self.factory_tags.append(self.e.reg('ecx'))
        return self.codec

    def close(self):
        self.e.uc.hook_del(self.graph_hook)
        super().close()

    def reject_native(self, name):
        raise ValueError('Native error branch: ' + name)

    def sync(self, register='rcx'):
        super().sync(register)
        self.input.limit = min(len(self.input.data), self.e.read(STREAM + 0x38, 'I'), self.e.read(STREAM + 0x40, 'I'))
        if self.input.position > self.input.limit: raise ValueError('Nested input limit exceeded')

    def tag(self):
        self.sync(); e = self.e
        if e.read(STREAM + 0x25, 'B'):
            result = e.read(STREAM + 0x3C, 'I'); e.write(STREAM + 0x25, 0, 'B'); self.input.last_key = result
        else:
            result = self.input.tag(); self.position()
        e.write(STREAM + 0x20, result, 'I')
        return result

    def raw_byte(self):
        self.sync(); value = self.input.take(1)[0]; self.position(); return value

    def float32(self):
        self.sync(); raw = self.input.take(4); self.position()
        self.e.uc.reg_write(self.e.R.UC_X86_REG_XMM0, int.from_bytes(raw, 'little'))
        return 0

    def blob(self, kind):
        self.sync(); raw = self.input.blob(); self.position()
        if kind != 'string': raise ValueError('Only managed strings are bound here')
        text = raw.decode('utf8'); encoded = text.encode('utf-16-le')
        pointer = self.e.alloc(24 + len(encoded)); self.e.write(pointer + 0x10, len(encoded) // 2, 'I')
        if encoded: self.e.uc.mem_write(pointer + 0x14, encoded)
        self.values[pointer] = raw.hex()
        return pointer

    def allocate(self):
        e = self.e; klass = e.reg('rcx'); p = e.alloc(512)
        e.write(p, klass); e.write(p + 0x14, 1, 'I')
        if klass == self.list_class:
            self.lists.add(p)
        elif klass in self.class_indices:
            self.objects[p] = self.class_indices[klass]
        else:
            raise ValueError(f'Unbound scene allocation class {klass:#x}')
        return p

    def constructor_info(self):
        e = self.e
        if e.reg('rcx') not in self.ctor_infos: raise ValueError('Constructor reflection outside bound scene/position types')
        cursor = e.reg('rdx')
        if e.read(cursor): return 0
        e.write(cursor, 1)
        return self.ctor_infos[e.reg('rcx')]

    def new_array(self, capacity):
        p = self.e.alloc(32 + capacity * 4); self.e.write(p + 24, capacity, 'I')
        self.arrays[p] = capacity
        return p

    def allocate_array(self):
        if self.e.reg('rcx') != self.array_class: raise ValueError('Only uint32 array allocation is bound')
        capacity = self.e.reg('edx')
        if capacity > CAPACITY: raise ValueError('Array fixture capacity exceeded')
        return self.new_array(capacity)

    def array_copy(self):
        e = self.e; src, dst = e.reg('rcx'), e.reg('r8'); start, end = e.reg('rdx'), e.reg('r9')
        count = e.read(e.reg('rsp') + 0x28, 'I')
        if src not in self.arrays or dst not in self.arrays: raise ValueError('Unbound scene array copy')
        if start + count > self.arrays[src] or end + count > self.arrays[dst]: raise ValueError('Scene array copy bounds')
        if count: e.uc.mem_write(dst + 32 + end * 4, bytes(e.uc.mem_read(src + 32 + start * 4, count * 4)))
        return 0

    def memory_write(self, *args):
        self.graph_write(*args)

    def graph_write(self, uc, access, address, size, value, _):
        if not self.active: return
        for pointer, index in self.objects.items():
            if pointer + 24 <= address < pointer + 176:
                self.events.append(dict(typeIndex=index, objectOffset=address - pointer, size=size,
                                        key=self.e.read(STREAM + 0x20, 'I'), value=value & ((1 << (size * 8)) - 1),
                                        source=hex(self.e.reg('rip'))))
                break

    def snapshot(self, pointer=OBJECT):
        e = self.e; scalars, nested = {}, {}
        for field in e.records[SCENE]['fields']:
            if field['offsetKind'] != 'instance': continue
            off, kind = field['offset'], field['type']['kind']
            if kind == 'class':
                child = e.read(pointer + off)
                if child and self.objects.get(child) != POSITION: raise ValueError('Nested content outside traced position type')
                nested[hex(off)] = [e.read(child + x, 'I') for x in (24, 28, 32)] if child else None
            elif kind not in ('string', 'genericinst'):
                scalars[hex(off)] = e.read(pointer + off, 'B' if kind == 'bool' else 'Q' if kind == 'uint64' else 'I')
        items = e.read(pointer + 0x20); array = e.read(items + 0x10); count = e.read(items + 0x18, 'I')
        if count > self.arrays[array]: raise ValueError('Scene list count exceeds fixture allocation')
        return dict(rawStoredScalars=scalars, positionFloat32Bits=nested,
                    repeatedUint32=[e.read(array + 32 + 4 * i, 'I') for i in range(count)],
                    stringUTF8Hex=self.values[e.read(pointer + 0x38)])

    def run_scene(self, wire, recursion_limit=100):
        e = self.e; self.active = False; e.prepare(); e.heap_cursor = self.heap_floor
        if len(wire) > CAPACITY: raise ValueError('Scene wire exceeds fixture capacity')
        self.input = WireInput(wire); self.events, self.values, self.objects, self.arrays, self.lists = [], {}, {}, {}, set()
        self.empty = e.alloc(24); self.values[self.empty] = ''; e.write(0x145AC9E50, self.empty)
        e.write(self.list_static, self.new_array(0))
        e.write(OBJECT, self.classes[SCENE]); e.write(OBJECT + 0x14, 1, 'I'); self.objects[OBJECT] = SCENE
        e.run(CONSTRUCTOR)
        defaults = self.snapshot()
        e.uc.mem_write(BUFFER + 0x20, wire); e.write(BUFFER + 0x18, len(wire), 'I')
        e.write(STREAM + 0x40, len(wire), 'I'); e.write(STREAM + 0x38, 0x7FFFFFFF, 'I'); e.write(STREAM + 0x2C, recursion_limit, 'I')
        self.active = True
        try: e.run(READER)
        finally: self.active = False
        if self.input.position != len(wire) or e.read(STREAM + 0x34, 'I') or e.read(STREAM + 0x38, 'I') != 0x7FFFFFFF:
            raise ValueError('Unbalanced scene reader or incomplete input')
        return dict(wire=wire.hex(), constructorDefaults=defaults, storedObject=self.snapshot(), stores=self.events,
                    models=[call['address'] for call in e.calls])
