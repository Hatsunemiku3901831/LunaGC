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

## 七项设置的确认保存

`HandlerMusicGameBookSaveSettingsReq` 已接入正式接包分发，响应保留请求序号。
`MusicGameBookSettingsData` 将三项 uint32 的完整位模式与四项 bool 保存到 Player 文档；
登录及打开千音雅集时，在已有 AllDataNotify 的字段 10 同步。

- 保存确认后返回成功及七项设置；已确认的相同请求复用当前值，不增加存档版本。
- 缺少嵌套设置返回失败；显式空设置表示将七项值设为零／false，可以覆盖旧值。
- 新玩家与缺少该字段的旧档保留设置缺省状态；读取和登录不补写存档。
- 写入前失败及提交后确认丢失保留原候选，先核对／确认该次保存，再接受其他设置。
- 保存尚未确认时，快照、主题变更及演奏开始／结算沿用相同确认入口。
- 先前未确认的主题与演奏奖励也在设置变更前完成核对，首次奖励保持一次发放。
- 只持久化已配对的七个字段，未知字段继续由协议保留，但不成为新存档字段。

设置消息保存原生值，其完整业务含义和资源值域继续配对。
当前没有据此宣称主题选择、模组选择或全部设置页面已经可用。

新增业务检查首轮 15 项中 14 项失败，旧档缺省对照通过。
首版冷读夹具缺少 TeamManager 导致 11 项检查失败，随后补齐夹具；这项调整没有修改产品代码。
之后四组 156 项通过，再增加旧档与演奏中未确认写入的检查。
最终设置专项 **21 项通过**；完整关联回归 **30 个套件、805 项通过，零失败、错误与跳过**，
Gradle `test`、`jar` 成功。

检查包括真实隔离 Mongo、写入前／确认丢失／响应丢失、并发序号、完整替换、
旧档读取、未知字段、主题与首次演奏奖励共存及中断后重试。
10 个源码／协议／测试与构建输入的宿主机／容器哈希一致，JAR 与编译项目类逐字节一致。
新增的 12 个类限于两个新消息及其生成类、接包入口和设置模型；
变化的 7 个已有类限于 Player、MusicGameBook、PacketOpcodes 及内部类，没有项目类被删除。

产物：`debug/aster-mid-20261011/batch322/music-book-settings.jar`。
SHA-256：`543758f1712ec6f3afa6375a8cdb22bf987a7c04cf2c6e3723357e32d06a4bd4`。
失败、各轮 XML 与 `verification.json` 同在 `batch322/`。
设置界面的当前客户端验收仍待完成。
