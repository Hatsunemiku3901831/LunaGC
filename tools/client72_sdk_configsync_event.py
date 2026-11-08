"""Owned-memory classifier for the CN72 ConfigSync local_config event.

Classifier handles owned bytes only. The separate native proof runs a fixed SDK
in an offline emulator and emits redacted code, path and ABI evidence.
"""
from __future__ import annotations

import json
import math
try:
    from .client72_sdk_natural_config_contract import CN_ENVS
except ImportError:
    from client72_sdk_natural_config_contract import CN_ENVS

FIXED_FIXTURES = {
    'tools/client72_sdk_route_loader.py': 'd4816a4448ef2533d20ab7bc656ef617888a79ded69fb3416c5defe24fdf6cac',
    'tools/client72_sdk_login_chain.py': '53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2',
    'tools/client72_sdk_response_contract.py': '792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5',
    'tools/client72_sdk_transport.py': '43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237',
    'tools/client72_sdk_natural_config_contract.py': 'b5b86b31f5fb46736fae1e68168ad3298a73b501607b75d9cc2ca8214d9ae6ba',
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'local/migration72/python/capstone/lib/capstone.dll': '76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
    'local/migration72/python/unicorn/lib/unicorn.dll': '010d85dce84b8f6ffaec3d5420f1b5f7768f859eb831be574c7da23e656ee8f1',
}


def classify_owned_event(name: bytes, payload: bytes, references) -> dict:
    """Classify owned bytes using pinned resource rows supplied by the caller.

    The current code does not establish the lifetime of a natural callback pointer.
    Failures return fixed status values and never include source bytes or exceptions.
    """
    if name != b'local_config':
        return {'status': 'not-local-config'}
    try:
        event = json.loads(payload)
        if not isinstance(event, dict):
            return {'status': 'unsupported-shape'}
        env = event.get('env')
        if type(env) not in (int, float) or not math.isfinite(env) or int(env) != env:
            return {'status': 'unsupported-env'}
        env = int(env)
        if env not in CN_ENVS:
            return {'status': 'unsupported-env'}
        rows = [row for row in references if row.env == env]
        if len(rows) != 1:
            return {'status': 'reference-unavailable'}
        app_key = event.get('app_key')
        url = event.get('url')
        if not isinstance(app_key, str) or not isinstance(url, str):
            return {'status': 'unsupported-shape'}
        routes = json.loads(url)
        if not isinstance(routes, dict) or not isinstance(routes.get('comboLogin'), str):
            return {'status': 'unsupported-shape'}
        return {'status': 'classified-owned', 'env': 'cn-env-' + str(env),
                'appKeyMatches': app_key.encode('utf-8') == rows[0].app_key,
                'comboUrlMatches': routes['comboLogin'].encode('utf-8') == rows[0].combo_url}
    except (UnicodeError, json.JSONDecodeError, ValueError, OverflowError):
        return {'status': 'invalid-json'}

