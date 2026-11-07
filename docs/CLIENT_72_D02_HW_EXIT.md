# D02-R2：单硬件观察退出与事件库存缺口

2026-11-07，本卡只分析已导出证据与固定源码，没有连接调试器、启动/继续/附加客户端、操作断点、Git、Gradle或数据库。主线当前目标已退出，持久库仍须按主线的四点ledger处理。本卡的离线审计完成，**根因、自然GlobalDispatch入口/返回及D-02仍未验收**；交由主线审核，不是停止主线工作。

主线已审核工具/6项负例、固定源码和本轮三份原始输入，独立6检查与真实审计重放通过，完整原字节与代理一致，SHA `f09f1fb8aff165730cafaedeefaf437fc3aa47321f1746bdd3c38987c5fb6e35`。证明和日志在`d02-active/main/hw-exit-main-{audit.json,tests.log}`；只接收本文限定结论，不宣称根因或实机通过。

## 问题、输入与失败出口

目标是定位`80000004`故障与高层事件缺失的最早可证分界。固定原始7.2客户端的新管理员会话在系统点因自动TLS归属被旧观察器拒绝；主线按旧ledger精确清除四个自有点后，使用已审专用前置续接在同一目标运行。本卡不重做已通过的通用软件/HW fixture，不预判保护、隐藏线程或返回值。

| 固定输入 | SHA-256 |
| --- | --- |
| `main/global-dispatch-resume1.json` | `4ba34a38adbd960a033cfb1754ec1455daff804cbed8f376c0cb6f291cd4bbf4` |
| `main/wer-after-hw.private.json` | `ff156b07f8eb195b4adf53915afde776550549a55bd2448382cbe892365f4d64` |
| `main/wer-copy/Report.wer` | `d0aed5de85f56077a860236cf406e2d7a4672cb93c623e7b7b76c52ceaa35737` |

以上路径相对忽略目录`local/migration72/d02-active/`。Report.wer为主线管理员只读复制，89,256字节；复制证明为`main/wer-elevated-copies.private.json`。WER列出的临时mdmp已不存在，归档只有Report.wer；**没有取得minidump或故障线程context**。用户当时不在电脑前，启动画面为**未观测**，不能写成没有窗口；报告`UI[2]`只是应用路径。

失败出口：缺少原始异常事件/TID/context时保留unknown，不从退出码、同址WER、静态源码或合成通道通过推导自然入口已观察，也不原样重复该HW启动。

## 本轮已核事实

[离线审计](../tools/audit_client72_hw_exit.py)要求run/WER/可选Report.wer显式SHA-256，并固定四份调试器/插件源码hash。实际输出`hw-exit/audit.json`：

- 54个停止：1系统、15个有独立TLS地址依据的自动点、1个主EP、37个首次AV；54次PAUSE/RESUME与逐停止`con`或`con 1`再`run`逐项配对，没有额外已发布的暂停或继续命令。
- EP才安装唯一slot0执行点，RVA`112250E0`；安装前六DR为0，完整执行寄存器保持，代码窗口保持。之后42个停止的双寄存器API一致，均见该DR0、DR7=`401`及DR6=`ffff0ff0`。这只覆盖实际选中上下文，不能替代故障线程的DR状态。
- 总续接观察38.875秒；最后一条普通`run`至PUB退出16.187秒。OS/PUB/日志均exit`80000004`，EXIT+STOP齐全；日志eviction0、PUB线程存活。没有自然入口/返回、高层HW命中或最终`80000004`异常事件。
- WER1000按本轮CREATE的PID和完整路径匹配；1001及Report.wer以报告ID关联，APPCRASH/code/RVA一致。故障RVA等于安装点；这不是参数、调用栈或业务调用的证据。

## Report.wer新增的模块证据

Report.wer有124个唯一`LoadedModule`，本轮PUB有88个唯一`EVENT_LOAD_DLL`名称；两者交集84，WER独有40项（主EXE与39 DLL），PUB独有4项（`netapi32.dll`、`netutils.dll`、`wkscli.dll`、`srvcli.dll`）。39个WER独有DLL的完整文件名在全部导出`logMessages`中也均没有出现。

WER独有项包括`LoadedModule[60]`的`${GAME_PATH}/YuanShen_Data/Plugins/Astrolabe.dll`、`[65]` HoYoChannel、`[97]` Mmoron、`[121]` telemetry，以及图形、输入和系统组件。因此，本轮“没有Astrolabe加载事件”不能写成“没有加载Astrolabe”。WER提供了独立的模块存在记录；没有该模块加载时刻、基址/内存hash、自然调用、请求正文、信任材料消费或SDK认证成功证据。AccountPlatNative没有列在这份WER中，也不能将缺项扩大成整个运行期从未加载。

两个集合不是同一时刻、同一来源的完整事件历史，差集不证明某线程隐藏或特定保护机制。旧观察器没有订阅`EVENT_CREATE_THREAD/EXIT_THREAD`，其raw缺少这两类事件是实现范围，不能作为线程通知消失的证据。Report.wer没有显式故障TID/context/dump字段。

