"""Find exact ConfigMeta MonoScript references in fixed built-in v17 catalogs.

Only installed asset files are read. Bundle files, code, processes and target
execution are outside this catalog audit.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PurePosixPath
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / 'local/migration72/d02-resume-20261111b/scene'
ASSET_PINS = 'assets-inputs.private.json'
ASSET_PINS_SHA = '5af18d727c823f44255b482c4213f226edaad57edce100fb50c6efdbfd4f2371'
PROFILE_SHA = 'a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d'
MANIFEST = 'local/migration72/evidence/manifest-full.json'
MANIFEST_SHA = 'cd572bacd2c9dbbb8372d22cfe0fae675c5c40844ca78bc71f17f000d253af00'
TARGET = ('ConfigMetaConfig', 'MoleMole.Config', 'Assembly-CSharp.dll')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.parent != PRIVATE or output.exists():
        parser.error('Use a fresh filename in this card private directory')
    profile = json.loads(verified(ROOT / 'protocol/cn-7.2/profile.json', PROFILE_SHA))
    pins = json.loads(verified(PRIVATE / ASSET_PINS, ASSET_PINS_SHA))
    manifest = json.loads(verified(ROOT / MANIFEST, MANIFEST_SHA))
    client = Path(subprocess.check_output([
        'powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        str(ROOT / 'tools/game_path.ps1')], text=True).strip())
    for row in profile['files'].values():
        verified(client / row['path'], row['sha256'], row['size'])
    installed = {entry['path']: entry for section in manifest['manifests']
                 for entry in section['entries']}
    catalogs = {}
    for pin in pins:
        raw = verified(client / pin['path'], pin['sha256'], pin['bytes'])
        expected = installed[pin['path']]
        if (expected['status'] != 'matched' or expected['expectedSize'] != len(raw)
                or hashlib.md5(raw).hexdigest() != expected['expectedMD5']):
            raise ValueError('Selected installed manifest entry differs: ' + pin['path'])
        catalogs[Path(pin['path']).name] = (raw, parse_directory(raw))
    report = audit_catalogs(catalogs)
    report.update(task='F-03d3s2/SCENE-ACTUAL-BUILTIN-CATALOGS',
                  assetSources=pins, assetPinsSHA256=ASSET_PINS_SHA,
                  profileSHA256=PROFILE_SHA, manifestSHA256=MANIFEST_SHA,
                  toolSHA256=digest(Path(__file__).read_bytes()),
                  scope='Fixed built-in catalogs only; manifest consistency is not origin authentication.',
                  actualPathHashRecovered=False, actualMPLevelGroupRecovered=False,
                  bundleCatalogsInspected=False, clientExecuted=False)
    raw = (json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode()
    with output.open('xb') as stream:
        stream.write(raw)
    print(json.dumps(dict(catalogs=len(catalogs), scripts=len(report['targetScripts']),
                          references=len(report['targetReferences']), bytes=len(raw), sha256=digest(raw))))


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def verified(path, expected, size=None):
    raw = path.read_bytes()
    if (size is not None and len(raw) != size) or digest(raw) != expected:
        raise ValueError('Frozen source changed: ' + str(path))
    return raw


class Reader:
    def __init__(self, raw, start=0, end=None):
        self.raw, self.pos, self.end = raw, start, len(raw) if end is None else end

    def take(self, size):
        if size < 0 or self.pos + size > self.end:
            raise ValueError('Serialized EOF at ' + str(self.pos))
        raw = self.raw[self.pos:self.pos + size]
        self.pos += size
        return raw

    def read(self, fmt):
        values = struct.unpack('<' + fmt, self.take(struct.calcsize('<' + fmt)))
        return values[0] if len(values) == 1 else values

    def align(self):
        self.take((-self.pos) & 3)

    def cstring(self):
        end = self.raw.find(b'\0', self.pos, self.end)
        if end < 0:
            raise ValueError('Unterminated serialized string')
        raw = self.take(end - self.pos + 1)
        return raw[:-1].decode('utf8')

    def string(self):
        value = self.take(self.read('i')).decode('utf8')
        self.align()
        return value

    def count(self, minimum):
        count = self.read('i')
        if count < 0 or count > (self.end - self.pos) // minimum:
            raise ValueError('Impossible serialized count')
        return count


def parse_directory(raw):
    if len(raw) < 20:
        raise ValueError('Truncated serialized header')
    meta, size, version, data = struct.unpack_from('>4I', raw)
    if (size != len(raw) or version != 17 or raw[16:20] != bytes(4)
            or not 20 + meta <= data <= size):
        raise ValueError('Only complete little-endian v17 catalogs are supported')
    r = Reader(raw, 20, 20 + meta)
    engine = r.cstring()
    platform, tree = r.read('iB')
    if engine != '2017.4.30f1' or platform != 19 or tree != 0:
        raise ValueError('Unreviewed Unity version/platform/type-tree')
    types = []
    for index in range(r.count(23)):
        kind, stripped, script = r.read('iBh')
        script_hash = r.take(16).hex() if kind == 114 else None
        types.append(dict(index=index, classID=kind, stripped=stripped,
                          scriptIndex=script, scriptHash=script_hash, typeHash=r.take(16).hex()))
    objects, seen = [], set()
    for ordinal in range(r.count(20)):
        r.align()
        pid, start, length, typ = r.read('qIIi')
        if not 0 <= typ < len(types) or start + data + length > size or pid in seen:
            raise ValueError('Invalid serialized object identity or extent')
        seen.add(pid)
        objects.append(dict(ordinal=ordinal, pathID=pid, start=start + data,
                            bytes=length, typeIndex=typ, classID=types[typ]['classID']))
    object_table_end = r.pos
    scripts = []
    for index in range(r.count(12)):
        file_id = r.read('i')
        r.align()
        scripts.append(dict(index=index, fileID=file_id, pathID=r.read('q')))
    externals = []
    for index in range(r.count(22)):
        empty = r.cstring()
        guid, typ, path = r.take(16).hex(), r.read('i'), r.cstring()
        externals.append(dict(fileID=index + 1, empty=empty, guid=guid, type=typ, path=path))
    user_information = r.cstring()
    if r.pos != r.end:
        raise ValueError('Unconsumed v17 metadata')
    return dict(metadataBytes=meta, dataOffset=data, engine=engine, platform=platform,
                metadataEnd=r.pos, objectTableEnd=object_table_end, types=types,
                objects=objects, scripts=scripts, externals=externals, userInformation=user_information)


def mono_script(raw):
    r = Reader(raw)
    name, order = r.string(), r.read('i')
    properties = r.take(16).hex()
    class_name, namespace, assembly = r.string(), r.string(), r.string()
    editor = r.read('B')
    if r.pos != r.end:
        raise ValueError('Unconsumed MonoScript object')
    return dict(name=name, executionOrder=order, propertiesHash=properties,
                className=class_name, namespace=namespace, assembly=assembly, editor=editor)


def audit_catalogs(catalogs):
    summaries, targets, references = [], [], []
    for name, (raw, catalog) in sorted(catalogs.items()):
        for obj in catalog['objects']:
            if obj['classID'] != 115:
                continue
            source = raw[obj['start']:obj['start'] + obj['bytes']]
            script = mono_script(source)
            if (script['className'], script['namespace'], script['assembly']) == TARGET:
                targets.append(dict(catalog=name, **obj, script=script,
                                    objectSHA256=digest(source), objectHex=source.hex()))
    target_keys = {(target['catalog'], target['pathID']) for target in targets}
    for name, (raw, catalog) in sorted(catalogs.items()):
        checked = 0
        for obj in catalog['objects']:
            if obj['classID'] != 114:
                continue
            typ = catalog['types'][obj['typeIndex']]
            if not 0 <= typ['scriptIndex'] < len(catalog['scripts']):
                raise ValueError('MonoBehaviour script index outside script table')
            script = catalog['scripts'][typ['scriptIndex']]
            if not 0 <= script['fileID'] <= len(catalog['externals']) or obj['bytes'] < 28:
                raise ValueError('MonoBehaviour script reference outside catalog')
            observed = struct.unpack_from('<iq', raw, obj['start'] + 16)
            if observed != (script['fileID'], script['pathID']):
                raise ValueError('MonoBehaviour PPtr differs from metadata script table')
            checked += 1
            external = (catalog['externals'][script['fileID'] - 1]['path']
                        if script['fileID'] else name)
            file_name = PurePosixPath(external.replace('\\', '/')).name
            if (file_name, script['pathID']) in target_keys:
                references.append(dict(catalog=name, **obj, script=script, scriptFile=external))
        summaries.append(dict(catalog=name, metadataBytes=catalog['metadataBytes'],
                              metadataEnd=catalog['metadataEnd'], objectTableEnd=catalog['objectTableEnd'],
                              types=len(catalog['types']), objects=len(catalog['objects']),
                              classCounts=dict(sorted(Counter(o['classID'] for o in catalog['objects']).items())),
                              scriptTypes=len(catalog['scripts']), externals=catalog['externals'],
                              monoBehaviourPPtrsChecked=checked))
    return dict(catalogs=summaries, targetScripts=targets, targetReferences=references,
                targetTuple=list(TARGET),
                conclusion='Target MonoScript references in these built-in catalogs only; bundle references are not searched.')


if __name__ == '__main__':
    main()
