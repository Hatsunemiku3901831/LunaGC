# D02-R2：非调试器原始启动监测器

2026-11-07 修正：主线首次实机 `plain/run-elevated1.json` 使用观察器 SHA-256 `3925aaa11068c6109c4300a123c8869cb1222eaf951efabf8ef060cf58d8aad5`，线程快照调用错误地以 PID 0 过滤，所列 44 次中的 4224 个线程查询为 Idle PID 0，不是目标线程；其 OpenThread 错误 87 不代表目标线程状态，不能作为目标 ThreadHide true、false 或 unknown 的逐线程结果。原始文件保留。目标独立句柄的存活90秒、模块快照和创建时间不依赖该线程过滤错误，但它们的解释仍按原实机记录限定。自线程 probe 独立于此错误。新版本将目标 PID 传入过滤，并用实际 sample 函数体、虚拟快照与 OpenThread 守卫核对；修复前红测和修复后全量日志分别为 `plain/thread-pid-red.log`、`plain/thread-pid-green.log`。新一轮实际线程结果须另行获得。

工具现已由主线审核、两次实际执行，最终状态见下文。前次单硬件观察记录 SHA-256 `4ba34a38adbd960a033cfb1754ec1455daff804cbed8f376c0cb6f291cd4bbf4`，38.875秒时 OS 退出 `80000004`；Report.wer SHA-256 `d0aed5de85f56077a860236cf406e2d7a4672cb93c623e7b7b76c52ceaa35737`。当时用户未在现场，画面未知；没有 dump 或最终异常事件。详见 [HW退出审计](CLIENT_72_D02_HW_EXIT.md)。非调试器对照改变多个变量，不能单独归因。

## 使用条件和行为

仅主线在用户明确就绪、复核现场与运行前置后执行；游戏界面供用户观察，不输入账号或凭据。工具通过当前 checkout 的 `tools/game_path.ps1` 解析路径，固定比对 profile 和五文件哈希，记录 profile、`.env`、`.env.local`、`config.json` 的哈希和状态，不输出配置内容。拒绝已发现的 `YuanShen.exe`、`GenshinImpact.exe` 和可查询路径属于游戏目录的进程；无法读取路径的进程数量单独记录，不能据此断言所有进程路径已知。进程快照失败即拒绝启动。不会连接、附加或改变已有调试器。

`CreateProcessW` 直接指定原始 exe、原游戏目录、仅含 exe 的命令行、flags=0、无调试标志；默认窗口行为不隐藏游戏。无安装补丁或服务器启动；原始哈希覆盖 Astrolabe 和 AccountPlatNative，不能证明整个系统不存在其他外部影响。使用独立进程句柄核 PID、可执行路径、`GetProcessTimes` 创建 FILETIME 和存活/退出码；创建时间查询失败时记录原始 Win32 错误，不假称已取得。两秒间隔 Toolhelp 采样模块和线程，前后记录固定候选日志与 revision 文件大小、时间、SHA-256，不复制文件内容。模块、线程均为分时采样，无法证明瞬态对象不存在；文件状态变化不证明客户端读取了该文件。

预算最长90秒，从启动前计时；采样调用若阻塞，最终返回可能稍晚。自然退出记录退出码；预算内存活则保留进程，只关闭观察句柄，供用户正常关闭。程序失败也不会终止目标；先核输出 PID、路径与系统状态，不能把预算后的存活目标当作无目标。

线程隐藏标志为可选只读采样：动态调用 `NtQueryInformationThread` class17，先对观察器自身线程验证状态、1字节返回长度和0/1；仅自测支持时，对 `OpenThread(0x40)` 且 `GetProcessIdOfThread` 匹配目标 PID 的线程查询。失败、线程已退出、长度或值异常均为 unknown；不暂停线程、不查询或写线程 context、不设置线程信息。该接口为内部接口，来源固定 [phnt ntpsapi.h](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpsapi.h)，SHA-256 `3477b3fa901715c5ad139ae154f86d6c36f3415962731d6c0de0dd3c8417f4d8`。某线程某时刻的值不能证明旧故障线程状态或退出原因。

