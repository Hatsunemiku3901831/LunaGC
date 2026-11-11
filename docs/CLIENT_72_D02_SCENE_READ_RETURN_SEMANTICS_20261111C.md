# D02 Scene read return region：有界语义与剩余控制尾区

任务：`F-03d3s2/SCENE-READ-RETURN-SEMANTICS`，父项 `D02-R1/R3`。本包只消费既存原件；31B 源的集成第三验收在派工时尚待，不代替主线验收，不升级完整 logical read、Scene、resourceReady 或自然 reader。

## 固定输入与本包核查

- 原专题：`docs/CLIENT_72_D02_SCENE_READ_RETURN_CONTINUATION_REGION_20261111C.md`。
- 原件根：`local/migration72/d02-resume-20261111c/scene-read-return-continuation-region/{fresh1,fresh2}/`。真实 unique containing/exact region 为 `[141138edb,141138efa)`，31B，SHA-256 `3ce6e2cb3e27002912cb0d2e42c8c2e318fa1ddcc5eea18a7e7499f053a40c03`；9 条指令、0 引用、`141138ef9 RET`，末尾无 fallthrough。pdata 原记录 RVA `1a605f78`、原 12B `db8e1301fa8e1301c4f0c103`，unwind RVA `3c1f0c4`。
- Terra 已交双 fresh 七件同、7 tests 与 pureartifact audit；audit SHA-256 `5fffe3d39c053a599469542dc3d0af43168548028810024d068068c40fb392ea`。本语义包核对冻结字节/指令、前驱同帧寄存器流和精确外边；没有重跑采窗或这 7 tests。
- 只读接续已验 204B core `[141138c70,141138d3c)`（SHA `ba5cd6de5a0f86c0d054b57c769670a3c97b8b11c6fffeefcd80601ecb5eafa0`）及 415B continuation `[141138d3c,141138edb)`（SHA `d837f527ce2dec86c4cbdf0464d772c989040d072cc8a232921114e193a865c2`）。三段合计 650B 是已知 region 台账，不是完整逻辑函数边界。

## 观察、推导、未知

以原 `141138c70` 入口 RSP 为 `S`，本段仍使用原 frame，入口 RSP=`S-c8`；不建立新的函数 ABI。

| 原 PC | 直接观察 | 同帧效果 |
| --- | --- | --- |
| `141138edb` / `141138ee3` | `mov r15,[rsp+90]` / `mov rsi,[rsp+e0]` | 恢复原 R15、RSI；对应 core 中已保存的槽 |
| `141138eeb` | `add rsp,a0` | RSP 从 `S-c8` 到 `S-28` |
| `141138ef2..ef8` | 依次 `pop r13,r12,rdi,rbp,rbx` | 五个 8B 恢复使 RSP 到 `S` |
| `141138ef9` | `RET` | 使用原返回地址；返回后 RSP=`S+8` |

**观察：**这 9 条指令均不写 RAX/EAX，不执行 CALL，也不写 dst 字节。此前 `141138ed0` 为 `mov rax,rbx`，`141138ed3` 已恢复原 R14；本段本身不恢复 R14。因此 future incoming 不能仅凭“到达 edb”省略 R14 的前置证明。

**推导：**在已验非零 `DWORD[This]` 路径、所有先前未知 CALL/检查/cleanup 均正常接续到 `ed0` 的条件下，完整 64 位 `K−remaining` 经 RAX 保留至原 caller 的 RET。它是该路径的算术进度结果；先前未知 virtual slot 的成功语义、实际复制字节、有效 array 容量和所有输入的 full/partial/EOF/error 尚未由此成立。refill 忽略 native read 的返回值这一旧合同不变。

**未知：**`This` 首 DWORD 的零分支、loop 失败尾区、early 边界返回，以及它们各自的返回/cleanup 仍未覆盖。上游 managed `compressionType=0` 没有绑定到此 `DWORD[This]`，不能据此前置选定当前业务分支。旧 `141fdf310` 真实复制样本只读复用其限定结论，不推广为当前未知 slot 或任意 copy 已成功。Pool、`+18` 初始来源及有效自然 reader 仍另验。

## 剩余准确控制边与 incoming

三个 target 都在已知 650B 之外，当前没有它们的字节或 containing pdata 结论。它们是当前逻辑 read 剩余控制分支，先检查真实元数据去重可避免机械串行三个单窗；不以源可定位性虚报外部阻塞。

