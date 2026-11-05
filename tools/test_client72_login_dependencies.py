import copy
import json
from pathlib import Path
import tempfile
import unittest

from tools.review_client72_login_dependencies import recover, summary, check_boundaries, build_report


class LoginDependencyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo = Path(__file__).resolve().parents[1]
        cls.java = next((cls.repo / 'local/runtime').glob('jdk-17*'))
        cls.path = cls.repo / 'protocol/cn-7.2/login-dependency-review.json'
        cls.review = json.loads(cls.path.read_text('utf8'))
        cls.report = recover(cls.repo, cls.java, cls.path)

    def test_real_review_reproduces_all_entries_without_runtime_promotion(self):
        self.assertEqual(summary(self.report), json.loads(
            (self.repo / 'protocol/cn-7.2/login-dependency-observations.json').read_text('utf8')))
        self.assertFalse(self.report['closureComplete'])
        self.assertFalse(self.report['targetProtocolReady'])
        self.assertEqual(self.report['runtimeStatus'], 'not-tested')
        self.assertEqual(len(self.report['boundarySites']), 10)
        self.assertTrue(all(site['status'] == 'open' for site in self.report['boundarySites']))
        self.assertGreater(self.report['edgeKinds']['same-owner-unique-name-arity-candidate'], 0)
        ids = {row['id'] for row in self.report['methods']}
        for method in ('ArtifactTransmuter#onLogin()', 'GameHome#onOwnerLogin(Player)',
                       'SceneScriptManager#init()', 'PluginManager#invokeHandler(Event,EventHandler<T>)',
                       'Player#loadFromDatabase()'):
            self.assertTrue(any(key.endswith('.' + method) for key in ids), method)

    def test_real_report_is_deterministic(self):
        self.assertEqual(self.report, recover(self.repo, self.java, self.path))

    def test_changed_parent_source_or_readiness_is_rejected(self):
        for field, value, message in (
                ('parentReviewSHA256', '0' * 64, 'Parent'),
                ('sourceSHA256', {next(iter(self.review['sourceSHA256'])): '0' * 64}, 'Source changed'),
                ('closureComplete', True, 'cannot claim'),
                ('targetProtocolReady', True, 'cannot claim')):
            review = copy.deepcopy(self.review)
            review[field] = value
            with tempfile.TemporaryDirectory(dir=self.repo / 'local/migration72/analysis') as folder:
                path = Path(folder) / 'review.json'
                path.write_text(json.dumps(review), 'utf8')
                with self.assertRaisesRegex(ValueError, message):
                    recover(self.repo, self.java, path)

    def test_boundary_drift_or_promotion_is_rejected(self):
        row = dict(kind='method', owner='Test', name='root', parameters=[], calls=[
            dict(kind='reference', selector='this::later', argumentCount=None)])
        review = dict(boundarySites=[dict(
            **{'from': 'Test#root()'}, kind='reference', selector='this::later',
            argumentCount=None, occurrences=1, status='open', reason='scheduled')])
        check_boundaries([row], review)
        for field, value in [('occurrences', 2), ('status', 'verified'), ('reason', '')]:
            changed = copy.deepcopy(review)
            changed['boundarySites'][0][field] = value
            with self.assertRaisesRegex(ValueError, 'boundary'):
                check_boundaries([row], changed)

    def test_unused_reviewed_edges_are_not_counted_as_coverage(self):
        rows = [dict(kind='method', owner='Test', name=name, parameters=[],
                     file='Test.java', line=1, sourceSHA256='fixture', calls=calls)
                for name, calls in [('root', []), ('unused', [dict(
                    kind='invoke', selector='root', argumentCount=0)])]]
        review = dict(boundarySites=[], roots=['Test#root()'], reviewedEdges=[dict(
            **{'from': 'Test#unused()', 'to': 'Test#root()'}, kind='invoke',
            selector='root', argumentCount=0, occurrences=1)])
        with self.assertRaisesRegex(ValueError, 'unreachable'):
            build_report(rows, review)


if __name__ == '__main__':
    unittest.main()
