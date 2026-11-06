# AstaPS 7.2 固定来源协议审计

本项只交付可重复的离线来源审计。没有复制 AstaPS 运行实现或 Proto 到生产目录，没有修改三层映射状态，没有启动服务、操作客户端或推进 D-02。四类生产 schema 的融合由独立批次验收；本项不能替代其构建、wire 回归或客户端验收。

## 固定来源与复现

参考项目 AstaPS 固定提交 `a49f51aa6f2787ffbc25cee46801b70dee860d23`，Git 根树 `9940bf7756ce5ff8be8a836a4485a645b490c2e1`。参考 checkout 必须干净，工具拒绝不同提交、根树、未提交/未跟踪差异，以及 Proto 源树或本地映射摘要漂移。参考源码、原始清单和完整报告留在本地忽略目录；共享摘要不含机器路径、客户端二进制、凭据或抓包。

- 工具：[audit_astaps72.py](../tools/audit_astaps72.py)
- 回归：[test_audit_astaps72.py](../tools/test_audit_astaps72.py)
- 脱敏摘要：[astaps-audit-observations.json](../protocol/cn-7.2/astaps-audit-observations.json)
- 目标依据：[mappings.json](../protocol/cn-7.2/mappings.json)，固定 UTF-8/LF SHA-256 `e72a9161cc7420cde7f0ef59bcb82a23103172ca51d20c3213e952f90e918048`；摘要另保存 `extract_client72.HASHES` 的三个目标输入哈希。

先将 `ASTAPS_REFERENCE`、`PROTOC72` 指向已核对的本地参考 checkout 和 protoc 可执行文件，不套用历史机器路径。只需 Python 标准库、Git 和 protoc：

```powershell
python -m unittest tools.test_audit_astaps72 -v
python tools/audit_astaps72.py `
  --reference $env:ASTAPS_REFERENCE --protoc $env:PROTOC72 `
  --output local/migration72/evidence/astaps-audit.json `
  --summary-output local/migration72/evidence/astaps-audit-observations.json
