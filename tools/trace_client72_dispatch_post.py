"""Signed dispatch JSON through nested reader and post-parse config call entry.

Does not execute config consumption, B0 storage, HTTP or the actual client.
"""
import base64
import hashlib
import json
from pathlib import Path
try:
    from .trace_client72_dispatch_prefix import DispatchPrefix72, ENTRY, SUCCESS, REJECT
    from .extract_client72 import HASHES, read_verified, extract, PE
    from .client72_emulator import Offline72
    from .client72_reader import WireInput
    from .client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from .client72_seed_crypto import rsa_public_numbers
    from .trace_client72_auth_sources import parse_args
except ImportError:
    from trace_client72_dispatch_prefix import DispatchPrefix72, ENTRY, SUCCESS, REJECT
    from extract_client72 import HASHES, read_verified, extract, PE
    from client72_emulator import Offline72
    from client72_reader import WireInput
    from client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from client72_seed_crypto import rsa_public_numbers
    from trace_client72_auth_sources import parse_args

CONFIG, ERROR_ROUTE = 0x151204AC0, 0x151211BDD
REGION_CELL, REGION_READER = 0x1458DEE48, 0x149BB4EB0
# Independent literal keys: response14/string, response3/message, region2/uint32,
# region13/string, response12/bytes, response11/bytes, response1/int32.
CASES = (
    ('nonzero', '7207666978747572651a0a10ac026a05696e6e657262036366675a036b6579', 'config'),
    ('empty-region', '72001a00', 'config'),
    ('uint32-max', '7201391a0610ffffffff0f', 'config'),
    ('merge-region', '7207666978747572651a0310ac021a076a05696e6e6572', 'config'),
    ('missing-region', '720766697874757265', 'error-route'),
    ('negative-ret', '08ffffffffffffffffff017201391a00', 'error-route'),
    ('truncated-region', '7201391a0a10ac02', 'wire-reject'),
    ('truncated-scalar', '7201391a021080', 'wire-reject'),
)


