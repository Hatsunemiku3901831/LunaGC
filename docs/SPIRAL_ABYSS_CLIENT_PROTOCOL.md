# 深境螺旋客户端与协议说明

客户端负责选层、选队、祝福卡片、计时与结算显示，服务端负责可接受状态、场景执行和进度记录。界面组件存在，只能说明客户端保留这类功能；服务端构建出 protobuf 对象，也不能证明消息已经在网络上发送或被当前客户端识别。

本文基线和阅读顺序见[总览](SPIRAL_ABYSS_SYSTEM.md)。下面把本机客户端符号、源码协议定义和运行传输限制分开记录。

## 本机客户端证据

本机客户端目录按[项目约定](../AGENTS.md)为 `C:\GAME\Genshi`，配置 `game_version=7.1.0`、`wpf_version=7.1.0.48145775`。仓库未包含可直接阅读的完整客户端 C# 源码；本次使用已有的[类型提取工具](../tools/extract_client_type_names.py)读取并验证下列文件：

| 文件 | SHA-256 |
| --- | --- |
| `YuanShen.exe` | `7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e` |
| `YuanShen_Data/Native/Data/Metadata/global-metadata.dat` | `469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161` |

工具验证原文件哈希后恢复类型、字段名、方法名和可识别的直接返回命令号的方法。全量得到 88,904 个类型、733,442 条方法记录和 4,923 个命令号 getter，后者有 4,919 个不同值。这里只保留与本系统有关的[符号摘录](data/tower-client-symbols-2026-10-21.json)；完整提取物位于本机 `local/tower-doc-audit-20261021/`。

### 实际界面组件

表中字段／方法名来自该客户端；“职责”是根据这些名称及服务端流程作出的界面职责归纳，不是完整还原了原生方法控制流。

| 客户端类型，均在 `MoleMole` 命名空间 | 类型索引 | 关键成员 | 对应职责 |
| --- | --- | --- | --- |
| `MonoTowerPage` | 15034 | `_floorPanel`、`_levelPanel`、`_teamPanel`、`get_rewardButton`、`get_btnReset` | 深渊总页面、子页导航、重置与奖励入口 |
| `MonoTowerFloorItem` | 19573 | `_starCountText`、`_doubleTeamIcon`、`_rewardBtn`、`_picLock` | 楼层星数、双队提示、奖励和锁定展示 |
| `MonoTowerLevelPanel` | 36989 | `_levelScroller`、`_doubleTeamText`、`_unlockText` | 单层各间、解锁条件及双队要求 |
| `MonoTowerTeamPanel` | 56243 | `_singleTeamRoot`、`_doubleTeamRoot`、`_firstTeamScroller`、`_secondTeamScroller` | 一队／双队编辑和预设队伍入口 |
| `MonoTowerStageDialog` | 81253 | `_firstMonsterList`、`_secondMonsterList`、`_starTextArray` | 上下半怪物预览和星级条件 |
| `MonoTowerBegin` | 72924 | `scroller`、`buffBtnGrp`、`iconList` | 开始阶段的卡片／增益展示 |
| `MonoTowerBuffDialog` | 24541 | `_timeText`、`_buffTextList` | 增益说明和时间显示 |
| `MonoTowerEndWin` | 41858 | `timeInfo`、`starConditionTxt`、`firstPassRewardScroller`、`get_continueContainer` | 成功结算、首通奖励、继续／重挑战入口 |
| `MonoTowerEndExit` | 35244 | `_starList`、`_rewardScroller`、`get_btnAgain`、`get_btnExit` | 退出或未完成状态的结果与操作 |
| `MonoTowerAppraisalRewardDialog` | 64478 | `_floorText`、`_starText`、`_rewardScroller` | 楼层星级奖励展示 |
| `MonoTowerBookDialog` | 51437 | `_towerDataList`、`_layerRoomText`、`_avatarIconRoot` | 挑战记录页面 |
| `MonoTowerReviewDialog` | 67856 | `_monthText`、`_starNumText`、`get_receiveButton` | 周期回顾与领取入口 |

没有根据这些名字推断出每个按钮的网络绑定。特别是“重试”“重置本层”“暂离”“放弃本轮”在界面上可能分别发不同消息，必须以实际协议样本区分。

