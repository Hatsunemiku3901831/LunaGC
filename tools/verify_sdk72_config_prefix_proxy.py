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
from . import client72_sdk_early_classification_audit as diagnosis
from . import client72_sdk_scan_limit_followup as follow

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'local/migration72/d02-parallel-20261109/b-sdk-prefix'
SPLIT_RAW = ROOT/'local/migration72/d02-parallel-20261109/b-sdk-split'
LEGACY_RAW = ROOT/'local/migration72/d02-resume-20261109/r2-early-bridge'
STATE = 'Sdk72ConfigPrefixProxyState'
EXTRAS = [(STATE,48),('sdk72_config_prefix_inner',49),('sdk72_config_inner',50),('sdk72_key_input_inner',51)]
ENTRY_RVA, CALLER_RVA = 0x6d80910, 0x198035f6
MOVED = bytes.fromhex('5556574883ec70488d6c247048c745f0feffffff')
EXE_WINDOWS = ((0x6d80910,359),(0x6e363b0,139),(0x19807960,64))
CALLER_WINDOW = (0x198035e8,46)
REUSED_HELPER_WINDOWS = ((0x534480,0x534522,'2050a5e1bc7d2cd740bc79304abcd34f3f4ca65c454e5bd495ffe62a394086cc'),
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
    'tools/client72_sdk_early_classification_audit.py':'1cd5528eddb31e12d176f6f4b031b47ea65c20cb11b9a5fd5a76818bb0acbd02',
    'docs/CLIENT_72_SDK_EARLY_CLASSIFICATION_AUDIT.md':'4e969346b3ae69da8ecf63523beac917a44ca6ee1d8470a14106f07ce259801f',
    'local/migration72/d02-parallel-20261109/b-sdk-analysis/final-first.private.json':'db2bd764c3cdacab42c687412e5a9089e7156dec6c975e0c8e2aadbf51fef525',
}
PRIOR_PROOFS = {
    'r2-config-trigger/main-third.private.json':'f99de6aa2b80367507cc4e17d7bf6b1fdfec44851c436a3c52f96d4e31ea7eb9',
    'r2-configsync/native-proof-main-third.private.json':'3ecd3a8b26be29e989a7be65b170adee9a1f77346d1a4c04e9f00e8b4948680f',
    'r2-config-observer/live1.private.json':'b036ba21735b780a342083938b83b6bd558d895834e288c0421b26bd37d51df3',
    'r1-runtime-inputs/live/runtime-live1.private.json':'04cbe1ffd685244d4599a384186297fa4fdf19e50c2d7c6864f6b9cb4a3b48ac',
}
CASES = ['exact','env-double','key-mismatch','url-mismatch','unknown-env','invalid-json','invalid-utf16',
         'wrong-shape','nested-shape','over-limit','nul-boundary','utf8-over-limit','unknown-caller-no-name-read',
         'approx-name-no-payload-read','case-name-no-payload-read','unreadable-payload','unreadable-name','unicode-extra-field',
         'unit-next-slot-real-nul','unit-identical-prefix-no-nul','utf8-last-allowed-byte','utf8-first-rejected-byte',
         'invalid-surrogate-unit-limit']
PREFIX_CASES = ['language-after-required-fields','language-before-url','cut-inside-url','fake-keys-in-other-string',
    'duplicate-required-key','wrong-env','wrong-url-shape','key-mismatch-stays-false','url-mismatch-stays-false',
    'invalid-escape-in-language','invalid-utf16-in-required-prefix','closed-language-then-other-large-field',
    'unobserved-suffix-is-not-validated','prefix-full-input-language','prefix-env-double','prefix-field-order',
    'prefix-escaped-selected-key','prefix-terminal-backslash','prefix-terminal-unicode-escape',
    'prefix-terminal-high-surrogate','prefix-terminal-low-surrogate','prefix-escaped-quote',
    'prefix-invalid-language-hex','prefix-consumed-utf8-last-byte','prefix-consumed-utf8-first-rejected',
    'prefix-nested-unselected','prefix-duplicate-escaped-key','prefix-env-boolean','prefix-missing-comma',
    'prefix-invalid-required-escape','prefix-same-prefix-no-nul']
