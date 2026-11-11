"""Read the fixed ConfigMeta serialized directory and bind its MonoScript identity."""
import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.migration72.client72_scene_mplevel_actual_assets import Reader, mono_script

OUT = ROOT/'local/migration72/d02-resume-20261111b/scene-configmeta-serialized'
REQUEST = 'local/migration72/d02-resume-20261111b/scene/configmeta-serialized-directory-request.private.md'
CONTENT = 'local/migration72/d02-resume-20261111b/scene-configmeta-ooz/main-third/content.bin'
ASSETS = 'local/migration72/d02-resume-20261111b/scene/assets-main-third.private.json'
PINS = {REQUEST: (6083, '156300e9e6e3431cea49c7fd61664149aa181d92b62cc5989deb27867fe3a9f5'),
        CONTENT: (334996, '5dbd48d5856e05ee877932dc7282a51ce30e03e4208174fd43c327f3a68f5a5e'),
        ASSETS: (20492, '3b3bc0c7cb26678cb672606832c229571000279add93ad6703351495bc63239d'),
        'tools/migration72/client72_scene_mplevel_actual_assets.py': (None, 'b7855047e003c49d5eeaa1bda932e076b462ba973506432e698677c21f6a75d3'),
        'tools/client72_resource_payload.py': (25622, 'b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa')}
ENGINE = '2017.4.30f1\n2'
FILES = ('pre-parse.private.json', 'directory.private.json', 'identity.private.json', 'object-0.bin', 'object-1.bin', 'object-2.bin', 'unparsed-script-data.bin')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def checked(path, size, digest):
    raw = path.read_bytes()
    require((size is None or len(raw) == size) and sha(raw) == digest, 'pin mismatch: '+path.name)
    return raw


def save(path, obj):
    with path.open('xb') as f:
        f.write((json.dumps(obj, sort_keys=True, ensure_ascii=False, indent=2)+'\n').encode())


def header(raw):
    require(len(raw) >= 20, 'truncated header')
    meta, size, version, data = struct.unpack_from('>4I', raw)
    require(size == len(raw) and version == 17 and raw[16:20] == bytes(4) and 20+meta <= data <= size, 'invalid v17 header')
    return meta, size, data


