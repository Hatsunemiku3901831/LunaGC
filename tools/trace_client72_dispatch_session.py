"""Continuous signed dispatch -> SDK update/diagnostic -> session B0 -> Token.

The first dispatch entry only. Declared uninitialized SDK exports/log sink are
not real-client acceptance; tag14/tag965 business semantics remain unknown.
"""
import base64
import json
from pathlib import Path
try:
    from .trace_client72_dispatch_config import DispatchConfig72, STOP as CONFIG_STOP, verify_environment
    from .trace_client72_dispatch_post import check_declarations
    from .trace_client72_dispatch_prefix import DispatchPrefix72, ENTRY
    from .client72_sdk_update import SDKUpdate72, SDK_ENTRY, CELLS
    from .client72_dispatch_notice import DispatchNotice72
    from .client72_emulator import Offline72, GLOBAL
    from .client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from .client72_seed_crypto import rsa_public_numbers
    from .extract_client72 import HASHES, read_verified, extract, number, PE, TypeResolver, HEADER_VA
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .trace_client72_auth_sources import parse_args
    from .trace_client72_token_complete import construct_native_token
    from .trace_client72_login import Writer72
    from .trace_client72_token import varint, parse_wire
except ImportError:
    from trace_client72_dispatch_config import DispatchConfig72, STOP as CONFIG_STOP, verify_environment
    from trace_client72_dispatch_post import check_declarations
    from trace_client72_dispatch_prefix import DispatchPrefix72, ENTRY
    from client72_sdk_update import SDKUpdate72, SDK_ENTRY, CELLS
    from client72_dispatch_notice import DispatchNotice72
    from client72_emulator import Offline72, GLOBAL
    from client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from client72_seed_crypto import rsa_public_numbers
    from extract_client72 import HASHES, read_verified, extract, number, PE, TypeResolver, HEADER_VA
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from trace_client72_auth_sources import parse_args
    from trace_client72_token_complete import construct_native_token
    from trace_client72_login import Writer72
    from trace_client72_token import varint, parse_wire

STOP, MISSING = 0x151212983, 0x151212D8E
TYPE_BINDINGS=((699017,0x1458875C8,4354,'class'),(700517,0x14588F3E8,4356,'class'),
               (700519,0x14588F408,4358,'class'),(698608,0x145886158,452,'class'),
               (706335,0x1458AEE08,77368,'valuetype'),(698601,0x145886110,None,'szarray'))
CASES=(('high-text','9007199254740993','CN',2,'normal'),
       ('empty','','com.miHoYo.GenshinImpact',0,'normal'),
       ('distinct','source-B','CN',15,'normal'),
       ('no-session','unused','CN',2,'no-session'),
       ('no-singleton','unused','CN',2,'no-singleton'),
       ('bad-config','unused','CN',2,'bad-config'))


class DispatchSession72(DispatchConfig72):
    def __init__(self,e,binary,metadata,plain,signer,channel,language,languages,mode):
        super().__init__(e,binary,metadata,plain,signer)
        self.sdk_update=None; self.notice=None; self.stored=False
        self.channel=channel; self.language=language; self.languages=languages
        # This singleton is only tested for presence in the selected non-IFix
        # path. Its initialization and methods are not claimed to execute.
        e.write(GLOBAL+0xF2E0,0 if mode=='no-singleton' else e.alloc())
        if mode=='no-session': e.write(e.read(GLOBAL+0x9E80)+0x180,0)
        e.allow(0x1527567B0)
        for address in (STOP,MISSING): e.allowed.append((address,address+1))

    def observe(self,address):
        if address==CONFIG_STOP:
            # Install SDK/OS/library fixtures at their first consumer. Do not
            # reset CPU state or overwrite the parsed object/session payload.
            self.sdk_update=SDKUpdate72(self,'sdk_app_fixture','7.2.0-fixture',self.channel,self.language,self.languages)
            self.notice=DispatchNotice72(self)
            DispatchPrefix72.observe(self,address)
        else: super().observe(address)
        if self.sdk_update: self.sdk_update.observe(address); self.notice.observe(address)
        if address==0x15121297C: self.stored=True
        if address in (STOP,MISSING): self.stop=address; self.e.uc.emu_stop()


