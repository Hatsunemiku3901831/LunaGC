# 国服 7.2 任务规划与验收

任务证据基线：2026-11-08 / `50f745e`。仅面向国服 7.2，Windows portable。

## 当前阶段

**已恢复持续执行；D-02 未完成。** 用户恢复指令覆盖历史暂停/仅离线/阶段后停止的节奏限制；当前执行计划和现场入口见[恢复计划](docs/CLIENT_72_D02_RESUME_20261108.md)。历史交接仅用于追溯。

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
| D02-R1 角色与场景初始化 | 进行中 | raw适配器、有限Java投影及消费合同可复用；完整Avatar构造、g2、Scene/Team/Entity正式发送未闭 | 同源角色/库存/资源→真实Java发送→目标消费者及必要状态/顺序闭合，未知不参与发送 |
| D02-R2 SDK与连接前置 | 进行中 | 原始登录页/初始化、SDK请求与响应合同、未启用key核心已验限定范围；响应修复、路由/信任/加载及自然互通待验 | 正确认证/错误拒绝、会话交换、dispatch/密钥和必需补丁合同成立；实际装载/本服连接在R5/R6复核 |
| D02-R3 最小资源与保存恢复 | 进行中 | 真实资源字节/六表、Java目的合同、Lua/章节/保存局部验证已有；字段桥梁、选中revision、自然加载和正向重绑未闭 | 必需资源/引用/脚本实际加载有效；保存→新Player→真实manager恢复成立，不能手设loadedAll |
| D02-R4 P0部署前汇合 | 待开始 | 依赖R1/R2/R3的必要合同；C-01b网络边界与既有回归直接复用 | 必经请求/响应/通知、嵌套、聚合、顺序、资源与最终发包一致；生成、相关回归和构建通过，未验runtime继续保留 |
| D02-R5 隔离部署 | 待开始 | 依赖R4；InstanceRoot入口已验，真实实例未部署 | 独立配置/data/DB/资源，日志/健康/TCP/UDP/PID/运行JAR SHA及实际DLL/信任一致；[部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md) |
| D02-R6 客户端验收（D-02 / F-05） | 待开始 | 依赖R5；原SDK登录页不等于本服登录 | 第5节每项通过并记录范围；失败需修复、回归和正常重部署后复验 |

依赖关系：`R0 → {R1, R2, R3} → R4 → R5 → R6`。三条前置线共享数据合同，但不能用环形等待搁置独立实现：SDK日志/响应修复无需等待完整Avatar；资源字段必须与实际消费者相互核实。部署前证明合同，部署后验证真实连接，不能要求先实机成功才允许部署。

## 3. 当前工作包：发布顺序、依赖与验收

以下为当前获授权执行的任务设计；“可发布”表示输入已足，可优先实现。新增工作包复用旧ID/父项，不重复计算成果。

### 3.1 优先发布：明确修复与有界验证

| 工作包 / 归属 | 状态与依赖 | 交付与验收 | 边界与证据 |
| --- | --- | --- | --- |
| R2-LOG 剩余session日志修复（D-01 / F-04） | 已完成；限定日志红绿/隔离回归/构建；部署待R4 | 有效泄漏红测→最小日志修复；认证/会话采用/保存保持，合成token及异常正文不入日志，关联隔离回归/构建 | 只修DefaultAuthenticators.SessionKeyAuthenticator遗留片段；既有三类日志修复保留；[响应规格](docs/CLIENT_72_SDK_RESPONSE_CONTRACT.md)、[日志合同](docs/CLIENT_72_AUTH_LOGGING.md) |
| R2-SESSION 单token响应适配（D-01 / F-02） | 已完成；独立HTTP、Java→原生及账号保存回归/构建；部署待R4 | `/account/ma-cn-session/app/verify`成功返回`data.user_info`与`data.token{token_type:1,token}`，沿用同一认证token；真实Java成功/错误响应→原生parser/formatter，64位ID字符串及会话内容一致；独立账号/密码/保存回归 | 不重新生成或轮换token，verifySToken数组合同不一并改；错误认证不得出token，type4不擅认combo必经；[SDK响应](docs/CLIENT_72_SDK_RESPONSE_CONTRACT.md) |
| R2-KEY 窄key核心接原生Crypto（D-01 / F-04） | 进行中；同一核心→准确native桥接已验；实际loaded、自然caller/route及生产接入未验 | 同一核心输出供真实SDK Crypto，覆盖身份、caller、寄存器/栈/返回保持及错误拒绝；再形成实际loaded绑定、合法加载/校验影响的最小计划 | 离线出口不批准整DLL安装；[ABI](docs/CLIENT_72_SDK_KEY_INPUT_ABI.md)、[原生桥接](docs/CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md)、[Crypto](docs/CLIENT_72_SDK_CRYPTO_CONTRACT.md) |

