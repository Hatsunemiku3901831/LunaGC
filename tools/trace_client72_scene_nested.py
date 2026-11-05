"""B-02e6: six native nested schemas and nonempty scene message graphs."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import struct
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, TypeResolver, number
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_emulator import Offline72
    from .client72_scene_nested import BINDINGS, CTORS, READERS, WRITERS, CODECS, SceneNestedReader72, SceneNestedWriter72
    from .trace_client72_login import observed_fields, serializable
    from .trace_client72_scene_wait import SCALARS, CHILDREN, declarations as scene_declarations
    from .trace_client72_token import varint
    from .observed_proto72 import render, compare_fixtures
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, TypeResolver, number
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_emulator import Offline72
    from client72_scene_nested import BINDINGS, CTORS, READERS, WRITERS, CODECS, SceneNestedReader72, SceneNestedWriter72
    from trace_client72_login import observed_fields, serializable
    from trace_client72_scene_wait import SCALARS, CHILDREN, declarations as scene_declarations
    from trace_client72_token import varint
    from observed_proto72 import render, compare_fixtures


ORDER = (66040, 36137, 67761, 25866, 34133, 69612)
EXPECTED = {
    66040: {(24, 'bool', None)},
    36137: {(24, 'uint32', None), (28, 'bool', None), (32, 'uint64', None)},
    67761: {(24, 'genericinst', 35584)},
    25866: {(24, 'genericinst', 35584), (32, 'genericinst', 35584), (40, 'genericinst', 35584)},
    34133: {(24, 'string', None), (32, 'class', 36137), (40, 'string', None), (48, 'bool', None)},
    69612: {(24, 'genericinst', 35584), (32, 'class', 25866), (40, 'uint32', None)},
}
EXPECTED_TAGS = {66040: {24: 3}, 36137: {24: 2, 28: 3, 32: 1}, 67761: {24: 10},
                 25866: {24: 1, 32: 7, 40: 6}, 34133: {24: 13, 32: 1, 40: 6, 48: 15},
                 69612: {24: 13, 32: 1, 40: 11}}


def summarize(report):
    summary = {k: v for k, v in report.items() if k not in ('fixtures', 'readerFixtures')}
    summary['writerFixtureCount'] = len(report['fixtures'])
    summary['readerFixtureCount'] = len(report['readerFixtures'])
    summary['observations'] = [dict(name=row['name'], independentWire=row.get('independentWire', False), boundaryScope=row.get('boundaryScope'), expected=row['expected'],
                                    storedObject=row['result']['storedObject'],
                                    executedReaders=[hex(entry) for entry in (*READERS.values(), 0x1523856B0) if hex(entry) in row['result']['executedAddresses']],
                                    executedConstructors=[hex(entry) for entry in (*CTORS.values(), 0x152387890) if hex(entry) in row['result']['executedAddresses']],
                                    models=row['result']['models']) for row in report['readerFixtures']]
    return summary


def validate_summary(summary):
    if summary['inputSHA256'] != HASHES or summary['runtimeStatus'] != 'not-tested' or summary['targetProtocolReady'] or summary['businessSemantics'] != 'unresolved':
        raise ValueError('Nested inputs/scope or semantic status changed')
    if (summary['writerFixtureCount'], summary['readerFixtureCount'], summary['independentProtocFixtures']) != (13, 24, 13):
        raise ValueError('Incomplete native/protoc nested coverage')
    messages = {m['typeIndex']: m for m in summary['messages']}
    if set(messages) != set(ORDER): raise ValueError('Nested declaration family incomplete')
    for index, message in messages.items():
        if {f['objectOffset']: f['tag'] for f in message['fields']} != EXPECTED_TAGS[index]:
            raise ValueError('Nested field/wire binding changed')
        if any(f['semanticStatus'] != 'unresolved' or f['wireStatus'] != 'emulated-observation' for f in message['fields']):
            raise ValueError('Unknown nested semantics promoted')
    observations = summary['observations']
    names = {row['name']: row for row in observations}
    required = {f'{index}-{mode}' for index in ORDER for mode in ('nonzero', 'zero')} | {
        '36137-maximum', 'scene-all-nonempty', '66040-explicit-false', '66040-bool-two-normalized',
        '66040-known-number-wrong-wire-skipped', '36137-independent-max', '67761-packed', '67761-unpacked',
        '67761-mixed-duplicates', '34133-duplicate-child-explicit-zero', 'scene-duplicate-nested-explicit-zero',
        'INTMAX-empty-child-fixture-accepted'}
    if len(observations) != len(required) or set(names) != required: raise ValueError('Missing independent nested boundary')
    for row in observations:
        if row['storedObject'] != row['expected']: raise ValueError('Native object differs from independent inputs')
        index = row['storedObject']['typeIndex']
        entry = 0x1523856B0 if index == 46311 else READERS[index]
        constructor = 0x152387890 if index == 46311 else CTORS[index]
        if hex(entry) not in row['executedReaders'] or hex(constructor) not in row['executedConstructors'] or hex(entry) in row['models']:
            raise ValueError('Native reader/standalone constructor omitted or modeled')
    if names['36137-maximum']['storedObject']['fields']['0x20'] != 0xFFFFFFFFFFFFFFFF:
        raise ValueError('Inner uint64 maximum truncated')
    if names['scene-all-nonempty']['storedObject']['fields']['0x68'] != 0xFEDCBA9876543210:
        raise ValueError('Outer scene uint64 truncated')
    if not names['INTMAX-empty-child-fixture-accepted']['boundaryScope']:
        raise ValueError('Accepted stream fixture limit not disclosed')
    errors = {r['name']: (r['origin'], r['helper']) for r in summary['rejections']}
    if errors != {'negative-child-length': ('native-error-branch', '0x1509cafa0'),
                  'truncated-child-input': ('bounded-input-model', '0x14751fd30'),
                  'child-recursion-limit': ('native-error-branch', '0x1509cb410')}:
        raise ValueError('Native errors and explicit model rejection conflated')
    if not any('constructor dispatch' in text and 'EOF' in text for text in summary['outsideScope']):
        raise ValueError('Managed construction/EOF boundary omitted')


def declarations(binary, metadata, startup, records):
    pe = PE(binary)
    resolver = TypeResolver(pe, startup, records, pe.offset(HEADER_VA))
    literals = Literals72(binary, metadata)
    base = number(binary, pe.offset(USAGE_REGISTRATION) + 0x48, 'Q')
    byid = {row['index']: row for row in records}
    bindings = []
    for index, row, source, cell in BINDINGS:
        key = (row * 0x57C15286 + 0x26DDEBDA) & 0xFFFFFFFF
        encoded = ((number(metadata, literals.usages_base + row * 8) + 0xABBDCABF) & 0xFFFFFFFF) ^ key ^ 0x2A357D90
        slot = number(metadata, literals.usages_base + row * 8 + 4) ^ key ^ 0x1855AA65
        resolved = resolver.resolve_index(encoded & 0x1FFFFFFF)
        if encoded >> 29 != 1 or encoded & 0x1FFFFFFF != source or base + slot * 8 != cell or resolved.get('typeDefinition') != index:
            raise ValueError('Nested metadata usage binding changed')
        fields = [f for f in byid[index]['fields'] if f['offsetKind'] == 'instance']
        if {(f['offset'], f['type']['kind'], f['type'].get('typeDefinition')) for f in fields} != EXPECTED[index]:
            raise ValueError('Nested instance declaration changed')
        if any([argument['kind'] for argument in f['type']['arguments']] != ['uint32']
               for f in fields if f['type']['kind'] == 'genericinst'):
            raise ValueError('Nested repeated declaration is not uint32')
        methods = []
        for name, address, count in (('.ctor', CTORS[index], 0), ('MOMMACBEMEK', READERS[index], 1), ('CAIINKNGKDM', WRITERS[index], 1)):
            found = [m for m in byid[index]['methods'] if m['registeredAddress'] == hex(address)]
            if len(found) != 1 or found[0]['name'] != name or found[0]['parameterCount'] != count:
                raise ValueError('Nested constructor/reader/writer declaration changed')
            methods.append(found[0])
        bindings.append(dict(typeIndex=index, usageRow=row, sourceIndex=source, cell=hex(cell), type=resolved, fields=fields, methods=methods))
    return dict(bindings=bindings, scene=scene_declarations(binary, metadata, startup, records),
                initializedMetadataGuard=dict(address='0x145b8be66', value=1, scope='149B5ECE0 lazy metadata setup; IFix not enabled'))


def input_values(row, payloads, *, zero=False, maximum=False):
    values = {}
    for field in row['fields']:
        if field['offsetKind'] != 'instance':
            continue
        offset, kind = field['offset'], field['type']['kind']
        if kind == 'bool': value = 0 if zero else 1
        elif kind == 'uint32': value = 0 if zero else 0xFFFFFFFF if maximum else 0x12340000 + offset
        elif kind == 'uint64': value = 0 if zero else 0xFFFFFFFFFFFFFFFF if maximum else 0xFEDCBA9800000000 + offset
        elif kind == 'genericinst': value = ('list32', [] if zero else [0x12340000 + offset, 0x80000000 + offset])
        elif kind == 'string': value = b'' if zero else f'嵌套\U0001f319{row["index"]}_{offset}\0'.encode('utf8')
        elif kind == 'class': value = None if zero else ('message', payloads[field['type']['typeDefinition']])
        else: raise ValueError('Unbound nested fixture kind')
        values[offset] = value
    return values


def expected_object(row, values, children):
    fields = {}
    for field in row['fields']:
        if field['offsetKind'] != 'instance': continue
        offset, kind = field['offset'], field['type']['kind']
        value = values[offset]
        if kind == 'class': value = copy.deepcopy(children[field['type']['typeDefinition']]) if value is not None else None
        elif kind == 'genericinst': value = list(value[1])
        elif kind == 'string': value = value.hex()
        fields[hex(offset)] = value
    return dict(typeIndex=row['index'], fields=fields)


def length_field(tag, raw):
    return varint(tag * 8 + 2) + varint(len(raw)) + raw


def complete_scene_wire(payloads):
    parts, expected = [], {}
    for offset, tag, kind in SCALARS:
        value = 0xFEDCBA9876543210 if kind == 'uint64' else 1 if kind == 'bool' else 2 if kind == 'int32' else (0x80000000 + offset)
        parts.append(varint(tag * 8) + varint(value))
        expected[hex(offset)] = value
    items = [0, 1, 0x80000000, 0xFFFFFFFF, 1]
    parts.append(length_field(13, b''.join(varint(v) for v in items)))
    expected['0x20'] = items
    text = '入场嵌套\U0001f319\0'.encode('utf8')
    parts.append(length_field(293, text)); expected['0x38'] = text.hex()
    position = b'\x0d' + struct.pack('<f', 1.25) + b'\x15' + struct.pack('<f', -2.5) + b'\x1d' + struct.pack('<f', 3.75)
    for offset, tag, child in CHILDREN:
        parts.append(length_field(tag, position if child == 84343 else payloads[child]))
    return b''.join(parts), expected


def recover(binary, metadata, startup, records, libraries):
    declared = declarations(binary, metadata, startup, records)
    writer_engine = Offline72(binary, libraries, records, True)
    reader_engine = Offline72(binary, libraries, records, True)
    writer = SceneNestedWriter72(writer_engine)
    reader = SceneNestedReader72(reader_engine)
    fixtures, reader_fixtures, messages, payloads, children = [], [], [], {}, {}
    try:
        for index in ORDER:
            row = writer_engine.records[index]
            values = input_values(row, payloads)
            sample = writer.run(index, values)
            payloads[index] = bytes.fromhex(sample['wire'])
            expected = expected_object(row, values, children)
            parsed = reader.run_message(index, payloads[index])
            if parsed['storedObject'] != expected:
                raise ValueError('Native nested baseline differs from independent values')
            children[index] = expected
            fields = observed_fields(row, sample, {})
            messages.append(dict(typeIndex=index, clientType=row['name'], fields=fields,
                                 writerEntry=hex(WRITERS[index]), readerEntry=hex(READERS[index]),
                                 constructorEntry=hex(CTORS[index]), runtimeStatus='not-tested'))
            for label, vals in (('nonzero', values), ('zero', input_values(row, payloads, zero=True))):
                written = sample if label == 'nonzero' else writer.run(index, vals)
                result = parsed if label == 'nonzero' else reader.run_message(index, bytes.fromhex(written['wire']))
                wanted = expected_object(row, vals, children)
                if result['storedObject'] != wanted:
                    raise ValueError('Native nested zero/default differs from independent values')
                written['name'] = f'{index}-{label}'
                fixtures.append(written)
                reader_fixtures.append(dict(name=written['name'], result=result, expected=wanted))
        # The inner uint64 is a separate boundary from the outer scene uint64.
        values = input_values(writer_engine.records[36137], payloads, maximum=True)
        written = writer.run(36137, values)
        result = reader.run_message(36137, bytes.fromhex(written['wire']))
        expected = expected_object(writer_engine.records[36137], values, children)
        if result['storedObject'] != expected: raise ValueError('Nested uint64/uint32 maximum truncated')
        written['name'] = '36137-maximum'; fixtures.append(written)
        reader_fixtures.append(dict(name=written['name'], result=result, expected=expected))
        complete, scalar_expected = complete_scene_wire(payloads)
        result = reader.run_message(46311, complete)
        expected = dict(typeIndex=46311, fields=scalar_expected)
        position = dict(typeIndex=84343, fields={'0x18': 0x3FA00000, '0x1c': 0x40700000, '0x20': 0xC0200000})
        for offset, _, child in CHILDREN:
            expected['fields'][hex(offset)] = copy.deepcopy(position if child == 84343 else children[child])
        if result['storedObject'] != expected: raise ValueError('Complete scene nested graph differs from independent inputs')
        reader_fixtures.append(dict(name='scene-all-nonempty', result=result, expected=expected))
        # Independently hand-built wire: this is not native writer round-trip input.
        independent = [
            ('66040-explicit-false', 66040, bytes.fromhex('1800'), dict(typeIndex=66040, fields={'0x18': 0})),
            ('66040-bool-two-normalized', 66040, bytes.fromhex('1802'), dict(typeIndex=66040, fields={'0x18': 1})),
            ('66040-known-number-wrong-wire-skipped', 66040, bytes.fromhex('1dffffffff'), dict(typeIndex=66040, fields={'0x18': 0})),
            ('36137-independent-max', 36137, bytes.fromhex('08ffffffffffffffffff0110ffffffff0f1801'),
             dict(typeIndex=36137, fields={'0x18': 0xFFFFFFFF, '0x1c': 1, '0x20': 0xFFFFFFFFFFFFFFFF})),
        ]
        items = [1, 0x80000000, 0xFFFFFFFF, 1]
        packed = length_field(10, b''.join(varint(v) for v in items))
        unpacked = b''.join(b'\x50' + varint(v) for v in items)
        wanted = dict(typeIndex=67761, fields={'0x18': items})
        independent.extend((('67761-packed', 67761, packed, wanted), ('67761-unpacked', 67761, unpacked, wanted),
                            ('67761-mixed-duplicates', 67761, packed + unpacked, dict(typeIndex=67761, fields={'0x18': items * 2}))))
        leaf_a = b'\x08' + varint(0xFEDCBA9876543210) + b'\x10\x7b'
        leaf_b = bytes.fromhex('10001801')
        merged = dict(typeIndex=34133, fields={'0x18': '', '0x20': dict(typeIndex=36137,
                      fields={'0x18': 0, '0x1c': 1, '0x20': 0xFEDCBA9876543210}), '0x28': '', '0x30': 0})
        independent.append(('34133-duplicate-child-explicit-zero', 34133, length_field(1, leaf_a) + length_field(1, leaf_b), merged))
        for name, index, wire, wanted in independent:
            parsed = reader.run_message(index, wire)
            if parsed['storedObject'] != wanted: raise ValueError('Independent nested wire expectation mismatch: ' + name)
            reader_fixtures.append(dict(name=name, independentWire=True, result=parsed, expected=wanted))
        # A repeated outer message merges into the existing object and its child.
        suffix = length_field(1357, length_field(1, leaf_b) + length_field(6, b'') + b'\x78\x00')
        wanted = copy.deepcopy(expected)
        wanted['fields']['0x40']['fields']['0x20']['fields']['0x18'] = 0
        wanted['fields']['0x40']['fields']['0x28'] = ''
        wanted['fields']['0x40']['fields']['0x30'] = 0
        parsed = reader.run_message(46311, complete + suffix)
        if parsed['storedObject'] != wanted: raise ValueError('Scene duplicate nested content did not merge')
        reader_fixtures.append(dict(name='scene-duplicate-nested-explicit-zero', independentWire=True, result=parsed, expected=wanted))
        oversized = b'\x0a' + varint(0x7FFFFFFF)
        parsed = reader.run_message(34133, oversized)
        wanted = dict(typeIndex=34133, fields={'0x18': '', '0x20': dict(typeIndex=36137,
                      fields={'0x18': 0, '0x1c': 0, '0x20': 0}), '0x28': '', '0x30': 0})
        if parsed['storedObject'] != wanted: raise ValueError('Large declared empty-child fixture changed')
        reader_fixtures.append(dict(name='INTMAX-empty-child-fixture-accepted', independentWire=True,
                                    boundaryScope='accepted only under this explicit stream/EOF model; physical refill/EOF ABI unverified',
                                    result=parsed, expected=wanted))
        rejections = []
        errors = [('negative-child-length', b'\x0a\xff\xff\xff\xff\x0f', 100, 'negative-length', '0x1509cafa0', 'native-error-branch'),
                  ('truncated-child-input', b'\x0a\x02\x08', 100, 'Truncated or invalid length', '0x14751fd30', 'bounded-input-model'),
                  ('child-recursion-limit', length_field(1, b'\x08\x01'), 0, 'recursion-limit', '0x1509cb410', 'native-error-branch')]
        for name, wire, limit, diagnostic, helper, origin in errors:
            try:
                reader.run_message(34133, wire, recursion_limit=limit)
            except ValueError as error:
                if diagnostic not in str(error) or int(helper, 16) not in reader.address_counts:
                    raise ValueError('Wrong nested error path: ' + name) from error
                rejections.append(dict(name=name, wire=wire.hex(), error=str(error), helper=helper,
                                       origin=origin, helperIsModel=True, nativeInstructions=reader_engine.steps))
            else:
                raise ValueError('Malformed nested input accepted: ' + name)
        native = {}
        for address in (*CTORS.values(), *READERS.values(), *WRITERS.values(), *(entry for entry, _ in CODECS)):
            end = reader_engine.ends[address]
            raw = binary[reader_engine.pe.offset(address, end - address, executable=True):][:end - address]
            native[hex(address)] = dict(size=len(raw), sha256=hashlib.sha256(raw).hexdigest())
        return dict(schemaVersion=1, inputSHA256=HASHES, declarations=declared, nativeCode=native,
                    fixtures=serializable(fixtures), readerFixtures=reader_fixtures, messages=messages, rejections=rejections,
                    separatelyExecutedConstructorDefaults={str(i): reader.constructor_defaults[i] for i in ORDER},
                    runtimeStatus='not-tested', targetProtocolReady=False, businessSemantics='unresolved',
                    models=['initialized metadata/classes and constructor reflection; lazy metadata guard supplied initialized',
                            'managed allocation, UTF-8 string conversion and bounded array storage',
                            'parent child-allocation storage initialized from separately executed constructor defaults; protected-parent constructor dispatch not recovered',
                            'writer string/message bytes and repeated codec emission; native message selection/tag/writer executes',
                            'native ReadMessage/limits/constructors/readers/uint32 repeated bodies execute'],
                    outsideScope=['business field names', 'IFix bodies and real managed ABI',
                                  'protected-parent constructor dispatch and physical stream refill/EOF ABI; INTMAX empty-child fixture accepted, not a production safety conclusion',
                                  'network/SDK/media/full scene entry/D-02'])
    finally:
        reader.close(); writer_engine.close(); reader_engine.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'summary-output', 'proto-output', 'protoc'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    outputs = (args.output, args.summary_output, args.proto_output)
    if len({p.resolve() for p in outputs}) != 3 or any(p.resolve().is_relative_to(args.client.resolve()) for p in outputs):
        parser.error('Distinct research outputs outside client required')
    binary, metadata, startup = [read_verified(args.client / name, sha) for name, sha in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report = recover(binary, metadata, startup, records, args.libraries)
    for path in outputs: path.parent.mkdir(parents=True, exist_ok=True)
    args.proto_output.write_text(render(report['messages']), 'utf8')
    report['independentProtocFixtures'] = compare_fixtures(args.protoc.resolve(), args.proto_output.resolve(), report, report)
    report['protocSHA256'] = hashlib.sha256(args.protoc.read_bytes()).hexdigest()
    report['toolSourceSHA256'] = {name: hashlib.sha256((Path(__file__).resolve().parents[1] / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                                 for name in ('tools/trace_client72_scene_nested.py', 'tools/client72_scene_nested.py', 'tools/client72_scene_reader.py')}
    summary = summarize(report)
    validate_summary(summary)
    for path, document in ((args.output, report), (args.summary_output, summary)):
        path.write_text(json.dumps(document, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(messages=len(report['messages']), fields=sum(len(m['fields']) for m in report['messages']),
                          writerFixtures=len(report['fixtures']), readerFixtures=len(report['readerFixtures']),
                          independentProtocFixtures=report['independentProtocFixtures'])))


if __name__ == '__main__': main()
