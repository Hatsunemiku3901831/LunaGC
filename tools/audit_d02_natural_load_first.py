import argparse, hashlib, json, os, re, shutil, subprocess, time, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'local/migration72/d02-parallel-20261109/c-natural-load'
AUDIT = ROOT / 'local/migration72/d02-resume-20261109/r3-natural-load-audit/source-input-class-audit.private.json'
PROOF = ROOT / 'local/migration72/d02-resume-20261108/build/artifact-proof.json'
JAR = ROOT / 'LunaGC-7.1.0.jar'
JAR_HASH = '8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291'
AUDIT_HASH = 'f5fc932b0ea4501625a5a7db526a5ce63d25885bf5dfb0cdaa8c86899e6259cb'
INPUTS = [('local/migration72/offline-sol-terra/chapter-audit/ChapterExcelConfigData.json','ExcelBinOutput/ChapterExcelConfigData.json','6c291ee3b5ca1e29b1b4395c1e13103d1965e1ce687eb3e0364e08dfcc55f164'),('local/migration72/d02-r3-candidate/ExcelBinOutput/QuestExcelConfigData.json','ExcelBinOutput/QuestExcelConfigData.json','c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87'),('local/migration72/d02-r3-candidate/BinOutput/Quest/303.json','BinOutput/Quest/303.json','aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca')]
LOG = '''<configuration><appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%date %-5level [%thread] %logger - %msg%n%ex</pattern></encoder></appender><root level="INFO"><appender-ref ref="CONSOLE"/></root></configuration>\n'''
FORBIDDEN = ('emu.grasscutter.database.', 'com.mongodb.client.MongoClients', 'emu.grasscutter.server.http.HttpServer', 'emu.grasscutter.server.dispatch.DispatchServer', 'emu.grasscutter.server.game.GameServer', 'emu.grasscutter.plugin.PluginManager', 'emu.grasscutter.command.CommandMap')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def check(ok, message):
    if not ok: raise ValueError(message)
def protected():
    rows=json.loads((BASE.parent/'initial-inventory.private.json').read_text(encoding='utf-8-sig'))['protected']
    result={}
    for row in rows:
        p=Path(row['path']); p=p if p.is_absolute() else ROOT/p
        result[str(p)] = sha(p) if p.is_file() else None
    return result
def forbidden_lines(content):
    hits=[]
    for line in content.splitlines():
        match=re.search(r"\[class,init\]\s+\d+\s+Initializing '([^']+)'", line)
        if match:
            name=match.group(1).replace('/', '.')
            if any(name.startswith(s) for s in FORBIDDEN): hits.append(line)
    return hits
def inventory(p):
    result={}
    for f in p.rglob('*'):
        check(not f.is_symlink() and not getattr(f, 'is_junction', lambda: False)(), 'link in work: '+str(f))
        result[str(f.relative_to(p))] = sha(f) if f.is_file() else '<directory>'
    return result
def verify():
    check(sha(AUDIT)==AUDIT_HASH, 'audit pin drift'); check(sha(JAR)==JAR_HASH,'JAR drift')
    audit=json.loads(AUDIT.read_text(encoding='utf-8-sig')); proof=json.loads(PROOF.read_text(encoding='utf-8'))
    check(proof['codeCommit']=='496e96f' and proof['jarSHA256']==JAR_HASH and proof['allChangedMatchCompiled'], 'build proof drift')
    for row in audit['sources']:
        p=ROOT / row['path']; check(p.is_file() and sha(p)==row['sha256'], 'source drift: '+str(p))
    archive=next(a for a in audit['archives'] if a['path']=='LunaGC-7.1.0.jar')
    count=0
    with zipfile.ZipFile(JAR) as z:
        names=set(z.namelist())
        for family in archive['production_class_families']:
            prefix=family['class_family']; expected={r['entry'] for r in family['entries']}; actual={n for n in names if n==prefix+'.class' or n.startswith(prefix+'$') and n.endswith('.class')}
            check(actual==expected,'class family drift: '+prefix)
            for row in family['entries']:
                data=z.read(row['entry']); check(hashlib.sha256(data).hexdigest()==row['sha256'] and data==(ROOT/'build/classes/java/main'/row['entry']).read_bytes(),'class drift: '+row['entry']); count+=1
        for n in names:
            check(not (n.startswith('historical-scripts/Quest/Share/') or n.startswith('historical-scripts/Gadget/') or n=='historical-scripts/Scene/groups_replacement.lua'), 'historical script: '+n)
    for src,_,digest in INPUTS:
        path=ROOT/src; check(path.is_file() and not path.is_symlink() and path.stat().st_nlink==1 and sha(path)==digest,'input drift or link: '+src)
    jdks=list((ROOT/'local/runtime').glob('jdk-17*')); check(len(jdks)==1 and jdks[0].name=='jdk-17.0.20.1+1','JDK ambiguity')
    jdk=jdks[0]; release=(jdk/'release').read_text(); check('JAVA_RUNTIME_VERSION="17.0.20.1+1"' in release and 'IMPLEMENTOR="Eclipse Adoptium"' in release,'JDK identity')
    return {'audit_sha256':sha(AUDIT),'proof_sha256':sha(PROOF),'jar_sha256':sha(JAR),'source_count':len(audit['sources']),'class_family_count':len(archive['production_class_families']),'class_count':count,'jdk':str(jdk),'jdk_release_sha256':sha(jdk/'release'),'java_sha256':sha(jdk/'bin/java.exe'),'javac_sha256':sha(jdk/'bin/javac.exe'),'driver_sha256':sha(ROOT/'tools/D02NaturalLoadFirstAudit.java'),'runner_sha256':sha(Path(__file__))}
