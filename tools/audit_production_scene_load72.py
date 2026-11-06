"""Run the actual production ScriptLoader/SceneGroup in disposable isolated JVMs.

It does not invoke triggers/actions or install resources/configuration/caches.
The full run has a fail-before-business-call boundary, never a successful API model.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


RUNTIME_SHA256 = '2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe'
# Independently reviewed E-01c6 build; this is a research baseline, not a second
# supported client/runtime. Filled only after the portable artifact is checked.
REQUIRE_BINDINGS_RUNTIME_SHA256 = '7ca18e10c056fd0715e57cdbbb13b4607e324362675a7127b2ed28b5269e8f5f'
REQUIRE_BINDINGS_LOADER_SHA256 = 'e73c9aff3a76093db7e69ff80b4802888c61eceeffcd20a792acab6f73ebf0b9'
LEGACY_COMMIT = 'dac2a9d43332ef51879522cf39da2831b0ca0543'
MANIFEST_SHA256 = '47b783755a7c8f714a868db551f76a503092e0fd7895cc7a787e6060e651c02d'
PARENT_REPORT_SHA256 = 'b99d5deeb475e2dae3269e7946f574d001230e98d9ae18c212b39ad4eada3a7d'
REVIEWED_SOURCES = {
    "src/main/java/emu/grasscutter/Grasscutter.java": "38988046618236e4459d7870bb5b9d6517ae6b0a39f4b2c350044174a18942e8",
    "src/main/java/emu/grasscutter/DebugConstants.java": "9336247c74524049536d03641f09671be3ac94c786a6a1660715911155859c47",
    "src/main/java/emu/grasscutter/config/ConfigContainer.java": "0f9c3d2c43904083856f9673dc51ba49254abafd33f109572c9ba1b1d9b2c41f",
    "src/main/java/emu/grasscutter/config/Configuration.java": "b8db10ea40b195f133be85d18782e4cfbd6a0e44ed0674528cd0a08a4cbcf96e",
    "src/main/java/emu/grasscutter/utils/FileUtils.java": "5da9bb7ba187c0ba49b846a095a446167a142779e0f97b35e168c6b90f43a2d0",
    "src/main/java/emu/grasscutter/utils/Utils.java": "c643243b1b46f5b492d98eb5c03245535112c77c8919efe6f0a3ead851559265",
    "src/main/java/emu/grasscutter/data/DataLoader.java": "beda4e11fd7476906deb3e0024892733c4331f5233e60743f4dd4cfada354835",
    "src/main/java/emu/grasscutter/scripts/ScriptLoader.java": "ff7a09590df7038fa1c9ab48f347ebe8f0845bc67360d967844908b715324cc1",
    "src/main/java/emu/grasscutter/scripts/ScriptLib.java": "3b554d175c4028e0d30bc4727035f5f04d44f76a3d88b562b0d03227b4cfcdf2",
    "src/main/java/emu/grasscutter/scripts/ScriptLibUserdata.java": "691ac41d63b2fcca6833c989293d7f4df7ddd98261d21162badd90e3c1b102b5",
    "src/main/java/emu/grasscutter/scripts/data/SceneGroup.java": "7e899a4457920cdc304a9db55450b8eb20062ab25f127165432abd9f92d44f7e",
    "src/main/java/emu/grasscutter/scripts/data/SceneSuite.java": "825f43f6b8506ff22d2154c1d01772c5f978cccdf86da18e478ac6788e7bccf6",
    "src/main/java/emu/grasscutter/scripts/serializer/LuaSerializer.java": "3f4c17056f0c83d0380345ba785164bec45133e87613380b6c51105cbc23302f",
    "src/main/java/emu/grasscutter/scripts/GroupLuaCall.java": "ab0d0f59ba44ed4ca76e80a7e09e1dec2e43457ea7cf28d0978e6419c29be1d8",
    "src/main/java/emu/grasscutter/scripts/GroupTimerLua.java": "5fbc7ab94fc36d1c64646ded091f897b4f2a43b04c902d966696dd8df82a9b57",
    "src/main/java/emu/grasscutter/scripts/MonsterTideLua.java": "c2def0a3229ed951c66e7ec06769fe4671e6dff9cd7507069686a54f58ad4ee1",
    "src/main/java/emu/grasscutter/scripts/ScenePlayBattleLua.java": "035b10c07d610d5fcdec9a48e9dddc319f7d75e8ae8e483a80c7cdfba0144b34",
    "src/main/java/emu/grasscutter/scripts/TemplateReminderLua.java": "fba2ad78f57dcecf1d16332e80576cdc9a2843a9cb2f63bfaaa0f7e57a97140d",
    "src/main/java/emu/grasscutter/scripts/ScenePlayRewardLua.java": "a62dcd288cdd0ab7e25c1a9ab5e6a9aa3b547fb25dcc5ed4a96e558996479301"
}
FORBIDDEN_INITIALIZATION = re.compile(r"Initializing '(emu/grasscutter/(?:database/[^']+|server/(?:game/GameServer|http/HttpServer|dispatch/DispatchServer)|scripts/SceneScriptManager|data/(?:GameData|ResourceLoader)))'")


def sha256(path, normalize=False):
    raw = path.read_bytes()
    return hashlib.sha256(raw.replace(b'\r\n', b'\n') if normalize else raw).hexdigest()


def verify_inputs(repository, baseline, parent_path, jar, *, runtime_sha256=RUNTIME_SHA256,
                  reviewed_sources=REVIEWED_SOURCES):
    if sha256(jar) != runtime_sha256:
        raise ValueError('Review changed production loader JAR')
    for name, expected in reviewed_sources.items():
        if sha256(repository / name, True) != expected:
            raise ValueError('Review changed production loader dependency: ' + name)
    if sha256(parent_path) != PARENT_REPORT_SHA256:
        raise ValueError('Fixed parent inline report changed')
    parent = json.loads(parent_path.read_text('utf8'))
    encoded = json.dumps(parent['files'], sort_keys=True, separators=(',', ':')).encode()
    if hashlib.sha256(encoded).hexdigest() != MANIFEST_SHA256:
        raise ValueError('Fixed script manifest changed')
    commit = subprocess.run(['git', '-C', str(baseline), 'rev-parse', 'HEAD'], check=True, capture_output=True, text=True).stdout.strip()
    if commit != LEGACY_COMMIT:
        raise ValueError('Legacy resource commit changed')
    subprocess.run(['git', '-C', str(baseline), 'diff', '--quiet', 'HEAD', '--', 'Scripts/Scene/3', 'Scripts/Common'], check=True)
    for name, metadata in parent['files'].items():
        path = (baseline / 'Scripts' / name).resolve()
        if not path.is_relative_to((baseline / 'Scripts').resolve()):
            raise ValueError('Invalid script manifest path')
        if path.stat().st_size != metadata['bytes'] or sha256(path) != metadata['sha256']:
            raise ValueError('Fixed script bytes changed: ' + name)
    return parent


def compile_helper(repository, java, jar, classes):
    subprocess.run([str(java / 'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes),
                    str(repository / 'tools/Client72ProductionSceneLoadAudit.java')], check=True, capture_output=True)


def run_process(java, jar, classes, resources, groups, manifest, fast, guard, work, evidence_prefix):
    work.mkdir(parents=True)
    config = dict(version=14, folderStructure=dict(resources=str(resources), scripts=str(resources / 'Scripts'),
                  data=str(work / 'data'), plugins=str(work / 'plugins'), cache=str(work / 'cache'), packets=str(work / 'packets')),
                  server=dict(fastRequire=fast))
    (work / 'config.json').write_text(json.dumps(config), 'utf8')
    request = work / 'request.json'
    request.write_text(json.dumps(dict(groups=groups, manifest=list(manifest), guardBusinessCalls=guard)), 'utf8')
    output = work / 'result.json'
    run = subprocess.run([str(java / 'bin/java.exe'), '-Xmx2g', '-XX:-OmitStackTraceInFastThrow', '-Dfile.encoding=UTF-8',
                          '-Duser.dir=' + str(work), '-Xlog:class+init=info:file=class-init.log', '-cp',
                          str(classes) + ';' + str(jar), 'Client72ProductionSceneLoadAudit', str(request), str(output)],
                         cwd=work, capture_output=True)
    evidence_prefix.parent.mkdir(parents=True, exist_ok=True)
    for suffix, raw in (('.stdout.log', run.stdout), ('.stderr.log', run.stderr),
                        ('.class-init.log', (work / 'class-init.log').read_bytes())):
        Path(str(evidence_prefix) + suffix).write_bytes(raw)
    if output.exists():
        Path(str(evidence_prefix) + '.raw.json').write_bytes(output.read_bytes())
    run.check_returncode()
    initialized = (work / 'class-init.log').read_text('utf8')
    forbidden = FORBIDDEN_INITIALIZATION.findall(initialized)
    if forbidden:
        raise ValueError('Production infrastructure initialized; stop expansion: ' + ', '.join(sorted(set(forbidden))))
    report = json.loads(output.read_text('utf8'))
    report['forbiddenInfrastructureInitialized'] = []
    if report['fastRequire'] != fast:
        raise ValueError('Configured/static fastRequire mismatch')
    changes = {name: row for name, row in report['effectiveScriptSources'].items() if row != manifest[name]}
    report['effectiveSourceDifferences'] = changes
    return report


def compare_modes(modes):
    fast = {row['id']: row for row in modes['fast']['groups']}
    inline = {row['id']: row for row in modes['inline']['groups']}
    differences = []
    for group in sorted(fast.keys() | inline.keys()):
        a, b = fast.get(group), inline.get(group)
        if a is None or b is None:
            differences.append(dict(group=group, kind='not-processed-in-both-modes'))
            continue
        changed = [key for key in ('status', 'collections', 'suiteBound', 'objectsSHA256', 'bindingsSHA256',
                                   'bindingTypes', 'missingNamedTriggerFunctions') if a[key] != b[key]]
        if changed:
            differences.append(dict(group=group, changed=changed))
    return differences


def summarize(report):
    summary = {key: value for key, value in report.items() if key not in ('modes', 'groupDifferences')}
    summary['modes'] = {}
    for mode, data in report['modes'].items():
        groups = data['groups']
        summary['modes'][mode] = dict(requested=data['requestedGroups'], processed=data['processedGroups'],
            statusCounts=dict(sorted(Counter(row['status'] for row in groups).items())),
            loadedFlagTrue=sum(row['isLoadedFlag'] for row in groups),
            allCollectionsMaterialized=sum(row['allCollectionsMaterialized'] for row in groups),
            errorEvents=sum(len(row['errors']) for row in groups), businessAttempts=data['businessAttempts'],
            missingScripts=data['missingScripts'], effectiveSourceDifferences=data['effectiveSourceDifferences'],
            forbiddenInfrastructureInitialized=data['forbiddenInfrastructureInitialized'], newJavaThreadNames=data['newJavaThreadNames'],
            collections=dict(sorted(sum_counters(row['collections'] for row in groups).items())),
            suiteBound=dict(sorted(sum_counters(row['suiteBound'] for row in groups).items())))
    summary['differentGroups'] = len(report['groupDifferences'])
    summary['differenceKindCounts'] = dict(sorted(Counter(key for row in report['groupDifferences'] for key in row.get('changed', [row.get('kind')])).items()))
    summary['groupDifferencesSHA256'] = hashlib.sha256(json.dumps(report['groupDifferences'], sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    return summary


def sum_counters(rows):
    result = Counter()
    for row in rows:
        result.update(row)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('baseline', 'java-home', 'runtime-jar', 'output', 'summary-output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--repository', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--parent-report', type=Path, default=Path('local/migration72/evidence/scene-grid-inline-audit.json'))
    parser.add_argument('--scope', choices=('probe', 'all-static'), default='probe')
    parser.add_argument('--loader-review', choices=('baseline', 'require-bindings'), default='baseline')
    args = parser.parse_args()
    repository, baseline, java, jar = (path.resolve() for path in (args.repository, args.baseline, args.java_home, args.runtime_jar))
    for output in (args.output, args.summary_output):
        if output.resolve().is_relative_to(baseline):
            parser.error('Output must be outside resource input')
    runtime_sha256 = RUNTIME_SHA256
    reviewed_sources = REVIEWED_SOURCES
    if args.loader_review == 'require-bindings':
        runtime_sha256 = REQUIRE_BINDINGS_RUNTIME_SHA256
        reviewed_sources = {**REVIEWED_SOURCES,
            'src/main/java/emu/grasscutter/scripts/ScriptLoader.java': REQUIRE_BINDINGS_LOADER_SHA256}
    parent = verify_inputs(repository, baseline, args.parent_report, jar,
                          runtime_sha256=runtime_sha256, reviewed_sources=reviewed_sources)
    groups = [row['id'] for row in parent['groupDeclarations'] if not row['dynamicLoad'] and row['evaluated']]
    if args.scope == 'probe':
        groups = [133106026]
    manifest = {name: dict(bytes=row['bytes'], sha256=row['sha256']) for name, row in parent['files'].items()}
    if args.scope == 'probe':
        manifest = {name: manifest[name] for name in ('Scene/3/scene3_group133106026.lua', 'Common/V3_0/CameraLook.lua')}
    modes = {}
    with tempfile.TemporaryDirectory(prefix='scene-production-load-', dir=repository / 'local/migration72/analysis') as folder:
        root = Path(folder); classes = root / 'classes'; classes.mkdir()
        compile_helper(repository, java, jar, classes)
        for mode in ('fast', 'inline'):
            modes[mode] = run_process(java, jar, classes, baseline, groups, manifest, mode == 'fast', args.scope == 'all-static',
                                      root / mode, args.output.resolve().with_name(args.output.stem + '-' + mode))
            if modes[mode]['businessAttempts'] or modes[mode]['effectiveSourceDifferences']:
                raise ValueError('Observed business/source boundary; raw report retained, stop before expansion')
    report = dict(schemaVersion=1, scope=args.scope, loaderReview=args.loader_review,
                  legacyResourceCommit=LEGACY_COMMIT, runtimeJarSHA256=runtime_sha256,
                  parentReportSHA256=PARENT_REPORT_SHA256, inputManifestSHA256=MANIFEST_SHA256, reviewedSources=reviewed_sources,
                  toolSourceSHA256={name: sha256(repository / name, True) for name in (
                      'tools/Client72ProductionSceneLoadAudit.java', 'tools/audit_production_scene_load72.py')},
                  modes=modes, groupDifferences=compare_modes(modes), resourceReady=False, legacySourceIsNative72=False,
                  limitations=['Only actual ScriptLoader.init/getScript/createBindings/eval/SceneGroup.load; no modeled loader',
                               'Independent JVM per fast/inline; fresh in-memory caches and isolated config/data/cache paths',
                               'Fixed manifest is pre-read with production readScript to verify effective sources, not an old disk cache',
                               'Group declarations/filter inherited from fixed parent report; SceneBlock.load/events and getGroupGrids not invoked',
                               'No GameData table loading, trigger/action, entity spawn, save, sockets or client acceptance',
                               'Full scope wraps actual API functions with fail-before-call observation; original-userdata compatibility not claimed',
                               'Missing nonexistent API calls can remain ordinary nil errors; captured production errors are not discarded'])
    for output, data in ((args.output, report), (args.summary_output, summarize(report))):
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(data, indent=2, sort_keys=True) + '\n', 'utf8')
    print(json.dumps(summarize(report)['modes'], sort_keys=True))


if __name__ == '__main__':
    main()
