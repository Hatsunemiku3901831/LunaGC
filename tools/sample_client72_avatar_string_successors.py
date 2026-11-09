"""Read-only String candidate sampler, tied to a sealed fresh stock epoch."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime,timezone,timedelta
import hashlib
import json
from pathlib import Path
import re
import struct
from . import collect72_avatar_string_successors as card
from . import sample_client72_interface_context as env

ROOT=Path(__file__).resolve().parents[1]
RAW=ROOT/'local/migration72/d02-parallel-20261109/combined-successor-inputs'
MONITOR_SHA='e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e'
HELPER_SHA='02ffaeab9c5e2f96bd135f7007b1f964c2863fb16471611c03867b3aefdda251'

def digest(raw):return hashlib.sha256(raw).hexdigest()
def timestamp(value):
    result=datetime.fromisoformat(value.replace('Z','+00:00'))
    if result.tzinfo is None:raise ValueError('UTC timestamp required')
    return result.astimezone(timezone.utc)
def utc():return datetime.now(timezone.utc).isoformat()

def epoch_gate(plan_path,ready_path,*,now=None,root=ROOT):
    if plan_path != root/'local/migration72/d02-parallel-20261109/combined-successor-inputs/live-plan.private.json' or ready_path != root/'local/migration72/d02-parallel-20261109/combined-successor-inputs/live-ready.private.json':raise ValueError('Fixed plan and ready paths required')
    plan_raw=plan_path.read_bytes();plan=json.loads(plan_raw);ready=json.loads(ready_path.read_bytes())
    if set(plan)!={'schema','runId','createdUTC','monitorPath','outputPath','sourcePins','monitorSourceSHA256','budget'} or set(ready)!={'schema','planSHA256','runId','confirmedUTC','monitorPath'} or plan['schema']!=1 or ready['schema']!=1:raise ValueError('Epoch schema differs')
    run=plan['runId']
    if not isinstance(run,str) or not re.fullmatch(r'[A-Za-z0-9_-]+',run):raise ValueError('Invalid run identifier')
    monitor='local/migration72/d02-active/plain/d02-parallel-avatar-string-'+run+'.json'
    if plan['monitorPath']!=monitor or ready['monitorPath']!=monitor or ready['runId']!=run or ready['planSHA256']!=digest(plan_raw):raise ValueError('Epoch binding differs')
    if plan['monitorSourceSHA256']!=MONITOR_SHA or plan['budget']!=dict(maxReads=76,maxBytes=3264,maxSingleRead=208):raise ValueError('Frozen budget or monitor differs')
    expected={'tools/observe_client72_uninstrumented.py':MONITOR_SHA,'tools/sample_client72_interface_context.py':HELPER_SHA}
    for path in ('tools/collect72_avatar_string_successors.py','tools/sample_client72_avatar_string_successors.py'):
        expected[path]=digest((root/path).read_bytes())
    if set(plan['sourcePins'])!=set(expected):raise ValueError('Epoch source pin set differs')
    output=plan['outputPath']
    if not isinstance(output,str) or not output.startswith('local/migration72/d02-parallel-20261109/combined-successor-inputs/live/') or '\\' in output or not (root/output).resolve().is_relative_to((root/'local/migration72/d02-parallel-20261109/combined-successor-inputs/live').resolve()) or (root/output).exists():raise ValueError('Fresh planned output required')
    for path,want in expected.items():
        if plan['sourcePins'].get(path)!=want or digest((root/path).read_bytes())!=want:raise ValueError('Epoch source differs')
    for path,want in plan['sourcePins'].items():
        if digest((root/path).read_bytes())!=want:raise ValueError('Epoch source pin differs')
    source_raw=(root/monitor).read_bytes();source=json.loads(source_raw)
    if source['status']!='completed' or source['elapsedSeconds']<90 or not source['finalStatus']['alive'] or not source['pid'] or not source['samples'] or source['sourceSHA256']!=MONITOR_SHA:raise ValueError('Completed stock monitor required')
    created,confirmed,start,launch=map(timestamp,(plan['createdUTC'],ready['confirmedUTC'],source['startUtc'],source['launchTimeUtc']))
    current=now or datetime.now(timezone.utc)
    creation=source['processCreationTime']
    if creation['winerror'] is not None or type(creation['filetime']) is not int:raise ValueError('Process creation unavailable')
    creation_utc=datetime(1601,1,1,tzinfo=timezone.utc)+timedelta(microseconds=creation['filetime']//10)
    if not created<=confirmed<=start<=confirmed+timedelta(seconds=300) or creation_utc<confirmed or current>launch+timedelta(seconds=source['elapsedSeconds']+60) or current<start:
        raise ValueError('Fresh epoch timing differs')
    return plan,source,digest(source_raw)

def source_gate(*,now=None):
    plan,source,observation_sha=epoch_gate(RAW/'live-plan.private.json',RAW/'live-ready.private.json',now=now)
    game=Path(source['launch']['cwd']).resolve();target=game/'YuanShen.exe'
    if not env.same_file(source['image'],target) or not env.same_file(source['launch']['executable'],target):raise ValueError('Observed image differs')
    inputs=env.verified_inputs(ROOT,game);original=source['inputs']
    if inputs['profileSHA256']!=original['profileSHA256'] or inputs['files']!=original['files']:raise ValueError('Fixed stock differs')
    for name in ('.env','.env.local','config.json'):
        if any(original['config'][name].get(k)!=inputs['config'][name].get(k) for k in ('exists','sha256','error')):raise ValueError('Configuration differs')
    profile=json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    binary,metadata,startup=[(game/profile['files'][n]['path']).read_bytes() for n in ('binary','metadata','startup')]
    source_checks=card.verify_sources(binary,metadata,startup)
    pe=env.PE(binary);optional=struct.unpack_from('<I',pe.data,0x3c)[0]+24;size=struct.unpack_from('<I',pe.data,optional+56)[0]
    observed=env.main_module(source['samples'][-1]['modules'],target)
    if observed['size']!=size:raise ValueError('Observed module size differs')
    return plan,source,game,observed,dict(sourceObservationSHA256=observation_sha,inputChecks=inputs,stringSourceChecks=source_checks)

class ReadOnlyBackend(env.WindowsBackend):
    def __init__(self):
        super().__init__();self.k.ReadProcessMemory.argtypes=[W.HANDLE,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)];self.k.ReadProcessMemory.restype=W.BOOL
    def open(self,pid):return self.k.OpenProcess(env.PROCESS_READ_QUERY_WAIT,False,pid)
    def read(self,handle,address,width):
        buf,count=C.create_string_buffer(width),C.c_size_t();ok=self.k.ReadProcessMemory(handle,address,buf,width,C.byref(count))
        return buf.raw[:count.value],None if ok and count.value==width else dict(kind='read-failed',winerror=C.get_last_error() if not ok else None)

def sample(backend,game,evidence,observed,*,collector=None):
    collect=collector or card.collect;result=dict(task='AVATAR-STRING-SUCCESSORS-live',status='partial',backendRecords=[],identityObservations=[],endIdentityVerified=False,naturalCallerObserved=False,d02Complete=False,openedUTC=utc())
    if not evidence['finalStatus']['alive']:result['reason']='target-exited-before-open';return result
    handle=backend.open(evidence['pid'])
    if not handle:result['reason']='process-unavailable';return result
    requested=0;target=game/'YuanShen.exe'
    def identity(stage):
        row=dict(stage=stage);result['identityObservations'].append(row)
        row['creationTime']=backend.creation_time(handle);row['image']=backend.image(handle);row['status']=backend.status(handle)
        row['modules']=backend.snapshot(8|16,evidence['pid'],env.MODULEENTRY32W,'Module32FirstW','Module32NextW')
        row['mainModule']=env.main_module(row['modules'],target);main=row['mainModule']
        if row['creationTime']!=evidence['processCreationTime'] or not row['status']['alive'] or not env.same_file(row['image'],target) or main['base']!=observed['base'] or main['size']!=observed['size']:raise ValueError('process identity differs')
        return row
    stage='before'
    try:
        result['identityBefore']=identity(stage)
        def read(address,width):
            nonlocal requested
            if len(result['backendRecords'])>=card.MAX_READS or not 0<width<=card.MAX_SINGLE or requested+width>card.MAX_BYTES:raise ValueError('Backend budget exceeded')
            requested+=width;begin=utc()
            try:raw,error=backend.read(handle,address,width)
            except OSError as failure:
                code=getattr(failure,'winerror',None);raw,error=b'',dict(kind='read-os-error',winerror=code if code is not None else failure.errno)
            result['backendRecords'].append(dict(ordinal=len(result['backendRecords'])+1,address=address,requested=width,bytesRead=len(raw),bytesHex=raw.hex(),error=error,beginUTC=begin,endUTC=utc()))
            return raw,error
        stage='collection';result['collection']=collect(read,observed['base']);result['status']=result['collection']['status']
        if collector is None:
            actual=[(x['address'],x['requested'],x['bytesRead'],x['bytesHex']) for x in result['backendRecords']]
            recorded=[(x['address'],x['requested'],x['bytesRead'],x['bytesHex']) for x in result['collection']['records'] if x['backendCalled']]
            if actual!=recorded:raise ValueError('Backend ledger differs')
        stage='after';result['identityAfter']=identity(stage)
        stage='end-inputs';result['endInputs']=env.verified_inputs(ROOT,game);original=evidence['inputs'];current=result['endInputs']
        matches=current['profileSHA256']==original['profileSHA256'] and current['files']==original['files'] and all(
            all(original['config'][name].get(key)==current['config'][name].get(key) for key in ('exists','sha256','error'))
            for name in ('.env','.env.local','config.json'))
        stage='final-alive';result['finalStatus']=backend.status(handle)
        if not matches:result.update(status='partial',failureStage='end-inputs',reason='end-inputs-differ')
        elif not result['finalStatus']['alive']:result.update(status='partial',failureStage='final-alive',reason='target-exited')
        else:result['endIdentityVerified']=True
    except OSError as failure:
        result.update(status='partial',failureStage=stage,reason=stage+'-os-error',error=dict(type='OSError',winerror=getattr(failure,'winerror',None),errno=failure.errno))
    except (ValueError,KeyError,IndexError,StopIteration):result.update(status='partial',failureStage=stage,reason=stage+'-failed')
    finally:
        backend.close(handle);result.update(closedUTC=utc(),backendBudget=dict(reads=len(result['backendRecords']),requestedBytes=requested,maxReads=card.MAX_READS,maxBytes=card.MAX_BYTES,maxSingleRead=card.MAX_SINGLE),pid=evidence['pid'],creation=evidence['processCreationTime'])
    return result

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.parse_args(argv)
    plan,source,game,observed,proof=source_gate()
    output=(ROOT/plan['outputPath']).resolve()
    if not output.is_relative_to(RAW.resolve()) or output.exists():raise ValueError('Fresh planned output required')
    result=sample(ReadOnlyBackend(),game,source,observed);result.update(proof)
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('x',encoding='utf8') as stream:json.dump(result,stream,indent=2);stream.write('\n')
    print(json.dumps(dict(status=result['status'],budget=result.get('backendBudget'),d02Complete=False)))
    return 0 if result['status']=='candidate' else 1

if __name__=='__main__':raise SystemExit(main())
