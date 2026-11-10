# D02-R2 / SERVICE-TRIGGER-LOADER（2026-11-11）

## 有界中文实施计划

本卡承接[准确自然触发合同](CLIENT_72_SDK_NATURAL_SERVICE_TRIGGER_20261111.md)，独立实现 opt-in 两阶段 DLL 与真实组合 owned。旧八点和 service standalone 的实际结果只作来源，不冒充本卡验收。root 独占 Cargo/MSVC/Win32、安装、现场、恢复与 Git；本槽只写限定源码、纯 Python 准备检查和忽略目录原件。

| 项目 | 本卡合同 |
| --- | --- |
| 初始入口 | 原47透明转发，DllMain仅记录自身；首次冷转发完成 stock 绑定后，同步核 EXE 并安装唯一 producer，再放行原 callee。无 worker、等待 SDK、主动加载 SDK、route/key/认证或旧八点组合。 |
| EXE代码 | Check完整3564B及三个普通桥155/823/203B，共4745B，以512B chunk为11读；实际 path/base/SizeOfImage/disk SHA 先绑定。 |
| producer | EXE+197FED3C覆盖原19B，唯一回跳197FED4F；首次任意入口先CAS，再最多两次QWORD[R14]、[klass+F0]，完整64位目标必须等当轮 EXE+6E36310；失败/短读/错误封存无重试。name/args/callback不读不改。 |
| 同步安装 | 仅 bind 已加载准确 SDK，引用即进程期保留；原body1071B与两8B槽共3读1087B。真实装SDK+3413E0，先持有唯一core/hook/module，后发布 installed-before-pending-action，再恢复原19B/原虚CALL/原return。 |
| 独立账 | 代码绑定14/5832；producer数据2/16；service沿冻结核心首次任意1/8，低32 mode分类；47/状态/API/disk/ilhook decode/protection另账。 |
| owned | 真实near/far RIP搬移原19B；中段S=入口RSP−D8且mod16=0/RBP=S+80。原literalcell唯一、RBX和stack+20恢复后执行；原owned virtualCALL恰一次、原ret，再自然进入已真实安装的owned service21B。核全GPR16/XMM低128/FLAGS/S/RBP/128B栈/LastError/高64返回及两完整emitter。 |
| 失败与寿命 | 首错后正确、两读逐项失败/短读、SDK缺/身份/代码错、实际ilhook失败、成功后失败仍持有、service unknown/重入/真实其它线程；不假installer成功。所有目标hook/reference留至进程结束，owned仅正常返回后释放自有页。 |
| 来源与写域 | 冻结实际helper/source closure，维护文档复制私有context；仅本页、同名Python检查器、新example及同名目录必要源、本卡私有目录。Cargo/lock/旧源/共享索引只读，由root加准确声明后冻结。 |
| 退出条件 | 源包成熟即交root；真实ABI、RIP搬移或安装难点未解即保留失败和准确缺口，不放宽合同。GC、IFix、异常展开、高向量、早期覆盖、实际service线程归属及自动会话请求均unknown。 |

复用此前 README-first/branch 检索与已验 ilhook2.3.0、stage同步结构和 service 核心，不开启新重大检索或增加依赖。无凭据不等于零网络；自然producer/service/mode/loginUrl、合法route/key/认证及D-02仍未验。

## 当前状态

源码和纯 Python 准备已交回 root，**未构建、未运行 Win32、未安装、未启动客户端或服务**。6项纯准备测试通过；它们审核固定来源、源/字节/报告协议，不是 CPU 执行证明。本卡不宣称新 DLL、实际 emitter、组合 ABI 或自然事件已验。

私有根为 `local/migration72/d02-resume-20261110b/b-service-trigger-loader/`。root重新核五固定输入后，从已有完整 Check 窗补出 `check.bin`，3564B/SHA `2f1d9497c12f74668ec02b0af4e202df4acbcf17990e8acf15e43f415f42e8d1`；没有新增研究窗。`prepare.private.py`只消费既有原件，重复生成得到相同 binding，27172B/SHA `680868f1b6551c2af75d3fd6b6c1b55691772e2af7fa916409364453de433553`。冻结集合为78个实际固定来源及3份维护文档的私有快照；门禁核快照，不 pin 原维护文档路径。

root已按明确前置添加两个 Cargo example：DLL `sdk72_service_trigger_proxy`/`cdylib`，bin `sdk72_service_trigger_proxy_fixture`/`examples/sdk72_service_trigger_proxy/fixture_main.rs`。本槽没有修改 Cargo/lock；新 Cargo SHA `08f3a039ffbc2fbfd84143b6d5338b11d02fc251383b260984894efa2093ebc7`，旧 Cargo 原件保留。`source-epoch-prepared1.private.json`包含20项直接构建/检查输入，4277B/SHA `d06d54c957bf9bb0c3ef0b7fdad80218fa56c1cb0a983527c04f5fe715c5286e`。

