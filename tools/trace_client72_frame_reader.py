"""Native frame receive/decrypt/header validation with an opaque message factory.

The body factory/reader and pooled storage are explicit fixtures. No socket,
live client, actual message construction, or protocol semantics are inferred.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STACK
    from .client72_session_runtime import fragment
    from .trace_client72_frame import Frame72, RETURN
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STACK
    from client72_session_runtime import fragment
    from trace_client72_frame import Frame72, RETURN

ENTRY = 0x14A446CC0
ERRORS = {0x1509CB360, 0x1509CB150, 0x1404C2650}


class FrameReader72(Frame72):
    def __init__(self, e, key, alternate, selector):
        super().__init__(e, 0, b'', 0, 0, key, alternate, selector)
        self.reader = e.alloc()
        self.reader_type = e.synthetic_type(0x1458AD6D0)
        self.registry = e.alloc()
        e.write(GLOBAL + 0xd3c0, self.registry)
        self.factory_commands = []
        self.body_bytes = None
        self.reader_released = False
        e.models[0x151A853D0] = self.factory
        e.write(e.read(self.message) + 0x168, e.model_stub(self.read_body))
        e.write(e.read(self.message) + 0x128, 0x1495C9D60)
        for address in (ENTRY, 0x1483D2A00, 0x1483D28C0, 0x14CD81610,
                        0x14A42BD90, 0x1474D8100, 0x147520110, 0x1475202D0,
                        0x14751FDB0, 0x1475211E0, 0x14751FD30, 0x1475215C0,
                        0x1495AA480, 0x1495C9D60, 0x159B548E0): e.allow(address)

    def receive(self, data, skip_xor=False, null_array=False, supplied_length=None):
        e = self.e
        e.write(STACK + 0x100008, RETURN)
        initializer = fragment(e, 0x159B548E0, {RETURN}, dict(rsp=STACK+0x100008))
        if e.read(0x14586B610, 'B') != 1: raise ValueError('BitConverter initialization failed')
        self.input_array = self.array(data)
        reference = e.alloc()
        e.write(reference, 0 if null_array else self.input_array)
        e.write(STACK + 0x100008, RETURN)
        e.write(STACK + 0x100030, int(skip_xor), 'B')
        trace = fragment(e, ENTRY, {RETURN, *ERRORS}, dict(rsp=STACK+0x100008,
                         rcx=self.packet, rdx=reference,
                         r8=len(data) if supplied_length is None else supplied_length, r9=0))
        return initializer, trace

    def allocate_writer(self):
        if self.e.reg('rcx') != self.writer_pool or self.e.reg('rdx') != self.reader_type:
            raise ValueError('Unreviewed reader allocation')
        return self.reader

    def release_writer(self):
        if self.e.reg('rcx') != self.writer_pool or self.e.reg('rdx') != self.reader:
            raise ValueError('Unreviewed reader return')
        self.reader_released = True
        return 0

    def factory(self):
        if self.e.reg('rcx') != self.registry: raise ValueError('Wrong registry receiver')
        self.factory_commands.append(self.e.reg('edx'))
        return self.message

    def read_body(self):
        e = self.e
        if e.reg('rcx') != self.message or e.reg('rdx') != self.reader:
            raise ValueError('Wrong body receiver/reader')
        start, end = e.read(self.reader+0x44,'I'), e.read(self.reader+0x40,'I')
        array = e.read(self.reader+0x18)
        if array != self.input_array or not 0 <= start <= end <= e.read(array+24):
            raise ValueError('Opaque reader outside input array')
        self.body_bytes = bytes(e.uc.mem_read(array+32+start,end-start))
        e.write(self.reader+0x44,end,'I')
        return 0


def fixture_cases():
    header = bytes.fromhex('18ffffffff0f30c480808020')
    high = bytes.fromhex('18ffffffff0f30ffffffffffffffffff01')
    specs = [
        ('empty',0,b'',b'',0,False,None),
        ('signed-command-low',32767,header,b'body7!!',0,False,None),
        ('signed-command-high',32768,header,b'body8!!!',0,False,None),
        ('max-command-header',65535,high,b'body9!!!!',1,False,None),
        ('session-wrap',23172,header,bytes(range(256))*17,0,False,None),
        ('alternate-wrap',23172,header,bytes(range(256))*17,1,False,None),
        ('body-u32',32768,header,bytes(range(256))*256,0,False,None),
        ('plaintext-flag',32768,header,b'plain',0,True,None),
        ('duplicate-header',1,bytes.fromhex('1801180230033004'),b'dup',0,False,None),
        ('bad-start',1,header,b'bad',0,False,'start'),
        ('bad-end',1,header,b'bad',0,False,'end'),
        ('truncated-frame',1,header,b'cut',0,False,'truncate'),
        ('short-frame',0,b'',b'',0,False,'short'),
        ('null-array',0,b'',b'',0,False,'null'),
        ('truncated-header-varint',1,b'\x18\x80',b'body',0,False,None),
        ('invalid-header-tag',1,b'\x00\x01',b'body',0,False,None),
        ('wrong-key',1,header,b'body',0,False,'key'),
        ('trailing-bytes',1,header,b'body',0,False,'trailing'),
    ]
    cases = []
    for name, cmd, head, body, selector, skip, mutation in specs:
        data = struct.pack('>HHHI',0x4567,cmd,len(head),len(body))+head+body+b'\x89\xab'
        consumed = len(data)
        if mutation == 'start': data = b'\x44'+data[1:]
        elif mutation == 'end': data = data[:-1]+b'\xaa'
        elif mutation == 'truncate': data = data[:-1]
        elif mutation == 'short': data = data[:11]
        elif mutation == 'trailing': data += b'\x12\x34\x56'
        code = {'start':6,'end':7,'truncate':2,'short':5,'null':4,'key':6}.get(mutation,1)
        stop = {'truncated-header-varint':0x1509CB360,'invalid-header-tag':0x1509CB150}.get(name,RETURN)
        cases.append(dict(name=name,command=cmd,header=head,body=body,plain=data,selector=selector,
                          skip=skip,mutation=mutation,consumed=consumed,code=code,stop=stop))
    return cases


def recover(binary, records, libraries):
    by_index = {r['index']:r for r in records}
    bitconverter = by_index[173]
    if bitconverter['name'] != 'BitConverter' or not any(m['name']=='.cctor' and
            m['registeredAddress']=='0x159b548e0' for m in bitconverter['methods']):
        raise ValueError('BitConverter initializer declaration changed')
    if not any(m.get('virtualSlot')==11 and m['registeredAddress']=='0x1495c9d60'
               for m in by_index[67861]['methods']): raise ValueError('Base lifecycle slot changed')
    fixtures, results = [], []
    key = bytes((i*37+11)%256 for i in range(4096))
    alternate = bytes((i*19+7)%256 for i in range(4096))
    for case in fixture_cases():
        e = Offline72(binary,libraries,records,True)
        try:
            e.prepare()
            runtime = FrameReader72(e,key,alternate,case['selector'])
            encrypt_key = alternate if case['selector'] else key
            if case['mutation']=='key': encrypt_key = alternate
            data = case['plain'] if case['skip'] else bytes(
                b ^ encrypt_key[i%len(encrypt_key)] for i,b in enumerate(case['plain']))
            initializer, trace = runtime.receive(data,case['skip'],case['mutation']=='null')
            code = e.reg('eax') if trace['stop']==hex(RETURN) else None
            if trace['stop']!=hex(case['stop']) or (code is not None and code!=case['code']):
                raise ValueError(f"{case['name']}: unexpected native result {code} at {trace['stop']}")
            success = code == 1
            if success:
                if (runtime.body_bytes != case['body'] or runtime.factory_commands != [case['command']] or
                        e.read(runtime.packet+0x22,'H') != case['command'] or
                        e.read(runtime.packet+0x30,'I') != case['consumed'] or not runtime.reader_released or
                        e.read(runtime.message+0x14,'I') != 1):
                    raise ValueError('Body/command/consumed length/lifecycle mismatch')
                expected_values = ((0,0) if case['name']=='empty' else
                                   (2,4) if case['name']=='duplicate-header' else
                                   (0xffffffff,0xffffffffffffffff) if case['name']=='max-command-header' else
                                   (0xffffffff,0x200000044))
                if (e.read(runtime.header+0x30,'I'),e.read(runtime.header+0x18)) != expected_values:
                    raise ValueError('Native header decoding mismatch')
            if case['mutation']=='end' and (runtime.body_bytes != case['body'] or runtime.reader_released):
                raise ValueError('Bad trailer did not follow observed body-before-trailer ordering')
            xor_count = trace['executedAddresses'].get('0x14a446dbe',0)
            expected_xor = 0 if case['skip'] or case['mutation']=='null' else len(data)
            if xor_count != expected_xor: raise ValueError('Native receive XOR count mismatch')
            if case['mutation'] not in ('key','null') and bytes(e.uc.mem_read(runtime.input_array+32,len(data))) != case['plain']:
                raise ValueError('In-place native decrypt mismatch')
            results.append(dict(name=case['name'],command=case['command'],inputLength=len(data),
                                selector=case['selector'],skipXor=case['skip'],xorIterations=xor_count,
                                returnCode=code,stop=trace['stop'],factoryCommands=runtime.factory_commands,
                                bodyConsumed=runtime.body_bytes is not None,readerReleased=runtime.reader_released,
                                bodySHA256=hashlib.sha256(runtime.body_bytes).hexdigest() if runtime.body_bytes is not None else None,
                                packetLength=e.read(runtime.packet+0x30,'I'),
                                sequence=e.read(runtime.header+0x30,'I'),sentMs=e.read(runtime.header+0x18),
                                nativePathSHA256=trace['nativePathSHA256']))
            fixtures.append(dict(name=case['name'],initializer=initializer,trace=trace,passed=True))
        finally: e.close()
    report = dict(schemaVersion=1,inputSHA256=HASHES,runtimeStatus='not-tested',
                  fixtureCount=len(fixtures),results=results,fixtures=fixtures,
                  frameReaderExecuted=True,payloadReaderExecuted=False,actualFactoryExecuted=False,
                  wholeHandlerReturned=False,
                  models=['inherited Frame72 pooled storage; synthetic base message lifecycle slot',
                          'opaque factory observes command and returns fixture object',
                          'opaque body reader consumes the declared input window; no schema'],
                  native=['BitConverter .cctor, network endian converters and in-place XOR',
                          'PacketHead reader and CodedInputStream scalar/tag/limit helpers',
                          'length/marker checks, payload window, lifecycle counter and reader return'],
                  outsideScope=['actual payload factory/schema and decompression branch','dispatch/Handler/socket/real client',
                                'startup/IFix and key-derivation integration','error logging/cleanup after thrown exceptions'])
    errors = validate_summary(report)
    if errors: raise ValueError('; '.join(errors))
    return report


def validate_summary(report):
    errors = []
    if (report.get('inputSHA256')!=HASHES or report.get('runtimeStatus')!='not-tested' or
            report.get('frameReaderExecuted') is not True or report.get('payloadReaderExecuted') is not False or
            report.get('actualFactoryExecuted') is not False or report.get('wholeHandlerReturned') is not False or
            not report.get('models') or not report.get('outsideScope')):
        errors.append('Receive scope/input evidence changed')
    cases = fixture_cases()
    results = report.get('results',[])
    if report.get('fixtureCount')!=len(cases) or [r['name'] for r in results]!=[c['name'] for c in cases]:
        return errors+['Receive fixture coverage changed']
    for row,case in zip(results,cases):
        code = case['code'] if case['stop']==RETURN else None
        if row['returnCode']!=code or row['stop']!=hex(case['stop']): errors.append('Native receive outcome changed')
        if row['selector']!=case['selector'] or row['skipXor']!=case['skip'] or row['command']!=case['command']:
            errors.append('Receive inputs changed')
        if code==1:
            if (row['factoryCommands']!=[case['command']] or row['packetLength']!=case['consumed'] or
                    not row['readerReleased'] or not row['bodyConsumed'] or
                    row['bodySHA256']!=hashlib.sha256(case['body']).hexdigest()): errors.append('Receive content mismatch')
            values = ((0,0) if case['name']=='empty' else (2,4) if case['name']=='duplicate-header' else
                      (0xffffffff,0xffffffffffffffff) if case['name']=='max-command-header' else
                      (0xffffffff,0x200000044))
            if (row['sequence'],row['sentMs'])!=values: errors.append('Receive header value changed')
        if row['name']=='bad-end' and (not row['bodyConsumed'] or row['readerReleased']):
            errors.append('Trailer ordering changed')
        if row['xorIterations']!=(0 if case['skip'] or case['mutation']=='null' else len(case['plain'])):
            errors.append('Receive XOR coverage changed')
    return errors


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--summary-output',type=Path,required=True)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    a = p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()): p.error('Output must be outside client')
    b,m,s = [read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_ = extract(b,m,startup=s,structures=True)
    report = recover(b,records,a.libraries)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'])))


if __name__=='__main__':main()
