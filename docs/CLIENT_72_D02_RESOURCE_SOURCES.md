# D02-R3：必要资源输入来源审计

本批只审计来源和可独立闭合的资源输入，不修改运行资源、Proto、配置、补丁或 sender。当前 `resourceReady=false`；固定候选 JSON 与客户端二进制 parser 的对应关系、实际运行时 revision 选择及自然 loadAll 仍分别验收。

本来源子合同已由主线审核：第三新进程审计13份固定输入/3161清单行，8项定向检查通过；原字节与代理冻结双跑一致，SHA `e874ec98e88f651281869ae0b2caf8c45ae953032643fec93366d310042a726d`。证明为`local/migration72/d02-active/main/resource-source-third-proof.json`；不认证JSON→binary、自然选择或资源就绪。

## 开始前的小计划

| 项目 | 本批约定 |
| --- | --- |
| 目标 | 在本机既有文件及固定可验证来源中寻找 7.2 原始 blob/exporter 映射，补齐 Ornament、SkillDepot、Quest、Scene 必要输入的来源链与内容 hash；将离线可闭合和自然选择所需输入分开。 |
| 已有证据 | 固定候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`；根/Excel/BinOutput tree 已存。本机客户端三输入已被此前研究固定；depot/条件 parser、Ornament 两表 loader 身份及 Chapter/303 窄合同已有独立证据。 |
| 未知 | 实际资源容器与所选 revision；候选 exporter 的代码/字段映射；Ornament 实际表内容及默认记录；Quest 自动接受来源、LuaNotify20 的字符串进度语义。 |
| 假设 | 固定上游 tree 可补齐同一候选的缺表并验证 Git blob；客户端安装目录可能保留 manifest/容器输入。两者都不预设与自然运行选择相同。 |
| 最小实验 | 先核验既有 Git tree/commit 链并列出必要表 blob；有界检查本机候选/工具/安装资源清单；仅从固定 commit 获取缺失必要表和上游说明，核 blob/size/SHA-256；记录检索范围及否定结果。 |
| 修改范围 | 仅新增 `tools/audit_d02_resource_sources72.py`、对应 test、本页；原始结果在 `local/migration72/d02-active/resources/`。共享索引由主线维护。 |
| 失败出口 | tree/blob 不符立即拒绝该输入；缺 mapping 不造 alias；容器存在但无合法解码合同则保留 hash/路径与未知；自然选择未观察则不认证 revision，不运行猜测 loadAll。 |
| 验收 | 新工具对 tree 篡改/同名异内容/重复键/缺字段显式拒绝；实际审计双新进程字节一致；交付必要表来源、有限内容摘要、准确未知、可执行后继及自然启动需采集的字段。 |

## 实际结果

来源子合同已冻结。新增 [审计工具](../tools/audit_d02_resource_sources72.py) 和 [8 项测试](../tools/test_audit_d02_resource_sources72.py)，实际两次新 Python 进程从固定树、现有候选原字节和当前安装重新计算；没有使用摘要替代原文件校验。13 个输入均通过根→子树→blob/size 验证；7 个旧候选表的历史引用测试没有重复计为本轮结果。

固定候选仍是 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`，根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`。除上述十个 Excel 外，13 个输入包含 `BinOutput/Quest/303.json`、`BinOutput/Scene/Point/scene3_point.json` 和上游 README。完整 Git blob、大小、SHA-256、逐副本接受/拒绝和字段存在性在忽略目录报告；混合 fixture 仅按每文件的真实 blob 接受，不认证整个目录。

| 文件 | Git blob | SHA-256 |
| --- | --- | --- |
| AvatarOrnamentGroupExcelConfigData | `b0e8dde4176a8f4fdd2dcd49ca4587c6676da470` | `f1a4885c2fdff06021263e4d6627feca4ff135547820e85a814eecb6495122da` |
| AvatarOrnamentExcelConfigData | `4418e8163af74ca1ea06e76386e41bc1aca8cd70` | `aad42c01df12aaa4956374226e26db11cdbd3d72a5586e4c9bf2e4415ae2287a` |
| AvatarSkillDepotExcelConfigData | `f7770f01224583537156de91e0ec94ee9ff5115d` | `c149f12cce4df46aa9af15701a107b42a45feb7716c7f13448ccb6d7b90258b8` |
| AvatarSkillExcelConfigData | `f58ac30e0b62ee342ea11fc76d4e2b0d0613b24c` | `d799dcba02f383dae99fe32b3393408773f67001fe9622801366a524732102d7` |
| QuestExcelConfigData | `0788265f3a267a22abf2c7e33f04705704a04b0d` | `c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87` |
| MainQuestExcelConfigData | `4879ef692ba1e35afdf72d93896a4558508fdd5e` | `f6dcb39d39526cc699daf118b21c68481897238651af92b2e1ee77a54d757287` |
| ChapterExcelConfigData | `8f8f8fa23df84686883d931f248e6ff0b626ddd7` | `6c291ee3b5ca1e29b1b4395c1e13103d1965e1ce687eb3e0364e08dfcc55f164` |
| SceneExcelConfigData | `9f06ef286acb103789c813cee90a62965430d512` | `5a906393d1de46a6d397ae169cdcfb76a9ee77fc1c9e38dd3aec752656978550` |

两张 Ornament 表是本批新补齐的数据。Group 仅 1 行，具名 `avatarId=10000136/groupID=1`，原列 `DLGBPJPJMHP=101`；Ornament 仅 2 行，同属 groupID1，原列 `PBMHHJCEJPJ=101/102`，后者有 `ITEM_COUNT_GREATER_EQUAL`、参数 `220141,1`。完整原行只在本机候选文件。出生角色 10000005/10000007 没有该表具名 avatarId 行，但尚不能把这点转换为 tag38 空/default 许可；DL/PBM 两列业务名没有批准。已将这些固定输入直接交 Avatar 研究负责人继续 typed 来源研究。

本批重新确认 501/701 没有具名 energySkill，504/704 有该键；这仍只是 JSON 字段存在性，不能替代 flag→parser 对应。17,814 条 QuestExcel 仍全无 acceptCond、acceptCondComb 全为 object。缺失接受规则未由来源审计创造，自动接受来源保持未知。

## 本机资源清单与准确边界

固定三客户端输入实际 SHA-256 与既有 profile 相等。内置 R/D `49008628`、S `48722240` 与候选标签 R49086080/S49210135/D49230962 的关系仍未认证；没有改配置或版本文件。

| 内置 JSONL 清单 | 原字节 SHA-256 | 行数 / 审计路径内大小相符 |
| --- | --- | --- |
| data_versions_streaming | `e6510ecb754a6bd1afd8aec71568aef6fcf957475d22f962ff3bb0afb2265841` | 533 / 533 |
| res_versions_streaming | `512a84b0d1bd3e7b4e91d276e40815409f36ce103e307b20b5d2ed6a05fde21e` | 2624 / 2090 |
| silence_versions_streaming | `c658b5de5408dc30a590b3a565edd6fba35fc9064b2ebda4f4ee33c3cfc900df` | 4 / 4 |

审计的五个相对根为 StreamingAssets、其 AssetBundles/AudioAssets/VideoAssets，以及 Persistent。res 清单 534 条未在这五根定位；不能据此称安装损坏或 P0 缺文件。三清单均无大小冲突。所有容器 MD5 未重读，清单存在性/大小通过没有提升成完整安装或内容通过。

`Persistent/ctable.dat` 与 `StreamingAssets/ctable_streaming.dat` 同为 28,160,000 字节，SHA-256 都为 `03b2478ba340c6fcda27a4988c6501c32665b1323022e927aaeeb1f338e4710b`。实际 MD5 `cd5f73407c6c4733f3c86a145e9f94c0` 与 res 清单声明的 ctable.dat 大小/MD5 相符。这里已获得真实安装容器索引输入，不能继续笼统交接为“本机没有资源输入”；但该索引尚未解码，清单 remoteName 无所审逻辑表名，logicalName→block 对应仍未知。

上游[固定 README](https://github.com/DimbreathBot/AnimeGameData/blob/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1/README.md)及完整根树提供发布数据来源，没有提供本次 exporter 字段映射。工具/既有 references/analysis 的限定源码和文件名检索，以及 `site:github.com Genshin ctable.dat parser`、`site:github.com AnimeGameData exporter ExcelBinOutput 7.2 field mapping` 的有限检索没有得到可用于此 profile 的 exporter。该否定结果只覆盖所查范围，不证明此代码不存在，也不认证搜索结果中的其他客户端 profile。

## 实际验证与复现

```powershell
$resourceAuditClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/audit_d02_resource_sources72.py --client $resourceAuditClient
python -m unittest tools.test_audit_d02_resource_sources72 -v
```

只在缺失输入首次获取时显式加 `--fetch-missing`：下载固定 commit URL，先核大小/Git blob 再写忽略的候选缓存；不读 main，不写 resources。第三复验前保持同一专属输出目录，使 origin 标记一致；比较原字节与 `source-audit-frozen-2.json`，不能按 JSON 对象相等替代字节核同。

| 冻结产物 | SHA-256 / 字节 |
| --- | --- |
| `local/migration72/d02-active/resources/source-audit-frozen-{1,2}.json` | `e874ec98e88f651281869ae0b2caf8c45ae953032643fec93366d310042a726d` / 40,436 |
| `tools/audit_d02_resource_sources72.py` | `1df31c57bb533d495463decb9d7a660f633b266846243aa316741e20eced9a19` |
| `tools/test_audit_d02_resource_sources72.py` | `100ac28e09342479d1da5f9dd45dc8476b3f1c04d406363829d2025a7fcf12fb` |

8 项测试实际通过，涵盖 tree 链替换、blob 同长异内容/截断、混合副本拒绝、无可验输入拒绝、manifest 重键/重名/路径/类型边界及大于 int32 的文件大小。两次新进程原字节一致，证明 `source-audit-determinism.json`。首轮发现 Windows 文本换行使 stdout 的 UTF-8/LF hash 与落盘 CRLF hash 不同，已改为显式 UTF-8 字节写入；`source-audit-crlf-{1,2}.json` 保留作失败/修复前证据，不能与最终 LF hash 混用。

没有启动/操作客户端、调试器、Mongo、Gradle、服务或端口；没有资源/运行代码改动，不需要构建部署。没有本代理 Git 提交，交主线审核及独立提交。

## 后继：离线解码与自然选择分别推进

1. 已向主线交付 ctable 固定原字节位置/hash。实际静态链已定位到 `AssetBundleExternalResourceProvider.LoadResourceIndex 14713ea90 → AssetIndexDeserializer.DeserializeBinary 146f00000 → AssetIndexBinaryReader ctor146d8bd20`；文件后继到本机 native helper `14146c630/141122120/141122e00`。这给出可以离线继续研究的具体目标，不要求先取得自然运行结果。后继由独立`client72_resource_index.py`和`CLIENT_72_RESOURCE_INDEX.md`活动项推进，尚未完成解码；来源审计工具在本表冻结后不再承担原生解码。
2. 主线自然启动若经过 depot loader `14d6d53a0`：在 `14d6d53bf` 调 `14d6e1570` 返回 `14d6d53c4` 时，记录实际资源路径、选择的 manifest/revision、包装对象及其两条缓冲分支。`+20` 非空时记录该 native buffer 与 `+28` 长度；否则记录 `+10` 的托管数组、其 `+18` 长度和数据原字节。必须在 `14d6d53ef → 14d6d54a0` parser 和后继释放前固定 SHA-256，并保留 null/长度0/读取失败；只抓 I/O 入口或表名字不足。
3. 对同两张 Ornament loader 和实际必要 Quest/Scene 输入，使用相同的来源/选路/返回缓冲/解析缓存身份，核所选 ID、引用和拒绝日志。实际选路可以证明所选 revision；它不能创造服务器 acceptCond，也不能用一表返回推完整 loadAll。缺来源的 JSON alias、required/runtime/ready 状态保持。
