# 7.2 三层映射输入

`profile.json` 固定目标版本/渠道与关键输入哈希；`mappings.json` 分别维护 opcodes、messages、schemas 三张表。26个登录/选人/入场种子中，Token/Login各请求响应及PingReq共5个业务名为candidate；PacketHead类型及2个业务字段静态确认，其他业务未知保留未知。**空字段列表不代表实际空消息，当前没有完整正式协议。** P0完整闭包未确认，closure保持unresolved。

每条记录独立保留 status（unresolved/candidate/verified）、evidence、ambiguities。消息表另有方向、别名、runtimeStatus；字段表记录 semanticField/tag/type/cardinality/presence/packed/oneof、nestedType、enumValues或map键值。已验证字段必须写明presence和packed；枚举/嵌套/容器不能只保留一个wire type。

新增证据需给唯一ID、层次opcode/message/field/closure、profile输入键、相同SHA-256和相对证据位置/VA。不同层不能互借证据，静态候选不自动升为verified；runtimeStatus由实机结果维护。字段/消息拆分合并、删除或新增应先在ambiguities/证据说明中记录，确认后修订规范名，不能猜测旧字段的对应关系。

```powershell
python -m unittest tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

第一条为合成数据结构测试；普通校验只验证研究记录自洽，当前应通过。`--ready`要求所有层及P0闭包已核实，当前**必须失败**。它检查范围、别名/唯一包号、字段引用/容器和证据归属，不替代人工判断证据真实性，也不是Java/protoc生成器或客户端验收。

`wire-observations.json`和`research.proto`由extract_client72_wire.py从两个已审查的目标静态函数生成，共14个字段；原始字段名字避免伪造业务含义。校验器同时比对原始command/wire表，候选也不能与目标号/tag/类型冲突。`check_client72_wire_proto.py --protoc ...`编译离线Proto并比较手写独立wire断言；合成数据不是客户端抓包或登录验收。研究Proto不进入服务端Gradle源集，完整业务生成器仍待开发。

`commands.json` 为固定哈希上恢复的4,012条静态CmdID→混淆类型绑定，已有两个发送入口和封/解包证据；4,007个不同值，5组重号保留歧义。它与业务映射分离，不能直接替换PacketOpcodes；业务名、网络域、运行时IFix覆盖仍需核实。可通过 `extract_client72_commands.py` 确定性再生成。证据见 [网络研究](../../docs/CLIENT_72_NETWORK.md)。未知处没有7.1正包号回退。接手见 [提取记录](../../docs/CLIENT_72_EXTRACTION.md) 和 [Project](../../Project.md)。
