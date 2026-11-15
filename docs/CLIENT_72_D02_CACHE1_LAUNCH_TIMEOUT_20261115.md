# cache1 启动回执超时归因（2026-11-15）

任务：`R3-PROUD-CACHE1-LAUNCH-TIMEOUT`；父项：`R3-PROUD-EXISTING-CACHE-CAPTURE`。接手源码 HEAD 为 `e09271e0bb954b21ff32e6d1963e10de57fc26eb`。本项只研究已经发生的自有启动协调和回执问题，未启动客户端、未请求 UAC、未调用目标 getter 或采样器、未启动服务/数据库。

## 结论与准确缺口

原 cache1 超时发生在父 PowerShell 的同步 `Start-Process -Verb RunAs` 交接阶段，没有可观测的返回结果。父回执在调用前以 `CreateNew / FileShare.None` 打开，正文只在 `finally` 写入；等待期间它必然是锁住的 0B 文件。超时快照仅有两个原控制进程，没有 helper 或游戏候选；helper JSON 和 Launch JSON 缺席。取消后原父回执仍为 0B，并已在锁释放后原样封存。

这是可确定的失败阶段。现有证据不能确定操作系统为什么没有完成该交接，也不能确定 UAC 提示是否可见、用户点击的时间或其稍后报告窗口与旧请求的关系。因此不能把它归为用户拒绝、SDK 失败、客户端异常退出、cache cold 或 miss。当前资料也不能证明 helper 曾进入其第一处证据门之前的代码；不能把缺回执升级为绝对未创建任何进程。

协调实现存在两项已证实的不足：同步请求期间没有已经关闭、可读的阶段回执；90 秒总期限没有由该脚本独立执行。原父脚本没有时钟或期限参数。旧协调者在超时后才停止，不应后移总计时起点或增加等待来解释此故障。

## 固定输入与来源

原始目录为 `local/migration72/d02-resume-20261111c/integration/`。本次只读审计输出为 `local/migration72/d02-resume-20261115/launch-timeout/evidence-audit1.private.json`。

| 固定证据 | SHA-256 | 本次核对 |
| --- | --- | --- |
| `live-20261112-cache1-launch/completion1.private.json` | `f33023d33d42b85c42a062aef4abe5f80035f882e494f23b92b4cb04636315d8` | 匹配；5304B |
| `acceptance29-preflight/completion2.private.json` | `047617b4c58357e1f4faf9e0c40166d22c5812875a0facf9816a9602e2a23c11` | 匹配；11444B |
| `acceptance29-preflight/operation-plan2.private.json` | `96b61bb15e2bb715057552a3f238296c918f68222788620ecae2c7075d51032c` | 匹配；5280B |
| 原 UAC 父脚本 | `effccc62aa11657a0d31d0f5b8c67bdad6475fa62773f268ce740a1e26e3976d` | 匹配；1483B |
| 原 elevated helper | `9a91e8683dc4f56d7d0b7864e4cffba5bb2900086ec2fcaab0e9d82740921638` | 匹配；2414B |
| 原 Python Launch | `87d3e1155230b2b49201fd613653d87057dae2b238ef81f7a7c9fbbdf730117b` | 匹配；6046B |
| 原父回执及取消后副本 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | 均为 0B |
| 协调者取消标记 | `d5d61bda4e24ce8e83e48156e1f26559697da0372a1a87a26dca3f2ca04b750c` | 匹配；245B；不是 helper 运行日志 |

两份 completion 所列的 65 个归档 artifact 逐项 hash/bytes 匹配；Launch 和 sample 两份 source seal 共 21 个条目匹配。当前五 stock 文件匹配固定 profile，配置文件 hash 与原基线匹配，`.env.local` 不存在。本次只把这些文件检查作为输入完整性证据，不把历史环境快照或本次 hash 检查当作新现场 ready。149 个保护/原数据文件的本次起点指纹保存在私有审计结果；现有 AGENTS 用户差异、patch 未提交/未跟踪内容及 resources 差异均保留。

`completion2` 中的历史准备字段不能代表后来执行结果。`operation-plan2` 的待执行状态由本轮原始 live 证据补充；原初使用绝对输入 path 的旧 baseline 仍为被替代且未执行的资料，不能用它解释这次 dated Launch 的失败。

## 原调用链与时间线

