# 国服 7.2 任务与验收

更新：2026-11-06。仅面向 7.2；主仓库起点 d372f62（7.2-dev）。状态只用：待开始 / 进行中 / 阻塞 / 已完成。全部验收条件满足才完成。

## 当前阶段

2026-11-06本次用户已明确恢复持续推进至D-02，覆盖下方历史暂停范围。接手`65fc59a`；先补F-02连续收帧、F-03初始化与E-01真实加载缺口，再依赖满足后部署/实机。不将已有468研究/761 Java当作本轮重跑，不用离线或健康代替D-02。AGENTS/resources既有差异保留；只用Windows portable及脚本/API。

| 本次子项 | 依赖/限定工作 | 验收与边界 | 状态 |
| --- | --- | --- | --- |
| F-02c 连续响应收帧及选key来源 | 固定三输入；复用Token Handler安装key的同一原生运行时，追踪selector写入/读取、LoginRsp真实reader/路由 | 非零/64位/嵌套、独立wire、dispatch/session差异及错误key/边界；二次产物/关联回归；完整Handler/SDK/socket未执行不得晋级 | 进行中 |
| F-03d1 PlayerData独立身份与raw下界 | F-03；固定目标命名/注册/消费、5外字段及非空map闭包 | 上游7193仅候选；独立身份来源、实际reader/64位/错误wire及重复性；未知不命名、不改生产 | 进行中 |
| F-03d1a 79263/78350初始化raw对象图 | F-03d1固定输入、独立AOT9505/shared1541 | 2类型9raw字段及map/boxed oneof int64与float；非零/64位/负值/显式0/覆盖/错误边界、独立wire与双跑；只验raw，身份仍unresolved | 进行中 |
| F-03d1b PlayerData业务身份及字段来源 | F-03d1a、typed handler1507BC0E0及Player模型 | 独立目标literal/具名调用链闭合；禁止按上游或NICKNAME孤立字串批准；没有命名证据保持unknown | 待开始 |
| F-03d2 PlayerStore递归Item闭包 | F-03d1方法、本地固定输入 | 20892仅候选；先核Item/oneof闭包及独立身份/wire，未验不得发送 | 待开始 |
| F-03d3 AvatarData递归Avatar闭包 | F-03d1方法、本地固定输入 | 20143仅候选；AvatarInfo/Team/Rename及packed64位闭包，未验不得发送 | 待开始 |
| E-01c6 嵌套require调用者bindings恢复 | E-01c5已复现成功置null/异常残留 | 真实生产JUnit先红后绿；eval仅保存/try-finally恢复调用者，双require/异常/隔离与相关Lua回归；不称修复local词法defs、吞错或完整资源；构建/部署按实际前置记录 | 进行中 |
| F-04a SDK/补丁/资源前置现场复核 | 当前checkout/config/GAME_PATH、三输入与现有会话 | 三哈希/参考clean/归属已验；API会话无target；两个status退出1及6个revision候选；源码旧强制验签钩子与字符串工厂缺口保留，见CLIENT_72_RUNTIME_PREREQUISITES；仅核对不代表F-04完成 | 已完成 |

2026-11-06本批F-02b1/b2、F-03a/b/c限定融合验收完成，按用户要求暂停；暂不D-02实机。运行代码提交`4bbf9fd`：完整LoginReq/两响应及嵌套21类198字段，八个场景握手、容量5字段、进入场景通知四字段及8类44字段raw闭包已接入。未知字段保留原名且不发送；完整初始化/P0、SDK/补丁/资源及实际客户端序列仍未验。

实际468项研究、独立Mongo43套761项Java全部通过（失败/错误/跳过均0），五个生成检查无漂移。研究结构0错误、ready205缺口；74证据/1267源码与Proto、150根/矩阵、1120描述符文件、1257声明/4806字段，recover双跑。JDK17 portable构建24秒，JAR SHA-256 `570d64f4f1680ab023c7eda7f2e930ebf5eba79791030e8499100ca8f2799bf5`内嵌4bbf9fd；92变化/114新增class均匹配编译输出，109账号/Guard/Lua class保持，3个旧LoginRsp map holder被新raw字段替换。

前置不齐未部署，HTTP8088/UDP22101/测试Mongo27027无监听，无服务PID、健康及运行JAR无对象。三个独立测试库运行均正常关闭，关闭前仅admin/config/local；原Mongo/数据、配置和AGENTS/resources差异保留。收尾既有x64dbg进程仍在，原客户端目标进程未见、退出原因unknown；本轮仅盘点进程，没有客户端/调试器操作，不能据旧PID无条件run/load。详见[交接](HANDOFF2.md)与[融合记录](docs/CLIENT_72_ASTAPS_FUSION.md)。

| 本批子项 | 依赖与实际工作 | 验收 | 状态 |
| --- | --- | --- | --- |
| F-02b1 登录请求/两响应及嵌套wire接管 | 固定三输入；21类198字段、三已验CmdID；未知原名，nested optional仅Java存在性适配 | 50原生writer/34响应图/6拒绝；4显式0/false/空合并回归；468研究/761 Java、生成/构建均过 | 已完成 |
| F-02b2 响应builder实际发送合同 | Token仅uid/token/sign/server_rand_key，拒绝retcode-only；资源61仅子1/3/4/5 | 6原生消费；真实Java RSA成功及两坏参数、准入/UID/保存回归；无伪签名或失败切key；连续收帧selector未验 | 已完成 |
| F-03a 场景握手三层及值编码 | Ready/Init/Done/Post 8包、10类20字段，token/retcode与raw值分记；四请求关联当前场景 | 116原生结果+8省略边界+3独立1017通道；请求12/响应等15 Java；重发/完整调度不晋级 | 已完成 |
| F-03b 初始化最小消息 | 容量28836/22609的5业务字段/8 raw字段；历史选择缓存号及发送停用，显式选人保留 | 134 reader+12实际字典路由、enum/阈值；容量6 Java、账号/经济/保存回归；PlayerData/Store/Avatar完整初始化另缺 | 已完成 |
| F-03c 进入场景通知最小发送合同 | scene_id/type/token/非空pos；完整8类44 raw字段，Vector另验3轴；其余业务未知不发送 | 40原生样本两轮相同；wire12+producer8、三套入场/成员/复活81；原7构造API及LOADING/随机token保留；非实际落地 | 已完成 |

运行代码/Proto变更后做JDK17便携构建及产物/进程/日志/端口核对；完整前置未齐不部署，D-02不验收。本批已按交接流程备份核验HANDOFF2后覆盖，并只追加HANDOFF；暂停后不自动推进下一项。

F-03c已完成40组固定原生取证和48项组合研究回归，两轮逐字节一致；最小可发送字段为scene_id/type/token/非空pos，8类44 raw字段完整保留。位置由实际任务→传送载体→真实队列→具体Default/DirectlySetPos接收者，及独立QuickV3具名pos2/centerAfter两路确认；未执行最终头像落地。见[进入场景合同](docs/CLIENT_72_ENTER_SCENE_CONTRACT.md)。world/time/UID/dungeon/transaction/limited-region继续未知，四字段已接入并由20项独立wire/producer及三业务套81项验收，不将完整sender或P0/D-02标完成。

本批研究验收：F-02b2的[最小响应合同](docs/CLIENT_72_RESPONSE_CONTRACT.md)新增6原生夹具，tag61四字段具名全局目的地和next1337/7边界闭合，工具/摘要两轮相同；最小RSA4字段/retcode-only与既有crypto/Handler4组摘要重跑一致。F-03b的[初始化raw切片](docs/CLIENT_72_INITIALIZATION_WIRE.md)134原生reader/8字段/5模型拒绝、6测试及双跑一致；21136/46070历史选择通知坐标已证不能沿用。研究合同边界保留，对应最小生产接入/468研究/761 Java及构建如首段；不等于完整父项或实机完成。

F-03新增已验研究：[八握手合同](docs/CLIENT_72_SCENE_HANDSHAKE.md)116原生有限结果、10类20字段、13条业务绑定，10聚焦测试；请求/响应token及retcode值变换和缺省/raw0分别验证，DoneReq的1017已限定分清正常Game C2S与另一出站流，保留全局重号。[容量消费合同](docs/CLIENT_72_STORE_WEIGHT_CONTRACT.md)12次完整native接收/分发/真实字典插入，5字段目的地和enum/容量阈值另验，8聚焦测试；3字段保持未知。两份合同均双跑一致，最小运行接入/回归/源码矩阵/构建已收口，完整调度与客户端仍未验。

以下为F-01/F-02a及更早历史检查点，当前状态以上文为准。

上一批收尾：F-01及F-02a限定验收完成，按最新用户要求暂停；F-02/F-02b/F-03仍阻塞，F-04/F-05/D-02未验。提交73dd759（计划）、45e22ce（来源/差异）、4324cd7（四schema融合），均仅本地。399研究、4套43协议及关联、独立Mongo29套589账号/经济/保存/Lua各自全过，完整Java未重跑；结构0/ready228。便携构建23秒、JAR a8c2d893…53a8f8内嵌4324cd7；30变化class与编译输出一致，163个Guard/Player/脚本class保持。TCP8088/UDP22101/测试Mongo27027均无监听，无服务PID；健康/运行JAR无对象。完整P0/SDK/补丁/资源前置不齐且本轮不推进新实机，故未部署；原数据、原调试进程树与AGENTS/resources差异保持。详细备份和恢复入口见HANDOFF2。

最新范围调整：用户要求“目前先进行融合和维护文档，融合完成通过验收后进行暂停”。因此本轮先验收F-01及证据已足的F-02/F-03融合片段、对应构建/回归与文档，然后暂停；F-04/F-05及D-02保留未完成，不推进新实机阶段。下文最初的持续D-02指令已被此收束覆盖。

2026-11-06当前指令已明确恢复并覆盖下方旧暂停：融合AstaPS固定提交a49f51aa6f2787ffbc25cee46801b70dee860d23，持续推进D-02。主仓库接手基线c9aafa6；原384研究/ready228只是暂停基线，非本轮结果。参考来源MeChen618/AstaPS、HEAD及干净状态已复核。按实际差异拆分如下；不整仓合并、不复制生成Java，不沿用0/旧号/20000+或推断，保留本项目账号准入、确认保存、经济Guard和Lua修复。

| 融合子卡/优先级 | 依赖 | 实际差异与交付 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| F-01 / P0 来源与三层差异 | 当前checkout/固定参考 | 固定来源/commit/tree/GPLv3，协议与64源码差异审计；10包号相符、214字段130同形/84缺失、2处MD5语义冲突 | 45e22ce；2105 Proto/8066字段独立descriptor比对0差异，11测试及双跑一致；来源候选不晋级 | 已完成 |
| F-02 / P0 Token/Login/Ping接入 | F-01、F-02a/b | 已验wire与最小响应接入；连续收帧keyselector、SDK/完整P0与实际登录序列仍缺 | 子卡限定通过；不以761选集或构建替代完整登录/客户端验收 | 阻塞 |
| F-02a / P0 请求、包头与心跳wire片段 | F-01、本地固定wire及10已验业务名 | TokenReq/Head/PingReq/PingRsp共45字段，35保持原名；3已验号，停用无引用旧混淆号24604碰撞；Ping共享字段原样回显 | 4324cd7；改前14/8失败；改后43/0、独立Mongo29套589/0、399研究/0；两生成检查无差异，JDK17便携构建23秒、30变化class与编译输出相同；前置不足未部署，不晋级语义/父项/实机 | 已完成 |
| F-02b / P0 响应与完整Login接管 | F-02a、本地固定原生合同 | 21类198字段/完整raw闭包及最小发送，MD5采用本地结论；4bbf9fd | 50/34/6独立native、468研究/43套761 Java、五生成检查和portable构建；完整登录另由父项验收 | 已完成 |
| F-03 / P0 初始化/场景握手与嵌套 | F-01、F-02、本地scene证据 | 8握手、容量、四字段Notify及nested已接入；PlayerData/Store/Avatar、Notify其余业务及完整继续序列仍缺 | F-03a/b/c限定通过；完整P0/场景落地及D-02未验，不自动填补未知 | 阻塞 |
| F-04 / P0 SDK/补丁/资源运行前置 | F-02/F-03、D-01/E-01 | 核对本地信任与路由/ABI、最小资源及Lua复现；上游自动选人/版本放行不迁入 | 保留校验；实际构建、portable部署及日志/健康/TCP/UDP/PID/运行JAR SHA；不满足记录尝试/解除条件 | 待开始 |
| F-05 / P0 D-02实机闭环 | F-02/F-03/F-04 | 独立测试库/数据，新号显式选人、旧测试号、完整初始化/场景握手、心跳、退出重登 | 脚本/API实际客户端证据逐项通过，不使用computer-use、不操作无关目标、不以离线/健康代替 | 待开始 |

