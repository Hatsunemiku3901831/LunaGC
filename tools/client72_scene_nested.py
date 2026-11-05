"""Native scene nested readers with explicitly bounded managed storage.

Extends the existing scene fixture without changing its schema or behavior.
The target constructors, MergeFrom, ReadMessage and uint32 list readers run.
"""
from collections import Counter
try:
    from .client72_emulator import OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY
    from .client72_reader import WireInput
    from .client72_scene_reader import SceneReader72, SCENE
    from .trace_client72_login import Writer72
    from .trace_client72_token import varint
except ImportError:
    from client72_emulator import OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY
    from client72_reader import WireInput
    from client72_scene_reader import SceneReader72, SCENE
    from trace_client72_login import Writer72
    from trace_client72_token import varint


# Metadata usage source and destination were independently decoded.
BINDINGS = (
    (25866, 702419, 177918, 0x145898760),
    (66040, 705266, 359110, 0x1458A8C18),
    (67761, 705267, 364259, 0x1458A8C20),
    (34133, 705268, 227141, 0x1458A8C28),
    (69612, 705269, 371125, 0x1458A8C30),
    (36137, 187787, 239473, 0x1458DAE08),
)
CTORS = {66040: 0x153F9FB20, 36137: 0x14EF01060, 67761: 0x14BF3A950,
         25866: 0x14BCF8BD0, 34133: 0x149B5F620, 69612: 0x14D0E9E10}
READERS = {66040: 0x153F9FA70, 36137: 0x14EF01160, 67761: 0x14BF3A8B0,
           25866: 0x14BCF8CF0, 34133: 0x149B5ECE0, 69612: 0x14D0E9630}
WRITERS = {66040: 0x153F9F920, 36137: 0x14EF00CF0, 67761: 0x14BF3A9A0,
           25866: 0x14BCF8B20, 34133: 0x149B5F0F0, 69612: 0x14D0E9BC0}
CODECS = ((0x14BF3A880, ((0x20DA0, 82),)),
          (0x14BCF8AC0, ((0xAC28, 58), (0xAC20, 50), (0xAC30, 10))),
          (0x14D0E9B90, ((0x30780, 106),)))


