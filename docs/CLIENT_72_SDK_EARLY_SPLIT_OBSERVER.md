# R2 early split：两个 payload 预算终态的独立诊断产物

本项归属 `R2-CONFIGSYNC-EARLY-BRIDGE`、`R2-ROUTE-CONFIG-SELECT`。冻结规格来自[首诊审计](CLIENT_72_SDK_EARLY_CLASSIFICATION_AUDIT.md)，ABI、来源、装载和 owned 限制承接[早期桥](CLIENT_72_SDK_CONFIGSYNC_EARLY_BRIDGE.md)及[观察器](CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md)。独立诊断产物、真实 owned 和新现场预检现已通过；原 Marshal、SDK export/writer 返回、自然 Init/env/route 和 D-02 均未因此通过。

## 精确差异与冻结边界

新增 `sdk72_config_early_split_proxy` example、同名 observer/fixture，以及独立 verifier、Windows adapter 和定向测试。构建输入、来源证明及输出仅位于 `local/migration72/d02-parallel-20261109/b-sdk-split/`。旧源、旧 pin、旧 accepted proof/artifact 和旧 event5 报告全部保留。

| 原 payload 出口 | 新状态 | 仅能得出的事实 |
| --- | --- | --- |
| 扫描完 32768 个 UTF-16 单元仍未遇 NUL | `PAYLOAD_UNIT_LIMIT=11` / `payload-unit-scan-limit` | 本次扫描预算用完；不能判断下一槽、稍后或根本没有 NUL |
| NUL 已遇到，strict UTF-16 成功，自有 UTF-8 超过 65535B | `PAYLOAD_UTF8_LIMIT=12` / `payload-utf8-limit` | 自有 UTF-8 结果达到拒绝预算；尚未调用 JSON classifier |
| 其它路径 | 原 0/1/2/3/4/6/7/8/9/10 | 原语义和失败边界保持 |

新 decoder 的合法枚举准确为 `0/1/2/3/4/6/7/8/9/10/11/12`，拒绝旧 5。旧 verifier 继续将 5 投影为 `over-limit`，仅用于旧 epoch 纯回放；不为历史报告补猜具体出口。11/12 只能投影 fixed status、`anyEntrySeen`、`expectedCallerSeen`，env 和两个等值位必须为 0。

新 verifier 将 production loader 和 observer 与冻结旧源进行完整文本变换比较：loader 只允许独立 include 路径及 DATA 名；observer 只允许独立 callback/inner 名、两个常量和两个 return 点。其余生产代码必须逐字符相同。DATA 仍 72B、event offset56、字段顺序/原子顺序/release 发布、准确 caller 和 once 均保持。没有新增阶段、计数、自然 length/header/指针记录，未增读预算、放宽 UTF-16、改参数或校验。

预算仍为 name 最多 13 次 2B、payload 最多 32768 次 2B（包括首次 NUL）；一次准确 caller 内最多 32781 次 self-RPM/65562B，另有原 CPU 栈 return 的直接 8B 读取。collector 仍仅读固定 code 和自有 DATA，准确上限 9 次/1450B/单次512B。原 20B patch、完整 284B 搬移/返回 entry+20、38B wrapper 和 47 个转发均保持。

独立 `.def` 为原 47 项，另加 `Sdk72ConfigEarlySplitProxyState @48 DATA`、active `sdk72_config_early_split_inner @49`，以及冻结源码依赖的 dormant `sdk72_config_inner @50`、`sdk72_key_input_inner @51`。callback 独立，仍只有原准确 EXE 入口一个 hook。

## 来源和新的构建 epoch

先核首诊报告 `b-sdk-analysis/final-first.private.json` 为 10469B/SHA `db2bd764c3cdacab42c687412e5a9089e7156dec6c975e0c8e2aadbf51fef525`，以及该报告绑定的旧 source/proof/artifact。原六个完整原窗全部复用，其中两个 helper 窗的 bin、binding、instructions 均从旧忽略目录复核；没有新增原 PE 窗或 CPU 回放。

