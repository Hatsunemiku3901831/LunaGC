# 深境螺旋数据资源与存档

深渊使用多个编号层级。排期选择楼层，楼层选择关卡组，关卡选择副本，副本选择场景，场景加载 Lua 组。玩家记录以资源 ID 为键保存；只改 UI 层号或把新版本表格覆盖进目录，不能保证这条链仍可运行。

源码和客户端基线见[总览](SPIRAL_ABYSS_SYSTEM.md)，协议限制见[客户端与协议](SPIRAL_ABYSS_CLIENT_PROTOCOL.md)。本文的具体数值来自 2026-10-21 的[资源审计 JSON](data/tower-resource-audit-2026-10-21.json)，以后应重跑审计。

## 编号字典

以当前默认第 12 层第 1 间为例：

```text
scheduleId 62
  → floorId 1053，floorIndex 12
  → levelGroupId 53
  → levelId 157，levelIndex 1
  → dungeonId 3106
  → sceneId 33746
  → 状态组 233746002、工作台组 233746003
  → 上半战斗组 233746004、下半战斗组 233746005
```

| 字段 | 含义与常见混淆 |
| --- | --- |
| `scheduleId` | 资源中的排期标识，不是现实年月，也不是玩家当前存档周期 |
| `floorId` | 楼层配置主键，如 1053；不是界面第几层 |
| `floorIndex` | 界面层号，当前 1 至 12；不同排期可有不同 floorId 对应同一层号 |
| `levelGroupId` | 连接一层和其各间的资源分组 ID；不是 Lua groupId |
| `levelId` | 间配置主键，也是 `passedLevelMap`／`rewardedLevelIds` 的键 |
| `levelIndex` | 资源中一层内的 1 基间号 |
| `dungeonId` | 副本配置 ID，含场景、通关条件、起点等 |
| `sceneId` | 场景配置 ID；一次次进入可以创建不同的同 ID 场景实例 |
| Lua `group_id` | 场景脚本组主键，拥有怪物、机关、变量与 suite |
| Lua `config_id` | 组内实体／触发器等配置身份，不是网络 entityId |
| `challengeId` / `challengeIndex` | 当前 Java 命名：例 `ActiveChallenge(1,197,...)` 中分别为 1 与 197；后者查挑战类型配置 |
| `avatarId` / GUID | 自有角色稳定配置身份／当前会话网络身份；持久恢复应前者重新绑定后者 |

当前 `TowerManager` 假设每层三间，而且第一间开始的 `levelId` 连续；它通过加偏移取后续间，未按列表逐项查 `levelIndex`。审计确认本次默认 12 层都满足三间连续条件，这不意味着任意导入资源都满足。

## 资源文件及实际加载范围

| 文件，位于 `resources/ExcelBinOutput/` | 本次条数 | 当前作用 |
| --- | ---: | --- |
| `TowerScheduleExcelConfigData.json` | 126 | `TowerScheduleData` 加载 ID、入口层、排期楼层和月度配置 ID |
| `TowerFloorExcelConfigData.json` | 141 | `TowerFloorData` 加载 floorId、层号、关卡组、怪物备用等级、队伍数、楼层配置 ID |
| `TowerLevelExcelConfigData.json` | 423 | `TowerLevelData` 加载间 ID／索引、组、副本、星条件、怪物等级和首通奖励 |
| `TowerBuffExcelConfigData.json` | 36 | 有祝福配置资源；当前无完整选择／应用业务链 |
| `TowerRewardExcelConfigData.json` | 180 | 有 15 组、每组 12 层的奖励表；当前首通入口未按此表选择 rewardGroup |
| `DungeonExcelConfigData.json` | 2378 | Tower 间指向的副本、场景、通关配置 |
| `RewardExcelConfigData.json` | 见审计 | 首通 rewardId 最终解析到物品列表 |
| `DungeonPassExcelConfigData.json` | 另查对应行 | 双队示例 passCond=50；单队示例 passCond=51 |
| `DungeonChallengeConfigData.json` | 另查对应行 | 197、198 均为 `CHALLENGE_KILL_COUNT_IN_TIME` |

资源 README 明确这是围绕 7.1 组装并补入其他快照的社区资源集，不是完整官方客户端导出。表里存在某个字段，不代表 Java 类加载或业务使用它：

