# D02-R2：微软 WinHTTP provider 回环正对照

## 计划与边界

此前只读审计确认 `Microsoft-Windows-WinHttp-Diagnostics` GUID `64de121b-5f08-5853-ab48-7758f2ea2dd3` 的 event0 v0/v1 模板含 Path、HttpMethod、HttpStatus 与其他字段；自有 TraceLogging 夹具证明了该环境下的一组 PID/event ID 过滤和 TDH 解码，**均不证明微软 provider 会在实际请求中发事件**。本卡以一个自有进程、`127.0.0.1` 的系统分配临时端口和合成 GET/POST 哨兵做一次有限正对照；不启动客户端、不读取真实请求、不联网到外部、不涉及真实凭据，不修改任何系统诊断开关或客户端文件。

短时唯一自有 ETW 会话只启固定微软 GUID、仅该 fixture PID、event ID0；保留 EventHeader 的实际 ID/version 和 TDH 模板/字段证据，比较合成 Path 是否含 query、模板其他字段是否包含合成 header/body 哨兵、状态是否与真实 WinHTTP `QueryHeaders` 相符。回环服务只返回固定状态；不依赖项目固定端口8088/22101/27017/27027。原始事件若产生仅包含本轮合成数据，仍只放忽略目录；文档只记脱敏结果。实时 ETW 可能有操作系统后备文件，本次不读取或清理未知系统后备；合成样本不证明目标采集可防止凭据落盘。

验收为真实 WinHttpConnect/OpenRequest/SendRequest/ReceiveResponse/QueryHeaders 两次返回与自有服务收到请求，实际微软事件数量及版本/字段独立记录，正确 PID/ID 过滤，进程、端口关闭及唯一会话 STOP 和查询不存在。若 provider 不发事件或初始化、权限、服务、TDH 或回收失败，保留**第一次**完整结果并停止；不启用额外 provider、不改注册表/诊断开关、不扩大范围，也不把0事件解释为SDK不走WinHTTP。仅本地源码/工具/证据哈希和独立第三方可审核命令交主线；真实目标采集需后续另行判断。

## 唯一一次实际结果

自有进程调用 WinHttpOpen（NO_PROXY）、WinHttpConnect、OpenRequest、SendRequest、ReceiveResponse、QueryHeaders。IPv4 socket 只绑定环回地址的系统临时端口，代码拒绝项目四个固定端口；本轮命中临时端口且未重复尝试。同步 GET 和 POST 分别返回数值状态207、208，服务端各收到包含虚构 query/header 哨兵的请求，POST 还收到虚构 body 哨兵；服务端线程退出0、两次检查通过、监听 socket `closesocket` 返回0，结束时同一环回端口重新 bind 成功。该服务只核哨兵出现，方法与路径的输入来自 fixture 中实际 WinHTTP 调用参数，并未保存或打印服务端原始请求。

唯一自有会话 StartTrace 和 `EnableTraceEx2` 均返回0，使用固定 Diagnostics GUID、当前 fixture PID 和 event ID0；回调实际收到 **0 个该 provider 事件**，然后 `ControlTrace STOP=0`，按该会话名查询返回4201（不存在）；进程退出0。记录的 v0/v1、Path、header/body、状态等计数均为零，**因为没有事件，不能解释成字段为空、哨兵未被复制、状态不包含在事件中或其版本有效**。代码具备按事件 version 检查已审模板及提取各字段的分支，但本轮没有调用该成功分支；没有取得真实事件或实际 TDH schema。此前自有 TraceLogging 正反过滤只证明其自有 provider，不能替代这个 provider 的事件正对照。

请求结束后工具请求 ETW flush 并等待1.5秒，但未记录 flush 返回码或 ETW lost-event 计数；该0只说明本次有界观察未调用目标 provider 的回调，不能排除事件未生成、过滤作用域、缓冲、丢失、时序或其他原因。没有修改注册表或诊断开关、没有扩大 provider/事件范围、没有第二轮。该结果不证明目标 SDK 使用或不使用 WinHTTP，也不闭合目标路由、隐私或 D-02。符合本卡失败出口，保持停止；若要另做更长等待、不同 provider/事件或日志设置，必须先明确该独立实验的证据和范围，由主线协调，不能将本次结果当授权重试。

## 可复核输入与命令

源 `tools/sdk72_etw/winhttp_fixture.c` SHA-256 `838c37a8c1a9d9c3ee77c5224cbb55abda8c07510dcfff5705a55d0cfbda15d2`；离线守卫 `tools/test_sdk72_winhttp_etw.py` `41f7463db84dc271b0edbaddec3531be740268ab7858f6ab366d5828445e42d2`；本机 EXE `e8684c14e94121a23700935347c9972e73cd0353e3da5bbf8267cc735408e5cb`，首次日志 `27f8dc340e6465ca1ec54c0fc5cc4a4e5fb799de6cd2e80514283191f8848b1d`。完整对象、`winhttp.h`、四个 ETW 头、MSVC/compiler/linker hash 在 `local/migration72/d02-active/sdk-etw-observer/winhttp/hashes.json`；MSVC 19.44.35229、SDK 10.0.26100.0，`/W4 /WX /O2 /std:c11`，链接 `advapi32.lib tdh.lib ws2_32.lib winhttp.lib`。原始日志/退出码、`compile.log`、`tests.log` 同目录；日志不输出合成哨兵。

在预先进入 MSVC x64 工具环境的当前 checkout，可为审阅**仅重新编译**到一个此前不存在的独立忽略目录：

```powershell
$fixtureSource = (Resolve-Path tools/sdk72_etw/winhttp_fixture.c).Path
$fixtureOut = 'local/migration72/d02-active/sdk-etw-observer/winhttp/independent-build'
New-Item -ItemType Directory -Path $fixtureOut -ErrorAction Stop | Out-Null
Push-Location $fixtureOut
cl.exe /nologo /W4 /WX /O2 /std:c11 $fixtureSource /Fe:winhttp_fixture.exe /link advapi32.lib tdh.lib ws2_32.lib winhttp.lib
Pop-Location
```

第三方只读复核现有第一次输出，不重发请求或重启 ETW：

```powershell
Get-FileHash -Algorithm SHA256 tools/sdk72_etw/winhttp_fixture.c, tools/test_sdk72_winhttp_etw.py, local/migration72/d02-active/sdk-etw-observer/winhttp/winhttp_fixture.exe, local/migration72/d02-active/sdk-etw-observer/winhttp/actual-once.log
python -m unittest tools.test_sdk72_winhttp_etw -v
```

两项 Python 守卫仅检查首次日志的请求返回、限定端口、服务/会话关闭及没有把0计数误称为已观测字段；不再次启动 fixture，不是新的微软事件证据。所有文件及 HTTP 数据均为自有合成样本，未操作目标客户端、补丁或服务器。

## 主线证据审核

主线审完整C源、固定GUID/当前自有PID/ID筛选、WinHTTP实际调用、回环服务及清理路径，逐hash核source、EXE和首次日志；只读重新执行两项守卫通过，与自有provider两项合跑为`main-offline-review-tests.log`共4项。没有重发请求或重启这次Diagnostics实验，不把测试进程exit0当观察覆盖成功。该候选按0事件事实收口；下一沿自然加载11模块的准确导入/动态解析和SDK调用链另定实验。本项只研究工具/测试/脱敏文档，无生产/Proto/config/patch/资源变化，无服务部署。
