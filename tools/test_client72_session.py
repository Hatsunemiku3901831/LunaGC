import unittest
from tools.client72_session_runtime import permutation_table, SessionRuntime72
from tools.trace_client72_session import SEED_CASES


class SessionFixtureTests(unittest.TestCase):
    def test_storage_table_is_bijective_and_inverse(self):
        for multiplier,shift in ((1,0),(197,41),(255,255)):
            table=permutation_table(multiplier,shift)
            self.assertEqual(len(table),4096)
            for row in range(8):
                self.assertEqual(set(table[row*256:(row+1)*256]),set(range(256)))
                for value in range(256):
                    self.assertEqual(table[(row+8)*256+table[row*256+value]],value)

    def test_invalid_storage_table_inputs_rejected(self):
        for multiplier,shift in ((0,0),(2,0),(257,0),(True,0),(1,-1),(1,256),(1,True)):
            with self.assertRaises(ValueError):permutation_table(multiplier,shift)

    def test_array_allocation_rejects_unbounded_or_unknown_shape(self):
        runtime=object.__new__(SessionRuntime72)
        for length,width in ((-1,1),(4097,1),(True,1),(1,4)):
            with self.assertRaises(ValueError):runtime.array(length,width)

    def test_seed_vectors_exercise_byte_order_high_bits_and_extra_bytes(self):
        cases={name:(data,client) for name,data,client in SEED_CASES}
        data,client=cases['high64']
        self.assertEqual(int.from_bytes(data,'big')^client,0xffffffffffffffff)
        self.assertNotEqual(int.from_bytes(data,'little')^client,0xffffffffffffffff)
        self.assertEqual(cases['trailing-byte'][0][:8],cases['mixed'][0])
        self.assertTrue(any(client>>63 for _,client in cases.values()))


if __name__=='__main__':unittest.main()
