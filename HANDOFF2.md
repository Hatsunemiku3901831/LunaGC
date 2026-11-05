# 国服7.2详细交接：分区密钥到Token帧及真实启动ABI

更新：2026-11-06。用户要求持续按Project逐项推进至D-02实机验收；目标active，没有暂停指令。用户允许动态调试，已手动完成管理员启动和Automate加载，并明确禁止computer-use；以后只用脚本API。此文为持续工作检查点，不要求再次授权。

## 完成内容与当前状态

- A-04d5u提交0153d00：原生Ec2b注册/RNG初始化/raw与managed解码16样本，228回归与重复产物通过，初始化与live5000字节及独立seed103递推一致。
- A-04d5v已提交2523513：第一分区9块RSA响应→原生解密/摘要/验签/reader→三次Ec2b→两配置XOR/清槽→最终key原指针→真实Token构造/发送/size/writer/完整帧。4原生样本、234研究回归、二次trace/摘要一致；原dispatch-config、dispatch-session、token-frame各6样本产物逐字节不变。
- D-01b2限定验收完成：两次真实运行的UTF-8两缓存同对象/3虚槽均匹配目标声明；新增4处代码各32字节相同。后一次Global+F2E0/+9E80/+A0D0已非空，seed初始化字节1，selector1，key槽仍空。退出原因未知，不代表完整SDK/登录。
- v没有提供初始frame key。30677 tag11/+40实际供给三个decoder调用，第三次原返回指针由151212942安装至GLOBAL+19588，之后Token保持原指针及内容。两配置分别使用前两次返回指针XOR，随后清槽。
- 四样本：有效key的504字节帧、含中文/补充字符/NUL的5314字节跨4096周期帧、错误魔数Nope、空seed。后两组明确提供明文配置，最终key为空、Token帧保留明文；这只是目标行为，不自动改变服务端准入。
- 分区与Token是同一emulator中的两次显式入口调用，调度器未执行。Token构造至frame返回连续，seed oracle在frame返回后才执行其额外native getter。
- 研究校验0/ready1，213缺口仍在；四登录消息36业务字段不变。tag965和分区key源业务名等未知项不晋级。B-02整体未完成，D-02无登录入场验收。
- Java完整集历史217项失败未消除；234是研究Python回归数，不能当Java全绿。

## 最新真实调试状态

**最后一个目标进程已退出，管理员调试器保留。** 当前没有暂停中的游戏目标；旧mhypbase/TLS/注册ret记录均为历史。重新研究前先核对is_debugging与系统进程，确认无其他客户端后可用原管理员调试器load_executable，不需重复提权或插件加载。

本机PID/ASLR仅在忽略报告。Python入口`local/migration72/debug-venv/Scripts/python.exe`，X64DbgClient指向checkout内`local/runtime/x64dbg-2026.05.27/release/x64/x64dbg.exe`。最新索引`evidence/dynamic-active-session.json`的target也已结束；dynamic-startup-read.json仅是最早会话历史，不能再用其target PID。attach_session连接调试器；先is_debugging，再查询$pid/实际mod.main基址。debugging=false时不要把API的is_running=true误当游戏运行。detach_session只关闭接口，不是debuggee detach。

关键纠正：go(pass_exceptions=True)实际erun会传递所有首次异常，不能用来只交付当前异常。以后确需让应用处理当前异常时，用`cmd_sync('con 1')`再普通go。官方依据与复现见CLIENT_72_DYNAMIC_DEBUG末节。正常客户端异常不吞、不改返回值，也不隐藏调试器或绕过保护。

本机两处研究hardware watch禁用/删除后，表已无条目而DR0/DR1/DR6/DR7仍残留，实际RNG写入触发了自设DR6.B1。已核对仅为本研究地址，清理自设调试寄存器并处理该待决研究trap；没有跳过客户端指令或修改检查结果。证据dynamic-repro3-{hardware-control,clear-hardware,owned-watch-cleanup}.json。以后不要只看断点表，避免重新引入残留。TLS自动停点设置已由1改为0以减少观察干扰，原值记录dynamic-repro-event-settings.json；此为专用调试器配置，不是客户端校验。

退出记录：原运行80000003，两个复现80000004；改成con 1+run仍曾80000004。清理watch后运行更远，UTF-8/SDK对象和托管Il2CppExceptionWrapper已观察；HResult80131530、message为空，具体语义未命名。恢复软件观察后仍80000003退出，终止API断点没有调用现场，原因unknown。不能据此断言保护检查、客户端故障或协议不兼容。

最新证据：dynamic-utf8-cache{,-repro3}.json、dynamic-global-state-repro3.json、dynamic-http-breakpoints-repro3.json、dynamic-managed-exception-repro3.json、dynamic-phase-repro*.json和对应日志。软件单次断点跨重启可能丢失；14146ADA0、15A3464C0、15A366300、15997C290都需重新核对代码/基址后设置。analysis/dynamic_advance.py会真正继续当前目标，且现在读取active-session并拒绝旧--pass-exceptions选项；--pass-current-exception才是当前单次交付。dynamic_deliver_observed_exception.py仅用于经过核对的NTDLL同一C0000005现场，不能泛化自动忽略异常。

