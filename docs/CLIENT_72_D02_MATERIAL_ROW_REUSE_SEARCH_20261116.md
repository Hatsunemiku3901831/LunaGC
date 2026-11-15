# Material 行语法公开复用增量检索

任务 `R3-MATERIAL-ROW-REUSE-SEARCH-20261116`，服务于 `R3-MATERIAL-HOST-ROW-SYNTAX-20261116`。本次使用[中文检索计划](CLIENT_72_D02_MATERIAL_ROW_SEARCH_PLAN_20261116.md)最新版 SHA-256 `c70ea69d9119862e76217428a6611abeb56582a01f6c1cc33b22c65a96956c28`；原首版 SHA-256 `6cc7954e0c6c5d3c58e5efc9258ec7dde8bdf0ee697190111a07d57d35eaac8e`，修正内容是将 `70971` 标为 path literal index、将路径 loader 身份标为 `60257.m500132`，不改变本次检索范围。

**结论：找到了 5.2 的表加载/存在位方法和 2.2 的 Material 行 schema，均为旧版参考；没有发现可直接用于固定国服 7.2 Material count、首行入口或父 String cursor 的来源。** 旧版结构不能直接转成目标字段、偏移或默认值。该结论仅覆盖下述有限检索，不是外部源码不存在的证明。

固定目标前置为已提取的 Material `m_bytes` 1,560,105 B、SHA-256 `088f2b43c860c2892c1f6e9a0349547dcd94b021e5d56983349f2cfd81e7ee89`，以及[已验的 7.2 Material 和共同父 parser 原窗](CLIENT_72_D02_ACTIVE_ITEM_PARSER_SOURCES_20261116.md)。已验 94 个分类 literal 不在本次查询范围。目标 framing/count/首 row、条件 String helper 的 cursor 单位和长度仍分别需要固定目标来源；本次没有重新执行目标 parser、getter、native、客户端或上游工具。

## 固定来源和新增方法

