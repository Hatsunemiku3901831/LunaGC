# D-02 Sol/Terra 离线前置执行（2026-11-07）

用户已恢复此前暂停的开发，范围限静态、无人值守自动执行、证据明确的实现及验证；不启动或操作原始客户端、调试器，不装补丁、不部署游戏服务，不调用 Astra。仅国服 7.2 / Windows portable。接手 HEAD `4b04a1d`，原 resources 子模块差异保留。

## 依赖和本轮出口

顺序为证据审核 → 网络入口边界、Chapter/资源审计、有限 Java 投影 → 关联回归/构建/交接。资源自然加载且所需记录有效后才允许 QuestManager 正向保存恢复；不手工设置 loadedAll、不删有效存档。Avatar 未知构造和必经后缀、复杂 SceneInfo、未知资源语义/revision 关系后置，不因准备段通过而接正式 g2。自然 SDK/HTTP、原始游戏 HW、R5 部署和 R6 客户端验收本轮跳过。

本机接手证据在 `local/migration72/offline-sol-terra/initial-inventory.json`：五份 profile 输入逐 SHA-256 相符；`.env.local` 不存在，配置 HTTP 8088 / 游戏 22101、便携 Mongo 固定 27017 已按源码核对；只有原 Mongo 监听，测试 27027/8088/22101 无监听。无本项目游戏或 Java，原调试器保持未动。配置、JAR、资源原差异及数据目录全部保留。机器路径和进程明细不进入共享文件。

## 0：A-03/B-01/R0/R4 证据审核

最后统一 review `d32c68a` 的 108 输入作为基线。本次精确接纳下表八个已提交、主线审核范围明确的摘要，以及启动摘要的一个追加历史对照；没有重签旧工具 hash。主线独立复核提交内容、原始引用文件 hash、正式 review 证明和历史工具 Git blob 后，保留旧 review 对 startup 漂移的真实拒绝，再用原生成器连续恢复两次，输出完全一致。

| 新摘要 | 子任务 / 提交 | 只批准的范围 |
| --- | --- | --- |
| avatar-fight-props | f4b / `00d03cf` | 七个具名 ID 的 float map 消费；省略清旧值，完整战斗及数值来源未验 |
| scene-info-raw | s1 / `3edb025` | 9 类 36 raw 字段；83320 tag2/3 fixed32，复杂非空后代未验 |
| software-breakpoint | D-01b5f / `faadfc8` | 既有合成 fixture 对照；不是原始游戏自然命中 |
| avatar-skill-depot | f4c / `bd53b11` | 当前 wire depot 与默认资源分开，+6c 不代替 +68 |
| http-route | D-01b5h1 / `ca3b678` | 真实临时 HTTP 路由及签名版本拒绝，拒绝保留 |
| single-hardware | g-preflight / `cd668cc` | 合成及观察器前置；游戏 HW 未执行 |
| scene-info-contract | s2a / `11bb8fc` | GUID/实体四字段身份关联；51522 不命名 Weapon |
| avatar-initializer | f4d1 / `0554bd4` | 精确构造/省略片段；完整 ctor/handler 未验 |

`startup-observer-observations.json` 仅追加 `singleGlobalDispatchContrast`（`942205c`），原有键逐项相同。该历史实机退出证据没有在本轮重做。旧工具 bfdf、旧顶层 d4f293 与现 2298 分属不同执行快照；旧六处 Lua 审计工具 hash 也各自能对应历史 Git blob，不改成当前工具版本。

统一 review **108 → 116 输入**，原 107 项摘要及 1269 项生产来源保持，98 发送候选 / 156 根与矩阵 / 1122 描述符文件 / 1278 类型 / 4867 字段保持。三层、required、openBoundaries、closureComplete=false、targetProtocolReady=false、runtimeStatus=not-tested 均不变。24 项原矩阵/校验定向检查通过，结构 0 错误；ready 退出 1、224 缺口如实保留。

文件字节 hash 与 review 使用的 UTF-8/LF 文本 hash 分开记录。详细审计 `local/migration72/offline-sol-terra/evidence-audit/audit.json` 字节 SHA `f0bb0bdf68229001db44a86b88168c2d7bf429c911fc34571eb368676999da81`；主线接纳、拒绝、双恢复及日志在同级 `evidence-integration/`，不是新增客户端运行证明。

## 生产入口对应

- Avatar：`PacketAvatarDataNotify → Avatar.toProto → AvatarDataNotify/AvatarInfo` 仍为旧共享定义。独立 `Client72AvatarWire.proto` 的 65 类不在该生产调用内；g2 准备只输出测试包。
- 场景：`PacketPlayerEnterSceneInfoNotify` 及队伍/世界/实体通知仍需各自目标合同；s1/s2a 不是完整发送许可。已接管的八握手和有限 EnterScene、PlayerData、PlayerStore 保持其既有范围。
- 资源：根配置 fastRequire=true 保留。既有 inline 夹具和混合来源逐份保留，不作为目标 revision 或完整资源 ready。

网络、资源和有限投影的后续实际结果由各专题及本文件后续记录；本节是已执行的证据整合，不把后继标为通过。

## 1A：C-01b 网络入口边界

