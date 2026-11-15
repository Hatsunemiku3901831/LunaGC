"""Project accepted saved-current Depot fields from immutable host reports."""

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / 'local/migration72/d02-resume-20261116/old121-depot-core-fragment'
PINS = {
    'depotLogical': ('local/migration72/d02-resume-20261110b/c-all-depot-refs/main-third.private.json', 9014403, 'b6c806dfd5634f20501a9e57a70d88bf4d41b63b47740cf68dff817b7d79527a'),
    'mapping': ('local/migration72/d02-resume-20261108/r3-mapping/main-third.json', 10370640, 'd990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e'),
    'snapshot': ('local/migration72/d02-resume-20261110b/old-account-snapshot/run-20261110-2/selected-dependencies.private.json', 5459790, '3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7'),
    'bridgeRuleSource': ('tools/client72_d02_resource_bridge.py', 21485, 'a61531b49894a69a864c0467d683bb8f749dfcfe57051eda83b17a1cebdbbc11'),
    'depotReportProducerSource': ('tools/client72_d02_all_saved_depot_refs.py', 19043, 'faa91135c7c5cba3b09ed48a44866671289d434f296830b063232e2d332164cc'),
    'javaDependencySource': ('src/main/java/emu/grasscutter/data/excels/avatar/AvatarSkillDepotData.java', 5169, '2aa9dd97dd2d375d430af7053d3e7adab58e91cf3b997fadf5852d33c24d4f52'),
}
FIELDS = {'id': (105363, '0x54'), 'skills': (105358, '0x30'), 'energySkill': (105369, '0x6c')}
PRESENCE = 'not observed; object state does not distinguish absent input from defaults'
context = {'stage': 'entry', 'path': '', 'jsonPointer': ''}


def check(condition, message):
    if not condition:
        raise ValueError(message)


def integer(value, positive=False):
    check(type(value) is int and (1 if positive else 0) <= value <= 0x7fffffff,
          'expected an exact integer within Java int bounds')
    return value


def span(value):
    check(type(value) is dict and set(value) == {'begin', 'end', 'sha256'}, 'invalid source span shape')
    check(type(value['begin']) is int and type(value['end']) is int and 0 <= value['begin'] < value['end'], 'invalid source span bounds')
    digest = value['sha256']
    check(type(digest) is str and len(digest) == 64 and all(c in '0123456789abcdef' for c in digest), 'invalid source span hash')
    return value


def read_inputs():
    data = {}
    for name, (path, size, digest) in PINS.items():
        context.update(stage='input-pin', path=path, jsonPointer='', expectedSHA256=digest)
        blob = (ROOT / path).read_bytes()
        actual = hashlib.sha256(blob).hexdigest()
        context['actualSHA256'] = actual
        check(len(blob) == size and actual == digest, f'input pin mismatch: bytes={len(blob)}')
        if name in ('depotLogical', 'mapping', 'snapshot'):
            data[name] = json.loads(blob)
    context.pop('actualSHA256', None)
    context.pop('expectedSHA256', None)
    return data


def table_rows(table, count, path):
    context.update(stage='table', path=PINS['mapping'][0], jsonPointer=path)
    rows = table['rows']
    check(type(rows) is list and len(rows) == count and table['count'] == count, 'table count mismatch')
    result = {}
    for i, row in enumerate(rows):
        context['jsonPointer'] = f'{path}/rows/{i}'
        key = integer(row['key'], True)
        check(key not in result, 'duplicate table key')
        result[key] = (span(row['source']), f'{path}/rows/{i}/source')
    return result


def project_field(sample, sample_index, name):
    index, offset = FIELDS[name]
    context.update(stage='field', path=PINS['depotLogical'][0], jsonPointer=f'/samples/{sample_index}/fields')
    found = [(i, f) for i, f in enumerate(sample['fields']) if f['fieldIndex'] == index]
    check(len(found) == 1, 'expected exactly one field')
    i, field = found[0]
    pointer = f'/samples/{sample_index}/fields/{i}'
    context['jsonPointer'] = pointer
    check(field['owner'] == 22284 and field['offset'] == offset, 'field owner or offset mismatch')
    kind = field['type']
    if name == 'skills':
        check(kind['kind'] == 'szarray' and kind['rank'] == 1 and kind['elementType']['kind'] == 'valuetype' and kind['elementType']['typeDefinition'] == 58209, 'array type mismatch')
        check(type(field['value']) is list, 'skills is not a witnessed list')
        values = field['value']
    else:
        check(kind['kind'] == 'valuetype' and kind['typeDefinition'] == 58209, 'scalar type mismatch')
        values = [field['value']]
    check(field['inputPresence'] == PRESENCE, 'unexpected input presence')
    getters = field['getters']
    check(type(getters) is list and len(getters) == len(values), 'getter count mismatch')
    for j, (value, getter) in enumerate(zip(values, getters)):
        context['jsonPointer'] = f'{pointer}/getters/{j}'
        integer(value, name == 'id')
        check(type(getter['value']) is int and getter['value'] == value, 'getter value mismatch')
        trace = getter['trace']
        check(trace['entry'] == '0x14aef9db0' and trace['stop'] == '0x620000100' and
              trace['executedAddresses'].get('0x14aef9db0') == 1 and
              trace['executedAddresses'].get('0x620000100') == 1, 'getter entry/RETURN witness mismatch')
    return field['value'], field['inputPresence'], {
        'inputSHA256': PINS['depotLogical'][2], 'owner': 22284, 'fieldIndex': index,
        'offset': offset, 'type': '58209[]' if name == 'skills' else 58209,
        'fieldJSONpointer': pointer, 'getterJSONpointers': [f'{pointer}/getters/{j}' for j in range(len(getters))],
    }


