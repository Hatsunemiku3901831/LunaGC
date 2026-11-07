# D02-R2：只读 SDK 观察原型设计与离线夹具

## 计划和证据边界

目标是验证标准 WinHTTP 调用的最小白名单记录，保持原参数、返回和 `GetLastError`；只输出固定 host 别名、固定方法/路由标签、数值状态及顺序，未知仅长度/种类，不输出 query、headers、body、账号、密码、token 或其 hash。主线原始启动的44次模块快照包含 winhttp、wininet、AccountPlatNative、Astrolabe、HoYoChannel、mhypbase；这只证明采样时模块存在，不证明目标调用 WinHTTP、任何 DLL 初始化成功或 SDK 登录。两次90秒存活不证明完整入场。ETW 的敏感缓冲与覆盖缺口见 [ETW 审核](CLIENT_72_WINHTTP_ETW_PLAN.md)。

未知包括真实 SDK 使用 WinHTTP、WinINet、curl 或 Unity 的哪个调用链、时序、调用线程、异步完成、可合法使用的观察装载路径及客户端校验影响。标准 API 文档与本机 SDK 头只给调用 ABI，不证明自然调用、资源、SDK/Marshal 或进程内生命周期。旧 patch 的代理、ccp、memguard、NEUTRALIZE、swap、异常处理器等均不复用。

候选为独立 C 原型，以 SDK 声明的 `WinHttpConnect`、`WinHttpOpenRequest`、`WinHttpQueryHeaders` 形参/返回类型封装注入的**测试函数指针**，在调用后白名单分类；不实现任何 hook、DLL 装载、代理、网络、目标进程访问或文件替换。专门夹具将原调用替换为自有纯函数，记录入口参数和返回，并故意令观察回调破坏 LastError，校验包装返回时恢复真实被调函数留下的值；比较合成敏感哨兵不出现在输出。接口把可观测范围限定在标准 API，本卡不拦截其他模块或触发真实请求。

最小实验：固定 Windows SDK 头/编译工具/源码/产物 SHA-256；自有 fixture 测已知/未知 host、准确/未知 path、query/fragment/UTF-16 敏感哨兵、GET/POST/未知方法、成功/失败、数值 status 查询和其他查询，同时验证每个参数原样转发、返回及两类 LastError；callback 只接固定枚举/长度/状态，不接原始字符串、headers 或 body。Windows x64 非易失寄存器由独立汇编调用夹具检验，非该范围的 ABI 和目标自然运行保持未知。

失败出口：任何符号/头文件不匹配、参数改变、返回或 LastError 不一致、哨兵泄漏、x64非易失寄存器变化、编译或测试失败均不交付可用原型；仅记录实际失败，不关任何原生校验。真实初始化顺序、装载和原始 DLL 转发、并发重入或性能若成为必要依赖，先以具体现场和独立 fixture 建新卡，不能把本卡 wrapper 当作可安装目标 DLL。此卡不操作游戏/服务/调试器、不改原始五文件、生产、配置、协议或 patch；不要求部署。完成后更新下文实际验证及 hash，由主线审核。

## 实际原型及验证

本卡按上述计划完成静态链接的本地 EXE fixture，不是 DLL 或 hook，三个包装函数只调用注入的 fixture 函数指针。`observer.c` 用 C11 `_Generic` 静态断言三个函数指针类型与所安装 `winhttp.h` 声明完全匹配。固定 host 仅 `sdk-static.mihoyo.com` 和 `127.0.0.1`；固定路由仅 `/query_region_list`、既有离线 HTTP fixture 中的 `/query_cur_region/os_usa`、`/hk4e_cn/mdk/shield/api/loadConfig`。这些是分类夹具，不是自然请求身份。以 `?`/`#` 前缀作严格字面匹配，不解码、不输出任何原字符串。未知请求仅事件类型与长度；已知请求输出固定标签/GET或POST。时间由 fixture sink 用 `QueryPerformanceCounter` 记录，属于观察回调时间，不证明网络时序或跨线程顺序。

三个 API 的各参数指针与整数逐个由实际被调 fixture 比较；调用前 LastError 为 `0x9999`，被调各设不同错误值，sink 故意覆盖，再由包装恢复被调值。成功、失败、数值状态207、其他查询、已知/未知域名、ASCII与UTF-16敏感哨兵均覆盖，记录只有数值。夹具共88断言、0失败；独立 MASM 调用者检查 RBX/RBP/RSI/RDI/R12–R15 保持。此检查只覆盖该外层调用与被测路径，**未检查 XMM6–XMM15、异常/异步/并发或目标真实 hook ABI**；不能据此称完整 GPR/XMM 或实际 WinHTTP 调用通过。两个 Python 测试执行该 EXE，检查输出字段白名单、相对时序、敏感哨兵缺失及上述断言。被调函数未访问网络，EXE 的静态导入清单无 WinHTTP 或 Winsock；CRT 包含自身标准初始化及异常相关导入，不是旧 patch 的异常处理代码。

