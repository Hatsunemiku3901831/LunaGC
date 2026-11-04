# 国服 7.2 任务与验收

更新：2026-11-05。仅面向 7.2；主仓库起点 d372f62（7.2-dev）。状态只用：待开始 / 进行中 / 阻塞 / 已完成。全部验收条件满足才完成。

## 当前阶段

静态泛型/父类/字段布局、getter网络语义和首批wire已恢复；阶段A保护入口与阶段B业务映射继续进行。隔离研发服务已部署，但没有7.2可连接构建，客户端验收全部待执行。本机路径、初始差异、报告和日志在忽略的 local/migration72/；公共摘要放 docs/ 和 protocol/cn-7.2/。

已有资源修改 ScriptSceneData/flat.luas.scenes.full_globals.lua.json 不属本批，不覆盖、不提交。patch 起点 f287410，resources 起点 dac2a9d。初查时Java/MongoDB归属本工作区且配置引用旧客户端/存档；随后隔离研发部署记录见ENV-01。下一次启动仍须重新核对配置与进程，不能将历史部署结果当作当前验收或清空原数据。

## 任务卡

各行含目标、依赖、文件/交付物、验收/验证、状态、证据/提交与下一步。复杂任务可拆分子 ID，不能用子项通过替代整项。

| ID / 目标 | 依赖 | 文件 / 交付物 | 验收条件 / 验证方式 | 状态 | 证据位置 / 完成提交 | 下一步 |
| --- | --- | --- | --- | --- | --- | --- |
| DOC-01 接手骨架 | 无 | AGENTS、README、Project、Harness | 职责分明、路径/便携约束正确、已知与计划分离，核对脚本和 diff | 已完成 | 四份文档；3af414d | 后续批次持续更新 |
| A-01 固定客户端和环境 | DOC-01 | audit_client、verify_client_manifest、输入摘要 | 版本/渠道/关键 SHA-256、逐清单大小/摘要、Git/进程/配置/依赖归属；明确缺件 | 已完成 | docs/CLIENT_72_EXTRACTION.md、local/migration72/evidence/manifest-full.json；34ee887 | 输入改变后重新核验；来源认证/游戏验收另列 |
| A-02 服务端协议表面 | DOC-01 | inventory_protocol、报告、旁路清单 | 实际 Handler/发送包/别名/负占位/配置/字面量全盘点，动态调用人工复核 | 进行中 | docs/CLIENT_72_PROTOCOL_INVENTORY.md；— | 追踪17个间接发送位置和数字分派 |
| A-02a 确定性静态盘点工具 | DOC-01 | inventory_protocol、8项测试 | 忽略注释/字符串、继承链排除发送类假冲突、动态项不猜测、重复输出一致 | 已完成 | server-inventory.json、inventory-tests.log；34ee887 | 作为A-02/A-03输入 |
| A-03 登录入场依赖闭包 | A-02 | P0根、调用边、Proto引用图、复核表 | 完整覆盖新旧号、选人、初始化、场景、嵌套；人工和客户端复核 | 进行中 | 静态起点114名称/471文件，见协议盘点摘要；— | 追踪管理器/事件/脚本，结合真实序列收敛 |
| A-04 7.2 提取器 | A-01 | extract_client72、profile、研究记录 | 类型/成员/泛型/父类/布局/注册与CmdID候选有证据，边界及抽样验证 | 进行中 | docs/CLIENT_72_EXTRACTION.md；— | 保护入口/回调参数及运行时闭合泛型ABI仍需恢复 |
| A-04a 目标名称/字段/方法基础提取 | A-01 | extract_client72、7项测试 | 三份目标哈希守卫；独立名称向量；全部跨度/引用/代码地址校验；不把常量返回值标作包号 | 已完成 | client72-metadata.json、extraction-summary.json、提取研究摘要；34ee887 | 89,748类型/445,462字段/741,021方法供A-04后续分析 |
| A-04b 泛型/父类/静态布局 | A-04a | extract_client72 --structures、test_client72_structures、提取摘要 | 全泛型行/引用边界、父图无环、字段存储分类；未知布局保留未知，目标全量运行和13项测试 | 已完成 | client72-structures.json、structures-summary.json、logs/structures-tests.log；2003432 | 运行时闭合泛型和多维bounds仍需另验；供wire字段定位 |
| A-04c getter网络语义和静态包号 | A-04b | extract_client72_commands、commands.json、CLIENT_72_NETWORK | 两个发送调用点与封/解包闭环，槽/祖先/方法字向量及歧义测试，确定性提取；不冒充业务名称 | 已完成 | 网络研究摘要、commands-summary.json、commands-tests.log；333b628 | 4,012类型/4,007值；5组重号域未知；追踪业务与热修复 |
| B-01 映射模型及校验 | A-02a | protocol/cn-7.2、validate_protocol72、11项测试 | 三层状态/来源/歧义/别名，字段容器/引用及目标哈希校验，未知可保存但拒绝ready | 已完成 | protocol/cn-7.2/README.md；99d5a6c | 继续研究真实映射；拆合并语义由证据人工审批 |
| B-02 P0 三层映射 | A-03、A-04、B-01 | CmdID/message/field 表、wire fixture | 全部 P0 tag/类型/枚举/容器/嵌套有证据，非零/64 位/错误/边界测试 | 进行中 | docs/CLIENT_72_NETWORK.md；— | PacketHead/Ping静态wire已固化；补PingRsp、业务字段及保护入口 |
| B-02a 包头/心跳静态wire | A-04b、A-04c、B-01 | extract_client72_wire、wire-observations、research.proto、check_client72_wire_proto | 14个实例字段tag/offset/标量/bytes与writer匹配、包头读写交叉、确定性生成、独立非零/64位/边界wire断言；业务未知保留未知 | 已完成 | CLIENT_72_NETWORK；p0-research-tests.log（37项）、wire-proto-tests.log（9项）；c9abee3 | 回调闭合前PingReq业务名仍candidate；不代替B-02或实机验收 |
| A-04d 登录保护入口与回调类型 | A-04b、A-04c | 原生控制流、方法/泛型签名、受控观察工具 | Token/Login读写真实数据流有证据，响应回调与类型匹配；不得按保护字节线性猜测 | 进行中 | CLIENT_72_SIGNATURES、CLIENT_72_PROTECTED；— | 参数/PingRsp/TokenReq离线wire已恢复；继续Login嵌套和两条响应reader |
| A-04d1 方法参数和Action回调参数 | A-04b | extract_client72_signatures、CLIENT_72_SIGNATURES、测试 | 全参数跨度连续/边界与引用校验，独立向量、BYREF及VAR/MVAR区别；闭合Action参数可复现，未恢复项明确保留 | 已完成 | client72-signatures.json、signatures-summary.json、signatures-tests.log（12项）；ce9e3ae | 555,919参数、901条Action引用/835实例；返回类型和泛型方法实例另验 |
| B-02b PingRsp静态类型/指令/wire | A-04d1、A-04c、B-02a | extract_client72_wire、mappings、研究Proto、wire断言 | 回调类型/注册/分派/getter一致，3字段tag/偏移/有符号性，负错误值独立wire断言；静态与运行状态分离 | 已完成 | CLIENT_72_NETWORK；p0-callback-tests.log（44项）、wire-proto-tests.log（11项）；b87fe73 | MJGOAHFNCGC/25763；字段业务含义仍candidate，实机待验 |
| A-04d2 TokenReq保护writer离线观测 | A-04d1 | trace_client72_token、token-wire-observations、protected-token.proto、protoc比较 | 3组受控输入完整返回，28字段全部对应/零默认，显式helper模型；重复输出一致，独立wire/protoc比较通过，业务未知不晋级 | 已完成 | CLIENT_72_PROTECTED、token-protected-trace.json、token-protoc-check.json、token-determinism.json；4b15d73 | 28字段业务名称/非ASCII/IFix/实机仍需验证 |
| A-04d3 Login嵌套与Token/Login响应reader | A-04d2 | 离线输入模型、嵌套依赖、原始观察 | Login全字段及嵌套/列表，两个响应reader受控tag/value/错误路径，独立wire与边界验证 | 进行中 | CLIENT_72_PROTECTED；analysis/protected-probe-*.json；— | Login已输出52字段，补3嵌套、6bool消歧；响应先建立reader模型 |
| B-03 确定性生成 | B-01、B-02 | generator、PacketOpcodes、Proto | 只生成核实项，别名/引用检查，重复输出一致，protoc 编译，禁止旧号兜底 | 待开始 | — | 合成模型测试，再接真实映射 |
| C-01 双向网络接入 | B-03 | GameSession、BasePacket、Handler 注册、UnionCmd/枚举 | 注册冲突失败、号/长度边界、内外分派、目标 wire 测试，构建部署 | 待开始 | — | 映射确认后替换旧表和专属断言 |
| D-01 补丁和连接前置 | A-01、A-04 | patch、HTTP/SDK/dispatch/密钥 | 路由/渠道/版本/加密、各钩子 ABI/布局/实际加载验证 | 阻塞 | 旧特征仅静态扫描，缺目标 ABI；— | 分析必需钩子，区分商城可选项 |
| D-02 最小入场 | C-01、D-01、E-01 | 隔离配置、日志、验收报告 | 新号选人、旧测试号登录、初始化/场景握手、心跳、退出重登实测 | 待开始 | 无客户端验收；— | 前置满足后停旧服、构建隔离部署 |
| E-01 最小资源集 | A-01、A-03 | resources 来源、ID 差异/引用报告 | 固定 7.2 来源，必需表/脚本/文本/场景齐全，不覆盖既有修改 | 阻塞 | resources README 为 7.1 混合资源；— | 查本机/公开来源，按稳定 ID 比较 |
| E-02 基础玩法/保存 | D-02 | 移动/实体/队伍/战斗/背包/角色 | 受控操作、重登、重启冷读，无重复收支，逐项实机验证 | 待开始 | — | 先移动、切队、传送 |
| E-03 既有玩法 | E-02 | 任务/副本/深渊/商城/抽卡/邮件/家园 | 分功能请求/响应/通知、资源和保存闭环及游戏验收 | 待开始 | — | 按专题拆分子任务 |
| E-04 7.2 新内容 | E-01、E-02 | 新资源/处理逻辑、支持表 | 来源可追溯、ID 引用、逐内容实测及缺口 | 待开始 | — | 先产出新增内容差异 |
| ENV-01 隔离便携研发基线 | A-01 | 本机配置、构建和部署报告 | 独立库/数据、旧数据保留、正常停服重建、健康/TCP/UDP/PID/JAR哈希核对；不冒充7.2 | 已完成 | docs/CLIENT_72_RESEARCH_VALIDATION.md、evidence/deployment.json；68d1732 | 目标协议前置齐全后再做D-02 |
| V-01 全量回归失败归因与修复 | DOC-01 | 失败套件与保存夹具 | 每项有复现/根因/修复回归，保留经济/保存不变量；完整集重新验证 | 进行中 | 首次10563项中217失败；代表套件复查证据；— | 优先替换家具/加工save-only代理，继续审查其他失败 |
| V-01a 修复能力测试便携资源路径 | V-01复现 | SkillReadyPredicateTest | 两处硬编码路径改为配置/相对根，原35项断言不删不跳过并通过 | 已完成 | logs/skill-ready-portable.log，35/0/0；68d1732 | 纳入后续全量回归 |

