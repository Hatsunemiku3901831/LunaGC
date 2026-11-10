# F-03d3s2-pool / F-03d3f4d2：同 epoch TLS/R1/Pool 条件只读 collector

本批实现合并诊断，**未批准或执行 live、replay、CPU、外部 call、pop 或 capture 写**。`moduleTLS+1990` 的 Pool 状态与 Avatar `moduleTLS+19d0` 分别读取，pointee 和写域不互用。primary 仅为 `launch-primary-thread`，不证明自然 Pool/Avatar allocator caller。完整 Pool、Avatar、Scene 和 D-02 不晋级。

## 来源与最小扩展

冻结 [Pool 合同](CLIENT_72_D02_POOL_TLS_CONTRACT_20261110.md) SHA-256 `a6d6810a629a518cde1288c5137b281d217f76e9d4ea759cfc7147b69ebc68ba`。私有 `a-pool-tls/delivery.private.json`、implementation spec 和两份 `4fd7d618c280339d98e58b26f3b8223bb6651412f3408670a306d9965601ef8f` 报告均固定 pin。只读 `verify_sources()` 核新的五 pin，再复用原七 pin、owned ABI 与 R1 来源核验；原七 pin 没有修改。

Pool 来源强度仍是冻结指令文本及保存的代码 SHA；原1216B机器码没有 hex，本批没有重读 PE 或从反汇编重造机器码。

- 新 [合并 collector](../tools/client72_d02_tls_pool_inputs.py)：复用现有 TLS/R1 helpers 和同一 ledger，新增 Pool 条件步骤。
- 新 [人工测试](../tools/test_client72_d02_tls_pool_inputs.py)：人工 bytes、Python API callback 和冻结前 Python 源码参考，不导入旧现场 heap。
- [原 core](../tools/client72_d02_tls_inputs.py)：仅增加两个固定 profile，并提取两个实际消费者共用的入口/结束汇总；原默认语义保持。
- [原 adapter](../tools/observe_client72_tls_inputs.py)：仅新增 `include_pool=False/True`，选择两个已知 collector/source gate，True 要求第三个 tool pin。primary API/句柄逻辑不复制、不扩展。

原21项测试源码和断言逐字节保留。原规格、owned 工具/报告、旧证明/源码快照和共享文档均未修改；新源必须进入新的 epoch gate，不能沿用旧实现 SHA 的审批。

## 两个固定 profile

| profile | 首遍上限 | 首遍及统一 final 总上限 | 单读上限 |
| --- | --- | --- | --- |
| `avatar-r1`（默认） | 13读/340B | 26读/680B | 208B |
| `avatar-r1-pool` | 18读/369B | 36读/738B | 208B |

没有任意 cap 或通用 runtime 参数。`core.collect()` 继续默认原13项；`pool.collect()` 先在同一 ledger 中执行原 TLS/R1 helpers，再追加最多5读/29B，最后只做一次共同 final。原 Avatar/R1 partial 时不扩展 Pool，不用 `+1990` 替换 `+19d0`。

Pool 共用的 loader index、TEB+58、TLS slot 对应本事务 first ordinal1–3；不会复读这三项作为另一轮首读，也不能接受旧报告 TLS 输入。新增 Pool 记录保留对应原 reader，final 继续带同一来源标记。地址、宽度、原 bytes、short/error、全部首/final计数仍由同一 ledger 管理。

首读失败停止扩展，失败 span 不重试；成功 unique exact spans 在全部首读后统一复读。final 首次错误或改变即停止。分支依据是原始观察；`pool.stableInputBytes=false` 时不能把初步 `stopBefore` 当作已稳定输入。所有报告保持 nonAtomic，不声称没有并发 writer。

## 条件读与精确停止点

P 是本轮 `M+1990/8` 的原值。P=0是合法原分支；非零才读取P本身。

| 条件 | 追加首读/字节 | 合并首遍；含final总上界 | stopBefore |
| --- | --- | --- | --- |
| P=0，全局 `B+5bc87d8/8` 为0 | 2/16 | 15/356；30/712 | `14050ab6e`，call `1405336c0` 前 |
| P=0，全局QWORD非零，再读 `B+5bc7701/1` | 3/17 | 16/357；32/714 | byte=0：`14050a9f5`，否则 `14050ab6e` |
| P非零，先读 `P+0/4`、`P+8/1`，bit0清除 | 3/13 | 16/353；32/706 | `14050aac8`，call `14050a210` 前 |
| bit0置位，`P+18/8` 为0 | 4/21 | 17/361；34/722 | `14050aac0`，首次P+8写前 |
| head-array非零，再读 `P+20/8` | 5/29 | 18/369；36/738 | bound=0：`14050aac0`；否则 `14050a8f7` 首未采全局读前 |