def parse_directory(raw):
    meta, size, data = header(raw)
    r = Reader(raw, 20, 20+meta)
    engine_offset = r.pos
    engine = r.cstring()
    platform_offset = r.pos
    platform, tree = r.read('iB')
    require(engine == ENGINE and platform == 19 and tree == 0, 'engine/platform/tree mismatch')
    types_count_offset = r.pos
    types = []
    for index in range(r.count(23)):
        begin = r.pos
        kind, stripped, script = r.read('iBh')
        hash_offset = r.pos if kind == 114 else None
        script_hash = r.take(16).hex() if kind == 114 else None
        type_hash_offset = r.pos
        typ_hash = r.take(16).hex()
        types.append({'index': index, 'offset': begin, 'classID': kind, 'stripped': stripped, 'scriptIndex': script,
                      'classIDOffset': begin, 'strippedOffset': begin+4, 'scriptIndexOffset': begin+5,
                      'scriptHashOffset': hash_offset, 'scriptHash': script_hash, 'typeHashOffset': type_hash_offset, 'typeHash': typ_hash, 'end': r.pos})
    object_count_offset = r.pos
    objects, seen = [], set()
    for ordinal in range(r.count(20)):
        r.align()
        offset = r.pos
        pid, start, length, typ = r.read('qIIi')
        require(0 <= typ < len(types) and start+data+length <= size and pid not in seen, 'invalid object identity or extent')
        seen.add(pid)
        objects.append({'ordinal': ordinal, 'recordOffset': offset, 'pathID': pid, 'relativeStart': start, 'start': start+data,
                        'bytes': length, 'typeIndex': typ, 'classID': types[typ]['classID'],
                        'fieldOffsets': {'pathID': offset, 'byteStart': offset+8, 'byteSize': offset+12, 'typeIndex': offset+16}, 'sha256': sha(raw[start+data:start+data+length])})
    ordered = sorted(objects, key=lambda o: o['start'])
    require(all(a['start']+a['bytes'] <= b['start'] for a, b in zip(ordered, ordered[1:])), 'overlapping objects')
    object_table_end = r.pos
    scripts_count_offset = r.pos
    scripts = []
    for index in range(r.count(12)):
        offset = r.pos
        file_id = r.read('i')
        r.align()
        path_offset = r.pos
        scripts.append({'index': index, 'offset': offset, 'fileID': file_id, 'pathIDOffset': path_offset, 'pathID': r.read('q')})
    externals_count_offset = r.pos
    externals = []
    for index in range(r.count(22)):
        offset = r.pos
        empty = r.cstring()
        guid, typ, path = r.take(16).hex(), r.read('i'), r.cstring()
        externals.append({'index': index, 'offset': offset, 'fileID': index+1, 'empty': empty, 'guid': guid, 'type': typ, 'path': path})
    user_offset = r.pos
    user = r.cstring()
    require(r.pos == r.end, 'unconsumed metadata')
    by_id = {o['pathID']: o for o in objects}
    for typ in types:
        if typ['classID'] == 114:
            require(0 <= typ['scriptIndex'] < len(scripts), 'script index invalid')
            script = scripts[typ['scriptIndex']]
            require(script['fileID'] == 0 and script['pathID'] in by_id and by_id[script['pathID']]['classID'] == 115, 'script reference is not local class115')
    return {'headerOffsets': {'metadataBytes': 0, 'fileBytes': 4, 'version': 8, 'dataOffset': 12, 'endianAndReserved': 16},
            'metadataBytes': meta, 'fileBytes': size, 'version': 17, 'dataOffset': data, 'engineOffset': engine_offset, 'engine': engine,
            'platformOffset': platform_offset, 'platform': platform, 'typeTree': tree, 'typesCountOffset': types_count_offset, 'types': types,
            'objectCountOffset': object_count_offset, 'objects': objects, 'objectTableEnd': object_table_end,
            'scriptsCountOffset': scripts_count_offset, 'scripts': scripts, 'externalsCountOffset': externals_count_offset,
            'externals': externals, 'userInformationOffset': user_offset, 'userInformation': user, 'metadataEnd': r.pos}


