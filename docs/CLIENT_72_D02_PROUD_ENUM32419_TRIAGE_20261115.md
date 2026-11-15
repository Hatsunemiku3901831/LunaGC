# Proud enum32419 声明判断与共享 Pair 消费复用

`R3-PROUD-ENUM32419-QUERY-TRIAGE` 和 `R3-PROUD-PAIR-KEY-CONSUMER-REUSE` 已完成限定验收。复核[既有查询](CLIENT_72_D02_PROUD_ENUM32419_CONSUMER_QUERY_20261115.md)的 116 个唯一字面 enum32419 显参声明，唯一成熟的 Pair 消费候选已有验收来源，直接复用；本轮未新增取窗、测试、PE 或目标运行。

`66972.m554622 / 153a802a0` 的显参为 17694 和 enum32419；`17694.field83712 / +10` 是 `52802[]`。已有完整 148B 原窗 `[153a802a0,153a80334)` 记录的正常路径中，模式 flag 为零且 holder/数组非空时，按 8B 元素以低 32 位比较显式 key，首匹配将高 32 位 encoded54724 送入已验 `14f905140` Float getter；未命中或计数非正的正常返回保留已清零的 XMM0。formal return、hidden/native ABI、null 和 IFix 分支未知。type54724 是 encodedFloat 路径，不当作普通业务 enum 或实际 Float；enum32419 的 `FIGHT_PROP_*` 名称仅是声明线索，常数值未知。

这闭合了共享 Pair 的静态 key 检索→encoded value→getter 消费正例，修正此前“仅布局”的过窄当前结论。该正例的输入是 17694，尚未证明 `Proud26072.field127897` 或 `59500.field295065` 到该 consumer 的来源绑定；实际 row、M、非空 cache 与 key、同 epoch 选择及当前采用未知，不批准 Proud addProps 业务 alias。固定 signature 查询未覆盖 U32/Object/VAR 擦除、return、隐式 receiver 或全部 body；不能泛称全客户端无 consumer，也不把 52802 整列归 A。`UpdateRelicProp` 等声明没有当前 D-02 目标绑定，不因此新开 UI 或全 codec 窗口。

固定私有输入在 `local/migration72/d02-resume-20261115/proud-enum32419-triage/`：`triage.private.json` SHA-256 `02490561a9938f5943c3727c0143631bb3dc9a6f4aa1872567bea088ea18a462`，`reuse-spec.private.json` SHA-256 `cf55cf52e98fb0deab9279e9749af94b17ac8e05d2004a2bf7aed5587bf648e1`。既有 `local/migration72/d02-resume-20261109/r3-critical/windows1.private.json` 当前 SHA-256 `b6ccd552385701c22fc82d18376da29871f6afd8abeeef673892c195ea551684`；其历史 body SHA-256 `37d261e798aa33a155e9abeac6a0b42a31b44d813dcd6e8ae22f01b6a8420aa8` 是旧记录，JSON 无 rawhex，本轮未造 raw E8 或重算 body hash。

所有 A 返回/构造、`151841361`、`1488a5772` / `14c5108da` 后段、B loader 和 preview 冻结保持；本项未新增许可。下一步由主线审核准确依赖与缺少的来源输入，尚未规划的工作不标为全局阻塞。无新现场或运行材料变更，无构建、部署或客户端验收；D-02 / F-05 / D02-R6 仍为 0/6，局部完成不提升父项或 ready。
