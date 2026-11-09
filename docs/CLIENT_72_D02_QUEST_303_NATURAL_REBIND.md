# E-01/E-01c/E-01d1：303 自然加载后的局部保存重绑

当前状态：**主线实际编译、独立 Mongo、新 JVM 与24项独立审核通过，303局部正向恢复已验收。** 同一次自然加载后真实保存 → 新 Player → 真实 QuestManager 重新绑定63个子项；完整资源、事件/场景、E-01/E-01c/E-01d1、D02-R3 和 D-02 仍未闭。前置证据复用[三文件自然首诊](CLIENT_72_D02_NATURAL_LOAD_FIRST_RUN.md)、[303 fallback](CLIENT_72_D02_QUEST_303_RECOVERY.md)、[真实启动/保存](CLIENT_72_D02_QUEST_303_START.md)与[Chapter](CLIENT_72_D02_CHAPTER_LOAD.md)。

## 局部源码合同

`QuestManager.resourcesReadyForQuestLoad` 要求生产 `ResourceLoader.isLoadedAll()` 为 true 且 Quest map 非空。`loadFromDatabase` 经真实 `DatabaseHelper.getAllQuests` 读取本 UID 全部 GameMainQuest，逐章检查真实 MainQuestData 的同 ID，以及每一个保存 child 的 map key/subQuestId/mainQuestId 与真实 QuestData 的 id/mainId。任一父定义或子项缺失/不一致，该章只进入 deferred 集合；不发布或部分绑定。全部子项验证后才 `setOwner`、`setMainQuest`、`rebindConfig`、放入新 manager；只有进度元数据改变才调用父章 save。

该局部调用不执行 quest.start、Player.onLogin、任务事件、任务效果、World/Scene 或发包。它无需全部 161 表齐全或全部错误归零。自然 loadedAll=true 也不足以接受本卡，driver 额外逐项验证：

1. 同一次未修改 loadAll 自然返回，最初 false，之后 true，调用次数恰为 1。
2. 实际 Chapter map=436，Quest map=63；MainQuest303 实际 63 子项，与固定原 BIN 的全部 subId 同序且唯一。
3. 63 个实际 QuestData 均同 ID/mainId303/hasMatchingChapter=true；order、finish condition 的 type/param/count，以及 begin/finish/fail exec 的 type/param 与固定原 BIN 对照。30302 保持 COMPLETE_TALK `[30302,0]` 与两个解锁 exec 的原参数，不执行这些效果。
4. 保存的全部 63 个子项逐一匹配以上实际定义。缺项准确停止，不添加旧资源、占位、别名或手置 flags。

`Player()` 是生产反序列化裸构造；它创建真实 QuestManager 等管理器，但不创建 TeamManager/World/GameServer。已读 46 份构造/保存相关生产源码，除原 loader 审计的 31 源/96 class 外，再冻结 46 类族/93 class 条目，并与固定 JAR 和当前编译输出逐项比较。Inventory 读取现有配置限额；CookingCompoundManager 空 Compound map 的处理由现有生产代码支持；AbilityManager 的真实静态 handler 注册照常发生。未发现本调用额外必须提供 GlobalCombat、Scene 或完整资源。若真实裸构造/反射注册仍抛错，保留首缺返回，不能 override 管理器制造成功。

## 固定输入与写入边界

工具为 [driver](../tools/D02Quest303NaturalRebindAudit.java)、[runner](../tools/audit_d02_quest303_natural_rebind.py)、[检查](../tools/test_audit_d02_quest303_natural_rebind.py)。旧首诊工具、run、source-at-first-run 与 input-manifest 不修改、不复用。新根为 `local/migration72/d02-parallel-20261109/r3-quest303-natural-rebind/`，每次准备生成新的 run。

固定根 JAR SHA-256 为 `8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`，文件名遗留版本不改。runner 直接复用旧工具的固定 JAR/31 源/96 class/三输入检查与纯只读函数，不调用其 prepare/execute。R 仍只有三个普通独立副本，SHA 和候选来源与首诊完全相同；S 为 R/Scripts 三个空域；D 为空，默认数据仍从固定 JAR 解析。无原 resources、旧 GlobalCombat、假场景、Server 覆盖或脚本补丁。

保存输入是此前主线真实受控直接启动/保存得到的 `local/migration72/d02-r3-quest303-replay-873511c485744e10b8dad1d75e481df8/direct-bson.json`，17211 bytes，SHA-256 `3da7b80960cb2a09d2d1a0ee00ce7659600f855d635f4144ed23ab57aa4deff1`。仅自有 UID210001/303/63 子项，原文件只读，导入新 run 内同 SHA 普通副本。raw BSON、JSON、配置、路径、日志、class 和 proof 全部留忽略目录。

Mongo 由主线准备且独占：URI 必须显式为 `mongodb://127.0.0.1:27027`，唯一数据库名为 `lunagc_d02_quest303_natural_<32位小写uuid>`，dbpath 为本 run 的 `mongo-data/`。driver 不调用 DatabaseManager.initialize，仅仿原测试用该自有 Datastore 为 gameDatastore 接线，Morphia 只 map GameMainQuest。目标库已有任意集合则拒绝，不删除或覆盖。不会连接 27017，不创建账号/玩家主档，不 drop，不自行启动或关闭 Mongo。

