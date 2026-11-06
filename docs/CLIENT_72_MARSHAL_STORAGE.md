# D-01b3：Marshal原生转换与明确分配存储模型

2026-11-06。此项限定完成固定CN72输入下的`Marshal.PtrToStringAnsi`完整返回研究：非空UTF-8转换、UTF-16写入及返回走原生代码；CRT与托管GC分配仍是明确存储模型。没有把离线成功称作完整native allocator、实机Marshal命中、SDK认证或D-02通过。

## 两个真实TLS来源与原卡点

在原始客户端自然出现的两次首次C++异常暂停处，实际只读捕获了Global、UTF8缓存及事件线程/主线程选择上下文。两个UTF8缓存相同，虚槽11/19/34匹配既有目标地址；GC与CRT分配器均非零。没有为观察主动调用任何目标函数或输入账号，也未安装新DLL。完整动态尝试、退出原因与事件缺口由主线单独审核；本项只引用已捕获的上下文事实。

| 来源 | 固定目标指令链 | 实际观察与范围 |
| --- | --- | --- |
| TLS数组与index | `GS:[58]`、`145EEF080` | 实际从当前选择的TEB读取；本次index为0，不要求未来运行固定为0 |
| 托管字符串分配器 | `14052FCB4`后读取TLS storage `+19D0`，传给`140563CD0` | 两个选择上下文均非零；既有SeedString存储夹具只初始化了这一格 |
| CRT分配器 | `141CA7930`读取同一TLS storage `+1A50`，传给`141CAD0CC` | 两个选择上下文均非零，和`+19D0`不是同一格；原离线环境漏供此输入，故`141CAD123`从RCX=0读bucket |
| CRT页表 | `141CA72AC`从`145EE5B80`读取页表，再按指针索引 | 真机指针非零；只把malloc替换成普通虚拟堆地址，会在size-query的`141CA72CF`因缺页表停住 |

上述四个相关窗口各32字节都重新核对真实进程内存与固定EXE相同。原始TEB/TLS/分配器/页表地址与内存只留忽略`d01b3-natural-context.json`、`d01b3-context-detail.json`；不复制真实heap快照来伪装native初始化。

