"""Trace SDK JSON credentials to session fields and native Token construction.

JSON parsing/container storage and SDK return strings are declared fixtures.
Native JSON scalar getters, source checks, stores, seed crypto, constructor and
writer execute. UI work after the selected callback boundary is outside scope.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES,read_verified,extract,PE,number,TypeResolver,HEADER_VA
    from .extract_client72_literals import Literals72,USAGE_REGISTRATION
    from .client72_emulator import Offline72,GLOBAL,STACK
    from .client72_session_runtime import fragment
    from .trace_client72_token_constructor import TokenConstructor72
    from .trace_client72_login_constructor import fixture_uint32
    from .trace_client72_token_complete import construct_native_token
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire
except ImportError:
    from extract_client72 import HASHES,read_verified,extract,PE,number,TypeResolver,HEADER_VA
    from extract_client72_literals import Literals72,USAGE_REGISTRATION
    from client72_emulator import Offline72,GLOBAL,STACK
    from client72_session_runtime import fragment
    from trace_client72_token_constructor import TokenConstructor72
    from trace_client72_login_constructor import fixture_uint32
    from trace_client72_token_complete import construct_native_token
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire

ENTRY,SUCCESS,EARLY,ERROR=0x14F1917D0,0x14F191E18,0x14F192592,0x14F191882
FIELDS=((0x78,0x18,9,'string','account_uid','data.open_id'),
        (0xf8,0x40,2,'string','account_token','data.combo_token'),
        (0x138,0xb0,3,'uint32','channel_id','SDK info_get_channel_id'),
        (0x13c,0xa4,15,'uint32','account_type','data.account_type'),
        (0x15f,0xc4,11,'bool','is_guest','data.guest'))


class AuthSources72(TokenConstructor72):
    def __init__(self,e,binary,metadata,variant,channel):
        super().__init__(e,binary,metadata,variant,'456')
        self.sdk_values.update(info_get_device_id='auth_device_fixture',info_get_channel_id=channel,
                               login_get_asterisk_name='auth_display_fixture')
        e.write(self.account+0x13c,0,'I')
        for address in (ENTRY,0x152765180,0x152765200,0x1471680C0,0x147168150,0x147168440):e.allow(address)
        self.lazy=e.synthetic_type(0x145891A78);e.write(self.lazy+0xc4,1,'B')
        hierarchy=e.alloc();e.write(hierarchy,self.lazy);e.write(self.lazy+0x48,hierarchy)
        e.synthetic_type(0x1458862F0)
        self.normal=e.alloc(1024);self.nodes={};self.missing=object();self.json_reads=[];self.logs=[]
        for slot,method in ((6,self.get_item),(8,self.get_value),(14,self.contains)):
            stub=e.model_stub(method);e.write(self.normal+0xd0+slot*8,stub);e.write(self.lazy+0xd0+slot*8,stub)
        for slot,address in ((22,0x1471680C0),(24,0x147168150),(34,0x147168440)):
            e.write(self.normal+0xd0+slot*8,address)
        e.models.update({0x147145280:self.parse_json,0x1531FDB80:self.log,
                         0x159C9DF60:self.parse_int,0x159C0CBB0:self.parse_bool})
        self.controller=e.alloc(512);e.write(self.controller+0x167,1,'B');e.write(GLOBAL+0x9ec0,self.controller)
        self.page=e.alloc(1024)

    def node(self,value,path):
        p=self.e.alloc();self.e.write(p,self.lazy if value is self.missing else self.normal)
        self.nodes[p]=(value,path);return p

    def parse_json(self):return self.node(json.loads(self.text(self.e.reg('rcx'))),'$')

    def get_item(self):
        value,path=self.nodes[self.e.reg('rcx')];key=self.text(self.e.reg('rdx'))
        self.json_reads.append(dict(operation='get',path=path,key=key))
        return self.node(value.get(key,self.missing),path+'.'+key)

    def contains(self):
        value,path=self.nodes[self.e.reg('rcx')];key=self.text(self.e.reg('rdx'))
        self.json_reads.append(dict(operation='contains',path=path,key=key));return int(key in value)

    def get_value(self):
        value,path=self.nodes[self.e.reg('rcx')];self.json_reads.append(dict(operation='value',path=path))
        if type(value) is bool:text='true' if value else 'false'
        elif type(value) in (str,int):text=str(value)
        else:raise ValueError('Fixture scalar outside reviewed JSON types')
        return self.string(text)

    def parse_uint(self):
        e=self.e
        if e.reg('edx')!=7 or e.reg('r8')!=self.provider:raise ValueError('Unexpected UInt32 parse mode')
        ok,value=fixture_uint32(self.text(e.reg('rcx')));e.write(e.reg('r9'),value,'I');return int(ok)

    def parse_int(self):
        e=self.e
        if e.reg('edx')!=7 or e.reg('r8')!=self.provider:raise ValueError('Unexpected Int32 parse mode')
        value=int(self.text(e.reg('rcx')));ok=-(1<<31)<=value<(1<<31)
        e.write(e.reg('r9'),value&0xffffffff if ok else 0,'I');return int(ok)

    def parse_bool(self):
        text=self.text(self.e.reg('rcx'))
        if text not in ('true','false'):raise ValueError('Bool fixture is not canonical')
        self.e.write(self.e.reg('rdx'),int(text=='true'),'B');return 1

    def log(self):self.logs.append(self.text(self.e.reg('rcx')));return 0

    def session_snapshot(self):
        return {name:self.text(self.e.read(self.account+off)) if kind=='string' else
                self.e.read(self.account+off,'B' if kind=='bool' else 'I') for off,_,_,kind,name,_ in FIELDS}


def declarations(binary,metadata,startup,records):
    by_index={r['index']:r for r in records};node=by_index[8987]
    if (node['namespace'],node['name'])!=('SimpleJSON','JSONNode'):raise ValueError('JSON base identity changed')
    slots=[]
    for slot,name,address in ((6,'get_Item',0x147167DF0),(8,'get_Value',0x147167E10),(14,'ContainsKey',0x147167E80),
                              (22,'get_AsInt',0x1471680C0),(24,'get_AsUInt',0x147168150),(34,'get_AsBool',0x147168440)):
        matches=[m for m in node['methods'] if m['virtualSlot']==slot]
        if len(matches)!=1 or (matches[0]['name'],matches[0]['registeredAddress'])!=(name,hex(address)):
            raise ValueError('Native JSON virtual declaration changed')
        slots.append(dict(slot=slot,name=name,address=hex(address)))
    for owner,name,address in ((8987,'Parse',0x147145280),(12832,'GenshinCommonLog',0x1531FDB80)):
        if not any(m['name']==name and m['registeredAddress']==hex(address) for m in by_index[owner]['methods']):
            raise ValueError('Auth parser/log boundary identity changed')
    # Exact target usage row proves the class used by native missing-key checks.
    pe=PE(binary);literals=Literals72(binary,metadata);i=701114;key=(i*0x57c15286+0x26ddebda)&0xffffffff
    source=((number(metadata,literals.usages_base+i*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
    slot=number(metadata,literals.usages_base+i*8+4)^key^0x1855aa65
    cache=number(binary,pe.offset(USAGE_REGISTRATION)+0x48,'Q')
    resolver=TypeResolver(pe,startup,records,pe.offset(HEADER_VA));resolved=resolver.resolve_index(source&0x1fffffff)
    if source>>29!=1 or cache+slot*8!=0x145891A78 or resolved.get('typeDefinition')!=9000 or resolved.get('name')!='JSONLazyCreator':
        raise ValueError('Missing-key runtime class binding changed')
    return dict(jsonType=8987,slots=slots,lazyClassType=9000,lazyClassUsageRow=i,lazyClassCell='0x145891a78')


def recover(binary,metadata,startup,records,libraries,wire):
    declared=declarations(binary,metadata,startup,records);bindings=[]
    for off,dest,tag,kind,name,source in FIELDS:
        field=next(f for f in wire['fields'] if f['objectOffset']==dest)
        if (field['tag'],field['type'])!=(tag,kind):raise ValueError('Auth source/raw wire binding differs')
        bindings.append(dict(typeIndex=82491,clientField=field['clientField'],sessionOffset=off,objectOffset=dest,
                             tag=tag,type=kind,semanticField=name,source=source,status='verified'))
    basic={'open_id':'account-fixture-A','combo_token':'combo-token-A','guest':True,'account_type':1}
    cases=[('nonzero',0,basic,'123',True),
           ('uint32-max',0,dict(basic,open_id='9007199254740993',guest=False,account_type=0xffffffff),'4294967295',True),
           ('optional-absent',0,{'open_id':'account-fixture-B','combo_token':'combo-token-B'},'0',True),
           ('account-type-overflow',0,dict(basic,account_type=1<<32),'123',True),
           ('error-ret',-1,basic,'123',False),
           ('missing-open-id',0,{k:v for k,v in basic.items() if k!='open_id'},'123',False),
           ('empty-token',0,dict(basic,combo_token=''),'123',False),
           ('invalid-channel',0,basic,'bad',False),('channel-overflow',0,basic,'4294967296',False)]
    fixtures=[]
    for variant,(name,ret,data,channel,accepted) in enumerate(cases):
        e=Offline72(binary,libraries,records,True);runtime=None
        try:
            e.prepare();runtime=AuthSources72(e,binary,metadata,variant,channel);before=runtime.session_snapshot()
            payload=json.dumps(dict(ret=ret,data=data));pointer=runtime.string(payload)
            trace=fragment(e,ENTRY,{SUCCESS,EARLY,ERROR},dict(rsp=STACK+0x100008,rcx=runtime.page,rdx=pointer))
            expected_stop=SUCCESS if accepted else ERROR if ret else EARLY
            if trace['stop']!=hex(expected_stop):raise ValueError('Auth callback source branch changed')
            after=runtime.session_snapshot();crypto=None;encoded=None
            if accepted:
                expected=dict(account_uid=data['open_id'],account_token=data['combo_token'],
                              channel_id=fixture_uint32(channel)[1],account_type=fixture_uint32(str(data.get('account_type',0)))[1],
                              is_guest=int(data.get('guest',False)))
                if after!=expected:raise ValueError('SDK JSON value did not reach the native session field')
                crypto,_=construct_native_token(e,runtime,binary,metadata,0.5,True)
                e.run(0x1500C4A50,runtime.request,0)
                if e.reg('eax')!=24604:raise ValueError('Auth-sourced request has the wrong native command')
                writer=Writer72(e);encoded=writer.run(82491,runtime.values(82491,runtime.request,writer))['wire']
                decoded={t:(k,v) for t,k,v in parse_wire(bytes.fromhex(encoded))}
                for _,_,tag,kind,semantic,_ in FIELDS:
                    value=expected[semantic];value=value.encode('ascii') if kind=='string' else value
                    wt,zero=(2,b'') if kind=='string' else (0,0)
                    if decoded.get(tag,(wt,zero))!=(wt,value):raise ValueError('Auth source did not reach independent wire tag')
            elif after!=before or runtime.request is not None:raise ValueError('Rejected source branch altered credentials')
            if runtime.logs!=['Genshin Start Log: LoginMainPageContext OnSDKLoginCB'] or e.read(runtime.controller+0x167,'B')!=0:
                raise ValueError('Callback entry/log marker changed')
            fixtures.append(dict(name=name,accepted=accepted,sourcePayload=json.loads(payload),channel=channel,
                                 session=after,trace=trace,jsonReads=runtime.json_reads,sdkCalls=runtime.sdk_calls,
                                 crypto=crypto,requestWire=encoded,passed=True))
        finally:
            if runtime:runtime.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,declarations=declared,
                entry=hex(ENTRY),successStop=hex(SUCCESS),earlyStop=hex(EARLY),errorStop=hex(ERROR),
                runtimeStatus='not-tested',wholeCallbackExecuted=False,nativeSeedHelperExecuted=True,cmdId=24604,fieldBindings=bindings,
                results=[dict(name=f['name'],accepted=f['accepted'],channel=f['channel'],session=f['session'],passed=True) for f in fixtures],
                models=['Python JSON parsing and JSONNode object storage/lookup/value slots',
                        'bounded decimal Int32/UInt32 and canonical Boolean parsing; native scalar getters execute',
                        'explicit SDK device/channel/masked-name values and log capture',
                        'existing Token constructor and seed crypto runtime models'],
                outsideScope=['entire SDK JSON parser/type coercion','UI/optional fields after success stop',
                              'actual SDK login and callback-to-send scheduling','frame builder/network','real client'])


def main():
    a=parse_args();b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True);root=Path(__file__).resolve().parents[1]
    wire=json.loads((root/'protocol/cn-7.2/token-wire-observations.json').read_text('utf8'))
    if wire['inputSHA256']!=HASHES:raise ValueError('Token raw wire has different inputs')
    report=recover(b,m,s,records,a.libraries,wire)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],fields=len(report['fieldBindings']))))


def parse_args():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    return a


if __name__=='__main__':main()
