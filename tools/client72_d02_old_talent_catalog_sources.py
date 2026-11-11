"""Pin current OpenConfig source overlays only for the fixed old-account Talent keys."""
import argparse
import collections
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'local/migration72/d02-resume-20261111-asc/c-talent-sources/third.private.json'
REPORT_SHA = '19be8f2bde7e36eddb7e2d844c946e8b7d78c877c35f43cda4593353ab8f2cb0'
OUT = ROOT / 'local/migration72/d02-resume-20261111b/talent'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    if Path(args.output).name != args.output or not args.output.endswith('.json'):
        parser.error('use a new task-local JSON basename')
    raw = REPORT.read_bytes()
    if hashlib.sha256(raw).hexdigest() != REPORT_SHA:
        raise ValueError('fixed old-account Talent source report drift')
    report = json.loads(raw)
    keys = {r['candidate']['value'] for r in report['rows'] if r['candidate']['state'] == 'nonempty'}
    directories = ['EquipTalents', 'AvatarTalents', 'RelicTalents', 'TeamTalents']
    hits = collections.defaultdict(list)
    files, errors = [], []
    for directory in directories:
        for path in sorted((ROOT / 'resources/BinOutput/Talent' / directory).glob('*.json')):
            value = path.read_bytes()
            name = path.relative_to(ROOT).as_posix()
            files.append({'path': name, 'bytes': len(value), 'sha256': hashlib.sha256(value).hexdigest()})
            try:
                parsed = json.loads(value)
            except (ValueError, UnicodeError) as error:
                errors.append((name, str(error)))
                continue
            if isinstance(parsed, dict):
                for key in keys & parsed.keys():
                    hits[key].append(name)
    output = {'sourceReportSHA256': REPORT_SHA, 'directoryOrder': directories, 'files': files,
              'hits': dict(sorted(hits.items())), 'missing': sorted(keys - hits.keys()), 'errors': errors}
    encoded = json.dumps(output, sort_keys=True, indent=2).encode('utf8')
    with (OUT / args.output).open('xb') as stream:
        stream.write(encoded)
    print(json.dumps({'files': len(files), 'keys': len(keys), 'missing': len(output['missing']),
                      'multipleSources': sum(len(v) > 1 for v in hits.values()), 'errors': len(errors),
                      'sha256': hashlib.sha256(encoded).hexdigest()}))


if __name__ == '__main__':
    main()
