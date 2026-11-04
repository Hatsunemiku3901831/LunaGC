"""Controlled LoginReq writer, nested messages, repeated fields and oneof cases.

Fixed input hashes, explicit native helper models, no client process or server
integration. Enum numeric wire is retained as int32; named enum values unknown.
"""
import argparse
import json
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, OBJECT, STREAM, GLOBAL
    from .trace_client72_token import varint, parse_wire
    from .observed_proto72 import render
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, OBJECT, STREAM, GLOBAL
    from trace_client72_token import varint, parse_wire
    from observed_proto72 import render

ONEOFS = {
    54602: {11: (59347, 0x1458F1138), 12: (30171, 0x1458F1140), 13: (46567, 0x1458DA338), 14: (80597, 0x1458D8EB8)},
    70905: {11: (44353, 0x1458CA658), 12: (31708, 0x1458D7848)}}
CHILD_IDS = [45258, 21921, 68918, 54602, 70905, 59347, 30171, 46567, 80597, 44353, 31708]


class Writer72:
    def __init__(self, engine):
        self.e = engine
        engine.basic_writers()
        for cell in (0x1458BC2D8, *[cell for cases in ONEOFS.values() for _, cell in cases.values()]):
            engine.synthetic_type(cell)
        for guard in (0x145B92754, 0x145B934E5, 0x145B987C2):
            engine.write(guard, 1, 'B')
        self.codec32, self.codec64 = engine.alloc(), engine.alloc()
        # Actual .cctor immediates: 1487CE4F4 -> 0A, 14782F544 -> 12.
        for address, tag in ((0x1487CE4F4, 10), (0x14782F544, 18)):
            if bytes(engine.uc.mem_read(address, 5)) != b'\xb9' + struct.pack('<I', tag):
                raise ValueError('Repeated codec initializer changed')
        engine.write(GLOBAL + 0x31FB0, self.codec32); engine.write(GLOBAL + 0x2C340, self.codec64)
        engine.models.update({0x15171FE80: self.string, 0x147CF1A40: self.byte_length,
                              0x151767530: self.byte_write, 0x151719B20: self.message_write,
                              0x158DB8540: lambda: self.repeated(32), 0x1571DD480: lambda: self.repeated(64)})
        self.objects = {}

    def string(self):
        kind, data = self.objects[self.e.reg('rdx')]
        if kind != 'string':
            raise ValueError('String model type mismatch')
        data.decode('ascii'); self.e.emit(varint(len(data)) + data); return 0

    def byte_length(self):
        kind, data = self.objects[self.e.reg('rcx')]
        if kind != 'bytes':
            raise ValueError('Byte container mismatch')
        return len(data)

    def byte_write(self):
        kind, data = self.objects[self.e.reg('rdx')]
        if kind != 'bytes':
            raise ValueError('Byte container mismatch')
        self.e.emit(varint(len(data)) + data); return 0

    def message_write(self):
        kind, data = self.objects[self.e.reg('rdx')]
        if kind != 'message':
            raise ValueError('Message helper mismatch')
        self.e.emit(varint(len(data)) + data); return 0

    def repeated(self, width):
        kind, values = self.objects[self.e.reg('rcx')]
        if kind != f'list{width}' or self.e.reg('r8') != (self.codec32 if width == 32 else self.codec64):
            raise ValueError('Repeated helper/codec mismatch')
        if values:
            payload = b''.join(varint(v) for v in values)
            self.e.emit(varint(10 if width == 32 else 18) + varint(len(payload)) + payload, stream=self.e.reg('rdx'))
        return 0

    def pointer(self, kind, value, class_cell=None):
        p = self.e.alloc(256); self.objects[p] = (kind, value)
        if kind == 'string':
            self.e.write(p + 0x10, len(value), 'I'); self.e.uc.mem_write(p + 0x14, value.decode('ascii').encode('utf-16-le'))
        if class_cell is not None:
            self.e.write(p, self.e.read(class_cell))
        return p

    def run(self, index, values, case=None):
        e = self.e; row = e.records[index]; e.prepare(); self.objects = {}
        for field in row['fields']:
            if field['offsetKind'] != 'instance':
                continue
            off, t = field['offset'], field['type']; kind = t['kind']; value = values.get(off)
            if kind in ('class', 'object', 'genericinst', 'string'):
                if value is None:
                    pointer = 0
                elif isinstance(value, tuple):
                    pointer = self.pointer(*value)
                else:
                    pointer = self.pointer('string', value)
                e.write(OBJECT + off, pointer)
            else:
                size = 'B' if kind == 'bool' else 'Q' if kind == 'uint64' else 'I'
                e.write(OBJECT + off, (value or 0) & ((1 << (8 * struct.calcsize(size))) - 1), size)
        if case is not None:
            e.write(OBJECT + 0x20, case, 'I')
        entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'CAIINKNGKDM')
        data = e.run(entry)
        return dict(typeIndex=index, case=case, wire=data.hex(), instructions=e.steps, values=values)


