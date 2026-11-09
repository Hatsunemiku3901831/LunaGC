# R2：已有 unit-limit 副本中的必要字段前缀观察

本项归属 `R2-CONFIGSYNC-EARLY-BRIDGE`、`R2-ROUTE-CONFIG-SELECT`，严格消费[自然11后继规格](CLIENT_72_SDK_SCAN_LIMIT_FOLLOWUP.md)。自然事实来自 split `live3` 的 event11，不能以旧 event5 代替。B 的三份27372B报告 SHA `70a030a55150b99ba723e7e201d5dbdd4d0a0f330bf83fc62e3ed9ca9f25c0a1` 相同，12测试及13项主线审核已通过；审核 SHA `183b030ef3b73ef9663c852df83163836f00c3a659fd5d1941899302ba80770b`。本次是该合同的独立最小实现准备，完整 SDK serializer、自然 language 来源及自然超限原因仍未知。

## 唯一新增观察路径

新增 example `sdk72_config_prefix_proxy`，独立 observer、`prefix.rs`、fixture、verifier/adapter 及其测试。原 early/split 源、五源/55pin、manifest、accepted proof、artifact、自然账本和全部旧报告不变。私有输入/输出只在 `local/migration72/d02-parallel-20261109/b-sdk-prefix/`。

原复制器现在返回 `Complete(String)` 或 `UnitLimit(Vec<u16>)`。只有原扫描完32768个单位的出口，将**这一个既有 owned Vec** 移交给新路径并借用其 slice；没有再次读取目标或 clone 整个自然 buffer。完整String仍交原冻结分类器；读取失败、非法已终止UTF16、UTF8超限12及其它旧分支保持。

新解析器按真实JSON quoted key、escape、scalar value及分隔符逐项推进顶层对象。每个完整scalar独立交serde，拒绝已观察到的重复key、非法token、嵌套非标量outer值、未知env或未完成必要值。env/app_key/url可以按不同顺序出现，不能假设JSONObject枚举顺序。只有三项都已完整出现，随后遇真实顶层language键和opening quote，而且其quoted value词法状态仍跨副本末端，才形成新事实。

仅已消费至language opening quote前的前缀做strict UTF16→UTF8，仍限65535B。language尾部只扫描已有u16的引号/escape状态，不建立大UTF8正文；边界处的未完成escape、unicode escape或surrogate不修补、不声称有效。已完成必要scalar组成明确命名的自有 `PrefixProjection`，交冻结纯 `References/classify`；它不是原payload，也不传给真实SDK。env继续采用冻结 `as_f64` 的整数值语义，`1.0`保持可接受。

| 新输出 | 准确范围 |
| --- | --- |
| event13 `managed-prefix-fields-before-language-scan-limit` | 仅必要前缀字段及language词法预算位置；输出允许CN env及两个reference等值bool |
| 不满足任一前缀条件 | 保持11，env与两个等值位为0 |
| 其它旧分支 | 原0/1/2/3/4/6/7/8/9/10/11/12约束不变；5只属历史early epoch |

新decoder对13固定追加 `completePayloadValidated=false`、`payloadTerminatorObserved=false`、`originalMarshalObserved=false`、`naturalInitOrRoutePassed=false`。完整payload是否有效、未观察后缀是否重复/非法、NUL、原Marshal及自然Init/route均未验。旧split decoder仍拒绝13，旧5/11/12报告不改写。

## 保持的 ABI、预算和来源

DATA仍72B、event offset56、原字段顺序和release发布、准确caller及once保持。原47转发、唯一EXE入口hook、20B patch、完整284B搬移/返回entry+20及38B wrapper不变。新独立导出为 `Sdk72ConfigPrefixProxyState @48 DATA`、active `sdk72_config_prefix_inner @49`，冻结源码依赖的两个dormant为 `sdk72_config_inner @50`、`sdk72_key_input_inner @51`。

同步预算仍name最多13次2B、payload最多32768次2B（含首次NUL），总cap32781读/65562B，另原CPU栈return8B。外collector仍固定code/自有DATA的9读/1450B/单次512B，90秒monitor、前后身份/最终存活/finally CloseHandle和fixed failure保密约束保持。没有新hook、managed header/length、参数修改、清once或rearm；不输出自然正文、key/URL、指针、hash、长度或实际复制次数。

新source gate核86个冻结来源，其中包括B的76pin、旧split五源/55pin、实际旧产物/报告及新规格/主线审核。旧accepted Cargo `3ea4ff0853353f3070b818935c20e6e56b21219f64bea983e4aec8906ed8dd65` 绑定B已封存的 `b-sdk-limit11/accepted-cargo-manifest.private.toml`，当前manifest/lock另记为新 `buildInputs`；不修改旧工具当前manifest守卫，不把新构建放行成旧epoch。

本轮source-only门禁不读取GAME_PATH或目标PE。binding从已绑定旧split源码复制并核其SHA；References通过原缓存的完整class49/container/text extent与旧私有References逐byte比对，不记录其长度/hash。复用旧accepted的22项静态合同，并明确标记 `reusedAcceptedStaticContract=true`；本轮没有重新执行原PE静态读取，也没有新增原PE窗口。

