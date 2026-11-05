"""Bounded native frame builder; payload size/write are explicit opaque inputs.

Header serialization, endian conversion, stream writes and XOR execute natively.
No payload semantic claim, socket or live-client acceptance is made.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STACK, END
    from .client72_session_runtime import fragment
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STACK, END
    from client72_session_runtime import fragment
    from trace_client72_token import varint

ENTRY = 0x14A447E00
RETURN = END + 0x100


class Frame72:
    def __init__(self, e, command, payload, sequence, sent_ms, key, initial_key, selector):
        self.e = e
        self.payload = payload
        self.arrays = set()
        self.released = []
        self.pool = e.alloc()
        self.packet = e.alloc()
        self.header = e.alloc()
        self.message = e.alloc()
        self.stream = e.alloc()
        self.stream_ref = e.alloc()
        self.writer = e.alloc()
        self.buffer = self.array(bytes(131072))
        e.write(self.stream_ref, self.stream)
        e.write(self.packet + 0x10, self.message)
        e.write(self.packet + 0x18, self.header)
        e.write(self.packet + 0x22, command, 'H')
        e.write(self.header + 0x30, sequence, 'I')
        e.write(self.header + 0x18, sent_ms)
        e.write(self.header, e.alloc())
        e.write(self.stream + 0x28, self.buffer)
        for off in (0x3c, 0x3d, 0x3e): e.write(self.stream + off, 1, 'B')
        e.write(self.stream + 0x40, 131072, 'I')
        stream_class = e.alloc(512)
        e.write(self.stream, stream_class)
        e.write(stream_class + 0x1b8, 0x159BCDA20)
        message_class = e.alloc(1024)
        e.write(self.message, message_class)
        e.write(message_class + 0x138, e.model_stub(self.payload_size))
        e.write(message_class + 0x158, e.model_stub(self.payload_write))
        manager = e.alloc()
        wrapper = e.alloc()
        transport = e.alloc()
        transport_class = e.alloc(1024)
        iface = e.synthetic_type(0x1458C72D0)
        table = e.alloc()
        e.write(table, iface)
        e.write(table + 8, 4, 'I')
        e.write(transport_class + 0x80, table)
        e.write(transport_class + 0xc0, 1, 'H')
        e.write(transport_class + 0xd0 + 5*8, 0x1472CE3B0)
        e.write(transport, transport_class)
        e.write(transport + 0x90, self.pool)
        e.write(wrapper + 0x10, transport)
        e.write(manager + 0x58, wrapper)
        e.write(GLOBAL + 0xa0d0, manager)
        e.write(GLOBAL + 0x6af0, self.pool)
        writer_pool = e.alloc()
        writer_pool_class = e.alloc(512)
        e.write(writer_pool, writer_pool_class)
        e.write(GLOBAL + 0x5d60, writer_pool)
        self.writer_pool = writer_pool
        self.writer_type = e.synthetic_type(0x1458B7A00)
        e.write(writer_pool_class + 0xf0, e.model_stub(self.allocate_writer))
        e.write(writer_pool_class + 0xf8, e.model_stub(self.release_writer))
        e.synthetic_type(0x14589F4F8)
        e.write(GLOBAL + 0x19580, 0 if key is None else self.array(key))
        e.write(GLOBAL + 0x19588, 0 if initial_key is None else self.array(initial_key))
        e.write(0x145878FC0, selector, 'B')
        e.models.update({0x146F63190: self.allocate_bytes, 0x146F62F80: self.allocate_bytes,
                         0x146F633A0: self.release_bytes, 0x1404D7630: self.copy_array})
        for address in (ENTRY, 0x149935C30, 0x1472CE3B0, 0x159BCE3C0, 0x159BCE590,
                        0x159BCDA20, 0x1483D30D0, 0x1483D2E60, 0x14CD809E0,
                        0x14CD80D60, 0x151747770, 0x151767140, 0x1517662A0,
                        0x151766960, 0x151765E10, 0x159B541A0): e.allow(address)
        e.uc.mem_map(END, 4096)
        e.uc.mem_write(RETURN, b'\xc3')

    def run(self):
        e = self.e
        e.write(STACK + 0x100008, RETURN)
        trace = fragment(e, ENTRY, {RETURN, 0x14A448895, 0x14A448890, 0x159B46B50, 0x14A448572},
                         dict(rsp=STACK+0x100008, rcx=self.packet, rdx=self.stream_ref))
        length = e.read(self.packet + 0x30, 'I')
        return trace, bytes(e.uc.mem_read(self.buffer + 32, length))

    def array(self, data):
        p = self.e.alloc(32 + len(data))
        self.e.write(p + 24, len(data))
        if data: self.e.uc.mem_write(p + 32, data)
        self.arrays.add(p)
        return p

    def allocate_bytes(self):
        e = self.e
        if e.reg('rcx') != self.pool or e.reg('edx') > 131072:
            raise ValueError('Unreviewed byte pool request')
        return self.array(bytes(e.reg('edx')))

    def release_bytes(self):
        if self.e.reg('rcx') != self.pool or self.e.reg('rdx') not in self.arrays:
            raise ValueError('Unreviewed byte pool return')
        self.released.append(self.e.reg('rdx'))
        return 0

    def allocate_writer(self):
        if self.e.reg('rcx') != self.writer_pool or self.e.reg('rdx') != self.writer_type:
            raise ValueError('Unreviewed writer pool request')
        return self.writer

    def release_writer(self):
        if self.e.reg('rcx') != self.writer_pool or self.e.reg('rdx') != self.writer:
            raise ValueError('Unreviewed writer pool return')
        if self.e.read(self.writer + 0x18) or self.e.read(self.writer + 0x28):
            raise ValueError('Native writer cleanup did not execute')
        return 0

    def payload_size(self):
        if self.e.reg('rcx') != self.message: raise ValueError('Wrong payload receiver')
        return len(self.payload)

    def payload_write(self):
        e = self.e
        if e.reg('rcx') != self.message or e.reg('rdx') != self.writer:
            raise ValueError('Wrong payload writer')
        if e.read(self.writer + 0x2c, 'I') != len(self.payload):
            raise ValueError('Payload capacity mismatch')
        if self.payload: e.uc.mem_write(e.read(self.writer + 0x18) + 32, self.payload)
        e.write(self.writer + 0x28, len(self.payload), 'I')
        return 0

    def copy_array(self):
        e = self.e
        source, target = e.reg('rcx'), e.reg('r8')
        start, offset = e.reg('edx'), e.reg('r9d')
        count = e.read(e.reg('rsp') + 0x28, 'I')
        if (source not in self.arrays or target not in self.arrays or
                start + count > e.read(source + 24) or offset + count > e.read(target + 24)):
            raise ValueError('Array copy outside reviewed storage')
        if count: e.uc.mem_write(target + 32 + offset, bytes(e.uc.mem_read(source + 32 + start, count)))
        return 0


def expected_frame(command, payload, sequence, sent_ms, key):
    header = (b'\x18' + varint(sequence) if sequence else b'')
    header += b'\x30' + varint(sent_ms) if sent_ms else b''
    plain = struct.pack('>HHHI', 0x4567, command, len(header), len(payload)) + header + payload + b'\x89\xab'
    return bytes(v ^ key[i % len(key)] for i, v in enumerate(plain)) if key else plain


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--summary-output', type=Path, required=True)
    p.add_argument('--libraries', type=Path, default=Path('local/migration72/python'))
    a = p.parse_args()
    for path in (a.output, a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()): p.error('Output must be outside client')
    b, m, s = [read_verified(a.client/name, sha) for name, sha in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    report = recover(b, records, a.libraries)
    for path, data in ((a.output, report), (a.summary_output, {k:v for k,v in report.items() if k != 'fixtures'})):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2)+'\n', 'utf-8')
    print(json.dumps({'fixtures': report['fixtureCount']}))


def recover(binary, records, libraries):
    declarations = []
    by_index = {r['index']: r for r in records}
    for owner, slot, address in ((597,29,0x159BCDA20), (32016,5,0x1472CE3B0)):
        methods = [m for m in by_index[owner]['methods'] if m.get('virtualSlot') == slot]
        if len(methods) != 1 or int(methods[0]['registeredAddress'],16) != address:
            raise ValueError('Frame storage virtual declaration changed')
        declarations.append(dict(owner=owner, slot=slot, address=hex(address)))
    fixtures, results = [], []
    session_key = bytes((i*37+11) % 256 for i in range(4096))
    initial_key = bytes((i*19+7) % 256 for i in range(4096))
    cases = [
        ('zero', 0, 0, 0, 0, None, None, 0, None),
        ('short7', 1, 7, 1, 1, b'abc', b'defg', 0, None),
        ('short8', 32767, 8, 127, 127, b'abc', b'defg', 1, None),
        ('long9', 32768, 9, 128, 128, session_key, initial_key, 0, None),
        ('max-command', 65535, 17, 0xffffffff, 0xffffffffffffffff, session_key, initial_key, 1, None),
        ('session-key-wrap', 23172, 4103, 0xffffffff, 0x200000044, session_key, initial_key, 0, None),
        ('initial-key-wrap', 23172, 4103, 0xffffffff, 0x200000044, session_key, initial_key, 1, None),
        ('body-u32', 32768, 65536, 1, 0x200000044, session_key, initial_key, 0, None),
        ('missing-selected-session-key', 1, 9, 1, 1, None, initial_key, 0, None),
        ('missing-selected-initial-key', 1, 9, 1, 1, session_key, None, 1, None),
        ('empty-selected-key', 1, 9, 1, 1, b'', initial_key, 0, None),
        ('missing-message', 1, 9, 1, 1, session_key, initial_key, 0, 'message'),
        ('missing-stream', 1, 9, 1, 1, session_key, initial_key, 0, 'stream'),
        ('missing-header', 1, 9, 1, 1, session_key, initial_key, 0, 'header'),
        ('readonly-stream', 1, 9, 1, 1, session_key, initial_key, 0, 'readonly'),
    ]
    for name, command, length, sequence, sent_ms, key, initial, selector, missing in cases:
        e = Offline72(binary, libraries, records, True)
        try:
            e.prepare()
            payload = bytes((i*13+5) % 256 for i in range(length))
            runtime = Frame72(e, command, payload, sequence, sent_ms, key, initial, selector)
            if missing == 'message': e.write(runtime.packet+0x10, 0)
            elif missing == 'stream': e.write(runtime.stream_ref, 0)
            elif missing == 'header': e.write(runtime.packet+0x18, 0)
            elif missing == 'readonly': e.write(runtime.stream+0x3d, 0, 'B')
            trace, frame = runtime.run()
            expected_stop = {'header':0x14A448572, 'readonly':0x159B46B50}.get(missing, RETURN)
            if trace['stop'] != hex(expected_stop): raise ValueError('Unexpected frame terminal')
            passed = missing is None
            if passed:
                expected = expected_frame(command, payload, sequence, sent_ms, initial if selector else key)
                if frame != expected: raise ValueError('Native frame differs from independent bytes')
                if e.reg('al') != 1: raise ValueError('Frame builder did not return success')
                for address in (0x14A447EB9,0x14A448259,0x14A448309,0x14A448399,0x14A448434,
                                0x14CD809E0,0x14CD80D60,0x151767140,0x159BCE590,0x159BCDA20):
                    if hex(address) not in trace['executedAddresses']: raise ValueError('Missing native frame step')
                if e.read(runtime.stream+0x30,'I') != len(frame): raise ValueError('Stream length differs')
                selected = initial if selector else key
                xor_count = trace['executedAddresses'].get('0x14a44836f', 0)
                if xor_count != (len(frame) if selected else 0): raise ValueError('Wrong native XOR coverage')
                for offset, value in ((0x19580,key),(0x19588,initial)):
                    pointer = e.read(GLOBAL+offset)
                    if value is not None and bytes(e.uc.mem_read(pointer+32,len(value))) != value:
                        raise ValueError('Frame builder mutated key material')
            elif frame or (missing in ('message','stream') and e.reg('al') != 0):
                raise ValueError('Invalid frame input unexpectedly produced output')
            results.append(dict(name=name,command=command,payloadLength=length,sequence=sequence,sentMs=sent_ms,
                                selector=selector,selectedKeyLength=len((initial if selector else key) or b''),
                                stop=trace['stop'],frameProduced=passed,frameLength=len(frame),
                                frameSHA256=hashlib.sha256(frame).hexdigest(),
                                nativePathSHA256=trace['nativePathSHA256'],independentByteComparison=passed,
                                prefix=frame[:32].hex(),suffix=frame[-16:].hex()))
            fixtures.append(dict(name=name,trace=trace,frame=frame.hex(),passed=True))
        finally: e.close()
    report = dict(schemaVersion=1, inputSHA256=HASHES, fixtures=fixtures, results=results,
                  fixtureCount=len(fixtures), declarations=declarations,runtimeStatus='not-tested',
                  frameBuilderExecuted=True,payloadWriterExecuted=False,wholeHandlerReturned=False,
                  keySource='explicit byte arrays in GLOBAL+19580/+19588; selector145878FC0=0/1',
                  models=['synthetic typed storage/vtables and pool allocation/return',
                          'bounded Array.Copy backing-store operation',
                          'opaque payload CalculateSize/WriteTo with explicit bytes; no semantic name'],
                  native=['PacketHead CalculateSize/WriteTo and scalar varint writers',
                          'MemoryStream SetLength/Write/GetBuffer, endian conversions',
                          'frame header/trailer/length stores, whole-frame cyclic XOR and native cleanup'],
                  outsideScope=['payload constructor/serialization integration','native frame reader',
                                'key derivation integration and real startup/IFix','socket and live acceptance'])
    errors = validate_summary(report)
    if errors: raise ValueError('; '.join(errors))
    return report


def validate_summary(report):
    errors = []
    if (report.get('inputSHA256') != HASHES or report.get('runtimeStatus') != 'not-tested' or
            report.get('frameBuilderExecuted') is not True or report.get('payloadWriterExecuted') is not False or
            report.get('wholeHandlerReturned') is not False or not report.get('models') or not report.get('outsideScope')):
        errors.append('Frame scope/input evidence changed')
    results = report.get('results', [])
    if len(results) != 15 or report.get('fixtureCount') != 15:
        return errors + ['Missing frame boundary fixtures']
    for row in results:
        missing = row['name'].startswith('missing-') and not row['name'].startswith('missing-selected')
        rejected = missing or row['name'] == 'readonly-stream'
        expected_stop = {'missing-header':'0x14a448572','readonly-stream':'0x159b46b50'}.get(row['name'],hex(RETURN))
        if row['stop'] != expected_stop or row['frameProduced'] != (not rejected) or row['independentByteComparison'] != (not rejected):
            errors.append('Frame outcome changed')
        if not rejected:
            header = len(varint(row['sequence']))+1 if row['sequence'] else 0
            header += len(varint(row['sentMs']))+1 if row['sentMs'] else 0
            if row['frameLength'] != 12+header+row['payloadLength']:
                errors.append('Frame length changed')
        elif row['frameLength'] != 0: errors.append('Rejected input produced frame')
    indexed = {r['name']:r for r in results}
    required = {'zero','short7','short8','long9','max-command','session-key-wrap','initial-key-wrap','body-u32',
                'missing-selected-session-key','missing-selected-initial-key','empty-selected-key',
                'missing-message','missing-stream','missing-header','readonly-stream'}
    if set(indexed) != required: errors.append('Frame case set changed')
    elif (indexed['body-u32']['payloadLength'] != 65536 or indexed['max-command']['command'] != 65535 or
          indexed['max-command']['sentMs'] != 0xffffffffffffffff or indexed['long9']['command'] != 32768 or
          indexed['session-key-wrap']['selector'] != 0 or indexed['initial-key-wrap']['selector'] != 1 or
          indexed['session-key-wrap']['frameSHA256'] == indexed['initial-key-wrap']['frameSHA256']):
        errors.append('Frame boundary/key selection coverage changed')
    if set(indexed) == required:
        for name in ('missing-selected-session-key','missing-selected-initial-key','empty-selected-key','zero'):
            row = indexed[name]
            payload = bytes((i*13+5)%256 for i in range(row['payloadLength']))
            raw = expected_frame(row['command'],payload,row['sequence'],row['sentMs'],None)
            if row['selectedKeyLength'] != 0 or row['frameSHA256'] != hashlib.sha256(raw).hexdigest():
                errors.append('Missing/empty selected key incorrectly used fallback')
    return errors


if __name__ == '__main__': main()
