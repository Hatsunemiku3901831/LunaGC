# D-02 Scene reader factory 单窗机械验收（2026-11-11）

任务：`F-03d3s2/SCENE-READER-FACTORY-ONE-WINDOW`。本项只为已验同步 caller 的准确 reader factory 取得一个完整原函数及引用清单；两个传入的 int32 字面零仍不命名为 offset、count 或 mode。

## 输入与 body 前置门禁

固定请求的大小和 SHA-256、六项来源、六项工具、metadata、signatures 均匹配。metadata 使用禁止 globals 的 unpickler，注册地址唯一对应普通静态 `60257.m500134`，完整参数类型为 `uint64, int32, int32`。固定 profile 与五个 stock 的完整大小和 SHA-256 匹配，PE base 匹配。

既有 817 B caller 来源及已验 instruction boundaries 确认后，当前 PE 只额外读取原 13 B setup/E8，字节和 signed rel32 目标均匹配 `0x14d6f9210`。原 CodeRegistration cell 的实际表为 `0x1428a54d0`，普通 slot `0x142c76200` 原 QWORD 指向同一 entry。完整 exception directory 的 14,344,080 B 单独核对，entry 的 exact 与 containing 唯一且为同一原记录。boundary、原 12 B pdata 和 pre-body 诊断均在 body 读取前保存；可执行、file-backed 和 8192 B 上限随后通过。

## 实际结果

- 原 pdata 唯一范围 `[0x14d6f9210, 0x14d6f92fe)` 为 238 B；两次 fresh 的九份原件逐字节一致，body SHA-256 `f3c65163277d38595ff0b48bac94e1a6a047c097f71b28f518c545b028a9c905`。
- 连续解码覆盖全部原字节，共 68 条指令，低于 2048 条上限；报告保存 18 条 direct/indirect call、branch 和 RIP 引用，以及 1 个 return、1 个外部 tail 边界。列出引用不授权追读或执行目标。
- 工具双份审计及独立纯原件审计通过；后者重新核对固定来源、profile、metadata/signature、caller、AOT、pdata、字节重建和独立解码，未读当前 PE。原件保留在忽略目录 `local/migration72/d02-resume-20261111b/scene-reader-factory-window/`。
- `python -B -m unittest tools.migration72.test_scene_mplevel_reader_factory_window -v`：8 组通过，覆盖来源与 profile 漂移、限制性 unpickler、声明/参数歧义、caller 原字节和边界、AOT、pdata 缺失/重复/重叠、非可执行或非 file-backed、短读、8192 B/2048 条预算、连续解码与 output 覆盖。非正或超限时先保存 pre-body，再停止且不读取 body。

本项新增 code body 仅 238 B；exception directory 单独账列。构造函数、子 callee、xdata、全局内容、目标 CPU、native、live、客户端及调试器操作均为 0。未部署，也没有新增客户端验收。

## 复验与缺口

主线已实际执行第三命令，九份对应原件与正式双fresh逐字节相同；纯 `--audit fresh1-20261111b main-third-20261111b --output audit-third.private.json` 通过，1366 B，SHA-256 `87cee18986e6ab1068cb0826afd4b1ae1c3ef55ab3162705f3b397e09118eb14`。主线重跑上述8项测试全过。本单窗限定出口已验收。按用户最新指令完成当前任务后暂停，引用语义/reader构造后继未启动。

主线可运行 `python -B tools/migration72/scene_mplevel_reader_factory_window.py --output main-third-20261111b`，再将九原件与 `local/migration72/d02-resume-20261111b/scene-reader-factory-window/fresh1-20261111b` 逐 byte 比较；目录和文件拒绝覆盖。双 fresh 不代替第三次实际执行。

工厂自然返回值、reader backend、初始 cursor、slot 114 目标、世界工厂返回、Scene/resource ready 和 D-02 仍未通过。引用清单中的未知调用或 tail 均未追踪；如后续发现指向既有拒绝动作或其依赖，只停该准确边及其依赖。没有实施或重试 A 第二返回、热 Default 归属或 B 组合 loader。
