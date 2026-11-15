# 旧号活动 Item 保存引用限定投影

`R3-ACTIVE-ITEM-SAVED-REFS` 已完成固定快照的纯数据投影，未生成目标资源、新 Java 或 Player 恢复结果。候选族沿既有覆盖与 TPS 审阅划分，不证明目标类型、资源值、装备槽位或 ready。

固定 7 项 pins、source manifest、原 decoder 和源 BSON 在扫描前后保持；未重新导出数据库或重跑旧八键提取。items 共 12254 文档 / 4838984B、avatars 共 121 文档 / 163171B，均完整读至 EOF；所有旧 Item 八键及角色共同三键逐项匹配。候选 Reliquary 358 行 / 183 个 ID、TPS 8 行 / 8 个 ID 与固定覆盖及审阅一致，Avatar cohort 为 121 行。

| 保存引用 | 固定快照中的限定结果 |
| --- | --- |
| Reliquary `mainPropId` | 358 个正引用，28 个不同正 ID |
| Reliquary `appendPropIdList` | 3163 个正引用，22 个不同正 ID |
| TPS `tpsAccessoryIds` / `tpsMaterialAccessoryIds` | 两列表各 12 个正引用、12 个不同正 ID；各 3 行非空、5 行显式空 |
| Avatar `tpsWeaponIds` | 121 行均为显式空数组 |

五个新字段均无 missing 或 null；358 条候选 Reliquary 中正 `equipCharacter` 为 350，实际零为 8，均按原值保留。结果保留列表顺序、重复、BSON int32/int64 与 missing/null/empty/zero 的区别；正关联不证明目标槽位或恢复成功。保存引用将后续需求收敛为 main 28、append 22、TPS 配件各 12 的候选引用，不证明 target 资料或玩家已加载。该旧号的 Avatar TPS 武器列表为空不能外推其他账号，也不省略 `Inventory.restoreAccessories` 或自动授予条件；相关分支另行审定。

原执行只扫描 BSON 一次；随后仅用已生成投影和固定 coverage/TPS review 补逐 ID 汇总对照到 ledger，再将该对照合入私有 `project.private.py`。最终脚本未重新扫描 BSON，不能称其逐字节为首次执行版本。原数据及旧八键结果未改。

私有结果 `local/migration72/d02-resume-20261116/active-item-saved-refs/result.private.json` 为 402129B，SHA-256 `42f16eb1b83016f47a61668402c644dc58f167352d2d85f1b06d08001197e457`；同目录 `summary.zh.md` 与 request 保留执行范围、来源和 ledger。上游范围 `local/migration72/d02-resume-20261116/active-item-remaining-scope/sources.private.json` SHA-256 `fc23cfe08a62c77b804bf10d60290a68b4b77fbf5dfb69ea51810a9abe561206`，`scope.zh.md` SHA-256 `002096b509be15078febe0d248db0dd5ff142ee96a7d90bee34e7c1e014dfb9a`；其为必要性及普通包规划，不批准目标资源值。

未进行新测试、DB/JVM/native/PE、导出或客户端操作。完整属性、装备与实际 target 来源、Player 恢复和 ready 仍缺；A/B、preview 冻结及 D-02 / F-05 / D02-R6 0/6 不变。有限 metadata 声明查询另项进行中，不在本项冒称完成。
