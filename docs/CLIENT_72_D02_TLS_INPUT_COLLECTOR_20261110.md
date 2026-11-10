# F-03d3f4d2：新 stock 同 epoch TLS/R1 只读 collector 实现

已新增纯 callback collector、保留原 primary handle 的 adapter 和人工测试。**本卡只准实现，尚未运行任何 stock 采集、primary API 或 native CPU；live/replay 未批准。** 本机两 fresh owned ABI 的 6 次查询由主线先前完成，不能算作本 collector 的自然验收。完整 Avatar、allocator、pool、g2 和 D-02 状态不变。

## 文件与冻结来源

- [纯 collector](../tools/client72_d02_tls_inputs.py)：固定读图、预算、raw 记录及最后统一复读；没有 Windows backend。
- [primary adapter](../tools/observe_client72_tls_inputs.py)：正常 launch wrapper 和 `sample_primary`，没有 live CLI、现场发现或线程选择入口。
- [读图人工测试](../tools/test_client72_d02_tls_inputs.py)、[adapter 人工测试](../tools/test_observe_client72_tls_inputs.py)：自造 byte spans、Python API 回调和 Mock；没有加载游戏、真实 CreateProcessW 或 NT API。

合同为只读 [TLS 输入规格](CLIENT_72_D02_TLS_INPUT_SPEC_20261110.md)，SHA-256 `81c85ace479805daf2b41eeacb2311b09d2b08d2f24dfd3ac7f654bc43b3cfe3`。R1 字段直接来自原 fresh SHA `73e55971e1ab3785e9a13b7619bedda89da9a81fbcd548c740bfec90e60a6754` 的 `source.audit27–35`，只作为地址来源和解码规则，不导入旧 heap。

`verify_sources()` 只读七个固定文件 pin，核原 monitor、owned C++/driver、两 fresh 报告及主线独立 review；同时核原 27–35 的宽度、三个模块 RVA、实际 GenericClass/classInst/argument/backref/普通槽关系。owned 两 fresh 证明 SHA 为 `829fde6fea17a4b6733a0e92776bed5f508a5e5fec5e7ec431875c02258c2f79`，独立 review SHA 为 `88d59287628a883f62a372acfc40e902076455fc27fa4ccbb2e4c37bc80ebd46`。原规格、owned 源码/driver/9 tests、旧工具和证据均未修改。

## 固定读图与预算

| 顺序 | 同 epoch 来源 | 字节 |
| --- | --- | --- |
| 1–4 | `B+5eef080` → `TEB+58` → `vector[index]` → `moduleTLS+19d0` | 4、8、8、8 |
| 5–6 | `B+588ca20` → 实际 List class header | 8、208 |
| 7–10 | `header+68` GenericClass → classInst → 唯一 argument pointer → UInt32 descriptor | 32、16、8、16 |
| 11–13 | `B+5bc8f18` 普通 root → `root+1255*8` → `B+5bc96f8` 原 comparison cell | 8、8、8 |

首遍最多 **13 读/340B**；只把成功首读的 unique exact spans 在所有首读结束后统一复读，含 final 最多 **26 读/680B**，单读最多 **208B**。TLS index 为 unsigned32，**0 合法**。全指针使用完整 uint64，地址相加溢出不会发出 backend read。

零指针、short/API error 或语义不符立即停止首遍扩展并保持 partial；失败 span 不重试。已成功 span 仍在统一 final 阶段复核，包括成功读取到的零值。final 首次错误或漂移即停止并保留未完成状态、实际 raw、首/末 ordinal；没有通过重复失败读来取得成功。所有请求、实际返回字节数、raw hex、错误、阶段、时间及 backendCalled 都保留；重叠成功 span 出现矛盾也 partial。

R1 核 definition1255、methodInst0、backref 为实际 List class、count1、UInt32 kind9/byref0。保留完整 208B header、真实 `ca`/`c8`、`b4` 大小、descriptor bits/data、普通槽和 comparison 值；不强制 flags、大小32或旧比较结果。普通槽保持本轮真实值，不能用 closed backref 代替。三组指针 equality 只是观察，没有执行或证明对应 native 分支。

纯 collector 不证明传入 TEB 的来源，返回 `requiresRetainedPrimaryProof=true`、`candidateGraphOnly=true`。`candidate` 只表示这组候选图和复读成立；不会批准原指令执行或假定自然 allocator 线程。

## 原 primary handle 与独立 API 账本

`RetainedPrimaryBackend(base)` 包住主线已建立的原 backend，不自行构造 Windows backend。`launch(target,cwd)` 保持原两个返回值 `(hProcess, PID)`，直接正常 `CreateProcessW`，参数为空、inheritHandles=false、**creationFlags=0**。保留此次原 `hThread`/TID、完整 PROCESS_INFORMATION raw 和 API 返回；拒绝第二次 launch 覆盖原句柄。没有 Suspend、Debug、OpenThread、OpenProcess、线程枚举重选或查询 class fallback。

`sample_primary` 的成功路径依次为完整 process identity before、原 hThread 三个身份 API、alive before、ThreadBasic before、固定读图与统一 final、ThreadBasic after、alive after、完整 process identity after、finally close primary。

