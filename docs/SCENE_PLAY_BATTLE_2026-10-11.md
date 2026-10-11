# 场景多人战斗的基础流程与 7.1 通知

## 本次接入

新增按场景管理的 `ScenePlayBattleManager`，接入场景更新、玩家离场、组卸载和场景销毁。
活动控制器提供已满足活动资格的玩家和已加载的原始组；管理器验证场景、世界、玩家对象、
资源人数范围和组对象，随后按准备、就绪、开始倒计时、战斗和结算推进。

- 使用 `MpPlayGroupExcelConfigData` 的准备时长、玩法 ID、`groupList` 和
  `serverLuaCallGroupList`。当前第三章资源是准备 20 秒，三个辅助组和一个主控组。
- 状态值为 NONE 0、INIT 1、PREPARE 2、READY 3、PRESTART 4、START 5、STOP 6。
  READY 回调通过 `PrestartScenePlayBattle` 设置时长、倒计时、模式和进度阈值。
  原始第三章使用 600 秒、10 秒倒计时和 `{0,1500,3000,4500,6500}`。
- `AddScenePlayBattleProgress` 只在 START 接受非负整数增量，按 uint32 上限饱和，
  达到最后一个阈值胜利；到期后的提交先结算失败，不延长挑战。
  跨越多个分段只发送一次旧分段、新分段和最后分段索引。
- 状态事件参数为玩法类型、玩法 ID、状态；分段事件为旧分段、新分段、最后分段索引；
  结果事件的胜利／失败值放在 `param3`（1／0）。停止不另发 STOP Lua 状态事件，
  避免原始 `AsterBig.lua` 对不支持的状态执行失败分支。
- 离场后低于资源规定的人数，或房主离场，发送中断前的状态。准备期间清空状态，
  READY 及其后发送非胜利结算；结算只执行一次。
  单人 Direct 模式允许房主独自开始，Match 使用资源的最少人数。
- `GetScenePlayBattleUidValue` / `SetScenePlayBattleUidValue` 保存每轮、每名参与者的
  有符号 int64 值；未找到的键返回 0。终局回调可继续读取和更新尚在场的参与者数据，
  下一轮使用全新数据。该表不作为货币或永久活动进度。
- `GetScenePlayBattleHostUid`、`GetScenePlayBattleType` 以及
  `ScenePlayBattleUidOp` 已接入；操作通知使用实际在场的机关实体，验证所选参与者，
  保留参数数组、目标数组、索引和持续秒数。
- `group_id=0` 从当前脚本／实体上下文解析，对已卸载或被替换的组返回失败。
  Lua 的 UID、ID、数组和数值在 Java 转换前验证，避免小数或字符串被截断。
  LuaJ 只接受正负 `2^53-1` 内的整数统计值；读取超出此范围的 Java int64 明确报错，
  不返回舍入后的数值。
- 场景初始化发送当前活动战斗列表，结算后的战斗不再列为活动战斗。

`ScriptArgs` 增加事件 UID，场景 Lua 上下文提供 UID、组 ID 和源／目标实体 ID，
并保留 ScriptLib 接收对象及现有能力上下文。

## 排队与生命周期

战斗状态与通知在场景锁内按顺序更新，Lua 保持异步执行。通过
`SceneScriptManager.callEvent` 排队的已有战斗组事件捕获当时的轮次。
准备下一轮前，上一轮的战斗回调及这些组事件必须执行结束。
场景锁内不阻塞等待回调；未结束时拒绝开始新一轮。
组卸载、组对象替换、场景关闭或轮次替换后，旧回调的检查失败。
相同 UID 的新玩家对象不能接管断开玩家的旧统计或参与身份。

## 参考来源