首次编译因 fixture 变量 `accept` 与系统头的函数冲突失败，`compile-red.log` 保留；改名后另一轮因汇编输出参数写法导致链接找不到对象，已改为 `/Foabi_check.obj`，该轮命令行输出未另存。最终 MSVC x64 19.44.35229 `/W4 /WX /O2 /std:c11` 编译通过。实际固定 SDK `winhttp.h` SHA-256 `45b2ccf904eeaa904e773e90a1e677bd04e41fb57e003f3f398cde0195c2fdd2`；`cl.exe` `fe251ef50a1545b1b0835ee17b1e785459712b38d79e45b5c1d3d28970a36619`，`ml64.exe` `5caf9b734273a9ccb109eb0465581bd6c9fb67fa52a1cd12cc6d40ee12e40d4a`，`link.exe` `a364af801a8539e4324d9489313dbf001d959128451fc99a27b24676bbac058f`。源码、测试、三个对象及 fixture EXE 完整 hash 见忽略目录 `local/migration72/d02-active/sdk-observer/hashes.json`；fixture EXE SHA-256 `fdaba6e01a05ed8176cd9e6f3e7b0bd6f43513a4d0a549d0a8a8f9d1ad153e8e`。`fixture-output.txt`、`tests.log`、`compile-green.log`、`imports.txt` 同目录，仅合成内容。测试入口为 `python -m unittest tools.test_sdk72_observer -v`，须先按上述编译选项生成忽略目录中的 fixture。

本结果不具备客户端装载路径，不绑定真实 WinHTTP，也不捕获账号、任何实际请求或响应。下一步须先由主线审核拟使用的观察方式、具体调用点、装载与校验影响；若不能以不改变客户端原生校验的方式接入，保留阻塞，不借本夹具批准安装。

## 主线独立复核（2026-11-08）

主线已审四个C/header/ASM冻结源及边界，使用新`build.ps1`在独立`local/migration72/d02-active/sdk-observer/main-third/`实际编译，再设置`SDK72_OBSERVER_FIXTURE`指向新EXE运行两项Python检查，通过。该EXE实际88断言/0失败；本次SHA`ed0354e693923cdf722f449f53c223613770202436776da64232f863c2ddb5cb`，与旧构建产物不同，不宣称可重现同字节。首次第三编译对应四个原冻结源，提交前EOF整理见下文；build脚本SHA`bc2a8b5f17f444557c9c0e68ac799f77ae4c9fae5892f24e996f2627a0d090ce`，当前test SHA`a2671403befb2bde973b8ba44245c363dddf1c64c7f01a2e389b6ff3f910e585`；旧hashes.json的test条目是修改前历史值。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/sdk72_observer/build.ps1 `
  -OutputDirectory local/migration72/d02-active/sdk-observer/main-third
$env:SDK72_OBSERVER_FIXTURE = (Resolve-Path local/migration72/d02-active/sdk-observer/main-third/fixture.exe).Path
python -m unittest tools.test_sdk72_observer -v
Remove-Item Env:SDK72_OBSERVER_FIXTURE
```

输出目录须新建且位于checkout的local内；脚本经vswhere发现MSVC，仅编译合成fixture，不安装DLL或访问目标/网络。主线日志`main-third-build.log`、`main-third-tests.log`。本项只研究工具/测试/文档，未更改运行服务器/Proto/patch/config/资源，无需部署；真实SDK ABI/装载/异步覆盖仍未知。

Git检查发现三个C/header文件的末尾多余空行；先将原冻结源逐字节备份至`sdk-observer/frozen-source-before-eof-cleanup/`，证明只去末尾换行，ASM不变。最终源重新在`main-third-clean/`独立编译、88断言/2守卫仍通过，EXE SHA`bdc1c49eb02a471180a184b2608452619e4fc7335bc069bb0748c57f1c1604ae`；当前observer.c/header/fixture.c hash分别`143acdff…aee123`/`5e45cfd7…a44491`/`107b5561…f95010`，完整新旧hash及逐字节内容范围proof在该备份目录。两次主线产物/日志均保留，不把原hash写成最终源身份。
