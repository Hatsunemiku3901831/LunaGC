"""Frozen five-point route source/artifact audit and one pure 25-read collector."""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess

from . import verify_sdk72_config_stage_proxy as stage
from . import client72_sdk_natural_config_contract as natural
from . import audit_sdk72_clean_loader as peutil
from . import observe_client72_key_loader as frozen

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'local/migration72/d02-resume-20261110/b-route-implementation'
OLD = ROOT/'local/migration72/d02-parallel-20261109'
SELECT = ROOT/'local/migration72/d02-resume-20261109/r2-config-select'
STATE = 'Sdk72ConfigRouteProxyState'
EXTRAS = [(STATE,48),('sdk72_config_route_inner',49),('sdk72_config_inner',50),('sdk72_key_input_inner',51)]
EXE_WINDOWS = ((0x6d80910,359),(0x198035e8,46))
SDK_WINDOWS = ((0x36fba0,261),(0x378850,5422),(0x380e60,320))
SITE_RVAS = (0x6d809b5,0x6d809cf,0x37894a,0x379d5b,0x380f5a)
WIDTHS = (14,14,15,16,15)
SITE_NAMES = ('P0','P1','P2','P7','M0')
CN_ENVS = (0,1,4,6,8,9,22,19)
MAX_READS, MAX_BYTES, MAX_SINGLE = 25,9262,512
FAILURES = {101:'self-hold',102:'self-path',104:'module-absent',105:'module-hold',106:'module-path',
    107:'module-info',108:'module-size',109:'module-disk',110:'window-read',111:'window-identity',114:'binding-budget',
    201:'p0-return-read',202:'unknown-p0-caller',203:'name-read',204:'name-mismatch',205:'native-cell-read',
    206:'native-cell-target',207:'p2-return-read',208:'unknown-p2-caller',
    **{113+1000*n:'hook-install-'+SITE_NAMES[n] for n in range(5)}}
FIELDS = ('schema','bytes','phase','failure','win32_error','installed_mask','exe_base','sdk_base',
    'exe_size','sdk_size','original_held','self_held','exe_held','sdk_held','event','facts','route_class','reserved0')
FACT_NAMES = ('ordinaryNameValidated','nativeCallReturned','writerCaller','parserAccepted','mapCaller','lookupCompleted','keyPresent','valueNonempty','knownCnRoute','writerNormalEpilogue')
SEMANTIC_CASES = ('normal','wrong-p0','wrong-p2','wrong-m0-caller','wrong-m0-frame','m0-before-writer','foreign-thread','foreign-p7','p7-reuse','terminal-no-read','map-budget')
FAILURES.update({301:'writer-closed',302:'writer-frame',303:'map-caller-read',304:'map-caller-mismatch',305:'map-descriptor',306:'map-sentinel',307:'map-node',308:'map-key',309:'map-value',310:'node-budget',311:'map-read-budget',312:'node-cycle'})

def require(value,message):
    if not value: raise ValueError(message)

def digest(raw): return hashlib.sha256(raw).hexdigest()
def json_bytes(value): return (json.dumps(value,sort_keys=True,indent=2)+'\n').encode('utf8')
def source_id(path):
    path=Path(path); raw=path.read_bytes()
    return dict(path=path.relative_to(ROOT).as_posix(),bytes=len(raw),sha256=digest(raw))

def confined(path):
    path=Path(path).resolve()
    require(path.is_relative_to(RAW.resolve()),'Output/artifact is outside route experiment')
    return path

def write_new_or_equal(path,raw):
    if path.exists(): require(path.read_bytes()==raw,'Existing private build input differs')
    else:
        path.parent.mkdir(parents=True,exist_ok=True)
        with path.open('xb') as stream: stream.write(raw)

