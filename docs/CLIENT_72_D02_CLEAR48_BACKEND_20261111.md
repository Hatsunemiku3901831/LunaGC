# F-03d3f4d2 / CLEAR48-BACKEND：原 VINSERTF128 的后端边界

**现有固定 Unicorn2.1.4 的默认环境不足以执行本条原 VINSERTF128。** 独立单指令诊断得到原 `UC_ERR_INSN_INVALID(10)`；该环境虽报告默认 Haswell19、CR4.OSXSAVE已置位，其原虚拟 CPUID leaf1 **AVX bit28=0**。不能放宽旧 guard、改人工 I 走 SSE、host 模拟或直接重跑完整 helper。本卡没有证明具体 C decoder 拒绝行、其他模型或新后端的 AVX 能力。

## 有界计划与权限

本卡解释现有 owned clear48 中原 `141fdfab2` 的来源/解码长度冲突。复用 root 的 SSE35条/三写/原RET成功和 AVX20条后 guard 拒绝的失败原件；不改旧驱动、guard、人工 I/T/F/table、库文件或版本，不把 owned 结果当 stock replay、自然 GC 或 Avatar。

1. 先核原 904B、owned 失败报告、原 driver/loader 与本机 Capstone、Unicorn package/API 的完整来源和身份。区分 distribution、Python binding、native library；优先读既有本机源码/headers，不安装、升级或大范围联网检索。
2. 单独核 `141fdfab2` 的原 6B `c4e37d18c001`、Capstone 指令、Unicorn code hook 的报告宽度，以及旧 guard 因何先停。旧失败原件和原因不覆盖。
3. 仅获准一次严格 owned 诊断：独立默认 `Uc(X86,MODE64)`，原 CPU 配置保持；只执行这条原 register-only 指令，count1、timeout100ms。记录 codehook width、invalid-instruction hook 和原 UcError；任何数据内存访问立即 stop，不执行下一条，不模拟向量结果/改 RIP/伪 RET。
4. 依据这次实际结果与固定本机接口，判断默认后端是否足以支持本条原指令；若需改 CPU model、XCR0/CR4、后端或 native owned harness，先交准确来源、方法、预算和新门禁给 root，当前不自行 fallback。

可写仅本文、必要新 `tools/client72_d02_clear48_backend.py` /同名test，以及忽略目录 `local/migration72/d02-resume-20261110b/a-clear48-backend/`。0目标/live/SDK/客户端/GC/DB/JVM/共享构建/Git。新 stock controls 另由主线准备，本卡不能用 host CPU 能力补当轮 global。形成成熟规格即交回释放 max。

## 新批准的 CPUID / XGETBV 两阶段诊断计划

第一次原6B诊断已完成后，root 新批准以下两阶段，仅诊断同一个固定 Unicorn/default19 的虚拟 CPU 状态，不获取 host CPUID、不改 model/CR4/XCR0、不执行 XSETBV 或完整 helper。

- 阶段1：新独立 owned code 恰2B **`0fa2` / CPUID**，只初始化 owned EAX=1、ECX=0；以 count1/100ms 执行，保存完整原 EAX/EBX/ECX/EDX及 ECX 的 XSAVE bit26 / OSXSAVE bit27 / AVX bit28。code 2B 由本机固定 Capstone 独立解码；本机固定 `x86.h` / `x86_const.py` 另有 `UC_X86_INS_CPUID` 标识。无 CPUID hook 改值。
- 阶段2：仅阶段1正常完成且原 XSAVE/OSXSAVE/AVX 三位均置位时，才新建同默认配置实例，以 owned ECX=0 执行恰3B **`0f01d0` / XGETBV**，count1/100ms。保存原 EDX:EAX 和 XCR0 bit0/1/2。固定 Capstone 解码读 ECX、写 EDX/EAX；本机枚举另有 `UC_X86_INS_XGETBV`。若 CPUID 已不公布 AVX，足以解释默认环境不接受本指令，本轮不再为凑案例读取 XCR0。
- 两阶段均核 default model19 和 CR4=50200h 保持，独立 owned 寄存器/code page，原源码112pin完整核前后；任何数据内存访问、异常指令/地址、invalid hook、原 UcError或超限立即停止、保留实际输出，不修复或继续依赖阶段。只读检查寄存器不声称 AVX 运算成功。

原6B诊断报告与新两阶段报告使用不同 CreateNew 文件名，分别留真实 argv/UTC/exit/stdout/stderr；失败原件不覆盖。不重复原6B、不运行全helper、不开其他 CPU case。官方 Unicorn2.1.4 的 README-first 定向源核查计划另存本卡私有 `github-plan.zh.md` 并交 root 安排 Terra max，本代理不联网搜索或 clone。

## 固定输入与三种版本身份

