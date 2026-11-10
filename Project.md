# 国服 7.2 任务规划与验收

任务证据基线：2026-11-11；本批起始 HEAD `c1b652f`，见 [A、Scene、C 执行计划](docs/CLIENT_72_D02_RESUME_ASC_20261111.md)。仅面向国服 7.2，Windows portable。

## 当前阶段

**A、Scene、C 恢复执行。D-02 / F-05 / D02-R6 仍未完成。** A 复用 CLEAR48-FULL 人工 SSE/AVX，Scene 消费已取得的 MPLevel 131 B 原窗，C 复用已固定且核验归属的旧号及 Proud 单 key 文件语法。B 组合 loader 留待 Daybreak。当前无待启动的现场实验，旧 ready 已消费。

本文件维护目标、任务设计、依赖、发布顺序、任务状态、完成标记和验收结论。方法/复现索引放 [Harness](Harness.md)，详细证据与失败分析放对应专题，原始日志/进程/路径/凭据留忽略目录；不再向这里追加逐日进展、测试流水、JAR/PID 或暂停历史。旧版全文见[只读历史快照](Project.archive-2026-11-08.md)，旧快照不作为当前执行指令。

## 1. 目标、范围与完成规则

- D-02：独立测试环境中新旧号登录、新号显式选人/命名、完整初始化/场景握手、角色实际进入世界、连续心跳、两类账号退出重登及归属/保存正确。
- D-02只迁移入场必要依赖。完整战斗、商城、深渊、全地图及7.2新增内容归E-02/E-03/E-04；被入场真实调用的同类资源或通知仍属P0，不因功能名而删掉。
- 状态仅用：待开始 / 进行中 / 阻塞 / 已完成。子项完成只覆盖列明范围，不能代替父项或实机通过。已完成项保留ID与证据入口，后续只增加明确的新缺口，不重做整项。
- **任务状态和完成标记持续在本文件维护。** 新任务缺省为“待开始”，没有验收结论不能标为“已完成”；完成后及时更新对应状态、已验范围和证据链接，不能只在Harness、专题或历史快照登记。已标出的缺口、缺省/省略语义及未知/未验标记保留，只有新证据支持时才更新。
- CmdID→类型、类型→语义、字段→wire分别验收；未知保持未知。固定输入、数据保护、生成Java及本地提交等执行规范见 [AGENTS](AGENTS.md)。不改ready/required、不绕原生校验、不用旧号或空对象代替缺证据。
- 不按研究卡、字段或测试数量计算完成率。统一矩阵与原生片段用于证明部署前合同，完整运行顺序和客户端效果必须由最终实测反核。

<a id="d02-route"></a>

## 2. D-02阶段与依赖

| 阶段 | 状态 | 已有基础与剩余目标 | 阶段验收出口 |
| --- | --- | --- | --- |
| D02-R0 入场依赖台账 | 已完成 | 已登记源码候选、管理器/事件/脚本开放边界；运行闭包继续由R4/R6更新 | 新旧号路径及必需依赖有来源，未知未被标成可省略；[台账](docs/CLIENT_72_D02_ENTRY_DEPENDENCIES.md) |
| D02-R1 角色与场景初始化 | 进行中 | raw适配器、类/方法后继、warm输入及有限Java投影可复用；enum/String、完整Avatar构造、g2和Scene/Team/Entity未闭 | 同源角色/库存/资源→真实Java发送→目标消费者及必要状态/顺序闭合，未知不参与发送 |
| D02-R2 SDK与连接前置 | 进行中 | 日志/响应修复、真实HTTP往返、key桥接/装载及准确caller到达已验限定范围；SDK分类、自然Init/env/route/认证与完整校验未闭 | 正确认证/错误拒绝、会话交换、dispatch/密钥和必需补丁合同成立；实际装载/本服连接在R5/R6复核 |
| D02-R3 最小资源与保存恢复 | 进行中 | 真实资源/曲线/突破、出生HP/critical/装备/Gadget查找键、自然首诊、303局部正向Manager恢复及出生默认装备局部保存/冷绑定已验；完整行、配置/ability、revision及入场全部必要加载/恢复未闭 | 必需资源/引用/脚本实际加载有效；保存→新Player→真实manager恢复成立，不能手设loadedAll |
| D02-R4 P0部署前汇合 | 待开始 | 依赖R1/R2/R3的必要合同；C-01b网络边界与既有回归直接复用 | 必经请求/响应/通知、嵌套、聚合、顺序、资源与最终发包一致；生成、相关回归和构建通过，未验runtime继续保留 |
| D02-R5 隔离部署 | 待开始 | 依赖R4；InstanceRoot入口已验，真实实例未部署 | 独立配置/data/DB/资源，日志/健康/TCP/UDP/PID/运行JAR SHA及实际DLL/信任一致；[部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md) |
| D02-R6 客户端验收（D-02 / F-05） | 待开始 | 依赖R5；原SDK登录页不等于本服登录 | 第5节每项通过并记录范围；失败需修复、回归和正常重部署后复验 |

依赖关系：`R0 → {R1, R2, R3} → R4 → R5 → R6`。三条前置线共享数据合同，但不能用环形等待搁置独立实现：已完成SDK修复直接复用；输入明确的loadAll首诊不等待完整Avatar，资源字段仍与实际消费者相互核实。部署前证明合同，部署后验证真实连接，不能要求先实机成功才允许部署。

## 3. 并行执行路线与派工

本节维护本批任务 ID、范围、模型、依赖和文件归属，已完成前置在第 6 节。

<a id="d02-parallel"></a>

### 3.1 模型与责任

| 角色 | 指定模型 / 推理档位 | 责任与交付 |
| --- | --- | --- |
| 主线 / 日常开发 | GPT-6.1 Sol xhigh（`gpt-6.1-sol` / `xhigh`） | 规划、明确实现、集成、定向回归、现场、验收与提交；先确认输入和文件归属，再发布任务 |
| 难点分析子代理 | GPT-6.1 Sol max（`gpt-6.1-sol` / `max`） | 证据冲突、协议/ABI、调用链、零槽/空usage/分类失败及已复现但原因未明的卡点；交明确结论、独立证据、最小实现规格或准确缺输入 |
| 有界执行子代理 | GPT-6.1 Terra（`gpt-6.1-terra`，档位按任务指定） | 规格完整的清单/数据对照、受控转换、driver、测试准备、机械修改和文档整理；未知语义返回主线，不自行猜测 |
| 最后升级，非日常排班 | GPT-6 Astra max（`gpt-6-astra` / `max`） | 仅主线和Sol max实质分析仍不能解决、且影响D-02关键路径时使用；须先说明已排除假设、升级理由、准确问题和返回条件。缺样本、缺现场或未读既有证据不构成升级理由 |

起步采用 **主线＋A、Scene 两个 Sol max 子代理＋C 一个 Terra xhigh 子代理**。首个槽交回后已派 Sol max 推进 Proud 语义。明确规格随槽位交 Sol xhigh 或 Terra xhigh。

显式传入工具支持的模型与档位；模型不可用应如实返回主线调整，不能静默升级Astra或冒称已使用指定模型。子代理不得自行扩大派遣和修改范围。

并发上限为 4，含主线。按成熟规格轮转，B 留待用户后续使用 Daybreak，当前批次不派发 B。

### 3.2 批次路线与退出条件

| 批次 | 既有任务 / 本批动作 | 并行安排 | 退出条件与推荐模型 |
| --- | --- | --- | --- |
| 0 恢复盘点 | 复用R0及全部已验前置，重核HEAD/差异、输入、配置、现场；固定本批各分支来源与文件清单 | 主线准备后发布 A、Scene、C，不重做历史全量研究 | 主线Sol xhigh；各任务输入、责任文件、可复现证据入口与验收出口明确 |
| 1 复用已验成果推进后继 | A 对照 stock 条件与人工 CLEAR48；Scene 分析 131 B 原窗；C 推进旧号必要资源及 Proud | A、Scene Sol max，C Terra xhigh；语义交回后续派，主线串行构建/JVM | 分别交已验结论、明确实现规格或准确缺输入。出生装备、303、Metal、Promote/Curve、Skill/Depot/talents 和旧号快照直接复用 |
| 2 定向实机，按需插入 | 只补批次1证明必须取得的上下文和必需输入；已完成采样器复用 | 主线独占客户端/安装；同身份、同阶段、预算兼容才合并采集，否则分次串行 | 自有验证、源门禁及恢复方案先通过，再新实时ready；取得准确输入或保留有效失败。Sol max设计难点，Sol xhigh执行 |
| 3 A、Scene、C 闭合 | R1 完整 Avatar 和 Scene/Team/Entity，R3 必要资源和实际加载；R2 仅复用已验成果 | 各线责任文件独立，主线统一集成 | 必要消费者、顺序和数据分别成立。R2 剩余依赖继续约束 R4 |
| 4 接入与正向恢复 | F-03d3g2、F-03d3s2正式发送；E-01/E-01c/E-01d1真实必要加载及保存→新Player→Manager恢复 | 独立sender源码可并行；共享Proto/生成器/矩阵、构建与DB串行集成 | Sol xhigh主责；真实Java输出与目标消费者一致、独立库正向恢复及拒绝回归成立。父项前置不满足则只完成相应子出口 |
| 5 R4汇合 | A-03/B-02/B-03、C-01/F-02/F-03、V-01及F-04必要材料；最终收发/嵌套/枚举/顺序/资源汇合、定向回归与构建 | 审核可分工；主线统一生成、构建、准入和提交 | Sol xhigh；R1/R2/R3必要合同与实现齐备，现有C-01b等成果直接复用，运行时未验边界保留 |
| 6 R5→R6 | 隔离portable部署→新旧号六项实机验收 | 主线顺序执行；失败回到责任分支修复、回归、重部署复验 | Sol xhigh；第5节全部实测通过才完成D-02/F-05/D02-R6。有复现的疑难问题交Sol max，Astra仍按3.1升级条件 |

批次是依赖顺序，**不是全员同步屏障**：A、Scene、C 任一分支已有后继条件即可前移；某个分支需用户配合时继续其他独立工作。首诊无需等待完整Avatar或全部资源研究；首诊结果不等于resourceReady。正向Manager恢复必须等实际必要资源条件成立。批次2可在需要时穿插，不强制每批重启客户端。

### 3.3 当前批次派工与文件归属

| 分支 / 既有任务 | 本批具体动作及前置 | 产物与验收出口 | 责任文件与模型 |
| --- | --- | --- | --- |
| A：F-03d3f4d2；必要 String 共同合同 | 人工 SSE/AVX、第二 L64/flags8 及同 epoch GC/Default 已验输入。对照 stock 条件与人工路径 | 定最小输入、读写域和执行边界；第二分配、Default、自然线程、GC 所有权和对象身份分别验收 | Sol max 离线分析，产物限私有 a-stock-delta |
| B：R2-ROUTE-CONFIG-SELECT、R2-CONFIGSYNC-EARLY-BRIDGE | 已验八点、锁内 map、stock v2、PostRequest 和共用服务入口可只读复用 | 组合 loader 源、失败证据和未提交差异保留；自然请求采用、信任、认证与会话交换未闭 | 留待用户使用 [Daybreak](docs/CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md) |
| C：E-01/E-01c/E-01d1；R3-OLD-SAVE-DEPENDENCIES | 固定旧号 121 角色/12254 物品，全 Depot getter，Proud 单 key 文件语法已验 | 先核当前 Depot 的实际 Talent 引用，再核 Proud typed 行、group/level 和门槛。完整旧号恢复依赖真实必要行 | Terra xhigh 独占 Talent 引用 driver 与测试；Sol max 接 Proud，JVM/DB 归主线 |
| Scene：F-03d3s2 | 消费已得 131 B MPLevel parser 原窗及同源上下游，保留模板关联和包装器/回收入口的已有结论 | 先确定内联消费、refill、reader 状态和输出，再定首个缺口；slot114、Pool/Scene 与组件生命周期仍分项验收 | Sol max 离线分析，产物限私有 scene-parser |

本批输入、文件归属、产物及退出条件见 [执行计划](docs/CLIENT_72_D02_RESUME_ASC_20261111.md)。
每次派工先固定：**任务ID、输入版本/来源、准确问题、允许修改的文件、只读依赖、独立输出目录、禁止操作、产物、验收、停止/返回条件**。所有者以本批派工为准，一个文件同一时刻只有一个写入者。子代理返回“已完成/有界结论/阻塞”及证据；主线审核实际差异、必要独立样本和回归，再更新本页并提交。阻塞时写准确解除条件，不改父项为完成。

### 3.4 未完成工作包与模型分配

此表保留未完成任务的状态与验收账本。恢复后按依赖和3.3实际缺口发布任务，已有完成子项直接复用。

