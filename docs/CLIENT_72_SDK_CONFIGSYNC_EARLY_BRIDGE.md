# R2-CONFIGSYNC-EARLY-BRIDGE：SDK lazy load 前的准确 EXE 桥

## 实施前计划与选择

承接已验 [SDK 观察器](CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md) 的实际装载及 `no-matching-event`、[触发链](CLIENT_72_SDK_CONFIGSYNC_NATURAL_TRIGGER.md)、[账号回调先后](CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md) 和原版同 epoch 的两个 scalar 读取。旧未命中原因仍未知；另一轮末尾 registered/once 都为 1，不能解释过去事件、证明 export 或 writer 成功。原模块监测也未取得 hook 的生效时刻，不能把 SDK 首次采样时刻或 EXE hash 耗时当因果。

本卡只解决早期覆盖和 ABI：优先准确 EXE `AccountPlatNative.ConfigSyncWithNative`（method70148，RVA `6d80910`）入口，在等待 SDK 之前安装单个独立观察点。这个入口接收已经构造的 managed name/payload，若其原始读取合同可以固定，能直接取得 env 和两个既审 reference 等值 bool，不需要 SDK 已加载。只有 producer entry 的阶段位加 SDK export 的两个事实位，仍不能恢复错过的配置输入，不能解除该缺口；本卡不选那条无限观察路线。

现有完整 `6d80910..6d80a77` 359B 窗证明入口为 `RCX=this、RDX=name System.String、R8=payload System.String`，普通分支把两个字符串送 `140534480`，在 `6d809c9` 同步 call 已解析 export，之后分别释放两个临时 buffer。`LocalConfigSync` 在 `19807989` tail-jump 插件槽，implementation 又在 `6e363ee` tail-jump 本入口；准确普通 producer 路径的 CPU return 因而是原 `198035f1 call LocalConfigSync` 的 `B+198035f6`，不是 native export 的 `B+6d809cf`。未知 caller 不读取 name 或 payload。

原 [event proof](CLIENT_72_SDK_CONFIGSYNC_EVENT.md) 执行的是 SDK constructor/dispatcher 和当前 OS 的 UTF-8→UTF-16 转换，不是 EXE PInvoke 桥或 `140534480`。本卡不沿用“托管桥已经执行”的早先摘要。先复用已有准确字符串样本/元数据与 wrapper 窗；若必要，最多补两个沿已证直接调用的完整 PE exception 窗，先固定来源、extent、SHA，再分析。不得猜 System.String 结构、默认封送成功、保留自然指针或扩大成完整 Marshal/CRT 初始化。

## 允许改动、安装顺序与未知

只新增本页、`patch/examples/sdk72_config_early_proxy.rs` 及其同名目录、`tools/verify_sdk72_config_early_proxy.py` 与必要定向 test。私有来源/失败/构建输入/proof 仅在 `local/migration72/d02-resume-20261109/r2-early-bridge/`。旧 example、classifier、loader、源码、测试、Cargo 配置/lock、资源、配置、协议和共享索引全部冻结；主线统一处理构建、Git 和后续实际现场。

拟复用原 47 转发和 stock 装载/精确 EXE loaded 绑定，以及原 config classifier/reference 来源。独立入口的首次 Astrolabe 转发在返回原目标前同步准备 EXE 并安装 bridge；没有 SDK wait、key activate、trust/route hook、参数替换或成功返回。此顺序只保证安装先于本 proxy 的第一次原 Astrolabe 调用，不证明先于进程的任意其它线程/更早调用；尚未被覆盖的区间保持未知。准确 loaded module 路径/SizeOfImage、完整磁盘身份、三个绑定代码窗、完整 prologue 和实际搬移/恢复必须另验。失败保留固定 stage，不写配置、不主动加载 SDK。

## 入口读取、隐私与 ABI 验收合同

已固定的读取顺序是 return `B+198035f6` → managed name `+14` 起的完整 `local_config\0` UTF-16 单位 → 同一同步调用内的 payload `+14` 起有界 owned 复制。**不读 managed 长度、class/header 或 this，不保存自然参数指针。** 原 helper 不读 managed 长度，故不会在新 observer 里补猜它；遇内嵌 NUL 只观察其之前的输入，NUL 后不读。配置/key/URL/token 的正文、hash 和长度一律不输出。不可读、超限或非法 UTF-16 只给固定失败并保持原执行，不制造安全指针或成功值。

