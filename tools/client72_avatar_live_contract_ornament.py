"""Fixed CN72 Ornament parsers and exact-key candidate projection, offline only."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

try:
    from . import client72_avatar_deep as base
    from .client72_resource_deep import ResourceRuntime, ENCODE, RETURN, U32, GLOBAL, STACK
    from .extract_client72_enums import recover as recover_enums
except ImportError:
    import client72_avatar_deep as base
    from client72_resource_deep import ResourceRuntime, ENCODE, RETURN, U32, GLOBAL, STACK
    from extract_client72_enums import recover as recover_enums

GROUP, ORNAMENT = 0x152FE0BC0, 0x14A6FE2F0
CANDIDATES = {
    'AvatarOrnamentGroupExcelConfigData.json': ('f1a4885c2fdff06021263e4d6627feca4ff135547820e85a814eecb6495122da', 'b0e8dde4176a8f4fdd2dcd49ca4587c6676da470'),
    'AvatarOrnamentExcelConfigData.json': ('aad42c01df12aaa4956374226e26db11cdbd3d72a5586e4c9bf2e4415ae2287a', '4418e8163af74ca1ea06e76386e41bc1aca8cd70')}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'output', 'observations'):
        p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--python-libs', type=Path, default=Path('local/migration72/python'))
    p.add_argument('--candidate', type=Path, default=Path('local/migration72/d02-active/resources/candidate/ExcelBinOutput'))
    a = p.parse_args()
    if a.output.resolve() == a.observations.resolve() or any(x.resolve().is_relative_to(a.client.resolve()) for x in (a.output, a.observations)):
        p.error('Distinct outputs outside the client required')
    candidates = {}
    for name, (sha256, blob) in CANDIDATES.items():
        raw = (a.candidate / name).read_bytes()
        if hashlib.sha256(raw).hexdigest() != sha256 or hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() != blob:
            raise ValueError('Fixed Ornament candidate changed: ' + name)
        candidates[name] = dict(sha256=sha256, gitBlob=blob, bytes=len(raw), rows=json.loads(raw))
    b, m, s = [base.read_verified(a.client / name, digest) for name, digest in base.HASHES.items()]
    records, _ = base.extract(b, m, startup=s, structures=True)
    declared = declarations(b, m, records, a.python_libs)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.with_suffix('.static.json').write_text(json.dumps(declared, indent=2) + '\n', 'utf8')
    rt = ResourceRuntime(b, a.python_libs, records)
    try:
        rows = probe(rt, candidates, declared, a)
    finally:
        rt.close()
    source = ('tools/client72_avatar_live_contract_ornament.py', 'tools/client72_resource_deep.py',
              'tools/client72_emulator.py', 'tools/client72_session_runtime.py', 'tools/extract_client72.py')
    report = dict(task='F-03d3f4d2-ornament-parser', inputSHA256=base.HASHES, declarations=declared,
        candidateSources=candidates, native=rows, sourceSHA256={name: hashlib.sha256(Path(name).read_bytes()).hexdigest() for name in source},
        candidateCommitClaimFromResourceAcquisition='d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1',
        completeGroupScalarParser=True, completeOrnamentConditionParser=False, completeCacheBuilder=False,
        candidateRuntimeRevisionVerified=False, completeAvatarHandler=False, runtimeStatus='not-tested', targetProtocolReady=False,
        limitations=['All binary inputs are independent fixtures, not extracted client blobs; matching candidate keys authorize only the explicitly selected field-value projection.',
            'Original native parsers, scalar transformations and resource-ID encode/decode execute; managed byte-reader, arrays, string conversion and memory operations reuse the reviewed ResourceRuntime model.',
            'Candidate strings of 63/64 UTF8 bytes use the real bounded stack-buffer path; a synthetic 65-byte string stops before thread-local scratch allocation. Nested condition-array parser is not replaced by success.',
            'The cache-builder key fragment has explicit live-ins and stops before dictionary lookup; no whole builder or real loaded resource cache is claimed.',
            'No titleTextMapHash/nameTextMapHash/groupID/English alias or missing protagonist row is converted into default/empty production permission.'])
    a.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', 'utf8')
    a.observations.parent.mkdir(parents=True, exist_ok=True)
    a.observations.write_text(json.dumps(compact(report), indent=2, ensure_ascii=False) + '\n', 'utf8')
    print(json.dumps(dict(cases=len(rows), completeGroupScalarParser=True, resourceReady=False)))


def declarations(binary, metadata, records, libraries):
    base.load_emulator(libraries)
    from capstone import Cs, CS_ARCH_X86, CS_MODE_64
    pe = base.PE(binary)
    optional = base.number(binary, 0x3c) + 24
    rva, size = struct.unpack_from('<II', binary, optional + 112 + 3 * 8)
    ranges = {pe.base + begin: pe.base + end for begin, end, _ in struct.iter_unpack('<III', binary[pe.offset(pe.base + rva):pe.offset(pe.base + rva) + size])}
    decoder = Cs(CS_ARCH_X86, CS_MODE_64)
    functions = {}
    for entry in (GROUP, ORNAMENT, 0x15437AF90):
        end = ranges[entry]
        raw = binary[pe.offset(entry):pe.offset(entry) + end - entry]
        functions[hex(entry)] = dict(end=hex(end), nativeBytesSHA256=hashlib.sha256(raw).hexdigest(),
            instructions=[dict(address=hex(ins.address), instruction=ins.mnemonic + ' ' + ins.op_str) for ins in decoder.disasm(raw, entry)])
    expected = {(61286, 'DLGBPJPJMHP'): 0x1c, (44799, 'PBMHHJCEJPJ'): 0x2c,
                (44799, 'DENCEMCMLHM'): 0x18, (44799, 'EFFKPNKJHBP'): 0x20}
    for (owner, name), offset in expected.items():
        if next(field for field in records[owner]['fields'] if field['name'] == name)['offset'] != offset:
            raise ValueError('Exact candidate key/native field bridge changed')
    return dict(types=[records[index] for index in (61286, 44799, 58937)],
        logicEnum=recover_enums(binary, metadata, records, indices=(73624,))['enums'], functions=functions,
        exactCandidateKeys=[dict(owner=owner, name=name, offset=hex(offset)) for (owner, name), offset in expected.items()])


def group_bytes(values):
    mask = sum(bit for bit, key in ((0x40, 'group'), (4, 'avatar'), (0x10, 'plain'), (1, 'default')) if values.get(key) is not None)
    out = struct.pack('<H', mask ^ 0x56bc)
    for key in ('group', 'avatar', 'plain', 'default'):
        value = values.get(key)
        if value is None:
            continue
        raw = {'group': lambda: value ^ 0x05b6233b,
               'avatar': lambda: ((value ^ 0x32942559) - 0x5a8044f2) & U32,
               'plain': lambda: (value - 0x2b06fc55) & U32,
               'default': lambda: (value - 0x34259ba1) & U32}[key]()
        out += struct.pack('<I', raw)
    return out


def ornament_bytes(values, condition_array=False):
    mask = sum(bit for bit, key in ((0x10, 'id'), (8, 'text'), (0x20, 'group'), (4, 'plain'), (0x80, 'logic')) if values.get(key) is not None)
    mask |= 2 if condition_array else 0
    out = struct.pack('<H', ((mask + 0xe4a) & 65535) ^ 0x727c)
    if values.get('id') is not None:
        out += struct.pack('<I', (values['id'] - 0xc93c8186) & U32)
    if values.get('text') is not None:
        plain = values['text'].encode('utf8')
        out += struct.pack('<H', len(plain) ^ 0xeead)
        for start in range(0, len(plain), 8):
            chunk = plain[start:start + 8]
            out += ((int.from_bytes(chunk, 'little') - 0xe923d598fc68eead) & 0xffffffffffffffff).to_bytes(8, 'little')[:len(chunk)]
    for key in ('group', 'plain', 'logic'):
        value = values.get(key)
        if value is not None:
            raw = {'group': lambda: (value - 0x795aae9b) & U32,
                   'plain': lambda: value ^ 0x55127efa,
                   'logic': lambda: ((value ^ 0xa94fd967) - 0x846eb441) & U32}[key]()
            out += struct.pack('<I', raw)
    return out


def probe(rt, candidates, declared, args):
    e = rt.e
    for entry in (GROUP, ORNAMENT, 0x15437AF90):
        e.allow_unwind(entry)
    e.write(0x145B74D50, rt.array(0, 8))
    rows = []
    try:
        group_candidate = candidates['AvatarOrnamentGroupExcelConfigData.json']['rows'][0]
        for name, values in (
            ('group-omitted', {}), ('group-explicit-zero', dict(group=0, avatar=0, plain=0, default=0)),
            ('group-distinct-nonzero', dict(group=11, avatar=22, plain=33, default=44)),
            ('group-high-bits', dict(group=U32, avatar=0x80000001, plain=0xfffffffe, default=U32)),
            ('candidate-exact-DL-only', dict(default=group_candidate['DLGBPJPJMHP']))):
            obj = e.alloc(64)
            for offset in (0x10, 0x18, 0x1c):
                rt.call(ENCODE, rcx=obj + offset, rdx=777)
            e.write(obj + 0x14, 777, 'I')
            old_values = [rt.decode(obj + offset) for offset in (0x10, 0x18, 0x1c)] + [e.read(obj + 0x14, 'I')]
            data = group_bytes(values)
            _, cursor, trace = rt.parse(GROUP, data, obj)
            decoded = [rt.decode(obj + offset) for offset in (0x10, 0x18, 0x1c)]
            rows.append(dict(name=name, input=values, oldLogicalValues=old_values, binaryHex=data.hex(), cursor=cursor, decoded=decoded,
                plainField14=e.read(obj + 0x14, 'I'), trace=trace))
            scratch = STACK + 0xe0000
            dictionary = e.alloc(128)
            key_trace = rt.call(0x15437B12E, stops=(0x15437B14C,), r12=obj, rsi=dictionary,
                rbp=scratch + 0x18, r15=scratch)
            rows.append(dict(name=name + '-builder-key', encodedSourceOffset='0x18', key=e.reg('edx'),
                dictionaryArgumentPreserved=e.reg('rcx') == dictionary, trace=key_trace))
        candidate_rows = candidates['AvatarOrnamentExcelConfigData.json']['rows']
        cases = [('ornament-omitted', {}), ('ornament-explicit-zero', dict(id=0, group=0, plain=0, logic=0)),
                 ('ornament-distinct-nonzero', dict(id=42, group=73, plain=99, logic=2)),
                 ('ornament-high-bits', dict(id=U32, group=0x80000001, plain=0xfffffffe, logic=U32)),
                 ('ornament-short-unicode', dict(id=17, text='风-7.2', group=33, plain=55, logic=1))]
        cases += [('candidate-exact-PBM-' + str(row['PBMHHJCEJPJ']), dict(id=row['PBMHHJCEJPJ'])) for row in candidate_rows]
        enum_values = {row['symbol']: row['value'] for row in declared['logicEnum'][0]['values']}
        for row in candidate_rows:
            values = dict(id=row['PBMHHJCEJPJ'], text=row['DENCEMCMLHM'])
            if 'EFFKPNKJHBP' in row:
                values['logic'] = enum_values[row['EFFKPNKJHBP']]
            cases.append(('candidate-selected-exact-keys-' + str(row['PBMHHJCEJPJ']), values))
        for name, values in cases:
            data = ornament_bytes(values)
            obj = e.alloc(64)
            for offset in (0x2c, 0x24):
                rt.call(ENCODE, rcx=obj + offset, rdx=777)
            e.write(obj + 0x28, 777, 'I')
            e.write(obj + 0x20, 777, 'I')
            e.write(obj + 0x18, rt.string('old'))
            e.write(obj + 0x10, rt.array(3, 8))
            old_values = [rt.decode(obj + offset) for offset in (0x2c, 0x24)] + [e.read(obj + offset, 'I') for offset in (0x28, 0x20)]
            _, cursor, trace = rt.parse(ORNAMENT, data, obj)
            rows.append(dict(name=name, input=values, oldLogicalValues=old_values, oldText='old', oldConditionCount=3,
                binaryHex=data.hex(), cursor=cursor,
                id=rt.decode(obj + 0x2c), group=rt.decode(obj + 0x24), plainField28=e.read(obj + 0x28, 'I'),
                logic=e.read(obj + 0x20, 'I'), text=rt.strings[e.read(obj + 0x18)],
                conditionCount=e.read(e.read(obj + 0x10) + 24, 'I'), trace=trace))
        for name, data, stops in (
            ('nested-condition-array-before-parser', ornament_bytes({}, True), (0x14E7E6570,)),
            ('string-65-bytes-before-scratch-allocator', ornament_bytes(dict(text='X' * 65)), (0x14A6FE614, 0x14A6FE468))):
            obj, reader, array = e.alloc(64), e.alloc(64), e.alloc(32 + len(data))
            e.write(reader + 16, array)
            e.write(array + 24, len(data), 'I')
            e.uc.mem_write(array + 32, data)
            trace = rt.call(ORNAMENT, stops=stops, rcx=obj, rdx=reader)
            rows.append(dict(name=name, binaryHex=data.hex(), cursor=e.read(reader + 28, 'I'), trace=trace))
        _, cursor, trace = rt.parse(GROUP, bytes.fromhex('e956'))
        rows.append(dict(name='group-required-scalar-truncated', cursor=cursor, trace=trace))
        trace = rt.call(GROUP, rcx=e.alloc(64), rdx=0)
        rows.append(dict(name='group-null-reader', trace=trace))
        return rows
    except Exception:
        args.output.with_suffix('.failure.json').write_text(json.dumps(dict(completed=rows, calls=e.calls,
            registers={name: hex(e.reg(name)) for name in ('rip', 'rcx', 'rdx', 'r8', 'rsp')}), indent=2) + '\n', 'utf8')
        raise


def compact(value):
    if isinstance(value, list):
        return [compact(item) for item in value]
    if isinstance(value, dict):
        return {key: compact(item) for key, item in value.items() if key not in ('executedAddresses', 'binaryHex', 'instructions') or key == 'instructions' and not isinstance(item, list)}
    return value


if __name__ == '__main__':
    main()
