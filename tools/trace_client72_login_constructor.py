"""Execute the Login constructor through its protected continuations to send entry.

This is a bounded offline Windows/non-cloud fixture. Shell's old-provider branch
receives explicit opaque data; the new provider, IFix and real network are not
modeled. SDK wrappers, ByteString wrappers and native field stores execute.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .extract_client72_literals import Literals72
    from .client72_emulator import Offline72, GLOBAL, STACK
    from .client72_session_runtime import fragment
    from .trace_client72_login_semantics import SOURCES, GLOBALS, static_address, static_field
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from extract_client72_literals import Literals72
    from client72_emulator import Offline72, GLOBAL, STACK
    from client72_session_runtime import fragment
    from trace_client72_login_semantics import SOURCES, GLOBALS, static_address, static_field
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire

SDK_FIELDS=((0x18,'info_get_uapc','uapc'),(0x114,'info_get_channel_id','channel_id'),
            (0x158,'info_get_sub_channel_id','sub_channel_id'))
SDK_WIRE={0x18:(1533,'string'),0x114:(12,'uint32'),0x158:(364,'uint32')}


def fixture_uint32(text):
    """Deliberately small Number.TryParseUInt32 oracle; unsupported syntax fails."""
    if not isinstance(text,str) or len(text)>32:raise ValueError('Unsupported UInt32 fixture')
    if not text or any(c not in '0123456789' for c in text):return False,0
    value=int(text)
    return (True,value) if value<=0xffffffff else (False,0)


class Constructor72:
    def __init__(self,e,binary,metadata,variant,channel,subchannel):
        self.e=e;self.variant=variant;self.strings={};self.array_values={};self.stages=[];self.sdk_calls=[];self.shell_calls=[]
        self.allocations={};self.request=None
        self.sdk_values=dict(info_get_uapc=f'uapc_{variant}',info_get_channel_id=channel,info_get_sub_channel_id=subchannel)
        for cell in (0x1458AD418,0x1458C3DF0,0x145899180,0x145898960,0x1458971C0,
                     0x145886090,0x145890290,0x1458ACE48,0x145886BE8,0x1458BC2D8,0x14588D458):
            e.synthetic_type(cell)
        self.classes={e.read(cell):index for cell,index in ((0x1458C3DF0,76410),(0x145898960,18063),
                      (0x1458ACE48,68918),(0x1458BC2D8,45258))}
        self.empty=self.string('');e.write(GLOBAL,self.empty)
        literals=Literals72(binary,metadata)
        self.literal_rows={int(r['cell'],16):r for r in literals.group(literals.global_group)}
        self.literals=literals;self.loaded_literals={}
        self.literal_hook=e.uc.hook_add(e.U.UC_HOOK_MEM_READ,self.load_literal)
        # Actual no-op native icall thunks; verify bytes and execute them.
        for address,target in ((0x148A22550,0x1421C1F40),(0x148A22560,0x1421C1DB0)):
            raw=bytes(e.uc.mem_read(address,5))
            if raw[0]!=0xe9 or address+5+int.from_bytes(raw[1:],'little',signed=True)!=target or bytes(e.uc.mem_read(target,1))!=b'\xc3':
                raise ValueError('Reviewed native return thunk changed')
            e.allowed.extend(((address,address+5),(target,target+1)))
        for address in (0x151213730,0x14A42BD90,0x14D581600,0x147CF3230,0x147CF2450,
                        0x1470EC820,0x14C8B6570,0x14C8B66C0,0x152765A70,0x152763720,0x152758AC0):
            e.allow(address)
        e.models.update({0x14D4480C0:self.allocate,0x14C8AE9A0:self.stage,
                         0x14146AE70:self.shell,0x1471D39E0:lambda:self.sdk,
                         0x1471D4DC0:self.sdk_invoke,0x1531E9510:self.pbase,
                         0x140986A20:lambda:1920+variant,0x140986A40:lambda:1080+variant,
                         0x159AD6130:lambda:self.provider,0x159C9EC10:self.parse_uint,
                         0x159C080F0:lambda:self.encoding,
                         0x159A8D050:lambda:self.string(self.text(e.reg('rcx'))+self.text(e.reg('rdx')))})
        self.sdk=e.alloc();self.provider=e.alloc();e.write(GLOBAL+0xb850,self.sdk)
        self.encoding=e.alloc();encoding_class=e.alloc(1024);e.write(self.encoding,encoding_class)
        # Encoding slot34 is GetString(byte[]); metadata checked by recover().
        e.write(encoding_class+0x1e0,e.model_stub(self.get_string))
        flags=e.alloc();array=self.array((3628).to_bytes(4,'little'))
        e.write(array+24,1);e.write(flags+16,array);e.write(flags+24,1,'I');e.write(GLOBAL+0x6100,flags)
        # Cached non-cloud environment; execute the actual CloudGame predicate.
        e.write(0x14586EA48,1,'B');e.write(0x14586EA49,0,'B')
        account=e.alloc(512);manager=e.alloc(1024);e.write(manager+0x180,account);e.write(GLOBAL+0x9e80,manager)
        e.write(account+0x50,self.string(f'account_source_{variant}'))
        environment=e.alloc();e.write(environment+0x50,e.alloc());e.write(GLOBAL+0xee70,environment)
        e.write(GLOBAL+0x81b8,0)
        self.sender=e.alloc(1024);self.token=self.string(f'session_token_{variant}')
        for ordinal,(off,tag,address,name,meaning) in enumerate(SOURCES):
            matches=[(r,m) for r in e.records.values() for m in r['methods'] if m['registeredAddress']==hex(address)]
            if len(matches)!=1 or matches[0][0]['name']!='Miscs' or matches[0][1]['name']!=name:
                raise ValueError('Named Miscs source changed')
            if name=='GetRobotCheckOutput':value=self.byte_string(f'robot_{variant}'.encode())
            elif name in ('GetServerPlatformType','GetNetworkIP'):value=0x76540000+variant*100+ordinal
            else:value=self.string(f'{name}_{variant}')
            e.models[address]=lambda value=value:value
        for ordinal,(off,tag,raw,owner,name,meaning) in enumerate(GLOBALS):
            field=static_field(e.records,raw,owner,name)
            value=self.string(f'global_{variant}') if field['type']['kind']=='string' else 0x12340000+variant*100+ordinal
            e.write(static_address(raw),value,'Q' if field['type']['kind']=='string' else 'I')

    def close(self):self.e.uc.hook_del(self.literal_hook)

    def load_literal(self,uc,access,address,size,value,data):
        if address in self.literal_rows and address not in self.loaded_literals:
            row=self.literal_rows[address];value=self.literals.decode(row['index'])
            self.e.write(address,self.string(value));self.loaded_literals[address]=dict(index=row['index'],value=value)

    def string(self,text):
        raw=text.encode('utf-16-le')
        if len(raw)>2048:raise ValueError('String outside reviewed fixture capacity')
        p=self.e.alloc(20+len(raw)+2);self.e.write(p+16,len(raw)//2,'I');self.e.uc.mem_write(p+20,raw)
        self.strings[p]=text;return p

    def text(self,pointer):
        if not pointer:return ''
        if pointer not in self.strings:raise ValueError('Unknown managed string')
        return self.strings[pointer]

    def array(self,value):
        if len(value)>4096:raise ValueError('Array outside fixture capacity')
        p=self.e.alloc(32+len(value));self.e.write(p+24,len(value))
        if value:self.e.uc.mem_write(p+32,value)
        self.array_values[p]=value;return p

    def byte_string(self,value):
        p=self.e.alloc(64);self.e.write(p+20,1,'I');self.e.write(p+24,self.array(value));return p

    def allocate(self):
        cls=self.e.reg('rcx');index=self.classes.get(cls)
        if index is None:raise ValueError('Unreviewed managed allocation class')
        if index==18063:return self.byte_string(b'')
        p=self.e.alloc(512);self.e.write(p,cls);self.e.write(p+20,1,'I');self.allocations[p]=index
        for f in self.e.records[index]['fields']:
            if f.get('offsetKind')!='instance':continue
            if f['type']['kind']=='string':self.e.write(p+f['offset'],self.empty)
            elif f['type'].get('typeDefinition')==18063:self.e.write(p+f['offset'],self.byte_string(b''))
        if index==76410:
            if self.request is not None:raise ValueError('Multiple Login request allocations')
            self.request=p
        return p

    def stage(self):self.stages.append(self.e.reg('ecx'));return 0

    def shell(self):
        operation=self.e.reg('ecx')
        if operation not in (0,1,2,3):raise ValueError('Unreviewed opaque Shell provider operation')
        self.shell_calls.append(operation)
        return self.array(f'opaque_{self.variant}_{operation}'.encode())

    def sdk_invoke(self):
        if self.e.reg('rcx')!=self.sdk or self.e.reg('r8')!=0:raise ValueError('Unexpected SDK invocation arguments')
        key=self.text(self.e.reg('rdx'))
        if key not in self.sdk_values:raise ValueError('Unreviewed SDK source key')
        self.sdk_calls.append(key);return self.string(self.sdk_values[key])

    def pbase(self):
        # Explicit output inputs to the constructor; this does not read a host DLL.
        self.e.write(self.e.reg('rcx'),self.empty)
        self.e.write(self.e.reg('rdx'),self.string(f'pbase_fixture_{self.variant}'));return 0

    def parse_uint(self):
        if self.e.reg('edx')!=7 or self.e.reg('r8')!=self.provider:raise ValueError('Unreviewed numeric parse mode')
        text=self.text(self.e.reg('rcx'))
        if text not in self.sdk_values.values():raise ValueError('Unreviewed numeric source')
        ok,value=fixture_uint32(text);self.e.write(self.e.reg('r9'),value,'I');return int(ok)

    def get_string(self):
        if self.e.reg('rcx')!=self.encoding:raise ValueError('Unexpected encoding instance')
        value=self.array_values.get(self.e.reg('rdx'))
        if value is None:raise ValueError('Unknown encoded byte array')
        return self.string(value.decode('ascii'))

    def values(self,index,pointer,writer):
        values={}
        for f in self.e.records[index]['fields']:
            if f.get('offsetKind')!='instance':continue
            off=f['offset'];kind=f['type']['kind']
            if kind=='string':values[off]=self.text(self.e.read(pointer+off)).encode('ascii')
            elif kind in ('bool','uint32','int32','uint64','valuetype'):
                values[off]=self.e.read(pointer+off,'B' if kind=='bool' else 'Q' if kind=='uint64' else 'I')
            elif kind=='class':
                child=self.e.read(pointer+off)
                if not child:values[off]=None
                elif f['type']['typeDefinition']==18063:
                    array=self.e.read(child+24)
                    if array not in self.array_values:raise ValueError('Unknown ByteString storage')
                    values[off]=('bytes',self.array_values[array])
                else:
                    child_index=f['type']['typeDefinition']
                    if self.allocations.get(child)!=child_index:raise ValueError('Unexpected nested message')
                    encoded=writer.run(child_index,self.values(child_index,child,writer))
                    values[off]=('message',bytes.fromhex(encoded['wire']))
            else:
                if self.e.read(pointer+off):raise ValueError('Unreviewed nonempty container')
                values[off]=None
        return values


def recover(binary,metadata,records,libraries,wire):
    if wire.get('inputSHA256')!=HASHES:raise ValueError('Wire input mismatch')
    encoding=next(r for r in records if r['index']==443)
    slot=[m for m in encoding['methods'] if m.get('virtualSlot')==34]
    if len(slot)!=1 or slot[0]['name']!='GetString' or slot[0]['parameterCount']!=1:
        raise ValueError('Encoding virtual slot identity mismatch')
    fixtures=[];bindings=[]
    for variant,(channel,subchannel) in enumerate((('123','456'),('4294967295','0'),('4294967296','bad'))):
        e=Offline72(binary,libraries,records,True);runtime=None
        try:
            e.prepare();runtime=Constructor72(e,binary,metadata,variant,channel,subchannel)
            trace=fragment(e,0x151213730,{0x151214D20},dict(rsp=STACK+0x100008,rcx=runtime.sender,rdx=runtime.token,r8=0x80000000+variant))
            if e.reg('rcx')!=runtime.sender or e.reg('rdx')!=runtime.request or runtime.stages!=[6]:
                raise ValueError('Constructor did not hand its allocated request to send entry')
            if runtime.sdk_calls!=[key for off,key,name in SDK_FIELDS] or runtime.shell_calls!=[3,0,1,2]:
                raise ValueError('Constructor environment source call sequence changed')
            writer=Writer72(e);values=runtime.values(76410,runtime.request,writer)
            result=writer.run(76410,values);decoded={tag:(kind,value) for tag,kind,value in parse_wire(bytes.fromhex(result['wire']))}
            message=next(m for m in wire['messages'] if m['typeIndex']==76410)
            for off,key,name in SDK_FIELDS:
                f=next(f for f in message['fields'] if f['objectOffset']==off)
                if (f['tag'],f['type'])!=SDK_WIRE[off]:raise ValueError('Independent SDK field tag/type disagrees')
                expected=runtime.sdk_values[key].encode() if off==0x18 else fixture_uint32(runtime.sdk_values[key])[1]
                if values[off]!=expected:raise ValueError('SDK source did not reach native request field')
                actual=decoded.get(f['tag'],(2 if isinstance(expected,bytes) else 0,b'' if isinstance(expected,bytes) else 0))
                if actual!=(2 if isinstance(expected,bytes) else 0,expected):raise ValueError('SDK source to native wire mismatch')
                if variant==0:bindings.append(dict(typeIndex=76410,clientType=message['clientType'],clientField=f['clientField'],
                    objectOffset=off,tag=f['tag'],type=f['type'],semanticField=name,sourceKey=key,status='verified',scope='native-sdk-key-to-wire'))
            # Independent tags for the opaque native old-Shell data paths. These
            # checks do not assign a business name to either field.
            opaque=b''.join(f'opaque_{variant}_{i}'.encode() for i in (0,1,2))
            if decoded.get(624)!=(2,opaque) or decoded.get(1105)!=(2,f'opaque_{variant}_3'.encode()):
                raise ValueError('Opaque Shell provider data not preserved to independent wire tags')
            fixtures.append(dict(variant=variant,channel=channel,subChannel=subchannel,wire=result['wire'],
                                 trace=trace,sdkCalls=runtime.sdk_calls,shellOperations=runtime.shell_calls,
                                 loadedLiterals={hex(k):v for k,v in sorted(runtime.loaded_literals.items())},passed=True))
        finally:
            if runtime:runtime.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fieldBindings=bindings,fixtures=fixtures,
                entry='0x151213730',stop='0x151214d20',runtimeStatus='not-tested',
                opaqueSourceBindings=[dict(objectOffset=0xa8,tag=624,type='string',operations=[0,1,2],semanticStatus='unresolved'),
                                      dict(objectOffset=0x38,tag=1105,type='bytes',operations=[3],semanticStatus='unresolved')],
                models=['explicit Miscs/SDK/platform/Pbase outputs','old Shell opaque bytes for operations 0..3',
                        'managed allocations and metadata literal strings','ASCII Encoding.GetString/String.Concat',
                        'bounded decimal UInt32 parse and culture provider','stage capture'],
                environment=['native flag-list contains 3628; old Shell branch','native cached non-cloud predicate',
                             'empty optional environment feature list','default zero optional global flags'],
                outsideScope=['new Shell provider','cloud path','IFix','send queue body/network','real client'])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    wire=json.loads((Path(__file__).resolve().parents[1]/'protocol/cn-7.2/login-wire-observations.json').read_text('utf8'))
    report=recover(b,m,records,a.libraries,wire);summary={k:v for k,v in report.items() if k!='fixtures'}
    for path,value in ((a.output,report),(a.summary_output,summary)):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],fields=len(report['fieldBindings']))))


if __name__=='__main__':main()