融合细节/证据见[融合记录](docs/CLIENT_72_ASTAPS_FUSION.md)。下方暂停文字均为历史，不再限制已授权的恢复工作。

2026-11-06最新指令：完成当时在途的B-02e7和E-01c5后暂停，按约定交接。两项限定验收已完成，新增开发/取证现已停止；用户明确恢复前不启动下一项。原目标D-02仍未完成，不以研究、编译或健康代替实机。以GPT-6.1 Sol推进的授权及重复工作、边界/规划/验收明确时委派的授权保留；困难且值得时才用Astra xhigh。继续保护原数据和他人改动，只用Windows portable与脚本/API。

执行状态：B-02e1至e7与E-01b/c2/c3/c4/c5限定项完成，父B-02e/E-01/D-02未完成；执行已按用户要求暂停。B-02e7提交51f7cd2，16结果为7下一helper入口/4早退/2null/3IFix，81聚焦测试及旧原生回归通过；14AF3D130 body和25866下游仍未执行。E-01c5提交9aa8ca7，真实生产加载8测试和13,616组fast/inline各两轮完成：fast437组部分求值、481错误，inline零错误；540组binding、453组typed对象不同。旧纯求值407结果保留为不同口径。两项正式trace/报告/摘要分别双跑逐字节一致；首次失败均保留。

最终384研究回归通过，结构0错误、ready仍228缺口。人工复核新增scene-data与scene-production-load摘要，63→65输入；既有1287源码/Proto、三层矩阵/计数/候选/描述符/开放边界及原63输入hash均不变，旧review实际拒绝新集合，复核后recover两次一致。当前JAR仍2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe（69c3672），本子批仅离线工具/测试/文档，无重建部署要求；根fastRequire=true和config hash未改。收尾无本项目Java、HTTP/游戏/测试监听，健康/运行JAR无对象；既有调试目标与Mongo/存档、AGENTS/resources差异保留。此前580 Java与owner9项分别通过，完整Java集未重跑。详细暂停状态及下一步入口见HANDOFF2。

前批交接基线（下述运行/数量为历史，以首段为准）：静态结构、Token/Login原始wire及响应非空对象图已离线恢复；四个登录请求/响应身份与CmdID、36个字段来源、8类默认枚举102符号及2类连接状态枚举20符号已有目标证据。明确夹具下已执行Token原生seed/构造/完整帧，以及响应Handler的密码学、安装key、UID/token、版本hash、Login/Full分支、Login构造/实际序列化/完整帧；独立protoc、Java key及错误路径均有验证。不等于整个Handler返回、SDK初始化、真实收帧调度或登录。真实启动已观察全局及UTF-8缓存ABI，最后调试目标已退出，原因unknown。A-03a提供12源码/9根/80方法；A-03b扩展48源码/293方法/409边（337候选），2868未关联调用保留。A-03c给出97包构造/148引用种子/1139文件描述符依赖；缺根和未知三层均不晋级。260研究回归与221项Java经济/保存选集通过，不消除完整集历史217失败，完整集未重跑。交接时复查Java及游戏均未运行，HTTP/游戏端口无监听，MongoDB属本工作区；隔离研发基线部署是历史记录，目前没有7.2可连接构建或登录入场验收。本机路径和原始证据留忽略local/migration72，公共脱敏摘要在docs和protocol/cn-7.2。

已有资源修改 ScriptSceneData/flat.luas.scenes.full_globals.lua.json 不属本批，不覆盖、不提交。patch 起点 f287410，resources 起点 dac2a9d。初查时Java/MongoDB归属本工作区且配置引用旧客户端/存档；随后隔离研发部署记录见ENV-01。下一次启动仍须重新核对配置与进程，不能将历史部署结果当作当前验收或清空原数据。

## 任务卡

各行含目标、依赖、文件/交付物、验收/验证、状态、证据/提交与下一步。复杂任务可拆分子 ID，不能用子项通过替代整项。

