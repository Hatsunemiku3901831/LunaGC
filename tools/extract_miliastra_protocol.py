#!/usr/bin/env python3
"""Copy literal protocol metadata from the pinned MIT reference; execute no upstream code."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, default=root / 'debug/WebMiliastraNodesEditor/Genshin-Impact-Miliastra-Wonderland-Code-Node-Editor-Pack')
    parser.add_argument('--output', type=Path, default=root / 'src/main/resources/miliastra/node-protocol.json')
    args = parser.parse_args()
    source = args.reference / 'utils/node_data/index.json'
    data = json.loads(source.read_text(encoding='utf-8'))
    selected = {key: data[key] for key in ['Version', 'GameVersion', 'TypesList', 'NodesList', 'EnumList', 'ClientEnumList', 'GraphConstList']}
    result = {'source': 'https://github.com/Wu-Yijun/Genshin-Impact-Miliastra-Wonderland-Code-Node-Editor-Pack',
              'commit': subprocess.check_output(['git', '-C', str(args.reference), 'rev-parse', 'HEAD'], text=True).strip(),
              'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'license': 'MIT',
              'verifiedClientVersion': None, 'data': selected}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({'nodes': len(data['NodesList']), 'types': len(data['TypesList']), 'output': str(args.output)}))

if __name__ == '__main__':
    main()
