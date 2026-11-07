"""Independent raw-view contracts; native execution is checked by the fixed CLI."""
import io
import unittest

from tools.client72_resource_index_native import RawFileView, fnv32


class ResourceIndexNativeViewTest(unittest.TestCase):
    def test_fnv_known_vectors(self):
        self.assertEqual(fnv32(b''), 0x811c9dc5)
        self.assertEqual(fnv32(b'a'), 0xe40c292c)
        self.assertEqual(fnv32(b'hello'), 0x4f9f2cab)

    def test_reads_match_independent_binary_stream_at_boundaries(self):
        data = bytes(range(256))*3
        view = RawFileView('fixture.dat', data)
        stream = io.BytesIO(data)
        for position, size in ((0, 12), (255, 3), (len(data)-2, 8), (len(data), 8), ((1 << 33)+7, 8)):
            stream.seek(position)
            self.assertEqual(view.read_at(b'fixture.dat', fnv32(b'fixture.dat'), position, size), stream.read(size))
        self.assertEqual(view.requests[-1]['offset'], (1 << 33)+7)
        self.assertTrue(view.requests[-1]['eof'])

    def test_same_prefix_different_path_or_wrong_name_id_rejected(self):
        view = RawFileView('fixture.dat', b'original')
        with self.assertRaisesRegex(ValueError, 'identity'):
            view.read_at(b'fixture.dat.other', fnv32(b'fixture.dat.other'), 0, 8)
        with self.assertRaisesRegex(ValueError, 'identity'):
            view.read_at(b'fixture.dat', fnv32(b'fixture.dat') ^ 1, 0, 8)
        self.assertEqual(view.requests, [])

    def test_missing_and_eof_remain_distinct_without_fabricated_bytes(self):
        missing = RawFileView('missing.dat', None)
        empty = RawFileView('empty.dat', b'')
        self.assertEqual(missing.read_at(b'missing.dat', fnv32(b'missing.dat'), 0, 12), b'')
        self.assertEqual(empty.read_at(b'empty.dat', fnv32(b'empty.dat'), 0, 12), b'')
        self.assertTrue(missing.requests[0]['missing'])
        self.assertFalse(missing.requests[0]['eof'])
        self.assertFalse(empty.requests[0]['missing'])
        self.assertTrue(empty.requests[0]['eof'])

    def test_model_rejects_unbounded_or_signed_requests(self):
        view = RawFileView('f', b'012345')
        for offset, size in ((-1, 1), (1 << 64, 1), (0, -1), (0, 65537)):
            with self.subTest(offset=offset, size=size), self.assertRaises(ValueError):
                view.read_at(b'f', fnv32(b'f'), offset, size)
        self.assertEqual(view.requests, [])

    def test_zero_read_preserves_immutable_original(self):
        data = b'\x0e\xf2\x01\xb5\xff\x00'
        view = RawFileView('f', data)
        self.assertEqual(view.read_at(b'f', fnv32(b'f'), 0, 0), b'')
        self.assertEqual(view.read_at(b'f', fnv32(b'f'), 0, len(data)), data)
        self.assertIs(view.data, data)


if __name__ == '__main__':
    unittest.main()
