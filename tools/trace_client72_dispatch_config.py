"""Native dispatch config consumers and events, up to the next SDK boundary.

JSON containers, cached Encoding and empty event registrations are fixtures.
The two config bodies, scalar getters, list stores and event routing execute.
"""
import base64
import hashlib
import json
from pathlib import Path
try:
    from .trace_client72_dispatch_post import DispatchPost72, CONFIG, check_declarations
    from .trace_client72_dispatch_prefix import DispatchPrefix72, ENTRY
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, GLOBAL
    from .client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from .client72_seed_crypto import rsa_public_numbers
    from .trace_client72_auth_sources import parse_args
    from .trace_client72_token import varint
except ImportError:
    from trace_client72_dispatch_post import DispatchPost72, CONFIG, check_declarations
    from trace_client72_dispatch_prefix import DispatchPrefix72, ENTRY
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, GLOBAL
    from client72_rsa_fixture import private_numbers, sign_sha256, encrypt_seed, tlv
    from client72_seed_crypto import rsa_public_numbers
    from trace_client72_auth_sources import parse_args
    from trace_client72_token import varint

STOP = 0x1512124B9
RETURNS = (0x151211DD5, 0x151211E2C)
SCALARS = {'downloadThreadNum': (0x14586D08C, 'i'), 'showexception': (0x14586D069, 'B')}
LISTS = {'safeOptionalResourceCategories': 0x6248, 'secureOptionalResourcePolicyPlugins': 0x6160}
CASES = (
    ('empty', '{}', '{}'),
    ('first-settings', '{"downloadThreadNum":7,"showexception":true,"safeOptionalResourceCategories":[1,300]}', '{}'),
    ('second-settings', '{}', '{"downloadThreadNum":2147483647,"secureOptionalResourcePolicyPlugins":[-1,2147483647]}'),
    ('signed-boundary', '{"downloadThreadNum":-2147483648}', '{"downloadThreadNum":2147483648}'),
    ('bad-first-json', '{', '{}'),
    ('bad-second-json', '{}', '{'),
)


def plain_wire(first, second):
    # Fixed keys, independent of the observation tables. Region13/string and
    # Region14/uint32 feed the post-config global stores below.
    region = bytes.fromhex('6a05696e6e657270ffffffff0f')
    parts = [(b'\x72', b'9007199254740993'), (b'\x1a', region),
             (b'\x62', first.encode('ascii')), (b'\x6a', second.encode('ascii')), (b'\x5a', b'')]
    return b''.join(key+varint(len(value))+value for key,value in parts)