class DispatchPost72(DispatchPrefix72):
    def __init__(self, e, binary, metadata, plain, signer):
        super().__init__(e, binary, metadata, plain, signer, True)
        klass = e.synthetic_type(REGION_CELL)
        self.classes[klass] = 67961
        interface = e.alloc(); table = e.alloc(16)
        e.write(0x1458AAD80, interface)
        e.write(klass+0x80, table); e.write(klass+0xC0, 1, 'H'); e.write(klass+0xBE, 32, 'H')
        e.write(table, interface); e.write(table+8, 0, 'I'); e.write(klass+0xE8, REGION_READER)
        e.synthetic_type(0x1458C3CD0)
        e.models[0x1405336C0] = lambda: self.allocate() if e.reg('rcx') == klass else self.allocate_dispatch_environment()
        e.models[0x14051A870] = lambda: 0
        for address in (REGION_READER, 0x1474D7E60, 0x14751FFB0, 0x1475210F0,
                        0x147521760, 0x147521000, 0x147CF3070, 0x147CF2D60):
            e.allow(address)
        for address in (CONFIG, ERROR_ROUTE): e.allowed.append((address, address+1))
        e.models.update({0x14751FD30: lambda: self.read_wire('raw'),
                         0x1475208D0: lambda: self.read_wire('bytes'),
                         0x147520110: lambda: self.read_wire('scalar')})
        for address, reason in ((0x1509CB360, 'truncated-message'), (0x1509CAFA0, 'negative-length'),
                                (0x1509CB410, 'recursion-limit'), (0x1509CB200, 'invalid-end-tag')):
            e.models[address] = lambda reason=reason: self.reject_wire(reason)
        self.post_pointer = False; self.region_calls = 0; self.config_inputs = None
        self.wire_error = None

    def read_wire(self, kind):
        e = self.e; stream = e.reg('rcx')
        if stream not in self.inputs:
            array = e.read(stream+24)
            self.inputs[stream] = WireInput(bytes(e.uc.mem_read(array+32, e.read(array+24))))
        inp = self.inputs[stream]
        inp.position = e.read(stream+0x44, 'I')
        inp.limit = min(len(inp.data), e.read(stream+0x38, 'I'), e.read(stream+0x40, 'I'))
        try:
            if kind == 'raw': result = inp.take(1)[0]
            elif kind == 'bytes': result = self.byte_string(inp.blob())
            elif kind == 'scalar': result = inp.varint() & 0xffffffff
            else: return super().read_wire(kind)
        except ValueError:
            self.wire_error = 'bounded-wire-helper'
            raise
        e.write(stream+0x44, inp.position, 'I')
        return result

    def reject_wire(self, reason):
        self.wire_error = 'native-'+reason
        raise ValueError(self.wire_error)

    def observe(self, address):
        e = self.e
        if address == SUCCESS:
            self.post_pointer = e.read(e.read(e.reg('rbp')+0xA8)) == self.parsed
            if not self.post_pointer: raise ValueError('Post-parse object pointer changed')
            # Continue this same execution; do not skip the post-parse instructions.
            self.digest.update(address.to_bytes(8, 'little')); self.visits[address] += 1
            return
        super().observe(address)
        if address == REGION_READER: self.region_calls += 1
        if address == CONFIG:
            args = [e.reg(k) for k in ('rdx', 'r8')]
            self.config_inputs = dict(receiverPreserved=e.reg('rcx') == self.sender,
                                      mode=e.reg('r9d'), arrays=[bytes(e.uc.mem_read(p+32, e.read(p+24))).hex() for p in args])
        if address in (CONFIG, ERROR_ROUTE): self.stop = address; e.uc.emu_stop()

    def run_post(self, payload):
        try: result = self.run_prefix(payload)
        except ValueError:
            if self.wire_error is None: raise
            result = dict(stop=None, wireError=self.wire_error)
        region = self.e.read(self.parsed+72) if self.parsed else 0
        region_value = None
        if region:
            region_value = dict(typeIndex=self.allocations.get(region), uint32=self.e.read(region+316, 'I'),
                                text=self.text(self.e.read(region+48)))
        return dict(result, postParsePointerPreserved=self.post_pointer, regionReaderCalls=self.region_calls,
                    regionAllocations=sum(i == 67961 for i in self.allocations.values()), region=region_value,
                    configInputs=self.config_inputs, sessionB0Unchanged=self.text(self.e.read(self.account+176)) == self.session_values[176])


def check_declarations(binary, records):
    row = next(r for r in records if r['index'] == 67961)
    if not any(m['registeredAddress'] == hex(REGION_READER) and m['virtualSlot'] == 19 for m in row['methods']):
        raise ValueError('Region reader declaration changed')
    pe = PE(binary); address = 0x149BB595C; offset = pe.offset(address, 7)
    raw = binary[offset:offset+7]
    if raw[:3] != bytes.fromhex('488b1d') or address+7+int.from_bytes(raw[3:], 'little', signed=True) != REGION_CELL:
        raise ValueError('Region Clone self-class changed')
    offset = pe.offset(0x149BB5982, 5); raw = binary[offset:offset+5]
    if raw[0] != 0xe8 or 0x149BB5987+int.from_bytes(raw[1:], 'little', signed=True) != 0x149BB5A00:
        raise ValueError('Region Clone CopyFrom changed')


