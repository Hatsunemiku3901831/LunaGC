# 活动 Item 基类及派生 metadata 限定查询

`R3-ACTIVE-ITEM-BASE-DERIVED-METADATA` 已完成固定 metadata 的有限声明查询。v2 一次访问 89748 / 89748 条记录、检查 88083 条 parent 边；root `88968` 和 `34113` 各有 8 个后代，含 root 共 18 个声明、186 个 field、274 个 method、9 个 instance backing 引用。每 root 64、合计 130、每声明 128 field / 256 method 及输出 2 MiB 的 cap 均通过。

三项固定控制均通过：`63074.field313095/+38 → 23400`、`74316.field368811/+20 → 46431`、`87097.field434158/+20 → 20600`。这是有限声明身份和引用，不以继承、名称、形状或数量认定 Furniture/TPS 业务族，也不证明实际 row、无 A 初始化、字段业务 alias 或正式参数、返回和 ABI。Material / Reliquary 的已知角色来自既有具名 target 来源，并非此查询单独证明。

v1 四件原样保留，其失败结果 `result.private.json` SHA-256 `2b26213595858ff4bd7b3d7d45bfe07be08cc338321941bff62b540aaec57a22`：执行时误将 pickle 的整个二元容器视作 records 列表，访问 0 条，root controls 未查询；这不是缺来源或新安全拒绝。主线核既有 pinned consumer 后批准机械 v2：解包 `records,cells`，instance 按 `offsetKind` 判断而非仅 `attrs&10`，使用 decoded pickle pointer `#/0/index`，并将 Weapon offset 控制设为 `+20`。predicate、root、cap 与完整账要求未改；v1 失败不追认为通过，v2 三项 controls 和原产物自检一致。

私有 `local/migration72/d02-resume-20261116/active-item-base-derived-metadata/result-v2.private.json` 为 204153B，SHA-256 `9bca2a9545dce61152658d0c43c83b83586855f9bbbe03e4c1ae11cc30c966f7`；同目录 `summary-v2.zh.md` 和 `query-v2.private.py`（SHA-256 `3810b4271b1508f6c930ee47ff974700884ab9376b1ff21b209132ae044b906d`）保留查询及账本。固定上游范围 `active-item-remaining-scope/sources.private.json` SHA-256 `fc23cfe08a62c77b804bf10d60290a68b4b77fbf5dfb69ea51810a9abe561206`。

本项无独立旧测试、native/PE、JVM、DB、客户端、新族搜索或实际资源值。下一步对已具名 Material / Reliquary 的两个 ctor source-only 问题及实际保存引用的候选存在性做输入规划，规格尚待校对，不预称新窗或新值已取得。A/B、preview 与 Skill/Weapon 的准确阻塞保持，D-02 / F-05 / D02-R6 仍为 0/6。
