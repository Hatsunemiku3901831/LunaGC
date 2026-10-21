# 账号与存档运维和开发手册

本手册用于接手后的只读排查、备份恢复和修改验证。系统原理见[接手指南](ACCOUNT_AND_SAVE_SYSTEM.md)，字段归属见[数据模型](ACCOUNT_AND_SAVE_DATA_MODEL.md)。核对日期为 2026-10-21，源码基线为 `63f0fa7923ddd68c9c62e0dfb8cbcbc756d07371`。

文中的备份、恢复、重置、删除和测试命令是操作模板，本次文档分析没有执行这些数据变更。模板包含占位路径时必须先替换并检查目标。

## 本机运行约定

以 [AGENTS.md](../AGENTS.md) 为准：项目在 `C:\GS`，游戏在 `C:\GAME\Genshi`，使用 Windows 便携方式，`.env` 中 `LUNAGC_RUNTIME=portable`。只有用户以后明确要求，才改用 Docker、WSL 或虚拟化部署。

| 入口 | 用途与注意点 |
| --- | --- |
| [Start-GS.cmd](../Start-GS.cmd) | 启动便携 MongoDB 和游戏服务；已有服务时先检查健康 |
| [Stop-GS.cmd](../Stop-GS.cmd) | 经 GM `stop` 正常停止游戏服务，等待退出，超时不强杀；MongoDB 保持运行 |
| [GS-Status.cmd](../GS-Status.cmd) | 查看服务、数据库及补丁状态；非交互检查可直接调用脚本的 status 模式 |
| [Play-GS.cmd](../Play-GS.cmd) | 启动服务、安装补丁并启动游戏，会产生实际运行动作 |
| [Open-GM.cmd](../Open-GM.cmd) | 打开已授权管理页面，会在必要时启动服务；不适合作为“只看状态”的入口 |
| [Rebuild-GS.cmd](../Rebuild-GS.cmd) | 服务已停时使用便携 JDK 从源码构建 |
| `task.cmd serve` | 按当前 runtime 选择更新方式；本机 portable 路径应保持不变 |

本机默认 HTTP 8088、游戏 UDP 22101、MongoDB 27017；HTTP 和游戏端口可由配置／环境覆盖。检查时必须确认监听进程身份，不能仅凭端口被占用就认为运行的是此项目。

`tools/local_windows.ps1` 启动时保存 `local/running.json`，包含进程 ID、JAR 路径和启动时 SHA-256。文件中的哈希只有与当前进程身份相符时才有意义；磁盘 JAR 后来被覆盖，不代表正在运行的 JVM 已加载新内容。

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
