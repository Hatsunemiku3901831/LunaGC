"""Explicit ICollection/array storage for the actual scene list consumers."""
try:
    from .client72_emulator import GLOBAL, HEAP
except ImportError:
    from client72_emulator import GLOBAL, HEAP

NONZERO, CONSTRUCTOR, INSERT_RANGE, TYPE_CHECK = 0x153A3CB70, 0x15881E210, 0x15881F800, 0x14051B7B0


class SceneListRuntime72:
    def __init__(self, reader, owner, source, key, existing=None, capacity=8, collection=True):
        self.reader, self.e = reader, reader.e
        e = self.e; self.owner, self.source, self.key = owner, source, key
        self.source_class = reader.list_class
        self.original_class = bytes(e.uc.mem_read(self.source_class, 512))
        self.original_models = dict(e.models)
        self.events, self.allocations, self.dest = [], [], 0
        self.list_class = e.read(0x14588CA20) or e.synthetic_type(0x14588CA20)
        interface = e.synthetic_type(0x1458A1EC0)
        e.write(interface + 0xCA, 0x20, 'B'); e.write(interface + 0xA0, 0x20, 'B')
        e.write(self.source_class + 0xCA, 0x20, 'B')
        depth = e.alloc(128); e.write(depth + 0x43, 0x25, 'B'); e.write(self.source_class + 0x58, depth)
        table = e.alloc(16); e.write(table, interface); e.write(self.source_class + 0x80, table)
        e.write(self.source_class + 0xC0, int(collection), 'H'); e.write(self.source_class + 0xBE, 32, 'H')
        for slot, callback in ((0, self.count), (5, self.copy_to)):
            e.write(self.source_class + 0xD0 + slot * 8, e.model_stub(callback))
            e.write(self.source_class + 0xD0 + (32 + slot) * 8, e.alloc())
        self.empty_array = reader.new_array(0); e.write(0x145B74628, self.empty_array)
        self.dictionary, self.entries, self.buckets = e.alloc(80), e.alloc(32 + 8 * 24), e.alloc(32 + 17 * 4)
        e.write(owner + 0x60, self.dictionary); e.write(self.dictionary + 0x18, self.entries); e.write(self.dictionary + 0x20, self.buckets)
        e.write(self.dictionary + 0x3C, 0xFFFFFFFF, 'I'); e.write(self.entries + 24, 8, 'I'); e.write(self.buckets + 24, 17, 'I')
        info, klass, context, binder = e.alloc(), e.alloc(), e.alloc(512), e.alloc()
        e.write(info, klass); e.write(klass + 0x30, context); e.write(context + 0x88, binder); e.write(0x145994308, info)
        if existing is not None:
            self.dest = e.alloc(80); e.write(self.dest, self.list_class); e.write(self.dest + 0x1C, 9, 'I')
            array = reader.new_array(capacity); e.write(self.dest + 0x10, array); e.write(self.dest + 0x18, len(existing), 'I')
            for i, value in enumerate(existing): e.write(array + 32 + i * 4, value, 'I')
            e.write(self.entries + 32, key & 0x7FFFFFFF, 'I'); e.write(self.entries + 36, 0xFFFFFFFF, 'I')
            e.write(self.entries + 40, key, 'I'); e.write(self.entries + 48, self.dest)
            e.write(self.dictionary + 0x38, 1, 'I'); e.write(self.buckets + 32 + (key & 0x7FFFFFFF) % 17 * 4, 1, 'I')
        self.before = self.snapshot()
        self.writes = []
        self.hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, self.observe_write, begin=HEAP, end=HEAP + 0x1FFFFF)
        allocator = e.models[0x1405336C0]
        def allocate():
            pointer = allocator()
            if e.reg('rcx') == self.list_class:
                self.dest = pointer; self.allocations.append(hex(pointer))
            return pointer
        e.models[0x1405336C0] = allocate
        for entry in (NONZERO, CONSTRUCTOR, INSERT_RANGE, TYPE_CHECK, 0x1578B66F0, 0x1578B6A20):
            if entry == NONZERO: e.allow(entry)
            else: e.allow_unwind(entry)

    def count(self):
        e = self.e
        if e.reg('rcx') != self.source: raise ValueError('ICollection.Count source pointer mismatch')
        result = e.read(self.source + 0x18, 'I')
        self.events.append(dict(kind='ICollection.Count', count=result)); return result

    def observe_write(self, uc, access, address, size, value, _):
        if self.dest and address - self.dest in (0x10, 0x18, 0x1C):
            self.writes.append(dict(offset=hex(address - self.dest), size=size, value=value,
                                    source=hex(self.e.reg('rip'))))

    def copy_to(self):
        e = self.e; source, target, index = e.reg('rcx'), e.reg('rdx'), e.reg('r8d')
        if source != self.source: raise ValueError('ICollection.CopyTo source pointer mismatch')
        array, count = e.read(source + 0x10), e.read(source + 0x18, 'I')
        if array not in self.reader.arrays or target not in self.reader.arrays or count > self.reader.arrays[array] or index + count > self.reader.arrays[target]:
            raise ValueError('ICollection.CopyTo explicit array bounds violated')
        raw = bytes(e.uc.mem_read(array + 32, count * 4))
        if raw: e.uc.mem_write(target + 32 + index * 4, raw)
        self.events.append(dict(kind='ICollection.CopyTo', count=count, destinationIndex=index)); return 0

    def snapshot(self):
        e = self.e
        if not self.dest: return None
        array, size, version = e.read(self.dest + 0x10), e.read(self.dest + 0x18, 'I'), e.read(self.dest + 0x1C, 'I')
        capacity = self.reader.arrays.get(array, 0)
        return dict(pointer=hex(self.dest), arrayPointer=hex(array), size=size, version=version, capacity=capacity,
                    items=[e.read(array + 32 + i * 4, 'I') for i in range(size)] if array else [],
                    rawArray=[e.read(array + 32 + i * 4, 'I') for i in range(capacity)] if array else [])

    def dictionary_snapshot(self):
        e = self.e; count = e.read(self.dictionary + 0x38, 'I')
        return [dict(hash=e.read(self.entries + 32 + i * 24, 'I'), next=e.read(self.entries + 36 + i * 24, 'i'),
                     key=e.read(self.entries + 40 + i * 24, 'I'), value=hex(e.read(self.entries + 48 + i * 24))) for i in range(count)]

    def close(self):
        self.e.uc.hook_del(self.hook)
        self.e.models = self.original_models
        self.e.uc.mem_write(self.source_class, self.original_class)
