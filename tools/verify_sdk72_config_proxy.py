"""Private build inputs, exact PE/EAT and owned-output audit. Never loads a client DLL."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess

try:
    from . import audit_sdk72_clean_loader as peutil
    from . import observe_client72_key_loader as frozen
except ImportError:
    import audit_sdk72_clean_loader as peutil
    import observe_client72_key_loader as frozen

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'local/migration72/d02-resume-20261109/r2-config-observer'
RESOURCE = ROOT/'local/migration72/d02-resume-20261108/r2-route/main-third'
SOURCE_PROOF = ROOT/'local/migration72/d02-resume-20261108/r2-route/main-third.json'
SOURCE_PROOF_SHA = 'bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471'
BUNDLE_SHA = '8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda'
ROUTE_TOOL_SHA = 'd4816a4448ef2533d20ab7bc656ef617888a79ded69fb3416c5defe24fdf6cac'
REUSED_PINS = {
    'tools/observe_client72_key_loader.py':'44c37ecda27df5c2d0db9c00e2d1227b0314d1e444a0e2bd78a1491bcf54b5e2',
    'tools/observe_client72_clean_loader.py':'6ece909b020efd46a15124f20198c02b41c43f775637c50203a29ea2fb363dd7',
    'tools/observe_client72_uninstrumented.py':'e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e',
    'tools/audit_sdk72_clean_loader.py':'6ae98c400633d6778a4c6f23313267399bbf72d1672c910c725bcd783fe02dee',
    'tools/verify_sdk72_key_proxy.py':'90135f45eb06df1008eb5a4c7f42facd2355b71dafd08ea107904da4f81b222b',
    'patch/src/lib.rs':'1ff1f93d0bf682cb1741cd14de89a922a018fb089452a5e0f3c968085b73f302',
}
STATE = 'Sdk72ConfigProxyState'
EXTRAS = [(STATE,48),('sdk72_config_inner',49),('sdk72_key_input_inner',50)]
CN_ENVS = (0,1,4,6,8,9,22,19)
SDK_WINDOWS = ((0x13d5b0,96),(0x1343b0,64),(0x36fba0,64))
EXE_WINDOWS = ((0x6d80910,224),(0x6e363b0,64),(0x19807960,64))
OWNED_CASES = ['exact','env-double','key-mismatch','url-mismatch','unknown-env','invalid-json','invalid-utf8',
               'wrong-shape','nested-shape','over-limit','nul-boundary','unknown-caller-no-payload-read',
               'approx-name-no-payload-read','case-name-no-payload-read','unreadable-payload','unreadable-name']
EXPECTED_OWNED = dict(schema=1,scope='owned ConfigSync hook only',cases=OWNED_CASES,
    originalCallsPerInvocation=1,returnHigh64=True,gpr16=True,xmm16=True,flags=True,entryStack128=True,
    entryAndReturnLastError=True,dropRestored=True,actualInstallErrorNoWrite=True,referenceRejections=3,
    failureStateNotInstalled=True,allCnEnvClasses=True,sdkLoaded=False,clientStarted=False,naturalEventObserved=False,d02Passed=False)

def require(value, message):
    if not value: raise ValueError(message)

def digest(raw): return hashlib.sha256(raw).hexdigest()

def confined(path):
    path=Path(path).resolve()
    require(path.is_relative_to(RAW.resolve()),'Artifact/output is outside this experiment')
    return path

def write_new_or_equal(path,raw):
    if path.exists(): require(path.read_bytes()==raw,'Existing private build input differs')
    else:
        path.parent.mkdir(parents=True,exist_ok=True)
        with path.open('xb') as stream:stream.write(raw)

def json_bytes(value):return (json.dumps(value,indent=2)+'\n').encode()

def source_id(path):
    raw=path.read_bytes()
    return dict(path=path.relative_to(ROOT).as_posix(),bytes=len(raw),sha256=digest(raw))

def frozen_sources():
    proof_path=ROOT/'local/migration72/d02-resume-20261108/key-loader/verify-owned-final1/proof.private.json'
    require(digest(proof_path.read_bytes())==frozen.PINS[proof_path.relative_to(ROOT).as_posix()],'Frozen key audit changed')
    proof=json.loads(proof_path.read_bytes())
    pins={**proof['frozenSourcePins'],**REUSED_PINS}
    for name in ('patch/examples/sdk72_key_proxy/sdk72_key_proxy_loader.rs','patch/src/modules/sdk72_key_input.rs',
                 'patch/Cargo.lock',frozen.ILHOOK+'x64.rs',frozen.ILHOOK+'x64/move_inst.rs'):
        pins[name]=frozen.PINS[name]
    for name,expected in pins.items(): require(digest((ROOT/name).read_bytes())==expected,'Frozen dependency changed: '+name)
    return pins

def reference_bytes(resource):
    """Private return value only: no repr, lengths or hashes of references in reports."""
    try:
        rows=json.loads(resource)
        require(isinstance(rows,list) and [row['env'] for row in rows]==list(CN_ENVS)+( [2,3,5,7,11,12,21,20]),'Fixed reference env order differs')
        selected=[]
        for row in rows[:8]:
            key,url=row['app_key'],row['url']['comboLogin']
            require(isinstance(key,str) and bool(key) and isinstance(url,str) and bool(url),'Fixed reference shape differs')
            selected.append([row['env'],key,url])
        return json_bytes(selected)
    except (KeyError,TypeError,json.JSONDecodeError,UnicodeError):
        raise ValueError('Fixed reference shape differs') from None

def private_material(client):
    require(digest(SOURCE_PROOF.read_bytes())==SOURCE_PROOF_SHA,'Fixed resource proof changed')
    require(digest((ROOT/'tools/client72_sdk_route_loader.py').read_bytes())==ROUTE_TOOL_SHA,'Fixed resource reader changed')
    proof=json.loads(SOURCE_PROOF.read_bytes())
    carrier=client/'YuanShen_Data/StreamingAssets/MiHoYoSDKRes/PC/mihoyo_sdk_res'
    require(digest(carrier.read_bytes())==BUNDLE_SHA,'Fixed resource carrier changed')
    # Reparse the complete cached class49 carrier extent using the frozen reader.
    # This does not run a native decoder or hash the decoded config/key/URL.
    try:
        from .client72_sdk_route_loader import serialized_config
    except ImportError:
        from client72_sdk_route_loader import serialized_config
    decoded=(RESOURCE/'mihoyo_sdk_res.serialized.private.bin').read_bytes()
    binding,actual=serialized_config(decoded,proof['bundle']['parsed']['content'][0]['size'])
    # Frozen parser emits tuple nodes; the recorded JSON proof has array nodes.
    require(json.loads(json.dumps(binding))==proof['asset'],'Exact class49/container binding changed')
    cached=(RESOURCE/'server_pc.private.json').read_bytes()
    require(actual==cached,'Complete class49 text extent differs')
    return reference_bytes(actual),dict(carrierSHA256=BUNDLE_SHA,sourceProofSHA256=SOURCE_PROOF_SHA,
        exactClass49ContainerAndFullTextExtentEqual=True,referenceEnvs=list(CN_ENVS),
        referenceBytesLengthOrHashRecorded=False,decodedCarrierHashRecorded=False,freshDecodeExecuted=False)

def windows(pe,spans):return [dict(rva=rva,bytes=width,sha256=digest(pe.bytes_at(rva,width))) for rva,width in spans]

def binding_source(exe,sdk):
    rows=['use super::loader::Window;',f'pub const EXE_SIZE: u32 = {exe.image_size};',
          'pub const EXE_SHA: &str = "8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5";']
    for name,pe,spans in [('SDK_WINDOWS',sdk,SDK_WINDOWS),('EXE_WINDOWS',exe,EXE_WINDOWS)]:
        rows.append(f'pub const {name}: [Window<\'static>; 3] = [')
        for row in windows(pe,spans):rows.append('Window { rva: %d, bytes: %d, sha: "%s" },'%(row['rva'],row['bytes'],row['sha256']))
        rows.append('];')
    return ('\n'.join(rows)+'\n').encode()

def source_epoch():
    paths=[ROOT/'patch/examples/sdk72_config_proxy.rs',ROOT/'patch/examples/sdk72_config_proxy_fixture.rs',
        *sorted((ROOT/'patch/examples/sdk72_config_proxy').glob('*')),
        ROOT/'tools/verify_sdk72_config_proxy.py',ROOT/'tools/test_verify_sdk72_config_proxy.py',
        ROOT/'tools/observe_client72_config_proxy.py',ROOT/'tools/test_observe_client72_config_proxy.py',ROOT/'patch/Cargo.toml']
    return {path.relative_to(ROOT).as_posix():source_id(path) for path in paths if path.is_file()}

def verify_inputs(client,prepare=False):
    pins=frozen_sources()
    profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    inputs={}
    for name,row in profile['files'].items():
        # Installed proxy is verified separately; stock input is its exact alias.
        path=client/row['path']
        if name=='astrolabe' and digest(path.read_bytes())!=row['sha256']:path=path.with_name('Astrolabe_orig.dll')
        require(digest(path.read_bytes())==row['sha256'],'Fixed client input changed: '+name)
        inputs[name]=dict(path=row['path'],sha256=row['sha256'])
    refs,reference_proof=private_material(client)
    exe=peutil.PE((client/profile['files']['binary']['path']).read_bytes())
    sdk=peutil.PE((client/profile['files']['sdk']['path']).read_bytes())
    require(sdk.bytes_at(0x13d5b0,15)==bytes.fromhex('48895c24084889742410574883ec40'),'ConfigSync moved prefix changed')
    generated=binding_source(exe,sdk)
    if prepare:
        write_new_or_equal(RAW/'references.private.json',refs)
        write_new_or_equal(RAW/'binding.private.rs',generated)
    else:
        require((RAW/'references.private.json').read_bytes()==refs,'Private references differ from exact source')
        require((RAW/'binding.private.rs').read_bytes()==generated,'Private code binding differs from fixed inputs')
    return dict(frozenSourcePins=pins,fixedInputs=inputs,referenceProof=reference_proof,
        sdkWindows=windows(sdk,SDK_WINDOWS),exeWindows=windows(exe,EXE_WINDOWS),
        bindingSource=source_id(RAW/'binding.private.rs'),sources=source_epoch())

def def_text():
    return '\n'.join(['LIBRARY sdk72_config_proxy','EXPORTS',
        *[f'  {name} @{index+1}' for index,name in enumerate(peutil.source_names())],
        f'  {STATE} @48 DATA','  sdk72_config_inner @49','  sdk72_key_input_inner @50'])+'\n'

def require_artifact(raw):
    pe=peutil.PE(raw);names=peutil.source_names();exports=pe.exports()
    peutil.require_exports(exports,names,EXTRAS)
    state=next(row for row in exports if row['name']==STATE)
    require(pe.bytes_at(state['rva'],80)==struct.pack('<II',1,80)+bytes(72),'State schema/initial bytes differ')
    state_section=next(row for row in pe.sections if row[2]<=state['rva']<row[2]+row[3])
    require(state_section[-1]&0x80000000 and not state_section[-1]&0x20000000,'State is not writable non-executable data')
    # Reuse the independently frozen 47-thunk auditor, substituting only the
    # separately audited extra export schema; no unknown EAT entries are allowed.
    slots=[];resolvers=[]
    for index,name in enumerate(names):
        row=next(item for item in exports if item['name']==name);rva=row['rva'];code=pe.bytes_at(rva,24)
        require(code[:2]==b'\xff\x25' and code[6:9]==bytes((0x6a,index,0xe8)) and code[13:20]==bytes.fromhex('488d642408ff25'),'Frozen forwarding thunk changed')
        slot=rva+6+struct.unpack_from('<i',code,2)[0]
        require(rva+24+struct.unpack_from('<i',code,20)[0]==slot and struct.unpack('<Q',pe.bytes_at(slot,8))[0]==pe.base+rva+6,'Frozen cold slot differs')
        slots.append(slot);resolvers.append(rva+13+struct.unpack_from('<i',code,9)[0])
    require(slots==[slots[0]+8*n for n in range(47)] and len(set(resolvers))==1,'Frozen forwarding table differs')
    for name,_ in EXTRAS[1:]:
        rva=next(row['rva'] for row in exports if row['name']==name)
        section=next(row for row in pe.sections if row[2]<=rva<row[2]+row[3])
        require(section[-1]&0x20000000,'Explicit inner EAT entry is not code')
    return dict(bytes=len(raw),sha256=digest(raw),exports=exports,stateRva=state['rva'],tableRva=slots[0],
        stateBytes=80,dormantKeyInnerExplicitlyAudited=True,extraExportPurposes={STATE:'fixed metadata/event data only',
        'sdk72_config_inner':'unique config callback inner','sdk72_key_input_inner':'dormant frozen real loader type dependency; never referenced by config install'})

def check_owned(completed):
    # Never persist unrecognized stdout/stderr; no subprocess exception text.
    try:
        payload=json.loads(completed.stdout)
        emitted=payload.pop('ownedConfigPrefix')
        verified=verify_owned_emitter(emitted)
    except (ValueError,UnicodeError,KeyError,TypeError,AttributeError,struct.error):payload=None;emitted=None;verified=False
    passed=completed.returncode==0 and not completed.stderr and payload==EXPECTED_OWNED and verified
    return dict(passed=passed,status='owned-passed' if passed else 'owned-failed',publicResult=payload if passed else None,
        ownedEmitterVerified=bool(verified) if passed else False,ownedEmitter=emitted if passed else None)

def verify_owned_emitter(row):
    try:
        from . import observe_client72_config_proxy as config
    except ImportError:
        import observe_client72_config_proxy as config
    require(set(row)=={'entry','callback','userdata','trampoline','patchKind','originalHex','patchedHex','trampolineHex',
                      'trampolineBytes','dropRestored','calleeExecuted','callbackExecuted'},'Owned emitter fields differ')
    require(all(type(row[name]) is int and 0<row[name]<2**64 for name in ('entry','callback','userdata','trampoline')),'Owned emitter metadata differs')
    require(row['dropRestored'] is True and row['calleeExecuted'] is False and row['callbackExecuted'] is False,'Owned emitter execution boundary differs')
    original=config.MOVED+b'\x90'*81
    require(bytes.fromhex(row['originalHex'])==original,'Owned ConfigSync exact prefix differs')
    target,kind=frozen.patch_target(bytes.fromhex(row['patchedHex']),original,row['entry'])
    require(target==row['trampoline'] and kind==row['patchKind'],'Owned actual patch target differs')
    arrays=frozen.source_arrays((ROOT/(frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    expected=config.trampoline(dict(arrays=arrays),row['entry'],row['callback'],row['userdata'])
    require(row['trampolineBytes']==279 and bytes.fromhex(row['trampolineHex'])==expected,'Owned actual full279B differs')
    return True

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output-directory',type=Path,required=True)
    parser.add_argument('--prepare-build-inputs',action='store_true')
    parser.add_argument('--production',type=Path)
    parser.add_argument('--fixture',type=Path)
    parser.add_argument('--run-owned',action='store_true')
    args=parser.parse_args();output=confined(args.output_directory)
    require(not output.exists(),'Preserve earlier evidence; choose a fresh output')
    require(not args.run_owned or args.production and args.fixture,'Owned run requires production and fixture artifacts')
    proof=verify_inputs(args.client,args.prepare_build_inputs)
    output.mkdir(parents=True)
    (output/'config-proxy.def').write_bytes(def_text().encode('ascii'))
    proof['artifact']=require_artifact(confined(args.production).read_bytes()) if args.production else None
    proof['owned']=None
    if args.run_owned:
        fixture=confined(args.fixture);require(fixture.suffix.lower()=='.exe','Owned fixture must be an EXE')
        results=[]
        for _ in range(2):
            try:result=check_owned(subprocess.run([str(fixture)],cwd=output,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60,check=False))
            except subprocess.TimeoutExpired:result=dict(passed=False,status='owned-timeout',publicResult=None)
            results.append(result)
            if not result['passed']:break
        proof['owned']=dict(passed=len(results)==2 and all(row['passed'] for row in results),runs=results,fixture=source_id(fixture))
    proof.update(schema=1,clientStarted=False,naturalEventObserved=False,d02Passed=False)
    (output/'proof.private.json').write_bytes(json_bytes(proof))
    print(json.dumps(dict(sourceVerified=True,artifactVerified=proof['artifact'] is not None,ownedPassed=proof['owned']['passed'] if proof['owned'] else None,clientStarted=False)))
    if proof['owned'] is not None and not proof['owned']['passed']:raise SystemExit(1)

if __name__=='__main__':main()
