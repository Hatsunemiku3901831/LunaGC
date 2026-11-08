"""Fixed CN72 combo signer, offline native execution and independent HMAC oracle.

Reports never contain signing inputs, signing results, auth bodies or their hashes.
All synthetic byte strings are ephemeral; the real resource key is only compared.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import hmac
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'local/migration72/python')]
from client72_sdk_transport import Image, digest
from client72_sdk_response_contract import ResponseFixture
from unicorn import x86_const as ux

RAW = ROOT / 'local/migration72/d02-resume-20261108/r2-sign'
INVENTORY = 'local/migration72/d02-resume-20261108/initial-inventory.private.json'
CONFIG = ROOT / 'local/migration72/d02-resume-20261108/r2-route/final-v3-first/server_pc.private.json'
SDK_FILE = 'YuanShen_Data/Plugins/AccountPlatNative.dll'
SDK_SHA = 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'
JAVA_MANIFEST = 'local/migration72/d02-resume-20261108/r2-sign/java-epoch0-manifest.json'
ROUTE_REPORT = 'local/migration72/d02-resume-20261108/r2-route/final-v3-first.json'
FIXED = {
    INVENTORY: 'f891bafd31c950985ce214cad90e0ba24d2e8733a24dbdca21f9823474c6ff25',
    JAVA_MANIFEST: 'aaf6814ef6ea3cbf26b8c89e3231687fe88966f3b3631c764a28f8b72bfe927e',
    ROUTE_REPORT: 'bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471',
    'local/migration72/d02-resume-20261108/r2-route/rea-final.json': 'da35fb0b37ef5b661a3b6c76040b9bb3ba1c4119d949ccda06bd28e14603dc53',
    'tools/client72_sdk_transport.py': '43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237',
    'tools/client72_sdk_login_chain.py': '53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2',
    'tools/client72_sdk_response_contract.py': '792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5',
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/extract_client72_signatures.py': 'ee92527f3607be774c28be694d6f57e9eed3aa40068b7b584979e27b6470c349',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
    'local/migration72/python/unicorn/lib/unicorn.dll': '010d85dce84b8f6ffaec3d5420f1b5f7768f859eb831be574c7da23e656ee8f1',
    'local/migration72/python/capstone/__init__.py': '94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f',
    'local/migration72/python/unicorn/unicorn_py3/unicorn.py': 'b4de7d560790a587157b17edd71c83b4f2d2bade2447d1a5433c497468ac8aeb',
    'local/migration72/python/unicorn/x86_const.py': 'd7663db6425f52838f3e79e45a8fe22b6c02be7edb44367ea2118aec04ee5728',
}


class SignatureFixture(ResponseFixture):
    """Original strings, ordered map, canonicalizer and SHA/HMAC; storage model only."""
    def __init__(self, pe):
        self.signer_inputs = []
        self.fragment_end = None
        self.observed_body = None
        self.builder_sign_call = None
        self.builder_sign_return = None
        self.builder_output_pointer = None
        super().__init__(pe)

    def call(self, rva, *args):
        self.trace = []
        self.pending = []
        self.native_calls = []
        self.uc.mem_write(self.STACK - 0x10000, bytes(0x11000))
        self.put(self.STACK, self.STOP)
        self.uc.reg_write(ux.UC_X86_REG_RSP, self.STACK)
        for reg, value in zip([ux.UC_X86_REG_RCX, ux.UC_X86_REG_RDX,
                               ux.UC_X86_REG_R8, ux.UC_X86_REG_R9], args):
            self.uc.reg_write(reg, value)
        for i, value in enumerate(args[4:]):
            self.put(self.STACK + 0x28 + i * 8, value)
        self.uc.emu_start(self.pe.base + rva, self.STOP, count=2_000_000)
        if self.uc.reg_read(ux.UC_X86_REG_RIP) != self.STOP:
            raise ValueError('Native instruction budget exhausted')
        if self.uc.reg_read(ux.UC_X86_REG_RSP) != self.STACK + 8:
            raise ValueError('Native stack imbalance')
        self.finish_return(self.STOP, self.STACK + 8)
        return self.uc.reg_read(ux.UC_X86_REG_RAX)

    def on_code(self, uc, address, size, user):
        rva = address - self.pe.base
        if rva == 0x3bec2c:
            body = uc.reg_read(ux.UC_X86_REG_R8)
            length = self.get(uc.reg_read(ux.UC_X86_REG_R9))
            self.observed_body = bytes(uc.mem_read(body, length))
        if rva == self.fragment_end:
            self.trace.append(rva)
            uc.emu_stop()
            return
        if rva == 0x13a1b0:
            # Keep the actual aligned-large-allocation wrapper and its owner slot.
            self.trace.append(rva)
            return
        if rva == 0x396020:
            data = uc.reg_read(ux.UC_X86_REG_RDX)
            length = uc.reg_read(ux.UC_X86_REG_R8)
            key = uc.reg_read(ux.UC_X86_REG_R9)
            key_length = self.get(uc.reg_read(ux.UC_X86_REG_RSP) + 0x28)
            self.signer_inputs.append((bytes(uc.mem_read(data, length)),
                                       bytes(uc.mem_read(key, key_length))))
        if rva == 0x3bea13:
            self.builder_output_pointer = uc.reg_read(ux.UC_X86_REG_RDX)
            self.builder_sign_call = {
                'targetRva': self.get(uc.reg_read(ux.UC_X86_REG_RAX) + 0x58) - self.pe.base,
                'callerOutputPointer64': uc.reg_read(ux.UC_X86_REG_RDX) > 0xffffffff,
                'mapPointer64': uc.reg_read(ux.UC_X86_REG_R8) > 0xffffffff,
                'keyPointer64': uc.reg_read(ux.UC_X86_REG_R9) > 0xffffffff,
            }
        if rva == 0x3bea16:
            result = uc.reg_read(ux.UC_X86_REG_RAX)
            self.builder_sign_return = {
                'returnsCallerOutputPointer64': result == self.builder_output_pointer and result > 0xffffffff,
                'outputBytes': len(self.string(result)),
            }
        if rva == 0x2f4290:
            body = uc.reg_read(ux.UC_X86_REG_R8)
            length = self.get(uc.reg_read(ux.UC_X86_REG_R9))
            self.observed_body = bytes(uc.mem_read(body, length))
        super().on_code(uc, address, size, user)

    def string(self, address):
        length, capacity = self.get(address + 0x10), self.get(address + 0x18)
        if length > 16384 or capacity < length:
            raise ValueError('String outside this bounded synthetic input contract')
        return bytes(self.uc.mem_read(address if capacity < 16 else self.get(address), length))

    def set_string(self, address, value):
        raw = value.encode('utf-8') if isinstance(value, str) else value
        temporary = self.OBJECT + 0x60000
        self.uc.mem_write(temporary, raw + b'\0')
        self.call(0x138160, address, temporary, len(raw))

    def make_map(self, pairs, address=None):
        address = address or self.OBJECT + 0x1000
        head = self.allocate(0x60)
        for offset in [0, 8, 0x10]:
            self.put(head + offset, head)
        self.put(head + 0x18, 0x101, 2)
        self.put(address, head)
        self.put(address + 8, 0)
        key = self.OBJECT + 0x2000
        for name, value in pairs:
            self.put(key + 0x18, 15)
            self.set_string(key, name)
            slot = self.call(0x18f1e0, address, key)
            self.set_string(slot, value)
            self.call(0x1380e0, key)
        return address

    def parse_object(self, address, payload):
        self.call(0x14d040, address)
        temporary = self.OBJECT + 0x80000
        self.put(temporary + 0x18, 15)
        self.set_string(temporary, json.dumps(payload, ensure_ascii=False, separators=(',', ':')))
        accepted = self.call(0x14e110, address, temporary)
        self.call(0x1380e0, temporary)
        if not accepted & 255:
            raise ValueError('Original JSON setup rejected')

    def app_key_producer(self, config, key):
        source = self.OBJECT + 0x10000
        self.parse_object(source, {'app_key': key})
        rsp, rbp = 0x300078000, 0x300078100
        self.call(0x14d2b0, rbp + 0x160, source)
        self.trace, self.fragment_end = [], 0x378e70
        for reg, value in [(ux.UC_X86_REG_RSP, rsp), (ux.UC_X86_REG_RBP, rbp),
                           (ux.UC_X86_REG_R15, config), (ux.UC_X86_REG_R13, 0),
                           (ux.UC_X86_REG_RSI, 0xffffffffffffffff)]:
            self.uc.reg_write(reg, value)
        self.uc.emu_start(self.pe.base + 0x378dda, self.STOP, count=2_000_000)
        if self.uc.reg_read(ux.UC_X86_REG_RIP) != self.pe.base + self.fragment_end:
            raise ValueError('Original app_key writer missed its exact exit')
        self.fragment_end = None
        steps = len(self.trace)
        equal = self.string(config + 0x218) == key.encode('utf-8')
        self.STACK = 0x3000c0008
        for address in [rbp + 0x160, source]:
            self.call(0x14d450, address)
        if not equal:
            raise ValueError('Original app_key producer differs from its input')
        return {'entryRva': '0x378dda', 'exitRva': '0x378e70',
                'configOffset': '0x218', 'configModelOffset': '0x130',
                'nativeInstructions': steps, 'appKeyEqual': equal,
                'wholeInitExecuted': False, 'nativeJsonCleanupReturned': True}


def canonical(pairs):
    """Independent byte comparator and exact delimiters; no JSON/URL processing."""
    return b'&'.join(name + b'=' + value for name, value in sorted(dict(pairs).items()))


def oracle(pairs, key):
    return hmac.new((key or b'').split(b'\0', 1)[0], canonical(pairs), hashlib.sha256).hexdigest().encode('ascii')


def synthetic_cases():
    data = json.dumps({'uid': str((1 << 53) + 17), 'token': 'synthetic-only', 'guest': False},
                      separators=(',', ':')).encode()
    pairs = [(b'device', b'synthetic-device'), (b'data', data),
             (b'channel_id', b'1'), (b'app_id', b'4')]
    key = b'synthetic-key-only'
    nested = json.dumps({'z': [0, (1 << 63) - 1, {'unicode': '中文é😀', 'nil': None}],
                         'uid': str((1 << 53) + 17), 'guest': False},
                        ensure_ascii=False, separators=(',', ':')).encode()
    return [
        ('combo-insertion-reverse', pairs, key),
        ('combo-insertion-sorted', list(reversed(pairs)), key),
        ('nested-unicode-int64', [(b'data', nested), (b'device', '设备'.encode()),
                                 (b'app_id', b'4'), (b'channel_id', b'1')], key),
        ('empty-map-empty-key', [], b''),
        ('key-64-byte-boundary', [(b'x', b'boundary')], b'K' * 64),
        ('key-65-byte-prehash', [(b'x', b'boundary')], b'K' * 65),
        ('null-key-pointer', [(b'x', b'boundary')], None),
        ('embedded-nul-key-value', [(b'x', b'a\0b'), (b'a\0z', b'v')], b'head\0tail'),
        ('raw-delimiters-no-escape', [(b'b', b'x&y=z%20'), (b'a=', b'&')], key),
        ('byte-lexical-prefix-unicode', [(b'a\0b', b'0'), (b'aa', b'1'), (b'a', b'2'),
                                         ('é'.encode(), b'3'), ('中'.encode(), b'4'),
                                         (b'A', b'5')], key),
        ('large-string-aligned-allocation', [(b'data', b'X' * 4096)], key),
        ('value-byte-mutation', [(b'x', b'boundary!')], b'K' * 64),
    ]


def native_cases(pe):
    results = []
    outputs = []
    negative = []
    for label, pairs, key in synthetic_cases():
        env = SignatureFixture(pe)
        mapping = env.make_map(pairs)
        key_pointer, output = env.OBJECT + 0x4000, env.OBJECT + 0x5000
        if key is not None:
            env.uc.mem_write(key_pointer, key + b'\0')
        returned = env.call(0x3958e0, env.OBJECT, output, mapping, 0 if key is None else key_pointer)
        value = env.string(output)
        expected_input = (canonical(pairs), (key or b'').split(b'\0', 1)[0])
        if env.signer_inputs != [expected_input] or value != oracle(pairs, key) or returned != output:
            raise ValueError('Original signer/oracle differs in case: ' + label)
        outputs.append(value)
        negative.append({
            'case': label,
            'changedKeyDoesNotMatch': value != oracle(pairs, b'!' + (key or b'')),
            'changedDataByteDoesNotMatch': value != hmac.new(expected_input[1], expected_input[0] + b'!', hashlib.sha256).hexdigest().encode(),
        })
        results.append({'case': label, 'inputFields': len(pairs),
                        'canonicalBytes': len(expected_input[0]), 'keyBytesAtNativeHmac': len(expected_input[1]),
                        'originalCanonicalBytesEqual': True, 'oracleEqual': True,
                        'outputBytes': len(value), 'lowercaseHex64': len(value) == 64 and all(x in b'0123456789abcdef' for x in value),
                        'returnedCallerOutputPointer64': returned == output and returned > 0xffffffff,
                        'nativeInstructions': len(env.trace), 'balancedReturn': True,
                        'originalEntries': {hex(rva): env.trace.count(rva) for rva in
                                            [0x3958e0, 0x395900, 0x396020, 0x3b8080, 0x3ba2f0, 0x3ba3e0]},
                        'models': dict(sorted(Counter(x['model'] for x in env.events).items()))})
        env.call(0x1380e0, output)
    if outputs[0] != outputs[1] or outputs[4] == outputs[11] or not all(
            row['changedKeyDoesNotMatch'] and row['changedDataByteDoesNotMatch'] for row in negative):
        raise ValueError('Independent insertion-order or changed-byte check failed')
    return {'cases': results, 'negativeChecks': negative,
            'insertionOrderDoesNotChangeSignature': outputs[0] == outputs[1],
            'changedValueChangesSignature': outputs[4] != outputs[11]}


def producer_case(pe):
    env = SignatureFixture(pe)
    original = next(row for row in json.loads(CONFIG.read_bytes()) if row['env'] == 0)
    return {'sourceAsset': 'assets/plugins/mihoyosdk/resources/config/server_pc.json',
            'sourceEnvProjection': 0, 'naturalEnvSelected': False,
            **env.app_key_producer(env.OBJECT, original['app_key']),
            'appKeyBytesLengthOrHashRecorded': False, 'signerInvokedWithResourceKey': False}


def builder_case(pe):
    env = SignatureFixture(pe)
    aggregate, config, crypto, combo = (env.OBJECT + n for n in [0, 0x20000, 0x30000, 0x40000])
    env.put(pe.base + 0x7acc80, aggregate)
    env.put(aggregate + 0x40, config)
    env.put(aggregate + 0x38, crypto)
    network = env.OBJECT + 0xc0000
    env.put(aggregate + 0x10, network)
    env.put(network, pe.base + 0x6d1f48)
    env.put(config, pe.base + 0x6c6cd8)
    env.put(crypto, pe.base + 0x6d1cb8)
    key = 'synthetic-key-only'
    producer = env.app_key_producer(config, key)
    app, channel, device = b'4', b'1', 'synthetic-device-设备'.encode()
    for offset, value in [(0x1f8, app), (0x150, channel), (0xa8, device)]:
        env.put(config + offset + 0x18, 15)
        env.set_string(config + offset, value)
    urls = env.make_map([(b'comboLogin', b'https://fixture.invalid/combo')], env.OBJECT + 0x90000)
    env.put(config + 0x860, urls)
    inner = json.dumps({'uid': str((1 << 53) + 17), 'token': 'synthetic-only',
                        'guest': False, 'nested': {'unicode': '中文é😀', 'n': (1 << 63) - 1}},
                       ensure_ascii=False, separators=(',', ':')).encode()
    caller = env.OBJECT + 0xb0000
    env.uc.mem_write(caller, inner + b'\0')
    env.events = []
    # Run from the real entry to the exact network call. Do not fake a send result.
    env.trace, env.fragment_end = [], 0x3bec2c
    env.uc.mem_write(env.STACK - 0x10000, bytes(0x11000))
    env.put(env.STACK, env.STOP)
    env.uc.reg_write(ux.UC_X86_REG_RSP, env.STACK)
    env.uc.reg_write(ux.UC_X86_REG_RCX, combo)
    env.uc.reg_write(ux.UC_X86_REG_RDX, caller)
    env.uc.emu_start(pe.base + 0x3be5c0, env.STOP, count=2_000_000)
    if env.uc.reg_read(ux.UC_X86_REG_RIP) != pe.base + 0x3bec2c:
        raise ValueError('Builder missed the exact request boundary')
    expected_pairs = [(b'app_id', app), (b'channel_id', channel), (b'device', device), (b'data', inner)]
    expected_sign = oracle(expected_pairs, key.encode()).decode('ascii')
    expected_outer = {name.decode('ascii'): value.decode('utf-8') for name, value in expected_pairs}
    expected_outer['sign'] = expected_sign
    expected_body = json.dumps(expected_outer, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()
    actual_outer = json.loads(env.observed_body)
    if (actual_outer != expected_outer or env.signer_inputs != [(canonical(expected_pairs), key.encode())]
            or env.observed_body != expected_body or env.builder_sign_call['targetRva'] != 0x3958e0
            or not env.builder_sign_return['returnsCallerOutputPointer64']):
        raise ValueError('Original builder input/signature/serialization differs from independent oracle')
    route_equal = env.cstring(env.uc.reg_read(ux.UC_X86_REG_RDX)) == b'https://fixture.invalid/combo'
    target = env.get(env.uc.reg_read(ux.UC_X86_REG_RAX) + 0x10) - pe.base
    if not route_equal or target != 0x39c310:
        raise ValueError('Original builder request target or route differs')
    wrong_order = b'&'.join(name + b'=' + value for name, value in reversed(sorted(expected_pairs)))
    reserialized = json.dumps(json.loads(inner), ensure_ascii=True, sort_keys=True).encode()
    changed_pairs = [(name, reserialized if name == b'data' else value) for name, value in expected_pairs]
    wrong_message = hmac.new(key.encode(), expected_body, hashlib.sha256).hexdigest()
    negative = {
        'wholeOuterJsonDoesNotMatch': wrong_message != expected_sign,
        'reverseFieldOrderDoesNotMatch': hmac.new(key.encode(), wrong_order, hashlib.sha256).hexdigest() != expected_sign,
        'reserializedInnerDataDoesNotMatch': oracle(changed_pairs, key.encode()).decode() != expected_sign,
        'includingSignFieldDoesNotMatch': oracle(expected_pairs + [(b'sign', expected_sign.encode())], key.encode()).decode() != expected_sign,
    }
    if not all(negative.values()):
        raise ValueError('Independent incorrect-input alternatives unexpectedly match')
    return {'entryRva': '0x3be5c0', 'exitBeforeCallRva': '0x3bec2c', 'wholeBuilderReturned': False,
            'appKeyProducer': producer, 'signCallRva': '0x3bea13', 'signReturnRva': '0x3bea16',
            'signCall': env.builder_sign_call, 'signReturn': env.builder_sign_return,
            'originalSignerReturned': True,
            'originalCanonicalBytesEqual': True, 'signatureOracleEqual': True,
            'dataCStringBytesPreserved': actual_outer['data'].encode('utf-8') == inner,
            'outerFields': list(actual_outer), 'allOuterFieldValuesAreStrings': all(isinstance(x, str) for x in actual_outer.values()),
            'outerJsonCompactUtf8ByteEqual': True, 'outerJsonBytes': len(env.observed_body),
            'incorrectInputAlternatives': negative,
            'nativeInstructions': len(env.trace), 'exactCallerBoundaryReached': True,
            'queuedRequests': 0, 'nativeRequestUrlEqual': True,
            'networkVtableRva': '0x6d1f48', 'networkSlotByteOffset': '0x10',
            'networkTargetRva': hex(target),
            'originalEntries': {hex(rva): env.trace.count(rva) for rva in
                                [0x3be5c0, 0x3bea13, 0x3bea16, 0x36aa70, 0x3958e0, 0x395900, 0x396020,
                                 0x376ea0, 0x364910, 0x140940, 0x152a20, 0x2f4290]},
            'models': dict(sorted(Counter(x['model'] for x in env.events).items())),
            'networkExecuted': False, 'naturalEnvSelected': False,
            'naturalSignatureObserved': False}


def read_rva(pe, rva, size):
    address = pe.base + rva
    offset = next(raw + address - start for start, length, raw, _ in pe.sections
                  if start <= address and address + size <= start + length)
    return pe.data[offset:offset + size]


def static_proof(pe):
    ranges = [(0x3958e0, 0x3958fc), (0x395900, 0x395cf7), (0x396020, 0x3963e4),
              (0x136eb0, 0x136f1d), (0x3be5c0, 0x3bed42), (0x378dda, 0x378e70)]
    code = [{'startRva': hex(start), 'endRvaExclusive': hex(end),
             'codeSHA256': digest(read_rva(pe, start, end - start)),
             'instructions': [{'rva': hex(va - pe.base), 'size': size, 'mnemonic': mnemonic, 'operands': operands}
                              for va, size, mnemonic, operands in pe.instructions(start, end)]}
            for start, end in ranges]
    values = {'equalsLiteralRva': '0x6af09c', 'equalsLiteralExact': read_rva(pe, 0x6af09c, 2) == b'=\0',
              'ampersandLiteralRva': '0x6a8b70', 'ampersandLiteralExact': read_rva(pe, 0x6a8b70, 2) == b'&\0',
              'hexAlphabetRva': '0x6c11c0', 'lowercaseHexAlphabetExact': read_rva(pe, 0x6c11c0, 17) == b'0123456789abcdef\0',
              'hmacInitialPadRva': '0x6d9900', 'hmacInitialPad36Exact': read_rva(pe, 0x6d9900, 16) == b'\x36' * 16,
              'hmacPadTransitionRva': '0x6d9940', 'hmacPadTransition6aExact': read_rva(pe, 0x6d9940, 16) == b'\x6a' * 16,
              'signerVtableTargetRva': hex(int.from_bytes(read_rva(pe, 0x6d1cb8 + 0x58, 8), 'little') - pe.base)}
    if not all(value for key, value in values.items() if key.endswith('Exact')) or values['signerVtableTargetRva'] != '0x3958e0':
        raise ValueError('Original signer literals/table differ')
    return {'codeWindows': code, 'literalAndVtableChecks': values,
            'inheritedBuilderReaEvidenceId': 'ev_54efe118b813c73c4b03ac2399d822a646d2593edb18eb2a30d8e14371c4e802',
            'newReaSessionOpened': False}


def verify_inputs(client):
    for name, expected in FIXED.items():
        if digest((ROOT / name).read_bytes()) != expected:
            raise ValueError('Frozen source/evidence changed: ' + name)
    result = dict(FIXED)
    for row in json.loads((ROOT / INVENTORY).read_bytes())['inputs']:
        file = client / row['file']
        with file.open('rb') as stream:
            actual = hashlib.file_digest(stream, 'sha256').hexdigest()
        if actual != row['sha256'] or file.stat().st_size != row['size']:
            raise ValueError('Fixed client identity changed: ' + row['file'])
        result['GAME_PATH/' + row['file']] = actual
    carrier = 'YuanShen_Data/StreamingAssets/MiHoYoSDKRes/PC/mihoyo_sdk_res'
    actual = digest((client / carrier).read_bytes())
    if actual != '8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda':
        raise ValueError('Accurate source resource carrier changed')
    result['GAME_PATH/' + carrier] = actual
    route = json.loads((ROOT / ROUTE_REPORT).read_bytes())
    text = route['asset']['textAsset']
    payload = CONFIG.with_name('mihoyo_sdk_res.serialized.private.bin')
    with payload.open('rb') as stream:
        stream.seek(text['textOffset'])
        extracted = stream.read(text['textBytes'])
    if CONFIG.read_bytes() != extracted or text['textBytes'] != 475950:
        raise ValueError('Private resource projection is no longer byte-equal to the frozen extracted range')
    return result


def java_contract():
    manifest = json.loads((ROOT / JAVA_MANIFEST).read_bytes())
    sources = {}
    current = {}
    for name, identity in manifest.items():
        data = (RAW / 'java-epoch0' / name).read_bytes()
        if digest(data) != identity['sha256'] or len(data) != identity['bytes']:
            raise ValueError('Pinned receiver snapshot changed: ' + name)
        sources[name] = data.decode('utf-8')
        current[name] = digest((ROOT / name).read_bytes()) == identity['sha256']
    auth = sources['src/main/java/emu/grasscutter/auth/DefaultAuthenticators.java']
    combo = auth.split('public static class SessionKeyAuthenticator', 1)[1].split('public static class ExternalAuthentication', 1)[0]
    handler = sources['src/main/java/emu/grasscutter/server/http/dispatch/AuthenticationHandler.java']
    body = handler.split('private static void sessionKeyLogin(Context ctx)', 1)[1].split('/**', 1)[0]
    if '.sign' in body or '.sign' in combo or combo.count('requestData') != 2:
        raise ValueError('Receiver snapshot has an unreviewed outer-field consumer')
    return {'sourceEpoch': 'java-epoch0', 'sources': manifest, 'currentSourceMatchesEpoch': current,
            'evidenceKind': 'source contract; Java/HTTP/DB execution not performed by this card',
            'route': '/hk4e_cn/combo/granter/login/v2/login',
            'outerDtoFields': ['app_id:int', 'channel_id:int', 'data:String', 'device:String', 'sign:String'],
            'outerSignConsumedOrVerifiedByDefaultAuthenticator': False,
            'invalidMissingOrChangedSignRejectedByThisSourceContract': False,
            'innerDataDecodedOnceFromString': True,
            'handlerEarlyReturn': ['outer DTO null', 'data null', 'data empty', 'inner DTO null'],
            'successCondition': 'account exists AND (passwordRequired=false OR exact nonblank stored sessionKey equals inner token)',
            'lenientBranchCanAdoptSuppliedSessionKey': True,
            'failureRetcode': -201, 'successRetcode': 0,
            'successFields': ['open_id', 'combo_id', 'combo_token', 'data'],
            'pluginReplacementOrRuntimeBindingObserved': False, 'actualHttpAcceptanceExecuted': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('Require a fresh report path inside this card raw directory')
    fixed = verify_inputs(args.client)
    data = (args.client / SDK_FILE).read_bytes()
    if digest(data) != SDK_SHA:
        raise ValueError('Fixed SDK identity changed')
    pe = Image(data)
    result = {'schemaVersion': 1, 'task': 'R2-ROUTE-SIGN', 'fixedInputs': fixed,
              'static': static_proof(pe), 'nativeSigner': native_cases(pe),
              'resourceKeyProducer': producer_case(pe), 'builder': builder_case(pe),
              'serverContract': java_contract(), 'sourceSHA256': digest(Path(__file__).read_bytes()),
              'payloadsOrKeysOrSignaturesOrTheirHashesRecorded': False,
              'wholeBuilderExecuted': False, 'naturalSignatureObserved': False,
              'models': [
                  'All original canonicalizer/SHA-256/HMAC/hex instructions execute; no digest or success result is modeled.',
                  'Only bounded allocation/free storage and inherited single-thread OS models are available; reports list every model hit.',
                  'Ordered-map sentinel, component roots, cached device string and config fields are explicit caller inputs.',
                  'Actual app_key writer executes on one exact resource row; only equality is reported and its key is not passed to signer.',
                  'The builder starts at its real entry and stops before 3bec2c, the exact NetworkManager virtual call; no call/return is fabricated.',
                  'Natural selected environment, initialization payload, caller data and HTTP acceptance remain unobserved.',
              ],
              'remaining': [
                  'Whole builder return/network submission is not covered. An exploratory actual NetworkManager path stopped at an indirect request-information dependency at 3afb09.',
                  'The first exploratory fixture used an incorrect engine vtable as NetworkManager; the accepted sample instead binds exact 6d1f48 and stops before the send call.',
                  'Current default Java source ignores sign; this card does not enable a new signature admission gate.',
                  'Natural input/source identity and the actual server/runtime authentication binding still require mainline observation.',
              ],
              'productionCodeChanged': False, 'clientExecuted': False, 'd02Passed': False}
    if verify_inputs(args.client) != fixed:
        raise ValueError('Fixed inputs changed during execution')
    result['fixedInputsRecheckedAfterExecution'] = True
    result['testSourceSHA256'] = digest((ROOT / 'tools/test_client72_sdk_combo_signature.py').read_bytes())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    print(json.dumps({'task': result['task'], 'cases': len(result['nativeSigner']['cases']),
                      'oracleAllEqual': True, 'naturalSignatureObserved': False}))
