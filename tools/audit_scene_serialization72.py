"""E-01c: directly exercise the pinned production Lua serializer offline.

No server manager, ScriptLoader, event, trigger, cache or database is invoked.
The grid calculation is an explicitly labeled model of reviewed source.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

try:
    from .audit_scene_scripts72 import ENTITY_TABLES
    from .audit_scene_resource72 import audit_scene, verify_blob
    from .audit_resource72 import index_table, verify_tree, read_client_evidence
except ImportError:
    from audit_scene_scripts72 import ENTITY_TABLES
    from audit_scene_resource72 import audit_scene, verify_blob
    from audit_resource72 import index_table, verify_tree, read_client_evidence


RUNTIME_SHA256 = '3a05ca88115f93a46bbb0ca334f54241f560a291fbb5b05fa2d42bb0f87aa522'
LEGACY_COMMIT = 'dac2a9d43332ef51879522cf39da2831b0ca0543'
# Each interface/algorithm below was read before implementing the direct adapter.
REVIEWED_SOURCES = {
    "tools/Client72SceneScriptAudit.java": "49605e4cf2c66b282523cb3be0895b345bbe273b44e8c9c13e2568646e4668a0",
    "tools/audit_scene_scripts72.py": "7c23f644e2efa4fb1d7c94eb4af814e67b6bbc34453e1fe03fc70fb344de065c",
    "src/main/java/emu/grasscutter/scripts/serializer/LuaSerializer.java": "edc7da379f10a00d4a8ecd9eab7ebda4941113b5b98ad350e7c67e0d81d45e33",
    "src/main/java/emu/grasscutter/scripts/SceneScriptManager.java": "e6c707e1eab5d439e6762a25a0591a2c798125d1e95cb3091a72127929b8b89e",
    "src/main/java/emu/grasscutter/scripts/SceneIndexManager.java": "769a6b3e189c15239e2b805638326a88f3a62b6a3f4b0fe027952db3ab1b75a6",
    "src/main/java/emu/grasscutter/game/world/Position.java": "157543f5cd3213bc28e1c90b8610e6ebef612bba587dccbf0200c93be1ddc529",
    "src/main/java/emu/grasscutter/game/world/GridPosition.java": "723dceddf949bde958d810a288a9e26d7a37af6685fa35e4e4bca120bef21859",
    "src/main/java/emu/grasscutter/data/excels/GadgetData.java": "c8b1a417ee7305f6a1a451bb03f4733644531d2cd61dfb005da21ac0f2599a7e",
    "src/main/java/emu/grasscutter/Grasscutter.java": "38988046618236e4459d7870bb5b9d6517ae6b0a39f4b2c350044174a18942e8",
    "src/main/java/emu/grasscutter/scripts/data/Explore.java": "4b8fbae851eaa5261358305791cf46921db45f081f04058758ad93ab7debdcab",
    "src/main/java/emu/grasscutter/scripts/data/SceneBlock.java": "77714d52c8212b3bea610af8e73100737fdafafdd90346e64b72c8273822c186",
    "src/main/java/emu/grasscutter/scripts/data/SceneBossChest.java": "620cd0f0512fd4b0a006e6ed5b163fa017daa8fd2c2acde2ce6c88c0b598c505",
    "src/main/java/emu/grasscutter/scripts/data/SceneBusiness.java": "6b36ca0975fafd5279e86e37b749b42f627e890b63744ef38c766c2531247dde",
    "src/main/java/emu/grasscutter/scripts/data/SceneConfig.java": "acb257241a54d3cbf477c19f2310c41317c548a05f2f6a5867f2bfd67a0a14a5",
    "src/main/java/emu/grasscutter/scripts/data/SceneGadget.java": "fccdee73e1b1da968be909729ace0f7b23d2c7d56b05b9a2fe6418fa8e0144ac",
    "src/main/java/emu/grasscutter/scripts/data/SceneGadgetCrucibleConfig.java": "80e34cc7f1c44fe3beae9109af5b5e03379daba045821a76f37a121426239f9f",
    "src/main/java/emu/grasscutter/scripts/data/SceneGarbage.java": "f622d03459dbb7ac3720988cebf14f92911dcca0f996fd949e53a7e28cfc5e71",
    "src/main/java/emu/grasscutter/scripts/data/SceneGroup.java": "7e899a4457920cdc304a9db55450b8eb20062ab25f127165432abd9f92d44f7e",
    "src/main/java/emu/grasscutter/scripts/data/SceneInitConfig.java": "43093ddfc327330cf3da73413e7c7e604d72841ba454fea37a4d6ffbe2e368cf",
    "src/main/java/emu/grasscutter/scripts/data/SceneMeta.java": "4708e0fa2c35dedd64d8af444d8524ac02d42747a475d996f7115b2db575198f",
    "src/main/java/emu/grasscutter/scripts/data/SceneMonster.java": "ac92ded377141012ab073ae940706acb2b14fc3269305c8972b0ef5f2312c346",
    "src/main/java/emu/grasscutter/scripts/data/SceneNPC.java": "8f4014aae569f41c002e1c7cc67e3a5d386a0f7f5d0f30972e13ad17fe5c30e6",
    "src/main/java/emu/grasscutter/scripts/data/SceneObject.java": "e5b7ca6d9868e1a183b5ae2b755a67005d508f88dab9b04b66790181ae452d26",
    "src/main/java/emu/grasscutter/scripts/data/ScenePoint.java": "c54ce44f5a7d7d7f6cef3f3caa14bae2a7aeb797ca72a263d5a685636e8353aa",
    "src/main/java/emu/grasscutter/scripts/data/SceneRegion.java": "1fa2ed749d02ad23e4a151bd1aed7e7e7ceba7b212d84b6256a7267a5d0d7737",
    "src/main/java/emu/grasscutter/scripts/data/SceneReplaceable.java": "2a729a204730d0c12aa48007691362fc32071a9421153a163efdbab2aa1720a6",
    "src/main/java/emu/grasscutter/scripts/data/SceneSuite.java": "825f43f6b8506ff22d2154c1d01772c5f978cccdf86da18e478ac6788e7bccf6",
    "src/main/java/emu/grasscutter/scripts/data/SceneTrigger.java": "bd622e53c383c40183f2c3a18f78f73a48c425abdeb6e42e36274cabd645c43a",
    "src/main/java/emu/grasscutter/scripts/data/SceneVar.java": "93fb2c7f8119cc806f7cc41f77a02e7af473f34d8291755ab8f5bf770488986b",
    "src/main/java/emu/grasscutter/scripts/data/ScriptArgs.java": "837fdf6d4e7a19e092a61da2c03adc362beef62c88f99ee71cd8d01bce783e1d"
}


def sha256(path, normalize=False):
    raw = path.read_bytes()
    return hashlib.sha256(raw.replace(b'\r\n', b'\n') if normalize else raw).hexdigest()


def verify_sources(repository):
    for name, expected in REVIEWED_SOURCES.items():
        if sha256(repository / name, True) != expected:
            raise ValueError('Review changed serialization dependency: ' + name)
    return dict(REVIEWED_SOURCES)


def evaluate(repository, scripts, source, java_home, runtime_jar, config, output, work):
    helpers = ('Client72SceneScriptAudit.java', 'Client72SceneSerializeAudit.java')
    subprocess.run([str(java_home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(runtime_jar),
                    '-d', str(work), *(str(repository / 'tools' / name) for name in helpers)],
                   check=True, capture_output=True)
    run = subprocess.run([str(java_home / 'bin/java.exe'), '-Xmx1g', '-XX:-OmitStackTraceInFastThrow', '-Dfile.encoding=UTF-8',
                          '-Xlog:class+init=info:file=class-init.log', '-cp',
                          str(work) + ';' + str(runtime_jar), 'Client72SceneSerializeAudit',
                          str(scripts), str(source), str(config), str(output)],
                         cwd=work, capture_output=True)
    for suffix, raw in (('.java.stdout.log', run.stdout), ('.java.stderr.log', run.stderr),
                        ('.class-init.log', (work / 'class-init.log').read_bytes())):
        output.with_suffix(suffix).write_bytes(raw)
    run.check_returncode()
    initialized = (work / 'class-init.log').read_text('utf8')
    forbidden = re.findall(r"Initializing '(emu/grasscutter/(?:Grasscutter|database/[^']+|scripts/ScriptLoader|scripts/SceneScriptManager))'", initialized)
    if forbidden:
        raise ValueError('Unexpected production initialization: ' + ', '.join(forbidden))
    report = json.loads(output.read_text('utf8'))
    report['productionInfrastructureInitialized'] = False
    return report


def summarize(report):
    result = {key: value for key, value in report.items() if key not in ('files', 'issues')}
    manifest = json.dumps(report['files'], sort_keys=True, separators=(',', ':')).encode('utf8')
    result['scriptManifest'] = dict(files=len(report['files']), sha256=hashlib.sha256(manifest).hexdigest(),
                                  commonFiles=sum(name.startswith('Common/') for name in report['files']))
    result['issueCounts'] = dict(sorted(Counter(row['kind'] for row in report['issues']).items()))
    result['issueGroups'] = len({row['group'] for row in report['issues']})
    # Values are adapter/type observations, not raw scripts or candidate table rows.
    result['issuePathCounts'] = dict(sorted(Counter(row['kind'] + ':' + re.sub(r'\[[^]]+\]', '[]', row['path'])
                                                   for row in report['issues']).items()))
    result['issueManifestSHA256'] = hashlib.sha256(json.dumps(report['issues'], sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'baseline', 'client', 'java-home', 'runtime-jar', 'output', 'summary-output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--evidence', type=Path, default=Path('local/migration72/evidence'))
    parser.add_argument('--repository', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    repository, baseline, source, client, java, jar = (path.resolve() for path in
        (args.repository, args.baseline, args.source, args.client, args.java_home, args.runtime_jar))
    for output in (args.output, args.summary_output):
        if any(output.resolve().is_relative_to(root) for root in (source, baseline, client)):
            parser.error('Reports must be outside resource/client inputs')
    dependencies = verify_sources(repository)
    runtime_hash = sha256(jar)
    if runtime_hash != RUNTIME_SHA256:
        raise ValueError('Review changed offline runtime JAR')
    read = lambda name: json.loads((args.evidence / name).read_text('utf-8-sig'))
    trees = {key: read('resource-source-' + name + '.json') for key, name in (
        ('root', 'tree'), ('bin', 'BinOutput-tree'), ('excel', 'ExcelBinOutput-tree'),
        ('scene', 'Scene-tree'), ('point', 'Scene-Point-tree'))}
    _, revisions = read_client_evidence(client)
    inputs = audit_scene(source, baseline, read('resource-source-commit.json'), trees, revisions, repository)
    commit = subprocess.run(['git', '-C', str(baseline), 'rev-parse', 'HEAD'], check=True, capture_output=True, text=True).stdout.strip()
    if commit != LEGACY_COMMIT:
        raise ValueError('Review changed legacy scripts commit')
    subprocess.run(['git', '-C', str(baseline), 'diff', '--quiet', 'HEAD', '--', 'Scripts/Scene/3', 'Scripts/Common'], check=True)
    entries = verify_tree(trees['excel'])
    blobs = []
    for name in ENTITY_TABLES:
        raw, blob = verify_blob(source, 'ExcelBinOutput/' + name, entries.get(name))
        index_table(raw, 'id')
        blobs.append(blob)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.summary_output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='scene-serialization-', dir=repository / 'local/migration72/analysis') as folder:
        work = Path(folder)
        report = evaluate(repository, baseline / 'Scripts', source, java, jar, repository / 'config.json', args.output.resolve(), work)
    report.update(legacyResourceCommit=commit, targetSource=inputs['upstream'], inputBlobs=blobs,
                  runtimeJarSHA256=runtime_hash, reviewedSources=dependencies,
                  toolSourceSHA256={name: sha256(repository / name, True) for name in (
                      'tools/Client72SceneSerializeAudit.java', 'tools/audit_scene_serialization72.py')})
    for output, document in ((args.output, report), (args.summary_output, summarize(report))):
        output.write_text(json.dumps(document, sort_keys=True, indent=2) + '\n', 'utf8')
    print(json.dumps(dict(counts=report['counts'], issues=len(report['issues']), resourceReady=False), sort_keys=True))


if __name__ == '__main__':
    main()