## 实现与可审核边界

新 DLL 只复用 frozen clean core/table/assembly 的47转发，以及原 service observer 源。首次正常冷转发内同步 `prepare`，原 stock 绑定、EXE path/base/SizeOfImage/disk SHA及完整四窗通过后，真实安装 producer；没有 worker 或 SDK 等待。producer callback先CAS，再两次QWORD核普通目标；SDK失败时仍恢复原状态、透明继续原动作。只有自有 SDK 引用、service core/userdata/HookPoint均保留之后，才发布安装成功。

DATA固定128B；阶段/失败、producer匹配及安装和原service固定类别为公开结果。EXE/SDK base、SizeOfImage、两个 trampoline 与实际持有状态仅作固定模块/安装身份元信息；不保留或公开任意堆参数、class/方法目标、name/args/callback、账号或 raw mode。producer和service读取账在各自私有核心，代码绑定账独立。root最终布局门禁须确认128B及保留字节全零。

DLL EAT预期准确50项：原47 `Astrolabe_*` 转发，加 `Sdk72ServiceTriggerProxyState` DATA、`sdk72_service_trigger_inner` 和冻结核心的 `sdk72_service_entry_inner`。两个assembly callback是内部符号；没有旧key/config dormant导出。root须核所有47原名称/ordinal/真实thunk，不以总数量代替内容核对。DLL及fixture分别产出，不整包旧DLL。

组合 owned 的 EXE/SDK是两个自有分配。真实执行完整原 Check 前27B（八push共12B、SUB7B、LEA8B）后，以显式自有准备跳到精确原 producer；S=`entryRSP-D8`、mod16=0，RBP=S+80。原19B、原三MOV及原 `197FED58` 虚CALL始终在原自有 RVA；原return精确为 `197FED5E`。唯一原RIP literalcell在自有EXE+5AD8638，加载高64哨兵，未缓存替代name。原RBX/class与stack+20写零按真实原指令执行。

第一次 hooked producer 内真实读完整SDK3/1087，再准备显式自有continuation并真实安装 service Hooker；持有后才恢复原19B。原虚CALL的自有callee恰一次，再真实CALL已装service21B/原SUB7/自有采样callee/原ADD-pop-RET。自有桥只构造自有服务参数，不执行原managed桥或SDK业务，也不手调service callback。无hook基线与hook执行逐项比较GPR16、XMM0..15低128、RFLAGS、S/RBP、128B参数栈、LastError、两个原return、两个自有callee次数和高64返回。

每fresh预期60个组合case：near/far × MDK/AccountPlat/unknown × 五mode（高32位非零）× 两flags。每case审核两个完整实际emitter：producer near预计19B搬移/283B emitter，far预计35B/299B；后者必须精确用 frozen mover 的原literalcell/RBX借用及恢复序列。service固定采用已实际见过的20B canonical搬移/284B emitter；不以长度相同放过任意编码。以上均是待实际构建/owned核对的准确协议，实际不符则保存失败交回，不能改成只核前缀或放宽RIP机制。

夹具还写入两次producer数据读取逐项失败/短读、完整64位目标错误与首次错误封存、同步重入及实际其它OS线程、各14代码读失败/短读前缀、四EXE窗/SDK body/两槽共七项漂移、真实loaded-module不存在/path/size/disk失败与已取得引用保持、两个真实ilhook安装失败不写、成功后失败仍保留真实service hook、service首unknown/读取错误/短读/重入/其它线程。loaded-module负例只针对当前自有fixture EXE核相同binder，不能据此声称真实SDK已加载。所有native断言仍待root执行。

## 纯准备实际检查与保留失败

`python -B -m unittest tools.test_client72_sdk_service_trigger_loader -v` 6项通过：完整原窗及原CALL不被覆盖；78固定源/3快照逐项漂移或缺失拒绝；60组合case和120完整emitter报告协议；每个原字节/patch字节/emitter字节及地址/resume漂移拒绝；同步结构/唯一尝试/持有顺序；frozen ilhook数组及中段frame形状。人工生成报告只用来测试审核器，未写成native成功证据。`--verify-sources`通过，Python源AST解析通过；`offline-final1/`保留实际argv、stdout/stderr、退出码及时间。

