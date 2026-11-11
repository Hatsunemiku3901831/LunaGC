2026-11-11 [旧号隔离快照](CLIENT_72_D02_OLD_ACCOUNT_SNAPSHOT_20261110B.md)：原物理库无当前进程但锁非空、缺干净退出证据，先保留完整证据副本，再仅对第二工作副本自然恢复和只读BSON/索引导出，owned27027正常关闭；原142文件在当时前后保持。不是原库干净冷备/生产恢复，也未触发业务保存。所选旧号实际121角色与12254物品的固定输入已交资源和完整恢复后继，身份/原件只留忽略目录。

# 账号与存档运维和开发手册

2026-11-10 [303局部自然恢复](CLIENT_72_D02_QUEST_303_NATURAL_REBIND.md)在新独立27027/随机库实际编译及运行通过，10工具检查、24主线审核；新Player/真实Manager恢复63项、未就绪BSON不变、队列0→1→2且失败/拒绝/未完成均0。保留三文件loadAll的10WARN/326ERROR及17814行拒绝，不以此断言原resources有缺陷；全局resourceReady仍false。自身Mongo正常shutdown/exit0/端口释放，DB/BSON及失败run保留，原27017/运行数据不动；未部署GS或验客户端重登。

2026-11-08 R2-COMBO-HTTP：6项真实Javalin/default认证/独立Mongo同步保存测试及六例原生请求→真实HTTP→原生响应通过；错误认证不出token、BSON不变，成功login token沿既有规则轮换且session/角色归属保持。未新增字段或修改认证/保存策略；两新27027实例正常停库，原27017/数据不动。准确PIPE保密、64位/嵌套、16项mock与复现见[combo HTTP](CLIENT_72_SDK_COMBO_HTTP.md)，不代验自然SDK或D-02旧号重登。

认证日志最新完整隔离结果更新为`d02-active/auth-logging/mongo-review4/green/`：副本原data绝对路径继承已纠正，四可写目录显式归属新runtime后35项全过，原8文件前后SHA一致、Mongo正常关闭。路径纠正前结果/失败保留，详见[日志专题](CLIENT_72_AUTH_LOGGING.md)；无新增运行代码或构建。

2026-11-08 [认证日志修正](CLIENT_72_AUTH_LOGGING.md)两套35项通过：6项handler/RSA日志、29项密码重置/会话/账号验证；新独立Mongo27027正常shutdown/exit0/端口释放，原数据不动。红测、夹具key_id遗漏的关联失败及最终XML分别保留在`d02-active/auth-logging/`。构建随后核验，完整SDK/客户端及部署仍待。

2026-11-07本轮Astra仅离线研究：[资源](CLIENT_72_RESOURCE_DEEP_RESEARCH.md)记录真实parser与缺失转换依据；[Avatar](CLIENT_72_AVATAR_DEEP_RESEARCH.md)记录初始化/后缀。没有新DB、loadAll或正向QuestManager重绑执行。需自然资源就绪及必要map/引用/ERROR核对后才允许2B；装备最终属性恢复前不得把临时零能量覆盖保存值。未构建/部署。

2026-11-07限定离线回归：独立Mongo/运行目录下实际50套875项全过，0失败/错误/跳过，覆盖既有账号/经济/保存/Lua及有限协议选集并含网络43项。新目标Chapter模式真实303启动/保存2项另计，BSON/新Player冷读、loadedAll=false下真实manager拒绝保持，未做正向重绑或客户端。两个实例均正常shutdown，原27017及数据不动，303库/BSON保留。首轮相对输入漏接导致41路径失败原样留存，仅修隔离驱动后重跑。证据及范围见[离线执行](CLIENT_72_SOL_TERRA_OFFLINE.md)、[章节](CLIENT_72_D02_CHAPTER_LOAD.md)和HANDOFF2；本轮没有存档格式或账号生产实现变更。

2026-11-06 F-03d2c2实际回归：新独立Mongo27027及独立运行目录下47套829项Java全过，失败/错误/跳过均0，覆盖账号、经济、保存、物品恢复及Lua。ItemUseRecoveryTest仅适配库存返回类型的四个getter，原ID/GUID/数量断言保持。关闭前测试库仅admin/config/local并正常shutdown，原27017和数据保留。库存11份真实Java包另经目标原生分段消费验证，仍不是实机验收；完整P0/SDK/资源不足未部署，收口后按最新要求暂停，见[库存接入](CLIENT_72_INVENTORY_PRODUCTION.md)、Project及HANDOFF2。下方旧数字和执行状态属于历史。

2026-11-06本轮实际验证：F-02c 11原生/32关联、F-04b 14原生/23次key验签及18关联，完整496研究均通过；E-01c6 32 Lua/8加载审计及两轮整场通过限定合同。没有执行账号数据库测试、启动服务或客户端；前批43套761 Java不是本轮重跑。研究中的三次入口调用/本地root签名输入不能作为真实登录或信任加载验收。完整初始化/SDK/补丁/资源仍缺，不部署猜测协议，持续目标及下一项见Project。

2026-11-06 F-02b/F-03中间验收已完成：38套Java选集无失败/错误，覆盖本轮协议接入及账号/经济/保存/Lua关联回归；不是完整客户端初始化或D-02验收。登录生产schema为21类198字段（LoginReq55、两Rsp外层85、18个raw嵌套58）；独立50组native writer、34组response native graph及6组错误拒绝通过，4项Java独立wire回归覆盖嵌套显式零、false、空字符串及重复消息合并。生成/核对入口见[登录wire](CLIENT_72_LOGIN_WIRE.md)，存在性适配范围见[对象图](CLIENT_72_GRAPH_READERS.md)。

