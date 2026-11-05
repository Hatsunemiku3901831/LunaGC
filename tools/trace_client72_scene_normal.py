"""B-02e3 bounded native normal-scene consumption; no client is started."""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, number, TypeResolver
    from .extract_client72_literals import Literals72, USAGE_REGISTRATION
    from .client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from .client72_scene_reader import SceneReader72
    from .client72_stage_runtime import StageRuntime72
    from .client72_session_runtime import fragment
    from .trace_client72_request_identity import stage_signature
    from .trace_client72_scene_wait import declarations as scene_declarations, encode_fixture, scalar_wire
except ImportError:
    from extract_client72 import HASHES, read_verified, extract, PE, HEADER_VA, number, TypeResolver
    from extract_client72_literals import Literals72, USAGE_REGISTRATION
    from client72_emulator import Offline72, OBJECT, GLOBAL, STACK, END
    from client72_scene_reader import SceneReader72
    from client72_stage_runtime import StageRuntime72
    from client72_session_runtime import fragment
    from trace_client72_request_identity import stage_signature
    from trace_client72_scene_wait import declarations as scene_declarations, encode_fixture, scalar_wire

ENTRY, TASK, LOOKUP, INSERT = 0x14CD67260, 0x14CD5F7B0, 0x1578B66F0, 0x1578B6A20
NEXT, NULL, RECYCLE = 0x153A18E80, 0x1404C2BE0, 0x146E05720
NATIVE = (ENTRY, TASK, 0x147B2DDF0, 0x147B2F350, 0x147B2F6A0, 0x147B2F460)
UNWIND = (0x146E055F0, LOOKUP, INSERT)
NAMES = ('nonzero', 'uint32-high-bit', 'uint32-maximum', 'decoded-zero', 'explicit-raw-zero',
         'stage-enabled', 'current-task-slot', 'collision-preserves-entry', 'missing-dictionary',
         'missing-pool', 'pool-null-result', 'missing-three-float', 'missing-next-owner', 'existing-key-boundary')


def declarations(binary, metadata, startup, records):
    pe = PE(binary); byid = {r['index']: r for r in records}
    scene = scene_declarations(binary, metadata, startup, records)
    stage = stage_signature(binary, metadata, startup, records)
    literal = Literals72(binary, metadata); base = number(binary, pe.offset(USAGE_REGISTRATION) + 0x48, 'Q')
    resolver = TypeResolver(pe, startup, records, pe.offset(HEADER_VA)); bindings = []
    for row, cell, source_index, target in ((701228, 0x145892570, 478317, 11064), (710071, 0x1458C3938, 106228, 75453)):
        key = (row * 0x57C15286 + 0x26DDEBDA) & 0xFFFFFFFF
        source = ((number(metadata, literal.usages_base + row * 8) + 0xABBDCABF) & 0xFFFFFFFF) ^ key ^ 0x2A357D90
        slot = number(metadata, literal.usages_base + row * 8 + 4) ^ key ^ 0x1855AA65
        resolved = resolver.resolve_index(source & 0x1FFFFFFF)
        if source >> 29 != 1 or base + slot * 8 != cell or source & 0x1FFFFFFF != source_index or resolved.get('typeDefinition') != target:
            raise ValueError('Scene task usage binding drift')
        bindings.append(dict(usageRow=row, cell=hex(cell), sourceIndex=source_index, typeIndex=target, type=resolved))
    iface, task = byid[11064], byid[75453]
    if iface['name'] != 'IAutoAllocRecycle' or iface['namespace'] != 'MoleMole' or task['name'] != 'NMCKLNDCNKA':
        raise ValueError('Scene task/interface identity drift')
    expected_fields = {16: 'string', 24: 'class', 32: 'class', 40: 'uint32', 44: 'uint32', 48: 'uint64',
                       56: 'valuetype', 60: 'uint32', 64: 'bool', 65: 'bool', 66: 'bool', 68: 'valuetype',
                       80: 'valuetype', 84: 'uint32', 88: 'uint32'}
    fields = [f for f in task['fields'] if f['offsetKind'] == 'instance']
    if {f['offset']: f['type']['kind'] for f in fields} != expected_fields:
        raise ValueError('Scene task instance layout drift')
    if next(f for f in fields if f['offset'] == 68)['type'].get('typeDefinition') != 2329:
        raise ValueError('Scene task Vector3 destination drift')
    for name, address in (('AutoAllocTypeFields', 0x147B2DDF0), ('OnPoolAllocated', 0x147B2F350)):
        if len([m for m in task['methods'] if m['name'] == name and m['registeredAddress'] == hex(address) and m['parameterCount'] == 0]) != 1:
            raise ValueError('Scene task interface method drift')
    if [(m['name'], m['virtualSlot'], m['parameterCount']) for m in iface['methods']] != [
            ('AutoAllocTypeFields', 0, 0), ('AutoRecycleTypeFields', 1, 0), ('OnPoolAllocated', 2, 0), ('OnBeforePoolRecycled', 3, 0)]:
        raise ValueError('Scene task interface slots drift')
    methods = []
    for entry in NATIVE:
        found = [(r, m) for r in records for m in r['methods'] if m['registeredAddress'] == hex(entry)]
        if len(found) != 1: raise ValueError('Scene native method owner drift')
        r, m = found[0]
        if r['index'] != (18753 if entry in (ENTRY, TASK) else 75453): raise ValueError('Scene native owner mismatch')
        methods.append(dict(entry=hex(entry), ownerType=r['index'], methodIndex=m['index'], name=m['name']))
    return dict(scene=scene, stage=stage, bindings=bindings, taskFields=fields, methods=methods)


