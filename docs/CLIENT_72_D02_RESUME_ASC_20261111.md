# D-02 A、Scene、C 执行计划（2026-11-11）

以 `c1b652f` 为输入基线。盘点实际 GAME_PATH、五份 stock、SDK carrier、便携配置、子模块和本机端口。138 个原数据文件与交接时的快照哈希一致。保留用户 AGENTS、resources flat globals 和 patch 的 B 线差异。B 依赖维持 [Daybreak 队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)所列状态，已验部分只读复用。

| 任务 / 模型 | 冻结输入和准确问题 | 责任文件与产物 | 验收、返回条件及 D-02 依赖 |
| --- | --- | --- | --- |
| F-03d3f4d2 / Sol max | [人工 CLEAR48](CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md)、[GC/Default 同 epoch](CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md)、[第二分配条件](CLIENT_72_D02_SECOND_GC64_20261111.md)、首 pop 和完整构造依赖表。核 stock 条件与人工输入的差异，区分自然线程、GC 所有权、写域和对象身份。 | 仅私有 `local/migration72/d02-resume-20261111-asc/a-stock-delta/`，输出对照、当前覆盖、最小缺输入和后继规格。 | 每项结论追到原证据或原消费者。发现必须同 epoch 的证据缺口即交回主线决定来源。本包只读离线，解除 R1 下一次验证的输入和范围不明确问题。 |
| F-03d3s2 / Sol max | [131 B 原窗](CLIENT_72_D02_SCENE_MPLEVEL_PARSER_WINDOW_20261111.md) SHA `c576218f…cbe`、[分组来源](CLIENT_72_D02_SCENE_MPLEVEL_GROUP_SOURCE_20261111.md)、[slot114](CLIENT_72_D02_SCENE_SLOT114_20261111.md)、[生命周期](CLIENT_72_D02_SCENE_ROW_LIFECYCLE_20261110B.md)。逐条核 reader 内联消费、refill 条件、状态及输出。 | 仅私有 `local/migration72/d02-resume-20261111-asc/scene-parser/`，交字段、顺序、可实现/未知边界和首个确切缺口。 | 以原字节和已绑定上下游反证。现有窗口无从确定职责时，给出具体分支、缺值或唯一消费者后交回。解除 R1 场景分组规格缺口，slot114 和组件生命周期保留独立验收。 |
| E-01 / E-01c / E-01d1 / Terra xhigh | [旧号快照](CLIENT_72_D02_OLD_ACCOUNT_SNAPSHOT_20261110B.md)、[全保存 Depot](CLIENT_72_D02_ALL_SAVED_DEPOT_REFS_20261111.md) 原字段报告和当前 `Avatar.java`。按实际 Java 条件列出 121 角色当前 Depot 与保存解锁 Talent 的交集，留下保存但不属于当前 Depot 的引用。 | `tools/client72_d02_old_talent_refs.py` 和对应测试独占；输出到 `local/migration72/d02-resume-20261111-asc/c-talent-refs/`。 | SHA 绑定，逐角色严格关联，独立交集核验、确定性及缺行/重复输入测试。确认 105357 的批准职责后再投影。只决定 R3 的 Talent/OpenConfig 必查键，目标行和 OpenConfig 另验。 |
| E-01 / E-01c / E-01d1 / Sol max | [Proud 固定单 key](CLIENT_72_D02_PROUD_SINGLE_KEY_REFERENCE_20261111.md)、[phase6 消费](CLIENT_72_D02_OLD_PHASE6_PROUD_20261111.md)、[实际字段](CLIENT_72_D02_PROUD_ACTUAL_FIELDS_20261111.md) 和全保存 Depot 原字段。 | Talent 关联交回后用空槽发布。仅写私有 `c-proud/`，固定题目为实际 typed 行到 pair、group/level 及门槛。 | 单 key 语法直接复用。区分 Java 现行算式与目标 lookup，按具体证据确定下一小步，支持 R3 必需旧号行及恢复。 |
| E-01 / E-01d1 / 主线 | 固定存档中主角 10000007 确认保存 phase 6，已验实际 group 12 和同源原报告中完整 12/6 行。 | 独立 `D02OldTravelerPromoteResourceLoadTest` 与私有 `c-traveler-promote/` 运行目录。只写测试和专题。 | 同一旧号保存阶段、实际组、原行跨度与 Java 复合键一致。真实 loader 保留四个非零属性的 float bits 和三个正材料，过滤零 ID 非零 count 槽。材料条目自身的资源映射及升级成本语义保留，入场属性加载不消费这些条目。只关闭 R3 一行的 Java 加载，其他角色和完整恢复另验。 |

本批接续既有研究。优先复用 [定向外部检索](CLIENT_72_D02_GITHUB_INCREMENT_20261110B.md)、[Scene 复用审计](CLIENT_72_D02_SCENE_GITHUB_INCREMENT_20261111.md) 和已有固定资源。后继若形成新的大任务，先按 AGENTS 由 Sol max 写中文检索计划，Terra max 按仓库和 README 筛选，再核分支与实现。

主线持有 Project、Harness、Git、共享矩阵、构建、数据库、部署和现场。子代理只读冻结依赖，只写各自责任文件，不派工。最多主线加三个子代理。成熟实现随槽位分派。实机准备完成后取本轮实时 ready。R4 仍须 R1/R2/R3 必要出口，随后 R5 和 R6。D-02 以 Project 第 5 节六项实际验收关闭。
