# E-01d1 / R3-PROUD-SCALAR-TABLE-BUILDER-SEMANTICS

2026-11-11C，父 D02-R3。117B 已把原 seed 地址的传递、两分支和发布值说明完整；它本身不写 seed、不生成八 quarter，也不读对象长度。首个仍缺的静态边是间接 CALL 的 `145744ec8` 绑定值。现成注册追踪只有 PC 和 EB8 的观察，不能给 EC8 命名。本项可在移除 A/B 拒绝动作及其产物后独立完成；不晋级 tableInitialized、resourceReady、D02-R3 或 D-02。

输入为[唯一原窗](CLIENT_72_D02_PROUD_SCALAR_TABLE_BUILDER_WINDOW_20261111C.md)、[131985 发布者语义](CLIENT_72_D02_PROUD_SCALAR_INITIALIZER_SEMANTICS_20261111C.md)及[scalar 条件合同](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md)。忽略根 `local/migration72/d02-resume-20261111c/proud-scalar-table-builder-semantics/` 保存纯 artifact 审计和下一窗 request。既存源码、索引和共享文档保持只读。

## 原窗与逐路径值流

`[14146af10,14146af85)` 117B、26 条，code SHA-256 `b82fe715bc81becf0bc3359f9f798eaa7993aea3b5727893ec441bf8e5fa6abf`，pdata 12B SHA `4daa99e6e51231fae9a00201daa0346ce05a208e598293629b6aafb3834e4a4d`。`table-builder-window/fresh1,fresh2/` 八件逐 byte 相同；result 7774B SHA `d60572a944657d547fae2566f801980cbaae321acb28d69b26128017c60e21eb`。本项复核双 fresh、raw 字面重组、连续范围、pdata begin/end 和独立有符号 RIP/E8 位移；未声称第三次集成验收。

令入口 RCX 为 `p`，`W=address(14574d560)`，`C=U64[145744ec8]`。原 caller 在 `149a752bd` LEA 得到 `p=address(145872d30)`；它不是该地址存的 uint32 值。

| 原 site | 原证与条件值流 |
| --- | --- |
| `14146af14..af23` | `R8=p; EDX=800h; RCX=W; CALL C`。没有显式准备 R9，不据此补出精确原型或参数个数。LEA 不读取 W 的内容；本轮也未读取 C cell。 |
| `af29..af2b` | TEST **EAX** 后按低 32 位是否为 0 分支。`RAX=100000000h` 同样走零分支；负值及其他非零 uint32 均走非零分支。没有状态码业务名证据。 |
| 非零：`af2d..af3f` | CALL `1414d5b30` 得 `H`；`RCX=U64[H+130h]; EDX=0`；恢复栈后 tail `1404bb0a0`。正常返回值为后者未知的 RAX；本窗没有显式 `RAX=0`，不能称作 null/空数组或失败即不发布。 |
| 零：`af49..af5a` | CALL 同一 selector 得 `H`；`RCX=U64[H+130h]; EDX=800h; CALL 1404bb0a0` 得 `R`。本窗没有 null、类型、长度或容量检查。 |
| 零：`af5f..af78` | `RDX=W; R8D=800h; RBX=R; RCX=R+20h; CALL 141fdf310`；该调用正常返回后 `RAX=RBX=R`，忽略此次 callee 的返回值，恢复原 RBX 后 RET。这些是复制形状的参数，callee 的完整行为不由此窗单独证明。 |
| 回 caller `149a752c9..2d0` | 重新取 `root=U64[14586b600]`，写 `U64[root+e970h]=helperReturn`。对正常返回的两分支都适用，没有只接受零状态、非空结果或已有 table 为空的守卫。 |

本体没有对 `*p` 的直接 store。seed 地址只在 callback 前保存在 R8，CALL 后 R8 为易失寄存器；后面也未再次显式传入该地址。因而“callback 写 seed、何时写、是否写足 uint32、用什么状态或字节序”仍需 callback 原来源。无法从参数 `800h` 推出实际写满 2048B、managed array 的 length=2048 或返回对象的存储归属。

