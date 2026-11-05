"""Validate the three 7.2 mapping layers; --ready fails until evidence is complete.

This checks consistency and evidence references, not the truth of a researcher's
semantic interpretation. It never promotes candidates or generates old defaults.
"""
import argparse
from collections import defaultdict
import json
from pathlib import Path

STATES = {'unresolved', 'candidate', 'verified'}
SCALARS = {'double', 'float', 'int32', 'int64', 'uint32', 'uint64', 'sint32', 'sint64',
           'fixed32', 'fixed64', 'sfixed32', 'sfixed64', 'bool', 'string', 'bytes'}
MAP_KEYS = SCALARS - {'double', 'float', 'bytes'}


def validate(document, profile, *, ready=False):
    errors = []
    def require(condition, message):
        if not condition:
            errors.append(message)

    require(document.get('schemaVersion') == 1, 'Unsupported mapping schema version')
    require(profile.get('target', {}).get('version', '').startswith('7.2.') and
            profile.get('target', {}).get('channel') == 1, 'Profile must identify CN 7.2')
    require(document.get('target') == profile.get('target'), 'Target version/channel mismatch')
    evidence = {}
    for item in document.get('evidence', []):
        key = item.get('id')
        require(bool(key) and key not in evidence, f'Duplicate or missing evidence id: {key}')
        evidence[key] = item
        require(item.get('layer') in {'opcode', 'message', 'field', 'closure'}, f'{key}: invalid evidence layer')
        require(bool(item.get('location')), f'{key}: evidence location missing')
        file = profile.get('files', {}).get(item.get('inputFile'), {})
        require(bool(file.get('sha256')) and file.get('sha256') == item.get('sha256'), f'{key}: evidence input hash mismatch')

    def check_state(row, layer, label):
        state = row.get('status')
        require(state in STATES, f'{label}: invalid status')
        refs = row.get('evidence', [])
        if state != 'unresolved':
            require(bool(refs), f'{label}: evidence required')
        for ref in refs:
            require(ref in evidence and evidence[ref].get('layer') == layer, f'{label}: wrong or missing {layer} evidence {ref}')
        if state == 'verified':
            require(not row.get('ambiguities'), f'{label}: verified with unresolved ambiguity')
        if ready:
            require(state == 'verified', f'{label}: not verified')

    indexes = {}
    for table, layer in [('opcodes', 'opcode'), ('messages', 'message'), ('schemas', 'field')]:
        rows = {}
        require(isinstance(document.get(table), list), f'{table}: table required')
        for row in document.get(table, []):
            name = row.get('semanticName')
            require(bool(name) and name not in rows, f'{table}: duplicate/missing name {name}')
            rows[name] = row
            check_state(row, layer, f'{table}/{name}')
        indexes[table] = rows
    names = set(indexes['messages'])
    for table in ('opcodes', 'schemas'):
        require(set(indexes[table]) == names, f'{table}: message set mismatch')
    required = document.get('requiredMessages', [])
    require(bool(required) and len(set(required)) == len(required), 'Required message set empty or duplicated')
    require(set(required) <= names, 'Required messages missing')
    if ready:
        closure = document.get('closure', {})
        require(closure.get('status') == 'verified', 'P0 dependency closure not verified')
        check_state(closure, 'closure', 'P0 closure')

    used_opcodes, aliases = defaultdict(list), set()
    for name, message in indexes['messages'].items():
        require(message.get('direction') in {'C2S', 'S2C', 'both', 'embedded', 'HTTP'}, f'{name}: invalid direction')
        if message.get('status') != 'unresolved':
            require(bool(message.get('clientType')), f'{name}: client type required')
        for alias in message.get('aliases', []):
            require(alias not in names and alias not in aliases, f'{name}: duplicate alias {alias}')
            aliases.add(alias)
        opcode = indexes['opcodes'].get(name, {})
        value = opcode.get('cmdId')
        if value is not None:
            require(type(value) is int and 1 <= value <= 65535, f'{name}: opcode out of uint16 range')
            if type(value) is int:
                used_opcodes[value].append(name)
        if opcode.get('status') != 'unresolved' and message.get('direction') not in {'embedded', 'HTTP'}:
            require(value is not None, f'{name}: network opcode required')
        if opcode.get('status') == 'verified':
            require(message.get('status') == 'verified' and opcode.get('clientType') == message.get('clientType'),
                    f'{name}: opcode type and message evidence disagree')
        schema = indexes['schemas'].get(name, {})
        tags, fields = set(), set()
        for field in schema.get('fields', []):
            label = f"{name}/{field.get('semanticField')}"
            check_state(field, 'field', label)
            field_name, tag = field.get('semanticField'), field.get('tag')
            require(bool(field_name) and field_name not in fields, f'{label}: duplicate/missing field')
            fields.add(field_name)
            if tag is not None:
                require(type(tag) is int and 1 <= tag < (1 << 29) and not 19000 <= tag <= 19999,
                        f'{label}: invalid/reserved tag')
                require(tag not in tags, f'{label}: duplicate tag')
                tags.add(tag)
            if field.get('status') == 'verified':
                require(tag is not None, f'{label}: tag required')
                kind = field.get('type')
                cardinality = field.get('cardinality')
                require(kind in SCALARS | {'message', 'enum', 'map'}, f'{label}: invalid type')
                require(cardinality in {'singular', 'optional', 'repeated', 'map'}, f'{label}: invalid cardinality')
                require(field.get('presence') in {'implicit', 'explicit', 'oneof'}, f'{label}: presence missing/invalid')
                require(type(field.get('packed')) is bool, f'{label}: packed must be explicit true/false')
                if kind == 'message':
                    require(field.get('nestedType') in names, f'{label}: missing nested schema')
                if kind == 'enum':
                    enum = field.get('enumValues')
                    require(isinstance(enum, dict) and bool(enum) and
                            all(isinstance(k, str) and k and type(v) is int and -(1 << 31) <= v < (1 << 31)
                                for k, v in enum.items()), f'{label}: enum values missing/invalid')
                if kind == 'map':
                    require(cardinality == 'map' and field.get('keyType') in MAP_KEYS and field.get('valueType') in SCALARS | names,
                            f'{label}: map key/value missing or invalid')
                if field.get('packed'):
                    require(cardinality == 'repeated' and kind not in {'string', 'bytes', 'message', 'map'},
                            f'{label}: invalid packed field')
                if field.get('oneof'):
                    require(cardinality == 'singular', f'{label}: repeated/optional/map oneof invalid')
        if schema.get('status') == 'verified':
            require(all(f.get('status') == 'verified' for f in schema.get('fields', [])), f'{name}: schema includes unknown fields')
    for value, members in used_opcodes.items():
        require(len(members) == 1, f'Opcode {value} has multiple canonical messages: {members}; use explicit aliases')
    return errors


