# 国服 7.2 任务与验收

更新：2026-11-05。仅面向 7.2；主仓库起点 d372f62（7.2-dev）。状态只用：待开始 / 进行中 / 阻塞 / 已完成。全部验收条件满足才完成。

## 当前阶段

静态结构、Token/Login原始wire及响应非空对象图已离线恢复；四个登录请求/响应业务身份与CmdID、26个字段来源、8枚举102符号现有目标证据。解密后seed与Java密钥逐字节一致，token保存/参数链已分段验证，Login构造在明确旧Shell/非云夹具中已到发送入口。Token完整构造/发送、解密验签及剩余字段继续研究，A-04d/B-02保持进行中。隔离研发服务已部署，但没有7.2可连接构建，客户端验收全部待执行。本机路径、初始差异、报告和日志在忽略的 local/migration72/；公共摘要放 docs/ 和 protocol/cn-7.2/。

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
| A-04 7.2 提取器 | A-01 | extract_client72、profile、研究记录 | 类型/成员/泛型/父类/布局/注册与CmdID候选有证据，边界及抽样验证 | 进行中 | docs/CLIENT_72_EXTRACTION.md；— | 保护入口/回调参数及运行时闭合泛型ABI仍需恢复 |
| A-04a 目标名称/字段/方法基础提取 | A-01 | extract_client72、7项测试 | 三份目标哈希守卫；独立名称向量；全部跨度/引用/代码地址校验；不把常量返回值标作包号 | 已完成 | client72-metadata.json、extraction-summary.json、提取研究摘要；34ee887 | 89,748类型/445,462字段/741,021方法供A-04后续分析 |
| A-04b 泛型/父类/静态布局 | A-04a | extract_client72 --structures、test_client72_structures、提取摘要 | 全泛型行/引用边界、父图无环、字段存储分类；未知布局保留未知，目标全量运行和13项测试 | 已完成 | client72-structures.json、structures-summary.json、logs/structures-tests.log；2003432 | 运行时闭合泛型和多维bounds仍需另验；供wire字段定位 |
| A-04c getter网络语义和静态包号 | A-04b | extract_client72_commands、commands.json、CLIENT_72_NETWORK | 两个发送调用点与封/解包闭环，槽/祖先/方法字向量及歧义测试，确定性提取；不冒充业务名称 | 已完成 | 网络研究摘要、commands-summary.json、commands-tests.log；333b628 | 4,012类型/4,007值；5组重号域未知；追踪业务与热修复 |
| B-01 映射模型及校验 | A-02a | protocol/cn-7.2、validate_protocol72、11项测试 | 三层状态/来源/歧义/别名，字段容器/引用及目标哈希校验，未知可保存但拒绝ready | 已完成 | protocol/cn-7.2/README.md；99d5a6c | 继续研究真实映射；拆合并语义由证据人工审批 |
| B-02 P0 三层映射 | A-03、A-04、B-01 | CmdID/message/field 表、wire fixture | 全部 P0 tag/类型/枚举/容器/嵌套有证据，非零/64 位/错误/边界测试 | 进行中 | CLIENT_72_NETWORK、CLIENT_72_SESSION；— | 四个登录身份/26字段来源已确认；继续完整构造/发送、剩余字段和P0闭包 |
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
| B-02c Token/Login身份与字段来源 | A-04d4a、A-04d3c | 登录语义观察表、native受控样本、候选表/校验 | 注册/分派/参数/目标自身标记闭合；字段来源与wire关联，未知保留，独立回归 | 进行中 | CLIENT_72_LOGIN_SEMANTICS、CLIENT_72_SESSION；— | 四个登录身份/26字段已闭合；继续其余字段与完整受控业务路径 |
| A-04d4b 两个上游响应身份 | A-04d4a、A-04d1 | trace_client72_login_semantics、literal/参数/getter/注册/分派证据 | 27808/20835实际分派、6独立wire错误前缀命中目标自身标记；4包装参数样本纠正层级 | 已完成 | CLIENT_72_LOGIN_SEMANTICS、login-semantics-trace；010f0c4 | 请求配对、完整成功/错误处理与实机仍另验 |
| B-02c1 二十字段业务来源 | A-04d3c、A-04d4b | login-semantic-observations、mappings、校验和测试 | 14请求字段两组来源到原生wire；4响应全局消费者及2错误值，未知不晋级；87项回归、确定性复跑 | 已完成 | CLIENT_72_LOGIN_SEMANTICS、login-semantics-tests.log；010f0c4 | 剩余字段/请求配对；原始枚举与业务字段命名分别管理 |
| A-04d5a 解密后seed与会话密钥 | A-04d4b | client72_session_runtime、trace_client72_session、Java独立对照 | 明确解密后边界，原生整数存储/字节序/XOR/MT64/4096字节写入；12组Java逐字节对照、6短输入原生拒绝、确定性复跑 | 已完成 | CLIENT_72_SESSION、session-seed-trace/determinism、session-all-tests.log；0f324eb | 前段解密/验签/Shell与完整Handler仍待验证 |
| B-02c2 会话token保存与Login参数 | B-02c1、A-04d5a | trace_client72_token_flow、session-token-observations、映射/校验 | 独立响应tag6→原生会话+98保存→真实调用参数→构造+D8→请求tag1；4样本+缺会话拒绝、重复/空值/高参数、确定性及95项回归 | 已完成 | CLIENT_72_SESSION、session-token-trace/determinism；0c12f2c | 请求完整发送、+1D8语义与剩余业务字段仍待验 |
| A-04d5b 请求身份与阶段配对 | A-04d4b、B-02c2 | trace_client72_request_identity、阶段枚举、映射/校验 | 阶段方法签名/原生常量/请求构造self-class/响应前缀/原生getter交叉，6组样本和100项回归、重复一致 | 已完成 | CLIENT_72_SESSION、request-identity-trace/determinism；70f3e5b | 四个登录身份已闭合，完整构造/发送及字段闭包仍待验 |
| A-04d5c / B-02c3 Login构造与SDK来源 | A-04d5b、B-02c2 | trace_client72_login_constructor、SDK字段映射与校验 | 明确旧Shell/非云输入，真实保护构造到发送入口；3组来源→对象→writer，UInt32上界/溢出/无效、未知Shell语义不晋级、105项回归和重复一致 | 已完成 | CLIENT_72_SESSION、login-constructor-trace/determinism；cde97d4 | Token构造、发送队列、其余字段及密码学前段 |
| A-04d5d client seed来源 | A-04d5a | SessionRuntime72、session-seed-trace/observations | Random.NextDouble槽/2^64常量/原生转换/同值返回与保存，10组边界输入、既有18组后段及106项回归、重复一致 | 已完成 | CLIENT_72_SESSION、client-seed-source-tests.log；e716da8 | 15121DB40的Shell操作0x64/密文构造仍未知 |
| A-04d5e / B-02c4 Token构造外段与SDK来源 | A-04d5c、A-04d5d | trace_client72_token_constructor、映射/边界校验 | 15121DB40明确opaque输入；其余构造到send入口，4组tag10来源与原始会话字段wire断言、133项回归、确定性 | 已完成 | CLIENT_72_SESSION、token-constructor-trace/determinism；本批提交 | 密文helper未执行，继续发送队列/会话来源与密码学前段 |
| B-03 确定性生成 | B-01、B-02 | generator、PacketOpcodes、Proto | 只生成核实项，别名/引用检查，重复输出一致，protoc 编译，禁止旧号兜底 | 待开始 | — | 合成模型测试，再接真实映射 |
| C-01 双向网络接入 | B-03 | GameSession、BasePacket、Handler 注册、UnionCmd/枚举 | 注册冲突失败、号/长度边界、内外分派、目标 wire 测试，构建部署 | 待开始 | — | 映射确认后替换旧表和专属断言 |
| D-01 补丁和连接前置 | A-01、A-04 | patch、HTTP/SDK/dispatch/密钥 | 路由/渠道/版本/加密、各钩子 ABI/布局/实际加载验证 | 阻塞 | 旧特征仅静态扫描，缺目标 ABI；— | 分析必需钩子，区分商城可选项 |
| D-02 最小入场 | C-01、D-01、E-01 | 隔离配置、日志、验收报告 | 新号选人、旧测试号登录、初始化/场景握手、心跳、退出重登实测 | 待开始 | 无客户端验收；— | 前置满足后停旧服、构建隔离部署 |
| E-01 最小资源集 | A-01、A-03 | resources 来源、ID 差异/引用报告 | 固定 7.2 来源，必需表/脚本/文本/场景齐全，不覆盖既有修改 | 进行中 | CLIENT_72_RESOURCE_CANDIDATE；现有运行资源仍为7.1混合来源 | 固定候选已取得；继续revision、最小表/脚本/场景对齐 |
| E-01a 固定资源候选与七表审计 | A-01 | audit_resource72、resource-candidate-observations | Git tree/blob/大小/SHA-256链、稳定ID差异、849正引用/必要ID、7项拒绝/边界测试与重复一致；不冒充资源ready | 已完成 | CLIENT_72_RESOURCE_CANDIDATE、resource-candidate-audit/determinism；本项提交 | E-01所需revision/场景/Lua与其他资源继续验证 |
| E-02 基础玩法/保存 | D-02 | 移动/实体/队伍/战斗/背包/角色 | 受控操作、重登、重启冷读，无重复收支，逐项实机验证 | 待开始 | — | 先移动、切队、传送 |
| E-03 既有玩法 | E-02 | 任务/副本/深渊/商城/抽卡/邮件/家园 | 分功能请求/响应/通知、资源和保存闭环及游戏验收 | 待开始 | — | 按专题拆分子任务 |
| E-04 7.2 新内容 | E-01、E-02 | 新资源/处理逻辑、支持表 | 来源可追溯、ID 引用、逐内容实测及缺口 | 待开始 | — | 先产出新增内容差异 |
| ENV-01 隔离便携研发基线 | A-01 | 本机配置、构建和部署报告 | 独立库/数据、旧数据保留、正常停服重建、健康/TCP/UDP/PID/JAR哈希核对；不冒充7.2 | 已完成 | docs/CLIENT_72_RESEARCH_VALIDATION.md、evidence/deployment.json；68d1732 | 目标协议前置齐全后再做D-02 |
| V-01 全量回归失败归因与修复 | DOC-01 | 失败套件与保存夹具 | 每项有复现/根因/修复回归，保留经济/保存不变量；完整集重新验证 | 进行中 | 首次10563项中217失败；代表套件复查证据；— | 优先替换家具/加工save-only代理，继续审查其他失败 |
| V-01a 修复能力测试便携资源路径 | V-01复现 | SkillReadyPredicateTest | 两处硬编码路径改为配置/相对根，原35项断言不删不跳过并通过 | 已完成 | logs/skill-ready-portable.log，35/0/0；68d1732 | 纳入后续全量回归 |