原父脚本：校验 `UserReady` 和回执不存在 → `CreateNew` 打开父回执 → 设置内存字段 `uacRequested` → 同步调用 `Start-Process -Verb RunAs -PassThru` → 成功返回后记录进程对象 ID 并 Dispose → `finally` 写父 JSON、关文件。

原 helper：先解析目录和检查 receipt/log 不存在 → 打开 helper 回执 → 检查 ready/admin/source seal → 打开 helper log → 执行 Python Launch → `finally` 写 helper JSON。原 Python Launch 打开 Launch JSON 后才执行前检及普通启动。原 helper.log 在超时之后由协调者用作 no-repeat 取消标记，没有运行日志内容。

下表使用原件的 UTC，不以文件 basename 的日期推断执行时间。

| UTC | 原始证据与含义 |
| --- | --- |
| 2026-11-11 14:15:13.306908 | 原授权记录：只派一次正常 stock Launch；sample 要等标题/SDK 反馈及单独包 |
| 14:15:15.623308 | 原 prelaunch 通过；当时目标、项目 Java/Mongo、固定端口为 0 |
| 14:15:23.687519 / .937387 | 原外层 pwsh / UAC 父 PowerShell 创建 |
| 14:15:24.130685 | 父回执创建，0B |
| 原 165.6 秒观察 | 父回执 0B，helper/Launch 缺席；后续 clock shift 解释已在原件中纠正 |
| 14:20:52.298281 | timeout-process 快照：原两个控制进程存在，game 候选为空 |
| 14:21:27.402289 | stopped-timeout 落盘；原件父文件龄为 363.271 秒，明显超过总 90 秒 |
| 14:29:26.331720 | 协调者写入 245B 取消标记，阻止旧包晚到后继续 |
| 14:29:52.344227 | 自有等待会话正常 Ctrl+C 后，两个原控制进程不在；父文件仍为 0B |
| 14:30:51.106060 | closeout：锁已释放，复制 0B 原父回执；helper/Launch JSON 仍缺席，sample=0/RPM=0 |

本次用 parent 创建时间与 stopped JSON 时间直接相减为 363.271604 秒；它与原文件 metadata 采样记录的 363.271104574 秒相差不足 1ms，不改变超时判断。旧 closeout 不否认用户后来看到过窗口，也不能补出其点击/窗口与本次请求的归属。

## 自有 stub 验证

验证只复制原父脚本的相同字节，在独立 PowerShell 进程中用专名状态的 `Start-Process` 函数 stub 接管调用。stub 不调用系统 Start-Process，不创建子进程，不请求 RunAs，不执行 helper/Python/client。原件和旧 source seal 均未修改。

首个夹具因跨脚本 `$script:` 状态作用域错误而失败，原输出保留在 `stub-verification1/`，标记为未通过。v2 在新文件及新目录中修正夹具状态，未覆盖原失败。实际命令：

```powershell
python -X utf8 -B local/migration72/d02-resume-20261115/launch-timeout/audit_evidence.private.py
powershell.exe -NoProfile -ExecutionPolicy Bypass -File local/migration72/d02-resume-20261115/launch-timeout/verify_parent_receipt2.private.ps1
```

解释器实际绝对路径只保存在私有证据；复现应使用本机经核对的 Python 3。上述首条应以该本机解释器替换，不把机器路径作为交付依赖。

| v2 case | 调用尚未返回时 | 返回或正常异常之后 | 验证结论 |
| --- | --- | --- | --- |
| dispatch returned | 父文件 0B 且锁住；无 helper/Launch JSON | 父 JSON `helperStarted=true`、Dispose=true | 字段只证明 dispatch 进程对象返回；stub 没执行 helper，不能据此声称 helper 入场 |
| simulated 1223 denial | 同上 | `helperStarted=false`，error 落盘 | 正常可捕获失败会经过 finally；旧 0B 不能被解释成已观测的 UAC 拒绝 |
| virtual 91s pending | 同上；原起点 90 秒合同保持 | stub 主动抛普通 TimeoutException 后 error 落盘 | 重现等待中的回执盲区；验证没有把总起点后移或把上限加长 |

三例通过，真实子进程/UAC/Launch/RPM 均为 0。虚拟时钟只用于 stub，不是真实等候 91 秒。它没有模拟历史外部 Ctrl+C；普通异常能执行 finally，而历史中断留下 0B，二者不混称同一退出行为。v2 完整结果为 `stub-verification2/verification.private.json`。

## 已实现的最小离线修复

