"""Pinned AstaPS source comparison; never a protocol acceptance or deployment gate.

Only Python's standard library is needed. Actual protoc compilation checks the
fixed source tree; the source index retains relative file/line provenance.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

try:
    from .extract_client72 import HASHES
    from .inventory_protocol import clean_source
except ImportError:
    from extract_client72 import HASHES
    from inventory_protocol import clean_source

ROOT = Path(__file__).resolve().parents[1]
COMMIT = 'a49f51aa6f2787ffbc25cee46801b70dee860d23'
TREE = '9940bf7756ce5ff8be8a836a4485a645b490c2e1'
PROTO_DIGEST = '86d9e71e600ca29b286c53a17bffb94657fce7e797d837c1f0108b33cd91be18'
MAPPING_SHA256 = 'e72a9161cc7420cde7f0ef59bcb82a23103172ca51d20c3213e952f90e918048'
KEY_FILES = (
    'LICENSE', 'LICENSE-ClassGraph.txt', 'README.md',
    'docs/protocol-generation-7.2.md', 'docs/protocol-7.2-gaps.md',
    'tools/protocol/remap/remap_71_to_72.py',
    'tools/protocol/remap/recover_lost_72.py',
    'tools/protocol/remap/export_proto_72.py',
    'src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java',
    'src/main/java/emu/grasscutter/server/game/GameSession.java',
    'src/main/java/emu/grasscutter/server/game/GameServerPacketHandler.java',
    'src/main/java/emu/grasscutter/server/packet/send/PacketGetPlayerTokenRsp.java',
    'src/main/java/emu/grasscutter/server/packet/recv/HandlerPlayerLoginReq.java',
    'src/generated/java/emu/grasscutter/net/proto/GetPlayerTokenRspOuterClass.java',
)
SCALARS = set('double float int64 uint64 int32 fixed64 fixed32 bool string bytes uint32 sfixed32 sfixed64 sint32 sint64'.split())


def digest(value):
    return hashlib.sha256(value).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode('utf8')


def text_hash(path):
    return digest(path.read_text('utf8').encode('utf8'))


def verify_reference(reference):
    def git(*args):
        return subprocess.run(['git', '-C', str(reference), *args], capture_output=True, check=True).stdout.decode('utf8').strip()
    head, tree, status = git('rev-parse', 'HEAD'), git('rev-parse', 'HEAD^{tree}'), git('status', '--porcelain', '--untracked-files=all')
    if head != COMMIT or tree != TREE or status:
        raise ValueError('Expected the fixed clean AstaPS commit/tree')
    return dict(commit=head, gitTree=tree, clean=True)


def parse_proto(source, file):
    """Index the fixed exporter's line-oriented declarations, rejecting omissions.

    This is deliberately an index of the reviewed export format, not a general
    replacement for protoc. Protoc compiles the complete input independently.
    """
    messages, enums, stack = {}, {}, []
    for line, raw in enumerate(clean_source(source).splitlines(), 1):
        raw = raw.strip()
        if not raw:
            continue
        block = re.fullmatch(r'(message|enum|oneof)\s+(\w+)\s*\{', raw)
        if block:
            kind, name = block.groups()
            parent = next((x[2] for x in reversed(stack) if x[0] == 'message'), '')
            full = parent + '.' + name if parent else name
            if kind == 'message':
                if full in messages:
                    raise ValueError('Duplicate message: ' + full)
                messages[full] = dict(file=file, line=line, fields=[])
            elif kind == 'enum':
                enums[full] = {}
            stack.append((kind, name, full))
            continue
        if raw in ('}', '};'):
            if not stack:
                raise ValueError('Unbalanced Proto block')
            stack.pop()
            continue
        if raw.startswith(('syntax ', 'package ', 'import ', 'option ', 'reserved ', 'extensions ')):
            continue
        if stack and stack[-1][0] == 'enum':
            value = re.fullmatch(r'(\w+)\s*=\s*(-?\d+)(?:\s*\[[^]]+\])?;', raw)
            if not value:
                raise ValueError(f'Unindexed enum declaration: {file}:{line}')
            enums[stack[-1][2]][value[1]] = int(value[2])
            continue
        field = re.fullmatch(r'(repeated\s+|optional\s+)?(map<[^>]+>|[\w.]+)\s+(\w+)\s*=\s*(\d+)(?:\s*\[([^]]+)\])?;', raw)
        if not field or not stack:
            raise ValueError(f'Unindexed Proto declaration: {file}:{line}')
        label, typ, name, tag, options = field.groups()
        owner = next(x[2] for x in reversed(stack) if x[0] == 'message')
        entry = dict(name=name, tag=int(tag), declaredType=typ, cardinality='repeated' if label == 'repeated ' else 'singular',
                     explicitPacked=None if not options or 'packed' not in options else bool(re.search(r'\bpacked\s*=\s*true\b', options)),
                     oneof=stack[-1][1] if stack[-1][0] == 'oneof' else None, file=file, line=line)
        messages[owner]['fields'].append(entry)
    if stack:
        raise ValueError('Unclosed Proto block')
    return messages, enums


def resolve_shapes(messages, enums):
    def resolve(owner, typ):
        if typ in SCALARS:
            return typ, None
        scope = owner
        while scope:
            candidate = scope + '.' + typ
            if candidate in messages or candidate in enums:
                return ('enum' if candidate in enums else 'message'), candidate
            scope = scope.rpartition('.')[0]
        if typ not in messages and typ not in enums:
            raise ValueError('Unresolved indexed type: ' + typ)
        return ('enum' if typ in enums else 'message'), typ
    for owner, message in messages.items():
        tags, names = set(), set()
        for f in message['fields']:
            if f['tag'] in tags or f['name'] in names:
                raise ValueError('Duplicate field/tag: ' + owner)
            tags.add(f['tag']); names.add(f['name'])
            typ = f['declaredType']
            if typ.startswith('map<'):
                key, value = [x.strip() for x in typ[4:-1].split(',')]
                value_type, value_name = resolve(owner, value)
                f.update(type='map', cardinality='map', mapKeyType=key, mapValueType=value_type, mapValueTypeName=value_name, typeName=None)
            else:
                kind, name = resolve(owner, typ)
                f.update(type=kind, typeName=name)
            f['packed'] = f['explicitPacked'] if f['explicitPacked'] is not None else f['cardinality'] == 'repeated' and f['type'] not in ('message', 'string', 'bytes')


def parse_opcodes(source):
    rows = {}
    cleaned = clean_source(source)
    originals = source.splitlines()
    for line, raw in enumerate(cleaned.splitlines(), 1):
        match = re.fullmatch(r'\s*public static final int (\w+) = (-?\d+);\s*', raw)
        if match:
            old = re.search(r'7\.1:\s*(\d+)', originals[line - 1])
            rows[match[1]] = dict(value=int(match[2]), line=line, legacy71=int(old[1]) if old else None)
        elif re.search(r'public static final int', raw):
            raise ValueError('Unindexed opcode expression')
    return rows


def compare_targets(mapping, messages, opcodes):
    result = []
    for schema in mapping['schemas']:
        if schema['status'] == 'unresolved':
            continue
        name = schema['semanticName']; source = messages.get(name)
        target_op = next(x for x in mapping['opcodes'] if x['semanticName'] == name)
        fields = []
        for target in schema['fields']:
            upstream = next((f for f in source['fields'] if f['tag'] == target['tag']), None) if source else None
            shape = None
            if upstream:
                kind_match = upstream['type'] == target['type'] or upstream['type'] == 'enum' and target['type'] == 'int32'
                shape = kind_match and upstream['cardinality'] == target['cardinality'] and upstream['packed'] == target['packed']
                if target['type'] == 'map':
                    shape = shape and upstream['mapKeyType'] == target['keyType'] and upstream['mapValueType'] == target['valueType']
            target_keys = ('clientField', 'tag', 'type', 'cardinality', 'packed', 'semanticField', 'status', 'clientNestedType', 'keyType', 'valueType')
            fields.append(dict(target={k: target[k] for k in target_keys if k in target},
                proposedFieldName=target['semanticField'] if target['status'] == 'verified' else target['clientField'],
                astaAtSameTag=upstream, sameOuterShape=shape,
                semanticConflict=bool(upstream and target['status'] == 'verified' and upstream['name'] != target['semanticField'])))
        opcode = opcodes.get(name)
        result.append(dict(message=name, clientType=target_op['clientType'], targetCmdId=target_op['cmdId'], astaOpcode=opcode,
            opcodeMatches=target_op['cmdId'] == opcode['value'] if opcode and target_op['cmdId'] is not None else None,
            astaSchema=None if not source else {k: source[k] for k in ('file', 'line')}, fields=fields,
            counts=dict(target=len(fields), asta=len(source['fields']) if source else 0,
                matchingOuterShape=sum(f['sameOuterShape'] is True for f in fields),
                outerShapeConflict=sum(f['sameOuterShape'] is False for f in fields),
                missingTargetTags=sum(f['astaAtSameTag'] is None for f in fields),
                verifiedSemanticConflict=sum(f['semanticConflict'] for f in fields))))
    return result


def parked_wire_check(reference, protoc):
    # Independent fixed bytes: keys = 20002<<3, 20006<<3, 20007<<3; nonzero values.
    expected = bytes.fromhex('90e20903b0e20901b8e20901')
    root = reference / 'src/main/proto'
    text = b'account_type: 1 reg_platform: 3 is_proficient_player: true'
    result = subprocess.run([str(protoc), '--proto_path=' + str(root), '--encode=GetPlayerTokenRsp',
                             str(root / 'GetPlayerTokenRsp.proto')], input=text, capture_output=True, check=True)
    if result.stdout != expected:
        raise ValueError('Parked-field independent wire mismatch')
    decoded = subprocess.run([str(protoc), '--proto_path=' + str(root), '--decode=GetPlayerTokenRsp',
                              str(root / 'GetPlayerTokenRsp.proto')], input=expected, capture_output=True, check=True)
    expected_fields = {b'reg_platform: 3', b'is_proficient_player: true', b'account_type: 1'}
    if set(decoded.stdout.splitlines()) != expected_fields:
        raise ValueError('Parked-field independent read mismatch')
    return dict(input=text.decode('ascii'), expectedWireHex=expected.hex(), actualWireHex=result.stdout.hex(),
                independentWireAssert=True, independentReadAssert=True,
                meaning='20000+ is a migration marker, not a protobuf send/read prohibition')


def audit(reference, protoc, mapping_path=ROOT / 'protocol/cn-7.2/mappings.json'):
    identity = verify_reference(reference)
    if text_hash(mapping_path) != MAPPING_SHA256:
        raise ValueError('Target mapping source changed; review required')
    mapping = json.loads(mapping_path.read_text('utf8'))
    sources = sorted((reference / 'src/main/proto').glob('*.proto'))
    manifest = [dict(file=p.relative_to(reference).as_posix(), sha256=text_hash(p)) for p in sources]
    tree_digest = digest(canonical(manifest))
    if tree_digest != PROTO_DIGEST:
        raise ValueError('Pinned Proto source tree changed')
    messages, enums = {}, {}
    for path in sources:
        ms, es = parse_proto(path.read_text('utf8'), path.relative_to(reference).as_posix())
        if messages.keys() & ms.keys() or enums.keys() & es.keys():
            raise ValueError('Duplicate indexed type across files')
        messages.update(ms); enums.update(es)
    resolve_shapes(messages, enums)
    with tempfile.TemporaryDirectory(prefix='astaps72-audit-') as tmp:
        args = Path(tmp) / 'args.txt'; descriptor = Path(tmp) / 'audit.pb'
        args.write_text('\n'.join(['--proto_path=' + (reference / 'src/main/proto').as_posix(),
            '--descriptor_set_out=' + descriptor.as_posix()] + [p.name for p in sources]), encoding='utf8')
        compiled = subprocess.run([str(protoc), '@' + str(args)], capture_output=True, check=True)
        compilation = dict(success=True, warningCount=compiled.stderr.count(b'warning:'),
            protocVersion=subprocess.run([str(protoc), '--version'], capture_output=True, check=True).stdout.decode('ascii').strip())
    op_path = 'src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java'
    opcodes = parse_opcodes((reference / op_path).read_text('utf8'))
    all_fields = [dict(f, message=name) for name, m in sorted(messages.items()) for f in m['fields']]
    parked = [f for f in all_fields if f['tag'] >= 20000]
    values = Counter(x['value'] for x in opcodes.values())
    duplicates = {str(v): sorted(n for n, x in opcodes.items() if x['value'] == v) for v in sorted(values) if v > 0 and values[v] > 1}
    maps = sum(f['type'] == 'map' for f in all_fields)
    counts = dict(protoFiles=len(sources), messagesIncludingMapEntries=len(messages) + maps,
        messagesExcludingMapEntries=len(messages), enums=len(enums), fieldsExcludingMapEntries=len(all_fields),
        parkedFields=len(parked), parkedMessages=len({f['message'] for f in parked}), opcodes=len(opcodes),
        zeroOpcodes=values[0], positiveOpcodes=sum(x['value'] > 0 for x in opcodes.values()),
        negativeOpcodes=sum(x['value'] < 0 for x in opcodes.values()), duplicatePositiveOpcodeValues=len(duplicates))
    gaps = (reference / 'docs/protocol-7.2-gaps.md').read_text('utf8')
    legacy_section = gaps.split('## Messages with no 7.2 counterpart')[1].split('## Fields with no 7.2 number')[0]
    legacy_names = re.findall(r'`(\w+)`', legacy_section)
    legacy_messages = {name: messages[name] for name in legacy_names if name in messages}
    counts.update(documentedLegacyMessages=len(legacy_messages),
                  documentedLegacyFields=sum(len(m['fields']) for m in legacy_messages.values()))
    targets = compare_targets(mapping, messages, opcodes)
    counts.update(targetMessages=len(targets), targetFields=sum(t['counts']['target'] for t in targets),
        matchingTargetOuterShape=sum(t['counts']['matchingOuterShape'] for t in targets),
        missingTargetTags=sum(t['counts']['missingTargetTags'] for t in targets),
        targetOuterShapeConflicts=sum(t['counts']['outerShapeConflict'] for t in targets),
        verifiedSemanticConflicts=sum(t['counts']['verifiedSemanticConflict'] for t in targets))
    summary = dict(schemaVersion=1, inputSHA256=HASHES, reference=identity,
        targetMapping=dict(file='protocol/cn-7.2/mappings.json', sha256=MAPPING_SHA256, hashConvention='UTF-8 LF'),
        protoSourceTree=dict(fileCount=len(sources), sha256=tree_digest, hashConvention='SHA-256(canonical sorted file/LF-SHA256 rows)'),
        keySourceSHA256={name: text_hash(reference / name) for name in KEY_FILES}, counts=counts,
        compilation=compilation, targets=targets, parkedWireCheck=parked_wire_check(reference, protoc),
        license=dict(project='GPL-3.0', source='LICENSE', classGraphNotice='LICENSE-ClassGraph.txt (MIT dependency only)',
            attribution=['AstaPS', 'Grasscutter', 'LunaGC', 'HunkyMeow', 'kitkat 7.1 and NT 7.2 dump names per generation document']),
        runtimeStatus='not-tested', targetProtocolReady=False,
        boundaries=['Upstream remapped 7.1 schema is candidate evidence, not target-client proof',
            'Same outer shape excludes recursive nested schema and unresolved business meaning',
            'Only local verified semantic names may replace original target clientField names',
            'No mappings status changed; no runtime/source/Proto imported from AstaPS by this audit',
            'No service/client action; no login/scene/D-02 acceptance'])
    report = dict(summary=summary, sourceManifest=manifest, messages=messages, enums=enums,
        opcodes=opcodes, parkedFields=parked, duplicatePositiveOpcodes=duplicates, documentedLegacyMessages=legacy_messages)
    return report, summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--protoc', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--summary-output', type=Path, required=True)
    args = parser.parse_args()
    report, summary = audit(args.reference.resolve(), args.protoc.resolve())
    for path, value in ((args.output, report), (args.summary_output, summary)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(summary['counts'], sort_keys=True))


if __name__ == '__main__':
    main()