| ID / 目标 | 依赖 | 文件 / 交付物 | 验收条件 / 验证方式 | 状态 | 证据位置 / 完成提交 | 下一步 |
| --- | --- | --- | --- | --- | --- | --- |
| DOC-01 接手骨架 | 无 | AGENTS、README、Project、Harness | 职责分明、路径/便携约束正确、已知与计划分离，核对脚本和 diff | 已完成 | 四份文档；3af414d | 后续批次持续更新 |
| A-01 固定客户端和环境 | DOC-01 | audit_client、verify_client_manifest、输入摘要 | 版本/渠道/关键 SHA-256、逐清单大小/摘要、Git/进程/配置/依赖归属；明确缺件 | 已完成 | docs/CLIENT_72_EXTRACTION.md、local/migration72/evidence/manifest-full.json；34ee887 | 输入改变后重新核验；来源认证/游戏验收另列 |
| A-02 服务端协议表面 | DOC-01 | inventory_protocol、报告、旁路清单 | 实际 Handler/发送包/别名/负占位/配置/字面量全盘点，动态调用人工复核 | 已完成 | CLIENT_72_PROTOCOL_INVENTORY、server-surface-review.json；e87baa7 | 17位置/10组来源已复核；开放插件/配置边界交A-03/C-01，不代表运行期闭包 |
| A-02a 确定性静态盘点工具 | DOC-01 | inventory_protocol、8项测试 | 忽略注释/字符串、继承链排除发送类假冲突、动态项不猜测、重复输出一致 | 已完成 | server-inventory.json、inventory-tests.log；34ee887 | 作为A-02/A-03输入 |
| A-02b 动态来源复核守卫 | A-02a | review_protocol72_surface、人工清单、7项测试 | 全17动态位置/40来源hash/常量绑定/37数字case；新增或漂移拒绝，开放边界不晋级，重复一致 | 已完成 | CLIENT_72_PROTOCOL_INVENTORY、evidence/server-surface-review.json；e87baa7 | C-01校验事件后最终包，禁候选兜底并拒绝注册冲突 |
| A-03 登录入场依赖闭包 | A-02 | P0根、调用边、Proto引用图、复核表 | 完整覆盖新旧号、选人、初始化、场景、嵌套；人工和客户端复核 | 进行中 | 静态起点114名称/471文件，见协议盘点摘要；— | 追踪管理器/事件/脚本，结合真实序列收敛 |
| A-03a 方法级登录/场景源码索引 | A-02b | Client72JavaSurface、client72_java_surface、review_client72_login_surface、复核表/摘要 | JDK17实际parse，注释/字符串/UTF16与CRLF/record/switch/lambda/匿名类/重载边界；固定源码/13条人工边，未关联调用和目标映射保持未知，重复一致 | 已完成 | CLIENT_72_LOGIN_SOURCE_GRAPH、login-source-observations.json；12源码/9根/80方法，96边中83仅候选；8新增/248回归、二次一致；00e0bee | 收尾801834d；本批后续限定项均完成并停止，不代表A-03完整或7.2必需包集 |
| A-03b 管理器/事件源码依赖复核 | A-03a、V-01c | 固定源码/人工入口边、管理器与事件边界表、可复现摘要/测试 | 从已索引登录/场景方法逐条定位管理器/服务/事件/脚本入口及内建调用，记录条件/异步/动态开放边界和源码hash；拒绝漂移、候选不晋级、重复输出一致；不宣称完整或实机闭包 | 已完成 | CLIENT_72_LOGIN_DEPENDENCIES、login-dependency-observations；48源码/293方法/70人工规格、14聚焦/254回归/重复一致，本项提交 | a250354；A-03c已完成，整体仍待实机复核 |
| A-03c 候选消息/Proto与7.2三层缺口矩阵 | A-03b、B-01 | 源码候选到发送类/Proto依赖图、三层证据矩阵、工具/测试/摘要 | 每个候选有来源方法/构造、直接Proto与递归import/嵌套依赖；CmdID→类型、类型→语义、字段→wire分别列证据/歧义/缺口；未知不晋级，非零/64位/嵌套/list/错误边界既有证据不丢失，漂移及重复验证；A-03整体保持待实机 | 已完成 | CLIENT_72_LOGIN_PROTO_MATRIX、login-proto-gap-matrix；97构造/148引用/1139文件，6聚焦/260研究回归/重复一致，本项提交 | 已按指令停止；Astra下一项为B-02选人三件组三层证据，须先建子卡 |
| A-04 7.2 提取器 | A-01 | extract_client72、profile、研究记录 | 类型/成员/泛型/父类/布局/注册与CmdID候选有证据，边界及抽样验证 | 进行中 | docs/CLIENT_72_EXTRACTION.md；— | 保护入口/回调参数及运行时闭合泛型ABI仍需恢复 |
| A-04a 目标名称/字段/方法基础提取 | A-01 | extract_client72、7项测试 | 三份目标哈希守卫；独立名称向量；全部跨度/引用/代码地址校验；不把常量返回值标作包号 | 已完成 | client72-metadata.json、extraction-summary.json、提取研究摘要；34ee887 | 89,748类型/445,462字段/741,021方法供A-04后续分析 |
| A-04b 泛型/父类/静态布局 | A-04a | extract_client72 --structures、test_client72_structures、提取摘要 | 全泛型行/引用边界、父图无环、字段存储分类；未知布局保留未知，目标全量运行和13项测试 | 已完成 | client72-structures.json、structures-summary.json、logs/structures-tests.log；2003432 | 运行时闭合泛型和多维bounds仍需另验；供wire字段定位 |
| A-04c getter网络语义和静态包号 | A-04b | extract_client72_commands、commands.json、CLIENT_72_NETWORK | 两个发送调用点与封/解包闭环，槽/祖先/方法字向量及歧义测试，确定性提取；不冒充业务名称 | 已完成 | 网络研究摘要、commands-summary.json、commands-tests.log；333b628 | 4,012类型/4,007值；5组重号域未知；追踪业务与热修复 |
| B-01 映射模型及校验 | A-02a | protocol/cn-7.2、validate_protocol72、11项测试 | 三层状态/来源/歧义/别名，字段容器/引用及目标哈希校验，未知可保存但拒绝ready | 已完成 | protocol/cn-7.2/README.md；99d5a6c | 继续研究真实映射；拆合并语义由证据人工审批 |
| B-02 P0 三层映射 | A-03、A-04、B-01 | CmdID/message/field 表、wire fixture | 全部 P0 tag/类型/枚举/容器/嵌套有证据，非零/64 位/错误/边界测试 | 进行中 | CLIENT_72_NETWORK、CLIENT_72_SESSION；— | 四个登录身份/36字段来源已确认；继续完整Handler、剩余字段和P0闭包 |
| B-02a 包头/心跳静态wire | A-04b、A-04c、B-01 | extract_client72_wire、wire-observations、research.proto、check_client72_wire_proto | 14个实例字段tag/offset/标量/bytes与writer匹配、包头读写交叉、确定性生成、独立非零/64位/边界wire断言；业务未知保留未知 | 已完成 | CLIENT_72_NETWORK；p0-research-tests.log（37项）、wire-proto-tests.log（9项）；c9abee3 | 回调闭合前PingReq业务名仍candidate；不代替B-02或实机验收 |
| A-04d 登录保护入口与回调类型 | A-04b、A-04c | 原生控制流、方法/泛型签名、受控观察工具 | Token/Login读写真实数据流有证据，响应回调与类型匹配；不得按保护字节线性猜测 | 进行中 | CLIENT_72_SIGNATURES、CLIENT_72_PROTECTED、CLIENT_72_SESSION；— | 请求/响应身份、seed后段/token链已确认；继续完整保护构造/成功处理与未覆盖字段 |
| A-04d1 方法参数和Action回调参数 | A-04b | extract_client72_signatures、CLIENT_72_SIGNATURES、测试 | 全参数跨度连续/边界与引用校验，独立向量、BYREF及VAR/MVAR区别；闭合Action参数可复现，未恢复项明确保留 | 已完成 | client72-signatures.json、signatures-summary.json、signatures-tests.log（12项）；ce9e3ae | 555,919参数、901条Action引用/835实例；返回类型和泛型方法实例另验 |
| B-02b PingRsp静态类型/指令/wire | A-04d1、A-04c、B-02a | extract_client72_wire、mappings、研究Proto、wire断言 | 回调类型/注册/分派/getter一致，3字段tag/偏移/有符号性，负错误值独立wire断言；静态与运行状态分离 | 已完成 | CLIENT_72_NETWORK；p0-callback-tests.log（44项）、wire-proto-tests.log（11项）；b87fe73 | MJGOAHFNCGC/25763；字段业务含义仍candidate，实机待验 |
| A-04d2 TokenReq保护writer离线观测 | A-04d1 | trace_client72_token、token-wire-observations、protected-token.proto、protoc比较 | 3组受控输入完整返回，28字段全部对应/零默认，显式helper模型；重复输出一致，独立wire/protoc比较通过，业务未知不晋级 | 已完成 | CLIENT_72_PROTECTED、token-protected-trace.json、token-protoc-check.json、token-determinism.json；4b15d73 | 28字段业务名称/非ASCII/IFix/实机仍需验证 |
| A-04d3 Login嵌套与Token/Login响应reader | A-04d2 | 离线输入模型、嵌套依赖、原始观察 | Login全字段及嵌套/列表，两个响应reader受控tag/value/错误路径，独立wire与边界验证 | 已完成 | CLIENT_72_LOGIN_WIRE、CLIENT_72_GRAPH_READERS；242b1fb | 限定离线受控wire；业务语义、运行时ABI与实机另验，继续A-04d/B-02 |
| A-04d3a LoginReq完整原始wire及嵌套 | A-04d2 | trace_client72_login、login-wire-observations、login-research.proto、CLIENT_72_LOGIN_WIRE | 55外层字段、6bool逐个扰动、3嵌套闭包/列表/oneof，50原生夹具与protoc比较、3独立断言、重复输出一致；业务/枚举未知保留 | 已完成 | CLIENT_72_LOGIN_WIRE；login-trace/protoc-check/determinism、a04d3-login-tests.log；606ea03 | 响应reader与业务语义独立推进，不代替A-04d3总验收 |
| A-04d3b 响应外层及标量受控reader | A-04d3a | client72_reader、trace_client72_responses、response-wire-observations、CLIENT_72_RESPONSE_WIRE | 元数据85实例字段全部外层定位；256扫描、11受控样本（负数/64位/double/列表/重复/错误wire）、独立字节断言、10模型拒绝；未恢复内容不得晋级 | 已完成 | responses-trace/determinism、a04d3-reader-tests.log；91d9791 | 不代表嵌套内容或游戏客户端通过；接A-04d3c |
| A-04d3c 响应非空内容与合并行为 | A-04d3b | 嵌套reader、容器codec、独立fixture | 8类嵌套/值消息（含已有HML writer）及map entry、列表、重复消息合并、非空边界与独立wire验证 | 已完成 | CLIENT_72_GRAPH_READERS；response-graph-trace/determinism；242b1fb | 34原始wire+34protoc输入、6拒绝路径通过；业务/实机状态不晋级 |
| A-04d3c1 七个响应叶子双向wire | A-04d3b | trace_client72_response_leaves、叶子字段表/Proto、protoc检查 | 7类型30字段；26原生writer/27reader/26protoc/2独立断言通过，空值/负数/64位/bool与sfixed32区别，重复产物一致 | 已完成 | CLIENT_72_RESPONSE_WIRE；response-leaves-trace/protoc-check/determinism、a04d3-leaf-tests.log；5fe24ad | 只验独立叶子，继续父响应非空内容及合并行为 |
| A-04d3c2 map泛型AOT注册关联 | A-04d3c1 | extract_client72_map_aot、map-aot-observations、CLIENT_72_MAP_AOT | 从目标代码恢复12/14字节表关系；全函数引用范围检查；具体/共享入口同址；21选定注册与4项边界测试、重复输出一致 | 已完成 | CLIENT_72_MAP_AOT；map-aot-determinism、a04d3c-aot-tests.log；ce45b47 | 支持原生map adapter/SetItem执行，合成运行时上下文仍明确标注 |
| A-04d3c3 原生响应对象图读取 | A-04d3c1、A-04d3c2 | client72_graph/map_runtime/graph_fixtures、trace_client72_graph、response-graph.proto/observations | 7叶子接回父响应、3map/列表/HML全部oneof分支及重复合并；34原始+34protoc输入、6边界拒绝、重复产物一致，模型边界明确 | 已完成 | CLIENT_72_GRAPH_READERS；response-graph-trace/determinism、a04d3c-graph-tests.log；242b1fb | 模型仍限定ASCII/基础无IFix路径；继续业务字段、枚举符号及回调身份确认 |
| A-04d4a 登录标记与枚举常量 | A-04d1、A-04d3c | extract_client72_literals/enums、check_client72_metadata_values、原始符号表 | 目标usage source/slot分别恢复、4标记原生解码；6枚举70符号、14原生常量读取、5边界/独立向量测试通过 | 已完成 | CLIENT_72_LOGIN_SEMANTICS；metadata-values-native.json；a9ba540 | 连接Handler、业务字段与wire；平台来源枚举不当作网络枚举 |
| B-02c Token/Login身份与字段来源 | A-04d4a、A-04d3c | 登录语义观察表、native受控样本、候选表/校验 | 注册/分派/参数/目标自身标记闭合；字段来源与wire关联，未知保留，独立回归 | 进行中 | CLIENT_72_LOGIN_SEMANTICS、CLIENT_72_SESSION；— | 四个登录身份/35字段已闭合；继续其余字段与完整受控业务路径 |
| A-04d4b 两个上游响应身份 | A-04d4a、A-04d1 | trace_client72_login_semantics、literal/参数/getter/注册/分派证据 | 27808/20835实际分派、6独立wire错误前缀命中目标自身标记；4包装参数样本纠正层级 | 已完成 | CLIENT_72_LOGIN_SEMANTICS、login-semantics-trace；010f0c4 | 请求配对、完整成功/错误处理与实机仍另验 |
| B-02c1 二十字段业务来源 | A-04d3c、A-04d4b | login-semantic-observations、mappings、校验和测试 | 14请求字段两组来源到原生wire；4响应全局消费者及2错误值，未知不晋级；87项回归、确定性复跑 | 已完成 | CLIENT_72_LOGIN_SEMANTICS、login-semantics-tests.log；010f0c4 | 剩余字段/请求配对；原始枚举与业务字段命名分别管理 |
| A-04d5a 解密后seed与会话密钥 | A-04d4b | client72_session_runtime、trace_client72_session、Java独立对照 | 明确解密后边界，原生整数存储/字节序/XOR/MT64/4096字节写入；12组Java逐字节对照、6短输入原生拒绝、确定性复跑 | 已完成 | CLIENT_72_SESSION、session-seed-trace/determinism、session-all-tests.log；0f324eb | 前段解密/验签/Shell与完整Handler仍待验证 |
| B-02c2 会话token保存与Login参数 | B-02c1、A-04d5a | trace_client72_token_flow、session-token-observations、映射/校验 | 独立响应tag6→原生会话+98保存→真实调用参数→构造+D8→请求tag1；4样本+缺会话拒绝、重复/空值/高参数、确定性及95项回归 | 已完成 | CLIENT_72_SESSION、session-token-trace/determinism；0c12f2c | 请求完整发送、+1D8语义与剩余业务字段仍待验 |
| A-04d5b 请求身份与阶段配对 | A-04d4b、B-02c2 | trace_client72_request_identity、阶段枚举、映射/校验 | 阶段方法签名/原生常量/请求构造self-class/响应前缀/原生getter交叉，6组样本和100项回归、重复一致 | 已完成 | CLIENT_72_SESSION、request-identity-trace/determinism；70f3e5b | 四个登录身份已闭合，完整构造/发送及字段闭包仍待验 |
| A-04d5c / B-02c3 Login构造与SDK来源 | A-04d5b、B-02c2 | trace_client72_login_constructor、SDK字段映射与校验 | 明确旧Shell/非云输入，真实保护构造到发送入口；3组来源→对象→writer，UInt32上界/溢出/无效、未知Shell语义不晋级、105项回归和重复一致 | 已完成 | CLIENT_72_SESSION、login-constructor-trace/determinism；cde97d4 | Token构造、发送队列、其余字段及密码学前段 |
| A-04d5d client seed来源 | A-04d5a | SessionRuntime72、session-seed-trace/observations | Random.NextDouble槽/2^64常量/原生转换/同值返回与保存，10组边界输入、既有18组后段及106项回归、重复一致 | 已完成 | CLIENT_72_SESSION、client-seed-source-tests.log；e716da8 | 15121DB40的Shell操作0x64/密文构造仍未知 |
| A-04d5e / B-02c4 Token构造外段与SDK来源 | A-04d5c、A-04d5d | trace_client72_token_constructor、映射/边界校验 | 15121DB40明确opaque输入；其余构造到send入口，4组tag10来源与原始会话字段wire断言、133项回归、确定性 | 已完成 | CLIENT_72_SESSION、token-constructor-trace/determinism；423330e | 密文helper未执行，继续发送队列/会话来源与密码学前段 |
| A-04d5f 两请求到封包入口 | A-04d5c、A-04d5e | trace_client72_send_flow、声明参数/虚槽/校验 | 明确选择传输夹具，原请求指针经信封/接口到封包+10、原生getter→+22；8组非零/64位/禁用/缺包装、138项回归、重复一致 | 已完成 | CLIENT_72_SESSION、send-flow-trace/determinism；8638ea5 | 14A447E00函数体、另一传输与真实启动状态/实机另验 |
| A-04d5g seed字符串原生加密 | A-04d5d | client72_seed_crypto、trace_client72_seed_crypto、独立RSA断言 | 明确初始化/OS/托管夹具，完整helper返回；原生验签与加密、8组含零/64位边界/不同熵/未初始化/篡改，独立整数RSA/完整签名块与重复产物 | 已完成 | CLIENT_72_SESSION、seed-crypto-trace/observations/determinism；823762e | 真实SDK初始化和响应解密验签；不替代实机 |
| A-04d5h / B-02c5 完整Token与seed字段 | A-04d5e、A-04d5f、A-04d5g | trace_client72_token_complete、映射/校验 | 同次构造原生helper→原返回指针→+90→tag623→发送getter24604/封包入口；5组成功/错误及独立RSA/wire，149项回归与重复产物 | 已完成 | CLIENT_72_SESSION、token-complete-trace/observations/determinism；cdbc275 | client_rand_key已确认；SDK初始化、封包体及其他会话字段 |
| A-04d5i / B-02c6 响应wire到密钥安装 | A-04d5a、A-04d5g | client72_response_crypto、trace_client72_response_crypto、字段映射/校验 | 原生reader→解密→SHA256→验签→XOR/MT64；明确本地签名信任输入，8组含3组Java逐字节对照和错误拒绝，块/指令轨迹重复一致，155项回归 | 已完成 | CLIENT_72_SESSION、response-crypto-trace/observations/determinism；caa7bea | SDK warmup/真实初始化、完整Handler与本地补丁实际信任配置/加载，非实机验收 |
| A-04d5j / B-02c7 Handler入口到UID/token保存 | A-04d5i、B-02c2 | trace_client72_handler_core、UID映射/校验 | 原生入口→old/new warmup→密码学→UID setter/__LastUid__→token原指针保存；4组零/高位/uint32最大/空token，157项回归及重复产物 | 已完成 | CLIENT_72_SESSION、handler-core-trace/observations/determinism；b0ca919 | stop后robot-check/版本/UI动作、SDK真实初始化/持久PlayerPrefs与剩余请求字段 |
| A-04d5k / B-02c8 SDK认证来源到Token wire | A-04d5h、B-02c7 | trace_client72_auth_sources、五字段映射/校验 | 原生回调来源检查/写入→真实seed构造→getter24604/writer；9组含拒绝/可选字段/uint32边界，160项回归及重复产物 | 已完成 | CLIENT_72_SESSION、auth-sources-trace/observations/determinism；c7556a0 | 前段已收口；可选字段见A-04d5l，完整调度及B0来源关联继续A-04d5n |
| A-04d5l 认证回调可选字段 | A-04d5k | trace_client72_auth_tail、摘要/测试 | 从入口连续到14F192325；六字段/清零/Int32边界/溢出、两个exact-one标志、B0不变，重复产物一致 | 已完成 | auth-tail-observations.json、auth-tail-determinism.json；46d422c | UI/调度尾段未执行，六字段不晋级协议语义 |
| A-04d5m 分区候选源到B0/Token原始链 | A-04d5h、A-04d5l | trace_client72_dispatch_token、摘要/测试 | 两个post-parse片段、tag14 reader、真实seed构造/tag965 writer；8值样本/6缺对象边界、重复一致 | 已完成 | dispatch-token-observations.json、dispatch-token-determinism.json；5dcf882 | 源30677与协程实际绑定仍candidate；业务语义未知，不代表B-02完成 |
| A-04d5n / B-02c9 分区来源关联与tag965语义 | A-04d5m、A-04d1 | 分区解析调用/类型证据、有效和错误样本、映射摘要 | 实际解析结果类型→B0写入调用关系闭合；业务来源独立证据及wire边界；未知不命名 | 进行中 | CLIENT_72_SESSION；前缀探索在忽略analysis/probe_dispatch_prefix.py | 前缀见A-04d5n1、嵌套至配置入口见n2；第一入口见n4、第二协程恢复状态见n5；tag965无独立业务名证据，保持未知；继续必要登录字段/调度 |
| A-04d5n1 分区JSON/密码学到实际reader | A-04d5m、A-04d5i | trace_client72_dispatch_prefix、摘要/测试 | 真实entry→Base64/RSA/SHA/验签→30677分配/reader→post-parse指针；3成功/3拒绝，独立RSA/明文/wire、169回归及重复一致 | 已完成 | dispatch-prefix-observations.json、dispatch-prefix-determinism.json；c9f6870 | 成功明确本地签名信任输入；后处理、第二协程、业务语义与实机未验 |
| A-04d5n2 分区嵌套reader至配置入口 | A-04d5n1 | trace_client72_dispatch_post、摘要/测试 | 同次JSON/密码学→原生嵌套ReadMessage/limits/reader→配置参数；非零/uint32边界/重复合并/错误路径8组、回归及重复一致 | 已完成 | dispatch-post-observations.json、dispatch-post-determinism.json；c34ab42 | 不执行配置函数体；继续151204AC0至B0，业务语义未知 |
| A-04d5n3 分区配置消费与Region全局写入 | A-04d5n2 | trace_client72_dispatch_config、摘要/测试 | 同次JSON/密码学→两次配置原生返回/事件→Region全局；6组含Int32边界/列表/坏JSON，177回归及重复一致 | 已完成 | dispatch-config-observations.json、dispatch-config-determinism.json；c158e0e | 继续1512124B9的SDK前置/15275F080至B0，不跳过SDK调用 |
| A-04d5n4 第一分区入口连续到会话/Token | A-04d5n3、A-04d5h | SDK/提示模型、trace_client72_dispatch_session、摘要/测试 | JSON/原生密码学→配置→SDK包装/提示→B0原指针→同运行时seed/Token writer；6组边界、原Token5组不变、182回归与重复一致 | 已完成 | dispatch-session-observations.json、dispatch-session-determinism.json；4200ea6 | 第一入口关联已执行；第二协程151226CD0和tag965语义仍未验 |
| A-04d5n5 第二协程恢复状态到会话/Token | A-04d5n4 | trace_client72_dispatch_coroutine、摘要/测试 | 实际MoveNext状态2/3→密码学/reader→第二B0 store→Token；状态表/闭包校验、8组含拒绝/边界、187回归与重复一致 | 已完成 | dispatch-coroutine-observations.json、dispatch-coroutine-determinism.json；2a71ddf | 不覆盖初始HTTP等待和B0后尾段；保留tag965未知，继续必要登录字段 |
| A-04d5o / B-02c10 Token密钥选择字段 | A-04d5h、A-04d5i | trace_client72_key_selection、Java对照、映射/校验 | 原生tag1184=4、独立命名URL参数、唯一密钥匹配；3请求/6响应含错误key、192回归及重复产物一致 | 已完成 | key-selection-observations.json、key-selection-determinism.json；b89d855 | key_id按业务契约交叉证据确认；继续完整Handler/封包体及P0闭包，不代表D-02 |
| A-04d5p Handler尾段robot输入与版本哈希 | A-04d5j | client72_version_hash、trace_client72_handler_tail、摘要/测试 | Handler入口连续到原生setter、版本拼接、ASCII/SHA1/Base64及返回；7组含空值/块边界/Unicode/坏签名，198回归及重复一致 | 已完成 | handler-tail-observations.json、handler-tail-determinism.json；e1a0194 | 继续GameManager状态/回调到Login；tag3/tag658语义未知，不替代整Handler/实机 |
| A-04d5q Handler连续到Login与发送入口 | A-04d5p、A-04d5c、A-04d5f | trace_client72_handler_login、声明/条件/指针/wire证据 | 实际Login/Full→Action→Login构造→封包入口；9组含32/64位、空值、时钟/缺对象/包类型/坏签名，204回归及重复一致 | 已完成 | handler-login-observations.json、handler-login-determinism.json；dc41c99 | 已恢复；继续封包体/P0闭包，不能宣称实机或整个Handler返回 |
| A-04d5r 独立封包函数与循环XOR | A-04d5f | trace_client72_frame、摘要/测试 | 原生包头size/writer、帧/长度/端序/整帧XOR/清理返回；高位命令、64位包头、65536字节体、两密钥/循环/缺失/拒绝，独立字节与重复验证 | 已完成 | frame-observations.json、frame-determinism.json；15原生样本、210回归、两次产物一致；ae23ee7 | 负载writer和密钥来源为明确输入，继续串联/收帧，不晋级实机 |
| A-04d5s 独立收帧/解密与错误边界 | A-04d5r | trace_client72_frame_reader、摘要/测试 | 原生端序初始化/XOR/包头reader/长度与标记校验；18组高位/64位/重复/截断/坏tag/错key/尾随字节、独立输入与重复一致 | 已完成 | frame-reader-observations.json、frame-reader-determinism.json；216回归；b698f75 | factory/负载reader为明确模型，继续真实消息与密钥串联，不晋级客户端验收 |
| A-04d5t 连续原生Token到完整帧 | A-04d5h、A-04d5r | trace_client72_token_frame、摘要/测试 | 同CPU seed/构造/发送/实际size与writer/完整帧；6组含非ASCII/NUL/长字段/验签失败，独立全字段wire、222回归及重复一致 | 已完成 | token-frame-observations.json、token-frame-determinism.json；389fe4a | 帧初始key/SDK/OS仍明确夹具，继续真实key来源或Login/响应串联 |
| A-04d5u 原生Ec2b与初始化 | A-04d5t、D-01b1 | client72_ec2b、trace_client72_ec2b、摘要/测试 | 原生注册/RNG初始化/完整Ec2b/managed结果；16组含地址/缓存/扰动/拒绝、独立固定pair与live状态、228回归及重复一致 | 已完成 | ec2b-observations.json、ec2b-determinism.json；0153d00 | 非空分区来源接续A-04d5v，物理截断语义不扩大 |
| A-04d5v 分区Ec2b来源到Token完整帧 | A-04d5n4、A-04d5t、A-04d5u | trace_client72_dispatch_key_frame、摘要/测试 | 多RSA块真实解密/验签/reader→三次Ec2b→两配置XOR/清槽→最终key原指针→Token实际size/writer/帧；独立输入、跨周期/空key/错误魔数与重复回归 | 已完成 | dispatch-key-frame-observations.json、dispatch-key-frame-determinism.json；4原生样本、234回归、二次一致，旧三工具各6样本产物不变；2523513 | 继续Login/真实响应串联或P0/SDK；调度器/实机不在本项范围 |
| A-04d5w 响应Handler到Login完整帧 | A-04d5q、A-04d5r | trace_client72_handler_login_frame、摘要/测试 | 原生响应密码学/安装key→Handler/Login构造→发送→实际序列化/完整帧；保留key/request/token/hash指针，独立protoc/Java key、边界/错误/重复回归 | 已完成 | handler-login-frame-observations.json、handler-login-frame-determinism.json；10原生样本、240回归、二次一致，Token原6组产物不变；6db47fe | 继续A-03/P0调用闭包或真实响应收帧，不代表整个Handler返回/实机 |
| B-02d 选人三件组三层证据 | A-03c、固定7.2输入 | born研究工具/独立wire与protoc/三层摘要/Sol实现规格 | 仅DoSetPlayerBornDataNotify及SetPlayerBornDataReq/Rsp；固定输入hash，目标注册/getter与业务调用独立关联；实际原生执行、独立wire/protoc、非零及错误边界，64位/嵌套/list按实际schema验证或明确不适用；未知不晋级、不伪造空消息；证据闭合且可按明确规格实现回归立即停止，不进入B-03/C-01/D-02 | 已完成 | CLIENT_72_BORN、born-observations；39原生/6新增/266回归、独立protoc及重复一致；本项提交 | 立即停止研究，交Sol按明确规格实现回归；不自动进入B-03/C-01/D-02 |
| B-03 确定性生成 | B-01、B-02 | generator、PacketOpcodes、Proto | 只生成核实项，别名/引用检查，重复输出一致，protoc 编译，禁止旧号兜底 | 进行中 | 限定子卡B-03a；整体B-02/P0仍缺证据 | 全表/别名/引用仍待完整映射，局部通过不晋级 |
| B-03a 选人三件组确定性生成与wire接管 | B-01、B-02d | 固定证据生成器、三件组生产Proto/目标常量、独立wire回归 | 仅5008/8541/414与nick_name4/avatar_id11/retcode15；校验输入hash/三层证据及引用/冲突，候选与旧号不兜底；二次生成一致及check漂移拒绝；Gradle/protoc生成Java；通知/成功空payload、非零/负值/Unicode/uint32/未知及错误边界独立断言 | 已完成 | CLIENT_72_BORN；6生成/6研究工具测试、15生产Java wire通过；generateProto/首次生成与check哈希一致 | 仅接管三件组，B-03整体/P0不晋级；C-01a继续业务及Guard回归 |
| C-01 双向网络接入 | B-03 | GameSession、BasePacket、Handler 注册、UnionCmd/枚举 | 注册冲突失败、号/长度边界、内外分派、目标 wire 测试，构建部署 | 进行中 | 限定子卡C-01a；全域分派/连接前置未齐 | 完整P0/网络接入与部署另验收，局部通过不晋级 |
| C-01a 选人三件组业务接入与保存回归 | B-03a、B-02d | 通知/请求Handler/响应、新旧号登录门控、隔离Mongo回归、构建及部署判定 | 新号等待显式选人；重复创建不重复写/发放且错误不onLogin；非法输入/准入撤销不登录；成功确认持久化后继续；旧号不重选；非零错误真实编码tag15且无配置/旧号兜底；保留账号/经济/保存Guard选集；完成构建，前置不齐明确不部署、不启动服务/实机 | 已完成 | 8bd338f；19业务/15wire及共495项Java、272研究回归全过；便携build成功/JAR10类一致；CLIENT_72_BORN及born-build-check.json | 仅限定代码/离线验收；父任务不晋级；因完整成功继续/P0/SDK/资源前置停止，B-02e待Astra取证 |
| B-02e 选人成功继续与首批必需消息证据 | B-02d、C-01a离线实现 | 固定目标继续路径工具/样本、状态/调用依赖、实际序列证据 | 固定输入，Rsp0→14CD68860完整继续/状态/发送；新旧号序列核对选人三件组与首批初始化/场景门控；新增必需消息三层独立证据，未知/缺SDK前置如实记录，不扩大全部玩法 | 进行中 | 从a09e201恢复；先核对完整继续函数，尚无新增验收 | 自行沿固定目标离线继续，按需要拆可独立验收子项；实机先满足D-01/E-01等前置 |
| B-02e1 选人成功原生继续与异步边界 | B-02d、C-01a | trace_client72_born_continue、回归、born-continuation-observations、专题 | 固定三输入重新提取；reader→Handler→14CD68860、直接状态/UI及14CD62280闭包/Action/1523F1120回调登记；页面有无、性别/状态两值及可执行回调/失败边界独立断言；声明/方法/usage守卫，重复一致、原born回归保留；未知不模拟成功，父任务/runtime不晋级 | 已完成 | b68a8ab；33原生/9新增、原born39重跑不变；完整288研究回归通过，新增3证据人工接入矩阵后重复一致 | 46311对象的网络来源/reader/缓存等待及异步调度继续；20353消息仅原始三层，语义unknown，不改生产 |
| B-02e2 46311网络reader与选人缓存等待 | B-02e1、固定7.2输入 | trace_client72_scene_wait、独立wire/protoc及回归、摘要/专题 | 固定声明/注册/方法/代码守卫，核对27748→46311；实际原生reader外层及必要非空嵌套、非零/64位/list/重复/错误边界；网络Handler入口/前置门与14CD67260缓存clone/等待路径保留原指针，未执行后段与异步明确止步；独立断言、原born回归与二次一致，语义未知不晋级 | 已完成 | CLIENT_72_SCENE_WAIT；128发现/32正式、26外层/3非空三float、15枚举符号；真实分发先到1507AD120，空上下文路径→内部Handler→clone等待，config105/正常阶段记录明确止步；11新增/305回归、原39/33摘要不变、二次一致 | 其余5类非空嵌套、正常阶段记录后消费/业务字段编码、包装有上下文和真实调度；父任务/实机不晋级 |
| B-02e3 阶段计时纠名与正常场景消费 | B-02e2、已有85949阶段枚举 | 阶段原生工具/回归、正常分支证据与文档 | 重新核对14C8AE9A0的85949参数/OnPlayerEnterSceneNotify=10，实际原生开关/长度/时钟写入，纠正前批identity request误名；继续原reader对象至正常分支的加载任务/字段消费，明确存储与外部模型，非零/64位/嵌套/list/错误边界保留，未验语义不晋级、重复一致 | 已完成 | caaf8ad/cd13b15；计时13/正常14原生样本双跑一致，15新增测试，327完整研究回归通过；同reader→stage10→原生task/dictionary→153A18E80入口止，64位/list/string不变，旧39/33/32保留 | B-02e4恢复查询及零/非零分支；timer/完整回收/其余nested/业务字段与实机仍未验 |
| B-02e4 场景查询原生来源与双分支 | B-02e3正常分支证据、固定7.2输入 | 新scene-query工具/测试、来源/摘要与专题 | 核对153A18E80/153A357D0/156632D10声明/代码/返回字段，明确getter初始化与字典存储来源；原生查询覆盖无key/zero/nonzero/高位/碰撞/错误，接回同reader正常消费的两分支，分别验证原列表/decoded key传给153A3CB70或15881E210的原始参数，至少到其明确入口，不模拟未知后段；重复一致与既有回归，业务未知不晋级 | 已完成 | af573b9；19原生/10新增/337研究回归、双跑一致，旧normal14/born39/wait32实际重跑不变；cache为Dictionary<uint32,uint32>，冷记录来源止decoder，两个消费者body未执行；58→59输入，原矩阵不变 | B-02e5执行列表消费者；正式return声明/业务名、真实冷初始化/decoder和实机保持未知 |
| B-02e5 场景列表两消费者限定执行 | B-02e4两分支/原列表来源 | 新scene-list原生工具/测试/专题及摘要草稿 | 固定声明/泛型/完整代码来源；实际执行153A3CB70对owner+60的查找/创建/清空/列表消费及15881E210构造路径，在可证的托管接口/数组模型下覆盖非空/空/高位/重复/已有缓存与原始错误/IFix，接同reader双分支；未知后续调用明确停止，重复一致及旧19/14/32/39回归，不命名未知业务 | 已完成 | 4e0aaf8；24原生（14返回/7错误/1IFix/2fallback）、12新增、366整合研究通过，双跑与旧19/14/39/32回归一致；原数组/size/version/身份独立断言 | 继续消费者返回后的调用/69612数据消费；IEnumerable fallback和真实ABI/业务/实机未验 |
| B-02e6 入场其余非空嵌套wire | B-02e2固定外层/三float reader | 新scene-nested原生工具、Proto研究载体/独立wire、测试/摘要 | 覆盖66040/67761/34133/69612/25866及必要36137的声明与字段来源；真实writer/reader/构造及非空嵌套/列表/重复合并，非零/高位/64位外层保持、错误/长度/未知tag边界；独立protoc或手写wire比对，重复一致，未知业务不晋级 | 已完成 | CLIENT_72_SCENE_NESTED；6类15字段、13writer/24reader/3拒绝及13protoc、8新增/366研究通过，trace/摘要/Proto双跑一致，新增第63输入 | 父保护构造分发/真实EOF ABI未恢复，分配只提供独立原生构造器已观测默认存储；业务/完整入场保持未知 |
| B-02e7 69612子对象的原生消费 | B-02e5列表返回、B-02e6非空子对象 | 新scene-data消费工具/测试/专题与摘要草稿 | 核对153A16AA0签名/完整代码及正常分支从原消息+48取参来源；原reader生成69612及25866非空对象后实际执行消费者，覆盖空/非空/高位/重复/false与true实参及原始错误/IFix；能接同reader正常前缀则连续验证，未知调用先止入口，不模拟服务成功；独立存储/变换断言、双跑与原生回归，不晋级业务/实机 | 已完成 | 51f7cd2；16有限结果7/4/2/3、81聚焦全过；trace/摘要双跑，list24/query19/normal14/wait32/born39及nested/Proto回归逐字节相同；最终384研究通过 | 当前已暂停；未知14AF3D130 body/25866消费、非零query和bool后续系统仍未验，不构成完整入场 |
| D-01 补丁和连接前置 | A-01、A-04 | patch、HTTP/SDK/dispatch/密钥 | 路由/渠道/版本/加密、各钩子 ABI/布局/实际加载验证 | 进行中 | CLIENT_72_PATCH_ABI；四入口参数前缀已验，密钥/加载未验 | 字符串工厂、原生RSA/验签、实际加载与路由；商城钩子另列 |
| D-01a 命名HTTP/SDK入口参数 | A-04d1 | trace_client72_patch_abi、参数/捕获布局摘要 | 四声明static/instance/参数与原生前缀相符，12原生样本、143项研究回归、重复一致 | 已完成 | CLIENT_72_PATCH_ABI、patch-abi-trace/determinism；db5245e | 不含完整函数、RSA成功、钩子安装/加载或实机；继续D-01 |
| D-01b 真实初始化动态观察 | A-01、用户动态调试授权 | 调试工具来源/摘要、进程/模块/断点证据 | 固定客户端、核对目标进程；观察真实初始化/密钥来源，不跳过检查、不以局部命中代替实机 | 进行中 | CLIENT_72_DYNAMIC_DEBUG及startup-continuation-observations；连续连接150秒/5代码/40首次异常逐次核对与交付，结束仍运行/keys空；前轮重连事件缺口如实保留 | 保持现有目标，重新核对后观察SDK/HTTP/字符串工厂；退出根因与D-02未验，不无条件run |
| D-01b1 动态启动与RNG状态基线 | D-01b | startup-observations、动态调试文档 | 实时调试会话归属、5处原生代码、启动断点；5000字节RNG与原生初始化/独立递推匹配 | 已完成 | CLIENT_72_DYNAMIC_DEBUG；162c924 | 仅启动局部证据，继续密钥/SDK，不替代D-01b/D-02 |
| D-01b2 真实全局与UTF-8缓存ABI | D-01b1 | startup-runtime-observations、动态调试文档 | 两次实际运行的缓存同对象与3虚槽分别匹配目标Encoding/UTF8Encoding声明；追加4处32字节代码核对，启动/退出与调试器边界明确 | 已完成 | startup-runtime-observations.json；两次3槽匹配，运行全局/标志已记录；cf09603 | 启动仍退出，原因unknown；D-01b整体继续，另推进离线Login/响应 |
| D-02 最小入场 | C-01、D-01、E-01 | 隔离配置、日志、验收报告 | 新号选人、旧测试号登录、初始化/场景握手、心跳、退出重登实测 | 待开始 | 无客户端验收；— | 前置满足后停旧服、构建隔离部署 |
| E-01 最小资源集 | A-01、A-03 | resources 来源、ID 差异/引用报告 | 固定 7.2 来源，必需表/脚本/文本/场景齐全，不覆盖既有修改 | 进行中 | CLIENT_72_RESOURCE_CANDIDATE；现有运行资源仍为7.1混合来源 | 固定候选已取得；继续revision、最小表/脚本/场景对齐 |
| E-01a 固定资源候选与七表审计 | A-01 | audit_resource72、resource-candidate-observations | Git tree/blob/大小/SHA-256链、稳定ID差异、849正引用/必要ID、7项拒绝/边界测试与重复一致；不冒充资源ready | 已完成 | CLIENT_72_RESOURCE_CANDIDATE、resource-candidate-audit/determinism；本项提交 | E-01所需revision/场景/Lua与其他资源继续验证 |
| E-01b 固定scene3候选与服务端读取依赖 | E-01a、A-03源码索引 | audit_scene_resource72、读取依赖/差异审计、回归/摘要 | 沿固定commit/root/BinOutput/Scene核验scene3实际数据；按当前加载器列必需文件/字段/引用与Lua来源，缺失或未知不以旧资源替代；独立拒绝/边界测试及重复一致，不修改运行资源、不声称E-01/实机通过 | 已完成 | CLIENT_72_SCENE_RESOURCES；1823点/2817正引用无缺失，9源码hash、7新增/14资源测试、二次一致 | 按用户允许参考既有实现迁移Lua，继续运行时revision/其余资源与实际加载；E-01不晋级 |
| E-01c 参考既有实现迁移scene3脚本子集 | E-01b、用户允许参考7.1迁移 | audit_scene_scripts72/Client72SceneScriptAudit、固定旧脚本/目标7.2表、适配及回归 | 明确继承来源；实际Lua顶层/群组/Common求值、7.2 ID与ScriptLib接口核对；按真实索引/可见性扩展并逐项适配、回归；保留失败/未知，不凭旧脚本存在即称目标导出或实机通过 | 进行中 | CLIENT_72_SCENE_SCRIPT_MIGRATION；当前JAR下出生686组/5492正ID无缺，64块13,616静态组inline全过、fast407失败；8缺ID；反射61缺成员中2包装已注册、59未见入口涉及1468组；生产序列化问题与owner UID已修 | 继续fastRequire词法路径、其余接口真实契约、vision/revision与实际场景加载；不以离线顶层/列表消费晋级父项 |
| E-01c2 修复实际Lua序列化类型/字段丢失 | E-01c生产序列化审计、用户允许迁移 | LuaSerializer、SceneRegion/Monster/Gadget最小修复、生产JUnit与审计复现 | 先重现5组套件数值字符串污染、1210半径截断及两个布尔setter漏绑；保留字符串/数值/小数/真假边界与真实套件绑定/区域包含断言；只修已复现语义，审计失败不得删除或改Lua掩盖，构建/便携部署按前置如实核查，旧数据与他人资源差异保留 | 已完成 | 9408f41四处最小修复；7项先6失败后全过、13,616组/20,871套件双跑，截断/污染/漏绑消除仅余8缺ID；327研究/580 Java全过，便携JAR 4dd1c47a…bbe7，专题CLIENT_72_SCENE_SERIALIZATION_FIXES | 完整7.2前置未齐故不部署；E-01c3接口来源、fastRequire/revision/vision及实机继续，E-01c父项不晋级 |
| E-01c3 固定脚本API缺口与实现边界 | E-01c全静态群组摘要、固定继承Lua与当前ScriptLib源码 | 独立API来源审计/测试、摘要草稿与专题 | 重验固定脚本manifest与ScriptLib源码，逐个追溯62个缺成员的实际文件/行/原文及涉及组，区分词法引用、可证调用和动态开放边界；核对当前实现/近名方法，仅给有来源的复用候选，不按名称补空业务；独立注释/字符串/动态引用边界测试、两次结果一致，未知保留 | 已完成 | 52f1945；13,785文件/171名逐13,616组reconcile0；历史62中2个userdata包装已注册，60未见入口涉及1469组；1390位置/19源码、9新增，358当前研究选集通过，双跑一致；59→60基线输入 | E-01c4只迁移有42处参数/owner UID来源支持的GetSceneOwnerUid；其余业务/错误契约及真实触发仍未知，父项不晋级 |
| E-01c4 场景owner UID只读查询迁移 | E-01c3固定42处调用/317组、Scene/World/Player来源 | ScriptLib.GetSceneOwnerUid、实际Lua桥接回归与迁移说明 | 先复现真实ScriptLibUserdata缺方法；只实现当前Scene→World.host→Player.uid的无业务参数查询，实际Luacontext调用检查owner与当前/其他玩家不同、UID非零/边界及传GetAvatarEntityIdByUid的正常消费；不补GetGadgetConfigId或猜无效ID/缺owner返回，复用既有上下文拒绝；源码/JAR/依赖证据由主线复核、构建和相应回归，前置未齐记录不部署 | 已完成 | 69c3672；唯一4行、真实userdata9次先4失败后全过；旧220方法不变；24秒便携构建2566f466…83fe、三脚本CLI和当前API/依赖双跑、366研究全过 | 原三层不晋级；正常只读契约完成，完整7.2前置未齐故不部署；GetGadgetConfigId/其余59接口、fastRequire与资源/实机继续 |
| E-01c5 生产Lua加载路径与词法作用域 | E-01c固定输入、当前inline成功/fast407错误、新JAR | 独立生产加载审计/测试/专题及配置建议 | 使用隔离配置/data、固定只读脚本和当前JAR实际ScriptLoader.init/getScript/createBindings/eval及必要SceneGroup.load；两独立JVM对比fast/inline，先验证代表性local defs复现与真实吞错/表缺失，再按13,616静态组扩展；不执行trigger/action、不启manager线程/DB/服务器，不补空API或改Lua；报告实际对象/套件、差异/未知及重复性，配置变更另由主线核对 | 已完成 | 9aa8ca7；8实际JVM测试，13,616组两模式各两轮；fast437组/481错误、inline0，540 binding/453 typed差异；原失败保留、canonical hash修正后报告/摘要字节一致 | 当前已暂停；根fastRequire仍true，未修RequireFunction/部署；建议与真实运行配置变更另验，不外推任意加载顺序/完整原环境/实机 |
| E-02 基础玩法/保存 | D-02 | 移动/实体/队伍/战斗/背包/角色 | 受控操作、重登、重启冷读，无重复收支，逐项实机验证 | 待开始 | — | 先移动、切队、传送 |
| E-03 既有玩法 | E-02 | 任务/副本/深渊/商城/抽卡/邮件/家园 | 分功能请求/响应/通知、资源和保存闭环及游戏验收 | 待开始 | — | 按专题拆分子任务 |
| E-04 7.2 新内容 | E-01、E-02 | 新资源/处理逻辑、支持表 | 来源可追溯、ID 引用、逐内容实测及缺口 | 待开始 | — | 先产出新增内容差异 |
| ENV-01 隔离便携研发基线 | A-01 | 本机配置、构建和部署报告 | 独立库/数据、旧数据保留、正常停服重建、健康/TCP/UDP/PID/JAR哈希核对；不冒充7.2 | 已完成 | docs/CLIENT_72_RESEARCH_VALIDATION.md、evidence/deployment.json；68d1732 | 目标协议前置齐全后再做D-02 |
| V-01 全量回归失败归因与修复 | DOC-01 | 失败套件与保存夹具 | 每项有复现/根因/修复回归，保留经济/保存不变量；完整集重新验证 | 进行中 | 首次10563项中217失败；本次221项选集全过，未重跑完整集 | 其余失败逐项归因，含家具天赋资源路径；本批已停止 |
| V-01a 修复能力测试便携资源路径 | V-01复现 | SkillReadyPredicateTest | 两处硬编码路径改为配置/相对根，原35项断言不删不跳过并通过 | 已完成 | logs/skill-ready-portable.log，35/0/0；68d1732 | 纳入后续全量回归 |
| V-01b 家具保存夹具 | A-03a收尾、V-01复现 | FurnitureStartFlowTest/FurnitureStartRecoveryTest及必要测试夹具 | 先重现save-only代理失败；独立Mongo/随机测试库，物品/玩家/家园正式保存与冷读；保留扣费/失败阶段/防重入/容量/预留ID/重复领取断言，相关套件无失败跳过；不改生产语义 | 已完成 | workflow-fixture-summary.json；家具96/0/0/0；03f2a99、b53fda2 | V-01c亦已完成；全量集未重跑 |
| V-01c 加工手册夹具 | V-01b | CompoundHandbookTest及必要测试夹具 | 先复现；使用正式条件保存和独立Mongo/随机库，保留加工扣费/产出/手册/失败重试不重复收支及冷读断言，相关套件无失败跳过；不以空save/删断言修绿 | 已完成 | workflow-fixture-summary.json；18项及共221项回归全过；8dd87a3 | A-03b/c亦已完成并停止；V-01整体未完成 |
| V-01d 家具资源测试便携路径 | C-01a扩展Guard实际失败 | FurnitureStartFlowTest | 复现默认/resources缺文件；保持LUNAGC_TEST_RESOURCES优先，再用LUNAGC_RESOURCES_DIR或当前resources；原全部配方/扣费/容量断言不删不跳过，家具/经济保存选集重跑 | 已完成 | 初次467项中1项路径失败；修后家具原31项及关联110项全部通过，无失败/错误/跳过；born-changed-regression.log | 仅改测试路径，完整V-01未完成；最终Guard选集在C-01a汇总 |

