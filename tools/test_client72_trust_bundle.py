import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from tools.generate_client72_trust_bundle import HASHES, STOCK_KEYS, make_bundle, validate_bundle
from tools.client72_rsa_fixture import private_numbers, public_der, public_pem, sign_sha256


class TrustBundleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(__file__).resolve().parents[1]
        signer = private_numbers((cls.root / 'src/main/resources/keys/SigningKey.der').read_bytes())
        public = public_der(signer)
        # Private-key signature bytes here are a shape fixture only; target
        # private DER is not needed or materialized by the repository tests.
        cls.bundle = dict(schemaVersion=1, inputSHA256=HASHES, stockKeys=STOCK_KEYS,
                          rootPublicPem=public_pem(public).decode('ascii'),
                          serverPublicDerHex=public.hex(), serverPublicRawHex=public[2:].hex(),
                          publicKeySignatureHex=sign_sha256(signer, public).hex(), privateKeySignatureHex='00' * 256)

    def test_selected_public_root_and_full_der_signature_agree(self):
        validate_bundle(self.bundle)

    def test_wrong_target_or_extra_private_material_is_rejected(self):
        for key, value in [('inputSHA256', {}), ('stockKeys', {}), ('schemaVersion', 2), ('privateDerHex', '00')]:
            with self.subTest(key=key):
                bundle = copy.deepcopy(self.bundle)
                bundle[key] = value
                with self.assertRaises(ValueError):
                    validate_bundle(bundle)

    def test_bad_root_and_wrong_public_or_raw_key_are_rejected(self):
        for key, value in [('rootPublicPem', 'untrusted'), ('serverPublicDerHex', '00' * 270),
                           ('serverPublicRawHex', self.bundle['serverPublicDerHex'])]:
            with self.subTest(key=key):
                bundle = copy.deepcopy(self.bundle)
                bundle[key] = value
                with self.assertRaises(ValueError):
                    validate_bundle(bundle)

    def test_bad_public_signature_is_rejected_by_integer_rsa(self):
        bundle = copy.deepcopy(self.bundle)
        signature = bytearray.fromhex(bundle['publicKeySignatureHex'])
        signature[0] ^= 1
        bundle['publicKeySignatureHex'] = signature.hex()
        with self.assertRaisesRegex(ValueError, 'signature verification failed'):
            validate_bundle(bundle)

    def test_wrong_signature_length_or_encoding_is_rejected(self):
        for key, value in [('privateKeySignatureHex', '00' * 255), ('publicKeySignatureHex', 'gg')]:
            bundle = copy.deepcopy(self.bundle)
            bundle[key] = value
            with self.assertRaises(ValueError):
                validate_bundle(bundle)

    def test_generator_does_not_sign_unknown_target_private_material(self):
        with self.assertRaisesRegex(ValueError, 'fixed native discovery'):
            make_bundle(bytes(1192), b'not a signer')

    def test_cli_refuses_material_output_outside_ignored_local(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, str(self.root / 'tools/generate_client72_trust_bundle.py'),
                                     '--client', directory, '--output', str(Path(directory) / 'trust.json')],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn('must stay under ignored local/', result.stderr)
            self.assertFalse((Path(directory) / 'trust.json').exists())


if __name__ == '__main__':
    unittest.main()
