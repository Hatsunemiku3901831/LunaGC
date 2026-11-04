"""Offline native message graph execution with synthetic managed allocations.

ReadMessage/limits and concrete MergeFrom bodies execute in the target image.
Allocation, managed strings/bytes and selected runtime metadata are explicit
models. This is not an observed live runtime ABI.
"""
from pathlib import Path
import json
try:
    from .client72_emulator import OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY, HEAP
    from .client72_reader import WireInput
    from .trace_client72_responses import Reader72, CONTAINERS
    from .extract_client72 import HASHES
    from .client72_map_runtime import MapRuntime72
except ImportError:
    from client72_emulator import OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY, HEAP
    from client72_reader import WireInput
    from trace_client72_responses import Reader72, CONTAINERS
    from extract_client72 import HASHES
    from client72_map_runtime import MapRuntime72

TYPE_CELLS = {21921: 0x1458F1F70, 30171: 0x1458F1140, 31708: 0x1458D7848,
    37481: 0x1458C2398, 40503: 0x1459108C8, 44353: 0x1458CA658, 44443: 0x1458D57B0,
    45196: 0x14590EFD8, 46567: 0x1458DA338, 54602: 0x1458F1148, 56599: 0x1458BB4E8,
    59347: 0x1458F1138, 67060: 0x1458A2AB0, 70905: 0x1458F2D68, 80597: 0x1458D8EB8,
    81270: 0x1458C7770, 81413: 0x1458C90B8, 85772: 0x1458C23A0}


