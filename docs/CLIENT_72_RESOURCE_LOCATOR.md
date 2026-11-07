# D02-R3：资源 literal 到真实索引定位

## 重大问题计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 将准确 Avatar/depot/skill/Weapon/Ornament/Quest/Chapter/Scene literal，经固定客户端真实路径哈希、GetResourceIdentifier 位运算与两实际 index，连接到实际 block/blob。 |
| 固定证据 | payload-index source `3fd618d4c990f3358eff772b4af30553fb332c2e1ed20423a3f5783e6a59946c`、双报告 `e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9`；两 byte[] 与定位 artifact 固定来源；五 profile。 |
| 未知 | generic loader 类型后缀、准确 hash 算法、variant selector、packed identifier、路径/文件选择、实际 tag9 payload。 |
| 最小实验 | 先核真实 generic usage→类型；原生执行路径/后缀 hash 和 packed identifier 的纯计算段，与独立实现对照；只查实际 index 的真实 key/variant/location，固定少数命中文件做来源核对。 |
| 范围 | 本新专题、resource_locator 工具/test、忽略 resource-locator-astra；不改任何冻结合同或客户端，不 live、不 Git/Gradle/数据库。 |
| 失败出口 | 真实缺失字段/原生依赖按地址停止；无命中如实保留，禁止调路径/大小写/后缀来求命中。不伪造 Dictionary 查询或 codec 返回成功。 |
| 验收 | 来源可重算，实际 key 和文件/区间可复核；独立 64位/非零/边界/错误与双跑；ctable/revision/exporter 未闭保持 false。 |

## 已完成的实际来源桥梁

本轮用准确原生 hash、真实两份索引及 `25539185.blk` 完整 payload，取得九个目标的实际 class1208 `m_bytes`。八个具有准确 global literal；Quest 只有明确的候选路径与真实 index/entry 命中，不能混成已证 Quest literal/loader。**尚未运行各表 parser，不声称候选 JSON、revision 或 ctable 已闭合。**

`14d6e1570` 在 `14d6e158d` 以 `mov rdx,[rip-0x7d5e15c]` 读取 **145983438**，随后 `14d6e1597` 调 `154c35bb0`。该 cell 直接从固定 metadata usage735837 恢复为 methodSpec16871 → methodDefinition80127、methodInstantiation58257 → `UnityEngine.MiHoYoBinData`（type8026）。探索中曾把 RIP-relative 算术写成 +8 的邻 cell，邻项的 method inst=-1 立即未通过 TypeResolver；最终以指令 nextRIP 精确相减并固定真正 cell，不沿用邻项。

静态调用链是：

`14d6e1570 → 154c35bb0 → 154c35d60 → 154c35b10 → 1471361f0 → 146ebb3e0 → 14146acc0 → 1415caa50`。

`1471361f0` 用真实类型名查询 type map，再把 path 和后缀交给 `146ebb3e0`。真实 `31049740` type section 恰有 `UnityEngine.MiHoYoBinData → .MiHoYoBinData`，这项由实际 index bytes 读取，不是按命中结果试后缀。存储方向另核 `ReadTypeNameMap146f00480` 调 `14714b6b0(alias,fullName)`；`14714b75d..763` 以 fullName 为 Dictionary key、alias 为 value，原静态证明在 `resource-locator-astra/static-14714b6b0.json`。

原生 hash 全执行：UTF16 每个 code unit 的低 byte，先 path 后 suffix，补零到不小于 256 的最小 2 次幂长度；原生 MD5 状态/分块/finalize 后取最前八字节小端 u64。这里没有 lower-case 或 UTF8 转换。用 Python hashlib 独立对照全部九路径及空/null/Unicode/256/257 长度边界。

真实 index lookup 为完整扫描原 asset section，保留原 u64 key、tag/value、记录绝对 offset 和 SHA。所有九目标在 `31049741` 各命中一次，均唯一 `(tag0,value178)`；在 `31049740` 无对应 key。它们不依赖 selector 的实际值。没有用人工成功 map 替实际 index。

`ResourceIndex.GetResourceIdentifier` 是旧入口合同已核 owner11099/method78158/`147237b80`。本轮执行其纯计算 continuation `147237cf9..147237d18`，将真实 value 的低 30 位视作 signed 后取绝对值作为低32位，将 `(key>>8)&0xffffffff` 放高32位；未模拟其前置 HashSet/Dictionary 查询成功。再对九项真实高32位执行 native `141a12b90` 到字符串构造入口前，得到八位小写 hex entry 名。