## 阻塞与尝试

- B-02：PingRsp歧义、四个登录身份/CmdID、35字段及8枚举102符号已闭合；请求writer与响应非空对象图已恢复。两请求均在指定环境构造并到封包入口，Token完整原生seed helper已合并；响应tag477/server_rand_key和tag208/sign现已由wire接入原生密码学和密钥安装，成功使用明确本地签名信任输入。SDK真实初始化、完整Handler、封包函数体、其余字段与P0闭包未完成。原opaque实验保留历史限定范围，不代替本批证据，也不自动扩展未知字段。
- D-01：四个命名HTTP/SDK/Marshal入口已有声明与原生参数前缀证据；seed原生RSA/公钥验签已有离线执行和独立对照。字符串工厂完成、本地服务密钥/签名替换方案、P0钩子实际加载及连接仍未验。不能用前缀或旧签名返回值覆盖替代验收。
- E-01：运行资源仍为7.1混合来源。已固定7.2上游候选并核验七表/849引用，但其revision标签与本机内置文件数值集不同；热更新状态、必需Lua/场景/其余引用未闭合。不得覆盖资源或靠修改版本文字解除。
- E-01b：固定Scene/Point子树和scene3的1823点、2817正引用已审计；当前读取源码9份受hash约束。上游无Scripts树，现有scene3.lua缺7.2来源；42种未消费点位字段（含7混淆列）保持原名，真实Lua闭包、revision/加载和实机缺口仍在，见CLIENT_72_SCENE_RESOURCES。
- 安装一致性已核验：7,830条全部大小/MD5匹配；zip_tmp仍存在。此结果不认证清单来源、不代表运行热修复资源齐全。

