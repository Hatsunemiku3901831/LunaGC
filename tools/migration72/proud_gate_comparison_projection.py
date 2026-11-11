"""Project fixed observed Depot81127 leaves without producing production resources."""
import argparse
import collections
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'local/migration72/d02-resume-20261111b/proud-gate'
OUT = BASE / 'projection'
CONTRACT = BASE / 'projection-input-contract.private.json'
CONTRACT_SHA = '81e71c2f8cc2e0b16f88ef03418abe9ef1a660bdf2779bcae8103ed41ba10fd4'
PLAN = ROOT / 'docs/CLIENT_72_D02_PROUD_GATE_COMPARISON_PROJECTION_PLAN_20261111B.md'
PLAN_SHA = '83797a238eebef509ce55624886ba8550374aa03451a72ac6d63bbff6c4d9896'
ALL = 'local/migration72/d02-resume-20261110b/c-all-depot-refs/main-third.private.json'
ACTIVE = 'local/migration72/d02-resume-20261110b/c-depot-fields/main-third.private.json'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def checked(path, size, digest):
    raw = path.read_bytes()
    require(len(raw) == size and hashlib.sha256(raw).hexdigest() == digest, 'source pin mismatch: ' + str(path))
    return raw


def load_sources():
    contract = json.loads(checked(CONTRACT, 13035, CONTRACT_SHA))
    checked(PLAN, 10652, PLAN_SHA)
    source_data = {name: checked(ROOT / name, info['bytes'], info['sha256']) for name, info in contract['sources'].items()}
    return contract, json.loads(source_data[ALL]), json.loads(source_data[ACTIVE])


def snapshot(sample, report_name, sample_index):
    """Validate one original object snapshot and preserve its two observed leaves."""
    where = f'{report_name}/samples/{sample_index}'
    key, avatar, source = sample['key'], sample['avatarId'], sample['source']
    require(type(key) is int and type(avatar) is int and isinstance(source, dict) and all(k in source for k in ('begin','end','sha256')), where + ': bad identity/source')
    fields = [(i, f) for i, f in enumerate(sample['fields']) if f['fieldIndex'] == 105355]
    require(len(fields) == 1, where + ': expected one field105355')
    field_pos, field = fields[0]
    typ = field['type']
    require(field['owner'] == 22284 and field['offset'] == '0x18' and typ['kind'] == 'szarray' and typ['elementType']['kind'] == 'class' and typ['elementType']['typeDefinition'] == 81127, where + ': field type differs')
    items, getters = field['value'], field['getters']
    require(isinstance(items, list) and len(items) == 5 and isinstance(getters, list) and len(getters) == 10, where + ': expected five elements and ten getters')
    raw_slots, java_slots = [], []
    for index, item in enumerate(items):
        require(isinstance(item, list) and len(item) == 2, where + f': bad element {index}')
        by_index = {leaf['fieldIndex']: leaf for leaf in item}
        require(set(by_index) == {404853,404854} and len(by_index) == 2, where + f': wrong leaf indices at {index}')
        raw = {}
        for leaf_index, offset, getter_index, name in ((404853,'0x10',index*2,'requiredPhase'),(404854,'0x14',index*2+1,'group')):
            leaf = by_index[leaf_index]
            require(leaf['owner'] == 81127 and leaf['offset'] == offset and leaf['type']['kind'] == 'valuetype' and leaf['type']['typeDefinition'] == 58209, where + f': typed leaf mismatch at {index}/{leaf_index}')
            value = leaf['value']
            require(type(value) is int and 0 <= value <= 0xffffffff, where + f': invalid U32 at {index}/{leaf_index}: {value!r}')
            require(value <= 0x7fffffff, where + f': Java int cannot preserve U32 at {index}/{leaf_index}: {value}')
            getter = getters[getter_index]
            require(getter['value'] == value and getter['trace']['entry'] == '0x14aef9db0', where + f': getter mismatch at {index}/{leaf_index}')
            raw[name] = {'fieldIndex': leaf_index, 'offset': offset, 'rawU32': value, 'inputPresence': leaf.get('inputPresence','unknown (not specified by report)'), 'leafReportPath': f'{where}/fields/{field_pos}/value/{index}/{item.index(leaf)}', 'getterReportPath': f'{where}/fields/{field_pos}/getters/{getter_index}', 'getterEntry': getter['trace']['entry'], 'getterPathSHA256': getter['trace']['nativePathSHA256']}
        raw_slots.append({'index': index, **raw})
        java_slots.append({'proudSkillGroupId': raw['group']['rawU32'], 'needAvatarPromoteLevel': raw['requiredPhase']['rawU32']})
    return {'source': {'avatarId':avatar, 'key':key, 'row':source, 'reportPath':where, 'fieldIndex':105355, 'fieldOffset':'0x18', 'fieldInputPresence':field.get('inputPresence','unknown (not specified by report)'), 'fieldReportPath':f'{where}/fields/{field_pos}', 'queryReportPath':where+'/query', 'queryEntry':sample['query']['entry']}, 'rawSlots':raw_slots, 'javaFields':{'id':key,'inherentProudSkillOpens':java_slots}}