### 客户端补丁的职责边界

当前 [patch](../patch/README.md) 是连接、加密及相关兼容模块。对 `patch/src` 的深渊关键词检索没有找到本玩法专用实现，不能靠已安装补丁推导深渊逻辑已经补齐。

本次没有改动客户端文件，也没有运行游戏内挑战。类型及 getter 证据确认的是该二进制的结构；场景动画、界面跳转和完整包序列仍属于游戏内验收。

## 消息实现与传输状态

编号来自 [PacketOpcodes](../src/main/java/emu/grasscutter/net/packet/PacketOpcodes.java)，字段来自 `src/main/proto/`。负值是源码使用的未解析哨兵，绝不是可直接发送的真实 CmdId。

| 消息 | CmdId | 当前 Java 实现和限制 |
| --- | ---: | --- |
| `TowerAllDataReq` | -87 | 有 Handler，但注册器跳过非正编号，因此不能按此常量注册真实网络入口 |
| `TowerAllDataRsp` | 21818 | 有构造器；排期与时间等多个字段仍为 50000 段占位编号 |
| `TowerTeamSelectReq` | 20134 | 有 Handler，读取队伍并调用 `tryTeamSelect` |
| `TowerTeamSelectRsp` | -88 | 有成功／失败构造器；当前本机 fan-out 无此映射，发送时被丢弃 |
| `TowerEnterLevelReq` | 21373 | 有 Handler，只读取 `enter_point_id`；忽略 `_is_restart_floor` |
| `TowerEnterLevelRsp` | 7393 | 有构造器；成功时固定返回祝福 ID 4、28、18 |
| `TowerCurLevelRecordChangeNotify` | 9479 | 有构造器；当前层与间字段为占位编号，队伍信息留有 TODO |
| `TowerFloorRecordChangeNotify` | 7649 | 有构造器，但内容并非按完整存档生成，见下文 |
| `TowerLevelStarCondNotify` | 20675 | 有构造器，单个条件里的两个字段仍占位 |
| `TowerMiddleLevelChangeTeamNotify` | -190 | 空消息类型可构建，但本机没有可用发送映射；空描述符也不能靠现有 fan-out 自动恢复 |
| `TowerLevelEndNotify` | 25538 | 有 proto；当前通过 `DungeonSettleNotify` 的嵌套字段发送，无独立发送器 |
| `TowerBuffSelectReq` / `Rsp` | 4779 / 9661 | 有编号，当前未找到对应请求 Handler 和完整业务链 |
| `TowerGetFloorStarRewardReq` / `Rsp` | 28279 / 5775 | 有编号，当前未找到领取处理器和领奖业务链 |
| `TowerSurrenderReq` / `Rsp` | -265 / -266 | 未解析编号，未找到独立放弃处理器 |
| `TowerBriefDataNotify` | 7704 | 编号已列出，当前深渊链未填充对应简报 |
| `TowerDailyRewardProgressChangeNotify` | 28053 | 编号已列出，当前深渊链无对应日奖励进度实现 |
| `TowerRecordHandbookRsp` | 29419 | 编号已列出，未形成完整战报响应链 |

本机客户端的直接返回 CmdId getter 与表中 15 个正数 Tower 编号均能一一匹配到原生类型，例如 20134 对应 `IOJLBMJIMFC`、21373 对应 `NADIKODCFFI`、21818 对应 `OMPOFJHDDKG`。这些混淆名不独立证明语义命名正确，更不证明字段编号已经恢复。完整对应关系在符号 JSON 的 `packetCandidates`。

### 负数消息为什么导致测试与游戏表现不同

[GameServerPacketHandler.registerPacketHandler](../src/main/java/emu/grasscutter/server/game/GameServerPacketHandler.java) 跳过 `opcode.value() <= 0`；[GameSession.send](../src/main/java/emu/grasscutter/server/game/GameSession.java) 对非正出站编号先尝试 [FanOut](../src/main/java/emu/grasscutter/server/game/FanOut.java)，失败后记录 `has no 7.1 CmdId, so it is not being sent.` 并返回。