新 verifier 保留所有适用的旧 dependency pin。旧 Cargo manifest 的固定 SHA `f3a71cfb6415581cddcaf2dbcb654ca012b6ee4ae372b11e2719f89b64cb1a2e` 对应新忽略目录中不可变的 `legacy-cargo-manifest.private.toml`；旧 build/source proof 的原绑定保持。当前 manifest/lock 另以新 proof 的 `buildInputs` 记录，不能把新增 example 后的 manifest 重算为旧 epoch。新 proof 同时记录五个新源的 `sources`、独立 def SHA、准确 split 合同及旧首诊报告 SHA。

`source-first/` 只证明实现准备时的源与静态输入，当时 manifest 尚未追加 example；它不是主线后续构建的 accepted epoch。主线修改 manifest 后须在新目录重生 source proof，再构建并将该新 manifest/DLL/EXE/owned/source/def 完整绑定。

## 23 个 Rust fixture case 的规格

原 18 例保持同一 GPR16/XMM16/RFLAGS/栈128B、入口及返回 LastError、原函数恰一次、high64 返回、源对象不变、drop 恢复、安装失败无写及完整 284B ilhook emitter harness。旧 `over-limit` 改断言11，旧 `utf8-over-limit` 改断言12；新增五例：

| case | 输入与准确预期 |
| --- | --- |
| `unit-next-slot-real-nul` | 有效 JSON 后补空白，真实 NUL 正好位于预算后一槽；11，不读取该槽 |
| `unit-identical-prefix-no-nul` | 与前例预算内完全相同，无该 NUL；11，读取轨迹相同 |
| `utf8-last-allowed-byte` | ignored 字段以 U+0800 和 ASCII 补齐有效 JSON，自有 UTF-8 恰65535B且 units 在预算内；2 |
| `utf8-first-rejected-byte` | 同类有效 JSON，自有 UTF-8 恰65536B且 units 在预算内；12 |
| `invalid-surrogate-unit-limit` | 无 NUL 的 surrogate 前缀耗尽单位预算；11，未提前解码为10 |

原 `nul-boundary` 继续覆盖 NUL 在最后允许槽且结果2。23 例还各自调用真实 observer 的 owned 读取接口，核高64标签下的精确逐单元地址轨迹及终态第二次零追加读取；近似 name 零 payload、未知 caller 零参数读取保持。Python 的边界投影仅使用冻结标准库 owned model，验证区别与预算，不代表 Rust/原 Marshal 执行。

## 当前实际验证与未验项

实际最终 30 项 Python 定向检查通过，覆盖完整284B和返回边篡改、准确导出声明、9读预算、失败输出保密、11/12与分类位零约束、拒绝5及旧5纯回放、source 精确变换/23case顺序、放大预算/旧pin漂移拒绝、六个精确 owned 边界、adapter 前后模块/创建身份/最终存活/CloseHandle、90秒monitor和同次raw账本、固定失败与输出隔离。`python-final2.private.log` 保存本次输出。`source-final1`、`source-final2` 两fresh报告均16450B/SHA `b86b1d68abc81cd703e679c96773bd124e6714b4bd8f964cd928d6cc581823d0`，逐字节一致；55冻结pin、22静态断言、固定五stock与References/binding来源相等，新增原窗0。这两份仍是旧manifest下的构建前准备；artifact与Rust owned均未执行。

准备阶段新 adapter 的 `ACCEPTED_PROOF_SHA`、`PRODUCTION_SHA`、`FIXTURE_SHA`、`MANIFEST_SHA` 均为 `None`，真实门禁明确拒绝“epoch is not bound”。后续主线已完成新构建/owned/审核并回填这四个准确 SHA；实际真实隔离文件门禁结果见下节，不借旧 artifact 冒称通过。

准备脚本曾因 filename 替换只匹配 sdk72 前缀而误写旧 adapter/test 三行 import；已逐行准确还原，完整 SHA 恢复旧 `d2f9a6f0…ab5702b` / `94d06595…fcd9a0`，失败过程保存在新忽略目录 `copy-path-failure.private.json`。没有重算旧 pin 或修改旧报告。

实现代理未执行 Cargo/Gradle/protoc/JVM、DB、进程/端口查询控制、客户端/调试器/REA、安装恢复、Git 写入或部署。新产物主线实际构建与 owned 已在下节单独记录；自然实验准备和 GS 其它必要合同仍未闭合。

## 主线实际构建与回填后的真实文件门禁