- `TowerFloorData` 未加载 `unlockStarCount` 和各档星奖励 ID；当前选队入口也未用这些字段作服务器资格判定。
- `TowerLevelData` 未加载 `argumentListUpper`、怪物预览列表和祝福候选权重字符串。星级读取 `argumentList`。
- `TowerScheduleData` 未加载资源里的 `openTime`、`closeTime`、`rewardGroup`、`scheduleRewards`。显示时间来自另一路配置／算法。
- `floorLevelConfigId`、`monthlyLevelConfigId` 虽存在字段／getter，当前深渊链未见相应效果应用调用；不能据此保证地脉异常与渊月祝福完整实现。
- `TowerChallenge*`、`TowerDefense*` 资源属于其他系统，不能据文件名 Tower 就合并分析。

## 排期选择算法

配置入口是 `config.json` 的 **`server.game.gameOptions.tower`**；`Configuration.GAME_OPTIONS` 指向这一位置。不要误写成顶层 `game.tower` 或 `server.game.tower`。本机核对值与当前默认值一致：

```json
{
  "scheduleId": 0,
  "rotate": false,
  "rotationPool": 12,
  "skipEntranceFloors": true
}
```

`TowerSystem.getCurrentTowerScheduleData()` 的实际优先级：

1. `scheduleId>0` 且资源中存在：直接使用指定排期，**绕过脚本存在性检查**。
2. 未固定且 `rotate=true`：在最新 `rotationPool` 个通过检查的排期中，按半月序号取模轮换；pool≤0 或不小于可用数量时使用全部。
3. 不轮换：选通过检查的最大排期 ID。
4. 以上未选出：使用 `TowerSchedule.json.scheduleId` 回退；仍不存在则记录错误，并可能使后续非空假设失败。

一个容易误读的边界：固定的正 ID 不存在时日志写 `rotating instead`，但 `isRotating()` 仍因 ID>0 返回 false，所以实际先走“最新通过检查的排期”，不是自动半月轮换。

### 可玩检查到底检查了什么

`isPlayable` 遍历入口楼层和**所有非空 schedule detail**，要求每层存在、至少有一个关卡、每关有副本，且场景目录至少有一个 `scene{sceneId}_group*.lua`。

它不验证恰好三间、Lua 语法、所有引用组、怪物 AI、工作台、通关条件、客户端包或战斗结果。这里称“通过脚本存在性检查的候选”，不要把方法名 `isPlayable` 扩大为完整实战认证。

首次资源加载后计算并缓存候选列表；场景是否有脚本也被缓存。`load()` 只重新读排期配置，没有清这两个缓存。补 Lua 文件后应重新构建／按便携流程更新进程，而非假定调用 `load()` 会重算候选。

本次 126 个排期中通过 62 个，ID 为 1 至 62；默认选择 62。排期 63 开始的缺失例子是 floor 1054 → level 160 → dungeon 3107 → scene 33747 没有匹配组脚本。全部失败引用在审计 JSON 的 `rejectedSchedules`。

`getScheduleFloors()` 只使用第一个非空 detail，而候选检查会验证所有 detail。未来资源包含多段有效开放配置时，应检查这个不对称，而不是假定全部按时间合并开放。

### 时间与周期不是一件事

轮换算法使用 Java `Calendar.getInstance()` 的 JVM 默认时区，每月 1 日、16 日 04:00 换期；边界日 04:00 之前仍属于上一期。半月序号为 `year*24 + zeroBasedMonth*2 + halfIndex`，没有“首次开启从池首开始”的玩家锚点。这是本仓库算法，不是对当前官方周期的声明。

不轮换／固定排期时，显示时间使用 `TowerSchedule.json`。`DataLoader` 通过 `FileUtils.getDataPath` 优先当前用户数据目录，再回退内置 defaults。本机用户数据目录为 `local/data`，本次未发现其 TowerSchedule 覆盖文件；[内置配置](../src/main/resources/defaults/data/TowerSchedule.json)为 ID 45、开始 `2022-06-01T00:00:00+08:00`、下次 `2030-06-30T00:00:00+08:00`。

因此默认状态可能是“选出的内容为 62，显示时间沿用内置旧日期”，并非资源第 62 行的 2023 年开放日期。运行 JAR 若与源码不同，还必须核对它自己的 defaults。

`TowerData` 没有保存奖励周期 ID，`TowerSystem` 改选排期也没有清星级／奖励标记的业务流程。两个排期复用相同 floorId／levelId 时，旧进度与 `rewardedLevelIds` 会沿用；不能把 `rotate=true` 描述为完整的周期重置。

## 当前默认楼层映射

