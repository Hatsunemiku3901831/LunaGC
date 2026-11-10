"""Owned-buffer capability preparation for two exact original clear48 paths."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
if not __package__:
    sys.path.insert(0, str(ROOT))
    __package__ = 'tools'
from .trace_client72_token import load_emulator

PRIVATE = 'local/migration72/d02-resume-20261110b/a-clear48-owned/'
SPEC = PRIVATE + 'implementation-spec.zh.md'
AUDIT = 'local/migration72/d02-resume-20261110b/a-second-gc64/contract-main-third.private.json'
CODE = 'local/migration72/d02-resume-20261110b/a-gc/gc-helper.code.bin'
PINS = {"local/migration72/python/capstone/__init__.py": "94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f",
        "local/migration72/python/capstone/lib/capstone.dll": "76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3",
        SPEC: '459dd74554696ec3c8f3d2e87d0c1f69b01738bc5ce9062c9845ebf2e2170fe8',
        AUDIT: '665a737fc7815d5468d8c424ed9f9be090191ff98c63d11576536e95638afe3a',
        CODE: '34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31',
        'tools/client72_d02_first_pop_copy.py': '5933bd3f7690959e0e5a65823a601831bae4d0afB03f66b5082bd448a04777c5'.lower(),
        'tools/trace_client72_token.py': 'f2fbc4728f73447dff4f7320beb3a7a31fb546287272b62ccd88a8e940312fa0'}
BASE, ENTRY, END = 0x140000000, 0x141fdf9c0, 0x141fdfd48
H, STACK, SP, SENTINEL = 0x30000000, 0x20000000, 0x20000808, 0x21000000
LIMIT, TIMEOUT_US = 5000, 5_000_000
LIBS = ROOT / 'local/migration72/python'
INITIAL = bytes(range(1, 65))


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def load_sources(root=ROOT):
    pins = {}
    for name, expected in PINS.items():
        raw = (root / name).read_bytes()
        if digest(raw) != expected:
            raise ValueError('Frozen source differs: ' + name)
        pins[name] = {'bytes': len(raw), 'sha256': digest(raw)}
    audit = json.loads((root / AUDIT).read_bytes())
    if len(audit['inputPins']) != 10:
        raise ValueError('Expected ten frozen GC64 inputs')
    for name, expected in audit['inputPins'].items():
        raw = (root / name).read_bytes()
        actual = {'bytes': len(raw), 'sha256': digest(raw)}
        if actual != expected:
            raise ValueError('GC64 audited source differs: ' + name)
        pins[name] = actual
    raw = (root / CODE).read_bytes()
    if len(raw) != END - ENTRY or len(raw) != 904:
        raise ValueError('Original helper length differs')
    return pins, raw


def case_data(name):
    i = (BASE + 0x56afdd0, struct.pack('<I', 2 if name == 'SSE' else 3), 0x141fdfa86)
    if name == 'SSE':
        return [i, (BASE + 0x56afdd8, struct.pack('<Q',48), 0x141fdfc70),
                (BASE + 0x6d19140, struct.pack('<I',0x01fdfd35), 0x141fdfcfd)]
    if name == 'AVX':
        return [i, (BASE + 0x56afdd8, struct.pack('<Q',47), 0x141fdfa93),
                (BASE + 0x56afde0, struct.pack('<Q',48), 0x141fdfa9c),
                (BASE + 0x5eeeb08, b'\0', 0x141fdfaa5),
                (BASE + 0x6d190f4, struct.pack('<I',0x01fdfb94), 0x141fdfb49)]
    raise ValueError('Unknown owned case')


def write_policy(name):
    if name == 'SSE':
        return {0x141fdfd35:(H+32,16),0x141fdfd3c:(H+48,16),0x141fdfd43:(H+16,16)}
    if name == 'AVX':
        return {0x141fdfb94:(H+32,32),0x141fdfb9b:(H+16,32)}
    raise ValueError('Unknown owned case')


def allowed_read(spans, pc, address, width):
    return width > 0 and any(pc == site and start <= address and address + width <= start + len(raw)
                             for start, raw, site in spans)


def allowed_write(policy, pc, address, width):
    if pc not in policy or width <= 0:
        return False
    start, size = policy[pc]
    return start <= address and address + width <= start + size and H+16 <= address and address+width <= H+64


def output_path(value):
    path = Path(value).resolve()
    if not path.is_relative_to((ROOT / PRIVATE).resolve()) or path.exists():
        raise ValueError('Output must be a new file within the private directory')
    return path


class OwnedCase:
    def __init__(self, name, code):
        self.name, self.controls, self.policy = name, case_data(name), write_policy(name)
        self.U, self.R = load_emulator(LIBS)
        import capstone
        from capstone import Cs, CS_ARCH_X86, CS_MODE_64, CS_AC_READ, CS_AC_WRITE
        from capstone.x86_const import X86_OP_MEM
        from importlib.metadata import version
        if (capstone.__version__ != '5.0.7' or version('capstone') != '5.0.9'
                or capstone.cs_version() != (5, 0, 1280)
                or Path(capstone.__file__).resolve() != (LIBS / 'capstone/__init__.py').resolve()):
            raise ValueError('Pinned Capstone distribution/binding/native identity differs')
        self.read_access, self.write_access, self.mem_operand = CS_AC_READ, CS_AC_WRITE, X86_OP_MEM
        self.uc = self.U.Uc(self.U.UC_ARCH_X86, self.U.UC_MODE_64)
        self.pages, self.reads, self.backend_writes, self.writes, self.path = set(), [], [], [], []
        self.failure, self.pending, self.ret = None, None, None
        self._map(ENTRY, code)
        self._map(H, INITIAL)
        self._map(SP, struct.pack('<Q', SENTINEL))
        self._map(SENTINEL, b'\xcc')
        for address, raw, _ in self.controls:
            self._map(address, raw)
        self.read_policy = self.controls + [(SP, struct.pack('<Q',SENTINEL), 0x141fdfd47 if name == 'SSE' else 0x141fdfba2)]
        decoder = Cs(CS_ARCH_X86, CS_MODE_64); decoder.detail = True
        ins = list(decoder.disasm(code, ENTRY))
        if b''.join(bytes(row.bytes) for row in ins) != code:
            raise ValueError('Original code does not decode continuously')
        self.instructions = {row.address: row for row in ins}
        self.uc.reg_write(self.R.UC_X86_REG_RSP, SP)
        self.uc.reg_write(self.R.UC_X86_REG_RCX, H+16)
        self.uc.reg_write(self.R.UC_X86_REG_RDX, 0)
        self.uc.reg_write(self.R.UC_X86_REG_R8, 48)
        self.hooks = [self.uc.hook_add(self.U.UC_HOOK_CODE, self._code),
                      self.uc.hook_add(self.U.UC_HOOK_MEM_READ, self._read),
                      self.uc.hook_add(self.U.UC_HOOK_MEM_WRITE, self._write),
                      self.uc.hook_add(self.U.UC_HOOK_MEM_INVALID, self._invalid)]

    def _map(self, address, data):
        for page in range(address & ~4095, (address + len(data) + 4095) & ~4095, 4096):
            if page not in self.pages:
                self.uc.mem_map(page,4096); self.pages.add(page)
        self.uc.mem_write(address,data)

    def reg(self, name):
        return self.uc.reg_read(getattr(self.R, 'UC_X86_REG_' + name.upper()))

    def _stop(self, reason, **detail):
        if self.failure is None:
            self.failure = {'reason':reason, 'pc':hex(self.reg('rip')), **detail}
        self.uc.emu_stop()

    def _flush(self, completion):
        if self.pending is not None:
            row = self.pending
            row['completion'] = completion
            row['newBytesHex'] = bytes(self.uc.mem_read(row['address'],row['width'])).hex()
            for event in row['backendEvents']:
                event['newBytesHex'] = bytes(self.uc.mem_read(event['address'],event['width'])).hex()
            self.writes.append(row); self.pending = None

    def _code(self, uc, address, width, _):
        self._flush('next-original-instruction')
        ins = self.instructions.get(address)
        if ins is None or width != ins.size or not ENTRY <= address < END or address + width > END:
            self._stop('instruction-outside-original-code', address=hex(address), width=width); return
        if ins.mnemonic in ('call','push','pop','syscall','sysenter','int','int3'):
            self._stop('unapproved-control-or-stack-instruction',instruction=ins.mnemonic); return
        for operand in ins.operands:
            if operand.type != self.mem_operand or ins.mnemonic == 'lea':
                continue
            mem = operand.mem; effective = mem.disp
            if mem.base:
                regname = ins.reg_name(mem.base)
                effective += address + width if regname == 'rip' else self.reg(regname)
            if mem.index:
                effective += self.reg(ins.reg_name(mem.index)) * mem.scale
            if mem.segment or not operand.access:
                self._stop('unknown-memory-operand', instruction=ins.mnemonic); return
            effective &= (1<<64)-1
            if operand.access & self.read_access and not allowed_read(self.read_policy,address,effective,operand.size):
                self._stop('read-outside-explicit-span', address=hex(effective), width=operand.size); return
            if operand.access & self.write_access:
                if self.policy.get(address) != (effective,operand.size):
                    self._stop('write-outside-exact-original-instruction',address=hex(effective),width=operand.size); return
                self.pending = {'pc':hex(address),'instructionBytesHex':bytes(ins.bytes).hex(),
                                'address':effective,'width':operand.size,
                                'oldBytesHex':bytes(uc.mem_read(effective,operand.size)).hex(),'backendEvents':[]}
        if ins.mnemonic == 'ret':
            if not allowed_read(self.read_policy,address,self.reg('rsp'),8):
                self._stop('return-stack-outside-owned-sentinel'); return
            actual = int.from_bytes(uc.mem_read(self.reg('rsp'),8),'little')
            if actual != SENTINEL:
                self._stop('return-sentinel-differs',actual=hex(actual)); return
            self.ret = {'pc':hex(address),'stackReturn':hex(actual),'rspBefore':hex(self.reg('rsp')),
                        'raxBefore':hex(self.reg('rax')),'originalRetExecuted':False}
        self.path.append({'pc':hex(address),'bytesHex':bytes(ins.bytes).hex(), 'mnemonic':ins.mnemonic})

    def _read(self, uc, access, address, width, value, _):
        pc = self.reg('rip')
        if not allowed_read(self.read_policy,pc,address,width):
            self._stop('unexpected-native-read',address=hex(address),width=width); return
        self.reads.append({'pc':hex(pc),'address':address,'width':width,
                           'bytesHex':bytes(uc.mem_read(address,width)).hex()})

    def _write(self, uc, access, address, width, value, _):
        pc = self.reg('rip')
        if self.pending is None or not allowed_write(self.policy,pc,address,width):
            self._stop('unexpected-native-write',address=hex(address),width=width); return
        event = {'pc':hex(pc),'address':address,'width':width,
                 'backendValueHex':hex(value & ((1 << min(width*8,64))-1)),
                 'oldBytesHex':bytes(uc.mem_read(address,width)).hex()}
        self.pending['backendEvents'].append(event); self.backend_writes.append(event)

    def _invalid(self, uc, access, address, width, value, _):
        self._stop('unmapped-or-protected-access',access=access,address=hex(address),width=width)
        return False

    def run(self):
        error = None
        try:
            self.uc.emu_start(ENTRY,SENTINEL,timeout=TIMEOUT_US,count=LIMIT)
        except Exception as ex:
            error = {'type':type(ex).__name__,'message':str(ex)}
        rip = self.reg('rip')
        returned = rip == SENTINEL and self.failure is None and error is None
        self._flush('returned-to-owned-sentinel' if returned else 'interrupted-or-backend-failure')
        if self.ret is not None:
            self.ret['originalRetExecuted'] = returned
        if not returned and self.failure is None and error is None:
            self.failure = {'reason':'instruction-limit-or-timeout-or-unexpected-stop','pc':hex(rip)}
        final = bytes(self.uc.mem_read(H,64))
        return {'case':self.name,'artificialControls':[{'address':a,'bytesHex':raw.hex(),'readerPC':hex(pc)} for a,raw,pc in self.controls],
                'entry':hex(ENTRY),'ownedH':hex(H),'ownedStackPointer':hex(SP),'ownedSentinel':hex(SENTINEL),
                'instructionLimit':LIMIT,'timeoutMicroseconds':TIMEOUT_US,'path':self.path,'reads':self.reads,
                'logicalWrites':self.writes,'backendWrites':self.backend_writes,'originalRet':self.ret,
                'initialOwnedBufferHex':INITIAL.hex(),'finalOwnedBufferHex':final.hex(),
                'rax':hex(self.reg('rax')),'finalRSP':hex(self.reg('rsp')),'stopPC':hex(rip),
                'returned':returned,'failure':self.failure,'backendError':error}


def verify_execution(result):
    name = result['case']; policy = write_policy(name)
    events = result['logicalWrites']; expected = list(policy.items())
    if (not result['returned'] or result['failure'] is not None or result['backendError'] is not None
            or result['rax'] != hex(H+16) or result['finalRSP'] != hex(SP+8)
            or result['originalRet'] is None or not result['originalRet']['originalRetExecuted']
            or result['originalRet']['pc'] != hex(0x141fdfd47 if name=='SSE' else 0x141fdfba2)
            or result['originalRet']['stackReturn'] != hex(SENTINEL)
            or result['originalRet']['rspBefore'] != hex(SP) or result['originalRet']['raxBefore'] != hex(H+16)
            or len(result['path']) > LIMIT or len(events) != len(expected)):
        raise ValueError('Original return, execution or write count differs')
    image = bytearray(INITIAL)
    for event,(pc,(address,width)) in zip(events,expected):
        offset=address-H
        if (event['pc'] != hex(pc) or event['address'] != address or event['width'] != width
                or event['completion'] not in ('next-original-instruction','returned-to-owned-sentinel')
                or bytes.fromhex(event['oldBytesHex']) != image[offset:offset+width]
                or bytes.fromhex(event['newBytesHex']) != bytes(width)):
            raise ValueError('Original logical write event differs')
        coverage=set()
        for sub in event['backendEvents']:
            if sub['pc'] != event['pc'] or not allowed_write(policy,pc,sub['address'],sub['width']):
                raise ValueError('Backend split event outside logical write')
            coverage.update(range(sub['address'],sub['address']+sub['width']))
        if sum(sub['width'] for sub in event['backendEvents']) != width or coverage != set(range(address,address+width)):
            raise ValueError('Backend split events do not cover logical write')
        image[offset:offset+width]=bytes(width)
    if bytes.fromhex(result['finalOwnedBufferHex']) != bytes(image) or image[:16] != INITIAL[:16]:
        raise ValueError('Owned buffer inspector differs')
    reads=result['reads']; spans=case_data(name)+[(SP,struct.pack('<Q',SENTINEL),0x141fdfd47 if name=='SSE' else 0x141fdfba2)]
    for address, raw, pc in spans:
        matching=[r for r in reads if r['pc']==hex(pc) and address<=r['address'] and r['address']+r['width']<=address+len(raw)]
        if (sum(r['width'] for r in matching) != len(raw)
                or set(i for r in matching for i in range(r['address'],r['address']+r['width'])) != set(range(address,address+len(raw)))
                or any(bytes.fromhex(r['bytesHex']) != raw[r['address']-address:r['address']-address+r['width']] for r in matching)):
            raise ValueError('Native required read missing or differs')
    return {'originalReturnAndRAXVerified':True,'ownedPrefixUnchanged':True,'ownedClear48Verified':True,
            'exactLogicalWriteOrderVerified':True,'backendSplitCoverageVerified':True,'requiredReadsVerified':True}


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',required=True)
    args=parser.parse_args(argv); output=output_path(args.output)
    report={'task':'F-03d3f4d2/CLEAR48-OWNED','passed':False,'cases':[],
            'permissions':{'capturedCopyWriteApproved':False,'naturalGC':False,'completeAvatar':False,
                           'd02Complete':False,'liveAPIInvoked':False,'targetProcessWrite':False,
                           'ownedBufferWriteScope':{'address':hex(H+16),'bytes':48}}}
    with output.open('x',encoding='utf8',newline='\n') as stream:
        before = implementation = local_sources = None
        try:
            before, code=load_sources()
            report['sourcesSHA256']=before
            local_sources = [Path(__file__), ROOT / 'tools/test_client72_d02_clear48_owned.py']
            implementation = {str(path.relative_to(ROOT)): digest(path.read_bytes()) for path in local_sources}
            report['implementationAndTestSHA256']=implementation
            report['nativeEmulatorExecutionAttempted']=False
            for name in ('SSE','AVX'):
                runner=OwnedCase(name,code)
                report['nativeEmulatorExecutionAttempted']=True
                case=runner.run(); report['cases'].append(case)
                case['checks']=verify_execution(case)
            report['passed']=True
        except Exception as error:
            report['error']={'type':type(error).__name__,'message':str(error)}
        finally:
            if before is not None:
                try:
                    after, _ = load_sources()
                    unchanged = before == after and implementation == {str(path.relative_to(ROOT)): digest(path.read_bytes()) for path in local_sources}
                    report['sourceStableDuringRun'] = unchanged
                    if not unchanged:
                        report.update(passed=False,sourceRecheckError='Frozen sources changed during execution')
                except Exception as error:
                    report.update(passed=False,sourceStableDuringRun=False,
                                  sourceRecheckError={'type':type(error).__name__,'message':str(error)})
            json.dump(report,stream,ensure_ascii=False,sort_keys=True,indent=2);stream.write('\n')
    print(json.dumps({'passed':report['passed'],'output':str(output),'error':report.get('error')}))
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
