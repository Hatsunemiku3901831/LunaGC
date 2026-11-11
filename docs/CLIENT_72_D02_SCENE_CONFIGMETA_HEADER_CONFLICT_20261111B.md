# F-03d3s2 / CONFIGMETA-HEADER-CONFLICT（2026-11-11B）

## 中文重大问题计划

| 项目 | 本卡约定 |
| --- | --- |
| 目标 | 解释准确ConfigMeta候选segment中，原getter给出的total284563为何超过index所选212203 B；区分已验语义覆盖不足、实际格式/codec和原context/IV/边界来源问题。 |
| 已有 | 真实141 B loader来源、准确path/type suffix/index/block映射，两个fresh同一117 B解码header及六scalar getter，固定原BlobProbe/parser和历史四header合同。 |
| 准确冲突 | 实际getter为total284563、dataOffset145、tag9、blockBytes262144、blockCount2/contentCount1、末块72852；旧parser要求total不超过提供的212203 B，在payload前真实拒绝。 |
| 方法 | 先固定新原件SHA；只诊断已有header bytes、原getter记录和准确index定位行及同block邻接记录。对照原getter已有机器码/汇编及已验工具的字段来源，先判断header内部表/实际段长关系；需要新来源时写唯一有界输入规格交主线，不原样重跑。 |
| 修改范围 | 仅本页和私有`local/migration72/d02-resume-20261111b/scene-header-conflict/`；旧parser/门禁/工具/已冻结专题只读，不扩source、payload、新body、codec或现场。 |
| 产物 | 有来源的冲突诊断、排除/未排除假设、准确最小后继规格。必要的新大codec研究另先中文GitHub增量计划，再按主线调度检索；不重复旧全量检索。 |
| 验收 | 新判断能由固定原件及独立字段/区间算术复现，区分观测与推论；两fresh诊断和主线第三；不把tag9称LZ4，不把范围/新codec技术停止称外部阻塞或放宽parser。 |
| 停止 | A/B被拒动作及依赖保持不触。新安全拒绝保留原文并停该项及依赖，不换模型/工具重试。 |

## 当前状态

**本次长度错误的根因已实证为输入截短：index B=212203在这个样本中只覆盖header与第一压缩块，完整header声明及独立下一location A共同给出284563 B。** 原parser拒绝正确，不修改字段语义或放宽门禁。完整source/header-only包已双fresh与主线第三通过；tag9 payload尚未解码。

## 实际原件与三个独立等式

正式来源为 `scene-configmeta-locator/final1/` 与 `final2/` 的五份原件，均逐byte相同：

| 原件 | B / SHA-256 |
| --- | --- |
| segment.bin | 212203 / `2033c3659731308aa208d4e3ac9ee52e0b9df1396a5d0be32a3b5f5971859bb0` |
| decoded-header.bin | 117 / `207d6cdc9f26656a1a9ff05ad4b2087102fd826697045f1f40fffa38d4a5a741` |
| header-raw.private.json | 3474 / `6eed04b362954ac93b0fd989cbfb1c0e47737eb3fee9df418947084330084196` |
| pre-decode.private.json | 6838 / `1d9521427268abc45f62e1351413928ff09ba3e8bf6d56365763b1de12b2226e` |
| failure.private.json | 6949 / `f19ebc044f7a5c5bf97de4a09683569fec2849670ae0e669bfa10ccdc6653cb5` |

原header与实际getter一致给出 total284563、dataOffset145、tag9、blockBytes262144、blockCount2、contentCount1、末块72852。H+20的有符号相对地址指向H+56；这里两个原U32累计压缩末端为 **212058、284418**。

| 等式及独立来源 | 实际含义 |
| --- | --- |
| `145 + 212058 = 212203` | 原index B恰等于header到**第一**压缩块末端；此前选取的segment在这里结束。 |
| `145 + 284418 = 284563` | header total与其**最后**压缩末端自洽，维持既有v5完整blob长度解释。 |
| `308700 + 284563 = 593263` | 同一固定location表中下一bundle77054的A恰为593263，独立佐证完整物理终点；不是增长窗口直到parser通过。 |

当前短片恰缺 **284418−212058=72360 B**，等于第二压缩块的完整长度。

| 完整blob中的相对区间 | 实际文件绝对区间 | 长度 |
| --- | --- | ---: |
| header `[0,145)` | `[308700,308845)` | 145 |
| 压缩块0 `[145,212203)` | `[308845,520903)` | 212058 |
| 压缩块1 `[212203,284563)` | `[520903,593263)` | 72360 |

