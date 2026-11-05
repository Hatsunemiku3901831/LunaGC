# 服务端协议表面与 P0 依赖起点

2026-11-05，`tools/inventory_protocol.py` 对当前主仓库源码重新执行。此处数值为**遗留服务端源码盘点**，不是 7.2 CmdID。离线工具不启动服务器、不读数据库、不生成正式协议。

## 可复现结果

| 项目 | 本批结果 |
| --- | --- |
| PacketOpcodes 常量 | 3,273：正值2,845、负值427、零值1 |
| 不同正值 / 重复正值组 | 2,564 / 279；重复组只是别名候选，尚无规范名批准 |
| PacketHandler 子类注解 | 323；正值且未 disabled 的227 |
| 正值 Handler 冲突 | 静态继承链范围内0组；不包含插件、运行期注册 |
| 发送/覆盖调用位置 | 627，含17处不能在局部直接求值的表达式 |
| Proto 源 | 2,803 |
| P0 静态起点 / import 闭包 | 114个名称候选 / 471个Proto文件；不是完整调用闭包 |

报告：`local/migration72/evidence/server-inventory.json`。包含每条常量、注解、发送表达式、配置候选和引用的相对路径/行号、Java/Proto SHA-256。排除生成的 BuildConfig.java（其构建时间变化不属于协议源）。同一源码连续两次输出逐字节一致，测试还覆盖注释/字符串误报、别名表达式、负占位、动态调用、循环 import 和缺失引用。

一个实际误报已被锁定并修正：发送类 PacketQuestUpdateQuestVarRsp 带有 @Opcodes 注解，但不继承 PacketHandler；不能与请求 Handler 算作注册冲突。另有 setter 声明不能作为 setOpcode 调用。工具按继承链和成员调用形式区分，测试覆盖前者。

## 已复核的动态来源与接入限制

- `HandlerPlayerLoginReq`：配置 `newAccountIntro.doSetPlayerBornDataNotify`，且有默认旅行者超时兜底；7.2 新账号验收不能以兜底跳过选人。
- `HandlerSetPlayerBornDataReq`：`newAccountIntro.setPlayerBornDataRsp` 覆盖，当前命名常量为负占位；需真实7.2响应序列。
- `InvestigationHandbook`：private helper的3个调用分别传入调查全量/目标/章节通知常量。`PacketCrucibleMatch`、`PacketMpPlay`的private构造函数分别只有8/7个同类工厂调用，均传命名常量；外部活动控制器和Handler不能通过工厂参数指定opcode。
- `FanOut` / `data/proto-fanout.json`：外部候选目标及原包号重写；未验证的旧号不能成为新构建兜底。
- `PacketWindSeedClientNotify`、WindSeedUID/Windy 包装、`HandlerSceneInitFinishReq`：配置 cmdId、payloadField 与 sweep；调试试探不是正式映射证据。
- `DailyTaskProto` 的三条 *_CMD 是 PacketOpcodes 的间接引用，不能把局部求值未完成误称为目标未知三条新包号。
- `GameSession`、`DebugConstants`、`PacketOpcodesUtils`：发送设置、禁用/循环/调试列表、帧读取范围；`UnionCmdNotify.message_id` 和 Combat/Ability 枚举分派另行验收。

17个间接位置已逐个归入10组：3组private常量链、1组常量别名、4组运行配置、1组公开replay API、1组事件后的包复制。清单为 `protocol/cn-7.2/server-surface-review.json`，固定40份相关源码/数据的UTF-8规范换行SHA-256；记录的是遗留服务端来源，不将其中任何数字晋级为7.2包号。

`review_protocol72_surface`重新盘点并比对全部动态位置（文件/行/表达式/调用形式），检查常量绑定、源码摘要、配置候选和数字case；新位置、同数量表达式替换、来源改变都要求重新人工复核。server/net范围的37个数字case属于祈愿类型、商城类型、脚本参数索引和生日月份；没有直接数字参数发送。Combat/Ability使用生成枚举域，Ability的`typeVal > 100`只过滤日志；这些域仍需B-02独立映射。

