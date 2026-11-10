"""Independent offline checks for full-helper saved artifacts; no native execution."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools import client72_d02_clear48_full_artifacts as artifact

BOOT_CONTRACT = 'a-clear48-full-helper-boot-contract/boot-lifecycle-supplement.v1.private.json'
BOOT_CONTRACT_SHA = '2b78eed73664f85605bc693d85ab4562a68158e53d28ae2d6973479ef52ab338'


def need(condition, reason):
    if not condition:
        raise ValueError(reason)


def unique(pairs):
    value = {}
    for key, item in pairs:
        need(key not in value, 'duplicate JSON key: ' + key)
        value[key] = item
    return value


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique)


def hex_value(value, digits):
    need(isinstance(value, str) and re.fullmatch('[0-9a-f]{%d}' % digits, value) is not None,
         'invalid lowerhex')
    return int(value, 16)


def check_catalog(catalog, cases, source):
    need(len(source) == 904 and hashlib.sha256(source).hexdigest() == catalog['sourceSHA256'], 'source hash')
    rows = catalog['instructions']
    need(len(rows) == 176 and catalog['instructionCount'] == 176, 'catalog count')
    need(sum(row['supported'] is True for row in rows) == 63, 'supported count')
    offset = 0
    for ordinal, row in enumerate(rows):
        code = bytes.fromhex(row['bytesHex'])
        need(row['ordinal'] == ordinal and row['offset'] == offset and row['size'] == len(code) > 0,
             'catalog ordinal/extent')
        need(hex_value(row['rva'], 8) == 0x01fdf9c0 + offset and
             hex_value(row['originalPC'], 16) == 0x140000000 + 0x01fdf9c0 + offset, 'catalog address')
        need(source[offset:offset+len(code)] == code, 'catalog source bytes')
        need(type(row['supported']) is bool and (row['supported'] == bool(row['allowedCaseIds'])), 'catalog authorization')
        offset += len(code)
    need(offset == 904, 'catalog coverage')
    need({c['id'] for c in cases['cases']} == {257, 258}, 'case identities')
    for case in cases['cases']:
        ordinals = case['instructionOrdinals']
        need(len(ordinals) == {257: 37, 258: 40}[case['id']] and len(set(ordinals)) == len(ordinals), 'case ordinals')
        need(all(type(i) is int and 0 <= i < 176 and case['id'] in rows[i]['allowedCaseIds'] for i in ordinals), 'case authorization')
        need([int(rows[i]['originalPC'], 16) for i in ordinals] ==
             [int(pc, 16) for pc in case['expectedOriginalPCs']], 'case PCs')


def check_tf(before, submitted, readback, requested_tf):
    need(type(requested_tf) is bool, 'TF type')
    b = hex_value(before, 8)
    s = hex_value(submitted, 8)
    r = hex_value(readback, 8)
    need(s == (b & ~0x100) | (0x100 if requested_tf else 0), 'TF submission')
    need(r == s or (s & 2 and r == (s ^ 2)), 'TF readback')


def next_pc(row, pre_flags, registers, base, after_call):
    flow = row['flow']
    kind = flow['kind']
    if kind == 'sequential':
        original = hex_value(flow['nextOriginalPC'], 16)
        return base + original - 0x140000000
    if kind == 'conditional':
        flags = hex_value(pre_flags, 8)
        cf, zf = bool(flags & 1), bool(flags & 0x40)
        selected = {'CF=0 and ZF=0': not cf and not zf, 'CF=1': cf,
                    'CF=1 or ZF=1': cf or zf, 'ZF=0': not zf}[flow['condition']]
        original = hex_value(flow['takenOriginalPC' if selected else 'fallthroughOriginalPC'], 16)
        return base + original - 0x140000000
    if kind == 'indirect':
        return hex_value(registers['R11'], 16)
    if kind == 'ret':
        return after_call
    raise ValueError('unsupported flow')


def operand_address(row, registers, base):
    rva = hex_value(row['rva'], 8)
    g = lambda name: hex_value(registers[name], 16)
    if rva in (0x01fdfa86, 0x01fdfa93, 0x01fdfa9c, 0x01fdfaa5, 0x01fdfc70, 0x01fdfc79):
        return base + hex_value(row['dataOperands'][0]['originalTargetRva'], 8)
    if rva == 0x01fdfb49:
        return g('R10') + g('R11') * 4 + 0x06d190f0
    if rva == 0x01fdfcfd:
        return g('R10') + g('R11') * 4 + 0x06d19138
    if rva == 0x01fdfb94:
        return g('Rcx') + g('R8') - 0x20
    if rva == 0x01fdfd35:
        return (g('Rcx') + g('R9') - 0x20) & 0xffffffffffffffff
    if rva == 0x01fdfd3c:
        return g('Rcx') + g('R8') - 0x10
    if rva in (0x01fdfb9b, 0x01fdfd43):
        return g('Rax')
    if rva in (0x01fdfba2, 0x01fdfd47):
        return g('Rsp')
    raise ValueError('unknown data operand')


def check_page_query(row, expected_base, expected_bytes, allocation_base, state, protect, exact=True):
    allowed = row['allowedRange']
    need(set(allowed) == {'base', 'bytes'} and hex_value(allowed['base'], 16) == expected_base and
         type(allowed['bytes']) is int and allowed['bytes'] == expected_bytes, 'page allowed range')
    need(hex_value(row['address'], 16) == expected_base and row['returnedBytes'] == 48 and
         row['lastErrorIfZero'] is None, 'page query result')
    mbi = row['rawMemoryBasicInformation']
    need(set(mbi) == {'BaseAddress', 'AllocationBase', 'AllocationProtect', 'RegionSize', 'State', 'Protect', 'Type'}, 'MBI fields')
    start, size = hex_value(mbi['BaseAddress'], 16), hex_value(mbi['RegionSize'], 16)
    need(start <= expected_base and start + size >= expected_base + expected_bytes and
         hex_value(mbi['AllocationBase'], 16) == allocation_base, 'MBI range/allocation')
    need(not exact or (start == expected_base and size == expected_bytes), 'MBI exact interval')
    for key in ('AllocationProtect', 'State', 'Protect', 'Type'):
        need(type(mbi[key]) is int and 0 <= mbi[key] <= 0xffffffff, 'MBI DWORD')
    need(mbi['State'] == state and (protect is None or mbi['Protect'] == protect), 'MBI state/protection')


def check_memory(row):
    addr = hex_value(row['address'], 16)
    width = row['width']
    span = row['allowedRegion']
    need(set(span) == {'base', 'bytes', 'name'} and isinstance(span['name'], str) and span['name'], 'memory span')
    start = hex_value(span['base'], 16)
    need(type(width) is int and 0 < width <= 904 and type(span['bytes']) is int and span['bytes'] > 0 and
         start <= addr and addr + width <= start + span['bytes'] <= 0x10000000000000000, 'memory bounds')
    need(row['ReadProcessMemoryBOOL'] is True and type(row['actualBytesRead']) is int and row['actualBytesRead'] == width and
         isinstance(row['rawHex'], str) and re.fullmatch('[0-9a-f]*', row['rawHex']) is not None and
         len(row['rawHex']) == 2 * width, 'memory short or malformed')


def check_source_proof(epoch, summary, interface, expected):
    keys = interface['sourceProofKeys']
    need(len(keys) == 18 and len(set(keys)) == 18 and set(expected) == set(keys), 'source proof key set')
    need(epoch['schema'] == 'c48-full-helper-epoch-v1' and
         {k: epoch[k] for k in keys} == expected and summary['sourceProof'] == expected and
         all(type(epoch[k]) is type(expected[k]) and type(summary['sourceProof'][k]) is type(expected[k]) for k in keys), 'source proof mismatch')


def check_receipt(receipt, required_paths):
    need(receipt['schema'] == 'c48-full-helper-root-run-v1' and receipt['timedOut'] is False and
         receipt['parentCreated'] is True and receipt['childCreated'] is None and
         type(receipt['parentExit']) is int and receipt['parentExit'] == 0 and
         receipt['cleanupConfirmed'] is True and receipt['error'] is None, 'root receipt')
    need(type(receipt['parentPid']) is int and 0 < receipt['parentPid'] <= 0xffffffff and
         hx(receipt['parentCreationFileTime']) > 0, 'root process identity')
    start = datetime.datetime.fromisoformat(receipt['startedUtc'].replace('Z', '+00:00'))
    end = datetime.datetime.fromisoformat(receipt['endedUtc'].replace('Z', '+00:00'))
    need(start.tzinfo is not None and end.tzinfo is not None and 0 <= (end-start).total_seconds() <= 10, 'root deadline')
    before = receipt['before']
    need(isinstance(before, dict) and before and before == receipt['after'], 'receipt before/after')
    for path, raw in required_paths:
        need(path.is_absolute() and before.get(str(path.resolve())) == hashlib.sha256(raw).hexdigest(), 'receipt dependency')
    for path, digest in before.items():
        need(isinstance(path, str) and path and Path(path).is_absolute() and str(Path(path).resolve()) == path and
             hex_value(digest, 64) >= 0, 'receipt snapshot')


def check_dependency(review, receipt, required_sources):
    from pathlib import Path
    need(review['schema'] == 'c48-full-helper-dependency-review-v1' and
         all(review[k] is True for k in ('independentlyReviewed', 'peAndCallerUnwindReviewed', 'dependencyProofComplete')) and
         type(review['actualBuildExit']) is int and review['actualBuildExit'] == 0, 'dependency review')
    fields = ('actualCompiledSources', 'reviewedCurrentSources', 'source', 'buildLogs', 'buildTools',
              'compilerDlls', 'includes', 'libraries', 'searchedLibraries')
    entries = []
    for name in fields:
        group = review[name]
        need(isinstance(group, list) and (group or name == 'searchedLibraries'), 'dependency group ' + name)
        entries.extend(group)
    need(isinstance(review['artifacts'], dict) and review['artifacts'], 'review artifacts')
    entries.extend(review['artifacts'].values())
    need(len(review['actualCompiledSources']) == 8, 'compiled source count')
    source_paths = {x['path'] for x in review['source']}
    need(all(str(path.resolve()) in source_paths for path in required_sources), 'missing required source')
    need(all(e['path'] in source_paths for e in review['actualCompiledSources']), 'compiled source absent from review')
    for entry in entries:
        need(set(entry) == {'path', 'bytes', 'sha256'} and isinstance(entry['path'], str), 'dependency shape')
        path = Path(entry['path'])
        need(path.is_absolute() and str(path.resolve()) == entry['path'] and type(entry['bytes']) is int and entry['bytes'] >= 0, 'dependency path')
        raw = path.read_bytes()
        digest = hashlib.sha256(raw).hexdigest()
        need(entry['bytes'] == len(raw) and entry['sha256'] == digest == receipt['before'].get(entry['path']), 'dependency hash')


def check_store_sequence(case, head, records):
    state = bytearray.fromhex(case['buffer']['initialHex'])
    need(len(state) == 128 and len(records) == len(case['stores']), 'store count/initial')
    for spec, record in zip(case['stores'], records):
        start = 32 + spec['headOffset']
        width = spec['width']
        need(record['ordinal'] == spec['ordinal'] and hex_value(record['EA'], 16) == head + spec['headOffset'], 'store identity')
        need(record['width'] == width and record['oldHex'] == state[start:start+width].hex(), 'store old')
        need(record['newHex'] == spec['expectedActualNewHex'], 'store new')
        state[start:start+width] = bytes.fromhex(record['newHex'])
    need(state.hex() == case['buffer']['expectedFinalHex'], 'final buffer/guards')
    return bytes(state)


def check_transactions(case, catalog, records, events, base, image_base, call_rva, after_rva, head):
    """Recalculate case path from actual context, not producer preflight labels."""
    txs = records['transaction']
    ordinals = case['instructionOrdinals']
    need(len(txs) == len(ordinals) + 1 and all(type(t['txId']) is int for t in txs) and
         [t['txId'] for t in txs] == list(range(1, len(txs)+1)), 'transaction count/order')
    gets = {g['apiOrdinal']: g for g in records['get']}
    sets = {s['apiOrdinal']: s for s in records['set']}
    continues = {c['apiOrdinal']: c for c in records['continue']}
    memories = {m['apiOrdinal']: m for m in records['memory']}
    after = image_base + after_rva
    call_sp = None
    stores = []
    for index, tx in enumerate(txs):
        pre = gets[tx['preGetOrdinal']]
        post = gets[tx['postGetOrdinal']]
        pre_stop, post_stop = tx['preEventSeq'], tx['postEventSeq']
        need(pre['stopEventSeq'] == pre_stop and post['stopEventSeq'] == post_stop and pre_stop < post_stop and
             (index == 0 or pre_stop == txs[index-1]['postEventSeq']), 'transaction stop chain')
        need(tx['preGPR'] == pre['integer'] and tx['preEFlags'] == pre['control']['EFlags'] and
             tx['preRip'] == pre['control']['Rip'] and tx['postGPR'] == post['integer'] and
             tx['postEFlags'] == post['control']['EFlags'] and tx['postRip'] == post['control']['Rip'], 'transaction actual contexts')
        need(pre['seq'] < post['seq'] < tx['seq'] and
             (tx['eventPID'],tx['eventTID'],tx['eventType']) ==
             (events[post_stop]['eventPID'],events[post_stop]['eventTID'],events[post_stop]['eventType']), 'transaction order/identity')
        pre_rsp = hx(pre['control']['Rsp'])
        regs = dict(pre['integer'], Rsp=pre['control']['Rsp'])
        if index == 0:
            call_sp = pre_rsp
            need(call_sp % 16 == 0 and tx['instructionOrigin'] == 'owned_wrapper_CALL' and
                 all(tx[k] is None for k in ('sourceOrdinal', 'runtimeRva', 'originalPC')) and
                 tx['ownedLabel'] == 'C48FullCall' and tx['ownedRva'] == f'{call_rva:08x}' and
                 hx(tx['preRip']) == image_base + call_rva and tx['instructionBytes'] == '41ffd3', 'wrapper CALL')
            selected = base + 0x01fdf9c0
            need(hx(regs['R11']) == selected, 'CALL target')
            expect_ops = [('write', call_sp-8, 8, 'CALLslotOld', 'CALLslotNew')]
            expect_rsp = call_sp - 8
        else:
            ordinal = ordinals[index-1]
            row = catalog['instructions'][ordinal]
            rva = hex_value(row['rva'], 8)
            need(row['supported'] is True and case['id'] in row['allowedCaseIds'] and
                 tx['instructionOrigin'] == 'original_helper' and type(tx['sourceOrdinal']) is int and tx['sourceOrdinal'] == ordinal and
                 tx['ownedLabel'] is None and tx['ownedRva'] is None and
                 tx['runtimeRva'] == row['rva'] and tx['originalPC'] == row['originalPC'] and
                 hx(tx['preRip']) == base+rva and tx['instructionBytes'] == row['bytesHex'] and
                 pre_rsp == call_sp-8, 'helper source/PC')
            selected = next_pc(row, tx['preEFlags'], regs, base, after)
            expected = base + hex_value(catalog['instructions'][ordinals[index]]['rva'], 8) if index < len(ordinals) else after
            need(selected == expected, 'flags or indirect branch selected unexpected target')
            for register, expression in case['criticalPostAssertions'].get(str(ordinal), {}).items():
                if expression == 'D':
                    value = head+16
                elif expression == 'Bo':
                    value = base
                elif expression.startswith('Bo+'):
                    value = base+int(expression[3:], 16)
                elif expression.startswith('H+'):
                    value = head+int(expression[2:], 16)
                else:
                    value = int(expression, 16)
                need(hx(post['integer'][register]) == value, 'critical post register')
            expect_ops = []
            if row['dataOperands']:
                op = row['dataOperands'][0]
                ea = operand_address(row, regs, base)
                purpose = ('RETslot' if row['flow']['kind'] == 'ret' else
                           'operandRead' if op['direction'] == 'read' else 'writeOld')
                expect_ops.append((op['direction'], ea, op['width'], purpose, 'writeNew' if op['direction'] == 'write' else None))
            expect_rsp = call_sp if row['flow']['kind'] == 'ret' else call_sp-8
        need(tx['expectedNextPCs'] == [f'{selected:016x}'] and tx['preflightAccepted'] is True and
             tx['retired'] is True and tx['status'] == 'retired', 'preflight/retirement')
        s, cont = sets[tx['tfSetOrdinal']], continues[tx['continueOrdinal']]
        need(s['stopEventSeq'] == cont['stopEventSeq'] == pre_stop and s['beforeControl'] == pre['control'] and
             hx(s['submittedControl']['EFlags'], 8) & 0x100 and pre['seq'] < s['seq'] < cont['seq'] < post_stop and
             cont['BOOL'] is True and cont['cleanupOnly'] is False, 'transaction TF/continue')
        if index:
            need(txs[index-1]['seq'] < cont['seq'], 'pending overlap')
        event = events[post_stop]
        need(event['rawDebugEventCode'] == 1 and event['firstChance'] is True and
             type(event['exceptionCode']) is int and event['exceptionCode'] == 0x80000004 and hx(event['exceptionAddress']) == selected and
             hx(post['control']['Rip']) == selected and hx(post['control']['Rsp']) == expect_rsp, 'single step')
        need(len(tx['ops']) == len(expect_ops), 'operand count')
        fetch = [m for m in records['memory'] if m['purpose'] == 'instructionBytes' and m['stopEventSeq'] == pre_stop and
                 hx(m['address']) == hx(tx['preRip']) and m['rawHex'] == tx['instructionBytes']]
        need(len(fetch) == 1 and fetch[0]['seq'] < s['seq'], 'instruction fetch')
        fetch_span = fetch[0]['allowedRegion']
        need(hx(fetch_span['base']) == (image_base+call_rva-19 if index == 0 else base+0x01fdf9c0) and
             fetch_span['bytes'] == (27 if index == 0 else 904), 'instruction fetch span')
        for op, (direction, ea, width, pre_purpose, post_purpose) in zip(tx['ops'], expect_ops):
            old = memories[op['preReadOrdinal']]
            need(op['direction'] == direction and hx(op['EA']) == ea and type(op['width']) is int and op['width'] == width and
                 set(op['allowedSpan']) == {'base','bytes','name'} and
                 hx(op['allowedSpan']['base']) == ea and op['allowedSpan']['bytes'] == width and
                 old['purpose'] == pre_purpose and hx(old['address']) == ea and old['width'] == width and
                 isinstance(op['allowedSpan']['name'], str) and bool(op['allowedSpan']['name']) and
                 all(op['allowedSpan'][k] == old['allowedRegion'][k] for k in ('base','bytes')) and old['stopEventSeq'] == pre_stop and
                 old['seq'] < s['seq'] and op['preHex'] == old['rawHex'], 'operand pre')
            if post_purpose:
                new = memories[op['postReadOrdinal']]
                need(new['purpose'] == post_purpose and hx(new['address']) == ea and new['width'] == width and
                     all(new['allowedRegion'][k] == op['allowedSpan'][k] for k in ('base','bytes')) and new['stopEventSeq'] == post_stop and
                     post['seq'] < new['seq'] < tx['seq'] and op['postHex'] == new['rawHex'], 'operand post')
            else:
                need(op['postReadOrdinal'] is None and op['postHex'] is None, 'unexpected operand post')
            if index == 0:
                need(op['postHex'] == after.to_bytes(8, 'little').hex(), 'CALL return slot')
            elif row['flow']['kind'] == 'ret':
                need(ea == call_sp-8 and op['preHex'] == after.to_bytes(8, 'little').hex() and hx(post['integer']['Rax']) == head+16, 'RET slot/RAX')
            elif direction == 'write':
                stores.append({'ordinal': ordinal, 'EA': op['EA'], 'width': width, 'oldHex': op['preHex'], 'newHex': op['postHex']})
            else:
                controls = [x for x in case['controls'] if base + int(x['rva'],16) == ea and x['width'] == width]
                need(len(controls) == 1 and op['preHex'] == controls[0]['bytesHex'], 'control span/value')
    check_store_sequence(case, head, stores)
    return txs


def check_child_api(child):
    """Check frozen child allocation/protection/flush and release call sequence."""
    arena = hx(child['arenaBase'])
    buffer = hx(child['bufferReserveBase'])
    setup = child['setupApiRecords']
    release = child['releaseApiRecords']
    need(len(setup) == 12 and len(release) == 2 and arena % 65536 == buffer % 65536 == 0 and arena and buffer, 'child setup count/alignment')
    def alloc(row, address, size, alloc_type, protect, result):
        need(set(row) == {'api','address','size','allocationType','protect','result','lastErrorIfNull'} and
             row['api'] == 'VirtualAlloc' and hx(row['address']) == address and hx(row['size']) == size and
             type(row['allocationType']) is int and row['allocationType'] == alloc_type and
             type(row['protect']) is int and row['protect'] == protect and hx(row['result']) == result and
             row['lastErrorIfNull'] is None, 'child VirtualAlloc')
    def protect(row, address, new):
        need(set(row) == {'api','address','size','newProtect','BOOL','lastErrorIfFalse','oldProtect'} and
             row['api'] == 'VirtualProtect' and hx(row['address']) == address and hx(row['size']) == 4096 and
             type(row['newProtect']) is int and row['newProtect'] == new and row['BOOL'] is True and
             row['lastErrorIfFalse'] is None and type(row['oldProtect']) is int and row['oldProtect'] == 4, 'child VirtualProtect')
    pages = (0x01fdf000, 0x056af000, 0x05eee000, 0x06d19000)
    alloc(setup[0], 0, 0x06d20000, 0x2000, 1, arena)
    for row, offset in zip(setup[1:5], pages):
        alloc(row, arena+offset, 4096, 0x1000, 4, arena+offset)
    for row, offset, new in zip(setup[5:9], pages, (32, 2, 2, 2)):
        protect(row, arena+offset, new)
    flush = setup[9]
    need(set(flush) == {'api','process','address','size','BOOL','lastErrorIfFalse'} and
         flush['api'] == 'FlushInstructionCache' and hx(flush['process']) != 0 and
         hx(flush['address']) == arena+0x01fdf9c0 and hx(flush['size']) == 904 and
         flush['BOOL'] is True and flush['lastErrorIfFalse'] is None, 'child flush')
    alloc(setup[10], 0, 65536, 0x2000, 1, buffer)
    alloc(setup[11], buffer+4096, 4096, 0x1000, 4, buffer+4096)
    for row, address in zip(release, (buffer, arena)):
        need(set(row) == {'api','address','size','freeType','BOOL','lastErrorIfFalse'} and
             row['api'] == 'VirtualFree' and hx(row['address']) == address and hx(row['size']) == 0 and
             type(row['freeType']) is int and row['freeType'] == 0x8000 and row['BOOL'] is True and
             row['lastErrorIfFalse'] is None, 'child release')


def check_records(rows, summary, receipt, interface):
    common = interface['common']['required']
    need(rows and all(type(x['seq']) is int for x in rows) and [x['seq'] for x in rows] == list(range(1, len(rows)+1)), 'record sequence')
    times = [r['monotonicElapsedUs'] for r in rows]
    need(all(type(t) is int and 0 <= t <= 10000000 for t in times) and times == sorted(times), 'record deadline')
    kinds = interface['rawRecordKinds']
    records = {}
    for row in rows:
        need(all(k in row for k in common) and row['kind'] in kinds, 'raw common/kind')
        records.setdefault(row['kind'], []).append(row)
    for k in ('wait', 'get', 'set', 'continue', 'memory', 'pageQuery'):
        need([x['apiOrdinal'] for x in records.get(k, [])] == list(range(1, len(records.get(k, []))+1)), k+' ordinal')
    owned = exact_one(records.get('ownedChild', []), 'owned child')
    pid, tid = owned['pid'], owned['tid']
    need(type(pid) is int and type(tid) is int and 0 < pid <= 0xffffffff and 0 < tid <= 0xffffffff and
         pid != receipt['parentPid'] and (summary['pid'], summary['tid']) == (pid, tid) and
         owned['GetProcessTimesBOOL'] is True and owned['lastErrorIfFalse'] is None and
         hx(owned['creationFileTime']) >= hx(receipt['parentCreationFileTime']) > 0 and
         owned['processHandleOrigin'] == 'CreateProcess.PI.hProcess' and owned['threadHandleOrigin'] == 'CreateProcess.PI.hThread' and
         all(hx(owned[k]) not in (0, 0xffffffffffffffff) for k in ('processHandleRaw','threadHandleRaw')), 'owned identity')
    events = {x['seq']: x for x in records.get('event', [])}
    successes = []
    for wait in records.get('wait', []):
        need(type(wait['timeoutMs']) is int and 0 < wait['timeoutMs'] <= 50, 'wait slice')
        if wait['BOOL'] is True:
            need(wait['lastErrorIfFalse'] is None and wait['stopEventSeq'] == wait['seq']+1 and wait['stopEventSeq'] in events, 'wait/event')
            event = events[wait['stopEventSeq']]
            need((wait['eventPID'],wait['eventTID'],wait['eventType']) == (event['eventPID'],event['eventTID'],event['rawDebugEventCode']), 'wait identity')
            successes.append(event['seq'])
        else:
            need(wait['BOOL'] is False and wait['lastErrorIfFalse'] == 121 and wait['stopEventSeq'] is None and
                 (wait['eventPID'],wait['eventTID'],wait['eventType']) == (None,None,None), 'wait failure')
    need(successes == list(events) and len(events) <= 128, 'wait event bijection/budget')
    continues = records.get('continue', [])
    need(len(continues) == len(events), 'continue count')
    for event in events.values():
        need(type(event['eventPID']) is int and event['eventPID'] == pid and type(event['eventTID']) is int and 0 < event['eventTID'] <= 0xffffffff and
             type(event['rawDebugEventCode']) is int and event['eventType'] == event['rawDebugEventCode'] and
             event['continueStatus'] is None and event['ContinueDebugEventBOOL'] is None and event['continueRecordStatus'] == 'separate', 'event identity')
        if event['rawDebugEventCode'] == 1:
            need(event['firstChance'] is True and type(event['exceptionCode']) is int and 0 <= event['exceptionCode'] <= 0xffffffff and
                 type(event['exceptionFlags']) is int and 0 <= event['exceptionFlags'] <= 0xffffffff and
                 type(event['exceptionParameterCount']) is int and event['exceptionParameterCount'] == len(event['exceptionParameters']) <= 15, 'exception raw')
            hx(event['exceptionAddress'])
            for value in event['exceptionParameters']: hx(value)
        cont = exact_one([c for c in continues if c['stopEventSeq'] == event['seq']], 'continue event')
        next_wait = [w['seq'] for w in records['wait'] if w['seq'] > event['seq']]
        need(cont['seq'] > event['seq'] and (not next_wait or cont['seq'] < min(next_wait)) and
             (cont['eventPID'],cont['eventTID'],cont['eventType']) == (pid,event['eventTID'],event['eventType']) and
             cont['BOOL'] is True and cont['lastErrorIfFalse'] is None and type(cont['continueStatus']) is int and cont['continueStatus'] == 0x00010002 and
             cont['cleanupOnly'] is False, 'continue result/order')
    control_keys = {'SegCs','SegSs','EFlags','Rsp','Rip'}
    gpr_keys = {'Rax','Rbx','Rcx','Rdx','Rsi','Rdi','Rbp','R8','R9','R10','R11','R12','R13','R14','R15'}
    for get in records.get('get', []):
        need(get['requestedGroups'] == ['CONTROL','INTEGER'] and get['BOOL'] is True and get['lastErrorIfFalse'] is None and
             get['threadHandleOrigin'] == 'CreateProcess.PI.hThread' and set(get['control']) == control_keys and set(get['integer']) == gpr_keys, 'Get context')
        for k in ('Rsp','Rip'): hx(get['control'][k])
        hx(get['control']['EFlags'],8)
        need(all(type(get['control'][k]) is int and 0 <= get['control'][k] <= 65535 for k in ('SegCs','SegSs')), 'segments')
        for value in get['integer'].values(): hx(value)
    for s in records.get('set', []):
        need(s['requestedGroups'] == ['CONTROL'] and s['BOOL'] is True and s['lastErrorIfFalse'] is None and s['integerUnchanged'] is True, 'Set context')
        readback = exact_one([g for g in records['get'] if g['apiOrdinal'] == s['readbackGetOrdinal']], 'Set readback')
        prior = [g for g in records['get'] if g['stopEventSeq'] == s['stopEventSeq'] and g['seq'] < readback['seq'] and g['control'] == s['beforeControl']]
        pre = exact_one(prior[-1:], 'Set prior Get')
        a, b, r = s['beforeControl'], s['submittedControl'], readback['control']
        need(set(b) == control_keys and readback['stopEventSeq'] == s['stopEventSeq'] and readback['seq'] < s['seq'] and
             readback['integer'] == pre['integer'] and r == s['readbackControl'] and
             all(a[k] == b[k] == r[k] for k in control_keys if k != 'EFlags'), 'Set control/GPR')
        check_tf(a['EFlags'], b['EFlags'], r['EFlags'], bool(hx(b['EFlags'], 8) & 0x100))
        difference = hx(b['EFlags'],8) ^ hx(r['EFlags'],8)
        need(s['eflagsReadbackPolicy'] == 'c48-tf-only-reserved-bit1-readback-v2' and
             s['eflagsReadbackXor'] == f'{difference:08x}' and
             s['eflagsReadbackRepresentation'] == ('exact' if difference == 0 else 'reserved_bit1_1_to_0'), 'Set representation')
    for memory in records.get('memory', []):
        need(memory['purpose'] in interface['memoryPurposeEnum'], 'memory purpose')
        check_memory(memory)
    for kind in ('get','set','memory','pageQuery'):
        for row in records.get(kind, []):
            stop = row['stopEventSeq']
            need(stop in events and events[stop]['eventTID'] == tid and events[stop]['eventType'] == 1 and
                 (row['eventPID'],row['eventTID'],row['eventType']) == (pid,tid,1), 'operation identity')
            cont = exact_one([c for c in continues if c['stopEventSeq'] == stop], 'operation continuation')
            need(stop < row['seq'] < cont['seq'], 'operation outside stop')
    return records, events


def check_descriptor_and_pages(case, records, events, epoch, child, run_id, interface):
    create = exact_one([x for x in events.values() if x['rawDebugEventCode'] == 3], 'CREATE_PROCESS')
    image_base = hx(create['moduleBase'])
    image_size = hx(epoch['imageBytes'])
    need(image_base > 0 and hx(create['moduleImageBytes']) == image_size and
         create['fileSHA256'] == epoch['childExeSHA256'], 'image identity')
    memories = records['memory']
    desc = exact_one([x for x in memories if x['purpose'] == 'descriptor'], 'descriptor')
    need(hx(desc['address']) == image_base + epoch['descriptorRva'] and desc['width'] == 384 and
         hx(desc['allowedRegion']['base']) == image_base and desc['allowedRegion']['bytes'] == image_size, 'descriptor location')
    data = bytes.fromhex(desc['rawHex'])
    u32 = lambda o: struct.unpack_from('<I', data, o)[0]
    u64 = lambda o: struct.unpack_from('<Q', data, o)[0]
    need(data[:8] == b'C48FULL1' and (u32(8),u32(12),u32(16),u32(20)) == (1,384,case['id'],1) and
         data[24:40].hex() == run_id and u64(40) == image_base and (u32(48),u32(52)) == (child['pid'],child['tid']) and
         [u32(x) for x in (56,60,64,68,72)] == [epoch[x] for x in ('armRva','callRva','afterCallRva','wrapperBeginRva','wrapperEndRva')] and
         u32(76) == 904 and u32(204) == 0 and data[344:] == bytes(40), 'descriptor ABI/identity')
    arena, reserve, known, head, dest = (u64(x) for x in (80,104,128,144,152))
    need(arena and arena % 65536 == 0 and u64(88) == 0x06d20000 and u64(96) == arena+0x01fdf9c0 and
         reserve and reserve % 65536 == 0 and u64(112) == 65536 and u64(120) == reserve+4096 and
         known == reserve+4096 and u64(136) == 128 and head == known+32 and head % 32 == 0 and dest == head+16 and
         u32(176) == 4096 and u32(180) == 65536 and u32(184) == 1 and u32(188) == 2 and u32(192) == 1 and
         u32(196) == 0 and u32(200) == 0 and u64(208) & 6 == 6 and u32(216) >= 1 and u32(232+8) & 0x1c000000 == 0x1c000000 and
         data[248:280].hex() == epoch['source904SHA256'] and data[280:312].hex() == epoch['catalogSHA256'] and
         data[312:344].hex() == epoch['casesSHA256'], 'descriptor allocation/host/hash')
    need(child['schema'] == 'c48-full-helper-child-result-v1' and child['caseId'] == case['id'] and child['runId'] == run_id and
         child['hostGateValid'] is True and child['cpuidCount'] == 2 and child['xgetbvCount'] == 1 and
         hx(child['xcr0']) == u64(208) and child['cpuidLeaf0'] == list(struct.unpack_from('<4I', data,216)) and
         child['cpuidLeaf1'] == list(struct.unpack_from('<4I', data,232)) and
         hx(child['arenaBase']) == arena and hx(child['arenaReserved']) == 0x06d20000 and
         hx(child['bufferReserveBase']) == reserve and hx(child['headBase']) == head and hx(child['destination']) == dest and
         type(child['helperCallCount']) is int and child['helperCallCount'] == 1 and child['continuedToC'] is True and hx(child['returnedRAX']) == dest and
         child['initial128Hex'] == case['buffer']['initialHex'] and child['final128Hex'] == case['buffer']['expectedFinalHex'] and
         all(child[k] == epoch[k] for k in ('source904SHA256','catalogSHA256','casesSHA256')), 'child result')
    need(type(child['cpuidCount']) is int and type(child['xgetbvCount']) is int and
         all(type(x) is int and 0 <= x <= 0xffffffff for x in child['cpuidLeaf0']+child['cpuidLeaf1']), 'child host integer types')
    check_child_api(child)
    for purpose, address, expected in (('wrapper',image_base+epoch['wrapperBeginRva'],interface['wrapper']['bytesHex']),
                                       ('source904',arena+0x01fdf9c0,None),
                                       ('ownedBufferInitial',known,case['buffer']['initialHex']),
                                       ('ownedBufferFinal',known,case['buffer']['expectedFinalHex'])):
        mem = exact_one([m for m in memories if m['purpose'] == purpose], purpose)
        need(hx(mem['address']) == address and hx(mem['allowedRegion']['base']) == address and
             mem['allowedRegion']['bytes'] == mem['width'] and (expected is None or mem['rawHex'] == expected), purpose+' bytes/address')
        if purpose == 'source904':
            need(mem['width'] == 904 and hashlib.sha256(bytes.fromhex(mem['rawHex'])).hexdigest() == epoch['source904SHA256'], 'source904 read')
    pages = records.get('pageQuery', [])
    need(len(pages) == 13, 'page query count')
    for i, spec in enumerate(interface['arena']['queryIntervals']):
        row = pages[i]
        need(row['purpose'] == 'arena'+str(i), 'arena page order')
        check_page_query(row, arena+int(spec['rva'],16), spec['bytes'], arena,
                         0x1000 if spec['state'] == 'MEM_COMMIT' else 0x2000, spec['protect'])
    for i, spec in enumerate(interface['buffer']['queryIntervals']):
        row = pages[9+i]
        need(row['purpose'] == 'buffer'+str(i), 'buffer page order')
        check_page_query(row, reserve+spec['offset'], spec['bytes'], reserve,
                         0x1000 if spec['state'] == 'MEM_COMMIT' else 0x2000, spec['protect'])
    need(pages[12]['purpose'] == 'stack', 'stack query')
    arm_stop = desc['stopEventSeq']
    arm = exact_one([g for g in records['get'] if g['stopEventSeq'] == arm_stop and hx(g['control']['Rip']) == image_base+epoch['callRva']][:1], 'Arm context')
    c = hx(arm['control']['Rsp'])
    need(c % 16 == 0 and u64(160) <= c-8 and c+32 <= u64(168) and
         hx(arm['integer']['R11']) == arena+0x01fdf9c0 and hx(arm['integer']['Rcx']) == dest and
         hx(arm['integer']['Rdx']) == 0 and hx(arm['integer']['R8']) == 48 and
         not hx(arm['control']['EFlags'],8) & 0x100, 'Arm registers/stack')
    stack = pages[12]
    span = stack['allowedRange']
    mbi = stack['rawMemoryBasicInformation']
    allocation = hx(mbi['AllocationBase'])
    check_page_query(stack, hx(span['base']), span['bytes'], allocation, 0x1000, 4, exact=False)
    need(allocation != 0 and allocation <= u64(160) and hx(span['base']) <= c-8 and
         c+32 <= hx(span['base'])+span['bytes'] and hx(mbi['BaseAddress']) <= c-8 and
         c+32 <= hx(mbi['BaseAddress'])+hx(mbi['RegionSize']), 'stack committed span')
    need(all(p['stopEventSeq'] == arm_stop for p in pages), 'page queries at Arm')
    return arena, image_base, head


def check_breakpoints(records, events, image_base, arena, arm_rva, call_rva, expected_steps):
    calibrations = records.get('calibration', [])
    sysbp = exact_one([c for c in calibrations if c['name'] == 'systemBreakpoint'], 'system breakpoint')
    own = exact_one([c for c in calibrations if c['name'] == 'ownedInt3'], 'owned breakpoint')
    system_event, own_event = events[sysbp['stopEventSeq']], events[own['stopEventSeq']]
    nt_base = nt_end = 0
    for event in events.values():
        if event['seq'] >= system_event['seq']:
            break
        code = event['rawDebugEventCode']
        if code == 6:
            path = event['filePath']
            is_ntdll = path is not None and path.replace('\\','/').rsplit('/',1)[-1].casefold() == 'ntdll.dll'
            need(event['ntdllNameMatch'] is is_ntdll, 'ntdll source name')
            if is_ntdll:
                need(nt_base == 0 and event['fileHandleValid'] is True and event['fileIdentityBOOL'] is True and
                     event['fileCloseBOOL'] is True, 'ntdll source identity')
                nt_base = hx(event['moduleBase'])
                nt_end = nt_base + hx(event['moduleImageBytes'])
                need(0 < nt_base < nt_end <= 0xffffffffffffffff, 'ntdll range')
        elif code == 7 and hx(event['moduleBase']) == nt_base:
            nt_base = nt_end = 0
    need(nt_base != 0 and nt_base <= hx(system_event['exceptionAddress']) < nt_end, 'system breakpoint module')
    need(sum(g['stopEventSeq'] == system_event['seq'] for g in records['get']) == 1, 'system breakpoint Get count')
    for row, event, base in ((sysbp,system_event,nt_base), (own,own_event,image_base)):
        get = exact_one([g for g in records['get'] if g['stopEventSeq'] == event['seq'] and
                         g['control']['Rip'] == row['contextRip']][:1], 'breakpoint context')
        need(event['rawDebugEventCode'] == 1 and event['firstChance'] is True and event['exceptionCode'] == 0x80000003 and
             row['exceptionAddress'] == event['exceptionAddress'] and hx(row['moduleBase']) == base and
             hx(row['exceptionRva']) == hx(row['exceptionAddress'])-base and get['seq'] < row['seq'], 'breakpoint calibration')
    need(hx(own_event['exceptionAddress']) == image_base+arm_rva and hx(own['contextRip']) == image_base+call_rva, 'Arm breakpoint')
    steps = [x for x in calibrations if x['name'] == 'singleStep']
    need(len(steps) == expected_steps and len(calibrations) == expected_steps+2, 'calibration count')
    need(sysbp['addressOrigin'] == 'systemModule' and own['addressOrigin'] == 'childImage', 'breakpoint origin')
    need(records['transaction'][0]['preEventSeq'] == own_event['seq'], 'Arm transaction boundary')
    for index, row in enumerate(steps):
        event = events[row['stopEventSeq']]
        base = image_base if index == len(steps)-1 else arena
        origin = 'childImage' if index == len(steps)-1 else 'sourceArena'
        get = exact_one([g for g in records['get'] if g['stopEventSeq'] == event['seq'] and g['control']['Rip'] == row['contextRip']][:1], 'step context')
        need(row['addressOrigin'] == origin and hx(row['moduleBase']) == base and row['exceptionAddress'] == event['exceptionAddress'] and
             hx(row['exceptionRva'], 16 if origin == 'childImage' else 8) == hx(row['exceptionAddress'])-base and event['exceptionCode'] == 0x80000004 and
             get['seq'] < row['seq'], 'step calibration')
    need(system_event['seq'] < own_event['seq'] and
         [e['seq'] for e in events.values() if e['rawDebugEventCode'] == 1] ==
         [system_event['seq'],own_event['seq']] + [x['stopEventSeq'] for x in steps], 'exception sequence')


def check_normal_cleanup(records, exit_event):
    for row in records['cleanup']:
        if 'BOOL' in row:
            need(row['BOOL'] is True and row['lastErrorIfFalse'] is None, 'normal cleanup BOOL/error')
    exit_continue = exact_one([r for r in records['continue'] if r['stopEventSeq'] == exit_event['seq']], 'exit continue')
    wait = exact_one([r for r in records['cleanup'] if r['api'] == 'WaitForSingleObject'], 'process wait')
    getexit = exact_one([r for r in records['cleanup'] if r['api'] == 'GetExitCodeProcess'], 'process exit code')
    need(wait['handleOrigin'] == 'CreateProcess.PI.hProcess' and wait['result'] == 0 and wait['lastErrorIfFailed'] is None and
         exit_continue['seq'] < wait['seq'] < getexit['seq'] and getexit['BOOL'] is True and getexit['lastErrorIfFalse'] is None and
         getexit['exitCode'] == exit_event['exitCode'] == 0, 'exit wait/GetExit sequence')
    for origin in ('CreateProcess.PI.hProcess','CreateProcess.PI.hThread'):
        close = exact_one([r for r in records['cleanup'] if r['api'] == 'CloseHandle' and r['handleOrigin'] == origin], 'PI close')
        need(close['seq'] > getexit['seq'] and close['BOOL'] is True and close['lastErrorIfFalse'] is None, 'PI close result')


def validate(args):
    need(all(p.is_absolute() for p in (args.run,args.build,args.bootstrap,args.dependency_review,args.contract_dir,args.output)), 'absolute CLI paths')
    receipt = read_json(args.run/'receipt.json')
    artifacts = receipt['artifacts']
    need(set(artifacts) == {'parentExe','childExe','header','bootstrap','bootstrapMirror','helper','dependencyReview','contractDirectory'}, 'receipt artifacts')
    for name, path in artifacts.items():
        p = Path(path)
        need(p.is_absolute() and str(p.resolve()) == path and (p.is_dir() if name == 'contractDirectory' else p.is_file()), 'artifact path')
    expected_header = Path(__file__).resolve().parent/'client72_d02_clear48_full_helper'/'shared.h'
    expected_paths = {'parentExe':args.build/'parent.exe', 'childExe':args.build/'child.exe', 'header':expected_header,
                      'bootstrap':args.bootstrap/'bootstrap.bin', 'bootstrapMirror':args.bootstrap/'bootstrap.private.json',
                      'dependencyReview':args.dependency_review, 'contractDirectory':args.contract_dir}
    need(all(artifacts[k] == str(v.resolve()) for k,v in expected_paths.items()), 'receipt CLI artifact identity')
    need(expected_header.read_bytes() == (args.build/'shared.h').read_bytes() and
         hashlib.sha256(expected_header.read_bytes()).hexdigest() == artifact.HEADER, 'actual/snapshot header drift')
    case_id, run_id = receipt['caseId'], receipt['runId']
    need(type(case_id) is int and case_id in (257,258), 'case identity')
    hex_value(run_id,32)
    need(receipt['argv'] == [artifacts['parentExe'],'--child-exe',artifacts['childExe'],'--bootstrap',artifacts['bootstrap'],
         '--helper',artifacts['helper'],'--case',str(case_id),'--run-id',run_id,'--output',str((args.run/'native').resolve())], 'argv')
    contract = artifact.contracts(args.contract_dir)
    interface = json.loads(contract['interface.v1.private.json'], object_pairs_hook=unique)
    catalog = json.loads(contract['catalog.v1.private.json'], object_pairs_hook=unique)
    cases = json.loads(contract['cases.v1.private.json'], object_pairs_hook=unique)
    events_schema = json.loads(contract['events-and-results.v1.private.json'], object_pairs_hook=unique)
    source = Path(artifacts['helper']).read_bytes()
    check_catalog(catalog,cases,source)
    case = exact_one([c for c in cases['cases'] if c['id'] == case_id], 'case')
    required_paths = [(Path(path), Path(path).read_bytes()) for name,path in artifacts.items() if name != 'contractDirectory']
    required_paths.extend((args.contract_dir/name, raw) for name,raw in contract.items())
    required_paths.append((args.contract_dir/'contract-artifacts.private.json', (args.contract_dir/'contract-artifacts.private.json').read_bytes()))
    check_receipt(receipt,required_paths)
    review = read_json(args.dependency_review)
    need(set(review['artifacts']) == {'parentExe','parentMap','childExe','childMap'} and
         all(review['artifacts'][key]['path'] == str((args.build/name).resolve()) for key,name in
             (('parentExe','parent.exe'),('parentMap','parent.map'),('childExe','child.exe'),('childMap','child.map'))), 'review PE/map identity')
    root = Path(__file__).resolve().parents[1]
    private = root/'local/migration72/d02-resume-20261110b'
    pins_path = private/'a-clear48-backend/input-pins.private.json'
    fixed = {pins_path:'f02965fbe282ddfac9373df863a456f2ca26af4792dcfaf1db99c8ff1f2e9310',
             private/'a-clear48-trace-exit-lifecycle/exit-lifecycle-supplement.v2.private.json':interface['exitPolicySHA256'],
             private/'a-clear48-full-helper-owned/root-bindings.v1.private.json':'cf25a1a0f16a3b11f08ac719b6197e71aa6fba6a903a709abd86c76197494e6a',
             private/'a-clear48-full-helper-owned/child-api-records.v1.private.json':'a478fce47774611d39407d17a65d04a0ba7fb41504c43643543881af9cff69c1',
             private/'a-clear48-full-helper-owned/build-review-schema.v1.private.json':'49a8887844d1be080e7a372eeb531512e0d079efc28ccdb0003177884b8d02a1'}
    fixed[private/'a-clear48-full-helper-owned/transaction-owned-mapping.v1.private.json'] = '39391d1a1131b417b0ac910fca1e4f4b603622b425758be5b512c4bc9376d06d'
    fixed[private/'a-clear48-full-helper-owned/root-receipt-semantics.v1.private.json'] = 'a5ed259f2ad430ff63ecf8b3efc92bb5f76d13a6f2b162ae697ed02ae5299675'
    fixed[private/BOOT_CONTRACT] = BOOT_CONTRACT_SHA
    need(all(hashlib.sha256(p.read_bytes()).hexdigest() == digest for p,digest in fixed.items()), 'fixed supplement drift')
    old_pins = read_json(pins_path)
    need(len(old_pins) == 112, 'old source pin count')
    for name, pin in old_pins.items():
        path = root/name
        raw = path.read_bytes()
        need(len(raw) == pin['bytes'] and hashlib.sha256(raw).hexdigest() == pin['sha256'], 'old source drift')
    supplements = [root/'tools/client72_d02_clear48_full_generate.py', root/'tools/client72_d02_clear48_full_run.py',
                   root/'tools/client72_d02_clear48_full_artifacts.py', Path(__file__), args.contract_dir/'frozen-inputs.private.json',
                   args.bootstrap/'bootstrap.bin', args.bootstrap/'bootstrap.private.json']
    need(hashlib.sha256(supplements[0].read_bytes()).hexdigest() == '2d4e5b8e906bd33a30bbec8137d7b384639607b19e66c4a04f2059355773d39c', 'generator drift')
    need(hashlib.sha256((args.contract_dir/'frozen-inputs.private.json').read_bytes()).hexdigest() == 'eb84933be87cb5559fc800f4ffeb239f611310903c7335b6dd58c5a8dec5efe2', 'frozen inputs manifest drift')
    check_dependency(review,receipt,[args.contract_dir/name for name in contract] + [Path(artifacts['helper'])] +
                     list(fixed) + [root/name for name in old_pins] + supplements)
    compiled = {str((args.build/name).resolve()) for name in artifact.BUILD_SOURCES | set(artifact.GENERATED) | {'build-epoch.h'}}
    current = {str((expected_header.parent/name).resolve()) for name in artifact.BUILD_SOURCES}
    need({e['path'] for e in review['actualCompiledSources']} == compiled and len(review['actualCompiledSources']) == len(compiled) and
         {e['path'] for e in review['reviewedCurrentSources']} == current and len(review['reviewedCurrentSources']) == len(current), 'review source identities')
    artifact.audit_build(args.build, expected_header.parent)
    bootstrap = (args.bootstrap/'bootstrap.bin').read_bytes()
    need(len(bootstrap) == 256, 'bootstrap length')
    values = struct.unpack('<8sIIIIQ32s32s32s32s32s8IQ4I8s', bootstrap)
    magic, version, size, descriptor, mask, image_bytes, child_sha, header_sha, source_sha, catalog_sha, cases_sha, *tail = values
    arm, call, after, end, begin, desc_bytes, helper_rva, helper_bytes, reserve, exit_version, flags_version, count, wrap_bytes, reserved = tail
    need((magic,version,size,mask,desc_bytes,helper_rva,helper_bytes,reserve,exit_version,flags_version,count,wrap_bytes,reserved) ==
         (b'C48FBO01',1,256,3,384,0x01fdf9c0,904,0x06d20000,2,2,2,27,bytes(8)) and
         (arm,call,after,end) == (begin+18,begin+19,begin+22,begin+27) and descriptor+384 <= image_bytes and end <= image_bytes and
         child_sha.hex() == hashlib.sha256((args.build/'child.exe').read_bytes()).hexdigest() and
         header_sha.hex() == artifact.HEADER and source_sha.hex() == interface['source904SHA256'] and
         catalog_sha.hex() == interface['catalogSHA256'] and cases_sha.hex() == interface['casesSHA256'], 'bootstrap ABI/hash')
    mirror = read_json(args.bootstrap/'bootstrap.private.json')
    need(mirror['schema'] == 'c48-full-helper-bootstrap-root-v1' and mirror['executed'] is False and
         mirror['bootstrapSHA256'] == hashlib.sha256(bootstrap).hexdigest() and mirror['childExeSHA256'] == child_sha.hex() and
         mirror['sharedHeaderSHA256'] == header_sha.hex() and mirror['source904SHA256'] == source_sha.hex() and
         mirror['catalogSHA256'] == catalog_sha.hex() and mirror['casesSHA256'] == cases_sha.hex() and mirror['imageBytes'] == image_bytes, 'mirror')
    labels = mirror['labelsRva']
    need(set(labels) == set(artifact.SYMBOLS) and [labels[k] for k in ('g_c48FullDescriptor','C48FullWrapperBegin','C48FullArm','C48FullCall','C48FullAfterCall','C48FullWrapperEnd')] ==
         [descriptor,begin,arm,call,after,end], 'mirror labels')
    pe_raw, pe_base, pe_size, sections = artifact.pe_info(args.build/'child.exe')
    actual_labels = artifact.map_symbols(args.build/'child.map', pe_base)
    need(pe_size == image_bytes and labels == actual_labels and labels['C48FullWrapper'] == begin and
         labels['C48FullReadXcr0'] == labels['C48FullXcrEntry'] and
         labels['C48FullXcrEnd'] == labels['C48FullXcrEntry']+13, 'actual PE/map')
    artifact.mapped_code(pe_raw, sections, begin, artifact.WRAPPER)
    artifact.mapped_code(pe_raw, sections, labels['C48FullXcrEntry'], artifact.XCR)
    matching = [s for s in sections if s[1] <= descriptor and descriptor+384 <= s[1]+s[2]]
    need(len(matching) == 1 and matching[0][5] & 0xc0000000 == 0xc0000000 and not matching[0][5] & 0x20000000, 'descriptor PE permissions')
    epoch = {'childExeSHA256':child_sha.hex(), 'parentExeSHA256':hashlib.sha256((args.build/'parent.exe').read_bytes()).hexdigest(),
             'bootstrapSHA256':hashlib.sha256(bootstrap).hexdigest(), 'sharedHeaderSHA256':header_sha.hex(), 'source904SHA256':source_sha.hex(),
             'catalogSHA256':catalog_sha.hex(), 'casesSHA256':cases_sha.hex(), 'interfaceContractSHA256':hashlib.sha256(contract['interface.v1.private.json']).hexdigest(),
             'imageBytes':f'{image_bytes:016x}', 'descriptorRva':descriptor, 'wrapperBeginRva':begin, 'armRva':arm, 'callRva':call,
             'afterCallRva':after, 'wrapperEndRva':end, 'exitLifecyclePolicy':interface['exitPolicy'],
             'exitLifecycleContractSHA256':interface['exitPolicySHA256'], 'eflagsReadbackPolicy':interface['eflagsPolicy']}
    native = args.run/'native'
    summary, child = read_json(native/'parent-result.json'), read_json(native/'child-result.json')
    need(summary['schema'] == 'c48-full-helper-parent-result-v1' and summary['caseId'] == case_id and summary['runId'] == run_id and
         summary['normalTracePassed'] is True and summary['firstFailureKind'] is None and summary['pendingTxId'] is None and
         summary['pendingRetired'] is None, 'normal parent summary')
    rows = [json.loads(line, object_pairs_hook=unique) for line in (native/'events.jsonl').read_text(encoding='utf-8-sig').splitlines()]
    ep = exact_one([r for r in rows if r['kind'] == 'epoch'], 'epoch count')
    check_source_proof(ep,summary,interface,epoch)
    records, events = check_records(rows,summary,receipt,events_schema)
    arena, image_base, head = check_descriptor_and_pages(case,records,events,epoch,child,run_id,interface)
    need((child['pid'],child['tid']) == (summary['pid'],summary['tid']), 'child identity')
    txs = check_transactions(case,catalog,records,events,arena,image_base,call,after,head)
    check_breakpoints(records,events,image_base,arena,arm,call,len(txs))
    exit_event = exact_one([e for e in events.values() if e['rawDebugEventCode'] == 5], 'exit event')
    need(exit_event['exitCode'] == 0 and summary['childExitCode'] == 0 and summary['lastCompletedTx'] == len(txs), 'exit/retirement')
    releases = check_lifecycle(case_id, records, events, summary, txs, exit_event)
    check_normal_cleanup(records,exit_event)
    restore = records['set'][-1]
    final_get = next(g for g in records['get'] if g['apiOrdinal'] == txs[-1]['postGetOrdinal'])
    first_get = next(g for g in records['get'] if g['apiOrdinal'] == txs[0]['preGetOrdinal'])
    need(restore['stopEventSeq'] == txs[-1]['postEventSeq'] and restore['beforeControl'] == final_get['control'] and
         (hx(restore['submittedControl']['EFlags'],8) & 0x100) == (hx(first_get['control']['EFlags'],8) & 0x100) == 0 and
         all(final_get['integer'][k] == first_get['integer'][k] for k in ('Rbx','Rbp','Rsi','Rdi','R12','R13','R14','R15')) and
         hx(summary['returnRSP']) == hx(first_get['control']['Rsp']), 'final context/restore')
    need(all(summary[k] is True for k in ('EXIT_PROCESSobserved','EXIT_PROCESScontinued','ownedProcessSignaled','GetExitCodeProcessBOOL',
         'PIThreadCloseBOOL','PIProcessCloseBOOL','threadLifecycleConsistent','cleanupComplete')), 'summary lifecycle')
    need(summary['helperRetired'] == len(case['instructionOrdinals']) and summary['wrapperRetired'] == 1 and
         summary['helperControlReads'] == case['helperControlReads'] and summary['helperControlReadBytes'] == case['helperControlReadBytes'] and
         summary['helperStoreEvents'] == len(case['stores']) and summary['helperStoreSumBytes'] == case['logicalStoreSumBytes'] and
         summary['helperStoreUnionBytes'] == case['logicalStoreUnionBytes'] and summary['helperStoreIntersectionBytes'] == case['logicalStoreIntersectionBytes'] and
         hx(summary['returnRAX']) == head+16, 'summary helper results')
    need(all(type(summary[k]) is int for k in ('helperRetired','wrapperRetired','helperControlReads','helperControlReadBytes',
             'helperStoreEvents','helperStoreSumBytes','helperStoreUnionBytes','helperStoreIntersectionBytes')), 'summary integer types')
    counts = {'events':len(events), 'waits':len(records.get('wait',[])), 'gets':len(records.get('get',[])),
              'sets':len(records.get('set',[])), 'reads':len(records.get('memory',[])),
              'requestedReadBytes':sum(m['width'] for m in records['memory']), 'continues':len(records.get('continue',[])),
              'pageQueries':len(records.get('pageQuery',[])), 'retired':len(txs), 'helperRetired':len(txs)-1}
    need(summary['budgets'] == counts and all(type(v) is int for v in summary['budgets'].values()) and
         counts['reads'] == case['successfulInspectorReadCount'] and
         counts['requestedReadBytes'] == case['successfulInspectorRequestedBytes'] and counts['gets'] <= case['budgets']['getContextMax'] and
         counts['sets'] <= case['budgets']['setContextMax'], 'budget counts')
    result = {'schema':'c48-full-helper-validation-v1', 'accepted':True, 'caseId':case_id, 'runId':run_id,
              'pureArtifactValidationOnly':True, 'sourceProof':epoch, 'budgets':counts, 'borrowedReleases':releases,
              'bootLifecyclePolicy':'c48-full-helper-boot-lifecycle-v1', 'bootLifecycleContractSHA256':BOOT_CONTRACT_SHA,
              'inputSHA256':{str(p.resolve()):hashlib.sha256(p.read_bytes()).hexdigest() for p in
                             (native/'events.jsonl',native/'parent-result.json',native/'child-result.json',args.run/'receipt.json')}}
    result.update({k:False for k in events_schema['claimsAlwaysFalse']})
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('run','build','bootstrap','dependency-review','contract-dir','output'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args(argv)
    if not args.output.is_absolute():
        parser.error('--output must be absolute')
    with args.output.open('x', encoding='utf-8', newline='\n') as out:
        try:
            result, status = validate(args), 0
        except (ValueError, KeyError, TypeError, IndexError, OSError, AttributeError, OverflowError, StopIteration, struct.error) as exc:
            result, status = {'schema':'c48-full-helper-validation-v1','accepted':False,'error':str(exc),'errorType':type(exc).__name__}, 1
        json.dump(result,out,ensure_ascii=False,sort_keys=True,indent=2)
        out.write('\n')
    return status


def integer(value):
    return type(value) is int


def hx(value, width=16):
    return hex_value(value, width)


def exact_one(items, message):
    need(len(items) == 1, message)
    return items[0]


def check_lifecycle(case, records, events, summary, txs, exit_event):
    """Reconstruct owned thread origins and releases from raw events and API results."""
    pid, primary = summary['pid'], summary['tid']
    continues = {c['stopEventSeq']: c for c in records['continue']}
    terminal = txs[-1]['postEventSeq']
    arm = txs[0]['preEventSeq']
    if case == 3:
        boundary = txs[-1]['seq']
    else:
        restore = records['set'][-1]
        c = continues[terminal]
        need(txs[-1]['seq'] < restore['seq'] < c['seq'] and c['cleanupOnly'] is False,
             'terminal restore/continue boundary')
        boundary = c['seq']
    need(all(r['seq'] <= boundary for kind in ('get','set','memory','transaction','calibration','pageQuery')
             for r in records.get(kind, [])), 'operation after boundary')
    need(all(r['stopEventSeq'] >= arm for kind in ('set','memory','pageQuery')
             for r in records.get(kind, [])), 'operation before Arm')
    thread_table = {}
    releases = []
    create_count = 0
    def raw_handle(e, field, origin):
        value = hx(e[field + 'Raw'])
        valid = e[field + 'Valid']
        need(type(valid) is bool and valid == (value not in (0, 0xffffffffffffffff)), 'handle validity')
        need(value != 0xffffffffffffffff and e[field + 'Origin'] == origin, 'borrowed handle origin')
    def dword(value):
        need(integer(value) and 0 <= value <= 0xffffffff, 'DWORD')
    for e in events.values():
        code, tid, seq = e['rawDebugEventCode'], e['eventTID'], e['seq']
        c = continues[seq]
        need(not any(k in e for k in ('automaticCloseBOOL','borrowedCloseBOOL')), 'fabricated automatic close')
        if seq > boundary:
            need(code in (2,4,5,6,7,8), 'unexpected exit notification')
            need(c['cleanupOnly'] is (case == 3), 'exit continue mode')
        else:
            if seq < arm:
                need(code in (1,2,3,4,6,7,8) and (code not in (1,3) or tid == primary) and
                     (code != 4 or tid != primary), 'BOOT identity/event')
            else:
                need(tid == primary and code == 1, 'TRACE identity/event')
            if case == 3 and seq == terminal:
                need(c['cleanupOnly'] is True, 'fault continue mode')
            else:
                need(c['cleanupOnly'] is False, 'trace continue mode')
        if code == 3:
            create_count += 1
            need(seq == next(iter(events)) and create_count == 1 and tid == primary and tid not in thread_table,
                 'initial thread origin')
            raw_handle(e, 'debugProcessHandle', 'CREATE_PROCESS.borrowed')
            raw_handle(e, 'debugThreadHandle', 'CREATE_PROCESS.borrowed')
            hx(e['startAddress']); hx(e['threadLocalBase'])
            thread_table[tid] = (seq, e['debugThreadHandleValid'])
        elif code == 2:
            need(create_count == 1 and tid not in thread_table and len(thread_table) < 128, 'duplicate thread origin')
            raw_handle(e, 'debugThreadHandle', 'CREATE_THREAD.borrowed')
            hx(e['startAddress']); hx(e['threadLocalBase'])
            thread_table[tid] = (seq, e['debugThreadHandleValid'])
        else:
            need(tid in thread_table and thread_table[tid] is not None, 'unknown or exited thread')
        if code in (3,6):
            value = hx(e['fileHandleRaw'])
            need(type(e['fileHandleValid']) is bool and e['fileHandleValid'] == (value not in (0,0xffffffffffffffff)) and
                 e['fileHandleOrigin'] == ('CREATE_PROCESS.hFile' if code == 3 else 'LOAD_DLL.hFile'), 'file handle raw/origin')
            if e['fileHandleValid']:
                need(e['fileIdentityBOOL'] is True and e['fileCloseBOOL'] is True and
                     isinstance(e['filePath'], str) and bool(e['filePath']) and hx(e['moduleImageBytes']) > 0 and
                     e['fileIdentityError'] is None and e['fileCloseError'] is None and e['filePathError'] is None and
                     integer(e['filePathRequiredLength']) and 0 < e['filePathRequiredLength'] < 2048, 'file proof')
                identity = e['fileIdentity']
                need(isinstance(identity, dict) and set(identity) == {'volumeSerial','indexHigh','indexLow'}, 'file identity')
                for value in identity.values(): dword(value)
            else:
                need(e['filePath'] is None and e['filePathError'] is None and e['filePathRequiredLength'] == 0 and
                     e['fileIdentityBOOL'] is None and e['fileIdentityError'] is None and e['fileIdentity'] is None and
                     e['fileCloseBOOL'] is None and e['fileCloseError'] is None and e['moduleImageBytes'] is None, 'null file evidence')
        if code in (4,5): dword(e['exitCode'])
        if code == 7: hx(e['moduleBase'])
        if code == 8:
            hx(e['debugStringAddress'])
            need(all(integer(e[k]) and 0 <= e[k] <= 65535 for k in ('debugStringUnicode','debugStringLength')), 'debug string WORD')
        if code == 4:
            origin, valid = thread_table[tid]
            if valid:
                releases.append({'originEventSeq':origin, 'continueSeq':c['seq'], 'tid':tid, 'basis':'api_contract'})
            thread_table[tid] = None
        if code == 5:
            need(seq == exit_event['seq'] and seq == max(events), 'final exit order')
            for t, entry in thread_table.items():
                if entry is not None and entry[1]:
                    releases.append({'originEventSeq':entry[0], 'continueSeq':c['seq'], 'tid':t, 'basis':'api_contract'})
            create = next(x for x in events.values() if x['rawDebugEventCode'] == 3)
            if create['debugProcessHandleValid']:
                releases.append({'originEventSeq':create['seq'], 'continueSeq':c['seq'], 'basis':'api_contract', 'object':'process'})
    need(create_count == 1 and summary['exitEventSeq'] == exit_event['seq'] and
         summary['exitEventTID'] == exit_event['eventTID'] and summary['threadLifecycleConsistent'] is True, 'lifecycle summary')
    owned = records['ownedChild'][0]
    for r in records.get('cleanup', []):
        origin, api = r['handleOrigin'], r['api']
        need(api != 'CloseHandle' or origin in ('CreateProcess.PI.hProcess','CreateProcess.PI.hThread','NULInput','stdout','stderr'), 'manual borrowed close')
        if origin in ('CreateProcess.PI.hProcess','CreateProcess.PI.hThread'):
            need(r['handleRaw'] == owned['processHandleRaw' if origin.endswith('hProcess') else 'threadHandleRaw'], 'PI handle raw')
        if api == 'GetExitCodeProcess':
            need(r['BOOL'] is True and r['exitCode'] == exit_event['exitCode'] and integer(r['exitCode']), 'exit code agreement')
    terms = [r for r in records['cleanup'] if r['api'] == 'TerminateProcess']
    need(len(terms) == (1 if case == 3 else 0), 'terminate count')
    for api in ('GetExitCodeProcess','WaitForSingleObject'):
        exact_one([r for r in records['cleanup'] if r['api'] == api], 'cleanup API count')
    for origin in ('CreateProcess.PI.hProcess','CreateProcess.PI.hThread'):
        exact_one([r for r in records['cleanup'] if r['api'] == 'CloseHandle' and r['handleOrigin'] == origin], 'PI close count')
    return releases


if __name__ == '__main__':
    sys.exit(main())

