"""Recover seven response leaf messages with real native writers AND readers."""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72
    from .trace_client72_login import Writer72, scalar_values, serializable
    from .trace_client72_responses import Reader72, bind_fields
    from .observed_proto72 import render
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72
    from trace_client72_login import Writer72, scalar_values, serializable
    from trace_client72_responses import Reader72, bind_fields
    from observed_proto72 import render

LEAVES = (85772, 81270, 81413, 44443, 45196, 56599, 67060)


def expected_values(row, values):
    expected = {}
    for f in row['fields']:
        if f['offsetKind'] != 'instance': continue
        value = values[f['offset']]; kind = f['type']['kind']
        if not value or isinstance(value, tuple) and not value[1]: continue
        if kind == 'string': value = dict(kind='string', hex=value.hex())
        elif kind == 'class': value = dict(kind='bytes', hex=value[1].hex())
        else: value &= (1 << (64 if kind == 'uint64' else 32)) - 1
        expected[f['offset']] = value
    return expected


def recover(engine):
    writer = Writer72(engine); reader = Reader72(engine)
    # Execute real fixed32 encode/decode, model only bounded single-byte input.
    engine.allow(0x151766CA0); engine.allow(0x147521000)
    def raw_byte():
        reader.sync(); value = reader.input.take(1)[0]; reader.position(); return value
    engine.models[0x14751FD30] = raw_byte
    observations, writers, readers = [], [], []
    try:
        for index in LEAVES:
            row = engine.records[index]; baseline = scalar_values(row)
            variations = [baseline]
            for f in row['fields']:
                if f['offsetKind'] == 'instance' and f['type']['kind'] == 'bool': variations.append(baseline | {f['offset']: 1})
            variations.append(scalar_values(row, True))
            variations.append({off: (value[0], b'') if isinstance(value, tuple) else b'' if isinstance(value, bytes) else 0
                               for off, value in baseline.items()})
            entry = next(int(m['registeredAddress'], 16) for m in row['methods'] if m['name'] == 'MOMMACBEMEK')
            samples = []
            for values in variations:
                written = writer.run(index, values); writers.append(written)
                parsed = reader.run(index, bytes.fromhex(written['wire']), entry=entry)
                if {e['objectOffset']: e['value'] for e in parsed['events']} != expected_values(row, values):
                    raise ValueError('Native leaf reader differs from controlled object input')
                samples.append(parsed); readers.append(parsed)
            fields = bind_fields(row, samples)
            for f in fields: f['wireType'] = f['acceptedWireTypes'][0]
            observations.append(dict(typeIndex=index, clientType=row['name'], fields=fields,
                                     readerEntry=hex(entry), runtimeStatus='not-tested',
                                     writerEntry=next(m['registeredAddress'] for m in row['methods'] if m['name'] == 'CAIINKNGKDM')))
        # Signed FIXED32 (not varint int32): independent native wire input.
        sample = reader.run(44443, bytes.fromhex('6dffffffff7d2c010000'), entry=0x14824FBB0)
        if {e['objectOffset']: e['value'] for e in sample['events']} != {28: 4294967295, 24: 300}:
            raise ValueError('Independent sfixed32 reader oracle failed')
        sample['independentOracle'] = True; readers.append(sample)
        return observations, writers, readers
    finally: reader.close()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', required=True, type=Path); p.add_argument('--output', required=True, type=Path)
    p.add_argument('--mapping-output', required=True, type=Path); p.add_argument('--proto-output', required=True, type=Path)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    root = a.client.resolve(); outputs = [a.output.resolve(), a.mapping_output.resolve(), a.proto_output.resolve()]
    if len(set(outputs)) != 3 or any(v.is_relative_to(root) for v in outputs): p.error('Distinct outputs outside client required')
    b, m, s = [read_verified(root / n, h) for n, h in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    engine = Offline72(b, a.python_libs, records, trace_instructions=False)
    try: messages, fixtures, readers = recover(engine)
    finally: engine.close()
    context = dict(schemaVersion=1, inputSHA256=HASHES, runtimeStatus='not-tested',
                   limitations=['Standalone leaf routines; parent container integration not observed', 'ASCII strings', 'Business semantics unknown'])
    for path, content in zip(outputs, (dict(context, fixtures=serializable(fixtures), readerFixtures=readers), dict(context, messages=messages))):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(content, indent=2)+'\n', 'utf8')
    outputs[2].parent.mkdir(parents=True, exist_ok=True); outputs[2].write_text(render(messages), 'utf8')
    print(json.dumps(dict(messages=len(messages), fields=sum(len(m['fields']) for m in messages),
                         nativeWriterFixtures=len(fixtures), nativeReaderFixtures=len(readers))))


if __name__ == '__main__': main()
