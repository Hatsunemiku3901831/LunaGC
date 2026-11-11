# R3-PROUD-HOT-EQUALITY-INPUT/PREPARE：只读采样准备

本项已完成离线准备及主线自进程Win32验证，尚未采集客户端；未来只在用户本轮新ready后，从本项目stock进程取热链。没有启动、连接、读取或结束客户端。采样器与A第二返回、B组合loader的拒绝动作及其草稿无依赖；不构造、执行或调用Equals、Default、GC。已验FindEntry802B与metadata usage732126固定来源仅作读取地址证明，不证明自然查询曾发生。

`tools/migration72/proud_actual_adoption_hot_equality_sampler.py` 复用已验 observer 的文件身份、进程创建时间、主模块快照与只读句柄。identity 模式要求本次用户新 ready 和明确的本项目 stock PID，只用 `PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE` 获取身份与主模块，不读取进程内存；sample 模式重新核身份并用 `PROCESS_VM_READ | PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE` 执行读取。两个入口均根据当前 `GAME_PATH`、profile 核五文件 SHA，核对原 802 B 与固定 EXE 窗口、metadata cache、已验 usage 报告、observer 和 PE 提取器的固定 SHA；采样还要求 identity 时输入及配置一致，并核对精确 exePath、创建时间、主模块 base 和映像大小。不追踪其他模块。旧 observer 的主流程会启动新进程，故不用于给已有 PID 制作 identity。

同句柄依次最多七次 `ReadProcessMemory`，共最多 50 B：`B+594e6f0 → Q+38 → S[0] → O[0]=K`，继而 `K+BE` 读 U16 N、`K+110` 读 F、`K+110+N*8` 读 M。N=0 有效，即使后两次地址相同仍按规定独立读取。每项保留请求地址/长度、原始字节、实际长度、错误与 UTC；null、短读、错误或地址越界立即停止下游，不补默认值、不重试。F 不在固定 EXE 时只标记 `unknown`，不查其他模块，不读 MI 的额外 56 B。结果是顺序读取，非原子快照；同 epoch 不证明对象在各次读之间不变。

## 复现与主线操作

纯离线夹具，无 WinAPI 或目标进程：

```powershell
python -X utf8 -B -m unittest tools.migration72.test_proud_actual_adoption_hot_equality_sampler -v
```

主线如需先做真实 Win32 验证，可显式选择以下仅自进程测试；默认套件会跳过它。测试在 Python 自身分配缓冲区后用只读句柄读取，不访问游戏。以下两行在同一个 PowerShell 中执行，结束后移除临时变量：

```powershell
$env:PROUD_SAMPLER_SELF_TEST='1'
python -X utf8 -B -m unittest tools.migration72.test_proud_actual_adoption_hot_equality_sampler.HotEqualityReaderTests.test_win32_self_process_read -v
Remove-Item Env:PROUD_SAMPLER_SELF_TEST
```

主线自进程测试后，在用户明确新 ready 并确认本项目 stock PID 后，按顺序执行以下两条命令；两份输出须为尚不存在的私有文件，`<pid>`、`<identity>` 和 `<sample>` 自行替换：

```powershell
python -X utf8 -B tools/migration72/proud_actual_adoption_hot_equality_sampler.py identity --pid <pid> --output local/migration72/d02-resume-20261111b/proud-equality-sampler/<identity>.json --user-ready
python -X utf8 -B tools/migration72/proud_actual_adoption_hot_equality_sampler.py sample --identity local/migration72/d02-resume-20261111b/proud-equality-sampler/<identity>.json --output local/migration72/d02-resume-20261111b/proud-equality-sampler/<sample>.json --user-ready
```

输出仅在私有忽略目录。JSON 的 `sourceIdentitySHA256`、`sourceSHA256`、`inputChecks`、`sourceChecks`、`pid`、`creation`、`observedModule`、`mainModule` 及 `sampleStartUtc`/`sampleEndUtc` 关联来源；`records` 保留各次原始值和失败，`values` 仅含成功完整读取的值；`reads`/`requestedBytes` 为实际请求数/字节数。保留完整 identity、采样 JSON 和对应采样器源码原件；不要在共享文档发布进程地址或原始读取。采样退出码 0 只表示七读均完整且最终进程仍存活，非协议或客户端验收。

热对象存在且所有读取成功时，保留 F/M 和原件供主线另行判断身份；F 未落固定 EXE 时保持未知。任一指针为 null（包括 O 冷时）、读短或失败时结果为 `partial`，保留已读记录并停止，不能运行冷创建补齐。身份、输入或模块不符，以及进程退出前后状态不满足时停止；采样后退出标为 `partial`，读取记录仍保留。预检错误为 `error`，采样后状态查询出错也保留已有记录。任何新的安全审查拒绝须保存原文并停止被拒动作与依赖，不换方式重试；退出后不自动重启或恢复旧 ready。

本轮主线实际执行8项纯Python夹具，覆盖七读/50B、每步null/短读/错误、64位地址与值、U16的0和65535、地址溢出、模块外F、identity只读查询句柄，以及输入、创建时间、主模块基址不匹配时不读取；均通过。随后显式开启仅自进程Win32测试并实际通过，临时环境变量已移除。入口离线preflight重新核五固定文件和5项source pins，源码与预检快照留私有 `proud-equality-sampler/`；没有采样真实客户端。

实验问题只有“本时点该准确Default热对象及其F/M是否存在”。用户正常打开当前GAME_PATH的stock标题/SDK页后，主线核定唯一PID并执行一次identity与sample，预计30秒内完成；任何null/短读/错误原样保留，不自动重试或触发cold。用户只需反馈正常界面或异常退出；读取完成后可正常关闭客户端，没有DLL/文件改动需要恢复。热对象不保证存在，也未观察自然hit；原热hit、真实Proud行、producer、门槛、resourceReady和D-02均未因此完成。仅离线工具与测试，无服务构建、部署或重启；共享Project/Harness、Git和客户端现场由主线维护。
