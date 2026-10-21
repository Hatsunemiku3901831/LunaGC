# 深境螺旋排障验证与开发交接

> **开发增量**：新增开发和部署记录见[历史配置与管理工具](SPIRAL_ABYSS_MANAGEMENT.md)。本轮实际修改运行代码与 GM 页面，必须执行 portable 重建部署。新增 `TowerManagementTest`、`TowerRecordRegressionTest`、`TowerSpawnIntegrationTest`、`TowerResourceIntegrationTest`，并扩展召唤物清理测试；下方 227 项为前一轮文档审计的历史结果。

排查深渊时，先区分协议不可达、场景脚本问题、运行状态失配与存档／奖励问题，再选择测试。本文以[系统总览](SPIRAL_ABYSS_SYSTEM.md)的源码及资源基线为准，给出本机便携运行方式下的复现和交付要求。

## 本次验证结果

2026-10-21 使用便携 Java 17、独立 MongoDB 端口 27027 运行以下现有测试，共 **227 项、13 套件，失败 0、错误 0、跳过 0**。机器可读记录见[验证 JSON](data/tower-validation-2026-10-21.json)。

| 测试类 | 项数 | 主要保护范围 |
| --- | ---: | --- |
| `TowerTeamRecoveryTest` | 5 | 手工编码的选队 wire 字段、队伍顺序、空切队消息的构造 |
| `TowerPartySelectionTest` | 25 | 队伍数量、成员、重复、临时队伍选择和失败保护 |
| `TowerPartyPersistenceTest` | 18 | 队伍持久化、旧档兼容和不合法恢复 |
| `TowerPartyStableIdentityTest` | 8 | avatarId 重新绑定新 GUID、拒绝错误身份 |
| `TowerEntryOutcomeTest` | 12 | 传送失败／取消、成功响应时机、入口检查点 |
| `TowerChamberCheckpointTest` | 18 | 入口状态捕获、普通／特殊能量及恢复校验 |
| `TowerChamberRetryTest` | 22 | 重试准入、双队状态恢复、冷却和场景失败保护 |
| `TowerSceneHandoffTest` | 23 | 首入、换间、同 ID 新场景、单人和取消保护 |
| `TowerHalfClockTest` | 27 | 上下半剩余时间、零预算和 Lua 切队 |
| `TowerSettlementOrderTest` | 22 | Lua 回调先后、旧上下文拒绝、单工作线程与冻结星数 |
| `TowerEndNotificationTest` | 20 | 失败／退出、重复请求、异步结果与返程顺序 |
| `DungeonGroupPersistenceTest` | 5 | 普通持久组与深渊场景内组隔离 |
| `DungeonLoginRecoveryTest` | 22 | 副本返回来源及登录回到安全场景 |

测试结果不覆盖原生 UI、真实网络丢包／负 CmdId 过滤、全部 36 间实战、所有月度祝福、星级奖励领取、周期更新和经济故障注入。多数业务测试复用 `QuestTestFixture` 的隔离库和捕获包对象，不是真实客户端登录。

首次执行未设置 `LUNAGC_TEST_MONGO_URI`，fixture 在假设检查处跳过初始化，而部分子类的清理仍访问空状态，产生大量 NPE。配置独立测试库后重跑全部通过。后续看到类似“清理方法 NPE”时先检查测试前提，不要直接修改深渊业务来消除环境错误。

## 安全复现测试环境

本机使用 Windows 便携环境，不为这些测试部署 Docker 或 WSL。下面从项目根运行，在空闲 27027 端口创建独立测试数据库进程；不要把 `LUNAGC_TEST_MONGO_URI` 指向正式 27017。fixture 会创建并清理随机测试数据库。

