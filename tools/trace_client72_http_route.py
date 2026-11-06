"""Actual isolated CN72 HTTP output through native crypto and bounded list reader.

The fixed archive rejects 7.2 by version. Observe its real signed error body,
never override admission. Stop before the unrecovered error-detail reader.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, PE, read_verified, extract
    from .client72_emulator import Offline72
    from .client72_reader import WireInput
    from .client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256, tlv
    from .client72_seed_crypto import rsa_public_numbers, verify_key_signature
    from .trace_client72_response_crypto import discover_private_key
    from .trace_client72_dispatch_prefix import DispatchPrefix72, MERGE, REJECT
    from .trace_client72_trust_contract import TrustSupply72, attach_supplier, native_sites
    from .trace_client72_region_list import RegionListGraph72, validate_identity
except ImportError:
    from extract_client72 import HASHES, PE, read_verified, extract
    from client72_emulator import Offline72
    from client72_reader import WireInput
    from client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256, tlv
    from client72_seed_crypto import rsa_public_numbers, verify_key_signature
    from trace_client72_response_crypto import discover_private_key
    from trace_client72_dispatch_prefix import DispatchPrefix72, MERGE, REJECT
    from trace_client72_trust_contract import TrustSupply72, attach_supplier, native_sites
    from trace_client72_region_list import RegionListGraph72, validate_identity


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'fixtures', 'output', 'summary-output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if not args.output.resolve().is_relative_to(root / 'local'):
        parser.error('Raw route evidence belongs under local/.')
    manifest = json.loads((args.fixtures / 'result.json').read_text('utf8'))
    if (manifest['task'], manifest['actualArchiveVersion'], manifest['requestedVersion'], manifest['jarSHA256']) != (
            'D-01b5h', '7.1.0', 'CNRELWin7.2.0', 'dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3'):
        raise ValueError('Unexpected archive/request; do not override version admission')
    if sha((root / 'tools/Client72HttpRouteAudit.java').read_bytes()) != manifest['sourceSHA256']:
        raise ValueError('Executed HTTP source differs')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    libraries = root / 'local/migration72/python'
    sites = native_sites(binary)
    private_der, _, _ = discover_private_key(binary, metadata, records, libraries)
    private = private_numbers(private_der)
    stock_private = sha(private_der)
    signer = private_numbers((root / 'src/main/resources/keys/SigningKey.der').read_bytes())
    wrapped = (root / 'src/main/resources/keys/game_keys/4_Pub.der').read_bytes()
    _, body, _ = tlv(wrapped); _, _, at = tlv(body); tag, value, _ = tlv(body, at)
    if tag != 3 or value[0] != 0 or rsa_public_numbers(value[1:]) != tuple(private[1:3]):
        raise ValueError('Native private source does not correspond to actual HTTP key4')
    prior = json.loads((root / 'protocol/cn-7.2/seed-crypto-observations.json').read_text('utf8'))
    stock_public = next(row['keyEvidence']['derSHA256'] for row in prior['results'] if row['oracle'])
    public = public_der(signer)
    bundle = dict(public=public, root=public_pem(public), publicSignature=sign_sha256(signer, public),
        privateSignature=sign_sha256(signer, private_der), stockHashes=dict(public=stock_public, private=stock_private))
    del private_der
    payloads = {name: json.loads((args.fixtures / file).read_text('utf8')) for name, file in (
        ('actual-http-version-rejection', 'current-region.json'), ('bad-body-signature', 'current-region.bad-sign.json'))}
    plain = decrypt_integer(private, base64.b64decode(payloads['actual-http-version-rejection']['content'], validate=True))
    del private
    fields = wire_fields(plain)
    if fields.keys() != {1, 2, 3, 5} or fields[1] != [(0, 11)] or fields[2] != [(2, b'Connection Failed!')] or fields[3] != [(2, b'')]:
        raise ValueError('Actual HTTP plaintext is not the expected server-side version rejection')
    detail = wire_fields(fields[5][0][1])
    if detail.keys() != {1, 2, 3, 4} or detail[2][0][1] - detail[1][0][1] not in (1, 2):
        raise ValueError('Version-error producer detail/timing differs')
    text = detail[4][0][1].decode('utf8')
    if 'Server version: 7.1.0' not in text or 'Client version: 7.2.0' not in text:
        raise ValueError('Version-error producer text differs')
    fixtures = []
    for name, payload in payloads.items():
        oracle = verify_key_signature(plain, base64.b64decode(payload['sign'], validate=True), bundle['root'])
        accepted = name == 'actual-http-version-rejection'
        if oracle != accepted:
            raise ValueError('Independent integer RSA body signature differs')
        engine = Offline72(binary, libraries, records, True)
        runtime = None
        try:
            engine.prepare()
            runtime = RoutePrefix72(engine, binary, metadata, plain, signer, False)
            supplier = TrustSupply72(engine, bundle, None)
            attach_supplier(runtime, supplier, 'observe')
            result = runtime.run_prefix(payload)
            if result['stop'] != hex(MERGE if accepted else REJECT) or result['readerCalls'] != 0:
                raise ValueError('Native route signature/decrypt stop differs')
            if accepted and runtime.decrypted != plain:
                raise ValueError('Native plaintext differs from independent RSA')
            if [row['operation'] for row in supplier.checks] != [101, 103] or not all(row['native'] and row['oracle'] for row in supplier.checks):
                raise ValueError('Native key verification not exercised/passed for both key classes')
            for address in (0x140572059, 0x140574A95):
                expected = binary[PE(binary).offset(address, 32):][:32]
                if bytes(engine.uc.mem_read(address, 32)) != expected:
                    raise ValueError('Native trust/decision code changed')
            fixtures.append(dict(name=name, bodySignatureAccepted=accepted, integerSignatureOracle=oracle,
                plaintextSHA256=sha(plain), result=result, supply=supplier.result()))
            print(json.dumps(dict(case=name, bodySignatureAccepted=accepted, targetReaderCalls=0)), flush=True)
        finally:
            if runtime: runtime.close()
            engine.close()
    list_wire = (args.fixtures / 'region-list.response.bin').read_bytes()
    source = wire_fields(list_wire)
    expected_list = dict(MNJHOKMKIIO=dict(kind='bytes', hex=source[5][0][1].hex()),
        AMANAEJDNNF=dict(kind='bytes', hex=source[6][0][1].hex()), LCCLHKIFBMJ=True,
        PNECJNBHCNG=[dict(OKDLHHPJOLA='os_usa', HDLLHEMPPCF='os_usa', NOEEBKJAFAG='DEV_PUBLIC',
            MJCIFCHMPGH='http://127.0.0.1:' + str(manifest['httpPort']) + '/query_cur_region/os_usa')])
    validate_identity(binary, metadata, records)
    engine = Offline72(binary, libraries, records, False)
    graph = RegionListGraph72(engine, root / 'protocol/cn-7.2')
    try:
        list_result = graph.run_graph(87445, list_wire)
        if list_result['result'] != expected_list:
            raise ValueError('Actual HTTP list did not preserve fields in native reader')
    finally:
        graph.close(); engine.close()
    summary = dict(schemaVersion=1, task='D-01b5h1', inputSHA256=HASHES,
        actualHttpRequests=2, archiveVersion='7.1.0', requestedVersion='CNRELWin7.2.0', serverAdmittedClient=False,
        nativeCryptoCases=2, validSignedVersionRejection=True, badBodySignatureRejected=True,
        nativeCurrentRegionReaderExecuted=False, stopBeforeUnrecoveredErrorDetail=True,
        actualHttpListNativeFields=9, actualHttpListNativeReaderExecuted=True,
        originalClientExecuted=False, privateKeyExported=False, verificationForced=False,
        runtimeStatus='not-tested', targetProtocolReady=False, sourceSHA256={str(f.relative_to(root)).replace('\\', '/'): sha(f.read_bytes()) for f in (
            Path(__file__), root/'tools/Client72HttpRouteAudit.java', root/'tools/trace_client72_trust_contract.py',
            root/'tools/trace_client72_dispatch_prefix.py', root/'tools/trace_client72_region_list.py')},
        fixtureFileSHA256={f: sha((args.fixtures/f).read_bytes()) for f in ['result.json', 'region-list.response.bin', 'current-region.json', 'current-region.bad-sign.json']},
        boundaries=['Actual isolated HTTP only; no official SDK route or patched-client connection',
            'Native JSON storage, managed runtime and trust input models reused explicitly',
            'Version-error detail only interpreted as server producer data; target nested consumer unknown',
            'No version override, production code/ready change or successful 7.2 admission'])
    report = dict(summary=summary, sites=sites, cryptoFixtures=fixtures, listNative=list_result,
        serverProducedPlaintextHex=plain.hex())
    for path, value in [(args.output, report), (args.summary_output, summary)]:
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2)+'\n', 'utf8')


class RoutePrefix72(DispatchPrefix72):
    def observe(self, address):
        super().observe(address)
        if address == MERGE:
            self.stop = MERGE
            self.e.uc.emu_stop()


def decrypt_integer(private, cipher):
    width = (private[1].bit_length() + 7) // 8
    if width != 256 or not cipher or len(cipher) % width:
        raise ValueError('Expected complete RSA2048 HTTP blocks')
    result = bytearray()
    for offset in range(0, len(cipher), width):
        block = pow(int.from_bytes(cipher[offset:offset+width], 'big'), private[3], private[1]).to_bytes(width, 'big')
        delimiter = block.find(b'\0', 2)
        if block[:2] != b'\0\2' or delimiter < 10:
            raise ValueError('Invalid independent PKCS1 encryption block')
        result.extend(block[delimiter+1:])
    return bytes(result)


def wire_fields(data):
    source = WireInput(data)
    result = {}
    while key := source.tag():
        wire = key & 7
        if wire == 0: value = source.varint()
        elif wire == 2: value = source.blob()
        else: raise ValueError('Unexpected server producer wire kind')
        result.setdefault(key >> 3, []).append((wire, value))
    return result


def sha(data):
    return hashlib.sha256(data).hexdigest()


if __name__ == '__main__': main()
