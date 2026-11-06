"""CN72 store category limits: real reader/dispatcher/application/dictionary.

No Asta schema is an input. The existing raw reader slice is reused, not
rescanned. Managed storage, enum hash and the event sink remain explicit fixtures.
"""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, TypeResolver, HEADER_VA, BODY, number
    from .extract_client72_signatures import parameter_start, parameter_words
    from .extract_client72_enums import recover as recover_enums
    from .extract_client72_literals import Literals72
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from .trace_client72_responses import Reader72
    from .trace_client72_token import varint
    from .client72_session_runtime import fragment
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, TypeResolver, HEADER_VA, BODY, number
    from extract_client72_signatures import parameter_start, parameter_words
    from extract_client72_enums import recover as recover_enums
    from extract_client72_literals import Literals72
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from trace_client72_responses import Reader72
    from trace_client72_token import varint
    from client72_session_runtime import fragment

MASK = 0xFFFFFFFF
TYPE, CMD, READER, CONSTRUCTOR = 28836, 22609, 0x14FD45000, 0x14FD44980
DISPATCH, WRAPPER, APPLICATION, EVENT = 0x153CBB460, 0x153CBC210, 0x14E3D6DA0, 0x14E3D6C60
INSERT, EVENT_SEND, REGISTER = 0x157D1A480, 0x1532D6720, 0x1532ED560
RAW_SHA256 = '45b2565a33c10862946ed226b5408d8fc51c2f8640178d2d47c4a1e14327f4f4'
# tag, object offset, target item enum symbol, conventional purpose alias.
LIMITS = ((1, 0x1C, 'ITEM_RELIQUARY', 'reliquary_count_limit'),
          (9, 0x28, 'ITEM_WEAPON', 'weapon_count_limit'),
          (14, 0x34, 'ITEM_MATERIAL', 'material_count_limit'),
          (5, 0x20, 'ITEM_FURNITURE', 'furniture_count_limit'))
INDEPENDENT_WIRE = bytes.fromhex('080b1016202c28373001384d4863708d01')


def scalar_wire(tag, value):
    return varint(tag << 3) + varint(value)


def store_limits_wire(store_type, reliquary, furniture, weapon=0, material=0):
    """Encode only independently consumed uint32 fields; no weight alias."""
    if any(type(v) is not int or not 0 <= v <= MASK for v in (store_type, reliquary, furniture, weapon, material)):
        raise ValueError('Store and capacity inputs must be uint32')
    return b''.join(scalar_wire(tag, value) for tag, value in
                    ((1, reliquary), (5, furniture), (6, store_type), (9, weapon), (14, material)))


