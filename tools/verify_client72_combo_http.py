"""Owned native builder -> real Java HTTP/Mongo -> native parser/formatter, PIPE only."""
from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import os
from pathlib import Path
import queue
import subprocess
import sys
import threading
import time
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT/'local/migration72/d02-resume-20261108/r2-combo-http'
MAIN = 'emu.grasscutter.server.http.dispatch.ComboLoginNativeHttpFixture'
SDK_SHA = 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'
PINS = {
    'tools/client72_sdk_combo_signature.py': 'cca96bb3a066c8e3322bd3a63ceed2a994627e714e6a6a85c145bb9afbe15f9e',
    'tools/client72_sdk_response_contract.py': '792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5',
    'local/migration72/d02-active/sdk-response-astra/final-first.json': '77ffd6e3c378000456608197cd8ca197a6183c9b4378198ae969cf48f0442992',
}
JAVA_SOURCES = tuple('src/test/java/emu/grasscutter/server/http/dispatch/'+name+'.java' for name in
                     ('ComboLoginRoutesMongoTest', 'ComboLoginNativeHttpFixture', 'ComboHttpMongoHarness'))
CLASS_NAMES = tuple('emu/grasscutter/'+name+'.class' for name in (
    'server/http/dispatch/ComboLoginRoutesMongoTest', 'server/http/dispatch/ComboLoginNativeHttpFixture',
    'server/http/dispatch/ComboHttpMongoHarness', 'server/http/dispatch/AuthenticationHandler',
    'server/http/objects/ComboTokenReqJson', 'server/http/objects/ComboTokenReqJson$LoginTokenData',
    'server/http/objects/ComboTokenResJson', 'server/http/objects/ComboTokenResJson$LoginData',
    'auth/DefaultAuthentication', 'auth/DefaultAuthenticators$SessionKeyAuthenticator',
    'auth/AuthenticationSystem', 'game/Account', 'database/DatabaseHelper', 'database/DatabaseManager'))
CASES = ('success', 'wrong_token', 'empty_token', 'missing_token', 'unknown_uid', 'repeat_success')
UID, UNKNOWN_UID = '9007199254741009', '9223372036854775807'
ROUTE = '/hk4e_cn/combo/granter/login/v2/login'
COMMON_CHECKS = {'defaultAuthenticator', 'textualUidExact', 'nestedInputExact', 'accountCountUnchanged',
                 'sessionKeyUnchanged', 'playerOwnershipUnchanged'}
SUCCESS_CHECKS = {'generatedTokenMatchesMongo', 'loginTokenRotated', 'openIdExact64Text'}
FAILURE_CHECKS = {'failureBsonUnchanged', 'failureHasNoToken'}


def require(value, diagnostic):
    if not value:
        raise ValueError(diagnostic)


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_native(client):
    for name, expected in PINS.items():
        require(file_hash(ROOT/name) == expected, 'Frozen native source/report identity changed')
    sys.path[:0] = [str(ROOT/'tools'), str(ROOT/'local/migration72/python')]
    signature = importlib.import_module('client72_sdk_combo_signature')
    fixed = signature.verify_inputs(client)
    snapshot = signature.java_contract()  # Frozen nine-file epoch; never rewrite it.
    sdk = (client/signature.SDK_FILE).read_bytes()
    require(hashlib.sha256(sdk).hexdigest() == SDK_SHA, 'Fixed SDK identity changed')
    pe = signature.Image(sdk)
    report = json.loads((ROOT/'local/migration72/d02-active/sdk-response-astra/final-first.json').read_bytes())
    windows = {}
    for entry in ('0x3ca000', '0x3c91a0'):
        row = report['codeWindows'][entry]
        start, end = int(row['startRva'], 16), int(row['endRva'], 16)
        offset = pe.offset(pe.base+start)
        require(hashlib.sha256(sdk[offset:offset+end-start]).hexdigest() == row['sha256'], 'Frozen combo consumer code window changed')
        windows[entry] = {key: row[key] for key in ('startRva', 'endRva', 'sha256')}
    offset = pe.offset(pe.base+0x6d2220)
    require(int.from_bytes(sdk[offset:offset+8], 'little') == pe.base+0x39eb30, 'RequestManager parser vtable target changed')
    current = {name: file_hash(ROOT/name) for name in snapshot['sources']}
    return signature, pe, dict(fixedInputs=fixed, parentPins=PINS, currentJavaSourceSHA256=current,
                              frozenJavaEpochMatchesCurrent=snapshot['currentSourceMatchesEpoch'], consumerWindows=windows)