| 原件 / 本机组件 | 大小及完整 SHA-256 / 身份 |
| --- | --- |
| 原 helper904B | `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31` |
| root 原 `owned-main-first.private.json` | 3555B，`2b5d89bc5e72e33be656554222976baecfbe65773109b15fa6ef8305faedb445` |
| root 原 `owned-main-second.private.json` | 18930B，`426223c136b5872cca7f01ae5393c23408582905d86d81a6e5b936109b1781fd` |
| 原 owned driver | 18143B，`33346f7625e1178e12c1b9284de1d07a232f01ecf75bc1404589ca0823b83bc9` |
| 既有 `trace_client72_token.load_emulator` source | `f2fbc4728f73447dff4f7320beb3a7a31fb546287272b62ccd88a8e940312fa0` |
| Capstone `__init__.py` / 原 native DLL | `94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f` / `76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3` |
| Unicorn native DLL | 9598976B，`010d85dce84b8f6ffaec3d5420f1b5f7768f859eb831be574c7da23e656ee8f1` |
| Unicorn Python core / C API header | `b4de7d560790a587157b17edd71c83b4f2d2bade2447d1a5433c497468ac8aeb` / `0414b3948a8bdd3f28451f7f2ae03b5c790d08ea93e6e02b2e4db19a0de49507` |

Capstone 的 distribution metadata为 **5.0.9**，Python binding `__version__` 为 **5.0.7**，native `cs_version()` 为 **(5,0,1280)**；root 首轮在 identity 校验、构造CPU前因混同两种版本拒绝，不是客户端/CPU失败。root 只区分三项身份并固定原两文件，没有改 wheel、binding 或 DLL。本卡逐项复核三身份与固定来源，未用版本字符串替代文件 SHA。

Unicorn 的 distribution/binding均2.1.4，native `uc_version()`=(2,1,33621247)。本卡固定原GC64十源、root报告/driver/spec/loader和两本机包的Python/headers/DLL/metadata，共 **112项**；`input-pins.private.json` 18750B/SHA `f02965fbe282ddfac9373df863a456f2ca26af4792dcfaf1db99c8ff1f2e9310`。所有实际诊断执行前后逐项稳定。原测试14→16pin数量断言的机械修正由root另做，本卡未修改旧test，也不把该修正当作CPU门禁放宽。

## 来源解码与实际后端拒绝

原 `141fdfab2` 恰为904B窗内偏移F2h，原6B **`c4e37d18c001`**，固定Capstone完整解为 `vinsertf128 ymm0,ymm0,xmm0,1`。只有寄存器 operand；source连贯、地址在窗内，原ModRM为C0、imm8为01，没有依据把它裁成4B指令。

root 第二原报告中，SSE真实35条、三笔原16B逻辑写、原RET和owned sentinel已通过；AVX真实20条后在这条VEX前被旧 `_code` 的 `width != ins.size` 拒绝。旧reason复用了 `instruction-outside-original-code` 文案，实际是**native报告4B与源6B不符**，并非地址超出904B；该次guard及时停止，0AVX写、0AVX ret、`backendError=null`，不能从旧报告单独推出原UC错误。

本机固定绑定 `unicorn_py3/unicorn.py` 的 code hook只把native给出的 `address,size` 原样转给Python callback；invalid-instruction hook也直接返回callback布尔值。C header说明 invalid callback返回false即因invalid停止。绑定没有替Capstone改长度；旧guard不应被禁用或把size4当正常source长度。

### 一次获准原6B寄存器诊断

独立 `Uc(UC_ARCH_X86,UC_MODE_64)`，没有cpu参数，原model查询=**HASWELL19**。CR4执行前后 **50200h**，bit9 OSFXSR与bit18 OSXSAVE均1；owned YMM0事先置明确32B非零模式，原code只映射该6B所在私有RX page，不映射data/stack。只允许原PC一条、count1/100ms，未写CPU model、CR4或XCR0。

实际结果：

- code hook恰一次，原PC `141fdfab2`、native width4；invalid hook随后在同PC触发，原callback返回false，未修复或跳过。
- 原 `UcError.errno=10`，消息 `Invalid instruction (UC_ERR_INSN_INVALID)`；RIP仍为 `141fdfab2`，原6B和YMM0逐byte不变。
- 数据read/write、invalid-memory及出界指令事件均0，没有ret/栈操作，约1.29ms。此观察直接证明默认环境未完成原VINSERTF128；不是完整helper成功或stock控制量证据。

报告 `default-one.private.json` **21922B**，SHA **`466c8c1ff21c1b0cda42bf7881414a9e0c5bbfdc3544aba4e12b8a16e951455c`**；对应脚本SHA `013fb9fa8a186fe323f7e245681023c35d6fa5a57704b8eea126890174eb9cbf`。没有为复验而再执行这条原指令。

### 新批准 CPUID 的原结果与停止

阶段1单条owned `0fa2` 从EAX1/ECX0执行，hook恰2B，原RIP到owned PC+2，0内存/invalid/UC错误；default19与CR4保持。原四寄存器输出如下，高32位均0：

| EAX | EBX | ECX | EDX |
| --- | --- | --- | --- |
| `000306c4` | `00000800` | `ced82203` | `078bfbfd` |

