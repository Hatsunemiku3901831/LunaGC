"""Audit two frozen Proud windows and their typed source contract; no current PE or execution."""
import argparse
import hashlib
import io
import json
import pickle
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASE = 'local/migration72/d02-resume-20261110b/'
OUT = ROOT / BASE / 'c-proud-factory-index-contract'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG = 'local/migration72/evidence/client72-signatures.json'
FROZEN_SHA = 'e49509edc2d4ef89c4fbda496b5ffcce4a376020a87e9053118950049f4b8dbb'
FILES = ('boundary.private.json', 'cell.bin', 'code.asm.private.json', 'code.bin',
         'extraction.private.json', 'pdata.bin', 'pre-body.private.json', 'slot.bin')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='New task-local JSON basename')
    args = parser.parse_args()
    target = OUT / args.output
    if target.name != args.output or target.suffix != '.json' or target.exists():
        parser.error('output must be a new task-local JSON basename')
    result = audit()
    raw = encode(result)
    with target.open('xb') as stream:
        stream.write(raw)
    print(json.dumps(dict(bytes=len(raw), sha256=digest(raw), checks=len(result['checks']),
                         directStore400190Proven=True, indexContentLoadedProven=False, parserExecuted=False)))


def encode(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf8')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def require(value, message):
    if not value:
        raise ValueError(message)


class PureData(pickle.Unpickler):
    def find_class(self, module, name):
        raise ValueError('non-data metadata pickle')


def fixed_sources(read=None):
    read = read or (lambda path: path.read_bytes())
    raw = read(OUT / 'frozen-inputs.private.json')
    require(digest(raw) == FROZEN_SHA, 'frozen source manifest drift')
    manifest = json.loads(raw)
    fixed = {}
    for name, pin in manifest['sources'].items():
        raw = read(ROOT / name)
        require(len(raw) == pin['bytes'] and digest(raw) == pin['sha256'], 'fixed source drift: ' + name)
        fixed[name] = raw
    return manifest, fixed


def shape(value):
    return [value['kind'], value.get('typeDefinition'), value.get('instanceIndex'),
            [shape(argument) for argument in value.get('arguments', [])],
            shape(value['elementType']) if 'elementType' in value else None]


def typed_sources(records, cells, signatures):
    fields = {field['index']: field for field in records[80063]['fields']}
    expected = [(0x1458B5F28, 400190, 5071), (0x1458EA790, 400189, 50459),
                (0x1458EA7A0, 400193, 50463)]
    for address, index, instance in expected:
        value = cells[address]['type']
        require(value['typeDefinition'] == 18192 and value['instanceIndex'] == instance and
                shape(value) == shape(fields[index]['type']), 'requested closed type/field tuple differs')
    require(cells[0x145900658]['type']['typeDefinition'] == 41105 and
            records[41105]['parent']['typeDefinition'] == 26072 and
            cells[0x1458EA798]['type']['typeDefinition'] == 80064 and
            records[80064]['parent']['typeDefinition'] == 371, 'factory/comparer type provenance differs')
    by_index = {method['index']: method for method in signatures['methods']}
    ctor = by_index[138518]
    parameter_types = [signatures['typeReferences'][str(p['typeIndex'])] for p in ctor['parameters']]
    require(ctor['declaringType'] == 18192 and [t['kind'] for t in parameter_types] == ['string', 'genericinst'] and
            parameter_types[1]['typeDefinition'] == 1249 and
            parameter_types[1]['arguments'][0]['genericParameterPosition'] == 0,
            'generic constructor String/IEqualityComparer declaration differs')
    wrapper = by_index[279587]
    require(wrapper['registeredAddress'] == '0x14e2a5d60' and wrapper['declaringType'] == 33459 and
            len(wrapper['parameters']) == 1 and
            signatures['typeReferences'][str(wrapper['parameters'][0]['typeIndex'])]['kind'] == 'object',
            'original dispatch wrapper declaration differs')
    return dict(indexFields={str(i): fields[i] for i in range(400189, 400194)},
                selectedCells={hex(a): cells[a] for a in [0x145900658, 0x1458B5F28, 0x1458EA790,
                                                        0x1458EA798, 0x1458EA7A0]},
                genericConstructor=dict(declaration=ctor, parameterTypes=parameter_types,
                                        declaredFields=records[18192]['fields'], calledByCctor=False,
                                        exactClosedFieldOffsetsRecovered=False),
                comparerCandidate80064=dict(parent=records[80064]['parent'], methods=records[80064]['methods'],
                                            delegateType=False, interfaceImplementationRecordRecovered=False),
                dispatchWrapper=wrapper)


def anchors(lines, expected, label):
    indexed = {int(line['address'], 16): line for line in lines}
    for address, wanted in expected.items():
        line = indexed.get(address)
        require(line is not None and (line['mnemonic'], line['operands']) == wanted,
                label + ' original instruction differs: ' + hex(address))
    return {hex(address): indexed[address] for address in expected}


def factory_contract(lines):
    selected = anchors(lines, {
        0x149F0BB45: ('mov', 'rsi, rcx'),
        0x149F0BB48: ('cmp', 'byte ptr [rip - 0x43743bb], 0'),
        0x149F0BB4F: ('je', '0x149f0bb6b'),
        0x149F0BB51: ('cmp', 'byte ptr [rip - 0x46d40bc], 0'),
        0x149F0BB58: ('jne', '0x149f0bb85'),
        0x149F0BB5A: ('mov', 'rcx, qword ptr [rip - 0x460b509]'),
        0x149F0BB61: ('add', 'rsp, 0x20'), 0x149F0BB65: ('pop', 'rsi'),
        0x149F0BB66: ('jmp', '0x1405336c0'),
        0x149F0BB6B: ('mov', 'ecx, 0x1e0b4'),
        0x149F0BB70: ('call', '0x14052d0f0'),
        0x149F0BB75: ('mov', 'byte ptr [rip - 0x43743e8], 1'),
        0x149F0BB7C: ('cmp', 'byte ptr [rip - 0x46d40e7], 0'),
        0x149F0BB83: ('je', '0x149f0bb5a'),
        0x149F0BB85: ('mov', 'rax, qword ptr [rip - 0x46a058c]'),
        0x149F0BB8C: ('mov', 'rax, qword ptr [rax + 0x232a0]'),
        0x149F0BB93: ('mov', 'rcx, qword ptr [rax + 0x409a80]'),
        0x149F0BB9D: ('je', '0x149f0bbac'),
        0x149F0BB9F: ('mov', 'rdx, rsi'),
        0x149F0BBA7: ('jmp', '0x14e2a5d60'),
        0x149F0BBAC: ('call', '0x1404c2be0'),
    }, 'factory')
    require(not any(line['mnemonic'] == 'ret' for line in lines), 'factory has no local RET')
    return dict(anchors=selected, usageGuard='0x145b97794', usageGroup='0x1e0b4',
        usageInit='guard0 -> original14052d0f0(group) -> guard1; real initialized cell remains prerequisite',
        dispatchFlag='0x145837a9c',
        ordinaryTail=dict(site='0x149f0bb66', target='0x1405336c0', requestedClassCell='0x145900658',
                          requestedType=41105, restoresOriginalStack=True, ownRetCount=0,
                          ownObjectStores=0, explicit41105ConstructorCalls=0, returnObserved=False),
        replacement=dict(rootCell='0x14586b600', wrapperArrayOffset='0x232a0', rawElementOffset='0x409a80',
                         wrapperEntry='0x14e2a5d60', wrapperMethod=279587, receiver='wrapper element',
                         parameterRDX='original this', nullEdge='0x149f0bbac -> 0x1404c2be0'),
        resultSource='if downstream tail target returns normally, original RAX reaches base14d5e2540 store+20',
        allocationSucceededProven=False, returnTyped41105ForActualObjectProven=False,
        initializationSucceededProven=False, naturalDispatchObserved=False)


def cctor_contract(lines):
    selected = anchors(lines, {
        0x14D7ADBC6: ('mov', 'rsi, qword ptr [rip - 0x7c84475]'),
        0x14D7ADBCD: ('mov', 'rcx, qword ptr [rip - 0x7ef7cac]'),
        0x14D7ADBD4: ('call', '0x1405336c0'),
        0x14D7ADBDC: ('je', '0x14d7adc8d'),
        0x14D7ADBE2: ('mov', 'qword ptr [rax + 0x20], rsi'),
        0x14D7ADBE6: ('mov', 'qword ptr [rax + 0x10], 0'),
        0x14D7ADBF5: ('mov', 'qword ptr [rcx + 0x21f58], rax'),
        0x14D7ADBFC: ('movzx', 'eax, byte ptr [rip - 0x7f40ba5]'),
        0x14D7ADC03: ('mov', 'byte ptr [rip - 0x7f31949], al'),
        0x14D7ADC09: ('mov', 'byte ptr [rip - 0x7f3194e], 0'),
        0x14D7ADC10: ('mov', 'rsi, qword ptr [rip - 0x7c844b7]'),
        0x14D7ADC17: ('mov', 'rcx, qword ptr [rip - 0x7ec348e]'),
        0x14D7ADC1E: ('call', '0x1405336c0'),
        0x14D7ADC26: ('je', '0x14d7adc92'),
        0x14D7ADC28: ('mov', 'qword ptr [rax + 0x20], rsi'),
        0x14D7ADC2C: ('mov', 'qword ptr [rax + 0x10], 0'),
        0x14D7ADC3B: ('mov', 'qword ptr [rcx + 0x21f50], rax'),
        0x14D7ADC42: ('mov', 'rdi, qword ptr [rip - 0x7c84519]'),
        0x14D7ADC49: ('mov', 'rcx, qword ptr [rip - 0x7ec34b8]'),
        0x14D7ADC50: ('call', '0x1405336c0'),
        0x14D7ADC55: ('mov', 'rsi, rax'),
        0x14D7ADC58: ('mov', 'rcx, qword ptr [rip - 0x7ec34bf]'),
        0x14D7ADC5F: ('call', '0x1405336c0'),
        0x14D7ADC67: ('je', '0x14d7adc97'),
        0x14D7ADC69: ('mov', 'qword ptr [rax + 0x20], rdi'),
        0x14D7ADC6D: ('mov', 'qword ptr [rax + 0x10], rsi'),
        0x14D7ADC78: ('mov', 'qword ptr [rcx + 0x21f70], rax'),
        0x14D7ADC7F: ('mov', 'byte ptr [rip - 0x7f319c5], 0'),
        0x14D7ADC8C: ('ret', ''),
        0x14D7ADC8D: ('call', '0x1404c2be0'),
        0x14D7ADC92: ('call', '0x1404c2be0'),
        0x14D7ADC97: ('call', '0x1404c2be0'),
    }, 'cctor')
    calls = [line['operands'] for line in lines if line['mnemonic'] == 'call']
    require(calls == ['0x1405336c0'] * 4 + ['0x1404c2be0'] * 3, 'cctor direct-only call set differs')
    global_stores = [line['operands'] for line in lines if line['mnemonic'] == 'mov' and
                     line['operands'].startswith('qword ptr [rcx + ')]
    require(global_stores == ['qword ptr [rcx + 0x21f58], rax', 'qword ptr [rcx + 0x21f50], rax',
                             'qword ptr [rcx + 0x21f70], rax'], 'cctor actual direct field stores differ')
    return dict(anchors=selected, directStoreFields=[400190, 400189, 400193],
        noDirectStoreFields=[400191, 400192],
        stores=[dict(site='0x14d7adbf5', field=400190, globalOffset='0x21f58', requestedClassCell='0x1458b5f28',
                     classInstance=5071, tuple=['U32', 75077, 47647, 25030],
                     objectPlus20SourceCell='0x145b29758', objectPlus10='literal zero'),
                dict(site='0x14d7adc3b', field=400189, globalOffset='0x21f50', requestedClassCell='0x1458ea790',
                     classInstance=50459, tuple=['U32', 'U32[]', 47647, 47647],
                     objectPlus20SourceCell='0x145b29760', objectPlus10='literal zero'),
                dict(site='0x14d7adc78', field=400193, globalOffset='0x21f70', requestedClassCell='0x1458ea7a0',
                     classInstance=50463, tuple=[80067, 'U32', 80065, 47647],
                     objectPlus20SourceCell='0x145b29730', objectPlus10='RAX returned for class80064 at14d7adc50')],
        flags=[dict(site='0x14d7adc03', destination='0x14587c2c0', value='byte from0x14586d05e'),
               dict(site='0x14d7adc09', destination='0x14587c2c2', value=0),
               dict(site='0x14d7adc7f', destination='0x14587c2c1', value=0)],
        comparerNullCheck='class80064 allocation return is not checked here before storing+10',
        noLocalPlus18Store=True, noLocalPlus28Store=True, noDirectLoaderOrDelegateCall=True,
        constructorInliningInterpretation='consistent with String/IEqualityComparer declaration; exact generic field offsets unbound',
        indexContentLoadedProven=False, emptyDictionaryStateObserved=False,
        loaderEntryProven=False, staticConstructorExecuted=False,
        errorBoundary='three null checks call original1404c2be0; no error-return0/success conversion or EH evidence')


def audit():
    manifest, fixed = fixed_sources()
    sys.path.insert(0, str(ROOT))
    from tools import client72_d02_proud_factory_window as factory
    from tools import client72_d02_proud_index_ctor_window as ctor
    checks, windows = [], {}
    def check(name, value):
        require(value, name)
        checks.append(name)
    for part, runs, validator in [('c-proud-factory-window', ('factory1', 'factory2'), factory),
                                  ('c-proud-index-ctor-window', ('ctor1', 'ctor2'), ctor)]:
        prefix = BASE + part + '/'
        inherited = json.loads(fixed[prefix + 'frozen-inputs.private.json'])
        check(part + '-inherited-full-source-manifest', all(manifest['sources'][p] == pin
                                                           for p, pin in inherited['sources'].items()))
        copies = [{name: fixed[prefix + run + '/' + name] for name in FILES} for run in runs]
        check(part + '-two-fresh-eight-files-equal', copies[0] == copies[1])
        extraction = validator.validate_window(copies[0])
        check(part + '-complete-original-aot-pdata-decode', extraction['passed'])
        third = json.loads(fixed[prefix + 'main-third.private.json'])
        check(part + '-third-equals-both-audits', fixed[prefix + 'main-third.private.json'] ==
              fixed[prefix + 'final1.private.json'] == fixed[prefix + 'final2.private.json'])
        check(part + '-root-third-full-artifacts-and-extraction', third['extraction'] == extraction and
              third['artifacts'] == {n: dict(bytes=len(raw), sha256=digest(raw)) for n, raw in copies[0].items()})
        check(part + '-same-five-full-hashes', extraction['inputChecks'] ==
              {n: dict(bytes=size, sha256=sha) for n, (size, sha) in validator.FIVE.items()})
        windows[part] = dict(entry=extraction['entry'], endExclusive=extraction['endExclusive'],
            codeBytes=extraction['codeBytes'], instructions=extraction['instructions'],
            codeSHA256=extraction['codeSHA256'], pdataHex=extraction['pdataHex'],
            pdataSHA256=extraction['pdataSHA256'], aot=extraction['aot'], artifacts=third['artifacts'])
    records, cells = PureData(io.BytesIO(fixed[META])).load()
    signatures = json.loads(fixed[SIG])
    typed = typed_sources(records, cells, signatures)
    check('three-exact-closed-types-and-factory41105', bool(typed))
    f = factory_contract(json.loads(fixed[BASE + 'c-proud-factory-window/factory1/code.asm.private.json']))
    c = cctor_contract(json.loads(fixed[BASE + 'c-proud-index-ctor-window/ctor1/code.asm.private.json']))
    check('factory-usage-dispatch-and-ordinary-tail-contract', len(f['anchors']) == 21)
    check('cctor-actual-three-stores-and-no-loader-or-delegate', len(c['anchors']) == 32)
    base = anchors(json.loads(fixed[BASE + 'c-proud-parser-contract/base-constructor1/code.asm.private.json']), {
        0x14D5E2532: ('mov', 'rax, qword ptr [rsi]'), 0x14D5E2535: ('mov', 'rcx, rsi'),
        0x14D5E2538: ('xor', 'edx, edx'), 0x14D5E253A: ('call', 'qword ptr [rax + 0x118]'),
        0x14D5E2540: ('mov', 'qword ptr [rsi + 0x20], rax')}, 'base ctor')
    check('original-indirect-call-result-is-plus20-source', len(base) == 5)
    derived = anchors(json.loads(fixed[BASE + 'c-proud-parser-contract/constructor1/code.asm.private.json']), {
        0x149F0E5AC: ('mov', 'rax, qword ptr [rdi + 0x20]'),
        0x149F0E5B3: ('je', '0x149f0e5cf'),
        0x149F0E5B5: ('mov', 'rdx, qword ptr [rip - 0x460df64]'),
        0x149F0E5D1: ('mov', 'qword ptr [rdi + 0x40], rcx')}, 'derived ctor')
    check('derived-plus40-keeps-type-check-and-null-path', len(derived) == 4)
    wrapper_field = next(field for field in records[33459]['fields'] if field['index'] == 164202)
    check('replacement-array-typed-source', wrapper_field['rawOffset'] == '0x40232a0' and
          wrapper_field['type']['elementType']['typeDefinition'] == 33459)
    check('closed-aot-old-two-fresh-reports-equal', fixed[BASE + 'c-proud-actual-fields/closed-aot-final1.private.json'] ==
          fixed[BASE + 'c-proud-actual-fields/closed-aot-final2.private.json'])
    old_aot = json.loads(fixed[BASE + 'c-proud-actual-fields/closed-aot-final1.private.json'])
    check('old-other-tuple-does-not-bind5071', not any(row['owner'] == 18192 and row['classInstance'] == 5071
                                                    for row in old_aot['selected']))
    inherited = {key: json.loads(fixed[BASE + path]) for key, path in [
        ('mode', 'c-proud-mode-factory/main-third.json'),
        ('row', 'c-proud-row-reader/main-third.private.json'),
        ('parser', 'c-proud-parser-contract/main-third.json')]}
    check('row-span-and-two-u32-alias-remain-unproved', not inherited['row']['actualRowSpanApproved'] and
          not inherited['row']['parameterPairUniversalOffsetSizeAliasApproved'])
    check('mode-and-parser-not-executed', not inherited['mode']['naturalModeObserved'] and
          not inherited['parser']['parserExecuted'] and not inherited['parser']['childConditionalPermissionConsumed'])
    literal = json.loads(fixed[BASE + 'c-proud-row-source/reader-literal1.private.json'])
    check('old-whole-path-literal-not-two-new-cell-values', literal['cell'] == '0x145b29788' and
          literal['decoded']['value'] == 'Data/_ExcelBinOutput/ProudSkillExcelConfigData')
    allocator = json.loads(fixed['local/migration72/d02-parallel-20261109/r1-allocator-successor/allocator-full1.private.json'])
    check('existing-allocator-identity-only-no-new-body', allocator['start'] == '0x1405336c0' and
          digest(bytes.fromhex(allocator['bytesHex'])) == allocator['nativeSHA256'])
    return dict(task='E-01/R3-PROUD-FACTORY-INDEX-CONTRACT', passed=True,
        sourceSHA256=digest(Path(__file__).read_bytes()), frozenManifestSHA256=FROZEN_SHA,
        fixedSourceCount=len(fixed), originalWindows=windows, checks=checks, selectedDeclarations=typed,
        factory=f, indexCctor=c, originalBaseAnchors=base, originalDerivedAnchors=derived,
        representationContract=inherited['parser']['constructorContract'],
        modeContract=inherited['mode']['modeContract'],
        rowBackendContract=inherited['row']['branchContract'],
        readLedgerAdditions=[dict(source='0x145b97794', width=1, condition='factory entry usage guard'),
                            dict(source='0x145837a9c', width=1, condition='factory dispatch after usage ready'),
                            dict(source='0x145900658', width=8, condition='ordinary factory, must bind actual41105 class')],
        minimalPrerequisites=['proven ordinary mode/dispatch/class/usage context or separately labelled owned model',
            'allocator actual41105 class/GC/TLS/cache/normal-return context; never synthesize success or+20/+40',
            'exact two source literals and400190 index contents from its real loader, same key and actualU32 pair',
            'chosen reader backend actual bytes/effective bounds/cursor, distinct from whole2132778B payload',
            'root acceptance of real representation before any child14f6f13c0 permission'],
        minimumNextSource=dict(literalCells=['0x145b29758', '0x145b29760'], selectedRecordCipherMaxBytesEach=256,
            sourceIndexEqualsDestinationSlotAssumed=False, thirdLiteralCellUnresolved='0x145b29730',
            closedAot5071='separate c_proud_index_aot/root acceptance; no collection in this card',
            loaderDirectEdgeFromCctor=False, parserDriverReady=False),
        directStore400190Proven=True, directStore400191Proven=False, directStore400192Proven=False,
        indexContentLoadedProven=False, indexLoaderClosed=False, naturalModeObserved=False,
        naturalVtableObserved=False, factoryExecuted=False, managedRepresentationProvenForActualNewObject=False,
        actualRowSpanApproved=False, parserExecuted=False, child14f6f13c0Taken=False,
        proudSkillGroupIdAliasApproved=False, secondU32LevelIdentityApproved=False, promoteThresholdApproved=False,
        resourceReady=False, completeAvatar=False, d02Complete=False,
        newCodeWindows=0, newPEDataReads=0, nativeCPU=0, liveReads=0, newREA=0, network=0,
        targetProcessOperations=0, builds=0, database=0, gitOperations=0)


if __name__ == '__main__':
    main()