def project(contract, all_report, active_report):
    all_samples, active_samples = all_report['samples'], active_report['samples']
    require(len(all_samples) == 121 and len(active_samples) == 4, 'sample counts differ')
    rows = [snapshot(row, ALL, i) for i,row in enumerate(all_samples)]
    keys = [r['source']['key'] for r in rows]
    require(keys == contract['selection']['keyOrder'] and len(set(keys)) == 121 and len({r['source']['avatarId'] for r in rows}) == 121, 'ordered keys/avatar identity differ')
    by_key = {row['source']['key']: row for row in rows}
    joins = []
    for i, sample in enumerate(active_samples):
        active = snapshot(sample, ACTIVE, i)
        source = active['source']; original = by_key[source['key']]
        require(source['avatarId'] == original['source']['avatarId'] and source['row'] == original['source']['row'], f'active identity/source mismatch: {source["key"]}')
        require(active['javaFields'] == original['javaFields'], f'active leaves mismatch: {source["key"]}')
        pairs = [[x['requiredPhase']['rawU32'], x['group']['rawU32']] for x in active['rawSlots']]
        joins.append({'avatarId':source['avatarId'],'key':source['key'],'source':source['row'],'allReportPath':original['source']['reportPath'],'activeReportPath':source['reportPath'],'rawThresholdGroupSlots':pairs,'activeRawSlots':active['rawSlots']})
    expected = contract['fourNativeSnapshotCrossJoin']
    require([(j['avatarId'],j['key'],j['source'],j['rawThresholdGroupSlots']) for j in joins] == [(j['avatarId'],j['key'],j['source'],j['rawThresholdGroupSlots']) for j in expected], 'active frozen cross join differs')
    pairs = [slot for row in rows for slot in row['javaFields']['inherentProudSkillOpens']]
    phases = collections.Counter(x['needAvatarPromoteLevel'] for x in pairs)
    require(len(pairs) == 605 and phases == {0:363,1:121,4:121} and sum(x['proudSkillGroupId']==0 for x in pairs) == 215, 'fixed aggregate differs')
    flags = contract['outputSchema']['acceptanceInitiallyFalse'] + contract['outputSchema']['acceptanceRemainFalse']
    return {'sources': {'contractSHA256':CONTRACT_SHA, 'planSHA256':PLAN_SHA, 'fixed':contract['sources']}, 'scope': {'rows':121,'slots':605,'leaves':1210,'observedObjectValuesOnly':True,'inputPresence':'unknown','nativeOrLiveExecution':0,'unrelatedJavaFields':False}, 'rows':rows,'fourSampleJoin':joins,'acceptance':{key:False for key in flags}}


def save(path, value):
    with path.open('x', encoding='utf8', newline='\n') as f:
        json.dump(value, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='new basename under private projection directory')
    args=parser.parse_args()
    require(Path(args.output).name == args.output and not (OUT / args.output).exists(), 'new output basename required')
    dest=OUT / args.output
    dest.mkdir()
    stage='fixed-sources'
    try:
        contract, all_report, active_report = load_sources()
        stage='projection'
        report=project(contract, all_report, active_report)
        save(dest / 'projection.private.json', report)
        data=(dest / 'projection.private.json').read_bytes()
        print(json.dumps({'rows':len(report['rows']),'slots':report['scope']['slots'],'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}))
    except Exception as exc:
        save(dest / 'failure.private.json', {'stage':stage,'errorType':type(exc).__name__,'error':str(exc)})
        raise


if __name__ == '__main__':
    main()
