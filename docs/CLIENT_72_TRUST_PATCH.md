# F-04c：本地信任材料、正式回调与ilhook ABI

2026-11-06。本项按[F-04b输入合同](CLIENT_72_TRUST_CONTRACT.md)实际修改安全模块，并完成本机合成hook、生产Rust回调接入离线原生密码学及便携release构建。**没有安装DLL、启动游戏/服务/数据库或操作调试器；SDK、整个DLL加载和D-02仍未验。**

## 正式改动

`patch/src/modules/security/trust72.rs`负责一次性材料解析/目标三文件哈希/两个实际内存调用点校验，以及两个原生数据入口回调。Security初始化从`LUNAGC_TRUST_BUNDLE`指定的本地JSON加载材料；没有旧版本fallback、默认成功或缺文件时放行。已删除`after_key_sign_check`及RAX=1，验签钩子由返回后的`140574A9A`移动到call前`140574A95`。

`Interceptor::attach_with_data`只把不可变的已解析材料地址传给现有JmpBack注册；旧attach继续传0，其他模块行为不改。材料使用进程期的`OnceLock`持有，不使用跨回调的可变current状态。

| 回调 | 分类及实际修改 |
| --- | --- |
| COPY，RVA`572059` | `[R12]`为100或103；原公钥完整DER指纹匹配，raw268/DER270/分配271及当前寄存器布局相符。只改R13为本地raw公钥指针；不覆盖原key字节 |
| CHECK，RVA`574A95` | 100/103必须同时持有本bundle的R13指针和本地完整DER指纹；101必须是1192字节目标私钥指纹。只改R8签名指针和保存RSP+20处的root指针；R9/长度/RAX/flags不改 |
| 未识别输入 | 不供给本地key、签名或root。原生继续按原输入判断，不宣称所有未知但有效的原厂key都会被拒绝 |

签名长度、root/public DER及固定身份在材料入口校验；回调内的来源判别是原生输入入口所需分类。实际签名是否成立仍由客户端RSA校验决定；Rust loader不把256字节长度当密码学成功。

SDK RSA回调、HTTP、代理、ccp、memguard、ModuleManager与lib生命周期均未变更。本卡不借用这些历史代码宣称全DLL或SDK适配完成。

## 本地材料与生成

`tools/generate_client72_trust_bundle.py`逐文件校验三输入并运行既有native discovery，在内存取得目标私钥DER1192；核对固定SHA-256后签名，立即放弃该DER引用，绝不写私钥文件。明确复用仓库`src/main/resources/keys/SigningKey.der`作为服务端签名者与本地root签名者，不生成新秘密。

忽略目录`local/migration72/trust/client72-trust.json`仅含：schema、三输入hash、目标公/私DER指纹与长度、root公钥PEM、本地公钥完整DER与raw、两份256字节key签名。签名覆盖完整DER字节，不签268字节raw或hash文本。Python整数RSA独立核对新签名；原生后续再次验证。默认生成文件SHA-256为`325189ba9898e6897593b88482a25f659a83f4b0e882e1b6e85c8ebc224f939b`。

生成器另外运行seed helper，输出仅用于合成ABI测试的270字节原生**公钥**夹具`native-public-test.der`，固定指纹核验后保存在同一忽略目录，不加入bundle，也不包含目标私钥。材料与原始证据输出路径均限定本checkout的忽略`local/`。

## 实际依赖与ABI验证

patch没有额外AGENTS；沿用根规范。实际便携工具链为`rustc 1.101.0-nightly (19833af72 2026-10-20)`、`cargo 1.101.0-nightly (3960fa69b 2026-10-12)`，本机MSVC 2022 BuildTools。当前忽略Cargo.lock的ilhook实际版本是2.3.0，现将Cargo.toml精确固定为`=2.3.0`，没有升级或下载新依赖。

已读该版本`x64.rs`：trampoline先`push rsp`保存hook前CPU栈指针，再保存flags/寄存器并构造Registers；其RSP不是回调自己的栈。不能仅凭这一静态解释声称可用，因此实际编译运行`patch/examples/trust72_abi.rs`：

- 本机合成函数使用真实call指令、native风格R12/R13/RDI/栈参数与ilhook JmpBack搬移/跳回；分别在COPY/CHECK前保存CPU RSP，回调比较`Registers.rsp`逐次相等。
- 六例：100公钥、103公钥成功；未知公钥、不支持的operation、错误分配长度、坏key签名拒绝。每例两callback均执行；逐回调比较Registers与栈，只允许R13或R8及第五栈参数变化，其余GPR/XMM/flags字段和原key源保持。
- 在真实磁盘三输入校验后，从固定EXE复制两个调用窗口，实际执行正式site校验器；正确窗口通过，一字节改动拒绝。另3个错误bundle身份/root/raw输入在loader拒绝。
- 合成callee按固定测试材料判断接受/拒绝，**不把它称作RSA验签**。release优化版本也实际运行，结果见`trust-patch-abi-release.json`。

