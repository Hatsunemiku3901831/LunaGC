# 国服7.2动态调试与启动观察

2026-11-05。用户明确允许按实际需要动态调试；随后明确要求**不要使用computer-use（当前有bug）**。后续使用脚本API，不再调用界面自动化。动态启动观察不是D-02登录/入场验收。

## 工具与访问方式

- [x64dbg官方固定发布2026.05.27](https://github.com/x64dbg/x64dbg/releases/tag/2026.05.27)，ZIP SHA-256 d41966dfc5b435a372798245300ca0ab7bb8e48bdbf48512c6fb20fcca427697。
- [Automate插件0.8.1-ghost_fungus](https://github.com/dariushoule/x64dbg-automate/releases/tag/v0.8.1-ghost_fungus)，64位ZIP SHA-256 942cd81d2dd02141deacb848846b8a8a26b5f4de1731142a4df6f44581b0597b。Python客户端0.9.2协议同为ghost_fungus，安装在独立忽略venv，不改全局Python。
- 来源/哈希/安装报告：local/migration72/evidence/debugger-{release,install}.json、debugger-automate-{release,install}.json、debugger-python-install.json。依赖留local/runtime与local/migration72/debug-venv。
- 用户手动完成目标要求的管理员提权与插件热加载；当前脚本接口已实际连接。插件默认local模式，两个监听均为127.0.0.1且归属当前调试器，见debugger-local-ports.json；未启用远程模式。
- [会话API](https://dariushoule.github.io/x64dbg-automate-pyclient/api/session-control/)支持发现/连接已有调试会话。`attach_session`连接调试器，`detach_session`只断开API连接，不让debuggee脱离或继续；不得把它与debuggee的detach命令混同。不要调用start_session新建第二个目标。

## D-01b1实际范围

固定输入重新audit；真实进程在系统断点暂停。读取实际模块基址并计算VA偏移，核对5处各32字节：1411267B0、14146ADA0、1408BAD50、14A447E00、15121FF70，全部与固定EXE相同。没有沿用旧ASLR地址。

经原有系统DLL/目标TLS断点、PE入口，继续到目标1411267B0函数注册入口并暂停。真实Global指针槽14586B600仍为0；不代表SDK初始化完成。观察到Ec2b所用native RNG已初始化：5000字节状态、index624、maskFFFFFFFF、624个状态字符合以103为种子的独立MT32递推，其余624字为零。

找到原生初始化1408AF2A0；离线实际执行后5000字节与该真实样本逐字节一致，SHA-256 65357cbadcb1fa79ad0f588baf140f0378176037228b8e5f2d75cb4eb07b823f。该初始化并非由手工填充值替代。共享摘要[startup-observations.json](../protocol/cn-7.2/startup-observations.json)只含脱敏地址/哈希/范围，不含机器PID/路径/端口或原始内存。

已完成的是一次真实启动基线、5处代码比对及RNG状态交叉核对。尚未通过真实SDK、HTTP/认证、密钥调用、游戏网络、登录、选人或入场；D-01b与D-02仍未完成。

## 初始帧密钥探索与下一步

静态写入链包含151204BE4、151212942、151229526到GLOBAL+19588。非空输入进入14146ADA0→1411268D0：检查Ec2b格式后调用145744EB8指针。该指针由1411267B0初始化为1408BAEE0，继续1408BAD50。分区字段业务名仍待来源交叉验证，不硬套旧名称。

第一次离线探索未初始化上述RNG，陷入取样路径，20M/200M预算均未到返回；增加预算没有解决问题。真实状态指出缺失的初始化，随后执行1408AF2A0后，原生Ec2b使用仓库dispatchSeed.bin单组返回1，4096字节输出与dispatchKey.bin逐字节相同，SHA-256 20f0d259406a01c02c55bfdc57f69c90c1257222d721a141ccbe55ccfaa77eb0。这只是单组探索，不是正式兼容验收，也不是运行时使用旧客户端。

下一步正式化Ec2b工具，补足非空/缺失/错误头/边界与不同输入、重复产物、独立key断言；再接分区来源→实际key槽→Token帧。不能只把动态内存复制进工具充当原生初始化已恢复。

## 本机复现和保持状态

实时session/目标PID、端口、ASLR只在忽略报告中。先发现当前会话并通过API核对debugger PID、debuggee PID/模块、is_debugging/is_running，再读内存或设置断点；报告不能替代实时归属验证。

当前本机脚本（探索，不作为通用工具发布）：

- analysis/read_dynamic_startup.py、dynamic_phase01.py、dynamic_advance.py：连接现有会话，代码核对、分段继续并保存阶段报告。
- analysis/read_dynamic_rng.py：在已确认1411267B0暂停点读取RNG状态。
- analysis/probe_initial_key_native_init.py：原生初始化与单组Ec2b交叉对照。
- evidence/dynamic-startup-read.json、dynamic-phase-01至12.json、dynamic-ec2b-rng-state.{json,bin}、initial-key-native-init-exploration.json；logs/dynamic-phase-*.json。

这些路径均相对local/migration72。当前目标保留在调试暂停点，不能把任务active误解为游戏正在运行。已设置两个命名软件断点和Global/RNG mask写入观察点，后续须核对现有断点再继续；不得强杀未知进程或重复启动客户端。

启动脚本曾因PowerShell字符串数组拼接失去换行，已修正；提权重启没有继续原-cf脚本，界面输入也未生效。现已用Automate API成功读到内存和日志，不再依赖旧脚本或computer-use。用户无需再次为同一已授权研究确认动态调试；确需Windows安全对话框时仍由用户手动处理。


## A-04d5u 原生Ec2b独立验收

`client72_ec2b`首先实际执行1411267B0注册与1408AF2A0 RNG初始化，逐字节对照独立624字递推和上述真实5000字节hash，再执行1411268D0→1408BAEE0→1408BAD50→1408AF320。未替换cipher或成功结果。managed入口14146ADA0还实际读取数组长度、调用native decoder、经selector5取byte class、分配并复制4096字节结果；engine对象与managed分配是明确存储模型。

`trace_client72_ec2b`固定仓库dispatchSeed.bin（2076字节，SHA256 2258b87182df6e8e679c15a86184ddaf8d4b5eda24cd81bf12e85bb8a9e00637）与dispatchKey.bin（4096字节，上文hash）作独立输入/参考，不启动旧客户端。16组覆盖原始pair、不同堆地址、缓存后重复、key/body单字节扰动、错误魔数/内部key长度/body长度、null、零/负声明长度、缺函数指针、managed成功/拒绝/null。成功pair五组结果逐字节相同；扰动组输出改变；raw拒绝保持A5输出哨兵，managed拒绝返回null且不分配。

一个明确边界：raw decoder只对外部length做正值判断；声明length=1但**底层仍给完整2076字节**时会成功。本项不把该测试描述为实际截断managed数组通过，也不擅自增加运行防御或改变客户端。物理截断处理、分区来源/schema语义及连续帧仍待验证。

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
python tools/trace_client72_ec2b.py --client $env:GAME_PATH --output local/migration72/evidence/ec2b-trace.json --summary-output protocol/cn-7.2/ec2b-observations.json
python -m unittest tools.test_client72_ec2b
```

16原生样本、228研究回归、二次trace/摘要一致，研究校验0/ready1。只有离线工具/测试/摘要/文档变化，不部署运行服务。

实时观察补充：在同一管理员调试会话，从1411267B0单步到函数ret1411268C1，确认145744EB8由0变为按实际基址重定位的1408BAEE0；暂停在ret前，未声称整个SDK初始化。报告evidence/dynamic-ec2b-registration.json。之后继续使用Automate接口，遵守用户禁止computer-use要求。

## 2026-11-06后续启动状态

从注册函数ret继续后出现NTDLL中的首次访问异常，读址为主模块base+C0000002；原始指令/寄存器/栈已保存在忽略报告dynamic-startup-exception.json。通过Automate的`go(pass_exceptions=True)`将异常原样交给应用，未吞异常、未修改指令/返回值或关闭校验。应用继续加载系统库及mhypbase.dll，随后停在后者TLS回调（模块相对偏移5F58E4）；Global指针仍0。此阶段不认证整个SDK、反作弊状态或登录能力。

实时报告dynamic-phase-13至15.json和对应日志；最新phase15确认is_debugging=true、is_running=false。后续必须重新查询会话及实际基址，不能继续沿用1411267B0/1411268C1作为当前暂停点。第一分区非空Ec2b来源到Token帧已由A-04d5v离线验证，详见[网络研究](CLIENT_72_NETWORK.md#a-04d5v-分区密钥来源到token完整帧)，与本段真实启动范围分开记录。
## D-01b2真实全局、缓存ABI和调试器复现

后续实际运行建立Global存储；两次快照中GLOBAL+24B8与+10770均指向同一UTF-8对象，其class虚槽11/19分别匹配类型463的159C474F0/159C475D0，槽34匹配类型443的159B640B0。另核对MakeInitialUrl、Browser.LoadURL、SDK.RSAEncrypt和Ec2b入口各32字节，全部与固定EXE相同。后一次Global+F2E0、+9E80、+A0D0已非空，原生seed初始化字节为1，selector为1，但两key槽仍空；不能据此宣布完整SDK或登录通过。

**当前目标进程已退出，管理员调试器仍在。** 最新忽略会话索引为evidence/dynamic-active-session.json，指向的最后一次target已经结束；下次先查询is_debugging及系统进程。只有无其他目标时才能用现有管理员调试器重新load_executable，无需重复提权。单次软件断点在重启后可能不保留，应重新核对已加载模块/代码及断点表。

启动调试暴露两项观察工具问题：

- Automate的go(pass_exceptions=True)使用erun，按[官方说明](https://help.x64dbg.com/en/latest/commands/debug-control/erun.html)会传递全部首次异常，不是只传递当前异常。后续改用[DebugContinue](https://help.x64dbg.com/en/latest/commands/debug-control/DebugContinue.html)的`con 1`加普通run，仅交付当前异常，保留后续断点观察。前文将其写成单次传递不够准确，以本节为准。
- 本机一次禁用、随后删除研究用两处hardware watch后，断点表已无条目，但直接读取DR0/DR1/DR6/DR7仍显示原研究地址及使能位；1408AF2AA的RNG写入触发了对应DR6.B1。已只清理本研究设置的调试寄存器，并处理这个明确属于观察点的待决trap，未跳过客户端指令或修改客户端检查结果。证据dynamic-repro3-{hardware-control,clear-hardware,owned-watch-cleanup}.json。不能只信断点表状态。

控制复现先后记录退出码80000003/80000004。改用单次异常交付后仍出现80000004；清理残留后运行更远，Global/SDK对象建立，并观察到Il2CppExceptionWrapper、HResult80131530及空message。恢复软件观察后仍以80000003退出，未取得终止API断点调用现场。**退出原因unknown**，不能仅据状态码归因于保护、客户端缺陷或协议不兼容，也不绕过检查。SDK对象存在和语音库初始化日志不等于游戏认证成功。

共享摘要[startup-runtime-observations.json](../protocol/cn-7.2/startup-runtime-observations.json)；忽略证据dynamic-utf8-cache.json、dynamic-utf8-cache-repro3.json、dynamic-global-state-repro3.json、dynamic-http-breakpoints-repro3.json、dynamic-managed-exception-repro3.json、dynamic-phase-repro*.json及对应日志。旧phase20/21和repro3快照均为历史状态，当前没有运行目标。研究回归仍234，D-02未验；下一步离线Login/真实响应连续封包或独立核查调试退出机制。

## 2026-11-06恢复：连续API连接下的启动观察

恢复后重新发现本工作区的管理员Automate会话，确认无现存debuggee/游戏或Java进程，再按本机GAME_PATH加载固定三输入。5个不同入口各32字节与当前EXE相同：1411267B0、14A447E00、159C21C90、15A3464C0、15A366300。新目标的硬件断点表为空，DR0/1/2/3/6/7均为0；没有沿用旧PID、模块基址或暂停地址。

第一次分段API观察仍以80000003退出，未命中HTTP/Browser/Marshal或终止API观察点。复核发现，API断开期间应用可以停在下一个异常；重连的PUB/SUB事件流不包含之前事件，旧脚本可能在未分类该暂停原因时run。另一次初次订阅后立即run未收到事件，已留unknownStop。本次不把分段退出称为全程受控回放，也不据此归因保护、客户端或协议。

随后改为**同一连接连续观察150秒**：在每个新停止事件收到后才决定继续，并用调试器ex.code()/ex.addr()独立核对异常码和地址。共记录37个首次C0000005、1个首次406D1388和2个首次E06D7363；各个已观测首次异常均用con 1后普通run交给应用处理，没有第二次异常或进程退出事件。观察结束及后续只读核对均为debugging=true、running=true，Global已建立，两游戏帧key槽仍空。只有启动局部证据，没有SDK认证、补丁加载、帧收发、登录选人或D-02验收。

命令语义按[x64dbg DebugContinue](https://help.x64dbg.com/en/latest/commands/debug-control/DebugContinue.html)与[异常表达式](https://help.x64dbg.com/en/latest/introduction/Expression-functions.html#exceptions)核对；未用erun跳过后续首次观察，也未吞掉已分类异常。一次连续运行存活不能证明退出根因已修复，两个观察方法的时序不同；原退出原因仍unknown。HTTP/Browser/Marshal观察点在150秒区间内未命中，不等于这些函数不会调用或其ABI已完成验收。

共享摘要[startup-continuation-observations.json](../protocol/cn-7.2/startup-continuation-observations.json)只含输入hash、代码比对与脱敏范围。本机证据为evidence/continuation-startup-{load,01..06,managed-exception,continuous,live}.json，日志logs/continuation-startup-*，探索脚本analysis/observe_startup_{continuation,phase,continuous}.py，均在忽略local/migration72。分段脚本保留作失败过程，不作为推荐恢复入口。

**最新目标保留运行，API索引为evidence/dynamic-active-session.json；下次必须实时核对。** API断开只断开观察连接，不会停止目标；不得重复load新客户端。若已暂停，先读取当前ex.*和断点/日志，不能无条件run。需要进一步观察时保持同一连接，或从已保存并重新验证的当前停止原因恢复。运行服务仍未启动，数据和客户端文件未修改。