```powershell
if (Get-NetTCPConnection -State Listen -LocalPort 27027 -ErrorAction SilentlyContinue) {
    throw '27027 is occupied; verify the owner before choosing a test port.'
}
$gsTestRoot = Join-Path (Get-Location).Path ('local\tower-test-' + [guid]::NewGuid().ToString('N'))
$gsTestDb = Join-Path $gsTestRoot 'mongo'
New-Item -ItemType Directory -Path $gsTestDb -Force | Out-Null
$gsMongo = Get-ChildItem -LiteralPath .\local\runtime -Filter mongod.exe -File -Recurse |
    Select-Object -First 1
if (-not $gsMongo) { throw 'Portable MongoDB is missing.' }
$gsTestProcess = Start-Process -FilePath $gsMongo.FullName -ArgumentList @(
    '--dbpath', $gsTestDb, '--port', '27027', '--bind_ip', '127.0.0.1',
    '--logpath', (Join-Path $gsTestRoot 'mongod.log'), '--logappend', '--quiet'
) -WindowStyle Hidden -PassThru
$gsTestProcess.Id
```

确认该进程已监听测试端口后，再运行：

```powershell
$gsJdk = Get-ChildItem -LiteralPath .\local\runtime -Directory |
    Where-Object Name -Like 'jdk-17*' | Sort-Object Name -Descending | Select-Object -First 1
if (-not $gsJdk) { throw 'Portable JDK 17 is missing.' }
$env:JAVA_HOME = $gsJdk.FullName
$env:PATH = (Join-Path $gsJdk.FullName 'bin') + ';' + $env:PATH
$env:GRADLE_USER_HOME = Join-Path (Get-Location).Path 'local\gradle'
$env:LUNAGC_TEST_MONGO_URI = 'mongodb://127.0.0.1:27027'
& .\gradlew.bat --no-daemon test -PskipHandbook=1 --console=plain --tests '*Tower*Test' --tests '*DungeonGroupPersistenceTest' --tests '*DungeonLoginRecoveryTest'
if ($LASTEXITCODE -ne 0) { throw 'Tower regression failed.' }
```

结果在 `build/test-results/test/` 和 `build/reports/tests/test/`。核对 XML 的 failures、errors、skipped，不能只搜索终端有没有“异常”字样；测试会有意触发无效配置和拒绝分支日志。

结束后，通过数据库管理客户端对**已核实属于本轮的 27027 测试实例**执行 admin `shutdown`，等待该 PID 和监听端口消失。不要用 `Stop-GS.cmd` 停正式游戏服来清理测试库，也不要删除 `mongodata` 或 `local/data`。若复用已存在的测试实例，必须先确认归属，不擅自停止他人的进程。

## 只读排查顺序

1. 阅读当前 [AGENTS.md](../AGENTS.md)、[账号存档指南](ACCOUNT_AND_SAVE_SYSTEM.md)及相关章节；记录源码、资源版本和客户端哈希。
2. 运行 `GS-Status.cmd`，区分源代码状态与运行 JAR 状态。健康接口只能证明服务检查通过，不能证明深渊或奖励正确。
3. 只输出 `server.game.gameOptions.tower` 和资源／数据目录这些必要配置，不导出整份含令牌、数据库地址的配置。
4. 重跑只读资源审计，确认实际目标排期的 floor → level → dungeon → scene → group 链。
5. 从日志判断请求是否被注册处理、响应是否因缺 CmdId 被丢弃，再进入队伍、场景和 Lua 调试。
6. 涉及个人记录时使用只读数据库投影，保留原始整数类型，不调用会补写记录的 getter。

```powershell
(Get-Content .\config.json -Raw | ConvertFrom-Json).server.game.gameOptions.tower
python tools\audit_tower_resources.py --output local\tower-current-audit.json
rg -n 'Spiral Abyss|Tower team select|Temporary team|Tower all-data|Tower challenge|Tower dungeon|no 7.1 CmdId|nothing handles it|active_challenge|tower_team_setup' logs local\logs -g '*.log'
```

日志里的 UID、角色列表、令牌或其他账号信息属于本机排障内容，不把原始日志提交进 Git。只摘录经核对且必要的结构证据。

### 只读 MongoDB 投影

先按[存档运维手册](ACCOUNT_AND_SAVE_OPERATIONS.md)确认实际游戏数据库名称，再在数据库客户端设置目标；下面 UID 12345 只是占位例子：

