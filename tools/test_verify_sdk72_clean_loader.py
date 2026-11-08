import json
import subprocess
import unittest
from tools import verify_sdk72_clean_loader as verify


class VerifyCleanLoaderTest(unittest.TestCase):
    def test_abi_only_exact_public_schema_is_retained(self):
        data = json.dumps(verify.EXPECTED_ABI).encode()
        result = verify.checked_abi(subprocess.CompletedProcess([], 0, data, b''))
        self.assertTrue(result['passed'])
        self.assertEqual(result['public_payload']['forwarded_comparisons'], 188)

    def test_unknown_abi_fields_and_stderr_are_not_copied(self):
        payload = dict(verify.EXPECTED_ABI, unexpected='unrecognized-content')
        result = verify.checked_abi(subprocess.CompletedProcess([], 0, json.dumps(payload).encode(), b'unrecognized-content'))
        self.assertFalse(result['passed'])
        self.assertNotIn('public_payload', result)
        self.assertNotIn('unrecognized-content', json.dumps(result))

    def test_nonzero_abi_exit_cannot_pass_even_with_correct_counts(self):
        result = verify.checked_abi(subprocess.CompletedProcess([], 1, json.dumps(verify.EXPECTED_ABI).encode(), b''))
        self.assertFalse(result['passed'])

    def test_signed_windows_exit_and_exact_diagnostic_must_both_match(self):
        signed = verify.EXCEPTION - 2**32
        result = verify.checked_rejection(subprocess.CompletedProcess([], signed, b'', b'sdk72-clean-bind-failed stage=4 win32=1114\r\n'), 4, 1114)
        self.assertTrue(result['passed'])
        self.assertEqual(result['exit_hex'], '0xe0720001')
        self.assertEqual(result['raw_returncode'], signed)

    def test_other_exit_wrong_diagnostic_and_fake_return_cannot_pass(self):
        for code, output, error in [(1, b'', b'sdk72-clean-bind-failed stage=4 win32=1114\n'),
                                     (verify.EXCEPTION, b'', b'sdk72-clean-bind-failed stage=7 win32=127\n'),
                                     (verify.EXCEPTION, b'fake-return', b'sdk72-clean-bind-failed stage=4 win32=1114\n')]:
            with self.subTest(code=code, output=bool(output)):
                self.assertFalse(verify.checked_rejection(subprocess.CompletedProcess([], code, output, error), 4, 1114)['passed'])

    def test_unrecognized_stderr_is_not_persisted_or_hashed(self):
        result = verify.checked_rejection(subprocess.CompletedProcess([], verify.EXCEPTION, b'', b'unrecognized-content'), 3, 0)
        self.assertFalse(result['passed'])
        self.assertNotIn('public_stderr', result)
        self.assertNotIn('unrecognized-content', json.dumps(result))


if __name__ == '__main__':
    unittest.main()
