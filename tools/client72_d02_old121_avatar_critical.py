"""Fixed-source old121 critical fragments; one bounded, conditional native projection."""
from __future__ import annotations
import argparse
from collections import Counter
import json
import math
import pickle
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import client72_d02_old121_avatar_base_growth as base
from tools import probe72_avatar_critical_installer as old
from tools.client72_emulator import GLOBAL, STACK, END
from tools.client72_resource_deep import RETURN
from tools.client72_session_runtime import SessionRuntime72

SCOPE = ROOT / 'local/migration72/d02-resume-20261115/old121-critical-projection-scope/spec.private.json'
SCOPE_SHA = '025c7f08185241caeec3d960ffee8dbb07349bd21716c422fa41066a12939004'
OUT = ROOT / 'local/migration72/d02-resume-20261115/old121-avatar-critical'
LIMIT = 2_000_000
A = 0x1405336c0
FROZEN = base.FROZEN | {0x1518bda40, 0x14c09f510, 0x14dbf1710, 0x14dd204b0, 0x14d868b30}
# Fixed source identities are broader than this execution contract.
EXECUTABLE = {0x1498e2200, 0x1498e56c0, 0x1498e7a60, 0x14aef9db0, 0x14aefa090,
              0x14b770850, 0x14bf1bbf0, 0x14f905140, 0x14f905250, 0x14f9935d0,
              0x14f995d40, 0x14fc6e310, 0x14fc6e420, 0x14fdf7030, 0x150854240,
              0x152e90a60, 0x152e90fe0}


def inputs(client):
    scope = json.loads(base.read_pin(SCOPE, SCOPE_SHA))
    fixed = scope['fixedInputs']
    for item in fixed.values():
        if isinstance(item, dict) and 'path' in item:
            base.read_pin(ROOT / item['path'], item['sha256'])
    base_spec, pins, _, _, spans, blob, _, codec = base.inputs(client)
    report = json.loads((ROOT / fixed['critical']['path']).read_bytes())
    projection = json.loads((ROOT / fixed['baseProjection']['path']).read_bytes())
    for source_pins in (old.entry.PINS, old.PINS, report['sourcePins'], projection['inputPins']):
        for name, digest in source_pins.items():
            if name in pins and pins[name] != digest:
                raise ValueError('conflicting source pin: ' + name)
            path = client / name[len('GAME_PATH/'):] if name.startswith('GAME_PATH/') else ROOT / name
            base.read_pin(path, digest)
            pins[name] = digest
    manifest = dict(scope['nativeAuthority']['resourceReportRanges'])
    for key, row in scope['nativeAuthority']['criticalReportRanges'].items():
        if key in manifest and manifest[key] != row:
            raise ValueError('conflicting native source: ' + key)
        manifest[key] = row
    for key, row in scope['nativeAuthority']['criticalReportRanges'].items():
        actual = report['nativeSources'][key]
        if (actual['end'], actual['sha256']) != (row['endExclusive'], row['sha256']):
            raise ValueError('critical source identity mismatch ' + key)
    if not projection['complete'] or codec['proof'] != scope['resourceCodec']['proof']:
        raise ValueError('projection or resource codec mismatch')
    fixed_sources = {int(k): v for k, v in projection['selectedSources'].items()}
    if fixed_sources != spans:
        raise ValueError('cohort source mismatch')
    return scope, base_spec, pins, report, projection, spans, blob, codec, manifest