### 3.2 并行关闭：必要输入与完整消费者

| 工作包 / 归属 | 状态与当前首缺条件 | 交付与验收 | 证据入口 |
| --- | --- | --- | --- |
| F-03d3f4d2 完整Avatar构造/后缀 | 进行中；fresh父descriptor16B已取，离线cold推进到class1234首缺；typed身份/消费前时序及完整cold/default后继仍待 | 在有来源输入下连续执行cold/default/装备/技能/池等必经链，保留实际停止点；明确必要字段、同源资源和真实Java读取时机 | [父类后继](docs/CLIENT_72_AVATAR_PARENT_CONTINUATION.md)、[采样合同](docs/CLIENT_72_ENTRY_CONTEXT_SNAPSHOT.md)；旧Array/Type已取得，不再重复列缺 |
| F-03d3g2 正式Avatar发送 | 阻塞；依赖f4d2、R3及同源Store/角色/SavedVitals | 固定字段/tag/类型/枚举/值来源和省略语义，确定性生成；真实Java输出交目标消费者；非零/64位/非空/错误及账号保存回归 | g1和g2-prep已完成；只改Proto源，不改旧共享定义求方便；[投影](docs/CLIENT_72_AVATAR_JAVA_PROJECTION.md) |
| F-03d3s2 场景/Team/Entity闭合与发送 | 进行中；缺自然注册/stage、PoolCluster/MPLevel及组件生命周期 | 用准确producer和同源实体完成能力/附属对象/创建替换拆除与通知顺序；逐个批准必需三层和Java来源后接sender | [Scene上下文](docs/CLIENT_72_SCENE_RUNTIME_CONTEXT.md)、[场景token/属性](docs/CLIENT_72_SCENE_ENTRY_CLOSURE.md)；World stage与资源枚举分开 |
| R2-ROUTE SDK配置、会话与自然连接（D-01 / F-02 / F-04） | 进行中；缺server_pc资源loader producer/准确配置、combo签名和自然消费 | 固定来源追配置→选址→签名→会话交换；自然实验一次回答明确问题；必要输入可离线取得时先离线；与R2-SESSION/KEY汇合 | [请求链](docs/CLIENT_72_SDK_LOGIN_CHAIN.md)、[响应](docs/CLIENT_72_SDK_RESPONSE_CONTRACT.md)；原登录页/root非空不证明请求成功 |
| E-01 真实资源→Java必要字段 | 进行中；真实字节已取得，缺精确映射/默认/实际选择revision | 同源表/行→目标parser/消费者→Java字段逐项对应；先角色、depot、技能、默认装备、Scene及必需Quest，拒绝无证alias/省略 | [六表](docs/CLIENT_72_AVATAR_NATURAL_TABLES.md)、[Java目的合同](docs/CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md)、[资源深度](docs/CLIENT_72_RESOURCE_DEEP_RESEARCH.md) |
| E-01 / E-01c / E-01d1 自然加载与正向保存 | 阻塞；依赖必要资源映射、Lua可达路径和实际加载前提 | 必需map/引用/日志/场景事件有效后，独立库保存→新Player→QuestManager正向重绑；保留未就绪拒绝及BSON不变负例 | [最小资源](docs/CLIENT_72_D02_MINIMUM_RESOURCES.md)、[Chapter](docs/CLIENT_72_D02_CHAPTER_LOAD.md)、[303保存](docs/CLIENT_72_D02_QUEST_303_START.md)；不只看loadedAll标志 |

### 3.3 汇合与持续验证