def declarations(binary, metadata, startup, records, directory):
    raw_path = directory / 'initialization-wire-observations.json'
    if hashlib.sha256(raw_path.read_bytes()).hexdigest() != RAW_SHA256:
        raise ValueError('Reviewed raw initialization evidence changed')
    raw = json.loads(raw_path.read_text('utf8'))
    if raw['inputSHA256'] != HASHES or (raw['typeIndex'], raw['cmdId']) != (TYPE, CMD):
        raise ValueError('Reviewed raw identity changed')
    byid = {r['index']: r for r in records}
    methods = {int(m['registeredAddress'], 16): (r, m) for r in records for m in r['methods'] if m['registeredAddress'] != '0x0'}
    pe = PE(binary); header = pe.offset(HEADER_VA)
    resolver = TypeResolver(pe, startup, records, header)
    method_base = BODY + (number(binary, header + 0xB0) ^ 0x51324A1F)
    parameter_base = BODY + ((number(binary, header + 0x174) + 0xAE6BCD68) & MASK)
    typed = []
    for entry, owner_id in ((WRAPPER, 54747), (APPLICATION, 59658)):
        owner, method = methods[entry]
        if owner['index'] != owner_id: raise ValueError('Store consumer owner changed')
        first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
        _, ref = parameter_words(first, number(metadata, parameter_base + first * 8), number(metadata, parameter_base + first * 8 + 4))
        parameter = resolver.resolve_index(ref)
        if parameter.get('typeDefinition') != TYPE: raise ValueError('Store consumer typed parameter changed')
        typed.append(dict(entry=hex(entry), ownerTypeIndex=owner_id, owner=owner['name'], method=method['name'], parameter=parameter))
    getter = next(m for m in byid[TYPE]['methods'] if m.get('virtualSlot') == 22)
    if getter.get('literalU16Candidate') != CMD: raise ValueError('Store command getter changed')
    field = next(f for f in byid[TYPE]['fields'] if f['offset'] == 0x30)
    if field['type'].get('typeDefinition') != 55345: raise ValueError('Store enum type changed')
    dictionary = next(f for f in byid[59658]['fields'] if f['offset'] == 0x78)
    arguments = dictionary['type']['arguments']
    if (arguments[0].get('typeDefinition'), arguments[1]['kind']) != (34819, 'uint32'):
        raise ValueError('Store limit dictionary key/value types changed')
    enums = recover_enums(binary, metadata, records, indices=(55345, 34819))['enums']
    symbols = {v['symbol']: v['value'] for enum in enums for v in enum['values']}
    expected = dict(StoreNone=0, StorePack=1, StoreDepot=2, ITEM_RELIQUARY=3, ITEM_WEAPON=4, ITEM_MATERIAL=2, ITEM_FURNITURE=6)
    if any(symbols.get(k) != v for k, v in expected.items()): raise ValueError('Named store/item enum constants changed')
    # Exact call targets are checked arithmetically, avoiding a copied displacement.
    compare = binary[pe.offset(0x153CBB8F9, 12):][:12]
    if compare[:8] != bytes.fromhex('81f9515800000f84') or 0x153CBB905 + int.from_bytes(compare[8:], 'little', signed=True) != 0x153CBBD3C:
        raise ValueError('Store command dispatch branch changed')
    registration = binary[pe.offset(0x153CBFF84, 12):][:12]
    if registration[:8] != bytes.fromhex('66ba51584989f0e8') or 0x153CBFF90 + int.from_bytes(registration[8:], 'little', signed=True) != REGISTER:
        raise ValueError('Store command registration changed')
    call = binary[pe.offset(0x153CBBD4C, 5):][:5]
    if call[0] != 0xE8 or 0x153CBBD51 + int.from_bytes(call[1:], 'little', signed=True) != WRAPPER:
        raise ValueError('Store dispatcher typed forwarding changed')
    literal_reader = Literals72(binary, metadata)
    literal = next(r for r in literal_reader.group(0x71C6) if r['index'] == 25222)
    literal = dict(literal, value=literal_reader.decode(25222), initializationGroup='0x71c6')
    if literal['value'] != 'SHOP_ITEM_LIMITED' or literal['cell'] != '0x145afb250':
        raise ValueError('Capacity-boundary literal identity changed')
    code = binary[pe.offset(0x14E3E330D, 7):][:7]
    if code[:3] != bytes.fromhex('488b0d') or 0x14E3E3314 + int.from_bytes(code[3:], 'little', signed=True) != int(literal['cell'], 16):
        raise ValueError('Capacity-boundary literal reference changed')
    owner, method = methods[0x14E3D7D70]
    if owner['index'] != 59658 or method['parameterCount'] != 3: raise ValueError('Capacity readback declaration changed')
    first = parameter_start(method['index'], number(metadata, method_base + method['index'] * 26 + 4))
    readback_parameters = []
    for i in range(3):
        _, ref = parameter_words(first + i, number(metadata, parameter_base + (first + i) * 8), number(metadata, parameter_base + (first + i) * 8 + 4))
        readback_parameters.append(resolver.resolve_index(ref))
    if (readback_parameters[0].get('typeDefinition'), readback_parameters[1]['kind'], readback_parameters[2]['kind']) != (34819, 'int32', 'uint32') or not all(p.get('byRef') for p in readback_parameters[1:]):
        raise ValueError('Capacity current-count/limit out-parameters changed')
    entries = [CONSTRUCTOR, READER, DISPATCH, WRAPPER, APPLICATION, EVENT, INSERT, 0x14E3D7D70, 0x157D19FE0, 0x14E3E3190]
    return dict(rawEvidenceSHA256=RAW_SHA256, typedConsumers=typed, enums=enums, dictionaryField=dictionary,
                commandGetter=getter, dispatchCompare='0x153cbb8f9', dispatchForward='0x153cbbd4c',
                commandRegistration='0x153cbff84',
                capacityBoundaryLiteral=literal, capacityReadbackParameters=readback_parameters,
                nativeEntry32SHA256={hex(entry): hashlib.sha256(binary[pe.offset(entry, 32):][:32]).hexdigest() for entry in entries}), symbols