def fixture(reader, name):
    e = reader.e
    decoded = {'uint32-high-bit': (0x80000000, 0x80000001, 0xFEDCBA98),
               'uint32-maximum': (0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF), 'decoded-zero': (0, 0, 0)}.get(name, (3, 17, 42))
    d94, d7c, da0 = decoded
    raw = ( ((d94 ^ 0xE011) - 0x5889) & 0xFFFFFFFF,
            (((d7c - 0xC0E3) & 0xFFFFFFFF) ^ 0x2D03),
            ((da0 ^ 0x6B98) - 0x45A5) & 0xFFFFFFFF )
    if name == 'explicit-raw-zero':
        raw = (0, 0, 0); decoded = (0x5889 ^ 0xE011, 0x2D03 + 0xC0E3, 0x45A5 ^ 0x6B98)
    values = {0x94: raw[0], 0x7C: raw[1], 0xA0: raw[2], 0x68: 0xFEDCBA9876543210,
              0x38: '正常场景\U0001f319\0', 0x20: [1, 0x80000000, 0xFFFFFFFF],
              0x18: {24: 1.25, 28: 3.75, 32: -2.5}}
    if name == 'missing-three-float': values.pop(0x18)
    wire = encode_fixture(values)
    if name == 'explicit-raw-zero': wire += b''.join(scalar_wire(t, 0) for t in (1, 7, 10))
    read = reader.run_scene(wire)
    stage = StageRuntime72(e, name == 'stage-enabled')
    try:
        owner = e.alloc(1024); dictionary = e.alloc(); entries = e.alloc(32 + 16 * 24); buckets = e.alloc(32 + 17 * 4)
        next_owner = e.alloc(1024); e.write(GLOBAL + 0xA400, 0 if name == 'missing-next-owner' else next_owner)
        e.write(GLOBAL + 0x9E90, 0)  # No timer/coroutine context is supplied; its native early-return executes.
        e.write(owner + 0x38, 0 if name == 'missing-dictionary' else dictionary)
        e.write(dictionary + 0x18, entries); e.write(dictionary + 0x20, buckets); e.write(dictionary + 0x3C, 0xFFFFFFFF, 'I')
        e.write(entries + 24, 16, 'I'); e.write(buckets + 24, 17, 'I')
        task_class = e.synthetic_type(0x1458C3938); iface = e.synthetic_type(0x145892570); interface_table = e.alloc(16)
        e.write(task_class + 0x80, interface_table); e.write(task_class + 0xC0, 1, 'H'); e.write(task_class + 0xBE, 32, 'H'); e.write(interface_table, iface)
        for slot, target in ((0, 0x147B2DDF0), (2, 0x147B2F350)):
            e.write(task_class + 0xD0 + slot * 8, target); e.write(task_class + 0xD0 + (32 + slot) * 8, e.alloc())
        task = e.alloc(); e.write(task, task_class)
        # Dirty fields demonstrate actual OnPoolAllocated resets, while coroutine references stay null.
        for off in (0x2C, 0x30, 0x34, 0x38, 0x3C, 0x40, 0x50, 0x54): e.write(task + off, 0xA5A5A5A5, 'I')
        old = e.alloc(); e.write(old, task_class); e.write(old + 0x58, 123, 'I')
        if name == 'current-task-slot': e.write(owner + 0x30, old)
        seed = None
        if name in ('collision-preserves-entry', 'existing-key-boundary'):
            seed_key = decoded[0] if name == 'existing-key-boundary' else decoded[0] + 17
            seed = dict(key=seed_key, hash=seed_key & 0x7FFFFFFF, value=hex(old))
            e.write(dictionary + 0x38, 1, 'I'); e.write(entries + 0x20, seed['hash'], 'I'); e.write(entries + 0x24, 0xFFFFFFFF, 'I')
            e.write(entries + 0x28, seed_key, 'I'); e.write(entries + 0x30, old)
            e.write(buckets + 0x20 + seed['hash'] % 17 * 4, 1, 'I')
        pool, pool_class = e.alloc(), e.alloc(512); e.write(pool, pool_class)
        e.write(GLOBAL + 0x5D60, 0 if name == 'missing-pool' else pool); borrowed = []
        def borrow():
            if e.reg('rcx') != pool or e.reg('rdx') != task_class: raise ValueError('Pool receiver/type mismatch')
            borrowed.append(True)
            return 0 if name == 'pool-null-result' else task
        e.write(pool_class + 0xF0, e.model_stub(borrow))
        info, context_class, context, binder = e.alloc(), e.alloc(), e.alloc(512), e.alloc()
        e.write(info, context_class); e.write(context_class + 0x30, context); e.write(context + 0x88, binder); e.write(0x145A1F980, info)
        e.models[0x1531FDB80] = lambda: 0  # Typed scene log observation sink only.
        for address in NATIVE: e.allow(address)
        for address in UNWIND: e.allow_unwind(address)
        trace = fragment(e, ENTRY, {NEXT, NULL, RECYCLE}, dict(rsp=STACK + 0x100008, rcx=owner, rdx=OBJECT))
        next_args = dict(receiverMatched=e.reg('rcx') == next_owner, decoded7c=e.reg('edx')) if trace['stop'] == hex(NEXT) else None
        message_unchanged = reader.snapshot() == read['storedObject']
        source_preserved = e.reg('rsi') == OBJECT if trace['stop'] == hex(NEXT) else None
        count = e.read(dictionary + 0x38, 'I')
        stored = [dict(hash=e.read(entries + 32 + i * 24, 'I'), next=e.read(entries + 36 + i * 24, 'i'),
                       key=e.read(entries + 40 + i * 24, 'I'), value=hex(e.read(entries + 48 + i * 24))) for i in range(count)]
        lookup_index = None
        if trace['stop'] == hex(NEXT):
            # This closed generic has a PE unwind extent, not a registered base method.
            e.uc.context_restore(e.context); e.calls, e.failure, e.steps = [], None, 0
            for register, value in (('rsp', STACK + 0x100008), ('rcx', dictionary), ('rdx', decoded[0])):
                e.uc.reg_write(getattr(e.R, 'UC_X86_REG_' + register.upper()), value)
            e.uc.emu_start(LOOKUP, END, timeout=20_000_000, count=20_000_000)
            if e.failure or e.reg('rip') != END: raise ValueError(e.failure or 'Native dictionary lookup did not return')
            lookup_index = e.reg('eax')
        return dict(name=name, decoded=list(decoded), raw94_7c_a0=list(raw), reader=read, trace=trace, stop=trace['stop'],
                    messagePointerPreserved=source_preserved, messageContentUnchanged=message_unchanged, nextArguments=next_args,
                    taskPointer=hex(task), oldTaskPointer=hex(old), currentTaskPointer=hex(e.read(owner + 0x30)),
                    previousTaskPointer=hex(e.read(owner + 0x10)), borrowed=len(borrowed),
                    taskRaw=dict(key58=e.read(task + 0x58, 'I'), value28=e.read(task + 0x28, 'I'),
                                 floatBits=[e.read(task + x, 'I') for x in (0x44, 0x48, 0x4C)],
                                 stringPointerMatched=e.read(task + 0x10) == e.read(OBJECT + 0x38),
                                 resetWords={hex(x): e.read(task + x, 'I') for x in (0x2C, 0x30, 0x34, 0x38, 0x3C, 0x50, 0x54)},
                                 flags40_41=[e.read(task + x, 'B') for x in (0x40, 0x41)]),
                    dictionary=dict(seed=seed, entries=stored, lookupIndex=lookup_index),
                    stage=dict(enabled=name == 'stage-enabled', writes=stage.writes, clockCalls=stage.clock_calls))
    finally:
        stage.close()


