# R2-CONFIGSYNC-OBSERVE：准确同步入口观察

当前主线已完成release构建、两次自有进程原生验证、15项Python检查及12项独立审核；实际客户端尚未安装/启动。本页下方“夹具要求”和“未执行”描述的是源码冻结时阶段，实际结果以下方主线验收为准。

## 实施前计划

本卡承接已验 `local_config → 原handler lookup → 378850入口` 与[托管生产链](CLIENT_72_SDK_CONFIG_SELECTION.md)，目标是取得自然配置同步事件的有限分类，解除 R2 自然 env/资源等值关系的具体缺口。已有零hook加载、47转发、SDK绑定/持有、ilhook wrapper 和 key核心直接复用，不重做SDK响应或安装旧整DLL。

实施仅新增独立 config proxy example/观察核心、owned fixture、必要的 example 声明及审计/观察工具；旧 clean/key 核心、loader、table、Cargo.lock与生产 ext/lib.rs 全部冻结。允许复用旧 SDK loader 的 `wait_loaded/bind_loaded`，**不调用 key activate/install，不安装 key 或任何重定向/信任 hook**。若复用旧绑定模块在产物中留下 dormant 符号，必须逐个核 EAT 并说明用途，不能放开未知导出。

唯一 hook 为固定 SDK `HoYoSDK_ConfigSync` / RVA13d5b0，必须固定原窗口和实际模块身份；原调用/返回/寄存器/flags/XMM/栈/LastError保持。入口只接受准确原 EXE `AccountPlatNative.ConfigSyncWithNative` 返回地址 `B+6d809cf`（实际代码窗亦核对）；未知caller透明通过且不读正文。准确 name 逐字节匹配 `local_config\0` 后，才在原同步调用的参数生命周期内复制有界自有bytes。一次捕获最多65536B，仅为观察上限，超限保持固定失败，不声称全部合法配置都必能通过。

自有副本仅在内存按已审字段分类：既证CN env代号、对应固定资源 app_key 相等bool、url:string中 comboLogin 与同源资源相等bool。不得记录或hash自然正文、key、URL、device ID、账号/token或它们的长度。固定引用由忽略目录的已审 class49原文生成，只在内存/忽略构建输入使用，保留来源与完整资源extent等值证明，不把敏感材料写入patch源或共享文档。

产物按阶段独立验收：源码/保密分类和正反输入 → 实际release构建/固定EAT/原生owned ABI与透明原调用 → 主线审核新准确读取与恢复合同 → 等新user-ready → 单次90秒实际加载及只读state/code身份观察 → 用户正常退出/stock恢复与保护文件核对。代理不能调用Git、Cargo/Gradle、安装、DB或客户端；主线统一调度。原始资料在 `local/migration72/d02-resume-20261109/r2-config-observer/`。

owned验收须覆盖准确caller/name、非目标不读正文、env/shape/reference失配及固定失败、正文哨兵不输出、实际ilhook原调用一次/原返回/Win64 GPR/XMM/flags/stack/LastError与drop恢复；只在自有模块/代码运行，不调用真实SDK。实际入口、材料、ABI或装载不满足即停，保留首失败；不启额外hook，不换协议/版本/校验，不无假设重试。自然事件分类成功也不等于writer返回、路由生效、认证或D-02通过；这些边界分别保留。

## 独立实现与当前边界

新增 `patch/examples/sdk72_config_proxy.rs`、`sdk72_config_proxy/observer.rs` 与 owned fixture/assembly，Cargo只追加一个cdylib example声明，fixture由现有自动example发现。原clean core/table、key core/loader、生产lib、旧工具与lock不改。引用旧key模块是为了使用原loader的真实类型；入口只调用`bind_loaded/wait_loaded/pin_self`，没有key prepare/activate/install，也没有导入旧整DLL入口。

来源生成器 `tools/verify_sdk72_config_proxy.py` 固定既验main-third proof和原资源carrier，调用冻结class49读取器重核完整缓存container/type tree及全文extent相等，再在内存生成八个CN env引用。`references.private.json`只在本卡忽略目录供`include_bytes!`，报告不含它的长度、正文或hash；`binding.private.rs`只含固定EXE/SDK代码窗与磁盘身份。没有重解包或重新执行资源native decoder。最初metadata比较因tuple与JSON数组表示差异拒绝，未写build输入；现先把metadata按JSON值规范化再完整相等比较，原失败留private记录。

