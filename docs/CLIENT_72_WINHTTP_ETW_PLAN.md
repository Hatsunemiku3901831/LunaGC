# D02-R2：WinHTTP 被动观察出口的只读审核

2026-11-08后继：仅自有provider的[PID/ID夹具](CLIENT_72_SDK_ETW_OBSERVER.md)经主线实际第三次通过；[真实WinHTTP回环正对照](CLIENT_72_WINHTTP_ETW_FIXTURE.md)一次GET207/POST208却收到0个Diagnostics事件，已按失败出口收口，不对游戏启此通道。以下“尚未实测”是初始元数据审计边界；后继不能以合成过滤通过代替Microsoft provider或SDK覆盖。

2026-11-07，结论：**当前不建议直接对目标进程开启 ETW**。本机存在可能包含路径、方法、状态的 WinHTTP 诊断模板，但不知道自然请求是否触发它，也未证明目标 PID 过滤、无原始落盘和脱敏在本机实际成立。仅模板不能解除 R2、SDK、Marshal 或 D-02。只做 provider 元数据、已有源码及官方文档审计；未连接/读取目标客户端、启动跟踪会话、发请求、改补丁或配置。

## 本机可验证结果

使用 `Get-WinEvent -ListProvider` 的元数据和 `wevtutil gp /ge:true /gm:true /f:xml`，而不是读取历史事件；另用 `logman query providers` 核主 provider 注册信息，该命令同时显示注册进程名称清单，没有读取其进程内容或保存该清单。本机 Windows SDK `10.0.26100.0` 存在 `evntrace.h`、`evntcons.h`、`tdh.h` 与 `winhttp.h`；`wevtutil`、`logman`、`tracerpt`、`wpr` 可用不等于可安全直接采集。首次误在 um 子目录查 `evntprov.h`；后在 shared 找到，并核 PID filter `0x80000004`、event ID filter `0x80000200` 和 PID 上限8。

| 本机 provider | 注册元数据与界限 |
| --- | --- |
| `Microsoft-Windows-WinHttp` / `7d44233d-3055-4b9c-ba64-0d47ca40a232` | 74个 ID/version 记录，61有模板；813–815 的 URL 字段属于自动代理查找，不能当所有请求；1053 有 Host/Module/Process 等。PII_PRESENT、PACKET 等关键词存在，关键词名称不是完整脱敏保证。 |
| `Microsoft-Windows-WinHttp-Diagnostics` / `64de121b-5f08-5853-ab48-7758f2ea2dd3` | event ID 0、version 0/1；v1 比 v0 多 `SessionGuid`。两个模板都有 `Path`/`HttpMethod` 为 ANSI 字符串，`HttpStatus` 为 UInt32；还含 ProcessName、ModuleName、Host、FaultReason、FaultCode、ErrorCode、ParseBuffer（UInt64）及其他字段。描述的 FaultDescription 与模板中的 FaultCode 命名不同，解码须以同版模板为准。 |

`Path` 未证明不含查询串或其他敏感内容，不能原样保存；`ParseBuffer` 只知道类型，不能声称是 body、指针可安全解引用或无敏感信息。两个事件是诊断元数据，未证明每个成功请求都发出或按请求顺序发出。事件头 PID/TID 是发事件的上下文，不自动证明跨进程服务代表哪个客户端请求。SDK 自带 curl、Unity 或独立进程可能完全绕开这些 WinHTTP 事件；当前 patch 的 `native_sdk.rs` 只对价格路径的 curl 处理属于旧源码行为，不能当自然目标调用证据。

本机 provider 清单 `local/migration72/d02-active/sdk-etw/winhttp-provider.xml` SHA-256 `afd11159cab37a40285cd94a669a7ab2df520653c72d7734ed8e38f64fa13d50`，诊断清单 `diagnostics-provider.xml` `eb3acc4085688823bb1f683bcd604cedb3c195f8dfd700915974882c7dd1528c`；`Microsoft-Windows-WinHttp-events.json` `e1ba046e7726743b29dbf490828e2c7b583282a4a52797c050d51ecea867a9b6`，`Microsoft-Windows-WinHttp-Diagnostics-events.json` `deabcfa187e1e5b0640d33c2e538a7542949ebcfaccb1ecf618a83cf3713a4d6`。JSON 为 `Get-WinEvent` 的事件声明及模板，不含事件实例。不同 Windows build 或资源版本须重新核对，不能套用该 hash。

## 过滤和落盘边界

