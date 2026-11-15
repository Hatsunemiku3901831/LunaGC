# Material 二进制行语法增量检索计划

任务 `R3-MATERIAL-HOST-ROW-SYNTAX-20261116`。Sol max负责本计划与本地source合同；Terra max按以下增量检索公开仓库。目标是获得**表头或首行前缀**的最小纯host合同，暂不实现全表walker。源码只读，原始响应与候选置忽略研究目录；仓库外clone，公开摘要用相对路径与固定hash，提交由指定集成者完成。

| 已验输入 | 固定身份及可复用范围 |
| --- | --- |
| Material真实payload | `debug/d02-material-exact-locator-20261116/entry-extract/Material.native-m_bytes.bin`，1560105B，SHA-256 `088f2b43c860c2892c1f6e9a0349547dcd94b021e5d56983349f2cfd81e7ee89`；提取result SHA-256 `812fe541fc17534f641510bd4644efbee7e060bd7f63c9943359a7f0334cbb0e`。仅条目身份，不含row/keyspan/cursor或自然采用。 |
| Material parser | `23400.m180606`，完整7128B `[14a88ca00,14a88e5d8)`，body SHA-256 `4e80512e145a4b7b56330536ad592d2d16fc4c64e0902aad6eedd22997148289`；[已验来源](CLIENT_72_D02_ACTIVE_ITEM_PARSER_SOURCES_20261116.md)。 |
| 共同父parser | `88968.m735168`，2018B `[150b6a9b0,150b6b192)`，body SHA-256 `64d41cef20efe8497be861797e4a9d224e96fc0cee2f71259145fd60b1e3485e`；[id/itemType条件来源](CLIENT_72_D02_ACTIVE_ITEM_BASE_RAW_INVENTORY_20261116.md)。 |
| 分类 | `23400.field110906/+c8 → enum34165`职责与原parser位式已验；94个literal本轮静态已取值，result SHA-256 `206351858a5ae43c5c222762f0f1a721c01fb8990999d131a85c8a3fe7b7da29`。不重查literal，不凭数值同形定位row。 |

仍缺Material自身table framing、count变换/单位、首row入口；其他表的4B count公式不能套用。派生parser先调用父parser，父F bit4可走String helper `50379.m418862/14f0de820`；其准确cursor增量尚无已验完整源语义，旧host模型 `cursor += N` 不作为证据。数组count、元素宽度与String字节数逐项从目标源闭合；不把托管数组布局当文件单位。

已查范围复用[UP-72账本](CLIENT_72_UPSTREAM_LEDGER_20261114.md)、[67分支](CLIENT_72_UPSTREAM_BRANCHES_20261114.md)、[固定文件](CLIENT_72_UPSTREAM_SOURCES_20261114.md)和[本轮公开报告](CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md)：`Genshin ExcelBin parser 7.2`、`MaterialExcelConfigData materialType 7.2`已各两排序一页0命中；宽 `MATERIAL_ Genshin resource parser`仅父查询第一页，未穷尽，不能当无来源。Go `excel-hk4e`只有具名JSON模型；非本机profile的 `genshin-il2cpp-re`不能提供本机边界。固定 `mavuika-rs@67833c157cdfb57eb0854e9b2781d5d89014204b`（5.2.51）已看README、唯一master与reader/derive方法，`from_binary.rs` SHA-256 `1eeec4ee59f99610ed88a912887b77aa0c72b2ed0a125086668a809d40215888`、derive `6e168e8a0a2faed3e601b64d6acb81a5a6ea9dc4728f8fce329d619b77c52612`。只复用语法设计参考，Default不证明目标缺flag值；不重抓这些原件。

本轮仅补以下精确分片，先仓库描述/README，再对明确相关者查全部公开分支与必要文件；按仓库+commit/tree/blob去重：

| 顺序 | 新查询或候选增量 | 预期复用 |
| --- | --- | --- |
| 1 | `MaterialExcelConfigData binary parser`、`Genshin ExcelBin binary reader`、`MiHoYoBinData from_binary` | host语法、table count与row cursor实现；不重复上述7.2旧词。 |
| 2 | 固定mavuika方法的Material/table-header相关文件；其公开Fork、PR/Issue只查binary/reader/Material改动及新增版本分支，不重复master方法 | 存在位、数组计数/宽度、字符串长度单位的可移植结构；保留5.2身份。 |
| 3 | 第1步README明确声称解析二进制的候选；补 `Genshin ExcelBin row parser`、`MaterialExcelConfigData deserialize` 精确入口 | 完整row边界证明或可改造成只走前缀的walker，不直接运行上游工具。 |
| 4 | 仅前述候选有新线索时查GitLab/Gitee/Forgejo对应仓库、分支、PR/Issue；搜索词追加 `Material binary reader` | 获取未在GitHub固定的同一方法增量。已有restricted地址不换入口重试。 |

每个新查询记录时间、排序、页码、total/incomplete与原始响应；只完成本表有限分片。宽结果超过1000时先加binary/reader/Material准确条件，不扩UP-72长尾。无README/分支独有增量、只有JSON或旧数据时记录实际排除并停止该候选；源码许可证和目标profile分别记录，不能以README的7.2标签代本机身份。

本地并行入口是既存 `inventory-contract.json`（SHA-256 `626cce90ce4f67a75969aa32d2ff3c96f1dcdb8897fe065ba6bc618f1a9d57a3`）及 `inventory-contract-observations.json`（`130f6387005ad3b5cf0e2dc2e7c73b930e698fa07f03a7fe8b48fefe87dc16db`）的 Material path-loader `14d6e1570` 身份/调用源，定位真实whole/table方法；该loader在固定metadata为 `60257.m500132`，70971是path literal index而非方法slot。不重跑fixture。最小出口依次选：准确header/count及首row起点，或该首row在String absent条件下可定位的父id/itemType前缀。不能预设首row的flag；缺String语义时只停止依赖该helper的路径。新body获取须先交固定PE/hash、唯一方法slot、完整pdata边界、目标问题和预算，由主线审定。

验收要求：可复现固定source→准确token/span→逻辑值或位置的链，每段记录presence、计数单位、完整物理边界和未知；候选值只作对照。达到一条最小合同立即交实现，不等全表；未找到增量则交准确缺源，不能把“未搜”标外阻塞。不执行A/B/preview/native/ctor/getter/TLS，不借其冻结产物构造依赖；遇hash/边界冲突、新安全拒绝或准确冻结依赖即停该动作并保留原证据，不换工具。payload可用、source通过均不等资源ready或D-02客户端验收。
