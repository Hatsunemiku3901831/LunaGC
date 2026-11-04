"""Compare protoc output with protected-code observations, not schema roundtrips."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

try:
    from .extract_client72 import HASHES
    from .trace_client72_token import fixture_values, TYPE_NAME
except ImportError:
    from extract_client72 import HASHES
    from trace_client72_token import fixture_values, TYPE_NAME


def check(protoc, trace, mapping, proto):
    if trace['inputSHA256'] != HASHES or mapping['inputSHA256'] != HASHES:
        raise ValueError('Trace input does not match fixed target')
    if {r['case'] for r in trace['fixtures']} != {'nonzero', 'boundary', 'zero'} or len(trace['fixtures']) != 3:
        raise ValueError('Missing or duplicate controlled fixtures')
    def encode(text):
        return subprocess.run([str(protoc), '--proto_path=' + str(proto.parent), '--encode=cn72.research.' + TYPE_NAME,
                               str(proto)], input=text.encode('ascii'), check=True, capture_output=True).stdout
    with tempfile.TemporaryDirectory() as directory:
        subprocess.run([str(protoc), '--proto_path=' + str(proto.parent),
                        '--descriptor_set_out=' + str(Path(directory) / 'token.pb'), str(proto)], check=True, capture_output=True)
    fields = [dict(name=f['clientField'], offset=f['objectOffset'], offsetKind='instance', type={'kind': f['type']}) for f in mapping['fields']]
    for fixture in trace['fixtures']:
        values = fixture_values(fields, fixture['case'])
        text = []
        for field in fields:
            value = values[field['offset']]
            if isinstance(value, bytes):
                value = json.dumps(value.decode('ascii'))
            elif field['type']['kind'] == 'bool':
                value = 'true' if value else 'false'
            text.append(f"{field['name']}: {value}")
        if encode('\n'.join(text)).hex() != fixture['wire']:
            raise ValueError(f"protoc disagrees with protected execution: {fixture['case']}")
    # Independent selected-field assertion: native key 1, 623, 1184; no encoder-generated oracle.
    expected = bytes.fromhex('0a024142 fa260473656564 804a04')
    if encode('CANLDLBEODF: "AB" JFCLDBKLKDF: "seed" FMMLBHKFGDH: 4') != expected:
        raise ValueError('Independent selected-field wire assertion failed')
    return dict(compiled=True, observationComparisons=3, independentWireAssertions=1, runtimeStatus='not-tested')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--protoc', type=Path, required=True)
    parser.add_argument('--trace', type=Path, required=True)
    parser.add_argument('--directory', type=Path, default=Path(__file__).resolve().parents[1] / 'protocol/cn-7.2')
    args = parser.parse_args()
    trace = json.loads(args.trace.read_text('utf8'))
    mapping = json.loads((args.directory / 'token-wire-observations.json').read_text('utf8'))
    print(json.dumps(check(args.protoc.resolve(), trace, mapping, (args.directory / 'protected-token.proto').resolve()), indent=2))


if __name__ == '__main__':
    main()