## 正式工具与复现

- client72_ec2b.py实际执行注册1411267B0、RNG初始化1408AF2A0、1411268D0→1408BAEE0→1408BAD50→1408AF320；engine selector5和managed分配为明确模型。
- trace_client72_dispatch_key_frame.py使用固定seed/key独立输入；大JSON按实际长度分配，9块RSA专用360秒/600M预算，旧prefix默认预算不变。
- DispatchConfig72.get_string读native XOR后的实际内存，用严格UTF-8模型；畸形UTF-8替换策略未验。
- construct_native_token新增可选continue_send，在seed oracle之前继续原发送现场；默认行为保持。TokenFrame72可显式不给key，旧默认夹具与产物不变。

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
python tools/trace_client72_dispatch_key_frame.py --client $env:GAME_PATH --output local/migration72/evidence/dispatch-key-frame-trace.json --summary-output protocol/cn-7.2/dispatch-key-frame-observations.json
python -m unittest tools.test_client72_dispatch_key_frame
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

全研究回归选集为tools/test_client72*.py，另加test_extract_client72、test_validate_protocol72、test_audit_resource72、test_inventory_protocol、test_review_protocol72_surface，本批234/0。原生运行必须单独执行trace工具，不能仅校验摘要就称原生已复现。

证据位于忽略local/migration72：evidence/dispatch-key-frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json及logs/dispatch-key-frame-{run,repeat,regression}.log。旧三工具为evidence/{dispatch-config,dispatch-session,token-frame}-key-regression.json及对应summary/log。

v trace SHA256为2f8b9a4591e75fcad63c2779c47f35e9d0258659a05a40fae5fa13a8fc3ada9f；summary为4f4839c19b1dfeb05a96e143764a2206eda7fd1c93c31691da4b2ceb628048ca。共享摘要protocol/cn-7.2/dispatch-key-frame-observations.json只含hash/合成值/范围。详细边界见CLIENT_72_NETWORK、CLIENT_72_DYNAMIC_DEBUG、Project和Harness。

## 弯路与避坑

1. 配置ASCII错误来自模型读取分配时缓存的旧密文；native XOR实际正确，改读真实内存解决，不能称客户端解密失败。
2. Frame的池化Array.Copy模型提前安装会污染分区native copy。现在分区期间移除该模型，发送现场才安装。
3. seed.finish会另行执行getter做oracle；必须先在原发送现场继续frame，不能finish后把RIP误当send。
4. 跨周期样本最初修改account+B0，覆盖了tag965源。现只扩大两个凭据字符串和device输入，保留B0原source，最终5314字节。
5. 独立wire测试曾将23/22字节JSON手算成22/21，已纠正固定hex长度，未改原生输入/行为。
6. 命令入口曾报PowerShell7的pwsh.dll不存在，显式使用系统WindowsPowerShell可继续；不修理无关安装。其重定向可能生成UTF-16日志，正式工具JSON自身固定UTF-8，读取日志注意BOM。

## 未完成与下一步

没有已确认的全局外部硬阻塞，目标继续active，不因检查点停止。

1. 优先独立推进离线Handler→Login实际size/writer/完整帧；76410槽13为152EF0D50，槽17为152EEB470。已有HandlerLogin72在14A447E00停止，必须延续原发送现场；Frame/Encoding/池模型不要污染前段密码学。可用现有3发送+拒绝/延迟样本作起点。
2. 可独立推进Handler→Login真实writer/完整帧，或真实响应reader/密钥安装接回收帧。已有工具/slot证据可复用。
3. A-03/B-02仍缺新旧号、选人、初始化/场景、嵌套/枚举业务闭包；B-03/C-01不得生成猜测映射。
4. D-01本地信任配置/补丁实际加载/路由与E-01资源revision/Lua/场景未闭合；当前补丁不变，不能放开守卫启动猜测协议。
5. D-02须新号选人、旧号登录、初始化/场景握手、稳定心跳、退出重登实测；离线回归和启动暂停均不替代。
6. V-01 Java217失败仍需逐项归因/修复，保留账号、经济、保存不变量。

## 工作区与备份

分支7.2-dev，沿用checkout。本批仅离线工具、测试、脱敏摘要和文档，无运行Java/主Proto/配置/补丁/资源改动，无需部署/重启。已核对GAME_PATH、portable和端口，.env.local不存在；Java未运行，便携MongoDB归属本工作区。运行前仍须实时核对配置/PID/端口/JAR SHA256。保留原mongodata及local/data。

AGENTS.md编码规范修改和resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异属他人，保留不提交。patch为f2874103ab70afc56260ee12d1eaca96247933b2，resources为dac2a9d43332ef51879522cf39da2831b0ca0543，引用不变。只本地提交，不推送。

覆盖本文前旧HANDOFF2已备份至local/migration72/handoffs/HANDOFF2.before-startup-runtime-2523513.md并逐字节/hash核验，报告evidence/handoff-startup-runtime-backup-check.json；旧hash为8e8b572a3714319ca9d7c3aac75c4442103a5625729dac452c3f020189aec404。HANDOFF.md只追加，旧暂停段落仅历史。