def validate_observations(document, profile, commands, wire, protected=None, observations=()):
    """Cross-check candidate claims too; static wire does not prove semantics."""
    errors = []
    expected = {row['path']: row['sha256'] for row in profile['files'].values()}
    tables = [('commands', commands), ('wire', wire)] + ([('protected', protected)] if protected else []) + list(observations)
    for label, table in tables:
        hashes = table.get('inputSHA256', {})
        if not hashes or any(expected.get(path) != sha for path, sha in hashes.items()):
            errors.append(f'{label}: observation input hash mismatch')
    ids = {row['clientType']: row['cmdId'] for row in commands.get('commands', [])}
    schemas = {row['clientType']: {f['clientField']: f for f in row['fields']}
               for row in wire.get('messages', []) + ([protected] if protected else []) +
               [row for _, table in observations for row in table.get('messages', [])]}
    contents = {}
    for _, table in observations:
        for binding in table.get('contentBindings', []):
            identity = (binding.get('clientType'), binding.get('clientField'))
            raw = schemas.get(identity[0], {}).get(identity[1])
            if (identity in contents or raw is None or raw.get('tag') != binding.get('tag') or
                    binding.get('contentStatus') != 'native-graph-observed' or not binding.get('cases') or
                    not set(binding['cases']).issubset(table.get('fixtureNames', []))):
                errors.append(f'{identity}: graph content binding mismatch')
            contents[identity] = binding
    messages = {row['semanticName']: row for row in document['messages']}
    for row in document['opcodes']:
        if row.get('cmdId') is not None and row.get('clientType'):
            if ids.get(row['clientType']) != row['cmdId']:
                errors.append(f"{row['semanticName']}: static command binding mismatch")
    for schema in document['schemas']:
        name = schema['semanticName']
        client_type = messages[name].get('clientType')
        if client_type not in schemas:
            continue
        for field in schema['fields']:
            raw = schemas[client_type].get(field.get('clientField'))
            keys = ('tag', 'type', 'cardinality', 'presence', 'packed', 'keyType', 'valueType')
            if raw is None or any(field.get(k) != raw.get(k) for k in keys):
                errors.append(f"{name}/{field.get('semanticField')}: static wire binding mismatch")
            elif field.get('clientNestedType') != raw.get('nestedType'):
                errors.append(f"{name}/{field.get('semanticField')}: nested client type mismatch")
            elif field.get('status') == 'verified' and field.get('semanticField', '').startswith('unknown_field_'):
                errors.append(f"{name}/{field.get('semanticField')}: business field meaning unresolved")
            elif field.get('status') == 'verified' and raw.get('contentStatus') == 'not-traced' and (client_type,field.get('clientField')) not in contents:
                errors.append(f"{name}/{field.get('semanticField')}: nested/container content not traced")
            if field.get('contentStatus') == 'native-graph-observed' and (client_type,field.get('clientField')) not in contents:
                errors.append(f"{name}/{field.get('semanticField')}: graph content evidence missing")
    return errors


