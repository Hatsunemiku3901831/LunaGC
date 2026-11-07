# D02-R2：SDK transport 的固定输入离线研究

主线2026-11-08已审真实PE/原生工具、模型及来源边界，独立第三回放`main/sdk-transport-third.json` SHA`3650c789f07778ee54a705d517e4ba88914231b0e426bfc53d9202af8edba9fb`与双跑逐字节相同，11项检查通过，证明`main/followups-main-proof.json`、日志`sdk-transport-main-tests.log`。接收的是11磁盘模块/2812导入及14离线样本，不是自然setopt调用或SDK登录；后继另追login worker/状态持有者，不安装旧patch、DLL或改变校验。本研究无生产改动，不需服务部署。

## 攻坚计划（2026-11-08）

目标：从已自然加载的11个客户端模块中分清账号登录、基础初始化及dispatch的接口身份与实际运输候选；在不修改原生校验、文件或进程的前提下，给主线可审的最小观察规格。

证据：固定profile五输入、原始无调试启动 `plain/run-elevated2.json`（SHA-256 `2643bfe4006acaf3a7a97c1c0e681d5e71ae8a3c27f048cc31d87dc273adcf9f`）、普通/延迟导入、导出、目标代码和本机独立PE解析。该启动的模块路径仅证明当时自然映射；本次磁盘哈希不证明当时内存字节与磁盘相等。

未知：SDK实际自然请求、动态GetProcAddress名称、异步返回与生命周期、账号与dispatch的完整链；微软ETW零事件不能排除WinHTTP。已有WinHTTP三函数包装不证明真实账号SDK使用三函数。

候选：AccountPlatNative嵌入curl及Winsock；Astrolabe的WinHTTP；gmesdk的WinINet；Unity/EXE或其他模块的动态接口。先由PE目录与精确调用指令逐步排除不符项，静态字符串只作种子。

最小实验：核五输入及11自然模块磁盘身份；独立比较普通/延迟导入并保留ordinal；从SDK导出和curl候选入口分析直接调用及ABI；只在明确模型边界内离线执行短小原生段，覆盖非零、64位和错误输入。所有原始产物留 `local/migration72/d02-active/sdk-transport-astra/`。

修改范围：仅本专题、`tools/client72_sdk_transport.py`、`tools/test_client72_sdk_transport.py`。不启动/attach/hook/inject/改断点，不更改游戏、补丁、配置、生产、网络、服务或数据库，不占共享构建、不提交Git；主线负责融合及共享索引。

失败出口：输入变化、解析歧义或模型越界时报告准确范围；没有自然调用身份时列唯一首缺输入及下一最小实验，不猜路由、不重跑旧HW/ETW实验、不借7.1地址直接安装。研究片段完成不等于D-02。

验收：固定source/input hashes，两次全新Python进程确定性、独立目录第三命令、针对真实风险的测试与清楚的静态/自然/模型边界。离线工具与文档无需部署。

## 本批结果与身份边界

**11模块的普通/延迟导入已交叉闭合；账号SDK的精确curl兼容入口及14组离线原生样本已验。自然账号登录→该入口、真实HTTP请求/响应及D-02仍未验。** 本批没有运行游戏、调试器、网络请求或服务；没有安装DLL，也没有变更任何原始客户端文件。不能以本研究批准旧patch。

`profile.json` SHA-256 `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d` 的五个文件全部实核匹配，详见 [SDK活动合同](CLIENT_72_D02_SDK_ACTIVE.md)。44次自然采样中151个不同模块仅取客户端根路径下11个；下表是**本次磁盘**身份。历史采样含模块名/路径/基址/大小，不含这些已加载代码的逐字节身份；两者不混同。

