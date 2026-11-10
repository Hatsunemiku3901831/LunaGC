# F-03d3f4d2 / A-GC-SECOND64：第二块 L64 的有界 GC helper 合同

**本轮真实第二块已经证明准确调用参数为 `RCX=H2+10h、EDX=0、R8=48`；现有原件不足以证明 helper 的实际分派和返回。** 首个必读缺口是 `141fdfa86` 对 `B+56afdd0/4` 的读取。该 4B 还不足以闭合任一侧：阈值、条件标志和所选跳表项也没有当轮 bytes。下文一次列全当前 L64 路径的最小条件输入和后继，不把预测清零写成 CPU 结果。

## 实施前计划与权限

本卡从新 A-only stock 捕获的第二块推进，仅研究实际 `S2=56 / bin4 / L2=64 / oldFlags=8` 产生的 `141fdf9c0` 必经路径。首块 `L1=32` 的 clear16 直接复用已验合同，不重复执行。自然 allocator 线程、GC 所有权、Default 三 QWORD、完整 20 分配 / 12 根 Avatar 与 g2、Scene、D-02 均未验。

1. 先核已有 904B helper、312B pop 及本 epoch 原 capture / 主线审核完整 SHA；逐请求重构 36 首读和对应 36 final，保留原 64B 第二块和首失败。
2. 从原 allocator 的真实 class U16、bin 公式和 pop 原读链证明准确 caller 与 `RCX/RDX/R8`，不把预期 `H2+16,0,48` 当实测值。
3. 只读既有 helper 原 904B、manifest 与冻结输入，列出 L64 对应的原 PC、分支、全部读写、返回和 CPU/global/外部函数依赖。禁止新增 PE 窗、CPU、live API、目标或副本写。
4. 完成最小合同后立即交主线：准确原路径、拟议 PC/地址/宽度写权限、真实 old bytes、最小缺失输入与实施出口。主线审核或另派 Sol xhigh / Terra 后才可执行 CPU；本 Sol max 不自行串写 helper / pop / HashSet 构造。
5. 若此必经分支读 CPU/global 或调用外部函数，先交准确原地址、来源和最小所需范围；不补零未知页、不 host 成功、不借旧 epoch heap。有限合同不足时保留准确首缺。

可写仅本文、`tools/client72_d02_second_gc64.py` 及必要同名 test、忽略目录 `local/migration72/d02-resume-20261110b/a-second-gc64/`。旧 source/pin/collector/adapter、共享索引、生产/Proto/patch/配置/资源只读；主线统一 Git 和共享状态。新 ready 已消费，客户端已正常退出，本卡不重用 ready，不启动目标、调试器、API、DLL、JVM、DB 或构建。

本项复用既有定向 GitHub partial 成果；没有新大检索缺口。外部相似实现不得覆盖原 bytes 或扩大到任意长度。

## 固定来源

| 原件 | SHA-256 |
| --- | --- |
| `a-gc/gc-helper.code.bin`，904B，`141fdf9c0..141fdfd48` | `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31` |
| A2 本轮 capture，私有 stock-helper 目录 | `ea554e7664bbdaa134dc636ee413caa4fa46bd9b13343c5fc4376bced2c843a5` |
| 主线 `main-live1-review.private.json` | `79daef5fcfa83173a117a05c8ae3750d28d5b554b47fd78831cdaf6034b184ec` |

以上三项已由本卡独立核对。另核 helper manifest SHA `73b50cba66ee3aed2a7b2c3f1b3a772104da6219686b3dd9fccc915f30c36915`、12B pdata、312B pop、420B allocator、1661B Avatar ctor 和 201B HashSet ctor，共十项完整 pin。所有路径、大小及完整 SHA 保存在本卡私有审计报告；没有重新读当前 PE 或现场。`newPEWindows/nativeCPUExecuted/liveAPIInvoked/capturedCopyWriteApproved` 仍为 0 / false。

## 本 epoch 原读链与准确 caller

本卡从 raw `bytesHex` 重构，不采用报告中仍为旧显示值的 `recordCellRead=false` / `secondRecordPointerKnown=false` 作数据源。36 个首读和对应 36 个 final 的地址、准确宽度、原 bytes 逐项一致；首账 36/784B，总账 72/1568B。它仍是非原子稳定候选，不证明 ABA 不存在或自然线程/GC 所有权。

