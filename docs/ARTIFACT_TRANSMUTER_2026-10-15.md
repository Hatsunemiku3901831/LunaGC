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

## 实际萃取与保存

已经接入真实 `ReliquaryOfferExtractReq`、背包使用和拥有道具时的登录状态同步。
当前 `ConfigWidgetNew.json` 将 220109 配置为背包的 `RelicCraftPage`，没有快捷栏标签；
背包打开不会消耗道具，也不会替玩家自动补发未拥有的道具。

萃取规则读取 `ConstValueExcelConfigData.json` 的三个 `COMPOUND_SPE_RELIQUARY_PARAMS` 常量，
当前资源如下：

- 五星圣遗物至少为内部等级 5，即界面 +4；每批最多 25 件。
- 按资源中的 21 档曲线计算进度，+4 为 4 点、+20 为 50 点。
- 105002 每份 0.5 点、105003 每份 2 点；其他材料不参与萃取。
- 100 点发放 1 个 105005，每周期最多萃取 1 个。

进度以千分点整数保存，再拆成通知的整数／小数字段，避免半点进度丢失。
同批 GUID、材料行、数量、归属、锁定／装备状态和奖励容量全部核对后才扣除；
任一项失败不会先消耗其他有效材料。满背包会计入本次支付释放的材料格子；
超过本周期剩余进度容量时整批拒绝，不吞掉多余材料。成功响应回传实际接受的材料和 GUID，
响应序号与请求一致；畸形消息在经济操作前拒绝。

本地周期沿用参考服务器的滚动 3 天配置，**不是官服版本日历**。
可在 `server.game.gameOptions` 下设置：

```json
"artifactTransmuter": {
  "scheduleId": 700,
  "cycleDurationSeconds": 259200
}
```

排期必须为正整数，周期支持 1 秒至 366 天。修改周期长度影响下一个周期；
修改排期 ID 会建立新周期并清零已萃取次数；未完成的进度继续保留。
周期到期按服务器秒数判断，临界秒前不会提前重置。
规则在首次使用时读取，资源更新后应重启服务或调用规则重载入口。

玩家 Mongo 文档的 `artifactTransmuterData` 保存进度、周期和次数；
经济操作先保存 `pending`，再确认扣除、奖励及结果写入，最后清除记录。
记录包含操作 ID、源物品持久 ID、扣除前数量和消耗量、前后进度／次数及计划奖励。
无法确认的操作会保留记录并阻止自动重试，包括重新登录和周期过期后，
需要先核对实际库存和记录；本次不提供自动补偿或自动重放。

## 萃取流程验证

新增 18 项真实请求／库存／Mongo 检查，首轮全部失败。
接入后发现常量表有未命名行，读取器改为只选择所需命名常量；18 项随后全部通过。
追加边界检查后共 29 项，首轮两项失败：支付腾出的背包格子未计入容量，
以及嵌套 Cost 的 Morphia 类型标识与其他功能重名，均已修正并保留失败证据。

最终萃取、协议、小道具请求及小道具保存相关 **53 项／4 套件**通过，
零失败、错误和跳过；JAR 构建通过。覆盖部分进度读档续存、次数限制读档／登录同步、
周期边界、并发请求，以及预约、扣除和奖励确认中断后读取实际库存与阻止重试。
故障测试保留预期的注入异常日志。
宿主机／容器源码 SHA-256、编译类／JAR 字节一致，无意外类删除。

证据在同一 `batch245/` 的 `artifact-extract-red-results/`、`artifact-extract-first-results/`、
`artifact-extract-second-results/`、`artifact-extract-boundary-results/`、
`artifact-extract-final-results/` 和 `artifact-extract-verification.json`。
JAR SHA-256：`d1f474660e7ade47317e9b2146678e91781686d9ebb97946cc20821176ef02aa`。

定向制作、套装次数键的资源对应、定向属性强化及伴随通知仍需继续接入；
当前客户端页面和完整玩法尚未实机验收，日常运行服务未替换。