class SceneNestedWriter72(Writer72):
    """Native message writers; strings/message bytes/list encoding are models."""
    def __init__(self, engine):
        super().__init__(engine)
        self.codec_tags = {}
        self.factory_calls = []
        for _, _, _, cell in BINDINGS:
            engine.synthetic_type(cell)
        engine.synthetic_type(0x1458974B0)
        engine.models[0x14E5A5E30] = self.nested_codec
        engine.models[0x158DB8540] = self.repeated32
        for entry, stores in CODECS:
            before = len(self.factory_calls)
            engine.prepare()
            engine.run(entry)
            if self.factory_calls[before:] != [tag for _, tag in stores]:
                raise ValueError('Writer codec initializer argument mismatch')
            for offset, tag in stores:
                if self.codec_tags[engine.read(GLOBAL + offset)] != tag:
                    raise ValueError('Writer codec initializer destination mismatch')

    def nested_codec(self):
        pointer = self.e.alloc()
        tag = self.e.reg('ecx')
        self.codec_tags[pointer] = tag
        self.factory_calls.append(tag)
        return pointer

    def repeated32(self):
        e = self.e
        kind, values = self.objects[e.reg('rcx')]
        if kind != 'list32':
            raise ValueError('Nested writer requires uint32 list storage')
        tag = self.codec_tags[e.reg('r8')]
        if values:
            payload = b''.join(varint(value) for value in values)
            e.emit(varint(tag) + varint(len(payload)) + payload, stream=e.reg('rdx'))
        return 0

    def string(self):
        kind, raw = self.objects[self.e.reg('rdx')]
        if kind != 'string':
            raise ValueError('Nested string encoding model received another type')
        raw.decode('utf8')
        self.e.emit(varint(len(raw)) + raw)
        return 0

    def pointer(self, kind, value, class_cell=None):
        if kind != 'string':
            return super().pointer(kind, value, class_cell)
        encoded = value.decode('utf8').encode('utf-16-le')
        pointer = self.e.alloc(24 + len(encoded))
        self.objects[pointer] = (kind, value)
        self.e.write(pointer + 0x10, len(encoded) // 2, 'I')
        self.e.uc.mem_write(pointer + 0x14, encoded)
        return pointer


class SceneNestedReader72(SceneReader72):
    def __init__(self, engine):
        self.constructor_defaults = {}
        super().__init__(engine)
        e = self.e
        interface = e.read(0x1458AAD80)
        for index, _, _, cell in BINDINGS:
            klass = e.synthetic_type(cell)
            table = e.alloc(16)
            e.write(klass + 0x80, table)
            e.write(klass + 0xC0, 1, 'H')
            e.write(klass + 0xBE, 32, 'H')
            e.write(table, interface)
            e.write(klass + 0xE8, READERS[index])
            self.classes[index] = klass
            self.class_indices[klass] = index
            info = e.alloc(64)
            e.write(info + 8, CTORS[index])
            e.write(info + 0x2F, 2, 'B')
            self.ctor_infos[klass] = info
            e.allow(CTORS[index])
            e.allow(READERS[index])
        self.codec_tags = {self.codec: 106}
        self.nested_factory_calls = []
        e.models[0x14E5A5E30] = self.nested_codec
        for entry, stores in CODECS:
            before = len(self.nested_factory_calls)
            e.prepare()
            e.run(entry)
            if self.nested_factory_calls[before:] != [tag for _, tag in stores]:
                raise ValueError('Nested codec initializer arguments changed')
            for offset, tag in stores:
                if self.codec_tags[e.read(GLOBAL + offset)] != tag:
                    raise ValueError('Nested codec initializer destination changed')
        self.heap_floor = e.heap_cursor
        # The body has a lazy metadata setup guard. Supply initialized metadata;
        # this is distinct from its IFix branches, which remain disabled inputs.
        e.write(0x145B8BE66, 1, 'B')
        self.address_counts = Counter()
        self.previous_observer = e.before_instruction
        e.before_instruction = self.observe_native

    def observe_native(self, address):
        self.address_counts[address] += 1
        if self.previous_observer:
            self.previous_observer(address)

    def close(self):
        self.e.before_instruction = self.previous_observer
        super().close()

    def nested_codec(self):
        e = self.e
        tag = e.reg('ecx')
        pointer, delegate = e.alloc(), e.alloc()
        e.write(pointer + 0x18, delegate)
        e.write(pointer + 0x34, tag, 'I')
        e.write(delegate + 0x18, 0x147520110)
        e.write(delegate + 0x40, STREAM)
        self.codec_tags[pointer] = tag
        self.nested_factory_calls.append(tag)
        return pointer

    def allocate(self):
        pointer = super().allocate()
        index = self.class_indices.get(self.e.reg('rcx'))
        if index not in CTORS:
            return pointer
        # Parent allocation is an explicit managed-storage boundary. Supply only
        # defaults already observed from this type's separately executed ctor.
        defaults = self.constructor_defaults[index]['fields']
        e = self.e
        for field in e.records[index]['fields']:
            if field['offsetKind'] != 'instance': continue
            offset, kind = field['offset'], field['type']['kind']
            value = defaults[hex(offset)]
            if kind == 'genericinst':
                if value != []: raise ValueError('Unbound nonempty constructor list default')
                items = e.alloc(512)
                e.write(items, self.list_class)
                e.write(items + 0x14, 1, 'I')
                e.write(items + 0x10, e.read(self.list_static))
                self.lists.add(items)
                e.write(pointer + offset, items)
            elif kind == 'string':
                if value != '': raise ValueError('Unbound nonempty constructor string default')
                e.write(pointer + offset, self.empty)
            elif kind == 'class':
                if value is not None: raise ValueError('Unbound constructor child default')
            else:
                e.write(pointer + offset, value, 'B' if kind == 'bool' else 'Q' if kind == 'uint64' else 'I')
        return pointer

    def snapshot(self, pointer=OBJECT):
        e = self.e
        index = self.objects[pointer]
        fields = {}
        for field in e.records[index]['fields']:
            if field['offsetKind'] != 'instance':
                continue
            offset, kind = field['offset'], field['type']['kind']
            at = pointer + offset
            if kind == 'class':
                child = e.read(at)
                fields[hex(offset)] = self.snapshot(child) if child else None
            elif kind == 'string':
                fields[hex(offset)] = self.values[e.read(at)]
            elif kind == 'genericinst':
                items = e.read(at)
                array = e.read(items + 0x10)
                count = e.read(items + 0x18, 'I')
                if count > self.arrays[array]:
                    raise ValueError('Nested repeated count exceeds fixture array')
                fields[hex(offset)] = [e.read(array + 32 + i * 4, 'I') for i in range(count)]
            else:
                fields[hex(offset)] = e.read(at, 'B' if kind == 'bool' else 'Q' if kind == 'uint64' else 'I')
        return dict(typeIndex=index, fields=fields)

    def run_message(self, index, wire, recursion_limit=100):
        e = self.e
        self.active = False
        e.prepare()
        e.heap_cursor = self.heap_floor
        if len(wire) > CAPACITY:
            raise ValueError('Nested fixture exceeds bounded input capacity')
        self.input = WireInput(wire)
        self.events, self.values, self.objects, self.arrays, self.lists = [], {}, {}, {}, set()
        self.empty = e.alloc(24)
        self.values[self.empty] = ''
        e.write(0x145AC9E50, self.empty)
        e.write(self.list_static, self.new_array(0))
        e.write(OBJECT, self.classes[index])
        e.write(OBJECT + 0x14, 1, 'I')
        self.objects[OBJECT] = index
        self.address_counts.clear()
        constructor = 0x152387890 if index == SCENE else CTORS[index]
        entry = 0x1523856B0 if index == SCENE else READERS[index]
        e.run(constructor)
        defaults = self.snapshot()
        self.constructor_defaults[index] = defaults
        e.uc.mem_write(BUFFER + 0x20, wire)
        e.write(BUFFER + 0x18, len(wire), 'I')
        e.write(STREAM + 0x40, len(wire), 'I')
        e.write(STREAM + 0x38, 0x7FFFFFFF, 'I')
        e.write(STREAM + 0x2C, recursion_limit, 'I')
        self.active = True
        try:
            e.run(entry)
        finally:
            self.active = False
        if self.input.position != len(wire) or e.read(STREAM + 0x34, 'I') or e.read(STREAM + 0x38, 'I') != 0x7FFFFFFF:
            raise ValueError('Nested reader did not consume or balance message limits')
        return dict(typeIndex=index, wire=wire.hex(), constructorDefaults=defaults,
                    storedObject=self.snapshot(), stores=self.events,
                    models=[call['address'] for call in e.calls],
                    nativeInstructions=e.steps,
                    executedAddresses={hex(address): count for address, count in sorted(self.address_counts.items())})