| 工作包 / 归属 | 状态与依赖 | 验收要求 |
| --- | --- | --- |
| A-03 / B-02 / B-03 证据与生成 | 进行中；复用已完成B-01，随新增合同维护 | 精确审核新增/变化来源、保留漂移拒绝、重复生成一致；已验证片段才接入，旧号/未知不兜底。索引更新不是新的语义发现；[矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md) |
| C-01 / F-02 / F-03 网络与初始化整合 | 进行中；全量出口归R4 | 复用C-01b帧/注册修复，补必要Union/枚举/最终发包与序列。检查onLogin、SceneInitFinish、EnterSceneDone后的队伍/世界/实体，不能只验八握手 |
| V-01 回归失败归因 | 进行中；按本次改动和R4范围选集 | 新运行先明确基线/夹具/生产失败；保护账号、经济、保存、Lua。完整全量未重跑就不宣称全绿；不机械用历史失败总数安排任务 |
| F-04 部署材料及隔离核验 | 进行中；复用ENV-01基线，实际部署待R4 | 真实SDK/信任/资源合同充分后形成7.2产物；正常构建和停服，按InstanceRoot归属核对，不仅改版本或环境变量放行；[隔离](docs/CLIENT_72_PORTABLE_ISOLATION.md) |
| F04-LOADER-CLEAN 独立透明入口 | 进行中；原导出表已固定，自有夹具与源码准备中 | 独立于旧模块入口透明转发原库导出，只读核对已加载SDK身份与准确窗口；自有Win32夹具先验，再准备用户在场的真实加载。保留系统/客户端拒绝，不能绕过校验；[计划](docs/CLIENT_72_CLEAN_LOADER.md) |

## 4. 任务发布与范围控制

1. **恢复后先选明确实现与最小缺输入。** 优先R2-LOG、R2-SESSION；R2-KEY原生桥接、资源行映射、Avatar/Scene精确输入各按独立依赖推进。新实验须有目标、来源、候选解释、最小样本、修改范围、停止条件和验收出口。
2. **输入足够就交实现。** 证据冲突、复杂调用链/ABI交深度分析；边界明确的实现、测试、审计和生成可交Sol/Terra。现场与Git/Gradle/DB/固定端口/安装由单一主线串行控制，独立分析可并行。
3. **没有新问题不重复研究。** 已验raw、表定位、构造片段、隔离脚本和测试直接复用。新协议/代码变化才触发相关回归；首次失败和解除条件写专题，不在本表追加过程。
4. **必要性先于深挖。** 未知是否属于入场，不得随意删掉，也不默认所有玩法都是P0。先用当前源码、目标消费者与独立样本确认；可追溯文件能解决的先离线，必须自然上下文时只采准确输入。
5. **旧观察路线不再排队重复。** 旧SW/HW同参数重启、无SDK覆盖的ETW候选和已完成合成fixture保留研究结果；只有新的可检验假设才重新发布，不能把历史失败原因当已解决。

<a id="d02-offline-sol-terra"></a>

原“Sol/Terra离线前置路线”已收口，成果在第6节；该锚点保留供旧链接使用。之后的离线批次从本文件当前工作包筛选，不重开已完成的C-01b、Chapter、g2-prep，也不把旧仅离线授权当作当前执行决定。

## 5. 验收标准

每个任务完成前，须具备**明确范围、可追溯来源、验收结论和可复现证据入口**。协议/运行变更另须独立正反样本、对应实现/生成及受影响回归；不适用项说明理由。静态、离线原生、模型和实机范围分列；必要时双跑核一致。运行变更按AGENTS完成构建/部署核验；缺前置则任务只在局部范围完成，部署及父项保持未验。

实机验收使用受控脚本/API与用户确认，只操作本项目目标，不使用computer-use；原始数据留忽略目录。

| D-02验收项 | 当前状态 | 通过条件 |
| --- | --- | --- |
| 旧测试号本服登录 | 待开始 | 真实SDK/认证→本服会话，账号与角色归属正确；原SDK页不能代入 |
| 新号显式选人/命名 | 待开始 | 新账号进入正常选人流程，合法创建确认保存；错误/重复请求不重复创建或发放 |
| 初始化与场景握手 | 待开始 | 必需角色/库存/队伍/世界/实体状态被客户端正确消费，整个加载序列完成 |
| 实际进入世界 | 待开始 | 可见角色落地与对应服务/协议证据一致；LOADED或健康不代替 |
| 连续心跳与会话保持 | 待开始 | 执行前固定观察时长，期间连续回包、无异常断会话，记录完整观察结果 |
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
| Scene深度/运行上下文限定成果（归D02-R1/R4） | 已完成 | retain/copy、hash注册/查询、token与80154消费、真实Scene/Gadget索引及工厂前缀；PoolCluster/stage/实体后继未验。 | [深度合同](docs/CLIENT_72_SCENE_DEEP_RESEARCH.md)、[入场后继](docs/CLIENT_72_SCENE_ENTRY_CLOSURE.md)、[运行上下文](docs/CLIENT_72_SCENE_RUNTIME_CONTEXT.md) |

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
| 认证日志与InstanceRoot前置（归D02-R2/R5） | 已完成 | 三个认证类日志修复和数据隔离回归、独立运行目录入口已验；DefaultAuthenticator遗留日志已按R2-LOG修复，真实部署未验。 | [日志](docs/CLIENT_72_AUTH_LOGGING.md)、[隔离入口](docs/CLIENT_72_PORTABLE_ISOLATION.md) |

