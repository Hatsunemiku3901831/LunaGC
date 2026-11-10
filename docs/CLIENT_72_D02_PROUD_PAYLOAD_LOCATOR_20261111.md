# E-01/R3-PROUD-PAYLOAD-LOCATOR：有界执行计划

- 目标：仅为固定 46B 路径定位完整原 `m_bytes`，供独立获批的 parser 工作包使用，不解释字段。
- 已有：两 fresh 原 literal 报告和旧九目标 host/native 证据；旧 locator、两 assets section、位置 sidecar 和一个 serialized 由完整 SHA 固定。
- 缺口：新路径的真实全部命中、selector、block/serialized 来源与实际 m_bytes 尚未核定。新路径原 hash 未执行；两个 U32、parser 和 revision 均未知。
- 依赖与来源：先固定双 literal 报告与 decoded UTF-8、locator 脚本/报告、index 脚本/报告、两原 m_bytes/sidecar；对旧九目标做 host 反核。仅复用既有 host `reference_hash`、`reference_pack`、`find_matches`、`serialized_objects`。
- 范围：只扫描两个固定 assets section 中一个准确 key；保留所有 record、variants、source span 和 selector。只在唯一真实 selection 能绑定已冻结 serialized 后读取该材料，按 schema/name/fileID/class/长度门禁提取，不预设 bundle、block 或使用候选 JSON。
- 产物与验收：两个 fresh 新目录分别保存 payload 与报告，逐字节比较；报告记录全来源 SHA、literal、原 record/sidecar/serialized spans、拒绝覆盖和未知状态。门禁测试覆盖正常、漂移、多记录、覆盖、越界。
- 停止和恢复：无/多 record、selector 不明、来源漂移、位置不唯一或不同 bundle/serialized 不匹配时保存诊断停止，交主线准确材料缺口，不扩大文件集。工作包 B 的原 parser、目标 CPU、客户端、资源安装、网络、服务、Git、生产和共享索引均不操作。

## 实际结果

仅对已闭 literal `Data/_ExcelBinOutput/ProudSkillExcelConfigData` 做 host reference：原 46B UTF-8 SHA-256 `dbcea51e0cf6e8d6809b225fe28a34b77c69838ea8d182d7fd77465a5e684fea`；两冻结 literal 报告各 4477B、SHA-256 `9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9`，包含原 `14d7ad962` load 与 `14d7ad98b` reader call 来源。旧 locator 报告固定九目标的 host hash/pack/entry name 均重新核对一致；本次不重新执行旧或新 native hash。实际类型 map 给出的后缀为 `.MiHoYoBinData`，新路径 host key `856341c768ed2e38`。这不是该新路径已执行目标 hash 的证明。

两个固定 assets section 完整扫描，section 原 end、长度、SHA 与 count 均重核：

| index | section `[start,end)` | count | SHA-256 |
| --- | --- | ---: | --- |
| 31049740 | `[19661,12973844)` | 925261 | `b4164b4750852c03e1ce32b8768e0fc06ce8b82b6e288bcf85ba83830115d89b` |
| 31049741 | `[4,3607108)` | 257650 | `ade9b903cdc39d5719bf6fc6ea22006af40edbcf60db72b572759fc0b7ba4c3b` |

唯一命中在 31049741 原 m_bytes `[2695750,2695764)`，原 14B `382eed68c74163850100b2000000`，SHA-256 `1684d1055eedd5fea73653180233b2bea5fe2c0cb74ade669eb12f6ee56d4074`；完整 variants 为 `[(0,178)]`，单个 tag0 非零值在已有 selector 证明范围内不依赖 selector。host packing 得 bundle `178`、entry `c768ed2e`。真实 location sidecar `[4248,4272)` 原 24B `71b28501b200000000000000000000000000000007000000`，SHA-256 `f3a5f66979bcee59d06c7a716f52ec341b4b54feae5452d9a49d0847a5ed65a6`，实际 tuple `(25539185,178,0,0,0,7)` 与已证明旧 bundle/location 一致，之后才读取冻结 serialized；未预设此结果。

