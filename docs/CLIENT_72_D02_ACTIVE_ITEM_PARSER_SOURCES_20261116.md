# Material 与 Reliquary parser 限定来源验收

`R3-ACTIVE-MATERIAL-RESOURCE-PARSER-SOURCE`、其 `-RUN2` 和 `R3-ACTIVE-RELIQUARY-RESOURCE-PARSER-SOURCE` 已完成限定静态 source 验收。只闭合准确原始来源，不证明字段语义、值、row、初始化、Java 恢复或 ready；准确冻结直接引用为零仅覆盖本窗直接索引，不证明传递路径 A-free。

## Reliquary

固定 `46431.m385148 / 1529b1930`，参数 `327791` 的 type reference `86325 → class 42005`。fresh AOT、完整 exception、unique containing / exact begin / overlap 门通过，完整区间 `[1529b1930,1529b2643)` 3347B，一次读取 body SHA-256 `05b247be7560e4b5fc05679018fc94d9bae9bb5aba75441e9ab7f150b820a145`；strict 连续重拼 833 条指令，217 引用（74 CALL / 125 branch / 18 RIP），indirect unknown 0、准确冻结直接命中 0。

证据 3375B = AOT 16 + pdata 12 + body 3347；header 两读 4616B / 4096 distinct，exception 一读 14344080B，五 stock hash 550661784B（PE 450927000B），metadata 与预检另账。scope 中两个历史 `entryResultPins` 未作为本次输入且未单独重核 hash；主线判断二者仅为 ctor 1B 上下文，该静态 parser source 不依赖 C3 行为，省略不影响本次准确来源门禁证明；不据此推 ctor/初始化，也不能声称所有历史 pins 均重 hash。父 parser 的 body 未由本窗验收。

私有根 `local/migration72/d02-resume-20261116/active-item-reliquary-parser-window/`：`capture/result.private.json` SHA-256 `76cdf2b241f5f61e31c61dc465c48c18084ba69d28ad0491b5a3f640f3ad9e1d`，`manifest.private.json` SHA-256 `0b0ee6faeba55427fd4b53d41ddcc0309257ca4b8d74c35bccbe6c54e6a06b40`。后继[最小语义及父来源](CLIENT_72_D02_RESOURCE_SCALAR_AND_BASE_SOURCES_20261116.md)已限定验收；该原窗不因后继而证明初始化或实际值。

## Material

固定 `23400.m180606 / 14a88ca00`，参数 `155009` 的 type reference `86325 → class 42005`。RUN2 的 fresh AOT、完整 exception hash、唯一 pdata raw 12B 与原完整 row 区间 `[14a88ca00,14a88e5d8)` 一致；一次读取 7128B body SHA-256 `4e80512e145a4b7b56330536ad592d2d16fc4c64e0902aad6eedd22997148289`。strict 1754 条指令、447 引用（131 CALL / 271 branch / 45 RIP），indirect unknown 0、准确冻结直接命中 0。

证据 7156B = AOT 16 + pdata 12 + body 7128，pdata 从已读目录复制，不重复计物理读；header 两读 4616B / 4096 distinct，exception 一读 14344080B，五 stock hash 550661784B；PE hash 加本次计量来源读为 465282840B。hash IO、parse IO 实调用与预检另账，`capture/io-addendum.private.json` 记录应用层补充，底层 loader / OS 调用未计量，不称全部 IO 已覆盖。原 4096B cap 首轮失败与更早 schema KeyError 保留，不追认旧轮；完整边界现已取得。

私有根 `local/migration72/d02-resume-20261116/active-item-material-parser-window-run2/`：`capture/result.private.json` SHA-256 `fb45c38944e99756b492bf4daaaa2b4d9ec75e0fbb0672a93297abd3506f2cb2`；`manifest.private.json` SHA-256 `a091503fd0e67580c83ad7106458884404f22ed90deba36f2fcde1065eb07656`；`capture/body.asm.private.json` SHA-256 `90e3002d9a86c40311a7571ea61733f0c7e1f190e86404d9344c0cd5131841d4`；`capture/reference-index.private.json` SHA-256 `bfc29fa85255c9579b71af6227e423a5ef1d71b73c0a171c6e3866d2d2c92a50`。

两项 source 采集未执行目标、native、ctor/parser/getter 或 host row，未运行 JVM、DB、客户端或测试，也未修改 tracked 代码。Skill 最小语义及另一个 `14f905250` Float setter 静态来源已限定验收，见[来源](CLIENT_72_D02_RESOURCE_SCALAR_AND_BASE_SOURCES_20261116.md)；后继[条件合同与raw flag盘点](CLIENT_72_D02_SCALAR_CONTRACTS_AND_SKILL_FLAGS_20261116.md)亦已限定验收，不证明自然输入或新行值。A/B、preview 和准确冻结边保持，D-02 / F-05 / D02-R6 仍 0/6。