只在自有副本按冻结 classifier 处理既证 CN env 及 app_key/comboLogin reference 等值。DATA 只允许固定装载/代码元数据、一次事实位和固定状态、允许 env、两个 bool；无计数、时间序列、自然指针或内容摘要。命中仅证明准确 managed bridge 入口的输入，不证明 IFix/普通分支、实际 export、writer 返回、route、认证或 D-02。

owned 夹具必须在自有代码/对象中覆盖 Win64 全 GPR16、XMM0–15、RFLAGS、原栈 128B、入口/返回 LastError、原调用严格一次/原 high64 返回、源对象保持、未知 caller 零 name/payload 读、近似 name 零 payload 读、UTF-16 与 NUL/超限、非零/高64/边界、env/shape/reference 正反、一次捕获及 drop 恢复。另在自有页核准确真实 prologue 的实际 ilhook 搬移/完整 trampoline/返回边；不执行真实 SDK 或 EXE 代码页。保留 wrapper 已知范围限制：YMM/ZMM、x87/MXCSR、异常展开、生产并发和完整 GC 生命周期未验。

## 准确来源和封送边界

本卡只新增以下两个完整 exception 窗，合计 490B；来源/extent/SHA 与原 bytes 先持久化，再反汇编，原件留本卡忽略目录。没有第三个新 PE 窗、CPU 执行或目标读取。

| 直接 helper | RVA 范围（end 互斥） | bytes / SHA-256 |
| --- | --- | --- |
| wrapper 两次直接调用的原 helper | `534480..534522` | 162 / `2050a5e1bc7d2cd740bc79304abcd34f3f4ca65c454e5bd495ffe62a394086cc` |
| 上一 helper 在 `5344a7` 的直接目标 | `507600..507748` | 328 / `b6a18926616c8714228a0d92937933d8fed66a53947d6fafa516c7c6a599de87` |

`534480` 对非空参数取 `+14` 后交给 `507600`；后者比较 u16 NUL、扫描单位并进入 surrogate/codepoint 分支，再调用已有准确编码入口 `510cc0`。本卡预算不再展开该第三函数，也不执行 CRT allocation/copy/free。新 observer 对 owned 单位使用 Rust strict UTF-16 解码，非法 surrogate 拒绝；它只证明原桥入口的 **managed JSON 语义分类**，没有声称与原编码器对所有输入等价。原 caller/参数源的固定 metadata 另重解 method70148/70136 的两个 string 参数和 instance flags，避免以名称或静态两参 ABI 代替原指令。

## 独立实现与有界读取

新增 example 默认作为自动发现的 owned EXE；主线用同一个源的 `cargo rustc --crate-type cdylib` 单独产生 DLL，因此没有更改 Cargo.toml/lock。它直接复用冻结 clean 47 转发、真实 loaded binder、旧 References/classify 与 owned assembly。首次冷转发先绑定原库，然后在同一 resolver 正常调用范围中 pin self、绑定 EXE/引用、核第四个准确 caller 窗并安装单个 `6d80910` JmpBack；返回 resolver 后才调用原 Astrolabe API。没有 worker、SDK wait 或 key/trust/route 激活。DLL 原加载初始化、proxy 首转发以前和其它线程的先后仍未知，不能将“更早安装”写成全启动覆盖。

EXE loaded 绑定窗为完整 bridge `6d80910/359B`、完整 implementation `6e363b0/139B`、原 producer 尾调度 `19807960/64B`；另核 `198035e8/46B` 的 once/direct call/原返回后继。原 bridge 的前 20B 由六个完整指令组成且无 PC-relative operand，预期完整 trampoline 为 284B、返回 `entry+20`。实际 ilhook 编码和恢复由新的 owned 页另外验，不能只据这个静态预期标通过。

回调先将 `any_entry_seen` 只置 true。未知 caller 除原 CPU stack return 外不读任何参数；首个准确 caller 将 `expected_caller_seen` 只置 true，并独占一次尝试，错误 name、读取失败等也终止该次观察，后续只透明执行原函数。name 最多 13 次 2B 读取（26B），payload 最多 32768 次 2B（65536B，含首次 NUL）；一次 eligible 尝试最多 **32781 次 self-RPM / 65562B**，另有原 CPU stack 的直接 8B return 读取。无 NUL/超限拒绝；自有解码 UTF-8 超过 65535B 也拒绝，该长度只在内存比较，不发布。没有把上限宣称为所有合法配置的最大值。

