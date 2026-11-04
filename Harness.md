# 研发索引与执行骨架

[AGENTS](AGENTS.md) 管规范，[README](README.md) 管能力说明，[Project](Project.md) 管任务验收；本文件维护索引与可复现流程，不重复长状态清单。目标仅国服 7.2。

## 仓库入口

| 路径 | 作用 |
| --- | --- |
| src/main/java/emu/grasscutter/Grasscutter.java、build.gradle | 运行入口、版本、依赖、Gradle/protoc |
| src/main/proto/ | 遗留协议源，尚未按 7.2 验证 |
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

新增已实现：verify_client_manifest（完整清单）、extract_client72（固定目标基础元数据及泛型/父类/静态字段布局）、extract_client72_commands（静态包号）、extract_client72_wire（两消息研究Proto）、inventory_protocol（静态协议表面与P0 import下界）。证据说明：[目标提取](docs/CLIENT_72_EXTRACTION.md)、[网络与wire](docs/CLIENT_72_NETWORK.md)、[协议盘点](docs/CLIENT_72_PROTOCOL_INVENTORY.md)。尚未实现：完整P0调用图、运行时闭合泛型ABI、正式业务源码生成和完整7.2 wire fixture。

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

选择测试时，--tests紧跟test任务；serverSmokeTest单独调用，不要把过滤参数放在第二个任务后面。实际遇到的端口冲突证明测试与研发服务不能并行抢占22101。测试完成、释放端口后再部署；不要只看HTTP健康。

## 三层映射校验入口

已建立 [protocol/cn-7.2](protocol/cn-7.2/README.md)。profile固定输入，mappings分opcodes/messages/schemas，完整闭包未确认。commands.json单独保存静态CmdID→混淆类型绑定，不自动提供业务名称。证据和保护入口阻塞见 [网络研究](docs/CLIENT_72_NETWORK.md)。运行 `python tools/validate_protocol72.py` 校验研究记录；`python tools/validate_protocol72.py --ready` 当前应退出1。未知不能作为正式常量生成。测试 `python -m unittest tools.test_validate_protocol72 -v` 使用合成模型，不是目标wire测试。

wire-observations.json为目标tag/offset/类型/helper的静态观察；research.proto是原始混淆名的离线产物，不能复制为正式业务Proto。check_client72_wire_proto用手写目标wire断言，输入是合成值，非客户端抓包。本批复现证据：logs/p0-research-tests.log、wire-proto-tests.log；evidence/wire-summary.json、mapping-check.json、mapping-ready.json。commands与wire重复生成必须逐字节一致。接手保护入口先读CLIENT_72_NETWORK的VA和已尝试方法，原始反汇编在analysis/，不要把填充字节或IFix替代路径当作正常逻辑。

本批构建/测试/部署索引：[CLIENT_72_RESEARCH_VALIDATION](docs/CLIENT_72_RESEARCH_VALIDATION.md)。完整失败快照full-test-results/、限定通过targeted-test-results/、汇总evidence/*-summary.json均在local/migration72/。运行验证deployment.json与jar-class-check.json；配置备份private-before/含敏感信息，仅本机读取。能力资源测试支持LUNAGC_RESOURCES_DIR，默认仓库resources/；历史/其他套件可能仍硬编码容器路径，执行前检查。