排期 62 的入口层为 1001 至 1008，后四层为 1044、1045、1052、1053。默认前八层会补为已通关；关闭跳过后这些资源仍存在。

| 界面层号 | floorId | levelId，按第 1 至 3 间 | dungeonId，按间 | sceneId，按间 | 队伍数 |
| --- | ---: | --- | --- | --- | ---: |
| 1 | 1001 | 1、2、3 | 3310、3410、3510 | 33100、34100、35100 | 1 |
| 2 | 1002 | 4、5、6 | 3311、3411、3511 | 33101、34101、35101 | 1 |
| 3 | 1003 | 7、8、9 | 3312、3412、3512 | 33102、34102、35102 | 1 |
| 4 | 1004 | 10、11、12 | 3313、3413、3513 | 33103、34103、35103 | 1 |
| 5 | 1005 | 13、14、15 | 3341、3441、3541 | 33401、34401、35401 | 2 |
| 6 | 1006 | 16、17、18 | 3342、3442、3542 | 33402、34402、35402 | 2 |
| 7 | 1007 | 19、20、21 | 3343、3443、3543 | 33403、34403、35403 | 2 |
| 8 | 1008 | 22、23、24 | 3344、3444、3544 | 33404、34404、35404 | 2 |
| 9 | 1044 | 130、131、132 | 3407、3507、3607 | 33737、34737、35737 | 2 |
| 10 | 1045 | 133、134、135 | 3408、3508、3608 | 33738、34738、35738 | 2 |
| 11 | 1052 | 154、155、156 | 3105、3205、3305 | 33745、34745、35745 | 2 |
| 12 | 1053 | 157、158、159 | 3106、3206、3306 | 33746、34746、35746 | 2 |

最后四层的服务端怪物等级分别为 72/74/76、80/82/85、88/90/92、95/98/100。来源是 `TowerLevelData.monsterLevel+1`；找不到间时才回退到楼层 `overrideMonsterLevel`，再无楼层则为 1。

`EntityMonster.recalcStats` 对深渊最大 HP 额外乘系数：1–2 层 1.0，3–7 层 1.5，8–11 层 2.0，12 层及以上 2.5。这里只描述该额外步骤；完整 HP 还取决于怪物基础配置、成长、其他属性与效果。

## Lua 场景结构与真实例子

场景目录通常含 `scene{id}.lua`、区块文件和多个 `scene{id}_group{groupId}.lua`。scene 文件给出生点、朝向、死亡高度、区块；group 给怪物、机关、变量、suite、触发器和动作函数。

以[第 12 层第一间 scene33746](../resources/Scripts/Scene/33746/scene33746.lua)为例：

| 组 | 作用 |
| --- | --- |
| [233746002](../resources/Scripts/Scene/33746/scene33746_group233746002.lua) | `stage=0`、`TPL_TIME=0` 初始状态 |
| [233746003](../resources/Scripts/Scene/33746/scene33746_group233746003.lua) | 工作台上／下半选项；开始时 `SetIsAllowUseSkill(1)` |
| [233746004](../resources/Scripts/Scene/33746/scene33746_group233746004.lua) | 上半挑战 `ActiveChallenge(1,197,600,233746004,1,0)`；成功保存剩余时间、禁技能、stage=1、切队 |
| [233746005](../resources/Scripts/Scene/33746/scene33746_group233746005.lua) | 下半读取 TPL_TIME，启动挑战 2／198；成功 stage=2；失败调用 `CauseDungeonFail` |

这个例子的上半怪物配置为 24021101，下半资源列出 25410101、23010401 和两个 25210501。具体生成顺序仍要看 suite、触发器和刷新动作，不能只统计 `monsters` 数组长度当作一波数量。

专项测试还使用[第 5 层第一间 scene33401](../resources/Scripts/Scene/33401/scene33401.lua)的真实回调，其状态组为 233401001、上下半为 233401003／233401004。不要把测试里的这些固定组 ID 用到其他层。

`DungeonPass` 50 要求完成挑战标识 2，51 要求完成标识 1；这使双队场景的上半成功不会直接等同整间完成。`TowerDungeonSettleListener` 的 stage 检查提供另一层保护。

`TowerMirrorTeamSetUp` 支持一／两参数 Lua 调用，检查单玩家与当前深渊管理器，将 1 基队伍转为 0 基，成功后停止旧怪潮。`TowerCountTimeStatus` 当前只告警并返回 0，是未实现桥接，返回 0 不等于逻辑完成。

