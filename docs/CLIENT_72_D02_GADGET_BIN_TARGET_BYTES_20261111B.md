# F-03d3s2 / GADGET-BIN-TARGET-BYTES

2026-11-11：基于主线已验索引第三批与唯一目标关联，仅转换和独立解码 blocks 765..771，取得真实 class1208 对象及其 byte array。没有执行其他压缩块、目标 native codec、完整 archive reader、reader42005 或客户端；本项不解析 MPLevel 或推断 reader 起点。

入口一次去重核对固定计划/请求、索引第三批 pre/result/target/audit 及其继承的 source/profile/五 stock/实际直接依赖，包括原索引对象及完整索引报告，再从真实索引记录和已验 metadata 重新核对精确名称、local fileID、完整 64 位 pathID、class/typeHash 和七条范围。物理文件全长 139134667 字节顺序流式 SHA-256/MD5 核身份；为复用按绝对偏移的已验 setup，内存使用真实 `[0,26119089)` 前缀。其他块只因该连续物理前缀存在，没有转换、解码或当作可用输出。

七块压缩范围为 `[25873585,26119089)`，共 245504 字节；单个已验 probe 逐块执行原 prefix 和独立 LZ4，每块分别保存原始、转换、输出和 proof。无新 model/callee、无 target native codec；各块 header storage 13725 字节、archive/table、guard 和 128 字节后尾部的保护合同均通过。真实窗口 `[100270080,101187584)` 为 917504 字节，SHA-256 `2ee6c7ef8f6c91d88fb228ddc803d424d1be36a777e6adbc44388b1d7d36b398`，未对其他输出区间补零。

索引唯一 entry `44935541` 的 fileID 0、pathID 14783、fileSize 782517，按完整 64 位位型对应 metadata 中的 class1208/pathID 14783 对象 `[100385712,101168236)`。从真实窗口裁出对象 782524 字节，SHA-256 `91f24ac25a4a50a5d4dec7152e9c3a86b2290a14100ea102571c413b2f88f9a8`。真实前 4 字节为 little-endian count 782517，与索引 fileSize 相等；紧随的真实 payload 为 782517 字节，SHA-256 `25bf9c7e24ca9d708b39deafe361c0a339daf9cbb5aadc6b61a2324e57cd891e`。对象剩余 3 字节真实值为 `000000`，已另存，SHA-256 `709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c`；本项不推定它们的语义，也未将其计入 payload。

## 验证与下一项

- 最终 `fresh2`、`fresh3` 的 34 份对应文件逐字节一致；`audit.private.json` 纯原件审计重核物理源、七个 raw 范围、独立 LZ4、窗口、对象、真实 count、索引 fileSize、payload 和全部尾字节，当前 native 调用为 0。`fresh1` 为加入显式 flags 与审计前的开发记录，不作为最终双 fresh。
- `python -m unittest tools.migration72.test_scene_mplevel_actual_gadget_bin_target -v`：4 项通过，覆盖精确七个 index/range、物理源和 header 漂移、窗口不足、截断 u32、count 越界或与 fileSize 不符、非零尾部保留以及 payload 与对象前 4 字节分离；夹具为人工构造。
- 主线已实际执行第三次：`python tools/migration72/scene_mplevel_actual_gadget_bin_target.py --output main-third`，34 份对应文件与最终双 fresh 逐字节相同；随后 `--audit fresh2 main-third --output audit-third.private.json` 通过，审计 4977 B，SHA-256 `603c2892ec83bebcc81755f0f06a1ab045965afdb517fc6f191376e8b464f1d4`。主线重跑 4 项测试通过。真实 payload 与逐块原件留在忽略目录 `local/migration72/d02-resume-20261111b/scene-gadget-bin-target/`。

该 payload 已完成限定 byte 来源恢复；不能将其起点自动称为 reader42005 的自然起点。后续由主线根据独立证据研究 reader 和分组。`targetNativeCodecExecuted=false`、`fullPayloadDecoded=false`、`reader42005Executed=false`、`reader42005StartKnown=false`、`MPLevelRecovered=false`、`naturalLoaderReady=false`、`SceneReady=false`、`D02Ready=false`；本项无客户端验收、运行代码或部署修改。