def validate_fixtures(rows):
    if len(rows) != len(NAMES) or {r['name'] for r in rows} != set(NAMES): raise ValueError('Incomplete normal scene fixtures')
    for r in rows:
        name = r['name']; decoded = r['decoded']; raw94, raw7c, rawa0 = r['raw94_7c_a0']
        required = {'uint32-high-bit': [0x80000000, 0x80000001, 0xFEDCBA98],
                    'uint32-maximum': [0xFFFFFFFF] * 3, 'decoded-zero': [0, 0, 0]}.get(name)
        if required is not None and decoded != required: raise ValueError('Missing uint32 boundary coverage')
        if name == 'explicit-raw-zero' and r['raw94_7c_a0'] != [0, 0, 0]: raise ValueError('Missing explicit raw zero wire coverage')
        if decoded != [((raw94 + 0x5889) & 0xFFFFFFFF) ^ 0xE011, ((raw7c ^ 0x2D03) + 0xC0E3) & 0xFFFFFFFF,
                       ((rawa0 + 0x45A5) & 0xFFFFFFFF) ^ 0x6B98]: raise ValueError('Raw integer decode mismatch')
        error = name in ('missing-dictionary', 'missing-pool', 'pool-null-result', 'missing-three-float', 'missing-next-owner')
        stop = hex(NULL if error else RECYCLE if name == 'existing-key-boundary' else NEXT)
        if r['stop'] != stop or r['trace']['stop'] != stop: raise ValueError('Native boundary mismatch')
        addresses = r['trace']['executedAddresses']
        if not all(hex(a) in addresses for a in (ENTRY, 0x14C8AE9A0, TASK)):
            raise ValueError('Missing actual normal scene entry/stage/task path')
        if set(map(hex, NATIVE + UNWIND)) & set(r['trace']['models']): raise ValueError('Native scene consumer replaced by model')
        null_call = {'missing-dictionary': 0x14CD5FB46, 'missing-pool': 0x14CD5FB4B,
                     'pool-null-result': 0x146E05718, 'missing-three-float': 0x14CD676D4, 'missing-next-owner': 0x14CD676D9}
        if error and hex(null_call[name]) not in addresses: raise ValueError('Wrong original null branch')
        if not r['messageContentUnchanged']: raise ValueError('Reader message mutated')
        expected_stage = [dict(index=10, size=4, bits=0x42F64000, source='0x14c8aea1e')] if name == 'stage-enabled' else []
        if r['stage']['writes'] != expected_stage or r['stage']['clockCalls'] != (['context', 'double-value'] if expected_stage else []):
            raise ValueError('Normal scene stage execution mismatch')
        if stop != hex(NEXT): continue
        if not all(hex(a) in addresses for a in NATIVE + UNWIND): raise ValueError('Incomplete native allocation/dictionary path')
        if not r['messagePointerPreserved'] or r['nextArguments'] != dict(receiverMatched=True, decoded7c=decoded[1]):
            raise ValueError('Next consumer pointer/argument mismatch')
        t = r['taskRaw']
        if (t['key58'], t['value28'], t['floatBits'], t['stringPointerMatched']) != (decoded[0], decoded[2], [0x3FA00000, 0xC0200000, 0x40700000], True):
            raise ValueError('Native task scalar/float/string mismatch')
        if any(t['resetWords'].values()) or t['flags40_41'] != ([0, 0] if name == 'current-task-slot' else [1, 1]):
            raise ValueError('Native allocation reset/task flags mismatch')
        if r['currentTaskPointer'] != r['taskPointer'] or r['previousTaskPointer'] != (r['oldTaskPointer'] if name == 'current-task-slot' else '0x0') or r['borrowed'] != 1:
            raise ValueError('Native task slot/borrow mismatch')
        d = r['dictionary']; index = d['lookupIndex']; entries = d['entries']
        if index != len(entries) - 1 or entries[index]['key'] != decoded[0] or entries[index]['value'] != r['taskPointer'] or entries[index]['hash'] != decoded[0] & 0x7FFFFFFF:
            raise ValueError('Native dictionary insertion/relookup mismatch')
        if d['seed'] and entries[0] != dict(d['seed'], next=-1): raise ValueError('Existing dictionary entry changed')
        source = r['reader']['storedObject']
        if [source['rawStoredScalars'][o] for o in ('0x94', '0x7c', '0xa0')] != r['raw94_7c_a0']:
            raise ValueError('Reader raw values differ from independent wire inputs')
        if source['rawStoredScalars']['0x68'] != 0xFEDCBA9876543210 or source['repeatedUint32'] != [1, 0x80000000, 0xFFFFFFFF] or source['stringUTF8Hex'] != '正常场景\U0001f319\0'.encode('utf8').hex():
            raise ValueError('Uint64/list/string source truncated')