九个定位都给 `(blockID25539185,bundle178,A0,B0,group0,categoryBits7)`。实际 manifest 的准确 `blocks/00/25539185.blk` 项与本机文件 size/MD5 一致；这证明实际索引数字与 manifest 文件对应。未执行 native 路径 opener，也没有把 A/B=0/0 当作空文件或把这份 index 声称为自然已激活的 index。

## 固定真实文件与完整 payload

`25539185.blk` 为 25,793,006 B，SHA `33d71a39ea639d4f9f9a30d7432b11602e52047e5f43b09ba2e3eaa20990266a`；`pkg_version` 与 `data_versions_streaming` 均记录 MD5 `681d56fb69d26e168120e80be3712b3f`、相同长度。它来自本轮实际 index 命中，不是扫描全部资源挑选头。

沿已采、固定 archive context，实际执行 native signature check `1415aa740`、header prepare `1415aa760`、header transform `1415aa820` 和原 getters。header 2293 B、dataOffset2321、tag3、546 块、每块131072、尾块121740，uncompressed 71555980 B；单 CAB 内容 `CAB-36d5b592c9a82c093e5f435fb1dba561`，完整文件无尾。内存预分配与按实际文件复制是显式 fixture；IV、未变换尾段和 canary 均检查，没有 modeled file-open 成功。

546 块**每一块**均执行真实 native prefix transform，再独立 LZ4 解码；首/尾两个真实块还执行完整 native archive/tag3 decoder，并逐 byte 对照独立结果。没有把两样本外推成 546 次 native codec。全量 `25539185.serialized.bin` 为 71,555,980 B，SHA `0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7`。

实际 serialized v17/no-tree 有 2155 个对象：class142、class1210 和 class1208 三种类型，class1210 的 2153 条 entry 构成真实名字→fileID/pathID/u64FileSize。九个 native 名字各唯一命中，fileID 全0，指向本文件 class1208 对象，所有 u64FileSize 与长度前缀一致。对象/entry 解析是从已核 native schema 得到的独立实现，尚未执行完整 SerializedFile consumer。

| 目标 | u64 key | native entry 名 | pathID | byte[] 文件内 offset | byte[] 长度 |
| --- | --- | --- | --- | --- | --- |
| Avatar | `35f3539930d6e87b` | `9930d6e8` | 534 | 2963772 | 64384 |
| AvatarSkillDepot | `4085e3111ad97226` | `111ad972` | 550 | 3198884 | 26024 |
| AvatarSkill | `6c171d1cb9bcabe4` | `1cb9bcab` | 551 | 3224916 | 141702 |
| Weapon | `d5110a52de0bdd1e` | `52de0bdd` | 2115 | 70942612 | 128936 |
| AvatarOrnamentGroup | `d2b519e0714c53fd` | `e0714c53` | 544 | 3106548 | 22 |
| AvatarOrnament | `b7257f7259d2d6b6` | `7259d2d6` | 543 | 3106196 | 346 |
| Quest 候选路径 | `c173d43b87ae8396` | `3b87ae83` | 1646 | 49255284 | 2921870 |
| Chapter | `0e854841e6ee3d55` | `41e6ee3d` | 786 | 6463212 | 39220 |
| Scene | `f34140fd805cfcaa` | `fd805cfc` | 1828 | 55079412 | 280285 |

offset 指完整 decoded serialized 文件内的 byte[] 数据起点，不含其前面的4字节长度，不是 compressed blk offset。文件产物名称为 `<Name>.native-m_bytes.bin`。

| 目标 | byte[] SHA-256 |
| --- | --- |
| Avatar | `466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9` |
| AvatarSkillDepot | `fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b` |
| AvatarSkill | `a5d2fdc6bbc32ea408c51e90c04777b771e9ab4fec4c35fc74186b6dc3c236fe` |
| Weapon | `cca19efbe032e5416d78c28fcb69c2ea2d9707187197aa4ed90d869c3a1ff918` |
| AvatarOrnamentGroup | `bd3bd58de65c59b1f4bb9f87d1535725cf22e621f402965cc42fbb765b454f00` |
| AvatarOrnament | `291895b576850960aa89965ae1ee7e4644f2c0fc31c01973cc6141e139201400` |
| Quest 候选路径 | `8bf07b67e5d0efdf64e043e668c3e214193152c51e5a313349719dbe07484aa3` |
| Chapter | `7bcffabec121dc4e980125c9dbd43a1f781a3cc85c360b4493aefcc2011b3f20` |
| Scene | `2964028cba6cd66b4c04e86472411d10756643e230c1b6b5c34206ab7a0e0ce0` |

