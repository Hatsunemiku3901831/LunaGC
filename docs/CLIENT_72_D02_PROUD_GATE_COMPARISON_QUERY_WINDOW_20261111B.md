# D-02 Proud query caller 单窗机械验收（2026-11-11）

任务：`E-01d1/R3-PROUD-ACTUAL-ADOPTION/PROUD-GATE-COMPARISON-QUERY-WINDOW`。本项只证明固定来源、caller 原范围、普通 AOT 身份和 E8 指令边界；不推断参数、返回值或比较的业务语义。

## 固定来源与前置门禁

私有机器请求和规格按大小及 SHA-256 固定，11 项来源匹配，三份 73 候选索引逐字节相同。原 metadata 和参数报告分别确认 caller `65145/540714` 唯一声明、五个非 byref class 参数，以及 query `63488/526531` 唯一声明和一个非 byref uint32 参数。经固定路径解析器核对当前 `GAME_PATH` 下五个 stock 文件的完整大小和 SHA-256，PE base 匹配。

实际普通 CodeRegistration cell 为 `0x1428a54d0`；按各全局 method index 分别计算 caller slot `0x142cc5620` 和 query slot `0x142ca9ae8`，原 QWORD 分别指向 `0x14a621790` 和 `0x14d6d70f0`。原 site `0x14a6218fa` 的 E8 有符号 rel32 计算目标等于实际 query slot。两个 slot 按 caller、query 顺序保存为 16 B 原件；没有读取 query body。

完整 exception directory 单独核对 14,344,080 B 及固定 SHA-256；caller entry 的 exact、containing 与 site containing 唯一且同一条原记录。该 pdata 指定 `[0x14a621790, 0x14a621aab)`，795 B，在同一可执行且 file-backed 的范围。body 之前先保存 boundary、8 B cell、16 B slots、12 B pdata、5 B E8 和 pre-body 诊断。

## 实际结果

- 两次 fresh 的九份原件逐字节一致；`code.bin` 为 795 B，SHA-256 `2c45365f85d4b3a63bbccb0494292411211f08b39c316343fc6b2f9ee485fb26`。
- 连续解码精确覆盖 pdata 区间，共 201 条指令，低于 512 条上限；完整解码证实原 site 五字节是一条完整 E8 指令，目标为准确 query。报告列出 62 个直接调用、分支和 RIP 引用，不对引用目标追加读取。
- 工具内双份审计及独立 artifact 审计通过；独立审计重建九原件、核对双 slot、原 pdata/E8、输入记录、另一次解码和引用，未读当前 PE。原件和审计留在忽略目录 `local/migration72/d02-resume-20261111b/proud-gate/`。
- `python -m unittest tools.migration72.test_proud_gate_comparison_query_window -v`：8 项通过。覆盖来源哈希/索引漂移、两项声明和全部参数、slot 身份及次序、E8 有符号位移和目标偏差、pdata 缺失/重复/重叠、1024 B 与 512 条边界、非可执行/非 file-backed、短读、解码缺口、site 中部/漂移和拒绝覆盖；site 门禁失败时不读 body 并保留原件及失败阶段。

本项仅新增一个 795 B code window；exception directory 单独账列。xdata、callee、其他 body、RIP 目标、目标 CPU/native/live、客户端和调试器操作均为 0。未部署，也没有新增客户端验收。

## 复验与缺口

主线已在仓库根目录实际运行 `python tools/migration72/proud_gate_comparison_query_window.py --output query-main-third-20261111b`，九原件与私有目录 `query-fresh1-20261111b` 逐字节相同，8项门禁独立通过；目录和文件拒绝覆盖。准确业务语义继续交Sol max。

query 输入业务身份、返回值是否为 Depot、是否流向同一 typed 81127 元素 `+0x10`、同 Avatar 突破值、比较门槛及采用/跳过均为 unknown。其他六个准确 caller 亦未被排除。完整 Avatar、资源就绪和 D-02 客户端验收均未因此完成。既有 A 第二返回合同与 B 组合 loader 安全拒绝项没有实施或重试。