class StoreLimits72:
    def __init__(self, binary, records, libraries):
        self.e = e = Offline72(binary, libraries, records, trace_instructions=False)
        self.reader = Reader72(e)
        for entry in (CONSTRUCTOR, READER, DISPATCH, WRAPPER, APPLICATION, EVENT): e.allow(entry)
        e.allow_unwind(INSERT)
        # Hashing is the sole modeled Dictionary primitive; actual native insert,
        # overwrite, bucket/entry stores and native key equality execute.
        self.hash_stub = e.model_stub(lambda: e.read(e.reg('rcx') + 0x10, 'I'))
        e.models[EVENT_SEND] = self.event_sink
        self.events = []

    def close(self):
        self.reader.close(); self.e.close()

    def event_sink(self):
        e = self.e
        if e.reg('rcx') != self.bus: raise ValueError('Unexpected event receiver')
        raw = bytes(e.uc.mem_read(e.reg('rdx'), 24))
        self.events.append(raw.hex())
        return 0

    def dictionary(self):
        e = self.e; p = e.alloc(); buckets = e.alloc(32 + 17 * 4); entries = e.alloc(32 + 17 * 16)
        e.write(buckets + 0x18, 17, 'I'); e.write(entries + 0x18, 17, 'I')
        e.write(p + 0x10, entries); e.write(p + 0x18, buckets); e.write(p + 0x44, MASK, 'I')
        return p

    def setup(self):
        e = self.e
        info, klass, context, hashclass = e.alloc(), e.alloc(), e.alloc(), e.alloc(512)
        e.write(0x145A6B588, info); e.write(info, klass); e.write(klass + 0x30, context)
        e.write(context + 0x88, info); e.write(context + 0x80, hashclass)
        e.write(hashclass + 0xC8, 1, 'B'); e.write(hashclass + 0xE0, self.hash_stub)
        self.pack, self.depot = e.alloc(), e.alloc()
        self.packdict, self.depotdict = self.dictionary(), self.dictionary()
        e.write(self.pack + 0x78, self.packdict); e.write(self.depot + 0x78, self.depotdict)
        e.write(GLOBAL + 0xB4D0, self.pack); e.write(GLOBAL + 0x286A0, self.depot)
        self.bus = e.alloc(); e.write(GLOBAL + 0x9E50, self.bus)
        self.module, self.packet = e.alloc(), e.alloc()
        self.events = []

    def values(self, dictionary):
        e = self.e; entries = e.read(dictionary + 0x10)
        return {str(e.read(entries + 32 + i * 16 + 8, 'I')): e.read(entries + 32 + i * 16 + 12, 'I')
                for i in range(e.read(dictionary + 0x38, 'I'))}

    def case(self, name, wires, command=CMD):
        e = self.e; results = []
        for index, wire in enumerate(wires):
            read = self.reader.run(TYPE, wire, READER)
            if index == 0: self.setup()
            e.write(self.packet + 0x10, OBJECT); e.write(self.packet + 0x22, command, 'H')
            reads, path = [], []
            def memory_read(uc, access, address, size, value, data):
                reads.append(dict(objectOffset=address - OBJECT, size=size, instruction=hex(e.reg('rip'))))
            hook = e.uc.hook_add(e.U.UC_HOOK_MEM_READ, memory_read, begin=OBJECT + 0x18, end=OBJECT + 0x37)
            e.uc.context_restore(e.context); e.calls, e.failure, e.steps = [], None, 0
            e.before_instruction = lambda address: path.append(address)
            try: e.run(DISPATCH, self.module, self.packet)
            finally: e.before_instruction = None; e.uc.hook_del(hook)
            actual_entries = [entry for entry in (DISPATCH, WRAPPER, APPLICATION, INSERT, EVENT) if entry in path]
            results.append(dict(wire=wire.hex(), readerEvents=read['events'], packetFieldReads=reads,
                                executedNativeEntries=[hex(entry) for entry in actual_entries],
                                nativePathSHA256=hashlib.sha256(b''.join(a.to_bytes(8, 'little') for a in path)).hexdigest(),
                                nativeInstructions=e.steps, packLimits=self.values(self.packdict), depotLimits=self.values(self.depotdict),
                                events=list(self.events), models=[c['address'] for c in e.calls],
                                completion='reader-and-dispatcher-returned'))
        return dict(name=name, command=command, results=results)

    def registration(self):
        e = self.e; e.prepare(); e.allow(0x153CBFF50)
        owner, network = e.alloc(), e.alloc(); calls = []
        def register():
            if e.reg('rcx') != network or e.reg('r8') != owner: raise ValueError('Registration source changed')
            calls.append(e.reg('dx')); return 0
        e.models[REGISTER] = register
        trace = fragment(e, 0x153CBFF50, {0x153CBFF90}, dict(rsp=STACK + 0x100008, rcx=owner, rdx=network))
        if calls != [20892, CMD]: raise ValueError('Registration command sequence changed')
        return dict(commands=calls, trace=trace, scope='prefix through target registration; registration table itself is an explicit sink')

    def constructor(self):
        e = self.e; rows = []
        offsets = (0x18, 0x1C, 0x20, 0x24, 0x28, 0x2C, 0x30, 0x34)
        for value in (0, 0x12345678):
            e.prepare()
            for offset in offsets: e.write(OBJECT + offset, value, 'I')
            e.run(CONSTRUCTOR, OBJECT, 0)
            actual = {hex(offset): e.read(OBJECT + offset, 'I') for offset in offsets}
            if set(actual.values()) != {value}: raise ValueError('Constructor changed raw fields')
            rows.append(dict(initialRawValue=value, actualFields=actual, nativeInstructions=e.steps, completion='constructor-returned'))
        return dict(entry=hex(CONSTRUCTOR), fixtures=rows,
                    scope='Actual constructor leaves zero and sentinel storage unchanged; managed zero allocation is an explicit fixture, not a constructor-provided gameplay default')

    def null_boundaries(self):
        e = self.e; rows = []
        e.prepare(); receiver = e.alloc()
        trace = fragment(e, WRAPPER, {0x1404C2BE0}, dict(rsp=STACK + 0x100008, rcx=receiver, rdx=0))
        rows.append(dict(name='null-message', trace=trace, scope='Actual native null branch reaches throw helper; helper body not executed'))
        e.prepare(); self.setup(); e.write(GLOBAL + 0xB4D0, 0)
        e.write(OBJECT + 0x30, 1, 'I'); e.write(self.packet + 0x10, OBJECT); e.write(self.packet + 0x22, CMD, 'H')
        trace = fragment(e, DISPATCH, {0x1404C2BE0}, dict(rsp=STACK + 0x100008, rcx=self.module, rdx=self.packet))
        if self.values(self.packdict) or self.values(self.depotdict) or self.events:
            raise ValueError('Missing-store boundary mutated capacity or emitted an event')
        rows.append(dict(name='missing-pack-instance', trace=trace, scope='Actual native route check reaches throw helper before application; helper body not executed'))
        return rows

    def capacity_boundaries(self):
        """Read the real applied material limit, then execute its threshold test."""
        e = self.e
        e.allow(0x14E3D7D70); e.allow(0x14E3E3190); e.allow_unwind(0x157D19FE0)
        info = e.read(0x145A6B588); klass = e.read(info); context = e.read(klass + 0x30)
        e.write(context + 0x78, info); e.write(0x145A6B1B0, info)
        outer, entries, items = e.alloc(), e.alloc(32 + 24), e.alloc()
        e.write(entries + 0x18, 1, 'I'); e.write(entries + 0x30, items)
        e.write(outer + 0x28, entries); e.write(self.pack + 0x50, outer)
        current_info, current_klass, current_context = e.alloc(), e.alloc(), e.alloc()
        e.write(current_info, current_klass); e.write(current_klass + 0x30, current_context)
        e.write(current_context + 0x78, current_info); e.write(0x145A6B1A8, current_info)
        def current_lookup():
            if e.reg('rcx') != outer or e.reg('edx') != 2: raise ValueError('Current material lookup source changed')
            return 0
        e.models[0x1573D2D90] = current_lookup
        e.write(0x145BA3CB2, 1, 'B')  # Explicit metadata-initialized fixture.
        before = self.values(self.packdict); rows = []
        if before.get('2') != 141: raise ValueError('Capacity probe must use the independently read/applied material source')
        for current in (0, 140, 141, 142):
            e.write(items + 0x44, current, 'I'); e.write(items + 0x40, 0, 'I')
            trace = fragment(e, 0x14E3E32D1, {0x14E3E330D, 0x14E3E3385}, dict(rsp=STACK + 0x100008))
            actual_current = e.read(STACK + 0x100008 + 0x34, 'I')
            actual_limit = e.read(STACK + 0x100008 + 0x30, 'I')
            expected = '0x14e3e3385' if current < 141 else '0x14e3e330d'
            if (actual_current, actual_limit, trace['stop']) != (current, 141, expected):
                raise ValueError('Real downstream capacity comparison differs')
            if self.values(self.packdict) != before: raise ValueError('Readback modified the applied limits')
            rows.append(dict(currentCountSource=current, appliedMaterialLimit=141, currentCountReadback=actual_current,
                             limitReadback=actual_limit, belowLimit=current < 141, trace=trace))
        return dict(fixtures=rows, appliedLimitSourceFixture='duplicate-wire-last-wins',
                    scope='Native item-enum count/limit getter, real capacity dictionary lookup and current<limit branch execute; current-item lookup/index and item-count storage are explicit inputs. Stops before SHOP_ITEM_LIMITED lookup/UI or function return epilogue.')


