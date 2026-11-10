# F-03d3f4d2 / CLEAR48-TRACE-EXIT-LIFECYCLE：事件与句柄来源

2026-11-11。本包只为自有 trace 完成后的 WAIT_EXIT 和失败清理补 Win32 公开来源；不是实现规格、运行许可或客户端验收。经核的冻结摘要 SHA-256 为 `07e8d8e98dc64d0834798ee82de54d166709be37d5439d71acb3ab4e10e758c4`。已批准计划记载第三次完整 W1 在三条退休并恢复 TF 后，因同一 owned PID 的新 TID 创建事件被原身份门拒绝；失败清理中有 primary EXIT_THREAD 和新 TID EXIT_PROCESS。冻结记录仍为 `unknownPidOrThread`、child exit 90、`cleanupComplete=false`；前三次 W1 仍失败，W2/W3 未起。本包未重跑或改写原始证据，也不将 child 自报回 C 当作正常 OS 退出。

## 官方结论与边界

| 问题 | 来源支持的范围 | 不能推出或本包保留的边界 |
| --- | --- | --- |
| 事件 PID/TID | [DEBUG_EVENT](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-debug_event) 的 `dwProcessId`、`dwThreadId` 分别表示发生事件的进程和线程；成功 [WaitForDebugEvent](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-waitfordebugevent) 返回实际结构。主线程的创建在 CREATE_PROCESS 事件中，CREATE_THREAD 不包含它。 | 不能用事先预期的 primary TID 代替每次实际返回值。CREATE_THREAD 的事件 TID 与新线程句柄属于同一登记项，是结合事件描述的常规解释，第三方固定实现亦如此；本包没有找到明写 `dwThreadId == GetThreadId(hThread)` 的独立等式，也未调用 ID API 验证。 |
| CREATE_THREAD 信息 | [CREATE_THREAD_DEBUG_INFO](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-create_thread_debug_info) 提供因创建而触发事件的线程 `hThread`、`lpThreadLocalBase` 和 `lpStartAddress`；NULL `hThread` 无效，否则文档列出 context 和 suspend/resume 权限。[Debugging Events](https://learn.microsoft.com/en-us/windows/win32/debug/debugging-events) 描述事件发生在新线程开始用户态执行前。 | 文档称 start address 可能只是近似，不能由此、TLS 地址或相邻模块事件证明具体创建者、执行起点、系统模块内部行为或 CRT 路径；不凭列出的权限增加 ID 查询权假设。页面中 TLS 偏移的文字不在此升级成 x64 布局保证。 |
| EXIT_THREAD 与最终 EXIT_PROCESS | [Debugging Events](https://learn.microsoft.com/en-us/windows/win32/debug/debugging-events) 一般描述线程退出时报告 EXIT_THREAD；明确最后线程退出不再单发 EXIT_THREAD，而改为 EXIT_PROCESS。[EXIT_THREAD_DEBUG_INFO](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-exit_thread_debug_info) 和 [EXIT_PROCESS_DEBUG_INFO](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-exit_process_debug_info) 分别携带线程和进程退出码。 | 因此不能要求每个线程都先有 EXIT_THREAD，也没有官方依据要求最终事件 TID 等于初始 TID。已读页面未逐项枚举 ExitProcess/TerminateProcess 并发终止路径的完整 debug-event 顺序，不能据此补造实际事件、确定某个线程一定最后退出或推断该次 OS 正常退出。 |
| 暂停与继续 | [Debugging Events](https://learn.microsoft.com/en-us/windows/win32/debug/debugging-events) 称通知事件时受影响进程的线程被暂停；[ContinueDebugEvent](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-continuedebugevent) 用此前报告事件的 PID/TID 继续，并给出成功/失败返回。文档要求由创建被调试进程的线程调用 Wait 和 Continue；[主循环示例](https://learn.microsoft.com/en-us/windows/win32/debug/writing-the-debugger-s-main-loop)将实际事件的两个 ID 传给 Continue。 | 对“只允许已登记 owned 线程”“何时转入 WAIT_EXIT”“失败后可继续什么”须由本项目另定有界合同，不是微软对所有 debugger 强制的状态机。不能以调用 Continue 代替其成功返回，也不能由旧 TID 或失败 wait 造成功事件。 |
| 事件进程/线程句柄 | [WaitForDebugEvent](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-waitfordebugevent) 明确指出 CREATE_PROCESS 与 CREATE_THREAD 在 DEBUG_EVENT 中返回对应句柄；[ContinueDebugEvent](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-continuedebugevent) 规定成功继续 EXIT_THREAD 后关闭调试器的相应线程句柄；成功继续 EXIT_PROCESS 后关闭调试器进程及线程句柄。Debugging Events 对进程退出描述为系统关闭调试器的进程和全部线程句柄。 | 这里的自动关闭以实际退出事件及成功 Continue 为依据；此前已关闭的句柄不再计算为剩余持有句柄。这不是本项目调用 `CloseHandle` 得到 `TRUE`。不得伪报手动关闭，也不能对事件提供的线程句柄额外双关。失败 Continue 不能按成功的关闭路径记账。 |
| `PROCESS_INFORMATION` 句柄 | [PROCESS_INFORMATION](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/ns-processthreadsapi-process_information) 另描述创建进程返回的 `hProcess`、`hThread`，要求使用完分别 `CloseHandle`；进程/线程对象保留到最后的相关句柄释放。 | 按句柄**来源**分别记录 PI 的实际 close 返回和 DEBUG_EVENT 的自动关闭，不把后者挪作 PI 的 CloseHandle 证据。这些页面没有证明两来源的句柄数值恒相等或恒不相等，本包不作该断言。 |
| image/DLL 文件句柄 | [CREATE_PROCESS_DEBUG_INFO](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-create_process_debug_info)、[LOAD_DLL_DEBUG_INFO](https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-load_dll_debug_info) 对 `hFile` 均说明 NULL 无效；有效文件句柄由 debugger 使用 `CloseHandle`。文件名可缺失，LOAD 的 `lpBaseOfDll` 是被加载进程中的基址。 | 只记录实际收到的字段和真实 CloseHandle 返回；已读结构页未单独规定 `INVALID_HANDLE_VALUE` 情形，不将它猜成新规则或修改现有事件处理。文件名、DLL 名及相邻事件不证明谁创建线程。 |
| UNLOAD | [Debugging Events](https://learn.microsoft.com/en-us/windows/win32/debug/debugging-events) 将 UNLOAD 描述为 DLL 最后卸载时的事件，其信息包含 DLL 基址；进程退出自动卸载 DLL 不产生该 UNLOAD 事件。 | 不能要求退出时每个已加载 DLL 都有对应 UNLOAD，也不能从卸载事件推导线程来源。此页已覆盖本问题，故没有单取 UNLOAD 结构页。 |
| ExitProcess / ExitThread | [ExitProcess](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-exitprocess) 描述进程结束时其余线程终止且不接收 DLL_THREAD_DETACH，DLL 随后收到 DLL_PROCESS_DETACH；文档说明从应用 main 返回会调用 ExitProcess。[ExitThread](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-exitthread) 说明最后线程退出时进程也终止。 | 子进程自己的“回 C”状态不能代替 debugger 接收到的 EXIT_PROCESS、成功 Continue 和进程对象完成信号；也不能将 DLL 通知过程扩写成已经观测的 debug 事件顺序。 |
| 失败终止 | [TerminateProcess](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-terminateprocess) 需要有 PROCESS_TERMINATE 权限；终止其他进程时为异步发起，可等待 process handle signaled 确认终止完成。 | 成功 BOOL 只证明请求成功发起，不证明退出码、退出事件或 OS 已退出。实际失败、先前已记录的首失败和清理范围不能因发起终止而变成成功。 |
| TID 复用 | [PROCESS_INFORMATION](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/ns-processthreadsapi-process_information) 说明线程对象释放后其 ID 可能复用。 | “一个 trace 生命周期中拒绝同一数值重复登记”可作为另审的保守上限，而不是 OS 保证不重用；若后继支持复用，应凭实际创建事件区分代际。 |

上述资料是 Windows 桌面 API 的公开合同，多数结构页标注 Windows XP 及以上，不是对某台机器、某个 x64 用户态模块或任何单次实验的具体行为证明。TRACE 保持 primary 身份、WAIT_EXIT 登记条件、继续失败处理、容量/时间预算和 validator 必须由合同专题另行审核确定；本页没有扩大已批准事件集合。

## 既有仓库复用与差异

先读旧私有台账定位已有导出，再读 [TitanEngine 固定 README](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/readme.txt)、许可与固定源码，没有访问既有 clone 或 Git 对象库，也没有执行 Git 命令。README 自述为修复/增强的 Windows 调试引擎，包含 x64 支持；根 [LICENSE](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/LICENSE) 为 LGPL-3.0。本包只参考事件循环，不复制实现；将来实际分发或复制时的具体许可问题需单独评估。

旧 API 元数据列出八个分支；直接复用的 `x64dbg` 固定 commit `947ad6107f348489350838470d5a69394e1c5f13`，`master` 固定 commit `e089f4af41a461b69017db3750f79fbaed1008df`。现有树元数据表明两者 README 与 LICENSE blob 一致，DebugLoop blob 分别为 `18b2b45dc754806663c071a32643fd44611890e8` 和 `51e427f1d4824320916db4033cd73a06705ca9e7`；本包没有读取 master 的 DebugLoop，不能声称完整分支差异。旧 README、LICENSE、x64dbg DebugLoop 的导出原字节及 blob 摘要已独立文件校验：

| 导出文件 | bytes | SHA-256 | 与本合同的有限关系 |
| --- | --- | --- | --- |
| `titan-readme.txt` | 1314 | `201e91fd081b5e500e7531a1747c6b274689369c001cced0b7326474d5375822` | 先核项目目的，再看相关已导出源码。 |
| `titan-LICENSE` | 7651 | `da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768` | 根许可为 LGPL-3.0；本包未复制运行实现。 |
| `titan-debugloop.cpp` | 76090 | `8b4ff26eb26849bc51b92f1b46b3fd535474863dc8bfcdcc5801b9985dac2459` | CREATE_THREAD 登记事件 TID/handle/start/TLS，EXIT_THREAD 从列表删除，常规继续使用事件 PID/TID；关闭创建进程和 DLL 的文件句柄。 |

[固定 DebugLoop](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp) 还在一次 Wait 失败但进程已 signal 的分支自行构造 EXIT_PROCESS 并赋 PI primary TID（约第 138–153 行）。这是该第三方代码的行为，不是 Win32 返回的真实事件；与本包要求保留实际 wait/event 身份、不补造结果不兼容。其 callback、OpenThread、硬件断点、attach 等通用引擎功能也没有被纳入本任务。没有为此新 clone 或进入其他分支源码。

## 本次来源与核验

原始 HTTP 14 页均为 200、无重定向；每页完整 URL、开始/结束 UTC、响应头、原响应 bytes/SHA-256、显示日期、`ms.date`、`updated_at` 和正文派生文件哈希见私有 `local/migration72/d02-resume-20261110b/a-clear48-trace-exit-lifecycle-sources/network-ledger.md` 与 `http-manifest.private.json`。原始 HTML 为权威副本；`.article.txt` 是离线抽取，较宽的 `.technical.txt` 还包含页面模板。每页的授权提示在原 HTML 中均标为默认隐藏，技术正文已返回，不能把该模板误记为安全拒绝。

为确认两个事件身份/终止顺序歧义，另在一次 web 调用中提交两条微软限定查询，按 2 次高层操作计；结果主要是现有页面的多语言版本，没有新增可用的精确互证，也没有另开页面。计划原预见的查询为必要时定向 GitHub README，本次查询主题调整为微软合同歧义，属查询类型偏差，已在私有台账记录；其精确请求 UTC 和底层响应未采集，不补造。实际总计 **16/18**：官方 GET 14、查询 2、GitHub blob/ref GET 0；无 GitHub 新检索或 clone。一次本地 Python 单行汇总因 PowerShell 引号引起 SyntaxError，未修改原件；后续独立文件校验成功。实际没有安全/Daybreak 拒绝。

本轮只做公开来源获取与纯文件校验，没有调用 Debug API、运行 native/CPU、查询或控制进程、打开客户端/PE/系统二进制、构建、部署或操作数据库。源码、四份设计 JSON、实际日志和父/验证器均未修改；共享索引与提交由主线负责。该来源包不提升 W1/W2/W3、完整 helper、Avatar 或 D-02 的验收状态；下一步由合同审阅方结合固定原件完成有界规格，是否实现和新 epoch 由主线另行决定。
