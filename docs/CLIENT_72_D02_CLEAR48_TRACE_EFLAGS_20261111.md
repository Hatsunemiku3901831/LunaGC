# F-03d3f4d2/CLEAR48-TRACE-EFLAGS-READBACK

2026-11-11。本文只分析主线唯一 W1 的原始失败和公开合同；本分析代理没有新增 native 运行、Debug API、CPU/进程/客户端操作、构建、网络、数据库或 Git。可写仅本文与私有 `a-clear48-trace-eflags/`。原 [trace 合同](CLIENT_72_D02_CLEAR48_TRACE_IMPLEMENTATION_CONTRACT_20261111.md)、四设计 JSON、header、C/ASM、runner、validator 和 root 构建/审核原件均未修改。root 另派 Terra max 依本文私有中文有界计划核官方来源，其实际网络及偏差见下；没有把普通 API 表示问题自动归入 Daybreak。

## 当前结论

原 W1 按原合同**失败且保持失败**。本次直接证明：parent 提交侧只改变 TF；同一暂停期的成功回读额外清除了原 `EFlags` 的 bit1。它没有证明 CALL 已执行、硬件运行态 RFLAGS.bit1 为 0、当前 Windows 使用了某个内部 sanitize mask，也没有证明 TF 单步或 YMM 保存。保留 `before=00000202`、`submitted=00000302`、`readback=00000300`，不能重写为相等。

问题位于两种不同断言之间：**程序提交时只改 TF**是输入约束，当前实现满足；**Windows 回读的整个 EFlags 必须与提交逐位相等**是额外的输出约束，当前 W1 未满足。公开 SDK、Intel 对保留位的规则和本次原件必须分别记录，不能用硬件图中的常量替代 Windows `CONTEXT` 表示。

## 原件与源码冻结

相对私有根为 `local/migration72/d02-resume-20261110b/`。`a-clear48-trace-eflags/freeze_failure.py` 仅做标准库文件读取/哈希/JSON 关联和整数断言；生成 `failure-source-proof.private.json`，10631B，SHA-256 `876b892e90bf754b0d8d5b5e4b307197b48066de8b660c88348174e5bdc4abc0`。该 proof 明确 `originalRunRemainsFailed=true`，不是 validator 的正常 trace 通过报告。

| 输入 | SHA-256 |
| --- | --- |
| 当前和 build-main3 快照 `parent.c`（各 74416B） | `e5b60ee368d724e947e2aad0d12fa2cbe1fe6201506d3775f441cc02a6f67cea` |
| root 依赖/源码/构建审核 | `7d4d17e850a9ec0c489d3608b67e3efd1f0f9cca2693e700593e41f49f59eda7` |
| root pe-main3 审核 | `d8db0feedb7a7e523ea51e419255a7e0d2e8db51f1b6c8ab8f9ed5bf742e6ea4` |
| `w1-main1-20261111/native/events.jsonl` | `7377c54ffa294c714214f8179476ffe0a2c9a077f44775623f952cc8dd306932` |
| 同轮 `receipt.json`（59355B） | `bbc4e1859f8e60fa6dfe2363ef7db1cfaaaffdc2ba6638287b91cd066dbff410` |
| 同轮 `native/parent-result.json`（1282B） | `480a7f9d803f4a55144d4ed8adcfa500932fa9c6da1ca98e9f79a3abbcf67eb9` |

PE 身份沿原回执和已核 root 审查，不在本卡重开 PE 或系统 binary 窗口：parent EXE `84547fc4a8c411d78f693f3b926ceb37063cdb7e70200cbbba0d257510467bb6`；child EXE `9616750854a1e9f99360835b598128e22d9b97a5c729e4ac235937ea01de65fb`。root 已核 build-main3 exit0、真实 caller/unwind、6 源/129 include/5 library/2 compiler DLL/10 日志；本卡仅核引用审核文件与源快照，不重复冒称独立完成这些底层审核。

## 同一停点的事实链

事件序号是 JSONL 全记录序号，并非 debug event 计数。Stop29 是自有 INT3，真实异常地址为 Arm，Get30 的实际 RIP 已等于 Call；没有写 RIP 纠偏。

