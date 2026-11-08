"""Bounded CN72 Config contract and immutable owned-memory collector; no live backend."""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / 'local/migration72/d02-resume-20261108/r2-natural-config'
SDK_SHA = 'adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25'
AGGREGATE_RVA, CONFIG_ROOT_RVA, CONFIG_VTABLE_RVA = 0x7acc80, 0x7e79a0, 0x6c6cd8
ENV_OFFSET, APP_KEY_OFFSET, URL_SHARED_OFFSET = 0x8a0, 0x218, 0x860
LOOKUP_KEY = b'comboLogin'
MAX_NODES, MAX_MAP_SIZE, MAX_MAP_KEY_BYTES = 32, 305, 64
MAX_URL_BYTES, MAX_APP_KEY_BYTES = 256, 128
MAX_READS, MAX_BYTES = 76, 4644
CN_ENVS = (0, 1, 4, 6, 8, 9, 22, 19)
CN_HOSTS = ('hk4e-sdk.mihoyo.com', 'sandbox-sdk.mihoyo.com', 'preapi-takumi.mihoyo.com',
            'devapi-takumi.mihoyo.com', 'ptsapi-takumi.mihoyo.com', 'api-beta-sdk.mihoyo.com',
            'audit-api.mihoyo.com', 'hotfix-sdk.mihoyo.com')
COMBO_PATH = '/hk4e_cn/combo/granter/login/v2/login'
ALLOWED_ROUTES = {('https://' + host + COMBO_PATH).encode('ascii'): 'cn-env-' + str(env)
                  for env, host in zip(CN_ENVS, CN_HOSTS)}
PINNED = {
    'local/migration72/d02-resume-20261108/initial-inventory.private.json': 'f891bafd31c950985ce214cad90e0ba24d2e8733a24dbdca21f9823474c6ff25',
    'local/migration72/d02-active/sdk-login-chain-astra/rea-final.json': '94ff59a7900378914d1ee387616bf81412bc0be306090f6c9db563b5e0e091a6',
    'local/migration72/d02-resume-20261108/r2-route/rea-final.json': 'da35fb0b37ef5b661a3b6c76040b9bb3ba1c4119d949ccda06bd28e14603dc53',
    'local/migration72/d02-resume-20261108/r2-route/main-third.json': 'bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471',
    'local/migration72/d02-active/main/cold-arrays-tool/live-elevated1.json': '63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0',
    'local/migration72/d02-active/sdk-crypto-contract-astra/final-first.json': 'c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313',
    'tools/sample_client72_initialization.py': '2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae',
    'tools/client72_sdk_login_chain.py': '53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2',
    'tools/client72_sdk_route_loader.py': 'd4816a4448ef2533d20ab7bc656ef617888a79ded69fb3416c5defe24fdf6cac',
    'tools/client72_sdk_combo_signature.py': 'cca96bb3a066c8e3322bd3a63ceed2a994627e714e6a6a85c145bb9afbe15f9e',
}


def digest(data):
    """Only callers with code/evidence file bytes use this helper, never app_key or URL."""
    return hashlib.sha256(data).hexdigest()


class ContractStop(Exception):
    """A fixed classification, containing no address, string, payload or derived identity."""


@dataclass(frozen=True, repr=False)
class ReferenceRow:
    env: int
    app_key: bytes
    combo_url: bytes


def references_from_server_pc_bytes(resource):
    """In-memory conversion only. Caller first proves the exact class49 text/carrier identity.

    No app-key values, lengths or hashes are produced or retained in a report. This function does
    not read/re-unpack a bundle or authorize a natural memory backend.
    """
    rows = json.loads(resource)
    if not isinstance(rows, list):
        raise ValueError('Expected the fixed server_pc array')
    result = []
    for row in rows:
        if (not isinstance(row, dict) or type(row.get('env')) is not int
                or not isinstance(row.get('app_key'), str) or not isinstance(row.get('url'), dict)
                or not isinstance(row['url'].get('comboLogin'), str)):
            raise ValueError('Invalid fixed resource row shape')
        result.append(ReferenceRow(row['env'], row['app_key'].encode('utf-8'),
                                   row['url']['comboLogin'].encode('utf-8')))
    return tuple(result)


