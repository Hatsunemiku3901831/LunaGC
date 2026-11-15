"""Fixed-source old-account Avatar base/growth projection; offline and fragment-only."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
import pickle
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import probe72_avatar_entry_resource_fields as accepted
from tools.client72_emulator import STACK, END
from tools.client72_resource_deep import RETURN

prior = accepted.prior
SCOPE = ROOT / 'local/migration72/d02-resume-20261115/old121-avatar-projection-scope/spec.private.json'
SCOPE_SHA = '315499f7309678d215c313c8250ad58e94024a5f070c33cc21352102a6497b7b'
OUT = ROOT / 'local/migration72/d02-resume-20261115/old121-avatar-base-growth'
LIMIT = 2_000_000
ALLOWED_MODELS = {0x140514ee0, 0x141fdf9c0, 0x141fdf310, 0x14f0de820}
FROZEN = {0x1405336c0, 0x151841361, 0x1488a5772, 0x14c5108da,
          0x149f0c450, 0x149f0cd44, 0x149f0d30b, 0x149f0d741, 0x149f0da28,
          0x151976df0, 0x1404c2be0}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_pin(path, digest):
    data = path.read_bytes()
    if sha(data) != digest:
        raise ValueError('pin mismatch: ' + str(path))
    return data


def inputs(client):
    spec = json.loads(read_pin(SCOPE, SCOPE_SHA))
    pins, sources, _ = prior.verify_inputs(client)
    for section in ('fixedReports', 'fixedResourceInputs'):
        for item in spec[section].values():
            if isinstance(item, dict) and 'path' in item:
                read_pin(ROOT / item['path'], item['sha256'])
                pins[item['path']] = item['sha256']
    for name, info in spec['fixedCodeAndJava'].items():
        read_pin(ROOT / name, info['sha256'])
        pins[name] = info['sha256']
    old = json.loads((ROOT / spec['fixedReports']['baseGrowth']['path']).read_bytes())
    manifest = spec['nativeRangeAuthority']['manifest']
    if set(manifest) != set(old['nativeSources']) or any(
            v['endExclusive'] != old['nativeSources'][k]['end'] or
            v['sha256'] != old['nativeSources'][k]['sha256'] for k, v in manifest.items()):
        raise ValueError('fixed native manifest mismatch')
    for name, digest in old['sourcePins'].items():
        path = client / name[len('GAME_PATH/'):] if name.startswith('GAME_PATH/') else ROOT / name
        read_pin(path, digest)
        pins[name] = digest
    for name, digest in accepted.PINS.items():
        read_pin(ROOT / name, digest)
        pins[name] = digest
    mapping = json.loads((ROOT / spec['fixedReports']['mapping']['path']).read_bytes())
    promote = json.loads((ROOT / spec['fixedReports']['curves']['path']).read_bytes())
    table = next(x for x in mapping['native']['parsedTables'] if x['name'] == 'Avatar')
    curve = next(x for x in promote['tables'] if x['name'] == 'AvatarCurve')
    ids = spec['cohort']['ids']
    if len(ids) != 121 or len(set(ids)) != 121 or table['count'] != 169 or table['cursor'] != table['payloadBytes']:
        raise ValueError('cohort or whole Avatar evidence shape mismatch')
    selected = {}
    for key in ids:
        hits = [x for x in table['rows'] if x['key'] == key]
        if len(hits) != 1:
            raise ValueError('missing or duplicate fixed source key: ' + str(key))
        selected[key] = hits[0]['source']
    blob = (ROOT / spec['fixedResourceInputs']['avatarPayload']['path']).read_bytes()
    for key, span in selected.items():
        if not 4 <= span['begin'] < span['end'] <= len(blob) or sha(blob[span['begin']:span['end']]) != span['sha256']:
            raise ValueError('source span mismatch: ' + str(key))
    codec = prior.card.natural_codec(ROOT / spec['fixedResourceInputs']['codecSnapshot']['path'])
    if codec['proof'] != spec['storageContext']['proof'] or old['scalarStorageCodec'] != codec['proof']:
        raise ValueError('codec proof mismatch')
    return spec, pins, sources, old, selected, blob, curve, codec


class BoundedRuntime(prior.PromoteRuntime):
    def __init__(self, manifest, *args, **kwargs):
        self.manifest = {int(k, 16): v for k, v in manifest.items()}
        self.ledger = []
        self.total = 0
        self.active = None
        self.last_address = None
        super().__init__(*args, **kwargs)
        self.models = {k: v for k, v in self.models.items() if k in ALLOWED_MODELS}
        self.e.models = {k: v for k, v in self.e.models.items() if k in ALLOWED_MODELS}
        self.e.allowed[:] = [(start, int(v['endExclusive'], 16)) for start, v in self.manifest.items()]
        self.e.allowed.append((RETURN, RETURN + 1))
        self.input_arrays = {}
        if set(self.e.models) != ALLOWED_MODELS or 0x1405336c0 in self.models:
            raise ValueError('model isolation failed before native call')

    def bind_function(self, address):
        if address in self.e.models:
            return
        if address not in self.manifest:
            raise ValueError('unapproved native entry ' + hex(address))
        super().bind_function(address)
        row = self.functions[hex(address)]
        expected = self.manifest[address]
        if row['end'] != expected['endExclusive'] or row['sha256'] != expected['sha256']:
            raise ValueError('native range identity mismatch ' + hex(address))

    def call(self, entry, stops=(), **registers):
        if stops:
            raise ValueError('unexpected stop override')
        e = self.e
        e.calls, e.failure, e.steps = [], None, 0
        e.write(STACK + 0x100008, RETURN)
        for key, value in dict(rsp=STACK + 0x100008, **registers).items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + key.upper()), value)
        counts, reached = Counter(), []
        self.last_address = None
        event = dict(entry=hex(entry), row=dict(self.active) if self.active else None, totalBefore=self.total, models=[], counts={})
        self.ledger.append(event)
        def observe(address):
            previous = self.last_address
            self.last_address = address
            self.total += 1
            counts[address] += 1
            if address in e.models:
                event['models'].append(dict(address=hex(address), site=hex(previous) if previous else None))
            if self.total >= LIMIT:
                raise ValueError('cumulative hook limit reached')
            if address in FROZEN:
                raise ValueError('frozen boundary target=' + hex(address) + ' site=' + (hex(previous) if previous else 'unknown'))
            if address == RETURN:
                reached.append(address)
                e.uc.emu_stop()
                return
            if address in e.models:
                if address not in ALLOWED_MODELS:
                    raise ValueError('unapproved model ' + hex(address))
                return
            if not any(start <= address < int(v['endExclusive'], 16) for start, v in self.manifest.items()):
                raise ValueError('unapproved native target=' + hex(address) + ' site=' + (hex(previous) if previous else 'unknown'))
            if address in self.manifest:
                self.bind_function(address)
        e.before_instruction = observe
        try:
            e.uc.emu_start(entry, END, count=LIMIT)
        finally:
            e.before_instruction = None
            event.update(instructions=e.steps, totalAfter=self.total,
                         counts={hex(k): v for k, v in sorted(counts.items())},
                         nativeModels=e.calls, error=e.failure, stop=hex(reached[0]) if reached else None)
        if e.failure or reached != [RETURN]:
            raise ValueError(e.failure or 'native call did not return within its original 2M limit')
        return event


def field(rt, owner, index, pointer):
    matches = [f for f in rt.records[owner]['fields'] if f['index'] == index and f['offsetKind'] == 'instance']
    if len(matches) != 1:
        raise ValueError('field identity changed')
    f = matches[0]
    return dict(owner=owner, fieldIndex=index, offset=hex(f['offset']), type=f['type'],
                value=rt.snapshot_value(pointer + f['offset'], f['type'], 0))


def parse_row(rt, key, typ, parser, span, blob):
    e = rt.e
    rt.active = {'key': key, 'type': typ, 'stage': 'construct'}
    rt.reader, rt.row_entry = e.alloc(64), parser
    identity = sha(blob)
    if identity not in rt.input_arrays:
        data = rt.array(len(blob), 1)
        e.uc.mem_write(data + 0x20, blob)
        rt.input_arrays[identity] = data
    data = rt.input_arrays[identity]
    e.write(rt.reader + 0x10, data)
    pointer = prior.equip.object_storage(rt, typ)
    ctor = next(int(m['registeredAddress'], 16) for m in rt.records[typ]['methods'] if m['name'] == '.ctor')
    rt.call(ctor, rcx=pointer)
    e.write(rt.reader + 0x1c, span['begin'], 'I')
    rt.active['stage'] = 'parse'
    rt.call(parser, rcx=pointer, rdx=rt.reader)
    if e.read(rt.reader + 0x1c, 'I') != span['end']:
        raise ValueError('parser cursor mismatch')
    return pointer


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--check-only', action='store_true')
    a = p.parse_args()
    output = a.output.resolve()
    if not output.is_relative_to(OUT.resolve()) or output.exists():
        p.error('fresh output inside the private directory required')
    spec, pins, sources, old, spans, blob, curve_table, codec = inputs(a.client.resolve())
    if a.check_only:
        print(json.dumps({'pins': len(pins), 'rows': len(spans), 'codec': codec['proof']['tableSHA256']}))
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    result = dict(task=spec['proposedTask'], complete=False, scopeSHA256=SCOPE_SHA, inputPins=pins,
                  selectedSources=spans, fragments=[], nativeOracle=[], ledger=[], resourceReady=False,
                  presence='unknown', active=None)
    rt = None
    try:
        binary, metadata, startup = [(a.client / name).read_bytes() for name in list(prior.card.FIVE_INPUTS)[:3]]
        records, cells = pickle.loads((prior.equip.RAW / 'metadata.private.pkl').read_bytes())
        rt = BoundedRuntime(spec['nativeRangeAuthority']['manifest'], binary, metadata, startup, records,
                            cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
        prior.equip.install_table(rt, 'Avatar')
        prior.bind_generics(rt, prior.SPECS['AvatarCurve']['whole'])
        # Generic setup registers host dictionary stubs that this job cannot use.
        rt.models = {k: v for k, v in rt.models.items() if k in ALLOWED_MODELS}
        rt.e.models = {k: v for k, v in rt.e.models.items() if k in ALLOWED_MODELS}
        if set(rt.e.models) != ALLOWED_MODELS or 0x1405336c0 in rt.models:
            raise ValueError('model isolation failed before native call')
        curve_blob = (ROOT / spec['fixedResourceInputs']['curvePayload']['path']).read_bytes()
        curve_row = next(x for x in curve_table['rows'] if x['key'] == 100)
        span = curve_row['source']
        if sha(curve_blob[span['begin']:span['end']]) != span['sha256']:
            raise ValueError('Curve100 source mismatch')
        cp = parse_row(rt, 100, 68066, 0x14fd05cc0, span, curve_blob)
        rt.active['stage'] = 'curve readout'
        # Decode the four original typed CurveInfo values and compare the fixed report.
        f = next(x for x in rt.records[68066]['fields'] if x['offset'] == 0x10 and x['offsetKind'] == 'instance')
        infos = rt.snapshot_value(cp + 0x10, f['type'], 0)
        expected_infos = next(x['value'] for x in curve_row['fields'] if x['offset'] == '0x10')
        if infos != expected_infos or len(infos) != 4:
            raise ValueError('Curve100 typed factors mismatch')
        curve = dict(key=100, pointer=hex(cp), source=span, fields=[dict(offset='0x10', value=infos)])
        java_fight = {name: int(value) for name, value in re.findall(r'^    (FIGHT_PROP_\w+)\((\d+)\)', sources['game/props/FightProperty.java'], re.M)}
        for key in spec['cohort']['newReadoutIds']:
            pointer = parse_row(rt, key, 58272, 0x14f9935d0, spans[key], blob)
            rt.active['stage'] = 'five field readout'
            fields = [field(rt, owner, idx, pointer) for owner, idx in
                      ((27264, 134163), (63287, 313953), (63287, 313964), (63287, 313951), (63287, 313947))]
            if [f['offset'] for f in fields] != ['0x18', '0x64', '0x9c', '0x5c', '0x48'] or fields[0]['value'] != key:
                raise ValueError('field identity or decoded ID mismatch')
            if [f['type'].get('typeDefinition') for f in fields[:4]] != [58209, 54724, 54724, 54724] or fields[4]['type'].get('elementType', {}).get('typeDefinition') != 81112:
                raise ValueError('field type identity mismatch')
            row = dict(key=key, pointer=hex(pointer), source=spans[key], fields=fields)
            fragment = accepted.project_avatar(row, rt.enum_symbols, java_fight, curve_table)
            fragment.update(fieldSources=fields, payloadSHA256=sha(blob), inputPresence='unknown')
            for prop in accepted.PROPERTIES:
                rt.active['stage'] = 'oracle property ' + str(prop)
                expected, base, binding, factor = accepted.oracle(row, curve, prop)
                trace = rt.call(accepted.HELPER, rcx=pointer, rdx=cp, r8=prop)
                bits = rt.e.reg('xmm0') & 0xffffffff
                if bits != expected:
                    raise ValueError('native oracle bits mismatch')
                result['nativeOracle'].append(dict(avatarId=key, level=100, property=prop,
                    baseBits=base['bits'], growthType=binding, factorBits=factor['bits'],
                    resultBits=f'{bits:08x}', independentOracleBits=f'{expected:08x}', traceIndex=len(rt.ledger)-1))
            result['fragments'].append(fragment)
        result['fragments'].append(next(x for x in old['javaProjectionFragments'] if x['key'] == 10000007))
        result['nativeOracle'].extend(x for x in old['consumers']['completeHelper'] if x['avatarId'] == 10000007 and x['level'] == 100)
        if len(result['fragments']) != 121 or len(result['nativeOracle']) != 363:
            raise ValueError('incomplete cohort or oracle')
        prior.equip.verify_client(a.client)
        result.update(complete=True, active=None, scalarStorageCodec=codec['proof'])
    except Exception as error:
        result.update(error=str(error), active=rt.active if rt else None)
        raise
    finally:
        if rt:
            result.update(ledger=rt.ledger, cumulativeHookVisits=rt.total)
            rt.e.close()
        output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', encoding='utf8')
    print(json.dumps(dict(complete=result['complete'], output=str(output), hookVisits=rt.total)))


if __name__ == '__main__':
    main()