callback 的非零分支没有执行 `141fdf310`。零分支若 R 无效，则本体仍计算 `R+20h` 并交给后续调用；正常返回、容量及完整复制都是合同前置，不能当已观察事实。caller 的 IFix flag 非零分支跳过此 helper 和本发布 store；flag 为零只是进入该路径的条件，不是只初始化一次的证据。发布后六 CALL 和一次 tail 已由前专题列出，它们的内容未审，不能声称 seed/table 此后不变或本函数是唯一 writer。

## 现成来源能证明到哪里

已按 REA 技能复用固定来源，未打开目标或新分析项目。只读检索 `analysis/`、`evidence/`、`tools/`、`docs/` 中 EC8、注册入口及缺失 PC；没有新 GitHub 检索或 PE-wide 分析。

| 已检查固定来源 | 结论与能力边界 |
| --- | --- |
| `analysis/disasm-1411267b0.txt` | listing 到 `141126804`，可证写 EA0、EA8、EB0、EB8 等槽，EB8 值为 `1408baee0`；到此没有 EC8 store。不能从后续槽间距补出指令。 |
| `evidence/ec2b-trace{,-repeat}.json` | 两件各 542378B、SHA `b7d8076d6496222bb72e23c7f1921db7fe475f3542e4c4a056962a6631c85209`。registration 含 41 个 native PC `1411267b0..1411268c1`，models 为空；没有各指令 bytes、寄存器写值或 EC8 cell 值。PC 中的 `14112680b/812/819` 不是写槽/target 的证据。 |
| `initial-key-native-init-exploration.json` / `initial-key-live-init-exploration.json` | 同一入口 PC 记录，仍无 EC8 写值。前者 2806B SHA `5645292ae9e12389a09e8931bf14a5be99b38a0c0070fbcd82a6c6093b644a24`；后者 1719B SHA `296e0e598f0c05e06acd2d7425595f1669aa0d94b477e2f857964bd0bda74c96`。 |
| `evidence/dynamic-ec2b-registration.json` | 历史真实观察称暂停在 `1411268c1` 的 RET 前，只记录 EB8 从 0 变为 `1408baee0`。没有 EC8 或本 scalar callback 的观察。当前不连接或续行该会话。 |
| `evidence/d02-r2-d01b5c-http-imports.json` | 8222B SHA `bd7774317897343e600239a7af290ed1bef45894cb6bc68051be76df770cb3c8`；是 HTTP/加载相关筛选名单，无 IAT 槽地址。既不能把 EC8 绑定到一个导入，也不能据未命中断言它不是导入。已有 SDK REA 导入报告的目标为 AccountPlatNative.dll，不充当主 EXE 的 EC8 来源。 |
| `analysis/disasm-1414d5b30.txt` 与既存工具 | listing 只给 `ECX=5; tail 1419d9f00`。`client72_ec2b.py` 的 engine+130/byte class 和 `1404bb0a0` 的严格 4096B 分配是明确 fixture；`client72_seed_crypto.py` 将 `141fdf310` 设为 memcpy 模型。这些模型不证明本窗 800h 的真实对象/长度，也不证明 EC8 是 Ec2b 或随机表函数。本项未导入或执行它们。 |

这是一项可继续取得的本机静态 source 首缺，未写成所有来源均外部阻塞。两个地址 `145744ec8`、`14574d560` 目前分别只称 callback cell 和传入地址，未按邻址或旧工具名赋业务名。

## 最小后继：只定位 EC8 绑定

私有 `next-window-request.private.json` 固定唯一候选入口 `1411267b0`。必要性来自已存注册入口实际写同一槽族的原 listing 和完整执行 PC，而不是声称它已经是 EC8 writer。先完成该入口静态写值绑定，比先读取未知 callback/global 或补整个初始化树更小。