唯一安装点为SDK `13d5b0`。SDK窗为`13d5b0/96B`、`1343b0/64B`、`36fba0/64B`；EXE窗为`6d80910/224B`、`6e363b0/64B`、`19807960/64B`，并固定实际EXE/SDK路径、磁盘、loaded SizeOfImage和模块持有。worker先核EXE及typed引用，再等待SDK，减少发现SDK之后的EXE整文件hash延迟。**Astrolabe首转发、worker调度和自然首次ConfigSync的先后未验；事件可能早于hook安装，no-matching-event必须保持未知。** 不为此增加额外hook或重试。

入口先检查原CPU栈返回地址等于本次EXE base+`6d809cf`；未知caller连name也不读。准确caller才最多逐字节读`local_config\0`，近似name透明通过且不读payload。第一条准确name事件独占一次同步观察：按自进程RPM逐字节复制，首次NUL即停，不读NUL后字节；总读取上限65536B，含终止NUL，未终止/超限/读失败仅发布固定失败，原调用仍照常执行。原临时Marshal字符串的同步调用边界不扩成异步线程读取许可；不保留自然payload指针或正文。

只有自有副本进入JSON分类，env须是八个既证CN代号，app_key及内嵌url:string/comboLogin只做引用等值bool。错误不格式化serde异常或正文；实现没有catch_unwind、全局panic hook或假设性panic兜底。没有已知可达的输入驱动panic：边界/类型/JSON错误均返回固定状态；不宣称覆盖OOM、异常展开、YMM/ZMM或x87/MXCSR。callback沿用已验XMM4/5+完整shadow wrapper，Get/SetLastError包住观察核心，ilhook保存其余已验Win64状态。

固定EAT共50项：原47转发、`Sdk72ConfigProxyState @48 DATA`（80B）、`sdk72_config_inner @49`、`sdk72_key_input_inner @50`。最后一项为冻结真实loader类型依赖带来的dormant原函数，必须单独确认代码EAT及冻结源；不允许任意额外符号。80B DATA只包含装载/代码身份元数据、固定事件状态、env代号与两个bool，不含正文、自然指针、长度、hash或事件计数。

owned fixture只把自有assembly模板放入自有高64位内存，不加载/调用真实SDK。16个实际hook case设计覆盖准确事件、double env、两个引用失配、未知env、JSON/UTF8/嵌套形状、65536B未终止及最后一字节NUL、未知caller、近似/大小写name、不可读name/payload；另核八个CN分类及三类引用拒绝。每次原调用一次、high64返回、GPR16/XMM16/RFLAGS/原栈128B/入口与返回LastError和源payload保持、重复调用及drop恢复；实际无效x64入口的ilhook失败须无写且不报installed。再在另一自有页写入准确15B ConfigSync prologue，只安装/读回/drop、不执行该页或callback；Python必须核完整实际279B trampoline、patch、userdata/callback绑定及`entry+15`返回，才能接受owned报告。报告中的代码与地址仅来自自有生成页，不含客户端内存或自然输入。这些当前是**待主线实际构建/执行的夹具要求**，不是已经通过的原生结果。

只读观察器 `tools/observe_client72_config_proxy.py` 在明确新ready的CLI才启动90秒module monitor。它要求同一来源epoch、准确50 EAT、private引用来源等值及主线两次owned报告后才接受产物；不远程调用DATA getter或任何函数。最多11读1429B：state前后、47转发表、SDK三窗、EXE三窗、完整279B trampoline和38B callback。原搬移15B无PC-relative operand，必须完整核原编码及`entry+15`返回。code/userdata只用于准确代码相等比较，不解引用userdata或私有引用；事件DATA只白名单投影，copying/状态变化/短读保持partial。

## 主线构建与验收入口

先核本机GAME_PATH、portable Rust/MSVC/CARGO_HOME/RUSTUP_HOME；本卡专属`CARGO_TARGET_DIR`为忽略目录的`r2-config-observer/target`。生成器只读现有stock输入，private输入重复生成必须逐字节相同，不能覆盖不同材料：

