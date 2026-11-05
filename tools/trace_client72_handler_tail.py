"""Continue Token Handler through robot-input storage and native version hash.

Version components and two-string formatting are declared library/environment
inputs. Target concatenation, ASCII encoding, SHA1 and Base64 execute. This stops
before the remaining Handler UI/SDK branches, and does not run Login construction.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES,read_verified,extract,PE,HEADER_VA,BODY,number,TypeResolver,Names
    from .extract_client72_literals import Literals72,USAGE_REGISTRATION
    from .extract_client72_signatures import parameter_start,parameter_words
    from .client72_emulator import Offline72,GLOBAL,OBJECT
    from .client72_response_crypto import ResponseCrypto72,REJECT
    from .client72_version_hash import VersionHash72,TYPE_BINDINGS,HASH_ENTRY,HASH_RETURN
    from .trace_client72_handler_core import HandlerReader72,HandlerCore72,STOP
    from .trace_client72_response_crypto import discover_private_key,response_wire
    from .client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from .trace_client72_token import varint
except ImportError:
    from extract_client72 import HASHES,read_verified,extract,PE,HEADER_VA,BODY,number,TypeResolver,Names
    from extract_client72_literals import Literals72,USAGE_REGISTRATION
    from extract_client72_signatures import parameter_start,parameter_words
    from client72_emulator import Offline72,GLOBAL,OBJECT
    from client72_response_crypto import ResponseCrypto72,REJECT
    from client72_version_hash import VersionHash72,TYPE_BINDINGS,HASH_ENTRY,HASH_RETURN
    from trace_client72_handler_core import HandlerReader72,HandlerCore72,STOP
    from trace_client72_response_crypto import discover_private_key,response_wire
    from client72_rsa_fixture import private_numbers,encrypt_seed,sign_sha256
    from trace_client72_token import varint

CASES=(('full-version',('CNRELWin7.2.0','engine-unused','client-unused','123','456','789'),'secret-A',b'robot\x00\xff',False),
       ('empty',(None,)*6,'',b'',False),
       ('sha1-length55',('v'*48,None,None,None,None,None),'',b'\x80',False),
       ('sha1-length56',('v'*49,None,None,None,None,None),'',b'\xff'*64,False),
       ('sha1-length64',('v'*57,None,None,None,None,None),'',bytes(range(256)),False),
       ('ascii-unicode',('V',None,None,None,None,None),'\u5bc6\u94a5\xe9\U0001f511\0',b'\0\xff',False),
       ('invalid-signature',('CNRELWin7.2.0',None,None,None,None,None),'secret-rejected',b'rejected',True))


class TailReader72(HandlerReader72):
    def blob(self,kind):
        if kind!='string':return super().blob(kind)
        self.sync();data=self.input.blob();self.position();raw=data.decode('utf8').encode('utf-16-le');e=self.e
        p=e.alloc(22+len(raw));e.write(p+16,len(raw)//2,'I')
        if raw:e.uc.mem_write(p+20,raw)
        self.values[p]=dict(kind=kind,hex=data.hex());return p


class HandlerTail72(HandlerCore72):
    def __init__(self,e,binary,metadata,client_seed,signer,parts):
        super().__init__(e,binary,metadata,client_seed,signer,True)
        self.version=VersionHash72(self,parts);self.tail_entries=0
        # A non-pooled prior ByteString proves both replacement and empty clearing.
        self.prior_robot=e.alloc(64);e.write(self.prior_robot,e.read(0x145898960));e.write(GLOBAL+0x11c18,self.prior_robot)
        self.prior_hash=self.version.string('prior-version-hash');e.write(GLOBAL+0x11b90,self.prior_hash)

    def observe_response(self,address):
        if address==STOP:
            self.tail_entries+=1;ResponseCrypto72.observe_response(self,address)
        else:super().observe_response(address)
        self.version.observe(address)
        if address==HASH_RETURN:self.stop=address;self.e.uc.emu_stop()


def declarations(binary,metadata,startup,records):
    pe=PE(binary);h=pe.offset(HEADER_VA);literals=Literals72(binary,metadata)
    cache=number(binary,pe.offset(USAGE_REGISTRATION)+0x48,'Q');resolver=TypeResolver(pe,startup,records,h)
    bindings=[]
    for i,cell,index in TYPE_BINDINGS:
        key=(i*0x57c15286+0x26ddebda)&0xffffffff
        source=((number(metadata,literals.usages_base+i*8)+0xabbdcabf)&0xffffffff)^key^0x2a357d90
        slot=number(metadata,literals.usages_base+i*8+4)^key^0x1855aa65;value=resolver.resolve_index(source&0x1fffffff)
        if source>>29!=1 or cache+slot*8!=cell or value['kind']!='class' or value['typeDefinition']!=index:
            raise ValueError('Version hash type registration changed')
        bindings.append(dict(usageRow=i,cell=hex(cell),typeIndex=index,name=value['name']))
    by_index={r['index']:r for r in records};names=Names(metadata,BODY+(number(binary,h+0x70)^0x18202ccc))
    method_base=BODY+(number(binary,h+0xb0)^0x51324a1f)
    parameter_base=BODY+((number(binary,h+0x174)+0xae6bcd68)&0xffffffff);methods=[]
    for address,name,parameters in ((0x1531E9EB0,'set_robotCheckLoginInputData',['value']),
            (0x1531D02B0,'GetUniversalGameVersion',[]),
            (0x1531D03D0,'GetGameVersionAndChangelist',['gameVer','engineCL','clientCL','resourceCL','silenceCL','dataCL']),
            (HASH_ENTRY,'CalcVersionHash',['version','secretKey'])):
        method=next(m for m in by_index[12832]['methods'] if m['registeredAddress']==hex(address))
        if method['name']!=name or method['parameterCount']!=len(parameters):raise ValueError('Version hash named declaration changed')
        actual=[]
        if parameters:
            first=parameter_start(method['index'],number(metadata,method_base+method['index']*26+4))
            for i in range(first,first+len(parameters)):
                ni,ti=parameter_words(i,number(metadata,parameter_base+i*8),number(metadata,parameter_base+i*8+4))
                actual.append(dict(name=names.decode(ni),type=resolver.resolve_index(ti)))
        if [p['name'] for p in actual]!=parameters:raise ValueError('Version hash parameter names changed')
        methods.append(dict(address=hex(address),name=name,parameters=actual))
    for slot,address in ((5,0x159AE26D0),(11,0x159AE2BE0),(17,0x159AE3540)):
        if not any(m['virtualSlot']==slot and m['registeredAddress']==hex(address) for m in by_index[420]['methods']):
            raise ValueError('ASCII virtual method association changed')
    selected=literals.selected((40025,61645,61646,62885))['literals']
    if {r['index']:r['value'] for r in selected}!={40025:'_S',61645:'_R',61646:'_D',62885:'{0}{1}mhy2020'}:
        raise ValueError('Version hash target literals changed')
    return dict(types=bindings,methods=methods,literals=selected,encoding='ASCIIEncoding',hash='SHA1CryptoServiceProvider')


def expected_input(parts,secret):
    # A null component is skipped by the actual GetUniversalGameVersion branch.
    version=(parts[0] or '')+''.join(prefix+parts[index] for prefix,index in (('_R',3),('_S',4),('_D',5)) if parts[index] is not None)
    text=version+secret+'mhy2020';units=text.encode('utf-16-le')
    # .NET ASCII's one-character '?' fallback replaces each UTF16 code unit,
    # including each half of a surrogate pair. Embedded NUL remains a byte.
    raw=bytes(value if value<128 else 63 for value in (int.from_bytes(units[i:i+2],'little') for i in range(0,len(units),2)))
    return version,raw,base64.b64encode(hashlib.sha1(raw).digest()).decode('ascii')


def recover(binary,metadata,startup,records,libraries,signing_key):
    declared=declarations(binary,metadata,startup,records)
    private,_,_=discover_private_key(binary,metadata,records,libraries);client=private_numbers(private)
    signer=private_numbers(signing_key.read_bytes());plain=bytes.fromhex('fedcba9876543210');client_seed=0x0123456789abcdef
    fields=[]
    for offset,tag,kind,consumer,argument in ((0x60,3,'bytes','set_robotCheckLoginInputData','value'),(0x80,658,'string','CalcVersionHash','secretKey')):
        f=next(f for r in records if r['index']==37481 for f in r['fields'] if f.get('offsetKind')=='instance' and f['offset']==offset)
        fields.append(dict(typeIndex=37481,clientField=f['name'],objectOffset=offset,tag=tag,type=kind,
                           consumer=consumer,argument=argument,semanticStatus='unresolved'))
    fixtures=[]
    for name,parts,secret,blob,rejected in CASES:
        signature=sign_sha256(signer,plain)
        if rejected:signature=bytes([signature[0]^1])+signature[1:]
        wire=response_wire(encrypt_seed(client,plain),signature)+b'\x48\x01\x32\x01t'
        if blob:wire+=b'\x1a'+varint(len(blob))+blob
        if secret:
            raw=secret.encode('utf8');wire+=b'\x92\x29'+varint(len(raw))+raw
        e=Offline72(binary,libraries,records,True);reader=None;runtime=None
        try:
            reader=TailReader72(e);read=reader.run(37481,wire);runtime=HandlerTail72(e,binary,metadata,client_seed,signer,parts)
            trace,key=runtime.run_handler();v=runtime.version
            if runtime.stop!=(REJECT if rejected else HASH_RETURN):raise ValueError('Handler tail success/rejection boundary changed')
            raw_pointer=e.read(OBJECT+0x60);actual_hash=e.read(GLOBAL+0x11b90)
            expected_version,encoded,expected_hash=expected_input(parts,secret)
            if rejected:
                if key is not None or v.formats or v.hash_inputs or v.version_calls or runtime.tail_entries:
                    raise ValueError('Rejected crypto reached version hash')
                if e.read(GLOBAL+0x11c18)!=runtime.prior_robot or actual_hash!=runtime.prior_hash:
                    raise ValueError('Rejected crypto modified post-crypto globals')
            else:
                if runtime.tail_entries!=1 or v.version_calls!=1 or e.read(GLOBAL+0x11c18)!=raw_pointer:
                    raise ValueError('Handler lost robot-input pointer or version source sequence')
                if v.hash_inputs!=[encoded] or v.hash_outputs!=[hashlib.sha1(encoded).digest()] or runtime.text(actual_hash)!=expected_hash:
                    raise ValueError('Native ASCII/SHA1/Base64 differs from independent oracle')
                if [f['inputs'] for f in v.formats]!=[[expected_version,secret]]:
                    raise ValueError('Native response/version source arguments differ')
            fixtures.append(dict(name=name,rejected=rejected,versionParts=parts,secret=secret,robotHex=blob.hex(),
                                 stop=hex(runtime.stop),tailEntries=runtime.tail_entries,versionSourceCalls=v.version_calls,
                                 robotPointerPreserved=not rejected,priorGlobalsUnchanged=rejected,
                                 nativeHashInputHex=encoded.hex() if not rejected else None,
                                 nativeSHA1=v.hash_outputs[0].hex() if v.hash_outputs else None,
                                 versionHash=runtime.text(actual_hash),nativeOperations=runtime.operations,
                                 formats=v.formats,reader=read,trace=trace,passed=True))
            print(json.dumps(dict(case=name,stop=hex(runtime.stop),hashBytes=len(encoded) if not rejected else None)),flush=True)
        finally:
            if runtime:runtime.close()
            if reader:reader.close()
            e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=len(fixtures),fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k not in ('reader','trace')} for f in fixtures],
                declarations=declared,fieldBindings=fields,entry='0x1507be0e0',stop=hex(HASH_RETURN),
                output=dict(globalOffset=0x11b90,owner='Miscs',field='<versionHash>k__BackingField',
                            loginTag=1996,loginField='client_version_hash',loginConsumption='previous separate native source/writer proof'),
                runtimeStatus='not-tested',wholeHandlerExecuted=False,continuousLoginExecuted=False,
                modelsFrom=['handler-core-observations.json'],
                addedModels=['six named byref version components from GetGameVersionAndChangelist',
                             'String.Format for exact target literal and two nullable strings',
                             'managed allocations/type vtables; initialized native-constructed question-mark fallback caches',
                             'non-pooled ByteString prior value; valid UTF8 response string storage'],
                nativeExecuted=['robot input setter/equality','GetUniversalGameVersion concatenation','ASCII encoding/default fallbacks',
                                'SHA1CryptoServiceProvider/SHA1Internal','Buffer.BlockCopy','Base64 and versionHash store'],
                outsideScope=['real local version source parser','pooled/refcounted ByteString release','full general String.Format',
                              'remaining Handler branches and Login continuity','production trust/SDK initialization','real-client acceptance'])


def validate_summary(report):
    errors=[]
    required=dict(schemaVersion=1,inputSHA256=HASHES,fixtureCount=7,entry='0x1507be0e0',stop=hex(HASH_RETURN),
                  runtimeStatus='not-tested',wholeHandlerExecuted=False,continuousLoginExecuted=False)
    if any(report.get(k)!=v for k,v in required.items()):errors.append('Handler tail: scope/input mismatch')
    declared=report.get('declarations',{})
    if declared.get('encoding')!='ASCIIEncoding' or declared.get('hash')!='SHA1CryptoServiceProvider':
        errors.append('Handler tail: encoding/hash identity mismatch')
    types=declared.get('types',[])
    if [(t.get('usageRow'),t.get('cell'),t.get('typeIndex')) for t in types]!=[(i,hex(c),t) for i,c,t in TYPE_BINDINGS]:
        errors.append('Handler tail: runtime class binding mismatch')
    literal_values={r.get('index'):r.get('value') for r in declared.get('literals',[])}
    if literal_values!={40025:'_S',61645:'_R',61646:'_D',62885:'{0}{1}mhy2020'}:
        errors.append('Handler tail: version/hash literals differ')
    expected=[(0x60,3,'bytes','BHHECPBGDKB','set_robotCheckLoginInputData','value'),
              (0x80,658,'string','FIHDHDLJBLC','CalcVersionHash','secretKey')]
    fields=report.get('fieldBindings',[])
    if [(f.get('objectOffset'),f.get('tag'),f.get('type'),f.get('clientField'),f.get('consumer'),f.get('argument')) for f in fields]!=expected:
        errors.append('Handler tail: raw field consumer mismatch')
    if any(f.get('semanticStatus')!='unresolved' or f.get('typeIndex')!=37481 for f in fields):
        errors.append('Handler tail: unknown semantics were promoted')
    output=report.get('output',{})
    if any(output.get(k)!=v for k,v in dict(globalOffset=0x11b90,owner='Miscs',field='<versionHash>k__BackingField',
                                          loginTag=1996,loginField='client_version_hash',
                                          loginConsumption='previous separate native source/writer proof').items()):
        errors.append('Handler tail: hash output source changed')
    rows=report.get('results',[])
    if len(rows)!=len(CASES):errors.append('Handler tail: missing cases')
    for row,(name,parts,secret,blob,rejected) in zip(rows,CASES):
        version,encoded,hashed=expected_input(parts,secret)
        target=dict(name=name,rejected=rejected,versionParts=list(parts),secret=secret,robotHex=blob.hex(),
                    stop=hex(REJECT if rejected else HASH_RETURN),tailEntries=int(not rejected),versionSourceCalls=int(not rejected),
                    robotPointerPreserved=not rejected,priorGlobalsUnchanged=rejected,
                    nativeHashInputHex=None if rejected else encoded.hex(),
                    nativeSHA1=None if rejected else hashlib.sha1(encoded).hexdigest(),
                    versionHash='prior-version-hash' if rejected else hashed,
                    nativeOperations={'101':0,'103':-7 if rejected else 0},passed=True)
        # In-memory reports use tuples/int keys; JSON summaries use lists/strings.
        actual=dict(row);actual['versionParts']=list(row.get('versionParts',[]))
        actual['nativeOperations']={str(k):v for k,v in row.get('nativeOperations',{}).items()}
        if any(actual.get(k)!=v for k,v in target.items()):errors.append(f'Handler tail: {name} native outcome differs')
        if row.get('formats')!=([] if rejected else [dict(format='{0}{1}mhy2020',inputs=[version,secret],result=version+secret+'mhy2020')]):
            errors.append(f'Handler tail: {name} format/source arguments differ')
    return errors


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path);p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'))
    p.add_argument('--signing-key',type=Path,default=Path('src/main/resources/keys/SigningKey.der'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()];records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,a.libraries,a.signing_key)
    errors=validate_summary(report)
    if errors:raise ValueError(errors)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],stop=report['stop'],semantics='unresolved')))


if __name__=='__main__':main()
