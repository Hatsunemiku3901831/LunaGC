"""Observe a candidate RegionDispatch reader -> session+B0 -> Token tag965.

The upstream reader/coroutine type association and business name remain unknown.
Only the declared post-parse fragments execute; no HTTP/decrypt/scheduling claim.
"""
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, STACK, OBJECT, GLOBAL
    from .client72_session_runtime import fragment
    from .trace_client72_responses import Reader72
    from .trace_client72_auth_sources import parse_args
    from .trace_client72_token_constructor import TokenConstructor72
    from .trace_client72_token_complete import construct_native_token
    from .trace_client72_login import Writer72
    from .trace_client72_token import parse_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, STACK, OBJECT, GLOBAL
    from client72_session_runtime import fragment
    from trace_client72_responses import Reader72
    from trace_client72_auth_sources import parse_args
    from trace_client72_token_constructor import TokenConstructor72
    from trace_client72_token_complete import construct_native_token
    from trace_client72_login import Writer72
    from trace_client72_token import parse_wire

PATHS = ((0x151212949,0x151212983,0x151212E11,0x151212E7B),
         (0x15122952D,0x151229567,0x1512299DF,0x151229A49))
BINDING = dict(sourceTypeIndex=30677, sourceField='DGHGFAELIDO', sourceOffset=32,
               sourceTag=14, sessionTypeIndex=58013, sessionOffset=176,
               requestTypeIndex=82491, requestOffset=112, requestTag=965, type='string')


def recover(binary, metadata, records, libraries):
    by_index={r['index']:r for r in records}
    for index,off,name in ((30677,32,'DGHGFAELIDO'),(58013,176,None),(82491,112,None)):
        f=next(f for f in by_index[index]['fields'] if f.get('offset')==off)
        if f['type']['kind']!='string' or (name and f['name']!=name):raise ValueError('Raw source layout changed')
    if not any(m['registeredAddress']=='0x153a598a0' and m['virtualSlot']==19 for m in by_index[30677]['methods']):
        raise ValueError('Candidate reader declaration changed')
    fixtures=[]
    for path_index,(entry,stop,missing_manager,missing_response) in enumerate(PATHS):
        for variant,(name,value) in enumerate((('nonzero','source-fixture-A'),('empty',''),
                                              ('high-numeric-text','9007199254740993'),('distinct','source-fixture-B'))):
            e=Offline72(binary,libraries,records,True); reader=Reader72(e); runtime=None
            try:
                raw=value.encode('ascii')
                # Independent literal protobuf key 0x72: field14, length-delimited.
                source_wire=b'\x72'+bytes([len(raw)])+raw
                read=reader.run(30677,source_wire,0x153A598A0)
                pointer=e.read(OBJECT+32)
                if reader.values.get(pointer)!=dict(kind='string',hex=raw.hex()):
                    raise ValueError('Candidate native reader did not store independent tag14')
                # Reader helper storage is an explicit UTF-16 managed-string fixture.
                e.write(pointer+16,len(raw),'I');e.uc.mem_write(pointer+20,value.encode('utf-16-le')+b'\0\0')
                runtime=TokenConstructor72(e,binary,metadata,variant,'456');runtime.strings[pointer]=value
                box=e.alloc();e.write(box,OBJECT);e.allowed.append((entry,stop))
                trace=fragment(e,entry,{stop,missing_manager,missing_response},dict(rsp=STACK+0x100008,rbx=box))
                if trace['stop']!=hex(stop) or e.read(runtime.account+176)!=pointer:
                    raise ValueError('Native dispatch fragment did not preserve source pointer to session+B0')
                crypto,_=construct_native_token(e,runtime,binary,metadata,0.5,True)
                request_pointer=e.read(runtime.request+112)
                if raw and request_pointer!=pointer:raise ValueError('Token constructor replaced the nonempty source pointer')
                request_text=bytes(e.uc.mem_read(request_pointer+20,e.read(request_pointer+16,'I')*2)).decode('utf-16-le')
                if request_text!=value:raise ValueError('Token constructor changed the source value')
                runtime.strings[request_pointer]=request_text
                e.run(0x1500C4A50,runtime.request,0)
                if e.reg('eax')!=24604:raise ValueError('Token command mismatch')
                writer=Writer72(e);wire=writer.run(82491,runtime.values(82491,runtime.request,writer))['wire']
                decoded=[(t,k,v) for t,k,v in parse_wire(bytes.fromhex(wire)) if t==965]
                if decoded!=([(965,2,raw)] if raw else []):raise ValueError('Independent Token tag965 mismatch')
                # Independent expected key aa3c, not generated from observations.
                expected_wire=b'\xaa\x3c'+bytes([len(raw)])+raw if raw else b''
                if expected_wire and expected_wire not in bytes.fromhex(wire):raise ValueError('Literal wire assertion failed')
                fixtures.append(dict(name=f'path{path_index}-{name}',source=value,reader=read,trace=trace,
                                     crypto=crypto,requestWire=wire,expectedFieldWire=expected_wire.hex(),
                                     sessionPointerPreserved=True,requestPointerPreserved=request_pointer==pointer,passed=True))
            finally:
                if runtime:runtime.close()
                reader.close();e.close()
        for name in ('missing-manager','missing-session','missing-source'):
            e=Offline72(binary,libraries,records,True);runtime=None
            try:
                e.prepare();runtime=TokenConstructor72(e,binary,metadata,0,'456')
                prior=e.read(runtime.account+176);box=e.alloc();source=e.alloc();e.write(box,source)
                e.write(source+32,runtime.string('unused-source'))
                manager=e.read(GLOBAL+0x9e80)
                if name=='missing-manager':e.write(GLOBAL+0x9e80,0)
                elif name=='missing-session':e.write(manager+0x180,0)
                else:e.write(box,0)
                e.allowed.append((entry,stop))
                trace=fragment(e,entry,{stop,missing_manager,missing_response},dict(rsp=STACK+0x100008,rbx=box))
                expected=missing_manager if name=='missing-manager' else missing_response if name=='missing-source' else stop
                if trace['stop']!=hex(expected) or e.read(runtime.account+176)!=prior or runtime.request is not None:
                    raise ValueError('Missing dispatch input branch changed')
                fixtures.append(dict(name=f'path{path_index}-{name}',trace=trace,unchanged=True,passed=True))
            finally:
                if runtime:runtime.close()
                e.close()
    return dict(schemaVersion=1,inputSHA256=HASHES,rawBinding=BINDING,fixtureCount=len(fixtures),fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k not in ('reader','trace','crypto','requestWire')} for f in fixtures],
                paths=[dict(entry=hex(a),stop=hex(b),missingManagerStop=hex(c),missingSourceStop=hex(d)) for a,b,c,d in PATHS],
                sourceAssociationStatus='candidate',semanticStatus='unknown',runtimeStatus='not-tested',
                nativeSeedHelperExecuted=True,wholeDispatchExecuted=False,
                models=['existing bounded Reader72 ASCII helper and UTF-16 storage',
                        'source30677 object supplied explicitly at post-parse fragment; upstream association not proven',
                        'existing Token SDK/environment/OS/seed inputs'],
                outsideScope=['HTTP JSON content/sign decryption','actual coroutine-to-source30677 type binding',
                              'business name of tag965','full dispatch flow','real client/network'])


