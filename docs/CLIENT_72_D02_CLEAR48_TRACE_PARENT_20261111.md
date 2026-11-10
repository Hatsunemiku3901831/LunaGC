# F-03d3f4d2/CLEAR48-TRACE-WITNESS-PARENT

## 中文有界计划

本卡只写 parent C、本文及私有目录，不改共享头、child、build 或其他工具；不构建、不执行 CPU/Debug API、不访问真实进程。核对四个冻结 JSON、共享头及 child；固定事件原件结构，完成有限状态、交易、预算与失败收尾，再静态审查交 root。未知事件/身份/地址及首次失败停止，不以理论预期补充运行记录。安全/Daybreak 立即停止。

## Parent 原件接口 v1（供独立 validator 对接）

以下是源码输出约定，不是实测原件。所有文件为新建目录内 CreateNew；`events.jsonl` 每行为一个对象。每行共有 `kind`、单调递增 `seq`、`phase`、`eventPID`、`eventTID`、`eventType`、`monotonicElapsedUs`；当前没有 debug stop 的三项 event 身份为 null。phase 可为 `BOOT`、`ARM_STOP`、`PENDING`、`POST_STOP`、`TRACE_DONE`、`WAIT_EXIT`、`FAILED_STOP`、`TERMINATING`、`EXITED`。时间从单调毫秒时钟换算为微秒，不声称微秒精度。64 位地址/GPR 为 16 位小写十六进制字符串；flags/code 为 8 位小写十六进制；原始字节为定宽小写十六进制；未观察为 null。各 API ordinal 分种类从 1 开始。

