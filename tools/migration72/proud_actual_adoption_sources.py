"""Resolve only the fixed Proud hot-map static sources; no target execution."""
import argparse
import hashlib
import json
import pickle
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.extract_client72 import PE, TypeResolver, HEADER_VA, METADATA_REGISTRATION

OUT = ROOT / 'local/migration72/d02-resume-20261111b/proud'
PINS = {
    'tools/extract_client72.py': '7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d',
    'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl': 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    'local/migration72/d02-resume-20261110b/c-proud-actual-fields/closed-aot-final1.private.json': '6318807d1e034ecd6bcb4c8df95d07eb53670a1de8df76362b7c6270d926bf9e',
    'local/migration72/d02-resume-20261111b/proud/interface-metadata-query.private.json': 'b6ea32ac9bc5a5c96019cf5ea90541572502bbbf3f1e14f9ca273d620847cad3',
    'local/migration72/d02-resume-20261111b/proud/store2/code.bin': 'dee346d2bb53b96567d9de97db6ffca4c7cc6354d8f3e3033fd64a4aedfe2389',
}
FIVE = {
    'YuanShen.exe': '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5',
    'YuanShen_Data/Managed/Metadata/global-metadata.dat': '45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a',
    'YuanShen_Data/Managed/Metadata/startup-metadata.dat': 'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105',
    'YuanShen_Data/Plugins/AccountPlatNative.dll': 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25',
    'YuanShen_Data/Plugins/Astrolabe.dll': '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if Path(args.output).name != args.output or not args.output.endswith('.json'):
        parser.error('use a new task-local JSON basename')
    output = OUT / args.output
    if output.exists():
        parser.error('output already exists')
    fixed = {p: checked(ROOT / p, h) for p, h in PINS.items()}
    records, cells = pickle.loads(fixed[next(p for p in PINS if p.endswith('.pkl'))])
    aot = json.loads(fixed[next(p for p in PINS if 'closed-aot-' in p)])
    observed = json.loads(fixed[next(p for p in PINS if 'interface-metadata-query' in p)])
    client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                 '-File', str(ROOT / 'tools/game_path.ps1')], text=True).strip())
    inputs = {p: checked(client / p, h) for p, h in FIVE.items()}
    binary, metadata, startup = [inputs[p] for p in list(FIVE)[:3]]
    pe = PE(binary)
    spans = []

    def raw(artifact, offset, width, label):
        data = {'binary': binary, 'metadata': metadata, 'startup': startup}[artifact]
        value = data[offset:offset + width]
        if len(value) != width:
            raise ValueError('short static source: ' + label)
        spans.append(dict(artifact=artifact, offset=hex(offset), width=width, label=label,
                          hex=value.hex(), sha256=hashlib.sha256(value).hexdigest()))
        if sum(x['width'] for x in spans) > 1536:
            raise ValueError('static type-resolution byte budget exceeded')
        return value

    header = pe.offset(HEADER_VA)
    registration = pe.offset(METADATA_REGISTRATION)
    references = int.from_bytes(raw('binary', registration, 8, 'type-reference base'), 'little')
    reference_count = (int.from_bytes(raw('binary', registration + 0x8c, 4, 'reference count'), 'little') + 0xce67355c) & 0xffffffff
    instances = int.from_bytes(raw('binary', registration + 0x70, 8, 'generic instances'), 'little')
    generic_base = (int.from_bytes(raw('binary', header + 0xd0, 4, 'generic table base'), 'little') + 0xe1492096) & 0xffffffff
    generic_size = (int.from_bytes(raw('binary', header + 0x44, 4, 'generic table bytes'), 'little') + 0xe42e197f) & 0xffffffff
    if generic_size == 0 or generic_size % 8:
        raise ValueError('generic table extent differs')
    # Reuse the established resolver with lazy selected-instance arguments;
    # its eager constructor would inventory unrelated generic instances.
    resolver = object.__new__(TypeResolver)
    resolver.pe, resolver.startup, resolver.records = pe, startup, records
    resolver.references, resolver.reference_count = references, reference_count
    resolver.reference_base = pe.offset(references, reference_count * 16)
    resolver.instances, resolver.generic_base = instances, generic_base
    resolver.generic_count, resolver.cache = generic_size // 8, {}
    resolver.arguments = SelectedArguments(resolver, raw)
    resolutions = {str(i): resolver.resolve_index(i) for i in (500948, 500949, 447289)}
    for i, result in sorted(resolver.cache.items()):
        raw('binary', resolver.reference_base + i * 16, 16, 'type reference ' + str(i))
        if result['kind'] == 'genericinst':
            raw('startup', generic_base + result['genericIndex'] * 8, 8, 'generic row ' + str(result['genericIndex']))
    for row in observed['rows']:
        if metadata[row['rowOffset']:row['rowOffset'] + 70].hex() != row['rowHex']:
            raise ValueError('fixed interface metadata row differs')
    interface, = [v for v in resolutions.values() if v.get('typeDefinition') == 20037]
    if [(v['kind'], v['data']) for v in interface['arguments']] != [('var', '0xb0e'), ('var', '0xb0f')]:
        raise ValueError('interface key/value parameter identity differs')
    dictionary = cells[0x145935d18]
    if (dictionary['type']['typeDefinition'], dictionary['type']['instanceIndex']) != (1222, 50461):
        raise ValueError('Store Dictionary usage differs')
    target = selected_insert(metadata, aot)
    slot = int(target['pointerSlot'], 16)
    pointer = raw('binary', pe.offset(slot, 8), 8, 'selected TryInsert pointer')
    if int.from_bytes(pointer, 'little') != 0x1593e7bf0:
        raise ValueError('Store direct callee does not match closed TryInsert pointer')
    target['pointerQWORD'] = pointer.hex()
    result = dict(task='R3-PROUD-ACTUAL-ADOPTION/STATIC-SOURCES', sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  pins=PINS, inputSHA256=FIVE, selectedTypeReferences=resolutions, rawResolutionSpans=spans,
                  rawResolutionBytes=sum(x['width'] for x in spans), dictionaryUsage=dictionary, insertTarget=target,
                  declaredInterfaceProven=True, actualInterfaceOffsetOrVtableProven=False,
                  naturalDispatchObserved=False, actualMapHit=False, nativeCPU=0, live=0, resourceReady=False)
    encoded = (json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode()
    with output.open('xb') as stream:
        stream.write(encoded)
    print(json.dumps(dict(bytes=len(encoded), sha256=hashlib.sha256(encoded).hexdigest(), rawResolutionBytes=result['rawResolutionBytes'], insertTarget=target['address'])))


def checked(path, expected):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('frozen source SHA differs: ' + path.name)
    return data


class SelectedArguments(dict):
    """Read only argument arrays reached by the three selected type references."""
    def __init__(self, resolver, raw):
        self.resolver, self.raw = resolver, raw

    def __missing__(self, instance):
        resolver = self.resolver
        data = self.raw('binary', resolver.pe.offset(resolver.instances + instance * 16, 16), 16,
                        'generic instance ' + str(instance))
        count, pointer = struct.unpack('<QQ', data)
        if not 0 < count <= 16:
            raise ValueError('selected generic argument count exceeds bounded scope')
        args = self.raw('binary', resolver.pe.offset(pointer, count * 8), count * 8,
                        'generic argument pointers ' + str(instance))
        result = [resolver.pointer_index(v[0]) for v in struct.iter_unpack('<Q', args)]
        self[instance] = result
        return result


def selected_insert(metadata, aot):
    """Join one declared Dictionary method and class tuple in existing tables."""
    tables = aot['tables']
    for table in ('genericMethods', 'methodSpecs'):
        row = tables[table]
        offset = int(row['offset'], 16)
        if hashlib.sha256(metadata[offset:offset + row['bytes']]).hexdigest() != row['sha256']:
            raise ValueError('fixed AOT metadata table differs')
    table_base = int(tables['genericMethods']['offset'], 16)
    spec_base = int(tables['methodSpecs']['offset'], 16)
    selected = []
    for i in range(tables['genericMethods']['count']):
        entry = metadata[table_base + i * 14:table_base + (i + 1) * 14]
        pointer, spec = struct.unpack_from('<ii', entry)
        spec_raw = metadata[spec_base + spec * 12:spec_base + (spec + 1) * 12]
        if struct.unpack('<iii', spec_raw) != (10196, 50461, -1):
            continue
        selected.append(dict(owner=1222, method=10196, name='TryInsert', classInstance=50461,
                             methodInstance=-1, tableIndex=i, tableRecord14=entry.hex(), methodSpec=spec,
                             specRecord12=spec_raw.hex(), pointerIndex=pointer,
                             pointerSlot=hex(int(tables['pointerBase'], 16) + pointer * 8), address='0x1593e7bf0'))
    if len(selected) != 1:
        raise ValueError('TryInsert closed tuple is not unique')
    return selected[0]


if __name__ == '__main__':
    main()
