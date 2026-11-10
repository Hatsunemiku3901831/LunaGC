# D02-R2 / COMMON-SERVICE-OWNED-PREP（2026-11-11）

## 有界实施计划

本卡承接[共用入口合同及主线第三复验](CLIENT_72_SDK_NATIVE_ACCOUNT_LOCAL_ROUTE_20261111.md)，只准备独立普通 Cargo bin、纯观察核心、绑定边界与自有 Win64 夹具。主线串行负责实际 Rust 构建、Win32 运行、现场及 Git；本槽不运行构建、客户端、DLL、live API、服务或目标 CPU，不改旧 PostRequest 源码或门禁。

| 项目 | 本卡合同 |
| --- | --- |
| 唯一原入口 | 固定 SDK RVA `[3413E0,34180F)`，1071B/265条，SHA `eea7fc7301072783039a2dcf7951c2a61e87dd3f935b63701b49fa2e6e32ef1a`；两服务槽 `6C2098+8`、`6C2CA8+8` 同指该入口。原件仅复用 `static-main-third`，报告 SHA `a68dbaa1538dbc552f14fa801c68c7f5d025c8ece83d8d54d90545de12387bee`。 |
| 首次观察 | 任意入口先 CAS；RCX=0直接 unknown，否则仅一次8B `[RCX]`，精确比较当轮 SDK base+两完整 vtable RVA。R8只取低32位，输出 `0/1/3/11/other`。错误/短读封存，无重试。 |
| 公开结果 | 仅 `base-service-entry` 事实、`mdk/accountplat/unknown`、固定模式和读取失败类；不输出指针、raw、未知长度/hash、caller、事件计数或 returned/HTTP/auth。私有读取账与 emitter 原件只进忽略目录。 |
| 透明性 | callback不分配、不调用目标、不改入口参数或服务字段；assembly先保存XMM4/5，ilhook保存GPR/RFLAGS/XMM0..3，Win64 callee保持XMM6..15，LastError包住内核并恢复。原函数一次，S0/原return/参数栈和正常返回保持。 |
| 独立账 | 原完整body1071B及两个8B槽，3读1087B；ilhook decode/patch/protection和本次完整 emitter 另账。不能借用 PostRequest 的16B/280B验收。 |
| owned | 真实执行原21B（八push+LEA）搬移、原`sub rsp,458h`、自有callee一次及原ADD/反序pop/RET形状；near/far实际patch与搬移均检查。独立assembly对照无hook基线的GPR16、XMM16低128、RFLAGS、S0/原ret、128B owned参数栈、LastError及高64返回。 |
| 语义夹具 | 两精确表、unknown/null、模式高32非零、首错后正确、读取失败/短读、同步重入和真实其它OS线程；每case独立状态不扩大现场一次预算。 |
| 写域 | 仅五个新独立Rust源、本页、同名Python审核器及本卡私有目录；Cargo/lock/build/helper、生产、配置/Proto/资源、旧材料和共享索引只读。 |
| 源与停止 | 按实际直接与历史来源闭包冻结，不虚构55项。共享可维护文档复制私有context snapshot，未来门禁不pin原路径。固定源/原bytes、emitter、布局/ABI或放置失败即保留原件并停止，不缩检查或扩读。 |

复用本仓库已审 ilhook2.3.0/owned assembly外壳与来源，无新重大外部依赖或检索目标；此前 README-first/branches 检索成果直接沿用。本例不提供 DllMain、47转发、loaded-module resolver 或客户端安装入口，未来独立loader仍须核SDK实际path/base/SizeOfImage/disk SHA、留至进程寿命及正常退出恢复。

自然service/mode/loginUrl仍unknown，route批准0项，不接key/route重定向、不输入账号或伪调用目标。不把等SDK页面90秒当可用trigger。SEH/C++/托管展开、YMM/ZMM上半部、x87/MXCSR等保持unknown。

## 实际状态

源码与纯离线准备已交回，**尚未构建、未运行 Win32、未安装、未新增客户端验收**。`python -B -m unittest tools.test_client72_sdk_service_entry_owned -v` 的6项通过；检查完整1071B/265条/唯一pdata/两槽与原21B/SUB/正常出口、41固定源加3快照逐项漂移或缺失拒绝、ilhook三数组及两个完整精确emitter模板、104case报告完整性、任一emitter/地址/resume变化拒绝、unknown公开结构。人工报告只测审核器，不是CPU执行证明。固定源CLI与准备脚本重复生成通过，三个Python源AST解析通过；原始stdout/stderr和退出码留本卡私有 `offline-execution.private.json`。

