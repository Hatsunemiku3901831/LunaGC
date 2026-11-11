# F-03d3s2 / GADGET-BIN-METADATA-PREFIX

2026-11-11：在已验 FIRST 最终批次基础上，仅取得压缩 blocks 1..26，复用已验 block0 输出，形成真实 serialized prefix 并读取完整 metadata。没有执行 block27 及后续块、完整 445 MB payload、目标 native codec、完整 archive reader 或客户端。本项到 class1210 对象范围计划即停，没有解析 asset index，也未将 entry 名称与任何对象先行绑定。

冻结请求和 26 个实际范围、FIRST 最终六原件、正式 header、旧 source 及 Reader、profile、五 stock、已有 snapshot 和原物理文件在入口核验，重复依赖去重；固定实际使用的 Unicorn Python 模块与 DLL，并验证加载路径。物理文件 139134667 字节仅顺序流式计算 SHA-256/MD5 用于身份核对，保留真实 `[0,1539016)` 前缀，前 `[0,74727)` 与 FIRST header 和 block0 原件一致。新增 raw 仅 `[74727,1539016)`，与固定 header 给出的 26 个逐块区间一致。

单个 BlobProbe specialization 依次执行 26 次已验原 prefix 路径，并以独立 LZ4 逐块获得各 131072 字节；不调用 `PayloadProbe.__init__`、tag3 注册、codec9 构造或新 model/callee。每块记录原始/转换/输出 SHA、调用和写入轨迹；输入两侧 guard、128 字节后尾部、archive、256 字节 table 和 13725 字节 header storage 均按合同保护。block0 的原输出只读复用，没有重新执行。27 块真实输出原序拼接为 3,538,944 字节，SHA-256 `4c99c4ec09a102d177a27cfe08b432c93b36af642d2e7e4b0644abfc346d5393`；真实 metadata `[20,3523165)` 为 3,523,145 字节，SHA-256 `79cedf3b5b7cb308c36a2e0a250fd7627a72cc8bd00b33739b87d99102aff0d3`。

## 真实 metadata 目录

入口区分可用 prefix 3,538,944 字节与文件声明长度 445,421,180 字节；对象 extent 按后者验证，对象内容只有确在前者内才标记可用。metadata 的 engine 精确为 `2017.4.30f1\n2`，其中 `\n` 表示实际换行；platform 为 **-2**，typeTree 0。固定旧 R3 三份已验 catalog 与此 engine、platform 和三种 typeHash 一致。新限定 parser 不修改旧 FIRST 的 platform 19 gate，不伪造完整文件长度或调用旧 full-file parser。

类型为 class142、1210、1208；真实对象总数 **176152**，分别为 1、1、176150。对象表记录 `[116,3523156)`，保留全部 signed 64 位 pathID，校验类型、完整声明文件 extent、重复和重叠。随后从实际已解出的 metadata 尾部读取 scripts count 0、externals count 0、空 userInformation，精确结束于 3523165；这些尾部结果不是预设值。全部对象记录、类型和字段位置保留在私有目录报告。

唯一 class1210 对象 ordinal 1、pathID 2，typeHash `375926f2689845ddfc700437e60d9774`；声明的真实输出范围 `[5637072,11273880)`、长度 5,636,808，当前 prefix 内不可用。覆盖它需 block 43..86 共 44 块，固定 header 给出的连续物理范围 `[2238253,4560114)`；每块准确 raw 与输出半开区间见私有 index plan。它们均未读取或解码。后继必须先按该实际 extent 取得索引字节，再在已验字段语法与真实对象长度内处理实际 count；现有旧 asset_index 的 100000 条上限不能直接假定覆盖这个新对象。索引 pathID 的 U64 与 metadata signed I64 需按完整 64 位位型关联，不压低 32 位。

## 实际验证

- 最终 `fresh4`、`fresh5` 的 110 份对应文件逐字节一致，含每个新块 raw、转换、输出和 proof，以及 pre、真实 prefix、metadata、目录、index plan 和结果。此前 fresh1–fresh3 为最终 Unicorn 直接依赖固定前的开发批次，不作为最终验收。
- `audit-final.private.json` 纯原件审计通过：重核物理源及 26 个 raw 范围，重算 LZ4、concat、metadata、完整目录和计划，不再次调用 native。
- `python -m unittest tools.migration72.test_scene_mplevel_actual_gadget_bin_metadata -v`：5 项通过，覆盖固定区间与 index 0/27 拒绝、range 漂移、metadata 跨界、真实 -2 与旧 19 区分、负/高 64 位 ID、类型/extent/重复/重叠/EOF、候选块边界和 source pin 漂移。均为人工测试数据或固定范围说明，不复制客户端原 bytes 作夹具。
- 主线已实际运行第三入口 `python tools/migration72/scene_mplevel_actual_gadget_bin_metadata.py --output main-third`，110份原件同fresh4，5项测试独立通过；`--audit fresh4 main-third --output audit-third.private.json`通过，15903B/SHA `51d111814d055f19e2017d0e16e223be6b638a0383a335e24d93486dabdae4a0`。所有原块与逐块证据、完整目录和精确计划保留于忽略目录 `local/migration72/d02-resume-20261111b/scene-gadget-bin-metadata-prefix/`。

`block0Reexecuted=false`、`targetNativeCodecExecuted=false`、`fullPayloadDecoded=false`、`reader42005Executed=false`、`MPLevelRecovered=false`、`naturalLoaderReady=false`、`SceneReady=false`、`D02Ready=false`。本项没有客户端验收、运行代码或部署修改。
