# E-01 / E-01c / E-01d1 旧号 Talent 引用范围

固定旧号快照及 121 个当前 Depot 的实际 typed 读出，分别核对每个保存角色、当前 Depot key 和原行跨度。原 field105357 的 Talent 成员职责复用[目标消费链](CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md)。Java `Avatar.getTalentIdList` 取当前 Depot 和保存解锁列表交集。旧号加载的 `recalcConstellations` 与属性重算使用该集合查 Talent 行，随后按各自条件读取 `OpenConfig`。

- 121 个保存角色都有交集，共 726 个唯一当前查键，4 个活动角色包含其中 24 个。
- 一个角色另有 36 个非当前 Depot 的保存键。保留这些引用，当前 Depot 的上述两条调用不消费它们。
- 目标 Talent 行及 `OpenConfig` 字符串、对应 BinOutput 条目与实际 revision 待分别核定。行键范围可以直接用于筛选资源。

主线重跑 7 项测试和第三个 CLI。三次各 89,076 B，SHA-256 均为 `6ba484b3a4c65b3ed773e084fa95d559c91f1385abb0e7454c16606e1f76690a`。测试独立逐角色比较查键、当前 Depot、活动标记和来源跨度，并覆盖缺行、重复、错误归属和哈希漂移。结果只写私有 `local/migration72/d02-resume-20261111-asc/c-talent-refs/`。

```powershell
python -X utf8 -B -m unittest tools.test_client72_d02_old_talent_refs -v
python -X utf8 -B -m tools.client72_d02_old_talent_refs --output <新文件名>.private.json
```