def scalar_values(row, boundary=False):
    out = {}
    for field in row['fields']:
        if field['offsetKind'] != 'instance':
            continue
        off, kind = field['offset'], field['type']['kind']
        if kind == 'string': out[off] = f'f_{off}'.encode()
        elif kind == 'bool': out[off] = 0
        elif kind == 'uint32': out[off] = 0xffffffff - off if boundary else 0x12340000 + off
        elif kind == 'int32': out[off] = -100 - off if boundary else 0x12340000 + off
        elif kind == 'uint64': out[off] = (1 << 53) + off if boundary else (1 << 40) + off
        elif kind == 'valuetype': out[off] = 1  # Explicit numeric sample, not a named enumerator claim.
        elif kind == 'class' and field['type']['name'] == 'PPGGBLPHADP': out[off] = ('bytes', f'b_{off}'.encode())
        else: out[off] = None
    return out


def observed_fields(row, baseline, variants):
    rows = parse_wire(bytes.fromhex(baseline['wire'])); identity = {}
    for off, value in baseline['values'].items():
        if value is None or value == 0: continue
        if isinstance(value, tuple): value = value[1]
        if isinstance(value, list): value = b''.join(varint(v) for v in value)
        if isinstance(value, int): value &= (1 << 64) - 1
        key = (2 if isinstance(value, bytes) else 0, value)
        if key in identity: raise ValueError('Ambiguous fixture values')
        identity[key] = off
    tags = {}
    for tag, wire, value in rows:
        if (wire, value) not in identity: raise ValueError('Unknown wire value')
        tags[identity[(wire, value)]] = (tag, wire)
    base_by_tag = {t: (w, v) for t, w, v in rows}
    for off, variant in variants.items():
        diff = {t: (w, v) for t, w, v in parse_wire(bytes.fromhex(variant['wire']))}
        added = set(diff) - set(base_by_tag)
        if len(added) != 1 or any(diff.get(t) != value for t, value in base_by_tag.items()):
            raise ValueError('Boolean perturbation changed more than one field')
        tag = added.pop()
        if diff[tag] != (0, 1): raise ValueError('Not a boolean write')
        tags[off] = (tag, 0)
    result = []
    for field in row['fields']:
        if field['offsetKind'] != 'instance': continue
        off, t = field['offset'], field['type']
        if off not in tags: raise ValueError(f'Missing field {field["name"]}')
        kind = t['kind']; tag, wire = tags[off]
        item = dict(clientField=field['name'], objectOffset=off, tag=tag, wireType=wire,
                    type=kind, cardinality='singular', presence='implicit', packed=False,
                    wireStatus='emulated-observation', semanticStatus='unresolved')
        if kind == 'class':
            item['type'] = 'bytes' if t['name'] == 'PPGGBLPHADP' else 'message'
            if item['type'] == 'message': item.update(nestedType=t['name'], nestedTypeIndex=t['typeDefinition'], presence='explicit')
        elif kind == 'genericinst': item.update(type=t['arguments'][0]['kind'], cardinality='repeated', packed=True)
        elif kind == 'valuetype': item.update(type='int32', enumClientType=t['name'], enumValuesStatus='unresolved')
        result.append(item)
    return sorted(result, key=lambda f: f['tag'])


def serializable(value):
    if isinstance(value, bytes): return {'hex': value.hex()}
    if isinstance(value, (tuple, list)): return [serializable(v) for v in value]
    if isinstance(value, dict): return {str(k): serializable(v) for k, v in value.items()}
    return value