def frozen_inputs():
    spec=ROOT/'docs/CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md'
    projection=ROOT/'local/migration72/d02-resume-20261110/b-natural-successor/projection2.private.json'
    bridge=ROOT/'local/migration72/d02-resume-20261110/b-natural-successor/implement-bridge.zh.md'
    require(digest(spec.read_bytes())=='ddea93c0ecf709dc15e58ea89c84dc25fcf699f94fc506e491b4bb630a03b3e7','Route specification differs')
    require(digest(projection.read_bytes())=='9c730dea9203ab1abc7dd53d79637c66850393a42ae405c297bf79b879dc0340','Five-point projection differs')
    require(digest(bridge.read_bytes())=='1b1405fbd895eac3306d983fd9b9196e35f78228ea5817f4d4e085c35baa4b94','Implementation bridge differs')
    old,pins=stage.frozen_inputs();p=json.loads(projection.read_bytes())
    require(p['passed'] is True and len(p['checks'])==38 and all(p['checks'].values()),'Projection not complete')
    pins.update({name:row['sha256'] for name,row in p['inputs'].items()})
    for f in (spec,projection,bridge):pins[f.relative_to(ROOT).as_posix()]=digest(f.read_bytes())
    prior=ROOT/'local/migration72/d02-resume-20261110/b-implementation/accepted.private.json'
    accepted=json.loads(prior.read_bytes())
    require(digest(prior.read_bytes())=='77296f80138e094ac5a76eae167ac436e85691189246b5cad5e9ccb2b4057abd','Historical stage acceptance differs')
    for row in [accepted['proof'],accepted['production'],accepted['fixture'],*accepted['sourceClosure'].values()]:pins[row['path']]=row['sha256']
    # Historical manifest is verified against an immutable snapshot, NEVER the
    # current manifest after main adds this new example. Old gate stays intact.
    for name in ('Cargo.toml','Cargo.lock'):
        path=RAW/'historical-stage'/name
        expected=accepted['manifest']['sha256'] if name=='Cargo.toml' else '8482fc5d3e86ac5416038e446431e729a5e55c338d8824407f9aab878c323b2f'
        require(digest(path.read_bytes())==expected,'Historical build input differs')
        pins[path.relative_to(ROOT).as_posix()]=expected
    pins['tools/client72_sdk_natural_config_contract.py']='b5b86b31f5fb46736fae1e68168ad3298a73b501607b75d9cc2ca8214d9ae6ba'
    increment='local/migration72/d02-resume-20261110/b-natural-successor/github-increment-20261110T012716Z/'
    for name,sha in {'manifest.json':'38eaff793bd5afcec631c3c1cf18d8891d7b8e54f8d0125820526ce638b31385',
        'request-ledger.json':'116b6e6778b879bc4485edc2b2396c8cbc60eb2b4a5f90ec11e6075ef4bacb48',
        'result.json':'85df625e7089347d4768437c893488b577a7c965542833a5f3fb5e46197604c6'}.items():pins[increment+name]=sha
    for name,expected in pins.items():require(digest((ROOT/name).read_bytes())==expected,'Frozen source differs: '+name)
    sites=[old['sites'][n] for n in (0,1,2,7)]+[dict(id='M0',module='sdk',startRva='0x380f5a',endRvaExclusive='0x380f69',stolenBytes=15,originalHex='498d4e10e8e1d90d0090488d4c2448')]
    return dict(sites=sites,projection=p),pins

def source_epoch():
    """Actual Rust includes plus transitive local Python imports, pinned by path."""
    paths={ROOT/'patch/examples/sdk72_config_route_proxy.rs',*list((ROOT/'patch/examples/sdk72_config_route_proxy').glob('*')),
        ROOT/'tools/verify_sdk72_config_route_proxy.py',ROOT/'tools/observe_client72_config_route_proxy.py',
        ROOT/'tools/test_verify_sdk72_config_route_proxy.py',ROOT/'tools/test_observe_client72_config_route_proxy.py'}
    required=['patch/examples/sdk72_config_route_proxy/'+name for name in ('loader.rs','observer.rs','map.rs','fixture.rs','fixture.S')]
    require(all((ROOT/name).is_file() for name in required),'Route source closure is incomplete')
    queue=[p for p in paths if p.suffix=='.py']; seen=set()
    while queue:
        p=queue.pop()
        if p in seen: continue
        seen.add(p)
        tree=ast.parse(p.read_text(encoding='utf8'))
        for node in ast.walk(tree):
            names=[]
            if isinstance(node,ast.ImportFrom):
                if node.level and node.module: names.append(node.module.split('.')[0])
                elif node.level: names.extend(a.name for a in node.names)
                elif node.module and node.module.startswith('tools.'): names.append(node.module.split('.')[1])
            elif isinstance(node,ast.Import): names.extend(a.name.split('.')[1] for a in node.names if a.name.startswith('tools.'))
            for name in names:
                candidate=ROOT/'tools'/str(name+'.py')
                if candidate.is_file() and candidate not in paths: paths.add(candidate); queue.append(candidate)
    # Frozen Rust transitive include set is explicit; old files are never edited.
    paths.update(ROOT/name for name in ('patch/examples/sdk72_clean_proxy/sdk72_clean_proxy_core.rs',
        'patch/examples/sdk72_clean_proxy/sdk72_clean_proxy_table.rs','patch/examples/sdk72_clean_proxy/sdk72_clean_proxy.S',
        'patch/examples/sdk72_key_proxy/sdk72_key_proxy_loader.rs','patch/examples/sdk72_config_proxy/observer.rs',
        'patch/src/modules/sdk72_key_input.rs','patch/build.rs',f'{frozen.ILHOOK}lib.rs',f'{frozen.ILHOOK}err.rs',
        f'{frozen.ILHOOK}x86.rs',f'{frozen.ILHOOK}x64.rs',f'{frozen.ILHOOK}x64/move_inst.rs',f'{frozen.ILHOOK}x64/tests.rs'))
    return {p.relative_to(ROOT).as_posix():source_id(p) for p in sorted(paths) if p.is_file()}

