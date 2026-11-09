"""Frozen split event11, source-only language inputs, and owned prefix observations."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
SPLIT = 'local/migration72/d02-parallel-20261109/b-sdk-split/'
OUTPUT = ROOT / 'local/migration72/d02-parallel-20261109/b-sdk-limit11'
ROUTE = 'local/migration72/d02-resume-20261108/r2-route/'
WINDOWS = 'local/migration72/d02-parallel-20261109/b-sdk-limit11/main-windows1/'
MANIFEST_SNAPSHOT = 'local/migration72/d02-parallel-20261109/b-sdk-limit11/accepted-cargo-manifest.private.toml'
MAX_UNITS, MAX_UTF8 = 32768, 65535
CN_ENVS = (0, 1, 4, 6, 8, 9, 22, 19)
KEY, URL = 'OWNED_KEY_SENTINEL', 'OWNED_URL_SENTINEL'
PINS = {
    SPLIT + 'live3.private.json': 'd2fbef9b4e9598da983ba7e5a3ad2d50d91b09531717e4ad7017ceea39638815',
    SPLIT + 'live3.private.json.reads.private.json': 'e07e9e64d5929a4cee1fa29e063403d3c0c7aabbf616512e15b74835bd4d2b14',
    SPLIT + 'owned-main1/proof.private.json': '6ddcbb41a82fd63174b9db70a96bb0fe83dfcb8b700e7afcdd9f9789be1a79ae',
    SPLIT + 'main-owned-review.private.json': 'c53422f80ae8ad0f8033b2a5039fa1372e41e9788b05bd4526d2ba76cfe96499',
    SPLIT + 'live3-restored.private.json': '0a0ffc75eda58a001facbcc4738950c937c1de69cb8ba839064b07e272f65cdb',
    WINDOWS + 'manifest.private.json': 'd6e228325cc80a421b3df0ed9738d3c6af9e217e9fbace1e1118191b9075f696',
    'tools/observe_client72_config_early_split_proxy.py': 'bf9267fe9d305b0f0388690c30401391ee07d031e9abd4007e7465ab30a717bc',
    'local/migration72/evidence/client72-structures.json': 'fd82e0a55baa69301e87c422465e3797b0f1279778e6b82d938c28446f1b56c5',
    'local/migration72/d02-resume-20261109/r2-config-select/sync-windows.private.json': '4722fea419f51cb985648ccf0ff5f318c3a32b489c49c25ad8d07c2b25c1a9d1',
    'tools/client72_sdk_route_loader.py': 'd4816a4448ef2533d20ab7bc656ef617888a79ded69fb3416c5defe24fdf6cac',
    ROUTE + 'main-third.json': 'bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471',
    ROUTE + 'final-v3-first.json': 'bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471',
    ROUTE + 'final-v3-repeat.json': 'bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471',
    MANIFEST_SNAPSHOT: '3ea4ff0853353f3070b818935c20e6e56b21219f64bea983e4aec8906ed8dd65',
}


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def frozen_inputs():
    files = {}
    accepted_raw = (ROOT / (SPLIT + 'owned-main1/proof.private.json')).read_bytes()
    require(digest(accepted_raw) == PINS[SPLIT + 'owned-main1/proof.private.json'], 'Accepted proof identity differs')
    accepted = json.loads(accepted_raw)
    manifest_raw = (ROOT / (WINDOWS + 'manifest.private.json')).read_bytes()
    require(digest(manifest_raw) == PINS[WINDOWS + 'manifest.private.json'], 'Window manifest identity differs')
    pins = {**accepted['frozenSourcePins'], **PINS}
    pins.update(json.loads(manifest_raw)['sourcePins'])
    pins.update({name: row['sha256'] for name, row in accepted['sources'].items()})
    require(accepted['buildInputs']['patch/Cargo.toml']['sha256'] == PINS[MANIFEST_SNAPSHOT], 'Accepted manifest snapshot binding differs')
    pins.update({name: row['sha256'] for name, row in accepted['buildInputs'].items() if name != 'patch/Cargo.toml'})
    for name, row in ((accepted['owned']['fixture']['path'], accepted['owned']['fixture']),
                      (SPLIT + 'target/release/examples/sdk72_config_early_split_proxy.dll', accepted['artifact'])):
        pins[name] = row['sha256']
    for name, expected in pins.items():
        raw = (ROOT / name).read_bytes()
        require(digest(raw) == expected, 'Frozen source/evidence differs: ' + name)
        files[name] = raw
    return files, pins


def audit_epoch(files):
    load = lambda name: json.loads(files[SPLIT + name])
    accepted, live = load('owned-main1/proof.private.json'), load('live3.private.json')
    review, restored = load('main-owned-review.private.json'), load('live3-restored.private.json')
    inputs = live['inputs']
    require(inputs['artifactAuditSHA256'] == review['acceptedProofSHA256'] == PINS[SPLIT + 'owned-main1/proof.private.json'],
            'Accepted source join differs')
    require(inputs['sources'] == accepted['sources'] and inputs['frozenSourcePins'] == accepted['frozenSourcePins'] and
            len(accepted['sources']) == 5 and len(accepted['frozenSourcePins']) == 55, 'Source epoch differs')
    require(inputs['productionSHA256'] == review['productionSHA256'] == accepted['artifact']['sha256'] ==
            inputs['installedProxy']['sha256'] and inputs['fixtureSHA256'] == review['fixtureSHA256'] ==
            accepted['owned']['fixture']['sha256'], 'Actual artifact join differs')
    require(inputs['manifestSHA256'] == review['manifestSHA256'] == accepted['buildInputs']['patch/Cargo.toml']['sha256'] and
            live['sourceSHA256'] == PINS['tools/observe_client72_config_early_split_proxy.py'], 'Build/adapter join differs')
    require(accepted['owned']['passed'] and len(accepted['owned']['runs']) == 2 and review['passed'], 'Owned acceptance differs')
    for run in accepted['owned']['runs']:
        result = run['publicResult']
        require(run['passed'] and run['ownedEmitterVerified'] and len(result['cases']) == 23 and
                all(result[k] for k in ('gpr16', 'xmm16', 'flags', 'entryStack128', 'entryAndReturnLastError',
                                       'returnHigh64', 'dropRestored', 'sourceObjectsUnchanged',
                                       'unknownCallerZeroNameOrPayloadReads', 'wrongNameZeroPayloadReads')),
                'Frozen actual ABI result differs')
    ledger = load('live3.private.json.reads.private.json')['reads']
    require([r['requested'] for r in ledger] == [72, 376, 359, 139, 64, 46, 284, 38, 72] and
            all(not r['error'] and r['bytesRead'] == r['requested'] == len(bytes.fromhex(r['bytesHex'])) for r in ledger),
            'Same-call ledger differs')
    before, after = (bytes.fromhex(ledger[n]['bytesHex']) for n in (0, -1))
    state = struct.unpack('<6IQ10I', before)
    require(before == after and state[:6] == (1, 72, 5, 0, 0, 1) and state[11:] == (1, 1, 11, 0, 0, 0),
            'Frozen event11 state differs')
    binding = live['binding']
    require(binding['event'] == dict(status='payload-unit-scan-limit', anyEntrySeen=True, expectedCallerSeen=True) and
            binding['status'] == 'installed-early-code-bound' and all(binding[k] for k in
            ('fullTrampolineAndCallbackEqual', 'returnEntryPlus20Verified', 'stableStateEqual')),
            'Frozen code binding differs')
    require(len(live['samples']) == 45 and restored['clientAbsent'] and restored['originalAliasRemoved'],
            'Frozen monitor/restoration summary differs')
    return dict(liveSourceAcceptedArtifactAndFixtureJoined=True, frozenPins=55, newSources=5,
                previousActualOwnedFreshRuns=2, previousActualOwnedCasesPerRun=23,
                naturalEvent=11, utf8LimitEventObserved=False, classificationSucceeded=False,
                fixedCollectorReads=9, fixedCollectorBytes=1450,
                acceptedBuildManifestSnapshotUsed=True,
                originalMarshalObserved=False, nativeInitReturned=False, naturalEnvSelected=False,
                naturalRouteObserved=False, naturalPayloadLengthKnown=False, missingNulProved=False,
                frozenRestorationSummaryMatched=True)


def metadata_and_windows(files):
    types = json.loads(files['local/migration72/evidence/client72-structures.json'])['types']
    language = next(t for t in types if t['index'] == 4127)
    fields = [f['name'] for f in language['fields'] if f['offsetKind'] == 'instance']
    require(language['name'] == 'LanguageModel' and len(fields) == 695 and
            all(f['type']['kind'] == 'string' for f in language['fields'] if f['offsetKind'] == 'instance'),
            'LanguageModel schema differs')
    methods = {m['index']: (t, m) for t in types for m in t['methods'] if m['index'] in (32769, 4408, 32772, 32767, 31963)}
    manifest = json.loads(files[WINDOWS + 'manifest.private.json'])
    require(manifest['totalExtentBytes'] == 648 and not manifest['nativeCpuExecuted'] and not manifest['liveTargetRead'],
            'New window scope differs')
    require(manifest['fixedEXE']['sha256'] == json.loads(files[SPLIT + 'owned-main1/proof.private.json'])['fixedInputs']['binary']['sha256'],
            'New window EXE/accepted identity differs')
    sys.path.insert(0, str(ROOT / 'local/migration72/python'))
    import capstone
    cs = capstone.Cs(capstone.CS_ARCH_X86, capstone.CS_MODE_64)
    code, identities = {}, []
    for window in manifest['windows']:
        method = methods[window['methodDefinition']][1]
        entry, end = int(window['entry'], 16), int(window['end'], 16)
        require(int(method['registeredAddress'], 16) == entry, 'Window registered method differs')
        raw = bytes.fromhex(window['bytesHex'])
        require(digest(raw) == window['sha256'] and raw == (OUTPUT / 'main-windows1' / window['path']).read_bytes() and
                end - entry == len(raw), 'Window source/raw differs')
        a, z, unwind = struct.unpack('<III', bytes.fromhex(window['pdata']['bytesHex']))
        require((a, z, unwind) == (entry - 0x140000000, end - 0x140000000,
                                 int(window['pdata']['unwindRVA'], 16)), 'Original pdata record differs')
        instructions = list(cs.disasm(raw, entry))
        require(sum(i.size for i in instructions) == len(raw), 'Incomplete disassembly')
        code[window['methodDefinition']] = {i.address: (i.mnemonic, i.op_str) for i in instructions}
        identities.append(dict(method=window['methodDefinition'], entry=entry, end=end,
                               bytes=len(raw), sha256=window['sha256']))
    lang, string = code[32769], code[4408]
    require(lang[0x1599D1177] == ('call', methods[32772][1]['registeredAddress']) and
            lang[0x1599D11D8] == ('call', methods[32767][1]['registeredAddress']) and
            lang[0x1599D11BF] == ('call', methods[31963][1]['registeredAddress']) and
            lang[0x1599D11FB] == ('mov', 'qword ptr [rax + 0xb60], rsi'), 'Language producer branches differ')
    expected = {0x159B94F47: ('mov', 'ebx, dword ptr [rcx + 0x20]'),
                0x159B94F4A: ('add', 'ebx, dword ptr [rcx + 0x24]'),
                0x159B94F56: ('add', 'eax, 2'), 0x159B94F5C: ('add', 'rdx, 0x14'),
                0x159B94FA0: ('mov', 'dword ptr [rax + 0x10], ebx'),
                0x159B94FA6: ('add', 'rbx, 0x14'), 0x159B94FEB: ('call', '0x141fdf310')}
    require(all(string[a] == value for a, value in expected.items()), 'Actual StringBuilder representation differs')
    windows = json.loads(files['local/migration72/d02-resume-20261109/r2-config-select/sync-windows.private.json'])
    ins = {a: (m, op) for w in windows if w['rva'] == '0x19806d70' for a, _, m, op in w['instructions']}
    require(ins[0x1598077F6] == ('call', '0x1599d1160') and ins[0x159807800] == ('call', '0x140d35430') and
            ins[0x15980782B] == ('mov', 'qword ptr [rbx + 0x10], rdi') and
            ins[0x15980783C] == ('call', '0x1597ae580') and ins[0x15980793C] == ('call', '0x159b94f30'),
            'Full local configuration language input differs')
    return fields, dict(newCompleteWindows=identities, newCodeBytes=648,
                        languageInstanceStringFields=695, languageObjectPassedToExistingSerializer=True,
                        sourceStringLengthOffset=16, sourceCharsOffset=20,
                        allocationReservesExtraUnit=True, allocatorZeroOrNulWriteProved=False,
                        getLanguageStringOrLocalBranch='natural selection unknown',
                        serializer140d35430FieldCoverageProved=False, newNativeCpuExecuted=False)


def source_assets(files):
    # Reuse only the frozen module's pure class49 parser. No native fixture/decoder is constructed.
    from .client72_sdk_route_loader import serialized_config, read_string
    proof = json.loads(files[ROUTE + 'main-third.json'])
    decoded = (ROOT / (ROUTE + 'main-third/mihoyo_sdk_res.serialized.private.bin')).read_bytes()
    for prior in ('final-v3-first', 'final-v3-repeat'):
        require(decoded == (ROOT / (ROUTE + prior + '/mihoyo_sdk_res.serialized.private.bin')).read_bytes(),
                'Frozen decoded artifact equality differs')
    extent = proof['bundle']['parsed']['content'][0]['size']
    binding, resource = serialized_config(decoded, extent)
    require(json.loads(json.dumps(binding)) == proof['asset'] and
            resource == (ROOT / (ROUTE + 'main-third/server_pc.private.json')).read_bytes(),
            'Existing full class49/container/text binding differs')
    data = decoded[:extent]
    _, _, _, offset = struct.unpack_from('>4I', data)
    cursor = data.index(b'\0', 20) + 1
    _, _, count = struct.unpack_from('<iBI', data, cursor)
    cursor += 9
    kinds = []
    for _ in range(count):
        kind, _, _ = struct.unpack_from('<iBh', data, cursor)
        cursor += 7 + (16 if kind == 114 else 0) + 16
        nodes, strings = struct.unpack_from('<II', data, cursor)
        cursor += 8 + nodes * 24 + strings
        kinds.append(kind)
    count = struct.unpack_from('<I', data, cursor)[0]
    cursor += 4
    objects = []
    for _ in range(count):
        cursor = (cursor + 3) & ~3
        path_id, start, size, type_index = struct.unpack_from('<QIII', data, cursor)
        cursor += 20
        objects.append((path_id, offset + start, offset + start + size, kinds[type_index]))
    selected, containers = {}, []
    for path_id, start, end, kind in objects:
        if kind == 49:
            name, at = read_string(data, start, end)
            if name not in (b'zh-cn', b'zh-tw'):
                continue
            size = struct.unpack_from('<I', data, at)[0]
            at += 4
            require(at + size <= end and (at + size + 3) & ~3 == end, 'Language TextAsset extent differs')
            selected[path_id] = data[at:at + size]
        elif kind == 142:
            _, at = read_string(data, start, end)
            count = struct.unpack_from('<I', data, at)[0]
            at += 4 + 12 * count
            count = struct.unpack_from('<I', data, at)[0]
            at += 4
            for _ in range(count):
                key, at = read_string(data, at, end)
                _, _, file_id, target = struct.unpack_from('<iiiQ', data, at)
                at += 20
                containers.append((key.decode(), file_id, target))
    links = [(key, target) for key, file_id, target in containers if target in selected and file_id == 0]
    require(len(links) == 4, 'Language/region container join is not unique')
    assets = {key: json.loads(selected[target]) for key, target in links}
    expected = {'assets/plugins/mihoyosdk/resources/' + folder + '/' + locale + '.json'
                for folder in ('language', 'region') for locale in ('zh-cn', 'zh-tw')}
    require(set(assets) == expected, 'Exact language/region container candidates differ')
    rows = json.loads(resource)
    require([r['env'] for r in rows[:8]] == list(CN_ENVS), 'Fixed CN source order differs')
    return rows[:8], assets


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def _constant_rejected(_):
    raise ValueError('Non-JSON constant')


def _open_string_tail(text):
    """Lexical quoted tail, not a claim about complete JSON/UTF16 or its unseen suffix."""
    if not text.startswith('"'):
        return False
    at = 1
    while at < len(text):
        char = text[at]
        if char == '"' or ord(char) < 0x20:
            return False
        if char == '\\':
            at += 1
            if at == len(text):
                return True
            escape = text[at]
            if escape == 'u':
                for _ in range(4):
                    at += 1
                    if at == len(text):
                        return True
                    if text[at] not in '0123456789abcdefABCDEF':
                        return False
            elif escape not in '"\\/bfnrt':
                return False
        at += 1
    return True


def observe_owned_prefix(raw, reference_key=KEY, reference_url=URL):
    """Proposed observation on an already-owned event11 buffer; never calls a read callback."""
    require(len(raw) == MAX_UNITS * 2, 'Expected the existing full unit-budget owned buffer')
    text = raw.decode('utf-16-le', 'surrogatepass')
    decoder = json.JSONDecoder(parse_constant=_constant_rejected)
    failure = dict(event=11, prefixFieldsObserved=False)
    at = 0
    while at < len(text) and text[at] in ' \r\n\t':
        at += 1
    if at == len(text) or text[at] != '{':
        return failure
    at += 1
    seen, required = set(), {}
    try:
        while at < len(text):
            while at < len(text) and text[at] in ' \r\n\t':
                at += 1
            key, at = decoder.raw_decode(text, at)
            if not isinstance(key, str) or key in seen:
                return failure
            seen.add(key)
            while at < len(text) and text[at] in ' \r\n\t':
                at += 1
            if at == len(text) or text[at] != ':':
                return failure
            at += 1
            while at < len(text) and text[at] in ' \r\n\t':
                at += 1
            if key == 'language':
                if set(required) != {'env', 'app_key', 'url'} or not _open_string_tail(text[at:]):
                    return failure
                consumed = text[:at].encode('utf-16-le', 'surrogatepass').decode('utf-16-le', 'strict')
                if len(consumed.encode()) > MAX_UTF8:
                    return failure
                env, key_value, url_value = (required[k] for k in ('env', 'app_key', 'url'))
                if type(env) not in (int, float) or env not in CN_ENVS or int(env) != env or not isinstance(key_value, str) or not isinstance(url_value, str):
                    return failure
                routes = json.loads(url_value, parse_constant=_constant_rejected)
                if not isinstance(routes, dict) or not isinstance(routes.get('comboLogin'), str):
                    return failure
                return dict(event=13, prefixFieldsObserved=True, env='cn-env-' + str(int(env)),
                            appKeyMatches=key_value == reference_key, comboUrlMatches=routes['comboLogin'] == reference_url,
                            languageQuotedValueCrossesUnitBudget=True, completePayloadValidated=False,
                            payloadTerminatorObserved=False, originalMarshalObserved=False, naturalInitOrRoutePassed=False)
            if at == len(text) or text[at] in '[{':
                return failure
            value, at = decoder.raw_decode(text, at)
            while at < len(text) and text[at] in ' \r\n\t':
                at += 1
            if at == len(text) or text[at] not in ',}':
                return failure
            if key in ('env', 'app_key', 'url'):
                required[key] = value
            if text[at] == '}':
                return failure
            at += 1
    except (ValueError, UnicodeError):
        return failure
    return failure


def source_projection_cases(rows, assets, language_fields):
    outputs = []
    language = assets['assets/plugins/mihoyosdk/resources/language/zh-cn.json']
    for row in rows:
        base = dict(env=row['env'], app_key=row['app_key'], url=compact(row['url']))
        variants = [('without-language', base),
                    ('language-model-empty-owned-values', dict(base, language=compact(dict.fromkeys(language_fields, '')))),
                    ('language-model-cached-source-values', dict(base, language=compact({k: language.get(k) for k in language_fields})))]
        for label, obj in variants:
            raw = compact(obj).encode('utf-16-le') + b'\0\0'
            exceeded = len(raw) > MAX_UNITS * 2
            prefix = observe_owned_prefix(raw[:MAX_UNITS * 2], row['app_key'], row['url']['comboLogin']) if exceeded else None
            outputs.append(dict(cnEnv=row['env'], inputKind=label, unitBudgetExhausted=exceeded,
                                ownedPrefixObservation=prefix, naturalInput=False))
    return outputs


def owned_cases():
    url = compact(dict(comboLogin=URL))
    base = dict(env=0, app_key=KEY, url=url)
    long = 'a' * MAX_UNITS
    exact = compact(dict(base, language=long))
    cases = [('language-after-required-fields', exact, 13),
             ('language-before-url', compact(dict(env=0, app_key=KEY, language=long, url=url)), 11),
             ('cut-inside-url', compact(dict(env=0, app_key=KEY, url=long, language=long)), 11),
             ('fake-keys-in-other-string', compact(dict(env=0, gamebiz='"app_key":"x","url":"y"', language=long)), 11),
             ('duplicate-required-key', '{"env":0,"env":1,"app_key":' + compact(KEY) + ',"url":' + compact(url) + ',"language":' + compact(long) + '}', 11),
             ('wrong-env', compact(dict(base, env=2, language=long)), 11),
             ('wrong-url-shape', compact(dict(base, url={}, language=long)), 11),
             ('key-mismatch-stays-false', compact(dict(base, app_key='OWNED_DIFFERENT', language=long)), 13),
             ('url-mismatch-stays-false', compact(dict(base, url=compact(dict(comboLogin='OWNED_DIFFERENT')), language=long)), 13),
             ('invalid-escape-in-language', exact.replace('"language":"a', '"language":"\\q', 1), 11),
             ('invalid-utf16-in-required-prefix', exact.replace(KEY, '\ud800', 1), 11),
             ('closed-language-then-other-large-field', compact(dict(base, language='', gamebiz=long)), 11),
             ('unobserved-suffix-is-not-validated', exact[:MAX_UNITS] + 'INVALID_UNOBSERVED_SUFFIX', 13)]
    result = []
    for label, text, expected in cases:
        raw = text.encode('utf-16-le', 'surrogatepass')
        require(len(raw) >= MAX_UNITS * 2, 'Owned fixture does not reach unit budget')
        outcome = observe_owned_prefix(raw[:MAX_UNITS * 2])
        require(outcome['event'] == expected, 'Owned prefix case differs: ' + label)
        result.append(dict(case=label, observation=outcome))
    return result


def run_audit():
    files, pins = frozen_inputs()
    epoch = audit_epoch(files)
    fields, static = metadata_and_windows(files)
    rows, assets = source_assets(files)
    projections = source_projection_cases(rows, assets, fields)
    require(all(row['unitBudgetExhausted'] == (row['inputKind'] != 'without-language') for row in projections),
            'Source projection boundary contrast differs')
    return dict(schema=1, taskIDs=['R2-CONFIGSYNC-EARLY-BRIDGE', 'R2-ROUTE-CONFIG-SELECT'],
                sourceSHA256=digest(Path(__file__).read_bytes()), frozenSourcePins=pins,
                frozenEpoch=epoch, staticInference=static,
                existingDecodedArtifactsEqual=True, freshBundleDecodeExecuted=False,
                sourceAssets=[dict(path=k, modelFieldMatches=sum(f in value for f in fields),
                                   isLanguageFolder='/language/' in k) for k, value in sorted(assets.items())],
                sourceProjectionCases=projections, ownedPythonCases=owned_cases(),
                generalSerdeOrRustParserEquivalenceClaimed=False,
                naturalScanLimitCauseProved=False, languageSourceCandidateSupported=True,
                naturalPrefixObserved=False, naturalPayloadRead=False,
                naturalLengthHashOrBodyRecorded=False, newNativeExecution=False,
                originalMarshalExecuted=False, naturalInitEnvOrRoutePassed=False, d02Passed=False,
                nextImplementation=dict(event=13, scope='required prefix fields observed before open language value crosses unit budget',
                    onlyAfterEvent11=True, newTargetReads=0, stateBytes=72, maxUnits=MAX_UNITS, maxUtf8Bytes=MAX_UTF8,
                    fullPayloadValidationClaimed=False, fallbackOnPrefixFailure=11))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    require(output.is_relative_to(OUTPUT.resolve()) and not output.exists(), 'Output must be a new file inside this task')
    result = run_audit()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(status='bounded-followup-passed', naturalCauseProved=False,
                          sourceProjectionCases=len(result['sourceProjectionCases']), ownedCases=len(result['ownedPythonCases']),
                          naturalInitOrRoutePassed=False, d02Passed=False)))


if __name__ == '__main__':
    main()
