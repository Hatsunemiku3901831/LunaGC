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
