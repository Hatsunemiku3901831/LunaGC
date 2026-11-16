import base64
import io
import json
import subprocess
import struct
import tempfile
import unittest
from unittest import mock
from pathlib import Path
from tools.migration72 import proud_float_direct_e8_index as mod


def call(site, target=mod.TARGET):
    return b'\xe8' + struct.pack('<i', target - site - 5)


def row(begin, end, unwind=3):
    return struct.pack('<III', begin, end, unwind)


class RawScanTests(unittest.TestCase):
    def test_signed_64_bit_and_wrong_target(self):
        for site, expected in [(0x14f900000, 'e83b510000'), (0x14f906000, 'e83bf1ffff')]:
            self.assertEqual(call(site).hex(), expected)
            self.assertEqual(len(list(mod.raw_candidates(call(site), site, 10))), 1)
            self.assertEqual(list(mod.raw_candidates(call(site, mod.TARGET + 1), site, 10)), [])

    def test_opcodes_end_and_false_instruction(self):
        site = 0x14f900000
        self.assertEqual(list(mod.raw_candidates(b'\xe9' + call(site)[1:] + b'\xff', site, 0)), [])
        self.assertEqual(list(mod.raw_candidates(call(site)[:4], site, 0)), [])
        self.assertEqual(len(list(mod.raw_candidates(call(site), site, 0))), 1)
        data = b'\xb8\x11\x22\x33' + call(site + 4)
        result = list(mod.raw_candidates(data, site, 0))
        self.assertEqual(result[0]['site'], site + 4)
        self.assertFalse(mod.FALSES['instructionBoundaryValidated'])

    def test_exclusions_and_non_executable_domain(self):
        site = 0x14f900000
        data = call(site) + call(site + 5)
        intervals = mod.subtract(site, site + 10, [{'start': site + 3, 'end': site + 6}])
        self.assertEqual(intervals, [(site, site + 3), (site + 6, site + 10)])
        self.assertEqual([r for a, b in intervals for r in mod.raw_candidates(data[a-site:b-site], a, a-site)], [])
        sections = [{'flags': 0, 'data': call(site)}, {'flags': 0x20000000, 'data': b'xxxxx'}]
        self.assertEqual([r for s in sections if s['flags'] & 0x20000000 for r in mod.raw_candidates(s['data'], site, 0)], [])


class ScanLedgerTests(unittest.TestCase):
    class TrackedFile(io.BytesIO):
        def __init__(self, data):
            super().__init__(data)
            self.reads = []

        def read(self, size=-1):
            offset = self.tell()
            data = super().read(size)
            self.reads.append((offset, size, len(data)))
            return data

    def test_candidate_cap_preserves_read_ledger_and_prefix(self):
        first = 0x14f900000
        second = 0x14f910000
        first_bytes = b''.join(call(first + 5*i) for i in range(2))
        second_bytes = b''.join(call(second + 5*i) for i in range(4095)) + b'\x90' * 9
        later_bytes = b'later-domain-and-pdata'
        domains = [
            {'begin': first, 'end': first + len(first_bytes), 'fileOffset': 0, 'bytes': len(first_bytes)},
            {'begin': second, 'end': second + len(second_bytes), 'fileOffset': len(first_bytes), 'bytes': len(second_bytes)},
            {'begin': second + 0x10000, 'end': second + 0x10000 + len(later_bytes),
             'fileOffset': len(first_bytes) + len(second_bytes), 'bytes': len(later_bytes)}]
        identity = {'bytes': 1234, 'sha256': 'synthetic'}
        header = {'readCalls': 2, 'bytesRead': 70, 'intervals': [{'offset': 0, 'bytes': 70}]}
        source = first_bytes + second_bytes + later_bytes
        expected = list(mod.raw_candidates(first_bytes, first, 0)) + list(mod.raw_candidates(second_bytes, second, len(first_bytes)))
        self.assertEqual(len(expected), 4097)
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp)
            f = self.TrackedFile(source)
            with self.assertRaisesRegex(ValueError, 'candidate limit'):
                mod.scan_domains(f, domains, dest, identity, header)
            self.assertEqual(f.reads, [(0, len(first_bytes), len(first_bytes)),
                (len(first_bytes), len(second_bytes), len(second_bytes))])
            original = (dest / 'stopped.private.json').read_bytes()
            report = json.loads(original)
            self.assertEqual(report['binaryHashRead'], identity)
            self.assertEqual(report['headerRead'], header)
            self.assertEqual(report['stopSite'], second + 4094 * 5)
            self.assertEqual(report['observedCandidateCount'], 4097)
            self.assertEqual(report['retainedCandidateCount'], 4096)
            self.assertFalse(report['completeIndex'])
            self.assertEqual(report['pdataReadBytes'], 0)
            self.assertEqual(report['execScan']['declaredDomains'], domains)
            self.assertEqual(report['execScan']['readCalls'], 2)
            self.assertEqual(report['execScan']['bytesReturned'], len(first_bytes) + len(second_bytes))
            reads = report['execScan']['reads']
            self.assertTrue(reads[0]['scanComplete'])
            self.assertFalse(reads[1]['scanComplete'])
            self.assertEqual(reads[1]['scannedThroughSiteInclusive'], report['stopSite'])
            self.assertEqual(reads[1]['returnedBytes'], len(second_bytes))
            expected_prefix = [dict(item, scanDomainBegin=first if item['site'] < second else second) for item in expected[:4096]]
            self.assertEqual(report['rawCandidates'], expected_prefix)
            self.assertTrue(all(report[k] is False for k in mod.FALSES))
            with self.assertRaises(FileExistsError):
                mod.scan_domains(self.TrackedFile(source), domains, dest, identity, header)
            self.assertEqual((dest / 'stopped.private.json').read_bytes(), original)

    def test_under_cap_preserves_candidates(self):
        site = 0x14f900000
        data = call(site) + b'\x90' + call(site + 6)
        domain = {'begin': site, 'end': site + len(data), 'fileOffset': 0, 'bytes': len(data)}
        f = self.TrackedFile(data)
        with tempfile.TemporaryDirectory() as tmp:
            actual = mod.scan_domains(f, [domain], Path(tmp), {}, {})
            expected = [dict(row, scanDomainBegin=site) for row in mod.raw_candidates(data, site, 0)]
            self.assertEqual(actual, expected)
            self.assertEqual(f.reads, [(0, len(data), len(data))])
            self.assertEqual(list(Path(tmp).iterdir()), [])


