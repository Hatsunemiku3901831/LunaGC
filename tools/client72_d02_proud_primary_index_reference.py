"""File-only conditional reference for the fixed 7.2 Proud primary index."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = Path('local/migration72/d02-resume-20261110b')
MANIFEST = BASE / 'c-proud-index-handler-contract/frozen-inputs.private.json'
SPEC = BASE / 'c-proud-index-handler-contract/next-primary-index-parser-spec.zh.md'
OLD_RAW = Path('local/migration72/d02-active/scene-runtime-context-astra/GadgetPrimaryIndex.native-m_bytes.bin')
OLD_REPORT = Path('local/migration72/d02-active/scene-runtime-context-astra/primary-native1.json')
MAIN_DIR = BASE / 'c-proud-payload-locator/final3'
NEW_DIR = BASE / 'c-proud-primary-index-locator'
PINS = {
    str(MANIFEST): (47977, 'e0df5135b0ecbf585c86959d33f4c85412b814f2d6a657b73515ea50902ebf01'),
    str(SPEC): (8480, '9741042201743440428d892cf1bd687c98872c48e7ebed7274c55fe0ff5a046f'),
    str(OLD_RAW): (163245, '1e95ae771d6597160b2438d5a1ead40f04f58a84a26be466c0226fab6923138f'),
    str(OLD_REPORT): (1860159, '466b0762a484c8b0c946151fa2301ffed870e550e6b4cd4bddb1ea435a3f7deb'),
}
U32 = 0xffffffff
U64 = 0xffffffffffffffff
LIMITS = dict(targetCPU=0, nativeParserExecuted=False, naturalBackendProven=False,
              actualIndexFilled=False, actualRowSpanApproved=False, factoryProven=False,
              groupAliasApproved=False, levelAliasApproved=False, thresholdProven=False,
              javaFormulaProven=False, resourceReady=False, completeAvatar=False, d02Complete=False)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def verified(path, size, digest):
    raw = (ROOT / path).read_bytes()
    if len(raw) != size or sha(raw) != digest:
        raise ValueError(f'frozen source mismatch: {path}')
    return raw


def preflight():
    manifest_raw = verified(MANIFEST, *PINS[str(MANIFEST)])
    verified(SPEC, *PINS[str(SPEC)])
    manifest = json.loads(manifest_raw)
    sources = manifest['sources']
    if len(sources) != 238 or sum(v['bytes'] for v in sources.values()) != 317072913:
        raise ValueError('frozen source inventory differs')
    for path, pin in sources.items():
        verified(path, pin['bytes'], pin['sha256'])
    for path in (OLD_RAW, OLD_REPORT):
        verified(path, *PINS[str(path)])
    location = json.loads((ROOT / MAIN_DIR / 'result.private.json').read_bytes())
    entry = location['entry']
    if (not location['passed'] or not location['actualPayloadExtracted'] or
        entry['bytes'] != 2132778 or entry['sha256'] != '82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835' or
        entry['payloadSpan'][1] - entry['payloadSpan'][0] != entry['bytes']):
        raise ValueError('main payload provenance differs')
    return {'manifestSHA256': sha(manifest_raw), 'sourceCount': len(sources),
            'sourceBytes': sum(v['bytes'] for v in sources.values())}


class Boundary(Exception):
    def __init__(self, stage, start, read_end):
        self.stage, self.start, self.read_end = stage, start, read_end


def primitive(data, cursor, stage):
    start = cursor
    value = 0
    for i in range(10):
        if start + i >= len(data):
            raise Boundary(stage, start, len(data))
        b = data[start + i]
        if i == 9:
            value |= (b << 63) & U64
            terminal = 'tenth_high_set_no_advance' if b & 128 else 'tenth_terminated'
            next_cursor = start if b & 128 else start + 10
            break
        value |= (b & 127) << (7 * i)
        if not b & 128:
            terminal = 'terminated'
            next_cursor = start + i + 1
            break
    return {'rawU64': value, 'high32Nonzero': bool(value >> 32),
            'readSpan': [start, start + i + 1], 'rawHex': data[start:start+i+1].hex(),
            'nextCursor': next_cursor, 'terminalCase': terminal, 'originalAL': 1}


def candidate(first, second, payload):
    end = first + second
    valid = end <= len(payload)
    return {'mathematicalEnd': end, 'u32Wrap': end > U32,
            'candidateSpan': [first, end] if valid else None,
            'candidateSliceSHA256': sha(payload[first:end]) if valid else None,
            'driverRowBounds': 'within_payload' if valid else 'out_of_bounds'}


def parse(data, payload):
    result = {'inputBytes': len(data), 'inputSHA256': sha(data), 'records': [],
              'condition': 'byte1457e5ef3 assumed zero for every item',
              'nativeEOFCheck': False, 'handlerAL': None, 'driverStatus': 'partial'}
    cursor = 0
    reads = 0
    stage = 'header'
    try:
        def read(name):
            nonlocal cursor, reads
            if reads >= len(data) and cursor < len(data):
                raise RuntimeError('driver_primitive_budget')
            item = primitive(data, cursor, name)
            reads += 1
            cursor = item['nextCursor']
            if item['terminalCase'] == 'tenth_high_set_no_advance':
                raise StopIteration((name, item))
            return item
        header = read('header')
        result['header'] = header
        u = header['rawU64'] & U32
        bits = (u >> 1) ^ (-(u & 1) & U32)
        count = bits if bits < 0x80000000 else bits - 0x100000000
        result['count'] = count
        if count < 0:
            result.update(handlerAL=1, driverStop='negative_count_unsupported', originalZeroItems=True)
        else:
            keys = set()
            current = 0
            for i in range(count):
                stage = 'key'
                key_read = read(stage)
                key = key_read['rawU64'] & U32
                initial = None
                if i == 0:
                    stage = 'initial'
                    initial = read(stage)
                    current = initial['rawU64'] & U32
                stage = 'second'
                second_read = read(stage)
                second = second_read['rawU64'] & U32
                row = {'index': i, 'key': key, 'keyRead': key_read,
                       'initialRead': initial, 'secondRead': second_read,
                       'firstU32': current, 'secondU32': second}
                row.update(candidate(current, second, payload))
                current = (current + second) & U32
                if key in keys:
                    result.update(driverStop='duplicate_key', originalInsert='error_helper',
                                  duplicateRecord=row)
                    break
                keys.add(key)
                result['records'].append(row)
            else:
                result.update(handlerAL=1, driverStatus='complete', originalInsert='normal')
    except Boundary as exc:
        result.update(driverStop='input_boundary', boundary={'stage': exc.stage, 'start': exc.start,
                      'readEnd': exc.read_end, 'nativeBoundary': '152c257c2_to_1404c2650'})
    except StopIteration as exc:
        name, item = exc.value
        result.update(driverStop='nonadvancing_primitive_budget', nonadvancing={'stage': name, 'read': item})
    except RuntimeError as exc:
        if str(exc) != 'driver_primitive_budget':
            raise
        result['driverStop'] = str(exc)
    result.update(cursor=cursor, primitiveReads=reads, remainingBytes=len(data)-cursor)
    return result


def lookup(records, key):
    for row in records:
        if row['key'] == key:
            return {'key': key, 'hit': True, 'index': row['index'],
                    'pair': [row['firstU32'], row['secondU32']]}
    return {'key': key, 'hit': False, 'index': None, 'pair': None}


def old_oracle():
    old = (ROOT / OLD_RAW).read_bytes()
    native = json.loads((ROOT / OLD_REPORT).read_bytes())
    if native['payloadSHA256'] != sha(old):
        raise ValueError('old raw/native identity differs')
    parsed = parse(old, bytes(native['end']))
    rows = [[r['key'], r['firstU32'], r['secondU32']] for r in parsed['records']]
    if (parsed['driverStatus'] != 'complete' or parsed['count'] != 31682 or
        rows != native['rows'] or parsed['cursor'] != native['cursor'] or
        parsed['cursor'] != len(old) or rows[-1][1] + rows[-1][2] != native['end'] or native['end'] != 2944342):
        raise ValueError('old Gadget ordered oracle differs')
    checks = []
    for q in native['queries']:
        got = lookup(parsed['records'], q['key'])
        if (not got['hit'] and q['position'] != U32) or got['hit'] != (q['range'] is not None) or (got['hit'] and
            (got['index'] != q['position'] or rows[got['index']] != q['range'])):
            raise ValueError('old Gadget query differs')
        checks.append(got)
    boundary = parse(old[:3], b'')
    if boundary.get('driverStop') != 'input_boundary' or boundary['records'] or boundary['boundary']['stage'] != 'key':
        raise ValueError('old Gadget truncated boundary differs')
    return {'rawSHA256': sha(old), 'nativeReportSHA256': sha((ROOT / OLD_REPORT).read_bytes()),
            'orderedRowsCompared': len(rows), 'cursor': parsed['cursor'], 'eof': len(old),
            'end': native['end'], 'queries': checks, 'truncated3Boundary': boundary['boundary']}


def make_report(source="fresh1"):
    sources = preflight()
    oracle = old_oracle()
    data = (ROOT / NEW_DIR / source / 'payload.bin').read_bytes()
    payload = (ROOT / MAIN_DIR / 'payload.bin').read_bytes()
    result = parse(data, payload)
    records = result['records']
    result.update(task='E-01/R3-PROUD-PRIMARY-INDEX-REFERENCE', sources=sources, oldGadgetOracle=oracle,
                  mainPayload={'bytes': len(payload), 'sha256': sha(payload)}, limitations=LIMITS,
                  keyZero=lookup(records, 0), maxU32=lookup(records, U32),
                  allZeroPairHits=[r['key'] for r in records if r['firstU32'] == r['secondU32'] == 0],
                  outOfBoundsCount=sum(r['candidateSpan'] is None for r in records),
                  wrapCount=sum(r['u32Wrap'] for r in records))
    return result


def audit(report, source="fresh1"):
    """Independently re-read all recorded spans and recompute all candidate slices."""
    preflight()
    old_oracle()
    data = (ROOT / NEW_DIR / source / 'payload.bin').read_bytes()
    payload = (ROOT / MAIN_DIR / 'payload.bin').read_bytes()
    if report['inputSHA256'] != sha(data) or report['mainPayload'] != {'bytes': len(payload), 'sha256': sha(payload)}:
        raise ValueError('audit input identity differs')
    pos = 0
    def check(read):
        nonlocal pos
        a, b = read['readSpan']
        raw = data[a:b]
        if a != pos or raw.hex() != read['rawHex'] or not 1 <= len(raw) <= 10:
            raise ValueError('audit span differs')
        value = sum((v & 127) << (7*i) for i,v in enumerate(raw[:9]))
        if len(raw) == 10:
            value |= (raw[9] & 1) << 63
        terminal = ('tenth_high_set_no_advance' if raw[-1] & 128 else 'tenth_terminated') if len(raw) == 10 else 'terminated'
        if any(not x & 128 for x in raw[:-1]) or (len(raw) < 10 and raw[-1] & 128):
            raise ValueError('audit continuation differs')
        next_pos = a if terminal == 'tenth_high_set_no_advance' else b
        if (read['rawU64'], read['high32Nonzero'], read['terminalCase'], read['nextCursor'], read['originalAL']) != (value, bool(value >> 32), terminal, next_pos, 1):
            raise ValueError('audit primitive differs')
        pos = next_pos
        return value
    h = check(report['header']) & U32
    nbits = (h >> 1) ^ (-(h & 1) & U32)
    n = nbits if nbits < 0x80000000 else nbits - 0x100000000
    if report['driverStatus'] != 'complete' or report['count'] != n or len(report['records']) != n:
        raise ValueError('audit requires complete result')
    keys = set()
    current = 0
    for i, row in enumerate(report['records']):
        key = check(row['keyRead']) & U32
        if i == 0:
            current = check(row['initialRead']) & U32
        elif row['initialRead'] is not None:
            raise ValueError('audit unexpected initial read')
        second = check(row['secondRead']) & U32
        end = current + second
        span = [current, end] if end <= len(payload) else None
        digest = sha(payload[current:end]) if span is not None else None
        if (row['index'],row['key'],row['firstU32'],row['secondU32'],row['mathematicalEnd'],
            row['u32Wrap'],row['candidateSpan'],row['candidateSliceSHA256'],row['driverRowBounds']) != (
            i,key,current,second,end,end>U32,span,digest,'within_payload' if span is not None else 'out_of_bounds') or key in keys:
            raise ValueError(f'audit record differs at {i}')
        keys.add(key)
        current = end & U32
    if pos != report['cursor'] or report['remainingBytes'] != len(data)-pos or report['handlerAL'] != 1:
        raise ValueError('audit terminal differs')
    if (report['keyZero'] != lookup(report['records'],0) or report['maxU32'] != lookup(report['records'],U32) or
        report['allZeroPairHits'] != [r['key'] for r in report['records'] if r['firstU32'] == r['secondU32'] == 0] or
        report['outOfBoundsCount'] != sum(r['candidateSpan'] is None for r in report['records']) or
        report['wrapCount'] != sum(r['u32Wrap'] for r in report['records']) or report['limitations'] != LIMITS):
        raise ValueError('audit summary differs')
    return {'recordsAudited': n, 'cursor': pos, 'remainingBytes': len(data)-pos,
            'everyPrimitiveSpanAndCandidateSliceRechecked': True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['run','audit'])
    parser.add_argument('path', type=Path)
    parser.add_argument('--source', choices=['fresh1','fresh2','main-third'], default='fresh1')
    args = parser.parse_args()
    if args.mode == 'run':
        obj = make_report(args.source)
        args.path.parent.mkdir(parents=True, exist_ok=True)
        with args.path.open('x', encoding='utf-8', newline='\n') as output:
            output.write(json.dumps(obj, ensure_ascii=False, sort_keys=True, indent=2)+'\n')
        print(json.dumps({'sha256': sha(args.path.read_bytes()), 'count': obj.get('count'), 'cursor': obj['cursor'], 'driverStatus': obj['driverStatus'], 'driverStop': obj.get('driverStop')}))
    else:
        raw = args.path.read_bytes()
        result = audit(json.loads(raw), args.source)
        result['reportSHA256'] = sha(raw)
        print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()