## 执行记录

- 2026-11-05：建立四文档，初查 Git/进程/端口并保存已有资源差异，重新执行客户端 audit。尚未运行本批测试/构建，不沿用历史数量。

- 本批研究：安装全清单通过，目标静态提取与协议盘点工具已实现。53项研究/原有脚本测试、8项盘点测试通过（原始日志留local）；服务端基线构建通过。独立研发数据配置已准备，首次部署因测试JVM占用UDP未通过，正常停止并等测试退出后，最终部署健康、端口与JAR哈希均核对通过。

下一项：A-04d/B-02 继续15A4E0D9A后的Handler动作、真实SDK初始化及剩余会话字段；亦可独立推进14A447E00封包体和D-01本地签名信任配置。响应密码学到密钥安装已有明确夹具证据，完整Handler/实际补丁加载仍待验。A-02动态包号已复核；独立可做A-03管理器依赖及7.2资源快照核查。B-02整体仍未达到服务端生成条件。

- 验证补充：完整Java测试集实际运行10,563项，217失败、0跳过，已保留full-test-results和full-test-summary.json，不宣称全绿。新JVM单独重跑账号创建、UID预留、登录准入、写队列、写栅栏回归通过；共133项；HTTP冒烟2项通过。便携路径修复后SkillReadyPredicate35项通过；完整集没有再次全跑，仍不得宣称全绿。最终服务健康/TCP/UDP/hash核对通过，详情见docs/CLIENT_72_RESEARCH_VALIDATION.md。

