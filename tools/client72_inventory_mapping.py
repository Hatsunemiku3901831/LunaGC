"""Reviewed inventory mapping slice; native wire and business sources stay separate."""
import hashlib
import json

try:
    from .extract_client72 import HASHES
    from .generate_inventory72_wire import RAW_CANONICAL_SHA256
    from .client72_inventory_contract import validate_report as validate_material
    from .client72_equip_contract import validate_report as validate_equip
except ImportError:
    from extract_client72 import HASHES
    from generate_inventory72_wire import RAW_CANONICAL_SHA256
    from client72_inventory_contract import validate_report as validate_material
    from client72_equip_contract import validate_report as validate_equip

NAMES = {68537: 'PlayerStoreNotify', 65333: 'Item', 63442: 'Material',
         44169: 'Equip', 64947: 'Weapon', 74063: 'Reliquary'}
REPORT_SHA256 = (RAW_CANONICAL_SHA256,
    'd4c9c4be68e479d5631bf7d870450fec52b1da661e47368a54557ef156ece31d',
    'a4c71df6fbdc31c7ed44a12849c7cad8df21a226165b2a2468bbb8db5724c3bc')


def inventory_fields(raw, material, equip):
    bindings = {(b['typeIndex'], b['tag']): (b['semanticField'], reference)
                for table, reference in ((material, 'inventory-material-fields'), (equip, 'inventory-equip-fields'))
                for b in table['fieldBindings']}
    observed = {row['typeIndex']: row for row in raw['messages']}
    result = {}
    for index, name in NAMES.items():
        fields = []
        for source in observed[index]['fields']:
            field = {k: v for k, v in source.items() if k != 'semanticStatus'}
            semantic, reference = bindings.get((index, source['tag']), (None, None))
            field.update(semanticField=semantic or f"unknown_field_{source['tag']}",
                status='verified' if semantic else 'unresolved', evidence=['inventory-wire'] + ([reference] if semantic else []),
                ambiguities=[] if semantic else ['Business meaning unresolved'],
                packed=source.get('packed', False), presence='oneof' if source.get('oneof') else (
                    'explicit' if source['type'] == 'message' and source['cardinality'] == 'singular' else 'implicit'),
                wireStatus='native-graph-observed', runtimeStatus='not-tested')
            if source.get('nestedTypeIndex'):
                child = source['nestedTypeIndex']; field['clientNestedType'] = observed[child]['clientType']
                if semantic: field['nestedType'] = NAMES[child]
            if (index, source['tag']) == (68537, 14):
                field['applicationEnumValues'] = {'StoreNone': 0, 'StorePack': 1, 'StoreDepot': 2}
            elif (index, source['tag']) in ((44169, 3), (74063, 6)):
                field['applicationEncoding'] = dict(wireFalseToModel=1, wireTrueToModel=2,
                    targetEnumType=85101 if index == 44169 else 73539)
            fields.append(field)
        result[name] = fields
    return result


def validate_inventory(document, profile, raw, material, equip):
    errors = ['Inventory material: ' + error for error in validate_material(material)]
    errors += ['Inventory equip: ' + error for error in validate_equip(equip)]
    for table, expected in zip((raw, material, equip), REPORT_SHA256):
        canonical = (json.dumps(table, indent=2) + '\n').encode('utf8')
        if hashlib.sha256(canonical).hexdigest() != expected or table.get('inputSHA256') != HASHES:
            errors.append('Inventory: approved native report changed')
    hashes = {profile['files'][key]['path']: profile['files'][key]['sha256']
              for key in ('binary', 'metadata', 'startup')}
    if hashes != HASHES: errors.append('Inventory: fixed profile changed')
    if errors: return errors
    tables = {key: {r['semanticName']: r for r in document[key]} for key in ('opcodes', 'messages', 'schemas')}
    observed = {row['typeIndex']: row for row in raw['messages']}
    expected_fields = inventory_fields(raw, material, equip)
    for index, name in NAMES.items():
        prefix = 'inventory' if index == 68537 else 'inventory-' + name.lower()
        for table, reference in (('opcodes', prefix + ('-command' if index == 68537 else '-embedded')),
                                 ('messages', prefix + '-identity')):
            row = tables[table].get(name, {})
            if (row.get('status') != 'verified' or row.get('clientType') != observed[index]['clientType'] or
                    row.get('evidence') != [reference] or row.get('ambiguities') or
                    (table == 'opcodes' and row.get('cmdId') != (20892 if index == 68537 else None)) or
                    (table == 'messages' and (row.get('direction') != ('S2C' if index == 68537 else 'embedded') or
                        row.get('aliases') or row.get('runtimeStatus') != 'not-tested'))):
                errors.append(f'Inventory {name}: native identity/direction mismatch')
        schema = tables['schemas'].get(name, {})
        state = 'verified' if all(f['status'] == 'verified' for f in expected_fields[name]) else 'candidate'
        if (schema.get('status') != state or schema.get('fields') != expected_fields[name] or
                schema.get('evidence') != ['inventory-wire', 'inventory-material-fields', 'inventory-equip-fields']):
            errors.append(f'Inventory {name}: raw/semantic boundary changed')
    return errors