serialized 文件 71555980B、SHA-256 `0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7`。复用 v17 bounded schema（class 142/1208/1210），唯一 index entry `c768ed2e` 在 index pathID 2、serialized `[122560,122592)`，record SHA-256 `376dabd03a3b13b720035c2ef2eb9ec8f5c8b5c395d737bc1fda558a1fc683ff`；其 fileID=0、pathID=1582、fileSize=2132778，对应唯一 class1208 对象 `[46722360,48855144)`；m_bytes 原 span `[46722364,48855142)`，完整 **2132778B**，SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`。该定位依赖 host hash/pack 和旧来源证明，尚未由新路径目标 native hash 或目标 serialized consumer 独立执行确认。

## 固定来源与复现

实际两 fresh 为 `final3`、`final4`，以下四文件各自逐 byte 一致。早期 `first`、`final1`、`final2` 保留；最终 CLI 加入 literal 生产者 pin 和五输入来源的完整报告后重跑，前者不作最终基线。

| 产物 | bytes | SHA-256 |
| --- | ---: | --- |
| index diagnostic | 9191 | `b79835288f1f8f969162e9c74a80890e75bbe66873a2be74db846c898af8a791` |
| location diagnostic | 9717 | `f4d41c7961b495cfefeb6010372d29048d12aa4752586e42c89d21a675bacacc` |
| 原 payload | 2132778 | `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835` |
| 结果报告 | 11593 | `e2513467fad5fb61a90e6aeec27c1ccf030b5b52fa75cb9a6874749190424839` |

两 fresh 分别核固定 locator 报告 `fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda`、index 报告 `e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9`、两 index 原 m_bytes `216c791b536ce8e4f1f0128c200a791015768491bf23f2a8a9bc618720b6eb7d` / `782aa1208ab3070a567f8907e7f15b8f3ad046b0d661cdd4774ef9af8f81dffc` 及两 sidecar `c013043753bdb2679bb9a59d4aab4d6db62d87febd37bb27e69adb00ee8093f2` / `29b5f4215a2c5caf2d1b57cf967f0d8e1bdb96f00af57d229e63be2a27c4ebb0`。旧 locator 源 SHA-256 `413d2501323fde768076d59222a39af30ebd4857d89759478d060b6c22834456`，index 源 `3fd618d4c990f3358eff772b4af30553fb332c2e1ed20423a3f5783e6a59946c`；完整所有源、长度、literal 生产者 caller/来源和原 spans 记录在私有报告。

新工具 14483B SHA-256 `d2983e92afc250ad46a6219d73c1d513420f9611dcf77ad72865dabf620014d7`，测试 2873B SHA-256 `fc47042a67d48916fae39b19d9e21df2aa97ffc3e6b56b6ddddf7c859ea2969d`。`python -m unittest tools.test_client72_d02_proud_payload_locator -v` 5 项通过，覆盖正常提取、来源漂移、无/多记录和 selector 歧义、位置多记录/错误边界、payload 越界及拒绝覆盖。复现：`python tools/client72_d02_proud_payload_locator.py --output <新私有basename>`；第三独立复验、Project/Harness 和 Git 由主线处理。

本次 `targetHashNativeExecuted=false`、`nativeSerializedLookupExecuted=false`、`parserExecuted=false`、`revisionProven=false`、`resourceReady=false`。未使用目标 CPU、客户端、目标 DLL、live、数据库、网络或部署；未解释 payload 或推断行数、字段与别名。下一前置为另行批准 parser 的单 entry 静态合同，以及原行 key→两个 U32 的有来源定位；只有后者成立才能从本完整 payload 确定实际行 span。客户端验收新增范围为 0，D-02 仍未完成。

主线第三CLI与本卡两fresh完整输出逐byte一致；三卡合计17项测试通过。独立汇合review SHA `fd9f52f3f5bf4630158f7015573fb9326a75f72370ba64619cf0e31e5f295937`，私有 `c-proud-parser-contract/main-integration-review.private.json`。本卡仅按上述静态/提取范围验收，原parser与实际row/mode/factory、resourceReady及D-02未晋级。
