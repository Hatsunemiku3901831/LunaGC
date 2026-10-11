# 未归的熄星：陨星回收接入

## 已接入的流程

本批将第二章的营地、难度请求、原始封印战斗和领奖串联。活动目录仍为「还原中」。

- 资源中的 15 组营地批次，每批四处，共 60 处回收点。
- 恢复 60 个控制组、60 个战斗组及 `AsterMiddle.lua`，资源目录缺失时使用包内回退。
- 按玩家当前排期、第 3 天、冒险等阶 20 和内容结束时间判断开放；附近加载使用独立世界组。
- 难度选择检查实际装置、玩家位置、加载状态、房主身份、排期与资源的世界等级范围。
- 原 Lua 收到装置配置 ID、难度 ID 和怪物等级；两条怪物创建路径均使用所选等级。
- 成功封印后生成原始奖励点；失败、超时、组卸载和旧回调不能创建奖励。
- 查看奖励不扣树脂。确认领取使用 20 原粹树脂，发放冒险阅历、好感经验及熄星能量。
- 领取次数推进六项累计任务，阈值为 5 / 10 / 15 / 20 / 25 / 30；任务奖励通过既有活动领奖入口领取。
- 奖励生成时固定房主世界内的玩家资格；每名符合活动门槛的玩家独立查看、支付树脂和领取。
- 联机首次成功领奖后保留奖励点 10 秒；全部具备资格的玩家领完，或奖励点到期后结束营地。
- 同批四处奖励点全部结束后刷新下一批。世界地图使用房主营地，累计任务使用各人的领取次数。

难度 8、9 的本地资源世界等级列表为空，因此不自行扩大其可选范围。
掉落来自 `Server/DropTableExcelConfigData.json` 的 `210010000`～`210010080`，
不是把 `rewardId` 9101～9109 当作实际奖励表 ID。这些 ID 属于奖励预览。
每次回收获得 100 冒险阅历、10～20 好感经验及 15～45 熄星能量，数量由对应掉落表决定。

## 原始资源与状态参考