独立Mongo下真实JCA RSA交换成功与非法Base64 `%%`、错误RSA密文`AQ==`失败共3项通过，实际调用认证后处理、稳定UID建档及确认保存；NoLoadPlayer只隔离完整资源加载，密钥由夹具临时生成，不代表客户端签名信任或实际认证通过。成功包仅uid/token/sign/server_rand_key，失败仅retcode且发送时仍用dispatch key，失败不安装会话key、不产生伪签名回退。另有入口回归证明加密模式key_id缺失/0/非正值在AccountLoginAdmission、创建及加载玩家前拒绝；已进入认证准备后的RSA失败不能套用该无副作用结论。既有账号准入、保存、经济Guard及Lua未绕行。

首跑6个失败已保留原始结果：2项旧登录/重置夹具沿用未显式关闭的加密配置，3项RSA测试把准备阶段全部发包数误当作唯一Token响应数，1项旧基线仍断言已退役场景包号。修正仅为夹具恢复原有非加密测试前提、按opcode筛选Token响应以及移除已迁移协议的旧断言；真实RSA三项显式启用加密，未通过修改生产准入或跳过校验消除失败。首跑及复跑结果留在忽略目录`local/migration72/f02b-f03-java-test-results`、`f02b-f03-retry-java-test-results`。

本轮F-03四组握手/请求token关联和StoreWeight仅覆盖已证字段，见[场景握手](CLIENT_72_SCENE_HANDSHAKE.md)、[请求省略边界](CLIENT_72_SCENE_REQUEST_CONTRACT.md)、[StoreWeight](CLIENT_72_STORE_WEIGHT_CONTRACT.md)。初始化Notify仍在核实，LoginRsp连续收帧key selector未证；SDK/信任/补丁/资源和实机D-02前置不能由测试通过替代。以下F-02a及A系列段落保留各批次的测试数字和交接状态，不作为当前暂停/阻塞状态。

2026-11-06融合验证：独立`local/migration72/test-mongo-fusion`/27027执行29套589项账号/经济/保存/Lua选集，全无失败/错误/跳过；正常shutdown前仅admin/config/local，测试库自清理且原Mongo27017/原数据未动。另43协议及关联全过，完整Java历史失败未重跑/消除。F-02a便携构建成功但完整P0/SDK/补丁/资源前置未齐，未部署、健康/运行JAR无服务对象，D-02仍未验；见[融合记录](CLIENT_72_ASTAPS_FUSION.md)。限定验收后按用户要求暂停，不用账号创建或读档入口做只读探测。

本手册用于接手后的只读排查、备份恢复和修改验证。系统原理见[接手指南](ACCOUNT_AND_SAVE_SYSTEM.md)，字段归属见[数据模型](ACCOUNT_AND_SAVE_DATA_MODEL.md)。核对日期为 2026-10-21，源码基线为 `63f0fa7923ddd68c9c62e0dfb8cbcbc756d07371`。

文中的备份、恢复、重置、删除和测试命令是操作模板，本次文档分析没有执行这些数据变更。模板包含占位路径时必须先替换并检查目标。

新增分区连续链6组、182项研究回归及原Token5组重跑通过；SDK导出/日志sink未初始化、容器/OS输入为明确离线夹具，不连接数据库或实际SDK，不替代D-02验收。复现见会话研究A-04d5n4。

第二分区协程新增8组原生样本、187项研究回归；state2/3是明确恢复夹具，传输错误仅验到错误分支入口，不代表实机网络重试或账号登录通过。

Handler尾段新增7原生样本、198研究回归，包含SHA1 55/56/64字节边界和坏签名不改后续全局状态；编码已核实为ASCII而非UTF-8。仅离线研究，未改运行Java/Proto/配置/补丁/资源，无部署重启；见会话研究A-04d5p。

A-04d5q共9原生样本、204研究回归及重复产物通过。三成功样本到封包入口，五条件分支在已安装密钥后停止但不发送Login，坏签名在安装前拒绝；8组密钥与Java逐字节相等。研究校验0/ready1（213项），无实际服务/数据库/客户端操作。本项完成后按用户要求暂停并交接。

最新执行状态以Project为准：用户2026-11-06要求A-03a完成后暂停交接。A-04d5w（6db47fe）10原生样本、240研究回归与重复产物通过，完整Login负载与独立protoc一致，9组key与Java一致；A-03a新增源码索引后248研究回归通过。无账号/保存运行代码、数据库或部署改动。动态启动另见CLIENT_72_DYNAMIC_DEBUG：观察到缓存ABI但最后目标退出，原因未知，无登录入场验收。恢复入口见HANDOFF2；用户恢复前不启动下一任务。

## 本机运行约定

key_id研究新增3组原生请求与6组JCA响应对照、192项研究回归：正确key4的会话密钥与Java逐字节相等，错误key2/3/5原生拒绝且不安装密钥。复现见会话研究A-04d5o；仅离线工具/研究映射变化，没有启动客户端、连接数据库或部署运行服务。

以 [AGENTS.md](../AGENTS.md) 为准：使用当前checkout和本机`GAME_PATH`，启动前核对`.env`、`.env.local`、`config.json`及进程/端口归属。当前国服7.2分支使用Windows便携方式（`LUNAGC_RUNTIME=portable`），不部署Docker/WSL或启用虚拟化，不沿用历史路径/PID。

| 入口 | 用途与注意点 |
| --- | --- |
| [Start-GS.cmd](../Start-GS.cmd) | 启动便携 MongoDB 和游戏服务；已有服务时先检查健康 |
| [Stop-GS.cmd](../Stop-GS.cmd) | 经 GM `stop` 正常停止游戏服务，等待退出，超时不强杀；MongoDB 保持运行 |
| [GS-Status.cmd](../GS-Status.cmd) | 查看服务、数据库及补丁状态；非交互检查可直接调用脚本的 status 模式 |
| [Play-GS.cmd](../Play-GS.cmd) | 启动服务、安装补丁并启动游戏，会产生实际运行动作 |
| [Open-GM.cmd](../Open-GM.cmd) | 打开已授权管理页面，会在必要时启动服务；不适合作为“只看状态”的入口 |
| [Rebuild-GS.cmd](../Rebuild-GS.cmd) | 服务已停时使用便携 JDK 从源码构建 |
| `task.cmd serve` | 按当前 runtime 选择更新方式；本机 portable 路径应保持不变 |

