"""Compare fixed old-account Talent lookup keys with two Excel sources and checkout BinOutput.

Offline evidence only; source hits do not establish target revision or runtime OpenConfig.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

from tools.audit_resource72 import git_hash, index_table, unique_object, verify_tree
from tools.audit_d02_resources72 import COMMIT, ROOT as CANDIDATE_ROOT

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261111-asc/c-talent-sources'
FILE = 'AvatarTalentExcelConfigData.json'
GITLINK = 'dac2a9d43332ef51879522cf39da2831b0ca0543'
PINS = {
    'keys': ('local/migration72/d02-resume-20261111-asc/c-talent-refs/main-third.private.json', '6ba484b3a4c65b3ed773e084fa95d559c91f1385abb0e7454c16606e1f76690a'),
    'candidate': ('local/migration72/d02-r3-candidate/ExcelBinOutput/' + FILE, 'f573e89101fc47a8a395def6b840f075dd7dfb52d6df69488b9fefbe2b6ab0d4'),
    'checkout': ('resources/ExcelBinOutput/' + FILE, '6b10e3bcf21dc0cb5f07e0abd74624af8c4fd853eb75689da9906f560841c886'),
    'resourceLoader': ('src/main/java/emu/grasscutter/data/ResourceLoader.java', 'fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015'),
    'openConfigLoader': ('src/main/java/emu/grasscutter/data/OpenConfigLoader.java', '698b37f47a1d528324dac720ac2d05546c768549c7ab8810a56be69bebc9832d'),
    'talentData': ('src/main/java/emu/grasscutter/data/excels/avatar/AvatarTalentData.java', '8bdcc424b2860e11e9e49bea4202e26991bdb7c133361e6e4f129d5ef75cbf8d'),
    'avatarJava': ('src/main/java/emu/grasscutter/game/avatar/Avatar.java', '9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d'),
    'sceneInfoNotify': ('src/main/java/emu/grasscutter/server/packet/send/PacketPlayerEnterSceneInfoNotify.java', '07763b4d1e515e1083681209acc11280c78abdfa6190669d7c3ca0b6da1d9201'),
}
EVIDENCE = ROOT / 'local/migration72/evidence'
BIN = ROOT / 'resources/BinOutput/Talent/AvatarTalents'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def read_json(raw):
    return json.loads(raw, object_pairs_hook=unique_object)


def git(*args, cwd=ROOT):
    return subprocess.check_output(['git', *args], cwd=cwd).decode('utf-8').strip()


def pinned_inputs():
    data = {}
    for name, (path, expected) in PINS.items():
        raw = (ROOT / path).read_bytes()
        if digest(raw) != expected:
            raise ValueError(name + ' SHA-256 mismatch')
        data[name] = raw
    commit = read_json((EVIDENCE / 'resource-source-commit.json').read_bytes())
    root_tree = read_json((EVIDENCE / 'resource-source-tree.json').read_bytes())
    excel_tree = read_json((EVIDENCE / 'resource-source-ExcelBinOutput-tree.json').read_bytes())
    if commit.get('commit') != COMMIT or commit.get('treeSHA') != CANDIDATE_ROOT:
        raise ValueError('candidate commit mismatch')
    root_entries = verify_tree(root_tree)
    excel_entries = verify_tree(excel_tree)
    entry = excel_entries[FILE]
    if (root_tree['sha'] != CANDIDATE_ROOT or root_entries['ExcelBinOutput']['sha'] != excel_tree['sha']
            or entry['size'] != len(data['candidate']) or entry['sha'] != git_hash('blob', data['candidate'])):
        raise ValueError('candidate tree/blob mismatch')
    if git('rev-parse', 'HEAD:resources') != GITLINK or git('rev-parse', 'HEAD', cwd=ROOT / 'resources') != GITLINK:
        raise ValueError('resources gitlink mismatch')
    excel_line = git('ls-tree', 'HEAD', '--', 'ExcelBinOutput/' + FILE, cwd=ROOT / 'resources')
    excel_blob = excel_line.split()[2]
    checkout_git_bytes = subprocess.check_output(['git', 'show', 'HEAD:ExcelBinOutput/' + FILE], cwd=ROOT / 'resources')
    if (git_hash('blob', checkout_git_bytes) != excel_blob or
            data['checkout'].replace(b'\r\n', b'\n') != checkout_git_bytes):
        raise ValueError('checkout Excel differs from gitlink blob beyond CRLF')
    tree_line = git('ls-tree', 'HEAD', '--', 'BinOutput/Talent/AvatarTalents', cwd=ROOT / 'resources')
    tree_sha = tree_line.split()[2]
    listing = git('ls-tree', '-r', 'HEAD', '--', 'BinOutput/Talent/AvatarTalents', cwd=ROOT / 'resources')
    tracked = {}
    for line in listing.splitlines():
        meta, path = line.split('\t', 1)
        mode, kind, sha = meta.split()
        if kind == 'blob' and path.endswith('.json'):
            tracked[path.removeprefix('BinOutput/Talent/AvatarTalents/')] = {'mode': mode, 'blob': sha}
    proof = {'candidateCommit': COMMIT, 'candidateRootTree': CANDIDATE_ROOT,
             'candidateExcelTree': excel_tree['sha'], 'candidateExcelBlob': entry['sha'],
             'resourcesGitlink': GITLINK, 'checkoutExcelBlob': excel_blob,
             'checkoutExcelGitBlobSHA256': digest(checkout_git_bytes),
             'checkoutExcelMatchesGitBlobAfterCRLFNormalization': True,
             'checkoutTalentTree': tree_sha}
    return data, tracked, proof


def lookup_keys(raw, expected_count=726):
    keys = read_json(raw)['currentDepotTalentLookupKeys']
    if (not isinstance(keys, list) or len(keys) != expected_count or
            any(type(k) is not int or k < 0 or k > 0x7fffffff for k in keys) or
            keys != sorted(set(keys))):
        raise ValueError('invalid, missing, duplicate, or unsorted lookup keys')
    return keys


def config(row):
    if row is None:
        return {'state': 'missing-row'}
    if 'openConfig' not in row:
        return {'state': 'null', 'fieldPresent': False, 'value': None}
    value = row['openConfig']
    if value is None:
        return {'state': 'null', 'fieldPresent': True, 'value': None}
    if not isinstance(value, str):
        raise ValueError('non-string OpenConfig')
    return {'state': 'empty' if len(value) == 0 else 'nonempty', 'fieldPresent': True, 'value': value}


def scan_bin(directory, tracked):
    if not directory.is_dir():
        raise ValueError('Talent source directory missing')
    sources, by_key = [], {}
    for file in sorted(directory.rglob('*.json')):
        if not file.is_file():
            continue
        rel = file.relative_to(directory).as_posix()
        raw = file.read_bytes()
        obj = read_json(raw)
        if not isinstance(obj, dict):
            raise ValueError('Talent source is not an object: ' + rel)
        blob = git_hash('blob', raw)
        record = {'path': rel, 'sha256': digest(raw), 'blob': blob,
                  'gitlinkBlob': tracked.get(rel, {}).get('blob'),
                  'matchesGitlinkBytes': tracked.get(rel, {}).get('blob') == blob,
                  'matchesGitlinkAfterCRLFNormalization': tracked.get(rel, {}).get('blob') == git_hash('blob', raw.replace(b'\r\n', b'\n')),
                  'directOpenConfigDirectory': file.parent == directory}
        sources.append(record)
        for name, value in obj.items():
            encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
            hit = {'path': rel, 'valueSHA256': digest(encoded), 'array': isinstance(value, list),
                   'effectCount': len(value) if isinstance(value, list) else None,
                   'directOpenConfigDirectory': record['directOpenConfigDirectory']}
            by_key.setdefault(name, []).append(hit)
    return sources, by_key, sorted(set(tracked) - {r['path'] for r in sources})


def compare(keys, candidate, checkout, hits):
    rows = []
    for key in keys:
        a, b = config(candidate.get(key)), config(checkout.get(key))
        def side(item):
            name = item.get('value')
            found = hits.get(name, []) if item['state'] in ('empty', 'nonempty') else []
            return {**item, 'binOutputHits': found, 'sourceCount': len(found),
                    'multipleSources': len(found) > 1}
        rows.append({'talentId': key, 'candidate': side(a), 'checkout': side(b),
                     'javaValueDiffers': (a['state'], a.get('value')) != (b['state'], b.get('value'))})
    return rows


def make_report(data, tracked, proof):
    keys = lookup_keys(data['keys'])
    candidate = index_table(data['candidate'], 'talentId')
    checkout = index_table(data['checkout'], 'talentId')
    sources, hits, missing_tracked = scan_bin(BIN, tracked)
    rows = compare(keys, candidate, checkout, hits)
    return {'task': 'E-01d1/R3 old Talent source comparison', 'proof': proof,
            'inputs': {name: {'path': path, 'sha256': sha} for name, (path, sha) in PINS.items()},
            'sourceFiles': sources, 'trackedJsonMissingFromCheckout': missing_tracked,
            'rows': rows,
            'summary': {'lookupKeys': len(keys), 'differentJavaValues': sum(r['javaValueDiffers'] for r in rows),
                        'candidateMissingRows': sum(r['candidate']['state'] == 'missing-row' for r in rows),
                        'checkoutMissingRows': sum(r['checkout']['state'] == 'missing-row' for r in rows)},
            'boundary': ('Comparison is limited to fixed lookup keys and current checkout AvatarTalents source files. '
                         'Missing field and explicit null both become Java null; empty and nonempty strings remain distinct. '
                         'Avatar filters null and empty before OpenConfig lookup, while PacketPlayerEnterSceneInfoNotify filters null only and looks up the empty string. '
                         'Hit is an exact JSON key, not proof of successful Java deserialization or target identity. '
                         'loadTalents recursively walks each directory and can reload nested files; multiple files are not resolved by traversal order. '
                         'OpenConfigLoader uses direct files in four directories plus cache; other directories, cache, parse validity and runtime winner are not established here. '
                         'Target OpenConfig, complete restoration and client acceptance remain unverified.'),
            'resourceReady': False, 'd02Complete': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='new JSON basename in private task directory')
    args = parser.parse_args()
    output = OUT / args.output
    if output.parent.resolve() != OUT.resolve() or output.suffix != '.json' or output.exists():
        parser.error('output must be a new JSON basename in the task directory')
    data, tracked, proof = pinned_inputs()
    raw = (json.dumps(make_report(data, tracked, proof), ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf-8')
    OUT.mkdir(parents=True, exist_ok=True)
    with output.open('xb') as stream:
        stream.write(raw)
    print(f'bytes={len(raw)} sha256={digest(raw)}')


if __name__ == '__main__':
    main()
