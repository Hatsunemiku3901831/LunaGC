# F-03d3f4d2 / CLEAR48-TRACE-EXIT-LIFECYCLE

2026-11-11，Sol max 有界合同审阅。**第三次完整 W1 仍失败；准确缺口是自有进程退出期的线程生命周期，而不是 TF、已退休指令或平台安全拒绝。** 推荐以独立 `c48-owned-exit-lifecycle-v2` 补充合同一次覆盖 WAIT_EXIT 与失败排空，TRACE 仍严格绑定原 primary PID/TID。本文不是运行许可；前三次完整 W1 不追认，W2/W3 未执行，完整904B、自然 GC、第二分配、Avatar/g2 和 D-02 均不晋级。

本包只写本文和私有 `local/migration72/d02-resume-20261110b/a-clear48-trace-exit-lifecycle/`。parent、validator/tests、runner、child/ASM、四旧设计 JSON、header、原件和公共任务索引未改；未操作网络、Git、PE/新代码窗口、CPU/native/emulator、Debug API、live/REA、进程、构建或数据库。root 另派 Terra max 获取官方来源，本代理只读已导出原件。无需服务构建、便携部署或重启，本地提交和 Project/Harness/交接由 root 统一处理。

## 有界计划与原有边界

先读 AGENTS/README、Project 当前阶段/派工/验收、Harness trace 索引、迁移与部署的研究边界，再读同日 [TRACE_ACTUAL](CLIENT_72_D02_CLEAR48_TRACE_ACTUAL_20261111.md)、[EFLAGS 合同](CLIENT_72_D02_CLEAR48_TRACE_EFLAGS_20261111.md)及其 parent/validator/source 专题、[WAIT_EXIT parent](CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_PARENT_20261111.md)、[WAIT_EXIT validator](CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_VALIDATOR_20261111.md)、[WAIT_IDENTITY](CLIENT_72_D02_CLEAR48_TRACE_WAIT_IDENTITY_CONTRACT_20261111.md)。沿 [原实现合同](CLIENT_72_D02_CLEAR48_TRACE_IMPLEMENTATION_CONTRACT_20261111.md)冻结本次失败、必要源码/SDK/四 JSON，先重算真实关联，再核官方事件/句柄语义，最后给最小实现和独立负例。没有把旧单点补丁逐个继续运行。

完整中文计划 `a-clear48-trace-exit-lifecycle/plan.zh.md` 为8019B，SHA `be4b1c71076fc0e6739f3490b08f247251ed3a512403ce387c4bc73935c7ac55`；定向检索计划 `sources-plan.zh.md` 为5701B，SHA `dca6911a4b9194935174f548921c8a2d020b4c24e07f49ca5f21f0808a395c41`。root 已接纳并派 Terra max；额外明确禁止检索包任何 Git 命令，只复用已导出 README/固定源码和有预算的显式 HTTP，不触 partial clone 懒取。检索仅补退出生命周期，未重开旧 EFlags/904B 研究。完整来源和实际台账见[本轮来源专题](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_SOURCES_20261111.md)。

## 本次失败：事实与缺失

冻结入口 `failure-freeze.private.json` SHA `07e8d8e98dc64d0834798ee82de54d166709be37d5439d71acb3ab4e10e758c4`。root 的 main5 构建/PE/依赖审核 SHA `3682de8e2a87f49b4e940226db29a35bc3957109b67e96b6c0bbd7574998d5f2`及29纯门直接复用，本代理没有重跑构建、PE 或这些测试。源码/事件输入为：

| 输入 | SHA-256 |
| --- | --- |
| main5 parent，75552B | `6e0e7d331184dfdc039f081d017419222fa3e312511fcc700ff1d8bb7f148d73` |
| 当前 validator，36119B | `12e76d506b1e8856b5482e65c75589a93e85a9030e2d794d7744de6b418e90e1` |
| 第三 W1 events，38417B | `16b4a007005c7b736d61c31a8e6db7ed8a2cf0fa181cb0c203a3c268994ae69c` |
| 同轮 parent-result | `a8fe505eaff2a6b1a9fd266280130d73ee21c0de5834a829c314dbdf0244d165` |
| 同轮 child-result | `2ce6edd73d38f022481e51a522ac6b4cc4e4bed5247e6864c42708c5f3bac9ca` |
| 同轮 root receipt | `b6e3bcafca797a50adadf4e05cf6a0854f9a72a4ece1654b1208d081d78104bf` |

