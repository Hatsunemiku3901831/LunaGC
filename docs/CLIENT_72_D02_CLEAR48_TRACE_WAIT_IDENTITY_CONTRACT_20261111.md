# F-03d3f4d2 / CLEAR48-TRACE-WAIT-IDENTITY-CONTRACT

2026-11-11，Sol max 有界接口审阅。结论与一行最小规格已交主线 Sol xhigh，由主线实施、构建和验收；本代理只写本文及私有 `local/migration72/d02-resume-20261110b/a-clear48-trace-wait-identity/`，未修改 parent、validator、测试或公共任务索引。这里是失败原因与源级合同，**不是完整 W1 通过报告或运行许可**。两次旧 W1 仍失败，W2/W3 未执行。

## 裁定：修 parent 的成功 wait 日志初始化

第二次 W1 的 12 次 `WaitForDebugEvent` 都成功，且各自紧接一条实际 event；所有 wait 的公共头 `eventPID/eventTID/eventType` 却都是 null。对应 event 保留了真实返回的 PID/TID/type。普通、未改动的冻结新版 `check_records` 在第一条成功 wait 以 `wait identity` 拒绝，这是应保留的有效关联门。

parent 的 `wait_event` 在成功 API 返回后已经把 `DEBUG_EVENT` 的真实 PID/TID/type 放入 `log`，但在它们之前将 `log.stopSeq` 置零；随后 `line_begin` 只在 `stopSeq != 0` 时打印这些身份值，因此把已经确定的身份错误地序列化为 null。wait 自身的 `stopEventSeq` 和随后 event 的身份、序号都是正确的。人工 fixture 则在生成 wait 后直接填入真实身份，因而以前的 synthetic 通过没有覆盖这个实际 emitter 缺口。

应保持 validator 原来的成功 wait 身份等式，不从 event 回填实际 raw、不接受成功 wait 身份缺失，也不把 fixture 改成缺身份以迁就 emitter。parent 必须记录 **API 实际返回** 的身份，不能以预期的 `PI` 身份常量替换；未知 PID/TID 仍保留实际值并由原门拒绝。

## 最小实现规格及序号含义

仅在 `wait_event` 的 `if (ok)` 内、现有 `log.eventType` 赋值之后和 `line_begin(..., "wait")` 之前增加：

```c
run->log.stopSeq = run->log.sequence + 2u;
```

设进入该处前公共序号为 n。`line_begin(wait)` 将它增为 n+1，wait 的既有 `stopEventSeq = sequence+1` 输出 n+2；下一次 `line_begin(event)` 将它增为 n+2，`log_event` 随即仍把 `stopSeq` 定为自己的实际序号。因此提前设置的 n+2 是该已收到停点的**预留 event 记录序号**，不是多记一次事件，也不是增加一次 wait。原件的实际配对为 `8→9、11→12、14→15、17→18、20→21、23→24、28→29、40→41、50→51、60→61、68→69、72→73`；这 12 个独立 n 都满足上述算术。

保留入口的 `stopSeq=0`、wait 字段的 `sequence+1`、两条调用 `log_event` 前的既有 `sequence+1` 赋值、`log_event` 内的定值和全部 API 返回存储。timeout/failed wait 不进入成功块，公共身份和 `stopEventSeq` 仍为 null，不沿用上一停点身份。未改 `line_begin` 的全局规则，不增加参数、辅助状态或通用归一层。事件/上下文/读写域、预算、计数、状态机与原始 SDK 结构保持；本项与[WAIT_EXIT 的晚 LOAD_DLL 修复](CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_PARENT_20261111.md)各有单独依据。

私有纯字节重建的 `proposed-parent.spec.c` 为 75552B，SHA-256 `6e0e7d331184dfdc039f081d017419222fa3e312511fcc700ff1d8bb7f148d73`。主线回报已按此一行规格实施，源 SHA 与此相同；新构建、依赖和后续运行由主线独立核验，本代理不等待或代报这些结果。

## writeEvents 失败与停止证据

成功 wait 意味着已经收到一个实际 debug event，原代码已令 `outstanding=1`，并不意味着两行日志都已写入。此时若 wait 的 `line_finish` 失败，现有 `fail_run("writeEvents")` 在没有更早失败时冻结本次首失败；已有失败继续 sticky。随后尝试 `log_event`，最后返回 0；源级流程不新增执行 Continue 或退休授权。增加该赋值后，本次首次 writeEvents 失败的 `stopEventSeq` 从原来丢失的零变为预留的 n+2；紧接的 event 尝试使用同一个 n+2。

这只改善首失败关联，**不保证 n+2 那一行已持久化**：WriteFile 失败或短写可能留下缺行、半行，后续 event 写入也可能失败。`log.failed` 仍置位，`cleanup_run` 及最终收口使 `cleanupComplete=false`，normal/expected-negative 成功退出均不能成立。validator 的完整 sequence、wait/event 和 Continue 关联门不放宽，不能凭 summary 中的预留序号给损坏日志补造一条 event。

