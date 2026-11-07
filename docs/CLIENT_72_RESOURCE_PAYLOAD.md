# D02-R3：真实 blob payload 的下一原生读取段

2026-11-08，接续已冻结的 [blob 头合同](CLIENT_72_RESOURCE_BLOB.md)。本限定子合同已由主线审核、第三回放并接收；只修改新 payload 工具、测试及本页。客户端已由用户正常退出，研究过程无实时控制、重新启动、进程 API、断点或共享 Gradle。

主线第三完整raw `main/payload-third.json` SHA`efd152bc8e7337ab5af6aa685a1b16567e63c10a84a31398781d2711521deced`，报告及11份完整产物与独立双跑逐字节相同；7项独立检查通过，证明`main/followups-main-proof.json`、日志`payload-main-tests.log`。主线核清400块原生prefix+独立LZ4与5块完整原生codec的区别、两个内存模型及64位m_FileSize纠正。无生产/Proto/config/patch/资源改动，不要求部署；ctable/exporter/revision和资源ready仍未批准。

## 重大问题计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 闭合 `1415aa8c0` 的真实 v5 块前缀变换到 `1415a1ed0` 的 codec VT+18，取得可追溯的注册/类型/算法和独立真实块读取证据，为资源来源继续推进。 |
| 固定证据 | 五 profile 客户端输入；resources5/context6 及执行源码；四真实 blob 和清单；blob 工具源码 SHA `e0be8ece8071730ea7a310208931e71eb358f07646dcd103dd28661d0cfee798`；双跑报告 SHA `fe10d49fdfd7fb5df910da6407fd96bfdbfb02f907cced418145c8d4b5a4d739`。 |
| 未知与候选 | tag3/9 的实际 codec 对象及注册方式，VT+18 的 ABI、真实压缩块处理顺序、额外 native 状态。候选是固定静态注册、构造器可恢复的状态，或未采集的自然上下文；不根据数字套旧版算法。 |
| 最小实验 | 先静态追 codec 表写入与类型，再按真实索引切出固定首/末块，执行原生前缀变换及已证明的 codec；用独立解码/字节布局或错误边界验证，而非仅看 bool。 |
| 修改范围 | 新 `tools/client72_resource_payload.py`、对应 test、本页；忽略输出 `local/migration72/d02-active/resource-payload-astra/`。旧 blob/provider/native/index 合同、生产/patch/配置/资源均不修改。 |
| 失败出口 | 首个未知依赖记录精确地址、寄存器/字段与来源；若必须自然 codec 对象，只给最小只读链，不猜对象/返回/密钥，不重新启动目标，不宣称 ctable 已解码。 |
| 验收 | 固定 input/source hash、实际执行范围与模型边界、独立真实块及错误/边界样本、新进程双跑一致、主线第三复验；精确列剩余 exporter/ctable/Avatar 缺口。 |

先前 REA 对同一大 PE 的函数分析已在 provider 初始化期限耗尽、无函数结果；不重复原样重试。本项延续固定 PE/异常目录/Capstone/Unicorn 的可复验静态及离线证据，不称它们为 REA 反编译成功。

## 现在取得的完整输入

三份 LZ4 payload 的 **400 个块**已经逐一从四文件合同中的原始字节切出，执行真实 `1415aa8c0` 前缀变换并由独立 LZ4 解码器完成。全量产物不是从首末块推算：每块保存输入/变换/输出 SHA 和实际长度，累计长度符合已核原生索引；整个 serialized-file 的文件大小和对象边界也吻合。

五个首/末块另走完整 `1415a1ed0` 原生链并与独立解码逐字节相同。**“所有块做原生前缀”与“五块做完整原生 codec”分别统计**，不声称全部 400 块都执行了原生 codec。

| 来源文件 | 块数 | 完整 serialized 字节数 | SHA-256 |
| --- | ---: | ---: | --- |
| `20527480.blk` | 1 | 5,424 | `c97e7f50c4dcd399a8633a05b1dcc275d69c8ff59999f05154ddb5d1c57868b2` |
| `31049740.blk` | 371 | 48,618,468 | `944116a92195b7c103a962f9cb0a9c91649a9f901a9fb397c332470dbd44f063` |
| `31049741.blk` | 28 | 3,625,456 | `c3508ba37ccd5c98336217b8b198e9158d8c7f36077a63c9f9a14d9ac434a138` |

