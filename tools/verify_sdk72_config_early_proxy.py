"""Exact offline source/artifact audit and pure read plan. No client lifecycle API."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys

from . import verify_sdk72_config_proxy as frozen_config
from . import observe_client72_key_loader as frozen
from . import audit_sdk72_clean_loader as peutil

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'local/migration72/d02-resume-20261109/r2-early-bridge'
STATE = 'Sdk72ConfigEarlyProxyState'
EXTRAS = [(STATE,48),('sdk72_config_early_inner',49),('sdk72_config_inner',50),('sdk72_key_input_inner',51)]
ENTRY_RVA, CALLER_RVA = 0x6d80910, 0x198035f6
MOVED = bytes.fromhex('5556574883ec70488d6c247048c745f0feffffff')
EXE_WINDOWS = ((0x6d80910,359),(0x6e363b0,139),(0x19807960,64))
CALLER_WINDOW = (0x198035e8,46)
NEW_WINDOWS = ((0x534480,0x534522,'2050a5e1bc7d2cd740bc79304abcd34f3f4ca65c454e5bd495ffe62a394086cc'),
               (0x507600,0x507748,'b6a18926616c8714228a0d92937933d8fed66a53947d6fafa516c7c6a599de87'))
PINS = {
    'tools/verify_sdk72_config_proxy.py':'8717a4f98f6d9d2c1c70016e966e3765ad526b0ce2aae96d17a53cc590d81457',
    'patch/examples/sdk72_config_proxy/fixture.S':'f0b7a4769cccaa5fa0d7f2d36999a83339a670d6b3b96d43482cfa1c9a877d63',
    'local/migration72/d02-resume-20261109/r2-early-bridge/capture_exact_helper.private.py':'6335e2aa3dea1b0a172f35189cae468173af393e6c2018d39b12d472a746f383',
    'tools/extract_client72.py':'7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'tools/extract_client72_signatures.py':'ee92527f3607be774c28be694d6f57e9eed3aa40068b7b584979e27b6470c349',
    'local/migration72/evidence/client72-structures.json':'fd82e0a55baa69301e87c422465e3797b0f1279778e6b82d938c28446f1b56c5',
    'local/migration72/python/capstone/lib/capstone.dll':'76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3',
    'patch/examples/sdk72_config_proxy.rs':'11536fbd7edef62c0c27b70d1d5adede14c2872948b98e2e0c6006110ff2646b',
    'patch/examples/sdk72_config_proxy/observer.rs':'c93e4da7dd167fca267156e7ee20b7bcb6445c89ed1ff36475552949989c7b75',
}
PRIOR_PROOFS = {
    'r2-config-trigger/main-third.private.json':'f99de6aa2b80367507cc4e17d7bf6b1fdfec44851c436a3c52f96d4e31ea7eb9',
    'r2-configsync/native-proof-main-third.private.json':'3ecd3a8b26be29e989a7be65b170adee9a1f77346d1a4c04e9f00e8b4948680f',
    'r2-config-observer/live1.private.json':'b036ba21735b780a342083938b83b6bd558d895834e288c0421b26bd37d51df3',
    'r1-runtime-inputs/live/runtime-live1.private.json':'04cbe1ffd685244d4599a384186297fa4fdf19e50c2d7c6864f6b9cb4a3b48ac',
}
CASES = ['exact','env-double','key-mismatch','url-mismatch','unknown-env','invalid-json','invalid-utf16',
         'wrong-shape','nested-shape','over-limit','nul-boundary','utf8-over-limit','unknown-caller-no-name-read',
         'approx-name-no-payload-read','case-name-no-payload-read','unreadable-payload','unreadable-name','unicode-extra-field']
EXPECTED_OWNED = dict(schema=1,scope='owned EXE managed ConfigSync input only',cases=CASES,
    originalCallsPerInvocation=1,returnHigh64=True,gpr16=True,xmm16=True,flags=True,entryStack128=True,
    entryAndReturnLastError=True,dropRestored=True,actualInstallErrorNoWrite=True,failureStateNotInstalled=True,
    sourceObjectsUnchanged=True,referenceRejections=3,allCnEnvClasses=True,exactUnitReadBudget=True,
    unknownCallerZeroNameOrPayloadReads=True,wrongNameZeroPayloadReads=True,terminalEventZeroFurtherReads=True,
    sdkLoaded=False,clientStarted=False,nativeMarshalExecuted=False,naturalEventObserved=False,d02Passed=False)
FIELDS = ('schema','bytes','phase','failure','win32_error','installed','exe_base','exe_size','original_bound',
          'self_held','exe_held','any_entry_seen','expected_caller_seen','event','env','app_key_matches','combo_url_matches')
EVENTS = {0:'no-exact-caller-attempt',1:'copying',2:'managed-input-classified',3:'name-read-failed',4:'payload-read-failed',
          5:'over-limit',6:'invalid-json',7:'unsupported-env',8:'unsupported-shape',9:'non-target-name',10:'invalid-utf16'}
MAX_READS, MAX_BYTES, MAX_SINGLE = 9,1450,512

def require(value,message):
    if not value:raise ValueError(message)

def digest(raw):return hashlib.sha256(raw).hexdigest()
def json_bytes(value):return (json.dumps(value,indent=2)+'\n').encode()

def confined(path):
    path=Path(path).resolve()
    require(path.is_relative_to(RAW.resolve()),'Output/artifact is outside this experiment')
    return path

def write_new_or_equal(path,raw):
    if path.exists():require(path.read_bytes()==raw,'Existing private build input differs')
    else:
        path.parent.mkdir(parents=True,exist_ok=True)
        with path.open('xb') as stream:stream.write(raw)

def source_id(path):
    raw=path.read_bytes()
    return dict(path=path.relative_to(ROOT).as_posix(),bytes=len(raw),sha256=digest(raw))

def source_epoch():
    files=[ROOT/'patch/examples/sdk72_config_early_proxy.rs',
        *sorted((ROOT/'patch/examples/sdk72_config_early_proxy').glob('*.rs')),
        Path(__file__),ROOT/'tools/test_verify_sdk72_config_early_proxy.py']
    return {p.relative_to(ROOT).as_posix():source_id(p) for p in files}

def window(pe,rva,width):return dict(rva=rva,bytes=width,sha256=digest(pe.bytes_at(rva,width)))

def binding_source(exe):
    rows=['use super::loader::Window;',f'pub const EXE_SIZE: u32 = {exe.image_size};',
        'pub const EXE_SHA: &str = "8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5";',
        "pub const EXE_WINDOWS: [Window<'static>;3] = ["]
    for rva,width in EXE_WINDOWS:
        w=window(exe,rva,width);rows.append('Window { rva:%d, bytes:%d, sha:"%s" },'%(rva,width,w['sha256']))
    rows+= ['];',"pub const CALLER_WINDOW: Window<'static> = Window { rva:%d, bytes:%d, sha:\"%s\" };"%
        (*CALLER_WINDOW,window(exe,*CALLER_WINDOW)['sha256']),
        'pub const MOVED: [u8;20] = ['+','.join(str(x) for x in MOVED)+'];']
    return ('\n'.join(rows)+'\n').encode()

def static_contract(exe,metadata,startup):
    sys.path.insert(0,str(ROOT/'local/migration72/python'))
    import capstone
    from .extract_client72 import PE as DeclPE,HEADER_VA,BODY,MASK32,number,method_flags_slot,TypeResolver,CODE_REGISTRATION,Names
    from .extract_client72_signatures import parameter_start,parameter_words
    checks=[]
    def passed(label,value):require(value,label);checks.append(label)
    b=exe.data;decl=DeclPE(b);header=decl.offset(HEADER_VA)
    optional=struct.unpack_from('<I',b,60)[0]+24
    rv,n=struct.unpack_from('<II',b,optional+136)
    pdata={a:z for a,z,u in struct.iter_unpack('<III',exe.bytes_at(rv,n))}
    cs=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64)
    reused=[];by_rva={}
    for path in ('explore-windows.private.json','sync-windows.private.json'):
        records=json.loads((RAW.parent/'r2-config-select'/path).read_bytes())
        for w in records:
            a,z=(int(x,16) for x in w['bounds'])
            if a not in (ENTRY_RVA,0x6e363b0,0x198035c0,0x19806d70):continue
            code=exe.bytes_at(a,z-a)
            actual=[[i.address,i.size,i.mnemonic,i.op_str] for i in cs.disasm(code,exe.base+a)]
            passed('reused-full-window-'+hex(a),pdata[a]==z and digest(code)==w['sha256'] and actual==w['instructions'])
            by_rva[a]={i[0]-exe.base:i[2:] for i in actual}
            reused.append(dict(rva=a,endRVAExclusive=z,bytes=z-a,sha256=w['sha256']))
    new=[]
    for a,z,expected in NEW_WINDOWS:
        code=exe.bytes_at(a,z-a);stem='window-'+hex(a)
        saved=json.loads((RAW/(stem+'.binding.private.json')).read_bytes())
        actual=[dict(rva=i.address-exe.base,bytes=i.size,hex=i.bytes.hex(),mnemonic=i.mnemonic,operands=i.op_str)
                for i in cs.disasm(code,exe.base+a)]
        passed('new-complete-window-'+hex(a),pdata[a]==z and digest(code)==expected==saved['sha256'] and
            code==(RAW/(stem+'.private.bin')).read_bytes() and sum(i['bytes'] for i in actual)==z-a and
            actual==json.loads((RAW/(stem+'.instructions.private.json')).read_bytes()))
        by_rva[a]={i['rva']:[i['mnemonic'],i['operands']] for i in actual}
        new.append(dict(rva=a,endRVAExclusive=z,bytes=z-a,sha256=expected))
    wrapper,impl,producer,parent=(by_rva[r] for r in (ENTRY_RVA,0x6e363b0,0x19806d70,0x198035c0))
    passed('exact-moved20-no-pc-relative',exe.bytes_at(ENTRY_RVA,20)==MOVED and
        [i[2:] for i in [[x.address,x.size,x.mnemonic,x.op_str] for x in cs.disasm(MOVED,exe.base+ENTRY_RVA)]]==
        [['push','rbp'],['push','rsi'],['push','rdi'],['sub','rsp, 0x70'],['lea','rbp, [rsp + 0x70]'],['mov','qword ptr [rbp - 0x10], 0xfffffffffffffffe']])
    passed('entry-managed-registers',wrapper[0x6d80924]==['mov','rsi, r8'] and wrapper[0x6d80927]==['mov','rdi, rdx'])
    passed('both-original-marshal-calls',wrapper[0x6d809ad]==['mov','rcx, rdi'] and wrapper[0x6d809b0]==['call','0x140534480'] and
        wrapper[0x6d809b8]==['mov','rcx, rsi'] and wrapper[0x6d809bb]==['call','0x140534480'])
    passed('native-argument-lifetime',wrapper[0x6d809c3]==['mov','rcx, rdi'] and wrapper[0x6d809c6]==['mov','rdx, rax'] and
        wrapper[0x6d809c9][0]=='call' and wrapper[0x6d809d7][0]=='call' and wrapper[0x6d809e5][0]=='call')
    passed('implementation-preserves-name-payload-tail',impl[0x6e363e2]==['mov','rdx, rdi'] and impl[0x6e363e5]==['mov','r8, rsi'] and
        impl[0x6e363ee]==['jmp','0x146d80910'])
    passed('producer-restores-frame-and-tail',producer[0x1980797a]==['mov','r8, rsi'] and producer[0x19807980]==['add','rsp, 0x38'] and
        producer[0x19807987]==['pop','r14'] and producer[0x19807989]==['jmp','r10'])
    passed('actual-tail-chain-return',parent[0x198035f1]==['call','0x159806d70'] and parent[CALLER_RVA]==['call','0x159807e30'])
    marshal,utf16=by_rva[0x534480],by_rva[0x507600]
    passed('original-marshal-plus14-not-length',marshal[0x534499]==['mov','rdx, rcx'] and marshal[0x53449c]==['add','rdx, 0x14'] and
        marshal[0x5344a7]==['call','0x140507600'] and not any(' + 0x10]' in op for mn,op in marshal.values()))
    passed('original-helper-u16-nul-scan',utf16[0x507615]==['cmp','word ptr [rdx], 0'] and
        utf16[0x507634]==['cmp','word ptr [rsi + rax*2 + 2], 0'] and utf16[0x50763e]==['jne','0x140507630'])
    passed('original-helper-codepoint-path-not-modeled-success',utf16[0x5076f0]==['movzx','edx, word ptr [rsi]'] and
        utf16[0x5076fa]==['cmp','eax, 0xd800'] and utf16[0x507727]==['call','0x140510cc0'])
    records=json.loads((ROOT/'local/migration72/evidence/client72-structures.json').read_bytes())['types']
    methods={m['index']:m for t in records for m in t['methods']}
    methodbase=BODY+(number(b,header+0xb0)^0x51324a1f)
    parameterbase=BODY+((number(b,header+0x174)+0xae6bcd68)&MASK32)
    names=Names(metadata,BODY+(number(b,header+0x70)^0x18202ccc))
    resolver=TypeResolver(decl,startup,records,header)
    pointers=decl.offset(number(b,decl.offset(CODE_REGISTRATION,0x78)+0x70,'Q'))
    signatures=[]
    for index,rva in ((70148,ENTRY_RVA),(70136,0x6e363b0)):
        at=methodbase+index*26;first=parameter_start(index,number(metadata,at+4));params=[]
        for j in range(methods[index]['parameterCount']):
            pi=first+j;ni,ti=parameter_words(pi,number(metadata,parameterbase+pi*8),number(metadata,parameterbase+pi*8+4))
            resolved=resolver.resolve_index(ti)
            passed('raw-string-parameter-'+str(pi),resolved['kind']=='string')
            params.append(dict(index=pi,name=names.decode(ni),typeIndex=ti,type=resolved))
        flags,slot=method_flags_slot(index,number(metadata,at+0x10,'H'),number(metadata,at+0x12,'H'))
        passed('instance-method-address-'+str(index),not flags&0x10 and len(params)==2 and
            number(b,pointers+index*8,'Q')==exe.base+rva==int(methods[index]['registeredAddress'],16))
        signatures.append(dict(method=index,rva=rva,flags=flags,slot=slot,parameters=params))
    return dict(checks=checks,reusedWindows=reused,newWindows=new,newWindowCount=2,newCodeBytes=490,
        rawTypedParameters=signatures,managedLengthRead=False,charsOffset=20,terminatorUnitBytes=2,
        exactCallerReturnRVA=CALLER_RVA,originalMarshalExecuted=False,managedToNativeEncodingEquivalenceClaimed=False)

def verify_inputs(client,prepare=False):
    pins={**frozen_config.frozen_sources(),**PINS}
    for name,expected in pins.items():require(digest((ROOT/name).read_bytes())==expected,'Frozen dependency differs: '+name)
    prior={}
    for name,expected in PRIOR_PROOFS.items():
        p=RAW.parent/name;require(digest(p.read_bytes())==expected,'Prior evidence differs: '+name)
        prior[name]=dict(sha256=expected)
    profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes());fixed={};contents={}
    for name,row in profile['files'].items():
        raw=(client/row['path']).read_bytes()
        require(len(raw)==row['size'] and digest(raw)==row['sha256'],'Fixed stock input differs: '+name)
        fixed[name]=dict(path=row['path'],bytes=len(raw),sha256=row['sha256']);contents[name]=raw
    exe=peutil.PE(contents['binary'])
    contract=static_contract(exe,contents['metadata'],contents['startup'])
    refs,reference_proof=frozen_config.private_material(client)
    generated=binding_source(exe)
    if prepare:
        write_new_or_equal(RAW/'references.private.json',refs)
        write_new_or_equal(RAW/'binding.private.rs',generated)
    else:
        require((RAW/'references.private.json').read_bytes()==refs,'Private reference source differs')
        require((RAW/'binding.private.rs').read_bytes()==generated,'Private code binding differs')
    return dict(frozenSourcePins=pins,priorProofs=prior,fixedInputs=fixed,referenceProof=reference_proof,
        staticContract=contract,exeWindows=[window(exe,*s) for s in EXE_WINDOWS],callerWindow=window(exe,*CALLER_WINDOW),
        bindingSource=source_id(RAW/'binding.private.rs'),sources=source_epoch())

def def_text():
    return '\n'.join(['LIBRARY sdk72_config_early_proxy','EXPORTS',
        *[f'  {name} @{index+1}' for index,name in enumerate(peutil.source_names())],
        *[f'  {name} @{ordinal}'+(' DATA' if name==STATE else '') for name,ordinal in EXTRAS]])+'\n'

def require_artifact(raw):
    pe=peutil.PE(raw);names=peutil.source_names();exports=pe.exports()
    peutil.require_exports(exports,names,EXTRAS)
    state=next(row for row in exports if row['name']==STATE)
    require(pe.bytes_at(state['rva'],72)==struct.pack('<II',1,72)+bytes(64),'Early state initial bytes differ')
    section=next(s for s in pe.sections if s[2]<=state['rva']<s[2]+s[3])
    require(section[-1]&0x80000000 and not section[-1]&0x20000000,'Early state must be writable non-code DATA')
    slots=[];resolvers=[]
    for index,name in enumerate(names):
        row=next(r for r in exports if r['name']==name);rva=row['rva'];code=pe.bytes_at(rva,24)
        require(code[:2]==b'\xff\x25' and code[6:9]==bytes((0x6a,index,0xe8)) and
            code[13:20]==bytes.fromhex('488d642408ff25'),'Frozen forwarding thunk differs')
        slot=rva+6+struct.unpack_from('<i',code,2)[0]
        require(rva+24+struct.unpack_from('<i',code,20)[0]==slot and
            struct.unpack('<Q',pe.bytes_at(slot,8))[0]==pe.base+rva+6,'Frozen cold slot differs')
        slots.append(slot);resolvers.append(rva+13+struct.unpack_from('<i',code,9)[0])
    require(slots==[slots[0]+8*n for n in range(47)] and len(set(resolvers))==1,'Frozen forwarding table differs')
    for name,_ in EXTRAS[1:]:
        rva=next(r['rva'] for r in exports if r['name']==name)
        require(next(s for s in pe.sections if s[2]<=rva<s[2]+s[3])[-1]&0x20000000,'Explicit dormant/active inner must be code')
    return dict(bytes=len(raw),sha256=digest(raw),exports=exports,stateRva=state['rva'],tableRva=slots[0],stateBytes=72,
        extraExportPurposes={STATE:'fixed early bridge metadata/facts/classification DATA',
            'sdk72_config_early_inner':'unique installed EXE callback inner',
            'sdk72_config_inner':'dormant frozen References/classify source dependency; never installed',
            'sdk72_key_input_inner':'dormant frozen loader type dependency; never activated'})

def trampoline(arrays,entry,callback,userdata):
    prolog,epilog1,epilog2=arrays
    prefix=prolog+b'\x48\xba'+struct.pack('<Q',userdata)+bytes.fromhex('4889e14883ec1048b8')
    prefix+=struct.pack('<Q',callback)+bytes.fromhex('ffd04883c410')+epilog1+epilog2
    require(len(prefix)==250,'Frozen ilhook JmpBack layout differs')
    return prefix+MOVED+bytes.fromhex('ff2500000000')+struct.pack('<Q',entry+20)

def verify_owned_emitter(row):
    require(set(row)=={'entry','callback','userdata','trampoline','patchKind','originalHex','patchedHex','trampolineHex',
        'trampolineBytes','dropRestored','calleeExecuted','callbackExecuted'},'Owned emitter fields differ')
    require(all(type(row[k]) is int and 0<row[k]<2**64 for k in ('entry','callback','userdata','trampoline')),'Owned metadata differs')
    require(row['dropRestored'] is True and row['calleeExecuted'] is False and row['callbackExecuted'] is False,'Owned execution boundary differs')
    original=MOVED+b'\x90'*76
    require(bytes.fromhex(row['originalHex'])==original,'Owned EXE exact prefix differs')
    target,kind=frozen.patch_target(bytes.fromhex(row['patchedHex']),original,row['entry'])
    require(target==row['trampoline'] and kind==row['patchKind'],'Owned actual patch differs')
    arrays=frozen.source_arrays((ROOT/(frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    require(row['trampolineBytes']==284 and bytes.fromhex(row['trampolineHex'])==trampoline(arrays,row['entry'],row['callback'],row['userdata']),
        'Owned actual complete284B/entry+20 differs')
    return True

def check_owned(completed):
    try:
        payload=json.loads(completed.stdout);emitted=payload.pop('ownedExePrefix')
        verified=verify_owned_emitter(emitted)
    except (ValueError,UnicodeError,KeyError,TypeError,AttributeError,struct.error):payload=None;emitted=None;verified=False
    passed=completed.returncode==0 and not completed.stderr and payload==EXPECTED_OWNED and verified
    return dict(passed=passed,status='owned-passed' if passed else 'owned-failed',publicResult=payload if passed else None,
        ownedEmitterVerified=bool(verified) if passed else False,ownedEmitter=emitted if passed else None)

def event_output(state):
    event=state['event'];require(event in EVENTS,'Unknown early event')
    facts={k:state[k] for k in ('any_entry_seen','expected_caller_seen')}
    require(all(v in (0,1) for v in facts.values()) and facts['expected_caller_seen']<=facts['any_entry_seen'],'Invalid fact bits')
    require(event==0 or facts['expected_caller_seen']==1,'Event without exact caller')
    result=dict(status=EVENTS[event],anyEntrySeen=bool(facts['any_entry_seen']),expectedCallerSeen=bool(facts['expected_caller_seen']))
    if event==2:
        require(state['env'] in frozen_config.CN_ENVS and state['app_key_matches'] in (0,1) and state['combo_url_matches'] in (0,1),'Invalid classification')
        result.update(env='cn-env-'+str(state['env']),appKeyMatches=bool(state['app_key_matches']),comboUrlMatches=bool(state['combo_url_matches']))
    else:require(event==1 or state['env']==state['app_key_matches']==state['combo_url_matches']==0,'Failure carries classification data')
    return result

def artifact_contract(proxy,exe):
    verified=require_artifact(proxy);pe=peutil.PE(proxy);exports={r['name']:r for r in pe.exports()}
    inner=exports['sdk72_config_early_inner']['rva'];candidates=[]
    pattern=re.compile(re.escape(frozen.CALLBACK_PREFIX)+b'.{4}'+re.escape(frozen.CALLBACK_SUFFIX),re.S)
    for section in pe.sections:
        if section[-1]&0x20000000:
            for match in pattern.finditer(pe.take(section[4],section[3])):
                rva=section[2]+match.start()
                if rva+21+struct.unpack_from('<i',match[0],17)[0]==inner:candidates.append((rva,match[0]))
    require(len(candidates)==1,'Unique exact early wrapper/inner absent')
    arrays=frozen.source_arrays((ROOT/(frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    exepe=peutil.PE(exe);require(exepe.bytes_at(ENTRY_RVA,20)==MOVED,'EXE moved code differs')
    return dict(stateRva=verified['stateRva'],tableRva=verified['tableRva'],callbackRva=candidates[0][0],callbackCode=candidates[0][1],
        arrays=arrays,exeImageSize=exepe.image_size,exeWindows=[(r,exepe.bytes_at(r,n)) for r,n in EXE_WINDOWS],
        callerWindow=(CALLER_WINDOW[0],exepe.bytes_at(*CALLER_WINDOW)))

def collect(read,modules,contract,original_rvas):
    """Pure parent-owned collector adapter: 9 reads/1450B, no PID or OS backend."""
    rows=[];requested=0
    def get(name,address,width):
        nonlocal requested
        require(len(rows)<MAX_READS and requested+width<=MAX_BYTES and 0<width<=MAX_SINGLE,'Read budget exceeded')
        requested+=width
        try:data,error=read(address,width)
        except OSError:data,error=b'','os-read-failed'
        good=error is None and len(data)==width
        rows.append(dict(name=name,address=address,bytes=width,bytesRead=len(data),error=None if good else 'read-failed'))
        return data if good else None
    proxy,exe,original=(modules[n]['base'] for n in ('Astrolabe.dll','YuanShen.exe','Astrolabe_orig.dll'))
    result=dict(status='partial',reads=rows,targetFunctionsCalled=False,payloadReadByCollector=False,referenceReadByCollector=False,
        nativeMarshalExecuted=False,nativeExportObserved=False,writerReturnObserved=False,routeEffectObserved=False,d02Passed=False)
    before=get('state.before',proxy+contract['stateRva'],72)
    if before is None:return result
    state=dict(zip(FIELDS,struct.unpack('<6IQ10I',before)))
    wanted=dict(schema=1,bytes=72,phase=5,failure=0,win32_error=0,installed=1,exe_base=exe,exe_size=contract['exeImageSize'],
        original_bound=1,self_held=1,exe_held=1)
    if not all(state[k]==v for k,v in wanted.items()):result['reason']='state-not-installed';return result
    try:result['event']=event_output(state)
    except ValueError:result['reason']='invalid-event';return result
    result['metadata']={k:state[k] for k in FIELDS if k not in ('event','env','app_key_matches','combo_url_matches')}
    table=get('proxy.forward-targets',proxy+contract['tableRva'],376)
    result['forwardTargetsEqual']=table==struct.pack('<47Q',*(original+r for r in original_rvas))
    code=[];entry=None
    for rva,expected in [*contract['exeWindows'],contract['callerWindow']]:
        actual=get('exe.window.'+hex(rva),exe+rva,len(expected));code.append(dict(rva=rva,originalEqual=actual==expected))
        if rva==ENTRY_RVA:entry=actual
    result['code']=code
    if result['forwardTargetsEqual'] and all(r['originalEqual'] for r in code if r['rva']!=ENTRY_RVA) and entry is not None:
        try:
            target,kind=frozen.patch_target(entry,contract['exeWindows'][0][1],exe+ENTRY_RVA)
            actual=get('early.trampoline',target,284)
            userdata=struct.unpack_from('<Q',actual,141)[0] if actual is not None else 0
            expected=trampoline(contract['arrays'],exe+ENTRY_RVA,proxy+contract['callbackRva'],userdata)
            callback=get('early.callback',proxy+contract['callbackRva'],len(contract['callbackCode']))
            equal=userdata!=0 and actual==expected and callback==contract['callbackCode']
            result.update(patchEncoding=kind,fullTrampolineAndCallbackEqual=equal,returnEntryPlus20Verified=equal)
            if equal:result['status']='installed-early-code-bound'
        except ValueError:result['reason']='patch-or-trampoline-differs'
    after=get('state.after',proxy+contract['stateRva'],72)
    result['stableStateEqual']=after==before
    if after!=before or any(r['error'] for r in rows) or state['event']==1:result['status']='partial'
    result['budget']=dict(reads=len(rows),bytes=requested,maxReads=MAX_READS,maxBytes=MAX_BYTES,maxSingle=MAX_SINGLE)
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True);parser.add_argument('--output-directory',type=Path,required=True)
    parser.add_argument('--prepare-build-inputs',action='store_true');parser.add_argument('--production',type=Path)
    parser.add_argument('--fixture',type=Path);parser.add_argument('--run-owned',action='store_true')
    args=parser.parse_args();output=confined(args.output_directory)
    require(not output.exists(),'Preserve earlier evidence; choose a fresh output')
    require(not args.run_owned or args.production and args.fixture,'Owned run requires both artifacts')
    proof=verify_inputs(args.client,args.prepare_build_inputs);output.mkdir(parents=True)
    (output/'config-early-proxy.def').write_bytes(def_text().encode('ascii'))
    proof['artifact']=require_artifact(confined(args.production).read_bytes()) if args.production else None
    proof['owned']=None
    if args.production:artifact_contract(confined(args.production).read_bytes(),(args.client/'YuanShen.exe').read_bytes())
    if args.run_owned:
        fixture=confined(args.fixture);require(fixture.suffix.lower()=='.exe','Owned fixture must be EXE')
        runs=[]
        for _ in range(2):
            try:row=check_owned(subprocess.run([str(fixture)],cwd=output,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60,check=False))
            except subprocess.TimeoutExpired:row=dict(passed=False,status='owned-timeout',publicResult=None)
            runs.append(row)
            if not row['passed']:break
        proof['owned']=dict(passed=len(runs)==2 and all(r['passed'] for r in runs),runs=runs,fixture=source_id(fixture))
    proof.update(schema=1,task='R2-CONFIGSYNC-EARLY-BRIDGE',clientStarted=False,naturalEventObserved=False,d02Passed=False)
    (output/'proof.private.json').write_bytes(json_bytes(proof))
    print(json.dumps(dict(sourceVerified=True,staticChecks=len(proof['staticContract']['checks']),newWindows=2,newCodeBytes=490,
        artifactVerified=proof['artifact'] is not None,ownedPassed=proof['owned']['passed'] if proof['owned'] else None,clientStarted=False)))
    if proof['owned'] is not None and not proof['owned']['passed']:raise SystemExit(1)

if __name__=='__main__':main()