class ConfigSyncFixture:
    """Factory wrapper keeps native fixture imports out of the pure classifier."""

    @staticmethod
    def create(image):
        import ctypes
        import sys
        from pathlib import Path
        root = Path(__file__).resolve().parents[1]
        sys.path.insert(0, str(root / 'local/migration72/python'))
        try:
            from .client72_sdk_route_loader import RouteFragments
        except ImportError:
            from client72_sdk_route_loader import RouteFragments
        from unicorn import x86_const as ux

        api = ctypes.WinDLL('kernel32', use_last_error=True).MultiByteToWideChar
        api.argtypes = [ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_int,
                        ctypes.c_void_p, ctypes.c_int]
        api.restype = ctypes.c_int
        stub = 0x710000000

        class OwnedFixture(RouteFragments):
            def __init__(self, pe):
                super().__init__(pe)
                self.uc.mem_map(stub, 0x1000)
                self.uc.mem_write(stub, b'\xc3')
                self.put(pe.base + 0x5fb2b8, stub)
                self.conversion_count = 0
                self.conversion_records = []

            def on_code(self, uc, address, size, user):
                if address != stub:
                    return super().on_code(uc, address, size, user)
                self.trace.append(address - self.pe.base)
                self.conversion_count += 1
                code_page = uc.reg_read(ux.UC_X86_REG_RCX) & 0xffffffff
                flags = uc.reg_read(ux.UC_X86_REG_RDX) & 0xffffffff
                source = uc.reg_read(ux.UC_X86_REG_R8)
                count = ctypes.c_int32(uc.reg_read(ux.UC_X86_REG_R9) & 0xffffffff).value
                stack = uc.reg_read(ux.UC_X86_REG_RSP)
                destination = self.get(stack + 0x28)
                capacity = ctypes.c_int32(self.get(stack + 0x30, 4)).value
                if count == -1:
                    copied = self.cstring(source) + b'\0'
                elif 0 <= count <= 8192:
                    copied = bytes(uc.mem_read(source, count))
                else:
                    raise ValueError('owned-conversion-input-outside-budget')
                if capacity < 0 or capacity > 8192 or (not destination and capacity):
                    raise ValueError('owned-conversion-output-outside-budget')
                input_buffer = ctypes.create_string_buffer(copied, len(copied))
                output_buffer = (ctypes.c_uint16 * capacity)() if destination else None
                ctypes.set_last_error(0)
                result = api(code_page, flags, input_buffer, count, output_buffer, capacity)
                record = {'codePage': code_page, 'flags': flags, 'sourceCount': count,
                          'capacity': capacity, 'result': result,
                          'inputCopyMatches': bytes(input_buffer) == copied,
                          'outputBufferSupplied': bool(destination), 'outputCopyMatches': None}
                self.conversion_records.append(record)
                if result == 0:
                    record['status'] = 'system-conversion-failed'
                    raise ValueError('system-conversion-failed')
                if destination:
                    output = bytes(output_buffer)[:result * 2]
                    uc.mem_write(destination, output)
                    record['outputCopyMatches'] = bytes(uc.mem_read(destination, result * 2)) == output
                record['status'] = 'returned'
                uc.reg_write(ux.UC_X86_REG_RAX, result)
                uc.reg_write(ux.UC_X86_REG_RIP, self.get(stack))
                uc.reg_write(ux.UC_X86_REG_RSP, stack + 8)

            def run_to_writer(self, config, name, payload):
                self.pending, self.native_calls, self.trace = [], [], []
                self.uc.mem_write(self.STACK - 0x10000, bytes(0x11000))
                self.put(self.STACK, self.STOP)
                for register, value in ((ux.UC_X86_REG_RSP, self.STACK),
                                        (ux.UC_X86_REG_RCX, config),
                                        (ux.UC_X86_REG_RDX, name),
                                        (ux.UC_X86_REG_R8, payload)):
                    self.uc.reg_write(register, value)
                self.stop_at = 0x378850
                self.uc.emu_start(self.pe.base + 0x36fba0, self.STOP, count=2000000)
                if self.uc.reg_read(ux.UC_X86_REG_RIP) != self.pe.base + self.stop_at:
                    raise ValueError('writer-entry-cut-not-reached')
                return (self.uc.reg_read(ux.UC_X86_REG_RCX) == config,
                        self.uc.reg_read(ux.UC_X86_REG_RDX) == payload)

        return OwnedFixture(image)


class ProofFailure(Exception):
    def __init__(self, proof):
        self.proof = proof
        super().__init__('native-proof-stopped')


def trace_record(fixture, expected_stop):
    import hashlib
    import struct
    from unicorn import x86_const as ux
    rip = fixture.uc.reg_read(ux.UC_X86_REG_RIP)
    stop = 'return' if rip == fixture.STOP else (
        hex(rip - fixture.pe.base) if fixture.pe.base <= rip < fixture.pe.base + 0xa00000 else 'external')
    path = b''.join(struct.pack('<q', rva) for rva in fixture.trace)
    last_native = next((rva for rva in reversed(fixture.trace) if 0 <= rva < 0xa00000), None)
    return {'instructions': len(fixture.trace), 'instructionPathSha256': hashlib.sha256(path).hexdigest(),
            'lastNativeInstructionRva': hex(last_native) if last_native is not None else None,
            'expectedStop': expected_stop, 'actualStop': stop,
            'entryStackOffset': 0,
            'stopStackOffset': fixture.uc.reg_read(ux.UC_X86_REG_RSP) - fixture.STACK}


def sdk_windows(raw):
    import hashlib
    try:
        from .client72_sdk_natural_config_contract import SdkImage
    except ImportError:
        from client72_sdk_natural_config_contract import SdkImage
    sdk = SdkImage(raw)
    rows = []
    for address in (0x13d5b0, 0x1343b0, 0x3637d0, 0x3751e0, 0x36fba0, 0x378850):
        start, end = sdk.body(address)
        if start != address:
            raise ValueError('sdk-window-entry-mismatch')
        rows.append({'startRva': hex(start), 'endRvaExclusive': hex(end),
                     'sha256': hashlib.sha256(sdk.at(start, end - start)).hexdigest()})
    for start, end in ((0x36e4e0, 0x36e4e6), (0x143055, 0x14305b)):
        rows.append({'startRva': hex(start), 'endRvaExclusive': hex(end),
                     'sha256': hashlib.sha256(sdk.at(start, end - start)).hexdigest()})
    return rows


