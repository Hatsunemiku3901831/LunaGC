"""Offline preparation/source and owned-report protocol tests. No native launch."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import struct
import unittest

from tools import client72_sdk_final_route_classifier as final

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'local/migration72/d02-resume-20261110b/b-postrequest-owned'
ORIGINAL = ROOT / 'local/migration72/d02-resume-20261110b/b-postrequest/window-original'
MANIFEST_SHA = '15fe4f33595b9466a06fcf18c4d31c2ed260c462d1d3da17699f74e6e87a2088'
BINDING_SHA = '7bff135521d629fd5da5ee70d23b617a332ef6331ac40176552176b46da16371'
ENTRY, CALLER = 0x1992C890, 0x19801317
PROLOGUE = bytes.fromhex('4157415641554154565755534883ec68')
ENVS = (0, 1, 4, 6, 8, 9, 22, 19)
HOSTS = ('hk4e-sdk.mihoyo.com', 'sandbox-sdk.mihoyo.com', 'preapi-takumi.mihoyo.com',
         'devapi-takumi.mihoyo.com', 'ptsapi-takumi.mihoyo.com', 'api-beta-sdk.mihoyo.com',
         'audit-api.mihoyo.com', 'hotfix-sdk.mihoyo.com')


def require(value):
    if not value:
        raise ValueError('postrequest-owned-validation-failed')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def fingerprint(path):
    engine, size = hashlib.sha256(), 0
    with Path(path).open('rb') as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b''):
            size += len(data)
            engine.update(data)
    return dict(bytes=size, sha256=engine.hexdigest())


def verify_sources(get_identity=fingerprint):
    try:
        return _verify_sources(get_identity)
    except (OSError, ValueError, KeyError, TypeError):
        raise ValueError('postrequest-owned-validation-failed') from None


def _verify_sources(get_identity):
    raw = (RAW / 'frozen-inputs.private.json').read_bytes()
    require(digest(raw) == MANIFEST_SHA)
    manifest = json.loads(raw)
    rows = manifest['fixedSources'] + manifest['contextSnapshots']
    require(len(manifest['fixedSources']) == 55 and len(manifest['contextSnapshots']) == 3)
    for row in rows:
        require(get_identity(ROOT / row['path']) == {key: row[key] for key in ('bytes', 'sha256')})
    require(digest((RAW / 'binding.private.rs').read_bytes()) == BINDING_SHA)
    return manifest


def rust_array(text, name):
    match = re.search(r'pub const ' + re.escape(name) + r': &\[u8\] = &\[([0-9,\s]*)\];', text)
    require(match is not None)
    return bytes(int(value) for value in match[1].split(',') if value.strip())


def bindings():
    text = (RAW / 'binding.private.rs').read_text(encoding='utf8')
    arrays = {name: rust_array(text, name) for name in ('ORIGINAL_BODY', 'ORIGINAL_CALL', 'PROLOG', 'EPILOG1', 'EPILOG2')}
    route_text = text.split('pub const ROUTES:', 1)[1]
    routes = [(int(env), bytes(int(v) for v in raw.split(',') if v.strip()))
              for env, raw in re.findall(r'\((\d+), &\[([0-9,\s]*)\]\)', route_text)]
    require(tuple(env for env, _ in routes) == ENVS)
    return arrays, routes


def prefix(arrays, callback, userdata):
    raw = arrays['PROLOG'] + b'\x48\xba' + struct.pack('<Q', userdata)
    raw += bytes.fromhex('4889e14883ec1048b8') + struct.pack('<Q', callback)
    raw += bytes.fromhex('ffd04883c410') + arrays['EPILOG1'] + arrays['EPILOG2']
    require(len(raw) == 250)
    return raw


def verify_emitter(row):
    arrays, _ = bindings()
    require(set(row) == {'placement', 'patchKind', 'moverKind', 'entry', 'moduleBase', 'callback', 'userdata',
                        'trampoline', 'flagsSeed', 'originalHex', 'patchedHex', 'trampolineHex', 'category',
                        'executed', 'baselineEqual', 'savedRspEqual', 'originalCallerAndTail', 'dropRestored', 'event'})
    require(all(row[key] is True for key in ('executed', 'baselineEqual', 'savedRspEqual', 'originalCallerAndTail', 'dropRestored')))
    for key in ('moduleBase', 'entry', 'callback', 'userdata', 'trampoline'):
        require(type(row[key]) is int and 2**32 <= row[key] < 2**63)
    base, entry, address = row['moduleBase'], row['entry'], row['trampoline']
    require(entry == base + ENTRY and row['placement'] in ('near', 'far') and row['flagsSeed'] in (0x202, 0xAD7))
    original = bytes.fromhex(row['originalHex'])
    require(original == PROLOGUE)
    distance = address - entry - 5
    kind = 'near' if abs(distance) <= 0x7FFFFFFF else 'far'
    moved_kind = 'near' if abs(entry - (address + 250)) < 0x7FFFF000 else 'far'
    patch = (b'\xe9' + struct.pack('<i', distance) + original[5:] if kind == 'near'
             else bytes.fromhex('ff2500000000') + struct.pack('<Q', address) + original[14:])
    expected = prefix(arrays, row['callback'], row['userdata']) + original
    expected += bytes.fromhex('ff2500000000') + struct.pack('<Q', entry + 16)
    require(len(expected) == 280 and bytes.fromhex(row['patchedHex']) == patch)
    require(bytes.fromhex(row['trampolineHex']) == expected)
    require(row['placement'] == row['patchKind'] == kind == row['moverKind'] == moved_kind)
    require(row['category'] in ['managed-final-request-cn-env-' + str(env) for env in ENVS])
    require(row['event'] == dict(status='classified-sealed', postRequestEntry=True, exactOriginalCaller=True,
                                 category=row['category']))
    return dict(placement=kind, category=row['category'], flagsSeed=row['flagsSeed'], emitterBytes=280)


def expected_summary():
    return dict(schema=1, scope='owned synthetic PostRequest prologue/call/restore/tail only',
                gpr16=True, xmm16=True, rflags=True, stack128=True, lastError=True, returnHigh64=True,
                savedEntryRsp=True, ownedOriginalCallerReturn=True, ownedCalleeOnce=True, bothActualNearFar=True,
                firstWrongCallerSealed=True, reentrantZeroReads=True, actualOtherOsThreadZeroReads=True,
                ordinalFailuresAndShortReads=True, unknownNoDisclosure=True, repeat1000ZeroReads=True,
                actualInstallFailureNoWrite=True, ownedCodeBindingRpm=True,
                parameterCapReads=5, parameterCapBytes=168, bindingReads=2, bindingBytes=315,
                movedBytes=16, emitterBytes=280, clientLoaded=False, sdkExecuted=False,
                gcOwnershipVerified=False, exceptionUnwindVerified=False, naturalTriggerObserved=False, d02Passed=False)


def verify_owned_report(report):
    require({key: value for key, value in report.items() if key != 'emitters'} == expected_summary())
    rows = report['emitters']
    require(type(rows) is list and len(rows) == 32)
    verified = [verify_emitter(row) for row in rows]
    require(verified == [dict(placement=kind, category='managed-final-request-cn-env-' + str(env),
                             flagsSeed=flags, emitterBytes=280)
                         for kind in ('near', 'far') for env in ENVS for flags in (0x202, 0xAD7)])
    return dict(passed=True, scope='owned-report-structure-and-exact-emitter', verifiedEmitters=verified,
                publicResult=expected_summary())


def artificial_emitter(kind='near', env=0, flags=0x202):
    """Protocol parser fixture only: never used as a native-run receipt."""
    arrays, _ = bindings()
    base = 0x1000000000
    entry = base + ENTRY
    address = entry + 0x100000 if kind == 'near' else 0x100000000
    callback, userdata = 0x2000001000, 0x2000002000
    original = PROLOGUE
    patch = (b'\xe9' + struct.pack('<i', address-entry-5) + original[5:] if kind == 'near'
             else bytes.fromhex('ff2500000000') + struct.pack('<Q', address) + original[14:])
    emitted = prefix(arrays, callback, userdata) + original + bytes.fromhex('ff2500000000') + struct.pack('<Q', entry+16)
    category = 'managed-final-request-cn-env-' + str(env)
    return dict(placement=kind, patchKind=kind, moverKind=kind, entry=entry, moduleBase=base,
                callback=callback, userdata=userdata, trampoline=address, flagsSeed=flags,
                originalHex=original.hex(), patchedHex=patch.hex(), trampolineHex=emitted.hex(), category=category,
                executed=True, baselineEqual=True, savedRspEqual=True, originalCallerAndTail=True, dropRestored=True,
                event=dict(status='classified-sealed', postRequestEntry=True, exactOriginalCaller=True, category=category))


class PreparationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = verify_sources()
        cls.arrays, cls.routes = bindings()
        cls.classifier = final.load_classifier()

    def test_full_original_body_and_independent_call_identity(self):
        body = (ORIGINAL / 'code.bin').read_bytes()
        self.assertEqual(self.arrays['ORIGINAL_BODY'], body)
        self.assertEqual(len(body), 310)
        self.assertEqual(digest(body), '7994592283e5d24d2ebb66d4e5c891d4702dad902ba1dfbb0806d2ec9c507809')
        self.assertEqual(body[:16], PROLOGUE)
        self.assertEqual(body[16:21].hex(), '0f29742450')
        self.assertEqual(body[0x111:0x126].hex(), '0f287424504883c4685b5d5f5e415c415d415e415f')
        self.assertEqual(ENTRY + 0x126 + 5 + struct.unpack_from('<i', body, 0x127)[0], 0x1456230)
        caller = self.arrays['ORIGINAL_CALL']
        self.assertEqual(caller, (ORIGINAL / 'caller-19801312.bin').read_bytes())
        self.assertEqual(CALLER + struct.unpack_from('<i', caller, 1)[0], ENTRY)
        instructions = json.loads((ORIGINAL / 'code.asm.private.json').read_bytes())
        self.assertEqual(b''.join(bytes.fromhex(row['hex']) for row in instructions), body)
        self.assertEqual(len(instructions), 79)
        self.assertFalse(any(row['mnemonic'].lower() == 'ret' for row in instructions))

    def test_rust_table_matches_independent_fixtures_and_source_bound_classifier(self):
        lengths = set()
        for (env, raw), host in zip(self.routes, HOSTS):
            fixture = ('https://' + host + '/hk4e_cn/combo/granter/login/v2/login?').encode('utf-16-le')
            self.assertEqual(raw, fixture)
            self.assertEqual(self.classifier.classify(raw), 'managed-final-request-cn-env-' + str(env))
            lengths.add(len(raw)//2)
        self.assertEqual(lengths, set(range(65,71)))

    def test_1090_perturbations_and_unknown_boundary_protocol(self):
        table = dict((raw, env) for env, raw in self.routes)
        count = 0
        for _, raw in self.routes:
            for offset in range(len(raw)):
                changed = bytearray(raw); changed[offset] ^= 1; changed = bytes(changed)
                self.assertNotIn(changed, table)
                self.assertEqual(self.classifier.classify(changed), 'unknown')
                count += 1
            for changed in (raw[:-2], raw + b'?\0', raw + 'x=1'.encode('utf-16-le'), raw + b'\0',
                            raw[:10] + b'\0\0' + raw[12:], raw[:10] + b'\0\xd8' + raw[12:]):
                self.assertNotIn(changed, table)
                self.assertEqual(self.classifier.classify(changed), 'unknown')
        self.assertEqual(count, 1090)
        for units in (64,65,70,71):
            self.assertNotIn(b'Z\0' * units, table)

    def test_every_frozen_input_drift_or_absence_refuses(self):
        identities = {ROOT/row['path']: {key: row[key] for key in ('bytes','sha256')}
                      for row in self.manifest['fixedSources'] + self.manifest['contextSnapshots']}
        for target in identities:
            for missing in (False,True):
                def changed(path):
                    if path == target:
                        if missing: raise FileNotFoundError('PRIVATE_SOURCE_SENTINEL')
                        return dict(bytes=0, sha256='0'*64)
                    return identities[path]
                with self.subTest(path=target, missing=missing):
                    with self.assertRaisesRegex(ValueError, '^postrequest-owned-validation-failed$'):
                        verify_sources(changed)
        self.assertEqual(len(identities), 58)

    def test_emitter_arrays_come_from_frozen_ilhook_source(self):
        name = next(row['path'] for row in self.manifest['fixedSources'] if row['path'].endswith('/ilhook-2.3.0/src/x64.rs'))
        source = (ROOT / name).read_text()
        for function, array in [('write_trampoline_prolog','PROLOG'), ('write_trampoline_epilog1','EPILOG1'),
                                ('write_trampoline_epilog2_common','EPILOG2')]:
            text = source.split('fn ' + function + '(',1)[1].split('\nfn ',1)[0]
            literal = text.split('buf.write(&[',1)[1].split('])',1)[0]
            raw = bytes(int(value,16) for value in re.findall(r'0x([0-9a-fA-F]{2})\b',literal))
            self.assertEqual(self.arrays[array],raw)
        self.assertIn('jmp_inst_size: 14,',source)
        self.assertIn('MAX_INST_LEN * 2',source)

    def test_complete_280b_near_far_report_protocol_and_no_legacy_length(self):
        for kind in ('near','far'):
            self.assertEqual(verify_emitter(artificial_emitter(kind))['placement'], kind)
        self.assertEqual(len(prefix(self.arrays, 0x2000001000, 0x2000002000)), 250)

    def test_each_emitter_component_drift_rejected(self):
        for key in ('originalHex','patchedHex','trampolineHex'):
            row = artificial_emitter(); raw = bytearray.fromhex(row[key]); raw[-1] ^= 1; row[key] = raw.hex()
            with self.subTest(key=key), self.assertRaises(ValueError): verify_emitter(row)
        for key in ('callback','userdata','entry','moduleBase','trampoline'):
            row = artificial_emitter(); row[key] += 8
            with self.subTest(key=key), self.assertRaises(ValueError): verify_emitter(row)
        row = artificial_emitter(); row['event']['returned'] = True
        with self.assertRaises(ValueError): verify_emitter(row)

    def test_full_report_requires_each_case_and_keeps_unknown_boundaries(self):
        report = dict(expected_summary(), emitters=[artificial_emitter(kind, env, flags)
                     for kind in ('near','far') for env in ENVS for flags in (0x202,0xAD7)])
        self.assertEqual(len(verify_owned_report(report)['verifiedEmitters']), 32)
        for mutate in ('missing','duplicate','client','unknown'):
            bad = copy.deepcopy(report)
            if mutate == 'missing': bad['emitters'].pop()
            elif mutate == 'duplicate': bad['emitters'][1] = bad['emitters'][0]
            elif mutate == 'client': bad['clientLoaded'] = True
            else: bad['gcOwnershipVerified'] = True
            with self.subTest(mutate=mutate), self.assertRaises(ValueError): verify_owned_report(bad)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--verify-sources', action='store_true')
    group.add_argument('--verify-owned', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    verify_sources()
    if args.verify_sources:
        result = dict(passed=True, scope='offline-preparation-only', fixedSources=55, contextSnapshots=3,
                      build=False, nativeExecution=False)
    else:
        require(args.verify_owned.resolve().is_relative_to(RAW.resolve()))
        result = verify_owned_report(json.loads(args.verify_owned.read_bytes()))
    encoded = (json.dumps(result, sort_keys=True, indent=2) + '\n').encode()
    if args.output:
        require(args.output.resolve().is_relative_to(RAW.resolve()))
        with args.output.open('xb') as stream: stream.write(encoded)
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