def prepare():
    pins=verify(); BASE.mkdir(parents=True,exist_ok=True)
    run=BASE / ('run-'+time.strftime('%Y%m%d-%H%M%S')+'-'+os.urandom(4).hex()); run.mkdir()
    work=run/'work'; r=work/'resources'
    for d in ['resources/ExcelBinOutput','resources/BinOutput','resources/Scripts/Quest/Share','resources/Scripts/Gadget','resources/Scripts/Scene','data','plugins','cache','packets','temp','src/main/resources']:(work/d).mkdir(parents=True,exist_ok=True)
    (run/'classes').mkdir()
    for src,dst,_ in INPUTS:
        target=r/dst; target.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(ROOT/src,target); check(not target.is_symlink() and target.stat().st_nlink==1 and sha(target)==sha(ROOT/src),'copy mismatch: '+dst)
    config={'version':14,'folderStructure':{'resources':str(r),'scripts':'resources:Scripts/','data':str(work/'data'),'plugins':str(work/'plugins'),'cache':str(work/'cache'),'packets':str(work/'packets')},'language':{'language':'en_US','fallback':'en_US','document':'EN'},'server':{'fastRequire':True,'http':{'startImmediately':False},'game':{'enableConsole':False}}}
    (work/'config.json').write_text(json.dumps(config,indent=2)+'\n',encoding='utf-8'); (work/'logback.xml').write_text(LOG,encoding='utf-8'); (work/'src/main/resources/logback.xml').write_text(LOG,encoding='utf-8')
    pins.update({'run':str(run),'inputs':INPUTS,'work_before':inventory(work),'protected_before':protected(),'scripts':'empty; historical direct domains absent','server_and_script_scene_data':'absent','candidate_commit':'d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'})
    (run/'input-manifest.json').write_text(json.dumps(pins,indent=2)+'\n',encoding='utf-8'); return run

def work_changes_ok(changes, removed):
    return not removed and all(path.replace('\\', '/').startswith('temp/') and before is None
                               for path, (before, after) in changes.items())

def run_ok(result, driver):
    return (result.get('status')=='EXITED' and result.get('compile_exit')==0 and result.get('exit')==0
            and not result.get('forbidden_class_init') and not result.get('protected_changed')
            and work_changes_ok(result.get('changed_work', {}), result.get('removed_work', []))
            and isinstance(driver,dict) and driver.get('outcome')=='RETURNED'
            and not driver.get('observationFailure'))

