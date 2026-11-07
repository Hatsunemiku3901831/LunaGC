# D02-R2：SDK key 输入供给核心与自有 Win64 ABI 夹具

## 实施计划（2026-11-08）

目标仅为未启用的供给核心和自有合成函数上的真实 ilhook 2.3.0 ABI 实验。主线仍负责自然入口、route、合法 loader、校验影响及实机。没有接入 DllMain、ModuleManager、默认 patch 开关，没有加载、attach、hook 或注入客户端。

证据：固定 profile 五输入；已冻结 [Crypto 合同](CLIENT_72_SDK_CRYPTO_CONTRACT.md) source/test/双 raw；主线第三原生回放 SHA `c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313` 相同且 15 检查通过；现有 trust72 自有夹具和本机 ilhook 2.3.0 源码。仅复用原生密码学结果，不重复 native crypto 或启动 REA 会话。

已知：入口 `3950C0`，准确 this vtable `6D1CB8`；caller 返回 RVA 仅 `34EEB7` / `34EF88`；唯一允许修改是入口 CPU `[RSP+28]` 的 NUL 结尾 SPKI PEM 指针。原 SDK mode0 PEM 268B，SHA `42a8cfbff43e26979202d5bc16d17602c627ac40d54dc43b48b1815e75d5daac`。主线离线提供本地 public-only PEM 276B，SHA `aea72393abf5b79744b0977715b8c8b7e7b99de55c55babeddeb901f3aefddf2`；独立公钥数值证明为 server 明确 `passport_1024` 的 RSA1024/e65537，不读、不带私钥，不要求其编码与 stock 同长。

未知：自然调用、当前 route、合法 loader、内存校验影响、生产装卸/并发/异常展开。SDK key 与 dispatch key4、SigningKey、两个 trust site 分开。本批不能使这些未知晋级。

候选与最小实验：核心只校验固定 SDK 磁盘身份、准确 stock/local public 材料，再在一次入口分类 this、caller 和当前 key；unknown 保留。自有可执行内存中放置准确 RVA 的合成 caller/callee，真实 JmpBack 回调只调用该核心。assembly 独立保存进入 callee 的全部 GPR、XMM0–15、flags、RSP、已拥有参数栈和 LastError，与不 hook 基线比较；验证原 callee 仍执行一次、返回原 output pointer、output/length 只具有合成 callee 的原生效果。观察链不读取 R8 输入 bytes。

修改范围：`patch/src/modules/sdk72_key_input.rs`、`patch/examples/sdk72_key_input_abi.rs`，必要自有夹具文件；仅本专题。所有 raw、构建和日志只进 `local/migration72/d02-active/sdk-key-input-sol/`。不改生产模块注册、Cargo 依赖、Java、Proto、配置、资源、版本、ready 或 required。主线处理 Git、共享索引及后续审核。

失败出口：材料/模块身份、this/caller/current-key 不符时拒绝准备或保持输入；ABI 不等时保留失败，不靠缩小寄存器样本宣称完整。自有成功不替代原生密码学、客户端或生产装载验收。

验收：两个独立进程 raw 确定性；非零/高64、两准确 caller、unknown caller/key/module、空/错/超长材料；完整覆盖范围和未覆盖状态单列；冻结源码/产物 hash，交第三复验步骤。任何实际生产部署保持前置未满足。

## 实现与审核边界

`sdk72_key_input.rs` 提供未注册的 `Sdk72KeyInput`。prepare 只在入口核完整 SDK 磁盘 SHA 与两份准确 public-only PEM 身份，持有各自加 NUL 的独立 Box；本地公钥数值匹配由主线 `public-fixture-proof.json` 承担，DLL 不解析或持有私钥。276B 是本次明确材料的身份，不是宣称所有 RSA1024 SPKI 编码都必须等长。

回调按返回地址→this vtable→当前 PEM 顺序分类，只读 this 接口头、入口 stack 和当前 public PEM，绝不解引用 R8 的输入 bytes。已知 mode0 才写 `[入口 RSP+28]` 为已预制本地 PEM 指针；其他寄存器和输入存储都不写。unknown caller/module/vtable/key 保持原输入，既不改 native 返回，也不改 length/output 或重调用原函数。