def recover(binary, metadata, startup, records, libraries):
    declared = declarations(binary, metadata, startup, records)
    e = Offline72(binary, libraries, records, True); reader = SceneReader72(e)
    try:
        rows = [fixture(reader, name) for name in NAMES]
        code = {}
        for address in NATIVE + UNWIND:
            end = e.ends[address] if address in NATIVE else e.unwind_ranges[address]
            raw = binary[e.pe.offset(address, end - address, executable=True):][:end - address]
            code[hex(address)] = dict(size=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    finally:
        reader.close(); e.close()
    validate_fixtures(rows)
    return dict(schemaVersion=1, inputSHA256=HASHES, declarations=declared, nativeCode=code, fixtures=rows, fixtureCount=len(rows),
                runtimeStatus='not-tested', targetProtocolReady=False, boundary=hex(NEXT), boundaryExecuted=False,
                scope='reader original pointer -> stage10 -> native task allocation/reset/dictionary -> partial task consumption; stop before next query',
                models=['initialized metadata/classes and synthetic interface dispatch storage',
                        'pool borrow returns one explicitly supplied dirty task; null result also tested',
                        'fixed-capacity dictionary uint32 entries/buckets and generic binder storage; native lookup/insert execute',
                        'no timer/coroutine context; native 147B2F6A0 early-return executes',
                        'managed wire primitives/strings/list storage from SceneReader72; native reader arithmetic executes',
                        'typed log observation sink and StageRuntime72 explicit switch/array/clock'],
                outsideScope=['decoded94/7C/A0 business names/semantics', 'five untraced nested message contents',
                              'recycling existing key body, dictionary growth/custom comparer/free-list',
                              'timer/coroutine initialization and lifecycle', '153A18E80 body/return and later scene consumers',
                              'real network ordering/SDK/media/client/complete scene entry or D-02'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'summary-output'): p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--libraries', type=Path, default=Path('local/migration72/python')); a = p.parse_args()
    if a.output.resolve() == a.summary_output.resolve() or any(x.resolve().is_relative_to(a.client.resolve()) for x in (a.output, a.summary_output)):
        p.error('Distinct output paths outside the read-only client required')
    b, m, s = [read_verified(a.client / n, h) for n, h in HASHES.items()]
    records, _ = extract(b, m, startup=s, structures=True)
    report = recover(b, m, s, records, a.libraries)
    report['toolSourceSHA256'] = {n: hashlib.sha256((Path(__file__).resolve().parents[1] / n).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                                 for n in ('tools/trace_client72_scene_normal.py', 'tools/client72_scene_reader.py', 'tools/client72_stage_runtime.py')}
    summary = {k: v for k, v in report.items() if k != 'fixtures'}
    summary['observations'] = [{k: v for k, v in row.items() if k not in ('trace', 'reader')} for row in report['fixtures']]
    for path, value in ((a.output, report), (a.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(fixtures=len(report['fixtures']), boundary=report['boundary'], boundaryExecuted=False, runtimeStatus='not-tested')))


if __name__ == '__main__': main()
