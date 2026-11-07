"""Original CN72 SDK response consumers; all payloads remain in synthetic memory."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'local/migration72/python')]
from client72_sdk_transport import Image,digest,MODULE_HASHES
from client72_sdk_login_chain import Fixture
from unicorn import x86_const as ux

RAW=ROOT/'local/migration72/d02-active/sdk-response-astra'
INPUT_MANIFEST_SHA='23410d0432a53786b151c5978a9fd1fd318432a36c4cc57fb163f51b2f2f5821'
OBSERVED_ENTRIES={0x342b10:'int32',0x2f6190:'bool8',0x338990:'bool8',0x31f3f0:'bool8',
                  0x337b20:'void',0x33ca40:'void',0x342460:'void',0x3c91a0:'pointer64'}


def synthetic(seed=0,size=31):
    return ''.join(chr(65+(i*7+seed)%26) for i in range(size))


def signed32(value):
    return (value+0x80000000)%0x100000000-0x80000000


class ResponseFixture(Fixture):
    def __init__(self,pe):
        self.observers={}
        self.pending=[]
        self.native_calls=[]
        super().__init__(pe)

    def call(self,rva,*args):
        self.pending=[];self.native_calls=[]
        result=super().call(rva,*args)
        self.finish_return(self.STOP,self.STACK+8)
        return result

    def finish_return(self,address,stack):
        if self.pending and self.pending[-1]['return']==address and self.pending[-1]['stack']+8==stack:
            row=self.pending.pop();kind=OBSERVED_ENTRIES[row['entryRva']]
            value=self.uc.reg_read(ux.UC_X86_REG_RAX)
            record={'entryRva':hex(row['entryRva']),'returned':True,'returnKind':kind}
            if kind=='int32':record['status']=signed32(value&0xffffffff)
            elif kind=='bool8':record['value']=value&255
            elif kind=='pointer64':record['nonzero64Pointer']=value>0xffffffff
            self.native_calls.append(record)

    def on_code(self,uc,address,size,user):
        stack=uc.reg_read(ux.UC_X86_REG_RSP)
        self.finish_return(address,stack)
        if address-self.pe.base in OBSERVED_ENTRIES:
            self.pending.append({'entryRva':address-self.pe.base,'return':self.get(stack),'stack':stack})
        callback=self.observers.get(address-self.pe.base)
        if callback:callback()
        super().on_code(uc,address,size,user)

    def allocate(self,size):
        # The original JSON printer allocates zero bytes for an empty child list.
        return super().allocate(1 if size==0 else size)

    def cstring(self,address):
        value=bytearray()
        while len(value)<8192:
            byte=bytes(self.uc.mem_read(address+len(value),1))
            if byte==b'\0':return bytes(value)
            value+=byte
        raise ValueError('Synthetic string exceeds fixture input contract')

    def json_value(self,node):
        if not node:return None
        kind=self.get(node+0x18,4)
        if kind==0:return False
        if kind==1:return True
        if kind==2:return None
        if kind==3:return int.from_bytes(self.uc.mem_read(node+0x28,8),'little',signed=True)
        if kind==4:
            import struct
            return struct.unpack('<d',self.uc.mem_read(node+0x30,8))[0]
        if kind==5:return self.cstring(self.get(node+0x20)).decode('utf-8')
        if kind in [6,7]:
            child=self.get(node+0x10);result=[] if kind==6 else {}
            while child:
                value=self.json_value(child)
                if kind==6:result.append(value)
                else:result[self.cstring(self.get(child+0x40)).decode('utf-8')]=value
                child=self.get(child)
            return result
        raise ValueError('Unknown synthetic JSON kind: '+str(kind))

    def json_object(self,address):
        return self.json_value(self.get(address+8) or self.get(address+0x10))

    def packet(self,payload,address=None):
        address=address or self.OBJECT+0x8000
        encoded=(payload if isinstance(payload,bytes) else json.dumps(payload,separators=(',',':')).encode())
        self.uc.mem_write(address,encoded+b'\0')
        return address

    def set_string(self,address,value):
        raw=value.encode() if isinstance(value,str) else value
        temporary=self.OBJECT+0x90000
        self.uc.mem_write(temporary,raw+b'\0')
        self.call(0x138160,address,temporary,len(raw))

    def parse_object(self,address,payload):
        self.call(0x14d040,address)
        temporary=self.OBJECT+0x91000
        self.put(temporary+0x18,15)
        self.set_string(temporary,json.dumps(payload,separators=(',',':')))
        result=self.call(0x14e110,address,temporary)
        self.call(0x1380e0,temporary)
        if not result&255:raise ValueError('Synthetic JSON setup rejected')

    def initialized_service(self):
        service=self.OBJECT;aggregate=service+0x1000;request=service+0x2000;engine=service+0x3000
        self.call(0x341090,service)
        self.put(service,self.pe.base+0x6c2ca8)  # Exact factory store, 3176F6/3176FD.
        self.put(self.pe.base+0x7acc80,aggregate)
        self.put(aggregate+8,request);self.put(request,self.pe.base+0x6d2218)
        self.put(self.pe.base+0x7acd88,engine);self.put(engine,self.pe.base+0x6bf300)
        return service

    def evidence(self,entries):
        return {'nativeInstructions':len(self.trace),'visitedEntries':{hex(x):self.trace.count(x) for x in entries},
                'balancedReturn':True,'nativeCalls':list(self.native_calls),
                'modelCalls':dict(sorted(Counter(e['model'] for e in self.events).items()))}


def login_cases(pe):
    token=synthetic();ident=str((1<<53)+17)
    base_user={'aid':ident,'mid':ident,'email':synthetic(1)}
    base_data={'user_info':base_user,'token':{'token_type':1,'token':token}}
    cases=[('complete',{'retcode':0,'data':base_data}),('null-body',None),('empty-body',b''),
      ('malformed',b'{'),('missing-retcode',{'data':base_data}),('retcode-string',{'retcode':'0','data':base_data}),
      ('retcode-fraction',{'retcode':0.75,'data':base_data}),('retcode-nonzero',{'retcode':-101,'data':base_data}),
      ('missing-data',{'retcode':0}),('null-data',{'retcode':0,'data':None}),
      ('missing-user',{'retcode':0,'data':{'token':base_data['token']}}),
      ('null-user',{'retcode':0,'data':{'user_info':None}}),('string-user',{'retcode':0,'data':{'user_info':synthetic()}}),
      ('empty-user',{'retcode':0,'data':{'user_info':{}}}),
      ('missing-token',{'retcode':0,'data':{'user_info':base_user}}),
      ('token-wrong-type',{'retcode':0,'data':{'user_info':base_user,'token':{'token_type':'1','token':42}}}),
      ('token-default',{'retcode':0,'data':{'user_info':base_user,'token':{'token':token}}}),
      ('token-zero',{'retcode':0,'data':{'user_info':base_user,'token':{'token_type':0,'token':token}}}),
      ('verify-tokens-list',{'retcode':0,'data':{'user_info':base_user,'tokens':[{'token_type':1,'token':synthetic(5)}]}})]
    result=[]
    for label,payload in cases:
        env=ResponseFixture(pe);service=env.initialized_service();error=env.OBJECT+0x4000;data=error+0x1000;user=data+0x1000
        for address in [error,data]:env.call(0x14d040,address)
        env.call(0x156530,user)
        if label=='verify-tokens-list':env.set_string(user+0x170,token);env.put(user+0x148,1,4)
        body=0 if payload is None else env.packet(payload)
        status=signed32(env.call(0x342b10,service,body,error,user,data)&0xffffffff)
        proof=env.evidence([0x342b10,0x39eb30,0x2f6190,0x338990,0x31f3f0])
        result.append({'case':label,'status':status,'aidExact':env.string(user).decode()==ident,
          'tokenType':env.get(user+0x148,4),'stokenLength':len(env.string(user+0x170)),
          'stokenEqualsInput':env.string(user+0x170).decode()==token,
          'gameTokenLength':len(env.string(user+0x150)),**proof})
        # Caller owns all three outputs. Service/helper singleton lifetime remains explicit.
        env.call(0x1552f0,user)
        for address in [error,data]:env.call(0x14d450,address)
    return result


def user_cases(pe):
    ident=str((1<<53)+19);value=synthetic(2)
    cases=[('full-nested-list',{'uid':synthetic(3),'aid':ident,'mid':ident,'name':synthetic(4),
        'is_email_verify':1,'is_guest':True,'channel_id':37,'thirdLoginTimestamp':(1<<40)+27,
        'loginTime':(1<<40)+29,'agree_persistent_login_data':True,
        'links':[{'nickname':value,'thirdparty':synthetic(4,2),'union_id':ident},
                 {'nickname':synthetic(5,17),'thirdparty':synthetic(6,2),'union_id':ident}]}),
        ('empty',{}),('numeric-id',{'aid':(1<<53)+19}),('wrong-bools',{'is_guest':1,'agree_persistent_login_data':1}),
        ('email-zero',{'is_email_verify':0}),('email-string-zero',{'is_email_verify':'0'}),
        ('list-wrong-type',{'links':{}}),('list-empty',{'links':[]}),
        ('number-precision-boundary',{'thirdLoginTimestamp':(1<<53)+27,'loginTime':(1<<53)+29}),
        ('false-bools',{'is_guest':False,'agree_persistent_login_data':False})]
    result=[]
    for label,payload in cases:
        env=ResponseFixture(pe);service=env.initialized_service();user=env.OBJECT+0x6000
        env.call(0x156530,user)
        ret=env.call(0x31f3f0,env.get(service+0x368),env.packet(payload),user)&255
        proof=env.evidence([0x31f3f0,0x324480])
        start,end=env.get(user+0x2d8),env.get(user+0x2e0)
        rows=[{'nicknameBytes':len(env.string(p)),'thirdpartyBytes':len(env.string(p+0x20)),
               'unionIdExact64Text':env.string(p+0x40).decode()==ident} for p in range(start,end,0x60)]
        result.append({'case':label,'returnLow8':ret,'aidExact64Text':env.string(user).decode()==ident,
          'emailVerifiedByte':env.get(user+0x80,1),'guestByte':env.get(user+0x190,1),
          'persistentByte':env.get(user+0x2f8,1),'channelId':env.get(user+0x200,4),
          'timestampExact64':env.get(user+0x288)==(1<<40)+27,'loginTimeExact64':env.get(user+0x2f0)==(1<<40)+29,
          'above53TimestampRounded':label=='number-precision-boundary' and env.get(user+0x288)==(1<<53)+28,
          'above53LoginTimeRounded':label=='number-precision-boundary' and env.get(user+0x2f0)==(1<<53)+28,
          'linksCount':len(rows),'links':rows,**proof})
        env.call(0x1552f0,user)
        result[-1]['vectorStorageFreed']=not start or start not in env.allocations
    return result


def exchange_cases(pe):
    token=synthetic(7);old=synthetic(8)
    cases=[('valid',{'retcode':0,'data':{'token':{'token_type':4,'token':token}}}),
        ('different-type',{'retcode':0,'data':{'token':{'token_type':9,'token':token}}}),
        ('type-omitted',{'retcode':0,'data':{'token':{'token':token}}}),
        ('empty-token',{'retcode':0,'data':{'token':{'token_type':4,'token':''}}}),
        ('missing-token',{'retcode':0,'data':{}}),('wrong-token',{'retcode':0,'data':{'token':{'token':42}}}),
        ('error',{'retcode':-101}),('invalidates-cache',{'retcode':-100}),('malformed',b'{'),('null',None)]
    result=[]
    for label,payload in cases:
        env=ResponseFixture(pe);service=env.initialized_service();ctx=env.OBJECT+0x10000
        env.put(ctx+0x40,service);env.call(0x156530,ctx+0x48);env.set_string(ctx+0x1b8,old)
        helper=env.get(service+0x368);observations=[]
        def observe():
            observations.append(signed32(env.uc.reg_read(ux.UC_X86_REG_R9)&0xffffffff))
        env.observers[0x33e210]=observe
        if label=='invalidates-cache':
            env.call(0x33ca40,ctx,env.packet({'retcode':0,'data':{'token':{'token':token}}}))
            observations.clear()
        before=env.get(helper+0x3f8)
        env.call(0x33ca40,ctx,0 if payload is None else env.packet(payload))
        proof=env.evidence([0x33ca40,0x39eb30,0x2f6190,0x18f9f0,0x33e210])
        head=env.get(helper+0x3f0);node=env.get(head);rows=[]
        while node!=head:
            rows.append({'keyMatchesStoken':env.string(node+0x10).decode()==old,
                         'valueMatchesResponse':env.string(node+0x30).decode()==token})
            node=env.get(node)
        result.append({'case':label,'reportedStatuses':observations,'cacheBefore':before,
                       'cacheAfter':env.get(helper+0x3f8),'cacheRows':rows,**proof})
    return result


def formatter_cases(pe):
    result=[];token=synthetic(9);ident=str((1<<53)+21)
    for label,tt,with_top in [('login-type1',1,True),('verify-list-only',1,False)]:
        env=ResponseFixture(pe);service=env.initialized_service();user=env.OBJECT+0x6000
        error=env.OBJECT+0x4000;data=env.OBJECT+0x5000;out=env.OBJECT+0x7000;config=env.OBJECT+0x10000
        for p in [error,data,out]:env.call(0x14d040,p)
        env.call(0x156530,user);env.put(user+0x148,tt,4);env.set_string(user+0x170,token)
        payload={'retcode':0,'data':{'user_info':{'aid':ident,'mid':ident}}}
        payload['data']['token' if with_top else 'tokens']={'token_type':1,'token':token} if with_top else [{'token_type':1,'token':token}]
        status=signed32(env.call(0x342b10,service,env.packet(payload),error,user,data)&0xffffffff)
        env.put(config,pe.base+0x6c6cd8);env.put(config+0xc0,15);env.set_string(config+0xa8,synthetic(10))
        env.put(env.OBJECT+0x1000+0x40,config)
        env.call(0x342460,service,user,out)
        value=env.json_object(out)
        result.append({'case':label,'status':status,'typeAfterParse':env.get(user+0x148,4),
          'openIdExact64Text':value.get('open_id')==ident,'comboIdExact':value.get('combo_id')==ident,
          'comboTokenEqualsInput':value.get('combo_token')==token,'comboTokenLength':len(value.get('combo_token','')),
          'guest':value.get('guest'),'loginType':value.get('login_type'),'fieldNames':sorted(value),
          **env.evidence([0x342460,0x36aa70])})
    return result


def combo_cases(pe):
    token=synthetic(11);ident=str((1<<53)+23);result=[]
    cases=[('complete',{'combo_id':ident,'open_id':ident,'combo_token':token,'account_type':1,'data':json.dumps({'guest':False})}),
           ('minimal',{}),('guest-true',{'data':json.dumps({'guest':True})}),
           ('wrong-types',{'combo_id':42,'open_id':False,'combo_token':{},'account_type':'1','data':{}})]
    for label,payload in cases:
        env=ResponseFixture(pe);env.initialized_service();data=env.OBJECT+0x4000;out=env.OBJECT+0x5000;user=env.OBJECT+0x6000
        env.parse_object(data,payload);env.call(0x156530,user);env.put(user+0x148,1,4);env.set_string(user+0x170,token)
        env.call(0x3c91a0,env.OBJECT+0x10000,out,0,data,user)
        value=env.json_object(out);inner=value.get('data',{})
        result.append({'case':label,'ret':value.get('ret'),'fieldNames':sorted(value),
           'dataFieldNames':sorted(inner),'comboTokenExact':inner.get('combo_token')==token,
           'openIdExact64Text':inner.get('open_id')==ident,'accountType':inner.get('account_type'),
           'loginType':inner.get('login_type'),'guest':inner.get('guest'),
           **env.evidence([0x3c91a0])})
    return result


def refresh_cases(pe):
    old=synthetic(13);new=synthetic(14);result=[]
    for label,payload in [('type1',{'new_token':{'token_type':1,'token':new}}),
                          ('type0',{'new_token':{'token_type':0,'token':new}}),
                          ('missing',{}),('empty',{'new_token':{'token_type':1,'token':''}})]:
        env=ResponseFixture(pe);service=env.initialized_service();user=env.OBJECT+0x6000;data=env.OBJECT+0x4000
        env.call(0x156530,user);env.set_string(user+0x170,old);env.put(user+0x148,1,4);env.parse_object(data,payload)
        env.call(0x337b20,service,data,user)
        result.append({'case':label,'tokenType':env.get(user+0x148,4),
          'stokenReplaced':env.string(user+0x170).decode()==new,'oldStokenRetained':env.string(user+0x170).decode()==old,
          'gameTokenReplaced':env.string(user+0x150).decode()==new,**env.evidence([0x337b20,0x33e210])})
    return result


class ObservedBoundary(Exception):
    pass


def callback_forwarding(pe):
    env=ResponseFixture(pe);base=env.OBJECT;jobber=base+0x1000;callback=base+0x2000;owner=base+0x3000;bodyptr=base+0x4000
    env.put(base,pe.base+0x6c5430);env.put(base+0x48,jobber)
    env.put(jobber,pe.base+0x6c5390);env.put(jobber+0x68,callback)
    env.put(callback,pe.base+0x6c2c00);env.put(callback+8,owner)
    body=env.packet({'retcode':0});env.put(bodyptr,body)
    seen=[]
    def observe():
        seen.append({'ownerSlotExact64':env.uc.reg_read(ux.UC_X86_REG_RCX)==callback+8,
                     'ownerValueExact64':env.get(callback+8)==owner,
                     'responsePointerExact64':env.uc.reg_read(ux.UC_X86_REG_RDX)==body})
        raise ObservedBoundary('entry-observed-no-return-or-success-model')
    env.observers[0x32f640]=observe
    try:env.call(0x34b5d0,base,bodyptr)
    except ObservedBoundary:pass
    if len(seen)!=1:raise ValueError('Original response forwarding did not reach service')
    return {'syntheticLayoutFromExactWriters':True,'stoppedBeforeService':True,'hits':seen,
            'nativeInstructions':len(env.trace),'visitedEntries':{hex(x):env.trace.count(x) for x in [0x34b5d0,0x359f50,0x340e90]},
            'externalCallbackReturnModeled':False}


def route_and_exchange_producer(pe):
    env=ResponseFixture(pe);env.initialized_service()
    def exit_registration(args):
        env.events.append({'model':'CRT-process-exit-registration','callbackRva':hex(args[0]-pe.base)})
        return 0
    env.cut[0x48274c]=exit_registration
    env.call(0x107fe0)
    config=env.OBJECT+0x10000;url_object=config+0x2000;name=config+0x3000;out=config+0x4000
    env.put(config,pe.base+0x6c6cd8);env.put(config+0x870,url_object)
    sentinel=env.allocate(0x60)
    for offset in [0,8,0x10]:env.put(sentinel+offset,sentinel)
    env.put(sentinel+0x18,0x101,2);env.put(url_object,sentinel)
    env.call(0x37e270,url_object,0)
    routes={
      'loginUrl':'/account/ma-cn-passport/app/loginByPassword',
      'tokenVerify':'/account/ma-cn-session/app/verify',
      'exchangeToken':'/account/ma-cn-session/app/exchange'}
    observations=[]
    for key,path in routes.items():
        env.uc.mem_write(name,key.encode()+b'\0');env.call(0x3773c0,config,out,name,0)
        equal=env.string(out)==('https://passport-api.mihoyo.com'+path).encode()
        if not equal:raise ValueError('Fixed code-zero route differs')
        observations.append({'key':key,'host':'passport-api.mihoyo.com','path':path,'nativeReaderEqual':True})
        env.call(0x1380e0,out)
    aggregate=env.OBJECT+0x1000;env.put(aggregate+0x40,config);env.put(aggregate+0x10,env.OBJECT+0x20000)
    mid=str((1<<53)+31);token=synthetic(15)
    midptr=env.packet(mid.encode(),env.OBJECT+0x22000);tokenptr=env.packet(token.encode(),env.OBJECT+0x23000)
    callback=env.OBJECT+0x24000;hits=[]
    def observe():
        url=env.uc.reg_read(ux.UC_X86_REG_RDX);body=env.uc.reg_read(ux.UC_X86_REG_R8)
        value=json.loads(env.cstring(body))
        hits.append({'urlWhitelisted':env.cstring(url)==b'https://passport-api.mihoyo.com/account/ma-cn-session/app/exchange',
          'fieldNames':sorted(value),'midExact64Text':value.get('mid')==mid,
          'destinationTokenType':value.get('dst_token_type'),
          'sourceFieldNames':sorted(value.get('src_token',{})),
          'sourceTokenType':value.get('src_token',{}).get('token_type'),
          'sourceTokenExact':value.get('src_token',{}).get('token')==token})
        raise ObservedBoundary('send-boundary-no-network-or-return-model')
    env.observers[0x3ac990]=observe
    try:env.call(0x3adc20,env.OBJECT+0x25000,midptr,tokenptr,callback,callback)
    except ObservedBoundary:pass
    exchange_steps=len(env.trace);route_visited=0x3773c0 in env.trace
    user=env.OBJECT+0x26000;env.call(0x156530,user)
    env.set_string(user,mid);env.set_string(user+0x20,mid);env.set_string(user+0x170,token);env.put(user+0x148,1,4)
    env.put(user+0x1d8,0,1)  # Synthetic cached user is not currently logged in; skips UI-only conditional.
    env.put(aggregate+0x48,env.OBJECT+0x28000);env.put(aggregate+0x58,env.OBJECT+0x29000)
    verify=[]
    def observe_verify():
        url=env.uc.reg_read(ux.UC_X86_REG_RDX);body=env.uc.reg_read(ux.UC_X86_REG_R8)
        value=json.loads(env.cstring(body))
        verify.append({'urlWhitelisted':env.cstring(url)==b'https://passport-api.mihoyo.com/account/ma-cn-session/app/verify',
          'fieldNames':sorted(value),'midExact64Text':value.get('mid')==mid,'refresh':value.get('refresh'),
          'tokenFieldNames':sorted(value.get('token',{})),'tokenType':value.get('token',{}).get('token_type'),
          'tokenExact':value.get('token',{}).get('token')==token})
        raise ObservedBoundary('request-manager-boundary-no-network-or-return-model')
    env.observers[0x3a5990]=observe_verify
    try:env.call(0x331730,env.OBJECT,user,callback)
    except ObservedBoundary:pass
    return {'routes':observations,'sendBoundary':hits,'nativeInstructions':exchange_steps,
            'nativeRouteEntryVisited':route_visited,'verifyBoundary':verify,'verifyInstructions':len(env.trace),
            'transportExecuted':False}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to((ROOT/'local').resolve()):
        raise ValueError('Output must be new inside checkout local/')
    manifest_path=RAW/'input-manifest.json'
    if digest(manifest_path.read_bytes())!=INPUT_MANIFEST_SHA:raise ValueError('Fixed input manifest changed')
    fixed=json.loads(manifest_path.read_bytes())
    for path,row in fixed.items():
        if digest((ROOT/path).read_bytes())!=row['sha256']:raise ValueError('Fixed input changed: '+path)
    modules={};profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    for row in profile['files'].values():
        sha=digest((args.client/row['path']).read_bytes())
        if sha!=row['sha256']:raise ValueError('Profile input changed')
        modules[row['path']]=sha
    parent=json.loads((ROOT/'local/migration72/d02-active/sdk-transport-astra/final-first.json').read_bytes())
    for row in parent['modules']:
        sha=digest((args.client/row['path']).read_bytes())
        if sha!=MODULE_HASHES[Path(row['path']).name]:raise ValueError('Natural module disk input changed')
        modules[row['path']]=sha
    pe=Image((args.client/'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes())
    windows={}
    for entry in [0x34b5d0,0x359f50,0x340e90,0x342b10,0x338990,0x31f3f0,0x33ca40,0x3c91a0,
        0x342460,0x337b20,0x331730,0x3adc20,0x3a5e00,0x3a5990,0x3cbe30,0x3cb120,0x3be5c0,
        0x3ca000,0x3cff40,0x156530,0x1552f0,0x2f6190,0x376ea0,0x364910,0x380e60,0x140940,
        0x345910,0x33bdb0,0x342cb0,0x342680,0x32f460,0x32f640,0x341090,0x332f60]:
        bounds=pe.enclosing(entry);start,end=bounds or (entry,entry+64)
        instructions=pe.instructions(start,end)
        if not bounds:end=instructions[-1][0]-pe.base+instructions[-1][1]
        data=pe.data[pe.offset(pe.base+start):pe.offset(pe.base+end-1)+1]
        windows[hex(entry)]={'startRva':hex(start),'endRva':hex(end),'pdata':list(bounds) if bounds else None,
          'coverage':'complete-pdata-window' if bounds else 'bounded-entry-window-no-pdata',
          'machineCodeHex':data.hex(),'sha256':digest(data),
          'instructions':[[hex(a-pe.base),m,o] for a,z,m,o in instructions]}
    slots={}
    for table,offsets in [(0x6c5430,[0x10]),(0x6c5390,[0x20,0x28]),(0x6c2c00,[0x10]),
        (0x6c2ca8,[0x60,0x140,0x198,0x1a0,0x1e8]),(0x6d2218,[8,0xc0,0xe0]),
        (0x6bf300,[0x90]),(0x6c2fb8,[0x10]),(0x6c27a0,[0x10]),(0x6c6cd8,[0x80,0x90,0xc0])]:
        slots[hex(table)]={hex(off):hex(int.from_bytes(pe.data[pe.offset(pe.base+table+off):pe.offset(pe.base+table+off)+8],'little')-pe.base) for off in offsets}
    rea=json.loads((RAW/'rea-final.json').read_bytes())
    result={'scope':'CN72 original SDK instructions, synthetic memory only; no natural response or network',
      'sourceSHA256':digest(Path(__file__).read_bytes()),
      'testSourceSHA256':digest((ROOT/'tools/test_client72_sdk_response_contract.py').read_bytes()),
      'inputManifestSHA256':INPUT_MANIFEST_SHA,'fixedInputs':fixed,'moduleSHA256':modules,
      'callbackForwarding':callback_forwarding(pe),'login':login_cases(pe),'users':user_cases(pe),
      'exchange':exchange_cases(pe),'formatter':formatter_cases(pe),'combo':combo_cases(pe),
      'refresh':refresh_cases(pe),'requestProducers':route_and_exchange_producer(pe),
      'codeWindows':windows,'vtableSlots':slots,
      'reaEvidence':[{'id':r['evidence_id'],'parameters':r.get('parameters'),
                     'limitations':r.get('limitations',[])} for r in rea['records']
                     if r.get('operation')=='procedure_pseudo_code'],
      'models':['Synthetic sparse objects use original vtables and exactly documented field layouts.',
        'Native BaseLoginService/UserInfo/CJsonObject constructors, JSON parser, guards, copies, trees, destructors execute.',
        'Storage allocation/free, single-thread synchronization and CRT exit registration are explicit models.',
        'malloc(0) is modeled as one byte of storage for native empty-JSON printing; no parse success is modeled.',
        'Optional UI/telemetry components are absent; verify has nonnull prerequisite objects and is_login=false.',
        'Formatter uses preexisting synthetic cached device string; OS device generation/registry are not executed.',
        'Forwarding and request producer boundaries stop without returning a synthetic business success.',
        'No callback parser or response guard is replaced. Constructors retain helper/service singleton state.'],
      'remainingUnknowns':['Natural login, verify, exchange, combo and chosen runtime route are unobserved.',
        'The full post-login UI/compliance/stage and registry persistence lifecycle is static-only here.',
        'Combo route is Config+860 map key comboLogin, loaded from Config/server_pc.json via disk or callback140940; selected asset bytes/callback producer are not yet bound.',
        'Natural managed Invoke action and successful final SDK callback to game are not proven.',
        'HTTP status/TLS/trust/signature and complete server authentication are not executed by this offline card.']}
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'sha256':digest(args.output.read_bytes()),'bytes':args.output.stat().st_size,
        'cases':sum(len(result[k]) for k in ['login','users','exchange','formatter','combo','refresh'])}))


if __name__=='__main__':main()