| 原记录 | 实际内容 | 能证明的范围 |
| --- | --- | --- |
| Get30，ordinal2，stop29 | CONTROL\|INTEGER；EFlags `202`，TF=0 | 本次成功 API 输出，不代表未来硬件状态 |
| memory32–36 | descriptor256B、wrapper25B、leaf11B、CALL bytes3B、slot old8B | 5 读共303B；尚无真实 CALL 栈写 |
| Set 调用；其记录为38 | CONTROL，复制同 stop 的 SegCs/SegSs/Rsp/Rip；仅将 TF 置1，提交 `302`；BOOL=true | `202 XOR 302 = 100`；输入侧无 bit1 修改 |
| Get37，ordinal3，stop29 | 成功；EFlags `300`；全部15个 GPR、SegCs/SegSs/Rsp/Rip 与 Get30 相同 | `302 XOR 300 = 2`；TF 正确为1，唯一额外 raw 差为 bit1 |
| transaction39 | tx1，retired=false，status=`not_executed`；continue/post 均 null | 未尝试授权 CALL Continue，已知尚未执行 |
| cleanup40→continue41 | 先自有 PI 句柄 TerminateProcess(90) 成功，再 cleanupOnly Continue | 清理继续不能当作 CALL 退休 |

Get37 在 Set38 **记录**之前，是因为 `set_tf` 在调用 Set 后先调用并记录回读 Get，再记录引用该 Get ordinal 的 Set。源码不是先 Get 再 Set。`line_control` 直接以 SDK `DWORD EFlags` 格式化，不在 logger 中移除 bit1。

`parent.c` 的 `set_tf` 418–465 行复制控制字段，436 行仅改变 TF，437 行调用 Set；446 行的 `readback->EFlags == submitted.EFlags` 是失败合取项。validator 的 177 行另要求 `rb.control == readbackControl == submittedControl`，同样包含整个 raw EFlags equality；179 行检查 before→submitted 只能改变 TF。现有四 JSON 的 `onlyMutableMask=00000100`、sameStoppedGetCopy 与必需 raw 字段保持冻结，不能将输入 mask 偷换为回读 mask。

原 summary：firstFailure=`setReadbackMismatch`，parent exit2，8 debug events/8 waits/3 Get/1 Set/5 reads303B/8 Continue/0 retired；其中 cleanup Continue 两次。自有 child exit90、EXIT observed/continued、process signaled、PI thread/process close 和 cleanupComplete 均 true，root before=after、timedOut=false。只有 W1 一次，W2/W3 没有启动。该失败不是安全工具拒绝；没有可据此登记 Daybreak 的证据。

## 公开资料能证明什么

### 本机固定 WinSDK 与现有外部参考

`audit_local_sources.py` 重新核四个冻结 SDK 头的大小/SHA 并保存精确行段，输出 `local-sdk-reference-proof.private.json`，11899B，SHA-256 `414f807d6dc183a2d2915ce260ed994f7c72be1375b5150e9773304ae4879a78`。WinSDK 10.0.26100.0 `winnt.h` SHA 为 `8693f0ad4ada355c912d40ca41ce929b480e92291bda2f9227acf56736bffbff`。

AMD64 的 `CONTEXT_CONTROL` 指定 SegSs、Rsp、SegCs、Rip、EFlags；`CONTEXT_INTEGER` 指定另15个 GPR，`EFlags` 类型是 DWORD。Get 的参数为 in/out CONTEXT，Set 为 const CONTEXT。头文件提供布局和分组，没有给出该 Windows build 的 bit1 转换函数、mask 或逐位相等保证。SDK 中 `MUST BE SANITIZED` 注释出现在 i386/WOW64 的结构，不能挪作 AMD64 当前实现证明。