class OwnedMemory:
    """Copies caller-owned byte segments; cannot open/read a process or execute a target."""
    def __init__(self, segments):
        self.segments = tuple(sorted((address, bytes(data)) for address, data in segments.items()))
        end = 0
        for address, data in self.segments:
            if not 0 < address < 2**64 or not data or address < end or address + len(data) > 2**64:
                raise ValueError('Owned memory segments overlap or exceed the 64-bit address space')
            end = address + len(data)
        self.reads = 0
        self.bytes_read = 0

    def read(self, address, size):
        if not 0 < address < 2**64 or not 0 < size <= 256 or address + size > 2**64:
            raise ContractStop('invalid-read-range')
        if self.reads >= MAX_READS or self.bytes_read + size > MAX_BYTES:
            raise ContractStop('read-budget-exhausted')
        self.reads += 1
        self.bytes_read += size
        for start, data in self.segments:
            if start <= address and address + size <= start + len(data):
                return data[address - start:address - start + size]
        raise ContractStop('owned-range-unavailable')


def u64(data, offset=0):
    return struct.unpack_from('<Q', data, offset)[0]


def bounded_string(memory, header, bound, failure):
    length, capacity = u64(header, 16), u64(header, 24)
    if length > bound or length > capacity:
        raise ContractStop(failure)
    if capacity < 16:
        if capacity != 15 or length > 15:
            raise ContractStop(failure)
        return header[:length]
    pointer = u64(header)
    if not pointer:
        raise ContractStop(failure)
    return memory.read(pointer, length) if length else b''


def config_header(memory, sdk_base, result):
    aggregate = u64(memory.read(sdk_base + AGGREGATE_RVA, 8))
    config = u64(memory.read(sdk_base + CONFIG_ROOT_RVA, 8))
    if not aggregate or not config:
        raise ContractStop('config-root-not-initialized')
    component = u64(memory.read(aggregate + 0x40, 8))
    result['configRootAgreement'] = component == config
    if component != config:
        raise ContractStop('config-root-disagreement')
    matches = u64(memory.read(config, 8)) == sdk_base + CONFIG_VTABLE_RVA
    result['configVtableMatches'] = matches
    if not matches:
        raise ContractStop('config-vtable-mismatch')
    result['storedEnv'] = struct.unpack('<i', memory.read(config + ENV_OFFSET, 4))[0]
    return config


def combo_value(memory, url_object):
    descriptor = memory.read(url_object, 16)
    head, count = u64(descriptor), u64(descriptor, 8)
    if not head or count > MAX_MAP_SIZE:
        raise ContractStop('url-map-descriptor-outside-contract')
    sentinel = memory.read(head, 32)
    node = u64(sentinel, 8)
    if sentinel[25] != 1 or (count == 0) != (node == head):
        raise ContractStop('url-map-sentinel-mismatch')
    for _ in range(MAX_NODES):
        if node == head:
            return None
        entry = memory.read(node, 64)  # links/isnil and key only; no non-target value.
        if entry[25] != 0:
            raise ContractStop('url-map-node-isnil-mismatch')
        key = bounded_string(memory, entry[32:64], MAX_MAP_KEY_BYTES, 'url-map-key-invalid')
        if key == LOOKUP_KEY:
            header = memory.read(node + 64, 32)
            return bounded_string(memory, header, MAX_URL_BYTES, 'combo-url-invalid')
        node = u64(entry, 16 if key < LOOKUP_KEY else 0)  # unsigned-byte lexicographic order.
    if node == head:
        return None
    raise ContractStop('url-map-node-budget-exhausted')


def collect_owned_snapshot(memory, sdk_base, references):
    """Pure collector over immutable owned segments, NOT a live-read authorization.

    A natural map/key read needs an independently established interval without Init/reload/teardown.
    The current getter and writer lock a mutex; immutable owner alone does not protect nodes.
    """
    result = {
        'task': 'R2-NATURAL-CONFIG', 'scope': 'owned-immutable-memory-only',
        'liveBackendImplemented': False, 'naturalDeepReadApproved': False,
        'nativeInitCompletionObserved': False, 'snapshotStatus': 'pending',
        'configRootAgreement': None, 'configVtableMatches': None, 'storedEnv': None,
        'appKeyComparisonCompleted': False, 'appKeyEqualsResourceEnvs': [],
        'comboLookupStatus': 'not-read', 'storedComboRouteClass': 'not-read',
        'comboEqualsResourceEnvs': [], 'getterFallbackExpectedFromStaticContract': False,
        'storedEnvAndResourceUrlAgree': None,
        'budget': {'maximumReads': MAX_READS, 'maximumBytes': MAX_BYTES, 'maximumNodes': MAX_NODES},
    }
    try:
        config = config_header(memory, sdk_base, result)
        key = bounded_string(memory, memory.read(config + APP_KEY_OFFSET, 32),
                             MAX_APP_KEY_BYTES, 'app-key-unavailable')
        result['appKeyEqualsResourceEnvs'] = sorted(row.env for row in references if key == row.app_key)
        result['appKeyComparisonCompleted'] = True
        del key
        shared = memory.read(config + URL_SHARED_OFFSET, 16)
        url_object, control = u64(shared), u64(shared, 8)
        if not url_object or not control:
            raise ContractStop('url-object-not-initialized')
        value = combo_value(memory, url_object)
        result['comboLookupStatus'] = 'absent' if value is None else 'present'
        result['storedComboRouteClass'] = 'absent' if value is None else ALLOWED_ROUTES.get(value, 'unknown')
        result['getterFallbackExpectedFromStaticContract'] = value is None or value == b''
        if value is not None:
            result['comboEqualsResourceEnvs'] = sorted(row.env for row in references if value == row.combo_url)
            result['storedEnvAndResourceUrlAgree'] = result['storedEnv'] in result['comboEqualsResourceEnvs']
        result['snapshotStatus'] = 'collected-owned-only'
    except ContractStop as error:
        result['snapshotStatus'] = str(error)
    # Actual byte/read totals are intentionally omitted: they could expose app-key length/SSO state.
    return result


