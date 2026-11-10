# F-03d3f4d2/CLEAR48-NATIVE-CAPABILITY-SPEC：单条原AVX的自有EXE规格

## 中文有界计划与当前范围

本卡只规划，不实现工具、不编译或执行任何native/目标CPU，不操作client/live/PID、共享构建或Git。C-mode包已经交回，只读。先读当前AGENTS/README/Project/Harness/迁移/部署及CLEAR48-OWNED/BACKEND、UNICORN-AVX-SOURCES、GC-DEFAULT-STOCK-CAPTURE；复用旧`a-clear48-backend/github-plan.zh.md`、官方12调用台账和主线MSVC14.44.35207四工具完整hash盘点。

新stock I6/T0=524288原条件唯一选AVX；固定Unicorn单条已invalid，官方源审已说明准确拒绝，故不重跑或改I/guard/model。本6B是已有任务的有界后继，没有新GitHub仓库/分支复用方向，不重做大检索或Terra职责。下一实现采用本机已有C+ml64+Windows SDK/CRT，不安装/引入第三方库，不依赖默认PATH的rustup。

1. 固定原helper904B SHA `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31`、原site141fdfab2/offsetf2的完整6B`c4e37d18c001`，及旧失败/CPUID/112pin原件SHA；只读现有原文件，不重取目标窗。
2. 仅为准确ISA与Windows ABI/SEH/API合同补必要primary来源。独立于已用完的Unicorn12调用账：本卡最多8次web工具调用、最多10个具体URL读取与2条定向search query；限Intel官方ISA/编译器文档与Microsoft Learn官方API/ABI，不泛搜仓库或二手教程。先记录必要性/URL/检索预算，再把实际URL/抓取时间/内容边界/SHA留本卡私有目录；固定源已足够则不追加。遇平台安全门禁/Daybreak立即记停止，不重试/换模型。
3. 给出可机械实现的独立Windows x64 EXE：baseline C只做来源/host CPUID门禁与SEH/报告；ml64保留原6B，显式owned YMM输入与前后快照、可核load/store/VZEROUPPER/RET；说明ABI、原指令与wrapper写域、异常和退出。host CPUID缺XSAVE/OSXSAVE/AVX任一即不执行XGETBV/AVX；XGETBV(0)仅有条件只读，不改CR4/XCR0、目标配置或CPU特性。
4. 来源前后/PE原bytes与解码、独占原件、一次call/超时/exit、必要的不同依赖负门禁分别列清。当前只冻结规格和来源manifest；实现、两fresh及主线第三执行需主线另批。原6B能力通过也不等于完整904B/helper/捕获副本/自然GC/Default/完整Avatar/D-02验收。成熟后交Sol xhigh/可用Terra并释放max。

## 当前状态

本有界规格已完成，可交Sol xhigh/可用Terra实现；尚未写C/ASM/runner工具或构建/执行EXE。静态读取核了旧112pin、原904B/6B切片、拟定wrapper/XGETBV leaf的完整byte decode和primary ISA/ABI/SEH来源。`nativeCapabilityPassed=false`、`helperExecuted=false`、`capturedCopyWriteApproved=false`、`naturalGC=false`、`completeAvatar=false`、`d02Complete=false`。新执行须主线另批；本卡交回后停止写入。

## 已有材料与本轮primary来源

原904B位于`a-gc/gc-helper.code.bin`，entry141fdf9c0..141fdfd48；本卡原样核SHA `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31`，offsetf2的6B是`c4e37d18c001`，其单独SHA `7011fa44092f8b088c4f8a22692141e27bd41618b2df4521dd1be5b91f2a4a3a`。旧`input-pins.private.json`18750B/SHA `f02965fbe282ddfac9373df863a456f2ca26af4792dcfaf1db99c8ff1f2e9310`的112项逐一保持；旧一次invalid报告`466c8c1ff21c1b0cda42bf7881414a9e0c5bbfdc3544aba4e12b8a16e951455c`、CPUID报告`9827a36280829717a1f0022c84ef25290f77f32ec91c98fdfc6f79fe2355e459`不改。它们是虚拟Unicorn结果，不能填作新host结果。