class DispatchConfig72(DispatchPost72):
    def __init__(self, e, binary, metadata, plain, signer):
        super().__init__(e, binary, metadata, plain, signer)
        for address in (CONFIG, 0x146D3F6E0, 0x147517860, 0x1532D6720, 0x1532ECA40, 0x14F8EF7B0): e.allow(address)
        e.allow_unwind(0x1565EE8D0)
        e.allowed.append((STOP, STOP+1))
        for cell in (0x1458A4718, 0x1458C3930, 0x145890E98, 0x1458AAA70, 0x1458ADFD8): e.synthetic_type(cell)
        e.write(GLOBAL+0x24B8, self.encoding)
        self.config_lists = {}
        for name,offset in LISTS.items():
            p=e.alloc(); array=e.alloc(32+16*4); e.write(array+24,16,'I'); e.write(p+16,array)
            # Nonempty initial state proves the missing-key clear branch.
            e.write(p+24,1,'I'); e.write(array+32,123,'i'); e.write(GLOBAL+offset,p)
            self.config_lists[name]=p
        for address,fmt in SCALARS.values(): e.write(address,0,fmt)
        self.events_manager=e.alloc()
        e.write(self.events_manager+16,e.alloc()); e.write(self.events_manager+24,e.alloc())
        e.write(GLOBAL+0x9E50,self.events_manager)
        self.box_class=e.synthetic_type(0x1458C3958)
        e.models[0x140525C40]=self.box_mode
        e.write(self.normal+0x1F0,e.model_stub(self.as_array))
        self.config_calls=[]; self.config_returns=[]; self.events=[]; self.json_error=False

    def box_mode(self):
        e=self.e
        if e.reg('rcx')!=self.box_class: raise ValueError('Unexpected boxed type')
        p=e.alloc(); e.write(p,self.box_class); e.write(p+16,e.read(e.reg('rdx'),'I'),'I')
        return p

    def as_array(self):
        e=self.e; p=e.reg('rcx'); value,path=self.nodes[p]
        if not isinstance(value,list): raise ValueError('Config fixture expects JSON array')
        store=e.alloc(); array=e.alloc(32+len(value)*8)
        e.write(array+24,len(value),'I'); e.write(store+16,array); e.write(store+24,len(value),'I')
        for i,item in enumerate(value): e.write(array+32+i*8,self.node(item,f'{path}[{i}]'))
        e.write(p+24,store)
        return p

    def parse_json(self):
        try: return super().parse_json()
        except json.JSONDecodeError:
            self.json_error=True
            raise

    def snapshot(self):
        e=self.e
        values={name:e.read(address,fmt) for name,(address,fmt) in SCALARS.items()}
        for name,p in self.config_lists.items():
            values[name]=[e.read(e.read(p+16)+32+i*4,'i') for i in range(e.read(p+24,'I'))]
        return values

    def observe(self, address):
        e=self.e
        if address==CONFIG:
            arrays=[bytes(e.uc.mem_read(e.reg(k)+32,e.read(e.reg(k)+24))).hex() for k in ('rdx','r8')]
            self.config_calls.append(dict(mode=e.reg('r9d'),arrays=arrays,receiverPreserved=e.reg('rcx')==self.sender))
            DispatchPrefix72.observe(self,address)
        else: super().observe(address)
        if address==0x1532D6720:
            event=e.reg('rdx'); boxed=e.read(event+8)
            if e.reg('rcx')!=self.events_manager or e.read(boxed)!=self.box_class: raise ValueError('Event fixture receiver/type mismatch')
            self.events.append(dict(id=e.read(event,'I'),mode=e.read(boxed+16,'I')))
        if address in RETURNS:
            self.config_returns.append(dict(address=hex(address),nativeReturnRAX=hex(e.reg('rax')),
                                            returnAL=e.reg('al'),values=self.snapshot()))
        if address==STOP: self.stop=address; e.uc.emu_stop()

    def run_config(self, payload):
        try: result=self.run_post(payload)
        except ValueError:
            if not self.json_error: raise
            result=dict(stop=None,sessionB0Unchanged=self.text(self.e.read(self.account+176))==self.session_values[176])
        return dict(result,configCalls=self.config_calls,configReturns=self.config_returns,events=self.events,
                    jsonHelperRejected=self.json_error,regionGlobals=None if self.stop!=STOP else
                    dict(text=self.text(self.e.read(GLOBAL+0x6240)),textCopy=self.text(self.e.read(GLOBAL+0x6078)),
                         uint32=self.e.read(0x14586CFB4,'I')))


def verify_environment(records):
    by_index={r['index']:r for r in records}
    expected=[(443,'utf8Encoding','0x40024b8'),(11325,'SafeOptionalResourceCategories','0x4006248'),
              (11325,'SecureOptionalResourcePolicyPlugins','0x4006160')]
    for owner,name,raw in expected:
        if not any(f['name']==name and f['rawOffset']==raw for f in by_index[owner]['fields']): raise ValueError('Config environment declaration changed')
    if not any(m['name']=='get_AsArray' and m['virtualSlot']==36 for m in by_index[8987]['methods']): raise ValueError('JSON array slot changed')


