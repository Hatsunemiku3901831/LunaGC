# D02-R2-KEY：同一供给核心接准确 SDK Crypto 原生片段

## 有界计划（2026-11-08）

目标是补齐 [未启用 key 核心](CLIENT_72_SDK_KEY_INPUT_ABI.md) 到 [固定 SDK Crypto](CLIENT_72_SDK_CRYPTO_CONTRACT.md) 的离线执行桥梁。固定 SDK SHA-256 为 `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`，五输入以本轮私有 inventory/profile 逐项复核。沿用冻结 Crypto 机器码、存储/CRT/单线程 Windows 状态与合成熵模型；不重新开 REA、整 EXE 分析或 live。

问题与证据出口：

1. 准确 caller 的原始 mode0 getter、C-string/SSO 选择及间接 CALL 能否把第五个参数交给同一 Rust 核心？从 `34EE6B` / `34EF3C` 开始执行原始 SDK，分别停在原始返回点 `34EEB7` / `34EF88`，在 `3950C0` 入口取 snapshot；不宣称已执行完整登录 jobber 或其输入预处理。
2. Rust host 指针如何进入 emulator？新独立 example 用内存管道接收入口 snapshot，把 stack、this 接口头、公开 key snapshot 分别复制到 host owned storage，调用原 `Sdk72KeyInput::prepare/supply_at_entry`。host 返回的第五槽只用于来源判断；Python 将核心持有的公开 bytes 复制进独立 emulator allocation，再只写 emulator `[入口 RSP+28]`。不能把 host 指针直接写入 emulator，也不能用 Python 重写分类。
3. 原生 PEM→RSA→Base64 和拒绝是否保持？正例在内存核 padding、独立整数 RSA、Base64 decode 及已选 server private key 解密；边界 117/118、非零/高64长度、UTF8/嵌入NUL、未知 caller/module/vtable/key、null/坏/截断/超长 key 分列。无效 this 只验核心拒绝，不伪造原生函数可接受 null this。
4. 除第五槽外是否透明？逐次对比调用核心前后的全部 GPR、XMM0–15、RFLAGS、128B 已拥有参数栈、this、长度和原 public source；原生返回必须是 caller 提供的 output 对象，完整长度存储保持，输出所有权由原生析构验证。与无桥接 baseline 对照，成功和失败都取原生返回。

允许文件仅新 `tools/client72_sdk_key_native_bridge.py`、对应 test、本专题和 `patch/examples/sdk72_key_native_bridge.rs`；不改供给核心、冻结父工具、模块注册、依赖或生产代码。所有 raw/build/log 仅在 `local/migration72/d02-resume-20261108/r2-key-native/`。管道中 PEM 不写盘；认证输入/明密文及其 hash 不打印、不序列化、不写盘。报告仅保留固定来源身份、长度、模型、布尔和未知。

主线独占 Cargo/Git/Gradle/DB/固定端口/客户端/调试器/部署。源码准备完成后由主线串行离线 release 构建该 example，预计只产生专属 target 下的 EXE/中间文件；本卡执行离线 Python 与该自有 helper。两个新进程确定性、独立结果检查和第三复现命令为验收出口；首次失败保留明确阶段，输入 hash 改变或有未闭原生边则停止于准确缺口，不扩大 DLL 或旧 HW 路线。

本卡不批准目标 hook/装载。实际 loaded SDK 身份和入口原指令绑定、自然 caller/route、合法 loader、内存校验影响、生产并发/卸载/异常展开及 D-02 客户端验收继续独立。

## 当前状态

**限定离线桥梁已通过。** 最终 v2 两个全新进程各完成18个 baseline/bridge 原生案例和19次同一 Rust 核心调用；两份报告逐字节一致，14项独立检查全部通过。未启用核心、准确 SDK caller 与原 PEM/RSA/Base64 已闭合；真实 loaded/natural/route/loader、生产接入和 D-02 仍未完成。v1 的析构栈夹具缺陷和首失败全部保留，v1 不作为通过证据。

## 实现和证据层次

`sdk72_key_native_bridge.rs` 通过源码路径直接包含未变更的 `sdk72_key_input.rs`，原核心 SHA-256 固定为 `67af556fea165d1597741cee2bbf2d310542c6c5c9637e44ee8877f4cc885bcd`。初始化时原 `prepare` 读取固定 SDK 磁盘 bytes，校验准确 stock/local public 身份；原生 getter 产生的 stock 经管道输入，local 使用已有受控公开材料。当前 helper 没有私钥读取接口，也没有 auth input bytes 接口。它不执行 ilhook wrapper；已有真实自有 ilhook ABI 证据继续由前卡承担。