class GraphReader72(Reader72):
    def __init__(self, engine, directory):
        super().__init__(engine); e = engine
        self.schemas = {}
        for filename in ('login-wire-observations.json', 'response-leaf-wire-observations.json', 'response-wire-observations.json'):
            table=json.loads((Path(directory)/filename).read_text('utf8'))
            if table.get('inputSHA256')!=HASHES:raise ValueError('Graph schema input mismatch')
            self.schemas.update({r['typeIndex']: r for r in table['messages']})
        # Interface dispatch layout is read from 1474D7E60 itself.
        self.interface = e.alloc(); e.write(0x1458AAD80, self.interface)
        self.classes, self.class_indices, self.entries = {}, {}, {}
        for index, cell in TYPE_CELLS.items():
            row = e.records[index]
            entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
            klass = e.alloc(1024); interface_table = e.alloc(16)
            e.write(cell, klass); e.write(klass + 0xC7, 1, 'B')
            e.write(klass + 0x80, interface_table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(interface_table, self.interface); e.write(interface_table + 8, 0, 'I')
            e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index; self.entries[index] = entry
            e.allow(entry)
        for guard in (0x145B92FAE, 0x145B9274D, 0x145B934DB, 0x145B987C0): e.write(guard, 1, 'B')
        for entry in (0x1474D7E60, 0x14751FFB0, 0x1475210F0, 0x147521760, 0x147521000):
            e.models.pop(entry, None); e.allow(entry)
        for entry in CONTAINERS: e.models.pop(entry, None)
        self.setup_lists()
        self.maps=MapRuntime72(self,directory)
        e.models.update({0x1405336C0: self.allocate, 0x14D4480C0: self.allocate,
                         0x14051A870: lambda: 0, 0x14751FD30: self.raw_byte, 0x159C69AF0: self.array_copy})
        e.allow_unwind(0x14051B7B0)
        for entry, name in ((0x1509CAFA0, 'negative-length'), (0x1509CB360, 'truncated-message'),
                            (0x1509CB410, 'recursion-limit'), (0x1509CB200, 'invalid-end-tag')):
            e.models[entry] = lambda name=name: self.reject_native(name)
        self.heap_floor = e.heap_cursor
        e.before_instruction = self.observe_entry
        self.graph_hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, self.graph_write, begin=HEAP, end=HEAP+0x1FFFFF)
        self.active = False

    def setup_lists(self):
        e = self.e
        for cell, offset in ((0x14592A0F0, 0), (0x14592A1A0, 8), (0x1458974B0, None), (0x14589BED0, None)):
            klass = e.synthetic_type(cell); block = e.alloc(); e.write(klass+0x38, block)
            if offset is not None: e.write(block+offset, 1, 'B')
        for static, tag, width in ((0x174D0, 3010, 32), (0x2C340, 18, 64)):
            codec, delegate = e.alloc(), e.alloc()
            e.write(codec+0x18, delegate); e.write(codec+0x34, tag, 'I')
            e.write(delegate+0x18, 0x147520110 if width==32 else 0x1475202D0); e.write(delegate+0x40, STREAM)
            e.write(GLOBAL+static, codec)
        for entry in (0x158DB8E10, 0x1571DDD60, 0x158DB8BC0, 0x1571DDB10, 0x1571DCBE0): e.allow_unwind(entry)
        for static, tag, index in ((0x2FEC0, 8146, 44443), (0x2FED0, 4466, 45196)):
            codec = e.alloc(); e.write(codec+0x10, tag, 'I'); e.write(codec+0x18, self.classes[index]); e.write(GLOBAL+static, codec)
        # Only the already materialized RGCTX slots used by fixed-capacity append.
        method, klass, context = e.alloc(), e.alloc(), e.alloc(512)
        e.write(method, klass); e.write(klass+0x30, context)
        meta2, klass2, context2 = e.alloc(), e.alloc(), e.alloc()
        e.write(context+0xC8, meta2); e.write(meta2, klass2); e.write(klass2+0x30, context2); e.write(context2+0x38, e.alloc())
        self.message_list_context = method
        e.allow_unwind(0x158DC41F0)

    def array_copy(self):
        e = self.e; src, dst = e.reg('rcx'), e.reg('r8'); start, end = e.reg('rdx'), e.reg('r9')
        count = e.read(e.reg('rsp')+0x28, 'I')
        if src not in self.arrays or dst not in self.arrays: raise ValueError('Unknown managed array')
        width, capacity = self.arrays[src]; dw, dc = self.arrays[dst]
        if width != dw or start+count > capacity or end+count > dc: raise ValueError('Managed array copy bounds/type')
        e.uc.mem_write(dst+32+end*width, bytes(e.uc.mem_read(src+32+start*width, count*width))); return 0

    def new_array(self, width, capacity=64):
        p = self.e.alloc(32+capacity*width); self.e.write(p+24, capacity, 'I'); self.arrays[p] = (width, capacity); return p

    def close(self):
        self.e.uc.hook_del(self.graph_hook); self.e.before_instruction = None; super().close()

    def reject_native(self, name): raise ValueError('Native error branch: ' + name)

    def sync(self, register='rcx'):
        stream=self.e.reg(register)
        if stream not in self.streams:raise ValueError('Unknown graph reader stream')
        if self.streams[stream] is None:
            array=self.e.read(stream+0x18);length=self.e.read(array+0x18,'I')
            if length>CAPACITY:raise ValueError('Secondary reader capacity')
            self.streams[stream]=WireInput(bytes(self.e.uc.mem_read(array+32,length)))
        self.stream=stream;self.input=self.streams[stream]
        self.input.position=self.e.read(stream+0x44,'I')
        self.input.limit = min(len(self.input.data), self.e.read(stream + 0x38, 'I'), self.e.read(stream + 0x40, 'I'))
        if self.input.position > self.input.limit: raise ValueError('Nested input limit exceeded')

    def position(self):self.e.write(self.stream+0x44,self.input.position,'I')

    def tag(self):
        self.sync(); e = self.e
        if e.read(self.stream + 0x25, 'B'):
            result = e.read(self.stream + 0x3C, 'I'); e.write(self.stream + 0x25, 0, 'B'); self.input.last_key = result
        else: result = self.input.tag(); self.position()
        e.write(self.stream + 0x20, result, 'I'); return result

    def raw_byte(self): self.sync(); value = self.input.take(1)[0]; self.position(); return value

    def allocate(self):
        klass = self.e.reg('rcx')
        mapped=self.maps.allocate(klass)
        if mapped is not None:return mapped
        if klass not in self.class_indices: raise ValueError(f'Unmodeled allocation class {klass:#x}')
        return self.new_message(self.class_indices[klass])

    def new_message(self, index, address=None):
        e = self.e; p = e.alloc(512) if address is None else address
        e.write(p, self.classes[index]); e.write(p + 0x14, 1, 'I')
        self.objects[p] = index
        for f in e.records[index]['fields']:
            if f['offsetKind'] != 'instance': continue
            t = f['type']; off = f['offset']
            if t['kind'] == 'string' or t['kind'] == 'class' and t['name'] == 'PPGGBLPHADP':
                pointer = e.alloc(); self.values[pointer] = dict(kind='string' if t['kind'] == 'string' else 'bytes', hex='')
                e.write(p + off, pointer)
            elif t['kind'] == 'genericinst':
                pointer = e.alloc(); self.containers[pointer] = dict(type=t, items=[])
                if len(t['arguments'])==1 and t['arguments'][0]['kind'] in ('uint32', 'uint64'):
                    width = 4 if t['arguments'][0]['kind']=='uint32' else 8
                    array = self.new_array(width); e.write(pointer+0x10, array)
                    e.write(pointer, e.read(0x1458974B0 if width==4 else 0x14589BED0))
                    self.containers[pointer].update(width=width, capacity=64)
                elif t['typeDefinition']==25060:
                    backing = e.alloc(); array=self.new_array(8); e.write(backing+0x10,array);e.write(pointer+0x10,backing)
                    self.containers[pointer].update(messageList=True, capacity=64)
                elif len(t['arguments'])==2:self.maps.create(pointer,t)
                e.write(p + off, pointer)
        return p

    def observe_entry(self, address):
        if not self.active: return
        self.maps.before(address)
        if address in (0x158DB8E10,0x1571DDD60,0x158DB8BC0,0x1571DDB10,0x1571DCBE0,0x158DC41F0):
            self.entered.append(dict(kind='container-helper',entry=hex(address)))
        if address == 0x1474D7E60:
            stream=self.e.reg('rcx')
            self.nested_calls.append(dict(typeIndex=self.objects.get(self.e.reg('rdx')), key=self.e.read(stream+0x20, 'I'),
                                          position=self.e.read(stream+0x44, 'I'), depth=self.e.read(stream+0x34, 'I')))
        if address == 0x158DC41F0:
            info=self.containers[self.e.reg('rcx')]
            if not info.get('messageList') or self.e.read(self.e.reg('r8')+0x18) != self.classes[info['type']['arguments'][0]['typeDefinition']]:
                raise ValueError('Message list codec/type mismatch')
            self.e.uc.reg_write(self.e.R.UC_X86_REG_R9, self.message_list_context)
        if address in self.entries.values():
            self.entered.append(dict(typeIndex=self.objects.get(self.e.reg('rcx')), entry=hex(address)))

    def memory_write(self, *args): self.graph_write(*args)

    def graph_write(self, uc, access, address, size, value, _):
        if not self.active: return
        for p, index in self.objects.items():
            if p+24 <= address < p+512:
                self.events.append(dict(typeIndex=index, objectOffset=address-p, key=self.e.read(STREAM+0x20, 'I'),
                                        size=size, source=hex(self.e.reg('rip')))); break
        node_store=self.maps.observe_store(address,size)
        if node_store:self.map_stores.append(node_store)

    def snapshot(self, pointer, depth=0):
        if depth > 20: raise ValueError('Snapshot recursion')
        if not pointer: return None
        index = self.objects[pointer]; result = {}; e = self.e
        for f in self.schemas[index]['fields']:
            if f.get('oneof') and e.read(pointer+f['discriminatorOffset'], 'I') != f['discriminatorValue']: continue
            off, kind = f['objectOffset'], f['type']
            if f['cardinality'] in ('map', 'repeated'):
                container = e.read(pointer+off); info = self.containers[container]
                if 'width' in info:
                    count = e.read(container+0x18, 'I'); array=e.read(container+0x10); width=info['width']
                    if count > info['capacity']: raise ValueError('List capacity exceeded')
                    value = [e.read(array+0x20+i*width, 'I' if width==4 else 'Q') for i in range(count)]
                elif info.get('messageList'):
                    backing=e.read(container+0x10); array=e.read(backing+0x10); count=e.read(backing+0x18,'I')
                    if count > info['capacity']: raise ValueError('Message list capacity exceeded')
                    value=[self.snapshot(e.read(array+32+i*8),depth+1) for i in range(count)]
                elif 'mapDictionary' in info:value=self.maps.snapshot(info['mapDictionary'],depth)
                else: value = info['items']
            elif kind == 'message': value = self.snapshot(e.read(pointer+off), depth+1)
            elif kind in ('string', 'bytes'):
                obj = self.values[e.read(pointer+off)]; value = bytes.fromhex(obj['hex']).decode('ascii') if kind == 'string' else obj
            else:
                fmt = 'B' if kind == 'bool' else 'd' if kind == 'double' else 'Q' if kind == 'uint64' else 'i' if kind in ('int32','sfixed32') else 'I'
                value = e.read(pointer+off, fmt)
                if kind == 'bool': value = bool(value)
            if value is not None and value != '' and value != 0 and value != [] and (value != {} or kind=='message' and f['cardinality']=='singular') and value != {'kind': 'bytes','hex': ''}:
                result[f['clientField']] = value
        return result

    def run_graph(self, index, data, recursion_limit=100):
        e = self.e; self.active = False; e.prepare(); e.heap_cursor = self.heap_floor
        if len(data) > CAPACITY: raise ValueError('Input exceeds fixture capacity')
        self.input = WireInput(data); self.objects, self.values, self.containers, self.arrays = {}, {}, {}, {}
        self.streams={STREAM:self.input};self.stream=STREAM;self.values.update(self.maps.static_values);self.maps.reset()
        self.events, self.nested_calls, self.entered, self.map_stores = [], [], [], []
        self.new_message(index, OBJECT)
        e.uc.mem_write(BUFFER+0x20, data); e.write(BUFFER+0x18, len(data), 'I')
        e.write(STREAM+0x40, len(data), 'I'); e.write(STREAM+0x38, 0x7FFFFFFF, 'I'); e.write(STREAM+0x2C, recursion_limit, 'I')
        self.active = True
        try: e.run(self.entries[index])
        finally: self.active = False
        if self.streams[STREAM].position != len(data) or e.read(STREAM+0x34, 'I') != 0 or e.read(STREAM+0x38, 'I') != 0x7FFFFFFF:
            raise ValueError('Unbalanced nested reader or incomplete input')
        return dict(typeIndex=index, wire=data.hex(), result=self.snapshot(OBJECT), nestedCalls=self.nested_calls,
                    entered=self.entered, stores=self.events,mapStores=self.map_stores)
