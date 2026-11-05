"""A-03b built-in source dependency review; never a runtime or target protocol closure."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import tempfile

try:
    from .client72_java_surface import compile_scanner, scan_sources, source_graph, method_id
    from .review_client72_login_surface import check_review
except ImportError:
    from client72_java_surface import compile_scanner, scan_sources, source_graph, method_id
    from review_client72_login_surface import check_review


def check_boundaries(rows, review):
    methods = {method_id(row): row for row in rows if row['kind'] == 'method'}
    for site in review['boundarySites']:
        calls = methods[site['from']]['calls']
        matches = [call for call in calls if (call['kind'], call['selector'], call['argumentCount']) ==
                   (site['kind'], site['selector'], site['argumentCount'])]
        if len(matches) != site['occurrences'] or site['status'] != 'open' or not site['reason']:
            raise ValueError('Open dependency boundary drift or promotion: ' + site['selector'])


def build_report(rows, review):
    check_boundaries(rows, review)
    graph = source_graph(rows, review['roots'], review['reviewedEdges'])
    selected = {row['id'] for row in graph['methods']}
    if any(edge['from'] not in selected for edge in review['reviewedEdges']):
        raise ValueError('Reviewed edge is unreachable from source roots')
    if any(site['from'] not in selected for site in review['boundarySites']):
        raise ValueError('Boundary is unreachable from source roots')
    return dict(schemaVersion=1, scope=review['scope'], closureComplete=False,
                targetProtocolReady=False, runtimeStatus='not-tested', javaAttributionExecuted=False,
                sourceSHA256=review['sourceSHA256'], roots=review['roots'],
                methodCount=len(graph['methods']), edgeCount=len(graph['edges']),
                edgeKinds=dict(sorted(Counter(e['basis'] for e in graph['edges']).items())),
                unlinkedCallSiteCount=len(graph['unresolved']), methods=graph['methods'],
                sourceEdges=graph['edges'], unlinkedCallSites=graph['unresolved'],
                boundarySites=review['boundarySites'], openBoundaries=review['openBoundaries'],
                parentReview=review['parentReview'], parentReviewSHA256=review['parentReviewSHA256'])


def recover(repo, java_home, review_path):
    review = json.loads(review_path.read_text('utf8'))
    check_review(repo, review)
    if hashlib.sha256((repo / review['parentReview']).read_bytes()).hexdigest() != review['parentReviewSHA256']:
        raise ValueError('Parent A-03a review changed')
    with tempfile.TemporaryDirectory(prefix='login-dependencies-', dir=repo / 'local/migration72/analysis') as directory:
        classes = Path(directory)
        compile_scanner(java_home, classes, repo / 'tools/Client72JavaSurface.java')
        rows = scan_sources(repo, java_home, classes, list(review['sourceSHA256']))
    report = build_report(rows, review)
    report['reviewSHA256'] = hashlib.sha256(review_path.read_bytes()).hexdigest()
    report['toolSourceSHA256'] = {name: hashlib.sha256((repo / name).read_text('utf8').encode('utf8')).hexdigest()
                                 for name in ('tools/Client72JavaSurface.java', 'tools/client72_java_surface.py',
                                              'tools/review_client72_login_dependencies.py')}
    return report


def summary(report):
    return {k: v for k, v in report.items() if k != 'unlinkedCallSites'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--review', type=Path, default=Path('protocol/cn-7.2/login-dependency-review.json'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    args = parser.parse_args()
    repo = args.repo.resolve()
    report = recover(repo, args.java_home.resolve(), repo / args.review)
    for path, data in ((args.output, report), (args.summary_output, summary(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + '\n', 'utf8')
    print(json.dumps({k: report[k] for k in ('methodCount', 'edgeCount', 'unlinkedCallSiteCount', 'targetProtocolReady')}))


if __name__ == '__main__':
    main()