- 本批本地提交：3af414d（四文档）、34ee887（目标提取/安装/盘点）、99d5a6c（映射证据模型）。68d1732（便携测试修复/最终验收记录）；均未推送。72项Python检查分批通过；所有7.2游戏内验收仍未执行。

- 收尾：测试MongoDB已通过管理命令正常关闭，测试数据保留；运行MongoDB与隔离研发服务继续运行。原资源子模块修改保留且未提交。四文档已同步，本批实际证据和完整失败清单可从Harness接手。

- A-04续推：2003432完成泛型/父类/静态布局，333b628确认getter网络用途与4,012条静态绑定；c9abee3完成B-02a，恢复2个消息14字段及研究Proto，加入5个登录/心跳业务候选和包头证据。当前实际37项工具/映射与9项独立wire测试通过，普通映射校验通过、ready按预期失败。包号表及wire/Proto重复生成逐字节一致。仅离线工具/研究协议/文档变更，无服务端构建部署要求；7.2实机仍全部待验收。旧资源子模块差异继续保留，patch无修改，全部本地提交未推送。

- A-04d续推：ce9e3ae完成参数/Action回调参数，b87fe73锁定PingRsp/25763及3字段；4b15d73将TokenReq保护writer离线完整恢复28字段并通过3输入/protoc比较。LoginReq另有52字段实验输出、缺3嵌套，响应入口已到达已知原生检查。当前47项工具/映射、11项包头/心跳wire及Token的3比较+1独立断言通过，ready仍失败。仅离线研发，无服务端重启或7.2实机验收；三个功能提交均未推送，资源原始修改保留。

- A-04d3a：LoginReq恢复全部55字段，6个bool逐个消歧；展开12消息/83字段及两组oneof全部分支。50次原生/protoc比较、3独立wire断言、52项工具回归通过；二次报告/映射/Proto一致。研究协议不接入运行，客户端仍待验收。下一项为TokenRsp/LoginRsp受控读取模型。

- A-04d3b：两个受保护响应reader完整返回，TokenRsp 41/LoginRsp 44外层字段全部定位；256扫描、11受控验证和10模型拒绝通过。原始观察接入三层候选表，63项工具回归通过、普通校验通过、ready按预期退出1。嵌套/map/消息列表仅空envelope，保留not-traced；不生成服务端Proto、不重启运行服务。下一项A-04d3c共享NNKFJMDCCMB；原资源子模块差异保留。

- A-04d3c1继续推进：7个新叶子的30字段双向恢复，发现GHANJBIKPDH的两个int32存储实际为sfixed32（13/15），以原生fixed32读写和独立wire锁定。26writer/27reader/26protoc比较及2独立断言通过、三产物重复一致；当前64项回归通过。父响应非空嵌套/容器仍待验证，A-04d3c保持进行中。

- 本轮功能提交：606ea03（A-04d3a Login完整writer）、91d9791（A-04d3b 响应外层reader/候选映射）、5fe24ad（A-04d3c1 七叶子双向wire），全部仅本地。服务端运行代码/主Proto/客户端未修改，无部署或重启；7.2新旧号与入场仍待验收。

- A-04d3c当前：已沿原生ReadMessage/PushLimit/PopLimit完成28组父响应嵌套/map/list/oneof样本与28组protoc编码输入验证，6类拒绝路径通过；正在补充同分支/切换分支边界及最终回归。AOT前置已独立验收，A-04d3c整体暂不标完成。

- A-04d3c完成：原生ReadMessage/limits、18消息137字段研究闭包、21条非空内容绑定；34组独立wire及34组protoc输入通过，6组拒绝（5条原生异常路径/1条helper边界）通过。map重复entry和重复value覆盖、普通消息/同case合并、oneof换case清除均有执行证据；75项回归通过。合成托管分配/RGCTX/字典底层模型明确记录，不能代替客户端验收。

- A-04d4/B-02c本轮：a9ba540恢复目标literal/default表；扩展为7枚举86符号，4原生literal解码和16原生常量读取通过。17组原生语义检查（4分派、6wire错误前缀、4共享包装、1响应全局消费、2请求来源到wire）通过；两个响应业务名/CmdID与20字段晋级，两个请求仍candidate。87项工具/映射回归通过，普通校验通过、ready仍失败。仅离线研发，无运行代码/主Proto/配置/客户端修改，未重启；实机全部待验收，原resources差异保留。

- 确定性复核：login-semantics trace/摘要、literal/enum观察表四份产物二次运行逐字节相同，摘要见evidence/login-semantics-determinism.json。研究校验实际退出0，ready实际退出1。

- 本地提交：a9ba540（A-04d4a标记/常量）、010f0c4（A-04d4b响应身份/B-02c1字段来源及语言域扩展）；未推送。A-04d/B-02整体保持进行中，未宣称7.2可登录。

- A-04d5a：重新核对portable/GAME_PATH/研发库与Java/MongoDB进程归属，无客户端进程，保留resources原差异。解密后seed原生片段12组与现有Java MT64逐字节一致、6组短输入拒绝，91项工具回归通过，trace/摘要重复一致。未更改运行代码/主Proto/配置/补丁，无重启要求。当前继续会话token保存与Login调用参数、保护续段；完整解密/验签与D-02仍未验收，B-02不得晋级ready。

