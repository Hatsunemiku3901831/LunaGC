"""Evaluate a pinned legacy scene subset against fixed CN72 entity IDs.

Only Lua top-level declarations execute, in a separate Java process. This tool
does not start Grasscutter, load a database, call triggers or install resources.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

try:
    from .audit_resource72 import index_table, verify_tree, read_client_evidence
    from .audit_scene_resource72 import audit_scene, verify_blob
except ImportError:
    from audit_resource72 import index_table, verify_tree, read_client_evidence
    from audit_scene_resource72 import audit_scene, verify_blob


ENTITY_TABLES = ('GadgetExcelConfigData.json', 'MonsterExcelConfigData.json', 'NpcExcelConfigData.json')


def evaluate(repository, scripts, source, java_home, runtime_jar, output, work, all_static=False, fast_require=False):
    helper = repository / 'tools/Client72SceneScriptAudit.java'
    subprocess.run([str(java_home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(runtime_jar),
                    '-d', str(work), str(helper)], check=True, capture_output=True)
    subprocess.run([str(java_home / 'bin/java.exe'), '-Xmx1g', '-Dfile.encoding=UTF-8', '-cp',
                    str(work) + ';' + str(runtime_jar), 'Client72SceneScriptAudit', str(scripts),
                    str(source), str(output), *(['all-static'] if all_static else []),
                    *(['fast-require'] if fast_require else [])], check=True, capture_output=True)
    return json.loads(output.read_text('utf8'))


def summarize(report):
    result = {key: value for key, value in report.items() if key not in ('files', 'issues', 'groupDeclarations')}
    encoded = json.dumps(report['files'], sort_keys=True, separators=(',', ':')).encode('utf8')
    result['evaluatedScriptManifest'] = dict(files=len(report['files']), sha256=hashlib.sha256(encoded).hexdigest(),
                                            commonFiles=sum(name.startswith('Common/') for name in report['files']))
    result['issueCounts'] = dict(sorted(Counter(row['kind'] for row in report['issues']).items()))
    missing = set(report['missingDirectScriptLibMembers'])
    result['groupDeclarationCounts'] = dict(
        static=sum(not row['dynamicLoad'] for row in report['groupDeclarations']),
        dynamic=sum(row['dynamicLoad'] for row in report['groupDeclarations']),
        dynamicEvaluated=sum(row['dynamicLoad'] and row['evaluated'] for row in report['groupDeclarations']),
        staticEvaluated=sum(not row['dynamicLoad'] and row['evaluated'] for row in report['groupDeclarations']),
        staticWithMissingMembers=sum(not row['dynamicLoad'] and bool(missing & set(row['directScriptLibMembers']))
                                     for row in report['groupDeclarations']),
        dynamicWithMissingMembers=sum(row['dynamicLoad'] and bool(missing & set(row['directScriptLibMembers']))
                                      for row in report['groupDeclarations']))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'baseline', 'client', 'java-home', 'runtime-jar', 'output', 'summary-output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--evidence', type=Path, default=Path('local/migration72/evidence'))
    parser.add_argument('--repository', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--all-static', action='store_true', help='Evaluate the non-dynamic groups of every scene3 block for grid-input audit')
    parser.add_argument('--require-mode', choices=('configured', 'inline', 'fast'), default='configured')
    args = parser.parse_args()
    repository, baseline, source = (path.resolve() for path in (args.repository, args.baseline, args.source))
    configured = json.loads((repository / 'config.json').read_text('utf-8-sig'))['server']['fastRequire']
    if type(configured) is not bool:
        parser.error('Configured fastRequire must be boolean')
    fast_require = configured if args.require_mode == 'configured' else args.require_mode == 'fast'
    for output in (args.output, args.summary_output):
        if any(output.resolve().is_relative_to(root.resolve()) for root in (source, baseline, args.client)):
            parser.error('Reports must be outside resource/client input trees')
    read = lambda name: json.loads((args.evidence / name).read_text('utf-8-sig'))
    trees = {key: read('resource-source-' + name + '.json') for key, name in (
        ('root', 'tree'), ('bin', 'BinOutput-tree'), ('excel', 'ExcelBinOutput-tree'),
        ('scene', 'Scene-tree'), ('point', 'Scene-Point-tree'))}
    _, revisions = read_client_evidence(args.client)
    inputs = audit_scene(source, baseline, read('resource-source-commit.json'), trees, revisions, repository)
    baseline_commit = subprocess.run(['git', '-C', str(baseline), 'rev-parse', 'HEAD'], check=True,
                                     capture_output=True, text=True).stdout.strip()
    original_baseline = read('resource-candidate-audit.json')['baselineGitCommit']
    if baseline_commit != original_baseline:
        raise ValueError('Review changed legacy resource commit')
    subprocess.run(['git', '-C', str(baseline), 'diff', '--quiet', 'HEAD', '--',
                    'Scripts/Scene/3', 'Scripts/Common'], check=True)
    entries = verify_tree(trees['excel'])
    blobs = []
    for name in ENTITY_TABLES:
        raw, blob = verify_blob(source, 'ExcelBinOutput/' + name, entries.get(name))
        index_table(raw, 'id')
        blobs.append(blob)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.summary_output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='scene-script-audit-', dir=repository / 'local/migration72/analysis') as folder:
        work = Path(folder)
        report = evaluate(repository, baseline / 'Scripts', source, args.java_home.resolve(),
                          args.runtime_jar.resolve(), work / 'evaluation.json', work, args.all_static, fast_require)
    report.update(legacyResourceCommit=baseline_commit, targetSource=inputs['upstream'], requireMode=args.require_mode,
                  inputBlobs=blobs, scriptLibSourceSHA256=hashlib.sha256((repository / 'src/main/java/emu/grasscutter/scripts/ScriptLib.java').read_bytes().replace(b'\r\n', b'\n')).hexdigest(),
                  toolSourceSHA256={name: hashlib.sha256((repository / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                                    for name in ('tools/Client72SceneScriptAudit.java', 'tools/audit_scene_scripts72.py')},
                  runtimeJarSHA256=hashlib.sha256(args.runtime_jar.read_bytes()).hexdigest(),
                  javaScope='existing compiled LuaJ/enum bindings/API names only; no server start or target network-enum evidence')
    for path, value in ((args.output, report), (args.summary_output, summarize(report))):
        path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', 'utf8')
    print(json.dumps(dict(groups=report['declaredGroups'], evaluated=report['evaluatedGroups'],
                         references=report['checkedPositiveEntityIds'], issues=len(report['issues']),
                         missingAPIMembers=len(report['missingDirectScriptLibMembers']), resourceReady=False)))


if __name__ == '__main__':
    main()
