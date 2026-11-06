"""Bounded CN72 trust-input supply at two native call sites, with real verification.

This offline contract does not install hooks or write client/private-key files.
It supplies precomputed public material and signatures, never verification results.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import struct

try:
    from .extract_client72 import HASHES, PE, read_verified, extract
    from .client72_emulator import Offline72
    from .trace_client72_seed_crypto import SeedString72
    from .client72_response_crypto import ResponseCrypto72, CryptoResponseReader72, SUCCESS, REJECT
    from .trace_client72_response_crypto import discover_private_key, response_wire
    from .trace_client72_dispatch_prefix import DispatchPrefix72, SUCCESS as DISPATCH_SUCCESS, REJECT as DISPATCH_REJECT
    from .client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256, encrypt_seed, tlv
    from .client72_seed_crypto import rsa_public_numbers, verify_key_signature
    from .trace_client72_session import java_keys
except ImportError:
    from extract_client72 import HASHES, PE, read_verified, extract
    from client72_emulator import Offline72
    from trace_client72_seed_crypto import SeedString72
    from client72_response_crypto import ResponseCrypto72, CryptoResponseReader72, SUCCESS, REJECT
    from trace_client72_response_crypto import discover_private_key, response_wire
    from trace_client72_dispatch_prefix import DispatchPrefix72, SUCCESS as DISPATCH_SUCCESS, REJECT as DISPATCH_REJECT
    from client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256, encrypt_seed, tlv
    from client72_seed_crypto import rsa_public_numbers, verify_key_signature
    from trace_client72_session import java_keys

API, COPY, CHECK, CHECK_RETURN = 0x140571EB0, 0x140572059, 0x140574A95, 0x140574A9A
PATTERNS = {COPY: 'E8 ? ? ? ? 48 83 C4 20 66 41 C7 06 30 82',
            CHECK: 'E8 ? ? ? ? 48 83 C4 30 84 C0 74 ? 41 8B 04 24'}
CASES = [('seed-local', 'seed', None), ('seed-other-entropy', 'seed', 'entropy'),
         ('seed-bad-key-signature', 'seed', 'public-signature'),
         ('seed-wrong-root', 'seed', 'root'), ('seed-unknown-key', 'seed', 'unknown-key'),
         ('response-local', 'response', None), ('response-bad-private-signature', 'response', 'private-signature'),
         ('response-bad-public-signature', 'response', 'public-signature'),
         ('response-wrong-root', 'response', 'root'), ('response-wrong-server-key', 'response', 'wrong-server-key'),
         ('response-bad-body-signature', 'response', 'body-signature'), ('response-bad-cipher', 'response', 'cipher'),
         ('dispatch-local', 'dispatch', None), ('dispatch-bad-body-signature', 'dispatch', 'body-signature')]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def native_sites(binary):
    pe = PE(binary)
    rows = []
    for address, pattern in PATTERNS.items():
        regex = re.compile(b''.join(b'.' if token == '?' else re.escape(bytes.fromhex(token))
                                    for token in pattern.split()), re.S)
        hits = [start + match.start() for start, size, offset, flags in pe.sections if flags & 0x20000000
                for match in regex.finditer(binary[offset:offset + size])]
        offset = pe.offset(address, 5, executable=True)
        target = address + 5 + struct.unpack_from('<i', binary, offset + 1)[0]
        if hits != [address] or target != (0x141FDF9C0 if address == COPY else 0x14078BAB0):
            raise ValueError('Native signature/call target differs from the pinned contract')
        rows.append(dict(address=hex(address), pattern=pattern, hits=[hex(v) for v in hits],
                         callTarget=hex(target), bytesSHA256=sha(binary[offset:offset + 32])))
    return rows


class TrustSupply72:
    """One immutable known-key bundle; unknown inputs receive no local trust.

    At COPY only R13 changes. At CHECK only R8 and the fifth stack argument
    change. Key lengths, instructions, return values and flags stay native.
    """
    def __init__(self, e, bundle, mode):
        self.e, self.bundle, self.mode = e, bundle, mode
        self.events, self.calls, self.checks = [], [], []
        self.current = None
        self.public = bundle['alternatePublic'] if mode == 'wrong-server-key' else bundle['public']
        signature = bundle['alternateSignature'] if mode == 'wrong-server-key' else bundle['publicSignature']
        self.raw = self.store(self.public[2:])
        self.root = self.store(bundle['root'] + b'\0')
        self.signatures = {}
        for kind, value in [('public', signature), ('private', bundle['privateSignature'])]:
            if mode == kind + '-signature':
                value = bytes([value[0] ^ 1]) + value[1:]
            self.signatures[kind] = self.store(value)

    def store(self, raw):
        pointer = self.e.alloc(len(raw))
        self.e.uc.mem_write(pointer, raw)
        return pointer

    def observe(self, address):
        e = self.e
        if address == API:
            operation = e.reg('ecx')
            if operation not in (100, 101, 103):
                raise ValueError('Unexpected crypto operation')
            self.calls.append(dict(operation=operation, callerReturn=hex(e.read(e.reg('rsp'))),
                                   dataLength=e.reg('r8d'), signatureLength=e.read(e.reg('rsp') + 0x40, 'I'),
                                   sourcePointer=e.read(e.reg('rsp') + 0x30)))
            if self.mode == 'unknown-key':
                pointer = self.calls[-1]['sourcePointer'] + 32
                e.uc.mem_write(pointer, bytes([e.read(pointer, 'B') ^ 1]))
        elif address == COPY:
            operation = e.read(e.reg('r12'), 'I')
            full_length = e.reg('rbx')
            raw_pointer = e.reg('r13')
            if (operation != self.calls[-1]['operation'] or raw_pointer != self.calls[-1]['sourcePointer']
                    or e.reg('rcx') != e.reg('r14') or e.reg('edx') != 0
                    or e.reg('r8') != full_length + 1 or e.reg('r15') != full_length + 1):
                raise ValueError('Native memset/source-register contract changed')
            raw_length = full_length - 2
            raw = bytes(e.uc.mem_read(raw_pointer, raw_length))
            if int.from_bytes(raw[:2], 'big') + 4 != full_length:
                raise ValueError('Raw DER length does not match the native allocation')
            kind = 'private' if operation == 101 else 'public'
            original_hash = sha(b'\x30\x82' + raw)
            recognized = original_hash == self.bundle['stockHashes'][kind]
            changed = []
            if recognized and kind == 'public':
                if len(self.public) != full_length:
                    raise ValueError('Replacement key requires a different allocation length')
                e.uc.reg_write(e.R.UC_X86_REG_R13, self.raw)
                changed.append('r13')
            self.current = dict(operation=operation, kind=kind, rawLength=raw_length, derLength=full_length,
                                allocationLength=full_length + 1, originalDerSHA256=original_hash,
                                sourceRecognized=recognized, copyChangedRegisters=changed)
            self.events.append(self.current)
        elif address == CHECK:
            row = self.current
            der = bytes(e.uc.mem_read(e.reg('rcx'), e.reg('edx')))
            kind = row['kind']
            expected = self.bundle['stockHashes']['private'] if kind == 'private' else sha(self.public)
            if (e.reg('rcx') != e.reg('rdi') or e.reg('edx') != row['derLength']
                    or e.reg('r9d') != 256 or e.read(e.reg('rsp') + 0x20) != e.reg('rsi')):
                raise ValueError('Native verification-call ABI changed')
            supplied = row['sourceRecognized'] and sha(der) == expected
            if supplied:
                e.uc.reg_write(e.R.UC_X86_REG_R8, self.signatures[kind])
                if self.mode != 'root':
                    e.write(e.reg('rsp') + 0x20, self.root)
            signature = bytes(e.uc.mem_read(e.reg('r8'), e.reg('r9d')))
            root = bytes(e.uc.mem_read(e.read(e.reg('rsp') + 0x20), 428)).split(b'\0')[0]
            row.update(supplied=supplied, verifierDerSHA256=sha(der), signatureSHA256=sha(signature),
                       rootSHA256=sha(root), signatureLength=256,
                       checkChangedRegisters=['r8'] if supplied else [],
                       checkChangedStackArguments=[5] if supplied and self.mode != 'root' else [])
            self.checks.append(dict(operation=row['operation'], oracle=verify_key_signature(der, signature, root)))
        elif address == CHECK_RETURN:
            self.checks[-1]['native'] = bool(e.reg('al'))
            if self.checks[-1]['native'] != self.checks[-1]['oracle']:
                raise ValueError('Unmodified native key verification differs from integer RSA')

    def result(self):
        return dict(events=self.events, calls=[{k: v for k, v in row.items() if k != 'sourcePointer'}
                                               for row in self.calls], keyChecks=self.checks)


def attach_supplier(runtime, supplier, method):
    observe = getattr(runtime, method)
    e = supplier.e
    registers = ('rax', 'rbx', 'rcx', 'rdx', 'rsi', 'rdi', 'rbp', 'rsp',
                 'r8', 'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15', 'rip', 'eflags')
    def combined(address):
        before = {name: e.reg(name) for name in registers} if address in (COPY, CHECK, CHECK_RETURN) else None
        stack = bytes(e.uc.mem_read(e.reg('rsp'), 0x30)) if address == CHECK else None
        supplier.observe(address)
        if before is not None:
            changed = [name for name, value in before.items() if e.reg(name) != value]
            expected = supplier.current['copyChangedRegisters'] if address == COPY else (
                supplier.current['checkChangedRegisters'] if address == CHECK else [])
            if changed != expected:
                raise ValueError('Input supplier changed an undeclared register, flag or return value')
            if address == CHECK_RETURN:
                supplier.current['returnRegistersAndFlagsUntouched'] = True
            if stack is not None:
                after_stack = bytes(e.uc.mem_read(e.reg('rsp'), 0x30))
                changed_stack = [i for i in range(0, 0x30, 8) if stack[i:i + 8] != after_stack[i:i + 8]]
                expected_stack = [0x20] if supplier.current['checkChangedStackArguments'] else []
                if changed_stack != expected_stack:
                    raise ValueError('Input supplier changed an undeclared stack argument')
        observe(address)
    setattr(runtime, method, combined)


def recover(binary, metadata, records, libraries, root, java_home):
    sites = native_sites(binary)
    private_der, private_signature, discovery = discover_private_key(binary, metadata, records, libraries)
    client_key = private_numbers(private_der)
    signing_bytes = (root / 'src/main/resources/keys/SigningKey.der').read_bytes()
    signer = private_numbers(signing_bytes)
    if signer[1].bit_length() != 2048 or client_key[1].bit_length() != 2048:
        raise ValueError('Expected two 2048-bit fixture keys')
    client_public = (root / 'src/main/resources/keys/game_keys/4_Pub.der').read_bytes()
    _, body, _ = tlv(client_public)
    _, _, offset = tlv(body)
    tag, value, _ = tlv(body, offset)
    if tag != 3 or value[0] != 0 or rsa_public_numbers(value[1:]) != tuple(client_key[1:3]):
        raise ValueError('Native private key does not match repository key4 public numbers')
    prior = json.loads((root / 'protocol/cn-7.2/seed-crypto-observations.json').read_text('utf8'))
    if prior['inputSHA256'] != HASHES:
        raise ValueError('Seed discovery has different inputs')
    stock_public = next(row['keyEvidence']['derSHA256'] for row in prior['results'] if row['oracle'])
    public = public_der(signer)
    if len(public) != 270 or (root / 'patch/server_public_key.bin').read_bytes() != public[2:]:
        raise ValueError('Patch public blob does not equal server public DER without 30 82')
    bundle = dict(public=public, root=public_pem(public), publicSignature=sign_sha256(signer, public),
                  privateSignature=sign_sha256(signer, private_der), alternatePublic=public_der(client_key),
                  alternateSignature=sign_sha256(signer, public_der(client_key)),
                  stockHashes=dict(public=stock_public, private=sha(private_der)))
    private_length = len(private_der)
    del private_der
    plain = bytes.fromhex('fedcba9876543210')
    client_seed = 0x0123456789abcdef
    reference, java_sources = java_keys(java_home, [int.from_bytes(plain, 'big') ^ client_seed], root)
    fixtures = []
    pe = PE(binary)
    for name, kind, mode in CASES:
        e = Offline72(binary, libraries, records, True)
        runtime = reader = None
        try:
            accepted = mode in (None, 'entropy')
            if kind == 'seed':
                e.prepare()
                runtime = SeedString72(e, binary, metadata, 0.5, True, 37 if mode == 'entropy' else 0)
                supplier = TrustSupply72(e, bundle, mode)
                attach_supplier(runtime, supplier, 'observe')
                result = runtime.run(0.5, True, None if accepted else mode)
                details = dict(ioctlReturn=result['ioctlReturn'], base64Length=result['base64Length'],
                               encryptionOracle=result['oracle'], trace=result['trace'])
            elif kind == 'response':
                cipher = bytes(256) if mode == 'cipher' else encrypt_seed(client_key, plain)
                signature = sign_sha256(signer, plain)
                if mode == 'body-signature':
                    signature = bytes([signature[0] ^ 1]) + signature[1:]
                reader = CryptoResponseReader72(e)
                reader.run(37481, response_wire(cipher, signature))
                runtime = ResponseCrypto72(e, binary, metadata, client_seed)
                supplier = TrustSupply72(e, bundle, mode)
                attach_supplier(runtime, supplier, 'observe_response')
                trace, key = runtime.run_response()
                if runtime.stop != (SUCCESS if accepted else REJECT) or (key is not None) != accepted:
                    raise ValueError('Response acceptance/key-install boundary changed')
                if accepted and key != reference[0]:
                    raise ValueError('Session key differs from independent Java MT64')
                expected_plain = b'' if mode in ('private-signature', 'root', 'cipher') else plain
                if runtime.decrypted != expected_plain:
                    raise ValueError('Native decrypted bytes differ from independent plaintext')
                body_oracle = verify_key_signature(expected_plain, signature, public_pem(supplier.public))
                expected_verify = supplier.checks[-1]['oracle'] and body_oracle
                if bool(runtime.signature_result) != expected_verify or expected_verify != accepted:
                    raise ValueError('Native response body signature differs from integer RSA')
                details = dict(nativeOperations=runtime.operations, bodySignatureAccepted=expected_verify,
                               bodySignatureIntegerOracle=body_oracle,
                               nativeSHA256=runtime.hash_result.hex(), sessionKeySHA256=sha(key) if key else None,
                               javaByteComparison=key is not None, trace=trace)
            else:
                dispatch_plain = b'\x72\x09fixture-A'
                signature = sign_sha256(signer, dispatch_plain)
                if mode == 'body-signature':
                    signature = bytes([signature[0] ^ 1]) + signature[1:]
                payload = dict(content=base64.b64encode(encrypt_seed(client_key, dispatch_plain)).decode(),
                               sign=base64.b64encode(signature).decode())
                e.prepare()
                runtime = DispatchPrefix72(e, binary, metadata, dispatch_plain, signer, False)
                supplier = TrustSupply72(e, bundle, mode)
                attach_supplier(runtime, supplier, 'observe')
                result = runtime.run_prefix(payload)
                if result['stop'] != hex(DISPATCH_SUCCESS if accepted else DISPATCH_REJECT):
                    raise ValueError('Dispatch parser acceptance boundary changed')
                if result['readerCalls'] != int(accepted) or (accepted and result['value'] != 'fixture-A'):
                    raise ValueError('Dispatch plaintext/reader boundary changed')
                details = dict(readerCalls=result['readerCalls'], nativePlaintextSHA256=result['nativePlaintextSHA256'],
                               postParsePointerPreserved=result['postParsePointerPreserved'], trace=result['trace'])
            # Both complete native call-site windows remain byte-identical.
            for address in (COPY, CHECK):
                offset = pe.offset(address, 32)
                if bytes(e.uc.mem_read(address, 32)) != binary[offset:offset + 32]:
                    raise ValueError('Native call/decision instructions changed')
            fixtures.append(dict(name=name, kind=kind, accepted=accepted, inputTamper=mode,
                                 nativeInstructionWindowsUnchanged=True, **supplier.result(), **details, passed=True))
            print(json.dumps(dict(case=name, accepted=accepted, keyChecks=supplier.checks)), flush=True)
        finally:
            if runtime:
                runtime.close()
            if reader:
                reader.close()
            e.close()
    if fixtures[0]['encryptionOracle']['cipherSHA256'] == fixtures[1]['encryptionOracle']['cipherSHA256']:
        raise ValueError('Different entropy failed to produce different native ciphertext')
    return dict(schemaVersion=1, task='F-04b', inputSHA256=HASHES, fixtureCount=len(fixtures), sites=sites,
                runtimeStatus='not-tested', hookInstalled=False, verificationForced=False, targetPrivateKeyExported=False,
                rootSource='explicit fixture: existing SigningKey public key also serves as offline trust root',
                sourceEvidence=dict(stockDerSHA256=bundle['stockHashes'], nativePrivateDerLength=private_length,
                                    nativePrivateSignatureSHA256=sha(private_signature), clientPublicFileSHA256=sha(client_public),
                                    clientPublicMatch='src/main/resources/keys/game_keys/4_Pub.der',
                                    localRootSHA256=sha(bundle['root']), localPublicDerSHA256=sha(public),
                                    patchPublicBlobSHA256=sha(public[2:]), signingFixtureSHA256=sha(signing_bytes)),
                materialDiscovery=discovery, javaSourceSHA256=java_sources,
                fixtures=fixtures, results=[{k: v for k, v in row.items() if k != 'trace'} for row in fixtures],
                modelsFrom=['seed-crypto-observations.json', 'response-crypto-observations.json', 'dispatch-prefix-observations.json'],
                permittedSupply=dict(copy=['R13 raw-public-key pointer only; matching source and equal DER length'],
                                     check=['R8 precomputed key-signature pointer', 'stack argument5 root-public-PEM pointer'],
                                     privateMaterial='native source stays unchanged; only its precomputed signature supplied'),
                outsideScope=['actual DLL hook trampoline and live ABI', 'production root provisioning and bundle packaging',
                              'SDK initialization/HTTP route/string factory', 'whole Handler/dispatch/socket', 'real-client acceptance'])


def validate_summary(report):
    errors = []
    for key, expected in dict(inputSHA256=HASHES, fixtureCount=len(CASES), runtimeStatus='not-tested',
                              hookInstalled=False, verificationForced=False, targetPrivateKeyExported=False).items():
        if report.get(key) != expected:
            errors.append('Trust contract input/scope mismatch: ' + key)
    rows = report.get('results', [])
    if [(row.get('name'), row.get('kind'), row.get('inputTamper')) for row in rows] != CASES:
        errors.append('Trust contract case list changed')
    for row in rows:
        mode, kind = row.get('inputTamper'), row.get('kind')
        accepted = mode in (None, 'entropy')
        operations = [100] if kind == 'seed' else [101, 103]
        if row.get('accepted') != accepted or row.get('passed') is not True or row.get('nativeInstructionWindowsUnchanged') is not True:
            errors.append('Trust acceptance or instruction-preservation mismatch')
        checks = row.get('keyChecks', [])
        if [check.get('operation') for check in checks] != operations:
            errors.append('Trust operation coverage mismatch')
        for check in checks:
            rejected = mode in ('root', 'unknown-key') or mode == ('private-signature' if check['operation'] == 101 else 'public-signature')
            if check.get('native') != (not rejected) or check.get('oracle') != (not rejected):
                errors.append('Trust native/oracle decision mismatch')
        if len(row.get('events', [])) != len(operations):
            errors.append('Trust native hook coverage mismatch')
        for event in row.get('events', []):
            private = event.get('operation') == 101
            length = 1192 if private else 270
            known = mode != 'unknown-key'
            if (event.get('derLength'), event.get('rawLength'), event.get('allocationLength')) != (length, length - 2, length + 1):
                errors.append('Trust native DER/allocation lengths mismatch')
            if (event.get('sourceRecognized'), event.get('supplied')) != (known, known):
                errors.append('Trust unknown-input policy mismatch')
            if event.get('copyChangedRegisters') != (['r13'] if known and not private else []):
                errors.append('Trust copy-site register mutation mismatch')
            if event.get('checkChangedRegisters') != (['r8'] if known else []):
                errors.append('Trust verification-site register mutation mismatch')
            if event.get('checkChangedStackArguments') != ([5] if known and mode != 'root' else []):
                errors.append('Trust root argument mutation mismatch')
            if event.get('returnRegistersAndFlagsUntouched') is not True:
                errors.append('Trust native result was not preserved')
        if kind == 'seed' and (row.get('ioctlReturn'), row.get('base64Length')) != ((0, 344) if accepted else (-7, 0)):
            errors.append('Trust seed result mismatch')
        if kind == 'response' and (row.get('bodySignatureAccepted'), row.get('javaByteComparison'), bool(row.get('sessionKeySHA256'))) != (accepted, accepted, accepted):
            errors.append('Trust response acceptance/install mismatch')
        if kind == 'response':
            returns = {int(key): value for key, value in row.get('nativeOperations', {}).items()}
            if returns != {101: -7 if mode in ('private-signature', 'root', 'cipher') else 0, 103: 0 if accepted else -7}:
                errors.append('Trust native operation return mismatch')
        if kind == 'dispatch' and (row.get('readerCalls'), row.get('postParsePointerPreserved')) != (int(accepted), accepted):
            errors.append('Trust dispatch reader boundary mismatch')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    args = parser.parse_args()
    for path in (args.output, args.summary_output):
        if path.resolve().is_relative_to(args.client.resolve()):
            parser.error('Output must remain outside client')
    binary, metadata, startup = [read_verified(args.client / name, digest) for name, digest in HASHES.items()]
    records, _ = extract(binary, metadata, startup=startup, structures=True)
    report = recover(binary, metadata, records, args.libraries, Path(__file__).resolve().parents[1], args.java_home)
    errors = validate_summary(report)
    if errors:
        raise ValueError(errors)
    for path, value in ((args.output, report), (args.summary_output, {k: v for k, v in report.items() if k != 'fixtures'})):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')


if __name__ == '__main__':
    main()
