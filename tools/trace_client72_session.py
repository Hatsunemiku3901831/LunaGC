"""Bounded native seed suffix versus the existing Java server MT64 implementation.

Starts at the declared decrypted-byte-array boundary, not Handler entry.
No RSA/Shell/IFix success is invented, and no server/client process is started.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72
    from .client72_session_runtime import SessionRuntime72
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72
    from client72_session_runtime import SessionRuntime72

SEED_CASES = (
    ('zero', bytes(8), 0),
    ('high64', bytes.fromhex('fedcba9876543210'), 0x0123456789abcdef),
    ('msb', bytes.fromhex('8000000000000000'), 0),
    ('client-msb', bytes.fromhex('0000000000000001'), 0x8000000000000000),
    ('mixed', bytes.fromhex('0102030405060708'), 0x1020304050607080),
    ('trailing-byte', bytes.fromhex('0102030405060708ff'), 0x1020304050607080),
)


def java_keys(java_home, seeds, root):
    javac, java = (java_home / 'bin' / f'{name}.exe' for name in ('javac','java'))
    sources = [root / 'tools/Client72SeedReference.java',
               root / 'src/main/java/emu/grasscutter/utils/algorithms/MersenneTwister64.java']
    cache = root / 'local/migration72/analysis'
    cache.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='session-oracle-', dir=cache) as directory:
        subprocess.run([str(javac), '-d', directory, *map(str,sources)], check=True, capture_output=True)
        result = subprocess.run([str(java), '-cp', directory, 'Client72SeedReference',
                                 *[f'{seed:x}' for seed in seeds]], check=True, capture_output=True, text=True)
    keys = [bytes.fromhex(line) for line in result.stdout.splitlines()]
    if len(keys) != len(seeds) or any(len(key) != 4096 for key in keys):
        raise ValueError('Independent Java reference returned unexpected key count/length')
    return keys, {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}


def recover(binary, records, libraries, java_home, root):
    seeds = [int.from_bytes(data[:8],'big') ^ client for _,data,client in SEED_CASES]
    reference, source_hashes = java_keys(java_home, seeds, root)
    fixtures = [];source_fixtures=[]
    for multiplier,shift,random_value in ((1,0,0x12345678),(197,41,0x7ffffffe)):
        e = Offline72(binary, libraries, records, True)
        try:
            e.prepare()
            runtime = SessionRuntime72(e, multiplier, shift, random_value)
            for sample in (0.0,2**-53,0.5,0.123456789,math.nextafter(1.0,0.0)):
                source_fixtures.append(dict(permutation=[multiplier,shift],**runtime.generate_client_seed(sample)))
            for (name,data,client),expected in zip(SEED_CASES,reference):
                trace,key = runtime.seed_suffix(data,client)
                if key != expected:
                    raise ValueError('Native session key differs from independent server MT64')
                fixtures.append(dict(name=name, permutation=[multiplier,shift], randomValue=random_value,
                                     decryptedInputHex=data.hex(), clientSeedHex=f'{client:016x}',
                                     keySHA256=hashlib.sha256(key).hexdigest(), javaByteComparison=True,
                                     trace=trace, passed=True))
            for length in (0,1,7):
                trace,key = runtime.seed_suffix(bytes(range(length)),0x8000000000000001)
                if key is not None:
                    raise ValueError('Short input unexpectedly produced a session key')
                fixtures.append(dict(name=f'reject-length-{length}', permutation=[multiplier,shift],
                                     trace=trace, passed=True))
        finally:
            e.close()
    return dict(schemaVersion=1, inputSHA256=HASHES, fixtureCount=len(fixtures), fixtures=fixtures,
                clientSeedSourceFixtures=source_fixtures,
                javaSourceSHA256=source_hashes, runtimeStatus='not-tested',
                scope='native client-seed source and post-decryption suffix; encrypted string construction excluded',
                observedAlgorithm='big-endian first 8 decrypted bytes XOR uint64 client seed; MT64 first output reseeds a second MT64; discard one output; 512 big-endian uint64 outputs',
                boundaries=dict(entry='0x15a4e064f', stop='0x15a4e0d4b',
                                outsideScope=['RSA decryption/signature verification','Shell warmup','IFix','full Token Handler','real client']),
                models=['managed object/array allocation with class and size allowlist',
                        'thread-local System.Random object with controlled Next value',
                        '16x256 bijective runtime storage table; native encoder/decoder execute'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path)
    p.add_argument('--java-home',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    binary,metadata,startup=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(binary,metadata,startup=startup,structures=True)
    report=recover(binary,records,a.libraries,a.java_home,Path(__file__).resolve().parents[1])
    summary={k:v for k,v in report.items() if k!='fixtures'}
    summary['comparisons']=[{k:v for k,v in f.items() if k!='trace'} for f in report['fixtures']]
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],clientSeedSources=len(report['clientSeedSourceFixtures']),javaComparisons=12,nativeRejections=6)))


if __name__=='__main__':main()