def projection(data):
    logical, mapping, snapshot = (data[k] for k in ('depotLogical', 'mapping', 'snapshot'))
    context.update(stage='cohort', path=PINS['snapshot'][0], jsonPointer='/avatars')
    avatars = snapshot['avatars']
    check(type(avatars) is list and len(avatars) == 121, 'snapshot cohort count mismatch')
    pairs = {}
    for i, avatar in enumerate(avatars):
        context['jsonPointer'] = f'/avatars/{i}/fields'
        fields = avatar['fields']
        pair = (integer(fields['avatarId'], True), integer(fields['skillDepotId'], True))
        check(pair not in pairs, 'duplicate saved pair')
        pairs[pair] = f'/avatars/{i}/fields'
    check(len({p[0] for p in pairs}) == 121 and len({p[1] for p in pairs}) == 121, 'duplicate avatar or saved depot')

    context.update(stage='table', path=PINS['mapping'][0], jsonPointer='/native/parsedTables')
    tables = mapping['native']['parsedTables']
    depot_matches = [(i, t) for i, t in enumerate(tables) if t['name'] == 'AvatarSkillDepot']
    skill_matches = [(i, t) for i, t in enumerate(tables) if t['name'] == 'AvatarSkill']
    check(len(depot_matches) == len(skill_matches) == 1, 'table selection mismatch')
    di, depot = depot_matches[0]
    si, skill = skill_matches[0]
    depots = table_rows(depot, 171, f'/native/parsedTables/{di}')
    skills = table_rows(skill, 1176, f'/native/parsedTables/{si}')
    context.update(stage='whole', path=PINS['depotLogical'][0], jsonPointer='/whole')
    whole = logical['whole']
    check(whole['count'] == 171 and whole['cursor'] == depot['cursor'] == 26024, 'whole count/cursor mismatch')
    rows = whole['rows']
    check(type(rows) is list and len(rows) == 171, 'whole rows mismatch')
    whole_pairs = []
    for i, row in enumerate(rows):
        context['jsonPointer'] = f'/whole/rows/{i}'
        whole_pairs.append((integer(row['key'], True), span(row['source'])))
    check(whole_pairs == [(k, v[0]) for k, v in depots.items()], 'whole key/order/source mismatch')

    context.update(stage='controls', path=PINS['mapping'][0], jsonPointer='/javaProjectionFragments')
    controls = [(i, x) for i, x in enumerate(mapping['javaProjectionFragments']) if x['table'] == 'AvatarSkillDepot']
    check(len(controls) == 5 and len({x['key'] for _, x in controls}) == 5, 'control count or uniqueness mismatch')
    controls_by_key = {x['key']: (i, x) for i, x in controls}
    fragments, saved, seen, matches = [], [], set(), []
    positive, energy, slots, zero = set(), set(), 0, 0
    samples = logical['samples']
    context.update(stage='cohort', path=PINS['depotLogical'][0], jsonPointer='/samples')
    check(type(samples) is list and len(samples) == 121, 'sample count mismatch')
    for i, sample in enumerate(samples):
        context.update(stage='cohort', path=PINS['depotLogical'][0], jsonPointer=f'/samples/{i}')
        pair = (integer(sample['avatarId'], True), integer(sample['key'], True))
        check(pair in pairs and pair not in seen, 'sample pair missing or duplicated')
        seen.add(pair)
        check(pair[1] in depots and span(sample['source']) == depots[pair[1]][0], 'sample source differs from whole')
        fields, presence, provenance = {}, {}, {}
        for name in FIELDS:
            fields[name], presence[name], provenance[name] = project_field(sample, i, name)
        check(fields['id'] == pair[1], 'id differs from key')
        if pair[1] in controls_by_key:
            ci, control = controls_by_key[pair[1]]
            context.update(stage='control', path=PINS['mapping'][0], jsonPointer=f'/javaProjectionFragments/{ci}')
            check(control['fragmentOnly'] is True and control['source'] == sample['source'] and control['javaFields'] == fields, 'control mismatch')
            matches.append({'key': pair[1], 'mappingJSONpointer': f'/javaProjectionFragments/{ci}'})
        slots += len(fields['skills'])
        zero += fields['skills'].count(0)
        positive.update(v for v in fields['skills'] if v > 0)
        if fields['energySkill'] > 0:
            energy.add(fields['energySkill'])
            positive.add(fields['energySkill'])
        fragments.append({'table': 'AvatarSkillDepot', 'key': pair[1], 'source': sample['source'],
                          'javaFields': fields, 'fragmentOnly': True, 'fullDeclaredJavaInputRow': False,
                          'actualJavaLoaded': False, 'inputPresence': presence, 'fieldProvenance': provenance})
        saved.append({'avatarId': pair[0], 'savedCurrentDepotId': pair[1], 'snapshotJSONpointer': pairs[pair],
                      'logicalSampleJSONpointer': f'/samples/{i}'})
    context.update(stage='reconciliation', path=PINS['depotLogical'][0], jsonPointer='/samples')
    check(seen == set(pairs), 'missing sample pair')
    check((slots, slots - zero, zero, len(energy), len(positive)) == (484, 248, 236, 121, 369), 'reference counts mismatch')
    approved = {100540, 100550, 10067, 10068}
    check(positive & approved == {10067, 10068} and energy & approved == {10068}, 'approved Skill intersections mismatch')
    context.update(stage='skill-key-span', path=PINS['mapping'][0], jsonPointer=f'/native/parsedTables/{si}/rows')
    check(positive <= skills.keys(), 'positive Skill key missing')
    references = [{'id': key, 'source': skills[key][0], 'mappingSourceJSONpointer': skills[key][1]} for key in sorted(positive)]
    return {
        'task': 'R3-OLD121-DEPOT-CORE-FRAGMENT-PROJECTION',
        'inputPins': {k: {'path': v[0], 'bytes': v[1], 'sha256': v[2]} for k, v in PINS.items()},
        'fragments': fragments, 'savedCurrentDepotPairs': saved,
        'controls': {'mappingControlCount': 5, 'matched': matches, 'priorRealJavaControlKeys': [501, 701, 704]},
        'skillReferences': {'status': 'key/spanOnly', 'positions': 'fragments[].javaFields skills and energySkill preserve positions',
                            'skillsSlots': slots, 'regularPositive': slots - zero, 'regularZero': zero,
                            'energyPositive': len(energy), 'positiveUnique': len(positive), 'entries': references,
                            'approvedIntersection': [10067, 10068], 'missingAllPositiveFragments': 367,
                            'energyApprovedIntersection': [10068], 'missingPositiveEnergyFragments': 120},
        'flags': {'newNativeCalls': 0, 'newTargetCPUCalls': 0, 'newModels': 0, 'newPEReads': 0,
                  'liveReads': 0, 'constructors': 0, 'resourceReady': False,
                  'completePlayerRecovery': False, 'd02Complete': False},
    }


