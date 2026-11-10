"""Artificial UTF-16LE byte fixtures; no managed objects or target execution."""
from contextlib import redirect_stderr, redirect_stdout
import hashlib
import io
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

from tools import client72_sdk_final_route_classifier as classifier

FIXTURES = (
    (0, 'hk4e-sdk.mihoyo.com'),
    (1, 'sandbox-sdk.mihoyo.com'),
    (4, 'preapi-takumi.mihoyo.com'),
    (6, 'devapi-takumi.mihoyo.com'),
    (8, 'ptsapi-takumi.mihoyo.com'),
    (9, 'api-beta-sdk.mihoyo.com'),
    (22, 'audit-api.mihoyo.com'),
    (19, 'hotfix-sdk.mihoyo.com'),
)
PATH = '/hk4e_cn/combo/granter/login/v2/login'
SECRET = 'UNKNOWN_PRIVATE_SENTINEL'


def sample(host):
    return ('https://' + host + PATH + '?').encode('utf-16-le')


class FinalRouteTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.engine = classifier.load_classifier()

    def test_eight_exact_independent_fixtures(self):
        lengths = set()
        for env, host in FIXTURES:
            with self.subTest(env=env):
                raw = sample(host)
                lengths.add(len(raw) // 2)
                self.assertEqual(self.engine.classify(raw), 'managed-final-request-cn-env-' + str(env))
        self.assertEqual(lengths, set(range(65, 71)))

    def test_no_normalization_or_query_changes(self):
        for _, host in FIXTURES:
            raw = sample(host)
            variants = (raw[:-2], raw + '?'.encode('utf-16-le'), raw + 'x=1'.encode('utf-16-le'),
                        raw + '&x=1'.encode('utf-16-le'), raw.replace(b'h\0', b'H\0', 1),
                        raw[:10] + b'\0\0' + raw[12:], raw[:10] + b'\x00\xd8' + raw[12:],
                        raw + b'\x00')
            for candidate in variants:
                self.assertEqual(self.engine.classify(candidate), 'unknown')

    def test_boundary_lengths_and_invalid_type(self):
        for length in (64, 65, 70, 71):
            self.assertEqual(self.engine.classify(b'Z\0' * length), 'unknown')
        known = {len(sample(host)) // 2: sample(host) for _, host in FIXTURES}
        self.assertNotEqual(self.engine.classify(known[65]), 'unknown')
        self.assertNotEqual(self.engine.classify(known[70]), 'unknown')
        for value in (None, '', bytearray(sample(FIXTURES[0][1])), memoryview(b'x'), 65, True):
            self.assertEqual(self.engine.classify(value), 'unknown')

    def test_unknown_never_discloses_contents_length_or_hash(self):
        raw = (SECRET + 'x' * 50).encode('utf-16-le')
        stdout, stderr = io.StringIO(), io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            outcome = self.engine.classify(raw)
            rejected = self.engine.classify(SECRET)
        rendered = outcome + rejected + stdout.getvalue() + stderr.getvalue()
        self.assertEqual(outcome, 'unknown')
        self.assertEqual(rejected, 'unknown')
        for value in (SECRET, raw.hex(), str(len(raw)), str(len(raw) // 2), hashlib.sha256(raw).hexdigest()):
            self.assertNotIn(value, rendered)
        self.assertNotIn(SECRET, repr(self.engine))

    def test_every_frozen_source_drift_or_missing_refuses_before_use(self):
        original = Path.read_bytes
        for relative in classifier.PINS:
            target = classifier.ROOT / relative
            for mode in ('changed', 'missing'):
                def read(path):
                    if path == target:
                        if mode == 'missing':
                            raise OSError(SECRET)
                        return original(path) + b'changed'
                    return original(path)
                with self.subTest(source=relative, mode=mode), patch.object(Path, 'read_bytes', read):
                    with self.assertRaises(classifier.SourceValidationError) as raised:
                        classifier.load_classifier()
                    self.assertEqual(str(raised.exception), 'source-validation-failed')
                    self.assertNotIn(SECRET, repr(raised.exception))

    def test_cli_fixed_receipt(self):
        result = subprocess.run([sys.executable, '-B', str(classifier.ROOT / classifier.SOURCE.replace(
            'client72_sdk_managed_route_inputs.py', 'client72_sdk_final_route_classifier.py')), '--verify-sources'],
            cwd=classifier.ROOT, capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout.strip(), '{"sourceValidation":"passed","scope":"offline-source-bound-classifier"}')
        self.assertEqual(result.stderr, '')


if __name__ == '__main__':
    unittest.main()