接口和状态流程参考固定提交的
[ScriptLib](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/script/script_lib.cpp) 与
[BaseScenePlayBattle](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/player/scene_play/base_scene_play_battle.cpp)。
活动调用、准备和结果回调参考
[AsterBig.lua](https://github.com/Ahanlei123/2.8_live_data/blob/f25e3155e38f9dc8f647493bf23a935147caf59e/lua/common/AsterBig.lua)。
参考源码按 Git blob 摘要验证，仅作为本地只读资料；未照搬其旧协议字段或运行其中程序。

## 当前客户端字段

只读核对用户提供并确认的 7.1 客户端；本次记录使用的文件摘要：

| 文件 | SHA-256 |
| --- | --- |
| YuanShen.exe | `7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e` |
| Native/Data/Metadata/global-metadata.dat | `469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161` |

| 消息 | 类型 / 读取地址 | 包号与字段 |
| --- | --- | --- |
| ScenePlayBattleInfo | CKIJOLOBLLB / 22401 / `0x14c7c69e0` | type 2、progress 3、duration 5、play_id 7、mode 9、start_time 10、state 11、play_type 12、prepare_end_time 13、progress_stage_list 15 |
| ScenePlayBattleInfoNotify | PLMPEOBIGGG / 28496 / `0x14ae2dcd0` | 包号 2762；battle_info 2 |
| ScenePlayBattleInfoListNotify | HBMIGDCODHI / 85087 / `0x151131fa0` | 包号 20153；battle_info_list 4 |
| ScenePlayBattleInterruptNotify | DHDKLGMDPBN / 64399 / `0x153f98880` | 包号 20684；interrupt_state 1、play_type 6、play_id 13 |
| ScenePlayBattleUidOpNotify | JBJHIEOFJBK / 49234 / `0x14cc60310` | 包号 6526；uid_list 1、param_duration 3、entity_id 4、param_list 7、play_id 8、op 9、play_type 10、param_target_list 11、param_str 12、param_index 14 |
| ScenePlayBattleResultNotify | NCEOLMKDOJC / 73015 / `0x14e6b15d0` | 包号 8678；play_id 1、is_win 4、cost_time 5、settle_reward_info_list 8、settle_player_info_list 9、play_type 13 |
| ScenePlayBattleSettlePlayerInfo | GFLEPCONJAF / 62733 / `0x1473fff10` | online_id 2、nickname 3、card_list 4、head_image 5、uid 8、param 9（int64）、statistic_id 10、profile_picture 13 |
| ScenePlayBattleSettleRewardInfo | GJJGOHMLDOI / 83082 / `0x1510f8170` | reward_item_list 1、uid 10 |
| ExhibitionDisplayInfo | KCMCFJNHADI / 57194 / `0x151338d10` | detail_param 1、param 2、id 3；修正本项目原先 id 1 / detail_param 3 |

使用实例字段类型和偏移、实际读取分支、共同混淆成员及已有已核对消息交叉确认。
`mode` 的含义由旧消息结构、其他已确认字段和剩余单一标量对应推断，字段 9 的读取分支
已确认。结果的两个数组同时核对实际泛型子类型，不能按旧描述符或成员排列调换。
ProfilePicture（49812）和 ItemParam（62819）的嵌套读取器也已核对。
包号直接读取五个返回函数；状态 0–6 直接读取当前元数据的整数默认值。
ExhibitionDisplayInfo 核对实际跳转表 `0x151338e4c`，该修正同时用于既有原素烘炉称号。

## 已验证与剩余工作

新增 46 项检查，使用实际 LuaJ、真实玩家／机关、实际场景更新和异步事件执行器、
当前资源表及独立字节样本。覆盖准备／开始边界、跨分段进度、超时竞争、一次结算、
人数变更、房主离场、UID 替换、卸载／销毁、等待旧回调、复刻数据清空、整数精度、
非法参数、机关操作、全部消息字段及嵌套数组。
相邻封印战斗、原素烘炉、能力与实体上下文、时间轴和协议描述符回归共
217 项／15 套件通过，无失败、错误或跳过。

这是第三章的基础依赖。第三章仍需接入原始大型组、PlayTeamEntity 能力机关、
能量提交与持续效果、入口及匹配、战斗评分与称号、通用奖励机关、40 树脂领取和
熄星精粹。当前管理器只发送真实战斗结果及参与者基本资料，不填充未完成的评分、称号
或物品奖励；第三章仍不开放为可游玩活动，完整 7.1 客户端流程尚未验证。

本地证据位于被忽略的 `debug/aster-mid-20261011/batch76/`，含固定参考文件摘要、
11 个实际读取器、包号／枚举核对、独立测试结果及构建输入与包的校验。
