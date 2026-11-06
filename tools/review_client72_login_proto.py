"""A-03c source candidates, protoc dependencies and an unchanged CN72 three-layer gap matrix."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

try:
    from .client72_java_surface import compile_scanner, scan_sources, method_id
    from .inventory_protocol import clean_source
    from .review_client72_login_dependencies import recover as dependencies, summary
except ImportError:
    from client72_java_surface import compile_scanner, scan_sources, method_id
    from inventory_protocol import clean_source
    from review_client72_login_dependencies import recover as dependencies, summary


def digest(path):
    return hashlib.sha256(path.read_text('utf8').encode('utf8')).hexdigest()


def packet_candidates(repo, graph, rows):
    sources = {}
    for path in sorted((repo / 'src/main/java/emu/grasscutter/server/packet/send').glob('*.java')):
        text = clean_source(path.read_text('utf8'), True)
        declaration = re.search(r'\bclass\s+(\w+)\s+extends\s+([\w.]+)', text)
        if declaration:
            sources[declaration[1]] = (path, declaration[2].split('.')[-1])
    base = repo / 'src/main/java/emu/grasscutter/net/packet/BasePacket.java'
    sources['BasePacket'] = (base, None)
    selected = {row['id']: row for row in graph['methods']}
    sites = defaultdict(list)
    for method in rows:
        if method['kind'] != 'method' or method_id(method) not in selected:
            continue
        direct = {(v['constructedType'], v['constructionLine']) for v in selected[method_id(method)]['directPacketArguments']}
        for call in method['calls']:
            name = call['selector'].split('.')[-1].split('<')[0]
            if call['kind'] != 'construct' or not (name in sources or name.startswith('Packet')):
                continue
            site = dict(method=method_id(method), file=method['file'], line=call['line'],
                        lambdaDepth=call['lambdaDepth'], directSendArgument=(call['selector'], call['line']) in direct)
            if name == 'BasePacket':
                raw = (repo / method['file']).read_bytes().decode('utf8').encode('utf-16-le')
                site['constructorArguments'] = [raw[start * 2:end * 2].decode('utf-16-le')
                                                for start, end in call['argumentSpansUTF16']]
            sites[name].append(site)
    candidates = []
    for name, references in sorted(sites.items()):
        chain, pending, seen = [], name, set()
        while pending in sources and pending not in seen:
            seen.add(pending)
            path, pending = sources[pending]
            chain.append(path)
        protos = sorted({value + '.proto' for path in chain for value in re.findall(
            r'\b(\w+)OuterClass\b', clean_source(path.read_text('utf8'), True))})
        candidates.append(dict(javaType=name, dynamicOpcode=name == 'BasePacket',
            basePacketInheritanceObserved='BasePacket' in seen,
            unresolvedParent=pending, sourceSites=references,
            javaSourceSHA256={p.relative_to(repo).as_posix(): digest(p) for p in chain},
            directProtoReferences=protos,
            meaning='legacy source construction; not proof of sending, CN72 identity or necessity'))
    return candidates


def read_descriptors(repo, roots, protoc, java_home, protobuf_jar, work):
    descriptor = work / 'dependencies.pb'
    command = [str(protoc), '--proto_path=' + str(repo / 'src/main/proto'),
               '--include_imports', '--descriptor_set_out=' + str(descriptor), *sorted(roots)]
    compiled = subprocess.run(command, capture_output=True, check=True)
    subprocess.run([str(java_home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(protobuf_jar),
                    '-d', str(work), str(repo / 'tools/Client72ProtoDependencies.java')], capture_output=True, check=True)
    result = subprocess.run([str(java_home / 'bin/java.exe'), '-Dfile.encoding=UTF-8', '-cp',
                             str(work) + ';' + str(protobuf_jar), 'Client72ProtoDependencies', str(descriptor)],
                            capture_output=True, check=True)
    files, types, fields, symbols = {}, {}, [], []
    for line in result.stdout.decode('utf8').splitlines():
        v = line.split('\t')
        if v[0] == 'file':
            files[v[1]] = dict(package=v[2], javaOuterClass=v[3], imports=[])
        elif v[0] == 'import':
            files[v[1]]['imports'].append(v[2])
        elif v[0] in ('message', 'enum'):
            types[v[2]] = dict(file=v[1], kind=v[0], mapEntry=v[0] == 'message' and v[3] == 'true')
        elif v[0] == 'field':
            fields.append(dict(file=v[1], owner=v[2], name=v[3], legacyTag=int(v[4]), type=v[5],
                label=v[6], typeName=v[7] or None, oneofIndex=int(v[8]) if v[8] else None,
                explicitPacked=(v[9] == 'true') if v[9] else None, proto3Optional=v[10] == 'true'))
        elif v[0] == 'symbol':
            symbols.append(dict(file=v[1], owner=v[2], name=v[3], legacyValue=int(v[4])))
        else:
            raise ValueError('Unexpected descriptor row')
    for field in fields:
        if field['typeName'] and field['typeName'] not in types:
            raise ValueError('Descriptor type dependency missing: ' + field['typeName'])
    return dict(files=dict(sorted(files.items())), types=dict(sorted(types.items())),
                fields=sorted(fields, key=lambda v: (v['owner'], v['legacyTag'])),
                enumSymbols=sorted(symbols, key=lambda v: (v['owner'], v['name'])),
                descriptorSHA256=hashlib.sha256(descriptor.read_bytes()).hexdigest(),
                protocVersion=subprocess.run([str(protoc), '--version'], capture_output=True, check=True).stdout.decode().strip(),
                warningCount=compiled.stderr.count(b'warning:'))


def gap_matrix(names, mapping, origins):
    tables = {kind: {row['semanticName']: row for row in mapping[kind]}
              for kind in ('opcodes', 'messages', 'schemas')}
    matrix = []
    for name in sorted(names):
        layers = {kind: tables[kind].get(name, dict(semanticName=name, status='unresolved', evidence=[],
                  ambiguities=['No CN72 evidence row; legacy type/name is not a target mapping']))
                  for kind in tables}
        fields = layers['schemas'].get('fields', [])
        gaps = []
        for kind in tables:
            if layers[kind]['status'] != 'verified':
                gaps.append(kind + ': identity/semantics/wire not fully verified')
        if not fields and not (layers['schemas']['status'] == 'verified'
                               and layers['schemas'].get('emptySchemaEvidence')):
            gaps.append('schemas: no field evidence; this does not prove an empty target message')
        if any(field['status'] != 'verified' for field in fields):
            gaps.append('schemas: unresolved or candidate business fields remain')
        gaps.append('runtime: no actual CN72 client sequence acceptance')
        matrix.append(dict(legacyName=name, sourceOrigins=sorted(origins.get(name, [])),
            sourceNameIsTargetEvidence=False, layers=layers,
            fieldStatusCounts=dict(sorted(Counter(field['status'] for field in fields).items())),
            gaps=gaps, targetProtocolReady=False))
    return matrix


def analyze(repo, java_home, protoc, protobuf_jar):
    graph = dependencies(repo, java_home, repo / 'protocol/cn-7.2/login-dependency-review.json')
    expected = json.loads((repo / 'protocol/cn-7.2/login-dependency-observations.json').read_text('utf8'))
    if summary(graph) != expected:
        raise ValueError('A-03b source report changed; review dependencies first')
    with tempfile.TemporaryDirectory(prefix='login-proto-', dir=repo / 'local/migration72/analysis') as directory:
        work = Path(directory)
        compile_scanner(java_home, work, repo / 'tools/Client72JavaSurface.java')
        rows = scan_sources(repo, java_home, work, list(graph['sourceSHA256']))
        candidates = packet_candidates(repo, graph, rows)
        origins = defaultdict(set)
        for row in candidates:
            for proto in row['directProtoReferences']:
                origins[proto.removesuffix('.proto')].add(row['javaType'])
            if row['dynamicOpcode']:
                for site in row['sourceSites']:
                    for expression in site['constructorArguments']:
                        for name in re.findall(r'\bPacketOpcodes\.(\w+)', expression):
                            origins[name].add(site['method'] + ': legacy symbolic opcode only')
        root_files = sorted({row['file'] for row in graph['methods'] if row['id'] in graph['roots']})
        for name in root_files:
            for value in re.findall(r'\b(\w+)OuterClass\b', clean_source((repo / name).read_text('utf8'), True)):
                origins[value].add(name)
        mapping = json.loads((repo / 'protocol/cn-7.2/mappings.json').read_text('utf8'))
        for name in mapping['requiredMessages']:
            origins[name].add('existing requiredMessages seed; completeness unresolved')
        # A verified target field can introduce an embedded semantic alias
        # without a direct Java import of that alias (the runtime keeps its raw
        # type name). Keep that independently proved dependency in the matrix.
        target_schemas={row['semanticName']:row for row in mapping['schemas']}
        pending=list(origins)
        while pending:
            name=pending.pop()
            for field in target_schemas.get(name,{}).get('fields',[]):
                if field['status']!='verified':continue
                child=field.get('nestedType') if field['type']=='message' else field.get('valueType') if field['type']=='map' else None
                if child not in target_schemas:continue
                if child not in origins:pending.append(child)
                origins[child].add('verified target nested dependency: '+name+'/'+field['semanticField']+'; no runtime necessity claim')
        proto_roots = {name + '.proto' for name in origins}
        missing_roots = sorted(name for name in proto_roots if not (repo / 'src/main/proto' / name).is_file())
        descriptors = read_descriptors(repo, proto_roots - set(missing_roots), protoc, java_home, protobuf_jar, work)
    source_hashes = dict(graph['sourceSHA256'])
    for row in candidates:
        source_hashes.update(row['javaSourceSHA256'])
    source_hashes.update({name: digest(repo / name) for name in root_files})
    source_hashes.update({'src/main/proto/' + name: digest(repo / 'src/main/proto' / name) for name in descriptors['files']})
    evidence_files = ['profile.json', 'mappings.json', 'commands.json'] + sorted(
        p.name for p in (repo / 'protocol/cn-7.2').glob('*observations.json'))
    evidence_hashes = {'protocol/cn-7.2/' + name: digest(repo / 'protocol/cn-7.2' / name) for name in evidence_files}
    matrix = gap_matrix(origins, mapping, origins)
    commands = json.loads((repo / 'protocol/cn-7.2/commands.json').read_text('utf8'))
    for row in matrix:
        opcode = row['layers']['opcodes']
        matches = [entry for entry in commands['commands'] if entry['clientType'] == opcode.get('clientType')]
        row['targetCommandBindings'] = matches
        if opcode.get('cmdId') is not None and not any(entry['cmdId'] == opcode['cmdId'] for entry in matches):
            raise ValueError('Target CmdID/type evidence disagrees: ' + row['legacyName'])
    return dict(schemaVersion=1, scope='A-03c legacy source/protoc dependencies and CN72 evidence gaps only',
        closureComplete=False, targetProtocolReady=False, runtimeStatus='not-tested',
        sourceSHA256=dict(sorted(source_hashes.items())), evidenceSHA256=evidence_hashes,
        clientInputSHA256=json.loads((repo / 'protocol/cn-7.2/profile.json').read_text('utf8'))['files'],
        packetCandidates=candidates, protoRootFiles=sorted(proto_roots), missingProtoRoots=missing_roots,
        protoDependencies=descriptors,
        messageMatrix=matrix, evidenceCatalog=mapping['evidence'],
        commandTableScope={name: commands[name] for name in (
            'bindingStatus', 'businessNamesStatus', 'runtimeStatus', 'transportDomainsStatus', 'duplicateValues')},
        counts=dict(packetCandidates=len(candidates), dynamicPacketCandidates=sum(v['dynamicOpcode'] for v in candidates),
                    protoRootFiles=len(proto_roots), protoFiles=len(descriptors['files']),
                    descriptorTypes=len(descriptors['types']), descriptorFields=len(descriptors['fields']),
                    messageMatrix=len(matrix)),
        openBoundaries=graph['openBoundaries'],
        limitations=['Java Proto references are lexical and include imports/parents not proven to execute',
                    'protoc descriptor closure includes unused imports; it is not a minimal client-required set',
                    'Legacy tags/types/enum values never supply CN72 field evidence',
                    'Mapped rows and field evidence are copied unchanged; new legacy names remain unresolved',
                    'Nonzero/64-bit/nested/list/error/boundary wire regressions remain separate required evidence',
                    'No protocol generation, runtime integration, client reverse engineering or client debugging'])


def recover(repo, java_home, protoc, protobuf_jar, review_path):
    review = json.loads(review_path.read_text('utf8'))
    if review['closureComplete'] is not False or review['targetProtocolReady'] is not False:
        raise ValueError('Dependency matrix cannot promote closure or protocol readiness')
    for group in ('sourceSHA256', 'evidenceSHA256'):
        for name, expected in review[group].items():
            if digest(repo / name) != expected:
                raise ValueError('Dependency or evidence changed; review again: ' + name)
    report = analyze(repo, java_home, protoc, protobuf_jar)
    if any(report[group] != review[group] for group in ('sourceSHA256', 'evidenceSHA256', 'missingProtoRoots')):
        raise ValueError('Dependency input set changed; review again')
    report['reviewSHA256'] = hashlib.sha256(review_path.read_bytes()).hexdigest()
    report['toolSourceSHA256'] = {name: digest(repo / name) for name in (
        'tools/review_client72_login_proto.py', 'tools/Client72ProtoDependencies.java')}
    report['toolBinarySHA256'] = {name: hashlib.sha256(path.read_bytes()).hexdigest()
                                 for name, path in [('protoc', protoc), ('protobufJava', protobuf_jar)]}
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--protoc', type=Path, required=True)
    parser.add_argument('--protobuf-java', type=Path, required=True)
    parser.add_argument('--review', type=Path, default=Path('protocol/cn-7.2/login-proto-review.json'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = recover(args.repo.resolve(), args.java_home.resolve(), args.protoc.resolve(),
                     args.protobuf_java.resolve(), args.repo.resolve() / args.review)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', 'utf8')
    print(json.dumps(report['counts']))


if __name__ == '__main__':
    main()
