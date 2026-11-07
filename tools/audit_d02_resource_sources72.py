"""Audit pinned D-02 input provenance; never converts or deploys resources."""
import argparse
import collections
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import urllib.request

try:
    from .audit_resource72 import git_hash, read_client_evidence, unique_object, verify_tree
except ImportError:
    from audit_resource72 import git_hash, read_client_evidence, unique_object, verify_tree

COMMIT = 'd54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'
ROOT_TREE = '5b334e711319726c504da95dbfe6ebb26baaa7d3'
EXCEL = ('AvatarExcelConfigData.json', 'AvatarSkillDepotExcelConfigData.json',
         'AvatarSkillExcelConfigData.json', 'WeaponExcelConfigData.json',
         'AvatarOrnamentGroupExcelConfigData.json', 'AvatarOrnamentExcelConfigData.json',
         'QuestExcelConfigData.json', 'MainQuestExcelConfigData.json',
         'ChapterExcelConfigData.json', 'SceneExcelConfigData.json')
PROFILE = {
    'YuanShen.exe': '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5',
    'YuanShen_Data/Managed/Metadata/global-metadata.dat': '45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a',
    'YuanShen_Data/Managed/Metadata/startup-metadata.dat': 'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105',
}
TREE_FILES = {
    '': 'evidence/resource-source-tree.json',
    'ExcelBinOutput': 'evidence/resource-source-ExcelBinOutput-tree.json',
    'BinOutput': 'evidence/resource-source-BinOutput-tree.json',
    'BinOutput/Quest': 'd02-r3-candidate/BinOutput-Quest-tree.json',
    'BinOutput/Scene': 'evidence/resource-source-Scene-tree.json',
    'BinOutput/Scene/Point': 'evidence/resource-source-Scene-Point-tree.json',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output-dir', type=Path, default=Path('local/migration72/d02-active/resources'))
    parser.add_argument('--fetch-missing', action='store_true')
    args = parser.parse_args()
    repo, client, output = args.repo.resolve(), args.client.resolve(), args.output_dir.resolve()
    if output.is_relative_to(client) or output.is_relative_to(repo / 'resources'):
        parser.error('Output must be outside client and runtime resources')
    local = repo / 'local/migration72'
    trees = {name: read_json(local / path) for name, path in TREE_FILES.items()}
    commit = read_json(local / 'evidence/resource-source-commit.json')
    entries = verify_chain(commit, trees)
    output.mkdir(parents=True, exist_ok=True)
    roots = [('audit_cache', output / 'candidate'),
             ('fixed_candidate', local / 'sources/AnimeGameData' / COMMIT),
             ('r3_mixed_fixture', local / 'd02-r3-candidate'),
             ('chapter_fixed_copy', local / 'offline-sol-terra/chapter-audit')]
    files = []
    values = {}
    for relative, entry in entries.items():
        data, origins = obtain_input(relative, entry, roots, args.fetch_missing)
        value = json.loads(data, object_pairs_hook=unique_object) if relative.endswith('.json') else None
        values[relative] = value
        files.append(dict(path=relative, gitBlob=entry['sha'], size=len(data),
                          sha256=hashlib.sha256(data).hexdigest(), origins=origins,
                          shape=describe_json(value) if value is not None else None))
    identity, revisions = read_client_evidence(client)
    profile = {name: file_hash(client / name) for name in PROFILE}
    if profile != PROFILE:
        raise ValueError('Fixed client profile changed')
    manifests = audit_manifests(client)
    report = dict(schemaVersion=1, task='D02-R3-resource-input-provenance', candidate=commit,
                  sourceSHA256={name: file_hash(repo / name) for name in (
                      'tools/audit_d02_resource_sources72.py', 'tools/audit_resource72.py')},
                  trees=[dict(path=name or '.', gitTree=tree['sha'],
                              sha256=file_hash(local / TREE_FILES[name])) for name, tree in trees.items()],
                  files=files, selectedFacts=selected_facts(values), clientIdentity=identity,
                  clientProfileSHA256=profile, bundledRevisionFiles=revisions, manifests=manifests,
                  mappingEvidence=dict(exporterFieldMapKnown=False, rawParserBridgeKnown=False,
                      reviewedCandidateRootNames=sorted(row['path'] for row in trees['']['tree']),
                      upstreamReadmeSHA256=next(row['sha256'] for row in files if row['path'] == 'README.md'),
                      scope='Complete pinned root and README provide data provenance, not exporter code or a field map.'),
                  resourceReady=False, runtimeRevisionSelected=False, resourceFileIOExecuted=False,
                  limitations=['Candidate byte identity does not establish installed runtime revision alignment.',
                      'Logical table names are absent from reviewed manifest remoteName fields; no block-to-table map is inferred.',
                      'Manifest sizes and selected descriptor hashes are audited; all container MD5 values are not reread.',
                      'No raw binary parser, native loader, loadAll, database, client or debugger is executed.',
                      'No JSON alias, automatic acceptance rule, optional-table or default Ornament claim is approved.'])
    encoded = json.dumps(report, indent=2, ensure_ascii=False) + '\n'
    (output / 'source-audit.json').write_bytes(encoded.encode('utf-8'))
    print(json.dumps(dict(files=len(files), manifestRows=sum(x['rows'] for x in manifests['lists']),
                         resourceReady=False, reportSHA256=hashlib.sha256(encoded.encode()).hexdigest())))


def read_json(path):
    return json.loads(path.read_bytes(), object_pairs_hook=unique_object)


def file_hash(path, algorithm='sha256'):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def verify_chain(commit, trees):
    if (commit.get('commit') != COMMIT or commit.get('treeSHA') != ROOT_TREE or
            commit.get('repository') != 'https://github.com/DimbreathBot/AnimeGameData' or
            commit.get('versionLabel') != 'CNRELWin7.2.0_R49086080_S49210135_D49230962'):
        raise ValueError('Pinned candidate identity changed')
    indexes = {name: verify_tree(tree) for name, tree in trees.items()}
    if trees['']['sha'] != ROOT_TREE:
        raise ValueError('Pinned root changed')
    for name, tree in trees.items():
        if not name:
            continue
        parent, _, leaf = name.rpartition('/')
        entry = indexes[parent].get(leaf)
        if not entry or entry['type'] != 'tree' or entry['sha'] != tree['sha']:
            raise ValueError('Tree chain mismatch: ' + name)
    paths = ['README.md'] + ['ExcelBinOutput/' + name for name in EXCEL]
    paths += ['BinOutput/Quest/303.json', 'BinOutput/Scene/Point/scene3_point.json']
    result = {}
    for path in paths:
        parent, _, leaf = path.rpartition('/')
        entry = indexes[parent].get(leaf)
        if not entry or entry['type'] != 'blob' or type(entry.get('size')) is not int:
            raise ValueError('Required source blob absent: ' + path)
        result[path] = entry
    return result


def verify_blob(data, entry):
    if len(data) != entry['size'] or git_hash('blob', data) != entry['sha']:
        raise ValueError('Pinned blob size/content mismatch')


def obtain_input(relative, entry, roots, fetch_missing):
    accepted = None
    origins = []
    for label, base in roots:
        path = base / relative
        # Chapter's prior fixed copy is directly inside the audit directory.
        if label == 'chapter_fixed_copy' and relative == 'ExcelBinOutput/ChapterExcelConfigData.json':
            path = base / 'ChapterExcelConfigData.json'
        if not path.is_file():
            continue
        data = path.read_bytes()
        matches = len(data) == entry['size'] and git_hash('blob', data) == entry['sha']
        origins.append(dict(origin=label, sha256=hashlib.sha256(data).hexdigest(), accepted=matches))
        if matches:
            accepted = data
    if accepted is None:
        if not fetch_missing:
            raise ValueError('No verified local input: ' + relative)
        url = 'https://raw.githubusercontent.com/DimbreathBot/AnimeGameData/' + COMMIT + '/' + relative
        accepted = urllib.request.urlopen(url, timeout=30).read()
        verify_blob(accepted, entry)
        path = roots[0][1] / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(accepted)
        origins.append(dict(origin='audit_cache', sha256=hashlib.sha256(accepted).hexdigest(), accepted=True))
    return accepted, origins


def describe_json(value):
    if not isinstance(value, list):
        return dict(topLevel=type(value).__name__)
    fields = collections.Counter(key for row in value if isinstance(row, dict) for key in row)
    return dict(topLevel='array', rows=len(value), fieldPresence=dict(sorted(fields.items())))


def selected_facts(values):
    groups = values['ExcelBinOutput/AvatarOrnamentGroupExcelConfigData.json']
    ornaments = values['ExcelBinOutput/AvatarOrnamentExcelConfigData.json']
    depots = values['ExcelBinOutput/AvatarSkillDepotExcelConfigData.json']
    quest = values['ExcelBinOutput/QuestExcelConfigData.json']
    return dict(ornamentGroupAvatarIds=[row.get('avatarId') for row in groups],
                ornamentGroupRawScalarColumns=[{key: val for key, val in row.items() if type(val) is int} for row in groups],
                ornamentRawScalarColumns=[{key: val for key, val in row.items() if type(val) is int} for row in ornaments],
                ornamentColumnSemanticsApproved=False,
                birthAvatarIdsPresentInOrnamentGroups={str(key): any(row.get('avatarId') == key for row in groups) for key in (10000005, 10000007)},
                selectedDepotFieldPresence={str(key): sorted(next(row for row in depots if row.get('id') == key)) for key in (501, 504, 701, 704)},
                questAcceptCondPresent=sum('acceptCond' in row for row in quest),
                questAcceptCondCombShapes=dict(collections.Counter(type(row.get('acceptCondComb')).__name__ for row in quest)),
                automaticAcceptanceSourceKnown=False)


def parse_manifest(data):
    result = []
    names = set()
    for line in data.decode('utf-8-sig').splitlines():
        if not line.strip():
            continue
        row = json.loads(line, object_pairs_hook=unique_object)
        if not isinstance(row, dict):
            raise ValueError('Manifest record must be an object')
        name = row.get('remoteName')
        if (not isinstance(name, str) or not name or '\\' in name or ':' in name or
                PurePosixPath(name).is_absolute() or any(p in ('', '.', '..') for p in name.split('/')) or name in names or
                type(row.get('fileSize')) is not int or row['fileSize'] < 0 or
                not isinstance(row.get('md5'), str) or not re.fullmatch('[0-9a-f]{32}', row['md5']) or
                not isinstance(row.get('hash'), str) or not re.fullmatch('[0-9a-f]{16}', row['hash'])):
            raise ValueError('Invalid/duplicate manifest record')
        names.add(name)
        result.append(row)
    return result


def audit_manifests(client):
    streaming = client / 'YuanShen_Data/StreamingAssets'
    lists = []
    logical_matches = []
    for name in ('data_versions_streaming', 'res_versions_streaming', 'silence_versions_streaming'):
        path = streaming / name
        data = path.read_bytes()
        rows = parse_manifest(data)
        missing, matched, mismatched = [], 0, []
        for row in rows:
            relative = row['remoteName']
            candidates = [streaming / 'AssetBundles' / relative, streaming / relative,
                          streaming / 'AudioAssets' / relative, streaming / 'VideoAssets' / relative,
                          client / 'YuanShen_Data/Persistent' / relative]
            found = [p for p in candidates if p.is_file()]
            if not found:
                missing.append(relative)
            elif any(p.stat().st_size == row['fileSize'] for p in found):
                matched += 1
            else:
                mismatched.append(relative)
            if any(token in relative for token in ('Ornament', 'SkillDepot', 'QuestExcel', 'SceneExcel')):
                logical_matches.append(dict(manifest=name, remoteName=relative))
        lists.append(dict(name=name, sha256=hashlib.sha256(data).hexdigest(), rows=len(rows),
                          locatedWithExpectedSize=matched, notLocatedInAuditedRoots=missing, sizeMismatches=mismatched,
                          containerMD5Reread=False))
    descriptors = []
    for relative in ('YuanShen_Data/Persistent/ctable.dat', 'YuanShen_Data/StreamingAssets/ctable_streaming.dat',
                     'YuanShen_Data/Persistent/base_res_version_hash',
                     'YuanShen_Data/Persistent/res_revision_eternal', 'YuanShen_Data/Persistent/ScriptVersion'):
        path = client / relative
        row = dict(path=relative, exists=path.is_file())
        if path.is_file():
            row.update(size=path.stat().st_size, sha256=file_hash(path), md5=file_hash(path, 'md5'))
            if path.name == 'ctable.dat':
                declared = next(item for item in parse_manifest((streaming / 'res_versions_streaming').read_bytes())
                                if item['remoteName'] == 'ctable.dat')
                row['resManifestSizeAndMD5Match'] = row['size'] == declared['fileSize'] and row['md5'] == declared['md5']
        descriptors.append(row)
    return dict(lists=lists, logicalTableRemoteNameMatches=logical_matches, descriptors=descriptors,
                searchedRoots=['StreamingAssets/AssetBundles', 'StreamingAssets', 'StreamingAssets/AudioAssets',
                               'StreamingAssets/VideoAssets', 'Persistent'],
                logicalNameToContainerKnown=False, naturalSelectionObserved=False)


if __name__ == '__main__':
    main()
