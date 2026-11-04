"""Observe protected response field dispatch with bounded, explicit reader models.

Nested messages and message/map containers accept EMPTY payloads only. Their
outer field bindings are observations; their content schemas are not recovered.
"""
import argparse
import json
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY
    from .client72_reader import WireInput
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, OBJECT, STREAM, BUFFER, GLOBAL, CAPACITY
    from client72_reader import WireInput
    from trace_client72_token import varint

ENTRIES = {37481: 0x148B7EA10, 40503: 0x14B89EC90}
# Static codec locations/tags recovered from each target .cctor; not server IDs.
CODECS = {0x174D0: (37481, 376), 0x2FEB0: (40503, 4), 0x2FEC8: (40503, 265),
          0x2FEB8: (40503, 1218), 0x2FEC0: (40503, 1018), 0x2FED0: (40503, 558)}
CONTAINERS = {0x158DB8E10: {35584}, 0x155F9BE70: {17961},
              0x158912430: {66210}, 0x156D35A40: {66210}, 0x158DC41F0: {25060}}


class Reader72:
    def __init__(self, engine):
        self.e = e = engine
        e.allow(0x14A42BD90)  # Execute real reference-count check, synthetic count=1.
        e.allow(0x147CF2D60)  # Execute real ByteString by-reference assignment wrapper.
        for cell in (0x145898960, 0x1458C2398, 0x1459108C8): e.synthetic_type(cell)
        for address, code in ((0x148B811A4, 'b9c20b0000'), (0x14B8A179A, 'c74010d21f0000'),
                              (0x14B8A1835, 'c740204a080000'), (0x14B8A18B7, 'c7402012260000'),
                              (0x14B8A1903, 'c7402022000000'), (0x14B8A193B, 'c7401072110000')):
            expected = bytes.fromhex(code)
            if bytes(e.uc.mem_read(address, len(expected))) != expected: raise ValueError('Response codec initializer changed')
        self.codecs = {}
        for offset, identity in CODECS.items():
            pointer = e.alloc(); e.write(GLOBAL + offset, pointer); self.codecs[pointer] = identity
        self.heap_floor = e.heap_cursor
        e.models.update({0x1474D8100: self.tag, 0x1474D7BB0: self.skip,
                         0x147520110: lambda: self.scalar(32), 0x1475202D0: lambda: self.scalar(64),
                         0x1475213F0: self.fixed64, 0x147500E70: lambda: self.blob('string'),
                         0x1475208D0: lambda: self.blob('bytes'), 0x1474D7E60: self.nested})
        for address in CONTAINERS: e.models[address] = lambda address=address: self.container(address)
        self.hook = e.uc.hook_add(e.U.UC_HOOK_MEM_WRITE, self.memory_write, begin=OBJECT + 24, end=OBJECT + 511)
        self.active = False

    def close(self): self.e.uc.hook_del(self.hook)

    def sync(self, register='rcx'):
        if self.e.reg(register) != STREAM: raise ValueError('Unexpected reader stream')
        self.input.position = self.e.read(STREAM + 0x44, 'I')
        if self.input.position > len(self.input.data): raise ValueError('Reader position out of bounds')

    def position(self): self.e.write(STREAM + 0x44, self.input.position, 'I')

    def tag(self):
        self.sync(); result = self.input.tag(); self.position()
        self.e.write(STREAM + 0x20, result, 'I')
        return result

    def skip(self): self.sync(); self.input.skip(); self.position(); return 0

    def scalar(self, width):
        self.sync(); value = self.input.varint(); self.position()
        return value & ((1 << width) - 1)

    def fixed64(self):
        self.sync(); value = self.input.double_bits(); self.position(); return value

    def blob(self, kind):
        self.sync(); data = self.input.blob(); self.position()
        if kind == 'string': data.decode('ascii')  # Wider encoding requires separate evidence.
        pointer = self.e.alloc(); self.values[pointer] = dict(kind=kind, hex=data.hex())
        return pointer

    def envelope(self, pointer, helper):
        data = self.input.blob(); self.position()
        if data: raise ValueError('Nonempty nested/container payload not modeled')
        self.events.append(dict(key=self.input.last_key, objectOffset=self.pointers[pointer],
                                source=hex(helper), observation='empty-envelope', value={'hex': ''}))

    def nested(self):
        self.sync(); pointer = self.e.reg('rdx'); off = self.pointers[pointer]
        t = self.fields[off]['type']
        if t['kind'] != 'class' or t['name'] == 'PPGGBLPHADP': raise ValueError('Nested argument type mismatch')
        self.envelope(pointer, 0x1474D7E60); return 0

    def container(self, helper):
        self.sync('rdx'); pointer = self.e.reg('rcx'); off = self.pointers[pointer]
        t = self.fields[off]['type']; key = self.input.last_key
        if t['kind'] != 'genericinst' or t['typeDefinition'] not in CONTAINERS[helper]:
            raise ValueError('Container helper/type mismatch')
        if self.codecs.get(self.e.reg('r8')) != (self.index, key >> 3):
            raise ValueError('Container codec/tag mismatch')
        if helper == 0x158DB8E10:
            if key & 7 == 2:
                packed = WireInput(self.input.blob()); values = []
                while packed.position < len(packed.data): values.append(packed.varint() & 0xffffffff)
            elif key & 7 == 0: values = [self.input.varint() & 0xffffffff]
            else: raise ValueError('Unexpected repeated uint32 wire')
            self.position(); self.lists.setdefault(off, []).extend(values)
            self.events.append(dict(key=key, objectOffset=off, source=hex(helper), observation='modeled-list', value=values))
        else:
            if key & 7 != 2: raise ValueError('Unexpected container envelope wire')
            self.envelope(pointer, helper)
        return 0

    def memory_write(self, uc, access, address, size, value, _):
        if self.active:
            self.events.append(dict(key=self.input.last_key, objectOffset=address - OBJECT, size=size,
                                    value=value & ((1 << (size * 8)) - 1),
                                    source=hex(self.e.reg('rip')), observation='native-store'))

    def run(self, index, data):
        e = self.e; self.active = False; e.prepare(); e.heap_cursor = self.heap_floor
        if len(data) > CAPACITY: raise ValueError('Input exceeds fixture capacity')
        self.index, self.input = index, WireInput(data)
        self.fields = {f['offset']: f for f in e.records[index]['fields'] if f['offsetKind'] == 'instance'}
        self.events, self.values, self.pointers, self.lists = [], {}, {}, {}
        e.write(OBJECT + 0x14, 1, 'I')
        e.uc.mem_write(BUFFER + 0x20, data); e.write(BUFFER + 0x18, len(data), 'I')
        e.write(STREAM + 0x40, len(data), 'I'); e.write(STREAM + 0x38, 0x7fffffff, 'I'); e.write(STREAM + 0x2c, 100, 'I')
        for off, f in self.fields.items():
            if f['type']['kind'] in ('class', 'genericinst', 'string'):
                pointer = e.alloc(); self.pointers[pointer] = off; e.write(OBJECT + off, pointer)
        self.active = True
        try: e.run(ENTRIES[index])
        finally: self.active = False
        if self.input.position != len(data): raise ValueError('Reader returned before consuming fixture')
        for event in self.events:
            if event['objectOffset'] not in self.fields or not event['key']: raise ValueError('Unattributed object store')
            if event['observation'] == 'native-store' and event['value'] in self.values:
                event['value'] = self.values[event['value']]
        return dict(typeIndex=index, wire=data.hex(), events=self.events, observedNativeInstructions=e.steps)