纯文件审计冻结25输入及其副本，核88条 JSONL 的连续序号、全部15组成功 wait→实际 event→Continue；成功 wait 的 PID/TID/type 已真实齐全，旧空身份错误没有再次出现。全部 trace Get/Set/读/事务仍在原 primary PID/TID。三事务依次为真实 CALL、64位非零 MOV、RET，退休后继、实际 RSP、CALL 写槽与 RET 同槽读取吻合。9 Get、4 Set、9读330B、3退休均与原 summary 一致。

末退休 stop61：Get62 原 EFlags=`202`；恢复 Set66 仅请求原 TF=0，提交仍`202`，同 stop 回读 Get65=`200`；差为既定 EFlags v2 的 bit1 单向表示，TF已为0，其余控制/GPR未改。正常 Continue67 成功后 phase 才成为 WAIT_EXIT。这些是原局部事实，不是完整 W1 通过。

| 原序号 / phase | 真实事件与后继 | 本轮能证明什么 |
| --- | --- | --- |
| 69、72 / WAIT_EXIT | 原 primary 上的两次 LOAD_DLL；各自身份/文件关闭和 Continue 成功 | 事件分别带有 kernel.appcore.dll、msvcrt.dll 文件路径；路径不证明后续线程创建者 |
| 75 / WAIT_EXIT | 同 owned PID、新 TID 的 CREATE_THREAD；此前 wait74 完整对应 | 原全局 primary-TID 门以 `unknownPidOrThread` 停止；没有正常 Continue 此事件 |
| 76→77 / TERMINATING | 自有 PI.hProcess 的 TerminateProcess(90) TRUE，再对 stop75 cleanup-only Continue TRUE | 这是失败清理，不是退休/正常退出授权 |
| 79 / TERMINATING | 原 primary 的 EXIT_THREAD，随后 cleanup Continue80 TRUE | 原 logger 未保存该线程的 exitCode，保持未知 |
| 82 / TERMINATING | 新 TID 的 EXIT_PROCESS，exitCode=90，Continue83 TRUE | 最终退出事件真实来自新 TID，不能替换为 primary |
| 84–88 / TERMINATING | PI process signaled、GetExit、两个 PI Close、child-result 存在均成功 | 原 summary cleanupComplete=false；本轮不能将它改为 true |

parent 实际 exit2，child 实际 OS exit90；child 自报 `continuedToC=true/exit=0` 只证明该文件写入路径已到达，不能代替 OS 正常退出。root receipt before=after、未超时；root 已报告 fresh OS 自有 parent/child 无残留，本代理未再次查询，也未用该外部盘点覆盖 parent 的原 false。

原 CREATE_THREAD 只有通用事件头，**没有保存 hThread、startAddress、threadLocalBase**；原 EXIT_THREAD 没有 exitCode。不能把缺字段补为零/null、从系统路径推导线程起源，或把此轮主动终止90解释成原本必会正常0退出。

## 源码与冻结设计的准确冲突

当前 parent 的 `event_loop` 1048–1050 在判断 phase 前统一要求初始 PID/TID；WAIT_EXIT 分支1139–1150只接受 LOAD_DLL 和 EXIT_PROCESS。因此 e75 在抵达 WAIT_EXIT 分支前就失败。`cleanup_run` 1211仍要求最终 EXIT_PROCESS 的 TID等于 primary，否则置 `cleanupInconsistent`；1215–1217对其他新 TID也置位后继续排空。两处是同一旧单线程假设，不能只改其中一处。