```javascript
db.players.findOne(
  { _id: 12345 },
  { _id: 1, towerData: 1, sceneId: 1, dungeonReturnLocation: 1 }
)
```

检查当前层和间、角色稳定身份、检查点对应层间、星记录与已发奖励集合。`currentLevelId`、`entryScene` 和半场计时没有持久字段是正常设计，不能因为 BSON 没有它们就补写假的运行状态。

对奖励事故，必须同时检查玩家记录和实际物品／货币变动；`rewardedLevelIds` 或结算 UI 单独一项都不足以证明完整到账。修复前成套备份，避免直接清空领取标记触发重发。

## 症状与首查位置

| 症状 | 首查证据与入口 | 不应直接作出的判断 |
| --- | --- | --- |
| 打不开深渊总页或没有更新记录 | 检查 `TowerAllDataReq=27826` → `TowerAllDataRsp=21818`、开放状态 42／44及实际入口交互 | 2026-10-22 已修复旧负数编号与排期字段；核对运行 JAR 是否更新 |
| 第九层锁定 | 全量包是否实际送达；前八层记录、最后入口层六星、第九层空记录、排期字段 | 不能只改数据库星数而忽略占位字段 |
| 选了两队仍带大世界队伍 | `TowerTeamSelectReq` payload、成功日志、稳定身份、`restoreTemporaryTeams`；选择响应被丢弃情况 | 不能把两队顺序丢失误认成角色模型错误 |
| 进本取消后队伍乱了 | `enterSceneWithTemporaryTeam` 与传送事件、原场景是否仍在、失败是否发成功响应 | 不能在迁移前先刷新旧场景实体 |
| 进本空房或没有工作台 | 当前排期是否被强制固定；scene／block／group 文件，suite、组加载与 Lua warning | 有任意 group 文件不代表关卡完整 |
| 上半结束后角色不出现 | 确认公共计时／下半组已加载，检查 TPL_TIME 写入错误；`TowerMirrorTeamSetUp`、`spawnPlayer` 与切队通知 `28798` | 实体距离保护曾跳过 SUPER 格子；深渊现应完整加载静态组 |
| 下半技能一直灰 | 工作台是否到达 stage=1 分支、是否执行 `SetIsAllowUseSkill(1)`、CanUseSkill 包 | 不宜在 `onBegin()` 全局硬开技能掩盖脚本错误 |
| 下半时间过多或不限时 | `evt.param2`、TPL_TIME、`HalfTime` 的场景／挑战身份和零预算校验 | 不能再用 600 减掉一次“剩余时间” |
| 通关等待期间掉星 | 是否使用成功挑战冻结的 `finishedTime`、是否走了新分支 | 不能让异步脚本等待继续消耗成绩时间 |
| 上半就结算或下半不结算 | passCond 50／51、Lua stage、成功事件回调与结算先后 | 挑战成功不自动等于整间成功 |
| 第一间通关却提示少于六星，无法继续 | 嵌套结算 `continue_state=8` 应为 1，`next_floor_id=3` 应为空；当前间通知应仍为刚完成的间号 | 2026-10-22 已修复旧占位字段；同层换间不要求六星，不能靠补星绕过 |
| 重试不生效 | 实际是哪条请求、检查点完整性、当前间是否已推进、`canRestartChamber` | 普通 DungeonRestartReq 当前明确拒绝深渊 |
| 重登回大世界 | `recoverDungeonLoginLocation` 和持久返回地点 | 这是当前设计，不自动说明保存丢失 |
| UI 星数与重新开页不同 | 增量 FloorRecordChangeNotify 的固定 levelId／条件与全量记录比较 | 不能只看 UI 断定 Mongo 星记录错误 |
| 结算有奖励但物品没到账 | 首通记录、Inventory 接纳结果、奖励配置与保存顺序 | 展示列表来自配置，不是到账回执 |
| 星级奖励点了没反应 | 28279 是否已有 Handler、请求日志和当前奖励业务 | 编号／资源存在不等于实现领取 |
| 换期后没有重新领奖 | 缺周期键、floorId／levelId 复用和旧 rewardedLevelIds | `rotate=true` 不会自动清档 |