| 批次 / 工作包 | 状态与依赖 | 交付与验收 | 推荐模型 | 证据与边界 |
| --- | --- | --- | --- | --- |
| 1A→按需2→3 / F-03d3f4d2 完整Avatar构造/后缀 | 进行中；原getter/owned字符及首pop副本已验；第二record/L64/flags8与GC/Default同epoch输入已取得，Default对象身份仍opaque。完整904B人工SSE/AVX读写和RET已验，stock条件路径待核 | 先核stock与人工路径差异，再推进第二分配/Default消费者；完整20分配/12根及子构造逐项闭合。自然String tuple/caller/trigger、TLS/GC所有权、enum/cold普通10294及g2保持未知，不补值/host成功 | Sol max冻结复杂合同，Sol xhigh/Terra接明确实现 | [自然输入边界](docs/CLIENT_72_D02_A_SUCCESSOR_20261110.md)、[原6B实际能力](docs/CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_RESULTS_20261111.md)；完整父项不晋级 |
| 1B→按需2→3 / R2-CONFIGSYNC-EARLY-BRIDGE | 进行中；自然13及八点实际同配置writer/parser/env写入/原生返回与正常退出恢复已验，完整业务及自然Init/route未闭 | 首次任意P0/P2与三预算保持，实际外采30读10415B/512；原状态前后相同，8点代码/真实trampoline绑定。后继自然Init/env→route按证据最小推进；unwind/完整payload及业务未闭 | Sol max处理新语义；Sol xhigh现场/恢复 | [八点实际验证](docs/CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md)、[实施审核](docs/CLIENT_72_D02_B_SDK_AUDIT_20261110.md) |
| 1B→按需2→3 / R2-ROUTE-CONFIG-SELECT | 进行中；准确托管loader存在，当前env/base=0的同epochstock候选已验；自然选择/Init/采用未验 | 固定托管env选择、managed→native必要输入和安全事件；原始材料可得先离线，合同足够再实机 | Sol max语义；Sol xhigh接入 | [执行计划](docs/CLIENT_72_D02_RESUME_20261109.md) |
| 1B→按需2→3 / R2-CONFIG-ROUTE-WRITE（归R2-ROUTE-CONFIG-SELECT） | 已完成；36源147pin/独立产物准入与新ready90秒/16项原件审核和恢复通过，仅关闭锁内map观察 | 首同owner M0占尝试后验同writer帧/caller，锁内只查comboLogin并输出固定类别；新M0账69/4440、绑定15/6408、外采25/9262原件已验；非空comboLogin为固定cn-route-env-0。托管/native reader采用与认证另验 | Sol xhigh实现与主线现场；新语义再Sol max | [单一五点合同、原码及边界](docs/CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md) |
| 1B/3，必要性确认后 / R2-PLUGIN-UI-REGISTRATION | 进行中；137B入口静态子项已验，producer未闭 | 固定RegisterTask入参、field25231字典set_Item上下文、真实producer和所选task；只在自然路径必要时继续，不以静态入口通过代验entry写入、实际转发或视图行为 | Sol max | [来源、已验子项及准确解除输入](docs/CLIENT_72_SDK_PLUGIN_UI_REGISTRATION.md) |
| 2/3，复用early结论 / R2-CONFIGSYNC-OBSERVE | 进行中；实机装载已验，目标事件未捕获 | 复用装载/恢复和early结论核触发/安装时序；确需不同观察时先列新问题，取得目标事件及producer/consumer身份后验收。保持原转发/校验/恢复，不重复同实验 | Sol xhigh执行；Sol max处理未闭语义 | [实际结果与未知](docs/CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md#新ready实际90秒未命中及恢复) |
| 1C首诊→3加载→4恢复 / E-01 / E-01c / E-01d1 自然加载与正向保存 | 进行中；三文件自然首诊及303局部真实保存→新Player→Manager重绑已完成，完整必要资源/日志/场景事件与入场恢复仍未闭 | 局部63项及未就绪BSON不变直接复用；继续实际必需map/引用和完整加载，不由自然loadedAll=true推断resourceReady或全部恢复通过 | Sol xhigh定边界/集成；Terra做明确driver/清单 | [局部正向恢复](docs/CLIENT_72_D02_QUEST_303_NATURAL_REBIND.md)、[实际首诊](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_RUN.md)、[最小资源](docs/CLIENT_72_D02_MINIMUM_RESOURCES.md) |
| 1C/3→4 / E-01 真实资源→Java必要字段 | 进行中；能量/装备/曲线/Promote/七字段绑定及talents职责、投影和五项真实Java已验；两出生Java构造返回仍不批准proud规范化/ability、完整资源与加载/revision | 同源表/行→目标parser/消费者→Java字段逐项对应；先角色、depot、技能、默认装备、Scene及必需Quest，拒绝无证alias/省略；已实测Java接受缺引用，不能拿返回代验完整性 | Sol max处理新语义；Sol xhigh实现；Terra做已批投影 | [字段分层](docs/CLIENT_72_D02_AVATAR_ITEM_GAPS_20261110.md)、[七字段真实绑定](docs/CLIENT_72_D02_C_BRIDGE_JAVA_20261110.md)、[突破真实加载](docs/CLIENT_72_D02_C_RESOURCE_LOAD_20261110.md) |
| 1A共同输入→3→4 / E-01/R3-ITEM-ENTRY | 进行中；gadgetId/key、完整候选/Java加载、有限parser语义、D-02必要性及Metal局部真实生命周期已验；String同epoch图/getter/owned字符路径已验，目标完整内容未闭 | 自然Hash/FindEntry/配置命中及目标字段仍未知；不猜defense/common/LKLP alias、不许可空ability。当前A审必要String消费者；完整能力、加载及保存另验，factory须先证明D-02必要性，sender依原合同 | Sol max维护String/配置语义；Sol xhigh现场/加载；Terra明确实现 | [真实候选/Java加载](docs/CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md)、[必要性与生命周期验收](docs/CLIENT_72_D02_GADGET_NECESSITY.md)、[目标未知](docs/CLIENT_72_GADGET_ABILITY_SEMANTICS.md) |
| 3→4 / R2-ROUTE SDK配置、会话与自然连接（D-01 / F-02 / F-04） | 进行中；包/loader、map/app_key、原生builder/sign与Java真实HTTP/保存往返已验；自然Init/env及运行route未闭 | 固定来源追配置→选址→签名→会话交换；自然实验一次回答明确问题；必要输入可离线取得时先离线；与R2-SESSION/KEY汇合 | Sol max闭合新语义；Sol xhigh接入 | [配置loader](docs/CLIENT_72_SDK_ROUTE_LOADER.md)、[真实接收](docs/CLIENT_72_SDK_COMBO_HTTP.md)、[请求链](docs/CLIENT_72_SDK_LOGIN_CHAIN.md)；原登录页/root非空不证明请求成功 |
| 3→4 / R2-KEY 窄key核心接原生Crypto（D-01 / F-04） | 进行中；同核native桥接、实际零hook/窄key装载及精确代码绑定已验；自然caller/route和完整校验未验 | 同一核心输出供真实SDK Crypto，覆盖身份、caller、寄存器/栈/返回保持及错误拒绝；自然key/认证与完整校验仍须实际验证 | Sol xhigh复用实现；Sol max处理自然caller/校验难点 | 不启用旧整DLL；[ABI](docs/CLIENT_72_SDK_KEY_INPUT_ABI.md)、[原生桥接](docs/CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md)、[实际key观察](docs/CLIENT_72_KEY_LOADER_OBSERVATION.md) |
| 2/3→4 / R2-KEY-LOAD 独立入口启用key核心 | 进行中；产物/owned与实际安装/代码绑定/正常页恢复已验；自然caller与完整校验未验 | 独立产物仅接准确3950c0和同一callback；实际代码绑定不代验自然key被选中、认证或完整信任路径 | 主线Sol xhigh实机；Sol max处理已复现难点 | [产物与验收](docs/CLIENT_72_SDK_KEY_LOADER.md)、[实际观察](docs/CLIENT_72_KEY_LOADER_OBSERVATION.md) |
| 3→4 / F-03d3s2 场景/Team/Entity闭合与发送 | 进行中；Pool实际函数/模式/目标头及独立TLS分支门静态合同已验；合并只读工具及World row365B转发原窗/主线第三已验；146e055f0为AutoAllocRecycle调度，非实体ctor。自然分配/注册/stage/MPLevel与组件生命周期未闭，真实row后继148d6ebf0已验模板关联，actual slot114仍未闭 | 用准确producer和同源实体完成能力/附属对象/创建替换拆除与通知顺序；逐个批准必需三层和Java来源后接sender | Sol max语义/生命周期；Sol xhigh发送实现 | [Scene上下文](docs/CLIENT_72_SCENE_RUNTIME_CONTEXT.md)、[场景token/属性](docs/CLIENT_72_SCENE_ENTRY_CLOSURE.md)；World stage与资源枚举分开 |
| 4，f4d2/R3出口充分后 / F-03d3g2 正式Avatar发送 | 阻塞；依赖f4d2、R3及同源Store/角色/SavedVitals | 固定字段/tag/类型/枚举/值来源和省略语义，确定性生成；真实Java输出交目标消费者；非零/64位/非空/错误及账号保存回归 | Sol xhigh；Terra做明确生成/向量准备 | g1和g2-prep已完成；只改Proto源，不改旧共享定义求方便；[投影](docs/CLIENT_72_AVATAR_JAVA_PROJECTION.md) |
| 随合同更新→5 / A-03 / B-02 / B-03 证据与生成 | 进行中；复用已完成B-01，随新增合同维护 | 精确审核新增/变化来源、保留漂移拒绝、重复生成一致；已验证片段才接入，旧号/未知不兜底。索引更新不是新的语义发现；[矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md) | Sol xhigh审核/集成；Terra做明确索引/漂移检查 | [既有规范](AGENTS.md) |
| 4→5 / C-01 / F-02 / F-03 网络与初始化整合 | 进行中；全量出口归R4 | 复用C-01b帧/注册修复，补必要Union/枚举/最终发包与序列。检查onLogin、SceneInitFinish、EnterSceneDone后的队伍/世界/实体，不能只验八握手 | Sol xhigh；复杂协议冲突交Sol max | [既有规范](AGENTS.md) |
| 随变更→5 / V-01 回归失败归因 | 进行中；按本次改动和R4范围选集 | 新运行先明确基线/夹具/生产失败；保护账号、经济、保存、Lua。完整全量未重跑就不宣称全绿；不机械用历史失败总数安排任务 | Sol xhigh归因修复；Terra做明确夹具/结果整理 | [既有规范](AGENTS.md) |
| 准备可提前→5→6 / F-04 部署材料及隔离核验 | 进行中；复用ENV-01基线，实际部署待R4 | 真实SDK/信任/资源合同充分后形成7.2产物；正常构建和停服，按InstanceRoot归属核对，不仅改版本或环境变量放行；[隔离](docs/CLIENT_72_PORTABLE_ISOLATION.md) | 主线Sol xhigh；Terra做清单/离线核对 | [既有规范](AGENTS.md) |

R2-CONFIGSYNC-OBSERVE与EARLY-BRIDGE共享自然配置目标：先消费已有early结果，只有不同问题/时机需要时才安排旧观察路线。E-01首诊是现有自然加载父项的诊断步骤，父项“阻塞”表示完整加载/正向恢复前置未齐，不禁止输入明确的首诊。新发现须先归到现有任务并证明D-02必要性，不因一个失败再复制一套父任务。

### 3.5 并行与互斥规则

| 对象 | 可同步执行 | 必须由主线串行或先冻结的操作 |
| --- | --- | --- |
| 独立源码/工具/证据 | A/B/C各有文件清单与输出目录；只读冻结来源可以共享 | 不同时写同一文件、输出或已固定报告；公共helper/协议矩阵/源pin变更先由主线协调，通知受影响分支后再必要复验 |
| 当前客户端五输入 | 只读历史快照、无需检查现场身份的分析可在实机时继续 | 依赖当前stock磁盘身份的回放与临时DLL安装互斥；恢复后重核身份，不能把orig别名替代当前输入或放宽hash |
| 客户端/调试器/安装 | 合同兼容的R1/R2/String采样可由主线同次执行 | 单一现场负责人；新实时ready，固定scope/预算与备份恢复；原版和临时DLL的不同输入epoch不混用，不启动冲突目标 |
| 构建/生成/资源加载 | 独立文件的实现与测试准备可并行 | 主线统一Git、Gradle/Cargo、protoc及共享生成输出；实际JVM加载/集成测试不占用其他分支的静态map或输出；子代理不自行运行共享构建 |
| DB/端口/部署 | 隔离配置和验收脚本可提前准备 | 主线独占Mongo/固定端口/服务/运行目录；按源码核端口，保护原数据，正常停旧实例后部署；不能只改环境变量宣称隔离 |
| Project/Harness/提交 | 子代理提交各自专题/证据及建议状态 | 主线统一更新共享索引、验收结论、任务标记和本地Git提交；不自动推送，不提交他人修改 |

## 4. 任务发布与范围控制

1. **按3.2依赖及3.4准确缺口持续调度。** 本轮A/B/C的已验子项列在第6节，包括自然首诊及303局部恢复，不重新发布旧研究或重复首诊。本轮恢复授权覆盖上轮暂停，按准确缺口持续发布、验收并汇合未闭出口；R2-LOG/SESSION及既有来源/采样成果直接复用。
2. **规格成熟就实现。** Sol max给出明确来源/字段/时序和验收后，转Sol xhigh/Terra做最小变更；重大问题或长任务先规划。主线审核后执行必要测试/独立复现、本地提交、更新状态，再启动后继；无新变化或未决风险不重跑历史全量。
3. **实机只补准确缺输入。** 不直接放大SDK预算、重复同参数观察或用零槽补值；需要用户操作时先备妥实验再一次说明操作/预期/反馈并等待新ready，同时继续独立工作。采集通过不代替消费者或业务通过。
4. **必要性先于扩展。** 缺表/日志须结合真实入场调用筛选；不把全部Lua/资源表/玩法列P0，也不将未知默认判作可省略。完整Avatar/Scene、自然SDK、真实资源与保存各有独立出口，不以一项片段通过互相代验。
5. **外部复用按缺口启动。** E-01/R3-UPSTREAM-D02和R2-PATCH-DELTA的审计已完成；AstaPS候选用于对照、已验同一行直接复用。只有新提交或当前缺口有明确可用差异时审增量。WinHTTP先证自然路径覆盖及本地缺陷；不整包替换、不启强制验签、无证alias或未知输入放行。
6. **保留完成标记与未知。** 已完成子项仍在本文维护；已验范围之外的缺口继续留父项。日志/命令/逐次失败留Harness/专题，本文仅维护任务设计、责任、依赖、状态和验收结论。恢复后只有D-02全部通过、用户明确暂停或所有独立工作均有准确外部阻塞时交接，按AGENTS先备份HANDOFF2再写详细交接，HANDOFF只追加。

<a id="d02-offline-sol-terra"></a>

原“Sol/Terra离线前置路线”已收口，成果在第6节；旧锚点保留。后续从第3节按依赖派工，当前未闭语义优先Sol max，日常实现Sol xhigh，有界执行Terra；Astra不列常规批次。旧路线、暂停或继续历史不覆盖本页当前阶段及用户最新指令。

## 5. 验收标准

每个任务完成前，须具备**明确范围、可追溯来源、验收结论和可复现证据入口**。协议/运行变更另须独立正反样本、对应实现/生成及受影响回归；不适用项说明理由。静态、离线原生、模型和实机范围分列；必要时双跑核一致。运行变更按AGENTS完成构建/部署核验；缺前置则任务只在局部范围完成，部署及父项保持未验。

实机验收使用受控脚本/API与用户确认，只操作本项目目标，不使用computer-use；原始数据留忽略目录。

| D-02验收项 | 当前状态 | 通过条件 |
| --- | --- | --- |
| 旧测试号本服登录 | 待开始 | 真实SDK/认证→本服会话，账号与角色归属正确；原SDK页不能代入 |
| 新号显式选人/命名 | 待开始 | 新账号进入正常选人流程，合法创建确认保存；错误/重复请求不重复创建或发放 |
| 初始化与场景握手 | 待开始 | 必需角色/库存/队伍/世界/实体状态被客户端正确消费，整个加载序列完成 |
| 实际进入世界 | 待开始 | 可见角色落地与对应服务/协议证据一致；LOADED或健康不代替 |
| 连续心跳与会话保持 | 待开始 | 按既定方案连续10分钟回包、无异常断会话，记录完整观察结果 |
| 两类账号退出重登/保存 | 待开始 | 各自正常退出再登录，角色、装备、归属及本次受控进度持久一致 |

全部通过才关闭D-02/F-05/D02-R6。新旧号可先旧后新共用基础链，但两类账号均须重登；失败修复后正常重建部署复验。验收证据放专题/报告，本文只更新结论和链接。

## 6. 已完成任务与成果目录

下列均为**已完成的限定验收**，逐行保留“已完成”状态，合并展示不删除旧ID；父项未完成状态不被覆盖。后续完成的任务继续在本文件登记，只有验收范围一致的项才可合并。详细样本、提交、测试数量、失败和实现限制在链接专题及[原任务全文](Project.archive-2026-11-08.md#任务卡)。有后继不重开已验子项；报告里的历史待办若与当前工作包冲突，以当前依赖和最新专题证据为准。

### 6.1 基础、登录与会话

| 任务ID | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| DOC-01、A-01、ENV-01 | 已完成 | 接手规范、固定客户端和隔离研发基线；不代表7.2可连接。 | [提取/输入](docs/CLIENT_72_EXTRACTION.md)、[研发基线](docs/CLIENT_72_RESEARCH_VALIDATION.md) |
| A-02、A-02a、A-02b | 已完成 | 服务端静态协议盘点及动态来源守卫；开放插件/事件边界保留。 | [表面盘点](docs/CLIENT_72_PROTOCOL_INVENTORY.md) |
| A-03a、A-03b、A-03c、D02-R0 | 已完成 | 方法索引、管理器依赖、Proto候选矩阵与入场台账；只完成A-03/B-02/C-01的台账层，运行闭包未验。 | [源码图](docs/CLIENT_72_LOGIN_SOURCE_GRAPH.md)、[依赖](docs/CLIENT_72_LOGIN_DEPENDENCIES.md)、[矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md) |
| A-04a、A-04b、A-04c、A-04d1 | 已完成 | 元数据、泛型/父类/布局、静态包号与签名恢复；同值域和运行时未知保留。 | [提取](docs/CLIENT_72_EXTRACTION.md)、[网络](docs/CLIENT_72_NETWORK.md)、[签名](docs/CLIENT_72_SIGNATURES.md) |
| B-01 | 已完成 | 三层模型、输入约束及研究/ready校验；未知可记录但不自动晋级。 | [模型](protocol/cn-7.2/README.md) |
| B-02a、B-02b、A-04d2 | 已完成 | 包头/心跳raw及Token保护writer限定恢复。 | [网络](docs/CLIENT_72_NETWORK.md)、[保护入口](docs/CLIENT_72_PROTECTED.md) |
| A-04d3、A-04d3a、A-04d3b、A-04d3c、A-04d3c1、A-04d3c2、A-04d3c3 | 已完成 | Login完整raw、响应叶子/非空图、map AOT和合并行为；业务/运行时合同另验。 | [Login wire](docs/CLIENT_72_LOGIN_WIRE.md)、[响应图](docs/CLIENT_72_GRAPH_READERS.md)、[AOT](docs/CLIENT_72_MAP_AOT.md) |
| A-04d4a、A-04d4b、B-02c1、A-04d5b | 已完成 | 登录标记/枚举、请求响应身份及首批字段来源。 | [登录语义](docs/CLIENT_72_LOGIN_SEMANTICS.md)、[会话](docs/CLIENT_72_SESSION.md) |
| A-04d5a、B-02c2、A-04d5c、B-02c3、A-04d5d、A-04d5e、B-02c4、A-04d5f | 已完成 | seed、token保存、SDK来源与请求构造/发送入口限定链。 | [会话](docs/CLIENT_72_SESSION.md) |
| A-04d5g、A-04d5h、B-02c5、A-04d5i、B-02c6、A-04d5j、B-02c7、A-04d5k、B-02c8、A-04d5l、A-04d5m | 已完成 | 原生RSA/验签、密钥安装、UID/token、认证来源和分区候选链；实际SDK调度另验。 | [密码学/会话](docs/CLIENT_72_SESSION.md) |
| A-04d5n1、A-04d5n2、A-04d5n3、A-04d5n4、A-04d5n5、A-04d5o、B-02c10、A-04d5p、A-04d5q | 已完成 | 分区解析/配置/恢复链、key选择、Handler到Login；未知业务不因路径通过而命名。 | [分区/Handler](docs/CLIENT_72_SESSION.md) |
| A-04d5r、A-04d5s、A-04d5t、A-04d5u、A-04d5v、A-04d5w | 已完成 | 原生封/收帧、Ec2b、Token/Login完整帧限定链与错误边界；自然socket时序未验。 | [网络](docs/CLIENT_72_NETWORK.md)、[会话](docs/CLIENT_72_SESSION.md) |
| F-01、F-02a、F-02b、F-02b1、F-02b2、F-02c | 已完成 | 固定参考差异、登录/响应raw与最小发送、连续收帧key合同；完整登录未验。 | [融合](docs/CLIENT_72_ASTAPS_FUSION.md)、[收帧](docs/CLIENT_72_RECEIVE_KEY.md) |
| B-02d、B-03a、C-01a | 已完成 | 选人三件组研究、生成和业务/保存接入；新号显式选择，不代入实机。 | [选人](docs/CLIENT_72_BORN.md) |
| C-01b | 已完成 | 真实帧入口、最终发送域检查及注册冲突最小修复；业务映射/完整C-01另验。 | [网络边界](docs/CLIENT_72_NETWORK_BOUNDARIES.md) |

### 6.2 初始化、场景与Avatar

| 任务ID / 成果 | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| F-03d3f4d2-SECOND-ALLOCATOR-SPEC | 已完成 | 主线29pin独立第三20429B同双fresh；实际generic AOT/caller/唯一pdata及201B/53条核原ac214。旧265是静态显示错误，旧请求/pin保留。零参Default三QWORD及第二pop两条有界后继明确，但仅proposal，不改本轮AB98/2304，不批准新采集/写/CPU或完整对象语义。 | [长度勘误、源合同、后继及停止点](docs/CLIENT_72_D02_SECOND_ALLOCATOR_SPEC_20261110.md) |
| F-03d3f4d2/R1-METHOD-CACHE | 已完成 | 原lookup/hash/深比较与hit/miss边界、完整有界collector及主线第三已验；新ready原版90秒实际候选图取得、正常页/退出及保护文件保持。原生采样图回放/自然caller/完整构造/default/Handler/g2未代验。 | [合同与实机输入](docs/CLIENT_72_AVATAR_METHOD_CACHE.md) |
| F-03d3f4d2/R1-METHOD-CACHE-REPLAY | 已完成 | 同轮采样图的原9查询及连续原factory/8方法/64B写回通过，三新进程同字节、20检查/12独立审核；旧六前缀保留，止准确16B请求。自然caller/完整cold/default/Handler/g2仍未验。 | [源绑定回放](docs/CLIENT_72_AVATAR_METHOD_CACHE_REPLAY.md) |
| F-03d3f4d2/R1-HIERARCHY-STORAGE | 已完成 | 准确16B/两clear/已采Object8B复制、原store/xchg发布通过；三新进程同字节，17检查及10独立审核；4新增模型/45读345B。下一32B/完整cold/default/Handler/g2未代验。 | [有界原生存储](docs/CLIENT_72_AVATAR_HIERARCHY_STORAGE.md) |
| B-02e1、B-02e2、B-02e3、B-02e4、B-02e5、B-02e6、B-02e7 | 已完成 | 选人成功继续、缓存等待、计时/场景查询、列表和非空子图消费片段；完整继续/落地未验。 | [继续](docs/CLIENT_72_BORN_CONTINUATION.md)、[场景消费](docs/CLIENT_72_SCENE_DATA.md)、[嵌套](docs/CLIENT_72_SCENE_NESTED.md) |
| F-03a、F-03b、F-03c | 已完成 | 八握手、容量和进入场景通知最小发送合同；完整初始化/实体另验。 | [握手](docs/CLIENT_72_SCENE_HANDSHAKE.md)、[容量](docs/CLIENT_72_STORE_WEIGHT_CONTRACT.md)、[入场通知](docs/CLIENT_72_ENTER_SCENE_CONTRACT.md) |
| F-03d1a、F-03d1b1、F-03d1c | 已完成 | PlayerData raw、用途与有限生产接入；其余属性及完整初始化不晋级。 | [PlayerData](docs/CLIENT_72_PLAYER_DATA_CONTRACT.md) |
| F-03d2a、F-03d2a1、F-03d2a2、F-03d2a3、F-03d2b1、F-03d2b2、F-03d2c1、F-03d2c2 | 已完成 | 库存完整raw、Material/Equip有限语义、独立适配器及最小生产发送；精炼转换/其他分支保留。 | [库存生产](docs/CLIENT_72_INVENTORY_PRODUCTION.md)、[装备](docs/CLIENT_72_EQUIP_CONTRACT.md) |
| F-03d3a、F-03d3b、F-03d3c、F-03d3d、F-03d3e1、F-03d3e2、F-03d3e3、F-03d3e4、F-03d3e5、F-03d3e6、F-03d3e7、F-03d3e8、F-03d3g1 | 已完成 | 完整Avatar raw、子树/容器及独立Java适配器；不等于必要字段/正式sender。 | [完整raw](docs/CLIENT_72_AVATAR_COMPLETE_RAW.md)、[适配器](docs/CLIENT_72_AVATAR_WIRE_ADAPTER.md) |
| F-03d3f1、F-03d3f2a、F-03d3f2b、F-03d3f3 | 已完成 | 身份/三属性/装备GUID、Hero与本地角色来源、Team限定消费。 | [Avatar合同](docs/CLIENT_72_AVATAR_CONTRACT.md)、[控制](docs/CLIENT_72_AVATAR_CONTROL.md)、[入场身份](docs/CLIENT_72_SCENE_ENTRY_IDENTITY.md)、[队伍](docs/CLIENT_72_AVATAR_TEAM.md) |
| F-03d3f4a、F-03d3f4b、F-03d3f4c、F-03d3f4d1、F-03d3g2-prep | 已完成 | 生命态、FightProp、depot、构造/省略片段及真实getter隔离投影；完整构造和g2未验。 | [生命态](docs/CLIENT_72_AVATAR_STATE.md)、[属性](docs/CLIENT_72_AVATAR_FIGHT_PROPS.md)、[depot](docs/CLIENT_72_AVATAR_SKILL_DEPOT.md)、[构造片段](docs/CLIENT_72_AVATAR_INITIALIZER.md)、[投影](docs/CLIENT_72_AVATAR_JAVA_PROJECTION.md) |
| F-03d3s1、F-03d3s2a | 已完成 | SceneInfo有限非空raw及GUID/实体/附属模型关联；完整能力/生命周期未验。 | [Scene raw](docs/CLIENT_72_SCENE_INFO_RAW.md)、[身份关联](docs/CLIENT_72_SCENE_INFO_CONTRACT.md) |
| Avatar深度子合同（归F-03d3f4d2） | 已完成 | rgctx/List/Sorted、槽查询/回退、tag29/38、Ornament来源、warm/cold与接口片段已验；父descriptor/完整Handler仍未验。 | [深度合同](docs/CLIENT_72_AVATAR_DEEP_RESEARCH.md)、[warm构造](docs/CLIENT_72_AVATAR_LIVE_CONTRACT.md)、[cold合同](docs/CLIENT_72_AVATAR_COLD_CONTRACT.md)、[cold后继](docs/CLIENT_72_AVATAR_COLD_COMPLETION.md)、[接口](docs/CLIENT_72_AVATAR_COLD_INTERFACES.md) |
| Avatar真实表与HandlerClosure限定成果（归D02-R1/R3） | 已完成 | 六真实表、同缓存消费、Int32配置与泛型factory有限后继；NativeConfig旧typed解释已撤回，不批准自然模式/默认或g2。 | [六表/勘误](docs/CLIENT_72_AVATAR_NATURAL_TABLES.md)、[HandlerClosure](docs/CLIENT_72_AVATAR_HANDLER_CLOSURE.md) |
| F-03d3f4d2-parent-continuation | 已完成 | fresh静态父descriptor重定位模型、准确vector增长/释放及单线程已知锁后继；实际止于class1234未采读，未写cachedclass/default，完整cold/Handler未完成。 | [父类后继](docs/CLIENT_72_AVATAR_PARENT_CONTINUATION.md) |
| F-03d3f4d2-fresh-ancestor | 已完成 | 精确Object kind28/canonical模型、fresh1234重定位及原1239/1234类壳发布；真实首缺cache1169。Nullable opaque模型与未绑定对照分列，完整类初始化/default/Handler未完成。 | [fresh后继](docs/CLIENT_72_AVATAR_FRESH_ANCESTOR.md) |
| R1-SAMPLE-INTERFACES-prep | 已完成 | 1169/1249接口和Pool TLS索引的最小采样器、固定来源及预算已验；实际新user-ready采样未执行。旧Array/Type/371等不重采，null/unknown与上层未完成保持。 | [接口采样](docs/CLIENT_72_INTERFACE_CONTEXT_SNAPSHOT.md) |
| Scene深度/运行上下文限定成果（归D02-R1/R4） | 已完成 | retain/copy、hash注册/查询、token与80154消费、真实Scene/Gadget索引及工厂前缀；PoolCluster/stage/实体后继未验。 | [深度合同](docs/CLIENT_72_SCENE_DEEP_RESEARCH.md)、[入场后继](docs/CLIENT_72_SCENE_ENTRY_CLOSURE.md)、[运行上下文](docs/CLIENT_72_SCENE_RUNTIME_CONTEXT.md) |
| F-03d3s2-WORLD-ROW-SUCCESSOR | 已完成 | 20pin/41静态断言，主线第三42834B同双fresh；146e055f0仅AutoAllocRecycle slot0→slot2。唯一365B/79条row consumer只向148d6ebf0转交，未直接读+50/+58或写entity字段；MPLevel键职责沿已验来源，注册/能力组仍未知，不解锁新增Java alias或sender。 | [准确排除、原件与最小后继](docs/CLIENT_72_D02_SCENE_REQUIRED_SUCCESSOR_20261110.md) |
| F-03d3s2-pool-prefix | 已完成 | fresh实际Pool绑定→真实非零Allocate前缀，准确首缺loader TLS索引；独立零模式控制已验，未提供TLS/allocator成功模型，分配/生命周期仍未闭。 | [Pool后继](docs/CLIENT_72_SCENE_POOL_CONTINUATION.md) |
| F-03d3f4d2/R1-CLASS80-STORAGE | 已完成 | 原32B发布、两接口行、inflation/class返回及首vtable消费已验；停普通缓存输入，完整cold/default未验。 | [实际结果/预算/准确后继](docs/CLIENT_72_AVATAR_CLASS80_STORAGE.md) |
| F-03d3f4d2/R1-RUNTIME-INPUT-COLLECTOR | 已完成 | 纯collector来源、预算、owned正反及全部unique末尾复读已验；无target/native调用，不代替自然输入。 | [实际接口与来源](docs/CLIENT_72_AVATAR_RUNTIME_INPUTS.md) |
| F-03d3f4d2/R1-RUNTIME-INPUT-ADAPTER | 已完成 | 新原版运行的ordinary/warm候选、身份、统一复读及退出保护已验；自然caller与消费者闭合另验。 | [实际输入与未验边界](docs/CLIENT_72_AVATAR_RUNTIME_INPUTS.md#新ready原版实测) |
| F-03d3f4d2/R1-RUNTIME-INPUT-REPLAY | 已完成 | 同轮warm原返回、四ordinary MethodInfo及ASLR函数指针原store已验；cold停普通10294槽，完整构造/default原写未验。 | [实际消费者与精确后继](docs/CLIENT_72_AVATAR_RUNTIME_REPLAY.md) |
| F-03d3f4d2/R1-WARM-USAGES | 已完成 | main/optionalString分立的纯collector、来源、读预算与统一末尾复读owned已验；自然输入由adapter另验，完整构造未验。 | [真实来源/预算/主线验收](docs/CLIENT_72_AVATAR_WARM_USAGES.md) |
| F-03d3f4d2/R1-WARM-USAGES-ADAPTER | 已完成 | 有界自然采集、身份/复读及退出恢复已验；7/8候选取得，enum两个零槽及String空usage真实保留，完整图/消费者/父项未完成。 | [实际结果与准确未闭项](docs/CLIENT_72_AVATAR_WARM_USAGES.md#本轮合并实测与收尾) |
| F-03d3f4d2（最新warm原消费者后继）；E-01/R3-ITEM-ENTRY（String共同合同v2） | 已完成 | 三fresh10case/158步同字节，20检查及主线17审核；uint32构造/getter/List片段和零槽lazy分支有界执行，allocator/锁/definition pointer分别止准确首缺。String真实Dictionary carrier合同已由主线冻结为独立54读2704B；只批准collector实现，未采自然图、未完成比较/配置/父项。 | [原执行、未知和合同](docs/CLIENT_72_AVATAR_WARM_CONTINUATION.md) |
| F-03d3f4d2（typed allocator普通查询后继步骤） | 已完成 | 原完整Avatar入口连续执行allocator及旧已验纯native查询，61条完成/62次前观察，准确止于未采普通类缓存root；三fresh同字节，26测试及主线13项审核通过。未取得分配返回、普通1255结果或GC/TLS/OS成功，不代验完整构造。 | [原窗、执行、真实首缺及String后继](docs/CLIENT_72_AVATAR_ALLOCATOR_SUCCESSOR.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（同epoch联合后继采集工具步骤） | 已完成 | 独立collector/adapter、三fresh人工图、16测试及134项审核通过；最坏76读/3264B、统一final与18父pin保持，主线实际stock/source门禁通过。只批准新epoch采集准备，未采自然String/普通root或验原生消费，父项不晋级。 | [合同、工具及实际门禁范围](docs/CLIENT_72_AVATAR_STRING_SUCCESSOR_INPUTS.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（联合stock自然输入采集步骤） | 已完成 | 新实时确认下90秒/44监测，69读2704B取得四分支同epoch候选图；15项完整纯重放、身份/末尾复读及正常页/退出保护通过。FindEntry仍零，factory/比较/完整构造/配置能力未验；只交冻结输入给后继，不晋级父项。 | [实际输入、去重预算及未验边界](docs/CLIENT_72_AVATAR_STRING_SUCCESSOR_INPUTS.md#新实时确认的联合stock采集) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（fresh原getter/普通查询比较后继步骤） | 已完成 | 两fresh同字节，真实GetDefault25条返回同轮Default；完整Avatar连续105完成后止缺TLS4B。13测试/13主线审核及限定String共同边界冻结通过；无host返回/跨epoch数据，Equals体/allocator返回/完整父项未验。 | [实际原执行、准确首缺及冻结范围](docs/CLIENT_72_FRESH_AVATAR_STRING_SUCCESSOR.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（String Equals实际入口前缀步骤） | 已完成 | 无pdata事实保留，独立获批64B中只执行可达48B；两fresh8call/66完成、13测试及12主线审核通过。null/null、同owned指针、长度不同实际AL=1/1/0；等长不同地址停Helper2908尾跳前，未比较字符/自然键，不完成完整比较器/FindEntry或父项。 | [准确源、owned范围与尾ABI](docs/CLIENT_72_STRING_EQUALS_SUCCESSOR.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（String原字符比较步骤） | 已完成 | 两fresh各14case从实际10339经原尾跳/177B Helper2908返回，累计28call/1302完成且预期AL全符；13只读测试及主线15审核通过。显式owned NUL尾读逐字节保留；自然allocator终止存储、Hash/FindEntry、自然键、完整Comparer/构造及父项未验。 | [原字符路径、预算与主线验收](docs/CLIENT_72_STRING_EQUALS_CONTENT.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（必要String自然后继边界） | 已完成 | 三个原窗/28关键opcode/10pin及TLS420B静态复核、6测试与主线第三同字节；明确Gadget到asset-key的实际consumer及首NUL hash规则，旧5次hash只复用。自然字典/查询/MI/帧/trigger缺失，26/816图仅proposal，未新CPU/PE/live或验完整查询。 | [准确源、必要性、输入与停止条件](docs/CLIENT_72_D02_A_SUCCESSOR_20261110.md) |
| F-03d3f4d2（allocator TLS直接callee单窗步骤） | 已完成 | 重新核五stock后取得唯一312B/74指令PE exception完整窗；双静态报告与主线第三同字节，明确size32/bin2、真实freelist/写域及三个未审外部边。15/82读取与捕获副本写域仅proposal，未新CPU/live或验allocator返回/完整Avatar。 | [真实原窗、输入、写入与停止合同](docs/CLIENT_72_D02_AVATAR_TLS_SUCCESSOR_20261110.md) |
| F-03d3f4d2（owned线程ABI步骤） | 已完成 | 固定phnt分支/提交及SDK来源，独立cl构建、两fresh恰六次class0、9测试和原48B独立审核通过；仅当前机器原生x64自有main/worker的TEB/身份/GS58关系成立。不是游戏线程/TLS/池或回放许可，未晋级完整Avatar。 | [实际构建、原件、失败及后继](docs/CLIENT_72_D02_OWNED_TEB_ABI_20261110.md) |
| F-03d3f4d2（同epoch只读TLS/R1采集器实现步骤） | 已完成 | 七冻结来源/原audit27–35关系、21人工测试及主线独立第三图/失败守卫通过；保留本次正常launch原primary，API另账、13/340→统一26/680。仅工具实现，未调用真实目标API或采自然TLS，primary非自然allocator线程证明、pool写域/replay仍未批。 | [实现、准确预算及主线审核](docs/CLIENT_72_D02_TLS_INPUT_COLLECTOR_20261110.md) |
| F-03d3s2-pool；F-03d3f4d2（Pool TLS条件分支合同步骤） | 已完成 | 16冻结pin/46关键反汇编/20call site、双静态20007B与主线第三同字节；明确Pool+1990与Avatar+19d0不同、P+20为U64界限、条件读取及首外call/首写/未采global前停止。原窗没有保存机器码hex，不声称重核raw；18/369→36/738仅未来合并规格，未验自然caller或分配。 | [准确分支、预算及执行边界](docs/CLIENT_72_D02_POOL_TLS_CONTRACT_20261110.md) |
| F-03d3s2-pool；F-03d3f4d2（合并条件只读工具步骤） | 已完成 | 34人工测试、原21测试字节保持及默认完整/错误报告同原版，12来源门禁和主线独立第三图/primary人工门禁通过。两个固定profile：默认13/340→26/680，合并18/369→36/738，单208；统一final、API数不变。0真实API/native/live，未验自然allocator、block/写域或pop。 | [实现与明确边界](docs/CLIENT_72_D02_TLS_POOL_COLLECTOR_20261110.md) |
| F-03d3f4d2（完整head输入及单pop副本合同步骤） | 已完成 | 27pin/44关键bytes、409B必经callee与20直接site/12roots，双23139B及主线第三同字节；真实L32..208完整span、六精确写域仅proposal，未获CPU/写/live许可。单block不是完整构造，后继items/第二类另记。 | [真实长度、原指令与精确边界](docs/CLIENT_72_D02_AVATAR_CAPTURE_ALLOCATION_SPEC_20261110.md) |
| F-03d3f4d2（完整head只读工具实现步骤） | 已完成 | 52人工测试、原34源码保持、两旧profile完整输出同before、主线L87/L208第三图/四tool来源与primary人工门禁通过；第三固定profile最大28/622→56/1244，API上限不变。0真实API/native/live，未实采block、执行pop或批准写。 | [实现及主线独立审核](docs/CLIENT_72_D02_AVATAR_BLOCK_COLLECTOR_20261110.md) |
| F-03d3f4d2（items与第二类输入合同步骤） | 已完成 | 20pin/metadata/call字节双10281B与主线第三同字节；B+5b74628仅原值供H+10，producer/数组身份未知；第二B+5897cf0绑定HashSet2147/instance11 UInt32，旧请求56/bin4非新值。增量8/304→16/608为未批proposal，不补普通槽或盲跟N。 | [准确来源与下一有限输入](docs/CLIENT_72_D02_AVATAR_CTOR_NEXT_INPUTS_20261110.md) |
| F-03d3f4d2（items与HashSet同epoch只读工具步骤） | 已完成 | 第四固定profile、68人工测试/主线S65/L87及共享L208第三与API人工门禁通过；原三profile九场景完整报告/请求同before，旧test只适配一处registry名称。最大36/926→72/1852，统一final、五tool新epoch；0真实API/native/live，不验第二pop/数组身份/完整Ctor。 | [实现、预算与主线审核](docs/CLIENT_72_D02_AVATAR_CTOR_COLLECTOR_20261110.md) |
| E-01（Depot talents职责/投影/真实Java步骤） | 已完成 | 同typed Depot+28→解码匹配→EnhanceDetail Talent=8/current发布、唯一5901B/16pin及原default常量主线第三通过；八投影正反/第三同bytes、五实际Java/14独立审核通过。两出生真实构造返回，单删talents均NPE529；不批准proud规范化、空能力、完整资源/原生Avatar/g2/保存。 | [直接职责](docs/CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md)、[真实Java与scope](docs/CLIENT_72_D02_DEPOT_TALENTS_JAVA_20261110.md) |
| E-01（两出生Avatar已批准片段合并步骤） | 已完成 | 五完整报告同canonical行、32片段值/float32位、两独立与主线第三1211B输出/20940B来源manifest逐字节一致，七正反测试通过；仅十个已批字段，completeAvatar/resourceReady仍false。0新Java/CPU/live，不覆盖talents、完整构造/保存。 | [同源规则、实际审核及边界](docs/CLIENT_72_D02_BIRTH_AVATAR_JOIN_20261110.md) |
| F-03d3f4d2/A-GC-OLDFLAGS8 | 已完成 | 本epoch L32/oldFlags8必经904B helper的准确15条SSE分支闭合：两次同址H+10h/16清理、无该分支GC回调/外部call。18pin与主线第三同字节；完整20分配/12根及子构造依赖列全，其他长度/GC/自然线程未验。 | [静态合同与完整构造依赖](docs/CLIENT_72_D02_GC_HELPER_20261110.md) |
| F-03d3f4d2/FIRST-POP-COPY | 已完成 | 原1405336c0入口经同epoch普通查询/TLS/pop/helper/post原ret到owned sentinel，200条原指令、九写七span44B；8测试及主线第三36425B同SHA通过。无自然ctor frame，线程/GC所有权、第二分配/Default及完整Avatar/g2仍未验。 | [原CPU副本、拒绝与边界](docs/CLIENT_72_D02_FIRST_POP_COPY_20261110B.md) |
| F-03d3f4d2/SECOND-INPUTS-OFFLINE | 已完成 | 第五pure profile/六pin adapter固定44/1184首账、88/2368总账及208单读；83人工测试、旧11路径仅adapter源pin变、两个最终driver同SHA通过。首块仅已验L32/flags8继续；第二helper/Default cold及自然输入/线程/GC/完整构造未验。 | [精确图、纠正与准备门禁](docs/CLIENT_72_D02_SECOND_INPUT_COLLECTOR_20261111.md) |
| F-03d3f4d2/A2-STOCK-PREPARATION | 已完成 | 两fresh及主线第三14人工组/4 PS parser同SHA，98源新seal与actualCheckOnly/19文件保护/进程固定监听空通过。新库存仅接纳已知用户AGENTS修改，其余保护不变。仅准备完成；新ready未取，采集/正常退出另验。 | [范围与操作条件](docs/CLIENT_72_D02_A2_STOCK_PREPARATION_20261111.md) |
| F-03d3f4d2/A2-STOCK-CAPTURE（第二输入子出口） | 已完成 | 本轮新ready正常启动45时点90秒、第五profile一次；独立R2/request56/bin4、完整L64/flags8取得，准确停unknown-post-pop，Default未读。72读1568B/36稳定span、PI/TBI/原句柄关闭/结束源码stock和正常退出保护进程端口主审通过。非第二pop/GC所有权/完整Avatar。 | [实际原件、停止与退出](docs/CLIENT_72_D02_A2_STOCK_CAPTURE_20261111.md) |
| F-03d3f4d2/A-GC-SECOND64（条件输入合同） | 已完成 | 原pop链推导H2+16/EDX0/R8=48、6unique29B条件输入/互斥table/两侧预测写域与窗外尾跳首缺一次列全；主线第三15094B与两fresh同SHA。全部新控制未采，0CPU/写/原返回。 | [条件分支与v2最小图](docs/CLIENT_72_D02_SECOND_GC64_20261111.md) |
| F-03d3f4d2/GC-DEFAULT-INPUTS | 已完成 | 纯profile/七pin adapter、helper准备及新stock同epoch输入已验限定范围；42稳定span/84读1648B，I6/T0=524288→原AVX table，Default非零不透明且不读对象。CPU/写/原返回及完整构造未验，旧v1/五profile不改。 | [实施](docs/CLIENT_72_D02_GC_DEFAULT_INPUTS_20261111.md)、[实采与退出](docs/CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md) |
| F-03d3f4d2/GC-DEFAULT-OFFLINE | 已完成 | 100项纯/adapter回归、主线第三与两fresh同SHA，旧五profile三形状规范化完整结果/请求一致；七pin、新module end6d19144、一次final/TBI96/close及独立Default边界核实。修unsupported长度r8显示，未扩执行许可。 | [主线验收](docs/CLIENT_72_D02_GC_DEFAULT_INPUTS_20261111.md) |
| F-03d3f4d2/GC-DEFAULT-STOCK-PREPARATION | 已完成 | helper两fresh及主线第三15人工组/4 PS parser同SHA；107源新seal/actualCheckOnly及19文件/缺席/进程固定监听空通过。仅新scope准备出口；后续新ready实采另项验收，不执行目标helper或装DLL。 | [实验与退出条件](docs/CLIENT_72_D02_GC_DEFAULT_STOCK_PREPARATION_20261111.md) |
| F-03d3f4d2/GC-DEFAULT-STOCK-CAPTURE | 已完成 | 新ready后45时点/一次采样；主线纯重建原84记录/PI/TBI/关闭/107源快照及stock，用户确认退出、OS/保护/端口核验通过。页面/异常未获本轮明确反馈，不扩大验收。自然allocator/GC/第二返回与完整Avatar未验。 | [来源与准确范围](docs/CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md) |
| F-03d3f4d2/CLEAR48-OWNED-PREP | 已完成 | 独立owned两路径driver及7纯门禁已交；主线区分固定Capstone distribution5.0.9/binding5.0.7/native(5,0,1280)并固定原文件。实际SSE35条/三写/RET通过，AVX20条后严格guard停止，完整两路径执行未完成。 | [原件与实际边界](docs/CLIENT_72_D02_CLEAR48_OWNED_20261111.md) |
| F-03d3f4d2/CLEAR48-BACKEND | 已完成 | 一次原6B owned诊断invalid10、一次CPUID原AVX bit0，112pin稳定；不再XGETBV/改model/强走SSE。主线纯第三21462B同两fresh，定位当前backend能力边界；非AVX成功或原GC返回。 | [准确失败及下一后端规格](docs/CLIENT_72_D02_CLEAR48_BACKEND_20261111.md) |
| F-03d3f4d2/CLEAR48-BACKEND-GITHUB | 已完成 | Terra max按Sol max中文计划README-first核官方12调用/two ref/四blob，主线静态核原tree/blob一致。VEX-L拒绝/缺opcode项解释边界，无可直接使用AVX支持；DLL实际构建关联及全分支覆盖未知。 | [来源及复用范围](docs/CLIENT_72_D02_UNICORN_AVX_SOURCES_20261111.md) |
| F-03d3f4d2/CLEAR48-NATIVE-CAPABILITY-SPEC | 已完成 | Sol max固定原6B/23B/13B、Intel SDM及MS ABI/SEH，主线静态核112pin与原904切片通过；独立EXE/host前置/一次call/异常/输出/预算合同已定。仅规格，未build/CPU，不扩904B或捕获副本。 | [完整最小规格](docs/CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_20261111.md) |
| F-03d3f4d2/CLEAR48-NATIVE-CAPABILITY-IMPL | 已完成 | 5纯测试/112原pin通过；主线独立构建exit0、128实际include/6搜索库/编译DLL与PE/caller/SEH核实。两次真实原6B能力及独立UD2通过，完整288B核对；两轮包装器失败原件保留。仅owned单条能力，未验904B/自然GC/第二分配/Avatar。 | [实际构建、三run与边界](docs/CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_RESULTS_20261111.md)、[冻结实现交付](docs/CLIENT_72_D02_CLEAR48_NATIVE_IMPLEMENTATION_20261111.md) |
| F-03d3f4d2/CLEAR48-NATIVE-TRACE-SPEC | 已完成 | Sol max原176条/读写域、真实CALL/RET与逐条事务合同主线静态复核；仅规格，不证明debug TF/YMM或原904B运行。 | [原窗与准确范围](docs/CLIENT_72_D02_CLEAR48_NATIVE_TRACE_SPEC_20261111.md) |
| F-03d3f4d2/CLEAR48-NATIVE-TRACE-GITHUB | 已完成 | Sol max中文计划、Terra max README-first/分支定向24高层操作，外部clone固定ref/tree/blob与16项主审通过；部分覆盖，参考Win32事件模式，不导入TitanEngine或其bytepatch/context写法。 | [来源、差异与剩余witness](docs/CLIENT_72_D02_CLEAR48_TRACE_GITHUB_20261111.md) |
| F-03d3f4d2/CLEAR48-NATIVE-TRACE-IMPLEMENTATION-CONTRACT | 已完成 | 四设计JSON/256B descriptor/128B bootstrap及48字段offset、精确25/11/23/3/13B由root源级第三核通过；只冻结W1/W2/W3，未编译断言/执行debug witness。root唯一header已给Terra，完整904B/捕获副本仍另批。 | [分包、接口与预算](docs/CLIENT_72_D02_CLEAR48_TRACE_IMPLEMENTATION_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-FULL-HELPER-IMPLEMENTATION-CONTRACT | 已完成 | root第三3449B同最终交付SHA，54源/112旧pin/176原指令/67offset，37/40路径及63允许目录已审；只限人工首组规格，不代helper执行或完整Avatar。 | [冻结合同及主线发布](docs/CLIENT_72_D02_CLEAR48_FULL_HELPER_IMPLEMENTATION_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-FULL-SHARED-V1 | 已完成 | 384/256 B、67个编译偏移断言、176项固定目录、实际PE与依赖、两组runner各309项输入前后哈希已验。 | [实际构建与首组](docs/CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-FULL-PARENT-V1 | 已完成 | 首次BOOT失败保留。新合同按自有线程来源接收BOOT通知，主线程SSE/AVX的37/40条退休、实际TF、页、写域与退出已验。 | [BOOT合同及原记录](docs/CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-FULL-CHILD-V1 | 已完成 | 两个自有child各一次真CALL，SSE三笔16 B和AVX两笔32 B，写域均48 B，实际RET、C返回、释放及OS0通过。范围限人工输入。 | [child设计](docs/CLIENT_72_D02_CLEAR48_FULL_CHILD_20261111.md)、[实际验收](docs/CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-FULL-VALIDATOR-V1 | 已完成 | 33项Python通过，包括两条完整人工CLI、BOOT来源与TRACE线程变异。两次实际原始记录经严格checker与主线写域复核。 | [validator设计](docs/CLIENT_72_D02_CLEAR48_FULL_VALIDATOR_20261111.md)、[实际验收](docs/CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-STOCK-DELTA | 已完成 | 核 stock 三个控制量和 taken 路径，36 条静态候选；人工 AVX 共有尾部和写域可复用。第三复核一致。stock 原写/返回、第二 allocator、Default 消费待验。 | [条件和后继](docs/CLIENT_72_D02_CLEAR48_STOCK_DELTA_20261111.md) |
| F-03d3f4d2/CLEAR48-STOCK-OWNED | 已完成 | 同 epoch H2/三个控制量在两个自有进程调用原904 B helper，两个返回地址/清零48 B/守卫头部/释放及退出通过。逐指令、第二 allocator/Default 与自然所有权另验。 | [运行和边界](docs/CLIENT_72_D02_CLEAR48_STOCK_OWNED_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-WITNESS-CHILD | 已完成 | main6实际三项新owned witness均验收：W1三退休/非零64位返回/正常退出，W2七退休/两32B原件及288B guards，W3仅CALL退休、UD2未退休/负证人收口。只限backend，904B未链接。 | [三项实际原件与准确范围](docs/CLIENT_72_D02_CLEAR48_TRACE_RESULTS_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-WITNESS-PARENT | 已完成 | 完整v2实际正常与负证人通过，TF-only/原flags/逐stop old-new与真实新线程退出来源、实际Continue/PI关闭通过；W1/W2 OS0、W3 parent30/child90，旧三W1失败永久保留。自然GC/allocator与Avatar不晋级。 | [三项实际原件与准确范围](docs/CLIENT_72_D02_CLEAR48_TRACE_RESULTS_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-ARTIFACT-TESTS | 已完成 | 独立Terra及root各8组纯人工PE/map门通过；旧空/缺/重复source清单红测保留，root要求精确五源后全绿。只验离线bootstrap材料门，不代真实构建/PE或debug witness。 | [主线源审核与门禁](docs/CLIENT_72_D02_CLEAR48_TRACE_CHILD_20261111.md#主线源级审核与独立-artifact-门)；独有trace_artifacts工具/test |
| F-03d3f4d2/CLEAR48-TRACE-VALIDATOR-OFFLINE | 已完成 | 独立Terra与root4测试方法通过，含三synthetic CLI/24原件/5回执变异/输出拒绝；严格绑定Get/Set/event/transaction/材料与退出。仅离线工具验收，W1实际仍失败。 | [接口及纯门](docs/CLIENT_72_D02_CLEAR48_TRACE_VALIDATOR_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-ROOT-RUNNER-OFFLINE | 已完成 | 独立Terra与root9组mock通过，false审核/空数组前置拒绝、精确argv/12路径/owned Popen/创建身份/超时仅own句柄与保真退出码已核；实际已执行单一W1并保留失败回执，不代表native通过。 | [实际回执与材料](docs/CLIENT_72_D02_CLEAR48_TRACE_ACTUAL_20261111.md)；独有trace_run工具/test |
| F-03d3f4d2/CLEAR48-TRACE-EFLAGS-READBACK | 已完成 | Sol max合同/官方三页与SDM、Terra max来源及root第三审核通过；原202/302/300及旧W1失败/零退休保留。新v2仅许回读bit1从1到0，其余31位/寄存器/组/提交TF-only不变，不证明内核mask或执行态。phnt懒取偏差已留账并停Git。 | [合同](docs/CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_20261111.md)、[来源](docs/CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_SOURCES_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-EFLAGS-V2-IMPLEMENTATION | 已完成 | 两Terra独立文件实现，root逐差/26纯测试（validator9、runner9、artifact8）通过；提交TF-only/原raw、bit1单向、31位/15GPR及关联负例已验。新main4实际构建exit0/PE caller与unwind通过，依赖审核与新witness另验。 | [parent源码范围](docs/CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_PARENT_V2_20261111.md)、[独立validator及负例](docs/CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_VALIDATOR_V2_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-WAIT-EXIT-LOAD | 已完成 | Terra parent仅WAIT_EXIT复用BOOT的LOAD_DLL Continue，validator仅W1/W2固定退休尾允许0+LOAD_DLL；root独立纯回归通过（含128/129、错身份/关闭/继续/异常/TF恢复、W3拒绝）。其它阶段/预算/条件文件句柄门不变，不代新实际W1。 | [parent差异](docs/CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_PARENT_20261111.md)、[尾序列与负例](docs/CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_VALIDATOR_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-WAIT-IDENTITY-CONTRACT | 已完成 | Sol max79raw/两冻结checker定向非通过诊断及writeEvents/sticky语义闭合；root一行+2源码同提案SHA，29纯门/main5实际成功Wait身份成立，validator身份门未降。该run真实exit/summary失败及前三W1未通过不变。 | [准确合同/诊断/边界](docs/CLIENT_72_D02_CLEAR48_TRACE_WAIT_IDENTITY_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-EXIT-LIFECYCLE | 已完成 | Sol max完整有界v2/机器规格，root25输入+副本/11交付/14官方hash已核；TRACE仍primary，退出仅真实创建登记/实际Continue与末线程EXIT，PI/event/file关闭分账、原预算不扩。前三W1失败不变，不代native成功。 | [完整合同及独立负例规格](docs/CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_CONTRACT_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-EXIT-LIFECYCLE-V2-IMPLEMENTATION | 已完成 | 两Terra实现/root集成37纯门/main6真实build及PE/依赖第三；新W1/W2/W3实际完整checker通过。W3后续metadata和早Term两缺口修复/原件保留；7/8仅人工未实测，完整helper另项。 | [实现、实际验收与未验](docs/CLIENT_72_D02_CLEAR48_TRACE_RESULTS_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-EXIT-LIFECYCLE-ROOT-BINDING | 已完成 | root runner精确review.source合同path/SHA/before-after绑定，缺合同/修改合同含review更新反例均不spawn；10 mock与主线集成36纯门通过。12 artifacts/5编译源/旧check_inputs保持，非真实native通过。 | [root绑定/纯门与范围](docs/CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_RUNNER_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-EXIT-LIFECYCLE-V2-INTERFACE | 已完成 | Sol max21输入/59静态断言及实际emitter→checker映射；唯一W3早Term缺口root已补准确顺序和两负例，37纯门通过，未发现第二接口冲突。不代CPU；后继3实际另验已通过。 | [独立接口审阅](docs/CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_INTERFACE_AUDIT_20261111.md) |
| F-03d3f4d2/CLEAR48-TRACE-EXIT-LIFECYCLE-SOURCES | 已完成 | Terra max14官方HTTP原件/正文、固定Titan README/必要源码和分支元数据已核；root14 raw/正文hash第三通过。16/18高层操作含两微软查询偏离原预见GitHub查询，已留账、精确查询UTC未知；无新Git/lazyfetch或runtime。 | [句柄/末线程来源及限定](docs/CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_SOURCES_20261111.md) |
| R2-ROUTE-CONFIG-SELECT/POSTREQUEST-ENTRY | 已完成 | 唯一310B/79条、原40108 CALL/return与16B搬移静态核实，首次任意入口String观察5读168B/绑定2读315B已定；5测试/主线第三95断言同SHA通过。普通尾跳不等于同步返回，自然请求/GC/EH未知。 | [入口与合法触发前置](docs/CLIENT_72_SDK_POSTREQUEST_ENTRY_20261111.md) |
| R2-ROUTE-CONFIG-SELECT/POSTREQUEST-OWNED | 已完成 | 8离线测试/12源epoch及独立Cargo release locked/offline构建通过；两fresh Win32各32 case共64个280B完整near/far emitter、原16B搬移/原caller/寄存器/128B栈/flags/LastError/高64返回及首次占用/失败通过。无SDK/客户端/安装/自然请求，GC/EH未验。 | [实际owned与边界](docs/CLIENT_72_SDK_POSTREQUEST_OWNED_20261111.md) |
| D02-R2/NATIVE-ACCOUNT-LOCAL-ROUTE（共用入口合同） | 已完成 | MDK/Account同3413E0唯一1071B/265条，min14搬移21B、首次任意入口仅读8B service/低32 mode；5测试和第三7原件同SHA通过。自然service/mode/loginUrl未知，批准自然route0项，不借MDK注册代客户端合同。 | [来源、ABI与接线条件](docs/CLIENT_72_SDK_NATIVE_ACCOUNT_LOCAL_ROUTE_20261111.md) |
| D02-R2/COMMON-SERVICE-OWNED | 已完成 | 6离线测试/15源epoch、独立Cargo构建及两fresh各104真实case通过（200原SUB+8独立witness），208完整emitter；source覆盖21B、实际near/far均20B规范编码/284B emitter，唯一resume/普通Win64/8B参数与1087B绑定账核实。自然服务/route/trigger、EH/高向量未验。 | [原SUB与独立witness实际验收](docs/CLIENT_72_SDK_SERVICE_ENTRY_OWNED_20261111.md) |
| D02-R2/NATURAL-SERVICE-TRIGGER（静态producer合同） | 已完成 | 原19B普通CheckLogin producer/三managed桥1181B/准确51B名字和native cell已核；主线第三22原件同两fresh、37断言/7测试通过。只证明更早同步接线规格，不代表自然service、mode、实际请求或认证；组合loader另项Daybreak阻塞。 | [身份、边界与原件](docs/CLIENT_72_SDK_NATURAL_SERVICE_TRIGGER_20261111.md) |
| D02-R2/SERVICE-TRIGGER-LOADER | 阻塞 | 源码包6纯检查已过，实际组合build退出101（u64→usize）；后继子代理遭平台安全门禁。按用户最新指示只保留source epoch/20源快照/失败日志，未来统一Daybreak处理，不重试或换模型。DLL/组合owned/自然事件未验，A/C继续。 | [Daybreak队列及具体恢复动作](docs/CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md) |
| F-03d3s2/SCENE-ROW-LIFECYCLE（模板关联子出口） | 已完成 | 唯一585B/120条下游将原row存entity.field138373/+140；不消费row+50/+58。16pin/30静态断言主线第三同SHA通过。条件容器调用非具名Remove，首未闭actual slot114、组件/拆除/通知及MPLevel完整配置不批准。 | [原窗、准确排除与后继](docs/CLIENT_72_D02_SCENE_ROW_LIFECYCLE_20261110B.md) |
| F-03d3s2/SCENE-SLOT114-FROZEN-AUDIT | 已完成 | 7pin/17静态断言主线第三5325B与两fresh同SHA；冻结36读全不含klass+460的8B，也没有factory实际返回实体→klass来源。只关闭准确缺输入审计；1483df040仍metadata候选，不读候选body。后继同调用frame及3读224B需合法factory触发/独立合同，Scene父项未完成。 | [缺口及最小来源链](docs/CLIENT_72_D02_SCENE_SLOT114_20261111.md) |
| F-03d3s2/SCENE-TEMPLATE-PREDECESSOR | 已完成 | 原592B/119条证明普通路径旧模板引用正常清空后再由wrapper关联新引用；主线第三46断言同两fresh及6测试。不证明销毁/组件拆除、helper所有权或动态slot114，完整Scene仍未验。 | [原引用生命周期](docs/CLIENT_72_D02_SCENE_TEMPLATE_PREDECESSOR_20261111.md) |
| F-03d3s2/SCENE-MPLEVEL-GROUP-SOURCE | 已完成 | root第三51096B同双fresh，4pin/35断言；ConfigMetaConfig路径hash数组选bin第二QWORD→reader→70954.m587454→同对象register来源已闭。actual bin/group/能力仍未知，slot114未晋级。 | [准确producer与唯一原窗](docs/CLIENT_72_D02_SCENE_MPLEVEL_GROUP_SOURCE_20261111.md) |
| F-03d3s2/SCENE-MPLEVEL-PARSER-WINDOW | 已完成 | 两fresh九原件相同，fresh完整817B caller及E8/AOT/唯一pdata得131B/42条；root4门禁/10458B第三同双fresh。仅source，不代MPLevel分组/自然reader或实际注册。 | [准确parser原窗](docs/CLIENT_72_D02_SCENE_MPLEVEL_PARSER_WINDOW_20261111.md) |
| F-03d3s2/SCENE-MPLEVEL-PREFIX | 已完成 | 131 B 原窗内联1 B、条件 refill、置位输出地址及 typed 消费者经125断言复核；实际2675 B正分支原窗双读一致。清位、实际分组及完整Scene待验。 | [前缀和原窗](docs/CLIENT_72_D02_SCENE_MPLEVEL_PREFIX_20261111.md) |
| F-03d3s2/SCENE-MPLEVEL-PARSER-CONTRACT | 待开始 | 原窗已验，语义后继尚未实际派发；用户要求当前工作验收后暂停，本卡不启动。以后恢复再审131B普通inline/refill及field350252首缺，不按机械首call盲扩helper树。 | 预留独有专题/private；需以后明确恢复 |


### 6.3 SDK、补丁、观察与运维前置

| 任务ID / 成果 | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| R2-LOG | 已完成 | SessionKeyAuthenticator敏感日志有效红绿、认证/会话采用/保存保持、隔离回归与portable构建；不变更认证策略，真实部署/SDK另验。 | [日志](docs/CLIENT_72_AUTH_LOGGING.md) |
| R2-SESSION | 已完成 | session verify独立单token响应、真实HTTP成功/错误/64位/嵌套、Java→原生parser/formatter正反和账号/密码/保存回归/构建；自然SDK及D-02未验。 | [适配与验收](docs/CLIENT_72_SDK_SESSION_ADAPTER.md) |
| D-01a、D-01b1、D-01b2、D-01b3a | 已完成 | 命名入口参数、启动RNG、全局/UTF8、TLS/Marshal限定合同；完整SDK和自然函数返回另验。 | [入口ABI](docs/CLIENT_72_PATCH_ABI.md)、[动态](docs/CLIENT_72_DYNAMIC_DEBUG.md)、[Marshal](docs/CLIENT_72_MARSHAL_STORAGE.md) |
| D-01b4、D-01b5a、D-01b5b、D-01b5e、D-01b5f、D-01b5g-preflight | 已完成 | 观察通道、自有SW/HW、实际退出/对照与观察前置均按限定范围验收；根因或本服成功未被推定。 | [通道](docs/CLIENT_72_DEBUG_CHANNEL.md)、[启动观察](docs/CLIENT_72_STARTUP_OBSERVER.md)、[软件点](docs/CLIENT_72_SOFTWARE_BREAKPOINT_CHANNEL.md)、[单HW](docs/CLIENT_72_SINGLE_HARDWARE_OBSERVER.md) |
| D-01b5c、D-01b5d、D-01b5h1 | 已完成 | 当前区/RegionList的身份和Java→原生消费、真实HTTP签名版本拒绝；7.2成功准入未验。 | [当前区](docs/CLIENT_72_HTTP_DISPATCH.md)、[列表](docs/CLIENT_72_REGION_LIST.md)、[HTTP路由](docs/CLIENT_72_HTTP_ROUTE_CONTRACT.md) |
| F-04a、F-04b、F-04c、F-04c2 | 已完成 | 运行前置核对、本地信任输入/保留验签补丁及便携路径供给；未批准整DLL加载。 | [前置](docs/CLIENT_72_RUNTIME_PREREQUISITES.md)、[信任](docs/CLIENT_72_TRUST_CONTRACT.md)、[补丁](docs/CLIENT_72_TRUST_PATCH.md)、[路径](docs/CLIENT_72_TRUST_LAUNCHER.md) |
| 原始启动与只读初始化成果（归D-01b / D02-R1/R2） | 已完成 | 原SDK登录页、初始化/cold数组与资源槽取证；首次错误解释已撤回，不代入本服登录。 | [原始启动](docs/CLIENT_72_UNINSTRUMENTED_STARTUP.md)、[初始化](docs/CLIENT_72_RUNTIME_INITIALIZATION_SNAPSHOT.md)、[cold数组](docs/CLIENT_72_COLD_ARRAYS_SNAPSHOT.md)、[资源槽](docs/CLIENT_72_LIVE_RESOURCE_CONTRACT.md) |
| SDK观察候选与HW分界成果（归D02-R2） | 已完成 | 标准API自有夹具/ETW过滤/WinHTTP无事件结果及HW退出边界已记录；不证明SDK观察覆盖，不默认重试。 | [观察夹具](docs/CLIENT_72_SDK_OBSERVER_DESIGN.md)、[ETW](docs/CLIENT_72_SDK_ETW_OBSERVER.md)、[WinHTTP](docs/CLIENT_72_WINHTTP_ETW_FIXTURE.md)、[HW分界](docs/CLIENT_72_D02_HW_EXIT.md) |
| SDK transport/login/Crypto/response限定成果（归D02-R2） | 已完成 | 准确请求所有权、原生key/算法/route、session单token规格；成功本服连接未验；DTO独立修复见R2-SESSION。 | [transport](docs/CLIENT_72_SDK_TRANSPORT.md)、[登录链](docs/CLIENT_72_SDK_LOGIN_CHAIN.md)、[Crypto](docs/CLIENT_72_SDK_CRYPTO_CONTRACT.md)、[response](docs/CLIENT_72_SDK_RESPONSE_CONTRACT.md) |
| SDK key核心及采样器准备（归D02-R1/R2/R4） | 已完成 | 未启用核心自有ABI、有限根/头采样工具已验；父descriptor已由后继补取，新增原生桥接见下项，实际加载仍未验。 | [key ABI](docs/CLIENT_72_SDK_KEY_INPUT_ABI.md)、[采样器](docs/CLIENT_72_ENTRY_CONTEXT_SNAPSHOT.md) |
| R2-KEY-native-bridge | 已完成 | 同一Rust核心实际供给准确caller→原PEM/RSA/Base64，保持入口状态/原返回及拒绝；限定离线桥接，不证明loaded、自然输入、hook wrapper或整DLL。 | [原生桥接](docs/CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md) |
| R2-ROUTE-loader | 已完成 | 准确SDKDelegate.LoadFile→包内TextAsset、实际配置与url:string/map/app_key片段；主combo builder直接读初始化map。完整Init、自然环境选择、签名与本服请求未验。 | [配置loader](docs/CLIENT_72_SDK_ROUTE_LOADER.md) |
| R2-ROUTE-SIGN | 已完成 | 原生HMAC-SHA256字节合同/准确caller与外层序列化；固定Java源码接收条件已审。完整builder/网络、自然配置/请求和运行HTTP接受未验，不新增无依据验签策略。 | [combo签名](docs/CLIENT_72_SDK_COMBO_SIGNATURE.md) |
| R2-NATURAL-CONFIG-contract | 已完成 | 准确Config root/env/map writer/reader与owned不可变collector；原持锁URL事件及key参数事件已定位。自然Init完成/teardown区间未闭，禁止外部异步深读，未取得自然env/key/route。 | [配置生命周期合同](docs/CLIENT_72_SDK_NATURAL_CONFIG.md) |
| F04-LOADER-CLEAN-owned | 已完成 | 独立零hook入口实际编译，自有Win32夹具核47导出、透明ABI/原调用和4类真实异常拒绝；未加载实际客户端DLL，完整性/实际时序与自然SDK另验。 | [透明加载](docs/CLIENT_72_CLEAN_LOADER.md) |
| F04-LOADER-CLEAN-live | 已完成 | 实际透明proxy/stock加载、47目标表与SDK Crypto/两caller原窗一致；登录页正常、正常退出后恢复stock。只核零hook加载/绑定，未启用key/trust/route或认证。 | [真实加载](docs/CLIENT_72_CLEAN_LOADER.md#真实零hook加载观察器修正与恢复) |
| 认证日志与InstanceRoot前置（归D02-R2/R5） | 已完成 | 三个认证类日志修复和数据隔离回归、独立运行目录入口已验；DefaultAuthenticator遗留日志已按R2-LOG修复，真实部署未验。 | [日志](docs/CLIENT_72_AUTH_LOGGING.md)、[隔离入口](docs/CLIENT_72_PORTABLE_ISOLATION.md) |
| R2-CONFIGSYNC-EVENT | 已完成 | 固定producer、原ctor/OS转换、dispatcher及name负例至local_config writer入口已验；自然caller/writer返回未验。 | [原始证据与模型](docs/CLIENT_72_SDK_CONFIGSYNC_EVENT.md) |
| R2-CONFIGSYNC-OBSERVE-owned | 已完成 | 独立DLL构建与owned ABI、实际搬移/恢复及输入守卫已验；自然目标事件另验。 | [实际构建与限定验收](docs/CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md#主线实际构建与自有验证) |
| R2-CONFIGSYNC-NATURAL-TRIGGER | 已完成 | 注册、once、插件槽、literal及条件gate静态来源已验；自然选择、执行时序及本服route未验。 | [准确边界/后继](docs/CLIENT_72_SDK_CONFIGSYNC_NATURAL_TRIGGER.md) |
| R2-CONFIGSYNC-ACCOUNT-CALLBACK | 已完成 | 两个账号回调与单例fallback的准确静态先后关系已验；条件路径不等于自然执行，完整callback/route未验。 | [准确范围及下一接口](docs/CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md) |
| R2-CONFIGSYNC-FALLBACK-ENTRY | 已完成 | fallback至40416具名视图入口的静态分派已验；自然业务与完整注册来源未验。 | [原始边与下一输入](docs/CLIENT_72_SDK_CONFIGSYNC_FALLBACK_ENTRY.md) |
| R2-CONFIGSYNC-SHOWLOGINVIEW | 已完成 | ShowLoginView至准确插件UI委托分派的类型/调用边已验；实际注册producer由未完成父项继续。 | [准确类型/来源/首缺](docs/CLIENT_72_SDK_CONFIGSYNC_LOGIN_VIEW.md) |
| R2-CONFIGSYNC-SCALAR-OBSERVE | 已完成 | 新epoch有来源的四次1B自然gate输入已取并核保护；仅标量事实，不代验条件后继。 | [来源/实测/未知](docs/CLIENT_72_SDK_CONFIGSYNC_SCALAR_OBSERVATION.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE-owned | 已完成 | 新单EXE桥的来源、独立构建、导出、自有ABI及完整搬移/恢复已验；不代验自然Marshal/SDK返回或分类成功。 | [实际构建、ABI与边界](docs/CLIENT_72_SDK_CONFIGSYNC_EARLY_BRIDGE.md) |
| R2-CONFIGSYNC-EARLY-LIVE-ADAPTER | 已完成 | 新只读adapter的owned正反和真实文件门禁已验；自然事件结果由EARLY-BRIDGE父项维护。 | [实际范围与现场未知](docs/CLIENT_72_SDK_CONFIGSYNC_EARLY_BRIDGE.md#新现场adapter的owned与真实文件门禁) |
| R2-CONFIGSYNC-EARLY-BRIDGE（离线超限归因步骤） | 已完成 | live/accepted/源/构建/实际artifact同epoch及caller/两managed String/Marshal表示已核；旧5只来自payload的两个限额出口，18个owned Python模型/13检查及主线第三同双fresh。自然具体出口、原Marshal执行、分类/Init/route仍未验。 | [归因、边界和独立split规格](docs/CLIENT_72_SDK_EARLY_CLASSIFICATION_AUDIT.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE（独立11/12产物及现场准备步骤） | 已完成 | 最小分流保持旧源和限额；新DLL/EXE实际构建、两fresh各23个真实owned、30项检查、accepted绑定及真实隔离文件门禁通过；主线新CheckOnly与独立审核通过。未安装/启动本轮客户端，实时ready和自然终态仍待取得。 | [实际产物、预检及未验边界](docs/CLIENT_72_SDK_EARLY_SPLIT_OBSERVER.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE（split自然预算出口区分步骤） | 已完成 | 新实时确认下准确caller终态11，45监测/9同源读取、完整代码绑定及纯重放经16项主线审核；用户正常页/正常退出，stock和保护恢复保持。只区分本epoch预算出口，不验JSON分类/Marshal/Init/route或父项；旧5仍未知具体出口。 | [实际终态、恢复及未验范围](docs/CLIENT_72_SDK_EARLY_SPLIT_OBSERVER.md#新实时确认的实际终态11与恢复) |
| R2-CONFIGSYNC-EARLY-BRIDGE（language输入与前缀后继规格步骤） | 已完成 | 两个准确完整原窗、695字段模型及24项来源投影、13项前缀正反、12测试和主线三fresh/13审核通过；language结构足以使自有合法候选超预算。仅离线来源/规格，不确认自然language位置、完整serializer、NUL/Marshal/Init/route。 | [来源、实施合同及新实验边界](docs/CLIENT_72_SDK_SCAN_LIMIT_FOLLOWUP.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE（独立前缀产物与文件门禁步骤） | 已完成 | 原读取限额内借用已有副本，独立DLL/fixture实际release构建、两fresh各54例ABI、51EAT/72B DATA/完整284B、六源/86pin及主线14审核通过；新adapter四SHA绑定、33检查和真实隔离布局文件门禁通过。未安装GAME_PATH或取得新自然13/11，不代验完整payload/Marshal/Init/route。 | [实际构建、ABI及门禁](docs/CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md#主线实际构建abi及文件门禁验收) |
| R2-CONFIGSYNC-EARLY-BRIDGE（自然必要前缀13观察步骤） | 已完成 | 新ready下90秒/45monitor、9读1450B、完整原代码绑定及16项主线纯重放通过；自然cn-env-0/两reference等值已取，用户正常页/退出及stock/保护恢复通过。仅前缀，不代验完整payload/NUL、原Marshal/SDK writer/Init/route/认证。 | [实际事件、恢复及准确未验范围](docs/CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md#新epoch自然13正常退出与stock恢复) |
| R2-CONFIGSYNC-EARLY-BRIDGE（writer窗口与整轮后继观察规格步骤） | 已完成 | 原5422B完整writer、21项静态投影与12项预算纯模型、主线8项审核通过；首次任意P0/P2唯一尝试，参数16读/37B、绑定17读/6409B、外采成功30读/10415B和拒绝1读/256B分别固定。仅规格；八点观察器未实现/构建/实机，unwind、自然Marshal/writer/Init/route及父项未验。 | [来源、修正、主线验收及未实施边界](docs/CLIENT_72_SDK_PREFIX_NATURAL_SUCCESSOR.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE、R2-ROUTE-CONFIG-SELECT（八点实施审核步骤） | 已完成 | 26项冻结材料审核与主线第三同字节，明确P0实际14B→C3、新loader窗口分块、首次尝试、writer-open、真实mask及三账本；旧原文/报告保持。已交Sol xhigh实施，实际产物/owned/collector/自然执行、unwind和业务均待验。 | [实施合同、覆盖纠正与出口](docs/CLIENT_72_D02_B_SDK_AUDIT_20261110.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE、R2-ROUTE-CONFIG-SELECT（八点观察器离线产物步骤） | 已完成 | 33源/105pin、独立DLL/fixture构建、规定两fresh owned及主线第三、16个near/far实际emitter、17测试、51导出/256B DATA及真实隔离文件门禁通过；新helper实际CheckOnly匹配保护/输入且进程端口空。自然现场尚待新ready，unwind/完整Marshal/writer/Init/route及业务不由owned通过代验。 | [产物SHA、accepted、复现与现场出口](docs/CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE、R2-ROUTE-CONFIG-SELECT（八点自然阶段观察步骤） | 已完成 | 新ready的90秒/45sample、外采30读10415B/512及15项原字节审核通过，完整代码/8trampoline/转发绑定、256B状态前后相同；同配置parser/env写入与原生返回成立，用户正常页/退出，stock/保护恢复。P5/P6仅分支，完整payload、Init/route/认证、unwind及D-02不晋级。 | [实际自然证据及边界](docs/CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md#本轮自然阶段观察与恢复) |
| R2-CONFIG-ROUTE-WRITE（阶段语义与五点原锁后继规格步骤） | 已完成 | 本轮原DATA独立语义/原320B窗口+pdata及38项冻结投影、主线第三通过；明确stored DWORD0不等于managed env选择，P5/P6仅分支。首次M0/同writer帧/P7关闭、只读map分类/新三预算及ABI反例已冻结。定向公开heads唯一main同旧commit，有限范围无新增量；未实现/构建/实机新observer，不关闭route业务。 | [自然语义和明确实施合同](docs/CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md) |
| R2-CONFIG-ROUTE-WRITE（五点自然锁内map观察步骤） | 已完成 | 新实时ready90秒/45样本，25读9262B和16项原件审核通过；DATA首末同256B、facts1023、同writer帧/代码/返回绑定，comboLogin非空=固定cn-route-env-0；用户正常页/退出、stock/保护恢复。非managed env/实际route采用/auth，不关闭R2或D-02。 | [实际证据/恢复与准确分界](docs/CLIENT_72_SDK_CONFIG_ROUTE_OBSERVER.md#本轮自然锁内map观察与恢复) |
| R2-ROUTE-CONFIG-SELECT（托管必要消费者静态步骤） | 已完成 | 新核原AOT/真实328B和caller、单字节? literal；89项冻结审计/主线第三20425B一致。40108读托管URLConfig.comboLogin+?→PostRequest33107，不读SDK native map；当前state/Init/env/真正请求采用及GC/ABI/EH仍未验。 | [准确调用、来源与后继](docs/CLIENT_72_SDK_ROUTE_CONSUMER_SUCCESSOR_20261110.md) |
| R2-ROUTE-MANAGED-STOCK-SPEC（归R2-ROUTE-CONFIG-SELECT） | 已完成 | 21pin/68项来源审计与主线独立第三13884B一致；真实typed/String布局和13项固定读图、unknown统一脱敏、A/B独立final与98/2304合计上界已冻结。仅stock当前值输入规格，不证明SetEnv/GetURL/请求采用、GC所有权或认证。 | [已审规格、隐私及预算](docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_SPEC_20261110.md) |
| AB-STOCK-CURRENT-INPUTS（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 有界B reader/adapter、新协调器和门禁离线实现；两fresh及主线各111人工项通过，第三98读2050B，source/晚失败脱敏和一次cleanup独立审核通过。旧A18源/四profile字节保持；总cap98/2304，尚无实际CheckOnly/自然输入/目标CPU或副本写权限。 | [实现、主审及现场前置](docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_COLLECTOR_20261110.md) |
| AB-STOCK-PREPARATION（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 主线63源并集seal、实际CheckOnly及8项独立审核通过，五stock/载体/13保护/portable14/缺席与进程固定监听空；该准备不代验现场；之后本轮实际0RPM失败见299后继。新轮必须新实时确认，A/B各自身份/final/敏感seal保持。 | [实现与门禁入口](docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_COLLECTOR_20261110.md) |
| AB-STOCK-STARTUP-299（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 首次模块聚合错误299/空items、A/B0RPM、自有句柄各关闭一次及用户退出后stock/保护/进程监听复核均已核；184项静态/主线第三通过。仅关闭失败归因边界及最窄v2规格，Create/First子阶段、唯一根因和页面表现未知；独立v2及后续实际采集另项已验，本失败不代验自然输入，不能重用本轮ready。 | [原件、API证据与v2严格边界](docs/CLIENT_72_D02_STOCK_STARTUP_299_20261110.md) |
| AB-STOCK-STARTUP-299-V2（离线实现及实际准备步骤） | 已完成 | 最小新flow仅pre-first299空结果及窗口末None拒绝，原helpers/A4/B2字节保持；两fresh及主线各136人工项、四PS Parser及主线第三98读2078B通过。83源seal/真实CheckOnly与8项独立门禁核对通过，尚不代验自然输入或业务。 | [新实现、审核与门禁](docs/CLIENT_72_D02_AB_STOCK_V2_20261110.md) |
| AB-STOCK-V2-CAPTURE（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 新ready45次/90秒，实际A62/1388+B26/432=88读1820B；9项原件重构/ABI/身份/末核/83源快照审核通过，用户无异常退出且stock/保护/进程监听核验通过。32B head/HashSet身份与托管当前env/base0仅为候选；未执行native或证明GC/自然caller/route采用/认证。 | [同一v2合同](docs/CLIENT_72_D02_AB_STOCK_V2_20261110.md) |
| R2-CONFIG-ROUTE-WRITE（五点独立产物准入步骤） | 已完成 | 36传递源/147冻结pin、独立DLL/EXE构建、两fresh owned及第三、10 near/far实际emitter/真实mini帧、24 Python、51导出/256B DATA及真实隔离文件门禁通过；新helper实际CheckOnly通过。新实时ready/自然map仍待，完整writer/真实SDK锁/route采用/auth/EH不由owned代验。 | [实际产物/accepted/准备与边界](docs/CLIENT_72_SDK_CONFIG_ROUTE_OBSERVER.md) |
| F04-LOADER-CLEAN 独立透明入口 | 已完成 | 零hook独立透明入口、实际stock/SDK绑定、正常登录页/退出恢复已验；key/trust/route自然链和完整校验另验。 | [计划/产物/真实范围](docs/CLIENT_72_CLEAN_LOADER.md) |
| R2-KEY-LOAD-OBSERVE 固定只读观察 | 已完成 | 窄key入口实际安装、精确代码/转发/回调绑定、正常页及退出恢复已验；未证明自然key调用、route或完整校验。 | [实际结果、来源与边界](docs/CLIENT_72_KEY_LOADER_OBSERVATION.md) |
| R2-ROUTE-CONFIG-SELECT/B-REQUEST-ADOPTION（静态选择子出口） | 已完成 | 唯一LoadLocalConfig2481B/504条、三caller、readOnly仅跳local回写与cp_app_id匹配client.gameKey、两次加载和返回边界已核。来源23/最终31断言及主线23复核通过；自然选择/实际采用未验。 | [精确选择与请求入口规格](docs/CLIENT_72_D02_ROUTE_ADOPTION_20261110.md) |
| R2-ROUTE-CONFIG-SELECT/FINAL-URL-CLASSIFIER | 已完成 | 八个完整base各加单?的纯UTF16LE分类器及七直接源门禁，18测试/独立8正例1090扰动/7漂移主线复核通过；只输出固定类/unknown。未观察33107/HTTP或认证，不解锁hook。 | [实现、来源与边界](docs/CLIENT_72_SDK_FINAL_ROUTE_CLASSIFIER_20261110B.md) |
| D02-GITHUB-INCREMENT-AB（有界补查） | 已完成 | Sol max中文计划→Terra max检索，8输入/16原件主审通过；bounded partial/no-new-evidence，未发现可直接集成实现。B401/词条重复及A分支覆盖不全保留，历史B派工计划按原SHA恢复快照，不改旧pin。 | [范围、来源与限制](docs/CLIENT_72_D02_GITHUB_INCREMENT_20261110B.md) |
| F-03d3s2/SCENE-GITHUB-INCREMENT（有界补查） | 已完成 | README优先，两个固定World JSON均有MPLevel且与本地三者两两不同；主线第三同SHA，三层tree/两blob复核。12请求上限及响应丢失/分页缺口保留，target分组/自然命中/生命周期仍未知，无集成许可。 | [候选样本与准确未闭](docs/CLIENT_72_D02_SCENE_GITHUB_INCREMENT_20261111.md) |


### 6.4 资源、脚本、保存与回归

| 任务ID / 成果 | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| E-01/R3-ITEM-AFFIX-FRAGMENT | 已完成 | 11101真实有序[0,0]经原数组AOT/解码至具名EquipAffix getter；独立11501非零/零对照通过。三新进程同字节、17检查/11审核/91pin；完整Item/gadget/正引用未验。 | [真实数组与来源](docs/CLIENT_72_D02_ITEM_ENTRY_RESOURCE.md) |
| E-01/R3-JAVA-ITEM-AFFIX-FRAGMENT | 已完成 | 真实ResourceLoader/onLoad→Item map→GameItem身份/count/level/affixes及受控对照3项通过；源[0,0]/NONE保持，map恢复、loadedAll不变。完整Item/实体/保存未验。 | [Java真实加载与构造](docs/CLIENT_72_D02_ITEM_AFFIX_JAVA_LOAD.md) |
| E-01/R3-JAVA-AVATAR-FRAGMENT | 已完成 | 100Curve/两出生HP/grow片段真实ResourceLoader加载，18个native float32结果一致；4项及32隔离回归通过，缺binding/curve真实拒绝，loadedAll保持。本项仅HP/grow，critical后继另已验；完整行/能力/自然加载与保存未验。 | [真实Java加载](docs/CLIENT_72_D02_AVATAR_JAVA_LOAD.md) |
| E-01/R3-AVATAR-ENTRY | 已完成 | Avatar.hpBase/grow binding通过真实选中行→原缓存查询片段→完整helper闭合；主线第三与双跑同原字节、18项和99源pin已核。本项不覆盖critical，后继许可另列；完整Avatar/Item/能力/自然加载/revision未验。 | [属性与成长](docs/CLIENT_72_D02_AVATAR_ENTRY_RESOURCE.md) |
| E-01/R3-UPSTREAM-D02、R2-PATCH-DELTA | 已完成 | 两固定远端/35个原文件及必要行、消费者、许可和差异分类已审；未有可直接解除首缺的合入增量。未知省略/Quest/Scene不造alias，WinHTTP缺覆盖暂缓；不代验父项。 | [定向审计](docs/CLIENT_72_D02_UPSTREAM_REVIEW.md) |
| E-01/R3-JAVA-WEAPON-FRAGMENT | 已完成 | 准确原生派生11101两属性、100曲线、零阶原列表经真实 Gson/onLoad，4项Java通过；NONE/0保留与上游缺省过滤分列。完整Item/loadAll/revision/保存未验。 | [Java片段加载](docs/CLIENT_72_D02_WEAPON_JAVA_LOAD.md) |
| E-01a、E-01b | 已完成 | 固定候选七表、scene3依赖及引用审计；不代表整套资源已安装/可用。 | [候选](docs/CLIENT_72_RESOURCE_CANDIDATE.md)、[scene3](docs/CLIENT_72_SCENE_RESOURCES.md) |
| E-01c2、E-01c3、E-01c4、E-01c5、E-01c6 | 已完成 | Lua序列化修复、API来源、owner UID、生产加载审计及嵌套require恢复；local作用域/必要接口/完整加载仍开放。 | [序列化](docs/CLIENT_72_SCENE_SERIALIZATION_FIXES.md)、[API](docs/CLIENT_72_SCENE_SCRIPT_API.md)、[生产加载](docs/CLIENT_72_PRODUCTION_SCENE_LOAD.md)、[require](docs/CLIENT_72_REQUIRE_BINDINGS_FIX.md) |
| E-01d1a、E-01d1b、E-01d1c、E-01d1d、E-01d1e、E-01d1f、E-01d1g、E-01d1h、E-01d1i、E-01d1j、E-01d1k、E-01d1l | 已完成 | 受控角色/depot、出生网格、能力、实际Lua事件、日常保存和初始实体/自动回调；只验对应suite/路径，不代替所有异步后代或revision。 | [最小资源各子合同](docs/CLIENT_72_D02_MINIMUM_RESOURCES.md) |
| E-01d1m、E-01d1n1、E-01d1n2、E-01d1n3 | 已完成 | Quest台账、目标303拒绝/Fallback、启动/冷读和未就绪门禁、Chapter/通知及反射加载清单；正向manager恢复未验。 | [任务台账](docs/CLIENT_72_D02_QUEST_ENTRY.md)、[恢复](docs/CLIENT_72_D02_QUEST_303_RECOVERY.md)、[保存](docs/CLIENT_72_D02_QUEST_303_START.md)、[Chapter](docs/CLIENT_72_D02_CHAPTER_LOAD.md) |
| E-01/E-01c/E-01d1（三文件自然loadAll首诊步骤） | 已完成 | 一个新JVM真实调用并自然返回；实际路径、全部拒绝、Chapter436/Quest63、10WARN/326ERROR、temp唯一变化及原数据保持经14项主线审核。只验固定部分输入，不验resourceReady、正向Manager或完整资源兼容。 | [实际首诊与复现](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_RUN.md) |
| E-01/E-01c/E-01d1（自然303局部保存与正向Manager恢复步骤） | 已完成 | 同一新JVM自然加载后，真实Morphia/save/新Player/QuestManager将63项全部绑定真实定义，状态/进度/归属保持；未就绪0写且BSON相同，正向队列精确两写、原始类型/字节差异经独立复核，Mongo正常停库。仅局部303，不验全局resourceReady、入场事件/场景或客户端重登。 | [实际执行、BSON边界及24项审核](docs/CLIENT_72_D02_QUEST_303_NATURAL_REBIND.md) |
| 资源深度parser/条件子合同（归E-01/R3） | 已完成 | depot/条件parser、LHFO嵌套及GameTime/LuaNotify分界，Ornament标量/条件数组；未知JSON映射和接受规则未补造。 | [深度资源](docs/CLIENT_72_RESOURCE_DEEP_RESEARCH.md)、[Ornament](docs/CLIENT_72_AVATAR_ORNAMENT_CONTRACT.md)、[条件数组](docs/CLIENT_72_AVATAR_ORNAMENT_CONDITIONS.md) |
| 实际资源来源/容器/索引/locator链（归E-01/R3） | 已完成 | 候选/真实来源、provider、索引reader、blob头、完整payload/index与精确表字节取得；不再等待同一已解决解包问题。 | [来源](docs/CLIENT_72_D02_RESOURCE_SOURCES.md)、[provider](docs/CLIENT_72_RESOURCE_PROVIDER.md)、[索引入口](docs/CLIENT_72_RESOURCE_INDEX.md)、[native reader](docs/CLIENT_72_RESOURCE_INDEX_NATIVE.md)、[blob](docs/CLIENT_72_RESOURCE_BLOB.md)、[payload](docs/CLIENT_72_RESOURCE_PAYLOAD.md)、[完整索引](docs/CLIENT_72_RESOURCE_PAYLOAD_INDEX.md)、[locator](docs/CLIENT_72_RESOURCE_LOCATOR.md) |
| E-01-bridge-energy（归R3） | 已完成 | Avatar/Depot/Skill七字段通过同缓存原生角色索引/能量槽/元素消费者闭合，正引用/缺省/枚举和片段转换已验；完整资源行/加载/revision/保存恢复仍未验。 | [字段桥梁](docs/CLIENT_72_D02_RESOURCE_BRIDGE.md) |
| E-01-bridge-equipment（归R3） | 已完成 | 真实三表完整解析、初始武器/两项属性/同源曲线及突破ID消费者闭合；非零/64位/float32及缺引用/未知枚举拒绝已验。仍为字段片段，完整Java行/突破加成/加载/revision未验。 | [装备字段桥梁](docs/CLIENT_72_D02_EQUIPMENT_RESOURCE.md) |
| R1-SAMPLE-INTERFACES（归R1/Scene） | 已完成 | 新ready原始启动后准确取得1169/1249头及metadata、双零compact与loader TLS索引；预算、五输入及正常退出已验。线程TLS、消费时序及完整构造未验。 | [输入合同与实测](docs/CLIENT_72_INTERFACE_CONTEXT_SNAPSHOT.md) |
| R2-COMBO-HTTP（归R2-ROUTE） | 已完成 | 准确原生builder/sign字节→真实Java HTTP/default认证/Mongo同步保存→原生parser/formatter六正反例及关联测试已验；64位归属和失败BSON保持。全UI/callback/自然SDK/route未验。 | [真实接收与主线证明](docs/CLIENT_72_SDK_COMBO_HTTP.md) |
| R2-KEY-LOAD-owned（归R2-KEY/F-04） | 已完成 | 独立47转发/key入口、准确SDK三窗/材料守卫、release构建/精确EAT49及主线owned正反例通过；冻结core/clean/依赖对应同一来源。本子项只验owned，实际安装见OBS；自然caller/完整校验/route未验。 | [构建及主线第三](docs/CLIENT_72_SDK_KEY_LOADER.md) |
| F-03d3f4d2-fresh-interfaces（归R1） | 已完成 | 原前缀保持、第三类壳/接口写回及方法64B存储已验；两case/不可变index投影和准确method-cache首缺有来源。完整初始化/default/Handler未完成，最小roots合同不等于采样ready。 | [主线第三与边界](docs/CLIENT_72_AVATAR_FRESH_INTERFACES.md) |
| E-01-PROMOTE-ENTRY（归R3） | 已完成 | 三真实表完整解析及出生用途2个Promote/100个AvatarCurve输入、Avatar批准字段片段、完整Weapon属性6例/非零突破已验；宿主白名单同集合修复验证。完整Avatar/Item、加载/revision/保存恢复未验。 | [主线第三与准确用途](docs/CLIENT_72_D02_PROMOTE_RESOURCE.md) |
| E-01/E-01c/E-01d1（出生Promote真实Java加载步骤） | 已完成 | 固定第三报告→两个出生Promote真实ResourceLoader/onLoad与复合键通过，保留原零槽后由onLoad过滤；同上下文复验100Curve。四Python/两JUnit及保护/class-init检查通过；缺曲线由投影入口拒绝，loader不自行保证。非完整Avatar/Item/资源就绪、manager保存或D-02验收。 | [实际加载、拒绝及限制](docs/CLIENT_72_D02_C_RESOURCE_LOAD_20261110.md) |
| E-01/R3-AVATAR-ITEM-ENTRY（实际必读字段分层与桥接规格步骤） | 已完成 | 排重既有目标/Java成果，按出生/旧号恢复/SceneTeam分层必要字段；已批七字段真实绑定后继交Terra及主线执行。该步最初的talents+28缺口已由后续原typed职责和真实Java子项闭合；proud的group/level及提升门槛仍未知，不用零值或Java默认批准；不是完整资源验收。 | [准确字段分层、可执行后继与未知](docs/CLIENT_72_D02_AVATAR_ITEM_GAPS_20261110.md) |
| E-01-DEPOT-PROUD-BOUNDED（归E-01/R3-ITEM-ENTRY） | 已完成 | 20pin/三完整原窗与主线第三20977B同字节；Depot+18/81127+14→ProudSkillInherent→typed pair80067查询/output U32成立。另一个304B候选没有threshold消费；pair业务身份、81127+10与Avatar promote比较和Java算式等价未闭，不批准proud投影或完整资源。 | [有界结论、排除项及准确缺输入](docs/CLIENT_72_D02_DEPOT_PROUD_SUCCESSOR_20261110.md) |
| E-01d1/R3-AVATAR-LOCAL-SAVE | 已完成 | 真实公开Crypto/Account/GameSession/Player入口、两出生AvatarStorage保存→全新Player冷绑定、缺定义/不同UID三项JDK17/Mongo通过；主线11审核、12原BSON和既有更新/无重复创建核实，owned Mongo正常关闭保留库。仅未装备AvatarStorage，不代验正向Inventory、完整资源/Player、场景或重登。 | [实际运行、恢复与范围](docs/CLIENT_72_D02_AVATAR_LOCAL_SAVE_JAVA_20261110.md) |
| E-01d1/R3-EQUIPPED-LOCAL-RESTORE | 已完成 | 两出生各自自然默认装备、受控Player历史/保存及各两次新Player的AvatarStorage→Inventory/postLoad冷绑定，缺11101的真实NPE/BSON保留及同源恢复后原Item重绑，三JUnit和13项原件审核通过。27份BSON核原ID/owner/HP与装备关系、五次Item Guard推进，无重复发放/冷Player写；owned Mongo正常关闭保留库。仅无World局部恢复，不代验完整资源/Player、Entity/场景或客户端重登。 | [实际运行、两次准备纠正与边界](docs/CLIENT_72_D02_EQUIPPED_LOCAL_RESTORE_20261110.md) |
| E-01d1/R3-OLD-SAVE-DEPENDENCIES（离线driver子出口） | 已完成 | 5项Python及主线54输入/九组BSON与Extended JSON投影、独立引用/CLI/hash拒绝通过；inactive Item不产生装备依赖。用户已选定旧号，真实一致快照、额外资源和完整恢复仍未验，不代关R3。 | [源码合同、实际审核与范围](docs/CLIENT_72_D02_OLD_SAVE_DEPENDENCIES_20261110.md) |
| E-01/R3-AVATAR-ITEM-ENTRY（七字段真实Java绑定步骤） | 已完成 | 固定三报告/9片段→真实Skill/Depot/Curve/Avatar loader，四JUnit通过；出生同对象绑定与704→10068/Wind、缺引用/错误顺序的真实null/None行为均已实测。map/fetters/loadedAll及保护保持；704普通引用未齐，完整Avatar/能力/revision/保存/g2未验。 | [实际四项、隔离与未验范围](docs/CLIENT_72_D02_C_BRIDGE_JAVA_20261110.md) |
| E-01/R3-ITEM-ENTRY（出生武器真实库存保存步骤） | 已完成 | 同源六键Item11101经真实loader/GameItem普通保存队列/Guard→Mongo→新Player空Avatar/Inventory恢复，三JUnit及12项原件审核通过。两实例及cold Guard重存revision2、缺定义原BSON保持并恢复、UID隔离及冷读零写成立；仅未装备武器，不验完整Avatar/Player/场景/客户端。 | [实际运行、隔离恢复及范围](docs/CLIENT_72_D02_ITEM_SAVE_JAVA_20261110.md) |
| Java资源目的合同（归E-01/R3） | 已完成 | 加载类/字段/准确消费者及缺Weapon失败位置已审；不是target→Java转换许可。 | [Java目的](docs/CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md) |
| V-01a、V-01b、V-01c、V-01d | 已完成 | 能力便携路径、家具保存/加工手册夹具及家具资源路径修复；完整V-01保持未完成。 | [账号保存验证](docs/ACCOUNT_AND_SAVE_OPERATIONS.md)、[离线收口](docs/CLIENT_72_SOL_TERRA_OFFLINE.md) |
| E-01/R3-WEAPON-GADGET-BRIDGE | 已完成 | 出生武器gadgetId经原具名查询绑定50011101，交换/高位/缺缓存/空对象边界已验；完整factory、配置和生命周期未验。 | [实际来源/片段/边界](docs/CLIENT_72_D02_WEAPON_GADGET_BRIDGE.md) |
| E-01/R3-JAVA-GADGET-FRAGMENT | 已完成 | 同源片段经真实ResourceLoader/onLoad与GameItem绑定，缺键零值对照、map恢复和loadedAll保持已验；未加载Gadget配置。 | [实际加载与后继](docs/CLIENT_72_D02_WEAPON_GADGET_JAVA_LOAD.md) |
| E-01/R3-ITEM-ENTRY（Gadget/ability完整候选原文审计步骤） | 已完成 | 两份完整固定原文、27pin、commit/tree/blob、7精确Java字段/全部未知叶、三fresh同字节及12检查通过；选定来源已仓库外无checkout克隆。名称闭包不代验目标内容、未知对象可省略或revision。 | [准确来源、字段损失及主线审核](docs/CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md) |
| E-01/R3-ITEM-ENTRY（局部Gadget配置与ability真实Java加载步骤） | 已完成 | 独立配置/CWD下真实两loader及实际map的三JUnit通过、loadedAll不变；字段损失/未知对象与缺键负向保留，主线13审核及数据/进程/端口保护通过。未执行EntityWeapon/ability生命周期、loadAll、目标客户端或保存，不升父项。 | [实际Java加载、XML及未验范围](docs/CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md#主线第三审核外部克隆及实际java加载) |
| E-01/R3-ITEM-ENTRY（Gadget/ability未知对象与parser静态收敛步骤） | 已完成 | 原名称注册、196497字典parser及196498选择/工厂/动态分派的准确来源与有限静态表达式已审；双/三fresh、分阶段19/10/8个pin和主线定向审核保持。reader/原asset/实际类与字段语义未闭，不批准LKLP/defense/common alias；514570后继未批准或提取，不升父项。 | [静态结果、主线审核及停止边界](docs/CLIENT_72_GADGET_ABILITY_SEMANTICS.md) |
| E-01/R3-ITEM-ENTRY（D-02必要性与Metal局部Java生命周期步骤） | 已完成 | 31源必要性矩阵及两项真实loader→EntityWeapon→Ability初始化/root局部生命周期通过，Gradle/XML与15项主线审核通过；baseline sync0/owned参数sync1、null定义槽负向、executor0及保护/进程端口核验保持。不代验目标字段/空ability许可、普通入场完整sender、保存、客户端或父项。 | [实际测试、完整证据与未验边界](docs/CLIENT_72_D02_GADGET_NECESSITY.md#主线实际java执行与验收) |
| E-01/R3-GADGET-CONFIG-KEY | 已完成 | 真实+58键至原查询/hash前缀及交换/+50对照/空边界已验；本项止String Default前。jsonName职责另由后继批准，配置实际命中仍未验。 | [真实路由、模型和最小后继](docs/CLIENT_72_D02_GADGET_CONFIG_KEY.md) |
| E-01/R3-GADGET-JSONNAME-LOOKUP-KEY | 已完成 | typed实体配置域与Java key消费者对应，仅批准出生{id,jsonName}；真实loader/getJsonName、缺省null、map恢复/loadedAll保持已验。旧false报告冻结，配置内容/命中/ability未验。 | [新许可/实际加载/边界](docs/CLIENT_72_D02_GADGET_LOOKUP_KEY_JAVA_LOAD.md) |
| E-01/R3-CRITICAL-SEMANTICS | 已完成 | 共享基础字段经原installer及typed属性20/22确认为critical/criticalHurt基础贡献；+78、自然Avatar/modifier及完整行未验。 | [原静态范围](docs/CLIENT_72_D02_AVATAR_CRITICAL_RESOURCE.md)、[实际许可](docs/CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md) |
| E-01/R3-CRITICAL-INSTALLER | 已完成 | 两出生真实行、独立输入、交换/零/缺接收者通过原installer/dispatcher/getter；自然构造和完整父项未验。 | [实际范围/模型/原生许可](docs/CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md) |
| E-01/R3-JAVA-CRITICAL-FRAGMENT | 已完成 | 同源critical/criticalHurt与HP/grow片段经真实loader/getter保留float32，交换/缺键对照及状态恢复已验；完整行/能力/保存未验。 | [实际Java加载及未验边界](docs/CLIENT_72_D02_AVATAR_CRITICAL_JAVA_LOAD.md) |
| E-01/R3-STRING-COMPARER-INPUT | 已完成 | String类型/继承/伴随槽、ABI与最小采集来源合同已验；未取得自然Default/Equals，两个后备未执行。 | [实际合同、继承/伴随槽与未验边界](docs/CLIENT_72_GADGET_STRING_COMPARER_INPUT.md) |
| E-01/R3-ITEM-ENTRY；F-03d3f4d2（String独立采集器实现步骤） | 已完成 | 冻结Dictionary carrier→实际Default/Equals的独立54读2704B collector及stock epoch adapter通过17测试、三同字节owned和16主线审核/当前来源门禁。只验人工输入及工具，未采自然图、未执行比较器/配置/ability或完成父项。 | [实现、主线证据及自然输入缺口](docs/CLIENT_72_STRING_CONSUMER_COLLECTOR.md) |
| E-01/R3-ITEM-ENTRY；F-03d3f4d2（String自然carrier诊断步骤） | 已完成 | 新实时stock采集取得真实Dictionary泛型owner、FindEntry零/getter非零、37项definition来源与精确末尾复读；15项主线纯重放审核及正常退出保护通过。v2按门禁partial，getter对象/完整String图/消费者仍未验，不晋级父项。 | [实际输入、停止及未知](docs/CLIENT_72_STRING_CONSUMER_COLLECTOR.md#新stock实测的lazy诊断与非零getter) |
| E-01/R3-NATURAL-LOAD-FIRST-AUDIT | 已完成 | 真实loadAll直接入口、路径/副作用、Lua执行域和独立JVM首诊规格已审；未选定首诊输入、未建driver或运行loadAll，不新增逐项审批/全资源P0要求。 | [准确结论与主线收尾](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_AUDIT.md) |
| E-01d1/R3-OLD-ACCOUNT-SNAPSHOT | 已完成 | 用户所选旧号在原物理证据副本的第二工作副本恢复并只读导出；唯一账号/Player及owner/claim关联通过，142源文件在当时前后保持，owned27027正常关闭。实际121角色/12254物品/471装备，原库/配置未写；仅快照，不验业务完整恢复或客户端。 | [复制、恢复、实际关系与边界](docs/CLIENT_72_D02_OLD_ACCOUNT_SNAPSHOT_20261110B.md) |
| E-01d1/R3-OLD-AVATAR-COVERAGE | 已完成 | 固定旧号121 Avatar/121当前Depot/409次389不同保存Skill均命中目标键/报告span；5测试及主线第四与三fresh同SHA通过。762 Talent/410 Proud、实际phase6资源/完整字段及恢复未闭；四活动不代全部121。 | [键对照与准确后继](docs/CLIENT_72_D02_OLD_AVATAR_COVERAGE_20261111.md) |
| E-01d1/R3-OLD-ITEM-COVERAGE | 已完成 | 活动10727/不同ID9128、inactive1527保留；目标Weapon交239ID/1663行，四固定候选表命中9120ID，8ID未知。6测试及主线第四/三fresh同SHA，候选tree/blob/SHA已核；不是目标类型/装备恢复许可。 | [有界四族对照与未知](docs/CLIENT_72_D02_OLD_ITEM_COVERAGE_20261111.md) |
| E-01/R3-OLD-ITEM-TPS-CANDIDATE | 已完成 | 源码ItemData明确第五TPS资源族，固定外部tree的唯一blob增量1次获取；8活动ID224001–224008各唯一候选命中。strict JSON/blob/全SHA及原不可变投影/四表报告主审通过，原报告不改。仅补候选存在性；目标ItemType/资源命中/字段/完整Inventory仍未验。 | [增量来源及验收](docs/CLIENT_72_D02_OLD_ITEM_COVERAGE_20261111.md#tps-candidate) |
| E-01/R3-OLD-PHASE6-PROUD（首缺与样本规格） | 已完成 | 全121 postLoad实际重建proud，四活动真实保存样本/四原payload跨度/16同窗opcode及20旧pin主线第三同SHA；不批准pair group/level、81127+10门槛或Java算式。 | [准确必经消费与未闭语义](docs/CLIENT_72_D02_OLD_PHASE6_PROUD_20261111.md) |
| E-01/R3-OLD-DEPOT-FIELDS（四行字段样本） | 已完成 | 原whole171行/26024B与mapping一致，四原query返回本次typed对象，97 getter到原RETURN；5测试与主线第三1121330B同SHA通过。保留170字符串/模型/34批准11执行区间及对象零空，input presence和业务alias仍未知，非全121恢复。 | [原执行、模型与字段边界](docs/CLIENT_72_D02_OLD_DEPOT_FIELDS_20261111.md) |
| E-01/R3-ALL-SAVED-DEPOT-REFS | 已完成 | 全121实际保存当前Depot原whole171/cursor26024、121 query/2924 getter RETURN，34批准/11实际区间及各span核对；14测试/主线第三9014403B与两fresh同SHA通过。726外层/嵌套原值和presence unknown保留，仅数据样本，不批准字段alias/全phase6资源或完整恢复。 | [原字段、数量与后继边界](docs/CLIENT_72_D02_ALL_SAVED_DEPOT_REFS_20261111.md) |
| E-01d1/R3-OLD-TALENT-REFS | 已完成 | 固定全121角色的 Java 当前 Depot Talent 查键，共726，活动24；36个非当前 Depot 保存引用保留。7项定向测试和第三CLI一致。目标行、OpenConfig和 revision 待验证。 | [引用与复现](docs/CLIENT_72_D02_OLD_TALENT_REFS_20261111.md) |
| E-01d1/R3-OLD-TRAVELER-PROMOTE | 已完成 | 已选旧号主角真实 group 12/phase 6 行通过独立 Java loader，四个非零属性位、三正材料和零 ID 过滤均通过。其余角色和实际属性/完整恢复待验。 | [来源、测试和范围](docs/CLIENT_72_D02_OLD_TRAVELER_PROMOTE_20261111.md) |
| E-01d1/R3-OLD-PROMOTE-GROUPS | 已完成 | 121 个真实保存角色均经原Avatar切片取得独立 group，phase6 唯一行与原跨度匹配，缺键0；10项和主线CLI一致。字段和真实加载另验。 | [原引用和复现](docs/CLIENT_72_D02_OLD_PROMOTE_GROUPS_20261111.md) |
| E-01/R3-PROUD-ACTUAL-FIELDS（producer与闭合身份） | 已完成 | 174B getter/3179B producer两fresh及原source核；同原row的+90/+84经codec组成pair并传同枚举行键，56 QWORD闭合map138526及HashSet15620 AddIfNotPresent身份。6测试/主线复验通过。map写入/真实Proud表/group-level/门槛及Java算式仍未知。 | [原关系、AOT与后继](docs/CLIENT_72_D02_PROUD_ACTUAL_FIELDS_20261111.md) |
| E-01/R3-PROUD-ROW-WINDOW（机械取窗） | 已完成 | 精确df70原caller/AOT两QWORD/唯一pdata后772B/152条连续body已取；5门禁与主线第三8原件和两fresh逐byte同SHA通过。21 direct call/25 RIP仅原指令，不批准资源来源/返回对象业务语义，另交Sol max。 | [原窗、绑定及引用](docs/CLIENT_72_D02_PROUD_ROW_WINDOW_20261111.md) |
| E-01/R3-PROUD-ROW-SOURCE | 已完成 | 原606B producer、唯一literal与typed63831/529236 parser链闭合；主线第三48799B与两fresh一致，6测试。两个U32仍未命名offset/size，真实行/codec/业务alias未验。 | [原行来源](docs/CLIENT_72_D02_PROUD_ROW_SOURCE_20261111.md) |
| E-01/R3-PROUD-PAYLOAD-LOCATOR | 已完成 | 复用固定index/sidecar/serialized输入，唯一Proud m_bytes 2132778B已提取；主线第三四原件与final3/4逐byte一致，5测试。host hash/pack定位不替代目标native命中、revision、行边界或resourceReady。 | [完整payload定位及限制](docs/CLIENT_72_D02_PROUD_PAYLOAD_LOCATOR_20261111.md) |
| E-01/R3-PROUD-PARSER-CONTRACT | 已完成 | 原parser/derived/base ctor三窗8412B/1956条及原child E9、+20/+40来源已核；主线第三91234B同两fresh，6测试。child未取/未执行、actual mode与factory产物和行span未闭，非完整parser成功。 | [表示来源和最小输入](docs/CLIENT_72_D02_PROUD_PARSER_CONTRACT_20261111.md) |
| E-01/R3-PROUD-MODE-FACTORY-SOURCE | 已完成 | 原63831 slot9 definition→529228/AOT已静态绑定，两mode原窗345B/90条、typed Int32集合/六dispatch及初始化输入合同已闭；6门禁/主线第三41737B同两fresh。自然mode/vtable/MethodInfo、factory/parser执行仍未验，不置+20/+40。 | [静态合同和下一输入](docs/CLIENT_72_D02_PROUD_MODE_FACTORY_20261111.md) |
| E-01/R3-PROUD-FACTORY-WINDOW | 已完成 | 准确529228/149f0bb40原114B/27条与原slot9间接caller、五hash/typed/AOT/唯一pdata已核；两fresh八件、主线第三12296B同SHA及7测试。仅机械原窗/两call六RIP，不证明factory产物或执行，交Sol max语义。 | [原窗与复核](docs/CLIENT_72_D02_PROUD_FACTORY_WINDOW_20261111.md) |
| E-01/R3-PROUD-INDEX-CTOR-WINDOW | 已完成 | 准确663526原221B/45条及AOT/pdata、两fresh八件核实；主线第三14772B同SHA/7测试。仅机械窗，不以.cctor存在代index实际填充。 | [原窗](docs/CLIENT_72_D02_PROUD_INDEX_CTOR_WINDOW_20261111.md) |
| E-01/R3-PROUD-INDEX-AOT | 已完成 | 18192实例5071声明筛得138527/ptr84537→157ffc4c0，实际1QWORD；同index共享唯一，跨未读slot同code未知。主线第三12430B同两fresh、5测试；返回类型/方法行为未验。 | [限定闭合身份](docs/CLIENT_72_D02_PROUD_INDEX_AOT_20261111.md) |
| E-01/R3-PROUD-FACTORY-INDEX-CONTRACT | 已完成 | factory请求41105并尾跳allocator；cctor仅直接写400190/400189/400193，不写400191/400192，不填index。主线第三24断言41969B同两fresh、6测试。原返回/loader/有效row及字段业务身份未闭。 | [准确来源和停止点](docs/CLIENT_72_D02_PROUD_FACTORY_INDEX_CONTRACT_20261111.md) |
| E-01/R3-PROUD-INDEX-LITERALS | 已完成 | 两原cell分别解PrimaryIndex/IndexGroupId，2×74B；主线第三2429B同audit、6测试，sourceIndex/slot分离。不推业务角色/填充或借旧MiHoYoBinData suffix求命中。 | [两literal身份](docs/CLIENT_72_D02_PROUD_INDEX_LITERALS_20261111.md) |
| E-01/R3-PROUD-LOOKUP-WINDOW | 已完成 | 157ffc4c0完整236B/71条、原caller/5071 AOT/pdata及双八原件已核；主线第三15186B同两fresh、6测试。仅机械窗口，首惰性loader/path边及行为待Sol max，不命名FindEntry/TryGetValue。 | [原消费者窗口](docs/CLIENT_72_D02_PROUD_LOOKUP_WINDOW_20261111.md) |
| E-01/R3-PROUD-LAZY-INDEX-CONTRACT | 已完成 | 主线第三12550B/34原PC断言同两fresh；先无条件call157ffc650再读壳+18，本236B不读壳+20。negative index清8B/AL0，nonnegative复制8B/AL1，整个RAX不是bool合同。loader/path/index实际填充仍未知。 | [值流及唯一下一source](docs/CLIENT_72_D02_PROUD_LAZY_INDEX_CONTRACT_20261111.md) |
| E-01/R3-PROUD-PREPARE-AOT-D | 已完成 | 已知5071的16准确slot/128B中唯一目标157ffc650绑定138529/spec175101/ptr84545；主线第三24456B同两audit、5测试。同index共享行仅自身，跨其他index同code未知，不推loader语义。 | [原槽及范围](docs/CLIENT_72_D02_PROUD_PREPARE_AOT_20261111.md) |
| E-01/R3-PROUD-PREPARE-WINDOW-W | 已完成 | 唯一pdata给289B/82条，两fresh八原件与主线第三14701B同SHA，6门禁通过；真实caller/5071 AOT/五hash已核。8call/14branch/2RIP仅机械，不推loader语义。 | [独立原窗专题](docs/CLIENT_72_D02_PROUD_PREPARE_WINDOW_20261111.md) |
| E-01/R3-PROUD-PREPARE-CONTRACT | 已完成 | 主线第三36303B同两fresh，185来源/82条重解码/58原PC通过；壳+20路径→原loader/type8026/suffix已闭合，+28分支按实际成功/空/handler结果分开。本289B不读写壳+18，实际填充/row仍未验。 | [必要loader链与单source规格](docs/CLIENT_72_D02_PROUD_PREPARE_CONTRACT_20261111.md) |
| E-01/R3-PROUD-PRIMARY-INDEX-LOCATOR | 已完成 | 两fresh及主线第三四件同byte/5测试；准确路径唯一record/variant/tag0/bundle178/location/serialized entry闭合，31858B原payload SHA f87adf6f…e8eb05。185旧源完整hash另账，无新PE/native hash或index解析。内容/row/自然加载/资源ready未验。 | [准确完整payload与范围](docs/CLIENT_72_D02_PROUD_PRIMARY_INDEX_LOCATOR_20261111.md) |
| E-01/R3-PROUD-INDEX-HANDLER-WINDOW | 已完成 | 主线第三29862B同两fresh八原件、6测试；原两E8/138530/5071/slot/唯一pdata绑定，735B/189条完整窗。目录选择扫描14344080B/1195340条单独记账，目标pdata12B；不读xdata/callee或推index语义。 | [原窗、预算及复现](docs/CLIENT_72_D02_PROUD_INDEX_HANDLER_WINDOW_20261111.md) |
| E-01/R3-PROUD-INDEX-HANDLER-CONTRACT | 已完成 | 主线第三128270B同两fresh，238源/四已存body3178B/852条/95原PC通过；同735B与旧Scene/refU64原bytes闭合普通格式、ZigZag/低32/U32wrap/第10字节/替换及错误边界。仅普通条件规格，不代自然flag/backend或新Proud native。 | [准确合同与旧证据适用范围](docs/CLIENT_72_D02_PROUD_INDEX_HANDLER_CONTRACT_20261111.md) |
| E-01/R3-PROUD-PRIMARY-INDEX-REFERENCE | 已完成 | 主线第三5954083B同双fresh，全部6371项/span/slice独立audit及10边界测试通过；旧Gadget31682全项原oracle复算匹配。新Proud cursor31858=EOF，0wrap/越界，首(4,330)/末(2132482,296)仅候选范围。原覆盖输出缺口已修CreateNew，历史原件保留；未新native/row批准/alias/ready。 | [文件参考、边界和未验](docs/CLIENT_72_D02_PROUD_PRIMARY_INDEX_REFERENCE_20261111.md) |
| E-01/R3-PROUD-ACTUAL-ROW-CONTRACT | 已完成 | 主线第三6382B同两fresh，147源/10915已存bytes/2527条/28anchor；同key22101候选[4,334)已核。缓存backend可在明确条件下承载完整file+cursor4，B不消费；+40=null首inlineDWORD前必须经过base493918，尚不能恢复本体值或批准行尾。 | [新输入与首缺](docs/CLIENT_72_D02_PROUD_ACTUAL_ROW_CONTRACT_20261111.md) |
| E-01/R3-PROUD-BASE-PARSER-WINDOW | 已完成 | 两fresh八原件、主线第三71756B同SHA与7门禁；准确59500.m493918/ordinaryAOT/两QWORD/unique pdata给2097B/483条完整窗。目录选择扫描单列，未新CPU/reader消费或alias。 | [单窗与预算](docs/CLIENT_72_D02_PROUD_BASE_PARSER_WINDOW_20261111.md) |
| E-01/R3-PROUD-BASE-PARSER-CONTRACT | 已完成 | root第三100731B同两fresh，95源/五旧完整窗12233B/2811条/71锚点；明确普通条件prefix31、25项/22B块后cursor134，原2ce3必调14ee1eb20。derived+40-null不保证base+20-null；63831 presence/完整330B/业务字段仍未闭。 | [base消费合同与唯一后继](docs/CLIENT_72_D02_PROUD_BASE_PARSER_CONTRACT_20261111.md) |
| E-01/R3-PROUD-NATIVE-STRUCT-ARRAY-WINDOW | 已完成 | 两fresh八件及root第三16984B同SHA、5门禁通过；新ordinaryAOT两QWORD/完整目录唯一pdata给329B/88条原体。只源窗，不代文件消费/对象或alias，未新xdata/邻body/CPU。 | [唯一数组parser原窗](docs/CLIENT_72_D02_PROUD_NATIVE_STRUCT_ARRAY_WINDOW_20261111.md) |
| E-01/R3-PROUD-NATIVE-STRUCT-ARRAY-CONTRACT | 已完成 | root第三207092B同双fresh，53源/571原指令/52数组锚点；同keyheader明确count2、首元素cursor138，allocator回退/Empty/循环边界分立。已有Promote只有hash/PC，无可重解码bytes；完整元素/最终cursor/presence仍未知。 | [数组合同及准确后继](docs/CLIENT_72_D02_PROUD_NATIVE_STRUCT_ARRAY_CONTRACT_20261111.md) |
| E-01/R3-PROUD-ELEMENT-READER-WINDOW | 已完成 | root第三13768B同两fresh、5门禁；ordinaryAOT/完整目录唯一pdata给285B/80条原体，旧Promote完整SHA匹配；未扩callee/xdata/CPU。 | [准确元素原窗](docs/CLIENT_72_D02_PROUD_ELEMENT_READER_WINDOW_20261111.md) |
| E-01/R3-PROUD-ELEMENT-READER-CONTRACT | 已完成 | root第三54095B同双fresh，86pin/10385B原body/2429条/81锚点；元素2/6/10B与32位carry、同key两缺省元素到142成立。float输入0不代实际encoded DWORD；未新增source/CPU或完整row。 | [元素合同与成熟前缀规格](docs/CLIENT_72_D02_PROUD_ELEMENT_READER_CONTRACT_20261111.md) |
| E-01/R3-PROUD-BASE-PREFIX-REFERENCE | 已完成 | Sol xhigh工具/Terra独立8门+root最终8门；新双fresh/第三各27306B同SHA，138B raw span独立audit，条件cursor142。先前direct写/失败cursor元数据缺口已修且旧原件保留；不代float存储/自然对象/完整Proud。 | [限定参考/最终epoch/复现](docs/CLIENT_72_D02_PROUD_BASE_PREFIX_REFERENCE_20261111.md) |
| E-01/R3-PROUD-DERIVED-FILE-CONTRACT | 已完成 | root第三576784B同双fresh，36源/1778条/83锚点/22presence及21样本call；rawH与P分立，条件142→318后必经17208.m130225，同reader返回后还需4B，不能反推nested消费12B/终点334。 | [准确消费/未知/唯一后继](docs/CLIENT_72_D02_PROUD_DERIVED_FILE_CONTRACT_20261111.md) |
| E-01/R3-PROUD-NATIVE-17208-ARRAY-WINDOW | 已完成 | 新两QWORD/unique pdata得完整329B/90条；双fresh八件相同，root第三17885B及5门禁通过。仅source，不代文件消费/实际对象；无xdata/邻callee/CPU。 | [准确数组原窗](docs/CLIENT_72_D02_PROUD_NATIVE_17208_ARRAY_WINDOW_20261111.md) |
| E-01/R3-PROUD-NATIVE-17208-ARRAY-CONTRACT | 已完成 | root第三96575B同双fresh，90条/65锚点；同key318→322、count4、同reader首元素及分配/Empty/循环边界分立。nested返回/最终row未知，不反推元素宽度。 | [准确合同及唯一后继](docs/CLIENT_72_D02_PROUD_NATIVE_17208_ARRAY_CONTRACT_20261111.md) |
| E-01/R3-PROUD-NATIVE-17208-ELEMENT-WINDOW | 已完成 | 两fresh八件与完整目录唯一pdata，317B/86条；root5门禁/15137B机械audit及1719B独立第三同双fresh。旧摘要未作门禁，无CPU/helper新取。 | [准确元素原窗与第三](docs/CLIENT_72_D02_PROUD_NATIVE_17208_ELEMENT_WINDOW_20261111.md) |
| E-01/R3-PROUD-NATIVE-17208-ELEMENT-CONTRACT | 已完成 | root第三228055B同双fresh，73源/全86条/41元素及53跨窗锚点、81连续物理span；四元素322→330、尾33位和低U32输入2→334。仅单key条件语法，actual encoded/row/自然命中未验。 | [准确元素合同](docs/CLIENT_72_D02_PROUD_NATIVE_17208_ELEMENT_CONTRACT_20261111.md) |
| E-01/R3-PROUD-SINGLE-KEY-REFERENCE | 已完成 | root16门禁及新双fresh/第三82835B同SHA，81 raw span逐项匹配完整payload，从4到334/330B，候选只事后对照。U32 header运算标记16→32红绿已修并保留旧epoch；只验固定key条件文件语法，encoded/alias/自然backend/完整Proud未验。 | [最终源码/第三/边界](docs/CLIENT_72_D02_PROUD_SINGLE_KEY_REFERENCE_20261111.md) |
| E-01/R3-PROUD-ROW-READER | 已完成 | 原1438B/309条、三个backend分支参数及typed缓存已核；6测试/主线第三85断言31839B同两fresh。两个U32用途按分支分开，不统一offset/size；+1c赋值不证明有效row span，400190 producer及真实索引仍未闭。 | [准确合同与下一候选](docs/CLIENT_72_D02_PROUD_ROW_READER_20261111.md) |


## 7. 旧任务归并与后续范围

旧ID保留用于检索/证据关联，不再维护第二套重复执行队列。合并是任务管理调整，不是技术验收通过。

| 旧父项/未闭分支 | 统一去向与保留条件 |
| --- | --- |
| A-03、B-02、B-03、C-01 | R4的依赖/三层/生成/最终收发；其子项成果见第6节，完整运行闭包仍待R6 |
| A-04、A-04d、A-04d4 | 作为R1/R2实际缺输入的支撑研究，不再独立追求“恢复全部客户端”；A-04d4仅旧分组名称，具体已验项仍保留 |
| B-02c、A-04d5n、B-02c9、F-02 | R2登录/分区及R4必要字段闭合；tag965等未知保持，先证明是否必需，不按旧研究顺序盲扩 |
| B-02e、F-03、F-03d1、F-03d1b、F-03d2、F-03d2b、F-03d3、F-03d3f2 | R1及R4的完整初始化/场景；PlayerData/Store已验切片保留，新增字段按实际必要性发卡 |
| D-01、D-01b、D-01b3、D-01b5、D-01b5g、F-04 | R2及R5；观察记录完成不等于SDK通过，成熟自有fixture不反复扩展，新自然实验须精确新问题 |
| E-01、E-01c、E-01d1 | R3；全资源/全Lua扫清不作为D-02默认前置，但必要缺口不省略 |
| F-05、D-02 | 同一最终验收归D02-R6，避免重复计数 |
| V-01 | 先做受影响回归与R4必需失败；完整全量结论独立保留，不以历史数字替代当前结果 |

| 后续任务 | 状态 | 启动依赖与验收 |
| --- | --- | --- |
| E-02 基础玩法/持久化扩验 | 待开始 | D-02后逐项移动、切队、传送、战斗/背包/角色及服务重启冷读，保留经济不变量 |
| E-03 既有玩法 | 待开始 | E-02后按任务/副本/深渊/商城/抽卡/邮件/家园专题闭合请求、资源、保存和客户端效果 |
| E-04 7.2新增内容 | 待开始 | E-01/E-02基础具备，来源可追溯、ID引用和新增处理逻辑逐内容实测 |

维护时同步更新当前工作包与已完成成果目录中的状态、完成标记、验收结论及证据链接；不要粘贴命令输出、长hash、PID、逐次失败、百分比估计或重复暂停指令。新增任务必须说明父项、阻塞出口和验收标准；已完成成果只能补充边界或新增后继，不能因重排删除。合并任务仍须保留各子项完成范围与未闭缺口，不以整组“已完成”覆盖尚未通过的子项。