`log_event` 847–953没有 CreateThread/ExitThread union 字段，借用 process/thread 也只有来源文字，没有实际 handle 值；不能由 logger 不记录推出 SDK 没有字段。固定 WinSDK 10.0.26100.0 `minwinbase.h` 292–384明确给出九类事件编号、CREATE_THREAD 的 hThread/TLS/start、两个 exitCode 与 DEBUG_EVENT 实际 PID/TID；SDK只是结构声明，不证明本机 CRT 内部行为。

validator 的 `check_records` 143–165又全局将 wait/event/Continue 绑到 primary；`check_witness` 228只允许1/3/5/6，256–262的尾序列只允许 W1/W2 退休后有 type6；W3清理401–402固定两次 cleanup Continue。新合同必须同步这些入口、序列与清理关联，不能只给 parent 加分支或删除 `wait identity`。Get/Set/memory/transaction 的 primary 门和原全部退休断言继续保留。

原四 JSON 中 `event.onlyKnownPIDAndInitialTID=true`、`handles.unknownTID=failurecleanup only` 是原合同内容，**本次确实要补充其适用 phase**。不能声称四 JSON 原本已经批准正常多线程退出。四文件和 header 的 SHA 保持 [原实现合同](CLIENT_72_D02_CLEAR48_TRACE_IMPLEMENTATION_CONTRACT_20261111.md)列值；EFlags v2、输入TF-only、pending三态和真实 wait 身份补丁均不变。

## 官方规则、解释与未知

本代理完整读 Terra 导出的14份官方技术正文，逐项核原 HTML/正文大小与 SHA、HTTP200及URL身份，并独立在原 HTML 定位关键句；复核旧固定 Titan README/DebugLoop 的 SHA。这里引用公开页面支持合同，不把网页日期当当前系统实现日期。