def declarations(binary,metadata,startup,records):
    pe=PE(binary); literals=Literals72(binary,metadata)
    cache=number(binary,pe.offset(USAGE_REGISTRATION)+0x48,'Q')
    resolver=TypeResolver(pe,startup,records,pe.offset(HEADER_VA)); bindings=[]
    for i,cell,index,kind in TYPE_BINDINGS:
        key=(i*0x57C15286+0x26DDEBDA)&0xffffffff
        source=((number(metadata,literals.usages_base+i*8)+0xABBDCABF)&0xffffffff)^key^0x2A357D90
        slot=number(metadata,literals.usages_base+i*8+4)^key^0x1855AA65
        value=resolver.resolve_index(source&0x1fffffff)
        if source>>29!=1 or cache+slot*8!=cell or value['kind']!=kind: raise ValueError('SDK type usage binding changed')
        if index is None:
            if value['kind']!='szarray' or value['elementType'].get('typeDefinition')!=371: raise ValueError('Diagnostic array is not Object[]')
        elif value.get('typeDefinition')!=index: raise ValueError('SDK type identity changed')
        bindings.append(dict(usageRow=i,cell=hex(cell),typeIndex=index,kind=value['kind']))
    by_index={r['index']:r for r in records}
    for index,name,address,count,slot in ((4354,'.ctor',0x1597AE4E0,0,65535),(4354,'Add',0x1597C35D0,2,15),
        (4354,'WriteToStringBuilder',0x1597C3B10,4,21),(452,'.ctor',0x159BABF40,4,65535),
        (452,'ToString',0x159B94F30,0,3)):
        if not any((m['name'],m['registeredAddress'],m['parameterCount'],m['virtualSlot'])==(name,hex(address),count,slot) for m in by_index[index]['methods']):
            raise ValueError('SDK managed-library declaration mismatch')
    return bindings


def recover(binary,metadata,startup,records,libraries,root):
    check_declarations(binary,records); verify_environment(records)
    bindings=declarations(binary,metadata,startup,records)
    enums=json.loads((root/'protocol/cn-7.2/enum-observations.json').read_text('utf8'))
    if enums['inputSHA256']!=HASHES: raise ValueError('Language enum input mismatch')
    languages={v['value']:v['symbol'] for v in next(r for r in enums['enums'] if r['typeIndex']==77368)['values']}
    signer=private_numbers((root/'src/main/resources/keys/SigningKey.der').read_bytes())
    _,body,_=tlv((root/'src/main/resources/keys/game_keys/4_Pub.der').read_bytes())
    _,_,at=tlv(body); _,value,_=tlv(body,at); n,exponent=rsa_public_numbers(value[1:])
    fixtures=[]
    for name,source,channel,language,mode in CASES:
        raw=source.encode('ascii'); config=b'{' if mode=='bad-config' else b'{}'
        plain=b'\x72'+varint(len(raw))+raw+b'\x1a\x00\x62'+varint(len(config))+config+b'\x6a\x02{}\x5a\x00'
        payload=dict(content=base64.b64encode(encrypt_seed([0,n,exponent],plain)).decode(),sign=base64.b64encode(sign_sha256(signer,plain)).decode())
        e=Offline72(binary,libraries,records,True); runtime=None
        try:
            e.prepare(); runtime=DispatchSession72(e,binary,metadata,plain,signer,channel,language,languages,mode)
            flow=runtime.run_config(payload); source_pointer=e.read(runtime.parsed+32)
            stored=e.read(runtime.account+176); sdk=runtime.sdk_update; notice=runtime.notice
            row=dict(name=name,source=source,mode=mode,stop=flow['stop'],storeExecuted=runtime.stored,
                     sessionPointerPreserved=stored==source_pointer,sessionUnchanged=flow['sessionB0Unchanged'],
                     postParsePointerPreserved=runtime.post_pointer,jsonHelperRejected=runtime.json_error,
                     sdkEntries=sdk.entries if sdk else 0,sdkOutputs=sdk.outputs if sdk else [],
                     dictionaryReleases=sdk.releases if sdk else [],noticeCalls=notice.calls if notice else [],
                     nativeCastCalls=notice.cast_calls if notice else 0,tokenFieldWire=None,cmdId=None)
            token=None
            if mode=='normal':
                crypto,_=construct_native_token(e,runtime,binary,metadata,0.5,True,seed=runtime.crypto)
                request_source=e.read(runtime.request+112)
                if raw and request_source!=source_pointer: raise ValueError('Token replaced nonempty dispatch source')
                request_text=bytes(e.uc.mem_read(request_source+20,e.read(request_source+16,'I')*2)).decode('utf-16-le')
                if request_text!=source: raise ValueError('Token dispatch source value changed')
                # Empty sources select the native default string object, which
                # was allocated by the crypto runtime rather than Constructor72.
                runtime.strings[request_source]=request_text
                writer=Writer72(e); wire=writer.run(82491,runtime.values(82491,runtime.request,writer))['wire']
                expected=b'\xaa\x3c'+varint(len(raw))+raw if raw else b''
                if [(t,k,v) for t,k,v in parse_wire(bytes.fromhex(wire)) if t==965] != ([(965,2,raw)] if raw else []): raise ValueError('Independent Token tag965 assertion failed')
                e.run(0x1500C4A50,runtime.request,0)
                if e.reg('eax')!=24604: raise ValueError('Token native getter changed')
                row.update(tokenFieldWire=expected.hex(),cmdId=24604)
                token=dict(crypto=crypto,wire=wire,requestPointerPreserved=request_source==source_pointer)
            fixtures.append(dict(**row,flow=flow,token=token))
            print(json.dumps(dict(fixture=name,stop=flow['stop'],store=runtime.stored)),flush=True)
        finally:
            if runtime: runtime.close()
            e.close()
    report=dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k not in ('flow','token')} for f in fixtures],
                entry=hex(ENTRY),stop=hex(STOP),missingSingletonStop=hex(MISSING),typeBindings=bindings,
                semanticStatus='unknown',runtimeStatus='not-tested',wholeDispatchExecuted=False,
                firstEntryAssociation='native-continuous-to-session',secondCoroutineAssociation='not-tested',
                nativeSeedHelperReused=True,models=['inherited dispatch trust/JSON/config fixtures',
                    'SDK JSONObject/StringBuilder, dictionary pool/storage, Enum.ToString and native string bridge',
                    'explicit app/version/CPU/GPU/channel/language inputs; empty preference backend and uninitialized native SDK export table',
                    'presence-only singleton +F2E0; normal non-IFix path never dereferences it',
                    'Object[]/String/System.Object metadata, OS thread1/main thread1; log sink uninitialized'],
                outsideScope=['actual SDK initialization/export callbacks','actual diagnostic sink/UI','remaining dispatch tail/second coroutine',
                              'tag14/tag965 business semantics','frame builder/socket/actual client'])
    errors=validate_summary(report)
    if errors: raise ValueError(errors)
    return report