主线实际 release/locked/offline DLL 构建9.28秒、fixture构建5.35秒，均退出0。新manifest为 SHA `3ea4ff0853353f3070b818935c20e6e56b21219f64bea983e4aec8906ed8dd65`，原lock保持。实际DLL577536B/SHA `946082a0c8bb29277ae29627d7791be9852b6d7f29fb120c36cb54f3e56a7f1a`、EXE761856B/SHA `7f07ad5e122bb994d4bc41914ff9724642048eeb5e0cf1db7d9e86349ea6c176`；两fresh各23case真实完整ABI、每case真实observer读取接口的精确轨迹/终态零读、完整284B原prefix搬移与drop均通过。它们是新owned合成代码/对象执行，不是原EXE Marshal或自然SDK执行。

accepted proof 为 `owned-main1/proof.private.json`，SHA `6ddcbb41a82fd63174b9db70a96bb0fe83dfcb8b700e7afcdd9f9789be1a79ae`；主线15项独立审核 `main-owned-review.private.json` SHA `c53422f80ae8ad0f8033b2a5039fa1372e41e9788b05bd4526d2ba76cfe96499`，核55旧pin、新源/manifest/lock、51 EAT/72B可写非code DATA、两fresh实际ABI/high64和完整284B逐byte等。首次主线source join把构建前故意为null的artifact/owned也拿来比较成品而失败，原失败单独保留；修正为完整比较source/build/material、artifact及owned分别验，没有改写原owned proof。

实现代理随后仅回填新adapter四个常量，未改绑定accepted proof的Rust、verifier、其test或Cargo。真实 `accepted_proof()` 实际通过，确认55pin、同source/build epoch、2fresh各23case、实际fixture及def/split合同。再在 `adapter-final/installed-layout-owned/` 建立六个stock/carrier hardlink仅供只读使用，并将新proxy普通复制到布局的Astrolabe位置；没有修改hardlink属性或输入内容。

未mock的 `preflight()` 实际验证五逻辑stock/原Astrolabe alias、新proxy/actualfixture/accepted proof、binding、完整code contract、47stock exports与References相等。原五stock及SDK carrier的前后大小/SHA全部相同，新proxy与artifact不是同一文件。完整结果/布局身份/前后原件身份/调用轨迹为 `adapter-final/source-gate-owned-layout.private.json`，SHA `08bd9030a49127b4ce60e96ff6e95749ec8e19b491e3564377c368076d138925`；跟踪只出现adapter的accepted/preflight及文件hash函数，没有构造Windows backend、调用monitor、进程/端口/target读取或启动布局。回填影响的30项定向检查再次通过，日志 `adapter-final/tests.private.log`。

以上只证明新产物在隔离自有文件布局的真实门禁，**不表示GAME_PATH已安装或自然事件通过**。客户端、原Marshal、SDK export/writer返回、自然env/route及D-02仍待验收，未部署GS。

## 主线构建与后续验收入口

主线统一在 `patch/Cargo.toml` 追加以下声明，保持 lock 与旧 example 不变；实现代理不修改 manifest：

```toml
[[example]]
name = "sdk72_config_early_split_proxy"
crate-type = ["cdylib"]
```

以下命令由主线串行执行，`GAME_PATH` 来自本批已核配置/盘点。source/build 输出均须选择新的目录；不要覆盖 `source-first` 或旧 epoch。fixture CLI 只运行新自有 EXE 两次，不装载真实 SDK/EXE。

