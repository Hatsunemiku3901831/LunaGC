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

# 编码规范
- 不要为猜测出来、几乎不会发生的竞态或安全边界情况加复杂度。真觉得要加，先说清楚具体会怎么出错、概率多大、要多写多少代码，我同意了再做。不要悄悄扩大改动范围。
- 外部传进来的数据，只在入口那一层校验一次。往里传已经确定类型的值，内部不要反复做防御性检查。
- 不要提前抽只用一次的函数。只有会复用、包住了确实复杂的逻辑，或者它代表一个能起清楚名字的概念时才抽。
- 主函数只写正常流程，校验和细节放到它下面的小函数里。
- 入口只校验一次，拒绝重复判空和滥用 try/catch；单次逻辑不乱抽函数，低概率防御先征求同意

# 开发前先看
- 在开始进行新任务（大任务，小任务就没必要这样做了）前，先去GITHUB全网爬取有没有类似能复用的仓库和成果（GITHUB上有没有仓库能做到这个）（重点爬取分支），避免重复造轮子（使用GPT 6.1 sol max制定查找计划和规划查找（中文md文档），6.1 Terra max根据计划进行检索和爬取），如果有则克隆到本地（不要放在本仓库内），并分析和融合可复用的部分

涉及专题时先读，并同步受影响章节：

- 账号/认证/存档：[系统](docs/ACCOUNT_AND_SAVE_SYSTEM.md)、[数据模型](docs/ACCOUNT_AND_SAVE_DATA_MODEL.md)、[运维](docs/ACCOUNT_AND_SAVE_OPERATIONS.md)。
- 深境螺旋：[系统](docs/SPIRAL_ABYSS_SYSTEM.md)、[协议](docs/SPIRAL_ABYSS_CLIENT_PROTOCOL.md)、[资源](docs/SPIRAL_ABYSS_DATA_AND_RESOURCES.md)、[验证](docs/SPIRAL_ABYSS_OPERATIONS.md)。
- 商城/充值/月卡/纪行/活动商店：[系统](docs/SHOP_AND_RECHARGE_SYSTEM.md)、[数据](docs/SHOP_AND_RECHARGE_DATA.md)、[接口](docs/SHOP_AND_RECHARGE_PROTOCOL.md)、[验证](docs/SHOP_AND_RECHARGE_OPERATIONS.md)。当前充值为免费发放；普通商城、通用活动币、熄星账本分别核查。


# 交接流程（每次进行交接的时候按照以下工作流）
先备份原HANDOFF2.md，然后覆盖HANDOFF2.md写入详细的交接文档（如果没有就生成），内容包括当前状态，已完成内容，未完成内容，当前阻塞，走的弯路和如何避坑，下一步计划动作，然后HANDOFF.md写入简略交接和HANDOFF2.md的引导。HANDOFF.md持续维护，如果没有特殊情况和要求，HANDOFF.md只增加写入不删减