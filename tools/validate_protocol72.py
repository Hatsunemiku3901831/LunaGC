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


def validate_scene_fusion(document, profile, handshake, capacity, initialization):
    """Check the reviewed native handshake/capacity slice across all three layers."""
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    for label,table in [('handshake',handshake),('capacity',capacity),('initialization',initialization)]:
        if table.get('inputSHA256')!=hashes:errors.append(f'Scene fusion {label}: fixed input mismatch')
        if table.get('runtimeStatus')!='not-tested' or table.get('targetProtocolReady') is not False:
            errors.append(f'Scene fusion {label}: offline evidence cannot claim live acceptance')
    tables={k:{r['semanticName']:r for r in document[k]} for k in ('opcodes','messages','schemas')}
    roles=handshake.get('roles',[])
    expected={('EnterSceneReadyReq',74391,5046),('EnterSceneReadyRsp',17278,9948),
              ('SceneInitFinishReq',20380,4905),('SceneInitFinishRsp',36349,21392),
              ('EnterSceneDoneReq',63999,1017),('EnterSceneDoneRsp',74698,298),
              ('PostEnterSceneReq',74076,1696),('PostEnterSceneRsp',51326,26760)}
    if {(r.get('semanticName'),r.get('typeIndex'),r.get('cmdId')) for r in roles}!=expected:
        errors.append('Scene fusion: reviewed role/command set changed')
    raw={r['typeIndex']:r for r in handshake.get('messages',[])}
    bindings={(b['typeIndex'],b['tag']):b for b in handshake.get('semanticBindings',[])}
    for role in roles:
        name=role['semanticName'];index=role['typeIndex']
        for table,ref in [('opcodes','scene-handshake-command'),('messages','scene-handshake-identity')]:
            actual=tables[table].get(name,{})
            if (actual.get('status')!='verified' or actual.get('clientType')!=role['clientType']
                    or ref not in actual.get('evidence',[])
                    or (table=='opcodes' and actual.get('cmdId')!=role['cmdId'])):
                errors.append(f'Scene fusion {name}: {table} native identity mismatch')
        fields=tables['schemas'].get(name,{}).get('fields',[])
        if {f['tag'] for f in fields}!={f['tag'] for f in raw.get(index,{}).get('fields',[])}:
            errors.append(f'Scene fusion {name}: complete raw field set mismatch')
        for f in fields:
            original=next((v for v in raw.get(index,{}).get('fields',[]) if v['tag']==f['tag']),{})
            if any(f.get(k)!=original.get(k) for k in ('tag','clientField','objectOffset','type','cardinality')):
                errors.append(f'Scene fusion {name}/{f["tag"]}: native wire binding mismatch')
            child=original.get('nestedTypeIndex')
            if child is not None and f.get('clientNestedType')!=raw.get(child,{}).get('clientType'):
                errors.append(f'Scene fusion {name}/{f["tag"]}: nested type mismatch')
            binding=bindings.get((index,f['tag']))
            if binding:
                encoding={k:binding[k] for k in ('applicationToWire','wireToApplication') if k in binding}
                if (f.get('semanticField')!=binding['name'] or f.get('status')!='verified'
                        or f.get('applicationEncoding')!=encoding or 'scene-handshake-fields' not in f.get('evidence',[])):
                    errors.append(f'Scene fusion {name}/{f["tag"]}: token/retcode semantic or encoding mismatch')
            elif f.get('status')!='unresolved' or not f.get('semanticField','').startswith('unknown_field_'):
                errors.append(f'Scene fusion {name}/{f["tag"]}: unknown field promoted')
    name='StoreWeightLimitNotify'
    for table,ref in [('opcodes','store-capacity-command'),('messages','store-capacity-identity')]:
        actual=tables[table].get(name,{})
        if (actual.get('status')!='verified' or actual.get('clientType')!='KNCCLAJAEEC'
                or ref not in actual.get('evidence',[]) or (table=='opcodes' and actual.get('cmdId')!=22609)):
            errors.append(f'Scene fusion capacity: {table} purpose alias mismatch')
    fields=tables['schemas'].get(name,{}).get('fields',[])
    known={b['tag']:b for b in capacity.get('bindings',[])}
    if set(known)!={1,5,6,9,14} or {f['tag'] for f in fields}!={f['tag'] for f in initialization.get('fields',[])}:
        errors.append('Scene fusion capacity: complete raw/semantic field set mismatch')
    for f in fields:
        original=next((v for v in initialization.get('fields',[]) if v['tag']==f['tag']),{})
        if any(f.get(k)!=original.get(k) for k in ('tag','clientField','objectOffset','type','cardinality','packed')):
            errors.append(f'Scene fusion capacity/{f["tag"]}: native wire mismatch')
        b=known.get(f['tag'])
        if b:
            if (f.get('status')!='verified' or f.get('semanticField')!=b['semanticField']
                    or f.get('applicationEncoding')!={'valueEncoding':b['valueEncoding']}):
                errors.append(f'Scene fusion capacity/{f["tag"]}: native capacity purpose mismatch')
        elif f.get('status')!='unresolved' or not f.get('semanticField','').startswith('unknown_field_'):
            errors.append(f'Scene fusion capacity/{f["tag"]}: unknown field promoted')
        if f['tag']==6 and f.get('enumValues')!={'StoreNone':0,'StorePack':1,'StoreDepot':2}:
            errors.append('Scene fusion capacity: target store enum mismatch')
    token=next((f for f in tables['schemas'].get('PlayerEnterSceneNotify',{}).get('fields',[]) if f['tag']==10),{})
    b=bindings.get((46311,10),{})
    if (token.get('semanticField')!='enter_scene_token' or token.get('status')!='verified'
            or token.get('applicationEncoding')!={'wireToApplication':b.get('wireToApplication')}):
        errors.append('Scene fusion Notify: native token chain mismatch')
    return errors


