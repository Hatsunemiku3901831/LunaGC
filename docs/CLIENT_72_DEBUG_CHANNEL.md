# D-01b4：自有合成目标的调试通道合同

本项只加载仓库源码编译的 `channel_fixture.exe`，未重新加载游戏、安装补丁、改客户端文件或启动服务。五种模式各执行两轮，固定目标的脱敏功能摘要相同；每例应用进度、独立 Windows 进程句柄、PUB 退出事件和调试器日志均确认正常退出 0。它说明本机通道在这些有限操作下的行为，**没有解释 D-01b3 的游戏退出原因，也不构成启动、SDK、Marshal 或 D-02 验收**。

## 固定来源与执行对象

- [自有目标源码](../tools/DebuggerChannelFixture.cpp)：真实非法地址写入由 SEH 捕获；真实 C++ `throw 72` 由对应 `catch` 捕获。两个计数独立检查，不能只看异常事件数。随后等待两个具名事件，分别自然调用导出 marker，最后正常返回。等待有有限上限；没有网络、游戏库、账号或私有材料。
- [构建工具](../tools/build_debug_channel72.ps1)：使用本机既有 MSVC x64，`/Od /MT /EHsc /Zi`，产物只写忽略目录。该轮 EXE 为 496,640 字节，SHA-256 `54b68323ec091af156788d08cb36d412f3d812cd6b596cc9c21978766f5196b7`。源码 SHA-256 `2e3c36a17b3bcc5627a63bfc5954b69c6b83e7df2ce7d2d3eb3e968a841406ff`。含调试路径/时间信息的构建不声称跨机器字节重现。
- 调试器为固定 x64dbg `2026.05.27`，Automate 插件 `v0.8.1-ghost_fungus`，Python client `0.9.2`。本轮再次核对 EXE、DLL、插件与 ZeroMQ 四个实际文件哈希；值见[共享摘要](../protocol/cn-7.2/debug-channel-observations.json)。这些文件先前与固定发布压缩包逐字节相符，原始来源证据保留在 `local/migration72/evidence/d01b3-debugger-source.json`。

## 五种实测模式

每例先接收新的 CREATE_PROCESS、SYSTEMBREAKPOINT，核匹配 PID、路径、debugging=true/running=false；新目标硬件表为空，当时选中线程的 DR0/1/2/3/6/7 均为 0。应用首次 AV `C0000005`、C++ `E06D7363` 逐个核对 PUB 与 `ex.code/addr/firstchance`，再 `con 1` 和普通 `run`。应用两个处理计数均为 1。

| 模式 | 应用异常后操作 | 后续事件 | 最终应用/OS/PUB/日志 |
| --- | --- | --- | --- |
| baseline | 无额外暂停点 | 无诊断异常 | phase40，marker 累加3，退出0 |
| pause-con | 在等待事件时显式 pause；确认 PAUSE_DEBUG；裸 `con` 后 run | 无额外 EXCEPTION | 同上 |
| pause-con0 | 同上，但故意用 `con 0` 作负例 | 同暂停地址首次 `80000003`；核归属后裸 con 恢复 | 同上 |
| hw-con | 仅导出 marker 一个硬件执行点；命中后删除自有点，裸 con 后 run | BREAKPOINT 命中 slot0/marker；无额外 EXCEPTION | 同上 |
| hw-con0 | 同上，但故意用 `con 0` 作负例 | 同 marker 地址首次 `80000004`；核归属后裸 con 恢复 | 同上 |

硬件例同时记录事件地址、CIP、DR0、DR7=1，以及删除点前、API 删除后和显式清理后的寄存器。此次 API 删除后 DR0/1/2/3/6/7 已全零；后续清理未改变 CIP/RSP/RAX/RCX/RDX。命中时 DR6 已为 0，不能单靠 DR6=0 否定命中，证据为匹配的 BREAKPOINT 事件和实际地址。此处只处理刚建立的自有 slot0，不清未知断点。

两个负例产生的 `80000003/80000004` 均是首次异常，随后恢复并退出 0，**不是复现了游戏的致命退出**。没有把所有 `80000003/80000004` 都当成调试器事件消费；只有命令刚产生、地址相同且归属明确的合成诊断点允许恢复。所有其他未知/二次异常停止本例并保存待审现场。

## 实际发现与失败记录

