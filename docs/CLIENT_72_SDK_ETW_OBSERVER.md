# D02-R2：实时 ETW 自有夹具观察

## 目标、证据和最小实验

目标是判断无注入、无断点且不改变客户端校验的 SDK 观察是否有可审前置；现有 [WinHTTP 元数据审计](CLIENT_72_WINHTTP_ETW_PLAN.md) 固定 Diagnostics provider `64de121b-5f08-5853-ab48-7758f2ea2dd3` 的 event 0 v0/v1，包括 Path、HttpMethod、HttpStatus 及其他字段。声明不证明每次请求发出事件、目标自然调用走 WinHTTP，或包括 SDK curl/Unity/Marshal。当前游戏已退出，本卡不启动游戏或对目标启用 ETW。

Windows 实时 ETW 可能在消费者缺席时落磁盘后备；不以无显式 ETL 文件当作无持久化保证。本次只允许独立自有 provider 的虚构敏感哨兵进入短时自有会话，不发送真实网络/账号数据；不采系统其他 provider 或目标。真实 provider 仍必须等持久化条件和本机 PID 隔离均有可验证证据后，由主线单独决定。

最小验证分层：重新解析已导出的固定事件模板，精确核 provider GUID、ID/版本、Path/Method/Status 类型及未知字段；独立静态消毒函数在内存只产 hostAlias、GET/POST、固定 path 标签、数值状态与时间，未知路径只留长度/事件种类，不输出 query/header/body/账号密码 token 或其 hash。再尝试唯一名称的自有 provider 实时会话，实测 `EnableTraceEx2` 的 PID 与 event ID filter、实时 EventRecordCallback/TDH 类型解码、正常停止及输出哨兵缺失。若权限或系统接口失败，保留原始错误和实际完成范围；禁止改目标或开启系统范围 trace 以使测试通过。

测试须区分声明/原生 ETW 实际/合成语义，不把自有 provider 的成功扩成微软 provider 或客户 SDK 的覆盖。PID错配、ID错配、未知版本、解码错误、消费者不可用或持续落盘风险均为失败出口；自有会话按其名称和句柄正常停止，不删除其他会话或清理未知文件。结果、源码/头文件/工具/产物哈希及独立第三次验收入口写入本文件后由主线审核；本卡不改生产、patch、配置、协议、旧方案或共享索引。

## 实际自有夹具结果

本卡只启用自有 TraceLogging provider `27f11d3b-9dfc-46ce-a401-0f26863c8e99`，两个进程均为该 fixture，自有事件包含虚构 query/header 哨兵。每次会话使用自身 PID 和 tick 组成的名称，先注册两个进程，再用 `EnableTraceEx2` 的 PID 和 event ID=0 双过滤：父进程事件1、子进程0；切换 PID 后子进程事件1。ID=7 对照在 ID 过滤时0，移除 ID 过滤后1。每次调用返回0；EventRecordCallback 对自有 event0/version0 用 `TdhGetProperty` 实际读取 Host、Path、HttpMethod、HttpStatus，解码错误0，经过 `sanitize` 后只输出固定 host/method/route 编号、status 和时间。未知路径纯函数样本仅保留事件外的长度，host/method/status均归零；合成 query/header 哨兵未出现在 stdout。三个阶段为同一短时会话，并非三条目标请求。

最终固定源码的两次自有进程运行 `fixture-final2.log`、`fixture-final3.log` 均记录 `start=0`、两次 PID过滤与正向控制成功、事件 ID 正反对照、子进程退出0、`stop=0`，随后按**该自有名称**查询返回4201（不存在）。不同 PID、时间戳与名称按各轮实际保留；不声称 raw 相同。`tools.test_sdk72_etw` 两项通过：固定本机 Microsoft Diagnostics GUID、ID0 v0/v1 的完整字段名称/类型与顺序，以及 fixture 日志中的正反对照/哨兵不外显；未知 version 拒绝。微软 provider v1 **仅声明检查**；自有 TraceLogging 样本只实际解码 v0，且它的 schema 不是微软清单，不能称已解码真实 WinHTTP v0/v1。

