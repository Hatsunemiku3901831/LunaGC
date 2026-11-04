"""Research schema rendering and fixture/protoc comparison, never server generation."""
import json
import re
import subprocess
from pathlib import Path


def identifier(value):
    if not re.fullmatch('[A-Za-z_][A-Za-z_0-9]*', value):
        raise ValueError('Invalid research identifier')
    return value


def render(messages):
    lines = ['// Generated from target observations. Research only; no runtime acceptance.',
             '// Enum fields use int32 storage until symbolic values are recovered.',
             'syntax = "proto3";', 'package cn72.research;', '']
    for msg in messages:
        lines.append('message ' + identifier(msg['clientType']) + ' {')
        oneofs = {}
        for f in msg['fields']:
            if f['type']=='map':
                value=identifier(f['nestedType']) if f['valueType']=='message' else identifier(f['valueType'])
                kind=f"map<{identifier(f['keyType'])}, {value}>"
            else: kind = identifier(f['nestedType']) if f['type'] == 'message' else identifier(f['type'])
            prefix = 'repeated ' if f['cardinality'] == 'repeated' else ''
            suffix = ' [packed = true]' if f['packed'] else ''
            line = f"{prefix}{kind} {identifier(f['clientField'])} = {f['tag']}{suffix};"
            if f.get('oneof'): oneofs.setdefault(f['oneof'], []).append(line)
            else: lines.append('  ' + line)
        for name, fields in oneofs.items():
            lines.append('  oneof ' + identifier(name) + ' {')
            lines.extend('    ' + line for line in fields); lines.append('  }')
        lines.extend(['}', ''])
    return '\n'.join(lines)


def fixture_text(sample, schemas, samples):
    msg = schemas[sample['typeIndex']]; lines = []
    for f in msg['fields']:
        if f.get('oneof') and sample.get('case') != f['discriminatorValue']: continue
        value = sample['values'].get(str(f['objectOffset']))
        if value is None: continue
        name = f['clientField']
        if f['type'] == 'message':
            child = samples[(f['nestedTypeIndex'], value[1]['hex'])]
            lines.append(name + ' { ' + fixture_text(child, schemas, samples) + ' }')
        elif f['cardinality'] == 'repeated':
            lines.extend(f'{name}: {v}' for v in value[1])
        elif f['type'] in ('string', 'bytes'):
            data = value if f['type'] == 'string' else value[1]
            # Octal text-format escapes preserve arbitrary bytes without locale conversions.
            escaped = ''.join('\\%03o' % b for b in bytes.fromhex(data['hex']))
            lines.append(f'{name}: "{escaped}"')
        elif f['type'] == 'bool': lines.append(f'{name}: {"true" if value else "false"}')
        else: lines.append(f'{name}: {value}')
    return ' '.join(lines)


def compare_fixtures(protoc, proto, mapping, report):
    schemas = {m['typeIndex']: m for m in mapping['messages']}
    samples = {(s['typeIndex'], s['wire']): s for s in report['fixtures']}
    for sample in report['fixtures']:
        text = fixture_text(sample, schemas, samples)
        name = schemas[sample['typeIndex']]['clientType']
        result = subprocess.run([str(protoc), '--proto_path=' + str(proto.parent),
                                 '--encode=cn72.research.' + name, str(proto)], input=text.encode('ascii'), capture_output=True, check=True)
        if result.stdout.hex() != sample['wire']:
            raise ValueError(f'protoc/native mismatch for {name}: {text}')
    return len(report['fixtures'])


def result_text(index, result, schemas):
    """Encode an independently expected object graph, never decode native bytes."""
    fields={f['clientField']:f for f in schemas[index]['fields']};lines=[]
    def scalar(kind,value):
        if kind=='bool':return 'true' if value else 'false'
        if kind in ('string','bytes'):
            data=value.encode('ascii') if kind=='string' else bytes.fromhex(value['hex'])
            return '"'+''.join('\\%03o'%b for b in data)+'"'
        return str(value)
    for name,value in result.items():
        f=fields[name];identifier(name)
        if f['cardinality']=='map':
            for key,item in value.items():
                key_text=scalar(f['keyType'],key)
                part='value { '+result_text(f['nestedTypeIndex'],item,schemas)+' }' if f['valueType']=='message' else 'value: '+scalar(f['valueType'],item)
                lines.append(name+' { key: '+key_text+' '+part+' }')
        else:
            for item in (value if f['cardinality']=='repeated' else [value]):
                lines.append(name+' { '+result_text(f['nestedTypeIndex'],item,schemas)+' }' if f['type']=='message' else name+': '+scalar(f['type'],item))
    return ' '.join(lines)
