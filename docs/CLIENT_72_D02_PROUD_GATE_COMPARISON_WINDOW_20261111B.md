# D-02 Proud comparison 候选单窗机械验收（2026-11-11）

任务：`E-01d1/R3-PROUD-ACTUAL-ADOPTION/PROUD-GATE-COMPARISON-WINDOW`。本项只证明固定来源、原 pdata 范围及候选指令边界，不证明门槛、同 Avatar、采用或跳过。

## 固定输入与门禁

按私有规格与请求固定哈希，核对 12 项来源及三份 73 候选索引逐字节一致；metadata 与参数报告中的唯一声明为 owner 26342 / method 204056，唯一参数为非 byref class18163。经固定路径解析器核对当前 `GAME_PATH` 的五个 stock 文件的完整大小和 SHA-256，PE base 匹配。

当前 PE 的普通 AOT 原 cell 值为 `0x1428a54d0`，按全局方法索引 204056 计算 slot `0x142a33d90`，原 slot 指向 `0x14d4aa490`。完整 exception directory 的 14,344,080 B SHA-256 与请求一致；entry 的 exact、containing 和 site containing 均唯一且为同一原记录。原 pdata 规定 `[0x14d4aa490, 0x14d4aa840)`，944 B，位于同一个可执行、file-backed 范围。原 cell、slot、12 B pdata、候选 7 B 与 boundary/pre-body 诊断先落盘，之后才读取 body。

## 实际结果

- 两个 fresh 各产生九份文件，全部逐字节一致；`code.bin` 为 944 B，SHA-256 `ab0370d2a556bdde048cfda664fe41d8cc7f6d7e18cc0af889904c9b419241cb`。
- 连续解码完整覆盖原 pdata 起止，共 238 条指令，低于 512 条上限；site `0x14d4aa4e3` 的 `488bb9b8120200` 恰为一条完整指令。反汇编含原 PC/字节，报告列出 77 个直接分支、调用及 RIP 引用；这些引用没有授权进一步读取。
- 工具内双份审计和另写的一次独立产物审计通过；后者检查九文件一致性、固定输入记录、AOT、三种 pdata 选择、原字节重建和另一次完整解码，未读取当前 PE。审计原件位于忽略目录 `local/migration72/d02-resume-20261111b/proud-gate/`。
- 人工测试 `python -m unittest tools.migration72.test_proud_gate_comparison_window -v`：8 项通过，包含哈希/索引漂移、声明歧义及参数类型、AOT 偏差、pdata 缺失/重复/重叠、字节/指令预算、可执行与 file-backed 边界、短读、不完整解码、site 中部/漂移、拒绝覆盖；另验证候选字节失败时不读 body 且保留诊断。

此项新增代码窗 1 个、944 B；exception directory 单独账列。xdata、callee、其他 body、RIP 目标读取均为 0；没有目标 CPU/native/live、客户端或调试器操作。未部署，也没有新增客户端验收。

## 复验与缺口

主线已在仓库根目录运行 `python tools/migration72/proud_gate_comparison_window.py --output window-main-third-20261111b`，九份原件与私有目录 `window-fresh1-20261111b` 逐字节相同，8项门禁独立重跑通过；目录和文件均拒绝覆盖。来源已交Sol max复核准确语义，未因候选指令成立而批准门槛。

参数是否流向同一 Avatar、81127 同元素 `+0x10` 的准确来源与 codec、同 Avatar 的突破值、比较与采用/跳过仍为 unknown。完整 Avatar、资源就绪和 D-02 客户端验收均未因此完成。A 第二返回合同和 B 组合 loader 的既有安全拒绝项没有实施或重试。
