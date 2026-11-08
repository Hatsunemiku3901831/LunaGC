"""Offline identity/PE audit and self-owned loader tests; never starts a client."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys

try:
    from . import audit_sdk72_clean_loader as clean
except ImportError:
    import audit_sdk72_clean_loader as clean

ROOT=Path(__file__).resolve().parents[1]
RAW=ROOT/'local/migration72/d02-resume-20261108/key-loader'
CORE='patch/src/modules/sdk72_key_input.rs'
CORE_SHA='67af556fea165d1597741cee2bbf2d310542c6c5c9637e44ee8877f4cc885bcd'
CLEAN_AUDIT=ROOT/'local/migration72/d02-resume-20261108/clean-loader/offline-artifacts-main1.json'
PUBLIC_ROOT=ROOT/'local/migration72/d02-active/sdk-key-input-sol'
PUBLIC={
    'sdk-mode0.public.spki.pem':(268,'42a8cfbff43e26979202d5bc16d17602c627ac40d54dc43b48b1815e75d5daac'),
    'passport_1024.public.spki.pem':(276,'aea72393abf5b79744b0977715b8c8b7e7b99de55c55babeddeb901f3aefddf2'),
}
WINDOWS=[(0x3950c0,253,'634a8f823f6f84675977f867ec3e114c6eed100f68be81bb49e2add2e0820b8c'),
         (0x34ee6b,76,'a8c01b85e8f4f33a66b66f715337e0170ea3cbda961578afe2ba4f52e0243a8a'),
         (0x34ef3c,76,'f986612ad684f67b05286d4a367d6a163ef0f0c5a5f549cf13b9d4f00c08d24d')]
STATE_EXPORT='Sdk72KeyProxyState'
CORE_EXPORT='sdk72_key_input_inner'
MOCK_SHA='7688425c50ae84d31ba9f863bd58518e96b44d862a631f43163f223b114a69d0'
DEFAULT_MOCK=ROOT/'local/migration72/d02-resume-20261108/clean-loader/mock-normal/sdk72_clean_mock.dll'
EXPECTED_OWNED={
    'schema':1,'scope':'self-owned modules/code only; real SDK disk input is never loaded',
    'public_state_bytes':64,'production_data_export_dormant':True,'production_forwarding_calls':0,
    'original_sdk_loaded':False,'original_sdk_called':False,'getter_called':False,
    'loaded_binding_positive':True,'loaded_binding_rejections':9,
    'three_windows_independently_guarded':True,'actual_read_error_rejected':True,
    'absent_zero_budget_timeout':True,'held_module_survives_caller_release':True,
    'failed_bindings_release_reference':True,'good_selected_material_prepared':True,
    'material_rejections':2,'owned_hook_original_callback':True,'owned_return_high64':True,
    'owned_hook_drop_restores_code':True,'actual_ilhook_error_no_target_write':True,
    'failure_state_not_installed':True,'worker_not_started_by_state_read':True,
    'not_covered':['production SDK hook','natural caller execution','client integrity response','route/trust/auth','D-02'],
}


def digest(raw):return hashlib.sha256(raw).hexdigest()


def confined(path):
    path=Path(path).resolve()
    if not path.is_relative_to(RAW.resolve()):raise ValueError('Output/artifact must stay in the key-loader directory')
    return path


def write_json(path,value):
    # Binary output is intentional: the hash names actual LF bytes on Windows.
    raw=(json.dumps(value,indent=2)+'\n').encode('utf8')
    Path(path).write_bytes(raw)


def identity(path):
    path=Path(path);raw=path.read_bytes()
    return dict(path=path.resolve().relative_to(ROOT).as_posix(),bytes=len(raw),sha256=digest(raw))


def def_text(names,core_inner=False):
    lines=['LIBRARY sdk72_key_proxy','EXPORTS']
    lines += [f'  {name} @{index+1}' for index,name in enumerate(names)]
    lines.append(f'  {STATE_EXPORT} @48 DATA')
    if core_inner:lines.append(f'  {CORE_EXPORT} @49')
    return '\n'.join(lines)+'\n'


def require_artifact(raw,names,allow_core_inner=False):
    if len(names)!=47:raise ValueError('The fixed export contract requires47 names')
    pe=clean.PE(raw);exports=pe.exports()
    extras=[(STATE_EXPORT,48)]+([(CORE_EXPORT,49)] if allow_core_inner else [])
    clean.require_exports(exports,names,extras)
    state=next(row for row in exports if row['name']==STATE_EXPORT)
    expected=struct.pack('<II',1,64)+bytes(56)
    if pe.bytes_at(state['rva'],64)!=expected:raise ValueError('Fixed public data schema/initial state differs')
    section=next((row for row in pe.sections if row[2]<=state['rva'] and state['rva']+64<=row[2]+row[3]),None)
    if section is None or not section[-1]&0x80000000 or section[-1]&0x20000000:
        raise ValueError('State export must be writable non-executable data')
    slots=[];resolvers=[]
    for index,name in enumerate(names):
        row=next(item for item in exports if item['name']==name);rva=row['rva']
        code=pe.bytes_at(rva,24)
        section=next((item for item in pe.sections if item[2]<=rva and rva+24<=item[2]+item[3]),None)
        if section is None or not section[-1]&0x20000000 or code[:2]!=b'\xff\x25' or code[6:9]!=bytes((0x6a,index,0xe8)) or code[13:20]!=b'\x48\x8d\x64\x24\x08\xff\x25':
            raise ValueError('Frozen hot/cold export thunk instructions differ')
        slot=rva+6+struct.unpack_from('<i',code,2)[0]
        cold_slot=rva+24+struct.unpack_from('<i',code,20)[0]
        resolver=rva+13+struct.unpack_from('<i',code,9)[0]
        if cold_slot!=slot or struct.unpack('<Q',pe.bytes_at(slot,8))[0]!=pe.base+rva+6:
            raise ValueError('Frozen cold-target initial pointer/slot differs')
        slots.append(slot);resolvers.append(resolver)
    if slots!=[slots[0]+index*8 for index in range(47)] or len(set(resolvers))!=1:
        raise ValueError('Frozen47 table/resolver identity differs')
    if allow_core_inner:
        inner=next(row for row in exports if row['name']==CORE_EXPORT)
        section=next((row for row in pe.sections if row[2]<=inner['rva']<row[2]+row[3]),None)
        if section is None or not section[-1]&0x20000000:raise ValueError('Frozen core inner export is not native code')
    return dict(bytes=len(raw),sha256=digest(raw),exports=exports,stateBytes=64,stateSchema=1,
        frozenCoreInnerExplicitlyAllowed=allow_core_inner,productionForwardingCalled=False,
        frozenThunkCount=47,initialTargetTableRva=slots[0],coldResolverRva=resolvers[0])


def verify_sources(client):
    baseline=json.loads(CLEAN_AUDIT.read_bytes());pins=dict(baseline['source_hashes']);pins[CORE]=CORE_SHA
    for path,expected in pins.items():
        if digest((ROOT/path).read_bytes())!=expected:raise ValueError('Frozen source drift: '+path)
    inputs=[]
    inventory=json.loads((ROOT/'local/migration72/d02-resume-20261108/initial-inventory.private.json').read_bytes())
    for row in inventory['inputs']:
        raw=(client/row['file']).read_bytes()
        if len(raw)!=row['size'] or digest(raw)!=row['sha256']:raise ValueError('Fixed input drift: '+row['file'])
        inputs.append(dict(file=row['file'],bytes=len(raw),sha256=row['sha256']))
    material={}
    for name,(width,expected) in PUBLIC.items():
        raw=(PUBLIC_ROOT/name).read_bytes()
        if len(raw)!=width or digest(raw)!=expected:raise ValueError('Selected public-only build input drift')
        material[name]=dict(bytes=width,sha256=expected)
    sdk=clean.PE((client/'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes())
    if sdk.image_size!=0x932000 or any(digest(sdk.bytes_at(rva,width))!=expected for rva,width,expected in WINDOWS):
        raise ValueError('Fixed SDK three-window identity differs')
    paths=[ROOT/'patch/examples/sdk72_key_proxy.rs',*sorted((ROOT/'patch/examples/sdk72_key_proxy').glob('*.rs'))]
    fixture=ROOT/'patch/examples/sdk72_key_proxy_fixture.rs'
    if fixture.exists():paths.append(fixture)
    return dict(frozenSourcePins=pins,fixedInputs=inputs,publicBuildInputs=material,
        sources={path.relative_to(ROOT).as_posix():identity(path) for path in paths},
        tools={path.relative_to(ROOT).as_posix():identity(path) for path in
               [Path(__file__).resolve(),ROOT/'tools/test_verify_sdk72_key_proxy.py'] if path.exists()},
        sdkWindows=[dict(rva=rva,bytes=width,sha256=expected) for rva,width,expected in WINDOWS],
        privateKeyRead=False,targetGetterCalled=False,clientStarted=False)


def checked_owned(completed):
    result=dict(exitCode=completed.returncode,stdoutBytes=len(completed.stdout),stderrBytes=len(completed.stderr),
        classification='owned_fixture_failed',passed=False)
    try:payload=json.loads(completed.stdout)
    except (ValueError,UnicodeDecodeError):return result
    if json.dumps(payload,sort_keys=True)!=json.dumps(EXPECTED_OWNED,sort_keys=True) or completed.stderr:return result
    result['publicResult']=payload
    result['passed']=completed.returncode==0
    result['classification']='owned_passed' if result['passed'] else 'owned_fixture_nonzero'
    return result


def owned_runs(output,fixture,production,mock,client):
    fixture=confined(fixture);production=confined(production);mock=Path(mock).resolve()
    if digest(mock.read_bytes())!=MOCK_SHA:raise ValueError('Only the frozen self-written mock artifact may be loaded')
    clean.require_exports(clean.PE(mock.read_bytes()).exports(),clean.source_names(),[('OwnedMockAttachCount',48)])
    results=[];public=[]
    for name in ('first','repeat'):
        directory=output/('owned-'+name);directory.mkdir()
        for filename,source in (('OwnedKeySdk.dll',mock),('OwnedOtherKeySdk.dll',mock),('key-proxy.dll',production)):
            target=directory/filename;shutil.copyfile(source,target)
            if digest(target.read_bytes())!=digest(Path(source).read_bytes()):raise ValueError('Owned artifact copy identity differs')
        command=[str(fixture),str(directory/'OwnedKeySdk.dll'),str(directory/'key-proxy.dll'),
                 str((client/'YuanShen_Data/Plugins/AccountPlatNative.dll').resolve())]
        try:
            completed=subprocess.run(command,cwd=directory,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
                timeout=45,check=False)
            result=checked_owned(completed)
        except subprocess.TimeoutExpired as error:
            result=dict(classification='owned_timeout',passed=False,stdoutBytes=len(error.stdout or b''),stderrBytes=len(error.stderr or b''))
        write_json(output/('owned-'+name+'-result.private.json'),result)
        if result['passed']:
            (output/('owned-'+name+'-public.json')).write_bytes(completed.stdout);public.append(completed.stdout)
        results.append(dict(name=name,command=command,**result))
        if not result['passed']:break
    return dict(runs=results,byteIdentical=len(public)==2 and public[0]==public[1],
        allPassed=len(results)==2 and all(row['passed'] for row in results),
        artifacts=dict(fixture=identity(fixture),production=identity(production),ownedMock=identity(mock)),
        clientDllLoaded=False,productionForwardingCalls=0,rawUnrecognizedOutputPersisted=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output-directory',type=Path,required=True)
    parser.add_argument('--production',type=Path)
    parser.add_argument('--allow-frozen-core-inner',action='store_true')
    parser.add_argument('--run-owned',action='store_true')
    parser.add_argument('--fixture',type=Path,default=RAW/'target/release/examples/sdk72_key_proxy_fixture.exe')
    parser.add_argument('--owned-mock',type=Path,default=DEFAULT_MOCK)
    args=parser.parse_args()
    output=confined(args.output_directory)
    if output.exists():parser.error('Preserve prior evidence; choose a new output directory')
    if args.run_owned and not args.production:parser.error('--run-owned requires the audited production artifact')
    proof=verify_sources(args.client)
    names=clean.source_names()
    proof['artifact']=None
    if args.production:
        production=confined(args.production)
        proof['artifact']=require_artifact(production.read_bytes(),names,args.allow_frozen_core_inner)
    output.mkdir(parents=True)
    (output/'key-proxy.def').write_bytes(def_text(names).encode('ascii'))
    (output/'key-proxy-with-core-inner.def').write_bytes(def_text(names,True).encode('ascii'))
    proof['buildInputs']={name:identity(output/name) for name in ('key-proxy.def','key-proxy-with-core-inner.def')}
    proof['artifactVerified']=proof['artifact'] is not None
    proof['owned']=owned_runs(output,args.fixture,args.production,args.owned_mock,args.client) if args.run_owned else None
    proof['ready']=False;proof['actualHookAccepted']=False;proof['d02Complete']=False
    proof['sourceBuildArtifactBoundary']='New source identities and actual artifacts are recorded separately. The parent performed serial locked/offline builds; no binary reproducibility or client hook acceptance is inferred.'
    proof['buildContext']={str(path.relative_to(ROOT)):identity(path) for path in
        [ROOT/'patch/Cargo.toml',ROOT/'patch/Cargo.lock',*sorted(RAW.glob('build-*-main*.log'))] if path.is_file()}
    write_json(output/'proof.private.json',proof)
    print(json.dumps(dict(sourceIdentityVerified=True,artifactVerified=proof['artifactVerified'],
        ownedAllPassed=proof['owned']['allPassed'] if proof['owned'] else None,
        sdkWindows=3,buildDefs=2,clientStarted=False,ready=False)))
    if proof['owned'] is not None and not proof['owned']['allPassed']:raise SystemExit(1)


if __name__=='__main__':main()