```powershell
python -B -m tools.verify_sdk72_config_early_split_proxy --client $env:GAME_PATH --prepare-build-inputs --output-directory local/migration72/d02-parallel-20261109/b-sdk-split/source-main1
$env:CARGO_HOME = (Resolve-Path local/cargo).Path
$env:RUSTUP_HOME = (Resolve-Path local/rustup).Path
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'local/migration72/d02-parallel-20261109/b-sdk-split/target'
$splitDef = (Resolve-Path local/migration72/d02-parallel-20261109/b-sdk-split/source-main1/config-early-split-proxy.def).Path
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_early_split_proxy --crate-type cdylib --release --locked --offline -- -C "link-arg=/DEF:$splitDef"
if ($LASTEXITCODE -ne 0) { throw 'Split DLL build failed; preserve the exact failure.' }
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_early_split_proxy --crate-type bin --release --locked --offline
if ($LASTEXITCODE -ne 0) { throw 'Split owned EXE build failed.' }
python -B -m tools.verify_sdk72_config_early_split_proxy --client $env:GAME_PATH --production local/migration72/d02-parallel-20261109/b-sdk-split/target/release/examples/sdk72_config_early_split_proxy.dll --fixture local/migration72/d02-parallel-20261109/b-sdk-split/target/release/examples/sdk72_config_early_split_proxy.exe --run-owned --output-directory local/migration72/d02-parallel-20261109/b-sdk-split/owned-main1
python -B -m unittest tools.test_verify_sdk72_config_early_split_proxy tools.test_observe_client72_config_early_split_proxy -v
```

上述实际差异审核、构建、两fresh23例、51 EAT/72B DATA/完整搬移恢复、主线独立审核、accepted回填及真实隔离布局门禁现已通过。下一项是主线准备并冻结新的安装/正常退出/stock恢复脚本、adapter及依赖源pin、五stock/13保护/进程端口盘点与新实时ready；不得复用旧ready、输出名或旧现场计划。

现场准备接口仍为 `preflight(client, artifact, proof_path=ACCEPTED_PROOF)`，仅做已安装布局门禁；CLI额外承担原90秒monitor及同次9读账本。主线封存新准备并取得本轮ready后才可使用以下接口，其中输出名须换为本轮未存在名称：

```powershell
python -B -m tools.observe_client72_config_early_split_proxy --client $env:GAME_PATH --production local/migration72/d02-parallel-20261109/b-sdk-split/target/release/examples/sdk72_config_early_split_proxy.dll --artifact-audit local/migration72/d02-parallel-20261109/b-sdk-split/owned-main1/proof.private.json --output local/migration72/d02-parallel-20261109/b-sdk-split/NEW-LIVE-NAME.private.json --user-ready
```

此次没有调用该现场CLI。自然实验仍只回答准确caller的11/12或其它明确终态，原90秒、一次尝试和读取预算保持；不清once、不重arm、不新增自然内容/length/header/指针记录，不记录payload/key/URL正文、hash或长度。父项和D-02继续未完成。

## 新现场准备与主线预检

本轮独立 `run/launch/restore-split-live1.ps1`、新 inventory/plan 已封存。计划 SHA-256 `72a368e21b7e0b79757b35d412aa15eb3549b9987b1388d3460278037022d3fb`，91 项源 pin 包含实际 Python 导入闭包，三脚本 Parser 零错误；launcher 在外层 proof 绑定，避免计划哈希循环。新 ready 必须关联 runId/计划 SHA，确认时间不早于计划且使用时不超过300秒。只有主线取得新的实时回复后才生成凭据；本次尚未生成。

主线实际运行 `run-split-live1.ps1 -CheckOnly`：五 stock、SDK carrier、13 个现存保护文件及 `.env.local` 缺省均保持；当前 AGENTS 外部修改以新盘点显式记录，其余保护与原盘点一致。相关进程、固定 TCP/UDP 监听均为0，无 alias，未安装/未启动。实际摘要为 `live1-prepared.private.json`。主线另直接执行 accepted_proof 并核实际布局证明、全部91源、准备身份及新输出不存在，17项独立审核通过，入口为 `main-binding-live-review.private.py/json`；30项定向 Python 检查再次通过。

预检后 AGENTS 再有外部文字更新，明确 GitHub 检索关注分支。原 live1 盘点、计划和实际预检均保留；其保护基线不再用于安装。新的 live2 准备继承同一已验产物和源 epoch，显式记录新 AGENTS，再由主线实际预检；不重写旧 inventory 或豁免漂移。

现场只采 SDK，一次90秒、外部最多9读1450B/单次512B；内部回调限额保持。用户需允许本次 UAC、等待正常登录页并保持不登录；主线完成采集后提示正常退出，再按同一 journal/backup/新DLL/stock精确恢复并复核。String 使用原版身份，分次串行，不使用此实验的 alias 替代 stock。新自然终态尚未知，不提前标记分类、Init 或路由通过。GS必要前置未齐，未部署 GS。
