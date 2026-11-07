"""One bounded, uninstrumented CN72 startup. Raw evidence remains in local/."""
import argparse
import ctypes as C
from ctypes import wintypes as W
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PROFILE = ROOT / 'protocol/cn-7.2/profile.json'
EXPECTED = {
    'binary': '8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5',
    'metadata': '45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a',
    'startup': 'b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105',
    'sdk': 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25',
    'astrolabe': '87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442',
}
CANDIDATES = ('output_log.txt', 'YuanShen_Data/output_log.txt', 'config.ini', 'YuanShen_Data/Persistent/audio_revision',
              'YuanShen_Data/Persistent/res_revision_eternal',
              'YuanShen_Data/Persistent/ScriptVersion',
              'YuanShen_Data/StreamingAssets/AssetBundles/data_revision',
              'YuanShen_Data/StreamingAssets/AssetBundles/res_revision',
              'YuanShen_Data/StreamingAssets/AssetBundles/silence_revision')


def sha256(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def file_state(path):
    try:
        st = path.stat()
        return dict(exists=True, size=st.st_size, mtimeNs=st.st_mtime_ns, sha256=sha256(path))
    except FileNotFoundError:
        return dict(exists=False)
    except OSError as exc:
        return dict(error=str(exc))


def verified_inputs(root, game):
    profile = json.loads((root / 'protocol/cn-7.2/profile.json').read_text(encoding='utf-8'))
    files = {}
    for name in ('binary', 'metadata', 'startup', 'sdk', 'astrolabe'):
        item = profile['files'][name]
        actual = sha256(game / item['path'])
        if item['sha256'] != EXPECTED[name] or actual != EXPECTED[name]:
            raise ValueError('Client profile hash mismatch: ' + name)
        files[name] = dict(path=item['path'], sha256=actual)
    return dict(profileSHA256=sha256(root/'protocol/cn-7.2/profile.json'), files=files,
                config={name: file_state(root/name) for name in ('.env', '.env.local', 'config.json')})


def interpretation(status, length, value):
    if status == 0 and length == 1 and value in (0, 1):
        return bool(value)
    return None


class PROCESSENTRY32W(C.Structure):
    _fields_ = [('dwSize', W.DWORD), ('cntUsage', W.DWORD), ('th32ProcessID', W.DWORD),
                ('th32DefaultHeapID', C.c_size_t), ('th32ModuleID', W.DWORD), ('cntThreads', W.DWORD),
                ('th32ParentProcessID', W.DWORD), ('pcPriClassBase', C.c_long), ('dwFlags', W.DWORD),
                ('szExeFile', W.WCHAR * 260)]


class THREADENTRY32(C.Structure):
    _fields_ = [('dwSize', W.DWORD), ('cntUsage', W.DWORD), ('th32ThreadID', W.DWORD),
                ('th32OwnerProcessID', W.DWORD), ('tpBasePri', C.c_long), ('tpDeltaPri', C.c_long), ('dwFlags', W.DWORD)]


class MODULEENTRY32W(C.Structure):
    _fields_ = [('dwSize', W.DWORD), ('th32ModuleID', W.DWORD), ('th32ProcessID', W.DWORD),
                ('GlblcntUsage', W.DWORD), ('ProccntUsage', W.DWORD), ('modBaseAddr', C.c_void_p),
                ('modBaseSize', W.DWORD), ('hModule', W.HMODULE), ('szModule', W.WCHAR * 256), ('szExePath', W.WCHAR * 260)]


class STARTUPINFOW(C.Structure):
    _fields_ = [('cb', W.DWORD), ('lpReserved', W.LPWSTR), ('lpDesktop', W.LPWSTR), ('lpTitle', W.LPWSTR),
                ('dwX', W.DWORD), ('dwY', W.DWORD), ('dwXSize', W.DWORD), ('dwYSize', W.DWORD),
                ('dwXCountChars', W.DWORD), ('dwYCountChars', W.DWORD), ('dwFillAttribute', W.DWORD),
                ('dwFlags', W.DWORD), ('wShowWindow', W.WORD), ('cbReserved2', W.WORD),
                ('lpReserved2', C.c_void_p), ('hStdInput', W.HANDLE), ('hStdOutput', W.HANDLE), ('hStdError', W.HANDLE)]


class PROCESS_INFORMATION(C.Structure):
    _fields_ = [('hProcess', W.HANDLE), ('hThread', W.HANDLE), ('dwProcessId', W.DWORD), ('dwThreadId', W.DWORD)]


class WindowsBackend:
    def __init__(self):
        self.k = C.WinDLL('kernel32', use_last_error=True)
        self.k.CreateToolhelp32Snapshot.argtypes = [W.DWORD, W.DWORD]; self.k.CreateToolhelp32Snapshot.restype = W.HANDLE
        self.k.CloseHandle.argtypes = [W.HANDLE]; self.k.CloseHandle.restype = W.BOOL
        for name, struct in [('Process32FirstW', PROCESSENTRY32W), ('Process32NextW', PROCESSENTRY32W),
                             ('Thread32First', THREADENTRY32), ('Thread32Next', THREADENTRY32),
                             ('Module32FirstW', MODULEENTRY32W), ('Module32NextW', MODULEENTRY32W)]:
            fn = getattr(self.k, name); fn.argtypes = [W.HANDLE, C.POINTER(struct)]; fn.restype = W.BOOL
        self.k.CreateProcessW.argtypes = [W.LPCWSTR, W.LPWSTR, C.c_void_p, C.c_void_p, W.BOOL, W.DWORD,
                                         C.c_void_p, W.LPCWSTR, C.POINTER(STARTUPINFOW), C.POINTER(PROCESS_INFORMATION)]
        self.k.CreateProcessW.restype = W.BOOL
        self.k.WaitForSingleObject.argtypes = [W.HANDLE, W.DWORD]; self.k.WaitForSingleObject.restype = W.DWORD
        self.k.GetExitCodeProcess.argtypes = [W.HANDLE, C.POINTER(W.DWORD)]; self.k.GetExitCodeProcess.restype = W.BOOL
        self.k.GetProcessTimes.argtypes = [W.HANDLE, C.POINTER(W.FILETIME), C.POINTER(W.FILETIME), C.POINTER(W.FILETIME), C.POINTER(W.FILETIME)]
        self.k.GetProcessTimes.restype = W.BOOL
        self.k.QueryFullProcessImageNameW.argtypes = [W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD)]; self.k.QueryFullProcessImageNameW.restype = W.BOOL
        self.k.OpenThread.argtypes = [W.DWORD, W.BOOL, W.DWORD]; self.k.OpenThread.restype = W.HANDLE
        self.k.OpenProcess.argtypes = [W.DWORD, W.BOOL, W.DWORD]; self.k.OpenProcess.restype = W.HANDLE
        self.k.GetProcessIdOfThread.argtypes = [W.HANDLE]; self.k.GetProcessIdOfThread.restype = W.DWORD
        self.k.GetCurrentThread.restype = W.HANDLE
        self.nt = getattr(C.WinDLL('ntdll'), 'NtQueryInformationThread', None)
        if self.nt:
            self.nt.argtypes = [W.HANDLE, W.ULONG, C.c_void_p, W.ULONG, C.POINTER(W.ULONG)]
            self.nt.restype = C.c_long

    def snapshot(self, flags, pid, struct, first, next_):
        handle = self.k.CreateToolhelp32Snapshot(flags, pid)
        if handle == C.c_void_p(-1).value:
            return dict(error=C.get_last_error(), items=[])
        try:
            item = struct(); item.dwSize = C.sizeof(item); rows = []
            ok = getattr(self.k, first)(handle, C.byref(item))
            if not ok:
                error = C.get_last_error()
                return dict(error=None if error == 18 else error, items=rows)
            while ok:
                if struct is PROCESSENTRY32W:
                    rows.append(dict(pid=item.th32ProcessID, name=item.szExeFile))
                elif struct is THREADENTRY32:
                    if item.th32OwnerProcessID == pid:
                        rows.append(dict(tid=item.th32ThreadID, ownerPid=item.th32OwnerProcessID))
                else:
                    rows.append(dict(name=item.szModule, path=item.szExePath, base=item.modBaseAddr, size=item.modBaseSize))
                ok = getattr(self.k, next_)(handle, C.byref(item))
            error = C.get_last_error()
            return dict(error=None if error == 18 else error, items=rows)
        finally:
            self.k.CloseHandle(handle)

    def processes(self, game):
        result = self.snapshot(2, 0, PROCESSENTRY32W, 'Process32FirstW', 'Process32NextW')
        related = []; inaccessible = 0
        for row in result['items']:
            if row['name'].lower() in ('yuanshen.exe', 'genshinimpact.exe'):
                related.append(row); continue
            handle = self.k.OpenProcess(0x1000, False, row['pid'])
            if not handle:
                inaccessible += 1
                continue
            try:
                try:
                    image = Path(self.image(handle)).resolve()
                except OSError:
                    inaccessible += 1
                    continue
                if image.is_relative_to(game):
                    related.append(row)
            finally:
                self.k.CloseHandle(handle)
        return dict(error=result['error'], items=related, inaccessiblePaths=inaccessible)

    def launch(self, target, cwd):
        startup = STARTUPINFOW(); startup.cb = C.sizeof(startup)
        info = PROCESS_INFORMATION(); command = C.create_unicode_buffer('"' + str(target) + '"')
        if not self.k.CreateProcessW(str(target), command, None, None, False, 0, None, str(cwd), C.byref(startup), C.byref(info)):
            raise C.WinError(C.get_last_error())
        self.k.CloseHandle(info.hThread)
        return info.hProcess, info.dwProcessId

    def status(self, handle):
        wait = self.k.WaitForSingleObject(handle, 0)
        code = W.DWORD()
        if wait not in (0, 258) or not self.k.GetExitCodeProcess(handle, C.byref(code)):
            raise C.WinError(C.get_last_error())
        return dict(alive=wait == 258, signaled=wait == 0, exitCode=code.value)

    def image(self, handle):
        buf = C.create_unicode_buffer(32768); size = W.DWORD(len(buf))
        if not self.k.QueryFullProcessImageNameW(handle, 0, buf, C.byref(size)):
            raise C.WinError(C.get_last_error())
        return buf.value

    def creation_time(self, handle):
        creation, exit_, kernel, user = (W.FILETIME() for _ in range(4))
        if not self.k.GetProcessTimes(handle, C.byref(creation), C.byref(exit_), C.byref(kernel), C.byref(user)):
            return dict(filetime=None, winerror=C.get_last_error())
        return dict(filetime=(creation.dwHighDateTime << 32) | creation.dwLowDateTime, winerror=None)

    def query(self, handle):
        if not self.nt:
            return dict(status=None, length=None, raw=None, value=None, error='unavailable')
        value = C.c_ubyte(255); length = W.ULONG(0)
        status = self.nt(handle, 0x11, C.byref(value), 1, C.byref(length)) & 0xffffffff
        return dict(status=status, length=length.value, raw=value.value,
                    value=interpretation(status, length.value, value.value))

    def self_probe(self):
        return self.query(self.k.GetCurrentThread())

    def sample(self, pid, allow_query):
        threads = self.snapshot(4, pid, THREADENTRY32, 'Thread32First', 'Thread32Next')
        modules = self.snapshot(8 | 16, pid, MODULEENTRY32W, 'Module32FirstW', 'Module32NextW')
        for row in threads['items']:
            row['queryTimeUtc'] = datetime.now(timezone.utc).isoformat()
            if not allow_query:
                row['hide'] = dict(value=None, error='self-probe-unsupported'); continue
            handle = self.k.OpenThread(0x40, False, row['tid'])
            if not handle:
                row['hide'] = dict(value=None, error='OpenThread', winerror=C.get_last_error()); continue
            try:
                owner = self.k.GetProcessIdOfThread(handle)
                row['handleOwnerPid'] = owner
                row['hide'] = self.query(handle) if owner == pid else dict(value=None, error='thread-owner-mismatch')
            finally:
                self.k.CloseHandle(handle)
        return dict(threads=threads, modules=modules)

    def close(self, handle):
        self.k.CloseHandle(handle)