### 组状态为什么不能复用数据库

深渊初入、换间和重试的新 `Scene` 使用 `persistentGroups=false`。`SceneScriptManager` 不加载旧数据库组实例，创建的 `SceneGroupInstance` 也不按普通世界组持久化；`stage`、TPL_TIME、suite、已死亡／移除实体从新场景重新开始。

持久玩家进度和这类临时组状态必须分开。把深渊改成普通持久场景，可能载入上轮的 stage=2 或死亡怪物列表，造成空房／无法开始。不要通过删除正式世界所有 `scene_groups` 来修复深渊。

## 星级判定

`getCurLevelStars` 从三星向一星查找第一个满足的条件：

- `TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN`：实际比较为 `remaining >= argumentList[1]`，并非严格大于。
- `TOWER_COND_LEFT_HP_GREATER_THAN`：实际比较为守护实体整数 HP 百分比 `>= argumentList[2]`。
- 未知／缺少条件类型记录错误并继续降档；条件参数列表不足仍存在异常风险。

护卫条件的前两个参数被封装成 `sceneId/configId`，本地部分真实值实际是组 ID／组内实体 ID；当前计算只调用挑战的 `getGuardEntityHpPercent()`，没有根据这两个参数重新查目标。守护实体缺失或最大 HP≤0 时返回 100，这是一种容错，不能当成真实满血证据。

第 11 层第一间使用守护百分比阈值 20、40、60；第 12 层三间均为剩余秒数 180、300、420。完整 36 间条件见资源审计的 `floors[].levels[].starConditions`。

`passedLevelMap` 保留较高星数，不因重打较低星覆盖。但已有空楼层记录中，首次零星通关不会新增该间的 0 项，因为代码只在新星数大于旧值 0 时更新；没有该键不能一概解释为“从未通关”。

## 奖励链路与一致性边界

### 间首通奖励

成功结算调用 `notifyCurLevelRecordChangeWhenDone`，再进入 `grantFirstPassReward`：

1. 楼层记录存在、该 levelId 尚未标记领奖且 `stars>0`。
2. 查 `TowerLevelData.firstPassRewardId`，再查 `RewardData.rewardItemList`。
3. `Inventory.addItemParamDatas(items, ActionReason.TowerFirstPassReward)`。
4. `markLevelRewarded(levelId)`，返回配置列表供结算展示。

本仓库的奖励条件明确要求至少一星；不要把注释中的“首通”理解为零星也发。例：level 157 → reward 621034，资源配置是摩拉 202×40000、104003×2、104002×4、104013×7、117002×1。该配置没有原石；星级原石奖励是另一条应实现的链。

重要边界：

- `Inventory.addItemParamDatas` 返回 void，内部按物品逐项尝试接纳；深渊调用方不核对完整接纳结果，仍标记该间已领奖，并把原配置列表返回作展示。背包／道具限制可能造成展示、标记与到账不一致，需专项复现后修复。
- `rewardedLevelIds` 的本对象检查提供常规重复结算去重，不是与物品一起提交的跨文档事务。
- 这条方法没有独立持久操作 ID、待恢复奖励计划、同步存档确认或失败补偿，且记录更新方法自身未调用 `player.save()`。其他角色／物品保存和后续玩家保存不能自动构成同一原子提交。
- 强退、写入失败、部分入包以及旧档缺领取集合时，存在重发或漏发风险；不能直接清标记重打来“修复”奖励。

