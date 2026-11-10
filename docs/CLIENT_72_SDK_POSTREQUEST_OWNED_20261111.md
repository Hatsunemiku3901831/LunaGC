# R2-ROUTE-CONFIG-SELECT / POSTREQUEST-OWNED-PREP（2026-11-11）

## 先行实施计划

本卡承接[准确入口规格](CLIENT_72_SDK_POSTREQUEST_ENTRY_20261111.md)，只交独立 owned 可执行例、纯观察核心、源门禁和夹具准备。主线独占 Cargo 声明、实际构建、Win32 owned 运行、现场 loader、Project/Harness 与提交。本卡不加载客户端/SDK，不安装 DLL，不调用登录、HTTP 或目标托管函数，不取得新的现场许可。

| 项目 | 固定合同 |
| --- | --- |
| 输入 | 原33107 body310B SHA `7994592283e5d24d2ebb66d4e5c891d4702dad902ba1dfbb0806d2ec9c507809`；原40108 CALL5B SHA `e93f40cc303afd39dd5c825e11191626e24a13edc5fe3b6745752113b4f3611c`；原pdata12B SHA `2d5e615593d1bf523556be98b40edc1d62a335de993663887a130754abc66536`；入口原16B `4157415641554154565755534883ec68`。 |
| ABI来源 | ilhook2.3.0 `x64.rs` SHA `1b1350b1fc6b0a2cac0726bd3765c1dbc8bd90450f99439adca02ba208a9e8a7`、`move_inst.rs` SHA `f98e6d9f15faeaab12e00e81c912778b4f8e789f53058b5d3547e3abdcd30720`；既验key ABI、八点/五点 wrapper与loader仅作为冻结来源。全部直接/历史输入完整SHA存本卡 `frozen-inputs.private.json`，共享可维护文档仅留上下文快照，不作为运行门禁。 |
| 分类来源 | [FINAL_URL_CLASSIFIER](CLIENT_72_SDK_FINAL_ROUTE_CLASSIFIER_20261110B.md)及其七直接pin；安装前从固定源生成Rust UTF16LE八表，与独立人工fixture对照。仅完整base加单`?`，不做前缀、query剥除或规范化。 |
| 可写 | `patch/examples/sdk72_postrequest_proxy.rs`、同名目录四独立源码，本文、`tools/test_client72_sdk_postrequest_owned.py`，以及忽略目录 `local/migration72/d02-resume-20261110b/b-postrequest-owned/`。Cargo/旧例/共享helper/Proto/矩阵/配置/资源/原数据只读。 |
| 参数读域 | 首次任意入口CAS先占尝试；原`[S0]`8B精确为`B+19801317`；fresh `B+5886040`8B、`RDX`class8B、`RDX+10`长度4B、仅L65..70时`RDX+14`恰2L字节。最高5读168B，错误/短读按请求宽度，失败即封存，无重试/补读/救场。 |
| 代码绑定 | 独立账仅原body310B及caller5B，2读315B；入口已含在body内。ilhook自身30B decode、patch/protection及安装内省独立登记，不能使用旧预算。owned直接内省完整本次280B emitter，不沿用旧297B。 |
| 写域 | observer只写自有固定状态和私有读账，分类不改目标；owned Hooker只写自有allocation入口，合成body只写自有Context/栈。无目标String pin、修改、缓存或跨线程留用。 |
| 发布 | 只发布固定终态、准确入口/原caller事实位、固定类别或unknown；私有计数不进入逐事件输出。没有returned/HTTP/auth成功字段。emitter原件只留私有输出。 |
| 夹具 | 自有reserve中准确E8 caller→原16B→原movaps→合成callee一次→原XMM6/栈/反序pop→原尾跳RVA→合成RET→准确原caller返回。assembly先于Rust独立记录ilhook savedRSP；GPR16/XMM0..15低128/RFLAGS/128B自有参数栈/LastError及高64位返回与无hook基线对照；near/far都必须真实执行。 |
| 停止 / 恢复 | 固定源/原bytes漂移、布局/完整emitter不符、near/far放置失败或ABI比较不等立即失败并保留原件，不缩小测试、不放大预算。正常owned函数返回且无执行线程后才Drop恢复自有入口，随后释放自有allocation。未来现场成功HookPoint/userdata/module必须按既验方案留至目标进程寿命，不能借owned Drop批准现场卸载。 |