def validate_enter_scene_fusion(document, profile, contract, domain):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    for label,table in [('enter contract',contract),('1017 domain',domain)]:
        if (table.get('inputSHA256')!=hashes or table.get('runtimeStatus')!='not-tested'
                or table.get('targetProtocolReady') is not False):
            errors.append(f'Scene fusion {label}: fixed offline scope mismatch')
    schema=next(r for r in document['schemas'] if r['semanticName']=='PlayerEnterSceneNotify')
    bindings={b['tag']:b for b in contract.get('semanticBindings',[]) if b['typeIndex']==46311}
    if set(bindings)!={7,8,10,14}:
        errors.append('Scene fusion Notify: reviewed consumer binding set changed')
    for tag,b in bindings.items():
        f=next((f for f in schema['fields'] if f['tag']==tag),{})
        if (f.get('status')!='verified' or f.get('semanticField')!=b['name']
                or f.get('objectOffset')!=b['objectOffset']
                or f.get('applicationEncoding')!={'wireToApplication':b['wireToApplication']}):
            errors.append(f'Scene fusion Notify/{tag}: named consumer mismatch')
        if tag==8:
            values={v['symbol']:v['value'] for v in contract['identity']['enum']['values']}
            if f.get('enumValues')!=values:errors.append('Scene fusion Notify: target Enter enum mismatch')
        if tag==14 and (f.get('clientNestedType')!='BGDCNKAKGHI' or f.get('nestedType')!='Vector'):
            errors.append('Scene fusion Notify: independently named position/vector link missing')
    if any(f['status']!='unresolved' for f in schema['fields'] if f['tag'] not in bindings):
        errors.append('Scene fusion Notify: unproved sender field promoted')
    vector=next((r for r in document['schemas'] if r['semanticName']=='Vector'),{})
    axes={b['tag']:b for b in contract.get('semanticBindings',[]) if b['typeIndex']==84343}
    if vector.get('status')!='verified' or len(vector.get('fields',[]))!=3:
        errors.append('Scene fusion Vector: embedded three-axis schema missing')
    for f in vector.get('fields',[]):
        b=axes.get(f['tag'],{})
        if (f.get('status')!='verified' or f.get('semanticField')!=b.get('name') or f.get('type')!='float'
                or f.get('objectOffset')!=b.get('objectOffset')):
            errors.append('Scene fusion Vector: named native axis mismatch')
    expected={(63999,'game-session','C2S'),(22065,'beyond-wpf-stream','process-outbound-to-external-stream')}
    if ({(r['typeIndex'],r['transportDomain'],r['direction']) for r in domain.get('domains',[])}!=expected
            or domain.get('globalStaticDuplicateRetained') is not True
            or domain.get('gameC2SAmbiguity')!='resolved-for-observed-normal-native-transport-paths'):
        errors.append('Scene fusion 1017: normal transport distinction or global duplicate lost')
    opcode=next(r for r in document['opcodes'] if r['semanticName']=='EnterSceneDoneReq')
    if opcode.get('domainEvidence')!='protocol/cn-7.2/cmd1017-domain-observations.json':
        errors.append('Scene fusion 1017: mapped domain evidence missing')
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


def validate_session_token_flow(document, profile, flow, wire_tables):
    errors=[]
    expected={profile['files'][key]['path']:profile['files'][key]['sha256'] for key in ('binary','metadata','startup')}
    if flow.get('inputSHA256')!=expected:errors.append('Session token flow: input hashes incomplete/mismatched')
    reviewed=dict(responseType='CLDBKDNJHFG',responseCmdId=27808,responseOffset=0x68,responseTag=6,
                  store='0x15a4e0d93',sessionType='HOMIEDIPBBJ',sessionOffset=0x98,
                  caller='0x1507c2211',call='0x1507c2218',constructor='0x151213730',
                  requestType='KOHBOMDDFCB',requestCmdId=23172,requestOffset=0xd8,requestTag=1)
    if flow.get('chain')!=reviewed:errors.append('Session token flow: reviewed store/caller/wire chain mismatch')
    messages={m['semanticName']:m for m in document['messages']}
    opcodes={m['semanticName']:m for m in document['opcodes']}
    wire={m['clientType']:m for t in wire_tables for m in t['messages']}
    bindings=flow.get('fieldBindings',[])
    expected_bindings={('CLDBKDNJHFG',0x68,6),('KOHBOMDDFCB',0xd8,1)}
    if len(bindings)!=2 or {(b.get('clientType'),b.get('objectOffset'),b.get('tag')) for b in bindings}!=expected_bindings:
        errors.append('Session token flow: source/destination binding set mismatch')
    for b in bindings:
        fields=[f for m in document['schemas'] if messages.get(m['semanticName'],{}).get('clientType')==b.get('clientType')
                for f in m['fields'] if f.get('clientField')==b.get('clientField')]
        actual=[f for f in wire.get(b.get('clientType'),{}).get('fields',[]) if f.get('objectOffset')==b.get('objectOffset')]
        if (b.get('scope')!='bounded-session-credential-transfer' or b.get('semanticField')!='token'
                or b.get('status')!='verified' or b.get('type')!='string' or len(fields)!=1 or len(actual)!=1):
            errors.append('Session token flow: invalid credential binding');continue
        if any(actual[0].get(k)!=b.get(k) for k in ('tag','type','clientField')):
            errors.append('Session token flow: wire observation disagreement')
        if ('session-token-flow' not in fields[0].get('evidence',[]) or
                any(fields[0].get(k)!=b.get(k) for k in ('tag','type','semanticField','status'))):
            errors.append('Session token flow: mapping disagreement')
    for name,client,cmd in (('GetPlayerTokenRsp','CLDBKDNJHFG',27808),('PlayerLoginReq','KOHBOMDDFCB',23172)):
        if messages.get(name,{}).get('clientType')!=client or opcodes.get(name,{}).get('cmdId')!=cmd:
            errors.append('Session token flow: command/type disagreement')
    return errors