def native_request(signature, pe, seed, label):
    """New parameterized owned inputs; original builder/signer/send-before-call boundary unchanged."""
    env = signature.SignatureFixture(pe)
    aggregate, config, crypto, combo = [env.OBJECT+n for n in (0, 0x20000, 0x30000, 0x40000)]
    env.put(pe.base+0x7acc80, aggregate)
    env.put(aggregate+0x40, config); env.put(aggregate+0x38, crypto)
    network = env.OBJECT+0xc0000
    env.put(aggregate+0x10, network); env.put(network, pe.base+0x6d1f48)
    env.put(config, pe.base+0x6c6cd8); env.put(crypto, pe.base+0x6d1cb8)
    key = b'owned-combo-http-key'
    env.app_key_producer(config, key.decode('ascii'))
    app, channel, device = b'4', b'1', 'owned-device-设备'.encode()
    for offset, value in ((0x1f8, app), (0x150, channel), (0xa8, device)):
        env.put(config+offset+0x18, 15); env.set_string(config+offset, value)
    route = seed['url'].encode('ascii')
    env.put(config+0x860, env.make_map([(b'comboLogin', route)], env.OBJECT+0x90000))
    inner = dict(uid=UNKNOWN_UID if label == 'unknown_uid' else seed['uid'], guest=False,
                 nested=dict(unicode='中文é😀', n=(1 << 63)-1))
    if label != 'missing_token':
        inner['token'] = ('owned-unmatched-session' if label == 'wrong_token' else '' if label == 'empty_token'
                          else seed['session_token'])
    data = json.dumps(inner, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    caller = env.OBJECT+0xb0000; env.uc.mem_write(caller, data+b'\0')
    env.trace, env.events, env.fragment_end = [], [], 0x3bec2c
    ux = signature.ux
    env.uc.mem_write(env.STACK-0x10000, bytes(0x11000)); env.put(env.STACK, env.STOP)
    for register, value in ((ux.UC_X86_REG_RSP, env.STACK), (ux.UC_X86_REG_RCX, combo), (ux.UC_X86_REG_RDX, caller)):
        env.uc.reg_write(register, value)
    env.uc.emu_start(pe.base+0x3be5c0, env.STOP, count=2_000_000)
    require(env.uc.reg_read(ux.UC_X86_REG_RIP) == pe.base+0x3bec2c, 'Native builder missed exact send boundary')
    pairs = [(b'app_id', app), (b'channel_id', channel), (b'data', data), (b'device', device)]
    expected = {name.decode('ascii'): value.decode('utf-8') for name, value in pairs}
    expected['sign'] = signature.oracle(pairs, key).decode('ascii')
    expected_body = json.dumps(expected, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()
    require(env.observed_body == expected_body and env.signer_inputs == [(signature.canonical(pairs), key)],
            'Native builder/signature byte comparison failed')
    require(env.builder_sign_call['targetRva'] == 0x3958e0
            and env.builder_sign_return['returnsCallerOutputPointer64'], 'Native signer ABI comparison failed')
    require(env.cstring(env.uc.reg_read(ux.UC_X86_REG_RDX)) == route
            and env.get(env.uc.reg_read(ux.UC_X86_REG_RAX)+0x10) == pe.base+0x39c310,
            'Native builder route/target comparison failed')
    proof = dict(entryRva='0x3be5c0', stoppedBeforeCallRva='0x3bec2c', signerRva='0x3958e0',
        signCallRva='0x3bea13', signReturnRva='0x3bea16', originalSignerReturned=True,
        signerOracleEqual=True, originalCanonicalBytesEqual=True, outerCompactUtf8BytesEqual=True,
        allOuterFieldsStrings=all(isinstance(v, str) for v in json.loads(env.observed_body).values()),
        dataBytesPreserved=json.loads(env.observed_body)['data'].encode('utf-8') == data,
        nativeInstructions=len(env.trace), wholeBuilderReturned=False, nativeNetworkExecuted=False,
        naturalConfigObserved=False)
    return env.observed_body, proof


def native_response(signature, pe, frame, seed):
    env = signature.ResponseFixture(pe)
    env.initialized_service()
    aggregate, request = env.OBJECT+0x1000, env.OBJECT+0x2000
    status, message, data, user, out = [env.OBJECT+n for n in (0x4000, 0x5000, 0x6000, 0x7000, 0x10000)]
    env.put(status, 0xffffffff, 4); env.put(message+0x18, 15)
    env.call(0x14d040, data)
    # 3CA14F..174: RCX=RequestManager, RDX=raw response C-string, R8D=0,
    # R9=status*, stack+20=message std::string*, stack+28=data CJsonObject*.
    accepted = env.call(0x39eb30, request, env.packet(frame['body'].encode('utf-8')), 0,
                        status, message, data) & 255
    native_status = (env.get(status, 4)+0x80000000) % 0x100000000-0x80000000
    proof = dict(parserRva='0x39eb30', parserAccepted=bool(accepted), status=native_status,
                 parserEvidence=env.evidence([0x39eb30]), fullComboCallbackExecuted=False)
    require(bool(accepted), 'Original RequestManager rejected Java response syntax')
    success = frame['case'] in ('success', 'repeat_success')
    require(native_status == (0 if success else -201), 'Native response status differs from HTTP authentication')
    if success:
        env.call(0x156530, user); env.put(user+0x148, 1, 4); env.set_string(user+0x170, seed['session_token'])
        env.call(0x14d040, out)
        message_pointer = message if env.get(message+0x18) < 16 else env.get(message)
        env.call(0x3c91a0, env.OBJECT+0x20000, out, message_pointer, data, user)
        value = env.json_object(out); result = value.get('data', {})
        checks = dict(retZero=value.get('ret') == 0, openIdExact64Text=result.get('open_id') == seed['uid'],
            comboTokenMatchesMongo=result.get('combo_token') == frame['expected_combo_token'],
            guestFalse=result.get('guest') is False, accountTypeOne=result.get('account_type') == 1,
            channelTokenPreserved=result.get('channel_token') == seed['session_token'],
            comboIdExactText=result.get('combo_id') == '157795300')
        require(all(checks.values()), 'Original combo formatter differs from Java/Mongo result')
        proof.update(formatterRva='0x3c91a0', checks=checks, formatterEvidence=env.evidence([0x3c91a0]))
        env.call(0x1552f0, user); env.call(0x14d450, out)
    else:
        parsed = env.json_object(data)
        require(not parsed or not parsed.get('combo_token'), 'Native failed response exposed combo token')
        proof['failureHasNoToken'] = True
        proof['formatterSkippedForFailure'] = True
    env.call(0x1380e0, message); env.call(0x14d450, data)
    return proof


def exchange(seed, transact, builder, consumer):
    require(seed.get('schema') == 'combo-http-v1' and seed.get('phase') == 'seed'
            and seed.get('uid') == UID and isinstance(seed.get('session_token'), str)
            and bool(seed['session_token']), 'Java seed schema or textual identity differs')
    url = urlsplit(seed['url'])
    require(url.scheme == 'http' and url.hostname == '127.0.0.1' and url.path == ROUTE
            and url.port not in (None, 8088, 27017, 27027) and not url.query and not url.fragment,
            'Java fixture is not the exact ephemeral loopback route')
    rows, secrets = [], [seed['session_token']]
    for label in CASES:
        body, request_proof = builder(seed, label)
        require(isinstance(body, bytes), 'Native request must be raw UTF-8 bytes')
        request_text = body.decode('utf-8'); secrets.append(request_text)
        frame = transact(dict(schema='combo-http-v1', case=label, request_body=request_text))
        require(frame.get('schema') == 'combo-http-v1' and frame.get('phase') == 'response'
                and frame.get('case') == label and frame.get('http_status') == 200,
                'Java HTTP frame order/status/schema differs')
        expected_checks = COMMON_CHECKS | (SUCCESS_CHECKS if label in ('success', 'repeat_success') else FAILURE_CHECKS)
        require(isinstance(frame.get('body'), str) and set(frame['checks']) == expected_checks
                and all(value is True for value in frame['checks'].values()), 'Java domain assertions failed')
        secrets.append(frame['body'])
        if label in ('success', 'repeat_success'):
            require(isinstance(frame.get('expected_combo_token'), str) and bool(frame['expected_combo_token']),
                    'Java success lacks Mongo token witness')
            secrets.append(frame['expected_combo_token'])
        else:
            require('expected_combo_token' not in frame and frame['checks'].get('failureBsonUnchanged') is True
                    and frame['checks'].get('failureHasNoToken') is True, 'Java failure lacks BSON/no-token witness')
        proof = consumer(frame, seed)
        rows.append(dict(case=label, request=request_proof, httpStatus=200, javaChecks=frame['checks'], response=proof))
    return rows, secrets


class PipeChild:
    """Bounded owned child only. All child output remains in RAM; no exception embeds it."""
    def __init__(self, command, cwd):
        self.process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE)
        self.lines, self.stderr = queue.Queue(), bytearray()
        self.deadline = time.monotonic()+180
        self.threads = [threading.Thread(target=self._stdout, daemon=True), threading.Thread(target=self._stderr, daemon=True)]
        for thread in self.threads:
            thread.start()

    def _stdout(self):
        while True:
            line = self.process.stdout.readline(1 << 20)
            self.lines.put(line)
            if not line or len(line) >= (1 << 20):
                return

    def _stderr(self):
        while True:
            data = self.process.stderr.read(8192)
            if not data:
                return
            self.stderr.extend(data)

    def receive(self):
        try:
            line = self.lines.get(timeout=max(0, self.deadline-time.monotonic()))
        except queue.Empty:
            raise RuntimeError('Owned Java PIPE timed out') from None
        require(bool(line) and len(line) < (1 << 20), 'Owned Java PIPE ended or exceeded frame budget')
        try:
            return json.loads(line)
        except (ValueError, UnicodeError):
            raise ValueError('Owned Java PIPE emitted non-protocol output') from None

    def transact(self, frame):
        self.process.stdin.write(json.dumps(frame, ensure_ascii=False, separators=(',', ':')).encode()+b'\n')
        self.process.stdin.flush()
        return self.receive()

    def close(self):
        self.process.stdin.close()
        try:
            self.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.process.terminate()  # Only the exact test child created above; no target client.
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill(); self.process.wait(timeout=5)
        for thread in self.threads:
            thread.join(timeout=2)


def class_binding(classpath):
    paths = [Path(value) for value in classpath.split(os.pathsep)]
    bindings = {}
    for name in CLASS_NAMES:
        matches = [path/name for path in paths if (path/name).is_file()]
        require(len(matches) == 1, 'Compiled fixture/receiver class is absent or ambiguous in classpath')
        bindings[name] = dict(path=str(matches[0].resolve().relative_to(ROOT)), sha256=file_hash(matches[0]))
    return bindings


def execute(args):
    args.failure_stage = 'fixed-input-and-classpath-preflight'
    require(os.environ.get('LUNAGC_RUNTIME') == 'portable', 'LUNAGC_RUNTIME must be portable')
    signature, pe, sources = load_native(args.client)
    classpath = args.classpath_file.read_text(encoding='utf-8-sig').strip()
    bindings = class_binding(classpath)
    child = PipeChild([str(args.java), '-cp', classpath, MAIN, '--pipe'], args.working_directory)
    try:
        args.failure_stage = 'java-seed'
        seed = child.receive()
        def builder(seed, label):
            args.failure_stage = 'native-3BE5C0-to-3BEC2C:'+label
            return native_request(signature, pe, seed, label)
        def transact(frame):
            args.failure_stage = 'java-http-and-mongo:'+frame['case']
            return child.transact(frame)
        def consumer(frame, seed):
            args.failure_stage = 'native-39EB30-and-3C91A0:'+frame['case']
            return native_response(signature, pe, frame, seed)
        rows, secrets = exchange(seed, transact, builder, consumer)
        args.failure_stage = 'java-final-cleanup'
        complete = child.receive()
        require(complete == dict(schema='combo-http-v1', phase='complete', cases=6,
                uuidDatabaseDropped=True, httpStopped=True), 'Java final cleanup contract differs')
    finally:
        child.close()
    require(child.process.returncode == 0, 'Java fixture failed; captured streams remain in memory')
    args.failure_stage = 'public-report-privacy'
    while not child.lines.empty():
        require(child.lines.get_nowait() == b'', 'Java emitted trailing non-protocol stdout')
    diagnostic = bytes(child.stderr).decode('utf-8', errors='replace')
    require(not any(secret in diagnostic for secret in secrets), 'Credential/body reached child diagnostics')
    report = dict(task='R2-COMBO-HTTP', status='completed', sdkSHA256=SDK_SHA, sources=sources,
        sourceSHA256=file_hash(Path(__file__)), javaSourceSHA256={name: file_hash(ROOT/name) for name in JAVA_SOURCES},
        javaClassBinding=bindings, cases=rows, cleanup=dict(uuidDatabaseDropped=True, httpStopped=True),
        childExitCode=0, childDiagnosticsContainCredentialsOrBodies=False,
        requestResponseOrCredentialPersisted=False, requestResponseOrCredentialHashPersisted=False,
        wholeNativeBuilderReturned=False, nativeNetworkExecuted=False, fullComboCallbackExecuted=False,
        naturalConfigObserved=False, naturalSdkLoginVerified=False, d02Complete=False,
        models=['Owned aggregate/config/device/route/app_key, original app_key writer and signer',
                'Real Javalin/Gson/HTTP/default authentication/Morphia/Account sync-save and UUID Mongo database',
                'Frozen SDK storage/single-thread synchronization models; original JSON parser and combo formatter',
                'RequestManager 39EB30 then 3C91A0 separately; full 3CA000 UI/callback not executed'])
    require(not any(secret in json.dumps(report, ensure_ascii=False) for secret in secrets), 'Secret entered public report')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('client', 'java', 'classpath-file', 'working-directory', 'output'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(RAW.resolve()) or output.exists():
        parser.error('Choose a new output inside the private r2-combo-http directory')
    try:
        report = execute(args)
    except Exception as failure:
        report = dict(task='R2-COMBO-HTTP', status='error', failureType=type(failure).__name__,
            failureStage=getattr(args, 'failure_stage', 'fixed-input-and-classpath-preflight'),
            diagnostic='COMBO_HTTP_BRIDGE_FAILED', sourceSHA256=file_hash(Path(__file__)),
            requestResponseOrCredentialPersisted=False, requestResponseOrCredentialHashPersisted=False,
            naturalSdkLoginVerified=False, d02Complete=False)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2); stream.write('\n')
    print(json.dumps(dict(status=report['status'], cases=len(report.get('cases', [])), rawPersisted=False)))
    if report['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
