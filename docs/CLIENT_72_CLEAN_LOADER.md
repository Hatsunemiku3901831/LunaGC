# F04-LOADER-CLEAN：独立透明加载入口

## 实施前计划（2026-11-08）

目标是将实际 DLL 加载与已有 SDK key/信任核心分开验证，形成一个可审阅的独立入口。现有 `patch/src/lib.rs` 默认启用未验的 ccp/memguard、proxy neutralize、swap 等路径，不能作为这次加载许可。新入口只透明转发准确原 Astrolabe 导出，并对当前进程已加载的 SDK 做有来源的身份/入口窗口核对；本子项不安装 hook、不启动网络、不绕过游戏或协议校验。

输入为本轮固定五文件及 patch 已验核心/ABI/原生合同，原 DLL SHA `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`、SDK SHA `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。先从实际 PE 导出表固定全部名称/ordinal，再设计转发；不能复用旧 proxy 的函数名假设或返回成功桩。加载来源、模块路径、磁盘身份、模块基址/大小和准确内存窗分别记录；磁盘 hash 不等于全部内存一致。

范围仅新增独立 proxy/owned fixture 的源码与本专题，默认模块/现有 lib/proxy/安全或 SDK 核心/依赖/安装器不改。全部产物、日志、机器路径与导出表 raw 留专属 `local/migration72/d02-resume-20261108/clean-loader/`。具体允许文件与 Cargo 构建由主线统一调度，不用“禁用列表”夹带旧入口；不启用 CcpBlocker、memguard、swap、NEUTRALIZE、异常处理器或未验模块。

验收先在自有 DLL/调用夹具完成真实 Win32 LoadLibrary/导出转发、参数/返回/寄存器/LastError 保持、拒绝错误文件/窗口、正常生命周期及编译产物核对。生产固定身份不可被 fixture 输入放宽。然后主线才为真实客户端准备可回退的安装/归属/采集方案；这次实施不修改客户端，真实加载另取用户 ready，不能把 owned fixture 记成客户端验收。

未知是真实客户端加载/完整性响应、加载时序、SDK 当前内存窗口及生产生命周期。保留这些实际失败；若系统/客户端合法校验拒绝，不通过替换磁盘、修改校验返回或隐藏模块求通。R2-KEY 同核心原生桥接正在主线第三复验；将来启用 key/trust/route 还需各自合同及对应回归，本子项不擅自启用。

停止条件为固定输入/导出漂移、原库或窗口身份不符、透明性失败或未闭必要调用边；保留准确失败和解除条件，不假转发成功。主线统一 Git/Gradle/Cargo/DB/端口/客户端/调试器/安装。父 R2/F-04/D-02 保持未完成。

## 真实加载观察准备

主线已编译独立production/owned DLL、fixture和三种自有mock；首个真实Win32夹具通过94次baseline、188次透传比较、47名称/ordinal及6个binder拒绝，原始日志保留。独立重复和异常拒绝矩阵由子代理继续检查，完成并审核后才进入以下实验。

本轮只回答“零hook的独立proxy能否正常加载并转发原Astrolabe，SDK是否实际加载且Crypto原窗相符”。不启用key/trust/route、不输入凭据、不连接本服。新主线观察器`tools/observe_client72_clean_loader.py`复用已验CreateProcess/模块枚举/进程身份框架，不复用原始五文件profile去假装proxy仍是stock：四个其余输入继续精确匹配profile；Astrolabe.dll必须为审核构建的proxy，Astrolabe_orig.dll必须为原固定SHA。两者与profile的区别明确记录，不修改profile。

实验前核当前GAME_PATH、无客户端/调试器、原Astrolabe及SDK哈希、归属、编译artifact/来源/导出；将原Astrolabe逐字节备份到专属忽略目录，并确认不存在待覆盖的Astrolabe_orig.dll。只复制固定stock到该orig名字，再将审核proxy复制到Astrolabe.dll；不碰其它客户端文件。脚本与构建manifest固定hash，所有机器路径留private。新用户ready后才以普通CreateProcess自然启动，观察90秒模块/存活，无debug flags或目标函数调用。

观察末尾只在同一PID/creation/image仍活且模块路径/大小/磁盘身份相符时ReadProcessMemory：proxy47指针表376B，SDK完整Crypto入口253B及两个准确caller窗各76B，共最多4读781B。指针表地址从实际构建PE各导出`jmp [rip+disp32]`独立推导，并要求47槽是同一连续表；期望目标为当次原库模块base+固定stock export RVA。只保留代码来源hash、计数、模块身份与相等bool，不读请求、账号、token或任意堆对象。未加载、早退、短读、target漂移均保留实际失败，不重试猜地址。

采集完成请用户从窗口正常退出，OS核无目标后按备份hash恢复Astrolabe.dll，核恢复原SHA；只移除本实验新建且仍等于原SHA的Astrolabe_orig.dll，备份和proxy产物保留。若目标仍活、文件变更或归属不明，不覆盖/清理，报告准确待处理状态。若客户端拒绝该透明proxy，保留失败并恢复，不修改完整性检查。用户的前次ready不沿用到本次启动。该轮是加载/绑定实验，不是D-02登录验收。

## 实际固定输入与导出（2026-11-08）

按 reverse-engineer-anything 技能使用 REA 的目标无关 `inspect_native_exports`，只读实际 stock PE；Evidence `ev_ca1d87cbeb282bb7bfc2a2b2f7dbf8288870103b34bc66ca7e40ed3047856bd4`。完整 raw 为本卡忽略目录的 `stock-exports-rea.json`。结果是 AMD64/PE32+、47 个命名导出、ordinal 基数 1、全部 ordinal 1–47，没有无名项或 forwarder。未打开引擎分析或执行 DLL，不需要创建/关闭二进制会话。

| ordinal | 准确导出名称 |
| --- | --- |
| 1 | `Astrolabe_AddDetectionThread` |
| 2 | `Astrolabe_BuriedReport` |
| 3 | `Astrolabe_CrashReport` |
| 4 | `Astrolabe_CrashReportSetCustomData` |
| 5 | `Astrolabe_DisableBuried` |
| 6 | `Astrolabe_EnableAreaSensitive` |
| 7 | `Astrolabe_EnableHttpProxy` |
| 8 | `Astrolabe_GameLogReport` |
| 9 | `Astrolabe_GetCurrentTime` |
| 10 | `Astrolabe_GetLocalTime` |
| 11 | `Astrolabe_GetOccurTime` |
| 12 | `Astrolabe_Init` |
| 13 | `Astrolabe_InstallCrashReport` |
| 14 | `Astrolabe_InstallGameLog` |
| 15 | `Astrolabe_InstallHang` |
| 16 | `Astrolabe_InstallLogPlugin` |
| 17 | `Astrolabe_InstallMemMonitor` |
| 18 | `Astrolabe_InstallReplay` |
| 19 | `Astrolabe_IsLastLaunchCrashed` |
| 20 | `Astrolabe_LogPluginClose` |
| 21 | `Astrolabe_LogPluginDisableCallback` |
| 22 | `Astrolabe_LogPluginFlush` |
| 23 | `Astrolabe_LogPluginSetUploadConfig` |
| 24 | `Astrolabe_LogPluginSetUploadSeperateConfig` |
| 25 | `Astrolabe_LogPluginUpload` |
| 26 | `Astrolabe_LogPluginUploadWithAttachment` |
| 27 | `Astrolabe_LogPluginWrite` |
| 28 | `Astrolabe_OnHang` |
| 29 | `Astrolabe_RemoveDetectionThread` |
| 30 | `Astrolabe_ReplayFightingReport` |
| 31 | `Astrolabe_ReplayReport` |
| 32 | `Astrolabe_SendHeartBeat` |
| 33 | `Astrolabe_SetConfig` |
| 34 | `Astrolabe_SetCustomData` |
| 35 | `Astrolabe_SetFtcSwitch` |
| 36 | `Astrolabe_SetJavaVM` |
| 37 | `Astrolabe_SetLrsag` |
| 38 | `Astrolabe_SetNetworkStateFunction` |
| 39 | `Astrolabe_SetReportEnv` |
| 40 | `Astrolabe_SetWindowThreadId` |
| 41 | `Astrolabe_Start` |
| 42 | `Astrolabe_StartAcc` |
| 43 | `Astrolabe_StartReport` |
| 44 | `Astrolabe_StartWithoutReport` |
| 45 | `Astrolabe_StartWithoutReportAndTime` |
| 46 | `Astrolabe_TimeDestroy` |
| 47 | `Astrolabe_UploadFileByChunks` |

1/40 的原 RVA 均为 `82330`，29/32 的原 RVA 均为 `82E10`。这些原库别名保留；不能把 47 个名称等同于 47 个互异函数地址。旧 build.rs 的名称恰好相同，本卡仍以此次实际表而非旧“7.0”注释作为依据。

离线 Python 工具重新校验本轮清单全部五输入，stock/SDK 分别再次命中本页计划中的固定 SHA。SDK `SizeOfImage=0x932000`；准确 `3950C0..3951BD` 共 253B，SHA-256 `634a8f823f6f84675977f867ec3e114c6eed100f68be81bb49e2add2e0820b8c`，与已验 Crypto 卡的完整窗口一致。这里只证明磁盘身份和已研究窗口，未读取真实 loaded SDK。

## 独立源码与透明边界

新增入口是 `patch/examples/sdk72_clean_proxy.rs`，只引入本卡 `sdk72_clean_proxy/` 下的 core/table/assembly。它不引入父 lib.rs、ModuleManager、proxy.rs、CCP、swap、memguard、VEH 或 disable-list；没有 hook、网络、控制台或后台线程。源文件均为新增；父现有 source 未改。patch 下本轮再次检索未发现额外 AGENTS。

- DllMain 是独立 assembly，只在 PROCESS_ATTACH 保存自身 HMODULE 并返回 TRUE；不调用 API、不加载原库或 SDK。
- 47 个导出各自直接 JMP 至槽。首次槽指向 cold stub：保存全部 GPR、XMM0–15、RFLAGS 和原 LastError，提供真实 Win64 shadow space，进入一次严格绑定，恢复后 tail JMP。成功后所有 hot 槽均为原库地址，只执行一个 JMP，不猜测各原函数签名。
- 生产首次绑定从 Windows 返回的自身 DLL 绝对路径取得同目录 `Astrolabe_orig.dll`，先核固定 stock SHA，再真实 `LoadLibraryExW`，使用 DLL_LOAD_DIR/DEFAULT_DIRS 搜索依赖；验证 Windows 实际模块路径、基址/尺寸，逐项按名称和原 ordinal 解析并比较，全部完成后才发布表。成功引用保留至进程退出，proxy detach 不卸载原库。
- 实际必要失败使用固定异常码 `0xE0720001`、EXCEPTION_NONCONTINUABLE，两个参数为分类和原 Win32 error；stderr 只写固定诊断及这两个数字。没有 missing()/零返回或成功桩，也不装异常处理器。主线已明确接受此失败出口；客户端若拒绝新 DLL 的真实身份仍需保留失败，不隐藏模块或换盘求通。

生产新增一个明确诊断导出 `Sdk72CleanInspectLoadedSdk @48`，不属于 stock 导出；前 47 个名称/ordinal 必须逐项一致，产物审查同时拒绝未知额外导出。诊断只用 GetModuleHandleW 查已有 AccountPlatNative.dll，核 Windows 模块实际路径等于 proxy 同目录目标、实际基址/SizeOfImage、完整磁盘 SHA，以及 ReadProcessMemory 得到的上述 253B 窗口。返回结构区分路径、磁盘和窗口检查；调用前后 LastError 保持。没有 SDK 时返回分类 10，不自动加载或启动 SDK；窗口通过也不声称全部内存一致。正向真实 SDK loaded binding 本轮仍未知。

错误分类为 1 模块路径、2 原文件读取、3 stock hash、4 LoadLibraryEx、5 实际原库路径、6 ModuleInfo、7 名称解析、8 ordinal 解析、9 ordinal/地址范围不符、10 SDK 未加载、11 SDK 路径、12 SDK 尺寸/窗口范围、13 SDK 磁盘、14 ReadProcessMemory、15 窗口身份、16 诊断输出空指针。17 只属于 owned proxy 缺少测试 hash 的入口。生产常量门没有 fixture 环境变量或可配置身份。

## 自有构建与验证入口

`sdk72_clean_proxy_owned.rs` 是单独的 owned 测试 DLL，使用同一表、cold/hot assembly 和通用 binder，只从测试环境读取 owned 原库 hash。生产入口始终只传 STOCK_SHA，owned 输入不放宽生产门。`sdk72_clean_proxy_fixture.rs` 是 EXE；同目录内嵌资料夹下的 `sdk72_clean_proxy_mock.rs` 是只含自写 assembly 的 mock DLL，没有客户端指令。mock 支持正常、PROCESS_ATTACH 拒绝及缺最后导出三种单独构建。

主线已仅在 Cargo.toml 追加两个 cdylib example 声明：`sdk72_clean_proxy`、`sdk72_clean_proxy_owned`。fixture EXE 由现有自动 example 发现，不改依赖或 lock。所有构建使用本卡专属 CARGO_TARGET_DIR；不构建父默认 lib，不安装 DLL。

先生成固定导出链接输入（拒绝覆盖旧证据）：

```powershell
python tools/audit_sdk72_clean_loader.py --client $env:GAME_PATH --output local/migration72/d02-resume-20261108/clean-loader/offline-new.json --emit-build-inputs local/migration72/d02-resume-20261108/clean-loader/build-inputs-new
```

由主线串行构建，绝对 .def 路径由当前 checkout 解析；production.def 为 47 原名加诊断 @48，owned.def 仅 47 原名：

```powershell
cargo rustc --manifest-path patch/Cargo.toml --example sdk72_clean_proxy --release --locked --offline -- -C link-arg=/DEF:<production.def绝对路径>
cargo rustc --manifest-path patch/Cargo.toml --example sdk72_clean_proxy_owned --release --locked --offline -- -C link-arg=/DEF:<owned.def绝对路径>
cargo build --manifest-path patch/Cargo.toml --example sdk72_clean_proxy_fixture --release --locked --offline
rustc --edition=2021 --crate-type=cdylib --crate-name sdk72_clean_mock patch/examples/sdk72_clean_proxy/sdk72_clean_proxy_mock.rs -C opt-level=3 -C panic=abort -C link-arg=/DEF:<mock.def绝对路径> --out-dir <本卡独立mock目录>
```

deny mock 添加 `--cfg clean_owned_mock_deny`；omit mock 添加 `--cfg clean_owned_mock_omit` 并改用 mock-omit.def。这些都只构建自写 DLL，不 LoadLibrary 真实 stock 或 SDK。主线 production/owned/fixture 及三个 mock 均已编译成功，产物在本卡 `target/release/examples/` 和 `mock-{normal,deny,omit}/`。后续真实 owned 结果见下节，不代表真实客户端加载。

owned stage 将 normal mock 命名为 OwnedAstrolabe.dll，并与 owned/production proxy 放在同一忽略目录，再由主线执行：

```powershell
sdk72_clean_proxy_fixture.exe abi <OwnedAstrolabe.dll> <owned-proxy.dll> <production-proxy.dll>
```

每轮实际跑 94 基线、188 次透传比较：第一条真实 cold 解析、后续 hot 及 47 名称/ordinal，两个 flags 种子、完整 GPR/XMM0–15、RSP/128B caller stack、高 64 位参数/指针、原函数恰好一次、原 RAX/XMM 返回、进入和返回 LastError；正常 unload/reload 验原库进程期持有。通用 loaded binder 正例及路径/hash/尺寸/窗口/范围/缺模块六拒绝；生产诊断只验 SDK 不存在且不自动加载。

失败另用 `reject-child <owned-proxy.dll>` 子进程，真实触发 owned 文件缺失、hash 错误、原库 DllMain 拒绝或缺导出。只有受测 owned child 设置进程本地错误框抑制；不安装异常处理器。核固定异常退出、固定 stage/Win32 诊断及无假返回，不把“进程非零”单独当验收。本轮不调用 production Astrolabe 转发导出；其固定 stock 门经源码及产物审阅，与通用 binder 的 owned 实跑分开，不用 fixture SHA 放宽生产输入。

## 已执行、首次失败与尚待主线

- 已执行只读 REA 完整导出表，以及 Python 五输入/47 导出/253B 磁盘窗口核对；`offline-first.json` 和四份 build-inputs 已生成。没有 DLL 加载。
- Python 8 项独立合成 PE 测试已通过：名称/ordinal、forwarder、无名项、EAT hole、额外项/错 ordinal、畸形/越界，以及构建输入和输出归属。首次无名项使 None/string 排序报 TypeError，原日志 `audit-tests-first-red.log` 和旧源码保留；只改排序键为 ordinal/name 后 `audit-tests-green.log` 全绿。
- 主线 production/owned/fixture 及三个 mock 编译成功；fixture 首编译的 Failure→anyhow 转换 E0277 原样保存在 `build-fixture-main1.log`。仅在 fixture 的 module_path 调用处显式映射固定分类/Win32 数字，不扩 core 错误体系；修后 fixture 源 SHA `d7ede2d02d78c2128d88534b8f7bab6f3f1749f1cb9d7f215271f68f952714b4`。主线首次真实 Win32 运行 `abi-main1.log` 退出 0，限定 owned 验收通过；精确 production 47+诊断48、owned47 的 PE 产物审查为 `offline-artifacts-main1.json`。
- 未覆盖 YMM/ZMM 高半、x87/MXCSR、异常展开、并发卸载、真实 stock/SDK 执行或自然 loader 时序。没有用这些未知扩大代码。真实客户端安装、加载身份/完整性响应、SDK 正向内存窗和完整生命周期均须主线后续新 ready 与实际验收。
- 本子代理没有执行 Cargo/rustc/Gradle、Git、DB、端口、客户端、调试器或安装；在主线追加授权后实际运行已编译的 owned fixtures，结果如下。新代码及自有夹具交主线审核/提交；R2、F-04、D-02 未因此晋级。

## 两个新进程与四个拒绝子进程：实际结果

新增 `tools/verify_sdk72_clean_loader.py` 和独立测试。runner 只接受本卡忽略目录中的已编译产物，先核 9 个 Rust/assembly 源与主线编译后审计完全相同、production/owned 产物 SHA 同主线审计，再检查三个 mock 的准确导出。两个独立 ABI stage 各复制自有产物、启动全新 EXE，production 仅执行 DllMain 与缺 SDK 诊断，不调用任何 production Astrolabe 转发导出。

本次 `verify-agent1/` 实际全部通过：两轮各 94 基线/188 透传比较，公开原始报告均 713B，逐字节相同，SHA-256 `b01c808b3cd4cac8cea36ce03ef3aaf4c6da9f44a0b5c7cc9a1123d32d099f8f`。所有正常 ABI、一次原 callee、真实 LoadLibrary/名称与 ordinal、ModuleInfo/ReadProcessMemory、LastError、正常 detach/reload 及六 loaded-binder 拒绝均按上述范围实跑。

| owned 拒绝输入 | 实际原始进程退出值 | 异常码 | stage / 原 Win32 error | stdout |
| --- | --- | --- | --- | --- |
| 文件不存在 | 3765567489 | `0xE0720001` | 2 / 2 | 0B |
| 文件 hash 不符 | 3765567489 | `0xE0720001` | 3 / 0 | 0B |
| 原 DLL PROCESS_ATTACH 返回 FALSE | 3765567489 | `0xE0720001` | 4 / 1114 | 0B |
| 缺最后一个名称导出 | 3765567489 | `0xE0720001` | 7 / 127 | 0B |

四份 stderr 均严格等于单行 `sdk72-clean-bind-failed stage=<数字> win32=<数字>`，没有额外正文或假返回。runner 只持久化已核精确白名单布尔/计数 JSON、固定数字诊断和实际退出值；未知 stdout/stderr 仅记长度，不复制或求 hash。每个 child 最长 45 秒，有界超时判失败；没有重试并抹去首次失败。

`verify-agent1/source-build-artifact-proof.json` 固定 9 个源身份、6 份实际 artifact、Cargo.toml/lock、4 份 .def、编译日志及两轮/四拒绝结果，SHA-256 `e1773606264abb2a8c7326fcd98865473259d316979bdc45d274cdce26ca805e`。production DLL 为 156672B/SHA `b61fde01eefc86ce96afd135bcf171b0650a0dafe59bb0ef43cea6379ed2ab05`；owned DLL 为 152064B/SHA `71a3170cf4f7d2f5437528dc04228ca412eb8fae746566447af8b22507ba86d2`；fixture EXE 为 252928B/SHA `cf7efc3142e9e95ac560aed213d925c506b54a3fb551160f2beb76ba6e181e53`。这些是当前主线实际构建产物，不宣称不同构建二进制可重现。

14 项 Python 测试实际通过，日志 `python-suite-agent1.log`；含前述 8 项 PE/构建输入检查和 6 项 runner 白名单、真实退出/错误匹配、signed Windows code 归一化与拒绝未知输出，不以单纯非零退出刷绿。主线第三复验用新目录：

```powershell
python tools/verify_sdk72_clean_loader.py --output-directory local/migration72/d02-resume-20261108/clean-loader/verify-main-third
python -m unittest tools.test_audit_sdk72_clean_loader tools.test_verify_sdk72_clean_loader -v
```

主线第三 `verify-main-third/` 已实际完成，同样两 fresh ABI 和四真实拒绝全部通过，公开 ABI 与双跑相同；其 source/build/artifact proof 也与代理首轮逐字节同 SHA `e1773606…ca805e`。主线同时运行这 14 项及只读观察器 7 项，共 21 项通过。随后仅将审计测试“拒绝覆盖证据”的夹具改成测试自建临时文件，去掉对忽略目录 REA raw 已存在的依赖；该单项已重新通过，日志 `audit-independent-evidence-test.log`，不重复计算测试数。Rust/asm、已编译 artifact、runner 及真实执行合同全部保持。

## 进入真实加载前的剩余合同

本项已完成的是独立入口和真实 owned Win32 验收。没有在这批进程加载真实 Astrolabe 或 AccountPlatNative，没有安装客户端文件、读取自然 SDK 内存或启用 key/trust/route hooks。

下一项由主线独占：新 user ready 和五输入/当前进程清点 → 审核透明 proxy/固定 stock 的备份、文件归属及回退方案 → 按本页透明实验许可准备真实加载。新 proxy 的磁盘身份必须如实保留，stock 只作为明确的 Astrolabe_orig.dll，不做 swap、隐藏或校验伪成功。真实客户端若拒绝模块签名/身份，保留准确系统/客户端返回并正常退出和回退。

随后需取得真实模块 Windows 路径、基址、SizeOfImage、磁盘身份和自然 SDK 的 253B 准确窗口。诊断 export 不是自然 producer，本卡未新增自动轮询或远程调用；主线须指定已审的实际只读观察入口/时序，不能把“诊断函数存在”当作自然 SDK 已绑定。首次原函数调用时机、加载器锁上下文及客户端完整生命周期仍须实测，owned 正例不能替代这些合同。用户此前的一次 ready 不沿用为新的安装/启动许可，具体真实实验流程由主线当前活动计划负责。