| 客户端相对路径（Plugins简写为`YuanShen_Data/Plugins/`） | SHA-256 | 直接网络导入 |
| --- | --- | --- |
| `YuanShen.exe` | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` | Winsock49；WinHTTP仅代理配置1 |
| `MHYPBase.dll` | `4b8dff048c1db0a359a3717a17be55f02ea9884d6d573c330399fb54f1968f45` | Winsock29 |
| `Plugins/AccountPlatNative.dll` | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` | Winsock30；WinHTTP代理相关5 |
| `Plugins/Astrolabe.dll` | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` | WinHTTP请求链12 |
| `Plugins/HoYoChannel.dll` | `de55c7983f83f737f9570e04cbabc395f7ae0c7c98def76c97135cd187e57cd7` | 无上述普通网络DLL；四个delay DLL |
| `Plugins/Mmoron.dll` | `79715e6b257233d4779d8c7ab9d749377636e973eef350dea4d91099301c26d0` | 无上述导入 |
| `Plugins/TencentGME.dll` | `8d06b104314ec75014aa5728440d7c56a5cdf15d32a5dd9d39b9143d4b7934fb` | 无上述导入 |
| `Plugins/gmesdk.dll` | `6bb1b0b59a0d0aec8b23c5d12356332c25c1d92b4b1fd9a739472045a26e4c00` | Winsock27；WinINet13 |
| `Plugins/MTBenchmark_Windows.dll` | `27d5910979ade663bed753d0ebb759bf3efea6b7133976ba8092a7d7bf25d8ca` | 无上述导入 |
| `Plugins/telemetry.dll` | `f7457e733ed047112c1929d6b5c8c7c7f199d19b517ed1aca81602b2a20a78d5` | Winsock34；WinHTTP代理相关5 |
| `Plugins/ZFProxyWeb.dll` | `0754391949106c03507cbfa09403e0e899112299cb75ff2de71dc378abe4018f` | 无上述导入 |

独立手写PE解析与REA `inspect_pe_imports` **2812行逐字段/顺序相同**：2726普通导入、86延迟导入，182项保留原始ordinal，不能把pefile自动补的ordinal名称说成目标named import。DLL名、name/ordinal、ILT/IAT RVA、normal/delay分别比较。HoYoChannel唯一delay组为`steam_api64.dll`8、`play_pc_sdk.dll`7、`Microsoft.Xbox.Services.GDK.C.Thunks.dll`43、`EOSSDK-Win64-Shipping.dll`28；这不证明本轮加载这些依赖。SDK自身delay目录为空。

账号SDK五个WinHTTP导入准确是 `WinHttpGetProxyForUrl`、`WinHttpSetTimeouts`、`WinHttpCloseHandle`、`WinHttpGetIEProxyConfigForCurrentUser`、`WinHttpOpen`，没有Connect/OpenRequest/SendRequest；该导入组合也不能单独证明其实际用途只有代理。Astrolabe才直接声明这三项及ReceiveResponse/ReadData/WriteData/QueryHeaders/AddRequestHeaders/CrackUrl等；gmesdk另有WinINet。模块加载或系统WinHTTP存在均不证明账号请求走哪条路径。

REA完整原始包 `rea-imports-exports.json` SHA-256 `0c47927148a7cc5d07f2596f0b464e7d04061fad80cfeee612604b6e3acd6c9a`。账号import Evidence `ev_d1abb311f1f524df83f29e287b6daade674eb90711965cc702f499290a123dac`、SDK export `ev_99b71aa41d39e92f1b4c0b224d0c5d1bf1bfc54dfbc117d100cc1575e783e9db`；其余10项ID均在每模块结果。REA仅解析目录，没有调试或运行。

## 动态解析与精确SDK接口

工具在每模块可执行section搜索特定RIP相对MOV/LEA/CALL/JMP引用，再从`.pdata`开始反汇编核指令边界；这不是完整控制流或所有动态解码的穷尽证明。SDK得到74个GetProcAddress引用点，其中56点的前7条指令内有不跨call/jump的RDX字符串字面量（例如SetThreadDescription、RtlGetVersion、InitSecurityInterfaceA、WSA事件和CRT API），18点保持未知。`1F976C..1F980B`九处名称来自对象`+20..+60`，把返回写`+68..+A8`，不能猜成HTTP函数。其余未知点与窗口逐项保留在raw。SDK对WinHttpConnect/OpenRequest/SendRequest、HttpSendRequestW、curl_easy_setopt/curl_easy_perform的ASCII-NUL及UTF16-NUL精确搜索均0；这只是指定编码的字面量缺失，不能排除加密名、其他解析器或函数指针。

托管声明每次直接从五固定输入重新解码，未依赖旧business-methods缓存。`MiHoYo.SDK.AccountPlatNative`类型9798与`NetworkManager`类型4362参数原名/类型保留。SDK导出目录内部DLL名称为`MiHoYoSDK.dll`，磁盘选择仍是固定`AccountPlatNative.dll`，不能按内部名称选另一个文件。

| 接口 | EXE注册VA → SDK RVA | 真实静态参数/转发 |
| --- | --- | --- |
| `HoYoSDK_InitGlobalEnv` | `146D81AD0` → `13CE60` | 四个string声明依次是curDir/productName/companyName/dataDir，第五int32 clientType；EXE经`140534480`转换四个string，解析精确library/export名，再按Win64前四寄存器、栈第5参调用；SDK转单例vtable slot0→`132650` |
| `HoYoSDK_Init` | SDK导出`13CEC0` | 单个输入包装为callback对象后slot1→`132360`；本批未确认自然消费者，不据名称认定已初始化 |
| `HoYoSDK_InvokeReturn` | `146D81540` → `13CF10` | 两个string为strFuncName/strArgs；SDK清单例`+DC`后tail-jump slot2→`132AE0`；EXE保存64位RAX，释放两个转换后的临时参数，再原样返回RAX。返回对象所有权/实际Marshal仍未验 |
| `HoYoSDK_Invoke` | `146D81A00` → `13D0C0` | 两个string、HoYoSDKNativeCallback、int32 callbackIndex；EXE转换string/委托后用RCX/RDX/R8/R9D调用；SDK将64位callback和32位index装入临时functor，清`+DC`后slot4→`133020` |
| `HoYoSDK_ReleaseResult` | `146D81600` → `13D1D0` | 参数声明`IntPtr lpBuffer`；本批只核声明/导出，没有执行释放或推断其他返回值的释放方法 |

单例工厂`13CDD0`经`131EF0`构造函数将vtable RVA`694980`写对象；其COL RVA`6DD018`的MSVC RTTI精确名为`.?AVMiHoYoSDKDll@mihoyo_sdk@@`，上表slot来自该表的qword，不是方法名猜测。`Invoke`目标`133020`确有对action字符串的长度/树查找；完整动作注册→具体login worker→异步网络调用仍未闭合。`login_login`、`login_by_account`、`login_account_plat`、AccountPlatAccountLoginJobber、MDKAccountLoginJobber、ComboLogin各字符串的真实RIP引用已固定，但字符串或日志引用不直接等于自然业务身份。

## curl兼容入口与四个caller

固定SDK首选基址`180000000`；本卡精确入口 **RVA`41E6F0` / preferred VA`18041E6F0`**，`.pdata`区间`[41E6F0,41E71E)`共46字节。运行时只能以已核该模块实际基址加RVA定位。旧patch的`41AE90`注释不适用；本卡不变更旧patch。

```text
89 54 24 10 4C 89 44 24 18 4C 89 4C 24 20 48 83 EC 28
48 85 C9 75 08 8D 41 2B 48 83 C4 28 C3 4C 8D 44 24 40
E8 87 9D 00 00 48 83 C4 28 C3
```

二进制没有该函数的符号/导出名；RVA`611540`有`libcurl/7.54.0`字面量。**curl_easy_setopt是结合目标变参封装、option分派/存储、标准常见数值及HoYoNetworkEngine调用所作的函数语义推断**，不是静态符号或自然调用证据。后文的setopt均只指上述完整身份。未从外部取得另一版本源码来替换目标事实。

入口按Win64将EDX保存到入口RSP+10，R8/R9保存到+18/+20，分配28字节栈；RCX=0原生返回EAX43，否则R8指向原始RSP+18的va_list槽，直接调用RVA`4284A0`。RCX为64位opaque handle，EDX为32位option，第三个可变参数R8为该option决定的值，不能统一解释成32位长度或字符串。10002对应NUL结尾字节串指针，无显式长度参数；原生strlen读至首NUL，本卡验证UTF8字节原样复制，不将其称为SDK完整编码策略。

四处caller函数内共有34条**从`.pdata`边界验证**的直接call：

| caller RVA | call数量 | 精确静态来源及顺序 |
| --- | --- | --- |
| `2F0940` | 9 | vtable`6BF300` slot9；先`41E620`获handle→`2F3360`通用配置→`2F0A42`设10002 URL→10023/10015/60/20011/10001/47/13/10029等→`2F0B54`调用`41E6E0` |
| `2F3360` | 11 | 上述builder直接调用的共同配置函数；不是第四个独立HTTP请求，也不是账号登录入口；不含10002 |
| `2F3940` | 7 | 同vtable slot10；`41E620`→共同配置→`2F3A24`设10002→10023/20011/10001/80/13/10029→`2F3AF9`调用`41E6E0` |
| `2F5AE0` | 7 | 调用者`2F58B7`、`2F9589`已定位；`2F5B65`设10002（从字符串对象inline/heap分支取指针）→20011/43/20056/10001/10057/10029；完整调度和业务身份未知 |

vtable`6BF300`前的COL`6EC748`独立链接RTTI`.?AVHoYoNetworkEngine@mihoyo_sdk@@`，故前两个builder的类身份比“附近有HTTP字符串”更强；slot名GET/POST仍是option行为推断，不能据此把它们直接标成账号/dispatch。`41E6E0`原生仅`xor edx,edx; jmp 41E730`，它在setopt后被调用这一静态顺序已固定，完整perform/网络/返回不在本批执行范围。EXE的Unity NetworkManager及GlobalDispatch仍是独立路径，不能用这些SDK样本代验。

## 原生样本、存储与错误

14样本都从原始46字节入口进入并执行真实`4284A0`分派；null句柄在入口前置分支返回。**未截获或替换setopt validator、字符串setter、返回码或跳转表。** 唯二模型是CRT分配入口`4978C0`和释放入口`48F4F0`：提供有所有权账本的合成存储或分配失败，所有模型事件均单独输出。CRT/OS初始化、真实heap、线程、网络、TLS、证书、GC及自然CURL初始化未执行；句柄是离线零初始化布局，不是假称真实curl_easy_init。

- 非零option13值37原生写`handle+2C8`为37000；option30120的`9007199254740993`和-1完整写`+210`，option10001保留64位指针`123456789ABCDEF0`至`+1C0`；未知option999999原生返回48；null handle返回43且不进dispatcher。
- option10002的真实分支`42930D`→`42DE80`→`4A2E30`，native strlen与`4A5710`复制均执行。ASCII、空串、UTF8、内嵌NUL、1024个后缀字节共五样本，另加`not a URL`语法样本，均返回0。**setopt此处复制存储，不验证URI语法，也不执行TLS/签名校验。** 后续perform是否拒绝无效URL未知。
- URL副本写入`handle+628`并同步`+7C0`；指针不同于调用方R8，分配尺寸为首NUL前字节数+1。改变输入buffer后副本不变；null URL调用原生清除并释放既有存储。替换URL会先释放旧值并复制新值；分配失败走原生`42DEB3`返回27并留下空slot，不把旧值继续保留。上述两处存储偏移只适用于此固定SDK。
- 不合成无效指针的“安全返回”；NUL/可读范围属于调用方原生合同，本卡没有给未知地址添加验证或异常吞掉。观察器若不能安全分类一个输入，应只记录无法分类，原参数照常传给原函数。

原型探索最初把真实CRT一路执行到IAT `HeapAlloc`时因没有真实OS绑定而停止，该失败没有改写为成功。最终夹具将模型明确下移到两处存储边界，字符串长度/复制及27错误分支仍原生执行；不是修改原DLL或让真实客户端使用模型。

## 主线可审的最小观察规格（未实现、未安装）

唯一首缺输入是**一次自然SDK的`base+41E6F0`调用事件：已核内存模块身份、返回地址所属caller、EDX10002及脱敏固定host/route分类**。有该证据才能把精确静态入口提升为这轮自然请求的候选出处；即使命中也不证明请求发出、成功或登录验收。

下一最小实验由主线另审装载方式和对原生校验的影响后决定，限定SDK这一地址；不能把“磁盘五文件未改”当作内存inline hook必然不影响校验的证明，也不准复用旧patch的其他模块/handler。若无合法装载/观察方式，该前置如实未满足。

合同要求：

1. 同时核磁盘hash、实际模块基址和入口46字节；这是一次目标读取，历史模块snapshot不能代替。原始代码前14字节恰为三个home-slot MOV，没有RIP相对寻址；只说明指令边界，不证明任意detour实现安全。覆盖区长度、转移/回跳、展开/异常、线程装载和卸载需要实现方自己的具体审计。
2. 完整保留RCX/RDX/R8/R9、栈可变参数、原函数执行顺序、返回EAX以及调用前/后的LastError可观察状态；单独测非零、高64位、null、unknown、分配失败、嵌套/重入与退出。不能重调用原函数、主动构造登录调用或把模型成功带入真实目标。只观察10002，其他option原样通过；不改URL或任何校验。
3. 指针内容仅在调用期间做有界的**内存分类**，不输出原字符串、query、header、body、账号/密码/token，亦不输出它们的hash。共享/磁盘只允许预审白名单固定host/route代号、option、caller模块相对RVA、状态及时间；未知路径只记固定unknown。长度/容量只是采集上限，不得截断后传回原函数。URL含`?`/`#`后的内容不进入日志；不要把动态路径片段当白名单。
4. entry时调用者URL尚有效；原函数返回后不能按原指针解引用，也不能自行追踪SDK存储生命周期。setopt返回0只是选项已接受；自然perform返回、状态码、回调和账号动作仍需独立后继合同。当前既不建议重定向三组未知builder，也不给生产route列表。
5. 实验有界一次；入口字节/模块身份不符、原生校验异常、退出或未命中则保留准确状态并停止，不换旧地址、不扩大hook、不将无事件解释为不用curl。

