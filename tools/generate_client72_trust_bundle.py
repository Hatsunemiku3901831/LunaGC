"""Generate local public trust material; target private DER never leaves memory."""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .trace_client72_response_crypto import discover_private_key
    from .client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256
    from .client72_seed_crypto import verify_key_signature
    from .client72_emulator import Offline72
    from .trace_client72_seed_crypto import SeedString72
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from trace_client72_response_crypto import discover_private_key
    from client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256
    from client72_seed_crypto import verify_key_signature
    from client72_emulator import Offline72
    from trace_client72_seed_crypto import SeedString72

STOCK_PUBLIC = '7427ee11ef690cfd43a001758eabc3604042b62f78a31cb21f4afe6d715d629e'
STOCK_PRIVATE = 'e27f729e1944a7550b51d27b3c3bf4b680209cb982413d3245d56df2ae7f0602'
LOCAL_PUBLIC = '46cf36c9b816eb147b75ff26a09412b368c8f945d0a76b9a252b989184245a0b'
LOCAL_ROOT = 'f1085f473bb98d8a1213a087e62e04061c407fe84c6bd58261a7bf4fcd5c3aa8'
SIGNING_KEY = '4830a2c186bf114d7523796a3360e13ea1bb667f1921e3253aab154133a7501e'
STOCK_KEYS = dict(public=dict(derLength=270, derSHA256=STOCK_PUBLIC),
                  private=dict(derLength=1192, derSHA256=STOCK_PRIVATE))


def make_bundle(private_der, signing_bytes):
    if len(private_der) != 1192 or hashlib.sha256(private_der).hexdigest() != STOCK_PRIVATE:
        raise ValueError('Target private DER differs from fixed native discovery')
    if hashlib.sha256(signing_bytes).hexdigest() != SIGNING_KEY:
        raise ValueError('SigningKey differs from the explicitly selected local signer')
    signer = private_numbers(signing_bytes)
    public = public_der(signer)
    root = public_pem(public)
    private_signature = sign_sha256(signer, private_der)
    if not verify_key_signature(private_der, private_signature, root):
        raise ValueError('Independent target-key signature verification failed')
    bundle = dict(schemaVersion=1, inputSHA256=HASHES, stockKeys=STOCK_KEYS,
                  rootPublicPem=root.decode('ascii'), serverPublicDerHex=public.hex(),
                  serverPublicRawHex=public[2:].hex(), publicKeySignatureHex=sign_sha256(signer, public).hex(),
                  privateKeySignatureHex=private_signature.hex())
    validate_bundle(bundle)
    return bundle


def validate_bundle(bundle):
    expected = {'schemaVersion', 'inputSHA256', 'stockKeys', 'rootPublicPem', 'serverPublicDerHex',
                'serverPublicRawHex', 'publicKeySignatureHex', 'privateKeySignatureHex'}
    if set(bundle) != expected or bundle['schemaVersion'] != 1 or bundle['inputSHA256'] != HASHES or bundle['stockKeys'] != STOCK_KEYS:
        raise ValueError('Trust bundle schema or fixed input identity changed')
    public = bytes.fromhex(bundle['serverPublicDerHex'])
    raw = bytes.fromhex(bundle['serverPublicRawHex'])
    root = bundle['rootPublicPem'].encode('ascii')
    signatures = [bytes.fromhex(bundle[name]) for name in ('publicKeySignatureHex', 'privateKeySignatureHex')]
    if len(public) != 270 or raw != public[2:] or hashlib.sha256(public).hexdigest() != LOCAL_PUBLIC:
        raise ValueError('Unexpected local public key')
    if hashlib.sha256(root).hexdigest() != LOCAL_ROOT or root != public_pem(public):
        raise ValueError('Unexpected local root public PEM')
    if any(len(signature) != 256 for signature in signatures):
        raise ValueError('Key signatures must both contain 256 bytes')
    if not verify_key_signature(public, signatures[0], root):
        raise ValueError('Independent server-key signature verification failed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=Path('local/migration72/trust/client72-trust.json'))
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--evidence', type=Path, default=Path('local/migration72/evidence/trust-bundle-generation.json'))
    parser.add_argument('--public-fixture', type=Path, default=Path('local/migration72/trust/native-public-test.der'))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    for path in (args.output, args.evidence, args.public_fixture):
        if not path.resolve().is_relative_to((root / 'local').resolve()):
            parser.error('Trust material and raw evidence must stay under ignored local/')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    private_der, _, trace = discover_private_key(binary, metadata, records, args.libraries)
    bundle = make_bundle(private_der, (root / 'src/main/resources/keys/SigningKey.der').read_bytes())
    del private_der
    emulator = Offline72(binary, args.libraries, records, True)
    runtime = None
    try:
        emulator.prepare()
        runtime = SeedString72(emulator, binary, metadata, 0.5, True)
        runtime.run(0.5, True, None)
        native_public = runtime.der
        if hashlib.sha256(native_public).hexdigest() != STOCK_PUBLIC:
            raise ValueError('Native seed public-key fixture identity changed')
        args.public_fixture.parent.mkdir(parents=True, exist_ok=True)
        args.public_fixture.write_bytes(native_public)
    finally:
        if runtime:
            runtime.close()
        emulator.close()
    encoded = (json.dumps(bundle, indent=2) + '\n').encode('ascii')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(encoded)
    proof = dict(inputSHA256=HASHES, bundleSHA256=hashlib.sha256(encoded).hexdigest(),
                 bundleBytes=len(encoded), targetPrivateKeyExported=False, nativeDiscovery=trace,
                 materialSHA256={name: hashlib.sha256(bytes.fromhex(bundle[name])).hexdigest()
                                 for name in ('serverPublicDerHex', 'serverPublicRawHex', 'publicKeySignatureHex', 'privateKeySignatureHex')})
    args.evidence.parent.mkdir(parents=True, exist_ok=True)
    args.evidence.write_text(json.dumps(proof, indent=2) + '\n', 'utf8')
    print(json.dumps({key: proof[key] for key in ('bundleSHA256', 'bundleBytes', 'targetPrivateKeyExported')}))


if __name__ == '__main__':
    main()
