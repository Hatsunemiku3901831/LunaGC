# 千音雅集模组选择：2026-10-16

## 当前协议与选择状态

在已接入的千音雅集目录、演奏、首次奖励和设置保存基础上，继续补模组流程。
参考 AstaPS 的同名入口仍是空目录，以下依据固定 SHA-256 的本机 7.1 原生代码与资源。

| 本地描述性名称 | 原生类型 | 包号 | 字段 |
| --- | --- | --- | --- |
| MusicGameBookSaveModsReq | FAGBLBIMBLK，55845 | 5759 | 9：repeated uint32 JNJDOLEBBOO |
| MusicGameBookSaveModsRsp | JPGBFOCHHNA，39113 | 238 | 12：repeated uint32 JNJDOLEBBOO；14：int32 retcode |

- 设置页 `RequestModsUpdate`（`0x1485ad860`）将具名 `_selectedModList`（`+0x2e0`）
  传给模块 `0x14f7bfb60`；后者从 TypeInfo `0x1457b61c8` 创建请求。
- 请求 getter `0x14f771ac0` 返回 5759，静态初始化 `0x14f7718f0` 创建 tag `0x4a` 的 uint32 编解码器。
- 响应 getter `0x14b418d40` 返回 238，初始化 `0x14b418d10` 使用 tag `0x62`；
  reader `0x14b418d70` 同时接受 `0x60/0x62` 的未压缩／压缩列表，按 `0x70` 读取错误码。
- 响应入口 `0x14f7b6a30` 将列表替换到客户端 DTO 的 `+0x58`，设置页 `UpdateBeforeModList`
  从同一位置读取此前选择。AllDataNotify 的 `JNJDOLEBBOO` 字段 12 也写入这个位置。

因此，全量状态的这项数组对应已选模组；谱面记录中的同名数组仍需按自己的消费路径处理。
请求和响应的本地名称用于说明用途，不声称恢复了官方明文名称。
原有混淆别名 `EGHBJIMNHAD/AAJAOEJLENE` 保留。

另确认结算字段 `PGDGFMNNIII`（字段 7）来自 `MusicPlayPageContext._restartTimes`（`+0x28c`），
即重开次数；它不表示模组选择。

## 协议验证与后续

新增 11 项检查在缺少消息与别名的基线全部失败，补齐后通过。
连同现有千音消息、主题通知及设置消息，4 个套件、141 项通过，零失败、错误与跳过。
覆盖原生包号、字段数量、类型、压缩／未压缩列表、uint32 高位、清空选择、
有符号错误及未知字段保留。

radare2 MCP 本轮仍返回 `Transport closed`；只读分析使用重新验证输入的 Capstone／元数据工具。
原生提取、失败与通过 XML、`protocol-verification.json` 保存在
`debug/aster-mid-20261011/batch323/`。

本批先补齐消息定义；实际选择保存、组合约束、演奏资格及客户端验收继续接入。
