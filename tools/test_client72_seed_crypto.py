import base64
import hashlib
import unittest
from tools.client72_seed_crypto import rsa_public_numbers, verify_key_signature, check_seed_cipher


def der_item(tag,payload):
    n=len(payload)
    length=bytes([n]) if n<128 else bytes([0x82])+n.to_bytes(2,'big')
    return bytes([tag])+length+payload


class SeedCryptoOracleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Public synthetic test key from two known Mersenne primes. It is never
        # used for a client, server, credential or production signature.
        p=(1<<521)-1;q=(1<<607)-1;cls.n=p*q;cls.exponent=65537
        cls.private=pow(cls.exponent,-1,(p-1)*(q-1));cls.width=(cls.n.bit_length()+7)//8
        modulus=cls.n.to_bytes(cls.width,'big')
        cls.der=der_item(0x30,der_item(2,b'\0'+modulus)+der_item(2,b'\x01\x00\x01'))
        cls.pem=b'-----BEGIN RSA PUBLIC KEY-----\n'+base64.b64encode(cls.der)+b'\n-----END RSA PUBLIC KEY-----'

    def sign_block(self,block):
        return pow(int.from_bytes(block,'big'),self.private,self.n).to_bytes(self.width,'big')

    def test_signature_checks_entire_padding_digest_and_message(self):
        message=b'synthetic key DER fixture'
        digest=bytes.fromhex('3031300d060960864801650304020105000420')+hashlib.sha256(message).digest()
        block=b'\0\1'+b'\xff'*(self.width-len(digest)-3)+b'\0'+digest
        signature=self.sign_block(block)
        self.assertTrue(verify_key_signature(message,signature,self.pem))
        self.assertFalse(verify_key_signature(message+b'!',signature,self.pem))
        self.assertFalse(verify_key_signature(message,signature[:-1],self.pem))
        bad=block[:8]+b'\xfe'+block[9:]
        self.assertFalse(verify_key_signature(message,self.sign_block(bad),self.pem))
        self.assertFalse(verify_key_signature(message,self.n.to_bytes(self.width,'big'),self.pem))

    def test_cipher_checks_nonzero_padding_seed_and_every_cipher_byte(self):
        for seed in (0,1,1<<63,(1<<64)-1):
            plain=seed.to_bytes(8,'big');padding=b'\x73'*(self.width-len(plain)-3)
            block=b'\0\2'+padding+b'\0'+plain
            cipher=pow(int.from_bytes(block,'big'),self.exponent,self.n).to_bytes(self.width,'big')
            self.assertTrue(check_seed_cipher(self.der,plain,block,cipher)['passed'])
            with self.assertRaises(ValueError):check_seed_cipher(self.der,plain,block,cipher[:-1]+bytes([cipher[-1]^1]))
            with self.assertRaises(ValueError):check_seed_cipher(self.der,plain,block[:5]+b'\0'+block[6:],cipher)
            with self.assertRaises(ValueError):check_seed_cipher(self.der,plain+b'x',block,cipher)

    def test_der_accepts_target_shape_and_rejects_truncation_and_negative_integer(self):
        self.assertEqual(rsa_public_numbers(self.der),(self.n,self.exponent))
        for raw in (self.der[:-1],self.der+b'\0',b'\x30\x80',b'\x30\x06\x02\x01\xff\x02\x01\x03'):
            with self.assertRaises(ValueError):rsa_public_numbers(raw)


if __name__=='__main__':unittest.main()