每次 `3950C0` 入口，Python 取全部 16 GPR、XMM0–15、RFLAGS、128B stack 和 LastError，仅将核心会解引用的 stack、this 头、269B public key snapshot 复制到 host。caller 和 this vtable 仍是准确 emulator module-relative 数值；RCX/RSP 及第五参数换成 host owned storage 的地址，RDX/R8/R9只作为不解引用的数值传入。原核心 `supply_at_entry` 实际分类并写 host 第五槽；helper 只依据该槽是否等于核心持有的 `public_pointer` 判断输出来源，导出的是从该指针实际读取的 277B（含 NUL）公开材料。

Python 不实现 caller/key/vtable 决策，只核 helper 来源和公开材料，再复制到 emulator 独立 buffer，并将 emulator `[RSP+28]` 改为这个 emulator 地址。host 地址没有跨入 emulator；pipe transcript 不落盘。host Registers 的全部字段、XMM0–3 及 storage 对比由 helper 执行；emulator 完整 GPR/XMM/flags/LastError 和 stack 除第五槽逐字节比较，由 Python 在继续执行第一条 Crypto 指令前完成。入口前后的 length/input/this/public source 也比较；这些内存只用于断言，不序列化。

两准确 caller 从真实 key getter 和参数选择指令开始，执行原间接 CALL 到原 encrypt，停在真实返回地址，未执行 jobber 的外层 C-string 长度计算、空输入业务拒绝或 HTTP。坏 key 案例只在 caller 写第五槽之前改变夹具 RDX 参数；错误 vtable 只给合成接口头使用同函数槽的另一个表。这些是明确的测试输入，不作为自然 SDK 状态。unknown-caller 直接调用原 `3950C0`，使用模块外 STOP 返回点。null this 仅送原核心分类，不调用会解引用它的原 Crypto。

首次 `probe-first` 停在预期结果断言：原工具误把准确 stock PEM 之后附加一字节视为原生 parser 可接受的输入。独立 `probe-key-suffix` 仅执行该案例，实际 output 长度为0，原生 `4BA050`、`4BA010` 都返回0，原 Crypto 原样返回 output 对象且只执行一次。该输入的核心拒绝供给与原生解析拒绝是两个事实，现都按真实结果验收；没有更改公钥解析、RSA、返回码或原核心。首失败 stderr 和独立定位 raw 均保留，未覆盖；最终案例错误消息另补固定案例名以便归因，不输出参数内容。

v1 完整矩阵产生 18 个原生案例和19次核心调用，供给/算法/入口状态/原返回断言都完成；14项独立检查有1项失败，指向 `outputOwnedCopyReleased`。原因是父 `CryptoFixture.call` 在调用前清默认 stack 的既定窗口，而本卡准确 caller 把 account encrypt output/password getter output 放在该窗口；沿用该调用器做析构会先清掉待析构对象。独立 `probe-owned-cleanup` 确认原 output/getter owner 都仍在账本；只换到不重叠的析构调用栈，同一原生 `1380E0` 就释放两者。修复限定在本卡新工具：析构采用单独 stack，并将原 output/getter 释放结果加入最终报告前的硬断言，另记录 cleanup 原生步数及包含析构的 model events；冻结父工具不改，不把错误夹具当原生内存泄漏。v1 两份 raw 实际逐字节一致，但因该语义失败不验收；其 source/test 已原字节归档，source hash 与 v1 报告相符，失败测试日志和单例定位证据均保留。

每例先以无桥接的独立 emulator 执行 baseline，再以相同初始状态执行桥接。已验范围将区分入口透明性与原生返回 ABI：入口全部 GPR/XMM/flags/owned stack 保持，原生返回只要求正常 Win64 非易失 GPR、XMM6–15、RSP 和原 output pointer；易失寄存器和返回 flags 不要求与入口相等。原生长度存储保留完整 QWORD，底层 RSA 仍按原代码读取低 DWORD。

## 显式 emulator / CRT 模型

本卡没有新写密码学成功模型。冻结 `CryptoFixture` 与其 `Fixture` 的 source/依赖清单逐项核 hash；每个案例单列实际命中的 model boundary 和调用数，所有目标指令的 trace 仅用于内存计数/边界检查，不输出认证内容。

`nativeSteps` / `cleanupNativeSteps` 是相应阶段目标地址的 code-hook 事件数，包含模型入口的一次事件；这些入口由明确模型返回，计数不能解释为每条指令都没有模型。原生 PEM/padding/RSA/Base64 入口均不在模型集合，成功和拒绝都执行原始目标代码。