def validate_login_semantics(document, profile, semantics, literals, enums, wire):
    """Bind semantic claims to bounded target observations, not name resemblance."""
    errors=[]
    expected={r['path']:r['sha256'] for r in profile['files'].values()}
    for label,table in [('login semantics',semantics),('login literals',literals),('enums',enums)]:
        hashes=table.get('inputSHA256',{})
        if not hashes or any(expected.get(p)!=s for p,s in hashes.items()):errors.append(f'{label}: input hash mismatch')
    labels={r['index']:r for r in literals.get('literals',[])}
    roles={r['semanticName']:r for r in semantics.get('roles',[])}
    messages={r['semanticName']:r for r in document['messages']}
    opcodes={r['semanticName']:r for r in document['opcodes']}
    for name,message in messages.items():
        if 'login-handler-identity' in message.get('evidence',[]) and name not in roles:
            errors.append(f'{name}: Handler identity observation missing')
    for name,role in roles.items():
        marker=labels.get(role.get('literalIndex'),{}).get('value','')
        if marker!=f'[Login] On{name}: ' or not role.get('declarationCheck'):
            errors.append(f'{name}: missing target Handler identity evidence')
        if messages.get(name,{}).get('clientType')!=role['clientType'] or opcodes.get(name,{}).get('cmdId')!=role['cmdId']:
            errors.append(f'{name}: Handler/command/type disagreement')
    bindings={}
    for row in semantics.get('fieldBindings',[]):
        key=(row.get('clientType'),row.get('clientField'))
        if key in bindings or not row.get('source') or row.get('scope')!='bounded-native-data-flow':
            errors.append(f'{key}: invalid semantic observation')
        bindings[key]=row
    for schema in document['schemas']:
        msg=messages.get(schema['semanticName'],{})
        for field in schema['fields']:
            if 'login-source-semantics' not in field.get('evidence',[]):continue
            row=bindings.get((msg.get('clientType'),field.get('clientField')), {})
            if any(field.get(k)!=row.get(k) for k in ('semanticField','tag','type','status')):
                errors.append(f"{schema['semanticName']}/{field.get('semanticField')}: semantic source binding mismatch")
    enum_map={r['clientType']:r for r in enums.get('enums',[])}
    if len(enum_map)!=len(enums.get('enums',[])):errors.append('Duplicate enum type')
    for binding in bindings.values():
        source_enum=binding.get('source',{}).get('enumClientType')
        if source_enum and source_enum not in enum_map:errors.append('Semantic source enum missing')
    wire_map={r['clientType']:r for r in wire.get('messages',[])}
    for binding in enums.get('fieldBindings',[]):
        enum=enum_map.get(binding.get('enumClientType'),{})
        values=enum.get('values',[])
        if not values or len({v['symbol'] for v in values})!=len(values):
            errors.append('Enum symbol set missing/duplicated');continue
        fields=wire_map.get(binding.get('clientType'),{}).get('fields',[])
        if binding.get('role')=='oneof-discriminator':
            tags={f['discriminatorValue'] for f in fields if f.get('discriminatorOffset')==binding['objectOffset']}
            if {v['value'] for v in values}!={0}|tags:errors.append('Enum oneof values disagree with native wire cases')
        else:
            found=[f for f in fields if f['clientField']==binding.get('clientField')]
            if len(found)!=1 or found[0].get('enumClientType')!=binding.get('enumClientType') or found[0]['objectOffset']!=binding['objectOffset']:
                errors.append('Enum field declaration/wire binding mismatch')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path(__file__).resolve().parents[1] / 'protocol/cn-7.2')
    parser.add_argument('--ready', action='store_true')
    args = parser.parse_args()
    document = json.loads((args.directory / 'mappings.json').read_text('utf-8'))
    profile = json.loads((args.directory / 'profile.json').read_text('utf-8'))
    errors = validate(document, profile, ready=args.ready)
    commands = json.loads((args.directory / 'commands.json').read_text('utf-8'))
    wire = json.loads((args.directory / 'wire-observations.json').read_text('utf-8'))
    protected = json.loads((args.directory / 'token-wire-observations.json').read_text('utf-8'))
    extra = [(name, json.loads((args.directory / name).read_text('utf8')))
             for name in ('login-wire-observations.json', 'response-wire-observations.json', 'response-leaf-wire-observations.json',
                          'response-graph-observations.json', 'map-aot-observations.json')]
    errors.extend(validate_observations(document, profile, commands, wire, protected, extra))
    semantic_tables=[json.loads((args.directory/name).read_text('utf8')) for name in
                     ('login-semantic-observations.json','login-literal-observations.json','enum-observations.json','login-wire-observations.json')]
    errors.extend(validate_login_semantics(document,profile,*semantic_tables))
    print(json.dumps({'mode': 'ready' if args.ready else 'research-structure', 'valid': not errors, 'errors': errors},
                     ensure_ascii=False, indent=2))
    raise SystemExit(bool(errors))


if __name__ == '__main__':
    main()
