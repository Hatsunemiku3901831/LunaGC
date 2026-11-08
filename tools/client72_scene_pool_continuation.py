"""Fixed fresh Pool Allocate prefix with explicit refusal of unknown loader TLS."""
import argparse
import hashlib
import json
from pathlib import Path
import struct

from tools.client72_emulator import Offline72, STACK, END
from tools.extract_client72 import PE, number
from tools.observe_client72_uninstrumented import EXPECTED

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'local/migration72/d02-resume-20261108/scene-pool'
SNAPSHOT = ROOT/'local/migration72/d02-active/main/entry-context-tool/live-resume-20261108-2.json'
SNAPSHOT_SHA = '119f8a0b3c3ecc8829eda9bf7422c3646c643afa34ac041ea69b5607a02a7384'
WINDOWS = ((0x1470687c0, 32, '98a93ee052b474915ef1212a25986b49326f99a9f91f23530bb52b0196432861'),
           (0x14050a890, 0x4c0, '7b3f2a829a31d0f3aaeaa16dc46628a996c41d8431779f9d06bddc108234c73b'),
           (0x14050ad90, 0xae, '4ebcea02f7a4568b35fc295eb99d1cb93f1d01d1cef2a74124b0998d76db07d2'))
TLS_READER, TLS_INDEX = 0x14050a8ae, 0x145eef080


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists() or not output.is_relative_to(RAW.resolve()):
        parser.error('Choose a new output in the scene-pool private directory')
    profile = json.loads((ROOT/'protocol/cn-7.2/profile.json').read_bytes())
    inputs = {}
    for name, expected in EXPECTED.items():
        item = profile['files'][name]
        value = (args.client/item['path']).read_bytes()
        if item['sha256'] != expected or digest(value) != expected:
            raise ValueError('Fixed client input changed: '+name)
        inputs[name] = expected
    binary = (args.client/profile['files']['binary']['path']).read_bytes()
    snapshot_raw = SNAPSHOT.read_bytes()
    if digest(snapshot_raw) != SNAPSHOT_SHA:
        raise ValueError('Fresh Pool source changed')
    head, mode = fresh_inputs(json.loads(snapshot_raw))
    static = native_sources(binary)
    actual = run_prefix(binary, head, mode, True)
    control = run_prefix(binary, head, 0, False)
    report = {'task': 'F-03d3s2-pool-prefix', 'fixedInputs': inputs,
              'sourceSHA256': digest(Path(__file__).read_bytes()), 'freshSnapshotSHA256': SNAPSHOT_SHA,
              'dependencies': {name: digest((ROOT/'tools'/name).read_bytes()) for name in
                               ('client72_emulator.py', 'extract_client72.py', 'observe_client72_uninstrumented.py')},
              'static': static, 'actualModePrefix': actual, 'controlledZeroMode': control,
              'models': ['Offline PE mapping, owned stack and opaque nonzero this storage only.',
                         'The captured class46314 header is copied to model storage; none of its fields is consumed before the stop.',
                         'No allocator, TLS, OS thread, object constructor or profiling return is modeled.'],
              'allocatorCompleted': False, 'worldFactoryCompleted': False, 'naturalCallerObserved': False,
              'nextInput': {'readerRVA': '0x50a8ae', 'moduleRVA': '0x5eef080', 'bytes': 4,
                            'source': 'Exact PE TLS directory AddressOfIndex; loader-owned value not present in the snapshot',
                            'laterThreadRequirement': 'The allocating thread GS:[58] and that exact TLS slot; a login-page main thread is not an established allocation caller'},
              'd02Passed': False}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'firstMissingReader': '0x50a8ae', 'bytes': 4, 'allocatorCompleted': False}))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def fresh_inputs(snapshot):
    if snapshot['sourceSHA256'] != 'ecab38c9e39daf5ea48bb017cc7060c61c8b142c4f3844d37b54c3e7374f9c7c' or not snapshot['poolAllocateBindingVerified']:
        raise ValueError('Exact fresh Pool binding is not established')
    rows = {x['name']: x for x in snapshot['reads']}
    data = {}
    for name in ('pool.entityClass46314.header', 'pool.mode'):
        row = rows[name]; value = bytes.fromhex(row['bytesHex'])
        if row['error'] is not None or len(value) != row['requested'] or digest(value) != row['sha256']:
            raise ValueError('Captured Pool span is incomplete')
        data[name] = value
    head = data['pool.entityClass46314.header']
    if len(head) != 0xd0 or struct.unpack_from('<Q', head, 0x10)[0] != 46314 or struct.unpack_from('<I', head, 0x18)[0]>>16&255 != 18:
        raise ValueError('Only the exact observed class46314 may supply this prefix')
    mode = data['pool.mode']
    if mode != b'\x01':
        raise ValueError('Fresh natural Pool mode differs from the planned nonzero path')
    return head, mode[0]