- [DEBUG_EVENT](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-debug_event)把 PID/TID定义为事件发生的进程/线程，主线程创建属于 CREATE_PROCESS，type2不包括主线程。结合 [CREATE_THREAD_DEBUG_INFO](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-create_thread_debug_info)的新线程句柄含义，将同一 type2 的 event TID与该 handle作为一项登记，是本合同的关联解释，另有固定 Titan同项登记参考；**不是本次执行 GetThreadId 的独立互证**。hThread可为NULL；start只可能是近似地址。只记录这些数值，不读TLS、不执行入口，也不从地址/模块名判定创建者。
- [Debugging Events](https://learn.microsoft.com/en-us/windows/win32/debug/debugging-events)说明末线程退出产生 EXIT_PROCESS，末线程不另发 EXIT_THREAD。最终事件不应被强制改成初始 TID，也不能要求每个登记线程都先有 EXIT_THREAD。它还说明退出自动卸载DLL不产生 UNLOAD_DLL；该事件不能被当作必有的退出前置。该页没有枚举当前 CRT/OS 所有退出场景的逐事件顺序，本合同不预言下一轮具体顺序。
- [WaitForDebugEvent](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-waitfordebugevent)与 [ContinueDebugEvent](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-continuedebugevent)要求创建被调试进程的同一调试线程调用；Continue使用收到事件的实际 PID/TID。成功 Continue(EXIT_THREAD)后系统关闭对应调试事件线程句柄；成功 Continue(EXIT_PROCESS)后关闭调试事件 process/thread 句柄，进程及剩余线程的范围由 Debugging Events补充。不能提前手动关闭或伪造 CloseHandle返回。
- [PROCESS_INFORMATION](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/ns-processthreadsapi-process_information)另要求使用完PI.hProcess/hThread后分别 CloseHandle。两种来源分账，既不由event自动关闭替代PI关闭，也不声称它们数值必然相同或不同。PID/TID可以在对象释放后复用；后述拒绝同run重复TID是本有界 witness 的保守范围，不是Win32普遍禁令。
- [ExitProcess](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-exitprocess)规定 main返回会进入进程退出及DLL清理，不能在child写出result时提前host成功；[TerminateProcess](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-terminateprocess)对其他进程是异步请求，TRUE不等于退出完成。真实 EXIT Continue、PI signal和GetExit仍须分别取得。

第三方仅复用 [Titan固定README](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/readme.txt)确认的项目，以及 [固定DebugLoop](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp#L352-L437)按事件登记/移除线程的模式。该引擎还会设硬件断点、取/设更多状态；138–153在wait失败且process signaled时会自行构造EXIT并填primary TID。**这类伪造事件、完整调试引擎和多线程步进都不采用**；不导入其实现或运行库。原固定 ref/必要分支及许可证沿来源专题保留。

## 推荐补充合同 v2

机器可读规格为本卡私有 `exit-lifecycle-supplement.v2.private.json`，policy固定 `c48-owned-exit-lifecycle-v2`；这是待root接纳的新设计，无实际运行结果。建议新parent summary明确 `schema=c48-parent-result-v2`，epoch、summary、validator报告都标该policy；validator成功/失败报告也使用v2。四旧 JSON与header继续原hash，v2只覆盖退出期身份/事件和对应cleanup接受方式，不改二进制ABI、child、case、ready/required或原预算。旧v1无policy/raw字段的原件继续按旧epoch失败，不能由新checker猜字段兼容成通过。

### 1. TRACE与WAIT_EXIT分界

BOOT、ARM_STOP、PENDING、POST_STOP、TRACE_DONE的事件/上下文/事务仍只允许owned primary PID/TID。BOOT原允许集合不变；ARM之后仅原case的准确异常可授权退休。出现任何新线程、late DLL或其他事件均依原合同失败，不通过提前写phase获得退出权限。

只有 W1全部3条/W2全部7条真实退休、原RET到AfterCall且RSP/结果/必要buffer通过、pending已清、同stop CONTROL-only恢复原TF并成功回读、同一末stop的正常Continue成功后，才进入WAIT_EXIT。validator从这些raw关联**独立推出**边界，不能单凭`phase=WAIT_EXIT`相信已经完成。W3永不进入正常WAIT_EXIT。

WAIT_EXIT后禁止任何Get/Set context、inspector内存读、新pending/transaction、TF重设或退休授权；仅处理后表的真实通知及退出。child仍正常返回main/CRT，不改为ExitProcess特例、不跳DLL/CRT、不提前写host成功。

当前源码的局部变量`traceDone`只在`event_loop`完成最终EXIT并返回1后才为真；不能因三/七条已退休就将它置真、跳过退出事件或使失败清理误走正常路径。退出期准入状态和完整进程退出结果须分别保留。

### 2. 有界线程登记

一个进程，初始身份始终来自本次CreateProcess PI及creation FILETIME；summary.tid永远仍是primary。primary借用句柄来源由真实CREATE_PROCESS补齐，不能拿它覆盖PI句柄。新线程只由**同owned PID的真实type2事件**建立，不从普通事件TID、路径、地址、成功wait标签或预期列表建身份。

登记项保存真实TID、创建event seq、来源phase、借用handle原值/来源、start/TLS原值，以及`create_pending→live→exit_pending→released_by_contract`状态。收到创建先记录；只有对应Continue TRUE才进入live。EXIT_THREAD对应Continue TRUE后该TID终结，保留墓碑，不再对它做API操作。一个outstanding事件只观察/登记一次；清理继续同一event不会再新增登记项。

生命周期更新依据实际`ContinueDebugEvent` BOOL，不能依据“API和日志均成功”的C辅助函数返回值：若API TRUE后写日志失败，实际系统动作已经发生，内部来源账仍按真实BOOL更新，但日志失败继续阻止完整接受；不能重放Continue或补造持久化行。

用最多128项的简单数组，按记录数遍历匹配TID；TID不是数组下标。容量由原128 debug event总限自然约束，不增加线程/事件/时间预算。重复live TID、primary的重复type2、同run已终结TID再创建均停止；不实现罕见复用的代际跟踪。此保守边界无需创建线程、查询handle或复杂并发状态机。

CREATE_THREAD.hThread实际NULL应记raw零与valid=false，按文档可以登记event TID，后续不使用该handle，也不存在要手动关闭它的责任。非空原值只作为来源证据保存；不增OpenThread/GetThreadId/GetProcessIdOfThread/Get/Set/Suspend/Resume或线程句柄复制。INVALID_HANDLE_VALUE等非文档NULL无效表示原样失败，不能当NULL修正。

### 3. 正常WAIT_EXIT完整事件表

所有行先要求实际PID=owned PID；除type2建立新身份外，TID必须来自primary或已登记且未被成功EXIT_THREAD终结的线程。wait与Continue始终逐条等于**该实际event**的PID/TID/type，绝不回填primary。下表是阶段有限通知处理，不是给其他线程单步许可。

| 真实事件 | WAIT_EXIT处理 | 身份/收口约束 |
| --- | --- | --- |
| CREATE_THREAD(2) | 实录union并登记；DBG_CONTINUE成功后标live | 正TID、新登记来源、未超128总事件；不把event TID写成创建者TID |
| EXIT_THREAD(4) | 实录DWORD exitCode；成功Continue后终结对应项 | primary也可先退出；不要求线程exitCode=进程exitCode，不对已退出线程再取context |
| LOAD_DLL(6) | 沿原hFile条件门实录身份/路径/size/close，再DBG_CONTINUE | 有效hFile原检查不减；实际NULL沿原无效handle行为；路径只作文件来源 |
| UNLOAD_DLL(7) | 实录实际lpBaseOfDll，再DBG_CONTINUE | 无新handle、无远程读；不要求退出时每个DLL必有该事件，不新增模块扫描/通用模块表 |
| OUTPUT_DEBUG_STRING(8) | 只保存pointer、WORD unicode、WORD length元数据，再DBG_CONTINUE | 不读字符串/指针、不给新memory预算；零/边界length按真实WORD保存，不能据通知宣称打印内容已验证 |
| EXIT_PROCESS(5) | 实录DWORD exitCode及实际TID；成功Continue后EXITED | TID须有此前创建来源且未单独终结，可为新线程；不强制primary，不要求其先发EXIT_THREAD，不要求所有其他项逐个有EXIT_THREAD。成功Continue后剩余借用句柄按API合同关闭 |
| EXCEPTION(1)、RIP(9)、重复CREATE_PROCESS(3)、未知code | 原样记录后失败，不正常Continue | 不能因退出期吞异常/调试错误或凭phase宽放；RIP需保存raw error/type |

本次真实必需的是2/4/5/6。7/8同属官方明确的非异常通知，纳入该有限表以完整覆盖退出期DLL/诊断通知，不读新payload、不改执行状态；这是显式v2设计范围，**本轮未观察7/8**，必须有独立人工回归，不能登记为实测。它避免再按某个DLL名字或某一条通知做补丁。

正常完整W1/W2仍必须实际OS exit0、PI signal/GetExit/Close成功、parent exit0、无Terminate/cleanup-only、child原result身份与内容齐全，才可通过。最终EXIT是全进程终点；剩余登记项可由process-wide终止收口，不能补造它们各自的EXIT_THREAD。

### 4. 失败清理与句柄责任

firstFailure与pending三态持续粘住；错误进入FAILED_STOP/TERMINATING以后不再Get/Set/读operand、改寄存器、建授权transaction或封退休。仅用本次PI.hProcess调用TerminateProcess(90)一次，记录实际输入/BOOL/error。只有TRUE以后，才能对outstanding的**owned PID**事件作cleanup-only Continue并排空；FALSE不Continue、不换OpenProcess/detach/工具，按原KillOnExit策略结束并保留未完成。

清理沿同一线程登记来源更新已观察的创建/退出事实，包括导致TRACE失败的type2，但这只是句柄收口证据，不改变先前phase拒绝或首失败。新type2在TERMINATING可按同样有限规则登记；4/5/6/7/8只接受实际owned事件并记录来源。非创建通知的未知TID不能被自动登记：即使Term已成功、为了释放owned暂停事件尝试cleanup-only Continue，也要保留`threadLifecycleConsistent=false`、`cleanupComplete=false`，不能通过正常case或预期负证人。foreign PID绝不Continue，停止drain并记录范围不一致。额外异常/RIP/未知code同样不能取得完整cleanup接受；原UD2 outstanding是W3已有的唯一批准异常。

owned PID应在成功Wait的入口确认后，才调用文件metadata/线程登记/上下文处理；若不符只保留返回的raw身份/union并失败，不先操作foreign事件的file/thread句柄。该入口归属规则在正常和cleanup共用，内部不反复猜测PID。

每个实际收到事件的metadata/file处理只做一次；清理不重复关闭hFile。正常和失败均共享128事件、10000ms总deadline，Wait仍≤50ms，无第二轮计时或重试。已到上限只能记录有限的终止/已有outstanding收口尝试，不新增Wait或退休许可；证据不完整就false，不延长预算补成成功。

| 来源 | 使用与关闭 | 可写入的证据 |
| --- | --- | --- |
| CreateProcess.PI.hProcess/hThread | 原process/context用途；最终各手动CloseHandle一次 | 原handle值、固定来源、真实CloseHandle BOOL/error；原signal/GetExit门保持 |
| CREATE_PROCESS hProcess/hThread、CREATE_THREAD hThread | 借用，不手动Close；线程EXIT成功Continue或最终process EXIT成功Continue后按官方合同释放 | 创建event的真实原值/来源，以及触发的Continue seq；**没有自动关闭的CloseHandle BOOL** |
| CREATE_PROCESS/LOAD_DLL有效hFile | 原身份检查后手动CloseHandle一次，出错原样失败 | raw值、来源、现有identity/path/size/close原件；实际NULL与缺失字段严格分开 |
| LOAD/UNLOAD地址、TLS/start、debug-string pointer | 仅事件metadata | 不将pointer当已读对象，不新增远程读或推导具体系统模块行为 |

成功EXIT Continue以后，仍用PI.hProcess核signaled，再GetExitCodeProcess，核其实际exit值与EXIT事件一致，再关闭两个PI句柄。`cleanupComplete`继续合取EXIT observed/continued、signal/GetExit一致、PIclose、日志完整及生命周期一致；不因为有新TID而false，也不因为物理进程已结束而丢弃身份/日志失败。PI与event handle数值不作相等/不等假设；如实际关闭失败，保留失败，不自创所有权修复。

W3只有原CALL退休1、UD2未退休、首失败expectedUD2、真实Terminate90、child-result不存在、parent30及上述收口通过，才可接受“预期负证人”。将原固定2次cleanup Continue改为**原UD2 stop加全部真实drain事件的一一对应集合**，每项都在成功Term之后、cleanupOnly=true；可有退出线程通知但不得出现新的退休/正常C返回。W3没有因此获得正常WAIT_EXIT或完整trace通过。

### 5. 新日志字段与validator的独立验收

保留全部原required和raw字段，新增只来自现有返回结构/已有调用的值：

| 记录 | 必要补充 |
| --- | --- |
| epoch / summary /验证报告 | 固定v2 policy；summary schema v2、实际exitEventSeq/exitEventTID或未取得null、threadLifecycleConsistent；summary.tid仍是primary |
| ownedChild | PI.hProcess/hThread原值与原来源，creation FILETIME原门不动 |
| CREATE_PROCESS / CREATE_THREAD event | `debugThreadHandleRaw`、固定origin、valid、`startAddress/threadLocalBase`；CREATE_PROCESS另有debugProcessHandleRaw/origin/valid；raw零代表实际NULL，缺字段/null未知不能冒充实际零 |
| EXIT_THREAD / EXIT_PROCESS event | 两者都保存实际`exitCode`；最终TID来自通用事件头，不猜测 |
| LOAD/CREATE_PROCESS hFile | 新raw handle与origin，原有效/无效、身份/路径/size/关闭字段保留；validator补齐当前parent已有而旧checker未核的有效hFile路径非空/size非零要求，不改无效hFile许可 |
| UNLOAD / OUTPUT_DEBUG_STRING / RIP event | 分别为真实moduleBase；debugStringAddress/unicode/length；ripError/ripType。未收事件无对应观察值 |
| cleanup原API记录 | 实际PI handleRaw；Term的requestedExitCode=90、GetExit的实际exitCode，原BOOL/error、Wait result/timeout保留 |

借用句柄的自动释放只由validator根据创建来源和成功Continue独立导出`api_contract`证据及相应序号，不能称直接观察到内核Close、也不要求/接受伪造manual Close记录。parent可保存必要状态，validator不能只相信其`knownThread/allowed/consistent`标签。

继续使用原record kind，不引入另一套事件流。handle/pointer为16位lowerhex实际值，valid为与原值一致的bool，exitCode/rip字段为DWORD非负整数，debug-string两个WORD保持原宽度；未观察才用null。CREATE_PROCESS的借用origin保持`CREATE_PROCESS.borrowed`，type2为`CREATE_THREAD.borrowed`，file origin分别`CREATE_PROCESS.hFile`/`LOAD_DLL.hFile`；PI原origin不变。summary的exitEventSeq/TID在实际收到type5后填写，即使后续Continue失败也不抹去观察；尚无type5则null。GetExit记录仅BOOL TRUE时有实际exitCode，否则null。

checker需要按以下次序一次校验入口并传递已确定类型的值：先schema/epoch/source/完整原件与序号预算；再wait/event/Continue实际身份双射；再既有primary TRACE与全部事务/TF恢复；最后按由证据推出的边界遍历退出/清理生命周期，独立构造有限登记表与收口。普通行公共头要与其实际所属stop对应，不能继续用全局primary，也不能宽成“任意正TID”。Get/Set/memory及transaction仍单独要求primary、准确stop和原允许phase；超时/失败wait保持三身份和stop皆null，成功wait缺任一身份依旧拒绝。

原W1/W2尾部从固定type6串改为上述有限状态遍历，正常末尾唯一type5，之后不得再有event/操作。W3保留fault前准确序列，Term后才允许有界cleanup通知。源与结果中的政策/字段不符或混旧epoch立即拒绝；不能为旧文件回填v2 policy或借raw事件局部诊断输出accepted=true。

## 交实现包的独立正负例

以下全部是待实现的人工artifact测试规格，**本包没有运行这些候选测试，更没有启动native**。parent emitter必须以源级独立审核证明实际union字段记录和phase顺序；synthetic fixture不能代替真实logger。validator测试从独立构造的raw数据重算，不复制parent的分类结果。

| 组 | 必须接受的人工范围 / 必须拒绝的变异 |
| --- | --- |
| 正常生命周期 | W1/W2原primary直接EXIT；create A→primary EXIT_THREAD→A EXIT_PROCESS；A先EXIT_THREAD而primary最终EXIT；两个以上type2的不同退出次序。最终线程不先有EXIT_THREAD，剩余句柄由process-wide收口 |
| 原始类型边界 | type2实际NULL hThread、非零64位handle/start/TLS；type4非零DWORD exit；late LOAD有效及NULL hFile；UNLOAD rawbase；type8 length0/65535、unicode非零和64位pointer且绝无新ReadProcessMemory。缺字段/错误类型/超宽值拒绝 |
| TRACE不松 | type2/4/6/7/8在末退休/恢复/Continue之前均拒绝；假phase=WAIT_EXIT、少退休、TF未恢复、恢复Get错stop或TF=1、末Continue失败不能放行；任何其他TID的Get/Set/memory/transaction拒绝 |
| 身份与顺序 | 未CREATE的TID发4/5/6/7/8、foreign PID、type2重复live/primary/墓碑TID、EXIT_THREAD重复/之后再发普通事件、EXIT_PROCESS来自已单独终结TID均拒绝。wait/Continue必须匹配各自event，九个旧缺失/错身份负例保持，不能统一改成primary |
| 事件失败 | WAIT_EXIT任何异常、RIP、重复type3、未知code、额外step、EXIT后再event均拒绝；错误Continue状态/BOOL、cleanupOnly标错、双Continue/缺Continue、缺/重复wait或seq都拒绝 |
| 句柄责任 | 真实event raw缺失、NULL被当成已关闭、借用handle手动Close、伪造自动Close BOOL、重复PI/file手动Close、有效file身份/path/size/关闭失败拒绝；Continue失败不能导出自动关闭已完成 |
| 失败与W3 | Term FALSE后任何Continue拒绝；正常case出现Term/cleanupOnly拒绝；W3原UD2+多个合法drain可预期负接受，首失败不同、额外异常/未知TID、UD2退休、child-result存在、新C返回或exit非90均拒绝；原失败不能因cleanup完成改为正常 |
| 实际收口 | signal/GetExit/PIclose任一失败、EXIT与GetExit值不符、日志写失败/半行/缺event都拒绝。root fresh OS无残留不能替代这些原件 |
| 预算与冻结 | 合法128个总event可验、129拒绝；TRACE/WAIT_EXIT/TERMINATING合计，不重置wall或计数。原case Get上界9/17/6、Set4/8/2、读16/512或64/4096保持。四JSON/header/前三失败hash不变，旧epoch缺v2必拒绝 |

本设计没有增加CPU operand、Get/Set、线程API或新memory预算。固定数组容量沿已有128事件上界，最多线性小表遍历，不做任意线程调试、线程起源追踪或罕见竞态修复。遇到TID复用或没有创建来源的实际事件，应按该明确边界停止并记录；不得把终止期例外搬进TRACE或临场放大集合。

## 实际验证、停止与交回

本包实际完成：冻结25个原件/源码/四JSON/header/SDK输入及副本；重算第三W1的88条raw及三退休/TF恢复/失败收口；核当前parent和validator的准确冲突；独立核14份官方raw/正文和旧固定README/DebugLoop。没有调用普通validator、替换其need、修改raw rows或生成正常通过报告。派生审计始终`accepted=false/completeW1Accepted=false/oldThreeW1RunsRemainFailed=true`。

| 私有产物 | SHA-256 |
| --- | --- |
| `frozen-inputs.private.json` | `7b16ae81958e520fe9c4c6a5bf786530b8d72e36b8be6bc3de03cbe2ce9939cd` |
| `failure-source-audit.private.json` | `104238f9b2e4dd29fcd0c37c6b7f0c92beb43de7430816286492b2e0e9aa172a` |
| `official-source-review.private.json` | `2fa558c8a6af083f04049f2bdcb870f581ea10cfa75a5cfbbc479cd693d28b09` |

官方raw检查第一次因HTML去标签在NULL后的逗号前插入空格导致精确断言失败，保留`official-source-attempt1.private.json`；检查raw后仅规范该空白，第二次14页及两旧导出物全部核对通过，没有重取网络或改网页。首次失败没有产出通过报告。此前一次只读路径检索误用了不存在的旧目录，已沿原实现合同更正，不扩取文件或运行范围。

输入/source/policy漂移、其他writer冲突、有效文件API失败、非法身份/事件、预算耗尽或官方不支持的关键语义均原样停止；实际安全/Daybreak拒绝停该包记账，不换工具/模型重试。本轮没有这种拒绝，也没有需要等待新现场才能形成上述规格的缺口。

**下一可执行任务**：root 审核/冻结v2补充及文件分工，交Sol xhigh/Terra分别修改parent记录/有限生命周期与独立validator/人工负例；root再做真实source/build/PE/依赖新epoch和必要纯门。只有这些出口通过，root才另定一次新W1；完整OS正常0和v2实际字段须由新run证明，再决定W2/W3顺序。当前分析交回并释放max，不等待实现、构建或新run；本文件不授权执行。