| 原读序号 | 从原 bytes 得到的链 | 原指令职责 |
| --- | --- | --- |
| 3、4 | TLS module `M` → `M+19d0/8=C` | allocator 的原 GS58/TLS/context 链，采用既有已审等价，不声称新读自然 GS base |
| 28、29 | `B+5897cf0/8=K2`，完整 class208B | `15183fea1` 取 class，`15183fea8` 原 call `1405336c0`；K2+b4 的 U16=56，CA=62h，C7=1 |
| 31、13 | ordinary2147 非零，且不等 comparison | 原普通查询的准确支持分支，不用旧 epoch cell |
| 29 | K2+8 原 DWORD 的 low3=3 | CA bit02 置位，原 `79f..7b6` 得 tag=1，class 参数为 K2 OR 1；原 `1405337d8` call pop |
| 32 | `C+28h+10h*4 /8=R2` | 原 bsr/shift 对 S2=56 得 bin4；`140563d34` 读独立非零 R2，不等 R1 |
| 33、34、35 | `R2+18/8=64`、`R2+20/8=H2`、`R2+10/2=8` | 64 位 L2、非零 head 及原 lock-inc 初值，没有拿 request56 充 L64 |
| 36 | 完整 H2/64，H2 以 32B 对齐，H2[8]=08 | 原 `d39` 令 RSI=H2；`d60` 在写新 flags 前保存旧 AL=08 |

原 `140563d6f` 读取 **R2+18 的 U64=64** 到 R8；`d77/d79` 对旧 AL 的 bit08 不跳过；`d7b` 原 64 位加 −16 得 48；`d7f` 令 RCX=RSI+10h；`d83` 清 EDX；`d85 e836bca701` 的 rel32 独立重算为 `141fdf9c0`，原 return 为 `140563d8a`。这些是有来源输入上的静态数据流推导，未声称采到自然 call frame。

完整 H2 的 SHA 为 `ce0af5beb64945a93b2927ef01544ebcc8f8c53c7042de933eefd08cd75d71cc`；待清理 `[H2+10h,H2+40h)` 的 48B SHA 为 `fd1b041a57ea2a62074cc20abb3f0e3aa5a7090adc8e7a61e991237314f57d76`，其中 **18 个非零 byte**。原内容留私有证据，不补脏值、不重用首块、不读 H2 内 next 指向的块。原件及其 payload 均没有被写。

## clear48 的全部静态分派依赖

原 904B 连续独立解码 176 条。无条件前缀只经过 `9c0/9c3/9c6/9cd/9d0/9da/9de/9e3/9e7/a70/a74/a78` 共 12 条：RAX 和 R9 保存目的 `D=H2+10h`，R10 由原 RIP-relative LEA 得模块基址，DL=0 扩展并复制形成零 XMM；48>15 且 48>32，所以必到 `141fdfa86`，此前没有 helper 内存读、store 或 call。**本卡没有执行这 12 条。**

令 `I=U32[B+56afdd0]`、`T0=U64[B+56afdd8]`、`T1=U64[B+56afde0]`、`F=U8[B+5eeeb08]`，比较均按原无符号条件。名称只说明本体中的分派职责，不证明具体 CRT 版本或 host CPU 能力。

| 原 PC / 原 bytes | 精确输入 | 本 L64 的用途和条件 |
| --- | --- | --- |
| `141fdfa86 833d43036d0303` | `B+56afdd0/4`，必读 | `I<3` 在 `a8d` 跳 `c70`；否则走 `a93`。不能拿磁盘初值或 host CPUID 填 I |
| `141fdfa93 4c3b053e036d03` | `B+56afdd8/8` | I≥3，比较原 clear 长度48；48≤T0 跳 `ab2` |
| `141fdfa9c 4c3b053d036d03` | `B+56afde0/8` | 仅 I≥3 且 48>T0；48>T1 也跳 `ab2` |
| `141fdfaa5 f6055cf0f00302` | `B+5eeeb08/1` | 仅 I≥3 且 T0<48≤T1；F bit02 置位在 `aac` 尾跳窗外 `141fdf9a0`，否则 `ab2` |
| `141fdfc70 4c3b0561016d03` | 同一 `B+56afdd8/8` | I<3；48≤T0 跳 `c86` |
| `141fdfc79 f60588eef00302` | 同一 `B+5eeeb08/1` | 仅 I<3 且 48>T0；F bit02 置位在 `c80` 尾跳同一窗外地址，否则 `c86` |
| `141fdfb49 478b9c9af090d106` | **本 H2 对齐下** `B+6d190f4/4` | AVX 侧对齐后 R8=32，rounded32/index1；原 DWORD 零扩展为 RVA，加原 R10 后在 `b54` 间接跳 |
| `141fdfcfd 478b9c9a3891d106` | **本 H2 对齐下** `B+6d19140/4` | SSE 侧对齐后 R8=32，rounded32/index2；原 DWORD 零扩展为 RVA，加原 R10 后在 `d08` 间接跳 |

