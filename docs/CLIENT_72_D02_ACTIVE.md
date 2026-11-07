# D-02 持续开发与实机联调（2026-11-07）

用户已明确恢复研究、生产实现、回归、Windows portable 隔离部署及实际客户端验收，覆盖此前暂停、仅离线和模型阶段限制。D-02 未完成；本记录是活动计划，不是暂停交接。完整战斗、商城、深渊和新增内容不在本阶段。

## 接手与依赖

接手 HEAD `52999ce`，分支 `7.2-dev`；主仓库仅 resources 子模块既有 dirty，具体文件与哈希留本机初始清单。patch `3675ea7` clean，resources `dac2a9d` 加原差异。五份 profile 输入逐文件 SHA-256 全匹配；配置、原 mongodata/local/data 保留。当前实际 OS 清点无游戏、Java、MongoDB、调试器进程，8088/22101/27017/27027 无监听；旧会话/PID/保留 pause 不适用。初始私有证明在 `local/migration72/d02-active/main/initial-inventory.private.json`。

| 依赖 | 当前决定与验收出口 |
| --- | --- |
| 直接复用 | C-01b 网络入口修复、已验协议适配器、原生精确片段、资源/章节/保存验证和127份证据；旧测试数量不视为本轮重跑 |
| Astra / R1 | enum/cold初始化、完整Avatar构造/后缀与真实装备/技能来源；先找可追溯静态或导出输入，精确列出必要自然观察，不以片段批准g2 |
| Sol / R3 | 本机候选、固定来源commit、实际blob/exporter桥梁与revision；有独立消费者证据才允许转换，资源自然就绪后才正向QuestManager重绑 |
| Terra / R2 | HTTP/SDK路由、签名/密钥、Marshal、信任继承及补丁ABI现有合同审计；不将静态命中或旧版本请求视为真实7.2准入 |
| 主线 / R2 | 独占客户端、调试器和断点；重核持久点归属后做一次有界自然启动，捕获HTTP入口/返回或首个有效退出/停止现场 |
| 等待前置 / R4-R6 | R1/R2/R3合同齐后核完整必经消息/嵌套/资源/顺序，相关回归与生成检查；再串行构建、隔离部署、核产物/进程/端口/加载材料，最后新旧号完整实测 |

Git暂存/提交、共享Gradle、数据库、端口、构建安装由主线串行。代理仅拥有各自新工具/测试/专题和忽略输出；没有实时控制或生产sender授权。主线审核代码、原始证明并必要复验后接收。

## 首个实机实验 D-01b5g / D02-R2

- 目标：区分原软件观察点导致的观察失败与真实自然调用/退出；得到GlobalDispatch正文和同线程同栈返回，或新的准确失败现场。
- 证据：固定客户端profile、既有单HW实现与合成验证、D-01b5e旧4点精确ledger；当前现场没有target，需新管理员调试器会话。
- 未知：自然入口是否达到、先前退出原因、实际SDK/HTTP/Marshal/资源初始化；不预判为客户端保护。
- 候选解释：主模块软件字节修改影响；观察通道未交付高层事件；应用在入口前自然失败。单变量改为slot0硬件执行观察，不能据一次未命中排除所有解释。
- 最小实验：核调试器四文件hash、API/OS归属、持久库四点名字/模块/RVA/属性；复用已审观察器，以固定原始客户端新load，入口/返回同一硬件槽切换，120秒上限。运行时原始正文/寄存器/日志只留local。
- 修改范围：仅观察器自有点、已验证异常逐个交付与诊断读取；不安装补丁、不启动服务、不改原文件、参数、返回、校验或信任。
- 失败出口：未知点/异常/线程/寄存器/字节或归属不符立即停；预算届满显式pause并核清自有HW。保留新证据，不原样重复启动；按新矛盾修订假设，推进独立R1/R3工作。
- 验收：新CREATE/SYSTEM/OS/PID一致；入口/返回自然命中、原字节/GPR保持、TID/RSP关联及清点成立，或准确OS/PUB/log退出/停止及残留状态。该实验不代表SDK/补丁、部署或D-02通过。

## D-02 最终验收状态

旧测试号登录、新号显式选人命名、完整初始化与场景握手、角色实际入世界、连续心跳/会话、两类账号退出重登及归属保存，全部仍待实测。服务端LOADED、编译、健康、单帧或截图不替代这些条件。

## 已复现的观察前置问题与限定修复

新管理员调试器会话已实际建立，四个debugger文件与既有固定来源匹配。首次真实load有新CREATE/SYSTEM/OS/PID，停于系统点；尚无任何continue命令、应用停止/入口观察或HW安装时，归属检查拒绝自动TLS点。10个singleshoot点经各自live模块PE TLS数组只读核实，不能按名称认领。另3个旧自有系统终止点的持久RVA与当前系统符号不符且已被调试器禁用，旧GlobalDispatch软件点仍在。

先核当前dd64逐字节等于备份，SHA等于原e受审ledger；再按四点精确名字、模块、RVA、无条件/命令属性识别清理，不以新export地址认领旧点。实际只清四个已证自有点，10TLS+EP全部保留，完整GPR/flags/六DR逐值保持，目标仍同一暂停现场。原失败、只读PE核实、清理前后及所有hash留`main/global-dispatch-run1.json`、`preflight-readonly.private.json`、`legacy-cleanup.private.json`。

Terra修复观察器前置只保留独立TLS数组命中且singleshoot的自动点；专用`--resume-preflight-from`仅接受固定旧源码产生的本次归属拒绝、ready/OS存活暂停、无命令/停点/观察/HW操作/自有点的记录，实时重新比较PID/CIP/RSP/TID及全部非DR寄存器（flags仅既有bit1容差），不重新load。普通HW的旧resume/continue限制保持。主线审核真实diff、原始拒绝与清理证明；31项守卫通过，四核心分类/返回函数AST与接手HEAD一致，实际失败输入通过专用门槛。红测为隔离目录的旧源码+新测试重现，原始live失败另留存，不混记。

同target专用续跑已实际结束：预算120秒，38.875秒时OS/PUB/log均退出`80000004`，54停止为1系统/15精确TLS/1EP/37已匹配首机会AV，只有EP后一次slot0安装，无自然HTTP入口/返回及无高层`80000004`事件。Windows Error1000的本轮PID与创建时间匹配，fault RVA恰为`112250e0`；1001为同报告APPCRASH。这不是业务入口成功，也不足以归因保护或仅PUB丢失。继续由Astra只读分析本轮raw、Windows错误报告及固定TitanEngine/x64dbg/Automate源码，禁止原样重复load。普通权限读取WER归档已被OS拒绝，已准备只复制本轮报告的管理员helper，实际授权及执行结果待核，不能说已取得dump。

收尾只读API为无target、四类表空；新dd64保存3个当前系统RVA终止点与1个GlobalDispatch HW点，均精确登记到`main/debugger-after-hw.private.json`，下次load前必须按这份新ledger核归属。OS目标已退出，不把HW历史ledger称活跃残留，也不把API空称持久库空。

该修复仅研究观察器/测试，无生产、Proto、配置、资源或补丁变更，无需服务重启。SDK与Scene源码补充审计分别见[SDK合同](CLIENT_72_D02_SDK_ACTIVE.md)、[Scene依赖](CLIENT_72_D02_SCENE_ACTIVE.md)；SceneTeam武器非空及空MEET仍发送等源码事实不能替代目标wire和客户端消费。