def validate_fixtures(rows):
    byname = {r['name']: r for r in rows}
    expected = {'3': 11, '4': 99, '2': 141, '6': 55}
    if byname['independent-pack']['results'][0]['wire'] != INDEPENDENT_WIRE.hex(): raise ValueError('Independent store wire changed')
    for name, route, values in [('independent-pack', 'packLimits', expected), ('depot', 'depotLimits', expected),
                                ('store-none-falls-through-pack', 'packLimits', expected),
                                ('unknown-store-falls-through-pack', 'packLimits', expected),
                                ('unconsumed-fields-perturbed', 'packLimits', expected),
                                ('uint32-upper-bound', 'packLimits', dict.fromkeys(expected, MASK)),
                                ('explicit-zero', 'packLimits', dict.fromkeys(expected, 0)),
                                ('omitted-fields-managed-zero', 'packLimits', dict.fromkeys(expected, 0))]:
        result = byname[name]['results'][0]
        other = 'depotLimits' if route == 'packLimits' else 'packLimits'
        if result[route] != values or result[other] != {}: raise ValueError('Native store limits differ: ' + name)
        if {r['objectOffset'] for r in result['packetFieldReads']} != {0x30, 0x1C, 0x28, 0x34, 0x20}:
            raise ValueError('Unexpected packet field consumption: ' + name)
        if result['events'] != ['190100000000000000000000000000000000000000000000']:
            raise ValueError('Actual event boundary changed')
        if result['executedNativeEntries'] != [hex(v) for v in (DISPATCH, WRAPPER, APPLICATION, INSERT, EVENT)]:
            raise ValueError('Native application path incomplete')
    update = byname['existing-dictionary-overwrite']['results']
    if len(update) != 2 or update[0]['packLimits'] != expected or update[1]['packLimits'] != {'3': 7, '4': 9, '2': 10, '6': 8}:
        raise ValueError('Native existing dictionary overwrite failed')
    if len(update[1]['events']) != 2: raise ValueError('Second update did not reach event boundary')
    duplicate = byname['duplicate-wire-last-wins']['results'][0]
    if duplicate['packLimits'] != {'3': 7, '4': 99, '2': 141, '6': 55}: raise ValueError('Duplicate limit failed')
    unknown = byname['unregistered-command']['results'][0]
    if unknown['packLimits'] or unknown['depotLimits'] or unknown['packetFieldReads'] or unknown['events']:
        raise ValueError('Unknown command unexpectedly consumed packet')


