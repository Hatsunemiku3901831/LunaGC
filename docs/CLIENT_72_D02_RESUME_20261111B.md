# D-02 恢复执行计划（2026-11-11，本批 B）

本批从 `14e63dc` 接手。用户已解除暂停，执行到 D-02 六项实测全部通过，或所有剩余独立任务均有准确阻塞。只面向国服 7.2，使用 Windows portable。Project 修改前原件已存入私有 `local/migration72/d02-resume-20261111b/before-plan/`。

## 目标、已有成果与缺口

复用上一批 Scene 前缀、字典字段与计数、文本游标、原序子名称、插入及空分支；C 复用固定旧号、121 条 phase6 的真实 Java 加载、出生装备、303、Metal、Skill/Depot/talents 及 Proud 条件文件语法。研究片段不能代验真实加载、完整 Player 恢复或客户端入场。

A-SECOND-RETURN-CONTRACT 及其依赖、B SERVICE-TRIGGER-LOADER 组合 loader 保持安全审查拒绝阻塞。不运行未验草稿、不重试、不换模型接续；原件只读保留。移除这些动作及产物后，以下 Scene/C 任务仍能使用既有允许输入完成自身验收，其产物不实现被拒动作。

## 顺序、责任和产物

| 任务 / 模型 | 准确问题与依赖 | 可写范围与私有输出 | 验收出口 / 返回条件 |
| --- | --- | --- | --- |
| F-03d3s2/SCENE-ACTUAL-INPUTS，Sol max | 先定位本机同源 ConfigMetaConfig 的 PathHash.bin、payload、reader 起点，核实际 MPLevel 分组；再按同 factory 返回对象及 slot114 检查生命周期可推进范围 | 独有 `*scene_mplevel_actual*` / `*scene_world_return*` 工具与测试、Scene actual inputs 专题；私有 `scene/` | 哈希绑定实际输入、完整分组和原序名称；不能取得则列已查来源与准确最小输入。不得重复原窗静态提取或猜 slot |
| E-01d1/R3-PROUD-ACTUAL-ADOPTION，Sol max | 核旧号 `(221,1)` 实际 map 命中与目标行；同 typed 元素门槛、角色突破值比较及采用/跳过 | 独有 `*proud_actual_adoption*` 工具与测试、Proud actual adoption 专题；私有 `proud/` | 每项关系追到同源真实消费者，条件解析与自然命中分开；规格明确即交 Terra，缺输入准确返回 |
| E-01d1/R3-OLD-TALENT-ENTRY-NULLS，Terra xhigh | 726 查键中六个 null/空串差异在真实 Java 入场路径的行为；只复用候选来源，不据其批准目标配置 | 独立 Java 测试、`*old_talent_entry_null*` 工具与测试、Talent entry nulls 专题；私有 `talent/` | 源码实际调用链、六差异及正配置/缺键对照，主线运行真实 Java；目标 parser/语义缺口交 Sol |
| D02-R3 集成，主线 | 上述资源条件满足后，同一旧号真实 Player、背包、装备及 postLoad；只纳实际必经依赖 | 主线独占共享构建、JVM、Mongo 与独立运行目录 | 真实加载与保存恢复，不手设 loadedAll，不以空 proud/ability 放行；缺必要输入则准确记录 |
| D02-R4 → R5 → R6，主线 | R1/R2/R3 必要合同齐后，正式 Avatar、Scene/Team/Entity、回归构建、隔离部署和六项现场 | 主线独占 Proto/生成、Git、Project/Harness、部署、客户端和调试器 | 不放宽 ready/required。现场准备及退出恢复先完成，再请求本轮新实时 ready |

总并发上限 4，含主线；起步 Scene/Proud 为 `gpt-6.1-sol / max`，Talent 为 `gpt-6.1-terra / xhigh`，均显式调度成功。规格成熟后释放 max 槽，按独立文件分包 Terra；不为满槽制造工作。主会话没有可核验的模型切换接口，不冒称已切换；其职责按主线 Sol xhigh 执行。所有共享写入、集成和提交由主线串行完成。

## 输入、复用和运行边界

当前 HEAD、子模块 gitlink 与实际 HEAD 一致；用户 AGENTS、resources 既有差异、patch 的 Cargo.toml 与组合 loader 源保留且不提交。配置按当前 `.env`、缺省不存在的 `.env.local`、version 14 config 及 portable 脚本核对。源码 HTTP/SDK 8088、游戏 UDP 22101；默认 Mongo 27017，显式隔离实例 27027。初始无相关进程或上述监听。原始路径、身份、配置、哈希清单与运行证据留私有目录。

这是既有固定来源的续行，复用 [已有检索](CLIENT_72_D02_GITHUB_INCREMENT_20261110B.md)、[Scene 检索](CLIENT_72_D02_SCENE_GITHUB_INCREMENT_20261111.md) 和本机外置克隆。形成新的大任务时先 Sol max 中文检索计划，再 Terra max 按仓库/README、相关分支补查；不整包引入 AstaPS-Resource 或 hk4e-patch-universal。

## 验收、提交和停止

每个独立验收项完成后，主线核差异归属、来源、真实测试及边界，以 Hatsunemiku3901831 的指定姓名/邮箱同时作为 Author 和 Committer 本地提交，不推送。仅测试、离线工具和文档变更无需重启；运行材料变更须按 AGENTS 构建与前置满足后的部署核验，前置不足记录原因。

普通技术难题继续分析；缺输入说明来源与解除条件；待现场先完成准备；新安全拒绝保留原提示/停止点/失败原件并停止该动作及依赖，重新检查独立任务。Daybreak 只整理用户待判断的最小单目标包，输入不足标缺输入，不自动派工。局部通过不晋级父项或 ready。

D-02 仍由 Project 六项实测关闭。只有全部独立工作确有外部输入、待现场、受支持执行条件或记录拒绝阻塞时交接：先备份 HANDOFF2，再覆盖详细状态、阻塞、最小 Daybreak 队列和下一步；HANDOFF 只追加。