def validate_request_identities(document, profile, identity, enums, responses):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if identity.get('inputSHA256')!=hashes:errors.append('Request identity: target input mismatch')
    declaration=identity.get('stageDeclaration',{})
    if any(declaration.get(k)!=v for k,v in dict(entry='0x14c8ae9a0',methodIndex=711360,
           parameterTypeIndex=675783,enumTypeIndex=85949,enumClientType='NEKEJMEIJBF').items()):
        errors.append('Request identity: stage method/parameter declaration mismatch')
    expected_enum=next((e for e in enums.get('enums',[]) if e.get('typeIndex')==85949),None)
    if not expected_enum or identity.get('stageEnum')!=expected_enum:
        errors.append('Request identity: stage constants disagree with enum observations')
    symbols={v['symbol']:v['value'] for v in (expected_enum or {}).get('values',[])}
    expected={'GetPlayerTokenReq':('NJAALDCMHEP',24604,'OnPlayerTokenReq',8,'GetPlayerTokenRsp','OnPlayerTokenRsp',9),
              'PlayerLoginReq':('KOHBOMDDFCB',23172,'OnLoginReq',6,'PlayerLoginRsp','OnLoginRsp',7)}
    native={'GetPlayerTokenReq':dict(typeIndex=82491,clone='0x1500c2190',selfClassCell='0x1458c5500',
                                    copyFrom='0x1500c2240',getter='0x1500c4a50',constructorPrefix='0x15121fff5'),
            'PlayerLoginReq':dict(typeIndex=76410,clone='0x152ef0480',selfClassCell='0x1458c3df0',
                                 copyFrom='0x152ef0530',getter='0x152ef20f0',constructorPrefix='0x151213730')}
    roles=identity.get('roles',[])
    if len(roles)!=2 or {r.get('semanticName') for r in roles}!=set(expected):
        errors.append('Request identity: missing/duplicate/extra role')
    messages={m['semanticName']:m for m in document['messages']}
    opcodes={o['semanticName']:o for o in document['opcodes']}
    response_map={r['semanticName']:r for r in responses.get('roles',[])}
    for role in roles:
        name=role.get('semanticName')
        if name not in expected:continue
        client,cmd,symbol,value,rsp,rs,rv=expected[name]
        required=dict(clientType=client,cmdId=cmd,stageSymbol=symbol,stageValue=value,responseName=rsp,
                      responseStageSymbol=rs,responseStageValue=rv,status='verified',scope='native-stage-and-type-identity')
        required.update(native[name])
        if (any(role.get(k)!=v for k,v in required.items()) or symbols.get(symbol)!=value or symbols.get(rs)!=rv
                or not all(role.get(k) for k in ('clone','selfClassCell','copyFrom','getter'))):
            errors.append(f'{name}: stage/type/native binding mismatch')
        response=response_map.get(rsp,{})
        if any(role.get(k)!=response.get(v) for k,v in [('responseClientType','clientType'),('responseCmdId','cmdId'),('responseHandler','handler')]):
            errors.append(f'{name}: paired response identity mismatch')
        message,opcode=messages.get(name,{}),opcodes.get(name,{})
        if (message.get('clientType')!=client or message.get('status')!='verified' or
                'request-stage-identity' not in message.get('evidence',[]) or opcode.get('cmdId')!=cmd or
                opcode.get('status')!='verified' or 'request-stage-command' not in opcode.get('evidence',[])):
            errors.append(f'{name}: mapping does not match stage evidence')
    return errors