本轮准确ISA依据为Intel官方[SDM revision090完整PDF](https://cdrdv2-public.intel.com/874240/325462-090-sdm-vol-1-2abcd-3abcd-4.pdf)：私有原件26361704B，SHA `3686e42931e893b5016c70fb131403795ef173237d073afcfec52755fcd3ea50`。只用于Vol.2C 5-406编码表、5-409的VEX operation、Vol.1 §14.3/14-15 AVX gate；不以EVEX/AVX-512语义替代本VEX。6B解得VEX.256/66/0F3A/W0，opcode18、ModRM C0选择同一YMM0/XMM0、imm8=1：低128位保留旧YMM0低位，高128位复制旧XMM0，即复制旧YMM0低位。它仅需AVX，不额外要求AVX2，不访问数据内存。这是bit copy，不作浮点运算。

host门以该Intel §14.3及Microsoft[CPUID intrinsics](https://learn.microsoft.com/en-us/cpp/intrinsics/cpuid-cpuidex?view=msvc-170)为据；ABI以Microsoft[x64 calling convention](https://learn.microsoft.com/en-us/cpp/build/x64-calling-convention?view=msvc-170)为据；异常以[GetExceptionInformation](https://learn.microsoft.com/en-us/windows/win32/debug/getexceptioninformation)为据。Microsoft网页只固定本轮技术正文的来源/摘要，不冒称完整HTML原件SHA。完整Intel PDF及选页原stream/提取文、摘要边界与实际预算在私有`a-clear48-native-capability/`。

本轮实际3次web工具调用、1次限定官方PDF GET，10次URL读取（含三篇定点复读）、2query，预算到此结束。web PDF因26361704B超工具尺寸上限；一个Intel compiler HTML不可访问，均保留失败；随后仅对同一已知官方PDF作≤30MB/40秒的直接GET并离线提取，没有换来源或继续检索。未遇平台安全门禁/Daybreak。首次PDF提取把CR当LF分隔导致0命中，保留并纠正本地提取；一次研究JSON把6B SHA重用为904B字段，原断言已核904B，首次JSON保留，最终拆成`sha256`与`instructionSHA256`。原材料与旧原件均不变。

## 实现选择与构建前置

首选**已有MSVC C + ml64组成独立Windows x64 console EXE**：C只负责来源、baseline CPUID、门禁、SEH、owned存储、比较与CreateNew报告；独立MASM leaf放精确指令。无需第三方库、Unicorn、JIT、Rust或安装。已有SDK/CRT正常静态链接可用性须由主线构建前核实际环境，当前只证明四工具文件在盘，不声称编译或SDK依赖已通过。

主线原`native-msvc-inventory.private.json`1440B/SHA `b74b6c1b2e82538cfd57f2ccdb9ddd695827c2b83a0b5351c02a747ac074bc00`固定14.44.35207：

| 工具 | bytes | 完整SHA-256 |
| --- | ---: | --- |
| cl.exe | 677776 | `fe251ef50a1545b1b0835ee17b1e785459712b38d79e45b5c1d3d28970a36619` |
| ml64.exe | 646504 | `5caf9b734273a9ccb109eb0465581bd6c9fb67fa52a1cd12cc6d40ee12e40d4a` |
| link.exe | 3252616 | `a364af801a8539e4324d9489313dbf001d959128451fc99a27b24676bbac058f` |
| dumpbin.exe | 23376 | `cfee7ed764b7b0866aae0be12f9c0ad2a7f5aecc4046ba2b7de26335827ca815` |

未来全部source、obj、map/PDB、exe、stdout/stderr和报告放单独新私有子目录；不写旧目录原件或共享构建。通过盘点的绝对工具路径及对应vcvars64在**子shell**建环境，固定实际MSVC/Windows SDK版本、完整argv/退出码/环境来源。清点`CL/_CL_/LINK`等可能追加选项；不得继承未记录的`/arch:AVX*`或`/GL`。C使用x64 baseline（无`/arch:AVX*`）、`/Od /Oi /W4 /WX /GS /MT /Z7`；MASM为独立leaf；链接固定x64、无增量、保留map/symbol和不折叠leaf。具体绝对路径只在私有构建计划，不拷历史PATH。

四个exe hash不等于完整工具链：主线须记录实际使用的编译器DLL、SDK/VC headers与所选CRT/SDK libs；用已有工具的include/link日志形成实际依赖清单并SHA，不扫整机、不安装缺项。C调用点与C的SEH/unwind资料一并审；其新增guard路径先于两个ASM入口，不把CRT/OS内部指令称作全程已追踪。缺工具/头/lib或安全门禁就留诊断停止，不借其它模型/编译器/loader绕过。

## 一次调用前的host门

只能记录本自有EXE所在host的实际值，不能用虚拟Haswell19或stock I6代填。正常流程固定为：

1. baseline C以`__cpuidex(0,0)`记录EAX/EBX/ECX/EDX，要求max basic leaf≥1；再`__cpuidex(1,0)`记录四原值。仅这两次CPUID，不按CPU商品名判断。
2. 要求leaf1 ECX bits26/27/28（XSAVE/OSXSAVE/AVX）均1，即`(ECX & 0x1c000000)==0x1c000000`。缺任一则输出`host-prerequisite-missing`，XGETBV和原6B尝试计数均0；没有force/mask/重试选项。
3. 只在上述条件真时，受SEH保护调用一次ReadXcr0 leaf，ECX必须0，记录完整EDX:EAX组成的64位XCR0。要求`(XCR0 & 0x6)==0x6`，同时具备XMM/YMM状态。否则报告不可用并停止，原6B计数0。全值保留，不能只报告true或猜XCR0=7。
4. 不执行XSETBV/修改CR4、系统boot/进程feature mask、Unicorn模型或项目配置。不以XGETBV/CPUID通过代替原6B能力；随后仍只有一次受控Probe调用。

为了让baseline C无需`/arch:AVX`也不依赖编译器对`_xgetbv`的开关，拟定独立leaf ReadXcr0：`31c9 0f01d0 48c1e220 4809d0 c3`（13B/5条），分别清ECX、XGETBV、组合EDX:EAX到RAX、RET；只改volatile RAX/RCX/RDX，RSP不作软件调整。XGETBV site相对入口+2、RET+12。此序列已静态解码，尚未嵌入PE或执行；主线也须对其实际PE核bytes、label/RVA和decode。

## 精确23B Probe与原6B位置

对C暴露固定原型`void Probe(const unsigned char *input32, unsigned char *before32, unsigned char *after32)`；标准Win64三指针为RCX/RDX/R8。不用`__m256`按值参数、vectorcall、返回vector或内联C汇编。MASM leaf不分配栈、不改非volatile寄存器、不call其它函数。规范byte序列如下，既是设计约束也是未来PE门禁；它目前只是规格，未构建：

| 相对入口 | bytes | 指令与数据权限 |
| ---: | --- | --- |
| +0 | `c5fe6f01` | VMOVDQU YMM0 ← [RCX]，只读owned input32的32B |
| +4 | `c5fe7f02` | [RDX] ← YMM0，写owned before32的32B |
| +8 | **`c4e37d18c001`** | 原VINSERTF128 YMM0,YMM0,XMM0,1；0数据内存 |
| +14 | `c4c17e7f00` | [R8] ← YMM0，写owned after32的32B |
| +19 | `c5f877` | VZEROUPPER，快照已完成后处理volatile upper lanes |
| +22 | `c3` | 自有leaf RET，仅读owned当前栈顶8B返回地址 |

整个23B SHA `745debaba204f25ccca49722a7ee7f4595d875e07bbc6da98201c101445138d1`；入口/原site/RET/End四个public label（End=+23）进入map，当前没有实际RVA。原6B用显式DB原bytes嵌入，不能改写为等效指令、删除/改imm或把6B裁成4B。wrapper和ReadXcr0也必须与表中bytes一致；若ml64输出不同，停在构建产物门，先评审差异，不悄悄更新期望SHA。

未来主线从map/符号定位entry RVA，按PE section映射读取该单一23B及原site+8的6B，记录file offset、RVA、section权限、完整SHA、逐指令bytes/长度/operands；固定Capstone和dumpbin作独立解码对照，要求连续覆盖到唯一RET+22和End+23，无额外call/jump。child运行前只读自身对应23B/13B并保存，与固定PE一致；ASLR只改变base，不改变本相对布局。不要全文件搜到一处6B就当执行site。

## owned输入、输出与ABI

三个独立owned block各96B：32B前guard、32B payload、32B后guard，block按32B对齐，从而payload也对齐。input为`01..20`（32个非零递增字节），低lane `01..10`、高lane `11..20`；before/after payload分别用不同非零毒值初始化，guards每块前/后也用不同固定非零模式。三指针互不重叠，完整288B初始原件保存。

真实pre快照必须逐byte等于32B input。原6B之后的真实post32必须为：

```text
01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10
01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10
```

参考来自固定ISA和独立常量输入，在call之前建立；不能从实际post反推expected，或先写expected到输出。input payload32B和全部192B guards前后不变；只允许Probe两笔原wrapper store各32B到两个payload，总64B，按PC分别核，不把它叫GC的48B写。原6B自身无数据read/write；RET栈8B与C/CRT正常owned stack另列。结果保留三个block完整before/after，不能只留pass/hash或全零buffer。

ABI依据：YMM0及其它YMM upper halves属于volatile；XMM6..15低128位和RBX/RBP/RDI/RSI/R12..15/RSP等属nonvolatile。Probe只用YMM0、参数寄存器和最后VZEROUPPER，不改nonvolatile低XMM或GPR，也不改MXCSR/x87配置。VZEROUPPER会清所有适用YMM高半，故**原6B结果是+14的32B快照**，不是声称C返回后YMM0仍保持该post。C负责正常32B shadow space/调用前栈对齐，call生成返回地址，leaf无栈调整，RET回到审定C continuation。该void函数不赋予RAX业务意义，更不能要求原helper的RAX48。

leaf不动栈/nonvolatile，可按Win64 leaf规则通过模拟return展开；C的`__try/__except`框架必须有正常compiler unwind。未来审实际caller/disassembly/unwind输出；仅C continuation置`normalReturnObserved=1`才算正常返回。这里没有single-step trace，不虚报每条动态instruction事件；有固定无分支leaf、精确bytes、一次调用、真实pre/post与正常返回四层证明。完整904B未来仍必须另审逐原PC写事件，不能拿本23B的终态规则代替。

## SEH、次数、退出和原件

ReadXcr0及Probe分别在明确phase的`__try`内。filter表达式把`GetExceptionInformation()`传给记录函数，立即复制ExceptionCode/ExceptionAddress、CONTEXT.Rip/Rsp与必要volatile GPR、phase、当前image base/RVA、是否已进入/正常返回等到预留owned记录；不在handler中再次调用该宏或借已失效指针延后读取。filter选择`EXCEPTION_EXECUTE_HANDLER`；只输出异常终态，不CONTINUE_EXECUTION、不改RIP/YMM、不跳过原6B、不fallback或再调用。

真实`EXCEPTION_ILLEGAL_INSTRUCTION`数值和异常PC原样保存；即使PC在原6B之外也记录准确位置，例如先失败的VMOVDQU/XGETBV/VZEROUPPER，不能一律称原6B不支持。异常时普通CONTEXT的XMM0不代表完整YMM0，本规格不额外扩展XState捕获或伪填高lane；未到+14的post保留毒值及`postSnapshotReached=false`。一旦发生任意原异常，该次capability失败并停止，不因partial数据接着成功路径。

每个能力child最多CPUID2次、XGETBV1次、Probe1次（原6B最多1次）；无warm-up、循环、重试或指令patch。外层主线launcher新建隐藏owned child，保存唯一创建句柄/PID+creation、绝对EXE/source/output归属，**10秒墙钟**超时仅停止该已创建child并写独立watchdog失败；不访问任何目标进程。native直线leaf没有Unicorn计数hook；静态6条/23B和once调用是预算依据，不能伪报count1硬件hook。

输出采用不存在的新run目录，child结果与parent stdout/stderr/argv/UTC/exit/watchdog各自CreateNew，不覆盖旧失败、半成品或另一个角色的文件。来源核验失败在调用/launch前留gate失败。退出约定建议：0只表示全部能力门通过；10来源/bytes门失败；20 host前置不足；30真实SEH异常；31真实pre/post/guard/return不符；124由parent表示超时。异常/不支持均不是pass。root可统一最终码表，但必须先冻结且report/进程exit一致，不能用exit0承载被省略的执行。

## 必要测试和实际验收次序（均未执行）

按不同依赖验证，避免对同一结果凑数量：

1. **纯来源与输出门。** 改动新owned工作副本的6B/23B、错误file offset/截断decode、旧source漂移或已存在输出名，都在launch前拒绝。只变测试副本，不动112pin原件。validator将实际报告的post/guard/normalReturn字段单独变异，应拒绝；变异报告明确是synthetic，不覆盖真实原件。
2. **纯前置逻辑。** 注入给测试函数的声明数据覆盖无leaf1、XSAVE/OSXSAVE/AVX缺位，以及XCR0缺XMM/YMM；检查调用计划不会越过门。它是纯predicate/调用计数测试，不改真实host值；正式CLI没有force/feature覆盖开关。
3. **SEH独立负证人（需主线在执行计划中另列批准）。** 若需要实际核SEH路径，用单独process/case中唯一owned `UD2`（`0f0b`）一次，原6B执行0次，核真实illegal code与精确UD2 RVA，非零预定终态后退出。它只验异常通路，不算AVX能力，也不在异常后继续Probe。没有这项批准就不编译/执行该额外入口，只把SEH尚未实际校验列缺口；不能以RaiseException或伪JSON替代真实illegal witness。
4. **原6B能力。** 所有前门通过后一次call，核真实host寄存器值、C continuation、完整pre/post/guards及源前后保持。首次失败立即保留停止；成功才可按主线批准再启动第二fresh child（仍一次call）作复验。两原报告保留PID/time/ASLR地址差异，独立比较source/PE bytes、门谓词、固定input和真实pre/post/guards；不刷值凑JSON逐byte一致。主线第三可做独立纯artifact审核，不需要为了计数再执行一次原指令。

主线执行前后均重核旧112pin、固定原904/6B、compiler四源与本次实际source/dependencies/EXE完整SHA；capability只在新code image中运行。保留build日志、map/PDB、PE摘要/两decoder输出、before/after source manifest、host gate/异常或结果、独立read-only比较。未取得的SDK依赖SHA、EXE/RVA和实际host寄存器现在均是unknown，不能预填成功。

## 交付、已做核验与后继

本卡只写本文和私有`local/migration72/d02-resume-20261110b/a-clear48-native-capability/`；未写实现工具，未修改旧112pin/guard/driver、C-mode三件文件、Project/Harness或生产。当前实际核验是112项来源、904B和6B独立SHA/切片、23B/13B拟定byte sequence的完整静态decode，以及primary source边界；没有编译测试、host CPUID/XGETBV或原指令执行。

私有最终`byte-and-source-spec.final.private.json`25170B SHA `85add258803469ad0ce12be24a437ded217bff7f8b90a406056434bd6287d903`固定全部112pin和拟定bytes/decoder身份；原6B/23B/13B都不冒称已在PE中观察。`primary-source-ledger.private.json`2467B SHA `c2ef8765b9e5a02752052314d9961e620bac566cd63e30c4370a8d88d5319369`与`primary-source-facts.private.json`2415B SHA `91ad1f32e518b1f0a919e3a8f2549ae8c9157f5d7228a740671e1a1d18a13a4c`记实际URL/失败/预算和摘取边界。主线负责未来实现文件归属、构建与执行授权、独立验收和Git。

下一可执行任务是按此规格实现/静态门禁该自有EXE，先取一条原6B的能力事实。即便能力通过，完整904B relocation、准确controls/两条AVX重叠写和48B写域、原RET/RAX/RSP、single-step/异常与捕获副本都需另合同；本卡不为其定制未审trace或跑904B。新stock Default仍是非零opaque，第二allocator、自然GC所有权、完整Avatar/g2和D-02保持未验。遇平台安全门禁/Daybreak按用户要求记录停包，不重试、不改模型或改工具链绕过。
