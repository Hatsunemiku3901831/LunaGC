# E-01d1 / R3-AVATAR-LOCAL-SAVE：出生 Avatar 局部保存与冷绑定准备

子代理完成三个 JUnit 和独立 Mongo driver 准备后，主线已实际运行三项并独立审核通过；准确范围见下方实际验收。父项 E-01d1/D02-R3 仍进行中；`completeAvatar=false`、`resourceReady=false`、`d02Complete=false`。复用已验 C 固定 GitHub/AstaPS 检索与来源，没有新网络检索或 clone。

## 合同更正与真实入口

初始规格把已验 Item 的 Guard、revision/token 和冷读零写外推给 Avatar。真实 [DatabaseHelper](../src/main/java/emu/grasscutter/database/DatabaseHelper.java) 的 `saveGameObject` 只对 Account、Player、Activity、GameItem 调 Guard，Avatar 走普通 `datastore.save`；[Avatar](../src/main/java/emu/grasscutter/game/avatar/Avatar.java) 没有 SaveGuard/SaveStamp。两名主角的 [AvatarStorage.loadFromDatabase](../src/main/java/emu/grasscutter/game/avatar/AvatarStorage.java) 还会在绑定后调用 `setSkillDepotData` 和 `avatar.save`。主线已撤回上述外推，不把原有主动保存判成缺陷。本卡忠实验证该局部行为；这是纠正无来源预设，不是放宽资源 ready 或生产校验。

不能直接沿用 Item 测试的 `new Player(); setUid()` 当完整冷绑定前置：无参 Player 是 Morphia 入口，没有天然 TeamManager。经主线核准，本卡使用真实公开 `Crypto.loadKeys()`→`new Account()/setId`→`new GameSession(null)/setAccount`→`new Player(session)/setUid`，不 override TeamManager、不反射填 manager，也不进入完整 `Player.loadFromDatabase` 或 `onLogin`。

已只读核对 [Player](../src/main/java/emu/grasscutter/game/player/Player.java) 的公开构造、`applyProperties`、`applyStartingSceneTags` 和相关 manager 构造：建立真实 TeamManager 等局部对象，SceneTag/Compound 在新 worker 中必须自然为空，构造不保存账号/玩家。CookingCompound 会建立两个静态 cache，测试保留并恢复其原引用。`applyProperties` 会自然尝试构造并发送属性通知；无 tunnel 的 [GameSession.send](../src/main/java/emu/grasscutter/server/game/GameSession.java) 在原 catch 内结束，可能留 DEBUG。测试不替换发送、不关闭 XOR，先走 [Crypto.loadKeys](../src/main/java/emu/grasscutter/utils/Crypto.java) 的真实启动入口，仅断言 dispatch/secret 非空，保留并恢复所有被该入口影响的静态引用和公钥 map；不输出 key、key 内容 hash 或凭据。

这里的 session 没有 server/tunnel、没有完成登录，`packetsSent` 必须仍为 0。普通构造的通知尝试、DEBUG 和任何异常都保留在隔离日志；0 不代表通知语义、网络、SDK、密钥信任或客户端通过。若出现新的实际失败，保留真实首栈，不补返回值或关闭校验。

## 数据输入与复用范围

forked worker 必须设置三个绝对路径系统属性，值从当前 checkout 解析；测试先读取原 bytes 并核完整 SHA。

| 属性 | 相对输入 | SHA-256 |
| --- | --- | --- |
| `d02.talents.projection` | `local/migration72/d02-resume-20261110/c-depot-talents-implementation/first2.private.json` | `ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f` |
| `d02.talents.mapping` | `local/migration72/d02-resume-20261108/r3-mapping/main-third.json` | `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e` |
| `d02.talents.promote` | `local/migration72/d02-resume-20261108/r3-promote/main-third.json` | `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1` |

真实 loader 片段来自旧 [D02BirthDepotTalentsResourceLoadTest](../src/test/java/emu/grasscutter/data/D02BirthDepotTalentsResourceLoadTest.java)，源 SHA `646bd3d5fefa0d2a4bec408b3d60401885b50c448d8a27f24ceb6577b291e385`。静态对照确认新 `loadAll` 辅助方法与旧正向路径逐行一致，仅去掉旧测试的删 talents 负例参数和分支；它依次调用五个真实 typed loader，不是全局 `ResourceLoader.loadAll`。

资源只有四条 Skill、501/701/704 Depot、100 条 Curve、`12<<8` Promote 和两个出生 Avatar 的已批字段。来源及未闭项见 [talents Java 实测](CLIENT_72_D02_DEPOT_TALENTS_JAVA_20261110.md)、[直接消费者](CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md)及 [proud 后继](CLIENT_72_D02_DEPOT_PROUD_SUCCESSOR_20261110.md)。没有增加 alias、伪技能、空 proud 对象或 ability。当前 onLoad 将未批准 proud 字段规范化为空的行为只作为这批片段的既有 Java 上下文，不获得目标业务许可，也不覆盖 proud/ability、资源完整性或自然 revision。