主线审核完整接线规格后，新增两个工具及新私有薄包装；旧 acceptance29、原 Launch 和已验采样器均未改字节：

| 文件 | 用途 / SHA-256 |
| --- | --- |
| `tools/migration72/launch_stage_receipt.ps1` | 立即关闭并发布阶段回执、读取共享期限、区分阶段与结果；`fdb0d2b284b74074c79aa601b3c67e179c653254c9ba6f774f9f1d30c43f7c1f` |
| `tools/migration72/test_launch_stage_receipt.ps1` | 16 条自有合同检查；`193ddcaaf064ec0785c69b302fa063e4fc803c158987417aa99f8f53f38de469` |
| 私有 `launch-envelope-v1/coordinate.private.ps1` | 原入口第一条语句取 QPC，普通创建自有 dispatcher，独立观察同一总期限；不在 RunAs 内等候 |
| 私有 `dispatch.private.ps1` | 唯一同步 RunAs 位置；dispatch-returned/failed 与 helper-entry 分列 |
| 私有 `helper.private.ps1` | 先记入场并检查同 request/cancel；正常权限及源检查后进入 Launch adapter |
| 私有 `launch.private.py` | 调用封存的原 `run_live`，只在委托 backend.launch 前加期限/取消门；超时结果不得成为成功 |