| 新 primary API | 上限 | 必须保留/比较 |
| --- | --- | --- |
| GetThreadId / GetProcessIdOfThread / GetThreadTimes | 各1 | 原 CreateProcessW TID/PID；创建 FILETIME 与其他时间 raw |
| WaitForSingleObject(hThread,0) | 2 | 前/后均 WAIT_TIMEOUT；退出即 partial |
| NtQueryInformationThread class0 | 2 | status=0、ReturnLength=48、完整原48B，PID/TID=原句柄身份、TEB 前后相同 |
| CloseHandle 原 hThread | 1 | finally 一次，返回/错误保留，失败强制 partial |

两次 ThreadBasic 的 **96B 输出 buffer 独立记账，不计入 RPM 680B**。Affinity/Priority/ExitStatus 保留，不要求整块 raw 相同；alive thread 的 exit FILETIME 没有零值断言。报告同时列 `apiRequests` 和实际 `attempted` 的 `apiCounts`，不会把 unavailable API 当实际成功调用。后 query 错误仍保留 before、after raw 和已经取得的 read ledger；没有第二个线程或 TEB fallback。

sample 只关闭原 primary handle，原 process handle 继续由主线外层 finally 负责。wrapper 的 `close(hProcess)` 也会尝试 primary cleanup，覆盖采样前的外层失败；已尝试的 primary close 缓存结果，不二次调用或隐藏关闭失败。

## 主线 helper 的准确集成合同

本卡没有 live 主程序，也没有生成 ready、调用 GAME_PATH、盘点进程/端口或启动客户端。实际 stock 输入、保护/配置、完整源 pin、实时 ready、采集时机、整轮预算及与 B 临时 DLL 的现场互斥，由主线另审和执行。

主线 helper 必须在同一次正常 launch 的原句柄仍保留期间调用本 adapter；不能先让旧 monitor 的 finally 关闭进程/primary，再从旧报告采样。不得复用这里的人工 fixture 或历史 PID/TID/TEB/heap。

传入 `epoch` 字典包含：

```python
{
    "epochId": "本轮由主线固定的唯一标识",
    "pid": 本次CreateProcessW返回PID,
    "image": 本轮已核对的原游戏完整路径,
    "processCreationTime": 本轮原进程创建FILETIME整数,
    "module": {"name": "YuanShen.exe", "path": 同一原文件路径,
               "base": 本轮实际模块base整数, "size": 本轮实际模块size整数},
    "stockInputPins": 本轮实际核对的五个stock输入SHA字典,
    "implementationPins": 两个新工具的已审核完整SHA字典,
}
```

五 stock 输入必须匹配冻结 profile 的 binary/metadata/startup/sdk/astrolabe；两个 implementationPins 必须匹配本次源码。adapter 自动执行只读 `verify_sources()`，主线仍须把新工具 pin 绑入本轮正式 gate/ready。

另提供 `identity(hProcess,pid)` callback，使用本次原 hProcess 返回 `{pid,image,processCreationTime,module}`；before/after 与 epoch 精确相等。主线负责其中实际文件身份、进程状态及模块观测的 API/错误证据和整轮开销；callback 应返回已经统一路径表示的完整基线，不能拿旧报告原样返回冒充本轮观测。adapter 保留 callback 原返回，任何创建时间、image、PID、base/size/模块变化都 partial，随后仍 finally 清理原 primary。

`base` 由主线既有 Windows backend 提供 kernel32/nt 句柄和普通 process 方法；wrapper 自己绑定本卡 primary API 与精确 ReadProcessMemory。没有 collector 自行枚举选择 primary 的路径。

## 实际测试与边界

主线独立重跑21项人工测试通过，并另构造完整64位/index17、不同class flags/size的第三人工图，实际26读680B/统一13span相同；null context反例实际首4读后只复读这4个成功span，不进入R1图。七冻结来源及原audit27–35字段关系经真实文件门禁通过，源码前后保持。主线proof为 `local/migration72/d02-resume-20261110/a-tls-preparation/collector-main-review/main-review.private.json`，SHA `6f2c79a753aff0cfe9a132aeea35fd660aa9deb53d884aa2923161437960a2ca`。该审核0真实Windows API/native/live，只完成工具实现子项。

执行命令：

```powershell
python -m unittest tools.test_client72_d02_tls_inputs tools.test_observe_client72_tls_inputs -v
```

实际 **21 项人工 Python 测试通过**，覆盖 index0/31、完整64位、13/340与统一26/680、单208上限、零指针、short/error/地址溢出、definition/method/backref/count/kind/byref、真实 flags/大小变化、final漂移、前后 process/TEB/退出变化、status/length/48B raw、source/epoch拒绝、原句柄flags0/两返回值/一次关闭及关闭失败。不同 priority raw 被正确保留；原本完整的候选图遇单独 close 失败也变 partial。

另只读核七个冻结文件和 source.audit27–35 的实际字段关系通过。全部人工 spans/API 回调均明确标人工，不能代验自然线程/真实 Windows launch 或 stock 采集。私有计划、原始测试 stdout/stderr、源 pin 和交付摘要仅位于 `a-tls-preparation/collector-implementation/`。

本实现代理未构建、运行 native/JVM/Mongo、查询现场/GAME_PATH/进程/端口、操作客户端、部署、修改 Git/共享文档或派子代理。后继为主线独立审核、集成实际 stock 门禁/helper 和整轮预算，再决定是否批准同 epoch 采样；本次没有 live 许可。`naturalAvatarAllocatorThreadObserved=false`、`canReplayPop=false`、`liveApproved=false`、`replayApproved=false`；未采15/82池、block、GC 状态，未写 capture 或执行 CPU/OS 仿真。