每个 RIP-relative 地址均从原指令长度和有符号 disp32 独立重算。以上六个 unique 数据 span 共29B：四个 global21B、两个互斥 table 项各4B，**本轮 36 个原 span 对它们没有任何 byte 覆盖**。当前不能选 I 的一侧，不能确定阈值分支、F 或 table 值。两项 table 必须有原值才能确定准确尾部；“目标代码已经在 904B 内”不能代替这次缺失的数据读。

### 两条窗内普通尾部的条件合同

这些是可交实现的**条件规格**，没有获副本写权；期待的 table RVA 也没有被观测。不得由 host 填表来使预期成立。新原读项若不是列明目标，保留原值并止间接跳前，不把另一条窗内入口、填充区或窗外地址当成功。

| 分支 | 对齐算术 / 期待的 table 值 | 条件成立后准确 store 和 ret |
| --- | --- | --- |
| I<3 且未触发外跳 → `c86` SSE | D mod16=0；R9=−16，RCX=D+16=H2+20h，RDX=16，R8=32；index2，期待原 table DWORD=`01fdfd35` | `141fdfd35` 写 H2+20h/16；`d3c` 写 H2+30h/16；`d43` 写原 RAX=D=H2+10h/16；`d47 ret` |
| I≥3 且未触发外跳 → `ab2` AVX | 原 `vinsertf128` 生成零 YMM；D mod32=16；R9=−16，RCX=H2+20h，RDX=16，R8=32；index1，期待原 table DWORD=`01fdfb94` | `141fdfb94` 写 H2+20h/32；`b9b` 写原 RAX=H2+10h/32；`b9f vzeroupper`；`ba2 ret` |

SSE 三个16B事件不重叠；AVX 两个32B事件在 `[H2+20h,H2+30h)` 重叠16B，顺序不得合并。两侧 unique 写域均恰为 **`[H2+10h,H2+40h)` /48B**，预测内容是零。old/new 必须在后续真实副本执行时逐事件读取，不能从静态预测填报告；AVX 第二写的重叠 old bytes 应来自第一写后的副本。

在当前对齐下，两侧 adjusted R8=32，分别必跳过256B和128B大循环，所以 `ad5` 的阈值再读、NT 循环及相应第三张 table 不经过。目的 payload 不被指令读取；填零模式早在对齐修改 RDX 前形成。正常尾部没有 global 写、GC 回调或任何 call；ret 只消费原 caller return，RAX 保持 D。后续原 pop 的 `140563db6` 才重新置 RAX=H2，不混为 helper 的返回值。

本合同只覆盖这份冻结 H2 的对齐、clear48 与上表两项准确 table 目标。它不泛化任意长度或新的对齐；新 epoch 需从真实 head 重算并核对适用条件，未知形状停止，不强改地址。AVX 原指令在实际执行后端能否完整支持也尚未验证，不能用 SSE/host memset 代替。

### 窗外尾跳与无法缩减的缺口

`141fdfaac` 和 `141fdfc80` 都是 `jne 141fdf9a0`，**该地址在原 904B begin 前20h，不在现有窗内**。没有另一个 call/push；已有原 return 仍在栈上。到尾跳前 RCX=D、EDX=0、R8=48，RAX/R9=D、R10=B，XMM0为零。该外部 body 的机器码、原边界、全部读写与实际 ret 均未知，不能因其参数像填充函数就 host 成功。此路径只能保留分支证据并停在原尾跳前；需要主线另审精确 body 来源后再研究。

故当前已形成完整的输入/停止合同，但 **helper 实际完整路径、任何清零写、原返回和第二 allocation 返回均未验**。仅补必读4B后还会缺 T0，AVX 条件下再缺 T1/F，最终缺所选 table；不能逐次准备只多4B的 live 轮次。

## 交主线的只读 v2 collector 最小规格

严格新 tool/profile/scope/seal，原四 profile、第五 v1、collector/adapter/source pin 只读。本页不授权实现、现场或 CPU。建议将“第二块的 helper 分派候选”和“Default 候选链”明确为两个只读数据子图，主函数仍只有正常流程，所有原件/输入先在入口一次验证。

