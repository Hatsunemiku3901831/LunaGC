# D02-R2 / D-01b5：有界启动观察与调试寄存器复现

2026-11-06。D-01b5a完成两次原始国服7.2启动观察及三个自有合成进程的调试通道实验，**未取得自然非空Marshal返回、SDK/HTTP返回或登录入场通过**。第二次游戏观察的全部停点DR干净、继续状态明确，仍以`80000003`退出。Windows独立错误报告把两次故障均定位到主模块RVA`52FBF0`，恰是字符串factory观察入口；故障与观察点同址已经有证据，完整因果和最后调用栈仍未知。

D-01b5b随后完成一次零主模块观察点对照：同一新目标经过两段合计118.641秒观察后仍存活，已核预算pause并保留原进程。中间因新线程命名异常停住审计216.656秒，不称连续120秒。最新状态见本文末节；不能再沿D-01b5a的“当前无目标”状态load。

共享摘要为[startup-observer-observations.json](../protocol/cn-7.2/startup-observer-observations.json)。所有真实路径、PID/TID、异常参数、字符串、栈、Windows事件及完整WER报告保留在忽略的`local/migration72/`。没有改客户端文件、安装补丁、启动服务或数据库，没有主动构造客户端调用、修改校验或强制返回结果。

## 观察合同与实际边界

[observe_client72_startup.py](../tools/observe_client72_startup.py)只连接实时发现且核归属的既有管理员Automate会话，不启动新调试器、不使用computer-use。重新解析`tools/game_path.ps1`，核固定EXE、两份metadata、SDK与Astrolabe五个SHA-256；调试器和插件来源沿用[D-01b4](CLIENT_72_DEBUG_CHANNEL.md)。本批另核TitanEngine.dll与x64bridge.dll同固定发布ZIP逐字节一致。

每次新load必须出现新的CREATE_PROCESS及SYSTEMBREAKPOINT，匹配PID/路径、debugging=true/running=false，并能打开独立Windows进程句柄。load/pause返回值不算存活证明。每个load的日志游标从0开始，连续保存PUB事件，订阅线程是否存活、日志eviction与OS退出码分别记录。`is_running=true`在debugging=false时也实测出现，不能单独用它判定目标还活着。

计划观察上限120秒；到预算仍运行时才请求显式pause，并核PAUSE_DEBUG、PID及OS存活。两次游戏都提前退出，未执行预算pause。应用首次`C0000005/E06D7363`必须逐事件匹配`ex.code/addr/firstchance`再`con 1`加普通run；系统、已核TLS/EP及自有观察点用裸`con`加run。未知/二次异常、事件缺失、两寄存器API不一致或不属于已核旧点的DR变化停止恢复。

两轮设置相同的五个主模块软件点：Marshal、factory、MakeInitialUrl、Browser.LoadURL、SDK.RSAEncrypt；三个终止点为NtTerminateProcess、RtlExitUserProcess、TerminateProcess。主模块每个入口按实际ASLR重定位并核固定文件32字节。只有明确持久化来源、名字及当前地址均匹配的旧点才清理；不按`cn72_`前缀批量清点。自然返回关联按返回地址、RSP和事件TID三者判断；本批这些入口全部未获得BREAKPOINT命中，因此没有实际自然返回验收。

## 两次游戏观察

| 项目 | 第一轮：旧DR恢复后续接 | 第二轮：干净DR与逐停点双API |
| --- | --- | --- |
| 新load/ready | CREATE_PROCESS、SYSTEMBREAKPOINT、路径/PID/OS均匹配 | 同左 |
| load至第一次run | 552.266秒，因前置拒绝与同目标排查暂停 | 0.203秒 |
| 首次run至EXIT | 13.156秒 | 12.625秒 |
| 观察器续接/完整运行耗时 | 15.531秒 | 16.188秒 |
| 应用异常 | 37次匹配首次AV，无C++异常 | 同左 |
| 普通停止 | 系统停点及主模块EP | 同左 |
| 主模块/终止API命中 | 0 / 0 | 0 / 0 |
| DR观察 | 系统点清为0；EP及后38停点又读到旧factory DR0/DR7 | 39个停点六DR均0，dump/eval逐值一致 |
| OS / PUB / 日志退出码 | 三方`80000004`，EXIT和STOP齐全 | 三方`80000003`，EXIT和STOP齐全 |
| 日志丢失 / PUB线程 | eviction=0 / 存活 | 同左 |
| 文件及结束状态 | 五文件hash保持、无相关目标 | 同左 |