def recover(binary, metadata, records, libraries, root):
    check_declarations(binary, records)
    signer = private_numbers((root/'src/main/resources/keys/SigningKey.der').read_bytes())
    _, body, _ = tlv((root/'src/main/resources/keys/game_keys/4_Pub.der').read_bytes())
    _, _, at = tlv(body); _, value, _ = tlv(body, at); n, exponent = rsa_public_numbers(value[1:])
    fixtures = []
    for name, wire, outcome in CASES:
        plain = bytes.fromhex(wire)
        payload = dict(content=base64.b64encode(encrypt_seed([0,n,exponent], plain)).decode(),
                       sign=base64.b64encode(sign_sha256(signer, plain)).decode())
        e = Offline72(binary, libraries, records, True); runtime = None
        try:
            e.prepare(); runtime = DispatchPost72(e, binary, metadata, plain, signer)
            result = runtime.run_post(payload)
            fixtures.append(dict(name=name, plainWire=wire, outcome=outcome, **result))
        finally:
            if runtime: runtime.close()
            e.close()
    report = dict(schemaVersion=1, inputSHA256=HASHES, fixtureCount=len(fixtures), fixtures=fixtures,
                  results=[{k:v for k,v in f.items() if k != 'trace'} for f in fixtures],
                  entry=hex(ENTRY), configStop=hex(CONFIG), errorRouteStop=hex(ERROR_ROUTE),
                  regionTypeIndex=67961, regionClassCell=hex(REGION_CELL), regionReader=hex(REGION_READER),
                  semanticStatus='unknown', runtimeStatus='not-tested', wholeDispatchExecuted=False,
                  models=['explicit local signing trust and JSON/OS fixtures inherited from DispatchPrefix72',
                          'managed Region allocation/interface metadata; native ReadMessage/limits/reader execute',
                          'bounded ASCII/string/byte/scalar helpers; allocation initializes absent fields'],
                  outsideScope=['config helper body', 'post-config/B0 store', 'second coroutine',
                                'tag14/tag965 semantics', 'complete Region schema', 'actual SDK/HTTP/client'])
    errors = validate_summary(report)
    if errors: raise ValueError(errors)
    return report


def validate_summary(report):
    errors = []
    expected = dict(inputSHA256=HASHES, fixtureCount=8, entry=hex(ENTRY), configStop=hex(CONFIG),
                    errorRouteStop=hex(ERROR_ROUTE), regionTypeIndex=67961, regionClassCell=hex(REGION_CELL),
                    regionReader=hex(REGION_READER), semanticStatus='unknown', runtimeStatus='not-tested', wholeDispatchExecuted=False)
    if any(report.get(k) != v for k,v in expected.items()): errors.append('Dispatch post scope mismatch')
    rows = report.get('results', [])
    if [(r.get('name'),r.get('plainWire'),r.get('outcome')) for r in rows] != list(CASES): errors.append('Dispatch post cases mismatch')
    for row in rows:
        outcome = row.get('outcome'); config = outcome == 'config'; wire_error = outcome == 'wire-reject'
        if row.get('sessionB0Unchanged') is not True: errors.append('Premature B0 write')
        if wire_error:
            if row.get('wireError') not in ('native-truncated-message','bounded-wire-helper') or row.get('postParsePointerPreserved') or row.get('configInputs'):
                errors.append('Invalid wire reached post-parse')
            continue
        if row.get('stop') != hex(CONFIG if config else ERROR_ROUTE) or row.get('postParsePointerPreserved') is not True:
            errors.append('Post-parse route mismatch')
        if row.get('nativePlaintextSHA256') != hashlib.sha256(bytes.fromhex(row['plainWire'])).hexdigest(): errors.append('Plaintext mismatch')
        checks = row.get('keyChecks', [])
        if [c.get('operation') for c in checks] != [101,103] or any(c.get('native') is not True or c.get('oracle') is not True for c in checks):
            errors.append('Missing real crypto verification')
        if config:
            name = row['name']; nonzero = name in ('nonzero','merge-region')
            if row.get('region') != dict(typeIndex=67961,uint32=300 if nonzero else 0xffffffff if name=='uint32-max' else 0,text='inner' if nonzero else ''):
                errors.append('Independent nested values mismatch')
            if row.get('regionReaderCalls') != (2 if name=='merge-region' else 1) or row.get('regionAllocations') != 1:
                errors.append('Nested merge/allocation mismatch')
            if row.get('configInputs') != dict(receiverPreserved=True,mode=1,arrays=['636667','6b6579'] if name=='nonzero' else ['','']):
                errors.append('Native config arguments mismatch')
        elif row.get('configInputs') is not None: errors.append('Error route reached config')
    return errors


def main():
    a = parse_args(); b,m,s = [read_verified(a.client/name, sha) for name,sha in HASHES.items()]
    records,_ = extract(b,m,startup=s,structures=True)
    report = recover(b,m,records,a.libraries,Path(__file__).resolve().parents[1])
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],wholeDispatchExecuted=False)))


if __name__ == '__main__': main()
