# 国服7.2详细交接：A-03a源码索引完成，按用户要求暂停

更新：2026-11-06。用户最新要求“完成当前子任务之后先暂停，然后按照要求完成交接”。A-03a已完成并提交00e0bee；研发目标已设为paused，未启动下一子任务。以下后续动作须等用户明确恢复后执行。D-02未完成，也没有7.2登录入场实机验收。

用户允许按实际需要动态调试，并已手动完成管理员x64dbg启动和Automate加载；明确禁止computer-use。恢复后仍只用脚本API，不重复要求用户加载已准备的插件，先查实际会话状态。

## 当前子任务 A-03a：已完成与验证

研发提交 **00e0bee**：方法级登录/场景源码索引。新增tools/Client72JavaSurface.java、client72_java_surface.py、review_client72_login_surface.py及8项测试；复核输入为protocol/cn-7.2/login-source-review.json，共享摘要为login-source-observations.json，说明见[CLIENT_72_LOGIN_SOURCE_GRAPH](docs/CLIENT_72_LOGIN_SOURCE_GRAPH.md)。

- 固定12份遗留服务端源码、9个Handler根、80个方法、96条边。其中13条是人工复核边，83条仅为同owner唯一名称/参数数量候选；1012未关联调用保留，不代表1012个未知消息。
- 61个直接构造参数类型名含BasePacket；总构造类型名62，额外PacketWindSeedUID来自工厂返回。Player.onLogin有20个直接构造参数。均不代表完整/最小7.2消息集，不与A-02的114名称/471文件口径混算。
- JDK17实际parse，不执行项目/注解处理或符号归属；字段/初始化器和匿名/局部类分别记录。候选边、重载歧义、管理器/服务/事件/脚本/隐式初始化均不自动晋级。closureComplete=false、targetProtocolReady=false、runtimeStatus=not-tested。
- 新号intro启用时进入PICKING_CHARACTER、按配置可发通知、先LoginRsp后等待选人；旧号先Player.onLogin再LoginRsp；无角色且intro关闭会创建默认旅行者。超时兜底另有lambda和状态检查，不能据此验收真实选人。SetBorn走准入/角色与资源检查、保存、onLogin、配置响应及欢迎邮件；服务端业务源码不等于客户端协议证据。
- 8项聚焦测试及248项研究回归通过。覆盖Unicode/CRLF原始UTF-16位置、record/switch、lambda/匿名类/字段作用域、间接构造、重载、漂移和禁止晋级。两次完整报告/摘要逐字节相同，研究映射校验0/ready1（213项缺口）。未重跑Java完整项目测试，历史217失败未解决。
- 运行代码/Proto/配置/补丁/资源没有改动；没有部署、数据库修改或新增实机验收。README、Project、Harness、协议盘点及三份账号专题文档已同步；AGENTS和resources他人差异保持未提交。

复现（checkout根；JDK路径按本机核对）：

