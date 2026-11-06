# LunaGC CN 7.2 开发分支

2026-11-06当前已恢复，覆盖下方历史暂停：先融合固定AstaPS成果并完成对应验收/文档维护，然后暂停。D-02仍未完成，本轮不推进新实机阶段。详见[融合子卡](Project.md)和[融合记录](docs/CLIENT_72_ASTAPS_FUSION.md)。接手基线c9aafa6/384研究/ready228；参考库来源、HEAD和干净状态已核对，保留本地账号/保存/经济/Lua修复。

目标是原生适配所提供的国服 7.2 客户端，包括网络指令、消息及字段、客户端补丁与资源。**尚未支持 7.2 登录或进入世界。** 本分支不维护 7.1 兼容；遗留版本号、Proto、包号仍是研究输入，不能视为目标协议。

当前批（2026-11-06，已暂停）：[69612部分消费](docs/CLIENT_72_SCENE_DATA.md)及[真实生产Lua加载审计](docs/CLIENT_72_PRODUCTION_SCENE_LOAD.md)限定完成，本地提交51f7cd2/9aa8ca7；最终384研究回归通过、65输入复核、ready仍228缺口。实际fast加载437组部分求值/481错误，inline13,616组零错误，未修生产或改根配置。未知helper/完整协议/SDK/资源与D-02实机仍未完成，未部署。已按用户要求停止新增工作，恢复入口见[HANDOFF2](HANDOFF2.md)，任务状态见[Project](Project.md)；下方旧数量/运行记录为历史。

接手顺序：[工作规范](AGENTS.md) → [任务与验收](Project.md) → [研发索引](Harness.md)。详细方法见 [迁移研究](docs/CLIENT_72_MIGRATION.md)。历史专题保留业务背景，不构成 7.2 验收。

| 支持层次 | 当前状态 |
| --- | --- |
| 计划支持 | 新旧账号登录、选人、入场、基础玩法及后续内容，按 Project 分项验收 |
| 已实现 | 继承业务/便携脚本，7.2安装清单、元数据/泛型/父类/静态布局提取、静态CmdID→类型表、服务协议盘点、登录原始wire与响应对象图离线读取；不代表业务已匹配7.2 |
| 测试通过 | 当前266项研究工具/映射/资源审计回归通过；原生seed加密、响应解密验签/密钥Java对照、Handler核心、SDK认证来源、两处分区恢复路径、key_id密钥选择及Handler原生版本哈希、独立封/收帧及XOR、连续Token序列化封包及Ec2b验证通过；分区key到Token帧4样本、Handler到Login完整帧10样本与独立protoc/重复验证通过，具体范围见Harness；Java完整集既有217项失败未据此消除 |
| 客户端验收通过 | 暂无；进入世界与完整玩法分别验收 |

前置缺口：完整三层业务映射、其余枚举/字段语义、运行时泛型ABI/IFix和 7.2 资源来源；补丁仍需 ABI 与实机验证。本地安装清单7,830条大小/MD5已匹配；静态包号4,012条含5组未解决重号；包头/心跳请求与响应共17个静态wire字段已恢复，业务字段语义仍不完整，见[网络研究](docs/CLIENT_72_NETWORK.md)。未知项不发布为服务端正式包号或字段。

另已完成555,919个方法参数及835个Action实例的参数恢复，并通过离线CPU模型观察TokenReq保护writer的28字段，见[签名研究](docs/CLIENT_72_SIGNATURES.md)和[保护入口](docs/CLIENT_72_PROTECTED.md)。后续[Login请求](docs/CLIENT_72_LOGIN_WIRE.md)已补齐55外层字段、12消息83字段；[两个响应](docs/CLIENT_72_RESPONSE_WIRE.md)已定位85外层字段，另7个叶子30字段已双向恢复；[非空响应对象图](docs/CLIENT_72_GRAPH_READERS.md)现已覆盖18消息137字段的离线读取、map/list与oneof合并行为，仍保留明确的托管运行环境模型限制。这些结果没有替换服务端协议或放开版本准入。

[登录语义](docs/CLIENT_72_LOGIN_SEMANTICS.md)和[会话研究](docs/CLIENT_72_SESSION.md)已确认四个Token/Login请求/响应身份与CmdID、36个字段来源；默认枚举表8类102符号，另验证2类连接状态枚举20符号。限定夹具下已贯通分区Ec2b密钥来源到Token完整帧，以及响应Handler的密码学/安装key/版本hash到Login实际序列化和完整帧；包头/XOR/错误边界另有独立验证。真实响应收帧调度、完整Handler返回、SDK/补丁/资源前置及其余字段仍未验或未知；真实启动缓存ABI观察不代表登录入场通过。

