"""Fixed key 22101 conditional file syntax; no native execution or object model."""
import argparse
import hashlib
import json
from pathlib import Path
try:
    from tools import client72_d02_proud_base_prefix_reference as base
except ModuleNotFoundError:
    import client72_d02_proud_base_prefix_reference as base

ROOT = Path(__file__).resolve().parents[1]
DIR = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-single-key-reference'
SPEC = ROOT / 'local/migration72/d02-resume-20261110b/c-proud-native-17208-element-contract/terra-single-key-reference-spec.zh.md'
CONTRACT = SPEC.parent / 'main-third.private.json'
BASE_SOURCE = Path(base.__file__)
PINS = {
    SPEC: (13078, 'f98b1da78d11c921d4fa7def19a8748b7199ba7d754cebfd77275ca18b5294cc'),
    CONTRACT: (228055, '3cb8f15c510dc01b317dbff3b0963ba229cccb56a4a7939fea51c64e55858e0c'),
    BASE_SOURCE: (None, 'd98b1f3d643e17759940d66c557b82c9e3c3ffb240025c0b462441c3f5186522'),
    ROOT / base.PAYLOAD: (2132778, base.PAYLOAD_SHA),
}
U32 = 0xffffffff


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def preflight():
    data = None
    for path, (size, expected) in PINS.items():
        raw = path.read_bytes()
        if (size is not None and len(raw) != size) or digest(raw) != expected:
            raise ValueError('frozen source drift: ' + path.name)
        if path == ROOT / base.PAYLOAD:
            data = raw
    return data


def bounds(cursor, width, physical_end, candidate_end):
    """Numerical witness of original unsigned start, MOVSXD and host width."""
    low = cursor & U32
    signed = low if low < 0x80000000 else low - 0x100000000
    return dict(originalStartCheck=low < physical_end, movsxdAddressOffset=signed,
                physicalFullWidthCheck=0 <= signed and signed + width <= physical_end,
                candidateBoundaryCrossed=signed + width > candidate_end,
                cursorAdvanceLow32=(low + width) & U32)


class Reader(base.Reader):
    def read(self, phase, pc, width, **fields):
        start = self.cursor
        check = bounds(start, width, len(self.data), self.candidate_end)
        try:
            return super().read(phase, pc, width, **fields)
        finally:
            self.tokens[-1].update(check)
            self.tokens[-1]['cursorAdvanceLow32'] = check['cursorAdvanceLow32']

    def absent(self, phase, pc, **fields):
        token = super().absent(phase, pc, **fields)
        token.update(originalStartCheck=None, physicalFullWidthCheck=None,
                     movsxdAddressOffset=None, candidateBoundaryCrossed=False,
                     transformWidthBits=0)
        return token


def read_int(r, phase, pc, width, operation, fn, **fields):
    raw, t = r.read(phase, pc, width, **fields)
    value = int.from_bytes(raw, 'little')
    result = fn(value)
    t.update(operation=operation, transformWidthBits=width * 8,
             inputBits=f'{value:0{width*2}x}', resultBits=f'{result:0{width*2}x}')
    return result, t


def setter(t, input_value, call_pc, copy_pc, offset, field=None):
    t['conditionalSetterCopy'] = dict(inputBits=f'{input_value:08x}', callPC=call_pc,
        copyPC=copy_pc, receiverOffset=offset, fieldIndex=field,
        localClearBeforeCall=True, normalReturnRequired=True, actualStored=None,
        nativeExecuted=False)


def element(r, index):
    header, t = read_int(r, '17208-element-header', '147e91807', 2,
                          'U32(rawU16 xor 0x3f87)', lambda x: x ^ 0x3f87, elementIndex=index)
    t['transformWidthBits'] = 32
    for mask, field, offset, pc, defaultpc, callpc, copypc, op, fn in [
        (4, 81538, 4, '147e9185b', '147e9181f', '147e9186f', '147e91878',
         'U32(raw + 0x2061053c)', lambda x: (x + 0x2061053c) & U32),
        (0x80, 81537, 0, '147e918b0', '147e9187c', '147e918cc', '147e918d5',
         'U32(raw + 0x6c483fd7) xor 0x4bfd37e4', lambda x: ((x + 0x6c483fd7) & U32) ^ 0x4bfd37e4),
    ]:
        common = dict(elementIndex=index, fieldIndex=field, presenceSource='element header', presenceMask=mask)
        if header & mask:
            value, t = read_int(r, '17208-element-member', pc, 4, op, fn, **common)
        else:
            value = 0
            t = r.absent('17208-element-member', defaultpc, defaultOrigin='zero setter input; encoded receiver unknown',
                         resultBits='00000000', **common)
        setter(t, value, callpc, copypc, offset, field)


def chosen(condition, label):
    if not condition:
        raise base.FileStop('unapproved-derived-alternative: ' + label)


