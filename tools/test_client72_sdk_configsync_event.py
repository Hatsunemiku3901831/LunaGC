import json
import unittest
import tempfile
from pathlib import Path
from dataclasses import replace

try:
    from .client72_sdk_configsync_event import classify_owned_event, verify_native_owned
    from .client72_sdk_natural_config_contract import ReferenceRow
except ImportError:
    from client72_sdk_configsync_event import classify_owned_event, verify_native_owned
    from client72_sdk_natural_config_contract import ReferenceRow


class ConfigSyncOwnedEventTest(unittest.TestCase):
    def setUp(self):
        self.reference = ReferenceRow(4, b'fixture-private-key', b'https://fixture.invalid/path')
        self.data = {'env': 4, 'app_key': 'fixture-private-key',
                     'url': json.dumps({'comboLogin': 'https://fixture.invalid/path'}),
                     'device_id': 'SENSITIVE-SENTINEL'}

    def run_case(self, data, name=b'local_config', refs=None):
        payload = json.dumps(data).encode('utf-8')
        result = classify_owned_event(name, payload, (self.reference,) if refs is None else refs)
        text = json.dumps(result)
        for secret in ('fixture-private-key', 'fixture.invalid', 'SENSITIVE-SENTINEL'):
            self.assertNotIn(secret, text)
        return result

    def test_wrong_sdk_identity_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'fixture.bin'
            path.write_bytes(b'wrong-sdk')
            with self.assertRaisesRegex(ValueError, '^sdk-identity-mismatch$'):
                verify_native_owned(path)

    def test_selected_reference_and_float_env(self):
        result = self.run_case(self.data)
        self.assertEqual(result, {'status': 'classified-owned', 'env': 'cn-env-4',
                                  'appKeyMatches': True, 'comboUrlMatches': True})
        self.data['env'] = 4.0
        self.assertEqual(self.run_case(self.data), result)

    def test_mismatches_are_independent_booleans(self):
        self.data['app_key'] = 'different'
        self.assertEqual(self.run_case(self.data)['appKeyMatches'], False)
        self.assertEqual(self.run_case(self.data)['comboUrlMatches'], True)
        self.data['url'] = json.dumps({'comboLogin': 'https://different.invalid/path'})
        self.assertEqual(self.run_case(self.data)['comboUrlMatches'], False)

    def test_unrelated_name_and_bad_env_never_select_reference(self):
        self.assertEqual(self.run_case(self.data, b'local_config_extra'), {'status': 'not-local-config'})
        for env in (True, 4.5, 1000000, '4', None):
            self.data['env'] = env
            self.assertEqual(self.run_case(self.data), {'status': 'unsupported-env'})

    def test_missing_or_duplicate_reference_is_not_guessed(self):
        self.assertEqual(self.run_case(self.data, refs=()), {'status': 'reference-unavailable'})
        self.assertEqual(self.run_case(self.data, refs=(self.reference, replace(self.reference))),
                         {'status': 'reference-unavailable'})

    def test_invalid_shapes_and_utf8_have_fixed_redacted_status(self):
        for url in ({'comboLogin': 'ignored'}, '[]', '{}'):
            self.data['url'] = url
            self.assertEqual(self.run_case(self.data)['status'], 'unsupported-shape')
        self.data['url'] = '{invalid'
        self.assertEqual(self.run_case(self.data)['status'], 'invalid-json')
        self.assertEqual(classify_owned_event(b'local_config', b'\xff', (self.reference,)),
                         {'status': 'invalid-json'})


if __name__ == '__main__':
    unittest.main()
