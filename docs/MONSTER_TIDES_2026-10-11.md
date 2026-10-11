# 场景组怪物潮

## 已接入

`AutoMonsterTide(context, tide_index, group_id, config_ids, total, minimum, maximum)`
按场景组和潮次分别管理；存活数低于 `minimum` 时补到 `maximum`。
按配置表循环选择怪物，跳过仍存活或已被其他内容占用的配置 ID。
`total = 0` 为无限潮；有限潮只计算成功创建的怪物，不超出总数。

Lua 增加 `PauseAutoMonsterTide`、`ContinueAutoMonster`、`EndMonsterTide`、
`KillMonsterTide`，这四项参数分别为组 ID、潮次，End 另带结束原因。
暂停后仍记录死亡，恢复时立即检查补怪；结束停止补怪并保留幸存者的死亡记录，
清除则以 `VISION_MISS` 移走实际归属该潮的对象，不制造掉落或死亡／结束事件。
重复开启同一潮次返回失败；已结束的潮等存活怪物和最终回调全部结束后才释放 ID。

事件使用原始 `EVENT_MONSTER_TIDE_CREATE`、`DIE`、`OVER`：

| 事件 | param1 | param2 | param4 | source / source_name |
| --- | --- | --- | --- | --- |
| CREATE | 累计创建数 | 补充波次 | 怪物类型 | 潮次字符串 |
| DIE | 累计死亡数 | 0 | 怪物类型 | 潮次字符串 |
| OVER | 结束原因 | 结束时死亡数 | 0 | 潮次字符串 |

CREATE／DIE 保留实际怪物实体 ID。有限潮在下一次补充检查发现已达到总数时
发送 OVER，可能仍有幸存者；不是等全部怪物死亡才发送。

仅归属该潮的实际怪物对象可以更新计数；其他组、场景、同配置替代对象和重复死亡
通知均无效。组卸载、场景销毁和创建失败清理存活对象、监听器及待执行回调。
回调在场景锁内核对潮次／组对象后执行，拒绝已清除或替换的潮；历史活动沿用可用条件。
场景关闭先标记失效，清理过程中也不能重新开启潮。

Lua 参数在数值转换前检查：组、潮次与配置 ID 为正 int32，数量／结束原因为
非负 int32，存活上下限为正数且 `minimum <= maximum <= config_ids` 数量。
配置表必须连续、无重复 ID，并且组中存在全部定义。初始可用配置不足时不生成部分潮。
所有失败返回 `-1`，成功返回 `0`；`ContinueAutoMonster` 使用原始绑定名称。

## 原始行为依据

固定参考 [Havesten/hk4e-sources 的 MonsterTideComp](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/group/monster_tide_comp.cpp)
和 [ScriptLib 绑定](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/script/script_lib.cpp)。
按原实现核对上下限、循环顺序、无限潮、结束／清除区别和三个事件的计数、来源及怪物类型。
本地原文件、固定树 Git blob 与 SHA-256 在 `debug/aster-mid-20261011/batch81/sources.json`。
本批为 Lua／场景行为适配，没有新增或推断客户端协议字段。

## 验证与范围

测试覆盖真实 LuaJ 参数调用、实体创建／移除、补充上下限、有限及无限波次、
暂停／恢复、多个组与潮次、错误配置、重复／无关死亡、失效回调、场景关闭、
创建异常回滚，以及真实事件线程与清除操作的互斥。

新增 **65 项检查**，与跨组调用、场景玩法队伍机关、SGV、能力生命周期、
烘炉、陨星回收和时间轴等相邻回归一起，共 **499 项／23 套件通过**，
无失败、错误或跳过；最终回归及打包 88 秒。
563 处输入、13 个变化运行类和包内历史资源一致，新增 3 类，
删除 2 个旧的全场景潮监听器类，无其他删除或意外变化。
构建证据保存在 `debug/aster-mid-20261011/batch81/`，候选包 SHA-256 为
`cf49f227428cab775335d9933464b7c60b6eb4d035d9f20323e7c5b777458231`。
首轮修正匿名 Lua 函数继承的 `name` 字段遮蔽局部操作名的问题。

本批未切换日常运行服务。「未归的熄星」第三章仍需大型原始组、能量提交、
入口／匹配和奖励；完整客户端游玩仍待验证。