实际新source epoch恰**六源**：example、observer、fixture、prefix四个Rust源，加新verifier及其test。新adapter的source守卫按这个完整闭包及 `prefixContract.sourceCount=6`核对，不能套用旧五源。新def、manifest/lock、实际DLL/fixture和owned结果须由主线随后另建accepted epoch。

## 实现代理交付时的验证

本节保留构建前交付范围；主线随后实际执行及当前状态见下节。

原23个Rust ABI case保留，新增B的13个前缀正反及18个边界，共54例规格，全部接入原同一个完整ABI循环：GPR16/XMM16/RFLAGS、栈128B、入口/返回LastError、原调用恰一次、high64返回、源对象不变、每例实际observer读取接口的精确地址轨迹、终态零追加读、drop恢复及完整284B emitter。13与11的读取轨迹都恰止于原单位预算。

新增边界包括完整language输入仍2、整数值float env、字段重排、escaped key、language末端backslash/部分unicode escape/高低surrogate、escaped quote、非法hex、已消费前缀UTF8恰65535/65536、未知outer嵌套值、escaped duplicate、bool env、缺分隔符、非法必要escape和相同前缀无NUL。合法完整候选与非法未观察后缀可得到同13，四项完整性/自然业务范围仍固定false。

实际最终33项Python检查通过，日志 `tests-final.private.log`；它们覆盖独立B oracle的31个前缀/完整输入投影、准确source变换/54case名单、六源闭包、历史manifest分离、状态/事实位/分类位约束、完整284B篡改、9读预算及adapter的owned/mock生命周期/输出守卫。B oracle未修改。这些检查不执行Rust parser，也不宣称Python对任意JSON与serde等价。

`source-final1`、`source-final2` 两fresh source-only报告均31034B/SHA `0b817fdf9c82b6e7c68d5666e6a900915263e118bcef474e761bb4f6e02a393f`，逐byte相同；六源/86冻结pin/31个Python oracle投影/零新PE窗。它们仍记录主线追加新example前的manifest，不能代替后续新构建epoch。def SHA为 `12647050cec9207594cd83081e3df144da690e69fcb2344d73a2184b82f93984`；旧源/报告及当前Cargo保持的核验为 `frozen-legacy-preserved.private.json`。

新adapter的四个accepted/artifact/fixture/manifest常量仍为 `None`，实际门禁明确拒绝尚未绑定的epoch。新DLL/EXE尚未构建，54例Rust/实际EAT/DATA/新emitter均未执行，新隔离安装文件布局门禁与自然实验未发生。没有Git、Cargo/Gradle/JVM/DB、进程/端口/target、客户端/调试器/REA或部署操作。共享索引、协议、配置和资源未改；主线统一审核、构建、提交并维护Project/Harness。

## 主线实际构建、ABI及文件门禁验收

主线仅在Cargo追加独立example，lock及旧源保持。release/locked/offline DLL构建9.16秒、fixture构建5.97秒，分别630272B/SHA `4e9f94ef61aa94e4a3c708f52bfeb21d278783d5ee6c55c88df1a9ac40a52e23`、871936B/SHA `b0391765fc440d621ac87ae3795acba861fb46e639b0e71be2a8d6f3bcc4094b`。新manifest SHA `d0e13f90b8a4a468d545ec3a08641a4eaa0115e38e47343d1449d2764c482c3e`；旧accepted manifest副本与守卫不变。

新`owned-main1/proof.private.json` SHA `5925f0d4212375fbccb137f9afc3d9190418b5877e4cf549273b3b19415938b4`已实际执行两fresh各54例。完整GPR/XMM/RFLAGS/栈/LastError、恰一次原调用、高64返回、源对象/读取轨迹及drop均通过；每次完整284B emitter核验通过。实际51 EAT/72B DATA、六源/86旧pin、新def/manifest/lock/artifact/fixture和前缀限定范围经主线14项审核，审核SHA `19353b32b3b079ed9ffe2bb9f47509e1506727aa0e5902240d14de337c9b2ad1`。独立源码复核及33检查也通过，详见`independent-review/source-review.private.json`。

主线只回填新adapter四个SHA常量；绑定后33项再通过。adapter SHA `c3ffdeb1713c789378e74190b7b32c77abbf189bc21134d6c72697621f2f41b1`。在忽略目录以当前stock硬链接和独立proxy副本构成隔离文件布局，实际未mock的accepted/preflight门禁通过，记录SHA `67c824047dada8190f0ce1c69b68025647a72443f31587a28e6656a33e23ab83`。此布局不是GAME_PATH安装，也未启动客户端。私有wrapper的旧LASTEXITCODE、审核中exports列表计数及记录bytes序列化问题均保留原件，修正未改变源、产物、预算或门禁。

本节构建验收时尚未取得新自然13/11；其后实际新epoch见下节。原Marshal、完整Init/route/认证、R1/R3及R4前置仍未齐，未部署游戏服务。每轮prefix专属scope、备份/安装/退出恢复脚本须主线预检并取得新实时确认；旧ready不复用。