def validate_login_constructor(document, profile, constructor, wire):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if constructor.get('inputSHA256')!=hashes:errors.append('Login constructor: input mismatch')
    if constructor.get('entry')!='0x151213730' or constructor.get('stop')!='0x151214d20':
        errors.append('Login constructor: execution boundaries mismatch')
    expected={0x18:(1533,'string','uapc','info_get_uapc'),0x114:(12,'uint32','channel_id','info_get_channel_id'),
              0x158:(364,'uint32','sub_channel_id','info_get_sub_channel_id')}
    bindings=constructor.get('fieldBindings',[])
    if len(bindings)!=3 or {b.get('objectOffset') for b in bindings}!=set(expected):
        errors.append('Login constructor: SDK source binding set mismatch')
    raw=next((m for m in wire.get('messages',[]) if m.get('typeIndex')==76410),{})
    schema=next((s for s in document['schemas'] if s['semanticName']=='PlayerLoginReq'),{})
    for b in bindings:
        off=b.get('objectOffset')
        if off not in expected:continue
        tag,kind,name,key=expected[off]
        required=dict(tag=tag,type=kind,semanticField=name,sourceKey=key,clientType='KOHBOMDDFCB',typeIndex=76410,
                      status='verified',scope='native-sdk-key-to-wire')
        if any(b.get(k)!=v for k,v in required.items()):errors.append('Login constructor: SDK key/field claim mismatch')
        fields=[f for f in raw.get('fields',[]) if f.get('objectOffset')==off]
        if len(fields)!=1 or any(b.get(k)!=fields[0].get(k) for k in ('tag','type','clientField')):
            errors.append('Login constructor: raw wire disagreement')
        mapped=[f for f in schema.get('fields',[]) if f.get('clientField')==b.get('clientField')]
        if (len(mapped)!=1 or 'login-sdk-source' not in mapped[0].get('evidence',[]) or
                any(mapped[0].get(k)!=b.get(k) for k in ('tag','type','semanticField','status'))):
            errors.append('Login constructor: semantic mapping disagreement')
    claims={f.get('clientField') for f in schema.get('fields',[]) if 'login-sdk-source' in f.get('evidence',[])}
    if claims!={b.get('clientField') for b in bindings}:errors.append('Login constructor: undeclared SDK claim')
    return errors


def validate_token_constructor(document, profile, constructor, wire):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if constructor.get('inputSHA256')!=hashes:errors.append('Token constructor: input mismatch')
    if (constructor.get('entry')!='0x15121ff70' or constructor.get('stop')!='0x151214d20' or
            constructor.get('completion')!='constructor-with-opaque-seed-boundary' or
            constructor.get('runtimeStatus')!='not-tested'):
        errors.append('Token constructor: bounded completion claim mismatch')
    boundary=constructor.get('opaqueSeedBoundary',{})
    expected_boundary=dict(address='0x15121db40',nativeExecuted=False,objectOffset=0x90,
                           tag=623,type='string',semanticStatus='unresolved')
    if boundary!=expected_boundary:errors.append('Token constructor: opaque crypto boundary mismatch')
    bindings=constructor.get('fieldBindings',[])
    required=dict(typeIndex=82491,clientType='NJAALDCMHEP',objectOffset=0xb4,tag=10,type='uint32',
                  semanticField='sub_channel_id',sourceKey='info_get_sub_channel_id',status='verified',scope='native-sdk-key-to-wire')
    if len(bindings)!=1 or any(bindings[0].get(k)!=v for k,v in required.items()):
        errors.append('Token constructor: SDK source binding mismatch')
    schema=next((s for s in document['schemas'] if s['semanticName']=='GetPlayerTokenReq'),{})
    for b in bindings:
        raw=[f for f in wire.get('fields',[]) if f.get('objectOffset')==b.get('objectOffset')]
        if len(raw)!=1 or any(b.get(k)!=raw[0].get(k) for k in ('tag','type','clientField')):
            errors.append('Token constructor: SDK/raw wire disagreement')
        mapped=[f for f in schema.get('fields',[]) if f.get('clientField')==b.get('clientField')]
        if (len(mapped)!=1 or 'token-sdk-source' not in mapped[0].get('evidence',[]) or
                any(mapped[0].get(k)!=b.get(k) for k in ('tag','type','semanticField','status'))):
            errors.append('Token constructor: semantic mapping disagreement')
    claims={f.get('clientField') for f in schema.get('fields',[]) if 'token-sdk-source' in f.get('evidence',[])}
    if claims!={b.get('clientField') for b in bindings}:errors.append('Token constructor: undeclared SDK claim')
    session={(r.get('sessionOffset'),r.get('objectOffset'),r.get('tag'),r.get('type'))
             for r in constructor.get('rawSessionBindings',[])}
    expected={(0x78,0x18,9,'string'),(0xf8,0x40,2,'string'),(0xb0,0x70,965,'string'),
              (0x138,0xb0,3,'uint32'),(0x13c,0xa4,15,'uint32'),(0x15f,0xc4,11,'bool')}
    if session!=expected or len(constructor.get('rawSessionBindings',[]))!=6:
        errors.append('Token constructor: raw session source set mismatch')
    other={(r.get('objectOffset'),r.get('tag'),r.get('type'),r.get('source'))
           for r in constructor.get('rawOtherBindings',[])}
    if (other!={(0x50,1300,'string','old Shell operation 5'),(0x98,1184,'uint32','literal 4'),
                (0x78,83,'string','global +18190'),(0xbc,1206,'uint32','0x145878704'),(0xc0,561,'uint32','0x145878700')}
            or len(constructor.get('rawOtherBindings',[]))!=5):
        errors.append('Token constructor: raw other source set mismatch')
    raw_bindings=[boundary,*constructor.get('rawSessionBindings',[]),*constructor.get('rawOtherBindings',[])]
    for b in raw_bindings:
        matches=[f for f in wire.get('fields',[]) if f.get('objectOffset')==b.get('objectOffset')]
        if (b.get('semanticStatus')!='unresolved' or len(matches)!=1 or
                any(b.get(k)!=matches[0].get(k) for k in ('tag','type'))):
            errors.append('Token constructor: raw source/wire or unknown-semantics mismatch')
    return errors


