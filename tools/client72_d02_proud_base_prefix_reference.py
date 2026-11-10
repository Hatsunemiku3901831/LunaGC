"""Conditional file grammar only; never constructs or executes native objects."""
import argparse
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('local/migration72/d02-resume-20261110b')
PRIVATE = BASE / 'c-proud-base-prefix-reference'
SPEC = BASE / 'c-proud-element-reader-contract/terra-reference-spec.zh.md'
MANIFEST = BASE / 'c-proud-element-reader-contract/frozen-inputs.private.json'
PAYLOAD = BASE / 'c-proud-payload-locator/main-third/payload.bin'
SPEC_SHA = '311bf6644f362d9aa602406b8dfe5b1e72485590924a37eb88437525a47e8a8d'
MANIFEST_SHA = 'df5a5476a0d2047cc07cb731027d0e443c19d68c109d3095ac50bd85ca56db85'
PAYLOAD_SHA = '82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835'
U32 = 0xffffffff
U64 = 0xffffffffffffffff
CONDITIONS = [
    'derived+40=null and base+20=null are separate explicit conditions',
    'each relevant reader+20 branch uses its ordinary inline route',
    'preceding original object/allocator/storage/cleanup calls return normally',
    'file abstraction assigns no extra file consumption to those calls',
    'no natural backend, mode, typed receiver or allocator is constructed',
]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def preflight():
    spec = (ROOT / SPEC).read_bytes()
    manifest_raw = (ROOT / MANIFEST).read_bytes()
    if len(spec) != 8283 or sha(spec) != SPEC_SHA or sha(manifest_raw) != MANIFEST_SHA:
        raise ValueError('frozen specification/manifest drift')
    sources = json.loads(manifest_raw)['sources']
    if len(sources) != 86 or sum(p['bytes'] for p in sources.values()) != 216964461:
        raise ValueError('source inventory drift')
    for name, pin in sources.items():
        raw = (ROOT / name).read_bytes()
        if len(raw) != pin['bytes'] or sha(raw) != pin['sha256']:
            raise ValueError('source drift: ' + name)
    data = (ROOT / PAYLOAD).read_bytes()
    if len(data) != 2132778 or sha(data) != PAYLOAD_SHA:
        raise ValueError('payload drift')
    return data


class FileStop(Exception):
    pass


class Reader:
    def __init__(self, data, cursor, candidate_end):
        self.data, self.cursor, self.candidate_end = data, cursor, candidate_end
        self.tokens = []

    def read(self, phase, pc, width, **fields):
        start = self.cursor
        # MOVSXD cursor followed by unsigned comparison against array length.
        original_start = start < 0x80000000 and start < len(self.data)
        physical = start + width <= len(self.data)
        token = dict(phase=phase, readPC=pc, cursorBefore=start, cursorAfter=start,
                     span=[start, start+width], width=width, wireBytes=width,
                     rawHex=None, rawLEHex=None, present=True,
                     originalStartCheck=original_start, physicalFullWidthCheck=physical,
                     candidateBoundaryCrossed=start+width > self.candidate_end)
        token.update(fields)
        self.tokens.append(token)
        if not original_start:
            raise FileStop('original-start-check-failed; original helper not executed')
        if not physical:
            raise FileStop('physical-width-unavailable; original start check passed')
        raw = self.data[start:start+width]
        self.cursor = (start+width) & U32
        token.update(rawHex=raw.hex(), rawLEHex=hex(int.from_bytes(raw, 'little')),
                     cursorAfter=self.cursor)
        return raw, token

    def absent(self, phase, pc, **fields):
        token = dict(phase=phase, readPC=None, defaultPC=pc, present=False,
                     span=None, width=0, wireBytes=0, rawHex=None, rawLEHex=None,
                     cursorBefore=self.cursor, cursorAfter=self.cursor)
        token.update(fields)
        self.tokens.append(token)
        return token


