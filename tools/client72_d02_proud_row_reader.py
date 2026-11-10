"""Pin-bound static Proud row-reader audit; no current PE, CPU or live access."""
import argparse
import hashlib
import json
from pathlib import Path
import pickle
import struct
import sys


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-row-reader'
ROW = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-row-source'
PAYLOAD = 'local/migration72/d02-resume-20261110b/c-proud-payload-locator/'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG = 'local/migration72/evidence/client72-signatures.json'
PINS = {
    META: 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    SIG: '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    'tools/client72_d02_proud_row_source.py': '355d59784ca99cdc075f0e4e09cc5914bd16408e306b8322efa29a1e5684b240',
    'local/migration72/d02-resume-20261110b/c-proud-row-reader/collect_row_reader.private.py': 'f39394d36b693b1f95f9fbc2686d7f46c9683262a4dd8128eeccaf2f106e128a',
    'local/migration72/d02-resume-20261110b/c-proud-row-source/reader-literal1.private.json': '9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9',
    'local/migration72/d02-resume-20261110b/c-proud-row-source/reader-literal2.private.json': '9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9',
    PAYLOAD + 'final3/result.private.json': 'e2513467fad5fb61a90e6aeec27c1ccf030b5b52fa75cb9a6874749190424839',
    PAYLOAD + 'final4/result.private.json': 'e2513467fad5fb61a90e6aeec27c1ccf030b5b52fa75cb9a6874749190424839',
    PAYLOAD + 'final3/payload.bin': '82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835',
    PAYLOAD + 'final4/payload.bin': '82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835',
}
READER_PINS = {
    'boundary.private.json': '95b4bb330aa673d3547b165c122a89781a2177814bbd4f13db0f45273d7eb2dd',
    'cell.bin': '68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da',
    'slot.bin': 'ac909ee30306875ef49583ce04ce6bd9890919461238f69401aed547de4ea139',
    'pdata.bin': '6d32d24174457e5e4b7a1bfdb0330dc377017bd801ac9a9f0e4961390423e5a2',
    'pre-body.private.json': '97940f5b6e9bfe4d882df0c6f587235d38cbef017cfe89463ed7c21809329129',
    'code.bin': '45f7d9818dbdef1bc86c8438926a359ed63a7cddead83d9a4c8d7954a6d7ae21',
    'code.asm.private.json': '21579200c4d958f990eeba4aaf2e6c313ac6596ff66adde962c0c56e17de711f',
    'extraction.private.json': '1b491512e5a10473619c4c4789cf2e31ba70591209948fecdb76682e08abb514',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='New JSON basename in the task private directory')
    args = parser.parse_args()
    target = OUT / args.output
    if (target.name != args.output or target.suffix != '.json' or target.exists()):
        parser.error('output must be a new private JSON basename')
    result = audit()
    raw = (json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf-8')
    with target.open('xb') as stream:
        stream.write(raw)
    print(json.dumps({'bytes': len(raw), 'sha256': digest(raw), 'assertions': len(result['checks']),
                      'rowReaderStaticContractApproved': True, 'rowSpanApproved': False}))


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def pinned(path, expected):
    raw = path.read_bytes()
    if digest(raw) != expected:
        raise ValueError('fixed-source-drift: ' + str(path.relative_to(ROOT)))
    return raw


def reader_window(files, validator):
    report, assembly = validator(files, 0x14BB5B0B0, 0x14BB5B64E, 309)
    if report['codeBytes'] > 8192 or report['instructions'] > 2048:
        raise ValueError('reader-approved-bound-exceeded')
    pre = json.loads(files['pre-body.private.json'])
    if pre['stage'] != 'pdata-before-body' or pre['newCodeWindowCount'] != 0:
        raise ValueError('boundary-not-recorded-before-body')
    return report, assembly


def audit():
    sources = {name: pinned(ROOT / name, expected) for name, expected in PINS.items()}
    sys.path.insert(0, str(ROOT))
    from tools import client72_d02_proud_row_source as prior
    reader_sets = [{name: pinned(OUT / folder / name, expected) for name, expected in READER_PINS.items()}
                   for folder in ('reader1', 'reader2')]
    producer_sets = [{name: pinned(ROW / folder / name, expected) for name, expected in prior.PRODUCER_PINS.items()}
                     for folder in ('typed-producer1', 'typed-producer2')]
    checks = {}

    def check(name, accepted):
        if not accepted:
            raise ValueError(name)
        checks[name] = True

    check('two-fresh-reader-eight-files-identical', reader_sets[0] == reader_sets[1])
    check('two-frozen-producer-eight-files-identical', producer_sets[0] == producer_sets[1])
    report, code = reader_window(reader_sets[0], prior.window)
    producer, caller = prior.window(producer_sets[0], 0x14D7AD8E0, 0x14D7ADB3E, 145)
    check('original-aot-slot-and-table',
          int(report['aot']['table'], 16) + 595516 * 8 == int(report['aot']['slot'], 16) and
          int.from_bytes(reader_sets[0]['slot.bin'], 'little') == 0x14BB5B0B0)
    check('original-caller-setup-and-e8',
          report['caller']['setupAndCallHex'] == '4889f989da4189f0e820d73afe' and
          report['caller']['sha256'] == prior.PRODUCER_PINS['code.bin'] and
          caller[0x14D7AD98B]['hex'] == 'e820d73afe' and
          0x14D7AD98B + 5 + struct.unpack('<i', bytes.fromhex('20d73afe'))[0] == 0x14BB5B0B0)
    profile = json.loads(sources['protocol/cn-7.2/profile.json'])['files']
    check('both-fresh-five-inputs-complete', all(
        json.loads(files['extraction.private.json'])['inputChecks'][item['path']]['sha256'] == item['sha256'] and
        json.loads(files['extraction.private.json'])['inputChecks'][item['path']]['bytes'] == item['size']
        for files in reader_sets for item in profile.values()))
    records = pickle.loads(sources[META])[0]
    # Only the selected declaring classes and exact direct-target declarations are inspected.
    selected_owners = {71907, 42005, 75077, 80063, 60257, 11307, 33459}
    by_owner = {r['index']: r for r in records if r['index'] in selected_owners}
    method = next(m for m in by_owner[71907]['methods'] if m['index'] == 595516)
    caller_method = next(m for m in by_owner[80063]['methods'] if m['index'] == 663546)
    cctor = next(m for m in by_owner[80063]['methods'] if m['name'] == '.cctor')
    check('exact-method-and-caller-identity',
          method['registeredAddress'] == '0x14bb5b0b0' and method['parameterCount'] == 3 and
          caller_method['registeredAddress'] == '0x14d7ad8e0')
    signatures = json.loads(sources[SIG])
    selected_methods = {595516, 663546, 500130, 500132, 80101, 264414}
    sigs = {m['index']: m for m in signatures['methods'] if m['index'] in selected_methods}
    types = [signatures['typeReferences'][str(p['typeIndex'])] for p in sigs[595516]['parameters']]
    check('reader-string-int32-int32-signature',
          [(t['kind'], t['byRef']) for t in types] == [('string', False), ('int32', False), ('int32', False)])
    fields = {f['index']: f for r in by_owner.values() for f in r['fields']}
    check('source-pair-remains-two-u32',
          [(fields[i]['offset'], fields[i]['type']['kind']) for i in (372773, 372774)] ==
          [(16, 'uint32'), (20, 'uint32')])
    check('handle-cache-is-string-to-u64', fields[358013]['rawOffset'] == '0x4014910' and
          [a['kind'] for a in fields[358013]['type']['arguments']] == ['string', 'uint64'])
    check('reader-cache-and-shared-reader-typed42005',
          fields[358014]['type']['arguments'][1]['typeDefinition'] == 42005 and
          fields[358014]['rawOffset'] == '0x4014918' and
          fields[358015]['type']['typeDefinition'] == 42005 and
          fields[358015]['rawOffset'] == '0x4014920')
    check('reader-field-storage-layout',
          [(fields[i]['offset'], fields[i]['type']['kind']) for i in (207760, 207761, 207762, 207763, 207764)] ==
          [(16, 'szarray'), (24, 'int32'), (28, 'int32'), (32, 'uint64'), (40, 'int32')] and
          fields[207760]['type']['elementType']['kind'] == 'uint8')
    index_type = fields[400190]['type']
    check('row-index-tuple-remains-unresolved', fields[400190]['rawOffset'] == '0x4021f58' and
          index_type['typeDefinition'] == 18192 and index_type['instanceIndex'] == 5071 and
          [a.get('typeDefinition', a['kind']) for a in index_type['arguments']] ==
          ['uint32', 75077, 47647, 25030])
    expected = {
        0x14BB5B0BA: '4489c7', 0x14BB5B0BD: '4189d6', 0x14BB5B0C0: '4889cb',
        0x14BB5B0C3: '803dc2cccef900', 0x14BB5B0CA: '0f85a4020000',
        0x14BB5B0D0: '803d871fd1f900', 0x14BB5B0D7: '0f849b010000',
        0x14BB5B0F3: '803d47bdd1f900', 0x14BB5B10F: '488bb010490100',
        0x14BB5B169: '488b74c130', 0x14BB5B16E: '85ff', 0x14BB5B170: '750b',
        0x14BB5B175: 'e8e6805cf5', 0x14BB5B17A: '4889c7',
        0x14BB5B198: '488b9820490100', 0x14BB5B1A8: '488b4310',
        0x14BB5B1B5: '3b7818', 0x14BB5B1B8: '7e5f',
        0x14BB5B1E0: '89c5', 0x14BB5B1E2: '8d442d00', 0x14BB5B1E6: '39fd',
        0x14BB5B1E8: '7cf6', 0x14BB5B207: 'e8d49c9bf4', 0x14BB5B215: '48894310',
        0x14BB5B244: 'c7401c00000000', 0x14BB5B24B: '488b5010',
        0x14BB5B24F: '4963c6', 0x14BB5B252: '4889442420',
        0x14BB5B257: '4889f1', 0x14BB5B25A: '4531c0', 0x14BB5B25D: '4189f9',
        0x14BB5B260: 'e82b7f5cf5', 0x14BB5B26C: '488ba820490100',
        0x14BB5B27F: '488b88605d0000', 0x14BB5B29C: 'ff90f0000000',
        0x14BB5B2AE: '48c7401000000000', 0x14BB5B2B6: 'c7401c00000000',
        0x14BB5B2BD: '4889c1', 0x14BB5B2C0: '4889da', 0x14BB5B2C3: '4589f0',
        0x14BB5B2C6: '4189f9', 0x14BB5B2C9: 'e8c2ddb901',
        0x14BB5B2E2: 'c744242000001000', 0x14BB5B2EF: '31d2',
        0x14BB5B2F1: '4531c0', 0x14BB5B2F4: '4531c9', 0x14BB5B2F7: 'e8f4dd35fb',
        0x14BB5B392: '4889da', 0x14BB5B395: '4589f0', 0x14BB5B398: '4189f9',
        0x14BB5B3A5: 'e96607d301', 0x14BB5B3D9: '488bb018490100',
        0x14BB5B42F: '488b6cc130', 0x14BB5B43D: '4489751c',
        0x14BB5B441: '4889e8', 0x14BB5B44E: 'c3',
        0x14BB5B44F: '4889d9', 0x14BB5B452: '31d2', 0x14BB5B454: '4531c0',
        0x14BB5B457: 'e81461b801', 0x14BB5B4C1: '0f8576ffffff',
        0x14BB5B4C7: 'e8147796f4', 0x14BB5B4FF: 'e84c7196f4',
    }
    for address, raw in expected.items():
        check('original-opcode-' + hex(address), code[address]['hex'] == raw)
    for address, target in ((0x14BB5B0C3, 0x145847D8C), (0x14BB5B0D0, 0x14586D05E),
                            (0x14BB5B0F3, 0x145876E41)):
        row = next(r for r in report['ripDataReferences'] if int(r['address'], 16) == address)
        check('exact-branch-cell-' + hex(address), int(row['target'], 16) == target)
    for method_id, address in ((500130, '0x14d6f9090'), (500132, '0x14d6e1570'),
                                (80101, '0x146eb90f0'), (264414, '0x14d88bb10')):
        check('exact-direct-signature-' + str(method_id), sigs[method_id]['registeredAddress'] == address)
    check('typed-populate-helper-takes42005',
          signatures['typeReferences'][str(sigs[500130]['parameters'][0]['typeIndex'])]['typeDefinition'] == 42005)
    literals = [json.loads(sources[name]) for name in PINS if 'reader-literal' in name]
    path = literals[0]['decoded']['value']
    check('frozen-proud-path-literal-identical', literals[0] == literals[1] and
          path == 'Data/_ExcelBinOutput/ProudSkillExcelConfigData')
    check('whole-payload-identity-only',
          sources[PAYLOAD + 'final3/payload.bin'] == sources[PAYLOAD + 'final4/payload.bin'] and
          len(sources[PAYLOAD + 'final3/payload.bin']) == 2132778 and
          sources[PAYLOAD + 'final3/result.private.json'] == sources[PAYLOAD + 'final4/result.private.json'])
    selected_declarations = {
        'readerOwner': by_owner[71907], 'readerStorage': by_owner[42005],
        'sourcePair': by_owner[75077], 'rowIndexField': fields[400190],
        'callerMethod': caller_method, 'indexOwnerStaticConstructorCandidate': cctor,
        'selectedExactSignatures': sigs,
    }
    return {
        'task': 'E-01/R3-PROUD-ROW-READER', 'passed': True, 'checks': checks,
        'sourceSHA256': digest(Path(__file__).read_bytes()), 'fixedPins': PINS, 'readerPins': READER_PINS,
        'callerIdentity': {'owner': 80063, 'method': caller_method, 'site': '0x14d7ad98b',
                           'setupAndCallHex': report['caller']['setupAndCallHex']},
        'readerIdentity': report['identity'], 'readerWindow': {
            k: report[k] for k in ('entry', 'endExclusive', 'codeBytes', 'codeSHA256', 'instructions',
                                   'pdataHex', 'pdataSHA256', 'aot', 'exceptionDirectory')},
        'selectedDeclarations': selected_declarations,
        'originalFieldIdentity': {'A': 372773, 'B': 372774, 'bothProducerTypes': 'uint32',
                                   'bothReaderDeclaredTypes': 'int32'},
        'branchContract': {
            'dispatch': 'byte145847d8c!=0 tail-transfers original String/A/B to264414; no local bytes contract',
            'populate': 'byte14586d05e==0 obtains object, clears+10/+1c, calls500130(reader,String,A,B); helper body not collected',
            'handleCache': {
                'condition': '14586d05e!=0 and145876e41!=0 after original class-init checks',
                'cacheField': 358013, 'cacheValueType': 'uint64; not a FileStream object',
                'A': 'MOVSXD from original int32 bits, fifth stack argument to141123190',
                'B': 'nonzero original int32 or low32 of141123260 return when original B==0; drives buffer capacity and fourth argument',
                'copyCall': '141123190(handle, sharedReader.byteArray, 0, B_effective, sign_extend(A))',
                'reader': 'field358015 shared42005; writes+1c=0 and may replace+10; does not set+18/+20/+28 here',
                'returnCountChecked': False, 'exactRowBytesOrBoundsProven': False,
                'zeroCountMeansRemainingAfterAProven': False,
            },
            'readerCache': {
                'condition': '14586d05e!=0 and145876e41==0 after original class-init checks',
                'cacheField': 358014, 'cacheValueType': 'class42005',
                'hit': 'same cached object; write+1c=A unchanged bits; B unused',
                'miss': 'call500132(String,0,0), store result through original generic helper, nonnull then write+1c=A; B unused',
                'BIsUniversalSliceSize': False, 'APlusBBoundsCheckPresent': False,
            },
            'errors': 'original null helpers1404c2be0 and dictionary-array bounds helper1404c2650 remain; no execution or replacement',
        },
        'int32ToInt64Examples': [{'u32Bits': hex(n), 'signedInt32': n if n < 0x80000000 else n - 0x100000000,
                                  'signExtendedU64': hex(n if n < 0x80000000 else n + 0xffffffff00000000)}
                                 for n in (0, 0x7fffffff, 0x80000000, 0xffffffff)],
        'rowReaderStaticContractApproved': True, 'actualRowSpanApproved': False,
        'globalRowCountApproved': False, 'wholePayloadOffsetZeroIsRowApproved': False,
        'parameterPairUniversalOffsetSizeAliasApproved': False,
        'closedLookup157ffc4c0Approved': False, 'indexLoaderProducerBodyObserved': False,
        'parserExecuted': False, 'rowLocationValuesObserved': False,
        'proudSkillGroupIdAliasApproved': False, 'secondU32LevelIdentityApproved': False,
        'promoteThresholdApproved': False, 'gTimes100PlusLevelProven': False,
        'resourceReady': False, 'completeAvatar': False, 'd02Complete': False,
        'auditCurrentPEReads': 0, 'targetCPU': 0, 'liveReads': 0, 'xdataBytesRead': 0,
        'neighborCalleeBytesRead': 0, 'newREASessions': 0,
    }


if __name__ == '__main__':
    main()
