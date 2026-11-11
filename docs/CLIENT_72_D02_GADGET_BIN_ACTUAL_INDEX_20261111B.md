# F-03d3s2 / GADGET-BIN-ACTUAL-INDEX

2026-11-11：在已验 metadata 第三份目录和精确 class1210 计划基础上，仅转换并独立解码 blocks 43..86，读取真实完整索引并关联固定 entry。本项不执行其他压缩块、目标 native codec、完整 archive reader、reader42005 或客户端，也没有解析 MPLevel。

入口核对固定计划/请求、metadata 第三批完整目录及审计、源工具/直接依赖、五 stock、snapshot 和物理源。为复用已验按绝对偏移的 setup，只保留真实物理 `[0,4560114)` 前缀；其中 27..42 范围只随真实前缀处于内存，没有转换、解码或当作可用输出。物理文件全长 139134667 字节只作顺序 SHA-256/MD5 身份核验。仅对 44 个批准区间执行原 prefix 和独立 LZ4：压缩范围连续 `[2238253,4560114)`；每块有原始、转换、输出及 proof，保护实际 13725 字节 header storage、archive、table、双 guard 与 128 字节后尾部，0 model、0 protected write。真实输出窗口 `[5636096,11403264)`，5,767,168 字节，SHA-256 `a7afef0f05953a615e2773b88b5bc672b9c1d2abd90803318e09a69afefab5d2`；没有填补输出 0..42 或构造伪完整文件。

## 索引与唯一目标

从窗口准确截出 metadata 已验 class1210/pathID 2 对象 `[5637072,11273880)`，5,636,808 字节，SHA-256 `584c840f9687a9512e1d5ab6f86c7703ab5857e6fa5257ca95f153ed4492209d`。新限定 parser 按既有字段 schema 读取 aligned UTF-8 名称、u32 count 和每条 name/fileID/u64 pathID/u64 fileSize；count 由真实对象剩余字节约束，完整实际 **176150** 条记录后恰到 EOF。旧 `asset_index` 的 100000 条样本上限及其文件未修改。私有报告保留全部记录的原序和字节范围。

精确字符串 `44935541` 唯一，record ordinal 47407、索引对象相对区间 `[1517032,1517064)`，record SHA-256 `e1244642609d4c6f2ea85fb3a1c524077b9c3b8aab20bb11f008e9f143fe3370`；fileID 0、U64 pathID 14783、fileSize 782517。以完整 64 位位型关联 metadata 唯一 class1208 对象 ordinal 14782、signed pathID 14783、typeHash `0451de58691ec5b6e08bba3d9b5b85fa`。没有按低 32 位或相近名称选择。

目标对象声明真实输出范围 `[100385712,101168236)`，782524 字节；当前窗口不含对象或它的 4 字节头，因此索引 fileSize 与实际 byte-array count **尚未交叉核验**，不假设对象剩余字节语义。其最小后续块为 765..771，已验 header 映射的连续物理范围 `[25873585,26119089)`；七条逐块 raw/输出半开范围保留于私有目标计划，均未读取或解码。后继若按授权取得目标真实字节，应先核 4 字节 count 与索引 fileSize，再保存实际 payload 和剩余原字节；不能提前构造、补零或解析业务字段。

## 验证与边界

- 最终 `fresh4`、`fresh5` 共 182 份对应文件逐字节一致。`audit-final.private.json` 纯原件审计重新核物理源、44 个原始块和 LZ4 输出、真实窗口、索引、唯一目标及块计划，当前 native 调用为 0。`fresh1` 为开发记录，`fresh2`/`fresh3` 为审计补充 END 状态前的批次，不当作最终结果。
- `python -m unittest tools.migration72.test_scene_mplevel_actual_gadget_bin_index -v`：6 项通过，人工生成 176150 条级别索引并覆盖真实 count/extent、字符串越界、截断/尾余量、缺失/重复 entry、非零 fileID、负/高 64 位和低 32 位碰撞、错误 class/hash/fileSize、不可用对象、范围及 source 漂移。
- 主线已实际执行第三次：`python tools/migration72/scene_mplevel_actual_gadget_bin_index.py --output main-third`，182 份对应文件与最终双 fresh 逐字节相同；随后 `--audit fresh4 main-third --output audit-third.private.json` 通过，审计 26248 B，SHA-256 `cad4dc075ea8c90e5ea441fbcb04fbbeb4b0a2ad98cd12db744afc3b827bd20b`。主线重跑 6 项测试通过。逐块原件、完整索引记录及目标计划留在忽略目录 `local/migration72/d02-resume-20261111b/scene-gadget-bin-index/`。

`targetNativeCodecExecuted=false`、`fullPayloadDecoded=false`、`reader42005Executed=false`、`MPLevelRecovered=false`、`naturalLoaderReady=false`、`SceneReady=false`、`D02Ready=false`。本项无客户端验收、运行代码或部署修改。
