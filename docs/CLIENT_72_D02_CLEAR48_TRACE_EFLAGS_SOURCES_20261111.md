# CLEAR48 TRACE：EFLAGS 表示合同的定向来源

日期：2026-11-11。任务：F-03d3f4d2/CLEAR48-TRACE-EFLAGS-SOURCES。范围为固定微软 API 页面、Intel SDM 090、本地已有固定仓库资料的静态核对；不实施、修复或重试 runtime。

## 结论与边界

主线交付的同一 owned W1 停点记录为：Get `EFlags=0x202`，只将 TF 加入输入 `0x302`，Set 返回 TRUE，随后同停点 Get 得 `0x300`。本包将这些作为已冻结输入，未独立重跑；按该记录 TF 为 1，提交值与回读值仅差 reserved bit 1（`0x2`），其余记录中的控制/GPR 项不变。CALL 当时未 Continue，retired 为 0。原合同的逐位比较仍失败，不能在事后屏蔽该位并追认旧 W1。

| 来源 | 可支持的结论 | 不能支持的结论 |
| --- | --- | --- |
| [SetThreadContext](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setthreadcontext) | `ContextFlags` 选择设置部分；某些不可指定的 `CONTEXT` 值会被静默修正，例子包括涉及特权模式的 CPU 状态位、调试寄存器全局使能位等；非零返回表示上下文已设置。 | 文档没有点名 EFlags bit 1，也没有给出此机器上的完整修正掩码；TRUE 不保证所提交各位按原样读回或已经执行。 |
| [GetThreadContext](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getthreadcontext) | `ContextFlags` 选择获取部分；文档说明运行中线程不能取得有效 context，当前线程调用即使返回成功也不能保证结果有效。 | 页面没有承诺每个保存位等于先前提交值，也没有保证返回的是未经系统表示转换的硬件寄存器原样值。缺少保证不等于证明必定发生了转换。 |
| [x86 64-bit CONTEXT](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-context) | 该架构的公开结构中 `EFlags` 为 `DWORD`；结构本身是平台相关寄存器数据的表示。 | 成员声明未定义 bit 1 必须以何值保存在 `CONTEXT` 中，不能从宽度或名称推算 Get/Set 的逐位合同。 |
| [Intel SDM 090](https://cdrdv2-public.intel.com/874240/325462-090-sdm-vol-1-2abcd-3abcd-4.pdf)，Vol. 1 §1.3.2、§3.4.3、Figure 3-8、§3.4.3.4 | 图 3-8 在 EFLAGS bit 1 的保留位格中标记 1；§3.4.3 给出初始化值 `00000002H`，同时把 bit 1 列为保留位并明确软件不应依赖其状态。通用保留位指导包含测试前屏蔽、写入时遵循文档指定值或此前读值；64 位模式下 RFLAGS 低 32 位与 EFLAGS 对应。图表已从固定 PDF 单页渲染并目视复核。 | ISA 图、初始化值和保留位指导不能证明 Windows `CONTEXT` 的内存保存/回读必须是某个值，也不能确定此处由哪个环节改变、内核实际 mask 或之后执行态。 |

静态材料不能证明本次差异就是文档所说的静默修正，也不能证明其他位的处理或未来一次运行成功。处理新验收合同前应保留原始提交与回读值并单独审核证据；本报告不授权新的运行预算，也不更改原结果。

## 固定仓库材料的限定作用

- [TitanEngine 固定 README](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/readme.txt) 与已有分支元数据确认参考版本；[固定源码中的一个路径](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.Control.cpp#L59-L66)展示读取 context、对 `EFlags` 加入 TF、再设置 context。该局部路径未给出 W1 同停点逐位回读合同；代码是第三方历史实现类比，不证明当前 Windows 如何转换保留位，也不代表该项目其余控制流适用于本任务。
- [phnt 固定 README](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/README.md) 将项目描述为 Native API 头文件集合；[固定声明](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpsapi.h#L2609-L2638)给出取、设线程 context 的函数签名，未给出此处所需的保留位转换保证。它不是 Microsoft 对本机 Windows 实现的保证。

本包没有读取内核源码、系统二进制或目标代码，也没有操作客户端、进程、Debug API、CPU、构建或数据库。微软页面的原始响应、Intel 固定原件与派生页面、固定仓库的来源身份、UTC、哈希及失败均记入本机私有台账；共享内容不含本机路径或原始运行数据。实际安全门禁/Daybreak 未发生。

本次读取已有 phnt 参考 clone 的 README 时，因其是按需获取对象的 clone，原本意图只读的 Git 操作自动联网获取对象，并在该参考 clone 对象库写入了一个 pack；这是范围偏差，已记录并立即停止所有 Git 操作，未清理或改写该对象库。初始本地索引还在读取 README 前命中了既存头文件，之后已核对固定 README/ref 并重新审阅相关声明。三次微软 GET 加上述一次意外 Git 懒获取按 4 次高层网络操作计；懒获取内部 HTTP 请求数和原始传输未采集，不将两者等同。本项目仓库 Git 未操作；本项交主线审核后处理提交。