[网络边界](CLIENT_72_NETWORK_BOUNDARIES.md)先经真实接收/注册/事件替包入口红测40项、24个预期失败，再最小修复三个生产文件；绿测36帧+7注册全过。实际反射发现324类、启用227/注册227/冲突0。未知Union、别名、业务号及完整初始化未扩大，C-01/R4父项不关闭。

主线逐行复核后，表面review只更新3个实际来源指纹；49源依赖只更新GameSession，294方法/411边及边界保持；Proto review只更新GameSession/BasePacket及相应依赖摘要，116输入/1269来源、98候选/156矩阵/全部描述符与三层/required/runtime保持。旧拒绝及双恢复证明在`local/migration72/offline-sol-terra/network-source-review/`。首次主线比较器漏排除嵌套javaSourceSHA256的失败保留，仅修比较器，不放宽语义断言；一次测试模块名写错的导入错误也保留，正确入口后实际120研究检查通过，9生成器check无漂移。

独立Mongo关联回归实际50套875项全部通过，失败/错误/跳过0（其中包含网络43，不能与绿测重复相加）。原47套账号/经济/保存/Lua/协议及CombatSessionSequence和两网络套件使用独立运行目录；关闭前仅admin/config/local，Mongo正常退出且27027释放。证据目录`local/migration72/offline-sol-terra/mongo-e825b6f516564e888f0b4d5bce19d2da/`。首轮新cwd漏接相对输入导致41个路径失败，目录`mongo-8c106c20b11743a8a73280ccc13b74fc/`保留；补齐既有夹具的resources/protocol/src输入后重跑，无产品或断言修改。

本阶段仍不部署游戏服务；生产修复的portable打包与class/JAR核验由阶段收尾记录，不以编译或上述注册成功代替D-02实机。

## 1B：E-01d1n3 Chapter 与资源前置

[章节专题](CLIENT_72_D02_CHAPTER_LOAD.md)固定目标commit/tree/blob，两个独立JVM真实loader逐行字段/末次写索引核对通过：436安装，339/326键，零键98/111行，30302均无命中、0 ERROR、loadedAll=false。报告SHA `47524bc3879665f68350650dcc7c94a4ab604fc3a11d4963919a3a7176814a62`。成熟n2夹具新增显式目标表模式，独立Mongo实际2项全过；两条启动构造2/64包、没有ChapterState增量，BSON/新Player冷读和真实manager未就绪拒绝保持。

沿loadAll调用顺序建立152资源注解/161文件引用清单，候选tree156有blob、5不在tree，现有24个引用逐blob一致；不猜未知键、不混称全部P0。清单不覆盖完整动态引用或运行revision。目标QuestExcel拒绝、未知字段及必要资源完整性仍阻塞自然就绪，2B正向重绑定没有执行，不伪造loadedAll。

## 1C：F-03d3g2-prep 有限 Java 来源投影

[投影专题](CLIENT_72_AVATAR_JAVA_PROJECTION.md)以真实Avatar/TeamInfo/TeamManager/GameItem getter生成独立raw适配器测试输出，明确只读输入；不调用旧Avatar.toProto。修改后的Java1项通过，覆盖非零/高位GUID/map/list/零值存在性、正逆序属性插入确定性及同一物品实际Store GUID关联。两次全新原生回放逐字节一致，SHA `0d608af88c56b20be36c73deb34fc36075811105fd095a5d0f1f64ee5c418c14`；只验f1身份/属性/装备与f3队伍片段。

首次Team枚举接口复用和第二次ASCII字符串模型错误保留；按已验独立runtime与Team UTF-8 reader正确组合后通过，未新建ABI/未知调用模型。Store ID0与原生row ID10001不作身份一致性断言，仅GUID关联。f4a/b/c、s2a的来源和省略边界另列规格表；完整Avatar构造、15183f290等后缀、复杂SceneInfo仍阻塞正式g2，不以准备段接生产。

## 3：构建、现场与停止检查

真实反射152类/注解文件/优先级与实际GameData map已补核，最终章节双JVM报告同SHA `3b5d7799…f6f25a`；保留Map.of键序第一次字节不一致，有序输出后通过，不增加生产范围。Chapter/通知、有限投影、网络与索引的可执行出口均已完成；资源自然就绪/正向重绑定、完整构造/场景、自然SDK/HTTP/实机和正式g2均仍依赖准确的深度或实机证据，不继续猜测。

生产提交8c1fb85的portable构建27.5秒成功；JAR SHA-256 `f475dbf6d91bac591a9b4346726b935171764b446b9ab64e6a8734b44c8cade8`，8变化class均匹配编译、46296保持、无增删。未部署，status退出1表示stopped/Healthy=false，没有运行PID/JAR；原Mongo保留，自有测试进程0、8088/22101/27027无监听，五客户端输入及配置/原数据/resources差异核同。仅后续离线工具/测试/文档变更，不重复打包。

本地提交：证据c34808f、网络8c1fb85、章节0c22864、投影05de04b、反射777da12；最后文档提交以git log为准，未推送。Project/Harness及各专题、README、迁移/部署和账号保存三文档已同步。HANDOFF2先备份核验再覆盖、HANDOFF仅追加并核原字节前缀；具体队列、输入、最小复现、尝试/排除、解除条件、被阻塞后继和下一步见[HANDOFF2](../HANDOFF2.md)。**本轮无需值守且适合Sol/Terra的工作已收口，D-02仍未完成。**