## 同一生产Rust回调执行真实客户端密码学

`patch/examples/trust72_bridge.rs`是测试专用cdylib，没有DllMain或hook安装。`tools/trace_client72_trust_patch.py`通过ctypes加载它，把目标输入仅在内存映射给同一生产回调，再将实际回调输出供给Unicorn中的原生客户端。目标私钥不会写入桥文件、日志或共享摘要。

Registers的总大小及RSP/rflags/RAX偏移由实际Rust库导出，与ctypes布局比较后使用。每次实际回调都与F-04b独立供给合同对照；异常输入不会由Python伪造Rust接受结果。Python合同只作为预期，目标继续执行的数据来自Rust回调输出。

11例覆盖4成功/7拒绝、18次原生key验签：完整seed helper两组熵、坏公钥签名、未知key；Token响应成功、坏私钥签名、坏公钥签名、坏正文签名、错误密文；分区解析成功/坏正文签名。实际包含101私钥供签与100/103公钥分类。RSA/PKCS#1/SHA/验签和分支都执行目标代码，独立整数RSA、hashlib及Java4096字节MT64对照保留。错误root/另一服务端key在正式loader已拒绝，不能伪装成可加载的运行配置。

debug桥首轮11例通过；最终release优化桥两轮各11例/18次key验签/36次生产callback，完整79,105字节报告逐字节相同，SHA-256为`68e4427e947edfb82d1703685e18dce8db3f2531df4350e7a4d9af020abc2b60`。3,354字节bundle及其生成证明也双跑逐字节相同。脱敏总摘要为`protocol/cn-7.2/trust-patch-observations.json`，未把任何target private DER或机器路径提交。

## 红测、测试及构建

先在旧`after_key_sign_check`真实函数上加回归：输入RAX=0，期望仍为0；实际1，红测失败。删除该强制返回钩子并接入前置供给后，同一拒绝不变条件转绿。`trust-patch-red.log`保留首次失败。Rust库50项、Python聚焦25项均通过，失败/错误/忽略或跳过均0；这不是主线完整研究集，也不是实机验收。

实际使用`tools/local_windows.ps1 -Mode buildpatch`构建，release退出0；旧DLL先在忽略builds备份并逐hash核对。新`patch/target/release/ext.dll` SHA-256为`dec47347b6e94e9db161705909910f302fdacc39a88c86502213e9ad2713cd4c`。未复制或安装到客户端；无运行DLL/PID归属验收。本卡没有修改服务端，不执行Gradle、服务/数据库启动或健康验证。

原始证据均在`local/migration72/evidence/trust-{bundle-*,patch-*}`与同前缀logs；共享摘要只保留hash、测试统计和边界。patch独立提交`3675ea789c71a6667c995f104271d1e031c34018`且工作区干净；父工具/测试/专题另提交，父patch引用由主线审阅后接收。子模块没有Git作者配置，本次commit仅复用父仓库已配置的身份参数，没有改全局配置。

## 复现与部署尚缺的接线

```powershell
python tools/generate_client72_trust_bundle.py --client $env:GAME_PATH
$env:CARGO_HOME = (Resolve-Path local/cargo).Path
$env:RUSTUP_HOME = (Resolve-Path local/rustup).Path
& local/cargo/bin/cargo.exe test --manifest-path patch/Cargo.toml --lib --locked --offline
& local/cargo/bin/cargo.exe build --manifest-path patch/Cargo.toml --example trust72_abi --example trust72_bridge --release --locked --offline
& patch/target/release/examples/trust72_abi.exe local/migration72/trust/client72-trust.json local/migration72/trust/native-public-test.der $env:GAME_PATH
python tools/trace_client72_trust_patch.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --bridge patch/target/release/examples/trust72_bridge.dll --bundle local/migration72/trust/client72-trust.json --output local/migration72/evidence/trust-patch-release-native.json
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/local_windows.ps1 -Mode buildpatch
```

后续portable play/patch/调试启动必须在**任何客户端启动前**预检材料、固定输入与`LUNAGC_TRUST_BUNDLE`，并保证该变量确实传给启动进程。当前已有管理员x64dbg是在bundle生成前启动的，之后给PowerShell设置变量不会更新该既有进程环境；默认load继承它的旧环境。因此env-only运行供给尚未闭合，本卡没有修改未知进程环境，也没有重开或操作调试器。

主线下一项可选择明确本地材料位置/launcher接线，或在真正需要时重新建立带有已核对环境的授权调试器进程。当前初始化失败可能经既有ModuleManager.unwrap传播，而且HTTP等模块可能先于Security初始化；本卡**未验收这些失败的整体恢复或启动原子性**。不据测试loader拒绝声称全客户端能安全回退，不借本卡重写lib生命周期。

现有源码版本号与安装守卫未改，F-04其他前置、SDK/HTTP/字符串工厂、实际DLL加载及D-02仍保留待验。完成这些接线与前置后，才进行允许范围的便携安装及实机验证。