## 实验阶段与验收输出

顺序为 bootstrap/实际根与配置 SHA → 新库导入 fixture → 自然 loadedAll=false 时真实新 Player 的 Manager 拒绝 → 原始 BSON 与 SHA 不变/队列 0 写 → 生产 loadAll 一次 → 上述局部必要定义成立 → 真实 Morphia 冷读 GameMainQuest、setOwner(真实 saver)、真实 main.save/awaitIdle → 另一个同 UID 新 Player 调真实 Manager → 比对所有对象与持久字段。

恢复需同时满足：manager 仅含 303；父章对象与保存源对象不同；owner/questManager 指向最后新 Player 的实际实例；63 个 GameQuest.mainQuest/owner 指向最后父章/新 Player；QuestData 引用恰为该轮 GameData 实际对象。30302 保持 UNFINISHED，62 项 UNSTARTED，finishProgress `[0]`、原始时间、questVars/timeVar、父状态、归属与非空条件元数据保持。边界子类只记录 sendPacket，预期全部 0，不替换数据库、Manager 或定义。

fixture 没有 `failConditionKeys`，真实 rebind 将其绑定为空 keys，所以该次 Manager 恢复预期有一次父章 autosave；与前面的显式 main.save 合计队列 completed=2。driver 分阶段核队列 failed/rejected/coalesced/cancelled/outstanding=0、精确完成数、唯一 quests 记录和唯一写入集合。空元数据的省略/空集合差异在受控 projection 中归一；其余 raw 字段差异逐路径保留，原始数据库返回的 BSON bytes 和 SHA 每阶段保存，不能用 projection 冒充 BSON 字节完全相同。

全部 WARN/ERROR 附阶段/顺序/线程/logger/消息/stack。LOADALL 内已知 17814 QuestExcel BEGIN_OBJECT 拒绝与其它缺表/目录诊断继续保留，实际 map 快照记录全部反射资源。LOADALL 外 ERROR、自然加载异常、必要定义缺失、保存不一致、额外服务初始化或超时均停止。它们描述三文件输入不足，不能归因为原 resources 缺陷。局部通过时仍明确 `globalResourceReady=false`。

## 主线唯一执行入口

先审核本卡源码，再串行执行。下面仅为主线复现命令；本卡准备阶段没有运行 javac/Java/Gradle/Mongo。

```powershell
python -m unittest tools.test_audit_d02_quest303_natural_rebind -v
$d02Db = 'lunagc_d02_quest303_natural_' + [guid]::NewGuid().ToString('N')
$d02Run = python tools/audit_d02_quest303_natural_rebind.py --prepare-only --mongo-uri mongodb://127.0.0.1:27027 --database $d02Db
```

主线在该新 run 的 `mongo-data/` 启动便携 Mongo，参数固定 `--bind_ip 127.0.0.1 --port 27027 --dbpath <该run/mongo-data> --logpath <该run/mongo.private.log> --logappend`。Windows 后台用 Start-Process 时 `-WindowStyle Hidden`。先核原进程/端口归属，不替换已有占用。启动后实际核 Mongo 可执行路径、命令行、创建身份、27027 监听 PID、dbpath，及无项目 GameServer/HTTP/Dispatch/插件、8088 TCP/22101 UDP 监听；本 runner 不自行查询或认领现场。

将主线真实核验摘要写到该 run 的 `main-preflight.private.json`，字段为 `run`、`mongo_uri`、`database`、`mongo_dbpath`、正整数 `mongo_pid`、同值 `mongo_27027_pid`、`mongo_identity_verified=true`、`project_game_http_dispatch_plugins_absent=true`、空数组 `http_8088_listeners`/`game_22101_listeners`，以及带时区 ISO8601 `observed_utc`。这是实测证明，不能照模板直接填 true。runner 只接受 5 分钟内且与固定 manifest 完全关联的摘要，并记录其 SHA。

```powershell
python tools/audit_d02_quest303_natural_rebind.py --run $d02Run --preflight (Join-Path $d02Run 'main-preflight.private.json')
```

runner 内唯一 javac CLI 只用固定便携 JDK17、固定 JAR 作 classpath，在该 run/classes 编译单一新 driver，预算 90 秒；唯一 Java CLI 只将该 classes 与固定 JAR 入 classpath、CWD 指向该 work、临时与日志根指向该 run，运行预算 120 秒，DB 每次等待 20 秒。所有命令/环境覆盖存在性、PID、退出码、class SHA 写入私有 result；源码/配置/候选/原保护文件前后核验，只有预定 temp 的新项允许变化。监控每 100ms 检查禁止类初始化，不能宣称它能在初始化前阻止副作用。

