"""Frozen early-bridge diagnosis and owned Python model; no live or native backend."""
import argparse
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import struct


ROOT = Path(__file__).resolve().parents[1]
EARLY = 'local/migration72/d02-resume-20261109/r2-early-bridge/'
OUTPUT_ROOT = ROOT / 'local/migration72/d02-parallel-20261109/b-sdk-analysis'
CHARS_OFFSET, MAX_UNITS, MAX_UTF8_BYTES = 0x14, 32768, 65535
CALLER_RVA = 0x198035F6
CN_ENVS = (0, 1, 4, 6, 8, 9, 22, 19)
KEY, URL = 'OWNED_KEY_SENTINEL', 'OWNED_URL_SENTINEL'
SOURCE_PINS = {
    'patch/examples/sdk72_config_early_proxy.rs': '4f0de41e9236bc5b22913cbc9992103477b5e7f170916174c3eb1fda607c87ba',
    'patch/examples/sdk72_config_early_proxy/observer.rs': 'e2c009a04d93c2f3653d9a0537b802088f0f0185af0088f72c466a49528335d6',
    'patch/examples/sdk72_config_early_proxy/fixture.rs': '47838c0ab188acd2efdf46d9d5e54971525c2b0140f3c7ad193e8ebf1195d59a',
    'patch/examples/sdk72_config_proxy/observer.rs': 'c93e4da7dd167fca267156e7ee20b7bcb6445c89ed1ff36475552949989c7b75',
    'patch/examples/sdk72_config_proxy/fixture.S': 'f0b7a4769cccaa5fa0d7f2d36999a83339a670d6b3b96d43482cfa1c9a877d63',
    'tools/verify_sdk72_config_early_proxy.py': '0da66cb72b2bfda98673af7ab876bb73829abe26d5e32b541c55ce0dee3837d8',
    'tools/test_verify_sdk72_config_early_proxy.py': '652b9bb0739d74b1fabfb92704c5ea8736ae7be9c0e8f2133878c76a68722414',
    'tools/observe_client72_config_early_proxy.py': 'd2f9a6f0a8d69e6eacba65c838fd9f25bfe4c12167000349dbc05a162ab5702b',
    'tools/test_observe_client72_config_early_proxy.py': '94d0659582517fc37c058a8982d22a603c3653a5e2b5f4fe2e0fb93bb3fcd9a0',
    'patch/Cargo.toml': 'f3a71cfb6415581cddcaf2dbcb654ca012b6ee4ae372b11e2719f89b64cb1a2e',
    'patch/Cargo.lock': '8482fc5d3e86ac5416038e446431e729a5e55c338d8824407f9aab878c323b2f',
    EARLY + 'source-final1/proof.private.json': '8344e740ed4063dbcd03eac62ffdc7e6dc828987cecad5f60b4996b00a96e033',
    EARLY + 'source-final2/proof.private.json': '8344e740ed4063dbcd03eac62ffdc7e6dc828987cecad5f60b4996b00a96e033',
    EARLY + 'source-main-third/proof.private.json': '8344e740ed4063dbcd03eac62ffdc7e6dc828987cecad5f60b4996b00a96e033',
    EARLY + 'owned-main1/proof.private.json': '394964693753e2f52e12cc298d8502ae7bcd8a0094536f48a8b703c2a71c1b04',
    EARLY + 'main-owned-review.private.json': '6b51fa169b4915d8e5738ee1991711fee78bb5e6d59a00551e7c5018a079b573',
    EARLY + 'main-build-epoch2.private.json': '52ccc1769272057fcc652101da0e00514ef9b5dd97156cc9955a0303d1543053',
    EARLY + 'live2.private.json': 'd7f1f483ae293069dbd4611837377c4b076674d7fe04cf42c9a1cc1f79488771',
    EARLY + 'live2.private.json.reads.private.json': 'ad4fe1971aea1a3db7fd384b65d2596cd94560b69480cf9a127ccbf7dcc79c0b',
    EARLY + 'binding.private.rs': 'd1d5ab065b5d5c70c36c90cbc11c84a1d091a9d3a7b764d5829af48f8c190fbc',
    EARLY + 'window-0x534480.instructions.private.json': 'e1b2bac2cc8ba73846184b401748543c29980be05609df253d12eafab9adb280',
    EARLY + 'window-0x507600.instructions.private.json': '84162b3b35ef8f3d13561718ab044f2974d715f04ea1c342bfbceb5a753fbb5a',
    EARLY + 'target/release/examples/sdk72_config_early_proxy.dll': '20186081e073678510d0c162e6bbab1597e8f00b3154861809e90935932854a9',
    EARLY + 'target/release/examples/sdk72_config_early_proxy.exe': 'eb498ab5fbacf1625d6126ae36c70fe40cafe46a56d344379675d1e77ce942f5',
    'local/migration72/d02-resume-20261109/r2-config-select/sync-windows.private.json': '4722fea419f51cb985648ccf0ff5f318c3a32b489c49c25ad8d07c2b25c1a9d1',
    'local/migration72/d02-resume-20261109/r2-config-select/explore-windows.private.json': '8871dd865a9be44f06a2950a8bce9e23ba150d60b9900a731599ace7a280a247',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def frozen_inputs(root=ROOT):
    """Pin read-only evidence first. No configuration/reference plaintext is opened."""
    files = {name: (root / name).read_bytes() for name in SOURCE_PINS}
    for name, expected in SOURCE_PINS.items():
        require(digest(files[name]) == expected, 'Frozen source/evidence identity differs: ' + name)
    return files


def audit_epoch(files):
    load = lambda name: json.loads(files[EARLY + name])
    accepted = load('owned-main1/proof.private.json')
    source = load('source-final2/proof.private.json')
    live = load('live2.private.json')
    review = load('main-owned-review.private.json')
    build = load('main-build-epoch2.private.json')
    inputs = live['inputs']
    require(inputs['artifactAuditSHA256'] == review['artifactAuditSHA256'] ==
            SOURCE_PINS[EARLY + 'owned-main1/proof.private.json'], 'Accepted proof join differs')
    require(source['sources'] == accepted['sources'] == inputs['sources'] == build['sources'], 'Source epoch join differs')
    for name, row in accepted['sources'].items():
        require(row['sha256'] == SOURCE_PINS[name], 'Accepted source pin differs')
    require(accepted['frozenSourcePins']['patch/examples/sdk72_config_proxy/observer.rs'] ==
            SOURCE_PINS['patch/examples/sdk72_config_proxy/observer.rs'], 'Classifier source join differs')
    for field, record, path in (
        ('productionSHA256', accepted['artifact'], 'target/release/examples/sdk72_config_early_proxy.dll'),
        ('fixtureSHA256', accepted['owned']['fixture'], 'target/release/examples/sdk72_config_early_proxy.exe'),
    ):
        require(inputs[field] == record['sha256'] == SOURCE_PINS[EARLY + path], 'Artifact/fixture join differs')
    require(review['production']['sha256'] == inputs['productionSHA256'] == inputs['installedProxy']['sha256'] and
            review['fixture']['sha256'] == inputs['fixtureSHA256'], 'Actual artifact review join differs')
    require(inputs['manifestSHA256'] == review['manifestSHA256'] == build['manifestAfterSHA256'] ==
            SOURCE_PINS['patch/Cargo.toml'] and build['cargoLockSHA256'] == SOURCE_PINS['patch/Cargo.lock'],
            'Manifest/lock join differs')
    require(accepted['owned']['passed'] and len(accepted['owned']['runs']) == 2, 'Accepted owned runs differ')
    for run in accepted['owned']['runs']:
        result = run['publicResult']
        require(run['passed'] and run['ownedEmitterVerified'] and len(result['cases']) == 18 and
                all(result[k] for k in ('gpr16', 'xmm16', 'flags', 'entryStack128', 'entryAndReturnLastError',
                                       'dropRestored', 'returnHigh64', 'sourceObjectsUnchanged',
                                       'unknownCallerZeroNameOrPayloadReads', 'wrongNameZeroPayloadReads')),
                'Accepted ABI result differs')
    raw = load('live2.private.json.reads.private.json')['reads']
    require([row['requested'] for row in raw] == [72, 376, 359, 139, 64, 46, 284, 38, 72] and
            all(not row['error'] and row['bytesRead'] == row['requested'] and
                len(bytes.fromhex(row['bytesHex'])) == row['requested'] for row in raw), 'Frozen raw read ledger differs')
    before, after = (bytes.fromhex(raw[index]['bytesHex']) for index in (0, -1))
    require(before == after, 'Frozen natural state differs')
    fields = struct.unpack('<6IQ10I', before)
    require(fields[:6] == (1, 72, 5, 0, 0, 1) and fields[12:] == (1, 5, 0, 0, 0) and fields[11] == 1,
            'Frozen event/facts differ')
    binding = live['binding']
    require(binding['event'] == dict(status='over-limit', anyEntrySeen=True, expectedCallerSeen=True) and
            binding['status'] == 'installed-early-code-bound' and all(binding[k] for k in
            ('fullTrampolineAndCallbackEqual', 'returnEntryPlus20Verified', 'stableStateEqual')),
            'Live code binding differs')
    require(not any(binding[k] for k in ('nativeMarshalExecuted', 'nativeExportObserved',
                                        'writerReturnObserved', 'routeEffectObserved')), 'Unproved scope differs')
    contract = accepted['staticContract']
    require(contract['charsOffset'] == CHARS_OFFSET and not contract['managedLengthRead'] and
            contract['exactCallerReturnRVA'] == CALLER_RVA and not contract['originalMarshalExecuted'],
            'Managed source contract differs')
    for method in contract['rawTypedParameters']:
        require(not method['flags'] & 0x10 and len(method['parameters']) == 2 and
                all(p['type']['kind'] == 'string' and not p['type']['byRef'] for p in method['parameters']),
                'Actual ABI metadata differs')
    return dict(sourceProofsEqual=True, liveAcceptedSourceAndBuildEpochEqual=True,
                actualArtifactAndFixtureHashesEqual=True, manifestAndLockEqual=True,
                acceptedOwnedFreshRuns=2, acceptedOwnedCasesPerRun=18,
                historicalCodeBound=True, stableHistoricalEvent=5,
                anyEntrySeen=True, expectedCallerSeen=True, historicalClassificationSucceeded=False,
                naturalNameLimitExcluded=True,
                remainingOrigins=['payload-unit-scan-limit', 'payload-utf8-limit'],
                naturalOriginDisambiguated=False, naturalMissingNulProved=False)


def audit_static_paths(files):
    early = files['patch/examples/sdk72_config_early_proxy/observer.rs'].decode()
    classifier = files['patch/examples/sdk72_config_proxy/observer.rs'].decode()
    inspect = early[early.index('    fn inspect('):early.index('\nfn failure(')]
    copy = early[early.index('fn copy_payload('):early.index('\n#[link(')]
    classify = classifier[classifier.index('pub fn classify('):classifier.index('\npub struct Observer')]
    require('OVER_LIMIT' not in inspect and 'failure(frozen_observer::NAME_READ)' in inspect and
            'failure(NON_TARGET_NAME)' in inspect, 'Name exit map differs')
    require(copy.count('frozen_observer::OVER_LIMIT') == 2 and 'OVER_LIMIT' not in classify and
            'if text.len() > MAX_UTF8_BYTES' in copy and 'for index in 0..MAX_UNITS' in copy,
            'Payload/classifier exit map differs')
    windows = json.loads(files['local/migration72/d02-resume-20261109/r2-config-select/sync-windows.private.json'])
    windows += json.loads(files['local/migration72/d02-resume-20261109/r2-config-select/explore-windows.private.json'])
    by_rva = {w['rva']: {a - 0x140000000: (m, o) for a, _, m, o in w['instructions']} for w in windows}
    bridge, implementation = by_rva['0x6d80910'], by_rva['0x6e363b0']
    bridge_expected = {
        0x6D80924: ('mov', 'rsi, r8'), 0x6D80927: ('mov', 'rdi, rdx'),
        0x6D809AD: ('mov', 'rcx, rdi'), 0x6D809B0: ('call', '0x140534480'),
        0x6D809B8: ('mov', 'rcx, rsi'), 0x6D809BB: ('call', '0x140534480'),
        0x6D809C3: ('mov', 'rcx, rdi'), 0x6D809C6: ('mov', 'rdx, rax'),
    }
    require(all(bridge[rva] == value for rva, value in bridge_expected.items()) and
            implementation[0x6E363E2] == ('mov', 'rdx, rdi') and
            implementation[0x6E363E5] == ('mov', 'r8, rsi') and
            implementation[0x6E363EE] == ('jmp', '0x146d80910') and
            by_rva['0x198035c0'][0x198035F1] == ('call', '0x159806d70'),
            'Bridge/implementation/caller argument chain differs')
    marshal, scan = [
        {row['rva']: (row['mnemonic'], row['operands']) for row in json.loads(files[EARLY + name])}
        for name in ('window-0x534480.instructions.private.json', 'window-0x507600.instructions.private.json')
    ]
    require(marshal[0x53449C] == ('add', 'rdx, 0x14') and marshal[0x5344A7] == ('call', '0x140507600') and
            scan[0x507615] == ('cmp', 'word ptr [rdx], 0') and
            scan[0x507634] == ('cmp', 'word ptr [rsi + rax*2 + 2], 0') and
            scan[0x50763E] == ('jne', '0x140507630'), 'Actual managed representation differs')
    producer = next(w for w in windows if w['rva'] == '0x19806d70')
    ins = {a - 0x140000000: (m, o) for a, _, m, o in producer['instructions']}
    expected = {
        0x19807756: ('mov', 'edx, dword ptr [rax + 0x58]'),
        0x19807759: ('mov', 'r8b, 1'),
        0x1980775C: ('call', '0x159776120'),
        0x1980793C: ('call', '0x159b94f30'),
        0x19807941: ('mov', 'rsi, rax'),
        0x1980797A: ('mov', 'r8, rsi'),
        0x19807989: ('jmp', 'r10'),
    }
    require(all(ins[rva] == instruction for rva, instruction in expected.items()), 'Producer parameter chain differs')
    return dict(nameFailureEvents=[3, 9], payloadLimitEvent=5, payloadLimitExitCount=2,
                classifierCanEmitLimit=False, entryRVA=0x6D80910, exactCallerReturnRVA=CALLER_RVA,
                registers=dict(rcx='this', rdx='managed name', r8='managed payload'),
                exactBridgeAndImplementationInstructionsEqual=True,
                exactMarshalHelperInstructionsEqual=True,
                charsOffset=CHARS_OFFSET, managedLengthRead=False, terminatorUnitBytes=2,
                loaderCallRVA=0x1980775C, loaderEnvSource='ConfigModel+0x58', loaderSecondArgument=True,
                producerToStringRVA=0x1980793C, producerTailRVA=0x19807989,
                naturalEnvSelected=False, naturalNativeInitReturned=False, naturalRouteObserved=False,
                uiProducerRequiredForThisDiagnosis=False)


@dataclass(frozen=True)
class Outcome:
    event: int
    origin: str
    env: int = 0
    key_match: bool = False
    url_match: bool = False


class OwnedMemory:
    """Only immutable owned bytes. Addresses are labels; never OS pointers."""
    def __init__(self, segments):
        self.segments = tuple((base, bytes(raw)) for base, raw in segments)
        self.reads = []

    def read_u16(self, address):
        self.reads.append(address)
        for base, raw in self.segments:
            if base <= address and address + 2 <= base + len(raw):
                return struct.unpack_from('<H', raw, address - base)[0]
        return None


def classify_owned(text):
    """Independent JSON oracle for owned cases, not a general serde equivalence claim."""
    try:
        obj = json.loads(text, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except ValueError:
        return Outcome(6, 'invalid-json')
    if not isinstance(obj, dict):
        return Outcome(8, 'unsupported-shape')
    env = obj.get('env')
    if type(env) not in (int, float) or not math.isfinite(env) or not 0 <= env <= 0xFFFFFFFF or int(env) != env or env not in CN_ENVS:
        return Outcome(7, 'unsupported-env')
    if not isinstance(obj.get('app_key'), str) or not isinstance(obj.get('url'), str):
        return Outcome(8, 'unsupported-shape')
    try:
        routes = json.loads(obj['url'])
    except ValueError:
        return Outcome(6, 'invalid-json')
    if not isinstance(routes, dict) or not isinstance(routes.get('comboLogin'), str):
        return Outcome(8, 'unsupported-shape')
    return Outcome(2, 'classified', int(env), obj['app_key'] == KEY, routes['comboLogin'] == URL)


def inspect_owned(memory, name, payload, known_caller=True, terminal=False):
    """Python path model of the frozen callback, with separate explanatory origins."""
    if terminal or not known_caller:
        return Outcome(0, 'terminal-no-read' if terminal else 'unknown-caller')
    for index, expected in enumerate(b'local_config\0'):
        unit = memory.read_u16(name + CHARS_OFFSET + index * 2)
        if unit is None:
            return Outcome(3, 'name-read')
        if unit != expected:
            return Outcome(9, 'non-target-name')
    raw = bytearray()
    for index in range(MAX_UNITS):
        unit = memory.read_u16(payload + CHARS_OFFSET + index * 2)
        if unit is None:
            return Outcome(4, 'payload-read')
        if unit == 0:
            try:
                text = raw.decode('utf-16-le', 'strict')
            except UnicodeDecodeError:
                return Outcome(10, 'invalid-utf16')
            if len(text.encode('utf-8')) > MAX_UTF8_BYTES:
                return Outcome(5, 'payload-utf8-limit')
            return classify_owned(text)
        raw.extend(struct.pack('<H', unit))
    return Outcome(5, 'payload-unit-scan-limit')


def managed(text=None, *, units=None, terminated=True):
    raw = text.encode('utf-16-le') if text is not None else struct.pack('<' + 'H' * len(units), *units)
    return bytes([0xA5]) * CHARS_OFFSET + raw + (b'\0\0' if terminated else b'')


def packet(**changes):
    obj = dict(env=0, app_key=KEY, url=json.dumps(dict(comboLogin=URL), separators=(',', ':')))
    obj.update(changes)
    return json.dumps(obj, ensure_ascii=False, separators=(',', ':'))


def utf8_boundary_packet(target):
    base = packet(ignored='')
    three_byte, one_byte = divmod(target - len(base.encode()), 3)
    return packet(ignored='\u0800' * three_byte + 'a' * one_byte)


def owned_cases():
    name, payload = 0x8000000200000000, 0x8000000300000000
    exact = managed('local_config')
    base = packet(ignored='')
    unit_boundary = packet(ignored='a' * (MAX_UNITS - 1 - len(base)))
    late_nul = packet(ignored='a' * (MAX_UNITS - len(base)))
    specifications = [
        ('exact', exact, managed(packet()), True, 2, 'classified'),
        ('unknown-caller', b'', b'', False, 0, 'unknown-caller'),
        ('unreadable-name', b'', b'', True, 3, 'name-read'),
        ('long-name', managed('local_config_extra'), b'', True, 9, 'non-target-name'),
        ('case-name', managed('Local_config'), b'', True, 9, 'non-target-name'),
        ('unreadable-payload', exact, b'', True, 4, 'payload-read'),
        ('invalid-utf16', exact, managed(units=[0xD800]), True, 10, 'invalid-utf16'),
        ('unit-last-slot-nul', exact, managed(unit_boundary), True, 2, 'classified'),
        ('unit-next-slot-real-nul', exact, managed(late_nul), True, 5, 'payload-unit-scan-limit'),
        ('unit-identical-prefix-no-nul', exact, managed(late_nul, terminated=False), True, 5, 'payload-unit-scan-limit'),
        ('utf8-last-allowed-byte', exact, managed(utf8_boundary_packet(MAX_UTF8_BYTES)), True, 2, 'classified'),
        ('utf8-first-rejected-byte', exact, managed(utf8_boundary_packet(MAX_UTF8_BYTES + 1)), True, 5, 'payload-utf8-limit'),
        ('url-object', exact, managed(packet(url={})), True, 8, 'unsupported-shape'),
        ('reference-mismatch', exact, managed(packet(app_key='OWNED_DIFFERENT')), True, 2, 'classified'),
        ('unknown-env', exact, managed(packet(env=2)), True, 7, 'unsupported-env'),
        ('invalid-json', exact, managed('{OWNED_BODY_SENTINEL'), True, 6, 'invalid-json'),
        ('nul-tail-unread', exact, managed(packet()) + b'OWNED_TAIL_SENTINEL', True, 2, 'classified'),
        ('swapped-parameters', managed(packet()), exact, True, 9, 'non-target-name'),
    ]
    result = []
    for label, name_bytes, payload_bytes, known, expected, origin in specifications:
        memory = OwnedMemory([(name, name_bytes), (payload, payload_bytes)])
        outcome = inspect_owned(memory, name, payload, known)
        require((outcome.event, outcome.origin) == (expected, origin), 'Owned case result differs: ' + label)
        first_reads = len(memory.reads)
        inspect_owned(memory, name, payload, terminal=True)
        require(len(memory.reads) == first_reads, 'Owned terminal event read again')
        name_reads = sum(name <= address < payload for address in memory.reads)
        payload_reads = first_reads - name_reads
        require(first_reads <= 13 + MAX_UNITS and all(address >= name + CHARS_OFFSET if address < payload else
                address >= payload + CHARS_OFFSET for address in memory.reads), 'Owned read boundary differs')
        result.append(dict(case=label, event=outcome.event, origin=outcome.origin,
                           nameReads=name_reads, payloadReads=payload_reads,
                           keyMatches=outcome.key_match, urlMatches=outcome.url_match,
                           terminalRepeatReads=0))
    return result


def run_audit():
    files = frozen_inputs()
    epoch = audit_epoch(files)
    static = audit_static_paths(files)
    owned = owned_cases()
    return dict(schema=1, taskIDs=['R2-CONFIGSYNC-EARLY-BRIDGE', 'R2-ROUTE-CONFIG-SELECT'],
                sourceSHA256=digest(Path(__file__).read_bytes()), frozenSourcePins=SOURCE_PINS,
                frozenEpoch=epoch, staticInference=static, ownedPythonModel=owned,
                nativeExecutionThisRun=False, actualOwnedAbiReexecuted=False, clientStarted=False,
                naturalPayloadRead=False, naturalPayloadHashOrLengthRecorded=False,
                nativeMarshalExecuted=False, nativeExportObserved=False, writerReturnObserved=False,
                naturalInitEnvOrRoutePassed=False, d02Passed=False,
                minimalImplementation=dict(legacyEvent5='over-limit; historical origin remains ambiguous',
                    event11='payload-unit-scan-limit', event12='payload-utf8-limit', stateBytes=72,
                    maxUnits=MAX_UNITS, maxUtf8Bytes=MAX_UTF8_BYTES,
                    naturalUnitReadCap=32781, naturalBytesCap=65562, collectorReads=9, collectorBytes=1450,
                    originalForwardingUnchanged=True, newHooks=0, extraNaturalReads=0))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    require(output.is_relative_to(OUTPUT_ROOT.resolve()), 'Output is outside the assigned private directory')
    require(not output.exists(), 'Output already exists')
    report = run_audit()
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(report, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(status='bounded-audit-passed', ownedCases=len(report['ownedPythonModel']),
                          naturalOriginDisambiguated=False, d02Passed=False)))


if __name__ == '__main__':
    main()