| 来源及身份 | 实际检查 | 可复用边界与许可 |
| --- | --- | --- |
| [mavuika-rs](https://git.xeondev.com/mavuika-rs/mavuika-rs/src/commit/67833c157cdfb57eb0854e9b2781d5d89014204b) `master@67833c157cdfb57eb0854e9b2781d5d89014204b`、tree `09ee5cd070a34a973205457f8019047a7f9bbaa2` | 复用仓外既有 clone；README 指向 `CNCBWin5.2.51`。固定 tree 路径清单无 Material 文件。本次只增读 `excel/mod.rs`、`util/mod.rs`、`lib.rs`，不重抓已审 `from_binary.rs` / derive。 | AGPL-3.0。旧版通用设计仅可参考，不证明目标 7.2 framing 或同一对象语义。 |
| [SagiriYuuki/YSRes](https://github.com/SagiriYuuki/YSRes/tree/726dfe5f6af3700abb9623183be0c7e03b586f00) `main@726dfe5f6af3700abb9623183be0c7e03b586f00`、tree `f5e9015823740c997bd82f036b80bf7398dd066b` | README 声明 target 3.4，公开分支 1，全部状态 Issue / PR 均 0；根 tree 未见 LICENSE。仓外无工作树 clone 后只读 tree、README 和 gitlink，未取或运行 bin 文件。 | README 仅为版本声明，不证明 schema 覆盖或目标一致；根项目许可未确认。 |
| [Hiro420/YSRes](https://github.com/Hiro420/YSRes/tree/0c9692c5aa9e2a63ae0ca9d949276ca06bd8e174) `main@0c9692c5aa9e2a63ae0ca9d949276ca06bd8e174`、tree `725715e2f0dc5fb85554e84a94f8c1f88aa1b328`；[ztsvlr/YSRes](https://github.com/ztsvlr/YSRes/tree/778104ce79f63f45389a1e4ac62906a0c2244061) `main@778104ce79f63f45389a1e4ac62906a0c2244061`、tree `2d138960d744d552b563146d0c43ad1ebe686ea1` | 两者均为上述仓库 Fork，README blob 同为 `f334295fdc8a6945cf2f0bcce6d2de1e01a9e5d1`、SHA-256 `e104268ed700a6b05767c474122bbf21f93d9148732597ef4a6c3339159462b7`，均声明 3.4；各有 1 分支、全部状态 Issue / PR 均 0。两者 `ksy` gitlink 均为下列 `19f393…`，不能计作两个独立 schema。 | 两个 Fork 根许可证 API 均为 null；只读比较。 |
| [DataParser-paimooon](https://github.com/paimooon/DataParser-paimooon/tree/19f393de7cee692e22a8136181c6070e1f68884e) `19f393de7cee692e22a8136181c6070e1f68884e`、tree `d986617e0b8f6ea40abb3862143261570f051461` | 两个 Fork 的共同 gitlink；该提交 README 明示定义基于客户端 2.2，`Excel/material_excel_config.ksy` 直接定义旧 Material 行。仓外 clone，只读目标 KSY 和辅助类型，未执行。仓库公开默认分支仅 `main`；全部状态 Issue / PR 均 0。 | 根许可 GPL-2.0，Material KSY 也声明 GPLv2；仅参考旧格式，不导入运行代码或视为国服 7.2 输入。 |

### 5.2 表加载及存在位

`mavuika-data/src/excel/mod.rs` blob `61db2a1cdc17f5498b9c9c5ce9f7a638214d563d`、SHA-256 `6eae520ed830992e90e0ea9ae3a494590c4103dab26d5f6b5d3682738246f93c`：其旧版 loader 先以 `i32::from_binary` 读条目数量，再按条目数顺序读取对应行。`mavuika-data/src/util/mod.rs` blob `5b8c38ee88d40e203c7b6af6cff448ecb2fe0a97`、SHA-256 `609c47935fa9afbf1ece0f2026893cd2140c29bc42910448bd16026a69696752`：旧版 `ExistFlag` 先读取 1 字节 size，再读该数目的 flag 字节，按 bit 检查字段。`lib.rs` blob `85ea1d708fddd5d911679ea5848d03c268038374`、SHA-256 `1c363fa07c9a20266b176f3891ebbf143cce6b48fb5144929b298402fc6abce6` 的测试示例采取同样的 count→rows 顺序；测试未运行。

这些是对应 5.2 代码中的结构，不是目标表头公式、count 单位或 7.2 首行 offset。既有[上一轮对照](CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md)已记录其旧版 `from_binary` 字符串与数值读取及 derive 缺位 `Default`；不把 Default、旧 String 长度或存在位布局复制到本机输入。Forgejo API 于 2026-11-15T18:39Z 对该仓 `forks`、全部状态 `issues`、`pulls` 各取得 1 页、HTTP 200、0 条；此前页面列出 1 分支，未发现新增版本分支。

### 2.2 Material 行与同源差异

[Material 行 KSY](https://github.com/paimooon/DataParser-paimooon/blob/19f393de7cee692e22a8136181c6070e1f68884e/Excel/material_excel_config.ksy) blob `97e8cb4975c0cbbab26cef664edd80aebe12f3f6`、SHA-256 `86c32d23526b0dba17274df51fa59bfcc9b60d109fccd8e3c3b91b02fb87de93`：该旧 schema 以 length-prefixed bitfield 开始，然后按其 `has_field` 逐字段读取；它本身是行 schema，不给出本机目标表 count 和首行物理边界。[辅助类型](https://github.com/paimooon/DataParser-paimooon/blob/19f393de7cee692e22a8136181c6070e1f68884e/aux_types.ksy) blob `ae660848f0199ef80ee97d283788e1fdbfde0d64`、SHA-256 `12b7b7ecafc1494d624871f46037da43fa8a273a2b91ad8f0b71e9a8b158c92e`，将旧 String 表示为 unsigned-VLQ 长度及指定字节数 UTF-8 数据；旧 bitfield 也以 unsigned-VLQ 长度引导。

YSRes parent 锁定的另一 gitlink `1045cf6f0d5d77f914d5441c318724bcb67a19be`、tree `82434f98262f230f74ae7ac46c85c9193ca99c1b` 的 Material 文件 blob `4605d08b57cace1591b5308bbeb9fab6c59c0389`、SHA-256 `04795bd774016fc0f1c5abb34c7ae2662f93858e75928b7be727038bf82556a1`；对比 `19f393…`，唯一 diff 是 `item_use` 数组长度从 signed-VLQ 改为 unsigned-VLQ。两条 gitlink 是同仓不同 schema，不能当两份独立目标样本；此差异本身也不能决定 7.2 的长度解释。目标 parser 先调用共同父 parser，不能仅凭旧版字段名字或次序假定对应。缺同一目标源的 String cursor 语义时，停止依赖该 helper 的路径。

## 查询范围与未覆盖

2026-11-15T18:38Z 实际执行 GitHub REST 仓库检索：每项追加 `is:public fork:true in:name,description,readme`，各取 relevance / updated 两种排序，每页 100。下表均为两排序各自的第 1 页、`incomplete=false`；页数与原始响应见忽略目录 manifest。

| 新查询 | 每个排序 total / 页 | 实际筛选 |
| --- | --- | --- |
| `MaterialExcelConfigData binary parser` | 2 / 1 | Hiro420、ztsvlr 两个 YSRes Fork；README、所有公开分支、Issue/PR 和共同 gitlink 按上表检查。 |
| `Genshin ExcelBin binary reader` | 0 / 1 | 仓库名称/描述/README 零结果，不等于源码不存在。 |
| `MiHoYoBinData from_binary` | 0 / 1 | 同上。 |
| `Genshin ExcelBin row parser` | 5 / 1 | RinoPaw/Genshin-Reverse 及四个 Fork 的默认 README 相同，标明其固定 7.1 Global 样本；没有作为国服 7.2 Material row 来源。未逐分支重审旧 profile。 |
| `MaterialExcelConfigData deserialize` | 0 / 1 | 仓库名称/描述/README 零结果，不等于源码不存在。 |

搜索引擎还按上述 binary/parser 字段词发现 YSRes 线索；二手摘要仅用于发现，实际结论以固定 Git 仓库的 README、tree、gitlink 和源码为准。搜索引擎结果未另保存原页。被搜索引擎提及的 `360NENZ/paimooon-YSRes` 的 GitHub repo 和 README API 均返回 404，不能推断其公开状态或内容；两条 YSRes Fork 和其父仓是独立公开检索命中，不是该 404 地址的替代读取。未对已限制的 Codeberg 地址换入口，也未重做旧 7.2 泛词、Go JSON、67 分支或宽查询。

YSRes parent API 报 13 个 Fork，本次仅审精确查询命中的两个；DataParser API 报 6 个 Fork，未逐一审查。未做 GitHub 全局代码搜索，其他 Fork 非默认分支及其他托管平台未穷尽。mavuika 当前三项 API 返回成功的 0 与 YSRes 404、未搜索范围分开记录；没有把权限或范围不足写成“外部没有实现”。

## 复现记录和后继

忽略目录 `local/migration72/d02-material-row-search-20261116/` 含 GitHub 搜索原页、README、分支及 Issue/PR 响应、固定源码和 manifest。`github-manifest.json` SHA-256 `90ec425a6a6a6023c44e4e2ff4af5885844354d8cb30bd40b8ac01cb89f7fc1b`；`ysres-manifest.json` `5faf9121ab775de626eaa847a7b6b8cd53381332d22f984badb8a81947f3f527`；`readmes-manifest.json` `9250243c2f1a2f9d8a870973ea9654214422b6b2387dff2441c249c8c20b5df6`；`forgejo-manifest.json` `50a564b0b4b97af6b2a24001fe437b9c9e762844986adeddbd2f2ec1f6e4a094`；`mavuika-manifest.json` `323e836d7da6466b6ba433bfa6c574b467ecc9adcef7f8466f564f473d7247ba`；`dataparser-manifest.json` `271d9db2ad9cb32faf0e739a3c0c1fe08563bdf250ce7d83a4c6ee481deae761`。本次读取的固定源码均核 Git blob 与 SHA-256；旧版差异为只读 `git diff`，没有运行代码。

可直接复用的是已固定的旧版源码与本报告中的来源/失败记录，不能直接复用其 count、字段排列、String 长度或缺位默认值。最小后继是主线按照固定国服 PE、唯一方法 slot 和完整边界审定目标 table 方法来源，先闭合 count 和首 row；随后按实际首行 presence 决定是否还需独立 String helper 来源。未获得目标合同前，不生成 walker、猜首行 flag 或用旧版 schema 造资源。A/B、preview 与准确依赖继续冻结；本次无新安全拒绝，无 Git 提交、运行变更、构建、数据库或实机验收。
