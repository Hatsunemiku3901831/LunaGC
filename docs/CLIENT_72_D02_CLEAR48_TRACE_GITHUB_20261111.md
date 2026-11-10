# F-03d3f4d2/CLEAR48-NATIVE-TRACE-GITHUB：有界静态检索

2026-11-11（本机日期）。按已冻结中文计划 `a-clear48-native-trace-spec/github-plan.zh.md`，SHA-256 `e2771659f9706423d1c20ae79fe5db791bbe4e2439a66e86ca7f13d76e70fbfe`，只研究对自建 Windows x64 child 和自有代码/data 的单步思路。没有实现或运行 trace、编译、安装、操作目标进程、采集新客户端代码/CPU/live/数据库。此卡不验原 6B/SEH、原 904B、自然 GC/Avatar 或 D-02；其结果须由主线另行验收。固定旧 [Unicorn 上游审计](CLIENT_72_D02_UNICORN_AVX_SOURCES_20261111.md)和 [native 能力规格](CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_20261111.md)直接复用。

## 推荐与边界

推荐后继规格选择 **Win32 自建 child 的调试事件循环**，仅把 TitanEngine 的事件线程关联与重设 TF 当静态设计参考，依据微软 API 合同编写受限流程；本卡没有复制它的代码。第二方案是整合 TitanEngine 运行库：其功能多、默认行为与本合同有实质差异，且有 LGPL-3.0 义务，本次不采用。此前固定 Capstone 离线清单优先，不为 32B 存储引入新 decoder 或完整 DBI；本次没有验证另一个 decoder 或进程内 VEH 实现，不能推断它们可用/不可用。

外部源码可证明有限模式，不证明 904B 自然完成、每次内存写入、YMM、实际异常或 CALL/RET。尤其必须先有 root 新建并验收的原 6B 能力/SEH 结果；**904B 未知外跳、读写域、真实 RET/RSP 和 YMM 状态均仍待新的自有 witness**。不因本次静审提前标记通过。

## README、分支、许可证与克隆台账

- 定向搜索先查仓库和 README：`QzOS/zdbg` 的搜索摘要声称 Win64 自建进程/TF，但实际 GitHub 仓库 API 返回 **404**，未重复请求，身份、版本和许可均未知，不采纳。`x64dbg/x64dbg` README 只确认其引用 TitanEngine 作为调试引擎，未深入该大仓库。微软样例搜索命中的是无关 ETW provider，未进入源码。三份仓库级 README 摘要/原件在上限四之内；搜索结果中的其他代码片段未作为源码证据。
- [TitanEngine README](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/readme.txt)自称 Windows x64 引擎并列异常和系统断点修复；README 本身没有证明 904B 所需逐指令/EA/YMM 合同，源码是后续有限静审结果。[根 LICENSE](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/LICENSE)为 LGPL-3.0；若将来复制或分发其实现，先按具体使用方式评估许可/声明义务。本卡仅参考并引用，不主张已完成法律判断，也未审核全部依赖许可。
- GitHub 仓库元数据默认 `x64dbg`，非 archived；API 所报 `pushed_at=2026-09-07T00:42:31Z`。分支首屏八项且 `Link` 头为空：`arm-on-windows`、`dbgeng`、`dp0bugfix`、`dynmem`、`master`、`scylla_lib`、`vs2010`、`x64dbg`。只比较两个准确 ref：`x64dbg` commit `947ad6107f348489350838470d5a69394e1c5f13`、tree `b0333e61e7435e0fbb0cfb0ed301d043807f12f9`；`master` commit `e089f4af41a461b69017db3750f79fbaed1008df`、tree `a8b13411658d6e11cd47844dbaf18a0d836a92ea`，commit 日期 2016-04-22。两树 README 和 LICENSE blob 相同，三个已比较的主要源码 blob 不同；未阅读 master 源码或其余分支，不能声称全面差异/维护状况。
- 已按授权在项目外 `<D02_REFERENCES>/clear48-trace-titan-947ad610-20261111/` 进行一次 `--depth 1 --single-branch --no-tags --no-checkout` 克隆，HEAD/tree 与上述 API 固定值相同；没有 checkout、递归子模块、编译、安装或执行仓库代码。实际路径和原始 API/clone 记录仅在忽略目录 `a-clear48-trace-github/`。

