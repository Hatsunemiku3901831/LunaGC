# D-02 Proud Promote 上下文交集单窗机械验收（2026-11-11）

任务：`E-01d1/R3-PROUD-GATE-COMPARISON/PROMOTE-CONTEXT-WINDOW`。本项固定来源并取得唯一 caller 原窗，验证指令边界；不推断 phase、同 Avatar、81127 比较或采用行为。

## 来源和 body 前置门禁

私有规格和请求的大小与 SHA-256 匹配，10 项来源固定。独立以 module base 加到旧 41 条上下文的 function RVA，再与 73 候选的原 containing begin 相交，得到 4 条上下文、2 个函数，与既有 join 相符。仅选择 `[0x14cbdd560, 0x14cbddd52)`；另一个 4427 B 交集函数保持 unknown，未读取。

metadata 的 caller、group getter 和配置查询三项声明及注册地址唯一；owner 25486 的 `+0x230` 字段是非 byref class18163，`+0x25c` 是 uint32 字段。经固定路径解析器核对当前 `GAME_PATH` 的五个 stock 文件完整大小和 SHA-256，PE base 匹配。

原 CodeRegistration cell 为 `0x1428a54d0`；按普通全局方法索引读取三个 slot，顺序为 caller `198316`、group getter `138176`、配置查询 `176922`，实际 QWORD 与各固定 entry 相符。完整 exception directory 单独核对 14,344,080 B 和固定 SHA-256；caller entry 的 exact/containing、7 B 候选和三个旧上下文 callsite 的 containing 均唯一且为同一原 pdata。原 extent 为 2034 B，并在同一可执行、file-backed 范围内。

body 之前保存 boundary、8 B cell、24 B slots、12 B pdata、7 B candidate、按地址顺序排列的 15 B provenance E8 原件和 pre-body 诊断；三条 E8 的 signed rel32 目标分别匹配实际 getter/query slot。getter 与 query 只核身份，没有读取其 body。

## 实际结果

- 两次 fresh 的十份原件逐字节一致。`code.bin` 为 2034 B，SHA-256 `0a07ead5e86f32a54a7892ffb054d897cef610cf3a1516e44ee4f24f39ad07d6`。
- 连续解码完整覆盖原 pdata 起止，共 437 条指令，低于 1024 条上限。完整解码确认 7 B 候选和三条 provenance E8 均是各自地址上的完整指令；报告列出 148 个直接调用、分支及 RIP 引用，不追加读取目标。
- 工具内双份审计与独立 artifact 审计通过；后者从十份原件重建原字节，重新核对三 slot、四 site 的 pdata、E8 目标、完整解码和引用，未读当前 PE。原件与审计留在忽略目录 `local/migration72/d02-resume-20261111b/proud-gate/`。
- `python -m unittest tools.migration72.test_proud_gate_comparison_promote_window -v`：7 组通过，覆盖来源漂移、交集、字段与唯一注册、三 slot 身份和次序、三 E8 顺序/位移/漂移、pdata 缺失与重叠、4096 B/1024 条预算、可执行和 file-backed 边界、短读、连续解码及各 site 指令中部、拒绝覆盖；五类前置失败均验证不读 body 并保留阶段。

新增 code window 仅 1 个、2034 B；exception directory 单独账列。xdata、callee、其他 body、RIP 目标、目标 CPU/native/live、客户端和调试器操作均为 0。未部署，也没有新增客户端验收。

## 复验与缺口

主线已在仓库根目录实际运行 `python tools/migration72/proud_gate_comparison_promote_window.py --output promote-main-third-20261111b`，十份原件与 `local/migration72/d02-resume-20261111b/proud-gate/promote-fresh1-20261111b` 逐字节相同，7组反例独立通过；目录和文件拒绝覆盖。准确业务语义另交Sol max，未用机械来源代替比较结论。

已有来源将 `0x15184adf0` 标识为 Avatar 定义的 Promote group 来源；它不是实际突破 phase。owner `+0x25c` 的实际 phase 来源、同对象关系、typed 81127 元素与 `+0x10` codec、比较和采用/跳过均为 unknown。完整 Avatar、资源就绪和 D-02 客户端验收未因此完成。既有 A 第二返回合同与 B 组合 loader 安全拒绝项没有实施或重试。
