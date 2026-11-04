"""Deterministic source inventory, never a target-version protocol mapping.

Records lexical evidence, including disabled/unresolved and dynamic expressions.
Import closure over explicit P0 roots is a lower bound, not a Java call graph.
"""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re

JAVA = 'src/main/java/emu/grasscutter'
ROOTS = ('GetPlayerTokenReq', 'PlayerLoginReq', 'PingReq', 'SetPlayerBornDataReq',
         'EnterSceneReadyReq', 'SceneInitFinishReq', 'EnterSceneDoneReq', 'PostEnterSceneReq', 'UnionCmdNotify')


def clean_source(source, mask_strings=False):
    # Keep strings/character literals intact, and preserve offsets for evidence.
    pattern = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(pattern, lambda m: re.sub(r'[^\n]', ' ', m[0]) if mask_strings or m[0].startswith('/') else m[0], source)


def line_at(text, offset):
    return text.count('\n', 0, offset) + 1


def evaluate(expression, values):
    expression = expression.strip()
    if re.fullmatch(r'-?(?:\d+|0[xX][0-9a-fA-F]+)', expression):
        return int(expression, 16 if 'x' in expression.lower() else 10)
    return values.get(expression.removeprefix('PacketOpcodes.'))


def declarations(source):
    source = clean_source(source, True)
    rows = [dict(name=m[1], expression=m[2].strip(), line=line_at(source, m.start()), value=None)
            for m in re.finditer(r'public\s+static\s+final\s+int\s+(\w+)\s*=\s*([^;]+);', source)]
    values = {}
    for _ in range(len(rows) + 1):
        changed = False
        for row in rows:
            value = evaluate(row['expression'], values)
            if value is not None and row['value'] is None:
                row['value'] = values[row['name']] = value
                changed = True
        if not changed:
            break
    return rows


def handlers(source, values):
    source = clean_source(source, True)
    rows = []
    for m in re.finditer(r'@Opcodes\s*\(([^)]*)\)', source):
        args = m[1]
        value_match = re.search(r'(?:^|,)\s*value\s*=\s*([^,]+)', args)
        expression = value_match[1].strip() if value_match else args.split(',')[0].strip()
        following = re.search(r'\bclass\s+(\w+)', source[m.end():])
        rows.append(dict(expression=expression, name=expression.removeprefix('PacketOpcodes.'),
                         value=evaluate(expression, values), disabled=bool(re.search(r'disabled\s*=\s*true', args)),
                         line=line_at(source, m.start()), handler=following[1] if following else None))
    return rows


def calls(source):
    source = clean_source(source, True)
    rows = []
    for m in re.finditer(r'\b(super|new\s+BasePacket)\s*\(|\.\s*(setOpcode)\s*\(', source):
        depth, end, quote, escaped = 0, m.end(), None, False
        for end in range(m.end(), len(source)):
            char = source[end]
            if quote:
                if not escaped and char == quote:
                    quote = None
                escaped = char == '\\' and not escaped
                continue
            if char in ('"', "'"):
                quote = char
            elif char == '(':
                depth += 1
            elif char == ')':
                if depth == 0:
                    break
                depth -= 1
            elif char == ',' and depth == 0:
                break
        rows.append(dict(kind=m[1] or m[2], expression=source[m.end():end].strip(), line=line_at(source, m.start())))
    return rows


def proto_closure(imports, roots):
    seen, missing, pending = set(), set(), list(roots)
    while pending:
        name = pending.pop()
        if name in seen or name in missing:
            continue
        if name not in imports:
            missing.add(name)
        else:
            seen.add(name)
            pending.extend(imports[name])
    return dict(files=sorted(seen), missing=sorted(missing))