README、license 和本轮实际静审源码逐字节从 Git blob 计算 SHA-1 并与固定 tree 相符；完整证明 `titan-fixed-blob-proof.json` SHA-256 `8c709bd5cff7616d0a13ebf4e0617e8d1a89c3d14eb3803fd6b9fd985e1f3170`。下表 SHA-256 是解码后原文件字节，非网页 HTML：

| 固定 ref 下路径 | Git blob | SHA-256 |
| --- | --- | --- |
| `readme.txt` | `6f7443996c813173fb6490d7df35d7466613d1fa` | `201e91fd081b5e500e7531a1747c6b274689369c001cced0b7326474d5375822` |
| `LICENSE` | `65c5ca88a67c30becee01c5a8816d964b03862f9` | `da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768` |
| `TitanEngine/TitanEngine.Debugger.DebugLoop.cpp` | `18b2b45dc754806663c071a32643fd44611890e8` | `8b4ff26eb26849bc51b92f1b46b3fd535474863dc8bfcdcc5801b9985dac2459` |
| `TitanEngine/TitanEngine.Debugger.cpp` | `c39e2ac9acce0dbbe719130a2b4b96b2166f1df2` | `2dbe6bd885e67d98bb3a9db6a319ab47f9141a8bf73510a2782d908812107d32` |
| `TitanEngine/Global.Engine.Context.cpp` | `e9a42036d794d537d16ddea8126211c4f8da0f8c` | `17463832ce0fabfe209bd849468b835654db28b345ccb5c123a9913b7609c6a5` |
| `TitanEngine/TitanEngine.Debugger.Control.cpp` | `c448b31b958d2cd46a31b704eac1169def098c35` | `ea7eca9759e0f08a55215b36fa20e4390f39a210e84cf5f417199804e8038293` |
| `TitanEngine/Global.Debugger.cpp` | `bca4e7f6065d277bfafc79f3c0cf289920e233fd` | `d78be5548d63a8fae2cbca14ee2cb617e406ae9aeed0285bba69edca2f8b7cb7` |
| `TitanEngine/Global.Debugger.h` | `cc3f5bed5fd7951353a5e1d0b6330604206d91a2` | `1a59ef33f64f954e0c25ab55793817ec99646b2f1818f83ff1292d9426702e18` |

因此 license/header/source 合计七 blob，README 单列；实际深入一个仓库、两个 tree，且只审核其中一 ref 的六源码/header。无全网覆盖之称。

## 可参考内容与明确不采用的差异

