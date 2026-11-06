# E-01d1n1：目标章节 303 的隔离加载与恢复

本合同实测目标 30302 Excel 行被当前 loader 拒绝，以及目标 chapter 303 的 BIN 子项通过现有 fallback 恢复。它不证明 30302 能在真实玩家登录时启动、保存或完成，也不证明目标任务语义、客户端或 D-02 通过；入口必要性和未知字段见 [任务入场审计](CLIENT_72_D02_QUEST_ENTRY.md)。没有修改生产代码、资源、配置或字段 alias。

## 实际执行与结果

`tools/Client72Quest303Audit.java` 使用独立配置、空 data/cache/resources 目录和独立 JVM，不启动 MongoDB、HTTP、Dispatch、GameServer 或 ScriptLoader。它读取固定目标 QuestExcel 原始数组，从中选出 30302 行并原样保留其 JSON 内容，分别调用 `JsonUtils.decode` 和真实 `ResourceLoader.loadFromResource`：**1 个负例按预期拒绝，安装 0、自动索引 0**。异常为 `JsonSyntaxException`，内部 `Invalid Enum definition - BEGIN_OBJECT`；资源诊断日志也记录了该行拒绝。切片只是对解析后的同一 JSON 对象重新序列化，没有更改字段或提供替代别名；切片 SHA-256 `00c8729b680159bc55d9d6a9c332d8f16f487ad3c0aeac44f63df36e5dce3b0c`。

随后使用真实 `JsonUtils.loadToClass` 读取固定目标 303.json，按生产 `ResourceLoader.loadQuests` 的顺序先加入 MainQuestData map，再执行 `MainQuestData.onLoad → QuestBinFallback.recover`。**63/63 个 chapter 子项实际安装**，逐项与原 BIN 对照 subId、mainId、order、finish condition 的类型/整数参数/count，及 begin/finish/fail exec 的类型和参数：共比较 63 个 finish condition、136 个 exec。所有 63 个 `automaticAcceptance=false`，自动条件索引无 key。30302 单独断言 `QUEST_CONTENT_COMPLETE_TALK [30302,0]` 及 `QUEST_EXEC_UNLOCK_POINT ["3","3"]`、`QUEST_EXEC_UNLOCK_AREA ["3","3"]`。这只验证当前 Java 恢复出来的指定字段与该 BIN 的具名字段一致，不执行 `QuestManager.addQuest`、`Player.onLogin`、存档或条件/exec 的游戏效果。

63 个 finish condition 均包含当前 `QuestCondition` 未识别的 `BGHPAKBEPND`，本章中这些值均为空，Java `paramStr` 也为空；不能由空值样本确定该字段语义。章节子项还出现当前 `QuestData` 未消费的 `BKEKBPAKKJD`、`FKEABBKFLNC`、`guideHint` 和 `showType`。本合同没有验证这些字段、目标文本/引导语义或所有 QuestData 字段：例如这 63 个原 BIN 子项未提供具名 `descTextMapHash`，不能据 ID/条件匹配推断展示数据已恢复。别的章节可能含非空未知字段，不能将 303 的结果推广至全部目标任务。

两次新进程完整报告逐字节一致，SHA-256 `d34b13d5d034d7bdb5d5a498a1e78775e136805b3c2ac3829c69a0c24c15a184`。工具源码 SHA-256 `51fbc85ac7f3e88bf10ca5ab3d683d895bd223a6de211f8506198e6d1510b410`，编译 class `5f2938d0d0758da9a58a8abe3642d69888c921a0ec77d3f79200f372ca4e6328`。原始报告、stdout/stderr、class-init、隔离配置和类清单保存在 `local/migration72/d02-r3-quest303-n1-verified-8be635547aee444183003f45d3b9bfdb/`。两次 class-init 均未见 database、MongoClients、HTTP、DispatchServer、GameServer 或 ScriptLoader 初始化；这是离线加载实验，不是服务/客户端验收。

首次隔离运行因未创建空 `resources/BinOutput` 和 `resources/ExcelBinOutput`，在任务加载前被 `Utils.startupCheck` 终止，原 `class-init.log` 及退出状态留在 `local/migration72/d02-r3-quest303-n1-d01de3c226e54bfa95c27386448f48ef/`。补齐隔离目录后真实运行；并未创建或修改原资源。早期成功实验和最终工具有不同源码版本，以此处两次最终报告为准。

## 固定输入与类来源

目标 QuestExcel SHA-256 `c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87`，目标 chapter 303 SHA-256 `aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca`；候选 commit/tree/blob 与旧表/章节来源见前述入场审计。工具同时验证旧 QuestExcel SHA-256 `aacb6edba8b97e3dd39fef80444bb180756fe1d6090c341cd0545ec08f0951a3` 和旧 chapter SHA-256 `44b646a0f29a3a1a1b780d58902ee28e7d5749ba13a45e6b9b2a6d48d12ad8cc`，但本合同没有把旧资源当作目标数据输入或再次运行旧表对照。