def validate_summary(report, mappings, wire):
    errors=[]
    if wire.get('inputSHA256')!=HASHES:errors.append('Dispatch raw wire input mismatch')
    expected=dict(inputSHA256=HASHES, rawBinding=BINDING, fixtureCount=14,
                  sourceAssociationStatus='candidate', semanticStatus='unknown', runtimeStatus='not-tested',
                  nativeSeedHelperExecuted=True, wholeDispatchExecuted=False)
    if any(report.get(k)!=v for k,v in expected.items()):errors.append('Dispatch source scope/binding mismatch')
    paths=[dict(entry=hex(a),stop=hex(b),missingManagerStop=hex(c),missingSourceStop=hex(d)) for a,b,c,d in PATHS]
    if report.get('paths')!=paths:errors.append('Dispatch source native boundaries mismatch')
    rows=report.get('results',[])
    names=[f'path{i}-{name}' for i in range(2) for name in ('nonzero','empty','high-numeric-text','distinct','missing-manager','missing-session','missing-source')]
    if [r.get('name') for r in rows]!=names:errors.append('Dispatch source cases mismatch')
    for row in rows:
        if row.get('passed') is not True:errors.append('Dispatch source failed fixture')
        if 'source' in row:
            raw=row['source'].encode('ascii')
            expected_wire=(b'\xaa\x3c'+bytes([len(raw)])+raw).hex() if raw else ''
            if (row.get('expectedFieldWire')!=expected_wire or row.get('sessionPointerPreserved') is not True
                    or row.get('requestPointerPreserved')!=bool(raw)):
                errors.append('Dispatch source pointer/wire mismatch')
        elif row.get('unchanged') is not True:errors.append('Dispatch missing input changed session')
    raw=next((f for f in wire['fields'] if f.get('objectOffset')==112),{})
    mapped=next((f for s in mappings['schemas'] if s['semanticName']=='GetPlayerTokenReq' for f in s['fields'] if f.get('tag')==965),{})
    if (any(raw.get(k)!=v for k,v in dict(tag=965,type='string',clientField='OEDFOPBKADI').items())
            or any(mapped.get(k)!=v for k,v in dict(status='unresolved',semanticField='unknown_field_965',type='string').items())):
        errors.append('Dispatch raw observation cannot promote tag965 semantics')
    return errors


def main():
    a=parse_args();b,m,s=[read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_=extract(b,m,startup=s,structures=True)
    report=recover(b,m,records,a.libraries)
    root=Path(__file__).resolve().parents[1]/"protocol/cn-7.2"
    mappings=json.loads((root/"mappings.json").read_text("utf8"))
    wire=json.loads((root/"token-wire-observations.json").read_text("utf8"))
    errors=validate_summary(report,mappings,wire)
    if errors:raise ValueError(errors)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'],semanticStatus=report['semanticStatus'])))


if __name__=='__main__':main()