def derived(r):
    rawh, t = read_int(r, 'derived-header', '149f0c2eb', 4, 'raw xor 0x576386bb', lambda x: x ^ 0x576386bb)
    p = rawh
    rawh = int(t['inputBits'], 16)
    # Every alternative is unapproved, including opposite presence states.
    def present(source, mask, expected, label):
        actual = bool(source & mask)
        chosen(actual == expected, label)
        return dict(presenceSource='rawH' if source == rawh else 'P', presenceMask=mask, present=actual)
    def val(label, pc, op, fn, source, mask, setter_pc=None, copy_pc=None):
        fields = present(source, mask, True, label)
        v, t = read_int(r, label, '149f0'+pc, 4, op, fn, **fields)
        if setter_pc:
            setter(t, v, '149f0'+setter_pc, ('149f0'+copy_pc if copy_pc else None), label)
        return v
    def absent(label, source, mask, pc, origin):
        fields = present(source, mask, False, label)
        fields.pop('present')
        r.absent(label, '149f0'+pc, defaultOrigin=origin, **fields)
    val('nativeData+8','c394','raw xor 0x67cfbc0a',lambda x:x^0x67cfbc0a,rawh,0x40)
    fields = present(p,1,True,'this+30'); fields['fieldIndex'] = 316247
    n,_ = read_int(r,'this+30-count','149f0c40e',4,'raw xor 0x51fb290d',lambda x:x^0x51fb290d,**fields)
    chosen(n == 5, 'this+30-count')
    for i in range(n):
        n2,_ = read_int(r,'this+30-item-length','149f0c4f6',2,'raw xor 0x59f5',lambda x:x^0x59f5,arrayIndex=i,**fields)
        chosen(n2 == 0, 'nonzero string item')
    fields = present(rawh,0x4000,True,'nativeData+c')
    n,_ = read_int(r,'nativeData+c-length','149f0c974',2,'U16(raw + 0x3cc2)',lambda x:(x+0x3cc2)&0xffff,**fields)
    chosen(n == 20, 'nativeData+c-length')
    raw,t = r.read('nativeData+c-block','149f0cb50',n,**fields)
    decoded = b''.join((int.from_bytes(raw[i:i+8],'little') ^ 0x3d4852595ff73cc2).to_bytes(8,'little')[:len(raw[i:i+8])] for i in range(0,n,8))
    t.update(operation='independent zero-padded LE64 groups xor 0x3d4852595ff73cc2',transformWidthBits=64,transformedHex=decoded.hex(),nativeStringReturned=False, additionalReadPCs=['149f0cbd8'])
    val('nativeData+14','cccf','raw xor 0x0ee3a93d',lambda x:x^0x0ee3a93d,rawh,0x2000)
    fields = present(p,0x10000,True,'this+58'); fields['fieldIndex'] = 316252
    n,_ = read_int(r,'this+58-count','149f0cd28',4,'U32(raw + 0x5928ebcd) xor 0xe5110d4d',lambda x:((x+0x5928ebcd)&U32)^0xe5110d4d,**fields)
    chosen(n == 2,'this+58-count')
    for i in range(n):
        read_int(r,'this+58-item','149f0cdd9',4,'U32(raw + 0x8f18178f)',lambda x:(x+0x8f18178f)&U32,arrayIndex=i,**fields)
    absent('nativeData+18',rawh,4,'ce02','permutation/key encoded default unknown')
    val('nativeData+1c','cf56','U32(raw + 0xed7d386e)',lambda x:(x+0xed7d386e)&U32,p,8)
    absent('nativeData+20',p,2,'cf66','direct DWORD zero')
    val('nativeData+24','d00c','U32(raw + 0xe88bf06f)',lambda x:(x+0xe88bf06f)&U32,rawh,0x80000)
    val('nativeData+28','d065','U32((raw xor 0xc12fa19e) + 0xafca1ce0)',lambda x:((x^0xc12fa19e)+0xafca1ce0)&U32,rawh,0x8000000)
    val('nativeData+2c','d1d9','raw xor 0x3735b1d5',lambda x:x^0x3735b1d5,p,0x10,'d1ea','d1f6')
    val('nativeData+30','d14c','raw xor 0xf6fdb5f0',lambda x:x^0xf6fdb5f0,p,0x4000000,'d15d','d169')
    absent('this+48',rawh,0x100000,'d16c','Empty object unknown')
    val('nativeData+34','d55f','U32((raw xor 0xf87a5ca3) + 0xc311e70e)',lambda x:((x^0xf87a5ca3)+0xc311e70e)&U32,p,0x200000,'d579','d585')
    val('nativeData+38','d49d','U32(raw + 0xbcba8385)',lambda x:(x+0xbcba8385)&U32,rawh,0x800,'d4ae','d4ba')
    absent('nativeData+3c',p,0x2000000,'d4bd','table/key encoded default unknown')
    fields = present(rawh,0x1000,True,'this+28'); fields['fieldIndex'] = 316246
    n,_ = read_int(r,'this+28-count','149f0d701',4,'raw xor 0x6d228175',lambda x:x^0x6d228175,**fields)
    chosen(n == 20,'this+28-count')
    for i in range(n):
        read_int(r,'this+28-item','149f0d7d9',4,'raw xor 0x79de8e23',lambda x:x^0x79de8e23,arrayIndex=i,**fields)
    val('nativeData+3d','d871','raw xor 0x35fae5db',lambda x:x^0x35fae5db,p,0x40000000)
    absent('nativeData+41',p,0x8000,'d881','direct bool false')
    fields = present(rawh,0x800000,True,'this+50'); fields['fieldIndex'] = 316251
    n,t = read_int(r,'17208-array-count','147e91d89',4,'U32(raw + 0x2bedd918) xor 0x33f5051c',lambda x:((x+0x2bedd918)&U32)^0x33f5051c,**fields)
    chosen(n == 4,'17208-array-count')
    t['normalReturnConditions'] = 'allocation, construction, element and storage calls return normally'
    for i in range(n):
        element(r,i)
    tail = val('nativeData+42','db23','U32(raw + 0xbbf30334)',lambda x:(x+0xbbf30334)&U32,p,0x400000,'db34','db40')
    r.tokens[-1]['wideSum'] = f'{int(r.tokens[-1]["inputBits"],16)+0xbbf30334:x}'
    absent('this+38',rawh,0x20000000,'db43','Empty object unknown')