`FanOut` 只读当前数据目录的 `proto-fanout.json`；本机该文件无 Tower 项。它只处理出站包，不能替请求处理器注册负数编号；而空消息的描述符字段表为空，会被它拒绝。因此不能把 fan-out 当成缺失深渊协议的通用解法。

多项测试使用捕获包对象的替代 `GameSession.send`。测试可验证“逻辑生成了通知”，不经过以上真实发送拒绝分支，也不经过客户端解析。直接调用 Handler 的测试同样不能证明它已注册到网络路由。

## 关键 protobuf 字段

字段编号属于当前源码线格式定义，后续修改前应比较原生读写方法／实际 payload。50000 段字段在这里作为未完成兼容项，不因 protobuf 编译成功而视为已验证。

### 选队和入场

| 消息 | 字段与编号 | 消费方式 |
| --- | --- | --- |
| `TowerTeamSelectReq` | `floor_id=3`、`tower_team_list=6` | 每个列表元素是一个半场队伍 |
| `TowerTeam` | `avatar_guid_list=1`、`tower_team_id=8` | Handler 只提取 GUID 列表，保留收到的顺序 |
| `TowerTeamSelectRsp` | `retcode=3` | `RET_SUCC` 或 `RET_FAIL`；仍受未解析 CmdId 限制 |
| `TowerEnterLevelReq` | `enter_point_id=15`、`_is_restart_floor=4` | 入口点用于返程；布尔重开标记当前未处理 |
| `TowerEnterLevelRsp` | `tower_buff_id_list=4`、`floor_id=6`、`level_index=7`、`retcode=14` | 楼层配置 ID、1 基间号和候选祝福 |

`TowerTeamRecoveryTest` 用 `CodedOutputStream` 手工构造请求验证这组字段和顺序；它不是本次从原生客户端抓取的完整交互记录。GUID 是 uint64，导出到 JSON 或 JavaScript 时应保留字符串／完整整数语义，不能经浮点数丢失精度。

### 全量状态和增量状态

`TowerAllDataRsp` 当前使用的普通编号包括 `retcode=15`、`cur_level_record=13`、`is_finished_entrance_floor=10`、`tower_floor_record_list=11`。关键待恢复字段包括：

| 字段 | 当前源码编号 |
| --- | ---: |
| `tower_schedule_id` | 50000 |
| `next_schedule_change_time` | 50002 |
| `floor_open_time_map` | 50005 |
| `schedule_start_time` | 50009 |
| `TowerCurLevelRecord.cur_level_index` | 50000 |
| `TowerCurLevelRecord.cur_floor_id` | 50001 |

全量包固定 `cur_level_record.is_empty=true`。增量 `PacketTowerCurLevelRecordChangeNotify` 只设层、间，没有队伍、祝福和上／下半状态。保存进度再齐全，也不能据此认为客户端已经收到可恢复本轮的全部信息。

`TowerFloorRecord` 的 `floor_id=14`、`passed_level_record_list=7`、`floor_star_reward_progress=10`、`passed_level_map=11`；`TowerLevelRecord` 的 `level_id=4`、`satisfied_cond_list=13`。

当前 `PacketTowerFloorRecordChangeNotify` 把传入的**本间星数**设为 `floor_star_reward_progress`，并固定发送 `level_id=1`、条件 `[1,2,3]`，未同步完整 `passed_level_map`。这与全量包从真实楼层记录构造的方式不同，必须作为已有兼容欠账处理，不能照抄作新功能模板。

### 条件通知和最终结算

`TowerLevelStarCondNotify` 外层是 `floor_id=11`、`level_index=2`、`cond_data_list=3`。内部 `star_cond_index=50000`、`cond_value=50001`，`is_pause=6`、`is_fail=7`。初始调用传入 `lostStar=4` 时生成三条默认空条件；下降时只通知失去的那一星。这是当前构造器行为，不是已确认的 7.1 最佳编码。

`DungeonSettleNotify` 通过 field 1536 嵌入 `TowerLevelEndNotify`，后者定义：

| 字段 | 编号 | 当前来源 |
| --- | ---: | --- |
| `finished_star_cond_list` | 15 | 从 1 到本次星数 |
| `reward_item_list` | 9 | 本次首通入口返回的配置奖励列表 |
| `is_success` | 14 | 当前挑战成功状态 |
| `next_floor_id` | 50000 | 最后一间完成后查下一层 |
| `continue_state` | 50001 | 0、1、2 三种继续状态 |