第一轮不是干净的单变量对照。首次load正确拒绝从调试器数据库恢复的旧`cn72_d01b3_factory_hw`；依据旧`d01b3-hardware.json`的名称、slot0、模块与RVA及固定代码hash核归属后，才删除该点并清已核DR。前置异常退出关闭了OS句柄，续接重新核同PID、路径、基址、CIP/RSP及事件TID后重开句柄，**存在观察句柄间隙**。没有另load替换这个目标。

清理时寄存器dump的EFLAGS由`246`变`244`，仅bit1不同。工具最初要求所有非DR字段逐位相等，因此再次停止；尝试恢复原EFLAGS后读回仍是`244`。后续合成目标两API同样复现这一保留位差异，未观察到算术/业务flags改变，不把它称作客户端故障。相关失败及恢复尝试均保留。

第一版只在ready核DR，虽然保存了之后每停点的完整dump，却没有拦住EP后旧DR再现。该缺口已经在工具中补为逐停点双API及DR守卫，但第一轮结果不追溯标作干净。第二轮没有旧硬件点或清理动作，全部停点DR为0，仍失败，说明裸con及DR清理并不足以解决启动。

## 独立Windows错误报告

读取Application日志1000/1001，限定本轮时间范围及目标EXE名称。两份Application Error按PID及目标路径与对应新CREATE_PROCESS关联，WER按报告ID和异常签名交叉核对，结果均为`YuanShen.exe+52FBF0`；异常码分别为`80000004`、`80000003`，同OS/PUB/log一致。

两份Report.wer已复制到忽略证据目录并固定hash；其中列出的临时minidump与内部metadata文件读取时已不存在。没有终止栈或最后异常的PUB样本，不声称已经取得完整factory调用、参数、分配或返回；故障位置与研究断点同址也不能直接改写为反调试/保护归因。原始来源为`d02-r2-application-events{,-xml}.json`、`d02-r2-wer-preservation.json`及`d02-r2-wer-*-Report.wer`。

两轮最后15个DLL加载事件相同，末端是webio/winnsi；最后调试器暂停均是既有NTDLL首次AV。旧D-01b3零主模块点控制轮之后还到达音频及更多网络库，并有两次C++异常。这提供进一步单变量对照的依据，旧60秒控制观察不替代本次运行。

## 合成复现与寄存器API核对

只复用D-01b4固定hash的`channel_fixture.exe`，未新增目标源码或构建。三个自有进程最终均应用phase40、marker累加3、AV/C++处理各1次，OS/PUB/log退出0。中途两次守卫失败保持原始记录，再核同一个自有目标继续，未将失败删去归并为全程成功。

1. **系统点现设现删**：设唯一marker硬件点、按地址删除、显式清DR，随后EP、AV、C++和显式pause均DR0；12份同停点dump/eval完全相同。设点时两API同样显示EFLAGS`246→244`。
2. **系统点新设并保留**：在系统点两API均读到marker，但首run到同事件TID的EP后，两API均读0，硬件表仍有enabled marker。守卫停止；同目标续接正常退出，保存该点到调试器数据库。
3. **下一load恢复后在系统点删除**：系统点恢复marker；API删除及显式清理后表空、两API全0。到EP，两API又读到marker/DR7=`401`，硬件表仍空，守卫立即停止。只清这个已证明属于本实验的EP残留后，后续AV、C++、pause均干净并正常退出0。

这精确复现了“系统点清理后EP又出现旧DR”的通道问题，不能归咎于两个Python API读不同缓存，也不证明游戏具体退出原因。最后一例证明在该合成目标EP后清理能保持后续已观察点稳定；不是所有线程/未来时刻的无限保证。