1. [InitDebugW](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.cpp#L306-L350) 默认使用创建进程调试标志；只是启动机制参考。其可选无 ASLR 路径含进程改造、终止与重试，整段不采用。未来只允许按批准的身份创建一个自己的 child，失败即停止。
2. [DebugLoop](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp#L37-L67)以事件线程 ID 关联 pending step；[StepInto](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.Control.cpp#L39-L68)从该线程取上下文、对 EFlags 设 TF 并保存状态，[single-step 分支](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp#L759-L798)接收事件；这是事件驱动与线程绑定的可复用思路，不是原样可移植的完整有限状态机。代码中存在未逐次检查的 Get/Set 返回值；本合同要记录失败并停止。
3. **实际上下文组并非 CONTROL-only。** [Global.Debugger.cpp 44–70](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/Global.Debugger.cpp#L44-L70)令 `ContextControlFlags` 通常为 `CONTEXT_CONTROL | CONTEXT_DEBUG_REGISTERS`，在该文件描述的 ARM 环境变为 `CONTEXT_ALL`。另一个 [完整 context 路径](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/Global.Engine.Context.cpp#L185-L198)还涉及浮点/扩展状态。都不能证明只写 TF 时 YMM 高半保存；不采用完整 context 回写。
4. 它的 [StepOver](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.Control.cpp#L73-L86) 对 CALL/REP 等通过临时软件断点步过，[断点处理](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp#L630-L653)改回原字节并调整 RIP，另有 [栈写](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp#L24-L34)以及硬件/页断点、attach 等；均不用于本合同。系统首个断点的源码处理只表明该引擎的分类方式，不证明新 child 的入口握手或实际 RIP 约定。
5. [异常处理](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp#L587-L608)默认给出不同 first/second-chance 续行策略，有时把二次异常标记 handled。新流程应保留异常码、地址、first-chance、PID/TID 和原上下文；任何非预期异常、越界、预算耗尽或外跳均按批准合同失败，不能把它们续行为成功。超时和退出也要保留自己的 child 身份和准确状态，不借其他进程补救。

## 主要来源合同与仍缺 witness

- 已固定的 Intel [SDM 090 PDF](https://cdrdv2-public.intel.com/874240/325462-090-sdm-vol-1-2abcd-3abcd-4.pdf) SHA-256 `3686e42931e893b5016c70fb131403795ef173237d073afcfec52755fcd3ea50`，PDF 第 3360 页（Vol. 3A 7-25）把 single-step 列为 trap，区分 fault/trap 保存指令指针；PDF 第 3812 页（Vol. 3B §20.3.1.4、20-11）说明 TF 条件和异常处理入口前清 TF，并列出特殊指令例外。这里的 ISA 定义不等同 Windows 调试事件实测：不能假定所有 `STATUS_SINGLE_STEP` 都是所需 TF 事件，不能假定 Windows 返回 CONTEXT 中 TF 的具体值，也不能把 RIP 按字节线性加长当作实际 CALL/RET 后继。自有 witness 应校准实际 event/CONTEXT、精确前后 RIP/RSP、控制流和 TF 重设。
- [Microsoft Debugging Events](https://learn.microsoft.com/en-us/windows/win32/debug/debugging-events)说明事件通知时受影响进程线程暂停、事件携带进程/线程信息及创建/退出相关句柄；[GetThreadContext](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getthreadcontext)和[SetThreadContext](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setthreadcontext)按 ContextFlags 选择上下文组且不应对运行线程使用；[ContinueDebugEvent](https://learn.microsoft.com/en-us/windows/win32/api/debugapi/nf-debugapi-continuedebugevent)区分 handled 与 not-handled，须用报告该事件的 PID/TID，只有创建被调试进程的线程可调用。句柄归属、每次调用返回值和先/后二次异常须按实际 API 合同核对，不能一律 CloseHandle 或续行。
- [LocateXStateFeature](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-locatexstatefeature)要求已按 XState 初始化的 CONTEXT，特性位置/大小不能以固定偏移猜测。普通 XMM/CONTEXT 不能充当完整 YMM 证据；即使后继仅设置 CONTROL，仍需独立、自有、不同高低 lane 的真实 before/after 向量样例证明本机内核/调用路径的状态保留，不能从 API 文案推断通过。
- 有限方案在前一已知停止点依据真实寄存器和固定清单计算下一条所有显式和隐式内存操作，包括 CALL/RET 栈和 RIP-relative，用真实宽度检查 owned 允许区间；未知目标、溢出、未覆盖 operand 或地址失败即停止。对每笔 32B 重叠写分别在执行前后取该次 owned 区间真实 old/new 并绑定原 PC；不可仅比较终态、模拟 host 指令、跳过 RET 或写入预期值。该提议依赖清单完整性、单线程/其他线程隔离、事件前后状态与 ReadProcessMemory 可用性，当前都没有本机实证。

## 实际预算、失败与后继

高层操作预算：4/4 query、13 次 GitHub API GET（其中一次 404）、1 次 Git clone transport、6 次 Microsoft Learn URL open，共 **24/24**；工具层三次 web 调用合并了 4 个查询和 6 次页面读取。clone 内部 HTTP 协商次数未作网络包级计数，不冒称精确底层请求数；全部实际入口及失败顺序在忽略目录 `network-ledger.md`。Microsoft Learn 返回的可读正文上方含通用“需要授权”页面提示；正文实际可读，没有登录、绕过或获得工具安全拒绝，后续无其他网络访问。预算到此结束，覆盖为 partial。

本次实际验证：固定计划 SHA；Git blob 与两 tree/两个 ref 的关联；外部浅克隆 HEAD/tree；Intel 原 PDF SHA 与列明页面的离线文本提取；源码逐行静审。没有运行单步/测试、读取 host CPU 或客户端。原 PDF 页提取与检查可重放自忽略目录 `intel-extract-pages.py`，其输出清单 `intel-page-proof.json` SHA-256 `ae294483c3d6c277f3b143008f50e4eaffaa22d9f090b78e618677b36a5d15da`。

下一项：交主线审阅本 ledger，并由 Sol max 在独立 trace 规格中决定 owned child 入口握手、线程/事件生命周期、EA/隐式栈前置门、失败停止及 CALL/RET/YMM witness；由主线另批能力/SEH实际结果和后继执行范围。不得把此文、源码静审、编译或 API 健康当作客户端验收。