def recover(binary, metadata, startup, records, libraries, directory):
    declaration, symbols = declarations(binary, metadata, startup, records, directory)
    h = StoreLimits72(binary, records, libraries)
    try:
        registration = h.registration()
        constructor = h.constructor()
        boundaries = h.null_boundaries()
        # The independent vector contains nonzero values for ALL raw fields,
        # including the three fields not read by this normal application path.
        rows = [h.case('unregistered-command', [INDEPENDENT_WIRE], 65535),
                h.case('independent-pack', [INDEPENDENT_WIRE]),
                h.case('depot', [store_limits_wire(2, 11, 55, 99, 141)]),
                h.case('store-none-falls-through-pack', [store_limits_wire(0, 11, 55, 99, 141)]),
                h.case('unknown-store-falls-through-pack', [store_limits_wire(MASK, 11, 55, 99, 141)]),
                h.case('unconsumed-fields-perturbed', [INDEPENDENT_WIRE + b''.join(scalar_wire(tag, MASK) for tag in (2, 4, 7))]),
                h.case('uint32-upper-bound', [store_limits_wire(1, MASK, MASK, MASK, MASK)]),
                h.case('explicit-zero', [store_limits_wire(1, 0, 0, 0, 0)]),
                h.case('omitted-fields-managed-zero', [b'']),
                h.case('existing-dictionary-overwrite', [INDEPENDENT_WIRE, store_limits_wire(1, 7, 8, 9, 10)]),
                h.case('duplicate-wire-last-wins', [INDEPENDENT_WIRE + bytes.fromhex('0807')])]
        validate_fixtures(rows)
        capacity = h.capacity_boundaries()
    finally: h.close()
    bindings = [dict(tag=6, clientField='CLFJNKKFONI', objectOffset=0x30, semanticField='store_type',
                     evidence='Target enum StorePack=1/StoreDepot=2 and native route selection', valueEncoding='identity', status='verified-offline')]
    raw = json.loads((directory/'initialization-wire-observations.json').read_text('utf8'))
    for tag, offset, symbol, meaning in LIMITS:
        field = next(f for f in raw['fields'] if f['tag'] == tag and f['objectOffset'] == offset)
        bindings.append(dict(tag=tag, clientField=field['clientField'], objectOffset=offset, semanticField=meaning,
                             targetItemSymbol=symbol, targetItemValue=symbols[symbol], valueEncoding='identity-uint32',
                             evidence='Real native receiver loads -> Dictionary<target item enum,uint32> native insertion and overwrite', status='verified-offline'))
    unknown = [dict(field, consumerObservation='Not read by the executed normal dispatcher/wrapper/application path', semanticStatus='unresolved')
               for field in raw['fields'] if field['tag'] in (2, 4, 7)]
    return dict(schemaVersion=1, inputSHA256=HASHES, typeIndex=TYPE, clientType='KNCCLAJAEEC', cmdId=CMD,
                semanticName='StoreWeightLimitNotify', semanticIdentityStatus='verified-offline-purpose-alias',
                identityInference='Independent target command registration/dispatch, typed store receiver and named store/item enum capacity application establish the notification role. No official message-name literal was found; canonical name remains a server purpose alias.',
                declarations=declaration, registration=registration, constructor=constructor, nativeNullBoundaries=boundaries,
                downstreamCapacityBoundaries=capacity,
                bindings=bindings, unresolvedFields=unknown,
                fixtureCount=len(rows), dispatcherExecutions=sum(len(r['results']) for r in rows), fixtures=rows,
                runtimeStatus='not-tested', targetProtocolReady=False,
                limitations=['Only IFix-absent normal paths; no client process/network or event listener execution',
                             'Initialized pack/depot dictionaries with capacity17 and explicit generic metadata; no allocator/resize coverage',
                             'Enum hash is an explicit identity callback; native key equality, bucket/entry insertion and overwrite execute',
                             'WireInput reader primitives remain explicit fixtures; no new reader scan or native malformed-input acceptance claim',
                             'Event281 is captured at the real send call and acknowledged as an explicit void sink; subscribers are not executed',
                             'Tag2/tag4/tag7 have no business assignment here; Asta weight_limit tag2 is not promoted',
                             'Downstream current-material lookup/index and current-count storage are explicit inputs; the limit lookup and current<limit comparison are native. No UI or full material admission function executes',
                             'No nested/list/uint64 fields exist in this slice; no such coverage claimed'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--evidence-directory', type=Path, default=Path('protocol/cn-7.2'))
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.client.resolve()): parser.error('Write outside read-only client')
    binary, metadata, startup = [read_verified(args.client/name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    output = recover(binary, metadata, startup, records, args.python_libs, args.evidence_directory)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, ensure_ascii=False)+'\n', 'utf8')
    print(json.dumps(dict(bindings=len(output['bindings']), unresolved=len(output['unresolvedFields']), fixtures=output['fixtureCount'], dispatcherExecutions=output['dispatcherExecutions'])))


if __name__ == '__main__': main()