HTTP/SDK 8088、MongoDB 27017等固定端口须按当前源码核查；游戏UDP基线为22101。不能仅改环境变量声称隔离，也不能仅凭端口被占用就认为运行的是此项目。

`tools/local_windows.ps1` 启动时保存 `local/running.json`，包含进程 ID、JAR 路径和启动时 SHA-256。文件中的哈希只有与当前进程身份相符时才有意义；磁盘 JAR 后来被覆盖，不代表正在运行的 JVM 已加载新内容。

7.2离线Token/响应密码学及SDK认证来源工具的复现入口见[Harness](../Harness.md)和[会话研究](CLIENT_72_SESSION.md)。认证来源9组覆盖正常/可选字段、uint32上界/溢出和无效输入；使用虚构账号/令牌及明确JSON/SDK夹具，不执行真实认证或PlayerPrefs持久写入。这些工具不连接数据库，不替代新旧号、选人、场景和退出重登的D-02实机验收；已有mongodata和运行数据须保留。

## 只读排查的顺序

1. 读取当前分支、工作区、AGENTS 和 runtime 配置，确认处于预期项目。
2. 确认服务与数据库的监听进程、健康接口、队列失败趋势及近期错误。
3. 从账号 ID 找玩家 UID，再核对稳定归属；避免直接用显示昵称或预留 UID 当主键。
4. 用原始 MongoDB 投影定位对应集合、版本、恢复凭证与引用关系。
5. 对照具体 handler／manager 的加载与保存路径，判断是认证失败、数据未写入、数据被拒绝加载，还是客户端通知问题。

不要用完整登录、`getOrCreatePlayer`、家园自动创建、成就业务 getter、账号编号领取或 `save()` 来做只读探测。这些入口可能创建、迁移、保存或清理数据。

### 可直接使用的本机只读检查

在项目根目录的 PowerShell 执行：

```powershell
git status --short
git log -1 --oneline
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\local_windows.ps1 -Mode status
```

读取健康详情时只输出诊断字段：

```powershell
$gsConfig = Get-Content -LiteralPath .\config.json -Raw | ConvertFrom-Json
$gsHealthUrl = 'http://127.0.0.1:' + $gsConfig.server.http.bindPort + '/status/server'
$gsHealth = Invoke-RestMethod -Uri $gsHealthUrl -TimeoutSec 5
$gsHealth.status.health | ConvertTo-Json -Depth 8
```

服务停止时接口失败是预期状态。配置里有管理令牌，诊断报告不应输出整个 `config.json`、`.env` 或 GM 授权 URL。

### 队列字段怎么读

| 字段 | 含义 | 常见误判 |
| --- | --- | --- |
| `queued`、`capacity` | 等待队列长度与容量 | queued 为 0 仍可能有执行中写入 |
| `active` | 线程池工作线程中的活动任务 | 不包含所有提交线程执行或其他直接写入 |
| `outstanding` | 该队列尚未完成的已接收工作 | 结束可能是成功、失败或取消 |
| `failed` | 累计任务执行异常 | 健康正常不表示 failed 一定为 0；要比较增量 |
| `callerRuns` | 队满后由提交线程执行的累计次数 | 保存没有被丢弃，但业务线程可能变慢 |
| `coalesced` | 合并的保存请求数 | 不是丢档计数；只合并同对象同目标的等待任务 |
| `rejected`、`cancelled` | 拒绝或取消的累计次数 | 不能记为保存成功 |
| `shutdown` | 队列已停止接收 | 后续保存可能被拒绝 |

健康计算见 [RuntimeDiagnostics](../src/main/java/emu/grasscutter/server/game/RuntimeDiagnostics.java)：逻辑循环未停滞、无检测到的死锁、队列等待占用低于 80%。失败计数和一次真实数据库读写成功不在这个布尔表达式里。停机日志出现 `Database shutdown timed out with unfinished writes` 时，不能把进程退出称为已完整保存。

## 额外天赋显示锁定（2026-10-23）

角色 `avatars.proudSkillList` 同时保存普通被动和额外天赋。仅检查该列表及任务完成记录，不能确认客户端已收到解锁状态：`AvatarInfo`、`ShowAvatarInfo`、`SceneAvatarInfo` 和 `AvatarSkillDepotChangeNotify` 必须填写专用 `special_proud_skill_list`。当前发送逻辑从所选技能库的特殊被动组与角色已拥有被动的交集生成该列表，保持存档与战斗被动列表不变。

魔导话题表不包含所有额外天赋（例如八重神子“被祈之证言”），应同时核对 `AvatarSkillDepotExcelConfigData.specialProudSkillOpens`。回归测试 `AvatarSpecialProudSkillPacketTest` 覆盖隔离数据库重载后的登录、展示、场景及技能库切换包；真实客户端天赋界面的锁定提示仍需游戏内复核。

客户端会把两种被动合并到按技能组编号索引的字典，因此两种发送列表必须互斥。实机曾在同时发送八重神子 `585101` 到两个列表时报告 `ArgumentException: An item with the same key has already been added. Key: 5851`，随后天赋界面空白。所有上述快照及普通被动变化通知 `ProudSkillChangeNotify` 现在使用专门的普通被动投影，排除额外天赋；战斗逻辑仍读取完整的 `proudSkillList`。测试同时验证客户端合并时无重复组、普通/额外天赋完整、突破通知不会重新引入重复、内部存档集合未改变。

## MongoDB 只读查询模板

以下是 **mongosh JavaScript**，不是 PowerShell。需先连接已核对的 MongoDB，并把库名、账号 ID 和 UID 换成实际目标。查询本身不调用游戏加载回调，不输出密码或登录令牌。在线状态不断变化时，各条查询不构成跨集合一致快照。

