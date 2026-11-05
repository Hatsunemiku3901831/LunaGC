"""E-01b: audit pinned scene3 inputs and reviewed server read dependencies.

This is an offline input audit. It never installs resources or evaluates Lua,
and it does not infer hotfix revisions or client compatibility from matching IDs.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import re

try:
    from .audit_resource72 import (audit, compare_rows, git_hash, index_table,
                                   read_client_evidence, unique_object, verify_tree)
except ImportError:
    from audit_resource72 import (audit, compare_rows, git_hash, index_table,
                                  read_client_evidence, unique_object, verify_tree)


COMMIT = 'd54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'
ROOT_TREE = '5b334e711319726c504da95dbfe6ebb26baaa7d3'
POINT_FILE = 'BinOutput/Scene/Point/scene3_point.json'
REFERENCE_TABLES = ('GadgetExcelConfigData.json', 'DungeonExcelConfigData.json',
                    'DailyDungeonConfigData.json', 'SceneExcelConfigData.json')
# Reviewed on this checkout. Hashes normalize CRLF, as do the source graph tools.
LOADERS = {
    'src/main/java/emu/grasscutter/data/ResourceLoader.java': 'fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015',
    'src/main/java/emu/grasscutter/data/common/PointData.java': '18067d5baaf75975a1c5ee31b510c3d6d138c6e2e2981da9c219a91fc65cc740',
    'src/main/java/emu/grasscutter/data/excels/scene/SceneData.java': '043dc72f72f8fcdf73f36bcc07c88231758cd131389003c63b7358daf2fdee77',
    'src/main/java/emu/grasscutter/data/excels/dungeon/DailyDungeonData.java': 'e7d3105f2709ba85773aae8adc75cf1669b8f90d36707be294caeb52e0f9cc82',
    'src/main/java/emu/grasscutter/scripts/data/SceneMeta.java': '4708e0fa2c35dedd64d8af444d8524ac02d42747a475d996f7115b2db575198f',
    'src/main/java/emu/grasscutter/scripts/data/SceneBlock.java': '77714d52c8212b3bea610af8e73100737fdafafdd90346e64b72c8273822c186',
    'src/main/java/emu/grasscutter/scripts/data/SceneGroup.java': '7e899a4457920cdc304a9db55450b8eb20062ab25f127165432abd9f92d44f7e',
    'src/main/java/emu/grasscutter/scripts/ScriptLoader.java': 'ff7a09590df7038fa1c9ab48f347ebe8f0845bc67360d967844908b715324cc1',
    'src/main/java/emu/grasscutter/utils/FileUtils.java': '5da9bb7ba187c0ba49b846a095a446167a142779e0f97b35e168c6b90f43a2d0',
}
ALIASES = {'PHBHHNDGFCF': 'maxSpringVolume', 'JHHFPGJNMIN': 'dungeonIds',
           'GLEKJMEEOMH': 'dungeonRandomList', 'HFOBOOHKBGF': 'groupIDs',
           'JHBICGBAPIH': 'tranSceneId'}
POINT_FIELDS = {
    **dict.fromkeys(('id', 'areaId', 'gadgetId', 'maxSpringVolume', 'tranSceneId'), 'int'),
    **dict.fromkeys(('tranPos', 'tranRot', 'pos', 'rot', 'size'), 'vector'),
    **dict.fromkeys(('forbidSimpleUnlock', 'unlocked', 'groupLimit'), 'bool'),
    **dict.fromkeys(('dungeonIds', 'dungeonRandomList', 'groupIDs'), 'list'),
    '$type': 'string',
}


def verify_blob(source, path, entry):
    if not entry or entry.get('type') != 'blob':
        raise ValueError('Missing pinned resource blob: ' + path)
    raw = (source / path).read_bytes()
    if len(raw) != entry['size'] or git_hash('blob', raw) != entry['sha']:
        raise ValueError('Resource size/Git blob mismatch: ' + path)
    return raw, dict(path=path, size=len(raw), gitBlob=entry['sha'],
                     sha256=hashlib.sha256(raw).hexdigest())


def index_points(raw, review_fields=True):
    document = json.loads(raw, object_pairs_hook=unique_object)
    if not isinstance(document, dict) or not isinstance(document.get('points'), dict):
        raise ValueError('Scene point object/map required')
    points = {}
    projected = {}
    for key, row in document['points'].items():
        if not re.fullmatch(r'0|[1-9][0-9]*', key) or int(key) > 2147483647 or not isinstance(row, dict):
            raise ValueError('Point key must be a canonical nonnegative int32')
        identity = int(key)
        points[identity] = row
        if not review_fields:
            continue
        projected[identity] = fields = {}
        for name, value in row.items():
            field = ALIASES.get(name, name)
            kind = POINT_FIELDS.get(field)
            if kind is None:
                continue  # Retained in the raw comparison; never renamed by shape.
            if field in fields:
                raise ValueError('Ambiguous point field/alias: ' + field)
            valid = (kind == 'int' and type(value) is int and 0 <= value <= 2147483647
                     or kind == 'bool' and type(value) is bool
                     or kind == 'string' and isinstance(value, str)
                     or kind == 'list' and isinstance(value, list)
                     and all(type(v) is int and 0 <= v <= 2147483647 for v in value)
                     or kind == 'vector' and isinstance(value, dict)
                     and all(k in ('x', 'y', 'z') and type(v) in (int, float) and math.isfinite(v)
                             for k, v in value.items()))
            if not valid:
                raise ValueError('Reviewed point field shape differs: ' + field)
            fields[field] = value
    return document, points, projected


def point_references(points, tables):
    counts = Counter()
    missing = []
    daily_ids = set()
    for identity, row in sorted(points.items()):
        for field, target, is_list in (
                ('gadgetId', 'GadgetExcelConfigData.json', False),
                ('tranSceneId', 'SceneExcelConfigData.json', False),
                ('dungeonIds', 'DungeonExcelConfigData.json', True),
                ('dungeonRandomList', 'DailyDungeonConfigData.json', True)):
            value = row.get(field, [] if is_list else 0)
            for value in value if is_list else [value]:
                if not value:
                    continue
                counts[field] += 1
                if value not in tables[target]:
                    missing.append(dict(pointId=identity, field=field, target=target, targetId=value))
                elif field == 'dungeonRandomList':
                    daily_ids.add(value)
    # Follow every weekday for the schedules actually referenced by scene3.
    for identity in sorted(daily_ids):
        row = tables['DailyDungeonConfigData.json'][identity]
        for day in ('monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'):
            values = row.get(day, [])
            if not isinstance(values, list) or any(type(v) is not int or v < 0 for v in values):
                raise ValueError('Daily dungeon day must contain nonnegative integer IDs')
            for value in values:
                if not value:
                    continue
                counts['daily.' + day] += 1
                if value not in tables['DungeonExcelConfigData.json']:
                    missing.append(dict(dailyDungeonId=identity, field=day,
                                        target='DungeonExcelConfigData.json', targetId=value))
    return dict(checkedPositiveReferences=sum(counts.values()), byField=dict(sorted(counts.items())),
                referencedDailySchedules=sorted(daily_ids), missing=missing,
                scope='declared point gadget/scene/dungeon/schedule and referenced seven-day dungeon IDs only')


def audit_scene(source, baseline, commit, trees, revisions, repository):
    root = verify_tree(trees['root'])
    if commit['commit'] != COMMIT or trees['root']['sha'] != ROOT_TREE:
        raise ValueError('Reviewed fixed CN 7.2 commit required')
    parent = root
    for name, key in (('BinOutput', 'bin'), ('Scene', 'scene'), ('Point', 'point')):
        child = verify_tree(trees[key])
        if parent.get(name, {}).get('type') != 'tree' or parent[name]['sha'] != trees[key]['sha']:
            raise ValueError('Root/BinOutput/Scene/Point tree chain mismatch')
        parent = child
    # Reuse the independently established commit/root/Excel/revision checks.
    candidate = audit(source, baseline, commit, trees['root'], trees['excel'], revisions)
    for path, expected in LOADERS.items():
        actual = hashlib.sha256((repository / path).read_bytes().replace(b'\r\n', b'\n')).hexdigest()
        if actual != expected:
            raise ValueError('Review changed server resource reader: ' + path)
    raw, point_blob = verify_blob(source, POINT_FILE, parent.get('scene3_point.json'))
    document, points, projected = index_points(raw)
    old_raw = (baseline / POINT_FILE).read_bytes()
    _, before, _ = index_points(old_raw, review_fields=False)
    tables = {}
    blobs = [point_blob]
    excel = verify_tree(trees['excel'])
    for name in REFERENCE_TABLES:
        data, blob = verify_blob(source, 'ExcelBinOutput/' + name, excel.get(name))
        tables[name] = index_table(data, 'id')
        blobs.append(blob)
    if 3 not in tables['SceneExcelConfigData.json']:
        raise ValueError('Source scene3 row absent')
    all_fields = Counter(name for row in points.values() for name in row)
    unconsumed = {name: count for name, count in sorted(all_fields.items())
                  if ALIASES.get(name, name) not in POINT_FIELDS}
    script_path = 'Scripts/Scene/3/scene3.lua'
    baseline_script = baseline / script_path
    scripts_available = root.get('Scripts', {}).get('type') == 'tree'
    return dict(schemaVersion=1, upstream=commit, inputBlobs=blobs,
                treeChain={key: trees[key]['sha'] for key in ('root', 'bin', 'scene', 'point', 'excel')},
                reviewedLoaderSHA256=LOADERS, baselinePointSHA256=hashlib.sha256(old_raw).hexdigest(),
                pointCount=len(points), baselinePointCount=len(before), pointDifferences=compare_rows(before, points),
                rootKeys=sorted(document), pointFields=dict(sorted(all_fields.items())),
                fieldsNotConsumedByPointData=unconsumed, references=point_references(projected, tables),
                sceneExcelFields=sorted(tables['SceneExcelConfigData.json'][3]),
                scripts=dict(sourceContainsScriptsTree=scripts_available, requiredRoot=script_path,
                             baselineRootPresent=baseline_script.is_file(),
                             baselineRootSHA256=hashlib.sha256(baseline_script.read_bytes()).hexdigest() if baseline_script.is_file() else None,
                             baselineProvenance72='unverified',
                             dependentPatterns=['Scripts/Scene/3/scene3_block<blockId>.lua',
                                                'Scripts/Scene/3/scene3_group<groupId>.lua',
                                                'Scripts/Common/<require argument>.lua'],
                             scope='path dependencies from reviewed readers; not a complete Lua closure or evaluated scene'),
                bundledRevisionFiles=candidate['bundledRevisionFiles'],
                sourceRevisionLabels=candidate['sourceRevisionLabels'],
                revisionValueSetsEqual=candidate['revisionValueSetsEqual'],
                resourceReady=False, runtimeStatus='not-tested',
                gaps=['runtime hotfix/revision alignment', 'authenticated CN 7.2 scene/block/group/Common Lua',
                      'remaining required resource references and loader execution', 'real client entry acceptance'],
                scope='fixed scene3 candidate, declared point fields/references and server read dependencies only')


def summary(report):
    result = {k: v for k, v in report.items() if k != 'pointDifferences'}
    diff = report['pointDifferences']
    result['pointDifferences'] = {key: len(diff[key]) for key in (
        'addedIds', 'removedIds', 'changedFieldsById', 'sharedValueChangesById', 'fieldPresenceChangesById')}
    result['pointDifferences']['unchanged'] = diff['unchanged']
    result['references'] = {k: v for k, v in report['references'].items() if k != 'missing'}
    result['references']['missingCount'] = len(report['references']['missing'])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'baseline', 'client', 'output', 'summary-output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--evidence', type=Path, default=Path('local/migration72/evidence'))
    parser.add_argument('--repository', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    for output in (args.output, args.summary_output):
        if any(output.resolve().is_relative_to(root.resolve()) for root in (args.source, args.baseline, args.client)):
            parser.error('Reports must be outside resource/client input trees')
    read = lambda name: json.loads((args.evidence / name).read_text('utf-8-sig'))
    trees = {key: read('resource-source-' + name + '.json') for key, name in (
        ('root', 'tree'), ('bin', 'BinOutput-tree'), ('excel', 'ExcelBinOutput-tree'),
        ('scene', 'Scene-tree'), ('point', 'Scene-Point-tree'))}
    client, revisions = read_client_evidence(args.client)
    report = audit_scene(args.source, args.baseline, read('resource-source-commit.json'),
                         trees, revisions, args.repository)
    report['client'] = client
    for path, value in ((args.output, report), (args.summary_output, summary(report))):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(points=report['pointCount'], references=report['references']['checkedPositiveReferences'],
                         missing=len(report['references']['missing']), resourceReady=False)))


if __name__ == '__main__':
    main()
