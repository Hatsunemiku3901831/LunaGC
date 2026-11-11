# LunaGC 国服 7.2 开发分支

LunaGC 是基于 Grasscutter 的游戏服务端项目。本分支的目标是原生适配所提供的国服 7.2 客户端，包括网络协议、客户端补丁与资源。本分支只面向国服 7.2；7.1 的代码、协议和资源仅作业务理解与映射研究的参考，不维护双版本运行环境。

**完整的 7.2 客户端登录与入场验收尚未通过，当前不能宣称可直接游玩。** 协议研究、局部测试、构建或服务健康均不能代替客户端验收。任务状态和具体通过范围以 [Project](Project.md) 为准。

## 项目已有能力

以下是服务端已有实现，不表示已与 7.2 客户端完成适配：

- **账号、角色与存档**：账号认证、UID 归属、玩家数据与 MongoDB 保存，以及角色、装备和库存管理。保存涉及多个集合和运行数据，详见[账号与存档系统](docs/ACCOUNT_AND_SAVE_SYSTEM.md)。
- **世界与玩法框架**：世界、场景、实体、队伍、任务和脚本等服务端逻辑；相关资源和协议仍须按目标版本验证。
- **玩法及经济实现**：包含深境螺旋的选队、场景交接与结算等逻辑，以及普通商城、免费充值、月卡、纪行和活动商店的服务端实现。各自功能边界见[深境螺旋系统](docs/SPIRAL_ABYSS_SYSTEM.md)和[商城与充值系统](docs/SHOP_AND_RECHARGE_SYSTEM.md)。
- **本地工具**：提供 Windows 便携运行、服务管理和客户端补丁相关入口；补丁适配与实际部署需要单独验证。

## 7.2 迁移范围与已验证片段

项目将 **CmdID→消息类型、类型→业务语义、字段→wire** 分层研究和验收，固定输入并记录证据与歧义。已验的局部成果包括：

- 元数据和静态网络映射研究，以及部分包头、心跳、登录请求和响应对象图的 wire 恢复；部分消息和字段已结合独立样本、Java 或目标原生消费者验证。见[协议模型](protocol/cn-7.2/README.md)、[网络研究](docs/CLIENT_72_NETWORK.md)和[登录 wire](docs/CLIENT_72_LOGIN_WIRE.md)。
- HTTP 路由、会话密钥和登录链路的限定验证，包括真实 HTTP 往返与部分原生会话处理；自然 SDK 初始化、路由、认证和完整连接仍需闭合。见[会话研究](docs/CLIENT_72_SESSION.md)及 [Project](Project.md)。
- 资源来源、索引与内容的研究，以及部分资源在真实 Java 加载、保存和恢复路径上的局部验证；完整资源版本、必要加载与管理器恢复仍待验证。见[迁移研究](docs/CLIENT_72_MIGRATION.md)及 [Project](Project.md)。
- 角色与场景初始化的部分结构、消费者和场景数据已作限定验证；完整角色、队伍、实体及场景握手尚未闭合。见 [Project](Project.md)。

这些片段不等于完整业务映射或可用客户端链路。D-02 仍需在隔离环境完成新旧账号登录、选人、初始化、实际入场、心跳及退出重登保存的实机验收；通过条件见 [Project 验收标准](Project.md#5-验收标准)。

## Windows 便携运行

本分支使用 `LUNAGC_RUNTIME=portable`，以 [tools/local_windows.ps1](tools/local_windows.ps1) 和对应 `.cmd` 为入口。依赖、本机配置、客户端与资源不随仓库完整交付。准备环境、检查端口和数据、构建、部署及验收前，先读[部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md)；路径以当前 checkout 和本机 `GAME_PATH` 为准。

当前 7.2 协议、补丁和资源前置尚未全部满足，历史部署命令或旧版本示例不能作为启动目标客户端的依据。保留已有数据库与运行数据；测试应使用独立数据。Proto 只修改源文件，Java 由 Gradle/protoc 生成。

## 文档导航

- [AGENTS.md](AGENTS.md)：工作规范与接手约束。
- [Project.md](Project.md)：任务、依赖、状态与验收标准。
- [Harness.md](Harness.md)：研发索引、测试及复现入口。
- [HANDOFF2.md](HANDOFF2.md)：详细交接、未完成项与阻塞；[HANDOFF.md](HANDOFF.md) 为简要交接。
- [7.2 迁移研究](docs/CLIENT_72_MIGRATION.md)与[部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md)：适配方法和 Windows 便携运行。
- [账号与存档](docs/ACCOUNT_AND_SAVE_SYSTEM.md)、[深境螺旋](docs/SPIRAL_ABYSS_SYSTEM.md)、[商城与充值](docs/SHOP_AND_RECHARGE_SYSTEM.md)：系统设计、数据、接口及验证入口。
- [README 历史原文](README.archive-2026-11-11.md)：仅供追溯，不作为当前状态或执行指令。

README 只在项目范围、对外能力、运行要求或重要使用说明发生变化时更新；普通任务完成、提交、测试及暂停/恢复不写入 README。任务进度与验收更新在 Project，过程记录与复现入口放在 Harness 或专题文档。

## 上游、资源与许可

本项目基于 [girluh/LunaGC](https://github.com/girluh/LunaGC)（基于 Grasscutter），客户端补丁参考 [capyb2222/animegamepatch](https://github.com/capyb2222/animegamepatch)；感谢 Terax（nt）。[MeChen618/AstaPS](https://github.com/MeChen618/AstaPS) 曾用于对照，来源、许可和使用范围见[审计](docs/CLIENT_72_ASTAPS_AUDIT.md)。

资源来源、贡献者及权属说明见[资源 README](resources/README.md)；补丁许可见 [patch/LICENSE](patch/LICENSE)。仓库根目录没有独立 LICENSE 文件，各来源及组件的许可说明应分别查阅。