def validate_token_complete(document, profile, complete, wire):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if complete.get('inputSHA256')!=hashes:errors.append('Token complete: input mismatch')
    expected=dict(entry='0x15121ff70',helper='0x15121db40',helperNativeExecuted=True,
                  constructorStop='0x151214d20',sendStop='0x14a447e00',frameBuilderExecuted=False,
                  runtimeStatus='not-tested',semanticName='GetPlayerTokenReq',typeIndex=82491,cmdId=24604)
    if any(complete.get(k)!=v for k,v in expected.items()):errors.append('Token complete: execution/identity boundary mismatch')
    binding=complete.get('seedBinding',{})
    expected_binding=dict(objectOffset=0x90,tag=623,type='string',clientField='JFCLDBKLKDF',
                          semanticField='client_rand_key',status='verified')
    if any(binding.get(k)!=v for k,v in expected_binding.items()):errors.append('Token complete: seed binding mismatch')
    raw=[f for f in wire.get('fields',[]) if f.get('objectOffset')==0x90]
    if len(raw)!=1 or any(raw[0].get(k)!=binding.get(k) for k in ('tag','type','clientField')):
        errors.append('Token complete: wire disagreement')
    schema=next((s for s in document['schemas'] if s['semanticName']=='GetPlayerTokenReq'),{})
    mapped=[f for f in schema.get('fields',[]) if 'token-native-seed' in f.get('evidence',[])]
    if len(mapped)!=1 or any(mapped[0].get(k)!=binding.get(k) for k in ('tag','type','clientField','semanticField','status')):
        errors.append('Token complete: semantic mapping disagreement')
    results=complete.get('results',[])
    actual=[(r.get('seedHex'),r.get('initializedFixture'),r.get('tamperedInput'),r.get('ioctlReturn'),
             r.get('seedWireLength'),r.get('cmdId'),r.get('constructorStillSendsOnEmptySeed'),r.get('passed')) for r in results]
    required=[('0000000000000000',True,None,0,344,24604,False,True),
              ('8000000000000000',True,None,0,344,24604,False,True),
              ('fffffffffffff800',True,None,0,344,24604,False,True),
              ('8000000000000000',False,None,-2,0,24604,True,True),
              ('8000000000000000',True,'signature',-7,0,24604,True,True)]
    if actual!=required:errors.append('Token complete: native success/rejection cases changed')
    return errors


def validate_response_crypto(document,profile,crypto,wire):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if crypto.get('inputSHA256')!=hashes:errors.append('Response crypto: input mismatch')
    expected=dict(entry='0x15a4de707',successStop='0x15a4e0d4b',rejectionStop='0x15a4e0645',
                  shortStop='0x159ca4ea0',runtimeStatus='not-tested',wholeHandlerExecuted=False,keyVerificationForced=False)
    if any(crypto.get(k)!=v for k,v in expected.items()):errors.append('Response crypto: scope mismatch')
    bindings=crypto.get('fieldBindings',[])
    actual={(b.get('objectOffset'),b.get('tag'),b.get('clientField'),b.get('semanticField')) for b in bindings}
    if len(bindings)!=2 or actual!={(0x98,477,'CGDONMBOALA','server_rand_key'),(0x88,208,'GKBJLBAKMCJ','sign')}:
        errors.append('Response crypto: field source set mismatch')
    schema=next((s for s in document['schemas'] if s['semanticName']=='GetPlayerTokenRsp'),{})
    message=next((m for m in wire['messages'] if m['typeIndex']==37481),{})
    for binding in bindings:
        raw=[f for f in message.get('fields',[]) if f.get('objectOffset')==binding.get('objectOffset')]
        mapped=[f for f in schema.get('fields',[]) if f.get('clientField')==binding.get('clientField')]
        if (binding.get('typeIndex')!=37481 or binding.get('type')!='string' or binding.get('status')!='verified' or
                len(raw)!=1 or any(raw[0].get(k)!=binding.get(k) for k in ('tag','type','clientField')) or
                len(mapped)!=1 or 'response-native-crypto' not in mapped[0].get('evidence',[]) or
                any(mapped[0].get(k)!=binding.get(k) for k in ('tag','type','semanticField','status'))):
            errors.append('Response crypto: wire/semantic binding disagreement')
    results=crypto.get('results',[])
    expected_cases=[('local-zero','local',True,True),('local-high64','local',True,True),('local-trailing','local',True,True),
                    ('stock-invalid-signature','stock',False,False),('local-invalid-signature','local',False,False),
                    ('local-invalid-key-signature','local',False,False),('local-invalid-cipher','local',False,False),
                    ('local-short-seed','local',True,False)]
    if [(r.get('name'),r.get('trust'),r.get('signatureAccepted'),r.get('javaByteComparison')) for r in results]!=expected_cases:
        errors.append('Response crypto: trust/success/rejection cases changed')
    if len(crypto.get('trustInputReplacements',[]))!=3:errors.append('Response crypto: omitted local trust fixture boundary')
    return errors