## 阻塞与尝试

- B-02：PingRsp的80候选歧义已由参数/注册/分派解除，见B-02b。登录四个核心读写入口仍需恢复；线性分析未成功，正在尝试有边界的离线CPU模拟和受控对象。解除条件：保护数据流有可复现证据，并区分模拟辅助函数与实际运行；业务字段含义继续独立确认。
- D-01：静态命中不是 ABI 证明；解除条件为 P0 钩子的参数/布局/加载和实际连接验证。
- E-01：资源声明为 7.1 混合来源；须取得并核验 7.2 必需资源及引用，修改版本文字不能解除。
- 安装一致性已核验：7,830条全部大小/MD5匹配；zip_tmp仍存在。此结果不认证清单来源、不代表运行热修复资源齐全。

## 执行记录

- 2026-11-05：建立四文档，初查 Git/进程/端口并保存已有资源差异，重新执行客户端 audit。尚未运行本批测试/构建，不沿用历史数量。

- 本批研究：安装全清单通过，目标静态提取与协议盘点工具已实现。53项研究/原有脚本测试、8项盘点测试通过（原始日志留local）；服务端基线构建通过。独立研发数据配置已准备，首次部署因测试JVM占用UDP未通过，正常停止并等测试退出后，最终部署健康、端口与JAR哈希均核对通过。