新 DATA 恰 72B，只有装载/EXE 代码身份元数据、两个一次事实位、固定状态、允许 env 与两个 bool。EAT 预期精确 51 项：原 47、`Sdk72ConfigEarlyProxyState @48 DATA`、新 active `sdk72_config_early_inner @49`，以及因冻结分类器/loader 源依赖而显式审计的 dormant `sdk72_config_inner @50`、`sdk72_key_input_inner @51`。只有新 inner 被唯一 hook 使用，另外两项不注册、不激活，不能泛化为其它调用许可。

`tools/verify_sdk72_config_early_proxy.py` 同时提供没有 PID/OS backend 的纯 `collect(read, modules, contract, original_rvas)` 准备。完整成功账本恰 **9 读/1450B**（state 两次、47 表、EXE 三窗/caller 一窗、284B trampoline、38B callback），max single512B。它不读参数、引用或目标函数，不解引用 userdata；仅把 userdata 当精确机器码比较输入。state 不稳定/copying/短读或代码不符一律 partial。这个纯工具尚未接入自然 adapter，也未执行第二次目标读取。

## 验收、恢复和停止

本卡先完成固定 source/window pins、两次 fresh 静态/纯工具证明和 owned 准备；主线另实际 Cargo release/locked/offline 构建，审核 EAT、新 DATA、真实 owned ABI/恢复和第三 proof。没有实际构建/owned 执行前不写“通过”。本代理不查询/控制共享进程、端口、客户端、调试器或 REA 会话，不运行上游脚本、Cargo/Gradle、DB、安装/恢复操作。

完整来源/owned/主线审核以后，任何自然实验仍须另准备固定五 stock、13 保护文件、同 epoch 产物、精确只读账本、备份/安装/正常退出/stock 恢复合同和新 user-ready；当前没有授权安装或启动。来源漂移、第二新窗预算耗尽仍缺必要语义、未知 caller、首个真实读取错误、超限或 ABI 不等即保留首失败并停止对应路径，不放开未知、不清 once、不强制目标调用、不以新一轮重启替代解释。

## 本轮实际验证与主线下一步

`source-final1/proof.private.json`、`source-final2/proof.private.json` 由两个 fresh Python 进程产生，均 **11,556B / SHA `8344e740ed4063dbcd03eac62ffdc7e6dc828987cecad5f60b4996b00a96e033`**，逐字节一致，22 项静态断言通过。固定五 stock、冻结 loader/classifier/assembly 等 29 pin、四份既有 proof、四个复用完整窗、两个新完整窗、原始类型/instance 声明与尾调用 return 均在报告中。`source-negative.private.json` 在内存改坏首新窗 expected SHA，准确拒绝于该源窗，没有修改二进制或放行后继。10 项 Python 检查覆盖完整 284B 和返回边篡改、输出保密白名单、exact EAT、facts/event 非法组合、9 读预算、caller/code 窗篡改、读取错误、不稳定/copying 和固定失败投影，全过。

这些是 **静态和纯工具** 结果。18 个真实 owned hook case、自有高64代码/参数、完整 ABI、实际 ilhook 搬移/drop/失败、实际 DLL EAT 和装载均尚未执行，不写通过；完整 EXE Marshal/CRT/GC、自然输入和 D-02 仍未验。没有 Git、Cargo/Gradle、DB/端口/进程查询、安装、服务或客户端操作，未部署原因是主线构建/owned/自然审核尚未完成，不能以研究结果代部署。

主线已取得本卡来源与依赖的构建空档后，以新的本卡 target 和输出执行；不改旧 example/Cargo 配置。`--crate-type cdylib` 是本例的独立 DLL 构建方式，实际 Cargo 是否接受该 example override 仍由此次构建确认；若拒绝，保留错误，由主线处理最小 target 声明，不能伪称已产生 DLL。

```powershell
$earlyGamePath = & ./tools/game_path.ps1
$env:CARGO_HOME = (Resolve-Path local/cargo).Path
$env:RUSTUP_HOME = (Resolve-Path local/rustup).Path
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'local/migration72/d02-resume-20261109/r2-early-bridge/target'
$earlyDef = (Resolve-Path local/migration72/d02-resume-20261109/r2-early-bridge/source-final2/config-early-proxy.def).Path
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_early_proxy --crate-type cdylib --release --locked --offline -- -C "link-arg=/DEF:$earlyDef"
if ($LASTEXITCODE -ne 0) { throw 'Early observer DLL build failed; preserve the exact failure.' }
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_early_proxy --crate-type bin --release --locked --offline
if ($LASTEXITCODE -ne 0) { throw 'Owned early observer EXE build failed.' }
python -m tools.verify_sdk72_config_early_proxy --client $earlyGamePath --production local/migration72/d02-resume-20261109/r2-early-bridge/target/release/examples/sdk72_config_early_proxy.dll --fixture local/migration72/d02-resume-20261109/r2-early-bridge/target/release/examples/sdk72_config_early_proxy.exe --run-owned --output-directory local/migration72/d02-resume-20261109/r2-early-bridge/owned-main1
python -m unittest tools.test_verify_sdk72_config_early_proxy -v
```

