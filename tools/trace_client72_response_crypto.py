"""Replay Token-response wire through native RSA/SHA and session-key install.

Positive cases use an explicitly declared local signed-key/root fixture. Stock
keys are also exercised with an invalid response signature. No verification
decision is modeled, forced, or treated as live-client acceptance.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES,read_verified,extract
    from .client72_emulator import Offline72,OBJECT,GLOBAL
    from .client72_response_crypto import CryptoResponseReader72,ResponseCrypto72,DECRYPT,SUCCESS,REJECT,SHORT
    from .client72_rsa_fixture import private_numbers,public_der,public_pem,sign_sha256,encrypt_seed,tlv
    from .client72_seed_crypto import rsa_public_numbers,verify_key_signature
    from .trace_client72_session import java_keys
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES,read_verified,extract
    from client72_emulator import Offline72,OBJECT,GLOBAL
    from client72_response_crypto import CryptoResponseReader72,ResponseCrypto72,DECRYPT,SUCCESS,REJECT,SHORT
    from client72_rsa_fixture import private_numbers,public_der,public_pem,sign_sha256,encrypt_seed,tlv
    from client72_seed_crypto import rsa_public_numbers,verify_key_signature
    from trace_client72_session import java_keys
    from trace_client72_token import varint


def response_wire(cipher,signature):
    cipher_text=base64.b64encode(cipher);signature_text=base64.b64encode(signature)
    # Independent tags 477/208, both wire type2. Native reader performs stores.
    return b'\xea\x1d'+varint(len(cipher_text))+cipher_text+b'\x82\x0d'+varint(len(signature_text))+signature_text


def discover_private_key(binary,metadata,records,libraries):
    e=Offline72(binary,libraries,records,True);reader=None;runtime=None
    try:
        reader=CryptoResponseReader72(e);reader.run(37481,response_wire(bytes(256),bytes(256)))
        runtime=ResponseCrypto72(e,binary,metadata,0,discover=True)
        material_trace,_=runtime.run_response()
        if runtime.stop!=DECRYPT:raise ValueError('Native key material discovery missed its boundary')
        private_der,private_signature=runtime.original_keys[101]
    finally:
        if runtime:runtime.close()
        if reader:reader.close()
        e.close()
    return private_der,private_signature,material_trace


def recover(binary,metadata,records,libraries,signer,java_home,root):
    fields=[];response=next(r for r in records if r['index']==37481)
    for off,tag,name in ((0x98,477,'server_rand_key'),(0x88,208,'sign')):
        field=next(f for f in response['fields'] if f.get('offsetKind')=='instance' and f['offset']==off)
        if field['type']['kind']!='string':raise ValueError('Response crypto field is not string')
        fields.append(dict(typeIndex=37481,clientType=response['name'],clientField=field['name'],
                           objectOffset=off,tag=tag,type='string',semanticField=name,status='verified',
                           scope='native-wire-to-crypto-under-declared-trust-inputs'))
    private_der,private_signature,material_trace=discover_private_key(binary,metadata,records,libraries)
    client_key=private_numbers(private_der)
    if client_key[1].bit_length()!=2048 or signer[1].bit_length()!=2048:raise ValueError('Expected 2048-bit fixture keys')
    public_matches=[]
    for path in sorted((root/'src/main/resources/keys/game_keys').glob('*_Pub.der')):
        _,body,_=tlv(path.read_bytes());_,_,at=tlv(body);tag,value,_=tlv(body,at)
        if tag!=3 or value[0]!=0:raise ValueError('Unexpected repository public-key wrapper')
        if rsa_public_numbers(value[1:])==tuple(client_key[1:3]):public_matches.append(path.relative_to(root).as_posix())
    if public_matches!=['src/main/resources/keys/game_keys/4_Pub.der']:raise ValueError('Client decryption public key association changed')
    cases=[('local-zero',bytes(8),0,'local',None),
           ('local-high64',bytes.fromhex('fedcba9876543210'),0x0123456789abcdef,'local',None),
           ('local-trailing',bytes.fromhex('0102030405060708ff'),0xffffffffffffffff,'local',None),
           ('stock-invalid-signature',bytes.fromhex('fedcba9876543210'),1,'stock','body-signature'),
           ('local-invalid-signature',bytes.fromhex('fedcba9876543210'),1,'local','body-signature'),
           ('local-invalid-key-signature',bytes.fromhex('fedcba9876543210'),1,'local','key-signature'),
           ('local-invalid-cipher',bytes.fromhex('fedcba9876543210'),1,'local','cipher'),
           ('local-short-seed',bytes.fromhex('01020304050607'),1,'local',None)]
    reference,java_sources=java_keys(java_home,[int.from_bytes(p[:8],'big')^c for _,p,c,_,_ in cases[:3]],root)
    fixtures=[];stock_public=None
    for ordinal,(name,plain,client_seed,trust,tamper) in enumerate(cases):
        cipher=bytes(256) if tamper=='cipher' else encrypt_seed(client_key,plain)
        signature=sign_sha256(signer,plain) if trust=='local' else bytes(256)
        if trust=='local' and tamper=='body-signature':signature=bytes([signature[0]^1])+signature[1:]
        e=Offline72(binary,libraries,records,True);reader=None;runtime=None
        try:
            reader=CryptoResponseReader72(e);read=reader.run(37481,response_wire(cipher,signature))
            for off,raw in ((0x98,cipher),(0x88,signature)):
                pointer=e.read(OBJECT+off)
                if reader.values[pointer]['hex']!=base64.b64encode(raw).hex():raise ValueError('Response wire store differs')
            runtime=ResponseCrypto72(e,binary,metadata,client_seed,signer if trust=='local' else None,tamper)
            trace,key=runtime.run_response()
            decrypted=b'' if tamper in ('cipher','key-signature') else plain
            if runtime.decrypted!=decrypted:raise ValueError('Native decryption differs from supplied plaintext')
            public=public_der(signer) if trust=='local' else runtime.original_keys[103][0]
            expected_verify=verify_key_signature(decrypted,signature,public_pem(public))
            if bool(runtime.signature_result)!=expected_verify:raise ValueError('Native body signature differs from independent RSA')
            if runtime.operations!={101:-7 if tamper in ('cipher','key-signature') else 0,103:0 if expected_verify else -7}:
                raise ValueError('Native crypto return codes differ from success/rejection expectations')
            expected_stop=SHORT if expected_verify and len(plain)<8 else SUCCESS if expected_verify else REJECT
            if runtime.stop!=expected_stop:raise ValueError('Response crypto did not take the expected success/error branch')
            if expected_stop==SUCCESS:
                if key!=reference[ordinal] or e.read(0x145878FC0,'B')!=0:raise ValueError('Native key differs from Java or key-switch store missing')
            elif key is not None or e.read(0x145878FC0,'B')!=1:
                raise ValueError('Rejected response installed a session key')
            if runtime.session.call(0x14CB58170,0x14587D740)!=client_seed:raise ValueError('Response altered saved client seed')
            if stock_public is None:stock_public=runtime.original_keys[103][0]
            if runtime.original_keys[103][0]!=stock_public:raise ValueError('Stock response public key changed between fixtures')
            fixtures.append(dict(name=name,trust=trust,tamperedInput=tamper,plaintextHex=plain.hex(),
                                 clientSeedHex=f'{client_seed:016x}',reader=read,trace=trace,
                                 nativeOperations=runtime.operations,keySignatureChecks=runtime.key_checks,
                                 nativeSHA256=runtime.hash_result.hex(),signatureAccepted=expected_verify,
                                 sessionKeySHA256=hashlib.sha256(key).hexdigest() if key else None,
                                 javaByteComparison=key is not None,passed=True))
            print(json.dumps(dict(case=name,stop=hex(runtime.stop),sessionKey=key is not None)),flush=True)
        finally:
            if runtime:runtime.close()
            if reader:reader.close()
            e.close()
    seed=json.loads((root/'protocol/cn-7.2/seed-crypto-observations.json').read_text('utf8'))
    expected_public=next(r['keyEvidence']['derSHA256'] for r in seed['results'] if r['oracle'])
    if hashlib.sha256(stock_public).hexdigest()!=expected_public:raise ValueError('Request/response public-key relationship changed')
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,fieldBindings=fields,
                entry='0x15a4de707',successStop=hex(SUCCESS),rejectionStop=hex(REJECT),shortStop=hex(SHORT),
                runtimeStatus='not-tested',wholeHandlerExecuted=False,keyVerificationForced=False,
                stockPrivateDerSHA256=hashlib.sha256(private_der).hexdigest(),
                stockPrivateSignatureSHA256=hashlib.sha256(private_signature).hexdigest(),
                stockPublicDerSHA256=hashlib.sha256(stock_public).hexdigest(),publicKeyMatches=public_matches,
                fixtureRootPublicSHA256=hashlib.sha256(public_der(signer)).hexdigest(),
                trustInputReplacements=['local cases: native private-key signature','local cases: server public key and its signature',
                                        'local cases: root PEM input to native key verifier'],
                materialDiscovery=material_trace,javaSourceSHA256=java_sources,
                shaConstantSHA256=runtime.constant_hash,
                results=[{k:v for k,v in f.items() if k not in ('reader','trace')} for f in fixtures],
                modelsFrom=['seed-crypto-observations.json','response-wire-observations.json'],
                addedModels=['ASCII reader string storage','SHA virtual class and byte/uint array layouts',
                             'native metadata SHA constant bytes supplied as initialized storage',
                             'byte[] Clone/type-check and SuppressFinalize storage boundary'],
                outsideScope=['real SDK warmup/initialization','original-server valid response signature',
                              'production key provisioning and patch installation','UID/account processing after key install',
                              'whole Handler and actual network','real-client acceptance'])


def main():
    a=parse_args();root=Path(__file__).resolve().parents[1]
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    signing_bytes=a.signing_key.read_bytes();signer=private_numbers(signing_bytes)
    report=recover(b,m,records,a.libraries,signer,a.java_home,root)
    report['signingFixtureFileSHA256']=hashlib.sha256(signing_bytes).hexdigest()
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf-8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],fields=len(report['fieldBindings']),runtimeStatus=report['runtimeStatus'])))


def parse_args():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--java-home',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path);p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    p.add_argument('--signing-key',type=Path,default=Path('src/main/resources/keys/SigningKey.der'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    return a


if __name__=='__main__':main()
