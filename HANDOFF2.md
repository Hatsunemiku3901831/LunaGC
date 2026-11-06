# AstaPS限定融合验收后暂停交接

更新：2026-11-06。用户先恢复至D-02，随后明确“目前先进行融合和维护文档，融合完成通过验收后进行暂停”。本轮F-01来源/差异和证据已足的F-02a已完成限定验收，现按最新要求暂停，不开始下一项。**完整融合父项F-02/F-03未完成，D-02实机仍未验收**；不得把本轮限定融合、编译、健康或离线通过写成实机通过。

任务/状态以[Project](Project.md)为准，复现索引见[Harness](Harness.md)，总记录见[AstaPS融合](docs/CLIENT_72_ASTAPS_FUSION.md)。接手c9aafa6；本轮本地提交73dd759（计划）、45e22ce（固定来源审计）、4324cd7（运行wire片段）。最终文档提交以git log为准，均未推送；JAR对应最后运行源码提交4324cd7，后续文档提交无需重建。

## 覆盖前备份和必须保留的约束

原HANDOFF2已先备份至`local/migration72/handoffs/HANDOFF2-before-astaps-fusion-c9aafa6-20261106.md`，SHA-256 `4e5740c018511658c273cbe609e7d529672162f2d9d95ac514aa45409d43bff2`，逐字节/hash一致后才覆盖。证明`local/migration72/evidence/fusion-handoff-backup.json`；HANDOFF.md只追加，收尾核对旧字节前缀保持。旧详细交接包含B-02e7/E-01c5原生/加载边界、动态调试弯路和历史复现，备份保留，不丢弃其未完成项。

- 仅国服7.2、Windows portable，入口tools/local_windows.ps1/.cmd；不Docker/WSL/虚拟化、不维护旧客户端运行环境。用户禁止computer-use/UI，只脚本/API。本轮未操作客户端或调试API，未安装补丁、重复load、pause/run或写内存。
- 已有管理员x64dbg/目标进程树保留。本轮只做系统进程盘点，没有重新读取is_debugging/is_running/内存；旧交接的key空/flags0不是本轮观察，恢复必须重新发现并核对归属、代码和停止原因，不能沿用旧PID/基址/暂停点或盲目run。
- 原AGENTS.md修改及resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异保留未提交；前后SHA分别为3915f755…affee2及db3e6a2e…42e5bfe，完整hash见fusion-final-runtime-check。patch f2874103ab70afc56260ee12d1eaca96247933b2、resources dac2a9d43332ef51879522cf39da2831b0ca0543，引用不变。
- 不清库、不强杀未知进程、不操作无关客户端，原mongodata/local/data及research运行数据保留。所有账号准入/稳定UID/确认保存/经济Guard、Lua序列化与owner UID修复保持。
- 三输入以extract_client72.HASHES及profile为准；已通过生成器重新读取固定目标hash。CmdID→类型、类型→语义、字段→wire分层，未知保持未知；只改Proto源，Gradle/protoc生成Java，未复制参考生成类。

## F-01：固定AstaPS来源、协议及实现差异（45e22ce）

参考MeChen618/AstaPS，`local/migration72/references/AstaPS-7.2.155`，HEAD a49f51aa6f2787ffbc25cee46801b70dee860d23，Git根树9940bf7756ce5ff8be8a836a4485a645b490c2e1；本轮开始和收尾均干净。根LICENSE实际GNU GPLv3，不能以build.gradle的Apache描述代替；ClassGraph为单独MIT依赖。本轮只比较来源，运行Proto由本地目标证据生成，未复制对方实现/Proto/生成Java。署名/许可证来源已记README和专题，后续实际采用源码须保留对应许可证。

新增audit_astaps72工具/11项测试、astaps-audit-observations摘要和协议专题。2105 Proto全树protoc3.18.1编译、8066字段与独立descriptor读取器逐字段比对0差异，报告/摘要两轮逐字节一致。全量2033非map-entry消息、147枚举、967个20000+字段/331消息；1895常量中1316正/567零/12负、13组正号重号。上游文档92零/290 parked仅“server code uses”口径，不能当全量。其三个映射脚本的旧descriptor/kitkat/NT原始输入未随提交提供，不声称复跑或独立证明映射过程。

11个本地已验身份共214外层字段，10个可比CmdID相符、130同tag同外层shape、84缺tag；不证明递归nested或业务一致。LoginRsp tag437本地client_md5、上游client_silence_md5；tag1255相反，两处采用本地已验结论。TokenReq account_type/sub_channel_id在上游仍20004/20005，本地真实15/10优先；原始tag1/5不猜对应上游parked string。PingReq的bytes tag3/9亦缺于上游。