def bind_fields(row, fixtures):
    by_offset = {}
    for sample in fixtures:
        for event in sample['events']: by_offset.setdefault(event['objectOffset'], []).append(event)
    output = []
    for f in row['fields']:
        if f['offsetKind'] != 'instance': continue
        events = by_offset.get(f['offset'], [])
        if not events: raise ValueError('Missing response field ' + f['name'])
        tags = {e['key'] >> 3 for e in events}; wires = sorted({e['key'] & 7 for e in events})
        if len(tags) != 1: raise ValueError('Ambiguous response field binding')
        t = f['type']; kind = t['kind']
        result = dict(clientField=f['name'], objectOffset=f['offset'], tag=tags.pop(), acceptedWireTypes=wires,
                      type=kind, cardinality='singular', presence='implicit', packed=False, wireStatus='emulated-observation',
                      semanticStatus='unresolved', sources=sorted({e['source'] for e in events}))
        if kind == 'class':
            result['type'] = 'bytes' if t['name'] == 'PPGGBLPHADP' else 'message'
            if result['type'] == 'message': result.update(nestedType=t['name'], nestedTypeIndex=t['typeDefinition'], contentStatus='not-traced', presence='explicit')
        elif kind == 'genericinst':
            args = t['arguments']; result['cardinality'] = 'map' if len(args) == 2 else 'repeated'
            value = args[-1]; result['type'] = 'message' if value['kind'] == 'class' else value['kind']
            if len(args) == 2: result['keyType'] = args[0]['kind']
            if result['type'] == 'message': result.update(nestedType=value['name'], nestedTypeIndex=value['typeDefinition'])
            if len(args) == 2: result.update(valueType=result['type'], type='map')
            result['contentStatus'] = 'modeled-helper' if t['typeDefinition'] == 35584 else 'not-traced'
            result['packed'] = t['typeDefinition'] == 35584
        expected = ([0, 2] if result['packed'] else [2] if result['cardinality'] != 'singular' or
                    result['type'] in ('string', 'bytes', 'message') else [1] if kind == 'double' else [0])
        if wires != expected: raise ValueError('Metadata/wire representation mismatch')
        output.append(result)
    return sorted(output, key=lambda f: f['tag'])


