# F-03d3f4d2/CLEAR48-NATIVE-TRACE-SPEC（2026-11-11）

## 本轮中文有界计划

本卡仅研究和规划，不写trace实现、不构建、不执行原native指令或host能力probe、不调用目标/live API、不操作进程/Git、不招募。上一C合同所有写权已交root。先读当前入口与CLEAR48 capability/implementation/backend/owned、SECOND_GC64、GC_DEFAULT_STOCK_CAPTURE，固定旧904B/112pin及真实stock输入；原6B能力/SEH实测未验，所以后续均为条件规格。

重大trace后端开发的GitHub README-first/重点相关branch检索计划已先写`local/migration72/d02-resume-20261110b/a-clear48-native-trace-spec/github-plan.zh.md`，交root另派新Terra max；本Sol max不承担检索。随后只给最小可行后端、映射/逐PC读写/真实CALL-RET合同、完整失败停止与分包，不改904B或扩任何callee。可写仅本页与该独立忽略目录；其余全部只读，成熟后立即交回释放max。

## 当前出口与实施前置

本有界规划已完成，交root/new Terra；**没有trace源码、构建或执行增量**。推荐保留原RVA的稀疏自有arena，加自有child的Windows调试事件循环：本条执行前核原bytes/有效地址/宽度，真实硬件执行后由下一single-step事件记录退休与post。这样可分开保存两条AVX store，避免只凭最终零buffer验收。

| 前置 | 当前边界与解除条件 |
| --- | --- |
| 原6B/host能力 | root正在审23B EXE，首次cap尚未在本卡获接受。必须先有已审同源PE/依赖、真实SEH witness及原6B能力；本卡不填host CPUID/XCR0或成功。 |
| Windows单步合同 | 新Terra max按本卡GitHub计划核README/相关branch/license/API/上下文。已有Microsoft ABI/SEH和Intel PDF可复用，但不替代TF事件时点、CONTROL-only回写与实际AVX状态保留的证据。 |
| trace自身witness | root另批后，以自有23B Probe校准CALL/逐条step/两笔真实store/RET/YMM保留，再以独立owned UD2验证真正异常失败。没有这层，不执行904B。 |
| 完整helper | 上述均经root验收后，才另批新原904B native-owned调用。人工case与捕获副本不同；捕获副本写仍未批准，不自动接pop/第二allocator。 |

这里的预期single-step是trace协议事件；未知异常才是失败。协议事件必须符合唯一pending指令/线程/下一PC，否则同样失败，不能拿任意single-step或异常当“继续成功”。

## 已冻结来源与stock/人工输入分离

原helper `[141fdf9c0,141fdfd48)` 904B/176条，SHA `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31`；12B pdata SHA `d56e1bc42b22adb681a4c0b03e40b3ada8cb149f4602bde5d9796082a283e201`。原6B位于offsetF2/141fdfab2，`c4e37d18c001`。旧112pin manifest SHA `f02965fbe282ddfac9373df863a456f2ca26af4792dcfaf1db99c8ff1f2e9310`逐项完整大小/SHA保持。Unicorn一次invalid及虚拟AVX=0停止保留，不重跑、换model或放宽旧guard。

stock原capture SHA `18fea5b455257a4ebde0709cb23ac96adb663033492198814bf6495842d0a840`、主审 SHA `55f26e400f0a9db3b221462c19d8b7771d37e7ea7a81519937c5112619ffd5b0`。该epoch真实I=6、T0=524288、长度48，唯一AVX table DWORD=`01fdfb94`；**T1/F未读且此路径不需**。没有真实helper call frame/RET、线程或GC所有权；Default仍非零opaque。只可把这些数值明确标为stock来源，不补未知T1/F，也不能让本native-owned调用变成自然GC。

| case | 显式来源与控制量 | 地位 |
| --- | --- | --- |
| 旧SSE fixture | 人工I2/T0=48/SSE table01fdfd35；不读T1/F | 旧Unicorn实际35条、三写/RET的原合同与报告保留；它不经过SSE的F读取支路，不能改称该侧最长路径。 |
| SSE-long提案 | **新显式人工变体**I2/T0=47/F0/SSE table01fdfd35；T1不允许读 | 若root要一次覆盖SSE最长clear48条件支路，先单独接受此变体；不得悄悄改旧fixture或旧pin。 |
| 旧AVX-long fixture | 原人工I3/T0=47/T1=48/F0/AVX table01fdfb94 | 条件读取最全；原Unicorn只走20条便停在6B，不是已完成AVX。新backend可在另批后按原值测试。 |
| stock-derived后继 | I6/T0=524288/AVX table01fdfb94；T1/F不存在 | 独立后继，须另批owned数据/捕获副本写。人工两侧通过也不能替代它或自动推进第二allocator。 |

