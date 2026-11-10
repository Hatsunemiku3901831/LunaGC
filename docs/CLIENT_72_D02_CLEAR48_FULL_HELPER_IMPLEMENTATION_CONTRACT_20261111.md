# F-03d3f4d2 / CLEAR48-NATIVE-904-IMPLEMENTATION-CONTRACT

主线已完成两条人工路径的集成验收。下文保留实现前冻结范围，BOOT 补充、实际构建及原始结果见 [人工首组验收](CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md)。

2026-11-11。**完整 904B 首组实现合同已冻结，可分 Child、Parent、Validator 三包；本卡没有实现、构建或执行 helper。** 本组仅新人工 SSE-long 与原人工 AVX-long 各一个新 child；完整 904B 原样复制，允许实际执行的原路径分别为 37、40 条。两条路径合计允许 63 个不同原 PC，不代表 176 条路径全覆盖，更不代表完整 Avatar。

## 中文有界计划与当前前置

目的：把[已有完整规格](CLIENT_72_D02_CLEAR48_NATIVE_TRACE_SPEC_20261111.md)收敛为唯一共享 ABI、握手、bootstrap、原指令/EA 目录、raw 事件/结果、独立校验和互不交叉的实现包。先读工作规范、README/Project/Harness/迁移/部署，再核既存 904B/112pin、[原 6B 能力](CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_RESULTS_20261111.md)、[三 witness 实际结果](CLIENT_72_D02_CLEAR48_TRACE_RESULTS_20261111.md)、[v2 退出合同](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_CONTRACT_20261111.md)、[接口审阅](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_INTERFACE_AUDIT_20261111.md)及 GC/Default/完整构造缺口；只读已有文件和 SDK 声明。

Sol max 原中文 GitHub 计划 SHA `e2771659f9706423d1c20ae79fe5db791bbe4e2439a66e86ca7f13d76e70fbfe`及 Terra max 的 [README/分支/API 固定成果](CLIENT_72_D02_CLEAR48_TRACE_GITHUB_20261111.md)直接复用。本续项无新的准确外部来源缺口，不重新全网检索或克隆。没有复制 TitanEngine 代码/运行库；其完整 context、软件断点和 RIP 修正仍不适用。

本卡先写私有中文计划，冻结原件，再作纯文件拼回、地址/布局/预算审计，最后交可直接分工的规格。可写只有本文与 `local/migration72/d02-resume-20261110b/a-clear48-full-helper-contract/`。旧源码、实际 header、四旧 JSON、Project/Harness/HANDOFF、其他包只读；Git/公共索引和按用户规则的最终交接由 root 统一处理。本卡不招募，不等待 C 资源包，也不等实现或现场占用 max。

| 已接受前置 | 限定证据 |
| --- | --- |
| 原 6B、真实正常返回/SEH | 原能力两 fresh 及独立 UD2 已经主线验收；不在本卡重跑 |
| main6 三 backend witness | root 37 纯门、新 build/PE/依赖后，W1/W2/W3 顺序各一次真实通过；汇合 SHA `71824960ea1aa0f8c1542df8b46607566f970e3fb2e23c63f6de9223202f77f2` |
| W1 / W2 | 真 CALL/MOV64/RET 共 3 退休、OS0；原 23B leaf 共 7 退休，两笔真实 32B、完整 288B/guard、OS0 |
| W3 | 只 CALL 退休 1，真实 UD2 未退休，parent30/child90、cleanupComplete=true；只是预期负证人 |
| 原源/独立审核 | parent SHA `53a0a42cc8079677db3657ab81f9d06c9918fe243a2ac32d50110d4c9882db13`；validator SHA `50d71da7f89486c753edba030e445871137f9f551dccc837b2b2b430910f71ad`；root 依赖 SHA `11641653fa70bb4a42868436ee6fcd5ef9815f5a2860b5d972946d3ee5e8d50e` |

这些前置允许规划新 helper，实现不能把 W1/W2/W3 的旧 case1/2/3 或 256B/128B ABI 扩标签冒充完整 helper。前三次失败 W1 原件和 false 结论永久保留。

## 原始覆盖及仍缺的完整构造