微软说明 [EVENT_FILTER_TYPE_PID](https://learn.microsoft.com/en-us/windows/win32/api/evntprov/ns-evntprov-event_filter_descriptor) 是 `0x80000004`，配 `EnableTraceEx2` 限定启用时已存在的 PID，最多8个；重新启动后 PID 不会自动继承。文档同时限定适用的会话类型，不能仅凭调用成功断言所有本机 provider/会话组合生效。先以持有进程句柄、image、creation FILETIME 和当前 PID 绑定，启用时及结束时复核；EventHeader PID 不匹配一律丢弃，重启/退出即结束本轮，不能追随重用 PID。事件 ID 过滤仅限制事件，不会删除字段；目标外进程的可验证拒绝仍须 fixture。

[ENABLE_TRACE_PARAMETERS](https://learn.microsoft.com/en-us/windows/win32/api/evntrace/ns-evntrace-enable_trace_parameters) 支持版本2的 filter descriptors，可选 process-start-key；[实时消费](https://learn.microsoft.com/en-us/windows/win32/etw/consuming-events) 可用 `OpenTrace`/`ProcessTrace` 的 EventRecordCallback 与 TDH 在内存解析。**但实时消费不等于无磁盘落盘**：微软明确指出无消费者的实时会话可能用磁盘缓冲，[EVENT_TRACE_PROPERTIES](https://learn.microsoft.com/en-us/windows/win32/api/evntrace/ns-evntrace-event_trace_properties) 有此限制。因此设实时模式和空 LogFileName 并不足以证明无后备文件；AutoLogger 的 `DisableRealtimePersistence` 文档仅对应该配置路径，不能擅自推断一次性会话也具备同样保证。未证明关闭/故障/消费者掉线时不落盘前，不能对真实目标采集，也不能使用 netsh、WPR 或默认 ETL 来代替。微软亦指出 [WinHTTP trace 可能含敏感信息](https://learn.microsoft.com/en-us/windows/win32/winhttp/winhttp-security-considerations)。

消费者即使只保存白名单字段，ETW 在内核/用户缓冲中仍传送完整 payload；任何调试输出、异常文本、原始事件序列化、自动转储和备份都不得承载原始内容。若以后经过独立验证，持久化只能包含事件相对时间、已核身份、限定 provider/ID/version、允许的固定方法枚举、数值状态和**固定路由标签**；在内存先丢弃 query、fragment、host、用户名、路径参数、headers、body、错误自由文本和 ParseBuffer，不保存原 URL、变长原始字段或敏感值的 hash。只识别精确固定路由及已审参数化路由标签，未知/编码歧义标记 unknown，不输出原始路径。禁用堆栈和 dump；统计丢包，禁止将零事件推断为无请求。

## 后继最小实验与停止条件

第一步不是重启游戏：先实现并审查独立自有进程与实时消费者的诊断 fixture，验证本机选定会话的 PID filter 真正屏蔽第二自有 PID、只允许所选 ID/version、未知字段/异常/consumer 停止均不输出原文；以无真实凭据的合成 query/header/body 哨兵检查消费者输出和所有可验证会话缓存/日志。必须有明确且可验证的非持久会话方案，连同消费者异常、断连和关机/故障行为；若无法证明，记录阻塞而不打开目标会话。fixture 本身是否能让 WinHTTP-Diagnostics 生成事件未知；不能用自建 provider 的成功冒称微软 provider 覆盖成功。

仅上述前置和主线复核完成后，在已有自然 UI 上选择用户可见的一次自然动作，启用仅该 PID 的所选事件并设短时预算，顺序检查身份、过滤启用结果、消费者接收与丢失计数、停止自身会话及资源释放。事件 0 的版本、目标 PID、方法枚举、状态和固定路由标签匹配才是该次事件的有效证据；路径无法安全分类、provider 未发事件、跨进程事件、身份变化、过滤或持久化控制无法核验、异常/丢包即停止并保持 unknown，不扩大采集范围或重试自然启动。SDK 请求若实际走 native curl 或 Unity 而非该 provider，须另寻有来源的被动出口；ETW 不能提供托管 `Marshal.PtrToStringAnsi` 的自然参数或返回。若无独立可安全采集的出口，则等待现有客户端/服务端的脱敏日志或更精确的自然观测证据，不将静态命中、无事件或猜测网络参数当验收。

此次未实现消费者、未启动会话、未做 fixture，也无目标实机新增证据；SDK 路由、信任链和 D-02 均未验收。