最小新native资格组建议SSE-long与旧AVX-long各一次；旧SSE35条作既有对照原件，不为次数重跑。若root先选择旧SSE case，则保留其原I2/T0=48，SSE-long覆盖明确仍缺。每个case独立child/arena，不混controls与raw output。

## 方法选择：自有child调试循环优先

| 方法 | 适合范围 | 本卡决定 |
| --- | --- | --- |
| C SEH | 保存真实fault和失败退出；23B直线能力已用此规格 | 单靠SEH没有每条store前后的停点，不能完成本trace。 |
| TF+VEH | 可在自有进程收step异常，但handler会在同进程/线程运行；须审异常递归、寄存器与XState恢复 | 保留为待源审候选，不把普通CONTEXT.XMM当完整YMM，不先实现两套backend。 |
| 自有child debug loop | parent处理stop、读真实context/owned memory；child原helper停着时无需运行C记录handler | **首选条件方案**。只CreateProcess自己的固定EXE/child模式，不attach PID、枚举他人进程或注入DLL。成熟source/API合同和witness不成立即交回，不自行改为VEH重试。 |

可用本机已盘点MSVC C+ml64/SDK；复用现capability的来源/构建/依赖/PE审核方法，不能把四工具在盘当已build或依赖齐备。建议一个独立EXE的parent/child两个模式，C parent用真实SDK的DEBUG_EVENT/CONTEXT定义，避免临时手写ctypes对齐。没有外部PID参数、step-over、软件断点改904B、代码重写或任意内存walk。外部源码只有限复用事件/上下文/许可成立的小部分，不整包移植debugger/DBI。

## 原bytes、映射、relocation与PC归一化

原模块基准`B0=140000000`，helper RVA=`01fdf9c0`。**不做904B指令relocation改写**：在自有child新reserve一个64KB对齐的arena基准`Bo`，helper仍放`Bo+01fdf9c0`，controls/table按各自原RVA放置。原`141fdf9c6 4c8d15330602fe`真实LEA由runtime nextRIP减01fdf9cd，得到Bo；其余RIP disp及原table RVA随后自然成立。

需要的最高末端为`06d19144`，reserve向64KB上取整=`06d20000`（只reserve约109MiB地址空间）。仅commit源code所在页、case所需control/table页和独立owned buffer/guard/stack；其余仍NOACCESS。code初始化RW→完整904B SHA→RX，并FlushInstructionCache；controls最终只读。逻辑允许span仅表中准确字节，**commit页中OS初始零/padding不是已知输入**，不能供原指令读取或用整页清零补未知。失败不换固定原地址或多次试分配；一次新arena失败就记录停止。

| 原数据RVA | 宽度/内容来源 | 允许读取的case |
| --- | --- | --- |
| 056afdd0 | U32 I | 所有clear48 case |
| 056afdd8 | U64 T0 | 所有；按本侧准确PC读取 |
| 056afde0 | U64 T1 | 仅AVX-long；stock-derived不提供 |
| 05eeeb08 | U8 F | SSE-long/AVX-long；旧SSE与stock-derived不提供 |
| 06d190f4 | U32原RVA01fdfb94 | 仅AVX侧index1 |
| 06d19140 | U32原RVA01fdfd35 | 仅SSE侧index2 |

table DWORD保持原RVA，不改成VA、不patch间接跳。实际R10须是Bo，table读出的原DWORD零扩展加Bo；`b54/d08`执行前核实际R11等于本case唯一获准tail。未审窗外`141fdf9a0`没有code页或成功模型。

每事件保留raw runtime PC和`RVA=PC-Bo`；仅当PC是原904B的准确指令边界才给`originalPC=B0+RVA`。buffer以本轮H+offset、stack以本轮真实call return slot+offset另标；wrapper/ARM/after-call属于该EXE/map的独立label，不能强投到客户端原PC。不同child的ASLR/PID/地址/时钟保留原样，比较时另做归一化视图，不要求native原报告逐byte相同。

## 真CALL/RET与逐条事务

建议专门ASM wrapper接受`(entry,D,length)`，用volatile寄存器准备R11=entry、RCX=D、EDX=0、R8=48；Win64 shadow space/16B对齐由真实wrapper提供。wrapper有真实编译unwind与`Arm/Call/AfterCall/End`标签；Arm是**wrapper自己的**一条INT3，紧接真实register CALL。原904B无INT3、无任何改写。以上是新源码设计，不是已观察的PE字节；未来先核map/PE/caller/unwind，不能通过搜索一个0xCC认定握手。