因此继续按钮问题可能来自占位字段，而非 `hasNextLevel()` 算错。失败和退出使用普通 `BaseDungeonResult`，不能假定会携带同样的 Tower 明细。

## 共用副本消息与按钮语义

| 消息 | CmdId | 深渊相关行为 |
| --- | ---: | --- |
| `DungeonChallengeBeginNotify` | 7266 | 发挑战 ID、索引、组及参数列表 |
| `DungeonChallengeFinishNotify` | 20234 | 报告当前半场挑战结束，不等于整间结算 |
| `DungeonSettleNotify` | 23138 | 整个副本结果及嵌套 Tower 明细 |
| `CanUseSkillNotify` | 918 | 入场／重试禁用，Lua 工作台重新启用 |
| `DungeonDieOptionReq` / `Rsp` | 873 / 22925 | REPLAY 调重试、CANCEL 调退出、REVIVE 调通用复活；返回空响应 |
| `PlayerQuitDungeonReq` / `Rsp` | 26190 / 20129 | 调退出，发送空响应；实际返程可异步完成 |
| `DungeonRestartReq` / `Rsp` | 9381 / 25275 | 普通材料／天赋／圣遗物领域的领奖后重开入口，保护器拒绝深渊 |

`DungeonDieOptionReq` 当前并不根据 `tryRestartDungeon` 的 boolean 返回错误响应。收到其空响应不能认定场景已经成功重建。REVIVE 的存在也不应被表述为已按深渊完整规则限制复活；验收应记录客户端是否会在此状态发送该分支及服务端效果。

进入场景还依赖普通传送握手、实体出现、队伍通知和工作台交互。下半只更新队伍列表而未生成可操纵实体，会表现为无法移动、放技能或切角色；`tryUseTemporaryTeam` 当前会移除旧半场实体并调用 `scene.spawnPlayer`，不能删除这一步而只保留空的切队通知。

## 祝福与月度效果的实现边界

资源包含 `TowerBuffExcelConfigData`、`towerBuffConfigStrList`、楼层 `floorLevelConfigId` 和排期 `monthlyLevelConfigId`。当前入场响应固定提供 `[4,28,18]`，不按候选权重生成；未找到 `TowerBuffSelectReq` 的完整 Handler、选择保存、持续期管理或效果应用链。

资源中的 4 对应 `buffId=701004`、持续本层；28 和 18 分别对应 701028、701018，持续本间。这只能解释配置含义。发送 ID 不等于效果已应用，`monthlyLevelConfigId` 有字段和 getter 也不等于渊月祝福已生效。

客户端有怪物预览组件，资源亦有 `firstMonsterList`、`secondMonsterList` 或较新预览索引；实际服务端出生怪物由 Lua 控制。混合年代资源可能使预览与实战不同，应逐组对照，不根据 UI 图标修写怪物属性。

## 后续协议恢复方法

1. 先记录客户端可执行文件与元数据 SHA-256，确认仍匹配现有提取工具的支持基线。版本改变时不得直接套旧地址。
2. 每次只执行一个可复现操作，例如打开深渊、提交两队、选择祝福、领取星级奖励或点击重试；记录时间、场景、按键和相关出入站包。
3. 比对注册器是否有 Handler，以及 `GameSession` 是否实际发送；区分未实现请求、负 CmdId 丢弃、字段不匹配和业务拒绝。
4. 沿原生序列化／解析证据确认 field number、wire type、嵌套类型与枚举；不要按相邻 CmdId 猜测消息，也不要把对象字段偏移当作 protobuf 字段号。
5. 先补固定 payload 回归，再验证真实 `GameSession` 路径，最后做原生客户端操作验收。单独构造 protobuf 仍不足以证明网络闭环。

只读提取命令：

```powershell
python tools\extract_client_type_names.py 'C:\GAME\Genshi' --output local\tower-doc-audit-20261021\client-type-metadata.json --include-fields --include-methods --include-parents --include-packet-ids
```

输出包含大量与本任务无关的客户端符号，应保留在忽略目录。公共接手资料只保存必要的小范围结构摘要和来源指纹。
