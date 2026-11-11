import struct
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import scene_mplevel_actual_codec9_header_reader_window as subject


class HeaderReaderGatesTest(unittest.TestCase):
    def test_caller_binding(self):
        subject.call_gate(subject.CALL_BYTES)
        with self.assertRaisesRegex(ValueError, 'caller E8'):
            subject.call_gate(bytes.fromhex('e8b55e0a00'))

    def test_actual_unique_exact_and_containing(self):
        base, rva = 0x140000000, 0x1000
        begin = subject.ENTRY-base
        row = struct.pack('<III', begin, begin+90, 0x1234)
        diag = subject.extent(row, base, rva)
        self.assertEqual(subject.choose(diag)['size'], 90)
        self.assertEqual(diag['exact'][0]['unwindRVA'], '0x1234')
        for table in (b'', row+row, struct.pack('<III', begin-1, begin+1, 0)+row):
            with self.assertRaisesRegex(ValueError, 'pdata missing or ambiguous'):
                subject.choose(subject.extent(table, base, rva))
        for end in (begin, begin+4097):
            with self.assertRaises(ValueError):
                subject.choose(subject.extent(struct.pack('<III', begin, end, 0), base, rva))
        with self.assertRaisesRegex(ValueError, 'unaligned'):
            subject.extent(b'x', base, rva)

    def test_pins_reject_size_and_digest_drift(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'input'
            p.write_bytes(b'fixed')
            subject.check(p, 5, subject.sha(b'fixed'))
            for n, h in ((4, subject.sha(b'fixed')), (5, subject.sha(b'other'))):
                with self.assertRaisesRegex(ValueError, 'pin mismatch'):
                    subject.check(p, n, h)

    def test_decode_contiguous_and_edges(self):
        rows = subject.decode(bytes.fromhex('e800000000750190c3'))
        self.assertEqual([r['size'] for r in rows], [5, 2, 1, 1])
        self.assertTrue(rows[0]['call'])
        self.assertEqual(rows[0]['directTarget'], hex(subject.ENTRY+5))
        self.assertTrue(rows[1]['branch'])
        self.assertEqual(rows[1]['directTarget'], hex(subject.ENTRY+8))
        with self.assertRaisesRegex(ValueError, 'incomplete decode'):
            subject.decode(b'\x0f')


def audit(first, second, destination):
    # Independent artifact audit: reconstruct coverage and edges directly with Capstone.
    import hashlib
    import json
    import sys
    sys.path.insert(0, str(subject.ROOT / 'local/migration72/python'))
    import capstone
    from capstone.x86_const import X86_OP_IMM, X86_OP_MEM, X86_REG_RIP
    require = subject.require
    dirs = [subject.OUT / first, subject.OUT / second]
    copies = [{n: (d/n).read_bytes() for n in subject.FILES} for d in dirs]
    require(copies[0] == copies[1], 'fresh artifacts differ')
    raw = copies[0]
    boundary, pre, report = (json.loads(raw[n]) for n in ('boundary.private.json', 'pre-body.private.json', 'extraction.private.json'))
    req, pins = subject.source_gate()
    require(boundary['pins'] == pre['pins'] == report['pins'] == pins, 'source provenance mismatch')
    require(boundary['stock'] == pre['stock'] == report['stock'] == req['stock'], 'stock provenance mismatch')
    require(boundary['stage'] == pre['stage'] == 'pdata-before-body' and report['stage'] == 'complete', 'stage mismatch')
    require(boundary['caller'] == pre['caller'] == report['caller'] == {'address': hex(subject.CALL), 'hex': subject.CALL_BYTES.hex(), 'target': hex(subject.ENTRY), 'savedInstructionBoundary': True}, 'caller mismatch')
    diag = boundary['exceptionDirectory']['diagnostic']
    require(diag == pre['exceptionDirectory']['diagnostic'] == report['exceptionDirectory']['diagnostic'], 'pdata provenance mismatch')
    require(len(diag['exact']) == len(diag['containing']) == 1 and diag['exact'] == diag['containing'], 'pdata not unique')
    row = diag['exact'][0]
    begin, end, unwind = struct.unpack('<III', bytes.fromhex(row['recordHex']))
    require(begin + 0x140000000 == subject.ENTRY and end-begin == row['size'] and row['unwindRVA'] == hex(unwind) and 0 < end-begin <= 4096, 'pdata raw triple mismatch')
    require(pre['selected'] == report['selected'] == row and pre['budget'] == report['budget'] == {'maxBodyBytes': 4096, 'bodyBytes': end-begin, 'maxInstructions': 2048, 'xdataBytes': 0}, 'budget mismatch')
    code, saved = raw['body.bin'], json.loads(raw['body.asm.private.json'])
    digest = hashlib.sha256(code).hexdigest()
    require(len(code) == end-begin and report['body']['sha256'] == digest and report['body']['instructions'] == len(saved) <= 2048, 'body mismatch')
    require(hashlib.sha256(raw['body.asm.private.json']).hexdigest() == report['assemblySHA256'], 'assembly hash mismatch')
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    cs.detail = True
    rebuilt = []
    cursor = subject.ENTRY
    for ins in cs.disasm(code, subject.ENTRY):
        b = bytes(ins.bytes)
        require(ins.address == cursor and code[cursor-subject.ENTRY:cursor-subject.ENTRY+len(b)] == b, 'decode gap')
        branch, call = ins.group(capstone.CS_GRP_JUMP), ins.group(capstone.CS_GRP_CALL)
        target = hex(ins.operands[0].imm) if (branch or call) and ins.operands and ins.operands[0].type == X86_OP_IMM else None
        rip = [hex(ins.address + ins.size + op.mem.disp) for op in ins.operands if op.type == X86_OP_MEM and op.mem.base == X86_REG_RIP]
        rebuilt.append({'address': hex(ins.address), 'size': ins.size, 'hex': b.hex(), 'mnemonic': ins.mnemonic, 'operands': ins.op_str, 'branch': branch, 'call': call, 'directTarget': target, 'ripTargets': rip})
        cursor += ins.size
    require(cursor == subject.ENTRY+len(code) and rebuilt == saved, 'assembly coverage or edges mismatch')
    require(report['calls'] == [r for r in saved if r['call']] and report['branches'] == [r for r in saved if r['branch']] and report['ripReferences'] == [r for r in saved if r['ripTargets']], 'edge index mismatch')
    outside = [r for r in saved if r['branch'] and (r['directTarget'] is None or not subject.ENTRY <= int(r['directTarget'], 16) < subject.ENTRY+len(code))]
    require(report['externalBranches'] == outside and report['sourceCoverage'] == ('incomplete-external-branches' if outside else 'complete-linear-window'), 'external coverage mismatch')
    require(report['passed'] and report['newBodies'] == 1 and not any(report[k] for k in ('nativeCPU', 'live', 'xdataBytes', 'streamBytes', 'newDataTables', 'newHelpers', 'sceneReady', 'D02Ready')), 'scope mismatch')
    require(report['toolSHA256'] == hashlib.sha256(Path(subject.__file__).read_bytes()).hexdigest(), 'extractor changed')
    result = {'passed': True, 'twoFreshFilesEqual': True, 'currentPEReads': 0, 'size': len(code), 'sha256': digest, 'instructions': len(saved),
              'externalBranches': len(outside), 'artifacts': {n: {'bytes': len(b), 'sha256': hashlib.sha256(b).hexdigest()} for n, b in raw.items()}}
    subject.save(subject.OUT/destination, result)
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    import sys
    if len(sys.argv) == 5 and sys.argv[1] == '--audit':
        for name in sys.argv[2:]:
            subject.require(Path(name).name == name, 'task-local basename required')
        audit(*sys.argv[2:])
    else:
        unittest.main()
