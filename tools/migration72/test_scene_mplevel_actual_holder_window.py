import io
import pickle
import tempfile
import unittest
from pathlib import Path

from tools.migration72 import scene_mplevel_actual_holder_window as subject


class HolderWindowTest(unittest.TestCase):
    def test_pins_reject_size_and_content_drift(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'input'
            path.write_bytes(b'abc')
            self.assertEqual(subject.check_file(path, 3, subject.sha(b'abc'))['bytes'], 3)
            with self.assertRaisesRegex(ValueError, 'identity drift'):
                subject.check_file(path, 4, subject.sha(b'abc'))
            with self.assertRaisesRegex(ValueError, 'identity drift'):
                subject.check_file(path, 3, subject.sha(b'xyz'))

    def test_metadata_disallows_pickle_globals(self):
        self.assertEqual(subject.PureData(io.BytesIO(pickle.dumps([1]))).load(), [1])
        with self.assertRaisesRegex(ValueError, 'global class'):
            subject.PureData(io.BytesIO(pickle.dumps(Path('x')))).load()

    def test_exact_site_boundary_and_rip_target(self):
        before = subject.SITE-subject.BEGIN
        code = b'\x90'*before + subject.RAW_SITE + b'\x90'*(subject.END-subject.SITE-len(subject.RAW_SITE))
        rows = subject.decode(code)
        self.assertEqual(bytes.fromhex(''.join(x['bytes'] for x in rows)), code)
        self.assertEqual(next(x for x in rows if x['address'] == hex(subject.SITE))['ripTargets'], [hex(subject.TARGET)])
        with self.assertRaisesRegex(ValueError, 'site is not exact'):
            subject.decode(code[:before-1] + b'\xb8' + code[before:])
        with self.assertRaisesRegex(ValueError, 'site is not exact'):
            subject.decode(code[:before] + subject.RAW_SITE[:-1] + b'\xf9' + code[before+7:])
        with self.assertRaisesRegex(ValueError, 'incomplete decode'):
            subject.decode(code[:-1])


if __name__ == '__main__':
    unittest.main()
