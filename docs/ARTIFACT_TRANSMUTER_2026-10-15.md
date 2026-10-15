# 圣言自明机适配

## 当前协议

对照 AstaPS 固定提交 `7cc148d1f752a43d4b9146b3b73f26875902546f`，
使用既有提取器校验当前客户端三个输入文件的 SHA-256，再核对包号 getter、请求写入器、
响应读取器和实际状态接收函数。

| 消息 | 包号 | 字段与编号 |
| --- | --- | --- |
| ReliquaryOfferExtractReq | 1781 | schedule_id 1、reliquary_guid_list 5、item_list 8 |
| ReliquaryOfferExtractRsp | 24 | item_list 3、retcode 5、reliquary_guid_list 6、schedule_id 12 |
| ReliquaryOfferDefineReq | 22095 | main_prop_type 1、append_prop_type_list 3、equip_type 8、purchase_id 9、schedule_id 10 |
| ReliquaryOfferDefineRsp | 3259 | purchase_id 1、schedule_id 3、retcode 11 |
| ReliquaryOfferDataNotify | 24434 | schedule_id 5、progress 7、cycle_end_time 8、fractional_progress 10、extracted_count 11、define_count_list 13 |

萃取物品列表复用已经核实的 `ItemParam`，字段 1 为物品 ID、字段 2 为数量；
GUID 和属性列表保留 packed 编码。
定义次数元素的键位于字段 12、次数位于字段 13，键暂命名为 `define_id`，不猜测其资源语义。

原生依据包括：

- 萃取请求类型 70879，写入器 `0x1526784a0`；响应类型 39158，读取器 `0x148d95110`。
- 定向请求类型 75016，写入器 `0x15177ad50`；响应类型 42571，读取器 `0x1521764f0`。
- 状态通知类型 70916，读取器 `0x14bd64740`；实际状态接收函数 `0x1482a7800`。
- 次数元素类型 86859，读取器 `0x147b02520`；物品参数类型 62819，读取器 `0x1478c4f30`。

状态接收函数将通知的整数进度与浮点小数相加；不能只保存整数，否则资源中 0.5 点材料的进度会丢失。
它将字段 5 写入萃取请求读取的排期变量，并将字段 11 写入已萃取次数。
参考源码将这几项及次数元素的键／值顺序写反，本地使用上述原生位置。

`ReliquaryOfferCompanionNotify` 的包号 228 也已有可读别名；其嵌套数据含义尚未完整配对，
本次未增加猜测的协议结构或发送空成功消息。
原有不透明包号别名继续保留，避免影响其他调用。

radare2 反汇编连接报告 Transport closed，本次使用既有只读 Capstone 提取器。
原始证据在 `debug/aster-mid-20261011/batch245/` 的 `artifact-native.json`、
`artifact-message-signatures.json`、`artifact-state-consumer.log` 和对应反汇编。

## 验证和剩余工作

新增 6 项协议测试，首轮全部失败；补齐后连同客户端调时协议相关 **12 项／2 套件**通过，
零失败、错误和跳过，JAR 构建通过。字节样本检查排期、材料、GUID、主／副属性、
错误码、小数进度及定义次数的独立字段，并核对 6 个包号别名。
宿主机／容器源码 SHA-256、编译类／JAR 字节一致，无意外类删除。

证据：`artifact-wire-red-results/`、`artifact-wire-final-results/` 和 `artifact-wire-verification.json`。
JAR SHA-256：`d14713f368596a733fafb7183e81050f35b2a6875c94a9a06a99362ae9e21b11`。

协议结构已补齐。实际入口、萃取消耗和发奖、定向制作、周期保存及定向属性强化仍需继续接入；
不把本次协议检查当作完整玩法或客户端画面验收。日常运行服务未替换。
