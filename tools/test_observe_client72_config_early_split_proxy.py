"""Owned filesystem/backend lifecycle tests; never construct a Windows backend."""
import copy
import contextlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from . import observe_client72_config_early_split_proxy as observer
from . import test_verify_sdk72_config_early_split_proxy as fixtures

PRIVATE = observer.RAW/'adapter-agent'


class OwnedBackend:
    def __init__(self, image, snapshot, read):
        self.image_path = str(image)
        self.creation = dict(filetime=0x123456789abcdef0, winerror=None)
        self.snapshots = [copy.deepcopy(snapshot), copy.deepcopy(snapshot)]
        self.statuses = [dict(alive=True) for _ in range(3)]
        self.creations = [copy.deepcopy(self.creation), copy.deepcopy(self.creation)]
        self.images = [self.image_path, self.image_path]
        self.opened, self.closed, self.reads, self.metadata_calls = [], [], [], []
        self.reader = read
        self.open_handle = 1

    def open(self, pid):
        self.opened.append(pid)
        return self.open_handle

    def status(self, handle):
        self.metadata_calls.append('status')
        return self.statuses.pop(0)

    def creation_time(self, handle):
        self.metadata_calls.append('creation')
        return self.creations.pop(0)

    def image(self, handle):
        self.metadata_calls.append('image')
        return self.images.pop(0)

    def snapshot(self, *args):
        self.metadata_calls.append('modules')
        return self.snapshots.pop(0)

    def read(self, handle, address, width):
        self.reads.append((address, width))
        return self.reader(address, width)

    def close(self, handle):
        self.closed.append(handle)