driver 正常 drain 数据库写队列并关闭自身 MongoClient，保留测试库；不发 Mongo shutdown。退出后主线复核该 JVM 已退出和 Mongo 同一身份，再对该自有 27027 admin 发正常 `shutdown`，接受关闭连接的预期响应，并确认该 PID 正常结束/27027 释放。已有 `ShutdownD02Mongo.java` 可先只读核 URI/来源后复制到此 run 单独编译使用；也可用已验证的便携 Mongo shell。无论成功或失败均保留 dbpath/数据库，不清库；不强杀未知进程。禁止初始化或预算超限时 runner 只结束它自己刚创建并持有的唯一 JVM，准确保留停止阶段。

## 当前实际检查与后继

实际 Python 检查 10 项通过，覆盖固定 JAR/源/class/输入、源漂移拒绝、27017/远端/库名拒绝、保存身份/子项/状态拒绝、真实历史 class-init 日志的服务边界、独立路径、主线 preflight 时效/归属、局部通过条件与拒绝归因。准备时另读取固定 JAR class-file 核对32个 API 名称/descriptor；实际编译和运行结果如下，不以符号存在性代替执行。

`run-20261109-235129-65b72696/` prepare 后因 AGENTS 外部更新拒绝；`run-20261109-235409-56098734/` 又在主线执行前因同一文件新增“大任务”措辞而 PRECHECK_FAILED，未编译/运行 driver，其独立 Mongo 正常停库，数据目录保留。两次 manifest/停止结果均未改写。主线重读新规则并另建 `run-20261110-000249-6f0a9d25/`，manifest SHA-256 `981eacb4f8b53f49641787a97aa0e2d17c1a67735c0aafd9f52f484a8bdb94c9`，才执行成功。

成功 run 的 javac/Java 均退出0，实际 JVM 2.734秒。loadAll 恰一次，false→自然true；152个实际资源map仅 Chapter436/Quest63 非空，固定原 BIN 的63项定义、ID、顺序、finish条件和exec参数对照通过。17814条 QuestExcel 表示拒绝、LOADALL内10 WARN/326 ERROR 保留；LOADALL外仅预期未就绪门禁1 WARN，无ERROR。该三文件快照不足以证明全局资源就绪，`globalResourceReady=false` 保持。

真实 Manager 未就绪时未发布、0写入且原始 BSON 字节相同；就绪后真实 Morphia 冷读、显式保存、新 Player/Manager 恢复均成功。63个child、父章、新owner、QuestManager和该轮真实QuestData对象引用逐一成立；30302 UNFINISHED、其余62 UNSTARTED、进度/时间/vars/归属保持，边界发包0。真实默认写队列 completed 精确0→1→2，其余三队列完成0，各阶段排队/活动/失败/拒绝/合并/取消均0。一次Manager autosave来自原fixture缺省的空failConditionKeys绑定；它没有改变数据库记录的字节。

### 原始 BSON 的类型保真复核

driver 的 `explicitSaveRawFieldDifferences` / `managerRestoreRawFieldDifferences` 经 `Document.parse(raw.toJson())` 比较，属于宽松JSON字段对照，**不保留BSON整数宽度**。两字段在本次为空，不能据此声称原始BSON完全相同。原始四阶段 `.bson` 始终单独保存。

主线另审核并实际编译运行只读 `r3-bson-width-review/R3BsonWidthReview.java`（源码SHA `b93246d6043d7b11ba6b733b67562a027d63f84958c2755c06784599cf865742`），逐路径比较真正BSON类型、值和顺序。seed→未就绪完全相同；首次显式保存恰73处 INT32→INT64：63个child的 startGameDay=0，以及timeVar十项=-1，数值全部保持，恰解释292B增长；无字段增删、值或顺序差异。显式保存→Manager恢复原始字节完全相同。宽度变化来自此前宽松JSON夹具导入后由真实Morphia按long字段保存，不是生产存档迁移或进度改变。结果及失败的未引用PowerShell `-Dfile.encoding` 命令保留；修正引号后仅重跑离线比较器，没有重跑Mongo实验。

主线24项独立审核复核实际manifest、source-at-prepare/当前工具、固定JAR/生产源/class、driver class、必要定义、队列/诊断、上述原始字节与类型、保护文件及正常停库；证明为本卡根 `main-review.private.py/json`，SHA-256 `265ca8da78d8aa82b68c31103d7387f97bdea0033a7eb5a76fc7b09b5042be56`。实际run内保护文件无变化，工作根唯一增量为指定temp内hsperfdata目录。主线确认自身Mongo同一exe/PID/dbpath后正常shutdown，Mongo/关闭helper均退出0、日志有正常完成、27027释放，数据库保留；当前相关进程和固定TCP/UDP均0。后续AGENTS再次更新“重点爬取分支”发生在该run结束后，单列新baseline，不修改旧结果。

源码或保护文件变动后若确需新实验，必须另 prepare 新 run，不能改写 manifest 或复用已编译/失败 run。本局部出口现已成立；后继继续实际必要完整资源、Gadget配置/ability、事件/场景及最终新旧号保存恢复，不因局部303成功提前晋级父项。

本卡只改离线driver/runner/测试/文档，无需 GS 部署；完整 D02-R3/R4 前置不足，本卡未启动 GS/客户端。账号/存档公共专题及 Project/Harness 已同步实际局部验收；客户端退出重登和D-02仍未验。