def float_input(bits):
    return dict(floatInputBits=f'{bits:08x}',
                ieee754Hex=struct.unpack('<f', bits.to_bytes(4, 'little'))[0].hex(),
                storedEncodedDWORD=None, storedEncodedStatus='unknown-missing-leaf-body')


def element(reader, index):
    raw, token = reader.read('element-header', '14ee1ea46', 2, arrayIndex=index)
    total = int.from_bytes(raw, 'little') + 0x6f60
    token.update(operation='U32(rawU16 + 0x6f60)', resultBits=f'{total:08x}',
                 headerSumU32=total, headerLow16=total & 0xffff)
    if total & 0x40:
        raw, token = reader.read('element-enum', '14ee1ea90', 4, arrayIndex=index, fieldIndex=261827)
        token.update(operation='rawU32 xor 0xc4cda654', resultBits=f'{int.from_bytes(raw,"little") ^ 0xc4cda654:08x}')
    else:
        token = reader.absent('element-enum', '14ee1ea9a', arrayIndex=index, fieldIndex=261827,
                              resultBits='00000000', defaultOrigin='original direct receiver+0 DWORD write')
    token['conditionalReceiverWrite'] = dict(pc='14ee1ea9a', offset=0, width=4,
                                            bits=token['resultBits'], nativeExecuted=False)
    if total & 0x80:
        raw, token = reader.read('element-float', '14ee1ead4', 4, arrayIndex=index, fieldIndex=261828)
        bits = int.from_bytes(raw, 'little') ^ 0x64f1d829
        token.update(operation='rawU32 xor 0x64f1d829', resultBits=f'{bits:08x}', **float_input(bits))
    else:
        reader.absent('element-float', '14ee1ea9d', arrayIndex=index, fieldIndex=261828,
                      defaultOrigin='original XMM1 zero before unconditional float leaf call', **float_input(0))