def recover(reader):
    fixtures, messages = [], []
    for index in ENTRIES:
        samples = []
        # A bounded search, NOT a protobuf tag-range assumption. Complete coverage
        # is checked against all metadata instance fields, and missing fields fail.
        for wire in (0, 2, 1, 5):
            for start in range(1, 2048, 64):
                data = b''.join(varint(t << 3 | wire) +
                    (varint(300 + t) if wire == 0 else b'\0' if wire == 2 else
                     bytes.fromhex('000000000000f43f') if wire == 1 else bytes.fromhex('0000a03f'))
                    for t in range(start, min(start + 64, 2048)))
                samples.append(reader.run(index, data))
        fields = bind_fields(reader.e.records[index], samples)
        messages.append(dict(typeIndex=index, clientType=reader.e.records[index]['name'], fields=fields,
                             readerEntry=hex(ENTRIES[index]), runtimeStatus='not-tested',
                             completeness='outer-field-bindings-only'))
        fixtures.extend(samples)
    return messages, fixtures


def validate_controlled(reader, messages):
    samples, model_rejections = [], []
    for msg in messages:
        index = msg['typeIndex']
        empty = reader.run(index, b'')
        if empty['events']: raise ValueError('Empty response mutated object fields')
        samples.append(empty)
        for boundary in (False, True):
            pieces, expected = [], {}
            for f in msg['fields']:
                tag, off, kind = f['tag'], f['objectOffset'], f['type']
                if f['cardinality'] == 'repeated' and kind == 'uint32':
                    values = [1, 128, 0xffffffff]; payload = b''.join(varint(v) for v in values)
                    pieces.append(varint(tag << 3 | 2) + varint(len(payload)) + payload); expected[off] = values
                elif f.get('contentStatus') == 'not-traced':
                    pieces.append(varint(tag << 3 | 2) + b'\0'); expected[off] = {'hex': ''}
                elif kind in ('string', 'bytes'):
                    data = f'field_{off}'.encode() if kind == 'string' else bytes([0, 255]) + str(off).encode()
                    pieces.append(varint(tag << 3 | 2) + varint(len(data)) + data)
                    expected[off] = dict(kind=kind, hex=data.hex())
                elif kind == 'double':
                    data = struct.pack('<d', -123.75 if boundary else 1.25)
                    pieces.append(varint(tag << 3 | 1) + data); expected[off] = int.from_bytes(data, 'little')
                else:
                    value = (1 if not boundary else 0) if kind == 'bool' else (
                        (1 << 53) + off if kind == 'uint64' else (-1 if boundary else -123) if kind == 'int32'
                        else 0xffffffff - off if boundary else 300 + off)
                    pieces.append(varint(tag << 3) + varint(value & ((1 << 64) - 1)))
                    expected[off] = value & ((1 << (64 if kind == 'uint64' else 32)) - 1)
            sample = reader.run(index, b''.join(pieces))
            actual = {e['objectOffset']: e['value'] for e in sample['events']}
            if actual != expected: raise ValueError(f'Controlled response values differ: {index}')
            sample['expectedValues'] = expected; samples.append(sample)
        # Independent hand-authored keys/values, not built from recovered schemas.
        raw = ('38' if index == 37481 else '28') + '8180808080808010' + ('70' if index == 37481 else '40') + 'ffffffffffffffffff01'
        sample = reader.run(index, bytes.fromhex(raw))
        actual = {e['objectOffset']: e['value'] for e in sample['events']}
        expected = {248: 9007199254740993, 260: 4294967295} if index == 37481 else {240: 9007199254740993, 224: 4294967295}
        if actual != expected: raise ValueError('Independent response wire oracle failed')
        sample['independentOracle'] = True; samples.append(sample)
        # Duplicate scalar is last-one-wins; a mismatched wire is skipped by native dispatch.
        raw = '100110021200' if index == 37481 else '300130023200'
        sample = reader.run(index, bytes.fromhex(raw))
        off = 236 if index == 37481 else 260
        if [e['value'] for e in sample['events']] != [1, 2] or any(e['objectOffset'] != off for e in sample['events']):
            raise ValueError('Duplicate/wrong-wire dispatch failed')
        samples.append(sample)
        for data in (b'\0', b'\x80', bytes.fromhex('0aff'), bytes.fromhex('088080808080808080808002')):
            try: reader.run(index, data)
            except ValueError as error: model_rejections.append(dict(typeIndex=index, wire=data.hex(), reason=str(error)))
            else: raise ValueError('Malformed fixture accepted by controlled model')
        # Unsupported nested content must remain unsupported, not silently skipped.
        tag = 454 if index == 37481 else 61
        data = varint(tag << 3 | 2) + bytes.fromhex('020801')
        try: reader.run(index, data)
        except ValueError as error:
            if 'Nonempty nested/container' not in str(error): raise
            model_rejections.append(dict(typeIndex=index, wire=data.hex(), reason=str(error)))
        else: raise ValueError('Unknown nested content incorrectly accepted')
    # Explicitly check both repeated encodings enter the same protected field branch.
    sample = reader.run(37481, bytes.fromhex('c01701c0178001c21702ac02'))
    values = [v for e in sample['events'] for v in e['value']]
    if values != [1, 128, 300] or any(e['objectOffset'] != 168 for e in sample['events']):
        raise ValueError('Packed/unpacked response list failed')
    samples.append(sample)
    return samples, model_rejections


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path); p.add_argument('--output', required=True, type=Path)
    p.add_argument('--mapping-output', required=True, type=Path)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    root = a.client.resolve(); outputs = [a.output.resolve(), a.mapping_output.resolve()]
    if len(set(outputs)) != 2 or any(v.is_relative_to(root) for v in outputs): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(root / n, h) for n, h in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    engine = Offline72(b, a.python_libs, records, trace_instructions=False); reader = Reader72(engine)
    try:
        messages, fixtures = recover(reader)
        checks, rejections = validate_controlled(reader, messages)
    finally: reader.close(); engine.close()
    context = dict(schemaVersion=1, inputSHA256=HASHES, runtimeStatus='not-tested',
                   limitations=['Nested/map/message-list payloads empty only', 'ASCII string model',
                                'Synthetic mutable objects and initialized codecs', 'No runtime IFix/ABI acceptance'])
    for path, content in zip(outputs, (dict(context, discoveryFixtures=fixtures, validationFixtures=checks,
                                         modelRejections=rejections), dict(context, messages=messages))):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(content, indent=2)+'\n', 'utf8')
    print(json.dumps(dict(messages=len(messages), outerFields=sum(len(m['fields']) for m in messages),
                         discoveryFixtures=len(fixtures), validationFixtures=len(checks), modelRejections=len(rejections))))


if __name__ == '__main__': main()