仍可独立离线推进的明确后继：从`MiHoYoSDKDll::Invoke`动作注册树及`login_login`/`login_account_plat`注册位置，核回调对象/vtable与AccountPlatAccountLoginJobber、HoYoNetworkEngine slot9/10的完整关联；另外解析对象驱动的九个GetProcAddress名称来源。它们是准确的离线缺口，不全部归于实机。本批已经冻结源；后继应另卡承载。

## 实际验证、冻结身份与复现

两次全新Python进程 `final-first.json`/`final-repeat.json` **逐字节一致**，SHA-256均`3650c789f07778ee54a705d517e4ba88914231b0e426bfc53d9202af8edba9fb`。每轮都重新核五输入、11模块及REA输入，执行14组目标原生样本。11项测试通过（5项独立合成PE错误/ordinal/delay/伪call边界，6项实际报告/source/ABI/metadata守卫）；不是11次实机请求。日志为`final-first.log`、`final-repeat.log`、`tests-final.log`。

冻结source SHA-256：`tools/client72_sdk_transport.py` `43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237`；test `e90bb87414ee3b5c7159938ec3d38960b78eefdfe2b58123346df2c5e793b3db`。依赖`extract_client72.py` `7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d`、`extract_client72_signatures.py` `ee92527f3607be774c28be694d6f57e9eed3aa40068b7b584979e27b6470c349`；本机Capstone5.0.9/Unicorn2.1.4。早期research-v1至v4为探索版本，只认最终source/results，所有原始文件保留在本卡忽略目录。

独立第三命令如下；`third.json`必须此前不存在，工具拒绝覆盖或写出checkout的local目录。该命令重新执行离线原生片段，不启动游戏或发请求。

```powershell
$sdkRoot = & tools/game_path.ps1
python tools/client72_sdk_transport.py --client $sdkRoot `
  --natural local/migration72/d02-active/plain/run-elevated2.json `
  --rea-bundle local/migration72/d02-active/sdk-transport-astra/rea-imports-exports.json `
  --output local/migration72/d02-active/sdk-transport-astra/third.json
$env:SDK72_TRANSPORT_REPORT = 'local/migration72/d02-active/sdk-transport-astra/third.json'
python -m unittest tools.test_client72_sdk_transport -v
Remove-Item Env:SDK72_TRANSPORT_REPORT
Get-FileHash -Algorithm SHA256 tools/client72_sdk_transport.py, tools/test_client72_sdk_transport.py, `
  local/migration72/d02-active/sdk-transport-astra/third.json
```

本批只有研究工具/测试/文档，无需便携构建或部署；客户端验收增量为0。子代理按主线边界不提交Git，融合提交与Project/Harness由主线处理。D02-R2以及D-02保持未完成。
