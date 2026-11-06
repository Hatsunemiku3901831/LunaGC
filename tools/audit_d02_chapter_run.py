import hashlib, json, os, subprocess, uuid, urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[1]
base = root/'local/migration72/offline-sol-terra/chapter-audit'/('run-'+uuid.uuid4().hex)
base.mkdir(parents=True)
jar = root/'local/migration72/builds/LunaGC-d02-quest-dfdebbda.jar'
input_file = base/'ChapterExcelConfigData.json'
fixed='d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'
blob='8f8f8fa23df84686883d931f248e6ff0b626ddd7'
sha='6c291ee3b5ca1e29b1b4395c1e13103d1965e1ce687eb3e0364e08dfcc55f164'
cached=root/'local/migration72/offline-sol-terra/chapter-audit/ChapterExcelConfigData.json'
git_repo=os.environ.get('D02_CANDIDATE_GIT')
if git_repo:
    tree=subprocess.run(['git','-C',git_repo,'rev-parse',fixed+'^{tree}'],capture_output=True,check=True,timeout=20).stdout.decode().strip()
    assert tree=='5b334e711319726c504da95dbfe6ebb26baaa7d3'
    actual=subprocess.run(['git','-C',git_repo,'rev-parse',fixed+':ExcelBinOutput/ChapterExcelConfigData.json'],capture_output=True,check=True,timeout=20).stdout.decode().strip()
    assert actual==blob
    content=subprocess.run(['git','-C',git_repo,'show',fixed+':ExcelBinOutput/ChapterExcelConfigData.json'],capture_output=True,check=True,timeout=20).stdout
    origin='local git fixed commit'
elif cached.is_file():
    content=cached.read_bytes(); origin='fixed cached copy'
else:
    url='https://raw.githubusercontent.com/DimbreathBot/AnimeGameData/'+fixed+'/ExcelBinOutput/ChapterExcelConfigData.json'
    with urllib.request.urlopen(url,timeout=30) as response: content=response.read()
    origin='fixed commit URL'
assert hashlib.sha256(content).hexdigest()==sha
assert hashlib.sha1(b'blob '+str(len(content)).encode()+b'\0'+content).hexdigest()==blob
input_file.write_bytes(content); (base/'input-origin.txt').write_text(origin+'\n',encoding='utf-8')
jdk = next((root/'local/runtime').glob('jdk-17*'))
classes = base/'classes'; classes.mkdir()
def run(command, cwd, label, env=None):
    try:
        p = subprocess.run(command, cwd=cwd, env=env, capture_output=True, timeout=90)
    except subprocess.TimeoutExpired as e:
        (base/(label+'.timeout')).write_text(str(e), encoding='utf-8')
        raise
    (base/(label+'.stdout')).write_bytes(p.stdout); (base/(label+'.stderr')).write_bytes(p.stderr)
    p.check_returncode()
run([str(jdk/'bin/javac.exe'),'-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(root/'tools/D02ChapterAudit.java')],root,'compile')
env=os.environ.copy(); env.pop('LUNAGC_RESOURCES_DIR',None)
for label in ['first','repeat']:
    work=base/label; work.mkdir()
    for name in ['resources/BinOutput','resources/ExcelBinOutput','scripts','data','plugins','cache','packets']: (work/name).mkdir(parents=True)
    cfg={'version':14,'folderStructure':{k:str(work/k) for k in ['resources','scripts','data','plugins','cache','packets']}}
    (work/'config.json').write_text(json.dumps(cfg),encoding='utf-8')
    logback=work/'logback.xml'; logback.write_text('<configuration><appender name="console" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%level %logger - %msg%n</pattern></encoder></appender><root level="WARN"><appender-ref ref="console"/></root></configuration>',encoding='utf-8')
    run([str(jdk/'bin/java.exe'),'-Dlogback.configurationFile='+str(logback),'-Dfile.encoding=UTF-8','-Xlog:class+init=info:file=class-init.log','-cp',str(classes)+';'+str(jar),'D02ChapterAudit',str(root),str(input_file),str(work/'result.json')],work,label,env)
    init=(work/'class-init.log').read_text(encoding='utf-8',errors='replace')
    forbidden=['emu/grasscutter/database/','com/mongodb/client/MongoClients','emu/grasscutter/server/http/HttpServer','emu/grasscutter/server/dispatch/DispatchServer','emu/grasscutter/server/game/GameServer','emu/grasscutter/scripts/ScriptLoader']
    assert not any("Initializing '"+x in init for x in forbidden)
assert (base/'first/result.json').read_bytes()==(base/'repeat/result.json').read_bytes()
print('result sha256',hashlib.sha256((base/'first/result.json').read_bytes()).hexdigest(), 'evidence',base)
