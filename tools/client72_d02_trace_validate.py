"""Validate three owned trace witness artifacts offline; never runs a process."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import struct
import sys

HEADER = '689c722e78d116d8de876238eea125958d289a1c2b7971ad7b34baa3cacb61b1'
PINS = {
    'interface.v1.private.json': '0230c5212b0f1645345069455716e50a1635b575d6708670f03cf0bd2b7ae832',
    'witness-cases.private.json': '1250c0bdc83182de47b89b33981d08b741679e8ac68b4978e6014cbf1932be99',
    'event-schema.v1.private.json': '5cc14c09ac7682101f2af9f052920b503a9b7e7e79c7771d877eae4f6f5fb461',
    'frozen-inputs.private.json': 'a965f6309a988ea221b4789e02e794a17c9616090c1534a460ae9e11049627f5',
}
WRAPPER = bytes.fromhex('4883ec284989cb4889d14c89c24d89c8cc41ffd34883c428c3')
LEAVES = {1: bytes.fromhex('48b8efcdab89674523f1c3'),
          2: bytes.fromhex('c5fe6f01c5fe7f02c4e37d18c001c4c17e7f00c5f877c3'),
          3: bytes.fromhex('0f0bc3')}
LABELS = ('g_c48Descriptor', 'C48Arm', 'C48Call', 'C48AfterCall', 'C48WrapperEnd',
          'C48ReturnLeaf', 'C48YmmLeaf', 'C48FaultLeaf', 'C48WrapperBegin')
EPOCH_KEYS = ('descriptorRva', 'armRva', 'callRva', 'afterCallRva', 'wrapperEndRva',
              'returnLeafRva', 'ymmLeafRva', 'faultLeafRva', 'wrapperBeginRva')
GPRS = ('Rax', 'Rbx', 'Rcx', 'Rdx', 'Rsi', 'Rdi', 'Rbp', 'R8', 'R9', 'R10', 'R11', 'R12', 'R13', 'R14', 'R15')
CONTROL = ('SegCs', 'SegSs', 'EFlags', 'Rsp', 'Rip')
EFLAGS_READBACK_POLICY = 'c48-tf-only-reserved-bit1-readback-v2'


def need(condition, message):
    if not condition:
        raise ValueError(message)


def integer(value):
    return type(value) is int


def hx(value, width=16):
    need(isinstance(value, str) and re.fullmatch('[0-9a-f]{%d}' % width, value) is not None,
         'invalid lower-case hex/%d' % width)
    return int(value, 16)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'duplicate JSON key: ' + key)
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique_object)


def exact_one(items, message):
    need(len(items) == 1, message)
    return items[0]


def valid_context(row):
    c, g = row['control'], row['integer']
    need(set(c) == set(CONTROL) and set(g) == set(GPRS), 'context fields')
    for key in ('Rsp', 'Rip'):
        hx(c[key])
    hx(c['EFlags'], 8)
    need(all(integer(c[k]) and 0 <= c[k] <= 0xffff for k in ('SegCs', 'SegSs')), 'segment')
    for value in g.values():
        hx(value)


def raw_hex(value, width):
    need(isinstance(value, str) and re.fullmatch('[0-9a-f]*', value) is not None and len(value) == width * 2, 'raw bytes')
    return bytes.fromhex(value)


def check_inputs(bootstrap, mirror, header, contracts, receipt, input_paths):
    need(sha(header) == HEADER and len(bootstrap) == 128, 'header/bootstrap')
    for name, raw in contracts.items():
        need(name in PINS and sha(raw) == PINS[name], 'contract drift')
        json.loads(raw, object_pairs_hook=unique_object)
    need(set(contracts) == set(PINS), 'contract set')
    values = struct.unpack('<8sIIIIQ32s32s8I', bootstrap)
    magic, version, size, descriptor, mask, image_size, child_sha, header_sha, *rvas = values
    need((magic, version, size, mask) == (b'C48BOOT1', 1, 128, 7), 'bootstrap ABI')
    need(header_sha.hex() == HEADER and image_size > 0, 'bootstrap header/image')
    actual = [descriptor] + rvas
    need(all(integer(x) and 0 <= x < image_size for x in actual), 'bootstrap RVA')
    need(descriptor + 256 <= image_size and rvas[7] + len(WRAPPER) <= image_size and
         all(rvas[i] + len(LEAVES[case]) <= image_size for i,case in ((4,1),(5,2),(6,3))), 'bootstrap extents')
    need((rvas[0], rvas[1], rvas[2], rvas[3]) == (rvas[7] + 16, rvas[7] + 17, rvas[7] + 20, rvas[7] + 25), 'wrapper labels')
    need(mirror['schema'] == 'c48-bootstrap-root-v1' and type(mirror['imageBytes']) is int and
         mirror['imageBytes'] == image_size and mirror['childExeSHA256'] == child_sha.hex() and
         mirror['sharedHeaderSHA256'] == HEADER and mirror['bootstrapSHA256'] == sha(bootstrap), 'bootstrap mirror')
    need(all(type(mirror['labelsRva'][k]) is int and mirror['labelsRva'][k] == v for k, v in zip(LABELS, actual)), 'mirror labels')
    need(receipt['schema'] == 'c48-root-run-v1' and receipt['timedOut'] is False and
         integer(receipt['parentPid']) and receipt['parentPid'] > 0 and integer(receipt['parentExit']), 'root receipt')
    hx(receipt['parentCreationFileTime'])
    start = datetime.datetime.fromisoformat(receipt['startedUtc'].replace('Z', '+00:00'))
    end = datetime.datetime.fromisoformat(receipt['endedUtc'].replace('Z', '+00:00'))
    need(start.tzinfo is not None and end.tzinfo is not None and 0 <= (end-start).total_seconds() <= 10, 'root wall time')
    before, after = receipt['before'], receipt['after']
    need(isinstance(before, dict) and before and before == after, 'root protected inputs changed')
    for path, raw in input_paths:
        need(before.get(str(path.resolve())) == sha(raw), 'receipt lacks actual input hash: ' + str(path))
    for path, digest in before.items():
        need(isinstance(path, str) and path and isinstance(digest, str), 'receipt entry')
        hx(digest, 64)
    expected = dict(zip(EPOCH_KEYS, actual))
    expected.update(childExeSHA256=child_sha.hex(), bootstrapSHA256=sha(bootstrap),
                    sharedHeaderSHA256=HEADER, imageBytes=f'{image_size:016x}')
    return expected


def check_records(rows, summary, receipt, epoch):
    need(rows and [r['seq'] for r in rows] == list(range(1, len(rows)+1)), 'sequence')
    times = [r['monotonicElapsedUs'] for r in rows]
    need(all(integer(t) and 0 <= t <= 10000000 for t in times) and times == sorted(times), 'monotonic/wall budget')
    by_kind = {}
    for row in rows:
        by_kind.setdefault(row['kind'], []).append(row)
    allowed = {'epoch', 'ownedChild', 'wait', 'event', 'calibration', 'get', 'set', 'continue', 'memory', 'transaction', 'cleanup'}
    need(set(by_kind) <= allowed, 'unknown record kind')
    for k in ('wait', 'get', 'set', 'continue', 'memory'):
        need([r['apiOrdinal'] for r in by_kind.get(k, [])] == list(range(1, len(by_kind.get(k, []))+1)), k + ' ordinals')
    ep = exact_one(by_kind.get('epoch', []), 'epoch count')
    need({k: ep[k] for k in epoch} == epoch and summary['sourceProof'] == epoch, 'epoch/sourceProof')
    owned = exact_one(by_kind.get('ownedChild', []), 'owned child count')
    pid, tid = owned['pid'], owned['tid']
    need(integer(pid) and integer(tid) and pid > 0 and tid > 0 and pid != receipt['parentPid'] and
         owned['GetProcessTimesBOOL'] is True and owned['lastErrorIfFalse'] is None and
         hx(owned['creationFileTime']) >= hx(receipt['parentCreationFileTime']) > 0 and
         owned['processHandleOrigin'] == 'CreateProcess.PI.hProcess' and owned['threadHandleOrigin'] == 'CreateProcess.PI.hThread' and
         (summary['pid'], summary['tid']) == (pid, tid), 'owned child identity')
    events = {r['seq']: r for r in by_kind.get('event', [])}
    for row in rows:
        need((row['eventPID'], row['eventTID'], row['eventType']) == (None, None, None) or
             (row['eventPID'], row['eventTID']) == (pid, tid), 'event PID/TID')
    waits = by_kind.get('wait', [])
    successes = []
    for w in waits:
        need(integer(w['timeoutMs']) and 0 < w['timeoutMs'] <= 50, 'wait slice')
        if w['BOOL'] is True:
            need(w['lastErrorIfFalse'] is None and w['stopEventSeq'] == w['seq'] + 1 and w['stopEventSeq'] in events, 'wait/event')
            e = events[w['stopEventSeq']]
            need((w['eventPID'],w['eventTID'],w['eventType']) == (pid,tid,e['rawDebugEventCode']), 'wait identity')
            successes.append(w['stopEventSeq'])
        else:
            need(w['BOOL'] is False and w['lastErrorIfFalse'] == 121 and w['stopEventSeq'] is None, 'wait failure')
    need(successes == list(events), 'missing or duplicate event wait')
    for e in events.values():
        need((e['eventPID'],e['eventTID']) == (pid,tid) and e['eventType'] == e['rawDebugEventCode'] and e['continueStatus'] is None and
             e['ContinueDebugEventBOOL'] is None and e['continueRecordStatus'] == 'separate', 'raw event')
    continues = by_kind.get('continue', [])
    need(len(continues) == len(events), 'event continue count')
    for e in events.values():
        c = exact_one([c for c in continues if c['stopEventSeq'] == e['seq']], 'event continue association')
        need(c['seq'] > e['seq'] and (c['eventPID'], c['eventTID'], c['eventType']) == (pid, tid, e['eventType']) and
             c['BOOL'] is True and c['lastErrorIfFalse'] is None and c['continueStatus'] == '00010002' and type(c['cleanupOnly']) is bool, 'continue failed')
        later_events = [n for n in events if n > e['seq']]
        need(not later_events or c['seq'] < min(later_events), 'event continued after next event')
    for g in by_kind.get('get', []):
        need(g['requestedGroups'] == ['CONTROL', 'INTEGER'] and g['BOOL'] is True and g['lastErrorIfFalse'] is None and
             g['threadHandleOrigin'] == 'CreateProcess.PI.hThread' and g['stopEventSeq'] in events and g['seq'] > g['stopEventSeq'], 'get')
        valid_context(g)
    for s in by_kind.get('set', []):
        need(s['requestedGroups'] == ['CONTROL'] and s['BOOL'] is True and s['lastErrorIfFalse'] is None and s['integerUnchanged'] is True, 'set')
        rb = exact_one([g for g in by_kind['get'] if g['apiOrdinal'] == s['readbackGetOrdinal']], 'set readback')
        prior = [g for g in by_kind['get'] if g['stopEventSeq'] == s['stopEventSeq'] and g['seq'] < rb['seq'] and g['control'] == s['beforeControl']]
        pre = exact_one(prior[-1:], 'set before')
        need(rb['stopEventSeq'] == s['stopEventSeq'] and rb['seq'] < s['seq'] and
             rb['integer'] == pre['integer'] and rb['control'] == s['readbackControl'], 'set context readback')
        a, b = s['beforeControl'], s['submittedControl']
        need(set(b) == set(CONTROL) and all(a[k] == b[k] for k in CONTROL if k != 'EFlags') and
             (hx(a['EFlags'], 8) ^ hx(b['EFlags'], 8)) & ~0x100 == 0, 'set changed non-TF')
        submitted, readback = hx(b['EFlags'], 8), hx(rb['control']['EFlags'], 8)
        difference = submitted ^ readback
        representation = ('exact' if difference == 0 else
                          'reserved_bit1_1_to_0' if submitted & 2 and difference == 2 else 'rejected')
        need(s['eflagsReadbackPolicy'] == EFLAGS_READBACK_POLICY and
             s['eflagsReadbackXor'] == f'{difference:08x}' and
             s['eflagsReadbackRepresentation'] == representation, 'set EFlags audit')
        need(all(rb['control'][k] == b[k] for k in CONTROL if k != 'EFlags') and
             representation != 'rejected', 'set context readback')
    for m in by_kind.get('memory', []):
        width = m['width']; addr = hx(m['address']); span = m['allowedRegion']
        need(integer(width) and width > 0 and m['stopEventSeq'] in events and
             hx(span['begin']) <= addr and addr + width <= hx(span['endExclusive']) and
             m['ReadProcessMemoryBOOL'] is True and m['lastErrorIfFalse'] is None and m['actualBytesRead'] == width, 'memory failure/span')
        raw_hex(m['rawHex'], width)
    for kind in ('get','set','memory'):
        for row in by_kind.get(kind, []):
            stop = row['stopEventSeq']
            c = exact_one([c for c in continues if c['stopEventSeq'] == stop], 'stopped operation continuation')
            need(stop < row['seq'] < c['seq'] and
                 (row['eventPID'],row['eventTID'],row['eventType']) ==
                 (pid,tid,events[stop]['eventType']), 'operation outside stop')
    counts = {k: len(by_kind.get(v, [])) for k, v in [('events', 'event'), ('waits', 'wait'), ('gets', 'get'), ('sets', 'set'), ('reads', 'memory'), ('continues', 'continue')]}
    counts['readRequestedBytes'] = sum(m['width'] for m in by_kind.get('memory', []))
    counts['retired'] = sum(t['retired'] is True for t in by_kind.get('transaction', []))
    need(summary['budgets'] == counts and all(integer(v) for v in summary['budgets'].values()), 'summary counts')
    case = receipt['caseId']
    need(counts['events'] <= 128 and counts['gets'] <= {1:9,2:17,3:6}[case] and counts['sets'] == {1:4,2:8,3:2}[case] and
         counts['reads'] <= (64 if case == 2 else 16) and counts['readRequestedBytes'] <= (4096 if case == 2 else 512), 'budget')
    return by_kind, events


def context_at(records, stop, rip=None):
    matches = [g for g in records['get'] if g['stopEventSeq'] == stop and (rip is None or g['control']['Rip'] == f'{rip:016x}')]
    need(matches, 'missing stopped context')
    return matches[0]


def check_witness(case, run_id, records, events, summary, child, epoch, receipt):
    event_list = list(events.values())
    create = exact_one([e for e in event_list if e['rawDebugEventCode'] == 3], 'create event')
    base = hx(create['moduleBase'])
    image_size = hx(epoch['imageBytes'])
    need(base > 0 and hx(create['moduleImageBytes']) == image_size and create['fileSHA256'] == epoch['childExeSHA256'] and
         create['fileHandleValid'] is True and create['fileIdentityBOOL'] is True and create['fileCloseBOOL'] is True and
         create['debugProcessHandleOrigin'] == create['debugThreadHandleOrigin'] == 'CREATE_PROCESS.borrowed', 'created image proof')
    need(all(e['rawDebugEventCode'] in (1, 3, 5, 6) for e in event_list), 'unexpected event')
    for e in event_list:
        if e['rawDebugEventCode'] == 6 and e['fileHandleValid'] is True:
            need(e['fileIdentityBOOL'] is True and e['fileCloseBOOL'] is True, 'module file proof')
        if e['rawDebugEventCode'] == 1:
            need(e['firstChance'] is True and integer(e['exceptionParameterCount']) and
                 e['exceptionParameterCount'] == len(e['exceptionParameters']) <= 15, 'exception')
            hx(e['exceptionCode'], 8); hx(e['exceptionAddress']); hx(e['exceptionFlags'], 8)
            for param in e['exceptionParameters']:
                hx(param)
    cal = records.get('calibration', [])
    sysbp = exact_one([c for c in cal if c['name'] == 'systemBreakpoint'], 'system BP')
    own = exact_one([c for c in cal if c['name'] == 'ownedInt3'], 'owned BP')
    sys_event = events[sysbp['stopEventSeq']]; own_event = events[own['stopEventSeq']]
    ntdll = [e for e in event_list if e['rawDebugEventCode'] == 6 and e['ntdllNameMatch'] is True and
             e['fileHandleValid'] is True and e['moduleImageBytes'] is not None and
             hx(e['moduleBase']) <= hx(sys_event['exceptionAddress']) < hx(e['moduleBase']) + hx(e['moduleImageBytes'])]
    nt = exact_one(ntdll, 'system BP module')
    for c, e, module in ((sysbp, sys_event, hx(nt['moduleBase'])), (own, own_event, base)):
        g = context_at(records, e['seq'], hx(c['contextRip']))
        need(e['exceptionCode'] == '80000003' and c['exceptionAddress'] == e['exceptionAddress'] and
             hx(c['moduleBase']) == module and hx(c['exceptionRva']) == hx(c['exceptionAddress'])-module and g['seq'] < c['seq'], 'BP calibration')
    arm = context_at(records, own_event['seq'], base + epoch['callRva'])
    need(hx(own_event['exceptionAddress']) == base + epoch['armRva'] and not hx(arm['control']['EFlags'], 8) & 0x100, 'owned marker')
    event_codes = [e['rawDebugEventCode'] for e in event_list]
    sys_index = next(i for i,e in enumerate(event_list) if e['seq'] == sysbp['stopEventSeq'])
    own_index = next(i for i,e in enumerate(event_list) if e['seq'] == own['stopEventSeq'])
    need(0 < sys_index < own_index and event_codes[:sys_index] == [3] + [6]*(sys_index-1) and
         event_codes[sys_index] == event_codes[own_index] == 1 and
         event_codes[sys_index+1:own_index] == [6]*(own_index-sys_index-1) and
         event_codes[own_index+1:] == [1]*{1:3,2:7,3:2}[case] + [5], 'debug event sequence')
    stack_c = hx(arm['control']['Rsp'])
    need(stack_c % 16 == 0, 'stack alignment')
    leaf_rva = epoch[{1:'returnLeafRva', 2:'ymmLeafRva', 3:'faultLeafRva'}[case]]
    leaf = base + leaf_rva
    after = base + epoch['afterCallRva']
    need(hx(arm['integer']['R11']) == leaf, 'leaf register')
    mem = records['memory']
    descriptor = exact_one([m for m in mem if m['purpose'] == 'descriptor'], 'descriptor count')
    image_span = {'begin':f'{base:016x}','endExclusive':f'{base+image_size:016x}'}
    need(hx(descriptor['address']) == base + epoch['descriptorRva'] and descriptor['width'] == 256 and descriptor['allowedRegion'] == image_span, 'descriptor address')
    d = raw_hex(descriptor['rawHex'], 256)
    u32 = lambda off: struct.unpack_from('<I', d, off)[0]
    u64 = lambda off: struct.unpack_from('<Q', d, off)[0]
    need(d[:8] == b'C48TRC1\0' and (u32(8), u32(12), u32(16), u32(20)) == (1,256,case,1) and
         d[24:40].hex() == run_id and u64(40) == base and (u32(48),u32(52)) == (summary['pid'],summary['tid']) and
         (u32(56),u32(60),u32(64),u32(68),u32(72)) == (epoch['armRva'],epoch['callRva'],epoch['afterCallRva'],leaf_rva,len(LEAVES[case])) and
         u64(104) <= stack_c-8 < stack_c <= u64(112) and u32(164) == 0 and u32(208) == u32(212) == 0 and d[216:] == bytes(40), 'descriptor fields')
    source = [m for m in mem if m['purpose'] == 'sourceCode']
    need(all(m['allowedRegion'] == image_span for m in source), 'source span')
    for addr, code in ((base+epoch['wrapperBeginRva'], WRAPPER), (leaf, LEAVES[case])):
        need(any(hx(m['address']) == addr and m['width'] == len(code) and bytes.fromhex(m['rawHex']) == code for m in source), 'full source bytes')
    blocks = [u64(80+i*8) for i in range(3)]
    if case == 2:
        arena = u64(120)
        need(u32(76) == 3 and u64(128) == 65536 and u64(136) == arena and 288 <= u64(144) <= 65536 and
             blocks == [arena, arena+96, arena+192] and (u32(152),u32(156),u32(160)) == (1,2,1) and
             u32(176) >= 1 and u32(200) & 0x1c000000 == 0x1c000000 and u64(168) & 6 == 6 and
             [hx(arm['integer'][k]) for k in ('Rcx','Rdx','R8')] == [x+32 for x in blocks], 'YMM descriptor')
        initial = b''.join(bytes([a])*32 + p + bytes([b])*32 for a,p,b in
                           ((0xa1,bytes(range(1,33)),0xa2),(0xb1,bytes([0xcc])*32,0xb2),(0xc1,bytes([0xdd])*32,0xc2)))
        final = bytearray(initial); final[128:160] = bytes(range(1,33)); final[224:256] = bytes(range(1,17))*2
        for purpose, expected in (('ownedBufferInitial',initial),('ownedBufferFinal',bytes(final))):
            m = exact_one([m for m in mem if m['purpose'] == purpose], purpose)
            need(hx(m['address']) == arena and m['width'] == 288 and m['allowedRegion'] == {'begin':f'{arena:016x}','endExclusive':f'{arena+288:016x}'} and bytes.fromhex(m['rawHex']) == expected, purpose)
    else:
        need(u32(76) == 0 and all(u64(o) == 0 for o in (80,88,96,120,128,136,144,168)) and
             all(u32(o) == 0 for o in (152,156,160,176,180,184,188,192,196,200,204)) and
             all(hx(arm['integer'][k]) == 0 for k in ('Rcx','Rdx','R8')), 'non-YMM descriptor')
    if case != 3:
        need(child is not None and child['schema'] == 'c48-child-result-v1' and child['caseId'] == case and
             child['runId'] == run_id and (child['pid'],child['tid']) == (summary['pid'],summary['tid']) and
             hx(child['imageBase']) == base and child['continuedToC'] is True and child['exit'] == 0 and
             (child['cpuidCount'],child['xgetbvCount']) == ((2,1) if case == 2 else (0,0)), 'child result')
        need(hx(child['xcr0']) == u64(168) and child['cpuidLeaf0'] == list(struct.unpack_from('<4I',d,176)) and
             child['cpuidLeaf1'] == list(struct.unpack_from('<4I',d,192)), 'child host data')
        need(child['blocksBefore'] == (initial.hex() if case == 2 else '') and child['blocksAfter'] == (bytes(final).hex() if case == 2 else ''), 'child blocks')
        if case == 1:
            need(hx(child['returnValue']) == 0xf123456789abcdef, 'child return')
        else:
            hx(child['returnValue'])
    else:
        need(child is None, 'W3 child result must be absent')
    offsets = {1:[(0,10),(10,1)],2:[(0,4),(4,4),(8,6),(14,5),(19,3),(22,1)],3:[(0,2)]}[case]
    pcs = [(base+epoch['callRva'], WRAPPER[17:20])] + [(leaf+o, LEAVES[case][o:o+n]) for o,n in offsets]
    txs = records.get('transaction', [])
    need(len(txs) == len(pcs) and [t['txId'] for t in txs] == list(range(1,len(pcs)+1)), 'transaction sequence')
    mem_by_id = {m['apiOrdinal']:m for m in mem}
    sets = {s['apiOrdinal']:s for s in records['set']}
    cont = {c['apiOrdinal']:c for c in records['continue']}
    for i, (tx, (pc, code)) in enumerate(zip(txs, pcs)):
        pre_stop, post_stop = tx['preEventSeq'], tx['postEventSeq']
        need(pre_stop in events and post_stop in events and pre_stop < post_stop and (i == 0 or pre_stop == txs[i-1]['postEventSeq']), 'transaction stops')
        pre = context_at(records, pre_stop, pc)
        post = context_at(records, post_stop)
        fault = case == 3 and i == 1
        next_pc = (pcs[i+1][0] if i+1 < len(pcs) else after)
        need(tx['instructionOrigin'] == ('wrapper' if i == 0 else 'leaf') and hx(tx['preRip']) == pc and
             raw_hex(tx['instructionBytes'],len(code)) == code and tx['preGPR'] == pre['integer'] and tx['preEFlags'] == pre['control']['EFlags'] and
             tx['postGPR'] == post['integer'] and tx['postEFlags'] == post['control']['EFlags'] and tx['postRip'] == post['control']['Rip'] and
             tx['expectedNextPCs'] == ([] if fault else [f'{next_pc:016x}']) and
             tx['retired'] is (not fault) and tx['status'] == ('unretired' if fault else 'retired'), 'transaction context/retirement')
        need(any(m['stopEventSeq'] == pre_stop and hx(m['address']) == pc and m['rawHex'] == code.hex() for m in source), 'transaction source')
        s, c = sets[tx['tfSetOrdinal']], cont[tx['continueOrdinal']]
        need(s['stopEventSeq'] == c['stopEventSeq'] == pre_stop and s['beforeControl'] == pre['control'] and
             hx(s['submittedControl']['EFlags'],8) & 0x100 and s['seq'] < c['seq'] < post_stop and c['cleanupOnly'] is False, 'transaction authorization')
        need(tx['tfSetOrdinal'] == i+1 and tx['seq'] > post['seq'], 'transaction ordinal/order')
        event = events[post_stop]
        need(event['rawDebugEventCode'] == 1 and event['firstChance'] is True and
             event['exceptionCode'] == ('c000001d' if fault else '80000004') and
             hx(post['control']['Rip']) == (leaf if fault else next_pc) and
             hx(post['control']['Rsp']) == (stack_c if not fault and i == len(pcs)-1 else stack_c-8), 'post event/rip/rsp')
        if fault:
            need(hx(event['exceptionAddress']) == leaf, 'fault address')
        elif case == 1 and i >= 1:
            need(hx(post['integer']['Rax']) == 0xf123456789abcdef, 'MOV/RET RAX')
        expected_op = None
        if i == 0: expected_op = ('write',stack_c-8,8,'CALLslotOld','CALLslotNew',None,after.to_bytes(8,'little'))
        elif not fault and i == len(pcs)-1: expected_op = ('read',stack_c-8,8,'RETslot',None,after.to_bytes(8,'little'),None)
        elif case == 2 and i in (1,2,4):
            block = {1:0,2:1,4:2}[i]
            old = (bytes(range(1,33)),bytes([0xcc])*32,bytes([0xdd])*32)[block]
            new = (None,bytes(range(1,33)),bytes(range(1,17))*2)[block]
            expected_op = ('read' if i == 1 else 'write', blocks[block]+32,32,'operandRead' if i == 1 else 'writeOld', None if i == 1 else 'writeNew',old,new)
            need(hx(pre['integer'][{1:'Rcx',2:'Rdx',4:'R8'}[i]]) == blocks[block]+32, 'operand register')
        need(len(tx['ops']) == (1 if expected_op else 0), 'operand count')
        if expected_op:
            direction, ea, width, oldpurpose, newpurpose, old, new = expected_op
            op = tx['ops'][0]; m = mem_by_id[op['preReadOrdinal']]
            need(op['direction'] == direction and hx(op['EA']) == ea and op['width'] == width and
                 op['allowedSpan'] == {'begin':f'{ea:016x}','endExclusive':f'{ea+width:016x}'} and
                 m['stopEventSeq'] == pre_stop and m['purpose'] == oldpurpose and hx(m['address']) == ea and m['width'] == width and
                 m['allowedRegion'] == op['allowedSpan'] and m['seq'] < s['seq'] and op['preHex'] == m['rawHex'] and (old is None or bytes.fromhex(op['preHex']) == old), 'operand pre')
            if newpurpose:
                m = mem_by_id[op['postReadOrdinal']]
                need(m['stopEventSeq'] == post_stop and m['purpose'] == newpurpose and hx(m['address']) == ea and m['width'] == width and
                     m['allowedRegion'] == op['allowedSpan'] and post['seq'] < m['seq'] < tx['seq'] and op['postHex'] == m['rawHex'] == new.hex(), 'operand post')
            else:
                need(op['postReadOrdinal'] is None and op['postHex'] is None, 'unexpected operand post')
    need(len(source) == len(pcs)+2 and len(mem) == {1:9,2:20,3:7}[case], 'unexpected or missing memory read')
    need(len([c for c in cal if c['name'] == 'singleStep']) == (1 if case == 3 else len(pcs)), 'single step calibration count')
    need(len(cal) == 2 + (1 if case == 3 else len(pcs)), 'unexpected calibration')
    for c in [x for x in cal if x['name'] == 'singleStep']:
        e = events[c['stopEventSeq']]; g = context_at(records,e['seq'],hx(c['contextRip']))
        need(e['exceptionCode'] == '80000004' and c['exceptionAddress'] == e['exceptionAddress'] and
             hx(c['moduleBase']) == base and hx(c['exceptionRva']) == hx(c['exceptionAddress'])-base and g['seq'] < c['seq'], 'single step calibration')
    exit_event = exact_one([e for e in event_list if e['rawDebugEventCode'] == 5], 'exit event')
    expected_exit = 90 if case == 3 else 0
    need(exit_event['exitCode'] == expected_exit and summary['childExitCode'] == expected_exit and receipt['parentExit'] == (30 if case == 3 else 0), 'actual exit')
    cleanup = records.get('cleanup', [])
    for api, origin in [('CreateProcessW','newOwnedChild'),('DebugSetProcessKillOnExit','debuggerThread'),('GetExitCodeProcess','CreateProcess.PI.hProcess'),('CloseHandle','CreateProcess.PI.hThread'),('CloseHandle','CreateProcess.PI.hProcess')]:
        matches = [r for r in cleanup if r['api'] == api and r['handleOrigin'] == origin and r['BOOL'] is True and r['lastErrorIfFalse'] is None]
        exact_one(matches, 'cleanup '+api+' '+origin)
    wait = exact_one([r for r in cleanup if r['api'] == 'WaitForSingleObject' and r['handleOrigin'] == 'CreateProcess.PI.hProcess'], 'signaled')
    exit_continue = exact_one([c for c in records['continue'] if c['stopEventSeq'] == exit_event['seq']], 'exit continue')
    need(wait['result'] == 0 and wait['lastErrorIfFailed'] is None and wait['seq'] > exit_continue['seq'], 'process signal')
    exit_code = exact_one([r for r in cleanup if r['api'] == 'GetExitCodeProcess' and r['handleOrigin'] == 'CreateProcess.PI.hProcess'], 'exit code API')
    need(wait['seq'] < exit_code['seq'] and all(r['seq'] > exit_code['seq'] for r in cleanup if r['api'] == 'CloseHandle' and r['handleOrigin'] in ('CreateProcess.PI.hProcess','CreateProcess.PI.hThread')), 'PI cleanup order')
    need(all(summary[k] is True for k in ('epochValidated','EXIT_PROCESSobserved','EXIT_PROCESScontinued','ownedProcessSignaled','GetExitCodeProcessBOOL','killOnDebuggerExitAccepted','cleanupComplete','PIThreadCloseBOOL','PIProcessCloseBOOL')), 'summary cleanup')
    cleanup_cont = [c for c in records['continue'] if c['cleanupOnly']]
    need(summary['cleanupContinues'] == len(cleanup_cont), 'cleanup count')
    result_check = exact_one([r for r in cleanup if r['api'] == 'GetFileAttributesW' and r['handleOrigin'] == 'childResult'], 'child result existence')
    need((result_check['BOOL'],result_check['lastErrorIfFalse']) == ((False,2) if case == 3 else (True,None)), 'child result existence result')
    if case == 3:
        term = exact_one([r for r in cleanup if r['api'] == 'TerminateProcess' and r['handleOrigin'] == 'CreateProcess.PI.hProcess'], 'terminate')
        need(term['BOOL'] is True and term['lastErrorIfFalse'] is None and summary['TerminateProcessBOOL'] is True and summary['terminationRequestedExit'] == 90 and
             summary['firstFailureKind'] == 'expectedUD2' and summary['normalTracePassed'] is False and summary['pendingRetired'] is False and
             summary['pendingTxId'] == 2 and summary['lastCompletedTx'] == 1 and summary['stopEventSeq'] == txs[-1]['postEventSeq'] and
             summary['rawCode'] == 'c000001d' and summary['exceptionAddress'] == summary['contextRip'] == f'{leaf:016x}' and
             len(cleanup_cont) == 2 and all(c['seq'] > term['seq'] for c in cleanup_cont) and
             {c['stopEventSeq'] for c in cleanup_cont} == {txs[-1]['postEventSeq'],exit_event['seq']}, 'fault cleanup')
    else:
        need(summary['normalTracePassed'] is True and summary['firstFailureKind'] is None and summary['pendingTxId'] is None and
             summary['pendingRetired'] is None and summary['lastCompletedTx'] == len(pcs) and summary['TerminateProcessBOOL'] is None and
             summary['terminationRequestedExit'] is None and not cleanup_cont and
             all(summary[k] is None for k in ('stopEventSeq','rawCode','exceptionAddress','contextRip')), 'normal summary')
        restore = records['set'][-1]
        need(restore['stopEventSeq'] == txs[-1]['postEventSeq'] and not hx(restore['submittedControl']['EFlags'],8) & 0x100, 'TF restore')


def check_dependency(receipt, paths):
    names = {'parentExe','parentMap','childExe','childMap','header','bootstrap','bootstrapMirror','dependencyReview','interface','cases','eventSchema','frozenInputs'}
    artifacts = receipt['artifacts']
    need(set(artifacts) == names, 'receipt artifact set')
    need(all(isinstance(p, str) and Path(p).is_absolute() and str(Path(p).resolve()) == p for p in artifacts.values()), 'artifact absolute path')
    need(len(set(artifacts.values())) == len(artifacts), 'duplicate artifact path')
    for name, path in paths.items():
        need(artifacts[name] == str(path.resolve()), 'artifact CLI path ' + name)
    need(isinstance(receipt['argv'], list) and len(receipt['argv']) == 11 and all(isinstance(a,str) for a in receipt['argv']) and
         receipt['argv'][:9] == [artifacts['parentExe'],'--child-exe',artifacts['childExe'],'--bootstrap',artifacts['bootstrap'],
                                 '--case',str(receipt['caseId']),'--run-id',receipt['runId']] and
         receipt['argv'][9] == '--output', 'parent argv')
    review = read_json(Path(artifacts['dependencyReview']))
    need(review['schema'] == 'c48-trace-dependency-review-v1' and review['independentlyReviewed'] is True and
         type(review['actualBuildExit']) is int and review['actualBuildExit'] == 0 and review['peAndCallerUnwindReviewed'] is True, 'independent review')
    need(set(review['artifacts']) == {'parentExe','parentMap','childExe','childMap'}, 'review artifact set')
    entries = list(review['artifacts'].values())
    for name in ('source','includes','libraries','compilerDlls','buildLogs'):
        need(isinstance(review[name],list) and review[name], 'empty dependency group ' + name)
        entries += review[name]
    source_names = {Path(e['path']).name.lower() for e in review['source']}
    need({'shared.h','child.c','witness.asm','parent.c','build.ps1','build-epoch.h'} <= source_names, 'review source set')
    for name, entry in review['artifacts'].items():
        need(entry['path'] == artifacts[name], 'review artifact identity')
    before = receipt['before']
    for name, path in artifacts.items():
        need(path in before and sha(Path(path).read_bytes()) == before[path], 'artifact hash '+name)
    for entry in entries:
        need(set(entry) == {'path','bytes','sha256'} and isinstance(entry['path'],str) and Path(entry['path']).is_absolute() and
             str(Path(entry['path']).resolve()) == entry['path'] and integer(entry['bytes']) and entry['bytes'] >= 0, 'dependency entry')
        raw = Path(entry['path']).read_bytes()
        need(len(raw) == entry['bytes'] and sha(raw) == entry['sha256'] == before.get(entry['path']), 'dependency hash')


def validate(args):
    paths = {'header':args.header, 'bootstrap':args.bootstrap, 'bootstrapMirror':args.bootstrap_mirror,
             'interface':args.contract_dir/'interface.v1.private.json', 'cases':args.contract_dir/'witness-cases.private.json',
             'eventSchema':args.contract_dir/'event-schema.v1.private.json', 'frozenInputs':args.contract_dir/'frozen-inputs.private.json'}
    receipt = read_json(args.receipt)
    check_dependency(receipt, paths)
    output_dir = Path(receipt['argv'][10]).resolve()
    need(args.events.resolve() == output_dir/'events.jsonl' and args.parent_result.resolve() == output_dir/'parent-result.json' and
         args.child_result.resolve() == output_dir/'child-result.json', 'run output paths')
    inputs = [(p,p.read_bytes()) for p in paths.values()]
    contracts = {p.name:p.read_bytes() for p in paths.values() if p.name in PINS}
    bootstrap = args.bootstrap.read_bytes()
    epoch = check_inputs(bootstrap,read_json(args.bootstrap_mirror),args.header.read_bytes(),contracts,receipt,inputs)
    need(sha(Path(receipt['artifacts']['childExe']).read_bytes()) == epoch['childExeSHA256'], 'bootstrap child exe')
    summary = read_json(args.parent_result)
    case, run_id = receipt['caseId'], receipt['runId']
    need(type(case) is int and case in (1,2,3), 'caseId')
    hx(run_id,32)
    need(summary['schema'] == 'c48-parent-result-v1' and summary['caseId'] == case and summary['runId'] == run_id, 'summary identity')
    rows = [json.loads(line, object_pairs_hook=unique_object) for line in args.events.read_text(encoding='utf-8-sig').splitlines()]
    child = read_json(args.child_result) if args.child_result.exists() else None
    records, events = check_records(rows,summary,receipt,epoch)
    check_witness(case,run_id,records,events,summary,child,epoch,receipt)
    output_files = [args.events,args.parent_result,args.receipt] + ([args.child_result] if child is not None else [])
    return {'schema':'c48-trace-validation-v1','caseId':case,'runId':run_id,'accepted':True,
            'normalTracePassed':case != 3,'expectedNegativeAccepted':case == 3,
            'pureArtifactValidationOnly':True,'independentReviewIsExternal':True,
            'eflagsReadbackPolicy':EFLAGS_READBACK_POLICY,
            'inputSHA256':{str(p.resolve()):sha(p.read_bytes()) for p in output_files}}


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('header','bootstrap','bootstrap-mirror','contract-dir','events','parent-result','child-result','receipt','output'):
        p.add_argument('--'+name,type=Path,required=True)
    args = p.parse_args(argv)
    # Reserve the output before reading input. Never overwrite any existing artifact.
    with args.output.open('x',encoding='utf-8',newline='\n') as out:
        try:
            result = validate(args)
            status = 0
        except (ValueError, KeyError, TypeError, IndexError, OSError, AttributeError, OverflowError, StopIteration, struct.error) as exc:
            result = {'schema':'c48-trace-validation-v1','accepted':False,'pureArtifactValidationOnly':True,
                      'eflagsReadbackPolicy':EFLAGS_READBACK_POLICY,
                      'error':str(exc),'errorType':type(exc).__name__}
            status = 1
        json.dump(result,out,sort_keys=True,indent=2,ensure_ascii=False)
        out.write('\n')
    return status


if __name__ == '__main__':
    sys.exit(main())