def validate_handler_core(document,profile,handler,wire):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if handler.get('inputSHA256')!=hashes:errors.append('Handler core: input mismatch')
    expected=dict(entry='0x1507be0e0',stop='0x15a4e0d9a',runtimeStatus='not-tested',wholeHandlerExecuted=False)
    if any(handler.get(k)!=v for k,v in expected.items()):errors.append('Handler core: execution scope mismatch')
    binding=handler.get('fieldBinding',{})
    required=dict(typeIndex=37481,clientField='PJAJDHBBMGB',objectOffset=0xd4,tag=9,type='uint32',
                  semanticField='uid',status='verified',sessionOffset=0x168,prefsKey='__LastUid__')
    if binding!=required:errors.append('Handler core: UID binding mismatch')
    raw=[f for m in wire['messages'] if m['typeIndex']==37481 for f in m['fields'] if f['objectOffset']==0xd4]
    schema=next((s for s in document['schemas'] if s['semanticName']=='GetPlayerTokenRsp'),{})
    mapped=[f for f in schema.get('fields',[]) if 'token-handler-uid' in f.get('evidence',[])]
    if (len(raw)!=1 or any(raw[0].get(k)!=binding.get(k) for k in ('tag','type','clientField')) or
            len(mapped)!=1 or any(mapped[0].get(k)!=binding.get(k) for k in ('tag','type','clientField','semanticField','status'))):
        errors.append('Handler core: raw/semantic UID disagreement')
    rows=handler.get('results',[])
    if [(r.get('oldWarmup'),r.get('uid')) for r in rows]!=[(True,1),(False,0xffffffff),(False,0x80000000),(True,0)]:
        errors.append('Handler core: UID/warmup fixture set mismatch')
    for r in rows:
        expected_warmup=[] if r.get('oldWarmup') else [{'operation':202,'size':16,
            'length':0 if r.get('uid')==0xffffffff else 8,'return':-2 if r.get('uid')==0xffffffff else -1}]
        if r.get('warmup')!=expected_warmup or not r.get('uidSaved') or not r.get('tokenPointerPreserved'):
            errors.append('Handler core: warmup result or session store mismatch')
    return errors


def validate_auth_sources(document,profile,auth,wire):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if auth.get('inputSHA256')!=hashes:errors.append('Auth sources: input mismatch')
    expected=dict(entry='0x14f1917d0',successStop='0x14f191e18',earlyStop='0x14f192592',errorStop='0x14f191882',
                  runtimeStatus='not-tested',wholeCallbackExecuted=False,nativeSeedHelperExecuted=True,cmdId=24604)
    if any(auth.get(k)!=v for k,v in expected.items()):errors.append('Auth sources: execution scope mismatch')
    required={(0x78,0x18,9,'string','account_uid','data.open_id'),(0xf8,0x40,2,'string','account_token','data.combo_token'),
              (0x138,0xb0,3,'uint32','channel_id','SDK info_get_channel_id'),
              (0x13c,0xa4,15,'uint32','account_type','data.account_type'),(0x15f,0xc4,11,'bool','is_guest','data.guest')}
    bindings=auth.get('fieldBindings',[])
    actual={(b.get('sessionOffset'),b.get('objectOffset'),b.get('tag'),b.get('type'),b.get('semanticField'),b.get('source')) for b in bindings}
    if len(bindings)!=5 or actual!=required:errors.append('Auth sources: credential source set mismatch')
    schema=next((s for s in document['schemas'] if s['semanticName']=='GetPlayerTokenReq'),{})
    for b in bindings:
        raw=[f for f in wire['fields'] if f.get('objectOffset')==b.get('objectOffset')]
        mapped=[f for f in schema.get('fields',[]) if f.get('clientField')==b.get('clientField')]
        if (b.get('typeIndex')!=82491 or b.get('status')!='verified' or len(raw)!=1 or
                any(raw[0].get(k)!=b.get(k) for k in ('tag','type','clientField')) or len(mapped)!=1 or
                'token-sdk-auth-sources' not in mapped[0].get('evidence',[]) or
                any(mapped[0].get(k)!=b.get(k) for k in ('tag','type','semanticField','status'))):
            errors.append('Auth sources: wire/semantic disagreement')
    cases=[('nonzero',True),('uint32-max',True),('optional-absent',True),('account-type-overflow',True),
           ('error-ret',False),('missing-open-id',False),('empty-token',False),('invalid-channel',False),('channel-overflow',False)]
    if [(r.get('name'),r.get('accepted')) for r in auth.get('results',[])]!=cases:
        errors.append('Auth sources: source acceptance/rejection cases changed')
    declared=auth.get('declarations',{})
    if any(declared.get(k)!=v for k,v in dict(jsonType=8987,lazyClassType=9000,lazyClassUsageRow=701114,lazyClassCell='0x145891a78').items()):
        errors.append('Auth sources: JSON/lazy runtime identity mismatch')
    return errors