### 6.4 资源、脚本、保存与回归

| 任务ID / 成果 | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| E-01a、E-01b | 已完成 | 固定候选七表、scene3依赖及引用审计；不代表整套资源已安装/可用。 | [候选](docs/CLIENT_72_RESOURCE_CANDIDATE.md)、[scene3](docs/CLIENT_72_SCENE_RESOURCES.md) |
| E-01c2、E-01c3、E-01c4、E-01c5、E-01c6 | 已完成 | Lua序列化修复、API来源、owner UID、生产加载审计及嵌套require恢复；local作用域/必要接口/完整加载仍开放。 | [序列化](docs/CLIENT_72_SCENE_SERIALIZATION_FIXES.md)、[API](docs/CLIENT_72_SCENE_SCRIPT_API.md)、[生产加载](docs/CLIENT_72_PRODUCTION_SCENE_LOAD.md)、[require](docs/CLIENT_72_REQUIRE_BINDINGS_FIX.md) |
| E-01d1a、E-01d1b、E-01d1c、E-01d1d、E-01d1e、E-01d1f、E-01d1g、E-01d1h、E-01d1i、E-01d1j、E-01d1k、E-01d1l | 已完成 | 受控角色/depot、出生网格、能力、实际Lua事件、日常保存和初始实体/自动回调；只验对应suite/路径，不代替所有异步后代或revision。 | [最小资源各子合同](docs/CLIENT_72_D02_MINIMUM_RESOURCES.md) |
| E-01d1m、E-01d1n1、E-01d1n2、E-01d1n3 | 已完成 | Quest台账、目标303拒绝/Fallback、启动/冷读和未就绪门禁、Chapter/通知及反射加载清单；正向manager恢复未验。 | [任务台账](docs/CLIENT_72_D02_QUEST_ENTRY.md)、[恢复](docs/CLIENT_72_D02_QUEST_303_RECOVERY.md)、[保存](docs/CLIENT_72_D02_QUEST_303_START.md)、[Chapter](docs/CLIENT_72_D02_CHAPTER_LOAD.md) |
| 资源深度parser/条件子合同（归E-01/R3） | 已完成 | depot/条件parser、LHFO嵌套及GameTime/LuaNotify分界，Ornament标量/条件数组；未知JSON映射和接受规则未补造。 | [深度资源](docs/CLIENT_72_RESOURCE_DEEP_RESEARCH.md)、[Ornament](docs/CLIENT_72_AVATAR_ORNAMENT_CONTRACT.md)、[条件数组](docs/CLIENT_72_AVATAR_ORNAMENT_CONDITIONS.md) |
| 实际资源来源/容器/索引/locator链（归E-01/R3） | 已完成 | 候选/真实来源、provider、索引reader、blob头、完整payload/index与精确表字节取得；不再等待同一已解决解包问题。 | [来源](docs/CLIENT_72_D02_RESOURCE_SOURCES.md)、[provider](docs/CLIENT_72_RESOURCE_PROVIDER.md)、[索引入口](docs/CLIENT_72_RESOURCE_INDEX.md)、[native reader](docs/CLIENT_72_RESOURCE_INDEX_NATIVE.md)、[blob](docs/CLIENT_72_RESOURCE_BLOB.md)、[payload](docs/CLIENT_72_RESOURCE_PAYLOAD.md)、[完整索引](docs/CLIENT_72_RESOURCE_PAYLOAD_INDEX.md)、[locator](docs/CLIENT_72_RESOURCE_LOCATOR.md) |
| E-01-bridge-energy（归R3） | 已完成 | Avatar/Depot/Skill七字段通过同缓存原生角色索引/能量槽/元素消费者闭合，正引用/缺省/枚举和片段转换已验；完整资源行/加载/revision/保存恢复仍未验。 | [字段桥梁](docs/CLIENT_72_D02_RESOURCE_BRIDGE.md) |
| Java资源目的合同（归E-01/R3） | 已完成 | 加载类/字段/准确消费者及缺Weapon失败位置已审；不是target→Java转换许可。 | [Java目的](docs/CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md) |
| V-01a、V-01b、V-01c、V-01d | 已完成 | 能力便携路径、家具保存/加工手册夹具及家具资源路径修复；完整V-01保持未完成。 | [账号保存验证](docs/ACCOUNT_AND_SAVE_OPERATIONS.md)、[离线收口](docs/CLIENT_72_SOL_TERRA_OFFLINE.md) |

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