def verify_native_owned(sdk_path):
    """Execute the fixed constructor and dispatch on owned Unicorn memory only."""
    import hashlib
    from pathlib import Path
    root = Path(__file__).resolve().parents[1]
    for relative, expected in FIXED_FIXTURES.items():
        if hashlib.sha256((root / relative).read_bytes()).hexdigest() != expected:
            raise ValueError('fixture-identity-mismatch')
    try:
        from .client72_sdk_transport import Image
    except ImportError:
        from client72_sdk_transport import Image

    raw = Path(sdk_path).read_bytes()
    sdk_hash = hashlib.sha256(raw).hexdigest()
    if sdk_hash != 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25':
        raise ValueError('sdk-identity-mismatch')
    image = Image(raw)
    fixture = ConfigSyncFixture.create(image)
    proof = {'schemaVersion': 2, 'status': 'running', 'sdkSha256': sdk_hash,
             'sourceSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
             'dependencies': FIXED_FIXTURES, 'sdkWindows': sdk_windows(raw),
             'calls': {}, 'osConversions': fixture.conversion_records}
    config = fixture.OBJECT + 0x10000
    stage = 'constructor'
    try:
        constructor_return = fixture.call(0x3637d0, config)
        proof['calls'][stage] = trace_record(fixture, 'return')
        if constructor_return != config or fixture.get(config + 0xa88) != 7 or fixture.conversion_count != 6:
            raise ValueError('constructor-fixture-contract-mismatch')
        name, payload = fixture.OBJECT + 0xd000, fixture.OBJECT + 0xe000
        fixture.uc.mem_write(payload, b'{"env":0,"app_key":"owned-fixture","url":"{}"}\0')
        fixture.stop_at = 0x378850
        for stage, other_name in (('negativeUnregistered', b'unregistered_fixture\0'),
                                  ('negativePrefix', b'local_config_extra\0')):
            fixture.uc.mem_write(name, other_name)
            fixture.call(0x36fba0, config, name, payload)
            proof['calls'][stage] = trace_record(fixture, 'return')
            if 0x378850 in fixture.trace or 0x36e4e0 in fixture.trace:
                raise ValueError('unregistered-name-reached-writer')
        stage = 'positiveWriterEntryCut'
        fixture.uc.mem_write(name, b'local_config\0')
        config_match, payload_match = fixture.run_to_writer(config, name, payload)
        proof['calls'][stage] = trace_record(fixture, '0x378850')
        proof['calls'][stage]['configPointerEqual'] = config_match
        proof['calls'][stage]['payloadPointerEqual'] = payload_match
        if not config_match or not payload_match or 0x36e4e0 not in fixture.trace:
            raise ValueError('registered-dispatch-mismatch')
    except Exception as error:
        proof['status'] = 'stopped'
        known = {'system-conversion-failed', 'owned-conversion-input-outside-budget',
                 'owned-conversion-output-outside-budget', 'constructor-fixture-contract-mismatch',
                 'unregistered-name-reached-writer', 'registered-dispatch-mismatch',
                 'writer-entry-cut-not-reached'}
        reason = str(error) if str(error) in known else type(error).__name__
        proof['firstStop'] = {'stage': stage, 'errorKind': type(error).__name__,
                              'reason': reason, 'trace': trace_record(fixture, 'unknown')}
        raise ProofFailure(proof) from error
    proof['status'] = 'owned-dispatch-entry-verified'
    proof['constructorReturned'] = True
    proof['unregisteredReturnedWithoutWriter'] = True
    proof['registeredReachedWriterEntry'] = True
    proof['writerArgumentsMatch'] = True
    return proof


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, help='path to the fixed SDK file, read only')
    args = parser.parse_args()
    try:
        result = verify_native_owned(args.sdk)
    except ProofFailure as error:
        print(json.dumps(error.proof, sort_keys=True))
        raise SystemExit(1)
    except (ValueError, OSError) as error:
        reason = str(error) if str(error) in ('sdk-identity-mismatch', 'fixture-identity-mismatch', 'sdk-window-entry-mismatch') else type(error).__name__
        print(json.dumps({'status': 'stopped-before-execution', 'reason': reason}))
        raise SystemExit(1)
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
