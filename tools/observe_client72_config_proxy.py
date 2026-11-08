"""Fixed ConfigSync code/state collector; live startup is an explicit parent-only CLI."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import struct

try:
    from . import observe_client72_clean_loader as clean
    from . import observe_client72_key_loader as frozen
    from . import verify_sdk72_config_proxy as audit
except ImportError:
    import observe_client72_clean_loader as clean
    import observe_client72_key_loader as frozen
    import verify_sdk72_config_proxy as audit

ROOT,RAW=audit.ROOT,audit.RAW
ENTRY_RVA=0x13d5b0
MOVED=bytes.fromhex('48895c24084889742410574883ec40')
FIELDS=('schema','bytes','phase','failure','win32_error','installed','sdk_base','exe_base',
        'sdk_size','exe_size','original_bound','self_held','sdk_held','exe_held','event','env','app_key_matches','combo_url_matches')
EVENTS={0:'no-matching-event',1:'copying',2:'classified',3:'name-read-failed',4:'payload-read-failed',5:'over-limit',
        6:'invalid-json',7:'unsupported-env',8:'unsupported-shape'}
MAX_READS,MAX_BYTES,MAX_SINGLE=11,1600,512

def decode_state(raw):return dict(zip(FIELDS,struct.unpack('<6I2Q10I',raw)))

def event_output(state):
    event=state['event']
    audit.require(event in EVENTS,'Unknown event state')
    result=dict(status=EVENTS[event])
    if event==2:
        audit.require(state['env'] in audit.CN_ENVS and state['app_key_matches'] in (0,1) and state['combo_url_matches'] in (0,1),'Classified state differs')
        result.update(env='cn-env-'+str(state['env']),appKeyMatches=bool(state['app_key_matches']),comboUrlMatches=bool(state['combo_url_matches']))
    else:
        audit.require(event==1 or state['env']==state['app_key_matches']==state['combo_url_matches']==0,'Failure state contains classification values')
    return result

def artifact_contract(proxy,sdk,exe):
    verified=audit.require_artifact(proxy);pe=clean.PE(proxy)
    exports={row['name']:row for row in pe.exports()}
    inner=exports['sdk72_config_inner']['rva'];candidates=[]
    pattern=re.compile(re.escape(frozen.CALLBACK_PREFIX)+b'.{4}'+re.escape(frozen.CALLBACK_SUFFIX),re.S)
    for section in pe.sections:
        if section[-1]&0x20000000:
            for match in pattern.finditer(pe.take(section[4],section[3])):
                rva=section[2]+match.start()
                if rva+21+struct.unpack_from('<i',match[0],17)[0]==inner:candidates.append((rva,match[0]))
    audit.require(len(candidates)==1,'Unique exact config wrapper/inner is absent')
    arrays=frozen.source_arrays((ROOT/(frozen.ILHOOK+'x64.rs')).read_text(encoding='utf8'))
    sdkpe,exepe=clean.PE(sdk),clean.PE(exe)
    audit.require(sdkpe.bytes_at(ENTRY_RVA,15)==MOVED,'ConfigSync moved code differs')
    return dict(stateRva=verified['stateRva'],tableRva=verified['tableRva'],callbackRva=candidates[0][0],
        callbackCode=candidates[0][1],arrays=arrays,sdkImageSize=sdkpe.image_size,exeImageSize=exepe.image_size,
        sdkWindows=[(rva,sdkpe.bytes_at(rva,n)) for rva,n in audit.SDK_WINDOWS],
        exeWindows=[(rva,exepe.bytes_at(rva,n)) for rva,n in audit.EXE_WINDOWS])

def trampoline(contract,entry,callback,userdata):
    prolog,epilog1,epilog2=contract['arrays']
    prefix=prolog+b'\x48\xba'+struct.pack('<Q',userdata)+bytes.fromhex('4889e14883ec1048b8')
    prefix+=struct.pack('<Q',callback)+bytes.fromhex('ffd04883c410')+epilog1+epilog2
    audit.require(len(prefix)==250,'Frozen ilhook JmpBack layout differs')
    # All fifteen original bytes have no PC-relative operand, so the exact iced
    # encoding is independent of near/far trampoline allocation.
    return prefix+MOVED+bytes.fromhex('ff2500000000')+struct.pack('<Q',entry+15)

def state_kind(state,sdk,exe,contract):
    if state['schema']!=1 or state['bytes']!=80:return 'invalid'
    if state['phase']==5:
        expected=dict(failure=0,win32_error=0,installed=1,sdk_base=sdk,exe_base=exe,sdk_size=contract['sdkImageSize'],
                      exe_size=contract['exeImageSize'],original_bound=1,self_held=1,sdk_held=1,exe_held=1)
        return 'installed' if all(state[k]==v for k,v in expected.items()) else 'invalid'
    if state['phase']==6 and 101<=state['failure']<=113:
        bound=state['failure']==113
        expected=dict(installed=0,sdk_held=0,exe_held=0,event=0,env=0,app_key_matches=0,combo_url_matches=0,
            original_bound=1,self_held=int(state['failure']!=101),sdk_base=sdk if bound else 0,exe_base=exe if bound else 0,
            sdk_size=contract['sdkImageSize'] if bound else 0,exe_size=contract['exeImageSize'] if bound else 0)
        return 'failed' if all(state[k]==v for k,v in expected.items()) else 'invalid'
    return 'nonterminal' if state['phase'] in range(5) else 'invalid'

def collect(read,modules,contract,original_rvas):
    rows=[];requested=0
    def get(name,address,width):
        nonlocal requested
        audit.require(len(rows)<MAX_READS and requested+width<=MAX_BYTES and 0<width<=MAX_SINGLE,'Read budget exceeded')
        requested+=width
        try:data,error=read(address,width)
        except OSError:data,error=b'','os-read-failed'
        good=error is None and len(data)==width
        # Addresses/widths here describe only fixed code or the public 80B DATA.
        rows.append(dict(name=name,address=address,bytes=width,bytesRead=len(data),error=None if good else 'read-failed'))
        return data if good else None
    proxy,sdk,exe,original=(modules[name]['base'] for name in ('Astrolabe.dll','AccountPlatNative.dll','YuanShen.exe','Astrolabe_orig.dll'))
    result=dict(status='partial',reads=rows,targetFunctionsCalled=False,payloadReadByCollector=False,referenceReadByCollector=False,
                writerReturnObserved=False,routeEffectObserved=False,authenticationObserved=False,d02Passed=False)
    before=get('state.before',proxy+contract['stateRva'],80)
    if before is None:return result
    state=decode_state(before);kind=state_kind(state,sdk,exe,contract);result['stateKind']=kind
    result['metadata']={name:state[name] for name in FIELDS if name not in ('event','env','app_key_matches','combo_url_matches')}
    if kind in ('installed','failed'):
        try:result['event']=event_output(state)
        except ValueError:result['reason']='invalid-event-state';return result
        table=get('proxy.forward-targets',proxy+contract['tableRva'],376)
        result['forwardTargetsEqual']=table==struct.pack('<47Q',*(original+rva for rva in original_rvas))
        code=[];observed_entry=None
        for label,base,spans in [('sdk',sdk,contract['sdkWindows']),('exe',exe,contract['exeWindows'])]:
            for rva,expected in spans:
                actual=get(label+'.window.'+hex(rva),base+rva,len(expected))
                code.append(dict(module=label,rva=rva,originalEqual=actual==expected))
                if label=='sdk' and rva==ENTRY_RVA:observed_entry=actual
        result['code']=code
        other_equal=all(row['originalEqual'] for row in code if not (row['module']=='sdk' and row['rva']==ENTRY_RVA))
        if kind=='installed' and result['forwardTargetsEqual'] and other_equal and observed_entry is not None:
            try:
                target,encoding=frozen.patch_target(observed_entry,contract['sdkWindows'][0][1],sdk+ENTRY_RVA)
                template=trampoline(contract,sdk+ENTRY_RVA,proxy+contract['callbackRva'],1)
                actual=get('config.trampoline',target,len(template))
                userdata=struct.unpack_from('<Q',actual,141)[0] if actual is not None else 0
                expected=trampoline(contract,sdk+ENTRY_RVA,proxy+contract['callbackRva'],userdata)
                callback=get('config.callback',proxy+contract['callbackRva'],len(contract['callbackCode']))
                equal=userdata!=0 and actual==expected and callback==contract['callbackCode']
                result.update(patchEncoding=encoding,fullTrampolineAndCallbackEqual=equal,returnEntryPlus15Verified=equal)
                if equal:result['status']='installed-code-bound'
            except ValueError:result['reason']='patch-or-trampoline-differs'
        elif kind=='failed' and result['forwardTargetsEqual'] and all(row['originalEqual'] for row in code):
            result['status']='failed-original-code-bound'
    after=get('state.after',proxy+contract['stateRva'],80)
    result['stableStateEqual']=after==before
    if after!=before or any(row['error'] for row in rows):result['status']='partial'
    if state['event']==1:result.update(status='partial',reason='event-copy-in-progress')
    result['budget']=dict(reads=len(rows),bytes=requested,maxReads=MAX_READS,maxBytes=MAX_BYTES,maxSingle=MAX_SINGLE)
    return result

def preflight(client,artifact,proof_path):
    proof=json.loads(audit.confined(proof_path).read_bytes())
    audit.require(proof['owned']['passed'] is True and all(row['publicResult']==audit.EXPECTED_OWNED and row['ownedEmitterVerified'] is True
                  and audit.verify_owned_emitter(row['ownedEmitter']) for row in proof['owned']['runs']),
                  'Main-owned acceptance is required')
    current=audit.verify_inputs(client)
    audit.require(current['sources']==proof['sources'] and current['bindingSource']==proof['bindingSource'], 'Audited source epoch changed')
    proxy=audit.confined(artifact).read_bytes()
    audit.require(audit.require_artifact(proxy)==proof['artifact'],'Audited artifact changed')
    plugins=client/'YuanShen_Data/Plugins';stock=(plugins/'Astrolabe_orig.dll').read_bytes()
    sdk=(plugins/'AccountPlatNative.dll').read_bytes();exe=(client/'YuanShen.exe').read_bytes()
    audit.require((plugins/'Astrolabe.dll').read_bytes()==proxy,'Installed proxy differs from audited artifact')
    clean.require_exports(clean.PE(stock).exports(),clean.source_names())
    return current,proxy,stock,sdk,exe,artifact_contract(proxy,sdk,exe)

def inspect_process(backend,client,evidence,blobs,contract):
    if not evidence['finalStatus']['alive']:return dict(status='partial',reason='target-exited')
    handle=backend.k.OpenProcess(0x101010,False,evidence['pid'])
    if not handle:return dict(status='partial',reason='process-unavailable')
    try:
        audit.require(backend.creation_time(handle)==evidence['processCreationTime'] and Path(backend.image(handle)).samefile(client/'YuanShen.exe')
                      and backend.status(handle)['alive'],'Same live process identity differs')
        snapshot=backend.snapshot(8|16,evidence['pid'],clean.MODULEENTRY32W,'Module32FirstW','Module32NextW')
        prior=evidence['samples'][-1]['modules'];modules={}
        audit.require(snapshot['error'] is None and prior['error'] is None,'Module enumeration failed')
        for name,raw in blobs.items():
            wanted=client/name if name=='YuanShen.exe' else client/'YuanShen_Data/Plugins'/name
            now=[row for row in snapshot['items'] if row['name'].casefold()==name.casefold()]
            old=[row for row in prior['items'] if row['name'].casefold()==name.casefold()]
            audit.require(len(now)==len(old)==1,'Module absent or ambiguous')
            row,previous=now[0],old[0]
            audit.require(Path(row['path']).samefile(wanted) and Path(previous['path']).samefile(wanted) and row['base']==previous['base']
                and row['size']==previous['size']==clean.PE(raw).image_size and audit.digest(Path(row['path']).read_bytes())==audit.digest(raw),'Module binding differs')
            modules[name]=row
        backend.k.ReadProcessMemory.argtypes=[W.HANDLE,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)]
        backend.k.ReadProcessMemory.restype=W.BOOL
        def read(address,width):
            output=C.create_string_buffer(width);count=C.c_size_t()
            ok=backend.k.ReadProcessMemory(handle,address,output,width,C.byref(count))
            return output.raw[:count.value],None if ok and count.value==width else 'read-failed'
        result=collect(read,modules,contract,[row['rva'] for row in clean.PE(blobs['Astrolabe_orig.dll']).exports()])
        if not backend.status(handle)['alive']:result['status']='partial'
        return result
    finally:backend.close(handle)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',required=True,type=Path)
    parser.add_argument('--production',required=True,type=Path)
    parser.add_argument('--artifact-audit',required=True,type=Path)
    parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--user-ready',action='store_true')
    args=parser.parse_args()
    if not args.user_ready:parser.error('New user-ready is required for this visible startup')
    output=audit.confined(args.output);audit.require(not output.exists(),'Preserve earlier evidence; choose a fresh output')
    result=dict(scope='ConfigSync exact code/state only',sourceSHA256=clean.sha256(Path(__file__)),
        startUtc=datetime.now(timezone.utc).isoformat(),status='preflight')
    try:
        current,proxy,stock,sdk,exe,contract=preflight(args.client,args.production,args.artifact_audit)
        result['inputs']=current;backend=clean.ModuleBackend()
        clean.observe(backend,args.client,result,90)
        result['binding']=inspect_process(backend,args.client,result,{'Astrolabe.dll':proxy,'Astrolabe_orig.dll':stock,
            'AccountPlatNative.dll':sdk,'YuanShen.exe':exe},contract)
        result['status']='completed' if result['binding']['status'] in ('installed-code-bound','failed-original-code-bound') else 'partial'
    except (OSError,ValueError,KeyError,IndexError,TypeError):
        # Never stringify untrusted exception/JSON text.
        result.update(status='partial',reason='fixed-preflight-or-collection-failure')
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('xb') as stream:stream.write(audit.json_bytes(result))
    print(json.dumps(dict(status=result['status'],event=result.get('binding',{}).get('event'),d02Passed=False)))
    if result['status']!='completed':raise SystemExit(1)

if __name__=='__main__':main()
