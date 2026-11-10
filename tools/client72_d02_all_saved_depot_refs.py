"""Offline, pin-bound all saved Depot typed fields; no semantic projection."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261110b/c-all-depot-refs'
OLD_OUT = ROOT / 'local/migration72/d02-resume-20261110b/c-depot-fields'
COVERAGE = ROOT / 'local/migration72/d02-resume-20261110b/c-old-avatar/third.private.json'
COVERAGE_SHA = 'ca9b24ed6e47cca54bc069a1c4f3b8cf19b5900117860efe755ade49293246ea'
OLD_RESULT_SHA = '8c06c130d7f2efa6a31a5848eda471810834e019c1a1153158576d2c15f3dfd5'
OLD_DRIVER_SHA = 'ee165bd0bc624cb7616772a4dec5f1d8536a07a7104c50bb53b60dfc42cd8d3b'
COVERAGE_DRIVER_SHA = '18190368053f20272defbe10c3d55bdd1d91fc661bf75b66941fa2e5871c0bd3'
MAPPING = 'local/migration72/d02-resume-20261108/r3-mapping/main-third.json'
AUDIT = OLD_OUT / 'source-audit.private.json'
PAYLOAD = 'local/migration72/d02-active/resource-locator-astra/locator-final-1/AvatarSkillDepot.native-m_bytes.bin'
LOCATOR = 'local/migration72/d02-active/resource-locator-astra/locator-final-1.json'
CODEC = 'local/migration72/d02-active/plain/initialization-resources-elevated5.json'
PINS = {MAPPING: 'd990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e',
        PAYLOAD: 'fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b',
        LOCATOR: 'fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda',
        CODEC: 'c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d',
        'tools/client72_d02_resource_bridge.py': 'a61531b49894a69a864c0467d683bb8f749dfcfe57051eda83b17a1cebdbbc11'}
AUDIT_SHA = '7e6010fa975587364d5a89fe5bc02cf564b5ecb80fea3efda4dc49c6b95f04e4'
FIELDS = ((105354, 0x10), (105355, 0x18), (105357, 0x28), (105358, 0x30), (105363, 0x54), (105369, 0x6c))


def read_pinned(path, digest):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != digest:
        raise ValueError('Pinned input differs: ' + str(path.relative_to(ROOT)))
    return data


def check_rows(actual, expected):
    a = {x['key']: x['source'] for x in actual}
    b = {x['key']: x['source'] for x in expected}
    if (len(a) != 171 or len(b) != 171 or len(actual) != 171 or len(expected) != 171 or a != b
            or [x['key'] for x in actual] != [x['key'] for x in expected]):
        raise ValueError('Whole parser key/source mismatch')


def select_samples(snapshot, coverage, mapping, acceptance):
    from tools.client72_d02_old_avatar_coverage import analyze
    calculated = analyze(snapshot, mapping)
    for name, value in calculated.items():
        if coverage.get(name) != value:
            raise ValueError('Coverage differs from saved projection: ' + name)
    samples = calculated['avatars']
    if (len(samples) != 121 or acceptance['oldAccountActualAvatarCount'] != 121
            or calculated['missingActiveAvatarIds'] or len({x['currentDepotId'] for x in samples}) != 121
            or any(x['depotSource'] is None for x in samples)):
        raise ValueError('Saved selection incomplete or duplicated')
    return samples


def mark_presence(value):
    if isinstance(value, list):
        for item in value:
            mark_presence(item)
    elif isinstance(value, dict) and 'fieldIndex' in value and 'value' in value:
        value['inputPresence'] = 'unknown'
        mark_presence(value['value'])


def positive_fields(samples):
    from collections import Counter
    counts = {}
    def visit(value, index):
        if type(value) is int:
            if value > 0:
                counts.setdefault(index, Counter())[value] += 1
        elif isinstance(value, list):
            for item in value:
                visit(item, index)
        elif isinstance(value, dict):
            if 'fieldIndex' in value and 'value' in value:
                visit(value['value'], value['fieldIndex'])
            elif 'enumType' in value:
                visit(value['value'], index)
    for sample in samples:
        for field in sample['fields']:
            visit(field['value'], field['fieldIndex'])
    return [{'fieldIndex': index, 'values': [{'value': value, 'occurrences': count}
            for value, count in sorted(counter.items())]} for index, counter in sorted(counts.items())]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='new JSON basename inside task output directory')
    args = parser.parse_args()
    output = OUT / args.output
    if output.parent.resolve() != OUT.resolve() or output.suffix != '.json' or output.exists():
        parser.error('output must be a new JSON basename in the task directory')
    failure = output.with_suffix('.failure.json')
    if failure.exists():
        parser.error('failure evidence already exists')
    rt = None
    stage = 'source-gate'
    try:
        fixed = {name: read_pinned(ROOT / name, digest) for name, digest in PINS.items()}
        audit = json.loads(read_pinned(AUDIT, AUDIT_SHA))
        for group in ('inherited20PinsRechecked', 'newPins'):
            for name, proof in audit[group].items():
                data = read_pinned(ROOT / name, proof['sha256'])
                if len(data) != proof['bytes']:
                    raise ValueError('Audit source length differs: ' + name)
        read_pinned(ROOT / 'tools/client72_d02_old_depot_fields.py', OLD_DRIVER_SHA)
        read_pinned(ROOT / 'tools/client72_d02_old_avatar_coverage.py', COVERAGE_DRIVER_SHA)
        old_result = json.loads(read_pinned(OLD_OUT / 'main-third.private.json', OLD_RESULT_SHA))
        coverage = json.loads(read_pinned(COVERAGE, COVERAGE_SHA))
        from tools import client72_d02_old_avatar_coverage as coverage_tool
        coverage_blobs = {name: read_pinned(ROOT / name, digest) for name, digest in coverage_tool.PINS.items()}
        snapshot = json.loads(coverage_blobs[coverage_tool.SNAPSHOT + 'selected-dependencies.private.json'])
        manifest = json.loads(coverage_blobs[coverage_tool.SNAPSHOT + 'selected-manifest.private.json'])
        acceptance = json.loads(coverage_blobs[coverage_tool.SNAPSHOT + 'acceptance.private.json'])
        if (acceptance['selectedManifestSHA256'] != coverage_tool.PINS[coverage_tool.SNAPSHOT + 'selected-manifest.private.json']
                or acceptance['selectedDependenciesSHA256'] != coverage_tool.PINS[coverage_tool.SNAPSHOT + 'selected-dependencies.private.json']
                or not acceptance['accountOwnershipVerified'] or not acceptance['sourceAndWorkingDatabaseSeparated']
                or acceptance['wholeAccountOrClientRestored']):
            raise ValueError('Snapshot acceptance mismatch')
        for name, entry in manifest.items():
            if entry['sha256'] != snapshot['inputs'][name]['sha256'] or entry['format'] != snapshot['inputs'][name]['format']:
                raise ValueError('Manifest/projection mismatch')
            data = Path(entry['path']).read_bytes()
            if hashlib.sha256(data).hexdigest() != entry['sha256']:
                raise ValueError('Original export hash differs: ' + name)
        mapping = json.loads(fixed[MAPPING])
        samples = select_samples(snapshot, coverage, mapping, acceptance)
        for name, digest in mapping['sourcePins'].items():
            read_pinned(ROOT / name, digest)
        from tools import client72_avatar_natural_tables as card
        from tools.client72_avatar_cold_interfaces import FIVE_INPUTS
        from tools.client72_resource_deep import RETURN, DECODE
        client = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass',
                       '-File', str(ROOT / 'tools/game_path.ps1')], text=True).strip())
        inputs = [card.base.read_verified(client / name, digest) for name, digest in FIVE_INPUTS.items()]
        payload = fixed[PAYLOAD]
        if [x['avatarId'] for x in audit['activeSamples']] != audit['activeRegularTeamOrder']:
            raise ValueError('Fixed active sample selection differs')
        for sample in samples:
            span = sample['depotSource']
            if hashlib.sha256(payload[span['begin']:span['end']]).hexdigest() != span['sha256']:
                raise ValueError('Active payload span differs')
        codec = card.natural_codec(ROOT / CODEC)
        if codec['proof']['tableSHA256'] != '2c01c4289cd5c8e363f8b9f7d02446ea2bbad008ecc5f752d91e3c8769cfc76a' or codec['proof']['seedHex'] != '2e000a3b':
            raise ValueError('Storage codec differs')
        binary, metadata, startup = inputs[:3]
        records, _ = card.base.extract(binary, metadata, startup=startup, structures=True)
        cells = card.base.type_cells(binary, metadata, startup, records)
        expected = next(t for t in mapping['native']['parsedTables'] if t['name'] == 'AvatarSkillDepot')
        approved = mapping['native']['nativeSources']
        pe = card.base.PE(binary)
        for entry, proof in approved.items():
            start, end = int(entry, 16), int(proof['end'], 16)
            raw = binary[pe.offset(start):pe.offset(start) + end - start]
            if hashlib.sha256(raw).hexdigest() != proof['sha256']:
                raise ValueError('Approved native bytes differ ' + entry)

        class GuardedRuntime(card.NaturalRuntime):
            def bind_function(self, address):
                if address not in self.e.models:
                    proof = approved.get(hex(address))
                    if proof is None:
                        raise ValueError('Unapproved native entry ' + hex(address))
                    end = self.extents.get(address) or self.e.ends.get(address)
                    if end != int(proof['end'], 16):
                        raise ValueError('Native extent differs ' + hex(address))
                    raw = self.binary[self.e.pe.offset(address):self.e.pe.offset(address) + end - address]
                    if hashlib.sha256(raw).hexdigest() != proof['sha256']:
                        raise ValueError('Native bytes differ ' + hex(address))
                return super().bind_function(address)

            def call(self, entry, stops=(), **registers):
                trace = super().call(entry, stops, **registers)
                if entry == DECODE and self.collect_getters:
                    if trace['stop'] != hex(RETURN):
                        raise ValueError('Getter did not return')
                    self.getters.append(dict(pointer=hex(registers['rcx']), value=self.e.reg('eax'), trace=trace))
                return trace

            def snapshot_value(self, pointer, typ, depth):
                if self.collect_getters and typ['kind'] == 'class':
                    target = self.e.read(pointer)
                    if target and self.objects.get(target) != typ['typeDefinition']:
                        raise ValueError('Nested parser object type differs')
                return super().snapshot_value(pointer, typ, depth)

        stage = 'runtime-init'
        GuardedRuntime.collect_getters = False
        GuardedRuntime.getters = []
        rt = GuardedRuntime(binary, metadata, startup, records, cells, ROOT / 'local/migration72/python',
                            'AvatarSkillDepot', codec=codec, capacity=171)
        ranges = [(int(k, 16), int(v['end'], 16)) for k, v in approved.items()]
        stops = {RETURN, 0x1404c2be0, 0x1404c2650, 0x1404c2c70, 0x1404c2770, 0x1404bbd90}
        def guard_instruction(uc, address, size, user_data):
            if address not in rt.e.models and address not in stops and not any(a <= address < b for a, b in ranges):
                rt.e.failure = 'Unapproved instruction ' + hex(address)
                uc.emu_stop()
        rt.e.uc.hook_add(rt.e.U.UC_HOOK_CODE, guard_instruction)
        stage = 'whole'
        table = rt.parse_table(payload)
        check_rows(table['rows'], expected['rows'])
        if table['cursor'] != 26024 or table['count'] != 171:
            raise ValueError('Whole cursor/count differs')
        fields = {f['index']: f for f in records[22284]['fields']}
        for index, offset in FIELDS:
            if fields[index]['offset'] != offset:
                raise ValueError('Field metadata offset differs')
        for owner, layout in ((40302, ((198735, 0x10), (198736, 0x18), (198737, 0x1c))),
                              (81127, ((404853, 0x10), (404854, 0x14)))):
            if [(f['index'], f['offset']) for f in records[owner]['fields']] != list(layout):
                raise ValueError('Nested field schema differs')
        if ([fields[i]['type']['elementType'].get('typeDefinition') for i in (105354, 105355, 105357, 105358)]
                != [40302, 81127, 58209, 58209]
                or [fields[i]['type'].get('typeDefinition') for i in (105363, 105369)] != [58209, 58209]
                or [f['type'].get('typeDefinition') for f in records[40302]['fields'][1:]] != [85159, 58209]
                or records[40302]['fields'][0]['type']['elementType'].get('typeDefinition') != 58209
                or [f['type'].get('typeDefinition') for f in records[81127]['fields']] != [58209, 58209]):
            raise ValueError('Field metadata types differ')
        rows = {row['key']: row for row in table['rows']}
        output_samples = []
        for sample in samples:
            key = sample['currentDepotId']
            stage = 'query-' + str(key)
            row = rows[key]
            if row['source'] != sample['depotSource']:
                raise ValueError('Sample source differs from same-runtime row')
            query = rt.call(0x14d6d70f0, rcx=key)
            pointer = rt.e.reg('rax')
            if query['stop'] != hex(RETURN) or pointer != int(row['pointer'], 16) or rt.objects.get(pointer) != 22284:
                raise ValueError('Native query pointer/type differs')
            values = []
            for index, offset in FIELDS:
                stage = 'field-' + str(key) + '-' + str(index)
                field = fields[index]
                rt.getters = []
                rt.collect_getters = True
                try:
                    value = rt.snapshot_value(pointer + offset, field['type'], 1)
                finally:
                    rt.collect_getters = False
                if 'unresolved' in json.dumps(value):
                    raise ValueError('Unresolved native snapshot')
                mark_presence(value)
                values.append(dict(owner=22284, fieldIndex=index, field=field['name'], offset=hex(offset),
                                   typeIndex=field['typeIndex'], type=field['type'], value=value, getters=rt.getters,
                                   inputPresence='not observed; object state does not distinguish absent input from defaults'))
            if next(f['value'] for f in values if f['fieldIndex'] == 105363) != key:
                raise ValueError('Decoded id differs from query key')
            output_samples.append(dict(avatarId=sample['avatarId'], key=key, source=row['source'],
                rowPointer=row['pointer'], queryRAX=hex(pointer), query=query, fields=values,
))
        old_by_key = {sample['key']: sample for sample in old_result['samples']}
        current_by_key = {sample['key']: sample for sample in output_samples}
        def without_presence(value):
            if isinstance(value, list):
                return [without_presence(x) for x in value]
            if isinstance(value, dict):
                return {k: without_presence(v) for k, v in value.items() if k != 'inputPresence'}
            return value
        for key, previous in old_by_key.items():
            current = current_by_key[key]
            if current['source'] != previous['source'] or any(
                    without_presence(a['value']) != without_presence(b['value'])
                    for a, b in zip(current['fields'], previous['fields'])):
                raise ValueError('Prior active typed values differ')
        result = dict(task='E-01/R3-ALL-SAVED-DEPOT-REFS', inputSHA256=FIVE_INPUTS,
            fixedPins=PINS, inheritedPins=mapping['sourcePins'], auditSHA256=AUDIT_SHA,
            coveragePins=coverage_tool.PINS, auditInherited20=audit['inherited20PinsRechecked'],
            auditNewPins=audit['newPins'], coverageSHA256=COVERAGE_SHA, oldDriverSHA256=OLD_DRIVER_SHA,
            coverageDriverSHA256=COVERAGE_DRIVER_SHA, oldResultSHA256=OLD_RESULT_SHA,
            originalExportSHA256={name: entry['sha256'] for name, entry in manifest.items()},
            sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), codec=codec['proof'],
            codeManifest=rt.functions, approvedCodeManifest=approved,
            models=dict(modelEntrypoints={hex(k): v.__name__ for k,v in rt.e.models.items()},
                allocations=rt.allocations, metadataGroups=rt.metadata_groups, loaderUsages=rt.loader_usages,
                stringConversions=rt.string_conversions,
                typedClasses=[dict(pointer=hex(k), type=v) for k,v in rt.type_records.items()],
                strings={hex(k): v for k,v in rt.strings.items()}, scalarStorage='fixed natural_codec',
                limits=dict(perCallInstructions=40000000, perCallSeconds=60, array=4096, depth=5)),
            whole=dict(cursor=table['cursor'], count=table['count'], trace=table['trace'],
                       rows=[{k: row[k] for k in ('key', 'pointer', 'source')} for row in table['rows']],
                       existingSelectedQueryCount=len(table['selected'])),
            samples=output_samples, positiveValuesByFieldIndex=positive_fields(output_samples),
            inherentProjectionApproved=False, promoteThresholdApproved=False, gTimes100PlusLevelProven=False,
            specialQuestProjectionApproved=False, resourceReady=False, completeAvatar=False, d02Complete=False)
        raw = (json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf8')
        with output.open('xb') as f:
            f.write(raw)
        print(json.dumps(dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(), samples=len(output_samples))))
    except Exception as error:
        detail = dict(stage=stage, error=str(error))
        if rt is not None:
            detail.update(nativeFailure=rt.e.failure, registers={n: hex(rt.e.reg(n)) for n in ('rip','rcx','rdx','r8','rax','rsp')},
                          callOrder=rt.call_order, nativeSources=rt.functions)
        with failure.open('x', encoding='utf8') as f:
            json.dump(detail, f, ensure_ascii=False, indent=2)
        raise
    finally:
        if rt is not None:
            rt.e.close()


if __name__ == '__main__':
    import sys
    sys.path.insert(0, str(ROOT))
    main()