固定来源按实际闭包为41项，不照抄PostRequest的55项；包括主线静态7原件及其15个历史来源、提取工具、冻结ilhook/iced来源、旧owned五源和四个构建输入。三个共享专题复制到本卡私有 `context/`，未来门禁只核快照路径。`frozen-inputs.private.json` SHA `38fc265d34dbcab56e628acc0c246acb5be9c15e76e9845a9d9ad9709e96fc41`；生成binding SHA `5e0a470490857ff44fd7a1585c6b9f82d4cb5a60553e211df36d5442e25f3619`。本次15个源码/构建输入的 `source-epoch.private.json` 为2949B / SHA `43fe8b0878f39da42ef4bbf09905d258628c51b5027a356ac29cf4bd06d4683a`，不将整个可变patch HEAD当门禁。

| 项目 | 已写范围与实际边界 |
| --- | --- |
| 绑定 | 独立3读1087B；完整body单独SHA，disk两槽原8B先冻结；owned/runtime两槽则精确完整QWORD=`B+3413E0`，不拿preferred VA当实际重定位结果，不mask/truncate。ordinal失败/短读及每项漂移均写入Rust夹具，待主线运行。 |
| 参数观察 | 首次任意entry CAS在读取之前；null0读，其余最多1/8，完整vtable与R8低32分类。unknown/null/首错后正确、每次错误/短读、成功/失败同步重入和真实其它OS线程均有Rust断言，待主线运行。unknown不保留原word或指针。 |
| 完整emitter | source覆盖始终21B，唯一resume=`B+3413F5`。iced可能仅省去PUSH RBP前冗余REX40，因此有限模板分别是原21B或只去首40的20B，完整emitter为285B或284B。只接受这两个逐byte精确模板；实际near/far分别报告真实encoding，不仅验长度。当前没有实际emitter。 |
| 原SUB主case | 两placement×五种对象（两精确表/unknown/null/unreadable）×五模式×两flags，共100case。保持原21B、原位置7B SUB、正常20B ADD/pop/RET；中间为自有采样/callee，不调用原SDK函数。GPR/XMM/栈/LastError/S0、原owned E8的return、高64返回和原callee一次与baseline对照，待主线运行。原函数是void式服务入口，高64只为合成ABI哨兵，不是认证结果。 |
| 独立continuation witness | 原SUB会覆盖算术flags，LEA会改变RBP，因此另写4case（两placement×两flags），在自有continuation中、搬移后且SUB前capture全状态，再执行相同SUB形状。此新增自有序列不是原SDK bytes；与无hook基线核GPR/XMM/RFLAGS/S0/128B参数栈/LastError，再核正常callee/RET。待主线运行，不能借主case的SUB后快照声称已看见SUB前状态。 |
| 生命周期 | 无效指令实际Hooker失败不写和正常返回后Drop恢复原21B均有夹具，待主线运行。本bin没有目标module引用或转发器，不能据owned Drop许可未来目标卸载。 |

一次源码查看的Windows路径wildcard被rg拒绝，改用已知精确iced路径；没有修改旧材料。REX与SUB两个问题及主线认可的最小处理记在私有 `attempt-notes.private.json`，没有Rust失败被省略，因为本槽没有运行Rust。

## 主线独立构建与两fresh

主线先取得构建空档，核本次15源码epoch与实际当前文件，再用新的独立target；保留所有失败/历史目录。私有build脚本只复用主线已经成功的本机MSVC/SDK入口，未由本槽执行。以下从当前checkout根运行：