class MetadataTests(unittest.TestCase):
    def test_pdata_all_containment_and_edges(self):
        base = 0x140000000
        sites = [base + n for n in (10, 20, 24, 30, 40, 50)]
        table = b''.join([row(20, 25), row(20, 25, 4), row(22, 28), row(30, 34), row(35, 40), row(50, 55)])
        m = mod.pdata_matches(sites, table, base, 100, 200)
        self.assertEqual(len(m[sites[0]]['containingSite']), 0)
        self.assertEqual(len(m[sites[1]]['coveringFiveBytes']), 2)
        self.assertEqual(len(m[sites[2]]['containingSite']), 3)
        self.assertEqual(len(m[sites[2]]['coveringFiveBytes']), 0)
        self.assertEqual(len(m[sites[3]]['containingSite']), 1)
        self.assertEqual(len(m[sites[3]]['coveringFiveBytes']), 0)
        self.assertEqual(len(m[sites[4]]['containingSite']), 0)
        self.assertEqual(len(m[sites[5]]['coveringFiveBytes']), 1)
        self.assertEqual(m[sites[1]]['containingSite'][1]['recordRVA'], 112)

    def test_aliases_preserve_all_and_missing(self):
        owner = {'index': 1, 'name': 'T', 'namespace': '', 'methods': [
            {'index': 1, 'registeredAddress': '0x100'}, {'index': 2, 'registeredAddress': '0x100'}]}
        sig = {'methods': [{'index': 1, 'parameters': [{'typeIndex': 1}]}],
               'typeReferences': {'1': {'typeDefinition': 52802, 'byRef': True}}}
        aliases = mod.method_aliases([owner], sig, {0x100, 0x200})
        self.assertEqual(len(aliases[0x100]), 2)
        self.assertEqual(aliases[0x100][0]['directTypedHints'][0]['typeDefinition'], 52802)
        self.assertEqual(aliases[0x200], [])


class AuditTests(unittest.TestCase):
    def test_audit_missing_and_mutated_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp)/'a', Path(tmp)/'b'
            a.mkdir(); b.mkdir()
            with self.assertRaises(FileNotFoundError): mod.audit_artifacts(a, b)
            for path in (a, b):
                mod.save(path/mod.ARTIFACTS[0], {'target': mod.TARGET, 'rawCandidates': [], **mod.FALSES})
                mod.save(path/mod.ARTIFACTS[1], {'execScan': {'domains': [], 'bytesRead': 0}, **mod.FALSES})
                mod.save(path/mod.ARTIFACTS[2], {'ranges': []})
            self.assertTrue(mod.audit_artifacts(a,b)['passed'])
            (b/mod.ARTIFACTS[0]).write_bytes(b'changed')
            with self.assertRaises(ValueError): mod.audit_artifacts(a,b)


class ResolverTests(unittest.TestCase):
    def test_portable_argv_and_original_failure_buffers(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp)
            result = subprocess.CompletedProcess([], 7, b'out\x00', b'err\xff')
            with mock.patch.object(mod.subprocess, 'run', return_value=result) as run:
                with self.assertRaises(ValueError):
                    mod.resolve_game_path(dest)
            argv = run.call_args.args[0]
            self.assertEqual(argv[:5], ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File'])
            self.assertEqual(argv[5], str(mod.ROOT / 'tools/game_path.ps1'))
            self.assertEqual(run.call_count, 1)
            recorded = json.loads((dest / 'resolver-failure.private.json').read_bytes())
            self.assertEqual(recorded['argv'], argv)
            self.assertEqual(recorded['exitCode'], 7)
            self.assertEqual(base64.b64decode(recorded['stdoutBase64']), b'out\x00')
            self.assertEqual(base64.b64decode(recorded['stderrBase64']), b'err\xff')
            self.assertEqual(recorded['phase'], 'game-path-resolver')
            self.assertTrue(recorded['utc'])
            self.assertEqual(sum(recorded[k] for k in ('PEHashReadBytes','PEHeaderReadBytes','PEExecReadBytes','PEPdataReadBytes')), 0)

    def test_success_returns_path(self):
        result = subprocess.CompletedProcess([], 0, b'example\r\n', b'')
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(mod.subprocess, 'run', return_value=result) as run:
            self.assertEqual(mod.resolve_game_path(Path(tmp)), 'example')
            self.assertEqual(run.call_count, 1)


if __name__ == '__main__': unittest.main()
