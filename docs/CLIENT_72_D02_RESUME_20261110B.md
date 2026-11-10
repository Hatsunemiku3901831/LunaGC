# D-02 恢复执行批次 B（2026-11-10）

## 目标与固定起点

用户本轮明确解除“收尾后暂停”，授权研究、实现、回归、受控取证和前置满足后的 Windows portable 隔离部署，持续到 D-02 六项全部实测通过。阶段状态与验收以 [Project](../Project.md) 为准，不沿用旧 ready、PID、VA 或无进程结论。

起始 HEAD `a38aef7592b24c7af7d22e7900c33d0aba0ab7b8`，分支 `7.2-dev`。修改 Project 前已逐字节备份七份入口文档至 `local/migration72/d02-resume-20261110b/before/`；Project 备份 SHA-256 `1fe499b71b84692a5c0a5121b70503b54dded4c6f37070cc2101ac92689b5983`。

新只读盘点 `initial-inventory.private.json` SHA-256 `6c6c8f9eef85e42632fefa4dc251a44b6c701c8056240ce30243e53304c307f8`：当前 GAME_PATH 重新解析，五 stock 完整 SHA、SDK carrier 和13项保护文件匹配；portable/config14、两个缺席条件保持；相关进程和固定监听均为0。patch HEAD `85dbaf9ad155eee69516fac96597a2c25afbb354` clean，resources HEAD `dac2a9d43332ef51879522cf39da2831b0ca0543` 与 gitlink 一致；既有 flat globals 差异保留。该结论只描述盘点时点及列明文件集，不是后续现场身份或全数据库字节证明。

## 直接复用与准确缺口

八点同配置 writer/parser/env 写入与原生正常返回、锁内 comboLogin map、stock v2 的 TLS/record/非零完整 head/HashSet 与 managed env/base、出生默认装备局部保存/冷恢复均已验，不重新发布。自然 loadAll 首诊、303恢复、AstaPS候选/loader、Metal局部生命周期、Promote/Curve、Skill/Depot/talents直接复用。

关键路径仍是 `R1/R2/R3必要合同与实现 → 正式Avatar/Scene/Team/Entity及必要加载保存 → R4 → R5 → R6`。R1完整构造、Scene生命周期、R2准确请求采用/认证、R3完整必要资源与所选旧号恢复尚未闭；局部成功不关闭父项。

## 派工、写入归属与验收

实际总上限4槽，主线加子代理最多4个，不创建额外聊天。所有子代理禁止扩招、Git/共享构建/现场操作；主线统一 Git、Project/Harness、Proto/生成器/矩阵、Gradle/Cargo/JVM、MongoDB、固定端口、部署、客户端/调试器/DLL恢复。冻结来源变更先通知消费者，不改写旧 pin 或失败原件。

| 包 / 模型 | 固定输入与准确问题 | 可写文件 / 私有原件 | 本包验收与返回条件 |
| --- | --- | --- | --- |
| A-GC-OLDFLAGS8，归F-03d3f4d2；`gpt-6.1-sol/max` | stock v2同epoch候选、准确allocator/head/第二构造及既有String合同；先核oldFlags=8必经141fdf9c0语义、输入、外部边及允许执行范围，列完整构造所有分配/子构造的已覆盖/未闭路径 | `docs/CLIENT_72_D02_GC_HELPER_20261110.md`、`tools/client72_d02_gc_helper_20261110.py`及同名test；`a-gc/` | 最小有界原窗/来源与GC合同或准确首缺、完整依赖图、下一可实现规格；不补TLS/对象/槽/未知页/host返回。明确后立即转Sol xhigh/Terra |
| B-REQUEST-ADOPTION，归R2-ROUTE-CONFIG-SELECT；`gpt-6.1-sol/max` | 八点、锁内map、stock v2、40108 comboLogin加单个问号至PostRequest；核LoadLocalConfig/SetEnv/GetURL选择、消费者触发、最终URL分类/调用身份/返回边界 | `docs/CLIENT_72_D02_ROUTE_ADOPTION_20261110.md`、独立`tools/research/d02_route_adoption_20261110_*.py`；`b-route/` | 可实施的最小准确观察/接入规格或准确缺输入；不能把map/env0当调用、跨epoch拼成功、重复页面实验或扩预算。明确后交Sol xhigh |
| R3-OLD-SAVE-DEPENDENCIES，归E-01d1；`gpt-6.1-terra/xhigh` | 当前持久模型与已有离线BSON/EXTENDED JSON；先确定是否已有明确选定旧号的固定快照，用已验装备BSON验证依赖提取driver | `tools/client72_d02_old_save_dependencies.py`及test、独立专题；`c-old-save/` | SHA固定的只读提取、真实字段名/正引用/缺字段/UID隔离与脱敏输出；没有所选旧号则准确记录待主线选定/取得快照，不猜账号/alias，不重复Java局部恢复 |
| F-03d3s2后继；首个max槽释放后`gpt-6.1-sol/max` | World row仅转发至148d6ebf0、AutoAllocRecycle不是entity ctor；沿准确下游核MPLevel、Pool/Scene和组件创建/替换/拆除/通知 | 新Scene独立专题/工具/输出，发布时再冻结文件清单 | 先证明窗口/研究必要性并有界规划；已验转发行不重做，不由包装器或回收接口猜字段。明确发送实现交Sol xhigh |

独立私有根均为 `local/migration72/d02-resume-20261110b/`。主线保留共享输入的唯一写入权。成熟资源族、独立driver或测试准备按不重叠文件拆成多个Terra；拆分必须解除当前依赖。未映射字段不自动全列P0，proud门槛、defense/common/LKLP及目标parser/自然hit未知交Sol max。

## 定向外部复用与升级

新重大缺口先由Sol max写中文Markdown GitHub补查计划，Terra max据计划执行，重点核分支；复用已有固定来源和仓库外克隆，只补新缺口。任何候选先核版本、来源、适用边界与实际差异，值得克隆的放仓库外。AstaPS-Resource只取必要资源，hk4e-patch-universal只参考有证据价值的差异，不整包替换或强制验签成功。覆盖不全如实记录，不能把失败/超时当无结果。

Astra仅在主线与Sol max实质分析仍未解、阻塞D-02时使用，先列排除项、准确问题、理由与返回条件；缺样本、现场或未读既有成果不升级。连续尝试没有新信息时整理缺输入并换方法，不重复同计划。

## 实机插入、部署、停止与恢复

本轮尚无待用户动作。只有离线实现、独立构建/owned验证、来源/预算门禁、备份/恢复方案均通过后，主线一次说明问题、用户动作、预计时长、正常表现、反馈、退出恢复，取得本轮新实时ready才启动。身份/阶段/预算/恢复兼容才合并，其他现场串行，不使用computer-use，不要求官方账号登录。等待期间继续独立任务；失败保留原件先归因。

运行代码/Proto/配置/补丁/资源变更按对应构建和前置满足后的portable部署核日志、健康、TCP/UDP/PID/运行JAR SHA及实际补丁。只改Proto源，Gradle/protoc生成Java。不以“还没实机成功”制造禁止部署循环；当前缺的必要合同须逐项解除，不改ready/required、版本或空对象过门。

每个独立出口由主线验收后更新Project/Harness并及时本地提交，不推送、不夹带用户/resources差异。仅D-02六项全过、用户明确暂停或所有可独立工作确有准确外部阻塞时停止。交接前备份HANDOFF2再覆盖，HANDOFF只追加；保留原数据库/运行数据和全部失败原件。