owned CLI 只启动新自有 EXE 两次，校验严格白名单后才保存脱敏结果；不加载真实客户端 DLL，不将任意 stdout/stderr/异常正文写入 proof。主线须逐项复核产物/source epoch、51 EAT、72B DATA、18 例 ABI/透明性、完整 284B 实际生成/恢复和第二次 fresh 结果，再安排独立第三复验。若真实编码与静态模板不等，先保留完整 owned 失败并按冻结 ilhook/iced 源解释，不能放宽成只核后缀或摘要。只有这一出口通过，才考虑自然 adapter 与另一次准确准备；当前纯 `collect` 本身不启动任何目标。

前期导入失败均发生于 PE 读取前：private helper checkout root 层数及默认 Python 未带 Capstone，已改为正确 root 和既有 `local/migration72/python`，没有安装依赖。大型 minified cache 不能直接文本 grep 输出整行，应结构化投影；本卡最终只保存准确静态/类型投影，没有复制自然配置、key 或 URL。早先“managed→UTF8 原桥已执行”和读取 `+10` 长度的假设均已纠正，不作为后续来源。

## 主线构建与owned实际验收

主线实际第三source proof与双fresh逐字节相同（11,556B/SHA `8344e740ed4063dbcd03eac62ffdc7e6dc828987cecad5f60b4996b00a96e033`），22静态断言及10项Python检查通过。已审核本卡实际Rust、fixture、pure verifier与scope；原Marshal只读证据不升级为CPU执行，strict UTF16分类也不声称与原非法代理处理等价。

首次真实Cargo命令退出101：自动发现的example被认作bin，不能覆盖crate type。保留`build-dll-main1.log`后，主线仅在Cargo.toml按既有模式新增本example的`crate-type=["cdylib"]`声明，依赖/lock/旧example不变。DLL继续显式cdylib，owned EXE改为上面的显式bin覆盖。修后release/locked/offline实际DLL构建8.84秒、fixture6.1秒均退出0；构建helper/旧manifest/前后SHA留本卡忽略目录，没有运行上游脚本。

DLL为577,536B/SHA `20186081e073678510d0c162e6bbab1597e8f00b3154861809e90935932854a9`；owned EXE为726,016B/SHA `eb498ab5fbacf1625d6126ae36c70fe40cafe46a56d344379675d1e77ce942f5`。实际PE核AMD64/DLL、51个准确导出和72B可写非code DATA；47原forward及两个明确dormant inner来源保持。两fresh自有进程各18case通过，原调用一次、high64、GPR16/XMM16/RFLAGS/栈128B、入口/返回LastError、源不变/读预算/错误拒绝/drop恢复成立；真实20B prologue在自有页的完整284B ilhook搬移及返回+20逐byte核过，没有执行目标EXE/SDK代码页。

主线另12项独立审核核29冻结pin、5个新source、三份静态相等、source/manifest/lock、两个实际artifact、两个fresh结果/18case、所有ABI/隐私断言和完整emitter，证明`main-owned-review.private.json`。`owned-main1/proof.private.json` SHA `394964693753e2f52e12cc298d8502ae7bcd8a0094536f48a8b703c2a71c1b04`。Cargo manifest本次新SHA单独记录，不冒充继承旧source pin。

尚未安装客户端、读取自然managed输入、验证完整原桥/SDK调用/信任或部署GS。下一项是新独立adapter与可恢复脚本；准备和实际现场分别验收，另等新user-ready。R4、完整Avatar/资源前置尚未闭，不部署猜测协议。

## 新现场adapter的owned与真实文件门禁

新增`tools/observe_client72_config_early_proxy.py`，13,697B/SHA `d2f9a6f0a8d69e6eacba65c838fd9f25bfe4c12167000349dbc05a162ab5702b`；测试22,957B/SHA `94d0659582517fc37c058a8982d22a603c3653a5e2b5f4fe2e0fb93bb3fcd9a0`。旧adapter/verifier不变。新入口先核准确accepted proof、两fresh18例、实际fixture/artifact、29冻结pin/新source/Cargo及binding/ref来源，再按安装布局明确核四stock常规路径、原Astrolabe alias和已验观察DLL；不调用或放松原五stock的旧门禁。

