"""Continuous native seed/Token/queue/size/writer/frame execution, offline only.

UTF-8 encoding, OS/SDK and initial frame key remain explicit fixtures. Native
payload serialization and size are not replaced with precomputed payloads.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import struct

try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STACK, END
    from .client72_session_runtime import fragment
    from .trace_client72_frame import Frame72, RETURN, expected_frame
    from .trace_client72_token_constructor import TokenConstructor72, OPAQUE_HELPER
    from .trace_client72_seed_crypto import SeedString72
    from .trace_client72_send_flow import SendFlow72, declarations, slot_method
    from .trace_client72_token import parse_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STACK, END
    from client72_session_runtime import fragment
    from trace_client72_frame import Frame72, RETURN, expected_frame
    from trace_client72_token_constructor import TokenConstructor72, OPAQUE_HELPER
    from trace_client72_seed_crypto import SeedString72
    from trace_client72_send_flow import SendFlow72, declarations, slot_method
    from trace_client72_token import parse_wire


class TokenFrame72(Frame72):
    def __init__(self,e,*,initial_key=bytes((i*19+7)%256 for i in range(4096)),
                 session_key=bytes((i*37+11)%256 for i in range(4096))):
        self.key = initial_key
        super().__init__(e,0,b'',0,0,session_key,self.key,0)
        self.encoding = e.alloc()
        cls = e.alloc(512)
        e.write(self.encoding,cls)
        e.write(cls+0x128,e.model_stub(self.byte_count))
        e.write(cls+0x168,e.model_stub(self.get_bytes))
        # Execute the target .cctor's cached UTF-8 branch and its actual stores.
        e.write(GLOBAL+0x24b8,self.encoding)
        for a in (0x1517676B0,0x1500C16E0,0x1500C2660,0x1517204C0,0x15171FE80): e.allow(a)
        e.write(STACK+0x100008,RETURN)
        self.initializer = fragment(e,0x1517676B0,{RETURN},dict(rsp=STACK+0x100008))
        if e.read(GLOBAL+0x10770)!=self.encoding: raise ValueError('Native UTF-8 cache binding failed')
        e.write(STACK+0x100008,END)
        self.encoding_calls = Counter()
        self.flow = None

    def attach(self,ctor,sequence,sent_ms):
        e = self.e
        self.flow = SendFlow72(e,ctor,82491,24604,sequence,sent_ms,'connected')
        flow = self.flow
        cls = e.read(ctor.request)
        for slot,address in ((13,0x1500C16E0),(17,0x1500C2660)):
            slot_method(e.records[82491],slot,address)
            e.write(cls+0xd0+slot*8,address)
        e.write(GLOBAL+0xa0d0,ctor.sender)
        transport_class = e.read(flow.transport)
        e.write(transport_class+0xd0+5*8,0x1472CE3B0)
        e.write(flow.transport+0x90,self.pool)
        stream = e.read(flow.transport+0x20)
        e.write(stream,e.read(self.stream))
        e.write(stream+0x28,self.buffer)
        e.write(stream+0x3e,1,'B')
        pool_class = e.read(flow.pool)
        self.writer_pool = flow.pool
        e.write(pool_class+0xf0,e.model_stub(self.allocate_send_object))
        e.write(pool_class+0xf8,e.model_stub(self.release_writer))

    def allocate_send_object(self):
        if self.e.reg('rdx')==self.writer_type: return self.allocate_writer()
        return self.flow.allocate_envelope()

    def payload_size(self):
        raise ValueError('Opaque payload size is forbidden in the continuous Token experiment')

    def payload_write(self):
        raise ValueError('Opaque payload writer is forbidden in the continuous Token experiment')

    def byte_count(self):
        if self.e.reg('rcx')!=self.encoding: raise ValueError('Unexpected encoding receiver')
        self.encoding_calls['GetByteCount'] += 1
        return len(self.ctor.text(self.e.reg('rdx')).encode('utf-8'))

    def get_bytes(self):
        e = self.e
        text = self.ctor.text(e.reg('rdx'))
        if e.reg('rcx')!=self.encoding or e.reg('r8d') or e.reg('r9d')!=len(text.encode('utf-16-le'))//2:
            raise ValueError('Unsupported UTF-8 substring request')
        array,offset = e.read(e.reg('rsp')+0x28),e.read(e.reg('rsp')+0x30,'I')
        raw = text.encode('utf-8')
        if array not in self.arrays or offset+len(raw)>e.read(array+24):
            raise ValueError('UTF-8 output outside pooled array')
        if raw: e.uc.mem_write(array+32+offset,raw)
        self.encoding_calls['GetBytes'] += 1
        return len(raw)


def check_payload(e,ctor,body,wire):
    actual = {tag:(kind,value) for tag,kind,value in parse_wire(body)}
    expected = {}
    for field in wire['fields']:
        address = ctor.request+field['objectOffset']
        kind = field['type']
        if kind=='string': value = ctor.text(e.read(address)).encode('utf-8')
        elif kind in ('uint32','bool'): value = e.read(address,'B' if kind=='bool' else 'I')
        else: raise ValueError('Unexpected Token scalar schema')
        if value: expected[field['tag']] = (2 if kind=='string' else 0,value)
    if actual!=expected or len(parse_wire(body))!=len(expected):
        raise ValueError('All-field independent source/wire comparison failed')
    return actual


def recover(binary,metadata,startup,records,libraries,wire):
    if wire.get('inputSHA256')!=HASHES or wire.get('typeIndex')!=82491:
        raise ValueError('Unverified Token wire observations')
    transport = declarations(binary,metadata,startup,records)
    by_index = {r['index']:r for r in records}
    encoding = by_index[443]
    for slot,name,count in ((11,'GetByteCount',1),(19,'GetBytes',5)):
        methods = [m for m in encoding['methods'] if m.get('virtualSlot')==slot]
        if len(methods)!=1 or (methods[0]['name'],methods[0]['parameterCount'])!=(name,count):
            raise ValueError('Encoding signature changed')
    if not any(f['name']=='utf8Encoding' and f.get('rawOffset')=='0x40024b8' for f in encoding['fields']):
        raise ValueError('UTF-8 named cache changed')
    cases = [('zero-seed',0.0,True,None),('high-seed',math.nextafter(1.0,0.0),True,None),
             ('unicode',0.5,True,None),('key-wrap',0.5,True,None),
             ('not-initialized',0.5,False,None),('bad-signature',0.5,True,'signature')]
    fixtures,results = [],[]
    for variant,(name,sample,initialized,tamper) in enumerate(cases):
        e = Offline72(binary,libraries,records,True)
        ctor = None
        try:
            e.prepare()
            frame = TokenFrame72(e)
            ctor = TokenConstructor72(e,binary,metadata,variant,'4294967295' if name=='high-seed' else '456')
            frame.ctor = ctor
            if name in ('unicode','key-wrap'):
                value = '账号\U0001f680\x00末尾' if name=='unicode' else 'L'*1000
                for offset in (0x78,0xf8,0xb0): e.write(ctor.account+offset,ctor.string(value))
                e.write(GLOBAL+0x18190,ctor.string(value))
            e.models.pop(OPAQUE_HELPER)
            seed = SeedString72(e,binary,metadata,sample,initialized,0,tamper)
            seed.close()  # Retain the outer constructor's literal storage hook.
            helper_returns,helper_pointers,frame_returns = [],[],[]
            counts,digest = Counter(),hashlib.sha256()
            request_before = []
            sequence,sent_ms = (0,0) if name=='zero-seed' else (0xffffffff,0x200000044)
            def observe(address):
                seed.observe(address)
                counts[address]+=1;digest.update(struct.pack('<Q',address))
                if address==OPAQUE_HELPER: helper_returns.append(e.read(e.reg('rsp')))
                if helper_returns and address==helper_returns[0]:
                    pointer = e.reg('rax')
                    ctor.strings[pointer] = bytes(e.uc.mem_read(pointer+20,e.read(pointer+16,'I')*2)).decode('utf-16-le')
                    helper_pointers.append(pointer)
                if address==0x151214D20:
                    if frame.flow is not None or (e.reg('rcx'),e.reg('rdx'))!=(ctor.sender,ctor.request):
                        raise ValueError('Unexpected continuous send handoff')
                    request_before.append(bytes(e.uc.mem_read(ctor.request,0xc8)))
                    frame.attach(ctor,sequence,sent_ms)
                if address==0x14A447E00:
                    if e.reg('rcx')!=frame.flow.packet or e.read(frame.flow.packet+0x10)!=ctor.request:
                        raise ValueError('Frame lost original request pointer')
                    frame_returns.append(e.read(e.reg('rsp')))
                if frame_returns and address==frame_returns[0]: e.uc.emu_stop()
            e.before_instruction = observe
            e.uc.reg_write(e.R.UC_X86_REG_RSP,STACK+0x100008)
            e.uc.reg_write(e.R.UC_X86_REG_RCX,ctor.sender)
            e.uc.emu_start(0x15121FF70,END,timeout=20_000_000,count=20_000_000)
            if e.failure or len(frame_returns)!=1 or e.reg('rip')!=frame_returns[0] or e.reg('al')!=1:
                raise ValueError(e.failure or 'Continuous Token/frame did not return')
            if (len(helper_pointers),ctor.opaque_calls,ctor.stages)!=(1,0,[8]):
                raise ValueError('Seed/Token stage continuity differs')
            if request_before!=[bytes(e.uc.mem_read(ctor.request,0xc8))] or e.read(ctor.request+0x90)!=helper_pointers[0]:
                raise ValueError('Request or seed pointer mutated during frame write')
            length = e.read(frame.flow.packet+0x30,'I')
            encrypted = bytes(e.uc.mem_read(frame.buffer+32,length))
            plain = bytes(value^frame.key[i%len(frame.key)] for i,value in enumerate(encrypted))
            header_length = int.from_bytes(plain[4:6],'big')
            body = plain[10+header_length:-2]
            values = check_payload(e,ctor,body,wire)
            if encrypted!=expected_frame(24604,body,sequence,sent_ms,frame.key):
                raise ValueError('Independent complete-frame expression differs')
            if e.read(0x145878FC0,'B')!=1: raise ValueError('Token did not select initial key slot')
            for address in (0x15121FF70,OPAQUE_HELPER,0x151214D20,0x1472CD100,
                            0x14A447E00,0x1500C16E0,0x1500C2660,0x1517204C0,0x15171FE80,0x14A448434):
                if not counts[address]: raise ValueError('Missing continuous native stage')
            trace = dict(entry='0x15121ff70',stop=hex(frame_returns[0]),nativePathSHA256=digest.hexdigest(),
                         instructions=e.steps,executedAddresses={hex(a):n for a,n in sorted(counts.items())},
                         models=dict(sorted(Counter(c['address'] for c in e.calls).items())))
            crypto = seed.finish(helper_pointers[0],sample,initialized,tamper)
            seed_wire = values.get(623,(2,b''))[1]
            if seed_wire!=ctor.text(helper_pointers[0]).encode('ascii'):
                raise ValueError('Seed return differs from field623 bytes')
            if name=='unicode' and (not frame.encoding_calls['GetBytes'] or values[9][1]!='账号\U0001f680\x00末尾'.encode('utf8')):
                raise ValueError('Non-ASCII encoding branch missing')
            if name=='key-wrap' and length<=4096: raise ValueError('Long request did not wrap key')
            results.append(dict(name=name,cmdId=24604,stages=ctor.stages,requestPointerPreserved=True,
                                requestUnchanged=True,seedPointerPreserved=True,seedWireLength=len(seed_wire),
                                signatureAccepted=crypto['signatureAccepted'],ioctlReturn=crypto['ioctlReturn'],
                                frameLength=length,payloadLength=len(body),frameSHA256=hashlib.sha256(encrypted).hexdigest(),
                                payloadSHA256=hashlib.sha256(body).hexdigest(),headerWire=plain[10:10+header_length].hex(),
                                selector=1,encodingCalls=dict(frame.encoding_calls),
                                nativePathSHA256=trace['nativePathSHA256'],allFieldWireComparison=True))
            fixtures.append(dict(name=name,initializer=frame.initializer,trace=trace,crypto=crypto,
                                 payloadWire=body.hex(),frameWire=encrypted.hex(),passed=True))
        finally:
            if ctor: ctor.close()
            e.close()
    report = dict(schemaVersion=1,inputSHA256=HASHES,runtimeStatus='not-tested',fixtureCount=len(fixtures),
                  results=results,fixtures=fixtures,transportParameterBindings=transport,
                  continuousTokenFrame=True,payloadWriterExecuted=True,payloadSizeExecuted=True,
                  seedHelperExecuted=True,wholeHandlerReturned=False,socketExecuted=False,
                  models=['Token constructor SDK/platform/old Shell and native crypto OS/storage fixtures',
                          'SendFlow72 explicitly selected transport, vtables, clock/sequence and pool storage',
                          'Frame72 pooled arrays/Array.Copy; no opaque body callbacks are executed',
                          'cached UTF-8 Encoding.GetByteCount/GetBytes; native string writer fast/slow branches execute',
                          'explicit two 4096-byte frame-key arrays; constructor itself switches selector to1'],
                  outsideScope=['real initial frame-key source','actual SDK/startup/IFix','Login/response receive integration',
                                'socket/complete Handler/live client'])
    errors = validate_summary(report)
    if errors: raise ValueError('; '.join(errors))
    return report


def validate_summary(report):
    errors=[]
    if (report.get('inputSHA256')!=HASHES or report.get('runtimeStatus')!='not-tested' or
            any(report.get(k) is not True for k in ('continuousTokenFrame','payloadWriterExecuted','payloadSizeExecuted','seedHelperExecuted')) or
            report.get('wholeHandlerReturned') is not False or report.get('socketExecuted') is not False or
            not report.get('models') or not report.get('outsideScope')): errors.append('Token/frame scope changed')
    results=report.get('results',[])
    if report.get('fixtureCount')!=6 or [r['name'] for r in results]!=[
            'zero-seed','high-seed','unicode','key-wrap','not-initialized','bad-signature']:
        return errors+['Token/frame fixture set changed']
    for row in results:
        if row['cmdId']!=24604 or row['stages']!=[8] or row['selector']!=1 or any(row[k] is not True for k in (
                'requestPointerPreserved','requestUnchanged','seedPointerPreserved','allFieldWireComparison')):
            errors.append('Token/frame continuity changed')
        if row['frameLength']!=12+len(bytes.fromhex(row['headerWire']))+row['payloadLength']:
            errors.append('Token/frame length differs')
        expected_return={'not-initialized':-2,'bad-signature':-7}.get(row['name'],0)
        if row['ioctlReturn']!=expected_return or row['seedWireLength']!=(0 if expected_return else 344):
            errors.append('Seed error behavior changed')
        if row['name']=='unicode' and row['encodingCalls'].get('GetBytes',0)<1: errors.append('Unicode branch missing')
        if row['name']=='key-wrap' and row['frameLength']<=4096: errors.append('Key wrap not covered')
    return errors


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--summary-output',type=Path,required=True)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    wire=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/token-wire-observations.json').read_text('utf8'))
    report=recover(b,m,s,records,a.libraries,wire)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'])))


if __name__=='__main__':main()
