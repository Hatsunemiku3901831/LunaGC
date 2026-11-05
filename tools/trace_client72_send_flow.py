"""Execute bounded request -> envelope -> transport -> packet-builder handoff.

The transport implementation is an explicit offline selection (AKFCDHJBCHD).
No socket, frame builder, encryption, IFix or live client is executed. Token
construction retains its separately documented opaque seed-helper boundary.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, GLOBAL, STACK, END
    from .client72_session_runtime import fragment
    from .trace_client72_login_constructor import Constructor72
    from .trace_client72_token_constructor import TokenConstructor72
    from .trace_client72_request_identity import self_cell
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire, varint
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, GLOBAL, STACK, END
    from client72_session_runtime import fragment
    from trace_client72_login_constructor import Constructor72
    from trace_client72_token_constructor import TokenConstructor72
    from trace_client72_request_identity import self_cell
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire, varint

ROLE_DATA=((82491,'GetPlayerTokenReq',24604,0x1458C5500,0x15121FF70),
           (76410,'PlayerLoginReq',23172,0x1458C3DF0,0x151213730))
RETURN_GATE=END+0x100


def slot_method(row,slot,address):
    found=[m for m in row['methods'] if m.get('virtualSlot')==slot]
    if (len(found)!=1 or int(found[0]['registeredAddress'],16)!=address or
            int(found[0].get('flags','0'),16)&0x10):
        raise ValueError('Declared native virtual slot changed')
    return found[0]


def declarations(binary,metadata,startup,records):
    by_index={r['index']:r for r in records}
    for slot,address in ((14,0x1495C9DC0),(20,0x1495C9F60),(12,0x1495C9D70)):
        slot_method(by_index[67861],slot,address)
    slot_method(by_index[13973],6,0x1516443C0)
    slot_method(by_index[79029],4,0)
    slot_method(by_index[32016],8,0x1472CCF80)
    fields={(f.get('offset'),f['type'].get('typeDefinition')) for f in by_index[13973]['fields']}
    if (0x10,67861) not in fields:raise ValueError('Envelope message field changed')
    pe=PE(binary);h=pe.offset(HEADER_VA)
    method_base=BODY+(number(binary,h+0xb0)^0x51324a1f)
    param_base=BODY+((number(binary,h+0x174)+0xae6bcd68)&0xffffffff)
    resolver=TypeResolver(pe,startup,records,h);bindings=[]
    for owner,index in ((79029,655015),(68785,569162),(32016,250987)):
        methods=[m for m in by_index[owner]['methods'] if m['index']==index]
        if len(methods)!=1 or methods[0]['parameterCount']!=1:raise ValueError('Transport method declaration changed')
        first=parameter_start(index,number(metadata,method_base+index*26+4))
        _,ref=parameter_words(first,number(metadata,param_base+first*8),number(metadata,param_base+first*8+4))
        parameter=resolver.resolve_index(ref)
        if parameter.get('typeDefinition')!=13973 or parameter.get('byRef'):
            raise ValueError('Transport parameter is not the target envelope')
        bindings.append(dict(ownerTypeIndex=owner,methodIndex=index,parameterIndex=first,
                             parameterTypeIndex=ref,envelopeTypeIndex=13973))
    return bindings


class SendFlow72:
    def __init__(self,e,runtime,index,command,sequence,sent_ms,mode):
        self.e=e;self.runtime=runtime;self.mode=mode;self.pool_calls=0;self.packet_calls=0
        request_class=e.read(runtime.request)
        e.write(request_class+0xbe,25,'H')
        getter=slot_method(e.records[index],22,0x1500C4A50 if index==82491 else 0x152EF20F0)
        if getter.get('literalU16Candidate')!=command:raise ValueError('Request command getter mismatch')
        for slot,address in ((14,0x1495C9DC0),(20,0x1495C9F60),(12,0x1495C9D70),(22,int(getter['registeredAddress'],16))):
            e.write(request_class+0xd0+slot*8,address);e.allow(address)
        # Explicit packet-representation branch; no claim that startup/IFix has
        # selected this branch in a real running client.
        e.write(0x1458715C0,1,'B');e.synthetic_type(0x14589F4F8)
        registry=e.alloc();e.write(registry+0x10,e.alloc());e.write(GLOBAL+0xd3c0,registry)
        self.pool=e.alloc();pool_class=e.alloc(512);e.write(self.pool,pool_class);e.write(GLOBAL+0x5d60,self.pool)
        self.envelope=e.alloc();envelope_class=e.alloc(512);e.write(self.envelope,envelope_class)
        e.write(envelope_class+0x100,0x1516443C0);e.write(self.envelope+0x1c,sequence,'I')
        self.envelope_type=e.synthetic_type(0x1458C39F8)
        e.write(pool_class+0xf0,e.model_stub(self.allocate_envelope))
        wrapper=e.alloc();self.transport=e.alloc(512);tc=e.alloc(2048)
        e.write(wrapper+0x10,self.transport);e.write(self.transport,tc)
        e.write(runtime.sender+0x58,0 if mode=='missing-wrapper' else wrapper)
        iface=e.synthetic_type(0x1458C72D0);table=e.alloc();e.write(table,iface)
        # Target interface slot4 -> concrete declaration slot8, offset4.
        e.write(table+8,4,'I');e.write(tc+0x80,table);e.write(tc+0xc0,1,'H');e.write(tc+0xbe,64,'H')
        e.write(tc+0xd0+8*8,0x1472CCF80)
        e.write(0x14586D047,int(mode!='disabled-transport'),'B')
        e.write(0x14586D06A,0,'B');e.write(0x145B7E9A1,1,'B')
        e.write(self.transport+0x88,e.alloc());e.write(self.transport+0x28,e.alloc())
        stream=e.alloc();e.write(self.transport+0x20,stream)
        e.write(stream+0x3c,1,'B');e.write(stream+0x3d,1,'B');e.write(stream+0x40,32768,'I')
        self.packet=e.alloc();self.header=e.alloc();e.write(self.header+0x14,1,'I');e.write(self.packet+0x18,self.header)
        e.models[0x14A446A70]=self.allocate_packet
        e.write(0x1458733E0,1000000000000)
        e.write(0x145873380,1000000000000+sent_ms*10000)
        for address in (0x151214D20,0x151A933A0,0x149935970,0x1472CCF80,0x1472CD100,
                        0x1516443C0,0x159BCE3C0,0x14A4489D0):e.allow(address)
        e.allow_unwind(0x1574896C0)  # HashSet<ushort>.Contains; empty set native path.

    def allocate_envelope(self):
        e=self.e
        if (e.reg('rcx')!=self.pool or e.reg('rdx')!=self.envelope_type or e.reg('r8')!=0 or self.pool_calls):
            raise ValueError('Unexpected envelope pool request')
        self.pool_calls+=1;return self.envelope

    def allocate_packet(self):
        if self.e.reg('ecx')!=0x50925 or self.packet_calls:
            raise ValueError('Unexpected framed-packet allocation')
        self.packet_calls+=1;return self.packet


def recover(binary,metadata,startup,records,libraries):
    signatures=declarations(binary,metadata,startup,records);fixtures=[];requests=[]
    for index,name,command,cell,entry in ROLE_DATA:
        association=self_cell(binary,next(r for r in records if r['index']==index),cell,command)
        requests.append(dict(semanticName=name,typeIndex=index,cmdId=command,constructorEntry=hex(entry),
                             commandGetter=association['getter'],selfClassCell=association['selfClassCell']))
        for variant,mode in enumerate(('connected','connected-high-header','disabled-transport','missing-wrapper')):
            e=Offline72(binary,libraries,records,True);runtime=None
            try:
                e.prepare()
                runtime=(TokenConstructor72(e,binary,metadata,variant,'456') if index==82491 else
                         Constructor72(e,binary,metadata,variant,'123','456'))
                ctor=fragment(e,entry,{0x151214D20},dict(rsp=STACK+0x100008,rcx=runtime.sender,rdx=runtime.token,r8=0x80000000+variant))
                if e.reg('rdx')!=runtime.request or e.reg('rcx')!=runtime.sender:
                    raise ValueError('Constructor did not preserve its request/sender')
                sequence=0xffffffff if variant==1 else 1
                sent_ms=0x200000044 if variant==1 else 1234567
                flow=SendFlow72(e,runtime,index,command,sequence,sent_ms,mode)
                # Unlike the emulator's END sentinel, this mapped return gate
                # is visited by the code hook before its instruction executes.
                e.uc.mem_map(END,4096);e.uc.mem_write(RETURN_GATE,b'\xc3')
                e.write(STACK+0x100008,RETURN_GATE)
                size=0xc8 if index==82491 else 0x160
                before=bytes(e.uc.mem_read(runtime.request,size))
                trace=fragment(e,0x151214D20,{0x14A447E00,RETURN_GATE},dict(rsp=STACK+0x100008,rcx=runtime.sender,rdx=runtime.request))
                if bytes(e.uc.mem_read(runtime.request,size))!=before:raise ValueError('Send routing mutated the request')
                connected=mode.startswith('connected')
                if trace['stop']!=hex(0x14A447E00 if connected else RETURN_GATE):raise ValueError('Unexpected transport handoff boundary')
                if (flow.pool_calls,flow.packet_calls)!=(int(mode!='missing-wrapper'),int(connected)):
                    raise ValueError('Unexpected envelope/packet allocation count')
                if mode!='missing-wrapper' and e.read(flow.envelope+0x10)!=runtime.request:
                    raise ValueError('Envelope lost original request pointer')
                header_wire=None;request_wire=None
                if connected:
                    if (e.reg('rcx')!=flow.packet or e.read(flow.packet+0x10)!=runtime.request or
                            e.read(flow.packet+0x22,'H')!=command or e.read(flow.header+0x30,'I')!=sequence or
                            e.read(flow.header+0x18)!=sent_ms or e.read(flow.header+0x20,'I')!=0):
                        raise ValueError('Native packet builder arguments/command/header mismatch')
                    writer=Writer72(e)
                    request_values=runtime.values(index,runtime.request,writer)
                    header_values=runtime.values(27182,flow.header,writer)
                    request_wire=writer.run(index,request_values)['wire']
                    header_wire=writer.run(27182,header_values)['wire']
                    expected=b'\x18'+varint(sequence)+b'\x30'+varint(sent_ms)
                    if bytes.fromhex(header_wire)!=expected:raise ValueError('Independent header wire mismatch')
                    decoded={t:(k,v) for t,k,v in parse_wire(bytes.fromhex(request_wire))}
                    token_tag=623 if index==82491 else 1
                    expected_token=runtime.opaque_value if index==82491 else runtime.text(runtime.token)
                    if decoded.get(token_tag)!=(2,expected_token.encode('ascii')):
                        raise ValueError('Constructed request data changed before serialization')
                fixtures.append(dict(semanticName=name,mode=mode,cmdId=command,constructor=ctor,send=trace,
                                     headerWire=header_wire,requestWire=request_wire,requestUnchanged=True,
                                     commandGetter=association['getter'],passed=True))
            finally:
                if runtime:runtime.close()
                e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,runtimeStatus='not-tested',fixtureCount=len(fixtures),fixtures=fixtures,
                transportParameterBindings=signatures,requestBindings=requests,
                entry='0x151214d20',connectedStop='0x14a447e00',frameBuilderExecuted=False,
                chain=['151214D20 request envelope store +10','149935970 interface slot4',
                       '1472CCF80 concrete slot8','1472CD100 packet command/header/body assignment',
                       '14A447E00 boundary before frame build'],
                models=['explicit synthetic classes/vtables and selected interface implementation',
                        'typed envelope/packet pool allocation; native assignments execute',
                        'explicit connected transport, empty filter set and managed stream storage',
                        'explicit clock ticks and sequence inputs','constructor and writer models listed in their separate reports'],
                environment=['1458715C0 true selects native command getter mode','14586D06A false selects normal transport path',
                             '14586D047 varied enabled/disabled','no IFix replacement'],
                outsideScope=['actual transport selection/startup ABI','frame builder body/encryption/socket',
                              'other transport implementation BHBBNHFEPPB','Token encrypted-seed helper 15121DB40','real client acceptance'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf-8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],stop=report['connectedStop'])))


if __name__=='__main__':main()