## 需要继续开发的明确边界

下面列的是当前源码缺口及应有的下一步证据，未在本文档任务中修改玩法。

| 顺序 | 范围 | 下一步应完成的内容 |
| --- | --- | --- |
| 1 | 真实协议入口 | 恢复全量请求、选队响应、切队通知等 CmdId，核对真实注册与发送；不能猜编号 |
| 2 | 关键响应字段 | 恢复全量排期／时间、当前层间、星条件、继续状态的占位字段；纠正增量楼层记录构造 |
| 3 | 重试与本轮恢复 | 明确原生按钮发包，处理 `_is_restart_floor` 或正确替代协议；区分继续、重开本间、重置本层和放弃 |
| 4 | 领奖与经济一致性 | 首通完整容量预检、接纳确认、持久操作凭证及中断恢复；实现星级奖励领取 |
| 5 | 排期重置 | 设计周期键、入口层与周期层分离、共享 floorId 处理、历史记录和一次领奖规则 |
| 6 | 祝福与能力 | 选择请求、候选资源、保存、作用范围、Ability 应用与移除，覆盖月度及楼层效果 |
| 7 | 玩法完整度 | 守护目标、怪潮、入口状态、战报、跳层、成就／纪行专用 watcher 及逐间验收 |
| 8 | 额外输入保护 | 当前选队未完整核对排期成员／解锁；空排期、坏条件参数、空挑战结果等边界需加复现 |

例如 `TowerResult.onProto` 在前半段检查了 challenge 非空，却仍无条件调用 `challenge.isSuccess()`；这是值得补测试的空状态风险，不能依靠注释宣布已处理。候选筛选也不保证 passCond 配置可解析，强制指定新排期尤其应完整检查依赖。

## 修改时必须保留的不变量

- **身份**：floorId、levelId、dungeonId、sceneId、Lua groupId、avatarId、GUID 分层使用。
- **状态**：同一间的检查点不被下半覆盖；两队以稳定角色 ID 和原顺序对应。
- **迁移**：先校验与预装新场景，再发布和迁移；失败成功码、队伍索引和实体必须一致。
- **线程**：锁顺序遵循现有玩家传送锁及场景保护；不要在脚本队列里等待同队列后续任务。
- **时钟**：上半输出剩余时间，下半使用同一预算；零预算不能变无限时。
- **结算**：Lua 成功／结算完成后再记录；迟到事件不能改变替换后的场景或玩家队伍。
- **经济**：服务器发奖与 UI 列表分开证明，重复请求／保存失败必须有可恢复依据。
- **持久化**：保留完整 MongoDB 与运行数据；禁止用失效 GUID、人工补 stage 或批量删组状态修档。

收到新故障时通常先用现有 fixture 增补复现，再改业务；不要把测试跳过、改成捕获包成功或删掉身份校验当成修复。

## 原生客户端验收矩阵

以下项目本次未实操。执行时为每行记录客户端哈希、运行 JAR SHA-256、源码／资源版本、排期、层间、相关包、日志片段和结果，使用隔离测试账号。

| 场景 | 要确认的结果 |
| --- | --- |
| 新账号默认跳过前八层 | 入口层星数、第九层开放、未实发被跳过的首通奖励；重开页面一致 |
| 关闭跳过的新账号 | 单队与双队层、真实前层解锁条件，不沿用已补星的旧档 |
| 双队选队 | 顺序正确、重复角色被拒、取消后大世界队伍不变 |
| 首入与连续三间 | 每间地图与怪物正确，场景不复用旧 group 状态 |
| 上下半切换 | 旧实体移除、新角色可操作、工作台和技能可用、预算衔接正确 |
| 守护间 | 目标实体 HP、三星阈值与实际显示一致，不因 guard 缺失误判满血 |
| 0／1／2／3 星结果 | 最高纪录、通知、继续按钮和本次奖励按实现／目标规则分别核对 |
| 上半／下半死亡与重试 | 返回本间上半，两队 HP／生命之契／能量恢复，冷却正确 |
| 开始前退出、失败后退出、成功后退出 | 不重复结算，恢复正常队伍且返回正确位置 |
| 断线／重登 | 返回安全世界、稳定队伍可重绑，不伪称恢复运行战斗 |
| 重复领奖及背包容量不足 | 物品、货币和领奖标记一致，不漏发、不重复 |
| 第 12 层最后一间 | 没有第 13 层或 floor 0，结束 UI 可退出 |
| 排期边界与复用楼层 | 显示时间、周期状态、已领取记录的规则一致 |
| 祝福与地脉效果 | 选卡实际影响战斗，并按本间／本层／周期正确结束 |