产物位于忽略目录各报告同名文件夹下，如 `payload-final-1/31049740.serialized.bin`。源 blob 全 SHA、清单/自然路径链由冻结 blob 报告绑定；本次重新核五 profile 文件及四 blob 全 SHA，并要求 archive6 的实际 SHA 等于冻结 blob 合同引用。

## codec 来源与实际执行顺序

固定 PE 的 `1415c2270` 初始化器在 `1415c24e9` 写全局 `B+5757460+3*8`；对象只含 vtable `14220c5c8`。该表 +18 是 `1415c2710`，再调用 `141c77d70`。后者实际使用 token 高/低半字节、扩展长度、16 位小端回溯距离及重叠复制；结合真实样本和独立格式实现，判定为 LZ4 block 解码，不是只根据 tag3 猜测。

独立实现依据 [LZ4 官方 v1.10.0 block 格式](https://github.com/lz4/lz4/blob/v1.10.0/doc/lz4_Block_format.md)，不声称目标静态链接库的精确版本就是 v1.10.0。原文副本留忽略目录；工具自身源 SHA 固定独立 Python 实现。

每个完整原生样本保留有序入口，实际顺序为：

`1415a1ed0 → 140971460 → 141a2e4f0(NULL allocation boundary) → 1415a3e20 → 1415b6ee0 → 1415aa8c0 → 1408bc470(及已核原算法) → 1415c2710(VT+18) → 141c77d70 → 141a2e5a0(release)`。

`1415a1ed0` 的请求 offset 和 count 须按块大小对齐。本实验单次请求一个完整块容量；末块的真实输出仅为索引中的 tail 长度，余下容量保持 canary。它先从固定 cached window 读取真实压缩跨度；前缀变换不触碰 128 字节之后的数据；codec 输入/输出长度按原 caller 的两条比较核精确相等。全链返回 true、输出长度及字节对照同时成立才接受。

tag3 对象采用准确初始化器的 `1415c24ce..1415c24f0` 片段写入 vtable/global；RSI 来自同一初始化器已证常量，RAX 是离线分配的 8 字节对象。它是明确寄存器夹具，不称完整初始化器或自然运行对象。tag9 则执行真实 `1415c1e80` ctor，确认 VT `14220c570`、成员+8=9、VT+18=`1415c1ea0`。后者进入 `1415c1700/141c802e0`，固定代码有 Oodle decoder 及 `oodlelzcompressors.cpp` 来源字符串；没有把它送入 LZ4。第四 blob 的真实块前缀已变换，但其 codec/payload 本轮未执行完成。

## 内存模型与错误边界

仅两个 native 函数有模型：

- `141a2e4f0` 只接受旧指针 NULL、size≤0x40000、alignment1、label0x48、flags0。准确下游 `141a2f1d0` 的 NULL 分支 `141a2f61b` 转到实际分配接口，支持把该窄情形解释为新存储；不处理未知 realloc 或内存服务 singleton。
- `141a2e5a0` 只接受该模型持有且 label 相同、两侧 canary 完整的指针。每个原生块后所有 scratch 均释放，才回收离线夹具区域。

文件读数、前缀算法、codec、长度比较和内存复制均执行原代码；没有 I/O 返回模型或成功桩。archive/header/cache 对象依据固定原文件和冻结头结果构造，并非完整原生 archive ctor。实际 256 字节算法 context 使用 archive6，未套 managed permutation。

native 验证另有 11 个案例：literal、重叠 match 两个成功；截短 literal、输出不足、空输入、input≥INT_MAX 四个 codec 拒绝；非对齐 offset/count、零 count、null 输出，以及固定真实压缩块的 cache 窗口少一字节五个 archive 拒绝。失败保持输出界限，真实短窗口不进入 codec；其分配和释放仍完整。另有 7 个独立单测覆盖长 literal/match、无效距离/截断/容量、索引名字/尾部、64 位 pathID/fileSize 及 serialized header 拒绝。

## serialized-file 与 `0000006f` 的桥梁

实际 payload 没有 `UnityFS` 字符串魔数；不能依文件扩展名就说它是该格式。其首四个大端 u32 分别为 metadataSize、实际 fileSize、17、dataOffset=0x1000；endian字段为0，metadata中引擎串是 `2017.4.30f1\n2`，platform=-2、无type tree。三个完整文件的 metadataSize 均165，按实际记录解析得三个对象，所有区间均在文件内。

固定客户端 `141491de0` 的 metadata consumer 直接解释上述 v17 的 type/对象字段、处理 platform=-2 并检查引擎字符串；相应诊断明确包含 `SerializedFile` 和期望 `2017.4.30f1`。本轮静态恢复其消费依据，**没有声称完整 native SerializedFile consumer 已执行**。

三个文件都列 classID142/1210/1208。固定 native schema 有：`1419dfbc0 → 1419d91e0` 声明 `m_Name`，再 `1419e15a0` 声明 `m_AssetIndex` 元素的 `m_FileID` 4字节、`m_PathID` 8字节、`m_FileSize` **8字节**；`141136af0` 声明 `m_bytes` 为字节数组。独立解析按这些字段宽度核本次对象。类注册表本身的运行时映射未执行，字段/schema证据与实际 classID/对象内容的一致性保留为静态/结构结论。

必须保留一次纠正：初次候选脚本把索引最后8字节误拆成两个u32“length/offset”。准确 schema 表明那是一个 uint64 `m_FileSize`，已在冻结前修正；测试用大于2³²的 fileSize 和大于2³²的 pathID 防止回归。零高位不代表存在 byteOffset 字段。

| 文件 | 实际索引名字 | m_FileID / m_PathID | m_FileSize 与本地 byte[] 实际长度 | byte[] SHA-256 |
| --- | --- | --- | ---: | --- |
| `20527480` | `2ff4e9af` | 0 / 3 | 1,170 | `50f647957e278d6ef16f1c4f6098ddced9c072c64a304576eff26aa475db6697` |
| `31049740` | `0000006f` | 0 / 3 | 48,614,214 | `216c791b536ce8e4f1f0128c200a791015768491bf23f2a8a9bc618720b6eb7d` |
| `31049741` | `0000006f` | 0 / 3 | 3,621,204 | `782aa1208ab3070a567f8907e7f15b8f3ad046b0d661cdd4774ef9af8f81dffc` |

在这三个具体文件里，pathID3 对象从 serialized offset **4248** 开始，其 u32 数组长度之后的 payload 从 **4252** 开始。工具依据每个文件真实 metadata 计算这两个数，不硬编码切片；完整 byte[] 输出为 `*.object3.m_bytes.bin`。索引 fileID必须0、pathID存在、fileSize恰等于被引用数组长度，才输出关联。

20527480 的对象内实际名字是 `channelconfig.asb`，1,170字节能完整解析成渠道 JSON；可见 `ChannelName=CNRELWin`、`PackageName=YuanShen` 等字段。这只是固定客户端资源中的配置内容，不证明自然运行已经消费或开启某配置，不替代SDK/HTTP实机验收。

两个 `0000006f` 的 byte[] 已取得，但没有执行完整 `141122120/141122e00` reader。`31049740` 开头是 u32 357 和随后可见类型名字符串；`31049741` 开头 u32 为0。**这些开头不能直接命名为某算法或 exporter 格式**。下一项需对照准确 `LoadBinDataZipFile/ResourceIndex/DeserializeBinary` 消费步骤、完整计数/64位/嵌套/尾部，再确认逻辑名及 revision。当前不声称它们等于原 `ctable.dat` 解码结果，也未把资产原字节写入生产资源。

## 复验

```powershell
python -m unittest tools.test_client72_resource_payload -v
python tools/client72_resource_payload.py --client "$env:GAME_PATH" --blob-report local/migration72/d02-active/resource-blob-astra/blob-final-1.json --context local/migration72/d02-active/plain/initialization-archive-elevated6.json --output local/migration72/d02-active/resource-payload-astra/payload-final-1.json
```

第二个新进程写 `payload-final-2.json`，每份报告旁各自产生完整 payload、byte[] 和五个原生样本。批次设四分钟上限；这里的批次不计实机运行。冻结 SHA/字节一致性记录在 `resource-payload-astra/determinism.json`。仅离线工具/专题变更，无需部署；主线负责第三复验和提交。ctable、exporter/revision、资源ready及 D02 完整客户端验收仍未完成。

冻结双跑报告均196,804字节，SHA `efd152bc8e7337ab5af6aa685a1b16567e63c10a84a31398781d2711521deced`，报告及11份产物逐字节相同。工具源码 SHA `b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa`；7单测、11 native边界、5真实完整原生块与400全量独立块已验。后继转新 payload-index 合同，本页三文件冻结不再扩展。