def validate_summary(report):
    errors=[]
    expected=dict(inputSHA256=HASHES,fixtureCount=6,entry=hex(ENTRY),stop=hex(STOP),missingSingletonStop=hex(MISSING),
                  semanticStatus='unknown',runtimeStatus='not-tested',wholeDispatchExecuted=False,
                  firstEntryAssociation='native-continuous-to-session',secondCoroutineAssociation='not-tested',nativeSeedHelperReused=True)
    if any(report.get(k)!=v for k,v in expected.items()): errors.append('Dispatch session scope mismatch')
    if report.get('typeBindings')!=[dict(usageRow=i,cell=hex(c),typeIndex=t,kind=k) for i,c,t,k in TYPE_BINDINGS]:
        errors.append('SDK/runtime type binding mismatch')
    rows=report.get('results',[])
    if [r.get('name') for r in rows]!=[c[0] for c in CASES]: errors.append('Dispatch session cases mismatch')
    for row,(_,source,channel,language,mode) in zip(rows,CASES):
        stored=mode=='normal'; sdk=mode in ('normal','no-session')
        stop=None if mode=='bad-config' else hex(MISSING if mode=='no-singleton' else STOP)
        if (row.get('stop')!=stop or row.get('source')!=source or row.get('mode')!=mode or row.get('storeExecuted')!=stored
                or row.get('sessionPointerPreserved')!=stored or row.get('sessionUnchanged')==stored): errors.append('Native B0 source/store mismatch')
        if row.get('postParsePointerPreserved') is not True or row.get('jsonHelperRejected')!=(mode=='bad-config'): errors.append('Dispatch parse boundary mismatch')
        outputs=[dict(entry='0x140ac4160',value=dict(app_id='sdk_app_fixture',app_version='7.2.0-fixture',area='os' if channel!='CN' else 'cn',compile_type='release',use_device_name=True)),
                 dict(entry='0x140ac4180',value=dict(CPU='cpu-fixture',GPU='gpu-fixture',Language={0:'LanguageNone',2:'LanguageSc',15:'LanguageIt'}[language]))] if sdk else []
        if row.get('sdkOutputs')!=outputs or row.get('sdkEntries')!=int(sdk): errors.append('SDK source/output mismatch')
        if row.get('dictionaryReleases')!=([dict(entriesBeforeClear=1,countAfterClear=0)] if sdk else []): errors.append('SDK dictionary clear mismatch')
        notice=[dict(code=5000,argument='SDKServer - OnConnectRegionDispatch: GameBiz==Null')] if sdk else []
        if row.get('noticeCalls')!=notice or row.get('nativeCastCalls')!=int(sdk): errors.append('Native diagnostic branch missing')
        raw=source.encode('ascii'); field=(b'\xaa\x3c'+varint(len(raw))+raw).hex() if raw else ''
        if row.get('tokenFieldWire')!=(field if stored else None) or row.get('cmdId')!=(24604 if stored else None): errors.append('Native Token source/wire mismatch')
    return errors


def main():
    a=parse_args(); b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,Path(__file__).resolve().parents[1])
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],semanticStatus='unknown')))


if __name__=='__main__': main()
