"""Offline exact cold EqualityComparer input, RuntimeType cache and selector."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .extract_client72_signatures import annotate_generic_positions, bind_class_parameters
    from .client72_session_runtime import fragment
    from .client72_avatar_contract import RETURN
    from .client72_emulator import STACK
    from .client72_seed_crypto import import_slots
except ImportError:
    import client72_avatar_deep as base
    from extract_client72_signatures import annotate_generic_positions, bind_class_parameters
    from client72_session_runtime import fragment
    from client72_avatar_contract import RETURN
    from client72_emulator import STACK
    from client72_seed_crypto import import_slots

ENTRIES = (0x140517680, 0x140538230, 0x14053C1C0, 0x1404F0740, 0x1404F0820,
           0x157D7FE90, 0x159B6E720)
DESCRIPTORS = (0x691310, 0x691320, 0x691330, 0x691340, 0x691350)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if args.output.resolve() == args.observations.resolve() or any(path.resolve().is_relative_to(args.client.resolve()) for path in (args.output, args.observations)):
        parser.error('Distinct output paths outside client required')
    b, m, s = [base.read_verified(args.client / name, digest) for name, digest in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    declared = declarations(b, m, s, records, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(declared, indent=2) + '\n', 'utf8')
    result = probe(b, records, declared, args)
    root = Path(__file__).resolve().parents[1]
    report = dict(task='F-03d3f4d2-cold-contract', inputSHA256=base.HASHES, declarations=declared, native=result,
        completeColdComparer=False, completeConstructor=False, completeAvatarHandler=False,
        runtimeStatus='not-tested', targetProtocolReady=False,
        sourceSHA256={name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
            'tools/client72_avatar_cold_contract.py', 'tools/extract_client72.py', 'tools/extract_client72_signatures.py',
            'tools/client72_emulator.py', 'tools/client72_session_runtime.py', 'tools/client72_seed_crypto.py')},
        models=['Initialized typed class/cache/MethodInfo/RGCTX and RuntimeType wrapper memory are explicit managed fixtures.',
            'Native type resolution, type hash/compare, sparse cache lookup and hit wrapper execute without success models.',
            'Only imported AcquireSRWLockShared/ReleaseSRWLockShared are single-thread ownership models; real OS locks do not run.',
            'Comparer TypeCode switch is an explicit live-in fragment; reflection-derived TypeCode and generic comparer creation are not executed.'],
        unresolved=['Cold reflection-cache miss allocation/insertion is stopped before typed allocation.',
            'HasIEquatable, enum underlying RuntimeType/TypeCode production, MakeGenericType and comparer construction remain required.',
            'The full cold comparer default-cache write and natural initialization are not established.'])
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    args.observations.parent.mkdir(parents=True, exist_ok=True)
    args.observations.write_text(json.dumps(compact(report), indent=2) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(result), completeColdComparer=False)))


def declarations(binary, metadata, startup, records, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = base.PE(binary)
    h = pe.offset(base.HEADER_VA)
    resolver = base.TypeResolver(pe, startup, records, h)
    target_index = resolver.arguments[5917][0]
    target_type = resolver.resolve_index(target_index)
    if target_type.get('typeDefinition') != 20060:
        raise ValueError('Exact Avatar enum argument changed')
    class_base = base.BODY + ((base.number(binary, h + 0x50) + 0xe113ba5c) & 0xffffffff)
    class_size = (base.number(binary, h + 0x104) + 0x938d8470) & 0xffffffff
    ranges = dict(struct.iter_unpack('<II', metadata[class_base:class_base + class_size]))
    definitions = base.BODY + (base.number(binary, h + 0x9c) ^ 0x4b5cdca3)
    generic_base = base.BODY + ((base.number(binary, h + 0x94) + 0xd97e8a5c) & 0xffffffff)
    packed = ranges[1234]
    slots = []
    for slot in (0, 1, 2):
        word = base.number(metadata, definitions + ((packed & 0xffffff) + slot) * 4)
        opened = annotate_generic_positions(resolver.resolve_index(word & 0x3ffffff), metadata, generic_base)
        slots.append(dict(slot=slot, word=hex(word), kind=(word >> 26) & 31, type=bind_class_parameters(opened, [target_type])))
    if [row['kind'] for row in slots] != [2, 1, 2] or slots[1]['type'].get('typeDefinition') != 20060:
        raise ValueError('EqualityComparer typed context changed')
    methods = [dict(owner=owner['index'], ownerName=owner['name'], method=method) for owner in records for method in owner['methods']
               if method['registeredAddress'] in ('0x159b6e720', '0x159b6e5b0', '0x159b6eb50')]
    imports = import_slots(pe)
    locks = {name: hex(imports[name]) for name in ('AcquireSRWLockShared', 'ReleaseSRWLockShared')}
    if locks != {'AcquireSRWLockShared': '0x143bac0f8', 'ReleaseSRWLockShared': '0x143bac708'}:
        raise ValueError('Reflection lock imports changed')
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    extents = {pe.base + start: pe.base + end for start, end, _ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    functions = {}
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    for start in ENTRIES:
        end = extents[start]
        raw = binary[pe.offset(start):pe.offset(start) + end - start]
        functions[hex(start)] = dict(end=hex(end), nativeBytesSHA256=hashlib.sha256(raw).hexdigest(),
            instructions=[dict(address=hex(ins.address), instruction=ins.mnemonic + ' ' + ins.op_str) for ins in decoder.disasm(raw, start)])
    return dict(targetType=target_type, targetTypeIndex=target_index, targetDescriptor=hex(resolver.references + target_index * 16),
        typeReferenceBase=hex(resolver.references), equalityContext=slots, methods=methods,
        selectorTypes={hex(offset): resolver.resolve_index(offset // 16) for offset in DESCRIPTORS},
        lockImports=locks, functions=functions)


class ColdRuntime:
    def __init__(self, e):
        self.e = e
        self.lock = e.alloc(8)
        self.lock_depth = 0
        self.lock_calls = []
        for name, cell, action in (('acquire', 0x143BAC0F8, self.acquire), ('release', 0x143BAC708, self.release)):
            e.write(cell, e.model_stub(action))
        e.write(0x145BC9028, self.lock)
        e.write(0x145699598, 0, 'I')

    def invoke(self, entry, stops=None, **regs):
        self.e.write(STACK + 0x100008, RETURN)
        return fragment(self.e, entry, stops or {RETURN}, dict(rsp=STACK + 0x100008, **regs))

    def acquire(self):
        if self.e.reg('rcx') != self.lock or self.lock_depth:
            raise ValueError('Single-thread reflection shared-lock fixture mismatch')
        self.lock_depth = 1
        self.lock_calls.append('acquire')
        return 0

    def release(self):
        if self.e.reg('rcx') != self.lock or self.lock_depth != 1:
            raise ValueError('Single-thread reflection shared-lock fixture mismatch')
        self.lock_depth = 0
        self.lock_calls.append('release')
        return 0

    def cache(self, entries):
        """Construct the native sparse-map storage established by its reader."""
        e = self.e
        table, block, payload, values = e.alloc(128), e.alloc(64), e.alloc(16 * max(1, len(entries))), e.alloc(8 * max(1, len(entries)))
        by_position = {}
        for key, value in entries:
            self.invoke(0x1404F0740, rcx=key)
            position = e.reg('rax') & 63
            step = 1
            while position in by_position:
                position = (position + step) & 63
                step += 1
            by_position[position] = (key, value)
        e.write(table + 8, 63)
        e.write(table + 0x10, block)
        e.write(table + 0x18, block + 32)
        e.write(table + 0x28, block)
        e.write(table + 0x60, values)
        e.write(block, payload)
        e.write(block + 8, sum(1 << position for position in by_position))
        for index, (position, (key, value)) in enumerate(sorted(by_position.items())):
            e.write(payload + index * 16, key)
            e.write(payload + index * 16 + 8, index)
            e.write(values + index * 8, value)
        return table


def probe(binary, records, declared, args):
    e = base.Offline72(binary, args.python_libs, records, trace_instructions=True)
    e.uc.mem_map(RETURN & ~4095, 4096)
    e.uc.mem_write(RETURN, b'\x90')
    e.prepare()
    for entry in ENTRIES:
        e.allow_unwind(entry)
    e.allowed.append((0x1405269F0, 0x1405269F5))
    rt = ColdRuntime(e)
    result = []
    try:
        descriptor = int(declared['targetDescriptor'], 16)
        other, equivalent, byref = e.alloc(16), e.alloc(16), e.alloc(16)
        e.uc.mem_write(other, bytes(e.uc.mem_read(descriptor, 16)))
        e.uc.mem_write(equivalent, bytes(e.uc.mem_read(descriptor, 16)))
        e.uc.mem_write(byref, bytes(e.uc.mem_read(descriptor, 16)))
        e.write(other, 20124)
        e.write(equivalent + 8, e.read(equivalent + 8, 'I') | 0x600, 'I')
        e.write(byref + 8, e.read(byref + 8, 'I') | 0x40000000, 'I')
        hashes = {}
        for name, pointer in (('enum', descriptor), ('attributes', equivalent), ('collision', other), ('byref', byref)):
            trace = rt.invoke(0x1404F0740, rcx=pointer)
            hashes[name] = dict(value=e.reg('rax'), trace=trace)
        result.append(dict(name='type-hash-key-semantics', hashes=hashes))
        for name, key, entries in (
            ('cache-hit', descriptor, [(descriptor, 0xFEDCBA9876543210)]),
            ('cache-equivalent-type-different-pointer-attributes', equivalent, [(descriptor, 0xFEDCBA9876543210)]),
            ('cache-collision-hit', descriptor, [(other, 77), (descriptor, 0xFEDCBA9876543210)]),
            ('cache-collision-miss', descriptor, [(other, 77)]),
            ('cache-byref-distinct-miss', byref, [(descriptor, 0xFEDCBA9876543210)]),
            ('cache-empty-miss', descriptor, [])):
            table, key_slot, output = rt.cache(entries), e.alloc(8), e.alloc(8)
            e.write(key_slot, key)
            e.write(output, 0x123456789)
            trace = rt.invoke(0x14053C1C0, rcx=table, rdx=key_slot, r8=output)
            result.append(dict(name=name, found=bool(e.reg('rax') & 255), value=e.read(output), trace=trace))
        enum_class = e.alloc(512)
        e.uc.mem_write(enum_class + 0x10, bytes(e.uc.mem_read(descriptor, 16)))
        class_table = e.alloc((20060 + 1) * 8)
        e.write(class_table + 20060 * 8, enum_class)
        e.write(0x145BC8F18, class_table)
        method, owner, slots, comparer_class, comparer_cache = e.alloc(64), e.alloc(512), e.alloc(64), e.alloc(512), e.alloc(8)
        e.write(method, owner)
        e.write(owner + 0xc8, 1, 'B')
        e.write(owner + 0x30, slots)
        e.write(slots + 0x10, comparer_class)
        e.write(slots + 0x18, descriptor)
        e.write(comparer_class + 0xc8, 1, 'B')
        e.write(comparer_class + 0x38, comparer_cache)
        runtime_type_class = e.alloc(512)
        e.write(runtime_type_class + 0x10, 284)
        reflection = e.alloc(32)
        e.write(reflection, runtime_type_class)
        e.write(reflection + 0x10, enum_class + 0x10)
        table = rt.cache([(enum_class + 0x10, reflection)])
        e.write(0x145BC9068, table)
        trace = rt.invoke(0x157D7FE90, {0x159B6E720}, rcx=method)
        result.append(dict(name='cold-comparer-reaches-exact-helper-input', helperArgumentIsReflection=e.reg('rcx') == reflection,
            reflectionObjectClass=e.read(e.read(reflection) + 0x10), reflectionTypeIndex=e.read(e.read(reflection + 0x10)),
            defaultCacheStillNull=e.read(comparer_cache) == 0,
            lockCalls=list(rt.lock_calls), lockBalanced=rt.lock_depth == 0, trace=trace))
        e.write(0x145BC9068, rt.cache([]))
        rt.lock_calls.clear()
        trace = rt.invoke(0x157D7FE90, {0x1405382A9}, rcx=method)
        result.append(dict(name='cold-reflection-cache-before-allocation', defaultCacheStillNull=e.read(comparer_cache) == 0,
            lockCalls=list(rt.lock_calls), lockBalanced=rt.lock_depth == 0, trace=trace))
        # The exact fixed registration pointer supplies all selector descriptors.
        e.write(0x145BC8EE0, 0x1428A53C8)
        for code in (0, 5, 6, 7, 8, 9, 10, 11, 12, 13, 0xffffffff):
            trace = rt.invoke(0x159B6EA12, {0x159B6E9A2, 0x159B6EA6A}, eax=code)
            pointer = e.reg('rcx')
            offset = pointer - int(declared['typeReferenceBase'], 16)
            selected = declared['selectorTypes'][hex(offset)]
            result.append(dict(name='comparer-selector-' + str(code), typeCode=code,
                descriptorOffset=hex(offset), selectedType=selected['typeDefinition'], selectedName=selected['name'], trace=trace))
        return result
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=result,
            registers={name: hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r8', 'rsp')}, calls=e.calls), indent=2) + '\n', 'utf8')
        raise
    finally:
        e.close()


def compact(value):
    if isinstance(value, list):
        return [compact(item) for item in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {key: value[key] for key in ('entry', 'stop', 'completion', 'liveIn', 'instructions', 'nativePathSHA256', 'models')} | {
                'nativeEntries': sorted((set(map(hex, (*ENTRIES, 0x1405269F0, 0x15A4D6B60))) | {value['entry']}) & set(value['executedAddresses']))}
        return {key: compact(item) for key, item in value.items() if key != 'instructions' or not isinstance(item, list)}
    return value


if __name__ == '__main__':
    main()
