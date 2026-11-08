"""Consume actual Java HTTP responses with the fixed SDK; credentials stay in a pipe."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'local/migration72/python')]
from client72_sdk_response_contract import ResponseFixture, signed32
from client72_sdk_transport import Image

SDK_SHA = 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'
RESPONSE_TOOL_SHA = '792dd8f1597b577673caac064cd3da1b3fcd13ca1e906eae382bde901b6628e5'
MAIN_CLASS = 'emu.grasscutter.server.http.dispatch.SessionVerifyNativeResponseFixture'


def consume(pe, body, expected_token, case):
    # The original serialized HTTP body goes directly to the native parser, without re-encoding.
    env = ResponseFixture(pe)
    service = env.initialized_service()
    error, data, user, out, config = [env.OBJECT + offset for offset in (0x4000, 0x5000, 0x6000, 0x7000, 0x10000)]
    for address in (error, data, out):
        env.call(0x14d040, address)
    env.call(0x156530, user)
    if case == 'passport_success':
        env.put(user + 0x148, 1, 4)
        env.set_string(user + 0x170, expected_token)
    status = signed32(env.call(0x342b10, service, env.packet(body.encode('utf-8')), error, user, data) & 0xffffffff)
    result = dict(case=case, status=status, typeAfterParse=env.get(user + 0x148, 4),
                  stokenEqualsInput=env.string(user + 0x170) == expected_token.encode('utf-8'),
                  stokenLength=len(env.string(user + 0x170)),
                  parserEvidence=env.evidence([0x342b10, 0x2f6190, 0x338990, 0x31f3f0]))
    assert result['parserEvidence']['visitedEntries']['0x342b10'] == 1, 'Missing native parser entry'
    if case == 'session_error':
        assert status == -101, 'Native error status changed'
        assert not env.string(user + 0x150) and not env.string(user + 0x170), 'Error issued a token'
        result['errorHasNoToken'] = True
    else:
        assert status == 0, 'Native parser rejected Java success'
        env.put(config, pe.base + 0x6c6cd8)
        env.put(config + 0xc0, 15)
        env.set_string(config + 0xa8, 'synthetic-config')
        env.put(env.OBJECT + 0x1000 + 0x40, config)
        env.call(0x342460, service, user, out)
        result['formatterEvidence'] = env.evidence([0x342460, 0x36aa70])
        assert result['formatterEvidence']['visitedEntries']['0x342460'] == 1, 'Missing native formatter entry'
        formatted = env.json_object(out)
        result.update(openIdExact64Text=formatted.get('open_id') == '9007199254740993',
                      comboIdExact64Text=formatted.get('combo_id') == '9007199254740993',
                      comboTokenEqualsInput=formatted.get('combo_token') == expected_token,
                      comboTokenLength=len(formatted.get('combo_token', '')))
        assert result['openIdExact64Text'] and result['comboIdExact64Text'], '64-bit textual identity changed'
        if case == 'session_success':
            assert result['typeAfterParse'] == 1 and result['stokenEqualsInput'], 'Session token not preserved'
            assert result['comboTokenEqualsInput'], 'Formatter lost the session token'
        else:
            assert result['typeAfterParse'] == 0 and result['comboTokenLength'] == 0, 'Array negative control changed'
    env.call(0x1552f0, user)
    for address in (error, data, out):
        env.call(0x14d450, address)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--working-directory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or not args.output.resolve().is_relative_to((ROOT / 'local').resolve()):
        raise ValueError('Choose a new output under checkout local/')
    parent = ROOT / 'tools/client72_sdk_response_contract.py'
    if hashlib.sha256(parent.read_bytes()).hexdigest() != RESPONSE_TOOL_SHA:
        raise ValueError('Frozen native response fixture changed')
    sdk = (args.client / 'YuanShen_Data/Plugins/AccountPlatNative.dll').read_bytes()
    if hashlib.sha256(sdk).hexdigest() != SDK_SHA:
        raise ValueError('SDK identity changed')
    classpath = args.classpath_file.read_text(encoding='utf-8')
    # Never redirect this child's stdout/stderr to disk or expose them in an exception.
    java = subprocess.run([str(args.java), '-cp', classpath, MAIN_CLASS], cwd=args.working_directory,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45)
    if java.returncode != 0:
        raise RuntimeError('Java HTTP fixture failed; captured streams remain private in memory')
    responses = json.loads(java.stdout)
    assert responses['lookup_count'] == 3, 'Unexpected authentication count'
    pe = Image(sdk)
    rows = [consume(pe, responses[name], responses['expected_token'], name)
            for name in ('session_success', 'session_error', 'passport_success')]
    sources = ['src/main/java/emu/grasscutter/server/http/dispatch/AuthenticationHandler.java',
               'src/main/java/emu/grasscutter/server/http/objects/SessionVerifyResponseJson.java',
               'src/test/java/emu/grasscutter/server/http/dispatch/SessionVerifyRoutesTest.java',
               'src/test/java/emu/grasscutter/server/http/dispatch/SessionVerifyNativeResponseFixture.java']
    classes = ['emu/grasscutter/server/http/dispatch/AuthenticationHandler.class',
               'emu/grasscutter/server/http/objects/SessionVerifyResponseJson.class',
               'emu/grasscutter/server/http/objects/SessionVerifyResponseJson$SessionData.class']
    report = dict(sdkSHA256=SDK_SHA, parentSHA256=RESPONSE_TOOL_SHA,
                  sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  javaSourceSHA256={p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in sources},
                  javaClassSHA256={p: hashlib.sha256((ROOT / 'build/classes/java/main' / p).read_bytes()).hexdigest() for p in classes},
                  authenticationCount=3, cases=rows, rawResponsesPersisted=False,
                  credentialOrBodyHashesPersisted=False, naturalSdkVerified=False, d02Complete=False,
                  models=['Read-only Java account fixture; real handler/authenticator/Javalin/Gson/HTTP',
                          'Original SDK JSON parser, decoder, formatter and destructors',
                          'Parent fixture storage allocation/free and single-thread synchronization models',
                          'Sparse service/config objects with exact documented vtables; no UI or network SDK lifecycle'])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(cases=len(rows), allAssertionsPassed=True, rawResponsesPersisted=False)))


if __name__ == '__main__':
    main()
