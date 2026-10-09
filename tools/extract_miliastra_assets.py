#!/usr/bin/env python3
"""Adapt literal MIT asset schemas; never run code from the reference checkout."""
import hashlib
import json
import subprocess
from pathlib import Path

def main():
    root = Path(__file__).resolve().parents[1]
    reference = root / 'debug/UGC-File-Generate-Utils/proto'
    names = {'gia': 'MiliastraAssetCollection', 'asset': 'MiliastraAssetDefinitions',
             'entity': 'MiliastraAssetEntity', 'camera': 'MiliastraAssetCamera'}
    commit = subprocess.check_output(['git', '-C', str(reference), 'rev-parse', 'HEAD'], text=True).strip()
    hashes = {}
    for original, adapted in names.items():
        source = reference / (original + '.proto')
        hashes[source.name] = hashlib.sha256(source.read_bytes()).hexdigest()
        content = source.read_text(encoding='utf-8')
        content = content.replace('game.gia', 'lunagc.miliastra.assets')
        for old, new in names.items():
            content = content.replace('"' + old + '.proto"', '"' + new + '.proto"')
        content = content.replace('syntax = "proto3";', 'syntax = "proto3";\n'
            'option java_package = "emu.grasscutter.game.beyond.proto.assets";\n'
            'option java_outer_classname = "' + adapted + '";')
        header = '// Adapted from luern0313, MIT, proto submodule ' + commit + '.\n'
        (root / 'src/main/proto' / (adapted + '.proto')).write_text(header + content, encoding='utf-8', newline='\n')
    output = root / 'src/main/resources/miliastra'
    (output / 'LICENSE.assets-protocol').write_bytes((reference / 'LICENSE').read_bytes())
    provenance = {'source': 'https://github.com/luern0313/UGC-File-Generate-Utils',
                  'protoCommit': commit, 'license': 'MIT', 'verifiedClientVersion': None,
                  'sourceSha256': hashes, 'changes': ['package', 'imports', 'Java options', 'attribution']}
    (output / 'assets-protocol.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(provenance))

if __name__ == '__main__':
    main()
