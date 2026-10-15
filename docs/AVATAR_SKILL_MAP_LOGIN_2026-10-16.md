# 旧技能存档的登录补全

本地登录日志在 `Avatar.getSkillLevelMap → Avatar.toProto → PacketAvatarDataNotify`
出现空指针：新技能没有存档等级时，`Map.putIfAbsent` 插入默认值后返回 null，
原代码立即调用 `intValue()`，使第一次登录快照构造失败。
技能库新增攻击模式技能等情况会触发这一缺口。

参考 AstaPS 固定提交
[`7cc148d1f752a43d4b9146b3b73f26875902546f`](https://github.com/MeChen618/AstaPS/blob/7cc148d1f752a43d4b9146b3b73f26875902546f/src/main/java/emu/grasscutter/game/avatar/Avatar.java)
中已有的对应修复，改用 `computeIfAbsent` 获取实际等级。

- 当前技能库中缺失或值为 null 的技能，在第一次读取时补为 1 级。
- 已升级技能保留原等级，其他技能库的存档记录保持原值。
- 保留原来的内存补全语义；返回值仍是当前技能库等级的独立副本。
- 原始等级、有效等级、角色登录快照和展示资料使用同一入口。

## 验证

新增 8 项测试，覆盖默认等级、已学等级、空值、其他技能库、返回副本、有效等级、
实际 `PacketAvatarDataNotify` 和展示资料。登录快照分别覆盖内存旧数据与真实 Mongo 冷读，
并检查保存后的默认技能为 1、原先的 6 级技能没有变化。

补齐测试夹具所需的队伍及技能库资料后，专门在构建容器中回放原始 getter：
8 项中 7 项均以实机同一个 `putIfAbsent(...).intValue()` 空指针失败。
宿主修复源码始终保留，回放后恢复容器输入并重新执行最终验证。

最终技能等级、队伍加成、技能状态、七七／芭芭拉及登录活动初始化相关
**162 项／7 套件通过**，零失败、错误和跳过，`test`／`jar` 成功。
2 个源输入核对宿主／容器 SHA-256，2 个变化类核对编译目录／JAR 字节。
候选 JAR SHA-256：
`fe697b8219a1d91528921e1162d5604bee2303be1b769667361b5330cdab30ba`。

实机异常位于 `debug/aster-mid-20261011/batch272/current-login-exception.log`；
原始回放、最终测试与产物核对位于 `debug/aster-mid-20261011/batch273/`，包括
`skill-login-causal-red-results/`、`skill-login-final-results/` 和
`skill-login-verification.json`。完整客户端登录仍以新的实际会话为准。
