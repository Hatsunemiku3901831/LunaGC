"""Bounded existing-cache preparation; never run against a target without new authorization."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
import signal
from pathlib import Path
import struct
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from observe_client72_uninstrumented import MODULEENTRY32W, verified_inputs, sha256

PRIVATE = ROOT/'local/migration72/d02-resume-20261111c/proud-existing-cache-input-prepare'
SPEC = PRIVATE/'specification-v3r1.private.json'
SPEC_SHA = 'ab06e108985bdbc084bda33123ee0a89010c1d77f77a22b47ad4ee50340caa10'
PHASE_SHA = 'b66688652e923c72861701218b3c9f98e527e6a98d5aba896ec5326500c7b164'
LIMITS = {
 'identity': {'OpenProcess':1, 'GetProcessTimes':1, 'QueryFullProcessImageNameW':1,
              'WaitForSingleObject':2, 'GetExitCodeProcess':2, 'CreateToolhelp32Snapshot':1,
              'Module32FirstW':1, 'CloseSnapshot':1, 'CloseProcess':1},
 'sample': {'OpenProcess':1, 'GetProcessTimes':2, 'QueryFullProcessImageNameW':2,
            'WaitForSingleObject':2, 'GetExitCodeProcess':2, 'CreateToolhelp32Snapshot':2,
            'Module32FirstW':2, 'CloseSnapshot':2, 'CloseProcess':1, 'ReadProcessMemory':52}}

class Stop(Exception):
    pass

class Ledger:
    def __init__(self, stage, deadline, cancelled=lambda: False):
        self.stage, self.deadline, self.cancelled = stage, deadline, cancelled
        self.calls, self.records = {}, []
    def call(self, name, fn, *args, mandatory=False):
        if not mandatory and (self.cancelled() or time.monotonic() >= self.deadline):
            raise Stop('cancelled' if self.cancelled() else 'deadline')
        n = self.calls.get(name, 0)
        if n >= LIMITS[self.stage].get(name, 0):
            raise Stop('api-budget-' + name)
        self.calls[name] = n + 1
        record = {'api':name}
        self.records.append(record)
        try:
            result = fn(*args)
            record['returned'] = (bool(result) if name.startswith('Close') else
                                  result[1] is None if name == 'ReadProcessMemory' else True)
            return result
        except Exception as exc:
            record.update(returned=False, error=type(exc).__name__, detail=str(exc))
            raise

class Reader:
    def __init__(self, read, deadline, cancelled=lambda: False):
        self.read, self.deadline, self.cancelled = read, deadline, cancelled
        self.records = []
    def get(self, name, address, width):
        if self.cancelled() or time.monotonic() >= self.deadline:
            raise Stop('cancelled' if self.cancelled() else 'deadline')
        if len(self.records) >= 52 or sum(x['requested'] for x in self.records) + width > 298:
            raise Stop('rpm-budget')
        if address <= 0 or address + width > 1 << 64:
            raise Stop('address-outside-u64')
        rec = {'name':name, 'address':address, 'requested':width, 'returned':0, 'hex':'', 'error':None}
        self.records.append(rec)
        try:
            data, error = self.read(address, width)
        except Stop:
            self.records.pop()  # Ledger stopped before invoking ReadProcessMemory.
            raise
        except OSError as exc:
            rec['error'] = {'type':type(exc).__name__, 'winerror':getattr(exc, 'winerror', None)}
            raise Stop('read-error') from exc
        rec.update(returned=len(data), hex=data.hex(), error=error)
        if error is not None or len(data) != width:
            if error is None: rec['error'] = 'short-read'
            raise Stop('read-error' if error is not None else 'short-read')
        return data
    def number(self, name, address, width, signed=False):
        return int.from_bytes(self.get(name, address, width), 'little', signed=signed)


def check_index(index, entry_len):
    if index == 0xffffffff: raise Stop('empty-chain')
    if index & 0x80000000: raise Stop('negative-index')
    if index >= entry_len: raise Stop('positive-index-out-of-range')

def collect(reader, base):
    result = {'status':'incomplete', 'twoReadSetsEqual':False, 'actualNativeGetterReturn':None,
              'currentAdoption':None, 'actualRow':False, 'resourceReady':False,
              'atomicSnapshot':False, 'nativeLookup':False}
    try:
        flag = reader.number('backend', base+0x587c2c0, 1)
        if flag != 0: raise Stop('unsupported-backend')
        root = reader.number('root', base+0x586b600, 8)
        if not root: raise Stop('cold-root')
        cache = reader.number('cache', root+0x21f60, 8)
        if not cache: raise Stop('cold-cache')
        entries, buckets, comparer = struct.unpack('<QQQ', reader.get('cache-pointers', cache+0x18, 24))
        if comparer: raise Stop('unsupported-comparer')
        if not buckets: raise Stop('cold-buckets')
        if not entries: raise Stop('cold-entries')
        bucket_len = reader.number('bucket-length', buckets+0x18, 4, signed=True)
        if bucket_len <= 0: raise Stop('unsupported-bucket-length')
        entry_len = reader.number('entry-length', entries+0x18, 4, signed=True)
        if entry_len < 0: raise Stop('unsupported-entry-length')
        bucket = reader.number('bucket', buckets+0x20+4*(22101 % bucket_len), 4)
        index = (bucket - 1) & 0xffffffff
        native_budget = max(entry_len, 0) + 1
        for n in range(4):
            check_index(index, entry_len)
            address = entries+0x20+24*index
            entry_hash, next_index, key = struct.unpack('<IiI', reader.get('entry', address, 12))
            if entry_hash == 22101 and key == 22101:
                p = reader.number('P', address+0x10, 8)
                if not p: raise Stop('null-P')
                managed = reader.number('managed', p+0x40, 8)
                if not managed: raise Stop('managed-representation-null')
                field84 = reader.number('field203373-encoded', managed+0x84, 4)
                field90 = reader.number('field203376-encoded', managed+0x90, 4)
                result['observedEncoded'] = {'field203373':field84, 'field203376':field90}
                table = reader.number('inverse-table', root+0xe970, 8)
                result['observedCodecInputs'] = {'table':table}
                if not table: raise Stop('null-inverse-table')
                length = reader.number('inverse-length', table+0x18, 4)
                result['observedCodecInputs']['length'] = length
                seed = reader.number('seed', base+0x5872d30, 4)
                result['observedCodecInputs'].update(seed=seed, fields={})
                partial = {}
                for name, encoded in result['observedEncoded'].items():
                    q = encoded ^ seed
                    indices = [0x400 | (q >> 24), 0x500 | ((q >> 16) & 255),
                               0x600 | ((q >> 8) & 255), 0x700 | (q & 255)]
                    field = {'indices':indices, 'inverseBytes':[]}
                    result['observedCodecInputs']['fields'][name] = field
                    if any(index >= length for index in indices): raise Stop('inverse-index-out-of-range')
                    for index in indices:
                        field['inverseBytes'].append(reader.number('inverse-'+name, table+0x20+index, 1))
                    partial[name] = int.from_bytes(bytes(field['inverseBytes']), 'big')
                    result['partialConditionalGetterU32'] = dict(partial)
                first = list(reader.records)
                for rec in first:
                    data = reader.get('replay-'+rec['name'], rec['address'], rec['requested'])
                    if data.hex() != rec['hex']: raise Stop('read-set-changed')
                result.update(status='completed', twoReadSetsEqual=True,
                              conditionalGetterU32=partial)
                break
            native_budget -= 1
            if native_budget == 0: raise Stop('native-loop-budget-exhausted')
            index = next_index & 0xffffffff
            check_index(index, entry_len)
            if n == 3: raise Stop('collision-cap')
    except Stop as exc:
        result['status'] = str(exc)
    result.update(records=reader.records, reads=len(reader.records),
                  requestedBytes=sum(x['requested'] for x in reader.records),
                  returnedBytes=sum(x['returned'] for x in reader.records))
    return result

class WinAPI:
    def __init__(self):
        self.k = C.WinDLL('kernel32', use_last_error=True)
        signatures = {'OpenProcess':([W.DWORD,W.BOOL,W.DWORD],W.HANDLE),
          'GetProcessTimes':([W.HANDLE,C.POINTER(W.FILETIME),C.POINTER(W.FILETIME),C.POINTER(W.FILETIME),C.POINTER(W.FILETIME)],W.BOOL),
          'QueryFullProcessImageNameW':([W.HANDLE,W.DWORD,W.LPWSTR,C.POINTER(W.DWORD)],W.BOOL),
          'WaitForSingleObject':([W.HANDLE,W.DWORD],W.DWORD),
          'GetExitCodeProcess':([W.HANDLE,C.POINTER(W.DWORD)],W.BOOL),
          'CreateToolhelp32Snapshot':([W.DWORD,W.DWORD],W.HANDLE),
          'Module32FirstW':([W.HANDLE,C.POINTER(MODULEENTRY32W)],W.BOOL),
          'CloseHandle':([W.HANDLE],W.BOOL),
          'ReadProcessMemory':([W.HANDLE,C.c_void_p,C.c_void_p,C.c_size_t,C.POINTER(C.c_size_t)],W.BOOL)}
        for name,(args,ret) in signatures.items():
            fn=getattr(self.k,name); fn.argtypes=args; fn.restype=ret
    def open(self, rights, pid):
        h=self.k.OpenProcess(rights,False,pid)
        if not h: raise C.WinError(C.get_last_error())
        return h
    def times(self,h):
        a,b,c,d=(W.FILETIME() for _ in range(4))
        if not self.k.GetProcessTimes(h,C.byref(a),C.byref(b),C.byref(c),C.byref(d)): raise C.WinError(C.get_last_error())
        return (a.dwHighDateTime<<32)|a.dwLowDateTime
    def image(self,h):
        buf=C.create_unicode_buffer(32768); size=W.DWORD(len(buf))
        if not self.k.QueryFullProcessImageNameW(h,0,buf,C.byref(size)): raise C.WinError(C.get_last_error())
        return buf.value
    def wait(self,h):
        v=self.k.WaitForSingleObject(h,0)
        if v not in (0,258): raise C.WinError(C.get_last_error())
        return v
    def exitcode(self,h):
        v=W.DWORD()
        if not self.k.GetExitCodeProcess(h,C.byref(v)): raise C.WinError(C.get_last_error())
        return v.value
    def snapshot(self,pid):
        h=self.k.CreateToolhelp32Snapshot(8|16,pid)
        if h==C.c_void_p(-1).value: raise C.WinError(C.get_last_error())
        return h
    def first(self,h):
        v=MODULEENTRY32W(); v.dwSize=C.sizeof(v)
        if not self.k.Module32FirstW(h,C.byref(v)): raise C.WinError(C.get_last_error())
        return {'name':v.szModule,'path':v.szExePath,'base':v.modBaseAddr,'size':v.modBaseSize}
    def close(self,h): return bool(self.k.CloseHandle(h))
    def read(self,h,address,width):
        b=C.create_string_buffer(width); n=C.c_size_t()
        ok=self.k.ReadProcessMemory(h,address,b,width,C.byref(n))
        return b.raw[:n.value], None if ok else {'winerror':C.get_last_error()}


def live_status(api, ledger, h, mandatory=False):
    wait=ledger.call('WaitForSingleObject',api.wait,h,mandatory=mandatory)
    code=ledger.call('GetExitCodeProcess',api.exitcode,h,mandatory=mandatory)
    if wait != 258 or code != 259: raise Stop('process-not-alive')
    return {'wait':wait,'exitCode':code}

def module(api, ledger, pid, target, image_size, mandatory=False):
    h=ledger.call('CreateToolhelp32Snapshot',api.snapshot,pid,mandatory=mandatory)
    closed=False
    try:
        row=ledger.call('Module32FirstW',api.first,h,mandatory=mandatory)
        if row['name'].casefold()!='yuanshen.exe' or Path(row['path']).resolve()!=target or row['size']!=image_size or not row['base']:
            raise Stop('module-identity-mismatch')
        return row
    finally:
        closed=ledger.call('CloseSnapshot',api.close,h,mandatory=True)
        if not closed: raise Stop('snapshot-close-failed')

def identity(api, pid, target, image_size, ledger):
    h=ledger.call('OpenProcess',api.open,0x101000,pid)
    try:
        creation=ledger.call('GetProcessTimes',api.times,h)
        image=ledger.call('QueryFullProcessImageNameW',api.image,h)
        if Path(image).resolve()!=target: raise Stop('image-identity-mismatch')
        live_status(api,ledger,h)
        mod=module(api,ledger,pid,target,image_size)
        live_status(api,ledger,h)
        return {'creation':creation,'image':image,'module':mod}
    finally:
        if not ledger.call('CloseProcess',api.close,h,mandatory=True): raise Stop('process-close-failed')

def sample(api, pid, target, image_size, expected, ledger):
    h=ledger.call('OpenProcess',api.open,0x101010,pid)
    result={'status':'precheck-failed'}; post_error=None
    try:
        try:
            creation=ledger.call('GetProcessTimes',api.times,h)
            image=ledger.call('QueryFullProcessImageNameW',api.image,h)
            live_status(api,ledger,h)
            mod=module(api,ledger,pid,target,image_size)
            if creation!=expected['creation'] or Path(image).resolve()!=target or mod!=expected['module']:
                raise Stop('identity-epoch-mismatch')
            reader=Reader(lambda a,w: ledger.call('ReadProcessMemory',api.read,h,a,w),ledger.deadline,ledger.cancelled)
            result=collect(reader,mod['base'])
        except (Stop,OSError) as exc:
            result={'status':str(exc),'errorType':type(exc).__name__}
        finally:
            try:
                creation2=ledger.call('GetProcessTimes',api.times,h,mandatory=True)
                image2=ledger.call('QueryFullProcessImageNameW',api.image,h,mandatory=True)
                live_status(api,ledger,h,mandatory=True)
                mod2=module(api,ledger,pid,target,image_size,mandatory=True)
                if creation2!=expected['creation'] or Path(image2).resolve()!=target or mod2!=expected['module']:
                    raise Stop('post-identity-epoch-mismatch')
            except (Stop,OSError) as exc:
                post_error={'type':type(exc).__name__,'detail':str(exc)}
        if post_error:
            result['postCheckError']=post_error
            result.pop('conditionalGetterU32',None)
            result['status']='postcheck-failed'
        return result
    finally:
        if not ledger.call('CloseProcess',api.close,h,mandatory=True):
            result.pop('conditionalGetterU32',None)
            result['status']='process-close-failed'
            result['cleanupObserved']=False
        elif result['status']=='completed':
            if ledger.cancelled():
                result.pop('conditionalGetterU32',None)
                result['status']='cancelled'
            elif time.monotonic() >= ledger.deadline:
                result.pop('conditionalGetterU32',None)
                result['status']='deadline'


def preflight():
    if sha256(SPEC)!=SPEC_SHA: raise ValueError('spec pin mismatch')
    spec=json.loads(SPEC.read_bytes()); phase=PRIVATE/'phase-a-pins.private.json'
    if sha256(phase)!=PHASE_SHA: raise ValueError('phase-A pin mismatch')
    for item in json.loads(phase.read_bytes())['pins']:
        p=ROOT/item['path']
        if p.stat().st_size!=item['bytes'] or sha256(p)!=item['sha256']: raise ValueError('source pin mismatch: '+item['path'])
    for name,digest in {**spec['additionalDocumentPins'], **spec['getterSourcePins']}.items():
        if sha256(ROOT/name)!=digest: raise ValueError('document pin mismatch: '+name)
    game=Path(subprocess.check_output(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'tools/game_path.ps1')],text=True,encoding='utf-8').strip()).resolve()
    inputs=verified_inputs(ROOT,game); target=(game/'YuanShen.exe').resolve()
    if (game/inputs['files']['binary']['path']).resolve()!=target: raise ValueError('profile binary mismatch')
    with target.open('rb') as f: head=f.read(8192)
    if len(head)<64 or head[:2]!=b'MZ': raise ValueError('PE DOS header')
    off=struct.unpack_from('<I',head,0x3c)[0]
    if off+84>len(head) or head[off:off+4]!=b'PE\0\0' or struct.unpack_from('<H',head,off+4)[0]!=0x8664 or struct.unpack_from('<H',head,off+24)[0]!=0x20b: raise ValueError('PE header bounds or type')
    size=struct.unpack_from('<I',head,off+24+56)[0]
    if not size: raise ValueError('PE image size')
    return target,size,inputs,{'headerBytes':len(head),'stockHashIO':'separate','sourceHashIO':'separate'}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pid',type=int,required=True); parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--seconds',type=int,required=True); parser.add_argument('--user-ready',action='store_true')
    args=parser.parse_args(); output=args.output.resolve()
    if args.pid<=0 or not args.user_ready or not 1<=args.seconds<=300 or not output.is_relative_to(PRIVATE.resolve()) or output==PRIVATE.resolve() or output.exists(): parser.error('explicit PID, new ready, seconds 1..300 and new private output required')
    result={'status':'preflight','specSHA256':SPEC_SHA,'toolSHA256':sha256(Path(__file__)), 'timeUtc':datetime.now(timezone.utc).isoformat()}
    deadline=time.monotonic()+args.seconds
    cancellation={'requested':False}
    def cancel(_signum, _frame): cancellation['requested']=True
    prior_handler=signal.signal(signal.SIGINT,cancel)
    try:
        target,size,inputs,io=preflight(); result.update(inputChecks=inputs,fileIO=io)
        if cancellation['requested']: raise Stop('cancelled')
        if time.monotonic()>=deadline: raise Stop('deadline')
        api=WinAPI(); i=Ledger('identity',deadline,lambda: cancellation['requested']); s=Ledger('sample',deadline,lambda: cancellation['requested'])
        try:
            observed=identity(api,args.pid,target,size,i); result['identity']=observed
            result['sample']=sample(api,args.pid,target,size,observed,s); result['status']=result['sample']['status']
        finally:
            result['apiLedger']={'identity':i.records,'sample':s.records,'identityCounts':i.calls,'sampleCounts':s.calls}
    except (Stop,OSError,ValueError,KeyError) as exc:
        result.update(status='error',errorType=type(exc).__name__,error=str(exc))
    finally:
        signal.signal(signal.SIGINT,prior_handler)
    with output.open('x',encoding='utf-8') as f: json.dump(result,f,indent=2,ensure_ascii=False); f.write('\n')
    print(json.dumps({'status':result['status']})); return 0 if result['status']=='completed' else 1
if __name__=='__main__': sys.exit(main())
