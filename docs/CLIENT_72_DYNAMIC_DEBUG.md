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