运行使用本机固定 JAR 副本 `local/migration72/builds/LunaGC-d02-quest-dfdebbda.jar`，SHA-256 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`。工具在运行前比较 JAR 内 ResourceLoader、QuestData 及嵌套类、MainQuestData、QuestBinFallback、GameData、JsonUtils、JsonAdapters 和条件/执行/逻辑枚举共 **37 个 class** 与当前 `build/classes/java/main` 的字节，任一不一致即拒绝。11 个对应源码文件和 37 个 class 的 SHA-256 保存在本机 `class-and-source-hashes.json`，文件 SHA-256 `a089076f7b2b64ff8c4a3cf79f3bffd2e8aef8bccb0afb5f69c9e7d72ec0bdaf`；源码 hash 单独记录，class 字节一致不等于目标客户端语义已证实。

## 复现

先准备固定候选 Excel 和 BIN、上述固定 JAR 副本、现有主项目编译 class 和便携 JDK17；不要求启动数据库，也不运行 Gradle。以下从已跟踪源码独立 javac，创建两个新工作目录，清除资源路径环境覆盖，写日志并核两次报告一致。保留目录用于审阅。

```powershell
@'
from pathlib import Path
import hashlib,json,os,subprocess,uuid
r=Path('.').resolve(); jar=r/'local/migration72/builds/LunaGC-d02-quest-dfdebbda.jar'; jdk=next((r/'local/runtime').glob('jdk-17*'))
base=r/'local/migration72'/('d02-r3-quest303-n1-replay-'+uuid.uuid4().hex);base.mkdir();classes=base/'classes';classes.mkdir()
c=subprocess.run([str(jdk/'bin/javac.exe'),'-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(r/'tools/Client72Quest303Audit.java')],capture_output=True)
(base/'compile.stdout').write_bytes(c.stdout);(base/'compile.stderr').write_bytes(c.stderr);c.check_returncode();env=os.environ.copy();env.pop('LUNAGC_RESOURCES_DIR',None)
for label in ['first','repeat']:
 w=base/label;w.mkdir();(w/'resources/BinOutput').mkdir(parents=True);(w/'resources/ExcelBinOutput').mkdir(parents=True)
 cfg={'version':14,'folderStructure':{k:str(w/k) for k in ['resources','scripts','data','plugins','cache','packets']}};(w/'config.json').write_text(json.dumps(cfg),encoding='utf8')
 logback=w/'logback.xml';logback.write_text('<configuration><appender name="console" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%level %logger - %msg%n</pattern></encoder></appender><root level="WARN"><appender-ref ref="console"/></root></configuration>',encoding='utf8')
 p=subprocess.run([str(jdk/'bin/java.exe'),'-Dlogback.configurationFile='+str(logback),'-Dfile.encoding=UTF-8','-Xlog:class+init=info:file=class-init.log','-cp',str(classes)+';'+str(jar),'Client72Quest303Audit',str(r),str(r/'local/migration72/d02-r3-candidate/ExcelBinOutput/QuestExcelConfigData.json'),str(r/'local/migration72/d02-r3-candidate/BinOutput/Quest/303.json'),str(w/'result.json')],cwd=w,env=env,capture_output=True,timeout=90)
 (w/'stdout.log').write_bytes(p.stdout);(w/'stderr.log').write_bytes(p.stderr);p.check_returncode()
 lines=(w/'class-init.log').read_text(encoding='utf8',errors='replace').splitlines()
 bad=[x for x in ['emu/grasscutter/database/','com/mongodb/client/MongoClients','emu/grasscutter/server/http/HttpServer','emu/grasscutter/server/dispatch/DispatchServer','emu/grasscutter/server/game/GameServer','emu/grasscutter/scripts/ScriptLoader'] if any("Initializing '"+x in z for z in lines)]
 assert not bad,bad;print(label,'sha256',hashlib.sha256((w/'result.json').read_bytes()).hexdigest())
assert (base/'first/result.json').read_bytes()==(base/'repeat/result.json').read_bytes();print('evidence',base)
'@ | python -
```

上述文档代码已实际提取并在新目录运行，两次报告均为 `d34b13d5d034d7bdb5d5a498a1e78775e136805b3c2ac3829c69a0c24c15a184`；日志在 `local/migration72/d02-r3-quest303-n1-replay-ce5c7a35477e436ab015aadf863b7101/`。

主线逐项审核工具及真实生产调用顺序，核对11个源码hash及37个archive class，再从本专题提取命令独立javac、运行两个新JVM；报告与上述最终值逐字节相同，未见所列数据库/服务/ScriptLoader初始化。主线目录 `local/migration72/d02-r3-quest303-n1-replay-c316f14e2e9d44c5a4193987ebdf66d3/`，证明 `local/migration72/evidence/d02-r3-quest303-main-review.json`。各批次结果不累加为客户端通过数。

后续[n2真实启动/保存](CLIENT_72_D02_QUEST_303_START.md)已通过两项独立Mongo定向并经主线复现，验证直接30302/Progress私有入口、异步保存/DatabaseHelper冷读及真实未就绪门禁；正向QuestManager重绑定、完整登录/scene/目标通知仍未验。针对其他章节的非空未知字段需要独立目标消费者证据，不能以本合同扩大字段 alias 或标记 R3/resourceReady。