| target / 冻结 source PC | 触发条件与必须保留的 incoming 差异 |
| --- | --- |
| `141138f06` ← `141138d46` (`0f84ba010000`) | `DWORD[This]==0`。RSP=`S-c8`，RDI=This，RBP=K，R12=原 dst，RSI=q，R15=K，R13=O，RBX=0；原 R14 已存 `[rsp+98]`。D 创建、temp 初始化、virtual/copy/cleanup 尚未执行 |
| `141138efa` ← `141138dbe/de1/e1e/e32` | 分别为 entry 长度超 temp 界、signed chunk<0、virtual AL=0、输出长度不匹配。D=`[rsp+d0]`，当前 dst=`[rsp+d8]`，q=RSI、remaining=R15，原 R14 已存。`dbe` 的 R12 是旧流值（首轮 D，后续可能更新后的 dst）；其余三边 R12=within。各边 RCX/flags/未知 CALL 输出不同，不合成单个有利状态 |
| `141138f85` ← `141138d12/d1c/d34` | 分别为 dst==0、unsigned O>=`[This+30]`、DIV quotient bit63=1。均在 `d3c` 保存 R14 前；原 R14 仍在寄存器，`[rsp+98]` 未由此路径保存。前两边 RSI/R15 仍是入口值；仅 d34 已置 RSI=q、R15=K、RDX=余数 |

所有边沿用原 frame，不作为 standalone callee 执行；精确寄存器、raw branch bytes 与源 hash 见私有 source-check/request。早期 DIV 的零除数、先前 helper 及当前 CALL 的异常/失败语义仍不补值。

## 唯一后继规格：控制尾区 containing region 批诊断

后继 `F-03d3s2/SCENE-READ-CONTROL-TAIL-REGIONS` 以主线接受 31B 源为前置。只为上述三个已有准确 target 请求真实 unique containing pdata；不把 target 伪造为 method entry，record.Begin 可不同。全部 target 的元数据诊断必须在任何 body 读取之前完成：

1. 固定 stock/profile/exception directory 和旧 204+415+31 来源，重验 8 条 Jcc 的 48B 原 bytes、signed rel32 target；AOT 不适用，读取 0B。
2. 保存每 target 的全部 containing/exact 匹配和原 record RVA/12B。各 target 必须有唯一 containing；exact 若存在须一致。按真实 record RVA+原 bytes 去重，实际选集为 1..3 个完整 region；同一 row 覆盖多个 target 只取一次，不猜相邻 span。
3. 在 pre-body 输出真实范围、目标覆盖、每个旧 region 交叠、不同新 row 两两交叠、去重字节账、section/file-backed 与完整预算。任何 missing/ambiguous、旧交叠、非同一 row 的新交叠、来源或预算失败，整批 body=0，保留真实诊断，不挑有利子集或截断前缀。
4. 全部门通过后，才读真实最小选集。**总上限 8192B、2048 指令，最多 3 个 region；不是每 target 各一份预算。** pdata 最多 36B；exception directory 14344080B 单独一次来源核查；callee/global/xdata/live/CPU 均 0。解码从真实 Begin 开始，每 target 必须落在指令边界，记录所有 RET/外 branch/末 fallthrough；region 完整不自动等于 logical read 完整。

request 已固定 source、ABI、门禁、输出及 8 个纯审计测试要求。本包未执行后继，未写模型/driver；它先解除三个真实控制尾区的来源缺口，不扩 codec/registry/allocator 或其他 helper。准确 A/B 已拒 CALL/data 边一旦出现即停对应依赖并保留原提示，不经本包续接。

## 交付与最小文案修正

私有目录：`local/migration72/d02-resume-20261111c/scene-read-return-semantics/`。

| 文件 | 字节 / SHA-256 |
| --- | --- |
| `semantic-source-check.private.json` | 11284 / `91c421ebfa773d9e63a63723da4b0b095beb80b2e09c0fed84ad87003c9ff745` |
| `control-tail-regions-request.private.json` | 24276 / `0ed83005c2fee73f13e1e1bbf0b59f33df9dbe1d89b1ec92a25d4ace09fd5614` |
| `loop-target-doc-fix.private.diff` | 692 / `dd5cd6df6dee85833248e1ce8461d7cbaa76608f68dafe6f8128e6e924f999d1` |

另按主线明确授权，仅将旧 415B 语义专题表的“remaining signed>0回daa”改为“回da6（重做表界比较）”：`141138e77 0f8f29ffffff → 141138da6`。只有这一行文案变更；旧 raw/source-check/request 不动、不重跑链、不操作 Git。已立即将最小 diff 交主线与 intake_audit。其余共享文件、源码、工具、配置、Proto、patch、资源保持只读；本包新增 PE/source、CPU/runtime/live 与部署操作均为 0。
