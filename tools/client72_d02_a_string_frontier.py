"""Source-only CN72 String/FindEntry successor audit; no native or live execution."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'local/migration72/d02-resume-20261110/a'
BASE = 0x140000000
OLD = 'local/migration72/d02-resume-20261109/r3-gadget-config-key/'
FRESH = 'local/migration72/d02-parallel-20261109/fresh-successor-replay/'
RAW = ('local/migration72/d02-parallel-20261109/combined-successor-inputs/live/'
       'avatar-string-stock-20261109T150554Z-b9263392.private.json')
RAW_SHA = '1a2e7509f6149f995cc0cc5c4ddc8f0c3f8acd297afcfc27c1eb9d5caee9c63e'
PINS = {
    OLD + 'string-hash-window.private.json': 'e229b0a3e5c9482db045997464ca02c841f2dd0f8613c880189f55b0fee1bd2b',
    OLD + 'lookup-windows.private.json': 'd392f4501a39cf2d3a4eb900fb066554022ec18979808648bd75fa39928ac6a1',
    OLD + 'two-direct-callees.private.json': '65302ea45325f125fc731b5282e8f03b9c2e8de3f43fd03d51936b505dfbeb97',
    OLD + 'main-third.json': '10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd',
    FRESH + 'first.private.json': '73e55971e1ab3785e9a13b7619bedda89da9a81fbcd548c740bfec90e60a6754',
    FRESH + 'main-approved-string-common-input.private.json': '07494634d3d46ee26807e0f96672b3378bd50fa7451786801c988a86b0bea08e',
    FRESH + 'string-common-input-boundary.private.json': 'b0475a9154e3bd5a5213c41648dfe0f0a0bc7d8737cbd55a55a2cfcbc763dd13',
    RAW: RAW_SHA,
    'local/migration72/d02-parallel-20261109/r1-allocator-successor/allocator-full1.private.json':
        'ee0e3d4b24cbd235b177b8ee7e6edfd20bd49feac89e1bda79b8d759d08274e2',
    'local/migration72/d02-parallel-20261109/string-equals-content/first.private.json':
        '215969b45954049249bd08fccca28c6ac2bfe051b7004b16b2d285b4f56ee640',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists() or not output.is_relative_to(OUT.resolve()):
        parser.error('Use a new output under this card a/ directory')
    report = audit(load_sources())
    report['sourceSHA256'] = sha(Path(__file__).read_bytes())
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True) + '\n', 'utf-8')
    print(json.dumps({'status': report['status'], 'sourcePins': len(PINS),
                      'newNativeCalls': 0, 'newLiveReads': 0, 'outputSHA256': sha(output.read_bytes())}))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def load_sources():
    result = {}
    for name, expected in PINS.items():
        content = (ROOT / name).read_bytes()
        if sha(content) != expected:
            raise ValueError('Frozen source changed: ' + name)
        result[name] = json.loads(content)
    return result


def verify_window(window):
    """Check the complete saved instruction bytes, independent of their text."""
    cursor = int(window['start'], 16)
    data = bytearray()
    by_address = {}
    for row in window['instructions']:
        raw = bytes.fromhex(row['bytes'])
        if int(row['address'], 16) != cursor or row['size'] != len(raw):
            raise ValueError('Non-contiguous saved original instruction')
        by_address[cursor] = raw
        data.extend(raw)
        cursor += len(raw)
    if cursor != int(window['end'], 16) or len(data) != window['bytes'] or sha(data) != window['sha256']:
        raise ValueError('Saved original window bytes/extent/hash differ')
    return by_address


def rip_target(site, raw):
    if len(raw) != 7 or raw[:3] not in (bytes.fromhex('488b05'), bytes.fromhex('488b0d')):
        raise ValueError('Expected exact RIP-relative pointer load')
    return site + 7 + struct.unpack('<i', raw[3:])[0]


def original_hash_prediction(text):
    """Pure host consequence of the pinned opcodes, not new native evidence."""
    units = struct.unpack('<' + 'H' * (len(text.encode('utf-16-le', 'surrogatepass')) // 2),
                          text.encode('utf-16-le', 'surrogatepass'))
    first = second = 0x1505
    for position, unit in enumerate(units):
        if unit == 0:
            break
        if position & 1:
            second = ((second * 33) ^ unit) & 0xffffffff
        else:
            first = ((first * 33) ^ unit) & 0xffffffff
    return (first + second * 0x5d588b65) & 0xffffffff


def audit(sources):
    hash_window = sources[OLD + 'string-hash-window.private.json']['windows'][0]
    find_window = sources[OLD + 'lookup-windows.private.json']['windows'][0]
    consumer_window = next(w for w in sources[OLD + 'two-direct-callees.private.json']['windows']
                           if w['start'] == '0x14aa9b8a0')
    hash_ins, find_ins, caller_ins = [verify_window(w) for w in (hash_window, find_window, consumer_window)]
    critical = {
        'hash': {0x159a8da20: '440fb74914', 0x159a8da29: '7457',
                 0x159a8da50: '440fb74902', 0x159a8da58: '743d',
                 0x159a8da66: '440fb74904', 0x159a8da76: '75c8',
                 0x159a8da78: '69c0658b585d', 0x159a8da81: 'c3',
                 0x159a8da96: 'c3', 0x159a8daa3: 'c3'},
        'findEntry': {0x158b48c03: '488b5928', 0x158b48c10: '4c8b6130',
                      0x158b48c14: '488b7910', 0x158b48cc6: '488b94d0e0000000',
                      0x158b48cce: 'ff90e0000000', 0x158b48cf2: '8b5c8320',
                      0x158b48d02: '488b4020', 0x158b48d12: 'e8593b92fe',
                      0x158b48d23: 'ffcb', 0x158b48d53: '488d3449',
                      0x158b48d5e: '488b54f508', 0x158b48d6d: '4c8b8cc810010000',
                      0x158b48d7d: 'ff9010010000', 0x158b48da2: '8b5cf504'},
        'consumer': {0x14aa9b8d1: '488bb8e8130200', 0x14aa9b8ef: '4c8b4078',
                     0x14aa9b902: 'e8d9d20a0e', 0x14aa9b923: '488b74c130'},
    }
    for label, instructions in zip(critical, (hash_ins, find_ins, caller_ins)):
        for site, expected in critical[label].items():
            if instructions[site].hex() != expected:
                raise ValueError('Critical original opcode differs: ' + hex(site))
    roots = {name: hex(rip_target(site, caller_ins[site]) - BASE) for name, site in (
        ('class17586UsageRVA', 0x14aa9b8b6), ('globalStoragePointerRVA', 0x14aa9b8ca),
        ('tryGetValueMIUsageRVA', 0x14aa9b8e1))}
    assert roots == {'class17586UsageRVA': '0x589b558', 'globalStoragePointerRVA': '0x586b600',
                     'tryGetValueMIUsageRVA': '0x59ae108'}
    old = sources[OLD + 'main-third.json']
    old_cases = [row for row in old['cases'] if row['nativeStringHash'] is not None]
    assert len(old_cases) == 5
    for row in old_cases:
        assert '\0' not in row['selectedText']
        assert row['nativeStringHash'] == original_hash_prediction(row['selectedText'])
        assert row['trace']['stop'] == '0x158b48cf6' and not row['trace']['models']
    raw, fresh = sources[RAW], sources[FRESH + 'first.private.json']
    approval = sources[FRESH + 'main-approved-string-common-input.private.json']
    assert fresh['inputEpochSHA256'] == approval['rawSHA256'] == RAW_SHA
    assert raw['collection']['carrier']['findEntryMI'] == 0 and approval['findEntryStillZero']
    assert raw['collection']['string']['identity']['definition'] == 1243
    assert fresh['native'][0]['completedOriginalInstructions'] == 25
    avatar = fresh['native'][1]
    assert avatar['completedOriginalInstructions'] == 105 and avatar['preferredStop'] == '0x1405337b9'
    equals = sources['local/migration72/d02-parallel-20261109/string-equals-content/first.private.json']
    assert equals['rawSHA256'] == RAW_SHA and not equals['ownedClassOrHeaderInvented']
    assert not equals['naturalAllocatorTerminatorVerified'] and not equals['fullFindEntry']
    allocator = sources['local/migration72/d02-parallel-20261109/r1-allocator-successor/allocator-full1.private.json']
    allocator_bytes = bytes.fromhex(allocator['bytesHex'])
    assert len(allocator_bytes) == 420 and sha(allocator_bytes) == 'c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2'
    allocator_start = int(allocator['start'], 16)
    tls_sites = {0x1405337b9: '8b05c1b89b05', 0x1405337bf: '65488b0c2558000000',
                 0x1405337c8: '488b04c1', 0x1405337cc: '488b88d0190000',
                 0x1405337d8: 'e8f3040300', 0x140533858: '488988d0190000'}
    for site, expected in tls_sites.items():
        offset = site - allocator_start
        assert allocator_bytes[offset:offset + len(expected) // 2].hex() == expected
    return {
        'task': 'F-03d3f4d2 / E-01-R3-ITEM-ENTRY', 'schemaVersion': 1,
        'status': 'source-audit-complete; exact-natural-input-missing', 'sourcePins': PINS,
        'oldEpochNotImportedIntoFresh': True, 'freshInputSHA256': RAW_SHA,
        'windows': [{k: w[k] for k in ('start', 'end', 'bytes', 'sha256')}
                    for w in (hash_window, find_window, consumer_window)],
        'criticalOpcodeAssertions': sum(map(len, critical.values())), 'rootSources': roots,
        'hashSource': {'methodDefinition': 2922, 'declaringType': 293, 'virtualSlot': 2,
                       'extentSource': hash_window['extentSource'], 'PEExceptionExtentVerified': False,
                       'firstRead': 'query+0x14/2B', 'lengthFieldRead': False,
                       'stopRule': 'first uint16 zero, including embedded NUL',
                       'naturalTerminatorVerified': False},
        'oldOwnedNativeHashEvidence': [{k: row[k] for k in
            ('name', 'selectedText', 'nativeStringHash', 'configurationHitVerified')} for row in old_cases],
        'hostOnlyPredictions': {text: original_hash_prediction(text) for text in ('', 'A', 'A\0B', 'A\0C')},
        'dictionaryNullComparerLayout': {
            'comparer': '+0x10/Q', 'buckets': '+0x28/Q', 'entries': '+0x30/Q',
            'bucketLengthRead': 'buckets+0x18/u32', 'bucket': 'buckets+0x20+4*bucketIndex/i32',
            'firstEntryIndex': 'bucketValue-1', 'entryLengthRead': 'entries+0x18/Q; compare low32',
            'entryStride': 24, 'hash': '+0/i32', 'nextIndex': '+4/i32',
            'key': '+8/Q', 'value': '+16/u64', 'bucketHash': 'StringHash & 0x7fffffff',
            'equalsABI': 'RCX=actual Default,RDX=entry.key,R8=query,R9=actual Equals MI',
            'explicitComparerNonNull': 'different interface branch; no Default substitution'},
        'naturalFrontier': {
            'preferredEvent': '0x158b48be0 entry; exact original caller return 0x14aa9b907',
            'liveIn': 'actual RCX dictionary, RDX query String, R8 FindEntry MI, RSP; no supplied pointers',
            'firstMissingTupleObserved': False, 'naturalTriggerKnown': False,
            'currentLoginPageRepeatJustified': False,
            'firstReadGraph': [
                ['RSP', 8, 'original caller return; mismatch ends attempt'],
                ['RCX', 8, 'actual dictionary class'],
                ['R8', 56, 'actual MI10194 owner; do not compare function field with direct call target'],
                ['MI.owner', 208, 'dictionary class equals owner; actual owner+30/+68'],
                ['owner.GenericClass', 32, 'definition1222; methodInst0; backref owner'],
                ['GC.classInst', 16, 'two actual generic arguments'],
                ['classInst.arguments', 16, 'two descriptor pointers'],
                ['argument0', 16, 'String kind14/byref0'],
                ['argument1', 16, 'UInt64 kind11/byref0'],
                ['owner.RGCTX+0x78', 8, 'actual published slot13 equals R8; otherwise partial'],
                ['dictionary+0x10', 8, 'actual comparer; nonzero ends null-comparer successor'],
                ['dictionary+0x28', 8, 'actual buckets; zero remains original empty branch'],
                ['dictionary+0x30', 8, 'actual entries; no fabricated array']],
            'proposedFirstPassReads': 13, 'proposedFirstPassBytes': 408,
            'proposedIncludingUnifiedExactFinalReads': 26, 'proposedIncludingUnifiedExactFinalBytes': 816,
            'maxSingleRead': 208, 'newBudgetApproved': False,
            'laterPrerequisites': [
                'same-epoch query klass293 and actual klass+e0 hash function/companion MI2922',
                'query+10 length and real UTF16 bytes through first NUL; no scan outside explicit cap',
                'same-epoch getter/Default/Equals graph; old Default pointer cannot cross epoch',
                'actual selected bucket, bounded chain, actual entry key length/content/NUL and value',
                'source Gadget+58/50011101 lineage or mark manually selected query; natural caller claim stays false'],
            'atomicSnapshotClaimed': False},
        'decision': 'necessary Gadget consumer before general TLS runtime; contract only until natural tuple exists',
        'TLSBoundary': {'freshCompletedInstructions': 105, 'missing': 'B+0x5eef080/4B',
                        'oldColdOrdinary10294IsSeparate': True, 'TLSOrGCModeled': False,
                        'nextSourceReads': ['GS:[0x58]/8B', 'TLSVector[index]/8B', 'moduleTLS+0x19d0/8B'],
                        'actualThreadAndGSBaseCaptured': False,
                        'nullStateBranch': 'original writes B+0x5699688 into moduleTLS+0x19d0',
                        'bothStateBranchesCall': '0x140563cd0 at 0x1405337d8',
                        'calleeOriginalWindowAvailableInThisCard': False,
                        'calleesNotFetched': ['0x140563cd0', '0x14055ecf0', '0x140525e00'],
                        'savedAllocatorBytesVerified': 420, 'newExtractionRequested': False},
        'newNativeCalls': 0, 'newLiveReads': 0, 'newPEExtraction': 0,
        'newStockOrGamePathReads': 0, 'hostConsumerSuccess': False,
        'naturalHashVerified': False, 'fullFindEntryVerified': False,
        'configurationHitVerified': False, 'completeAvatar': False, 'g2Verified': False, 'd02Complete': False,
    }


if __name__ == '__main__':
    main()