def identity(raw, directory, target):
    objects, types, scripts = directory['objects'], directory['types'], directory['scripts']
    require(len(target) == 1, 'target script not unique')
    obj = [o for o in objects if o['classID'] == 114]
    require(len(obj) == 1, 'class114 instance not unique')
    obj = obj[0]
    typ = types[obj['typeIndex']]
    pointer = scripts[typ['scriptIndex']]
    scripts_objects = [o for o in objects if o['pathID'] == pointer['pathID'] and o['classID'] == 115]
    require(len(scripts_objects) == 1 and pointer['fileID'] == 0, 'script binding mismatch')
    script_obj = scripts_objects[0]
    script_raw = raw[script_obj['start']:script_obj['start']+script_obj['bytes']]
    parsed = mono_script(script_raw)
    sr = Reader(script_raw)
    script_offsets = {}
    for field, read in [('name', sr.string), ('executionOrder', lambda: sr.read('i')),
                        ('propertiesHash', lambda: sr.take(16).hex()), ('className', sr.string),
                        ('namespace', sr.string), ('assembly', sr.string), ('editor', lambda: sr.read('B'))]:
        offset = sr.pos
        value = read()
        require(value == parsed[field], 'MonoScript field mismatch')
        script_offsets[field] = {'relative': offset, 'absolute': script_obj['start']+offset, 'endRelative': sr.pos}
    require(sr.pos == len(script_raw), 'MonoScript field coverage mismatch')
    prior = target[0]
    require(script_raw.hex() == prior['objectHex'] and sha(script_raw) == prior['objectSHA256'] and parsed == prior['script'], 'MonoScript object or tuple mismatch')
    require((parsed['className'], parsed['namespace'], parsed['assembly']) == ('ConfigMetaConfig','MoleMole.Config','Assembly-CSharp.dll'), 'MonoScript tuple mismatch')
    require(parsed['propertiesHash'] == typ['typeHash'], 'type/properties hash mismatch')
    data = raw[obj['start']:obj['start']+obj['bytes']]
    r = Reader(data)
    game_offset = r.pos
    game = r.read('iq')
    enabled_offset = r.pos
    enabled = r.read('B')
    r.align()
    script_offset = r.pos
    observed = r.read('iq')
    name_offset = r.pos
    name = r.string()
    require(game == (0, 0) and enabled == 1 and observed == (pointer['fileID'], pointer['pathID']) and name == 'MetaConfig' and r.pos == 44, 'MonoBehaviour prefix mismatch')
    return {'class114ObjectOrdinal': obj['ordinal'], 'class114TypeIndex': obj['typeIndex'], 'scriptIndex': typ['scriptIndex'],
            'scriptHash': typ['scriptHash'], 'typeHash': typ['typeHash'], 'scriptPointer': pointer,
            'class115ObjectOrdinal': script_obj['ordinal'], 'scriptObjectSHA256': sha(script_raw), 'monoScript': parsed, 'monoScriptFieldOffsets': script_offsets,
            'prefix': {'gameObjectOffset': game_offset, 'gameObjectAbsoluteOffset': obj['start']+game_offset, 'gameObject': list(game),
                       'enabledOffset': enabled_offset, 'enabledAbsoluteOffset': obj['start']+enabled_offset, 'enabled': enabled,
                       'scriptOffset': script_offset, 'scriptAbsoluteOffset': obj['start']+script_offset, 'scriptPPtr': list(observed),
                       'nameOffset': name_offset, 'nameAbsoluteOffset': obj['start']+name_offset, 'name': name,
                       'cursor': r.pos, 'absoluteCursor': obj['start']+r.pos},
            'unparsedScriptBytes': len(data)-r.pos, 'unparsedScriptSHA256': sha(data[r.pos:]), 'customSchemaParsed': False,
            'PathHashRecovered': False, 'MPLevelRecovered': False, 'naturalLoaderReady': False, 'SceneReady': False, 'D02Ready': False}


def fixed_header(raw):
    require(header(raw) == (193,334996,4096), 'actual header mismatch')


def actual_gate(raw, directory):
    require((directory['metadataBytes'], directory['fileBytes'], directory['dataOffset'], directory['metadataEnd']) == (193, 334996, 4096, 213), 'actual header mismatch')
    expected_types = [(115,-1,None,'2da9c96f9efc8b31a480d5b8858755f3'), (142,-1,None,'97da5f4688e45a57c8b42d4f42497297'), (114,0,'12b0735b23e73993f0270e1a05d69ccb','84d4d013fe32a74a07ac60086b9f9cea')]
    require(all(x['stripped'] == 0 for x in directory['types']) and [(x['classID'],x['scriptIndex'],x['scriptHash'],x['typeHash']) for x in directory['types']] == expected_types, 'actual types mismatch')
    expected_objects = [(-3593561398687781936,0,4096,105,115,'6425b85708815abe7565930dc1379ae2d6f00bcdfeddb7787a97de645336fc8f'), (1,112,4208,176,142,'f8fa4d41805bad7244ebe0a2a22e119689ff78d189928c30533a4be4c34831f0'), (8119248251482879859,288,4384,330612,114,'28334d219f581b798dc48ecd8113714fa55b79a316472e75c0e0d6dea52ebe02')]
    require([(x['pathID'],x['relativeStart'],x['start'],x['bytes'],x['classID'],x['sha256']) for x in directory['objects']] == expected_objects, 'actual objects mismatch')
    require(len(directory['scripts']) == 1 and (directory['scripts'][0]['fileID'],directory['scripts'][0]['pathID']) == (0,expected_objects[0][0]) and not directory['externals'] and directory['userInformation'] == '', 'actual script/external mismatch')