本例为普通bin，不提供DllMain/47转发/安装入口；现场透明转发与loaded-module path/base/SizeOfImage/disk SHA绑定由下一独立loader包审查。本卡不复制整个旧patch，也不开启旧多点observer。GC pin/EH/unwind/YMM/ZMM/x87/MXCSR仍未知，不加入异常展开、resolver或并发装卸机制。

## 实际结果与主线复验

本卡已完成源码和离线准备。实际 `python -B -m unittest tools.test_client72_sdk_postrequest_owned -v` **8项通过**，含独立八表/1090逐byte扰动、原310B/79条拼接/原CALL与尾跳身份、源55项加3上下文逐项漂移/缺失拒绝、固定ilhook三段数组、完整280B near/far报告协议和32case完整性/坏字段拒绝。报告协议的人工数据仅测试审核器，**没有当作实际emitter或CPU结果**。固定源CLI通过，private准备脚本重跑逐byte相等，三份Python源AST解析通过；原始stdout/stderr及退出码见本卡 `offline-execution.private.json`。输入manifest SHA `15fe4f33595b9466a06fcf18c4d31c2ed260c462d1d3da17699f74e6e87a2088`，生成Rust binding SHA `7bff135521d629fd5da5ee70d23b617a332ef6331ac40176552176b46da16371`。

准备阶段两处错误已保留在私有 `attempt-notes.private.json`：最初把ilhook routine部分误计35B，源数组检查拒绝后按实际33B改正；报告协议的人工far地址恰为4GiB，严格大于4GiB的门禁误拒绝，改为允许高32位非零的`>=4GiB`。后续正式8项及门禁通过。没有Rust失败被隐去，因为本卡完全没有运行Rust构建或Win32。

| 验收项 | 本卡实际状态 / 主线下一步 |
| --- | --- |
| 固定源/生成分类表/原窗/报告协议 | 已完成上列离线检查，不能替代Rust执行。 |
| observer语义 | 已写Rust夹具：首次错误后正确caller无救场、1000重复、同步重入、真实其它OS线程、null/String class高64/zero-cell、int32长度边界、未知字符及每个ordinal错误/短读；**待主线编译运行**。 |
| 代码绑定 | 已写独立2/315账；人工ordinal/漂移夹具和Win32 owned实际RPM路径均在Rust例内，**待主线运行**。 |
| 普通ABI/near与far实际emitter | 已写32case（两placement×八类×两flags），完整280B逐byte核后才执行原16B/movaps/合成call/原恢复/原尾跳。assembly在Rust前记录Registers.rsp；合成callee一次、高64返回、原caller和128B自有参数栈均有断言；**尚未构建/运行**。每case新建独立观察状态，不能把owned多case当现场预算扩展。 |
| 生命周期 | owned安装失败不写/Drop恢复测试已写，只有受控调用正常返回后才Drop；未来目标进程寿命保留仍由下一loader包另审。 |
| 客户端/业务 | 新增验收0；没有安装/服务部署，D-02未完成。合法本服认证route/key/信任及40103成功AccountModel未闭，本卡不能反向解除前置。 |

新例由Cargo自动发现为bin；本卡没有写Cargo声明、Cargo.lock、旧helper或任何索引。主线取得构建空档后，先审核 `source-epoch.private.json` 的全部当前源码和输入；使用新的独立target目录，不覆盖历史产物：

```powershell
$postCheckout = (Resolve-Path '.').Path
$postRaw = Join-Path $postCheckout 'local/migration72/d02-resume-20261110b/b-postrequest-owned'
$env:CARGO_HOME = Join-Path $postCheckout 'local/cargo'
$env:RUSTUP_HOME = Join-Path $postCheckout 'local/rustup'
$env:CARGO_TARGET_DIR = Join-Path $postRaw 'target-main-owned1'
if (Test-Path -LiteralPath $env:CARGO_TARGET_DIR) { throw 'Choose a new owned target directory.' }
& (Join-Path $env:CARGO_HOME 'bin/cargo.exe') build --manifest-path patch/Cargo.toml `
  --example sdk72_postrequest_proxy --release --locked --offline
