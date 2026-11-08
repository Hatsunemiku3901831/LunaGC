# R2-KEY-LOAD：独立入口接入已验key核心

当前限定实现、release构建和主线第三owned验收已通过，实际安装/目标hook接受尚待新的无凭据实机观察。原核心与clean转发源码保持；没有启用旧整DLL。观察合同另见[实际窄hook计划](CLIENT_72_KEY_LOADER_OBSERVATION.md)。

当前状态：独立opt-in生产DLL与owned fixture已由主线离线编译；实际EAT严格为原47+state数据@48+冻结核心内部符号@49。新runner两个fresh owned进程各917B、逐字节相同，12项独立Python检查通过。生产源码/578048B产物已冻结。本卡没有加载真实SDK或调用getter/目标函数，没有安装客户端文件；目标hook接受、自然key回调、route/trust/认证及D-02仍未完成。

## 实施前计划（2026-11-08）

目标是在已实际通过的透明入口上，独立构建一个只接准确SDK Crypto key输入的显式实验产物。复用未改的核心与47导出转发，不把旧ext.dll或其它模块带入。当前已有核心自有ilhook ABI、同一核心→真实caller/PEM/RSA/Base64桥梁、真实零hook加载、实际SDK入口253B与两caller各76B绑定；自然key回调、本服route、信任与完整运行仍未验。

任务分两步：先实现/构建/自有测试并审可运行产物，再准备新用户ready后的正常加载、实际hook状态与校验影响观察。第一轮不输入凭据；真实认证待准确route/HTTP与运行接收方案汇合，不能把SDK页面或hook installed记为D-02。若系统/客户端拒绝该窄hook，保留失败并回退，不改变完整性验证、异常门或模块可见性。

固定源：核心 `patch/src/modules/sdk72_key_input.rs` SHA `67af556fea165d1597741cee2bbf2d310542c6c5c9637e44ee8877f4cc885bcd`；独立透明入口源/asm见 `clean-loader/offline-artifacts-main1.json`。核心只接受两caller返回RVA34eeb7/34ef88、this VT6d1cb8和固定stock public，唯一写入为入口stack+28第五参数；R8内容不读。原SDK磁盘SHA及原入口/callerSHA按[真实加载](CLIENT_72_CLEAN_LOADER.md)，不能仅GetModuleHandle非空便装hook。原公共材料两文件在既有sdk-key-input-sol目录，长度/hash由核心入口核验；不读取私钥、不调用目标getter取材料。

允许新 `patch/examples/sdk72_key_proxy.rs`、必要的同前缀私有子目录/owned fixture、新 `tools/verify_sdk72_key_proxy.py`、对应test和本页。Cargo最小example声明由主线添加，依赖/lock及旧lib/modules/clean-loader核心/asm/SDK key核心全部冻结；不加旧ccp/memguard/swap/NEUTRALIZE、异常处理器、route/trust、注入器或调试器。原始材料/产物/日志仅 `local/migration72/d02-resume-20261108/key-loader/`，不自动安装。

入口继续在DllMain仅记录本模块，不在loader lock内加载/改代码。首次正常转发解析原库后只启动一次普通有界初始化worker；worker只等待已加载SDK（总预算90秒），绝不LoadLibrary SDK。它不声称自然Init完成或深读Config安全区间。绑定实际路径/文件身份、SizeOfImage、SDK磁盘及三个准确原窗后，准备同一核心并用已验ilhook2.3.0 JmpBack/原callback安装唯一3950c0入口。核心、hook及其模块都需在回调整个进程期保持；不建立通用并发卸载管理器，不重试未知身份或覆盖已有hook。

SDK未加载/材料错/原窗不符/安装失败均记录固定状态且不装hook，保留原转发；该状态不被当成本服ready，研究时不输入凭据。可提供一个只含schema、初始化阶段/错误、实际模块绑定和安装结果的固定数据export供主线RPM，不放账号/key/token/body/cipher或其hash；不远程调用export或目标函数。复用原callback，不为统计复制分类逻辑或引入未验ABI wrapper。若实际必须新增callback状态采集，先返回明确必要性及自有ABI验收方案，不悄悄加进本卡。

