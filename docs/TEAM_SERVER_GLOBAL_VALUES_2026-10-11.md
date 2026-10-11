# 队伍服务端全局值的 Lua 读写

## 已接入

`SetTeamServerGlobalValue(context, uid, key, value)`、
`AddTeamServerGlobalValue(context, uid, key, delta)` 和
`GetTeamServerGlobalValue(context, uid, key)` 已接入实际队伍能力值表。
旧 Set 接口只返回成功，且使用整数参数；现在小数值可以保留，Add 和 Get 也可从 Lua 调用。

- 玩家按 UID 在当前调用场景内查找，校验玩家、世界、队伍所属玩家和当前队伍生命周期。
  不存在的玩家／队伍、失效对象及非法写入返回 `-1`，正常写入返回 `0`。
  读取不存在的值返回 `0`，不会创建键。
- Set／Add 只写 `SGV_` 前缀的服务端值。Get 与原始接口一样读取现有动态能力值，
  可读取服务端已经存在的其他名称；能力求值使用同一张表。
- Set 可写任意有限 float 值。Add 首次创建直接使用增量；已有键的增量结果限制在
  `0` 至 `Float.MAX_VALUE`。因此缺失键的负数创建与已有键的负数递减有不同语义。
- Lua 的 UID 必须为正整数，名称必须为字符串，写入量必须为数值。
  拒绝字符串／布尔值的隐式数值转换、小数 UID、非有限值和转成 float 后溢出的参数。
  已损坏的旧数值不能用于 Add，但可用有效 Set 替换。
- 写入立即更新服务端状态并刷新现有全局值能力监听器，向场景广播
  `ServerGlobalValueChangeNotify` 的**最终绝对值**。客户端迟到的 SGV 能力确认不能覆盖它。
- 同一队伍的并发增量、更新及通知保持顺序。场景初始化前延迟的通知保留实体 ID、
  UID、队伍对象和生命周期；换队、离场、修改 ID／UID 或失效后不再发送旧快照。
- 场景初始化回调改用并发队列。多个玩家同时排队不会覆盖其他回调，回调中追加工作也会
  继续处理；入队时初始化恰好完成的工作仍执行一次。

这些值保存于当前队伍实体，不作为跨世界／断线后保留的活动进度或货币余额。
接口接入不代表引用它的所有活动都已完整还原。

## 固定参考与协议

参考 [Havesten/hk4e-sources 的 ScriptLib](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/script/script_lib.cpp)：
Set／Add 通过场景内玩家取得普通队伍实体，操作 float，缺少玩家／队伍返回失败；
Get 读取其动态能力值。
[Creature](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/creature/creature.cpp)
的 Set／Add 确认了 SGV 前缀、创建／增量边界和最终值的场景广播。
`AbilityComp::isServerGlobalValueKey` 同样在该固定版本核对。
下载文件经 Git blob SHA-1 和字节数核对，来源证据保存在本批本地目录。

当前 7.1 客户端的包号 **1441** 和 key **7**、entity **10**、value **11**
沿用[已核对的通知](TEAM_GLOBAL_VALUES_2026-10-11.md)。本批没有改写包号或字段。
与队伍能量的 `ServerUpdateGlobalValueNotify` 增量／赋值操作是不同接口。
场景初始化的延迟发送和对象生命周期校验是本项目的运行适配。

## 验证范围

新增 `TeamServerGlobalValueTest` 使用实际 LuaJ、真实队伍、现有能力监听器、客户端能力
确认入口、场景初始化回调及独立协议字节，验证小数、创建／夹限、玩家隔离、无效参数、
旧快照失效、并发增量和通知顺序。原始资源 `LanlaluoCommander.lua` 的进入／离开区域函数
直接执行，确认进入玩家的队伍等级设置和清零，实际调用本项目的 Lua 接口。

新增 40 项检查及相邻场景玩法、能力生命周期、队伍值、烘炉、时间轴和协议回归，
共 335 项／19 套件通过，无失败、错误或跳过；最终回归和打包 32 秒。
544 处输入和 7 个变化的运行类与构建一致，新增 1 个运行类，无删除或意外类变动。
本地构建与测试证据位于 `debug/aster-mid-20261011/batch78/`，候选包 SHA-256 为
`b5a50b5d0349a67ef7d6bb8fc4997537dfab9c7a4da08d8fcb53399467e962cf`。
候选包更新后仍需游戏内验证，本批不切换日常服务；完整活动流程和原始大型场景仍待接入。