ECX XSAVE bit26=1、OSXSAVE bit27=1、**AVX bit28=0**。这是固定虚拟CPU自身执行的CPUID，没有CPUID hook、host CPUID或host OS能力替值。按先写计划及root授权的停止条件，**阶段2 XGETBV没有执行，XCR0继续unknown**；没有XSETBV、模型/CR4/XCR0改动，没有再跑原6B或全helper。

报告 `state-probes-first.private.json` **2730B**，SHA **`9827a36280829717a1f0022c84ef25290f77f32ec91c98fdfc6f79fe2355e459`**；脚本SHA `227f55b5ea0b7b171332167ca26d478126245dad384a0dae86a7e0544a3cd85f`。本卡总实际CPU预算消费为**一次失败的原VINSERTF128尝试、一次完成的owned CPUID**，不是两次完整helper，也没有未记录的其他model试跑。

## 根因边界与交回实施规格

已经观察到的是：固定默认backend不公布AVX，且准确原AVX指令触发invalid；CPU名“Haswell”及已开OSXSAVE不能替代实际feature公布和执行。这个结果足以拒绝把当前默认Unicorn当作本AVX尾部的可用执行后端。native在VEX prefix+opcode共4B后报长度，与前置feature拒绝相符，**但具体C decoder在哪一行、是否在ModRM/imm读取前拒绝仍是推断**，需要准确release源码，不把宽度4自称已定位译码器内部原因。

本机API提供 `ctl_get_cpu_model` 和 `ctl_set_cpu_model`，Python构造器的可选cpu参数会在uc_open后立即设置；固定C header要求设置model在uc_open后任何其他Unicorn API前。当前default已经19，重复显式选19没有新证据价值。Python和C寄存器枚举均没有直接的 `UC_X86_REG_XCR0`；YMM寄存器API只证明存储可读写，MSR也不能冒作XCR0。当前不推荐凭猜测轮试其他model或只改XCR0。

下一包可直接交Sol xhigh/Terra，当前不执行：

1. root安排定向README-first上游源审计，核准2.1.4准确ref、默认model与CPUID/TCG feature mask、VEX拒绝分支和是否存在可用配置；本卡私有计划SHA `5c6e54dd309f50759a88973728b1a3e819e7af24b4d4c61d4f7a3712c73c6c2e`，上限10分钟/12调用/一个仓库/两ref。先README、license、branch/tag/tree，再准确文件，不自行clone/升级。源码tag与本机DLL的构建关联另记unknown。
2. 若原源码给出**一个**可使当前固定backend公布AVX的明确受支持配置，先向root给该配置的原API、调用顺序、来源和预算。获批后只在新owned实例先CPUID，具备必要原bits才读XGETBV；必要状态满足后才单条原6B count1/100ms复验。必须同时具备source6B、native完整解码、无invalid/内存访问、RIP+6及真实YMM低128位复制到高128位的原结果；不能host补YMM或用CPUID通过代替。
3. 若该release源码证明所有适用model都被实现/编译feature mask阻断，结束无证模型试跑；原默认后端保留SSE已验能力，AVX另开新后端任务。最小native-owned替代合同应先是独立自有EXE中的**同一6B register-only指令**，由root审核现有compiler/原源、host CPUID+XGETBV只读前置、非法指令异常保留及一次受控调用预算后再构建/执行；当前不启动native harness、不安装库、不把它扩成客户端调用。
4. 只有新后端的原6B能力真正通过，再按原owned完整合同准备SSE/AVX两case，保留原904B、人工I/T/F/table、48B唯一写域、三/两笔原写顺序和AVX16B重叠、原RET/sentinel、RAX/RSP及逐笔old/new。完整helper仍须单独实际验收；原guard保持，stock controls和自然GC继续独立。

## 实际静态复核与退出

`audit-results.private.py` 仅纯Python读取已存在报告、原bytes和112pin，独立核root SSE35/三写/ret、原AVX20/guard、这次6B/width4/invalid10和CPUID四寄存器/AVX0、XGETBV未执行、两次source稳定及绑定转发接口。它不导入/构造Unicorn，不执行CPU。两fresh审计均exit0、各 **21462B**、逐byte一致，SHA **`7dd3de897180454714d381569513e7bfe8b29e0046a255892c334bd224542674`**；不是第二次CPU试验。主线可仅作静态第三复核：

```powershell
python -B local/migration72/d02-resume-20261110b/a-clear48-backend/audit-results.private.py --output local/migration72/d02-resume-20261110b/a-clear48-backend/audit-main-third.private.json
```

本卡只新增本文及私有计划/诊断/静态审计/原件，没有新增受跟踪tool/test，未改旧驱动、guard、控制值、package、库版本或其他源码，未Git。没有target/live/SDK/客户端/DB/JVM/共享构建或部署，不需GS重启。当前按AVX0停止并交回释放max；自然GC/Default/第二allocation返回、完整Avatar/g2/Scene和D-02均不晋级。

主线只作静态第三审计，21462B报告与两fresh逐byte同SHA `7dd3de897180454714d381569513e7bfe8b29e0046a255892c334bd224542674`；没有重复CPU。后续[新stock](CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md)确实选择AVX，故需准确新后端规格，不能以SSE成功覆盖。