独立非零wire `90e20903b0e20901b8e20901`经实际protoc编码和反向读取通过；参考生成Java与builder也实际读写/设置20002/20006/20007。因此parked不是发送防线，不能照搬上游“不会读写”。

实现专题/astaps-implementation-observations固定c9aafa6源码快照：64输入、Luna295/Asta288选定方法、107包候选、166 Proto种子，两仓既存imports闭包1150/1157，protoc+独立解析集合相同。它们是保守源码候选，不是完整必需首包序列；虚调用、插件、异步、Lua/活动边界仍开放。源码/hash/import/缺根/差异摘要已提交，原AST/diff/descriptor留忽略目录。

不采用：Asta忽略PlayerLoginReq/EnterSceneReadyReq payload及缺schema、自动选人/修主角、7.1两次pause启发式、RSA失败伪sign后切key、ClientIpStr被OS版本字符串覆盖、未知payload经济fallback及无关玩法/线程改动。选人三件组本仓已接管，保持确认保存后继续，不重复导入。

## F-02a：四schema/45字段wire片段（4324cd7）

只接管GetPlayerTokenReq28、PacketHead6、PingReq8、PingRsp3字段；10个已有verified业务名保留、35个使用目标clientField原名。PacketHead sequence/sent_ms保留，Ping字段业务名仍candidate；现有值从Req tag13/14向Rsp tag6/1原样回显，不据此晋级含义。3号为TokenReq24604/PingReq26605/PingRsp25763，旧混淆LCNLHAOBBFE无其他引用且占用24604，置负-124604，新增规范反向名称断言。

新generate_login72_slice在固定三输入、独立raw wire表、既有verified语义边界和已验身份下生成；拒绝hash/tag/type/容器/语义边界/CmdID漂移和新碰撞，不以Asta名字晋级。两个生成器--check均无差异。其余TokenRsp/LoginReq/LoginRsp仍是待接管遗留输入，不能启动本产物当可连接服务。

新Protocol72LoginWireCompatibilityTest使用固定原生合成183字节Token向量、独立手写wire、45字段descriptor对照、非零/uint32/uint64边界、UTF8/NUL、浮点、重复/错wire/未知嵌套与重复值、截断/错误输入、处理器回显/包头和规范号。不是同schema自编自解。旧测试只移除已由目标向量接管的两个TokenReq/Ping用例，其余3项改名UnmigratedProtocolBaselineTest，明确只是未迁入诊断，不维护旧客户端环境。

## 实际验收与证据链

- 改前新Java14项实际8失败（旧tag/号/缺字段）；改后14全过，关联选人/时钟/未迁入诊断合计4套43/0失败错误跳过，证据fusion-wire-green-summary.json。首次改后因测试直接GameSession触发Configuration/Grasscutter循环初始化，13/14通过并连带时钟失败；只让测试先Grasscutter.getConfig，未改生产初始化，首次XML/日志保留。
- 独立新test-mongo-fusion/27027下29套589账号/准入/UID/保存/经济/出生/Lua回归全过，fusion-java-regression-summary.json；不要与43相加称同一次执行，选人有重叠。测试Mongo正常shutdown，关前仅admin/config/local。原Mongo27017未动，完整Java历史10563项中217失败未重跑/消除。
- 399研究回归，失败/错误/跳过0，约46.39秒，fusion-research-regression-summary.json。包含原384及新生成4/审计11；旧生产加载/脚本研究使用固定前构建快照，见下方归档说明。
- 旧source/dependency/proto/server-surface review先实际拒绝；只更新Ping一个方法hash/原始getter和父链、Ping sender/四Proto共6源码、2旧摘要及2新增Asta摘要，65→67证据。source81方法/95边/1000未关联、人工边和开放边界不变。Proto仅PacketPingRsp候选来源和4 descriptor owner变化；1287源码/Proto、99候选/148矩阵与根/1140文件/1254声明/4786字段保持，recover双跑相同。fusion-login-proto-review-check.json及first-*-rejection保留。
- mappings原字节/规范hash与状态均未改；L1/L2各11 verified/137 unresolved，schema3 verified/8 candidate/137 unresolved；214字段仍41 verified/9 candidate/164 unresolved。结构valid=true/0错误，ready退出1/228缺口；不把rawwire接入称三层全verified或P0完整。

初次Gradle未设便携cache而尝试下载，未作为有效结果；正式测试使用local/gradle和便携JDK17，未改构建版本/protobuf依赖。一次性Proto review脚本先误认为javaType带包名前缀而断言失败，检查实际为PacketPingRsp后只纠正工具断言；未改变业务结果或放宽正式守卫。

## 构建、运行与资源边界

