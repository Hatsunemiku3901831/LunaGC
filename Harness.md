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

已有工具为上表 audit、旧 extractor、Gradle/protoc、脚本测试、便携部署。计划新增（未实现）：安装清单检查、协议表面盘点/P0 图、三层映射校验/生成、目标提取器和独立 wire fixture。实现后补真实命令。

## 可复现命令

在仓库根执行，先将已核对的本机 GAME_PATH 赋给当前 shell；Python 不自动读 .env。

```powershell
git status --short
git submodule status
git -C resources status --short
git -C patch status --short
python tools/audit_client.py --client $env:GAME_PATH --output local/migration72/evidence/client-audit.json
python -m unittest tools.test_audit_client tools.test_local_windows tools.test_patch_game tools.test_runtime -v
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