```

完整报告保存所有 Proto 文件的相对路径/LF SHA、消息/枚举/字段、零号/负号/旧号以及 parked 明细。正式摘要只保存全量计数、Proto 清单树 digest、14个关键文件 hash、214字段对照和独立编码/读取证明。Proto 清单树 digest 是按相对文件名排序的 `{file,sha256}` 行经 canonical JSON（键排序、无空格、UTF-8）后求 SHA-256，固定为 `86d9e71e600ca29b286c53a17bffb94657fce7e797d837c1f0108b33cd91be18`，不是 Git tree SHA。

工具用 protoc 实际编译全树，标准库索引器读取该固定导出器的逐行格式，拒绝无法索引的声明、未解析类型和重复字段。它不是通用 Proto 编译器。一次性原审计另用 protobuf descriptor 实现独立读取 protoc 产物，与正式索引逐项交叉核对全部8,066字段的 tag/kind/cardinality/packed/type引用/oneof/map形状完全相等；该依赖只在本地交叉核对使用，正式工具和单测不依赖它。每次正式运行仍实际执行全树编译和 parked 编解码。摘要不使用 descriptor 二进制 hash，避免把工具版本差异冒充源树变化；实际 protoc 版本和警告数单列。

## 实际结果与口径

2026-11-06 使用 protoc 3.18.1，全部 2,105 个源文件编译通过。全量结果：

| 对象 | 数量 |
| --- | ---: |
| 非 map-entry 消息 / 含合成 map-entry 消息 | 2,033 / 2,154 |
| 枚举 / 非 map-entry 字段 | 147 / 8,066 |
| 20000+ 字段 / 所属消息 | 967 / 331 |
| opcode 常量 | 1,895 |
| 正号 / 零号 / 负占位 | 1,316 / 567 / 12 |
| 正号重复值组 | 13 |
| 上游文档列明无 7.2 counterpart 的消息 / 实际字段 | 105 / 218 |

上游 gaps 文档的 92 零号、290 parked 采用“server code uses”口径，不能代替全量盘点。零号与负占位在上游 `GameServerPacketHandler` 注册和 `GameSession.send` 入口跳过；这并不补足其真实目标包号。全量重复正号含别名，不自动认定分派冲突或按数字合并类型。旧号注释保存在完整报告的 `opcodes[].legacy71`，不会用作目标 CmdID 回填。

三层对照只采用本仓库已验证身份，11类含不带 CmdID 的 PacketHead，10个可比 CmdID 全部与 Asta 一致。214个目标外层字段中，130个同 tag 且外层 scalar/cardinality/packed 一致，84个没有对应目标 tag；现有同 tag 外层形状冲突为零。枚举存储与 int32 仅按外层存储对照；map 还比较 key/value 种类。嵌套消息的递归兼容、枚举业务名和未知字段语义不因此晋级。

| 消息 | 目标 CmdID | 目标字段 / 同外层形状 / 缺目标 tag |
| --- | ---: | --- |
| GetPlayerTokenReq | 24604 | 28 / 24 / 4 |
| GetPlayerTokenRsp | 27808 | 41 / 33 / 8 |
| PlayerLoginReq | 23172 | 55 / 0 / 55 |
| PlayerLoginRsp | 20835 | 44 / 33 / 11 |
| PingReq | 26605 | 8 / 6 / 2 |
| PingRsp | 25763 | 3 / 3 / 0 |
| DoSetPlayerBornDataNotify | 5008 | 0 / 0 / 0 |
| SetPlayerBornDataReq | 8541 | 2 / 2 / 0 |
| SetPlayerBornDataRsp | 414 | 1 / 1 / 0 |
| PlayerEnterSceneNotify | 27748 | 26 / 22 / 4 |
| PacketHead | — | 6 / 6 / 0 |

还有两处确定的**已验证语义冲突**：PlayerLoginRsp 的 tag437，本地为 `client_md5`，Asta 为 `client_silence_md5`；tag1255 本地为 `client_silence_md5`，Asta 为 `client_md5`。即使外层 wire 形状相同，也不能继承上游业务名。

摘要 `targets[].fields` 为完整214字段的可实施对照：`target` 保留本地原始字段、tag/type/cardinality/packed/status；`proposedFieldName` 仅在本地 status=verified 时使用 semanticField，否则保留 clientField；`astaAtSameTag` 给参考字段及相对文件/行号，`sameOuterShape` 和 `semanticConflict` 分开记录。原 mappings 不被修改。

## 映射脚本边界

已审阅三个固定脚本和生成/gaps文档，hash随摘要保存：

1. `remap_71_to_72.py` 由服务端7.1 descriptor，经 kitkat 7.1 CmdID/名称/旧父字段号，再经 NT 7.2 名称/可读父字段传播。字段按归一化名称改 tag；stable消息还保留部分旧混淆字段号。类型和容器沿用旧 descriptor，映射过程本身没有验证目标 field shape。
2. `recover_lost_72.py` 用至少两个消息的未匹配字段共现和种类兼容推断，文档声明恢复183字段。其 kind 不包含 repeated，`enum-or-msg` 允许枚举或消息，不是独立 wire 证明。更弱的 `--single` 上游没有应用，不能整体接收其候选。
3. `export_proto_72.py` 保留服务端名称及递归引用；可选 NT additions 会转换枚举、必要时补零，解析不了依赖的字段会丢弃，重跑会覆盖手工修订。

旧 descriptor、kitkat 和 NT 原始输入没有随该参考提交提供，本项没有宣称复跑或独立证明整条旧映射链。参考提交的实际交付 Proto 和关键来源已固定，足以做差异审计，不能直接成为本机国服客户端的完整协议证明。

## Parked 字段仍然读写

上游文档称20000+字段不读不写，实际生成类和构造器不支持这个结论。上游 `GetPlayerTokenRspOuterClass.java:1513/1525/1528` 写出 reg_platform=20002、is_proficient_player=20006、account_type=20007；同文件 `2646/2666/2671` 的 reader case 读取它们，`PacketGetPlayerTokenRsp.java:118..125` 还实际设置非零值。

工具固定合成输入 reg_platform=3、is_proficient_player=true、account_type=1，独立期望 wire 为 `90e20903b0e20901b8e20901`。实际 protoc 编码逐字节相等，再将这份独立字节交给 protoc 解码，三个非零字段全部恢复。单测分别拒绝编码为空或解码值不符；真实编解码结果随摘要保存。20000+只能作为迁移标记，不能作为发送防线；本项不添加运行防线或扩展安全逻辑。

## 可融合项与保留缺口

首批限定 TokenReq/PingReq/PingRsp/PacketHead，可直接使用本地固定目标字段及已verified业务名；其他字段保持原名：

- TokenReq缺 tag1/string/CANLDLBEODF、tag5/string/IDBJELLONNH；已verified的 sub_channel_id、account_type 应分别为 tag10、tag15。不能猜 Asta 两个 parked string 与目标1/5的对应。
- PingReq 缺 bytes/tag3/IMEDCDCPICN 和 bytes/tag9/BAHKGFLONLL；其余6字段、PingRsp的3字段、Head的6字段外层形状一致。Ping字段语义仍candidate，旧getter/setter调用的替换只能称现有值回显，不能晋级语义。
- Asta没有PlayerLoginReq Proto，Handler直接不解析payload；本地55字段及嵌套闭包应作为下一独立批依据。DoSetPlayerBornDataNotify也没有Asta Proto，但空包发送与本地已验证空schema一致。
- PlayerEnterSceneNotify的46/395/763/1357四tag仍缺；22同形状字段不证明旧业务名，嵌套与场景成功序列仍待独立验收。

其余未知语义、967个parked归属、105个旧schema、手写旧wire、其他P0消息、SDK/密钥/资源/实际登录入场仍保持原缺口。本批维护文档和限定融合完成后按用户要求暂停，不推进新实机阶段。

## 许可证、验收与交付

参考根 `LICENSE` 实际为 **GNU GPLv3**，LF SHA-256为 `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986`；build.gradle的其他描述不能替代实际许可证。`LICENSE-ClassGraph.txt` 是ClassGraph依赖的MIT通知，不能用于覆盖项目许可证。README署名Grasscutter并列LunaGC/HunkyMeow参考，协议生成文档注明kitkat/NT来源；后者原始输入的授权证明未随提交提供。本项记录来源和差异，没有将Asta实现或Proto复制进生产目录；保留项目/提交/文件粒度归属。

实际验证：11项合成/摘要测试通过；固定参考全树protoc编译与parked非零独立编解码通过；正式完整报告/脱敏摘要重复输出逐字节相同（本机复核证据在 `local/migration72/evidence/astaps-audit-determinism.json`）。无本地Git提交；由root复核四个新增文件后统一集成提交。仅离线工具、测试和文档，无重建重启要求；生产融合的运行验收边界由对应批次单列。
