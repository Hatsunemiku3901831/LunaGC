"""Compile leaf research Proto and compare with native writer fixtures."""
import argparse
import json
from pathlib import Path
import subprocess
try:
    from .extract_client72 import HASHES
    from .observed_proto72 import compare_fixtures
except ImportError:
    from extract_client72 import HASHES
    from observed_proto72 import compare_fixtures


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--protoc', required=True, type=Path); p.add_argument('--trace', required=True, type=Path)
    p.add_argument('--directory', type=Path, default=Path(__file__).resolve().parents[1] / 'protocol/cn-7.2'); a = p.parse_args()
    mapping = json.loads((a.directory / 'response-leaf-wire-observations.json').read_text('utf8'))
    trace = json.loads(a.trace.read_text('utf8')); proto = (a.directory / 'response-leaves.proto').resolve()
    if mapping['inputSHA256'] != HASHES or trace['inputSHA256'] != HASHES: raise ValueError('Wrong target')
    count = compare_fixtures(a.protoc.resolve(), proto, mapping, trace)
    oracles = [('GHANJBIKPDH', 'PGEMKKCNEMJ: -1 EMBPDEHFOJK: 300', '6dffffffff7d2c010000'),
               ('NNKFJMDCCMB', 'JJLJMLEJIIK: 300 EBMBKOOKKPD: 1', '08ac021001')]
    for name, value, wire in oracles:
        r = subprocess.run([str(a.protoc.resolve()), '--proto_path='+str(proto.parent), '--encode=cn72.research.'+name, str(proto)],
                           input=value.encode('ascii'), capture_output=True, check=True)
        if r.stdout.hex() != wire: raise ValueError('Independent leaf wire oracle failed')
    print(json.dumps(dict(nativeComparisons=count, independentWireAssertions=len(oracles), runtimeStatus='not-tested')))


if __name__ == '__main__': main()
