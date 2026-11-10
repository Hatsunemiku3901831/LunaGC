"""Build a full-helper bootstrap from saved owned PE/map files only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct

HEADER = '26fafcb733635adfc62b1a8afcc6a9139ab69142196e7a02bd43fdff2b77ff48'
MANIFEST = '66bf1cc7d2506e82741576f043412daf21a2299634021b660de06d9de6c81cdd'
WRAPPER = bytes.fromhex('4883ec284989cb4889d131d241b830000000cc41ffd34883c428c3')
XCR = bytes.fromhex('31c90f01d048c1e2204809d0c3')
SYMBOLS = ('g_c48FullDescriptor', 'C48FullWrapper', 'C48FullWrapperBegin', 'C48FullArm',
           'C48FullCall', 'C48FullAfterCall', 'C48FullWrapperEnd', 'C48FullReadXcr0',
           'C48FullXcrEntry', 'C48FullXcrEnd')
BUILD_SOURCES = {'shared.h', 'child.c', 'full_wrapper.asm', 'parent.c', 'build.ps1'}
GENERATED = {'catalog.generated.h': '74b50f4f91b5d63fba2bb8a169958ef23046a3ce9cb2f8e5675f47b6e0cb205f',
             'cases.generated.h': '5898f72e5779a3cf7041661a27c95e8ef29e4dd2c53b0a988ce9d91e0a4a428a'}


def need(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def unique(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'duplicate JSON key: ' + key)
        result[key] = value
    return result


def contracts(directory):
    raw = (directory / 'contract-artifacts.private.json').read_bytes()
    need(sha(raw) == MANIFEST, 'contract manifest drift')
    manifest = json.loads(raw, object_pairs_hook=unique)
    expected = {'cases.v1.private.json', 'catalog.v1.private.json', 'events-and-results.v1.private.json',
                'interface.v1.private.json', 'packages.v1.private.json', 'shared.h.proposal'}
    need(set(manifest['artifacts']) == expected, 'contract artifact set')
    result = {}
    for name, pin in manifest['artifacts'].items():
        data = (directory / name).read_bytes()
        need(len(data) == pin['bytes'] and sha(data) == pin['sha256'], 'contract drift: ' + name)
        result[name] = data
        if name.endswith('.json'):
            json.loads(data, object_pairs_hook=unique)
    return result


def pe_info(path):
    raw = path.read_bytes()
    need(len(raw) >= 64 and raw[:2] == b'MZ', 'PE DOS header')
    pe = struct.unpack_from('<I', raw, 0x3c)[0]
    need(pe + 24 <= len(raw) and raw[pe:pe+4] == b'PE\0\0', 'PE signature')
    machine, count, _, _, _, opt_size, _ = struct.unpack_from('<HHIIIHH', raw, pe+4)
    opt = pe + 24
    need(machine == 0x8664 and opt_size >= 112 and opt + opt_size + count * 40 <= len(raw), 'PE header bounds')
    need(struct.unpack_from('<H', raw, opt)[0] == 0x20b, 'PE32+ required')
    base = struct.unpack_from('<Q', raw, opt+24)[0]
    image_size = struct.unpack_from('<I', raw, opt+56)[0]
    need(image_size > 0 and count > 0, 'empty image')
    sections = []
    for i in range(count):
        p = opt + opt_size + 40*i
        name = raw[p:p+8].rstrip(b'\0').decode('ascii')
        vs, va, rs, rp = struct.unpack_from('<IIII', raw, p+8)
        flags = struct.unpack_from('<I', raw, p+36)[0]
        need(va + vs <= image_size and rp + rs <= len(raw), 'section outside image/file')
        sections.append((name, va, vs, rp, rs, flags))
    return raw, base, image_size, sections


def mapped_code(raw, sections, rva, expected):
    matches = [s for s in sections if s[1] <= rva and rva + len(expected) <= s[1] + min(s[2], s[4])]
    need(len(matches) == 1, 'code mapping')
    s = matches[0]
    need(s[5] & 0x60000000 == 0x60000000 and not s[5] & 0x80000000, 'code permissions')
    offset = s[3] + rva - s[1]
    need(raw[offset:offset+len(expected)] == expected, 'code bytes')
    return {'rva': rva, 'bytes': expected.hex(), 'section': s[0], 'fileOffset': offset, 'flags': s[5]}


def map_symbols(path, base):
    result = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        match = re.match(r'^\s*[0-9a-fA-F]+:[0-9a-fA-F]+\s+(\S+)\s+([0-9a-fA-F]{8,16})\b', line)
        if match and match[1] in SYMBOLS:
            need(match[1] not in result, 'duplicate map symbol: ' + match[1])
            result[match[1]] = int(match[2], 16) - base
    need(set(result) == set(SYMBOLS), 'missing map symbol')
    return result


def audit_build(build, source_dir):
    record = json.loads((build / 'build-record.private.json').read_text(encoding='utf-8-sig'), object_pairs_hook=unique)
    need(record['schema'] == 'c48-full-helper-build-v1' and type(record['exit']) is int and record['exit'] == 0 and
         record['sharedHeaderSHA256'] == HEADER and record['fixtureExecuted'] is False and
         record['original904Executed'] is False, 'build record')
    sources = record['source']
    need(len(sources) == 5 and {x['name'] for x in sources} == BUILD_SOURCES, 'build source set')
    manifest = json.loads((build / 'source-manifest.private.json').read_text(encoding='utf-8-sig'), object_pairs_hook=unique)
    need(manifest == sources, 'source manifest')
    for item in sources:
        raw = (build / item['name']).read_bytes()
        need(type(item['bytes']) is int and len(raw) == item['bytes'] and sha(raw) == item['sha256'] and
             raw == (source_dir / item['name']).read_bytes(), 'source snapshot drift: ' + item['name'])
    entries = record['files']
    need(len(entries) == len({e['name'] for e in entries}), 'duplicate build file')
    names = {e['name'] for e in entries}
    need(BUILD_SOURCES | set(GENERATED) | {'build-epoch.h', 'child.exe', 'child.map', 'parent.exe',
         'parent.map', 'source-manifest.private.json'} <= names, 'missing build file')
    for item in entries:
        name = item['name']
        need(isinstance(name, str) and Path(name).name == name, 'invalid build filename')
        raw = (build / name).read_bytes()
        need(type(item['bytes']) is int and len(raw) == item['bytes'] and sha(raw) == item['sha256'], 'build file drift: ' + name)
    for name, digest in GENERATED.items():
        need(sha((build / name).read_bytes()) == digest, 'generated header drift: ' + name)
    epoch = (build / 'build-epoch.h').read_text(encoding='ascii')
    need(re.fullmatch(r'\s*#define\s+C48F_SHARED_HEADER_SHA256_HEX\s+"' + HEADER + r'"\s*', epoch) is not None, 'build epoch')
    return sources


def prepare(build, header, contract_dir, output):
    need(all(p.is_absolute() for p in (build, header, contract_dir, output)), 'absolute paths required')
    need(build.is_dir() and header.is_file() and contract_dir.is_dir() and not output.exists(), 'input/output path')
    fixed = contracts(contract_dir)
    header_raw = header.read_bytes()
    need(sha(header_raw) == HEADER and (build / 'shared.h').read_bytes() == header_raw, 'header drift')
    sources = audit_build(build, header.parent)
    raw, base, image_size, sections = pe_info(build / 'child.exe')
    labels = map_symbols(build / 'child.map', base)
    need(all(0 <= x < image_size for x in labels.values()), 'label outside image')
    begin = labels['C48FullWrapperBegin']
    need((labels['C48FullWrapper'], labels['C48FullArm'], labels['C48FullCall'],
          labels['C48FullAfterCall'], labels['C48FullWrapperEnd']) ==
         (begin, begin+18, begin+19, begin+22, begin+27), 'wrapper labels')
    xcr = labels['C48FullXcrEntry']
    need(labels['C48FullReadXcr0'] == xcr and labels['C48FullXcrEnd'] == xcr+13, 'XCR labels')
    proofs = {'wrapper': mapped_code(raw, sections, begin, WRAPPER), 'xcr': mapped_code(raw, sections, xcr, XCR)}
    descriptor = labels['g_c48FullDescriptor']
    matches = [s for s in sections if s[1] <= descriptor and descriptor+384 <= s[1]+s[2]]
    need(descriptor+384 <= image_size and len(matches) == 1 and
         matches[0][5] & 0xc0000000 == 0xc0000000 and not matches[0][5] & 0x20000000, 'descriptor permissions/bounds')
    interface = json.loads(fixed['interface.v1.private.json'], object_pairs_hook=unique)
    bootstrap = struct.pack('<8sIIIIQ32s32s32s32s32s8IQ4I8s', b'C48FBO01', 1, 256, descriptor, 3,
        image_size, hashlib.sha256(raw).digest(), bytes.fromhex(HEADER), bytes.fromhex(interface['source904SHA256']),
        bytes.fromhex(interface['catalogSHA256']), bytes.fromhex(interface['casesSHA256']),
        labels['C48FullArm'], labels['C48FullCall'], labels['C48FullAfterCall'], labels['C48FullWrapperEnd'],
        begin, 384, 0x01fdf9c0, 904, 0x06d20000, 2, 2, 2, 27, bytes(8))
    need(len(bootstrap) == 256, 'bootstrap size')
    mirror = {'schema': 'c48-full-helper-bootstrap-root-v1', 'executed': False, 'bootstrapSHA256': sha(bootstrap),
              'childExeSHA256': sha(raw), 'sharedHeaderSHA256': HEADER, 'imageBytes': image_size,
              'source904SHA256': interface['source904SHA256'], 'catalogSHA256': interface['catalogSHA256'],
              'casesSHA256': interface['casesSHA256'],
              'labelsRva': labels, 'byteProof': proofs, 'descriptorSection': matches[0][0],
              'childMapSHA256': sha((build / 'child.map').read_bytes()), 'contractManifestSHA256': MANIFEST,
              'source': sources,
              'dependencyReviewRequired': True, 'callerUnwindReviewRequired': True}
    output.mkdir()
    (output / 'bootstrap.bin').write_bytes(bootstrap)
    with (output / 'bootstrap.private.json').open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(mirror, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write('\n')
    return mirror


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('build', 'header', 'contract-dir', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    a = parser.parse_args()
    print(json.dumps(prepare(a.build, a.header, a.contract_dir, a.output), sort_keys=True))


if __name__ == '__main__':
    main()