class Runtime(base.BoundedRuntime):
    def __init__(self, manifest, *args, **kwargs):
        self.random_stub = None
        self.model_descriptors = []
        super().__init__(manifest, *args, **kwargs)

    def call(self, entry, stops=(), **registers):
        self.models.pop(A, None)
        self.e.models.pop(A, None)
        if stops not in ((), (old.SLICE_STOP,)) or (stops and entry != old.SLICE_START):
            raise ValueError('unexpected entry/stop pair')
        e = self.e
        e.calls, e.failure, e.steps = [], None, 0
        live = {'rsp': STACK + 0x100008, **registers}
        e.write(live['rsp'], RETURN)
        for name, value in live.items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + name.upper()), value)
        counts, reached = Counter(), []
        event = dict(entry=hex(entry), row=dict(self.active) if self.active else None,
                     totalBefore=self.total, liveIn={k: hex(v) for k, v in live.items()})
        self.ledger.append(event)
        last = None
        def observe(ip):
            nonlocal last
            previous, last = last, ip
            self.total += 1
            counts[ip] += 1
            if self.total >= LIMIT:
                raise ValueError('cumulative hook limit reached')
            if ip in FROZEN:
                raise ValueError('frozen boundary ' + hex(ip))
            if ip == RETURN or (stops and ip == old.SLICE_STOP):
                reached.append(ip)
                e.uc.emu_stop()
                return
            allowed_models = base.ALLOWED_MODELS | {0x149a75140}
            if self.random_stub is not None:
                allowed_models.add(self.random_stub)
            if ip in e.models:
                if ip not in allowed_models:
                    raise ValueError('unapproved model ' + hex(ip))
                return
            if 0x1505f5420 <= ip < 0x1505f6f5e and not old.SLICE_START <= ip < old.SLICE_STOP:
                raise ValueError('outside shared slice ' + hex(ip))
            if not (old.SLICE_START <= ip < old.SLICE_STOP or any(
                    start <= ip < int(self.manifest[start]['endExclusive'], 16) for start in EXECUTABLE)):
                raise ValueError('unapproved native target ' + hex(ip) + ' from ' + (hex(previous) if previous else 'entry'))
            if ip in self.manifest:
                self.bind_function(ip)
        e.before_instruction = observe
        try:
            e.uc.emu_start(entry, END, count=min(LIMIT, LIMIT - self.total))
        finally:
            e.before_instruction = None
            event.update(instructions=e.steps, totalAfter=self.total,
                         executedAddresses={hex(k): v for k, v in sorted(counts.items())},
                         modelCalls=e.calls, failure=e.failure,
                         stop=hex(reached[0]) if len(reached) == 1 else None)
        expected = old.SLICE_STOP if stops else RETURN
        if e.failure or reached != [expected]:
            raise ValueError(e.failure or 'exact stop not reached')
        return event


class Session(SessionRuntime72):
    def __init__(self, runtime):
        self.runtime = runtime
        super().__init__(runtime.e, 5, 7, random_value=0x12345678)

    def call(self, address, receiver=None, argument=0):
        rt, e = self.runtime, self.runtime.e
        rt.models.pop(A, None)
        e.models.pop(A, None)
        random_class = e.read(self.random)
        stub = e.read(random_class + 0xf8)
        next_double = e.read(random_class + 0x110)
        e.models.pop(next_double, None)
        rt.random_stub = stub
        rt.model_descriptors = [dict(address=hex(0x149a75140), role='declared Random object accessor', random=hex(self.random)),
                                dict(address=hex(stub), role='Random.Next fixture', classSlot='0xf8', value='0x12345678', randomClass=hex(random_class)),
                                dict(address=hex(next_double), role='NextDouble disabled', enabled=False)]
        registers = {'rdx': argument}
        if receiver is not None:
            registers['rcx'] = receiver
        rt.call(address, **registers)
        return e.reg('rax')