```powershell
$taskGamePath = & ./tools/game_path.ps1
python -m unittest tools.test_verify_sdk72_config_proxy tools.test_observe_client72_config_proxy -v
python -m tools.verify_sdk72_config_proxy --client $taskGamePath --prepare-build-inputs --output-directory local/migration72/d02-resume-20261109/r2-config-observer/source-review-new
cargo rustc --manifest-path patch/Cargo.toml --example sdk72_config_proxy --release --locked --offline -- -C link-arg=/DEF:<source-review-new/config-proxy.def绝对路径>
cargo build --manifest-path patch/Cargo.toml --example sdk72_config_proxy_fixture --release --locked --offline
python -m tools.verify_sdk72_config_proxy --client $taskGamePath --production <本卡target/release/examples/sdk72_config_proxy.dll> --fixture <本卡target/release/examples/sdk72_config_proxy_fixture.exe> --run-owned --output-directory local/migration72/d02-resume-20261109/r2-config-observer/owned-review-new
```

owned执行工具只持久化严格白名单通过结果；未知stdout/stderr及异常正文不写报告。主线须另审实际源码/构建/产物SHA和EAT、owned ABI/原调用、完整trampoline原编码/恢复，再按原计划准备备份/安装/恢复及新user-ready。只有以上出口足够，才运行本卡观察CLI：`--client <本机GAME_PATH> --production <审核DLL> --artifact-audit <owned-review-new/proof.private.json> --user-ready --output <本卡新private文件>`。该CLI不包含安装/恢复操作，不能由代理调用或沿用旧ready。源码阶段15项Python纯测试通过；原生结果与实机部署由主线分别验收。

## 主线实际构建与自有验证

主线重新核portable Rust nightly 1.101.0（2026-10-20）、本机MSVC后，在专属target执行上述两条release/locked/offline命令，production/fixture实际分别9.61秒及3.97秒成功。仅有既有未用依赖等warning；未更改Cargo.lock或旧核心，未构建/安装生产ext替换入口。

| 实际产物 | bytes / SHA-256 |
| --- | --- |
| `target/release/examples/sdk72_config_proxy.dll` | 616,448 / `702adc01b2e850c78cee52d6b4639ab22680ea56be54dca57b82290c3eeb350b` |
| `target/release/examples/sdk72_config_proxy_fixture.exe` | 648,704 / `99f9fe07b7bba4a6d6b7750118de285853af7cc7c70510b3125c96ba29e9d602` |
| `owned-main1/proof.private.json` | 19,755 / `fbca1b25d668b30d92c3b129a9ef67ffc9218add4e65c910224c35fa8a25bd97` |

审计工具实际启动两个新fixture进程，16个hook正反例、八个CN分类、三类引用拒绝、GPR16/XMM16/RFLAGS/原栈128B/LastError、原调用一次/high64返回/源payload保持、重复调用/drop恢复及真实ilhook失败无写全过。两次另有实际279B搬移代码安装/读回/drop，准确原15B和`entry+15`返回一致；该搬移页没有执行，不混同ABI callee的实际执行或真实SDK。15项Python检查主线实际重跑通过。

主线另核9份新源码与来源冻结epoch、20旧依赖pin、精确50导出名/ordinal及80B DATA、实际产物SHA、16例结果与279B尾部、唯一安装点及无key激活，共12项，见 `main-owned-review.private.json`。原始构建日志留本卡忽略目录。

新 `run-config-live1.ps1` / `restore-config-live1.ps1` 已生成并通过PowerShell AST解析；启动前会重新核当前GAME_PATH、五stock、13保护文件、当前进程/端口、来源epoch及准确产物，再备份stock、创建本轮orig别名及临时安装本独立DLL。恢复仅在实际客户端退出且安装文件/备份/别名均匹配时执行。主线准备盘点见 `main-preparation.private.json`：五输入和13保护文件保持、相关进程/TCP/UDP均0、无orig别名；尚未安装。下一步等待新的实时ready后单次90秒观察，collector上限11读1429B；自然首次事件是否晚于worker安装仍未知，无事件不能反推未发生。完整路由/信任/认证与D-02仍未验；R4前置未齐，GS不部署。