CASES += PREFIX_CASES
EXPECTED_OWNED = dict(schema=1,scope='owned EXE managed ConfigSync input only',cases=CASES,
    originalCallsPerInvocation=1,returnHigh64=True,gpr16=True,xmm16=True,flags=True,entryStack128=True,
    entryAndReturnLastError=True,dropRestored=True,actualInstallErrorNoWrite=True,failureStateNotInstalled=True,
    sourceObjectsUnchanged=True,referenceRejections=3,allCnEnvClasses=True,exactUnitReadBudget=True,
    unknownCallerZeroNameOrPayloadReads=True,wrongNameZeroPayloadReads=True,terminalEventZeroFurtherReads=True,
    sdkLoaded=False,clientStarted=False,nativeMarshalExecuted=False,naturalEventObserved=False,d02Passed=False)
FIELDS = ('schema','bytes','phase','failure','win32_error','installed','exe_base','exe_size','original_bound',
          'self_held','exe_held','any_entry_seen','expected_caller_seen','event','env','app_key_matches','combo_url_matches')
EVENTS = {0:'no-exact-caller-attempt',1:'copying',2:'managed-input-classified',3:'name-read-failed',4:'payload-read-failed',
          6:'invalid-json',7:'unsupported-env',8:'unsupported-shape',9:'non-target-name',10:'invalid-utf16',
          11:'payload-unit-scan-limit',12:'payload-utf8-limit',13:'managed-prefix-fields-before-language-scan-limit'}
MAX_READS, MAX_BYTES, MAX_SINGLE = 9,1450,512