```javascript
// 示例名称；分库时两个名称可能不同。
const gsAccounts = db.getSiblingDB("grasscutter");
const gsGame = db.getSiblingDB("grasscutter");
const gsAccountId = "12001";
const gsUid = 23001;

gsAccounts.accounts.find(
  { _id: gsAccountId },
  { _id: 1, reservedPlayerId: 1, passwordRequired: 1,
    isBanned: 1, banEndTime: 1, "saveStamp.revision": 1 }
).toArray();
gsAccounts.account_uid_owners.find({ _id: gsAccountId }).toArray();
gsGame.player_uid_reservations.find({ _id: gsUid }).toArray();
gsGame.players.find(
  { accountId: gsAccountId },
  { _id: 1, accountId: 1, sceneId: 1, "saveStamp.revision": 1 }
).toArray();

gsGame.avatars.countDocuments({ ownerId: gsUid });
gsGame.items.countDocuments({ ownerId: gsUid, deleted: { $ne: true }, count: { $gt: 0 } });
gsGame.items.countDocuments({ ownerId: gsUid, deleted: true });
gsGame.items.countDocuments({ ownerId: gsUid, deleted: true, count: { $gt: 0 } });
gsGame.activities.find(
  { uid: gsUid },
  { _id: 1, activityId: 1, scheduleId: 1, "saveStamp.revision": 1 }
).toArray();
gsGame.counters.find({ _id: { $in: ["Account", "Player"] } }).toArray();
gsGame.mailCounters.find({ _id: "sequence" }).toArray();
```

核对唯一性和索引：

```javascript
gsAccounts.accounts.getIndexes();
gsAccounts.account_uid_owners.getIndexes();
gsGame.player_uid_reservations.getIndexes();
gsGame.players.getIndexes();
gsGame.items.getIndexes();

// 仅在需要全库审计时运行，较大数据库应考虑查询负载。
gsGame.players.aggregate([
  { $group: { _id: "$accountId", count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } }
]).toArray();
gsGame.activities.aggregate([
  { $group: { _id: { uid: "$uid", activityId: "$activityId" }, count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } }
]).toArray();
```

查询结果含玩家标识和归属元数据，按本机资料处理。对外报告只保留必要的脱敏关系和统计。

## 备份方案

### 必须成套保留什么

- 账号和游戏数据库的完整集合、原始 BSON 类型、索引及两库对应关系。必须包括计数器、UID 归属、删除物品和所有恢复凭证。
- `local/data/` 的运行数据；如果 `folderStructure.data` 被修改，则备份它实际指向的目录。
- 本机配置 `.env`、存在时的 `.env.local`、`config.json`，以及启用的插件和自定义密钥。含敏感内容的备份只在受控本地／备份介质保存，不提交 Git。
- 源码提交、资源子模块提交、实际 JAR SHA-256、Java/MongoDB 版本、备份时间与时区、备份方式、各库和集合数量。
- 用于核对异常的脱敏日志及文件清单。Git 只保存代码和文档，无法替代数据库备份。

同盘的 `local/backups/` 适合临时导出，不抵御整盘故障。验证后的备份还应保存在独立介质，并保留多个时间点。

### 方案一 停止游戏写入后逻辑备份