## 主线执行入口

固定位置使用新输出名，必须有 `--user-ready`；不得覆盖已有输出。`--seconds` 最多90，默认90。任何错误写忽略目录，工具不会自动改变权限或重试。

```powershell
python tools/observe_client72_uninstrumented.py --user-ready `
  --output local/migration72/d02-active/plain/run1.json --seconds 90
```

如 CreateProcess 实际返回 740（需要提升），先确认未成功创建目标且用户仍在现场；主线可用已授权的 Windows ShellExecuteEx `runas` 交互启动同一观察器，UAC 由用户处理，传同样参数并改为新的输出名。提升后的观察器仍执行固定输入、已有进程及90秒检查；应核对其用户环境的 GAME_PATH 与配置，不能据提权前结果替代。预期非零退出、访问拒绝、路径不匹配、快照错误或未观测UI均按实际结果保留，不猜测重试。存活且出现自然界面仅支持本次运行更远，不能当登录或 D-02 验收。

## 本次测试

模拟后端测试覆盖原始路径及无 debug flags、退出/存活、已有目标、枚举失败、启动失败、句柄图像不匹配、自测不支持及错误查询保持 unknown；修复后新增直接执行sample函数的PID/线程句柄owner回归，主线11项通过。对自身线程只读查询实际返回 NTSTATUS=0、ReturnLength=1、原始字节0；未构造隐藏线程或修改线程状态。最终日志与采样器7项合跑为`main/plain-sampler-final-tests.log`，共18项通过；先前模拟及红绿日志分别保留，不把合成启动算实机。

## 2026-11-08 主线实际结果

用户明确就绪后首次普通权限返回740且没有创建PID；主线以提升后的同一观察器启动，五profile/config再次核对，无参数/flags=0。第一次90秒存活、44样本、模块并集151；因上文PID0缺陷，线程含义全部撤回。用户报告已到SDK登录页；其自行试用测试凭据时出现账号密码错误，当时没有本服服务、路由或补丁，不能算本服登录失败/通过，凭据不记录。

用户要求重启；主线正常关闭helper运行时旧进程已经不存在，没有实际发关闭，也未观察该次退出码。核无游戏后只启动一个新进程，采用修复源`e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e`。第二次也90秒存活、44样本，151模块；实际线程结果10687 false、43 true、1 unknown。这只是各采样时刻的只读结果，不指认旧HW故障TID/原因。SDK及WinHTTP/WinINet模块存在不能证明自然请求身份或成功。

| 原始报告 | SHA-256 | 解释 |
| --- | --- | --- |
| `plain/run-elevated1.json` | `d07f9fadd81c23b03d338d17986d78a320237c6e4fa3e83c659b13532747a7a3` | 存活/模块证据有效，线程PID0含义撤回 |
| `plain/run-elevated2.json` | `2643bfe4006acaf3a7a97c1c0e681d5e71ae8a3c27f048cc31d87dc273adcf9f` | 修复源的实际存活/模块/线程记录 |

用户截图`plain/login-ui-restart.png` SHA`487043039e15e12ae4f4763e816dd94dd5668802930b0ec960da2e155ce8692a`可见SDK登录页及`CNRELWin7.2.0_R49008628_S48722240_D49008628`，不以这个label推断每个资源blob revision。用户随后确认通过界面正常退出，主线OS核无游戏/调试器/Java/Mongo或相关端口；没有保留句柄观察退出码。证明`plain/after-user-exit.private.json`及`main/checkpoint2-inventory.private.json`。当前没有target，旧报告不允许直接用于继续操作PID。

两版实际执行源分别保存`plain/source-snapshots/observe_client72_uninstrumented-v{0,1}.py`，SHA与报告唯一匹配，证明`executed-source-proof-all.json`。本项只修改研究工具/测试/脱敏文档，不改运行服务器/Proto/config/patch/资源，无需服务重启；本服SDK、入场、心跳和保存验收均未通过。
