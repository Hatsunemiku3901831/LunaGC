# Skill 元素字段后缀来源合同

`R3-SKILL-ELEMENT-SUFFIX-SOURCE-CONTRACT-20261116` 已完成 source-only 规格；后继 `R3-SKILL-ELEMENT-SUFFIX-HOST-PILOT-20261116` 由主线授权唯一集成者实施。本文不预称新值或 pilot 已通过。它只解决一个实际字段的可复现读取合同，不要求先闭合 367 条完整 Skill。

固定 8309B parser 的 durable listing SHA-256 为 `e05cbc6e1a98af8c21ec442887852a7df2d5fbba86eacfbee2d5d88fc82eaa16`，原 body SHA 为 `2f8dbdd35f208383213cc4843fe009845825a9f33e4231f553b3b0eb60a3602d`。在 `149b58b17` 覆盖 `88602.field440577/+54` 后，正常返回前只剩三个条件 token：原 mask 的 bit54 消费1B、bit53 消费4B、bit11 消费4B，没有后继字符串或列表。两个原 scalar setter 的正常路径不消费 reader 字节；本后继也不调用它们。

令 `T=bit54+4×bit53+4×bit11`。原 mask bit25 为1时，元素原 word 位于 `row.source.end-T-4`，按 `((LE32(word)+0x5a02b85a) mod 2^32) XOR 0xdd69ea57` 得到逻辑 enum74813；原码直接 store enum，不经过 scalar wrapper、codec、seed 或 Float getter。bit25 为0的源码分支给逻辑0且没有 word，不能把后缀的另一个值误当元素。本 pilot 三行均要求存在元素 token。

`source.end` 复用已验完整 Skill 表记录：原 driver 在 parser 入口记同 row/reader 的 cursor 和实际返回 PC，回到该 PC 时记 end，再按同 row 关联 source。旧 Skill trace 的 refill `14f614760` 次数为0；固定表1176行、141702B和EOF已验。本后继只读 key/source，不消费旧 row 内存、selected 字段或分配返回；相邻 span 用于核身份，不用于猜长度。

| 用途 | 已验原始 span / SHA-256 | 本次限定 |
| --- | --- | --- |
| 10068 旧 Wind7 控制 | `[5550,5699)` / `ef04cbb931f9ffaa45b02e07c34c7a5fa35bc0aab0a5032fba75095195d7bd5e` | 复用既有 mask，原 word 位置5690 |
| 11364 独立 Electric4 控制 | `[69683,69828)` / `2c73ff8cc93042130fbdb3527150931559925380c192727ea9c5037c9af5d255` | 来自固定 mapping 的 Skill rows[605]；不在旧号371盘点或121 Depot集合，不计新增 cohort 完成 |
| 10019 一个新 energy 字段 | `[1481,1594)` / `5ae4affad876b5321d123ba19b2fe9b73e5238a23ee10c1f1cb03df1843349e8` | 已验 Depot201 的 energySkill；复用 mask，原 word 位置1586；结果由 raw 公式产生，不预设候选 Ice5 |

11364 的独立非零控制来自原具名 `Miscs.GetAvatarElementType`：10000136→13601→11364，实际返回 Electric4；源窗 `153217960..153217abb` SHA-256 `202de0d982b8af2533dea6834cbe6238ef1fdea60d7ce9ec604149f8615f983a`，最后直接读 Skill `+54`。控制与表/span同在固定 mapping 报告 SHA-256 `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`。这是已验具名消费者的只读复用，不是新执行整表或 A 返回。

机器规格为 `debug/d02-skill-energy-search-20261116/local-analysis/element-suffix-source-spec.private.json`，34086B，SHA-256 `1a4f63d76ab810cbf8cda12954131b7ce471a3491b6c1aec410ae98701636fed`；含9个来源 pin、113条后缀指令、17个准确锚点、三个 source 和完整验收/停止条件。下一项新语义读取最多20B：11364首8B mask及三行各4B元素；完整文件与选中 span 的 hash读取另账。原始文件固定 SHA-256 `a5d2fdc6bbc32ea408c51e90c04777b771e9ab4fec4c35fc74186b6dc3c236fe`，不改来源。

验收须同时满足来源/hash/span、两种独立 bit 提取及算术检查、精确 end 记账、Wind7/Electric4 两个控制及 target/Java 元素名字与值一致。首个冲突保留 partial 后停止，不扫邻字节、换公式或扩大预算。原 pilot 实际输出仅 tableKey、source及元素字段；未新解 raw id。后继 canonical id 可复用下述已验逻辑身份链，不能额外要求 raw-id 解码。三项 energy cost/special 字段、完整 Skill、实际采用、Java正向绑定及旧 Player 恢复仍待验。

本合同只读既有来源，未读新 payload、取新窗口或执行 native/ctor/reader/TLS/模型/JVM/数据库/客户端。A/B、preview、common return及准确 tail 冻结保持。后继唯一写 `debug/d02-skill-energy-search-20261116/element-host-pilot/`；不改运行代码、资源或配置，D-02客户端六项不由本合同晋级。

## logicalID 与 tableKey 的既验绑定

`R3-SKILL-LOGICAL-ID-SOURCE-BINDING-20261116` 的依赖纠正已由主线接受：`logicalID=tableKey` 有现成完整来源，`idRawDecoded=false` 不能被改成必须重新解码 raw id 的阻塞。

原 whole 的 `14eb02e90` 从同一 RBX row 的 `+68` 取编码字段，`14eb02e9a` 经已验原 UInt32 getter `14aef9db0` 得到逻辑值；`14eb02ea4` 保存至 EBP，`14eb02ec8` 以 EDX=EBP 传字典 key，`14eb02eca` 同时以 R8=RBX 传入该 row，`14eb02ed0` 原生插入。原报告从同一字典 entry 读取 key 与 row，span 又按该 row 关联入口/返回 cursor。`440582/+68` 的 Skill id 职责和原 getter 已验，因此这是同 row 的逻辑 id 身份，不是把物理 encoded word、邻行或候选 ID 当作 id。

这条链复用上文 mapping 及 `local/migration72/analysis/avatar-skill-depot-resource-native.txt`（SHA-256 `a1a3f16f2f80848f0cc0d001c96a1a260d42b0e88dcd94b04a5b87a425bd7eee`）与原 span driver 的限定身份数据；没有新读或取得 A 返回/构造、row 对象或冻结产物。后继可输出 canonical `id=tableKey` 并记录 `logicalIDSource`，与独立通过的元素字段形成 **id/element 片段**。它仍不构成完整 Skill、三项能量字段或实际恢复通过。

更正记录 `debug/d02-skill-energy-search-20261116/local-analysis/logical-id-binding.private.json`，SHA-256 `361f753238974f76e6a73c89548fa411babf284fca7be9be27dcd9b7f6176cee`。它明确撤回旧 element 机器规格中“未新解 raw id 就不能形成 canonical id/element 片段”的额外先决条件，原规格原件保留。为 raw-id 前缀语法准备的 reader 单窗仅写了 source-only 提案，主线已取消，未执行；不作为此出口或 D-02 的必要依赖。
