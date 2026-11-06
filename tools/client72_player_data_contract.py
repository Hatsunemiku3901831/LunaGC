"""Native player-initialization purpose and bounded field-consumption contract.

The real reader, typed handler, property conversion/encoding/model callback and
SDK prefix execute. Managed collection iteration/storage and SDK JSON storage
are declared fixtures, not real SDK initialization or a complete Handler.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from .trace_client72_player_data import PlayerDataGraph72, map_bindings, ROOT
    from .client72_session_runtime import SessionRuntime72, fragment
    from .client72_graph_fixtures import scalar, message
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_emulator import Offline72, GLOBAL, OBJECT, STACK, END
    from trace_client72_player_data import PlayerDataGraph72, map_bindings, ROOT
    from client72_session_runtime import SessionRuntime72, fragment
    from client72_graph_fixtures import scalar, message

RETURN = END + 0x100
HANDLER, PROPS, SETTER, MODEL_CALLBACK = 0x1507BC0E0, 0x152F7F670, 0x152F7ED50, 0x152178DB0
DISPATCH, SDK, STOP, RENAME = 0x1507CC240, 0x1527670B0, 0x1471D59A0, 0x1507CEC80
TIME, TIME_READ, EPOCH_ENTRY = 0x14E699670, 0x14E65CCD0, 0x14E697558
EPOCH = 621355968000000000
RAW_SHA256 = '455a353974185c885622759247a19b94c905c037545232b29e3cede189bb8cf6'
UNRESOLVED = {'79263': [5, 7], '78350': [3, 4]}
LITERAL_CELLS = (0x145AD3E28, 0x145AFE7E8, 0x145AD3E30, 0x145AD1988, 0x145AFE7F0,
                 0x145AD4B00, 0x145AFE7F8, 0x145AFE800, 0x145ACCCF0, 0x145ACCAC8, 0x145ADCBE8)


def fixture_cases():
    base = dict(nickname='Tester', serverMs=1700000000123, nowMs=1000, receiptMs=950,
                raw5=9, raw7=True, properties=[])
    # tuple: outer map key, child property ID, oneof branch, value, independent tag4.
    specs = [
        ('level-different-map-key', dict(properties=[(7, 10013, 2, 42, 0)])),
        ('empty-name', dict(nickname='')),
        ('negative-int64', dict(properties=[(8, 10013, 2, -1, 0)])),
        ('high-int64', dict(properties=[(9, 4242, 2, 9007199254740993, 0)])),
        ('float-not-int64', dict(properties=[(7, 10013, 3, 2.5, 0)])),
        ('independent-tag4-only', dict(properties=[(7, 10013, 0, 0, 987)])),
        ('child-int64-beats-tag4', dict(properties=[(7, 10013, 2, 43, 987)])),
        ('two-map-keys-same-property', dict(properties=[(7, 10013, 2, 42, 0), (8, 10013, 2, 99, 0)])),
        ('scaled-property', dict(properties=[(7, 10035, 2, 123456789, 0)])),
        ('delay-max', dict(nowMs=100000, receiptMs=0)),
        ('delay-too-old', dict(nowMs=100001, receiptMs=0)),
        ('future-receipt', dict(nowMs=1000, receiptMs=1001)),
        ('timestamp-overflow-wrap', dict(serverMs=(1 << 64) - 1, nowMs=1, receiptMs=0)),
        ('timestamp-date-range-error', dict(serverMs=9007199254740993, nowMs=0, receiptMs=0)),
        ('empty-all', dict(nickname='', serverMs=0, raw5=0, raw7=False)),
    ]
    result = []
    for name, changes in specs:
        inputs = base | changes
        wire = scalar(5, inputs['raw5']) + scalar(7, int(inputs['raw7']))
        wire += message(8, inputs['nickname'].encode('ascii')) + scalar(13, inputs['serverMs'])
        expected = []
        for outer, prop, branch, value, independent in inputs['properties']:
            child = scalar(1, prop)
            if branch == 2: child += scalar(2, value)
            elif branch == 3: child += b'\x1d' + struct.pack('<f', value)
            if independent: child += scalar(4, independent)
            wire += message(6, scalar(1, outer) + message(2, child))
            raw = value if branch == 2 else 0
            expected.append(dict(type=prop, rawInt64=raw, storedDouble=float(raw) / (100 if prop == 10035 else 1)))
        if name == 'empty-all': wire = b''
        delta = inputs['nowMs'] - inputs['receiptMs']
        delta = delta if 0 <= delta <= 100000 else 0
        result.append(dict(name=name, inputs=inputs, wire=wire, expectedProperties=expected,
            delta=delta, adjusted=(inputs['serverMs'] + delta) & ((1 << 64) - 1)))
    return result


def declarations(binary, metadata, startup, records, directory):
    path = directory / 'player-data-wire-observations.json'
    if hashlib.sha256(path.read_bytes()).hexdigest() != RAW_SHA256:
        raise ValueError('Reviewed player-data raw evidence changed')
    pe = PE(binary); h = pe.offset(HEADER_VA); byid = {r['index']: r for r in records}
    resolver = TypeResolver(pe, startup, records, h)
    mb = BODY + (number(binary, h+0xB0) ^ 0x51324A1F)
    pb = BODY + ((number(binary, h+0x174) + 0xAE6BCD68) & 0xFFFFFFFF)
    typed = []
    for owner, entry in ((56238, HANDLER), (14237, SDK), (89508, PROPS), (89508, SETTER), (56238, RENAME)):
        method = next(m for m in byid[owner]['methods'] if m['registeredAddress'] == hex(entry))
        first = parameter_start(method['index'], number(metadata, mb+method['index']*26+4))
        parameters = [resolver.resolve_index(parameter_words(i, number(metadata, pb+i*8),
                      number(metadata, pb+i*8+4))[1]) for i in range(first, first+method['parameterCount'])]
        typed.append(dict(ownerType=owner, entry=hex(entry), method=method['name'], parameters=parameters))
    if ([v.get('typeDefinition') for v in typed[0]['parameters']] != [ROOT, 22292] or
        typed[1]['parameters'][0].get('typeDefinition') != 58013 or
        typed[2]['parameters'][0].get('arguments', [None, {}])[1].get('typeDefinition') != 78350 or
        [p['kind'] for p in typed[3]['parameters'][:2]] != ['uint32', 'int64']):
        raise ValueError('Typed consumer chain changed')
    for owner, slot, entry in ((89508, 7, SETTER), (89508, 14, 0x152F80100), (58013, 20, MODEL_CALLBACK)):
        if not any(m['virtualSlot'] == slot and m['registeredAddress'] == hex(entry) for m in byid[owner]['methods']):
            raise ValueError('Property virtual dispatch declaration changed')
    enums = recover_enums(binary, metadata, records, indices=(62096, 75464))['enums']
    symbols = {v['symbol']: v['value'] for enum in enums for v in enum['values']}
    if any(symbols.get(k) != v for k, v in {'PROP_PLAYER_LEVEL': 10013,
            'PROP_CUR_CLIMATE_METER': 10035, 'RefreshNickname': 842}.items()):
        raise ValueError('Independent enum symbols changed')
    selected_symbols = {'PROP_PLAYER_LEVEL', 'PROP_CUR_CLIMATE_METER', 'RefreshNickname'}
    enums = [dict(enum, values=[v for v in enum['values'] if v['symbol'] in selected_symbols]) for enum in enums]
    lr = Literals72(binary, metadata)
    literals = [dict(row, value=lr.decode(row['index'])) for row in lr.group(lr.global_group)
                if int(row['cell'], 16) in LITERAL_CELLS]
    if {r['cell']: r['value'] for r in literals}.get('0x145afe7f0') != 'role_name' or len(literals) != len(LITERAL_CELLS):
        raise ValueError('SDK key provenance changed')
    divisor = struct.unpack('<d', binary[pe.offset(0x142240D30, 8):][:8])[0]
    if divisor != 100.0: raise ValueError('Native property scaling constant changed')
    usage = 740031; key = (usage*0x57C15286+0x26DDEBDA) & 0xFFFFFFFF
    source = ((number(metadata, lr.usages_base+usage*8)+0xABBDCABF) & 0xFFFFFFFF) ^ key ^ 0x2A357D90
    slot = number(metadata, lr.usages_base+usage*8+4) ^ key ^ 0x1855AA65
    cache = number(binary, pe.offset(USAGE_REGISTRATION)+0x10, 'Q')
    callback = next(m for m in byid[14237]['methods'] if m['index'] == 105470)
    if source >> 29 != 3 or source & 0x1FFFFFFF != 105470 or cache+slot*8 != 0x1459A83B0 or callback['registeredAddress'] != '0x1527694d0':
        raise ValueError('SDK callback metadata binding changed')
    date = byid[190]
    if [(f['name'], f['rawOffset']) for f in date['fields'] if f['name'] in ('DaysToMonth365', 'DaysToMonth366')] != [
            ('DaysToMonth365', '0x4000080'), ('DaysToMonth366', '0x4000088')]:
        raise ValueError('Named calendar static storage changed')
    for name, address, count in (('DateToTicks', 0x159B965F0, 3), ('Add', 0x159B97510, 2)):
        if not any(m['name'] == name and m['registeredAddress'] == hex(address) and m['parameterCount'] == count for m in date['methods']):
            raise ValueError('DateTime method declaration changed')
    return dict(rawEvidenceSHA256=RAW_SHA256, typedConsumers=typed, enums=enums, literals=literals,
        scaledProperty=dict(type=10035, symbol='PROP_CUR_CLIMATE_METER', constant='0x142240d30', divisor=divisor),
        sdkCallback=dict(usageRow=usage, methodDefinition=105470, cell='0x1459a83b0', entry=callback['registeredAddress']),
        nativeEntry32SHA256={hex(a): hashlib.sha256(binary[pe.offset(a, 32):][:32]).hexdigest()
            for a in (HANDLER, PROPS, SETTER, MODEL_CALLBACK, SDK, RENAME, TIME, TIME_READ, EPOCH_ENTRY)})


class PlayerContract72:
    def __init__(self, e, graph, declared, inputs, ordinal):
        self.e, self.g = e, graph
        self.session = SessionRuntime72(e, 5 if ordinal % 2 == 0 else 13, 7 if ordinal % 2 == 0 else 29)
        # Actual primitive wrappers encode/decode; table initialization and RNG
        # are declared reversible fixtures, not observed startup state.
        for offset in (0xBED0, 0x31E60): e.write(GLOBAL+offset, e.read(GLOBAL+0x204D0))
        self.model, self.manager = e.alloc(512), e.alloc(1024)
        e.write(self.manager+0x180, self.model); klass = e.alloc(1024); e.write(self.model, klass)
        for slot, entry in ((14, 0x152F80100), (7, SETTER), (20, MODEL_CALLBACK)):
            e.write(klass+0xD0+slot*8, entry); e.allow(entry)
        self.pool = e.alloc(); poolclass = e.alloc(); e.write(self.pool, poolclass); e.write(GLOBAL+0x5D60, self.pool)
        e.write(poolclass+0xF0, e.model_stub(self.rent)); e.write(poolclass+0xF8, e.model_stub(lambda: 0))
        self.dictionaries, self.events, self.properties = {}, [], []
        e.models[0x156ED8B50], e.models[0x156ED8EA0] = self.lookup, self.insert
        self.bus = e.alloc(); e.write(GLOBAL+0x9E50, self.bus); e.models[0x1532D6720] = self.event
        self.eventclasses = {e.synthetic_type(0x145886170): 4, e.synthetic_type(0x1458862F0): 1}
        e.models[0x140525C40] = self.box
        e.synthetic_type(0x1458D6520)  # Reviewed initialized climate-storage class.
        self.packet = e.alloc(); e.write(self.packet+0x28, inputs['receiptMs'])
        e.write(self.packet+0x10, OBJECT); e.write(self.packet+0x22, 7193, 'H')
        # Named DateTime calendar statics are explicit library storage fixtures.
        for offset, days in ((0x80, (0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365)),
                             (0x88, (0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366))):
            table = e.alloc(84); e.write(table+24, 13, 'I')
            e.uc.mem_write(table+32, struct.pack('<13i', *days)); e.write(GLOBAL+offset, table)
        e.write(0x1458733E0, EPOCH); e.write(0x145873380, EPOCH+inputs['nowMs']*10000)
        self.sdk = e.alloc(); e.write(GLOBAL+0xB850, self.sdk); e.write(self.sdk+0xD1, 1, 'B')
        for offset in (0x10, 0x38): e.write(self.sdk+offset, self.string('sdk-fixture'))
        e.write(self.model+0x78, self.string('account-fixture')); e.write(self.model+0x168, 12345, 'I')
        e.write(self.model+0x70, self.string('prior-name')); e.write(self.model+0x15C, 1, 'B'); e.write(self.model+0x178, 71, 'I')
        self.json_class = e.synthetic_type(0x145891A60)
        self.json_dictionary = e.synthetic_type(0x145895110)
        self.json_string = e.synthetic_type(0x145891A68)
        self.action_class = e.synthetic_type(0x145887788)
        e.synthetic_type(0x14588D458)
        e.write(self.json_string+0x118, e.model_stub(self.json_set)); self.json = {}
        info, klass, context, constructor = e.alloc(), e.alloc(), e.alloc(), e.alloc()
        e.write(0x1459836D0, info); e.write(info, klass); e.write(klass+0x30, context)
        e.write(context+0x10, constructor); e.write(constructor+8, e.model_stub(lambda: 0))
        callback = e.alloc(); e.write(0x1459A83B0, callback)
        e.write(callback+8, int(declared['sdkCallback']['entry'], 16))
        e.models[0x1405336C0] = self.allocate_json
        e.models[0x146D571C0] = self.json_add
        e.models[0x159AD6130] = lambda: 0x1234
        e.models[0x159C1ABD0] = lambda: self.string(str(e.reg('edx')))
        e.models[0x159C1B230] = lambda: self.string(str(struct.unpack('<f', (e.reg('xmm1') & 0xFFFFFFFF).to_bytes(4, 'little'))[0]))
        e.models[0x151A95300] = lambda: 0
        e.write(GLOBAL+0xA420, e.alloc())
        self.sdk_service = e.alloc(); e.models[0x1471D39E0] = lambda: self.sdk_service
        e.models[0x146D57580] = lambda: self.string(json.dumps({k: self.text(v) for k, v in self.json.items()}, sort_keys=True))
        e.models[0x152754D90] = lambda: 0
        e.models[0x152754030] = lambda: 0
        for row in declared['literals']: e.write(int(row['cell'], 16), self.string(row['value']))
        e.models[0x156D36600] = self.contains; e.models[0x156D38EC0] = self.get
        self.enumerator = e.alloc(); enumclass = e.alloc(1024); table = e.alloc(48)
        e.write(self.enumerator, enumclass); e.write(enumclass+0x80, table); e.write(enumclass+0xC0, 3, 'H')
        for i, (cell, callback) in enumerate(((0x1458861A8, self.move_next),
                (0x1458A71F0, self.current), (0x1458861B0, lambda: 0))):
            interface = e.synthetic_type(cell); e.write(table+i*16, interface); e.write(table+i*16+8, i, 'I')
            e.write(enumclass+0xD0+i*8, e.model_stub(callback))
        e.models[0x156D36540] = self.enumerate
        e.uc.mem_map(END, 4096); e.uc.mem_write(RETURN, b'\xc3')
        for entry in (DISPATCH, HANDLER, 0x14E697940, TIME, 0x159B97510, PROPS, 0x14B1046F0,
                      0x14A1B7EC0, 0x14A1B8BF0, SDK, TIME_READ, RENAME, 0x159B965F0, 0x14E697520):
            e.allow(entry)
        self.hook = e.uc.hook_add(e.U.UC_HOOK_CODE, self.observe)
        self.delta = self.adjusted = None
        self.sdk_event = None

    def observe(self, uc, address, size, data):
        e = self.e
        if address == 0x1507BC116: self.delta = e.reg('rax')
        elif address == TIME: self.adjusted = e.reg('rcx')
        elif address == SETTER:
            self.properties.append(dict(type=e.reg('edx'), rawInt64=struct.unpack('<q', e.reg('r8').to_bytes(8, 'little'))[0]))
        elif address == 0x152F7F07C:
            self.properties[-1]['storedDouble'] = struct.unpack('<d', (e.reg('xmm0') & ((1 << 64)-1)).to_bytes(8, 'little'))[0]

    def close(self): self.e.uc.hook_del(self.hook)

    def string(self, value):
        pointer = self.e.alloc(); self.g.values[pointer] = dict(kind='string', hex=value.encode('ascii').hex())
        return pointer

    def text(self, pointer): return bytes.fromhex(self.g.values[pointer]['hex']).decode('ascii')

    def allocate_json(self):
        klass = self.e.reg('rcx')
        if klass not in (self.json_class, self.json_dictionary, self.json_string, self.action_class):
            raise ValueError('Unreviewed JSON allocation')
        pointer = self.e.alloc(); self.e.write(pointer, klass)
        return pointer

    def json_set(self): self.e.write(self.e.reg('rcx')+0x18, self.e.reg('rdx')); return 0

    def json_add(self):
        self.json[self.text(self.e.reg('rdx'))] = self.e.read(self.e.reg('r8')+0x18)
        return 0

    def rent(self):
        e = self.e; pointer = e.alloc(); entries = e.alloc(32+40*16)
        e.write(entries+24, 16, 'I'); e.write(pointer+0x18, entries)
        self.dictionaries[pointer] = dict(entries=entries, keys=[])
        return pointer

    def lookup(self):
        e = self.e; info = self.dictionaries[e.reg('rcx')]
        raw = bytes(e.uc.mem_read(e.reg('rdx'), 16))
        return info['keys'].index(raw) if raw in info['keys'] else -1

    def insert(self):
        e = self.e; info = self.dictionaries[e.reg('rcx')]
        key, value = [bytes(e.uc.mem_read(e.reg(register), 16)) for register in ('rdx', 'r8')]
        if key not in info['keys']: info['keys'].append(key)
        index = info['keys'].index(key)
        e.uc.mem_write(info['entries']+32+index*40+8, key)
        e.uc.mem_write(info['entries']+32+index*40+24, value)
        return 1

    def event(self): self.events.append(self.e.read(self.e.reg('rdx'), 'I')); return 0

    def box(self):
        e = self.e; klass = e.reg('rcx')
        if klass not in self.eventclasses: return self.g.box()
        pointer = e.alloc(); e.write(pointer, klass)
        e.uc.mem_write(pointer+16, bytes(e.uc.mem_read(e.reg('rdx'), self.eventclasses[klass])))
        return pointer

    def nodes(self, pointer):
        info = self.g.maps.dictionaries[self.e.read(pointer+0x10)]
        return [(self.e.read(node+40, 'I'), self.e.read(node+48)) for node in info['nodes']]

    def contains(self): return int(self.e.reg('edx') in dict(self.nodes(self.e.reg('rcx'))))
    def get(self): return dict(self.nodes(self.e.reg('rcx')))[self.e.reg('edx')]
    def enumerate(self): self.items = self.nodes(self.e.reg('rcx')); self.position = -1; return self.enumerator
    def move_next(self): self.position += 1; return int(self.position < len(self.items))

    def current(self):
        key, value = self.items[self.position]; pointer = self.e.reg('rcx')
        self.e.write(pointer, key); self.e.write(pointer+8, value)
        return pointer

    def run(self):
        e = self.e; e.write(STACK+0x100008, RETURN)
        pointer = e.read(OBJECT+0x18)
        e.write(e.read(pointer+0x18)+0x18, len(self.nodes(pointer)), 'I')
        trace = fragment(e, DISPATCH, {STOP, 0x159B975C8, 0x159B975FF},
            dict(rsp=STACK+0x100008, rcx=self.manager, rdx=self.packet))
        if trace['stop'] == hex(STOP):
            self.sdk_event = self.text(e.reg('rdx'))
            if e.reg('rcx') != self.sdk_service: raise ValueError('SDK event receiver changed')
        return trace


def recover(binary, metadata, startup, records, libraries, root):
    directory = root / 'protocol/cn-7.2'
    declared = declarations(binary, metadata, startup, records, directory)
    bindings = map_bindings(binary, metadata, startup, records)
    results, fixtures, nickname_event = [], [], None
    for ordinal, case in enumerate(fixture_cases()):
        e = Offline72(binary, libraries, records, True); graph = runtime = None
        try:
            graph = PlayerDataGraph72(e, directory, bindings)
            read = graph.run_graph(ROOT, case['wire'])
            runtime = PlayerContract72(e, graph, declared, case['inputs'], ordinal)
            epoch_trace = fragment(e, EPOCH_ENTRY, {0x14E697576},
                dict(rsp=STACK+0x100008, rdi=0x2BCA2875F4374000))
            if e.reg('rax') != EPOCH: raise ValueError('Native DateToTicks epoch mismatch')
            trace = runtime.run(); success = trace['stop'] == hex(STOP)
            if success == (case['name'] == 'timestamp-date-range-error'):
                raise ValueError('DateTime boundary outcome differs')
            if runtime.properties != case['expectedProperties'] or runtime.delta != case['delta'] or runtime.adjusted != case['adjusted']:
                raise ValueError('Native property or time arithmetic differs from independent input')
            values = {k: runtime.text(v) for k, v in runtime.json.items()}
            level = 0
            for prop in case['expectedProperties']:
                if prop['type'] == 10013: level = int(prop['storedDouble']) & 0xFFFFFFFF
            clock_seconds = offset_ticks = None
            if success:
                if values.get('role_name') != case['inputs']['nickname'] or values.get('role_level') != str(level):
                    raise ValueError('Named SDK destination differs from native source')
                if runtime.sdk_event != 'login_will_enter_game': raise ValueError('Named initialization event changed')
                if runtime.json['role_name'] != e.read(OBJECT+0x20) or e.read(runtime.model+0x70) != e.read(OBJECT+0x20):
                    raise ValueError('Nickname pointer lost between reader/model/SDK')
                offset_ticks = e.read(0x1458733C8, 'q')
                if offset_ticks != (case['adjusted']-case['inputs']['nowMs'])*10000:
                    raise ValueError('Native DateTime millisecond synchronization differs')
                e.write(STACK+0x100008, RETURN)
                clock_trace = fragment(e, TIME_READ, {RETURN}, dict(rsp=STACK+0x100008))
                clock_seconds = e.reg('rax')
                if clock_seconds != case['adjusted']//1000: raise ValueError('Native synchronized second clock differs')
            else: clock_trace = None
            row = dict(name=case['name'], stop=trace['stop'], passed=True, properties=runtime.properties,
                sdkRoleName=values.get('role_name'), sdkRoleLevel=values.get('role_level'),
                sdkEvent=runtime.sdk_event,
                nicknamePointerPreserved=success, rawModel5=e.read(runtime.model+0x178, 'I'),
                rawModel7=bool(e.read(runtime.model+0x15C, 'B')), delayMilliseconds=runtime.delta,
                adjustedMilliseconds=runtime.adjusted, clockSeconds=clock_seconds, offsetTicks=offset_ticks,
                models=trace['models'], nativeEntries=[hex(a) for a in (DISPATCH, HANDLER, PROPS, SETTER, MODEL_CALLBACK,
                    TIME, SDK, 0x14B1046F0, 0x14A1B7EC0, 0x14A1B8BF0) if hex(a) in trace['executedAddresses']],
                nativePathSHA256=trace['nativePathSHA256'], wireSHA256=hashlib.sha256(case['wire']).hexdigest())
            results.append(row); fixtures.append(dict(**row, reader=read, trace=trace, epochTrace=epoch_trace, clockTrace=clock_trace))
            if ordinal == 0:
                changed = runtime.string('Renamed'); argument = e.alloc(); e.write(argument+0x18, changed)
                e.write(GLOBAL+0x9F00, e.alloc()); e.models[0x148DEE170] = lambda: 0; e.models[0x150AAD620] = lambda: 0
                e.write(STACK+0x100008, RETURN)
                rename_trace = fragment(e, RENAME, {RETURN}, dict(rsp=STACK+0x100008, rcx=runtime.manager, rdx=argument))
                if e.read(runtime.model+0x70) != changed or runtime.events[-1] != 842:
                    raise ValueError('Independent nickname update/event chain differs')
                nickname_event = dict(entry=hex(RENAME), value=842, symbol='RefreshNickname', modelOffset=0x70,
                    pointerPreserved=True, nativeReturned=True, nativePathSHA256=rename_trace['nativePathSHA256'])
                fixtures[-1]['nicknameTrace'] = rename_trace
            print(json.dumps(dict(case=case['name'], properties=len(runtime.properties), outcome=trace['stop'])), flush=True)
        finally:
            if runtime: runtime.close()
            if graph: graph.close()
            e.close()
    return dict(schemaVersion=1, inputSHA256=HASHES, declarations=declared, results=results, fixtures=fixtures,
        fixtureCount=len(results), nicknameEvent=nickname_event, runtimeStatus='not-tested',
        semanticIdentityStatus='verified', semanticName='PlayerDataNotify', targetProtocolReady=False,
        identityBasis='inferred-native-player-initialization-purpose', originalClientMessageNameRecovered=False,
        wholeHandlerReturned=False, unresolvedTags=UNRESOLVED,
        fieldBindings=[dict(typeIndex=79263, tag=8, semanticField='nickname', status='verified',
            evidence='reader -> model+70 -> native SDK role_name; independent RefreshNickname updater'),
            dict(typeIndex=79263, tag=6, semanticField='prop_map', status='verified',
            evidence='native normal-property application/setter -> enum PROP_PLAYER_LEVEL -> model -> SDK role_level'),
            dict(typeIndex=78350, tag=1, semanticField='property_type', status='verified',
            evidence='child field, not outer map key, selects actual model property setter'),
            dict(typeIndex=78350, tag=2, semanticField='int64_value', status='verified',
            evidence='only discriminator2 unboxes int64 into actual property conversion and storage'),
            dict(typeIndex=79263, tag=13, semanticField='server_time', unit='milliseconds', status='verified',
            evidence='native receive-delay addition -> DateTime.Add(milliseconds) -> synchronized epoch-second clock')],
        models=['Existing raw graph allocation, primitive input, boxing and bounded map storage',
            'Map lookup/enumerator over actual decoded nodes; encoded-property dictionary storage and pool allocation',
            'Explicit 16x256 reversible tables and deterministic Random; native key/value encode/decode execute',
            'UTC local epoch/current clock and named DateTime calendar-array storage; actual DateToTicks(1970,1,1), elapsed clamp, Add and clock getter execute',
            'SDK JSON storage, number formatting, SDK environment callbacks and event sink; native data routing executes'],
        outsideScope=['Original English message spelling', 'tag5/tag7 meanings and child tag3/tag4 business semantics',
            'Full Handler return/SDK initialization/object-pool startup', 'Real timezone startup/IFix/live ABI/client acceptance',
            'All property enum consumers; only selected independent property cases are executed'])


def validate_report(report):
    errors = []
    expected = dict(schemaVersion=1, inputSHA256=HASHES, fixtureCount=15, runtimeStatus='not-tested',
        semanticIdentityStatus='verified', semanticName='PlayerDataNotify', targetProtocolReady=False,
        identityBasis='inferred-native-player-initialization-purpose', originalClientMessageNameRecovered=False,
        wholeHandlerReturned=False, unresolvedTags=UNRESOLVED)
    if any(report.get(k) != v for k, v in expected.items()) or not report.get('models'):
        errors.append('Player data input/scope changed')
    bindings = [(b.get('typeIndex'), b.get('tag'), b.get('semanticField'), b.get('status'))
                for b in report.get('fieldBindings', [])]
    if bindings != [(79263, 8, 'nickname', 'verified'), (79263, 6, 'prop_map', 'verified'),
                    (78350, 1, 'property_type', 'verified'), (78350, 2, 'int64_value', 'verified'),
                    (79263, 13, 'server_time', 'verified')]:
        errors.append('Limited field bindings changed or unknown field promoted')
    declared = report.get('declarations', {})
    symbols = [(enum.get('typeIndex'), value.get('symbol'), value.get('value'))
               for enum in declared.get('enums', []) for value in enum.get('values', [])]
    if (declared.get('rawEvidenceSHA256') != RAW_SHA256 or
        symbols != [(62096, 'PROP_PLAYER_LEVEL', 10013), (62096, 'PROP_CUR_CLIMATE_METER', 10035),
                    (75464, 'RefreshNickname', 842)] or declared.get('scaledProperty', {}).get('divisor') != 100.0):
        errors.append('Independent raw/enum/scale provenance changed')
    event = report.get('nicknameEvent', {})
    if any(event.get(k) != v for k, v in dict(entry=hex(RENAME), value=842, symbol='RefreshNickname',
        modelOffset=0x70, pointerPreserved=True, nativeReturned=True).items()):
        errors.append('Independent nickname update evidence missing')
    rows = report.get('results', [])
    if [r.get('name') for r in rows] != [c['name'] for c in fixture_cases()]: errors.append('Boundary fixtures changed')
    for row, case in zip(rows, fixture_cases()):
        success = case['name'] != 'timestamp-date-range-error'; level = 0
        for prop in case['expectedProperties']:
            if prop['type'] == 10013: level = int(prop['storedDouble']) & 0xFFFFFFFF
        target = dict(stop=hex(STOP) if success else '0x159b975c8', passed=True,
            properties=case['expectedProperties'], delayMilliseconds=case['delta'], adjustedMilliseconds=case['adjusted'],
            sdkRoleName=case['inputs']['nickname'] if success else None, sdkRoleLevel=str(level) if success else None,
            sdkEvent='login_will_enter_game' if success else None,
            nicknamePointerPreserved=success, rawModel5=case['inputs']['raw5'] if success else 71,
            rawModel7=case['inputs']['raw7'] if success else True,
            offsetTicks=(case['adjusted']-case['inputs']['nowMs'])*10000 if success else None,
            clockSeconds=case['adjusted']//1000 if success else None,
            wireSHA256=hashlib.sha256(case['wire']).hexdigest())
        if any(row.get(k) != v for k, v in target.items()): errors.append('Player model/native time/property contract changed')
        forbidden = {hex(a) for a in (DISPATCH, HANDLER, PROPS, SETTER, MODEL_CALLBACK, SDK, TIME, 0x14B1046F0, 0x14A1B7EC0, 0x14A1B8BF0)}
        if forbidden & set(row.get('models', [])): errors.append('Business dataflow replaced with a model')
        required = [DISPATCH, HANDLER, TIME] + ([PROPS, SDK] if success else [])
        if case['expectedProperties']: required += [SETTER, MODEL_CALLBACK, 0x14B1046F0, 0x14A1B7EC0, 0x14A1B8BF0]
        if not {hex(a) for a in required} <= set(row.get('nativeEntries', [])): errors.append('Native dataflow entry missing')
    return errors


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'summary-output'): p.add_argument('--'+name, required=True, type=Path)
    p.add_argument('--libraries', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    paths = (a.output, a.summary_output)
    if len({p.resolve() for p in paths}) != 2 or any(p.resolve().is_relative_to(a.client.resolve()) for p in paths):
        p.error('Distinct outputs must be outside client')
    binary, metadata, startup = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    root = Path(__file__).resolve().parents[1]
    report = recover(binary, metadata, startup, records, a.libraries, root)
    report['sourceSHA256'] = {name: hashlib.sha256((root/name).read_bytes()).hexdigest() for name in (
        'tools/client72_player_data_contract.py', 'tools/trace_client72_player_data.py', 'tools/client72_graph.py',
        'tools/client72_map_runtime.py', 'tools/client72_session_runtime.py', 'protocol/cn-7.2/player-data-wire-observations.json')}
    errors = validate_report(report)
    if errors: raise ValueError(errors)
    for path, value in ((a.output, report), (a.summary_output, {k: v for k, v in report.items() if k != 'fixtures'})):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2)+'\n', 'utf8')


if __name__ == '__main__': main()
