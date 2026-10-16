# 千音雅集设置保存：2026-10-16

## 当前协议

继续核对 AstaPS 的千音雅集空目录入口后，按本机固定 SHA-256 的 7.1 客户端补齐设置消息。
`SaveSettingsReq/Rsp` 是本地描述性命名，不声称恢复了官方明文消息名。

| 本地消息 | 当前原生类型 | 包号 | 字段 |
| --- | --- | --- | --- |
| MusicGameBookSaveSettingsReq | FMDCIALMNDH，14112 | 25611 | 14：MusicGameBookSettings |
| MusicGameBookSaveSettingsRsp | PPMAKFAIFKH，87204 | 7502 | 3：MusicGameBookSettings；14：int32 retcode |

发送工厂 `0x14f7bc4c0` 从 TypeInfo 单元 `0x1457b4a98` 创建请求，
从 `0x1457b4aa0` 创建现有七字段设置对象；写入请求的 `+0x18`。
请求 getter `0x14ebec370` 返回 25611，writer `0x14ebec3d0` 写 tag `0x72`。
响应 getter `0x14b9da880` 返回 7502，reader `0x14b9da9c0` 分别按 tag `0x1a/0x70`
读取设置及有符号错误码。客户端模块的响应入口为 `0x14f7b4160`。
保留原有混淆包号别名 `DMEAKHDFOIG/HLKEHCIJBJO`，没有为同一包号另猜一个值。

设置中的三项 uint32 和四项 bool 保留现有原生字段名。
不将这些字段未经配对地命名为主题、模组、奖励编号或客户端本地校准值。

## 本地校准值的区别

方法缓存单元 `0x1458a57c8` 的 kind-6 使用项指向 method spec 21881，
其 method inst 58447 是内联的 16 字节 GenericInst，而非指针数组。
唯一类型参数确认为 `MusicGameBookLocalData`，定义 48766。
该类型明确声明 `musicGameHasSetBeatMapDelay`、`musicGameHasSetDropSpeed`、
`musicGameBeatMapDelay` 和 `musicGameDropSpeed`。
这进一步确认结算中的对应布尔值属于本地设置埋点；不据此阻断标准成绩或首次奖励。

## 协议验证

新增 10 项检查先在缺少消息／别名的基线全部失败，再补齐协议。
验证原生字段编号与类型、嵌套消息存在性、uint32 高位、有符号错误码和未知字段保留。
连同现有千音协议和主题通知检查，3 个套件、130 项通过，零失败、错误与跳过。
首次测试源码的编译笔误日志单独保留，没有计作产品问题的红测试。

radare2 MCP 本轮返回 `Transport closed`，未建立会话；使用已有固定输入校验的只读
Capstone／元数据工具继续核对。原生提取、失败与通过 XML 保存在
`debug/aster-mid-20261011/batch321/`。

本节确认消息定义与编解码；业务保存、重登同步和客户端画面验收继续接入。