验收出口：主线离线release构建与导出/源/产物身份，最小owned加载/错误拒绝/材料和原窗守卫，未改核心的既有ABI/原生证明仍对应同一源码/依赖；新loader逻辑须有有效失败与相应回归。随后实际实验再核安装产物、进程/模块/代码变化/固定状态、SDK页表现、正常退出和恢复。只证据充分的子出口标完成，R2-KEY/F-04/D-02继续未完成。

停止条件：任何固定源漂移、实际loaded identity或原指令不符、未知初始化/所有权边、目标校验拒绝或回调透明性失败。先查可验证输入，不重复旧HW/SW/ETW，不为不明确的生产生命周期添加复杂机制。主线独占Git/Cargo/Gradle/DB/端口/client/安装；子代理只交源码、自有输入/测试与精确构建命令。

## Rust实现前的具体设计与未闭边（R2-KEY-LOAD）

本节先于新Rust源码记录。R1 fresh-ancestor原三文件与raw已冻结；本卡只新增本页许可的独立example、同前缀owned/private源及Python审计/测试，raw只进`d02-resume-20261108/key-loader/`。不自行Cargo/rustc/构建、Git、客户端、调试器或安装；已有核心ABI与原Crypto桥梁不机械重跑。

入口直接包含冻结clean core/table/assembly，47个导出名称、ordinal和冷/热thunk一字节不改。`resolve_original`成功取得原库后，用一次性门启动普通worker；这是首次冷转发的**原库解析成功之后、原callee尚未返回**。冻结assembly没有“原callee返回后”通知；若必须严格等第一次原callee返回才启动，就需要新增转发ABI路径，当前范围不做该变更。DllMain继续只记录HMODULE。worker启动失败只发布固定错误，原47转发继续，不重试未知身份。

worker总等待预算90秒，只查询已加载的准确SDK。首次找到时取得该已加载模块的自有引用，验证实际Windows路径、SizeOfImage、磁盘SHA及三个准确窗口，失败释放自己取得的引用。只用GetModuleHandle/GetModuleHandleEx，不调用SDK的LoadLibrary或getter。验证成功后用同一磁盘bytes及两份明确public-only材料调用冻结`Sdk72KeyInput::prepare`；材料拟以相对忽略目录的`include_bytes!`嵌入构建，运行时没有寻找私钥或环境变量换key的分支。构建审计先验证两public文件的长度/hash，原核心再次执行其已验入口门。

唯一安装点为`base+3950c0`，`HookType::JmpBack`直接使用原assembly callback，userdata指向稳定Box中的原核心；不加统计/ABI wrapper。成功后将核心、HookPoint和SDK自有模块引用放入明确进程期holder；holder不Drop、不建通用卸载管理器。启动worker前固定本proxy模块的进程期引用，避免线程或原callback指向已卸载的本DLL。没有SDK、guard失败、prepare失败或ilhook返回Err，都不发布installed；不以任何失败当ready。

公开诊断拟为一个64B、`repr(C)`的固定**数据export**，只有schema/size、阶段、固定failure/Win32数字、actualSDK base/size、path/disk/三窗验证位、original/self/SDK引用状态和installed。无账号、key/public正文、body/cipher、hash、callback计数或任意字符串；主线只RPM读此数据，不远程调用导出。installed只在全部guard、prepare和hook成功后发布，仍不代表自然调用或D-02。字段先写、阶段最后release发布；观察器只接受稳定终态及其对应字段组合。

owned测试只加载自写DLL或使用自有可执行内存：新增loaded-binding边界通过同一三窗validator验证owned模块正例，以及缺模块、path、size、disk、三个窗各自漂移、读取范围/失败拒绝；两public材料进入同prepare路径，错误材料拒绝在安装前；自有非法指令入口产生真实ilhook错误且code不变。成功安装/holder验证只在owned代码，用原callback，绝不执行真实SDK函数或getter。旧47转发ABI沿已审未改table/asm复用；新生产产物仅可在owned测试中读取固定state，不调用其Astrolabe转发导出。Python仅持久化精确白名单的固定布尔/阶段/长度与源/产物身份，未知stdout/stderr不保存正文或hash。

当前已识别的边界：