[完整构造原账](CLIENT_72_D02_GC_HELPER_20261110.md#完整avatar构造依赖先于单块出口)已列全 20 个直接 allocator site / 12 个 usage 根和全部正常 call 边。这是静态源覆盖，不是自然执行次数；新 helper 即使通过也没有供应其余存储或所有权。

| usage RVA | 直接 allocation site（完整原地址） | 主要后继依赖 |
| --- | --- | --- |
| `588ca20` | `15183fe84`、`151840256` | 两个独立 List/items，首块不能同时充第二对象 |
| `5897cf0` | `15183fea8` | 第二 HashSet、零参 ctor、Default |
| `58fd7c8` | `15183fec8` | `157d7fe90`及原 `call rsi` |
| `588f388` | `15183ff21`、`15184029c` | items→对象+10→Avatar+68/+60 |
| `58fd7d0` | `15183ff45` | 原 `call rsi`→Avatar+1c0 |
| `58fd7d8` | `15183ff81`、`15183ffc1`、`151840004` | 三个原间接子构造→Avatar+50/+1c8/+40 |
| `58fd7e0` | `151840044`、`151840082`、`1518400c0` | MI/RGCTX→对象+10→Avatar+88/+1f0/+b8 |
| `58fd7e8` | `1518400fe`、`151840176`、`1518402c0` | static cache / 条件 producer→Avatar+1f8/+200/+1e0 |
| `58ed020` | `1518401ee` | MI/RGCTX→对象+10→Avatar+1d8 |
| `58a31c8` | `15184022c` | wrapper→Avatar+b0 |
| `58a31c0` | `15184023b` | `14ff00c60`子构造→wrapper+20 |
| `58d6e70` | `15184027a` | `15152d870`子构造→wrapper+18 |

原首 pop 的 200 条副本路径已经限定验收；[第二 L64](CLIENT_72_D02_SECOND_GC64_20261111.md)及[新 stock 输入](CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md)已给真实 request56/bin4/L64/flags8 和 helper 控制形状。Default 仍只是 nonzero opaque；自然 allocator 线程/GC 所有权、第二 allocation 返回、Default 消费、items 写入、其余分配及实际子构造均未闭。

正常体还有 `157d97690`、`157d7fe90`、五个 `call rsi`、三个 `140517680→140538230→159b6e720` 条件链及上述两子构造；错误边 `1404c2be0/140524010` 与末 INT3 也不能 host 成功。旧 cold 普通10294、enum、fresh、自然 String Hash/FindEntry tuple/caller/trigger、正式 Avatar g2、Scene 和 D-02 缺口不变。

## 固定原输入与首组 case

原 helper `[141fdf9c0,141fdfd48)` 共 904B/176 条，SHA `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31`。旧 manifest SHA `73b50cba66ee3aed2a7b2c3f1b3a772104da6219686b3dd9fccc915f30c36915`，原 12B pdata SHA `d56e1bc42b22adb681a4c0b03e40b3ada8cb149f4602bde5d9796082a283e201`；pdata 只保留来源，不作为新 arena unwind 注册。旧 112pin manifest SHA `f02965fbe282ddfac9373df863a456f2ca26af4792dcfaf1db99c8ff1f2e9310`及全部项保持。

| 新 case | 显式人工输入与来源 | 真实执行期望（仍为设计） |
| --- | --- | --- |
| 257 / `SSE_LONG_ARTIFICIAL_V1` | 新变体 I2、T0=47、F0、SSE DWORD `01fdfd35`；T1 和 AVX table 未提供、禁读 | helper37 + wrapper CALL1；控制4读17B、RET1读8B；d35/d3c/d43 三笔16B |
| 258 / `AVX_LONG_ARTIFICIAL_V1` | 旧人工 AVX-long 原值 I3、T0=47、T1=48、F0、AVX DWORD `01fdfb94`；SSE table 未提供、禁读 | helper40 + wrapper CALL1；控制5读25B、RET1读8B；b94/b9b 两笔32B、16B重叠 |

两 case 均只一个新 child、一个 arena、一次真实 helper CALL，先 SSE 完整接受才执行 AVX。旧 SSE I2/T0=48 的35条原件保留；不为本组修改它。stock-derived I6/T0=524288/AVX table 是独立后继，**不纳入本组，不补未知 T1/F**。captured copy 写、原 pop/第二 allocator、Default、自然 GC 和完整 Avatar 都不因本合同授权。

## 新 ABI、握手与 root 唯一 header

新实现目录建议 `tools/client72_d02_clear48_full_helper/`；新私有 build/run 根为 `local/migration72/d02-resume-20261110b/a-clear48-full-helper-owned/`。旧 witness 框架可继续按原 hash 复现。

`shared.h.proposal` 仅为本卡私有 ABI 提案，**没有改实际 header**。root 先由冻结 `interface.v1.private.json` 发布并冻结新 `shared.h`，再派 Child/Parent；两包只能消费同一 header，不能自行添字段或修 offset。自有结构 pack8，固定宽度类型，无 WinSDK 类型嵌入，不手写 CONTEXT/DEBUG_EVENT/MBI 偏移。

| 结构 | 固定布局与含义 |
| --- | --- |
| `C48FullDescriptor` 384B | magic `C48FULL1`；0..39 身份/版本/size/case/state/runId；40..79 imageBase/PID/TID、wrapper labels/sourceBytes；80..103 Bo/reserved/helperEntry；104..159 buffer reservation/page/known128/H/D；160..175 stack bounds；176..247 page/host/状态/CPUID/XCR0；248..343 source/catalog/cases 三个 raw SHA32；344..383 显式 reserved0 |
| `C48FullBootstrap` 256B | magic `C48FBO01`；descriptor RVA、mask3、PE SizeOfImage；child EXE/header/source/catalog/cases SHA32；192..211 五 wrapper RVA；212..231 descriptor384/helper RVA/904/reserved；232..247 exitPolicy2/eflagsPolicy2/caseCount2/wrapperBytes27；248..255 reserved0 |

机器 JSON / header 提案共 67 个字段 offset 和两个 size 已作纯文件算术核对，未执行 C `_Static_assert`；实际编译断言归 root 新 build。case mask bit0/bit1 分别对应257/258，不把数值257当 mask。所有实际 RVA 留待 root 新 map/PE 生成；不使用旧 main6 label、child 自报地址或搜索一个 CC 代替独立定位。

child 准备完毕才写 READY。parent 在 Arm 实读384B，要求版本/大小/run/case/本次真实 PI 身份/imageBase、全部 labels/指针、source/catalog/cases SHA、host gate 与 root bootstrap/编译 epoch 一致，保留完整 raw384。reserved 是本自有结构显式0；不代表未知页已知。descriptor 不是原 GC 对象，不包含虚构 TLS 或客户端 this。parent 不写 descriptor。

parent 参数固定 `--child-exe ABS --bootstrap ABS --helper ABS --case 257|258 --run-id 32lowerhex --output ABS_NEW_DIRECTORY`；child 固定 `--helper ABS --case 257|258 --run-id 32lowerhex --output ABS_CREATE_NEW_CHILD_RESULT`。入口一次校验路径/参数/大小/hash/数据清单，不提供 PID/attach、任意 entry/RVA/长度、改 case controls 或重试参数。

source904 是忽略目录中的固定只读文件。child/parent各在入口核其准确904B/SHA；原客户端904B不嵌入受跟踪 header。root 从冻结 JSON 产生私有 `catalog.generated.h/cases.generated.h`，原176条/63允许PC、case和数据hash都必须逐项一致；两生成物和 `build-epoch.h` 是实际编译依赖。它们只提供常量/记录，不是 x86 执行器。

## 稀疏 arena：物理页与逻辑允许域

保留原 RVA，不 relocation 904B。令原 B0=`140000000`；新 Bo 由一次 `VirtualAlloc(NULL,06d20000,MEM_RESERVE,PAGE_NOACCESS)` 得到，要求64KB对齐；不试固定历史地址、不失败换地址/机制。当前合同只接受 page4096、granularity65536，其他形状按不支持停止。

| arena 页 RVA / 物理权限 | 唯一已知逻辑字节 |
| --- | --- |
| `01fdf000` / RW初始化→RX | 只复制 `[01fdf9c0,01fdfd48)`904B；核复制后完整SHA，再保护并 FlushInstructionCache 准确904B |
| `056af000` / RW初始化→RO | I@`056afdd0`4B、T0@`056afdd8`8B；仅AVX-long提供 T1@`056afde0`8B |
| `05eee000` / RW初始化→RO | F@`05eeeb08`1B，显式人工0 |
| `06d19000` / RW初始化→RO | 本case唯一DWORD：AVX@`06d190f4`或SSE@`06d19140` |

只 commit 这四页，arena其余保留 MEM_RESERVE；不整页 memset/poison、补 NOP、清未知页或初始化另一侧 table。OS commit 的初始0及页 padding **不是输入或允许域**。controls只按本case准确17B/25B写入后只读。

原规格“窗外9a0没有code页”的字面说法在4KB页粒度下不成立：`01fdf9a0` 与 helper 实际同处 `01fdf000` RX页。它的原bytes没有提供，也不在逻辑 code span/指令许可里。阻止该边必须在原 `aac/c80` 根据真实 flags 计算 selected next **执行前拒绝**；不能依靠缺页、填NOP或先执行外跳、下次trap后才声称预防。页保护与逐PC授权是不同证据。

Arm停点新增且仅有13次有界 `VirtualQueryEx`：arena四commit+五reserve间隙共9段，buffer三段共3，真实call栈槽/影子空间所在段1。原SDK `MEMORY_BASIC_INFORMATION` 类型及实际返回尺寸/原字段记录，不手写布局；匹配 AllocationBase、范围、State、commit 的实际RX/RO/RW及无GUARD，不扫描其他地址。MEM_RESERVE 的 Protect 不假定为0，设计JSON的null表示“不指定固定期望”，raw DWORD仍照实保存。查询元数据不是读取padding，更不授权CPU操作数。

buffer另一次reserve64KB，仅commit offset1000的一页RW；knownBase=该页首，H=knownBase+32，D=H+10h，故H mod32=0。只初始化准确128B：前guard32=`a1`，H64=`01..40`，后guard32=`a2`；H前16B也是保护区。该页其他字节仍未知，前后reserve不提交。不是伪对象、捕获heap副本或TLS。正常返回后 child 正常释放两个reservation；失败由parent仅终止该owned child回收。

两 case 都沿已验次序真实 CPUID leaf0/leaf1各一次；XSAVE/OSXSAVE/AVX位齐备后才真实XGETBV一次，XCR0必要位6通过才准备执行。复用13B owned Xcr leaf；不在这一步调用原6B再做新能力轮。策略/分配/权限/flush失败只记录一次，不关闭保护、不放宽RWX或注册猜测CFG目标来取pass。

## 27B wrapper、真实栈槽与退休事务

新 `C48FullWrapper(entry,D)` 固定自有bytes如下，ASM使用真实 FRAME/allocstack28 unwind，root核新PE/map/caller与实际标签：

```text
4883ec28 4989cb 4889d1 31d2 41b830000000 cc 41ffd3 4883c428 c3
Begin+0; Arm+18; Call+19; AfterCall+22; End+27
```

wrapper设R11=entry、RCX=D、EDX=0、R8=48，然后自有INT3、真实CALL R11。Arm raw ExceptionAddress须等root Arm label，实际Context.RIP须已经是Call label；原TF要求0。差异停止，绝不调RIP纠偏。

以该停点实际RSP=C，要求C mod16=0、`[C-8,C+32)`属于本线程真实已提交栈；stack bounds来自child原TIB，父方另核实际页元数据。CALL事务先读真实 `[C-8,C)` old8，再核真实target=Bo+原entry，执行后唯一匹配step必须在helper entry、RSP=C-8、槽new=本次AfterCall。不会预写return/sentinel，也不放客户端 `140563d8a` 到栈上。CALL的8B是harness写，独立于helper清零账。

每个原指令只在准确已支持PC、准确case的下一ordinal处准备；父方实读该条code bytes，使用该停点真实CONTROL|INTEGER和原编码计算EA/宽度、flags分支或目标，先核完整允许span，保存old，再建立至多1个pending。只有通过全部preflight、TF Set与回读，才对该停点Continue。EA前置门是执行授权，不是反复入口判空。

下一真实first-chance SINGLE_STEP必须同owned primary PID/TID、唯一pending、ExceptionAddress=实际post RIP、实际RIP=已前置核定的唯一next。先取真实post、必要真实new，再封事务；不能把下一PC当上条store PC、用线性加size代替分支/CALL/RET或从最终零buffer补造事件。非volatile GPR在helper返回时与Arm原值一致；关键RAX/D、LEA所得R10/Bo、对齐R9=-16、RCX=H+20h、RDX=16/R8=32及table零扩展结果逐项核实际post。

pending继续三态：未尝试Continue为false/not_executed；已尝试Continue但无可靠退休/确定fault证据为null/retirement_unconfirmed；唯一已匹配实际step并完成post证据才为true/retired。明确在pending原PC处观察到非法指令/访问fault时保持false/unretired；其他异常/错PC不能猜成“未执行”。真实Continue/Terminate/Close BOOL先更新已发生的生命周期，再检查写日志结果；日志失败仍阻止接受，不重放API或撤销已发生事实。

原RET只允许真实读取同一 `[C-8,C)`8B，其值须是本次AfterCall；真实post必须RIP=AfterCall、RSP=C、RAX=D。在wrapper下一条执行前保留这些值、最终128B和所有事务；pending清空，同stop只恢复原TF并成功回读，再正常Continue，之后才进入WAIT_EXIT。其后wrapper正常add rsp28/ret到C，不将其当原客户端caller/第二allocator返回。

Get仅 `CONTROL|INTEGER`；Set构造CONTROL-only副本，只改TF，SegCs/SegSs/Rsp/Rip按同stop原值提交。不Get/Set FLOAT/XSTATE/debugregs，不改GPR或向量；普通CONTEXT不冒充YMM。

```text
S = (B & ~0x100) | requestedTF
R == S  或  (S.bit1==1 且 R==(S^2))
```

只有已批bit1单向1→0回读表示；提交侧不能清bit1，不能mask其他位、接受反向变化或回写“归一flags”。15 GPR与其余控制值仍精确不变，保存完整B/S/R/XOR和实际Get ordinal。

## 原PC、EA与精确允许访问

完整176行目录保留ordinal、原PC/RVA/offset/size/bytes、助记符/operand、显式/隐式数据方向和宽度、flow与case许可；拼回恰为904B。原窗有8项RIP/LEA，其中本组活动7项；额外 `ad5` 的T0读取所在大循环路线未授权，即使其目标span已知也必须先拒绝。LEA和memory-shaped NOP没有CPU数据读。任何未列PC/方向/宽度、loop/NT store、helper内CALL或另一条原RET都不执行。

| 原PC（均以 `141fdf` 为前缀） | runtime EA / 宽度 | 允许域/条件 |
| --- | --- | --- |
| `9c6` | nextRIP−01fdf9cd，只有LEA | 实际结果Bo，不读数据 |
| `a86` | nextRIP+原disp /4读 | Bo+056afdd0，I |
| `a93` / `c70` | 各自nextRIP+disp /8读 | Bo+056afdd8，分别AVX/SSE的T0 |
| `a9c` | nextRIP+disp /8读 | 仅AVX-long，Bo+056afde0的T1 |
| `aa5` / `c79` | 各自nextRIP+disp /1读 | 本case F，Bo+05eeeb08 |
| `b49` | R10+R11*4+06d190f0 /4读 | R10=Bo、index1，恰Bo+06d190f4，DWORD94fbfd01 |
| `cfd` | R10+R11*4+06d19138 /4读 | R10=Bo、index2，恰Bo+06d19140，DWORD35fdfd01 |
| `b94` | RCX+R8−20h /32写 | AVX，恰H+20h..40h |
| `b9b` | RAX /32写 | AVX，恰H+10h..30h |
| `d35` | RCX+R9−20h /16写 | SSE，恰H+20h..30h |
| `d3c` | RCX+R8−10h /16写 | SSE，恰H+30h..40h |
| `d43` | RAX /16写 | SSE，恰H+10h..20h |
| `ba2` / `d47` | RSP /8读 | 只真实CALL返回槽，分别AVX/SSE |

`a8d`按实际CF分SSE/AVX；T0=47使`a9a/c77`不跳过F前置，T1=48使`aa3`不跳过F；F0使`aac/c80`不外跳；adjusted R8=32使`ad3/ca1`跳过大循环。父方以真实pre flags选next，须恰等本case目录后继；任何意外分支在其执行前拒绝，不只要求目标“还在904B里”。

table DWORD始终是原U32 RVA，不改VA、不符号扩展、不patch间接跳。原load/add后实际R11须等Bo+本case唯一tail，`b54/d08`执行前再核；另一条窗内入口、padding、窗外9a0均拒绝。每事件保存runtimePC；仅源904B的准确指令边界映射`RVA=PC-Bo/originalPC=B0+RVA`。wrapper/system/Arm/AfterCall用各自EXE/module label，不能强投原PC。

SSE按d35→d3c→d43，三笔各16B，sum/union48、intersection0。AVX按b94→b9b，两笔各32B，sum64/union48/intersection16；第二笔执行前的重叠old必须等第一笔实际new，非重叠old保持该时刻人工payload。每笔完整old/new分别实读并绑定原PC；不拆8B后端hook、不先memset或从终态反造。完整128B首尾检查H前16B及全部64B guard不变。

这些是架构逻辑operand的执行前/退休后证据，不声称观测总线、cache或AVX物理写次序/原子性。fault可能已有partial写；保留已完成事件、原pending old/实际fault context，不填完整new或回滚后称未发生。

## Raw事件、结果与预算

新 `epoch/parent-result/child-result/validation/root-run/dependency-review` 都使用 `c48-full-helper-…-v1`，raw事件schema为 `c48-full-helper-events-v1`；旧三个case及旧ABI/schema一律拒绝。`events-and-results.v1.private.json`冻结完整字段形状、purpose/枚举和结果required。

raw kind沿已验事件协议：epoch、ownedChild、wait、event、continue、get、set、calibration、memory、transaction、cleanup，新增有界pageQuery。成功wait的实际PID/TID/code与紧随event及其Continue双射；timeout/error身份和stop为null，不回填primary。事务增sourceOrdinal/runtimeRva/originalPC、实际pre/post Get ordinal与preflight结果；sourceOrdinal为原0..175，wrapper三映射字段为null。完整原ExceptionRecord、firstChance、flags/参数与raw union/句柄来源继续保存。

所有pointer/u64寄存器16位lowerhex，source RVA8位、EFlags8位，DWORD/WORD为严格整数；未知用null+status，实际零不能冒充缺失。memory保存请求地址/宽度/准确允许span、真实BOOL/actualBytesRead/rawHex与ordinal；短读失败，不补齐。inspector读取与CPU操作数、code fetch、wrapper栈写分账，不能称CPU read hook。

正常parent结果须有完整sourceProof、case/run/实际PI身份、每类预算、source/总退休、控制读、两/三笔写及sum/union/intersection、真实RAX/RSP、原失败/pending、EXIT/Continue/signal/GetExit/PIclose及cleanup。child结果只在真实C continuation后CreateNew，含host原值、准确initial/final128、callCount1/返回RAX、setup/release API原记录和同epoch身份；正常OS0只能在结果写入/flush/close成功后返回。文件内不自证尚未发生的该文件关闭成功；root核真实OS0和完整artifact。

| 每case硬界 / 正常精确计划 | SSE-long257 | AVX-long258 |
| --- | ---: | ---: |
| 真实helper CALL / child次数 | 1 / 1 | 1 / 1 |
| helper退休 / 含wrapper退休 | 37 / 38 | 40 / 41 |
| Get CONTROL\|INTEGER上界 | 79 | 85 |
| Set CONTROL上界（每条TF+末恢复） | 39 | 42 |
| 正常inspector RPM准确次数/请求B | 56 / 1878 | 58 / 1929 |
| RPM硬上界 / 总请求B / 单读B | 64 / 4096 / 904 | 64 / 4096 / 904 |
| pageQuery / pending | 13 / 1 | 13 / 1 |

两侧共享每run10000ms总deadline（CreateProcess前建立，含启动及退出收口）、Wait≤50ms、128 debug event、原source退休硬界128；正常仍须精确37/40条，不能凑128条。128 event沿已验v2，比旧904B提案256更严格；TRACE/WAIT_EXIT/TERMINATING共用不重置。Get上界=2×总退休+3，Set=总退休+1，包含systemBP、Arm、每次post/回读和末恢复，无额外vector Get。

RPM正常计划是：descriptor384、wrapper27、完整code904、initial128各一次；每条含CALL实读code（SSE共170B、AVX181B）；控制4/17或5/25；CALL old/new各8；RET8；store old/new共96或128；final128一次。不额外预读未知T1/F、整页、整表或栈；故确切56/1878、58/1929。失败预算仍按实际已尝试调用/请求宽度计数。

## 退出、失败与停止恢复

完整沿 `c48-owned-exit-lifecycle-v2`，补充合同SHA固定 `cf52cfafa00eab47316a756a5e04a1edc59ca2b5606d4fa89d6cc2cb552105d8`；其实际union/BOOL顺序和资源责任已获独立审阅。TRACE包括Arm/PENDING/POST只允许primary；新thread/late DLL/未知事件立即失败。只有准确全部事务、RET/RAX/RSP/128B、pending清空、同stop TF恢复/回读及Continue成功，validator独立推出WAIT_EXIT边界，不能相信phase标签。

WAIT_EXIT仅同owned PID的2/4/5/6/7/8有限通知；type2建立实际TID/borrowed handle来源，其他通知必须已有live来源。primary可先退出，最终EXIT可来自新线程；不补造末线程EXIT_THREAD、不从DLL名/地址推线程起源。7/8只记原metadata，debug string不读payload；已验三witness未观察7/8，其覆盖仍只是人工边界测试。

边界后禁止Get/Set/RPM/pageQuery/新事务/退休。正常必须实际EXIT Continue→PI signal→GetExit与event同值0→两个PI真实Close、完整日志/生命周期、child C结果和root receipt，再parent0。借用event process/thread句柄不手动Close，只由validator按实际创建来源+成功EXIT Continue导出`api_contract`释放；不伪造内核Close BOOL。有效CREATE/LOAD hFile仍按原身份/path/size门手动关闭一次，NULL与未观察分开。

首失败/pending粘住，停止所有原指令授权；只对本次PI.hProcess一次Terminate90，真实TRUE后才能cleanup-only Continue原owned outstanding和有界drain。未知TID/foreign PID/额外异常/日志失败不能凭物理无残留改成cleanup通过；foreign PID不Continue。Term FALSE不换OpenProcess/detach/VEH或重试。正常case不能有Term/cleanup-only；full helper没有“预期负接受”，任何fault都失败，即使此前已清一笔。

原helper xdata未知，不新取、不补、不 `RtlAddFunctionTable`。unknown fault保存原首异常及当时真实context、pending/sourcePC/old和已完成事件；不让故障指令重试、改RIP、跳过、强回AfterCall或借C SEH包装成功。正常owned wrapper的真实unwind不等于原helper异常展开已验。

分配/执行策略拒绝、source/layout/真实EA/flags/间接跳、未知store/call/loop、短读、BOOL/日志、预算或退出失败均原样停本包。安全/Daybreak按用户要求记录队列后停包，不换模型/编译器/机制。首组失败不第二fresh；首组全过也须root另批才第二fresh，不顺取stock/captured copy。没有任何技术条件要等C包才能完成这份合同。

## 三包文件归属、构建与独立校验接口

| writer / 任务 | 唯一写域 | 必须交付 |
| --- | --- | --- |
| Child / `CLEAR48-FULL-CHILD-V1` | 新目录`child.c`、`full_wrapper.asm` | 入口固定source/host门、精确四页/128B、384B握手、27B真调用、真实C结果/释放；不写parent/header/generated data |
| Parent / `CLEAR48-FULL-PARENT-V1` | 新目录`parent.c` | 复用已验v2/EFLAGS源行为；新bootstrap/schema/budget；13查询、逐PC/EA执行前门、真实事务/写账/RET及完整退出；不改child/validator |
| Validator / `CLEAR48-FULL-VALIDATOR-V1` | 新`tools/client72_d02_clear48_full_artifacts.py`、`…_validate.py`及各同名前缀test | 纯artifact bootstrap/PE/map审核与独立raw重算/人工变异，不执行程序/API/模拟器，不改输入或其他包 |
| root | 新目录`shared.h/build.ps1`、新`tools/client72_d02_clear48_full_run.py`，全部私有生成/现场/公共状态 | 唯一header与编译epoch，实际build/PE/caller/unwind/依赖，冻结源前后，串行首组一次，独立checker+raw第三审核，本地Git不推送 |

root构建接口保留 `-BuildId` 新目录拒绝复用，便携MSVC/SDK来自已盘点原库存且开工重核；同步取得实际工具退出码。staged新shared.h/child.c/full_wrapper.asm/parent.c/build.ps1，加实际生成build-epoch/catalog/cases均独立pin；所有实际include/编译器DLL/link库与日志、EXE/map逐项核。旧源/四JSON不改，新依赖review必须显式绑定本合同机器规格、原904B、旧112pin、退出补充及真实构建输入；sourceProof/receipt前后集合不得漏声明依赖。

新artifact CLI：`--build ABS_BUILD --header ABS_ACTUAL_HEADER --contract-dir ABS_FROZEN_CONTRACT --output ABS_NEW_BOOTSTRAP_DIR`，只读该新自有PE/map和固定数据，产出256B bootstrap、mirror及artifact审计。root独立核27B连续bytes、labels、wrapper prolog/真实unwind、C caller参数/栈、Xcr13B/调用点、descriptor RW非exec及image bounds。客户端PE/新code/pdata/xdata不在其取证范围。

新validator CLI：`--run ABS_RUN --build ABS_BUILD --bootstrap ABS_BOOTSTRAP_DIR --dependency-review ABS_REVIEW --contract-dir ABS_FROZEN_CONTRACT --output ABS_NEW_JSON`。入口一次严格schema/重复key/源hash/ABI/receipt校验后，按raw序号重建身份、页范围、primary trace、每条bytes/preflags/EA/next、Set回读、每笔old/new/重叠、RET及退出生命周期。sourceProof精确键由interface JSON给出；epoch/summary精确相等，哈希及实际label另与bootstrap/root review关联。不能只相信allowed/retired/normalTracePassed/cleanup标签或child自报。

root runner只新建隐藏的本次parent，native parent同一主线程CreateProcess/Wait/Continue自己的child，不attach，不新建debugger工作线程。新run目录CreateNew，绑定创建时间/PID/TID/实际handle和EXE完整SHA；完成保留raw/child/summary/checker/root receipt及全部before=after。旧ready/PID或同build不能代替新run身份。本组不需要客户端ready、无客户端/DLL/DB/服务部署；文档/离线工具无需GS重启。

纯门至少包括：两case及64位输入边界/完整非零初始128；176连续/错误bytes/source/ordinal/schema/混旧case；错误RIP/LEA/EA/宽度/未知padding或T1/另一table；badDWORD指向另一已知PC；外跳/loop/NT/call执行前拒绝；漏/重复/错线程step、假phase/错误BOOL、TF提交/回读反向或其他位/GPR变化；CALL old/new/RET同槽与RSP、AVX第二old重叠和各guard变异；page元数据/未知reserved Protect非零；v2 2/4/5/6/7/8所有顺序/来源和文件/PI关闭；短读、日志失败、pending三态、预算/退出90或child结果缺失。synthetic PE/raw样本必须标synthetic，不能生成native已通过的替代证据。

实施顺序：root先发布新唯一header/生成约定→三包独立实施/纯门→root汇合新构建/实际PE依赖与source epoch→一个SSE-long child及独立完整验收→一个AVX-long child及独立完整验收→汇合限定结论。旧三backend不因本合同自动重跑，完整组没有新的UD2资格轮。任何新准确资料缺口只列有界中文来源计划交root处理，不能临场扩执行。

## 本卡产物、实际审计与交回

私有根简称 `a-clear48-full-helper-contract/`；所有机器路径/原客户端bytes/原raw/SDK副本留忽略目录。共享本文只留脱敏规格与摘要。

| 冻结产物 | bytes / SHA-256 |
| --- | --- |
| `catalog.v1.private.json` | 103431 / `02d1803ec7afd6162c23584a76315adaedbeeab81fd83214a76a32a50ecc491e` |
| `cases.v1.private.json` | 14340 / `ae3fe4ec1634dd7b93e177b739ab9868e0dbaf4c7308e5ab563af8d6add8651a` |
| `interface.v1.private.json` | 12099 / `5c513a0bf18bd84750b38bda40fd019140209673d2f74264305880fd9c761597` |
| `events-and-results.v1.private.json` | 8946 / `795974998cb25d114cefaff53fd7615f0d6c99fe5daf8226662136f8beaf5a26` |
| `packages.v1.private.json` | 4352 / `0fb6dec8888a15e12e143ea0c2d3192087f8f19fdde70ee71075860add5c1d25` |
| `shared.h.proposal` | 7479 / `7cbe71b1ac1af277b8b727804e0d14d22e9e7e4074f453d03329b19c5891d59d` |
| 最终纯文件审计 `pure-file-audit.delivery.private.json` | SHA `e439a9f030cb9f279a2fc8c12001832682ea19785bd7cb89886d64875abbb844` |

本卡实际：冻结54个既存输入及副本，核旧112项全部尺寸/hash；904B/176连续拼回、63支持/113拒绝、两路径37/40、67offset/两个size/预算、9a0同页、main6三原raw序号/3、7、1退休/实际step关系/原checker接受/退出均通过。未运行旧checker或重新native；对已有实际证据只读重算，不追认旧失败。

首次私有设计审计已通过后，收紧描述：MEM_RESERVE Protect不固定为0、AllocationProtect原返回不由reservation参数代填；child结果文件不自证其尚未完成的关闭；raw schema只继承case无关字段，不嵌旧三case资格。新增interface往返断言曾因Python tuple与JSON array直接比较而停止于审计输出之前；仅改为序列化JSON模型比较，原合同值未为通过而改。最终交付审计通过，旧首审文件保留为设计过程，不冒充当前pins。两次路径检索错误（Windows literal glob与误找owned/input-pins）止于只读，随后按准确back-end清单读取；没有原件修改或安全拒绝。

本卡新helper实现/PE/code/AOT/pdata/xdata/literal取证、native CPU、emulator、Debug API/live/REA、进程、网络/Git、构建、DB均0；未改生产、Proto、配置、补丁、资源和旧框架。没有新客户端验收或部署对象，`capturedCopyWriteApproved/naturalGC/secondAllocationReturned/completeAvatar/d02Complete`全部false。

任务出口：**合同成熟，交root发布新ABI后立即分Child/Parent/Validator实施，本max到此释放。** 本卡不等实现、不重复第二fresh或stock后继。root更新Project/Harness及本地提交；后续只有首组完整raw/实际正常OS0与独立校验齐备，才能增加相应owned helper限定验收。

## 主线发布与实施绑定

root第三3449B / SHA `e439a9f030cb9f279a2fc8c12001832682ea19785bd7cb89886d64875abbb844`与最终审计相同；另用host布局模型独立核67offset/384与256B，纯文件解析生成的176条C目录重新拼回原904B。未执行编译断言或native。实际新header仅替换提案注释，7461B / SHA `26fafcb733635adfc62b1a8afcc6a9139ab69142196e7a02bd43fdff2b77ff48`；原ABI字段和旧header不变。

root生成器为`tools/client72_d02_clear48_full_generate.py`，只将冻结JSON投影成忽略目录的`catalog.generated.h/cases.generated.h`，不执行x86。双fresh生成结果逐byte一致：14885B / SHA `74b50f4f91b5d63fba2bb8a169958ef23046a3ce9cb2f8e5675f47b6e0cb205f`及4763B / SHA `5898f72e5779a3cf7041661a27c95e8ef29e4dd2c53b0a988ce9d91e0a4a428a`。

独立私有`a-clear48-full-helper-owned/root-bindings.v1.private.json` SHA `cf25a1a0f16a3b11f08ac719b6197e71aa6fba6a903a709abd86c76197494e6a`冻结生成C类型、宏、输出文件、root回执/预算键及raw范围/MBI字段名称。它只明确实施接口：epoch的sourceProof字段仍位于top-level，summary保留同一字典；memoryPurposeEnum为purpose的唯一目录，原memoryRead.kinds只作描述，不形成第二个枚举；MEM_RESERVE Protect保持原DWORD，不由null期望补0。原六产物/业务语义不改。三Terra按Child/Parent/Validator互斥写域实施，root唯一管理编译、PE/依赖、run与共同字段变更。