| 边界 | 实际处理与限制 |
| --- | --- |
| PE / fixture 初始状态 | 原始 SDK sections 映射在 Unicorn；没有 OS loader、DLL 初始化、真实线程或客户端。继承 fixture logging DWORD `RVA764CA0=3` 和合成 GS/TLS 初态，均非 live 写入；这些初态不证明自然初始化 |
| C++ / CRT 存储 | 原 `4823A8/13A1B0/4978C0` 分配、`4823A0/48F4F0` 释放由父存储账本处理；Windows HeapAlloc/HeapFree/HeapReAlloc/HeapSize 使用同一账本。string/vector/PEM/RSA 的逻辑与复制仍是原始代码，不模型容器结果 |
| 单线程状态 | 原 mutex/condition 及 static-init epoch 边界，Windows critical section、TLS、Get/SetLastError、合成 PID/TID/时间；不执行竞争或真实 Windows 同步 |
| 模块与退出 | GetModuleHandleExW 只返回本次已映射的 SDK 基址；不是 loaded module 绑定。`48274C` 只记录 CRT 退出注册，不执行 OpenSSL 进程退出清理；全局/TLS allocations 保留至 fixture 销毁 |
| 熵 | BCryptGenRandom 只给可复现合成 bytes；随后原 DRBG、非零 padding 循环、大整数 RSA 与 Base64 都实际执行。不能据此宣称自然随机源通过 |
| 错误门 | 原 PEM 格式、RSA 尺寸/padding 和空输出路径均执行。父 fixture 的 fatal/native-error entry 仍抛异常，未知 import 仍终止，不以模型成功码兜底 |
| 桥接 | host 指针翻译和 emulator 第五槽写入是本卡明确测试接缝；其余入口状态逐字节核对。没有在 emulator 中执行 Rust hook wrapper，也不扩大前卡物理 ABI 证明 |

## 实际验收与来源

主线 `--release --locked --offline` 构建 example 退出0，`build-main2.log` 记录7.91秒；只有既有 manifest 的未使用 `cc` 警告。未改 Cargo manifest/lock、依赖、原供给核心或注册入口，没有构建/安装整补丁。本卡没有新 REA 会话，复用固定 SDK 和冻结父证据；没有 live、hook、客户端、调试器、DB、端口、服务或部署操作。

最终 `final-v2-first.json` / `final-v2-repeat.json` 各 **97,333B**，逐字节相同，SHA-256 均 **`8d53ace94b61dab07df16f560a151556d9300168e09b973404997ede5439dcdf`**；stderr 均为空。每轮18例分别先执行无桥接 baseline，再执行真实核心供给的同源 native caller/Crypto；额外1例只执行原核心的 null-this 拒绝。bridge 主阶段共33,501,553个目标 code-hook 事件，析构阶段另594个，解释范围见上节；没有序列化完整 trace 或认证内容。

- 两准确 caller 的9个 stock 正向供给中，8个原生加密成功并通过独立 padding、整数 RSA、Base64 和本服所选私钥解密；剩余118B样本在原 padding/RSA 返回0/-1，manager仍返回原 output pointer且内容为空。1/37/117B、空、内嵌NUL、7B UTF8及两组完整高64位长度均按原接口验证。
- unknown caller、module-base 关系不符和 vtable 不符的3例均不供给，完整 native 输出与 baseline 相同，仍是 stock key，不能被所选 server private key 解回同一 block。已经是 local key 的1例保持原指针，原生解密 oracle仍成立。
- malformed、empty、null、truncated、stock 后附字节的5例都不供给，两个原 PEM parser 均返回0，输出为空；没有改原 parser 或成功码。
- 全部入口的 GPR16、XMM0–15、flags、LastError及128B已拥有参数栈（只允许第五槽）均保持；原生调用严格一次、RAX为原 output对象、length完整QWORD不变，正常Win64非易失 GPR/XMM6–15保持，原 output/getter copy均由原生析构释放。OpenSSL进程全局/TLS账本仍保留，未声称全部 heap 清零。

`tests-final-v2.log` 的14项独立检查：失败/错误/跳过均0。元数据审计逐例比较 v1/v2，除明确的 output/getter 所有权、cleanup步数、进程保留分配数和包含析构的模型统计外，所有原生入口、返回、长度、分类和密码学 oracle字段完全一致；修复没有改变主 Crypto 语义。来源、双raw、build/test日志、两次失败定位和源码归档的长度/哈希统一见本卡忽略目录 `source-artifact-proof.json`；`tests-final-v2-result.json` 将实际检查结果绑定到最终报告和测试源。