已固定 [phnt commit](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpsapi.h#L2610-L2638)只在本次相关范围提供 NtGetContextThread/NtSetContextThread 声明；已固定 [Titan StepInto](https://github.com/x64dbg/TitanEngine/blob/947ad6107f348489350838470d5a69394e1c5f13/TitanEngine/TitanEngine.Debugger.Control.cpp#L39-L69)对取回的 EFlags OR TF，然后调用 Set，不做本次要求的同 stop raw equality。Titan 通常还请求 DEBUG_REGISTERS，且没有检查本卡全部返回值。它们均不是 Windows 当前实现，不能用来宣布本机 TF 或保留位行为已验；不导入其代码或放宽上下文组。

### Intel：bit1 的准确地位

固定 [Intel SDM 090](https://cdrdv2-public.intel.com/874240/325462-090-sdm-vol-1-2abcd-3abcd-4.pdf)，SHA-256 `3686e42931e893b5016c70fb131403795ef173237d073afcfec52755fcd3ea50`，本轮复用本机原件，未重新下载。Terra 抽取 PDF 1-based 第33、79–82页；本代理读全文并独立目视第80页图和核 PNG SHA `ae704cc1b30e875bc8838c757352a38a7ce1420ca4c373654cc801ff78d2f529`。

第79页 Vol.1 §3.4.3 明列 bit1、3、5、15、22–31 为 reserved，处理器初始化时 EFLAGS=2。第80页 Figure3-8 中 bit1 的灰格标1，图例要求保留位按先前读取值写回。第33页 §1.3.2 的比较/回写指导同样要求避免依赖保留位；第82页 §3.4.3.4 说明 RFLAGS 低32位同 EFLAGS。TF 是明确的 bit8，区别于 reserved bit1。

这些来源支持“提交沿同 stop 原值，只改 TF”及“不要将 reserved bit 的 raw 恒等当作应用语义”。它们**没有**规定 Windows CONTEXT 必须将 bit1 存为1；图示和初始化值也不足以给本次未恢复执行的 child 补造硬件读值。不能把 SDM 的通用保留位指导扩成忽略本卡所有非 TF 位。现有 `0x2` 差异必须原样公开，其他位仍严格检查。

### Microsoft Get/Set：接口选择与修正边界

Terra max 固定三份 HTTP200 原始 HTML、UTC、响应头和可读正文。本代理已读取三份完整技术正文；HTML 中默认隐藏的通用授权提示不是实际技术正文访问拒绝，未登录或绕过。此前 trace-GitHub ledger 没有保存 HTML，本轮不为旧网页编造 SHA。来源子包实际4/10次高层网络操作：三次官方 GET，另一次在读取既有 partial clone 的 phnt README 时 Git 意外懒取，并向项目外参考对象库写入 pack；该偏差已报告 root、登记其 ledger 并停止 Git，不能记成零网络/只读 Git 或再作懒取。它不提供当前 Windows 内部证明，也不是平台安全拒绝。本分析代理未调用该 Git 或网络路径。

| 官方页面 | 本轮原始 HTML bytes / SHA-256 |
| --- | --- |
| [GetThreadContext](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getthreadcontext) | 54161 / `217bd958eb0d3e1938032e04a0483825ee00b02ae65f6d946f2bcdeb235cd6d6` |
| [SetThreadContext](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setthreadcontext) | 53919 / `9f6d8703e56ca094ab5d93d6f341ca21110b8edcee074aba4697762850cad709` |
| [x64 CONTEXT](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-context) | 54366 / `5c14e51217b6e12bb7fe2018017870cf4609624dd0cba9c8c03f835b684309ba` |

Set 文档说明由 ContextFlags 选择设置的部分，并明确某些调用者不能指定的 CONTEXT 值会被静默修正，例子包括特权模式状态位和调试寄存器全局启用位。非零返回值表示上下文已设置。它没有点名 bit1、给出 AMD64 全部 EFlags mask，或承诺提交值原样返回；不能据此放行任何非 TF 差异。Get 文档说明按 ContextFlags 提取、结构依架构而异且不适用于正在运行的线程，没有规定 Get 的全部 raw 位等于上次 Set。两页最近更新均为2025-06-17，不能把网页日期等同当前 Windows build 实现。

x64 CONTEXT 页（2025-03-28）将 EFlags 声明为 DWORD，未进一步说明该成员的 bit1 存储表示。它和本机 SDK 互证公开类型，没有证明内部转换例程。本次 child 是被调试的另一线程且停在同一事件，不能把“Get 当前调用线程时可能成功但上下文无效”的另一个场景套进此案。

因此官方合同足以否定“TRUE 必须意味着全部 raw 位逐位往返”的先验要求；**本机 bit1 差异的具体方向仍由本次原件证明，而非由微软文档预言**。不能从总则反推当前内部函数或宣称所有保留位都可忽略。

## 事实、推断与未知

| 等级 | 结论 | 边界 |
| --- | --- | --- |
| 事实 | 成功 Set→成功 same-stop Get 出现唯一 bit1 raw 差；TF=1，GPR/其余控制值不变 | 绑定本次 EXE/source、stop29；不是跨 OS 全称 |
| 事实 | 输入只改 TF；原 raw equality 不满足，故及时停止 | 旧 W1 不追认通过 |
| 事实 | bit1 是 ISA 保留位；TF 是 bit8 | 不把二者混为可写请求位 |
| 可解释的推断 | 现象符合 Windows 上下文路径对不能由调用者指定的位作表示转换/修正 | 尚不能定位在 Set、Get 或内部存储/转换的哪一层 |
| 未知 | 当前 Windows 实际转换例程、准确 mask、bit1 为0的内部原因 | 本卡不读 kernel 或系统 binary；公开接口不保证内部实现 |
| 未知 | 恢复执行后的硬件 RFLAGS、真实 single-step、CALL/MOV/RET、YMM | 本 W1 在 Continue 前停止，不能从 API TRUE 推导 |

## 唯一推荐的最小合同补充（待 root 采纳）

只有 root 接受官方原件及本分析，并显式发布新源码/验收 epoch 后才能采用。它会改变旧“raw equality”验收谓词，必须明确记录这一变化；不能把旧 W1 转成正常通过。四设计 JSON 的必需字段、TF-only **提交**限制和来源 pin 不变，不给任何其他保留位/权限位增加例外。

设 B 为同 stop 的 before EFlags，T 为本次目标 TF（0或100h），S 为 submitted，R 为原始回读。提交侧继续要求：

```text
S = (B & ~0x100) | T
submitted SegCs/SegSs/Rsp/Rip = before 对应原值
Set groups = CONTROL；Get groups = CONTROL|INTEGER
```

回读侧继续要求成功 BOOL、同 stop/同 thread、原始字段和 Get ordinal 完整关联，15个 GPR、SegCs/SegSs/Rsp/Rip 逐位不变，TF 精确等于 T。候选补充仅为 bit1 定义两种可接受的原始表示：

```text
R == S
或 S.bit1==1 且 R == (S ^ 0x2)    # 仅已观察到的 1→0 表示差
```

第二分支不授权程序把 submitted.bit1 清零；B→S 仍禁止该改变。没有新证据时也不扩到反向0→1、其他 reserved bits 或所有 OS 修正位。每次仍保存完整 B/S/R，独立导出 raw XOR，并明确是否落入该命名的 bit1 表示分支；不回写“归一后”的 flags，不把 R 改写成 S。可用一个明确的等式分支实现，无需通用状态修复层或增加 Get/Set 数量。

这只把“输入 TF-only”和“回读 bit1 表示”分开。它不验证原子性、物理执行状态或单步退休。真实退休仍必须有后续本轮唯一匹配的 SINGLE_STEP、真实 next RIP/RSP/栈槽/old-new，并单独完成 W1/W2/W3；原清理规则与预算不变。

若 root 不接受该来源限定的表示补充，准确状态就是保持 `setReadbackMismatch`，而不是擅自去掉检查或换机制。当前没有需要以 Astra、kernel 或额外 runtime probe 补齐才能得出本卡事实结论的缺口。

## 交实现包的必要纯回归

以下是新谓词的人工测试需求，不是本轮 native 结果；本分析不写或修改 validator/tests。parent 和 validator 应采用同一明确二分支，保留 `rb.control == readbackControl` 的原件一致性，将与 submitted 的逐字段比较仅在 EFlags 上替换为本补充；不删 required、不改冻结四 JSON、组/身份/预算或 pending 三态。已有 raw 字段足够独立重算差值，不需要增加 API 调用或建立通用归一化框架。

| 输入/回读变异 | 新候选合同结果 |
| --- | --- |
| B202→S302，R302 | 接受 raw-equal 表示；仍需其余 trace 证据 |
| B202→S302，R300 | 接受仅 bit1 的表示关系；**旧 native W1 仍失败** |
| B302→S202，R200 | 仅人工覆盖 TF 恢复及同一 bit1 关系 |
| B202→S300 | 拒绝，程序提交改了非 TF bit1 |
| S302→R200/202 | 拒绝，TF 请求没有回读成立 |
| S302→R301、100、308 或任一其他位变化 | 拒绝，不因 OS 可修正某些位而通融 |
| S.bit1=0、R=S^2 | 拒绝，当前没有反向表示例外 |
| GPR/SegCs/SegSs/Rip/Rsp、stop/thread/ordinal 改变；缺 raw 字段 | 拒绝，原必需检查保持 |
| W3 fault被标退休，或 cleanup Continue 被算授权 | 拒绝，pending三态与真实异常合同保持 |

## 本轮实际验证与交回

已做原始事件/回执/summary 独立关联、精确哈希和 source=build-snapshot 核验；四 SDK 头、固定 phnt/Titan 相关片段核验；SDM 相关页全文及图、微软三官方完整技术正文的只读复核。两个私有静态 proof 成功生成。曾用错离线文件名读取失败及初次合并输出被截断，随后按实际文件名分段读取完成；没有借此扩大资料或运行范围。未运行 validator/native/Debug API/CPU、未改运行代码/配置/Proto/资源，故不需构建部署或重启。当前无客户端验收增量；完整904B、自然GC/第二分配/Default、Avatar/g2和D-02均未晋级。

本卡分析完成，官方原件已合并，准确未知不阻碍交出上述有界规格。下一可执行项交 Sol xhigh/Terra：在 root 新计划下仅修改明确的 parent/validator 谓词、对应源级说明及相应纯负例，固定补充合同、新 source/build/review epoch；root 完成原有构建/PE/依赖门后再决定是否一次新 W1。若新差异涉及 TF、其他位、GPR/控制值或事件关联，原样失败停止，不扩例外或预算。此文不批准运行，也不恢复 W2/W3。Project/Harness 和本地 Git 提交由 root 统一维护，本代理未操作这些文件或 Git；写权现交回 root。