def parse(data, cursor=4, candidate_end=334):
    first = base.parse(data,cursor,candidate_end)
    tokens = first['tokens']
    for t in tokens:
        if t['span'] is not None:
            t.update(bounds(t['cursorBefore'],t['width'],len(data),candidate_end))
        else:
            t.update(originalStartCheck=None,physicalFullWidthCheck=None,movsxdAddressOffset=None,candidateBoundaryCrossed=False)
        arithmetic_width = 32 if t['phase'] == 'element-header' else 64 if t['phase'] == 'base-block' else t['width']*8
        t.setdefault('transformWidthBits', arithmetic_width)
    result = dict(schema='proud-single-key-conditional-file-v1', key=22101, startCursor=cursor,
        physicalEnd=len(data), candidateEnd=candidate_end, tokens=tokens, parseStatus='partial',
        conditions=base.CONDITIONS, completeConditionalSingleKeyFileSyntax=False,
        completeProudReference=False,naturalBackendProven=False,naturalModeObserved=False,
        actualCursorObserved=False,actualIndexFilled=False,actualRowSpanApproved=False,
        typedObjectSuccessProven=False,actualEncodedValues=None,alias=None,resourceReady=False,
        completeAvatar=False,d02Complete=False,nativeParserExecuted=False,targetCPU=0)
    if first['parseStatus'] != 'complete_prefix':
        result.update(stopReason=first['stopReason'],stoppedCursor=first['stoppedCursor'])
        return result
    r = Reader(data,first['stoppedCursor'],candidate_end)
    r.tokens = tokens
    try:
        derived(r)
        result.update(parseStatus='complete', completeConditionalSingleKeyFileSyntax=(cursor == 4 and len(data) == 2132778 and digest(data) == base.PAYLOAD_SHA),
                      candidateMatches=(r.cursor == candidate_end and cursor == 4))
    except base.FileStop as e:
        result['stopReason'] = str(e)
    result['stoppedCursor'] = r.cursor
    result['consumedBytes'] = r.cursor-cursor
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',required=True)
    parser.add_argument('--audit')
    args = parser.parse_args(argv)
    if any(Path(n).name != n for n in (args.output,args.audit) if n is not None):
        parser.error('use a new task-local basename')
    DIR.mkdir(parents=True,exist_ok=True)
    with (DIR/args.output).open('x',encoding='utf-8',newline='\n') as stream:
        try:
            data = preflight()
            result = parse(data)
            if result['parseStatus'] != 'complete' or result['stoppedCursor'] != 334:
                raise ValueError('fixed sample incomplete: '+str(result.get('stopReason')))
            report = dict(sourceSHA256=digest(Path(__file__).read_bytes()),payloadSHA256=digest(data),
                          specSHA256=PINS[SPEC][1],contractSHA256=PINS[CONTRACT][1],result=result)
            encoded = json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2,allow_nan=False)+'\n'
            if args.audit and (DIR/args.audit).read_bytes() != encoded.encode('utf-8'):
                raise ValueError('reference differs from prior artifact')
            stream.write(encoded)
            return 0
        except (OSError,ValueError) as e:
            json.dump(dict(accepted=False,error=str(e)),stream,sort_keys=True,indent=2)
            stream.write('\n')
            return 1


if __name__ == '__main__':
    raise SystemExit(main())