def observe(backend, game, evidence, seconds=90, interval=2, sleep=time.sleep, monotonic=time.monotonic):
    inventory = backend.processes(game)
    if inventory['error'] is not None:
        raise RuntimeError('Cannot inventory existing processes')
    evidence['inventoryInaccessiblePaths'] = inventory.get('inaccessiblePaths', 0)
    if inventory['items']:
        raise RuntimeError('Existing game process: no launch')
    target = game / 'YuanShen.exe'
    evidence['candidateFilesBefore'] = {name: file_state(game/name) for name in CANDIDATES}
    evidence['selfThreadProbe'] = backend.self_probe()
    evidence['launch'] = dict(executable=str(target), cwd=str(game), arguments=[], creationFlags=0, debugFlags=False)
    start = monotonic(); deadline = start + seconds
    evidence['launchTimeUtc'] = datetime.now(timezone.utc).isoformat()
    handle, pid = backend.launch(target, game)
    evidence['pid'] = pid
    try:
        evidence['processCreationTime'] = backend.creation_time(handle)
        image = backend.image(handle)
        evidence['image'] = image
        if Path(image).resolve() != target.resolve():
            raise RuntimeError('Created process image mismatch')
        evidence['samples'] = []
        while True:
            status = backend.status(handle)
            if not status['alive']:
                evidence['finalStatus'] = status; break
            now = monotonic()
            if now >= deadline:
                evidence['finalStatus'] = status; break
            sample = backend.sample(pid, evidence['selfThreadProbe']['value'] is not None)
            sample.update(elapsedSeconds=round(now-start, 3), timeUtc=datetime.now(timezone.utc).isoformat())
            evidence['samples'].append(sample)
            sleep(min(interval, max(0, deadline-monotonic())))
        evidence['elapsedSeconds'] = round(monotonic()-start, 3)
    finally:
        evidence['candidateFilesAfter'] = {name: file_state(game/name) for name in CANDIDATES}
        backend.close(handle)
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=90, choices=range(1, 91))
    parser.add_argument('--user-ready', action='store_true', help='User is present for the visible startup.')
    args = parser.parse_args()
    if not args.user_ready:
        parser.error('The user must be ready to observe the visible startup.')
    output = args.output.resolve(); allowed = (ROOT/'local/migration72/d02-active/plain').resolve()
    if not output.is_relative_to(allowed) or output.exists():
        parser.error('Choose a new output under local/migration72/d02-active/plain/.')
    output.parent.mkdir(parents=True, exist_ok=True)
    evidence = dict(scope='uninstrumented startup; no debugger attachment', startUtc=datetime.now(timezone.utc).isoformat(),
                    sourceSHA256=sha256(Path(__file__)), status='preflight')
    try:
        game = Path(subprocess.check_output(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                                             str(ROOT/'tools/game_path.ps1')], text=True, encoding='utf-8').strip()).resolve()
        evidence['inputs'] = verified_inputs(ROOT, game)
        observe(WindowsBackend(), game, evidence, args.seconds)
        evidence['status'] = 'completed'
    except Exception as exc:
        evidence.update(status='error', errorType=type(exc).__name__, error=str(exc), winerror=getattr(exc, 'winerror', None))
    finally:
        output.write_text(json.dumps(evidence, indent=2, ensure_ascii=False), encoding='utf-8')
    if evidence['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
