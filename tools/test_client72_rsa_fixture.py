import unittest
from tools.client72_rsa_fixture import private_numbers,public_der,public_pem,sign_sha256,encrypt_seed,der_item
from tools.client72_seed_crypto import rsa_public_numbers,verify_key_signature


class RsaFixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        p=(1<<521)-1;q=(1<<607)-1;e=65537;d=pow(e,-1,(p-1)*(q-1))
        cls.numbers=[0,p*q,e,d,p,q,d%(p-1),d%(q-1),pow(q,-1,p)]
        parts=[]
        for value in cls.numbers:
            b=value.to_bytes(max(1,(value.bit_length()+7)//8),'big')
            parts.append(der_item(2,(b'\0' if b[0]&128 else b'')+b))
        cls.pkcs1=der_item(0x30,b''.join(parts))
        cls.pkcs8=der_item(0x30,b'\x02\x01\0'+bytes.fromhex('300d06092a864886f70d0101010500')+der_item(4,cls.pkcs1))

    def test_private_containers_produce_same_public_key_and_verifiable_signature(self):
        for raw in (self.pkcs1,self.pkcs8):
            key=private_numbers(raw);self.assertEqual(key,self.numbers)
            pub=public_der(key);self.assertEqual(rsa_public_numbers(pub),tuple(key[1:3]))
            message=bytes.fromhex('fedcba9876543210');signature=sign_sha256(key,message)
            self.assertTrue(verify_key_signature(message,signature,public_pem(pub)))
            self.assertFalse(verify_key_signature(message+b'!',signature,public_pem(pub)))

    def test_encryption_recovers_exact_seed_with_nonzero_padding(self):
        key=self.numbers;width=(key[1].bit_length()+7)//8
        for plain in (bytes(8),bytes.fromhex('fedcba9876543210'),b'123456789'):
            cipher=encrypt_seed(key,plain)
            block=pow(int.from_bytes(cipher,'big'),key[3],key[1]).to_bytes(width,'big')
            self.assertEqual(block[:2],b'\0\2');self.assertEqual(block.split(b'\0',2)[-1],plain)
            self.assertGreaterEqual(len(block[2:-len(plain)-1]),8)
        with self.assertRaises(ValueError):encrypt_seed(key,bytes(width-10))

    def test_incomplete_or_non_private_der_is_rejected_at_input(self):
        for raw in (self.pkcs1[:-1],self.pkcs8+b'\0',public_der(self.numbers),b'\x30\x80'):
            with self.assertRaises(ValueError):private_numbers(raw)


if __name__=='__main__':unittest.main()