def pins():
    raw = {p: checked(ROOT/p, size, digest) for p,(size,digest) in PINS.items()}
    return raw, {p: {'bytes':len(b),'sha256':sha(b)} for p,b in raw.items()}


def collect(dest):
    result = {'task':'F-03d3s2/CONFIGMETA-SERIALIZED-DIRECTORY', 'stage':'pins', 'toolSHA256':sha(Path(__file__).read_bytes())}
    try:
        raw, result['pins'] = pins()
        result['stage'] = 'pre-parse'
        save(dest/'pre-parse.private.json', result)
        content = raw[CONTENT]
        fixed_header(content)
        directory = parse_directory(content)
        actual_gate(content, directory)
        identity_report = identity(content, directory, json.loads(raw[ASSETS])['targetScripts'])
        require(identity_report['unparsedScriptBytes'] == 330568 and identity_report['prefix']['absoluteCursor'] == 4428, 'actual cursor mismatch')
        save(dest/'directory.private.json', directory)
        save(dest/'identity.private.json', identity_report)
        for obj in directory['objects']:
            (dest/('object-%d.bin'%obj['ordinal'])).write_bytes(content[obj['start']:obj['start']+obj['bytes']])
        obj = directory['objects'][identity_report['class114ObjectOrdinal']]
        (dest/'unparsed-script-data.bin').write_bytes(content[obj['start']+44:obj['start']+obj['bytes']])
        print(json.dumps({'objects':len(directory['objects']),'metadataEnd':directory['metadataEnd'],'scriptDataBytes':identity_report['unparsedScriptBytes'],'scriptDataSHA256':identity_report['unparsedScriptSHA256']}))
    except Exception as exc:
        result.update(passed=False, errorType=type(exc).__name__, error=str(exc))
        save(dest/'failure.private.json', result)
        raise


def audit(first, second, output):
    copies = [{n:(OUT/d/n).read_bytes() for n in FILES} for d in (first,second)]
    require(copies[0] == copies[1], 'fresh artifacts differ')
    files = copies[0]
    raw, checks = pins()
    pre = json.loads(files['pre-parse.private.json'])
    require(pre['stage'] == 'pre-parse' and pre['pins'] == checks and pre['toolSHA256'] == sha(Path(__file__).read_bytes()), 'source proof mismatch')
    fixed_header(raw[CONTENT])
    directory = parse_directory(raw[CONTENT]); actual_gate(raw[CONTENT], directory)
    report = identity(raw[CONTENT], directory, json.loads(raw[ASSETS])['targetScripts'])
    require(directory == json.loads(files['directory.private.json']) and report == json.loads(files['identity.private.json']), 'directory or identity mismatch')
    for obj in directory['objects']:
        require(files['object-%d.bin'%obj['ordinal']] == raw[CONTENT][obj['start']:obj['start']+obj['bytes']], 'object bytes mismatch')
    obj = directory['objects'][report['class114ObjectOrdinal']]
    require(files['unparsed-script-data.bin'] == raw[CONTENT][obj['start']+44:obj['start']+obj['bytes']], 'script data mismatch')
    save(OUT/output, {'passed':True,'freshFilesEqual':True,'currentDecoderCalls':0,'artifacts':{n:{'bytes':len(b),'sha256':sha(b)} for n,b in files.items()}})


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', required=True)
    p.add_argument('--audit', nargs=2)
    a = p.parse_args()
    require(Path(a.output).name == a.output and not (OUT/a.output).exists(), 'fresh task-local basename required')
    OUT.mkdir(parents=True, exist_ok=True)
    if a.audit:
        require(a.output.endswith('.json') and all(Path(x).name == x for x in a.audit), 'invalid audit names')
        audit(*a.audit, a.output)
    else:
        dest = OUT/a.output
        dest.mkdir()
        collect(dest)


if __name__ == '__main__':
    main()
