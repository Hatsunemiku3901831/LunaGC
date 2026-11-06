# D-01b5f：软件点与前序异常的自有合成对照

本卡补足[D-01b5e](CLIENT_72_STARTUP_OBSERVER.md)后的通道缺口。相同固定fixture、相同最终驱动与共享停止分类下，**有、无前序AV/C++的两轮都自然命中主EXE软件入口和匹配返回，并以OS/PUB/log三方0退出**。这收窄“前次con1普遍破坏后续软件点交付”的解释；没有再load游戏，也不据合成成功归因游戏保护、线程状态或R2成功。

## 输入、正常流程与实测

复用原`tools/DebuggerChannelFixture.cpp`及既有二进制，没有新建/构建目标。fixture SHA-256固定为`54b68323ec091af156788d08cb36d412f3d812cd6b596cc9c21978766f5196b7`，源码为`2e3c36a17b3bcc5627a63bfc5954b69c6b83e7df2ce7d2d3eb3e968a841406ff`；调试器/插件来源仍为D-01b4已核版本。只连接既有管理员Automate，未使用computer-use、未启动新调试器或服务/数据库。

[observe_client72_software_fixture.py](../tools/observe_client72_software_fixture.py)直接复用真实游戏观察器的`stop_action`、`register_action`和`matching_returns`，沿用同三个系统终止诊断点。只有一个fixture主模块marker软件点；自然入口命中后先删除它，再装一个自然返回点，按原TID/RSP+8关联，返回后移除。marker参数必须为1，返回时volatile计数必须为1；两个自有event gate仅控制合成程序原有等待流程，最后第二次自然marker累加到3。没有远程调用或构造游戏函数输入。

每例上限30秒。新的CREATE_PROCESS/SYSTEMBREAKPOINT、目标路径/PID/独立OS句柄和暂停状态均验证；逐停点双API/DR、异常身份/first-chance、点名/地址与后续普通run保持同合同。合成源码已定义正常exit0，故三个自有终止诊断点核其退出码0及phase40后裸con，允许该合成进程正常结束；游戏未知终止点仍按原合同停。

| 最终同版本实验 | 无前序异常 | 前序AV+C++ |
| --- | --- | --- |
| 观察停点 | 7 | 9 |
| 应用异常交付 | 无 | 1 AV、1 C++，逐个匹配后con1 |
| 普通SW入口/匹配返回 | 各1次，均EVENT_BREAKPOINT | 各1次，均EVENT_BREAKPOINT |
| 自有点继续 | 每点裸con+run | 同左，包括两个con1之后的marker |
| 正常终止诊断点 | 3 | 3 |
| 最终fixture | phase40、marker3、异常处理0/0 | phase40、marker3、异常处理1/1 |
| OS / PUB / 日志退出 | 0 / 0 / 0，EXIT+STOP齐全 | 同左 |
| 日志/PUB/恢复 | eviction0、订阅线程存活、两主模块代码窗口恢复 | 同左 |

两例均使用同一个最终驱动和共享观察器源码hash；原始证据是`d02-r2-synthetic-sw-exceptions.json`与`d02-r2-synthetic-sw-no-exceptions-v3.json`。共享[摘要](../protocol/cn-7.2/software-breakpoint-observations.json)记录这两个独立过程及全部输入/来源hash。原19项共享停止/单点/void返回守卫保持通过，本卡的新增运行回归是这两个真实自有进程，不能算作真实游戏验收。

主线已审核完整291行driver及专题、7份原始来源hash，两成功过程逐停点双API/DR、同TID/RSP+8返回、两代码窗口恢复、三正常终止点及OS/PUB/log0、EXIT/STOP均独立核同；另核两份固定插件源码hash及回调/发送边界。19项守卫复用刚完成的e主线执行，因共享源码未变而未机械重跑；主线未另启动fixture。证明 `local/migration72/evidence/d02-r2-d01b5f-main-review.json`。driver收口实际等候EXIT/STOP，但最终passed断言未另直接断言STOP；本轮两raw实际均有STOP，g新driver须显式约束，不能据tool passed单独泛化终态闭合。

## 两次驱动收口失败也保留

本批共启动4个自有合成过程，前两例没有标作完整通过：它们均已观测到marker入口/返回，应用报告phase40/marker3且独立OS最终exit0；但新驱动在两个gate完成后仍轮询fixture内存。退出时地址空间先卸载而进程句柄随后signaled，读取失败使驱动过早断开，最终PUB/日志闭合记录不完整。