def materials(audit):
    original,unused=stage.frozen_inputs();mat=stage.materials(original)
    code=(ROOT/'local/migration72/d02-resume-20261110/b-url-window/code.bin').read_bytes()
    require(len(code)==320 and digest(code)=='29c2a60f06dd4a43c84ae595acc887e41b66508c7257fa49bf91a0a2890bed88','Map code differs')
    dispatcher=next(row for row in mat['sdkWindows'] if row['rva']==0x36fba0)
    mat.update(mapWriter=code,sites=audit['sites'],sdkWindows=[dispatcher,
        dict(rva=0x378850,bytes=5422,sha256=digest(mat['writer'])),dict(rva=0x380e60,bytes=320,sha256=digest(code))])
    return mat

def binding_source(audit,mat):
    rows=['use super::route_loader::Window;',f'pub const EXE_SIZE: u32 = 459665408;',
        'pub const EXE_SHA: &str = "8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5";',
        "pub const EXE_WINDOWS: [Window<'static>; 2] = ["]
    for (rva,width),raw in zip(EXE_WINDOWS,(mat['bridge'],mat['caller'])):
        rows.append(f'Window {{ rva: {rva}, bytes: {width}, sha: "{digest(raw)}" }},')
    rows+= ['];',"pub const SDK_WINDOWS: [Window<'static>; 3] = ["]
    for row in mat['sdkWindows']: rows.append('Window { rva: %d, bytes: %d, sha: "%s" },'%(row['rva'],row['bytes'],row['sha256']))
    rows+= ['];','pub const SITE_BYTES: [&[u8]; 5] = [']
    rows.extend('&['+','.join(str(x) for x in bytes.fromhex(row['originalHex']))+'],' for row in audit['sites'])
    rows+= ['];','pub const BRIDGE: &[u8] = &['+','.join(str(x) for x in mat['bridge'])+'];']
    rows+=['pub const WRITER_BODY: &[u8] = &['+','.join(str(x) for x in mat['writer'])+'];','pub const MAP_BODY: &[u8] = &['+','.join(str(x) for x in mat['mapWriter'])+'];', 'pub const ROUTES: [&[u8]; 8] = [']
    rows += ['&['+','.join(str(x) for x in route)+'],' for route in natural.ALLOWED_ROUTES]
    rows+= ['];']
    return ('\n'.join(rows)+'\n').encode('ascii')

def def_text():
    return '\n'.join(['LIBRARY sdk72_config_route_proxy','EXPORTS',
        *[f'  {name} @{n+1}' for n,name in enumerate(peutil.source_names())],
        *[f'  {name} @{ordinal}'+(' DATA' if name==STATE else '') for name,ordinal in EXTRAS]])+'\n'

def require_artifact(raw):
    pe=peutil.PE(raw); names=peutil.source_names();exports=pe.exports()
    peutil.require_exports(exports,names,EXTRAS)
    state=next(row for row in exports if row['name']==STATE)
    require(pe.bytes_at(state['rva'],256)==struct.pack('<II',3,256)+bytes(248),'Route initial DATA differs')
    section=next(s for s in pe.sections if s[2]<=state['rva']<s[2]+s[3])
    require(section[-1]&0x80000000 and not section[-1]&0x20000000,'Route DATA is not writable non-code')
    slots=[];resolvers=[]
    for index,name in enumerate(names):
        rva=next(r['rva'] for r in exports if r['name']==name);code=pe.bytes_at(rva,24)
        require(code[:2]==b'\xff\x25' and code[6:9]==bytes((0x6a,index,0xe8)) and code[13:20]==bytes.fromhex('488d642408ff25'),'Frozen forward thunk differs')
        slot=rva+6+struct.unpack_from('<i',code,2)[0]
        require(rva+24+struct.unpack_from('<i',code,20)[0]==slot and struct.unpack('<Q',pe.bytes_at(slot,8))[0]==pe.base+rva+6,'Frozen cold slot differs')
        slots.append(slot);resolvers.append(rva+13+struct.unpack_from('<i',code,9)[0])
    require(slots==[slots[0]+8*n for n in range(47)] and len(set(resolvers))==1,'Frozen forwarding table differs')
    for name,_ in EXTRAS[1:]:
        rva=next(r['rva'] for r in exports if r['name']==name)
        require(next(s for s in pe.sections if s[2]<=rva<s[2]+s[3])[-1]&0x20000000,'Active/dormant inner is not code')
    return dict(bytes=len(raw),sha256=digest(raw),exports=exports,stateRva=state['rva'],stateBytes=256,
        tableRva=slots[0],extraExportPurposes={STATE:'five-point route fixed facts DATA',
        'sdk72_config_route_inner':'only active five-site inner','sdk72_config_inner':'dormant frozen dependency',
        'sdk72_key_input_inner':'dormant frozen dependency'})

