"""Main-only single startup adapter: metadata monitor, then one fixed collector."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime,timezone
import json
from pathlib import Path

from . import observe_client72_clean_loader as clean
from . import verify_sdk72_config_route_proxy as audit

ROOT,RAW=audit.ROOT,audit.RAW
MODULES=('YuanShen.exe','Astrolabe.dll','Astrolabe_orig.dll','AccountPlatNative.dll')

def utc():return datetime.now(timezone.utc).isoformat()

def accepted_proof(acceptance_path,acceptance_sha):
    """Main freezes this new private acceptance record after real artifact/owned
    review. Its exact SHA is an explicit argument, never inferred from disk.
    No source edit or old-epoch pin is needed to activate a reviewed artifact.
    """
    audit.require(isinstance(acceptance_sha,str) and len(acceptance_sha)==64 and all(c in '0123456789abcdef' for c in acceptance_sha),'Explicit acceptance SHA required')
    raw=audit.confined(acceptance_path).read_bytes()
    audit.require(audit.digest(raw)==acceptance_sha,'Main acceptance record differs')
    accepted=json.loads(raw)
    audit.require(set(accepted)=={'schema','proof','production','fixture','manifest','sourceClosure'} and accepted['schema']==3,'Route acceptance schema differs')
    for key in ('proof','production','fixture','manifest'):
        item=accepted[key];path=ROOT/item['path']
        if key!='manifest':path=audit.confined(path)
        else:audit.require(path.resolve()==(ROOT/'patch/Cargo.toml').resolve(),'Manifest path differs')
        audit.require(audit.source_id(path)==item,'Accepted '+key+' identity differs')
    proof=json.loads((ROOT/accepted['proof']['path']).read_bytes())
    audit.require(proof['schema']==3 and proof['owned']['passed'] is True and len(proof['owned']['runs'])==2,'Two fresh accepted owned runs required')
    for row in proof['owned']['runs']:
        audit.require(row['passed'] is True and row['status']=='owned-passed' and row['publicResult']==audit.expected_owned(),'Accepted owned summary differs')
        verified=[audit.verify_owned_emitter(r) for r in row['ownedEmitters']]
        audit.require(verified==row['verifiedEmitters'] and len(verified)==10,'Accepted actual emitter result differs')
        process=row['rawProcess'];directory=audit.confined(ROOT/process['directory'])
        metadata=json.loads((directory/'process.private.json').read_bytes())
        audit.require(metadata=={k:v for k,v in process.items() if k!='directory'},'Owned raw process metadata differs')
        audit.require(type(process['pid']) is int and process['pid']>0 and process['exitCode']==0 and
            process['timedOut'] is False and process['launchFailed'] is False and process['fixture']==accepted['fixture'],
            'Accepted fresh owned process identity differs')
        for stream in ('stdout','stderr'):
            raw=(directory/(stream+'.private.bin')).read_bytes()
            audit.require(dict(bytes=len(raw),sha256=audit.digest(raw))==process[stream],'Owned original '+stream+' differs')
        invocation=json.loads((directory/'argv.private.json').read_bytes())
        audit.require(invocation==dict(argv=process['argv'],cwd=str(directory),fixture=accepted['fixture']),
            'Owned original invocation differs')
    _,pins=audit.frozen_inputs()
    audit.require(proof['frozenSourcePins']==pins,'Accepted frozen pin set differs')
    audit.require(proof['sources']==accepted['sourceClosure']==audit.source_epoch(),'Accepted transitive source closure differs')
    audit.require(proof['buildInputs']=={name:audit.source_id(ROOT/name) for name in ('patch/Cargo.toml','patch/Cargo.lock')},'Accepted build inputs differ')
    audit.require(proof['definitionSHA256']==audit.digest(audit.def_text().encode('ascii')),'Accepted exact DEF differs')
    audit.require(proof['owned']['fixture']==accepted['fixture'],'Accepted fixture path differs')
    audit.require(proof['artifact']==audit.require_artifact((ROOT/accepted['production']['path']).read_bytes()),'Accepted production PE differs')
    return accepted,proof

def preflight(client,artifact,acceptance_path,acceptance_sha):
    client=Path(client).resolve();accepted,proof=accepted_proof(acceptance_path,acceptance_sha)
    audit.require(audit.source_id(audit.confined(artifact))==accepted['production'],'Selected artifact differs from acceptance')
    profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    audit.require(set(profile['files'])==set(clean.EXPECTED),'Profile fixed input set differs')
    fixed={};contents={}
    for name,row in profile['files'].items():
        audit.require(row==proof['fixedInputs'][name] and row['sha256']==clean.EXPECTED[name],'Fixed accepted profile differs')
        path=client/row['path']
        if name=='astrolabe':path=path.with_name('Astrolabe_orig.dll')
        raw=path.read_bytes()
        audit.require(len(raw)==row['size'] and audit.digest(raw)==row['sha256'],'Fixed stock/original alias differs')
        fixed[name]=dict(path=path.relative_to(client).as_posix(),bytes=len(raw),sha256=row['sha256'])
        if name in ('binary','astrolabe','sdk'):contents[name]=raw
    proxy=audit.confined(artifact).read_bytes();plugins=client/'YuanShen_Data/Plugins'
    audit.require((plugins/'Astrolabe.dll').read_bytes()==proxy,'Installed route proxy differs')
    audit.peutil.require_exports(audit.peutil.PE(contents['astrolabe']).exports(),audit.peutil.source_names())
    frozen,unused=audit.frozen_inputs();mat=audit.materials(frozen)
    audit.require((RAW/'binding-route.private.rs').read_bytes()==audit.binding_source(frozen,mat) and audit.source_id(RAW/'binding-route.private.rs')==proof['bindingSource'],'Accepted route binding differs')
    contract=audit.artifact_contract(proxy,contents['binary'],contents['sdk'])
    blobs={'YuanShen.exe':contents['binary'],'Astrolabe.dll':proxy,'Astrolabe_orig.dll':contents['astrolabe'],'AccountPlatNative.dll':contents['sdk']}
    identities={name:dict(path=str(client/name if name=='YuanShen.exe' else plugins/name),bytes=len(raw),
        sha256=audit.digest(raw),imageSize=audit.peutil.PE(raw).image_size) for name,raw in blobs.items()}
    rvas=[row['rva'] for row in audit.peutil.PE(contents['astrolabe']).exports()]
    inputs=dict(acceptanceSHA256=acceptance_sha,acceptance=audit.source_id(audit.confined(acceptance_path)),fixedInputs=fixed,
        installedProxy=accepted['production'],originalAlias=fixed['astrolabe'],sourceClosure=proof['sources'],
        frozenSourcePins=proof['frozenSourcePins'],bindingSource=proof['bindingSource'],
        profileSHA256=clean.sha256(ROOT/'protocol/cn-7.2/profile.json'))
    return inputs,identities,contract,rvas

class ReadOnlyBackend(clean.ModuleBackend):
    def __init__(self):
        super().__init__()
        self.k.ReadProcessMemory.argtypes=[W.HANDLE,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)]
        self.k.ReadProcessMemory.restype=W.BOOL
    def open(self,pid):return self.k.OpenProcess(clean.PROCESS_READ_QUERY_WAIT,False,pid)
    def read(self,handle,address,width):
        output=C.create_string_buffer(width);count=C.c_size_t()
        ok=self.k.ReadProcessMemory(handle,address,output,width,C.byref(count))
        error=None if ok and count.value==width else dict(kind='read-failed',winerror=C.get_last_error() if not ok else None)
        return output.raw[:count.value],error

def process_metadata(backend,handle,pid):
    return dict(timeUtc=utc(),status=backend.status(handle),creationTime=backend.creation_time(handle),
        image=backend.image(handle),modules=backend.snapshot(8|16,pid,clean.MODULEENTRY32W,'Module32FirstW','Module32NextW'))

def identity_failure(metadata,creation,client,prior,identities):
    if not metadata['status']['alive']:return 'target-exited'
    if metadata['creationTime']!=creation or creation['filetime'] is None or creation['winerror'] is not None:return 'process-creation-differs'
    if not Path(metadata['image']).samefile(client/'YuanShen.exe'):return 'process-image-differs'
    current=metadata['modules']
    if current['error'] is not None or prior['error'] is not None:return 'module-enumeration-failed'
    for name in MODULES:
        rows=[r for r in current['items'] if r['name'].casefold()==name.casefold()]
        old=[r for r in prior['items'] if r['name'].casefold()==name.casefold()]
        # Absence of SDK is a retained identity fact, allowed for no-P0/refusal.
        # It may not appear/disappear between the two compared snapshots.
        if name=='AccountPlatNative.dll' and not rows and not old:continue
        if len(rows)!=1 or len(old)!=1:return 'module-not-unique'
        row,previous,wanted=rows[0],old[0],identities[name]
        if not (Path(row['path']).samefile(wanted['path']) and Path(previous['path']).samefile(wanted['path'])
            and row['base']==previous['base'] and row['size']==previous['size']==wanted['imageSize']
            and Path(row['path']).stat().st_size==wanted['bytes'] and clean.sha256(Path(row['path']))==wanted['sha256']):return 'module-binding-differs'
    return None

def inspect_process(backend,client,evidence,identities,contract,original_rvas,raw_rows):
    result=dict(status='partial',d02Passed=False)
    if not evidence['finalStatus']['alive']:result['reason']='target-exited';return result
    result['openUTC']=utc();handle=backend.open(evidence['pid'])
    if not handle:result['reason']='process-unavailable';return result
    requested=0
    try:
        before=process_metadata(backend,handle,evidence['pid']);result['identityBefore']=before
        reason=identity_failure(before,evidence['processCreationTime'],client,evidence['samples'][-1]['modules'],identities)
        def read(address,width):
            nonlocal requested
            audit.require(len(raw_rows)<audit.MAX_READS and 0<width<=audit.MAX_SINGLE and requested+width<=audit.MAX_BYTES,'Raw collector budget exceeded')
            requested+=width;begin=utc()
            try:data,error=backend.read(handle,address,width)
            except OSError as exc:data,error=b'',dict(kind='os-read-failed',winerror=getattr(exc,'winerror',None))
            raw_rows.append(dict(ordinal=len(raw_rows)+1,address=address,requested=width,bytesRead=len(data),
                error=error,bytesHex=data.hex(),beginUTC=begin,endUTC=utc()))
            return data,error
        if reason:result['reason']='start-'+reason
        else:
            # Normalize snapshot spelling only after case-insensitive identity.
            modules={name:next((r for r in before['modules']['items'] if r['name'].casefold()==name.casefold()),None) for name in MODULES}
            modules={name:r for name,r in modules.items() if r is not None}
            result.update(audit.collect(read,modules,contract,original_rvas))
            result['collectedModules']=modules
        after=process_metadata(backend,handle,evidence['pid']);result['identityAfter']=after
        reason=identity_failure(after,evidence['processCreationTime'],client,before['modules'],identities)
        if reason:result.update(status='partial',reason='end-'+reason)
        result['statusAfterValidation']=backend.status(handle)
        if not result['statusAfterValidation']['alive']:result.update(status='partial',reason='end-target-exited')
        return result
    except (OSError,ValueError,KeyError,IndexError,TypeError,StopIteration):
        result.update(status='partial',reason='fixed-identity-or-collection-failure');return result
    finally:
        backend.close(handle);result['closeUTC']=utc();result['closeCount']=1
        result['rawBudget']=dict(reads=len(raw_rows),bytes=requested,maxReads=audit.MAX_READS,maxBytes=audit.MAX_BYTES,maxSingle=audit.MAX_SINGLE)

def output_paths(path):
    output=audit.confined(path);raw=output.with_name(output.name+'.reads.private.json')
    audit.require(not output.exists() and not raw.exists(),'Preserve prior evidence; choose fresh outputs')
    return output,raw

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',required=True,type=Path);parser.add_argument('--production',required=True,type=Path)
    parser.add_argument('--acceptance',required=True,type=Path);parser.add_argument('--acceptance-sha256',required=True)
    parser.add_argument('--output',required=True,type=Path);parser.add_argument('--user-ready',action='store_true')
    parser.add_argument('--preflight-only',action='store_true',help='Only verify actual isolated files/accepted artifacts; no process launch or RPM')
    args=parser.parse_args(argv)
    if not args.user_ready and not args.preflight_only:parser.error('New live ready is required for this visible startup')
    output,raw_output=output_paths(args.output);raw_rows=[]
    result=dict(scope='one arbitrary P0 attempt; fixed five-point route code/256B DATA only',sourceSHA256=clean.sha256(Path(__file__)),
        startUtc=utc(),status='preflight',rawLedger=raw_output.relative_to(ROOT).as_posix(),clientStarted=False,d02Passed=False)
    try:
        inputs,identities,contract,rvas=preflight(args.client,args.production,args.acceptance,args.acceptance_sha256);result['inputs']=inputs
        if args.preflight_only:
            result.update(status='preflight-passed',identities=identities,actualFileGate=True,processReads=0,
                contract=dict(stateRva=contract['stateRva'],tableRva=contract['tableRva'],callbackRva=contract['callbackRva'],innerRva=contract['innerRva']))
        else:
            backend=ReadOnlyBackend();clean.observe(backend,args.client.resolve(),result,90)
            result['clientStarted']=result.get('pid') is not None
            result['binding']=inspect_process(backend,args.client.resolve(),result,identities,contract,rvas,raw_rows)
            result['status']='completed' if result['binding']['status']=='installed-route-code-bound' else 'partial'
    except (OSError,RuntimeError,ValueError,KeyError,IndexError,TypeError):result.update(status='partial',reason='fixed-preflight-or-collection-failure')
    finally:
        result['endUtc']=utc();output.parent.mkdir(parents=True,exist_ok=True)
        with raw_output.open('xb') as stream:stream.write(audit.json_bytes(dict(schema=3,scope='same fixed collector reads only',reads=raw_rows)))
        with output.open('xb') as stream:stream.write(audit.json_bytes(result))
    print(json.dumps(dict(status=result['status'],event=result.get('binding',{}).get('event'),d02Passed=False)))
    if result['status'] not in ('completed','preflight-passed'):raise SystemExit(1)

if __name__=='__main__':main()