- `epoch`：`childExeSHA256`、`bootstrapSHA256`、`sharedHeaderSHA256`、`imageBytes`（PE SizeOfImage，16 位小写十六进制字符串）、`descriptorRva`、`wrapperBeginRva`、`armRva`、`callRva`、`afterCallRva`、`wrapperEndRva`、`returnLeafRva`、`ymmLeafRva`、`faultLeafRva`。仅在验证成功后发出。
- `ownedChild`：`pid`、`tid`、`processHandleOrigin` 固定 `CreateProcess.PI.hProcess`、`threadHandleOrigin` 固定 `CreateProcess.PI.hThread`；一次自有 PI 进程句柄的 `GetProcessTimesBOOL`、`lastErrorIfFalse`、`creationFileTime`（16 位小写十六进制，失败时 null）。身份读取失败粘住首错并终止，不进入 trace。
- `wait`：`apiOrdinal`、`BOOL`、`lastErrorIfFalse`、`timeoutMs`、`stopEventSeq`；timeout 按实际结果记录。成功 Wait 的 `stopEventSeq` 指向下一条 `event` 的 `seq`。
- `event`：`rawDebugEventCode`、`firstChance`、`exceptionCode`、`exceptionAddress`、`exceptionFlags`、`exceptionParameters`、`exceptionParameterCount`；非异常值为 null。`continueStatus` 和 `ContinueDebugEventBOOL` 在 event 行为 null，`continueRecordStatus` 为 `separate`；通过相同 `stopEventSeq` 关联 `continue` 行，不能把占位 null 解释为继续成功。CREATE_PROCESS/LOAD_DLL 另记 moduleBase、moduleImageBytes（16 位小写十六进制字符串或 null）、fileHandleValid、filePath、filePathError、filePathRequiredLength、fileIdentityBOOL、fileIdentityError、fileIdentity（volumeSerial/indexHigh/indexLow）、fileCloseBOOL、fileCloseError、ntdllNameMatch、fileSHA256；CREATE_PROCESS 另记借用 process/thread handle origin、startAddress、threadLocalBase。EXIT_PROCESS 另记 `exitCode`。
- `calibration`：`name` 为 `systemBreakpoint`、`ownedInt3` 或 `singleStep`，附 `stopEventSeq`、`exceptionAddress`、`contextRip`、`moduleBase`、`exceptionRva`。地址与 RIP 均实际观察，未预设两者关系。
- `get`：`apiOrdinal`、`requestedGroups` 固定 `["CONTROL","INTEGER"]`、`BOOL`、`lastErrorIfFalse`、`threadHandleOrigin`、`stopEventSeq`、`control`、`integer`。成功的 control 对象含 `SegCs`、`SegSs`、`EFlags`、`Rsp`、`Rip`；integer 对象含 `Rax`、`Rbx`、`Rcx`、`Rdx`、`Rsi`、`Rdi`、`Rbp`、`R8` 至 `R15`。失败时两个对象均 null。
- `set`：`apiOrdinal`、`stopEventSeq`、`requestedGroups` 固定 `["CONTROL"]`、`beforeControl`、`submittedControl`、`BOOL`、`lastErrorIfFalse`、`readbackGetOrdinal`、`readbackControl`、`integerUnchanged`。代码先执行并输出 readback `get` 行，再输出引用该 ordinal 的 `set` 行；回读失败时 ordinal 若已调用则保留、control 和 unchanged 为 null。只改 TF。
- `continue`：`apiOrdinal`、`stopEventSeq`、`continueStatus`、`BOOL`、`lastErrorIfFalse`、`cleanupOnly`。清理继续不得解释为授权或退休。
- `memory`：`apiOrdinal`、`stopEventSeq`、`purpose`、`address`、`width`、`allowedRegion`（`begin`、`endExclusive`）、`ReadProcessMemoryBOOL`、`lastErrorIfFalse`、`actualBytesRead`、`rawHex`。purpose 为合同中的 `sourceCode`、`descriptor`、`ownedBufferInitial`、`operandRead`、`writeOld`、`writeNew`、`CALLslotOld`、`CALLslotNew`、`RETslot`、`ownedBufferFinal`；短读保留实际原始字节且立即失败。
- `transaction`：`txId`、`instructionOrigin`（`wrapper` 或 `leaf`）、`preEventSeq`、`preRip`、`instructionBytes`、`preGPR`、`preEFlags`、`ops`、`expectedNextPCs`、`tfSetOrdinal`、`continueOrdinal`、`postEventSeq`、`postRip`、`postGPR`、`postEFlags`、`retired`、`status`（已确认退休为 `retired`；已观察预期 UD2 为 `unretired`；未尝试执行为 `not_executed`；已尝试 Continue 但退休未确认为 `retirement_unconfirmed`）。ops 为零或一个对象，含 `direction`（`read`/`write`）、`EA`、`width`、`allowedSpan`（`begin`/`endExclusive`）、`preReadOrdinal`、`preHex`、`postReadOrdinal`、`postHex`。读 API 已调用但短读时 ordinal 保留、对应完整 hex 为 null；部分 bytes 仍在 memory 行。W3 UD2 的 `expectedNextPCs` 为空数组、post 为实际故障停点、`retired=false`，其他指令该数组为一个实际允许后继。`retired` 对应 true、false、false、null；失败时的 `pendingRetired` 同样三态，`pendingTxId` 可区分未知与无 pending。事务行只尝试输出一次，写失败可能只留部分原件，不重发。
- `cleanup`：`api`、`handleOrigin`、`BOOL`、`lastErrorIfFalse`；`WaitForSingleObject` 特例为 `api`、`handleOrigin`、`timeoutMs`、`result`、`lastErrorIfFailed`。涵盖 CreateProcess、KillOnExit、Terminate、输出句柄及 PI 句柄关闭、真实 exit 和 child result 存在性。

`parent-result.json` 的 schema 为 `c48-parent-result-v1`，字段为 `caseId`、`runId`、`epochValidated`、`normalTracePassed`、`firstFailureKind`、`pid`、`tid`、`stopEventSeq`、`rawCode`、`exceptionAddress`、`contextRip`、`pendingTxId`、`pendingRetired`、`lastCompletedTx`、`TerminateProcessBOOL`、`terminationRequestedExit`、`cleanupContinues`、`EXIT_PROCESSobserved`、`EXIT_PROCESScontinued`、`ownedProcessSignaled`、`GetExitCodeProcessBOOL`、`childExitCode`、`killOnDebuggerExitAccepted`、`cleanupComplete`、`sourceProof`、`budgets`、`PIThreadCloseBOOL`、`PIProcessCloseBOOL`。`sourceProof` 在 epoch 通过时与 `epoch` 行的三个 SHA、imageBytes 和九个 RVA 一致，未通过为 null。`budgets` 键为 `events`、`waits`、`gets`、`sets`、`reads`、`readRequestedBytes`、`continues`、`retired`。`lastCompletedTx=0` 表示尚无完成事务。W3 期望 `firstFailureKind="expectedUD2"`、`pendingRetired=false`、`lastCompletedTx=1`、`normalTracePassed=false`、终止退出 90 且 parent 退出 30；这是负证人，不是正常通过。