def native_sources(binary):
    pe = PE(binary)
    windows = []
    for entry, width, expected in WINDOWS:
        value = binary[pe.offset(entry):pe.offset(entry)+width]
        if digest(value) != expected:
            raise ValueError('Reviewed native Allocate window changed')
        windows.append({'entry': hex(entry), 'bytes': width, 'SHA256': expected,
                        'extent': 'fixed leaf forwarder window' if width == 32 else 'PE exception directory function'})
    optional = number(binary, 0x3c)+24
    tls_rva, tls_bytes = struct.unpack_from('<II', binary, optional+112+9*8)
    index_address = struct.unpack_from('<Q', binary, pe.offset(pe.base+tls_rva)+16)[0]
    if tls_bytes != 40 or index_address != TLS_INDEX:
        raise ValueError('Exact PE TLS AddressOfIndex differs from the first native reader')
    return {'windows': windows, 'TLSdirectoryRVA': hex(tls_rva), 'TLSdirectoryBytes': tls_bytes,
            'addressOfIndex': hex(index_address), 'firstReader': hex(TLS_READER)}


def run_prefix(binary, header, mode, require_tls_stop):
    e = Offline72(binary, ROOT/'local/migration72/python', [], trace_instructions=True)
    e.prepare()
    klass = e.alloc(0xd0); e.uc.mem_write(klass, header)
    opaque_this = e.alloc(8)
    e.write(0x14586d70b, mode, 'B')
    e.allowed.append((0x1470687c0, 0x1470687e0))
    e.allow_unwind(0x14050a890); e.allow_unwind(0x14050ad90)
    trace, reads, stopped = [], [], []
    def before(address):
        trace.append(hex(address))
        if address == TLS_READER:
            stopped.append({'reason': 'unprovided-loader-TLS-index', 'reader': hex(address),
                            'address': hex(TLS_INDEX), 'bytes': 4, 'instructionExecuted': False})
            e.uc.emu_stop()
        elif address == 0x14050ad90:
            stopped.append({'reason': 'controlled-zero-mode-forward-target', 'entry': hex(address),
                            'instructionExecuted': False})
            e.uc.emu_stop()
    def memory(uc, access, address, width, value, context):
        if e.pe.base <= address < e.image_end:
            if (address, width) != (0x14586d70b, 1):
                e.failure = 'Unprovided runtime global read'; uc.emu_stop()
            else:
                reads.append({'address': hex(address), 'bytes': width, 'value': mode})
    e.before_instruction = before
    e.hooks.append(e.uc.hook_add(e.U.UC_HOOK_MEM_READ, memory))
    for name, value in dict(rsp=STACK+0x100008, rcx=opaque_this, rdx=klass, r8=0).items():
        e.uc.reg_write(getattr(e.R, 'UC_X86_REG_'+name.upper()), value)
    try:
        e.uc.emu_start(0x1470687c0, END, count=1000)
        expected = 'unprovided-loader-TLS-index' if require_tls_stop else 'controlled-zero-mode-forward-target'
        if e.failure or len(stopped) != 1 or stopped[0]['reason'] != expected or e.reg('rcx') != klass or e.calls:
            raise ValueError('Native Allocate prefix differs from its reviewed first-input boundary')
        return {'mode': mode, 'modeSource': 'fresh capture' if require_tls_stop else 'explicit independent zero-mode control',
                'stop': stopped[0], 'nativeTrace': trace, 'providedGlobalReads': reads,
                'classPointerPreserved64': klass > 0xffffffff and e.reg('rcx') == klass,
                'classHeaderConsumed': False, 'modelCalls': 0, 'allocatorReturned': False}
    finally:
        e.close()


if __name__ == '__main__':
    main()
