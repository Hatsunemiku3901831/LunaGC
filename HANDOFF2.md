# 国服 7.2 收尾交接（2026-11-11，暂停）

按用户要求，当前已启动的 CLEAR48-FULL 实现与人工首组、Proud 固定单 key 参考、Scene MPLevel parser 原窗已验收并本地提交。当前无在途执行包和待用户操作。D-02、F-05、D02-R6 保持未完成。恢复后依次读 AGENTS、README、Project、Harness、本页、迁移研究和部署手册，再进入所选专题。任务状态以 [Project](Project.md) 为准，复现入口见 [Harness](Harness.md)。

## 现场和交付边界

- 分支 `7.2-dev`，本批起点 `a38aef7`，交接前验收提交 `8b045c6`。本地提交均未推送。外部用户提交 `a465a5c` 和当前 AGENTS 修改保留。交接文档提交见 Git 日志。
- patch 实际 HEAD 与 gitlink 均为 `50f9bf82b3dbc0d4857321417dccf40f78c13ce4`，保留组合 loader 的未验 `Cargo.toml` 与两个 example 源位置。resources 实际 HEAD 与 gitlink 均为 `dac2a9d43332ef51879522cf39da2831b0ca0543`，保留既有 `ScriptSceneData/flat.luas.scenes.full_globals.lua.json` 差异。两处差异均未夹带。
- 新盘点在 `local/migration72/d02-resume-20261110b/closeout-20261111-paused/final-inventory.private.json`，SHA-256 `f98b064095ca23f21f9bfe37b63e97c50df83a73d3b3124fc17fbad7fe010a12`。用当前 GAME_PATH 复核五份 stock 输入和 SDK carrier。13 项保护中 12 项和起始一致，另保留用户 AGENTS 差异。独立核对原 `mongodata/` 129 文件和 `local/data/` 9 文件均与旧号快照原件一致。原测试库副本和失败原件保留。
- 最新 CIM 盘点未见相关游戏、Java、Mongo、调试器、补丁 guard 或自有实验进程。固定 TCP 8088/22101/27017/27027 和 UDP 22101 无监听。portable/config 14 有效，`.env.local` 与临时原件别名缺席。磁盘 JAR SHA-256 为 `8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`。R4 前置仍待，GS 实例未部署，当前无运行 JAR、服务 PID 或健康验收对象。
- 覆盖前已将 HANDOFF2 原件备份到本批 `closeout-20261111-paused/before/HANDOFF2.md`，SHA-256 `71460dd598a7e17121470c73bdb2fc1b199c0f36f5946524f520988e41ceafba`。Project 的更新前原件在同目录。HANDOFF 仅追加。

## 已完成的限定范围

| 任务与主要提交 | 验收与证据 |
| --- | --- |
| F-03d3f4d2/A2 与 GC/Default 输入，`2de9fa2`、`2ac7463` | A2 新现场取得第二请求 U16=56/bin4 和 L64/flags8，72 读 1568 B。另一次新现场取得同 epoch 42 稳定跨度、84 读 1648 B，I=6 与 T0=524288 选择原 AVX 表，Default 仅为不透明非零值。两轮用户退出，当前文件、进程和端口已复核。GC/Default 轮只收到「已退出」，页面及异常表现未知。见 [A2 实采](docs/CLIENT_72_D02_A2_STOCK_CAPTURE_20261111.md) 和 [GC/Default 实采](docs/CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md)。 |
| F-03d3f4d2/CLEAR48 退出生命周期，`eb4a998` | W1 真实 CALL/RET 与非零 64 位结果、W2 两笔向量写及守卫、W3 UD2 未退休和受控清理通过。旧三次 W1 失败各保留。见 [三个 witness](docs/CLIENT_72_D02_CLEAR48_TRACE_RESULTS_20261111.md)。 |
| F-03d3f4d2/CLEAR48-FULL Shared、Parent、Child、Validator，`8b045c6` | 实际 MSVC、67 偏移断言、PE/调用者/unwind/依赖审核及 33 项 Python 通过。257/258 两个自有 child 各一次真 CALL，37/40 条 helper 退休，56 读 1878 B / 58 读 1929 B，分别验证 3×16 B 与 2×32 B（重叠 16 B），写域各 48 B。守卫、真实 RET、返回 C、释放、父子退出 0 及各 309 文件前后哈希通过。见 [构建、原件与 BOOT 合同](docs/CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md)。 |
| R2 请求采用的限定出口，`77e2e83`、`872599a`、`f47a685`、`61bb1eb` | 选择语义和 URL 分类、PostRequest 及原生共用服务入口的源码合同与独立 owned 验收已交。原八点配置、锁内 map 和 stock v2 managed env/base 继续复用。两个独立 owned 包各有真实 emitters 64/208。见 [PostRequest](docs/CLIENT_72_SDK_POSTREQUEST_OWNED_20261111.md) 和 [共用服务](docs/CLIENT_72_SDK_SERVICE_ENTRY_OWNED_20261111.md)。 |
| E-01d1/R3 旧号依赖与 Proud，`3abf8ab`、`88f7664`、`09feca1`、`79a5369`、`76800fc`、`dbbfd34` | 已验所选旧号隔离副本的实际归属、121 角色与 12254 件物品，以及 Depot 真实 getter。固定 Proud key 22101 已验条件文件语法，81 个原字节跨度与 payload 对齐至 334，16 项检查通过。见 [旧号快照](docs/CLIENT_72_D02_OLD_ACCOUNT_SNAPSHOT_20261110B.md) 和 [Proud 单 key](docs/CLIENT_72_D02_PROUD_SINGLE_KEY_REFERENCE_20261111.md)。 |
| F-03d3s2 Scene，`b78f2e6`、`0f8e5ea`、`4071512` | 已验模板关联和 MPLevel 的 PathHash 第二 QWORD 到 reader、parser 和 register 来源。实际 817 B caller、E8、AOT 单元及唯一异常目录项给出 131 B/42 条 parser 原窗，4 项门禁通过。见 [分组来源](docs/CLIENT_72_D02_SCENE_MPLEVEL_GROUP_SOURCE_20261111.md) 和 [parser 原窗](docs/CLIENT_72_D02_SCENE_MPLEVEL_PARSER_WINDOW_20261111.md)。 |

