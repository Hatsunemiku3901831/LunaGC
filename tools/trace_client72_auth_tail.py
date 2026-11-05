"""Execute the CN72 auth callback through optional session fields, before UI work.

No protocol semantics are inferred from the neighboring optional fields.
"""
import json
from pathlib import Path
try:
    from .trace_client72_auth_sources import AuthSources72, ENTRY, EARLY, ERROR, declarations, parse_args
    from .extract_client72 import HASHES, read_verified, extract
    from .client72_emulator import Offline72, STACK
    from .client72_session_runtime import fragment
except ImportError:
    from trace_client72_auth_sources import AuthSources72, ENTRY, EARLY, ERROR, declarations, parse_args
    from extract_client72 import HASHES, read_verified, extract
    from client72_emulator import Offline72, STACK
    from client72_session_runtime import fragment

STOP = 0x14F192325
FIELDS = (('allow_community', 0x120), ('allow_ugc', 0x124), ('allow_pay', 0x118),
          ('country', 0xe0), ('age_group', 0x184), ('lrsag', 0x16c))
NATIVE = (0x15217B1B0, 0x15217A220, 0x1471685A0)


def recover(binary, metadata, startup, records, libraries):
    declared = declarations(binary, metadata, startup, records)
    by_index = {r['index']: r for r in records}
    for name, offset in FIELDS:
        f = next(f for f in by_index[58013]['fields'] if f.get('offset') == offset)
        if f['type']['kind'] != ('string' if name == 'country' else 'int32'):
            raise ValueError('Optional session field type changed')
    basic = dict(open_id='account-fixture', combo_token='token-fixture')
    cases = [('nonzero', dict(allow_community=1, allow_ugc=2, allow_pay=3, country='CN', age_group=4, lrsag=5)),
             ('zero', dict(allow_community=0, allow_ugc=0, allow_pay=0, country='', age_group=0, lrsag=0)),
             ('signed-boundaries', dict(allow_community=-1, allow_ugc=1, allow_pay=-2147483648,
                                        country='ZZ', age_group=2147483647, lrsag=-2147483648)),
             ('absent', {}),
             ('overflow', dict(allow_community=2147483648, allow_ugc=-2147483649,
                               allow_pay=2147483648, country='CN', age_group=2147483648, lrsag=-2147483649))]
    fixtures = []
    for variant, (name, optional) in enumerate(cases):
        e = Offline72(binary, libraries, records, True); runtime = None
        try:
            e.prepare(); runtime = AuthSources72(e, binary, metadata, variant, '123')
            for address in NATIVE: e.allow(address)
            # Nonzero prior state proves missing keys clear these six fields.
            for key, off in FIELDS: e.write(runtime.account+off, runtime.string('prior-country') if key=='country' else 71, 'Q' if key=='country' else 'i')
            b0 = e.read(runtime.account+0xb0)
            payload = dict(ret=0, data=dict(basic, **optional))
            trace = fragment(e, ENTRY, {STOP, EARLY, ERROR},
                             dict(rsp=STACK+0x100008, rcx=runtime.page, rdx=runtime.string(json.dumps(payload))))
            if trace['stop'] != hex(STOP): raise ValueError('Optional callback did not reach UI boundary')
            actual = {key: runtime.text(e.read(runtime.account+off)) if key=='country' else e.read(runtime.account+off,'i') for key,off in FIELDS}
            expected = {key: optional.get(key, '' if key=='country' else 0) for key,_ in FIELDS}
            expected = {k:v if k=='country' or -(1<<31)<=v<(1<<31) else 0 for k,v in expected.items()}
            flags = [e.read(address,'B') for address in (0x14586CFC7,0x14586D0B1)]
            if actual != expected or flags != [int(expected[k]==1) for k in ('allow_community','allow_ugc')]:
                raise ValueError('Optional JSON session store or exact-one flag mismatch')
            if e.read(runtime.account+0xb0) != b0: raise ValueError('Optional fields unexpectedly changed session+B0')
            fixtures.append(dict(name=name, input=optional, session=actual, exactOneFlags=flags,
                                 b0Unchanged=True, trace=trace, jsonReads=runtime.json_reads, passed=True))
        finally:
            if runtime: runtime.close()
            e.close()
    return dict(schemaVersion=1, inputSHA256=HASHES, entry=hex(ENTRY), stop=hex(STOP),
                declarations=declared, fixtureCount=len(fixtures), fixtures=fixtures,
                results=[{k:v for k,v in f.items() if k not in ('trace','jsonReads')} for f in fixtures],
                sessionBindings=[dict(source='data.'+k, sessionOffset=o, type='string' if k=='country' else 'int32') for k,o in FIELDS],
                semanticPromotions=[], wholeCallbackExecuted=False, runtimeStatus='not-tested',
                models=['existing AuthSources72 JSON/SDK/runtime fixtures; native scalar getters execute',
                        'prior optional session fields initialized to nonzero fixture values'],
                outsideScope=['UI/scheduling after stop','actual SDK login','protocol semantics for these fields','real client'])


def validate_summary(report):
    """Reject promoting bounded session observations to wire or live acceptance."""
    errors = []
    expected = dict(inputSHA256=HASHES, entry=hex(ENTRY), stop=hex(STOP), fixtureCount=5,
                    semanticPromotions=[], wholeCallbackExecuted=False, runtimeStatus='not-tested')
    if any(report.get(k)!=v for k,v in expected.items()): errors.append('Auth tail scope/input mismatch')
    bindings = [dict(source='data.'+k, sessionOffset=o, type='string' if k=='country' else 'int32') for k,o in FIELDS]
    if report.get('sessionBindings')!=bindings: errors.append('Auth tail session bindings mismatch')
    rows = report.get('results', [])
    if [r.get('name') for r in rows]!=['nonzero','zero','signed-boundaries','absent','overflow']:
        errors.append('Auth tail cases mismatch')
    for row in rows:
        expected = {key:row.get('input',{}).get(key,'' if key=='country' else 0) for key,_ in FIELDS}
        expected = {k:v if k=='country' or -(1<<31)<=v<(1<<31) else 0 for k,v in expected.items()}
        if (row.get('session')!=expected or row.get('exactOneFlags')!=[int(expected[k]==1) for k in ('allow_community','allow_ugc')]
                or row.get('b0Unchanged') is not True or row.get('passed') is not True):
            errors.append('Auth tail source/result mismatch')
    return errors


def main():
    a = parse_args(); b,m,s = [read_verified(a.client/name,sha) for name,sha in HASHES.items()]
    records,_ = extract(b,m,startup=s,structures=True)
    report = recover(b,m,s,records,a.libraries)
    errors = validate_summary(report)
    if errors: raise ValueError(errors)
    for path,value in ((a.output,report),(a.summary_output,{k:v for k,v in report.items() if k!='fixtures'})):
        path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(value,indent=2)+'\n','utf8')
    print(json.dumps(dict(fixtures=report['fixtureCount'], semanticPromotions=0)))


if __name__=='__main__': main()
