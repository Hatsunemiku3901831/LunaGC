"""Fixed-input Avatar ornament resource identity and bounded loader probes."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .extract_client72_literals import Literals72
    from .client72_session_runtime import fragment
except ImportError:
    import client72_avatar_deep as base
    from extract_client72_literals import Literals72
    from client72_session_runtime import fragment

INPUTS = {'avatar-deep-suffix-observations.json': '5e03ba3486470055450cc4233f8cf3a500680569d874e5e8b1e1efad5b9e58c6'}
SPECS = ((28604, 29427, 0x145B035B8, 0x15437AD90, 0x15437AB80, 0x13B70, 'AvatarOrnamentGroupExcelConfigData'),
         (39027, 40297, 0x145B18968, 0x147366B70, 0x147366A40, 0x1CD98, 'AvatarOrnamentExcelConfigData'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    args = parser.parse_args()
    if args.output.resolve() == args.observations.resolve() or any(path.resolve().is_relative_to(args.client.resolve()) for path in (args.output, args.observations)):
        parser.error('Distinct outputs outside client required')
    for name, digest in INPUTS.items():
        if hashlib.sha256((Path('protocol/cn-7.2') / name).read_bytes()).hexdigest() != digest:
            raise ValueError('Reviewed suffix changed')
    b, m, s = [base.read_verified(args.client / name, digest) for name, digest in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    cells = base.type_cells(b, m, s, records)
    declarations = inspect(b, m, records, cells, args.python_libs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix('.static.json').write_text(json.dumps(declarations, indent=2) + '\n', 'utf8')
    rows = probe(b, records, declarations, args)
    root = Path(__file__).resolve().parents[1]
    report = dict(task='F-03d3f4d2-ornament-source', inputSHA256=base.HASHES, inputContracts=INPUTS,
        declarations=declarations, native=rows, resourceFileIOExecuted=False,
        completeAvatarHandler=False, completeConstructor=False, runtimeStatus='not-tested', targetProtocolReady=False,
        sourceSHA256={name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (
            'tools/client72_avatar_deep_ornaments.py', 'tools/client72_emulator.py', 'tools/client72_session_runtime.py',
            'tools/extract_client72.py', 'tools/extract_client72_literals.py')},
        models=['Initialized literal cells hold explicit managed string fixtures; native getter/loader instructions execute up to file I/O.',
                'No resource file parser/cache construction or real client is executed. Group/default/member field English names remain unapproved.'])
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    args.observations.parent.mkdir(parents=True, exist_ok=True)
    args.observations.write_text(json.dumps(compact(report), indent=2) + '\n', 'utf8')
    print(json.dumps(dict(resources=len(rows), resourceFileIOExecuted=False)))


def inspect(binary, metadata, records, cells, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    decoder.detail = True
    pe = base.PE(binary)
    literals = Literals72(binary, metadata)
    literal_rows = {row['index']: row for row in literals.group(literals.global_group)}
    starts = sorted({int(method['registeredAddress'], 16) for record in records for method in record['methods'] if method['registeredAddress'] != '0x0'})
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    rows = []
    for owner, index, cell, getter, loader, cache, name in SPECS:
        literal = literal_rows[index] | dict(value=literals.decode(index))
        if literal['cell'] != hex(cell) or literal['value'] != 'Data/_ExcelBinOutput/' + name:
            raise ValueError('Ornament resource literal changed')
        field = next(field for field in records[owner]['fields'] if field['rawOffset'] == hex(0x4000000 + cache))
        functions = []
        owner_cells = set()
        for entry in (getter, loader):
            method = next(method for method in records[owner]['methods'] if method['registeredAddress'] == hex(entry))
            end = ranges.get(entry) or next(address for address in starts if address > entry)
            raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
            instructions, references = [], []
            for ins in decoder.disasm(raw, entry):
                instructions.append(dict(address=hex(ins.address), instruction=ins.mnemonic + ' ' + ins.op_str))
                for operand in ins.operands:
                    if operand.type == 3 and operand.mem.base == 41 and ins.address + ins.size + operand.mem.disp == cell:
                        references.append(hex(ins.address))
                    if operand.type == 3 and operand.mem.base == 41:
                        target = ins.address + ins.size + operand.mem.disp
                        if target in cells and cells[target]['type'].get('typeDefinition') == owner:
                            owner_cells.add(hex(target))
            if not references:
                raise ValueError('Getter/loader lost exact literal reference')
            functions.append(dict(entry=hex(entry), end=hex(end), method=method, nativeBytesSHA256=hashlib.sha256(raw).hexdigest(),
                                  instructions=instructions, literalReferenceSites=references))
        rows.append(dict(owner=owner, literal=literal, cacheOffset=hex(cache), cacheField=field, functions=functions,
                         initializedOwnerCells=sorted(owner_cells)))
    return rows


def probe(binary, records, declarations, args):
    e = base.Offline72(binary, args.python_libs, records, trace_instructions=False)
    rows = []
    try:
        end = base.RETURN & ~0xfff
        e.uc.mem_map(end, 4096)
        e.uc.mem_write(base.RETURN, b'\xc3')
        e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_CODE, e._code, begin=end, end=end + 4095))
        for item in declarations:
            for cell in item['initializedOwnerCells']:
                e.synthetic_type(int(cell, 16))
            data = item['literal']['value'].encode('utf-16le')
            pointer = e.alloc(24 + len(data))
            e.write(pointer + 16, len(data) // 2, 'I')
            e.uc.mem_write(pointer + 20, data)
            e.write(int(item['literal']['cell'], 16), pointer)
            traces = []
            for index, function in enumerate(item['functions']):
                entry = int(function['entry'], 16)
                e.allow(entry)
                e.write(base.STACK + 0x100008, base.RETURN)
                trace = fragment(e, entry, {base.RETURN} if index == 0 else {0x14D6E1570}, {'rsp': base.STACK + 0x100008})
                received = e.reg('rax' if index == 0 else 'rcx')
                if received != pointer:
                    raise ValueError('Native ornament literal pointer differs')
                traces.append(trace)
            rows.append(dict(resource=item['literal']['value'], exactLiteralPointerPreserved=True, traces=traces))
        return rows
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=rows, calls=e.calls,
            registers={name: hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r8', 'rsp')}), indent=2) + '\n', 'utf8')
        raise
    finally:
        e.close()


def compact(value):
    if isinstance(value, list):
        return [compact(item) for item in value]
    if isinstance(value, dict):
        if 'executedAddresses' in value:
            return {key: value[key] for key in ('entry', 'stop', 'completion', 'liveIn', 'instructions', 'nativePathSHA256', 'models')}
        return {key: compact(item) for key, item in value.items() if key != 'instructions' or not isinstance(item, list)}
    return value


if __name__ == '__main__':
    main()