def recover(binary,metadata,records,libraries,root):
    check_declarations(binary,records); verify_environment(records)
    signer=private_numbers((root/'src/main/resources/keys/SigningKey.der').read_bytes())
    _,body,_=tlv((root/'src/main/resources/keys/game_keys/4_Pub.der').read_bytes())
    _,_,at=tlv(body); _,value,_=tlv(body,at); n,exponent=rsa_public_numbers(value[1:])
    fixtures=[]
    for name,first,second in CASES:
        plain=plain_wire(first,second)
        payload=dict(content=base64.b64encode(encrypt_seed([0,n,exponent],plain)).decode(),
                     sign=base64.b64encode(sign_sha256(signer,plain)).decode())
        e=Offline72(binary,libraries,records,True); runtime=None
        try:
            e.prepare(); runtime=DispatchConfig72(e,binary,metadata,plain,signer)
            result=runtime.run_config(payload)
            fixtures.append(dict(name=name,plainWire=plain.hex(),**result))
            print(json.dumps(dict(fixture=name,stop=result['stop'])),flush=True)
        finally:
            if runtime: runtime.close()
            e.close()
    report=dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k!='trace'} for f in fixtures],entry=hex(ENTRY),stop=hex(STOP),
                semanticStatus='unknown',runtimeStatus='not-tested',wholeDispatchExecuted=False,
                models=['inherited local signing trust/JSON/managed allocations', 'cached ASCII Encoding.GetString',
                        'bounded JSON array/List<int> storage and boxed mode; native scalar getters/list writes execute',
                        'event manager with empty listener dictionary/list; native routing executes',
                        'initialized reviewed class flags; optional GLOBAL+E0F0 absent'],
                outsideScope=['nonempty dispatch-key decoding/XOR', 'SDK step at1512124E6 and following B0 store',
                              'registered event callbacks', 'second coroutine', 'full Region/schema semantics', 'actual client'])
    errors=validate_summary(report)
    if errors: raise ValueError(errors)
    return report


def validate_summary(report):
    errors=[]
    expected=dict(inputSHA256=HASHES,fixtureCount=6,entry=hex(ENTRY),stop=hex(STOP),semanticStatus='unknown',
                  runtimeStatus='not-tested',wholeDispatchExecuted=False)
    if any(report.get(k)!=v for k,v in expected.items()): errors.append('Config scope mismatch')
    rows=report.get('results',[])
    if [r.get('name') for r in rows]!=[c[0] for c in CASES]: errors.append('Config cases mismatch')
    for row,(_,first,second) in zip(rows,CASES):
        if row.get('plainWire')!=plain_wire(first,second).hex() or row.get('sessionB0Unchanged') is not True: errors.append('Config wire/B0 mismatch')
        rejected=first=='{' or second=='{'; completed=0 if first=='{' else 1 if second=='{' else 2
        if row.get('jsonHelperRejected')!=rejected or row.get('stop')!=(None if rejected else hex(STOP)): errors.append('Config outcome mismatch')
        calls=row.get('configCalls',[])
        expected_calls=[dict(mode=i+1,arrays=[text.encode().hex(),''],receiverPreserved=True) for i,text in enumerate((first,second))][:1 if first=='{' else 2]
        if calls!=expected_calls: errors.append('Config source bytes/modes mismatch')
        if row.get('events')!=[dict(id=919,mode=i+1) for i in range(completed)]: errors.append('Native config events mismatch')
        returns=row.get('configReturns',[])
        if len(returns)!=completed: errors.append('Missing native config returns')
        values={'downloadThreadNum':0,'showexception':0}
        for i,returned in enumerate(returns[:completed]):
            source=json.loads((first,second)[i])
            if 'downloadThreadNum' in source:
                v=source['downloadThreadNum']; values['downloadThreadNum']=v if -(1<<31)<=v<1<<31 else 0
            if 'showexception' in source: values['showexception']=int(source['showexception'])
            values.update({name:source.get(name,[]) for name in LISTS})
            if (returned.get('address')!=hex(RETURNS[i]) or returned.get('returnAL')!=1
                    or returned.get('values')!=values): errors.append('Native config scalar/list/default mismatch')
        if not rejected:
            if row.get('regionGlobals')!=dict(text='inner',textCopy='inner',uint32=0xffffffff): errors.append('Native Region globals mismatch')
            if row.get('nativePlaintextSHA256')!=hashlib.sha256(plain_wire(first,second)).hexdigest(): errors.append('Native plaintext mismatch')
    return errors


def main():
    a=parse_args(); b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,records,a.libraries,Path(__file__).resolve().parents[1])
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],wholeDispatchExecuted=False)))


if __name__=='__main__': main()
