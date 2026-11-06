"""Run actual isolated CN72 HTTP requests against the pinned pre-migration archive.

The route may reject admission. Output JSON identifies retained local evidence;
no game server, database, root configuration or client is modified.
"""
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import uuid

root = Path(__file__).resolve().parents[1]
sha = lambda path: hashlib.sha256(Path(path).read_bytes()).hexdigest()
protected = {str(p): sha(p) for p in [root / '.env', root / 'config.json', root / 'resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json']}
jar = root / 'local/migration72/builds/LunaGC-d02-quest-dfdebbda.jar'
assert sha(jar) == 'dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3'
jdk = next((root / 'local/runtime').glob('jdk-17*'))
work = root / 'local/migration72' / ('d02-http-route-main-' + uuid.uuid4().hex)
work.mkdir(); classes = work / 'classes'; classes.mkdir()
for folder in ['resources/BinOutput', 'resources/ExcelBinOutput']:
    (work / folder).mkdir(parents=True)
cfg = dict(version=14, folderStructure={name: str(work / name) for name in ['resources', 'scripts', 'data', 'plugins', 'cache', 'packets']}, server=dict(runMode='HYBRID', http=dict(bindAddress='127.0.0.1', bindPort=0, accessAddress='127.0.0.1', accessPort=0, encryption=dict(useEncryption=False, useInRouting=False)), game=dict(bindPort=22101, accessPort=22101, accessAddress='127.0.0.1', useXorEncryption=True)))
(work / 'config.json').write_text(json.dumps(cfg), encoding='utf8')
(work / 'logback.xml').write_text('<configuration><appender name="console" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%level %logger - %msg%n</pattern></encoder></appender><root level="WARN"><appender-ref ref="console"/></root></configuration>', encoding='utf8')
env = os.environ.copy(); env.pop('LUNAGC_RESOURCES_DIR', None)
compile_result = subprocess.run([str(jdk/'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), str(root/'tools/Client72HttpRouteAudit.java')], capture_output=True, timeout=90)
(work / 'compile.stdout.log').write_bytes(compile_result.stdout); (work / 'compile.stderr.log').write_bytes(compile_result.stderr)

compile_result.check_returncode()
try:
    run = subprocess.run([str(jdk/'bin/java.exe'), '-Dfile.encoding=UTF-8', '-Dlogback.configurationFile='+str(work/'logback.xml'), '-Xlog:class+init=info:file=class-init.log', '-cp', str(classes)+';'+str(jar), 'Client72HttpRouteAudit', str(root)], cwd=work, env=env, capture_output=True, timeout=60)
    (work/'stdout.log').write_bytes(run.stdout); (work/'stderr.log').write_bytes(run.stderr)

    run.check_returncode()
    result = json.loads((work/'result.json').read_text('utf8'))
    with socket.socket() as client:
        assert client.connect_ex(('127.0.0.1', result['httpPort'])) != 0, 'isolated listener still open'
    init = (work/'class-init.log').read_text('utf8', errors='replace')
    assert not any("Initializing '"+name in init for name in ['emu/grasscutter/database/', 'com/mongodb/client/MongoClients', 'emu/grasscutter/server/dispatch/DispatchServer', 'emu/grasscutter/server/game/GameServer', 'emu/grasscutter/scripts/ScriptLoader'])
    print(json.dumps(dict(evidence=str(work), matchedClasses=len(result['matchedClassSHA256']), portReleased=True, serverAdmittedClient=False)), flush=True)
finally:
    assert protected == {name: sha(name) for name in protected}, 'protected inputs changed'