def recover(engine):
    writer = Writer72(engine); payloads, observations, fixtures = {}, [], []
    # Leaves first; oneof alternatives are independently produced by native writers.
    order = [68918, 30171, 31708, 44353, 46567, 80597, 59347, 45258, 54602, 70905, 21921, 76410]
    for index in order:
        row = engine.records[index]
        if index in ONEOFS:
            fields = []
            for case, (child, cell) in ONEOFS[index].items():
                sample = writer.run(index, {24: ('message', payloads[child], cell)}, case)
                fields_wire = parse_wire(bytes.fromhex(sample['wire']))
                if len(fields_wire) != 1 or fields_wire[0] != (case, 2, payloads[child]):
                    raise ValueError('Oneof native type/tag/payload mismatch')
                fields.append(dict(clientField=f'case_{case}', objectOffset=24, discriminatorOffset=32,
                    discriminatorValue=case, tag=case, wireType=2, type='message', nestedType=engine.records[child]['name'],
                    nestedTypeIndex=child, cardinality='singular', presence='oneof', oneof='content', packed=False,
                    wireStatus='emulated-observation', semanticStatus='unresolved', typeInfoCell=hex(cell)))
                fixtures.append(sample)
                if case == 11: payloads[index] = bytes.fromhex(sample['wire'])
            empty = writer.run(index, {}, 0)
            if empty['wire']: raise ValueError('Unset oneof wrote data')
            fixtures.append(empty)
        else:
            values = scalar_values(row)
            for f in row['fields']:
                if f['offsetKind'] != 'instance': continue
                off, t = f['offset'], f['type']
                if t['kind'] == 'class' and t['name'] != 'PPGGBLPHADP': values[off] = ('message', payloads[t['typeDefinition']])
                if t['kind'] == 'genericinst':
                    width = 32 if t['arguments'][0]['kind'] == 'uint32' else 64
                    values[off] = (f'list{width}', [1, 128, (1 << (width - 1)) + 7])
            sample = writer.run(index, values); fixtures.append(sample)
            variants = {}
            for field in row['fields']:
                if field['offsetKind'] == 'instance' and field['type']['kind'] == 'bool':
                    off = field['offset']; variant = writer.run(index, values | {off: 1})
                    variants[off] = variant; fixtures.append(variant)
            fields = observed_fields(row, sample, variants)
            payloads[index] = bytes.fromhex(sample['wire'])
            boundary = values | {off: v for off, v in scalar_values(row, True).items() if v is not None and not isinstance(v, tuple)}
            boundary_sample = writer.run(index, boundary); fixtures.append(boundary_sample)
            # Bool perturbations have the same tag/value; the alternate baseline must preserve non-bool mappings.
            boundary_tags = {t for t, _, _ in parse_wire(bytes.fromhex(boundary_sample['wire']))}
            if boundary_tags != {t for t, _, _ in parse_wire(bytes.fromhex(sample['wire']))}:
                raise ValueError('Boundary input changed field presence')
            zero = {}
            for off, value in values.items():
                if isinstance(value, tuple):
                    zero[off] = None if value[0] == 'message' else (value[0], [] if value[0].startswith('list') else b'')
                else: zero[off] = b'' if isinstance(value, bytes) else 0
            empty = writer.run(index, zero)
            if empty['wire']: raise ValueError('Default object wrote data')
            fixtures.append(empty)
        observations.append(dict(typeIndex=index, clientType=row['name'], fields=fields, runtimeStatus='not-tested'))
    return observations, fixtures


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path); p.add_argument('--output', required=True, type=Path)
    p.add_argument('--mapping-output', required=True, type=Path)
    p.add_argument('--proto-output', required=True, type=Path)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    root = a.client.resolve(); outputs = [a.output.resolve(), a.mapping_output.resolve(), a.proto_output.resolve()]
    if len(set(outputs)) != 3 or any(v.is_relative_to(root) for v in outputs): p.error('Distinct outputs outside client required')
    binary, meta, startup = [read_verified(root / name, sha) for name, sha in HASHES.items()]
    records, _ = extract(binary, meta, startup=startup, structures=True)
    engine = Offline72(binary, a.python_libs, records)
    try: observations, fixtures = recover(engine)
    finally: engine.close()
    context = dict(schemaVersion=1, inputSHA256=HASHES, runtimeStatus='not-tested', scope='Offline base path with explicit helper models',
                   limitations=['Enum symbolic values not recovered', 'Oneof variant field names synthetic', 'ASCII-only string models', 'Runtime IFix/ABI not observed'])
    for path, content in zip(outputs, (dict(context, fixtures=serializable(fixtures)), dict(context, messages=observations))):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(content, indent=2)+'\n', encoding='utf8')
    outputs[2].parent.mkdir(parents=True, exist_ok=True)
    outputs[2].write_text(render(observations), encoding='utf8')
    print(json.dumps(dict(messages=len(observations), fields=sum(len(r['fields']) for r in observations), fixtures=len(fixtures))))


if __name__ == '__main__': main()
