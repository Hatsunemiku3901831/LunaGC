"""Fixed CN72 Gadget +58 configuration-key route to the first comparer gap."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import pickle
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import probe72_weapon_gadget_bridge as bridge
from tools.client72_emulator import GLOBAL, STACK, END
from tools.client72_resource_deep import RETURN

prior = bridge.prior
RAW = ROOT / 'local/migration72/d02-resume-20261109/r3-gadget-config-key'
SCENE = ROOT / 'local/migration72/d02-active/main/scene-runtime-context-third.json'
OLD_CONFIG = ROOT / 'local/migration72/d02-active/scene-runtime-context-astra/config-producer-static.json'
OLD_BRIDGE = ROOT / 'local/migration72/d02-resume-20261109/r3-gadget-bridge/main-third.json'
QUERY, QUERY_END = 0x15154bff9, 0x15154c002
KEY_READER, CACHE_READER, FIND = 0x14aa9c630, 0x14aa9b8a0, 0x158b48be0
HASH, COMPARER_CARRIER_STOP = 0x159a8da20, 0x158b48cf6
HOOK_LIMIT = 500_000
PINS = {
    'tools/probe72_weapon_gadget_bridge.py': 'e51cd62c8cda9fd5804f4c088f821a084318cb950edb5a59b25d2b23d66580de',
    'tools/client72_scene_runtime_context.py': 'b5af21df00710e711b551f88507445e987eae998ba12e049e67a54204e19802e',
    'local/migration72/d02-active/main/scene-runtime-context-third.json': 'bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4',
    'local/migration72/d02-active/scene-runtime-context-astra/config-producer-static.json': 'bebaeba56c57e8335ddfe14e778dec41df765d85393fd5ec46f9d29d57c750c8',
    'local/migration72/d02-resume-20261109/r3-gadget-bridge/main-third.json': '51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880',
    'local/migration72/d02-resume-20261109/r3-gadget-config-key/two-direct-callees.private.json': '65302ea45325f125fc731b5282e8f03b9c2e8de3f43fd03d51936b505dfbeb97',
    'local/migration72/d02-resume-20261109/r3-gadget-config-key/lookup-windows.private.json': 'd392f4501a39cf2d3a4eb900fb066554022ec18979808648bd75fa39928ac6a1',
    'local/migration72/d02-resume-20261109/r3-gadget-config-key/string-hash-window.private.json': 'e229b0a3e5c9482db045997464ca02c841f2dd0f8613c880189f55b0fee1bd2b',
    'local/migration72/d02-resume-20261109/r3-gadget-config-key/resource-candidates.private.json': '30fffb027bce1494b277cde910ecadb2c60b05a6ffe2abc788315b37e9cdeab5',
    'local/migration72/d02-resume-20261109/r3-gadget-config-key/typed-bindings.private.json': 'b9e97eb64f970f815f8979c11368d78fbb76e007c39ca71a921d8e5884f7baa1',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('A fresh report in this card private directory is required')
    pins, _ = prior.equip.verify_inputs(args.client)
    old_bridge = json.loads(OLD_BRIDGE.read_bytes())
    candidates = json.loads((RAW / 'resource-candidates.private.json').read_bytes())
    for name, digest in (old_bridge['sourcePins'] | PINS | candidates['sourcePins']).items():
        path = args.client / name.removeprefix('GAME_PATH/') if name.startswith('GAME_PATH/') else ROOT / name
        prior.card.base.read_verified(path, digest)
        pins[name] = digest
    binary, metadata, startup = [(args.client / name).read_bytes() for name in list(prior.card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((prior.equip.RAW / 'metadata.private.pkl').read_bytes())
    scene, old = json.loads(SCENE.read_bytes()), json.loads(OLD_CONFIG.read_bytes())
    sources, static = source_contract(binary, records, scene, old, old_bridge)
    codec = prior.card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = ConfigKeyRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python',
                          'Weapon', codec=codec, approved_sources=sources)
    report = dict(task='E-01/R3-GADGET-CONFIG-KEY', schemaVersion=1, sourcePins=pins,
        sourceSHA256=sha(Path(__file__).read_bytes()),
        testSourceSHA256=sha((ROOT / 'tools/test_probe72_gadget_config_key.py').read_bytes()),
        staticContract=static, resourceCandidates=candidates, cases=[])
    try:
        report['rows'] = rows = parse_rows(rt, scene)
        report['ownedCacheFixture'] = install_forward_fixture(rt, old)
        for row in rows:
            for offset in (0x50, 0x58):
                pointer = rt.e.read(int(row['pointer'], 16) + offset)
                rt.e.write(pointer, rt.string_class)
        keys = [rt.e.read(int(row['pointer'], 16) + 0x58) for row in rows]
        report['cases'].append(run_case(rt, rows[0], 'original-50011101', False))
        report['cases'].append(run_case(rt, rows[1], 'original-90000004', False))
        report['cases'].append(run_case(rt, rows[0], '50011101-key-from-90000004', False, replacement=keys[1]))
        report['cases'].append(run_case(rt, rows[1], '90000004-key-from-50011101', False, replacement=keys[0]))
        report['cases'].append(run_case(rt, rows[0], '50011101-field50-used-as58', False,
            replacement=rt.e.read(int(rows[0]['pointer'], 16) + 0x50)))
        report['cases'].append(run_case(rt, rows[0], 'original-50011101-empty-cache', True))
        report['cases'].append(run_case(rt, rows[0], 'null-field58', False, replacement=0))
        report.update(sourceConfigurationKeyRoutingVerified=True, nativeConfigurationHitVerified=False,
            configurationOwnerLookupExecuted=False, finalConfigurationLookupExecuted=False, defaultComparerExecuted=False,
            javaJsonNameAliasApproved=False, javaProjectionFragments=[], field50AliasApproved=False,
            opaque25270ValueInstalled=False, configurationSourceBytesVerified=False, abilityInputsApproved=False,
            firstUnresolvedInput=unresolved_contract(), fullFactoryExecuted=False, completeGadgetInputRow=False,
            javaResourcesLoaded=False, activeRevisionVerified=False, resourceReady=False, d02Complete=False,
            modelScope=[
                'The frozen resource runtime supplies typed managed storage, metadata/RGCTX, allocation, UTF8 and paired scalar codec fixtures. No new consumer-success callback is installed.',
                'Runtime initialization binds the frozen Weapon loader and its storage/TLS fixture; no Weapon constructor, parser or loader is executed.',
                'Only real50011101/90000004 Gadget spans and their old logical readouts are replayed; no whole table, primary index, archive or runtime process is used.',
                'Actual parsed UTF16 strings receive the explicit metadata293 System.String vtable header. This is owned typed storage, not observed natural CLR initialization.',
                'The first cache is an explicit empty Dictionary<string,uint64> with comparer null. Bucket storage is present for bounded prefix cases and explicitly null for the missing-cache case. No source-key/hash or successful value is prefilled.',
                'The final equality-comparer carrier is intentionally not supplied. Prefixes stop before its first load; no DefaultComparer factory or unproved pointee executes.',
                'The old world factory query slice is a declared independent entry using the actual parsed row in RBX; allocator, pre-query registration call and entity lifecycle do not execute.',
                'No17591 configuration object/state or25270 configuration value is fabricated. Local JSON/ability and fixed upstream tree are candidates only; no external download or production resource change occurs.'])
        prior.equip.verify_client(args.client)
        report['fiveInputsRecheckedAfterNative'] = True
    except Exception as error:
        report['failure'] = dict(type=type(error).__name__, message=str(error), nativeFailure=rt.e.failure,
            registers={k: hex(rt.e.reg(k)) for k in ('rip', 'rsp', 'rbp', 'rcx', 'rdx', 'r8', 'rax')})
        raise
    finally:
        report.update(nativeCalls=rt.native_calls, nativeCallTotals=rt.call_totals,
            nativeInstructionHookVisits=rt.call_instruction_total, nativeSources=rt.functions,
            eachCallLimit=2_000_000, hookLimit=HOOK_LIMIT, scalarStorageCodec=codec['proof'],
            inheritedNativeConfigModeFixture=rt.config_proof)
        target = args.output.with_suffix('.failure.json') if 'failure' in report else args.output
        target.write_text(json.dumps(report, indent=2, sort_keys=True, ensure_ascii=False) + '\n', 'utf8')
        rt.e.close()
    print(json.dumps(dict(cases=len(report['cases']), calls=len(report['nativeCalls']),
                         instructionHookVisits=report['nativeInstructionHookVisits'], jsonNameApproved=False)))


def sha(data):
    return hashlib.sha256(data).hexdigest()


class ConfigKeyRuntime(bridge.GadgetBridgeRuntime):
    def __init__(self, *args, approved_sources, **kwargs):
        self.approved_sources = approved_sources
        super().__init__(*args, **kwargs)

    def bind_function(self, address):
        if address in self.e.models:
            return
        if address not in self.approved_sources:
            raise ValueError('Unreviewed original function outside the three-window contract: ' + hex(address))
        expected = self.approved_sources[address]
        super().bind_function(address)
        if self.functions[hex(address)]['sha256'] != expected['sha256'] or self.functions[hex(address)]['end'] != expected['end']:
            raise ValueError('Original range differs from frozen source: ' + hex(address))

    def call(self, address, stops=(), *, stack_return=True, **registers):
        e = self.e
        remaining = HOOK_LIMIT - self.call_instruction_total
        if remaining <= 0:
            raise ValueError('Gadget configuration-key cumulative native budget exhausted')
        self.compact_allowed_ranges()
        e.calls, e.failure, e.steps = [], None, 0
        live = dict(rsp=STACK + 0x100008)
        live.update(registers)
        if stack_return:
            e.write(live['rsp'], RETURN)
        for name, value in live.items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + name.upper()), value)
        stop_set = {RETURN, 0x1404c2be0, 0x1404c2650, 0x1404c2c70, *stops}
        e.allowed.extend((x, x + 1) for x in stop_set)
        counts, reached, observed, hashes = Counter(), [], [], []

        def observe(ip):
            counts[ip] += 1
            if ip in stop_set:
                reached.append(ip)
                e.uc.emu_stop()
                return
            if ip in self.extents or ip in e.ends:
                self.bind_function(ip)
                observed.append(dict(entry=hex(ip), rcx=hex(e.reg('rcx')), rdx=hex(e.reg('rdx')), r8=hex(e.reg('r8'))))
            if ip in (0x159a8da81, 0x159a8da96, 0x159a8daa3):
                hashes.append(dict(beforeRet=hex(ip), value=e.reg('eax')))

        e.before_instruction = observe
        limit = min(2_000_000, remaining)
        try:
            e.uc.emu_start(address, END, count=limit)
        finally:
            e.before_instruction = None
            total = self.call_totals.setdefault(hex(address), dict(calls=0, instructionHookVisits=0))
            total['calls'] += 1
            total['instructionHookVisits'] += e.steps
            self.call_instruction_total += e.steps
            trace = dict(entry=hex(address), stop=hex(reached[0]) if len(reached) == 1 else None,
                instructions=e.steps, countLimit=limit, liveIn={k: hex(v) for k, v in live.items()},
                executedAddresses={hex(k): v for k, v in sorted(counts.items())}, observedEntries=observed,
                nativeStringHashes=hashes, models=[row['address'] for row in e.calls], failure=e.failure,
                stackReturnWritten=stack_return, unexecutedStopHookIncluded=len(reached) == 1)
            self.native_calls.append(trace)
        if e.failure or len(reached) != 1:
            raise ValueError(e.failure or 'Exact configuration-key stop not reached within native budget')
        return trace


def source_contract(binary, records, scene, old, old_bridge):
    sources = {}

    def add(entry, end, digest):
        key, value = int(entry, 16), dict(end=end, sha256=digest)
        if key in sources and sources[key] != value:
            raise ValueError('Disagreeing frozen original range: ' + entry)
        sources[key] = value

    for entry, row in old_bridge['nativeSources'].items():
        add(entry, row['end'], row['sha256'])
    for row in scene['declarations']['methods']:
        add(row['entry'], row['end'], row['codeSHA256'])
    for gadget in scene['gadgetRows']:
        for entry, row in gadget['nativeSources'].items():
            add(entry, row['end'], row['sha256'])
    for row in old['methods']:
        add(row['entry'], row['end'], row['sha256'])
    reused = json.loads((RAW / 'two-direct-callees.private.json').read_bytes())['windows']
    for row in reused:
        assert sources[int(row['start'], 16)] == dict(end=row['end'], sha256=row['sha256'])
    new = [*json.loads((RAW / 'lookup-windows.private.json').read_bytes())['windows'],
           *json.loads((RAW / 'string-hash-window.private.json').read_bytes())['windows']]
    for row in new:
        add(row['start'], row['end'], row['sha256'])
    from tools.client72_scene_deep import PE
    pe = PE(binary)
    for entry, row in sources.items():
        end = int(row['end'], 16)
        data = binary[pe.offset(entry):pe.offset(entry) + end - entry]
        if sha(data) != row['sha256']:
            raise ValueError('Original frozen range bytes differ: ' + hex(entry))
    string_hash = next(m for m in records[293]['methods'] if m['index'] == 2922)
    assert string_hash['registeredAddress'] == hex(HASH) and string_hash['virtualSlot'] == 2
    fields = dict(field50=next(f for f in records[40472]['fields'] if f['offset'] == 0x50),
        field58=next(f for f in records[40472]['fields'] if f['offset'] == 0x58),
        keyToAsset=next(f for f in records[17586]['fields'] if f['rawOffset'] == '0x40213e8'),
        assetToOwner=next(f for f in records[17586]['fields'] if f['rawOffset'] == '0x40213f0'),
        ownerToConfig=next(f for f in records[17591]['fields'] if f['offset'] == 0x30))
    assert fields['keyToAsset']['type']['arguments'][0]['kind'] == 'string'
    assert fields['keyToAsset']['type']['arguments'][1]['kind'] == 'uint64'
    assert fields['ownerToConfig']['type']['arguments'][1]['typeDefinition'] == 25270
    bindings = json.loads((RAW / 'typed-bindings.private.json').read_bytes())
    return sources, dict(newWindowCount=3, newWindowBytes=sum(w['bytes'] for w in new), newWindows=new,
        reusedCalleesNotNew=reused, reusedConfigSourceSHA256=sha(OLD_CONFIG.read_bytes()), fields=fields,
        dictionarySharedAot=bindings['dictionarySharedAot'], equalityComparerSharedAot=bindings['equalityComparerSharedAot'],
        concreteContextSource=bindings['concreteTypesComeFrom'],
        stringType=293, stringHashMethod=string_hash, dictionaryComparerNotSupplied=True,
        firstUnexecutedCarrierLoad=hex(COMPARER_CARRIER_STOP), wholeFactoryExecuted=False)


def parse_rows(rt, scene):
    payload = (ROOT / 'local/migration72/d02-active/scene-runtime-context-astra/Gadget.native-m_bytes.bin').read_bytes()
    prior.bind_generics(rt, 0x152e109f0)
    rt.reader, rt.row_entry = rt.e.alloc(64), 0x14e62b290
    result = []
    for key in (50011101, 90000004):
        original = next(row for row in scene['gadgetRows'] if row['key'] == key)
        data = payload[original['offset']:original['offset'] + original['length']]
        assert sha(data) == original['sliceSHA256']
        array = rt.array(len(data), 1)
        rt.e.uc.mem_write(array + 0x20, data)
        rt.e.write(rt.reader + 0x10, array)
        rt.e.write(rt.reader + 0x1c, 0, 'I')
        obj = prior.equip.object_storage(rt, 40472)
        trace = rt.call(0x14e62b290, rcx=obj, rdx=rt.reader)
        assert trace['stop'] == hex(RETURN) and rt.e.read(rt.reader + 0x1c, 'I') == len(data)
        values = []
        for owner in (27264, 40472):
            for field in rt.records[owner]['fields']:
                offset, typ = field['offset'], field['type']
                if offset is None:
                    continue
                kind = typ['kind']
                if kind == 'string':
                    value = rt.strings.get(rt.e.read(obj + offset))
                elif typ.get('typeDefinition') == 58209:
                    value = rt.decode(obj + offset)
                elif kind in ('bool', 'uint32', 'int32', 'uint64'):
                    value = rt.e.read(obj + offset, {'bool': 'B', 'uint32': 'I', 'int32': 'i', 'uint64': 'Q'}[kind])
                    if kind == 'bool':
                        value = bool(value)
                elif kind == 'valuetype' and rt.records[typ['typeDefinition']]['parent'].get('typeDefinition') == 204:
                    value = rt.e.read(obj + offset, 'I')
                else:
                    continue
                values.append(dict(owner=owner, field=field, value=value))
        assert values == original['values']
        result.append(dict(key=key, pointer=hex(obj), source=dict(begin=original['offset'],
            end=original['offset'] + len(data), sha256=sha(data)), values=values, parser=trace,
            sourceLogicalReadoutEqual=True, constructorExecuted=False))
    return result


def install_forward_fixture(rt, old):
    e = rt.e
    for entry in (0x15154bf20, KEY_READER, CACHE_READER, 0x14aa97730, 0x14aa9af10, FIND, HASH):
        rt.bind_function(entry)
    field = next(f for f in rt.records[17586]['fields'] if f['rawOffset'] == '0x40213e8')
    klass = rt.bind_type(field['type'])
    context, lookup_info, info = e.alloc(256), e.alloc(64), e.alloc(64)
    e.write(klass + 0x30, context)
    e.write(lookup_info, klass)
    e.write(context + 0x78, lookup_info)
    e.write(info, klass)
    usage = next(u for u in old['usages'] if u['cell'] == '0x1459ae108')
    assert usage['ownerType'] == 1222 and usage['classInstance'] == 3744 and usage['method'] == 'TryGetValue'
    e.write(0x1459ae108, info)
    dictionary, buckets, entries = e.alloc(80), rt.array(7, 4), rt.array(4, 24)
    e.write(dictionary, klass)
    e.write(dictionary + 0x28, buckets)
    e.write(dictionary + 0x30, entries)
    e.write(GLOBAL + 0x213e8, dictionary)
    rt.forward_dictionary, rt.forward_buckets = dictionary, buckets
    rt.string_class = rt.bind_type(dict(kind='class', typeDefinition=293, name='String', namespace='System'))
    return dict(field=field, dictionary=hex(dictionary), klass=hex(klass), context=hex(context),
        lookupInfo=hex(lookup_info), methodInfo=hex(info), methodUsage=usage,
        buckets=hex(buckets), bucketCapacity=7, entryCapacity=4, entries=hex(entries),
        successfulEntriesPrefilled=0, comparerExplicitlyNull=True, comparerCarrierNotSupplied=True,
        naturalInitializationObserved=False, originalDictionaryInsertExecuted=False,
        configured17591OwnerCreated=False, opaque25270ValueCreated=False)


def run_case(rt, row, name, empty, replacement=None):
    e = rt.e
    obj = int(row['pointer'], 16)
    before = e.read(obj + 0x58)
    pointer = before if replacement is None else replacement
    e.write(obj + 0x58, pointer)
    e.write(rt.forward_dictionary + 0x28, 0 if empty else rt.forward_buckets)
    try:
        trace = rt.call(QUERY, stops=(QUERY_END, COMPARER_CARRIER_STOP), stack_return=False,
            rsp=STACK + 0x100000, rbx=obj)
        text = rt.strings.get(pointer)
        key_reader = next(x for x in trace['observedEntries'] if x['entry'] == hex(KEY_READER))
        assert int(key_reader['rcx'], 16) == pointer
        returns_null = empty or pointer == 0
        if returns_null:
            assert trace['stop'] == hex(QUERY_END) and e.reg('rax') == 0 and not trace['nativeStringHashes']
        else:
            assert trace['stop'] == hex(COMPARER_CARRIER_STOP)
            assert len(trace['nativeStringHashes']) == 1
            assert trace['nativeStringHashes'][0]['value'] == reference_hash(text)
            assert e.reg('r14d') == (reference_hash(text) & 0x7fffffff)
            hashed = next(x for x in trace['observedEntries'] if x['entry'] == hex(HASH))
            assert int(hashed['rcx'], 16) == pointer
        assert not trace['models']
        return dict(name=name, sourceKey=row['key'], source=row['source'], rowPointer=hex(obj),
            suppliedField58Pointer=hex(pointer), originalField58Pointer=hex(before), selectedText=text,
            independentFactorySlice=True, fullFactoryExecuted=False, controlledCacheEmpty=empty,
            trace=trace, nativeReturnedNull=returns_null, configurationHitVerified=False,
            stoppedBeforeUnprovenComparer=not returns_null,
            nativeStringHash=None if returns_null else trace['nativeStringHashes'][0]['value'])
    finally:
        e.write(obj + 0x58, before)
        e.write(rt.forward_dictionary + 0x28, rt.forward_buckets)


def reference_hash(text):
    encoded = text.encode('utf-16-le')
    units = [int.from_bytes(encoded[i:i + 2], 'little') for i in range(0, len(encoded), 2)]
    first, second = 5381, 5381
    for position, unit in enumerate(units):
        if position % 2:
            second = ((33 * second) ^ unit) & 0xffffffff
        else:
            first = ((33 * first) ^ unit) & 0xffffffff
    return (first + second * 0x5d588b65) & 0xffffffff


def unresolved_contract():
    return dict(firstInput='The actual EqualityComparer<string> carrier/default and original comparison path',
        stoppedBefore='0x158b48cf6', nextUnexecutedInstructions=[
            'MethodInfo -> klass+0x30 -> RGCTX+0x20', 'call0x15746c870', 'EqualityComparer<string> instance klass virtual slot8/+0x110'],
        typedComparer=dict(owner=1234, classInstance=4, methodDefinition=10288, method='get_Default',
            address='0x15746c870', actualClassOrDefaultInstanceNotSupplied=True),
        comparerFactoryCodeWindowOpened=False, defaultComparerPointeeInvented=False,
        configurationCaches=[
            dict(root='GLOBAL+0x213e8', type='Dictionary<string,uint64>/3744', key='Weapon_Sword_Metal', value='Original full64-bit registered MiHoYoBinData asset key; unknown'),
            dict(root='GLOBAL+0x213f0', type='Dictionary<uint64,17591>/11691', value='Original registered configuration owner; unknown'),
            dict(owner=17591, fields={'+0x28': 'state41431', '+0x2c': 'reference count', '+0x30': 'Dictionary<string,25270>/11687', '+0x40': 'registered uint64 asset key'})],
        requiredConfigurationSource=dict(assetType='MiHoYoBinData/type8026', key='Weapon_Sword_Metal',
            producer='Frozen14aa99dc0 GetAssetPathHash<MiHoYoBinData> registration',
            exactTargetAssetHashAndBundle='Not recovered; local aggregate names cannot supply them',
            bytes='Exact target configuration dictionary/source bytes and typed25270 parse result, not a local candidate or opaque success callback'),
        abilityCandidate='Local Avatar_Weapon_Metal exists; exact target configuration reference, target ability source/registration and execution remain unverified')


if __name__ == '__main__':
    main()