def setup_codec(rt):
    e = rt.e
    array = e.models[0x140514ee0]
    before = (e.read(GLOBAL + 0xe970), e.read(0x145872d30, 'I'))
    start = rt.total
    session = Session(rt)
    e.models[0x140514ee0] = array
    for offset in (0xbed0, 0x31e60, 0x25a50):
        e.write(GLOBAL + offset, e.read(GLOBAL + 0x204d0))
    if before != (e.read(GLOBAL + 0xe970), e.read(0x145872d30, 'I')):
        raise ValueError('resource scalar codec changed')
    if A in rt.models or A in e.models or rt.total - start != 4:
        raise ValueError('session isolation or initialization changed')
    return session


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(OUT.resolve()) or output.exists():
        p.error('fresh output inside private directory required')
    output.parent.mkdir(parents=True, exist_ok=True)
    result = dict(task='R3-OLD121-AVATAR-CRITICAL-PROJECTION', complete=False, resourceReady=False,
                  presence='unknown', fragments=[], cases=[], namedReadback=[], ledger=[], active='input gates')
    rt = None
    try:
        scope, spec, pins, report, projection, spans, blob, codec, manifest = inputs(args.client.resolve())
        result.update(scopeSHA256=SCOPE_SHA, inputPins=pins, selectedSources=spans,
                      payloadSHA256=base.sha(blob), scalarStorageCodec=codec['proof'])
        binary, metadata, startup = [(args.client / name).read_bytes() for name in list(base.prior.card.FIVE_INPUTS)[:3]]
        records, cells = pickle.loads((base.prior.equip.RAW / 'metadata.private.pkl').read_bytes())
        rt = Runtime(manifest, binary, metadata, startup, records, cells,
                     ROOT / 'local/migration72/python', 'Weapon', codec=codec)
        base.prior.equip.install_table(rt, 'Avatar')
        rt.e.allowed[:] = [(start, int(row['endExclusive'], 16)) for start, row in rt.manifest.items()] + [(RETURN, RETURN + 1)]
        rt.bind_function(0x1505f5420)
        rt.input_arrays = {}
        rt.active = {'stage': '30710 initialization'}
        session = setup_codec(rt)
        result['codec30710'] = dict(multiplier=5, shift=7, randomNext='0x12345678', nativeInitializationHooks=4,
                                   resourceScalarCodecPreserved=True, modelDescriptors=rt.model_descriptors)
        for key in spec['cohort']['newReadoutIds']:
            pointer = base.parse_row(rt, key, 58272, 0x14f9935d0, spans[key], blob)
            rt.active['stage'] = 'id and resource getters'
            fields = [base.field(rt, owner, idx, pointer) for owner, idx in
                      ((27264, 134163), (63287, 313963), (63287, 313956), (63287, 313966))]
            if [f['offset'] for f in fields] != ['0x18', '0x98', '0x78', '0xa4'] or fields[0]['value'] != key:
                raise ValueError('field identity or id mismatch')
            if fields[0]['type'].get('typeDefinition') != 58209 or any(f['type'].get('typeDefinition') != 54724 for f in fields[1:]):
                raise ValueError('field type mismatch')
            if not all(math.isfinite(f['value']['float32']) for f in fields[1:]):
                raise ValueError('non-finite float')
            row = dict(key=key, pointer=hex(pointer), source=spans[key], fields=fields)
            rt.active['stage'] = 'shared slice and named readback'
            case = old.run_resource_case(rt, row, 'original-' + str(key))
            fragment = old.projection(row, [case])
            fragment.update(key=key, source=spans[key], payloadSHA256=base.sha(blob), inputPresence='unknown', fieldSources=fields)
            result['fragments'].append(fragment)
            result['cases'].append(case)
            result['namedReadback'].extend(dict(avatarId=key, **r) for r in case['namedReadback'])
        reuse = next(x for x in report['javaProjectionFragments'] if x['javaFields']['id'] == 10000007)
        reuse_case = next(x for x in report['cases'] if x.get('avatarId') == 10000007 and x.get('mutation') is None)
        if any(x['source'] != spans[10000007] for x in reuse['fields']):
            raise ValueError('reused 7 source mismatch')
        reuse = dict(reuse, key=10000007, source=spans[10000007], payloadSHA256=base.sha(blob), reused=True)
        result['fragments'].append(reuse)
        result['reused7NamedReadback'] = reuse_case['namedReadback']
        if {x['key'] for x in result['fragments']} != set(spans) or len(result['namedReadback']) != 240:
            raise ValueError('cohort incomplete')
        base.prior.equip.verify_client(args.client)
        result.update(complete=True, active=None)
    except Exception as exc:
        result.update(error=str(exc), active=rt.active if rt else result['active'])
        raise
    finally:
        if rt:
            result.update(ledger=rt.ledger, cumulativeHookVisits=rt.total, nativeSources=rt.functions,
                          modelDescriptors=rt.model_descriptors, nativeFailure=rt.e.failure)
            rt.e.close()
        output.write_text(json.dumps(result, indent=2, sort_keys=True, allow_nan=False) + '\n', encoding='utf8')
    print(json.dumps(dict(complete=result['complete'], hooks=rt.total, output=str(output))))

if __name__ == '__main__':
    main()