- 历史 41 个 native PC 加独立 pause-on-RET 记录提示连续跨度 `[1411267b0,1411268c2)`，预计 274B；这不是当前 pdata 或完整函数边界原证。先查固定 EXE 的唯一 exact/containing/overlap pdata，完整可执行 file-backed body 上限 **320B / 64 条**，pdata 原记录最多 12B，bounded evidence 合计上限 332B。不新读 caller、AOT、xdata、邻窗或任何 global 内容。
- **若无 unique pdata，bodyRead=0 交边界诊断。** 主线可另行审定是否仅取这一个精确 274B 的历史 trace-span，并明确其不是已证完整函数；本 request 不自动采用 RET、对齐或下一入口作 fallback。这避免把 leaf 无 pdata 误写成环境阻塞，也不偷偷扩大授权。
- 全 stock 身份 hash 输入 550661784B 与 exception index 14344080B 单列，不混入 332B。身份、来源、边界、预算和 decoder gate 均须先于新 body 读取；两个 fresh 加独立第三 intake。只索引原 RIP store、赋值寄存器来源及 raw 位移，**不得跟随所赋 target**。
- 候选若没有 EC8 store，真实交 not-found；新安全拒绝保留原提示/准确 site，停动作及依赖，不换路线。准确碰到 A allocator/第二返回/Default 归属或 B loader 边立即停对应枝。当前 `131984→149a75140→A` 冻结保持。

提交主线审定的最小 Terra 可写文件为 `tools/migration72/proud_scalar_callback_registration_window.py`、对应 `test_proud_scalar_callback_registration_window.py`、新 `docs/CLIENT_72_D02_PROUD_SCALAR_CALLBACK_REGISTRATION_WINDOW_20261111C.md`，独立输出仅 `local/migration72/d02-resume-20261111c/proud-scalar-table-builder-semantics/callback-registration-window/`。其余文件只读。测试须用独立字面/RIP 算术核 EC8 写值而非抄 collector 分类；覆盖 boundary 缺失/重复/交叠、cap 和 gate 前 bodyRead=0。它是 source→绑定包，不是 callback 实现、初始化执行或 codec 通用工具。

原绑定一旦有证，先核对应 target 是否已有固定完整 listing/raw，再由语义分析决定唯一最小后继；本包不预取该 target。当前尚不能给准确 callback AOT/pdata/caller E8：已证的是 `14146af23 -> U64[145744ec8]`，目标值未知，注册入口也没有已存 direct E8 caller 作为此 request 的依据。request 明写这些缺项。

## 对实际 scalar/row 的意义与交付

如后续证明 callback 建立同源的 `F0..F3`、`I0..I3` 和 seed `S`，下游又确实返回有足够容量的对象并完成从 W 到 `R+20h` 的 800h 复制，才能将本 publisher 接到[既有条件合同](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md)：对每个选中字节 `Ij[Fj[b]]=b`，读写用同一 uint32 seed 及有效 table epoch，getter 的 XOR 抵消 setter 的 XOR。2048B 足以容纳八个 256B quarter，但请求参数不证明实际布局或八组内容。已存自然 inverse/seed fixture 的数学 forward 重建仍只是条件 fixture；本 stub 不把它变为自然 forward 读数。

除绑定/目标 seed 写与 quarter 建立规则，还须保留实际初始化是否发生、后续 writer/更新和 epoch、对象/reader 状态、实际 encoded/getter、QueryWrapper base 寿命及实际 41105 row 的来源缺口。`actualForward/actualSeedEpoch/actualEncoded/actualGetterU32=null`，actualRow=false。没有自然热 Equals 输入不能称 map hit 或当前采用；不能造空 addProps、paramList、OpenConfig 让 Java 通过。本包不需要新 live，也不能凭静态规则消除这些实际 row 前置。

实际运行了忽略根 `audit_semantics.private.py`，只读现成 artifact；117B/26 条重组、双 fresh 八件、pdata 和八处原相对引用计算通过，五个低 32 位状态算术样例包含非零、最高位及高 32 位非零/低位零。未执行函数/模型、构造数组或伪造 callback 输出。review 9964B SHA `1bb07a4d71167697d27430f3dae9d1afdf46868b9d81edf777664ec83fd2c407`；request 9091B SHA `b2fd5057147a867aee5963b8295682935790a1a8864becdbd4ed44f93a248c23`。未操作 Git/index、构建、JVM、客户端、DB、端口或部署；共享索引由主线维护。
