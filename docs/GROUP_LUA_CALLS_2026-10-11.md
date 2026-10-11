# 跨场景组 Lua 调用

## 已接入

`ExecuteGroupLua(context, group_id, function_name, param_list)` 同步调用目标组；
已知但未加载的组按现有场景加载流程加载。
`ExecuteActiveGroupLua` 只查已加载组，不产生加载操作。

函数收到 `destination_context, previous_context, param1, param2, param3`，
参数表允许 0–3 个连续的 uint32 数值。目标上下文保留调用 UID、使用目标组 ID，
源／目标实体 ID 从零开始；第二个上下文保留原调用对象。
被调函数的 int32 返回码原样传回。无目标、无函数、参数错误、无整数返回值或
脚本异常返回 `-1`，修复函数绑定后可重新调用。

目标执行时切换 `ScriptLib` 的场景管理器和当前组，清除原实体及事件参数。
嵌套调用、普通返回和异常均恢复四个槽位。普通事件结束也清理其事件参数，
避免同一工作线程的下一次调用使用上次事件的数据。

调用前后核对调用组、目标组的实际加载对象及活动可用条件，
拒绝已卸载／替换的组和已关闭的历史活动。场景关闭后也不能继续调用。
调用在场景锁内完成；来源／目标所属场景战斗增加同步调用计数，
直到函数返回才释放，防止被调脚本结束一轮后在同一次调用中替换这轮战斗。

## 原始行为依据

固定参考 [Havesten/hk4e-sources 的 script_lib.cpp](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/script/script_lib.cpp)：

- `ExecuteGroupLua`／`ExecuteActiveGroupLua` 分别传递强制加载开关 1／0。
- `internalExecuteGroupLua` 读取 uint32 参数表，最多三项。
- 新建目标 `ScriptContext`，设置场景、玩家 UID 与目标组；原上下文单独传递。
- 调用结果为目标 Lua 函数的整数返回值，查找／执行失败返回 `-1`。

本地固定原文件、来源和哈希在 `debug/aster-mid-20261011/batch72/`。
原始「未归的熄星」第三章使用该接口在主控组、阶段组之间调用招怪逻辑；
其四个原始场景组本批已下载并按固定树的 Git blob 校验，
研究文件在 `debug/aster-mid-20261011/batch80/sources.json`。

## 验证

48 项新增检查通过，覆盖真实 LuaJ 调用、参数／返回值边界、两个上下文、
嵌套与异常恢复、禁止加载模式、缺失及修复的绑定、卸载／替换、历史活动关闭、
场景销毁，以及同步调用期间禁止替换已结束的场景战斗。
与场景玩法队伍机关、队伍全局值、能力回调、时间轴、烘炉和陨星回收等回归一起，
共 **434 项／22 套件通过**，无失败、错误或跳过；最终回归及打包 33 秒。

559 处输入、10 个变化的运行类和包内历史资源与候选构建一致；
新增 2 个运行类、无删除。证据在 `debug/aster-mid-20261011/batch80/`，
候选包 SHA-256 为
`a6cdd24ab2e35d06a5db73617964d6a6c20903bbe92b60c5f1b788596b07ea42`。

首次编译修正匿名 Lua 函数内的 Java 静态方法名称遮蔽；首次测试的唯一失败为
测试夹具使用了错误的 `MpPlayMatchData` 字段名，修正为实际 `minPlayers/maxPlayers`。

本批未切换日常服务。大型原始组、怪物潮、能量提交、入口／匹配及奖励还需继续接入；
第三章仍不发送完整可玩标记，完整客户端实玩尚未验证。