def public_formatter(result):
    """Return only the collector's explicit public schema; never serialize memory or references."""
    allowed = ('task', 'scope', 'liveBackendImplemented', 'naturalDeepReadApproved',
               'nativeInitCompletionObserved', 'snapshotStatus', 'configRootAgreement',
               'configVtableMatches', 'storedEnv', 'appKeyComparisonCompleted',
               'appKeyEqualsResourceEnvs', 'comboLookupStatus', 'storedComboRouteClass',
               'comboEqualsResourceEnvs', 'getterFallbackExpectedFromStaticContract',
               'storedEnvAndResourceUrlAgree', 'budget')
    return {key: result[key] for key in allowed}


def contract_metadata():
    return {
        'task': 'R2-NATURAL-CONFIG', 'sdkSHA256': SDK_SHA,
        'naturalDeepReadApproved': False, 'liveBackendImplemented': False,
        'rootAndEmbeddedScalarPlan': {'maximumReads': 5, 'maximumBytes': 36,
                                      'initializationCompletionStillRequired': True},
        'ownedSnapshotPlan': {'maximumReads': MAX_READS, 'maximumBytes': MAX_BYTES,
                              'maximumNodes': MAX_NODES, 'maximumMapSize': MAX_MAP_SIZE,
                              'maximumMapKeyBytes': MAX_MAP_KEY_BYTES, 'maximumUrlBytes': MAX_URL_BYTES,
                              'appKeyLengthBytesAndHashReported': False},
        'rootChain': {'aggregateCellRva': AGGREGATE_RVA, 'configSingletonCellRva': CONFIG_ROOT_RVA,
                      'aggregateConfigOffset': 0x40, 'configVtableRva': CONFIG_VTABLE_RVA,
                      'envOffset': ENV_OFFSET, 'appKeyOffset': APP_KEY_OFFSET,
                      'urlSharedPointerOffset': URL_SHARED_OFFSET},
        'missingNaturalBoundary': 'Init 378850 completed, with no Init/380E60/reload/teardown writer during any deep read',
        'noNewReadInCurrentTransparentLoaderExperiment': True,
    }


class SdkImage:
    """Only used after the exact complete SDK file digest is validated once."""
    def __init__(self, data):
        self.data = data
        pe = struct.unpack_from('<I', data, 60)[0]
        optional = pe + 24
        count = struct.unpack_from('<H', data, pe + 6)[0]
        optional_size = struct.unpack_from('<H', data, pe + 20)[0]
        self.base = struct.unpack_from('<Q', data, optional + 24)[0]
        self.sections = [struct.unpack_from('<8sIIIIIIHHI', data, optional + optional_size + 40 * i)
                         for i in range(count)]
        rva, size = struct.unpack_from('<II', data, optional + 112 + 3 * 8)
        entries = self.at(rva, size)
        self.functions = list(struct.iter_unpack('<III', entries))

    def at(self, rva, length):
        for _, _, start, size, offset, *_ in self.sections:
            if start <= rva and rva + length <= start + size:
                offset += rva - start
                return self.data[offset:offset + length]
        raise ValueError('Selected fixed SDK window is not file-backed')

    def body(self, rva):
        if rva == 0x363450:
            return rva, rva + 8
        rows = [(start, end) for start, end, _ in self.functions if start <= rva < end]
        if len(rows) != 1:
            raise ValueError('Selected fixed SDK function has no unique pdata body')
        return rows[0]