[登录源码索引](docs/CLIENT_72_LOGIN_SOURCE_GRAPH.md)已完成 A-03a：12份源码、9个入口、80个方法及13条人工调用边；候选边和未关联调用保持未验证，不代表完整P0闭包。2026-11-06已恢复限定离线工作，最终恢复入口见[详细交接](HANDOFF2.md)。

[固定7.2资源候选](docs/CLIENT_72_RESOURCE_CANDIDATE.md)已完成七表与849条选定正引用审计；revision对齐、Lua/场景与完整最小资源仍待验证，未替换运行资源。

使用 Windows 便携方式。需要 JDK 17、便携 MongoDB、Python 3.11+（研究工具）、Git/LFS；补丁构建另需 Rust nightly 和 MSVC。依赖/本机配置不随 Git 交付。准备及排障见 [部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md)，本分支约束和路径以 AGENTS、Project 和本机配置为准。

在仓库根目录运行，先按 Harness 核对配置及占用：

```powershell
# GAME_PATH 需从已核对的本机配置赋给当前 shell；Python 不自动读取 .env
python tools/audit_client.py --client $env:GAME_PATH --output local/migration72/evidence/client-audit.json
python -m unittest tools.test_audit_client tools.test_local_windows tools.test_patch_game tools.test_runtime -v
# 先设置 JAVA_HOME/PATH 为便携 JDK 17，确认目标 JAR 没有被占用
.\gradlew.bat --no-daemon jar -PskipHandbook=1 --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/local_windows.ps1 -Mode status
```

隔离研发服务已完成构建和健康/端口/JAR哈希核验，详见[本批验证](docs/CLIENT_72_RESEARCH_VALIDATION.md)。当前JAR仍由build.gradle派生为LunaGC-7.1.0.jar，并非可连接7.2的产物。Java 测试/集成测试先配置独立测试数据库，见 Harness。

| 入口 | 作用与限制 |
| --- | --- |
| Start-GS.cmd / Stop-GS.cmd / GS-Status.cmd | 启动或复用、正常保存停服、检查；start 不构建，默认可能安装客户端补丁 |
| Rebuild-GS.cmd / local_windows.ps1 -Mode build | 停服后构建服务端 |
| Rebuild-Patch.cmd / -Mode buildpatch | 构建 Rust 补丁，不证明适用于目标客户端 |
| Play-GS.cmd / -Mode play | 服务与客户端入口；当前 7.2 前置不满足，不能当作可玩入口 |
| Restore-Client.cmd | 退出目标客户端后恢复补丁，不用于无关客户端 |

Proto Java 由 Gradle/protoc 生成，不编辑 build/generated。版本无关业务回归保留；旧协议断言在有证据的 7.2 fixture 接管后替换，不能删除后声称兼容。

上游与署名：[girluh/LunaGC](https://github.com/girluh/LunaGC)（基于 Grasscutter）、[capyb2222/animegamepatch](https://github.com/capyb2222/animegamepatch)；Terax（nt）。资源来源和权属见 [资源 README](resources/README.md)。


2026-11-06 A-03a收尾：重新执行固定12源码/JDK17解析、8项聚焦测试和研究映射校验均通过；重生成摘要与已提交产物逐字节一致，ready仍退出1（213缺口）。`local/migration72/evidence/login-source-close{,-summary,-check,-ready}.json`为本机复核证据。本次用户指令覆盖旧暂停状态，依次推进V-01b/V-01c/A-03b/A-03c，A-03c提交后停止；禁止B-03/C-01/D-02及实机调试。A-03整体仍待完整闭包和实机复核。

2026-11-06 A-03c限定离线验收完成，见[候选消息/Proto与三层缺口矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md)：97包构造、148引用/种子、1139文件描述符依赖，缺DoSetPlayerBornDataNotify.proto如实保留。原三层状态及185字段不变；6新增/260研究回归、重复矩阵通过，研究校验0/ready1（213缺口）。V-01b/c经济/保存221项通过；完整Java集未重跑。按用户要求到此停止并交接Astra，下一研究为B-02选人三件组的独立7.2三层证据；A-03整体仍待完整闭包/实机。未改运行文件、未部署或调试。

2026-11-06 B-02d选人三件组证据闭合并停止，见[研究与Sol规格](docs/CLIENT_72_BORN.md)。只恢复目标5008通知、8541请求及414响应；昵称tag4、角色tag11、retcode tag15，通知空schema有目标原生证据。39原生样本/独立wire/protoc、6新增/266研究回归与重复产物通过；ready仍拒绝（204缺口），无运行集成、部署或客户端验收。
