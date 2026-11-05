"""Verify named HTTP/SDK hook argument prefixes on the pinned CN72 binary.

No patch is installed and no function is redirected. This checks declarations
against bounded native argument propagation, not full HTTP/RSA/loader behavior.
"""
import argparse
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver, Names
    from .extract_client72_signatures import parameter_start, parameter_words
    from .client72_emulator import Offline72, STACK, END
    from .client72_session_runtime import fragment
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, BODY, number, TypeResolver, Names
    from extract_client72_signatures import parameter_start, parameter_words
    from client72_emulator import Offline72, STACK, END
    from client72_session_runtime import fragment

TARGETS=((2910,23459,'MakeInitialUrl',0x15A3464C0,True,(('targetUrl','string'),('localUrl','string'))),
         (8135,60229,'LoadURL',0x15A366300,False,(('url','string'),('force','bool'))),
         (4389,33239,'RSAEncrypt',0x15997C290,True,(('publickey','string'),('content','string'))),
         (1140,9392,'PtrToStringAnsi',0x159C21C90,True,(('ptr','intptr'),)))


def check_declaration(row,owner,index,name,address,is_static,parameters):
    if (row.get('ownerTypeIndex')!=owner or row.get('methodIndex')!=index or row.get('name')!=name or
            row.get('entry')!=hex(address) or row.get('static') is not is_static or
            [(p.get('name'),p.get('kind')) for p in row.get('parameters',[])]!=list(parameters)):
        raise ValueError('Hook declaration differs from reviewed target')
    registers=('rcx','rdx','r8','r9')[0 if is_static else 1:]
    if any(p.get('register')!=reg for p,reg in zip(row['parameters'],registers)):
        raise ValueError('Hook argument register differs from reviewed native prefix')


def declarations(binary,metadata,startup,records,summary):
    pe=PE(binary);h=pe.offset(HEADER_VA)
    param_base=BODY+((number(binary,h+0x174)+0xae6bcd68)&0xffffffff)
    resolver=TypeResolver(pe,startup,records,h);names=Names(metadata,int(summary['stringBase'],16))
    by_index={r['index']:r for r in records};result=[]
    for owner,index,name,address,is_static,params in TARGETS:
        matches=[m for m in by_index[owner]['methods'] if m['index']==index]
        if len(matches)!=1:raise ValueError('Missing hook method')
        method=matches[0]
        first=parameter_start(index,number(metadata,int(summary['methodOffset'],16)+index*26+4))
        parameters=[]
        for ordinal in range(method['parameterCount']):
            at=first+ordinal
            raw_name,ref=parameter_words(at,number(metadata,param_base+at*8),number(metadata,param_base+at*8+4))
            resolved=resolver.resolve_index(ref)
            if resolved.get('byRef'):raise ValueError('Unexpected by-ref hook parameter')
            parameters.append(dict(name=names.decode(raw_name),kind=resolved['kind'],typeIndex=ref,
                                   register=('rcx','rdx','r8','r9')[ordinal+(0 if is_static else 1)]))
        row=dict(ownerTypeIndex=owner,owner=by_index[owner]['name'],methodIndex=index,name=method['name'],
                 entry=method['registeredAddress'],static=bool(int(method['flags'],16)&0x10),parameters=parameters)
        check_declaration(row,owner,index,name,address,is_static,params);result.append(row)
    closure=by_index[8146]
    fields={(f['name'],f.get('offset'),f['type']['kind'],f['type'].get('typeDefinition')) for f in closure['fields']}
    if fields!={('<>4__this',0x10,'class',8135),('url',0x18,'string',None),('force',0x20,'bool',None)}:
        raise ValueError('Browser native capture-object layout changed')
    uri=[m for m in by_index[1533]['methods'] if m['registeredAddress']=='0x15a1103f0']
    if len(uri)!=1 or uri[0]['name']!='CreateThis':raise ValueError('HTTP prefix Uri consumer changed')
    return result