def execute(run):
    check(not any((run/'classes').iterdir()) and not any((run/name).exists() for name in ('result.json','compile.stdout.log','compile.stderr.log','driver-result.json','stdout.log','stderr.log','class-init.log')), 'run already used')
    result={'status':'PRECHECK_FAILED'}; work=run/'work'; manifest=None
    try:
        manifest=json.loads((run/'input-manifest.json').read_text(encoding='utf-8')); pins=verify(); check(pins=={k:manifest[k] for k in pins},'pin drift'); check(protected()==manifest['protected_before'],'protected input drift'); check(inventory(work)==manifest['work_before'],'work input drift')
        env=os.environ.copy(); removed={k:k in env for k in ('LUNAGC_RESOURCES_DIR','LUNAGC_MONGO_URI','LUNAGC_PUBLIC_ADDRESS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')}
        for k in removed: env.pop(k,None)
        env.update(LUNAGC_RUNTIME='portable',TEMP=str(work/'temp'),TMP=str(work/'temp'))
        jdk=Path(manifest['jdk']); source=ROOT/'tools/D02NaturalLoadFirstAudit.java'; classes=run/'classes'; result['environment_removed_presence']=removed
        compile_cmd=[str(jdk/'bin/javac.exe'),'-encoding','UTF-8','-cp',str(JAR),'-d',str(classes),str(source)]; result['compile_command']=compile_cmd
        result['status']='COMPILE_ERROR'
        c=subprocess.run(compile_cmd,cwd=work,env=env,capture_output=True,text=True,timeout=90); (run/'compile.stdout.log').write_text(c.stdout,encoding='utf-8'); (run/'compile.stderr.log').write_text(c.stderr,encoding='utf-8'); result['compile_exit']=c.returncode
        if c.returncode:
            result['status']='COMPILE_FAILED'
        else:
            result['classes']=inventory(classes)
            cmd=[str(jdk/'bin/java.exe'),'-Xms64m','-Xmx1024m','-Djava.io.tmpdir='+str(work/'temp'),'-Dlogback.configurationFile='+str(work/'logback.xml'),'-Xlog:class+init=info:file=../class-init.log','-cp',str(classes)+os.pathsep+str(JAR),'D02NaturalLoadFirstAudit',str(run/'driver-result.json'),manifest['work_before']['config.json']]; result['command']=cmd
            result['status']='RUN_ERROR'
            with (run/'stdout.log').open('wb') as out,(run/'stderr.log').open('wb') as err:
                proc=subprocess.Popen(cmd,cwd=work,env=env,stdout=out,stderr=err); start=time.monotonic(); result['pid']=proc.pid
                while proc.poll() is None:
                    log=run/'class-init.log'; content=log.read_text(errors='replace') if log.exists() else ''
                    hits=forbidden_lines(content)
                    if hits: result['forbidden_class_init']=hits; proc.kill(); result['status']='FORBIDDEN_CLASS_INIT'; break
                    if time.monotonic()-start>90: proc.kill(); result['status']='TIMEOUT'; break
                    time.sleep(.1)
                result['exit']=proc.wait()
                if result['status']=='RUN_ERROR': result['status']='EXITED'
            log=run/'class-init.log'; content=log.read_text(errors='replace') if log.exists() else ''; hits=forbidden_lines(content); result['forbidden_class_init']=hits
            if hits: result['status']='FORBIDDEN_CLASS_INIT'
    except subprocess.TimeoutExpired as exc:
        result['status']='COMPILE_TIMEOUT'; result['error']=str(exc)
        (run/'compile.stdout.log').write_bytes(exc.stdout or b''); (run/'compile.stderr.log').write_bytes(exc.stderr or b'')
    except Exception as exc:
        result['error']=repr(exc)
    finally:
        if manifest is not None:
            try:
                result['protected_after']=protected(); result['protected_changed']={k:[v,result['protected_after'].get(k)] for k,v in manifest['protected_before'].items() if v!=result['protected_after'].get(k)}
                result['work_after']=inventory(work); result['changed_work']={k:[manifest['work_before'].get(k),v] for k,v in result['work_after'].items() if manifest['work_before'].get(k)!=v}; result['removed_work']=[k for k in manifest['work_before'] if k not in result['work_after']]
            except Exception as exc:
                result['postcheck_error']=repr(exc); result['status']='POSTCHECK_FAILED'
        driver=None
        try: driver=json.loads((run/'driver-result.json').read_text(encoding='utf-8'))
        except (OSError,ValueError): pass
        result['driver_outcome']=driver.get('outcome') if isinstance(driver,dict) else None
        result['success']=run_ok(result,driver)
        (run/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    return result
def main():
    parser=argparse.ArgumentParser(); group=parser.add_mutually_exclusive_group(required=True); group.add_argument('--prepare-only',action='store_true'); group.add_argument('--run',type=Path); a=parser.parse_args()
    if a.prepare_only: print(prepare())
    else:
        run=a.run.resolve(); check(run.parent==BASE.resolve() and run.name.startswith('run-'),'run outside designated root'); result=execute(run); print(result['status']); return 0 if result['success'] else 1
if __name__=='__main__': raise SystemExit(main())