- B-02c2：完成4组独立wire→会话保存→Login参数→完整writer检查和1组缺会话原生错误；2个token字段加入映射，累计22字段有来源。95项工具回归通过、普通校验通过、ready仍拒绝。Login保护续段已进一步观察旧Shell byte[]路径及ByteString包装，环境结果明确为受控输入；新Shell路径仍停在原生SDK内部。下一项为继续151213AC6续段到151214D20发送点，并关联TokenReq构造/响应注册；不把两请求晋级为完整已适配。仅离线研发，无部署，resources原修改保留。

- A-04d5b：新恢复目标登录阶段枚举NEKEJMEIJBF，原生值OnLoginReq/Rsp=6/7、OnPlayerTokenReq/Rsp=8/9，与原生构造分配/声明类型Clone/CopyFrom、响应前缀、getter共同确认两个请求身份与24604/23172。6组原生检查、4literal/22常量和100项回归通过，identity产物重复一致；四个登录身份闭合但剩余字段/完整构造/密码学/实机均未完成。B-02仍不ready，D-02仍待前置。未修改运行服务或原resources差异。

- A-04d5c/B-02c3：正式工具贯通151213730保护构造到151214D20，限定旧Shell、非云和列明环境模型；3个SDK字段uapc/channel_id/sub_channel_id进入三层表，累计25字段。3组原生构造/完整writer样本，105项回归及确定性复跑通过；原始Shell字段624/1105来源有证据但业务语义仍未知。仅离线研发，未重启、未修改资源、未实机验收。

- E-01来源续查：本机未找到独立animegamedata2/YuanShenResources目录。公开上游DimbreathBot/AnimeGameData固定提交d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1声明CNRELWin7.2.0_R49086080_S49210135_D49230962；已取提交/根树摘要到evidence/resource-source-{heads,tree}.json。这是可验证候选，尚未核对本机revision/表字段、脚本/场景或最小资源引用，不解除E-01；未覆盖resources。

- A-04d5d：恢复client seed原生来源与14587D740保存；10组受控Random.NextDouble、两套置换表通过，既有12密钥对照/6拒绝复跑通过，106项回归及确定性检查通过。Token密文构造进入Shell操作0x64后仍有SDK状态前置缺口，未给未知调用默认成功。离线工具变更，无部署；继续可独立的资源和协议盘点任务，D-02仍未验收。

- E-01a：新增只读audit_resource72，校验固定commit→真实根tree→Excel tree→七个blob的完整性并比较稳定ID；849条选定正引用、必要选人/入场ID无缺失，记录新增/共同值/字段有无差异。内置revision与上游标签不同且Lua/场景未闭合，resourceReady=false；E-01转进行中，不标完成。7资源测试与现有106项合计113通过，真实审计重复一致。未修改/提交resources或运行配置，无部署。

- A-02/A-02b：完成17动态位置来源、37数字case域及5类开放边界复核，新增40源码hash清单与漂移拒绝工具。15项盘点/复核测试通过，重复报告一致；源码盘点标完成，运行插件/P0闭包/7.2协议仍未验收。明确C-01需校验事件后最终包、拒绝候选兜底与注册覆盖。未改运行代码、Proto、配置或资源，无部署；继续Token构造和发送队列。

- A-04d5e/B-02c4：Token构造外段在明确opaque密文helper边界下到达发送入口，SDK sub_channel_id→tag10新增为第26字段；6原始会话来源及5其他原始来源保持未知语义。4组原生/wire样本、133项回归和确定性复跑通过；ready仍拒绝。未改运行代码/配置/资源，无部署，D-02仍待前置。

- A-04d5f：两请求构造结果经原生发送器/接口/AKFCDHJBCHD实现到14A447E00封包入口，原指针和24604/23172均保持；声明参数/虚槽有目标证据。8组含uint32上界/64位时间、禁用传输/缺包装通过，138项回归和确定性通过。封包函数体/加密/socket未执行，Token密文边界仍明确未知；没有实机验收或重启。下一项可推进封包边界、D-01命名HTTP/SDK入口ABI及会话字段上游来源。

- D-01a：MakeInitialUrl/Browser.LoadURL/SDKUtil.RSAEncrypt/Marshal.PtrToStringAnsi四声明与原生参数传递交叉；12样本含force真假、byte长度/NUL/非对齐/null通过，143项研究回归和重复产物通过。D-01转进行中；未安装补丁、未执行RSA/字符串工厂完成或实际HTTP。patch/resources引用均未改，运行服务不重启，D-02仍未验收。

- 接续探索：15121DB40在明确CRT内存/栈探测模型下进入ArgumentNullException(inArray)构造入口；仍无成功密文，原因尚未闭合。已定位类型usage缓存与5个目标单元供下一批正式提取验证；未知不晋级。AGENTS.md新增编码规范为工作中出现的他人修改，已读取遵循并保留，未纳入提交。

- A-04d5g：纠正inArray异常为夹具遗漏运行时空数组；正式执行15121DB40原生公钥验签、seed加密、byte[]复制和Base64至返回。8组含未初始化、零/64位边界、不同熵、签名/公钥篡改全部通过，独立RSA/完整签名块比对及二次产物一致，146项研究回归通过。初始化/OS/托管状态明确为夹具，无验签成功替换；研究校验退出0、ready退出1。未改运行文件或部署，D-02仍未验收。下一项合并Token整体构造、响应密码学前段及真实SDK初始化；AGENTS/resources他人差异保留。

- A-04d5h/B-02c5：完整原生seed helper合入同次Token构造，原返回指针到+90/tag623、getter24604及发送至封包入口全部贯通；确认client_rand_key业务契约，累计27字段。5组含成功/零/64位上界/未初始化/坏签名；后两组外层仍发送空seed，已作为真实行为固化。149项回归及重复产物通过，独立seed工具8组重跑产物未变；研究校验通过、ready仍退出1。仅离线研发，无运行部署或客户端验收；下一项响应密码学前段、SDK初始化及封包体。本地seed提交823762e。

- 响应前段接续：15A4DE707至原生操作101已成功验证内置私钥签名并解密独立密文；私钥公钥部分匹配仓库4_Pub.der。托管SHA-256与hashlib一致，操作103公钥与请求加密公钥/签名完全相同，错误响应签名真实返回-7并进入拒绝分支。仍为忽略目录探索，待正式工具/块级轨迹/有效响应签名夹具；未晋级字段或实机状态。目标公钥与仓库SigningKey不匹配，记录为本地信任配置待处理项，不跳过验签。完整Token本地提交cdbc275；AGENTS/resources原差异保留，无运行文件改动或部署。

- A-04d5i/B-02c6：正式贯通响应原生reader→RSA解密/私钥验签→SHA256→响应验签→seed XOR/MT64安装，成功使用明确本地签名信任输入，不替换验证决定。8组含3组4096字节Java逐字节对照、错误签名/密钥签名/密文及短seed拒绝，块/指令轨迹重复一致；155项回归通过，研究校验通过、ready仍退出1。新增server_rand_key/tag477、sign/tag208，累计29字段。账号专题/补丁研究同步边界，无运行代码/Proto/补丁/资源修改或部署，D-02未验收。下一项SDK warmup/完整Handler前缀、实际本地信任配置及剩余字段。

- A-04d5j/B-02c7：从1507BE0E0连续执行至15A4E0D9A，覆盖warmup选择、完整密码学、会话UID与token保存；tag9→+D4→session+168→目标__LastUid__确定uid，累计30字段。4组含0/80000000/FFFFFFFF、空token及0/8字节warmup；新warmup分别返回-2/-1后仍继续，未冒称SDK初始化成功。157项回归、二次产物及独立response工具重构回归通过，ready仍拒绝；仅离线开发，无运行部署。上一密码学提交caa7bea；下一项后续Handler动作/剩余账号字段和D-01实际信任配置。

- A-04d5k/B-02c8：原生SDK认证回调来源检查/会话写入接入真实seed构造和Token writer，确认account_uid、account_token、channel_id、account_type、is_guest五字段，累计35字段。9组非零/高数字文本/uint32边界/缺省/拒绝通过；160项回归、二次产物和原Token5组重构回归产物一致。JSON容器/SDK值仍为明确夹具，完整回调/实际认证/封包体未执行；研究校验通过，ready仍拒绝。无运行代码/Proto/补丁/配置/资源变更或重启。上一Handler核心提交b0ca919。用户要求完成当前子任务后暂停并交接，本项收口后不再启动新任务；D-02保持待开始，恢复资料见HANDOFF.md/HANDOFF2.md。

- A-04d5l：5组认证可选字段原生样本、27项聚焦回归通过，重复trace/摘要一致；session+B0未改写，登录语义字段仍35。仅离线工具/文档，无运行部署。B0写入新线索15121297C/151229560，源对象+20；候选30677 reader tag14待正式化，不沿用旧Proto名称。

- A-04d5m：候选30677 tag14→+20→两处post-parse片段→session+B0→Token+70/tag965，14原生样本两次一致，166研究回归通过。非空保留原指针，空值会话保留但请求取默认空对象且writer省略。三层研究校验退出0、ready退出1；源类型关联/业务仍未知，登录35字段不变。运行文件未改，不部署、不以健康替代D-02。

- 用户补充要求：持续维护本任务表，按依赖逐项开发、验收、标记。本批已填46d422c/5dcf882；A-04d5n/B-02c9进行中，A-04d/B-02整体不晋级，D-02仍待真实前置和实机验收。历史执行记录保留，不将历史暂停语句当作当前指令。

- A-04d5n1：原生151210540从JSON content/sign解析、Base64、操作101解密、SHA256、操作103验签到30677真实reader，并在151211BA1确认post-parse原指针。三成功明确本地签名信任输入；坏签名/原始信任下不匹配签名/缺content拒绝。6原生样本两次一致，33聚焦/169研究回归通过，研究校验0、ready1。修正未知元素class为SHA256Managed/uint数组夹具；返回寄存器原样记录，不猜成错误码。A-04d5n/B-02c9仍进行中，无运行部署或D-02实机通过。

- A-04d5n2：同次JSON/原生密码学→原生Region嵌套读取/limits→151204AC0配置入口，8组含重复合并、非零/uint32最大、缺嵌套/负返回值/两类截断；173回归通过。trace/摘要二次逐字节一致，研究校验0、ready1。配置body/B0连续处理仍未验，业务35字段不变；仅离线工具/文档，无运行部署。下一项151204AC0实际配置解析到151211DD5，不能把任意bytes参数样本当有效JSON。

- A-04d5n3：同次分区入口执行两段配置body和原生事件分发，随后Region到全局写入，终点1512124B9。6原生样本、177研究回归、两次trace/摘要逐字节一致，研究校验0、ready1。初次全EAX断言与实际低字节返回不符，现保留RAX并只断言原生AL；未删除字段/list断言。JSON/装箱/空监听器/初始化为明确模型，SDK调用与B0连续链未验；仅离线修改、无运行部署。

- A-04d5n4：第一分区入口同次JSON/密码学/配置/SDK包装/提示→B0原指针，再复用seed运行时到Token tag965；6原生样本、182回归、两次trace/摘要一致，原Token5组trace/摘要不变。导出表/日志sink未初始化等离线夹具明确；不是实际SDK或实机成功。缺session跳过store，缺singleton/坏JSON不构造Token；业务35字段、tag965未知、ready仍1。下一项23192.MoveNext151226CD0的第二分区解析关联。仅离线修改，无运行部署。

- A-04d5n5：真实MoveNext151226CD0 state2/3恢复路径→密码学/30677 reader→第二B0 store→同运行时seed/Token。8原生样本、187回归、两次trace/摘要一致；坏签名/缺content分别到错误提示/日志入口，传输错误与state5各走实际边界。研究校验0、ready1（214未满足项）。两处分区原始关联已分别验证，tag965业务名仍未知；下一步优先核查必要登录字段，不以旧proto名称或常量4晋级。仅离线修改，无运行部署。

- A-04d5o/B-02c10：请求tag1184=4、Miscs.GetGlobalDispatchUrl命名参数及唯一匹配公钥交叉确认key_id，累计36字段。3原生请求/6原生响应，正确key4与Java4096字节对照一致，错误key2/3/5不安装密钥；192项回归、二次产物一致，研究校验0/ready1（213未满足项）。名称是业务契约证据推断；明确局部URL/本地信任/Java响应夹具，未声称完整请求解密、实际SDK、补丁或D-02。只改研究工具/映射/文档，未部署；继续Handler尾段、封包体及P0闭包。