调试器表达式有一个已核实的区别：同发布tag源码中`teb()`来自当前选择线程的`hActiveThread`，`tid()`来自最近debug event的`dwThreadId`。因此`switchthread`后TEB变而`tid()`不变，不能据后者把两个选择上下文误认成同一线程，也不能把它当成切换失败。[固定2026.05.27源码](https://raw.githubusercontent.com/x64dbg/x64dbg/2026.05.27/src/dbg/exprfunc.cpp)

## 模型和真实执行边界

`tools/trace_client72_marshal.py`每轮重新校验EXE/global/startup三哈希并提取结构，核对目标声明确为Marshal的PtrToStringAnsi，执行入口`159C21C90`。非空分支实际执行C-string长度计算并tail-jump到`14052FBF0`，随后UTF-8解码、UTF-16存储及返回均原生执行；不是用Python直接替换输出。

新增`TrackedCrt72`只提供一个明确的单线程CRT上下文标识，写入fixture TLS `+1A50`，并在三个边界记录分配所有权：

- `141CAD0CC`：接收已观察的context、16对齐、offset0、非zero-fill分配请求，返回有记录的虚拟堆存储。
- `141CA72AC`：只接受已记录指针，返回fixture请求字节数；这是声明的usable-size/accounting输入，不是恢复了真实heap rounding。
- `141CBFE48`：按已记录指针释放，未知ownership不伪造成功。每组完成后账本必须为空。

size-query在此路径的调用返回点`141A30DA7`把结果用于原生分配统计。原生CRT初始化、bucket/page-table算法以及真实分配尺寸舍入都未执行；不能将这一模型的请求字节数用作真实堆布局证据。托管字符串对象仍由原有`SeedString72`的`140563CD0`存储模型分配，完整GC及生命周期也未验证。

最初两个失败都保留：无CRT来源时长URL停在`141CAD123`；只补malloc、未提供对应size-query账本时停在`141CA72CF`。本次用一致的分配/查询/释放存储合同消除这两个已复现的环境缺口，没有跳过转换检查或强改返回值。

## 实际验收

17个原生样本：null、空串、A、28字节URL、含中文及emoji的33字节UTF-8、内嵌NUL，以及7/8/15/16/31/32/63/64/255/256/1024字节边界。原始null返回null、空串返回长度0；内嵌NUL只转换前缀；emoji按两个UTF-16 code units计数。

各组结果与独立Python UTF-8→UTF-16LE逐字节相同；7个UTF-16单位及以下保持inline，8单位起走记录的CRT存储。长样本均有分配、size-query、释放三事件，账本最终为空。此处验证的是这些样本的真实路径，不泛化所有输入或错误编码行为。

最终两轮trace51,697字节及摘要21,451字节分别逐字节一致；摘要SHA-256为`fbc69a24c0406852480a54bf558edeab0b23471f3ceb68d7811dd834f7154f48`。6个新增范围/结果/ownership回归及seed/RSA关联测试共12项全部通过，失败/错误/跳过0。未改运行文件或安装补丁，无服务重启要求。

```powershell
python tools/trace_client72_marshal.py --client $env:GAME_PATH --output local/migration72/evidence/marshal-storage-trace.json --summary-output protocol/cn-7.2/marshal-storage-observations.json
python -m unittest tools.test_client72_marshal tools.test_client72_seed_crypto tools.test_client72_rsa_fixture -v
```

第二次使用忽略目录的`marshal-storage-repeat.json`与`marshal-storage-summary-repeat.json`，逐字节比较；证明文件`marshal-storage-determinism.json`和日志`marshal-storage-*.log`同在忽略目录。

实机自然非空Marshal/factory调用和完整返回仍未取得。单硬件观察未命中，后续退出不据状态码猜原因；对应动态摘要先留`local/migration72/evidence/d01b3-dynamic-summary-draft.json`供主线审核。下一项应解决可观测启动/终止链或在已确认自然停止状态继续只读观察，不能通过主动构造目标调用、跳校验或宣称此存储模型等于完整runtime来解除实机前置。

## 本次三轮动态尝试与准确停止状态

每次新load前都核对同一管理员API会话无目标、全系统无现有游戏，固定三文件及原SDK/Astrolabe共5个hash匹配。调试器exe/dll、Automate及其zmq四文件还与固定下载ZIP逐字节比较；两个API监听均是该调试器的loopback端口。每次load后核当前基址、5个入口代码窗口，硬件断点表及DR0/1/2/3/6/7均为空/0。只管理已明确属于本研究的cn72_观察点，其他断点保留。

| 尝试 | 实际观察 | 结论边界 |
| --- | --- | --- |
| 第一轮，5个主模块软件观察点+3终止API | 观察脚本约15.125秒；37个首次C0000005均逐事件核ex.code/addr后con1+普通run；另1个入口断点。捕获EXIT_PROCESS=`80000003` | Marshal/factory/SDK/HTTP和终止API点均未命中；没有终止栈，原因unknown |
| 第二轮控制，仅3终止API、零主模块观察点 | 50秒进度仍running；37个首次C0000005及2个首次E06D7363都逐个交付。60秒预算末请求pause，随后“已暂停”复核失败并保存assert；后来实时API及系统进程核实目标已结束，日志exit=`80000003` | PUB未捕获最后异常/退出事件，未取得计划快照；不能说pause成功，也不能直接说pause导致退出 |
| 第三轮，零主模块观察点，在自然异常暂停采集 | 同样37个首次C0000005、2个首次E06D7363。首个C++异常采集后原样交付，第二个采集后明确暂留未交付；取得本文两份Global/TLS/UTF8上下文 | 这是自然异常时的只读存储证据，没有Marshal调用命中，也不是完整启动成功 |

第三轮的同一目标在只读分析期间暂停了1297.985秒，随后按授权只设置一个slot0硬件执行点观察factory；set/run前再次核同target、相同待交付首次异常、空硬件表/全0 DR，设置后DR0等于已核factory地址、DR7=1，代码字节不变。con1加普通run后数秒，API显示目标结束；PUB仅捕获一个RESUME_DEBUG，未捕获hardware hit、最后异常或EXIT。独立后续日志确认本次exit是`80000004`（STATUS_SINGLE_STEP），**不是**前两轮的`80000003`。不能据这个退出码声称factory曾命中或定位了触发指令。

最终只读核查确认：仍是原API/debugger会话，debugging=false；全系统GAME_PATH目标及本轮旧target后代为0，五个受核客户端文件hash保持。硬件表此时为0，但退出后的空表不能保证下次新target没有DR残留；最新索引`dynamic-active-session.json`已记录无目标，不可沿旧PID或地址run。

这三轮并非严格的单变量因果实验：观察点设置、暂停时长和事件交付时机不同，最后一次尤其有1297.985秒长暂停。没有反调试/完整性/协议失败的独立归因证据，不关闭任何检查，也未强迫验签或其他返回值。控制脚本一次缩进错误发生在load之前，已修正并保留日志；此后pause复核失败及事件缺口也完整保留，不归并为通过。

原始目录索引：`d01b3-{preflight,debugger-source,continuous,control,natural-context,context-detail,hardware,final-audit}.json`及同前缀logs；脱敏详细草稿为`d01b3-dynamic-summary-draft.json`。实际原始路径、PID、字符串、内存和异常参数只在忽略目录。

当前解除条件是取得可靠的自然调用/终止链或解释观察通道末事件缺口，再在新一轮前重新验证目标、断点和DR归属；不能靠重复同类load、改DLL、关闭校验或主动调用factory填补缺失证据。若继续研究调试工具行为，应先把pause/hardware事件交付在独立合成目标中复现，与游戏自身原因分开。D-01b3整体、SDK及D-02仍未完成。