固定版本源码核对：Automate的[寄存器读取](https://github.com/dariushoule/x64dbg-automate/blob/v0.8.1-ghost_fungus/src/xauto_cmd.cpp)调用DbgGetRegDumpEx；x64dbg的[完整dump](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/_exports.cpp)和[表达式](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/value.cpp)分别到GetFullContextDataEx和GetContextDataEx。其固定TitanEngine子模块[共同读取实现](https://github.com/x64dbg/TitanEngine/blob/ec7a8b9352c47eb0f8cc63f033d36f705d658743/TitanEngine/Global.Engine.Context.cpp)都从GetThreadContext取得DR/flags，区别在AVX分支；实测也一致。

[按地址删除HW](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/commands/cmd-breakpoint-control.cpp)删除表项并请求TitanEngine清对应slot。[底层删除](https://github.com/x64dbg/TitanEngine/blob/ec7a8b9352c47eb0f8cc63f033d36f705d658743/TitanEngine/TitanEngine.Breakpoints.cpp)遍历登记线程并关闭该slot登记。系统点至入口的完整上下文恢复机制尚未直接跟踪，NtContinue/加载器保存context只是下一来源候选，不据这些静态片段宣称定位根因。

## D-01b5a的测试、收口及下一实验

8项新离线守卫与原8项调试通道守卫合计16项实际通过，失败/错误/跳过0，见`logs/d02-r2-startup-guards-v2.log`。覆盖异常身份/二次/未知trap、停止点归属、TLS地址来源、返回线程和栈关联、寄存器API/DR变化、日志解析及脱敏边界。这是研究工具验证，不是完整研究选集、Java或游戏验收。

```powershell
python -m unittest tools.test_client72_startup_observer tools.test_debug_channel72 -v
# 只在当前原始观察计划获准、实时归属检查满足后执行；不循环自动重试。
& local/migration72/debug-venv/Scripts/python.exe tools/observe_client72_startup.py `
  --output local/migration72/evidence/d02-r2-next.json --seconds 120
```

D-01b5a最终只读核查同一管理员会话无debuggee，相关游戏/fixture进程0，API普通/HW表0；这不保证以后新load不会从数据库恢复点。原始游戏数据库可能保存八个本研究软件点，后续必须按本次raw ledger核归属，不能直接再load/run。`d02-r2-final-readonly.json`保存当时现场和原配置hash。仅研究工具、测试、摘要、文档变更，无运行构建/部署要求。

最小后继为主线审核后的一次零主模块软件点对照：保留同三个终止API点与连续异常/OS/PUB/log，按新系统→EP与逐停点DR合同观察相同原始客户端120秒；比较是否越过factory故障阶段及WER位置。它只改变五个主模块软件观察点的存在，不改客户端检查、不构造函数调用、不放开补丁或连接尚未部署服务。若能自然继续，再为具体自然暂停/返回制定不干扰的观察方式；若仍退出，继续按实际首失败来源取证。D02-R2、D-01及D-02保持未验收。

## D-01b5b：零主模块软件点的有界对照

主线审核D-01b5a并提交`cc1f802`后，依据两份WER的factory同址故障、干净DR仍退出及旧零主点控制的更远进度，批准一次对照。计划固定在忽略`analysis/d02-r2-d01b5b-plan.json`。唯一预定观察变量是五个主模块软件点从5降到0；保留相同三个终止API、固定五客户端输入与调试器来源、系统→EP及逐停点双API/DR、单次异常交付和120秒预算。工具的`--main-observations none`仍核五处代码，但不安装这些点；共享摘要显式标明自然入口未被观察，不能将空入口列表解释为函数没有执行。

新load前实时核无target/相关游戏或fixture；load后逐名字、当前重定位地址及上一轮raw ledger清除八个已证持久点，再仅装三个终止点。新CREATE_PROCESS/SYSTEMBREAKPOINT、路径/PID、OS句柄与暂停状态完整匹配，没有新硬件观察点、补丁或目标函数调用。

| 阶段 | 实际结果 |
| --- | --- |
| 第一段 | 首run至新停止18.516秒；37次首次AV后出现首次`406D1388`，DLL已70个；白名单外事件立即保存，未交付，目标仍活着暂停 |
| 只读审计 | 同PID/待决code、addr、firstchance保持；三ULONG_PTR结构、可读NUL名称和当前本进程目标线程成立，六DR均0；审计暂停216.656秒 |
| 同目标续接 | 重新核原目标、CIP/RSP/TID、路径/OS句柄及该待决事件，重新装同三个终止点；已核线程命名异常原样con1，然后两次首次C++异常同样交付；本段预算100秒 |
| 预算收口 | 第二段首run至PAUSE_DEBUG为100.125秒；总观察区间118.641秒；显式pause后新PAUSE_DEBUG、same PID、OS alive/not-signaled、debugging=true/running=false全匹配 |
| 最终范围 | 累计88个DLL、37 AV+1线程命名+2 C++；所有已观察停点dump/eval一致、六DR0；PUB线程存活、logEvicted=0；无EXIT/STOP/退出日志及终止API命中 |

`406D1388`不是仅按号码放行。实际参数符合Microsoft公布的[x64线程命名异常结构](https://learn.microsoft.com/en-us/visualstudio/debugger/how-to-set-a-thread-name-in-native-code)：pack(8)结构在x64为24字节、三个ULONG_PTR，type=`1000`，最后一个64位量的低32位为目标TID、高32位flags为0；名称指针有界可读并含NUL，目标TID通过当前OS线程快照确认属于本进程。名称是音频相关枚举线程，不是SDK认证或协议字段。工具新分支还保留首异常和ex表达式匹配，畸形结构、无可读名称或无对应线程继续拒绝；没有修改异常参数、检查或返回值。

这次续接沿用同一个客户端，没有第二次load。审计结束后重开独立OS句柄，存在明确的句柄/观察间隙；第一段与第二段的累计事件保持同一进程来源，但不能把中间暂停算成持续运行。原始报告中的旧字段名`osHandleGapAfterRejectedPreflight`在本次表示这次审计续接间隙；工具现改为`osHandleGapBeforeContinuation`并另记续接种类，不修改已保存raw。

收口后仅删除本轮三个自有终止点，保留目标在明确的观察器预算pause。随后独立只读核同PID、CIP/RSP和OS路径/存活，普通/HW表均0、六DR0；五处live代码窗口全部与固定EXE原字节相同，五文件hash及根配置保持。最新现场为`evidence/d02-r2-d01b5b-active-session.json`，不是旧无目标索引。后续若恢复该目标，必须重新核归属，并按**自有pause使用裸con加普通run**；不能使用应用待决异常的con1路线，也不能因为旧退出记录而另load。

结果支持“这五个主模块软件观察点影响此前启动”的限定判断：撤点后本次越过原先56 DLL/factory诊断故障阶段并保持存活。由于线程命名停点另有审计暂停，并且没有直接观察其自然调用或返回，不把本次称作完全无干扰的时间因果实验，更不推导SDK/HTTP/Marshal完整运行、登录入场或保护机制归因。R2/D-02仍未验收。

本批9项启动观察器守卫加原8项通道守卫，实际17项通过，失败/错误/跳过0。新增线程命名结构拒绝与零主点摘要边界；前16项记录保留。日志为`d02-r2-d01b5b-{guards,thread-name-guards,final-guards}.log`。原始两段、只读审计、计划与现场均使用`d02-r2-d01b5b-*`前缀；共享`zeroMainModuleContrast`包含这些输入hash和精确时间，不含真实进程标识或内存。

```powershell
# 本次已执行记录；当前仍有目标，不可直接重跑load。
& local/migration72/debug-venv/Scripts/python.exe tools/observe_client72_startup.py `
  --main-observations none --prior-observation local/migration72/evidence/d02-r2-startup-run2.json `
  --output local/migration72/evidence/d02-r2-d01b5b-run1.json --seconds 120
python -m unittest tools.test_client72_startup_observer tools.test_debug_channel72 -v
```

下一步从当前已证明可存活的零主点状态制定自然调用观察，不重复已有Marshal离线转换/CRT模型研究。现有暂停目标可继续只读核查；进一步执行按新观察目标安排，下一次客户端load仍由主线协调。

## D-01b5c：保留目标的阶段快照与线程选择问题

本批没有新load、run、pause、断点或寄存器/客户端内存写入。先从D-01b5b同一自有预算pause读取OS、窗口元数据、日志、已加载模块、全局槽位和有限栈；只用既有Automate脚本API，未使用computer-use。窗口标题为“原神”，当时有237线程；7条TCP中5条Bound、2条远端443的CloseWait，无UDP。没有URL/请求体来源，不据此判断认证完成或等待特定服务。

原预算pause选中线程在`ntdll.NtWaitForSingleObject+14`，有限栈内有gmesdk候选返回地址。为区分音频等待与主循环，观察工具切换到最初CREATE_PROCESS的主线程，TEB的ClientId独立核为本进程；其CIP为`ntdll.NtDelayExecution+14`，栈内候选地址可关联到原文件`1416BD4C0`的`PlayerUpdateTime`字符串与`141706410`的帧率/时钟计算和SleepEx式等待路径。依据PE exception目录的函数边界重新解码，未采用最初任意地址起始的错位反汇编。栈候选不等于完整unwind调用栈，单张帧等待快照不能证明具体登录等待条件或排除其他初始化问题。

既有88个DLL事件中未出现AccountPlatNative或Astrolabe；这只是事件库存的缺项，不是对手动映射或全部模块状态的无限保证。当前output_log为空；旧SDK日志时间早于该进程，不能归入本轮。关键全局指针和若干UTF8/初始化槽位非零，已知19580/19588槽位仍零；阶段数组为16个零但记录开关也是0，不能用此数组推断“一个阶段也没执行”。这些证据支持继续查实际调用来源，未支持直接挂WinHttpOpenRequest：固定主EXE仅导入WinHTTP代理配置API，实际请求API来自尚未在事件库存中出现的Astrolabe模块。

**线程选择未恢复必须保留为工具问题。** 本批第一次栈工具的raw已写`selectionRestored=false`；此前进度总结称已恢复是错误总结，已向主线纠正。后续独立retention检查正确拒绝CIP/RSP与原预算pause不一致。只读比较证明当前TEB线程、CIP/RSP与先前主线程快照完全相同；双寄存器API一致，六DR为0，普通/HW表为空，OS同目标仍存活且debugging=true/running=false。日志与原快照完整同前缀，仅增加一条线程切换日志，未出现后续执行/退出记录。

确认原选中线程的OS句柄仍属于同一目标后，工具仅请求恢复该选中线程。`switch_thread`返回true，但TEB/CIP/RSP完全未变且无新增切换日志。[固定版本cbDebugSwitchthread](https://github.com/x64dbg/x64dbg/blob/2026.05.27/src/dbg/commands/cmd-thread-control.cpp)比较的是`GetDebugData()->dwThreadId`与请求线程；请求原事件线程时跳过更新`hActiveThread`，仍返回true。这与本机“已切到主线程，再切回原事件线程无效”相符。没有修改调试器内存、绕判断、恢复执行或再load来解决它。

当前准确状态为：**原目标保持预算暂停，但调试器选中上下文仍为主线程；原事件仍属D-01b5b自有pause。** 最新只读证据是`d02-r2-d01b5c-retention-mismatch.json`，恢复尝试为`d02-r2-d01b5c-restore-selection.json`；不能直接拿旧active-session的CIP/RSP作为当前选中线程。恢复执行前需同时核当前选中线程、原事件身份和OS归属；普通helper返回值不能代替核对。初次retention拒绝、序列化失败日志、旧`selectionRestored=false`及返回true但无效的恢复尝试均保留。

本批独立完成的Java当前区输出→原生消费前置见[HTTP_DISPATCH](CLIENT_72_HTTP_DISPATCH.md)：固定JAR六组实际响应、八组原生返回、三组拒绝，双跑一致及22项守卫通过。它没有推动暂停目标执行，也不替代自然SDK/HTTP/Marshal调用和返回。下一步先核RegionList的真实parse/caller/class/codec身份及路由信任闭包；新的同目标自然观察需先指定合适阶段与单个必要点，不能再同时装五个主模块点或重复factory软件点。

## D-01b5e：用户要求重启后的单GlobalDispatch点观察

用户明确要求“重启游戏，再进行观察”，覆盖此前保留旧pause的决定。D-01b5d的[RegionList来源与三HTTP支](CLIENT_72_REGION_LIST.md)先冻结审核后，本次只选择其共同正文consumer `1512250E0`的一个主模块软件入口点，保留原三个系统终止API诊断点；不重复原五点/factory，不新增硬件点，不装补丁或强造调用。目标ABI为RCX管理器、RDX托管Base64正文string、R8 completion Action。计划只读取header及有界UTF16；若自然命中，先移除入口点，再仅在已核caller `14F894D43`设置一个临时返回点，以同TID、RSP+8关联，void返回不把RAX当string。

关闭旧target前，执行策略参数遗漏、旧上下文相等/双API守卫和可见窗口唯一性先后导致准备脚本停止，所有首失败保留，均发生在close/stop之前。新的只读记录一度出现CIP/RSP和TEB均0，故不能把这时的DR零当成有效CPU寄存器读取；未据此恢复未知线程或异常。关闭已核目标与续接旧栈是不同操作：最终依据新OS句柄的完整路径/PID、固定EXE hash、调试器目标身份及无其他相关游戏核归属，选中上下文可用性单独记录。此时没有唯一可见标题游戏窗，未发送WM_CLOSE；仅执行一次既有debugger `stop`。旧进程OS/PUB/log均exit1，EXIT+STOP齐全、相关游戏0，明确记录为**调试器终止，不是应用正常退出**。

新load重新从日志游标0开始，并得到新CREATE_PROCESS/SYSTEMBREAKPOINT、匹配路径/PID、debugging=true/running=false及独立OS句柄。系统→EP及每停点双API/DR合同保持；匹配首次应用异常con1，自有系统/EP使用裸con。以下为实际结果：

| 项目 | 结果 |
| --- | --- |
| load事件至首次run | 0.343秒 |
| 首次run至EXIT | 37.766秒；观察器总耗时42.343秒，未达到120秒预算 |
| 调试停止 | 39个：系统、EP及37次首次AV；两API一致，所见六DR始终0 |
| 加载模块 | 90条DLL事件，末端包括网络/音频库及windows.ui |
| 自然入口/返回/终止点命中 | 0 / 0 / 0 |
| OS / PUB / 日志 | 均为`80000003`，EXIT与STOP齐全，eviction0，PUB订阅线程存活 |
| 文件/配置 | 五原始客户端文件及根配置hash保持 |

Windows Application1000按本次新PID和路径匹配，异常`80000003`、模块YuanShen.exe、故障RVA **`112250E0`**，恰为唯一主模块软件观察入口。Application1001及归档Report.wer的IntegratorReportIdentifier相同，code/offset亦一致；已保存原Report.wer和hash。1001列出的9个附件在读取时均不存在，未取得minidump。没有对应的最终异常/BREAKPOINT PUB或参数/调用栈，不能据WER故障地址宣称观察到了业务调用、参数或返回。

与旧结果并列后，证据是：五点轮两次WER在factory RVA52FBF0；撤五点后零主点观察118.641秒并预算pause；这次一个不同主点后，WER移到该点RVA112250E0。它加强“主模块软件观察改变启动结果”的候选，但不识别具体保护/线程/异常机制，也不是完整随机因果实验。39个已观测停点无DR残留、无日志eviction且PUB线程存活，分别排除了这些停点的已知DR问题和明显日志/订阅线程失效；它们不能证明最终事件一定曾由调试器或插件产生。

收口只读复核同一管理员会话无target，相关游戏0，当前API四种点表均空。调试器持久库另行只读复制、解压核到**4个本轮自有软件点**：GlobalDispatch及三终止点，名字/模块/RVA按本轮raw ledger逐个匹配；无未知点。未改库或据API表空宣称库中也无点。后续游戏load前必须按该ledger清理/复核，不能原样重试本次参数；本批没有再次load游戏。

工具新增`global-dispatch`模式，并保证该模式与原五点集合不相交；void返回不解引用RAX。新增两项回归与原17项合计19项通过，失败/错误/跳过0。实际运行版本按raw的`sourceSHA256`原样保存在`analysis/d02-r2-d01b5e-observer-executed.py`；运行后为可测试的单点选择/void返回提取了同语义函数，并使旧点归属读取尊重上一轮模式，未将新源码hash追写进原始运行。共享摘要分别保留executed/current源码hash。

原始运行`d02-r2-d01b5e-run1.json`、旧目标最终关闭`d02-r2-d01b5e-stop-retained-v5.json`、无目标/持久库审计`d02-r2-d01b5e-final-audit.json`及同前缀Application/WER/附件读取记录均在忽略证据目录。共享`singleGlobalDispatchContrast`只追加本阶段，旧b/c/d字段保留。没有运行代码/配置/Proto/补丁变更，不需部署。

主线已审核四文件及实际执行源码区别，独立19项通过；逐项核11原始来源hash、39停点的双API/六DR、OS/PUB/log终态、WER新PID/路径/报告ID及单点RVA、持久库四点归属，原共享其它章节逐字节保持。证明为 `local/migration72/evidence/d02-r2-d01b5e-main-review.json`，日志 `local/migration72/logs/d02-r2-d01b5e-main-guards.log`。不把当前helper的running=true读法当有target，也不把本卡完成当自然SDK/HTTP已完成。

下一项先用现有自有合成目标区分通道和目标特有行为。D-01b4覆盖普通系统/EP、硬件点、pause和AV/C++交付，尚未覆盖“先con1交付应用异常，再自然命中主EXE软件点，收到普通BP并裸con到返回”的完整序列。最小对照使用同固定fixture、同三终止诊断点和同一停止分类/返回关联合同，比较有/无前序AV+C++时的marker软件入口/返回/正常exit0。若合成也丢BP或异常退出，优先修通道；若合成全过，只能缩小通用continue状态解释，仍需对游戏特有线程/异常路由与插件事件来源取证，不能据此直接关闭检查或宣布根因。