## 阻塞与尝试

- B-02：PingRsp歧义、四个登录身份/CmdID、26字段及8枚举102符号已闭合；请求writer与响应非空对象图已恢复。解密后seed、token保存/参数链已分段验证；完整构造/发送、seed前段解密/验签、其余字段与P0闭包未完成。Login指定旧Shell/非云环境已到151214D20发送入口；Token构造外段现已在15121DB40不透明边界下到发送入口；密文helper与新Shell仍有原生SDK环境缺口。不能给未知helper默认成功或自动扩展已确认字段。
- D-01：静态命中不是 ABI 证明；解除条件为 P0 钩子的参数/布局/加载和实际连接验证。
- E-01：运行资源仍为7.1混合来源。已固定7.2上游候选并核验七表/849引用，但其revision标签与本机内置文件数值集不同；热更新状态、必需Lua/场景/其余引用未闭合。不得覆盖资源或靠修改版本文字解除。
- 安装一致性已核验：7,830条全部大小/MD5匹配；zip_tmp仍存在。此结果不认证清单来源、不代表运行热修复资源齐全。

## 执行记录

- 2026-11-05：建立四文档，初查 Git/进程/端口并保存已有资源差异，重新执行客户端 audit。尚未运行本批测试/构建，不沿用历史数量。

- 本批研究：安装全清单通过，目标静态提取与协议盘点工具已实现。53项研究/原有脚本测试、8项盘点测试通过（原始日志留local）；服务端基线构建通过。独立研发数据配置已准备，首次部署因测试JVM占用UDP未通过，正常停止并等测试退出后，最终部署健康、端口与JAR哈希均核对通过。

下一项：A-04d/B-02 继续15121FF70的Token构造到151214D20发送队列、seed解密验签前段与剩余字段。Login指定环境构造、会话token链和四个登录身份已单独验收。A-02动态包号已复核；独立可做A-03管理器依赖及新找到7.2资源快照核查。B-02整体仍未达到服务端生成条件。

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