本次明确了5类接入边界：

- `SendPacketEvent`在初始opcode检查后可以替换包，且BasePacket提供公开setOpcode；replayPacket主源码无调用者仍不能排除插件。C-01须验证最终包，而非只验证原包。
- `registerPacketHandler`当前用put覆盖同号；静态0冲突不能代替运行期冲突拒绝。`PacketOpcodesUtils`同值别名名称取决于反射覆盖顺序，B-03需明确规范名。
- UnionCmd将message_id/body递归交给相同handle，内外层wire、状态限制和递归边界分别验收；Combat/Ability/ForwardType不与CmdID混用。
- BasePacket将opcode和header长度截断写uint16，C-01需显式范围及帧边界断言；magic 0x4567/0x89ab不是命令号。
- FanOut外部候选/字段重写、Lua sweep、选人配置仍是开放输入。`cmdId()`本身在负配置时也会回退旧常量，禁用取决于调用者。不能沿用源码中“错误候选号无害”的假设。认证旁路保持关闭，运行config/env/插件在部署时另查。

A-02完成的是本次服务端源码表面盘点和人工动态来源复核，未声称插件运行期或P0调用图完整。A-03、B-02和C-01继续处理这些明确边界。15项测试（原盘点8项+复核7项）通过；两次报告SHA-256均为 `e2f6516faf292a1e7d8dfd0ceefefe505ccad0450bc9dc82fc27411d8c72b609`，`protocolReady`固定false。

## 登录和入场闭包起点

| 阶段 | 已定位源码入口 | 尚需追踪 |
| --- | --- | --- |
| SDK/dispatch | HTTP AuthenticationHandler、RegionHandler；QueryRegionList/QueryCurrRegion Proto | 目标渠道/版本/密钥与补丁实际HTTP链路 |
| Token/Login/Ping | 对应Handler与发送包；GameSession状态限制 | 目标三层映射、实际密钥切换与错误返回 |
| 新账号 | HandlerPlayerLoginReq、HandlerSetPlayerBornDataReq | 选人通知/响应、兜底分支禁用后的真实序列 |
| 初始化 | Player.onLogin、直接发送列表 | World.tryAddPlayer、Quest、Activity、Shop、Team、Home、成就/合成/锻造/树脂等间接通知和事件插件 |
| 入场 | EnterSceneReady、SceneInitFinish、EnterSceneDone、PostEnterScene | world/scene/team/entity 与各管理器、场景脚本延迟发送 |
| 容器 | UnionCmd；PacketHead | 子包CmdID/body、Combat/Ability枚举及其嵌套schema |

工具把整个 Player.java 纳入保守根文件，故包含非登录调用，仍会遗漏间接管理器发送。这既不是最小集，也不能证明完整集。3个“未找到同名Proto”候选已人工区分：CutsceneBeginNotify 对应大小写不同的 CutSceneBeginNotify.proto；TeamHexenzirkelChangeNotify 使用手工 CodedOutputStream；WindSeedUID 是其他发送包的包装。不能宣称缺了三个必须补造的 Proto。

A-03a现已完成[方法级登录/场景源码索引](CLIENT_72_LOGIN_SOURCE_GRAPH.md)：12源码/9根/80方法，13人工复核边及83候选边，1012未关联调用保留；61直接构造参数类型名和额外1工厂构造名都不代表必需消息集。下一步追踪管理器/事件/脚本并结合7.2受控连接观察收敛；目前没有实际客户端序列，A-03完整性不得标记通过。

```powershell
python -m unittest tools.test_inventory_protocol -v
python tools/inventory_protocol.py --output local/migration72/evidence/server-inventory.json
python -m unittest tools.test_inventory_protocol tools.test_review_protocol72_surface -v
python tools/review_protocol72_surface.py --output local/migration72/evidence/server-surface-review.json
```