若收到的是异常，首失败原始异常信息仍只来自已成功返回的 `run.event`；`contextRip` 仍只在 `lastContextStop` 等于当前停点时记录。新序号不会把前一停点的 context 变成本停点的 context。正常写入路径的事件序号、停点及后续证据关联不变。失败 wait 仍没有新停点；仅支持 timeout 的既有 validator 规则也不在本卡改变。

## 冻结输入与本轮定向接口检查

原件根是 `local/migration72/d02-resume-20261110b/`，现场文件、PID/TID、绝对路径与完整诊断仅留私有目录。以下固定实际失败，不重写旧 result：

| 输入 | SHA-256 |
| --- | --- |
| `a-clear48-trace-wait-exit/failure-freeze.private.json` | `e869cfffe4513e785443e0a3e5829c0fc225bb86169f7dcc881a4a952de304af` |
| 第二 W1 `receipt.json` | `1ad5c8e3486e4d835f397bbbb0269b7c48cd3c8ad28967fe80c6bf5668203d1b` |
| 同轮 `native/events.jsonl`，35619B | `e382f2b7c9589a6b71f17e66eea733da16d0670efd8d584eb0198aa1357d3c5f` |
| 同轮 `native/parent-result.json` | `f06e42460e99af438eb86bc2ff3e754efef8b8c38828cf36cc2e96c117460e36` |
| 同轮 `native/child-result.json` | `dbce07c2bbab7b1e8a38107dc8b8579b6294357c1efe2f18e2bea8493f4363fb` |
| build-main4 `parent.c`，75342B | `781e5ee0c22f2d7d1583f6b59d692db827af03a84e343081cea92a2640771c7e` |
| 仅 WAIT_EXIT 晚 DLL 修复的 parent 基线，75500B | `8b04815c1b7484d7439a7d8424d378d9168fd113124c9b5887f56fee29608586` |
| 冻结旧 validator，35911B | `be5f39a3450aea3770b2ce06fbd7e5b68091b9bc5abd9c08eaefae2891740e17` |
| 仅晚 DLL 尾序列修复的新版 validator，36119B | `12e76d506b1e8856b5482e65c75589a93e85a9030e2d794d7744de6b418e90e1` |

本卡核了四设计 JSON 的原 SHA、required 字段、失败汇总字段和本轮实际 phase，保持原文件不变；没有重开 PE、目标 code/AOT、SDK 或系统二进制。parent 两基线和 validator 两 epoch 均在本卡私有目录另存源快照；晚 DLL parent 基线由原 build-main4 加唯一已批准块精确重建并核对 8b048… 哈希。已有 trace/GitHub 来源及[有界晚事件计划](CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_VALIDATOR_20261111.md)直接复用，本单点续项没有新检索或克隆。

为检查被首个失败挡住的其余本轮接口，`audit_wait_identity.py` 在**原始 rows 不变**的情况下，对两个冻结 Python 模块各做一次明确标为非授权的诊断：仅将 `need` 替为记录器，枚举已知失败消息后继续观察；任何未枚举的 false 立即停止。没有调用完整 `validate/main`，没有重新打开其 PE/SDK 依赖，也没有输出正常 validator 通过格式。报告 `accepted=false`、`fullW1Accepted=false`、`originalRunRemainsFailed=true`，不能供 runner 或运行许可消费。

| 冻结 checker 对同一 79 条真实 rows 的观察 | false 谓词 |
| --- | --- |
| 晚 DLL 修复前 | `wait identity` 12 次；`debug event sequence`、`actual exit`、`normal summary` 各 1 次 |
| 晚 DLL 修复后 | `wait identity` 12 次；`actual exit`、`normal summary` 各 1 次 |

两轮分别求值 688 次 `need`；其中 673/674 次为 true。这是含格式辅助函数的谓词观察数，**不是测试数量、完整 validator 通过或新增 native 验收**。普通、未替换 `need` 的冻结新 `check_records` 也单独确认以 `wait identity` 拒绝原件。

本 run 其余有界接口没有出现新 false：