header另外两个相对指针落在H+64的content项与H+28的bitmap，均在117 B原header内。唯一content为offset0/size334996、flag=true、`CAB-46c2d3a53f36734ca42529d3cd8ec855`；`262144+72852=334996`，名字在原header最后一字节NUL终止。这里只诊断已有header，没有读取content数据。

## 为什么无需换getter、context或格式解释

新六个scalar getter各自的原生path SHA与[原四header合同](CLIENT_72_RESOURCE_BLOB.md)对应getter完全相同；实际值又分别与原117 B字段相等。signature/prepare/transform及getter记录均没有native函数模型、memory model、error或failure。新输入使用同一已验archive table SHA `789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297`，IV仍来自准确原segment的前置原bytes。完整相对表、块末端、content和下一独立location共同对齐，无须用替代context或新header格式解释本次拒绝。

[原index合同](CLIENT_72_RESOURCE_PAYLOAD_INDEX.md)只在bundle29475/A0/B3978的**单块**样本完成过B与完整blob长度桥接。单块时header+第一块即整个blob，不能推广为全部多块资源的B。此新样本修正的是上一locator规格对输入切片范围的前提；旧四header、原索引raw A/B及原parser保持原范围有效。当前只确认**本样本B等于第一块前缀长度**，不为B追加通用“预读长度/缓存长度”业务名，其原native使用语义仍需独立来源。

## 已批准的完整source与完整header复核

冻结 `scene-header-conflict/complete-blob-source-request.private.json` 为9,260 B / SHA **`7d7d37c45796ada9770f0a5e1a6ed19f4a3bb6ab727861ccd7818b09e903b82f`**。它限定：

- 每fresh重新解析GAME_PATH并核五stock、原block完整1,751,851 B/SHA `f0191829b0d173e2da353c06c81b119c0ceac7c4875f43da077d4e78c4f46a76` 后，唯一读取 `[308700,593263)` 的284563 B；原212203 B prefix必须与上述原件逐byte及SHA相同，只新增72360 B。
- 重新验证完整117 B header及现有14个scalar/block/content getter（含index==count），IV/guard/cookie/原执行范围和无成功模型门槛保持；不用新PE窗或376 MB扫描。
- 只把**实际取得完整284563 B后的len**交给原封未改的 `parse_header`；原212203 B短片、少1 B、prefix漂移、累计末端/相对表/content边界错误仍须拒绝。当前诊断没有给parser虚构一个更大file length来称通过。
- 在tag9前继续停止，0 payload prefix、0 codec、0 serialized解析；双fresh完整source/header与主线第三后，再决定codec任务。

该source/header-only包现已实际完成。`scene-configmeta-complete-header/fresh1/`、`fresh2/`及`main-third/`的五份原件逐byte一致：

| 新原件 | B / SHA-256 |
| --- | --- |
| blob.bin | 284563 / `e0193e92b4614a9aea3b128f7d2c77d9372dfb7fe6328044f402d58f9f54acb0` |
| new-tail.bin | 72360 / `7ffa03c6dbc8024f919bd70d3eedd9c46142b62da56bab7881957b170d4f631b` |
| decoded-header.bin | 117 / `207d6cdc9f26656a1a9ff05ad4b2087102fd826697045f1f40fffa38d4a5a741` |
| header.private.json | 13368 / `2d3bf8e7a164dc3fe37f7e11645146701e1885f70866c99b72de0a00565c9e28` |
| pre-decode.private.json | 5634 / `1b5db49b31decb7982ca6311414e6588648474838c076c83971f3fce96b9a22a` |

真实取得完整输入后，未改的`parse_header`和14个原getter均通过，输出仍为tag9、两个compressed ends212058/284418、content334996、fileTailBytes0。原212203B短片与少1B的284562B输入仍拒绝；原prefix逐byte相等。独立审核`audit1`、`audit2`、`audit-main-third`各1222B/SHA **`6190ef8926bd5495e09e9a9b9ecdc15ae396a0f194007ffa29660f867e7a8416`**。此出口只关闭完整header来源，未调用payload prefix或codec。

## tag9既有来源与实际支持边界

tag9早已出现在原09302948的单块样本，不是本次长度错误才发现的新格式。已有[原payload合同](CLIENT_72_RESOURCE_PAYLOAD.md)可以只读复用：