此前出生默认装备、303、Metal、AstaPS loader、Promote/Curve、Skill/Depot/talents 的限定验收可直接复用。此前的 Java/Mongo、账号、经济及 Lua 通过项保留原证据和测试范围。

## 失败与剩余依赖

- CLEAR48 的首个 SSE 在旧 BOOT 主线程限制下收到了实际自有新线程通知。父进程退出 2，子进程按清理退出 90，helper 未执行。保留该轮原件及严格 checker 拒绝。新合同独立绑定新源码、构建和运行，只让真实 BOOT 通知走已有来源表，逐步跟踪仍限定主线程。Sol max 在合同交付前遇到 Codex 客户端版本不兼容，主线接手草稿，修正固定来源计数并完成合同审核、实现和验收。
- A 的人工路径支持本轮自有写域结论。自然 allocator 线程、GC 所有权、stock 输入的原条件路径、第二分配返回和 Default 消费尚待。完整 Avatar 有 20 个直接分配位点和 12 个根，含子构造；自然 String Hash/FindEntry 仍缺准确 tuple、caller 和 trigger。旧 cold 普通 10294、enum、fresh 各自保留状态。
- B 组合 loader 首编译退出 101，`observer.rs:49` 的 u64/usize 不匹配。后续遇平台安全门禁，已按用户要求保留材料给未来 Daybreak 处理。自然服务选择、实际请求采用、key/信任、认证、会话交换及未验 unwind 保持开放。托管 env/base、native map 和实际请求必须各自按 epoch 归属。见 [Daybreak 队列](docs/CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)。
- C 的 Proud 单 key 参考只覆盖固定文件语法。实际 typed 行、group/level/提升门槛、defense/common/LKLP、自然配置命中、必要资源和完整旧号装备/Player 恢复仍待。旧 U32 header 被误标 16 的失败已保留，新结果按 32 位核。八件 Item 的候选命中和出生装备冷恢复各有独立范围。
- Scene parser 语义维持「待开始」。131 B 原窗的机械首调用还需区分实际数据消费和条件 refill。实际 World 返回对象的 slot114、MPLevel 分组值、Pool/Scene 与组件创建/替换/拆除、通知顺序未闭。整行包装器和回收入口已分别排除为字段消费者和实体构造器。

D-02 六项客户端验收全部保留未通过状态。尚需实测旧号本服登录和归属、新号显式选人命名及错误/重复请求、完整初始化与场景握手、实际入场、连续十分钟心跳，以及两类账号退出重登的一致性。

## 恢复后的可执行动作

收到恢复指令后先核新 HEAD、差异、配置、固定哈希和现场身份。A 从已验人工写域与 GC/Default 的实际读证据出发，定界 stock 条件路径的必要输入、自然线程与 GC 所有权，再决定下一次有界验证。Scene 可在现有 131 B 窗口先区分内联消费与 refill，再定首个缺输入。C 从真实旧号依赖选择入场必需的剩余 Proud 和装备恢复验证。B 的组合 loader 按 Daybreak 队列交接。

必要合同成熟后按 Project 推进正式 Avatar 和 Scene/Team/Entity 发送、R4 汇合、R5 Windows portable 隔离部署及 R6。需用户实机操作时，先完成离线准备和退出恢复，再取本轮新的实时 ready。当前没有待确认的启动或待恢复的 DLL。