1. 复用原一次 A 事务的 TLS/List/Pool/HashSet/独立第二块图和真实原值，不复用旧 epoch heap。仅本已审 L1=32/flags8、L2=64/flags8、真实 H2 对齐符合上述条件时收本 helper 图；其他尺寸/分支保持原 partial。不能先调用带 final 的旧 collect 再追加读。
2. 第一个 helper 读恰为 `B+56afdd0/4`；依原 unsigned 比较读取 T0、必要 T1 和 F。未触发窗外边才读取一项准确 table DWORD。I≥3 的最坏 **5读/25B**；I<3 的最坏 **4读/17B**。只取实际需要的项，不为互斥另一侧多读，不读 whole global 区或整张表。
3. table 值由原 DWORD 零扩展并加本 epoch B，只有与上表对应原目标相等才可称“helper 窗内输入候选齐备”；这仍不等于 CPU/写/返回通过。F bit02 外跳或其他 table 目标均保留实际 bytes 和准确 PC、标记 helper 子图 partial，不读窗外 body。
4. Default 是独立只读候选：`157d97698` 的 `B+594fd78/8=U`，非零才读 `U+38/8=P`，非零才读 `P/8=D`；D=0 保留 cold，D非零只记录 opaque QWORD，不读 D pointee。它不以 helper 成功作为“自然已返回”的前提，也不因为取得 D 就启用 HashSet ctor。若主线选择在 helper 不支持时仍采 Default，必须在新 scope 中明确这项独立图授权，整体仍 partial，不能默改 v1 的首未知边停止规则。
5. 任何真实读失败、短读、重叠矛盾均首失败停止后续读取，不重试。所有成功 unique span 在同一个 ledger 末尾统一 final 一次，原地址、原宽度；含控制量、所选 table、Default 和完整两块。组件级“不支持原指令路径”与“读失败”分开记录，不清掉任一 unknown。
6. 本 epoch 的实际形状若再有最坏 helper5/25和热 Default3/24，首账为 **44/833B**，总账 **88/1666B**。这只是当前形状，不是新 epoch 保证。保留旧完整 profile 的保守上界44/1184（其中已经包含 Default）时，新增 helper 至多5/25，新的上界应另设 **49/1209 首、98/2418 含 final，单读仍208**；不能把 Default 再加一次，也不能沿用旧88/2368宣称所有支持形状均够。实际较小也仍需新 scope/source seal/API/RPM预算门禁。
7. 原 primary/TBI 两次96B及各句柄正常关闭协议不扩大，不新建线程、装 DLL、暂停目标、host执行、写内存或构造对象。由主线完成新 adapter/owned/人工/actualCheckOnly/stock/保护/alias/进程端口及退出审核后才可申请新实时 ready；本轮 ready 已消费，当前不启动现场。

v2 验收至少覆盖 I<3 / I≥3 两侧，T0/T1 的48等号边界及完整U64，F=0/bit02置位，table 正确/错误/窗外目标，Default U/P零及 D cold/hot，所有新增首读/final短读、漂移和一次final/准确预算；fixture 只验读图与拒绝规则，不当自然数据。任何新对齐或长度先保持 unsupported，不借此卡扩大通用 memset。

## 实际静态验证与复现

私有 `audit-contract.private.py` 只读十项固定源，独立 Capstone 连续解码五个既有 code 窗；从原36+36记录、K2 U16/tag、bin4、独立R2/64B/H2/旧flags链推导入参，核原 call rel32、12条前缀、全部 RIP 数据地址、两侧对齐/index和条件写域。核六个新数据 span 在当前原捕获内逐 byte 均无覆盖，并确认没有 Default root 原读。不会导入 emulator、解析 PE、访问 target/API、写原件或写数据副本。

两次 fresh Python 均 exit0；语义报告各15094B、逐 byte 一致，SHA **`665a737fc7815d5468d8c424ed9f9be090191ff98c63d11576536e95638afe3a`**。最初独立静态报告也同 bytes/SHA；真实 argv、UTC、exit、stdout/stderr 分别保存在 `final-first/repeat.*`，时间不混入确定性语义报告。审计源码 SHA 为 `37589ba8c76d0b00b6bf816980a1be640d252a9838717c67a09cf4dbd4b27c90`。主线可选未存在的新输出名作第三审计：

```powershell
python -B local/migration72/d02-resume-20261110b/a-second-gc64/audit-contract.private.py --output local/migration72/d02-resume-20261110b/a-second-gc64/contract-main-third.private.json
```

本卡只新增本文及私有离线审计/报告，没有受跟踪工具或 test，也没有 Git 提交；共享 Project/Harness/交接与本地提交由主线处理。只有文档/离线研究，不需构建或 GS 重启。自然 allocator 线程、GC 所有权、Default 值/热路径、完整20分配/12根构造、Avatar/g2/Scene 与 D-02 全部保持未验。

主线第三静态CLI `contract-main-third.private.json` 同15094B/SHA `665a737fc7815d5468d8c424ed9f9be090191ff98c63d11576536e95638afe3a`。仅关闭条件输入/停止合同；所有必需controls/table仍未取得，未执行CPU或写副本。root已另冻结新GC/Default独立profile实施规格并交Terra，原v1源码/历史98源快照保留，新scope须全套独立验收后新ready。