class EarlyAdapterTests(unittest.TestCase):
    def setUp(self):
        PRIVATE.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix='owned-', dir=PRIVATE)
        self.addCleanup(self.temporary.cleanup)
        self.client = Path(self.temporary.name)
        self.plugins = self.client/'YuanShen_Data/Plugins'
        self.plugins.mkdir(parents=True)
        # Reuse the existing pure verifier's owned memory fixture, then exercise
        # the real collect through a separate OS/lifecycle boundary.
        self.modules, self.contract, self.memory, read = fixtures.EarlyBridgeAuditTests().make()
        self.identities = {}
        for index, name in enumerate(observer.MODULES):
            path = self.client/name if name == 'YuanShen.exe' else self.plugins/name
            raw = ('owned module '+name).encode()
            path.write_bytes(raw)
            self.modules[name].update(name=name, path=str(path), size=0x10000+index)
            self.identities[name] = dict(path=str(path), bytes=len(raw), sha256=observer.audit.digest(raw), imageSize=0x10000+index)
        snapshot = dict(error=None, items=[copy.deepcopy(self.modules[name]) for name in observer.MODULES])
        self.backend = OwnedBackend(self.client/'YuanShen.exe', snapshot, read)
        self.evidence = dict(pid=2468, finalStatus=dict(alive=True), processCreationTime=copy.deepcopy(self.backend.creation),
            samples=[dict(modules=copy.deepcopy(snapshot))])
        self.raw = []

    def inspect(self):
        return observer.inspect_process(self.backend, self.client, self.evidence, self.identities,
            self.contract, [n*16 for n in range(47)], self.raw)

    def test_same_nine_reads_raw_bytes_and_complete_lifecycle_without_sdk(self):
        result = self.inspect()
        self.assertEqual(result['status'], 'installed-early-code-bound')
        self.assertEqual(self.backend.opened, [2468])
        self.assertEqual(self.backend.closed, [1])
        self.assertEqual(self.backend.metadata_calls, ['status', 'creation', 'image', 'modules']*2+['status'])
        self.assertEqual(len(self.backend.reads), 9)
        self.assertEqual(sum(n for _, n in self.backend.reads), 1450)
        self.assertLessEqual(max(n for _, n in self.backend.reads), 512)
        self.assertEqual(result['rawBudget']['reads'], result['budget']['reads'])
        self.assertEqual(result['rawBudget']['bytes'], result['budget']['bytes'])
        self.assertEqual(result['event']['status'], 'managed-input-classified')
        for ordinal, (raw, (address, width), summary) in enumerate(zip(self.raw, self.backend.reads, result['reads']), 1):
            self.assertEqual(set(raw), {'ordinal', 'address', 'requested', 'bytesRead', 'error', 'bytesHex', 'beginUTC', 'endUTC'})
            self.assertEqual((raw['ordinal'], raw['address'], raw['requested']), (ordinal, address, width))
            self.assertEqual(bytes.fromhex(raw['bytesHex']), self.memory[address][:width])
            self.assertEqual(raw['bytesRead'], summary['bytesRead'])
            self.assertIsNone(raw['error'])
            self.assertLessEqual(raw['beginUTC'], raw['endUTC'])
        self.assertFalse(result['payloadReadByCollector'])
        self.assertFalse(result['referenceReadByCollector'])
        self.assertFalse(result['nativeMarshalExecuted'])
        self.assertLessEqual(result['openUTC'], result['identityBefore']['timeUtc'])
        self.assertLessEqual(result['identityAfter']['timeUtc'], result['closeUTC'])

    def test_exit_before_open_and_open_failure_do_not_read(self):
        self.evidence['finalStatus']['alive'] = False
        self.assertEqual(self.inspect()['reason'], 'target-exited')
        self.assertEqual(self.backend.opened, [])
        self.evidence['finalStatus']['alive'] = True
        self.backend.open_handle = 0
        self.assertEqual(self.inspect()['reason'], 'process-unavailable')
        self.assertEqual(self.backend.closed, [])
        self.assertEqual(self.backend.reads, [])
        self.assertEqual(self.raw, [])

    def test_start_creation_or_image_mismatch_closes_without_reads(self):
        for change in ('creation', 'image', 'dead'):
            with self.subTest(change=change):
                self.setUp()
                if change == 'creation':
                    self.backend.creations[0]['filetime'] += 1
                elif change == 'image':
                    self.backend.images[0] = str(self.plugins/'Astrolabe.dll')
                else:
                    self.backend.statuses[0]['alive'] = False
                result = self.inspect()
                self.assertEqual(result['status'], 'partial')
                self.assertEqual(self.backend.reads, [])
                self.assertEqual(self.backend.closed, [1])
                self.assertIn('identityAfter', result)

    def test_start_module_path_size_base_ambiguity_and_prior_drift_rejected(self):
        for change in ('path', 'size', 'base', 'duplicate', 'missing', 'snapshot-error', 'prior-base'):
            with self.subTest(change=change):
                self.setUp()
                row = self.backend.snapshots[0]['items'][1]
                if change == 'path':
                    row['path'] = str(self.plugins/'Astrolabe_orig.dll')
                elif change in ('size', 'base'):
                    row[change] += 1
                elif change == 'duplicate':
                    self.backend.snapshots[0]['items'].append(copy.deepcopy(row))
                elif change == 'missing':
                    self.backend.snapshots[0]['items'].pop()
                elif change == 'snapshot-error':
                    self.backend.snapshots[0]['error'] = 299
                else:
                    self.evidence['samples'][-1]['modules']['items'][1]['base'] += 1
                self.assertEqual(self.inspect()['status'], 'partial')
                self.assertEqual(self.backend.reads, [])
                self.assertEqual(self.backend.closed, [1])

    def test_end_creation_image_module_and_lifetime_drift_keep_same_raw(self):
        for change in ('creation', 'image', 'base', 'size', 'missing', 'dead', 'final-dead', 'snapshot-error'):
            with self.subTest(change=change):
                self.setUp()
                if change == 'creation':
                    self.backend.creations[1]['filetime'] += 1
                elif change == 'image':
                    self.backend.images[1] = str(self.plugins/'Astrolabe.dll')
                elif change == 'dead':
                    self.backend.statuses[1]['alive'] = False
                elif change == 'final-dead':
                    self.backend.statuses[2]['alive'] = False
                elif change == 'missing':
                    self.backend.snapshots[1]['items'].pop()
                elif change == 'snapshot-error':
                    self.backend.snapshots[1]['error'] = 299
                else:
                    self.backend.snapshots[1]['items'][1][change] += 1
                result = self.inspect()
                self.assertEqual(result['status'], 'partial')
                self.assertTrue(result['reason'].startswith('end-'))
                self.assertEqual(len(self.raw), 9)
                self.assertEqual(len(self.backend.reads), 9)
                self.assertEqual(self.backend.closed, [1])

    def test_disk_change_after_collection_is_not_success(self):
        original = self.backend.reader
        def changed(address, width):
            answer = original(address, width)
            if len(self.backend.reads) == 9:
                (self.plugins/'Astrolabe.dll').write_bytes(b'changed owned module')
            return answer
        self.backend.reader = changed
        result = self.inspect()
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(result['reason'], 'end-module-binding-differs')
        self.assertEqual(len(self.raw), 9)
        self.assertEqual(self.backend.closed, [1])

    def test_partial_bytes_and_oserror_preserved_without_exception_text(self):
        for failure in ('partial', 'oserror', 'partial-error'):
            with self.subTest(failure=failure):
                self.setUp()
                address = self.modules['Astrolabe.dll']['base']+self.contract['stateRva']
                expected = self.memory[address][:-1]
                def failed(a, n):
                    if failure == 'oserror':
                        raise OSError('PRIVATE_SENTINEL')
                    return expected, None if failure == 'partial' else dict(kind='read-failed', winerror=299)
                self.backend.reader = failed
                result = self.inspect()
                self.assertEqual(result['status'], 'partial')
                self.assertEqual(len(self.raw), 1)
                self.assertEqual(bytes.fromhex(self.raw[0]['bytesHex']), b'' if failure == 'oserror' else expected)
                self.assertEqual(self.raw[0]['bytesRead'], 0 if failure == 'oserror' else 71)
                self.assertNotIn('PRIVATE_SENTINEL', json.dumps([result, self.raw]))
                self.assertEqual(self.backend.closed, [1])
                self.assertIn('identityAfter', result)

    def test_error_in_later_fixed_read_retains_earlier_and_partial_raw(self):
        original = self.backend.reader
        def failed(address, width):
            data, error = original(address, width)
            return (data[:13], dict(kind='read-failed', winerror=299)) if len(self.backend.reads) == 3 else (data, error)
        self.backend.reader = failed
        result = self.inspect()
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(bytes.fromhex(self.raw[2]['bytesHex']), self.memory[self.raw[2]['address']][:13])
        self.assertEqual(self.raw[2]['error']['winerror'], 299)
        self.assertEqual(len(self.raw), len(self.backend.reads))
        self.assertLessEqual(len(self.raw), 9)
        self.assertEqual(self.backend.closed, [1])

    def test_raw_budget_blocks_extra_backend_reads_independently(self):
        for widths, expected_reads in (([32]*10, 9), ([512]*3, 2), ([513], 0), ([0], 0)):
            with self.subTest(widths=widths):
                self.setUp()
                def exceeding(read, modules, contract, rvas):
                    for width in widths:
                        read(0x12340000, width)
                    raise AssertionError('Budget failed to stop collector')
                with patch.object(observer.audit, 'collect', side_effect=exceeding):
                    result = self.inspect()
                self.assertEqual(result['status'], 'partial')
                self.assertEqual(result['reason'], 'fixed-collection-failure')
                self.assertEqual(len(self.backend.reads), expected_reads)
                self.assertEqual(len(self.raw), expected_reads)
                self.assertLessEqual(sum(row['requested'] for row in self.raw), 1450)
                self.assertEqual(self.backend.closed, [1])

    def test_metadata_error_closes_handle(self):
        with patch.object(self.backend, 'creation_time', side_effect=OSError('PRIVATE_SENTINEL')):
            result = self.inspect()
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(result['reason'], 'fixed-identity-failure')
        self.assertEqual(self.backend.closed, [1])
        self.assertEqual(self.backend.reads, [])
        self.assertNotIn('PRIVATE_SENTINEL', json.dumps(result))

    def test_output_scope_and_both_files_are_immutable(self):
        with self.assertRaises(ValueError):
            observer.output_paths(observer.ROOT/'outside.private.json')
        output = self.client/'result.private.json'
        first, raw = observer.output_paths(output)
        self.assertEqual(first, output)
        raw.write_bytes(b'owned earlier evidence')
        with self.assertRaises(ValueError):
            observer.output_paths(output)
        self.assertEqual(raw.read_bytes(), b'owned earlier evidence')
        raw.unlink()
        output.write_bytes(b'owned earlier result')
        with self.assertRaises(ValueError):
            observer.output_paths(output)
        self.assertEqual(output.read_bytes(), b'owned earlier result')

    def test_preflight_failure_writes_empty_raw_and_never_opens_or_launches(self):
        output = self.client/'preflight.private.json'
        arguments = ['--client', str(self.client), '--production', str(self.client/'absent.dll'),
            '--output', str(output), '--user-ready']
        with patch.object(observer, 'preflight', side_effect=ValueError('PRIVATE_SENTINEL')), \
            patch.object(observer, 'ReadOnlyBackend') as backend, patch.object(observer.clean, 'observe') as observe, \
            contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit) as raised:
                observer.main(arguments)
        self.assertEqual(raised.exception.code, 1)
        backend.assert_not_called()
        observe.assert_not_called()
        result = json.loads(output.read_bytes())
        raw = json.loads(output.with_name(output.name+'.reads.private.json').read_bytes())
        self.assertEqual(result['status'], 'partial')
        self.assertEqual(raw['reads'], [])
        self.assertNotIn('PRIVATE_SENTINEL', json.dumps(result))

    def test_cli_requires_fresh_user_ready_before_preflight(self):
        output = self.client/'not-started.private.json'
        with patch.object(observer, 'preflight') as preflight, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as raised:
                observer.main(['--client', str(self.client), '--production', str(self.client/'absent.dll'), '--output', str(output)])
        self.assertEqual(raised.exception.code, 2)
        preflight.assert_not_called()
        self.assertFalse(output.exists())

    def test_cli_preserves_complete_ninety_second_monitor_and_same_read_ledger(self):
        output = self.client/'complete.private.json'
        inputs = dict(fixedInputs={'astrolabe': dict(path='YuanShen_Data/Plugins/Astrolabe_orig.dll')},
            installedProxy=dict(path='YuanShen_Data/Plugins/Astrolabe.dll'))
        snapshot = copy.deepcopy(self.evidence['samples'][0]['modules'])
        self.backend.statuses = [dict(alive=True) for _ in range(5)]
        self.backend.creations = [copy.deepcopy(self.backend.creation) for _ in range(3)]
        self.backend.images = [self.backend.image_path]*3
        ticks = iter((0, 0, 90, 90, 90))
        real_observe = observer.clean.observe
        def observe(backend, game, report, seconds):
            self.assertEqual(seconds, 90)
            return real_observe(backend, game, report, seconds, sleep=lambda _: None, monotonic=lambda: next(ticks))
        with patch.object(observer, 'preflight', return_value=(inputs, self.identities, self.contract, [n*16 for n in range(47)])), \
            patch.object(observer, 'ReadOnlyBackend', return_value=self.backend), \
            patch.object(observer.clean, 'observe', side_effect=observe), \
            patch.object(self.backend, 'processes', create=True, return_value=dict(error=None, items=[])), \
            patch.object(self.backend, 'self_probe', create=True, return_value=dict(value=None)), \
            patch.object(self.backend, 'launch', create=True, return_value=(2, 2468)), \
            patch.object(self.backend, 'sample', create=True, return_value=dict(modules=snapshot)), \
            contextlib.redirect_stdout(io.StringIO()):
            observer.main(['--client', str(self.client), '--production', str(self.client/'owned.dll'),
                '--output', str(output), '--user-ready'])
        result = json.loads(output.read_bytes())
        raw = json.loads(output.with_name(output.name+'.reads.private.json').read_bytes())
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(result['inputs'], inputs)
        self.assertEqual(result['pid'], 2468)
        self.assertEqual(result['processCreationTime'], self.backend.creation)
        self.assertEqual(result['elapsedSeconds'], 90)
        self.assertEqual(result['samples'][0]['modules'], snapshot)
        self.assertIn('timeUtc', result['samples'][0])
        self.assertIn('launchTimeUtc', result)
        self.assertEqual(result['image'], self.backend.image_path)
        self.assertEqual(self.backend.closed, [2, 1])
        self.assertEqual(len(self.backend.reads), len(raw['reads']))
        self.assertEqual(len(raw['reads']), 9)

    def test_real_accepted_owned_source_gate_without_executing_fixture(self):
        if observer.ACCEPTED_PROOF_SHA is None:
            with self.assertRaisesRegex(ValueError,'epoch is not bound'):
                observer.accepted_proof(observer.ACCEPTED_PROOF)
            return
        proof = observer.accepted_proof(observer.ACCEPTED_PROOF)
        self.assertEqual(len(proof['owned']['runs']), 2)
        self.assertEqual(len(proof['owned']['runs'][0]['publicResult']['cases']), 23)
        self.assertEqual(proof['frozenSourcePins'], observer.audit.frozen_sources())
        with patch.object(observer.audit, 'source_epoch', return_value={}):
            with self.assertRaises(ValueError):
                observer.accepted_proof(observer.ACCEPTED_PROOF)

    def test_installed_preflight_uses_alias_four_stock_and_fresh_reference(self):
        profile_path = observer.ROOT/'protocol/cn-7.2/profile.json'
        profile = json.loads(profile_path.read_bytes())
        proof = dict(artifact={'owned': True}, bindingSource={'owned': True}, referenceProof={'owned': True},
            frozenSourcePins={}, sources={})
        fixed, paths = {}, {}
        for name, row in profile['files'].items():
            raw = ('owned fixed '+name).encode()
            path = self.client/row['path']
            if name == 'astrolabe':
                path = path.with_name('Astrolabe_orig.dll')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
            paths[name] = path
            row.update(size=len(raw), sha256=observer.audit.digest(raw))
            fixed[name] = dict(path=row['path'], bytes=len(raw), sha256=row['sha256'])
        proof['fixedInputs'] = fixed
        proxy = b'owned installed early proxy'
        artifact = self.client/'owned-production.dll'
        artifact.write_bytes(proxy)
        (self.plugins/'Astrolabe.dll').write_bytes(proxy)
        exports = [dict(name=name, ordinal=n+1, rva=n*16, forwarder=None)
            for n, name in enumerate(observer.audit.peutil.source_names())]
        pe = SimpleNamespace(image_size=0x20000, exports=lambda: exports)
        original_read = Path.read_bytes
        def read(path):
            if path == profile_path:
                return observer.audit.json_bytes(profile)
            if path == observer.RAW/'binding.private.rs':
                return b'owned binding'
            if path == observer.RAW/'references.private.json':
                return b'owned references'
            return original_read(path)
        with patch.object(observer, 'accepted_proof', return_value=proof), \
            patch.object(observer.clean, 'EXPECTED', {name: row['sha256'] for name, row in fixed.items()}), \
            patch.object(observer, 'PRODUCTION_SHA', observer.audit.digest(proxy)), \
            patch.object(Path, 'read_bytes', read), \
            patch.object(observer.audit, 'verify_inputs', side_effect=AssertionError('Stock-only gate must remain unused')), \
            patch.object(observer.audit, 'require_artifact', return_value=proof['artifact']), \
            patch.object(observer.audit.peutil, 'PE', return_value=pe), \
            patch.object(observer.audit, 'artifact_contract', return_value=self.contract), \
            patch.object(observer.audit, 'binding_source', return_value=b'owned binding'), \
            patch.object(observer.audit, 'source_id', return_value=proof['bindingSource']), \
            patch.object(observer.audit.frozen_config, 'private_material', return_value=(b'owned references', proof['referenceProof'])) as material:
            inputs, identities, contract, rvas = observer.preflight(self.client, artifact)
            material.assert_called_once_with(self.client)
            self.assertEqual(inputs['fixedInputs']['astrolabe']['path'], 'YuanShen_Data/Plugins/Astrolabe_orig.dll')
            self.assertEqual(inputs['installedProxy']['path'], 'YuanShen_Data/Plugins/Astrolabe.dll')
            self.assertEqual(set(identities), set(observer.MODULES))
            self.assertEqual(contract, self.contract)
            self.assertEqual(len(rvas), 47)
            for name, path in paths.items():
                with self.subTest(stock=name):
                    before = path.read_bytes()
                    path.write_bytes(b'changed owned input')
                    with self.assertRaises(ValueError):
                        observer.preflight(self.client, artifact)
                    path.write_bytes(before)
            with patch.object(observer.audit.frozen_config, 'private_material', return_value=(b'changed owned refs', proof['referenceProof'])):
                with self.assertRaises(ValueError):
                    observer.preflight(self.client, artifact)
            (self.plugins/'Astrolabe.dll').write_bytes(b'changed installed proxy')
            with self.assertRaises(ValueError):
                observer.preflight(self.client, artifact)


if __name__ == '__main__':
    unittest.main()