这些是源码可见的一致性缺口，不是本次对正式账号制造故障后的结论。修复设计应复用项目现有经济恢复模式，参见[存档系统](ACCOUNT_AND_SAVE_SYSTEM.md#经济操作和故障恢复)，但不能假定深渊已经接入这些模式。

### 楼层星奖励与周期奖励

`floorStarRewardProgress` 有保存字段，资源有 3／6／9 星等档位和较新 rewardGroup 规则；当前未找到 `TowerGetFloorStarRewardReq` 的业务入口。不要把 `PacketTowerFloorRecordChangeNotify` 填的数值视为奖励已发。

`TowerRewardExcelConfigData.rewardIdRoom` 在较新组可与 `TowerLevel.firstPassRewardId` 不同；当前实现只走后者。把新的排期 ID 固定进配置，不能自动切换到新奖励组。

月度简报、跳层领取、日奖励进度和专门的 Tower watcher 类型虽然有协议／枚举／资源痕迹，当前深渊完成链没有完整接入。普通 `TRIGGER_FINISH_CHALLENGE`、副本完成和部分手册／纪行触发不能代替深渊专门星数或无伤任务。

### 默认跳过前八层

`getRecordMap()` 每次按 `skipEntranceFloors` 调用 `grantEntranceFloors`。对需要补齐的入口层，删除不属于该层的旧间键、写三间各三星、标记三间首通已发、设置 `floorStarRewardProgress=9`，并创建第九层空记录。

这些是赠送的通过状态，不会发放相应物品。总星数已经≥9 的已有记录会提前跳过后续补标步骤；不能声称任意历史满星档都会补齐领奖集合。将开关改为 false 也不会反向清除已补写的记录，验收真实回廊进度应使用隔离新账号。

## 玩家持久字段字典

`Player.towerData` 内嵌在 `players` 文档中，没有独立 tower 集合。具体集合和保存保护见[存档数据模型](ACCOUNT_AND_SAVE_DATA_MODEL.md)。

| 字段 | 持久性 | 含义 |
| --- | --- | --- |
| `towerData.currentFloorId` | 保存 | 当前选择的资源楼层 ID |
| `towerData.currentLevel` | 保存 | 当前间零基偏移，完成后加一；3 表示本层三间已经走完 |
| `towerData.currentLevelId` | 不保存，`@Transient` | 当前楼层第一间 ID；恢复时从资源重建 |
| `towerData.entryScene` | 不保存，`@Transient` | 本次入口场景；不是完整的长期返回位置 |
| `towerData.abyssTeamGuids` | 保存 | 原选队 GUID 列表，供兼容与会话重新绑定更新 |
| `towerData.abyssAvatarIds` | 保存 | 按上／下半及队内顺序保存的自有角色 ID |
| `towerData.recordMap` | 保存 | floorId → `TowerLevelRecord` |
| `record.floorId` | 保存 | 该记录的楼层 ID |
| `record.passedLevelMap` | 保存 | levelId → 最高星数 |
| `record.floorStarRewardProgress` | 保存 | 星奖励进度字段；当前缺完整领取业务 |
| `record.rewardedLevelIds` | 保存 | 已标记首通发放的间 ID 集合，旧档可缺失 |
| `towerData.chamberState` | 保存 | 当前间入口角色快照 |
| `Player.towerManager` | 不保存 | 当前可能星数、挑战标志、半场计时等运行对象 |
| `TeamManager.temporaryTeam` 及使用索引 | 不保存 | 当前会话的临时队伍与激活半场 |
| `Player.dungeonReturnLocation` | 另一个持久字段 | 副本重登返回大世界的来源信息 |

### 检查点内部字段

`TowerChamberState` 保存 `floorId`、当前实际 `levelId`、按顺序排列的 `parties`、以及按 avatarId 索引的 `avatars`。每个 `AvatarState` 保存：

| 字段 | 含义 |
| --- | --- |
| `depotId` | 捕获时技能库 ID，用于防止旅行者换元素等导致恢复到错误池 |
| `energyProperty` | 普通元素或特殊能量的 FightProperty 编号 |
| `hp` | 入口当前 HP，可为 0；不是最大 HP |
| `debt` | 当前生命之契数值 |
| `energy` | 当前使用能量池的数值 |

恢复前验证完整角色集合、归属、技能库、能量属性、有限非负值及有效最大值。全部目标校验通过后才开始应用，HP／能量钳制到当前最大值。冷却不在快照内存储；重试走的是清冷却业务。

当前入场逻辑是捕获当时角色状态，没有一条在 `TowerManager` 中统一将新层全队设满血满能的显式初始化链。因此不要把检查点机制描述为已经实现官方所有入场状态规则；应按真实角色状态验收。

## 资源审计复现

[audit_tower_resources.py](../tools/audit_tower_resources.py) 只读取源码版本及资源，不连接数据库，不读玩家存档，也不改运行配置。默认按“最大脚本候选 ID”输出；它不根据运行配置或当前时间替服务端选期。

```powershell
python tools\audit_tower_resources.py --output local\tower-current-audit.json
python tools\audit_tower_resources.py --schedule 62 --output local\tower-schedule-62.json
```

报告包含输入表哈希、候选与失败引用、每层三间连续性、间／副本／场景映射、组脚本路径、星条件和首通奖励。默认候选检查刻意与当前 Java 的有限检查对齐，`threeConsecutiveLevels` 是另列的附加诊断；任何一个都不是原生客户端的通关证明。
