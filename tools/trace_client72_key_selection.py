"""Tie native Token tag1184 to the response key selector, with wrong-key controls.

The named dispatch URL argument is independent corroboration. Its two bounded
fragments stop at standard formatter/Concat calls; the full URL is not run.
JCA builds response fixtures from the observed request selector. Original client
seed ciphertext still targets the stock server key, so this is not a complete
local-server request decryption or live SDK/HTTP/login test.
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
    from .client72_emulator import Offline72, STACK
    from .client72_session_runtime import fragment
    from .client72_response_crypto import CryptoResponseReader72, ResponseCrypto72, SUCCESS, REJECT
    from .client72_rsa_fixture import private_numbers, tlv, encrypt_seed, sign_sha256
    from .client72_seed_crypto import rsa_public_numbers
    from .trace_client72_token_complete import construct_native_token
    from .trace_client72_token_constructor import TokenConstructor72
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire
    from .trace_client72_response_crypto import discover_private_key, response_wire
    from .trace_client72_handler_core import declarations as formatter_declarations
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, STACK
    from client72_session_runtime import fragment
    from client72_response_crypto import CryptoResponseReader72, ResponseCrypto72, SUCCESS, REJECT
    from client72_rsa_fixture import private_numbers, tlv, encrypt_seed, sign_sha256
    from client72_seed_crypto import rsa_public_numbers
    from trace_client72_token_complete import construct_native_token
    from trace_client72_token_constructor import TokenConstructor72
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire
    from trace_client72_response_crypto import discover_private_key, response_wire
    from trace_client72_handler_core import declarations as formatter_declarations


def recover(binary, metadata, startup, records, libraries, signer_path, java_home, root):
    named = formatter_declarations(binary, metadata, startup, records)
    for owner, address, name, count in ((12832, '0x1531e0a10', 'GetGlobalDispatchUrl', 2),
                                       (293, '0x159a8e9d0', 'Concat', 3)):
        method = next(m for r in records if r['index'] == owner for m in r['methods'] if m['registeredAddress'] == address)
        if (method['name'], method['parameterCount']) != (name, count):
            raise ValueError('Named URL/Concat method changed')
    requests = []; url = None
    for variant, sample in enumerate((0.0, 0.5, math.nextafter(1.0, 0.0))):
        e = Offline72(binary, libraries, records, True); ctor = None
        try:
            e.prepare(); ctor = TokenConstructor72(e, binary, metadata, variant, '456')
            if variant == 0:
                url = trace_url_argument(e, ctor, named)
            crypto, _ = construct_native_token(e, ctor, binary, metadata, sample, True)
            writer = Writer72(e)
            wire = writer.run(82491, ctor.values(82491, ctor.request, writer))['wire']
            decoded = {tag: (kind, value) for tag, kind, value in parse_wire(bytes.fromhex(wire))}
            selector = e.read(ctor.request + 0x98, 'I')
            if selector != 4 or decoded.get(1184) != (0, selector) or b'\x80\x4a\x04' not in bytes.fromhex(wire):
                raise ValueError('Native request selector did not reach independent tag1184 wire')
            requests.append(dict(variant=variant,selector=selector,selectorWire='804a04',
                                 clientSeedHex=crypto['seedHex'],nativeSeed=crypto,requestWire=wire,passed=True))
        finally:
            if ctor: ctor.close()
            e.close()
    private_der, _, discovery = discover_private_key(binary, metadata, records, libraries)
    client_key = private_numbers(private_der); signer = private_numbers(signer_path.read_bytes())
    public_keys = {}; key_files = {}
    for path in sorted((root / 'src/main/resources/keys/game_keys').glob('*_Pub.der')):
        raw = path.read_bytes(); _, body, _ = tlv(raw); _, _, at = tlv(body); tag, value, _ = tlv(body, at)
        if tag != 3 or value[0] != 0: raise ValueError('Unexpected public-key wrapper')
        key_id = int(path.stem.split('_')[0]); n, exponent = rsa_public_numbers(value[1:])
        public_keys[key_id] = [0, n, exponent]
        key_files[key_id] = dict(path=path.relative_to(root).as_posix(),sha256=hashlib.sha256(raw).hexdigest(),
                                 matchesNativePrivate=(n, exponent) == tuple(client_key[1:3]))
    if [key for key in public_keys if key_files[key]['matchesNativePrivate']] != [4]:
        raise ValueError('Native private key does not uniquely match selector4')
    # Each success uses the actual request value; controls deliberately substitute other available IDs.
    cases = [(f'selected-{i}', i, r['selector'], server) for i, (r, server) in
             enumerate(zip(requests, (1, 0xfedcba9876543210, 0xffffffffffffffff)))]
    cases += [(f'wrong-key-{key}', 1, key, 0xfedcba9876543210) for key in public_keys if key != 4]
    java, source_hashes = java_responses(java_home, root, signer_path, cases, requests)
    fixtures = []
    for (name, request_index, key_id, server_seed), (actual_id, plain, cipher, signature, reference) in zip(cases, java):
        client_seed = int(requests[request_index]['clientSeedHex'], 16)
        expected_plain = (client_seed ^ server_seed).to_bytes(8, 'big')
        if (actual_id, plain, cipher, signature) != (key_id, expected_plain, encrypt_seed(public_keys[key_id], expected_plain), sign_sha256(signer, expected_plain)):
            raise ValueError('Independent Java and Python response fixtures disagree')
        e = Offline72(binary, libraries, records, True); reader = None; runtime = None
        try:
            reader = CryptoResponseReader72(e); read = reader.run(37481, response_wire(cipher, signature))
            runtime = ResponseCrypto72(e, binary, metadata, client_seed, signer)
            trace, session_key = runtime.run_response(); selected = key_id == requests[request_index]['selector']
            expected_operations = {101: 0 if selected else -7, 103: 0 if selected else -7}
            if runtime.operations != expected_operations or runtime.stop != (SUCCESS if selected else REJECT):
                raise ValueError('Native key-selection success/rejection differs')
            if selected:
                if runtime.decrypted != plain or session_key != reference:
                    raise ValueError('Native decrypted seed/session key differs from Java server oracle')
            elif runtime.decrypted != b'' or session_key is not None:
                raise ValueError('Wrong key installed a session key or yielded plaintext')
            fixtures.append(dict(name=name,requestIndex=request_index,selectedKeyId=key_id,
                                 matchesRequest=selected,clientSeedHex=f'{client_seed:016x}',serverSeedHex=f'{server_seed:016x}',
                                 plaintextHex=plain.hex(),nativeOperations=runtime.operations,stop=hex(runtime.stop),
                                 keyChecks=runtime.key_checks,sessionKeyInstalled=session_key is not None,
                                 sessionKeySHA256=hashlib.sha256(session_key).hexdigest() if session_key else None,
                                 javaByteComparison=selected,reader=read,trace=trace,passed=True))
            print(json.dumps(dict(case=name,selectedKeyId=key_id,sessionKeyInstalled=session_key is not None)), flush=True)
        finally:
            if runtime: runtime.close()
            if reader: reader.close()
            e.close()
    field = next(f for r in records if r['index'] == 82491 for f in r['fields'] if f.get('offsetKind') == 'instance' and f['offset'] == 0x98)
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                requests=requests,results=[{k:v for k,v in f.items() if k not in ('reader','trace')} for f in fixtures],
                fieldBinding=dict(typeIndex=82491,clientField=field['name'],objectOffset=0x98,tag=1184,type='uint32',
                                  semanticField='key_id',status='verified',nativeValue=4,store='0x151220094'),
                semanticBasis='Contract inference: native request selector matches independently named dispatch key_id and only interoperable response public key',
                urlArgument=url,publicKeyFiles=key_files,javaSourceSHA256=source_hashes,
                nativePrivateDerSHA256=hashlib.sha256(private_der).hexdigest(),materialDiscovery=discovery,
                runtimeStatus='not-tested',wholeExchangeExecuted=False,keyVerificationForced=False,
                trustInputReplacements=['native private-key signature','server public key and signature','root PEM input'],
                modelsFrom=['token-complete-observations.json','response-crypto-observations.json'],
                outsideScope=['full GetGlobalDispatchUrl body/HTTP','original-server valid signature','local-server request seed decryption',
                              'SDK initialization and production trust patch','whole Handler/frame/socket','real-client acceptance'])


def trace_url_argument(e, ctor, named):
    e.allow(0x1531E0A10)
    number_trace = fragment(e, 0x1531E0D76, {0x159C1ABD0}, dict(rsp=STACK+0x100008))
    if (e.reg('rcx'), e.reg('edx'), e.reg('r8')) != (0, 4, ctor.provider):
        raise ValueError('Named URL key argument is not UInt32 constant4')
    prefix = ctor.string('fixture://dispatch?value=1'); formatted = ctor.string('4')
    concat_trace = fragment(e, 0x1531E0D8A, {0x159A8E9D0}, dict(rsp=STACK+0x100008,rsi=prefix,rax=formatted))
    literal = ctor.loaded_literals[0x145B42F08]
    if (literal['index'], literal['value'], ctor.text(e.reg('rdx'))) != (61981, '&key_id=', '&key_id='):
        raise ValueError('Native URL selector name changed')
    if (e.reg('rcx'), e.reg('r8')) != (prefix, formatted): raise ValueError('Concat arguments changed')
    return dict(method='Miscs.GetGlobalDispatchUrl',entry='0x1531e0a10',literal=literal,value=4,
                formatterMethodIndex=named['formatterMethodIndex'],formatKind=named['formatKind'],
                formatterExecuted=False,concatExecuted=False,formattedStringFixture='4',
                numberTrace=number_trace,concatTrace=concat_trace)


def java_responses(java_home, root, signing_key, cases, requests):
    sources = [root/'tools/Client72KeySelectionReference.java',root/'src/main/java/emu/grasscutter/utils/algorithms/MersenneTwister64.java']
    contract = [root/'src/main/java/emu/grasscutter/utils/Crypto.java',root/'src/main/java/emu/grasscutter/server/packet/recv/HandlerGetPlayerTokenReq.java']
    args = [str(root/'src/main/resources/keys/game_keys'),str(signing_key.resolve())]
    for _, index, key_id, server in cases: args.extend((str(key_id),requests[index]['clientSeedHex'],f'{server:x}'))
    with tempfile.TemporaryDirectory(prefix='key-selection-',dir=root/'local/migration72/analysis') as cache:
        subprocess.run([str(java_home/'bin/javac.exe'),'-d',cache,*map(str,sources)],check=True,capture_output=True)
        result = subprocess.run([str(java_home/'bin/java.exe'),'-cp',cache,'Client72KeySelectionReference',*args],check=True,capture_output=True,text=True)
    rows = []
    for line in result.stdout.splitlines():
        key_id, *values = line.split(); rows.append((int(key_id), *(bytes.fromhex(v) for v in values)))
    if len(rows) != len(cases) or any(len(r) != 5 or len(r[4]) != 4096 for r in rows):
        raise ValueError('Java reference returned unexpected fixture count/shape')
    return rows, {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources+contract}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--java-home',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path);p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    p.add_argument('--signing-key',type=Path,default=Path('src/main/resources/keys/SigningKey.der'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,a.signing_key,a.java_home,Path(__file__).resolve().parents[1])
    summary={k:v for k,v in report.items() if k not in ('fixtures','requests','materialDiscovery')}
    summary['requestResults']=[{k:v for k,v in r.items() if k not in ('nativeSeed','requestWire')} for r in report['requests']]
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],field=report['fieldBinding'])))


if __name__=='__main__':main()