```powershell
$serviceCheckout = (Resolve-Path '.').Path
$serviceRaw = Join-Path $serviceCheckout 'local/migration72/d02-resume-20261110b/b-service-entry-owned'
$serviceEpoch = Join-Path $serviceRaw 'source-epoch.private.json'
$serviceEpochSHA = '43fe8b0878f39da42ef4bbf09905d258628c51b5027a356ac29cf4bd06d4683a'
if ((Get-FileHash -LiteralPath $serviceEpoch -Algorithm SHA256).Hash.ToLowerInvariant() -ne $serviceEpochSHA) {
    throw 'Service source epoch differs.'
}
foreach ($serviceSource in (Get-Content -LiteralPath $serviceEpoch -Raw | ConvertFrom-Json).sourceClosure) {
    $serviceSourcePath = Join-Path $serviceCheckout $serviceSource.path
    if ((Get-Item -LiteralPath $serviceSourcePath).Length -ne $serviceSource.bytes -or
        (Get-FileHash -LiteralPath $serviceSourcePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $serviceSource.sha256) {
        throw 'Service owned source changed; preserve this epoch.'
    }
}
python -B tools/test_client72_sdk_service_entry_owned.py --verify-sources
if ($LASTEXITCODE -ne 0) { throw 'Service fixed sources differ.' }
$serviceTarget = Join-Path $serviceRaw 'target-main-owned1'
$serviceBuildLog = Join-Path $serviceRaw 'build-main-owned1.log'
if ((Test-Path -LiteralPath $serviceTarget) -or (Test-Path -LiteralPath $serviceBuildLog)) {
    throw 'Choose new owned target and build-log names.'
}
# 内部准确Cargo命令：build --manifest-path patch/Cargo.toml
#   --example sdk72_service_entry_proxy --release --locked --offline
& $env:ComSpec /d /c (Join-Path $serviceRaw 'build-main-owned1.cmd') > $serviceBuildLog 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Service owned build failed; preserve all logs.' }
$serviceFixture = Join-Path $serviceTarget 'release/examples/sdk72_service_entry_proxy.exe'
$serviceFixtureSHA = (Get-FileHash -LiteralPath $serviceFixture -Algorithm SHA256).Hash.ToLowerInvariant()
python -B (Join-Path $serviceRaw 'run_owned.private.py') --fixture $serviceFixture --sha256 $serviceFixtureSHA `
  --source-manifest $serviceEpoch --source-sha256 $serviceEpochSHA --output (Join-Path $serviceRaw 'main-owned1')
if ($LASTEXITCODE -ne 0) { throw 'Service owned verification failed; preserve all raw.' }
```

driver只允许本卡私有根下同名EXE、显式产物/epoch SHA和新输出目录，不接受PID、客户端路径、安装或构建参数。最多两个fresh，各45秒；失败保留stdout/stderr/进程元信息并停止，timeout只停止该Popen自有EXE。两fresh每轮104个完整emitter/实际case均须通过审核；地址可以不同，分类/范围和实际编码摘要必须一致。失败不改旧raw/门禁掩盖漂移；需修源则保存旧epoch，另冻结新epoch并选择新target/output。

本卡没有现场产物需要恢复；owned未来正常返回后才恢复自有入口/释放allocation。客户端stock、alias、配置/原数据和旧观察器均未改。主线审核真实构建/两fresh后再分别提交patch和父仓库引用、更新Project/Harness；本槽未Git提交或stage。下一项是上述串行构建/owned，随后合法loader/自然trigger仍需独立合同。自然service/mode/loginUrl、route/key/认证以及D-02六项仍未验，不要求用户当前操作或输入凭据。

## 主线实际构建及owned验收

主线核15源码epoch与41固定源/3快照，6项离线测试通过；全新target的便携MSVC/Cargo release/locked/offline构建13.55秒退出0，仅既有cc unused dependency警告。EXE为674816B、SHA `86f843d2d9de62fe48036672ee2206be3cdb3d75575bb1641359e663efc50d5d`。两fresh独立Win32均退出0，各104真实case，共208个完整emitter逐byte审核通过；100+4 per run分别为原SUB与独立SUB前witness。

实际near/far均把首冗余REX40规范为20B搬移，source覆盖仍21B；完整emitter均284B，唯一回跳原SUB的B+3413F5。两侧实际普通GPR/XMM低128/RFLAGS/S0/128B栈/LastError/原return/自有callee一次/高64返回均通过；独立witness只证明self-owned continuation，不冒充原SDK序列。两表完整64位重定位与mode低32、null0读、首次unknown封存、错误/短读/重入/1000重复/实际其它线程零新读均过。绑定3/1087与参数1/8单独核，raw及完整审核留`main-owned1/`。

patch子模块独立提交 `50f9bf8`，父仓库另核gitlink与本卡测试/专题/索引。没有修改旧PostRequest/原47转发器、生产DLL/JAR/配置/资源，未安装客户端，不需GS重启部署。仅关闭COMMON-SERVICE-OWNED子出口；自然服务/模式、loginUrl、合法trigger和loader安装时序、认证、EH/unwind/高向量与D02仍未验。下一有界问题沿已有自然配置同步入口核先安装后原service动作，不用页面等待替代producer顺序。