`P+0` 是完整 U32 观察，0合法，业务含义未知，不命名为TID。flags保留完整byte，只用bit0分类。`P+18` 只记录地址，不读取head-array条目。**`P+20` 是完整 unsigned U64 bound，不是pointer**；0、`2^32`、`2^63`、`2^64-1` 均按原值记录，不截断、不钳制、不分配数组、不循环或扩大采样。

全局QWORD非零只选择条件byte读取，不解引用该QWORD。短读/API error与真实0严格分开。没有读取 `P+40`、head-array、node、counter、`B+5bc7758` 或 `B+5bc7798`；最后两者只可作为未采前沿说明。没有取得新Pool class、自然调用帧、slot helper返回或节点所有权。

报告保留 preferred `stopBefore` 及本轮基址推导的 runtime 地址。call前沿只列来源合同已知callee，写前沿只列P+8/1B及原指令常数0；这些描述的 `approved`/`executed` 均为false。`allocatorReturned`、`headArrayRead`、`nodeRead`、`loopExecuted`、`externalCallExecuted`、`capturedWriteExecuted`、`capturedWritesApproved`、`canReplayPop`、`naturalPoolAllocatorThreadObserved` 全为false。

## adapter 与外部门禁

原 [集成合同](CLIENT_72_D02_TLS_INPUT_COLLECTOR_20261110.md)继续适用。默认 `sample_primary(..., include_pool=False)` 使用原collector和两个tool pin；True使用新合并collector，并要求 epoch `implementationPins` 精确包含：

```text
tools/client72_d02_tls_inputs.py
tools/observe_client72_tls_inputs.py
tools/client72_d02_tls_pool_inputs.py
```

True时 source gate 额外核Pool合同/来源五 pin。非bool profile或缺少新pin在入口拒绝，仍finally处理原 primary handle。完整 image/creation/module before/after、原hThread/TID及TEB关系保持，ThreadBasic最多2次/96B独立输出、alive2次、GetThreadId/GetProcessIdOfThread/GetThreadTimes各1、primary CloseHandle1；没有新增线程选择或API。

没有 live CLI、ready或现场启动器。主线仍须审核并实际执行 fresh stock五输入、carrier/保护/配置、源pin、实时ready、整轮预算、当前完整 process/module callback、采样时机和B临时DLL互斥门禁。只能在同次正常CreateProcessW原primary handle仍保留时集成，不能在旧报告结尾追加Pool读取或从历史PID/TEB重建同epoch。

## 实际验证与交回

执行纯 Python：

```powershell
python -m unittest tools.test_client72_d02_tls_inputs tools.test_observe_client72_tls_inputs tools.test_client72_d02_tls_pool_inputs -v
```

本批 **34项通过，0失败/错误/跳过**：原21项全部保留，新增13项含条件表各分支、真实零、完整U32/U64、不多读、全局short不猜零、API/OS error、reader/final来源、Pool final漂移、固定cap、旧partial不扩展，以及新profile/pin/identity/cleanup生命周期。

另将冻结前 core SHA `be7544d48a6907288c9cadb3db9a50859bf819b5caee9be28c9043d4259afc37` 作为只读 Python 参考，在完整、首读错误和final错误三种人工图上比较新默认 core，**完整报告与请求序列相同**。它只核默认语义，不是自然采样。实际文件门禁通过原七pin及新五pin，原件没有改写。

私有简明计划、source-before/after、输入pin、原始测试 stdout/stderr/argv/exit、静态来源核验与交付摘要均在 `a-tls-preparation/pool-collector-implementation/`。本实现代理未执行 Windows API/native/JVM/DB、查询 GAME_PATH/进程端口、启动客户端、网络、Git或子派。后继为主线独立第三人工图/原始ledger/失败路径审核和现场helper审核，再决定是否申请新ready；live/replay/write仍未批准。