def main():
    success = OUTPUT / 'projection.private.json'
    failure = OUTPUT / 'failure.private.json'
    if success.exists() or failure.exists():
        raise SystemExit('Output or failure receipt already exists; refusing to overwrite')
    try:
        result = projection(read_inputs())
        context.update(stage='serialization', path=str(success.relative_to(ROOT)), jsonPointer='')
        encoded = (json.dumps(result, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8')
        check(len(encoded) <= 1048576, f'output exceeds budget: {len(encoded)} bytes')
        OUTPUT.mkdir(parents=True, exist_ok=True)
        with success.open('xb') as out:
            out.write(encoded)
        print(json.dumps({'bytes': len(encoded), 'sha256': hashlib.sha256(encoded).hexdigest(),
                          'fragments': len(result['fragments']), 'controlsMatched': len(result['controls']['matched'])}))
    except Exception as exc:
        receipt = dict(context, errorType=type(exc).__name__, problem=str(exc))
        encoded = (json.dumps(receipt, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8')
        if len(encoded) > 1048576:
            raise SystemExit('Failure receipt exceeds budget') from exc
        OUTPUT.mkdir(parents=True, exist_ok=True)
        with failure.open('xb') as out:
            out.write(encoded)
        raise SystemExit(f'Projection stopped at {context["stage"]}; failure receipt saved') from exc


if __name__ == '__main__':
    main()