## 状态与边界

源码包含预检、事件/内存/上下文记录、有限交易及收尾；已解除临时编译阻断以交 root 做统一编译审查，绝不构成执行许可。仅做静态文本与有界分支核对，尚需 root 独立源码审查、统一构建、PE/SDK 检查及独立 validator 对接；本代理未调用编译器或任何 CPU/Debug API。本项不链接 904B，不覆盖客户端、自然线程、GC 所有权或 D-02 客户端验收。

## 静态核对与交回

`tools/client72_d02_native_trace/parent.c` 的 SHA-256 为 `cc2cdcc9ee7d082125f2084b097c39c21389fab2c3b69bbe8fba288dd144abb4`，73562 字节。四个冻结 JSON 与共享头的指定 SHA 均匹配；私有 `a-clear48-trace-parent/static-review.private.json` 保存源码 SHA、文本检查及范围。静态检查仅覆盖词法括号平衡、两个请求的 CONTEXT 组、指定禁用 API 未出现、各受限 API 单一调用点、W2 偏移/宽度字面量和 UD2/终止字面量；不等于 C 语法、链接、API 返回或运行正确性。

本卡未构建、不运行 CPU/Debug API/任何 child、不做客户端或服务验收、不执行 Git。需要 root 逐分支审源、统一编译、独立验证可执行文件及依赖/PE/SDK、完成 validator 和变异拒绝检查，并取得明确 CPU 执行批准后，才可开始新的自有 W1/W2/W3 验证。W3 仍只接受负证人；真实 TF、RIP 关系、YMM 保存、异常收尾和全部目标客户端行为仍未观察。下一项是 root 的源码审查与构建前准入；若发现问题须回到此源码修复、重新固定 SHA，不得以这份静态报告代替运行证据。


## Pending 证据纠正（新源码 epoch，未构建）

纠正前 root 源码 SHA-256 `bc9e80e4eff25e0c0ee69691bddbc802b997a91a743fcef10fc9f6d8b2235b0e`；独立源审 SHA-256 `51af064ce079f559bcec37c745fb293f6cd3f9faf0f47dc40143b2ea0dc0cb5c`。旧 build-main1/main2、PE、bootstrap 和原始审查保持历史证据，不是新源码的构建证明。

新增明确退休证据状态：未尝试 Continue 为 false/not_executed；Continue 一经尝试即未知 null/retirement_unconfirmed，不依 BOOL 猜执行；实际 post 全部通过后 true/retired；仅 W3 真实首机会 UD2 地址、RIP 和 RSP 均匹配后 false/unretired。状态未知或失败均不作为正常通过。成功 Get 的上下文先保存，连 Get 记录写失败也保留同一停点实际 post；事务行无论成功失败只尝试一次，无法落盘不假称已落盘。栈预检明确要求 CALL slot 和 [C,C+32) shadow space 均在已声明栈范围。

本纠正只改变证据表达及既定 ABI 上界，不增加指令执行或失败后继续。child-result 内容仍由独立 validator/root 核验。本轮没有编译、CPU/Debug API、目标进程、网络、数据库或 Git；须 root 以新 BuildId 重新编译和复审 PE/依赖，执行仍需明确 CPU 授权。

新 parent.c SHA-256：`e5b60ee368d724e947e2aad0d12fa2cbe1fe6201506d3775f441cc02a6f67cea`。定向纯静态核查保存在私有 `a-clear48-trace-parent/correction/static-checks.private.json`，覆盖成功 Get 保存时点、Continue 尝试后未知、UD2 与正常退休的确认条件、单次事务输出及 summary 三态；另外用 274631 个纯整数边界组合核对 shadow-space 范围谓词，其中含 64 位上界示例。它们不是 C 编译、Windows 调用或 witness 实测。正常 W1/W2 和 W3 的既有输出语义未改变；其真实 CPU/调试行为仍未验。旧 main2 构建成功只属于旧 SHA，不能作为本源码构建证据。