脚本来自固定版本的
[2.8 活动资源归档](https://github.com/Ahanlei123/2.8_live_data/tree/f25e3155e38f9dc8f647493bf23a935147caf59e/lua/activity/2001)。
原始文件及派生营地点位记录在包内 `historical-scripts/Activity/2001/sources.json`。
新增脚本仅转换到加载器要求的路径并统一换行；原始 SHA-256 和包内 SHA-256 分别记录。
营地点位从原活动 block 提取，装置、奖励配置及战斗组从对应原 Lua 提取。
20 个控制组使用 `AsterMiddle.lua`，它读取局部变量 `defs`，因此启用快速 require 时也必须内联。
当前资源目录中的同名 Common 只有占位注释，加载器对此使用包内原始脚本；
包含实际代码的资源版本仍优先使用。

难度、封印结束、领奖累计和营地刷新语义参考固定版本的
[AsterActivity](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/player/activity/aster_activity.cpp)。
参考方法为 `selectAsterMidDifficulty`、`onSealEnergyChargeBattleEnd`、`onTakeGeneralReward`、
`onGeneralRewardDestroy` 和 `refreshMidGroups`。旧协议字段号不作为 7.1 依据。

协作资格取自同版本的
[ScriptLib::CreateAsterMidGeneralRewardGadget](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/script/script_lib.cpp)，
它遍历房主世界玩家，不额外设置距离门槛。加入时刻晚于奖励生成的玩家不会新增资格；
已经具备资格的玩家断线返回后仍可在有效期内领取。
该函数将奖励初始到期设为 0、销毁冷却设为 10 秒。
[GadgetGeneralRewardComp](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/gadget/general_reward_comp.cpp)
在联机首次成功领取后启动该冷却，在剩余玩家全部领取或计时结束时销毁奖励。
这些旧服务端文件只用于行为参考；7.1 通知字段另行从当前客户端核对。

## 当前客户端协议证据

以下是 2026-10-09 对用户提供的客户端进行的只读核对。
对应 `YuanShen.exe` SHA-256：
`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`。
本地证据保存在被忽略的 `debug/talent-cooldown-20261009/`。

| 消息 | 读取／写入函数 | 字段 |
| --- | --- | --- |
| AsterMidCampInfo | `0x14bbdd5e0` 写入 | pos 13、camp_id 15 |
| AsterMidCampInfoNotify（9033） | `0x150e24410` 读取 | camp_list 13 |
| AsterMidInfoNotify（7830） | `0x14b8f5dc0` 读取 | info 6 |
| SceneGadgetInfo | `0x14b863e60` 读取 | general_reward 43，嵌套类型 79128 |
| GadgetGeneralRewardInfo | `0x14e7e47f0` 读取 | resin 1、dead_time 2、remain_uid_list 3、qualify_uid_list 4、item_param 5 |
| GadgetGeneralRewardInfoNotify（2019） | `0x14ec35800` 读取、`0x14ec35630` 包号返回 | entity_id 10、general_reward_info 13；2026-10-11 补充核对 |

一般奖励信息现有字段号与本次客户端相符。补充奖励点更新通知，首次领取后同步剩余玩家和绝对到期时间。
难度请求（29428）、回包（2401）和封印消息的核对见
[活动协议](ASTER_PROTOCOL.md)与[封印战斗](SEAL_BATTLES_2026-10-09.md)。

## 保存与重复领取

房主活动文档保存当前批次、成功奖励点、资格、未领玩家、预留和绝对到期时间。
每名玩家的活动文档保存领取凭据、次数和熄星能量；客人的进度不依赖自己的营地批次。
同一个营地在新批次生成新的奖励凭据，旧凭据不能领取新的奖励。
新复刻使用新排期文档，旧排期不能发奖；重新进入世界可以恢复已经成功的奖励点。

领取先确认房主奖励点与领取者 `pendingMidClaim` 的预留保存，再扣树脂和写入经验，最后确认各自进度。
预留保存失败不会扣树脂；树脂不足保留奖励点并清除预留。
奖励写入或最终确认结果不明确时保留预留并阻止重放，管理员核对实际库存、经验及进度后处理。
领取者最终保存已经确认而房主确认失败时，保留已确认的个人凭据和房主预留。
含不明确预留的奖励点不能自动结束营地；需要核对后处理，不能用到期清理替代发奖核对。
旧版仅房主奖励点升级为固定房主资格，保留旧的未确认领奖，不向后来加入的玩家补造资格。
任务领奖和陨星领奖共用预留互斥，避免同一活动同时重复发奖。

## 验证范围与剩余工作

检查覆盖 120 个原始组的实际加载、共用脚本内联、真实 Lua 难度选择和封印结束、
所选怪物等级、奖励实体交互、实际树脂及背包发奖入口、六项累计任务、
树脂不足、重复领取、排期错误、保存失败和重新加载的预留。
本次增加真实 Lua 下的独立领奖、逐人查看门槛、联机好感经验入口、迟加入、断线返回、
奖励点重载与到期；并使用隔离 Mongo 检查双文档保存及写入已接受、确认丢失的重载结果。
场景测试的经验接收端仍是隔离替身，数据库检查仅验证活动文档；不等于真实客户端的完整游玩验证。
相关回归和候选运行包证据位于 `debug/aster-mid-20261011/batch72/`。

仍需继续补齐：

- 两个原生余额槽与物品 109 / 110 的语义对应、活动商店、菲谢尔领取及剧情任务。
- 第三章的多人挑战、能量提交、匹配和领奖。
- 7.1 客户端的地图流式加载、操作面板、战斗与领奖显示实机验证。

本批提交代码并更新本地候选包，服务切换需另行进行。