适合当前便携单机：正常停止游戏服务，确认没有其他写入者，保留 MongoDB 运行，再用 Database Tools 导出完整数据库。官方说明指出，导出期间仍有写入时，普通 dump 可能不对应一个一致时间点；此处通过停止应用写入控制该问题。[MongoDB 备份示例](https://www.mongodb.com/docs/database-tools/mongodump/mongodump-examples/)

步骤：

1. 退出游戏，运行 `Stop-GS.cmd`，确认服务进程退出并检查保存失败／排空超时日志。分离部署时停止所有会写这些库的账号服、游戏服和工具。
2. 确认 MongoDB 仍为本项目实例，数据库名称与路由正确；确认 `mongodump.exe` 可用。便携 `mongod.exe` 存在不代表已安装 Database Tools 或 mongosh。
3. 向新的、不覆盖旧备份的目录导出。下面模板适用于已核对的本机同库 HYBRID；分库需分别导出，两份都成功才构成一组备份。

```powershell
# 操作模板：先替换工具路径，并完成上述停写检查。
$gsDumpExe = '<MongoDB Database Tools 的绝对目录>\mongodump.exe'
if (-not (Test-Path -LiteralPath $gsDumpExe)) { throw 'Backup tool path is not configured.' }
$gsBackupRoot = Join-Path (Get-Location).Path ('local\backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $gsBackupRoot) { throw 'Use a new backup directory.' }
New-Item -ItemType Directory -Path $gsBackupRoot | Out-Null
$gsArchive = Join-Path $gsBackupRoot 'grasscutter.archive.gz'
& $gsDumpExe --uri='mongodb://127.0.0.1:27017' --db=grasscutter --gzip "--archive=$gsArchive"
if ($LASTEXITCODE -ne 0) { throw 'Database backup failed; retain the output for diagnosis.' }
Get-FileHash -LiteralPath $gsArchive -Algorithm SHA256
```

4. 在同一停写窗口复制运行数据和配置到该备份组，记录清单和校验值。不要把源文件存在视为复制成功。
5. 检查每条工具的退出码和错误输出。数据库导出正常不代表配置、资源版本和运行数据已一起保留。
6. 恢复应用服务并检查健康；在隔离环境做恢复演练后，才能将备份标记为“已验证可恢复”。

`--archive` 和 `--gzip` 的组合保存压缩归档，避免用普通 JSON 导出代替完整 BSON 备份。当前本机启动脚本未启用副本集，不应直接套用依赖副本集的 `--oplog` 方案。[归档与压缩示例](https://www.mongodb.com/docs/database-tools/mongodump/mongodump-examples/)

### 方案二 彻底停库后物理冷备

正常停服并排空后，还要使用适合当前 MongoDB 的管理方式**干净关闭该数据库实例**，确认对应进程已退出，再复制整个 `mongodata/` 和配套运行数据。不要强杀，不要仅删除锁文件，也不要在 MongoDB 仍运行时逐文件复制并宣称是冷备。

直接复制多个文件不是原子快照，官方要求复制前停止写入。采用“数据库已干净退出”的条件可以避免本机普通文件复制期间仍发生数据库内部变化。[MongoDB 文件备份说明](https://www.mongodb.com/docs/manual/core/backups/)

当前启动／停止入口没有“一键冷备”功能。物理恢复需要兼容的 MongoDB 版本和完整目录，优先在独立目录、独立端口验证。不要把一批旧 WiredTiger 文件混入仍有新文件的正式目录。

## 恢复和迁移

### 先恢复到隔离目标

恢复前保留现状备份，明确要回到哪个时间点以及哪些账号受到影响。正式恢复会改变进度，并可能恢复备份里的旧密码、旧令牌和旧权限；需要把认证状态一起纳入恢复后的检查。

使用新的独立 MongoDB 实例或经过确认的空测试数据库。下面只展示目标为独立测试端口 `27027`、新数据库名的演练模板；不要改成正式库直接覆盖。

```powershell
$gsRestoreExe = '<MongoDB Database Tools 的绝对目录>\mongorestore.exe'
$gsArchive = '<已经核对 SHA-256 的备份归档绝对路径>'
if (-not (Test-Path -LiteralPath $gsRestoreExe)) { throw 'Restore tool path is not configured.' }
if (-not (Test-Path -LiteralPath $gsArchive)) { throw 'Archive is missing.' }
# 先确认 27027 是独立测试实例，目标库不存在或为空。
$gsRestoreName = 'lunagc_restore_' + (Get-Date -Format 'yyyyMMddHHmmss')
& $gsRestoreExe --uri='mongodb://127.0.0.1:27027' --gzip "--archive=$gsArchive" --nsInclude='grasscutter.*' --nsFrom='grasscutter.*' "--nsTo=$gsRestoreName.*" --stopOnError
if ($LASTEXITCODE -ne 0) { throw 'Restore failed; retain the isolated target for diagnosis.' }
```

`--nsFrom/--nsTo` 用于更换数据库命名空间；`--stopOnError` 可避免默认继续处理重复键等错误。还应核对恢复日志中的文档数量和索引结果，避免“没有匹配数据但命令退出”被误认成功。工具与 MongoDB 主版本／FCV 的兼容要求以实际版本文档为准。[mongorestore 说明](https://www.mongodb.com/docs/database-tools/mongorestore/)

恢复完成后依次核对：

1. 集合清单、文档数量、索引和 BSON 类型与备份清单一致。
2. `accounts` → `players` → 所属分表关系正确，两份 UID 归属记录吻合。
3. 计数器没有被回退或改成错误类型，邮件编号和投递键保留。
4. 物品删除记录、未完成计划、任务／活动排期和家园制作记录都在。
5. 使用匹配的源码、JAR、资源和运行数据，在独立服务配置中验证读取；此阶段的登录可能写入隔离库。
6. 在测试账号上检查背包、角色、位置、任务、家园、活动、制作队列；退出后再次加载，核对实际持久结果。

分库部署应恢复账号库和游戏库整套关系，并在独立服务中同时配置它们。不要让测试服务的其中一份配置仍指向正式库。

### 单账号恢复不能只复制 players

至少要按数据字典处理全部所属集合、双向好友、UID 归属与原实例 ID，并评估该玩家在其他玩家数据中的引用。物品付款和奖励凭证也必须来自一致时间点。

例如只回滚 `players` 而保留新 `items`，可能使“已付款”的凭证与库存不一致；只回滚 `items` 又可能使已领取奖励重复出现。把整库备份恢复到隔离库做差异分析，再制定字段／集合级恢复方案，比直接覆盖一份玩家文档更容易核对。

正式切换前要停止写入并保留回退副本；切换后按当前约定验证服务、账号归属和游戏内状态。不要用在线 raw update 绕过 Guard，也不要保留旧 JVM 中的玩家副本继续写新恢复的数据。

## 账号管理入口

内置 `account` 命令只允许控制台身份，游戏内玩家直接调用会被拒绝。GM 的命令接口能以控制台方式执行时，可使用相同语法；管理访问凭据与游戏账号密码分开。

| 命令 | 条件与效果 |
| --- | --- |
| `account list` | 列出账号和关联 UID；内部可能加载玩家对象，不作为纯原始 BSON 审计 |
| `account create <username> [UID]` | 全局实验密码开关关闭时；确认创建，授予 `*` 权限 |
| `account create <username> <password> [UID]` | 实验密码开关开启时；初始 BCrypt 散列和权限一起插入 |
| `account resetpass <username> <password>` | 命令要求实验密码开关开启；调用正式重置服务，清除旧 token 并启用账号级密码要求 |
| `account delete <username>` | 删除账号及归属玩家进度，保留 UID 身份元数据；不是“重置密码”或普通修复手段 |

密码不应写入提交、截图或共享日志。若因故障收到重置超时，先核对实际凭据和撤销结果；再次执行命令是新操作，不等于确认原先未完成的远端尝试。

## 恢复命令索引

控制台可按通用命令目标语法加入 `@UID`，例如 `itemuse @23001 status`；此处 UID 仅为示例。目标玩家是否需要在线／已加载场景，以命令和业务实现为准，不要承诺这些命令可以修复任意离线 BSON。

| 业务 | 先检查 | 可能的恢复操作 | 重要限制 |
| --- | --- | --- | --- |
| 物品使用与学习 | `itemuse status` | `resume/cancel <operationId>`；食物另有 `refund-cost/settle <operationId>` | 退库存成本不回滚已发生的所有游戏效果 |
| 活动货币 | `activitycoins pending`、`audit <activityId> <operationId>` | `resume/cancel <activityId> <operationId>` | 依据原计划和余额核对，不直接加减余额 |
| 活动领奖 | `activityrewards pending`、`audit <activityId> <operationId>` | `resume/cancel <activityId> <operationId>` | 整批计划不意味着所有奖励都已到账 |
| 神像供奉 | `statue audit <cityId>` | `resume/cancel <cityId> <operationId>` | 与 `level/reset` 管理改值不同；命令要求有效场景 |
| 圣遗物定向 | `transmuter status` | `resume/cancel <operationId>` | 根据原材料、预留实例和进度恢复 |
| TPS | `tps pending`、`audit <operationId>` | `resume/cancel <operationId>` | 报告和补给有各自阶段，不能用 refill 代替核对 |

以上命令都不代表任意阶段可以取消。锻造、加工、家具、邮件等还应进入对应专题，不能凭空套用同名恢复子命令。

## 常见症状与定位

| 症状 | 优先检查 | 避免的误操作 |
| --- | --- | --- |
| 用户名能登录但进入新号 | 当前账号库／游戏库、账号字符串 ID、`players.accountId`、UID 预留 | 立刻创建同名账号或手改 UID |
| 改密码后旧凭据还能走某入口 | 入口类别、`passwordRequired`、令牌写入确认、连接代次和撤销结果 | 只改 `password` 字段就称已撤销所有登录 |
| 首次建档失败／编号跳号 | 计数器、唯一索引、两份归属记录、并发首登异常 | 重置计数器、删除预留、复用空号 |
| 重连后背包缺失 | `items.ownerId`、deleted/count、资源定义、加载错误 | 根据 UI 直接补发，或只查看 players |
| 存档提示 stale／missing | 原对象 stamp、是否已有新副本写入、是否被删除、目标 Datastore | 给旧对象安装库里的新版本后强行保存 |
| 保存确认超时 | 库中当前 stamp 和业务凭证，网络／数据库错误 | 假定请求没执行并重复扣费／发奖 |
| 制作／领奖卡住 | pending 凭证、成本、产物实例与当前阶段 | 清空 pending、删除零数量物品、重复发奖 |
| 任务似乎回退 | quests、player 全局变量、group 状态、资源和 suite 绑定 | 仅改 isFinished 或发送全完成通知 |
| 开始游戏后白屏并反复提示重连 | `HandlerPlayerLoginReq` 异常、任务 Excel 与章节子任务集合、`quests.childQuests`；见[任务登录恢复](QUEST_LOGIN_ACCEPTANCE_2026-10-21.md) | 用 healthy=true 证明登录成功，或删除任务存档绕过异常 |
| 掉线后位置异常 | 副本返回点、场景资源、登录修复分支 | 将所有副本／大世界位置统一重置 |
| 家园没布局 | homes 的 ownerUid、sceneMap/mainHouseMap、资源兼容 | 覆盖或重新创建原家园文档 |
| 健康正常但出现保存失败 | failed/rejected 增量、保存异常、Mongo 可用性、版本冲突 | 仅看 healthy=true 就关闭调查 |
| 停服长时间未退出 | 逻辑线程、写入 outstanding、停机超时日志 | 立即强杀并报告已保存 |
| 升级后出现索引错误 | 旧索引定义、重复业务键、启动索引处理 | 未备份便删除索引／重复记录 |

## 开发与回归测试

### 按改动范围选择现有测试

测试目录：[数据库](../src/test/java/emu/grasscutter/database)、[账号与玩法](../src/test/java/emu/grasscutter/game)、[Dispatch](../src/test/java/emu/grasscutter/server/dispatch)、[玩法回归](../src/test/java/io/grasscutter)。

| 改动范围 | 主要测试类 |
| --- | --- |
| 创建／编号／归属 | `AccountCreationMongoTest`、`PlayerUidReservationMongoTest` |
| 账号版本／确认丢失 | `AccountSaveFenceMongoTest`、`AccountSaveGuardRecoveryTest` |
| 密码／删除 | `AccountPasswordResetMongoTest`、`PasswordResetAuthenticationTest`、`AccountDeletionMongoTest` |
| 本机登录准入／账号刷新 | `LocalLoginAdmissionMongoTest`、`PlayerAccountRefreshMongoTest` |
| 分离账号／请求匹配 | `DispatchRemoteAccountTest`、`DispatchRequestCorrelationTest`、`DispatchPeerRequestsTest`、`DispatchAccountRefreshTest`、`DispatchJsonDecodeTest` |
| 跨服撤销 | `CredentialRevocationTest`、`CredentialRevocationProcessTest`；后者覆盖实际子 JVM 和连接时序 |
| 队列／维护／派生写入 | `DatabaseWriteQueueTest`、`DatabaseWriteBarrierTest`、`DatabaseWriteFenceTest`、`DatabaseQueueIntegrationTest`、`DatabaseDatastoreScopeTest` |
| 玩家版本／旧副本 | `PlayerSaveFenceMongoTest`、`PlayerSaveGuardRecoveryTest` |
| 物品与活动版本 | `ItemSaveFenceMongoTest`、`ActivitySaveFenceMongoTest`、`ActivitySaveGuardRecoveryTest` |
| 退出与角色状态 | `PlayerLogoutIsolationTest`、`AvatarVitalsPersistenceTest` |
| 玩法经济恢复 | 对应业务的 Mongo／Persistence／Recovery 套件，以及[真实保存夹具说明](WORKFLOW_PERSISTENCE_TESTS_2026-10-17.md) |

多数 Mongo 测试要求 `LUNAGC_TEST_MONGO_URI`，未设置时会跳过；`DatabaseDatastoreScopeTest` 还有缺省连 localhost:27017 的路径。因此在运行涉及数据库的测试前，必须显式配置**独立测试实例**，不能把环境变量留空就认为不会接触本机数据库。第二实例测试使用 `LUNAGC_TEST_MONGO_SECONDARY_URI`，不提供时部分路径会退回第一实例，不能宣称覆盖了两台独立实例。

现有真实 Mongo 夹具一般创建 UUID 命名测试库并在结束后 drop。不要把夹具改成固定正式数据库名；测试结束后也要核对残留。故障注入必须区分“写入前失败”和“实际写入后确认丢失”，不能只把保存函数替换成空成功。

### 便携测试命令模板

先准备独立便携 MongoDB 测试实例及独立 dbpath，不启用 Docker。下面假设测试实例已在 27027 运行；它不负责启动该实例。

```powershell
$gsJdk = Get-ChildItem -LiteralPath .\local\runtime -Directory |
    Where-Object Name -Like 'jdk-17*' | Sort-Object Name -Descending | Select-Object -First 1
if (-not $gsJdk) { throw 'Portable JDK 17 is missing.' }
$env:JAVA_HOME = $gsJdk.FullName
$env:PATH = (Join-Path $gsJdk.FullName 'bin') + ';' + $env:PATH
$env:GRADLE_USER_HOME = Join-Path (Get-Location).Path 'local\gradle'
$env:LUNAGC_TEST_MONGO_URI = 'mongodb://127.0.0.1:27027'
& .\gradlew.bat --no-daemon test -PskipHandbook=1 --tests '*AccountSaveFenceMongoTest' --tests '*PlayerSaveFenceMongoTest' --tests '*ItemSaveFenceMongoTest' --tests '*DatabaseWriteQueueTest' --tests '*DatabaseWriteBarrierTest' --tests '*DatabaseWriteFenceTest'
if ($LASTEXITCODE -ne 0) { throw 'Targeted tests failed.' }
```

示例只是保存基础设施的选集，应按上表增补受改动影响的测试。结果在 `build/test-results/test/` 和 `build/reports/tests/test/`；同时检查失败、错误和跳过数。历史专题中的通过数量是当批证据，不是本次执行结果。

普通 `test` 排除了启动真实 HTTP 服务的 `GrasscutterTest`；它由 `serverSmokeTest` 单独执行，`check` 依赖该任务。运行服务冒烟前还要检查其配置、端口及数据库目标，不能在未知配置下直接启动第二个服务。

### 新修改必须回答的问题

1. 修改的是内存表现、持久字段、协议通知，还是它们之间的顺序？明确不变量和验收点。
2. 是否影响旧档加载、首次插入、重复请求、已有副本、删档后的旧对象？
3. 是否跨玩家、物品、活动或家园文档？若是，失败后靠什么凭证判断成本和奖励？
4. 是否在逻辑线程等待数据库或远端 RPC？是否违反维护屏障或同对象锁的顺序？
5. 是否新增集合／字段？同步更新删除清单、数据字典和备份范围。
6. 测试是否冷读真实 BSON，是否覆盖写入后丢确认，而不仅仅验证内存值？

收到具体故障后先复现并锁定，再修复。新发现但不在本次修改范围的风险明确登记；不要一边编写说明，一边静默改变真实账号或认证策略。

## 交付与运行环境更新

按本机约定，纯文档或测试改动无需重建／重启。改动服务器源码、运行配置或内置资源时，测试通过之后仍要完成便携构建和运行更新：

1. 正常停止旧服务，保留数据库和运行数据，确认排空结果。
2. 使用现有便携构建入口构建当前源码；或使用已核对为 portable 的 `task.cmd serve` 完成更新。
3. 启动新构建，核对健康、日志、HTTP／UDP 监听进程以及启动记录中的 JAR SHA-256。
4. 核对磁盘候选 JAR 与运行进程对应的构建一致，不能只报告“生成了 JAR”。
5. 说明仍需游戏内确认的操作，尤其是首次登录、重连、领奖／制作及退出后的冷读。
6. 及时 Git 提交并推送，仅暂存预期的代码／文档；不提交本机配置、凭据、日志、依赖、数据库或备份。

## 可交给下一个 AI 的任务说明

将下面文字与具体需求一同交给接手 AI：

```text
请先读取 AGENTS.md、docs/ACCOUNT_AND_SAVE_SYSTEM.md、
docs/ACCOUNT_AND_SAVE_DATA_MODEL.md 和 docs/ACCOUNT_AND_SAVE_OPERATIONS.md。
本机使用 Windows portable；保持当前项目和游戏路径，不启用 Docker 或 WSL。
先记录当前源码版本和工作区，再追踪本次需求涉及的登录或保存入口。
区分 Account ID、Player UID、持久实例 ID 和运行时 GUID。
区分异步入队、同步确认、版本条件、业务凭证与跨文档一致性。
读取真实数据时优先使用原始 BSON 投影，保护密码、令牌及本机配置。
不要重置计数器、释放 UID 归属、清空 pending 或物理删除物品墓碑来绕过错误。
具体故障先在隔离测试库复现，特别覆盖旧副本和实际写入后确认丢失。
修改运行内容后完成便携构建、正常更新和运行 JAR 校验；纯文档无需重启。
同步更新相关文档，提交并推送，说明验证结果和仍需游戏内确认的部分。
本次具体需求：在此填入要修复或实现的行为及可观察的验收条件。
```

2026-11-06 V-01b：家具两套夹具由save-only代理改为独立Mongo真实Player/Item Guard及GameHome保存，故障计数只消费同步确认；测试专用home/库存/回调不入BSON。原31+34=65项通过（失败/错误/跳过均0），六阶段故障、防重复扣费、队列/回执和模糊确认恢复断言保留；新增材料及家园冷读。复现前家具65项中40失败，原始报告fixture-before-test-results/，修复后furniture-fixture-test-results/与logs/furniture-fixture-after.log（均在local/migration72）。只改测试，无生产/Proto/配置变更，无部署或客户端验收；V-01完整集尚未重跑，不推算剩余失败数量。

2026-11-06 V-01c：加工手册夹具改用真实Player/Item Guard及隔离Mongo，保留原18项断言和同步故障阶段；ProcessPlayer专用库存/标志/回调排除出BSON。新增真实手册进度、产物、未重复扣原料及失败后预留冷读，明确内存进度1而失败确认后的持久进度0，重试仍拒绝重复发放。修复前18项中12失败，现18项全过；与加工队列35、家具96、Player/Item保存保护45、手册领奖27合计221项通过，失败/错误/跳过均0，证据workflow-fixture-test-results/、evidence/workflow-fixture-summary.json及logs/workflow-fixture-regression.log（local/migration72）。原断言未删未放宽；未改生产逻辑，无部署/实机验收。完整Java集未重跑，历史217失败不据此扣减。

2026-11-06 A-03c限定离线验收完成，见[候选消息/Proto与三层缺口矩阵](CLIENT_72_LOGIN_PROTO_MATRIX.md)：97包构造、148引用/种子、1139文件描述符依赖，缺DoSetPlayerBornDataNotify.proto如实保留。原三层状态及185字段不变；6新增/260研究回归、重复矩阵通过，研究校验0/ready1（213缺口）。V-01b/c经济/保存221项通过；完整Java集未重跑。按用户要求到此停止并交接Astra，下一研究为B-02选人三件组的独立7.2三层证据；A-03整体仍待完整闭包/实机。未改运行文件、未部署或调试。

### 7.2选人研究补充（2026-11-06，B-02d）

三件组目标号及字段已限定离线验证，见[CLIENT_72_BORN](CLIENT_72_BORN.md)的完整实现/回归规格。通知5008、请求8541（nick_name4/avatar_id11）、响应414（retcode15）。仅更新研究映射，没有修改账号、持久数据模型、Guard、创建/保存逻辑；后续实现需保留入口业务校验、重复创建和保存回归。当前39原生样本与266研究回归不代表真实选人/创建角色/存档验收；本批未重跑Java经济保存集，未部署。

V-01d（2026-11-06）：扩展Guard选集实际467项中1项失败，原因是FurnitureStartFlowTest默认读取/resources。只调整此测试入口路径：LUNAGC_TEST_RESOURCES优先，其次LUNAGC_RESOURCES_DIR，缺省当前checkout的resources；1396个合法/其他非法配方、扣费及容量断言保留。家具原31项和关联选集110项全部通过，无失败/错误/跳过。失败报告born-guard-before-test-results/与修后born-changed-test-results/、日志born-{guard-regression,changed-regression}.log均在local/migration72。完整Java集及FurnitureTalentPlanTest等其余V-01缺口未据此消除。

C-01a最终回归（2026-11-06）：独立便携Mongo测试实例、随机测试库；21套495项Java全部通过，失败/错误/跳过均0，其中包含原221项经济/保存选集、新19项选人、15项wire及原33项登录准入。新号、旧号冷读、重复幂等、凭据轮换、保存前失败/写后丢确认和半成品冷读均有验证；onLogin与欢迎邮件只观察继续入口，不是客户端/实际邮件落库验收。报告evidence/born-guard-final-summary.json和born-guard-final-test-results/，日志logs/born-guard-final.log，均在local/migration72。完整Java集未重跑；新运行代码需构建，但P0/SDK/补丁/资源前置未齐，不启动服务/游戏，未部署原因与最小后续取证见CLIENT_72_BORN。

2026-11-06 E-01c2回归：Lua序列化最小修复后，独立便携Mongo与随机测试库实际重跑前批21套495项账号/选人/经济/保存选集，并加入区域/脚本85项，共28套580项，失败/错误/跳过均0；没有改账号数据模型、认证或保存规则。测试收尾辅助JVM曾因fat JAR日志初始化卡住，已留线程栈并仅终止该辅助进程，修正辅助日志配置；测试Mongo正常关闭、同一目录复查仅admin/config/local，原数据保留。证据scene-serialization-java-regression-summary.json及scene-serialization-test-mongo-final.json在local/migration72；完整Java集未重跑，历史217失败保留。运行JAR已构建但7.2完整前置未齐不部署，D-02未完成。


2026-11-08 R2-LOG：真实 SessionKeyAuthenticator 四分支日志红绿及保存/登录校验通过；与前批日志和密码回归合计本轮39项全过，独立新27027/data/cache/packets/plugins，Mongo正常shutdown，原库和运行数据保留。红测、夹具编译首失败与准确复现范围见[认证日志合同](CLIENT_72_AUTH_LOGGING.md)；尚非本服SDK或D-02实机通过。


2026-11-08 R2-SESSION主线9套193项隔离回归及实际Java HTTP→固定原生parser/formatter三案例通过；原数据/配置保持，Mongo正常shutdown；自然SDK及D-02仍待验。见[session响应专题](CLIENT_72_SDK_SESSION_ADAPTER.md)。


2026-11-10 E-01/R3-ITEM-ENTRY局部保存：固定同源出生Weapon11101六键经真实loader、普通Item队列/Guard/Mongo和新Player真实Inventory冷恢复；主线独立库/JVM三测试通过，两件不同ObjectId、cold Guard重存revision2、缺定义保留与重绑、UID隔离及冷读零写成立。未改生产保存或账号规则；完整Player、角色装备、退出重登和D-02未验。独立Mongo正常关闭且原数据保护保持，准确命令/原件/范围见[局部库存实际验收](CLIENT_72_D02_ITEM_SAVE_JAVA_20261110.md)。
2026-11-11 [Talent六差异与当前Java](CLIENT_72_D02_OLD_TALENT_ENTRY_NULLS_20261111B.md)经隔离5项测试通过，固定AvatarTalents目录的720源键实际加载；六空值差异只涉及旧号非活动角色。未启Mongo、未读写原库、未执行完整Player/背包/postLoad或客户端重登，不能据此关闭保存恢复前置。