def validate_key_selection(document, profile, selection, wire):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if selection.get('inputSHA256')!=hashes:errors.append('Key selection: input mismatch')
    if any(selection.get(k)!=v for k,v in dict(runtimeStatus='not-tested',wholeExchangeExecuted=False,
                                               keyVerificationForced=False,fixtureCount=6).items()):
        errors.append('Key selection: execution scope mismatch')
    binding=selection.get('fieldBinding',{})
    expected=dict(typeIndex=82491,clientField='FMMLBHKFGDH',objectOffset=0x98,tag=1184,type='uint32',
                  semanticField='key_id',status='verified',nativeValue=4,store='0x151220094')
    if binding!=expected:errors.append('Key selection: field/value/store mismatch')
    raw=[f for f in wire['fields'] if f['objectOffset']==0x98]
    schema=next((s for s in document['schemas'] if s['semanticName']=='GetPlayerTokenReq'),{})
    mapped=[f for f in schema.get('fields',[]) if 'token-key-selection' in f.get('evidence',[])]
    if (len(raw)!=1 or any(raw[0].get(k)!=binding.get(k) for k in ('tag','type','clientField')) or len(mapped)!=1 or
            any(mapped[0].get(k)!=binding.get(k) for k in ('tag','type','clientField','semanticField','status'))):
        errors.append('Key selection: raw/semantic disagreement')
    url=selection.get('urlArgument',{})
    if (any(url.get(k)!=v for k,v in dict(method='Miscs.GetGlobalDispatchUrl',entry='0x1531e0a10',value=4,
            formatterMethodIndex=3829,formatKind='uint32',formatterExecuted=False,concatExecuted=False,
            formattedStringFixture='4').items()) or url.get('literal')!={'index':61981,'value':'&key_id='}):
        errors.append('Key selection: independent named URL argument mismatch')
    requests=selection.get('requestResults',[])
    if [(r.get('selector'),r.get('selectorWire'),r.get('clientSeedHex'),r.get('passed')) for r in requests]!=[
            (4,'804a04',seed,True) for seed in ('0000000000000000','8000000000000000','fffffffffffff800')]:
        errors.append('Key selection: native request cases changed')
    files=selection.get('publicKeyFiles',{})
    if (set(files)!={'2','3','4','5'} or [k for k,v in files.items() if v.get('matchesNativePrivate')]!=['4'] or
            any(v.get('path')!=f'src/main/resources/keys/game_keys/{k}_Pub.der' for k,v in files.items())):
        errors.append('Key selection: unique public/private association changed')
    cases=[(f'selected-{i}',i,4,True) for i in range(3)]+[(f'wrong-key-{k}',1,k,False) for k in (2,3,5)]
    results=selection.get('results',[])
    if [(r.get('name'),r.get('requestIndex'),r.get('selectedKeyId'),r.get('matchesRequest')) for r in results]!=cases:
        errors.append('Key selection: differential case set changed')
    for r,(_,_,_,selected) in zip(results,cases):
        if (r.get('nativeOperations')!={'101':0 if selected else -7,'103':0 if selected else -7} or
                r.get('stop')!=('0x15a4e0d4b' if selected else '0x15a4e0645') or
                r.get('sessionKeyInstalled')!=selected or r.get('javaByteComparison')!=selected or not r.get('passed') or
                bool(r.get('sessionKeySHA256'))!=selected or
                [(k.get('operation'),k.get('oracle'),k.get('native')) for k in r.get('keyChecks',[])]!=[(101,True,True),(103,True,True)]):
            errors.append('Key selection: native crypto/key installation outcome mismatch')
    if selection.get('trustInputReplacements')!=['native private-key signature','server public key and signature','root PEM input']:
        errors.append('Key selection: missing explicit local trust inputs')
    return errors


def validate_send_flow(document, profile, flow):
    errors=[]
    hashes={profile['files'][k]['path']:profile['files'][k]['sha256'] for k in ('binary','metadata','startup')}
    if flow.get('inputSHA256')!=hashes:errors.append('Send flow: input mismatch')
    if (flow.get('entry')!='0x151214d20' or flow.get('connectedStop')!='0x14a447e00' or
            flow.get('frameBuilderExecuted') is not False or flow.get('runtimeStatus')!='not-tested'):
        errors.append('Send flow: bounded execution claim mismatch')
    signatures={(r.get('ownerTypeIndex'),r.get('methodIndex'),r.get('parameterIndex'),
                 r.get('parameterTypeIndex'),r.get('envelopeTypeIndex')) for r in flow.get('transportParameterBindings',[])}
    expected={(79029,655015,503964,165362,13973),(68785,569162,447221,165362,13973),(32016,250987,197935,165362,13973)}
    if signatures!=expected or len(flow.get('transportParameterBindings',[]))!=3:
        errors.append('Send flow: transport/envelope declaration mismatch')
    requests=flow.get('requestBindings',[])
    names={'GetPlayerTokenReq':(82491,24604,'0x15121ff70','0x1500c4a50','0x1458c5500'),
           'PlayerLoginReq':(76410,23172,'0x151213730','0x152ef20f0','0x1458c3df0')}
    if len(requests)!=2 or {r.get('semanticName') for r in requests}!=set(names):
        errors.append('Send flow: request set mismatch')
    opcodes={r['semanticName']:r for r in document['opcodes']}
    for request in requests:
        name=request.get('semanticName')
        if name not in names:continue
        if tuple(request.get(k) for k in ('typeIndex','cmdId','constructorEntry','commandGetter','selfClassCell'))!=names[name]:
            errors.append('Send flow: request native binding mismatch')
        if opcodes.get(name,{}).get('cmdId')!=request.get('cmdId'):
            errors.append('Send flow: mapped command disagreement')
    return errors


