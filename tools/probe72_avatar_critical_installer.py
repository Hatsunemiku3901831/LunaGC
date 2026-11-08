"""Bounded fixed-CN72 shared Avatar field -> original critical installer proof."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import pickle
import re
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT), str(ROOT / 'local/migration72/python')]
from tools import probe72_avatar_entry_resource_fields as entry
from tools.client72_emulator import GLOBAL, STACK, END
from tools.client72_resource_deep import RETURN
from tools.client72_session_runtime import SessionRuntime72

prior = entry.prior
RAW = ROOT / 'local/migration72/d02-resume-20261109/r3-critical-installer'
INSTALLER, DISPATCHER = 0x1498e2200, 0x150854240
GETTERS = {20: 0x1498e56c0, 22: 0x1498e7a60}
SLICE_START, SLICE_STOP = 0x1505f5cc5, 0x1505f5d30
MAX_HOOKS, MAX_CALL_HOOKS = 500_000, 2_000_000
SYMBOLS = {20: 'FIGHT_PROP_CRITICAL', 22: 'FIGHT_PROP_CRITICAL_HURT'}
SOURCE_FIELDS = {20: (313963, 0x98, 'critical'), 22: (313966, 0xa4, 'criticalHurt')}
PINS = {
    'tools/probe72_avatar_entry_resource_fields.py': 'd2a00bc4228a7b531b9804840b84fd6b75552046058b562692a96f10e09e5297',
    'tools/client72_avatar_fight_props.py': 'c8705c1caf39a1514e4c5922978f19ee628b327412d7ed95a9b87293b730af2d',
    'tools/client72_session_runtime.py': 'c6ff407e33416869a855cfa86e97c3f63a9d24019ac0f6e6dfc74a959bfe1439',
    'local/migration72/d02-resume-20261109/r3-avatar/main-third.json': '32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e',
    'local/migration72/evidence/avatar-fight-props-native.json': 'af225811efc26b2ed225d548ac6fd643cec2388d674899d3762b0228862f2793',
    'local/migration72/d02-resume-20261109/r3-critical-installer/installer-window.private.json': '8e8b65b20491c513f85eb51f0e7fa156b55d88095d09b228b34634c6c1c8ec9c',
    'local/migration72/d02-resume-20261109/r3-critical-installer/enum-getter-window.private.json': 'f871529fc3b038c5dd1aad61f7734a05828664d9102d1699d9169c449ef9207e',
    'local/migration72/d02-resume-20261109/r3-critical-installer/critical20-getter-window.private.json': '0b889fbbb99a7f3ede3df12daea76fdd7d9bdc2a6c2354d01b03cb936cbe665d',
    'local/migration72/d02-resume-20261109/r3-critical-installer/main-exact22.private.json': '1a7cd185ad494b952b881edb8cca78b53ba7c8f2fa926f1b81bf7643fb8c9358',
    'local/migration72/d02-resume-20261109/r3-critical-installer/enum-jump-table.private.json': 'dc8025f7a569b6233466938a604524a179c8befd5dd9de7f81595ee11a3a4daa',
    'local/migration72/d02-resume-20261109/r3-critical-installer/enum-declaration.private.json': '74a48121b7e6d3e8598d40ebcbbb773c6f247275fd2cce9be1bf53be77c6e9fe',
    'local/migration72/d02-resume-20261109/r3-critical-consumer/old-owner-window.private.json': '8e4d698e46bd1d8570adf4f8b167304ac4d23ff8a16d554164d29d47190e9def',
    'src/main/java/emu/grasscutter/data/excels/avatar/AvatarData.java': 'be326428e1c3eee90c1c588c35b8d8133e5e59fa81adaeba4f902b7a4d53a779',
    'src/main/java/emu/grasscutter/game/avatar/Avatar.java': '9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d',
    'src/main/java/emu/grasscutter/game/props/FightProperty.java': 'a7abcc59bbaa39476158ab7bb39af544494b17617514d60f687ba4052f88cb57',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def bits(value):
    return f'{struct.unpack("<I", struct.pack("<f", value))[0]:08x}'


class CriticalRuntime(prior.PromoteRuntime):
    """One cumulative bounded native entry, including reused Session setup."""

    def __init__(self, *args, **kwargs):
        self.native_calls = []
        super().__init__(*args, **kwargs)

    def call(self, address, stops=(), **registers):
        e = self.e
        remaining = MAX_HOOKS - self.call_instruction_total
        if remaining <= 0:
            raise ValueError('Cumulative critical research hook budget exhausted')
        self.compact_allowed_ranges()
        e.calls, e.failure, e.steps = [], None, 0
        live = dict(rsp=STACK + 0x100008)
        live.update(registers)
        e.write(live['rsp'], RETURN)
        for name, value in live.items():
            e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + name.upper()), value)
        stop_set = {RETURN, 0x1404c2be0, 0x1404c2650, 0x1404c2c70, *stops}
        e.allowed.extend((x, x + 1) for x in stop_set)
        counts, reached = Counter(), []

        def observe(ip):
            if ip in (0x1518bda40, 0x14c09f510, 0x14dbf1710, 0x14dd204b0, 0x14d868b30):
                raise ValueError('Unreviewed modifier or IFix branch reached')
            if ip in self.extents or ip in e.ends:
                self.bind_function(ip)
                self.call_order.append(dict(entry=hex(ip), rcx=hex(e.reg('rcx')), rdx=hex(e.reg('rdx'))))
            counts[ip] += 1
            if ip in stop_set:
                reached.append(ip)
                e.uc.emu_stop()

        e.before_instruction = observe
        limit = min(MAX_CALL_HOOKS, remaining)
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
                         executedAddresses={hex(k): v for k, v in sorted(counts.items())},
                         models=[row['address'] for row in e.calls], failure=e.failure)
            self.native_calls.append(trace)
        if e.failure or len(reached) != 1:
            raise ValueError(e.failure or 'Exact critical native stop not reached within cumulative budget')
        return trace


class ReusedCodecSession(SessionRuntime72):
    def __init__(self, runtime):
        self.runtime = runtime
        super().__init__(runtime.e, 5, 7, random_value=0x12345678)

    def call(self, address, receiver=None, argument=0):
        registers = dict(rdx=argument)
        if receiver is not None:
            registers['rcx'] = receiver
        self.runtime.call(address, **registers)
        return self.runtime.e.reg('rax')


def install_reused_codec(rt):
    e = rt.e
    allocation = {address: e.models[address] for address in (0x1405336c0, 0x140514ee0)}
    scalar_before = (e.read(GLOBAL + 0xe970), e.read(0x145872d30, 'I'))
    begin = rt.call_instruction_total
    session = ReusedCodecSession(rt)
    e.models.update(allocation)
    # Float30710's own reviewed static table alias is installed by FightRuntime.
    # The two other aliases are the existing AvatarRuntime codec fixtures.
    for offset in (0xbed0, 0x31e60, 0x25a50):
        e.write(GLOBAL + offset, e.read(GLOBAL + 0x204d0))
    if scalar_before != (e.read(GLOBAL + 0xe970), e.read(0x145872d30, 'I')):
        raise ValueError('30710 setup modified the independent resource54724 codec')
    return dict(session=session, proof=dict(source='Existing FightProps AvatarRuntime/SessionRuntime72 fixture',
        multiplier=5, shift=7, randomNext=0x12345678, aliases=['0xbed0', '0x31e60', '0x25a50'],
        globalTableOffset='0x204d0', resourceScalarCodecPreserved=True,
        resourceAllocationModelsRestored=True, nativeInitializationHooks=rt.call_instruction_total - begin,
        naturalRandomObserved=False, newSuccessModel=False))


def static_contract(rt):
    windows = [json.loads((RAW / name).read_bytes())['windows'][0] for name in (
        'installer-window.private.json', 'enum-getter-window.private.json', 'critical20-getter-window.private.json')]
    windows.append(json.loads((RAW / 'main-exact22.private.json').read_bytes()))
    out = []
    for win in windows:
        address, end = int(win['start'], 16), int(win['end'], 16)
        rt.bind_function(address)
        source = rt.functions[hex(address)]
        if source['end'] != hex(end) or source['sha256'] != win['sha256']:
            raise ValueError('Reviewed installer/dispatcher/getter window changed')
        out.append({key: win[key] for key in ('start', 'end', 'bytes', 'sha256', 'metadata')})
    table = json.loads((RAW / 'enum-jump-table.private.json').read_bytes())['table']
    for prop, destination in ((20, 0x150854577), (22, 0x150854595)):
        row = next(x for x in table['entries'] if x['property'] == prop)
        if int(row['target'], 16) != destination or row['instructions'][-1]['instruction'] != 'jmp ' + hex(GETTERS[prop]):
            raise ValueError('Original signed enum dispatch target changed')
    actual = {v['symbol']: v['value'] for v in rt.enum_symbols[32419]['values'] if v['symbol'] in SYMBOLS.values()}
    expected = {symbol: prop for prop, symbol in SYMBOLS.items()}
    java = (ROOT / 'src/main/java/emu/grasscutter/game/props/FightProperty.java').read_text('utf8')
    java_values = {name: int(value) for name, value in re.findall(r'^    (FIGHT_PROP_\w+)\s*\((\d+)\)', java, re.M)}
    if actual != expected or {name: java_values[name] for name in expected} != expected:
        raise ValueError('Target original enum symbols and Java destinations differ')
    field_proof = []
    for prop, (index, offset, name) in SOURCE_FIELDS.items():
        field = next(f for f in rt.records[63287]['fields'] if f['index'] == index)
        if field['offset'] != offset or field['type'].get('typeDefinition') != 54724:
            raise ValueError('Shared source declaration changed')
        field_proof.append(dict(property=prop, symbol=SYMBOLS[prop], sourceOwner=63287, sourceField=field,
                                javaField=name, javaEnumValue=java_values[SYMBOLS[prop]]))
    rt.bind_function(0x1505f5420)
    shared = json.loads((ROOT / 'local/migration72/d02-resume-20261109/r3-critical-consumer/old-owner-window.private.json').read_bytes())['windows'][0]
    if rt.functions['0x1505f5420']['sha256'] != shared['sha256']:
        raise ValueError('Frozen shared resource slice changed')
    return dict(windows=out, enumType=32419, enumDeclarations=rt.enum_symbols[32419], fields=field_proof,
                sharedCallerSHA256=shared['sha256'], semanticNewWindows=4,
                receiverField=next(f for f in rt.records[59769]['fields'] if f['offset'] == 0x180))


def typed_receivers(rt):
    e = rt.e
    receiver = prior.equip.object_storage(rt, 24887)
    holder = prior.equip.object_storage(rt, 59769)
    component = prior.equip.object_storage(rt, 32376)
    e.write(holder + 0x180, receiver)
    e.write(receiver + 0x10, component)
    e.write(receiver + 0x608, 0, 'B')
    classes = {name: dict(type=rt.type_records[e.read(pointer)], classPointer=hex(e.read(pointer)),
                          allocatedBytes=int(rt.records[index]['fieldLayout']['rawSizeAndFlags'][0], 16))
               for name, pointer, index in (('receiver', receiver, 24887), ('holder', holder, 59769),
                                            ('component', component, 32376))}
    return dict(receiver=receiver, holder=holder, component=component, modifierFlag=False,
                classes=classes, controlledTypedStorage=True, naturalConstructorExecuted=False)


def named_readback(rt, store, expected):
    rows = []
    for prop in (20, 22):
        trace = rt.call(DISPATCHER, rcx=store['holder'], rdx=prop)
        actual = f'{rt.e.reg("xmm0") & 0xffffffff:08x}'
        if trace['stop'] != hex(RETURN) or actual != expected[prop]:
            raise ValueError('Complete typed named critical readback differs from independent bits')
        if hex(GETTERS[prop]) not in trace['executedAddresses'] or hex(0x152e90a60) not in trace['executedAddresses']:
            raise ValueError('Named readback did not execute original getter/codec')
        rows.append(dict(property=prop, symbol=SYMBOLS[prop], expectedBits=expected[prop], bits=actual, trace=trace))
    return rows


def run_installer_case(rt, name, inputs):
    store = typed_receivers(rt)
    trace = rt.call(INSTALLER, rcx=store['receiver'], **{f'xmm{i + 1}': int(word, 16) for i, word in enumerate(inputs)})
    if trace['stop'] != hex(RETURN):
        raise ValueError('Original critical installer did not return')
    readback = named_readback(rt, store, {20: inputs[0], 22: inputs[2]})
    middle = rt.call(0x152e90a60, rcx=store['receiver'] + 0x2e8)
    if f'{rt.e.reg("xmm0") & 0xffffffff:08x}' != inputs[1]:
        raise ValueError('Middle unnamed installer argument order changed')
    return dict(name=name, scope='complete-original-installer-and-typed-dispatcher',
                inputs=inputs, storage=store, installer=trace, namedReadback=readback,
                unnamedMiddleReadout=middle, middlePropertyAliasApproved=False)


def run_resource_case(rt, row, name, mutation=None):
    e = rt.e
    pointer = int(row['pointer'], 16)
    before = {offset: bytes(e.uc.mem_read(pointer + offset, 4)) for offset in (0x98, 0x78, 0xa4)}
    original = {int(f['offset'], 16): f['value']['bits'] for f in row['fields'] if f['offset'] in ('0x98', '0x78', '0xa4')}
    expected = {20: original[0x98], 22: original[0xa4]}
    if mutation == 'swap':
        e.uc.mem_write(pointer + 0x98, before[0xa4])
        e.uc.mem_write(pointer + 0xa4, before[0x98])
        expected = {20: original[0xa4], 22: original[0x98]}
    elif mutation == 'zero':
        if original[0x78] != '00000000':
            raise ValueError('Real row has no confirmed zero control field')
        e.uc.mem_write(pointer + 0x98, before[0x78])
        expected[20] = '00000000'
    store = typed_receivers(rt)
    try:
        trace = rt.call(SLICE_START, stops=(SLICE_STOP,), rsp=STACK + 0x100000,
                        rdi=pointer, rbx=store['receiver'])
        if trace['stop'] != hex(SLICE_STOP) or hex(INSTALLER) not in trace['executedAddresses']:
            raise ValueError('Shared field slice did not execute original installer')
        reads = named_readback(rt, store, expected)
        return dict(name=name, avatarId=row['key'], source=row['source'], mutation=mutation,
                    resourcePointer=row['pointer'], storage=store, trace=trace, namedReadback=reads,
                    shared63287FragmentOnly=True, completeAvatarNaturalPath=False,
                    originalEncodedWords={hex(k): v.hex() for k, v in before.items()})
    finally:
        for offset, value in before.items():
            e.uc.mem_write(pointer + offset, value)


def absent_receiver_case(rt):
    store = typed_receivers(rt)
    rt.e.write(store['holder'] + 0x180, 0)
    results = []
    for prop in (20, 22):
        trace = rt.call(DISPATCHER, rcx=store['holder'], rdx=prop)
        if trace['stop'] != hex(RETURN) or rt.e.reg('xmm0') & 0xffffffff:
            raise ValueError('Original absent receiver dispatcher boundary changed')
        if hex(GETTERS[prop]) in trace['executedAddresses']:
            raise ValueError('Absent receiver unexpectedly called property getter')
        results.append(dict(property=prop, bits='00000000', trace=trace))
    return dict(name='absent-receiver', storage=store, controlledMalformedStorage=True,
                resourceDefaultApproved=False, results=results)


def projection(row, cases):
    case = next(c for c in cases if c.get('avatarId') == row['key'] and c.get('mutation') is None)
    fields = {f['fieldIndex']: f for f in row['fields']}
    values, sources = {'id': row['key']}, []
    for prop, (index, offset, name) in SOURCE_FIELDS.items():
        value = fields[index]['value']
        actual = next(x['bits'] for x in case['namedReadback'] if x['property'] == prop)
        if value['bits'] != actual:
            raise ValueError('Projection lost native original field bits')
        values[name] = value['float32']
        sources.append(dict(javaField=name, bits=actual, sourceOwner=63287, sourceFieldIndex=index,
                            offset=hex(offset), targetProperty=prop, symbol=SYMBOLS[prop], source=row['source']))
    return dict(javaFields=values, fields=sources, fragmentOnly=True, fullDeclaredJavaInputRow=False,
                actualJavaLoaded=False, naturalAvatarConsumerExecuted=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to(RAW.resolve()):
        parser.error('A fresh output in the critical-installer private directory is required')
    pins, _, _ = prior.verify_inputs(args.client)
    for name, digest in (entry.PINS | PINS).items():
        prior.card.base.read_verified(ROOT / name, digest)
        pins[name] = digest
    binary, metadata, startup = [(args.client / n).read_bytes() for n in list(prior.card.FIVE_INPUTS)[:3]]
    records, cells = pickle.loads((prior.equip.RAW / 'metadata.private.pkl').read_bytes())
    codec = prior.card.natural_codec(ROOT / 'local/migration72/d02-active/plain/initialization-resources-elevated5.json')
    rt = CriticalRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python', 'Weapon', codec=codec)
    result = dict(task='E-01/R3-CRITICAL-INSTALLER', schemaVersion=1, sourcePins=pins,
                  sourceSHA256=sha(Path(__file__).read_bytes()),
                  testSourceSHA256=sha((ROOT / 'tools/test_probe72_avatar_critical_installer.py').read_bytes()), cases=[])
    try:
        result['staticContract'] = static_contract(rt)
        prior.equip.install_table(rt, 'Avatar')
        old = json.loads(prior.EQUIPMENT_REPORT.read_bytes())
        table = next(t for t in old['tables'] if t['name'] == 'Avatar')
        result['selectedRows'] = rows = entry.parse_selected(rt, table, entry.AVATARS, 'Avatar', 58272,
            0x14f9935d0, (prior.equip.SOURCE / 'Avatar.native-m_bytes.bin').read_bytes())
        result['codecModel'] = install_reused_codec(rt)['proof']
        result['cases'].append(run_installer_case(rt, 'independent-nonzero', ['3e000000', '3ec00000', '3fc00000']))
        result['cases'].append(run_installer_case(rt, 'independent-swapped', ['3fc00000', '3ec00000', '3e000000']))
        for row in rows:
            result['cases'].append(run_resource_case(rt, row, 'original-' + str(row['key'])))
        result['cases'].append(run_resource_case(rt, rows[0], 'birth5-swapped', 'swap'))
        result['cases'].append(run_resource_case(rt, rows[0], 'birth5-zero', 'zero'))
        result['cases'].append(absent_receiver_case(rt))
        result['javaProjectionFragments'] = [projection(row, result['cases']) for row in rows[:2]]
        result.update(nativeCalls=rt.native_calls, nativeCallTotals=rt.call_totals,
            nativeInstructionHookVisits=rt.call_instruction_total, nativeSources=rt.functions,
            completeAvatarNaturalPath=False, actualRuntimeConstructors=False, modifierBranchExecuted=False,
            targetDefaultApproved=False, completeAvatarInputRow=False, javaResourcesLoaded=False,
            activeRevisionVerified=False, resourceReady=False, d02Complete=False,
            sourceFieldAliasesApproved=True, caseLimit=8, hookLimit=MAX_HOOKS, singleCallLimit=MAX_CALL_HOOKS,
            scalarStorageCodec=codec['proof'], configStorage=rt.config_proof,
            modelScope=['Managed metadata/classes/hierarchy/arrays, memory libraries and UTF8 retain existing resource models.',
                'Resource54724 natural inverse snapshot/seed and reconstructed forward remain separate from reused30710 fixture.',
                'Reused30710 Random/TLS/permutation fixture is synthetic; all native initialization is counted by the sole call entry.',
                'Typed24887/59769/32376 storage has not run their constructors; nonnull32376 and+608=false are controlled inputs.',
                'Full installer, enum-typed dispatcher and two getters execute. Their two external modifier additions are disabled, not modeled successful.',
                'The original shared63287 resource slice starts after the sibling17494 guard; it is not a natural Avatar58272 caller.',
                'Null receiver is an explicit negative; natural target defaults, resource field absence and complete Java/runtime acceptance are not inferred.'])
    except Exception as error:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(error=str(error), partial=result,
            nativeFailure=rt.e.failure, nativeCalls=rt.native_calls, nativeCallTotals=rt.call_totals,
            nativeInstructionHookVisits=rt.call_instruction_total, nativeSources=rt.functions,
            registers={k: hex(rt.e.reg(k)) for k in ('rip', 'rsp', 'rcx', 'rdx', 'r8', 'r9')}), indent=2, sort_keys=True) + '\n', 'utf8')
        raise
    finally:
        rt.close()
    prior.equip.verify_client(args.client)
    result['fiveInputsRecheckedAfterNative'] = True
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(result['cases']), hooks=result['nativeInstructionHookVisits'],
                         approvedBirthFragments=len(result['javaProjectionFragments']), resourceReady=False)))


if __name__ == '__main__':
    main()