if ($LASTEXITCODE -ne 0) { throw 'PostRequest owned build failed; preserve the log.' }
$postFixture = Join-Path $env:CARGO_TARGET_DIR 'release/examples/sdk72_postrequest_proxy.exe'
$postFixtureSHA = (Get-FileHash -LiteralPath $postFixture -Algorithm SHA256).Hash.ToLowerInvariant()
# source-epoch冻结12个源码/构建输入；不能让driver自己信任磁盘新值。
python -B (Join-Path $postRaw 'run_owned.private.py') --fixture $postFixture --sha256 $postFixtureSHA `
  --source-manifest (Join-Path $postRaw 'source-epoch.private.json') `
  --source-sha256 9ccb43c7a480faf849e0d9cd198014688899f5da68cdfa927ee96336e3d41fd0 `
  --output (Join-Path $postRaw 'main-owned1')
if ($LASTEXITCODE -ne 0) { throw 'PostRequest owned verification failed; preserve all raw.' }
```

driver不构建、不安装，不接受PID/客户端路径，只允许本卡私有根下准确同名EXE、显式产物/来源SHA及新目录。最多两fresh、每个45秒；失败保留原始stdout/stderr/进程元信息并停止，timeout只停止该Popen自有子进程。两fresh各32实际emitter必须通过完整审核，分类/范围摘要相等；地址/进程身份本来可能不同，不要求raw字节相同。shared源修改或编译失败后要新冻结/新输出，不修改历史门禁掩盖漂移。

本卡未运行Cargo/cl/Gradle/JVM/DB/服务、客户端/调试器/live API/DLL安装/Git；未改配置、资源、原数据或任何旧文件。主线审核真实构建/owned结果后，才分别提交patch子模块与父仓库引用并更新Project/Harness。下一可执行项就是上述独立构建/两fresh；现场loader和原生账号本服接线继续分包，不能以官方登录、伪ret0或远程调用40108制造本卡事件。

## 主线实际构建与自有执行验收

主线核12源epoch/55冻结输入与上下文门禁后，在新的`target-main-owned1`执行便携MSVC14.44/SDK26100、Cargo release/locked/offline，14.88秒退出0；仅既有cc unused dependency警告，无源码调整。实际EXE为690688B、SHA `99e16b5eb222e8c313c91bea0e4cb0ee78e1f74f2f288955a2e681c10b05cd5a`。运行原两fresh driver，两个独立Win32进程各32case均退出0，64个实际280B near/far emitter全字节核验通过。8离线测试主线复验通过。

实际原16B搬移、原S0/return、合成callee恰一次、高64返回、16 GPR/XMM低128/RFLAGS/128B栈/LastError与baseline一致；真实其它OS线程/同步重入/1000后续进入均零新参数读，ordinal错误/短读、代码绑定RPM/失败不写均通过。参数5/168与绑定2/315独立。本次原件及结果在`main-owned1/`，raw含ASLR/进程身份，本来不同；只要求完整逐次核验与固定范围摘要相同。

patch子模块独立提交 `8b7acac`，仅五份新owned源码；初次子模块提交因缺user identity被Git拒绝，随后仅该命令复用父仓库已有身份，不改全局配置。父仓库另提交gitlink/测试/专题/索引，不夹带正在开发的service example或原resources用户差异。

仅关闭owned子出口；本次未加载SDK/客户端，未安装生产DLL，原运行JAR/配置/资源未改，无GS重启或部署动作。自然service/mode/loginUrl、本服key/route/信任与成功AccountModel仍前置；GC所有权、EH/unwind及客户端/D02未验。下一项是独立服务入口owned与合法触发/loader规格，不能据本fixture直接提交凭据或触发官方登录。
