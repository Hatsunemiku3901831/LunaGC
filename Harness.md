# 研发索引与执行骨架

2026-11-06最新恢复：用户要求继续逐项推进至D-02实机验收，覆盖前批停止点。B-02e1完成见下文；E-01b固定scene3审计见[场景资源](docs/CLIENT_72_SCENE_RESOURCES.md)。`audit_scene_resource72`要求固定commit/root/BinOutput/Scene/Point与Excel blob链，核对9源码hash；1823点、2817正引用无缺失，7新增/14资源回归及两次产物一致。资源尚未安装；当前真实调试目标状态见下一段，完整资源及运行时revision仍未验。

本批动态启动已接续：见[动态研究最新节](docs/CLIENT_72_DYNAMIC_DEBUG.md#2026-11-06恢复连续api连接下的启动观察)。分段重连发现当前异常事件缺口；改为连续连接150秒，5入口代码相同/DR全0、40首次异常由事件及ex.code/addr核对后逐个交付，目标存活运行、两key槽空；SDK/登录/退出根因未验。当前目标保留，不再适用上段“未启动游戏”的较早状态；先实时核对dynamic-active-session，禁止无条件run或重复load。用户另允许参考7.1场景实现迁移到7.2，E-01c开始实际Lua/目标ID核查，保留来源和未知。

B-02e1已提交b68a8ab，入口[选人继续](docs/CLIENT_72_BORN_CONTINUATION.md)：33原生样本/9新增测试、原born39样本重跑摘要不变，两次trace/摘要相同。未知消息20353的两个uint32字段有独立wire/protoc，正式业务名unknown；媒体/定时器仅登记或手动离线回调，完整异步/网络次序未验。完整研究选集288项全过；首次281中1项新增证据清单漂移失败保留，人工复核仅新增born-continuation/scene-resource/startup-continuation三份摘要，原1287源码/Proto、148行矩阵/三层状态、99候选/1140描述符及开放锚点均不变，50研究输入受hash约束。证据evidence/continuation-login-proto-{review-check,repeat}.json、logs/continuation-research-regression.log；不把门禁拒绝隐藏或放宽。

E-01c迁移输入检查：[脚本迁移](docs/CLIENT_72_SCENE_SCRIPT_MIGRATION.md)，`audit_scene_scripts72`经固定E-01b、3实体表blob、旧脚本Git及工具hash守卫，独立Java LuaJ进程实际求值。出生块686组/5492正ID无缺失；扩展全64块17,806组，按生产索引过滤求值13,616非dynamic组，当前fastRequire=true报407个顶层错误，现有inline路径比较全部成功。inline共13,785文件/104 Common、95,600正ID，8条缺ID全部garbages.gadgets，62缺ScriptLib成员涉及1503静态组。6新增/20聚焦、三报告和摘要二次一致；配置未改、触发器/生产序列化未执行，resourceReady=false、E-01c进行中。证据scene-{script,grid-script,grid-inline}-{audit,repeat,summary-repeat,determinism}.json和scene-script-focused-tests.log；不借旧缓存、删组或空接口过关。最新脚本API只读快照scene-phase-runtime-snapshot.json确认原目标仍运行、5处代码匹配、两key槽空，没有重新load/run。

E-01c工具批完整研究选集294项通过，logs/scene-script-research-regression.log；研究校验0错误、ready仍204缺口。人工复核仅新增上述三份Lua摘要，原1287源码/Proto、148行三层矩阵、99候选/1140描述符与开放边界均不变，证据输入50→53；重新recover两次输出相同，evidence/scene-script-login-proto-{review-check,repeat}.json。不修改门禁逻辑，也不把离线Lua结果用作协议证据。

E-01c工具批提交dfd11d6，继续B-02e2（已先建卡）：46311/27748 reader、网络1507AD6F0和14CD67260缓存等待。先恢复原生存储/外层wire与必要非空嵌套，未知不据旧语义命名；完整正常场景分支、媒体异步和实机仍是后续工作。

[AGENTS](AGENTS.md) 管规范，[README](README.md) 管能力说明，[Project](Project.md) 管任务验收；本文件维护索引与可复现流程，不重复长状态清单。目标仅国服 7.2。

## 仓库入口

| 路径 | 作用 |
| --- | --- |
| src/main/java/emu/grasscutter/Grasscutter.java、build.gradle | 运行入口、版本、依赖、Gradle/protoc |
| src/main/proto/ | 三件组选人Proto已按B-02d生成；其余遗留源仍待7.2核实 |
| src/main/java/emu/grasscutter/net/packet/ | PacketOpcodes、Opcodes、BasePacket、PacketOpcodesUtils |
| src/main/java/emu/grasscutter/server/game/ | GameSession 解帧/加密、GameServerPacketHandler 注册/状态分发 |
| src/main/java/emu/grasscutter/server/packet/recv/、send/ | Handler/发送包，直接依赖生成 Proto API |
| src/main/java/emu/grasscutter/server/http/ | SDK、dispatch、RegionHandler、GM/健康 |
| src/main/java/emu/grasscutter/game/player/Player.java | 登录初始化/管理器/退出保存；需沿管理器追踪间接通知 |
| src/main/java/emu/grasscutter/config/、data/proto-fanout.json | 包号配置覆盖、调试/屏蔽/候选旁路 |
| tools/extract_client_type_names.py、extract_client_usage_types.py、extract_client_literals.py | 已有旧 profile 提取器，不是 7.2 工具 |
| tools/audit_client.py | 已有只读摘要/profile/补丁静态扫描；不证明完整或兼容 |
| patch/ | Rust 子模块；HTTP/security/native_sdk/free_recharge 分功能核查 |
| resources/、data/ | 资源子模块/默认运行数据；配置 ID 与网络号、实例 GUID 分开 |
| src/test/、tools/test_*.py | Java/资源/脚本测试，wire 样本须有独立来源 |
| tools/local_windows.ps1、portable_settings.ps1、game_path.ps1 | 便携生命周期/归属/路径；.cmd 含 pause |
| tools/patch_game.ps1、client_patch_guard*.ps1 | 补丁安装/恢复/守护；start 默认可能修改客户端 |
| local/、debug/、build/ | 忽略的依赖/证据/日志/生成 Java |

详细方法：[迁移研究](docs/CLIENT_72_MIGRATION.md)；部署：[手册](docs/CODEX_ONE_CLICK_DEPLOY.md)（旧路径/Compose/7.1 结果仅历史参考）。专题接手从 AGENTS 的账号、深渊、商城链接进入。

## 流程与证据

输入（版本/渠道/清单/哈希）→ 分析（元数据/注册/序列化/调用场景）→ 映射（三层、来源、歧义、独立状态）→ 生成（确定性常量/Proto）→ 测试（wire/业务/边界）→ 客户端（新旧号/入场/保存/玩法）。未知边不能进入正式生成。

本机证据根 local/migration72/：evidence/ 放初始差异、客户端审计、安装检查；logs/ 放测试/构建/运行输出；analysis/ 放反汇编等未脱敏材料。共享摘要引用输入 SHA-256、相对路径和代码位置，不提交账号、令牌、原始抓包或二进制。

新增已实现：verify_client_manifest（完整清单）、extract_client72（固定目标基础元数据及泛型/父类/静态字段布局）、extract_client72_commands（静态包号）、extract_client72_wire（包头/心跳研究Proto）、inventory_protocol（静态协议表面与P0 import下界）。证据说明：[目标提取](docs/CLIENT_72_EXTRACTION.md)、[网络与wire](docs/CLIENT_72_NETWORK.md)、[协议盘点](docs/CLIENT_72_PROTOCOL_INVENTORY.md)。尚未实现：完整P0调用图、运行时闭合泛型ABI、正式业务源码生成和完整7.2 wire fixture。

## 可复现命令

在仓库根执行，先将已核对的本机 GAME_PATH 赋给当前 shell；Python 不自动读 .env。

```powershell
git status --short
git submodule status
git -C resources status --short
git -C patch status --short
python tools/audit_client.py --client $env:GAME_PATH --output local/migration72/evidence/client-audit.json
python tools/verify_client_manifest.py --client $env:GAME_PATH --output local/migration72/evidence/manifest-full.json
python tools/extract_client72.py --client $env:GAME_PATH --members --output local/migration72/evidence/client72-metadata.json
python tools/extract_client72.py --client $env:GAME_PATH --structures --output local/migration72/evidence/client72-structures.json
python tools/extract_client72_signatures.py --client $env:GAME_PATH --output local/migration72/evidence/client72-signatures.json
python -m unittest tools.test_client72_signatures tools.test_client72_structures -v
python -m unittest tools.test_extract_client72 tools.test_client72_structures -v
python tools/extract_client72_commands.py --client $env:GAME_PATH --output protocol/cn-7.2/commands.json
python -m unittest tools.test_client72_commands -v
python tools/extract_client72_wire.py --client $env:GAME_PATH --output protocol/cn-7.2/wire-observations.json --proto protocol/cn-7.2/research.proto
python -m unittest tools.test_extract_client72 tools.test_client72_structures tools.test_client72_commands tools.test_client72_wire tools.test_validate_protocol72 -v
# PROTOC72指定已经核对的本机protoc；本批用Gradle缓存的3.18.1
python tools/check_client72_wire_proto.py --protoc $env:PROTOC72
python tools/inventory_protocol.py --output local/migration72/evidence/server-inventory.json
python -m unittest tools.test_verify_client_manifest tools.test_extract_client72 tools.test_inventory_protocol tools.test_audit_client tools.test_local_windows tools.test_patch_game tools.test_runtime -v
```

使用 local/runtime/jdk-17*/bin，设置 JAVA_HOME/PATH。Java 从 src/main/proto 生成到 build/generated/source/proto，禁止手改生成目录。

```powershell
.\gradlew.bat --no-daemon generateProto -PskipHandbook=1 --console=plain
.\gradlew.bat --no-daemon test -PskipHandbook=1 --console=plain
.\gradlew.bat --no-daemon jar -PskipHandbook=1 --console=plain
```

集成测试先读账号运维手册，设置 LUNAGC_TEST_MONGO_URI、LUNAGC_TEST_MONGO_SECONDARY_URI 为独立测试实例；某些测试还读 LUNAGC_MONGO_URI。执行前检查测试建库/清理逻辑，不指向现有 grasscutter 数据；协议/工具测试尽量不连接数据库。

部署须在目标协议/补丁前置满足后顺序执行 local_windows.ps1 -Mode stop → build → start → status。补丁变更另做 buildpatch/patch，先退出目标客户端。逐步检查退出码，构建不覆盖在用 JAR。隔离数据库名和运行数据目录，保留已有数据；MongoDB 固定 27017、SDK HTTP 固定 8088，不能只改 .env 假设隔离。

LUNAGC_CLIENT_PATCH_GUARD=0 只供服务研究禁用守护，不放开 play/patch 版本检查。验证 HTTP /status/server、PID/命令归属、TCP HTTP/UDP 游戏端口、日志、磁盘 JAR SHA-256 与 local/running.json 和实际进程一致。7.2 实机结果单独记录。

## 故障定位与接手

1. 输入：路径、版本/渠道、哈希/清单、资源来源。
2. 提取：profile/解码边界/引用/可执行地址，未知结构停止生成。
3. 连接：补丁加载、SDK/HTTP 路由、版本、加密和密钥切换。
4. 协议：包头/长度、三层映射、别名/注册冲突、聚合/枚举子消息。
5. 生命周期：新号选人、旧号加载、初始化、场景握手、资源引用。
6. 保存/玩法：按专题复现，留证据后修复，构建部署再实测。

接手先读 Project 下一项与解除条件，再查看本文件入口及公共摘要；本机报告缺失须重新执行，不复制历史 PID/通过数。

方法/回调接手见 [CLIENT_72_SIGNATURES](docs/CLIENT_72_SIGNATURES.md)。已实现extract_client72_signatures：完整参数、BYREF、VAR/MVAR序号与闭合Action的Invoke参数；返回类型和泛型方法实例尚未恢复。client72-signatures.json通过method.index与typeIndex连接，actionCallbacks保存实例化参数；不得把声明指针0当作已恢复代码。

PingRsp现在通过参数、注册和分派三路锁定：MJGOAHFNCGC/25763，读函数15251F300。extract_client72_wire已加入有符号/无符号32位reader证据，研究Proto共17字段。新增接手日志logs/p0-callback-tests.log、ping-mapping-tests.log；反汇编analysis/ping-handler-registration.txt、pingrsp-dispatch.txt、pingrsp-size.txt。相同数字的metadata初始化用途不作为CmdID证据。

保护入口接手见 [CLIENT_72_PROTECTED](docs/CLIENT_72_PROTECTED.md)：已实现trace_client72_token（单一目标writer、显式4 helper模型、执行预算/未知状态失败）及check_client72_token_proto；token-wire-observations.json/protected-token.proto均为离线研究产物。依赖requirements-client72-trace.txt只装入local/migration72/python，详细命令在专题；不用Docker/WSL。证据在evidence/token-protected-trace.json、token-protoc-check.json、token-determinism.json及logs/a04d-tests.log。早期Login52字段/响应checkpoint留在analysis/protected-probe-*.json，已由下述Login完整writer和响应外层reader取代；不要将合成对象异常当作客户端缺陷。

A-04d检查点：ce9e3ae（参数/泛型回调参数）、b87fe73（PingRsp）、4b15d73（Token保护writer）；最新参数摘要将901条Action类型引用与835个不同实例分开统计。正式工具运行前仍核对固定输入哈希；不要把本机实验脚本的支持范围套用于正式提取器。

选择测试时，--tests紧跟test任务；serverSmokeTest单独调用，不要把过滤参数放在第二个任务后面。实际遇到的端口冲突证明测试与研发服务不能并行抢占22101。测试完成、释放端口后再部署；不要只看HTTP健康。

## 三层映射校验入口

已建立 [protocol/cn-7.2](protocol/cn-7.2/README.md)。profile固定输入，mappings分opcodes/messages/schemas，完整闭包未确认。commands.json单独保存静态CmdID→混淆类型绑定，不自动提供业务名称。证据和保护入口阻塞见 [网络研究](docs/CLIENT_72_NETWORK.md)。运行 `python tools/validate_protocol72.py` 校验研究记录；`python tools/validate_protocol72.py --ready` 当前应退出1。未知不能作为正式常量生成。测试 `python -m unittest tools.test_validate_protocol72 -v` 使用合成模型，不是目标wire测试。

wire-observations.json为目标tag/offset/类型/helper的静态观察；research.proto是原始混淆名的离线产物，不能复制为正式业务Proto。check_client72_wire_proto用手写目标wire断言，输入是合成值，非客户端抓包。本批复现证据：logs/p0-research-tests.log、wire-proto-tests.log；evidence/wire-summary.json、mapping-check.json、mapping-ready.json、wire-determinism.json。commands与wire重复生成必须逐字节一致，本批已核对。接手保护入口先读CLIENT_72_NETWORK的VA和已尝试方法，原始反汇编在analysis/，不要把填充字节或IFix替代路径当作正常逻辑。三个研发检查点为2003432（结构）、333b628（命令）、c9abee3（初始wire）；A-04d先从analysis/p0-types.json、login-type-cells.json、pingrsp-cells.json接续回调签名研究。

本批构建/测试/部署索引：[CLIENT_72_RESEARCH_VALIDATION](docs/CLIENT_72_RESEARCH_VALIDATION.md)。完整失败快照full-test-results/、限定通过targeted-test-results/、汇总evidence/*-summary.json均在local/migration72/。运行验证deployment.json与jar-class-check.json；配置备份private-before/含敏感信息，仅本机读取。能力资源测试支持LUNAGC_RESOURCES_DIR，默认仓库resources/；历史/其他套件可能仍硬编码容器路径，执行前检查。

A-04d3请求入口：[Login完整wire](docs/CLIENT_72_LOGIN_WIRE.md)；trace_client72_login、check_client72_login_proto、observed_proto72负责嵌套/oneof原生writer、研究Proto与原始夹具对照。login-wire-observations及login-research.proto是研究产物，枚举符号/业务语义不晋级。

响应接手以[原生对象图](docs/CLIENT_72_GRAPH_READERS.md)为当前入口。[外层/叶子检查点](docs/CLIENT_72_RESPONSE_WIRE.md)保留trace_client72_responses及trace_client72_response_leaves的限定范围；新client72_graph实际执行父子ReadMessage/limits和oneof，client72_map_runtime提供显式托管存储模型，client72_graph_fixtures保存独立输入，trace_client72_graph完成34组原始/34组protoc输入与6组拒绝验证。response-graph.proto为18消息137字段，response-graph-observations补充21条内容绑定；旧表not-traced作为历史扫描状态保留。

[map AOT注册](docs/CLIENT_72_MAP_AOT.md)：extract_client72_map_aot已恢复21条选定注册，核对具体/shared读取入口及adapter声明；运行对象图前从固定客户端重新提取并比对map-aot-observations。未知泛型方法不能按邻近地址或形状代入。

三层校验同时检查新内容绑定的tag、样本索引、目标hash；contentStatus=native-graph-observed只表示有边界离线内容证据，unknown_field仍不能verified。`python tools/validate_protocol72.py --ready`仍应失败。当前未改src/main/proto或部署服务，运行JAR不是7.2可连接产物。

复现命令集中在对象图与AOT专题；最新证据local/migration72/evidence/response-graph-trace.json、response-graph-determinism.json、map-aot-determinism.json及logs/a04d3c-graph-tests.log。先检查输入hash，再检查AOT绑定、流limit/lookahead、已允许的native/helper与实际对象存储；不要跳过未知调用或越界错误。

检查点：606ea03（Login writer）、91d9791（响应外层）、5fe24ad（7叶子）、ce45b47（map AOT）、242b1fb（响应对象图）。下一项以末尾登录语义检查点为准，运行时ABI/IFix/实机单列验收。原资源子模块差异继续保留。

登录语义接手：[标记与枚举](docs/CLIENT_72_LOGIN_SEMANTICS.md)。新增 extract_client72_literals/enums 从目标全局usage与field-default表恢复4条标记和6枚举70符号；check_client72_metadata_values执行原生字节解码和常量读取，证据为 evidence/metadata-values-native.json。现已确认两个上游响应Handler；网络管理器的两个强类型包装只处理共享配置，不能替代完整Handler。

当前登录语义入口：trace_client72_login_semantics，摘要 protocol/cn-7.2/login-semantic-observations.json；详细 evidence/login-semantics-trace.json，回归 logs/login-semantics-tests.log。17组原生检查和87项回归通过；2个响应身份、20字段来源与wire闭合；枚举扩展至7类型86符号、4个嵌套字段/判别器关联。validate_protocol72同时检查语义标记、类型/指令、字段来源、oneof枚举与wire，不自动晋级剩余未知。

下一项：Token Handler 1507BE0E0成功路径从15A4DE262/147CF3070继续；追踪seed/会话token写入，再与1507C2218→151213730发送参数闭合请求配对。Login构造来源检查的明确终点为151213AC6；不把该前缀通过当成完整构造/登录成功。旧文档把151221D70/1512160D0称为响应回调的措辞已校正，当前以CLIENT_72_LOGIN_SEMANTICS为准。

确定性证据：evidence/login-semantics-determinism.json记录trace、语义摘要、literal/enum表的重复输出SHA-256；本批研究校验mapping-check.json通过，mapping-ready.json仍拒绝发布。

本轮接手提交：a9ba540（literal/enum基础）、010f0c4（响应身份、来源到wire、语义校验及文档）；均仅本地。源码/主Proto/运行服务未变，现有resources子模块差异未提交。

会话研究新增入口：[CLIENT_72_SESSION](docs/CLIENT_72_SESSION.md)。trace_client72_session从明确的解密后检查点执行seed字节序/XOR、整数存储和MT64，12组Java独立逐字节对照与6组原生短输入拒绝。client72_session_runtime只模型化声明的托管分配/随机源/置换表，不模拟解密、验签、Shell成功。共享session-seed-observations.json；本机evidence/session-seed-trace.json、session-seed-determinism.json；91项回归在logs/session-all-tests.log。本项不代表完整Token成功路径或实机验收。

接续：响应+68→会话+98→1507C2211→151213730请求+D8需完成独立wire链；Login保护续段151213AC6已执行到环境类1458971C0检查。Shell新分支15A3CE060与解密/验签仍明确未知。任务状态以Project最新执行记录为准；只改离线研究工具无需重启运行服务。

上述token链现由trace_client72_token_flow完成：4组response tag6→session+98→Login参数→request tag1及1组null会话拒绝；两个token字段进入映射，新增validate_session_token_flow约束hash/来源链/wire一致。复现命令见CLIENT_72_SESSION，报告evidence/session-token-trace.json、session-token-determinism.json及logs/session-token-tests.log（95项）。构造完整续段仍从analysis/probe_login_continue.py研究，当前旧Shell受控byte[]→ByteString之后到148A22560→1421C1DB0原生ret；接着追踪151214D20发送参数和响应配对。该忽略脚本只供探索，正式证据必须迁入工具后重复验证；不能把其环境哨兵当实机结果。

请求身份新增trace_client72_request_identity：阶段方法14C8AE9A0的参数指向85949/NEKEJMEIJBF，原生常量和构造/响应前缀确认6/7/8/9四个命名阶段；声明类型Clone的self-class/CopyFrom与原生getter独立检查。两个请求身份/CmdID现静态verified，完整发送/全部字段/实机另列。8枚举102符号、4literal/22常量、6身份样本及100项回归通过；证据evidence/request-identity-{trace,determinism}.json、metadata-values-native-stage.json，日志request-identity-tests.log。seed/token本地提交0f324eb/0c12f2c；后续接151213BE2的SDK源、151213FF7续段和151214D20队列。研究ready仍应退出1。

Login构造现由trace_client72_login_constructor在指定旧Shell/非云夹具中到达151214D20；不含队列/网络执行。SDK包装实际读取3条目标literal，uapc/tag1533、channel_id/tag12、sub_channel_id/tag364来源到原生writer闭合，UInt32边界/失败覆盖。2个Shell字段只保留原始来源，不命名。新增校验validate_login_constructor，105项回归与三组构造重复一致；evidence/login-constructor-{trace,determinism}.json、logs/login-constructor-tests.log。请求身份提交70f3e5b；下一项Token构造15121FF70/队列及密码学前段。正式工具取代相应探索脚本，复现见CLIENT_72_SESSION。

E-01候选资源入口：[固定7.2上游提交](https://github.com/DimbreathBot/AnimeGameData/commit/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1)。提交/根树已只读记录resource-source-heads.json、resource-source-tree.json，根目录包括ExcelBinOutput/BinOutput/TextMap，不含Lua Scripts目录；不能据此称最小资源齐全。后续使用固定commit取最小子树并核对客户端revision、字段与稳定ID，不更新resources引用。

client seed来源已接入trace_client72_session：152F1B4D0执行NextDouble×2^64、编码、返回/全局同值保存；10组来源边界+既有18组后段复跑，106项回归通过。session-seed-trace新增clientSeedSourceFixtures，determinism已更新；日志client-seed-source-tests.log。Token密文helper15121DB40进入Shell 0x64仍未贯通，探索边界见CLIENT_72_SESSION。上一Login构造提交cde97d4；运行源码/配置未变，无重启。

资源候选审计入口：[CLIENT_72_RESOURCE_CANDIDATE](docs/CLIENT_72_RESOURCE_CANDIDATE.md)、audit_resource72。固定commit d54f6f0d的真实根tree为5b334e71；API用commit查询的sha别名不能代替tree对象hash。七个blob均按大小/Git SHA-1/SHA-256核查，稳定ID和849条选定正引用已验证；内置revision不一致、Lua/场景/完整资源仍待验，不复制进resources。7资源测试+106研究测试通过；evidence/resource-candidate-{audit,determinism}.json及logs/resource-candidate-all-tests.log。client seed来源提交e716da8；继续Token Shell/发送队列与A-02动态旁路审计。

A-02动态来源复核完成：`review_protocol72_surface`对照人工`server-surface-review.json`，17位置/10组、40源码hash、37数字case、5类开放边界。原盘点8项+新增7项测试通过，两次报告一致；复现命令见[协议盘点](docs/CLIENT_72_PROTOCOL_INVENTORY.md)。`protocolReady=false`，不能用来源审计代替7.2映射。C-01须在SendPacketEvent替换之后校验最终包，拒绝FanOut/sweep未知候选和注册冲突；插件/replay仍是开放接口。日志`logs/server-surface-tests.log`、报告`evidence/server-surface-review*.json`。未改运行源码/Proto/配置，无需重启。

Token外段新增`trace_client72_token_constructor`：15121FF70→151214D20，15121DB40明确用opaque字符串边界，不能称完整密码学成功。4组SDK sub_channel_id来源到请求tag10/uint32；6个原始会话偏移和Shell操作5等字段仍不命名。133项研究/盘点回归、重复trace/摘要通过，ready仍退出1；复现见[会话研究](docs/CLIENT_72_SESSION.md)，证据`evidence/token-constructor-{trace,determinism}.json`，日志`logs/token-constructor-tests.log`。A-02提交e87baa7；当前继续发送器/队列与会话源头，无运行部署。

发送链新增`trace_client72_send_flow`：构造对象→151214D20信封→149935970接口槽4→所选AKFCDHJBCHD槽8→1472CD100→14A447E00入口。接口/桥接/实现的参数均独立解码为JKGIAOABCNP；8组包含高位sequence/64位时间、禁用和缺包装。138项回归、重复产物通过；`evidence/send-flow-{trace,determinism}.json`与`logs/send-flow-tests.log`。对象池/虚表/传输选择/时钟为明确夹具，未执行封包体、socket或实际客户端；复现和边界见[会话研究](docs/CLIENT_72_SESSION.md)。Token外段提交423330e。D-01可独立追踪命名MakeInitialUrl/Marshal.PtrToStringAnsi/SDKUtil.RSAEncrypt，不能只凭旧特征命中安装补丁。

D-01入口：[CLIENT_72_PATCH_ABI](docs/CLIENT_72_PATCH_ABI.md)、`trace_client72_patch_abi`。四个命名方法的真实flags/参数与原生前缀已核对，Browser捕获布局另验；12原生样本、143项研究回归及重复产物通过。证据`evidence/patch-abi-{trace,determinism}.json`、`logs/patch-abi-tests.log`。仅参数前缀，不代表URI/字符串工厂/RSA函数完成或补丁实机加载；patch未变。下一步14052FBF0字符串工厂、原生RSA替换/验签数据流，或14A447E00封包体。发送链提交8638ea5，D-02仍待完整前置。

Token helper早期inArray异常已定位为夹具遗漏运行时空byte[]常量，正式恢复结果以下段为准。类型usage kind1/registration+48探索仍仅作索引。D-01a本地提交db5245e。工作期间AGENTS.md新增编码规范属他人修改，已读取并保留，不提交；resources原差异仍保留。

seed完整helper入口：`trace_client72_seed_crypto`、`client72_seed_crypto`，复现命令及模型边界见[会话研究A-04d5g](docs/CLIENT_72_SESSION.md#a-04d5g-完整seed-helper的原生验签加密与base64)。15121DB40已原生返回；5成功/1未初始化/2验签拒绝共8组，独立RSA/完整签名块/Base64对照通过，重复trace/摘要一致，146项研究回归通过。`evidence/seed-crypto-{trace,determinism}.json`、`logs/seed-crypto-{run,tests}.log`和`protocol/cn-7.2/seed-crypto-observations.json`。初始化标志/OS/TLS/熵/托管常量为显式夹具，验签不替换；没有实机或补丁验收。下一步合并Token外段与该helper，继续响应解密验签及SDK真实初始化；运行文件未改，无重启。

完整Token入口：`trace_client72_token_complete`从15121FF70在同次执行中调用原生seed helper，再经原返回指针/+90/tag623和24604发送到14A447E00；业务契约对应client_rand_key，累计27字段。5组含零/64位边界/未初始化/签名篡改，错误时外层仍发送空seed也有真实断言；独立密码学、完整writer、149项回归及重复一致。`evidence/token-complete-{trace,determinism}.json`、`logs/token-complete-{run,tests}.log`、共享`token-complete-observations.json`；复现见[会话研究](docs/CLIENT_72_SESSION.md)。独立seed工具8组重跑与此前产物相同。初始化/OS/Shell5/传输仍为夹具，未执行封包体或实机；下一项响应密码学前段/SDK初始化/封包体。seed helper提交823762e，无运行部署。

响应密码学探索索引：`analysis/probe_response_crypto.py`、`response-decrypt-probe.log`、`response-crypto-verify.log`、`evidence/response-crypto-exploration.json`。从15A4DE707实际执行操作101私钥验签/解密、SHA256Managed摘要及操作103错误响应签名拒绝，摘要/明文有独立对照；原始私钥材料只在忽略目录。公钥部分匹配仓库4_Pub.der，但服务器SigningKey与目标验签公钥不同。正式化时补块级轨迹、输入wire连接与成功/错误样本，不复用探索脚本作为正式证据；有效响应签名、本地信任配置、SDK初始化和实机仍待完成。完整Token提交cdbc275，当前149项回归不含此探索。

正式响应密码学入口：`trace_client72_response_crypto`、`client72_response_crypto`、`client72_rsa_fixture`。真实reader的tag477/server_rand_key、tag208/sign接入原生RSA/SHA/验签/XOR/MT64，8组中3组密钥与Java逐字节一致，4错误/1短seed均不安装密钥；155项回归和重复trace/摘要通过。有效样本明确替换本地签名信任输入，验证指令与结果未替换；块级/区域指令轨迹分开记录，SDK前缀、实际补丁和整Handler不在范围。复现见[会话研究A-04d5i](docs/CLIENT_72_SESSION.md)，证据`evidence/response-crypto-{trace,determinism}.json`、`logs/response-crypto-{run,tests}.log`及共享`response-crypto-observations.json`。默认读取仓库SigningKey作为离线夹具，只输出哈希，无数据库/运行服务变更。下一项SDK warmup/Handler前缀、封包体或D-01实际信任配置。

Handler核心入口：`trace_client72_handler_core`从1507BE0E0到15A4E0D9A，连续完成warmup分支、密码学、UID与token保存，另验证__LastUid__/UInt32/PlayerPrefs命名边界；tag9确认uid，累计30字段。4原生样本、157项回归及重复一致，旧8组response工具重构后产物不变。新warmup空/8字节fixture返回-2/-1后仍继续；实际SDK初始化和整个Handler不晋级。证据`evidence/handler-core-{trace,determinism}.json`、`logs/handler-core-{run,tests}.log`；复现见[会话研究](docs/CLIENT_72_SESSION.md)。上一响应密码学提交caa7bea。后续剩余账号字段可查目标ComboUserModel4112/LoginResultModel9886及会话58013的调用来源；未核实映射保持未知。

SDK认证来源入口：`trace_client72_auth_sources`执行14F1917D0至14F191E18的来源检查/写入，再用同一会话执行真实seed构造及Token writer；account_uid/tag9、account_token/tag2、channel_id/tag3、account_type/tag15、is_guest/tag11确认，累计35字段。9组含正常/缺省/uint32边界/拒绝通过，160项回归及重复产物通过；原完整Token5组重构回归产物不变。JSON容器和SDK值为明确夹具，原生scalar getter/缺键类型检查继续执行；未执行回调尾段/UI或实际认证调度。证据`evidence/auth-sources-{trace,determinism}.json`、`logs/auth-sources-{focused-tests,tests}.log`及共享`auth-sources-observations.json`；复现见[会话研究](docs/CLIENT_72_SESSION.md)。Handler核心提交b0ca919。按用户要求本项收口后暂停，恢复入口与探索避坑见[详细交接](HANDOFF2.md)；D-02未完成，无运行部署。

暂停交接：当前认证来源提交c7556a0；[HANDOFF.md](HANDOFF.md)为追加式简要记录，[HANDOFF2.md](HANDOFF2.md)为详细恢复入口。本次原HANDOFF2不存在，首次创建，无旧文件可备份。研发目标已暂停；用户恢复前不再执行下一子任务。AGENTS.md和resources既有差异保持未提交。

认证可选字段入口：`trace_client72_auth_tail`，复现见[会话研究](docs/CLIENT_72_SESSION.md)。5原生样本、27聚焦回归、两次trace/摘要一致；六session字段及exact-one标志已验，B0不变，未晋级协议/实机。证据`evidence/auth-tail-{trace,determinism}.json`与`logs/auth-tail-tests.log`。用户已恢复推进，旧暂停段落是历史记录。

分区候选源/B0入口：`trace_client72_dispatch_token`，复现和前缀探索边界见[会话研究A-04d5m](docs/CLIENT_72_SESSION.md#a-04d5m-分区候选源到sessionb0与token-tag965)。14原生样本、166研究回归、重复产物一致；30677 tag14供给两处post-parse片段后到session+B0和Token tag965。实际源类型关联仍candidate，语义unknown，ready仍拒绝，D-02未验。证据`evidence/dispatch-token-{trace,determinism}.json`和`logs/auth-tail-dispatch-regression.log`。

分区解析前缀：`trace_client72_dispatch_prefix`，入口/信任输入/复现见[会话研究A-04d5n1](docs/CLIENT_72_SESSION.md#a-04d5n1-分区json到实际响应reader)。6原生样本、33聚焦/169研究回归，两次trace/摘要一致；原生JSON消费→解密/摘要/验签→30677 reader→post-parse原指针。成功明确本地签名信任，后处理/第二协程/语义/实机未验；A-04d5n/B-02c9进行中。证据`evidence/dispatch-prefix-{trace,determinism}.json`、`logs/dispatch-prefix-regression.log`。

分区后处理入口：`trace_client72_dispatch_post`，复现/模型边界见[会话研究A-04d5n2](docs/CLIENT_72_SESSION.md#a-04d5n2-分区嵌套读取到配置处理入口)。JSON/原生密码学连续到实际Region嵌套reader，再至151204AC0配置入口；8原生样本、173研究回归通过。证据`evidence/dispatch-post-{trace,determinism}.json`及`logs/dispatch-post-regression.log`。只覆盖选定Region字段/配置实参；配置函数体、B0连续路径、第二协程、tag965语义和D-02均未验，不部署猜测协议。

分区配置消费：`trace_client72_dispatch_config`，见[会话研究A-04d5n3](docs/CLIENT_72_SESSION.md#a-04d5n3-两段配置消费原生事件与region全局写入)。同次入口→两次配置/原生事件→Region全局写入，终点1512124B9；6原生样本、177研究回归通过，证据`evidence/dispatch-config-{trace,determinism}.json`和`logs/dispatch-config-regression.log`。SDK调用15275F080、非空key、事件真实监听器、B0连续路径及实机未验。最初返回EAX断言不成立，现保留完整RAX及明确低字节；不要把地址残余猜为错误码。

第一分区连续链：`trace_client72_dispatch_session`及SDK/提示模型，见[会话研究A-04d5n4](docs/CLIENT_72_SESSION.md#a-04d5n4-第一分区入口连续到b0及token原始wire)。第一入口→原生配置/SDK包装/提示→session+B0原指针→复用seed运行时/Token tag965，6原生样本、182研究回归；原Token5组trace/摘要不变。证据`evidence/dispatch-session-{trace,determinism}.json`、`logs/dispatch-session-regression.log`。导出表和日志sink未初始化/JSON存储等夹具明确，非真实SDK初始化；第二协程、业务名和D-02仍未验。后续从23192.MoveNext151226CD0恢复其解析源。

第二分区恢复状态：`trace_client72_dispatch_coroutine`，见[会话研究A-04d5n5](docs/CLIENT_72_SESSION.md#a-04d5n5-第二协程恢复状态到b0及token)。MoveNext151226CD0 state2/3→实际密码学/reader→151229560写B0→Token tag965；8原生样本、187研究回归。两处分区原始关联分别有证据，但不含初始HTTP等待、尾段/错误恢复或tag965业务名。证据`evidence/dispatch-coroutine-{trace,determinism}.json`及`logs/dispatch-coroutine-regression.log`。下一步核查登录必需字段，不能把请求常量4与某一公钥文件名直接等同key_id。

Token密钥选择入口：`trace_client72_key_selection`和`Client72KeySelectionReference.java`，见[会话研究A-04d5o](docs/CLIENT_72_SESSION.md#a-04d5o--b-02c10-token-key_id的命名与密钥选择)。原生tag1184=4、独立`&key_id=4`参数和唯一可互操作的客户端公钥交叉确认key_id；3请求/6响应、192项回归及重复产物一致。证据`evidence/key-selection-{trace,determinism,mapping-check,mapping-ready}.json`，日志`logs/key-selection-{run,repeat,regression}.log`。本地信任替换/Java响应夹具边界明确，未执行本地服务端对原始请求seed密文的解密，未通过实机。继续Handler15A4E0D9A尾段或14A447E00封包体；tag965仍未知。

Handler尾段入口：`trace_client72_handler_tail`及`client72_version_hash`，见[会话研究A-04d5p](docs/CLIENT_72_SESSION.md#a-04d5p-handler尾段robot输入与原生版本哈希)。连续Handler→tag3 robot输入setter→版本拼接→tag658参与原生ASCII/SHA1/Base64→GLOBAL+11B90，停15A4E0DC7；7原生样本、198回归、重复产物一致。实际是ASCIIEncoding，早期UTF8推断已纠正。版本组件、指定二字符串格式化和初始化fallback为明确夹具；剩余Handler/Login/实机及两字段业务名未验。证据`evidence/handler-tail-{trace,determinism}.json`，日志`logs/handler-tail-{run,repeat,regression}.log`；下一项GameManager状态及1507C0BB0→1507C21B0→Login。

Handler到Login连接探索：`local/migration72/analysis/probe_handler_login.py`，单次从1507BE0E0连续到14A447E00，实际Login/Full分支/Action→Login构造→发送getter23172，token/hash原指针与wire相符。仅探索，尚未正式验收。类型/方法usage、WorldType和PackageType、组合模型修正及后续边界见[HANDOFF2](HANDOFF2.md)；下一项A-04d5q。WorldType.Level=2与PackageType.PlayGo=3不能误当登录/Windows条件。

Handler→Login正式入口：`trace_client72_handler_login`，见[会话研究A-04d5q](docs/CLIENT_72_SESSION.md#a-04d5q-连续handler到login构造与发送封包入口)。实际Login/Full→Action→Login构造→发送getter23172/封包入口，token/hash原指针及wire相符；9原生样本、204回归、两次产物一致，原枚举输出不变。6非发送样本覆盖时钟边界/缺状态/Medium包/坏签名；不声称完整Handler返回、SDK真实初始化或D-02。正式工具取代前段单组探索作验收依据。本项已完成，按用户最新要求暂停并交接；恢复后继续封包体或P0闭包。

暂停交接检查点：研发提交dc41c99，A-04d5q限定验收完成；目标状态paused，用户恢复前不继续执行。旧HANDOFF2已备份并核验，最新恢复入口与缺口见[HANDOFF2.md](HANDOFF2.md)，简要历史见[HANDOFF.md](HANDOFF.md)。D-02未完成，无实机验收或运行部署。


恢复推进与封包入口：用户已恢复，旧暂停记录为历史，目标active。`trace_client72_frame`执行14A447E00完整返回；15原生样本、210研究回归和重复产物通过，见[网络研究A-04d5r](docs/CLIENT_72_NETWORK.md#a-04d5r-独立封包函数与整帧xor)。`evidence/frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json`和`logs/frame-{run,repeat,regression}.log`为本机证据。负载writer/size、两槽密钥与池化存储是显式夹具，包头size/writer、端序、MemoryStream和XOR/清理执行原生代码。下一项14A446CC0收帧边界或构造/密钥串联，不能称整个Handler/实机通过。本轮Java未运行，原部署记录不是当前服务状态；没有运行文件变化，不重启。


原生收帧入口：`trace_client72_frame_reader`，见[网络研究A-04d5s](docs/CLIENT_72_NETWORK.md#a-04d5s-独立收帧解密包头及错误边界)。18原生样本、216研究回归、重复产物通过；`evidence/frame-reader-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json`与`logs/frame-reader-{run,repeat,regression}.log`。实际BitConverter.cctor/包头reader/标量helper/端序/XOR/尾标记执行，factory与body消费是明确模型。早期探索漏IsLittleEndian初始化导致假长度错误，正式执行159B548E0修正，不能当客户端故障。下一项真实payload与seed串联或P0闭包；D-02未验，目标active。


连续Token完整帧：`trace_client72_token_frame`，见[网络研究A-04d5t](docs/CLIENT_72_NETWORK.md#a-04d5t-连续token构造与真实序列化封包)。6原生样本、222研究回归，二次trace/摘要一致；evidence/token-frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json、logs/token-frame-{run,repeat,regression}.log。原生seed/构造/queue/size/writer/frame同CPU，不用opaque body；UTF-8缓存及编码方法、SDK/OS/初始key仍是明确夹具。Frame cctor单独执行后恢复栈END哨兵，避免SessionRuntime初始化误跳旧RETURN；不能把该夹具错误当目标故障。用户已允许按实际需要动态调试；本机官方固定x64dbg来源/摘要在evidence/debugger-{release,install}.json，二进制在忽略local/runtime，目标调试尚未验。


动态研究改用脚本API：[CLIENT_72_DYNAMIC_DEBUG](docs/CLIENT_72_DYNAMIC_DEBUG.md)。用户明确禁止继续computer-use，已手动完成管理员启动与Automate插件加载；当前实时会话需通过API重新确认，勿新开客户端。D-01b1真实启动/5处代码/5000字节RNG状态与原生初始化交叉通过；evidence/dynamic-*.json和logs/dynamic-phase-*.json留本机。Ec2b原生单组探索已输出与仓库dispatchKey相同，正式化/分区串联尚待；analysis/probe_initial_key_native_init.py及evidence/initial-key-native-init-exploration.json。此前零RNG夹具导致取样不返回，不能靠扩大预算或强置成功绕过。下一项正式Ec2b和真实初始化来源，D-02仍未验。


Ec2b正式入口：`client72_ec2b`与`trace_client72_ec2b`，见[动态调试A-04d5u](docs/CLIENT_72_DYNAMIC_DEBUG.md#a-04d5u-原生ec2b独立验收)。16原生样本、228回归、重复产物通过；evidence/ec2b-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json、logs/ec2b-{run,repeat,regression}.log。初始化实际执行1411267B0/1408AF2A0，完整解码不设成功模型，fixedpair/LIVE RNG/独立递推交叉；raw短声明不是物理截断。真实调试进程已另验注册目标写入，暂停1411268C1，见evidence/dynamic-ec2b-registration.json。下一项analysis/probe_dispatch_key.py探索非空key/RSA多块/配置XOR，未正式验收。

分区key到真实Token帧入口：`trace_client72_dispatch_key_frame`，见[网络研究A-04d5v](docs/CLIENT_72_NETWORK.md#a-04d5v-分区密钥来源到token完整帧)。4原生样本、234回归、二次trace/摘要一致；原dispatch-config、dispatch-session、token-frame各6样本产物不变。证据evidence/dispatch-key-frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json、logs/dispatch-key-frame-{run,repeat,regression}.log；回归旧工具报告为evidence/*-key-regression.json。修复配置GetString读取旧密文字节缓存的问题，实际XOR与key源本身正确。两段入口调用之间只保留原key，未执行调度器；SDK/HTTP/实际客户端仍未验，不部署运行服务。A-04d5u提交0153d00。

动态启动补充：异常按原样交给应用处理后，目标继续并停在mhypbase.dll的TLS回调，Global仍0；未吞异常或修改校验。当前API状态/异常/日志为evidence/dynamic-startup-exception.json、dynamic-phase-13至15.json和logs/dynamic-phase-*.json；不能再按旧1411268C1暂停点运行脚本。后续只用Automate，禁止computer-use；下一步真实全局/SDK初始化或已知命名HTTP入口观察。

D-01b2已完成真实全局/UTF-8缓存限定核对：两次运行同对象/三个虚槽与声明相等，另4处代码32字节相同；共享startup-runtime-observations.json，详见CLIENT_72_DYNAMIC_DEBUG末节。**最后目标已经退出，不能沿用旧暂停状态**；先查询is_debugging，活跃索引dynamic-active-session.json内PID也可能已结束。erun传递所有首次异常，后改con 1+run；本机hardware watch表删除后寄存器仍残留的现象已实证并清理自设观察点。退出原因仍unknown，不绕过检查；继续离线Login/响应，目标active。A-04d5v提交2523513。

Handler→Login完整帧入口：`trace_client72_handler_login_frame`，见[网络研究A-04d5w](docs/CLIENT_72_NETWORK.md#a-04d5w-响应handler到login完整帧)。10原生样本（4发送/6不发送）、240研究回归、二次trace/摘要一致；Token原6组产物不变。evidence/handler-login-frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json、logs/handler-login-frame-{run,repeat,regression}.log及evidence/token-frame-login-regression.json。Login/68918 size与writer、ByteString/native copy执行，独立protoc比较全负载、Java比较安装key。仅响应reader→Handler至帧返回，非收帧/整个Handler/实机；无运行部署。D-01b2提交cf09603，当前真实客户端已退出，目标仍active。


A-03a方法级源码入口：`review_client72_login_surface.py`调用JDK17解析器`Client72JavaSurface.java`，复现及边界见[登录源码索引](docs/CLIENT_72_LOGIN_SOURCE_GRAPH.md)。固定12源码/9根，80方法/96边（13人工、83候选）；1012未关联调用及隐式初始化/管理器/事件/脚本边界保留。8新增/248研究回归、二次报告/摘要一致；`evidence/login-source-surface{,-repeat,-determinism}.json`、`evidence/login-source-summary-repeat.json`、`evidence/login-source-mapping-{check,ready}.json`和`logs/login-source-surface-{run,repeat,focused-tests,regression}.log`。源坐标使用原始UTF-16，hash规范CRLF，不能混算；61直接构造参数名/62总构造名不是7.2必需包集。用户要求本项完成后暂停，最新恢复资料见HANDOFF2；无运行代码变更或部署，D-02未验。上一A-04d5w提交6db47fe。

暂停交接已完成：A-03a研发提交00e0bee，目标paused；旧HANDOFF2已备份并核验，最新恢复入口见[HANDOFF2](HANDOFF2.md)，HANDOFF仅追加。用户恢复前不启动下一任务。


2026-11-06 A-03a收尾：重新执行固定12源码/JDK17解析、8项聚焦测试和研究映射校验均通过；重生成摘要与已提交产物逐字节一致，ready仍退出1（213缺口）。`local/migration72/evidence/login-source-close{,-summary,-check,-ready}.json`为本机复核证据。本次用户指令覆盖旧暂停状态，依次推进V-01b/V-01c/A-03b/A-03c，A-03c提交后停止；禁止B-03/C-01/D-02及实机调试。A-03整体仍待完整闭包和实机复核。

2026-11-06 V-01b：家具两套夹具由save-only代理改为独立Mongo真实Player/Item Guard及GameHome保存，故障计数只消费同步确认；测试专用home/库存/回调不入BSON。原31+34=65项通过（失败/错误/跳过均0），六阶段故障、防重复扣费、队列/回执和模糊确认恢复断言保留；新增材料及家园冷读。复现前家具65项中40失败，原始报告fixture-before-test-results/，修复后furniture-fixture-test-results/与logs/furniture-fixture-after.log（均在local/migration72）。只改测试，无生产/Proto/配置变更，无部署或客户端验收；V-01完整集尚未重跑，不推算剩余失败数量。

V-01b补验：共用本夹具的FurniturePaidStartRecoveryTest原31项中27失败，本次31项全过。三个真实Mongo/确认丢失样本现在复用夹具隔离库，显式重新绑定代理目标的Item Guard，按第二次家园同步确认注入故障；保留冷读付款、重试和整批队列断言。共用GuardedPlayerTestStore映射选项对齐生产storeEmpties(true)/storeNulls(false)，避免空队列被夹具省略。三套家具合计96项全过；本机workflow-fixture-test-results/、workflow-fixture-summary.json及logs/workflow-fixture-regression.log。扩查FurnitureTalentPlanTest仍因硬编码/resources初始化失败（历史全量同因），属于V-01未修范围，未删/跳过或宣称全量通过。

2026-11-06 V-01c：加工手册夹具改用真实Player/Item Guard及隔离Mongo，保留原18项断言和同步故障阶段；ProcessPlayer专用库存/标志/回调排除出BSON。新增真实手册进度、产物、未重复扣原料及失败后预留冷读，明确内存进度1而失败确认后的持久进度0，重试仍拒绝重复发放。修复前18项中12失败，现18项全过；与加工队列35、家具96、Player/Item保存保护45、手册领奖27合计221项通过，失败/错误/跳过均0，证据workflow-fixture-test-results/、evidence/workflow-fixture-summary.json及logs/workflow-fixture-regression.log（local/migration72）。原断言未删未放宽；未改生产逻辑，无部署/实机验收。完整Java集未重跑，历史217失败不据此扣减。

2026-11-06 A-03b限定源码复核完成，见[登录管理器与事件依赖](docs/CLIENT_72_LOGIN_DEPENDENCIES.md)。48固定源码、293方法、409边（72人工调用位置/337候选），2868未关联保留；10处插件/替包/异步/脚本/服务开放锚点均未晋级。枚举合成跨度实际复现已修正，A-03a计数不变。14聚焦/254研究回归通过，两次报告/摘要相同。下一项A-03c候选消息/Proto及7.2三层缺口；A-03整体仍待完整闭包/实机。无运行变更、部署或实机调试。

2026-11-06 A-03c限定离线验收完成，见[候选消息/Proto与三层缺口矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md)：97包构造、148引用/种子、1139文件描述符依赖，缺DoSetPlayerBornDataNotify.proto如实保留。原三层状态及185字段不变；6新增/260研究回归、重复矩阵通过，研究校验0/ready1（213缺口）。V-01b/c经济/保存221项通过；完整Java集未重跑。按用户要求到此停止并交接Astra，下一研究为B-02选人三件组的独立7.2三层证据；A-03整体仍待完整闭包/实机。未改运行文件、未部署或调试。

交接收口：A-03c验收提交411d522后停止，无下一研究动作。旧HANDOFF2先备份并逐字节/hash核验，HANDOFF仅追加；详细入口见[HANDOFF2](HANDOFF2.md)。测试Mongo已正常关闭，原Mongo/数据/他人差异保留；无运行部署或客户端调试。

## B-02d选人三件组停止点（2026-11-06）

入口[CLIENT_72_BORN](docs/CLIENT_72_BORN.md)：`trace_client72_born.py`固定3输入hash，39组限定原生样本、独立wire/protoc；`test_client72_born`6项全过，原260项研究加本项共266项全过（无失败/错误/跳过）。二次trace和摘要逐字节一致。研究校验退出0，ready退出1/204缺口。三条状态为研究verified、runtime仍not-tested；字段总188（41V/9C/138U）。矩阵L1/L2各10V/138U，L3为3V/7C/138U，遗留缺根不变。

本机证据`evidence/born-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready,final-environment}.json`，日志`logs/born-{focused,repeat,regression}.log`。实际执行三getter/分派/请求构造与writer/size/响应reader与Handler、目标继承空schema；wire primitive/UTF-8/UI存储为显式模型，真实昵称validator、客户端/SDK/队列/完整成功继续均未验。不得将空通知等同于所有Rsp为空。

生产代码/Proto/配置/补丁未改，无部署或数据库测试；Java及游戏进程不存在，只有本工作区Mongo27017与两个既有调试器，均未操作。旧经济/保存221项通过仍属前批，本轮不冒称重跑。B-02d闭合后停止，给Sol的下一项规格在专题；不进入B-03/C-01/D-02。

B-02d收口：验收提交36eb486；先建卡3fc7e80。旧HANDOFF2已先备份并核验，详细停止状态与Sol规格入口见HANDOFF2，HANDOFF只追加。本轮研究已停止，未推送。

## B-03a/C-01a当前实现批（2026-11-06）

本次指令覆盖前批停止点；任务先建卡d2cfb14。B-03a生成提交c6bf035，V-01d测试路径修复9fe3bb2；C-01a业务/源码复核已完成回归，构建与最终交接记录见详细交接。父B-03/C-01仍进行中；所有客户端runtime仍未验。

- 生成：`python tools/generate_born72_sources.py --client $env:GAME_PATH`，重复用`--check`，实际输入hash与三层证据/引用/别名/冲突均受门禁；仅三件组，没有旧号兜底。Java由Gradle/protoc生成。
- Java：最终21套495项，失败/错误/跳过均0；19项BornDataFlowMongoTest、15项Protocol72BornCompatibilityTest、原33项LocalLoginAdmissionMongoTest，以及账号/UID/Account/Player/Item/Activity Guard与恢复、写入队列/栅栏/屏障和原221项经济保存选集。独立测试Mongo/随机库，不接正式存档。完整集未重跑。
- 研究：tools/test_client72*.py及test_extract_client72/test_validate_protocol72/test_audit_resource72/test_inventory_protocol/test_review_protocol72_surface/test_generate_born72_sources共272项通过；研究校验0，ready1（204缺口）。摘要/矩阵重生成重复一致，三层状态不晋级。
- 源码审计：A-03a当前81方法/95边/1000未关联；A-03b当前294方法/408边/2856未关联，10开放锚点不变；矩阵99构造/148种子/1140文件，missingProtoRoots为空由目标通知证据正式接管，非猜空message；动态来源15处/8组。
- 复现：`evidence/born-legacy-parser-reproduction.json`证明旧tag不能读出目标字段；首次467项的测试路径失败快照born-guard-before-test-results/保留，修后born-guard-final-test-results/和evidence/born-guard-final-summary.json核对495项。logs/born-{guard-final,research-regression}.log、evidence/born-{source-surface-review,login-source,login-dependencies,matrix-repeat,implementation-mapping-check,implementation-mapping-ready}.json都在local/migration72/。

部署前置仍缺完整P0/成功继续与真实必需首包序列、D-01实际SDK/信任/补丁加载、E-01资源revision/Lua/场景；不启动服务/游戏，不改版本。Astra最小后续取证为B-02e，固定输入沿Rsp0→14CD68860的继续与新旧号序列核对，范围/停止条件见[CLIENT_72_BORN](docs/CLIENT_72_BORN.md)。

最终收尾：C-01a代码/源码审计提交8bd338f；之后便携-Mode build退出0，JAR10类/代码提交相符、旧fallback/配置类均不存在。未部署，无HTTP/游戏/测试端口监听及Java/游戏进程，健康和运行JAR无对象不标通过；原Mongo保留，测试实例核对空测试库后正常关闭。报告evidence/born-{build-check,implementation-final-environment}.json及logs/born-portable-build.log。B-03a/C-01a/V-01d限定项完成；父任务仍进行中，已整理B-02e最小取证并停止新增工作，交接见HANDOFF2。