| 已有证据 | 实际通过及边界 |
| --- | --- |
| `tools/client72_resource_payload.py`，25,622 B/SHA `b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa` | 实际执行tag9 ctor `1415c1e80`，确认16 B离线对象的VT `14220c570`、+8=9、VT+18=`1415c1ea0`。不等于自然global codec9实例或完整初始化器已执行。 |
| 固定payload报告196,804 B/SHA `efd152bc8e7337ab5af6aa685a1b16567e63c10a84a31398781d2711521deced` | 旧09302948的3833 B块真实完成prefix变换，SHA `457c97655ea5990f8f1ea1167fde6cc0154d1b03d5ffc1aab445fb8b099999bd`；报告明确`payloadDecoded=false`、`Oodle state lifecycle not executed`。没有已验tag9解压产物。 |
| 旧保存 `static-1415c1e80.json`，4,223 B/SHA `1f7377dfa435a19b9be4238df294526e2915723ddf09c43d9bfc3fa55b8f4a30` | 构造和decoder wrapper前缀中可见 `1415c1700`状态准备与`1415c17e0`调用；这份128 B片段不是完整wrapper边界，不能补其未保存尾部或销毁职责。 |
| 旧保存 `static-1415c1930.json`，5,064 B/SHA `9331a14a008ab1fb76556233e4274ccb1fdd860cd69e54724444ec147bc0f976` | 已存初始化/CPU选择原汇编仅作来源；没有本轮执行。旧固定代码的Oodle诊断及`oodlelzcompressors.cpp`来源支持Oodle系列身份，不证明内部流一定是某一个compressor或版本。 |

进一步按旧JSON的record入口盘点，已找到完整wrapper125B、state215B、cleanup64B、native Create269B、reset274B、sizing274B、普通decode loop224B，以及保存于`static-141eb87c0.json`的核心3517B（code SHA `11bb0081a376854134b31662dff907fd29358f89e67d593b70773979efbf8d4a`）。这些来源不需重新提取。原wrapper每次清零16B状态，实际参数为state init(-1,0)→reset(0,expectedOutput)→core→普通收尾；count指针在wrapper中只读。核心先调用内部流头reader并查映射表后才选择算法，外层tag9不能决定内部compressor。

这次只读分析形成两个后继独立规格，均未执行：一是精确原CALL绑定的内部流头reader完整source窗，二是复用已验81B原prefix函数、严格停在codec之前的两块输入出口。前者不重取旧核心或递归追多算法body；后者不需要Oodle状态、自然codec registry或被拒A/B材料。具体pins、ABI/fixture、预算、反例及停止点留在忽略目录的`codec9-header-reader-source-request.private.json`（8088B/SHA `df478a7797a0632e10ebecdd1a9cf88286a5249e0eb93db7396c041afe08b2fc`）和`codec9-prefix-only-request.private.json`（10031B/SHA `42364f5b71128ae356940f6165d5bcb3b77718e1955266dcde3b3ffb9b5c5abf`）。这不是codec9已获支持或外部阻塞结论。

现有完整native块读取和独立LZ4对照只覆盖tag3。`PayloadProbe`没有提供已验codec9全状态生命周期/自然global实例/完整解码成功；不得把现有tag3支持当tag9支持，也不能仅改白名单或直接借未知返回继续。

本ConfigMeta的**目标输出模式**仅由实际header确定：原两压缩块分别需要262144 B、72852 B，按原顺序串接334996 B；单一CAB content为`[0,334996)`。完整state准备/解码/收尾的普通来源已整理，当前缺口是准确内部流头/算法分派、实际容量与callback/CPU/内存条件及所选callee的执行闭合；不能指定一个未证明的decoder mode或跳过state。Sol max中文GitHub增量计划已交主线，由Terra max按README与相关分支检索；检索与后继prefix/reader规格各自验收，不重做此前资源检索，不用外部成功代替原native生命周期或自然loader。

## 独立诊断复现

私有 `scene-header-conflict/audit-header-conflict.private.py` SHA **`db27c52a090eae82087cf11d113c56ec7be52db8218b673b85f58b5b1c67886b`** 只读固定header/索引/旧报告及source，43项来源和区间断言；同时确认原短片仍被原parser以同样错误拒绝。两fresh报告各 **10,491 B / SHA `9e86b36b40ea9628361889c905940e42389d9258e0ef4ec66fada763e11287f2`**，逐byte一致。这里不是43个native测试，没有原样重跑header Probe，也没有读新尾部。

```powershell
python -X utf8 -B local/migration72/d02-resume-20261111b/scene-header-conflict/audit-header-conflict.private.py --output local/migration72/d02-resume-20261111b/scene-header-conflict/diagnosis-main-third.private.json
```

主线第三诊断与上述完整source/header第三现均一致。此独立出口为输入截短诊断及完整header复核；旧parser/共享工具/运行材料未改，无部署/客户端新增验收。完整ConfigMeta对象、PathHash.bin/payload/reader、MPLevel分组和全部Scene/D-02父项保持未验。本页冻结交主线提交；后继codec产物另立验收，不追加到本页。
