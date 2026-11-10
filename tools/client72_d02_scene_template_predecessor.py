"""Frozen Scene template-reference predecessor audit; no current PE/CPU/live access."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import sys


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261110b/scene-template-predecessor'
META = 'local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl'
SIG = 'local/migration72/evidence/client72-signatures.json'
WRAPPER = 'local/migration72/d02-resume-20261110/scene-row-consumer-window/'
SCENE = 'local/migration72/d02-resume-20261110b/scene/'
PINS = {
    META: 'df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f',
    SIG: '28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe',
    'protocol/cn-7.2/profile.json': 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d',
    'tools/client72_d02_scene_row_lifecycle_20261110b.py': '92def66c2b34d100bb04b83bafe7fff3995988955257af9dc08d7c1f4ad92e39',
    WRAPPER + 'code.bin': '1a3a905d174604d2edb9c531d86569364f232240de6a5d036369feda86d8c0ec',
    WRAPPER + 'code.asm': '3718104cf0ca870277315a021943976bacabb09878228f848062ca200cba0376',
    SCENE + 'row-body/code.bin': '8670d03e37b4155990e0f8fa4dc07238dc090d0143378df352abe61a2d583f65',
    SCENE + 'main-third.private.json': 'fc37cad32bb1bbe2515e42395fc453768af06835e1229336db3bf5262c57f061',
    'local/migration72/d02-resume-20261110b/scene-slot114/main-third.private.json': '0d423a506b46ac30eb911b9da95a1f501a3bfabf84bfcd2b911acb1dc66f6e25',
    'local/migration72/d02-resume-20261110b/scene-github/inspection-main-third.json': '330be5e22b8acf306e30b8f06b372a33bdbab96f23b16f1dfadeac09733765c1',
    'local/migration72/d02-resume-20261110b/scene-template-predecessor/collect_predecessor.private.py': 'b4797e991cd2def021e93e7d59569a311d63ad541d15e18ee9e65e0e51d42c42',
}
WINDOW_PINS = {
    'boundary.private.json': '5432d00367e9ad970750d801d472984dfc840e83d0f122f296d8a19261bc60ab',
    'cell.bin': '68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da',
    'slot.bin': 'c336d4eba6a5e15b11f9ba52b71427d3e55726c00b47c46ad8e8d2ff4a0e74f7',
    'pdata.bin': 'b3b18578da6d41d2c805b44e02e712c8986a46f6773ec8998cff5730c6436abc',
    'pre-body.private.json': 'e63ed84aa4a1271e970f26a7fb1067e85665615b5387fda208c916a19c2d5f2c',
    'code.bin': 'c152e481dd8b80373fd22b4eee6e77dc59f8919513e27b48b3aea0b72e39a3dc',
    'code.asm.private.json': 'ed3d6b2d5b18783ae2704ae323c8c867b60841721e50d44e26ae99ad2216a846',
    'extraction.private.json': '2acce57c6faf23b3f32a710ab418ac062bf5ada859b65e9639a47716e16661ce',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, help='A new task-local JSON basename')
    args = parser.parse_args()
    output = OUT / args.output
    if output.name != args.output or output.suffix != '.json' or output.exists():
        parser.error('choose a new private JSON basename')
    result = audit()
    raw = (json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf-8')
    with output.open('xb') as stream:
        stream.write(raw)
    print(json.dumps({'bytes': len(raw), 'sha256': digest(raw), 'checks': len(result['checks']),
                      'oldTemplateReferenceClearStatic': True, 'componentDestructionProven': False}))


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def pinned(path, expected):
    raw = path.read_bytes()
    if digest(raw) != expected:
        raise ValueError('fixed-source-drift: ' + str(path.relative_to(ROOT)))
    return raw


def window(files):
    report = json.loads(files['extraction.private.json'])
    rows = json.loads(files['code.asm.private.json'])
    pre = json.loads(files['pre-body.private.json'])
    if (report['entry'] != '0x148d8e3f0' or report['endExclusive'] != '0x148d8e640' or
            report['codeBytes'] != len(files['code.bin']) or len(files['code.bin']) != 592 or
            report['instructions'] != len(rows) or len(rows) != 119 or
            not 0 < report['codeBytes'] <= 8192 or report['instructions'] > 2048):
        raise ValueError('wrong-complete-extent-or-approved-bound')
    diagnostic = report['exceptionDirectory']['diagnostic']
    if len(diagnostic['exact']) != 1 or diagnostic['exact'] != diagnostic['containing']:
        raise ValueError('nonunique-exact-pdata')
    if (struct.unpack('<III', files['pdata.bin']) != (0x8D8E3F0, 0x8D8E640, 0x3F14B30) or
            pre['stage'] != 'pdata-before-body' or pre['newCodeWindowCount'] != 0):
        raise ValueError('original-pdata-or-pre-body-boundary-differs')
    if (int.from_bytes(files['cell.bin'], 'little') != 0x1428A54D0 or
            int.from_bytes(files['slot.bin'], 'little') != 0x148D8E3F0 or
            int(report['aot']['slot'], 16) != 0x1428A54D0 + 219315 * 8):
        raise ValueError('wrong-original-aot')
    cursor, decoded = 0x148D8E3F0, {}
    for row in rows:
        address, raw = int(row['address'], 16), bytes.fromhex(row['hex'])
        if address != cursor or files['code.bin'][cursor - 0x148D8E3F0:cursor - 0x148D8E3F0 + len(raw)] != raw:
            raise ValueError('incomplete-or-drifted-original-instructions')
        decoded[address] = row
        cursor += len(raw)
    if cursor != 0x148D8E640:
        raise ValueError('incomplete-original-instructions')
    return report, decoded


def audit():
    raw = {name: pinned(ROOT / name, expected) for name, expected in PINS.items()}
    copies = [{name: pinned(OUT / folder / name, expected) for name, expected in WINDOW_PINS.items()}
              for folder in ('predecessor1', 'predecessor2')]
    sys.path.insert(0, str(ROOT))
    from tools import client72_d02_scene_row_lifecycle_20261110b as prior
    checks = []

    def check(condition, label):
        if not condition:
            raise ValueError(label)
        checks.append(label)

    check(copies[0] == copies[1], 'two-fresh-eight-original-artifacts-identical')
    report, code = window(copies[0])
    check(all(report[k] == 0 for k in ('nativeCPU', 'liveReads', 'xdataBytesRead', 'neighborCalleeBytesRead')),
          'no-cpu-live-xdata-or-neighbor-body')
    profile = json.loads(raw['protocol/cn-7.2/profile.json'])['files']
    check(all(json.loads(files['extraction.private.json'])['inputChecks'][item['path']]['sha256'] == item['sha256'] and
              json.loads(files['extraction.private.json'])['inputChecks'][item['path']]['bytes'] == item['size']
              for files in copies for item in profile.values()), 'both-fresh-five-full-input-identities')
    wrapper = prior.asm_rows(raw[WRAPPER + 'code.asm'])
    check(b''.join(bytes.fromhex(i['bytes']) for i in wrapper) == raw[WRAPPER + 'code.bin'] and
          all(int(b['address'], 16) == int(a['address'], 16) + len(bytes.fromhex(a['bytes']))
              for a, b in zip(wrapper, wrapper[1:])), 'all-365-original-wrapper-bytes-contiguous')
    ws = {int(i['address'], 16): i for i in wrapper}
    check(ws[0x148DA0A46]['text'] == 'mov rdx, rcx' and
          ws[0x148DA0A56]['text'] == 'mov qword ptr [rbp - 0x20], rdx' and
          ws[0x148DA0AA3]['text'] == 'mov rsi, qword ptr [rbp - 0x20]' and
          ws[0x148DA0AA7]['bytes'] == '4889f1' and ws[0x148DA0AAA]['bytes'] == 'e841d9feff',
          'same-receiver-only-predecessor-argument')
    check(0x148DA0AAF + struct.unpack('<i', bytes.fromhex('41d9feff'))[0] == 0x148D8E3F0 and
          report['caller']['setupAndCallHex'] == '4889f1e841d9feff' and
          report['caller']['sha256'] == PINS[WRAPPER + 'code.bin'], 'original-e8-and-aot-caller-binding')
    check(ws[0x148DA0AAF]['text'] == 'mov rcx, rsi' and
          ws[0x148DA0AB2]['text'] == 'mov rdx, qword ptr [rbp - 0x30]' and
          ws[0x148DA0AB6]['bytes'] == 'e835e1fcff' and
          raw[SCENE + 'row-body/code.bin'][0x146:0x14D].hex() == '4989b640010000',
          'normal-predecessor-return-before-original-new-template-association')
    records, cells = prior.PureData(io.BytesIO(raw[META])).load()
    method = next(m for m in records[28174]['methods'] if m['index'] == 219315)
    caller_method = next(m for m in records[28174]['methods'] if m['index'] == 219638)
    check(method['registeredAddress'] == '0x148d8e3f0' and method['parameterCount'] == 0 and
          method['virtualSlot'] == 65535 and caller_method['registeredAddress'] == '0x148da0a30',
          'ordinary-zero-declared-parameter-method-and-wrapper-identity')
    signatures = json.loads(raw[SIG])
    sigs = {m['index']: m for m in signatures['methods'] if m['index'] in (219315, 219638)}
    check(219315 not in sigs and sigs[219638]['declaringType'] == 28174,
          'signature-index-zero-parameter-omission-not-invented-return-type')
    template = prior.field_at(records[28174], 0x140)
    identity = prior.field_at(records[27264], 0x18)
    check(template['index'] == 138373 and template['type']['typeDefinition'] == 27264 and
          identity['index'] == 134163 and identity['type']['typeDefinition'] == 58209,
          'original-template-reference-and-encoded-id-field-indices')
    check(records[46314]['parent']['typeDefinition'] == 28174 and
          records[40472]['parent']['typeDefinition'] == 27264,
          'world-entity-and-gadget-row-domains-kept-distinct')
    caches = [next(f for f in records[owner]['fields'] if f['index'] == index)
              for owner, index in ((54132, 269426), (78883, 394856))]
    check([(f['rawOffset'], f['type']['typeDefinition'], f['type']['instanceIndex']) for f in caches] ==
          [('0x40237d0', 13732, 19433), ('0x4025e30', 13732, 46664)], 'two-original-custom-cache-domains')
    check(cells[0x1458A1300]['type']['typeDefinition'] == 52115 and
          cells[0x1458A47C8]['type']['typeDefinition'] == 40472, 'two-original-old-template-type-references')
    expected = {
        0x148D8E3F9: '4989ce', 0x148D8E3FC: '803d00dca5fc00',
        0x148D8E409: '498b8640010000', 0x148D8E410: '4885c0', 0x148D8E413: '0f842e010000',
        0x148D8E419: '488b3de02eb1fc', 0x148D8E445: '488b3d7c63b1fc',
        0x148D8E46B: '8b4018', 0x148D8E477: 'e834b91602', 0x148D8E47C: '89c7',
        0x148D8E492: '803dc8e6aefc00', 0x148D8E499: '0f849d000000',
        0x148D8E4BA: '488b88d0370200', 0x148D8E4CA: '4c8b058fccc7fc',
        0x148D8E4D3: '8b4018', 0x148D8E4DF: 'e8ccb81602', 0x148D8E4E4: '89c7',
        0x148D8E4FA: '803db0f5aefc00', 0x148D8E501: '7439',
        0x148D8E51E: '488b88305e0200', 0x148D8E52E: '4c8b05e384c8fc',
        0x148D8E535: '89fa', 0x148D8E537: 'e8c48e020c',
        0x148D8E53C: '49c7864001000000000000', 0x148D8E547: '4883c438',
        0x148D8E550: 'c3', 0x148D8E56F: '4c89f2', 0x148D8E57B: 'e990fdb404',
        0x148D8E614: 'e8c74573f7', 0x148D8E635: 'e8a64573f7',
    }
    for address, original in expected.items():
        check(code[address]['hex'] == original, 'original-opcode:' + hex(address))
    entity_accesses = [r for r in code.values() if '[r14' in r['operands']]
    check([r['operands'] for r in entity_accesses] ==
          ['rax, qword ptr [r14 + 0x140]', 'qword ptr [r14 + 0x140], 0'],
          'only-direct-entity-field-is-template-reference')
    check({r['target'] for r in report['directCalls']} ==
          {'0x14aef9db0', '0x154db7400', '0x140525e00', '0x1404c2be0'},
          'exact-direct-callees-no-new-entity-allocator-or-component-entry')
    reference_sites = {int(r['address'], 16): r['target'] for r in report['ripDataReferences']}
    check([reference_sites[p] for p in (0x148D8E492, 0x148D8E4FA, 0x148D8E4CA, 0x148D8E52E)] ==
          ['0x14587cb61', '0x14587dab1', '0x145a0b160', '0x145a16a18'],
          'exact-cache-gates-and-two-unresolved-methodinfo-cells')
    return {
        'task': 'F-03d3s2/SCENE-TEMPLATE-PREDECESSOR', 'passed': True, 'checks': checks,
        'toolSHA256': digest(Path(__file__).read_bytes()),
        'sources': {p: {'bytes': len(b), 'sha256': digest(b)} for p, b in raw.items()},
        'windowPins': WINDOW_PINS, 'body': {k: report[k] for k in
            ('entry', 'endExclusive', 'codeBytes', 'codeSHA256', 'instructions', 'aot', 'pdataHex',
             'pdataSHA256', 'exceptionDirectory')},
        'method': method, 'caller': {'method': caller_method, 'site': '0x148da0aaa',
                                    'return': '0x148da0aaf', 'receiverOnly': True},
        'fields': {'entityTemplate': template, 'encodedTemplateId': identity, 'customCaches': caches},
        'ordinaryFlow': {
            'noOldTemplate': 'old field138373 is null -> normal RET without a cache call or store',
            'otherOldTemplateType': 'not derived from52115 or40472 -> clear field138373 -> normal RET',
            'matchedTypeDisabledCache': 'decode old field134163/+18, original bool gate false -> clear -> normal RET',
            'matchedTypeEnabledCache': 'same old decoded U32 to typed custom cache154db7400; only if it returns normally, clear -> normal RET',
            'clearSite': '0x148d8e53c', 'clearValue': 0, 'normalReturnSite': '0x148d8e550',
            'newTemplateAssociation': 'wrapper then calls148d6ebf0 with original row; old585B setter stores138373',
        },
        'cacheHelper': {'entry': '0x154db7400', 'codeRead': False, 'executed': False,
                        'methodInfoCells': ['0x145a0b160', '0x145a16a18'],
                        'methodIdentityResolved': False, 'ownershipOrDestructionSemanticsApproved': False},
        'dispatch': {'flag': '0x1457ec003', 'target': '0x14d8de310', 'naturalValueObserved': False,
                     'localOrdinaryClearContractDoesNotCoverDispatch': True},
        'errors': 'original null helpers1404c2be0 and class-init140525e00 retained; no exception/return samples',
        'oldTemplateReferenceClearStatic': True, 'normalReturnValueTypeRecovered': False,
        'entityCreationProven': False, 'oldTemplateObjectDestroyedProven': False,
        'componentDestructionProven': False, 'cacheRemovalAliasApproved': False,
        'slot114ActualTargetObserved': False, 'existingEntity148d813d0SemanticsApproved': False,
        'mpLevelOriginalGroupApproved': False, 'notificationContractApproved': False,
        'naturalLifecycleObserved': False, 'completeScene': False, 'resourceReady': False, 'd02Complete': False,
        'auditCurrentPEReads': 0, 'targetCPU': 0, 'liveReads': 0, 'newREASessions': 0,
        'xdataBytesRead': 0, 'neighborCalleeBytesRead': 0,
    }


if __name__ == '__main__':
    main()
