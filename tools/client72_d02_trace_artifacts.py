"""Prepare root-owned witness bootstrap from linked PE/map; never starts a process."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.client72_d02_native_avx import pe_info, mapped_bytes

PRIVATE = ROOT / 'local/migration72/d02-resume-20261110b'
HEADER = '689c722e78d116d8de876238eea125958d289a1c2b7971ad7b34baa3cacb61b1'
CONTRACT = PRIVATE / 'a-clear48-trace-implementation-contract'
CONTRACT_PINS = {
    'interface.v1.private.json': '0230c5212b0f1645345069455716e50a1635b575d6708670f03cf0bd2b7ae832',
    'witness-cases.private.json': '1250c0bdc83182de47b89b33981d08b741679e8ac68b4978e6014cbf1932be99',
    'event-schema.v1.private.json': '5cc14c09ac7682101f2af9f052920b503a9b7e7e79c7771d877eae4f6f5fb461',
    'frozen-inputs.private.json': 'a965f6309a988ea221b4789e02e794a17c9616090c1534a460ae9e11049627f5',
}
BUILD_SOURCES = ('shared.h', 'child.c', 'witness.asm', 'parent.c', 'build.ps1')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def write_json(path, value):
    with path.open('x', encoding='utf-8', newline='\n') as output:
        json.dump(value, output, ensure_ascii=False, sort_keys=True, indent=2)
        output.write('\n')


def map_symbols(path, wanted):
    result = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        match = re.match(r'^\s*[0-9a-fA-F]+:[0-9a-fA-F]+\s+(\S+)\s+([0-9a-fA-F]{8,16})\b', line)
        if match and match[1] in wanted:
            require(match[1] not in result, 'duplicate symbol: ' + match[1])
            result[match[1]] = int(match[2], 16)
    require(set(result) == set(wanted), 'missing map symbol')
    return result


def prepare(build, output):
    owned = (PRIVATE / 'a-clear48-trace-witness').resolve()
    require(build.resolve().is_relative_to(owned) and output.resolve().is_relative_to(owned), 'non-owned artifact path')
    require(build.is_dir() and not output.exists(), 'build missing or output exists')
    output.mkdir()
    inputs = {}
    for name, expected in CONTRACT_PINS.items():
        raw = (CONTRACT / name).read_bytes()
        require(sha(raw) == expected, 'contract drift: ' + name)
        inputs[name] = json.loads(raw)
    header = (ROOT / 'tools/client72_d02_native_trace/shared.h').read_bytes()
    require(sha(header) == HEADER and (build / 'shared.h').read_bytes() == header, 'header epoch drift')
    build_record = json.loads((build / 'build-record.private.json').read_text(encoding='utf-8-sig'))
    require(build_record['exit'] == 0 and build_record['sharedHeaderSHA256'] == HEADER and not build_record['fixtureExecuted'], 'build not accepted')
    records = build_record['source']
    require(len(records) == len(BUILD_SOURCES) and {r['name'] for r in records} == set(BUILD_SOURCES), 'required build source set differs')
    source = []
    for record in records:
        name = record['name']
        raw = (build / name).read_bytes()
        require(sha(raw) == record['sha256'] and len(raw) == record['bytes'], 'built source snapshot drift')
        require(raw == (ROOT / 'tools/client72_d02_native_trace' / name).read_bytes(), 'current source drift')
        source.append(record)
    exe = build / 'child.exe'
    raw, base, sections = pe_info(exe)
    pe_offset = struct.unpack_from('<I', raw, 0x3c)[0]
    image_size = struct.unpack_from('<I', raw, pe_offset + 24 + 56)[0]
    wanted = [
        'g_c48Descriptor', 'C48Wrapper', 'C48WrapperBegin', 'C48Arm', 'C48Call',
        'C48AfterCall', 'C48WrapperEnd', 'C48ReturnLeaf', 'C48ReturnLeafEnd',
        'C48YmmLeaf', 'C48YmmLeafEnd', 'C48FaultLeaf', 'C48FaultLeafEnd',
        'C48ReadXcr0', 'C48XcrEntry', 'C48XcrEnd',
    ]
    labels = {k: value - base for k, value in map_symbols(build / 'child.map', wanted).items()}
    begin = labels['C48WrapperBegin']
    require(labels['C48Wrapper'] == begin and labels['C48Arm'] == begin + 16 and
            labels['C48Call'] == begin + 17 and labels['C48AfterCall'] == begin + 20 and
            labels['C48WrapperEnd'] == begin + 25, 'wrapper label drift')
    cases = inputs['witness-cases.private.json']
    leaves = [('C48WrapperBegin', cases['sharedWrapper']['hex'], 'C48WrapperEnd')]
    leaves.extend((name, case['leafHex'], end) for case, name, end in zip(cases['cases'],
        ('C48ReturnLeaf', 'C48YmmLeaf', 'C48FaultLeaf'), ('C48ReturnLeafEnd', 'C48YmmLeafEnd', 'C48FaultLeafEnd')))
    leaves.append(('C48XcrEntry', '31c90f01d048c1e2204809d0c3', 'C48XcrEnd'))
    bytes_proof = {}
    for name, code_hex, end in leaves:
        code = bytes.fromhex(code_hex)
        actual, mapping = mapped_bytes(raw, sections, labels[name], len(code))
        require(actual == code and labels[end] == labels[name] + len(code), 'leaf bytes or extent: ' + name)
        bytes_proof[name] = {'rva': labels[name], 'bytes': code_hex, 'sha256': sha(code), 'mapping': mapping}
    require(labels['C48ReadXcr0'] == labels['C48XcrEntry'], 'XCR alias')
    descriptor_rva = labels['g_c48Descriptor']
    matches = [s for s in sections if s[1] <= descriptor_rva and descriptor_rva + 256 <= s[1] + s[2]]
    require(len(matches) == 1 and matches[0][5] & 0x80000000 and not matches[0][5] & 0x20000000, 'descriptor not writable non-code')
    require(descriptor_rva + 256 <= image_size, 'descriptor outside image')
    bootstrap = struct.pack('<8sIIIIQ32s32s8I', b'C48BOOT1', 1, 128, descriptor_rva, 7, image_size,
        hashlib.sha256(raw).digest(), bytes.fromhex(HEADER), labels['C48Arm'], labels['C48Call'],
        labels['C48AfterCall'], labels['C48WrapperEnd'], labels['C48ReturnLeaf'], labels['C48YmmLeaf'],
        labels['C48FaultLeaf'], labels['C48WrapperBegin'])
    require(len(bootstrap) == 128, 'bootstrap bytes')
    with (output / 'bootstrap.bin').open('xb') as stream:
        stream.write(bootstrap)
    summary = {'schema': 'c48-bootstrap-root-v1', 'executed': False,
        'imageBytesMeaning': 'PE OptionalHeader.SizeOfImage, not file length',
        'imageBytes': image_size, 'childFileBytes': len(raw), 'childExeSHA256': sha(raw),
        'sharedHeaderSHA256': HEADER, 'bootstrapSHA256': sha(bootstrap), 'labelsRva': labels,
        'source': source, 'contractPins': CONTRACT_PINS, 'byteProof': bytes_proof,
        'dependencyReviewRequired': True, 'callerUnwindReviewRequired': True,
        'original904Linked': False, 'nativeCPU': 0}
    write_json(output / 'bootstrap.private.json', summary)
    print(json.dumps({'bootstrapBytes': 128, 'bootstrapSHA256': sha(bootstrap), 'nativeCPU': 0}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(args.build, args.output)


if __name__ == '__main__':
    main()