def parse(data, cursor=0, candidate_end=None):
    if not isinstance(data, bytes) or type(cursor) is not int or not 0 <= cursor <= U32:
        raise ValueError('bytes and U32 cursor required')
    if candidate_end is None:
        candidate_end = len(data)
    if type(candidate_end) is not int or candidate_end < 0:
        raise ValueError('candidate end must be nonnegative')
    reader = Reader(data, cursor, candidate_end)
    result = dict(parseStatus='partial', startCursor=cursor, candidateEnd=candidate_end,
                  physicalEnd=len(data), conditions=CONDITIONS, tokens=reader.tokens,
                  naturalBackendProven=False, naturalModeObserved=False, actualIndexFilled=False,
                  actualRowSpanApproved=False, typedAllocatorReturnProven=False,
                  typedReceiverOutputApproved=False, nativeParserExecuted=False,
                  completeProudReference=False, resourceReady=False, completeAvatar=False,
                  d02Complete=False, targetCPU=0)
    try:
        raw, token = reader.read('base-header', '14d5e2789', 2)
        prefix = int.from_bytes(raw, 'little') ^ 0x6c27
        token.update(operation='rawU16 xor 0x6c27', resultBits=f'{prefix:04x}')
        if prefix & 0x20:
            raw, token = reader.read('base-list-count', '14d5e2838', 4, fieldIndex=295066)
            count = (int.from_bytes(raw, 'little') + 0xb63a27ff) & U32
            token.update(operation='U32(rawU32 + 0xb63a27ff)', resultBits=f'{count:08x}')
            if count & 0x80000000:
                raise FileStop('unapproved-high-base-list-count')
            if count == 0:
                reader.absent('base-list', None, fieldIndex=295066,
                              defaultOrigin='base original zero-count source; object unknown')
            for index in range(count):
                raw, token = reader.read('base-list-item', '14d5e28e4', 4, arrayIndex=index, fieldIndex=295066)
                bits = int.from_bytes(raw, 'little') ^ 0xf4a5719b
                token.update(operation='rawU32 xor 0xf4a5719b', resultBits=f'{bits:08x}', **float_input(bits))
        else:
            reader.absent('base-list', None, fieldIndex=295066, defaultOrigin='base original default source; object unknown')
        if prefix & 1:
            raw, token = reader.read('base-block-length', '14d5e29ce', 2)
            size = int.from_bytes(raw, 'little') ^ 0x7915
            token.update(operation='rawU16 xor 0x7915', resultBits=f'{size:04x}')
            if size:
                first_pc = ('14d5e2ba0' if (size//8) % 4 else '14d5e2be0') if size >= 8 else '14d5e2c28'
                raw, token = reader.read('base-block', first_pc, size)
                decoded = b''.join(((int.from_bytes(raw[i:i+8], 'little') + 0x9a168e269ee37915) & U64).to_bytes(8, 'little')[:len(raw[i:i+8])]
                                   for i in range(0, size, 8))
                token.update(operation='independent LE groups plus 0x9a168e269ee37915 modulo U64; partial group low bytes',
                             transformedHex=decoded.hex(), stringEncoding=None, nativeStringReturned=False,
                             fileAbstractionBlock=True, originalCursorCommitPC='14d5e2c8a',
                             originalGroupLoadPCs=['14d5e2ba0','14d5e2be0','14d5e2be9','14d5e2bf4','14d5e2bff'],
                             remainderCopyCallPC='14d5e2c28')
            else:
                reader.absent('base-block', None, defaultOrigin='base original empty source; object unknown')
        else:
            reader.absent('base-block', None, defaultOrigin='base original default source; object unknown')
        if prefix & 0x10:
            raw, token = reader.read('element-count', '14ee1eb94', 4, fieldIndex=295065)
            count = int.from_bytes(raw, 'little') ^ 0x7aef1999
            token.update(operation='rawU32 xor 0x7aef1999', resultBits=f'{count:08x}')
            if count & 0x80000000:
                raise FileStop('unapproved-high-element-count-signed-loop')
            if count == 0:
                reader.absent('elements', '14ee1ec17..14ee1ec27', fieldIndex=295065,
                              defaultOrigin='11506/instance11802 static field55097 Empty; object unknown')
            for index in range(count):
                element(reader, index)
        else:
            reader.absent('elements', '14d5e2ab2', fieldIndex=295065, defaultOrigin='base original mask10-unset source; object unknown')
        result.update(parseStatus='complete_prefix', next63831InputCursor=reader.cursor,
                      actualCursorObserved=False)
    except FileStop as error:
        result.update(stopReason=str(error), next63831InputCursor=None)
    result['stoppedCursor'] = reader.cursor
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    parser.add_argument('--audit')
    args = parser.parse_args(argv)
    if any(Path(n).name != n for n in (args.output, args.audit) if n is not None):
        parser.error('use a new task-local basename')
    directory = ROOT / PRIVATE
    directory.mkdir(exist_ok=True)
    with (directory/args.output).open('x', encoding='utf-8', newline='\n') as stream:
        try:
            data = preflight()
            result = parse(data, 4, 334)
            report = dict(schema='proud-base-prefix-file-reference-v1', key=22101,
                          payloadSHA256=sha(data), specSHA256=SPEC_SHA, manifestSHA256=MANIFEST_SHA,
                          sourceSHA256=sha(Path(__file__).read_bytes()), result=result,
                          accepted=result['parseStatus']=='complete_prefix')
            if result['next63831InputCursor'] != 142:
                raise ValueError('fixed prefix cursor differs')
            report['next63831HeaderPreview'] = dict(span=[142,146], rawHex=data[142:146].hex(), readPC='149f0c2eb', decoded=False)
            encoded = json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)+'\n'
            if args.audit and (directory/args.audit).read_bytes() != encoded.encode('utf-8'):
                raise ValueError('reference differs from prior artifact')
            stream.write(encoded)
            status = 0
        except (ValueError, OSError) as error:
            json.dump(dict(schema='proud-base-prefix-file-reference-v1', accepted=False, error=str(error)), stream, sort_keys=True, indent=2)
            stream.write('\n')
            status = 1
    return status


if __name__ == '__main__':
    raise SystemExit(main())
