"""Native/oracle contract checks; no credential or signature values in failures."""
from pathlib import Path
import hashlib
import json
import os
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import client72_sdk_combo_signature as sign


class ComboSignatureContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        inventory = json.loads((sign.ROOT / sign.INVENTORY).read_bytes())
        cls.client = Path(os.environ.get('GAME_PATH', inventory['gamePath']))
        cls.fixed = sign.verify_inputs(cls.client)
        data = (cls.client / sign.SDK_FILE).read_bytes()
        if sign.digest(data) != sign.SDK_SHA:
            raise ValueError('Fixed SDK differs')
        cls.pe = sign.Image(data)
        cls.native = sign.native_cases(cls.pe)
        cls.builder = sign.builder_case(cls.pe)

    def test_five_original_inputs_rechecked(self):
        rows = json.loads((sign.ROOT / sign.INVENTORY).read_bytes())['inputs']
        self.assertEqual(len(rows), 5)
        self.assertTrue(all('GAME_PATH/' + row['file'] in self.fixed for row in rows))

    def test_complete_native_signer_matches_independent_oracle(self):
        self.assertEqual(len(self.native['cases']), 12)
        self.assertTrue(all(row['oracleEqual'] and row['originalCanonicalBytesEqual']
                            and row['balancedReturn'] for row in self.native['cases']))

    def test_hash_primitive_not_a_success_model(self):
        for row in self.native['cases']:
            self.assertEqual(row['originalEntries']['0x396020'], 1)
            self.assertGreater(row['originalEntries']['0x3b8080'], 1)
            self.assertTrue(set(row['models']).issubset({'storage-allocate', 'storage-free'}))

    def test_hex_shape_and_64bit_output_ownership(self):
        self.assertTrue(all(row['lowercaseHex64'] and row['outputBytes'] == 64
                            and row['returnedCallerOutputPointer64'] for row in self.native['cases']))

    def test_insertion_order_and_changed_byte(self):
        self.assertTrue(self.native['insertionOrderDoesNotChangeSignature'])
        self.assertTrue(self.native['changedValueChangesSignature'])
        self.assertTrue(all(row['changedKeyDoesNotMatch'] and row['changedDataByteDoesNotMatch']
                            for row in self.native['negativeChecks']))

    def test_hmac_key_block_boundary_executes_native_prehash(self):
        cases = {row['case']: row for row in self.native['cases']}
        short, long = (cases[name] for name in ['key-64-byte-boundary', 'key-65-byte-prehash'])
        self.assertEqual(short['keyBytesAtNativeHmac'], 64)
        self.assertEqual(long['keyBytesAtNativeHmac'], 65)
        self.assertGreater(long['originalEntries']['0x3ba2f0'], short['originalEntries']['0x3ba2f0'])

    def test_nul_contract_and_raw_byte_order(self):
        pairs = [(b'a\0b', b'x\0y'), (b'A', b'&=')]
        self.assertTrue(sign.canonical(pairs) == b'A=&=&a\0b=x\0y')
        self.assertTrue(sign.oracle(pairs, b'head\0tail') == sign.oracle(pairs, b'head'))
        self.assertTrue(sign.oracle(pairs, b'head') != sign.oracle([(b'a\0b', b'x'), (b'A', b'&=')], b'head'))

    def test_large_string_native_allocation_path(self):
        row = next(row for row in self.native['cases'] if row['case'] == 'large-string-aligned-allocation')
        self.assertGreater(row['canonicalBytes'], 4096)
        self.assertTrue(row['oracleEqual'])

    def test_unicode_nested_int64_remain_caller_string(self):
        self.assertTrue(self.builder['dataCStringBytesPreserved'])
        self.assertTrue(self.builder['outerJsonCompactUtf8ByteEqual'])
        self.assertTrue(self.builder['allOuterFieldValuesAreStrings'])

    def test_exact_builder_sign_call_and_request_boundary(self):
        self.assertEqual(self.builder['signCallRva'], '0x3bea13')
        self.assertEqual(self.builder['signReturnRva'], '0x3bea16')
        self.assertEqual(self.builder['signCall']['targetRva'], 0x3958e0)
        self.assertTrue(self.builder['signReturn']['returnsCallerOutputPointer64'])
        self.assertEqual(self.builder['signReturn']['outputBytes'], 64)
        self.assertEqual(self.builder['exitBeforeCallRva'], '0x3bec2c')
        self.assertEqual(self.builder['networkTargetRva'], '0x39c310')
        self.assertFalse(self.builder['wholeBuilderReturned'])
        self.assertFalse(self.builder['networkExecuted'])
        self.assertTrue(all(self.builder['incorrectInputAlternatives'].values()))

    def test_no_file_reload_on_actual_combo_getter_path(self):
        self.assertEqual(self.builder['originalEntries']['0x376ea0'], 1)
        self.assertEqual(self.builder['originalEntries']['0x364910'], 0)
        self.assertEqual(self.builder['originalEntries']['0x140940'], 0)

    def test_actual_resource_key_producer_equal_without_signing(self):
        row = sign.producer_case(self.pe)
        self.assertTrue(row['appKeyEqual'])
        self.assertFalse(row['appKeyBytesLengthOrHashRecorded'])
        self.assertFalse(row['signerInvokedWithResourceKey'])
        self.assertFalse(row['naturalEnvSelected'])

    def test_original_literals_and_code_windows(self):
        row = sign.static_proof(self.pe)
        self.assertEqual(len(row['codeWindows']), 6)
        self.assertTrue(all(value for key, value in row['literalAndVtableChecks'].items() if key.endswith('Exact')))

    def test_receiver_contract_is_source_only_and_sign_is_unused(self):
        row = sign.java_contract()
        self.assertFalse(row['outerSignConsumedOrVerifiedByDefaultAuthenticator'])
        self.assertFalse(row['actualHttpAcceptanceExecuted'])
        self.assertTrue(row['lenientBranchCanAdoptSuppliedSessionKey'])
        self.assertEqual(row['failureRetcode'], -201)

    def test_standard_hmac_and_manual_pad_construction_agree(self):
        for _, pairs, key in sign.synthetic_cases():
            raw = (key or b'').split(b'\0', 1)[0]
            if len(raw) > 64:
                raw = hashlib.sha256(raw).digest()
            block = raw.ljust(64, b'\0')
            inner = hashlib.sha256(bytes(x ^ 0x36 for x in block) + sign.canonical(pairs)).digest()
            manual = hashlib.sha256(bytes(x ^ 0x5c for x in block) + inner).hexdigest().encode()
            self.assertTrue(manual == sign.oracle(pairs, key))


if __name__ == '__main__':
    unittest.main()