1. parent以DEBUG_ONLY_THIS_PROCESS创建自己的隐藏child，绑定创建handle/PID/TID/creation/EXE完整SHA；拒绝attach或其他进程。启动loader事件与一次文档规定initial breakpoint只在未armed阶段按新Terra固定API合同处理；未知首机会异常仍失败。armed期间新增thread/未知DLL/debug事件失败，不跟OS/CRT代码做helper trace。
2. child完成来源/host门、arena/buffer配置，在独立固定小handshake（建议≤256B、布局由实际编译static_assert固定）写本轮owned范围/labels，之后进wrapper Arm。parent只在该停点读handshake和真实context，核source/Bo/H/permissions与寄存器。INT3原ExceptionAddress和Context.RIP语义须由witness校准；只有实际RIP恰为Call label才继续，**不改RIP纠偏**。
3. 此时真实RSP=C，要求C mod16=0且shadow/返回槽属于本child当前owned stack。parent保存原TF及CONTROL|INTEGER快照；Set另用CONTROL-only副本，仅EFlags.TF数值改变，其余CONTROL字段逐项等原快照，不请求INTEGER/FLOATING/XSTATE回写。其他RFLAGS/GPR/RIP/RSP、debug寄存器和向量状态不修改。CONTEXT_CONTROL的实际Get/Set使用及系统TF清理行为由源审/witness确认，不假设普通CONTEXT含完整YMM。
4. preflight真实CALL：确认calltarget为Bo+01fdf9c0、隐式写`[C-8,C)`宽8及真实返回地址AfterCall；读该槽old。执行后唯一匹配single-step应到helper entry，实际RSP=C-8；读取真实槽new=AfterCall，形成**wrapper CALL**事件。不是预写sentinel或把客户端140563d8a放进栈。
5. 对每条source指令，停点context是该条的pre状态。查固定176条清单核准确PC/bytes/size，计算其真实EA/宽度，核允许span；先保存原operand/目的old bytes和pending记录，然后只让硬件继续。下一匹配step取真实post context和已准许内存，检查实际nextPC/效果，再标retired并封口。比较/有效地址/分支前置计算只作授权检查，**不计算并回写原指令结果**。
6. TF事件通常表示上一条退休、RIP在下一条；本实现必须先以witness证明这种实际事件对应，再使用它。保留前后PC/完整原exception信息/线程ID；不能把post事件的RIP当上条store PC，也不能只根据静态下一地址生成事件。只有唯一pending指令能完成；重复/丢失/错线程/错PC立刻失败。每次继续前按实际API需要重设TF，不step-over CALL/RET。
7. helper的`ba2`或`d47`原RET preflight只许读真实`[C-8,C)`8B，值须是AfterCall；真实RET后的step必须在AfterCall，RAX=D且RSP=C。parent在任何wrapper下一条执行前保存该真实RAX/RSP，再恢复原TF（只该位）；不修改RAX/RSP/返回槽。child随后真实退出wrapper到C continuation，记录正常返回和guards，最后正常exit。此处证明的是**owned CALL到原helper及原RET**，没有执行客户端原pop caller或第二allocator。

原904B的异常unwind xdata没有取过，本包不注册猜测的RtlAddFunctionTable、伪xdata或把异常包装成成功。正常trace不需异常展开；未知fault在parent拿到原first-chance ExceptionRecord/CONTEXT后即失败终止该owned child，不让failed指令重试、跳过或进入正常AfterCall。不能据此声称原客户端异常unwind已验。

## 每条原read/write的前置EA与准确允许span

预先入口一次核完整source/case/清单/schema；每次step前的EA/控制流检查是本包必须的执行门，不是内部重复判空。EA用该停点真实GPR和该条原编码：RIP使用nextRIP，或base+index*scale+signed disp；只有具体获准的PC/方向/宽度/完整span才放行。LEA只有地址计算，不记CPU数据read；instruction fetch与parent inspector reads单列，不混为原operand读。