def validate_scene_wait(document, profile, table):
    errors = []
    hashes = {profile['files'][key]['path']: profile['files'][key]['sha256'] for key in ('binary', 'metadata', 'startup')}
    if table.get('inputSHA256') != hashes: errors.append('Scene wait: input hash mismatch')
    declaration = table.get('declarations', {})
    expected = dict(typeIndex=46311, clientType='BBECFDMDFJC', cmdId=27748, getter='0x1523856a0', semanticName='PlayerEnterSceneNotify')
    if any(declaration.get(key) != value for key, value in expected.items()): errors.append('Scene wait: identity declaration mismatch')
    if (table.get('runtimeStatus') != 'not-tested' or table.get('targetProtocolReady') is not False or
            table.get('closureComplete') is not False or len(table.get('fields', [])) != 26):
        errors.append('Scene wait: bounded evidence scope mismatch')
    try:
        from .trace_client72_scene_wait import validate_fixtures
    except ImportError:
        from trace_client72_scene_wait import validate_fixtures
    try:
        validate_fixtures(table['observations'])
    except ValueError as error:
        errors.append('Scene wait: ' + str(error))
    for key, evidence in (('opcodes', 'scene-enter-opcode'), ('messages', 'scene-enter-message')):
        row = next(r for r in document[key] if r['semanticName'] == 'PlayerEnterSceneNotify')
        if row.get('clientType') != 'BBECFDMDFJC' or row.get('status') != 'verified' or evidence not in row.get('evidence', []):
            errors.append('Scene wait: mapped identity evidence mismatch')
    schema = next(r for r in document['schemas'] if r['semanticName'] == 'PlayerEnterSceneNotify')
    if (schema.get('status') != 'candidate' or len(schema.get('fields', [])) != 26
            or any(f['status'] != 'unresolved' for f in schema['fields'] if f['tag'] not in {7,8,10,14})):
        errors.append('Scene wait: unknown field semantics promoted')
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
                          'response-graph-observations.json', 'map-aot-observations.json', 'born-observations.json')]
    scene_wait = json.loads((args.directory / 'scene-wait-observations.json').read_text('utf8'))
    extra.append(('scene-wait', dict(inputSHA256=scene_wait['inputSHA256'], messages=[dict(
        clientType=scene_wait['declarations']['clientType'], fields=scene_wait['fields'])])))
    errors.extend(validate_observations(document, profile, commands, wire, protected, extra))
    errors.extend(validate_scene_wait(document, profile, scene_wait))
    scene_tables=[json.loads((args.directory/name).read_text('utf8')) for name in (
        'scene-handshake-observations.json','store-weight-contract-observations.json','initialization-wire-observations.json')]
    errors.extend(validate_scene_fusion(document,profile,*scene_tables))
    enter_tables=[json.loads((args.directory/name).read_text('utf8')) for name in (
        'enter-scene-contract-observations.json','cmd1017-domain-observations.json')]
    errors.extend(validate_enter_scene_fusion(document,profile,*enter_tables))
    semantic_tables=[json.loads((args.directory/name).read_text('utf8')) for name in
                     ('login-semantic-observations.json','login-literal-observations.json','enum-observations.json','login-wire-observations.json')]
    errors.extend(validate_login_semantics(document,profile,*semantic_tables))
    flow=json.loads((args.directory/'session-token-observations.json').read_text('utf8'))
    errors.extend(validate_session_token_flow(document,profile,flow,[t for name,t in extra
                  if name in ('login-wire-observations.json','response-wire-observations.json')]))
    identity=json.loads((args.directory/'request-identity-observations.json').read_text('utf8'))
    errors.extend(validate_request_identities(document,profile,identity,semantic_tables[2],semantic_tables[0]))
    constructor=json.loads((args.directory/'login-constructor-observations.json').read_text('utf8'))
    errors.extend(validate_login_constructor(document,profile,constructor,semantic_tables[3]))
    token_constructor=json.loads((args.directory/'token-constructor-observations.json').read_text('utf8'))
    errors.extend(validate_token_constructor(document,profile,token_constructor,protected))
    token_complete=json.loads((args.directory/'token-complete-observations.json').read_text('utf8'))
    errors.extend(validate_token_complete(document,profile,token_complete,protected))
    response_crypto=json.loads((args.directory/'response-crypto-observations.json').read_text('utf8'))
    errors.extend(validate_response_crypto(document,profile,response_crypto,next(t for name,t in extra if name=='response-wire-observations.json')))
    handler_core=json.loads((args.directory/'handler-core-observations.json').read_text('utf8'))
    errors.extend(validate_handler_core(document,profile,handler_core,next(t for name,t in extra if name=='response-wire-observations.json')))
    auth_sources=json.loads((args.directory/'auth-sources-observations.json').read_text('utf8'))
    errors.extend(validate_auth_sources(document,profile,auth_sources,protected))
    key_selection=json.loads((args.directory/'key-selection-observations.json').read_text('utf8'))
    errors.extend(validate_key_selection(document,profile,key_selection,protected))
    send_flow=json.loads((args.directory/'send-flow-observations.json').read_text('utf8'))
    errors.extend(validate_send_flow(document,profile,send_flow))
    print(json.dumps({'mode': 'ready' if args.ready else 'research-structure', 'valid': not errors, 'errors': errors},
                     ensure_ascii=False, indent=2))
    raise SystemExit(bool(errors))


if __name__ == '__main__':
    main()
