# 工作规范

- 本分支只面向国服 7.2。7.1 资料仅供理解业务和恢复映射，不维护双版本、回退构建或旧客户端运行环境。
- 接手先读 [README](README.md)、[Project](Project.md)、[Harness](Harness.md)、[迁移研究](docs/CLIENT_72_MIGRATION.md) 和 [部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md)。Project 管任务和验收，Harness 管索引和复现；每批工作后更新实际状态。
- 路径以当前 checkout 和本机 GAME_PATH 为准，不套用历史路径、PID、测试数量。启动前核对 .env/.env.local/config.json、子模块、进程与端口归属。
- 使用 Windows 便携方式（LUNAGC_RUNTIME=portable），不部署 Docker/WSL 或启用虚拟化。入口为 tools/local_windows.ps1 及对应 .cmd。HTTP/SDK、MongoDB 固定端口须按源码核查，不能只改环境变量声称隔离。
- 实际研究、开发、测试和部署；优先依赖满足的任务。缺资料先查本机、已有工具和可验证来源，记录尝试及解除条件，继续可独立工作。
- 协议输入固定文件哈希；CmdID→类型、类型→语义、字段→wire 三层分别记录来源、歧义和验证状态。未知保持未知；不沿用未核实的旧地址、解码参数或正包号，不以改版本、跳过校验、静态命中、编译或健康通过代替实机验收。
- 问题优先有效复现/回归测试。协议需独立样本或 wire 断言，覆盖非零、64 位、嵌套、列表、错误和边界；保留版本无关的账号、经济、保存回归。只改 Proto 源，通过 Gradle/protoc 生成 Java。
- 使用独立测试数据库与运行数据；保留已有 mongodata/、local/data/。不清库、不强杀未知进程、不操作无关客户端。正常停止本项目旧进程后更新构建。
- 修改运行代码、Proto、配置、补丁或资源后，完成对应构建、便携部署及日志/健康/TCP/UDP/PID/运行 JAR SHA-256 核对。前置条件不满足则记录未部署原因，不启动猜测协议。仅文档、离线研究工具或测试修改无需重启。
- 每个独立验收项及时本地 Git 提交，不自动推送。先查差异归属，保留且不提交他人修改；子模块单独核对提交与父仓库引用。工作完成再交用户检查。
- 机器路径、令牌、密码、客户端二进制、原始抓包、依赖、日志和数据库留在忽略目录。共享文档使用相对路径或配置变量；只提交脱敏证据摘要。
- 阶段交付列任务 ID、修改、实际测试、客户端验收范围/缺口、本地提交、文档更新和下一项可执行任务。

涉及专题时先读，并同步受影响章节：

- 账号/认证/存档：[系统](docs/ACCOUNT_AND_SAVE_SYSTEM.md)、[数据模型](docs/ACCOUNT_AND_SAVE_DATA_MODEL.md)、[运维](docs/ACCOUNT_AND_SAVE_OPERATIONS.md)。
- 深境螺旋：[系统](docs/SPIRAL_ABYSS_SYSTEM.md)、[协议](docs/SPIRAL_ABYSS_CLIENT_PROTOCOL.md)、[资源](docs/SPIRAL_ABYSS_DATA_AND_RESOURCES.md)、[验证](docs/SPIRAL_ABYSS_OPERATIONS.md)。
- 商城/充值/月卡/纪行/活动商店：[系统](docs/SHOP_AND_RECHARGE_SYSTEM.md)、[数据](docs/SHOP_AND_RECHARGE_DATA.md)、[接口](docs/SHOP_AND_RECHARGE_PROTOCOL.md)、[验证](docs/SHOP_AND_RECHARGE_OPERATIONS.md)。当前充值为免费发放；普通商城、通用活动币、熄星账本分别核查。
