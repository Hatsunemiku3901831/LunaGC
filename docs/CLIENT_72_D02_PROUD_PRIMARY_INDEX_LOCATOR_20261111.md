# E-01/R3-PROUD-PRIMARY-INDEX-LOCATOR（2026-11-11）

## 有界计划和输入

只定位 400190 壳所持 PrimaryIndex literal 对应的完整原 payload，不解析 index、row 或 IndexGroupId。复用 root 已接受的 289B prepare 合同和旧纯 reference/schema；先逐项核全部固定来源及旧工具 pins、重算旧九个 native 报告目标的 hash/pack/name，然后只扫描两个固定 assets section。只有唯一 record、全部 variants 证明 selector 无关、唯一 location 与冻结 serialized 的 tuple 完全一致，才读取该 serialized 并按唯一 local class 1208 entry 提取。不执行目标 native hash、parser、probe 或目标 CPU，不新取代码窗、literal、AOT、PE、pdata、xdata，不访问客户端、网络、进程、构建或数据库。来源漂移、歧义或预算失败保留私有诊断并停止；不猜名称、换资源包或扫邻对象。

规格 `c-proud-prepare-contract/next-primary-index-locator-spec.zh.md` SHA-256 `0409a7e3a5a83dd7ace77f89a299bac8943f1997f2b081eafdf7591d980a2e5b`。已接受的两 fresh 和 root 第三 prepare 报告各 36303B，SHA-256 `9eb26c03c1ceb6f9cd9a000e7b9de688a2c9c8f2e2cf893902f8e119fbff5e27`；固定 185 项来源清单 37159B，SHA-256 `0e33c2b26fab21d557dd596f97aa918caa4c94e606331fdf24d745b34439003b`。逐项重核 185 项既存源共 302489408B，另核旧工具 pins、两 locator 报告、旧定位结果、literal 双报告/第三和规格；这些完整哈希门禁单独记账。只读原落盘 code/pdata 等用于既有 pins 哈希，不新增原窗。

原 58B literal 的 SHA-256 `058b4298fbe652d492fcc60b5c10a0214bb87e4ada40a4247a3f04c6a79f344a`；prepare 已验原 `157ffc664` 读壳+20，`157ffc680` 调既验 `14d6e1570`。该 loader 的精确 usage 绑定 `UnityEngine.MiHoYoBinData`/8026，固定 type-map 唯一给出 `.MiHoYoBinData`；未尝试其他后缀。旧九个 native 报告目标分别重算 host hash、pack、bundle 和 name 并比对，通过后才计算新目标。新目标 host key 为 `afd9ab7e6f3f4c9a`，**targetHashNativeExecuted=false**。

## 实际定位

两个 assets section 分别完整扫描 `[19661,12973844)` / 925261 条、`[4,3607108)` / 257650 条；合计 16561287B / 1182911 条，原 section SHA、end、count 全部匹配。唯一 record 在 31049741 原 bytes `[2695792,2695806)`，14B `9a4c3f6f7eabd9af0100b2000000`，SHA-256 `8578d7e9ff9a83f323389a4e0cf2e821108fa5b70f7673b4b71a558ac722964b`，完整 variants 为 `[(0,178)]`，在既有 selector 合同下独立于 selector。host pack 得 bundle 178、entry `7e6f3f4c`。

对应 sidecar 完整扫描 12864B / 536 条，唯一位置 `[4248,4272)` 原 24B `71b28501b200000000000000000000000000000007000000`，SHA-256 `f3a5f66979bcee59d06c7a716f52ec341b4b54feae5452d9a49d0847a5ed65a6`；tuple `(25539185,178,0,0,0,7)` 精确等于旧已验来源。随后才读取 71555980B serialized，SHA-256 `0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7`。本次数据磁盘读取（两完整 index、对应 sidecar、serialized）共 123804262B，小于 158416894B 上限；完整哈希门禁和既有报告另计。

唯一 entry 位于 index pathID 2，原 record `[103392,103424)`，32B SHA-256 `0e64fdd441f09ceea1c8f8bd27602e41409368bce2ba3f9838dba285fa61305d`；fileID 0，pathID 1585，fileSize 31858，与本地唯一 class1208 对象 `[48955768,48987632)` 及其长度前缀一致。完整 payload 原 span `[48955772,48987630)`，**31858B**，SHA-256 `f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05`。未解码或解释该 payload 内容。

## 复现与验证

```powershell
python -B -m unittest tools.test_client72_d02_proud_primary_index_locator -v
python -B tools/client72_d02_proud_primary_index_locator.py --output <新的私有basename>
python -B tools/client72_d02_proud_primary_index_locator.py --audit fresh1 fresh2 <新的审计basename>
```

5 项测试通过：完整 section/全部 variants 与越界、source drift、零/多 record 与 selector、零/多 location 与 extent、serialized entry local/class/长度，以及拒绝覆盖已有目录。两次独立 fresh 各约 1.49 秒，四件逐 byte 一致，离线 audit 通过：

| 产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| index diagnostic | 49360 | `71f93726b0a540464d6ab5263756ec6e3ef20467694691b4845e418a2b05806f` |
| location diagnostic | 49986 | `247efd98d8e06a9197874934ca42d7ebb0bdf8e3cbc70d8866d83193e2b9af4a` |
| payload | 31858 | `f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05` |
| result | 52202 | `1a2d897cf40fd1b922ecf8e46e1c5c040ad06f2d6483360b6bab2eb2f63e3b02` |

工具 SHA-256 `5c4791c358eba82380816926c51b06f322f094017cf8046fd158f1ba34ccf0c2`，测试 SHA-256 `5a0d86100809d248573e9bdef4f15bfdc30c9f2dc060caa65ff31df502f06b78`。原件、报告和 audit 在忽略目录 `local/migration72/d02-resume-20261110b/c-proud-primary-index-locator/`；第三独立复验和共享 Project/Harness/Git 由 root 负责。本卡仅工具、测试、专题和私有证据，无需部署；客户端验收新增范围为零。

成功出口仅为基于固定来源和 host reference 定位 PrimaryIndex 完整原 payload。自然激活和 index 填充、157ffc780 成功、壳+18、index 格式、两个 U32、实际 row span/backend/factory、group/level/threshold、Java 公式、resourceReady、Avatar 和 D-02 均未验，标志保持 false。下一步应由 root 独立复验此机械结果，并另行制定所需语义或原始执行输入的有界合同，不据 payload 身份推断其内容。
