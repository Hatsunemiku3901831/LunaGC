"""Bounded stock managed-route inputs. Sensitive reads remain pending until seal."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = 'local/migration72/d02-resume-20261110/b-managed-route-stock/'
SPEC = 'docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_SPEC_20261110.md'
PINS = {SPEC: '606814b70431e5ed208a4f5c71ca87760b5e6bf2c513012b3f525fbe9354ae94',
    PRIVATE+'implementation-spec.private.json': '3807982afbdab44f79b24b9a206e8bdbf216c7f8856192adeede7196b1b3b382',
    PRIVATE+'frozen-inputs.private.json': 'cb5e66330892e6a081f94545df82ed5697f2966c06d69bb7a0bf4d9f83808b5c',
    PRIVATE+'audit-first.private.json': '5cc9a55d521174813f21b9c8e21655d9b36e36ee3a0c917f323af4bf47f7c532',
    PRIVATE+'audit-repeat.private.json': '5cc9a55d521174813f21b9c8e21655d9b36e36ee3a0c917f323af4bf47f7c532'}
ENVS = (0, 1, 4, 6, 8, 9, 22, 19)
HOSTS = ('hk4e-sdk.mihoyo.com', 'sandbox-sdk.mihoyo.com', 'preapi-takumi.mihoyo.com',
    'devapi-takumi.mihoyo.com', 'ptsapi-takumi.mihoyo.com', 'api-beta-sdk.mihoyo.com',
    'audit-api.mihoyo.com', 'hotfix-sdk.mihoyo.com')
BASES = {('https://'+host+'/hk4e_cn/combo/granter/login/v2/login').encode('utf-16-le'): env
         for host, env in zip(HOSTS, ENVS)}
SENSITIVE = frozenset((9, 11, 12, 13))
NAMES = ('common-storage', 'config-type-usage', 'config-reference', 'config-object-class',
    'managed-env', 'url-type-usage', 'url-reference', 'url-object-class', 'combo-reference',
    'string-type-usage', 'string-object-class', 'string-length', 'string-characters')
CAPS = {'firstMaxReads': 13, 'firstMaxBytes': 226, 'totalMaxReads': 26,
        'totalMaxBytes': 452, 'maxSingleBytes': 138}
U64 = (1 << 64) - 1


def utc():
    return datetime.now(timezone.utc).isoformat()


def file_sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_sources(root=ROOT):
    for path, expected in PINS.items():
        if file_sha(root/path) != expected:
            raise ValueError('managed-route-frozen-source-differed')
    table = json.loads((root/(PRIVATE+'frozen-inputs.private.json')).read_bytes())
    for row in table['files']:
        # Source bytes only: never execute PE, unpickle metadata, or import old heap.
        if (root/row['path']).stat().st_size != row['bytes'] or file_sha(root/row['path']) != row['sha256']:
            raise ValueError('managed-route-audited-input-differed')
    return {'passed': True, 'sourcePins': dict(PINS), 'auditedInputCount': len(table['files']),
            'contextAtReadIsNotCurrentGate': True, 'sourceBytesOnly': True, 'metadataUnpickled': False}


class Stop(ValueError):
    pass


class PendingCapture:
    """Not JSON serializable; repr and exceptions never contain provisional input."""
    def __init__(self, module, now):
        self.module, self.now = module, now
        self.records, self.first = [], []
        self.outcome, self.env, self.route = 'invalid-entry', None, None
        self.final_ok = False

    def __repr__(self):
        return '<PendingManagedRoute: memory-only>'

    def read(self, callback, ordinal, address, width, phase='first'):
        if type(address) is not int or not 0 < address <= U64 - width + 1:
            raise Stop('address-range')
        row = {'ordinal': ordinal, 'name': NAMES[ordinal-1], 'phase': phase, 'beginUTC': self.now(),
               'address': address, 'requested': width, 'error': None, 'raw': b''}
        self.records.append(row)
        try:
            raw, error = callback(address, width)
            if type(raw) is not bytes:
                raise Stop('read-result-type')
            row['raw'] = raw
            if error is not None:
                row['error'] = _error_code(error)
            elif len(raw) != width:
                row['error'] = {'kind': 'short-or-overlong-read'}
        except Exception as error:
            row['error'] = {'kind': 'read-exception', **_winerror(error)}
        row['endUTC'] = self.now()
        if row['error'] is not None:
            raise Stop('read-failed')
        if phase == 'first':
            self.first.append(row)
        return row['raw']

    def final(self, callback):
        try:
            for first in self.first:
                raw = self.read(callback, first['ordinal'], first['address'], first['requested'], 'final')
                if raw != first['raw']:
                    raise Stop('final-drift')
            self.final_ok = True
        except Stop as failure:
            self.outcome = str(failure)

    def seal(self, external_ok=False):
        known = (external_ok is True and self.outcome == 'known' and self.final_ok
                 and self.env in ENVS and self.route in ENVS and len(self.records) == 26)
        records = []
        for row in self.records:
            item = {key: row[key] for key in ('ordinal', 'name', 'phase', 'beginUTC', 'endUTC', 'error')}
            item['outcome'] = 'read' if row['error'] is None else 'read-failed'
            if row['ordinal'] in SENSITIVE and not known:
                item['redacted'] = True
            else:
                item.update(address=row['address'], requested=row['requested'], bytesRead=len(row['raw']), bytesHex=row['raw'].hex())
            records.append(item)
        budget = dict(CAPS, actualReads=len(self.records), firstPassReads=sum(r['phase']=='first' for r in self.records),
            structuralRequestedBytes=sum(r['requested'] for r in self.records if r['ordinal'] not in SENSITIVE))
        if known:
            budget.update(requestedBytes=sum(r['requested'] for r in self.records),
                firstPassBytes=sum(r['requested'] for r in self.records if r['phase']=='first'))
        return {'schema': 'client72-managed-route-inputs-v1', 'module': self.module,
            'status': 'candidate' if known else 'partial', 'outcome': self.outcome,
            'summary': {'envNumber': self.env, 'envClass': _category(self.env),
                'baseRouteClass': _category(self.route) if known else 'unverified',
                'currentEnvAndBaseRouteAgree': self.env == self.route if known else None},
            'records': records, 'budget': budget, 'sensitiveRawSealed': known,
            'rawSource': 'actual-whitelisted-read' if known else 'structural-only',
            'currentStateCandidateOnly': True, 'nonAtomic': True, 'gcObjectPinningProved': False,
            'addressReuseExcluded': False, 'managedEnvSelectionObserved': False, 'getUrlReturnObserved': False,
            'postRequestObserved': False, 'authenticationPassed': False, 'd02Passed': False,
            'canReplayPop': False, 'nativeCPUExecuted': False, 'capturedCopyWriteApproved': False}


def _winerror(error):
    value = getattr(error, 'winerror', None)
    return {'winerror': value} if type(value) is int and 0 <= value <= 0xffffffff else {}


def _error_code(error):
    result = {'kind': 'api-error'}
    if type(error) is dict:
        for key in ('winerror', 'lastError'):
            value = error.get(key)
            if type(value) is int and 0 <= value <= 0xffffffff:
                result[key] = value
    return result


def _category(value):
    return 'managed-current-base-cn-env-' + str(value) if value in ENVS else 'unknown'


def collect(read, module, *, now=utc):
    pending = PendingCapture(module, now)
    if type(module) is not int or not 0 < module <= U64 - 0x5893d58:
        return pending
    try:
        _first(read, module, pending)
    except Stop as failure:
        pending.outcome = str(failure)
    pending.final(read)
    return pending


def _first(read, base, pending):
    def qword(ordinal, address, zero):
        value = int.from_bytes(pending.read(read, ordinal, address, 8), 'little')
        if value == 0:
            raise Stop(zero)
        return value
    storage = qword(1, base+0x586b600, 'storage-not-ready')
    config_type = qword(2, base+0x5887680, 'config-type-not-ready')
    config = qword(3, storage+0xc18, 'config-not-ready')
    if int.from_bytes(pending.read(read, 4, config, 8), 'little') != config_type:
        raise Stop('config-class-mismatch')
    pending.env = struct.unpack('<i', pending.read(read, 5, config+0x58, 4))[0]
    url_type = qword(6, base+0x5893d50, 'url-type-not-ready')
    url = qword(7, storage+0xb80, 'url-not-ready')
    if int.from_bytes(pending.read(read, 8, url, 8), 'little') != url_type:
        raise Stop('url-class-mismatch')
    text = qword(9, url+0xe0, 'base-absent')
    text_type = qword(10, base+0x5886040, 'string-type-not-ready')
    if int.from_bytes(pending.read(read, 11, text, 8), 'little') != text_type:
        raise Stop('string-class-mismatch')
    length = struct.unpack('<i', pending.read(read, 12, text+0x10, 4))[0]
    if length < 0:
        raise Stop('invalid-length')
    if length == 0:
        raise Stop('base-empty')
    if not 64 <= length <= 69:
        raise Stop('unknown-length')
    pending.route = BASES.get(pending.read(read, 13, text+0x14, 2*length))
    pending.outcome = 'known' if pending.route in ENVS and pending.env in ENVS else 'unknown-value'


def replay(report):
    """Known: replay actual bytes through the graph. Partial: structural verification only."""
    rows = report['records']
    if report['sensitiveRawSealed']:
        cursor = iter(rows)
        count = 0
        def read(address, width):
            nonlocal count
            row = next(cursor)
            count += 1
            if ((row['ordinal'],row['phase']) != ((count-1)%13+1,'first' if count<=13 else 'final')
                    or (row['address'], row['requested']) != (address, width) or row['error'] is not None
                    or row['bytesRead'] != width or row['name'] != NAMES[(count-1)%13]):
                raise ValueError('replay-request-mismatch')
            return bytes.fromhex(row['bytesHex']), None
        rebuilt = collect(read, report['module'], now=lambda: 'replay').seal(True)
        if (rebuilt['status'] != 'candidate' or rebuilt['summary'] != report['summary']
                or rebuilt['budget'] != report['budget'] or report['status'] != 'candidate'
                or report['rawSource'] != 'actual-whitelisted-read'):
            raise ValueError('replay-classification-differed')
        if next(cursor, None) is not None:
            raise ValueError('replay-extra-reads')
        return {'passed': True, 'scope': 'actual-whitelisted-content-and-structure', 'reads': len(rows)}
    allowed = {'ordinal', 'name', 'phase', 'beginUTC', 'endUTC', 'error', 'outcome', 'redacted'}
    first = {}
    final_started = False
    final_ordinals = []
    structural = 0
    for row in rows:
        ordinal = row['ordinal']
        if not 1 <= ordinal <= 13 or row['name'] != NAMES[ordinal-1]:
            raise ValueError('replay-ordinal')
        if ordinal in SENSITIVE:
            if set(row) != allowed or row['redacted'] is not True:
                raise ValueError('replay-sensitive-disclosure')
        else:
            structural += row['requested']
            if len(bytes.fromhex(row['bytesHex'])) != row['bytesRead']:
                raise ValueError('replay-structural-length')
            expected_width = 4 if ordinal == 5 else 8
            if row['requested'] != expected_width:
                raise ValueError('replay-structural-width')
        if row['phase'] == 'first' and not final_started and ordinal == len(first)+1:
            first[ordinal] = row
        elif row['phase'] == 'final':
            final_started = True
            final_ordinals.append(ordinal)
            if ordinal != len(final_ordinals) or ordinal not in first or first[ordinal]['error'] is not None:
                raise ValueError('replay-final-order')
            if ordinal not in SENSITIVE and (row['address'],row['requested']) != (first[ordinal]['address'],first[ordinal]['requested']):
                raise ValueError('replay-final-address')
        else:
            raise ValueError('replay-phase-order')
    if len(rows) != report['budget']['actualReads'] or structural != report['budget']['structuralRequestedBytes']:
        raise ValueError('replay-budget')
    if any(key in report['budget'] for key in ('requestedBytes','firstPassBytes')):
        raise ValueError('replay-sensitive-total')
    def qword(ordinal):
        return int.from_bytes(bytes.fromhex(first[ordinal]['bytesHex']),'little')
    expected = {1: report['module']+0x586b600, 2: report['module']+0x5887680,
                6: report['module']+0x5893d50, 10: report['module']+0x5886040}
    if 1 in first and first[1]['error'] is None:
        expected.update({3:qword(1)+0xc18,7:qword(1)+0xb80})
    if 3 in first and first[3]['error'] is None:
        expected.update({4:qword(3),5:qword(3)+0x58})
    if 7 in first and first[7]['error'] is None:
        expected[8]=qword(7)
    for ordinal,row in first.items():
        if ordinal not in SENSITIVE and row['address'] != expected[ordinal]:
            raise ValueError('replay-structural-address')
    return {'passed': True, 'scope': 'structural-only-no-content-reconstruction', 'reads': len(rows)}