| 原PC | 真实操作数EA/宽度 | 准确允许域 |
| --- | --- | --- |
| 141fdfa86 | nextRIP+disp32 /4读 | Bo+056afdd0，仅I |
| 141fdfa93 或141fdfc70 | 各自nextRIP+disp32 /8读 | Bo+056afdd8，仅T0；本侧一次 |
| 141fdfa9c | nextRIP+disp32 /8读 | Bo+056afde0，仅AVX-long |
| 141fdfaa5 或141fdfc79 | 各自nextRIP+disp32 /1读 | Bo+05eeeb08，仅获准F分支 |
| 141fdfb49 | R10+R11*4+06d190f0 /4读 | 必须恰Bo+06d190f4；原index1 |
| 141fdfcfd | R10+R11*4+06d19138 /4读 | 必须恰Bo+06d19140；原index2 |
| 141fdfb94 | RCX+R8-20h /32写 | 本轮H+20h..H+40h |
| 141fdfb9b | RAX /32写 | 本轮H+10h..H+30h |
| 141fdfd35 | RCX+R9-20h /16写 | 本轮H+20h..H+30h |
| 141fdfd3c | RCX+R8-10h /16写 | 本轮H+30h..H+40h |
| 141fdfd43 | RAX /16写 | 本轮H+10h..H+20h |
| 141fdfba2 /141fdfd47 | RSP /8读 | 只本轮真实CALL产生的返回槽 |

其余获准clear48指令只有寄存器/flags/代码控制，不授权其他数据访问；若实际进入loop、NT store、未列数据PC或任何call，先停并记不支持。活动路径没有helper内CALL；R8对齐后为32，不进入128/256B循环。未知条件外跳在`aac/c80` **执行前**据真实flags判定；要去9a0则失败，不执行跳转。间接跳同样在执行前核实际目标且需下一事件证明跳到同一tail，不事后发现越界才伪称被阻止。

控制读预算（不含代码fetch/inspector/RET）：旧SSE及stock-derived各3读16B；SSE-long4读17B；AVX-long5读25B。另原RET1读8B。wrapper CALL的1写8B单列为harness写，不能混入helper清零账。XMM/YMM寄存器操作无数据read；普通CONTEXT无法证明YMM高半时留unknown，最终每笔真实store仍保留完整实际32B。

## AVX两笔重叠写与48B唯一域

人工H为独立64B、H mod32=0，D=H+10h，EDX0/R8=48；payload以原人工01..40非零模式初始化，前后各32B不同非零guard、H前16B另完整保护。它是普通owned字节数组，不是伪GC对象/TLS/自然head。未获捕获副本写权，不复制真实heap到本次执行或假造oldFlags。

AVX必须分别有原PC `b94`、`b9b`两条已退休事件，每条完整32B old与真实post/new：第一写H+20..40；第二写H+10..30，与第一交H+20..30共16B。第二写pre读取必须发生在第一写完成之后，重叠old=第一写真实new，第二写非重叠old仍来自该时刻owned buffer。两事件逻辑写字节合计64、union48、intersection16；不合并成一笔48B，也不把Unicorn曾拆的8B backend事件当native硬件拆分。

SSE按d35/d3c/d43各16B且顺序不改；union48、合计48、不重叠。每次只对源指令真实EA写域取old/new，配合完整H64/guards最终快照；H前16B和全部guard不变，逻辑写只许H+[16,64)。必须独立比较期望零与**实际new**，不能先memset、先写expected或事后从全零buffer反造每条事件。

这套是“固定原编码+真实pre context/operand bytes+逐条退休stop+真实post bytes”的**架构逻辑operand事件**；它不声称观测总线事务、cache line或单条AVX内部物理写次序。不能从一个正常step推断硬件store原子性；异常时可能partial，原fault/当时可安全读取的实际bytes原样保留，不填completed或完整new。

## 失败、允许写域、预算与原件

child准备阶段只写自有code/data/guards/handshake/栈；code保护后不可写。helper运行阶段只有上表原store；parent只在核定线程CONTROL里改TF，不写payload/code/control/返回槽/GPR/YMM，不清未知页，不写原客户对象或调用目标TLS/GC。parent日志与报告写到本包独占目录；代码/guard页保护不变。实际EXE/系统执行策略随新构建核实，若拒绝自有arena执行/间接CALL就保留真实失败，不为pass关闭保护、放宽权限或改原指令。对原异常不选成功恢复或改RIP，普通工程未知返回具体缺口；安全/Daybreak按用户要求立即停包，不换模型/方法重试。

每case限1次真实helper CALL，建议source退休上限128条（含RET）+1条wrapper CALL，最多1个pending step；这是硬上限，不是伪造实际条数。新parent总墙钟10秒（含启动/退出）、debug事件硬上限256；超限只通过已创建handle终止该自有child并记失败。不得增加timeout或重试来凑pass。每次Wait有短期限以检查deadline，不sleep长时间；进程/线程/事件句柄关闭一次，保留exit/watchdog状态。

