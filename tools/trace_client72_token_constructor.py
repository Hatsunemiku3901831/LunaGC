"""Trace Token construction with an explicit opaque encrypted-seed boundary.

15121DB40 is NOT executed or claimed successful here. Its returned managed
string is a fixture input. The client executes the surrounding constructor,
SDK wrapper, old-Shell operation-5 consumer, field stores and request writer.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL, STACK
    from .client72_session_runtime import fragment
    from .trace_client72_login_constructor import Constructor72, fixture_uint32
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL, STACK
    from client72_session_runtime import fragment
    from trace_client72_login_constructor import Constructor72, fixture_uint32
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire

OPAQUE_HELPER = 0x15121DB40
# These offsets are source observations, NOT inferred account-field names.
SESSION_FIELDS = ((0x78,0x18,9,'string'),(0xf8,0x40,2,'string'),
                  (0xb0,0x70,965,'string'),(0x138,0xb0,3,'uint32'),
                  (0x13c,0xa4,15,'uint32'),(0x15f,0xc4,11,'bool'))


class TokenConstructor72(Constructor72):
    def __init__(self,e,binary,metadata,variant,subchannel,empty=False):
        super().__init__(e,binary,metadata,variant,'0',subchannel)
        cls=e.synthetic_type(0x1458C5500);self.classes[cls]=82491
        for cell in (0x1458C5508,0x14589F4F8):e.synthetic_type(cell)
        for entry in (0x15121FF70,0x152758600):e.allow(entry)
        self.opaque_value='' if empty else f'opaque_seed_boundary_{variant}'
        self.opaque_pointer=self.string(self.opaque_value);self.opaque_calls=0
        e.models[OPAQUE_HELPER]=self.seed_boundary
        self.account=e.read(e.read(GLOBAL+0x9e80)+0x180)
        self.session_values={}
        for source,off,tag,kind in SESSION_FIELDS:
            if kind=='string':value='' if empty else f'session_{variant}_{source:x}'
            elif kind=='bool':value=int(not empty)
            else:value=0 if empty else (0xffffffff if variant==1 else 0x87650000+source)
            self.session_values[source]=value
            e.write(self.account+source,self.string(value) if kind=='string' else value,
                    'Q' if kind=='string' else 'B' if kind=='bool' else 'I')
        # Unnamed target global inputs; never assign business semantics from
        # their neighboring fields or legacy schema names.
        self.global_values={0x145878704:0 if empty else 0x12340001+variant,
                            0x145878700:0 if empty else 0x12340011+variant}
        for address,value in self.global_values.items():e.write(address,value,'I')
        self.global_string='' if empty else f'global_18190_{variant}'
        e.write(GLOBAL+0x18190,self.string(self.global_string))

    def allocate(self):
        p=super().allocate()
        if self.allocations.get(p)==82491:
            if self.request is not None:raise ValueError('Multiple Token request allocations')
            self.request=p
        return p

    def seed_boundary(self):
        if self.e.reg('rcx')!=self.sender:raise ValueError('Opaque seed boundary receiver changed')
        self.opaque_calls+=1
        return self.opaque_pointer

    def shell(self):
        if self.e.reg('ecx')!=5:raise ValueError('Unexpected Token old-Shell operation')
        self.shell_calls.append(5)
        return self.array(f'opaque_token_shell5_{self.variant}'.encode('ascii'))


def recover(binary,metadata,records,libraries,wire):
    if wire.get('inputSHA256')!=HASHES or wire.get('typeIndex')!=82491:
        raise ValueError('Token wire input mismatch')
    by_index={row['index']:row for row in records}
    encoding=[m for m in by_index[443]['methods'] if m.get('virtualSlot')==34]
    if len(encoding)!=1 or encoding[0]['name']!='GetString' or encoding[0]['parameterCount']!=1:
        raise ValueError('Encoding virtual slot identity mismatch')
    for source,off,tag,kind in SESSION_FIELDS:
        fields=[f for f in by_index[58013]['fields'] if f.get('offsetKind')=='instance' and f['offset']==source]
        if len(fields)!=1 or fields[0]['type']['kind']!=kind:
            raise ValueError('Raw session source offset/type mismatch')
    fixtures=[];binding=None
    for variant,(subchannel,empty) in enumerate((('456',False),('4294967295',False),('4294967296',True),('bad',True))):
        e=Offline72(binary,libraries,records,True);runtime=None
        try:
            e.prepare();runtime=TokenConstructor72(e,binary,metadata,variant,subchannel,empty)
            trace=fragment(e,0x15121FF70,{0x151214D20},dict(rsp=STACK+0x100008,rcx=runtime.sender))
            if e.reg('rcx')!=runtime.sender or e.reg('rdx')!=runtime.request or runtime.stages!=[8]:
                raise ValueError('Token request/stage not preserved to send entry')
            if runtime.sdk_calls!=['info_get_sub_channel_id'] or runtime.shell_calls!=[5] or runtime.opaque_calls!=1:
                raise ValueError('Token constructor source sequence changed')
            if e.read(0x145878fc0,'B')!=1:raise ValueError('Token constructor did not set key-switch marker')
            writer=Writer72(e);values=runtime.values(82491,runtime.request,writer)
            result=writer.run(82491,values)
            decoded={tag:(kind,value) for tag,kind,value in parse_wire(bytes.fromhex(result['wire']))}
            def assert_wire(off,tag,kind,expected):
                row=next(f for f in wire['fields'] if f['objectOffset']==off)
                if (row['tag'],row['type'])!=(tag,kind):raise ValueError('Independent Token binding differs from wire observations')
                val=expected.encode('ascii') if kind=='string' else expected
                if values[off]!=val:raise ValueError('Token source differs from stored field')
                wt,zero=(2,b'') if kind=='string' else (0,0)
                if decoded.get(tag,(wt,zero))!=(wt,val):raise ValueError('Token source differs from native wire')
                return row
            f=assert_wire(0xb4,10,'uint32',fixture_uint32(subchannel)[1])
            if binding is None:
                binding=dict(typeIndex=82491,clientType=wire['clientType'],clientField=f['clientField'],
                             objectOffset=0xb4,tag=10,type='uint32',semanticField='sub_channel_id',
                             sourceKey='info_get_sub_channel_id',status='verified',scope='native-sdk-key-to-wire')
            for source,off,tag,kind in SESSION_FIELDS:
                assert_wire(off,tag,kind,runtime.session_values[source])
            assert_wire(0x90,623,'string',runtime.opaque_value)
            assert_wire(0x50,1300,'string',f'opaque_token_shell5_{variant}')
            assert_wire(0x98,1184,'uint32',4)
            assert_wire(0x78,83,'string',runtime.global_string)
            assert_wire(0xbc,1206,'uint32',runtime.global_values[0x145878704])
            assert_wire(0xc0,561,'uint32',runtime.global_values[0x145878700])
            fixtures.append(dict(variant=variant,subChannel=subchannel,emptySessionFields=empty,
                                 wire=result['wire'],trace=trace,sdkCalls=runtime.sdk_calls,
                                 shellOperations=runtime.shell_calls,opaqueSeedHelperCalls=runtime.opaque_calls,
                                 loadedLiterals={hex(k):v for k,v in sorted(runtime.loaded_literals.items())},passed=True))
        finally:
            if runtime:runtime.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fieldBindings=[binding],fixtures=fixtures,
                entry='0x15121ff70',stop='0x151214d20',runtimeStatus='not-tested',
                completion='constructor-with-opaque-seed-boundary',
                opaqueSeedBoundary=dict(address=hex(OPAQUE_HELPER),nativeExecuted=False,
                                        objectOffset=0x90,tag=623,type='string',semanticStatus='unresolved'),
                rawSessionBindings=[dict(sessionOffset=s,objectOffset=o,tag=t,type=k,semanticStatus='unresolved')
                                    for s,o,t,k in SESSION_FIELDS],
                rawOtherBindings=[dict(objectOffset=0x50,tag=1300,type='string',source='old Shell operation 5',semanticStatus='unresolved'),
                                  dict(objectOffset=0x98,tag=1184,type='uint32',source='literal 4',semanticStatus='unresolved'),
                                  dict(objectOffset=0x78,tag=83,type='string',source='global +18190',semanticStatus='unresolved'),
                                  dict(objectOffset=0xbc,tag=1206,type='uint32',source='0x145878704',semanticStatus='unresolved'),
                                  dict(objectOffset=0xc0,tag=561,type='uint32',source='0x145878700',semanticStatus='unresolved')],
                models=['explicit opaque managed string result at 15121DB40; no cryptography success claim',
                        'explicit Miscs/SDK/platform inputs','old Shell opaque bytes for operation 5',
                        'managed allocations and target metadata literal strings','ASCII Encoding.GetString',
                        'bounded decimal UInt32 parse and culture provider','stage capture'],
                environment=['native flag-list contains 3628; old Shell branch','native cached non-cloud predicate',
                             'SDK +c8 false; no optional pre-login SDK action','default zero optional global flags'],
                outsideScope=['15121DB40 encrypted seed construction and Shell 0x64','new Shell provider','cloud path',
                              'IFix','send queue body/network','real client'])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',required=True,type=Path);parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--summary-output',required=True,type=Path)
    parser.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=parser.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):parser.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    wire=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/token-wire-observations.json').read_text('utf-8'))
    report=recover(b,m,records,a.libraries,wire)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf-8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],fields=len(report['fieldBindings']),completion=report['completion'])))


if __name__=='__main__':main()