## 三个待运行案例

[新测试](../src/test/java/emu/grasscutter/data/D02BirthAvatarStorageMongoTest.java) 每例新建自有 MongoClient、UUID 数据库与真实 Morphia Avatar/GameItem 映射。保存对象均经 `new Avatar(approvedData)`→`AvatarStorage.addAvatar`→真实异步队列→`awaitIdle` 产生，不直接插入或修改成功 BSON。

| 案例 | 真实路径及预期断言 | 范围边界 |
| --- | --- | --- |
| AS-01 `as01TwoBirthAvatarsRebindWithExistingLoadUpdates` | 两出生角色真实构造；exp 分别 17/29，等级 1、突破 0、未装备；addAvatar 各 insert 一条。重载同源定义，使用全新同 UID 的公开构造 Player，仅 `AvatarStorage.loadFromDatabase`；各 update 一次且命令目标是各自原 `_id`，条数仍 2、items 仍 0，原 objectId/owner/avatar/depot/受控字段稳定，资源定义对象换新，GUID 重新绑定到新 Player/map | 正向不进入 Inventory；不要求或声称冷读零写、Guard/revision/token。Avatar 没有独立 `grade` 持久字段，这里按真实 `level/promoteLevel` 核等级/突破 |
| AS-02 `as02MissingAvatarOrStoredDepotDefinitionsPreserveRawBson` | 先真实保存两条；第一阶段移除全部所选 Avatar 定义，第二阶段重载后仅移除所存 Depot501/701（704仍在）。每阶段全新同 UID Player→真实 AvatarStorage→Inventory，均接受 0 Avatar/0 Item；原 BSON bytes、全部监听业务写数及队列 completed 不变 | 跳过没有定义的存档并保留，未证明资源补齐后 Inventory/装备恢复 |
| AS-03 `as03DifferentUidCannotBindSavedAvatars` | 先真实保存两条；另一 UID 的全新 Player→AvatarStorage→Inventory，接受 0 Avatar/0 Item，原 BSON bytes和写/队列计数保持 | owner 查询隔离；不证明恶意 BSON 修改、并发覆盖或完整账号认证 |

AS-01 的比较包含 `_id/ownerId/avatarId/skillDepotId/level/exp/promoteLevel/bornTime/flyCloak/fetterLevel/trialAvatarId/grantReason/fromParentQuestId`；不把未最终装备重算的 HP/能量当作完整恢复结果。保存阶段核 transient GUID/owner/定义/equips 不落库，也不出现不存在的 saveStamp。GUID 的低位从 warm3/4 变成 cold1/2，高位仍原 UID。主动更新不是重复创建，必须同时看每条命令的 `_id`、集合与 count。

AS-02/03 直接通过 Mongo `RawBsonDocument` 取实际 BSON bytes 比较，未从 JSON 重新编码冒充原字节。监听保存当前 UUID 库内 insert/update/delete/findAndModify 的完整命令到私有证据；控制台仅输出名称/集合/次数、队列摘要与测试库名。队列要求 drain 后 outstanding/queued=0，failed/rejected/cancelled 不增。构造 Player 前后另核零业务写、无 server、未登录及 packetsSent=0。

测试恢复 13 个资源 map、fetters 原引用、Curve 顺序、CookingCompound 两 cache、Crypto 原状态与原 gameDatastore，检查 loadedAll 仍原值且初始必须 false。每例 UUID 库保留在独立 dbpath 供主线审核，不 drop 库；关闭自建客户端。所有 BSON、命令、数据库及日志只留忽略目录。

## 主线执行入口与恢复

私有准备目录是 `local/migration72/d02-resume-20261110/c-avatar-local-save/`。中文计划 `plan.zh.md`，静态审核 `audit_preparation.py`/`preparation-review.private.json`；runner `run_avatar_java.py` 由 `prepare_driver.py` 从已验 `run_item_java.py` 派生。旧 runner 只读。**子任务没有运行新 runner**。

runner 每次新建 run/work、六个独立根与 version14 config，沿用已验隔离 logback/temp、JDK17、offline/no-daemon/max-workers1；使用该 run 专属 dbpath、显式27027、本机 portable mongod。它要求主线提供本轮新盘点及其完整 SHA，对13保护项、原 JAR、固定输入/源码门禁及实际进程/端口归属再核对；不能把旧盘点或本页当作当前现场许可。

主线先将本轮已审核的盘点文件绝对路径放到 `D02_AVATAR_INVENTORY`，再执行：

```powershell
$avatarInventory = (Resolve-Path -LiteralPath $env:D02_AVATAR_INVENTORY).Path
$avatarInventorySha = (Get-FileHash -LiteralPath $avatarInventory -Algorithm SHA256).Hash.ToLowerInvariant()
python local/migration72/d02-resume-20261110/c-avatar-local-save/run_avatar_java.py --inventory $avatarInventory --inventory-sha256 $avatarInventorySha --case all
```