- A-04d5p：Handler入口连续到原生robot输入setter、版本拼接、ASCII/SHA1/Base64与GLOBAL+11B90写入，终点15A4E0DC7。7原生样本、198研究回归、两次产物一致；研究校验0/ready1（213项）。注册类型核对纠正UTF8推断为ASCII，Unicode/NUL及SHA1块边界有独立断言；两响应字段仍unknown，不增加业务名称。版本来源与限定格式化/初始化缓存为显式夹具；未执行剩余Handler、Login连续链或D-02。无运行部署；下一段GameManager状态和Login调用。

- A-04d5q：连续Handler→实际Login/Full状态→Action→完整Login构造→队列/transport/getter23172→14A447E00，token/hash原指针、wire和32/64位包头核对通过。9原生样本、204回归、两次产物一致；原默认枚举输出不变。研究校验0/ready1（213项），8组已安装密钥与Java逐字节相等。未知字段不晋级，无运行部署或D-02实机验收。用户要求当前子任务完成后暂停：本项收口后不启动下一任务，完成备份式交接。

- 2026-11-05恢复推进：A-04d5r独立封包函数14A447E00完整返回；原生包头size/writer、端序、MemoryStream、XOR/清理，15样本含65536字节体、命令32767/32768/65535、uint64上界、两密钥循环和4拒绝。210研究回归及重复trace/摘要通过，研究校验0/ready1；未知业务字段未晋级。负载size/writer和密钥数组是明确输入，尚未串联上项构造/seed安装，也未执行收帧/socket/实机。仅离线工具/测试/文档，无部署。本轮配置portable、GAME_PATH已核对，Java/客户端未运行，只有本工作区MongoDB；既有AGENTS/resources差异保留。下一项收帧边界及发送链串联。

- A-04d5s：14A446CC0独立收帧18原生样本、216研究回归、两次trace/摘要一致；BitConverter原生.cctor补足小端静态初始化，包头reader/标量helper直接执行。原生错误码4/5/6/2/7及两个非法header抛错入口验证；负载在尾标记前消费，尾随字节不计入packetLength。factory/负载reader为明确模型，非真实schema/Handler/实机；普通校验0/ready1。未改运行文件，无部署。下一项串联真实登录消息和密钥来源或A-03 P0闭包。

- A-04d5t：6组同CPU原生seed→Token构造→队列→实际CalculateSize/保护writer→完整帧返回，原request和seed返回指针不变；中文/补充字符/NUL、4486字节帧跨key周期、未初始化/坏签名空seed行为通过。222研究回归及两次产物一致，校验0/ready1，未知字段不晋级。初始frame key/SDK/OS为明确输入，非实机，无运行部署。用户补充允许按实际情况动态调试，已核查本机并从官方固定发布准备x64dbg便携工具（忽略目录），实际目标调试尚未开始。

- D-01b1：用户完成管理员启动及Automate加载，按其要求停止computer-use；脚本API核对真实模块5处32字节，经过TLS/PE入口停1411267B0，Global尚未初始化。RNG状态5000字节与1408AF2A0原生初始化及独立seed103递推相同；Ec2b单组探索输出与仓库dispatchKey相同但尚未正式验收。目标当前保留调试暂停，未连猜测协议服务；D-02未完成，整体目标active。详见CLIENT_72_DYNAMIC_DEBUG。

- A-04d5u：16组原生Ec2b含raw/managed包装，固定seed/key逐字节、地址变化/缓存重复一致，扰动输出改变，错误输入不写输出/不分配。原生初始化与live5000字节和独立递推相符；228回归、二次trace/摘要一致，校验0/ready1。长度参数仅检查正值，短声明测试保留完整2076字节底层存储，不声称真实截断数组安全。动态调试另核实1411267B0→1411268C1原生注册指针写入，当前暂停ret，未声称SDK完成；下一项分区来源→key槽/帧。无运行部署。

- 2026-11-06 A-04d5v：9块RSA分区响应经原生密码学/reader→三次Ec2b→两配置XOR/清槽→第三次key原指针保留→真实Token封包，4样本覆盖5314字节跨周期/Unicode/NUL/空key/错误魔数；234回归与二次产物一致，原三工具各6样本产物不变，校验0/ready1（213项）。配置模型原本读旧密文缓存，已改读实际内存；没有改客户端或跳校验。分区→Token调度器、业务未知字段和D-02不晋级。动态会话已将首次异常交给应用，现停mhypbase TLS，Global仍0；无运行代码修改或部署。下一项Login/响应/SDK及P0闭包。
- D-01b2：真实Global建立，UTF-8两缓存同对象与3虚槽在两次运行均匹配声明，新增4处代码各32字节相同；key槽仍空，完整SDK/登录未验。纠正erun语义，改为con 1+run；实证研究hardware watch表删除后寄存器残留，清理自设watch后启动走得更远，但恢复软件观察仍以80000003退出，原因unknown。最后目标已结束，调试器保留；下一项离线Login/响应或退出机制独立核查，不部署猜测协议。
- A-04d5w：10样本连续Handler→响应安装key→实际Login/Full→Login构造/队列→原生外层/68918嵌套size与writer→完整帧；4发送/6不发送，4560字节Unicode/NUL跨周期、32/64位包头，完整payload逐字节与独立protoc输入一致，9组key与Java一致。240研究回归、二次trace/摘要一致，Token原6组产物不变，校验0/ready1（213项）。实际执行ByteString写入和native byte copy，不借用opaque payload；未知语义不晋级，无运行部署。下一项A-03/P0闭包或真实响应收帧。

- A-03a：JDK17实际语法解析固定12源码/9根/80方法，96边中13人工复核、83同owner名称/参数数量候选；1012未关联位置保留，61直接构造参数类型名及1间接工厂名不当作完整或最小必需包集。8新增/248研究回归、二次报告/摘要一致，校验0/ready1（213缺口）。源码新旧号/选人路径已分开；运行代码/协议/数据库/补丁不变，无部署或新增实机验收。用户要求完成当前项后暂停并按流程交接；恢复后继续管理器/事件/脚本边界，A-03与D-02不晋级。


2026-11-06 A-03a收尾：重新执行固定12源码/JDK17解析、8项聚焦测试和研究映射校验均通过；重生成摘要与已提交产物逐字节一致，ready仍退出1（213缺口）。`local/migration72/evidence/login-source-close{,-summary,-check,-ready}.json`为本机复核证据。本次用户指令覆盖旧暂停状态，依次推进V-01b/V-01c/A-03b/A-03c，A-03c提交后停止；禁止B-03/C-01/D-02及实机调试。A-03整体仍待完整闭包和实机复核。

2026-11-06 V-01b：家具两套夹具由save-only代理改为独立Mongo真实Player/Item Guard及GameHome保存，故障计数只消费同步确认；测试专用home/库存/回调不入BSON。原31+34=65项通过（失败/错误/跳过均0），六阶段故障、防重复扣费、队列/回执和模糊确认恢复断言保留；新增材料及家园冷读。复现前家具65项中40失败，原始报告fixture-before-test-results/，修复后furniture-fixture-test-results/与logs/furniture-fixture-after.log（均在local/migration72）。只改测试，无生产/Proto/配置变更，无部署或客户端验收；V-01完整集尚未重跑，不推算剩余失败数量。

V-01b补验：共用本夹具的FurniturePaidStartRecoveryTest原31项中27失败，本次31项全过。三个真实Mongo/确认丢失样本现在复用夹具隔离库，显式重新绑定代理目标的Item Guard，按第二次家园同步确认注入故障；保留冷读付款、重试和整批队列断言。共用GuardedPlayerTestStore映射选项对齐生产storeEmpties(true)/storeNulls(false)，避免空队列被夹具省略。三套家具合计96项全过；本机workflow-fixture-test-results/、workflow-fixture-summary.json及logs/workflow-fixture-regression.log。扩查FurnitureTalentPlanTest仍因硬编码/resources初始化失败（历史全量同因），属于V-01未修范围，未删/跳过或宣称全量通过。

2026-11-06 V-01c：加工手册夹具改用真实Player/Item Guard及隔离Mongo，保留原18项断言和同步故障阶段；ProcessPlayer专用库存/标志/回调排除出BSON。新增真实手册进度、产物、未重复扣原料及失败后预留冷读，明确内存进度1而失败确认后的持久进度0，重试仍拒绝重复发放。修复前18项中12失败，现18项全过；与加工队列35、家具96、Player/Item保存保护45、手册领奖27合计221项通过，失败/错误/跳过均0，证据workflow-fixture-test-results/、evidence/workflow-fixture-summary.json及logs/workflow-fixture-regression.log（local/migration72）。原断言未删未放宽；未改生产逻辑，无部署/实机验收。完整Java集未重跑，历史217失败不据此扣减。

2026-11-06 A-03b限定源码复核完成，见[登录管理器与事件依赖](docs/CLIENT_72_LOGIN_DEPENDENCIES.md)。48固定源码、293方法、409边（72人工调用位置/337候选），2868未关联保留；10处插件/替包/异步/脚本/服务开放锚点均未晋级。枚举合成跨度实际复现已修正，A-03a计数不变。14聚焦/254研究回归通过，两次报告/摘要相同。下一项A-03c候选消息/Proto及7.2三层缺口；A-03整体仍待完整闭包/实机。无运行变更、部署或实机调试。

2026-11-06 A-03c限定离线验收完成，见[候选消息/Proto与三层缺口矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md)：97包构造、148引用/种子、1139文件描述符依赖，缺DoSetPlayerBornDataNotify.proto如实保留。原三层状态及185字段不变；6新增/260研究回归、重复矩阵通过，研究校验0/ready1（213缺口）。V-01b/c经济/保存221项通过；完整Java集未重跑。按用户要求到此停止并交接Astra，下一研究为B-02选人三件组的独立7.2三层证据；A-03整体仍待完整闭包/实机。未改运行文件、未部署或调试。

交接收口：A-03c验收提交411d522后停止，无下一研究动作。旧HANDOFF2先备份并逐字节/hash核验，HANDOFF仅追加；详细入口见[HANDOFF2](HANDOFF2.md)。测试Mongo已正常关闭，原Mongo/数据/他人差异保留；无运行部署或客户端调试。

2026-11-06 B-02d：仅选人三件组研究闭合；5008/PMNMDAIKALO（目标确认空schema）、8541/JNJOMANHPCD（nick_name4、avatar_id11）、414/EODFLMDBPOG（retcode15）。39组限定原生执行、6聚焦与266研究回归全过，二次trace/摘要一致；三层只晋级这三条和3字段，204项ready缺口保留。原生消息reader错误来自显式WireInput边界，昵称validator只到入口，未绕过/伪造成功；具体限制与Sol实现规格见CLIENT_72_BORN。没有修改生产Java/Proto/配置/补丁/资源，无部署，Java与客户端未运行；Mongo27017和现有调试器未操作。已到用户停止点，后续只做文档/本地提交/备份交接。

B-02d收口：验收提交36eb486；先建卡3fc7e80。旧HANDOFF2已先备份并核验，详细停止状态与Sol规格入口见HANDOFF2，HANDOFF只追加。本轮研究已停止，未推送。

2026-11-06 B-03a/C-01a：先建卡d2cfb14，生成及生产wire接管c6bf035；V-01d先复现路径失败建卡87cda4c、修复9fe3bb2；业务/保存及源码矩阵复核8bd338f。最终21套495项Java与272项研究回归通过，失败/错误/跳过均0，普通校验0/ready1（204缺口）。便携入口-Mode build退出0，JAR10个改动/生成类逐字节匹配、嵌入代码提交8bd338f且无旧配置/默认旅行者方法。独立Mongo测试库清理后仅admin/config/local，正常关闭测试实例；原Mongo/存档/运行数据/他人修改保留。未部署，HTTP/游戏端口无监听，无Java/游戏进程；健康/运行JAR校验无运行对象，不冒称通过。已完成所有本批独立项，遇完整成功继续与真实必需消息序列的客户端取证缺口，整理B-02e后停止；父任务/实机/V-01全量不晋级。按规范先备份HANDOFF2并核对字节/hash，再覆盖详交接、只追加HANDOFF。

- 2026-11-06按用户新指令收尾暂停：B-02e7（51f7cd2）及E-01c5（9aa8ca7）限定完成，384研究回归0失败/错误/跳过；63→65证据输入只增hash、不晋级三层/runtime，旧review实际拒绝后人工复核并recover双跑。真实生产fast437/481与旧模型407口径分开；config仍true，运行前置未齐未部署。收尾证据scene-pause-{research-regression-summary,login-proto-review-check,final-runtime-check,stage-runtime-readonly}.json及同前缀日志留忽略目录；交接后不启动下一项。
