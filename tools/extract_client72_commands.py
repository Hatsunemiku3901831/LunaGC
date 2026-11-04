"""Hash-bound static command/type bindings; no business names or runtime claims.

Slot 22 on NCKOJEOGIIC is linked to the packet command word by the native send
and frame paths documented in docs/CLIENT_72_NETWORK.md. Duplicate values stay
ambiguous; this output cannot be used as a ready server opcode table.
"""
import argparse
from collections import defaultdict
import json
from pathlib import Path

try:
    from .extract_client72 import HASHES, read_verified, extract
except ImportError:
    from extract_client72 import HASHES, read_verified, extract

BASE_NAME, GETTER, SLOT = 'NCKOJEOGIIC', 'AMPEGOHOBJM', 22


def classify_commands(records):
    by_index = {r['index']: r for r in records}
    if len(by_index) != len(records):
        raise ValueError('Duplicate type index')
    bases = [r for r in records if r['name'] == BASE_NAME and not r['namespace']]
    if len(bases) != 1:
        raise ValueError('Expected one verified packet base')
    base = bases[0]
    roots = [m for m in base['methods'] if m['name'] == GETTER]
    if len(roots) != 1 or roots[0]['virtualSlot'] != SLOT or not int(roots[0]['flags'], 16) & 0x40:
        raise ValueError('Packet base virtual contract changed')

    def descendant(row):
        seen = set()
        while row['index'] != base['index']:
            if row['index'] in seen:
                raise ValueError('Cyclic parent chain')
            seen.add(row['index'])
            if 'parent' not in row:
                raise ValueError('Structures required')
            parent = row['parent']
            if parent is None:
                return False
            if parent['typeDefinition'] not in by_index:
                raise ValueError('Missing parent definition')
            row = by_index[parent['typeDefinition']]
        return True

    rows, used = [], defaultdict(list)
    for row in records:
        candidates = [m for m in row['methods'] if m['name'] == GETTER]
        if not candidates or row['index'] == base['index']:
            continue
        if len(candidates) != 1:
            raise ValueError('Multiple command getter declarations')
        method = candidates[0]
        if not descendant(row):
            raise ValueError('Getter name without verified packet ancestry')
        flags = int(method['flags'], 16)
        if method['virtualSlot'] != SLOT or method['parameterCount'] or not flags & 0x40 or flags & 0x10:
            raise ValueError('Invalid command getter signature')
        value = method.get('literalU16Candidate')
        if type(value) is not int or not 1 <= value <= 65535:
            raise ValueError('Nonliteral/invalid command getter; needs independent analysis')
        used[value].append(row['name'])
        rows.append(dict(typeIndex=row['index'], clientType=row['name'], cmdId=value,
                         getter=method['registeredAddress'], methodIndex=method['index']))
    return dict(schemaVersion=1, inputSHA256=HASHES,
                evidence='docs/CLIENT_72_NETWORK.md#静态包号调用链',
                bindingStatus='static-confirmed', businessNamesStatus='unresolved',
                runtimeStatus='not-tested', transportDomainsStatus='unresolved',
                packetBase=dict(typeIndex=base['index'], clientType=BASE_NAME, slot=SLOT),
                counts=dict(types=len(rows), distinctValues=len(used)),
                duplicateValues=[dict(cmdId=v, clientTypes=names, status='ambiguous-domain-or-alias')
                                 for v, names in sorted(used.items()) if len(names) > 1],
                commands=sorted(rows, key=lambda r: r['typeIndex']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if output.is_relative_to(root):
        parser.error('Write outside the read-only client directory')
    files = [read_verified(root / name, sha) for name, sha in HASHES.items()]
    records, _ = extract(files[0], files[1], startup=files[2], structures=True)
    report = classify_commands(records)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Compact one row per command, stable order; no extraction timestamps.
    commands = report.pop('commands')
    prefix = json.dumps(report, ensure_ascii=False, indent=2)[:-2]
    output.write_text(prefix + ',\n  "commands": [\n' + ',\n'.join(
        '    ' + json.dumps(row, ensure_ascii=False, separators=(',', ':')) for row in commands) + '\n  ]\n}\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'inputSHA256'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