**磁盘 SHA 验证不等于当前 loaded module 身份。** prepare 的 module_base 仍由未来受审 loader 提供；源码注释明确要求另证 loaded SDK 绑定和入口原指令。本批 unknown-module 用自有函数地址与另一 module_base 关系不符作为拒绝用例，并没有模拟出一个已受信的真实 loader。不能仅调用 prepare 就声称任何目标可装载。

ilhook 2.3.0 的真实 `x64.rs` 明确保存 hook 前 CPU RSP、flags、GPR 与 XMM0–3；JmpBack callback 前只额外分配 16B。新 assembly 回调不使用传入的不足 32B home space，自行分配对 Rust 内部调用完整的 32B shadow，并保存 volatile XMM4–5；XMM6–15 按 Win64 ABI 由 Rust callee 保持。GetLastError/SetLastError 包住实际供给核心。这里没有修改 ilhook 或借已有 trust72 例程的部分寄存器比较替代本次实验。

## 实际自有 ABI 实验

`sdk72_key_input_abi.rs` 只核固定五输入磁盘 SHA，随后在独立自有 8MB allocation 内复制 `sdk72_key_input_fixture.S` 的合成 caller/callee。全部机器码是本项目自写的夹具，没有复制/执行 SDK 指令；3950C0 槽放 16 个 NOP 后接合成逻辑，caller 的真实 CALL 返回准确落在两个允许 RVA 或一个 unknown RVA。真实 ilhook 2.3.0 JmpBack 只安装在该自有 allocation，结束后核前 32B 恢复并释放。

每个案例先执行未 hook 基线，再以同一上下文执行真实 hook。独立 assembly 在 callee 第一段保存进入时的 16 GPR（包括原始 RSP）、XMM0–15 各 128 bit、完整捕获 RFLAGS、128B 自有参数栈和 LastError；另一个纯 assembly 观察层读取真实 Registers.rsp，与 CALL 前保存的 CPU 预期值比较。没有通过 Rust callback 先破坏 XMM4–5 再观察的缺口。高 64 位不可读 R8 sentinel 在全部案例中保持，不被任何观察/供给/合成 callee 解引用。

14 案例全部通过：

- 两准确 caller 的非零、高 64 位 length storage，及 118 长度透传。高位不截断；118 案只证明 pointer supply 不改 length，不宣称加密接受，真实 RSA 的 117/118 边界仍由冻结 Crypto 原生合同承担。
- unknown caller、module_base 关系不符、vtable 不符、null this；未知、空、null、截断、超长、已为 local 的当前 PEM 均不供给。
- 每次原 callee 严格执行一次，返回同一 caller 提供的 32B output 对象；输出仅有合成原 callee 按第五指针身份产生的既定效果，length storage 和 public 源 bytes 不变。
- 完整 GPR/XMM/flags 与未 hook 基线一致；128B 参数窗口仅第五槽允许改变；原入口 LastError 与合成 callee 独立写出的返回 LastError 都核同。没有把 `Registers` 的 XMM0–3 子集当作全部 XMM。

另 7 项 prepare 拒绝通过：空、错误、超长、截断、把 stock 当 local 的材料；错误 stock 和错误 SDK 磁盘身份。没有私钥读取或 native crypto 模型成功码。

最终 release 编译退出 0，实际工具链 `rustc 1.101.0-nightly (19833af72 2026-10-20)`、`cargo 1.101.0-nightly (3960fa69b 2026-10-12)`，`--locked --offline`，只用专属 CARGO_TARGET_DIR。只余既有 Cargo manifest 的未使用 cc 警告，未改依赖或 lock。便携 rustfmt 组件缺失的尝试已记录，未安装/下载组件；早期 build 的函数 cast/dead-code 警告已通过明确符号引用修正，最终构建没有这两类新警告。

两个全新 EXE 进程 `final-first.json` / `final-repeat.json` 均退出 0，各 **7800B**，逐字节相同，SHA-256 均 **`d8350d14ef9309295c4c803405de7008bced8aa5235c1c217af403e51759b815`**。每轮 14 个实际合成 hook 案例、7 个材料/SDK 拒绝；stderr log 均为空。全部 raw、build log、public proof 与最终来源清单只在 `local/migration72/d02-active/sdk-key-input-sol/`，raw 只含固定分类、长度、公开身份、布尔和范围，没有合成或真实明密文及其 hash。