两次纯准备失败均保留：初始审核器错误使用不存在的数组函数名/`write_all`，改为冻结 `write_trampoline_epilog2_common`/`buf.write`；随后完整bytes断言发现本槽最初把 Check prologue误数为25B，真实为27B，已在自有夹具修正。两份当时审核源、第二次夹具源和两份red日志留私有根。没有native失败被隐去，因为本槽没有运行native。

## root 串行构建、两fresh及出错恢复

从当前checkout根执行；先取得构建空档并核20源epoch。root完成独立第三审后选择新的target/log，不复用旧service standalone target。

```powershell
$triggerRoot = (Resolve-Path '.').Path
$triggerRaw = Join-Path $triggerRoot 'local/migration72/d02-resume-20261110b/b-service-trigger-loader'
$triggerEpoch = Join-Path $triggerRaw 'source-epoch-prepared1.private.json'
$triggerEpochSHA = 'd06d54c957bf9bb0c3ef0b7fdad80218fa56c1cb0a983527c04f5fe715c5286e'
if ((Get-FileHash -LiteralPath $triggerEpoch -Algorithm SHA256).Hash.ToLowerInvariant() -ne $triggerEpochSHA) {
    throw 'Trigger epoch differs; preserve existing evidence.'
}
foreach ($triggerSource in (Get-Content -LiteralPath $triggerEpoch -Raw | ConvertFrom-Json).sourceClosure) {
    $triggerSourcePath = Join-Path $triggerRoot $triggerSource.path
    if ((Get-Item -LiteralPath $triggerSourcePath).Length -ne $triggerSource.bytes -or
        (Get-FileHash -LiteralPath $triggerSourcePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $triggerSource.sha256) {
        throw 'Trigger source differs; do not reuse this epoch.'
    }
}
python -B tools/test_client72_sdk_service_trigger_loader.py --verify-sources
if ($LASTEXITCODE -ne 0) { throw 'Trigger sources differ.' }
$triggerTarget = Join-Path $triggerRaw 'target-main-combined1'
$triggerBuildLog = Join-Path $triggerRaw 'build-main-combined1.log'
if ((Test-Path -LiteralPath $triggerTarget) -or (Test-Path -LiteralPath $triggerBuildLog)) {
    throw 'Choose new target and log names.'
}
# 在root已核便携MSVC/Windows SDK环境中执行，不另开未知工具链：
$env:CARGO_TARGET_DIR = $triggerTarget
# 实际cargo.exe取root本轮核过的位置。准确参数如下：
cargo build --manifest-path patch/Cargo.toml --example sdk72_service_trigger_proxy `
  --example sdk72_service_trigger_proxy_fixture --release --locked --offline > $triggerBuildLog 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Trigger build failed; preserve all raw.' }
$triggerFixture = Join-Path $triggerTarget 'release/examples/sdk72_service_trigger_proxy_fixture.exe'
$triggerFixtureSHA = (Get-FileHash -LiteralPath $triggerFixture -Algorithm SHA256).Hash.ToLowerInvariant()
python -B (Join-Path $triggerRaw 'run_owned.private.py') --fixture $triggerFixture --sha256 $triggerFixtureSHA `
  --source-manifest $triggerEpoch --source-sha256 $triggerEpochSHA --output (Join-Path $triggerRaw 'main-combined1')
if ($LASTEXITCODE -ne 0) { throw 'Trigger combined owned failed; preserve all raw.' }
```

driver只允许本卡私有根下准确fixture名称、显式EXE/epoch SHA和全新输出；无PID、客户端路径、安装或构建入口。两fresh各45秒，失败保留该Popen的argv/PID/stdout/stderr/exit并停止；超时只终止其自有fixture。每fresh60case/120emitter，双fresh合计120/240；规范化审核结果应一致，地址可因ASLR不同。root还须独立核DLL产物SHA/50EAT/47转发/128B DATA、实际两模块与代码预算及两个完整emitter，不能只信报告bool。

任何build/owned失败先保留日志、失败源码epoch和产物；本卡不改约束让其通过。允许范围内修源后冻结新epoch并选新target/output，旧目录不覆盖。当前没有客户端安装或现场产物需要恢复。未来安装/新ready/自然观察均由root单独门禁并正常退出；目标OS消失后才恢复stock/alias及核保护，禁止live卸载hook或覆盖DLL。GC、IFix、SEH/C++/托管展开、高向量、bootstrap前覆盖、实际service线程归属与已有自动会话请求仍unknown。

本卡没有Git stage/commit，Project/Harness/HANDOFF由root统一更新。下一可执行项是root第三审及上述独立构建/真实组合owned；之后才可准备新ready现场验证。自然producer/service/mode/loginUrl、route/key/认证和D-02六项仍未验，无待用户操作。
