"""Bounded data-consumer storage; all scene/data bodies execute natively."""
try:
    from .client72_emulator import GLOBAL, OBJECT
    from .client72_scene_nested import SceneNestedReader72
    from .client72_scene_reader import SCENE
    from .client72_scene_list import SceneListRuntime72
except ImportError:
    from client72_emulator import GLOBAL, OBJECT
    from client72_scene_nested import SceneNestedReader72
    from client72_scene_reader import SCENE
    from client72_scene_list import SceneListRuntime72

DATA, FIRST, CACHE, UNKNOWN = 0x153A16AA0, 0x153A16B30, 0x153A3CD60, 0x14AF3D130


class SceneDataReader72(SceneNestedReader72):
    def run_scene(self, wire, recursion_limit=100):
        # Same CPU/object, same native constructor/reader; only the result view is Graph.
        return self.run_message(SCENE, wire, recursion_limit)


class SceneDataRuntime72:
    def __init__(self, reader, owner, message, key, flag, name, ifix_table):
        self.reader, self.e = reader, reader.e
        e = self.e; self.owner, self.message, self.key, self.flag = owner, message, key, flag
        self.source = e.read(message + 0x18); self.child = e.read(message + 0x20)
        self.before = reader.snapshot(message); self.ingress = []
        # Keep owner+60 (outer list cache) independent from this owner+1F8 cache.
        temporary_owner = e.alloc(1024)
        self.storage = SceneListRuntime72(reader, temporary_owner, self.source, key, [99, 88], 8)
        e.write(owner + 0x1F8, self.storage.dictionary)
        # Retain the caller's allocator tracking; this data cache already exists.
        e.models[0x1405336C0] = self.storage.original_models[0x1405336C0]
        klass = reader.list_class
        e.models[e.read(klass + 0xD0)] = self.count
        e.models[e.read(klass + 0xD0 + 5 * 8)] = self.copy_to
        self.interface_calls = []
        e.write(0x145B8DFEC, 1, 'B')  # Initialized metadata, not an IFix-success input.
        for address, enabled in ((0x145832FBE, name == 'data-ifix'), (0x1458330AB, name == 'first-ifix'),
                                 (0x1458330B5, name == 'cache-ifix'), (0x145832FE6, False)):
            e.write(address, int(enabled), 'B')
        e.write(GLOBAL + 0x232A0, ifix_table)
        for offset in (0x3E4390, 0x3E4AF8, 0x3E4B48): e.write(ifix_table + offset, e.alloc())
        e.synthetic_type(0x1458A93B8)
        if name == 'missing-cache-dictionary': e.write(owner + 0x1F8, 0)
        if name == 'null-cache-entry': e.write(self.storage.entries + 48, 0)
        for address in (DATA, FIRST, CACHE): e.allow(address)
        self.hook = e.uc.hook_add(e.U.UC_HOOK_CODE, self.observe_ingress)

    def observe_ingress(self, uc, address, size, _):
        e = self.e
        if address == DATA:
            self.ingress.append(dict(entry=hex(address), ownerMatches=e.reg('rcx') == self.owner,
                                     messageMatches=e.reg('rdx') == self.message, flag=e.reg('r8d')))
        elif address in (FIRST, CACHE):
            self.ingress.append(dict(entry=hex(address), ownerMatches=e.reg('rcx') == self.owner,
                                     rawKey=e.reg('edx'), sourceListMatches=e.reg('r8') == self.source, flag=e.reg('r9d')))

    def count(self):
        e = self.e; source = e.reg('rcx')
        if source not in self.reader.lists: raise ValueError('Count outside actual reader uint32 lists')
        value = e.read(source + 0x18, 'I')
        self.interface_calls.append(dict(kind='Count', dataList=source == self.source, count=value)); return value

    def copy_to(self):
        e = self.e; source, target, index = e.reg('rcx'), e.reg('rdx'), e.reg('r8d')
        if source not in self.reader.lists: raise ValueError('CopyTo outside actual reader uint32 lists')
        array, count = e.read(source + 0x10), e.read(source + 0x18, 'I')
        if array not in self.reader.arrays or target not in self.reader.arrays or count > self.reader.arrays[array] or index + count > self.reader.arrays[target]:
            raise ValueError('Data ICollection array boundary violated')
        if count: e.uc.mem_write(target + 32 + index * 4, bytes(e.uc.mem_read(array + 32, count * 4)))
        self.interface_calls.append(dict(kind='CopyTo', dataList=source == self.source, count=count, index=index)); return 0

    def result(self, stop):
        e = self.e
        boundary = None
        if stop == hex(UNKNOWN):
            pointer = e.reg('rcx'); array = e.read(pointer + 0x10); size = e.read(pointer + 0x18, 'I')
            boundary = dict(cacheListMatches=pointer == self.storage.dest, listPointer=hex(pointer), rawKey=e.reg('edx'),
                            items=[e.read(array + 32 + i * 4, 'I') for i in range(size)])
        return dict(messagePointer=hex(self.message), sourceListPointer=hex(self.source), child25866Pointer=hex(self.child),
                    graphBefore=self.before, graphAfter=self.reader.snapshot(self.message),
                    ingress=self.ingress, cacheBefore=self.storage.before, cacheAfter=self.storage.snapshot(),
                    nativeListWrites=self.storage.writes, dictionary=self.storage.dictionary_snapshot(),
                    interfaceCalls=self.interface_calls,
                    unknownArguments=boundary)

    def close(self):
        self.e.uc.hook_del(self.hook)
        self.storage.close()