def inventory(repo):
    opcode_path = repo / JAVA / 'net/packet/PacketOpcodes.java'
    constants = declarations(opcode_path.read_text('utf-8'))
    values = {row['name']: row['value'] for row in constants if row['value'] is not None}
    receive, annotations, send, refs, config, source_hashes = [], [], [], [], [], {}
    parents = {}
    root_packets = {'PacketHead', 'QueryRegionListHttpRsp', 'QueryCurrRegionHttpRsp'} | set(ROOTS)
    root_files = {f'{JAVA}/server/packet/recv/Handler{name}.java' for name in ROOTS}
    root_files.add(f'{JAVA}/game/player/Player.java')
    for path in sorted((repo / JAVA).rglob('*.java')):
        if path.name == 'BuildConfig.java':
            continue  # Generated build timestamp/commit, not protocol source.
        relative = path.relative_to(repo).as_posix()
        source = path.read_text('utf-8')
        clean = clean_source(source, True)
        source_hashes[relative] = hashlib.sha256(source.encode('utf-8')).hexdigest()
        parents.update(re.findall(r'\bclass\s+(\w+)\s+extends\s+(\w+)', clean))
        annotations.extend(dict(file=relative, **row) for row in handlers(source, values))
        # Only super in a BasePacket subclass is an opcode; other constructors aren't.
        is_packet = bool(re.search(r'\bextends\s+BasePacket\b', clean))
        for row in calls(source):
            if row['kind'] == 'super' and not is_packet:
                continue
            send.append(dict(file=relative, value=evaluate(row['expression'], values), **row))
        for m in re.finditer(r'\bPacketOpcodes\.(\w+)', clean):
            refs.append(dict(file=relative, line=line_at(clean, m.start()), name=m[1], value=values.get(m[1])))
        if '/config/' in relative:
            for line, text in enumerate(clean.splitlines(), 1):
                if re.search(r'(?i)opcode|cmdid|setPlayerBornData|packet.*(?:list|=)', text):
                    config.append(dict(file=relative, line=line, source=text.strip()))
        if relative in root_files:
            root_packets.update(re.findall(r'\bnew\s+Packet(\w+)\s*\(', clean))
    for row in annotations:
        parent, visited = row['handler'], set()
        while parent in parents and parent not in visited:
            visited.add(parent)
            parent = parents[parent]
        if parent == 'PacketHandler':
            receive.append(row)
    duplicates, active = defaultdict(list), defaultdict(list)
    for row in constants:
        if row['value'] is not None and row['value'] > 0:
            duplicates[row['value']].append(row['name'])
    for row in receive:
        if row['value'] is not None and row['value'] > 0 and not row['disabled']:
            active[row['value']].append(row['handler'])
    imports, proto_hashes = {}, {}
    for path in sorted((repo / 'src/main/proto').glob('*.proto')):
        text = path.read_text('utf-8')
        imports[path.name] = re.findall(r'\bimport\s+(?:(?:public|weak)\s+)?"([^"]+)"', clean_source(text))
        proto_hashes[path.name] = hashlib.sha256(text.encode('utf-8')).hexdigest()
    # Follow actual Java outer class imports of direct send constructors as well.
    for name in sorted(root_packets.copy()):
        path = repo / JAVA / f'server/packet/send/Packet{name}.java'
        if path.is_file():
            root_packets.update(re.findall(r'\b(\w+)OuterClass\b', clean_source(path.read_text('utf-8'))))
    closure = proto_closure(imports, sorted(name + '.proto' for name in root_packets))
    fanout = repo / 'data/proto-fanout.json'
    report = dict(
        scope='Source baseline inventory only; no verified 7.2 mapping',
        counts=dict(constants=len(constants), positive=sum(v > 0 for v in values.values()),
                    negative=sum(v < 0 for v in values.values()), positiveDistinct=len(duplicates),
                    positiveAliasGroups=sum(len(n) > 1 for n in duplicates.values()),
                    handlers=len(receive), activePositiveHandlers=sum(len(n) for n in active.values()),
                    activeCollisionGroups=sum(len(n) > 1 for n in active.values()),
                    sendSites=len(send), dynamicSendSites=sum(r['value'] is None for r in send), protos=len(imports)),
        constants=constants, positiveAliasCandidates={str(k): v for k, v in sorted(duplicates.items()) if len(v) > 1},
        handlers=receive, opcodeAnnotations=annotations,
        activeHandlerCollisions={str(k): v for k, v in sorted(active.items()) if len(v) > 1},
        sends=send, opcodeReferences=refs, configurationCandidates=config,
        fanout=json.loads(fanout.read_text('utf-8')) if fanout.is_file() else None,
        p0StaticLowerBound=dict(rootFiles=sorted(root_files), rootMessages=sorted(root_packets), **closure),
        javaSHA256=source_hashes, protoSHA256=proto_hashes,
        limitations=['Lexical inventory, not runtime registrations or a complete Java call graph',
                     'Generated BuildConfig.java excluded from source fingerprints',
                     'Alias groups require canonical-name review; equal values do not imply equal semantics',
                     'P0 includes whole Player.java as a conservative root; indirect manager/event/plugin sends need review',
                     'Configuration candidates are lexical; runtime config overrides and numeric switch dispatch need review',
                     'Client-required messages and actual ordering are unknown until 7.2 observations'])
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    report = inventory(args.repo.resolve())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    print(json.dumps(report['counts'], indent=2))


if __name__ == '__main__':
    main()
