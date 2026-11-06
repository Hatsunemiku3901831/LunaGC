"""Hash-bound CN72 scene handshakes: raw wire and application arithmetic.

Offline only. Native constructors/serializers/consumers execute in Unicorn;
managed storage, formatting and the bounded byte stream are explicit fixtures.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import tempfile
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_emulator import Offline72, OBJECT, STREAM, BUFFER, STACK, GLOBAL, CAPACITY
    from .client72_scene_reader import SceneReader72, POSITION
    from .client72_reader import WireInput
    from .client72_session_runtime import fragment
    from .trace_client72_login import Writer72
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_emulator import Offline72, OBJECT, STREAM, BUFFER, STACK, GLOBAL, CAPACITY
    from client72_scene_reader import SceneReader72, POSITION
    from client72_reader import WireInput
    from client72_session_runtime import fragment
    from trace_client72_login import Writer72
    from trace_client72_token import varint

MASK = 0xFFFFFFFF
# Semantic role, target type, command, typed handler/application constructor,
# command registration (responses), request self-class (requests), literal site/index.
ROLES = (
    ('EnterSceneReadyReq', 74391, 5046, 0x15121E560, 0, 0x1458C4CC0, 0x15121E597, 34850),
    ('EnterSceneReadyRsp', 17278, 9948, 0x1507D4D70, 0x1507C2D0F, 0, 0x1507D4DB5, 49584),
    ('SceneInitFinishReq', 20380, 4905, 0x1512242B0, 0, 0x1458C5570, 0x1512242CC, 34990),
    ('SceneInitFinishRsp', 36349, 21392, 0x1507C5B10, 0x1507C2A8A, 0, 0x1507C5B2A, 46170),
    ('EnterSceneDoneReq', 63999, 1017, 0x15121E380, 0, 0x1458C4C98, 0x15121E399, 34845),
    ('EnterSceneDoneRsp', 74698, 298, 0x1507D50D0, 0x1507C2D1E, 0, 0x1507D5107, 49965),
    ('PostEnterSceneReq', 74076, 1696, 0x151226220, 0, 0x14589CB50, 0x15122623A, 35045),
    ('PostEnterSceneRsp', 51326, 26760, 0x1507EEE50, 0x1507C2A6C, 0, 0x1507EEE9C, 49650),
)
FIELDS = {
    74391: ((24, 3, 'uint32', None),),
    17278: ((24, 2, 'uint32', None), (28, 12, 'int32', None)),
    20380: ((24, 11, 'uint32', None),),
    36349: ((24, 1, 'uint32', None), (28, 7, 'int32', None)),
    63999: ((24, 12, 'uint32', None),),
    74698: ((24, 12, 'class', 82602), (32, 6, 'int32', None), (36, 4, 'uint32', None)),
    74076: ((24, 1, 'double', None), (32, 6, 'uint32', None)),
    51326: ((24, 6, 'int32', None), (28, 2, 'uint32', None)),
    82602: ((24, 1, 'class', 84343), (32, 11, 'uint32', None), (36, 3, 'uint32', None)),
    84343: ((24, 1, 'float', None), (28, 3, 'float', None), (32, 2, 'float', None)),
}
CTORS = {74391: 0x14734BD30, 17278: 0x147964510, 20380: 0x1518DEA70,
         36349: 0x14E7A8C40, 63999: 0x14A75C170, 74698: 0x148198940,
         74076: 0x14C963FB0, 51326: 0x1496712A0, 82602: 0x15330D820, 84343: 0x154048960}
READERS = {17278: 0x147964650, 36349: 0x14E7A8B40, 74698: 0x148198570,
           51326: 0x149671480, 82602: 0x15330DDD0, 84343: 0x154048D40}
WRITERS = {74391: 0x14734BA00, 20380: 0x1518DE8A0, 63999: 0x14A75C040,
           74076: 0x14C963FC0, 82602: 0x15330D8B0}
REQUEST_XOR_ADD = {74391: (0xE8C3, 0x4C01), 20380: (0x5673, 0xF8CD),
                   63999: (0x2034, -0x8609), 74076: (0x4902, -0xB3B6)}
RETCODE = {17278: (28, 12), 36349: (28, 7), 74698: (32, 6), 51326: (24, 6)}
RESPONSE_TOKEN = {17278: (24, 2, 0xC18A), 36349: (24, 1, 0xFFFF7F50),
                  74698: (36, 4, 0x3966), 51326: (28, 2, 0x12F00)}
REUSED_EVIDENCE = {
    'scene-wait-observations.json': 'd7cc253e567a935dc3ff7575cdd4d807df1b8789d318aa7535524e635b1ba5ef',
    'scene-nested-observations.json': 'ae91950a67ea8beefb030ba4165eecfc1fd45acca1c6ac64278abf5c411d3098',
    'scene-nested-research.proto': '316fa5fb18234b1d808ace1bf16418ac2f2267f21ba1db3b944b1d1e96231e35',
}


def raw_request_token(index, token):
    xor, add = REQUEST_XOR_ADD[index]
    return ((token ^ xor) + add) & MASK


def raw_retcode(index, code):
    value = code & MASK
    if index == 17278: return ((value ^ 0x83BE) + 0x1077) & MASK
    if index == 36349: return ((value - 0xA488) & MASK) ^ 0x7530
    if index == 74698: return ((value ^ 0x6D20) - 0xBEAC) & MASK
    if index == 51326: return ((value - 0xF7C) & MASK) ^ 0x73B7
    raise ValueError('Not a bound response')


def raw_response_token(index, token):
    if index == 17278: return ((token + 0xE034) & MASK) ^ 0x21BE
    if index == 36349: return ((token - 0x4765) & MASK) ^ 0xC7CB
    if index == 74698: return ((token ^ 0xBF71) - 0x860B) & MASK
    if index == 51326: return ((token ^ 0xE67B) + 0x4885) & MASK
    raise ValueError('Not a bound response')


def logical_response_token(index, raw):
    if index == 17278: return ((raw ^ 0x21BE) - 0xE034) & MASK
    if index == 36349: return ((raw ^ 0xC7CB) + 0x4765) & MASK
    if index == 74698: return ((raw + 0x860B) & MASK) ^ 0xBF71
    if index == 51326: return ((raw - 0x4885) & MASK) ^ 0xE67B
    raise ValueError('Not a bound response')


def logical_retcode(index, raw):
    if index == 17278: value = ((raw - 0x1077) & MASK) ^ 0x83BE
    elif index == 36349: value = ((raw ^ 0x7530) + 0xA488) & MASK
    elif index == 74698: value = ((raw + 0xBEAC) & MASK) ^ 0x6D20
    elif index == 51326: value = ((raw ^ 0x73B7) + 0xF7C) & MASK
    else: raise ValueError('Not a bound response')
    return value - (1 << 32) if value & 0x80000000 else value


def scalar_wire(tag, value, signed=False):
    if signed and value & 0x80000000: value |= 0xFFFFFFFF00000000
    return varint(tag << 3) + varint(value)


def blob_wire(tag, value):
    return varint(tag << 3 | 2) + varint(len(value)) + value


def usage_rows(binary, metadata, startup, records, indices):
    pe = PE(binary); lit = Literals72(binary, metadata)
    resolver = TypeResolver(pe, startup, records, pe.offset(HEADER_VA))
    base = number(binary, pe.offset(USAGE_REGISTRATION) + 0x48, 'Q')
    def first(i):
        return ((number(metadata, lit.groups_base + i * 4) + 0x8BFF3E05) & MASK) ^ ((i * 0x5843EAA0 - 0x443C8630) & MASK)
    result = []
    for row in [row for group in (lit.global_group, 0x17B5E) for row in range(first(group), first(group + 1))]:
        key = (row * 0x57C15286 + 0x26DDEBDA) & MASK
        source = ((number(metadata, lit.usages_base + row * 8) + 0xABBDCABF) & MASK) ^ key ^ 0x2A357D90
        if source >> 29 != 1: continue
        resolved = resolver.resolve_index(source & 0x1FFFFFFF)
        if resolved.get('typeDefinition') not in indices: continue
        slot = number(metadata, lit.usages_base + row * 8 + 4) ^ key ^ 0x1855AA65
        result.append(dict(usageRow=row, sourceIndex=source & 0x1FFFFFFF, typeIndex=resolved['typeDefinition'], cell=hex(base + slot * 8)))
    return result


def declarations(binary, metadata, startup, records):
    pe = PE(binary); header = pe.offset(HEADER_VA); byid = {r['index']: r for r in records}
    resolver = TypeResolver(pe, startup, records, header)
    method_base = BODY + (number(binary, header + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, header + 0x174) + 0xAE6BCD68) & MASK)
    usages = usage_rows(binary, metadata, startup, records, set(FIELDS) | {78239})
    methods = {int(m['registeredAddress'], 16): (r, m) for r in records for m in r['methods'] if m['registeredAddress'] != '0x0'}
    lit = Literals72(binary, metadata); literals = {x['index']: x for x in lit.group(lit.global_group)}
    roles = []
    for name, index, cmd, entry, registration, cell, site, literal_index in ROLES:
        row = byid[index]; getter = next(m for m in row['methods'] if m.get('virtualSlot') == 22)
        if getter.get('literalU16Candidate') != cmd or row['parent'].get('typeDefinition') != 67861:
            raise ValueError('Handshake command/base declaration drift')
        owner, method = methods[entry]
        if owner['index'] != (56238 if registration else 23177): raise ValueError('Handshake entry owner drift')
        first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
        _, ref = parameter_words(first, number(metadata, parameter_base + first * 8), number(metadata, parameter_base + first * 8 + 4))
        parameter = resolver.resolve_index(ref)
        if registration and parameter.get('typeDefinition') != index: raise ValueError('Handshake typed-handler drift')
        if not registration and parameter['kind'] != 'uint32': raise ValueError('Handshake token argument drift')
        literal = dict(literals[literal_index], value=lit.decode(literal_index))
        code = binary[pe.offset(site, 7):][:7]
        if code[:2] != b'\x48\x8b' or site + 7 + int.from_bytes(code[3:], 'little', signed=True) != int(literal['cell'], 16):
            raise ValueError('Handshake literal reference drift')
        if registration:
            code = binary[pe.offset(registration, 12):][:12]
            if code[:8] != b'\x66\xba' + cmd.to_bytes(2, 'little') + bytes.fromhex('4989f0e8') or registration + 12 + int.from_bytes(code[8:], 'little', signed=True) != 0x1532ED560:
                raise ValueError('Handshake network registration drift')
        elif not any(x['typeIndex'] == index and x['cell'] == hex(cell) for x in usages):
            raise ValueError('Handshake allocation self-class drift')
        roles.append(dict(semanticName=name, typeIndex=index, clientType=row['name'], cmdId=cmd,
                          getter=getter['registeredAddress'], entry=hex(entry), parameter=parameter,
                          registration=hex(registration) if registration else None,
                          selfClassCell=hex(cell) if cell else None, literal=literal, literalSite=hex(site),
                          commandDomainAmbiguity='1017 also occurs on a different target type; no global alias merge' if cmd == 1017 else None,
                          bindingStatus='verified-offline', semanticStatus='verified-offline', runtimeStatus='not-tested'))
    token_literals = []
    for literal_index, site in ((52998, 0x14CD5FA05), (60411, 0x15255C03E)):
        literal = dict(literals[literal_index], value=lit.decode(literal_index))
        code = binary[pe.offset(site, 7):][:7]
        if code[:2] != b'\x48\x8b' or site + 7 + int.from_bytes(code[3:], 'little', signed=True) != int(literal['cell'], 16):
            raise ValueError('Named loading/post token reference drift')
        token_literals.append(dict(literal=literal, site=hex(site)))
    wrapper_owner, wrapper = methods[0x1507D1CF0]
    first = parameter_start(wrapper['index'], number(metadata, method_base + wrapper['index'] * 26 + 4))
    _, ref = parameter_words(first, number(metadata, parameter_base + first * 8), number(metadata, parameter_base + first * 8 + 4))
    if wrapper_owner['index'] != 56238 or resolver.resolve_index(ref).get('typeDefinition') != 51326:
        raise ValueError('Post response dispatch wrapper parameter drift')
    wrapper_end = min(a for a in methods if a > 0x1507D1CF0)
    forwarding = []
    for at in range(0x1507D1CF0, wrapper_end - 4):
        code = binary[pe.offset(at, 5):][:5]
        if code[0] in (0xE8, 0xE9) and at + 5 + int.from_bytes(code[1:], 'little', signed=True) == 0x1507EEE50:
            forwarding.append(hex(at))
    if len(forwarding) != 1: raise ValueError('Post response wrapper forwarding differs')
    if not any(u['typeIndex'] == 78239 and u['cell'] == '0x1458ecbc8' for u in usages) or methods[0x150724560][0]['index'] != 78239:
        raise ValueError('Post coroutine metadata identity drift')
    coroutine_fields = [f for f in byid[78239]['fields'] if f['offset'] == 0x30 and f['offsetKind'] == 'instance']
    if len(coroutine_fields) != 1 or coroutine_fields[0]['type']['kind'] != 'uint32': raise ValueError('Post coroutine token storage drift')
    messages = []
    for index, expected in FIELDS.items():
        row = byid[index]; fields = [f for f in row['fields'] if f['offsetKind'] == 'instance']
        if {(f['offset'], f['type']['kind'], f['type'].get('typeDefinition')) for f in fields} != {(o, k, c) for o, _, k, c in expected}:
            raise ValueError('Handshake complete instance field declaration drift')
        output = []
        for off, tag, kind, child in expected:
            field = next(f for f in fields if f['offset'] == off)
            output.append(dict(clientField=field['name'], objectOffset=off, tag=tag,
                               type='message' if kind == 'class' else kind, nestedTypeIndex=child,
                               wireType=2 if child else 1 if kind == 'double' else 5 if kind == 'float' else 0,
                               semanticStatus='unresolved', cardinality='singular'))
        native = []
        for entry in (CTORS[index], READERS.get(index), WRITERS.get(index)):
            if not entry: continue
            owner, method = methods[entry]
            if owner['index'] != index: raise ValueError('Handshake native method owner drift')
            native.append(dict(method=method, entry32SHA256=hashlib.sha256(binary[pe.offset(entry, 32):][:32]).hexdigest()))
        messages.append(dict(typeIndex=index, clientType=row['name'], fields=output, methods=native))
    return dict(roles=roles, messages=messages, typeUsages=usages, tokenLiterals=token_literals,
                postCoroutine=dict(typeIndex=78239, entry='0x150724560', selfClassCell='0x1458ecbc8', tokenField=coroutine_fields[0]),
                postDispatchWrapper=dict(entry='0x1507d1cf0', method=wrapper, typeIndex=51326,
                                         forwardsAt=forwarding[0], target='0x1507eee50', execution='dispatcher to wrapper only'))


class HandshakeReader72(SceneReader72):
    def __init__(self, engine, usages):
        super().__init__(engine)
        e = self.e; interface = e.read(0x1458AAD80)
        for index, entry in READERS.items():
            if index == POSITION: continue
            cells = sorted({int(u['cell'], 16) for u in usages if u['typeIndex'] == index})
            if not cells: raise ValueError('Missing reader type usage')
            klass = e.synthetic_type(cells[0])
            for cell in cells[1:]: e.write(cell, klass)
            table = e.alloc(16); e.write(table, interface)
            e.write(klass + 0x80, table); e.write(klass + 0xC0, 1, 'H'); e.write(klass + 0xBE, 32, 'H')
            e.write(klass + 0xE8, entry)
            self.classes[index] = klass; self.class_indices[klass] = index
            info = e.alloc(64); e.write(info + 8, CTORS[index]); e.write(info + 0x2F, 2, 'B')
            self.ctor_infos[klass] = info
            e.allow(entry); e.allow(CTORS[index])
        self.heap_floor = e.heap_cursor

    def snapshot(self, pointer=OBJECT):
        e = self.e; index = self.objects[pointer]; fields = {}
        for off, _, kind, _ in FIELDS[index]:
            if kind == 'class':
                value = e.read(pointer + off); fields[hex(off)] = self.snapshot(value) if value else None
            else: fields[hex(off)] = e.read(pointer + off, 'Q' if kind == 'double' else 'I')
        return dict(typeIndex=index, fields=fields)

    def run_message(self, index, wire, recursion_limit=100):
        e = self.e; self.active = False; e.prepare(); e.heap_cursor = self.heap_floor
        if len(wire) > CAPACITY: raise ValueError('Handshake fixture exceeds bounded stream')
        self.input = WireInput(wire); self.events, self.values, self.objects, self.arrays, self.lists = [], {}, {}, {}, set()
        e.write(OBJECT, self.classes[index]); e.write(OBJECT + 0x14, 1, 'I'); self.objects[OBJECT] = index
        e.run(CTORS[index]); defaults = self.snapshot()
        e.uc.mem_write(BUFFER + 0x20, wire); e.write(BUFFER + 0x18, len(wire), 'I')
        e.write(STREAM + 0x40, len(wire), 'I'); e.write(STREAM + 0x38, 0x7FFFFFFF, 'I'); e.write(STREAM + 0x2C, recursion_limit, 'I')
        self.active = True
        try: e.run(READERS[index])
        finally: self.active = False
        if self.input.position != len(wire) or e.read(STREAM + 0x34, 'I') or e.read(STREAM + 0x38, 'I') != 0x7FFFFFFF:
            raise ValueError('Handshake reader did not consume/balance input')
        return dict(typeIndex=index, wire=wire.hex(), constructorDefaults=defaults,
                    storedObject=self.snapshot(), stores=self.events,
                    models=[call['address'] for call in e.calls])


def proto_text(messages):
    names = {m['typeIndex']: m['clientType'] for m in messages}
    lines = ['// Research raw-wire carrier. Application arithmetic is separate; not runtime-ready.',
             'syntax = "proto3";', 'package cn72.scene_handshake;', '']
    for message in messages:
        lines.append('message ' + message['clientType'] + ' {')
        for field in sorted(message['fields'], key=lambda f: f['tag']):
            kind = names[field['nestedTypeIndex']] if field['type'] == 'message' else field['type']
            lines.append(f'  {kind} {field["clientField"]} = {field["tag"]}; // raw object +0x{field["objectOffset"]:x}')
        lines.extend(['}', ''])
    return '\n'.join(lines)


def protoc_encode(protoc, proto, name, values):
    result = subprocess.run([str(protoc), '--proto_path=' + str(proto.parent),
                             '--encode=cn72.scene_handshake.' + name, str(proto)],
                            input=values.encode(), capture_output=True, check=True)
    return result.stdout


def request_probes(e, proto, protoc, messages):
    writer = Writer72(e)
    e.models[0x151765EF0] = lambda: e.emit(e.uc.reg_read(e.R.UC_X86_REG_XMM2).to_bytes(16, 'little')[:8]) or 0
    e.models.update({0x1531FDB80: lambda: 0, 0x159AB12F0: lambda: 0,
                     0x140525C40: lambda: 0, 0x14D581600: lambda: 0})
    e.allow(0x14A42BD90)
    e.synthetic_type(0x1458971C0)
    byid = {m['typeIndex']: m for m in messages}; rows = []
    for name, index, cmd, entry, registration, cell, _, _ in ROLES:
        if registration: continue
        e.allow(entry)
        for token in (0, 1, 0x80000000, 0xFFFFFFFF):
            e.prepare(); klass = e.synthetic_type(cell); request = e.alloc(256); owner = e.alloc(1024)
            state = e.alloc(1024); e.write(GLOBAL + 0x9E80, state); e.write(state + 0x1EC, 1, 'B')
            e.write(request, klass); e.write(request + 0x14, 1, 'I'); e.run(CTORS[index], request)
            expected = raw_request_token(index, token)
            offset = 32 if index == 74076 else 24
            constructor = e.read(request + offset, 'I')
            if constructor != raw_request_token(index, 0): raise ValueError('Request encoded-zero default drift')
            def allocate():
                if e.reg('rcx') != klass: raise ValueError('Unexpected handshake request allocation')
                return request
            e.models[0x14D4480C0] = allocate
            double_bits = 0 if token == 0 else 0xC0C81CA000000000 if token == 1 else 0x4340000000000001
            e.write(0x145873360, double_bits)
            trace = fragment(e, entry, {0x15120D730}, dict(rsp=STACK + 0x100008, rcx=owner, rdx=token, r8=0))
            if e.reg('rdx') != request or e.read(request + offset, 'I') != expected:
                raise ValueError('Request constructor/token arithmetic or pointer changed')
            # The native target writer sees the actual application-created object.
            wire = e.run(WRITERS[index], request)
            tag = 6 if index == 74076 else {74391: 3, 20380: 11, 63999: 12}[index]
            golden = (b'\x09' + struct.pack('<Q', double_bits) if index == 74076 and double_bits else b'') + scalar_wire(tag, expected)
            field = next(f for f in byid[index]['fields'] if f['objectOffset'] == offset)
            text = f'{field["clientField"]}: {expected}'
            if index == 74076:
                double_name = next(f['clientField'] for f in byid[index]['fields'] if f['objectOffset'] == 24)
                text += f'\n{double_name}: {struct.unpack("<d", struct.pack("<Q", double_bits))[0]!r}'
            encoded = protoc_encode(protoc, proto, byid[index]['clientType'], text)
            if wire != golden or wire != encoded:
                raise ValueError(f'Actual request writer differs from independent wire/protoc: {index}/{token} native={wire.hex()} independent={golden.hex()} protoc={encoded.hex()}')
            rows.append(dict(name=name + '-' + str(token), typeIndex=index, token=token,
                             storedToken=expected, constructorToken=constructor, wire=wire.hex(),
                             doubleBits=double_bits if index == 74076 else None, protocMatch=True,
                             requestPointerPreserved=True, trace=trace))
    e.models.pop(0x14D4480C0)
    return rows


def response_probes(reader, proto, protoc, messages):
    e = reader.e; rows = []; byid = {m['typeIndex']: m for m in messages}
    for index in RETCODE:
        for mode in ('empty', 'nonzero', 'maximum', 'duplicate-explicit-zero', 'wrong-wire'):
            values = {}; wire = b''; text = []
            for off, tag, kind, child in sorted(FIELDS[index], key=lambda f: f[1]):
                if child: continue
                if mode == 'empty': continue
                value = 0xFEDC1234 if kind == 'uint32' else 0x80000001
                if mode == 'maximum': value = MASK
                if mode == 'duplicate-explicit-zero': value = 0
                field = next(f for f in byid[index]['fields'] if f['objectOffset'] == off)
                if mode == 'wrong-wire': wire += blob_wire(tag, b'\x00'); continue
                if mode == 'duplicate-explicit-zero': wire += scalar_wire(tag, 17)
                wire += scalar_wire(tag, value, kind == 'int32'); values[hex(off)] = value
                printable = value - (1 << 32) if kind == 'int32' and value & 0x80000000 else value
                text.append(f'{field["clientField"]}: {printable}')
            result = reader.run_message(index, wire)
            expected = copy.deepcopy(result['constructorDefaults']); expected['fields'].update(values)
            if result['storedObject'] != expected: raise ValueError('Response reader differs from independent raw wire')
            checked = mode in ('empty', 'nonzero', 'maximum')
            if checked and protoc_encode(protoc, proto, byid[index]['clientType'], '\n'.join(text)) != wire:
                raise ValueError(f'Independent response wire/protoc mismatch: {index}/{mode}')
            rows.append(dict(name=f'{index}-{mode}', expected=expected, result=result, protocMatch=checked))
    # Independently specified nested float bit patterns, uint32 boundaries and duplicate merge.
    position = bytes.fromhex('0d0000a03f15000020c01d00008047')
    child = blob_wire(1, position) + bytes.fromhex('18ffffffff0f588180808008')
    wire = blob_wire(12, child) + scalar_wire(4, MASK) + scalar_wire(6, raw_retcode(74698, 0), True)
    result = reader.run_message(74698, wire)
    expected_child = dict(typeIndex=82602, fields={'0x18': dict(typeIndex=84343, fields={'0x18': 0x3FA00000, '0x1c': 0x47800000, '0x20': 0xC0200000}),
                                                    '0x20': 0x80000001, '0x24': MASK})
    expected = dict(typeIndex=74698, fields={'0x18': expected_child, '0x20': raw_retcode(74698, 0), '0x24': MASK})
    if result['storedObject'] != expected: raise ValueError('Nested Done response raw graph differs')
    rows.append(dict(name='done-nonempty-nested', expected=expected, result=result, protocMatch=False))
    repeat = wire + blob_wire(12, blob_wire(1, bytes.fromhex('0d00000000')) + bytes.fromhex('18005800'))
    result = reader.run_message(74698, repeat); expected = copy.deepcopy(expected)
    expected['fields']['0x18']['fields']['0x18']['fields']['0x18'] = 0
    expected['fields']['0x18']['fields']['0x20'] = 0; expected['fields']['0x18']['fields']['0x24'] = 0
    if result['storedObject'] != expected: raise ValueError('Nested duplicate/explicit-zero merge differs')
    rows.append(dict(name='done-duplicate-nested-explicit-zero', expected=expected, result=result, protocMatch=False))
    rejections = []
    for name, wire, limit, origin in (
        ('zero-tag', b'\x00', 100, 'bounded-input-model'),
        ('truncated-varint', b'\x20\x80', 100, 'bounded-input-model'),
        ('negative-child-length', b'\x62\xff\xff\xff\xff\x0f', 100, 'native-error-branch'),
        ('recursion-limit', blob_wire(12, child), 0, 'native-error-branch')):
        try: reader.run_message(74698, wire, limit)
        except ValueError as error:
            if (origin == 'native-error-branch') != ('Native error branch' in str(error)):
                raise ValueError('Native/model rejection provenance changed') from error
            rejections.append(dict(name=name, origin=origin, error=str(error)))
        else: raise ValueError('Expected bounded handshake rejection missing')
    return rows, rejections


def semantic_response_probes(reader):
    e = reader.e; rows = []
    e.models.update({0x1531FDB80: lambda: 0, 0x159AD6130: lambda: 0,
                     0x159C1AEB0: lambda: 0, 0x1531DC5E0: lambda: 0,
                     0x14C8AE4B0: lambda: 0, 0x1405336C0: lambda: e.alloc()})
    e.synthetic_type(0x1458AD418)
    reader.heap_floor = e.heap_cursor
    stops = {17278: (0x1507D4F41, 0x1507C5E10), 36349: (0x1507C5D7C, 0x1507C5E10),
             74698: (0x1507D51F2, 0x147161B60), 51326: (0x1507EEED9, 0x147161B60)}
    for _, index, _, handler, registration, _, _, _ in ROLES:
        if not registration: continue
        e.allow(handler)
        for mode, code in [('logical', c) for c in (0, 1, -1, -2147483648, 2147483647)] + [('raw-zero', logical_retcode(index, 0)), ('missing', 0)]:
            _, tag = RETCODE[index]; raw = raw_retcode(index, code)
            wire = b'' if mode == 'missing' else scalar_wire(tag, raw, True)
            result = reader.run_message(index, wire)
            owner = e.alloc(1024)
            trace = fragment(e, handler, set(stops[index]), dict(rsp=STACK + 0x100008, rcx=owner, rdx=OBJECT))
            expected_stop = stops[index][int(code != 0)]
            if trace['stop'] != hex(expected_stop): raise ValueError('Response actual success/error branch differs')
            observed = 0 if code == 0 else e.reg('r8d') if index in (17278, 36349) else e.read(e.reg('rsp') + 0x48, 'I')
            if observed != code & MASK: raise ValueError(f'Native response error consumer arithmetic differs: {index}/{code} observed={observed:#x}')
            rows.append(dict(name=f'{index}-retcode-{mode}-{code}', typeIndex=index, logicalRetcode=code, fieldPresent=mode != 'missing',
                             rawRetcode=raw, observedRetcodeBits=observed, wire=wire.hex(), trace=trace,
                             branch='success-prefix' if code == 0 else 'error-diagnostic-prefix', reader=result))
    return rows


def response_token_probes(reader):
    e = reader.e; rows = []
    e.allow(0x1507C5E10); e.allow(0x14CD5F7B0); e.allow_unwind(0x1578B66F0)
    e.write(0x145B91010, 1, 'B')  # Reviewed shared error helper's initialized metadata guard.
    e.models[0x147161B60] = lambda: 0  # Diagnostic sink only; task lookup executes natively.
    for _, index, _, handler, registration, _, _, _ in ROLES:
        if not registration: continue
        off, tag, default = RESPONSE_TOKEN[index]
        for mode, token in [('missing', 0), ('raw-zero', logical_response_token(index, 0)),
                            ('zero', 0), ('one', 1), ('high', 0x80000000), ('maximum', MASK)]:
            raw = 0 if mode == 'raw-zero' else raw_response_token(index, token)
            wire = scalar_wire(RETCODE[index][1], raw_retcode(index, 1), True)
            if mode != 'missing': wire += scalar_wire(tag, raw)
            result = reader.run_message(index, wire)
            owner, loader, dictionary = e.alloc(1024), e.alloc(1024), e.alloc()
            entries, buckets = e.alloc(32 + 16 * 24), e.alloc(32 + 17 * 4)
            e.write(loader + 0x38, dictionary); e.write(dictionary + 0x18, entries); e.write(dictionary + 0x20, buckets)
            e.write(dictionary + 0x3C, MASK, 'I'); e.write(entries + 24, 16, 'I'); e.write(buckets + 24, 17, 'I')
            e.write(GLOBAL + 0xBE90, loader)
            formatted = []
            def token_format(): formatted.append(e.reg('edx')); return 0
            e.models[0x159C1ABD0] = token_format
            trace = fragment(e, handler, {0x14CD5FA0F}, dict(rsp=STACK + 0x100008, rcx=owner, rdx=OBJECT))
            if formatted != [token] or e.reg('rcx') != e.read(0x145B31650):
                raise ValueError('Response token did not reach its independent named loading-task diagnostic')
            actual_raw = result['storedObject']['fields'][hex(off)]
            if actual_raw != (default if mode == 'missing' else raw): raise ValueError('Response token presence/default changed')
            rows.append(dict(name=f'{index}-token-{mode}', typeIndex=index, logicalToken=token,
                             rawToken=actual_raw, fieldPresent=mode != 'missing', observedNamedToken=formatted[0],
                             wire=wire.hex(), trace=trace, namedLiteralIndex=52998, lookupResult='missing-key-native'))
    return rows


def request_token_link_probes(reader):
    e = reader.e; rows = []
    for entry in (0x14828C930, 0x14CD68CF0, 0x14828C630, 0x14CD670F0, 0x148289D60, 0x148289E50, 0x150724560): e.allow(entry)
    e.write(0x145B9123E, 1, 'B')
    for token in (0, 1, 0x80000000, MASK):
        # The same Level argument is sent to SceneInitFinishReq, then to the
        # real LoadingTask lookup and its independently named token diagnostic.
        e.prepare(); owner, loader, dictionary = e.alloc(1024), e.alloc(1024), e.alloc()
        entries, buckets = e.alloc(32 + 16 * 24), e.alloc(32 + 17 * 4)
        e.write(loader + 0x38, dictionary); e.write(dictionary + 0x18, entries); e.write(dictionary + 0x20, buckets)
        e.write(dictionary + 0x3C, MASK, 'I'); e.write(entries + 24, 16, 'I'); e.write(buckets + 24, 17, 'I')
        e.write(GLOBAL + 0xBE90, loader); e.write(GLOBAL + 0xA0D0, e.alloc(1024))
        received, formatted = [], []
        def init_entry(): received.append(e.reg('edx')); return 0
        def named_token(): formatted.append(e.reg('edx')); return 0
        e.models[0x1512242B0] = init_entry; e.models[0x159C1ABD0] = named_token
        trace = fragment(e, 0x14828C930, {0x14CD5FA0F}, dict(rsp=STACK + 0x100008, rcx=owner, rdx=token, r8=0))
        if received != [token] or formatted != [token]: raise ValueError('Init request argument not linked to named LoadingTask token')
        rows.append(dict(name='init-token-' + str(token), typeIndex=20380, token=token, trace=trace,
                         requestEntryCaptured='0x1512242b0', namedLiteralIndex=52998, source='full Level entry -> request entry capture -> actual LoadingTask missing-key diagnostic'))
        # An isolated native producer block passes the same saved Level argument
        # to Init and Done. Their full application constructors/writers are tested
        # separately above; scheduling and earlier Level gates are outside scope.
        captured = []
        e.models[0x1512242B0] = lambda: captured.append((20380, e.reg('edx'))) or 0
        e.models[0x15121E380] = lambda: captured.append((63999, e.reg('edx'))) or 0
        trace = fragment(e, 0x14828C751, {0x14828C789}, dict(rsp=STACK + 0x100008, rax=GLOBAL, rbp=token))
        if captured != [(20380, token), (63999, token)]: raise ValueError('Init/Done shared producer argument differs')
        rows.append(dict(name='done-token-' + str(token), typeIndex=63999, token=token, trace=trace,
                         source='reviewed native producer block, same saved argument as verified Init token; preceding gates not executed'))
        e.models.pop(0x1512242B0); e.models.pop(0x15121E380)
        # The verified DoneRsp token enters the actual Level forwarding method
        # and coroutine allocation. Do not execute or fake the scheduler.
        result = reader.run_message(74698, scalar_wire(4, raw_response_token(74698, token)))
        e.write(GLOBAL + 0xA0D0, e.alloc(1024))
        level = e.alloc(1024); coroutine = e.alloc(512); klass = e.synthetic_type(0x1458ECBC8)
        e.write(coroutine, klass)
        def allocate():
            if e.reg('rcx') != klass: raise ValueError('Post producer coroutine class differs')
            return coroutine
        e.models[0x1405336C0] = allocate
        trace = fragment(e, 0x14CD6719E, {0x14828C1D0}, dict(rsp=STACK + 0x100008 - 0x68, rcx=level, rsi=OBJECT, r8=0, r9=0))
        if e.reg('rdx') != coroutine or e.read(coroutine + 0x30, 'I') != token:
            raise ValueError('Done response token not preserved in native Post coroutine allocation')
        producer = fragment(e, 0x150725574, {0x151226220}, dict(rsp=STACK + 0x100008, rax=GLOBAL, rsi=coroutine))
        if e.reg('edx') != token: raise ValueError('Actual Post request producer does not use saved Done response token')
        rows.append(dict(name='post-token-' + str(token), typeIndex=74076, token=token, trace=trace, producerTrace=producer,
                         source='actual Done reader -> declared Loading block -> Level forwarding/allocation; separate declared coroutine block -> Post request entry',
                         schedulerExecuted=False, reader=result))
        e.models[0x1405336C0] = lambda: e.alloc()
    return rows


def dispatch_probes(e):
    handlers = {9948: 0x1507D4D70, 21392: 0x1507C5B10, 298: 0x1507D50D0, 26760: 0x1507D1CF0}
    rows = []
    for cmd in (*handlers, 0, 65535):
        e.prepare(); message = e.alloc(); captured = []
        for handler in handlers.values():
            def observe(handler=handler): captured.append((handler, e.reg('rdx'))); return 0
            e.models[handler] = observe
        e.write(STREAM + 0x22, cmd, 'H'); e.write(STREAM + 0x10, message)
        e.run(0x1507CC240)
        if captured != ([(handlers[cmd], message)] if cmd in handlers else []):
            raise ValueError('Actual handshake command dispatcher differs')
        rows.append(dict(cmdId=cmd, handler=hex(handlers[cmd]) if cmd in handlers else None, originalPointer=True))
    for handler in handlers.values(): e.models.pop(handler)
    return rows


def scene_token_probe(binary, records, libraries):
    e = Offline72(binary, libraries, records, True); reader = SceneReader72(e); rows = []
    try:
        e.allow(0x14CD615A0); e.allow(0x15121E560)
        e.models.update({0x1531FDB80: lambda: 0, 0x140525C40: lambda: 0, 0x159AB12F0: lambda: 0, 0x14D581600: lambda: 0})
        e.synthetic_type(0x1458971C0)
        reader.heap_floor = e.heap_cursor
        for raw in (0, 1, 0x80000000, MASK):
            # Actual notification reader; continue the same original object into the
            # loading consumer, then the named Ready application constructor.
            result = reader.run_scene(scalar_wire(10, raw))
            loading = e.alloc(1024); game = e.alloc(256); network = e.alloc(1024)
            e.write(GLOBAL + 0xB330, game); e.write(GLOBAL + 0xA0D0, network)
            request = e.alloc(); klass = e.synthetic_type(0x1458C4CC0)
            e.write(request, klass); e.write(request + 0x14, 1, 'I')
            e.run(CTORS[74391], request)
            def allocate():
                if e.reg('rcx') != klass: raise ValueError('Scene Ready allocation type differs')
                return request
            e.models[0x14D4480C0] = allocate
            trace = fragment(e, 0x14CD615A0, {0x15120D730}, dict(rsp=STACK + 0x100008, rcx=loading, rdx=OBJECT))
            logical = (((raw + 0x5889) & MASK) ^ 0xE011)
            expected = raw_request_token(74391, logical)
            if e.reg('rdx') != request or e.read(request + 0x18, 'I') != expected:
                raise ValueError('Notification token did not reach Ready request through native arithmetic')
            rows.append(dict(rawNotificationToken=raw, logicalToken=logical, rawReadyToken=expected,
                             sourceField=dict(typeIndex=46311, objectOffset=148, tag=10),
                             targetField=dict(typeIndex=74391, objectOffset=24, tag=3), trace=trace,
                             originalNotificationObject=True, reader=result))
        return rows
    finally: reader.close(); e.close()


def recover(binary, metadata, startup, records, libraries, protoc):
    report = dict(schemaVersion=1, inputSHA256=HASHES, runtimeStatus='not-tested', targetProtocolReady=False,
                  scope='Eight scene handshakes, raw schemas and bounded native token/retcode arithmetic; no client process',
                  **declarations(binary, metadata, startup, records))
    root = Path(__file__).resolve().parents[1] / 'protocol/cn-7.2'
    for name, digest in REUSED_EVIDENCE.items():
        if hashlib.sha256((root / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Reused scene raw evidence changed: ' + name)
    report['reusedEvidenceSHA256'] = REUSED_EVIDENCE
    report['semanticBindings'] = [
        dict(typeIndex=74391, objectOffset=24, tag=3, name='enter_scene_token',
             status='verified-offline', applicationToWire='u32((token XOR 0xe8c3) + 0x4c01)',
             evidence='Named token literal34850; actual argument to constructor then actual writer'),
        dict(typeIndex=46311, objectOffset=148, tag=10, name='enter_scene_token',
             status='verified-offline', wireToApplication='u32(raw + 0x5889) XOR 0xe011',
             evidence='Actual notification reader -> 14cd615a0 -> 15121e560 -> Ready send boundary'),
        *[dict(typeIndex=index, objectOffset=32 if index == 74076 else 24, tag=tag, name='enter_scene_token',
               status='verified-offline', applicationToWire=formula, evidence=evidence)
          for index, tag, formula, evidence in (
              (20380, 11, 'u32((token XOR 0x5673) + 0xf8cd)', 'Level argument -> actual Init invocation and named LoadingTask token diagnostic'),
              (63999, 12, 'u32((token XOR 0x2034) - 0x8609)', 'Native Level producer block passes same preserved token to verified Init and Done constructors'),
              (74076, 6, 'u32((token XOR 0x4902) - 0xb3b6)', 'Verified Done response token -> native Level forwarding/coroutine field30 -> native Post invocation block; scheduler not executed'))],
        *[dict(typeIndex=index, objectOffset=RETCODE[index][0], tag=RETCODE[index][1], name='retcode',
               status='verified-offline', applicationToWire=formula,
               evidence='Independent wire -> actual native reader -> own handler success/error diagnostic prefix')
          for index, formula in ((17278, 'u32((code XOR 0x83be) + 0x1077)'),
                                 (36349, 'u32(code - 0xa488) XOR 0x7530'),
                                 (74698, 'u32((code XOR 0x6d20) - 0xbeac)'),
                                 (51326, 'u32(code - 0xf7c) XOR 0x73b7'))],
        *[dict(typeIndex=index, objectOffset=RESPONSE_TOKEN[index][0], tag=RESPONSE_TOKEN[index][1], name='enter_scene_token',
               status='verified-offline', applicationToWire=formula,
               evidence='Independent raw wire -> actual response handler -> actual LoadingTask lookup -> named token diagnostic literal52998')
          for index, formula in ((17278, 'u32(token + 0xe034) XOR 0x21be'),
                                 (36349, 'u32(token - 0x4765) XOR 0xc7cb'),
                                 (74698, 'u32((token XOR 0xbf71) - 0x860b)'),
                                 (51326, 'u32((token XOR 0xe67b) + 0x4885)'))],
    ]
    report['responsePresenceContract'] = [
        dict(typeIndex=index, retcode=dict(objectOffset=RETCODE[index][0], tag=RETCODE[index][1],
                                          absentRawDefault=raw_retcode(index, 0), absentLogicalDefault=0,
                                          explicitRawZeroLogicalValue=logical_retcode(index, 0)),
             token=dict(objectOffset=RESPONSE_TOKEN[index][0], tag=RESPONSE_TOKEN[index][1],
                        absentRawDefault=RESPONSE_TOKEN[index][2], absentLogicalDefault=0,
                        explicitRawZeroLogicalValue=logical_response_token(index, 0)),
             adapterRequirement='Preserve field presence: omitted field keeps target constructor default; explicit wire zero overwrites it. A Java optional field would be adapter representation, not recovered target Proto syntax.')
        for index in RETCODE]
    proto = proto_text(report['messages'])
    with tempfile.TemporaryDirectory(prefix='scene-handshake72-') as directory:
        path = Path(directory) / 'scene-handshake-research.proto'; path.write_text(proto, 'utf8')
        e = Offline72(binary, libraries, records, True)
        try:
            report['dispatchFixtures'] = dispatch_probes(e)
            report['requestFixtures'] = request_probes(e, path, protoc, report['messages'])
        finally: e.close()
        e = Offline72(binary, libraries, records, True); reader = HandshakeReader72(e, report['typeUsages'])
        try:
            report['readerFixtures'], report['rejections'] = response_probes(reader, path, protoc, report['messages'])
            report['semanticFixtures'] = semantic_response_probes(reader)
            report['responseTokenFixtures'] = response_token_probes(reader)
            report['requestTokenLinkFixtures'] = request_token_link_probes(reader)
        finally: reader.close(); e.close()
    report['sceneTokenFixtures'] = scene_token_probe(binary, records, libraries)
    report['outsideScope'] = [
        'Managed allocation/reflection, formatting and raw-byte stream refill/EOF are bounded models; IFix disabled.',
        'Response constructors and declared success/error prefixes execute; later gameplay/error recovery and actual transport do not.',
        'Post response dispatch first enters wrapper 1507D1CF0; full wrapper continuation is not claimed.',
        'Notify remaining 25 field business names and other scene nested business semantics remain unresolved.',
        'All eight tokens bound through named target diagnostics and native producer data flow; some producer blocks have explicit live-in context, not full scheduler execution.',
        'Post request double source is observed static cell 145873360; business name is unresolved.',
        'No complete required initialization sequence, service deployment, new/old-account client acceptance or D-02.',
    ]
    report['protoSHA256'] = hashlib.sha256(proto.encode()).hexdigest()
    return report, proto


def summarize(report):
    result = {k: v for k, v in report.items() if k not in ('requestFixtures', 'readerFixtures', 'semanticFixtures', 'sceneTokenFixtures', 'responseTokenFixtures', 'requestTokenLinkFixtures')}
    result['requestObservations'] = [{k: v for k, v in r.items() if k != 'trace'} | {'entry': r['trace']['entry'], 'stop': r['trace']['stop'], 'nativePathSHA256': r['trace']['nativePathSHA256']} for r in report['requestFixtures']]
    result['readerObservations'] = [dict(name=r['name'], wire=r['result']['wire'], expected=r['expected'], storedObject=r['result']['storedObject'],
                                         constructorDefaults=r['result']['constructorDefaults'], protocMatch=r['protocMatch']) for r in report['readerFixtures']]
    result['semanticObservations'] = [{k: v for k, v in r.items() if k not in ('trace', 'reader')} | {'stop': r['trace']['stop'], 'nativePathSHA256': r['trace']['nativePathSHA256']} for r in report['semanticFixtures']]
    result['sceneTokenObservations'] = [{k: v for k, v in r.items() if k not in ('trace', 'reader')} | {'nativePathSHA256': r['trace']['nativePathSHA256']} for r in report['sceneTokenFixtures']]
    result['responseTokenObservations'] = [{k: v for k, v in r.items() if k != 'trace'} | {'nativePathSHA256': r['trace']['nativePathSHA256']} for r in report['responseTokenFixtures']]
    result['requestTokenLinkObservations'] = [{k: v for k, v in r.items() if k not in ('trace', 'producerTrace', 'reader')} |
        {'nativePathSHA256': r['trace']['nativePathSHA256'], 'entry': r['trace']['entry'], 'stop': r['trace']['stop'],
         'producerPathSHA256': r.get('producerTrace', {}).get('nativePathSHA256')} for r in report['requestTokenLinkFixtures']]
    return result


def validate_summary(summary):
    if summary['inputSHA256'] != HASHES or summary['runtimeStatus'] != 'not-tested' or summary['targetProtocolReady']:
        raise ValueError('Handshake fixed inputs/runtime scope changed')
    if [(r['semanticName'], r['typeIndex'], r['cmdId']) for r in summary['roles']] != [r[:3] for r in ROLES]:
        raise ValueError('Handshake role identity changed')
    if summary['reusedEvidenceSHA256'] != REUSED_EVIDENCE:
        raise ValueError('Reused field/list/uint64 source evidence changed')
    if {m['typeIndex'] for m in summary['messages']} != set(FIELDS): raise ValueError('Handshake type family incomplete')
    for message in summary['messages']:
        actual = {(f['objectOffset'], f['tag'], 'class' if f['type'] == 'message' else f['type'], f['nestedTypeIndex']) for f in message['fields']}
        if actual != set(FIELDS[message['typeIndex']]): raise ValueError('Handshake wire schema changed')
        if any(f['semanticStatus'] != 'unresolved' for f in message['fields']): raise ValueError('Raw schema silently promoted business fields')
    if {(b['typeIndex'], b['tag'], b['name']) for b in summary['semanticBindings']} != {
            (74391, 3, 'enter_scene_token'), (46311, 10, 'enter_scene_token'),
            (20380, 11, 'enter_scene_token'), (63999, 12, 'enter_scene_token'), (74076, 6, 'enter_scene_token'),
            (17278, 12, 'retcode'), (36349, 7, 'retcode'), (74698, 6, 'retcode'), (51326, 6, 'retcode'),
            (17278, 2, 'enter_scene_token'), (36349, 1, 'enter_scene_token'), (74698, 4, 'enter_scene_token'), (51326, 2, 'enter_scene_token')}:
        raise ValueError('Handshake semantic boundary expanded or removed')
    if len(summary['messages']) != 10 or len(summary['requestObservations']) != 16 or len(summary['readerObservations']) != 22 or len(summary['semanticObservations']) != 28 or len(summary['sceneTokenObservations']) != 4 or len(summary['responseTokenObservations']) != 24 or len(summary['requestTokenLinkObservations']) != 12:
        raise ValueError('Handshake fixture coverage incomplete')
    for row in summary['requestObservations']:
        if row['storedToken'] != raw_request_token(row['typeIndex'], row['token']) or not row['protocMatch'] or not row['requestPointerPreserved']:
            raise ValueError('Request native/protoc arithmetic lost')
    for row in summary['readerObservations']:
        if row['expected'] != row['storedObject']: raise ValueError('Reader independent expected value changed')
    for row in summary['semanticObservations']:
        if row['rawRetcode'] != raw_retcode(row['typeIndex'], row['logicalRetcode']) or row['observedRetcodeBits'] != row['logicalRetcode'] & MASK:
            raise ValueError('Response application/wire arithmetic lost')
    for row in summary['sceneTokenObservations']:
        logical = (((row['rawNotificationToken'] + 0x5889) & MASK) ^ 0xE011)
        if row['logicalToken'] != logical or row['rawReadyToken'] != raw_request_token(74391, logical):
            raise ValueError('Notification-to-Ready token binding lost')
    for row in summary['responseTokenObservations']:
        if row['logicalToken'] != logical_response_token(row['typeIndex'], row['rawToken']) or row['observedNamedToken'] != row['logicalToken'] or row['namedLiteralIndex'] != 52998 or row['lookupResult'] != 'missing-key-native':
            raise ValueError('Response token named native lookup provenance changed')
    if {(r['typeIndex'], r['token']) for r in summary['requestTokenLinkObservations']} != {(i, t) for i in (20380, 63999, 74076) for t in (0, 1, 0x80000000, MASK)}:
        raise ValueError('Request token producer boundaries incomplete')
    if {r['name']: r['origin'] for r in summary['rejections']} != {'zero-tag': 'bounded-input-model', 'truncated-varint': 'bounded-input-model', 'negative-child-length': 'native-error-branch', 'recursion-limit': 'native-error-branch'}:
        raise ValueError('Handshake native/model rejection scope changed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--protoc', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    parser.add_argument('--proto-output', type=Path, required=True)
    args = parser.parse_args()
    for path in (args.output, args.summary_output, args.proto_output):
        if path.resolve().is_relative_to(args.client.resolve()): parser.error('Output must be outside client')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report, proto = recover(binary, metadata, startup, records, args.libraries, args.protoc)
    summary = summarize(report); validate_summary(summary)
    for path, value in ((args.output, report), (args.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', 'utf8')
    args.proto_output.parent.mkdir(parents=True, exist_ok=True); args.proto_output.write_bytes(proto.encode('utf8'))
    print(json.dumps(dict(roles=8, rawSchemas=10, fields=sum(map(len, FIELDS.values())), requests=16, readers=22, semanticPrefixes=28, responseTokens=24, requestTokenLinks=12, sceneToken=4, dispatch=6, rejections=4)))


if __name__ == '__main__': main()