def fixed_evidence(client):
    """Read-only existing evidence and eight SDK bodies; no engine, decode or native execution."""
    for relative, expected in PINNED.items():
        if digest((ROOT / relative).read_bytes()) != expected:
            raise ValueError('Frozen input/source identity changed: ' + relative)
    inventory = json.loads((ROOT / 'local/migration72/d02-resume-20261108/initial-inventory.private.json').read_bytes())
    files = {}
    sdk_data = None
    for row in inventory['inputs']:
        path = client / row['file']
        if row['file'] == 'YuanShen_Data/Plugins/AccountPlatNative.dll':
            sdk_data = path.read_bytes()
            actual = digest(sdk_data)
        else:
            with path.open('rb') as stream:
                actual = hashlib.file_digest(stream, 'sha256').hexdigest()
        if actual != row['sha256'] or path.stat().st_size != row['size']:
            raise ValueError('Fixed client input changed: ' + row['file'])
        files[row['file']] = actual
    image = SdkImage(sdk_data)
    route_rea = json.loads((ROOT / 'local/migration72/d02-resume-20261108/r2-route/rea-final.json').read_bytes())
    records = {int(row['parameters']['procedure'], 16) - image.base: row
               for row in route_rea['records'] if row.get('operation') == 'procedure_pseudo_code'}
    roles = {
        0x1538e0: ('aggregate_config_publish', ('DAT_1807e79a0', '0xa98', 'FUN_1803637d0', 'param_1[8] = DAT_1807e79a0')),
        0x3637d0: ('config_constructor', ('MiHoYoSDKConfigManager::vftable', 'FUN_180362d50', 'FUN_1803751e0')),
        0x3751e0: ('request_url_shared_owner', ('MiHoYoSDKRequestUrl', 'FUN_18037ac60(param_1 + 0x860', '_Mtx_init_in_situ(plVar4 + 4')),
        0x378850: ('init_env_key_url_writer', ('&DAT_18069409c,3', 'param_1 + 0x114', '"app_key"', 'param_1 + 0x43', 'FUN_180380e60')),
        0x376ea0: ('config_route_reader', ('param_1 + 0x860', 'FUN_18037e3e0', 'FUN_180364910')),
        0x37e3e0: ('ordered_map_reader', ('FUN_18045e920(param_1 + 2)', 'puVar6 + 4', 'puVar7 + 8', '_Mtx_unlock(param_1 + 2)')),
        0x380e60: ('map_clear_and_rebuild_writer', ('FUN_18045e920(param_1 + 2)', 'FUN_180166270', 'FUN_18018f1e0', '_Mtx_unlock(param_1 + 2)')),
    }
    code = {}
    for rva, (role, assertions) in roles.items():
        row = records[rva]
        pseudocode = row['normalized_result']
        if not all(text in pseudocode for text in assertions):
            raise ValueError('Exact reviewed producer/reader assertion changed')
        start, end = image.body(rva)
        code[hex(rva)] = {'role': role, 'startRva': start, 'endRva': end,
                          'codeSHA256': digest(image.at(start, end - start)),
                          'reaEvidenceId': row['evidence_id'], 'authority': 'fixed-code plus existing REA; not newly executed'}
    start, end = image.body(0x363450)
    if image.at(start, end - start) != bytes.fromhex('488d81e8000000c3'):
        raise ValueError('Exact ConfigModel getter body changed')
    code['0x363450'] = {'role': 'embedded_config_model_getter', 'startRva': start, 'endRva': end,
                        'codeSHA256': digest(image.at(start, end - start)), 'authority': 'exact eight-byte leaf body'}
    slots = {hex(slot): u64(image.at(CONFIG_VTABLE_RVA + slot, 8)) - image.base for slot in (0, 0x60, 0x80, 0xd8)}
    if slots != {'0x0': 0x364220, '0x60': 0x373d10, '0x80': 0x376ea0, '0xd8': 0x363450}:
        raise ValueError('Config vtable reader identity changed')
    if image.at(0x69409c, 4) != b'env\0':
        raise ValueError('Exact Init env literal changed')
    crypto = json.loads((ROOT / 'local/migration72/d02-active/sdk-crypto-contract-astra/final-first.json').read_bytes())
    held_reader = crypto['codeWindows']['0x37e3e0']
    instructions = {int(address, 16): (mnemonic, operands) for address, mnemonic, operands in held_reader['instructions']}
    expected_instructions = {
        0x37e3fe: ('mov', 'rbx, r8'), 0x37e404: ('mov', 'r14, rcx'),
        0x37e41f: ('add', 'rcx, 0x10'), 0x37e423: ('call', '0x18045e920'),
        0x37e428: ('test', 'eax, eax'), 0x37e42a: ('jne', '0x18037e5b4'),
        0x37e430: ('xor', 'ebp, ebp'), 0x37e466: ('mov', 'rsi, qword ptr [r14]'),
        0x37e51c: ('lea', 'rcx, [r14 + 0x10]'), 0x37e520: ('call', '0x18045e944'),
        0x37e525: ('test', 'rbx, rbx'),
    }
    if (any(instructions.get(address) != instruction for address, instruction in expected_instructions.items())
            or held_reader['sha256'] != code['0x37e3e0']['codeSHA256']):
        raise ValueError('Exact successful mutex-held reader boundary changed')
    natural = json.loads((ROOT / 'local/migration72/d02-active/main/cold-arrays-tool/live-elevated1.json').read_bytes())
    if (natural['sdkResult'] != 'roots-sampled-not-a-request' or natural['sdkModuleBinding'] != 'file-entry-matches'
            or not natural['sdkRoots']['engine']['vtableMatches'] or not natural['sdkRoots']['networkManager']['vtableMatches']):
        raise ValueError('Reviewed 99span SDK-root classifications changed')
    # Reuse previously reviewed exact class49 output; no re-decode and no config/key plaintext hash.
    bundle = client / 'YuanShen_Data/StreamingAssets/MiHoYoSDKRes/PC/mihoyo_sdk_res'
    if digest(bundle.read_bytes()) != '8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda':
        raise ValueError('Previously proved resource carrier changed')
    artifacts = ROOT / 'local/migration72/d02-resume-20261108/r2-route/main-third'
    resource = (artifacts / 'server_pc.private.json').read_bytes()
    with (artifacts / 'mihoyo_sdk_res.serialized.private.bin').open('rb') as stream:
        stream.seek(5995140)
        same = stream.read(475950) == resource
    if not same:
        raise ValueError('Reused private class49 text differs from its proved exact extent')
    references = references_from_server_pc_bytes(resource)
    envs = [row.env for row in references]
    if envs != [0, 1, 4, 6, 8, 9, 22, 19, 2, 3, 5, 7, 11, 12, 21, 20]:
        raise ValueError('Fixed resource environment order changed')
    del references, resource
    return {'fixedClientInputsRevalidated': True, 'fixedInputs': files, 'codeWindows': code,
            'configVtableSlots': slots, 'frozenInputsAndSources': PINNED,
            'previousNaturalRootEvidence': {'sdkRootClassifications': natural['sdkRoots'],
                                             'configPreviouslySampled': False, 'currentPidOrAddressReused': False},
            'reusedResource': {'carrierSHA256': '8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda',
                               'class49TextEqualsProvedCachedExtent': same, 'referenceEnvOrder': envs,
                               'appKeyBytesLengthOrHashRecorded': False, 'freshDecodeExecuted': False},
            'lifetimeBoundary': {'ownerPublishedBySingletonProducer': True,
                                 'deletingDestructorRva': 0x364220, 'destructorCallersNotClosed': True,
                                 'mapWriterFreesNodesUnderMutex': True, 'mapReaderLocksSameMutex': True,
                                 'naturalQuiescentIntervalProved': False},
            'minimalFutureEvents': {
                'urlMap': {'successfulLockCallRva': 0x37e423, 'heldEntryRva': 0x37e430,
                           'unlockCallRva': 0x37e520, 'unlockReturnRva': 0x37e525,
                           'urlObjectRegister': 'R14', 'publicLookupKeyRegister': 'RBX',
                           'protectsConfigEnvOrAppKey': False, 'hookOrObservationImplemented': False},
                'appKey': {'reviewedCallerCallRva': 0x3bea13, 'requiredCallerReturnRva': 0x3bea16,
                           'signerEntryRva': 0x3958e0, 'keyRegister': 'R9',
                           'source': 'ConfigModel+130/config+218', 'requestBodyReadAllowed': False,
                           'separateEntryBudgetAndAbiReviewStillRequired': True}}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--client', type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(RAW.resolve()) or output.exists():
        parser.error('Choose a new output inside this task raw directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    result = contract_metadata()
    result['sourceSHA256'] = digest(Path(__file__).read_bytes())
    result['fixedClientInputsRevalidated'] = False
    result['currentClientMemoryRead'] = False
    result['reason'] = 'preparation-only; full fixed-input validation is a separate main step after stock restoration'
    if args.client:
        result.update(fixed_evidence(args.client.resolve()))
        result['reason'] = 'fixed disk/evidence checks and owned-memory preparation only; no live backend'
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'task': result['task'], 'scope': 'pure-contract-preparation',
                      'liveBackendImplemented': False, 'naturalDeepReadApproved': False}))


if __name__ == '__main__':
    main()