## 新epoch自然13、正常退出与stock恢复

专属helper经主线逐项审阅，122个唯一source pin含实际28文件Python导入闭包；新plan SHA `69e6662d7d990ef6d262dd44e1ca5561ecf9f90089b9a72ec9248287a564a80c`。实际CheckOnly五stock/13保护/SDK carrier/无进程端口通过；用户随后新确认后才写本epoch ready、Hidden UAC备份/安装准确DLL并启动90秒观察。没有复用先前split或联合stock采集的ready。

实际45次monitor、唯一9读1450B/max512，前后72B自有DATA相同，完整284B trampoline、38B wrapper及47原转发精确绑定。准确caller自然取得**event13、cn-env-0、appKeyMatches=true、comboUrlMatches=true**，只确认必要前缀字段及后续language quoted value跨原unit预算。四项完整payload/NUL/Marshal/Init-route标志仍false，未保存自然正文、长度/hash、key或URL。原5/11 epoch和限额不改，停止继续追大language或无假设扩大读取。

原始`helper-preparation/live1.private.json` SHA `8be0a8a53eb173a375473645ad955d0fdd5ce5b0d63fa3f8c91dcfb73e3cdef2`；同名`.reads.private.json` SHA `2ed9de53be71624df8844d93ac6e01f63d8ebad38ad146374b8ac4927738691b`。主线以这9条原始账本纯重放，完整输出逐字段相同；16审核通过，`main-live-acceptance.private.json` SHA `934ba6137f724ffa35d20628ea7e8fd40cc65ed6a621df36892c9cd01844171e`。前后creation/module身份、末尾alive/关闭和新ready/source/artifact join均通过，observer实际exit0。

用户反馈页面正常、已正常退出。仅恢复本轮精确备份和自建alias，恢复helper实际exit0，五stock/13保护/缺席/carrier保持、自有alias移除；主线结束相关进程及固定监听0。原始journal/launch/prepared/finished/restored/after均在本卡`helper-preparation/`，stock互斥已释放。此自然前缀不关闭完整分类/原Marshal、SDK writer返回、自然Init/route、认证或D-02；已交Sol max固定最短后继，继续复用原桥/writer/HTTP/key，不重做语言预算实验。未部署GS。

## 主线构建复现和新的自然准备范围

主线先审核实际diff，在Cargo仅追加以下声明，保留lock和旧example；实现代理不改manifest：

```toml
[[example]]
name = "sdk72_config_prefix_proxy"
crate-type = ["cdylib"]
```

本轮source-only复现入口不需要client参数。主线追加manifest后须选择新目录重生proof；实际artifact/owned审核需要显式传入本批已核GAME_PATH，不能复用构建前manifest身份。

```powershell
python -B -m tools.verify_sdk72_config_prefix_proxy --prepare-build-inputs --output-directory local/migration72/d02-parallel-20261109/b-sdk-prefix/source-main1
$env:CARGO_HOME = (Resolve-Path local/cargo).Path
$env:RUSTUP_HOME = (Resolve-Path local/rustup).Path
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'local/migration72/d02-parallel-20261109/b-sdk-prefix/target'
$prefixDef = (Resolve-Path local/migration72/d02-parallel-20261109/b-sdk-prefix/source-main1/config-prefix-proxy.def).Path
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_prefix_proxy --crate-type cdylib --release --locked --offline -- -C "link-arg=/DEF:$prefixDef"
if ($LASTEXITCODE -ne 0) { throw 'Prefix DLL build failed; preserve the exact failure.' }
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_prefix_proxy --crate-type bin --release --locked --offline
if ($LASTEXITCODE -ne 0) { throw 'Prefix owned EXE build failed.' }
python -B -m tools.verify_sdk72_config_prefix_proxy --client $env:GAME_PATH --production local/migration72/d02-parallel-20261109/b-sdk-prefix/target/release/examples/sdk72_config_prefix_proxy.dll --fixture local/migration72/d02-parallel-20261109/b-sdk-prefix/target/release/examples/sdk72_config_prefix_proxy.exe --run-owned --output-directory local/migration72/d02-parallel-20261109/b-sdk-prefix/owned-main1
python -B -m unittest tools.test_verify_sdk72_config_prefix_proxy tools.test_observe_client72_config_prefix_proxy -v
```

主线实际release/locked/offline、两fresh各54例、51 EAT/72B DATA/完整284B搬移恢复、六源/86旧pin/新manifest/def/artifact join及独立第三审核完成后，才回填新adapter四SHA并验新的真实隔离文件门禁。构建前的artifact/owned为null，source gate本身不执行Rust；审核应比较准确source/build/material合同，实际产物/owned分别验。

之后另封存prefix专属scope、安装/备份/正常退出/stock恢复脚本和新保护基线/实际归属盘点，取得新的实时ready。不得复用split live3的ready、输出、PID或base。新自然问题仅为13是否满足前缀合同；11是有效负结果，不能自动归因于某字段。所有结果保持原预算并在首失败/终态后停止，不扩大读取或同参数重试。原Marshal、SDK export/writer返回、完整Init/route/认证及D-02继续单独验收。
