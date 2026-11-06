"""One explicitly owned execute slot; no target code writes or exception repair.

Shared by the bounded fixture and original-client observers. Unknown register,
thread, breakpoint-table or event states stop before any continuation.
"""
DR = ('dr0', 'dr1', 'dr2', 'dr3', 'dr6', 'dr7')
STATUS_IDLE = (0, 0xffff0ff0)
STATUS_SLOT0 = (0, 1, 0xffff0ff0, 0xffff0ff1)


def hardware_guard(snapshot, event, owned, table, pid):
    regs = snapshot['registers']
    if any(regs[k] != v for k, v in snapshot['registerExpressions'].items()):
        return 'stop-hardware-register-api-mismatch'
    if snapshot.get('selectedPid') != pid or snapshot.get('selectedTid') != snapshot['tid']:
        return 'stop-hardware-selected-thread-mismatch'
    if not owned:
        if table or any(regs[k] for k in ('dr0', 'dr1', 'dr2', 'dr3')) or regs['dr7'] not in (0, 0x400) or regs['dr6'] not in STATUS_IDLE:
            return 'stop-unowned-hardware-state'
        return None
    if len(table) != 1:
        return 'stop-hardware-table-count'
    bp = table[0]
    if (bp['addr'], bp['name'], bp['slot'], bp['mod']) != (owned['address'], owned['name'], 0, owned['module']):
        return 'stop-hardware-table-identity'
    if bp['type'] != 2 or bp['typeEx'] != 2 or bp['hwSize'] != 0 or not bp['enabled'] or bp['singleshoot'] or bp['fastResume'] or bp['breakCondition'] or bp['commandText']:
        return 'stop-hardware-table-mode'
    if regs['dr0'] != owned['address'] or any(regs[k] for k in ('dr1', 'dr2', 'dr3')) or regs['dr7'] not in (1, 0x401):
        return 'stop-hardware-active-registers'
    data = event['data'] if event and event['event'] == 'EVENT_BREAKPOINT' else None
    hit = data is not None and (data['type'] == 2 or data['addr'] == owned['address'])
    if hit:
        if (data['type'], data['addr'], data['name'], data['slot'], data['mod'], data['typeEx'], data['hwSize']) != (2, owned['address'], owned['name'], 0, owned['module'], 2, 0) or regs['cip'] != owned['address']:
            return 'stop-hardware-event-identity'
        if not data['enabled'] or not data['active'] or data['singleshoot'] or data['fastResume'] or data['breakCondition'] or data['commandText']:
            return 'stop-hardware-event-mode'
        if owned.get('expectedTid') is not None and (snapshot['tid'] != owned['expectedTid'] or regs['rsp'] != owned['expectedRsp']):
            return 'stop-hardware-return-thread-or-stack'
        if regs['dr6'] not in STATUS_SLOT0:
            return 'stop-hardware-hit-status'
    elif regs['dr6'] not in STATUS_IDLE:
        return 'stop-hardware-status-without-owned-hit'
    return None


def unchanged_cpu(before, after):
    a, b = before['registers'], after['registers']
    return (all(a[k] == b[k] for k in a if k not in DR+('eflags',))
            and a['eflags'] ^ b['eflags'] in (0, 2)
            and before['selectedTid'] == after['selectedTid']
            and before['selectedPid'] == after['selectedPid'])


class SingleHardwarePoint:
    def __init__(self, client, pid, module, snapshot, records):
        from x64dbg_automate.models import BreakpointType, HardwareBreakpointType
        self.client, self.pid, self.module = client, pid, module
        self.snapshot, self.records = snapshot, records
        self.bp_type, self.execute_type = BreakpointType.BpHardware, HardwareBreakpointType.x
        self.owned = None

    def table(self):
        return [bp.model_dump(mode='json') for bp in self.client.get_breakpoints(self.bp_type)]

    def check(self, snapshot, event=None):
        return hardware_guard(snapshot, event, self.owned, self.table(), self.pid)

    def install(self, address, name, expected_code, expected_tid=None, expected_rsp=None):
        assert self.owned is None
        before = self.snapshot()
        assert self.check(before) is None
        row = dict(action='install', address=address, name=name, before=before,
                   expectedCodeHex=expected_code.hex())
        self.records.append(row)
        assert self.client.read_memory(address, len(expected_code)) == expected_code
        assert self.client.set_hardware_breakpoint(address, self.execute_type, 1)
        row['tableAfterSet'] = self.table()
        assert len(row['tableAfterSet']) == 1 and row['tableAfterSet'][0]['addr'] == address and row['tableAfterSet'][0]['slot'] == 0
        self.owned = dict(address=address, name=name, module=self.module, slot=0,
                          expectedTid=expected_tid, expectedRsp=expected_rsp,
                          expectedCodeHex=expected_code.hex())
        assert self.client.cmd_sync(f'bphwname 0x{address:x}, "{name}"')
        row['after'] = self.snapshot(); row['tableAfter'] = self.table()
        assert hardware_guard(row['after'], None, self.owned, row['tableAfter'], self.pid) is None
        assert unchanged_cpu(before, row['after'])
        assert self.client.read_memory(address, len(expected_code)) == expected_code
        row['codeUnchanged'] = True

    def clear(self, reason, event=None):
        assert self.owned is not None
        before = self.snapshot()
        assert self.check(before, event) is None
        point = dict(self.owned)
        row = dict(action='clear', reason=reason, point=point, before=before)
        self.records.append(row)
        assert self.client.clear_hardware_breakpoint(point['address'])
        row['afterAPI'] = self.snapshot(); row['tableAfterAPI'] = self.table()
        dr = row['afterAPI']['registers']
        assert all(dr[k] == v for k, v in row['afterAPI']['registerExpressions'].items())
        assert unchanged_cpu(before, row['afterAPI'])
        assert not row['tableAfterAPI'] and dr['dr0'] in (0, point['address']) and not any(dr[k] for k in ('dr1','dr2','dr3'))
        assert dr['dr7'] in (0, 1, 0x400, 0x401) and dr['dr6'] in STATUS_SLOT0
        for name in ('dr0','dr6','dr7'):
            assert self.client.set_reg(name, 0)
        row['after'] = self.snapshot()
        assert unchanged_cpu(before, row['after'])
        assert hardware_guard(row['after'], None, None, self.table(), self.pid) is None
        assert not any(row['after']['registers'][k] for k in DR)
        expected = bytes.fromhex(point['expectedCodeHex'])
        assert self.client.read_memory(point['address'], len(expected)) == expected
        row['codeUnchanged'] = True
        self.owned = None