## 交付与运行环境

本机遵循[便携运行约定](../AGENTS.md)：启动、停止、状态和游戏入口分别为 `Start-GS.cmd`、`Stop-GS.cmd`、`GS-Status.cmd`、`Play-GS.cmd`。正式数据 `mongodata/` 与 `local/data/` 必须保留。

本次只新增文档、证据摘要和只读审计工具，不修改服务端运行逻辑、客户端或正式运行配置，因此无需重建运行 JAR／重启游戏服。执行现有 Gradle 测试不等于已经部署新的运行版本。

以后修改 Java、运行资源或配置时，测试完成后必须按便携流程重新构建、正常停止旧进程、启动新构建，核对健康接口、端口和运行 JAR SHA-256；在本机可以使用已按 portable 路由的 `task.cmd serve`。仅产生 build/classes 或单独的 JAR 不算完成运行更新。按项目要求及时 Git 提交、推送，并说明尚需游戏内验证的部分。

## 历史专题的关系

这些文档保留当批动机和复现证据；阅读时按后续实现覆盖早期限制，不把旧的“尚待实现”直接当成当前缺口：

| 主题 | 历史说明 |
| --- | --- |
| 选队与恢复 | [输入校验](TOWER_PARTY_SELECTION_2026-10-12.md)、[队伍持久化](TOWER_PARTY_PERSISTENCE_2026-10-12.md)、[稳定身份](TOWER_PARTY_IDENTITY_2026-10-12.md) |
| 进入与换间 | [入口结果](TOWER_ENTRY_OUTCOME_2026-10-12.md)、[场景交接](TOWER_SCENE_HANDOFF_2026-10-12.md) |
| 重试 | [检查点](TOWER_CHAMBER_CHECKPOINT_2026-10-12.md)、[角色恢复](TOWER_CHAMBER_RETRY_2026-10-12.md)、[组状态隔离](TOWER_RETRY_GROUPS_2026-10-12.md) |
| 时钟和结束 | [半场计时](TOWER_HALF_CLOCK_2026-10-12.md)、[成功结算顺序](TOWER_SETTLEMENT_ORDER_2026-10-12.md)、[失败退出](TOWER_END_NOTIFICATION_2026-10-12.md) |
| 角色持久化联动 | [生命与能量读档](AVATAR_VITALS_PERSISTENCE_2026-10-16.md) |

## 可交给下一个 AI 的任务说明

```text
先读 AGENTS.md、docs/SPIRAL_ABYSS_SYSTEM.md 及对应配套章节。
涉及存档先读 docs/ACCOUNT_AND_SAVE_SYSTEM.md。
核对当前源码、资源提交和客户端 SHA-256，重跑只读资源审计。
区分客户端符号证据、源码行为、实际发包和游戏内验收。
先确认负 CmdId、占位字段与未实现 Handler，再排查具体战斗逻辑。
为待修问题补复现，使用独立 MongoDB 测试实例，不碰正式玩家数据。
保留稳定队伍身份、检查点、半场预算、场景实例保护和异步结算顺序。
奖励修复必须覆盖重复、部分入包和保存失败，不能直接清领奖集合。
完成后同步本说明与证据，提交并推送；若改运行内容，按 portable 约定更新和验收。
```