def owned_prefix_inputs():
    """Owned text/units only; the independent B oracle supplies no Rust execution."""
    compact=follow.compact
    key,url=follow.KEY,compact(dict(comboLogin=follow.URL))
    long='a'*32768
    def stem(env='0',key=key,url=url,before=''):
        return '{'+before+'"env":'+env+',"app_key":'+compact(key)+',"url":'+compact(url)+',"language":'
    def body(prefix,language=long):return prefix+compact(language)+'}'
    exact=body(stem())
    def tail(value):
        prefix=(stem()+'"').encode('utf-16-le')
        return prefix+b'a\0'*(32768-len(prefix)//2-len(value))+struct.pack('<'+'H'*len(value),*value)
    def utf8_boundary(target):
        base=stem(before='"ignored":"",')
        three,one=divmod(target-len(base.encode()),3)
        prefix=stem(before='"ignored":'+compact('\u0800'*three+'a'*one)+',')
        require(len(prefix.encode())==target and len(prefix.encode('utf-16-le'))//2+1<32768,'Owned prefix boundary differs')
        return body(prefix)
    rows=[
        (PREFIX_CASES[0],exact,13),
        (PREFIX_CASES[1],compact(dict(env=0,app_key=key,language=long,url=url)),11),
        (PREFIX_CASES[2],body(stem(url=long)),11),
        (PREFIX_CASES[3],compact(dict(env=0,gamebiz='"app_key":"x","url":"y"',language=long)),11),
        (PREFIX_CASES[4],exact.replace('{','{"env":1,',1),11),
        (PREFIX_CASES[5],exact.replace('"env":0','"env":2',1),11),
        (PREFIX_CASES[6],exact.replace(compact(url),'{}',1),11),
        (PREFIX_CASES[7],exact.replace(key,'OWNED_DIFFERENT',1),13),
        (PREFIX_CASES[8],exact.replace(follow.URL,'OWNED_DIFFERENT',1),13),
        (PREFIX_CASES[9],exact.replace('"language":"a','"language":"\\q',1),11),
        (PREFIX_CASES[10],exact.replace(key,'\ud800'+key[1:],1),11),
        (PREFIX_CASES[11],stem()+'"","gamebiz":'+compact(long)+'}',11),
        (PREFIX_CASES[12],exact[:32768]+'INVALID_UNOBSERVED_SUFFIX',13),
        (PREFIX_CASES[13],body(stem(),'short'),2),
        (PREFIX_CASES[14],body(stem(env='1.0')),13),
        (PREFIX_CASES[15],body('{"url":'+compact(url)+',"unknown":null,"app_key":'+compact(key)+',"env":0,"language":'),13),
        (PREFIX_CASES[16],exact.replace('"env"','"e\\u006ev"',1),13),
        (PREFIX_CASES[17],tail([0x5c]),13),
        (PREFIX_CASES[18],tail([0x5c,0x75,0x31,0x32]),13),
        (PREFIX_CASES[19],tail([0xd800]),13),
        (PREFIX_CASES[20],tail([0xdc00]),13),
        (PREFIX_CASES[21],exact.replace('"language":"a','"language":"\\"a',1),13),
        (PREFIX_CASES[22],exact.replace('"language":"a','"language":"\\u12x',1),11),
        (PREFIX_CASES[23],utf8_boundary(65535),13),
        (PREFIX_CASES[24],utf8_boundary(65536),11),
        (PREFIX_CASES[25],body(stem(before='"ignored":[],')),11),
        (PREFIX_CASES[26],exact.replace('{','{"e\\u006ev":0,',1),11),
        (PREFIX_CASES[27],exact.replace('"env":0','"env":true',1),11),
        (PREFIX_CASES[28],exact.replace('"env":0,','"env":0 ',1),11),
        (PREFIX_CASES[29],exact.replace(key,'\\q',1),11),
        (PREFIX_CASES[30],exact,13),
    ]
    return [(label,value.encode('utf-16-le','surrogatepass') if isinstance(value,str) else value,event)
        for label,value,event in rows]

def owned_prefix_oracle():
    rows=[]
    for label,raw,event in owned_prefix_inputs():
        if event==2:
            outcome=diagnosis.classify_owned(raw.decode('utf-16-le'))
            require(outcome.event==2,'Owned complete-input classification differs')
            result=dict(event=2,appKeyMatches=outcome.key_match,comboUrlMatches=outcome.url_match)
        else:
            require(len(raw)>=32768*2,'Owned prefix does not reach cap')
            result=follow.observe_owned_prefix(raw[:32768*2])
            require(result['event']==event,'Independent owned prefix oracle differs: '+label)
        rows.append(dict(case=label,observation=result))
    return rows

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
    files=[ROOT/'patch/examples/sdk72_config_prefix_proxy.rs',
        *sorted((ROOT/'patch/examples/sdk72_config_prefix_proxy').glob('*.rs')),
        Path(__file__),ROOT/'tools/test_verify_sdk72_config_prefix_proxy.py']
    return {p.relative_to(ROOT).as_posix():source_id(p) for p in files}

def frozen_sources():
    _, pins = follow.frozen_inputs()
    pins.update(PINS)
    pins.update({
        'tools/client72_sdk_scan_limit_followup.py':'c58be572abaf15cd6b335ece5475a511d32c718e976087dda3087e566188f2a0',
        'tools/test_client72_sdk_scan_limit_followup.py':'84a6c9211bf68acfc84dfa087103105fdeb901040dd3ccab781c80fd921813be',
        'docs/CLIENT_72_SDK_SCAN_LIMIT_FOLLOWUP.md':'46cd974c79dea1713fc834910f4736c54735e2c045782a8d19110ded682224db',
        'local/migration72/d02-parallel-20261109/b-sdk-limit11/main-third.private.json':'70a030a55150b99ba723e7e201d5dbdd4d0a0f330bf83fc62e3ed9ca9f25c0a1',
        'local/migration72/d02-parallel-20261109/b-sdk-limit11/main-review.private.json':'183b030ef3b73ef9663c852df83163836f00c3a659fd5d1941899302ba80770b',
    })
    accepted=json.loads((SPLIT_RAW/'owned-main1/proof.private.json').read_bytes())
    binding=accepted['bindingSource'];pins[binding['path']]=binding['sha256']
    for name,expected in PRIOR_PROOFS.items():
        pins[(LEGACY_RAW.parent/name).relative_to(ROOT).as_posix()]=expected
    for name,expected in pins.items():
        require(digest((ROOT/name).read_bytes())==expected,'Frozen dependency differs: '+name)
    return pins

def prefix_source_contract():
    old=(ROOT/'patch/examples/sdk72_config_early_split_proxy.rs').read_text(encoding='utf8')
    expected=old.replace('sdk72_config_early_split_proxy','sdk72_config_prefix_proxy')
    expected=expected.replace('Sdk72ConfigEarlySplitProxyState','Sdk72ConfigPrefixProxyState')
    expected=expected.replace('d02-parallel-20261109/b-sdk-split/','d02-parallel-20261109/b-sdk-prefix/')
    expected=expected.replace('#[path = "sdk72_config_prefix_proxy/observer.rs"] mod observer;',
        '#[path = "sdk72_config_prefix_proxy/prefix.rs"] mod prefix;\n#[path = "sdk72_config_prefix_proxy/observer.rs"] mod observer;')
    require((ROOT/'patch/examples/sdk72_config_prefix_proxy.rs').read_text(encoding='utf8')==expected,'Prefix loader exceeds minimal transform')
    old=(ROOT/'patch/examples/sdk72_config_early_split_proxy/observer.rs').read_text(encoding='utf8')
    expected=old.replace('use super::{core, frozen_observer, loader};','use super::{core, frozen_observer, loader, prefix};')
    expected=expected.replace('pub const PAYLOAD_UTF8_LIMIT: u32 = 12;','pub const PAYLOAD_UTF8_LIMIT: u32 = 12;\npub const PREFIX_FIELDS: u32 = 13;')
    expected=expected.replace('Ok(text) => frozen_observer::classify(text.as_bytes(), &self.references),',
        'Ok(CopiedPayload::Complete(text)) => frozen_observer::classify(text.as_bytes(), &self.references),\n            Ok(CopiedPayload::UnitLimit(units)) => prefix::observe(&units, &self.references)\n                .unwrap_or_else(|| failure(PAYLOAD_UNIT_LIMIT)),')
    expected=expected.replace('fn copy_payload(address:', 'enum CopiedPayload { Complete(String), UnitLimit(Vec<u16>) }\n\nfn copy_payload(address:')
    expected=expected.replace('-> Result<String, u32> {','-> Result<CopiedPayload, u32> {')
    expected=expected.replace('return Ok(text);','return Ok(CopiedPayload::Complete(text));')
    expected=expected.replace('Err(PAYLOAD_UNIT_LIMIT)','Ok(CopiedPayload::UnitLimit(units))')
    expected=expected.replace('sdk72_config_early_split_callback','sdk72_config_prefix_callback').replace('sdk72_config_early_split_inner','sdk72_config_prefix_inner')
    require((ROOT/'patch/examples/sdk72_config_prefix_proxy/observer.rs').read_text(encoding='utf8')==expected,'Prefix observer exceeds minimal transform')
    parser=(ROOT/'patch/examples/sdk72_config_prefix_proxy/prefix.rs').read_text(encoding='utf8')
    require(all(token not in parser for token in ('ReadProcessMemory','read_unit','Registers','unsafe','from_utf16_lossy','units.clone')),'Prefix parser adds read or repair backend')
    fixture=(ROOT/'patch/examples/sdk72_config_prefix_proxy/fixture.rs').read_text(encoding='utf8')
    labels=re.findall(r'^\s*\("([a-z0-9-]+)",',fixture,re.M)
    require(len(labels)==len(CASES) and set(labels)==set(CASES),'Prefix fixture labels differ')
    require(len(source_epoch())==6,'Prefix source closure must contain four Rust and two audit/test sources')
    return dict(event=13,onlyAfterUnitLimit=True,completePayloadValidated=False,payloadTerminatorObserved=False,
        originalMarshalObserved=False,naturalInitOrRoutePassed=False,stateBytes=72,eventOffset=56,
        maxUnitsIncludingFirstNul=32768,maxUtf8Bytes=65535,maxSelfUnitReads=32781,maxSelfBytes=65562,
        extraNaturalReads=0,newOriginalPEWindows=0,fixtureCases=len(CASES),sourceCount=6)

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

def verify_inputs(client=None,prepare=False):
    pins=frozen_sources()
    accepted=json.loads((SPLIT_RAW/'owned-main1/proof.private.json').read_bytes())
    files,_=follow.frozen_inputs()
    prior_epoch=follow.audit_epoch(files)
    # Validate exact cached class49/container/full text extents, never GAME_PATH.
    follow.source_assets(files)
    refs=frozen_config.reference_bytes((frozen_config.RESOURCE/'server_pc.private.json').read_bytes())
    require(refs==(SPLIT_RAW/'references.private.json').read_bytes(),'Frozen reference bytes differ')
    generated=(SPLIT_RAW/'binding.private.rs').read_bytes()
    require(digest(generated)==accepted['bindingSource']['sha256'],'Frozen code binding differs')
    if prepare:
        write_new_or_equal(RAW/'references.private.json',refs)
        write_new_or_equal(RAW/'binding.private.rs',generated)
    else:
        require((RAW/'references.private.json').read_bytes()==refs,'Private reference source differs')
        require((RAW/'binding.private.rs').read_bytes()==generated,'Private code binding differs')
    if client is not None:
        for row in accepted['fixedInputs'].values():
            raw=(client/row['path']).read_bytes()
            require(len(raw)==row['bytes'] and digest(raw)==row['sha256'],'Fixed stock input differs')
    return dict(frozenSourcePins=pins,priorProofs=accepted['priorProofs'],fixedInputs=accepted['fixedInputs'],
        referenceProof=accepted['referenceProof'],referenceBytesEqual=True,bindingSource=source_id(RAW/'binding.private.rs'),
        staticContract=accepted['staticContract'],reusedAcceptedStaticContract=True,newOriginalPEWindows=0,
        exeWindows=accepted['exeWindows'],callerWindow=accepted['callerWindow'],sources=source_epoch(),
        prefixContract=prefix_source_contract(),legacySplitEpoch=prior_epoch,
        buildInputs={name:source_id(ROOT/name) for name in ('patch/Cargo.toml','patch/Cargo.lock')},
        definitionSHA256=digest(def_text().encode('ascii')),ownedPrefixPythonOracle=owned_prefix_oracle(),
        sourceGateExecutedRust=False,sourceOnlyNoGamePathRead=client is None)

def def_text():
    return '\n'.join(['LIBRARY sdk72_config_prefix_proxy','EXPORTS',
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
        extraExportPurposes={STATE:'fixed managed prefix observation metadata/facts DATA',
            'sdk72_config_prefix_inner':'unique installed EXE callback inner',
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
    if event in (2,13):
        require(state['env'] in frozen_config.CN_ENVS and state['app_key_matches'] in (0,1) and state['combo_url_matches'] in (0,1),'Invalid classification')
        result.update(env='cn-env-'+str(state['env']),appKeyMatches=bool(state['app_key_matches']),comboUrlMatches=bool(state['combo_url_matches']))
        if event==13:result.update(completePayloadValidated=False,payloadTerminatorObserved=False,originalMarshalObserved=False,naturalInitOrRoutePassed=False)
    else:require(event==1 or state['env']==state['app_key_matches']==state['combo_url_matches']==0,'Failure carries classification data')
    return result

def artifact_contract(proxy,exe):
    verified=require_artifact(proxy);pe=peutil.PE(proxy);exports={r['name']:r for r in pe.exports()}
    inner=exports['sdk72_config_prefix_inner']['rva'];candidates=[]
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
    parser.add_argument('--client',type=Path);parser.add_argument('--output-directory',type=Path,required=True)
    parser.add_argument('--prepare-build-inputs',action='store_true');parser.add_argument('--production',type=Path)
    parser.add_argument('--fixture',type=Path);parser.add_argument('--run-owned',action='store_true')
    args=parser.parse_args();output=confined(args.output_directory)
    require(not output.exists(),'Preserve earlier evidence; choose a fresh output')
    require(not args.run_owned or args.production and args.fixture,'Owned run requires both artifacts')
    require(not args.production or args.client is not None,'Artifact code contract requires explicit client from parent')
    proof=verify_inputs(args.client,args.prepare_build_inputs);output.mkdir(parents=True)
    (output/'config-prefix-proxy.def').write_bytes(def_text().encode('ascii'))
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
    print(json.dumps(dict(sourceVerified=True,sourceCount=len(proof['sources']),reusedAcceptedStaticChecks=len(proof['staticContract']['checks']),newWindows=0,newCodeBytes=0,
        artifactVerified=proof['artifact'] is not None,ownedPassed=proof['owned']['passed'] if proof['owned'] else None,clientStarted=False)))
    if proof['owned'] is not None and not proof['owned']['passed']:raise SystemExit(1)

if __name__=='__main__':main()