首修正只在读失败后已见OS signaled时继续收口，第二例证明这个窗口更早，故修正过窄。最终没有堆叠重试：两个gate交付完便停止不再需要的进度内存读取，仅等待OS/PUB/log终态。之后有异常和无异常对照都完整通过。两次失败raw、日志及各自执行源码均原样保留在`d02-r2-synthetic-sw-no-exceptions{,-v2}.json`及`analysis/d02-r2-synthetic-sw-{first,second}-executed.py`。

这条已复现的**新合成驱动**收口错误不能用来解释游戏e：游戏观察器运行等待中没有该fixture进度读取，而且e的最终EXIT/STOP均收到；e未观察到的是软件点/最终异常的高层发布。

## 80000003到脚本事件的固定来源

[固定TitanEngine DebugLoop](https://github.com/x64dbg/TitanEngine/blob/ec7a8b9352c47eb0f8cc63f033d36f705d658743/TitanEngine/TitanEngine.Debugger.DebugLoop.cpp)对异常事件先设NOT_HANDLED。80000003匹配活动软件点并成功恢复原字节后，才设DBG_CONTINUE并调用断点回调；未匹配或恢复失败可以转通用unhandled路径。本机尚未取得游戏故障时该低层表或恢复调用结果，不能据源码直接选择其中一支为原因。

[固定x64dbg debugger.cpp](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/debugger.cpp)的普通回调还要查点表/条件，再发CB_BREAKPOINT；表中不存在可只发pause并等待。通用cbException的first-chance、DoNotBreak、skip和线程命名条件可能在CB_EXCEPTION之前返回。因此“未观察到高层发布”不应改写为“内核事件已产生但被PUB丢失”。

[Automate固定plugin.cpp](https://github.com/dariushoule/x64dbg-automate/blob/v0.8.1-ghost_fungus/src/plugin.cpp)将CB_BREAKPOINT/CB_EXCEPTION等转换为PUB消息，未注册原始CB_DEBUGEVENT出口。[发送实现](https://github.com/dariushoule/x64dbg-automate/blob/v0.8.1-ghost_fungus/src/xauto_server.h)是加锁后的PUB发送，没有逐事件接收确认。PUB线程存活和日志eviction0只限定已观测通道状态，不能证明每个更上游事件都曾抵达此处。

通过既有只读BridgeSetting API取得的当前配置为`Exceptions.IgnoreRange=00000000-00000000:first:log:debuggee`、VerboseExceptionLogging=1、MaxSkipExceptionCount=0、ThreadNameSet=0，与专门忽略80000003的配置不符；没有修改任何设置。该读取不回溯游戏退出时的内部filter vector/skip状态。此记录与最终无target审计在`d02-r2-synthetic-sw-final-audit.json`。

## 当前边界和R2收敛方向

最终同一管理员会话无游戏/fixture，API点表为空。游戏持久库仍为e的4个自有点，hash未被合成实验改变；fixture库另有3个已核自有终止点。下一次目标load必须使用各自ledger核归属，不能混用或按前缀批删。

现有证据支持优先改变**主模块软件字节改写**这一观察方式，保留已经恢复的GlobalDispatch真实ABI/三支来源。可审的下一最小真实实验是：经主线协调后只一次新游戏，系统与EP先核干净，再用一个已核硬件执行点观察共同正文入口，命中后仍仅一个返回点；保持主模块原字节、三诊断点、逐停点双API与明确DR归属，120秒上限。它直接检验软件字节/恢复分支候选并争取自然HTTP正文与返回；没有命中或再退出都保存边界，不原样重试，不改检查/返回值。

另一个更低层的零主模块软件点方案是外层网络API观察，但当前三支WWW/System.Net/HoYoSDK不保证统一经过WinHTTP，单挂某个WinHTTP入口会留下已知来源缺口；只有在已知实际分支/模块后才适合选择该点。先取得真实自然输入，再把它接到已验Java/native RegionList/当前区合同、实际路由及信任材料加载前置，避免继续无限扩大通道研究。任何新游戏load在本卡均未执行。

```powershell
# 仅自有fixture；当前持久库有前轮3诊断点，必须传其对应raw ledger。
& local/migration72/debug-venv/Scripts/python.exe tools/observe_client72_software_fixture.py `
  --mode exceptions --prior local/migration72/evidence/d02-r2-synthetic-sw-no-exceptions-v3.json `
  --output local/migration72/evidence/d02-r2-synthetic-sw-next.json
```