`--case positive` 只运行 AS-01（预期1项）；`--case negative` 只运行 AS-02/03（预期2项）。`all` 预期3项，0 failure/error/skipped。选择器只是独立复现入口，不以负向通过抵消正向失败。runner 实际 Gradle 参数为：

```powershell
.\gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<本轮新run的isolated-test.init.gradle>' test --tests emu.grasscutter.data.D02BirthAvatarStorageMongoTest
```

init 会向 forked Test JVM 设置上表三属性、`d02.avatar.save.evidence`、`LUNAGC_TEST_MONGO_URI`、portable根、tmp/logback与 class-init。只在 Gradle 外层设属性不能替代该入口。无测试 URI/输入、source hash 漂移、非空现场或保护项变化即停，不连接27017或复用未知 Mongo。所需 portable 工具/旧隔离模板/正常 shutdown helper 缺失时准确返回，不下载、不猜替代路径。

保留 Gradle完整日志、XML、class-init、原始 BSON/命令、前后保护/输入/JAR及 Mongo 归属/正常 shutdown 记录。允许 DatabaseManager 初始化；GameServer/HTTP/DispatchServer/ScriptLoader/PluginManager 初始化为失败。正常结束只关闭本轮新启且 PID/exe/dbpath/TCP归属都相符的 Mongo，沿用已验 shutdown helper，等待exit0/日志完成/27027释放，保留dbpath。不强杀、不触碰原 mongodata/local/data，也不调用GS启动/部署。

首个 loader、Crypto公开加载、Player构造、Morphia、目标状态或命令断言失败保留原栈和原件，不补数据、raw成功Avatar、loadedAll、空proud/ability、manager或发送返回值；修正有来源的准备错误后另建新 run，原件不覆盖。`awaitIdle` 本身不等于保存成功，必须同时核队列失败、实际命令、原文档和 XML。只有测试/私有离线工具/文档变化，无需重启GS。

## 子代理准备阶段的验证与未完成项

交付时仅执行只读源码/输入审核、runner 文本准备和20项静态检查（包括固定 SHA、旧loader同源、三案例范围与私有Python AST）。这20项不是JUnit数量，也不是运行时结果。静态证明 `preparation-review.private.json` 记录源码/输入完整 SHA 和 `javaRun=false/mongoRun=false`。读取时三个错误候选路径均已按真实 `rg --files` 更正并记入私有证明，没有以缺路径推断实现缺失。

交付时尚未执行编译、Java、Mongo、服务、网络、客户端、native/PE/API或Git；随后主线完成如下真实运行。即使局部通过，也不覆盖正向 Inventory/初始武器/装备、完整Player、Guard并发/版本防护、proud/ability、场景、客户端保存/退出重登或D-02。

## 主线实际运行与独立验收

主线在AB首次299/0RPM轮正常退出核验后重新解析GAME_PATH、完整重核五stock/载体/13保护、portable14/缺席、进程与固定监听空；新盘点SHA `26691bcae15f152838066ea5a483573fc17e9c133b2725246c199edda3b0b745`。没有借用旧PID/ready或启动原数据库。

便携JDK17实际offline/no-daemon/max-workers1，真实compileTestJava/test通过，Gradle24秒/exit0；XML **tests3/failures0/errors0/skipped0**。AS-01两真实出生保存后新Player/定义重载，原objectId、owner和受控字段保持、GUID重绑、逐条既有update且条数仍2；AS-02两个缺定义阶段和AS-03另一UID均零业务写，原BSON不变。公开Crypto/Account/GameSession/Player入口真实执行，未伪manager/Guard/loadedAll、未关闭XOR；无tunnel发送尝试保留在原日志，不代验任何发包。

主线11项独立文件审核重读XML/源、12份实际BSON及三库命令，确认正向2insert+2update、两个负向各2insert后零update，选定字段与原ID保持；11301条class-init无GameServer/HTTP/DispatchServer/ScriptLoader/PluginManager初始化。三个UUID库留在本run的独立dbpath，27027 owned Mongo正常shutdown/exit0、日志完成，结束进程/固定监听空；原JAR、13保护和三个输入保持。

原件 `local/migration72/d02-resume-20261110/c-avatar-local-save/run-20261110T050108Z-765c9beb/`：`main-run.private.json` SHA `10740b5ab497996da07498812fb69ff6f6e3a5ca8d29d281d2750a427f1a9924`；`main-review.private.json` SHA `13dd95ac25364e11a4e0af0d6624f1bb379bb7bb414df74fe2ab428ac4ac3625`。原test/doc准备字节已被并行外部提交42c8a06收录，主线保留该提交，在新HEAD上补实际验收记录，不重写历史。

仅关闭E-01d1/R3-AVATAR-LOCAL-SAVE的局部子项。正向Inventory/默认武器/装备和最终vitals、完整Player/资源/proud/ability、自然revision、场景及客户端退出重登仍未验；resourceReady和D-02保持未完成。仅测试/私有driver/文档变化，没有GS重启或部署。