最终源 `tools/sdk72_etw/fixture.c` SHA-256 `8c13f9327b001cda1960eb7223826dcf2f85f6463cd6da0a6caeba8fdceeac09`，`redact.c` `66d26ffab5d67b69f94d5cc1a05a3a07161d6ee8ab59c2a7a0008bf51c7646d6`，`redact.h` `f0fdb845566e692e9e9c6a473510a1b8b82d25d0b8aeee8564a55a39ed09e98d`，测试 `d1e01e026a8f3968ee55a7bf8cd14b56b9139faf43b9e2a8125e3c4778677d3b`，本机编译 EXE `d70b5ca2c126d61e83e09b66100bfd25a8945ac7679e6b4dbd51d9888b5adcac`。MSVC 19.44.35229 `/W4 /WX /O2 /std:c11`、`advapi32`/`tdh`；对象、SDK 10.0.26100.0 五个相关头、compiler/linker 的具体 hash 与本机路径仅在 `local/migration72/d02-active/sdk-etw-observer/hashes.json`。`compile-final.log`、`tests.log` 及各轮输出在同目录。阶段早期会话只测 ID 负例，没有 ID 正对照；最终两轮增加正对照后才给以上结论。

内核 ETW 会先将**完整事件 payload** 放入会话缓冲，消费者取到后才脱敏；若消费者不在或处理不及时，微软文档指出实时记录存在 [backing file](https://learn.microsoft.com/en-us/windows/win32/etw/about-event-tracing) 及丢事件可能；[会话属性](https://learn.microsoft.com/en-us/windows/win32/api/evntrace/ns-evntrace-event_trace_properties) 的 `LogFileNameOffset=0` 不证明此后备路径不存在。系统可能使用 RtBackup 机制，本卡未读取、删除或定位系统后备文件，无法验证其当前具体文件名/生命周期，也没有证明本机异常/断连时无落盘；本轮仅为合成哨兵提供了可容忍输入。**不能对目标启用此原型或声称不落真实敏感内容。** 阶段现有结果不足以实现目标观察器，需先给出并验证进程过滤可用的会话在所有失败条件下不持久化原始字段的具体机制，或选择不产生成套原始 payload 的被动出口；不能自行扩大到系统跟踪或以现成日志工具写 ETL。

## 独立第三次复核

无需启动客户端。先核上述 EXE/source hash，确认当前有权限创建**仅自有合成 provider** 会话，再执行以下命令；这会实际创建并停止一个自有实时 ETW 会话，不是纯文件测试。输出名必须此前不存在，合成事件可能进入 Windows 实时后备机制。自有会话查询非4201、子进程不退出、启用错误、缺正向对照或哨兵外显均为失败，不清理其他会话。

```powershell
cmd /c "local\migration72\d02-active\sdk-etw-observer\fixture.exe > local\migration72\d02-active\sdk-etw-observer\third-review.log"
$env:SDK72_ETW_FIXTURE_LOG = 'local/migration72/d02-active/sdk-etw-observer/third-review.log'
python -m unittest tools.test_sdk72_etw -v
Remove-Item Env:SDK72_ETW_FIXTURE_LOG
```

此 fixture 中不连接微软 WinHTTP provider、不访问目标进程、不发网络请求，不实现真实客户端 PID 的消费者；真实 SDK 路由、Marshal、D-02 和隐私边界仍未闭合。

## 主线第三复核（2026-11-08）

主线已审C源码与固定合成GUID/自有child/session归属；核原EXE SHA后实际运行一次，`third-review.log`记录self1/child filtered0/child positive1、ID filtered0/positive1、decode errors0、child exit0、stop0/query-after4201，两个离线守卫通过，日志`third-tests.log`。主线没有把该次执行称为重新编译或真实Microsoft provider覆盖；运行仅合成数据，不操作客户端、服务、数据库或其他ETW会话。本卡作为有界研究提交，无生产部署。下一最小实验为自有本机WinHTTP请求正对照，以准确判断真实provider是否发出event0及字段内容；不对游戏启用这个原型。