def managed_string(e,text):
    raw=text.encode('utf-16-le');p=e.alloc(22+len(raw));e.write(p+0x10,len(raw)//2,'I')
    if raw:e.uc.mem_write(p+0x14,raw)
    return p


def recover(binary,metadata,startup,records,summary,libraries):
    declared=declarations(binary,metadata,startup,records,summary);fixtures=[]
    e=Offline72(binary,libraries,records,True)
    try:
        for owner,index,name,address,is_static,params in TARGETS:e.allow(address)
        for ordinal,(target,local) in enumerate((('https://example.invalid/path','http://127.0.0.1:8088/'),
                                               ('/local-path','file:///fixture/'))):
            e.prepare();target_ptr=managed_string(e,target);local_ptr=managed_string(e,local)
            cls=e.synthetic_type(0x1458863A8);uri=e.alloc();e.write(uri,cls)
            def allocate_uri():
                if e.reg('rcx')!=cls:raise ValueError('Unexpected HTTP prefix allocation')
                return uri
            e.models[0x1405336C0]=allocate_uri
            trace=fragment(e,0x15A3464C0,{0x15A1103F0},dict(rsp=STACK+0x100008,rcx=target_ptr,rdx=local_ptr))
            if (e.reg('rdi'),e.reg('rcx'),e.reg('rdx'),e.reg('r8'),e.reg('r9'))!=(target_ptr,uri,local_ptr,0,1):
                raise ValueError('HTTP URL parameters changed before native Uri.CreateThis')
            fixtures.append(dict(kind='http-prefix',variant=ordinal,trace=trace,passed=True))
        for force in (0,1):
            e.prepare();e.write(0x145B79ECE,1,'B')  # Explicit already-initialized metadata cache.
            cls=e.synthetic_type(0x145888FE8);capture=e.alloc();e.write(capture,cls);browser=e.alloc()
            url=managed_string(e,'https://example.invalid/路径' if force else 'file:///fixture/')
            def allocate_capture():
                if e.reg('rcx')!=cls:raise ValueError('Unexpected Browser capture allocation')
                return capture
            e.models[0x1405336C0]=allocate_capture
            trace=fragment(e,0x15A366300,{0x15A366342},dict(rsp=STACK+0x100008,rcx=browser,rdx=url,r8=force))
            if (e.read(capture+0x10),e.read(capture+0x18),e.read(capture+0x20,'B'))!=(browser,url,force):
                raise ValueError('Browser receiver/url/force capture disagrees')
            fixtures.append(dict(kind='browser-prefix',force=force,trace=trace,passed=True))
        e.models.pop(0x1405336C0)
        for variant in range(2):
            e.prepare();key=managed_string(e,f'public_key_fixture_{variant}');plain=managed_string(e,f'plaintext_fixture_{variant}')
            trace=fragment(e,0x15997C290,{0x1405336C0},dict(rsp=STACK+0x100008,rcx=key,rdx=plain))
            if (e.reg('rsi'),e.reg('r14'))!=(key,plain):raise ValueError('SDK RSA key/content ABI mismatch')
            fixtures.append(dict(kind='sdk-rsa-prefix',variant=variant,trace=trace,passed=True))
        # Execute the target strlen; stop at the native string factory before
        # UTF-8 conversion/managed allocation, neither of which is modeled.
        e.allowed.append((0x141CB67E0,0x141CB6888))
        for variant,value in enumerate((b'',b'http://127.0.0.1:8088/',b'12345678',b'ab\0trailing','路径'.encode('utf-8'))):
            e.prepare();p=e.alloc(len(value)+32)+(variant%2)
            e.uc.mem_write(p,value+b'\0')
            trace=fragment(e,0x159C21C90,{0x14052FBF0},dict(rsp=STACK+0x100008,rcx=p))
            expected=len(value.split(b'\0',1)[0])
            if (e.reg('rcx'),e.reg('edx'))!=(p,expected):raise ValueError('Native ANSI-pointer length handoff mismatch')
            fixtures.append(dict(kind='marshal-prefix',variant=variant,byteLength=expected,trace=trace,passed=True))
        e.prepare();e.run(0x159C21C90,0,0)
        if e.reg('rax')!=0:raise ValueError('Native null pointer did not return null')
        fixtures.append(dict(kind='marshal-null-native-return',passed=True))
    finally:e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,declarations=declared,fixtureCount=len(fixtures),fixtures=fixtures,
                browserCaptureTypeIndex=8146,httpUriConsumer='0x15a1103f0',
                scope='named-hook-entry-argument-prefixes',patchInstalled=False,runtimeStatus='not-tested',
                models=['managed string and receiver storage','typed Uri/capture allocation only',
                        'explicit initialized Browser metadata-cache flag'],
                outsideScope=['HTTP routing/URI function completion','native string factory conversion/allocation',
                              'RSA encryption/decryption/signature verification','trampoline relocation and live loading',
                              'startup/hotfix-selected code','native_sdk shop hooks','real client acceptance'])


def main():
    a=parse_args()
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,summary=extract(b,m,startup=s,structures=True)
    report=recover(b,m,s,records,summary,a.libraries)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf-8')
    print(json.dumps(dict(declarations=len(report['declarations']),fixtures=report['fixtureCount'])))


def parse_args():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--summary-output',required=True,type=Path)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    return a


if __name__=='__main__':main()
