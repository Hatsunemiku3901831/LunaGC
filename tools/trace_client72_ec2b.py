"""Native Ec2b decoding with repository pair and live RNG cross-evidence.

Standalone codec verification, not dispatch integration or real login.
"""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES,read_verified,extract
    from .client72_emulator import Offline72
    from .client72_ec2b import Ec2b72,KEY_HASHES,LIVE_RNG_SHA256
except ImportError:
    from extract_client72 import HASHES,read_verified,extract
    from client72_emulator import Offline72
    from client72_ec2b import Ec2b72,KEY_HASHES,LIVE_RNG_SHA256


def recover(binary,records,libraries,seed,reference):
    if len(seed)!=2076 or len(reference)!=4096 or {name:hashlib.sha256(data).hexdigest() for name,data in
            (('dispatchSeed.bin',seed),('dispatchKey.bin',reference))}!=KEY_HASHES:
        raise ValueError('Independent seed/key fixture drift')
    fixtures,results = [],[]
    cases = ['paired','relocated','cached-repeat','changed-key','changed-body','bad-magic',
             'bad-key-length','bad-body-length','null','zero-declared','negative-declared',
             'short-declared-full-storage','missing-function','managed-paired','managed-invalid','managed-null']
    for name in cases:
        e = Offline72(binary,libraries,records,True)
        try:
            e.prepare();runtime = Ec2b72(e)
            data = bytearray(seed)
            if name=='relocated':e.alloc(8192)
            if name=='changed-key':data[8]^=1
            elif name=='changed-body':data[100]^=1
            elif name in ('bad-magic','managed-invalid'):data[0]^=1
            elif name=='bad-key-length':data[4]=15
            elif name=='bad-body-length':data[24]=1
            elif name=='missing-function':e.write(0x145744EB8,0)
            length = {'zero-declared':0,'negative-declared':-1,'short-declared-full-storage':1}.get(name)
            previous = None
            if name=='cached-repeat':previous=runtime.decode_raw(bytes(data))
            managed = name.startswith('managed-')
            if managed:
                trace,pointer,output = runtime.decode_managed(None if name=='managed-null' else bytes(data))
                accepted = bool(pointer)
            else:
                trace,accepted,output = runtime.decode_raw(bytes(data),length,name=='null')
                accepted = bool(accepted)
            expect = name in ('paired','relocated','cached-repeat','changed-key','changed-body',
                              'short-declared-full-storage','managed-paired')
            if accepted!=expect:raise ValueError(f'{name}: native acceptance differs')
            equal = output==reference
            modified = name in ('changed-key','changed-body')
            if expect and (len(output)!=4096 or equal==modified):raise ValueError('Independent pair or mutation output differs')
            if not expect and output!=(None if managed else b'\xa5'*4096):raise ValueError('Rejected input changed output')
            if previous and (not previous[1] or previous[2]!=output):raise ValueError('Cached decode changed output')
            if name=='managed-paired' and len(runtime.output_allocations)!=1:raise ValueError('Managed result allocation not observed')
            if not expect and runtime.output_allocations:raise ValueError('Rejected input allocated a result')
            native = trace['executedAddresses']
            if accepted and any(a not in native for a in ('0x141126932','0x1408baee0','0x1408bad50','0x1408af320')):
                raise ValueError('Native cipher chain missing')
            if managed and '0x14146ada0' not in native:raise ValueError('Managed wrapper not executed')
            row = dict(name=name,accepted=accepted,managed=managed,repositoryByteComparison=equal,
                       outputLength=len(output) if accepted else 0,outputSHA256=hashlib.sha256(output).hexdigest() if accepted else None,
                       inputSHA256=hashlib.sha256(data).hexdigest(),outputAllocated=len(runtime.output_allocations),
                       nativePathSHA256=trace['nativePathSHA256'],declaredLength=length if length is not None else len(data),
                       backingInputLength=len(data),unchangedOnReject=(not accepted))
            results.append(row)
            fixtures.append(dict(**row,registration=runtime.registration,rngInitialization=runtime.rng_initialization,
                                 trace=trace,previousTrace=previous[0] if previous else None,passed=True))
        finally:e.close()
    # Different backing addresses and native cache states must not change the known pair.
    hashes={r['outputSHA256'] for r in results if r['repositoryByteComparison']}
    if hashes!={KEY_HASHES['dispatchKey.bin']}:raise ValueError('Pair result was address/cache dependent')
    report=dict(schemaVersion=1,inputSHA256=HASHES,fixtureSHA256=KEY_HASHES,fixtureCount=len(fixtures),
                fixtures=fixtures,results=results,runtimeStatus='not-tested',nativeCipherExecuted=True,
                nativeRngInitialization=True,liveRngStateSHA256=LIVE_RNG_SHA256,dispatchIntegrated=False,
                inputValidationObservation='raw length is only tested >0; full backing storage retained for short-declared sample',
                models=['NativeCrypto72 CRT allocation/memcpy/memset and OS fixtures',
                        'engine selector5 storage with byte element class and exact4096 managed allocation'],
                outsideScope=['physically truncated managed array behavior','dispatch response schema/field semantics',
                              'continuous dispatch-to-key/frame','real SDK/HTTP/game acceptance'])
    errors=validate_summary(report)
    if errors:raise ValueError('; '.join(errors))
    return report


def validate_summary(report):
    errors=[]
    if (report.get('inputSHA256')!=HASHES or report.get('fixtureSHA256')!=KEY_HASHES or
            report.get('runtimeStatus')!='not-tested' or report.get('nativeCipherExecuted') is not True or
            report.get('nativeRngInitialization') is not True or report.get('liveRngStateSHA256')!=LIVE_RNG_SHA256 or
            report.get('dispatchIntegrated') is not False or not report.get('models') or not report.get('outsideScope')):
        errors.append('Ec2b scope/input drift')
    results=report.get('results',[])
    names=['paired','relocated','cached-repeat','changed-key','changed-body','bad-magic','bad-key-length',
           'bad-body-length','null','zero-declared','negative-declared','short-declared-full-storage',
           'missing-function','managed-paired','managed-invalid','managed-null']
    if report.get('fixtureCount')!=16 or [r['name'] for r in results]!=names:return errors+['Missing Ec2b coverage']
    good={'paired','relocated','cached-repeat','short-declared-full-storage','managed-paired'}
    changed={'changed-key','changed-body'}
    for row in results:
        name=row['name'];accepted=name in good|changed
        if row['accepted']!=accepted or row['repositoryByteComparison']!=(name in good):errors.append('Ec2b native result drift')
        if row['outputLength']!=(4096 if accepted else 0):errors.append('Ec2b output size drift')
        if name in good and row['outputSHA256']!=KEY_HASHES['dispatchKey.bin']:errors.append('Independent pair mismatch')
        if not accepted and (not row['unchangedOnReject'] or row['outputAllocated']):errors.append('Rejection altered output')
        if name=='short-declared-full-storage' and (row['declaredLength'],row['backingInputLength'])!=(1,2076):
            errors.append('Short-length scope was lost')
    return errors


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--summary-output',type=Path,required=True)
    p.add_argument('--libraries',type=Path,default=Path('local/migration72/python'));a=p.parse_args()
    for path in (a.output,a.summary_output):
        if path.resolve().is_relative_to(a.client.resolve()):p.error('Output must be outside client')
    b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    root=Path(__file__).resolve().parents[1]/'src/main/resources/keys'
    seed,reference=[read_verified(root/name,sha) for name,sha in KEY_HASHES.items()]
    report=recover(b,records,a.libraries,seed,reference)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'])))


if __name__=='__main__':main()