| 最终来源 | SHA-256 |
| --- | --- |
| `tools/client72_sdk_key_native_bridge.py` | `05592fc4c49e10d28e0a9094c789ca7a5f57ab16e6d40830d97c58a8c073e944` |
| `tools/test_client72_sdk_key_native_bridge.py` | `febe07d760bdcb957c34ac314c5574ce86fc3914aa82f7453ccd790802f259b4` |
| `patch/examples/sdk72_key_native_bridge.rs` | `37092b498a0303d70f600bc6d43c973cde7c614a959e7d9f272551ecaabad89c` |
| 主线构建 helper EXE | `40b0668eba90e8f469a62f4d0ea1d73b4c900b27cabf5295ba51fb8f4e8c1120` |

报告只含固定来源身份、长度、模型和布尔；host pointer、pipe PEM、合成或真实明密文及其 hash 均未写盘。local private key仅在 Python 内存作独立数学 oracle，helper无私钥/认证输入接口。只有上述四个新文件属于本卡可提交范围（Rust example在patch子模块）；主线统一审查/本地提交、更新 Project/Harness，子代理未执行 Git。

主线独立第三完整回放 `third-main.json` 退出0，同两份v2逐字节一致；第三14项检查通过。三份报告身份/equality见上级 `native-third-review.private.json`，测试日志为 `tests-main-third.log`。主线复核了实际核心包含、host/emulator指针翻译、第五槽唯一写入、原生拒绝与析构栈修复，接受该限定桥梁。未增加目标hook、安装或部署；合法透明加载入口另按[独立计划](CLIENT_72_CLEAN_LOADER.md)推进。

## 独立复现

主线已提供专属 target 的 helper；若需独立重建，先取得主线 Cargo 空档，不并发构建：

```powershell
$env:CARGO_HOME = (Resolve-Path local/cargo).Path
$env:RUSTUP_HOME = (Resolve-Path local/rustup).Path
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'local/migration72/d02-resume-20261108/r2-key-native/target'
& local/cargo/bin/cargo.exe build --manifest-path patch/Cargo.toml `
  --example sdk72_key_native_bridge --release --locked --offline
if ($LASTEXITCODE -ne 0) { throw 'Offline native bridge helper build failed.' }
```

不要直接运行并重定向 helper stdout；该通道携带仅供父 Python 内存使用的公开 PEM bytes。通过 Python 执行时，其 stdout 只含报告长度/摘要，没有该通道内容：

```powershell
$sdkBridgeClient = & tools/game_path.ps1
$sdkBridgeReport = 'local/migration72/d02-resume-20261108/r2-key-native/third-main.json'
python tools/client72_sdk_key_native_bridge.py --client $sdkBridgeClient `
  --helper local/migration72/d02-resume-20261108/r2-key-native/target/release/examples/sdk72_key_native_bridge.exe `
  --output $sdkBridgeReport
if ($LASTEXITCODE -ne 0) { throw 'Native bridge replay failed.' }
$env:SDK72_KEY_NATIVE_REPORT = $sdkBridgeReport
python -m unittest tools.test_client72_sdk_key_native_bridge -v
Remove-Item Env:SDK72_KEY_NATIVE_REPORT
```

输出路径必须是本卡忽略目录内的新文件；保留已有 probe、双 raw 和第三复验。测试只读元数据报告与来源，没有 server/DB/network/live 操作。只有真实回放成功且检查通过才接受限定桥接；无运行 JAR/DLL 或健康/TCP/UDP 验收对象，不把 helper 编译当部署。生产模块、配置、资源和默认开关未修改，前置 loaded/route/合法 loader/校验合同仍不足，未部署。

## 后继最小出口

本卡通过后，主线先独立审来源、第三回放和分项本地提交，并更新 Project/Harness；它仅关闭“同一核心→准确原生 Crypto 离线桥梁”，D02-R2、R2-KEY 的生产接入和 D-02 仍未完成。

实际装载前仍需准确的 loaded SDK 绑定：当前 module base/范围与准确磁盘 SDK、`3950C0` 原入口及两 caller 原指令的对应关系。既有初始化根/setopt 的 `file-entry-matches` 只证明其采样范围，不能借给 Crypto 入口。供给材料已预制和持有的事实不产生 loader 权限。

下一自然输入仍是准确 AccountPlat 加密调用（caller、known-stock-key 布尔、长度、原 output 状态）与固定 route 分类；不需要账号、密码、cipher 或其 hash。route 可承接 Crypto 专题 `3773C0` 返回点 `34ED67` / 已验请求入口合同，但配置表存在不等于当次自然选择。loader producer/实际配置、合法窄装载方式及内存校验影响由主线另卡取得来源；当前没有批准整 DLL 装载、注入或新 HW 路线。生产并发/卸载/异常展开、YMM/ZMM 上半部、x87/MXCSR、真实 OS RNG/线程和完整 jobber/进程退出继续未验。