## 固定源码给出的分界

来源为x64dbg `2026.05.27`、其TitanEngine子模块`ec7a8b9352c47eb0f8cc63f033d36f705d658743`及Automate `v0.8.1-ghost_fungus`。固定文件在`local/migration72/analysis/d02-r2-debugger-source/`，审计工具逐文件核hash；源码解释不等于已经测到对应运行分支。

| 分界 | 已核源码行为与本轮限制 |
| --- | --- |
| Wait→EXIT | [TitanEngine DebugLoop 110–125](https://github.com/x64dbg/TitanEngine/blob/ec7a8b9352c47eb0f8cc63f033d36f705d658743/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp#L110)：WaitForDebugEvent失败而目标句柄signaled时，能合成EXIT_PROCESS，再走正常exit回调。因此三方退出一致不能证明最终EXCEPTION曾进过WaitForDebugEvent。 |
| Wait→原始插件回调 | 同文件140–171：SafeStep跨线程暂缓分支先设置DBG_REPLY_LATER并跳到continue，随后才是chDebugEvent。即使新增CB_DEBUGEVENT，缺失也不能被称作最底层没有收到事件。微软说明[DBG_REPLY_LATER](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-continuedebugevent)要求后续重放；现有raw没有其运行状态。 |
| SINGLE_STEP→HW | 同文件665–889：若不是内部恢复step，按事件TID取context；地址等于Dr0或DR6.B0且引擎slot0 enabled时，在HW回调前设DBG_CONTINUE。否则通常保留NOT_HANDLED，转通用异常回调。故障时读取context是否成功、内部slot/恢复状态均未知。 |
| HW→高层暂停 | [debugger.cpp 853–1081](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/debugger.cpp#L853)：普通有效无条件点会lock、发CB_BREAKPOINT并等待；点表无匹配也会PAUSE并等待。单纯丢PUB不能独立解释“正常进入上述等待路径后没有新增continue却退出”；仍可能有其他路径/外部状态，不能排除后便直接选根因。 |
| 通用异常→高层事件 | 同文件2121–2212：异常点、过滤、skip及线程命名可以改变到达CB_EXCEPTION的路径。本轮运行前ini是catch-all first/log/debuggee，并非专门忽略80000004；文件不证明故障时内部filter/skip状态。 |
| 插件→PUB | [Automate plugin.cpp](https://github.com/dariushoule/x64dbg-automate/blob/v0.8.1-ghost_fungus/src/plugin.cpp)只注册高层回调，没有CB_DEBUGEVENT；[发送实现](https://github.com/dariushoule/x64dbg-automate/blob/v0.8.1-ghost_fungus/src/xauto_server.h)不提供逐事件接收确认。线程存活/无日志eviction并不是端到端事件完整性证明。 |

本卡还补查第五文件TitanEngine.dll：当前磁盘SHA-256为`0f4f992b51218b4bca6a0a8aaf7d1044b56666634c9d62948a8e7873736200d0`，与既有发布zip `d41966dfc5b435a372798245300ca0ab7bb8e48bdbf48512c6fb20fcca427697`中`release/x64/TitanEngine.dll`逐字节相同。证明为`hw-exit/titanengine-disk-provenance.json`，只证明当前磁盘，不冒充本轮调试器加载模块清单或可重现构建证明。

## 主线选定的下一实验：非调试器原始启动

主线决定优先单次90秒原始启动对照，暂不更改固定调试器插件。目标是区分带断点的调试观察相关失败与不带调试器的原始启动失败，并寻找本轮自然资源/日志线索。

1. 主线先核无旧目标、固定五文件、命令行/工作目录/本机配置；用户明确就绪后观察启动UI。正常原始启动，不attach、注入、设点、装补丁或登录官方账号。
2. 全程持有目标OS句柄，记录实际退出码和PID归属；在预算内只读采样线程/模块与新日志、缓存文件时间/hash。进程I/O字节计数不证明读取了哪个文件；文件路径/blob消费只有实际文件事件、客户端日志或独立消费者证据才可记。
3. 如果存活超过旧约39秒且观察到自然界面，只支持此次原始启动较带断点观察更远；一次同时移除了调试器、TLS/EP、三个系统SW及HW多个变量，不能单独归因HW或据一次存活关闭问题。若仍退出，保留新OS/WER/日志，不套用本轮故障地址。
4. 预算到期记录明确状态，按主线既有正常关闭合同处理；未知进程/归属、未观测UI、读权限或短寿命对象缺口均如实记录。不把模块快照当完整加载时序，不把资源文件存在当已经消费。

## ThreadHideFromDebugger只读查询规格

当前WER、源码和39 DLL差集不能证明该标志。可将下一轮的只读查询作为独立附加观测，不能清除标志或据此绕过原生校验。

已固定[System Informer phnt ntpsapi.h](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpsapi.h)：枚举从0起，`ThreadHideFromDebugger`为17（`0x11`），声明查询返回BOOLEAN。下载SHA-256为`3477b3fa901715c5ad139ae154f86d6c36f3415962731d6c0de0dd3c8417f4d8`，只读来源证明`hw-exit/phnt-source.json`。这是该项目维护的native API定义，不是微软对class17的稳定公开保证；[微软文档](https://learn.microsoft.com/en-us/windows/win32/api/winternl/nf-winternl-ntqueryinformationthread)明确该API属于内部接口，需动态解析并处理不可用。

最小实现合同：

- 64位监测进程动态解析`ntdll!NtQueryInformationThread`，签名为`NTSTATUS(HANDLE, ULONG, PVOID, ULONG, PULONG)`。先仅对监测程序自己的线程查询，验证本机是否支持；不使用setter构造“隐藏”正例，也不连接调试器。
- 目标线程用`OpenThread(THREAD_QUERY_INFORMATION=0x40)`，以`GetProcessIdOfThread`核属于持有OS句柄的目标。该访问权含查询有限信息权限，依据[微软线程权限定义](https://learn.microsoft.com/en-us/windows/win32/procthread/thread-security-and-access-rights)，不请求SET_CONTEXT、SET_INFORMATION或SUSPEND_RESUME。
- class=`0x11`，输出为独立单字节BOOLEAN缓冲区，length=`1`，ReturnLength为独立ULONG。记录时间、目标归属、TID、NTSTATUS、ReturnLength、原始字节；仅成功且长度1、值0/1解释布尔值。导出缺失、拒绝访问、线程退出、非预期长度/值均为unknown；不默认为false、不扩大权限或重写上下文。
- 查询true只证明那个线程在采样时返回true；旧故障TID未知，不能回溯因果。全部false也不能排除两次采样间或短寿命线程。没有暂停线程或修改状态，故不是原子全进程快照。

本卡只给规格和固定来源，**没有执行任何native线程查询**。主线实现前先审API边界和自有查询结果；必要live采样由主线独占。

## 如果仍需插件观测

当前不实施。最小可审方案是独立新诊断插件，不修改固定Automate/TitanEngine：使用同版本SDK，注册CB_DEBUGEVENT与CB_BREAKPOINT/CB_EXCEPTION/CB_STOPDEBUG，按单调序号和时间直接写忽略文件，记录debug-event PID/TID/code/first-chance/address及高层回调对应关系；异常context仅从事件TID只读获取并记录成功/错误。不得调用继续/暂停/设置异常状态、内存/寄存器写入、SetInformationThread、返回替换或加载目标DLL。独立插件的源码、构建和DLL摘要及输出路径必须固定。

先用一个明确新增观测出口的自有fixture验证raw→高层对应、文件闭合、失败记录与无控制调用；不能把旧fixture原样重复算新成果。只有记录到最终异常，才可将缺口缩到该回调之后；未记录到仍保留Wait边界/REPLY_LATER等unknown。真正证明最底层接收还需独立记录WaitForDebugEvent返回与ContinueDebugEvent状态，单CB_DEBUGEVENT插件不能完成这个否定性结论。当前优先原始启动，不为此替换调试引擎。

## 验证与交付

实际执行[6项审计守卫](../tools/test_audit_client72_hw_exit.py)，覆盖hash漂移、WER PID/报告ID/code/RVA错配、错误AV继续、额外暂停、HW/CPU状态改变，以及WER模块存在不能提升为PUB加载/SDK成功；全部通过。实际原始run+WER+Report.wer审计通过，输出仍`rootCause=unknown`、客户端验收false。日志为`hw-exit/tests.log`。

```powershell
python -m unittest tools.test_audit_client72_hw_exit -v
python tools/audit_client72_hw_exit.py `
  --run local/migration72/d02-active/main/global-dispatch-resume1.json `
  --run-sha256 4ba34a38adbd960a033cfb1754ec1455daff804cbed8f376c0cb6f291cd4bbf4 `
  --wer local/migration72/d02-active/main/wer-after-hw.private.json `
  --wer-sha256 ff156b07f8eb195b4adf53915afde776550549a55bd2448382cbe892365f4d64 `
  --archive-report local/migration72/d02-active/main/wer-copy/Report.wer `
  --archive-report-sha256 d0aed5de85f56077a860236cf406e2d7a4672cb93c623e7b7b76c52ceaa35737 `
  --source-dir local/migration72/analysis/d02-r2-debugger-source `
  --output local/migration72/d02-active/hw-exit/audit.json
```

仅新增离线工具/测试/本专题，无运行文件、协议、配置、资源或补丁变更，不需构建部署。本卡没有Git提交权限，主线审核后负责本地提交并更新Project/Harness/活动记录。下一可执行项是主线协调的非调试器90秒原始启动监测，不再重复本轮HW参数。