## 已知未覆盖与下一项

主线已审三个真实源文件、assembly观察器和原始证明，先独立运行子代理EXE，再于新root-target离线release重建（退出0）并运行；两份主线raw都与双跑逐字节一致，均7800B/SHAd8350d14…59b815，14合成+7拒绝通过。独立重建EXE SHA`c534ce1ee9188d4f8cf551a77dc510e4f6cd3a153ed8523fcde6f1f41f8e337f`，与子代理EXE不同，不宣称二进制可重现；报告及其实际断言一致。证明`root-main-proof.json`，日志`root-build.log`。只接受未启用核心和自有正常Win64夹具；旧trust回调不能借本卡自动扩大ABI许可，真实native桥接/装载均未做。用户要求当前批次收口后暂停，下一项留交接，不开新任务。

本批不是完整生产 ABI/生命周期验收。明确未覆盖：YMM/ZMM 上半部、x87/MXCSR；参数窗口以外的 stack；DF=1 或异常/展开；生产并发、装载/卸载、完整线程状态；真实 loaded SDK 与入口指令绑定、自然 caller/route、合法 loader 与内存校验影响。普通 Win64 调用的 DF 保持 0，RFLAGS 使用两个算术位不同的输入种子，并逐位比较实际捕获值。

没有修改 production module 注册、DllMain、默认开关或任何其它 Rust 生产模块；没有构建/安装整 DLL 的许可。自然/loader/route 前置不足，因此未部署；Java、DB、配置、端口、服务、客户端和调试器均未操作。没有新的客户端验收，D02-R2 和 D-02 保持未完成。

主线审核、独立重建和第三运行已完成；patch独立本地提交为`1d9cae31b718a30b7e50fdfe8cc6ff9337af9bc8`，仅上述三新文件。子模块缺少身份配置时，提交命令临时复用父仓库已用身份，没有修改全局或仓库身份配置。后继若恢复，可另卡将同一供给核心的结果桥接进冻结 SDK 原生回放，验证本地 public 输入仍由原生 PEM/RSA/Base64 返回；该桥接也不能替代 natural/loader/route/response 合同或批准目标 hook。用户已要求本批完成后暂停，本轮没有开启此后继。

## 独立第三复验

不启游戏、服务、DB 或 debugger。须先由主线取得构建空档；第三输出必须用新文件名，保留本批双 raw。

```powershell
$sdkClient = & tools/game_path.ps1
$env:CARGO_HOME = (Resolve-Path local/cargo).Path
$env:RUSTUP_HOME = (Resolve-Path local/rustup).Path
$env:CARGO_TARGET_DIR = (Resolve-Path local/migration72/d02-active/sdk-key-input-sol).Path + '/target'
& local/cargo/bin/cargo.exe build --manifest-path patch/Cargo.toml `
  --example sdk72_key_input_abi --release --locked --offline
if ($LASTEXITCODE -ne 0) { throw 'Owned ABI build failed.' }
$sdkThird = 'local/migration72/d02-active/sdk-key-input-sol/third-main.json'
if (Test-Path -LiteralPath $sdkThird) { throw 'Choose a new third raw path.' }
& local/migration72/d02-active/sdk-key-input-sol/target/release/examples/sdk72_key_input_abi.exe `
  $sdkClient local/migration72/d02-active/sdk-key-input-sol/sdk-mode0.public.spki.pem `
  local/migration72/d02-active/sdk-key-input-sol/passport_1024.public.spki.pem `
  1> $sdkThird 2> local/migration72/d02-active/sdk-key-input-sol/third-main.log
if ($LASTEXITCODE -ne 0) { throw 'Owned ABI execution failed.' }
Get-FileHash -Algorithm SHA256 $sdkThird
```

EXE stdout 是完整脱敏报告，进程退出 0 才表示其内部断言通过；仅编译或磁盘 SHA 匹配不能代替上述实际执行。当前源码/EXE/hash 见同目录最终 `source-artifact-proof.json`，不将其机器路径加入共享文档。