```powershell
python tools/review_client72_login_surface.py --java-home local/runtime/jdk-17.0.20.1+1 --output local/migration72/evidence/login-source-surface.json --summary-output protocol/cn-7.2/login-source-observations.json
python -m unittest tools.test_client72_java_surface -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

本机证据：evidence/login-source-surface{,-repeat,-determinism}.json、evidence/login-source-summary-repeat.json、evidence/login-source-mapping-{check,ready}.json，日志logs/login-source-surface-{run,repeat,focused-tests,regression}.log。完整报告hash为64194613a1e53d45369a2b06d3deffcd74bed4d69b280fe3f3b8be6a9b92ccdc；共享摘要hash为8920466702325db2ba27837ab9d7588e2432049b858a3694e7f0350940055bf3。所有原始报告与工具临时编译产物留忽略目录。

## 此前原生与动态研究成果

- A-04d5u提交0153d00：原生Ec2b注册/RNG初始化/raw与managed解码16样本，228回归与重复产物通过，初始化与live5000字节及独立seed103递推一致。
- A-04d5v已提交2523513：第一分区9块RSA响应→原生解密/摘要/验签/reader→三次Ec2b→两配置XOR/清槽→最终key原指针→真实Token构造/发送/size/writer/完整帧。4原生样本、234研究回归、二次trace/摘要一致；原dispatch-config、dispatch-session、token-frame各6样本产物逐字节不变。
- D-01b2已提交cf09603：两次真实运行的UTF-8两缓存同对象/3虚槽均匹配目标声明；新增4处代码各32字节相同。后一次Global+F2E0/+9E80/+A0D0已非空，seed初始化字节1，selector1，key槽仍空。退出原因未知，不代表完整SDK/登录。
- A-04d5w已提交6db47fe：响应reader之后，1507BE0E0→原生密码学/安装key→Handler/Login构造→外层与68918实际size/writer→完整frame返回1472CD364，连续执行。10样本中4帧354/364/304/4560字节，含Unicode/NUL和跨key周期；6延迟/缺对象/Medium/坏签名均未发送。全负载独立protoc一致，9组安装key与Java一致；240研究回归、二次产物一致，原Token6组产物不变。未返回整个Handler，未执行响应收帧/factory/socket或实际登录。
- v没有提供初始frame key。30677 tag11/+40实际供给三个decoder调用，第三次原返回指针由151212942安装至GLOBAL+19588，之后Token保持原指针及内容。两配置分别使用前两次返回指针XOR，随后清槽。
- 四样本：有效key的504字节帧、含中文/补充字符/NUL的5314字节跨4096周期帧、错误魔数Nope、空seed。后两组明确提供明文配置，最终key为空、Token帧保留明文；这只是目标行为，不自动改变服务端准入。
- 分区与Token是同一emulator中的两次显式入口调用，调度器未执行。Token构造至frame返回连续，seed oracle在frame返回后才执行其额外native getter。
- 研究校验0/ready1，213缺口仍在；四登录消息36业务字段不变。tag965和分区key源业务名等未知项不晋级。B-02整体未完成，D-02无登录入场验收。
- Java完整集历史217项失败未消除；最新248是研究Python回归数，不能当Java全绿。

## 最新真实调试状态

**最后一个目标进程已退出，管理员调试器保留。** 当前没有暂停中的游戏目标；旧mhypbase/TLS/注册ret记录均为历史。重新研究前先核对is_debugging与系统进程，确认无其他客户端后可用原管理员调试器load_executable，不需重复提权或插件加载。

本机PID/ASLR仅在忽略报告。Python入口`local/migration72/debug-venv/Scripts/python.exe`，X64DbgClient指向checkout内`local/runtime/x64dbg-2026.05.27/release/x64/x64dbg.exe`。最新索引`evidence/dynamic-active-session.json`的target也已结束；dynamic-startup-read.json仅是最早会话历史，不能再用其target PID。attach_session连接调试器；先is_debugging，再查询$pid/实际mod.main基址。debugging=false时不要把API的is_running=true误当游戏运行。detach_session只关闭接口，不是debuggee detach。

关键纠正：go(pass_exceptions=True)实际erun会传递所有首次异常，不能用来只交付当前异常。以后确需让应用处理当前异常时，用`cmd_sync('con 1')`再普通go。官方依据与复现见CLIENT_72_DYNAMIC_DEBUG末节。正常客户端异常不吞、不改返回值，也不隐藏调试器或绕过保护。

本机两处研究hardware watch禁用/删除后，表已无条目而DR0/DR1/DR6/DR7仍残留，实际RNG写入触发了自设DR6.B1。已核对仅为本研究地址，清理自设调试寄存器并处理该待决研究trap；没有跳过客户端指令或修改检查结果。证据dynamic-repro3-{hardware-control,clear-hardware,owned-watch-cleanup}.json。以后不要只看断点表，避免重新引入残留。TLS自动停点设置已由1改为0以减少观察干扰，原值记录dynamic-repro-event-settings.json；此为专用调试器配置，不是客户端校验。

退出记录：原运行80000003，两个复现80000004；改成con 1+run仍曾80000004。清理watch后运行更远，UTF-8/SDK对象和托管Il2CppExceptionWrapper已观察；HResult80131530、message为空，具体语义未命名。恢复软件观察后仍80000003退出，终止API断点没有调用现场，原因unknown。不能据此断言保护检查、客户端故障或协议不兼容。

最新证据：dynamic-utf8-cache{,-repro3}.json、dynamic-global-state-repro3.json、dynamic-http-breakpoints-repro3.json、dynamic-managed-exception-repro3.json、dynamic-phase-repro*.json和对应日志。软件单次断点跨重启可能丢失；14146ADA0、15A3464C0、15A366300、15997C290都需重新核对代码/基址后设置。analysis/dynamic_advance.py会真正继续当前目标，且现在读取active-session并拒绝旧--pass-exceptions选项；--pass-current-exception才是当前单次交付。dynamic_deliver_observed_exception.py仅用于经过核对的NTDLL同一C0000005现场，不能泛化自动忽略异常。

## 正式工具与复现

最新w入口`trace_client72_handler_login_frame.py`，参数--client、--java-home、--protoc、--output、--summary-output。当前便携JDK为local/runtime/jdk-17.0.20.1+1；protoc在local/gradle的com.google.protobuf/protoc/3.18.1缓存，具体路径重新查找，不能套用另一机器绝对路径。完整命令见CLIENT_72_NETWORK末节。

LoginFrame72复用TokenFrame72.attach_flow绑定已存在的SendFlow，Handler观察器只在原发送现场添加池/虚表存储，不重置CPU。类型76410的152EF0D50/152EEB470与实际非空子类68918均原生执行。ByteString长度/写入、14A721B00小块copy、1404D7630原生Buffer copy执行；数组补齐已声明byte[] class，不使用原Frame copy模型。旧copy存储模型返回0，本段Buffer.BlockCopy会消费bool结果，不能直接套用；这里执行原生函数，不强置成功。

w证据为evidence/handler-login-frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json与logs/handler-login-frame-{run,repeat,regression}.log；原Token对照evidence/token-frame-login-regression.json。trace SHA256为2ea7746534fbc2c346084f1873c1a2cf4d55fc1791a9a249a7dd09e3682cd9df，summary为bec5774c147d249ff7e963a68b633da4930d3297b1ecacbd68a2d8386ebb95f2。下面v入口和证据仍有效。

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

全研究回归选集为tools/test_client72*.py，另加test_extract_client72、test_validate_protocol72、test_audit_resource72、test_inventory_protocol、test_review_protocol72_surface，当前248/0。原生运行必须单独执行trace工具，不能仅校验摘要就称原生已复现。

证据位于忽略local/migration72：evidence/dispatch-key-frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json及logs/dispatch-key-frame-{run,repeat,regression}.log。旧三工具为evidence/{dispatch-config,dispatch-session,token-frame}-key-regression.json及对应summary/log。

v trace SHA256为2f8b9a4591e75fcad63c2779c47f35e9d0258659a05a40fae5fa13a8fc3ada9f；summary为4f4839c19b1dfeb05a96e143764a2206eda7fd1c93c31691da4b2ceb628048ca。共享摘要protocol/cn-7.2/dispatch-key-frame-observations.json只含hash/合成值/范围。详细边界见CLIENT_72_NETWORK、CLIENT_72_DYNAMIC_DEBUG、Project和Harness。

## 弯路与避坑

1. 配置ASCII错误来自模型读取分配时缓存的旧密文；native XOR实际正确，改读真实内存解决，不能称客户端解密失败。
2. Frame的池化Array.Copy模型提前安装会污染分区native copy。现在分区期间移除该模型，发送现场才安装。
3. seed.finish会另行执行getter做oracle；必须先在原发送现场继续frame，不能finish后把RIP误当send。
4. 跨周期样本最初修改account+B0，覆盖了tag965源。现只扩大两个凭据字符串和device输入，保留B0原source，最终5314字节。
5. 独立wire测试曾将23/22字节JSON手算成22/21，已纠正固定hex长度，未改原生输入/行为。
6. 命令入口曾报PowerShell7的pwsh.dll不存在，显式使用系统WindowsPowerShell可继续；不修理无关安装。其重定向可能生成UTF-16日志，正式工具JSON自身固定UTF-8，读取日志注意BOM。
7. A-03a测试一度把规范LF字符串坐标与Windows原始CRLF文件坐标混比。解析器的UTF-16位置正确，已修正测试按原文件计算；hash仍规范CRLF→LF。不要通过改位置或吞语法错误使测试通过。
8. lambda深度只是语法信息，不能推断回调时机。直接new参数与变量/工厂返回严格分开；PacketWindSeedUID不能丢掉，也不能伪称直接发送。当前工具会拒绝方法ID碰撞；未执行符号归属和隐式初始化，不能宣称完整Java调用图。

## 未完成与下一步

当前停止原因是用户明确要求暂停，目标paused；不是宣称所有独立工作被外部条件阻塞。A-03闭包、B-02映射、D-01补丁/信任/路由、E-01资源以及Java失败仍是未完成前置。恢复后按以下顺序推进，不在暂停期间自行执行。

1. 优先继续A-03管理器/服务边界：使用A-03a的evidence/login-source-surface.json中unlinkedCallSites，从Player.onLogin的任务/活动/队伍/家园/邮件等实际调用逐个定位；把新增源码hash和人工边单独复核，再追消息构造/Proto引用。不盲目把所有未关联调用算作协议缺口，也不将整个管理器文件算作必需消息集；按实际边界另建子项。该后续尚未开始。
2. 亦可独立把真实响应reader/密钥安装接回原生收帧，明确factory/对象分配来源；已有FrameReader、Handler/graph工具可复用。不要只调用prepare重置CPU后声称连续收帧。
3. A-03/B-02仍缺新旧号、选人、初始化/场景、嵌套/枚举业务闭包；B-03/C-01不得生成猜测映射。
4. D-01本地信任配置/补丁实际加载/路由与E-01资源revision/Lua/场景未闭合；当前补丁不变，不能放开守卫启动猜测协议。
5. D-02须新号选人、旧号登录、初始化/场景握手、稳定心跳、退出重登实测；离线回归和启动暂停均不替代。
6. V-01 Java217失败仍需逐项归因/修复，保留账号、经济、保存不变量。

## 工作区与备份

分支7.2-dev，沿用checkout。A-03a本批仅离线工具、测试、脱敏摘要和文档，无运行Java/主Proto/配置/补丁/资源改动，无需部署/重启。已核对GAME_PATH、portable和端口，.env.local不存在；Java未运行，便携MongoDB归属本工作区。交接时再次只读核对Java/游戏进程不存在、HTTP8088/UDP22101无监听、便携MongoDB27017归属本工作区；两个x64dbg进程保留。记录evidence/login-source-handoff-processes.json。未停止MongoDB或调试器；PID及原始路径不写共享文档。运行前仍须实时核对配置/PID/端口/JAR SHA256。保留原mongodata及local/data。

AGENTS.md编码规范修改和resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异属他人，保留不提交。patch为f2874103ab70afc56260ee12d1eaca96247933b2，resources为dac2a9d43332ef51879522cf39da2831b0ca0543，引用不变。只本地提交，不推送。

覆盖本文前旧HANDOFF2已备份至local/migration72/handoffs/HANDOFF2.before-login-source-6db47fe.md并逐字节/hash核验；报告evidence/handoff-login-source-backup-check.json，旧hash为e9b0812da232ecadd20d842ea1b17167b9569146472d78be4c33e288613c2f75。该备份包含此前完整帧与动态启动检查点，更早备份仍保留。HANDOFF.md仅追加本次简要交接。最新暂停指令有效，旧active段落仅历史；用户恢复前不继续研发。