| 本轮已存在原件的接口 | 定向核对结果与限制 |
| --- | --- |
| epoch、owned 身份、公共序号/时间、wait/event/Continue、summary 计数 | 1 epoch、1 owned、12 event、12 wait、12 Continue；除列出的 wait 身份外，原字段/计数关联未见新冲突。receipt 的 166 个 before/after 条目相同；未重验文件系统上的 PE/依赖。 |
| Get/Set 与 EFlags v2 | 9 Get、4 Set；组、same-stop/ordinal、15 GPR、控制字段、原始回读和 audit 对应。前三次 B206→S306→R304，末次 B206→S206→R204，均保留真实 raw，属于既定 bit1 单向分支；不能套用首轮 202/302/300 数值。 |
| descriptor、memory、三事务、calibration、child 自报 | 9 读/330B、3 transaction、5 calibration；CALL 栈槽、MOV/RET 的 64 位 RAX、RET/RSP 与原三条退休证据互相对应，required 字段和本轮 phase 已齐。child 自报已回 C/exit0 只限自身报告。 |
| OS 退出与清理 | 实际 late DLL event69 后走失败，owned Terminate(90)、EXIT event73、cleanup Continue、signal/GetExit/PI close 关联明确。`actual exit`/`normal summary` 为 false 是实际终止90、parent2、两条 cleanup Continue 和失败汇总的正确结果，应永久保留。 |

本检查只覆盖这次真实 run 已走过的接口；不把 DLL 无效句柄或未走过的错误路径扩成新审计项目，也不宣称所有 schema 边界已完备。新版 verifier 的 DLL 文件字段门与[独立晚事件文档](CLIENT_72_D02_CLEAR48_TRACE_WAIT_EXIT_VALIDATOR_20261111.md)所列范围相同。

私有 `interface-diagnostic.private.json` 为派生诊断，SHA-256 `9989166632c447f6c49616a51b3019aafa55fc3ae307b72e176c9b7313c538d2`；另有 `normal-gate-rejection.private.json`。脚本结束重核全部固定输入 SHA，原 rows 的规范化指纹前后相同。首次私有脚本在尝试冻结新测试时，发现主线已开始其授权身份负例修改，test pin 从 26750… 变为 f23c07…，因此按 pin 停止；未运行语义诊断。该次脚本与失败回执另存 `audit_wait_identity.attempt1.py` / `attempt1.private.json`，随后去掉非必要的活动 test 依赖，仅用冻结必要源完成本次检查。没有借错误 pin 吸收未审核的活动源码。

## 实现验收与最小负例规格

parent 只需一行赋值的独立差异与上述 n/n+1/n+2 关系审核；timeout/failed 成功块不执行、首失败 sticky、写失败仍不可通过，均沿既有源码核对。对应纯测试应证明实际所需关联，不以 synthetic 成功替代 emitter 或 native：

| 测试输入/变异 | 要求 |
| --- | --- |
| 成功 wait 的返回 PID/TID/type 与下一 event 对应，含启动、单步、晚 DLL、清理 EXIT | 公共头保留真实返回值；wait seq=n+1、stop/event=n+2；后续 event/操作序号不位移。 |
| 成功 wait 的 PID、TID、type 各自缺失、置 null、设为不匹配值 | 九个人工子例全部拒绝。缺字段可由 KeyError 拒绝；错 PID/TID 可被更早的 `event PID/TID` 门拒绝，不强行要求所有错误都使用 `wait identity` 文本。 |
| wait 关联错 event、stop seq 非下一条、event 丢失或重复等待同一 event | 保留原 wait/event、sequence、bijection 门拒绝。不能仅补一个身份字段取得通过。 |
| timeout/失败 wait 接在有身份的成功事件之后 | emitter 仍输出三项 null 和 null stop；真实成功事件的身份不能泄漏给没有返回事件的 wait。保持原失败返回与 timeout 门，未在本卡新增其他失败类型许可。 |
| 成功 wait 的日志写失败/短写，或后继 event 写失败 | 源级确认 outstanding 仍指真实收到事件；无更早失败时首失败为 writeEvents，已有失败保持 sticky；n+2 仅为预留停点，log.failed 阻止完整成功。不补造行，不把诊断收集结果当通过；无需为本规格调用 native API 注错。 |
| 冻结的两份历史失败和旧结果 | 原 SHA、失败分类及非通过状态保持；不能因新源或新 checker 追认 W1，也不据局部退休启动 W2/W3。 |

主线已回报独立增加九个成功 wait 身份缺失/错误子例；本卡不修改其测试文件，也不把该回报算作本代理实际运行。新源/构建/依赖审核后，实际新 W1 的 normal validator 与 OS 正常退出仍必须由主线单独取得，之后才能按原顺序进入 W2/W3。

## 交回

本卡完成的是准确失败归因、保持身份门的一行最小规格、writeEvents 停点语义和本 run 其余接口的纯 Python 定向检查。未执行 native、Debug API、CPU/emulator、live/REA、进程、网络、Git、构建或数据库操作；没有安全/Daybreak 新拒绝或重试。仅文档和私有离线研究产物，无生产部署或重启。完整 W1、W2/W3、904B、第二分配/自然 GC、Default/Avatar/g2 与 D-02 客户端验收均不晋级。

Project/Harness、共享交接及本地提交由主线统一维护，本代理未操作 Git、未作提交。下一项为主线按新 epoch 完成其原有构建/依赖审核和一次新 W1 的完整独立验收；本分析写权现交回，释放 max，不等待新 build/run。
