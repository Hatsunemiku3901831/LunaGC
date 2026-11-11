# F-03d3s2 / GADGET-BIN-FIRST-BLOCK

2026-11-11：依据已验 ConfigMeta 字段、唯一 bin 索引及完整物理 header，只恢复 gadget 资源的第 0 压缩块并检查真实输出首部。目标真实 content 声明 445,421,180 字节；本项只得到前 131,072 字节，没有构造、稀疏模拟或零填充完整 content，也没有读取其他块用于转换或解码。

固定物理源完整顺序哈希为 139,134,667 字节、SHA-256 `cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768`、MD5 `edc854321f0e9d8c64fd6e80426fe657`；此全文件读取仅用于身份哈希。本项保存实际物理前缀 `[0,74727)` 中准确块 `[13737,74727)`，其前 13737 字节与已验原 header 原件逐字节相等。原 block 60990 字节，SHA-256 `05c4a18c84045432595cd428a99d6eff073cf589a7d50318854371db1f1c2722`。

复用已有 BlobProbe 环境和仅针对 index 0 的原 81 字节 prefix 函数；未调用 `PayloadProbe.__init__`、tag3 注册、codec9 构造/状态或目标 native codec，未添加 model。真实 header storage 的受保护范围为 13725 字节，archive、table、输入 guard 和 128 字节后尾部均未改变，受保护区域写入 0；返回 AL 1，0 modeledCalls / memoryModels。实际前 128 字节全部变化，转换后块 SHA-256 `e38c6305ddbf7985d4434a97f4ea20abc3198149a06dca77a4574b6d38825b43`。已有独立 LZ4 block 解码完整消费此固定输入并得到恰好 131072 字节，输出 SHA-256 `05eee2cc1040662019732b36c17bd4c3764ffa06ae4c1edd5ee1129580ef52c3`。

## 真实首部与下一范围

实际前 20 字节大端头给出 metadataSize 3,523,145、fileSize 445,421,180、version 17、dataOffset 3,523,168，endian/reserved 均为零。metadataEnd 为 3,523,165，超过当前可用 prefix 131,072 字节，因此状态是 `metadata-prefix-incomplete`；未解析 metadata 目录、引擎字符串、asset index、entry 或目标对象，也未以旧完整文件 parser 或伪造全长通过门禁。

要覆盖完整 metadata，连续输出 prefix 需要 block 0..26 共 27 块；block 0 已取得，其余 **1..26 仅记录计划，未读取或解码**。它们的物理压缩范围连贯覆盖 `[74727,1539016)`，各块实际 raw 和输出半开区间保存在私有 `directory.private.json`；第 26 块输出结束为 3,538,944，超过 metadataEnd 的部分属于块粒度，不能误作已读目录或目标数据。下一包先由主线固定这些最小目录块的范围与预算，再由真实目录定位 class1210；在其完整可用且类型 hash 符合已验 schema 后才能查询 entry `44935541`，之后再计划对应 class1208，不能先猜目标 extent。

## 复核

- 固定请求、主线第三份 header 四原件、fields 第三结果、五 stock、原工具/快照及直接复用的旧 Reader 均核 pin。前 13737 字节先与已验 header 一致；按真实物理长度重新使用未改旧 `parse_header`，不得把短 prefix 冒充完整文件。
- 最终 `fresh4`、`fresh5` 六份对应产物逐字节一致；纯原件 `audit-final.private.json` 重核物理哈希和原块、前缀尾部、独立 LZ4、实际 20 字节头与计划，当前 native 调用为 0。`fresh1` 为加入审计前开发记录，`fresh2`/`fresh3` 为直接 Reader pin 前的批次，不作为最终双 fresh。
- `python -m unittest tools.migration72.test_scene_mplevel_actual_gadget_bin_first -v`：7 项通过，覆盖 index 1 拒绝、短头、部分 metadata 只计划、边界和 ceil、完整 64 位 PPtr、不可用对象、缺失/多重 entry、source/header 漂移及 LZ4 错误输入。夹具为人工构造。
- 主线已实际运行第三入口 `python tools/migration72/scene_mplevel_actual_gadget_bin_first.py --output main-third-final`，六件与fresh4相同，7项测试独立通过；`--audit fresh4 main-third-final --output audit-third-final.private.json`通过，945B/SHA `88990269896264b0a1d53378890c2c838737a75f49ee4c4c814e2db6ed917358`。补Reader pin前的旧main-third保留，不作最终验收；原件和计划留在忽略目录 `local/migration72/d02-resume-20261111b/scene-gadget-bin-first/`。

本项 `nativePrefixExecuted=true`、`independentLZ4Decoded=true`；目标 native codec、完整 payload、reader42005、MPLevel、自然 loader、Scene 与 D-02 均未执行或未恢复。无客户端验收、运行代码或部署修改。