私有路径均相对 `local/migration72/d02-resume-20261115/launch-timeout/`。阶段先以 `CreateNew` 写入并关闭，再发布最终文件；读者不会把尚未关闭的 0B 文件当完整回执。原入口记录的 start/deadline/frequency 以十进制字符串跨进程传递，默认及新正式包固定 `deadline-start = 90 × frequency`，helper 和 adapter 不重新起计。同机 QPC 可用于不依赖 UTC 校时的间隔测量，.NET Stopwatch 使用该计时基准；依据见 [Microsoft QPC 说明](https://learn.microsoft.com/en-us/windows/win32/sysinfo/acquiring-high-resolution-time-stamps)和 [Stopwatch 文档](https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.stopwatch?view=netframework-4.8.1)。

外层到期发布 cancel 和不可覆写的 coordinator-result，返回 124；无需等待阻塞的 RunAs。晚 helper 先检查该 cancel/原期限并自己退出；前检越界则普通 Launch 调用不获准。已在期限内获准的原生 OS 调用不能由此包装强制打断；如果其结果迟到，结果仍停止，实际进程交用户正常关闭。不能把这项合作式边界称为硬中断保证。

新 source seal 为 27 pin / 5078B，SHA-256 `b383e994d03358a32966e30782da95f43465c61b848717aa24cccba3547de320`。旧原窗、hot sampler 模块仅按原封存的 Launch 来源被导入；本包没有运行其采样/读取函数。已只读核对被委托的正常 backend.launch 为原普通 CreateProcessW 路径，不连接 patch/B loader 或 A 分配返回产物。所有自有后台 Start-Process 均指定 `-WindowStyle Hidden`；没有修改全局 PSModulePath。

## 六场景自有进程闭环

纯合同 16 条全部通过：原 90 秒起点、189/190/191 虚拟边界、跨进程描述符、request/dispatch/helper/launch 区分、迟到结果、取消及禁止覆盖/重置。另由新外层协调者实际创建本轮自有 PowerShell/Python stub，原 `run_live` 只接 fake backend 和 fake preflight。真实 UAC、客户端 Launch、目标 RPM 均为 0。

| 场景 | 外层结果 / 实测耗时 | 下游断言 |
| --- | --- | --- |
| success | launched / 1.370s | dispatch、helper、launch 独立回执均出现；仅 stub launch，原 finally 关闭 fake handle |
| denied | stopped / 0.807s | 模拟 1223，未进入 helper 或 launch |
| blocked | deadline / 1.195s | 替身 dispatcher 仍阻塞时外层已返回；其后正常退出，无 helper |
| late-helper | deadline / 1.187s | 晚 helper 记录 cancelled，无 launch-entry |
| preflight-late | deadline / 2.201s | 已入 adapter，但前检越界后未调用 stub launch |
| cancel | cancelled / 1.364s | 取消阻止 stub launch |

仅在 self-stub 模式，blocked/late-helper 把原起点注入为已过 89 秒，preflight-late 注入为已过 88 秒；描述符的总长始终 90 秒，正式入口禁止这种测试起点。所有阶段保持相同 start/deadline；迟到 dispatcher/helper 正常退出后，coordinator-result hash 保持。没有通过增加等待或重新计时使失败变成功。

最终闭环回执 `envelope-verification4/completion.private.json` 为 4531B，SHA-256 `caf0b33643c562139d14c9f7f8ba486a78ac209a5b4a6e96a40e29388a1c67c5`。v1 因 Windows PowerShell 继承模块路径导致 Get-FileHash 缺失，在 request 前停止；按已有 `local_windows.ps1` 的匹配 Utility 导入修复，首失败日志与旧 seal 保留。v2/v3 是验证 driver 对“进程已退出”的返回码及晚 helper 等候对象选择错误，原输出均保留，未当作闭环通过；最终只接受 v4。此前父脚本作用域夹具的首失败也不追认。

最终只读 guard 为 `final-offline-guard1.private.json`，1157B，SHA-256 `78ff0d1afd037588386d0c17a39919e8a861e76b240c16983bf391973ecb7227`：149 保护/原数据文件、原 Launch 20 pins、五 stock、JAR 及 27 新源 pins 均保持；5 个 PS 文件 parse、2 个 Python 文件仅编译检查通过。`2026-11-15T01:34:23.6859050Z` 所有本轮历次自有 child PID 均不在，游戏候选、固定 TCP 8088/27017/27027 与 UDP 22101 均为 0。这只是该时点只读快照，现场前仍须新预检。

精确冻结清单为 `launch-review-package1.private.json`，SHA-256 `2c41d0abae054e5576ffe3f5b09461995766076e1bb212bf14fe6ed5396ddcf0`。现场单次方案为 `launch-operation-plan1.private.json`，5846B，SHA-256 `67d30e52fa03ff324688cc9c6081bf89eadfe94460560aff5f8e1b5d2326afbb`；涵盖新 ready 前置、唯一命令、同原 90 秒标题/SDK 反馈、正常 UAC 表现及退出恢复。包已交主线只读审查，运行控制明确交主线，本执行者停止自有进程测试及运行/端口操作。尚未请用户操作。

另为唯一现场操作者准备只读入口 `fresh_preflight.private.py`，7024B，SHA-256 `7025bc15a1495cf33d8f7293cb0a10eef90dbd574868e3405e4488fd2a7f1d67`；仅静态编译，未由本执行者运行。它固定新 seal/旧 baseline 并复用原 accepted preflight()，检查 27 pins、五 stock、环境/配置、JAR、149 文件及准确归属的控制/项目进程和固定端口；未知同名进程只记录。输出要求全新且 `ready=false`，漂移保留预期/实际文件元信息，不修复或放宽。该入口独立交主线审核，未改变已冻结的 27 pin 包。

## 分类、边界与下一项

| 项目 | 本次结论 |
| --- | --- |
| 输入 | 原归档与固定文件完整；没有取得新的 cache 输入 |
| cold / miss | 均未观测，不能分类为此 |
| timeout | 已确定：RunAs/elevation 交接未取得可观测返回，超过原 90 秒合同 |
| sample | 未派；target RPM=0；未执行 native getter |
| 剩余不确定 | 操作系统阻塞原因、原 UAC UI/点击时刻、后续窗口归属；本次 stub 不解除这些未知 |
| 当前阻塞 | 离线合同及薄封装已验；真实 RunAs/客户端支持尚未实测，主线现场审查及新实时 ready 尚缺 |
| 下一最小动作 | 主线只读审查冻结包，完成新预检及退出恢复核对后，取得新实时 ready；只执行计划中的一次普通 Launch，按原总 90 秒收反馈 |

`A-SECOND-RETURN-CONTRACT` 第二 allocator CALL/RET、热 Default 消费及同 epoch 归属继续冻结。B `D02-R2/SERVICE-TRIGGER-LOADER` 原拒绝 `This content was flagged for possible cybersecurity risk.` 及其依赖继续冻结；本次未构建、运行、修复或重试相关草稿。未新建 cache/Default，未沿旧 hot Equals 路线采样，未使用 computer-use。

本项仅离线工具/文档，无需服务重启、Proto 生成或便携部署。父项、resource ready、D-02 / F-05 / D02-R6 均不因本项晋级。共享 Project/Harness/HANDOFF 的状态由主线统一维护、独立文档写者更新并明确分包集成；本专题及两个新 tools 尚待主线明确提交包，未混入其他已验项的本地提交。