来源/布局/bytes/host门失败在arm前停止；EA/宽度/非法目标/预算失败在本条CPU执行前停止；真正非法指令、AV、unexpected breakpoint/step或OS事件保留raw code/flags/firstChance/ExceptionAddress/参数、CONTEXT、pending PC/旧bytes、最后完成事件。pending不标retired，原执行失败不能写normalReturn或零buffer成功。即使此前一笔store已发生，也保留partial而非回滚伪造“未写”。未记录原helper fault的真实业务异常/unwind仍未知。

报告至少分：来源/112pin/新增source及dependencies、EXE/map/PDB与原904B SHA、arena/page protections/实际labels、真实host门、case来源、完整H/guards初始、CALL事件、每条pre/post/允许读写、两/三笔完整old-new、RET及实际RAX/RSP、最终快照、异常/失败/exit、argv/UTC/PID/TID/创建归属和关闭。parent inspector读取的地址/宽度/结果单独计数；不要声称CPU read hook、伪硬件YMM快照或把静态预测当实际执行。

## 最小分包与验收次序

1. **G：新Terra max定向源审。** 按私有GitHub计划，先README，再必要branch/source/license，返回TF/debug-event/CONTEXT与EA方法的最小复用点、许可/版本/未闭条件。没有新安装、构建或运行。root接受后才定实现，不在本槽做检索。
2. **I：Terra xhigh/Sol xhigh实现/纯门。** root另给新源码写域。单独native parent/child模式、两个小ASM调用wrapper、固定904B输入清单与纯artifact auditor即可；优先复用已验capability的hash/PE/dependency/报告机制，不改旧112pin、旧driver或共享decoder。实现只做真实API step协议，不实现x86执行器。纯门覆盖错误source/bytes/PC、RIP/EA宽度/越界、未读T1/F、错table/未知外跳、重复/丢step、事件预算和真实报告变异；synthetic数据必须标synthetic，不当native witness。
3. **B：root独占构建/trace witness。** 原6B能力和SEH先已接受，依赖/caller/unwind/PE全部新审。另批1个正child以原23B Probe在此debug loop逐条校准（普通XMM不冒充YMM、真实两笔32B、CALL/RET/RSP），1个独立UD2 child验证raw fault与失败退出；UD2不执行Probe或helper。任何一步失败保留即停，不fallback到另一trace机制。
4. **H：root另批完整904B owned。** 冻结SSE-long变体后与原AVX-long各1 child；每次host门和全部source前后保持，真实逐PC old/new、RET/RAX/RSP通过才接受。首组失败不做第二fresh；首组全通过后root才可另批第二fresh，不为数量自动重复。第三审可纯artifact复核，不必再执行helper。
5. **S：独立后继。** stock-derived controls/捕获副本、原pop与第二allocator、Default消费者、自然线程/GC所有权和Avatar/g2分别另审。原6B或人工helper成功均不自动授权这些写/调用；本计划没有904B外callee许可。

成熟交付到这里即释放Sol max。最小待输入是root已验native能力/SEH原件、新Terra的固定TF/API/许可证源码，以及root明确选择/批准的人工case与新实现写域；不向用户索取官方登录或新目标现场。

## 本卡实际核验与交回

只运行host Python的纯文件/原清单校验：完整112pin、904B/176条连续拼回、6B切片、原7项RIP/LEA算术、五笔store原编码、stock raw/review的I6/T0/唯一table及未读T1/F；没有调用原native指令或任何目标API。静态报告`static-contract-review.private.json`25514B/SHA `734a10e253343169f5ab75c1637a8be31918f1ca668f77c0399d16c44d9a1490`；新source清单`frozen-inputs.private.json`21699B/SHA `0ea702f27cb7a0b7ff682d686dbc4ead032c98ac4d704923e8d16977bd010746`。可只读复核原件，不直接重复CreateNew脚本覆盖它们。

GitHub计划SHA `e2771659f9706423d1c20ae79fe5db791bbe4e2439a66e86ca7f13d76e70fbfe`。既有Intel SDM090完整PDF SHA `3686e42931e893b5016c70fb131403795ef173237d073afcfec52755fcd3ea50`、MSVC盘点与先前primary ledger均已核全SHA；当前没有新网络查询或新TF来源事实。新增文档/规划无需测试原程序、重启、生产构建或部署。

本卡没有修改前C/旧Scene/Proud/共享Project/Harness/HANDOFF、原904B/112源、Proto/patch/resources/config；无Git、客户端/调试器/目标进程、live、新CPU/EXE、网络、构建或DB操作。`nativeCapabilityPassed=false`表示本卡没有已接受的新cap结果，不推翻root后续独立验收；`nativeTraceExecuted/helperExecuted/capturedCopyWriteApproved/naturalGC/secondAllocationReturned/completeAvatar/d02Complete`均false。全部写权交root，本槽不直接实现或继续机械整理。