def decode_state(raw):
    require(len(raw)==256,'Route DATA length differs')
    state=dict(zip(FIELDS,struct.unpack_from('<6I2Q10I',raw)));state['trampolines']=struct.unpack_from('<5Q',raw,80)
    require(state['schema']==3 and state['bytes']==256 and state['reserved0']==0 and raw[120:]==bytes(136),'Route schema/reserved differs')
    phase,event,mask,facts=(state[k] for k in ('phase','event','installed_mask','facts'))
    require(phase in (0,1,2,3,5,6) and event in range(4) and mask&~31==0 and facts&~1023==0,'Invalid route metadata')
    require(all(state[n] in (0,1) for n in ('original_held','self_held','exe_held','sdk_held')),'Invalid held')
    require(all(bool(mask&(1<<n))==bool(t) for n,t in enumerate(state['trampolines'])),'Mask/trampoline differs')
    for high,low in ((8,7),(7,6),(6,5),(5,4),(4,2),(2,0),(3,2),(9,2),(1,0)):
        require(not facts&(1<<high) or facts&(1<<low),'Invalid route fact dependency')
    value=state['route_class']
    require(value in (0,1,2,3,*range(0x100,0x108)),'Invalid route class')
    expected=0 if not facts&(1<<5) else 1 if not facts&(1<<6) else 2 if not facts&(1<<7) else value if facts&(1<<8) else 3
    require(value==expected and bool(facts&(1<<8))==(value>=0x100),'Route facts/class disagree')
    require(bool(facts&(1<<1))==(event==2),'Native-return/event relation differs')
    require((phase==6)==(event==3) and (state['failure']!=0)==(event==3),'Failure seal differs')
    require(state['failure'] in FAILURES if event==3 else state['failure']==state['win32_error']==0,'Unknown failure')
    require(event!=0 or facts==0 and phase in (0,1,2),'No-attempt event has facts')
    if phase==2:require(mask==3 and event in (0,1),'EXE-only phase differs')
    if phase==5:require(mask==31 and all(state[n]==1 for n in ('original_held','self_held','exe_held','sdk_held')),'Five-site phase differs')
    return state

def event_output(state):
    bits=state['facts'];value=state['route_class'];facts={name:bool(bits&(1<<n)) for n,name in enumerate(FACT_NAMES)}
    route={0:'not-read',1:'absent',2:'empty',3:'unknown'}.get(value)
    if value>=0x100:route='cn-route-env-'+str(CN_ENVS[value-0x100])
    return dict(status=('no-arbitrary-p0-attempt','partial-attempt','same-owner-native-return','rejected-sealed')[state['event']],
        **facts,storedComboRouteClass=route,storedComboRouteObserved=bool(bits&(1<<5)),
        getterFallbackExpectedFromStaticContract=value in (1,2),normalEpilogueThenNativeReturned=bool(bits&2 and bits&(1<<9)),
        failure=FAILURES.get(state['failure']),naturalComboRouteUseObserved=False,storedEnvObserved=False,
        completePayloadValidated=False,naturalInitOrRoutePassed=False,voidIsBusinessSuccess=False,exceptionUnwindVerified=False)

def prefix(arrays,callback,userdata):
    prolog,epilog1,epilog2=arrays
    raw=prolog+b'\x48\xba'+struct.pack('<Q',userdata)+bytes.fromhex('4889e14883ec1048b8')
    raw+=struct.pack('<Q',callback)+bytes.fromhex('ffd04883c410')+epilog1+epilog2
    require(len(raw)==250,'Frozen ilhook prefix differs')
    return raw

def signed32(value):
    require(-2**31<=value<2**31,'Unknown/out-of-range near emitter encoding')
    return struct.pack('<i',value)

def moved_code(site,original,entry,dest):
    if site<4:return stage.moved_code((0,1,2,7)[site],original,entry,dest)
    require(len(original)==15 and original[:4]==bytes.fromhex('498d4e10') and original[4]==0xe8 and original[9:]==bytes.fromhex('90488d4c2448'),'M0 fixed instructions differ')
    target=entry+9+struct.unpack_from('<i',original,5)[0];near=abs(entry-dest)<0x7ffff000
    if near:return original[:4]+b'\xe8'+signed32(target-dest-9)+original[9:],'near'
    raw=bytearray(original[:4]+bytes.fromhex('ff1500000000')+original[9:])
    padding=(-(dest+len(raw)+5))%8;raw+=b'\xe9'+struct.pack('<I',padding+8)+b'\xcc'*padding
    struct.pack_into('<I',raw,6,len(raw)-10);raw+=struct.pack('<Q',target)
    return bytes(raw),'far'