1. 固定版本的 [cbDebugContinue](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/commands/cmd-debug-control.cpp) 依据有无参数选择继续状态。裸 `con` 是 DBG_CONTINUE；带任意参数，包括 `con 0`，是 DBG_EXCEPTION_NOT_HANDLED。不能把 `con 0` 当成“消费异常”。负例已在自有目标重现其后续差异。
2. 同文件的 pause 实现先暂停当前线程，在当前 CIP 设置临时软件断点，再恢复线程；[cbPauseBreakpoint](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/debugger.cpp) 删除该点并发 PAUSEDEBUG。因而显式 pause 有自己产生的停止原因。合成 pause 的 PUB 只有 PAUSE_DEBUG，没有对应 EXCEPTION；不能拿残留的 `ex.*` 自动判定当前停止原因。
3. Python client `wait_until_stopped()` 在“已停止运行”或“已不在调试”任一条件时返回 true，`load_executable()`/`pause()` 的返回值不单独证明存活且暂停。探索曾发生 load helper 为 true、紧接着状态断言为 false，而后新目标到达 SYSTEMBREAKPOINT。该次没有盲 run；只核清自有 phase0 目标后停止并排除。正式观察器以新事件和实际 PID/状态建立就绪条件。
4. 第一版正式摘要正确拒绝四例“退出日志缺失”。这是本观察器沿用了上个目标的日志游标：插件 [cb_init_debug](https://github.com/dariushoule/x64dbg-automate/blob/v0.8.1-ghost_fungus/src/plugin.cpp) 会清日志，[LogBuffer::clear/get_since](https://github.com/dariushoule/x64dbg-automate/blob/v0.8.1-ghost_fungus/src/xauto_log.cpp) 重置索引。修正为每次新初始化从 0 读，在退出事件后有界等待日志。修正后两轮全部日志、OS 与 PUB 一致；不能把先前游标错误写成插件 PUB 丢失。
5. 探索首个 `pause-con0` 产生新诊断异常时，旧断言停住；后续人工恢复没有保持有效的事件门。稍后只读记录已无 debuggee，缺少对应独立 OS 退出句柄，不能正式归因为门等待超时。该探索不在五例验收中。正式工具保持单通道与门句柄直到目标退出。错误原始记录保留，没有删去后只报绿灯。

本轮未重现 D-01b3 末段 PUB EXIT/EXCEPTION 缺失；这不证明那些历史事件存在或目标一定按上述路径退出。旧游戏观测仍只有实际日志的两个 `80000003`、一个 `80000004`，没有可归因的最终异常事件/终止栈，根因保持 unknown。

## 复现与证据范围

先按本机配置核空管理员 API 会话、全系统 GAME_PATH 目录/游戏名/合成目标进程和四个调试器文件。工具每次 load 前再查相关进程，不替换未知 target。依赖为已有 MSVC 与忽略目录的 Python client；不下载或启用新调试器，不操作 UI。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/build_debug_channel72.ps1
& local/migration72/debug-venv/Scripts/python.exe tools/check_debug_channel72.py `
  --output local/migration72/evidence/debug-channel-run1.json `
  --summary-output local/migration72/evidence/debug-channel-summary1.json
# 第二轮使用 run2/summary2；比较功能摘要，不比较含PID/时间的原始trace。
python -m unittest tools.test_debug_channel72 -v
```

[观察器](../tools/check_debug_channel72.py)只允许原始输出落在 `local/`。真实进程路径、PID、事件名、内存地址、寄存器、进度 ticks 和日志不进入共享摘要。错误中断时记录当前自有 target，断开 API 连接不等于停止 target；必须实时核清现场才能再跑。两轮原始记录为 `evidence/debug-channel-run{1,2}.json`，先前日志游标失败为 `evidence/debug-channel-trace.json`，探索为 `evidence/debug-channel-probe*.json` 及同名 logs，均在 `local/migration72/`。

实际执行 [8 项离线证据守卫](../tools/test_debug_channel72.py)，覆盖独立 OS/PUB/应用结果不一致、缺失事件或日志、错误诊断地址、硬件 CPU 证据、来源及范围越界；失败摘要不会被批准。两轮功能摘要均为 8,182 字节，SHA-256 `ed04d78644fb390816bd45a6b7878ecd8ef1d326a5e26ffe16c12c593b2b7b02`。动态原始记录含实际时间、PID 和地址，没有声明它们字节一致。

本轮收尾同一 API 无 debuggee，相关游戏/合成进程为 0；已更新忽略的 `debug-channel-active-session.json` 和 `dynamic-active-session.json`，旧索引另存。后续恢复真实启动的最低条件是重新核实时进程/哈希/会话，保持连续观察并持有目标 OS 句柄，以新事件和 CPU 状态区分应用异常、显式 pause 和自有硬件点；只对匹配应用首次异常交付，未知停止或事件缺失停止恢复。游戏实际退出原因、自然非空 Marshal、SDK/HTTP 调用仍未解决；本卡不授权重复加载游戏，下一真实启动观察由主线另行安排。
