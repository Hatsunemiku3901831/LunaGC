"""Prepare/run the bounded natural 303 save/new-Player/Manager experiment.

Only the main coordinator runs --run, after supplying fresh owned-Mongo/service proof.
This tool never starts/stops Mongo, drops a database, or starts a game service.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile

try:
    from tools import audit_d02_natural_load_first as first
except ModuleNotFoundError:
    import audit_d02_natural_load_first as first

ROOT = first.ROOT
BASE = ROOT / 'local/migration72/d02-parallel-20261109/r3-quest303-natural-rebind'
JAR = first.JAR
JAR_HASH = first.JAR_HASH
INPUTS = first.INPUTS
DRIVER = ROOT / 'tools/D02Quest303NaturalRebindAudit.java'
FIXTURE = ROOT / 'local/migration72/d02-r3-quest303-replay-873511c485744e10b8dad1d75e481df8/direct-bson.json'
FIXTURE_HASH = '3da7b80960cb2a09d2d1a0ee00ce7659600f855d635f4144ed23ab57aa4deff1'
FIXTURE_SIZE = 17211
URI = 'mongodb://127.0.0.1:27027'
DATABASE_RE = re.compile(r'lunagc_d02_quest303_natural_[0-9a-f]{32}')
COMPILE_SECONDS = 90
RUN_SECONDS = 120
FORBIDDEN = ('emu.grasscutter.server.http.HttpServer',
             'emu.grasscutter.server.dispatch.DispatchServer',
             'emu.grasscutter.server.game.GameServer',
             'emu.grasscutter.plugin.PluginManager', 'emu.grasscutter.command.CommandMap')
ENV_REMOVE = ('LUNAGC_RESOURCES_DIR', 'LUNAGC_MONGO_URI', 'LUNAGC_TEST_MONGO_URI',
              'LUNAGC_TEST_MONGO_SECONDARY_URI', 'LUNAGC_PUBLIC_ADDRESS', 'JAVA_TOOL_OPTIONS',
              'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH')
SOURCE_PINS = {
    'src/main/java/emu/grasscutter/database/DatabaseHelper.java': '5d6507f95429ec02b2fdc825cc4914c968d00d6ceeb8ca10c363249d83490085',
    'src/main/java/emu/grasscutter/database/DatabaseManager.java': 'd3c7ce3a9bac8ef1548904c16032d8f0f26e2229850f9a58005311cb212d9b75',
    'src/main/java/emu/grasscutter/database/DatabaseWriteBarrier.java': '9d5bbdf6050be554c02251be4ce7acdf033100dd8b9459edca92ce25238d89b4',
    'src/main/java/emu/grasscutter/database/DatabaseWriteQueue.java': 'cae53927cbe7a96e9536487d7c944406d12db58aacacd9d7fea631e502523bb5',
    'src/main/java/emu/grasscutter/game/ability/AbilityManager.java': '4b07a55831a4b7fabcc89742a228de73b0a36f88a7e795d45fb57ca4c78c0fa9',
    'src/main/java/emu/grasscutter/game/ability/AbilityRevivalSkillControl.java': 'a89f89fa87de02575474f1ab7d6eb9eec218fd7ee6a889f3754c849e22a03d5e',
    'src/main/java/emu/grasscutter/game/ability/AbilitySharedCooldown.java': '4aa4459ed68b13c7962d27e6fbf05d9e591bbf791da887c14ced053287a99e46',
    'src/main/java/emu/grasscutter/game/ability/AbilitySkillCooldown.java': '6cb840866c286b3f735750af480aeb2edaba6d78f366151765930a01d134b6bb',
    'src/main/java/emu/grasscutter/game/ability/ScarletQuartz.java': '562bfc982b8a6ea6901ca175cd78d3d3dfc966d9330bf92d59ff2137ae7488f5',
    'src/main/java/emu/grasscutter/game/ability/TowerBlessingEffects.java': '70f4b94c407df8ab34c2f02ad67c1bc92849ff9a7337c89248a3bbd03f2658e7',
    'src/main/java/emu/grasscutter/game/avatar/AvatarStorage.java': '6d6b07102d5f8b9f95ebbd4e18d1489f9143d502100dcfaadff0235db138e784',
    'src/main/java/emu/grasscutter/game/friends/FriendsList.java': 'bb0b06074f6c05a56b741fc55fd936af81ab99c69e1fa1957a595b87b20ede68',
    'src/main/java/emu/grasscutter/game/gacha/PlayerGachaBannerInfo.java': '6e82c014208d571f79a31f4a26e6168bc2574e473338478dbf10023409461073',
    'src/main/java/emu/grasscutter/game/gacha/PlayerGachaInfo.java': '975b27b3ecb920e339c6e4eb789c9c632805c8d92fecbad6735f638b50c09ea3',
    'src/main/java/emu/grasscutter/game/inventory/EquipInventoryTab.java': '11531e0b23ab04e6353bfae5bca16e351db7fd462e9d9b030b3fdb9d7bf480bb',
    'src/main/java/emu/grasscutter/game/inventory/Inventory.java': '8c410d0a8a49445440254e80dfca0ec6a7d5d769dcdd4c0f33cca7df5c0cae6e',
    'src/main/java/emu/grasscutter/game/inventory/MaterialInventoryTab.java': 'dbfa515a6c27a421d3a675040842669971875200ee47448e0401b82e3de8c6b5',
    'src/main/java/emu/grasscutter/game/mail/MailHandler.java': '774ab0bb89f26e63e4fa5473852114718c7735bbc13fd80f52b062bc2bf5b60b',
    'src/main/java/emu/grasscutter/game/managers/FurnitureManager.java': 'd963aa09f8d9c290244f8529ada0cba9aa015e2c47f78073dfc5b2e0c1802be3',
    'src/main/java/emu/grasscutter/game/managers/ResinManager.java': '0fe97f4b90072d567776411b3bf19bde54efcd852bd404e46c3a5190aee2d5ab',
    'src/main/java/emu/grasscutter/game/managers/SatiationManager.java': '32bfe91127b82dd43832a1a7bb870ff7cbc45b5b4d25f2e8cc1daea4776bf41c',
    'src/main/java/emu/grasscutter/game/managers/SotSManager.java': '172da283a9cf2a3aa4f0181b34c01fa13fdd1ed986831f85c7aa1ad3b9832455',
    'src/main/java/emu/grasscutter/game/managers/cooking/CookingCompoundManager.java': '14c42d08a68b6d2f9e07ff860ef3b6bbf71a5df45846eb07ed423aa0d7a280d3',
    'src/main/java/emu/grasscutter/game/managers/cooking/CookingManager.java': '3ad56f200dd62605f13f3289083339b70b6e9939c8d125e8cbfa84ec8b170092',
    'src/main/java/emu/grasscutter/game/managers/deforestation/DeforestationManager.java': '80e5495cf1a04bbac51005ac098b7cce1256ebd801decfaac8d0981ca1d4d110',
    'src/main/java/emu/grasscutter/game/managers/energy/EnergyManager.java': '38c0246f65297b7ee68c6f3bbb049f0654b88c4695dea9430f623535afb1b33f',
    'src/main/java/emu/grasscutter/game/managers/forging/ForgingManager.java': '4f67abc9a1d405f4073b28abbae41511d88b082c303b9ab7ddb3d6bec027b6ca',
    'src/main/java/emu/grasscutter/game/managers/mapmark/MapMarksManager.java': 'e77b7dd84deaf8c87b34d529d9da5d2cffd04dfaec94bec1f242638ff0785728',
    'src/main/java/emu/grasscutter/game/managers/stamina/StaminaManager.java': 'f102cee2cc545ed52dbf2ad37b4e84344cdaddf60c9007b3b8370576fb077333',
    'src/main/java/emu/grasscutter/game/player/BasePlayerManager.java': 'c66a53554e02103047db2f6467eb3daf593d06e4eaee5513c98b1812a9f0184a',
    'src/main/java/emu/grasscutter/game/player/InvokeHandler.java': '805c3db228286e7de03884b0bbde4cc01bcaefac1cc8deb234ef28299fa49f5e',
    'src/main/java/emu/grasscutter/game/player/Player.java': '7024c88ea996f3b5864d3badbb90ce742faa382153ee27ac5d0b3dc9ecf3e2c4',
    'src/main/java/emu/grasscutter/game/player/PlayerBirthday.java': '411f751fc4704ef40e6396e3a65028a589bb639d9871d7aa569d60edc9321a2d',
    'src/main/java/emu/grasscutter/game/player/PlayerBuffManager.java': '7a78f24ec2ebfaab58a426f28fa02a54a1ab426a5fcb4ce5011bdec69440c064',
    'src/main/java/emu/grasscutter/game/player/PlayerCodex.java': '2d7f803b058c119d704124464b412f139623b3228279d1ced566efe4e0eddab9',
    'src/main/java/emu/grasscutter/game/player/PlayerCollectionRecords.java': 'b4fbee1299e4bd42aadae8584b0f6e662bc58106c1c11493440168d6348df614',
    'src/main/java/emu/grasscutter/game/player/PlayerProgress.java': 'fb4dce050e218702ba410d86925e20f690122aecae0a25d7814c8df6c730d8ef',
    'src/main/java/emu/grasscutter/game/player/PlayerProgressManager.java': '9026574e73aac4d640a74c71da8577121bd7b797a7d3e05706f86f44ad706568',
    'src/main/java/emu/grasscutter/game/quest/GameMainQuest.java': '7e2233282cda70a249807b92329c8da454251cc6d2de68d9047104bba5816c2d',
    'src/main/java/emu/grasscutter/game/quest/GameQuest.java': '046158700edf0899b7025530c7f4996f7790f29857d12af00ca831370343021b',
    'src/main/java/emu/grasscutter/game/quest/QuestContentProgress.java': '88b3aea353aac6e3ce52e8eb0f96cb32fc3455f3d9f2bf61688094e126ea3586',
    'src/main/java/emu/grasscutter/game/quest/QuestManager.java': 'a3400f3cbf3bdbb9f15c2df6de21259c501497024f86b4362bdcb50b861ee49e',
    'src/main/java/emu/grasscutter/game/quest/QuestProgressBinding.java': 'a84cc1f59471ee5201684265ef01a39b572cb145030a094c0ce3d8ef77c2be1d',
    'src/main/java/emu/grasscutter/game/talk/TalkManager.java': 'fb974c07b171c0e0130633337e9247d60cdcf324ce576050fe21f5ab9dd02c00',
    'src/main/java/emu/grasscutter/game/tower/TowerData.java': '69934b09f6a192e81cf6a889771daf9788774433b411f16cd8734c0c48246181',
    'src/main/java/emu/grasscutter/game/tower/TowerManager.java': '28531fab5957ce5e70d641ce3966f5b5caab88ce299d5a401f5b440ea678c033',
}

sha = first.sha
check = first.check
inventory = first.inventory
protected = first.protected


def owned_database(uri, database):
    check(uri == URI, 'Mongo URI must be the explicit owned localhost:27027 endpoint')
    check(DATABASE_RE.fullmatch(database) is not None, 'unique natural-303 database name required')


def ordinary(path):
    check(path.is_file() and not path.is_symlink()
          and not getattr(path, 'is_junction', lambda: False)()
          and path.stat().st_nlink == 1, 'ordinary independent file required: ' + str(path))


def fixture_shape(document):
    check(isinstance(document, dict) and document.get('ownerUid') == 210001
          and document.get('parentQuestId') == 303, 'owned fixture identity mismatch')
    check(isinstance(document.get('_id'), dict)
          and re.fullmatch('[0-9a-f]{24}', document['_id'].get('$oid', '')) is not None,
          'owned fixture ObjectId mismatch')
    children = document.get('childQuests')
    check(isinstance(children, dict) and len(children) == 63, 'owned fixture child count mismatch')
    for key, child in children.items():
        check(key.isdecimal() and isinstance(child, dict) and child.get('subQuestId') == int(key)
              and child.get('mainQuestId') == 303, 'owned fixture child identity mismatch: ' + str(key))
        expected = 'QUEST_STATE_UNFINISHED' if key == '30302' else 'QUEST_STATE_UNSTARTED'
        check(child.get('state') == expected and child.get('finishProgressList') == [0],
              'owned fixture state/progress mismatch: ' + key)
        check('failConditionKeys' not in child, 'fixture metadata changed: autosave must be reassessed')
    check('30302' in children and document.get('questVars') == [0] * 5
          and document.get('timeVar') == [-1] * 10
          and document.get('state') == 'PARENT_QUEST_STATE_NONE'
          and document.get('isFinished') is False, 'owned fixture parent state mismatch')
    return set(map(int, children))


def verify():
    baseline = first.verify()  # Fixed JAR, original 31 sources/96 class, 3 inputs, historical domains.
    check(len(SOURCE_PINS) >= 40, 'constructor/save source pins are not frozen')
    extra_sources, families = [], []
    with zipfile.ZipFile(JAR) as archive:
        names = set(archive.namelist())
        for relative, expected in SOURCE_PINS.items():
            source = ROOT / relative
            check(source.is_file() and sha(source) == expected, 'restore source drift: ' + relative)
            extra_sources.append({'path': relative, 'sha256': expected})
            family = relative.removeprefix('src/main/java/').removesuffix('.java')
            entries = sorted(n for n in names if n == family + '.class'
                             or n.startswith(family + '$') and n.endswith('.class'))
            check(entries, 'restore class family absent: ' + family)
            rows = []
            for name in entries:
                content = archive.read(name)
                check(content == (ROOT / 'build/classes/java/main' / name).read_bytes(),
                      'restore class differs from current compiled output: ' + name)
                rows.append({'entry': name, 'sha256': hashlib.sha256(content).hexdigest()})
            families.append({'class_family': family, 'entries': rows})
    ordinary(FIXTURE)
    check(FIXTURE.stat().st_size == FIXTURE_SIZE and sha(FIXTURE) == FIXTURE_HASH, 'owned BSON fixture drift')
    document = json.loads(FIXTURE.read_text(encoding='utf-8'))
    ids = fixture_shape(document)
    raw_parent = json.loads((ROOT / INPUTS[2][0]).read_text(encoding='utf-8'))
    raw_ids = [child['subId'] for child in raw_parent['subQuests']]
    check(raw_parent['id'] == 303 and len(raw_ids) == 63 and len(set(raw_ids)) == 63
          and set(raw_ids) == ids, 'raw parent and fixture children do not match')
    return {'baseline': baseline, 'restore_sources': extra_sources, 'restore_class_families': families,
            'fixture_sha256': FIXTURE_HASH, 'fixture_bytes': FIXTURE_SIZE,
            'driver_sha256': sha(DRIVER), 'runner_sha256': sha(Path(__file__)),
            'fixture_child_ids': sorted(ids)}


def prepare(uri, database):
    owned_database(uri, database)
    pins = verify()
    BASE.mkdir(parents=True, exist_ok=True)
    run = BASE / ('run-' + time.strftime('%Y%m%d-%H%M%S') + '-' + os.urandom(4).hex())
    run.mkdir()
    work = run / 'work'
    resources = work / 'resources'
    for directory in ('resources/ExcelBinOutput', 'resources/BinOutput',
                      'resources/Scripts/Quest/Share', 'resources/Scripts/Gadget', 'resources/Scripts/Scene',
                      'data', 'plugins', 'cache', 'packets', 'temp', 'src/main/resources'):
        (work / directory).mkdir(parents=True, exist_ok=True)
    for directory in ('classes', 'mongo-data', 'source-at-prepare'):
        (run / directory).mkdir()
    for source, destination, digest in INPUTS:
        target = resources / destination
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / source, target)
        ordinary(target)
        check(sha(target) == digest, 'candidate copy mismatch: ' + destination)
    shutil.copyfile(FIXTURE, run / 'owned-direct-bson.json')
    for path in (DRIVER, Path(__file__)):
        shutil.copyfile(path, run / 'source-at-prepare' / path.name)
    config = {
        'version': 14,
        'folderStructure': {'resources': str(resources), 'scripts': 'resources:Scripts/',
                            **{name: str(work / name) for name in ('data', 'plugins', 'cache', 'packets')}},
        'language': {'language': 'en_US', 'fallback': 'en_US', 'document': 'EN'},
        'server': {'fastRequire': True, 'http': {'startImmediately': False},
                   'game': {'enableConsole': False}},
        'databaseInfo': {'game': {'connectionUri': uri, 'collection': database},
                         'server': {'connectionUri': uri, 'collection': database},
                         'asyncWrites': {'shutdownTimeoutSeconds': 20}},
    }
    (work / 'config.json').write_text(json.dumps(config, indent=2) + '\n', encoding='utf-8')
    for path in (work / 'logback.xml', work / 'src/main/resources/logback.xml'):
        path.write_text(first.LOG, encoding='utf-8')
    manifest = {
        'pins': pins, 'run': str(run), 'mongo_uri': uri, 'database': database,
        'mongo_dbpath': str(run / 'mongo-data'), 'mongo_log': str(run / 'mongo.private.log'),
        'work_before': inventory(work), 'protected_before': protected(),
        'fixture_copy_sha256': sha(run / 'owned-direct-bson.json'),
        'source_snapshot': inventory(run / 'source-at-prepare'),
        'candidate_inputs': INPUTS, 'candidate_commit': 'd54f6f0d423ded9d055bb740a3e5f3aa8f2667f1',
        'budgets': {'compile_seconds': COMPILE_SECONDS, 'runtime_seconds': RUN_SECONDS, 'save_wait_seconds': 20},
        'scope': 'only local303 restore; natural single loadAll; no start/login/world/service; retained UUID DB',
        'scripts': 'R/Scripts empty in three direct domains; fixed JAR historical direct domains absent',
    }
    (run / 'input-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    return run


def resolve_run(path):
    run = path.resolve()
    check(run.parent == BASE.resolve() and re.fullmatch(r'run-\d{8}-\d{6}-[0-9a-f]{8}', run.name),
          'run outside designated independent root')
    check(not run.is_symlink() and not getattr(run, 'is_junction', lambda: False)(), 'linked run rejected')
    return run


def validate_preflight(path, manifest):
    ordinary(path)
    check(path.parent.resolve() == Path(manifest['run']).resolve()
          and path.name == 'main-preflight.private.json', 'preflight outside designated run')
    proof = json.loads(path.read_text(encoding='utf-8-sig'))
    check(Path(proof['run']).resolve() == Path(manifest['run']).resolve(), 'preflight run mismatch')
    check(proof['mongo_uri'] == manifest['mongo_uri'] and proof['database'] == manifest['database'], 'preflight database mismatch')
    check(Path(proof['mongo_dbpath']).resolve() == Path(manifest['mongo_dbpath']).resolve(), 'preflight dbpath mismatch')
    check(isinstance(proof.get('mongo_pid'), int) and proof['mongo_pid'] > 0
          and proof.get('mongo_27027_pid') == proof['mongo_pid']
          and proof.get('mongo_identity_verified') is True, 'owned Mongo PID/port/command identity not verified')
    check(proof.get('project_game_http_dispatch_plugins_absent') is True
          and proof.get('http_8088_listeners') == [] and proof.get('game_22101_listeners') == [],
          'main coordinator has not verified service absence')
    observed = dt.datetime.fromisoformat(proof['observed_utc'].replace('Z', '+00:00'))
    check(observed.tzinfo is not None, 'preflight timestamp must include timezone')
    age = (dt.datetime.now(dt.timezone.utc) - observed).total_seconds()
    check(0 <= age <= 300, 'preflight is not fresh (five-minute window)')
    return {'path': str(path), 'sha256': sha(path), 'proof': proof}


def forbidden_lines(content):
    hits = []
    for line in content.splitlines():
        match = re.search(r"\[class,init\]\s+\d+\s+Initializing '([^']+)'", line)
        if match and any(match.group(1).replace('/', '.').startswith(prefix) for prefix in FORBIDDEN):
            hits.append(line)
    return hits


def diagnostics_summary(driver):
    diagnostics = driver.get('diagnostics', [])
    phases = {}
    for row in diagnostics:
        counts = phases.setdefault(row['phase'], {'WARN': 0, 'ERROR': 0})
        counts[row['level']] = counts.get(row['level'], 0) + 1
    return {'by_phase': phases, 'all_count': len(diagnostics),
            'quest_excel_rejected': driver.get('questExcelRejected'),
            'bounded_input_omissions_are_not_original_resources_failures': True,
            'global_resource_ready': False}


def run_ok(result, driver):
    return (result.get('status') == 'EXITED' and result.get('compile_exit') == 0 and result.get('exit') == 0
            and not result.get('forbidden_class_init') and not result.get('protected_changed')
            and not result.get('postcheck_error')
            and first.work_changes_ok(result.get('changed_work', {}), result.get('removed_work', []))
            and isinstance(driver, dict) and driver.get('outcome') == 'PASSED_LOCAL_303_REBIND'
            and driver.get('loadAllCalls') == 1 and driver.get('initialLoadedAll') is False
            and driver.get('loadedAll') is True and driver.get('loadAllReturned') is True
            and driver.get('localNecessaryResources') is True and driver.get('globalResourceReady') is False
            and driver.get('unreadyGatePassed') is True and driver.get('restoredChildren') == 63
            and driver.get('newObjectBindingsPassed') is True and driver.get('packetsAtBoundary') == 0
            and driver.get('explicitSaveProjectionPreserved') is True
            and driver.get('managerRestoreProjectionPreserved') is True
            and driver.get('databaseRetained') is True and driver.get('mongoClientClosed') is True
            and not driver.get('cleanupFailure'))


def execute(run, preflight_path):
    check(not any((run / 'classes').iterdir())
          and not any((run / name).exists() for name in ('result.json', 'compile.stdout.log', 'compile.stderr.log',
                                                       'driver-result.json', 'stdout.log', 'stderr.log', 'class-init.log')),
          'run already used; retain it and prepare another run')
    result = {'status': 'PRECHECK_FAILED', 'success': False}
    manifest = None
    work = run / 'work'
    try:
        manifest = json.loads((run / 'input-manifest.json').read_text(encoding='utf-8'))
        result['input_manifest_sha256'] = sha(run / 'input-manifest.json')
        check(Path(manifest['run']).resolve() == run, 'manifest run mismatch')
        owned_database(manifest['mongo_uri'], manifest['database'])
        check(verify() == manifest['pins'], 'restore manifest pin drift')
        ordinary(run / 'owned-direct-bson.json')
        check(sha(run / 'owned-direct-bson.json') == FIXTURE_HASH, 'fixture copy drift')
        check(inventory(run / 'source-at-prepare') == manifest['source_snapshot'], 'source snapshot drift')
        check(protected() == manifest['protected_before'], 'original protected input drift')
        check(inventory(work) == manifest['work_before'], 'independent work input drift')
        result['preflight'] = validate_preflight(preflight_path.resolve(), manifest)
        env = os.environ.copy()
        result['environment_removed_presence'] = {key: key in env for key in ENV_REMOVE}
        for key in ENV_REMOVE:
            env.pop(key, None)
        env.update(LUNAGC_RUNTIME='portable', TEMP=str(work / 'temp'), TMP=str(work / 'temp'))
        jdk = Path(manifest['pins']['baseline']['jdk'])
        classes = run / 'classes'
        command = [str(jdk / 'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(JAR),
                   '-d', str(classes), str(DRIVER)]
        result['compile_command'] = command
        result['status'] = 'COMPILE_ERROR'
        compiled = subprocess.run(command, cwd=work, env=env, capture_output=True, timeout=COMPILE_SECONDS)
        (run / 'compile.stdout.log').write_bytes(compiled.stdout)
        (run / 'compile.stderr.log').write_bytes(compiled.stderr)
        result['compile_exit'] = compiled.returncode
        if compiled.returncode:
            result['status'] = 'COMPILE_FAILED'
        else:
            result['driver_classes'] = inventory(classes)
            command = [str(jdk / 'bin/java.exe'), '-Xms64m', '-Xmx1024m', '-Dfile.encoding=UTF-8',
                       '-Djava.io.tmpdir=' + str(work / 'temp'),
                       '-Dlogback.configurationFile=' + str(work / 'logback.xml'),
                       '-Xlog:class+init=info:file=../class-init.log', '-cp',
                       str(classes) + os.pathsep + str(JAR), 'D02Quest303NaturalRebindAudit',
                       str(run / 'driver-result.json'), str(run / 'owned-direct-bson.json'),
                       manifest['work_before']['config.json'], manifest['mongo_uri'], manifest['database']]
            result['command'] = command
            result['status'] = 'RUN_ERROR'
            with (run / 'stdout.log').open('wb') as stdout, (run / 'stderr.log').open('wb') as stderr:
                process = subprocess.Popen(command, cwd=work, env=env, stdout=stdout, stderr=stderr)
                result['pid'] = process.pid
                started = time.monotonic()
                while process.poll() is None:
                    path = run / 'class-init.log'
                    hits = forbidden_lines(path.read_text(encoding='utf-8', errors='replace') if path.exists() else '')
                    if hits:
                        result['forbidden_class_init'] = hits
                        result['status'] = 'FORBIDDEN_CLASS_INIT'
                        process.kill()  # This runner's sole owned JVM, never an unrelated process.
                        break
                    if time.monotonic() - started > RUN_SECONDS:
                        result['status'] = 'TIMEOUT'
                        process.kill()
                        break
                    time.sleep(.1)
                result['exit'] = process.wait()
                result['runtime_seconds'] = time.monotonic() - started
                if result['status'] == 'RUN_ERROR':
                    result['status'] = 'EXITED'
            path = run / 'class-init.log'
            result['forbidden_class_init'] = forbidden_lines(path.read_text(encoding='utf-8', errors='replace') if path.exists() else '')
            if result['forbidden_class_init']:
                result['status'] = 'FORBIDDEN_CLASS_INIT'
    except subprocess.TimeoutExpired as failure:
        result['status'] = 'COMPILE_TIMEOUT'
        result['error'] = str(failure)
        (run / 'compile.stdout.log').write_bytes(failure.stdout or b'')
        (run / 'compile.stderr.log').write_bytes(failure.stderr or b'')
    except Exception as failure:
        result['error'] = repr(failure)
    finally:
        if manifest is not None:
            try:
                result['protected_after'] = protected()
                result['protected_changed'] = {key: [before, result['protected_after'].get(key)]
                                               for key, before in manifest['protected_before'].items()
                                               if before != result['protected_after'].get(key)}
                result['work_after'] = inventory(work)
                result['changed_work'] = {key: [manifest['work_before'].get(key), after]
                                          for key, after in result['work_after'].items()
                                          if manifest['work_before'].get(key) != after}
                result['removed_work'] = [key for key in manifest['work_before'] if key not in result['work_after']]
                check(sha(run / 'owned-direct-bson.json') == FIXTURE_HASH, 'owned fixture changed during run')
                check(inventory(run / 'source-at-prepare') == manifest['source_snapshot'], 'source snapshot changed during run')
            except Exception as failure:
                result['postcheck_error'] = repr(failure)
                result['status'] = 'POSTCHECK_FAILED'
        driver = None
        try:
            driver = json.loads((run / 'driver-result.json').read_text(encoding='utf-8'))
        except (OSError, ValueError):
            pass
        result['driver_outcome'] = driver.get('outcome') if isinstance(driver, dict) else None
        result['diagnostic_summary'] = diagnostics_summary(driver) if isinstance(driver, dict) else None
        result['success'] = run_ok(result, driver)
        result['global_resource_ready'] = False
        result['database_cleanup'] = 'No drop/shutdown performed. Main coordinator must normally stop its verified Mongo; retain dbpath.'
        (run / 'result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--prepare-only', action='store_true')
    group.add_argument('--run', type=Path)
    parser.add_argument('--mongo-uri')
    parser.add_argument('--database')
    parser.add_argument('--preflight', type=Path)
    args = parser.parse_args()
    if args.prepare_only:
        check(args.mongo_uri is not None and args.database is not None and args.preflight is None,
              'prepare needs explicit --mongo-uri and --database only')
        print(prepare(args.mongo_uri, args.database))
        return 0
    check(args.mongo_uri is None and args.database is None and args.preflight is not None,
          'run uses fixed manifest database and requires main-owned --preflight')
    result = execute(resolve_run(args.run), args.preflight)
    print(result['status'])
    return 0 if result['success'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