经tools/local_windows.ps1 -Mode build，JDK17 portable构建23秒成功。根JAR仍遗留文件名LunaGC-7.1.0.jar，SHA-256 **a8c2d89357e1c9253bbb9e982e95e075952f0b32968af3f5b0f236941a53a8f8**，内嵌4324cd7；30变化class全部与本次编译输出一致、无class增删。除4组Proto/Packet/Ping/Handler及其内联常量消费者，BuildConfig/InfoCommand/StartupArguments为git hash变化；163个Guard/Player/脚本相关class与原JAR逐字节相同。证据fusion-build-artifact.json。

旧JAR2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe先校验归档至`local/migration72/builds/LunaGC-before-fusion-2566f466.jar`，仅用于固定离线审计复现；两个scene审计CLI仍固定该旧SHA，不能用新根JAR并自动刷新守卫。对应专题已写明归档参数；不是维护旧客户端运行环境。

**没有部署/启动服务**：F-02b/F-03完整P0、D-01 SDK/信任/补丁与E-01最小资源前置未满足，用户本轮又限定融合后暂停；不得启动猜测协议。收尾无服务Java/HTTP TCP8088/game UDP22101/测试Mongo27027监听；status仅server-only只读，退出1为无服务器，健康和运行JAR无对象，磁盘构建hash不能当运行证明。原Mongo27017与调试进程树保留。本轮没有新号显式选人/旧号登录/初始化或场景握手/心跳/退出重登实机验收。

根config SHA0e94caea…75a45、fastRequire=true、portable/.env.local不存在、独立research库与runtime-data、HTTP8088/UDP22101保持；当前GAME_PATH由本机.env重新核对，三输入由生成器hash复核。机器路径/PID/命令行/日志只在fusion-final-runtime-check.json和local日志，恢复需重新发现，勿抄历史值。

## 未完成、阻塞和恢复后的下一项

当前暂停，不派发或执行下一项。用户明确恢复后先读AGENTS/README/Project/Harness/本交接/迁移与部署手册，核对dirty差异、配置/GAME_PATH/子模块/端口/进程；不要将最初的持续D-02指令解释为当前自动恢复授权。

1. F-02b优先沿PacketGetPlayerTokenRsp/PacketPlayerLoginRsp实际使用字段与既有target reader/consumer/密码学证据，对照Asta审计矩阵批量核验；采用本地MD5结论、不能按上游parked/string同型猜配。LoginReq本地55外层/12类83字段已有raw研究，但Asta没有schema，18业务字段以外仍须独立来源；不重复研究已闭合的密码学底层。补齐所用嵌套和错误/非零64位/list边界后再接管，保留账号/保存Guard。
2. F-03先从166候选/22握手根和固定raw scene证据筛必须序列；当前没有新增已verified场景语义schema。8类44raw场景字段保持研究载体，没有为无业务消费者重复生成运行类。Ready/Init/Done/Post三层、初始化完整闭包、选人后继续和真实新旧号次序仍缺；忽略请求不算空schema，不导入7.1 pause时序。
3. 原B-02e7后的14AF3D130/25866消费及SDK/补丁/资源/生产RequireFunction缺口继续存在，细节见旧HANDOFF2备份及专题；这次Asta对照未解决，不能删掉旧阻塞。优先满足实际必需链，避免无关玩法和底层重复研究。
4. D-01/E-01完整前置齐后才做F-04/F-05/D-02：独立库/数据、正常停旧服→portable构建部署→日志/健康/TCP/UDP/PID/运行JAR核对，实际新号显式选人、旧测试号、场景握手、心跳、退出重登。当前未授权启动新实机阶段；恢复再依用户范围安排。

## 复现入口和避坑

正式工具：tools/audit_astaps72.py、tools/generate_login72_slice.py及各test；具体命令见协议审计/Harness。固定reference/protoc/GAME_PATH均从本机配置核对，不把共享文档相对路径当机器绝对路径。生成器--check不写文件，不能向未verified业务名自动晋级。

本轮一次性编排在local/migration72/analysis的review_fusion_sources.py、review_fusion_dependencies.py、review_fusion_proto.py、run_fusion_java_regression.ps1、run_fusion_research.py、close_fusion_runtime.ps1、finish_fusion_handoff.py。它们带本次前置状态和写入步骤，不可盲目重跑已完成的review/交接脚本。原失败/新成功日志与XML、完整reference报告/AST/descriptor均留local，不提交原始依赖、客户端、日志、数据库。

本轮结束后只维护交接/状态文档并核对Git差异，不再新增研发或取证；下次交接仍须先备份核验本HANDOFF2再覆盖，HANDOFF仅追加。