def trampoline(arrays,site,original,entry,address,callback,userdata):
    head=prefix(arrays,callback,userdata);moved,kind=moved_code(site,original,entry,address+len(head))
    raw=head+moved+bytes.fromhex('ff2500000000')+struct.pack('<Q',entry+len(original))
    require(len(raw)<=384,'Actual emitter exceeds fixed 384-byte read')
    return raw+bytes(384-len(raw)),len(raw),kind

def artifact_contract(proxy,exe,sdk):
    verified=require_artifact(proxy);pe=peutil.PE(proxy)
    inner=next(r['rva'] for r in pe.exports() if r['name']=='sdk72_config_route_inner');wrappers=[]
    pattern=re.compile(re.escape(frozen.CALLBACK_PREFIX)+b'.{4}'+re.escape(frozen.CALLBACK_SUFFIX),re.S)
    for section in pe.sections:
        if section[-1]&0x20000000:
            for match in pattern.finditer(pe.take(section[4],section[3])):
                rva=section[2]+match.start()
                if rva+21+struct.unpack_from('<i',match[0],17)[0]==inner:wrappers.append((rva,match[0]))
    require(len(wrappers)==1 and len(wrappers[0][1])==38,'Unique exact route wrapper/inner absent')
    audit,_=frozen_inputs();mat=materials(audit);exepe,sdkpe=peutil.PE(exe),peutil.PE(sdk)
    exew=[(r,exepe.bytes_at(r,n)) for r,n in EXE_WINDOWS];sdkw=[(r,sdkpe.bytes_at(r,n)) for r,n in SDK_WINDOWS]
    require(exew[0][1]==mat['bridge'] and exew[1][1]==mat['caller'],'Disk EXE fixed windows differ')
    require(all(digest(raw)==row['sha256'] for (_,raw),row in zip(sdkw,mat['sdkWindows'])),'Disk SDK fixed windows differ')
    for n,row in enumerate(audit['sites']):
        image=exepe if n<2 else sdkpe
        require(image.bytes_at(SITE_RVAS[n],WIDTHS[n])==bytes.fromhex(row['originalHex']),'Disk exact route site differs')
    arrays=frozen.source_arrays((ROOT/(frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    return dict(stateRva=verified['stateRva'],tableRva=verified['tableRva'],callbackRva=wrappers[0][0],
        callbackCode=wrappers[0][1],innerRva=inner,arrays=arrays,exeImageSize=exepe.image_size,sdkImageSize=sdkpe.image_size,
        exeWindows=exew,sdkWindows=sdkw,siteOriginals=[bytes.fromhex(r['originalHex']) for r in audit['sites']])

def collect(read,modules,contract,original_rvas):
    """Exactly one ordered prefix of 25/9262; first DATA rejection is 1/256."""
    rows=[];requested=0
    result=dict(status='partial',reads=rows,targetFunctionsCalled=False,payloadReadByCollector=False,
        referenceReadByCollector=False,completePayloadValidated=False,naturalInitOrRoutePassed=False,d02Passed=False)
    def get(name,address,width):
        nonlocal requested
        require(len(rows)<MAX_READS and 0<width<=MAX_SINGLE and requested+width<=MAX_BYTES,'Collector budget exceeded')
        requested+=width
        try:data,error=read(address,width)
        except OSError:data,error=b'','os-read-failed'
        good=error is None and len(data)==width
        rows.append(dict(name=name,address=address,bytes=width,bytesRead=len(data),error=None if good else 'read-failed'))
        if not good:raise StopCollection('read-failed')
        return data
    try:
        proxy,exe,original=(modules[n]['base'] for n in ('Astrolabe.dll','YuanShen.exe','Astrolabe_orig.dll'))
        before=get('state.before',proxy+contract['stateRva'],256)
        try:state=decode_state(before)
        except ValueError:raise StopCollection('invalid-data')
        result['event']=event_output(state)
        result['metadata']={k:state[k] for k in FIELDS}
        if not (state['phase']==5 and state['installed_mask']==31 and state['event']==2 and state['failure']==state['win32_error']==0):
            result['status']='rejected' if state['phase']==6 and state['event']==3 else 'no-p0' if state['phase']==2 and state['event']==0 else 'partial'
            raise StopCollection('first-data-not-qualified-terminal')
        require('AccountPlatNative.dll' in modules,'Complete state without SDK module')
        sdk=modules['AccountPlatNative.dll']['base']
        if not (state['exe_base']==exe and state['sdk_base']==sdk and state['exe_size']==contract['exeImageSize'] and state['sdk_size']==contract['sdkImageSize']):
            raise StopCollection('state-module-identity-differs')
        table=get('proxy.forward-targets',proxy+contract['tableRva'],376)
        if table!=struct.pack('<47Q',*(original+r for r in original_rvas)):raise StopCollection('forward-targets-differ')
        targets=[None]*5;patches=[None]*5
        def bind_window(start,actual,expected,base,indices):
            rebuilt=bytearray(expected)
            for site in indices:
                offset=SITE_RVAS[site]-start
                if 0<=offset<len(rebuilt):
                    width=WIDTHS[site]
                    target,kind=frozen.patch_target(actual[offset:offset+width],expected[offset:offset+width],base+SITE_RVAS[site])
                    if target!=state['trampolines'][site]:raise StopCollection('entry-data-trampoline-differs')
                    targets[site]=target;patches[site]=kind
                    patch_width=5 if kind=='near' else 14
                    rebuilt[offset:offset+patch_width]=actual[offset:offset+patch_width]
            if actual!=rebuilt:raise StopCollection('full-code-window-differs')
        for rva,expected in contract['exeWindows']:
            actual=get('exe.window.'+hex(rva),exe+rva,len(expected))
            bind_window(rva,actual,expected,exe,range(2))
        cell=get('exe.native-cell',exe+0x57b4d60,8)
        if cell!=struct.pack('<Q',sdk+0x13d5b0):raise StopCollection('native-cell-differs')
        for rva,expected in contract['sdkWindows']:
            chunks=[]
            for offset in range(0,len(expected),MAX_SINGLE):
                chunks.append(get('sdk.window.'+hex(rva+offset),sdk+rva+offset,min(MAX_SINGLE,len(expected)-offset)))
            bind_window(rva,b''.join(chunks),expected,sdk,range(2,5))
        # Derive read addresses from validated entry JMPs, never DATA alone.
        require(all(address is not None for address in targets),'Missing fixed entry JMP')
        wrapper=get('route.wrapper',proxy+contract['callbackRva'],38)
        if wrapper!=contract['callbackCode']:raise StopCollection('wrapper-differs')
        emitted=[]
        for site,address in enumerate(targets):
            actual=get('route.'+SITE_NAMES[site]+'.trampoline',address,384)
            userdata=struct.unpack_from('<Q',actual,141)[0]
            if not userdata:raise StopCollection('null-userdata')
            base=exe if site<2 else sdk
            expected,length,mover=trampoline(contract['arrays'],site,contract['siteOriginals'][site],base+SITE_RVAS[site],address,proxy+contract['callbackRva'],userdata)
            if actual!=expected:raise StopCollection('full-trampoline-or-zero-tail-differs')
            emitted.append(dict(site=SITE_NAMES[site],patch=patches[site],mover=mover,bytes=length,zeroTailBytes=384-length))
        after=get('state.after',proxy+contract['stateRva'],256)
        if after!=before:raise StopCollection('state-changed')
        result.update(status='installed-route-code-bound',stableStateEqual=True,fullCodeAndTrampolinesEqual=True,emitters=emitted)
    except StopCollection as error:result['reason']=str(error)
    except (ValueError,KeyError,IndexError,TypeError,struct.error):result.update(status='partial',reason='fixed-structure-or-code-rejected')
    finally:result['budget']=dict(reads=len(rows),bytes=requested,maxReads=MAX_READS,maxBytes=MAX_BYTES,maxSingle=MAX_SINGLE)
    return result

class StopCollection(Exception):pass

def replay(sidecar,modules,contract,original_rvas):
    rows=iter(sidecar['reads']);used=0
    def read(address,width):
        nonlocal used
        row=next(rows);used+=1
        require(row['ordinal']==used and row['address']==address and row['requested']==width,'Same-read sidecar order differs')
        raw=bytes.fromhex(row['bytesHex']);require(len(raw)==row['bytesRead'],'Sidecar byte count differs')
        return raw,row['error']
    result=collect(read,modules,contract,original_rvas)
    require(used==len(sidecar['reads']),'Unused sidecar reads')
    return result

def verify_owned_emitter(row):
    require(set(row)=={'site','placement','entry','moduleBase','callback','userdata','trampoline','patchKind','moverKind',
        'originalHex','patchedHex','trampolineHex','dropRestored','executed','baselineEqual'},'Owned emitter fields differ')
    n=row['site'];require(type(n) is int and 0<=n<5,'Owned site differs')
    original=bytes.fromhex(row['originalHex']);patched=bytes.fromhex(row['patchedHex'])
    audit,_=frozen_inputs();require(original==bytes.fromhex(audit['sites'][n]['originalHex']),'Owned original bytes differ')
    require(row['entry']==row['moduleBase']+SITE_RVAS[n],'Owned module-relative entry differs')
    target,patch=frozen.patch_target(patched,original,row['entry'])
    require(target==row['trampoline'] and patch==row['patchKind'],'Owned actual entry JMP differs')
    arrays=frozen.source_arrays((ROOT/(frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    expected,length,mover=trampoline(arrays,n,original,row['entry'],target,row['callback'],row['userdata'])
    require(bytes.fromhex(row['trampolineHex'])==expected and mover==row['moverKind']==row['placement'],'Owned actual full mover/zero-tail differs')
    require(row['dropRestored'] is True and row['executed'] is True and row['baselineEqual'] is True,'Owned execution/drop evidence incomplete')
    return dict(site=n,placement=mover,bytes=length,zeroTailBytes=384-length)

def check_owned(completed):
    try:
        payload=json.loads(completed.stdout);rows=payload.pop('emitters')
        require(completed.returncode==0 and not completed.stderr,'Owned process failed')
        require(payload==expected_owned(),'Owned assertion summary differs')
        require(len(rows)==10 and {(r['site'],r['placement']) for r in rows}=={(n,p) for n in range(5) for p in ('near','far')},'Owned near/far site coverage incomplete')
        verified=[verify_owned_emitter(row) for row in rows]
        return dict(passed=True,status='owned-passed',publicResult=payload,ownedEmitters=rows,verifiedEmitters=verified)
    except (ValueError,UnicodeError,KeyError,TypeError,IndexError,AttributeError,struct.error):
        return dict(passed=False,status='owned-failed',publicResult=None)

def expected_owned():
    return dict(schema=3,scope='owned synthetic five-point route and mutex-held map only',semanticCases=list(SEMANTIC_CASES),
        gpr16=True,xmm16=True,flags=True,stack128=True,lastError=True,returnHigh64=True,dropRestored=True,
        allFiveNearFar=True,continuousBridgeTwoThreeArguments=True,actualOtherOsThreadZeroReads=True,
        actualM0FrameChain=True,unlockOnceBeforeCleanup=True,mapBoundaryCases=True,mapEveryOrdinalFailure=True,
        parameterCapReads=16,parameterCapBytes=37,mapCapReads=69,mapCapBytes=4440,bindingReads=15,bindingBytes=6408,
        installFailureSites=5,firstErrorRetained=True,repeat1000ZeroReads=True,writerClosedAtP7=True,
        fullTargetWriterExecuted=False,exceptionUnwindVerified=False,clientStarted=False,sdkLoaded=False,
        naturalComboRouteUseObserved=False,naturalInitOrRoutePassed=False,d02Passed=False)

def run_owned_process(fixture,output):
    """Preserve every actual attempt before interpreting its assertion summary."""
    output.mkdir()
    argv=[str(fixture)]
    (output/'argv.private.json').write_bytes(json_bytes(dict(argv=argv,cwd=str(output),fixture=source_id(fixture))))
    timed_out=False;pid=None;launch_failed=False
    try:
        process=subprocess.Popen(argv,cwd=output,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        pid=process.pid
        try:stdout,stderr=process.communicate(timeout=60)
        except subprocess.TimeoutExpired:
            timed_out=True
            # This is exclusively the fresh owned child created directly above.
            process.kill();stdout,stderr=process.communicate()
        code=process.returncode
    except OSError:
        launch_failed=True;stdout=b'';stderr=b'';code=None
    (output/'stdout.private.bin').write_bytes(stdout)
    (output/'stderr.private.bin').write_bytes(stderr)
    metadata=dict(argv=argv,pid=pid,exitCode=code,timedOut=timed_out,launchFailed=launch_failed,stdout=dict(bytes=len(stdout),sha256=digest(stdout)),
        stderr=dict(bytes=len(stderr),sha256=digest(stderr)),fixture=source_id(fixture))
    (output/'process.private.json').write_bytes(json_bytes(metadata))
    row=dict(passed=False,status='owned-timeout' if timed_out else 'owned-launch-failed',publicResult=None) if timed_out or launch_failed else check_owned(subprocess.CompletedProcess(argv,code,stdout,stderr))
    row['rawProcess']=dict(directory=output.relative_to(ROOT).as_posix(),**metadata)
    return row

def verify_inputs(client=None,prepare=False):
    audit,pins=frozen_inputs();mat=materials(audit);binding=binding_source(audit,mat)
    if prepare:write_new_or_equal(RAW/'binding-route.private.rs',binding)
    else:require((RAW/'binding-route.private.rs').read_bytes()==binding,'Route private binding differs')
    fixed=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())['files']
    if client is not None:
        for name,row in fixed.items():
            raw=(client/row['path']).read_bytes()
            require(len(raw)==row['size'] and digest(raw)==row['sha256'],'Fixed stock differs: '+name)
    return dict(frozenSourcePins=pins,sources=source_epoch(),bindingSource=source_id(RAW/'binding-route.private.rs'),
        buildInputs={name:source_id(ROOT/name) for name in ('patch/Cargo.toml','patch/Cargo.lock')},
        definitionSHA256=digest(def_text().encode('ascii')),fixedInputs=fixed,routeSites=audit['sites'],
        sourceOnlyNoGamePathRead=client is None,sourceGateExecutedRust=False,newOriginalPEWindows=0,
        parameterCap=dict(reads=16,bytes=37),mapCap=dict(reads=69,bytes=4440,maxSingle=256),bindingCap=dict(reads=15,bytes=6408,maxSingle=512),
        collectorCap=dict(reads=25,bytes=9262,maxSingle=512),priorSearchStatus='bounded-partial-wall-limit-exceeded')

def acceptance_record(proof_path,production):
    """Build a reviewable record; only main writes it after independent review.
    This does not launch anything or claim its own independent-third approval.
    """
    proof_path=confined(proof_path);production=confined(production)
    proof=json.loads(proof_path.read_bytes());unused,pins=frozen_inputs()
    require(proof['sources']==source_epoch() and proof['frozenSourcePins']==pins,'Cannot accept a different source epoch')
    require(proof['definitionSHA256']==digest(def_text().encode('ascii')) and proof['owned']['passed'] is True and
        len(proof['owned']['runs'])==2,'Complete current DEF/two-fresh proof required')
    require(all(row['passed'] is True and row['publicResult']==expected_owned() and
        type(row['rawProcess']['pid']) is int and row['rawProcess']['pid']>0 and row['rawProcess']['exitCode']==0 and
        row['rawProcess']['timedOut'] is False and row['rawProcess']['launchFailed'] is False for row in proof['owned']['runs']),
        'Fresh process evidence is incomplete')
    require(proof['artifact']==require_artifact(production.read_bytes()),'Cannot accept a different production artifact')
    require(proof['buildInputs']=={name:source_id(ROOT/name) for name in ('patch/Cargo.toml','patch/Cargo.lock')},'Build inputs changed')
    fixture=confined(ROOT/proof['owned']['fixture']['path']);require(source_id(fixture)==proof['owned']['fixture'],'Fixture changed')
    return dict(schema=3,proof=source_id(proof_path),production=source_id(production),fixture=source_id(fixture),
        manifest=source_id(ROOT/'patch/Cargo.toml'),sourceClosure=proof['sources'])

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path);parser.add_argument('--output-directory',required=True,type=Path)
    parser.add_argument('--prepare-build-inputs',action='store_true');parser.add_argument('--production',type=Path)
    parser.add_argument('--fixture',type=Path);parser.add_argument('--run-owned',action='store_true')
    args=parser.parse_args(argv);output=confined(args.output_directory)
    require(not output.exists(),'Preserve prior evidence; choose fresh output')
    require(not args.run_owned or args.production and args.fixture,'Owned run needs both artifacts')
    require(not args.production or args.client is not None,'Artifact gate needs freshly verified client path')
    proof=verify_inputs(args.client,args.prepare_build_inputs);output.mkdir(parents=True)
    (output/'config-route-proxy.def').write_bytes(def_text().encode('ascii'))
    proof['artifact']=require_artifact(confined(args.production).read_bytes()) if args.production else None
    proof['owned']=None
    if args.production:
        artifact_contract(confined(args.production).read_bytes(),(args.client/'YuanShen.exe').read_bytes(),
            (args.client/'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes())
    if args.run_owned:
        fixture=confined(args.fixture);require(fixture.suffix.lower()=='.exe','Owned artifact must be EXE')
        runs=[]
        for index in range(2):
            row=run_owned_process(fixture,output/('run-'+str(index+1)))
            runs.append(row)
            if not row['passed']:break
        proof['owned']=dict(passed=len(runs)==2 and all(r['passed'] for r in runs),runs=runs,fixture=source_id(fixture))
    proof.update(schema=3,task='R2-CONFIG-ROUTE-WRITE',clientStarted=False,d02Passed=False)
    (output/'proof.private.json').write_bytes(json_bytes(proof))
    print(json.dumps(dict(sourceVerified=True,sourceCount=len(proof['sources']),artifactVerified=proof['artifact'] is not None,
        ownedPassed=proof['owned']['passed'] if proof['owned'] else None,clientStarted=False,d02Passed=False)))
    if proof['owned'] is not None and not proof['owned']['passed']:raise SystemExit(1)

if __name__=='__main__':main()
