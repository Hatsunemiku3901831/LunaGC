# 旧号 Weapon 239 字段来源与行级依赖

`R3-OLD-WEAPON239-APPROVED-FIELDS-PLAN` 已完成限定资料和依赖审阅。旧号 10727 个活动 Item 行、9128 个不同 ID 与目标 Weapon 键相交为 **239 种、1663 行**。其中 `11101` 的同源六键片段及限定 loader 已验，可只读复用，覆盖 1 种、27 行；其余 **238 种、1636 行**尚无获批的完整六键新读出及限定 loader。`11501` 有 8 个活动行及旧逻辑样本，但不是新批准的六键 canonical 片段。集合与键覆盖不证明完整装备恢复。

既验六键为 `id`、`itemType`、`weaponProp`、`weaponPromoteId`、`skillAffix` 和 `gadgetId`。11101 的三个同源片段、字段职责、consumer、payload 及完整 pin 见私有 `local/migration72/d02-resume-20261116/old-weapon239-approved-fields-plan/spec.zh.md`；本次按原验收范围复用，不重跑 11101、局部保存、TPS8 或候选覆盖。

准确依赖来自**行级轨迹**：固定 `local/migration72/d02-resume-20261109/r3-item/main-third.json` SHA-256 `9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2` 的 `selectedRows[0]`（11101）和 `selectedRows[1]`（11501）分别保存 constructor/parser 的 `executedAddresses` 与 `models`。两行各在 resource ctor `15449a920` 记录一次、parser `154498500` 记录两次 `1405336c0` A model 命中。四条轨迹未记录原 A caller PC，准确 site 未知；不能从邻近地址推断，也不新取源。完整原窗范围与 hash 沿该私有规格及原报告记录，不用 whole 轨迹推断行级关系。

生产其余 238 种新值所需的已知 ctor/parser 构造和返回涉及既有 A 准确共用入口依赖，保持冻结。移除被拒动作及其产物后，旧 cohort、字段来源和 11101 已验事实仍可独立复用；当前未识别到已批准且独立于该依赖的逐行新值来源与 oracle。不能用旧 A 对象、raw bytes、候选 JSON 或出生常量充作新行值；也不扩大原 A 执行包或自动派 Daybreak。这里的停止不是新拒绝，不泛封所有 Weapon 或潜在 consumer。

本次仅做只读资料和依赖审阅，没有新 native、JVM、测试、构建、部署或客户端验收。完整 Avatar/Item/装备及旧号 Player 恢复仍未知，局部资料审定不晋级父项或 ready；D-02 / F-05 / D02-R6 仍为 0/6，A/B、preview 与准确冻结边保持。