## 错误、独立断言与下一缺口

首次新块的完整原生 prefix/LZ4 全量解码已经完成，但复用冻结 payload `serialized17()` 时，它的“首 byte 为 `{` 就 json.loads”分支遇到实际非 UTF8 二进制而抛错。保留了失败，不把它写成真实块解码失败；没有更改冻结源。本新工具只按明确 class1208 schema 读取 byte[]，不尝试由首字节猜内容格式。新增 `{\x00\xf5\xff...` 回归覆盖此实际发现。

八项单测覆盖固定实际 Avatar key、256/257 padding 转换、UTF16 低 byte 与大小写、signed30/64位拼接、高 pathID、byte[] 长度越界、metadata/对象 extent 错误和 binary brace prefix。实际 CLI 中另有11个 native 边界：六个 hash（含 null/空/Unicode/256/257）、四个 packed identifier（含高位/负值），以及负 digest width 原生返回0。它们不计入八个单测数量。

离线 hash 模型只接受总 UTF16 不超过1024 code units 的原生 stack 路径，唯一 native call model 是 `free(NULL,label6)`；原 MD5、stack probing、memset 都执行。表块 codec 的内存模型沿用已冻结 payload 合同，不含 I/O/密码/codec 成功桩。

接下来应将上述固定 byte[] 交对应准确 loader 的 Decode 与表 row parser，以已采自然 permutation/context 核实际 row/字段，再和候选 JSON 对比。当前字节存在不等于表已解码、候选 exporter 同源或自然 revision 已选。无需因 tag9/Oodle 开新工作：真正目标块实际是 tag3，本轮不再扩大09302948的无关 codec 分支。

## 复现

```powershell
python -m unittest tools.test_client72_resource_locator -v
python tools/client72_resource_locator.py --client $env:GAME_PATH --index-report local/migration72/d02-active/resource-payload-index-astra/index-final-1.json --payload-directory local/migration72/d02-active/resource-payload-astra/payload-final-1 --context local/migration72/d02-active/plain/initialization-archive-elevated6.json --output local/migration72/d02-active/main/locator-third.json
```

使用当前 `GAME_PATH`。每次校验五 profile、冻结 index source/report、payload report/两byte[]、archive snapshot 与 table bytes、实际新块及两份 manifest。四分钟为块 decode 明确上限。全部原始输出仅在忽略 `local/`，没有实机、Git、Gradle、数据库或运行资源变更，不需要部署。

## 本轮冻结产物

两新进程 `resource-locator-astra/locator-final-{1,2}.json` 各 1,452,522 B，SHA `fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda`；完整报告和10个 artifact（完整 serialized、九 byte[]）逐 byte 相等，证明 `resource-locator-astra/determinism.json`。

- `tools/client72_resource_locator.py` SHA `413d2501323fde768076d59222a39af30ebd4857d89759478d060b6c22834456`。
- `tools/test_client72_resource_locator.py` SHA `63db65c46befeee0f872f94b9bb1c42aa11ac5e94ed7c9ed380b939aac57f614`。
- 8项单测通过；CLI 9个真实 hash、9个实际值 packing continuation、9个 native entry-name、11个独立 native边界、新块 native头和546个完整块已复验。full native codec只为首尾2块。

旧 payload/index/native/blob/provider 源均未更改。主线已审真实代码、原始证明和模型范围，第三新进程 `main/locator-third.json` 为同一1,452,522字节/SHA，三报告与10个实际产物逐字节一致，8项检查通过；证明 `main/locator-main-proof.json`、日志 `main/locator-main-tests.log`。固定 byte[] 和精确 source/offset/SHA 可交 Avatar/Scene parser 研究复用；这项验收不授予实际 revision、资源 ready或生产转换许可。仅离线工具/测试/文档，无运行部署。