独立raw侧账仅保存纯collector**同次**最多9次/1450B/单次512B返回的固定code、pointer与自有72B DATA，没有额外RPM或managed payload读取。完整90秒monitor字段仍保存在主报告；三模块必须唯一且path/size/hash/base与同轮前后身份一致，末尾磁盘核对之后再查存活，finally关闭句柄。退出/创建身份变化/模块变化、短读、异常及中途失败保留实际raw且拒绝成功，不输出任意异常正文。事件是否发生与code-bound状态分开，未发生不冒充成功配置。

主线实际运行新增16项测试通过，另13项R1适配器生命周期测试同次通过；已审新adapter实际源码与各测试、实际accepted source gate。主线12项独立检查核9次同源raw/1450B、完整284B trampoline、两次72B DATA、三模块、前后metadata与最后存活、真实close、没有payload/reference/nativeMarshal宣称等，证明`adapter-main/main-review.private.json` SHA `0c2fc894db874bac0159751292357ace843ab23938fa31ca18a5e7affc12602b`。

另在忽略目录构造隔离文件布局：六个只读输入hardlink（固定五stock与SDK carrier）和一份已验proxy副本，实际执行未mock的`preflight`，五逻辑输入、47转发目标、binding/真实私有reference全等通过。证明`adapter-main/source-gate-owned-layout.private.json` SHA `e4d830b0b7179f0d7349a6fc4704ef960a353f2ef5da525ab41612489b16440b`。没有启动该布局或改动原文件；这只验证真实文件门禁，不等于GAME_PATH已安装、WinAPI现场或自然事件通过。

下一步合并R1新warm usage/optionalString纯图及新adapter后，封存主线脚本、备份/恢复和全源hash，复核当前进程/端口/保护输入，再请求新实时ready。所有历史ready已消费，尚无本轮安装或客户端启动。GS的R4前置仍不足，不部署。

上述准备现已完成：14源pin的合并计划、主线安装/监测及恢复脚本封存，实际CheckOnly核五stock/13保护、无alias/相关进程/固定端口通过。脚本hash、准确总预算及R1独立失败处理见[现场准备](CLIENT_72_AVATAR_WARM_USAGES.md#主线第三与新windows适配器)。安装分支仍需新实时ready，尚未运行；GS继续未部署。

## 本轮自然实测及停止结论

实际启动经历与修复见[R1合并实测](CLIENT_72_AVATAR_WARM_USAGES.md#本轮合并实测与收尾)：后台PowerShell Utility未加载，以及未提权CreateProcess入口，两次均未启动客户端或读取target；临时安装立即还原。补模块路径、明确Utility及安装前管理员guard，普通token负例/后台CheckOnly通过，再用用户已授权的UAC入口进行同范围一次自然实验，DLL和观察代码不变。

实际90秒45sample，9次1450B固定code/72B DATA读及其同次raw账本完整。47原转发目标、EXE入口20B patch、完整284B trampoline/返回+20、callback以及其余准确原代码窗均相符，前后DATA相同、前后身份及最后存活通过。`anyEntrySeen=true`、`expectedCallerSeen=true`：准确caller确实到达新桥；event5为`over-limit`，观察器按预设上限停止。**没有分类成功，也不能从这个合并错误码判断是name还是payload超限，更不能推断配置正文大小、内容或缺NUL。** 未保存自然字符串/密钥/URL及其hash/长度；没有扩大预算、改参数、校验或原转发。

实测主报告`live2.private.json` SHA `d7f1f483ae293069dbd4611837377c4b076674d7fe04cf42c9a1cc1f79488771`；同次9读账本SHA `ad4fe1971aea1a3db7fd384b65d2596cd94560b69480cf9a127ccbf7dcc79c0b`。主线按该账本离线纯重放完全相等，合并14项审核及退出恢复证明见R1。本次完整Marshal/export/writer return/路由/校验/账号认证仍未验证；code-bound与exact caller不能替代这些验收。

用户确认正常登录页、正常退出无异常；stock已恢复、alias移除，五输入/13保护保持，相关进程/固定端口0。用户现要求当前工作收尾后暂停，故本次观察/数据保护已收尾，不启动扩大字符串预算、额外hook/SDK路由或后继实验。父项仍进行中、D-02未完成；恢复前需新的指令、准确假设与准备，以及新的实时ready。