下一项：A-04d3 LoginReq嵌套/布尔消歧及Token/LoginRsp受控reader；同时沿已知构造入口确认TokenReq字段业务名。独立可做A-02动态包号、A-03管理器依赖。B-02整体仍未达到服务端生成条件。

- 验证补充：完整Java测试集实际运行10,563项，217失败、0跳过，已保留full-test-results和full-test-summary.json，不宣称全绿。新JVM单独重跑账号创建、UID预留、登录准入、写队列、写栅栏回归通过；共133项；HTTP冒烟2项通过。便携路径修复后SkillReadyPredicate35项通过；完整集没有再次全跑，仍不得宣称全绿。最终服务健康/TCP/UDP/hash核对通过，详情见docs/CLIENT_72_RESEARCH_VALIDATION.md。

- 本批本地提交：3af414d（四文档）、34ee887（目标提取/安装/盘点）、99d5a6c（映射证据模型）。68d1732（便携测试修复/最终验收记录）；均未推送。72项Python检查分批通过；所有7.2游戏内验收仍未执行。

- 收尾：测试MongoDB已通过管理命令正常关闭，测试数据保留；运行MongoDB与隔离研发服务继续运行。原资源子模块修改保留且未提交。四文档已同步，本批实际证据和完整失败清单可从Harness接手。

- A-04续推：2003432完成泛型/父类/静态布局，333b628确认getter网络用途与4,012条静态绑定；c9abee3完成B-02a，恢复2个消息14字段及研究Proto，加入5个登录/心跳业务候选和包头证据。当前实际37项工具/映射与9项独立wire测试通过，普通映射校验通过、ready按预期失败。包号表及wire/Proto重复生成逐字节一致。仅离线工具/研究协议/文档变更，无服务端构建部署要求；7.2实机仍全部待验收。旧资源子模块差异继续保留，patch无修改，全部本地提交未推送。

- A-04d续推：ce9e3ae完成参数/Action回调参数，b87fe73锁定PingRsp/25763及3字段；4b15d73将TokenReq保护writer离线完整恢复28字段并通过3输入/protoc比较。LoginReq另有52字段实验输出、缺3嵌套，响应入口已到达已知原生检查。当前47项工具/映射、11项包头/心跳wire及Token的3比较+1独立断言通过，ready仍失败。仅离线研发，无服务端重启或7.2实机验收；三个功能提交均未推送，资源原始修改保留。