- 冻结核心含私有`#[no_mangle] sdk72_key_input_inner`。[Rust Reference](https://doc.rust-lang.org/reference/abi.html#the-no_mangle-attribute)明确该属性会导出library/object符号，因此cdylib可能自动产生这个既有内部符号。当前尚未构建，不能宣称最终EAT只有47+state；首次产物审计必须严格发现它。若实际多出，主线须明确处理准确既有符号或独立链接出口，不能修改冻结核心、添加wrapper或悄悄接受未知导出。
- ilhook2.3.0在`CallbackOption::None`时，返回Err的路径位于目标JMP写入前；目标JMP写入的支路返回Ok。原库恢复保护的API返回值未被依赖库传播，此卡不改依赖。实际页面保护、并发执行时序、校验响应与自然key回调仍须首轮无凭据实机观察，不能以owned成功补足。
- 查询到SDK与自然Init/Config可读时序不同。本卡只核三窗和模块身份，不沿Config读更深字段，不宣称worker结束就是SDK业务ready。
- 生产panic/异常、YMM/ZMM/x87/MXCSR和并发卸载等超出已验普通Win64 ABI。不会为它们添加未授权管理层；正常运行若遇到具体必要边界则记录准确输入与解除条件。

## 具体实现及主线首次产物

新增`patch/examples/sdk72_key_proxy.rs`、同前缀`sdk72_key_proxy_loader.rs`和owned fixture。生产直接包含冻结clean core/table/assembly和SHA`67af556f…85bcd`的key核心，没有ModuleManager、旧DllMain、ccp、memguard、swap、route或trust模块。两份public-only PEM由相对忽略目录的`include_bytes!`进入独立产物，未读私钥或调用getter。SDK磁盘只读一次进入verified binding，再把同一bytes交原prepare；核心内部仍按其原入口重核身份。

首次冷解析成功之后只执行一次启动门；DllMain仍为冻结assembly，仅记录本模块。工作线程里以50ms上限间隔、总90秒预算等待已加载SDK；非空但身份错误是立即终止，不换身份重试。先对指定已加载SDK取得自己的引用，再核实际Windows路径、base/SizeOfImage、磁盘及入口253B/两caller76B。只有全部相符才prepare与安装。SDK不存在、路径/尺寸/hash/窗口/材料错误均不调用hook；原47转发继续。

本DLL在启动线程前以`PIN|FROM_ADDRESS`持至进程期，SDK以`FROM_ADDRESS`获得自有引用，失败只释放该自有引用；成功的core Box、HookPoint及SDK引用用明确进程期holder保留，没有通用卸载管理器。[GetModuleHandleExW官方合同](https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-getmodulehandleexw)区分了已加载模块引用与PIN语义。这里只调用原callback，不新增counter、Rust callback wrapper或目标getter。SDK是否业务初始化完成并未由模块存在推定。

主线只为生产追加了cdylib example，使用`--locked --offline`和专属target；fixture由自动example发现。首次生产/fixture构建均退出0，日志`build-proxy-main1.log`、`build-fixture-main1.log`在专属raw目录。子代理没有执行Cargo/rustc或改Cargo依赖/lock。

| 实际产物 | 字节 | SHA-256 |
| --- | --- | --- |
| `sdk72_key_proxy.dll` | 578048 | `ad18e741052a05850ff7b80b8043af0551981793dc7e3cca89be26e565a2139d` |
| `sdk72_key_proxy_fixture.exe` | 631808 | `ded080ec0ae53becfebadd764dfeb8dd434e7c0e66ad0ad78f48472aabbadce8` |

首次EAT实际为49项：原47名称/ordinal逐项一致；`Sdk72KeyProxyState @48`为DATA，RVA`8b000`；`sdk72_key_input_inner @49`为原冻结核心自动公开的内部函数，RVA`9050`。主线明确许可这个准确来源的单一内部符号，不修改核心/assembly以隐藏它，也没有放行未知额外导出。审计默认仍要求48；仅`--allow-frozen-core-inner`才要求准确49，缺此符号、改名、错ordinal或任何其它导出都拒绝。实际审查为`main1-eat-review.private.json`。没有新增可远程调用的诊断函数。

新PE审计另外逐项解码47个hot/cold thunk的24B形状：两个间接JMP必须指向同槽，push索引必须准确，冷call共用同resolver，初始376B表必须是完整64位`preferred base+cold entry`，47槽连续。当前target表RVA`8b1e0`，resolver RVA`14a0`；这两个RVA属于当前明确产物，观察器须从该产物重新解码，不能套旧clean产物地址。

## 固定state数据与错误分类

64B结构可按`<6IQ8I`解码。只有阶段5且installed=1、failure=0、绑定全通过和引用位成立，才是本工具的installed状态；依然不是客户端接受或本服ready。阶段0表示尚无首次转发启动；1为scheduled，2为等待SDK，3为绑定完成，4为core prepared，5为installed，6为failed。字段先写、phase最后release发布；主线只RPM读稳定终态，不调用任何导出。失败后的base/验证位可能是已经取得的历史绑定，sdk_held=0，不保证该模块仍活跃。

| offset | 字段 | 范围/含义 |
| --- | --- | --- |
| 00/04 | schema/bytes | 1/64 |
| 08/0c/10/14 | phase/failure/win32_error/installed | 固定阶段、固定分类、原Win32数字、0或1 |
| 18 | sdk_base | 8B实际绑定base |
| 20/24/28/2c | image_size/path_verified/disk_verified/windows_verified | 尺寸、0/1、0/1、三窗bitmask7 |
| 30/34/38/3c | original_bound/self_held/sdk_held/reserved | 引用/绑定标志及保留0 |

所有offset均为hex。state不含账号、key/PEM正文、body/cipher/hash、callback计数、路径字符串或任意诊断正文。

错误101自身引用，102自身路径，103线程创建，104SDK等待超时，105SDK引用，106SDK路径，107ModuleInfo，108SDK尺寸/窗口范围，109SDK磁盘，110RPM失败，111窗口身份，112材料/prepare，113ilhook安装。原stock绑定失败仍沿冻结clean core的固定分类和非返回异常出口，不被新worker成功码覆盖。

## 实际owned结果与失败留存

runner先核原五输入、冻结core/clean源和两public材料，再审生产EAT/64B初始state/47机器码。只允许SHA`7688425c…a69d0`的已有自写mock；stage为`OwnedKeySdk.dll`、另一错误路径副本及新proxy。两个子进程均只LoadLibrary自写mock和新proxy的DllMain/数据export，不调用生产Astrolabe导出，也不LoadLibrary真实stock或SDK。真实SDK文件只作为prepare的固定磁盘输入读取。

实际正例为同一三窗validator的owned模块路径/base/size/disk/window绑定，窗的expected hash来自独立PE磁盘映射；caller释放其原引用后verified引用仍保持模块，最后drop该引用才卸载，且所有拒绝支均未泄漏引用。

| 真实拒绝 | 预期固定分类 | 已实际核对 |
| --- | --- | --- |
| SDK不存在，零预算执行同wait/activate | 104 | 无自动加载，phase6/installed0 |
| 实际loaded路径与指定existing路径不同 | 106 | 拒绝 |
| SizeOfImage不符 | 108 | 拒绝 |
| 磁盘身份不符 | 109 | 拒绝 |
| 三个窗逐个错误hash | 各111 | 三例分别拒绝 |
| 窗口RVA溢出 | 108 | 拒绝 |
| 自写mock页临时NOACCESS | 110且真实Win32非零 | 实际RPM失败，随后原保护恢复 |
| local public错一字节、stock public错一字节 | 各112 | 两例均在安装前拒绝，owned code不变 |
| 自有x64非法指令入口 | 113 | 真实ilhook返回Err，目标64B不变，installed0 |

上表loaded-binding为9例（含三窗各一例和absent），材料为2例；ilhook真实错误另列，不重复计入9或2。成功安装仅在自有NOP+高64位返回callee上使用原callback；原callee返回`8000000100000001`，drop owned HookPoint后64B完全恢复。没有为数目重跑原14个完整ABI案例，也没有把本次简单callee当完整ABI证明。

`verify-owned-agent1/`及最终工具epoch的`verify-owned-final1/`均完成两fresh EXE、exit0/stderr0。每份公开JSON为**917B**，四份逐字节相同，SHA **`72b0759e370e6d5c019387e97b7ee8715dac382ad4d860c82a9743e5c22121a8`**。公开JSON只在精确字段/类型/值、exit0和空stderr全部相符后落盘；未知stdout/stderr仅留长度与固定失败分类，不保存正文或其hash。一次失败立即停止，不覆盖旧输出目录。

新增12项Python检查全部通过，覆盖准确49许可、DATA初始字段/权限、forwarder/ordinal、47 thunk索引/slot/resolver漂移、完整64位表指针、白名单/真实退出/未知正文拒绝及Windows实际LF字节写出。首次测试收集因一处subTest语法漏冒号失败，原`python-tests-first.log`与当时test/runner源字节归档保留；只修该测试语法后`python-tests-green1.log`通过。Rust/owned首轮没有失败，不把预期错误输入拒绝写成工具故障，也不覆盖原失败证据。

源码与产物分别记录：生产root3724B/SHA`ce325a8b795739725a52abcd5e68833a93ea7b3ee115c21e932ed86d5fe1119d`；loader9274B/SHA`c31f9b5f61d68669e54e55f5f8765fb89968417def1ed9679deaee1f1c9c76a4`。二者与主线编译前`prepare-source1`逐字节同源；fixture12572B/SHA`ec6bc526987f3de45f29d05ffa9f747686e7d413d214747bdb2a54552cf9d0f7`。编译由主线完成、artifact身份为实测，不能仅因源码相同声称不同build二进制可重现。

## 主线独立第三命令与后续

主线当前artifact已冻结，第三无需重新构建。先用新的专属输出目录：

```powershell
python tools/verify_sdk72_key_proxy.py --client $env:GAME_PATH `
  --output-directory local/migration72/d02-resume-20261108/key-loader/verify-main-third `
  --production local/migration72/d02-resume-20261108/key-loader/target/release/examples/sdk72_key_proxy.dll `
  --allow-frozen-core-inner --run-owned
python -m unittest tools.test_verify_sdk72_key_proxy -v
```

runner默认fixture为同target的`sdk72_key_proxy_fixture.exe`，默认mock为已验clean-loader的自写normal mock；两者先核身份再stage。不复制真实SDK到stage、不调用getter、不加载真实SDK。预期两新owned报告仍各917B/上述SHA，12项检查通过；这不是client hook许可或验收。

仅在主线明确需要重建时，原精确构建入口为：专属CARGO_TARGET_DIR=`key-loader/target`，保持本机已核CARGO_HOME/RUSTUP_HOME；`cargo rustc --manifest-path patch/Cargo.toml --example sdk72_key_proxy --release --locked --offline -- -C link-arg=/DEF:<prepare-source1/key-proxy.def绝对路径>`；`cargo build --manifest-path patch/Cargo.toml --example sdk72_key_proxy_fixture --release --locked --offline`。两个.def都保留，with-core-inner版显式声明既有内部符号@49；当前已审产物来自首个def且Rust自动公开@49，不能倒写其构建来源。

下一实际任务仍由主线新ready/明确计划执行：审安装/备份/回退与实际模块身份，RPM固定state和准确代码变化，观察无凭据登录页、自然运行/退出及完整性结果，最后恢复stock。state installed不证明回调自然执行；本卡没有为统计扩callback ABI。route/TLS/认证、trust、本服入场和D-02均独立，不能借本卡晋级。子代理无Git/构建/客户端/安装操作；Cargo声明和提交由主线统一处理。

## 主线第三与提交边界

主线已实际执行第三轮，输出目录采用新的 `verify-owned-main-third/`；无需再跑上方示例的另一个目录。两新owned进程各917B、exit0/stderr0，与冻结双跑同SHA `72b0759e370e6d5c019387e97b7ee8715dac382ad4d860c82a9743e5c22121a8`；12项Python检查通过。独立标准库再核新源/编译context/产物、四份owned原字节、准确EAT49与DATA权限、已冻结core/依赖及未晋级边界，证明 `key-loader/main-review.private.json`。生产和fixture是主线先前实际构建的同一产物，没有因工具检查重建或倒写来源。

patch独立本地提交为 `e2d307227c6b5614260d63b0b73776962583dc19`，只新增key入口/loader/owned fixture及最小Cargo example；原模块、lock和clean核心/asm未变。主线审查SDK模块lease、首冷转发worker启动、固定错误、原callback及进程期holder，实际代码尚未安装客户端。本轮主线第三无游戏、真实SDK加载/调用、生产47转发或getter；实际目标校验、自然key/route及D-02继续未验。子出口完成不关闭R2-KEY-LOAD父项。
